package id.goresan.engine

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Tekstur kertas kanvas (cat air, sketsa, kanvas, bergaris, …) dan noise untuk kuas cat air, dibuat langsung di
 * perangkat. Tanpa ketergantungan Android supaya bisa diuji sendiri.
 *
 * Satu ubin kertas = RGBA n×n: RGB = pengali warna (bayangan tonjolan kertas / garis), A = tinggi permukaan kertas
 * (255 = puncak) yang dipakai kuas cat air untuk granulasi (pigmen mengendap di lekukan).
 */
object Paper {
    const val NONE = 0
    const val COLD = 1
    const val ROUGH = 2
    const val HOT = 3
    const val SKETCH = 4
    const val CANVAS = 5
    const val FIBER = 6
    const val LINED = 7
    const val GRID = 8
    const val DOTS = 9
    const val CUSTOM = 10

    val names = listOf(
        "Polos", "Cat air", "Cat air kasar", "Cat air halus", "Kertas sketsa", "Kanvas",
        "Kertas serat", "Bergaris", "Kotak-kotak", "Titik-titik", "Gambar sendiri",
    )

    /** Kekuatan bawaan tiap jenis saat pertama dipilih. */
    fun defaultStrength(type: Int): Float = when (type) {
        COLD -> 0.32f
        ROUGH -> 0.42f
        HOT -> 0.3f
        SKETCH -> 0.32f
        CANVAS -> 0.38f
        FIBER -> 0.45f
        LINED, GRID, DOTS -> 0.85f
        CUSTOM -> 0.5f
        else -> 0f
    }

    const val N = 512

    // ---------------- noise ----------------

    private fun hash(x: Int, y: Int, seed: Int): Float {
        var h = x * 374761393 + y * 668265263 + seed * 1442695041
        h = (h xor (h ushr 13)) * 1274126177
        h = h xor (h ushr 16)
        return (h and 0xFFFFFF) / 16777215f
    }

    private fun vnoise(x: Float, y: Float, period: Int, seed: Int): Float {
        val xi = floor(x).toInt()
        val yi = floor(y).toInt()
        val fx = x - xi
        val fy = y - yi
        val ux = fx * fx * (3f - 2f * fx)
        val uy = fy * fy * (3f - 2f * fy)
        val x0 = Math.floorMod(xi, period)
        val y0 = Math.floorMod(yi, period)
        val x1 = Math.floorMod(xi + 1, period)
        val y1 = Math.floorMod(yi + 1, period)
        val a = hash(x0, y0, seed)
        val b = hash(x1, y0, seed)
        val c = hash(x0, y1, seed)
        val d = hash(x1, y1, seed)
        val top = a + (b - a) * ux
        val bot = c + (d - c) * ux
        return top + (bot - top) * uy
    }

    /** fBm berulang (tileable) di ubin n×n; [base] = jumlah sel oktaf pertama. */
    private fun fbm(n: Int, base: Int, octaves: Int, seed: Int, gain: Float = 0.5f): FloatArray {
        val out = FloatArray(n * n)
        var amp = 1f
        var total = 0f
        var p = base
        for (o in 0 until octaves) {
            val s = p.toFloat() / n
            for (y in 0 until n) {
                val fy = y * s
                for (x in 0 until n) out[y * n + x] += amp * vnoise(x * s, fy, p, seed + o * 31)
            }
            total += amp
            amp *= gain
            p *= 2
        }
        for (i in out.indices) out[i] /= total
        return out
    }

