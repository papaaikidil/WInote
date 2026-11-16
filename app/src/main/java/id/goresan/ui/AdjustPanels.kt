package id.goresan.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Balance
import androidx.compose.material.icons.filled.BlurCircular
import androidx.compose.material.icons.filled.BlurLinear
import androidx.compose.material.icons.filled.BlurOn
import androidx.compose.material.icons.filled.BrokenImage
import androidx.compose.material.icons.filled.CenterFocusStrong
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.FilterTiltShift
import androidx.compose.material.icons.filled.Flare
import androidx.compose.material.icons.filled.Gradient
import androidx.compose.material.icons.filled.Grain
import androidx.compose.material.icons.filled.GridOn
import androidx.compose.material.icons.filled.Timeline
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Waves
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import id.goresan.engine.Adj
import id.goresan.engine.Curves
import id.goresan.engine.Gradients
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.roundToInt

private val adjTitles = mapOf(
    Adj.HSB to "Rona, Saturasi, Kecerahan",
    Adj.BALANCE to "Keseimbangan Warna",
    Adj.CURVES to "Kurva",
    Adj.GMAP to "Peta Gradien",
    Adj.GAUSS to "Blur Gaussian",
    Adj.MOTION to "Blur Gerak",
    Adj.PERSP to "Blur Perspektif",
    Adj.NOISE to "Noise",
    Adj.SHARPEN to "Pertajam",
    Adj.BLOOM to "Bloom",
    Adj.GLITCH to "Glitch",
    Adj.HALFTONE to "Halftone",
    Adj.CHROMA to "Aberasi Kromatik",
    Adj.LIQUIFY to "Liquify",
    Adj.CLONE to "Klon",
)

/** Menu Penyesuaian, dikelompokkan seperti Procreate. */
@Composable
fun AdjustMenu(state: AppState, modifier: Modifier) {
    Surface(
        color = PanelBg,
        shape = RoundedCornerShape(18.dp),
        shadowElevation = 8.dp,
        modifier = modifier.width(300.dp),
    ) {
        Column(
            Modifier
                .heightIn(max = 800.dp)
                .verticalScroll(rememberScrollState())
                .padding(12.dp),
        ) {
            Text("Penyesuaian", color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 17.sp)
            Text("Berlaku untuk layer aktif", color = Muted, fontSize = 12.sp)
            Group("Warna")
            AdjustItem(Icons.Filled.Tune, Adj.HSB, state)
            AdjustItem(Icons.Filled.Balance, Adj.BALANCE, state)
            AdjustItem(Icons.Filled.Timeline, Adj.CURVES, state)
            AdjustItem(Icons.Filled.Gradient, Adj.GMAP, state)
            Group("Blur")
            AdjustItem(Icons.Filled.BlurOn, Adj.GAUSS, state)
            AdjustItem(Icons.Filled.BlurLinear, Adj.MOTION, state)
            AdjustItem(Icons.Filled.BlurCircular, Adj.PERSP, state)
            Group("Efek")
            AdjustItem(Icons.Filled.Grain, Adj.NOISE, state)
            AdjustItem(Icons.Filled.CenterFocusStrong, Adj.SHARPEN, state)
            AdjustItem(Icons.Filled.Flare, Adj.BLOOM, state)
            AdjustItem(Icons.Filled.BrokenImage, Adj.GLITCH, state)
            AdjustItem(Icons.Filled.GridOn, Adj.HALFTONE, state)
            AdjustItem(Icons.Filled.FilterTiltShift, Adj.CHROMA, state)
            Group("Alat")
            AdjustItem(Icons.Filled.Waves, Adj.LIQUIFY, state)
            AdjustItem(Icons.Filled.ContentCopy, Adj.CLONE, state)
        }
    }
}

@Composable
private fun Group(title: String) {
    Text(
        title.uppercase(),
        color = Muted,
        fontSize = 11.sp,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(start = 8.dp, top = 8.dp, bottom = 0.dp),
    )
}

@Composable
private fun AdjustItem(icon: ImageVector, kind: Int, state: AppState) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .clickable { state.startAdjust(kind) }
            .padding(horizontal = 8.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = Color.White, modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(12.dp))
        Text(adjTitles[kind] ?: "", color = Color.White, fontSize = 14.sp)
    }
}

// ================= bar penyesuaian =================

