package id.goresan.engine

import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * Masker pilihan (seperti alat Selection Procreate): 1 byte per piksel, 255 = terpilih penuh.
 * Semua fungsi di sini murni (tanpa GPU) supaya bisa dijalankan di thread bantu.
 */
object SelMask {
    const val OP_ADD = 0
    const val OP_REMOVE = 1
    const val OP_REPLACE = 2

    /** Segi banyak tertutup (aturan genap-ganjil), tepi halus: 4 garis bantu per baris + cakupan pecahan mendatar. */
    fun polygon(xs: FloatArray, ys: FloatArray, n: Int, w: Int, h: Int): ByteArray {
        val out = ByteArray(w * h)
        if (n < 3) return out
        var minY = Float.MAX_VALUE
        var maxY = -Float.MAX_VALUE
        for (i in 0 until n) { minY = min(minY, ys[i]); maxY = max(maxY, ys[i]) }
        val y0 = floor(minY).toInt().coerceIn(0, h)
        val y1 = ceil(maxY).toInt().coerceIn(0, h)
        val acc = FloatArray(w + 1)
        var xsBuf = FloatArray(64)
        for (y in y0 until y1) {
            java.util.Arrays.fill(acc, 0f)
            var touched = false
            for (sub in 0 until 4) {
                val sy = y + (sub + 0.5f) / 4f
                var c = 0
                var j = n - 1
                for (i in 0 until n) {
                    val ya = ys[j]; val yb = ys[i]
                    if ((ya <= sy && yb > sy) || (yb <= sy && ya > sy)) {
                        val t = (sy - ya) / (yb - ya)
                        if (c == xsBuf.size) xsBuf = xsBuf.copyOf(c * 2)
                        xsBuf[c++] = xs[j] + (xs[i] - xs[j]) * t
                    }
                    j = i
                }
                if (c < 2) continue
                java.util.Arrays.sort(xsBuf, 0, c)
                var k = 0
                while (k + 1 < c) {
                    val a = xsBuf[k].coerceIn(0f, w.toFloat())
                    val b = xsBuf[k + 1].coerceIn(0f, w.toFloat())
                    if (b > a) {
                        touched = true
                        val ia = floor(a).toInt()
                        val ib = min(w - 1, floor(b).toInt())
                        if (ia == ib) {
                            acc[ia] += (b - a) * 0.25f
                        } else {
                            acc[ia] += (ia + 1 - a) * 0.25f
                            for (x in ia + 1 until ib) acc[x] += 0.25f
                            if (ib < w) acc[ib] += (b - ib) * 0.25f
                        }
                    }
                    k += 2
                }
            }
            if (!touched) continue
            val row = y * w
            for (x in 0 until w) {
                val v = acc[x]
                if (v > 0f) out[row + x] = (v.coerceAtMost(1f) * 255f).roundToInt().toByte()
            }
        }
        return out
    }

    fun rect(x0: Float, y0: Float, x1: Float, y1: Float, w: Int, h: Int): ByteArray {
        val xs = floatArrayOf(min(x0, x1), max(x0, x1), max(x0, x1), min(x0, x1))
        val ys = floatArrayOf(min(y0, y1), min(y0, y1), max(y0, y1), max(y0, y1))
        return polygon(xs, ys, 4, w, h)
    }

    /** Elips di dalam kotak (x0,y0)-(x1,y1), tepi halus. */
    fun ellipse(x0: Float, y0: Float, x1: Float, y1: Float, w: Int, h: Int): ByteArray {
        val out = ByteArray(w * h)
        val cx = (x0 + x1) / 2f
        val cy = (y0 + y1) / 2f
        val rx = kotlin.math.abs(x1 - x0) / 2f
        val ry = kotlin.math.abs(y1 - y0) / 2f
        if (rx < 0.5f || ry < 0.5f) return out
        val ya = floor(cy - ry).toInt().coerceIn(0, h)
        val yb = ceil(cy + ry).toInt().coerceIn(0, h)
        val xa = floor(cx - rx).toInt().coerceIn(0, w)
        val xb = ceil(cx + rx).toInt().coerceIn(0, w)
        val rr = min(rx, ry)
        for (y in ya until yb) {
            val dy = (y + 0.5f - cy) / ry
            for (x in xa until xb) {
                val dx = (x + 0.5f - cx) / rx
                // Jarak bertanda kira-kira (piksel) ke tepi elips.
                val d = (sqrt(dx * dx + dy * dy) - 1f) * rr
                val v = (0.5f - d).coerceIn(0f, 1f)
                if (v > 0f) out[y * w + x] = (v * 255f).roundToInt().toByte()
            }
        }
        return out
    }

