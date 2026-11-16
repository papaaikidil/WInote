package id.goresan.engine

import android.media.Image
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMuxer
import id.goresan.Diag
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.nio.ByteBuffer
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger
import kotlin.math.max
import kotlin.math.min

/**
 * Perekam time-lapse sebuah karya. Tiap perubahan kanvas jadi satu bingkai video H.264 (30 fps). Bingkai disimpan
 * per sesi (segmen .mp4) di folder `timelapse` karya; daftar segmen yang sudah utuh ada di `index.json`, jadi
 * segmen yang terputus (aplikasi mati) tidak merusak rekaman lain. Ukuran video tetap sepanjang umur karya
 * (kanvas yang diubah ukurannya dipaskan di dalamnya).
 */
class TimeLapse(docDir: File, canvasW: Int, canvasH: Int) {
    val dir = folder(docDir)
    val vw: Int
    val vh: Int
    private val exec = Executors.newSingleThreadExecutor { r -> Thread(r, "goresan-timelapse") }
    private val queued = AtomicInteger(0)
    private val pool = ArrayDeque<ByteBuffer>()

    private var codec: MediaCodec? = null
    private var muxer: MediaMuxer? = null
    private var segFile: File? = null
    private var track = -1
    private var muxStarted = false
    private var frames = 0
    private val info = MediaCodec.BufferInfo()

    init {
        val idx = readIndex(dir)
        if (idx != null && idx.optInt("w") > 0 && idx.optInt("h") > 0) {
            vw = idx.optInt("w"); vh = idx.optInt("h")
        } else {
            val s = sizeFor(canvasW, canvasH)
            vw = s.first; vh = s.second
        }
    }

    /** Encoder masih mengerjakan bingkai sebelumnya: bingkai baru ditunda. */
    fun busy() = queued.get() >= 2

    /** Penyangga RGBA ukuran video (dipakai ulang supaya tidak membebani memori). */
    @Synchronized
    fun buffer(): ByteBuffer = pool.removeFirstOrNull() ?: Buffers.direct(vw * vh * 4)

    @Synchronized
    private fun recycle(b: ByteBuffer) {
        if (pool.size < 3) pool.addLast(b)
    }

    /** Kirim satu bingkai RGBA (premultiply, baris 0 = atas gambar). */
    fun submit(rgba: ByteBuffer) {
        queued.incrementAndGet()
        exec.execute {
            try {
                encode(rgba)
            } catch (e: Throwable) {
                Diag.log("Time-lapse: bingkai gagal ${e.javaClass.simpleName}: ${e.message}")
                abortSegment()
            } finally {
                recycle(rgba)
                queued.decrementAndGet()
            }
        }
    }

    /** Tutup segmen yang sedang direkam (dipanggil saat menyimpan/menutup karya). */
    fun finishSegment() {
        exec.execute {
            try {
                closeSegment()
            } catch (e: Throwable) {
                Diag.log("Time-lapse: tutup segmen gagal ${e.javaClass.simpleName}: ${e.message}")
                abortSegment()
            }
        }
    }

    /** Tutup segmen lalu hentikan thread perekam. [done] dipanggil (di thread perekam) setelah selesai. */
    fun shutdown(done: (() -> Unit)? = null) {
        finishSegment()
        exec.execute { done?.invoke() }
        exec.shutdown()
    }

    /** Jalankan [r] setelah semua bingkai yang antre tersimpan (untuk putar ulang/ekspor). */
    fun afterPending(r: () -> Unit) {
        finishSegment()
        exec.execute(r)
    }

