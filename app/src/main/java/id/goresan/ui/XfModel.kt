package id.goresan.ui

import android.graphics.Matrix
import android.graphics.PointF
import android.graphics.RectF
import id.goresan.engine.XfFrame
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.exp
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.round

/**
 * Keadaan transformasi (seperti alat Transformasi Procreate). Disimpan di thread antarmuka:
 *  - [quad]: letak keempat sudut kotak isi (kiri-atas, kanan-atas, kanan-bawah, kiri-bawah) di kanvas; dari sini
 *    dihitung matriks proyektif (geser, putar, skala, distorsi),
 *  - [net]: 4×4 titik kendali Warp di ruang asal (kotak isi asli), diterapkan sebelum matriks.
 */
class XfModel(val src: RectF) {
    companion object {
        const val FREEFORM = 0
        const val UNIFORM = 1
        const val DISTORT = 2
        const val WARP = 3

        const val NEAREST = 0
        const val BILINEAR = 1
        const val BICUBIC = 2

        /** Pegangan: 0..3 sudut (KA, KaA, KaB, KB), 4..7 tepi (atas, kanan, bawah, kiri), 8 putar, 100+k titik Warp. */
        const val H_ROTATE = 8
        const val H_NET = 100
    }

    val quad = FloatArray(8)
    val net = FloatArray(32)
    private val srcPts = floatArrayOf(src.left, src.top, src.right, src.top, src.right, src.bottom, src.left, src.bottom)
    var guideX = Float.NaN
    var guideY = Float.NaN

    init { reset() }

    fun reset() {
        srcPts.copyInto(quad)
        for (j in 0..3) for (i in 0..3) {
            net[(j * 4 + i) * 2] = src.left + src.width() * i / 3f
            net[(j * 4 + i) * 2 + 1] = src.top + src.height() * j / 3f
        }
    }

    val warped: Boolean
        get() {
            for (j in 0..3) for (i in 0..3) {
                if (abs(net[(j * 4 + i) * 2] - (src.left + src.width() * i / 3f)) > 0.01f) return true
                if (abs(net[(j * 4 + i) * 2 + 1] - (src.top + src.height() * j / 3f)) > 0.01f) return true
            }
            return false
        }

    /** Matriks ruang asal → kanvas (bisa proyektif bila didistorsi). */
    fun matrix(): Matrix {
        val m = Matrix()
        if (!m.setPolyToPoly(srcPts, 0, quad, 0, 4)) m.reset()
        return m
    }

    private fun inverse(): Matrix {
        val inv = Matrix()
        matrix().invert(inv)
        return inv
    }

    fun center(): PointF {
        val p = floatArrayOf(src.centerX(), src.centerY())
        matrix().mapPoints(p)
        return PointF(p[0], p[1])
    }

    fun post(m: Matrix) {
        m.mapPoints(quad)
    }

    fun rotate(deg: Float) {
        val c = center()
        post(Matrix().apply { setRotate(deg, c.x, c.y) })
    }

    fun flip(horizontal: Boolean) {
        val q = quad.copyOf()
        // Isi dicerminkan di dalam kotak yang sama: sudut-sudutnya ditukar.
        val order = if (horizontal) intArrayOf(1, 0, 3, 2) else intArrayOf(3, 2, 1, 0)
        for (k in 0..3) {
            quad[2 * k] = q[2 * order[k]]
            quad[2 * k + 1] = q[2 * order[k] + 1]
        }
    }

    fun fit(docW: Int, docH: Int) {
        val s = min(docW / src.width(), docH / src.height())
        val w = src.width() * s
        val h = src.height() * s
        val x0 = (docW - w) / 2f
        val y0 = (docH - h) / 2f
        floatArrayOf(x0, y0, x0 + w, y0, x0 + w, y0 + h, x0, y0 + h).copyInto(quad)
    }

    /** Titik kendali Warp di kanvas. */
    fun netCanvas(): FloatArray {
        val p = net.copyOf()
        matrix().mapPoints(p)
        return p
    }

    // ---------------- seretan ----------------

    private var dragId = -1
    private val startQuad = FloatArray(8)
    private val startNet = FloatArray(32)
    private val startNetCanvas = FloatArray(32)
    private var startX = 0f
    private var startY = 0f
    private var startAng = 0f
    private var startBoxAng = 0f
    private val weights = FloatArray(16)

    /** [sx], [sy]: titik di layar (untuk bobot Warp), [cx], [cy]: titik yang sama di kanvas. */
    fun dragStart(id: Int, cx: Float, cy: Float, screenPts: FloatArray?, sx: Float, sy: Float, sigma: Float) {
        dragId = id
        quad.copyInto(startQuad)
        net.copyInto(startNet)
        netCanvas().copyInto(startNetCanvas)
        startX = cx
        startY = cy
        val c = center()
        startAng = atan2(cy - c.y, cx - c.x)
        startBoxAng = atan2(quad[3] - quad[1], quad[2] - quad[0])
        if (screenPts != null) {
            for (k in 0 until 16) {
                val d = hypot(screenPts[2 * k] - sx, screenPts[2 * k + 1] - sy)
                weights[k] = exp(-(d * d) / (2f * sigma * sigma))
            }
        }
    }

