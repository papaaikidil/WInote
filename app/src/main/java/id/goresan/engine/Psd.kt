package id.goresan.engine

import java.io.File
import java.io.RandomAccessFile
import java.nio.ByteBuffer

/**
 * Penulis file Photoshop (.psd) berlapis, 8 bit RGB, dengan kompresi RLE (PackBits).
 * Layer ditulis dari paling bawah ke paling atas, lengkap dengan nama, opasitas, mode campur,
 * visibilitas, alpha lock, dan clipping mask.
 */
object Psd {
    class LayerOut(
        val name: String,
        val opacity: Float,
        val blend: Int,
        val visible: Boolean,
        val alphaLock: Boolean,
        val clip: Boolean,
        /** 0 = layer biasa, 1 = folder grup terbuka, 2 = folder grup terlipat, 3 = penutup grup. */
        val section: Int = 0,
        /** Mengembalikan piksel RGBA premultiplied sebesar kanvas. */
        val pixels: () -> ByteBuffer,
    )

    private val keys = arrayOf(
        "norm", "mul ", "dark", "idiv", "lbrn", "dkCl", "lite", "scrn", "div ", "lddg", "lgCl",
        "over", "sLit", "hLit", "vLit", "lLit", "pLit", "hMix", "diff", "smud", "fsub", "fdiv",
        "hue ", "sat ", "colr", "lum ",
    )

    fun write(file: File, w: Int, h: Int, layers: List<LayerOut>, merged: ByteBuffer, dpi: Int = 72, icc: ByteArray? = null) {
        if (file.exists()) file.delete()
        RandomAccessFile(file, "rw").use { f ->
            // Kepala file
            f.writeBytes("8BPS")
            f.writeShort(1)
            f.write(ByteArray(6))
            f.writeShort(3)
            f.writeInt(h)
            f.writeInt(w)
            f.writeShort(8)
            f.writeShort(3)
            // Data mode warna: kosong
            f.writeInt(0)
            // Sumber gambar: ResolutionInfo (1005) supaya DPI ikut terbaca di Photoshop/Krita, dan profil ICC (1039).
            val iccPad = if (icc != null) icc.size % 2 else 0
            f.writeInt(28 + if (icc != null) 12 + icc.size + iccPad else 0)
            f.writeBytes("8BIM")
            f.writeShort(1005)
            f.writeShort(0) // nama kosong (string Pascal, genap)
            f.writeInt(16)
            f.writeInt(dpi shl 16) // hRes, angka tetap 16.16
            f.writeShort(1) // piksel per inci
            f.writeShort(1) // satuan lebar: inci
            f.writeInt(dpi shl 16) // vRes
            f.writeShort(1)
            f.writeShort(1)
            if (icc != null) {
                f.writeBytes("8BIM")
                f.writeShort(1039)
                f.writeShort(0)
                f.writeInt(icc.size)
                f.write(icc)
                if (iccPad != 0) f.write(0)
            }

            // Informasi layer & masker
            val lmStart = f.filePointer
            f.writeInt(0) // panjang bagian layer & masker (diisi nanti)
            val liStart = f.filePointer
            f.writeInt(0) // panjang info layer (diisi nanti)
            f.writeShort(layers.size)

            // Catatan layer; panjang tiap kanal dicatat posisinya untuk diisi setelah data ditulis.
            val chanLenPos = ArrayList<LongArray>()
            for (l in layers) {
                if (l.section != 0) {
                    f.writeInt(0); f.writeInt(0); f.writeInt(0); f.writeInt(0)
                } else {
                    f.writeInt(0); f.writeInt(0); f.writeInt(h); f.writeInt(w)
                }
                f.writeShort(4)
                val pos = LongArray(4)
                val ids = intArrayOf(-1, 0, 1, 2)
                for (c in 0 until 4) {
                    f.writeShort(ids[c])
                    pos[c] = f.filePointer
                    f.writeInt(0)
                }
                chanLenPos.add(pos)
                f.writeBytes("8BIM")
                f.writeBytes(if (l.section == 1 || l.section == 2) "pass" else keys.getOrElse(l.blend) { "norm" })
                f.writeByte((l.opacity.coerceIn(0f, 1f) * 255f + 0.5f).toInt())
                f.writeByte(if (l.clip) 1 else 0)
                var flags = 0
                if (l.alphaLock) flags = flags or 1
                if (!l.visible) flags = flags or 2
                f.writeByte(flags)
                f.writeByte(0)
                val nameBytes = l.name.toByteArray(Charsets.ISO_8859_1).let { if (it.size > 255) it.copyOf(255) else it }
                var nameLen = 1 + nameBytes.size
                while (nameLen % 4 != 0) nameLen++
                // 'luni' = nama Unicode
                val uni = l.name
                var uniLen = 4 + uni.length * 2
                if (uniLen % 2 != 0) uniLen++
                // 'lsct' = penanda folder grup (12 byte isi untuk folder, 4 byte untuk penutup)
                val lsctLen = when (l.section) { 1, 2 -> 12; 3 -> 4; else -> 0 }
                val extra = 4 + 4 + nameLen + 12 + uniLen + (if (lsctLen > 0) 12 + lsctLen else 0)
                f.writeInt(extra)
                f.writeInt(0) // masker layer
                f.writeInt(0) // rentang campur
                f.writeByte(nameBytes.size)
                f.write(nameBytes)
                for (i in 1 + nameBytes.size until nameLen) f.writeByte(0)
                f.writeBytes("8BIM")
                f.writeBytes("luni")
                f.writeInt(uniLen)
                f.writeInt(uni.length)
                for (ch in uni) f.writeShort(ch.code)
                if (4 + uni.length * 2 < uniLen) f.writeByte(0)
                if (lsctLen > 0) {
                    f.writeBytes("8BIM")
                    f.writeBytes("lsct")
                    f.writeInt(lsctLen)
                    f.writeInt(l.section)
                    if (lsctLen == 12) {
                        f.writeBytes("8BIM")
                        f.writeBytes("pass")
                    }
                }
            }

            // Data gambar tiap layer
            val plane = ByteArray(w * h)
            for ((li, l) in layers.withIndex()) {
                if (l.section != 0) {
                    // Folder/penutup grup tidak punya piksel: tiap kanal hanya kode kompresi "mentah".
                    for (c in 0 until 4) {
                        f.writeShort(0)
                        val end = f.filePointer
                        f.seek(chanLenPos[li][c])
                        f.writeInt(2)
                        f.seek(end)
                    }
                    continue
                }
                val px = l.pixels()
                for (c in 0 until 4) {
                    extractPlane(px, w, h, c, plane)
                    val start = f.filePointer
                    writeRleChannel(f, plane, w, h)
                    val len = f.filePointer - start
                    val back = f.filePointer
                    f.seek(chanLenPos[li][c])
                    f.writeInt(len.toInt())
                    f.seek(back)
                }
            }
            // Panjang info layer harus genap
            var liLen = f.filePointer - liStart - 4
            if (liLen % 2 != 0L) { f.writeByte(0); liLen++ }
            f.writeInt(0) // info masker global
            val lmEnd = f.filePointer
            f.seek(liStart); f.writeInt(liLen.toInt())
            f.seek(lmStart); f.writeInt((lmEnd - lmStart - 4).toInt())
            f.seek(lmEnd)

            // Gambar gabungan (tanpa alfa), RLE: tabel panjang baris semua kanal, lalu datanya.
            f.writeShort(1)
            val tablePos = f.filePointer
            f.write(ByteArray(3 * h * 2))
            val rowLens = IntArray(3 * h)
            val row = ByteArray(w)
            val packed = ByteArray(w + w / 128 + 8)
            for (c in 0 until 3) {
                for (y in 0 until h) {
                    val o = y * w * 4
                    for (x in 0 until w) {
                        val a = merged.get(o + x * 4 + 3).toInt() and 0xFF
                        val v = merged.get(o + x * 4 + c).toInt() and 0xFF
                        row[x] = (if (a == 0) 255 else if (a == 255) v else minOf(255, v * 255 / a)).toByte()
                    }
                    val n = packBits(row, w, packed)
                    f.write(packed, 0, n)
                    rowLens[c * h + y] = n
                }
            }
            val end = f.filePointer
            f.seek(tablePos)
            val tb = ByteArray(rowLens.size * 2)
            for (i in rowLens.indices) {
                tb[i * 2] = (rowLens[i] shr 8).toByte()
                tb[i * 2 + 1] = rowLens[i].toByte()
            }
            f.write(tb)
            f.seek(end)
        }
    }

