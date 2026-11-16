package id.goresan.engine

import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.media.MediaMuxer
import java.io.File
import java.io.OutputStream
import java.nio.ByteBuffer
import kotlin.math.max

/** Penulis video MP4 (H.264) dari bingkai RGBA premultiply. [w], [h] genap. */
class VideoWriter(out: File, private val w: Int, private val h: Int, private val fps: Int) {
    private val enc: MediaCodec = MediaCodec.createEncoderByType("video/avc")
    private val mux = MediaMuxer(out.path, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
    private val info = MediaCodec.BufferInfo()
    private var track = -1
    private var started = false
    var frames = 0
        private set

    init {
        val fmt = MediaFormat.createVideoFormat("video/avc", w, h).apply {
            setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Flexible)
            setInteger(MediaFormat.KEY_BIT_RATE, (w.toLong() * h * 5).coerceIn(1_000_000L, 16_000_000L).toInt())
            setInteger(MediaFormat.KEY_FRAME_RATE, fps)
            setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1)
        }
        enc.configure(fmt, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
        enc.start()
    }

    /** Tambah satu bingkai, ditampilkan [repeat] kali (lama tampil = repeat / fps). */
    fun put(rgba: ByteBuffer, repeat: Int = 1) {
        repeat(max(1, repeat)) {
            var idx = enc.dequeueInputBuffer(20_000)
            var tries = 0
            while (idx < 0 && tries < 200) { drain(false); idx = enc.dequeueInputBuffer(20_000); tries++ }
            if (idx < 0) return
            val img = enc.getInputImage(idx) ?: return
            TimeLapse.rgbaToYuv(rgba, w, h, img)
            enc.queueInputBuffer(idx, 0, w * h * 3 / 2, frames * 1_000_000L / fps, 0)
            frames++
            drain(false)
        }
    }

    private fun drain(eos: Boolean) {
        while (true) {
            val i = enc.dequeueOutputBuffer(info, if (eos) 20_000 else 0)
            when {
                i == MediaCodec.INFO_TRY_AGAIN_LATER -> if (!eos) return
                i == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> { track = mux.addTrack(enc.outputFormat); mux.start(); started = true }
                i >= 0 -> {
                    val buf = enc.getOutputBuffer(i)
                    if (info.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG != 0) info.size = 0
                    if (buf != null && info.size > 0 && started) {
                        buf.position(info.offset); buf.limit(info.offset + info.size)
                        mux.writeSampleData(track, buf, info)
                    }
                    enc.releaseOutputBuffer(i, false)
                    if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) return
                }
            }
        }
    }

    fun finish() {
        val idx = enc.dequeueInputBuffer(50_000)
        if (idx >= 0) enc.queueInputBuffer(idx, 0, 0, frames * 1_000_000L / fps, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
        drain(true)
        runCatching { enc.stop() }
        enc.release()
        if (started) runCatching { mux.stop() }
        mux.release()
    }

    fun abort() {
        runCatching { enc.stop() }; runCatching { enc.release() }; runCatching { mux.release() }
    }
}

/**
 * Penulis GIF animasi. Tiap bingkai mendapat palet 256 warna sendiri (median cut dari sampel piksel), dikompres
 * LZW. [loop] = putar berulang terus.
 */
class GifWriter(private val out: OutputStream, private val w: Int, private val h: Int, loop: Boolean) {
    init {
        out.write("GIF89a".toByteArray())
        short(w); short(h)
        out.write(0x70) // tanpa tabel warna global, resolusi warna 8 bit
        out.write(0); out.write(0)
        if (loop) {
            out.write(byteArrayOf(0x21, 0xFF.toByte(), 0x0B))
            out.write("NETSCAPE2.0".toByteArray())
            out.write(byteArrayOf(3, 1, 0, 0, 0))
        }
    }

    private fun short(v: Int) { out.write(v and 0xFF); out.write((v shr 8) and 0xFF) }

