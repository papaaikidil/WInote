package id.goresan.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Redo
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Contrast
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ContentCut
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Flip
import androidx.compose.material.icons.filled.FilterNone
import androidx.compose.material.icons.filled.FitScreen
import androidx.compose.material.icons.filled.FormatColorFill
import androidx.compose.material.icons.filled.HighlightAlt
import androidx.compose.material.icons.filled.InvertColors
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MergeType
import androidx.compose.material.icons.filled.Opacity
import androidx.compose.material.icons.filled.OpenWith
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.Gesture
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.cos
import kotlin.math.sin

/** Satu aksi yang bisa dipasang di QuickMenu. */
class QuickAction(val label: String, val icon: ImageVector, val run: (AppState) -> Unit)

/** Semua aksi QuickMenu (indeks disimpan di Preferensi; urutan jangan diubah, hanya ditambah di belakang). */
val QuickActions: List<QuickAction> = listOf(
    QuickAction("Layer baru", Icons.Filled.Add) { s -> s.layerCmd { it.addLayer() } },
    QuickAction("Duplikat layer", Icons.Filled.FilterNone) { s -> val id = s.activeLayerId; s.layerCmd { it.duplicateLayer(id) } },
    QuickAction("Gabung ke bawah", Icons.Filled.MergeType) { s -> val id = s.activeLayerId; s.layerCmd { it.mergeDown(id) } },
    QuickAction("Bersihkan layer", Icons.Filled.Delete) { s -> val id = s.activeLayerId; s.layerCmd { it.clearLayer(id) } },
    QuickAction("Salin", Icons.Filled.ContentCopy) { s -> s.clipCopy() },
    QuickAction("Tempel", Icons.Filled.ContentPaste) { s -> s.clipPaste() },
    QuickAction("Potong", Icons.Filled.ContentCut) { s -> s.clipCopy(cut = true) },
    QuickAction("Balik warna", Icons.Filled.InvertColors) { s -> val id = s.activeLayerId; s.layerCmd { it.invertLayer(id) } },
    QuickAction("Pilih isi layer", Icons.Filled.SelectAll) { s -> s.selectLayerContents(s.activeLayerId) },
    QuickAction("Alpha Lock", Icons.Filled.Lock) { s ->
        s.layers.firstOrNull { it.id == s.activeLayerId }?.let { l ->
            s.setLayerProps(l.id, s.propsOf(l).copy(alphaLock = !l.alphaLock))
            s.showFlash(if (l.alphaLock) "Alpha Lock mati" else "Alpha Lock aktif")
        }
    },
    QuickAction("Masker layer", Icons.Filled.Contrast) { s -> val id = s.activeLayerId; s.layerCmd { it.addMask(id) } },
    QuickAction("Transformasi", Icons.Filled.OpenWith) { s -> s.startTransform() },
    QuickAction("Pilihan", Icons.Filled.HighlightAlt) { s -> s.toggleSelTool() },
    QuickAction("Balik tampilan", Icons.Filled.Flip) { s -> s.canvasView?.flipView() },
    QuickAction("Pas ke layar", Icons.Filled.FitScreen) { s -> s.canvasView?.fit(true) },
    QuickAction("Urungkan", Icons.AutoMirrored.Filled.Undo) { s -> s.undo() },
    QuickAction("Ulangi", Icons.AutoMirrored.Filled.Redo) { s -> s.redo() },
    QuickAction("Sembunyikan alat", Icons.Filled.Visibility) { s -> s.uiHidden = !s.uiHidden },
    QuickAction("Isi layer", Icons.Filled.FormatColorFill) { s -> val id = s.activeLayerId; val c = s.color; s.layerCmd { it.fillWholeLayer(id, c) } },
    QuickAction("Opasitas layer", Icons.Filled.Opacity) { s -> s.startLayerOpacity(s.activeLayerId) },
    QuickAction("Referensi", Icons.Filled.Gesture) { s ->
        s.layers.firstOrNull { it.id == s.activeLayerId }?.let { l -> s.layerCmd { it.setReference(l.id, !l.reference) } }
    },
)

