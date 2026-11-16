package id.goresan.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AspectRatio
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.Wallpaper
import androidx.compose.material.icons.filled.ViewInAr
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.activity.compose.BackHandler
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.math.roundToLong

/** Satuan ukuran kanvas; [perInch] = berapa satuan dalam satu inci (0 untuk piksel). */
enum class CanvasUnit(val label: String, val short: String, val perInch: Double) {
    MM("Milimeter", "mm", 25.4),
    CM("Sentimeter", "cm", 2.54),
    IN("Inci", "in", 1.0),
    PX("Piksel", "px", 0.0),
}

private class CanvasPreset(val name: String, val w: Int, val h: Int, val dpi: Int, val unit: CanvasUnit)

private val CanvasPresets = listOf(
    CanvasPreset("Layar Xiaomi Pad 7 (mendatar)", 3200, 2136, 300, CanvasUnit.PX),
    CanvasPreset("Layar Xiaomi Pad 7 (tegak)", 2136, 3200, 300, CanvasUnit.PX),
    CanvasPreset("Persegi", 2048, 2048, 300, CanvasUnit.PX),
    CanvasPreset("Persegi besar", 4096, 4096, 300, CanvasUnit.PX),
    CanvasPreset("A4 (tegak)", 2480, 3508, 300, CanvasUnit.MM),
    CanvasPreset("A4 (mendatar)", 3508, 2480, 300, CanvasUnit.MM),
    CanvasPreset("A5 (tegak)", 1748, 2480, 300, CanvasUnit.MM),
    CanvasPreset("A3 (tegak)", 3508, 4961, 300, CanvasUnit.MM),
    CanvasPreset("Layar 4K", 3840, 2160, 72, CanvasUnit.PX),
    CanvasPreset("Postingan Instagram 4:5", 2160, 2700, 72, CanvasUnit.PX),
)

private const val MIN_PX = 64
private const val MAX_PX = 8192
private val KeyBg = Color(0xFF3A3A42)
private val ErrorText = Color(0xFFFF8A80)

/** Angka dengan koma desimal Indonesia, nol di belakang dibuang. */
private fun num(v: Double, decimals: Int): String {
    var t = String.format(java.util.Locale.US, "%.${decimals}f", v)
    if (t.contains('.')) t = t.trimEnd('0').trimEnd('.')
    return t.replace('.', ',')
}

private fun pxToUnit(px: Int, dpi: Int, u: CanvasUnit): Double =
    if (u == CanvasUnit.PX) px.toDouble() else px.toDouble() / max(1, dpi) * u.perInch

private fun unitDecimals(u: CanvasUnit) = when (u) {
    CanvasUnit.MM -> 1
    CanvasUnit.CM, CanvasUnit.IN -> 2
    CanvasUnit.PX -> 0
}

/**
 * Layar "Kanvas khusus" seperti Procreate: ukuran dengan satuan mm/cm/inci/piksel lewat papan angka sendiri,
 * DPI, perkiraan maksimal layer, ukuran siap pakai, dan properti kanvas (warna latar, latar disembunyikan).
 */
