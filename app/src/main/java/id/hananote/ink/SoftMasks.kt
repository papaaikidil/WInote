package id.hananote.ink

import android.graphics.Matrix
import android.graphics.RectF
import id.hananote.data.ImageItem
import id.hananote.data.PageItem
import id.hananote.data.Stroke
import id.hananote.data.TextItem
import id.hananote.data.Tool
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.sqrt

/**
 * Bekas kuas penghapus lembut yang menempel pada objek yang dihapusnya (goresan, teks, atau benda), bukan
 * tergeletak sendiri di halaman. Dengan begitu bagian yang sudah dihapus tetap hilang walau objeknya digeser,
 * diperbesar, diputar, disalin, atau dibalik.
 *
 * - Goresan: bekasnya dalam koordinat halaman dan ikut diubah bersama titik-titik goresannya.
 * - Teks: koordinat teks (kiri atas = 0, satuan = ukuran huruf).
 * - Benda: koordinat isi benda 0..1 (untuk gambar: seluruh gambar aslinya, jadi tetap pas walau dipotong).
 */
object SoftMasks {

    // ---------- teks ----------

    fun textToLocal(t: TextItem, m: Stroke): Stroke {
        val k = 1f / t.size.coerceAtLeast(0.01f)
        val xs = FloatArray(m.size) { (m.xs[it] - t.x) * k }
        val ys = FloatArray(m.size) { (m.ys[it] - t.y) * k }
        return Stroke(Tool.ERASER, m.color, m.width * k, xs, ys, m.ps)
    }

    fun textPageMasks(t: TextItem): List<Stroke> {
        t.pageMasks?.let { return it }
        val k = t.size
        val out = t.masks.orEmpty().map { m ->
            val xs = FloatArray(m.size) { t.x + m.xs[it] * k }
            val ys = FloatArray(m.size) { t.y + m.ys[it] * k }
            Stroke(Tool.ERASER, m.color, m.width * k, xs, ys, m.ps)
        }
        t.pageMasks = out
        return out
    }

    // ---------- benda ----------

    /**
     * Matriks dari koordinat isi benda (0..1) ke halaman, sama seperti cara bendanya digambar: potongan gambar,
     * kotak benda, dibalik terhadap tengahnya, lalu diputar terhadap tengahnya. Hasil: skala lebar bekas.
     */
    private fun itemMatrix(item: PageItem, out: Matrix): Float {
        var l = 0f; var t = 0f; var r = 1f; var b = 1f
        var fx = 1f; var fy = 1f
        if (item is ImageItem) {
            l = item.crop.l; t = item.crop.t; r = item.crop.r; b = item.crop.b
            if (item.flipH) fx = -1f
            if (item.flipV) fy = -1f
        }
        val cw = (r - l).coerceAtLeast(0.01f)
        val ch = (b - t).coerceAtLeast(0.01f)
        val sx = item.w / cw
        val sy = item.h / ch
        out.reset()
        out.setTranslate(-l, -t)
        out.postScale(sx, sy)
        out.postTranslate(item.x, item.y)
        if (fx != 1f || fy != 1f) out.postScale(fx, fy, item.cx, item.cy)
        if (item.rotation != 0f) out.postRotate(item.rotation, item.cx, item.cy)
        return sqrt(abs(sx * sy)).coerceAtLeast(0.01f)
    }

    fun itemToLocal(item: PageItem, m: Stroke): Stroke? {
        val fwd = Matrix()
        val k = itemMatrix(item, fwd)
        val inv = Matrix()
        if (!fwd.invert(inv)) return null
        val pts = FloatArray(m.size * 2)
        for (i in 0 until m.size) { pts[i * 2] = m.xs[i]; pts[i * 2 + 1] = m.ys[i] }
        inv.mapPoints(pts)
        val xs = FloatArray(m.size) { pts[it * 2] }
        val ys = FloatArray(m.size) { pts[it * 2 + 1] }
        return Stroke(Tool.ERASER, m.color, m.width / k, xs, ys, m.ps)
    }

    fun itemPageMasks(item: PageItem): List<Stroke> {
        item.pageMasks?.let { return it }
        val fwd = Matrix()
        val k = itemMatrix(item, fwd)
        val out = item.masks.orEmpty().map { m ->
            val pts = FloatArray(m.size * 2)
            for (i in 0 until m.size) { pts[i * 2] = m.xs[i]; pts[i * 2 + 1] = m.ys[i] }
            fwd.mapPoints(pts)
            Stroke(Tool.ERASER, m.color, m.width * k, FloatArray(m.size) { i -> pts[i * 2] }, FloatArray(m.size) { i -> pts[i * 2 + 1] }, m.ps)
        }
        item.pageMasks = out
        return out
    }

    // ---------- goresan ----------

    /** Bekas penghapus [m] benar-benar menyentuh goresan [s] (bukan sekadar kotaknya bersinggungan). */
    fun touches(s: Stroke, m: Stroke): Boolean {
        if (!RectF.intersects(s.bounds, m.bounds) || s.size == 0 || m.size == 0) return false
        val reach = m.width / 2f + s.width * 0.8f + 1f
        val step = max(1, (m.size / 400))
        var j = 0
        while (j < m.size) {
            val px = m.xs[j]; val py = m.ys[j]
            if (px >= s.bounds.left - reach && px <= s.bounds.right + reach && py >= s.bounds.top - reach && py <= s.bounds.bottom + reach) {
                if (s.size == 1) {
                    val dx = px - s.xs[0]; val dy = py - s.ys[0]
                    if (dx * dx + dy * dy <= reach * reach) return true
                } else {
                    for (i in 1 until s.size) {
                        if (StrokeCut.dist(px, py, s.xs[i - 1], s.ys[i - 1], s.xs[i], s.ys[i]) <= reach) return true
                    }
                }
            }
            j += step
        }
        return false
    }

    /**
     * Catatan lama menyimpan bekas kuas penghapus sebagai coretan tersendiri di halaman. Pindahkan tiap bekas ke
     * objek di bawahnya yang tersentuh, lalu buang bekas lepasnya.
     */
    fun migrate(strokes: MutableList<Stroke>, texts: MutableList<TextItem>, items: MutableList<PageItem>) {
        val marks = strokes.filter { it.tool == Tool.ERASER }.sortedBy { it.z }
        if (marks.isEmpty()) return
        strokes.removeAll { it.tool == Tool.ERASER }
        val box = RectF()
        for (m in marks) {
            for (i in strokes.indices) {
                val s = strokes[i]
                if (s.z < m.z && touches(s, m)) strokes[i] = s.withMask(m)
            }
            for (i in texts.indices) {
                val t = texts[i]
                if (t.z >= m.z) continue
                InkRenderer.textBounds(t, box)
                if (RectF.intersects(box, m.bounds)) texts[i] = t.withMask(textToLocal(t, m))
            }
            for (i in items.indices) {
                val item = items[i]
                if (item.z >= m.z) continue
                InkRenderer.itemBounds(item, box)
                if (RectF.intersects(box, m.bounds)) itemToLocal(item, m)?.let { lm -> items[i] = item.withMask(lm) }
            }
        }
    }
}
