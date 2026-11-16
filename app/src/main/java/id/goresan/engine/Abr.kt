package id.goresan.engine

import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Pembaca kuas Photoshop (.abr), tanpa ketergantungan Android supaya bisa diuji sendiri.
 * - Versi 1 dan 2 (Photoshop lama): daftar kuas; yang dipakai hanya kuas bergambar (sampled).
 * - Versi 6, 7, dan 10 (Photoshop 7 ke atas): bagian "8BIM samp" berisi gambar ujung kuas, bagian "8BIM desc"
 *   berisi preset (nama, diameter, jarak, sudut, kebulatan) yang ditautkan ke gambar lewat UUID.
 * Gambar ujung: abu-abu 8 bit (16 bit diambil byte atasnya), 255 = cat penuh.
 */
object Abr {
    class Sample(val key: String, val name: String?, val spacing: Float?, val w: Int, val h: Int, val gray: ByteArray)

    /** Pengendali dinamika Photoshop: 0 mati, 1 memudar, 2 tekanan pena, 3 kemiringan pena, 4 roda, 5/6 arah, 7 putaran. */
    class Dyn(val control: Int, val jitter: Float)

    /** Satu preset kuas di bagian "desc" (bentuk, jarak, dinamika, tekstur), ditautkan ke gambar ujung lewat [uuid]. */
    class Preset(
        val name: String?,
        val uuid: String?,
        val computed: Boolean,
        val diameter: Float?,
        val hardness: Float?,
        val spacing: Float?,
        val angle: Float?,
        val roundness: Float?,
        val flipX: Boolean,
        val flipY: Boolean,
        val tipDynamics: Boolean,
        val minDiameter: Float?,
        val size: Dyn?,
        val angleDyn: Dyn?,
        val scatterOn: Boolean,
        val scatter: Dyn?,
        val count: Float?,
        val textureOn: Boolean,
        val textureId: String?,
        val textureScale: Float?,
        val textureDepth: Float?,
        val textureInvert: Boolean,
        /** "Texture Each Tip": tekstur per cap; mati (umumnya) = tekstur pada seluruh goresan. */
        val textureEachTip: Boolean,
        val paintDynamics: Boolean,
        val opacity: Dyn?,
        val flow: Dyn?,
        val minOpacity: Float?,
    )

    /** Pola (pattern) Photoshop: abu-abu 8 bit, w×h. */
    class Pattern(val id: String, val name: String, val w: Int, val h: Int, val gray: ByteArray)

    private const val MAX_SIDE = 12_000

    fun version(b: ByteArray): Int = if (b.size < 4) -1 else u16(b, 0)

    /**
     * Panggil [each] untuk setiap kuas bergambar, satu per satu (gambar sebelumnya boleh dibuang supaya hemat
     * memori). Mengembalikan jumlah kuas yang terbaca.
     */
    fun samples(b: ByteArray, each: (Sample) -> Unit): Int = when (version(b)) {
        1, 2 -> samplesV12(b, each)
        6, 7, 10 -> samplesV6(b, each)
        else -> 0
    }

    // ---------------- versi 1 & 2 ----------------

    private fun samplesV12(b: ByteArray, each: (Sample) -> Unit): Int {
        val ver = u16(b, 0)
        val count = u16(b, 2)
        var p = 4
        var n = 0
        repeat(count) {
            if (p + 6 > b.size) return n
            val type = u16(b, p)
            val size = u32(b, p + 2)
            val start = p + 6
            val next = start + size
            if (size < 0 || next > b.size) return n
            if (type == 2) {
                var q = start + 4 // misc
                val spacing = u16(b, q).toFloat()
                q += 2
                var name: String? = null
                if (ver == 2) {
                    val len = u32(b, q)
                    q += 4
                    if (len in 0..4096 && q + len * 2 <= next) {
                        name = utf16(b, q, len)
                        q += len * 2
                    }
                }
                q += 1 + 8 // antialias + batas pendek
                val s = image(b, q, next, "v$n", name, if (spacing > 0) spacing else null)
                if (s != null) { each(s); n++ }
            }
            p = next
        }
        return n
    }

    // ---------------- versi 6, 7, 10 ----------------

    private class Section(val key: String, val start: Int, val end: Int)