private fun pct(v: Float) = "${(v * 100).roundToInt()}%"
private fun signed(v: Float): String {
    val n = (v * 100).roundToInt()
    return if (n > 0) "+$n" else "$n"
}
private fun px(v: Float) = "${v.roundToInt()} px"
private fun deg(v: Float) = "${v.roundToInt()}°"

@Composable
fun AdjustBar(state: AppState, modifier: Modifier) {
    val k = state.adjustKind
    if (state.adjCollapsed) {
        AdjustMini(state, modifier)
        return
    }
    Surface(color = PanelBg, shape = RoundedCornerShape(18.dp), modifier = modifier.width(520.dp), shadowElevation = 6.dp) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(adjTitles[k] ?: "", color = Color.White, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.weight(1f))
                IconButton(onClick = { state.adjCollapsed = true }, modifier = Modifier.size(36.dp)) {
                    Icon(Icons.Filled.ExpandLess, "Sembunyikan panel", tint = Color.White)
                }
                if (k == Adj.CLONE) {
                    TextButton(onClick = { state.adjustDone(true) }) { Text("Selesai", fontWeight = FontWeight.SemiBold) }
                } else {
                    TextButton(onClick = { state.adjustDone(false) }) { Text("Batal") }
                    TextButton(onClick = { state.adjustDone(true) }) { Text("Terapkan", fontWeight = FontWeight.SemiBold) }
                }
            }
            val big = state.docMax
            if (k != Adj.LIQUIFY && k != Adj.CLONE) PenModeRow(state)
            when (k) {
                Adj.HSB -> {
                    S(state, 0, "Rona", -180f..180f, ::deg)
                    S(state, 1, "Saturasi", -1f..1f, ::pct)
                    S(state, 2, "Kecerahan", -1f..1f, ::pct)
                }
                Adj.GAUSS -> SQ(state, 0, "Radius", 0f, max(300f, big / 3f), ::px)
                Adj.BALANCE -> {
                    Chips(listOf("Bayangan", "Nada tengah", "Sorotan"), state.adjTab) { state.adjTab = it }
                    val b = state.adjTab * 3
                    S(state, b, "Sian ↔ Merah", -1f..1f, ::signed)
                    S(state, b + 1, "Magenta ↔ Hijau", -1f..1f, ::signed)
                    S(state, b + 2, "Kuning ↔ Biru", -1f..1f, ::signed)
                }
                Adj.CURVES -> {
                    Chips(listOf("Gamma", "Merah", "Hijau", "Biru"), state.adjTab) { state.adjTab = it }
                    CurveEditor(state)
                }
                Adj.GMAP -> {
                    GradientPresets(state)
                    S(state, 0, "Jumlah", 0f..1f, ::pct)
                }
                Adj.MOTION -> {
                    SQ(state, 0, "Jumlah", 0f, max(300f, big / 3f), ::px)
                    S(state, 1, "Arah", -180f..180f, ::deg)
                }
                Adj.PERSP -> {
                    Chips(listOf("Posisional", "Searah"), state.adjMode) { state.chooseAdjMode(it) }
                    S(state, 0, "Jumlah", 0f..2f, ::pct)
                    CenterHint(state)
                }
                Adj.NOISE -> {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.weight(1f)) {
                            Chips(listOf("Awan", "Gelombang", "Punggung"), state.adjMode) { state.chooseAdjMode(it) }
                        }
                        TextButton(onClick = { state.setAdj(4, state.adjV.getOrElse(4) { 0f } + 1f) }) { Text("Acak lagi") }
                    }
                    S(state, 0, "Jumlah", 0f..1f, ::pct)
                    S(state, 1, "Skala", 2f..max(60f, big / 4f), ::px)
                    S(state, 2, "Oktaf", 1f..8f, { "${it.roundToInt()}" }, steps = 6)
                    S(state, 3, "Turbulensi", 0f..1f, ::pct)
                }
                Adj.SHARPEN -> {
                    S(state, 0, "Jumlah", 0f..2f, ::pct)
                    SQ(state, 1, "Radius", 1f, max(40f, big / 50f), ::px)
                }
                Adj.BLOOM -> {
                    S(state, 0, "Jumlah", 0f..1f, ::pct)
                    S(state, 1, "Transisi", 0f..1f, ::pct)
                    SQ(state, 2, "Ukuran", 1f, max(200f, big / 5f), ::px)
                    S(state, 3, "Bakar", 0f..1f, ::pct)
                }
                Adj.GLITCH -> {
                    Chips(listOf("Artefak", "Gelombang", "Sinyal", "Pecah"), state.adjMode) { state.chooseAdjMode(it) }
                    S(state, 0, "Jumlah", 0f..1f, ::pct)
                    SQ(state, 1, "Ukuran blok", 2f, max(120f, big / 6f), ::px)
                    S(state, 2, "Variasi", 0f..100f, { "${it.roundToInt()}" })
                }
                Adj.HALFTONE -> {
                    Chips(listOf("Warna penuh", "Sablon", "Koran"), state.adjMode) { state.chooseAdjMode(it) }
                    S(state, 0, "Ukuran", 0f..1f, ::pct)
                }
                Adj.CHROMA -> {
                    Chips(listOf("Perspektif", "Geser"), state.adjMode) { state.chooseAdjMode(it) }
                    S(state, 0, "Jumlah", 0f..1f, ::pct)
                    if (state.adjMode == 0) {
                        S(state, 1, "Falloff", 0f..1f, ::pct)
                        CenterHint(state)
                    } else {
                        S(state, 2, "Arah", -180f..180f, ::deg)
                    }
                }
                Adj.LIQUIFY -> {
                    Chips(
                        listOf("Dorong", "Putar kanan", "Putar kiri", "Cubit", "Kembangkan", "Kristal", "Tepi", "Pulihkan"),
                        state.adjMode,
                    ) { state.chooseAdjMode(it) }
                    // Penggeser dua kolom supaya bar tidak menutupi banyak kanvas saat menyapu.
                    val distort = state.adjMode == Adj.LQ_CRYSTAL || state.adjMode == Adj.LQ_EDGE
                    Two({ SQ(state, 0, "Ukuran", 10f, max(200f, big / 2f), ::px) }, { S(state, 1, "Tekanan", 0f..1f, ::pct) })
                    Two(
                        { S(state, 4, "Momentum", 0f..1f, ::pct) },
                        { if (distort) S(state, 2, "Distorsi", 0f..1f, ::pct) else S(state, 3, "Sesuaikan", 0f..1f, ::pct) },
                    )
                    if (distort) Two({ S(state, 3, "Sesuaikan", 0f..1f, ::pct) }, {})
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Hint("Seret di kanvas · dua jari untuk geser/perbesar", Modifier.weight(1f))
                        TextButton(onClick = { state.liquifyReset() }) { Text("Atur ulang") }
                    }
                }
                Adj.CLONE -> {
                    Hint("Geser lingkaran sumber dengan jari (atau ketuk \"Atur sumber\" lalu ketuk kanvas), lalu gambar dengan kuas untuk menyalin dari sumber.")
                    Spacer(Modifier.height(4.dp))
                    FilterChip(
                        selected = state.cloneArmed,
                        onClick = { state.cloneArmed = !state.cloneArmed },
                        label = { Text("Atur sumber") },
                        colors = FilterChipDefaults.filterChipColors(selectedContainerColor = Color(0x553D8BFF)),
                    )
                }
            }
        }
    }
}

