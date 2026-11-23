@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)

package id.hananote.ui

import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.material.icons.filled.Brush
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.unit.Dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import id.goresan.DrawActivity
import id.goresan.engine.Storage
import android.content.SharedPreferences
import android.graphics.BitmapFactory
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.material3.FloatingActionButton
import androidx.activity.result.PickVisualMediaRequest
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Update
import id.hananote.data.notesOrder
import id.hananote.data.shelfOrder
import id.hananote.data.topBooks
import id.hananote.data.childBooks
import id.hananote.data.bookPath
import id.hananote.data.bookTree
import id.hananote.data.descendantBooks
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DocumentScanner
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.automirrored.filled.DriveFileMove
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Backup
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.Description
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.launch
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.material.icons.filled.Palette
import androidx.compose.ui.text.font.FontFamily
import id.hananote.data.CrashLog
import id.hananote.data.Accent
import id.hananote.data.ThemeMode
import id.hananote.data.Book
import id.hananote.data.BookColors
import id.hananote.data.Note
import id.hananote.data.PaperColors
import id.hananote.data.PaperSize
import id.hananote.data.PaperTemplate
import id.hananote.data.Repository
import id.hananote.data.SearchHit
import id.hananote.ink.PageIndexer
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import kotlinx.coroutines.delay
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Locale

private sealed interface LibDialog {
    data object Theme : LibDialog
    data object Backup : LibDialog
    data class Crash(val fresh: Boolean) : LibDialog
    /** Buku baru; [parent] tidak kosong = sub-buku di dalam buku itu. */
    data class NewBook(val parent: String = "") : LibDialog
    data class MoveBook(val book: Book) : LibDialog
    data class EditBook(val book: Book) : LibDialog
    data class DeleteBook(val book: Book, val count: Int) : LibDialog
    data class NewNote(val bookId: String) : LibDialog
    data class RenameNote(val note: Note) : LibDialog
    data class MoveNote(val note: Note) : LibDialog
    data class DeleteNote(val note: Note) : LibDialog
    data object Trash : LibDialog
}

/** Aksi yang dipakai kartu buku dan kartu catatan. */
private class LibActions(
    val selectBook: (String?) -> Unit,
    val openNote: (Note) -> Unit,
    val search: () -> Unit,
    val show: (LibDialog?) -> Unit,
    val importPdf: (bookId: String) -> Unit,
    val scanDoc: (bookId: String) -> Unit,
    val pinBook: (Book, Boolean) -> Unit,
    val pinNote: (Note, Boolean) -> Unit,
    val reorderBooks: (List<String>) -> Unit,
    val reorderNotes: (bookId: String, ids: List<String>) -> Unit,
    val autoNoteOrder: (bookId: String) -> Unit,
)

/**
 * Hubungkan seretan dengan daftar yang sedang tampil: yang disematkan hanya bisa berpindah di antara
 * yang disematkan, begitu juga yang tidak. Hasil urutan baru (id) diteruskan ke [commit].
 */
private fun <T> bindReorder(r: ReorderState, list: List<T>, pinned: (T) -> Boolean, id: (T) -> String, commit: (List<String>) -> Unit) {
    val pinnedCount = list.count(pinned)
    r.clamp = { from, to ->
        val item = list.getOrNull(from)
        when {
            item == null || list.isEmpty() -> from
            pinned(item) -> to.coerceIn(0, maxOf(0, pinnedCount - 1))
            else -> to.coerceIn(minOf(pinnedCount, list.lastIndex), list.lastIndex)
        }
    }
    r.onMove = { from, to ->
        if (from in list.indices && to in list.indices) {
            val l = list.toMutableList()
            l.add(to, l.removeAt(from))
            commit(l.map(id))
        }
    }
}

/** Tanda kecil untuk buku atau catatan yang disematkan. */
@Composable
private fun PinBadge(modifier: Modifier) {
    Box(
        modifier
            .size(26.dp)
            .shadow(2.dp, CircleShape)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.primary),
        contentAlignment = Alignment.Center,
    ) {
        Icon(Icons.Filled.PushPin, contentDescription = "Disematkan", tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(15.dp))
    }
}

@Composable
fun LibraryScreen(
    repo: Repository,
    isTablet: Boolean,
    selectedBookId: String?,
    onSelectBook: (String?) -> Unit,
    onOpenNote: (Note) -> Unit,
) {
    val books by repo.books.collectAsState()
    val notes by repo.notes.collectAsState()
    val thumbs by repo.thumbsVersion.collectAsState()
    var dialog by remember { mutableStateOf<LibDialog?>(null) }
    var searching by rememberSaveable { mutableStateOf(false) }

    val shelf = remember(books) { shelfOrder(topBooks(books)) }
    val counts = remember(notes) { notes.groupingBy { it.bookId }.eachCount() }
    // Tiap kali layar ini tampil lagi (misalnya selesai menggambar), gambar baru dimasukkan ke bukunya.
    val owner = LocalLifecycleOwner.current
    DisposableEffect(owner) {
        val obs = LifecycleEventObserver { _, ev -> if (ev == Lifecycle.Event.ON_RESUME) repo.appScope.launch { repo.syncArtNotes() } }
        owner.lifecycle.addObserver(obs)
        onDispose { owner.lifecycle.removeObserver(obs) }
    }
    // Impor PDF: pemilih berkas harus tetap ada sampai hasilnya kembali, jadi dipasang di layar rak.
    val context = LocalContext.current
    var pdfBook by remember { mutableStateOf<String?>(null) }
    var pdfBusy by remember { mutableStateOf(false) }
    var pdfError by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val pickPdf = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        val book = pdfBook
        if (uri != null && book != null) {
            pdfBusy = true
            scope.launch {
                val note = withContext(Dispatchers.IO) { runCatching { repo.createNoteFromPdf(context.applicationContext, book, uri) }.getOrNull() }
                pdfBusy = false
                if (note != null) onOpenNote(note) else pdfError = true
            }
        }
    }
    // Pindai dokumen: hasil PDF-nya langsung jadi catatan baru di buku yang dipilih.
    var scanError by remember { mutableStateOf<String?>(null) }
    val scanner = rememberDocScanner(
        onPdf = { uri, _ ->
            val book = pdfBook
            if (book != null) {
                pdfBusy = true
                scope.launch {
                    val note = withContext(Dispatchers.IO) { runCatching { repo.createNoteFromPdf(context.applicationContext, book, uri, "Pindaian") }.getOrNull() }
                    pdfBusy = false
                    if (note != null) onOpenNote(note) else pdfError = true
                }
            }
        },
        onError = { scanError = it },
    )
    scanError?.let { msg ->
        AlertDialog(
            onDismissRequest = { scanError = null },
            title = { Text("Pemindai belum bisa dipakai") },
            text = { Text(msg) },
            confirmButton = { TextButton(onClick = { scanError = null }) { Text("OK") } },
        )
    }
    val actions = remember {
        LibActions(
            selectBook = onSelectBook,
            openNote = onOpenNote,
            search = { searching = true },
            show = { dialog = it },
            importPdf = { book ->
                pdfBook = book
                runCatching { pickPdf.launch(arrayOf("application/pdf")) }
            },
            scanDoc = { book ->
                pdfBook = book
                scanner()
            },
            pinBook = { b, on -> repo.setBookPinned(b.id, on) },
            pinNote = { n, on -> repo.setNotePinned(n.id, on) },
            reorderBooks = { ids -> repo.reorderBooks(ids) },
            reorderNotes = { bookId, ids -> repo.reorderNotes(bookId, ids) },
            autoNoteOrder = { bookId -> repo.autoNoteOrder(bookId) },
        )
    }
    if (pdfBusy) {
        AlertDialog(
            onDismissRequest = {},
            title = { Text("Membuka PDF") },
            text = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 3.dp)
                    Spacer(Modifier.width(14.dp))
                    Text("Menyiapkan halaman…")
                }
            },
            confirmButton = {},
        )
    }
    if (pdfError) {
        AlertDialog(
            onDismissRequest = { pdfError = false },
            title = { Text("PDF tidak bisa dibuka") },
            text = { Text("Berkasnya mungkin rusak atau dikunci kata sandi.") },
            confirmButton = { TextButton(onClick = { pdfError = false }) { Text("OK") } },
        )
    }

    if (searching) {
        SearchScreen(
            repo = repo,
            onClose = { searching = false },
            onOpen = { hit ->
                // Buka catatan tepat di halaman yang cocok.
                if (hit.pageIndex >= 0) repo.prefs.edit().putInt("page_${hit.note.id}", hit.pageIndex).apply()
                searching = false
                onOpenNote(hit.note)
            },
        )
        return
    }

    // Rak berupa kisi sampul buku; buka buku untuk melihat catatannya (tablet dan HP sama).
    val current = books.firstOrNull { it.id == selectedBookId }
    // Kembali dari sub-buku ke buku induknya, dari buku paling atas ke rak.
    val up = current?.parent?.takeIf { p -> p.isNotEmpty() && books.any { it.id == p } }
    BackHandler(enabled = current != null) { onSelectBook(up) }
    if (current == null) ShelfScreen(repo, shelf, counts, actions, books)
    else BookScreen(repo, current, notes, counts[current.id] ?: 0, thumbs, actions, books, counts, onUp = { onSelectBook(up) })

    // Aplikasi sempat tertutup sendiri: tawarkan laporan galatnya sekali.
    LaunchedEffect(Unit) {
        if (dialog == null && CrashLog.unseen(context).isNotEmpty()) dialog = LibDialog.Crash(fresh = true)
    }
    LibraryDialogs(repo, dialog, shelf, onDismiss = { dialog = null }, actions = actions)
}