    private fun sections(b: ByteArray): List<Section> {
        val out = ArrayList<Section>()
        var p = 4
        while (p + 12 <= b.size) {
            val sig = ascii(b, p, 4)
            val key = ascii(b, p + 4, 4)
            val (len, head) = when (sig) {
                "8BIM" -> u32(b, p + 8).toLong() and 0xFFFFFFFFL to 12
                "8B64" -> if (p + 16 <= b.size) (u32(b, p + 8).toLong() shl 32) or (u32(b, p + 12).toLong() and 0xFFFFFFFFL) to 16 else break
                else -> break
            }
            val start = p + head
            val end = start + len
            if (len < 0 || end > b.size) {
                out.add(Section(key, start, b.size))
                break
            }
            out.add(Section(key, start, end.toInt()))
            p = end.toInt()
            if (p % 2 != 0) p++
        }
        return out
    }

    private fun samplesV6(b: ByteArray, each: (Sample) -> Unit): Int {
        val sub = u16(b, 2)
        var n = 0
        for (sec in sections(b)) {
            if (sec.key != "samp") continue
            var p = sec.start
            while (p + 4 <= sec.end) {
                val size = u32(b, p)
                if (size <= 0) break
                val start = p + 4
                val next = start + ((size + 3) / 4) * 4
                val end = min(start + size, sec.end)
                val keyLen = b[start].toInt() and 0xFF
                val key = if (start + 1 + keyLen <= end) ascii(b, start + 1, keyLen) else "s$n"
                val q = start + if (sub == 1) 47 else 301
                val s = image(b, q, end, key, null, null)
                if (s != null) { each(s); n++ }
                if (next <= p) break
                p = next
            }
        }
        return n
    }

    /**
     * Preset di bagian "desc" (urutan seperti di Photoshop). Deskriptor dibaca dengan mencari kunci lengkap
     * (panjang + nama + tipe) di dalam potongan tiap "brushPreset", jadi kunci bersarang (mis. nama pola di dalam
     * tekstur) tidak tertukar dengan nama preset.
     */
    fun presets(b: ByteArray): List<Preset> {
        if (version(b) < 6) return emptyList()
        val sec = sections(b).firstOrNull { it.key == "desc" } ?: return emptyList()
        val marks = ArrayList<Int>()
        val tag = "brushPreset".toByteArray(Charsets.ISO_8859_1)
        var i = indexOf(b, tag, sec.start, sec.end)
        while (i >= 0) {
            marks.add(i)
            i = indexOf(b, tag, i + tag.size, sec.end)
        }
        val out = ArrayList<Preset>()
        for ((k, m) in marks.withIndex()) {
            val e = if (k + 1 < marks.size) marks[k + 1] else sec.end
            out.add(readPreset(Chunk(b, m, e)))
        }
        return out
    }

    private class Chunk(val b: ByteArray, val start: Int, val end: Int) {
        fun key(k: String, type: String): ByteArray {
            val n = if (k.length == 4) 0 else k.length
            return byteArrayOf((n ushr 24).toByte(), (n ushr 16).toByte(), (n ushr 8).toByte(), n.toByte()) +
                (k + type).toByteArray(Charsets.ISO_8859_1)
        }
        fun find(k: String, type: String, from: Int = start): Int {
            val pat = key(k, type)
            val at = indexOf(b, pat, from, end)
            return if (at < 0) -1 else at + pat.size
        }
        fun unit(k: String, from: Int = start): Float? {
            val p = find(k, "UntF", from)
            return if (p < 0 || p + 12 > end) null else java.lang.Double.longBitsToDouble(u64(b, p + 4)).toFloat()
        }
        fun doub(k: String): Float? {
            val p = find(k, "doub")
            return if (p < 0 || p + 8 > end) null else java.lang.Double.longBitsToDouble(u64(b, p)).toFloat()
        }
        fun bool(k: String): Boolean? {
            val p = find(k, "bool")
            return if (p < 0 || p >= end) null else b[p].toInt() != 0
        }
        fun text(k: String, from: Int = start): String? {
            val p = find(k, "TEXT", from)
            if (p < 0 || p + 4 > end) return null
            val len = u32(b, p)
            if (len !in 0..4096 || p + 4 + len * 2 > end) return null
            return utf16(b, p + 4, len).trim()
        }
        fun dyn(k: String): Dyn? {
            val p = find(k, "Objc")
            if (p < 0) return null
            val t = find("bVTy", "long", p)
            val j = unit("jitter", p)
            val c = if (t < 0 || t + 4 > end) 0 else u32(b, t)
            return Dyn(c, j ?: 0f)
        }
    }