    /** Tambah bingkai RGBA premultiply (bagian transparan jadi putih) yang tampil [delayMs]. */
    fun put(rgba: ByteBuffer, delayMs: Int) {
        val n = w * h
        val rgb = IntArray(n)
        rgba.position(0)
        val row = ByteArray(w * 4)
        for (y in 0 until h) {
            rgba.get(row, 0, w * 4)
            for (x in 0 until w) {
                val o = x * 4
                val a = row[o + 3].toInt() and 0xFF
                val r = ((row[o].toInt() and 0xFF) + 255 - a).coerceAtMost(255)
                val g = ((row[o + 1].toInt() and 0xFF) + 255 - a).coerceAtMost(255)
                val b = ((row[o + 2].toInt() and 0xFF) + 255 - a).coerceAtMost(255)
                rgb[y * w + x] = (r shl 16) or (g shl 8) or b
            }
        }
        rgba.position(0)
        val pal = palette(rgb)
        val idx = mapPixels(rgb, pal)
        // Graphic Control Extension: jeda dalam 1/100 detik.
        out.write(byteArrayOf(0x21, 0xF9.toByte(), 4, 0x04))
        short(max(2, (delayMs + 5) / 10))
        out.write(0); out.write(0)
        // Image Descriptor dengan tabel warna lokal 256.
        out.write(0x2C)
        short(0); short(0); short(w); short(h)
        out.write(0x87)
        for (i in 0 until 256) {
            val c = if (i < pal.size) pal[i] else 0
            out.write((c shr 16) and 0xFF); out.write((c shr 8) and 0xFF); out.write(c and 0xFF)
        }
        lzw(idx)
    }

    fun finish() {
        out.write(0x3B)
        out.flush()
    }

    // ---- palet: median cut ----
    private fun palette(rgb: IntArray): IntArray {
        val step = max(1, rgb.size / 40000)
        val sample = IntArray((rgb.size + step - 1) / step) { rgb[it * step] }
        val boxes = ArrayList<IntArray>()
        boxes.add(sample)
        while (boxes.size < 256) {
            // Kotak dengan rentang warna terbesar (berbobot jumlah piksel) dibelah di median kanal terlebar.
            var bi = -1
            var best = 0L
            for (i in boxes.indices) {
                val b = boxes[i]
                if (b.size < 2) continue
                val rg = range(b)
                val score = rg.toLong() * b.size
                if (score > best) { best = score; bi = i }
            }
            if (bi < 0) break
            val b = boxes.removeAt(bi)
            val sh = widest(b)
            val sorted = b.sortedBy { (it shr sh) and 0xFF }.toIntArray()
            val m = sorted.size / 2
            boxes.add(sorted.copyOfRange(0, m))
            boxes.add(sorted.copyOfRange(m, sorted.size))
        }
        return IntArray(boxes.size) { i ->
            val b = boxes[i]
            var r = 0L; var g = 0L; var bl = 0L
            for (c in b) { r += (c shr 16) and 0xFF; g += (c shr 8) and 0xFF; bl += c and 0xFF }
            val k = max(1, b.size)
            ((r / k).toInt() shl 16) or ((g / k).toInt() shl 8) or (bl / k).toInt()
        }
    }

    private fun range(b: IntArray): Int {
        var r0 = 255; var r1 = 0; var g0 = 255; var g1 = 0; var b0 = 255; var b1 = 0
        for (c in b) {
            val r = (c shr 16) and 0xFF; val g = (c shr 8) and 0xFF; val bb = c and 0xFF
            if (r < r0) r0 = r; if (r > r1) r1 = r
            if (g < g0) g0 = g; if (g > g1) g1 = g
            if (bb < b0) b0 = bb; if (bb > b1) b1 = bb
        }
        return maxOf(r1 - r0, g1 - g0, b1 - b0)
    }

