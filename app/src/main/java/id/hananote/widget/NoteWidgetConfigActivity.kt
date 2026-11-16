package id.hananote.widget

import android.appwidget.AppWidgetManager
import android.content.Intent
import android.graphics.BitmapFactory
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import id.hananote.data.Note
import id.hananote.data.Repository
import id.hananote.ui.theme.HanaTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Memilih catatan yang ditampilkan widget "Satu catatan" (saat widget dipasang atau diatur ulang). */
class NoteWidgetConfigActivity : ComponentActivity() {
    private var widgetId = AppWidgetManager.INVALID_APPWIDGET_ID

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        widgetId = intent?.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
            ?: AppWidgetManager.INVALID_APPWIDGET_ID
        // Ditutup tanpa memilih: widget tidak jadi dipasang.
        setResult(RESULT_CANCELED, Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId))
        if (widgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            finish()
            return
        }
        enableEdgeToEdge()
        val repo = Repository.get(applicationContext)
        setContent {
            val theme by repo.theme.collectAsState()
            HanaTheme(theme) {
                PickNote(repo, onPick = { choose(it) }, onCancel = { finish() })
            }
        }
    }

    private fun choose(note: Note) {
        HanaWidgets.setNoteFor(this, widgetId, note.id)
        HanaWidgets.update(this, AppWidgetManager.getInstance(this), widgetId, recent = false)
        setResult(RESULT_OK, Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId))
        finish()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PickNote(repo: Repository, onPick: (Note) -> Unit, onCancel: () -> Unit) {
    val allNotes by repo.notes.collectAsState()
    // Widget catatan menampilkan halaman: gambar dari ruang gambar tidak ikut.
    val notes = allNotes.filter { it.art.isEmpty() }
    val books by repo.books.collectAsState()
    val bookNames = books.associate { it.id to it.title }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Pilih catatan untuk widget") },
                navigationIcon = {
                    IconButton(onClick = onCancel) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Batal") }
                },
            )
        },
    ) { pad ->
        if (notes.isEmpty()) {
            Box(Modifier.padding(pad).fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("Belum ada catatan. Buat catatan dulu di WInote.", style = MaterialTheme.typography.bodyLarge)
            }
            return@Scaffold
        }
        LazyVerticalGrid(
            columns = GridCells.Adaptive(150.dp),
            contentPadding = PaddingValues(16.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
            modifier = Modifier.padding(pad).fillMaxSize(),
        ) {
            items(notes.sortedByDescending { it.updated }, key = { it.id }) { n ->
                Column(
                    Modifier.clip(RoundedCornerShape(12.dp)).clickable { onPick(n) }.padding(4.dp),
                ) {
                    NoteThumb(repo, n, Modifier.fillMaxWidth().aspectRatio(0.72f))
                    Text(
                        n.title,
                        style = MaterialTheme.typography.titleSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 6.dp),
                    )
                    Text(
                        bookNames[n.bookId] ?: "",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

@Composable
private fun NoteThumb(repo: Repository, note: Note, modifier: Modifier) {
    val bmp by produceState<ImageBitmap?>(null, note.id) {
        value = withContext(Dispatchers.IO) {
            runCatching {
                val f = repo.thumbFile(note.id)
                if (f.exists()) BitmapFactory.decodeFile(f.path, BitmapFactory.Options().apply { inSampleSize = 1 })?.asImageBitmap() else null
            }.getOrNull()
        }
    }
    Box(
        modifier
            .clip(RoundedCornerShape(10.dp))
            .background(Color.White)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(10.dp)),
    ) {
        bmp?.let { Image(it, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize()) }
    }
}