// ================= rak: kisi sampul buku =================

/** Banyak kolom kisi buku dan catatan menurut lebar layar. */
@Composable
private fun gridColumns(): Int {
    val w = LocalConfiguration.current.screenWidthDp
    return when {
        w < 560 -> 2
        w < 900 -> 3
        w < 1200 -> 4
        else -> 5
    }
}

@Composable
private fun wideScreen(): Boolean = LocalConfiguration.current.screenWidthDp >= 600

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ShelfScreen(repo: Repository, shelf: List<Book>, counts: Map<String, Int>, actions: LibActions, all: List<Book>) {
    val gridState = rememberLazyGridState()
    val reorder = remember { ReorderState() }
    bindReorder(reorder, shelf, { it.pinned }, { it.id }, actions.reorderBooks)
    val edge = with(LocalDensity.current) { 72.dp.toPx() }
    LaunchedEffect(reorder.dragKey) { reorder.autoScroll(gridState, edge) }
    val wide = wideScreen()
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("WInote", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold) },
                actions = {
                    IconButton(onClick = actions.search) { Icon(Icons.Filled.Search, contentDescription = "Cari catatan") }
                    IconButton(onClick = { actions.show(LibDialog.Backup) }) { Icon(Icons.Filled.Backup, contentDescription = "Cadangan") }
                    TrashButton(repo) { actions.show(LibDialog.Trash) }
                    IconButton(onClick = { actions.show(LibDialog.Theme) }) { Icon(Icons.Filled.Palette, contentDescription = "Tampilan aplikasi") }
                    IconButton(onClick = { actions.show(LibDialog.NewBook()) }) { Icon(Icons.Filled.Add, contentDescription = "Buku baru") }
                    Spacer(Modifier.width(4.dp))
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { actions.show(LibDialog.NewBook()) }, shape = CircleShape) {
                Icon(Icons.Filled.Add, contentDescription = "Buku baru")
            }
        },
        containerColor = MaterialTheme.colorScheme.background,
    ) { pad ->
        Column(Modifier.padding(top = pad.calculateTopPadding()).fillMaxSize()) {
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))
            val side = if (wide) 40.dp else 20.dp
            LazyVerticalGrid(
                columns = GridCells.Fixed(gridColumns()),
                state = gridState,
                modifier = Modifier.fillMaxSize().onGloballyPositioned { reorder.listBounds = rectInRoot(it) },
                contentPadding = PaddingValues(start = side, end = side, top = if (wide) 36.dp else 20.dp, bottom = pad.calculateBottomPadding() + 110.dp),
                horizontalArrangement = Arrangement.spacedBy(if (wide) 44.dp else 20.dp),
                verticalArrangement = Arrangement.spacedBy(if (wide) 34.dp else 24.dp),
            ) {
                itemsIndexed(shelf, key = { _, b -> b.id }) { i, b ->
                    BookTile(
                        repo = repo, book = b, count = counts[b.id] ?: 0,
                        modifier = if (reorder.dragKey == b.id) Modifier else Modifier.animateItem(fadeInSpec = null, fadeOutSpec = null),
                        actions = actions, reorder = reorder, index = i,
                        subCount = childBooks(all, b.id).size,
                    )
                }
            }
        }
    }
}

// ================= gambar (ruang gambar Goresan) =================

