package id.goresan.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import id.goresan.engine.AngleMode
import id.goresan.engine.Brush
import id.goresan.engine.Grain
import id.goresan.engine.TipShape
import id.goresan.engine.ToolKind
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import id.goresan.engine.buildCurve
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.ln
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.roundToInt

private fun toolLabel(t: Int) = when (t) {
    ToolKind.SMUDGE -> "Kuas baur"
    ToolKind.ERASE -> "Kuas penghapus"
    else -> "Kuas"
}

@Composable
fun BrushPanel(state: AppState, modifier: Modifier) {
    Surface(
        color = PanelBg,
        shape = RoundedCornerShape(18.dp),
        shadowElevation = 8.dp,
        modifier = modifier.width(560.dp).heightIn(max = 640.dp),
    ) {
        val studio = state.studioBrushId
        if (studio != null) {
            BrushStudio(state, state.brushes.get(studio))
        } else {
            BrushLibrary(state)
        }
    }
}

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
private fun BrushLibrary(state: AppState) {
    val current = state.brushIdFor(state.tool)
    val cats = state.brushes.categories()
    var cat by remember { mutableStateOf(state.brushes.get(current).category) }
    if (cat !in cats) cat = cats.first()
    var catMenu by remember { mutableStateOf<String?>(null) }
    var brushMenu by remember { mutableStateOf<String?>(null) }
    var deleteCat by remember { mutableStateOf<String?>(null) }
    var deleteBrush by remember { mutableStateOf<String?>(null) }
    var renameCat by remember { mutableStateOf<String?>(null) }
    var query by remember { mutableStateOf("") }
    var newSet by remember { mutableStateOf(false) }
    var moveBrush by remember { mutableStateOf<Pair<String, Boolean>?>(null) }
    Column(Modifier.padding(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(toolLabel(state.tool), color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 17.sp)
            Spacer(Modifier.weight(1f))
            TextButton(onClick = { state.pickBrushFile?.invoke() }) { Text("Impor kuas") }
            TextButton(onClick = {
                val nb = state.brushes.duplicate(state.brushes.get(current))
                state.selectBrush(nb.id)
                state.studioBrushId = nb.id
            }) { Text("Duplikat kuas ini") }
        }
        Text(
            "Ketuk kuas yang sedang dipilih untuk membuka pengaturannya. Impor: .brush/.brushset Procreate, .abr Photoshop (bentuk, tekstur, dinamika), pola .pat, gambar PNG/JPG, atau daftar .wkuas WInote.",
            color = Muted, fontSize = 12.sp,
        )
        Spacer(Modifier.height(6.dp))
        // Cari kuas di semua daftar (seperti Brush Search Procreate).
        val focus = androidx.compose.ui.platform.LocalFocusManager.current
        OutlinedTextField(
            value = query,
            onValueChange = { query = it.take(40) },
            singleLine = true,
            placeholder = { Text("Cari kuas atau daftar…", fontSize = 13.sp) },
            trailingIcon = {
                if (query.isNotEmpty()) TextButton(onClick = { query = ""; focus.clearFocus() }) { Text("Hapus") }
            },
            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(imeAction = androidx.compose.ui.text.input.ImeAction.Search),
            keyboardActions = androidx.compose.foundation.text.KeyboardActions(onSearch = { focus.clearFocus() }),
            textStyle = androidx.compose.ui.text.TextStyle(fontSize = 14.sp, color = Color.White),
            modifier = Modifier.fillMaxWidth().height(52.dp),
        )
        Spacer(Modifier.height(6.dp))
        Row(Modifier.heightIn(max = 560.dp)) {
            Column(
                Modifier
                    .width(140.dp)
                    .fillMaxHeight()
                    .verticalScroll(rememberScrollState()),
            ) {
                for ((idx, c) in cats.withIndex()) {
                    val sel = c == cat && query.isBlank()
                    val custom = state.brushes.isCustomCategory(c)
                    // Perpustakaan: daftar bawaan lalu daftar buatan sendiri/impor.
                    if (idx == 0) LibHeader("Bawaan")
                    if (custom && (idx == 0 || !state.brushes.isCustomCategory(cats[idx - 1]))) LibHeader("Kuasku & impor")
                    Box {
                        Text(
                            c,
                            color = if (sel) Color.White else Color(0xFFB8B8C0),
                            fontWeight = if (sel) FontWeight.SemiBold else FontWeight.Normal,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .background(if (sel) Color(0x333D8BFF) else Color.Transparent)
                                .combinedClickable(
                                    onClick = { cat = c; query = "" },
                                    onLongClick = { cat = c; query = ""; catMenu = c },
                                )
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                        )
                        DropdownMenu(expanded = catMenu == c, onDismissRequest = { catMenu = null }) {
                            DropdownMenuItem(text = { Text("Ekspor daftar (.wkuas)") }, onClick = { catMenu = null; state.exportBrushSet(c) })
                            if (custom) {
                                DropdownMenuItem(text = { Text("Ganti nama daftar") }, onClick = { catMenu = null; renameCat = c })
                                DropdownMenuItem(
                                    text = { Text("Hapus daftar", color = Color(0xFFFF8A80)) },
                                    onClick = { catMenu = null; deleteCat = c },
                                )
                            }
                        }
                    }
                }
                Text(
                    "+ Daftar baru",
                    color = Accent,
                    fontSize = 13.sp,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .clickable { newSet = true }
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                )
            }
            Spacer(Modifier.width(8.dp))
            val searching = query.isNotBlank()
            val list = if (searching) state.brushes.search(query) else state.brushes.list.filter { it.category == cat }
            Column(Modifier.weight(1f).fillMaxHeight()) {
            if (searching) {
                Text(
                    if (list.isEmpty()) "Tidak ada kuas yang cocok" else "${list.size} kuas cocok",
                    color = Muted, fontSize = 12.sp, modifier = Modifier.padding(start = 4.dp, bottom = 4.dp),
                )
            } else if (state.brushes.isCustomCategory(cat)) {
                // Daftar buatan sendiri/impor: bisa diganti nama atau dihapus sekaligus.
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    Text("${list.size} kuas", color = Muted, fontSize = 12.sp, modifier = Modifier.weight(1f).padding(start = 4.dp))
                    TextButton(onClick = { renameCat = cat }) { Text("Ganti nama", fontSize = 13.sp) }
                    TextButton(onClick = { deleteCat = cat }) { Text("Hapus daftar", color = Color(0xFFFF8A80), fontSize = 13.sp) }
                }
            }
            LazyColumn(Modifier.weight(1f).fillMaxWidth()) {
                items(list, key = { it.id }) { b ->
                    LaunchedEffect(b.id, state.previews.containsKey(b.id)) { state.requestPreview(b) }
                    val sel = b.id == current
                    Box {
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .padding(vertical = 3.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(if (sel) Color(0x553D8BFF) else Color(0x14FFFFFF))
                            .combinedClickable(
                                onClick = { if (sel) state.studioBrushId = b.id else state.selectBrush(b.id) },
                                onLongClick = { brushMenu = b.id },
                            )
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(b.name, color = Color.White, fontSize = 14.sp, fontWeight = if (sel) FontWeight.SemiBold else FontWeight.Normal)
                            if (searching) Text("  · ${b.category}", color = Muted, fontSize = 12.sp)
                            if (state.brushes.isModified(b)) {
                                Text("  · diubah", color = Muted, fontSize = 12.sp)
                            }
                        }
                        val img = state.previews[b.id]
                        Box(Modifier.fillMaxWidth().height(52.dp)) {
                            if (img != null) {
                                Image(img, contentDescription = b.name, contentScale = ContentScale.Fit, modifier = Modifier.fillMaxWidth().height(52.dp))
                            }
                        }
                    }
                    DropdownMenu(expanded = brushMenu == b.id, onDismissRequest = { brushMenu = null }) {
                        DropdownMenuItem(text = { Text("Pengaturan kuas") }, onClick = {
                            brushMenu = null
                            state.selectBrush(b.id)
                            state.studioBrushId = b.id
                        })
                        DropdownMenuItem(text = { Text("Duplikat") }, onClick = {
                            brushMenu = null
                            val nb = state.brushes.duplicate(b)
                            state.selectBrush(nb.id)
                        })
                        if (!b.builtIn) {
                            DropdownMenuItem(text = { Text("Pindahkan ke daftar…") }, onClick = { brushMenu = null; moveBrush = b.id to true })
                        }
                        DropdownMenuItem(text = { Text("Salin ke daftar…") }, onClick = { brushMenu = null; moveBrush = b.id to false })
                        if (b.builtIn) {
                            if (state.brushes.isModified(b)) {
                                DropdownMenuItem(text = { Text("Kembalikan bawaan") }, onClick = {
                                    brushMenu = null
                                    state.brushes.reset(b.id)
                                    state.previews.remove(b.id)
                                    state.requestPreview(state.brushes.get(b.id))
                                    state.syncTool()
                                })
                            }
                        } else {
                            DropdownMenuItem(
                                text = { Text("Hapus kuas", color = Color(0xFFFF8A80)) },
                                onClick = { brushMenu = null; deleteBrush = b.id },
                            )
                        }
                    }
                    }
                }
            }
            }
        }
    }

    if (newSet) {
        var t by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { newSet = false },
            title = { Text("Daftar kuas baru") },
            text = {
                DialogImmersive()
                OutlinedTextField(value = t, onValueChange = { t = it.take(40) }, singleLine = true, label = { Text("Nama daftar") })
            },
            confirmButton = {
                TextButton(onClick = { cat = state.createBrushSet(t); query = ""; newSet = false }) { Text("Buat") }
            },
            dismissButton = { TextButton(onClick = { newSet = false }) { Text("Batal") } },
        )
    }
    moveBrush?.let { (id, move) ->
        val b = state.brushes.list.firstOrNull { it.id == id }
        val sets = state.brushes.customSets.filter { it != b?.category }
        AlertDialog(
            onDismissRequest = { moveBrush = null },
            title = { Text(if (move) "Pindahkan ke daftar" else "Salin ke daftar") },
            text = {
                DialogImmersive()
                Column(Modifier.heightIn(max = 360.dp).verticalScroll(rememberScrollState())) {
                    if (sets.isEmpty()) Text("Belum ada daftar buatan sendiri. Buat dulu lewat \"+ Daftar baru\".", color = Muted, fontSize = 13.sp)
                    for (c in sets) {
                        Text(
                            c,
                            fontSize = 15.sp,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .clickable {
                                    if (b != null) {
                                        if (move) state.moveBrushTo(b.id, c) else state.copyBrushTo(b, c)
                                        cat = c
                                    }
                                    moveBrush = null
                                }
                                .padding(horizontal = 8.dp, vertical = 12.dp),
                        )
                    }
                }
            },
            confirmButton = {},
            dismissButton = { TextButton(onClick = { moveBrush = null }) { Text("Batal") } },
        )
    }
    deleteCat?.let { c ->
        val n = state.brushes.countIn(c)
        AlertDialog(
            onDismissRequest = { deleteCat = null },
            title = { Text("Hapus daftar \"$c\"?") },
            text = {
                DialogImmersive()
                Text("$n kuas di daftar ini ikut terhapus dan tidak bisa dikembalikan. Gambar yang sudah dibuat tidak berubah.")
            },
            confirmButton = {
                TextButton(onClick = { state.deleteBrushCategory(c); deleteCat = null }) { Text("Hapus", color = Color(0xFFFF8A80)) }
            },
            dismissButton = { TextButton(onClick = { deleteCat = null }) { Text("Batal") } },
        )
    }
    deleteBrush?.let { id ->
        val name = state.brushes.list.firstOrNull { it.id == id }?.name ?: "kuas ini"
        AlertDialog(
            onDismissRequest = { deleteBrush = null },
            title = { Text("Hapus kuas \"$name\"?") },
            text = {
                DialogImmersive()
                Text("Kuas ini tidak bisa dikembalikan. Gambar yang sudah dibuat tidak berubah.")
            },
            confirmButton = {
                TextButton(onClick = { state.deleteBrush(id); deleteBrush = null }) { Text("Hapus", color = Color(0xFFFF8A80)) }
            },
            dismissButton = { TextButton(onClick = { deleteBrush = null }) { Text("Batal") } },
        )
    }
    renameCat?.let { c ->
        var t by remember(c) { mutableStateOf(c) }
        AlertDialog(
            onDismissRequest = { renameCat = null },
            title = { Text("Ganti nama daftar") },
            text = {
                DialogImmersive()
                OutlinedTextField(value = t, onValueChange = { t = it.take(40) }, singleLine = true, label = { Text("Nama daftar") })
            },
            confirmButton = {
                TextButton(enabled = t.isNotBlank(), onClick = {
                    state.renameBrushCategory(c, t)
                    cat = t.trim().take(40)
                    renameCat = null
                }) { Text("Simpan") }
            },
            dismissButton = { TextButton(onClick = { renameCat = null }) { Text("Batal") } },
        )
    }
}