    private fun widest(b: IntArray): Int {
        var r0 = 255; var r1 = 0; var g0 = 255; var g1 = 0; var b0 = 255; var b1 = 0
        for (c in b) {
            val r = (c shr 16) and 0xFF; val g = (c shr 8) and 0xFF; val bb = c and 0xFF
            if (r < r0) r0 = r; if (r > r1) r1 = r
            if (g < g0) g0 = g; if (g > g1) g1 = g
            if (bb < b0) b0 = bb; if (bb > b1) b1 = bb
        }
        val dr = r1 - r0; val dg = g1 - g0; val db = b1 - b0
        return if (dr >= dg && dr >= db) 16 else if (dg >= db) 8 else 0
    }

    /** Petakan tiap piksel ke warna palet terdekat (dengan cache 15-bit). */
    private fun mapPixels(rgb: IntArray, pal: IntArray): ByteArray {
        val cache = IntArray(32768) { -1 }
        val out = ByteArray(rgb.size)
        for (i in rgb.indices) {
            val c = rgb[i]
            val key = (((c shr 19) and 31) shl 10) or (((c shr 11) and 31) shl 5) or ((c shr 3) and 31)
            var p = cache[key]
            if (p < 0) {
                val r = (c shr 16) and 0xFF; val g = (c shr 8) and 0xFF; val b = c and 0xFF
                var bd = Int.MAX_VALUE
                p = 0
                for (j in pal.indices) {
                    val q = pal[j]
                    val dr = r - ((q shr 16) and 0xFF); val dg = g - ((q shr 8) and 0xFF); val db = b - (q and 0xFF)
                    val dd = dr * dr * 3 + dg * dg * 4 + db * db * 2
                    if (dd < bd) { bd = dd; p = j }
                }
                cache[key] = p
            }
            out[i] = p.toByte()
        }
        return out
    }

    // ---- LZW (seperti encoder GIF klasik) ----
    private val block = ByteArray(256)
    private var blockLen = 0
    private var accum = 0
    private var bits = 0
    private var nBits = 9
    private var maxCode = 511
    private var freeEnt = 258
    private var clearFlag = false

    private fun lzw(px: ByteArray) {
        out.write(8) // ukuran kode minimum
        accum = 0; bits = 0; blockLen = 0
        nBits = 9; maxCode = (1 shl nBits) - 1; freeEnt = 258; clearFlag = false
        val dict = HashMap<Int, Int>(8192)
        output(256)
        var ent = px[0].toInt() and 0xFF
        for (i in 1 until px.size) {
            val c = px[i].toInt() and 0xFF
            val key = (ent shl 8) or c
            val code = dict[key]
            if (code != null) { ent = code; continue }
            output(ent)
            ent = c
            if (freeEnt < 4096) {
                dict[key] = freeEnt++
            } else {
                dict.clear()
                freeEnt = 258
                clearFlag = true
                output(256)
            }
        }
        output(ent)
        output(257)
        // sisa bit dan blok
        while (bits > 0) { charOut(accum and 0xFF); accum = accum ushr 8; bits = max(0, bits - 8) }
        flushBlock()
        out.write(0)
    }

    private fun output(code: Int) {
        accum = accum and ((1 shl bits) - 1)
        accum = if (bits > 0) accum or (code shl bits) else code
        bits += nBits
        while (bits >= 8) { charOut(accum and 0xFF); accum = accum ushr 8; bits -= 8 }
        if (freeEnt > maxCode || clearFlag) {
            if (clearFlag) {
                nBits = 9; maxCode = (1 shl nBits) - 1; clearFlag = false
            } else {
                nBits++
                maxCode = if (nBits == 12) 4096 else (1 shl nBits) - 1
            }
        }
    }

    private fun charOut(b: Int) {
        block[blockLen++] = b.toByte()
        if (blockLen >= 254) flushBlock()
    }

    private fun flushBlock() {
        if (blockLen > 0) {
            out.write(blockLen)
            out.write(block, 0, blockLen)
            blockLen = 0
        }
    }
}