/** Buka ruang gambar: karya [open], kanvas baru, atau galerinya ([gallery]). */
fun startDrawing(ctx: Context, open: String? = null, gallery: Boolean = false) {
    if (!DrawActivity.supported()) {
        Toast.makeText(ctx, "Fitur gambar butuh Android 10 ke atas.", Toast.LENGTH_LONG).show()
        return
    }
    // Satu ruang gambar saja: yang lama ditutup (sudah tersimpan saat dijeda) lalu dibuka sesuai permintaan.
    val i = Intent(ctx, DrawActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
    when {
        open != null -> i.putExtra(DrawActivity.EXTRA_OPEN, open)
        !gallery -> i.putExtra(DrawActivity.EXTRA_NEW, true)
    }
    ctx.startActivity(i)
}

/**
 * Tombol + di dalam buku: [firstLabel] (catatan baru) dan Gambar baru, yang membuka ruang gambar dengan kuas,
 * layer, Alpha Lock, dan ColorDrop. Gambarnya disimpan di buku [bookId].
 */
@Composable
private fun AddMenu(
    firstLabel: String,
    onFirst: () -> Unit,
    repo: Repository,
    bookId: String,
    onSubBook: (() -> Unit)? = null,
    anchor: @Composable (open: () -> Unit) -> Unit,
) {
    val ctx = LocalContext.current
    var open by remember { mutableStateOf(false) }
    Box {
        anchor { open = true }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            DropdownMenuItem(
                text = { Text(firstLabel) },
                leadingIcon = { Icon(if (firstLabel.startsWith("Buku")) Icons.AutoMirrored.Filled.MenuBook else Icons.Filled.Description, contentDescription = null) },
                onClick = { open = false; onFirst() },
            )
            if (onSubBook != null) {
                DropdownMenuItem(
                    text = { Text("Sub-buku baru") },
                    leadingIcon = { Icon(Icons.Filled.CreateNewFolder, contentDescription = null) },
                    onClick = { open = false; onSubBook() },
                )
            }
            DropdownMenuItem(
                text = {
                    Column {
                        Text("Gambar baru")
                        Text(
                            "Kuas, layer, Alpha Lock, isi warna",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
                leadingIcon = { Icon(Icons.Filled.Brush, contentDescription = null) },
                onClick = { open = false; repo.markDrawingBook(bookId); startDrawing(ctx) },
            )
        }
    }
}

// ================= isi buku: kisi catatan =================

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BookScreen(
    repo: Repository,
    book: Book,
    notes: List<Note>,
    count: Int,
    thumbs: Long,
    actions: LibActions,
    all: List<Book>,
    counts: Map<String, Int>,
    onUp: () -> Unit,
) {
    var menu by remember { mutableStateOf(false) }
    val wide = wideScreen()
    val children = remember(all, book.id) { shelfOrder(childBooks(all, book.id)) }
    val path = remember(all, book.id) { bookPath(all, book.id) }
    val parentTitle = path.getOrNull(path.size - 2)?.title
    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = onUp) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = if (parentTitle != null) "Kembali ke $parentTitle" else "Kembali ke rak")
                    }
                },
                title = {
                    Column {
                        Text(book.title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        // Jalur folder: Rak › Seminar › TDA, lalu isinya.
                        val trail = if (path.size > 1) (listOf("Rak") + path.dropLast(1).map { it.title }).joinToString(" › ") + " · " else ""
                        Text(
                            trail + "$count catatan" + if (children.isNotEmpty()) " · ${children.size} sub-buku" else "",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                },
                actions = {
                    IconButton(onClick = actions.search) { Icon(Icons.Filled.Search, contentDescription = "Cari catatan") }
                    if (book.manualNotes) {
                        IconButton(onClick = { actions.autoNoteOrder(book.id) }) {
                            Icon(Icons.Filled.Update, contentDescription = "Urutkan menurut terakhir diubah")
                        }
                    }
                    AddMenu(
                        firstLabel = "Catatan baru", onFirst = { actions.show(LibDialog.NewNote(book.id)) }, repo = repo, bookId = book.id,
                        onSubBook = { actions.show(LibDialog.NewBook(book.id)) },
                    ) { open ->
                        IconButton(onClick = open) { Icon(Icons.Filled.Add, contentDescription = "Tambah catatan atau gambar") }
                    }
                    Box {
                        IconButton(onClick = { menu = true }) { Icon(Icons.Filled.MoreVert, contentDescription = "Pilihan buku") }
                        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                            DropdownMenuItem(
                                text = { Text("Ubah nama & sampul") },
                                leadingIcon = { Icon(Icons.Filled.Edit, contentDescription = null) },
                                onClick = { menu = false; actions.show(LibDialog.EditBook(book)) },
                            )
                            DropdownMenuItem(
                                text = { Text("Sub-buku baru") },
                                leadingIcon = { Icon(Icons.Filled.CreateNewFolder, contentDescription = null) },
                                onClick = { menu = false; actions.show(LibDialog.NewBook(book.id)) },
                            )
                            DropdownMenuItem(
                                text = { Text("Pindahkan buku ke…") },
                                leadingIcon = { Icon(Icons.AutoMirrored.Filled.DriveFileMove, contentDescription = null) },
                                onClick = { menu = false; actions.show(LibDialog.MoveBook(book)) },
                            )
                            DropdownMenuItem(
                                text = { Text(if (book.pinned) "Lepas sematan buku" else "Sematkan buku di depan") },
                                leadingIcon = { Icon(Icons.Filled.PushPin, contentDescription = null) },
                                onClick = { menu = false; actions.pinBook(book, !book.pinned) },
                            )
                            if (book.manualNotes) {
                                DropdownMenuItem(
                                    text = { Text("Urutkan catatan: terakhir diubah") },
                                    leadingIcon = { Icon(Icons.Filled.Update, contentDescription = null) },
                                    onClick = { menu = false; actions.autoNoteOrder(book.id) },
                                )
                            }
                            DropdownMenuItem(
                                text = { Text("Hapus buku") },
                                leadingIcon = { Icon(Icons.Filled.Delete, contentDescription = null) },
                                onClick = { menu = false; actions.show(LibDialog.DeleteBook(book, count)) },
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
        floatingActionButton = {
            AddMenu(
                firstLabel = "Catatan baru", onFirst = { actions.show(LibDialog.NewNote(book.id)) }, repo = repo, bookId = book.id,
                onSubBook = { actions.show(LibDialog.NewBook(book.id)) },
            ) { open ->
                FloatingActionButton(onClick = open, shape = CircleShape) {
                    Icon(Icons.Filled.Add, contentDescription = "Tambah catatan atau gambar")
                }
            }
        },
        containerColor = MaterialTheme.colorScheme.background,
    ) { pad ->
        Column(Modifier.padding(top = pad.calculateTopPadding()).fillMaxSize()) {
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))
            val side = if (wide) 40.dp else 20.dp
            if (children.isNotEmpty()) {
                SubBooksRow(repo, children, all, counts, actions, side)
                HorizontalDivider(
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
                    modifier = Modifier.padding(horizontal = side),
                )
            }
            NotesGrid(
                repo = repo,
                bookId = book.id,
                notes = notesOrder(notes.filter { it.bookId == book.id }, book.manualNotes),
                thumbs = thumbs,
                padding = PaddingValues(start = side, end = side, top = if (wide) 36.dp else 20.dp, bottom = pad.calculateBottomPadding() + 110.dp),
                actions = actions,
                onCreate = { actions.show(LibDialog.NewNote(book.id)) },
            )
        }
    }
}

/** Sub-buku di dalam buku ini: satu baris sampul kecil yang bisa digeser ke samping. */
@Composable
private fun SubBooksRow(repo: Repository, children: List<Book>, all: List<Book>, counts: Map<String, Int>, actions: LibActions, side: androidx.compose.ui.unit.Dp) {
    Column(Modifier.fillMaxWidth().padding(top = 16.dp, bottom = 12.dp)) {
        Text(
            "Sub-buku",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = side, bottom = 10.dp),
        )
        androidx.compose.foundation.lazy.LazyRow(
            contentPadding = PaddingValues(horizontal = side),
            horizontalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            items(children.size, key = { children[it].id }) { k ->
                val b = children[k]
                val cover = rememberCover(repo, b.cover)
                var menu by remember { mutableStateOf(false) }
                val sub = childBooks(all, b.id).size
                Column(
                    Modifier
                        .width(96.dp)
                        .combinedClickable(
                            onClickLabel = "Buka buku",
                            onLongClick = { menu = true },
                            onClick = { actions.selectBook(b.id) },
                        ),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Box {
                        BookCover(title = b.title, subtitle = "", color = b.color, selected = false, image = cover, label = false, modifier = Modifier.fillMaxWidth())
                        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                            DropdownMenuItem(
                                text = { Text("Ubah nama & sampul") },
                                leadingIcon = { Icon(Icons.Filled.Edit, contentDescription = null) },
                                onClick = { menu = false; actions.show(LibDialog.EditBook(b)) },
                            )
                            DropdownMenuItem(
                                text = { Text("Pindahkan buku ke…") },
                                leadingIcon = { Icon(Icons.AutoMirrored.Filled.DriveFileMove, contentDescription = null) },
                                onClick = { menu = false; actions.show(LibDialog.MoveBook(b)) },
                            )
                            DropdownMenuItem(
                                text = { Text("Hapus buku") },
                                leadingIcon = { Icon(Icons.Filled.Delete, contentDescription = null) },
                                onClick = { menu = false; actions.show(LibDialog.DeleteBook(b, counts[b.id] ?: 0)) },
                            )
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    Text(b.title, style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Text(
                        "${counts[b.id] ?: 0} catatan" + if (sub > 0) "\n$sub sub-buku" else "",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )
                }
            }
        }
    }
}

// ================= cari catatan =================

/**
 * Cari di semua catatan: judul, teks ketik, isi stiker, dan tulisan tangan yang sudah dibaca.
 * Tulisan tangan dari catatan lama dibaca di latar selama layar ini terbuka.
 */
@Composable
private fun SearchScreen(repo: Repository, onClose: () -> Unit, onOpen: (SearchHit) -> Unit) {
    var query by rememberSaveable { mutableStateOf("") }
    var hits by remember { mutableStateOf<List<SearchHit>>(emptyList()) }
    var searched by remember { mutableStateOf(false) }
    var indexing by remember { mutableStateOf(true) }
    var round by remember { mutableIntStateOf(0) }
    val focus = remember { FocusRequester() }
    BackHandler(onBack = onClose)
    LaunchedEffect(Unit) {
        runCatching { focus.requestFocus() }
        runCatching { withContext(Dispatchers.IO) { PageIndexer.indexMissing(repo) } }
        indexing = false
        round++
    }
    LaunchedEffect(query, round) {
        val q = query.trim()
        if (q.isEmpty()) {
            hits = emptyList(); searched = false
            return@LaunchedEffect
        }
        delay(220)
        hits = withContext(Dispatchers.IO) { runCatching { repo.search(q) }.getOrDefault(emptyList()) }
        searched = true
    }
    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = onClose) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Tutup pencarian") }
                },
                title = {
                    OutlinedTextField(
                        value = query,
                        onValueChange = { query = it },
                        placeholder = { Text("Cari judul, teks, atau tulisan tangan") },
                        singleLine = true,
                        leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                        trailingIcon = {
                            if (query.isNotEmpty()) {
                                IconButton(onClick = { query = "" }) { Icon(Icons.Filled.Close, contentDescription = "Hapus") }
                            }
                        },
                        modifier = Modifier.fillMaxWidth().padding(end = 12.dp).focusRequester(focus),
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
        containerColor = MaterialTheme.colorScheme.background,
    ) { pad ->
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = pad.calculateTopPadding() + 8.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (indexing) {
                item(key = "indexing") {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.width(10.dp))
                        Text(
                            "Menyiapkan tulisan tangan untuk pencarian…",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            if (searched && hits.isEmpty()) {
                item(key = "empty") {
                    Text(
                        "Tidak ada catatan yang cocok dengan “${query.trim()}”.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 16.dp),
                    )
                }
            }
            items(hits.size) { i -> SearchRow(hits[i], onOpen) }
        }
    }
}

@Composable
private fun SearchRow(hit: SearchHit, onOpen: (SearchHit) -> Unit) {
    val mark = MaterialTheme.colorScheme.primaryContainer
    val snippet = remember(hit) {
        buildAnnotatedString {
            append(hit.snippet)
            val start = hit.matchStart.coerceIn(0, hit.snippet.length)
            val end = (hit.matchStart + hit.matchLength).coerceIn(start, hit.snippet.length)
            addStyle(SpanStyle(background = mark, fontWeight = FontWeight.SemiBold), start, end)
        }
    }
    Surface(
        shape = RoundedCornerShape(14.dp),
        tonalElevation = 1.dp,
        modifier = Modifier.fillMaxWidth().clickable { onOpen(hit) },
    ) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(hit.note.title, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                hit.bookTitle + if (hit.pageIndex >= 0) " · Halaman ${hit.pageIndex + 1}" else " · Judul",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (hit.pageIndex >= 0) Text(snippet, style = MaterialTheme.typography.bodyMedium, maxLines = 3, overflow = TextOverflow.Ellipsis)
        }
    }
}

// ================= kartu =================

/** Gambar sampul yang sudah dibuka, supaya tidak dibaca ulang saat menggulir. */
private object CoverCache {
    private val cache = object : android.util.LruCache<String, ImageBitmap>(32) {}

    fun peek(name: String): ImageBitmap? = if (name.isBlank()) null else cache.get(name)

    fun load(name: String, file: java.io.File?): ImageBitmap? {
        if (file == null || !file.exists()) return null
        cache.get(name)?.let { return it }
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.path, bounds)
        var sample = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= 720) sample *= 2
        val bmp = BitmapFactory.decodeFile(file.path, BitmapFactory.Options().apply { inSampleSize = sample }) ?: return null
        return bmp.asImageBitmap().also { cache.put(name, it) }
    }
}

