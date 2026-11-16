package id.hananote.data

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Keadaan cadangan untuk layar pengaturan. */
data class BackupState(
    val folderUri: String? = null,
    val folderName: String? = null,
    val auto: Boolean = true,
    /** Selang cadangan otomatis dalam hari (1 = harian, 7 = mingguan). */
    val everyDays: Int = 1,
    val keep: Int = 5,
    val last: Long = 0L,
    val lastName: String? = null,
    val busy: String? = null,
    val message: String? = null,
    val error: String? = null,
)

/**
 * Cadangan semua catatan ke folder pilihan di perangkat (lewat pemilih folder Android), otomatis
 * atau manual, serta simpan ke berkas pilihan (misalnya Google Drive) dan pulihkan.
 *
 * Tiap cadangan adalah satu berkas "WInote-cadangan-tanggal-jam.zip" (dulu "HanaNote-cadangan-…"). Cadangan otomatis hanya dibuat
 * bila ada perubahan sejak cadangan terakhir dan selangnya sudah lewat; yang lama dihapus sampai
 * tersisa [BackupState.keep] berkas terbaru.
 */
object Backups {
    private const val PREFIX = "WInote-cadangan-"
    /** Nama cadangan sebelum aplikasi berganti nama; tetap ikut dirapikan dan bisa dipulihkan. */
    private const val OLD_PREFIX = "HanaNote-cadangan-"
    private const val MIME = "application/zip"

    private val mutex = Mutex()
    private val _state = MutableStateFlow(BackupState())
    val state: StateFlow<BackupState> = _state.asStateFlow()
    @Volatile private var loaded = false

    private fun repo(context: Context) = Repository.get(context)

    /** Baca pengaturan tersimpan (sekali). */
    fun init(context: Context) {
        if (loaded) return
        loaded = true
        val p = repo(context).prefs
        _state.value = BackupState(
            folderUri = p.getString("backup_tree", null),
            folderName = p.getString("backup_tree_name", null),
            auto = p.getBoolean("backup_auto", true),
            everyDays = p.getInt("backup_days", 1),
            keep = p.getInt("backup_keep", 5),
            last = p.getLong("backup_last", 0L),
            lastName = p.getString("backup_last_name", null),
        )
    }

    private fun update(block: (BackupState) -> BackupState) { _state.value = block(_state.value) }

    fun clearMessage() = update { it.copy(message = null, error = null) }

