package id.goresan

import android.content.Context
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Catatan teknis untuk melacak masalah di perangkat: langkah-langkah terakhir, frame yang lambat,
 * aplikasi tertutup sendiri, dan "macet" (thread utama atau thread kanvas tidak merespons beberapa detik).
 * Semuanya disimpan di berkas di dalam aplikasi; tidak ada yang dikirim ke mana pun.
 */
object Diag {
    private const val MAX_LINES = 400
    private val lines = ArrayDeque<String>()
    private val clock = SimpleDateFormat("HH:mm:ss.SSS", Locale.US)
    @Volatile private var file: File? = null
    @Volatile private var crashFlag: File? = null
    @Volatile var info = ""
        private set

    // Penjaga macet
    @Volatile var glThread: Thread? = null
    @Volatile var glBusySince = 0L
    @Volatile private var mainTick = 0L
    private var mainStallLogged = false
    private var glStallLogged = false

    @Volatile private var installed = false

    fun install(ctx: Context) {
        if (installed) return
        installed = true
        val f = File(ctx.filesDir, "laporan.txt")
        file = f
        crashFlag = File(ctx.filesDir, "laporan.crash")
        runCatching {
            if (f.exists()) f.readLines().takeLast(MAX_LINES).forEach { lines.addLast(it) }
        }
        val version = runCatching {
            @Suppress("DEPRECATION")
            ctx.packageManager.getPackageInfo(ctx.packageName, 0).let { "${it.versionName} (${it.versionCode})" }
        }.getOrDefault("?")
        info = "WInote Gambar $version · ${Build.MANUFACTURER} ${Build.MODEL} · Android ${Build.VERSION.RELEASE}"
        log("=== Aplikasi dibuka · $info")

        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            runCatching {
                log("!!! TERTUTUP SENDIRI di utas ${thread.name}: ${error.javaClass.simpleName}: ${error.message}")
                error.stackTraceToString().lines().take(40).forEach { log("    $it") }
                flush()
                crashFlag?.writeText("1")
            }
            previous?.uncaughtException(thread, error)
        }
        startWatchdog()
    }

    fun log(msg: String) {
        val line = clock.format(Date()) + "  " + msg
        synchronized(lines) {
            lines.addLast(line)
            while (lines.size > MAX_LINES) lines.removeFirst()
        }
        scheduleFlush()
    }

    fun text(): String = buildString {
        appendLine(info)
        synchronized(lines) { lines.forEach { appendLine(it) } }
    }

    /** Aplikasi tertutup sendiri pada sesi sebelumnya? (sekali saja) */
    fun takeCrashFlag(): Boolean {
        val f = crashFlag ?: return false
        if (!f.exists()) return false
        f.delete()
        return true
    }

    fun clear() {
        synchronized(lines) { lines.clear() }
        flush()
    }

    private val io = Handler(android.os.HandlerThread("goresan-log").apply { start() }.looper)
    private val flushRunnable = Runnable { flush() }

    private fun scheduleFlush() {
        io.removeCallbacks(flushRunnable)
        io.postDelayed(flushRunnable, 1500)
    }

    fun flush() {
        val f = file ?: return
        val snapshot = synchronized(lines) { lines.joinToString("\n") }
        runCatching { f.writeText(snapshot) }
    }

    private fun startWatchdog() {
        val main = Handler(Looper.getMainLooper())
        mainTick = SystemClock.uptimeMillis()
        val t = Thread({
            while (true) {
                main.post { mainTick = SystemClock.uptimeMillis() }
                try { Thread.sleep(1000) } catch (_: InterruptedException) { return@Thread }
                val now = SystemClock.uptimeMillis()
                // Thread utama (antarmuka) tidak merespons
                if (now - mainTick > 2500) {
                    if (!mainStallLogged) {
                        mainStallLogged = true
                        log("!!! Antarmuka macet ${now - mainTick} ms. Posisi thread utama:")
                        Looper.getMainLooper().thread.stackTrace.take(25).forEach { log("    $it") }
                        flush()
                    }
                } else if (mainStallLogged) {
                    mainStallLogged = false
                    log("Antarmuka kembali merespons")
                }
                // Thread kanvas sibuk terlalu lama dalam satu frame
                val since = glBusySince
                val gt = glThread
                if (since > 0 && gt != null && now - since > 2500) {
                    if (!glStallLogged) {
                        glStallLogged = true
                        log("!!! Kanvas macet ${now - since} ms. Posisi thread kanvas:")
                        gt.stackTrace.take(25).forEach { log("    $it") }
                        flush()
                    }
                } else if (glStallLogged && since == 0L) {
                    glStallLogged = false
                    log("Kanvas kembali merespons")
                }
            }
        }, "goresan-penjaga")
        t.isDaemon = true
        t.start()
    }
}
