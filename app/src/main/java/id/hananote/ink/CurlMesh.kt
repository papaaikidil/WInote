package id.hananote.ink

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/**
 * Bentuk lembaran kertas yang sedang dibalik, untuk animasi ganti halaman.
 *
 * Lembaran dianggap menggulung pada sebuah silinder: garis sumbunya bergerak dari pojok kanan bawah ke kiri,
 * mula-mula miring (pojok terangkat lebih dulu) lalu makin tegak. Bagian kertas sebelum sumbu tetap datar,
 * bagian di atas silinder melengkung, dan bagian yang sudah lewat terbalik dan rebah di atasnya (memperlihatkan
 * punggung kertas). Hasilnya segitiga-segitiga untuk digambar dengan tekstur gambar halaman:
 * [front] (sisi depan, datar dan lengkung bawah) dan [back] (punggung kertas, digambar di atasnya).
 *
 * Murni hitungan (tanpa kelas Android) supaya bisa diuji terpisah.
 */
class CurlMesh(private val curlBands: Int = 28) {

    class Part(cap: Int) {
        /** Posisi layar (x, y) tiap titik. */
        val verts = FloatArray(cap * 2)
        /** Posisi di gambar halaman (piksel) tiap titik. */
        val texs = FloatArray(cap * 2)
        /** Bayangan tiap titik: negatif = gelap (alfa hitam), positif = kilau (alfa putih). */
        val shade = FloatArray(cap)
        val indices = ShortArray(cap * 3)
        var count = 0
            internal set
        var indexCount = 0
            internal set

        internal fun clear() { count = 0; indexCount = 0 }
    }

    private val cap = (curlBands + 4) * 8
    val front = Part(cap)
    val back = Part(cap)

    /** Arah tegak lurus sumbu lipatan (menunjuk ke bagian yang terangkat), satuan. */
    var nx = 1f
        private set
    var ny = 0f
        private set

    /** Bayangan lengkungan di halaman bawah: mulai dari [shadowFrom] sampai [shadowTo] (diukur sepanjang n). */
    var shadowFrom = 0f
        private set
    var shadowPeak = 0f
        private set
    var shadowTo = 0f
        private set
    /** Tepi punggung kertas yang rebah (diukur sepanjang n), atau NaN bila belum ada. */
    var backEdge = Float.NaN
        private set
    /** Seberapa jauh lembaran sudah terangkat (0..1), untuk kekuatan bayangan. */
    var lift = 0f
        private set
    /** Rentang lembaran sepanjang n (sebelum dilengkungkan). */
    var uStart = 0f
        private set
    var uEnd = 0f
        private set
    /** Letak sumbu lipatan sepanjang n. */
    val foldU: Float get() = s
    /** Jari-jari gulungan. */
    val rollRadius: Float get() = r
    /** Geser dari koordinat layar lembaran ke koordinat gambarnya, sepanjang n. */
    val texOffsetU: Float get() = texDx * nx + texDy * ny

    /**
     * Gelap-terang kertas di titik lembaran yang berjarak [u] sepanjang n: negatif = bayangan (hitam), positif =
     * kilau (putih). Sama untuk setiap titik pada garis yang sejajar sumbu, jadi bisa digambar sebagai gradasi.
     */
    fun shadeAt(u: Float): Float {
        val d = u - s
        val half = (PI * r).toFloat()
        if (d <= 0f) return 0f
        if (d >= half) return -0.05f
        val th = d / r
        return if (th <= PI / 2) {
            // Sisi depan yang menggulung makin gelap mendekati puncak.
            -0.42f * (1f - cos(th))
        } else {
            // Punggung kertas: gelap di dekat puncak, lalu kilau lembut, lalu terang.
            val g = (th - PI * 0.70) / (PI * 0.13)
            (-0.34f * (1f + cos(th)) + 0.30f * exp(-g * g).toFloat())
        }
    }

    // Lembaran: kotak di layar dan geser tekstur.
    private var left = 0f
    private var top = 0f
    private var right = 0f
    private var bottom = 0f
    private var texDx = 0f
    private var texDy = 0f

    // Keadaan per bingkai.
    private var s = 0f
    private var r = 1f
    private var tx = 0f
    private var ty = 1f
    private var cx = 0f
    private var cy = 0f
    private var focal = 1f

    private val breaks = FloatArray(curlBands + 8)
    private val polyX = FloatArray(12)
    private val polyY = FloatArray(12)
    private val tmpX = FloatArray(12)
    private val tmpY = FloatArray(12)

