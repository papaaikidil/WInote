package id.goresan.engine

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import kotlin.math.cbrt
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt

/*
 * Palet warna tanpa pustaka Android (bisa diuji di JVM biasa): impor ASE (Adobe), ACO (Photoshop), .swatches
 * (Procreate), GPL (GIMP), daftar hex; ekspor .swatches; palet otomatis dari foto; dan harmoni warna.
 * Warna disimpan sebagai ARGB Int (alfa selalu 255).
 */

class Palette(var name: String, val colors: MutableList<Int> = ArrayList())

object Palettes {
    /** Batas warna per palet (cukup untuk palet besar dari ASE/ACO). */
    const val MAX_COLORS = 300

    /** Baca berkas palet; [fileName] membantu menebak format teks. Kosong bila tidak dikenal. */
    fun parse(bytes: ByteArray, fileName: String = ""): List<Palette> {
        val base = fileName.substringAfterLast('/').substringBeforeLast('.').ifBlank { "Palet" }
        val out = when {
            bytes.size >= 4 && String(bytes, 0, 4, Charsets.ISO_8859_1) == "ASEF" -> parseAse(bytes, base)
            bytes.size >= 4 && bytes[0] == 'P'.code.toByte() && bytes[1] == 'K'.code.toByte() -> parseSwatches(bytes, base)
            bytes.size >= 4 && bytes[0].toInt() == 0 && (bytes[1].toInt() == 1 || bytes[1].toInt() == 2) && !looksText(bytes) -> parseAco(bytes, base)
            else -> parseText(String(bytes, Charsets.UTF_8), base)
        }
        return out.filter { it.colors.isNotEmpty() }.onEach { p ->
            if (p.colors.size > MAX_COLORS) p.colors.subList(MAX_COLORS, p.colors.size).clear()
        }
    }

    private fun looksText(b: ByteArray): Boolean {
        val n = min(b.size, 64)
        var printable = 0
        for (i in 0 until n) {
            val c = b[i].toInt() and 0xFF
            if (c == 9 || c == 10 || c == 13 || c in 32..126) printable++
        }
        return printable == n
    }

    // ---------------- ASE ----------------

    private fun parseAse(b: ByteArray, base: String): List<Palette> {
        val r = Be(b)
        r.skip(4)
        r.u16(); r.u16()
        val blocks = r.u32()
        val loose = Palette(base)
        val out = ArrayList<Palette>()
        var cur: Palette? = null
        var i = 0L
        while (i < blocks && r.left() >= 6) {
            i++
            val type = r.u16()
            val len = r.u32().toInt()
            val end = r.pos + len
            when (type) {
                0xC001 -> {
                    val name = if (len >= 2) r.utf16(r.u16()) else ""
                    cur = Palette(name.ifBlank { base }).also { out.add(it) }
                }
                0xC002 -> cur = null
                0x0001 -> {
                    r.utf16(r.u16())
                    val model = r.str(4)
                    val c = when (model) {
                        "RGB " -> rgbf(r.f32(), r.f32(), r.f32())
                        "CMYK" -> cmyk(r.f32(), r.f32(), r.f32(), r.f32())
                        "LAB " -> {
                            var l = r.f32()
                            if (l <= 1.0001f) l *= 100f
                            lab(l, r.f32(), r.f32())
                        }
                        "Gray" -> { val g = r.f32(); rgbf(g, g, g) }
                        else -> null
                    }
                    if (c != null) (cur ?: loose).colors.add(c)
                }
            }
            r.pos = end
        }
        if (loose.colors.isNotEmpty()) out.add(0, loose)
        return out
    }

    // ---------------- ACO ----------------