    private fun readPreset(c: Chunk): Preset {
        val sampled = indexOf(c.b, "sampledBrush".toByteArray(Charsets.ISO_8859_1), c.start, c.end) >= 0
        val txtr = c.find("Txtr", "Objc")
        return Preset(
            name = c.text("Nm  "),
            uuid = c.text("sampledData"),
            computed = !sampled && indexOf(c.b, "computedBrush".toByteArray(Charsets.ISO_8859_1), c.start, c.end) >= 0,
            diameter = c.unit("Dmtr"),
            hardness = c.unit("Hrdn"),
            spacing = c.unit("Spcn"),
            angle = c.unit("Angl"),
            roundness = c.unit("Rndn"),
            flipX = c.bool("flipX") ?: false,
            flipY = c.bool("flipY") ?: false,
            tipDynamics = c.bool("useTipDynamics") ?: false,
            minDiameter = c.unit("minimumDiameter"),
            size = c.dyn("szVr"),
            angleDyn = c.dyn("angleDynamics"),
            scatterOn = c.bool("useScatter") ?: false,
            scatter = c.dyn("scatterDynamics"),
            count = c.doub("Cnt "),
            textureOn = c.bool("useTexture") ?: false,
            textureId = if (txtr >= 0) c.text("Idnt", txtr) else c.text("Idnt"),
            textureScale = c.unit("textureScale") ?: c.unit("Scl "),
            textureDepth = c.unit("textureDepth"),
            textureInvert = c.bool("InvT") ?: false,
            textureEachTip = c.bool("TxtC") ?: false,
            paintDynamics = c.bool("usePaintDynamics") ?: false,
            opacity = c.dyn("opVr"),
            flow = c.dyn("prVr"),
            minOpacity = c.unit("minimumOpacity"),
        )
    }

    // ---------------- pola (pattern) ----------------

    /** Pola di bagian "8BIM patt" berkas .abr, menurut UUID-nya. */
    fun patterns(b: ByteArray): Map<String, Pattern> {
        if (version(b) < 6) return emptyMap()
        val out = HashMap<String, Pattern>()
        for (sec in sections(b)) {
            if (sec.key != "patt") continue
            var p = sec.start
            while (p + 4 <= sec.end) {
                val len = u32(b, p)
                if (len <= 0) break
                val start = p + 4
                val end = min(start + len, sec.end)
                runCatching { pattern(b, start, end) }.getOrNull()?.let { out[it.id] = it }
                p = start + len
                while ((p - sec.start) % 4 != 0) p++
            }
        }
        return out
    }

    /** Berkas pola Photoshop (.pat): "8BPT", versi, jumlah, lalu pola berurutan (tanpa awalan panjang). */
    fun patFile(b: ByteArray): List<Pattern> {
        if (b.size < 10 || ascii(b, 0, 4) != "8BPT") return emptyList()
        val count = u32(b, 6)
        val out = ArrayList<Pattern>()
        var p = 10
        repeat(count.coerceIn(0, 500)) {
            val r = runCatching { patternWithEnd(b, p, b.size) }.getOrNull() ?: return out
            r.first?.let { out.add(it) }
            p = r.second
        }
        return out
    }