@Composable
private fun rememberCover(repo: Repository, name: String): ImageBitmap? {
    val img by produceState(initialValue = CoverCache.peek(name), name) {
        value = if (name.isBlank()) null
        else CoverCache.peek(name) ?: withContext(Dispatchers.IO) { runCatching { CoverCache.load(name, repo.coverFile(name)) }.getOrNull() }
    }
    return img
}

@Composable
private fun BookTile(
    repo: Repository,
    book: Book,
    count: Int,
    modifier: Modifier,
    actions: LibActions,
    reorder: ReorderState,
    index: Int,
    subCount: Int = 0,
) {
    var menu by remember { mutableStateOf(false) }
    val dragging = reorder.dragKey == book.id
    val isTarget = reorder.dragging && !dragging && reorder.target == index
    val cover = rememberCover(repo, book.cover)
    Column(
        modifier
            .reorderItem(reorder, book.id, index)
            .clickable(onClickLabel = "Buka buku") { actions.selectBook(book.id) }
            // Tahan lalu geser untuk memindah; tahan lalu lepas untuk pilihan lain.
            .reorderOnLongPress(reorder, book.id) { menu = true },
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(Modifier.fillMaxWidth()) {
            BookCover(
                title = book.title,
                subtitle = "",
                color = book.color,
                selected = dragging,
                image = cover,
                label = false,
                modifier = Modifier.fillMaxWidth(),
            )
            if (book.pinned) PinBadge(Modifier.align(Alignment.TopEnd).padding(top = 8.dp, end = 8.dp))
            if (isTarget) {
                Box(
                    Modifier
                        .matchParentSize()
                        .border(3.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(topStart = 4.dp, bottomStart = 4.dp, topEnd = 12.dp, bottomEnd = 12.dp))
                )
            }
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                DropdownMenuItem(
                    text = { Text(if (book.pinned) "Lepas sematan" else "Sematkan di depan") },
                    leadingIcon = { Icon(Icons.Filled.PushPin, contentDescription = null) },
                    onClick = { menu = false; actions.pinBook(book, !book.pinned) },
                )
                DropdownMenuItem(
                    text = { Text("Ubah nama & sampul") },
                    leadingIcon = { Icon(Icons.Filled.Edit, contentDescription = null) },
                    onClick = { menu = false; actions.show(LibDialog.EditBook(book)) },
                )
                DropdownMenuItem(
                    text = { Text("Pindahkan buku ke…") },
                    leadingIcon = { Icon(Icons.AutoMirrored.Filled.DriveFileMove, contentDescription = null) },
                    onClick = { menu = false; actions.show(LibDialog.MoveBook(book)) },
                )
                DropdownMenuItem(
                    text = { Text("Hapus buku") },
                    leadingIcon = { Icon(Icons.Filled.Delete, contentDescription = null) },
                    onClick = { menu = false; actions.show(LibDialog.DeleteBook(book, count)) },
                )
            }
        }
        Spacer(Modifier.height(14.dp))
        Text(
            book.title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Normal,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.fillMaxWidth(),
        )
        Text(
            "$count catatan" + if (subCount > 0) " · $subCount sub-buku" else "",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun NotesGrid(
    repo: Repository,
    bookId: String,
    notes: List<Note>,
    thumbs: Long,
    padding: PaddingValues,
    actions: LibActions,
    onCreate: () -> Unit,
) {
    if (notes.isEmpty()) {
        Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                PaperPreview(
                    PaperTemplate.LINED, PaperColors[0].second,
                    Modifier.width(84.dp).aspectRatio(0.72f).shadow(3.dp, RoundedCornerShape(4.dp)).clip(RoundedCornerShape(4.dp)),
                )
                Text("Buku ini masih kosong", style = MaterialTheme.typography.titleMedium)
                Text(
                    "Buat catatan pertama, lalu mulai menulis.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
                FilledTonalButton(onClick = onCreate) {
                    Icon(Icons.Filled.Add, contentDescription = null)
                    Spacer(Modifier.width(6.dp))
                    Text("Catatan baru")
                }
            }
        }
        return
    }
    val gridState = rememberLazyGridState()
    val reorder = remember(bookId) { ReorderState() }
    bindReorder(reorder, notes, { it.pinned }, { it.id }) { ids -> actions.reorderNotes(bookId, ids) }
    val edge = with(LocalDensity.current) { 72.dp.toPx() }
    LaunchedEffect(reorder.dragKey) { reorder.autoScroll(gridState, edge) }
    val wide = wideScreen()
    LazyVerticalGrid(
        columns = GridCells.Fixed(gridColumns()),
        state = gridState,
        modifier = Modifier.fillMaxSize().onGloballyPositioned { reorder.listBounds = rectInRoot(it) },
        contentPadding = padding,
        horizontalArrangement = Arrangement.spacedBy(if (wide) 44.dp else 20.dp),
        verticalArrangement = Arrangement.spacedBy(if (wide) 34.dp else 24.dp),
    ) {
        itemsIndexed(notes, key = { _, n -> n.id }) { i, n ->
            NoteCard(
                repo, n, thumbs, actions, reorder, i,
                modifier = if (reorder.dragKey == n.id) Modifier else Modifier.animateItem(fadeInSpec = null, fadeOutSpec = null),
            )
        }
    }
}

@Composable
private fun NoteCard(
    repo: Repository,
    note: Note,
    thumbs: Long,
    actions: LibActions,
    reorder: ReorderState,
    index: Int,
    modifier: Modifier = Modifier,
) {
    var menu by remember { mutableStateOf(false) }
    val dragging = reorder.dragKey == note.id
    val isTarget = reorder.dragging && !dragging && reorder.target == index
    val file = remember(note.id) { repo.thumbFile(note.id) }
    val bmp by produceState<ImageBitmap?>(initialValue = null, note.id, note.updated, thumbs) {
        value = withContext(Dispatchers.IO) {
            if (file.exists()) runCatching { BitmapFactory.decodeFile(file.absolutePath)?.asImageBitmap() }.getOrNull() else null
        }
    }
    val shape = RoundedCornerShape(topStart = 4.dp, bottomStart = 4.dp, topEnd = 12.dp, bottomEnd = 12.dp)
    Column(
        modifier
            .reorderItem(reorder, note.id, index)
            .clickable(onClickLabel = "Buka catatan") { actions.openNote(note) }
            // Tahan lalu geser untuk memindah; tahan lalu lepas untuk pilihan lain.
            .reorderOnLongPress(reorder, note.id) { menu = true },
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(Modifier.fillMaxWidth()) {
            Surface(
                shape = shape,
                shadowElevation = if (dragging) 10.dp else 4.dp,
                color = Color.White,
                border = if (isTarget) androidx.compose.foundation.BorderStroke(3.dp, MaterialTheme.colorScheme.primary) else null,
                modifier = Modifier.fillMaxWidth().aspectRatio(0.78f),
            ) {
                val b = bmp
                if (b != null) {
                    val isArt = note.art.isNotEmpty()
                    Image(
                        bitmap = b,
                        contentDescription = null,
                        contentScale = if (isArt) ContentScale.Fit else ContentScale.Crop,
                        alignment = if (isArt) Alignment.Center else Alignment.TopCenter,
                        modifier = Modifier.fillMaxSize().then(if (isArt) Modifier.padding(6.dp) else Modifier),
                    )
                } else {
                    PaperPreview(PaperTemplate.BLANK, PaperColors[0].second, Modifier.fillMaxSize())
                }
            }
            if (note.pinned) PinBadge(Modifier.align(Alignment.TopEnd).padding(top = 8.dp, end = 8.dp))
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                DropdownMenuItem(
                    text = { Text(if (note.pinned) "Lepas sematan" else "Sematkan di atas") },
                    leadingIcon = { Icon(Icons.Filled.PushPin, contentDescription = null) },
                    onClick = { menu = false; actions.pinNote(note, !note.pinned) },
                )
                DropdownMenuItem(
                    text = { Text("Ganti nama") },
                    leadingIcon = { Icon(Icons.Filled.Edit, contentDescription = null) },
                    onClick = { menu = false; actions.show(LibDialog.RenameNote(note)) },
                )
                DropdownMenuItem(
                    text = { Text("Pindahkan ke buku lain") },
                    leadingIcon = { Icon(Icons.Filled.SwapHoriz, contentDescription = null) },
                    onClick = { menu = false; actions.show(LibDialog.MoveNote(note)) },
                )
                DropdownMenuItem(
                    text = { Text("Hapus catatan") },
                    leadingIcon = { Icon(Icons.Filled.Delete, contentDescription = null) },
                    onClick = { menu = false; actions.show(LibDialog.DeleteNote(note)) },
                )
            }
        }
        Spacer(Modifier.height(14.dp))
        Text(
            note.title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Normal,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.fillMaxWidth(),
        )
        Text(
            if (note.art.isNotEmpty()) "${formatWhen(note.updated)} · Gambar" else "${formatWhen(note.updated)} · ${note.pageCount} halaman",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            maxLines = 1,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

// ================= dialog =================

@Composable
private fun LibraryDialogs(
    repo: Repository,
    dialog: LibDialog?,
    shelf: List<Book>,
    onDismiss: () -> Unit,
    actions: LibActions,
) {
    when (dialog) {
        null -> Unit
        LibDialog.Theme -> ThemeDialog(repo, onDismiss)
        LibDialog.Backup -> BackupDialog(repo, onDismiss)
        is LibDialog.Crash -> CrashDialog(dialog.fresh, onDismiss)
        is LibDialog.NewBook -> BookDialog(
            repo = repo,
            heading = if (dialog.parent.isEmpty()) "Buku baru" else "Sub-buku baru",
            initialTitle = "",
            initialColor = BookColors[shelf.size % BookColors.size],
            initialCover = "",
            confirm = "Buat buku",
            onDismiss = onDismiss,
        ) { title, color, cover ->
            val b = repo.createBook(title, color, cover, parent = dialog.parent)
            onDismiss()
            actions.selectBook(b.id)
        }
        is LibDialog.MoveBook -> {
            val all = repo.books.value
            val blocked = descendantBooks(all, dialog.book.id) + dialog.book.id
            AlertDialog(
                onDismissRequest = onDismiss,
                title = { Text("Pindahkan “${dialog.book.title}” ke") },
                text = {
                    Column(Modifier.verticalScroll(rememberScrollState())) {
                        if (dialog.book.parent.isNotEmpty()) {
                            BookPickRow(title = "Rak (paling atas)", color = null, depth = 0) {
                                repo.moveBook(dialog.book.id, "")
                                onDismiss()
                            }
                        }
                        for ((b, depth) in bookTree(all)) {
                            if (b.id in blocked || b.id == dialog.book.parent) continue
                            BookPickRow(title = b.title, color = b.color, depth = depth) {
                                repo.moveBook(dialog.book.id, b.id)
                                onDismiss()
                            }
                        }
                    }
                },
                confirmButton = { TextButton(onClick = onDismiss) { Text("Tutup") } },
            )
        }
        is LibDialog.EditBook -> BookDialog(
            repo = repo,
            heading = "Ubah buku",
            initialTitle = dialog.book.title,
            initialColor = dialog.book.color,
            initialCover = dialog.book.cover,
            confirm = "Simpan",
            onDismiss = onDismiss,
        ) { title, color, cover ->
            repo.updateBook(dialog.book.id, title, color, cover)
            onDismiss()
        }
        is LibDialog.DeleteBook -> ConfirmDialog(
            title = "Hapus “${dialog.book.title}”?",
            message = (if (dialog.count == 0) "Buku ini kosong dan akan dipindah ke Sampah." else "Buku ini beserta ${dialog.count} catatan di dalamnya akan dipindah ke Sampah.") +
                " Masih bisa dipulihkan selama ${Repository.TRASH_DAYS} hari.",
            confirm = "Hapus",
            onDismiss = onDismiss,
        ) {
            repo.deleteBook(dialog.book.id)
            onDismiss()
            // Sub-buku dihapus: kembali ke buku induknya.
            actions.selectBook(dialog.book.parent.ifEmpty { null })
        }
        is LibDialog.NewNote -> NewNoteDialog(
            repo.prefs, onDismiss,
            onPdf = { actions.importPdf(dialog.bookId); onDismiss() },
            onScan = { actions.scanDoc(dialog.bookId); onDismiss() },
        ) { title, w, h, template, color ->
            val n = repo.createNote(dialog.bookId, title, w, h, template, color)
            onDismiss()
            actions.openNote(n)
        }
        is LibDialog.RenameNote -> TextInputDialog(
            title = "Ganti nama catatan",
            initial = dialog.note.title,
            label = "Nama catatan",
            confirm = "Simpan",
            onDismiss = onDismiss,
        ) {
            repo.renameNote(dialog.note.id, it)
            onDismiss()
        }
        is LibDialog.MoveNote -> AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text("Pindahkan ke buku") },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    // Semua buku dan sub-buku, berjenjang seperti di rak.
                    val tree = bookTree(repo.books.value)
                    for ((b, depth) in tree) {
                        if (b.id == dialog.note.bookId) continue
                        BookPickRow(title = b.title, color = b.color, depth = depth) {
                            repo.moveNote(dialog.note.id, b.id)
                            onDismiss()
                        }
                    }
                    if (tree.size < 2) Text("Belum ada buku lain. Buat buku baru dulu dari rak.")
                }
            },
            confirmButton = { TextButton(onClick = onDismiss) { Text("Tutup") } },
        )
        is LibDialog.DeleteNote -> ConfirmDialog(
            title = "Hapus “${dialog.note.title}”?",
            message = "Catatan ini dipindah ke Sampah dan masih bisa dipulihkan selama ${Repository.TRASH_DAYS} hari.",
            confirm = "Hapus",
            onDismiss = onDismiss,
        ) {
            repo.deleteNote(dialog.note.id)
            onDismiss()
        }
        LibDialog.Trash -> TrashDialog(repo, onDismiss)
    }
}

