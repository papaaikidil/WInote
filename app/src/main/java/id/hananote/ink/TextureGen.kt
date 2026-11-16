package id.hananote.ink

import id.hananote.data.PaperTexture
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

/**
 * Membuat ubin tekstur kertas (ARGB, tidak tembus pandang) yang bisa disambung ke segala arah tanpa
 * terlihat batasnya. Semua dibuat dengan perhitungan, jadi tidak menambah ukuran aplikasi.
 */
object TextureGen {

    fun pixels(t: PaperTexture): IntArray {
        val n = t.tilePx
        val img = Rgb(n, t.base)
        val rnd = Random(t.id.hashCode())
        when (t) {
            PaperTexture.RECYCLED -> {
                img.shade(fbm(n, rnd, intArrayOf(3, 6, 12), floatArrayOf(0.6f, 0.3f, 0.1f)), 0.035f)
                img.shade(grain(n, rnd), 0.03f)
                repeat(70) {
                    val c = intArrayOf(0x8A8478, 0x9A8466, 0x7D8798, 0xB7AE9C)[rnd.nextInt(4)]
                    img.fiber(rnd, rnd.nextFloat() * n, rnd.nextFloat() * n, 4f + rnd.nextFloat() * 10f, 0.5f, c, 0.25f + rnd.nextFloat() * 0.25f)
                }
                repeat(150) {
                    val c = intArrayOf(0x4A4640, 0x6B5236, 0x4F5C7A, 0x2E2C2A, 0x8C7B60)[rnd.nextInt(5)]
                    img.dot(rnd.nextFloat() * n, rnd.nextFloat() * n, 0.35f + rnd.nextFloat() * rnd.nextFloat() * 1.1f, c, 0.3f + rnd.nextFloat() * 0.45f)
                }
            }
            PaperTexture.KRAFT -> {
                img.shade(fbm(n, rnd, intArrayOf(4, 8, 16), floatArrayOf(0.5f, 0.3f, 0.2f)), 0.06f)
                img.shade(grain(n, rnd), 0.04f)
                repeat(520) {
                    val dark = rnd.nextFloat() < 0.6f
                    val c = if (dark) 0x6A4824 else 0xEBD3AA
                    val a = if (dark) 0.10f + rnd.nextFloat() * 0.16f else 0.12f + rnd.nextFloat() * 0.16f
                    img.fiber(rnd, rnd.nextFloat() * n, rnd.nextFloat() * n, 5f + rnd.nextFloat() * 22f, 0.35f, c, a)
                }
                repeat(40) {
                    img.dot(rnd.nextFloat() * n, rnd.nextFloat() * n, 0.4f + rnd.nextFloat() * 0.8f, 0x4A3018, 0.35f)
                }
            }
            PaperTexture.LINEN -> {
                // Guratan halus mendatar dan tegak seperti serat kain linen yang ditekan ke kertas.
                val h = Lattice(4, n / 2, rnd)
                val v = Lattice(n / 2, 4, rnd)
                val f = FloatArray(n * n)
                for (y in 0 until n) for (x in 0 until n) {
                    val u = x.toFloat() / n
                    val w = y.toFloat() / n
                    f[y * n + x] = h.at(u, w) * 0.55f + v.at(u, w) * 0.45f
                }
                img.shade(f, 0.05f)
                img.shade(fbm(n, rnd, intArrayOf(4, 8), floatArrayOf(0.6f, 0.4f)), 0.015f)
                img.shade(grain(n, rnd), 0.012f)
            }
            PaperTexture.WATERCOLOR -> {
                // Permukaan kertas cat air yang bergelombang, diterangi dari kiri atas.
                val height = fbm(n, rnd, intArrayOf(12, 24, 48, 96), floatArrayOf(0.4f, 0.3f, 0.2f, 0.1f))
                img.shade(emboss(n, height, 2), 0.085f)
                img.shade(fbm(n, rnd, intArrayOf(3, 6), floatArrayOf(0.6f, 0.4f)), 0.012f)
            }
            PaperTexture.PARCHMENT -> {
                val blot = fbm(n, rnd, intArrayOf(3, 6, 12, 24), floatArrayOf(0.45f, 0.3f, 0.15f, 0.1f))
                for (i in blot.indices) {
                    val k = ((blot[i] + 0.15f) * 1.6f).coerceIn(0f, 1f)
                    img.mix(i, 0xB98E52, k * k * 0.22f)
                }
                img.shade(fbm(n, rnd, intArrayOf(32, 64), floatArrayOf(0.6f, 0.4f)), 0.025f)
                img.shade(grain(n, rnd), 0.02f)
                repeat(120) {
                    img.fiber(rnd, rnd.nextFloat() * n, rnd.nextFloat() * n, 6f + rnd.nextFloat() * 18f, 0.3f, 0x9C7646, 0.08f + rnd.nextFloat() * 0.08f)
                }
            }
            PaperTexture.CANVAS -> {
                img.shade(weave(n, 8, rnd), 0.045f)
                img.shade(fbm(n, rnd, intArrayOf(4, 8), floatArrayOf(0.6f, 0.4f)), 0.02f)
                img.shade(grain(n, rnd), 0.015f)
            }
            PaperTexture.WASHI -> {
                img.shade(fbm(n, rnd, intArrayOf(3, 6, 12), floatArrayOf(0.5f, 0.3f, 0.2f)), 0.02f)
                repeat(230) {
                    val light = rnd.nextFloat() < 0.65f
                    val c = if (light) 0xFFFFFF else 0xD9CDB2
                    val a = if (light) 0.30f + rnd.nextFloat() * 0.25f else 0.10f + rnd.nextFloat() * 0.12f
                    img.fiber(rnd, rnd.nextFloat() * n, rnd.nextFloat() * n, 20f + rnd.nextFloat() * 70f, 0.22f, c, a)
                }
                img.shade(grain(n, rnd), 0.012f)
            }
            PaperTexture.BLACK -> {
                img.shade(fbm(n, rnd, intArrayOf(4, 8, 16), floatArrayOf(0.5f, 0.3f, 0.2f)), 0.08f)
                img.shade(grain(n, rnd), 0.07f)
                repeat(160) {
                    img.fiber(rnd, rnd.nextFloat() * n, rnd.nextFloat() * n, 4f + rnd.nextFloat() * 14f, 0.4f, 0x77777E, 0.10f + rnd.nextFloat() * 0.12f)
                }
            }
        }
        return img.pack()
    }

