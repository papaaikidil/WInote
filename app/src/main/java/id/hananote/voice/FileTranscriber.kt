package id.hananote.voice

import android.content.Context
import android.content.Intent
import android.media.AudioFormat
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.os.Build
import android.os.Bundle
import android.os.ParcelFileDescriptor
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import androidx.annotation.RequiresApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.coroutines.resume

/**
 * Mengubah rekaman yang sudah tersimpan jadi teks (Android 13 ke atas).
 *
 * Rekaman dibuka jadi suara mentah 16 kHz satu kanal, lalu dialirkan ke pengenal suara perangkat
 * lewat jalur audio milik aplikasi. Tidak semua layanan pengenal mendukung cara ini; bila tidak,
 * hasilnya kosong dan pengguna diarahkan ke Dikte.
 */
object FileTranscriber {

    val supported: Boolean get() = Build.VERSION.SDK_INT >= 33

    /** Hasil: teks, atau null beserta alasan. */
    class Result(val text: String?, val problem: String?)

    suspend fun transcribe(context: Context, file: File, language: String, onPartial: (String) -> Unit): Result {
        if (Build.VERSION.SDK_INT < 33) {
            return Result(null, "Mengubah rekaman jadi teks butuh Android 13 atau lebih baru. Pakai Dikte untuk mengubah suara langsung jadi teks.")
        }
        if (!SpeechRecognizer.isRecognitionAvailable(context)) {
            return Result(null, "Layanan pengenal suara tidak tersedia. Pastikan aplikasi Google terpasang dan aktif.")
        }
        val pcm = withContext(Dispatchers.Default) { runCatching { decodeMono16k(file) }.getOrNull() }
            ?: return Result(null, "Rekaman tidak bisa dibuka.")
        if (pcm.isEmpty()) return Result(null, "Rekaman kosong.")
        val seconds = pcm.size / 16000L
        val text = withTimeoutOrNull((seconds + 45L) * 1000L) {
            withContext(Dispatchers.Main) { run33(context, pcm, language, onPartial) }
        }
        return when {
            text == null -> Result(null, "Terlalu lama menunggu pengenal suara. Coba lagi, atau pakai Dikte.")
            text.isBlank() -> Result(null, "Rekaman belum bisa dibaca di perangkat ini. Pakai Dikte untuk mengubah suara langsung jadi teks.")
            else -> Result(text, null)
        }
    }