@Composable
private fun S(
    state: AppState,
    i: Int,
    label: String,
    range: ClosedFloatingPointRange<Float>,
    display: (Float) -> String,
    steps: Int = 0,
) {
    val v = state.adjV.getOrElse(i) { 0f }
    if (steps > 0) {
        LabeledSlider(label, v, { state.setAdj(i, it.roundToInt().toFloat()) }, range, display)
    } else {
        LabeledSlider(label, v, { state.setAdj(i, it) }, range, display)
    }
}

/**
 * Penggeser dengan skala kuadrat: setengah pertama untuk nilai kecil yang perlu teliti, ujung kanan untuk efek yang
 * sangat kuat (mis. blur ratusan piksel).
 */
@Composable
private fun SQ(
    state: AppState,
    i: Int,
    label: String,
    lo: Float,
    hi: Float,
    display: (Float) -> String,
) {
    val v = state.adjV.getOrElse(i) { lo }
    val span = max(1e-3f, hi - lo)
    val t = kotlin.math.sqrt(((v - lo) / span).coerceIn(0f, 1f))
    LabeledSlider(label, t, { state.setAdj(i, lo + span * it * it) }, 0f..1f, { display(lo + span * it * it) })
}

/**
 * Bar penyesuaian yang disembunyikan: hanya satu baris kecil (judul, Hapus efek untuk mode Pena, tombol untuk
 * menampilkan panel lagi, Batal/Terapkan), supaya kanvas tidak tertutup saat menyapu.
 */