    private fun openSegment() {
        dir.mkdirs()
        val idx = readIndex(dir)
        var n = (idx?.optInt("next", 1) ?: 1)
        var f = File(dir, "seg-%05d.mp4".format(n))
        while (f.exists()) { n++; f = File(dir, "seg-%05d.mp4".format(n)) }
        val fmt = MediaFormat.createVideoFormat(MIME, vw, vh).apply {
            setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Flexible)
            setInteger(MediaFormat.KEY_BIT_RATE, bitrate(vw, vh))
            setInteger(MediaFormat.KEY_FRAME_RATE, FPS)
            setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 2)
        }
        val c = MediaCodec.createEncoderByType(MIME)
        c.configure(fmt, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
        c.start()
        codec = c
        muxer = MediaMuxer(f.path, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
        segFile = f
        track = -1
        muxStarted = false
        frames = 0
        writeNext(n + 1)
    }

    private fun encode(rgba: ByteBuffer) {
        if (codec == null) openSegment()
        val c = codec!!
        var idx = c.dequeueInputBuffer(20_000)
        var tries = 0
        while (idx < 0 && tries < 50) {
            drain(c, false)
            idx = c.dequeueInputBuffer(20_000)
            tries++
        }
        if (idx < 0) return
        val img = c.getInputImage(idx) ?: return
        rgbaToYuv(rgba, vw, vh, img)
        c.queueInputBuffer(idx, 0, vw * vh * 3 / 2, frames * 1_000_000L / FPS, 0)
        frames++
        drain(c, false)
        if (frames >= 900) closeSegment()
    }

    private fun drain(c: MediaCodec, eos: Boolean) {
        val m = muxer ?: return
        while (true) {
            val i = c.dequeueOutputBuffer(info, if (eos) 20_000 else 0)
            when {
                i == MediaCodec.INFO_TRY_AGAIN_LATER -> if (!eos) return
                i == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                    track = m.addTrack(c.outputFormat)
                    m.start()
                    muxStarted = true
                }
                i >= 0 -> {
                    val buf = c.getOutputBuffer(i)
                    if (info.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG != 0) info.size = 0
                    if (buf != null && info.size > 0 && muxStarted) {
                        buf.position(info.offset)
                        buf.limit(info.offset + info.size)
                        m.writeSampleData(track, buf, info)
                    }
                    c.releaseOutputBuffer(i, false)
                    if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) return
                }
            }
        }
    }

    private fun closeSegment() {
        val c = codec ?: return
        val f = segFile
        val n = frames
        try {
            if (n > 0) {
                val idx = c.dequeueInputBuffer(50_000)
                if (idx >= 0) c.queueInputBuffer(idx, 0, 0, n * 1_000_000L / FPS, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                drain(c, true)
            }
        } finally {
            runCatching { c.stop() }
            c.release()
            codec = null
            val m = muxer
            muxer = null
            var ok = false
            if (m != null) {
                ok = muxStarted && runCatching { m.stop() }.isSuccess
                runCatching { m.release() }
            }
            if (f != null) {
                if (ok && n > 0) addToIndex(f.name, n) else f.delete()
            }
            segFile = null
            frames = 0
        }
    }

    private fun abortSegment() {
        runCatching { codec?.stop() }
        runCatching { codec?.release() }
        codec = null
        runCatching { muxer?.release() }
        muxer = null
        segFile?.delete()
        segFile = null
        frames = 0
    }

    private fun writeNext(next: Int) {
        val o = readIndex(dir) ?: JSONObject().apply { put("w", vw); put("h", vh); put("segs", JSONArray()) }
        o.put("next", next)
        writeIndex(dir, o)
    }

    private fun addToIndex(name: String, n: Int) {
        val o = readIndex(dir) ?: JSONObject().apply { put("w", vw); put("h", vh); put("segs", JSONArray()) }
        o.put("w", vw); o.put("h", vh)
        val a = o.optJSONArray("segs") ?: JSONArray().also { o.put("segs", it) }
        a.put(JSONObject().apply { put("f", name); put("n", n) })
        writeIndex(dir, o)
    }

    companion object {
        const val FPS = 30
        private const val MIME = "video/avc"

        fun folder(docDir: File) = File(docDir, "timelapse")

        /** Ukuran video: muat dalam 1920×1080 (mendatar) atau 1080×1920 (tegak), kelipatan 16. */
        fun sizeFor(w: Int, h: Int): Pair<Int, Int> {
            val bw = if (w >= h) 1920 else 1080
            val bh = if (w >= h) 1080 else 1920
            val s = min(1f, min(bw.toFloat() / w, bh.toFloat() / h))
            val vw = max(64, ((w * s).toInt() / 16) * 16)
            val vh = max(64, ((h * s).toInt() / 16) * 16)
            return vw to vh
        }

        private fun bitrate(w: Int, h: Int) = (w.toLong() * h * 4).coerceIn(1_000_000L, 12_000_000L).toInt()

        fun readIndex(dir: File): JSONObject? = runCatching { JSONObject(File(dir, "index.json").readText()) }.getOrNull()

        private fun writeIndex(dir: File, o: JSONObject) {
            dir.mkdirs()
            val tmp = File(dir, "index.json.tmp")
            tmp.writeText(o.toString())
            tmp.renameTo(File(dir, "index.json"))
        }

        /** Jumlah bingkai yang sudah tersimpan. */
        fun frameCount(docDir: File): Int {
            val a = readIndex(folder(docDir))?.optJSONArray("segs") ?: return 0
            var n = 0
            for (i in 0 until a.length()) n += a.getJSONObject(i).optInt("n")
            return n
        }

        /** Hapus seluruh rekaman. */
        fun clear(docDir: File) {
            folder(docDir).deleteRecursively()
        }

        /**
         * Gabungkan semua segmen jadi satu video di [out]. [seconds] = null: semua bingkai; selain itu bingkai
         * dijarangkan supaya pas sekian detik. Bingkai terakhir ditahan [holdSec] detik. [progress] 0..1.
         */
        fun export(docDir: File, out: File, seconds: Int?, holdSec: Int, progress: (Float) -> Unit): Boolean {
            val dir = folder(docDir)
            val idx = readIndex(dir) ?: return false
            val vw = idx.optInt("w")
            val vh = idx.optInt("h")
            val segs = idx.optJSONArray("segs") ?: return false
            val files = ArrayList<Pair<File, Int>>()
            var total = 0
            for (i in 0 until segs.length()) {
                val o = segs.getJSONObject(i)
                val f = File(dir, o.optString("f"))
                if (f.exists()) { files.add(f to o.optInt("n")); total += o.optInt("n") }
            }
            if (total == 0 || vw <= 0 || vh <= 0) return false
            val keep = if (seconds == null) total else min(total, seconds * FPS)
            out.delete()
            val fmt = MediaFormat.createVideoFormat(MIME, vw, vh).apply {
                setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Flexible)
                setInteger(MediaFormat.KEY_BIT_RATE, bitrate(vw, vh))
                setInteger(MediaFormat.KEY_FRAME_RATE, FPS)
                setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1)
            }
            val enc = MediaCodec.createEncoderByType(MIME)
            enc.configure(fmt, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
            enc.start()
            val mux = MediaMuxer(out.path, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
            val st = ExportState(enc, mux)
            var seen = 0
            var lastKept: ByteArray? = null
            try {
                for ((f, _) in files) {
                    decodeSegment(f) { img ->
                        // Bingkai ke-seen dipakai bila membuat indeks keluaran naik (penjarangan merata).
                        val want = (seen.toLong() * keep / total).toInt()
                        val next = ((seen + 1).toLong() * keep / total).toInt()
                        seen++
                        if (next > want) {
                            lastKept = st.put(img, vw, vh)
                        }
                        progress(seen.toFloat() / total * 0.95f)
                    }
                }
                // Tahan gambar akhir.
                val last = lastKept
                if (last != null) repeat(holdSec * FPS) { st.putRaw(last, vw, vh) }
                st.finish()
                progress(1f)
                return st.frames > 0
            } catch (e: Throwable) {
                Diag.log("Time-lapse: ekspor gagal ${e.javaClass.simpleName}: ${e.message}")
                runCatching { st.abort() }
                out.delete()
                return false
            }
        }

        /** Keadaan encoder ekspor. */
        private class ExportState(val enc: MediaCodec, val mux: MediaMuxer) {
            var frames = 0
            var track = -1
            var started = false
            val info = MediaCodec.BufferInfo()

            /** Salin satu bingkai dari decoder; mengembalikan salinan I420 padatnya (untuk menahan bingkai akhir). */
            fun put(src: Image, vw: Int, vh: Int): ByteArray {
                val packed = packI420(src, vw, vh)
                putRaw(packed, vw, vh)
                return packed
            }

            fun putRaw(i420: ByteArray, vw: Int, vh: Int) {
                var idx = enc.dequeueInputBuffer(20_000)
                var tries = 0
                while (idx < 0 && tries < 200) { drain(false); idx = enc.dequeueInputBuffer(20_000); tries++ }
                if (idx < 0) return
                val img = enc.getInputImage(idx) ?: return
                unpackI420(i420, vw, vh, img)
                enc.queueInputBuffer(idx, 0, vw * vh * 3 / 2, frames * 1_000_000L / FPS, 0)
                frames++
                drain(false)
            }

            fun drain(eos: Boolean) {
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
                if (idx >= 0) enc.queueInputBuffer(idx, 0, 0, frames * 1_000_000L / FPS, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                drain(true)
                enc.stop(); enc.release()
                if (started) mux.stop()
                mux.release()
            }

            fun abort() {
                runCatching { enc.stop() }; runCatching { enc.release() }; runCatching { mux.release() }
            }
        }

        /** Putar satu segmen lewat decoder; [onFrame] menerima tiap bingkai (Image YUV). */
        private fun decodeSegment(f: File, onFrame: (Image) -> Unit) {
            val ex = MediaExtractor()
            ex.setDataSource(f.path)
            var ti = -1
            for (i in 0 until ex.trackCount) if (ex.getTrackFormat(i).getString(MediaFormat.KEY_MIME)?.startsWith("video/") == true) { ti = i; break }
            if (ti < 0) { ex.release(); return }
            ex.selectTrack(ti)
            val fmt = ex.getTrackFormat(ti)
            fmt.setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Flexible)
            val dec = MediaCodec.createDecoderByType(fmt.getString(MediaFormat.KEY_MIME)!!)
            dec.configure(fmt, null, null, 0)
            dec.start()
            val info = MediaCodec.BufferInfo()
            var inDone = false
            try {
                while (true) {
                    if (!inDone) {
                        val ii = dec.dequeueInputBuffer(10_000)
                        if (ii >= 0) {
                            val b = dec.getInputBuffer(ii)!!
                            val n = ex.readSampleData(b, 0)
                            if (n < 0) {
                                dec.queueInputBuffer(ii, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                                inDone = true
                            } else {
                                dec.queueInputBuffer(ii, 0, n, ex.sampleTime, 0)
                                ex.advance()
                            }
                        }
                    }
                    val oi = dec.dequeueOutputBuffer(info, 10_000)
                    if (oi >= 0) {
                        if (info.size > 0) dec.getOutputImage(oi)?.let { img -> onFrame(img); img.close() }
                        dec.releaseOutputBuffer(oi, false)
                        if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) break
                    }
                }
            } finally {
                runCatching { dec.stop() }
                dec.release()
                ex.release()
            }
        }

        /** RGBA premultiply (baris 0 = atas) → YUV420 BT.601 di [img]; bagian transparan jadi putih. */
        fun rgbaToYuv(src: ByteBuffer, w: Int, h: Int, img: Image) {
            val yP = img.planes[0]; val uP = img.planes[1]; val vP = img.planes[2]
            val yb = yP.buffer; val ub = uP.buffer; val vb = vP.buffer
            val yRow = yP.rowStride; val yPix = yP.pixelStride
            val uRow = uP.rowStride; val uPix = uP.pixelStride
            val vRow = vP.rowStride; val vPix = vP.pixelStride
            val row = ByteArray(w * 4)
            val row2 = ByteArray(w * 4)
            var y = 0
            while (y < h) {
                src.position(y * w * 4); src.get(row, 0, w * 4)
                val y2 = min(y + 1, h - 1)
                src.position(y2 * w * 4); src.get(row2, 0, w * 4)
                for (x in 0 until w) {
                    val o = x * 4
                    val a = row[o + 3].toInt() and 0xFF
                    val r = (row[o].toInt() and 0xFF) + 255 - a
                    val g = (row[o + 1].toInt() and 0xFF) + 255 - a
                    val b = (row[o + 2].toInt() and 0xFF) + 255 - a
                    yb.put(y * yRow + x * yPix, (((66 * r + 129 * g + 25 * b + 128) shr 8) + 16).coerceIn(0, 255).toByte())
                    if (y2 != y) {
                        val a2 = row2[o + 3].toInt() and 0xFF
                        val r2 = (row2[o].toInt() and 0xFF) + 255 - a2
                        val g2 = (row2[o + 1].toInt() and 0xFF) + 255 - a2
                        val b2 = (row2[o + 2].toInt() and 0xFF) + 255 - a2
                        yb.put(y2 * yRow + x * yPix, (((66 * r2 + 129 * g2 + 25 * b2 + 128) shr 8) + 16).coerceIn(0, 255).toByte())
                    }
                    if (x and 1 == 0) {
                        val cx = x shr 1
                        val cy = y shr 1
                        ub.put(cy * uRow + cx * uPix, (((-38 * r - 74 * g + 112 * b + 128) shr 8) + 128).coerceIn(0, 255).toByte())
                        vb.put(cy * vRow + cx * vPix, (((112 * r - 94 * g - 18 * b + 128) shr 8) + 128).coerceIn(0, 255).toByte())
                    }
                }
                y += 2
            }
            src.position(0)
        }

        /** Image YUV420 (bentuk apa pun) → larik I420 padat. */
        private fun packI420(img: Image, w: Int, h: Int): ByteArray {
            val out = ByteArray(w * h * 3 / 2)
            val yP = img.planes[0]; val uP = img.planes[1]; val vP = img.planes[2]
            val iw = min(w, img.width); val ih = min(h, img.height)
            var k = 0
            for (y in 0 until h) {
                val sy = min(y, ih - 1)
                for (x in 0 until w) out[k++] = yP.buffer.get(sy * yP.rowStride + min(x, iw - 1) * yP.pixelStride)
            }
            val cw = w / 2; val ch = h / 2
            for (p in listOf(uP, vP)) {
                for (y in 0 until ch) {
                    val sy = min(y, ih / 2 - 1)
                    for (x in 0 until cw) out[k++] = p.buffer.get(sy * p.rowStride + min(x, iw / 2 - 1) * p.pixelStride)
                }
            }
            return out
        }

        private fun unpackI420(src: ByteArray, w: Int, h: Int, img: Image) {
            val yP = img.planes[0]; val uP = img.planes[1]; val vP = img.planes[2]
            var k = 0
            for (y in 0 until h) for (x in 0 until w) yP.buffer.put(y * yP.rowStride + x * yP.pixelStride, src[k++])
            val cw = w / 2; val ch = h / 2
            for (p in listOf(uP, vP)) for (y in 0 until ch) for (x in 0 until cw) p.buffer.put(y * p.rowStride + x * p.pixelStride, src[k++])
        }
    }
}