    private fun parseAco(b: ByteArray, base: String): List<Palette> {
        val r = Be(b)
        var best: Palette? = null
        while (r.left() >= 4) {
            val ver = r.u16()
            val n = r.u16()
            if (ver != 1 && ver != 2) break
            val p = Palette(base)
            var ok = true
            for (k in 0 until n) {
                if (r.left() < 10) { ok = false; break }
                val space = r.u16()
                val w = r.u16(); val x = r.u16(); val y = r.u16(); val z = r.u16()
                if (ver == 2) {
                    if (r.left() < 4) { ok = false; break }
                    val nl = r.u32().toInt()
                    if (nl < 0 || r.left() < nl * 2) { ok = false; break }
                    r.utf16(nl)
                }
                val c = when (space) {
                    0 -> rgbf(w / 65535f, x / 65535f, y / 65535f)
                    1 -> hsv(w / 65535f * 360f, x / 65535f, y / 65535f)
                    2 -> cmyk(1f - w / 65535f, 1f - x / 65535f, 1f - y / 65535f, 1f - z / 65535f)
                    7 -> lab(w / 100f, x.toShort() / 100f, y.toShort() / 100f)
                    8 -> { val g = 1f - w / 10000f; rgbf(g, g, g) }
                    9 -> cmyk(w / 10000f, x / 10000f, y / 10000f, z / 10000f)
                    else -> null
                }
                if (c != null) p.colors.add(c)
            }
            // Bagian versi 2 (dengan nama) lebih lengkap; dipakai bila terbaca utuh.
            if (ok || best == null) best = p
            if (!ok) break
        }
        return listOfNotNull(best)
    }

    // ---------------- .swatches (Procreate) ----------------

    private fun parseSwatches(b: ByteArray, base: String): List<Palette> {
        val out = ArrayList<Palette>()
        ZipInputStream(ByteArrayInputStream(b)).use { z ->
            while (true) {
                val e = z.nextEntry ?: break
                if (!e.name.endsWith(".json", ignoreCase = true)) continue
                val text = String(z.readBytes(), Charsets.UTF_8)
                val root = runCatching { Json(text).value() }.getOrNull() ?: continue
                val list = when (root) {
                    is List<*> -> root
                    is Map<*, *> -> listOf(root)
                    else -> emptyList()
                }
                for (o in list) {
                    val m = o as? Map<*, *> ?: continue
                    val p = Palette((m["name"] as? String)?.ifBlank { null } ?: base)
                    for (sw in m["swatches"] as? List<*> ?: emptyList<Any>()) {
                        val s = sw as? Map<*, *> ?: continue
                        val comp = s["components"] as? List<*>
                        val c = if (comp != null && comp.size >= 3) {
                            rgbf(num(comp[0]), num(comp[1]), num(comp[2]))
                        } else {
                            hsv(num(s["hue"]) * 360f, num(s["saturation"]), num(s["brightness"]))
                        }
                        p.colors.add(c)
                    }
                    out.add(p)
                }
            }
        }
        return out
    }

    private fun num(v: Any?): Float = when (v) {
        is Number -> v.toFloat()
        is String -> v.toFloatOrNull() ?: 0f
        else -> 0f
    }

    /** Berkas .swatches Procreate (zip berisi Swatches.json). */
    fun toSwatches(p: Palette): ByteArray {
        val sb = StringBuilder()
        sb.append("[{\"name\":").append(jsonStr(p.name)).append(",\"swatches\":[")
        p.colors.forEachIndexed { i, c ->
            if (i > 0) sb.append(',')
            val h = toHsv(c)
            sb.append("{\"hue\":").append(fmt(h[0] / 360f))
                .append(",\"saturation\":").append(fmt(h[1]))
                .append(",\"brightness\":").append(fmt(h[2]))
                .append(",\"alpha\":1,\"colorSpace\":0}")
        }
        sb.append("]}]")
        val bo = ByteArrayOutputStream()
        ZipOutputStream(bo).use { z ->
            z.putNextEntry(ZipEntry("Swatches.json"))
            z.write(sb.toString().toByteArray(Charsets.UTF_8))
            z.closeEntry()
        }
        return bo.toByteArray()
    }

    private fun fmt(f: Float) = String.format(java.util.Locale.US, "%.6f", f)

    private fun jsonStr(s: String): String {
        val sb = StringBuilder("\"")
        for (ch in s) {
            when {
                ch == '"' -> sb.append("\\\"")
                ch == '\\' -> sb.append("\\\\")
                ch < ' ' -> sb.append(String.format("\\u%04x", ch.code))
                else -> sb.append(ch)
            }
        }
        return sb.append('"').toString()
    }

    // ---------------- GPL / daftar hex ----------------