/** Isi bawaan QuickMenu: layer baru, bersihkan, salin, tempel, balik tampilan, transformasi. */
val DefaultQuickSlots = listOf(0, 3, 4, 5, 13, 11)

/**
 * QuickMenu seperti Procreate: enam tombol melingkar di tengah layar untuk aksi yang sering dipakai.
 * Ketuk tombol = jalankan; tahan tombol = ganti aksinya; ketuk di luar = tutup.
 */
@Composable
fun QuickMenu(state: AppState) {
    var editing by remember { mutableIntStateOf(-1) }
    Box(
        Modifier
            .fillMaxSize()
            .background(Color(0x33000000))
            .pointerInput(Unit) { detectTapGestures { state.quickMenu = null } },
        contentAlignment = Alignment.Center,
    ) {
        Box(Modifier.size(320.dp), contentAlignment = Alignment.Center) {
            Box(
                Modifier.size(320.dp).clip(CircleShape).background(Color(0xE61E1E24)).border(1.dp, Color(0x33FFFFFF), CircleShape)
                    .pointerInput(Unit) { detectTapGestures { } },
            )
            Text("QuickMenu", color = Muted, fontSize = 12.sp)
            val slots = state.quickSlots
            for (k in 0 until 6) {
                val a = QuickActions.getOrNull(slots.getOrElse(k) { DefaultQuickSlots[k] }) ?: QuickActions[0]
                val ang = Math.toRadians(-90.0 + k * 60.0)
                val r = 108f
                Column(
                    Modifier
                        .offset((cos(ang) * r).dp, (sin(ang) * r).dp)
                        .size(92.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF2C2C34))
                        .semantics { contentDescription = "Quick: " + a.label }
                        .pointerInput(k, a) {
                            detectTapGestures(
                                onTap = {
                                    state.quickMenu = null
                                    a.run(state)
                                },
                                onLongPress = { editing = k },
                            )
                        }
                        .padding(8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Icon(a.icon, null, tint = Color.White, modifier = Modifier.size(24.dp))
                    Text(a.label, color = Color.White, fontSize = 11.sp, textAlign = TextAlign.Center, maxLines = 2, lineHeight = 13.sp)
                }
            }
        }
        Text(
            "Ketuk untuk menjalankan · tahan tombol untuk menggantinya",
            color = Color.White, fontSize = 12.sp,
            modifier = Modifier.align(Alignment.Center).offset(y = 180.dp),
        )
    }
    if (editing >= 0) {
        val k = editing
        AlertDialog(
            onDismissRequest = { editing = -1 },
            title = { Text("Ganti tombol QuickMenu") },
            text = {
                LazyColumn(Modifier.heightIn(max = 420.dp).width(320.dp)) {
                    itemsIndexed(QuickActions) { i, a ->
                        val sel = state.quickSlots.getOrNull(k) == i
                        androidx.compose.foundation.layout.Row(
                            Modifier
                                .fillMaxWidth()
                                .clip(androidx.compose.foundation.shape.RoundedCornerShape(10.dp))
                                .background(if (sel) Color(0x333D8BFF) else Color.Transparent)
                                .pointerInput(i) { detectTapGestures { state.setQuickSlot(k, i); editing = -1 } }
                                .padding(horizontal = 10.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(a.icon, null, modifier = Modifier.size(20.dp))
                            Text(a.label, fontSize = 15.sp, fontWeight = if (sel) FontWeight.SemiBold else FontWeight.Normal,
                                modifier = Modifier.padding(start = 12.dp))
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { editing = -1 }) { Text("Tutup") } },
            dismissButton = { TextButton(onClick = { state.resetQuickSlots(); editing = -1 }) { Text("Kembalikan bawaan") } },
        )
    }
}
