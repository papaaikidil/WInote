package id.goresan.engine

import android.content.Context
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.io.File
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt

/**
 * Profil warna karya: sRGB, Display P3, atau CMYK (cetak). Kanvas CMYK tetap dilukis dalam sRGB, tetapi
 * ditampilkan dan diekspor seperti hasil cetak (soft proof); separasi ke C, M, Y, K memakai LUT dari model cetak
 * offset kertas coated (lihat tools/cmyk_lut.py). Display P3 = ruang RGB lebih lebar dengan kurva nada sRGB.
 */
object ColorProfile {
    const val SRGB = 0
    const val P3 = 1
    const val CMYK = 2

    fun name(p: Int) = when (p) {
        P3 -> "Display P3"
        CMYK -> "CMYK (cetak)"
        else -> "sRGB"
    }

    /** Ruang RGB tempat piksel disimpan: CMYK memakai sRGB. */
    fun rgbSpace(p: Int) = if (p == P3) P3 else SRGB

    /** Matriks linear (baris utama). */
    private val P3_TO_SRGB = floatArrayOf(
        1.2249401f, -0.2249404f, 0f,
        -0.0420569f, 1.0420571f, 0f,
        -0.0196376f, -0.0786361f, 1.0982735f,
    )
    private val SRGB_TO_P3 = floatArrayOf(
        0.8224621f, 0.1775380f, 0f,
        0.0331941f, 0.9668058f, 0f,
        0.0170827f, 0.0723974f, 0.9105199f,
    )

    /** Matriks ruang [from] → [to] (baris utama), null bila sama. */
    fun matrix(from: Int, to: Int): FloatArray? {
        val a = rgbSpace(from)
        val b = rgbSpace(to)
        if (a == b) return null
        return if (a == P3) P3_TO_SRGB else SRGB_TO_P3
    }

    /** Sama seperti [matrix] tetapi kolom utama (untuk uniform mat3 GL). */
    fun matrixGl(from: Int, to: Int): FloatArray? {
        val m = matrix(from, to) ?: return null
        return floatArrayOf(m[0], m[3], m[6], m[1], m[4], m[7], m[2], m[5], m[8])
    }

    private fun lin(u: Float): Float = if (u <= 0.04045f) u / 12.92f else ((u + 0.055f) / 1.055f).pow(2.4f)
    private fun enc(u: Float): Float {
        val v = u.coerceIn(0f, 1f)
        return if (v <= 0.0031308f) v * 12.92f else 1.055f * v.pow(1f / 2.4f) - 0.055f
    }

    /** Ubah warna ARGB dari ruang [from] ke [to] (tampilan sama; di luar gamut dipotong). */
    fun convert(argb: Int, from: Int, to: Int): Int {
        val m = matrix(from, to) ?: return argb
        val r = lin(((argb shr 16) and 0xFF) / 255f)
        val g = lin(((argb shr 8) and 0xFF) / 255f)
        val b = lin((argb and 0xFF) / 255f)
        val nr = enc(m[0] * r + m[1] * g + m[2] * b)
        val ng = enc(m[3] * r + m[4] * g + m[5] * b)
        val nb = enc(m[6] * r + m[7] * g + m[8] * b)
        return (argb and 0xFF000000.toInt()) or
            ((nr * 255f).roundToInt() shl 16) or ((ng * 255f).roundToInt() shl 8) or (nb * 255f).roundToInt()
    }

    // ---------------- CMYK ----------------

    const val N = 33
    @Volatile private var proofLut: ByteArray? = null
    @Volatile private var sepLut: ByteArray? = null

    fun proofLut(ctx: Context): ByteArray =
        proofLut ?: ctx.assets.open("warna/cmyk-proof.bin").use { it.readBytes() }.also { proofLut = it }

    fun sepLut(ctx: Context): ByteArray =
        sepLut ?: ctx.assets.open("warna/cmyk-sep.bin").use { it.readBytes() }.also { sepLut = it }