    private fun parseText(t: String, base: String): List<Palette> {
        val p = Palette(base)
        val lines = t.lines()
        val gpl = lines.firstOrNull()?.trim()?.startsWith("GIMP Palette") == true
        val hex = Regex("#?([0-9a-fA-F]{6})\\b")
        for ((i, raw) in lines.withIndex()) {
            val line = raw.trim()
            if (line.isEmpty() || line.startsWith("#") && !hex.matches(line.take(7))) continue
            if (gpl) {
                if (i == 0 || line.startsWith("Columns:")) continue
                if (line.startsWith("Name:")) { p.name = line.removePrefix("Name:").trim().ifBlank { base }; continue }
                val parts = line.split(Regex("\\s+"))
                if (parts.size >= 3) {
                    val rr = parts[0].toIntOrNull(); val gg = parts[1].toIntOrNull(); val bb = parts[2].toIntOrNull()
                    if (rr != null && gg != null && bb != null) p.colors.add(rgb(rr, gg, bb))
                }
            } else {
                for (m in hex.findAll(line)) p.colors.add((0xFF000000.toInt()) or m.groupValues[1].toInt(16))
            }
        }
        return listOf(p)
    }

    // ---------------- palet dari foto ----------------

    /**
     * Warna utama gambar [px] (ARGB) dengan k-means di ruang Lab: paling banyak [count] warna, diurutkan
     * menurut rona lalu kecerahan, tanpa warna yang nyaris kembar.
     */
    fun fromImage(px: IntArray, count: Int = 30): List<Int> {
        // Ambil paling banyak ±6000 sampel merata; piksel hampir transparan dilewati.
        val step = max(1, px.size / 6000)
        val samples = ArrayList<FloatArray>()
        var i = 0
        while (i < px.size) {
            val c = px[i]
            if ((c ushr 24) >= 128) samples.add(toLab(c))
            i += step
        }
        if (samples.isEmpty()) return emptyList()
        val k = min(count, samples.size)
        val rnd = java.util.Random(7)
        // Pusat awal k-means++.
        val centers = ArrayList<FloatArray>()
        centers.add(samples[rnd.nextInt(samples.size)].copyOf())
        val dist = FloatArray(samples.size) { Float.MAX_VALUE }
        while (centers.size < k) {
            val last = centers.last()
            var sum = 0.0
            for (j in samples.indices) {
                val d = d2(samples[j], last)
                if (d < dist[j]) dist[j] = d
                sum += dist[j]
            }
            if (sum <= 0.0) break
            var t = rnd.nextDouble() * sum
            var pick = samples.size - 1
            for (j in samples.indices) {
                t -= dist[j]
                if (t <= 0) { pick = j; break }
            }
            centers.add(samples[pick].copyOf())
        }
        val assign = IntArray(samples.size)
        val size = IntArray(centers.size)
        repeat(14) {
            for (j in samples.indices) {
                var bi = 0
                var bd = Float.MAX_VALUE
                for (c in centers.indices) {
                    val d = d2(samples[j], centers[c])
                    if (d < bd) { bd = d; bi = c }
                }
                assign[j] = bi
            }
            val acc = Array(centers.size) { FloatArray(3) }
            java.util.Arrays.fill(size, 0)
            for (j in samples.indices) {
                val a = acc[assign[j]]
                val s = samples[j]
                a[0] += s[0]; a[1] += s[1]; a[2] += s[2]
                size[assign[j]]++
            }
            for (c in centers.indices) if (size[c] > 0) {
                centers[c][0] = acc[c][0] / size[c]; centers[c][1] = acc[c][1] / size[c]; centers[c][2] = acc[c][2] / size[c]
            }
        }
        // Kelompok yang sangat kecil (mis. warna campuran di tepi benda) dan warna yang nyaris kembar dibuang;
        // kelompok besar didahulukan supaya warna utama yang tersisa.
        val minSize = max(1, (samples.size * 0.004f).toInt())
        val order = centers.indices.filter { size[it] > 0 }.sortedByDescending { size[it] }
        val res = ArrayList<FloatArray>()
        for (c in order) {
            if (size[c] < minSize && res.size >= 3) continue
            if (res.any { d2(it, centers[c]) < 49f }) continue
            res.add(centers[c])
        }
        return res.map { fromLab(it[0], it[1], it[2]) }
            .sortedWith(compareBy<Int>({ hueBucket(it) }, { -toHsv(it)[2] }))
    }

    /** Warna nyaris abu-abu dikelompokkan di depan; lainnya per 30° rona. */
    private fun hueBucket(c: Int): Int {
        val h = toHsv(c)
        return if (h[1] < 0.12f) -1 else (h[0] / 30f).toInt()
    }

    private fun d2(a: FloatArray, b: FloatArray): Float {
        val x = a[0] - b[0]; val y = a[1] - b[1]; val z = a[2] - b[2]
        return x * x + y * y + z * z
    }

