package id.goresan.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

// ================= Pangkas & ubah ukuran =================

/**
 * Layar pangkas & ubah ukuran kanvas. Mode pangkas: bingkai bisa digeser/ditarik (juga keluar kanvas untuk
 * memperluas). Mode skala: isi kanvas diskalakan ke lebar × tinggi baru.
 */
@Composable
fun CropResizeScreen(state: AppState) {
    if (!state.cropOpen) return
    val w0 = state.docW
    val h0 = state.docH
    var scaleMode by remember { mutableStateOf(false) }
    var lock by remember { mutableStateOf(false) }
    var x0 by remember { mutableFloatStateOf(0f) }
    var y0 by remember { mutableFloatStateOf(0f) }
    var x1 by remember { mutableFloatStateOf(w0.toFloat()) }
    var y1 by remember { mutableFloatStateOf(h0.toFloat()) }
    var sw by remember { mutableIntStateOf(w0) }
    var sh by remember { mutableIntStateOf(h0) }
    val top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val snap = state.cropSnap
    val img = remember(snap) { snap?.asImageBitmap() }
    val ratio = if (scaleMode) w0.toFloat() / h0 else (x1 - x0) / max(1f, y1 - y0)

    fun setCropW(v: Int) {
        val w = v.coerceIn(16, 16384).toFloat()
        val h = if (lock) w / ratio else y1 - y0
        x1 = x0 + w
        y1 = y0 + h
    }
    fun setCropH(v: Int) {
        val h = v.coerceIn(16, 16384).toFloat()
        val w = if (lock) h * ratio else x1 - x0
        y1 = y0 + h
        x1 = x0 + w
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(Color(0xF2141418))
            .pointerInput(Unit) { detectTapGestures { } },
    ) {
        Column(Modifier.fillMaxSize().padding(top = top)) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(onClick = { state.cropOpen = false; state.cropSnap = null }) { Text("Batal", color = Color.White) }
                Spacer(Modifier.weight(1f))
                Text("Pangkas & ubah ukuran", color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 17.sp)
                Spacer(Modifier.weight(1f))
                TextButton(onClick = {
                    if (scaleMode) {
                        state.applyCrop(sw, sh, 0, 0, true)
                    } else {
                        val nw = (x1 - x0).roundToInt()
                        val nh = (y1 - y0).roundToInt()
                        state.applyCrop(nw, nh, -x0.roundToInt(), -y0.roundToInt(), false)
                    }
                }) { Text("Terapkan", color = Accent, fontWeight = FontWeight.SemiBold) }
            }
            Row(Modifier.weight(1f).fillMaxWidth()) {
                // ---- pratinjau dan bingkai ----
                BoxWithConstraints(Modifier.weight(1f).fillMaxHeight().padding(20.dp)) {
                    val bw = constraints.maxWidth.toFloat()
                    val bh = constraints.maxHeight.toFloat()
                    // Wilayah tampil: kanvas + 25% di tiap sisi (bingkai boleh keluar kanvas untuk memperluas).
                    val m = if (scaleMode) 0f else 0.25f
                    val ex0 = -w0 * m
                    val ey0 = -h0 * m
                    val ew = w0 * (1 + 2 * m)
                    val eh = h0 * (1 + 2 * m)
                    val k = min(bw / ew, bh / eh)
                    val offX = (bw - ew * k) / 2f
                    val offY = (bh - eh * k) / 2f
                    fun sx(cx: Float) = offX + (cx - ex0) * k
                    fun sy(cy: Float) = offY + (cy - ey0) * k
                    val handleR = with(LocalDensity.current) { 22.dp.toPx() }
                    var grab by remember { mutableIntStateOf(0) }
                    Canvas(
                        Modifier
                            .fillMaxSize()
                            .pointerInput(scaleMode, k, offX, offY) {
                                if (scaleMode) return@pointerInput
                                detectDragGestures(
                                    onDragStart = { p ->
                                        val l = sx(x0); val t = sy(y0); val r = sx(x1); val b = sy(y1)
                                        val nl = abs(p.x - l) < handleR
                                        val nr = abs(p.x - r) < handleR
                                        val nt = abs(p.y - t) < handleR
                                        val nb = abs(p.y - b) < handleR
                                        val inX = p.x > l - handleR && p.x < r + handleR
                                        val inY = p.y > t - handleR && p.y < b + handleR
                                        // bit: 1 kiri, 2 kanan, 4 atas, 8 bawah, 16 geser
                                        var g = 0
                                        if (nl && inY) g = g or 1
                                        if (nr && inY && !nl) g = g or 2
                                        if (nt && inX) g = g or 4
                                        if (nb && inX && !nt) g = g or 8
                                        if (g == 0 && p.x in l..r && p.y in t..b) g = 16
                                        grab = g
                                    },
                                    onDragEnd = { grab = 0 },
                                    onDragCancel = { grab = 0 },
                                ) { ch, d ->
                                    ch.consume()
                                    val dx = d.x / k
                                    val dy = d.y / k
                                    val g = grab
                                    val minS = 16f
                                    if (g == 16) {
                                        x0 += dx; x1 += dx; y0 += dy; y1 += dy
                                    } else {
                                        if (g and 1 != 0) x0 = min(x0 + dx, x1 - minS)
                                        if (g and 2 != 0) x1 = max(x1 + dx, x0 + minS)
                                        if (g and 4 != 0) y0 = min(y0 + dy, y1 - minS)
                                        if (g and 8 != 0) y1 = max(y1 + dy, y0 + minS)
                                        if (lock && g != 0) {
                                            // Rasio dikunci: tinggi mengikuti lebar (atau sebaliknya bila hanya tepi atas/bawah).
                                            val r0 = ratio
                                            if (g and 3 != 0) {
                                                val h = (x1 - x0) / r0
                                                if (g and 4 != 0) y0 = y1 - h else y1 = y0 + h
                                            } else if (g and 12 != 0) {
                                                val w = (y1 - y0) * r0
                                                x1 = x0 + w
                                            }
                                        }
                                    }
                                    // Tempel ke tepi kanvas bila dekat (10 px layar).
                                    val tol = 10f / k
                                    if (abs(x0) < tol) { if (g == 16) x1 -= x0; x0 = 0f }
                                    if (abs(y0) < tol) { if (g == 16) y1 -= y0; y0 = 0f }
                                    if (abs(x1 - w0) < tol) { if (g == 16) x0 += w0 - x1; x1 = w0.toFloat() }
                                    if (abs(y1 - h0) < tol) { if (g == 16) y0 += h0 - y1; y1 = h0.toFloat() }
                                }
                            },
                    ) {
                        // kotak-kotak = bagian di luar kanvas (transparan bila diperluas)
                        drawRect(Color(0xFF2A2A30), Offset(sx(ex0), sy(ey0)), Size(ew * k, eh * k))
                        val cl = sx(0f); val ct = sy(0f)
                        drawRect(Color.White, Offset(cl, ct), Size(w0 * k, h0 * k))
                        if (img != null) {
                            drawImage(
                                img,
                                dstOffset = IntOffset(cl.roundToInt(), ct.roundToInt()),
                                dstSize = IntSize((w0 * k).roundToInt(), (h0 * k).roundToInt()),
                            )
                        }
                        if (!scaleMode) {
                            val l = sx(x0); val t = sy(y0); val r = sx(x1); val b = sy(y1)
                            val dim = Color(0x99000000)
                            // gelapkan di luar bingkai
                            drawRect(dim, Offset(0f, 0f), Size(size.width, t.coerceAtLeast(0f)))
                            drawRect(dim, Offset(0f, b), Size(size.width, (size.height - b).coerceAtLeast(0f)))
                            drawRect(dim, Offset(0f, t), Size(l.coerceAtLeast(0f), b - t))
                            drawRect(dim, Offset(r, t), Size((size.width - r).coerceAtLeast(0f), b - t))
                            drawRect(Color.White, Offset(l, t), Size(r - l, b - t), style = Stroke(2.dp.toPx()))
                            // garis sepertiga
                            for (i in 1..2) {
                                val gx = l + (r - l) * i / 3f
                                val gy = t + (b - t) * i / 3f
                                drawLine(Color(0x66FFFFFF), Offset(gx, t), Offset(gx, b), 1.dp.toPx())
                                drawLine(Color(0x66FFFFFF), Offset(l, gy), Offset(r, gy), 1.dp.toPx())
                            }
                            val hs = 7.dp.toPx()
                            for (px in listOf(l, (l + r) / 2f, r)) for (py in listOf(t, (t + b) / 2f, b)) {
                                if (px == (l + r) / 2f && py == (t + b) / 2f) continue
                                drawCircle(Color.White, hs, Offset(px, py))
                                drawCircle(Accent, hs - 2.dp.toPx(), Offset(px, py))
                            }
                        }
                    }
                    if (img == null) CircularProgressIndicator(Modifier.align(Alignment.Center), color = Accent)
                }
                // ---- pengaturan ----
                Column(
                    Modifier.width(300.dp).fillMaxHeight().verticalScroll(rememberScrollState()).padding(end = 16.dp, top = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Row(
                        Modifier.clip(RoundedCornerShape(8.dp)).clickable { scaleMode = !scaleMode; if (scaleMode) lock = true },
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text("Ubah skala isi", color = Color.White, fontSize = 14.sp)
                            Text("Isi kanvas ikut membesar/mengecil (resample)", color = Muted, fontSize = 11.sp)
                        }
                        Switch(
                            checked = scaleMode,
                            onCheckedChange = { scaleMode = it; if (it) lock = true },
                            colors = SwitchDefaults.colors(checkedTrackColor = Accent),
                        )
                    }
                    Row(
                        Modifier.clip(RoundedCornerShape(8.dp)).clickable { lock = !lock },
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text("Kunci rasio", color = Color.White, fontSize = 14.sp, modifier = Modifier.weight(1f))
                        Switch(checked = lock, onCheckedChange = { lock = it }, colors = SwitchDefaults.colors(checkedTrackColor = Accent))
                    }
                    if (scaleMode) {
                        NumField("Lebar (px)", sw) { v ->
                            sw = v.coerceIn(16, 16384)
                            if (lock) sh = (sw / ratio).roundToInt().coerceIn(16, 16384)
                        }
                        NumField("Tinggi (px)", sh) { v ->
                            sh = v.coerceIn(16, 16384)
                            if (lock) sw = (sh * ratio).roundToInt().coerceIn(16, 16384)
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            for (f in listOf(50, 200)) {
                                Chip("$f%") {
                                    sw = (w0 * f / 100).coerceIn(16, 16384)
                                    sh = (h0 * f / 100).coerceIn(16, 16384)
                                }
                            }
                            Chip("Asli") { sw = w0; sh = h0 }
                        }
                    } else {
                        NumField("Lebar (px)", (x1 - x0).roundToInt()) { setCropW(it) }
                        NumField("Tinggi (px)", (y1 - y0).roundToInt()) { setCropH(it) }
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            NumField("Geser X", x0.roundToInt(), Modifier.weight(1f), allowNeg = true) { v ->
                                val w = x1 - x0; x0 = v.toFloat(); x1 = x0 + w
                            }
                            NumField("Geser Y", y0.roundToInt(), Modifier.weight(1f), allowNeg = true) { v ->
                                val h = y1 - y0; y0 = v.toFloat(); y1 = y0 + h
                            }
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Chip("Ulang") { x0 = 0f; y0 = 0f; x1 = w0.toFloat(); y1 = h0.toFloat() }
                            Chip("Persegi") {
                                val s = min(x1 - x0, y1 - y0)
                                val cx = (x0 + x1) / 2f; val cy = (y0 + y1) / 2f
                                x0 = cx - s / 2f; x1 = cx + s / 2f; y0 = cy - s / 2f; y1 = cy + s / 2f
                            }
                            Chip("Tengah") {
                                val w = x1 - x0; val h = y1 - y0
                                x0 = (w0 - w) / 2f; x1 = x0 + w; y0 = (h0 - h) / 2f; y1 = y0 + h
                            }
                        }
                        Text(
                            "Tarik tepi/sudut bingkai untuk memotong, atau tarik keluar kanvas untuk memperluas (bagian baru transparan). " +
                                "Tarik di dalam bingkai untuk menggeser.",
                            color = Muted, fontSize = 11.sp,
                        )
                    }
                    val nw = if (scaleMode) sw else (x1 - x0).roundToInt()
                    val nh = if (scaleMode) sh else (y1 - y0).roundToInt()
                    val dpi = state.docDpi.coerceAtLeast(1)
                    Text(
                        "Sekarang $w0 × $h0 px → baru $nw × $nh px\nCetak ${cm(nw, dpi)} × ${cm(nh, dpi)} cm pada $dpi DPI",
                        color = Color.White, fontSize = 12.sp,
                    )
                    Text(
                        "Riwayat urungkan dikosongkan setelah ukuran kanvas diubah.",
                        color = Color(0xFFFFB74D), fontSize = 11.sp,
                    )
                }
            }
        }
    }
}