@Composable
private fun LibHeader(title: String) {
    Text(
        title.uppercase(),
        color = Muted,
        fontSize = 10.sp,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(start = 12.dp, top = 10.dp, bottom = 2.dp),
    )
}

@Composable
private fun <T> Choice(label: String, value: T, options: List<T>, name: (T) -> String, onPick: (T) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, color = Color(0xFFDADAE0), fontSize = 13.sp, modifier = Modifier.weight(1f))
        Box {
            Text(
                name(value),
                color = Accent,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .clickable { open = true }
                    .background(Color(0x22FFFFFF))
                    .padding(horizontal = 10.dp, vertical = 6.dp),
            )
            DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
                for (o in options) {
                    DropdownMenuItem(text = { Text(name(o)) }, onClick = { onPick(o); open = false })
                }
            }
        }
    }
}

@Composable
private fun Section(title: String) {
    Spacer(Modifier.height(10.dp))
    Text(title, color = Accent, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
    HorizontalDivider(color = Color(0x22FFFFFF), modifier = Modifier.padding(vertical = 4.dp))
}

/** Subjudul kecil di dalam bagian Studio (mis. StreamLine / Stabilisasi / Penyaring gerak). */
@Composable
private fun SubLabel(title: String) {
    Text(title, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Medium, modifier = Modifier.padding(top = 6.dp))
}

/** Mode stempel: satu tarikan = satu cap utuh sepanjang tarikan (untuk kuas gambar seperti garis bawah). */
@Composable
private fun StampRow(on: Boolean, onChange: (Boolean) -> Unit) =
    SwitchRow("Stempel", "Satu tarikan = satu cap utuh sepanjang tarikan; ketuk = satu cap seukuran kuas", on, onChange)

@Composable
private fun SwitchRow(title: String, hint: String, on: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable { onChange(!on) }
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, color = Color.White, fontSize = 14.sp)
            Text(hint, color = Muted, fontSize = 11.sp)
        }
        androidx.compose.material3.Switch(
            checked = on, onCheckedChange = onChange,
            colors = androidx.compose.material3.SwitchDefaults.colors(checkedTrackColor = Accent),
        )
    }
}

