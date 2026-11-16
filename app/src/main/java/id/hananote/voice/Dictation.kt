package id.hananote.voice

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer

/**
 * Dikte: suara langsung diubah jadi teks memakai layanan pengenal suara perangkat
 * (biasanya Google; bisa tanpa internet bila paket bahasanya sudah diunduh).
 *
 * Pengenal Android berhenti sendiri setelah jeda bicara, jadi di sini ia dinyalakan ulang terus
 * sampai [stop] dipanggil, supaya bisa mendikte panjang.
 */
class Dictation(
    private val context: Context,
    private val language: String,
    private val listener: Listener,
) {
    interface Listener {
        /** Teks sementara kalimat yang sedang diucapkan. */
        fun onPartial(text: String)
        /** Satu potongan ucapan selesai dikenali. */
        fun onFinal(text: String)
        /** Kekerasan suara 0..1, untuk penunjuk di layar. */
        fun onLevel(level: Float)
        /** Ada masalah; [fatal] berarti dikte berhenti. */
        fun onProblem(message: String, fatal: Boolean)
        /** Dikte benar-benar berhenti (setelah [stop] atau masalah). */
        fun onStopped()
    }

    private val main = Handler(Looper.getMainLooper())
    private var recognizer: SpeechRecognizer? = null
    private var running = false
    private var quietRestarts = 0

    fun start(): Boolean {
        if (!SpeechRecognizer.isRecognitionAvailable(context)) {
            listener.onProblem("Layanan pengenal suara tidak tersedia. Pastikan aplikasi Google terpasang dan aktif.", true)
            return false
        }
        val r = runCatching { SpeechRecognizer.createSpeechRecognizer(context) }.getOrNull()
        if (r == null) {
            listener.onProblem("Pengenal suara tidak bisa dibuka.", true)
            return false
        }
        r.setRecognitionListener(callback)
        recognizer = r
        running = true
        listen()
        return true
    }

    private fun intent() = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
        putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
        putExtra(RecognizerIntent.EXTRA_LANGUAGE, language)
        putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, language)
        putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
        putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
        putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, context.packageName)
        putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 2500L)
        putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 2000L)
    }

    private fun listen() {
        if (!running) return
        runCatching { recognizer?.startListening(intent()) }
            .onFailure { fail("Pengenal suara tidak bisa dimulai.") }
    }

    private fun restartSoon(delayMs: Long = 120L) {
        main.postDelayed({ listen() }, delayMs)
    }

    /** Selesai: ucapan terakhir masih diproses, lalu [Listener.onStopped]. */
    fun stop() {
        if (!running) return
        running = false
        runCatching { recognizer?.stopListening() }
        // Bila pengenal tidak mengirim hasil lagi, tutup sendiri.
        main.postDelayed({ finish() }, 2500L)
    }

    fun destroy() {
        running = false
        main.removeCallbacksAndMessages(null)
        runCatching { recognizer?.destroy() }
        recognizer = null
    }

    private var finished = false

    private fun finish() {
        if (finished) return
        finished = true
        destroy()
        listener.onStopped()
    }

    private fun fail(message: String) {
        running = false
        listener.onProblem(message, true)
        finish()
    }

    private val callback = object : RecognitionListener {
        override fun onReadyForSpeech(params: Bundle?) = Unit
        override fun onBeginningOfSpeech() { quietRestarts = 0 }
        override fun onRmsChanged(rmsdB: Float) {
            // rmsdB kira-kira -2..10.
            listener.onLevel(((rmsdB + 2f) / 12f).coerceIn(0f, 1f))
        }
        override fun onBufferReceived(buffer: ByteArray?) = Unit
        override fun onEndOfSpeech() = Unit
        override fun onEvent(eventType: Int, params: Bundle?) = Unit

        override fun onPartialResults(partialResults: Bundle?) {
            val t = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull().orEmpty()
            if (t.isNotBlank()) listener.onPartial(t)
        }

        override fun onResults(results: Bundle?) {
            val t = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull().orEmpty()
            if (t.isNotBlank()) listener.onFinal(t.trim())
            if (running) restartSoon() else finish()
        }

        override fun onError(error: Int) {
            when (error) {
                SpeechRecognizer.ERROR_NO_MATCH, SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> {
                    // Diam sebentar: dengarkan lagi.
                    if (running) {
                        quietRestarts++
                        restartSoon(if (quietRestarts > 20) 600L else 120L)
                    } else finish()
                }
                SpeechRecognizer.ERROR_RECOGNIZER_BUSY, SpeechRecognizer.ERROR_CLIENT -> {
                    if (running) restartSoon(400L) else finish()
                }
                SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS ->
                    fail("WInote belum diizinkan memakai mikrofon.")
                SpeechRecognizer.ERROR_NETWORK, SpeechRecognizer.ERROR_NETWORK_TIMEOUT, SpeechRecognizer.ERROR_SERVER ->
                    fail("Pengenal suara butuh internet. Sambungkan internet, atau unduh bahasa Indonesia untuk pengenalan suara offline di pengaturan Google.")
                ERROR_LANGUAGE_NOT_SUPPORTED, ERROR_LANGUAGE_UNAVAILABLE ->
                    fail("Bahasa ini belum tersedia di pengenal suara perangkat. Coba sambungkan internet sebentar.")
                else -> if (running) restartSoon(400L) else finish()
            }
        }
    }

    companion object {
        // Nilai dari Android 12 (API 31); ditulis langsung supaya tetap bisa dipakai di Android lama.
        private const val ERROR_LANGUAGE_NOT_SUPPORTED = 12
        private const val ERROR_LANGUAGE_UNAVAILABLE = 13
    }
}