    /** Jarak ke titik acak terdekat (Worley), berulang; satuan = ukuran sel. */
    private fun worley(n: Int, cells: Int, seed: Int): FloatArray {
        val cs = n.toFloat() / cells
        val px = FloatArray(cells * cells)
        val py = FloatArray(cells * cells)
        for (j in 0 until cells) for (i in 0 until cells) {
            px[j * cells + i] = (i + hash(i, j, seed)) * cs
            py[j * cells + i] = (j + hash(i, j, seed + 7)) * cs
        }
        val out = FloatArray(n * n)
        for (y in 0 until n) {
            val cy = floor(y / cs).toInt()
            for (x in 0 until n) {
                val cx = floor(x / cs).toInt()
                var best = Float.MAX_VALUE
                for (dj in -1..1) for (di in -1..1) {
                    val ci = cx + di
                    val cj = cy + dj
                    val k = Math.floorMod(cj, cells) * cells + Math.floorMod(ci, cells)
                    // Titik sel tetangga di luar ubin digeser satu ubin supaya tetap bersambung.
                    val ox = px[k] + (ci - Math.floorMod(ci, cells)) * cs
                    val oy = py[k] + (cj - Math.floorMod(cj, cells)) * cs
                    val dx = x - ox
                    val dy = y - oy
                    val d = dx * dx + dy * dy
                    if (d < best) best = d
                }
                out[y * n + x] = sqrt(best) / cs
            }
        }
        return out
    }

    private fun norm(a: FloatArray): FloatArray {
        var lo = Float.MAX_VALUE
        var hi = -Float.MAX_VALUE
        for (v in a) { lo = min(lo, v); hi = max(hi, v) }
        val r = if (hi - lo < 1e-6f) 1f else hi - lo
        for (i in a.indices) a[i] = (a[i] - lo) / r
        return a
    }

    /** Bayangan tonjolan kertas dengan cahaya dari kiri atas. */
    private fun shadeOf(h: FloatArray, n: Int, light: Float, base: Float, cavity: Float): FloatArray {
        val out = FloatArray(n * n)
        for (y in 0 until n) for (x in 0 until n) {
            val gx = (h[y * n + (x + 1) % n] - h[y * n + (x - 1 + n) % n]) * 0.5f
            val gy = (h[((y + 1) % n) * n + x] - h[((y - 1 + n) % n) * n + x]) * 0.5f
            val l = -(gx + gy) * light
            out[y * n + x] = (base + l * 0.5f - (1f - h[y * n + x]) * cavity).coerceIn(0f, 1f)
        }
        return out
    }

    /** Noise halus berulang 256×256 untuk bercak dan tepi kuas cat air. */
    fun noiseTile(n: Int = 256): ByteArray {
        val f = norm(fbm(n, 8, 4, 77))
        return ByteArray(n * n) { (f[it] * 255f + 0.5f).toInt().toByte() }
    }