    /** Pola GIMP (.pat, tanda "GPAT"): ukuran kepala, versi, lebar, tinggi, byte per piksel, "GPAT", nama, piksel. */
    fun gimpPat(b: ByteArray): Pattern? {
        if (b.size < 24 || ascii(b, 20, 4) != "GPAT") return null
        val head = u32(b, 0)
        val w = u32(b, 8)
        val h = u32(b, 12)
        val bpp = u32(b, 16)
        if (w <= 0 || h <= 0 || w > MAX_SIDE || h > MAX_SIDE || bpp !in 1..4 || head < 24 || head > b.size) return null
        if (head.toLong() + w.toLong() * h * bpp > b.size) return null
        val name = String(b, 24, max(0, head - 24), Charsets.UTF_8).trimEnd('\u0000').trim()
        val gray = ByteArray(w * h) {
            val q = head + it * bpp
            val v = if (bpp >= 3) {
                ((b[q].toInt() and 0xFF) * 299 + (b[q + 1].toInt() and 0xFF) * 587 + (b[q + 2].toInt() and 0xFF) * 114) / 1000
            } else {
                b[q].toInt() and 0xFF
            }
            // Bagian transparan dianggap putih (tanpa tinta).
            val a = if (bpp == 2 || bpp == 4) b[q + bpp - 1].toInt() and 0xFF else 255
            (255 - (255 - v) * a / 255).toByte()
        }
        return Pattern("gimp-$w-$h-${name.hashCode()}", name, w, h, gray)
    }

    private fun pattern(b: ByteArray, start: Int, end: Int): Pattern? = patternWithEnd(b, start, end)?.first

    /** Satu pola mulai di [q0] (kolom versi); hasilnya pola (null bila mode warnanya tidak didukung) dan posisi sesudahnya. */
    private fun patternWithEnd(b: ByteArray, q0: Int, end: Int): Pair<Pattern?, Int>? {
        var q = q0
        if (q + 12 > end) return null
        q += 4 // versi
        val mode = u32(b, q); q += 4
        q += 4 // titik: tinggi, lebar
        val nameLen = u32(b, q); q += 4
        if (nameLen !in 0..4096 || q + nameLen * 2 > end) return null
        val name = utf16(b, q, nameLen).trim()
        q += nameLen * 2
        val idLen = b[q].toInt() and 0xFF
        val id = ascii(b, q + 1, idLen)
        q += 1 + idLen
        var table: ByteArray? = null
        if (mode == 2) {
            if (q + 768 > end) return null
            table = b.copyOfRange(q, q + 768)
            q += 768
            q += 4 // kolom tambahan sesudah tabel warna (jumlah warna, indeks transparan)
        }
        // Virtual Memory Array List
        if (q + 28 > end) return null
        q += 4 // versi 3
        val vmLen = u32(b, q); q += 4
        val vmEnd = min(q + vmLen, end)
        val top = u32(b, q); val left = u32(b, q + 4); val bottom = u32(b, q + 8); val right = u32(b, q + 12)
        q += 16
        val nch = u32(b, q); q += 4
        val w = right - left
        val h = bottom - top
        if (w <= 0 || h <= 0 || w > MAX_SIDE || h > MAX_SIDE || nch !in 0..64) return null to vmEnd
        val chans = ArrayList<ByteArray>()
        for (c in 0 until nch + 2) {
            if (q + 8 > vmEnd) break
            val written = u32(b, q); q += 4
            if (written == 0) continue
            val clen = u32(b, q); q += 4
            if (clen <= 0) continue
            val cEnd = min(q + clen, vmEnd)
            val img = if (c < nch) channel(b, q, cEnd) else null
            if (img != null && img.second == w && img.third == h) chans.add(img.first)
            q = cEnd
        }
        val n = w * h
        val gray = when {
            (mode == 1 || mode == 7 || mode == 8) && chans.isNotEmpty() -> chans[0]
            mode == 3 && chans.size >= 3 -> ByteArray(n) {
                val r = chans[0][it].toInt() and 0xFF
                val g = chans[1][it].toInt() and 0xFF
                val bl = chans[2][it].toInt() and 0xFF
                ((r * 299 + g * 587 + bl * 114) / 1000).toByte()
            }
            mode == 2 && chans.isNotEmpty() && table != null -> ByteArray(n) {
                val k = chans[0][it].toInt() and 0xFF
                val r = table[k].toInt() and 0xFF
                val g = table[256 + k].toInt() and 0xFF
                val bl = table[512 + k].toInt() and 0xFF
                ((r * 299 + g * 587 + bl * 114) / 1000).toByte()
            }
            mode == 4 && chans.size >= 4 -> ByteArray(n) {
                // CMYK tersimpan terbalik (255 = tanpa tinta): perkiraan kecerahan.
                val c = 255 - (chans[0][it].toInt() and 0xFF)
                val m = 255 - (chans[1][it].toInt() and 0xFF)
                val y = 255 - (chans[2][it].toInt() and 0xFF)
                val k = 255 - (chans[3][it].toInt() and 0xFF)
                (255 - min(255, (c * 299 + m * 587 + y * 114) / 1000 + k)).toByte()
            }
            else -> null
        }
        return (if (gray == null) null else Pattern(id, name, w, h, gray)) to vmEnd
    }

