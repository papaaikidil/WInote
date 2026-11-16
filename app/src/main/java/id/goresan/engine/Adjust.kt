package id.goresan.engine

import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/** Jenis penyesuaian (urutan seperti menu Penyesuaian Procreate). */
object Adj {
    const val HSB = 0
    const val GAUSS = 1
    const val BALANCE = 2
    const val CURVES = 3
    const val GMAP = 4
    const val MOTION = 5
    const val PERSP = 6
    const val NOISE = 7
    const val SHARPEN = 8
    const val BLOOM = 9
    const val GLITCH = 10
    const val HALFTONE = 11
    const val CHROMA = 12
    const val LIQUIFY = 13
    const val CLONE = 14

    /** Pass dalam shader filter yang bukan penyesuaian tersendiri: ambil bagian terang untuk Bloom. */
    const val BRIGHT = 100

    // Mode Liquify
    const val LQ_PUSH = 0
    const val LQ_TWIRL_R = 1
    const val LQ_TWIRL_L = 2
    const val LQ_PINCH = 3
    const val LQ_EXPAND = 4
    const val LQ_CRYSTAL = 5
    const val LQ_EDGE = 6
    const val LQ_RECON = 7
}

/** Kurva warna: titik-titik kendali (0..1) → tabel 256 nilai, spline kubik monoton (tanpa lonjakan liar). */
object Curves {
    fun table(xs: FloatArray, ys: FloatArray): FloatArray {
        val n = min(xs.size, ys.size)
        val out = FloatArray(256)
        if (n == 0) {
            for (i in 0 until 256) out[i] = i / 255f
            return out
        }
        // Urutkan dan buang titik dengan x kembar.
        val idx = (0 until n).sortedBy { xs[it] }
        val px = ArrayList<Float>()
        val py = ArrayList<Float>()
        for (i in idx) {
            val x = xs[i].coerceIn(0f, 1f)
            val y = ys[i].coerceIn(0f, 1f)
            if (px.isNotEmpty() && x - px.last() < 1e-4f) { py[py.size - 1] = y; continue }
            px.add(x); py.add(y)
        }
        val m = px.size
        if (m == 1) {
            for (i in 0 until 256) out[i] = py[0]
            return out
        }
        val d = FloatArray(m - 1) { (py[it + 1] - py[it]) / (px[it + 1] - px[it]) }
        val t = FloatArray(m)
        t[0] = d[0]
        t[m - 1] = d[m - 2]
        for (k in 1 until m - 1) t[k] = if (d[k - 1] * d[k] <= 0f) 0f else (d[k - 1] + d[k]) / 2f
        // Fritsch–Carlson: batasi garis singgung supaya tiap ruas tetap monoton.
        for (k in 0 until m - 1) {
            if (d[k] == 0f) { t[k] = 0f; t[k + 1] = 0f; continue }
            val a = t[k] / d[k]
            val b = t[k + 1] / d[k]
            val s = a * a + b * b
            if (s > 9f) {
                val tau = 3f / kotlin.math.sqrt(s)
                t[k] = tau * a * d[k]
                t[k + 1] = tau * b * d[k]
            }
        }
        var seg = 0
        for (i in 0 until 256) {
            val x = i / 255f
            val y = when {
                x <= px[0] -> py[0]
                x >= px[m - 1] -> py[m - 1]
                else -> {
                    while (seg < m - 2 && x > px[seg + 1]) seg++
                    val h = px[seg + 1] - px[seg]
                    val u = (x - px[seg]) / h
                    val u2 = u * u
                    val u3 = u2 * u
                    (2 * u3 - 3 * u2 + 1) * py[seg] + (u3 - 2 * u2 + u) * h * t[seg] +
                        (-2 * u3 + 3 * u2) * py[seg + 1] + (u3 - u2) * h * t[seg + 1]
                }
            }
            out[i] = y.coerceIn(0f, 1f)
        }
        return out
    }

    /** LUT 256×1 RGBA: tiap kanal = kurva kanalnya, lalu kurva gamma (master). */
    fun lut(master: FloatArray, r: FloatArray, g: FloatArray, b: FloatArray): ByteArray {
        val out = ByteArray(256 * 4)
        fun look(t: FloatArray, v: Float) = t[(v * 255f).roundToInt().coerceIn(0, 255)]
        for (i in 0 until 256) {
            out[i * 4] = (look(master, r[i]) * 255f).roundToInt().toByte()
            out[i * 4 + 1] = (look(master, g[i]) * 255f).roundToInt().toByte()
            out[i * 4 + 2] = (look(master, b[i]) * 255f).roundToInt().toByte()
            out[i * 4 + 3] = 255.toByte()
        }
        return out
    }
}

/** Gradient Map: preset gradien (gelap → terang). */
object Gradients {
    class Preset(val name: String, val colors: IntArray, val stops: FloatArray)

    private fun p(name: String, vararg c: Long): Preset {
        val cols = IntArray(c.size) { c[it].toInt() }
        return Preset(name, cols, FloatArray(c.size) { if (c.size == 1) 0f else it / (c.size - 1f) })
    }

    val presets: List<Preset> = listOf(
        p("Noir", 0xFF0B0B0C, 0xFF5E5E62, 0xFFF4F4F2),
        p("Senja", 0xFF1B0A3C, 0xFF8A1E5F, 0xFFF2643A, 0xFFFFD27A),
        p("Panas", 0xFF000000, 0xFF5A0000, 0xFFE0301E, 0xFFFFB000, 0xFFFFFFDD),
        p("Mistis", 0xFF10002B, 0xFF3D1A78, 0xFF2AA7A1, 0xFFE8F8B0),
        p("Laut", 0xFF021526, 0xFF03346E, 0xFF6EACDA, 0xFFE2E2B6),
        p("Sepia", 0xFF2B1A0E, 0xFF8A5A2B, 0xFFF3E1C0),
        p("Neon", 0xFF0D0221, 0xFFFF2A6D, 0xFF05D9E8, 0xFFF7F7FF),
        p("Hutan", 0xFF0B1D0E, 0xFF2F5D34, 0xFFA3C35B, 0xFFF4F1C1),
    )

    /** Preset terakhir: hitam → warna kuas → putih. */
    fun fromColor(c: Int): Preset = Preset("Warna kuas", intArrayOf(0xFF000000.toInt(), c or 0xFF000000.toInt(), -1), floatArrayOf(0f, 0.5f, 1f))

    fun lut(p: Preset): ByteArray {
        val out = ByteArray(256 * 4)
        val n = p.colors.size
        for (i in 0 until 256) {
            val x = i / 255f
            var k = 0
            while (k < n - 2 && x > p.stops[k + 1]) k++
            val c: Int
            if (n == 1 || x <= p.stops[0]) c = p.colors[0]
            else if (x >= p.stops[n - 1]) c = p.colors[n - 1]
            else {
                val a = p.colors[k]
                val b = p.colors[k + 1]
                val t = ((x - p.stops[k]) / max(1e-5f, p.stops[k + 1] - p.stops[k])).coerceIn(0f, 1f)
                fun ch(sh: Int) = (((a shr sh) and 0xFF) * (1 - t) + ((b shr sh) and 0xFF) * t).roundToInt()
                c = (ch(16) shl 16) or (ch(8) shl 8) or ch(0)
            }
            out[i * 4] = ((c shr 16) and 0xFF).toByte()
            out[i * 4 + 1] = ((c shr 8) and 0xFF).toByte()
            out[i * 4 + 2] = (c and 0xFF).toByte()
            out[i * 4 + 3] = 255.toByte()
        }
        return out
    }
}
