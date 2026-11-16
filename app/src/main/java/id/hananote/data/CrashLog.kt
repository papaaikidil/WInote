package id.hananote.data

import android.content.Context
import android.os.Build
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Laporan galat: bila aplikasi tertutup sendiri, penyebabnya (beserta jejak langkah terakhir) disimpan
 * ke berkas, lalu ditawarkan untuk dibagikan saat aplikasi dibuka lagi. Tidak ada yang dikirim
 * otomatis; pengguna yang memilih membagikannya.
 */
object CrashLog {
    private const val MAX_TRAIL = 40
    private const val MAX_FILES = 10
    private val trail = ArrayDeque<String>()
    @Volatile private var dir: File? = null
    @Volatile private var appInfo: String = ""
    private val clock = SimpleDateFormat("HH:mm:ss", Locale.US)

    /** Pasang penangkap galat. Dipanggil sekali saat aplikasi mulai. */
    fun install(context: Context) {
        val d = File(context.filesDir, "crash").apply { mkdirs() }
        dir = d
        val version = runCatching {
            @Suppress("DEPRECATION")
            context.packageManager.getPackageInfo(context.packageName, 0).let { "${it.versionName} (${it.versionCode})" }
        }.getOrDefault("?")
        appInfo = "WInote $version · ${Build.MANUFACTURER} ${Build.MODEL} · Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})"
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            runCatching { write("crash", "Aplikasi tertutup sendiri (utas ${thread.name})", error) }
            previous?.uncaughtException(thread, error)
        }
    }

    /** Catat satu langkah pengguna (misalnya "buka halaman 3"), supaya laporan galat menunjukkan apa yang terjadi sebelumnya. */
    fun note(event: String) {
        synchronized(trail) {
            trail.addLast(clock.format(Date()) + "  " + event)
            while (trail.size > MAX_TRAIL) trail.removeFirst()
        }
    }

    /** Simpan galat yang tertangkap (aplikasi tidak tertutup) tetapi perlu diketahui. */
    fun nonFatal(where: String, error: Throwable) {
        runCatching { write("galat", where, error) }
    }

    private fun write(kind: String, title: String, error: Throwable) {
        val d = dir ?: return
        val stamp = SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.US).format(Date())
        val steps = synchronized(trail) { trail.toList() }
        val text = buildString {
            appendLine("== Laporan galat WInote ==")
            appendLine(title)
            appendLine("Waktu: " + SimpleDateFormat("d MMM yyyy HH:mm:ss", Locale("id", "ID")).format(Date()))
            appendLine(appInfo)
            appendLine()
            appendLine("Langkah terakhir:")
            if (steps.isEmpty()) appendLine("  (tidak ada)") else steps.forEach { appendLine("  $it") }
            appendLine()
            appendLine("Galat:")
            append(error.stackTraceToString().take(24_000))
        }
        File(d, "$kind-$stamp.txt").writeText(text)
        // Simpan beberapa laporan terakhir saja.
        d.listFiles()?.sortedByDescending { it.name.substringAfter('-') }?.drop(MAX_FILES)?.forEach { it.delete() }
    }

    /** Laporan yang belum pernah ditunjukkan ke pengguna (hanya yang membuat aplikasi tertutup). */
    fun unseen(context: Context): List<File> {
        val seen = Repository.get(context).prefs.getString("crash_seen", "").orEmpty()
        return all(context).filter { it.name.startsWith("crash-") && it.name > seen }
    }

    fun all(context: Context): List<File> =
        (File(context.filesDir, "crash").listFiles()?.toList() ?: emptyList()).sortedByDescending { it.name.substringAfter('-') }

    fun markSeen(context: Context) {
        val newest = all(context).filter { it.name.startsWith("crash-") }.maxOfOrNull { it.name } ?: return
        Repository.get(context).prefs.edit().putString("crash_seen", newest).apply()
    }

    fun clear(context: Context) {
        all(context).forEach { it.delete() }
    }

    /** Gabungan beberapa laporan sebagai teks untuk dibagikan atau disalin. */
    fun combined(files: List<File>): String =
        files.take(5).joinToString("\n\n") { runCatching { it.readText() }.getOrDefault("") }.take(90_000)
}
