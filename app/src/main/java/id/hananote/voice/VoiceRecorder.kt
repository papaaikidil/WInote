package id.hananote.voice

import android.content.Context
import android.media.MediaPlayer
import android.media.MediaRecorder
import android.os.Build
import android.os.SystemClock
import java.io.File

/** Merekam suara ke berkas AAC (.m4a) yang kecil dan jernih untuk ucapan. */
class VoiceRecorder(private val context: Context) {
    private var rec: MediaRecorder? = null
    private var startedAt = 0L
    private var pausedAt = 0L
    private var pausedTotal = 0L
    var paused = false
        private set

    fun start(out: File) {
        @Suppress("DEPRECATION")
        val r = if (Build.VERSION.SDK_INT >= 31) MediaRecorder(context) else MediaRecorder()
        r.setAudioSource(MediaRecorder.AudioSource.MIC)
        r.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
        r.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
        r.setAudioChannels(1)
        r.setAudioSamplingRate(32000)
        r.setAudioEncodingBitRate(64000)
        r.setOutputFile(out.path)
        r.prepare()
        r.start()
        rec = r
        startedAt = SystemClock.elapsedRealtime()
        pausedTotal = 0L
        paused = false
    }

    /** Lama rekaman sejauh ini (tanpa waktu jeda). */
    fun elapsedMs(): Long {
        if (rec == null) return 0L
        val now = if (paused) pausedAt else SystemClock.elapsedRealtime()
        return now - startedAt - pausedTotal
    }

    /** Kekerasan suara 0..1. */
    fun level(): Float = runCatching { (rec?.maxAmplitude ?: 0) / 20000f }.getOrDefault(0f).coerceIn(0f, 1f)

    fun pause() {
        val r = rec ?: return
        if (paused) return
        runCatching { r.pause() }.onSuccess {
            paused = true
            pausedAt = SystemClock.elapsedRealtime()
        }
    }

    fun resume() {
        val r = rec ?: return
        if (!paused) return
        runCatching { r.resume() }.onSuccess {
            paused = false
            pausedTotal += SystemClock.elapsedRealtime() - pausedAt
        }
    }

    /** Hentikan rekaman. Hasil: lama rekaman (ms), atau -1 bila gagal atau terlalu pendek. */
    fun stop(): Long {
        val r = rec ?: return -1
        val ms = elapsedMs()
        rec = null
        val ok = runCatching { r.stop() }.isSuccess
        runCatching { r.release() }
        return if (ok && ms >= 400) ms else -1
    }

    fun cancel() {
        val r = rec ?: return
        rec = null
        runCatching { r.stop() }
        runCatching { r.release() }
    }

    companion object {
        /** Lama berkas audio (ms) menurut pemutar. */
        fun durationOf(file: File): Long = runCatching {
            val mp = MediaPlayer()
            try {
                mp.setDataSource(file.path)
                mp.prepare()
                mp.duration.toLong()
            } finally {
                mp.release()
            }
        }.getOrDefault(0L)
    }
}