    // ---------------- harmoni warna ----------------

    const val COMPLEMENTARY = 0
    const val SPLIT = 1
    const val ANALOGOUS = 2
    const val TRIADIC = 3
    const val TETRADIC = 4

    /** Selisih rona (derajat) warna-warna pasangan untuk tiap mode harmoni, di luar warna utama. */
    fun harmonyOffsets(mode: Int): FloatArray = when (mode) {
        COMPLEMENTARY -> floatArrayOf(180f)
        SPLIT -> floatArrayOf(150f, 210f)
        ANALOGOUS -> floatArrayOf(-30f, 30f)
        TRIADIC -> floatArrayOf(120f, 240f)
        else -> floatArrayOf(60f, 180f, 240f)
    }

    /** Warna-warna pasangan untuk rona [h] (0..360), saturasi [s], kecerahan [v]. */
    fun harmony(mode: Int, h: Float, s: Float, v: Float): List<Int> =
        harmonyOffsets(mode).map { o -> hsv(((h + o) % 360f + 360f) % 360f, s, v) }

    // ---------------- konversi warna ----------------

    fun rgb(r: Int, g: Int, b: Int): Int =
        (0xFF shl 24) or (r.coerceIn(0, 255) shl 16) or (g.coerceIn(0, 255) shl 8) or b.coerceIn(0, 255)

    fun rgbf(r: Float, g: Float, b: Float): Int = rgb((r * 255f).roundToInt(), (g * 255f).roundToInt(), (b * 255f).roundToInt())

    fun cmyk(c: Float, m: Float, y: Float, k: Float): Int {
        val kk = k.coerceIn(0f, 1f)
        return rgbf((1f - c.coerceIn(0f, 1f)) * (1f - kk), (1f - m.coerceIn(0f, 1f)) * (1f - kk), (1f - y.coerceIn(0f, 1f)) * (1f - kk))
    }

    fun hsv(h: Float, s: Float, v: Float): Int {
        val hh = ((h % 360f) + 360f) % 360f / 60f
        val ss = s.coerceIn(0f, 1f)
        val vv = v.coerceIn(0f, 1f)
        val i = hh.toInt()
        val f = hh - i
        val p = vv * (1f - ss)
        val q = vv * (1f - ss * f)
        val t = vv * (1f - ss * (1f - f))
        return when (i % 6) {
            0 -> rgbf(vv, t, p)
            1 -> rgbf(q, vv, p)
            2 -> rgbf(p, vv, t)
            3 -> rgbf(p, q, vv)
            4 -> rgbf(t, p, vv)
            else -> rgbf(vv, p, q)
        }
    }

    /** [h 0..360, s 0..1, v 0..1]. */
    fun toHsv(c: Int): FloatArray {
        val r = ((c shr 16) and 0xFF) / 255f
        val g = ((c shr 8) and 0xFF) / 255f
        val b = (c and 0xFF) / 255f
        val mx = max(r, max(g, b))
        val mn = min(r, min(g, b))
        val d = mx - mn
        var h = when {
            d == 0f -> 0f
            mx == r -> 60f * (((g - b) / d) % 6f)
            mx == g -> 60f * ((b - r) / d + 2f)
            else -> 60f * ((r - g) / d + 4f)
        }
        if (h < 0f) h += 360f
        return floatArrayOf(h, if (mx == 0f) 0f else d / mx, mx)
    }

    private fun lin(u: Float): Float = if (u <= 0.04045f) u / 12.92f else ((u + 0.055f) / 1.055f).toDouble().pow(2.4).toFloat()
    private fun gam(u: Float): Float = if (u <= 0.0031308f) 12.92f * u else (1.055 * u.toDouble().pow(1 / 2.4) - 0.055).toFloat()

    /** sRGB → CIE Lab (D65). */
    fun toLab(c: Int): FloatArray {
        val r = lin(((c shr 16) and 0xFF) / 255f)
        val g = lin(((c shr 8) and 0xFF) / 255f)
        val b = lin((c and 0xFF) / 255f)
        val x = (0.4124f * r + 0.3576f * g + 0.1805f * b) / 0.95047f
        val y = 0.2126f * r + 0.7152f * g + 0.0722f * b
        val z = (0.0193f * r + 0.1192f * g + 0.9505f * b) / 1.08883f
        fun f(t: Float) = if (t > 0.008856f) cbrt(t.toDouble()).toFloat() else 7.787f * t + 16f / 116f
        val fx = f(x); val fy = f(y); val fz = f(z)
        return floatArrayOf(116f * fy - 16f, 500f * (fx - fy), 200f * (fy - fz))
    }

