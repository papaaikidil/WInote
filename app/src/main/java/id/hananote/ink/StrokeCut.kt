package id.hananote.ink

import kotlin.math.ceil
import kotlin.math.sqrt

/**
 * Penghapus sebagian: membuang bagian goresan yang tersapu penghapus dan menyisakan potongannya.
 * Penghapus dianggap kapsul, yaitu garis dari posisi sebelumnya ke posisi sekarang dengan jari-jari
 * tertentu, supaya sapuan cepat tetap memotong walau titik sentuhnya berjauhan.
 */
object StrokeCut {

    class Piece(val xs: FloatArray, val ys: FloatArray, val ps: FloatArray)

    /**
     * Hasil: null bila goresan tidak tersentuh; daftar potongan sisa (bisa kosong bila habis) bila tersentuh.
     * [step] jarak terjauh antartitik setelah dirapatkan, supaya tepi potongan rapi.
     */
    fun cut(
        xs: FloatArray, ys: FloatArray, ps: FloatArray, n: Int,
        ax: Float, ay: Float, bx: Float, by: Float, reach: Float, step: Float,
    ): List<Piece>? {
        if (n == 0) return null
        if (n == 1) return if (dist(xs[0], ys[0], ax, ay, bx, by) <= reach) emptyList() else null

        // Periksa cepat tanpa membuat larik: adakah ruas yang tersentuh?
        var touched = false
        for (i in 1 until n) {
            if (segDist(xs[i - 1], ys[i - 1], xs[i], ys[i], ax, ay, bx, by) <= reach) { touched = true; break }
        }
        if (!touched) return null

        // Rapatkan titik pada ruas yang dekat penghapus.
        val px = FloatArrayList(n * 2)
        val py = FloatArrayList(n * 2)
        val pp = FloatArrayList(n * 2)
        val inside = BooleanArrayList(n * 2)
        var any = false
        for (i in 0 until n) {
            if (i > 0) {
                val x0 = xs[i - 1]; val y0 = ys[i - 1]; val x1 = xs[i]; val y1 = ys[i]
                val len = sqrt((x1 - x0) * (x1 - x0) + (y1 - y0) * (y1 - y0))
                if (len > step && segDist(x0, y0, x1, y1, ax, ay, bx, by) <= reach) {
                    val k = ceil(len / step).toInt()
                    for (j in 1 until k) {
                        val t = j.toFloat() / k
                        val x = x0 + (x1 - x0) * t
                        val y = y0 + (y1 - y0) * t
                        val hit = dist(x, y, ax, ay, bx, by) <= reach
                        any = any || hit
                        px.add(x); py.add(y); pp.add(ps[i - 1] + (ps[i] - ps[i - 1]) * t); inside.add(hit)
                    }
                }
            }
            val hit = dist(xs[i], ys[i], ax, ay, bx, by) <= reach
            any = any || hit
            px.add(xs[i]); py.add(ys[i]); pp.add(ps[i]); inside.add(hit)
        }
        if (!any) return null

        val out = ArrayList<Piece>()
        var start = -1
        for (i in 0..px.size) {
            val out_ = i == px.size || inside[i]
            if (!out_ && start < 0) start = i
            if (out_ && start >= 0) {
                if (i - start >= 2) out.add(Piece(px.slice(start, i), py.slice(start, i), pp.slice(start, i)))
                start = -1
            }
        }
        return out
    }

    /** Jarak titik ke kapsul (ruas a–b). */
    fun dist(px: Float, py: Float, ax: Float, ay: Float, bx: Float, by: Float): Float {
        val dx = bx - ax
        val dy = by - ay
        val len2 = dx * dx + dy * dy
        val t = if (len2 == 0f) 0f else (((px - ax) * dx + (py - ay) * dy) / len2).coerceIn(0f, 1f)
        val cx = ax + t * dx
        val cy = ay + t * dy
        return sqrt((px - cx) * (px - cx) + (py - cy) * (py - cy))
    }

    /** Jarak terdekat dua ruas: 0 bila berpotongan, selain itu jarak terkecil ujung ke ruas lainnya. */
    fun segDist(x0: Float, y0: Float, x1: Float, y1: Float, ax: Float, ay: Float, bx: Float, by: Float): Float {
        if (crosses(x0, y0, x1, y1, ax, ay, bx, by)) return 0f
        return minOf(dist(x0, y0, ax, ay, bx, by), dist(x1, y1, ax, ay, bx, by), dist(ax, ay, x0, y0, x1, y1), dist(bx, by, x0, y0, x1, y1))
    }

    private fun crosses(x0: Float, y0: Float, x1: Float, y1: Float, ax: Float, ay: Float, bx: Float, by: Float): Boolean {
        val d1 = cross(ax, ay, bx, by, x0, y0)
        val d2 = cross(ax, ay, bx, by, x1, y1)
        val d3 = cross(x0, y0, x1, y1, ax, ay)
        val d4 = cross(x0, y0, x1, y1, bx, by)
        return ((d1 > 0f && d2 < 0f) || (d1 < 0f && d2 > 0f)) && ((d3 > 0f && d4 < 0f) || (d3 < 0f && d4 > 0f))
    }

    private fun cross(ax: Float, ay: Float, bx: Float, by: Float, px: Float, py: Float) =
        (bx - ax) * (py - ay) - (by - ay) * (px - ax)

    private class FloatArrayList(cap: Int) {
        private var a = FloatArray(cap.coerceAtLeast(4))
        var size = 0
            private set
        fun add(v: Float) { if (size == a.size) a = a.copyOf(size * 2); a[size++] = v }
        fun slice(from: Int, to: Int): FloatArray = a.copyOfRange(from, to)
    }

    private class BooleanArrayList(cap: Int) {
        private var a = BooleanArray(cap.coerceAtLeast(4))
        var size = 0
            private set
        fun add(v: Boolean) { if (size == a.size) a = a.copyOf(size * 2); a[size++] = v }
        operator fun get(i: Int) = a[i]
    }
}