    /** Gabungkan [b] ke [a] (hasil di [a]). */
    fun combine(a: ByteArray, b: ByteArray, op: Int) {
        when (op) {
            OP_REPLACE -> b.copyInto(a)
            OP_REMOVE -> for (i in a.indices) {
                val va = a[i].toInt() and 0xFF
                if (va == 0) continue
                val vb = b[i].toInt() and 0xFF
                a[i] = (va * (255 - vb) / 255).toByte()
            }
            else -> for (i in a.indices) {
                val vb = b[i].toInt() and 0xFF
                if (vb > (a[i].toInt() and 0xFF)) a[i] = vb.toByte()
            }
        }
    }

    fun invert(a: ByteArray) {
        for (i in a.indices) a[i] = (255 - (a[i].toInt() and 0xFF)).toByte()
    }

    fun isEmpty(a: ByteArray): Boolean {
        for (v in a) if (v.toInt() != 0) return false
        return true
    }

    /** Kotak batas piksel yang terpilih (kosong bila tidak ada). */
    fun bounds(a: ByteArray, w: Int, h: Int): IRect {
        var x0 = w; var y0 = h; var x1 = -1; var y1 = -1
        for (y in 0 until h) {
            val row = y * w
            var any = false
            for (x in 0 until w) {
                if (a[row + x].toInt() != 0) {
                    any = true
                    if (x < x0) x0 = x
                    break
                }
            }
            if (!any) continue
            for (x in w - 1 downTo 0) if (a[row + x].toInt() != 0) { if (x > x1) x1 = x; break }
            if (y < y0) y0 = y
            y1 = y
        }
        return if (x1 < 0) IRect() else IRect(x0, y0, x1 + 1, y1 + 1)
    }

    /** Tepi diperhalus (Feather): blur kotak tiga kali, kira-kira Gaussian berjari-jari [r] piksel. */
    fun feather(src: ByteArray, w: Int, h: Int, r: Int): ByteArray {
        if (r <= 0) return src.copyOf()
        val rad = max(1, (r / 1.7f).roundToInt())
        // Nilai tetap 0..255 di setiap lintasan, jadi cukup disimpan per byte (hemat memori di kanvas besar).
        val cur = src.copyOf()
        val tmp = ByteArray(w * h)
        repeat(3) {
            boxH(cur, tmp, w, h, rad)
            boxV(tmp, cur, w, h, rad)
        }
        return cur
    }

    private fun boxH(src: ByteArray, dst: ByteArray, w: Int, h: Int, r: Int) {
        val win = 2 * r + 1
        for (y in 0 until h) {
            val row = y * w
            var sum = 0
            for (k in -r..r) sum += src[row + k.coerceIn(0, w - 1)].toInt() and 0xFF
            for (x in 0 until w) {
                dst[row + x] = (sum / win).toByte()
                sum += (src[row + min(w - 1, x + r + 1)].toInt() and 0xFF) - (src[row + max(0, x - r)].toInt() and 0xFF)
            }
        }
    }

    private fun boxV(src: ByteArray, dst: ByteArray, w: Int, h: Int, r: Int) {
        val win = 2 * r + 1
        for (x in 0 until w) {
            var sum = 0
            for (k in -r..r) sum += src[k.coerceIn(0, h - 1) * w + x].toInt() and 0xFF
            for (y in 0 until h) {
                dst[y * w + x] = (sum / win).toByte()
                sum += (src[min(h - 1, y + r + 1) * w + x].toInt() and 0xFF) - (src[max(0, y - r) * w + x].toInt() and 0xFF)
            }
        }
    }
}
