package id.hananote.ink

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.util.LruCache
import id.hananote.data.ImageItem
import java.io.File
import kotlin.math.roundToInt

/**
 * Bayangan gambar yang mengikuti bentuknya: foto biasa berbayang kotak, gambar tembus pandang
 * (stiker, hasil hapus latar) berbayang sesuai siluetnya.
 *
 * Bayangan dibuat kecil (sekitar 160 piksel) lalu dikaburkan dan diregangkan saat digambar,
 * jadi ringan dan halus di semua perbesaran tanpa efek yang bergantung pada kartu grafis.
 */
object Shadows {

    private const val SIDE = 160

    private val cache = LruCache<String, Bitmap>(40)

    /** Gambar bayangan di bawah [item] (dipanggil sebelum gambarnya sendiri). */
    fun draw(c: Canvas, item: ImageItem, imageDir: File?, paint: Paint) {
        val sh = item.shadow ?: return
        if (sh.opacity <= 0f || imageDir == null) return
        val longest = maxOf(item.w, item.h).coerceAtLeast(1f)
        // Lebar kabur dalam piksel bayangan kecil, dibulatkan supaya bisa disimpan untuk dipakai ulang.
        val blurPx = (sh.blur / longest * SIDE).roundToInt().coerceIn(0, 40)
        val key = "${item.file}|${item.crop}|$blurPx"
        val bmp = cache.get(key) ?: build(item, imageDir, blurPx)?.also { cache.put(key, it) } ?: return
        val pad = if (blurPx == 0) 0f else blurPx * longest / SIDE
        val dx = sh.distance * 0.35f
        val dy = sh.distance
        c.save()
        c.translate(dx, dy)
        if (item.rotation != 0f) c.rotate(item.rotation, item.cx, item.cy)
        if (item.flipH || item.flipV) c.scale(if (item.flipH) -1f else 1f, if (item.flipV) -1f else 1f, item.cx, item.cy)
        paint.reset()
        paint.isAntiAlias = true
        paint.isFilterBitmap = true
        paint.alpha = (sh.opacity.coerceIn(0f, 1f) * 255).toInt()
        c.drawBitmap(bmp, null, RectF(item.x - pad, item.y - pad, item.x + item.w + pad, item.y + item.h + pad), paint)
        c.restore()
    }

    private fun build(item: ImageItem, imageDir: File, blurPx: Int): Bitmap? {
        val src = ImageCache.get(File(imageDir, item.file), 512) ?: return null
        val crop = item.crop
        val sl = (crop.l * src.width).toInt().coerceIn(0, src.width - 1)
        val st = (crop.t * src.height).toInt().coerceIn(0, src.height - 1)
        val sr = (crop.r * src.width).toInt().coerceIn(sl + 1, src.width)
        val sb = (crop.b * src.height).toInt().coerceIn(st + 1, src.height)
        val ratio = item.w / item.h.coerceAtLeast(0.01f)
        val w = if (ratio >= 1f) SIDE else (SIDE * ratio).roundToInt().coerceAtLeast(4)
        val h = if (ratio >= 1f) (SIDE / ratio).roundToInt().coerceAtLeast(4) else SIDE
        val small = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        Canvas(small).drawBitmap(src, Rect(sl, st, sr, sb), Rect(0, 0, w, h), Paint(Paint.FILTER_BITMAP_FLAG))
        val px = IntArray(w * h)
        small.getPixels(px, 0, w, 0, 0, w, h)
        small.recycle()
        val alpha = IntArray(w * h) { px[it] ushr 24 }
        val (out, ow, oh) = ShadowMask.blur(alpha, w, h, blurPx)
        val argb = IntArray(ow * oh) { (out[it].coerceIn(0, 255) shl 24) }
        return Bitmap.createBitmap(argb, ow, oh, Bitmap.Config.ARGB_8888)
    }
}

/** Mengaburkan topeng alfa dengan tiga kali kabur kotak (mendekati kabur Gauss). Tanpa Android, jadi bisa diuji. */
object ShadowMask {

    /** Hasil: topeng baru yang diberi tepi [r] di tiap sisi, beserta lebar dan tingginya. */
    fun blur(alpha: IntArray, w: Int, h: Int, r: Int): Triple<IntArray, Int, Int> {
        if (r <= 0) return Triple(alpha.copyOf(), w, h)
        val ow = w + r * 2
        val oh = h + r * 2
        var buf = IntArray(ow * oh)
        for (y in 0 until h) for (x in 0 until w) buf[(y + r) * ow + x + r] = alpha[y * w + x]
        val rr = (r / 3).coerceAtLeast(1)
        repeat(3) {
            buf = boxH(buf, ow, oh, rr)
            buf = boxV(buf, ow, oh, rr)
        }
        return Triple(buf, ow, oh)
    }

    private fun boxH(src: IntArray, w: Int, h: Int, r: Int): IntArray {
        val out = IntArray(src.size)
        val div = r * 2 + 1
        for (y in 0 until h) {
            val row = y * w
            var sum = 0
            for (k in -r..r) sum += src[row + k.coerceIn(0, w - 1)]
            for (x in 0 until w) {
                out[row + x] = sum / div
                val add = (x + r + 1).coerceAtMost(w - 1)
                val sub = (x - r).coerceAtLeast(0)
                sum += src[row + add] - src[row + sub]
            }
        }
        return out
    }

    private fun boxV(src: IntArray, w: Int, h: Int, r: Int): IntArray {
        val out = IntArray(src.size)
        val div = r * 2 + 1
        for (x in 0 until w) {
            var sum = 0
            for (k in -r..r) sum += src[k.coerceIn(0, h - 1) * w + x]
            for (y in 0 until h) {
                out[y * w + x] = sum / div
                val add = (y + r + 1).coerceAtMost(h - 1)
                val sub = (y - r).coerceAtLeast(0)
                sum += src[add * w + x] - src[sub * w + x]
            }
        }
        return out
    }
}
