package id.goresan.engine

import java.io.ByteArrayOutputStream
import java.io.File
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.util.zip.CRC32
import java.util.zip.Deflater

/**
 * Penulis PNG beranimasi (APNG) tanpa pustaka Android: RGBA 8 bit dengan alfa sungguhan (beda dengan GIF), jadi
 * animasi berlatar transparan tetap halus tepinya. Jumlah bingkai diisi saat [finish].
 */
class ApngWriter(file: File, private val w: Int, private val h: Int, private val loop: Boolean) {
    private val f: RandomAccessFile
    private var seq = 0
    private var frames = 0
    private var actlPos = 0L
    private val deflater = Deflater(6)

    init {
        if (file.exists()) file.delete()
        f = RandomAccessFile(file, "rw")
        f.write(byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A))
        chunk("IHDR", ByteBuffer.allocate(13).putInt(w).putInt(h).put(8).put(6).put(0).put(0).put(0).array())
        actlPos = f.filePointer
        chunk("acTL", ByteBuffer.allocate(8).putInt(1).putInt(if (loop) 0 else 1).array())
    }

    private fun chunk(type: String, data: ByteArray, len: Int = data.size) {
        f.writeInt(len)
        val t = type.toByteArray(Charsets.ISO_8859_1)
        f.write(t)
        f.write(data, 0, len)
        val crc = CRC32()
        crc.update(t)
        crc.update(data, 0, len)
        f.writeInt(crc.value.toInt())
    }

    /** Tambah bingkai RGBA premultiplied (baris 0 = atas) yang tampil [delayMs] milidetik. */
    fun put(rgba: ByteBuffer, delayMs: Int) {
        // Baris PNG: penyaring "Sub" (beda dengan piksel kiri), warna tidak premultiplied.
        val stride = w * 4
        val raw = ByteArray((stride + 1) * h)
        val row = ByteArray(stride)
        rgba.position(0)
        var o = 0
        for (y in 0 until h) {
            rgba.get(row, 0, stride)
            for (x in 0 until w) {
                val i = x * 4
                val a = row[i + 3].toInt() and 0xFF
                if (a != 0 && a != 255) {
                    for (c in 0 until 3) row[i + c] = minOf(255, ((row[i + c].toInt() and 0xFF) * 255 + a / 2) / a).toByte()
                } else if (a == 0) {
                    row[i] = 0; row[i + 1] = 0; row[i + 2] = 0
                }
            }
            raw[o++] = 1
            for (x in 0 until stride) {
                val left = if (x >= 4) row[x - 4].toInt() else 0
                raw[o++] = (row[x].toInt() - left).toByte()
            }
        }
        rgba.position(0)
        deflater.reset()
        deflater.setInput(raw)
        deflater.finish()
        val bo = ByteArrayOutputStream(raw.size / 4 + 1024)
        val buf = ByteArray(64 * 1024)
        while (!deflater.finished()) {
            val n = deflater.deflate(buf)
            bo.write(buf, 0, n)
        }
        val z = bo.toByteArray()
        val d = delayMs.coerceIn(1, 65535)
        chunk(
            "fcTL",
            ByteBuffer.allocate(26).putInt(seq++).putInt(w).putInt(h).putInt(0).putInt(0)
                .putShort(d.toShort()).putShort(1000.toShort()).put(0).put(0).array(),
        )
        if (frames == 0) {
            chunk("IDAT", z)
        } else {
            val fd = ByteArray(z.size + 4)
            ByteBuffer.wrap(fd).putInt(seq++)
            System.arraycopy(z, 0, fd, 4, z.size)
            chunk("fdAT", fd)
        }
        frames++
    }

    fun finish() {
        chunk("IEND", ByteArray(0))
        // Jumlah bingkai sebenarnya di acTL.
        val end = f.filePointer
        f.seek(actlPos)
        chunk("acTL", ByteBuffer.allocate(8).putInt(frames).putInt(if (loop) 0 else 1).array())
        f.seek(end)
        f.close()
        deflater.end()
    }

    fun abort() {
        runCatching { f.close() }
        deflater.end()
    }
}
