package id.hananote.ink

import android.graphics.Path
import id.hananote.data.TapeItem
import id.hananote.data.TapeShape
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/** Membuat, menggambar garis tepi, dan mengenai selotip hafalan ([TapeItem]). Koordinat dalam satuan halaman. */
object Tapes {

    /** Pita lurus dari (x0, y0) ke (x1, y1). Hampir mendatar/tegak (±5°) diluruskan. */
    fun straight(x0: Float, y0: Float, x1: Float, y1: Float, thick: Float, color: Int): TapeItem {
        val len = hypot(x1 - x0, y1 - y0)
        var deg = Math.toDegrees(atan2((y1 - y0).toDouble(), (x1 - x0).toDouble())).toFloat()
        for (snap in floatArrayOf(-180f, -90f, 0f, 90f, 180f)) if (abs(deg - snap) <= 5f) deg = snap
        if (deg > 90f) deg -= 180f
        if (deg < -90f) deg += 180f
        val cx = (x0 + x1) / 2f
        val cy = (y0 + y1) / 2f
        val h = max(thick, 2f)
        return TapeItem(cx - len / 2f, cy - h / 2f, len, h, TapeShape.STRAIGHT, color, h, null, if (deg == 0f) 0f else deg)
    }

    /** Pita bebas mengikuti titik-titik [pts] (x, y bergantian). Null bila terlalu pendek. */
    fun free(pts: FloatArray, n: Int, thick: Float, color: Int): TapeItem? {
        if (n < 2) return null
        val half = max(thick, 2f) / 2f
        var l = pts[0]; var t = pts[1]; var r = pts[0]; var b = pts[1]
        for (i in 1 until n) {
            val x = pts[i * 2]; val y = pts[i * 2 + 1]
            if (x < l) l = x; if (x > r) r = x
            if (y < t) t = y; if (y > b) b = y
        }
        l -= half; t -= half; r += half; b += half
        val w = max(r - l, 1f)
        val h = max(b - t, 1f)
        val norm = FloatArray(n * 2)
        for (i in 0 until n) {
            norm[i * 2] = (pts[i * 2] - l) / w
            norm[i * 2 + 1] = (pts[i * 2 + 1] - t) / h
        }
        return TapeItem(l, t, w, h, TapeShape.FREE, color, half * 2f, norm)
    }

    /** Bentuk penuh seukuran kotak tarikan (sekecil-kecilnya [minSize] tiap sisi, tetap di sekitar titik awal). */
    fun shape(kind: TapeShape, x0: Float, y0: Float, x1: Float, y1: Float, minSize: Float, color: Int): TapeItem {
        var l = min(x0, x1); var r = max(x0, x1)
        var t = min(y0, y1); var b = max(y0, y1)
        if (r - l < minSize) { if (x1 >= x0) r = l + minSize else l = r - minSize }
        if (b - t < minSize) { if (y1 >= y0) b = t + minSize else t = b - minSize }
        return TapeItem(l, t, r - l, b - t, kind, color)
    }

    /** Titik sudut bentuk bersudut dalam 0..1 (x, y bergantian); null untuk bentuk lain. */
    fun polygon(kind: TapeShape): FloatArray? = when (kind) {
        TapeShape.TRIANGLE -> TRIANGLE
        TapeShape.DIAMOND -> DIAMOND
        TapeShape.HEXAGON -> HEXAGON
        TapeShape.STAR -> STAR
        TapeShape.HEART -> HEART
        else -> null
    }

    private val TRIANGLE = floatArrayOf(0.5f, 0f, 1f, 1f, 0f, 1f)
    private val DIAMOND = floatArrayOf(0.5f, 0f, 1f, 0.5f, 0.5f, 1f, 0f, 0.5f)
    private val HEXAGON = floatArrayOf(0.25f, 0f, 0.75f, 0f, 1f, 0.5f, 0.75f, 1f, 0.25f, 1f, 0f, 0.5f)
    private val STAR: FloatArray = run {
        // Bintang lima sudut yang memenuhi kotak: titik puncak di atas, dua kaki menyentuh dasar.
        val raw = FloatArray(20)
        for (k in 0 until 10) {
            val a = -Math.PI / 2 + k * Math.PI / 5
            val rr = if (k % 2 == 0) 1.0 else 0.4
            raw[k * 2] = (cos(a) * rr).toFloat()
            raw[k * 2 + 1] = (sin(a) * rr).toFloat()
        }
        fit(raw)
    }
    private val HEART: FloatArray = run {
        val n = 64
        val raw = FloatArray(n * 2)
        for (k in 0 until n) {
            val tt = k * 2.0 * Math.PI / n
            val s = sin(tt)
            raw[k * 2] = (16.0 * s * s * s).toFloat()
            raw[k * 2 + 1] = -(13.0 * cos(tt) - 5.0 * cos(2 * tt) - 2.0 * cos(3 * tt) - cos(4 * tt)).toFloat()
        }
        fit(raw)
    }

    /** Skala titik-titik supaya pas mengisi 0..1. */
    private fun fit(raw: FloatArray): FloatArray {
        var l = Float.MAX_VALUE; var t = Float.MAX_VALUE; var r = -Float.MAX_VALUE; var b = -Float.MAX_VALUE
        for (i in raw.indices step 2) {
            l = min(l, raw[i]); r = max(r, raw[i]); t = min(t, raw[i + 1]); b = max(b, raw[i + 1])
        }
        val out = FloatArray(raw.size)
        for (i in raw.indices step 2) {
            out[i] = (raw[i] - l) / (r - l)
            out[i + 1] = (raw[i + 1] - t) / (b - t)
        }
        return out
    }