    /** Satu saluran Virtual Memory Array: kedalaman, batas, kedalaman lagi, pemampatan, data. */
    private fun channel(b: ByteArray, q0: Int, end: Int): Triple<ByteArray, Int, Int>? {
        var q = q0
        if (q + 23 > end) return null
        val depth = u32(b, q); q += 4
        val top = u32(b, q); val left = u32(b, q + 4); val bottom = u32(b, q + 8); val right = u32(b, q + 12)
        q += 16
        q += 2 // kedalaman (diulang)
        val comp = b[q].toInt() and 0xFF; q += 1
        val w = right - left
        val h = bottom - top
        if (w <= 0 || h <= 0 || w > MAX_SIDE || h > MAX_SIDE || (depth != 1 && depth != 8 && depth != 16)) return null
        val s = decode(b, q, end, w, h, if (depth == 16) 2 else 1, comp) ?: return null
        return Triple(s, w, h)
    }

    // ---------------- gambar ujung ----------------

    /** Batas (atas, kiri, bawah, kanan), kedalaman, pemampatan, lalu data gambar mulai di [q]. */
    private fun image(b: ByteArray, q0: Int, end: Int, key: String, name: String?, spacing: Float?): Sample? {
        var q = q0
        if (q + 19 > end) return null
        val top = u32(b, q); val left = u32(b, q + 4); val bottom = u32(b, q + 8); val right = u32(b, q + 12)
        q += 16
        val depth = u16(b, q)
        val comp = b[q + 2].toInt() and 0xFF
        q += 3
        val w = right - left
        val h = bottom - top
        if (w <= 0 || h <= 0 || w > MAX_SIDE || h > MAX_SIDE || (depth != 8 && depth != 16)) return null
        val gray = decode(b, q, end, w, h, depth / 8, comp) ?: return null
        return Sample(key, name, spacing, w, h, gray)
    }

    /** Data gambar mentah atau PackBits per baris (panjang tiap baris 2 byte dulu); 16 bit diambil byte atasnya. */
    private fun decode(b: ByteArray, q0: Int, end: Int, w: Int, h: Int, bpp: Int, comp: Int): ByteArray? {
        var q = q0
        val row = w * bpp
        val raw = ByteArray(row * h)
        if (comp == 0) {
            if (q + raw.size > end) return null
            System.arraycopy(b, q, raw, 0, raw.size)
        } else if (comp == 1) {
            if (q + 2 * h > end) return null
            val lens = IntArray(h) { u16(b, q + 2 * it) }
            q += 2 * h
            var o: Int
            for (y in 0 until h) {
                val rowEnd = min(q + lens[y], end)
                val outEnd = (y + 1) * row
                o = y * row
                while (q < rowEnd && o < outEnd) {
                    var c = b[q++].toInt()
                    if (c >= 128) c -= 256
                    if (c < 0) {
                        if (c == -128) continue
                        if (q >= rowEnd) break
                        val v = b[q++]
                        val k = min(-c + 1, outEnd - o)
                        java.util.Arrays.fill(raw, o, o + k, v)
                        o += k
                    } else {
                        val k = min(c + 1, min(outEnd - o, rowEnd - q))
                        System.arraycopy(b, q, raw, o, k)
                        q += c + 1
                        o += k
                    }
                }
                q = rowEnd
            }
        } else if (comp == 2) {
            // ZIP (deflate) tanpa prediksi.
            val inf = java.util.zip.Inflater()
            try {
                inf.setInput(b, q, max(0, end - q))
                var o = 0
                while (o < raw.size && !inf.finished()) {
                    val k = inf.inflate(raw, o, raw.size - o)
                    if (k == 0 && (inf.needsInput() || inf.needsDictionary())) break
                    o += k
                }
                if (o < raw.size) return null
            } catch (e: java.util.zip.DataFormatException) {
                return null
            } finally {
                inf.end()
            }
        } else {
            return null
        }
        return if (bpp == 1) raw else ByteArray(w * h) { raw[it * bpp] }
    }