    /** Interpolasi trilinear LUT [ch] kanal pada warna sRGB (0..255). */
    private fun lookup(lut: ByteArray, ch: Int, r: Int, g: Int, b: Int, out: FloatArray) {
        val k = (N - 1) / 255f
        val fr = r * k; val fg = g * k; val fb = b * k
        val r0 = min(fr.toInt(), N - 2); val g0 = min(fg.toInt(), N - 2); val b0 = min(fb.toInt(), N - 2)
        val tr = fr - r0; val tg = fg - g0; val tb = fb - b0
        for (c in 0 until ch) {
            var acc = 0f
            for (dz in 0..1) for (dy in 0..1) for (dx in 0..1) {
                val w = (if (dx == 1) tr else 1 - tr) * (if (dy == 1) tg else 1 - tg) * (if (dz == 1) tb else 1 - tb)
                if (w == 0f) continue
                val i = (((b0 + dz) * N + (g0 + dy)) * N + (r0 + dx)) * ch + c
                acc += w * (lut[i].toInt() and 0xFF)
            }
            out[c] = acc / 255f
        }
    }

    /** Warna sRGB (ARGB) seperti hasil cetak CMYK. */
    fun proof(ctx: Context, argb: Int): Int {
        val o = FloatArray(4)
        lookup(proofLut(ctx), 3, (argb shr 16) and 0xFF, (argb shr 8) and 0xFF, argb and 0xFF, o)
        return (argb and 0xFF000000.toInt()) or
            ((o[0] * 255f).roundToInt() shl 16) or ((o[1] * 255f).roundToInt() shl 8) or (o[2] * 255f).roundToInt()
    }

    /** Persentase tinta C, M, Y, K (0..100) untuk warna sRGB. */
    fun separate(ctx: Context, argb: Int): IntArray {
        val o = FloatArray(4)
        lookup(sepLut(ctx), 4, (argb shr 16) and 0xFF, (argb shr 8) and 0xFF, argb and 0xFF, o)
        return IntArray(4) { (o[it] * 100f).roundToInt() }
    }

    /** Warna tidak bisa dicetak persis (selisih tampak dengan hasil cetaknya cukup besar). */
    fun outOfGamut(ctx: Context, argb: Int): Boolean {
        val p = proof(ctx, argb)
        val dr = ((argb shr 16) and 0xFF) - ((p shr 16) and 0xFF)
        val dg = ((argb shr 8) and 0xFF) - ((p shr 8) and 0xFF)
        val db = (argb and 0xFF) - (p and 0xFF)
        return dr * dr * 3 + dg * dg * 4 + db * db * 2 > 9 * 9 * 9
    }

