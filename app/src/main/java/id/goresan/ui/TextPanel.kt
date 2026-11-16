package id.goresan.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import id.goresan.engine.Fonts
import id.goresan.engine.TextSpec
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

private val TextTabs = listOf("Font", "Gaya", "Efek", "Bentuk")
private val Shapes = listOf("Lurus", "Lengkung", "Gelombang", "Lingkaran", "Jalur bebas")
private val QuickColors = listOf(
    0xFF000000.toInt(), 0xFFFFFFFF.toInt(), 0xFFE53935.toInt(), 0xFFFFC107.toInt(), 0xFF43A047.toInt(),
    0xFF1E88E5.toInt(), 0xFF8E24AA.toInt(), 0xFFFF7043.toInt(), 0xFF6D4C41.toInt(), 0xFFB0BEC5.toInt(),
)

/**
 * Layar Teks: ketik teks, pilih font (komik, artistik, eksklusif, dasar), atur ukuran/warna/tebal/miring/rata/jarak,
 * garis tepi dan bayangan, serta bentuk baris (lurus, lengkung, gelombang, lingkaran, jalur bebas). Di kanvas:
 * seret teks untuk memindah, seret titik biru untuk memutar dan mengubah ukuran; dua jari menggeser kanvas.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TextEditor(state: AppState) {
    val s = state.textSpec
    var tab by remember { mutableIntStateOf(0) }
    val imeOpen = WindowInsets.ime.getBottom(LocalDensity.current) > 0
    val focus = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    val focusReq = remember { FocusRequester() }
    // Teks baru: kolom langsung siap diketik.
    LaunchedEffect(Unit) { if (state.textSpec.text.isEmpty()) runCatching { focusReq.requestFocus() } }
    fun closeKeyboard() {
        focus.clearFocus()
        keyboard?.hide()
    }
    Box(Modifier.fillMaxSize()) {
        // Atas: judul + kolom teks. Selama mengetik, panel bawah disembunyikan supaya teks di kanvas tetap terlihat.
        Column(
            Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.statusBars)
                .pointerInput(Unit) { detectTapGestures { } },
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .background(BarBg)
                    .height(52.dp)
                    .padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Teks", color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
                Spacer(Modifier.width(12.dp))
                Text(
                    if (state.textDrawPath) "Gambar jalur di kanvas, teks akan mengikutinya" else "Seret teks untuk memindah · titik biru: putar & ukuran",
                    color = Muted, fontSize = 12.sp, maxLines = 1, modifier = Modifier.weight(1f),
                )
                TextButton(onClick = { closeKeyboard(); state.cancelText() }) { Text("Batal", color = Muted) }
                Button(onClick = { closeKeyboard(); state.commitText() }, colors = ButtonDefaults.buttonColors(containerColor = Accent)) { Text("Selesai") }
            }
            Row(
                Modifier
                    .padding(top = 8.dp, start = 12.dp, end = 12.dp)
                    .widthIn(max = 860.dp)
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(PanelBg)
                    .padding(6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                BasicTextField(
                    value = s.text,
                    onValueChange = { state.updateText(state.textSpec.copy(text = it)) },
                    textStyle = TextStyle(color = Color.White, fontSize = 16.sp),
                    cursorBrush = SolidColor(Accent),
                    maxLines = 3,
                    modifier = Modifier
                        .weight(1f)
                        .focusRequester(focusReq)
                        .clip(RoundedCornerShape(10.dp))
                        .background(Color(0x22FFFFFF))
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    decorationBox = { inner ->
                        if (s.text.isEmpty()) Text("Ketik teks… (Enter = baris baru)", color = Muted, fontSize = 16.sp)
                        inner()
                    },
                )
                if (imeOpen) {
                    TextButton(onClick = { closeKeyboard() }) { Text("Tutup keyboard", color = Accent) }
                }
            }
        }

        if (!imeOpen) Surface(
            color = PanelBg,
            shape = RoundedCornerShape(18.dp),
            shadowElevation = 8.dp,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .windowInsetsPadding(WindowInsets.navigationBars)
                .padding(12.dp)
                .widthIn(max = 860.dp)
                .fillMaxWidth()
                .pointerInput(Unit) { detectTapGestures { } },
        ) {
            BoxWithConstraints {
                val wide = maxWidth >= 600.dp
                Column(Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        TextTabs.forEachIndexed { i, t -> Chip(t, tab == i) { tab = i } }
                    }
                    Spacer(Modifier.height(8.dp))
                    Column(Modifier.heightIn(max = 260.dp).verticalScroll(rememberScrollState())) {
                        when (tab) {
                            0 -> FontTab(state, s)
                            1 -> StyleTab(state, s, wide)
                            2 -> EffectTab(state, s, wide)
                            else -> ShapeTab(state, s, wide)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun Chip(label: String, on: Boolean, onClick: () -> Unit) {
    Box(
        Modifier
            .clip(RoundedCornerShape(10.dp))
            .background(if (on) Accent else Color(0x22FFFFFF))
            .clickable { onClick() }
            .padding(horizontal = 12.dp, vertical = 7.dp),
    ) {
        Text(label, color = Color.White, fontSize = 13.sp, fontWeight = if (on) FontWeight.SemiBold else FontWeight.Normal)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FontTab(state: AppState, s: TextSpec) {
    val ctx = LocalContext.current
    var cat by remember { mutableStateOf(Fonts.def(s.font).category) }
    val families = remember { HashMap<String, FontFamily>() }
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        for (c in Fonts.categories) Chip(c, cat == c) { cat = c }
    }
    Spacer(Modifier.height(8.dp))
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        for (f in Fonts.all.filter { it.category == cat }) {
            val fam = families.getOrPut(f.id) {
                FontFamily(androidx.compose.ui.text.font.Typeface(Fonts.typeface(ctx, f.id, false, false)))
            }
            val on = s.font == f.id
            Box(
                Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .background(Color(0x18FFFFFF))
                    .border(if (on) 2.dp else 1.dp, if (on) Accent else Color(0x33FFFFFF), RoundedCornerShape(10.dp))
                    .clickable { state.updateText(state.textSpec.copy(font = f.id)) }
                    .padding(horizontal = 12.dp, vertical = 6.dp),
            ) {
                Text(f.name, color = Color.White, fontSize = 20.sp, fontFamily = fam, maxLines = 1)
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun StyleTab(state: AppState, s: TextSpec, wide: Boolean) {
    val maxSize = max(60f, min(1500f, min(state.docW, state.docH) * 0.8f))
    var picker by remember { mutableStateOf(false) }
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        StyleToggle("B", s.bold, FontWeight.Bold) { state.updateText(state.textSpec.copy(bold = it)) }
        StyleToggle("I", s.italic, style = FontStyle.Italic) { state.updateText(state.textSpec.copy(italic = it)) }
        StyleToggle("U", s.underline, deco = TextDecoration.Underline) { state.updateText(state.textSpec.copy(underline = it)) }
        StyleToggle("AA", s.caps) { state.updateText(state.textSpec.copy(caps = it)) }
        Spacer(Modifier.width(8.dp))
        Chip("Kiri", s.align == 0) { state.updateText(state.textSpec.copy(align = 0)) }
        Chip("Tengah", s.align == 1) { state.updateText(state.textSpec.copy(align = 1)) }
        Chip("Kanan", s.align == 2) { state.updateText(state.textSpec.copy(align = 2)) }
    }
    Spacer(Modifier.height(6.dp))
    TwoCol(wide, {
        LabeledSlider("Ukuran huruf", s.size.coerceIn(8f, maxSize), { state.updateText(state.textSpec.copy(size = it.roundToInt().toFloat())) }, 8f..maxSize, { "${it.roundToInt()} px" })
    }, {
        LabeledSlider("Skala", s.scale.coerceIn(0.1f, 5f), { state.updateText(state.textSpec.copy(scale = it)) }, 0.1f..5f, { "${(it * 100).roundToInt()}%" })
    })
    TwoCol(wide, {
        LabeledSlider("Jarak huruf", s.spacing.coerceIn(-0.1f, 1f), { state.updateText(state.textSpec.copy(spacing = it)) }, -0.1f..1f, { "${(it * 100).roundToInt()}" })
    }, {
        LabeledSlider("Jarak baris", s.lineHeight.coerceIn(0.6f, 3f), { state.updateText(state.textSpec.copy(lineHeight = it)) }, 0.6f..3f, { String.format(java.util.Locale.US, "%.2f", it) })
    })
    Text("Warna", color = Color.White, fontSize = 13.sp, modifier = Modifier.padding(top = 4.dp, bottom = 6.dp))
    ColorRow(state, s.color, { state.updateText(state.textSpec.copy(color = it)) }, onMore = { picker = !picker })
    if (picker) {
        Spacer(Modifier.height(8.dp))
        CompactColorPicker(s.color, { state.updateText(state.textSpec.copy(color = it)) }, emptyList(), width = 280.dp)
    }
}

@Composable
private fun EffectTab(state: AppState, s: TextSpec, wide: Boolean) {
    var picker by remember { mutableIntStateOf(0) }
    val maxOutline = max(4f, s.size * 0.25f)
    Text("Garis tepi", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
    LabeledSlider("Tebal garis tepi", s.outline.coerceIn(0f, maxOutline), { state.updateText(state.textSpec.copy(outline = it)) }, 0f..maxOutline, { "${it.roundToInt()} px" })
    ColorRow(state, s.outlineColor, { state.updateText(state.textSpec.copy(outlineColor = it, outline = if (state.textSpec.outline <= 0f) state.textSpec.size * 0.06f else state.textSpec.outline)) }, onMore = { picker = if (picker == 1) 0 else 1 })
    if (picker == 1) CompactColorPicker(s.outlineColor, { state.updateText(state.textSpec.copy(outlineColor = it)) }, emptyList(), width = 280.dp)
    Spacer(Modifier.height(10.dp))
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text("Bayangan", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
        Switch(checked = s.shadow, onCheckedChange = { state.updateText(state.textSpec.copy(shadow = it)) }, colors = SwitchDefaults.colors(checkedTrackColor = Accent))
    }
    if (s.shadow) {
        TwoCol(wide, {
            LabeledSlider("Geser kanan", s.shadowDx.coerceIn(-0.5f, 0.5f), { state.updateText(state.textSpec.copy(shadowDx = it)) }, -0.5f..0.5f, { "${(it * 100).roundToInt()}" })
        }, {
            LabeledSlider("Geser bawah", s.shadowDy.coerceIn(-0.5f, 0.5f), { state.updateText(state.textSpec.copy(shadowDy = it)) }, -0.5f..0.5f, { "${(it * 100).roundToInt()}" })
        })
        LabeledSlider("Kabur", s.shadowBlur.coerceIn(0f, 0.5f), { state.updateText(state.textSpec.copy(shadowBlur = it)) }, 0f..0.5f, { "${(it * 100).roundToInt()}" })
        ColorRow(state, s.shadowColor or 0xFF000000.toInt(), { c -> state.updateText(state.textSpec.copy(shadowColor = (c and 0x00FFFFFF) or (0x99 shl 24))) }, onMore = { picker = if (picker == 2) 0 else 2 })
        if (picker == 2) CompactColorPicker(s.shadowColor or 0xFF000000.toInt(), { c -> state.updateText(state.textSpec.copy(shadowColor = (c and 0x00FFFFFF) or (0x99 shl 24))) }, emptyList(), width = 280.dp)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ShapeTab(state: AppState, s: TextSpec, wide: Boolean) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Shapes.forEachIndexed { i, label ->
            Chip(label, s.shape == i) {
                if (i == 4) {
                    // Jalur bebas: gambar jalurnya di kanvas.
                    state.textDrawPath = true
                    if (state.textSpec.path.size >= 4) state.updateText(state.textSpec.copy(shape = 4))
                } else {
                    state.textDrawPath = false
                    state.updateText(state.textSpec.copy(shape = i))
                }
            }
        }
    }
    Spacer(Modifier.height(6.dp))
    when (s.shape) {
        1 -> LabeledSlider(
            "Lengkungan (− ke bawah, + ke atas)", s.curve.coerceIn(-1f, 1f),
            { state.updateText(state.textSpec.copy(curve = if (kotlin.math.abs(it) < 0.03f) 0f else it)) }, -1f..1f,
            { "${(it * 100).roundToInt()}" },
        )
        2 -> TwoCol(wide, {
            LabeledSlider("Tinggi gelombang", s.waveAmp.coerceIn(0f, 1.5f), { state.updateText(state.textSpec.copy(waveAmp = it)) }, 0f..1.5f, { "${(it * 100).roundToInt()}" })
        }, {
            LabeledSlider("Panjang gelombang", s.waveLen.coerceIn(1f, 12f), { state.updateText(state.textSpec.copy(waveLen = it)) }, 1f..12f, { String.format(java.util.Locale.US, "%.1f", it) })
        })
        3, 4 -> Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                if (s.shape == 3) "Balik sisi (teks di bagian bawah lingkaran)" else "Balik sisi (huruf di seberang jalur)",
                color = Color.White, fontSize = 13.sp, modifier = Modifier.weight(1f),
            )
            Switch(checked = s.flip, onCheckedChange = { state.updateText(state.textSpec.copy(flip = it)) }, colors = SwitchDefaults.colors(checkedTrackColor = Accent))
        }
        else -> Text("Baris lurus. Pilih Lengkung, Gelombang, Lingkaran, atau Jalur bebas untuk membentuk baris.", color = Muted, fontSize = 12.sp)
    }
    if (s.shape == 4 || state.textDrawPath) {
        Row(Modifier.padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(
                if (state.textDrawPath) "Gambar jalur di kanvas dengan jari atau pena…" else "Jalur bisa digambar ulang kapan saja.",
                color = Muted, fontSize = 12.sp, modifier = Modifier.weight(1f),
            )
            TextButton(onClick = { state.textDrawPath = !state.textDrawPath }) {
                Text(if (state.textDrawPath) "Batal gambar" else "Gambar ulang jalur", color = Accent)
            }
        }
    }
}

@Composable
private fun TwoCol(wide: Boolean, a: @Composable () -> Unit, b: @Composable () -> Unit) {
    if (wide) {
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Box(Modifier.weight(1f)) { a() }
            Box(Modifier.weight(1f)) { b() }
        }
    } else {
        a()
        b()
    }
}

@Composable
private fun StyleToggle(
    label: String,
    on: Boolean,
    weight: FontWeight = FontWeight.Normal,
    style: FontStyle = FontStyle.Normal,
    deco: TextDecoration? = null,
    onChange: (Boolean) -> Unit,
) {
    Box(
        Modifier
            .size(width = 44.dp, height = 36.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(if (on) Accent else Color(0x22FFFFFF))
            .clickable { onChange(!on) },
        contentAlignment = Alignment.Center,
    ) {
        Text(label, color = Color.White, fontSize = 15.sp, fontWeight = weight, fontStyle = style, textDecoration = deco)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ColorRow(state: AppState, current: Int, onPick: (Int) -> Unit, onMore: () -> Unit) {
    val colors = (listOf(state.color) + QuickColors).distinct()
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        for (c in colors) {
            val on = (c or 0xFF000000.toInt()) == (current or 0xFF000000.toInt())
            Box(
                Modifier
                    .size(30.dp)
                    .clip(CircleShape)
                    .background(Color(c or 0xFF000000.toInt()))
                    .border(if (on) 2.5.dp else 1.dp, if (on) Accent else Color(0x55FFFFFF), CircleShape)
                    .clickable { onPick(c or 0xFF000000.toInt()) },
            )
        }
        Box(
            Modifier
                .height(30.dp)
                .clip(RoundedCornerShape(15.dp))
                .background(Color(0x22FFFFFF))
                .clickable { onMore() }
                .padding(horizontal = 12.dp),
            contentAlignment = Alignment.Center,
        ) { Text("Warna lain…", color = Color.White, fontSize = 12.sp) }
    }
}