    /**
     * Garis tepi selotip sebelum diputar (kotak l, t, r, b), untuk diisi. Untuk pita bebas: jalur tengahnya
     * (digambar sebagai garis setebal [TapeItem.thick]).
     */
    fun outline(tp: TapeItem, out: Path) {
        out.reset()
        val l = tp.x; val t = tp.y; val r = tp.x + tp.w; val b = tp.y + tp.h
        when (tp.shape) {
            TapeShape.STRAIGHT, TapeShape.RECT -> out.addRect(l, t, r, b, Path.Direction.CW)
            TapeShape.ROUNDED -> {
                val rad = min(tp.w, tp.h) * 0.22f
                out.addRoundRect(l, t, r, b, rad, rad, Path.Direction.CW)
            }
            TapeShape.ELLIPSE -> out.addOval(l, t, r, b, Path.Direction.CW)
            TapeShape.FREE -> {
                val p = tp.path ?: return
                val n = p.size / 2
                if (n == 0) return
                out.moveTo(l + p[0] * tp.w, t + p[1] * tp.h)
                if (n == 1) out.lineTo(l + p[0] * tp.w + 0.01f, t + p[1] * tp.h)
                // Kurva halus melalui titik tengah antarsampel.
                for (i in 1 until n - 1) {
                    val x = l + p[i * 2] * tp.w; val y = t + p[i * 2 + 1] * tp.h
                    val nx = l + p[i * 2 + 2] * tp.w; val ny = t + p[i * 2 + 3] * tp.h
                    out.quadTo(x, y, (x + nx) / 2f, (y + ny) / 2f)
                }
                if (n >= 2) out.lineTo(l + p[(n - 1) * 2] * tp.w, t + p[(n - 1) * 2 + 1] * tp.h)
            }
            else -> {
                val poly = polygon(tp.shape) ?: return
                out.moveTo(l + poly[0] * tp.w, t + poly[1] * tp.h)
                for (i in 2 until poly.size step 2) out.lineTo(l + poly[i] * tp.w, t + poly[i + 1] * tp.h)
                out.close()
            }
        }
    }

    /** Titik halaman (px, py) mengenai selotip ini, dengan kelonggaran [pad]. */
    fun hit(tp: TapeItem, px: Float, py: Float, pad: Float): Boolean {
        // Ke koordinat selotip sebelum diputar.
        var dx = px - tp.cx
        var dy = py - tp.cy
        if (tp.rotation != 0f) {
            val rad = Math.toRadians(tp.rotation.toDouble())
            val c = cos(rad).toFloat()
            val s = sin(rad).toFloat()
            val lx = dx * c + dy * s
            val ly = -dx * s + dy * c
            dx = lx; dy = ly
        }
        val hw = tp.w / 2f
        val hh = tp.h / 2f
        if (abs(dx) > hw + pad || abs(dy) > hh + pad) return false
        return when (tp.shape) {
            TapeShape.STRAIGHT, TapeShape.RECT, TapeShape.ROUNDED -> true
            TapeShape.ELLIPSE -> {
                val ex = dx / (hw + pad)
                val ey = dy / (hh + pad)
                ex * ex + ey * ey <= 1f
            }
            TapeShape.FREE -> {
                val p = tp.path ?: return false
                val n = p.size / 2
                // Titik dalam satuan halaman relatif ke pojok kiri atas kotak.
                val qx = dx + hw
                val qy = dy + hh
                val reach = tp.thick / 2f + pad
                var best = Float.MAX_VALUE
                for (i in 0 until n) {
                    val ax = p[i * 2] * tp.w; val ay = p[i * 2 + 1] * tp.h
                    if (i == 0) { best = min(best, hypot(qx - ax, qy - ay)); continue }
                    val bx = p[i * 2 - 2] * tp.w; val by = p[i * 2 - 1] * tp.h
                    best = min(best, segDist(qx, qy, bx, by, ax, ay))
                    if (best <= reach) return true
                }
                best <= reach
            }
            else -> {
                val poly = polygon(tp.shape) ?: return true
                val qx = (dx + hw) / tp.w
                val qy = (dy + hh) / tp.h
                if (inside(poly, qx, qy)) return true
                // Dekat tepinya (dalam satuan halaman) juga dihitung kena.
                val m = poly.size / 2
                for (i in 0 until m) {
                    val j = (i + 1) % m
                    val d = segDist(
                        dx + hw, dy + hh,
                        poly[i * 2] * tp.w, poly[i * 2 + 1] * tp.h, poly[j * 2] * tp.w, poly[j * 2 + 1] * tp.h,
                    )
                    if (d <= pad) return true
                }
                false
            }
        }
    }

    private fun inside(poly: FloatArray, x: Float, y: Float): Boolean {
        var c = false
        val m = poly.size / 2
        var j = m - 1
        for (i in 0 until m) {
            val xi = poly[i * 2]; val yi = poly[i * 2 + 1]
            val xj = poly[j * 2]; val yj = poly[j * 2 + 1]
            if ((yi > y) != (yj > y) && x < (xj - xi) * (y - yi) / (yj - yi) + xi) c = !c
            j = i
        }
        return c
    }

    private fun segDist(px: Float, py: Float, ax: Float, ay: Float, bx: Float, by: Float): Float {
        val vx = bx - ax
        val vy = by - ay
        val l2 = vx * vx + vy * vy
        if (l2 <= 1e-6f) return hypot(px - ax, py - ay)
        val k = (((px - ax) * vx + (py - ay) * vy) / l2).coerceIn(0f, 1f)
        return hypot(px - (ax + k * vx), py - (ay + k * vy))
    }

    /** Jarak terkecil antartitik jalur bebas (satuan halaman) supaya jalurnya tidak terlalu rapat. */
    fun minStep(scale: Float): Float = 1.5f / max(scale, 0.05f)
}