    /**
     * Gambar abu-abu w×h dipaskan ke persegi n×n (di tengah, perbandingan sisi dijaga). Diperkecil dengan
     * rata-rata luas (tidak bergerigi), diperbesar bilinear.
     */
    fun fit(gray: ByteArray, w: Int, h: Int, n: Int): ByteArray {
        val out = ByteArray(n * n)
        val s = min(n.toDouble() / w, n.toDouble() / h)
        val dw = max(1, (w * s).roundToInt())
        val dh = max(1, (h * s).roundToInt())
        val ox = (n - dw) / 2
        val oy = (n - dh) / 2
        if (s <= 1.0) {
            val sum = LongArray(dw * dh)
            val cnt = IntArray(dw * dh)
            for (y in 0 until h) {
                val ty = min(dh - 1, (y * s).toInt())
                val row = y * w
                val trow = ty * dw
                for (x in 0 until w) {
                    val t = trow + min(dw - 1, (x * s).toInt())
                    sum[t] += (gray[row + x].toInt() and 0xFF).toLong()
                    cnt[t]++
                }
            }
            for (y in 0 until dh) for (x in 0 until dw) {
                val t = y * dw + x
                if (cnt[t] > 0) out[(oy + y) * n + ox + x] = (sum[t] / cnt[t]).toInt().toByte()
            }
        } else {
            for (y in 0 until dh) {
                val sy = ((y + 0.5) / s - 0.5).coerceIn(0.0, (h - 1).toDouble())
                val y0 = floor(sy).toInt(); val y1 = min(h - 1, y0 + 1); val fy = sy - y0
                for (x in 0 until dw) {
                    val sx = ((x + 0.5) / s - 0.5).coerceIn(0.0, (w - 1).toDouble())
                    val x0 = floor(sx).toInt(); val x1 = min(w - 1, x0 + 1); val fx = sx - x0
                    fun g(xx: Int, yy: Int) = (gray[yy * w + xx].toInt() and 0xFF).toDouble()
                    val v = (g(x0, y0) * (1 - fx) + g(x1, y0) * fx) * (1 - fy) + (g(x0, y1) * (1 - fx) + g(x1, y1) * fx) * fy
                    out[(oy + y) * n + ox + x] = v.roundToInt().coerceIn(0, 255).toByte()
                }
            }
        }
        return out
    }

    /**
     * Apakah gambar ujung ini dimaksudkan sebagai **stempel** (dicap sekali, bukan diulang sepanjang goresan):
     * sangat lebar/besar, atau besar dan disimpan seukuran aslinya tanpa dinamika apa pun (coretan, ornamen, tulisan).
     */
    fun looksStamp(w: Int, h: Int, p: Preset?): Boolean {
        val long = max(w, h)
        val aspect = long.toFloat() / max(1, min(w, h))
        val huge = long >= 1200 || (aspect >= 3f && long >= 300)
        if (p == null) return huge || (long >= 600 && aspect >= 1.35f)
        val diameter = p.diameter ?: long.toFloat()
        // Diperkecil jauh dari ukuran aslinya: dipakai sebagai ujung kuas biasa yang diulang.
        if (diameter < long * 0.5f) return false
        if (huge) return true
        val dynamic = p.tipDynamics || p.scatterOn ||
            (p.paintDynamics && (p.opacity?.control == 2 || p.flow?.control == 2))
        return !dynamic && long >= 300 && diameter >= long * 0.8f
    }

    /** Cerminkan gambar mendatar ([horizontal]) atau tegak. */
    fun flip(g: ByteArray, w: Int, h: Int, horizontal: Boolean): ByteArray = ByteArray(w * h) {
        val x = it % w
        val y = it / w
        if (horizontal) g[y * w + (w - 1 - x)] else g[(h - 1 - y) * w + x]
    }