    // ---------- bahan dasar ----------

    /** Derau nilai yang berulang setiap satu ubin; [px] dan [py] jumlah kisi mendatar dan tegak. */
    private class Lattice(val px: Int, val py: Int, rnd: Random) {
        private val v = FloatArray(px * py) { rnd.nextFloat() * 2f - 1f }

        fun at(u: Float, w: Float): Float {
            val x = u * px
            val y = w * py
            val x0 = floor(x).toInt()
            val y0 = floor(y).toInt()
            val fx = x - x0
            val fy = y - y0
            val sx = fx * fx * (3f - 2f * fx)
            val sy = fy * fy * (3f - 2f * fy)
            val a = g(x0, y0)
            val b = g(x0 + 1, y0)
            val c = g(x0, y0 + 1)
            val d = g(x0 + 1, y0 + 1)
            return (a + (b - a) * sx) * (1f - sy) + (c + (d - c) * sx) * sy
        }

        private fun g(i: Int, j: Int): Float = v[Math.floorMod(j, py) * px + Math.floorMod(i, px)]
    }

    private fun fbm(n: Int, rnd: Random, periods: IntArray, amps: FloatArray): FloatArray {
        val out = FloatArray(n * n)
        for (k in periods.indices) {
            val l = Lattice(periods[k], periods[k], rnd)
            val a = amps[k]
            for (y in 0 until n) {
                val w = y.toFloat() / n
                for (x in 0 until n) out[y * n + x] += l.at(x.toFloat() / n, w) * a
            }
        }
        return out
    }

    private fun grain(n: Int, rnd: Random): FloatArray = FloatArray(n * n) { rnd.nextFloat() * 2f - 1f }

    /** Bayangan timbul dari peta tinggi, cahaya dari kiri atas. */
    private fun emboss(n: Int, h: FloatArray, d: Int): FloatArray {
        val out = FloatArray(n * n)
        var max = 1e-6f
        for (y in 0 until n) for (x in 0 until n) {
            val a = h[Math.floorMod(y - d, n) * n + Math.floorMod(x - d, n)]
            val b = h[Math.floorMod(y + d, n) * n + Math.floorMod(x + d, n)]
            val s = a - b
            out[y * n + x] = s
            if (kotlin.math.abs(s) > max) max = kotlin.math.abs(s)
        }
        for (i in out.indices) out[i] /= max
        return out
    }