    @RequiresApi(33)
    private suspend fun run33(context: Context, pcm: ShortArray, language: String, onPartial: (String) -> Unit): String =
        suspendCancellableCoroutine { cont ->
            val pipe = ParcelFileDescriptor.createPipe()
            val read = pipe[0]
            val write = pipe[1]
            val segments = ArrayList<String>()
            val recognizer = SpeechRecognizer.createSpeechRecognizer(context)
            var done = false
            fun finish() {
                if (done) return
                done = true
                runCatching { recognizer.destroy() }
                runCatching { read.close() }
                if (cont.isActive) cont.resume(segments.joinToString(" ").trim())
            }
            cont.invokeOnCancellation {
                android.os.Handler(android.os.Looper.getMainLooper()).post { finish() }
            }
            recognizer.setRecognitionListener(object : RecognitionListener {
                override fun onReadyForSpeech(params: Bundle?) = Unit
                override fun onBeginningOfSpeech() = Unit
                override fun onRmsChanged(rmsdB: Float) = Unit
                override fun onBufferReceived(buffer: ByteArray?) = Unit
                override fun onEndOfSpeech() = Unit
                override fun onEvent(eventType: Int, params: Bundle?) = Unit
                override fun onPartialResults(partialResults: Bundle?) {
                    val t = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull().orEmpty()
                    if (t.isNotBlank()) onPartial((segments + t).joinToString(" "))
                }
                override fun onSegmentResults(segmentResults: Bundle) {
                    val t = segmentResults.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull().orEmpty()
                    if (t.isNotBlank()) {
                        segments.add(t.trim())
                        onPartial(segments.joinToString(" "))
                    }
                }
                override fun onEndOfSegmentedSession() = finish()
                override fun onResults(results: Bundle?) {
                    val t = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull().orEmpty()
                    if (t.isNotBlank()) segments.add(t.trim())
                    finish()
                }
                override fun onError(error: Int) = finish()
            })
            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, language)
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                putExtra(RecognizerIntent.EXTRA_AUDIO_SOURCE, read)
                putExtra(RecognizerIntent.EXTRA_AUDIO_SOURCE_CHANNEL_COUNT, 1)
                putExtra(RecognizerIntent.EXTRA_AUDIO_SOURCE_ENCODING, AudioFormat.ENCODING_PCM_16BIT)
                putExtra(RecognizerIntent.EXTRA_AUDIO_SOURCE_SAMPLING_RATE, 16000)
                putExtra(RecognizerIntent.EXTRA_SEGMENTED_SESSION, RecognizerIntent.EXTRA_AUDIO_SOURCE)
            }
            // Suara dialirkan dari jalur terpisah; pengenal membacanya sampai jalurnya ditutup.
            Thread {
                runCatching {
                    ParcelFileDescriptor.AutoCloseOutputStream(write).use { out ->
                        val buf = ByteBuffer.allocate(3200).order(ByteOrder.LITTLE_ENDIAN)
                        var i = 0
                        while (i < pcm.size) {
                            buf.clear()
                            val end = minOf(pcm.size, i + 1600)
                            for (k in i until end) buf.putShort(pcm[k])
                            out.write(buf.array(), 0, (end - i) * 2)
                            i = end
                        }
                    }
                }
            }.start()
            runCatching { recognizer.startListening(intent) }.onFailure { finish() }
        }

    /** Buka berkas audio jadi suara mentah 16 kHz satu kanal (16 bit). */
    private fun decodeMono16k(file: File): ShortArray {
        val ex = MediaExtractor()
        ex.setDataSource(file.path)
        var track = -1
        for (i in 0 until ex.trackCount) {
            val mime = ex.getTrackFormat(i).getString(MediaFormat.KEY_MIME).orEmpty()
            if (mime.startsWith("audio/")) { track = i; break }
        }
        if (track < 0) { ex.release(); return ShortArray(0) }
        ex.selectTrack(track)
        val format = ex.getTrackFormat(track)
        val codec = MediaCodec.createDecoderByType(format.getString(MediaFormat.KEY_MIME)!!)
        codec.configure(format, null, null, 0)
        codec.start()
        var rate = format.getInteger(MediaFormat.KEY_SAMPLE_RATE)
        var channels = format.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
        val mono = ShortList()
        val info = MediaCodec.BufferInfo()
        var inputDone = false
        var outputDone = false
        while (!outputDone) {
            if (!inputDone) {
                val inIdx = codec.dequeueInputBuffer(10_000)
                if (inIdx >= 0) {
                    val inBuf = codec.getInputBuffer(inIdx)!!
                    val n = ex.readSampleData(inBuf, 0)
                    if (n < 0) {
                        codec.queueInputBuffer(inIdx, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                        inputDone = true
                    } else {
                        codec.queueInputBuffer(inIdx, 0, n, ex.sampleTime, 0)
                        ex.advance()
                    }
                }
            }
            val outIdx = codec.dequeueOutputBuffer(info, 10_000)
            when {
                outIdx == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                    val f = codec.outputFormat
                    rate = f.getInteger(MediaFormat.KEY_SAMPLE_RATE)
                    channels = f.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
                }
                outIdx >= 0 -> {
                    val out = codec.getOutputBuffer(outIdx)!!
                    out.position(info.offset)
                    out.limit(info.offset + info.size)
                    val sb = out.order(ByteOrder.LITTLE_ENDIAN).asShortBuffer()
                    val ch = channels.coerceAtLeast(1)
                    while (sb.remaining() >= ch) {
                        var sum = 0
                        for (k in 0 until ch) sum += sb.get().toInt()
                        mono.add((sum / ch).toShort())
                    }
                    codec.releaseOutputBuffer(outIdx, false)
                    if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) outputDone = true
                }
            }
        }
        codec.stop()
        codec.release()
        ex.release()
        return resample(mono.toArray(), rate, 16000)
    }

    /** Ubah laju sampel dengan interpolasi garis lurus (cukup untuk ucapan). */
    fun resample(src: ShortArray, from: Int, to: Int): ShortArray {
        if (from == to || src.isEmpty() || from <= 0) return src
        val n = (src.size.toLong() * to / from).toInt()
        val out = ShortArray(n)
        val step = from.toDouble() / to
        for (i in 0 until n) {
            val pos = i * step
            val j = pos.toInt()
            val f = pos - j
            val a = src[j.coerceAtMost(src.size - 1)].toInt()
            val b = src[(j + 1).coerceAtMost(src.size - 1)].toInt()
            out[i] = (a + (b - a) * f).toInt().toShort()
        }
        return out
    }

    /** Daftar angka pendek yang bisa tumbuh tanpa membungkus tiap angka. */
    private class ShortList {
        private var data = ShortArray(1 shl 16)
        private var size = 0
        fun add(v: Short) {
            if (size == data.size) data = data.copyOf(size * 2)
            data[size++] = v
        }
        fun toArray(): ShortArray = data.copyOf(size)
    }
}