/** Satu pilihan buku tujuan (berjenjang menurut [depth]); [color] null = rak. */
@Composable
private fun BookPickRow(title: String, color: Int?, depth: Int, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(start = 8.dp + 22.dp * depth, end = 8.dp, top = 12.dp, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (color != null) {
            Box(Modifier.size(width = 18.dp, height = 24.dp).clip(RoundedCornerShape(2.dp, 5.dp, 5.dp, 2.dp)).background(Color(color)))
        } else {
            Icon(Icons.Filled.Home, contentDescription = null, modifier = Modifier.size(22.dp))
        }
        Spacer(Modifier.width(14.dp))
        Text(title, style = MaterialTheme.typography.bodyLarge)
    }
}

/** Tombol Sampah di rak, dengan jumlah isinya. */
@Composable
private fun TrashButton(repo: Repository, onClick: () -> Unit) {
    val notes by repo.trashNotes.collectAsState()
    val books by repo.trashBooks.collectAsState()
    val n = books.count { it.withBook == null } + notes.count { it.withBook == null }
    IconButton(onClick = onClick) {
        BadgedBox(badge = { if (n > 0) Badge { Text(if (n > 99) "99+" else "$n") } }) {
            Icon(Icons.Filled.Delete, contentDescription = if (n > 0) "Sampah, $n isi" else "Sampah")
        }
    }
}

