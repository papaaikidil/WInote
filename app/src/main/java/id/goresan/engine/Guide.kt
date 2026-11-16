package id.goresan.engine

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Panduan menggambar (Drawing Guide) seperti di Procreate: Grid 2D, Isometrik, Perspektif, dan Simetri.
 * Semua koordinat dalam piksel kanvas, sudut dalam radian. Tanpa ketergantungan Android supaya bisa diuji sendiri.
 */
data class DrawingGuide(
    val type: Int = GRID,
    /** Warna garis (ARGB). */
    val color: Int = DEFAULT_COLOR,
    /** Kepekatan garis 0..1. */
    val opacity: Float = 0.75f,
    /** Ketebalan garis 0..1 (di layar 0,5–5 dp). */
    val thickness: Float = 0.2f,
    /** Jarak kisi Grid 2D / sisi segitiga Isometrik, piksel kanvas. */
    val gridSize: Float = 150f,
    /** Titik pusat/asal panduan (titik hijau); NaN = tengah kanvas. */
    val cx: Float = Float.NaN,
    val cy: Float = Float.NaN,
    /** Putaran panduan (titik biru). */
    val angle: Float = 0f,
    /** Titik hilang perspektif: x0, y0, x1, y1, … (paling banyak 3 titik). */
    val vps: List<Float> = emptyList(),
    val symmetry: Int = SYM_VERTICAL,
    /** Simetri putar: salinan diputar, bukan dicerminkan. */
    val rotational: Boolean = false,
) {
    val vpCount: Int get() = vps.size / 2
    fun centerX(w: Int) = if (cx.isNaN()) w / 2f else cx
    fun centerY(h: Int) = if (cy.isNaN()) h / 2f else cy
    fun vpX(i: Int) = vps[2 * i]
    fun vpY(i: Int) = vps[2 * i + 1]

    companion object {
        const val GRID = 0
        const val ISO = 1
        const val PERSP = 2
        const val SYM = 3

        const val SYM_VERTICAL = 0
        const val SYM_HORIZONTAL = 1
        const val SYM_QUADRANT = 2
        const val SYM_RADIAL = 3

        const val MAX_VP = 3
        const val DEFAULT_COLOR = 0xFF2F9BFF.toInt()
        const val MIN_GRID = 8f
        /** Jarak titik putar (biru) dari titik pusat (hijau) di layar, dp. */
        const val ROTATE_HANDLE_DP = 72f
    }
}

object GuideGeom {
    private const val MAX_LINES = 1500
    private val SQRT3_2 = sqrt(3f) / 2f
    private const val DEG = (PI / 180.0).toFloat()

    /**
     * Garis panduan untuk digambar di layar, dalam piksel kanvas: [x0, y0, x1, y1] berurutan, sudah dipotong ke
     * kotak kanvas. [major] diisi garis utama (horizon perspektif, sumbu simetri) yang digambar sedikit lebih tebal.
     */
    fun lines(g: DrawingGuide, w: Int, h: Int, major: ArrayList<Float>? = null): FloatArray {
        val out = ArrayList<Float>(1024)
        val cx = g.centerX(w)
        val cy = g.centerY(h)
        when (g.type) {
            DrawingGuide.GRID -> {
                val s = max(DrawingGuide.MIN_GRID, g.gridSize)
                family(out, w, h, cx, cy, g.angle, s)
                family(out, w, h, cx, cy, g.angle + PI.toFloat() / 2f, s)
            }
            DrawingGuide.ISO -> {
                val s = max(DrawingGuide.MIN_GRID, g.gridSize) * SQRT3_2
                family(out, w, h, cx, cy, g.angle + 90f * DEG, s)
                family(out, w, h, cx, cy, g.angle + 30f * DEG, s)
                family(out, w, h, cx, cy, g.angle + 150f * DEG, s)
            }
            DrawingGuide.PERSP -> {
                for (i in 0 until g.vpCount) rays(out, w, h, g.vpX(i), g.vpY(i))
                val m = major ?: out
                if (g.vpCount >= 2) {
                    line(m, w, h, g.vpX(0), g.vpY(0), g.vpX(1) - g.vpX(0), g.vpY(1) - g.vpY(0))
                } else if (g.vpCount == 1) {
                    line(m, w, h, g.vpX(0), g.vpY(0), 1f, 0f)
                }
            }
            DrawingGuide.SYM -> {
                val m = major ?: out
                for (a in symmetryAxes(g)) line(m, w, h, cx, cy, cos(a), sin(a))
            }
        }
        return out.toFloatArray()
    }

