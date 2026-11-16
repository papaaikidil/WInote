package id.hananote.ui

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import id.hananote.data.Repository
import id.hananote.data.Backups
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow
import androidx.compose.runtime.collectAsState

/**
 * Tablet (lebar terpendek layar 600 dp ke atas) dan HP memakai tata letak yang berbeda:
 * tablet menampilkan rak dan isi buku sekaligus, HP memisahkannya ke dua layar.
 */
@Composable
fun HanaApp(repo: Repository, openRequest: MutableStateFlow<String?> = MutableStateFlow(null)) {
    val isTablet = LocalConfiguration.current.smallestScreenWidthDp >= 600
    var openNote by rememberSaveable { mutableStateOf<String?>(null) }
    var openBook by rememberSaveable { mutableStateOf<String?>(null) }
    val appCtx = LocalContext.current

    // Dibuka dari widget layar utama.
    val request by openRequest.collectAsState()
    LaunchedEffect(request) {
        val id = request ?: return@LaunchedEffect
        repo.note(id)?.let { n ->
            openBook = n.bookId
            if (n.art.isNotEmpty()) openDrawing(appCtx, n) else openNote = n.id
        }
        openRequest.value = null
    }

    // Kembali dari catatan ke rak: kesempatan untuk cadangan otomatis.
    val appContext = LocalContext.current.applicationContext
    LaunchedEffect(openNote) {
        if (openNote == null) repo.appScope.launch { Backups.maybeAuto(appContext) }
    }

    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        val noteId = openNote
        if (noteId != null && repo.note(noteId)?.let { it.art.isEmpty() } == true) {
            key(noteId) {
                EditorScreen(repo, noteId, isTablet, onBack = { openNote = null })
            }
        } else {
            LibraryScreen(
                repo = repo,
                isTablet = isTablet,
                selectedBookId = openBook,
                onSelectBook = { openBook = it },
                onOpenNote = { n ->
                    openBook = n.bookId
                    if (n.art.isNotEmpty()) openDrawing(appCtx, n) else openNote = n.id
                },
            )
        }
    }
}

/** Gambar di dalam buku dibuka di ruang gambar. */
private fun openDrawing(ctx: android.content.Context, n: id.hananote.data.Note) {
    if (!java.io.File(id.goresan.engine.Storage.dir(ctx, n.art), "doc.json").exists()) {
        android.widget.Toast.makeText(ctx, "Berkas gambar “${n.title}” tidak ditemukan.", android.widget.Toast.LENGTH_LONG).show()
        return
    }
    startDrawing(ctx, open = n.art)
}