    /** Simpan folder pilihan pengguna dan minta izin tetap untuk menulis ke sana. */
    fun setFolder(context: Context, tree: Uri) {
        init(context)
        val cr = context.contentResolver
        runCatching {
            cr.takePersistableUriPermission(tree, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
        }
        // Lepas izin folder lama yang tidak dipakai lagi.
        _state.value.folderUri?.let { old ->
            if (old != tree.toString()) runCatching {
                cr.releasePersistableUriPermission(Uri.parse(old), Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
            }
        }
        val name = folderName(context, tree)
        repo(context).prefs.edit().putString("backup_tree", tree.toString()).putString("backup_tree_name", name).apply()
        update { it.copy(folderUri = tree.toString(), folderName = name, error = null) }
    }

    fun setAuto(context: Context, on: Boolean) {
        repo(context).prefs.edit().putBoolean("backup_auto", on).apply()
        update { it.copy(auto = on) }
    }

    fun setEveryDays(context: Context, days: Int) {
        repo(context).prefs.edit().putInt("backup_days", days).apply()
        update { it.copy(everyDays = days) }
    }

    fun setKeep(context: Context, keep: Int) {
        repo(context).prefs.edit().putInt("backup_keep", keep).apply()
        update { it.copy(keep = keep) }
    }

    private fun folderName(context: Context, tree: Uri): String = runCatching {
        val doc = DocumentsContract.buildDocumentUriUsingTree(tree, DocumentsContract.getTreeDocumentId(tree))
        context.contentResolver.query(doc, arrayOf(DocumentsContract.Document.COLUMN_DISPLAY_NAME), null, null, null)?.use { c ->
            if (c.moveToFirst()) c.getString(0) else null
        }
    }.getOrNull() ?: "Folder pilihan"

    private fun hasAccess(context: Context, tree: Uri): Boolean =
        context.contentResolver.persistedUriPermissions.any { it.uri == tree && it.isWritePermission }

    private fun stampName(): String = PREFIX + SimpleDateFormat("yyyy-MM-dd-HHmm", Locale.US).format(Date()) + ".zip"

    /** Nama berkas saran untuk "Simpan cadangan ke…". */
    fun suggestedName(): String = stampName()

    /** Cadangan otomatis bila sudah waktunya dan ada perubahan. Aman dipanggil sesering apa pun. */
    suspend fun maybeAuto(context: Context) {
        init(context)
        val st = _state.value
        val tree = st.folderUri ?: return
        if (!st.auto || st.busy != null) return
        val repo = repo(context)
        val changed = repo.lastChange()
        if (changed == 0L || changed <= st.last) return
        val due = st.last + st.everyDays * 24L * 3600_000L - 30L * 60_000L
        if (System.currentTimeMillis() < due) return
        backupToFolder(context, Uri.parse(tree), auto = true)
    }

    /** Buat cadangan sekarang di folder pilihan. */
    suspend fun backupNow(context: Context) {
        init(context)
        val tree = _state.value.folderUri ?: run { update { it.copy(error = "Pilih folder cadangan dulu.") }; return }
        backupToFolder(context, Uri.parse(tree), auto = false)
    }

    private suspend fun backupToFolder(context: Context, tree: Uri, auto: Boolean) = mutex.withLock {
        val cr = context.contentResolver
        if (!hasAccess(context, tree)) {
            update { it.copy(error = "Folder cadangan tidak bisa diakses lagi. Pilih ulang folder.") }
            return@withLock
        }
        update { it.copy(busy = "Mencadangkan…", error = null, message = null) }
        val repo = repo(context)
        try {
            val name = stampName()
            val count = withContext(Dispatchers.IO) {
                val parent = DocumentsContract.buildDocumentUriUsingTree(tree, DocumentsContract.getTreeDocumentId(tree))
                val doc = DocumentsContract.createDocument(cr, parent, MIME, name) ?: error("Berkas cadangan tidak bisa dibuat.")
                try {
                    val n = cr.openOutputStream(doc, "w")?.use { out -> withContext(repo.writeDispatcher) { repo.writeBackup(out) } }
                        ?: error("Berkas cadangan tidak bisa ditulis.")
                    n
                } catch (e: Exception) {
                    runCatching { DocumentsContract.deleteDocument(cr, doc) }
                    throw e
                }
            }
            withContext(Dispatchers.IO) { prune(context, tree, _state.value.keep) }
            val now = System.currentTimeMillis()
            repo.prefs.edit().putLong("backup_last", now).putString("backup_last_name", name).apply()
            update {
                it.copy(
                    busy = null, last = now, lastName = name, error = null,
                    message = if (auto) null else "Cadangan dibuat: $name ($count catatan).",
                )
            }
        } catch (e: Exception) {
            CrashLog.nonFatal(if (auto) "Cadangan otomatis gagal" else "Cadangan gagal", e)
            update { it.copy(busy = null, error = "Cadangan gagal: ${e.message ?: e.javaClass.simpleName}") }
        }
    }

    /** Hapus cadangan lama di folder, sisakan [keep] berkas terbaru. */
    private fun prune(context: Context, tree: Uri, keep: Int) {
        val cr = context.contentResolver
        val treeId = DocumentsContract.getTreeDocumentId(tree)
        val children = DocumentsContract.buildChildDocumentsUriUsingTree(tree, treeId)
        val found = ArrayList<Pair<String, String>>()
        cr.query(
            children,
            arrayOf(DocumentsContract.Document.COLUMN_DOCUMENT_ID, DocumentsContract.Document.COLUMN_DISPLAY_NAME),
            null, null, null,
        )?.use { c ->
            while (c.moveToNext()) {
                val id = c.getString(0) ?: continue
                val name = c.getString(1) ?: continue
                if ((name.startsWith(PREFIX) || name.startsWith(OLD_PREFIX)) && name.endsWith(".zip")) found.add(id to name)
            }
        }
        found.sortByDescending { it.second.removePrefix(PREFIX).removePrefix(OLD_PREFIX) }
        for ((id, _) in found.drop(keep.coerceAtLeast(1))) {
            runCatching { DocumentsContract.deleteDocument(cr, DocumentsContract.buildDocumentUriUsingTree(tree, id)) }
        }
    }

    /** Simpan satu cadangan ke berkas yang dipilih pengguna (bisa di Google Drive atau kartu memori). */
    suspend fun saveTo(context: Context, target: Uri) = mutex.withLock {
        init(context)
        update { it.copy(busy = "Menyimpan cadangan…", error = null, message = null) }
        val repo = repo(context)
        try {
            val count = withContext(Dispatchers.IO) {
                context.contentResolver.openOutputStream(target, "w")?.use { out ->
                    withContext(repo.writeDispatcher) { repo.writeBackup(out) }
                } ?: error("Berkas tidak bisa ditulis.")
            }
            update { it.copy(busy = null, message = "Cadangan tersimpan ($count catatan).") }
        } catch (e: Exception) {
            CrashLog.nonFatal("Simpan cadangan ke berkas gagal", e)
            update { it.copy(busy = null, error = "Gagal menyimpan cadangan: ${e.message ?: e.javaClass.simpleName}") }
        }
    }

    /** Pulihkan dari berkas cadangan. Catatan yang ada tidak dihapus. */
    suspend fun restore(context: Context, source: Uri) = mutex.withLock {
        init(context)
        update { it.copy(busy = "Memulihkan…", error = null, message = null) }
        val repo = repo(context)
        val result = withContext(Dispatchers.IO) {
            runCatching {
                context.contentResolver.openInputStream(source)?.use { inp ->
                    withContext(repo.writeDispatcher) { repo.restoreBackup(inp) }
                }
            }.onFailure { CrashLog.nonFatal("Pulihkan cadangan gagal", it) }.getOrNull()
        }
        if (result == null) {
            update { it.copy(busy = null, error = "Berkas itu bukan cadangan WInote, atau rusak.") }
        } else {
            val parts = ArrayList<String>()
            if (result.added > 0) parts.add("${result.added} catatan ditambahkan")
            if (result.updated > 0) parts.add("${result.updated} diperbarui")
            if (result.kept > 0) parts.add("${result.kept} sudah sama atau lebih baru di sini")
            update { it.copy(busy = null, message = "Pemulihan selesai: " + parts.ifEmpty { listOf("tidak ada catatan baru") }.joinToString(", ") + ".") }
        }
    }
}