    /** CIE Lab (D65; Lab dari ASE/ACO memakai D50, selisihnya kecil untuk palet) → sRGB. */
    fun lab(l: Float, a: Float, bb: Float): Int = fromLab(l, a, bb)

    fun fromLab(l: Float, a: Float, bb: Float): Int {
        val fy = (l + 16f) / 116f
        val fx = fy + a / 500f
        val fz = fy - bb / 200f
        fun inv(t: Float) = if (t * t * t > 0.008856f) t * t * t else (t - 16f / 116f) / 7.787f
        val x = inv(fx) * 0.95047f
        val y = inv(fy)
        val z = inv(fz) * 1.08883f
        val r = 3.2406f * x - 1.5372f * y - 0.4986f * z
        val g = -0.9689f * x + 1.8758f * y + 0.0415f * z
        val b = 0.0557f * x - 0.2040f * y + 1.0570f * z
        return rgbf(gam(r.coerceIn(0f, 1f)), gam(g.coerceIn(0f, 1f)), gam(b.coerceIn(0f, 1f)))
    }

    // ---------------- pembaca biner big-endian ----------------

    private class Be(val b: ByteArray) {
        var pos = 0
        fun left() = b.size - pos
        fun skip(n: Int) { pos += n }
        fun u8(): Int = if (pos < b.size) b[pos++].toInt() and 0xFF else { pos++; 0 }
        fun u16(): Int = (u8() shl 8) or u8()
        fun u32(): Long = ((u16().toLong() shl 16) or u16().toLong())
        fun f32(): Float = java.lang.Float.intBitsToFloat(u32().toInt())
        fun str(n: Int): String { val s = String(b, pos, min(n, max(0, left())), Charsets.ISO_8859_1); pos += n; return s }
        /** [n] satuan UTF-16 (termasuk nol penutup). */
        fun utf16(n: Int): String {
            val sb = StringBuilder()
            for (i in 0 until n) {
                val ch = u16()
                if (ch != 0) sb.append(ch.toChar())
            }
            return sb.toString()
        }
    }

    // ---------------- pengurai JSON kecil ----------------

    private class Json(val s: String) {
        var i = 0
        fun ws() { while (i < s.length && s[i].isWhitespace()) i++ }
        fun value(): Any? {
            ws()
            if (i >= s.length) return null
            return when (val c = s[i]) {
                '{' -> obj()
                '[' -> arr()
                '"' -> str()
                't' -> { i += 4; true }
                'f' -> { i += 5; false }
                'n' -> { i += 4; null }
                else -> if (c == '-' || c.isDigit()) number() else error("JSON tidak sah")
            }
        }
        fun obj(): Map<String, Any?> {
            val m = LinkedHashMap<String, Any?>()
            i++
            ws()
            if (s[i] == '}') { i++; return m }
            while (true) {
                ws()
                val k = str()
                ws()
                i++ // ':'
                m[k] = value()
                ws()
                if (s[i] == ',') { i++; continue }
                i++ // '}'
                return m
            }
        }
        fun arr(): List<Any?> {
            val l = ArrayList<Any?>()
            i++
            ws()
            if (s[i] == ']') { i++; return l }
            while (true) {
                l.add(value())
                ws()
                if (s[i] == ',') { i++; continue }
                i++ // ']'
                return l
            }
        }
        fun str(): String {
            val sb = StringBuilder()
            i++
            while (i < s.length) {
                val c = s[i++]
                if (c == '"') break
                if (c == '\\') {
                    val e = s[i++]
                    when (e) {
                        'n' -> sb.append('\n'); 't' -> sb.append('\t'); 'r' -> sb.append('\r')
                        'b' -> sb.append('\b'); 'f' -> sb.append('\u000C')
                        'u' -> { sb.append(s.substring(i, i + 4).toInt(16).toChar()); i += 4 }
                        else -> sb.append(e)
                    }
                } else sb.append(c)
            }
            return sb.toString()
        }
        fun number(): Double {
            val st = i
            while (i < s.length && (s[i].isDigit() || s[i] in "+-.eE")) i++
            return s.substring(st, i).toDouble()
        }
    }
}