    /**
     * Pola diulang menjadi kira-kira persegi (mis. 96×64 → 2×3 ulangan = 192×192) dan sedikitnya 128 px, supaya
     * bisa jadi tekstur persegi yang berulang tanpa bentuknya gepeng. Hasil: gambar, lebar, tinggi.
     */
    fun tileSquare(g: ByteArray, w: Int, h: Int): Triple<ByteArray, Int, Int> {
        var rx = 1
        var ry = 1
        var best = Float.MAX_VALUE
        for (x in 1..8) {
            val y = Math.round(x.toFloat() * w / h).coerceIn(1, 8)
            val err = kotlin.math.abs(x * w - y * h).toFloat() / (x * w)
            if (err < best - 0.01f) { best = err; rx = x; ry = y }
        }
        val m = max(1, (128 + max(rx * w, ry * h) - 1) / max(rx * w, ry * h))
        rx *= m
        ry *= m
        val tw = rx * w
        val th = ry * h
        if (tw.toLong() * th > 16_000_000L) return Triple(g, w, h)
        if (rx == 1 && ry == 1) return Triple(g, w, h)
        val out = ByteArray(tw * th)
        for (y in 0 until th) {
            val sy = (y % h) * w
            for (x in 0 until tw) out[y * tw + x] = g[sy + x % w]
        }
        return Triple(out, tw, th)
    }

    /** Rentangkan kecerahan ke 0..255 (pola pudar jadi jelas sebagai kuas pola). */
    fun levels(g: ByteArray): ByteArray {
        var lo = 255
        var hi = 0
        for (v in g) {
            val x = v.toInt() and 0xFF
            if (x < lo) lo = x
            if (x > hi) hi = x
        }
        if (hi - lo < 8 || (lo == 0 && hi == 255)) return g
        return ByteArray(g.size) { (((g[it].toInt() and 0xFF) - lo) * 255 / (hi - lo)).toByte() }
    }

    /** Pola w×h ditarik ke persegi n×n (untuk tekstur berulang; pola memang bersambung di tepinya). */
    fun stretch(g: ByteArray, w: Int, h: Int, n: Int): ByteArray {
        val out = ByteArray(n * n)
        for (y in 0 until n) {
            val sy0 = y * h / n
            val sy1 = max(sy0 + 1, (y + 1) * h / n)
            for (x in 0 until n) {
                val sx0 = x * w / n
                val sx1 = max(sx0 + 1, (x + 1) * w / n)
                var sum = 0L
                var cnt = 0
                for (yy in sy0 until min(sy1, h)) for (xx in sx0 until min(sx1, w)) {
                    sum += g[yy * w + xx].toInt() and 0xFF
                    cnt++
                }
                out[y * n + x] = (if (cnt == 0) 0 else (sum / cnt).toInt()).toByte()
            }
        }
        return out
    }

    // ---------------- bantuan baca ----------------

    private fun indexOf(b: ByteArray, pat: ByteArray, from: Int, to: Int): Int {
        if (pat.isEmpty()) return -1
        val last = min(to, b.size) - pat.size
        var i = max(0, from)
        val f = pat[0]
        while (i <= last) {
            if (b[i] == f) {
                var k = 1
                while (k < pat.size && b[i + k] == pat[k]) k++
                if (k == pat.size) return i
            }
            i++
        }
        return -1
    }

    private fun u16(b: ByteArray, at: Int): Int =
        if (at + 2 > b.size) 0 else ((b[at].toInt() and 0xFF) shl 8) or (b[at + 1].toInt() and 0xFF)

    private fun u32(b: ByteArray, at: Int): Int =
        if (at + 4 > b.size) 0 else ((b[at].toInt() and 0xFF) shl 24) or ((b[at + 1].toInt() and 0xFF) shl 16) or
            ((b[at + 2].toInt() and 0xFF) shl 8) or (b[at + 3].toInt() and 0xFF)

    private fun u64(b: ByteArray, at: Int): Long = (u32(b, at).toLong() shl 32) or (u32(b, at + 4).toLong() and 0xFFFFFFFFL)

    private fun ascii(b: ByteArray, at: Int, n: Int): String =
        if (at + n > b.size) "" else String(b, at, n, Charsets.ISO_8859_1)

    private fun utf16(b: ByteArray, at: Int, chars: Int): String =
        String(b, at, chars * 2, Charsets.UTF_16BE).trimEnd('\u0000')
}
