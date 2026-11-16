package id.goresan.engine

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.sin
import kotlin.math.sqrt

/** Bentuk ujung kuas dan tekstur kertas dibuat langsung di perangkat, tanpa file gambar. */
object Procedural {

    private fun hash(x: Int, y: Int, seed: Int): Float {
        var h = x * 374761393 + y * 668265263 + seed * 1442695041
        h = (h xor (h ushr 13)) * 1274126177
        h = h xor (h ushr 16)
        return (h and 0xFFFFFF) / 16777215f
    }

    private fun smooth(e0: Float, e1: Float, x: Float): Float {
        val t = ((x - e0) / (e1 - e0)).coerceIn(0f, 1f)
        return t * t * (3f - 2f * t)
    }

    /** Value noise yang berulang setiap [period] sel. */
    private fun vnoise(x: Float, y: Float, period: Int, seed: Int): Float {
        val xi = floor(x).toInt()
        val yi = floor(y).toInt()
        val fx = x - xi
        val fy = y - yi
        val ux = fx * fx * (3f - 2f * fx)
        val uy = fy * fy * (3f - 2f * fy)
        val a = hash(Math.floorMod(xi, period), Math.floorMod(yi, period), seed)
        val b = hash(Math.floorMod(xi + 1, period), Math.floorMod(yi, period), seed)
        val c = hash(Math.floorMod(xi, period), Math.floorMod(yi + 1, period), seed)
        val d = hash(Math.floorMod(xi + 1, period), Math.floorMod(yi + 1, period), seed)
        val top = a + (b - a) * ux
        val bot = c + (d - c) * ux
        return top + (bot - top) * uy
    }

    /** fBm yang bisa diulang (tileable) pada gambar berukuran [n]. */
    private fun fbm(x: Int, y: Int, n: Int, basePeriod: Int, octaves: Int, seed: Int): Float {
        var sum = 0f
        var amp = 1f
        var total = 0f
        var p = basePeriod
        for (o in 0 until octaves) {
            sum += amp * vnoise(x.toFloat() * p / n, y.toFloat() * p / n, p, seed + o * 31)
            total += amp
            amp *= 0.5f
            p *= 2
        }
        return sum / total
    }

    fun tip(shape: TipShape, n: Int = 256): ByteArray {
        val out = ByteArray(n * n)
        val c = (n - 1) / 2f
        val r = n / 2f - 1f
        val dots = if (shape == TipShape.SPLATTER) splatterDots() else emptyList()
        for (y in 0 until n) for (x in 0 until n) {
            val dx = (x - c) / r
            val dy = (y - c) / r
            val d = sqrt(dx * dx + dy * dy)
            val a: Float = when (shape) {
                TipShape.HARD_ROUND -> (r - d * r + 0.5f).coerceIn(0f, 1f)
                TipShape.SOFT_ROUND -> 1f - smooth(0.45f, 1f, d)
                TipShape.AIRBRUSH -> if (d >= 1f) 0f else exp(-d * d * 4.5f) * (1f - smooth(0.8f, 1f, d))
                TipShape.PENCIL -> (1f - smooth(0.7f, 1f, d)) * (0.45f + 0.55f * hash(x, y, 7))
                TipShape.CHARCOAL -> {
                    val ang = (atan2(dy, dx) / (2f * PI.toFloat()) + 0.5f) * 12f
                    val rr = 0.78f + 0.2f * vnoise(ang, 0.5f, 12, 3)
                    (1f - smooth(rr - 0.12f, rr, d)) * (0.35f + 0.65f * vnoise(x / 9f, y / 9f, 1000, 11))
                }
                TipShape.INK_ROUGH -> {
                    val ang = (atan2(dy, dx) / (2f * PI.toFloat()) + 0.5f) * 16f
                    val rr = 0.86f + 0.12f * vnoise(ang, 0.5f, 16, 5)
                    ((rr - d) * r * 0.9f).coerceIn(0f, 1f)
                }
                TipShape.FLAT -> {
                    val edge = (1f - smooth(0.82f, 1f, abs(dx))) * (1f - smooth(0.82f, 1f, abs(dy)))
                    val streak = 0.45f + 0.55f * vnoise(x / 3f, y / 40f, 1000, 5)
                    edge * streak
                }
                TipShape.SPLATTER -> {
                    var m = 0f
                    for (dot in dots) {
                        val ex = dx - dot[0]
                        val ey = dy - dot[1]
                        val dd = sqrt(ex * ex + ey * ey)
                        m = max(m, 1f - smooth(dot[2] * 0.75f, dot[2], dd))
                    }
                    m
                }
                TipShape.CHALK -> {
                    val nz = vnoise(x / 5f, y / 5f, 1000, 21) * 0.6f + hash(x, y, 23) * 0.4f + 0.15f
                    (1f - smooth(0.65f, 1f, d)) * smooth(0.35f, 0.65f, nz)
                }
            }
            out[y * n + x] = (a.coerceIn(0f, 1f) * 255f + 0.5f).toInt().toByte()
        }
        return out
    }

    private fun splatterDots(): List<FloatArray> {
        val rnd = java.util.Random(42)
        val list = ArrayList<FloatArray>()
        for (i in 0 until 28) {
            val ang = rnd.nextFloat() * 2f * PI.toFloat()
            val rad = sqrt(rnd.nextFloat()) * 0.82f
            val size = 0.03f + rnd.nextFloat() * rnd.nextFloat() * 0.13f
            list.add(floatArrayOf(kotlin.math.cos(ang) * rad, sin(ang) * rad, size))
        }
        return list
    }

    fun grain(g: Grain, n: Int = 512): ByteArray {
        val out = ByteArray(n * n)
        for (y in 0 until n) for (x in 0 until n) {
            val v: Float = when (g) {
                Grain.NONE -> 1f
                Grain.PAPER -> {
                    val f = fbm(x, y, n, 16, 5, 101)
                    0.62f + (f - 0.5f) * 2.4f
                }
                Grain.CANVAS -> {
                    val wx = 0.5f + 0.5f * sin(2f * PI.toFloat() * x / 8f)
                    val wy = 0.5f + 0.5f * sin(2f * PI.toFloat() * y / 8f)
                    val weave = if (((x / 8) + (y / 8)) % 2 == 0) wx else wy
                    0.55f + 0.35f * weave + 0.25f * (fbm(x, y, n, 32, 2, 77) - 0.5f)
                }
                Grain.ROUGH -> smooth(0.32f, 0.68f, fbm(x, y, n, 32, 4, 203))
                Grain.NOISE -> hash(x, y, 99)
            }
            out[y * n + x] = (v.coerceIn(0f, 1f) * 255f + 0.5f).toInt().toByte()
        }
        return out
    }
}
