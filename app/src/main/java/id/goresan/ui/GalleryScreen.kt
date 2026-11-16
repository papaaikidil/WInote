package id.goresan.ui

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.IconButton
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import id.goresan.engine.Storage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun GalleryScreen(state: AppState) {
    var arts by remember { mutableStateOf<List<Storage.ArtInfo>>(emptyList()) }
    var loaded by remember { mutableStateOf(false) }
    LaunchedEffect(state.galleryVersion) {
        arts = withContext(Dispatchers.IO) { Storage.list(state.app) }
        loaded = true
    }
    // Dari tombol + di beranda WInote: pilihan kanvas baru langsung tampil.
    var showNew by remember { mutableStateOf(state.askNew) }
    var menuFor by remember { mutableStateOf<Storage.ArtInfo?>(null) }
    var renameFor by remember { mutableStateOf<Storage.ArtInfo?>(null) }
    var deleteFor by remember { mutableStateOf<Storage.ArtInfo?>(null) }
    val scope = rememberCoroutineScope()

    fun refresh() { state.galleryVersion++ }

    Column(Modifier.fillMaxSize().background(Color(0xFF121214)).windowInsetsPadding(WindowInsets.statusBars)) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 28.dp, vertical = 18.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = { state.onExit?.invoke() }) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Kembali ke WInote", tint = Color.White)
            }
            Spacer(Modifier.width(6.dp))
            Text("Gambar", color = Color.White, fontSize = 30.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Serif)
            Spacer(Modifier.width(14.dp))
            Text("${arts.size} karya", color = Muted, fontSize = 14.sp)
            Spacer(Modifier.weight(1f))
            TextButton(onClick = { state.showReport = true }) { Text("Laporan teknis", color = Muted) }
            Spacer(Modifier.width(8.dp))
            Button(
                onClick = { showNew = true },
                colors = ButtonDefaults.buttonColors(containerColor = Accent, contentColor = Color(0xFF00213F)),
            ) {
                Icon(Icons.Filled.Add, null)
                Spacer(Modifier.width(6.dp))
                Text("Kanvas baru", fontWeight = FontWeight.SemiBold)
            }
        }
        if (loaded && arts.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Belum ada karya", color = Color.White, fontSize = 20.sp)
                    Spacer(Modifier.height(6.dp))
                    Text("Ketuk \"Kanvas baru\" untuk mulai menggambar", color = Muted)
                }
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(230.dp),
                contentPadding = PaddingValues(horizontal = 24.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(20.dp),
                verticalArrangement = Arrangement.spacedBy(22.dp),
                modifier = Modifier.fillMaxSize(),
            ) {
                items(arts, key = { it.id }) { art ->
                    ArtCard(art, onOpen = { state.openArtwork(art.id) }, onMenu = { menuFor = art })
                }
            }
        }
    }

    state.flash?.let { msg ->
        val id = state.flashId
        LaunchedEffect(id) {
            delay(2000)
            if (state.flashId == id) state.flash = null
        }
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.BottomCenter) {
            Text(
                msg, color = Color.White,
                modifier = Modifier.padding(30.dp).clip(RoundedCornerShape(14.dp)).background(PanelBg).padding(horizontal = 16.dp, vertical = 10.dp),
            )
        }
    }

    if (showNew) NewCanvasScreen(
        state,
        onDismiss = {
            showNew = false
            state.askNew = false
            // Batal membuat kanvas yang diminta dari beranda: kembali ke beranda.
            if (state.exitOnClose) state.onExit?.invoke()
        },
        onCreated = {
            showNew = false
            state.askNew = false
        },
    )
    ReportDialog(state)

    menuFor?.let { art ->
        AlertDialog(
            onDismissRequest = { menuFor = null },
            title = { Text(art.name) },
            text = {
                Column {
                    Text("${art.w} × ${art.h} px", color = Muted)
                    Spacer(Modifier.height(10.dp))
                    MenuLine("Buka") { menuFor = null; state.openArtwork(art.id) }
                    MenuLine("Ganti nama") { menuFor = null; renameFor = art }
                    MenuLine("Duplikat") {
                        menuFor = null
                        scope.launch { withContext(Dispatchers.IO) { Storage.duplicate(state.app, art.id) }; refresh() }
                    }
                    MenuLine("Hapus", danger = true) { menuFor = null; deleteFor = art }
                }
            },
            confirmButton = { TextButton(onClick = { menuFor = null }) { Text("Tutup") } },
        )
    }

    renameFor?.let { art ->
        var name by remember(art.id) { mutableStateOf(art.name) }
        AlertDialog(
            onDismissRequest = { renameFor = null },
            title = { Text("Ganti nama karya") },
            text = { OutlinedTextField(value = name, onValueChange = { name = it }, singleLine = true) },
            confirmButton = {
                TextButton(onClick = {
                    val n = name.ifBlank { art.name }
                    renameFor = null
                    scope.launch { withContext(Dispatchers.IO) { Storage.rename(state.app, art.id, n) }; refresh() }
                }) { Text("Simpan") }
            },
            dismissButton = { TextButton(onClick = { renameFor = null }) { Text("Batal") } },
        )
    }

    deleteFor?.let { art ->
        AlertDialog(
            onDismissRequest = { deleteFor = null },
            title = { Text("Hapus \"${art.name}\"?") },
            text = { Text("Karya ini akan dihapus permanen dari tablet dan tidak bisa dikembalikan.") },
            confirmButton = {
                TextButton(onClick = {
                    deleteFor = null
                    scope.launch { withContext(Dispatchers.IO) { Storage.delete(state.app, art.id) }; refresh() }
                }) { Text("Hapus", color = Color(0xFFFF8A80)) }
            },
            dismissButton = { TextButton(onClick = { deleteFor = null }) { Text("Batal") } },
        )
    }
}

@Composable
private fun MenuLine(text: String, danger: Boolean = false, onClick: () -> Unit) {
    Text(
        text,
        color = if (danger) Color(0xFFFF8A80) else Color.White,
        fontSize = 16.sp,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .clickable { onClick() }
            .padding(vertical = 12.dp, horizontal = 4.dp),
    )
}

@Composable
private fun ArtCard(art: Storage.ArtInfo, onOpen: () -> Unit, onMenu: () -> Unit) {
    val img by produceState<ImageBitmap?>(null, art.thumb.path, art.modified) {
        value = withContext(Dispatchers.IO) {
            runCatching { BitmapFactory.decodeFile(art.thumb.path)?.asImageBitmap() }.getOrNull()
        }
    }
    val ratio = if (art.h > 0) (art.w.toFloat() / art.h).coerceIn(0.6f, 1.8f) else 1f
    Column(
        Modifier.pointerInput(art.id) {
            detectTapGestures(onTap = { onOpen() }, onLongPress = { onMenu() })
        },
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(1.25f)
                .clip(RoundedCornerShape(10.dp))
                .background(Color(0xFF1C1C20)),
            contentAlignment = Alignment.Center,
        ) {
            Box(
                Modifier
                    .padding(10.dp)
                    .aspectRatio(ratio)
                    .clip(RoundedCornerShape(4.dp))
                    .background(Color.White)
                    .border(1.dp, Color(0x33FFFFFF), RoundedCornerShape(4.dp)),
            ) {
                img?.let { Image(it, contentDescription = art.name, contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize()) }
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(art.name, color = Color.White, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(
            "${art.w} × ${art.h} · " + SimpleDateFormat("d MMM yyyy", Locale("id", "ID")).format(Date(art.modified)),
            color = Muted, fontSize = 12.sp,
        )
    }
}