private sealed interface TrashAsk {
    data class Note(val id: String, val title: String) : TrashAsk
    data class Book(val id: String, val title: String, val count: Int) : TrashAsk
    data object Empty : TrashAsk
}

/** Isi Sampah: pulihkan atau hapus permanen; isi yang lebih dari 30 hari terhapus sendiri. */
@Composable
private fun TrashDialog(repo: Repository, onDismiss: () -> Unit) {
    val tNotes by repo.trashNotes.collectAsState()
    val tBooks by repo.trashBooks.collectAsState()
    val books by repo.books.collectAsState()
    var ask by remember { mutableStateOf<TrashAsk?>(null) }
    var info by remember { mutableStateOf<String?>(null) }
    val loose = tNotes.filter { it.withBook == null }
    // Buku dan catatan dicampur, yang terakhir dihapus di atas.
    val rows: List<Any> = (tBooks.filter { it.withBook == null }.map { it as Any } + loose.map { it as Any })
        .sortedByDescending { if (it is Repository.TrashedBook) it.at else (it as Repository.TrashedNote).at }
    val empty = rows.isEmpty()
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Sampah") },
        text = {
            Column(Modifier.fillMaxWidth()) {
                Text(
                    "Catatan dan buku yang dihapus disimpan di sini selama ${Repository.TRASH_DAYS} hari, lalu terhapus permanen dengan sendirinya.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                info?.let {
                    Spacer(Modifier.height(8.dp))
                    Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                }
                Spacer(Modifier.height(10.dp))
                if (empty) {
                    Box(Modifier.fillMaxWidth().padding(vertical = 28.dp), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(Icons.Filled.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.outline, modifier = Modifier.size(40.dp))
                            Spacer(Modifier.height(8.dp))
                            Text("Sampah kosong.", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                } else {
                    LazyColumn(Modifier.fillMaxWidth().heightIn(max = 440.dp)) {
                        items(rows, key = { if (it is Repository.TrashedBook) "b" + it.book.id else "n" + (it as Repository.TrashedNote).note.id }) { r ->
                            when (r) {
                                is Repository.TrashedBook -> {
                                    val count = tNotes.count { it.withBook == r.book.id }
                                    TrashRow(
                                        icon = {
                                            Box(
                                                Modifier
                                                    .size(width = 30.dp, height = 40.dp)
                                                    .clip(RoundedCornerShape(3.dp, 7.dp, 7.dp, 3.dp))
                                                    .background(Color(r.book.color))
                                            )
                                        },
                                        title = r.book.title,
                                        subtitle = "Buku · $count catatan · " + trashAge(r.at),
                                        onRestore = {
                                            repo.restoreBook(r.book.id)
                                            info = "“${r.book.title}” kembali ke rak."
                                        },
                                        onDelete = { ask = TrashAsk.Book(r.book.id, r.book.title, count) },
                                    )
                                }
                                is Repository.TrashedNote -> {
                                    val from = books.firstOrNull { it.id == r.note.bookId }?.title
                                    TrashRow(
                                        icon = { TrashThumb(repo, r.note.id) },
                                        title = r.note.title,
                                        subtitle = (if (from != null) "Dari $from · " else "") + trashAge(r.at),
                                        onRestore = {
                                            val to = repo.restoreNote(r.note.id)
                                            val name = books.firstOrNull { it.id == to }?.title ?: repo.books.value.firstOrNull { it.id == to }?.title
                                            info = "“${r.note.title}” kembali" + (if (name != null) " ke buku $name." else ".")
                                        },
                                        onDelete = { ask = TrashAsk.Note(r.note.id, r.note.title) },
                                    )
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Tutup") } },
        dismissButton = {
            if (!empty) TextButton(onClick = { ask = TrashAsk.Empty }) { Text("Kosongkan sampah", color = MaterialTheme.colorScheme.error) }
        },
    )
    when (val a = ask) {
        is TrashAsk.Note -> ConfirmDialog(
            title = "Hapus permanen “${a.title}”?",
            message = "Catatan ini dan semua halamannya dihapus selamanya dan tidak bisa dipulihkan.",
            confirm = "Hapus permanen",
            onDismiss = { ask = null },
        ) {
            repo.deleteNoteForever(a.id)
            ask = null
        }
        is TrashAsk.Book -> ConfirmDialog(
            title = "Hapus permanen “${a.title}”?",
            message = "Buku ini beserta ${a.count} catatannya dihapus selamanya dan tidak bisa dipulihkan.",
            confirm = "Hapus permanen",
            onDismiss = { ask = null },
        ) {
            repo.deleteBookForever(a.id)
            ask = null
        }
        TrashAsk.Empty -> ConfirmDialog(
            title = "Kosongkan sampah?",
            message = "Semua isi Sampah dihapus selamanya dan tidak bisa dipulihkan.",
            confirm = "Kosongkan",
            onDismiss = { ask = null },
        ) {
            repo.emptyTrash()
            ask = null
            info = null
        }
        null -> Unit
    }
}

@Composable
private fun TrashRow(icon: @Composable () -> Unit, title: String, subtitle: String, onRestore: () -> Unit, onDelete: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(width = 36.dp, height = 46.dp), contentAlignment = Alignment.Center) { icon() }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(subtitle, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2)
        }
        TextButton(onClick = onRestore) { Text("Pulihkan") }
        IconButton(onClick = onDelete) {
            Icon(Icons.Filled.DeleteForever, contentDescription = "Hapus permanen", tint = MaterialTheme.colorScheme.error)
        }
    }
}

/** Gambar kecil catatan di Sampah. */
@Composable
private fun TrashThumb(repo: Repository, noteId: String) {
    val bmp by produceState<ImageBitmap?>(initialValue = null, noteId) {
        value = withContext(Dispatchers.IO) {
            val f = repo.thumbFile(noteId)
            if (!f.exists()) null else runCatching {
                BitmapFactory.decodeFile(f.absolutePath, BitmapFactory.Options().apply { inSampleSize = 4 })?.asImageBitmap()
            }.getOrNull()
        }
    }
    Surface(
        shape = RoundedCornerShape(3.dp, 6.dp, 6.dp, 3.dp),
        color = Color.White,
        shadowElevation = 2.dp,
        modifier = Modifier.size(width = 34.dp, height = 44.dp),
    ) {
        val b = bmp
        if (b != null) {
            Image(b, contentDescription = null, contentScale = ContentScale.Crop, alignment = Alignment.TopCenter, modifier = Modifier.fillMaxSize())
        } else {
            Box(contentAlignment = Alignment.Center) {
                Icon(Icons.Filled.Description, contentDescription = null, tint = Color(0xFF9A97A0), modifier = Modifier.size(20.dp))
            }
        }
    }
}

/** "Dihapus hari ini · sisa 30 hari". */
private fun trashAge(at: Long): String {
    val days = ((System.currentTimeMillis() - at) / (24L * 3600_000L)).toInt().coerceAtLeast(0)
    val left = (Repository.TRASH_DAYS - days).coerceAtLeast(1)
    val ago = when (days) {
        0 -> "dihapus hari ini"
        1 -> "dihapus kemarin"
        else -> "dihapus $days hari lalu"
    }
    return "$ago · sisa $left hari"
}

@Composable
private fun BookDialog(
    repo: Repository,
    heading: String,
    initialTitle: String,
    initialColor: Int,
    initialCover: String,
    confirm: String,
    onDismiss: () -> Unit,
    onConfirm: (String, Int, String) -> Unit,
) {
    var title by remember { mutableStateOf(initialTitle) }
    var color by remember { mutableStateOf(initialColor) }
    var cover by remember { mutableStateOf(initialCover) }
    var busy by remember { mutableStateOf(false) }
    var failed by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    // Gambar yang dipilih tapi tidak jadi dipakai dibuang.
    fun drop(name: String) { if (name.isNotBlank() && name != initialCover) repo.dropUnusedCover(name) }
    val pick = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) {
            busy = true
            failed = false
            scope.launch {
                val name = withContext(Dispatchers.IO) { runCatching { repo.importCover(context.applicationContext, uri) }.getOrNull() }
                busy = false
                if (name != null) { drop(cover); cover = name } else failed = true
            }
        }
    }
    val image = rememberCover(repo, cover)
    AlertDialog(
        onDismissRequest = { drop(cover); onDismiss() },
        title = { Text(heading) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    BookCover(
                        title = title.ifBlank { "Judul buku" }, subtitle = "", color = color,
                        image = image, label = false, modifier = Modifier.width(84.dp),
                    )
                    Spacer(Modifier.width(16.dp))
                    OutlinedTextField(
                        value = title,
                        onValueChange = { title = it },
                        label = { Text("Nama buku") },
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                    )
                }
                Text("Gambar sampul", style = MaterialTheme.typography.labelLarge)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedButton(
                        onClick = { runCatching { pick.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) } },
                        enabled = !busy,
                    ) {
                        Text(if (cover.isBlank()) "Pilih dari galeri…" else "Ganti gambar…")
                    }
                    if (busy) {
                        Spacer(Modifier.width(12.dp))
                        CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                    }
                    if (cover.isNotBlank() && !busy) {
                        Spacer(Modifier.width(8.dp))
                        TextButton(onClick = { drop(cover); cover = "" }) { Text("Pakai warna saja") }
                    }
                }
                if (failed) {
                    Text("Gambar itu tidak bisa dibuka. Coba gambar lain.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                }
                Text(if (cover.isBlank()) "Warna sampul" else "Warna sampul (dipakai bila gambar dihapus)", style = MaterialTheme.typography.labelLarge)
                Row(Modifier.horizontalScroll(rememberScrollState())) {
                    BookColors.forEach { c -> ColorDot(Color(c), c == color, { color = c }, size = 26.dp, description = "Warna sampul") }
                }
            }
        },
        confirmButton = { TextButton(onClick = { onConfirm(title.trim(), color, cover) }, enabled = !busy) { Text(confirm) } },
        dismissButton = { TextButton(onClick = { drop(cover); onDismiss() }) { Text("Batal") } },
    )
}

@Composable
private fun NewNoteDialog(
    prefs: SharedPreferences,
    onDismiss: () -> Unit,
    onPdf: () -> Unit,
    onScan: () -> Unit,
    onCreate: (String, Float, Float, PaperTemplate, Int) -> Unit,
) {
    val defaultTitle = remember { "Catatan " + SimpleDateFormat("d MMM", Locale.forLanguageTag("id-ID")).format(System.currentTimeMillis()) }
    var title by remember { mutableStateOf(defaultTitle) }
    var paper by remember {
        mutableStateOf(
            PaperChoice(
                size = runCatching { PaperSize.valueOf(prefs.getString("paper_size", "A4") ?: "A4") }.getOrNull(),
                landscape = prefs.getBoolean("paper_land", false),
                customW = prefs.getString("paper_custom_w", "200") ?: "200",
                customH = prefs.getString("paper_custom_h", "200") ?: "200",
            )
        )
    }
    var template by remember { mutableStateOf(runCatching { PaperTemplate.valueOf(prefs.getString("paper_tpl", "LINED") ?: "LINED") }.getOrDefault(PaperTemplate.LINED)) }
    var color by remember { mutableStateOf(prefs.getInt("paper_color", PaperColors[0].second)) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Catatan baru") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Nama catatan") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                PaperOptions(
                    paper = paper, template = template, color = color,
                    onPaper = { paper = it }, onTemplate = { template = it }, onColor = { color = it },
                )
                OutlinedButton(onClick = onPdf, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Filled.PictureAsPdf, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Buka PDF untuk ditulisi…")
                }
                OutlinedButton(onClick = onScan, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Filled.DocumentScanner, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Pindai dokumen dengan kamera…")
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val w = paper.widthPt() ?: return@TextButton
                    val h = paper.heightPt() ?: return@TextButton
                    prefs.edit()
                        .putString("paper_size", paper.size?.name ?: "CUSTOM")
                        .putBoolean("paper_land", paper.landscape)
                        .putString("paper_custom_w", paper.customW)
                        .putString("paper_custom_h", paper.customH)
                        .putString("paper_tpl", template.name)
                        .putInt("paper_color", color)
                        .apply()
                    onCreate(title.trim(), w, h, template, color)
                },
                enabled = paper.valid,
            ) { Text("Buat") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Batal") } },
    )
}