private fun cm(px: Int, dpi: Int) = String.format(Locale.US, "%.1f", px / dpi.toFloat() * 2.54f).replace('.', ',')

@Composable
private fun Chip(label: String, onClick: () -> Unit) {
    Text(
        label,
        color = Color.White,
        fontSize = 12.sp,
        modifier = Modifier
            .clip(RoundedCornerShape(14.dp))
            .background(Color(0xFF3A3A42))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 6.dp),
    )
}

/** Kolom angka: nilai diterapkan saat isinya angka yang sah. */
@Composable
private fun NumField(label: String, value: Int, modifier: Modifier = Modifier.fillMaxWidth(), allowNeg: Boolean = false, onValue: (Int) -> Unit) {
    var text by remember { mutableStateOf(value.toString()) }
    var focused by remember { mutableStateOf(false) }
    // Ikuti nilai dari luar (bingkai ditarik) selama kolom tidak sedang diketik.
    LaunchedEffect(value) { if (!focused) text = value.toString() }
    OutlinedTextField(
        value = text,
        onValueChange = { t ->
            val clean = t.filterIndexed { i, c -> c.isDigit() || (allowNeg && c == '-' && i == 0) }.take(6)
            text = clean
            clean.toIntOrNull()?.let(onValue)
        },
        label = { Text(label) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        modifier = modifier.onFocusChanged { focused = it.isFocused },
    )
}

// ================= Info kanvas =================

@Composable
fun CanvasInfoDialog(state: AppState) {
    if (!state.infoOpen) return
    val i = state.info
    AlertDialog(
        onDismissRequest = { state.infoOpen = false },
        title = { Text("Info kanvas") },
        text = {
            if (i == null) {
                CircularProgressIndicator(color = Accent)
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    InfoRow("Nama", state.docName)
                    InfoRow("Ukuran", "${i.w} × ${i.h} px")
                    InfoRow("Cetak", "${cm(i.w, i.dpi)} × ${cm(i.h, i.dpi)} cm · ${inch(i.w, i.dpi)} × ${inch(i.h, i.dpi)} inci · ${i.dpi} DPI")
                    InfoRow("Layer", "${i.layers} dari maks ${i.limit}" + if (i.groups > 0) " · ${i.groups} grup" else "")
                    InfoRow("Waktu menggambar", duration(i.timeMs))
                    InfoRow("Goresan", "%,d".format(Locale("id"), i.strokes))
                    InfoRow("Dibuat", SimpleDateFormat("d MMM yyyy, HH.mm", Locale("id")).format(Date(i.created)))
                    InfoRow("Ukuran berkas", String.format(Locale("id"), "%.1f MB", i.bytes / 1048576.0))
                    if (state.is3D) InfoRow("Model 3D", "%,d segitiga".format(Locale("id"), state.modelTris) + " · kanvas = tekstur")
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Profil warna", color = Muted, fontSize = 13.sp, modifier = Modifier.width(140.dp))
                        Text(id.goresan.engine.ColorProfile.name(state.profile), fontSize = 13.sp, modifier = Modifier.weight(1f))
                        TextButton(onClick = { state.profileOpen = true }) { Text("Ganti") }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = { state.infoOpen = false }) { Text("Tutup") } },
    )
}

/** Pilih profil warna karya (sRGB, Display P3, CMYK). */
@Composable
fun ProfileDialog(state: AppState) {
    if (!state.profileOpen) return
    var pick by remember { mutableIntStateOf(state.profile) }
    val cur = state.profile
    val converts = id.goresan.engine.ColorProfile.rgbSpace(pick) != id.goresan.engine.ColorProfile.rgbSpace(cur)
    AlertDialog(
        onDismissRequest = { state.profileOpen = false },
        title = { Text("Profil warna") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                for (t in 0..2) {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .clickable { pick = t }
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.Top,
                    ) {
                        RadioButton(selected = pick == t, onClick = { pick = t })
                        Column(Modifier.padding(top = 10.dp)) {
                            Text(id.goresan.engine.ColorProfile.name(t), fontWeight = FontWeight.SemiBold)
                            Text(profileHint(t), fontSize = 12.sp, color = Muted)
                        }
                    }
                }
                if (converts && pick != cur) {
                    Text(
                        if (pick == id.goresan.engine.ColorProfile.P3) {
                            "Piksel tiap layer diubah ke Display P3 supaya tampilannya tetap. Riwayat urungkan dikosongkan."
                        } else {
                            "Piksel tiap layer diubah ke sRGB; warna di luar sRGB dipotong ke yang terdekat. Riwayat urungkan dikosongkan."
                        },
                        fontSize = 12.sp, color = Color(0xFFF6C453),
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = pick != cur,
                onClick = { state.profileOpen = false; state.changeProfile(pick) },
            ) { Text("Terapkan") }
        },
        dismissButton = { TextButton(onClick = { state.profileOpen = false }) { Text("Batal") } },
    )
}

private fun inch(px: Int, dpi: Int) = String.format(Locale.US, "%.2f", px / dpi.toFloat()).replace('.', ',')

private fun duration(ms: Long): String {
    val m = ms / 60000
    val h = m / 60
    return when {
        h > 0 -> "$h jam ${m % 60} menit"
        m > 0 -> "$m menit"
        else -> "kurang dari 1 menit"
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row {
        Text(label, color = Muted, fontSize = 13.sp, modifier = Modifier.width(140.dp))
        Text(value, fontSize = 13.sp, modifier = Modifier.weight(1f))
    }
}

// ================= Jendela referensi =================

/**
 * Jendela kecil mengambang di atas kanvas: kanvas ini (melihat keseluruhan saat memperbesar) atau gambar contekan.
 * Geser lewat judul, ubah ukuran lewat sudut kanan-bawah, cubit untuk perbesar di dalam, ketuk dua kali untuk
 * kembali, tahan untuk mengambil warna.
 */
@Composable
fun ReferenceWindow(state: AppState) {
    if (!state.refOn) return
    val dens = LocalDensity.current
    val top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    var pos by remember { mutableStateOf(with(dens) { Offset(70.dp.toPx(), (90.dp + top).toPx()) }) }
    var wDp by remember { mutableFloatStateOf(320f) }
    var hDp by remember { mutableFloatStateOf(240f) }
    var zoom by remember { mutableFloatStateOf(1f) }
    var pan by remember { mutableStateOf(Offset.Zero) }
    var box by remember { mutableStateOf(IntSize.Zero) }
    val bmp = if (state.refMode == 0) state.refCanvas else state.refImage
    val img: ImageBitmap? = remember(bmp) { bmp?.asImageBitmap() }
    LaunchedEffect(state.refMode) { zoom = 1f; pan = Offset.Zero; if (state.refMode == 0) state.refreshRef() }

    Surface(
        color = PanelBg,
        shape = RoundedCornerShape(12.dp),
        shadowElevation = 10.dp,
        modifier = Modifier
            .offset { IntOffset(pos.x.roundToInt(), pos.y.roundToInt()) }
            .size(wDp.dp, hDp.dp)
            .border(1.dp, Color(0x33FFFFFF), RoundedCornerShape(12.dp)),
    ) {
        Box {
            Column(Modifier.fillMaxSize()) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .height(34.dp)
                        .pointerInput(Unit) { detectDragGestures { ch, d -> ch.consume(); pos += d } }
                        .padding(horizontal = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("Referensi", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.width(8.dp))
                    RefTab("Kanvas", state.refMode == 0) { state.refMode = 0 }
                    RefTab("Gambar", state.refMode == 1) {
                        if (state.refImage == null) state.pickRefImage?.invoke() else state.refMode = 1
                    }
                    Spacer(Modifier.weight(1f))
                    if (state.refMode == 1) {
                        Text(
                            "Ganti",
                            color = Accent,
                            fontSize = 12.sp,
                            modifier = Modifier.clickable { state.pickRefImage?.invoke() }.padding(horizontal = 6.dp, vertical = 4.dp),
                        )
                    }
                    Text(
                        "✕",
                        color = Color.White,
                        fontSize = 14.sp,
                        modifier = Modifier.clickable { state.refOn = false }.padding(horizontal = 6.dp, vertical = 4.dp),
                    )
                }
                Box(
                    Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .clipToBounds()
                        .background(Color(0xFF1E1E22))
                        .onSizeChanged { box = it }
                        .pointerInput(img) {
                            detectTransformGestures { _, p, z, _ ->
                                zoom = (zoom * z).coerceIn(1f, 12f)
                                pan += p
                            }
                        }
                        .pointerInput(img) {
                            detectTapGestures(
                                onDoubleTap = { zoom = 1f; pan = Offset.Zero },
                                onLongPress = { p ->
                                    val b = bmp ?: return@detectTapGestures
                                    // Titik layar -> piksel gambar (gambar dipaskan, lalu diperbesar di tengah + digeser).
                                    val bw = box.width.toFloat(); val bh = box.height.toFloat()
                                    val f = min(bw / b.width, bh / b.height)
                                    val cx = bw / 2f; val cy = bh / 2f
                                    val qx = cx + (p.x - cx - pan.x) / zoom
                                    val qy = cy + (p.y - cy - pan.y) / zoom
                                    val ix = ((qx - (bw - b.width * f) / 2f) / f).toInt()
                                    val iy = ((qy - (bh - b.height * f) / 2f) / f).toInt()
                                    if (ix in 0 until b.width && iy in 0 until b.height) {
                                        val c = b.getPixel(ix, iy)
                                        if (android.graphics.Color.alpha(c) > 0) {
                                            state.setColor(c or (0xFF shl 24))
                                            state.showFlash("Warna diambil dari referensi")
                                        }
                                    }
                                },
                            )
                        },
                ) {
                    if (img != null) {
                        Image(
                            img,
                            contentDescription = "Gambar referensi",
                            contentScale = ContentScale.Fit,
                            modifier = Modifier.fillMaxSize().graphicsLayer(
                                scaleX = zoom, scaleY = zoom, translationX = pan.x, translationY = pan.y,
                            ),
                        )
                    } else {
                        Text(
                            if (state.refMode == 1) "Ketuk Ganti untuk memilih gambar" else "Memuat…",
                            color = Muted, fontSize = 12.sp, modifier = Modifier.align(Alignment.Center),
                        )
                    }
                }
            }
            // pegangan ubah ukuran
            Box(
                Modifier
                    .align(Alignment.BottomEnd)
                    .size(26.dp)
                    .pointerInput(Unit) {
                        detectDragGestures { ch, d ->
                            ch.consume()
                            wDp = (wDp + d.x / density).coerceIn(160f, 900f)
                            hDp = (hDp + d.y / density).coerceIn(120f, 900f)
                        }
                    },
            ) {
                Canvas(Modifier.fillMaxSize().padding(6.dp)) {
                    for (i in 1..3) {
                        val o = size.width * i / 3f
                        drawLine(Color(0x99FFFFFF), Offset(o, size.height), Offset(size.width, o), 1.5.dp.toPx())
                    }
                }
            }
        }
    }
}

@Composable
private fun RefTab(label: String, sel: Boolean, onClick: () -> Unit) {
    Text(
        label,
        color = if (sel) Color.White else Muted,
        fontSize = 11.sp,
        modifier = Modifier
            .clip(RoundedCornerShape(10.dp))
            .background(if (sel) Color(0x553D8BFF) else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 3.dp),
    )
}