    /** Anyaman benang: mendatar dan tegak bergantian di atas dan di bawah. */
    private fun weave(n: Int, period: Int, rnd: Random): FloatArray {
        val threads = n / period
        val hb = FloatArray(threads) { rnd.nextFloat() * 0.6f - 0.3f }
        val vb = FloatArray(threads) { rnd.nextFloat() * 0.6f - 0.3f }
        val along = Lattice(16, threads, rnd)
        val alongV = Lattice(threads, 16, rnd)
        val out = FloatArray(n * n)
        for (y in 0 until n) for (x in 0 until n) {
            val j = y / period
            val i = x / period
            val ty = ((y % period) + 0.5f) / period
            val tx = ((x % period) + 0.5f) / period
            val hp = sin(PI.toFloat() * ty)
            val vp = sin(PI.toFloat() * tx)
            val hOnTop = (i + j) % 2 == 0
            val u = x.toFloat() / n
            val w = y.toFloat() / n
            val v = if (hOnTop) {
                hp * (1f + hb[j] + along.at(u, (j + 0.5f) / threads) * 0.6f) * (0.8f + 0.2f * vp)
            } else {
                vp * (1f + vb[i] + alongV.at((i + 0.5f) / threads, w) * 0.6f) * (0.8f + 0.2f * hp)
            }
            out[y * n + x] = v * 2f - 1f
        }
        return out
    }

    /** Gambar dalam angka pecahan supaya pencampuran warnanya halus. */
    private class Rgb(val n: Int, base: Int) {
        val r = FloatArray(n * n) { ((base shr 16) and 0xFF).toFloat() }
        val g = FloatArray(n * n) { ((base shr 8) and 0xFF).toFloat() }
        val b = FloatArray(n * n) { (base and 0xFF).toFloat() }

        fun shade(f: FloatArray, amount: Float) {
            for (i in f.indices) {
                val k = 1f + f[i] * amount
                r[i] *= k; g[i] *= k; b[i] *= k
            }
        }

        fun mix(i: Int, color: Int, a: Float) {
            if (a <= 0f) return
            r[i] += (((color shr 16) and 0xFF) - r[i]) * a
            g[i] += (((color shr 8) and 0xFF) - g[i]) * a
            b[i] += ((color and 0xFF) - b[i]) * a
        }

        /** Tetesan warna di titik pecahan, dibagi ke empat piksel sekitarnya (melingkar ke sisi seberang). */
        fun splat(x: Float, y: Float, color: Int, a: Float) {
            val x0 = floor(x).toInt()
            val y0 = floor(y).toInt()
            val fx = x - x0
            val fy = y - y0
            mix(idx(x0, y0), color, a * (1 - fx) * (1 - fy))
            mix(idx(x0 + 1, y0), color, a * fx * (1 - fy))
            mix(idx(x0, y0 + 1), color, a * (1 - fx) * fy)
            mix(idx(x0 + 1, y0 + 1), color, a * fx * fy)
        }

        fun dot(cx: Float, cy: Float, radius: Float, color: Int, a: Float) {
            val rr = radius + 1f
            var y = floor(cy - rr).toInt()
            while (y <= cy + rr) {
                var x = floor(cx - rr).toInt()
                while (x <= cx + rr) {
                    val d = sqrt((x + 0.5f - cx) * (x + 0.5f - cx) + (y + 0.5f - cy) * (y + 0.5f - cy))
                    val cover = (radius + 0.5f - d).coerceIn(0f, 1f)
                    if (cover > 0f) mix(idx(x, y), color, a * cover)
                    x++
                }
                y++
            }
        }

        /** Serat pendek yang sedikit melengkung. */
        fun fiber(rnd: Random, sx: Float, sy: Float, length: Float, bend: Float, color: Int, a: Float) {
            var x = sx
            var y = sy
            var ang = rnd.nextFloat() * PI.toFloat() * 2f
            val steps = (length * 2f).toInt().coerceAtLeast(2)
            for (k in 0 until steps) {
                // Ujung serat lebih tipis.
                val t = k.toFloat() / steps
                val taper = sin(PI.toFloat() * t).coerceAtLeast(0.25f)
                splat(x, y, color, a * 0.55f * taper)
                ang += (rnd.nextFloat() - 0.5f) * bend
                x += cos(ang) * 0.5f
                y += sin(ang) * 0.5f
            }
        }

        private fun idx(x: Int, y: Int) = Math.floorMod(y, n) * n + Math.floorMod(x, n)

        fun pack(): IntArray = IntArray(n * n) { i ->
            val rr = r[i].toInt().coerceIn(0, 255)
            val gg = g[i].toInt().coerceIn(0, 255)
            val bb = b[i].toInt().coerceIn(0, 255)
            (0xFF shl 24) or (rr shl 16) or (gg shl 8) or bb
        }
    }
}