@Composable
fun NewCanvasScreen(state: AppState, onDismiss: () -> Unit, onCreated: () -> Unit) {
    var name by remember { mutableStateOf("Tanpa judul") }
    var wPx by remember { mutableIntStateOf(3200) }
    var hPx by remember { mutableIntStateOf(2136) }
    var dpi by remember { mutableIntStateOf(300) }
    var unit by remember { mutableStateOf(CanvasUnit.PX) }
    // Bidang yang sedang diisi: 0 lebar, 1 tinggi, 2 DPI, -1 tidak ada (papan angka disembunyikan).
    var field by remember { mutableIntStateOf(0) }
    // Teks yang sedang diketik di bidang itu; null = belum mengetik (ketikan pertama mengganti nilai lama).
    var entry by remember { mutableStateOf<String?>(null) }
    // Ukuran fisik (inci) saat mulai mengetik DPI: dengan satuan fisik, ukuran cetak tetap dan pikselnya menyesuaikan.
    var physW by remember { mutableDoubleStateOf(0.0) }
    var physH by remember { mutableDoubleStateOf(0.0) }
    var tab by remember { mutableIntStateOf(0) }
    var bgc by remember { mutableIntStateOf(android.graphics.Color.WHITE) }
    var bgHidden by remember { mutableStateOf(false) }
    var paper by remember { mutableIntStateOf(id.goresan.engine.Paper.NONE) }
    var profile by remember { mutableIntStateOf(id.goresan.engine.ColorProfile.SRGB) }
    // Model 3D: berkas OBJ (+ MTL + gambar tekstur) atau GLB.
    val modelPicker = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.OpenMultipleDocuments(),
    ) { uris ->
        if (uris.isNotEmpty()) {
            onCreated()
            state.import3D(uris)
        }
    }

    val valid = wPx in MIN_PX..MAX_PX && hPx in MIN_PX..MAX_PX && dpi in 1..9600
    val limit = if (valid) state.estimateLayerLimit(wPx, hPx) else 0

    fun shown(f: Int): String = when (f) {
        0 -> num(pxToUnit(wPx, dpi, unit), unitDecimals(unit))
        1 -> num(pxToUnit(hPx, dpi, unit), unitDecimals(unit))
        else -> dpi.toString()
    }

    fun toPx(v: Double): Int {
        val px = if (unit == CanvasUnit.PX) v.roundToLong() else (v / unit.perInch * dpi).roundToLong()
        return px.coerceIn(0, 99_999).toInt()
    }

    fun apply(t: String) {
        val v = t.replace(',', '.').toDoubleOrNull() ?: return
        when (field) {
            0 -> wPx = toPx(v)
            1 -> hPx = toPx(v)
            2 -> {
                val nd = v.roundToInt().coerceIn(0, 9600)
                if (nd >= 1) {
                    dpi = nd
                    if (unit != CanvasUnit.PX) {
                        wPx = (physW * nd).roundToLong().coerceIn(0, 99_999).toInt()
                        hPx = (physH * nd).roundToLong().coerceIn(0, 99_999).toInt()
                    }
                }
            }
        }
    }

    fun begin() {
        physW = wPx.toDouble() / max(1, dpi)
        physH = hPx.toDouble() / max(1, dpi)
    }

    fun key(k: String) {
        if (field !in 0..2) return
        when (k) {
            "," -> {
                if (field == 2 || unit == CanvasUnit.PX) return
                val e = entry ?: run { begin(); "0" }
                if (!e.contains(',')) entry = "$e,"
            }
            "⌫" -> {
                val e = entry ?: run { begin(); shown(field) }
                val n = e.dropLast(1)
                entry = n
                apply(n)
            }
            else -> {
                val e = entry ?: run { begin(); "" }
                if (e.length >= 8) return
                val n = if (e == "0") k else e + k
                entry = n
                apply(n)
            }
        }
    }

    fun select(f: Int) {
        entry = null
        field = f
    }

    // Lapisan layar penuh di atas galeri (bukan jendela dialog, supaya bilah sistem tetap tersembunyi).
    BackHandler { onDismiss() }
    Box(Modifier.fillMaxSize()) {
        Surface(color = Color(0xFF1B1B1F), modifier = Modifier.fillMaxSize()) {
            Column(Modifier.fillMaxSize().padding(horizontal = 24.dp, vertical = 18.dp)) {
                // Kepala: judul, nama karya, Batal, Buat.
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    Text("Kanvas khusus", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.weight(1f))
                    OutlinedTextField(
                        value = name, onValueChange = { name = it.take(60) }, singleLine = true,
                        label = { Text("Nama karya") },
                        modifier = Modifier.widthIn(max = 320.dp),
                    )
                    Spacer(Modifier.weight(1f))
                    TextButton(onClick = onDismiss) { Text("Batal", fontSize = 16.sp) }
                    Spacer(Modifier.width(8.dp))
                    Button(
                        enabled = valid,
                        onClick = {
                            onCreated()
                            state.createArtwork(name, wPx, hPx, dpi, bgc, !bgHidden, paper, profile)
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFF6C453), contentColor = Color(0xFF3A2A00)),
                    ) { Text("Buat", fontWeight = FontWeight.SemiBold, fontSize = 16.sp) }
                }
                Spacer(Modifier.height(18.dp))
                BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
                    // Tablet tegak/layar sempit: menu pindah ke atas supaya papan angka tidak terpotong.
                    val narrow = maxWidth < 860.dp
                    val tabs: @Composable (Modifier) -> Unit = { m ->
                        SideItem(Icons.Filled.AspectRatio, "Ukuran", tab == 0, m) { tab = 0 }
                        SideItem(Icons.Filled.Dashboard, "Ukuran siap pakai", tab == 1, m) { tab = 1; select(-1) }
                        SideItem(Icons.Filled.Wallpaper, "Properti kanvas", tab == 2, m) { tab = 2; select(-1) }
                        SideItem(Icons.Filled.ViewInAr, "Model 3D", tab == 3, m) { tab = 3; select(-1) }
                    }
                    val body: @Composable (Modifier) -> Unit = { bm ->
                        Column(bm.verticalScroll(rememberScrollState())) {
                            when (tab) {
                                0 -> {
                                    Group {
                                        FieldRow("Lebar", "${if (field == 0 && entry != null) entry else shown(0)} ${unit.short}", field == 0) { select(0) }
                                        FieldRow("Tinggi", "${if (field == 1 && entry != null) entry else shown(1)} ${unit.short}", field == 1) { select(1) }
                                    }
                                    Spacer(Modifier.height(14.dp))
                                    Group {
                                        FieldRow("DPI", if (field == 2 && entry != null) entry!! else shown(2), field == 2) { select(2) }
                                        FieldRow("Maksimal layer", if (valid) "$limit" else "—", false, null)
                                    }
                                    Spacer(Modifier.height(10.dp))
                                    if (valid) {
                                        Text(
                                            "$wPx × $hPx px · ukuran cetak ${num(pxToUnit(wPx, dpi, CanvasUnit.CM), 1)} × " +
                                                "${num(pxToUnit(hPx, dpi, CanvasUnit.CM), 1)} cm pada $dpi DPI",
                                            color = Muted, fontSize = 13.sp,
                                        )
                                    } else {
                                        Text(
                                            "Lebar dan tinggi harus $MIN_PX–$MAX_PX piksel (sekarang $wPx × $hPx px)",
                                            color = ErrorText, fontSize = 13.sp,
                                        )
                                    }
                                    Spacer(Modifier.height(18.dp))
                                    if (field in 0..2) {
                                        Keypad(
                                            unit = unit,
                                            decimalOn = field != 2 && unit != CanvasUnit.PX,
                                            onUnit = { entry = null; unit = it },
                                            onKey = ::key,
                                            onNext = { select((field + 1) % 3) },
                                            onDone = { select(-1) },
                                        )
                                    } else {
                                        Text("Ketuk Lebar, Tinggi, atau DPI untuk mengubahnya", color = Muted, fontSize = 13.sp)
                                    }
                                }
                                1 -> {
                                    Group {
                                        for (p in CanvasPresets) {
                                            FieldRow(p.name, "${p.w} × ${p.h} px · ${p.dpi} DPI", false) {
                                                wPx = p.w; hPx = p.h; dpi = p.dpi; unit = p.unit
                                                tab = 0
                                                select(-1)
                                            }
                                        }
                                    }
                                    Spacer(Modifier.height(8.dp))
                                    Text("Ketuk salah satu untuk memakai ukurannya; masih bisa diubah di Ukuran", color = Muted, fontSize = 13.sp)
                                }
                                2 -> CanvasProps(state, wPx, hPx, bgc, { bgc = it }, bgHidden, { bgHidden = it }, paper, { paper = it }, profile, { profile = it })
                                else -> Column(Modifier.width(520.dp)) {
                                    Text("Melukis model 3D", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                                    Spacer(Modifier.height(8.dp))
                                    Text(
                                        "Pilih berkas GLB, atau OBJ beserta MTL dan gambar teksturnya sekaligus (tahan untuk memilih beberapa). " +
                                            "Kanvas menjadi tekstur model: lukis langsung di model 3D, atau di tampilan tekstur 2D dengan semua alat biasa.",
                                        color = Muted, fontSize = 13.sp,
                                    )
                                    Spacer(Modifier.height(6.dp))
                                    Text(
                                        "Model perlu peta UV supaya rapi; model tanpa UV memakai proyeksi kotak otomatis. Ukuran kanvas = ukuran tekstur " +
                                            "(tanpa tekstur: 2048 × 2048). Hasilnya bisa diekspor sebagai GLB bertekstur.",
                                        color = Muted, fontSize = 13.sp,
                                    )
                                    Spacer(Modifier.height(16.dp))
                                    Button(
                                        onClick = { modelPicker.launch(arrayOf("*/*")) },
                                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFF6C453), contentColor = Color(0xFF3A2A00)),
                                    ) { Text("Pilih model 3D (OBJ/GLB)…", fontWeight = FontWeight.SemiBold) }
                                    Spacer(Modifier.height(28.dp))
                                    Text("Face Paint", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                                    Spacer(Modifier.height(8.dp))
                                    Text(
                                        "Melukis di wajah sendiri lewat kamera depan: wajah dikenali di tablet (ML Kit), cat menempel dan ikut " +
                                            "bergerak. Kanvasnya tekstur wajah 1024 × 1024; layer Peta wajah (tersembunyi) jadi panduan di tampilan 2D.",
                                        color = Muted, fontSize = 13.sp,
                                    )
                                    Spacer(Modifier.height(12.dp))
                                    Button(
                                        onClick = { onCreated(); state.createFacePaint() },
                                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFF6C453), contentColor = Color(0xFF3A2A00)),
                                    ) { Text("Mulai Face Paint", fontWeight = FontWeight.SemiBold) }
                                }
                            }
                        }
                    }
                    if (narrow) {
                        Column(Modifier.fillMaxSize()) {
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                                tabs(Modifier.weight(1f))
                            }
                            Spacer(Modifier.height(16.dp))
                            body(Modifier.weight(1f).fillMaxWidth())
                        }
                    } else {
                        Row(Modifier.fillMaxSize()) {
                            Column(Modifier.width(230.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                tabs(Modifier.fillMaxWidth())
                            }
                            Spacer(Modifier.width(28.dp))
                            body(Modifier.weight(1f))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SideItem(icon: ImageVector, label: String, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    Row(
        modifier
            .clip(RoundedCornerShape(10.dp))
            .background(if (selected) Color(0xFF2F5BEA) else Color.Transparent)
            .clickable { onClick() }
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = if (selected) Color.White else Muted, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(12.dp))
        Text(
            label, color = if (selected) Color.White else Color(0xFFCFCFD6), fontSize = 15.sp,
            maxLines = 1, overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun Group(content: @Composable () -> Unit) {
    Column(
        Modifier
            .widthIn(max = 640.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(Color(0xFF26262C)),
    ) { content() }
}

@Composable
private fun FieldRow(label: String, value: String, selected: Boolean, onClick: (() -> Unit)?) {
    Row(
        Modifier
            .fillMaxWidth()
            .background(if (selected) Color(0x333D8BFF) else Color.Transparent)
            .then(if (onClick != null) Modifier.clickable { onClick() } else Modifier)
            .padding(horizontal = 18.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, color = Color.White, fontSize = 15.sp, modifier = Modifier.weight(1f))
        Text(value, color = if (selected) Accent else Color(0xFFE0E0E6), fontSize = 15.sp)
    }
}

@Composable
private fun Key(
    text: String,
    modifier: Modifier,
    h: Dp = 58.dp,
    bg: Color = KeyBg,
    fg: Color = Color.White,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    Box(
        modifier
            .height(h)
            .clip(RoundedCornerShape(10.dp))
            .background(if (enabled) bg else bg.copy(alpha = 0.35f))
            .clickable(enabled = enabled) { onClick() }
            .padding(horizontal = 4.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text, color = if (enabled) fg else fg.copy(alpha = 0.4f), fontSize = if (text.length == 1) 22.sp else 15.sp,
            maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis,
        )
    }
}

/** Papan angka mengikuti lebar yang tersedia (maks 640 dp), jadi tidak terpotong di tablet tegak. */
@Composable
private fun Keypad(
    unit: CanvasUnit,
    decimalOn: Boolean,
    onUnit: (CanvasUnit) -> Unit,
    onKey: (String) -> Unit,
    onNext: () -> Unit,
    onDone: () -> Unit,
) {
    val gap = 8.dp
    val digit = Color(0xFF55555F)
    Row(
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.widthIn(max = 640.dp).fillMaxWidth(),
    ) {
        Column(Modifier.weight(1.45f), verticalArrangement = Arrangement.spacedBy(gap)) {
            for (u in CanvasUnit.entries) {
                val on = u == unit
                Key(
                    u.label, Modifier.fillMaxWidth(),
                    bg = if (on) Color(0xFFD3E6FF) else KeyBg, fg = if (on) Color(0xFF0D2A4A) else Color.White,
                ) { onUnit(u) }
            }
        }
        Column(Modifier.weight(3f), verticalArrangement = Arrangement.spacedBy(gap)) {
            for (row in listOf(listOf("7", "8", "9"), listOf("4", "5", "6"), listOf("1", "2", "3"))) {
                Row(horizontalArrangement = Arrangement.spacedBy(gap)) {
                    for (k in row) Key(k, Modifier.weight(1f), bg = digit) { onKey(k) }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(gap)) {
                Key("0", Modifier.weight(2f), bg = digit) { onKey("0") }
                Key(",", Modifier.weight(1f), bg = digit, enabled = decimalOn) { onKey(",") }
            }
        }
        Column(Modifier.weight(1.45f), verticalArrangement = Arrangement.spacedBy(gap)) {
            Key("Hapus", Modifier.fillMaxWidth()) { onKey("⌫") }
            Key("Berikutnya", Modifier.fillMaxWidth()) { onNext() }
            Key("Selesai", Modifier.fillMaxWidth(), h = 58.dp * 2 + gap) { onDone() }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CanvasProps(
    state: AppState,
    w: Int,
    h: Int,
    bgc: Int,
    onBg: (Int) -> Unit,
    hidden: Boolean,
    onHidden: (Boolean) -> Unit,
    paper: Int,
    onPaper: (Int) -> Unit,
    profile: Int,
    onProfile: (Int) -> Unit,
) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(28.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
        Column {
            Text("Warna latar", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(10.dp))
            CompactColorPicker(
                color = bgc,
                onChange = onBg,
                swatches = listOf(
                    android.graphics.Color.WHITE, 0xFFF4E3CF.toInt(), 0xFFFFF8E7.toInt(), 0xFFD1D1D6.toInt(),
                    0xFF8E8E93.toInt(), 0xFF3A3A3C.toInt(), android.graphics.Color.BLACK, state.color,
                ) + state.palette.toList().take(16),
                width = 300.dp,
            )
        }
        Column(Modifier.width(260.dp)) {
            Text("Pratinjau", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(10.dp))
            val box = 220.dp
            val ratio = if (w > 0 && h > 0) w.toFloat() / h else 1f
            val pw = if (ratio >= 1f) box else box * ratio
            val ph = if (ratio >= 1f) box / ratio else box
            Box(Modifier.size(box), contentAlignment = Alignment.Center) {
                Box(
                    Modifier
                        .size(pw, ph)
                        .then(if (hidden) Modifier.checker() else Modifier.background(Color(bgc or 0xFF000000.toInt())))
                        .border(1.dp, Color(0x55FFFFFF)),
                )
            }
            Spacer(Modifier.height(14.dp))
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .clickable { onHidden(!hidden) }
                    .padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text("Latar disembunyikan", color = Color.White, fontSize = 15.sp)
                    Text("Latar transparan; bisa ditampilkan lagi dari panel Layer", color = Muted, fontSize = 12.sp)
                }
                Switch(checked = hidden, onCheckedChange = onHidden, colors = SwitchDefaults.colors(checkedTrackColor = Accent))
            }
        }
        Column(Modifier.width(300.dp)) {
            Text("Tekstur kertas", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(10.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                for (t in 0 until id.goresan.engine.Paper.CUSTOM) {
                    val on = paper == t
                    Text(
                        id.goresan.engine.Paper.names[t],
                        color = Color.White, fontSize = 13.sp,
                        modifier = Modifier
                            .clip(RoundedCornerShape(10.dp))
                            .background(if (on) Accent else Color(0x22FFFFFF))
                            .clickable { onPaper(t) }
                            .padding(horizontal = 12.dp, vertical = 7.dp),
                    )
                }
            }
            Text(
                "Bisa diganti kapan saja di Aksi › Kertas kanvas, termasuk dengan gambar kertas sendiri.",
                color = Muted, fontSize = 12.sp, modifier = Modifier.padding(top = 8.dp),
            )
        }
        Column(Modifier.width(300.dp)) {
            Text("Profil warna", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(10.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                for (t in 0..2) {
                    val on = profile == t
                    Text(
                        id.goresan.engine.ColorProfile.name(t),
                        color = Color.White, fontSize = 13.sp,
                        modifier = Modifier
                            .clip(RoundedCornerShape(10.dp))
                            .background(if (on) Accent else Color(0x22FFFFFF))
                            .clickable { onProfile(t) }
                            .padding(horizontal = 12.dp, vertical = 7.dp),
                    )
                }
            }
            Text(
                profileHint(profile) + " Bisa diganti di Aksi › Info kanvas.",
                color = Muted, fontSize = 12.sp, modifier = Modifier.padding(top = 8.dp),
            )
        }
    }
}

/** Penjelasan singkat profil warna. */
fun profileHint(p: Int): String = when (p) {
    id.goresan.engine.ColorProfile.P3 ->
        "Gamut lebih luas: merah, hijau, dan oranye lebih menyala di layar yang mendukung. PNG/JPG membawa profil Display P3."
    id.goresan.engine.ColorProfile.CMYK ->
        "Untuk dicetak: kanvas tampil seperti hasil cetak offset (warna yang tak bisa dicetak jadi kusam) dan bisa diekspor TIFF CMYK. Perkiraan kertas coated, bukan profil hasil ukur percetakan."
    else -> "Standar layar, web, dan media sosial."
}