    /**
     * Hitung bentuk lembaran.
     * [l], [t], [rt], [b]: kotak lembaran di layar. [texLeft]/[texTop]: posisi layar dari piksel (0,0) gambarnya.
     * [progress] 0 = belum dibalik, 1 = sudah terbalik seluruhnya. [radius] jari-jari gulungan (piksel layar).
     * [tilt] kemiringan awal sumbu (radian). [centerX]/[centerY]/[focalLen] untuk sedikit efek perspektif.
     */
    fun build(
        l: Float, t: Float, rt: Float, b: Float,
        texLeft: Float, texTop: Float,
        progress: Float, radius: Float, tilt: Float,
        centerX: Float, centerY: Float, focalLen: Float,
    ) {
        front.clear()
        back.clear()
        left = l; top = t; right = rt; bottom = b
        texDx = texLeft; texDy = texTop
        r = max(radius, 1f)
        cx = centerX; cy = centerY
        focal = max(focalLen, 4f * r)
        val e = progress.coerceIn(0f, 1f)
        // Sumbu mula-mula miring, lalu tegak saat lembaran sudah jauh terbalik.
        val phi = tilt * (1f - e) * (1f - e * 0.35f)
        nx = cos(phi); ny = sin(phi)
        tx = -ny; ty = nx

        val u0 = l * nx + t * ny
        val u1 = rt * nx + t * ny
        val u2 = rt * nx + b * ny
        val u3 = l * nx + b * ny
        val uMin = min(min(u0, u1), min(u2, u3))
        val uMax = max(max(u0, u1), max(u2, u3))
        val half = (PI * r).toFloat()
        s = uMax + (uMin - half - uMax) * e
        uStart = uMin
        uEnd = uMax
        lift = ((uMax - s) / (r * 2f)).coerceIn(0f, 1f)

        // Batas pita: datar | gulungan (dibagi halus, termasuk tepat di puncak) | punggung rebah.
        var nb = 0
        breaks[nb++] = uMin
        if (s > uMin && s < uMax) breaks[nb++] = s
        for (k in 1..curlBands) {
            val u = s + half * k / curlBands
            if (u > uMin && u < uMax) breaks[nb++] = u
        }
        breaks[nb++] = uMax
        // Sudah urut naik karena s ≤ s + ... dan semua di (uMin, uMax).
        for (i in 0 until nb - 1) {
            val a = breaks[i]
            val c = breaks[i + 1]
            if (c - a < 1e-3f) continue
            val mid = (a + c) * 0.5f - s
            val part = if (mid <= half * 0.5f) front else back
            band(a, c, part)
        }

        shadowFrom = s
        shadowPeak = project(s + r, r)
        shadowTo = shadowPeak + r * 1.4f + 10f
        val rest = uMax - s - half
        backEdge = if (rest > 0f) project(s - rest, 2f * r) else Float.NaN
    }

    /** Posisi sepanjang n setelah perspektif, untuk garis yang tingginya [z]. */
    private fun project(u: Float, z: Float): Float {
        val k = focal / (focal - z)
        val cu = cx * nx + cy * ny
        return cu + (u - cu) * k
    }

    /** Potong lembaran pada pita a ≤ u ≤ c lalu petakan ke bentuk melengkung. */
    private fun band(a: Float, c: Float, part: Part) {
        polyX[0] = left; polyY[0] = top
        polyX[1] = right; polyY[1] = top
        polyX[2] = right; polyY[2] = bottom
        polyX[3] = left; polyY[3] = bottom
        var n = clip(polyX, polyY, 4, tmpX, tmpY, a, keepAbove = true)
        n = clip(tmpX, tmpY, n, polyX, polyY, c, keepAbove = false)
        if (n < 3) return
        if (part.count + n > cap) return
        val base = part.count
        for (i in 0 until n) mapPoint(polyX[i], polyY[i], part)
        for (i in 1 until n - 1) {
            val k = part.indexCount
            part.indices[k] = base.toShort()
            part.indices[k + 1] = (base + i).toShort()
            part.indices[k + 2] = (base + i + 1).toShort()
            part.indexCount = k + 3
        }
    }

    /** Sutherland–Hodgman untuk satu batas u = [limit]. */
    private fun clip(
        inX: FloatArray, inY: FloatArray, n: Int, outX: FloatArray, outY: FloatArray,
        limit: Float, keepAbove: Boolean,
    ): Int {
        var m = 0
        for (i in 0 until n) {
            val j = (i + 1) % n
            val ui = inX[i] * nx + inY[i] * ny - limit
            val uj = inX[j] * nx + inY[j] * ny - limit
            val inI = if (keepAbove) ui >= 0f else ui <= 0f
            val inJ = if (keepAbove) uj >= 0f else uj <= 0f
            if (inI) { outX[m] = inX[i]; outY[m] = inY[i]; m++ }
            if (inI != inJ) {
                val f = ui / (ui - uj)
                outX[m] = inX[i] + (inX[j] - inX[i]) * f
                outY[m] = inY[i] + (inY[j] - inY[i]) * f
                m++
            }
        }
        return m
    }

    private fun mapPoint(px: Float, py: Float, part: Part) {
        val u = px * nx + py * ny
        val v = px * tx + py * ty
        val d = u - s
        val half = (PI * r).toFloat()
        val u2: Float
        val z: Float
        val shade = shadeAt(u)
        if (d <= 0f) {
            u2 = u; z = 0f
        } else if (d < half) {
            val th = d / r
            u2 = s + r * sin(th)
            z = r * (1f - cos(th))
        } else {
            u2 = s - (d - half)
            z = 2f * r
        }
        var x = u2 * nx + v * tx
        var y = u2 * ny + v * ty
        if (z > 0f) {
            val k = focal / (focal - z)
            x = cx + (x - cx) * k
            y = cy + (y - cy) * k
        }
        val i = part.count
        part.verts[i * 2] = x
        part.verts[i * 2 + 1] = y
        part.texs[i * 2] = px - texDx
        part.texs[i * 2 + 1] = py - texDy
        part.shade[i] = shade
        part.count = i + 1
    }
}
