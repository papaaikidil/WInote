package id.goresan.engine

import java.io.FilterOutputStream
import java.io.OutputStream
import java.util.zip.CRC32
import kotlin.math.roundToInt

/**
 * Menulis DPI ke file gambar yang sedang dikeluarkan `Bitmap.compress`, tanpa menampung seluruh file di memori:
 * - PNG: potongan `pHYs` disisipkan tepat sesudah `IHDR` (byte ke-33).
 * - JPG: kepadatan di kepala JFIF (byte 13–17) diganti titik per inci.
 * Bila kepala file tidak seperti yang diharapkan, data diteruskan apa adanya.
 */
class DpiOutputStream(out: OutputStream, private val dpi: Int, private val jpeg: Boolean) : FilterOutputStream(out) {
    private val head = ByteArray(if (jpeg) 18 else 33)
    private var n = 0
    private var passthrough = dpi <= 0

    override fun write(b: Int) {
        if (passthrough) {
            out.write(b)
            return
        }
        head[n++] = b.toByte()
        if (n == head.size) flushHead()
    }

    override fun write(b: ByteArray, off: Int, len: Int) {
        var i = off
        val end = off + len
        while (i < end && !passthrough) write(b[i++].toInt())
        if (i < end) out.write(b, i, end - i)
    }

    override fun close() {
        if (!passthrough && n > 0) {
            out.write(head, 0, n)
            passthrough = true
        }
        super.close()
    }

    private fun flushHead() {
        if (jpeg) patchJfif() else {
            out.write(head)
            if (isPng()) out.write(physChunk())
        }
        passthrough = true
    }

    private fun isPng(): Boolean =
        head[0] == 0x89.toByte() && head[1] == 'P'.code.toByte() && head[12] == 'I'.code.toByte() &&
            head[13] == 'H'.code.toByte() && head[14] == 'D'.code.toByte() && head[15] == 'R'.code.toByte()

    private fun physChunk(): ByteArray {
        val ppm = (dpi / 0.0254).roundToInt()
        val c = ByteArray(21)
        putInt(c, 0, 9)
        "pHYs".forEachIndexed { k, ch -> c[4 + k] = ch.code.toByte() }
        putInt(c, 8, ppm)
        putInt(c, 12, ppm)
        c[16] = 1 // satuan: meter
        val crc = CRC32()
        crc.update(c, 4, 13)
        putInt(c, 17, crc.value.toInt())
        return c
    }

    private fun patchJfif() {
        val jfif = head[0] == 0xFF.toByte() && head[1] == 0xD8.toByte() && head[2] == 0xFF.toByte() &&
            head[3] == 0xE0.toByte() && head[6] == 'J'.code.toByte() && head[7] == 'F'.code.toByte() &&
            head[8] == 'I'.code.toByte() && head[9] == 'F'.code.toByte() && head[10].toInt() == 0
        if (jfif) {
            val d = dpi.coerceIn(1, 65535)
            head[13] = 1 // titik per inci
            head[14] = (d shr 8).toByte(); head[15] = d.toByte()
            head[16] = (d shr 8).toByte(); head[17] = d.toByte()
        }
        out.write(head)
    }

    private fun putInt(a: ByteArray, at: Int, v: Int) {
        a[at] = (v ushr 24).toByte()
        a[at + 1] = (v ushr 16).toByte()
        a[at + 2] = (v ushr 8).toByte()
        a[at + 3] = v.toByte()
    }
}