/** Bagian Studio Kuas (seperti tab di kiri Brush Studio Procreate). */
private val studioTabs = listOf(
    "Jalur goresan", "Stabilisasi", "Runcing", "Bentuk", "Tekstur", "Rendering", "Wet Mix", "Cat air",
    "Dinamika", "Dinamika warna", "Pena", "Properti", "Kuas ganda",
)

/** Mode rendering seperti Procreate (indeks = Brush.render). */
private val RenderNames = listOf("Glaze ringan", "Glaze merata", "Glaze intens", "Glaze berat", "Blending merata", "Blending intens")

/** Cara menggabung kuas ganda (indeks = Brush.dualMode). */
private val DualNames = listOf("Irisan (kali)", "Gabung (tambah)", "Kurangi", "Selisih", "Gelapkan", "Terangkan")

@Composable
private fun BrushStudio(state: AppState, b: Brush) {
    fun up(nb: Brush) = state.updateBrush(nb)
    val pct: (Float) -> String = { "${(it * 100).roundToInt()}%" }
    // Seperti Procreate: 0% ditulis "Tidak ada".
    val pctNone: (Float) -> String = { if (abs(it) < 0.005f) "Tidak ada" else "${(it * 100).roundToInt()}%" }
    val pctSigned: (Float) -> String = { if (abs(it) < 0.005f) "Tidak ada" else "${if (it > 0) "+" else ""}${(it * 100).roundToInt()}%" }
    var tab by remember { mutableStateOf(0) }
    Column(Modifier.padding(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { state.studioBrushId = null }) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, "Kembali", tint = Color.White)
            }
            Text("Studio Kuas", color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 17.sp)
            Spacer(Modifier.weight(1f))
            if (b.builtIn) {
                TextButton(onClick = { state.brushes.reset(b.id); state.previews.remove(b.id); state.requestPreview(state.brushes.get(b.id)); state.syncTool() }) {
                    Text("Kembalikan bawaan")
                }
            } else {
                TextButton(onClick = { state.deleteBrush(b.id) }) { Text("Hapus kuas", color = Color(0xFFFF8A80)) }
            }
        }
        val img = state.previews[b.id]
        Box(
            Modifier
                .fillMaxWidth()
                .height(70.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(Color(0xFF111114)),
            contentAlignment = Alignment.Center,
        ) {
            if (img != null) Image(img, contentDescription = null, contentScale = ContentScale.Fit, modifier = Modifier.fillMaxWidth().height(70.dp))
        }
        Spacer(Modifier.height(6.dp))
        Row(Modifier.heightIn(max = 470.dp)) {
            Column(
                Modifier
                    .width(132.dp)
                    .fillMaxHeight()
                    .verticalScroll(rememberScrollState()),
            ) {
                studioTabs.forEachIndexed { i, t ->
                    val sel = i == tab
                    Text(
                        t,
                        color = if (sel) Color.White else Color(0xFFB8B8C0),
                        fontWeight = if (sel) FontWeight.SemiBold else FontWeight.Normal,
                        fontSize = 13.sp,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(if (sel) Color(0x333D8BFF) else Color.Transparent)
                            .clickable { tab = i }
                            .padding(horizontal = 10.dp, vertical = 9.dp),
                    )
                }
            }
            Spacer(Modifier.width(8.dp))
            Column(
                Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .verticalScroll(rememberScrollState()),
            ) {
                when (studioTabs[tab]) {
                    "Jalur goresan" -> {
                        StampRow(b.stamp) { up(b.copy(stamp = it)) }
                        // 1%..1000% berskala logaritmik: 1–10%, 10–100%, dan 100–1000% masing-masing sepertiga geseran,
                        // jadi jarak rapat tetap mudah diatur halus sementara cap bisa direnggangkan sampai 10× diameter.
                        LabeledSlider(
                            "Jarak antar cap",
                            (ln(b.spacing.coerceIn(SPACING_MIN, SPACING_MAX) / SPACING_MIN) / ln(SPACING_MAX / SPACING_MIN)).coerceIn(0f, 1f),
                            { up(b.copy(spacing = spacingAtSlider(it))) },
                            0f..1f,
                            { pct(spacingAtSlider(it)) },
                        )
                        LabeledSlider("Getaran jarak", b.spacingJitter, { up(b.copy(spacingJitter = it)) }, display = pctNone)
                        LabeledSlider("Getaran lateral", b.jitterLateral, { up(b.copy(jitterLateral = it)) }, display = pctNone)
                        LabeledSlider("Getaran linear", b.jitterLinear, { up(b.copy(jitterLinear = it)) }, display = pctNone)
                        LabeledSlider("Memudar (fall off)", b.fallOff, { up(b.copy(fallOff = it)) }, display = pctNone)
                        Hint("Getaran lateral menggeser cap tegak lurus arah goresan, linear searah goresan. Memudar: goresan mulai pekat lalu memudar sepanjang tarikan.")
                    }
                    "Stabilisasi" -> {
                        // Sama seperti bagian Stabilization di Brush Studio Procreate.
                        SubLabel("StreamLine")
                        LabeledSlider("Jumlah", b.streamline, { up(b.copy(streamline = it)) }, display = pctNone)
                        LabeledSlider("Tekanan", b.streamlinePressure, { up(b.copy(streamlinePressure = it)) }, display = pctNone)
                        SubLabel("Stabilisasi")
                        LabeledSlider("Jumlah", b.stabilization, { up(b.copy(stabilization = it)) }, display = pctNone)
                        SubLabel("Penyaring gerak")
                        LabeledSlider("Jumlah", b.motionFilter, { up(b.copy(motionFilter = it)) }, display = pctNone)
                        if (b.motionFilter > 0.001f) {
                            LabeledSlider("Ekspresi", b.motionExpression, { up(b.copy(motionExpression = it)) }, display = pctNone)
                        }
                    }
                    "Runcing" -> {
                        LabeledSlider("Runcing di awal", b.taperStart, { up(b.copy(taperStart = it)) }, display = pctNone)
                        LabeledSlider("Runcing di akhir", b.taperEnd, { up(b.copy(taperEnd = it)) }, display = pctNone)
                        LabeledSlider("Opasitas runcing", b.taperOpacity, { up(b.copy(taperOpacity = it)) }, display = pctNone)
                        LabeledSlider("Ujung", b.taperTip, { up(b.copy(taperTip = it)) }, display = { if (it < 0.3f) "Lancip ${(it * 100).roundToInt()}%" else "Tumpul ${(it * 100).roundToInt()}%" })
                        Hint("Berlaku juga untuk jari (tanpa tekanan). Ujung kecil = lancip halus seperti pena runcing, besar = ujung tumpul.")
                    }
                    "Bentuk" -> {
                        Choice("Ujung kuas", b.tip, TipShape.entries.toList(), { it.label }) { up(b.copy(tip = it)) }
                        Choice("Arah ujung", b.angleMode, AngleMode.entries.toList(), { it.label }) { up(b.copy(angleMode = it)) }
                        LabeledSlider("Sudut", b.angle, { up(b.copy(angle = it)) }, 0f..180f, { "${it.roundToInt()}°" })
                        LabeledSlider("Variasi sudut", b.angleJitter, { up(b.copy(angleJitter = it)) }, display = pctNone)
                        LabeledSlider("Sebaran", b.scatter, { up(b.copy(scatter = it)) }, display = pctNone)
                        LabeledSlider("Jumlah cap", b.count.toFloat(), { up(b.copy(count = it.roundToInt().coerceIn(1, 16))) }, 1f..16f, { "${it.roundToInt()}" })
                        if (b.count > 1) LabeledSlider("Getaran jumlah", b.countJitter, { up(b.copy(countJitter = it)) }, display = pctNone)
                        SwitchRow("Putaran acak", "Sudut awal acak di tiap goresan", b.randomized) { up(b.copy(randomized = it)) }
                        SwitchRow("Balik X", "Cerminkan bentuk mendatar", b.flipX) { up(b.copy(flipX = it)) }
                        SwitchRow("Balik Y", "Cerminkan bentuk tegak", b.flipY) { up(b.copy(flipY = it)) }
                        SubLabel("Kebulatan")
                        LabeledSlider("Kebulatan", b.roundness, { up(b.copy(roundness = it)) }, 0.05f..1f)
                        LabeledSlider("Ikut tekanan", b.pressureRound, { up(b.copy(pressureRound = it)) }, display = pctNone)
                        LabeledSlider("Ikut kemiringan", b.tiltRound, { up(b.copy(tiltRound = it)) }, display = pctNone)
                        LabeledSlider("Getaran tegak", b.roundJitterV, { up(b.copy(roundJitterV = it)) }, display = pctNone)
                        LabeledSlider("Getaran mendatar", b.roundJitterH, { up(b.copy(roundJitterH = it)) }, display = pctNone)
                    }
                    "Tekstur" -> {
                        if (b.grainFile.isNotEmpty()) {
                            // Tekstur pola dari berkas impor (grain Procreate / pola Photoshop); kekuatan 0 = tanpa tekstur.
                            Text("Tekstur: pola dari berkas impor", color = Color(0xFFBDBDC6), fontSize = 13.sp, modifier = Modifier.padding(vertical = 6.dp))
                        } else {
                            Choice("Tekstur", b.grain, Grain.entries.toList(), { it.label }) { up(b.copy(grain = it)) }
                        }
                        if (b.grain != Grain.NONE || b.grainFile.isNotEmpty()) {
                            LabeledSlider("Kekuatan tekstur", b.grainStrength, { up(b.copy(grainStrength = it)) })
                            val lo = if (b.grainFile.isNotEmpty()) 0.05f else 0.2f
                            LabeledSlider("Skala tekstur", b.grainScale.coerceIn(lo, 3f), { up(b.copy(grainScale = it)) }, lo..3f, { "${(it * 100).roundToInt()}%" })
                            SwitchRow(
                                "Tekstur menyatu di goresan",
                                "Tekstur dikenakan pada seluruh goresan, tidak tertutup walau cap menumpuk (seperti pola). Mati = tekstur per cap.",
                                b.grainStroke,
                            ) { up(b.copy(grainStroke = it)) }
                        }
                    }
                    "Rendering" -> {
                        SubLabel("Mode rendering")
                        Column {
                            RenderNames.forEachIndexed { i, n ->
                                val sel = b.render == i
                                Text(
                                    n,
                                    color = if (sel) Color.White else Color(0xFFB8B8C0),
                                    fontSize = 13.sp,
                                    fontWeight = if (sel) FontWeight.SemiBold else FontWeight.Normal,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(if (sel) Color(0x333D8BFF) else Color.Transparent)
                                        .clickable { up(b.copy(render = i)) }
                                        .padding(horizontal = 10.dp, vertical = 7.dp),
                                )
                            }
                        }
                        Hint(
                            when (b.render) {
                                0 -> "Cap menumpuk lembut; opasitas goresan tidak melewati opasitas kuas."
                                1 -> "Goresan rata: cap tidak menumpuk di dalam satu goresan."
                                2 -> "Cap lebih pekat, tepi tetap halus."
                                3 -> "Paling pekat, seperti cat tebal."
                                4 -> "Cat bercampur merata dengan warna di bawahnya (langsung di layer)."
                                else -> "Cat bercampur dengan warna di bawahnya, lebih pekat."
                            },
                        )
                        SubLabel("Lainnya")
                        LabeledSlider("Aliran (flow)", b.flow, { up(b.copy(flow = it)) }, 0.01f..1f, pct)
                        LabeledSlider("Tepi basah", b.wetEdges, { up(b.copy(wetEdges = it)) }, display = pctNone)
                        LabeledSlider("Tepi terbakar", b.burntEdges, { up(b.copy(burntEdges = it)) }, display = pctNone)
                        if (b.burntEdges > 0f) Choice("Mode tepi terbakar", b.burntMode, BlendNames.indices.toList(), { BlendNames[it] }) { up(b.copy(burntMode = it)) }
                        Choice("Mode campur", b.blendMode, BlendNames.indices.toList(), { BlendNames[it] }) { up(b.copy(blendMode = it)) }
                        SwitchRow("Campuran luminans", "Goresan hanya mengubah terang-gelap cat di bawahnya", b.lumBlend) { up(b.copy(lumBlend = it)) }
                        LabeledSlider("Ambang alfa", b.alphaThreshold, { up(b.copy(alphaThreshold = it)) }, display = pctNone)
                        Hint("Tepi basah melunakkan tepi goresan; tepi terbakar menggelapkan bagian yang menimpa cat lain. Ambang alfa membuat tepi tegas tanpa semi-transparan.")
                    }
                    "Wet Mix" -> {
                        LabeledSlider("Pengenceran", b.wmDilution, { up(b.copy(wmDilution = it)) }, display = pctNone)
                        LabeledSlider("Muatan cat", b.wmCharge, { up(b.copy(wmCharge = it)) }, display = pctNone)
                        LabeledSlider("Daya tempel (attack)", b.wmAttack, { up(b.copy(wmAttack = it)) }, display = pctNone)
                        LabeledSlider("Tarikan (pull)", b.wmPull, { up(b.copy(wmPull = it)) }, display = pctNone)
                        LabeledSlider("Gradasi tekstur", b.wmGrade, { up(b.copy(wmGrade = it)) }, display = pctNone)
                        LabeledSlider("Kabur", b.wmBlur, { up(b.copy(wmBlur = it)) }, display = pctNone)
                        LabeledSlider("Getaran kabur", b.wmBlurJitter, { up(b.copy(wmBlurJitter = it)) }, display = pctNone)
                        LabeledSlider("Getaran kebasahan", b.wmWetJitter, { up(b.copy(wmWetJitter = it)) }, display = pctNone)
                        Hint(
                            (if (b.wetMix()) "Wet Mix aktif: cat bercampur langsung dengan cat di layer. " else "Wet Mix mati (semua masih bawaan). ") +
                                "Pengenceran = banyaknya air; muatan = cat di kuas di awal goresan (makin kecil makin cepat habis); " +
                                "tarikan = cat di kanvas ikut terseret; kabur = cat di bawah kuas dilembutkan.",
                        )
                    }
                    "Cat air" -> {
                        SwitchRow(
                            "Efek cat air",
                            "Tepi basah tak rata, tepi gelap saat mengering, bercak, pigmen mengendap di kertas, lapisan menumpuk",
                            b.watercolor,
                        ) { up(b.copy(watercolor = it)) }
                        if (b.watercolor) {
                            LabeledSlider("Kepekatan pigmen", b.wcDensity, { up(b.copy(wcDensity = it)) }, 0.05f..1f, pct)
                            LabeledSlider("Tepi gelap", b.wcEdge, { up(b.copy(wcEdge = it)) })
                            LabeledSlider("Tepi lembut (basah)", b.wcSoft, { up(b.copy(wcSoft = it)) })
                            LabeledSlider("Bercak", b.wcBloom, { up(b.copy(wcBloom = it)) })
                            LabeledSlider("Granulasi", b.wcGrain, { up(b.copy(wcGrain = it)) })
                            SubLabel("Merembes (cat air hidup)")
                            LabeledSlider("Rembesan", b.wcSpread, { up(b.copy(wcSpread = it)) }, display = pctNone)
                            if (b.wcSpread > 0.001f) {
                                LabeledSlider("Lama basah", b.wcWetTime, { up(b.copy(wcWetTime = it)) }, 0.5f..8f, { "${"%.1f".format(it)} detik" })
                            }
                            Hint(
                                "Selama masih basah, cat terus merembes keluar mengikuti serat kertas lalu mengering dengan tepi gelap. " +
                                    "Goresan berikutnya di bagian yang masih basah ikut bercampur (basah di atas basah). " +
                                    "Ganti kuas/opasitas/layer, urungkan, atau alat lain = cat yang basah langsung kering.",
                            )
                            SwitchRow(
                                "Lapisan menumpuk",
                                "Sapuan di atas cat yang sudah ada jadi lebih pekat (glazing). Mati = menutup seperti biasa",
                                b.wcGlaze,
                            ) { up(b.copy(wcGlaze = it)) }
                        }
                    }
                    "Dinamika" -> {
                        SubLabel("Kecepatan")
                        LabeledSlider("Ukuran", b.speedSize, { up(b.copy(speedSize = it)) }, -1f..1f, pctSigned)
                        LabeledSlider("Opasitas", b.speedOpacity, { up(b.copy(speedOpacity = it)) }, -1f..1f, pctSigned)
                        LabeledSlider("Jarak cap", b.speedSpacing, { up(b.copy(speedSpacing = it)) }, display = pctNone)
                        Hint("+ = goresan cepat makin tipis/transparan, − = goresan lambat makin tipis/transparan.")
                        SubLabel("Getaran")
                        LabeledSlider("Ukuran", b.sizeJitter, { up(b.copy(sizeJitter = it)) }, display = pctNone)
                        LabeledSlider("Opasitas", b.opacityJitter, { up(b.copy(opacityJitter = it)) }, display = pctNone)
                    }
                    "Dinamika warna" -> {
                        Hint("Warna kedua diatur di panel Warna: kotak \"Kedua\" di kanan atas (ketuk = tukar, tahan = pakai warna sekarang).")
                        SubLabel("Getaran warna per cap")
                        LabeledSlider("Rona", b.hueJitter, { up(b.copy(hueJitter = it)) }, display = pctNone)
                        LabeledSlider("Saturasi", b.satJitter, { up(b.copy(satJitter = it)) }, display = pctNone)
                        LabeledSlider("Lebih terang", b.lightJitter, { up(b.copy(lightJitter = it)) }, display = pctNone)
                        LabeledSlider("Lebih gelap", b.darkJitter, { up(b.copy(darkJitter = it)) }, display = pctNone)
                        LabeledSlider("Warna kedua", b.color2Jitter, { up(b.copy(color2Jitter = it)) }, display = pctNone)
                        SubLabel("Getaran warna per goresan")
                        LabeledSlider("Rona", b.strokeHue, { up(b.copy(strokeHue = it)) }, display = pctNone)
                        LabeledSlider("Saturasi", b.strokeSat, { up(b.copy(strokeSat = it)) }, display = pctNone)
                        LabeledSlider("Lebih terang", b.strokeLight, { up(b.copy(strokeLight = it)) }, display = pctNone)
                        LabeledSlider("Lebih gelap", b.strokeDark, { up(b.copy(strokeDark = it)) }, display = pctNone)
                        LabeledSlider("Warna kedua", b.strokeColor2, { up(b.copy(strokeColor2 = it)) }, display = pctNone)
                        SubLabel("Warna menurut tekanan")
                        LabeledSlider("Rona", b.pressHue, { up(b.copy(pressHue = it)) }, display = pctNone)
                        LabeledSlider("Saturasi", b.pressSat, { up(b.copy(pressSat = it)) }, display = pctNone)
                        LabeledSlider("Kecerahan", b.pressBright, { up(b.copy(pressBright = it)) }, -1f..1f, pctSigned)
                        LabeledSlider("Warna kedua", b.pressColor2, { up(b.copy(pressColor2 = it)) }, display = pctNone)
                        SubLabel("Warna menurut kemiringan")
                        LabeledSlider("Rona", b.tiltHue, { up(b.copy(tiltHue = it)) }, display = pctNone)
                        LabeledSlider("Saturasi", b.tiltSat, { up(b.copy(tiltSat = it)) }, display = pctNone)
                        LabeledSlider("Kecerahan", b.tiltBright, { up(b.copy(tiltBright = it)) }, -1f..1f, pctSigned)
                        LabeledSlider("Warna kedua", b.tiltColor2, { up(b.copy(tiltColor2 = it)) }, display = pctNone)
                    }
                    "Pena" -> {
                        SubLabel("Kurva tekanan")
                        PressureCurve(b.pressureCurve) { up(b.copy(pressureCurve = it)) }
                        SubLabel("Tekanan")
                        LabeledSlider("Ukuran", b.pressureSize, { up(b.copy(pressureSize = it)) }, display = pctNone)
                        LabeledSlider("Opasitas", b.pressureOpacity, { up(b.copy(pressureOpacity = it)) }, display = pctNone)
                        LabeledSlider("Bleed", b.pressureBleed, { up(b.copy(pressureBleed = it)) }, display = pctNone)
                        Hint("Bleed: tekanan ringan hanya menampakkan bagian pekat bentuk dan tekstur, seperti kuas kering.")
                        SubLabel("Kemiringan")
                        LabeledSlider("Sudut mulai", b.tiltAngle, { up(b.copy(tiltAngle = it)) }, 25f..90f, { "${it.roundToInt()}°" })
                        TiltGraph(b.tiltAngle)
                        LabeledSlider("Ukuran", b.tiltSize, { up(b.copy(tiltSize = it)) }, display = pctNone)
                        LabeledSlider("Opasitas", b.tiltOpacity, { up(b.copy(tiltOpacity = it)) }, display = pctNone)
                        LabeledSlider("Bleed", b.tiltBleed, { up(b.copy(tiltBleed = it)) }, display = pctNone)
                    }
                    "Properti" -> {
                        if (!b.builtIn) {
                            var name by remember(b.id) { mutableStateOf(b.name) }
                            OutlinedTextField(
                                value = name,
                                onValueChange = { name = it; up(b.copy(name = it.ifBlank { "Kuas" })) },
                                label = { Text("Nama kuas") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                        SwitchRow(
                            "Hadap layar (orient to screen)",
                            "Bentuk kuas tetap menghadap ke atas layar walau kanvas diputar",
                            b.orientScreen,
                        ) { up(b.copy(orientScreen = it)) }
                        LabeledSlider("Ukuran maksimum", b.sizeMax, { up(b.copy(sizeMax = it)) }, 2f..1000f, { "${it.roundToInt()} px" })
                        LabeledSlider("Ukuran minimum", b.sizeMin, { up(b.copy(sizeMin = it)) }, 0.5f..50f, { "${"%.1f".format(it)} px" })
                        LabeledSlider("Opasitas maksimum", b.opacityMax, { up(b.copy(opacityMax = it)) })
                        LabeledSlider("Aliran (flow)", b.flow, { up(b.copy(flow = it)) }, 0.01f..1f, pct)
                        LabeledSlider("Kekuatan baur (smudge)", b.smudgeStrength, { up(b.copy(smudgeStrength = it)) })
                    }
                    "Kuas ganda" -> {
                        val others = state.brushes.list.filter { it.id != b.id && !it.stamp }
                        val ids = listOf("") + others.map { it.id }
                        val cur = if (others.any { it.id == b.dualId }) b.dualId else ""
                        Choice("Kuas kedua", cur, ids, { id -> if (id.isEmpty()) "Tidak ada" else others.firstOrNull { it.id == id }?.name ?: "?" }) {
                            up(b.copy(dualId = it))
                        }
                        if (cur.isNotEmpty()) {
                            Choice("Cara gabung", b.dualMode, DualNames.indices.toList(), { DualNames[it] }) { up(b.copy(dualMode = it)) }
                            LabeledSlider("Ukuran kuas kedua", b.dualSize, { up(b.copy(dualSize = it)) }, 0.1f..4f, { "×${"%.2f".format(it)}" })
                        }
                        Hint("Kuas kedua menggores di jalur yang sama dan menjadi topeng: \"Irisan\" hanya menampakkan bagian yang tertutup kedua kuas (mis. kuas bulat + tekstur kasar). Tidak berlaku untuk goresan cat basah (Wet Mix).")
                    }
                }
                Spacer(Modifier.height(12.dp))
            }
        }
    }
}

@Composable
private fun Hint(text: String) {
    Text(text, color = Muted, fontSize = 11.sp, modifier = Modifier.padding(vertical = 4.dp))
}

/**
 * Kurva tekanan kuas (seperti Pressure Graph Procreate): sumbu mendatar = tekanan pena, tegak = hasilnya.
 * Ketuk tempat kosong = tambah titik (paling banyak 4), seret titik = ubah, ketuk titik tanpa menyeret = hapus.
 */
@Composable
private fun PressureCurve(points: List<Float>, onChange: (List<Float>) -> Unit) {
    val cur by rememberUpdatedState(points)
    val change by rememberUpdatedState(onChange)
    Box(
        Modifier
            .fillMaxWidth()
            .height(180.dp)
            .padding(vertical = 4.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(Color(0x1FFFFFFF))
            .pointerInput(Unit) {
                awaitEachGesture {
                    val down = awaitFirstDown()
                    val w = size.width.toFloat()
                    val h = size.height.toFloat()
                    fun unit(o: Offset) = Offset((o.x / w).coerceIn(0f, 1f), (1f - o.y / h).coerceIn(0f, 1f))
                    val pts = cur.chunked(2).filter { it.size == 2 }.map { Offset(it[0], it[1]) }.toMutableList()
                    val u = unit(down.position)
                    var idx = -1
                    var best = Float.MAX_VALUE
                    pts.forEachIndexed { i, p ->
                        val d = hypot((p.x - u.x) * w, (p.y - u.y) * h)
                        if (d < best) { best = d; idx = i }
                    }
                    val near = best < 28.dp.toPx()
                    var added = false
                    if (!near) {
                        if (pts.size >= 4) return@awaitEachGesture
                        pts.add(u)
                        idx = pts.lastIndex
                        added = true
                        change(pts.flatMap { listOf(it.x, it.y) })
                    }
                    down.consume()
                    var moved = false
                    while (true) {
                        val ev = awaitPointerEvent()
                        val c = ev.changes.firstOrNull { it.id == down.id } ?: break
                        if (!c.pressed) break
                        if ((c.position - down.position).getDistance() > 6.dp.toPx()) moved = true
                        if (moved) {
                            pts[idx] = unit(c.position)
                            change(pts.flatMap { listOf(it.x, it.y) })
                        }
                        c.consume()
                    }
                    if (near && !moved && !added) {
                        pts.removeAt(idx)
                        change(pts.flatMap { listOf(it.x, it.y) })
                    }
                }
            },
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height
            val grid = Color(0x22FFFFFF)
            for (q in 1..3) {
                drawLine(grid, Offset(w * q / 4f, 0f), Offset(w * q / 4f, h))
                drawLine(grid, Offset(0f, h * q / 4f), Offset(w, h * q / 4f))
            }
            val lut = FloatArray(129)
            if (points.size >= 2) buildCurve(points, lut) else for (i in lut.indices) lut[i] = i / 128f
            val path = Path()
            for (i in lut.indices) {
                val x = i / 128f * w
                val y = (1f - lut[i]) * h
                if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
            }
            drawPath(path, Accent, style = Stroke(width = 2.dp.toPx()))
            points.chunked(2).filter { it.size == 2 }.forEach { p ->
                val c = Offset(p[0] * w, (1f - p[1]) * h)
                drawCircle(Color.Black, 7.dp.toPx(), c)
                drawCircle(Color.White, 5.dp.toPx(), c)
            }
        }
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            "Ketuk = tambah titik, seret = ubah, ketuk titik = hapus",
            color = Muted, fontSize = 11.sp, modifier = Modifier.weight(1f),
        )
        TextButton(enabled = points.isNotEmpty(), onClick = { onChange(emptyList()) }) { Text("Lurus") }
    }
}

/** Grafik sudut mulai kemiringan: busur 0–90° dari permukaan; bagian biru = sudut yang memicu efek kemiringan. */
@Composable
private fun TiltGraph(angle: Float) {
    Canvas(
        Modifier
            .fillMaxWidth()
            .height(70.dp)
            .padding(vertical = 4.dp),
    ) {
        val r = min(size.height * 0.95f, size.width * 0.45f)
        val c = Offset(size.width / 2f - r / 2f, size.height)
        drawArc(Color(0x33FFFFFF), 270f, 90f, true, topLeft = Offset(c.x - r, c.y - r), size = Size(r * 2, r * 2))
        // Sudut dari permukaan: 0° = mendatar (kanan), 90° = tegak (atas). Efek berlaku antara 0 dan sudut mulai.
        drawArc(Accent.copy(alpha = 0.6f), 360f - angle, angle, true, topLeft = Offset(c.x - r, c.y - r), size = Size(r * 2, r * 2))
        val rad = Math.toRadians(angle.toDouble())
        drawLine(Color.White, c, Offset(c.x + r * cos(rad).toFloat(), c.y - r * sin(rad).toFloat()), strokeWidth = 2.dp.toPx())
    }
}

private const val SPACING_MIN = 0.01f
private const val SPACING_MAX = 10f

/** Posisi geser 0..1 → jarak antar cap (pecahan diameter), dibulatkan ke persen bulat. */
private fun spacingAtSlider(t: Float): Float =
    ((SPACING_MIN * (SPACING_MAX / SPACING_MIN).pow(t.coerceIn(0f, 1f)) * 100f).roundToInt() / 100f).coerceIn(SPACING_MIN, SPACING_MAX)
