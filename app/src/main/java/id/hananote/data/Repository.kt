package id.hananote.data

import id.goresan.engine.Storage as ArtStorage
import android.content.Context
import android.content.SharedPreferences
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import android.provider.OpenableColumns
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.UUID
import id.hananote.ink.SoftMasks
import java.util.concurrent.Executors

/**
 * Semua data tersimpan di memori internal perangkat, jadi aplikasi berjalan penuh tanpa internet.
 *
 * hana/books.json            daftar buku (folder berbentuk buku)
 * hana/notes.json            daftar catatan
 * hana/notes/<id>/pages.json daftar lembar dalam satu catatan
 * hana/notes/<id>/<page>.bin goresan per lembar (biner, cepat dibaca)
 * hana/notes/<id>/thumb.png  sampul kecil catatan
 */
class Repository private constructor(context: Context) {

    private val appContext: Context = context.applicationContext

    /** Widget layar utama ikut diperbarui saat daftar catatan atau gambar kecilnya berubah. */
    private fun widgetsChanged() {
        runCatching { id.hananote.widget.HanaWidgets.refreshSoon(appContext) }
    }

    private val root = File(context.filesDir, "hana").apply { mkdirs() }
    private val notesRoot = File(root, "notes").apply { mkdirs() }
    private val booksFile = File(root, "books.json")
    private val notesFile = File(root, "notes.json")
    private val lock = Any()

    /** Lingkup kerja latar yang tetap hidup walau layar ditutup. */
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /** Semua penulisan berkas lewat satu jalur berurutan, supaya urutan simpan tidak tertukar. */
    val writeDispatcher = Executors.newSingleThreadExecutor().asCoroutineDispatcher()
    val writeScope = CoroutineScope(SupervisorJob() + writeDispatcher)
    val prefs: SharedPreferences = context.getSharedPreferences("hana_prefs", Context.MODE_PRIVATE)

    private val _books = MutableStateFlow<List<Book>>(emptyList())
    val books: StateFlow<List<Book>> = _books.asStateFlow()

    private val _notes = MutableStateFlow<List<Note>>(emptyList())
    val notes: StateFlow<List<Note>> = _notes.asStateFlow()

    private val _thumbsVersion = MutableStateFlow(0L)
    val thumbsVersion: StateFlow<Long> = _thumbsVersion.asStateFlow()

    // Isi Sampah (dideklarasikan sebelum init, karena init langsung memuatnya).
    private val trashFile = File(root, "trash.json")
    private val _trashNotes = MutableStateFlow<List<TrashedNote>>(emptyList())
    private val _trashBooks = MutableStateFlow<List<TrashedBook>>(emptyList())
    val trashNotes: StateFlow<List<TrashedNote>> = _trashNotes.asStateFlow()
    val trashBooks: StateFlow<List<TrashedBook>> = _trashBooks.asStateFlow()

    private val _theme = MutableStateFlow(
        ThemeSettings(
            accent = runCatching { Accent.valueOf(prefs.getString("theme_accent", "HANA") ?: "HANA") }.getOrDefault(Accent.HANA),
            mode = runCatching { ThemeMode.valueOf(prefs.getString("theme_mode", "SYSTEM") ?: "SYSTEM") }.getOrDefault(ThemeMode.SYSTEM),
            serifTitles = prefs.getBoolean("theme_serif", true),
        )
    )
    val theme: StateFlow<ThemeSettings> = _theme.asStateFlow()

    // ---------- font ----------

    private val fontsDir = File(root, "fonts").apply { mkdirs() }

    private val _fonts = MutableStateFlow(scanFonts())
    /** Font bawaan ditambah font yang ditambahkan pengguna. */
    val fonts: StateFlow<List<FontEntry>> = _fonts.asStateFlow()

    private fun scanFonts(): List<FontEntry> {
        FontRegistry.dir = fontsDir
        val imported = (fontsDir.listFiles() ?: emptyArray())
            .filter { it.isFile && it.extension.lowercase() in setOf("ttf", "otf", "ttc") }
            .sortedBy { it.name.lowercase() }
            .map { FontEntry("file:" + it.name, it.nameWithoutExtension.replace('_', ' '), imported = true) }
        return FontRegistry.builtIn + imported
    }

    /** Hasil menambah font: font yang berhasil ditambahkan, atau [error] (siap ditampilkan) bila tidak ada. */
    class FontImport(val added: List<FontEntry>, val error: String?)