/** Pilihan kertas: dipakai saat membuat catatan dan saat mengubah kertas di editor. */
@Composable
fun PaperOptions(
    paper: PaperChoice,
    template: PaperTemplate,
    color: Int,
    onPaper: (PaperChoice) -> Unit,
    onTemplate: (PaperTemplate) -> Unit,
    onColor: (Int) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Ukuran kertas", style = MaterialTheme.typography.labelLarge)
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            PaperSize.entries.forEach { s ->
                FilterChip(selected = s == paper.size, onClick = { onPaper(paper.copy(size = s)) }, label = { Text(s.label) })
            }
            FilterChip(selected = paper.size == null, onClick = { onPaper(paper.copy(size = null)) }, label = { Text("Custom") })
        }
        val size = paper.size
        if (size != null) {
            Text(
                "${mm(size.wMm)} × ${mm(size.hMm)} mm",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text("Arah", style = MaterialTheme.typography.labelLarge)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected = !paper.landscape, onClick = { onPaper(paper.copy(landscape = false)) }, label = { Text("Tegak") })
                FilterChip(selected = paper.landscape, onClick = { onPaper(paper.copy(landscape = true)) }, label = { Text("Mendatar") })
            }
        } else {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = paper.customW,
                    onValueChange = { onPaper(paper.copy(customW = it.take(6))) },
                    label = { Text("Lebar (mm)") },
                    singleLine = true,
                    isError = PaperChoice.parseMm(paper.customW) == null,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = { onPaper(paper.copy(customW = paper.customH, customH = paper.customW)) }) {
                    Icon(Icons.Filled.SwapHoriz, contentDescription = "Tukar lebar dan tinggi")
                }
                OutlinedTextField(
                    value = paper.customH,
                    onValueChange = { onPaper(paper.copy(customH = it.take(6))) },
                    label = { Text("Tinggi (mm)") },
                    singleLine = true,
                    isError = PaperChoice.parseMm(paper.customH) == null,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.weight(1f),
                )
            }
            Text(
                if (paper.valid) "Ukuran custom ${paper.customW} × ${paper.customH} mm"
                else "Isi lebar dan tinggi antara ${PaperSize.CUSTOM_MIN_MM.toInt()} dan ${PaperSize.CUSTOM_MAX_MM.toInt()} mm.",
                style = MaterialTheme.typography.bodySmall,
                color = if (paper.valid) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.error,
            )
        }
        Text("Pola", style = MaterialTheme.typography.labelLarge)
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            PaperTemplate.entries.forEach { t ->
                val sel = t == template
                Column(
                    Modifier.clip(RoundedCornerShape(8.dp)).clickable { onTemplate(t) }.padding(4.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    PaperPreview(
                        t, color,
                        Modifier
                            .width(46.dp)
                            .aspectRatio(0.72f)
                            .clip(RoundedCornerShape(4.dp))
                            .border(
                                if (sel) 2.dp else 1.dp,
                                if (sel) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                                RoundedCornerShape(4.dp),
                            ),
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        t.label,
                        style = MaterialTheme.typography.labelMedium,
                        color = if (sel) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        Text("Warna kertas", style = MaterialTheme.typography.labelLarge)
        Row(Modifier.horizontalScroll(rememberScrollState())) {
            PaperColors.forEach { (name, c) -> ColorDot(Color(c), c == color, { onColor(c) }, size = 24.dp, description = "Kertas $name") }
        }
    }
}

/** Pengaturan tampilan: warna utama, terang/gelap, dan gaya huruf judul. Langsung berlaku. */
@Composable
private fun ThemeDialog(repo: Repository, onDismiss: () -> Unit) {
    val t by repo.theme.collectAsState()
    val fonts by repo.fonts.collectAsState()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var fontMessage by remember { mutableStateOf<String?>(null) }
    var showCrash by remember { mutableStateOf(false) }
    val pickFont =rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            scope.launch {
                val r = withContext(Dispatchers.IO) {
                    runCatching { repo.importFont(context.applicationContext, uri) }
                        .getOrElse { Repository.FontImport(emptyList(), "Berkas itu tidak bisa dibaca.") }
                }
                val entry = r.added.firstOrNull()
                fontMessage = when {
                    entry == null -> r.error ?: "Berkas itu bukan font yang bisa dipakai."
                    r.added.size == 1 -> "Font “${entry.name}” ditambahkan."
                    else -> "${r.added.size} font ditambahkan: ${r.added.joinToString { it.name }}."
                }
            }
        }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Tampilan") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Warna utama", style = MaterialTheme.typography.labelLarge)
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                    Accent.entries.forEach { a ->
                        val sel = a == t.accent
                        Column(
                            Modifier
                                .clip(RoundedCornerShape(10.dp))
                                .clickable { repo.setTheme(t.copy(accent = a)) }
                                .padding(6.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                if (sel) Box(Modifier.size(44.dp).border(2.dp, MaterialTheme.colorScheme.onSurface, CircleShape))
                                Box(Modifier.size(34.dp).clip(CircleShape).background(Color(a.light)))
                            }
                            Spacer(Modifier.height(4.dp))
                            Text(a.label, style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
                Text("Mode", style = MaterialTheme.typography.labelLarge)
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ThemeMode.entries.forEach { m ->
                        FilterChip(selected = m == t.mode, onClick = { repo.setTheme(t.copy(mode = m)) }, label = { Text(m.label) })
                    }
                }
                Text("Huruf judul", style = MaterialTheme.typography.labelLarge)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = t.serifTitles,
                        onClick = { repo.setTheme(t.copy(serifTitles = true)) },
                        label = { Text("Klasik", fontFamily = FontFamily.Serif) },
                    )
                    FilterChip(
                        selected = !t.serifTitles,
                        onClick = { repo.setTheme(t.copy(serifTitles = false)) },
                        label = { Text("Modern", fontFamily = FontFamily.SansSerif) },
                    )
                }
                Text("Font tambahan", style = MaterialTheme.typography.labelLarge)
                val imported = fonts.filter { it.imported }
                if (imported.isEmpty()) {
                    Text(
                        "Belum ada. Tambahkan berkas font .ttf atau .otf (juga .woff, atau ZIP unduhan Google Fonts).",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                imported.forEach { f ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(f.name, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                        IconButton(onClick = { repo.deleteFont(f) }) { Icon(Icons.Filled.Delete, contentDescription = "Hapus font ${f.name}") }
                    }
                }
                if (fontMessage != null) {
                    Text(fontMessage!!, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                }
                FilledTonalButton(onClick = {
                    pickFont.launch(arrayOf("*/*"))
                }) {
                    Icon(Icons.Filled.Add, contentDescription = null)
                    Spacer(Modifier.width(6.dp))
                    Text("Tambah font")
                }
                Text(
                    "Warna sampul buku dan warna kertas diatur dari masing-masing buku dan catatan.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                HorizontalDivider()
                Text("Bantuan", style = MaterialTheme.typography.labelLarge)
                Text(
                    "Bila aplikasi pernah tertutup sendiri, penyebabnya tersimpan di sini dan bisa dibagikan ke Claude.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedButton(onClick = { showCrash = true }) { Text("Laporan galat…") }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Selesai") } },
    )
    if (showCrash) CrashDialog(fresh = false, onDismiss = { showCrash = false })
}

private fun mm(v: Float): String =
    if (v % 1f == 0f) v.toInt().toString() else String.format(Locale.US, "%.1f", v).replace('.', ',')

/** Pilihan ukuran kertas: salah satu ukuran baku, atau ukuran custom dalam milimeter. */
data class PaperChoice(
    val size: PaperSize?,
    val landscape: Boolean,
    val customW: String,
    val customH: String,
) {
    fun widthPt(): Float? = if (size != null) size.widthPt(landscape) else parseMm(customW)?.let { PaperSize.mmToPt(it) }
    fun heightPt(): Float? = if (size != null) size.heightPt(landscape) else parseMm(customH)?.let { PaperSize.mmToPt(it) }
    val valid: Boolean get() = widthPt() != null && heightPt() != null

    companion object {
        fun parseMm(s: String): Float? =
            s.trim().replace(',', '.').toFloatOrNull()?.takeIf { it >= PaperSize.CUSTOM_MIN_MM && it <= PaperSize.CUSTOM_MAX_MM }

        /** Pilihan awal dari ukuran halaman yang sudah ada. */
        fun fromPage(w: Float, h: Float): PaperChoice {
            val (size, land) = PaperSize.match(w, h)
            val wMm = mmText(PaperSize.ptToMm(w))
            val hMm = mmText(PaperSize.ptToMm(h))
            return PaperChoice(size, land, wMm, hMm)
        }

        private fun mmText(v: Float): String {
            val r = Math.round(v * 10f) / 10f
            return if (r % 1f == 0f) r.toInt().toString() else r.toString().replace('.', ',')
        }
    }
}