    /**
     * Tulis TIFF CMYK 8 bit (PackBits, satu baris per strip) dari piksel RGBA premultiply ([rgba], baris 0 = atas)
     * yang sudah disusun di atas latar. Bagian transparan dianggap kertas putih.
     */
    fun writeCmykTiff(ctx: Context, file: File, w: Int, h: Int, dpi: Int, rgba: ByteBuffer, progress: (Float) -> Unit = {}) {
        val lut = sepLut(ctx)
        if (file.exists()) file.delete()
        RandomAccessFile(file, "rw").use { f ->
            fun le16(v: Int) { f.write(v and 0xFF); f.write((v shr 8) and 0xFF) }
            fun le32(v: Int) { le16(v and 0xFFFF); le16((v ushr 16) and 0xFFFF) }
            f.writeBytes("II")
            le16(42)
            le32(0) // offset IFD, diisi nanti
            val offsets = IntArray(h)
            val counts = IntArray(h)
            val row = ByteArray(w * 4)
            val rowIn = ByteArray(w * 4)
            val packed = ByteArray(w * 4 + w * 4 / 128 + 8)
            val cache = HashMap<Int, Int>(4096)
            val tmp = FloatArray(4)
            rgba.position(0)
            for (y in 0 until h) {
                rgba.get(rowIn, 0, w * 4)
                for (x in 0 until w) {
                    val o = x * 4
                    val a = rowIn[o + 3].toInt() and 0xFF
                    // Premultiply di atas kertas putih.
                    val r = ((rowIn[o].toInt() and 0xFF) + 255 - a).coerceAtMost(255)
                    val g = ((rowIn[o + 1].toInt() and 0xFF) + 255 - a).coerceAtMost(255)
                    val b = ((rowIn[o + 2].toInt() and 0xFF) + 255 - a).coerceAtMost(255)
                    val key = (r shl 16) or (g shl 8) or b
                    var v = cache[key]
                    if (v == null) {
                        lookup(lut, 4, r, g, b, tmp)
                        v = ((tmp[0] * 255f).roundToInt() shl 24) or ((tmp[1] * 255f).roundToInt() shl 16) or
                            ((tmp[2] * 255f).roundToInt() shl 8) or (tmp[3] * 255f).roundToInt()
                        if (cache.size > 200_000) cache.clear()
                        cache[key] = v
                    }
                    row[o] = (v ushr 24).toByte()
                    row[o + 1] = (v ushr 16).toByte()
                    row[o + 2] = (v ushr 8).toByte()
                    row[o + 3] = v.toByte()
                }
                val n = packBits(row, w * 4, packed)
                offsets[y] = f.filePointer.toInt()
                counts[y] = n
                f.write(packed, 0, n)
                if (y % 64 == 0) progress(y / h.toFloat())
            }
            rgba.position(0)
            // Data tambahan tag (BitsPerSample, resolusi, nama perangkat lunak, offset & panjang strip).
            if (f.filePointer % 2 != 0L) f.write(0)
            val bpsPos = f.filePointer.toInt()
            repeat(4) { le16(8) }
            val resPos = f.filePointer.toInt()
            le32(dpi); le32(1); le32(dpi); le32(1)
            val swPos = f.filePointer.toInt()
            f.writeBytes("WInote"); f.write(0); f.write(0)
            val offPos = f.filePointer.toInt()
            for (v in offsets) le32(v)
            val cntPos = f.filePointer.toInt()
            for (v in counts) le32(v)
            val ifd = f.filePointer.toInt()
            val tags = ArrayList<IntArray>() // tag, tipe, jumlah, nilai/offset
            tags.add(intArrayOf(256, 4, 1, w))
            tags.add(intArrayOf(257, 4, 1, h))
            tags.add(intArrayOf(258, 3, 4, bpsPos))
            tags.add(intArrayOf(259, 3, 1, 32773))
            tags.add(intArrayOf(262, 3, 1, 5))
            tags.add(intArrayOf(273, 4, h, if (h == 1) offsets[0] else offPos))
            tags.add(intArrayOf(277, 3, 1, 4))
            tags.add(intArrayOf(278, 4, 1, 1))
            tags.add(intArrayOf(279, 4, h, if (h == 1) counts[0] else cntPos))
            tags.add(intArrayOf(282, 5, 1, resPos))
            tags.add(intArrayOf(283, 5, 1, resPos + 8))
            tags.add(intArrayOf(284, 3, 1, 1))
            tags.add(intArrayOf(296, 3, 1, 2))
            tags.add(intArrayOf(305, 2, 7, swPos))
            tags.add(intArrayOf(332, 3, 1, 1))
            le16(tags.size)
            for (t in tags) {
                le16(t[0]); le16(t[1]); le32(t[2])
                if (t[1] == 3 && t[2] == 1) { le16(t[3]); le16(0) } else le32(t[3])
            }
            le32(0)
            f.seek(4)
            le32(ifd)
        }
        progress(1f)
    }

    /** PackBits (TIFF/PSD). Mengembalikan panjang hasil di [out]. */
    private fun packBits(src: ByteArray, n: Int, out: ByteArray): Int {
        var i = 0
        var o = 0
        while (i < n) {
            var run = 1
            while (i + run < n && run < 128 && src[i + run] == src[i]) run++
            if (run >= 3) {
                out[o++] = (1 - run).toByte()
                out[o++] = src[i]
                i += run
            } else {
                val start = i
                var len = 0
                while (i < n && len < 128) {
                    if (i + 2 < n && src[i] == src[i + 1] && src[i] == src[i + 2]) break
                    i++; len++
                }
                out[o++] = (len - 1).toByte()
                System.arraycopy(src, start, out, o, len)
                o += len
            }
        }
        return o
    }