@Composable
private fun AdjustMini(state: AppState, modifier: Modifier) {
    val k = state.adjustKind
    Surface(color = PanelBg, shape = RoundedCornerShape(24.dp), modifier = modifier, shadowElevation = 6.dp) {
        Row(Modifier.padding(start = 16.dp, end = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(adjTitles[k] ?: "", color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
            if (state.adjPenActive) {
                Spacer(Modifier.width(10.dp))
                FilterChip(
                    selected = state.adjPenErase,
                    onClick = { state.adjPenErase = !state.adjPenErase },
                    label = { Text("Hapus efek") },
                    colors = FilterChipDefaults.filterChipColors(selectedContainerColor = Color(0x553D8BFF)),
                )
            }
            Spacer(Modifier.width(4.dp))
            TextButton(onClick = { state.adjCollapsed = false }) {
                Icon(Icons.Filled.ExpandMore, null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(4.dp))
                Text("Atur")
            }
            if (k == Adj.CLONE) {
                TextButton(onClick = { state.adjustDone(true) }) { Text("Selesai", fontWeight = FontWeight.SemiBold) }
            } else {
                TextButton(onClick = { state.adjustDone(false) }) { Text("Batal") }
                TextButton(onClick = { state.adjustDone(true) }) { Text("Terapkan", fontWeight = FontWeight.SemiBold) }
            }
        }
    }
}

@Composable
private fun Two(a: @Composable () -> Unit, b: @Composable () -> Unit) {
    Row(Modifier.fillMaxWidth()) {
        Box(Modifier.weight(1f)) { a() }
        Spacer(Modifier.width(14.dp))
        Box(Modifier.weight(1f)) { b() }
    }
}

/** Pilihan mode Layer / Pena untuk penyesuaian (kecuali Liquify dan Klon). */
@Composable
private fun PenModeRow(state: AppState) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.weight(1f)) {
            Chips(listOf("Seluruh layer", "Pena"), if (state.adjPen) 1 else 0) { state.switchAdjPen(it == 1) }
        }
        if (state.adjPen) {
            FilterChip(
                selected = state.adjPenErase,
                onClick = { state.adjPenErase = !state.adjPenErase },
                label = { Text("Hapus efek") },
                colors = FilterChipDefaults.filterChipColors(selectedContainerColor = Color(0x553D8BFF)),
            )
            TextButton(onClick = { state.penClear() }) { Text("Hapus semua") }
        }
    }
    if (state.adjPen) Hint("Sapukan pena di bagian yang ingin diberi efek · ukuran dan kekuatan sapuan dari penggeser samping")
}

@Composable
private fun CenterHint(state: AppState) {
    Hint(if (state.adjPen) "Titik pusat dipindah di mode Seluruh layer" else "Ketuk atau seret di kanvas untuk memindah titik pusat")
}

