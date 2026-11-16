package id.goresan.engine

import java.io.File
import java.io.RandomAccessFile
import java.nio.ByteBuffer

/** Penulis TIFF RGB/RGBA 8 bit (PackBits, satu strip per baris), dengan DPI dan profil ICC bila ada. */
object Tiff {
    /**
     * [rgba] premultiplied, baris 0 = atas. [alpha] true: 4 sampel dengan alfa terpisah (tidak premultiplied);
     * false: 3 sampel (gambar dianggap sudah di atas latar).
     */
    fun write(file: File, w: Int, h: Int, dpi: Int, rgba: ByteBuffer, alpha: Boolean, icc: ByteArray? = null, progress: (Float) -> Unit = {}) {
        if (file.exists()) file.delete()
        val spp = if (alpha) 4 else 3
        RandomAccessFile(file, "rw").use { f ->
            fun le16(v: Int) { f.write(v and 0xFF); f.write((v shr 8) and 0xFF) }
            fun le32(v: Int) { le16(v and 0xFFFF); le16((v ushr 16) and 0xFFFF) }
            f.writeBytes("II")
            le16(42)
            le32(0)
            val offsets = IntArray(h)
            val counts = IntArray(h)
            val rowIn = ByteArray(w * 4)
            val row = ByteArray(w * spp)
            val packed = ByteArray(w * spp * 2 + 16)
            val out = java.io.ByteArrayOutputStream(64 * 1024)
            var pos = 8
            rgba.position(0)
            for (y in 0 until h) {
                rgba.get(rowIn, 0, w * 4)
                for (x in 0 until w) {
                    val o = x * 4
                    val a = rowIn[o + 3].toInt() and 0xFF
                    val d = x * spp
                    for (c in 0 until 3) {
                        val v = rowIn[o + c].toInt() and 0xFF
                        row[d + c] = (if (!alpha || a == 255) v else if (a == 0) 0 else minOf(255, (v * 255 + a / 2) / a)).toByte()
                    }
                    if (alpha) row[d + 3] = a.toByte()
                }
                val n = packBits(row, row.size, packed)
                out.write(packed, 0, n)
                offsets[y] = pos
                counts[y] = n
                pos += n
                if (out.size() > 1 shl 20) { f.write(out.toByteArray()); out.reset() }
                if (y % 64 == 0) progress(y / h.toFloat())
            }
            f.write(out.toByteArray())
            rgba.position(0)
            if (f.filePointer % 2 != 0L) f.write(0)
            val bpsPos = f.filePointer.toInt()
            repeat(spp) { le16(8) }
            val resPos = f.filePointer.toInt()
            le32(dpi); le32(1); le32(dpi); le32(1)
            val swPos = f.filePointer.toInt()
            f.writeBytes("WInote"); f.write(0); f.write(0)
            val offPos = f.filePointer.toInt()
            for (v in offsets) le32(v)
            val cntPos = f.filePointer.toInt()
            for (v in counts) le32(v)
            var iccPos = 0
            if (icc != null) {
                iccPos = f.filePointer.toInt()
                f.write(icc)
                if (f.filePointer % 2 != 0L) f.write(0)
            }
            val ifd = f.filePointer.toInt()
            val tags = ArrayList<IntArray>()
            tags.add(intArrayOf(256, 4, 1, w))
            tags.add(intArrayOf(257, 4, 1, h))
            tags.add(intArrayOf(258, 3, spp, bpsPos))
            tags.add(intArrayOf(259, 3, 1, 32773))
            tags.add(intArrayOf(262, 3, 1, 2))
            tags.add(intArrayOf(273, 4, h, if (h == 1) offsets[0] else offPos))
            tags.add(intArrayOf(277, 3, 1, spp))
            tags.add(intArrayOf(278, 4, 1, 1))
            tags.add(intArrayOf(279, 4, h, if (h == 1) counts[0] else cntPos))
            tags.add(intArrayOf(282, 5, 1, resPos))
            tags.add(intArrayOf(283, 5, 1, resPos + 8))
            tags.add(intArrayOf(284, 3, 1, 1))
            tags.add(intArrayOf(296, 3, 1, 2))
            tags.add(intArrayOf(305, 2, 7, swPos))
            if (alpha) tags.add(intArrayOf(338, 3, 1, 2))
            if (icc != null) tags.add(intArrayOf(34675, 7, icc.size, iccPos))
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

    /** PackBits; [out] harus ≥ 2·n + 16. */
    private fun packBits(src: ByteArray, n: Int, out: ByteArray): Int {
        var i = 0
        var o = 0
        while (i < n) {
            var run = 1
            while (i + run < n && run < 128 && src[i + run] == src[i]) run++
            if (run >= 3 || (run == 2 && i + 2 >= n)) {
                out[o++] = (1 - run).toByte()
                out[o++] = src[i]
                i += run
            } else {
                // Literal sampai bertemu ulangan ≥ 3 atau 128 byte.
                val start = i
                var len = 0
                while (i < n && len < 128) {
                    if (i + 2 < n && src[i] == src[i + 1] && src[i] == src[i + 2]) break
                    i++
                    len++
                }
                out[o++] = (len - 1).toByte()
                System.arraycopy(src, start, out, o, len)
                o += len
            }
        }
        return o
    }
}
