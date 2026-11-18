package id.goresan.engine

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.util.zip.Inflater
import java.util.zip.ZipInputStream

/**
 * Mengenali berkas font dari isinya, bukan dari nama atau jenis berkas yang dilaporkan sistem (".ttf.otf",
 * berkas tanpa ekstensi, atau .otf yang dikira dokumen tetap terbaca):
 * - TTF, OTF, dan TTC dipakai apa adanya;
 * - font web WOFF diubah ke TTF/OTF biasa;
 * - ZIP (mis. unduhan Google Fonts) dibuka dan font di dalamnya diambil;
 * - berkas lain (PDF, gambar, WOFF2, ...) ditolak dengan pesan yang menjelaskan isinya.
 * Murni JVM supaya bisa diuji tanpa Android; pemeriksaan akhir (Typeface) dilakukan pemanggil.
 */
object FontFiles {
    /** Satu font siap simpan: [name] tanpa ekstensi, [ext] = ttf/otf/ttc. */
    class Font(val name: String, val ext: String, val bytes: ByteArray)

    /** Berkas bukan font yang bisa dipakai; [message] siap ditampilkan. */
    class NotFont(message: String) : Exception(message)

    /** Batas ukuran satu berkas yang dibaca. */
    const val MAX_BYTES = 40 shl 20
    private const val MAX_FONTS = 30

    /** Jenis font sfnt dari 4 bait pertama: "ttf", "otf", "ttc", atau null. */
    fun sfntExt(b: ByteArray): String? {
        if (b.size < 12) return null
        val m = magic(b)
        return when {
            m == "OTTO" -> "otf"
            m == "ttcf" -> "ttc"
            m == "true" -> "ttf"
            b[0].toInt() == 0 && b[1].toInt() == 1 && b[2].toInt() == 0 && b[3].toInt() == 0 -> "ttf"
            else -> null
        }
    }

    private fun magic(b: ByteArray): String = if (b.size >= 4) String(b, 0, 4, Charsets.ISO_8859_1) else ""

    /** 16 bait pertama dalam heksadesimal, untuk laporan teknis bila impor gagal. */
    fun head(b: ByteArray): String = b.take(16).joinToString(" ") { "%02x".format(it.toInt() and 0xFF) }

    /** Ambil font dari berkas bernama [name] berisi [bytes]. Melempar [NotFont] bila tidak ada font yang bisa dipakai. */
    fun extract(name: String, bytes: ByteArray): List<Font> {
        if (bytes.isEmpty()) throw NotFont("Berkasnya kosong.")
        val base = niceName(name)
        sfntExt(bytes)?.let { return listOf(Font(base, it, bytes)) }
        return when (magic(bytes)) {
            "wOFF" -> {
                val s = woffToSfnt(bytes)
                listOf(Font(base, sfntExt(s) ?: "ttf", s))
            }
            "wOF2" -> throw NotFont(
                "Ini font web WOFF2, belum bisa dipakai langsung. Cari versi .ttf atau .otf-nya (misalnya dari Google Fonts)."
            )
            "PK\u0003\u0004" -> fromZip(bytes)
            "%PDF" -> throw NotFont("Ini berkas PDF, bukan font. Pilih berkas font .ttf atau .otf.")
            "Rar!" -> throw NotFont("Ini berkas RAR. Ekstrak dulu, lalu pilih berkas .ttf atau .otf di dalamnya.")
            else -> throw NotFont(
                when {
                    isImage(bytes) -> "Ini berkas gambar, bukan font. Pilih berkas font .ttf atau .otf."
                    else -> "Berkas ini bukan font yang dikenali. Pilih berkas .ttf atau .otf."
                }
            )
        }
    }

    private fun isImage(b: ByteArray): Boolean {
        if (b.size < 4) return false
        val u = { i: Int -> b[i].toInt() and 0xFF }
        return (u(0) == 0x89 && u(1) == 0x50) || (u(0) == 0xFF && u(1) == 0xD8) || magic(b) == "GIF8" ||
            (magic(b) == "RIFF" && b.size >= 12 && String(b, 8, 4, Charsets.ISO_8859_1) == "WEBP")
    }

    /**
     * Nama font dari nama berkas: tanpa folder dan ekstensi (juga ekstensi ganda seperti ".ttf.otf"), dan tanpa
     * tambahan "VariableFont_wght" atau "[wght]" dari Google Fonts.
     */
    fun niceName(path: String): String {
        var n = path.substringAfterLast('/').substringAfterLast('\\')
        val exts = setOf("ttf", "otf", "ttc", "woff", "woff2", "zip", "font")
        while (n.contains('.') && n.substringAfterLast('.').lowercase() in exts) n = n.substringBeforeLast('.')
        n = n.replace(Regex("[-_ ]?VariableFont.*$", RegexOption.IGNORE_CASE), "")
            .replace(Regex("\\[[^]]*]"), "")
            .trim(' ', '-', '_')
        return n.ifBlank { "Font" }
    }