    // ---------------- ICC Display P3 ----------------

    /** Profil ICC v2 Display P3 kecil (matriks + kurva nada sRGB) untuk disematkan di PSD. */
    val iccDisplayP3: ByteArray by lazy { buildIcc() }

    private fun buildIcc(): ByteArray {
        fun s15(v: Double): Int = (v * 65536.0).roundToInt()
        val tagData = ArrayList<Pair<String, ByteArray>>()
        fun bytes(block: DataOutputStream.() -> Unit): ByteArray {
            val bo = ByteArrayOutputStream()
            DataOutputStream(bo).use { it.block() }
            return bo.toByteArray()
        }
        fun xyz(x: Double, y: Double, z: Double) = bytes { writeBytes("XYZ "); writeInt(0); writeInt(s15(x)); writeInt(s15(y)); writeInt(s15(z)) }
        val desc = bytes {
            val t = "Display P3"
            writeBytes("desc"); writeInt(0); writeInt(t.length + 1); writeBytes(t); writeByte(0)
            writeInt(0); writeInt(0) // unicode: kosong
            writeShort(0); writeByte(0); write(ByteArray(67)) // scriptcode
        }
        val cprt = bytes { writeBytes("text"); writeInt(0); writeBytes("Public Domain"); writeByte(0) }
        val curv = bytes {
            writeBytes("curv"); writeInt(0); writeInt(1024)
            for (i in 0 until 1024) writeShort((lin(i / 1023f) * 65535f).roundToInt().coerceIn(0, 65535))
        }
        tagData.add("desc" to desc)
        tagData.add("wtpt" to xyz(0.964203, 1.0, 0.824905))
        tagData.add("rXYZ" to xyz(0.515102, 0.241182, -0.001050))
        tagData.add("gXYZ" to xyz(0.291965, 0.692236, 0.041882))
        tagData.add("bXYZ" to xyz(0.157153, 0.066582, 0.784378))
        tagData.add("rTRC" to curv)
        tagData.add("gTRC" to curv)
        tagData.add("bTRC" to curv)
        tagData.add("cprt" to cprt)
        // Susun: kepala 128 + tabel tag + data (kurva sama dipakai bersama).
        val tableSize = 4 + tagData.size * 12
        var off = 128 + tableSize
        val placed = LinkedHashMap<ByteArray, Int>()
        val entries = ArrayList<IntArray>()
        for ((_, data) in tagData) {
            val at = placed[data] ?: run {
                val p = off
                placed[data] = p
                off += (data.size + 3) / 4 * 4
                p
            }
            entries.add(intArrayOf(at, data.size))
        }
        val total = off
        return bytes {
            writeInt(total); writeInt(0); writeInt(0x02400000)
            writeBytes("mntr"); writeBytes("RGB "); writeBytes("XYZ ")
            writeShort(2026); writeShort(1); writeShort(1); writeShort(0); writeShort(0); writeShort(0)
            writeBytes("acsp"); writeBytes("APPL"); writeInt(0); writeInt(0); writeInt(0)
            writeInt(0); writeInt(0) // atribut perangkat
            writeInt(0) // intent: perseptual
            writeInt(s15(0.9642)); writeInt(s15(1.0)); writeInt(s15(0.8249))
            writeInt(0) // pembuat
            write(ByteArray(16)) // id
            write(ByteArray(28)) // cadangan
            writeInt(tagData.size)
            for ((i, t) in tagData.withIndex()) { writeBytes(t.first); writeInt(entries[i][0]); writeInt(entries[i][1]) }
            for ((data, _) in placed) {
                write(data)
                repeat((4 - data.size % 4) % 4) { writeByte(0) }
            }
        }
    }
}