    /** Sudut sumbu simetri yang digambar (garis lewat titik pusat). */
    fun symmetryAxes(g: DrawingGuide): List<Float> {
        val h = g.angle
        val v = g.angle + PI.toFloat() / 2f
        return when (g.symmetry) {
            DrawingGuide.SYM_HORIZONTAL -> listOf(h)
            DrawingGuide.SYM_QUADRANT -> listOf(h, v)
            DrawingGuide.SYM_RADIAL -> List(4) { g.angle + it * 45f * DEG }
            else -> listOf(v)
        }
    }

    /** Sekelompok garis sejajar (arah [ang]) berjarak [step] satu sama lain, salah satunya lewat (cx, cy). */
    private fun family(out: ArrayList<Float>, w: Int, h: Int, cx: Float, cy: Float, ang: Float, step: Float) {
        val dx = cos(ang)
        val dy = sin(ang)
        // Arah tegak lurus: garis ke-k lewat (cx, cy) + n·k·step.
        val nx = -dy
        val ny = dx
        var lo = Float.MAX_VALUE
        var hi = -Float.MAX_VALUE
        for ((px, py) in corners(w, h)) {
            val t = (px - cx) * nx + (py - cy) * ny
            lo = min(lo, t)
            hi = max(hi, t)
        }
        val k0 = floor(lo / step).toInt()
        val k1 = ceil(hi / step).toInt()
        if (k1 - k0 > MAX_LINES) return
        for (k in k0..k1) line(out, w, h, cx + nx * k * step, cy + ny * k * step, dx, dy)
    }

    /** Sinar dari titik hilang: rapat di sekelilingnya bila di dalam kanvas, atau menyebar ke arah kanvas bila di luar. */
    private fun rays(out: ArrayList<Float>, w: Int, h: Int, vx: Float, vy: Float) {
        val inside = vx in 0f..w.toFloat() && vy in 0f..h.toFloat()
        if (inside) {
            val n = 48
            for (k in 0 until n) {
                val a = k * 2f * PI.toFloat() / n
                ray(out, w, h, vx, vy, cos(a), sin(a))
            }
            return
        }
        // Rentang sudut kanvas dilihat dari titik hilang.
        val base = atan2(h / 2f - vy, w / 2f - vx)
        var lo = 0f
        var hi = 0f
        for ((px, py) in corners(w, h)) {
            var d = atan2(py - vy, px - vx) - base
            while (d > PI) d -= 2f * PI.toFloat()
            while (d < -PI) d += 2f * PI.toFloat()
            lo = min(lo, d)
            hi = max(hi, d)
        }
        val n = 28
        for (k in 0..n) {
            val a = base + lo + (hi - lo) * k / n
            ray(out, w, h, vx, vy, cos(a), sin(a))
        }
    }

    private fun corners(w: Int, h: Int) = listOf(0f to 0f, w.toFloat() to 0f, 0f to h.toFloat(), w.toFloat() to h.toFloat())

    /** Garis tak hingga lewat (px, py) dengan arah (dx, dy), dipotong ke kanvas. */
    private fun line(out: ArrayList<Float>, w: Int, h: Int, px: Float, py: Float, dx: Float, dy: Float) =
        clip(out, w, h, px, py, dx, dy, -Float.MAX_VALUE)

    /** Setengah garis dari (px, py) ke arah (dx, dy), dipotong ke kanvas. */
    private fun ray(out: ArrayList<Float>, w: Int, h: Int, px: Float, py: Float, dx: Float, dy: Float) =
        clip(out, w, h, px, py, dx, dy, 0f)

    private fun clip(out: ArrayList<Float>, w: Int, h: Int, px: Float, py: Float, dx0: Float, dy0: Float, tStart: Float) {
        val len = hypot(dx0, dy0)
        if (len < 1e-6f) return
        val dx = dx0 / len
        val dy = dy0 / len
        var t0 = tStart
        var t1 = Float.MAX_VALUE
        // Lempeng x
        if (abs(dx) < 1e-6f) {
            if (px < 0f || px > w) return
        } else {
            var a = (0f - px) / dx
            var b = (w - px) / dx
            if (a > b) { val t = a; a = b; b = t }
            t0 = max(t0, a); t1 = min(t1, b)
        }
        if (abs(dy) < 1e-6f) {
            if (py < 0f || py > h) return
        } else {
            var a = (0f - py) / dy
            var b = (h - py) / dy
            if (a > b) { val t = a; a = b; b = t }
            t0 = max(t0, a); t1 = min(t1, b)
        }
        if (t1 <= t0) return
        out.add(px + dx * t0); out.add(py + dy * t0)
        out.add(px + dx * t1); out.add(py + dy * t1)
    }