    /**
     * Tambahkan font dari berkas pilihan pengguna. Isinya dikenali dari bait awalnya, bukan dari nama berkas:
     * TTF/OTF/TTC, font web WOFF (diubah ke TTF/OTF), atau ZIP berisi font (lihat [id.goresan.engine.FontFiles]).
     * Tiap font diperiksa dulu bisa dibaca Android sebelum disimpan.
     */
    fun importFont(context: Context, uri: Uri): FontImport {
        val cr = context.contentResolver
        val display = runCatching {
            cr.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
                if (c.moveToFirst()) c.getString(0) else null
            }
        }.getOrNull() ?: "Font"
        val input = runCatching { cr.openInputStream(uri) }.getOrNull()
            ?: return FontImport(emptyList(), "Berkas itu tidak bisa dibuka.")
        val bytes = input.use { id.goresan.engine.FontFiles.readLimited(it) }
            ?: return FontImport(emptyList(), "Berkas font terlalu besar (lebih dari 40 MB).")
        val fonts = try {
            id.goresan.engine.FontFiles.extract(display, bytes)
        } catch (e: id.goresan.engine.FontFiles.NotFont) {
            CrashLog.note("impor font gagal: $display, ${bytes.size} bait, awal ${id.goresan.engine.FontFiles.head(bytes)}: ${e.message}")
            return FontImport(emptyList(), e.message)
        }
        val saved = ArrayList<String>()
        for (f in fonts) {
            val safe = f.name.replace(Regex("[^A-Za-z0-9 _-]"), "").trim().ifEmpty { "Font" }.replace(' ', '_')
            val ext = "." + f.ext
            var target = File(fontsDir, safe + ext)
            var k = 2
            while (target.exists()) target = File(fontsDir, "${safe}_$k$ext").also { k++ }
            runCatching { target.writeBytes(f.bytes) }.onFailure { target.delete(); return@onFailure }
            val ok = target.exists() && runCatching { android.graphics.Typeface.Builder(target).build() }.getOrNull() != null
            if (ok) saved.add(target.name) else target.delete()
        }
        _fonts.value = scanFonts()
        if (saved.isEmpty()) {
            CrashLog.note("impor font ditolak Android: $display, ${bytes.size} bait, awal ${id.goresan.engine.FontFiles.head(bytes)}")
            return FontImport(emptyList(), "Font ini tidak bisa dibaca Android. Berkasnya mungkin rusak atau tidak lengkap.")
        }
        val names = saved.map { "file:$it" }.toSet()
        return FontImport(_fonts.value.filter { it.id in names }, null)
    }

    fun deleteFont(entry: FontEntry) {
        if (!entry.imported) return
        File(fontsDir, entry.id.removePrefix("file:")).delete()
        FontRegistry.forget(entry.id)
        _fonts.value = scanFonts()
    }

    fun setTheme(t: ThemeSettings) {
        _theme.value = t
        prefs.edit()
            .putString("theme_accent", t.accent.name)
            .putString("theme_mode", t.mode.name)
            .putBoolean("theme_serif", t.serifTitles)
            .apply()
    }

    init {
        load()
        if (_books.value.isEmpty()) createBook("Buku Pertama", BookColors[0])
        purgeOldTrash()
    }

    // ---------- sampah ----------

    /** Catatan di Sampah; [withBook] berisi id bukunya bila ikut terhapus bersama bukunya. */
    data class TrashedNote(val note: Note, val at: Long, val withBook: String? = null)
    data class TrashedBook(val book: Book, val at: Long)


    /** Pindahkan catatan ke Sampah (berkasnya tetap ada sampai Sampah dikosongkan). */
    fun deleteNote(id: String) = synchronized(lock) {
        val n = _notes.value.firstOrNull { it.id == id } ?: return@synchronized
        _notes.value = _notes.value.filter { it.id != id }
        _trashNotes.value = _trashNotes.value.filter { it.note.id != id } + TrashedNote(n, System.currentTimeMillis())
        saveNotes()
        saveTrash()
        widgetsChanged()
    }

    /** Pindahkan buku beserta semua catatannya ke Sampah. */
    fun deleteBook(id: String) = synchronized(lock) {
        val b = _books.value.firstOrNull { it.id == id } ?: return@synchronized
        val now = System.currentTimeMillis()
        val inBook = _notes.value.filter { it.bookId == id }
        _notes.value = _notes.value.filter { it.bookId != id }
        _books.value = _books.value.filter { it.id != id }
        _trashNotes.value = _trashNotes.value + inBook.map { TrashedNote(it, now, withBook = id) }
        _trashBooks.value = _trashBooks.value.filter { it.book.id != id } + TrashedBook(b, now)
        saveNotes()
        saveBooks()
        saveTrash()
        if (_books.value.isEmpty()) createBook("Buku Pertama", BookColors[0])
        widgetsChanged()
    }

    /**
     * Kembalikan catatan dari Sampah ke bukunya; bila bukunya sudah tidak ada, ke buku pertama di rak.
     * Hasil: buku tempat catatan itu kembali.
     */
    fun restoreNote(id: String): String? = synchronized(lock) {
        val t = _trashNotes.value.firstOrNull { it.note.id == id } ?: return@synchronized null
        val target = _books.value.firstOrNull { it.id == t.note.bookId }
            ?: shelfOrder(_books.value).firstOrNull()
            ?: createBook("Catatan", BookColors[0])
        val n = t.note.copy(bookId = target.id, order = topNoteOrder(target.id), pinned = false)
        _trashNotes.value = _trashNotes.value.filter { it.note.id != id }
        _notes.value = _notes.value + n
        saveNotes()
        saveTrash()
        _thumbsVersion.value = _thumbsVersion.value + 1
        widgetsChanged()
        target.id
    }

    /** Kembalikan buku beserta catatan yang ikut terhapus bersamanya. */
    fun restoreBook(id: String) = synchronized(lock) {
        val t = _trashBooks.value.firstOrNull { it.book.id == id } ?: return@synchronized
        val back = _trashNotes.value.filter { it.withBook == id }.map { it.note }
        _trashBooks.value = _trashBooks.value.filter { it.book.id != id }
        _trashNotes.value = _trashNotes.value.filter { it.withBook != id }
        _books.value = _books.value.filter { it.id != id } + t.book
        _notes.value = _notes.value.filter { n -> back.none { it.id == n.id } } + back
        saveBooks()
        saveNotes()
        saveTrash()
        _thumbsVersion.value = _thumbsVersion.value + 1
        widgetsChanged()
    }

    /** Hapus permanen satu catatan dari Sampah. */
    fun deleteNoteForever(id: String) = synchronized(lock) {
        val t = _trashNotes.value.firstOrNull { it.note.id == id } ?: return@synchronized
        purge(t.note)
        _trashNotes.value = _trashNotes.value.filter { it.note.id != id }
        saveTrash()
    }

    /** Hapus permanen buku dari Sampah, beserta catatan yang ikut terhapus bersamanya. */
    fun deleteBookForever(id: String) = synchronized(lock) {
        val t = _trashBooks.value.firstOrNull { it.book.id == id } ?: return@synchronized
        _trashNotes.value.filter { it.withBook == id }.forEach { purge(it.note) }
        _trashNotes.value = _trashNotes.value.filter { it.withBook != id }
        _trashBooks.value = _trashBooks.value.filter { it.book.id != id }
        saveTrash()
        dropCoverIfUnused(t.book.cover)
    }

    /** Kosongkan Sampah: semua isinya dihapus permanen. */
    fun emptyTrash() = synchronized(lock) {
        _trashNotes.value.forEach { purge(it.note) }
        val covers = _trashBooks.value.map { it.book.cover }
        _trashNotes.value = emptyList()
        _trashBooks.value = emptyList()
        saveTrash()
        covers.forEach { dropCoverIfUnused(it) }
    }

    /** Isi Sampah yang sudah lebih dari [TRASH_DAYS] hari dihapus permanen. */
    private fun purgeOldTrash() = synchronized(lock) {
        val limit = System.currentTimeMillis() - TRASH_DAYS * 24L * 3600_000L
        _trashBooks.value.filter { it.at < limit }.forEach { deleteBookForever(it.book.id) }
        _trashNotes.value.filter { it.at < limit && it.withBook == null }.forEach { deleteNoteForever(it.note.id) }
    }

    private fun dropCoverIfUnused(cover: String) {
        if (cover.isBlank()) return
        if (_books.value.any { it.cover == cover } || _trashBooks.value.any { it.book.cover == cover }) return
        coverFile(cover)?.delete()
    }

    private fun saveTrash() {
        val books = JSONArray()
        _trashBooks.value.forEach { books.put(bookJson(it.book).put("at", it.at)) }
        val notes = JSONArray()
        _trashNotes.value.forEach { t ->
            notes.put(noteJson(t.note).put("at", t.at).apply { t.withBook?.let { put("withBook", it) } })
        }
        writeAtomic(trashFile, JSONObject().put("books", books).put("notes", notes).toString())
    }

    private fun loadTrash() {
        if (!trashFile.exists()) return
        runCatching {
            val o = JSONObject(trashFile.readText())
            val books = o.optJSONArray("books") ?: JSONArray()
            val notes = o.optJSONArray("notes") ?: JSONArray()
            _trashBooks.value = (0 until books.length()).mapNotNull { i ->
                books.optJSONObject(i)?.let { j -> bookFrom(j)?.let { TrashedBook(it, j.optLong("at")) } }
            }
            _trashNotes.value = (0 until notes.length()).mapNotNull { i ->
                notes.optJSONObject(i)?.let { j ->
                    noteFrom(j)?.let { TrashedNote(it, j.optLong("at"), j.optString("withBook", "").ifBlank { null }) }
                }
            }
        }
    }

    // ---------- buku ----------

    fun createBook(title: String, color: Int, cover: String = ""): Book = synchronized(lock) {
        val now = System.currentTimeMillis()
        // Buku baru di ujung rak.
        val order = (_books.value.maxOfOrNull { it.order } ?: 0L) + 1
        val b = Book(UUID.randomUUID().toString(), title.ifBlank { "Buku baru" }, color, now, now, order = order, cover = cover)
        _books.value = _books.value + b
        saveBooks()
        pruneCovers()
        b
    }

    fun updateBook(id: String, title: String, color: Int, cover: String? = null) = synchronized(lock) {
        _books.value = _books.value.map {
            if (it.id == id) it.copy(
                title = title.ifBlank { it.title }, color = color, updated = System.currentTimeMillis(),
                cover = cover ?: it.cover,
            ) else it
        }
        saveBooks()
        pruneCovers()
    }

    // ---------- gambar sampul buku ----------

    /** Getter (bukan nilai tetap) karena dipakai saat buku pertama dibuat di init, sebelum baris ini. */
    private val coversDir: File get() = File(root, "covers").apply { mkdirs() }

    fun coverFile(name: String): File? = name.takeIf { it.isNotBlank() && SAFE_NAME.matches(it) }?.let { File(coversDir, it) }

    /** Simpan gambar pilihan pengguna sebagai sampul (diperkecil). Hasil: nama berkas, atau null bila gagal. */
    fun importCover(context: Context, uri: Uri): String? {
        val bmp = decodeOriented(context, uri, 1200) ?: return null
        val name = saveBitmap(bmp, coversDir, "cov_")
        bmp.recycle()
        return name
    }

    /** Hapus gambar sampul yang tidak dipakai buku mana pun (misalnya dipilih lalu dibatalkan). */
    private fun pruneCovers() {
        val used = _books.value.mapTo(HashSet()) { it.cover }
        _trashBooks.value.forEach { used.add(it.book.cover) }
        val old = System.currentTimeMillis() - 60 * 60 * 1000L
        coversDir.listFiles()?.forEach { f ->
            // Yang baru dipilih (dialog masih terbuka) dibiarkan dulu.
            if (f.isFile && f.name !in used && f.lastModified() < old) f.delete()
        }
    }

    /** Buang sampul yang tidak dipakai lagi sekarang juga (dipanggil saat dialog buku ditutup). */
    fun dropUnusedCover(name: String) = synchronized(lock) {
        if (name.isBlank() || _books.value.any { it.cover == name } || _trashBooks.value.any { it.book.cover == name }) return@synchronized
        coverFile(name)?.delete()
    }

    /** Simpan urutan rak baru: [ids] berisi semua buku sesuai urutan yang tampil. */
    fun reorderBooks(ids: List<String>) = synchronized(lock) {
        val pos = ids.withIndex().associate { (i, id) -> id to i.toLong() }
        val extra = ids.size.toLong()
        _books.value = _books.value.map { b -> b.copy(order = pos[b.id] ?: (extra + b.order)) }
        saveBooks()
    }

    /** Sematkan buku di depan rak, atau lepaskan. Yang baru disematkan/dilepas ditaruh paling depan kelompoknya. */
    fun setBookPinned(id: String, pinned: Boolean) = synchronized(lock) {
        val first = (_books.value.filter { it.pinned == pinned && it.id != id }.minOfOrNull { it.order } ?: 0L) - 1
        _books.value = _books.value.map { if (it.id == id) it.copy(pinned = pinned, order = first) else it }
        saveBooks()
    }


    // ---------- catatan ----------

    fun note(id: String): Note? = _notes.value.firstOrNull { it.id == id }

    fun createNote(
        bookId: String,
        title: String,
        widthPt: Float,
        heightPt: Float,
        template: PaperTemplate,
        paperColor: Int,
    ): Note = synchronized(lock) {
        val now = System.currentTimeMillis()
        val note = Note(UUID.randomUUID().toString(), bookId, title.ifBlank { "Catatan" }, now, now, 1, order = topNoteOrder(bookId))
        val page = PageMeta(UUID.randomUUID().toString(), widthPt, heightPt, template, paperColor)
        File(notesRoot, note.id).mkdirs()
        writePages(note.id, listOf(page))
        _notes.value = _notes.value + note
        saveNotes()
        touchBook(bookId, now)
        widgetsChanged()
        note
    }

    /**
     * Catatan baru dari widget: kertas seperti pilihan terakhir saat membuat catatan, di buku yang terakhir
     * dipakai (atau buku pertama di rak; bila belum ada buku, dibuatkan).
     */
    fun createQuickNote(): Note {
        val book = _notes.value.maxByOrNull { it.updated }?.bookId?.let { id -> _books.value.firstOrNull { it.id == id } }
            ?: shelfOrder(_books.value).firstOrNull()
            ?: createBook("Catatan", BookColors[0])
        val size = runCatching { PaperSize.valueOf(prefs.getString("paper_size", "A4") ?: "A4") }.getOrNull()
        val land = prefs.getBoolean("paper_land", false)
        fun mm(key: String) = (prefs.getString(key, "200") ?: "200").trim().replace(',', '.').toFloatOrNull()
            ?.takeIf { it >= PaperSize.CUSTOM_MIN_MM && it <= PaperSize.CUSTOM_MAX_MM }
        val w = size?.widthPt(land) ?: mm("paper_custom_w")?.let { PaperSize.mmToPt(it) } ?: PaperSize.A4.widthPt(false)
        val h = size?.heightPt(land) ?: mm("paper_custom_h")?.let { PaperSize.mmToPt(it) } ?: PaperSize.A4.heightPt(false)
        val tpl = runCatching { PaperTemplate.valueOf(prefs.getString("paper_tpl", "LINED") ?: "LINED") }.getOrDefault(PaperTemplate.LINED)
        val color = prefs.getInt("paper_color", PaperColors[0].second)
        val title = "Catatan " + java.text.SimpleDateFormat("d MMM", java.util.Locale.forLanguageTag("id-ID")).format(System.currentTimeMillis())
        return createNote(book.id, title, w, h, tpl, color)
    }

    /**
     * Salin PDF ke folder catatan dan baca ukuran halamannya. Hasil: nama berkas dan ukuran tiap
     * halaman (poin), atau null bila PDF tidak bisa dibuka (misalnya rusak atau berkata sandi).
     */
    fun importPdf(context: Context, noteId: String, uri: Uri): Pair<String, List<Pair<Float, Float>>>? {
        val dir = noteDir(noteId).apply { mkdirs() }
        val name = "doc_" + UUID.randomUUID().toString() + ".pdf"
        val f = File(dir, name)
        val ok = context.contentResolver.openInputStream(uri)?.use { inp -> FileOutputStream(f).use { inp.copyTo(it, 64 * 1024) }; true } ?: false
        if (!ok) return null
        val sizes = runCatching { id.hananote.ink.PdfPages.pageSizes(f) }.getOrNull()
        if (sizes.isNullOrEmpty()) {
            f.delete()
            return null
        }
        return name to sizes
    }

    /** Nama berkas yang dipilih pengguna (tanpa akhiran), untuk judul catatan. */
    fun displayName(context: Context, uri: Uri): String? = runCatching {
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
            if (c.moveToFirst()) c.getString(0) else null
        }
    }.getOrNull()?.substringBeforeLast('.')?.trim()?.takeIf { it.isNotEmpty() }

    /** Catatan baru dari PDF: tiap halaman PDF menjadi satu halaman yang bisa ditulisi. */
    fun createNoteFromPdf(context: Context, bookId: String, uri: Uri): Note? {
        val id = UUID.randomUUID().toString()
        val imported = importPdf(context, id, uri)
        if (imported == null) {
            File(notesRoot, id).deleteRecursively()
            return null
        }
        val (file, sizes) = imported
        val pages = sizes.mapIndexed { i, (w, h) ->
            PageMeta(UUID.randomUUID().toString(), w, h, PaperTemplate.BLANK, 0xFFFFFFFF.toInt(), PageBackground.pdf(file, i))
        }
        return synchronized(lock) {
            val now = System.currentTimeMillis()
            val title = displayName(context, uri) ?: "Dokumen PDF"
            val note = Note(id, bookId, title, now, now, pages.size, order = topNoteOrder(bookId))
            writePages(id, pages)
            _notes.value = _notes.value + note
            saveNotes()
            touchBook(bookId, now)
            note
        }
    }

    /** Simpan pengaturan nomor halaman catatan [id] (kosong = tanpa nomor). */
    fun setPageNumbers(id: String, spec: String) = synchronized(lock) {
        _notes.value = _notes.value.map { if (it.id == id) it.copy(pageNumbers = spec) else it }
        saveNotes()
    }

    fun renameNote(id: String, title: String) = synchronized(lock) {
        _notes.value = _notes.value.map { if (it.id == id) it.copy(title = title.ifBlank { it.title }) else it }
        saveNotes()
        // Gambar: nama karyanya ikut diganti supaya sama dengan di ruang gambar.
        _notes.value.firstOrNull { it.id == id }?.takeIf { it.art.isNotEmpty() }?.let { n ->
            runCatching { ArtStorage.rename(appContext, n.art, n.title) }
        }
        widgetsChanged()
    }

    // ---------- gambar di dalam buku ----------

    /** Berkas catatan (dan karya gambarnya) dihapus dari penyimpanan. */
    private fun purge(n: Note) {
        File(notesRoot, n.id).deleteRecursively()
        if (n.art.isNotEmpty()) runCatching { ArtStorage.delete(appContext, n.art) }
    }

    /** "Gambar baru" diketuk di buku [bookId]: gambar yang dibuat setelah ini masuk ke buku itu. */
    fun markDrawingBook(bookId: String) {
        prefs.edit().putString("art_book", bookId).putLong("art_since", System.currentTimeMillis()).apply()
    }

    /**
     * Cocokkan daftar gambar di ruang gambar dengan entri di buku: gambar baru dimasukkan ke buku tempat
     * "Gambar baru" diketuk (gambar lama yang belum punya buku masuk ke buku "Gambar"), lalu judul dan waktu
     * ubahnya disamakan dengan karyanya.
     */
    fun syncArtNotes() = synchronized(lock) {
        val arts = runCatching { ArtStorage.list(appContext) }.getOrNull() ?: return@synchronized
        val byId = arts.associateBy { it.id }
        val linked = HashSet<String>()
        _notes.value.forEach { if (it.art.isNotEmpty()) linked.add(it.art) }
        _trashNotes.value.forEach { if (it.note.art.isNotEmpty()) linked.add(it.note.art) }
        var changed = false
        var notes = _notes.value.map { n ->
            val a = if (n.art.isEmpty()) null else byId[n.art]
            if (a == null) n else {
                val title = a.name.ifBlank { n.title }
                val upd = maxOf(n.updated, a.modified)
                if (title != n.title || upd != n.updated) { changed = true; n.copy(title = title, updated = upd) } else n
            }
        }
        val pendingBook = prefs.getString("art_book", null)?.takeIf { id -> _books.value.any { it.id == id } }
        val since = prefs.getLong("art_since", 0L)
        var fallback: String? = null
        for (a in arts.filter { it.id !in linked }.sortedBy { it.modified }) {
            val bookId = if (pendingBook != null && a.modified >= since - 5_000) pendingBook else {
                fallback ?: (_books.value.firstOrNull { it.title == "Gambar" } ?: createBook("Gambar", BookColors[4 % BookColors.size])).id.also { fallback = it }
            }
            val order = (notes.filter { it.bookId == bookId && !it.pinned }.minOfOrNull { it.order } ?: 0L) - 1
            notes = notes + Note(
                UUID.randomUUID().toString(), bookId, a.name.ifBlank { "Gambar" }, a.modified, a.modified, 0,
                order = order, art = a.id,
            )
            changed = true
        }
        if (pendingBook != null || prefs.contains("art_book")) prefs.edit().remove("art_book").remove("art_since").apply()
        if (changed) {
            _notes.value = notes
            saveNotes()
            _thumbsVersion.value = _thumbsVersion.value + 1
            widgetsChanged()
        }
    }

    fun moveNote(id: String, bookId: String) = synchronized(lock) {
        val order = topNoteOrder(bookId)
        _notes.value = _notes.value.map { if (it.id == id) it.copy(bookId = bookId, order = order) else it }
        saveNotes()
    }

    /** Urutan untuk catatan yang baru masuk buku: paling atas (di bawah yang disematkan). */
    private fun topNoteOrder(bookId: String): Long =
        (_notes.value.filter { it.bookId == bookId && !it.pinned }.minOfOrNull { it.order } ?: 0L) - 1

    /** Simpan urutan catatan di buku [bookId] sesuai [ids]; bukunya lalu diurutkan sendiri. */
    fun reorderNotes(bookId: String, ids: List<String>) = synchronized(lock) {
        val pos = ids.withIndex().associate { (i, id) -> id to i.toLong() }
        _notes.value = _notes.value.map { n -> pos[n.id]?.let { n.copy(order = it) } ?: n }
        _books.value = _books.value.map { if (it.id == bookId) it.copy(manualNotes = true) else it }
        saveNotes()
        saveBooks()
    }

    /** Kembali mengurutkan catatan di buku ini menurut terakhir diubah. */
    fun autoNoteOrder(bookId: String) = synchronized(lock) {
        _books.value = _books.value.map { if (it.id == bookId) it.copy(manualNotes = false) else it }
        saveBooks()
    }

    /** Sematkan catatan di atas bukunya, atau lepaskan. */
    fun setNotePinned(id: String, pinned: Boolean) = synchronized(lock) {
        val n = _notes.value.firstOrNull { it.id == id } ?: return@synchronized
        val first = (_notes.value.filter { it.bookId == n.bookId && it.pinned == pinned && it.id != id }.minOfOrNull { it.order } ?: 0L) - 1
        _notes.value = _notes.value.map { if (it.id == id) it.copy(pinned = pinned, order = first) else it }
        saveNotes()
    }


    fun touchNote(id: String, pageCount: Int? = null) = synchronized(lock) {
        val now = System.currentTimeMillis()
        var bookId: String? = null
        _notes.value = _notes.value.map {
            if (it.id == id) {
                bookId = it.bookId
                it.copy(updated = now, pageCount = pageCount ?: it.pageCount)
            } else it
        }
        saveNotes()
        bookId?.let { touchBook(it, now) }
    }

    private fun touchBook(bookId: String, now: Long) {
        _books.value = _books.value.map { if (it.id == bookId) it.copy(updated = now) else it }
        saveBooks()
    }

    // ---------- lembar ----------

    fun loadPages(noteId: String): List<PageMeta> {
        val f = File(File(notesRoot, noteId), "pages.json")
        return readArray(f).mapNotNull { o ->
            runCatching {
                PageMeta(
                    id = o.getString("id"),
                    width = o.getDouble("w").toFloat(),
                    height = o.getDouble("h").toFloat(),
                    template = runCatching { PaperTemplate.valueOf(o.optString("t", "BLANK")) }.getOrDefault(PaperTemplate.BLANK),
                    paperColor = o.optInt("c", PaperColors[0].second),
                    background = o.optString("bg", PageBackground.PLAIN),
                )
            }.getOrNull()
        }
    }

    fun savePages(noteId: String, pages: List<PageMeta>) {
        synchronized(lock) { writePages(noteId, pages) }
        touchNote(noteId, pages.size)
    }

    private fun writePages(noteId: String, pages: List<PageMeta>) {
        val a = JSONArray()
        pages.forEach { p ->
            a.put(
                JSONObject()
                    .put("id", p.id)
                    .put("w", p.width.toDouble())
                    .put("h", p.height.toDouble())
                    .put("t", p.template.name)
                    .put("c", p.paperColor)
                    .apply { if (p.background.isNotEmpty()) put("bg", p.background) }
            )
        }
        val dir = File(notesRoot, noteId).apply { mkdirs() }
        writeAtomic(File(dir, "pages.json"), a.toString())
    }

    fun loadContent(noteId: String, pageId: String): PageContent =
        PageIO.read(File(File(notesRoot, noteId), "$pageId.bin"))

    fun saveContent(noteId: String, pageId: String, content: PageContent) {
        val dir = File(notesRoot, noteId).apply { mkdirs() }
        PageIO.write(File(dir, "$pageId.bin"), content)
    }

    fun copyPageStrokes(noteId: String, fromPageId: String, toPageId: String) {
        val dir = File(notesRoot, noteId)
        val src = File(dir, "$fromPageId.bin")
        if (src.exists()) src.copyTo(File(dir, "$toPageId.bin"), overwrite = true)
        val idx = File(dir, "$fromPageId.idx")
        if (idx.exists()) idx.copyTo(File(dir, "$toPageId.idx"), overwrite = true)
    }

    fun deletePageFile(noteId: String, pageId: String) {
        File(File(notesRoot, noteId), "$pageId.bin").delete()
        File(File(notesRoot, noteId), "$pageId.idx").delete()
    }

    // ---------- gambar ----------

    fun noteDir(noteId: String) = File(notesRoot, noteId)

    /** Buka gambar dari galeri, diperkecil sampai sisi terpanjang sekitar [maxSide], dan diputar sesuai arah foto. */
    private fun decodeOriented(context: Context, uri: Uri, maxSide: Int): Bitmap? {
        val cr = context.contentResolver
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        val probe = cr.openInputStream(uri) ?: return null
        probe.use { BitmapFactory.decodeStream(it, null, bounds) }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        var sample = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= maxSide) sample *= 2
        val opts = BitmapFactory.Options().apply { inSampleSize = sample }
        var bmp = cr.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, opts) } ?: return null

        val orientation = runCatching {
            cr.openInputStream(uri)?.use {
                ExifInterface(it).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
            }
        }.getOrNull() ?: ExifInterface.ORIENTATION_NORMAL
        val degrees = when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> 90f
            ExifInterface.ORIENTATION_ROTATE_180 -> 180f
            ExifInterface.ORIENTATION_ROTATE_270 -> 270f
            else -> 0f
        }
        val longest = maxOf(bmp.width, bmp.height)
        val shrink = if (longest > maxSide) maxSide.toFloat() / longest else 1f
        if (degrees != 0f || shrink < 1f) {
            val m = Matrix().apply {
                if (shrink < 1f) postScale(shrink, shrink)
                if (degrees != 0f) postRotate(degrees)
            }
            val out = Bitmap.createBitmap(bmp, 0, 0, bmp.width, bmp.height, m, true)
            if (out !== bmp) bmp.recycle()
            bmp = out
        }
        return bmp
    }

    /** Simpan gambar: PNG bila tembus pandang (stiker), JPEG bila foto. Hasil: nama berkas. */
    private fun saveBitmap(bmp: Bitmap, dir: File, prefix: String): String {
        val png = bmp.hasAlpha()
        val name = prefix + UUID.randomUUID().toString() + if (png) ".png" else ".jpg"
        dir.mkdirs()
        FileOutputStream(File(dir, name)).use {
            bmp.compress(if (png) Bitmap.CompressFormat.PNG else Bitmap.CompressFormat.JPEG, if (png) 100 else 88, it)
        }
        return name
    }

    /**
     * Salin gambar dari galeri ke folder catatan, diperkecil sampai sisi terpanjang sekitar 2048 piksel
     * dan diputar sesuai arah foto. Hasil: nama berkas beserta lebar dan tinggi gambar.
     */
    fun importImage(context: Context, noteId: String, uri: Uri): Triple<String, Int, Int>? {
        val bmp = decodeOriented(context, uri, 2048) ?: return null
        val name = saveBitmap(bmp, noteDir(noteId), "img_")
        val result = Triple(name, bmp.width, bmp.height)
        bmp.recycle()
        return result
    }

    /** Buka gambar catatan dalam ukuran aslinya (untuk diolah, misalnya dihapus latarnya). */
    fun loadNoteImage(noteId: String, file: String): Bitmap? {
        val f = File(noteDir(noteId), file)
        if (!f.exists()) return null
        val opts = BitmapFactory.Options().apply { inPreferredConfig = Bitmap.Config.ARGB_8888; inMutable = false }
        return BitmapFactory.decodeFile(f.path, opts)
    }

    /** Simpan gambar hasil olahan ke folder catatan (PNG bila tembus pandang). Hasil: nama berkas. */
    fun saveNoteImage(noteId: String, bmp: Bitmap): String = saveBitmap(bmp, noteDir(noteId), "img_")

    // ---------- stiker gambar ----------

    private val stickersDir = File(root, "stickers").apply { mkdirs() }

    private val _stickers = MutableStateFlow(scanStickers())
    /** Gambar yang ditambahkan ke menu stiker, terbaru di depan. */
    val stickers: StateFlow<List<File>> = _stickers.asStateFlow()

    private fun scanStickers(): List<File> =
        (stickersDir.listFiles() ?: emptyArray())
            .filter { it.isFile && (it.name.endsWith(".png") || it.name.endsWith(".jpg")) }
            .sortedByDescending { it.lastModified() }

    /** Tambahkan gambar ke menu stiker (diperkecil, tembus pandangnya tetap). */
    fun addSticker(context: Context, uri: Uri): Boolean {
        val bmp = decodeOriented(context, uri, 768) ?: return false
        saveBitmap(bmp, stickersDir, "stk_")
        bmp.recycle()
        _stickers.value = scanStickers()
        return true
    }

    /** Jadikan gambar yang sudah ada di catatan sebagai stiker di menu. */
    fun addStickerFromFile(src: File): Boolean {
        if (!src.exists()) return false
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(src.path, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return false
        var sample = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= 768) sample *= 2
        val bmp = BitmapFactory.decodeFile(src.path, BitmapFactory.Options().apply { inSampleSize = sample }) ?: return false
        saveBitmap(bmp, stickersDir, "stk_")
        bmp.recycle()
        _stickers.value = scanStickers()
        return true
    }

    fun deleteSticker(f: File) {
        if (f.parentFile == stickersDir) f.delete()
        _stickers.value = scanStickers()
    }

    /** Salin stiker ke folder catatan supaya catatan tetap utuh walau stikernya dihapus dari menu. */
    fun placeSticker(noteId: String, sticker: File): Triple<String, Int, Int>? {
        if (!sticker.exists()) return null
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(sticker.path, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        val ext = sticker.name.substringAfterLast('.', "png")
        val name = "img_" + UUID.randomUUID().toString() + "." + ext
        val dir = noteDir(noteId).apply { mkdirs() }
        sticker.copyTo(File(dir, name), overwrite = true)
        return Triple(name, bounds.outWidth, bounds.outHeight)
    }

    // ---------- indeks dan pencarian ----------

    fun pageFile(noteId: String, pageId: String) = File(noteDir(noteId), "$pageId.bin")
    fun indexFile(noteId: String, pageId: String) = File(noteDir(noteId), "$pageId.idx")

    fun writeIndex(noteId: String, pageId: String, text: String) {
        val dir = noteDir(noteId)
        if (!dir.exists()) return
        writeAtomic(indexFile(noteId, pageId), text)
    }

    /** Teks halaman yang bisa dicari: dari indeks, dengan teks ketik terbaru bila indeksnya tertinggal. */
    private fun searchableText(noteId: String, pageId: String): String {
        val idx = indexFile(noteId, pageId)
        val bin = pageFile(noteId, pageId)
        val index = if (idx.exists()) runCatching { idx.readText() }.getOrNull() else null
        val stale = index == null || (bin.exists() && idx.lastModified() + 1500 < bin.lastModified())
        if (!stale) return PageText.searchable(index!!)
        val typed = if (bin.exists()) PageText.typed(PageIO.read(bin)) else ""
        val hand = index?.let { PageText.handwritingPart(it) }.orEmpty()
        return typed + "\n" + hand
    }

    /** Cuplikan isi halaman pertama (teks ketik dan tulisan tangan yang sudah terbaca), untuk widget. */
    fun snippet(noteId: String, maxChars: Int = 180): String {
        val first = loadPages(noteId).firstOrNull() ?: return ""
        val text = runCatching { searchableText(noteId, first.id) }.getOrDefault("")
        val out = StringBuilder()
        for (line in text.lineSequence()) {
            val t = line.trim()
            if (t.isEmpty()) continue
            if (out.isNotEmpty()) out.append('\n')
            out.append(t)
            if (out.length >= maxChars) break
        }
        return if (out.length > maxChars) out.substring(0, maxChars).trimEnd() + "…" else out.toString()
    }

    /** Kotak centang yang diubah dari luar penyunting (misalnya dari widget). */
    class CheckEdit(val noteId: String, val pageId: String, val sticky: Boolean, val index: Int, val itemId: Long, val at: Int, val text: String)

    /** Perubahan dari widget, supaya penyunting yang sedang terbuka ikut memperbarui isinya. */
    val externalEdits = MutableSharedFlow<CheckEdit>(extraBufferCapacity = 16)

    /** Permintaan membuka halaman tertentu dari catatan yang (mungkin) sedang terbuka. */
    val pageJump = MutableStateFlow<Pair<String, Int>?>(null)

    /**
     * Centang atau batalkan kotak centang langsung di berkas halaman (dari widget). Teks atau stikernya dicari
     * lewat isinya, jadi aman walau urutannya berubah. Hasil: berhasil diubah.
     */
    fun toggleCheckInFile(e: CheckEdit): Boolean {
        val content = loadContent(e.noteId, e.pageId)
        if (!applyCheck(content, e)) return false
        saveContent(e.noteId, e.pageId, content)
        touchNote(e.noteId)
        externalEdits.tryEmit(e)
        return true
    }

    /** Terapkan perubahan centang pada isi halaman (yang sudah dimuat). Hasil: ada yang diubah. */
    fun applyCheck(content: PageContent, e: CheckEdit): Boolean {
        if (e.sticky) {
            val idx = content.items.indexOfFirst { it is StickyItem && it.id == e.itemId && it.text == e.text }
                .takeIf { it >= 0 } ?: content.items.indexOfFirst { it is StickyItem && it.text == e.text }
            if (idx < 0) return false
            val st = content.items[idx] as StickyItem
            val next = TextLists.toggleCheck(st.text, e.at) ?: return false
            content.items[idx] = st.withText(next, st.font, st.textSize, st.align)
            return true
        }
        val idx = if (content.texts.getOrNull(e.index)?.text == e.text) e.index else content.texts.indexOfFirst { it.text == e.text }
        if (idx < 0) return false
        val t = content.texts[idx]
        val next = TextLists.toggleCheck(t.text, e.at) ?: return false
        content.texts[idx] = t.withText(next)
        return true
    }

    /** Cari di judul catatan, teks ketik, isi stiker, dan tulisan tangan yang sudah terbaca. */
    fun search(query: String, limit: Int = 120): List<SearchHit> {
        val q = query.trim()
        if (q.isEmpty()) return emptyList()
        val books = _books.value.associateBy { it.id }
        val out = ArrayList<SearchHit>()
        for (note in _notes.value.sortedByDescending { it.updated }) {
            val bookTitle = books[note.bookId]?.title.orEmpty()
            val inTitle = note.title.indexOf(q, ignoreCase = true)
            if (inTitle >= 0) out.add(SearchHit(note, bookTitle, -1, note.title, inTitle, q.length))
            for ((i, p) in loadPages(note.id).withIndex()) {
                val text = searchableText(note.id, p.id)
                val at = text.indexOf(q, ignoreCase = true)
                if (at < 0) continue
                val from = (at - 48).coerceAtLeast(0)
                val to = (at + q.length + 72).coerceAtMost(text.length)
                var snippet = text.substring(from, to).replace('\n', ' ')
                var start = at - from
                if (from > 0) { snippet = "…$snippet"; start += 1 }
                if (to < text.length) snippet = "$snippet…"
                out.add(SearchHit(note, bookTitle, i, snippet, start, q.length))
                if (out.size >= limit) return out
            }
            if (out.size >= limit) break
        }
        return out
    }

    /** Halaman yang indeksnya belum ada, tertinggal, atau tulisan tangannya belum terbaca. */
    fun pagesNeedingIndex(handwritingReady: Boolean): List<Pair<String, PageMeta>> {
        val out = ArrayList<Pair<String, PageMeta>>()
        for (note in _notes.value) {
            for (p in loadPages(note.id)) {
                val idx = indexFile(note.id, p.id)
                val bin = pageFile(note.id, p.id)
                if (!bin.exists()) continue
                val needs = !idx.exists() ||
                    idx.lastModified() + 1500 < bin.lastModified() ||
                    (handwritingReady && runCatching { idx.bufferedReader().use { it.readLine() } }.getOrNull() == PageText.PARTIAL)
                if (needs) out.add(note.id to p)
            }
        }
        return out
    }

    // ---------- sampul kecil ----------

    fun thumbFile(noteId: String): File {
        val n = _notes.value.firstOrNull { it.id == noteId } ?: _trashNotes.value.firstOrNull { it.note.id == noteId }?.note
        if (n != null && n.art.isNotEmpty()) return File(ArtStorage.dir(appContext, n.art), "thumb.png")
        return File(File(notesRoot, noteId), "thumb.png")
    }

    fun saveThumb(noteId: String, bmp: Bitmap) {
        val dir = File(notesRoot, noteId)
        if (!dir.exists()) return
        val tmp = File(dir, "thumb.png.tmp")
        FileOutputStream(tmp).use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
        val f = thumbFile(noteId)
        if (!tmp.renameTo(f)) { f.delete(); tmp.renameTo(f) }
        _thumbsVersion.value = _thumbsVersion.value + 1
        widgetsChanged()
    }

    // ---------- cadangan ----------

    /** Waktu ubah terbaru dari semua catatan (0 bila belum ada catatan). */
    fun lastChange(): Long = maxOf(_notes.value.maxOfOrNull { it.updated } ?: 0L, _books.value.maxOfOrNull { it.updated } ?: 0L)

    /**
     * Tulis cadangan lengkap (semua buku, catatan, gambar, rekaman, font, dan stiker) sebagai zip ke [out].
     * Panggil lewat [writeDispatcher] supaya tidak bertabrakan dengan penyimpanan halaman.
     * Hasil: jumlah catatan yang dicadangkan.
     */
    fun writeBackup(out: java.io.OutputStream): Int {
        val notesNow: List<Note>
        synchronized(lock) {
            saveBooks()
            saveNotes()
            notesNow = _notes.value
        }
        val zip = java.util.zip.ZipOutputStream(BufferedOutputStream(out, 64 * 1024))
        zip.setLevel(java.util.zip.Deflater.BEST_SPEED)
        fun put(name: String, f: File) {
            zip.putNextEntry(java.util.zip.ZipEntry(name))
            f.inputStream().use { it.copyTo(zip, 64 * 1024) }
            zip.closeEntry()
        }
        val meta = JSONObject().put("app", "WInote").put("v", 1)
            .put("created", System.currentTimeMillis()).put("notes", notesNow.size).put("books", _books.value.size)
        zip.putNextEntry(java.util.zip.ZipEntry(BACKUP_MARK))
        zip.write(meta.toString().toByteArray(Charsets.UTF_8))
        zip.closeEntry()
        if (booksFile.exists()) put("books.json", booksFile)
        if (notesFile.exists()) put("notes.json", notesFile)
        for (n in notesNow) {
            val dir = File(notesRoot, n.id)
            dir.listFiles { f -> f.isFile && SAFE_NAME.matches(f.name) && !f.name.endsWith(".tmp") && !f.name.endsWith(".idx") }
                ?.forEach { put("notes/${n.id}/${it.name}", it) }
            if (n.art.isNotEmpty() && SAFE_NAME.matches(n.art)) {
                ArtStorage.dir(appContext, n.art).listFiles { f -> f.isFile && SAFE_NAME.matches(f.name) && !f.name.endsWith(".tmp") }
                    ?.forEach { put("karya/${n.art}/${it.name}", it) }
            }
        }
        fontsDir.listFiles { f -> f.isFile }?.forEach { put("fonts/${it.name}", it) }
        stickersDir.listFiles { f -> f.isFile }?.forEach { put("stickers/${it.name}", it) }
        coversDir.listFiles { f -> f.isFile && SAFE_NAME.matches(f.name) }?.forEach { put("covers/${it.name}", it) }
        // Kuas ruang gambar (pengaturan, daftar, gambar ujung/tekstur impor) ikut dicadangkan.
        runCatching { id.goresan.engine.BrushSync.backupFiles(appContext).forEach { (name, f) -> put(name, f) } }
        zip.finish()
        zip.flush()
        return notesNow.size
    }

    class RestoreResult(val added: Int, val updated: Int, val kept: Int)

    /**
     * Pulihkan dari zip hasil [writeBackup]. Tidak ada yang dihapus: catatan yang belum ada ditambahkan,
     * catatan yang versi cadangannya lebih baru diperbarui, sisanya dibiarkan. Hasil: null bila berkas
     * bukan cadangan WInote (atau Hana Note).
     */
    fun restoreBackup(input: java.io.InputStream): RestoreResult? {
        val tmp = File(root, ".restore-" + UUID.randomUUID())
        try {
            tmp.mkdirs()
            var marked = false
            java.util.zip.ZipInputStream(BufferedInputStream(input, 64 * 1024)).use { zin ->
                while (true) {
                    val e = zin.nextEntry ?: break
                    if (e.isDirectory) continue
                    val name = e.name
                    if (name == BACKUP_MARK) { marked = true; continue }
                    if (!BACKUP_PATH.matches(name)) continue
                    val target = File(tmp, name)
                    target.parentFile?.mkdirs()
                    FileOutputStream(target).use { zin.copyTo(it, 64 * 1024) }
                }
            }
            if (!marked) return null
            val backupBooks = parseBooks(File(tmp, "books.json"))
            val backupNotes = parseNotes(File(tmp, "notes.json"))
            var added = 0
            var updated = 0
            var kept = 0
            synchronized(lock) {
                val notes = _notes.value.toMutableList()
                val books = _books.value.toMutableList()
                for (bn in backupNotes) {
                    val src = File(tmp, "notes/${bn.id}")
                    val artSrc = File(tmp, "karya/${bn.art}")
                    if (!SAFE_NAME.matches(bn.id)) continue
                    if (bn.art.isEmpty() && !File(src, "pages.json").exists()) continue
                    if (bn.art.isNotEmpty() && (!SAFE_NAME.matches(bn.art) || !File(artSrc, "doc.json").exists())) continue
                    val i = notes.indexOfFirst { it.id == bn.id }
                    val local = notes.getOrNull(i)
                    if (local != null && local.updated >= bn.updated) { kept++; continue }
                    if (bn.art.isNotEmpty()) {
                        val artTarget = ArtStorage.dir(appContext, bn.art)
                        artTarget.deleteRecursively()
                        if (!artSrc.renameTo(artTarget)) { artSrc.copyRecursively(artTarget, overwrite = true) }
                    } else {
                        val target = File(notesRoot, bn.id)
                        target.deleteRecursively()
                        if (!src.renameTo(target)) { src.copyRecursively(target, overwrite = true) }
                    }
                    if (local == null) { notes.add(bn); added++ } else { notes[i] = bn; updated++ }
                    if (books.none { it.id == bn.bookId }) {
                        val b = backupBooks.firstOrNull { it.id == bn.bookId }
                            ?: Book(bn.bookId, "Buku pulihan", BookColors[books.size % BookColors.size], bn.created, bn.updated)
                        books.add(b)
                    }
                }
                // Buku kosong dari cadangan juga dikembalikan.
                for (b in backupBooks) if (books.none { it.id == b.id }) books.add(b)
                _books.value = books
                _notes.value = notes
                // Yang kembali dari cadangan tidak lagi di Sampah.
                val activeNotes = notes.mapTo(HashSet()) { it.id }
                val activeBooks = books.mapTo(HashSet()) { it.id }
                _trashNotes.value = _trashNotes.value.filter { it.note.id !in activeNotes }
                    .map { if (it.withBook != null && it.withBook in activeBooks) it.copy(withBook = null) else it }
                _trashBooks.value = _trashBooks.value.filter { it.book.id !in activeBooks }
                saveBooks()
                saveNotes()
                saveTrash()
            }
            // Font dan stiker yang belum ada.
            File(tmp, "fonts").listFiles()?.forEach { f -> val t = File(fontsDir, f.name); if (!t.exists()) f.copyTo(t) }
            File(tmp, "stickers").listFiles()?.forEach { f -> val t = File(stickersDir, f.name); if (!t.exists()) f.copyTo(t) }
            File(tmp, "covers").listFiles()?.forEach { f -> val t = File(coversDir, f.name); if (!t.exists()) f.copyTo(t) }
            runCatching { id.goresan.engine.BrushSync.restore(appContext, File(tmp, "kuas")) }
            _fonts.value = scanFonts()
            _stickers.value = scanStickers()
            _thumbsVersion.value = _thumbsVersion.value + 1
            widgetsChanged()
            return RestoreResult(added, updated, kept)
        } catch (e: Exception) {
            return null
        } finally {
            tmp.deleteRecursively()
        }
    }

    // ---------- berkas ----------

    private fun bookFrom(o: JSONObject): Book? = runCatching {
        val created = o.optLong("created")
        Book(
            o.getString("id"), o.optString("title", "Buku"), o.optInt("color", BookColors[0]), created, o.optLong("updated"),
            order = if (o.has("order")) o.optLong("order") else created,
            pinned = o.optBoolean("pinned", false),
            manualNotes = o.optBoolean("manualNotes", false),
            cover = o.optString("cover", ""),
        )
    }.getOrNull()

    private fun noteFrom(o: JSONObject): Note? = runCatching {
        Note(
            o.getString("id"), o.getString("bookId"), o.optString("title", "Catatan"), o.optLong("created"), o.optLong("updated"), o.optInt("pages", 1),
            order = o.optLong("order", 0L),
            pinned = o.optBoolean("pinned", false),
            art = o.optString("art", ""),
            pageNumbers = o.optString("pageNumbers", ""),
        )
    }.getOrNull()

    private fun bookJson(b: Book): JSONObject =
        JSONObject().put("id", b.id).put("title", b.title).put("color", b.color).put("created", b.created).put("updated", b.updated)
            .put("order", b.order).put("pinned", b.pinned).put("manualNotes", b.manualNotes)
            .apply { if (b.cover.isNotBlank()) put("cover", b.cover) }

    private fun noteJson(n: Note): JSONObject =
        JSONObject().put("id", n.id).put("bookId", n.bookId).put("title", n.title)
            .put("created", n.created).put("updated", n.updated).put("pages", n.pageCount)
            .put("order", n.order).put("pinned", n.pinned)
            .apply { if (n.art.isNotEmpty()) put("art", n.art) }
            .apply { if (n.pageNumbers.isNotEmpty()) put("pageNumbers", n.pageNumbers) }

    private fun parseBooks(f: File): List<Book> = readArray(f).mapNotNull { bookFrom(it) }

    private fun parseNotes(f: File): List<Note> = readArray(f).mapNotNull { noteFrom(it) }

    private fun load() {
        _books.value = parseBooks(booksFile)
        _notes.value = parseNotes(notesFile)
        loadTrash()
    }

    private fun saveBooks() {
        val a = JSONArray()
        _books.value.forEach { a.put(bookJson(it)) }
        writeAtomic(booksFile, a.toString())
    }

    private fun saveNotes() {
        val a = JSONArray()
        _notes.value.forEach { a.put(noteJson(it)) }
        writeAtomic(notesFile, a.toString())
    }

    private fun readArray(f: File): List<JSONObject> {
        if (!f.exists()) return emptyList()
        return try {
            val a = JSONArray(f.readText())
            (0 until a.length()).mapNotNull { a.optJSONObject(it) }
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun writeAtomic(f: File, text: String) {
        val tmp = File(f.parentFile, f.name + ".tmp")
        tmp.writeText(text)
        if (!tmp.renameTo(f)) { f.delete(); tmp.renameTo(f) }
    }

    companion object {
        /** Nama berkas yang boleh ada di dalam folder catatan. */
        private val SAFE_NAME = Regex("[A-Za-z0-9][A-Za-z0-9._-]{0,120}")
        /** Jalur yang boleh ada di dalam zip cadangan (mencegah berkas tertulis di luar folder pulihan). */
        private val BACKUP_PATH = Regex("(books\\.json|notes\\.json|notes/[A-Za-z0-9-]{1,64}/[A-Za-z0-9][A-Za-z0-9._-]{0,120}|karya/[A-Za-z0-9-]{1,64}/[A-Za-z0-9][A-Za-z0-9._-]{0,120}|fonts/[A-Za-z0-9][A-Za-z0-9 _.-]{0,120}|stickers/[A-Za-z0-9][A-Za-z0-9._-]{0,120}|covers/[A-Za-z0-9][A-Za-z0-9._-]{0,120}|kuas/kuas\\.json|kuas/kuas-daftar\\.json|kuas/files/[A-Za-z0-9][A-Za-z0-9._-]{0,120})")
        private const val BACKUP_MARK = "hananote-backup.json"
        /** Lama isi Sampah disimpan sebelum dihapus permanen. */
        const val TRASH_DAYS = 30

        @Volatile private var instance: Repository? = null
        fun get(context: Context): Repository =
            instance ?: synchronized(this) {
                instance ?: Repository(context.applicationContext).also { instance = it }
            }
    }
}

/**
 * Format biner isi halaman: cepat ditulis dan dibaca, ukurannya kecil.
 * HNS1 hanya goresan (0.1.1), HNS2 menambahkan teks (0.1.3), HNS3 menambahkan benda tempelan.
 * Semua versi lama tetap bisa dibaca.
 */
object PageIO {
    /**
     * "HNS1".."HNS9". Versi 6: perataan teks, mind map, dan tiap benda diberi panjang datanya.
     * Versi 8: nomor dan kelompok. Versi 9: urutan lapisan (z) untuk benda, teks, dan coretan.
     * Versi 10: ukuran tiap kolom dan baris tabel.
     * Versi 11: bekas kuas penghapus lembut menempel pada goresan, teks, dan benda yang dihapusnya.
     */
    private const val MAGIC_BASE = 0x484E5330
    private const val VERSION = 14

    private fun writeMasks(out: DataOutputStream, masks: List<Stroke>?) {
        val ms = masks.orEmpty()
        out.writeInt(ms.size)
        for (m in ms) {
            out.writeInt(m.color)
            out.writeFloat(m.width)
            out.writeInt(m.size)
            for (i in 0 until m.size) { out.writeFloat(m.xs[i]); out.writeFloat(m.ys[i]); out.writeFloat(m.ps[i]) }
        }
    }

    private fun readMasks(inp: DataInputStream): List<Stroke>? {
        val n = inp.readInt()
        if (n < 0 || n > 100_000) throw java.io.IOException("bekas penghapus rusak")
        if (n == 0) return null
        return List(n) {
            val color = inp.readInt()
            val w = inp.readFloat()
            val m = inp.readInt()
            if (m < 0 || m > 10_000_000) throw java.io.IOException("bekas penghapus rusak")
            val xs = FloatArray(m); val ys = FloatArray(m); val ps = FloatArray(m)
            for (i in 0 until m) { xs[i] = inp.readFloat(); ys[i] = inp.readFloat(); ps[i] = inp.readFloat() }
            Stroke(Tool.ERASER, color, w, xs, ys, ps)
        }
    }

    private const val ITEM_IMAGE = 1
    private const val ITEM_STICKY = 2
    private const val ITEM_LINK = 3
    private const val ITEM_TABLE = 4
    private const val ITEM_CALENDAR = 5
    private const val ITEM_MINDMAP = 6
    private const val ITEM_AUDIO = 7
    private const val ITEM_TAPE = 8
    private const val ITEM_COVER = 9

    private fun DataOutputStream.writeStr(s: String) {
        val b = s.toByteArray(Charsets.UTF_8)
        writeInt(b.size)
        write(b)
    }

    private fun DataInputStream.readStr(): String {
        val n = readInt()
        if (n < 0 || n > 8_000_000) throw java.io.IOException("teks rusak")
        val b = ByteArray(n)
        readFully(b)
        return String(b, Charsets.UTF_8)
    }

    fun write(file: File, content: PageContent) {
        val tmp = File(file.parentFile, file.name + ".tmp")
        DataOutputStream(BufferedOutputStream(FileOutputStream(tmp), 64 * 1024)).use { out ->
            out.writeInt(MAGIC_BASE + VERSION)
            out.writeInt(content.strokes.size)
            for (s in content.strokes) {
                out.writeByte(s.tool.ordinal)
                out.writeInt(s.color)
                out.writeFloat(s.width)
                out.writeInt(s.size)
                for (i in 0 until s.size) {
                    out.writeFloat(s.xs[i]); out.writeFloat(s.ys[i]); out.writeFloat(s.ps[i])
                }
                out.writeLong(s.group)
                out.writeLong(s.z)
                writeMasks(out, s.masks)
            }
            out.writeInt(content.texts.size)
            for (t in content.texts) {
                out.writeFloat(t.x); out.writeFloat(t.y); out.writeFloat(t.width); out.writeFloat(t.size)
                out.writeInt(t.color)
                out.writeStr(t.text)
                out.writeStr(t.font)
                out.writeByte(t.align.ordinal)
                out.writeLong(t.group)
                out.writeLong(t.z)
                writeMasks(out, t.masks)
                val runs = t.runs.toIntArray()
                out.writeInt(runs.size)
                for (v in runs) out.writeInt(v)
                out.writeFloat(t.linePitch)
            }
            out.writeInt(content.items.size)
            val buf = java.io.ByteArrayOutputStream()
            for (it in content.items) {
                val type = when (it) {
                    is ImageItem -> ITEM_IMAGE
                    is StickyItem -> ITEM_STICKY
                    is LinkItem -> ITEM_LINK
                    is TableItem -> ITEM_TABLE
                    is CalendarItem -> ITEM_CALENDAR
                    is MindMapItem -> ITEM_MINDMAP
                    is AudioItem -> ITEM_AUDIO
                    is TapeItem -> ITEM_TAPE
                    is CoverItem -> ITEM_COVER
                }
                // Isi tiap benda diawali panjangnya, jadi versi lama bisa melewati jenis benda yang belum dikenalnya.
                buf.reset()
                DataOutputStream(buf).use { d -> writeItem(d, it) }
                out.writeByte(type)
                out.writeInt(buf.size())
                buf.writeTo(out)
            }
        }
        if (!tmp.renameTo(file)) { file.delete(); tmp.renameTo(file) }
    }

    private fun writeItem(out: DataOutputStream, it: PageItem) {
        writeItemBody(out, it)
        out.writeLong(it.id)
        out.writeLong(it.group)
        out.writeLong(it.z)
        writeMasks(out, it.masks)
    }

    private fun writeItemBody(out: DataOutputStream, it: PageItem) {
        out.writeFloat(it.x); out.writeFloat(it.y); out.writeFloat(it.w); out.writeFloat(it.h)
        out.writeFloat(it.rotation)
        when (it) {
            is ImageItem -> {
                out.writeStr(it.file)
                out.writeByte((if (it.flipH) 1 else 0) or (if (it.flipV) 2 else 0))
                out.writeFloat(it.crop.l); out.writeFloat(it.crop.t); out.writeFloat(it.crop.r); out.writeFloat(it.crop.b)
                val sh = it.shadow
                out.writeBoolean(sh != null)
                if (sh != null) { out.writeFloat(sh.distance); out.writeFloat(sh.blur); out.writeFloat(sh.opacity) }
            }
            is AudioItem -> { out.writeStr(it.file); out.writeLong(it.durationMs) }
            is CoverItem -> out.writeInt(it.color)
            is TapeItem -> {
                out.writeByte(it.shape.ordinal)
                out.writeInt(it.color)
                out.writeFloat(it.thick)
                out.writeBoolean(it.covered)
                val pts = it.path
                out.writeInt(pts?.size ?: 0)
                if (pts != null) for (v in pts) out.writeFloat(v)
            }
            is StickyItem -> {
                out.writeInt(it.color); out.writeStr(it.text); out.writeStr(it.font); out.writeFloat(it.textSize)
                out.writeByte(it.align.ordinal)
            }
            is LinkItem -> { out.writeStr(it.url); out.writeStr(it.label) }
            is TableItem -> {
                out.writeInt(it.rows); out.writeInt(it.cols); out.writeInt(it.color)
                out.writeBoolean(it.header)
                for (f in it.colFrac) out.writeFloat(f)
                for (f in it.rowFrac) out.writeFloat(f)
                // Versi 14: teks di dalam sel.
                out.writeFloat(it.textSize)
                out.writeStr(it.font)
                out.writeByte(it.align.ordinal)
                out.writeInt(it.cells.size)
                for (s in it.cells) out.writeStr(s)
            }
            is CalendarItem -> { out.writeInt(it.year); out.writeInt(it.month); out.writeInt(it.color); out.writeInt(it.mark) }
            is MindMapItem -> {
                out.writeFloat(it.mapL); out.writeFloat(it.mapT); out.writeFloat(it.mapW); out.writeFloat(it.mapH)
                out.writeBoolean(it.tidy)
                out.writeInt(it.nodes.size)
                for (n in it.nodes) {
                    out.writeInt(n.id); out.writeInt(n.parent)
                    out.writeFloat(n.x); out.writeFloat(n.y)
                    out.writeStr(n.text)
                    out.writeInt(n.color)
                }
            }
        }
    }

    private fun align(i: Int) = Align.entries.getOrElse(i) { Align.LEFT }

    /**
     * Catatan sebelum versi 9 belum menyimpan urutan lapisan. Urutannya dibuat sama persis dengan
     * tampilan lamanya: benda tempelan (versi 8: tiap benda diikuti tulisan yang dulu menempel padanya),
     * lalu stabilo dan cat air, teks ketik, dan tulisan tangan paling atas.
     */
    private fun legacyLayers(strokes: List<Stroke>, texts: List<TextItem>, items: List<PageItem>, owners: LongArray?) {
        var z = 0L
        val placed = BooleanArray(strokes.size)
        for (it in items) {
            it.z = ++z
            if (owners != null && it.carriesInk) {
                for (under in listOf(true, false)) {
                    for (i in strokes.indices) {
                        if (!placed[i] && owners[i] == it.id && strokes[i].tool.underlay == under) { strokes[i].z = ++z; placed[i] = true }
                    }
                }
            }
        }
        // Tulisan milik benda yang sudah tidak ada dianggap bebas.
        for (i in strokes.indices) if (!placed[i] && strokes[i].tool.underlay) { strokes[i].z = ++z; placed[i] = true }
        for (t in texts) t.z = ++z
        for (i in strokes.indices) if (!placed[i]) { strokes[i].z = ++z; placed[i] = true }
    }

    private fun finish(strokes: ArrayList<Stroke>, texts: ArrayList<TextItem>, items: ArrayList<PageItem>, version: Int, owners: LongArray?): PageContent {
        if (version < 9) legacyLayers(strokes, texts, items, owners)
        strokes.sortBy { it.z }
        texts.sortBy { it.z }
        items.sortBy { it.z }
        // Bekas kuas penghapus lepas dari versi lama: tempelkan ke objek yang dihapusnya.
        if (strokes.any { it.tool == Tool.ERASER }) runCatching { SoftMasks.migrate(strokes, texts, items) }
        return PageContent(strokes, texts, items)
    }

    fun read(file: File): PageContent {
        if (!file.exists()) return PageContent()
        return try {
            DataInputStream(BufferedInputStream(FileInputStream(file), 64 * 1024)).use { inp ->
                val version = inp.readInt() - MAGIC_BASE
                if (version < 1 || version > VERSION) return PageContent()
                val n = inp.readInt()
                val strokes = ArrayList<Stroke>(n.coerceAtLeast(0))
                // Versi 8 mencatat benda tempat tulisan menempel; hanya dipakai untuk menyusun lapisan lama.
                val owners = if (version == 8) LongArray(n.coerceAtLeast(0)) else null
                repeat(n) { k ->
                    val tool = Tool.entries.getOrElse(inp.readByte().toInt()) { Tool.PEN }
                    val color = inp.readInt()
                    val w = inp.readFloat()
                    val m = inp.readInt()
                    val xs = FloatArray(m); val ys = FloatArray(m); val ps = FloatArray(m)
                    for (i in 0 until m) { xs[i] = inp.readFloat(); ys[i] = inp.readFloat(); ps[i] = inp.readFloat() }
                    val st = Stroke(tool, color, w, xs, ys, ps)
                    if (version >= 9) {
                        st.group = inp.readLong()
                        st.z = inp.readLong()
                        if (version >= 11) st.masks = readMasks(inp)
                    } else if (version == 8) {
                        owners!![k] = inp.readLong()
                        st.group = inp.readLong()
                    }
                    strokes.add(st)
                }
                val texts = ArrayList<TextItem>()
                if (version >= 2) {
                    val tn = inp.readInt()
                    repeat(tn) {
                        val x = inp.readFloat(); val y = inp.readFloat(); val w = inp.readFloat(); val size = inp.readFloat()
                        val color = inp.readInt()
                        val text = inp.readStr()
                        val font = if (version >= 4) inp.readStr() else FontRegistry.DEFAULT
                        val al = if (version >= 6) align(inp.readByte().toInt()) else Align.LEFT
                        val group = if (version >= 8) inp.readLong() else 0L
                        val z = if (version >= 9) inp.readLong() else 0L
                        val masks = if (version >= 11) readMasks(inp) else null
                        // Versi 12: gaya per huruf (tebal, miring, garis bawah, coret).
                        val runs = if (version >= 12) {
                            val rn = inp.readInt()
                            if (rn < 0 || rn > 3_000_000) throw java.io.IOException("gaya teks rusak")
                            TextRuns.of(IntArray(rn) { inp.readInt() }).clamp(text.length)
                        } else TextRuns.EMPTY
                        // Versi 13: jarak baris tetap (garis kertas).
                        val pitch = if (version >= 13) inp.readFloat().takeIf { it.isFinite() && it in 0f..2000f } ?: 0f else 0f
                        val t = TextItem(x, y, w, text, size, color, font, al, runs, pitch)
                        t.group = group
                        t.z = z
                        t.masks = masks
                        texts.add(t)
                    }
                }
                val items = ArrayList<PageItem>()
                if (version >= 3) {
                    val count = inp.readInt()
                    repeat(count) {
                        val type = inp.readByte().toInt()
                        if (version >= 6) {
                            val len = inp.readInt()
                            if (len < 0 || len > 64_000_000) throw java.io.IOException("benda rusak")
                            val bytes = ByteArray(len)
                            inp.readFully(bytes)
                            val item = runCatching {
                                DataInputStream(java.io.ByteArrayInputStream(bytes)).use { d ->
                                    readItem(d, type, version)?.also { item ->
                                        if (version >= 8) {
                                            item.id = d.readLong()
                                            item.group = d.readLong()
                                        }
                                        if (version >= 9) item.z = d.readLong()
                                        if (version >= 11) item.masks = readMasks(d)
                                    }
                                }
                            }.getOrNull()
                            if (item != null) items.add(item)
                        } else {
                            // Versi lama tanpa panjang: jenis yang tidak dikenal tidak bisa dilewati.
                            val item = readItem(inp, type, version) ?: return@use finish(strokes, texts, items, version, owners)
                            items.add(item)
                        }
                    }
                }
                finish(strokes, texts, items, version, owners)
            }
        } catch (e: Exception) {
            PageContent()
        }
    }

    private fun readItem(inp: DataInputStream, type: Int, version: Int): PageItem? {
        val x = inp.readFloat(); val y = inp.readFloat(); val w = inp.readFloat(); val h = inp.readFloat()
        val rot = if (version >= 5) inp.readFloat() else 0f
        return when (type) {
            ITEM_IMAGE -> {
                val file = inp.readStr()
                if (version >= 5) {
                    val flags = inp.readByte().toInt()
                    val crop = Crop(inp.readFloat(), inp.readFloat(), inp.readFloat(), inp.readFloat())
                    val shadow = if (version >= 7 && inp.readBoolean()) Shadow(inp.readFloat(), inp.readFloat(), inp.readFloat()) else null
                    ImageItem(x, y, w, h, file, rot, flags and 1 != 0, flags and 2 != 0, crop, shadow)
                } else {
                    ImageItem(x, y, w, h, file)
                }
            }
            ITEM_STICKY -> {
                val color = inp.readInt()
                if (version >= 4) {
                    val text = inp.readStr()
                    val font = inp.readStr()
                    val size = inp.readFloat()
                    val al = if (version >= 6) align(inp.readByte().toInt()) else Align.LEFT
                    StickyItem(x, y, w, h, color, text, font, size, rot, al)
                } else {
                    StickyItem(x, y, w, h, color)
                }
            }
            ITEM_LINK -> { val url = inp.readStr(); LinkItem(x, y, w, h, url, inp.readStr()) }
            ITEM_TABLE -> {
                val r = inp.readInt(); val c = inp.readInt(); val color = inp.readInt()
                if (version >= 10 && r in 1..200 && c in 1..50) {
                    val header = inp.readBoolean()
                    val cf = FloatArray(c) { inp.readFloat() }
                    val rf = FloatArray(r) { inp.readFloat() }
                    if (version >= 14) {
                        val size = inp.readFloat().takeIf { it.isFinite() && it in 1f..400f } ?: TableItem.CELL_TEXT_SIZE
                        val font = inp.readStr()
                        val al = align(inp.readByte().toInt())
                        val n = inp.readInt()
                        if (n < 0 || n > 20_000) throw java.io.IOException("isi tabel rusak")
                        val cells = List(n) { inp.readStr() }
                        TableItem(x, y, w, h, r, c, color, cf, rf, header, cells, size, font, al)
                    } else {
                        TableItem(x, y, w, h, r, c, color, cf, rf, header)
                    }
                } else {
                    TableItem(x, y, w, h, r, c, color)
                }
            }
            ITEM_CALENDAR -> {
                val yr = inp.readInt(); val mo = inp.readInt(); val col = inp.readInt()
                CalendarItem(x, y, w, h, yr, mo, col, inp.readInt())
            }
            ITEM_AUDIO -> { val f = inp.readStr(); AudioItem(x, y, w, h, f, inp.readLong()) }
            ITEM_COVER -> CoverItem(x, y, w, h, inp.readInt())
            ITEM_TAPE -> {
                val shape = TapeShape.entries.getOrElse(inp.readByte().toInt()) { TapeShape.RECT }
                val color = inp.readInt()
                val thick = inp.readFloat().takeIf { it.isFinite() && it in 0f..2000f } ?: 0f
                val covered = inp.readBoolean()
                val n = inp.readInt()
                if (n < 0 || n > 400_000) throw java.io.IOException("selotip rusak")
                val pts = if (n > 0) FloatArray(n) { inp.readFloat() } else null
                TapeItem(x, y, w, h, shape, color, thick, pts, rot, covered)
            }
            ITEM_MINDMAP -> {
                val ml = inp.readFloat(); val mt = inp.readFloat(); val mw = inp.readFloat(); val mh = inp.readFloat()
                val tidy = inp.readBoolean()
                val count = inp.readInt()
                val nodes = ArrayList<MindNode>(count.coerceIn(0, 10_000))
                repeat(count) {
                    val id = inp.readInt(); val parent = inp.readInt()
                    val nx = inp.readFloat(); val ny = inp.readFloat()
                    val text = inp.readStr()
                    nodes.add(MindNode(id, parent, nx, ny, text, inp.readInt()))
                }
                MindMapItem(x, y, w, h, nodes, ml, mt, mw, mh, tidy)
            }
            else -> null
        }
    }
}