    /** Ambil satu kanal (c: 0=alfa, 1=R, 2=G, 3=B) dalam bentuk tidak-premultiplied. */
    private fun extractPlane(px: ByteBuffer, w: Int, h: Int, c: Int, out: ByteArray) {
        val n = w * h
        for (i in 0 until n) {
            val o = i * 4
            val a = px.get(o + 3).toInt() and 0xFF
            if (c == 0) {
                out[i] = a.toByte()
            } else {
                val v = px.get(o + c - 1).toInt() and 0xFF
                out[i] = (if (a == 0) 0 else if (a == 255) v else minOf(255, v * 255 / a)).toByte()
            }
        }
    }

    private fun writeRleChannel(f: RandomAccessFile, plane: ByteArray, w: Int, h: Int) {
        f.writeShort(1)
        val tablePos = f.filePointer
        f.write(ByteArray(h * 2))
        val lens = IntArray(h)
        val row = ByteArray(w)
        val packed = ByteArray(w + w / 128 + 8)
        for (y in 0 until h) {
            System.arraycopy(plane, y * w, row, 0, w)
            val n = packBits(row, w, packed)
            f.write(packed, 0, n)
            lens[y] = n
        }
        val end = f.filePointer
        f.seek(tablePos)
        val tb = ByteArray(h * 2)
        for (i in 0 until h) {
            tb[i * 2] = (lens[i] shr 8).toByte()
            tb[i * 2 + 1] = lens[i].toByte()
        }
        f.write(tb)
        f.seek(end)
    }

    /** PackBits standar. */
    private fun packBits(src: ByteArray, n: Int, out: ByteArray): Int {
        var i = 0
        var o = 0
        while (i < n) {
            // Hitung pengulangan
            var run = 1
            while (i + run < n && run < 128 && src[i + run] == src[i]) run++
            if (run >= 2) {
                out[o++] = (1 - run).toByte()
                out[o++] = src[i]
                i += run
            } else {
                // Literal sampai ketemu pengulangan ≥ 2 atau 128 byte
                val start = i
                var len = 0
                while (i < n && len < 128) {
                    if (i + 1 < n && src[i] == src[i + 1]) break
                    i++
                    len++
                }
                if (len == 0) {
                    // Pengulangan dimulai tepat di sini; ditangani putaran berikutnya.
                    continue
                }
                out[o++] = (len - 1).toByte()
                System.arraycopy(src, start, out, o, len)
                o += len
            }
        }
        return o
    }
}