    /**
     * Arah garis yang boleh dipakai Bantuan menggambar dari titik awal (sx, sy), sebagai pasangan (dx, dy) satuan.
     * Kosong = tidak ada penguncian garis (Simetri).
     */
    fun directions(g: DrawingGuide, sx: Float, sy: Float): FloatArray {
        val a = ArrayList<Float>()
        fun add(ang: Float) { a.add(cos(ang)); a.add(sin(ang)) }
        when (g.type) {
            DrawingGuide.GRID -> { add(g.angle); add(g.angle + 90f * DEG) }
            DrawingGuide.ISO -> { add(g.angle + 90f * DEG); add(g.angle + 30f * DEG); add(g.angle + 150f * DEG) }
            DrawingGuide.PERSP -> {
                for (i in 0 until g.vpCount) {
                    val dx = g.vpX(i) - sx
                    val dy = g.vpY(i) - sy
                    val l = hypot(dx, dy)
                    if (l > 2f) { a.add(dx / l); a.add(dy / l) }
                }
                // 1 titik: garis mendatar dan tegak; 2 titik: garis tegak; 3 titik: hanya ke titik hilang.
                if (g.vpCount <= 1) add(0f)
                if (g.vpCount <= 2) add(90f * DEG)
            }
        }
        return a.toFloatArray()
    }

    /** Arah dari [dirs] yang paling dekat dengan gerakan (mx, my) (arah bolak-balik dianggap sama). */
    fun pick(dirs: FloatArray, mx: Float, my: Float): Int {
        var best = -1
        var bestV = -1f
        var k = 0
        while (k + 1 < dirs.size) {
            val v = abs(dirs[k] * mx + dirs[k + 1] * my)
            if (v > bestV) { bestV = v; best = k / 2 }
            k += 2
        }
        return best
    }

    /**
     * Salinan simetri selain aslinya. Tiap salinan 2 angka: jenis (0 = putar, 1 = cermin) dan sudut
     * (putar: besar putaran; cermin: arah sumbu cermin), semuanya terhadap titik pusat panduan.
     */
    fun symmetry(g: DrawingGuide): FloatArray {
        val a = ArrayList<Float>()
        fun rot(r: Float) { a.add(0f); a.add(r) }
        fun mir(axis: Float) { a.add(1f); a.add(axis) }
        val hAx = g.angle
        val vAx = g.angle + 90f * DEG
        val pi = PI.toFloat()
        when (g.symmetry) {
            DrawingGuide.SYM_HORIZONTAL -> if (g.rotational) rot(pi) else mir(hAx)
            DrawingGuide.SYM_QUADRANT -> if (g.rotational) {
                rot(pi / 2f); rot(pi); rot(3f * pi / 2f)
            } else {
                mir(vAx); mir(hAx); rot(pi)
            }
            DrawingGuide.SYM_RADIAL -> if (g.rotational) {
                for (k in 1..7) rot(k * pi / 4f)
            } else {
                rot(pi / 2f); rot(pi); rot(3f * pi / 2f)
                for (k in 0..3) mir(g.angle + k * pi / 4f)
            }
            else -> if (g.rotational) rot(pi) else mir(vAx)
        }
        return a.toFloatArray()
    }

    /**
     * Tambahkan salinan simetri tiap dab di [list] (dab asli tetap). Cermin membalik dab (kebulatan negatif),
     * jadi ujung kuas yang tidak simetris ikut tercermin, bukan sekadar diputar.
     */
    fun mirrorDabs(list: DabList, sym: FloatArray, cx: Float, cy: Float) {
        if (sym.isEmpty()) return
        val n = list.size
        val d = list.data
        var k = 0
        while (k + 1 < sym.size) {
            val kind = sym[k]
            val v = sym[k + 1]
            val c: Float
            val s: Float
            if (kind == 0f) { c = cos(v); s = sin(v) } else { c = cos(2f * v); s = sin(2f * v) }
            for (i in 0 until n) {
                val o = i * DabList.S
                val dx = d[o] - cx
                val dy = d[o + 1] - cy
                if (kind == 0f) {
                    list.add(cx + c * dx - s * dy, cy + s * dx + c * dy, d[o + 2], d[o + 3] + v, d[o + 4], d[o + 5], d[o + 6], d[o + 7], d[o + 8], d[o + 9])
                } else {
                    list.add(cx + c * dx + s * dy, cy + s * dx - c * dy, d[o + 2], 2f * v - d[o + 3], -d[o + 4], d[o + 5], d[o + 6], d[o + 7], d[o + 8], d[o + 9])
                }
            }
            k += 2
        }
    }
}