    /** Ubin kertas jenis [type] (RGBA n×n). Untuk [NONE] dan pola garis, tingginya kertas cat air halus. */
    fun generate(type: Int, n: Int = N): ByteArray {
        val shade: FloatArray
        val height: FloatArray
        var tint: (Int, Int) -> FloatArray? = { _, _ -> null }
        when (type) {
            ROUGH -> {
                val w1 = worley(n, 16, 5)
                val w2 = worley(n, 34, 9)
                val f1 = fbm(n, 16, 4, 11)
                val f2 = fbm(n, 6, 3, 41)
                height = norm(FloatArray(n * n) {
                    0.6f * (1f - w1[it].coerceIn(0f, 1f).let { v -> v * sqrt(v) }) + 0.3f * (1f - w2[it].coerceIn(0f, 1f)) +
                        0.35f * f1[it] + 0.35f * f2[it]
                })
                shade = shadeOf(height, n, 11f, 0.93f, 0.08f)
            }
            HOT, LINED, GRID, DOTS -> {
                val f1 = fbm(n, 64, 3, 13)
                val f2 = fbm(n, 12, 3, 17)
                height = norm(FloatArray(n * n) { 0.6f * f1[it] + 0.4f * f2[it] })
                shade = if (type == HOT) shadeOf(height, n, 2.6f, 0.96f, 0.025f) else shadeOf(height, n, 1.6f, 0.97f, 0.015f)
            }
            SKETCH -> {
                // Gigi kertas halus: noise rapat sedikit memanjang mendatar.
                val f1 = fbm(n, 128, 2, 19)
                val f2 = fbm(n, 24, 3, 23)
                val fine = FloatArray(n * n)
                for (y in 0 until n) for (x in 0 until n) fine[y * n + x] = vnoise(x * 128f / n, y * 128f / n, 128, 29)
                height = norm(FloatArray(n * n) { 0.45f * f1[it] + 0.25f * f2[it] + 0.3f * fine[it] })
                shade = shadeOf(height, n, 5f, 0.95f, 0.06f)
            }
            CANVAS -> {
                // Anyaman linen: benang mendatar dan tegak bergantian, dengan ketebalan tak rata.
                val period = 8
                val cells = n / period
                val f = fbm(n, 32, 3, 31)
                val thick = fbm(n, 64, 2, 37)
                height = norm(FloatArray(n * n) {
                    val x = it % n
                    val y = it / n
                    val wx = 0.5f + 0.5f * sin(2f * PI.toFloat() * (x + 0.5f) / period)
                    val wy = 0.5f + 0.5f * sin(2f * PI.toFloat() * (y + 0.5f) / period)
                    val over = ((x / period) + (y / period)) % 2 == 0
                    val weave = if (over) wx * (0.7f + 0.3f * wy) else wy * (0.7f + 0.3f * wx)
                    0.55f * weave + 0.3f * f[it] + 0.15f * thick[it] + 0f * cells
                })
                shade = shadeOf(height, n, 2.6f, 0.94f, 0.05f)
            }
            FIBER -> {
                // Kertas serat/daur ulang: serat pendek acak dan bintik.
                val base = fbm(n, 32, 4, 43)
                val fib = FloatArray(n * n)
                val rnd = java.util.Random(47)
                repeat(n * n / 520) {
                    val x0 = rnd.nextFloat() * n
                    val y0 = rnd.nextFloat() * n
                    val ang = rnd.nextFloat() * PI.toFloat()
                    val len = 6f + rnd.nextFloat() * 22f
                    val dark = 0.25f + rnd.nextFloat() * 0.75f
                    val steps = len.toInt() * 2
                    for (k in 0..steps) {
                        val t = k / steps.toFloat() - 0.5f
                        val x = Math.floorMod((x0 + kotlin.math.cos(ang) * len * t).toInt(), n)
                        val y = Math.floorMod((y0 + sin(ang) * len * t).toInt(), n)
                        fib[y * n + x] = max(fib[y * n + x], dark)
                    }
                }
                repeat(n * n / 900) {
                    val x = rnd.nextInt(n)
                    val y = rnd.nextInt(n)
                    fib[y * n + x] = max(fib[y * n + x], 1f)
                }
                height = norm(FloatArray(n * n) { 0.8f * base[it] + 0.2f * (1f - fib[it]) })
                val sh = shadeOf(height, n, 4f, 0.95f, 0.05f)
                shade = FloatArray(n * n) { (sh[it] - fib[it] * 0.1f).coerceIn(0f, 1f) }
            }
            else -> {
                // COLD (cat air cold press) dan bawaan.
                val w1 = worley(n, 30, 5)
                val w2 = worley(n, 64, 9)
                val f1 = fbm(n, 32, 4, 11)
                val f2 = fbm(n, 8, 3, 41)
                height = norm(FloatArray(n * n) {
                    0.55f * (1f - w1[it].coerceIn(0f, 1f).let { v -> Math.pow(v.toDouble(), 1.3).toFloat() }) +
                        0.3f * (1f - w2[it].coerceIn(0f, 1f)) + 0.35f * f1[it] + 0.25f * f2[it]
                })
                shade = shadeOf(height, n, 6.5f, 0.94f, 0.05f)
            }
        }
        // Pola garis/kotak/titik di atas kertas halus (jarak 64 px pada skala 1).
        when (type) {
            LINED, GRID, DOTS -> {
                val step = n / 8f
                tint = { x, y ->
                    val dx = abs(((x + 0.5f) % step) - step / 2f)
                    val dy = abs(((y + 0.5f) % step) - step / 2f)
                    val lineY = 1f - ((step / 2f - dy) - 0.6f).coerceIn(0f, 1f)
                    val lineX = 1f - ((step / 2f - dx) - 0.6f).coerceIn(0f, 1f)
                    when (type) {
                        LINED -> if (lineY > 0f) floatArrayOf(1f - 0.45f * lineY, 1f - 0.22f * lineY, 1f - 0.06f * lineY) else null
                        GRID -> {
                            val m = max(lineX, lineY)
                            if (m > 0f) floatArrayOf(1f - 0.38f * m, 1f - 0.2f * m, 1f - 0.05f * m) else null
                        }
                        else -> {
                            val d = sqrt((step / 2f - dx) * (step / 2f - dx) + (step / 2f - dy) * (step / 2f - dy))
                            val m = (3.2f - d).coerceIn(0f, 1f)
                            if (m > 0f) floatArrayOf(1f - 0.6f * m, 1f - 0.6f * m, 1f - 0.55f * m) else null
                        }
                    }
                }
            }
        }
        val out = ByteArray(n * n * 4)
        for (y in 0 until n) for (x in 0 until n) {
            val i = y * n + x
            val s = if (type == NONE) 1f else shade[i]
            val t = tint(x, y)
            val r = s * (t?.get(0) ?: 1f)
            val g = s * (t?.get(1) ?: 1f)
            val b = s * (t?.get(2) ?: 1f)
            out[i * 4] = (r.coerceIn(0f, 1f) * 255f + 0.5f).toInt().toByte()
            out[i * 4 + 1] = (g.coerceIn(0f, 1f) * 255f + 0.5f).toInt().toByte()
            out[i * 4 + 2] = (b.coerceIn(0f, 1f) * 255f + 0.5f).toInt().toByte()
            out[i * 4 + 3] = (height[i].coerceIn(0f, 1f) * 255f + 0.5f).toInt().toByte()
        }
        return out
    }