    /**
     * Font di dalam ZIP. Unduhan Google Fonts berisi font variabel dan folder "static" berisi tiap ketebalan: bila
     * ada folder static, yang diambil hanya gaya dasar (Regular, Bold, Italic, BoldItalic) supaya daftar font tidak
     * penuh; bila gaya dasar tidak ada, semua font static diambil.
     */
    private fun fromZip(bytes: ByteArray): List<Font> {
        val found = ArrayList<Pair<String, Font>>()
        var total = 0L
        var entries = 0
        try {
            ZipInputStream(ByteArrayInputStream(bytes)).use { z ->
                while (true) {
                    val e = z.nextEntry ?: break
                    if (++entries > 2000) break
                    val path = e.name.replace('\\', '/')
                    val leaf = path.substringAfterLast('/')
                    if (e.isDirectory || path.startsWith("__MACOSX/") || leaf.startsWith("._") || leaf.startsWith(".")) continue
                    val ext = leaf.substringAfterLast('.', "").lowercase()
                    if (ext !in setOf("ttf", "otf", "ttc", "woff", "")) continue
                    val data = readLimited(z, MAX_BYTES) ?: continue
                    total += data.size
                    if (total > 4L * MAX_BYTES) break
                    val font = runCatching { extract(leaf, data).firstOrNull() }.getOrNull() ?: continue
                    found.add(path to font)
                }
            }
        } catch (e: NotFont) {
            throw e
        } catch (e: Exception) {
            if (found.isEmpty()) throw NotFont("Berkas ZIP ini rusak atau tidak bisa dibuka.")
        }
        if (found.isEmpty()) throw NotFont("Di dalam ZIP ini tidak ada berkas font .ttf atau .otf.")
        val statics = found.filter { it.first.split('/').dropLast(1).any { d -> d.equals("static", true) } }
        val chosen = if (statics.isNotEmpty()) {
            val basic = statics.filter { styleOf(it.second.name) in BASIC }
            basic.ifEmpty { statics }
        } else {
            found
        }
        return chosen.take(MAX_FONTS).map { it.second }
    }

    private val BASIC = setOf("regular", "bold", "italic", "bolditalic")

    private fun styleOf(name: String): String = name.substringAfterLast('-', "").lowercase()

    /** Baca seluruh isi [z]; null bila lebih dari [limit] bait. */
    fun readLimited(z: java.io.InputStream, limit: Int = MAX_BYTES): ByteArray? {
        val out = ByteArrayOutputStream()
        val buf = ByteArray(64 * 1024)
        while (true) {
            val n = z.read(buf)
            if (n < 0) break
            out.write(buf, 0, n)
            if (out.size() > limit) return null
        }
        return out.toByteArray()
    }

    /** Ubah font web WOFF (versi 1) ke berkas sfnt (TTF/OTF) biasa: tiap tabel dibuka kompresinya lalu disusun ulang. */
    fun woffToSfnt(w: ByteArray): ByteArray {
        val bad = { NotFont("Font WOFF ini rusak dan tidak bisa dibaca.") }
        if (w.size < 44) throw bad()
        val b = ByteBuffer.wrap(w)
        val flavor = b.getInt(4)
        val num = b.getShort(12).toInt() and 0xFFFF
        if (num == 0 || num > 1024 || 44L + num * 20L > w.size) throw bad()
        val tags = IntArray(num)
        val sums = IntArray(num)
        val datas = arrayOfNulls<ByteArray>(num)
        for (i in 0 until num) {
            val p = 44 + i * 20
            val tag = b.getInt(p)
            val off = b.getInt(p + 4)
            val comp = b.getInt(p + 8)
            val orig = b.getInt(p + 12)
            if (off < 0 || comp < 0 || orig < 0 || orig > MAX_BYTES || off.toLong() + comp > w.size) throw bad()
            datas[i] = if (comp < orig) {
                val inf = Inflater()
                try {
                    inf.setInput(w, off, comp)
                    val out = ByteArray(orig)
                    var got = 0
                    while (got < orig && !inf.finished()) {
                        val n = inf.inflate(out, got, orig - got)
                        if (n == 0 && (inf.needsInput() || inf.needsDictionary())) break
                        got += n
                    }
                    if (got != orig) throw bad()
                    out
                } catch (e: java.util.zip.DataFormatException) {
                    throw bad()
                } finally {
                    inf.end()
                }
            } else {
                if (comp != orig) throw bad()
                w.copyOfRange(off, off + orig)
            }
            tags[i] = tag
            sums[i] = b.getInt(p + 16)
        }
        var es = 0
        while ((1 shl (es + 1)) <= num) es++
        val searchRange = (1 shl es) * 16
        val head = 12 + 16 * num
        var size = head.toLong()
        for (d in datas) size += (d!!.size + 3) and 3.inv()
        if (size > MAX_BYTES) throw bad()
        val out = ByteBuffer.allocate(size.toInt())
        out.putInt(flavor)
        out.putShort(num.toShort())
        out.putShort(searchRange.toShort())
        out.putShort(es.toShort())
        out.putShort((num * 16 - searchRange).toShort())
        var off = head
        for (i in 0 until num) {
            val len = datas[i]!!.size
            out.putInt(tags[i]); out.putInt(sums[i]); out.putInt(off); out.putInt(len)
            off += (len + 3) and 3.inv()
        }
        for (i in 0 until num) {
            val d = datas[i]!!
            out.put(d)
            repeat(((d.size + 3) and 3.inv()) - d.size) { out.put(0) }
        }
        return out.array()
    }
}