@Composable
private fun Hint(text: String, modifier: Modifier = Modifier) {
    Text(text, color = Muted, fontSize = 12.sp, modifier = modifier.padding(vertical = 2.dp))
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Chips(options: List<String>, selected: Int, onSelect: (Int) -> Unit) {
    FlowRow(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        options.forEachIndexed { i, label ->
            FilterChip(
                selected = i == selected,
                onClick = { onSelect(i) },
                label = { Text(label) },
                colors = FilterChipDefaults.filterChipColors(selectedContainerColor = Color(0x553D8BFF)),
            )
        }
    }
}

@Composable
private fun GradientPresets(state: AppState) {
    val all = (0..Gradients.presets.size).toList()
    Column(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        for (row in all.chunked(5)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                for (i in row) {
                    val p = state.gradientPreset(i)
                    val sel = state.adjMode == i
                    Column(
                        Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(8.dp))
                            .clickable { state.chooseAdjMode(i) }
                            .padding(2.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .height(28.dp)
                                .clip(RoundedCornerShape(6.dp))
                                .background(Brush.horizontalGradient(colorStops = Array(p.colors.size) { p.stops[it] to Color(p.colors[it]) }))
                                .border(if (sel) 2.dp else 1.dp, if (sel) Accent else Color(0x33FFFFFF), RoundedCornerShape(6.dp)),
                        )
                        Text(p.name, color = if (sel) Color.White else Muted, fontSize = 11.sp)
                    }
                }
                repeat(5 - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

/**
 * Editor kurva: ketuk untuk menambah titik, seret untuk memindah. Titik terpilih bisa dihapus.
 * Koordinat titik 0..1, y ke atas.
 */
@Composable
private fun CurveEditor(state: AppState) {
    val ch = state.adjTab
    val pts = state.curvePts[ch]
    val selState = remember(ch) { mutableIntStateOf(-1) }
    var sel by selState
    val line = when (ch) {
        1 -> Color(0xFFFF6B6B)
        2 -> Color(0xFF63D47A)
        3 -> Color(0xFF6B9BFF)
        else -> Color.White
    }
    Box(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp)
            .height(200.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(Color(0x1FFFFFFF))
            .pointerInput(ch, remember(ch) { curveGesture(state, pts, selState) }),
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height
            val grid = Color(0x22FFFFFF)
            for (q in 1..3) {
                drawLine(grid, Offset(w * q / 4f, 0f), Offset(w * q / 4f, h))
                drawLine(grid, Offset(0f, h * q / 4f), Offset(w, h * q / 4f))
            }
            drawLine(Color(0x33FFFFFF), Offset(0f, h), Offset(w, 0f))
            val t = Curves.table(FloatArray(pts.size) { pts[it].x }, FloatArray(pts.size) { pts[it].y })
            val path = Path()
            for (i in 0 until 256) {
                val x = i / 255f * w
                val y = (1f - t[i]) * h
                if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
            }
            drawPath(path, line, style = Stroke(width = 2.dp.toPx()))
            pts.forEachIndexed { i, p ->
                val c = Offset(p.x * w, (1f - p.y) * h)
                drawCircle(Color.Black, 7.dp.toPx(), c)
                drawCircle(if (i == sel) Accent else Color.White, 5.dp.toPx(), c)
            }
        }
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Hint("Ketuk untuk menambah titik, seret untuk mengubah kurva", Modifier.weight(1f))
        TextButton(
            enabled = sel in 1 until pts.lastIndex,
            onClick = {
                if (sel in 1 until pts.lastIndex) {
                    pts.removeAt(sel)
                    sel = -1
                    state.adjustChanged()
                }
            },
        ) { Text("Hapus titik") }
        TextButton(onClick = {
            pts.clear()
            pts.add(Offset(0f, 0f))
            pts.add(Offset(1f, 1f))
            sel = -1
            state.adjustChanged()
        }) { Text("Atur ulang") }
    }
}

/** Gerakan di editor kurva. Dibuat sekali per kanal supaya tidak diulang saat editor digambar ulang di tengah seretan. */
private fun curveGesture(
    state: AppState,
    pts: androidx.compose.runtime.snapshots.SnapshotStateList<Offset>,
    selState: androidx.compose.runtime.MutableIntState,
): suspend androidx.compose.ui.input.pointer.PointerInputScope.() -> Unit = {
    awaitEachGesture {
        val down = awaitFirstDown()
        val w = size.width.toFloat()
        val h = size.height.toFloat()
        fun norm(o: Offset) = Offset((o.x / w).coerceIn(0f, 1f), (1f - o.y / h).coerceIn(0f, 1f))
        var idx = -1
        var best = 28.dp.toPx()
        for (i in pts.indices) {
            val d = hypot(pts[i].x * w - down.position.x, (1f - pts[i].y) * h - down.position.y)
            if (d < best) { best = d; idx = i }
        }
        if (idx < 0) {
            val n = norm(down.position)
            var at = pts.indexOfFirst { it.x > n.x }
            if (at < 0) at = pts.size
            val tooClose = pts.any { kotlin.math.abs(it.x - n.x) < 0.02f }
            if (!tooClose) {
                pts.add(at, n)
                idx = at
                state.adjustChanged()
            }
        }
        selState.intValue = idx
        down.consume()
        if (idx < 0) return@awaitEachGesture
        while (true) {
            val ev = awaitPointerEvent()
            val c = ev.changes.firstOrNull { it.id == down.id } ?: break
            if (!c.pressed) break
            c.consume()
            val n = norm(c.position)
            val lo = if (idx > 0) pts[idx - 1].x + 0.01f else 0f
            val hi = if (idx < pts.lastIndex) pts[idx + 1].x - 0.01f else 1f
            pts[idx] = Offset(n.x.coerceIn(lo, max(lo, hi)), n.y)
            state.adjustChanged()
        }
    }
}