    /**
     * Foto kertas (abu-abu n×n, sudah persegi) dibuat bersambung tanpa garis sambungan: bagian tepi dicampur dengan
     * salinan yang digeser setengah ubin.
     */
    fun seamless(gray: ByteArray, n: Int): ByteArray {
        val h = n / 2
        val out = ByteArray(n * n)
        for (y in 0 until n) for (x in 0 until n) {
            val t = max(abs(x - h + 0.5f), abs(y - h + 0.5f)) / h
            val w = ((t - 0.55f) / 0.45f).coerceIn(0f, 1f).let { it * it * (3f - 2f * it) }
            val a = gray[y * n + x].toInt() and 0xFF
            val b = gray[((y + h) % n) * n + (x + h) % n].toInt() and 0xFF
            out[y * n + x] = (a + (b - a) * w + 0.5f).toInt().toByte()
        }
        return out
    }

    /** Kertas sendiri (abu-abu n×n, sudah bersambung) jadi ubin RGBA; kecerahan diratakan supaya rata-ratanya terang. */
    fun fromGray(gray: ByteArray, n: Int): ByteArray {
        val src = FloatArray(n * n) { (gray[it].toInt() and 0xFF) / 255f }
        var mean = 0f
        for (v in src) mean += v
        mean /= src.size
        var lo = 1f
        for (v in src) lo = min(lo, v)
        val k = if (mean - lo < 1e-3f) 1f else min(1f, 0.35f / (mean - lo))
        val out = ByteArray(n * n * 4)
        for (i in 0 until n * n) {
            val sh = (0.92f + (src[i] - mean) * k).coerceIn(0f, 1f)
            val b = (sh * 255f + 0.5f).toInt().toByte()
            out[i * 4] = b; out[i * 4 + 1] = b; out[i * 4 + 2] = b
            out[i * 4 + 3] = (src[i] * 255f + 0.5f).toInt().toByte()
        }
        return out
    }
}