    fun dragEnd() {
        dragId = -1
        guideX = Float.NaN
        guideY = Float.NaN
    }

    /**
     * Seret ke titik kanvas ([x], [y]). [mode] cara transformasi, [snap] Snapping hidup, [tol] jarak tempel (piksel
     * kanvas), [softWarp] seretan Warp di dalam kotak (bukan di titik kendali).
     */
    fun drag(x: Float, y: Float, mode: Int, snap: Boolean, tol: Float, docW: Int, docH: Int, softWarp: Boolean) {
        val id = dragId
        val dx = x - startX
        val dy = y - startY
        guideX = Float.NaN
        guideY = Float.NaN
        when {
            id >= H_NET -> {
                val k = id - H_NET
                val inv = inverse()
                val p = floatArrayOf(startNetCanvas[2 * k] + dx, startNetCanvas[2 * k + 1] + dy)
                inv.mapPoints(p)
                net[2 * k] = p[0]; net[2 * k + 1] = p[1]
            }
            id < 0 && softWarp -> {
                val inv = inverse()
                val p = FloatArray(32)
                for (k in 0 until 16) {
                    p[2 * k] = startNetCanvas[2 * k] + dx * weights[k]
                    p[2 * k + 1] = startNetCanvas[2 * k + 1] + dy * weights[k]
                }
                inv.mapPoints(p)
                p.copyInto(net)
            }
            id < 0 -> moveTo(dx, dy, snap, tol, docW, docH)
            id == H_ROTATE -> {
                startQuad.copyInto(quad)
                val c = center()
                var total = atan2(y - c.y, x - c.x) - startAng
                if (snap) {
                    val step = (Math.PI / 12).toFloat()
                    total = round((startBoxAng + total) / step) * step - startBoxAng
                }
                post(Matrix().apply { setRotate(Math.toDegrees(total.toDouble()).toFloat(), c.x, c.y) })
            }
            mode == DISTORT -> {
                startQuad.copyInto(quad)
                if (id < 4) {
                    quad[2 * id] = x; quad[2 * id + 1] = y
                } else {
                    val a = id - 4
                    val b = (a + 1) % 4
                    quad[2 * a] += dx; quad[2 * a + 1] += dy
                    quad[2 * b] += dx; quad[2 * b + 1] += dy
                }
            }
            else -> resizeTo(id, x, y, mode == UNIFORM)
        }
    }

    private fun moveTo(dx: Float, dy: Float, snap: Boolean, tol: Float, docW: Int, docH: Int) {
        var mx = dx
        var my = dy
        if (snap) {
            // Tempel: pusat kotak ke tengah kanvas, tepi kotak ke tepi kanvas.
            var minX = Float.MAX_VALUE; var maxX = -Float.MAX_VALUE; var minY = Float.MAX_VALUE; var maxY = -Float.MAX_VALUE
            for (k in 0..3) {
                minX = min(minX, startQuad[2 * k] + dx); maxX = max(maxX, startQuad[2 * k] + dx)
                minY = min(minY, startQuad[2 * k + 1] + dy); maxY = max(maxY, startQuad[2 * k + 1] + dy)
            }
            val cx = (minX + maxX) / 2f
            val cy = (minY + maxY) / 2f
            when {
                abs(cx - docW / 2f) < tol -> { mx += docW / 2f - cx; guideX = docW / 2f }
                abs(minX) < tol -> { mx -= minX; guideX = 0f }
                abs(maxX - docW) < tol -> { mx += docW - maxX; guideX = docW.toFloat() }
            }
            when {
                abs(cy - docH / 2f) < tol -> { my += docH / 2f - cy; guideY = docH / 2f }
                abs(minY) < tol -> { my -= minY; guideY = 0f }
                abs(maxY - docH) < tol -> { my += docH - maxY; guideY = docH.toFloat() }
            }
        }
        for (k in 0..3) {
            quad[2 * k] = startQuad[2 * k] + mx
            quad[2 * k + 1] = startQuad[2 * k + 1] + my
        }
    }

    /**
     * Ubah ukuran lewat pegangan sudut/tepi. Dihitung di ruang asal (kotak lurus), lalu dipetakan lagi dengan matriks
     * awal, jadi tetap benar walau kotaknya sudah diputar atau didistorsi.
     */
    private fun resizeTo(id: Int, x: Float, y: Float, uniform: Boolean) {
        startQuad.copyInto(quad)
        val m = matrix()
        val inv = Matrix()
        if (!m.invert(inv)) return
        val p = floatArrayOf(x, y)
        inv.mapPoints(p)
        val px = p[0]
        val py = p[1]
        var l = src.left; var t = src.top; var r = src.right; var b = src.bottom
        val w = src.width(); val h = src.height()
        val minSize = 2f
        when (id) {
            0, 1, 2, 3 -> {
                // Sudut lawan tetap.
                val ox = if (id == 0 || id == 3) src.right else src.left
                val oy = if (id == 0 || id == 1) src.bottom else src.top
                val cxs = if (id == 0 || id == 3) src.left else src.right
                val cys = if (id == 0 || id == 1) src.top else src.bottom
                var nx = px
                var ny = py
                if (uniform) {
                    val vx = cxs - ox; val vy = cys - oy
                    val s = (((px - ox) * vx + (py - oy) * vy) / (vx * vx + vy * vy)).coerceAtLeast(0.02f)
                    nx = ox + vx * s; ny = oy + vy * s
                }
                if (id == 0 || id == 3) l = min(nx, r - minSize) else r = max(nx, l + minSize)
                if (id == 0 || id == 1) t = min(ny, b - minSize) else b = max(ny, t + minSize)
            }
            4, 6 -> {
                if (id == 4) t = min(py, b - minSize) else b = max(py, t + minSize)
                if (uniform) {
                    val f = (b - t) / h
                    val cxm = src.centerX()
                    l = cxm - w * f / 2f; r = cxm + w * f / 2f
                }
            }
            5, 7 -> {
                if (id == 7) l = min(px, r - minSize) else r = max(px, l + minSize)
                if (uniform) {
                    val f = (r - l) / w
                    val cym = src.centerY()
                    t = cym - h * f / 2f; b = cym + h * f / 2f
                }
            }
        }
        val nq = floatArrayOf(l, t, r, t, r, b, l, b)
        m.mapPoints(nq)
        nq.copyInto(quad)
    }

    // ---------------- pegangan & gambar ----------------

    /** Pegangan di layar: pasangan (id, x, y). [toScreen] memetakan titik kanvas ke layar. */
    fun handles(mode: Int, toScreen: (FloatArray) -> Unit, rotateOffset: Float): List<Triple<Int, Float, Float>> {
        val out = ArrayList<Triple<Int, Float, Float>>()
        if (mode == WARP) {
            val p = netCanvas()
            toScreen(p)
            for (k in 0 until 16) out.add(Triple(H_NET + k, p[2 * k], p[2 * k + 1]))
            return out
        }
        val q = quad.copyOf()
        toScreen(q)
        for (k in 0..3) out.add(Triple(k, q[2 * k], q[2 * k + 1]))
        for (k in 0..3) {
            val j = (k + 1) % 4
            out.add(Triple(4 + k, (q[2 * k] + q[2 * j]) / 2f, (q[2 * k + 1] + q[2 * j + 1]) / 2f))
        }
        val r = rotateHandle(q, rotateOffset)
        out.add(Triple(H_ROTATE, r[0], r[1]))
        return out
    }

    /** Pegangan putar: di luar tengah tepi atas, [off] piksel layar menjauh dari pusat kotak. */
    private fun rotateHandle(q: FloatArray, off: Float): FloatArray {
        val mx = (q[0] + q[2]) / 2f
        val my = (q[1] + q[3]) / 2f
        val cx = (q[0] + q[2] + q[4] + q[6]) / 4f
        val cy = (q[1] + q[3] + q[5] + q[7]) / 4f
        var nx = mx - cx
        var ny = my - cy
        val len = hypot(nx, ny)
        if (len < 1e-3f) { nx = 0f; ny = -1f } else { nx /= len; ny /= len }
        return floatArrayOf(mx + nx * off, my + ny * off)
    }

    /** Data untuk mesin: matriks, jaring Warp (bila dipakai), interpolasi, dan gambar bantu di layar. */
    fun frame(mode: Int, interp: Int): XfFrame {
        val v = FloatArray(9)
        matrix().getValues(v)
        val useNet = warped
        val curves = ArrayList<FloatArray>()
        if (mode == WARP || useNet) {
            // Garis-garis kisi Warp (4 mendatar, 4 tegak), di kanvas.
            for (line in 0..3) for (dir in 0..1) {
                val pts = FloatArray(17 * 2)
                for (s in 0..16) {
                    val u = if (dir == 0) s / 16f else line / 3f
                    val w = if (dir == 0) line / 3f else s / 16f
                    val e = evalNet(u, w)
                    pts[2 * s] = e[0]; pts[2 * s + 1] = e[1]
                }
                matrix().mapPoints(pts)
                curves.add(pts)
            }
        }
        return XfFrame(
            m = v,
            net = if (useNet) net.copyOf() else null,
            src = floatArrayOf(src.left, src.top, src.right, src.bottom),
            interp = interp,
            quad = quad.copyOf(),
            mode = mode,
            netPts = if (mode == WARP) netCanvas() else null,
            curves = curves,
            guideX = guideX,
            guideY = guideY,
        )
    }

    /** Titik pada permukaan Bézier kubik jaring (ruang asal), u, w di 0..1. */
    fun evalNet(u: Float, w: Float): FloatArray = XfFrame.bezier(net, u, w)
}
