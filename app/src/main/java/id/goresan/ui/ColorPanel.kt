package id.goresan.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FileOpen
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

private val HueColors = listOf(
    Color(0xFFFF0000), Color(0xFFFFFF00), Color(0xFF00FF00), Color(0xFF00FFFF),
    Color(0xFF0000FF), Color(0xFFFF00FF), Color(0xFFFF0000),
)

@Composable
fun ColorPanel(state: AppState, modifier: Modifier) {
    var tab by remember { mutableIntStateOf(state.prefs.getInt("colorTab", 0).coerceIn(0, 4)) }
    var panelRect by remember { mutableStateOf(Rect.Zero) }
    Surface(
        color = PanelBg,
        shape = RoundedCornerShape(18.dp),
        shadowElevation = 8.dp,
        modifier = modifier.width(380.dp).onGloballyPositioned { panelRect = it.boundsInRoot() },
    ) {
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Warna", color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 17.sp)
                Spacer(Modifier.weight(1f))
                // Warna kedua (dinamika warna kuas): ketuk = tukar dengan warna utama, tahan = jadikan warna sekarang.
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(
                        Modifier
                            .size(width = 30.dp, height = 28.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .border(1.dp, Color(0x55FFFFFF), RoundedCornerShape(10.dp))
                            .background(Color(state.color2))
                            .pointerInput(Unit) {
                                detectTapGestures(
                                    onTap = { state.swapColor2() },
                                    onLongPress = { state.setColor2FromCurrent() },
                                )
                            },
                    )
                    Text("Kedua", color = Muted, fontSize = 9.sp)
                }
                Spacer(Modifier.width(8.dp))
                // Warna sebelumnya (kiri, bisa diketuk) dan warna sekarang (kanan).
                Row(Modifier.clip(RoundedCornerShape(10.dp)).border(1.dp, Color(0x55FFFFFF), RoundedCornerShape(10.dp))) {
                    Box(Modifier.size(width = 34.dp, height = 28.dp).background(Color(state.prevColor)).clickable { state.setColor(state.prevColor) })
                    Box(Modifier.size(width = 34.dp, height = 28.dp).background(Color(state.color)))
                }
            }
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                listOf("Cakram", "Klasik", "Harmoni", "Nilai", "Palet").forEachIndexed { i, n ->
                    val sel = tab == i
                    Text(
                        n,
                        color = if (sel) Color.White else Color(0xFFB8B8C0),
                        fontWeight = if (sel) FontWeight.SemiBold else FontWeight.Normal,
                        fontSize = 13.sp,
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(10.dp))
                            .background(if (sel) Color(0x333D8BFF) else Color(0x10FFFFFF))
                            .clickable { tab = i; state.prefs.putInt("colorTab", i) }
                            .padding(vertical = 8.dp),
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    )
                }
            }
            Spacer(Modifier.height(12.dp))
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                when (tab) {
                    0 -> ColorDisc(state)
                    1 -> ColorClassic(state)
                    2 -> ColorHarmony(state)
                    3 -> ColorValues(state)
                    else -> ColorPalette(state) { panelRect }
                }
            }
            if (tab != 4) {
                // Palet utama (ringkas) di bawah pemilih warna, seperti Procreate.
                val dp = state.palettes.getOrNull(state.defaultPaletteIndex)
                if (dp != null && dp.colors.isNotEmpty()) {
                    Spacer(Modifier.height(10.dp))
                    Row(
                        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(5.dp),
                    ) {
                        for (c in dp.colors.take(40)) Swatch(state, c, 28.dp, { panelRect }, menu = false)
                    }
                }
            }
            if (state.profile == id.goresan.engine.ColorProfile.CMYK) {
                // Karya CMYK: persentase tinta dan peringatan warna di luar gamut cetak.
                val c = state.color
                val ink = remember(c) { state.inks(c) }
                val out = remember(c) { state.outOfPrint(c) }
                Spacer(Modifier.height(10.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("C ${ink[0]}  M ${ink[1]}  Y ${ink[2]}  K ${ink[3]}", color = Muted, fontSize = 12.sp)
                    Spacer(Modifier.weight(1f))
                    if (out) {
                        val p = remember(c) { state.printable(c) }
                        Row(
                            Modifier
                                .clip(RoundedCornerShape(10.dp))
                                .background(Color(0x33F6C453))
                                .clickable { state.setColor(p) }
                                .padding(horizontal = 8.dp, vertical = 5.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text("Di luar gamut cetak", color = Color(0xFFF6C453), fontSize = 12.sp)
                            Spacer(Modifier.width(6.dp))
                            Box(
                                Modifier
                                    .size(18.dp)
                                    .clip(RoundedCornerShape(5.dp))
                                    .background(Color(p))
                                    .border(1.dp, Color(0x55FFFFFF), RoundedCornerShape(5.dp)),
                            )
                            Spacer(Modifier.width(6.dp))
                            Text("Pakai", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }
            if (state.history.isNotEmpty()) {
                Spacer(Modifier.height(12.dp))
                Text("Riwayat", color = Muted, fontSize = 12.sp)
                Spacer(Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    for (c in state.history.take(9)) {
                        Box(
                            Modifier
                                .size(28.dp)
                                .clip(CircleShape)
                                .background(Color(c))
                                .border(1.dp, Color(0x55FFFFFF), CircleShape)
                                .clickable { state.setColor(c) },
                        )
                    }
                }
            }
            Spacer(Modifier.height(4.dp))
            Text("Seret lingkaran warna di bilah atas ke kanvas untuk ColorDrop · tahan lalu seret kotak palet ke kanvas untuk SwatchDrop", color = Muted, fontSize = 11.sp)
        }
    }
}

@Composable
private fun ColorDisc(state: AppState) {
    Canvas(
        Modifier
            .size(300.dp)
            .pointerInput(Unit) {
                awaitEachGesture {
                    val down = awaitFirstDown()
                    down.consume()
                    val c = Offset(size.width / 2f, size.height / 2f)
                    val outer = min(size.width, size.height) / 2f
                    val ringW = outer * 0.17f
                    val inner = outer - ringW
                    val half = inner * 0.70f
                    val ringMode = (down.position - c).getDistance() > inner - 4f
                    fun handle(p: Offset) {
                        if (ringMode) {
                            var deg = Math.toDegrees(atan2((p.y - c.y).toDouble(), (p.x - c.x).toDouble())).toFloat()
                            if (deg < 0f) deg += 360f
                            state.setHsv(deg, state.sat, state.value)
                        } else {
                            val s = ((p.x - (c.x - half)) / (2f * half)).coerceIn(0f, 1f)
                            val v = 1f - ((p.y - (c.y - half)) / (2f * half)).coerceIn(0f, 1f)
                            state.setHsv(state.hue, s, v)
                        }
                    }
                    handle(down.position)
                    while (true) {
                        val ev = awaitPointerEvent()
                        val ch = ev.changes.firstOrNull { it.id == down.id } ?: break
                        if (!ch.pressed) break
                        ch.consume()
                        handle(ch.position)
                    }
                }
            },
    ) {
        val c = center
        val outer = size.minDimension / 2f
        val ringW = outer * 0.17f
        val inner = outer - ringW
        val half = inner * 0.70f
        drawCircle(brush = Brush.sweepGradient(HueColors, center = c), radius = outer - ringW / 2f, style = Stroke(ringW))
        val tl = Offset(c.x - half, c.y - half)
        val sz = Size(2f * half, 2f * half)
        drawRect(
            brush = Brush.horizontalGradient(listOf(Color.White, Color.hsv(state.hue, 1f, 1f)), startX = tl.x, endX = tl.x + sz.width),
            topLeft = tl, size = sz,
        )
        drawRect(
            brush = Brush.verticalGradient(listOf(Color.Transparent, Color.Black), startY = tl.y, endY = tl.y + sz.height),
            topLeft = tl, size = sz,
        )
        val ang = Math.toRadians(state.hue.toDouble())
        val rr = outer - ringW / 2f
        val hp = Offset(c.x + (cos(ang) * rr).toFloat(), c.y + (sin(ang) * rr).toFloat())
        drawCircle(Color.White, radius = ringW * 0.42f, center = hp, style = Stroke(3.dp.toPx()))
        val sp = Offset(tl.x + state.sat * sz.width, tl.y + (1f - state.value) * sz.height)
        drawCircle(Color(0x88000000), radius = 10.dp.toPx(), center = sp, style = Stroke(1.dp.toPx()))
        drawCircle(Color.White, radius = 8.dp.toPx(), center = sp, style = Stroke(2.5.dp.toPx()))
    }
}

@Composable
private fun ColorClassic(state: AppState) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Canvas(
            Modifier
                .size(width = 320.dp, height = 220.dp)
                .clip(RoundedCornerShape(10.dp))
                .pointerInput(Unit) {
                    awaitEachGesture {
                        val down = awaitFirstDown()
                        down.consume()
                        fun handle(p: Offset) {
                            state.setHsv(
                                state.hue,
                                (p.x / size.width).coerceIn(0f, 1f),
                                1f - (p.y / size.height).coerceIn(0f, 1f),
                            )
                        }
                        handle(down.position)
                        while (true) {
                            val ev = awaitPointerEvent()
                            val ch = ev.changes.firstOrNull { it.id == down.id } ?: break
                            if (!ch.pressed) break
                            ch.consume()
                            handle(ch.position)
                        }
                    }
                },
        ) {
            drawRect(Brush.horizontalGradient(listOf(Color.White, Color.hsv(state.hue, 1f, 1f))))
            drawRect(Brush.verticalGradient(listOf(Color.Transparent, Color.Black)))
            val sp = Offset(state.sat * size.width, (1f - state.value) * size.height)
            drawCircle(Color(0x88000000), radius = 10.dp.toPx(), center = sp, style = Stroke(1.dp.toPx()))
            drawCircle(Color.White, radius = 8.dp.toPx(), center = sp, style = Stroke(2.5.dp.toPx()))
        }
        Spacer(Modifier.height(12.dp))
        Canvas(
            Modifier
                .size(width = 320.dp, height = 28.dp)
                .clip(RoundedCornerShape(14.dp))
                .pointerInput(Unit) {
                    awaitEachGesture {
                        val down = awaitFirstDown()
                        down.consume()
                        fun handle(p: Offset) = state.setHsv((p.x / size.width).coerceIn(0f, 1f) * 360f, state.sat, state.value)
                        handle(down.position)
                        while (true) {
                            val ev = awaitPointerEvent()
                            val ch = ev.changes.firstOrNull { it.id == down.id } ?: break
                            if (!ch.pressed) break
                            ch.consume()
                            handle(ch.position)
                        }
                    }
                },
        ) {
            drawRect(Brush.horizontalGradient(HueColors))
            val x = state.hue / 360f * size.width
            drawCircle(Color.White, radius = size.height / 2f - 2.dp.toPx(), center = Offset(x.coerceIn(size.height / 2f, size.width - size.height / 2f), size.height / 2f), style = Stroke(3.dp.toPx()))
        }
    }
}

@Composable
private fun ColorValues(state: AppState) {
    val c = state.color
    val r = android.graphics.Color.red(c)
    val g = android.graphics.Color.green(c)
    val b = android.graphics.Color.blue(c)
    var hex by remember(c) { mutableStateOf(String.format("%06X", c and 0xFFFFFF)) }
    Column(Modifier.fillMaxWidth()) {
        LabeledSlider("Rona", state.hue, { state.setHsv(it, state.sat, state.value) }, 0f..360f, { "${it.roundToInt()}°" })
        LabeledSlider("Saturasi", state.sat, { state.setHsv(state.hue, it, state.value) })
        LabeledSlider("Kecerahan", state.value, { state.setHsv(state.hue, state.sat, it) })
        LabeledSlider("Merah", r.toFloat(), { state.setColor(android.graphics.Color.rgb(it.roundToInt(), g, b)) }, 0f..255f, { "${it.roundToInt()}" })
        LabeledSlider("Hijau", g.toFloat(), { state.setColor(android.graphics.Color.rgb(r, it.roundToInt(), b)) }, 0f..255f, { "${it.roundToInt()}" })
        LabeledSlider("Biru", b.toFloat(), { state.setColor(android.graphics.Color.rgb(r, g, it.roundToInt())) }, 0f..255f, { "${it.roundToInt()}" })
        OutlinedTextField(
            value = hex,
            onValueChange = { t ->
                val clean = t.removePrefix("#").uppercase().filter { it in "0123456789ABCDEF" }.take(6)
                hex = clean
                if (clean.length == 6) {
                    runCatching { state.setColor(android.graphics.Color.parseColor("#$clean")) }
                }
            },
            label = { Text("Kode hex") },
            prefix = { Text("#") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

/**
 * Satu kotak warna palet: ketuk = pakai; tahan lalu seret ke kanvas = SwatchDrop (isi area dengan warna ini);
 * tahan tanpa digeser = menu (hapus/ganti), bila [menu].
 */
@Composable
private fun Swatch(state: AppState, c: Int, size: androidx.compose.ui.unit.Dp, panelRect: () -> Rect, menu: Boolean) {
    var origin by remember { mutableStateOf(Offset.Zero) }
    var menuOpen by remember { mutableStateOf(false) }
    var moved by remember { mutableFloatStateOf(0f) }
    Box {
        Box(
            Modifier
                .size(size)
                .onGloballyPositioned { origin = it.positionInRoot() }
                .semantics { contentDescription = "Warna #" + String.format("%06X", c and 0xFFFFFF) }
                .clip(RoundedCornerShape(if (size < 32.dp) 7.dp else 8.dp))
                .background(Color(c))
                .border(
                    if (c == state.color) 2.dp else 1.dp,
                    if (c == state.color) Color.White else Color(0x44FFFFFF),
                    RoundedCornerShape(if (size < 32.dp) 7.dp else 8.dp),
                )
                .pointerInput(c) { detectTapGestures(onTap = { state.setColor(c) }) }
                .pointerInput(c, menu) {
                    var last = Offset.Zero
                    detectDragGesturesAfterLongPress(
                        onDragStart = { p ->
                            last = p
                            moved = 0f
                            state.setColor(c)
                        },
                        onDrag = { ch, d ->
                            ch.consume()
                            last += d
                            moved += d.getDistance()
                            if (moved > 12f) state.dropPos = origin + last
                        },
                        onDragEnd = {
                            val p = origin + last
                            state.dropPos = null
                            if (moved <= 12f) {
                                if (menu) menuOpen = true
                            } else if (!panelRect().contains(p)) {
                                state.dropSwatch(c, p.x, p.y)
                            }
                        },
                        onDragCancel = { state.dropPos = null },
                    )
                },
        )
        if (menu) {
            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                DropdownMenuItem(text = { Text("Ganti dengan warna sekarang") }, onClick = { menuOpen = false; state.replaceInPalette(c) })
                DropdownMenuItem(text = { Text("Hapus warna", color = Color(0xFFFF8A80)) }, onClick = { menuOpen = false; state.removeFromPalette(c) })
            }
        }
    }
}

private val HarmonyNames = listOf("Komplementer", "Komplementer terpisah", "Analog", "Triadik", "Tetradik")

/**
 * Harmoni warna: cakram rona (sudut) × saturasi (jarak dari tengah) dengan penggeser kecerahan. Titik besar = warna
 * sekarang; titik kecil = pasangan harmoninya (ketuk untuk dipakai).
 */
@Composable
private fun ColorHarmony(state: AppState) {
    var modeMenu by remember { mutableStateOf(false) }
    val pairs = id.goresan.engine.Palettes.harmony(state.harmonyMode, state.hue, state.sat, state.value)
    val offs = id.goresan.engine.Palettes.harmonyOffsets(state.harmonyMode)
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
        Box {
            Text(
                HarmonyNames[state.harmonyMode] + "  ▾",
                color = Color.White,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.clip(RoundedCornerShape(10.dp)).background(Color(0x14FFFFFF)).clickable { modeMenu = true }
                    .padding(horizontal = 14.dp, vertical = 7.dp),
            )
            DropdownMenu(expanded = modeMenu, onDismissRequest = { modeMenu = false }) {
                HarmonyNames.forEachIndexed { i, n ->
                    DropdownMenuItem(text = { Text(n) }, onClick = { modeMenu = false; state.chooseHarmony(i) })
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        Canvas(
            Modifier
                .size(250.dp)
                .pointerInput(Unit) {
                    awaitEachGesture {
                        val down = awaitFirstDown()
                        down.consume()
                        val c = Offset(size.width / 2f, size.height / 2f)
                        val rad = min(size.width, size.height) / 2f - 14.dp.toPx()
                        // Ketuk titik pasangan = pakai warna itu (titik utama pindah ke sana).
                        val hit = id.goresan.engine.Palettes.harmonyOffsets(state.harmonyMode).firstOrNull { o ->
                            val a = Math.toRadians(((state.hue + o) % 360f).toDouble())
                            val p = Offset(c.x + (cos(a) * rad * state.sat).toFloat(), c.y + (sin(a) * rad * state.sat).toFloat())
                            (down.position - p).getDistance() < 18.dp.toPx()
                        }
                        if (hit != null) {
                            state.setHsv(((state.hue + hit) % 360f + 360f) % 360f, state.sat, state.value)
                            while (true) {
                                val ev = awaitPointerEvent()
                                if (ev.changes.none { it.pressed }) break
                            }
                            return@awaitEachGesture
                        }
                        fun handle(p: Offset) {
                            val v = p - c
                            var deg = Math.toDegrees(atan2(v.y.toDouble(), v.x.toDouble())).toFloat()
                            if (deg < 0f) deg += 360f
                            val sat = (v.getDistance() / rad).coerceIn(0f, 1f)
                            state.setHsv(deg, sat, state.value)
                        }
                        handle(down.position)
                        while (true) {
                            val ev = awaitPointerEvent()
                            val ch = ev.changes.firstOrNull { it.id == down.id } ?: break
                            if (!ch.pressed) break
                            ch.consume()
                            handle(ch.position)
                        }
                    }
                },
        ) {
            val c = center
            val rad = size.minDimension / 2f - 14.dp.toPx()
            drawCircle(brush = Brush.sweepGradient(HueColors, center = c), radius = rad, center = c)
            drawCircle(brush = Brush.radialGradient(listOf(Color.White, Color.White.copy(alpha = 0f)), center = c, radius = rad), radius = rad, center = c)
            drawCircle(Color.Black.copy(alpha = (1f - state.value).coerceIn(0f, 1f)), radius = rad, center = c)
            drawCircle(Color(0x55FFFFFF), radius = rad, center = c, style = Stroke(1.dp.toPx()))
            fun at(h: Float): Offset {
                val a = Math.toRadians(h.toDouble())
                return Offset(c.x + (cos(a) * rad * state.sat).toFloat(), c.y + (sin(a) * rad * state.sat).toFloat())
            }
            val main = at(state.hue)
            for ((k, o) in offs.withIndex()) {
                val p = at(state.hue + o)
                drawLine(Color(0x88FFFFFF), main, p, strokeWidth = 1.5.dp.toPx())
                drawCircle(Color(pairs[k]), radius = 11.dp.toPx(), center = p)
                drawCircle(Color.White, radius = 11.dp.toPx(), center = p, style = Stroke(2.dp.toPx()))
            }
            drawCircle(Color(state.color), radius = 15.dp.toPx(), center = main)
            drawCircle(Color(0x88000000), radius = 17.dp.toPx(), center = main, style = Stroke(1.dp.toPx()))
            drawCircle(Color.White, radius = 15.dp.toPx(), center = main, style = Stroke(3.dp.toPx()))
        }
        Spacer(Modifier.height(8.dp))
        LabeledSlider("Kecerahan", state.value, { state.setHsv(state.hue, state.sat, it) })
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(width = 54.dp, height = 30.dp).clip(RoundedCornerShape(8.dp)).background(Color(state.color)).border(2.dp, Color.White, RoundedCornerShape(8.dp)))
            for (p in pairs) {
                Box(
                    Modifier.size(width = 54.dp, height = 30.dp).clip(RoundedCornerShape(8.dp)).background(Color(p))
                        .border(1.dp, Color(0x55FFFFFF), RoundedCornerShape(8.dp)).clickable { state.setColor(p) },
                )
            }
            Spacer(Modifier.weight(1f))
            TextButton(onClick = {
                state.newPalette("Harmoni " + HarmonyNames[state.harmonyMode].lowercase(), listOf(state.color) + pairs)
                state.showFlash("Warna harmoni disimpan jadi palet baru")
            }) { Text("Simpan") }
        }
    }
}

@Composable
private fun ColorPalette(state: AppState, panelRect: () -> Rect) {
    val ctx = androidx.compose.ui.platform.LocalContext.current
    var menu by remember { mutableStateOf(false) }
    var addMenu by remember { mutableStateOf(false) }
    var renaming by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    val pickFile = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> if (uri != null) state.importPalette(uri) }
    val pickPhoto = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri -> if (uri != null) state.paletteFromUri(uri) }
    val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicturePreview()) { bmp ->
        if (bmp != null) state.paletteFromBitmap(bmp, "Kamera " + java.text.SimpleDateFormat("d MMM HH.mm", java.util.Locale("id")).format(java.util.Date()))
    }
    val camPerm = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        if (ok) runCatching { camera.launch(null) }.onFailure { state.showFlash("Kamera tidak tersedia") }
        else state.showFlash("Izin kamera diperlukan untuk palet dari kamera")
    }
    val cur = state.palettes.getOrNull(state.paletteIndex)
    Column(Modifier.fillMaxWidth()) {
        // Daftar palet: ketuk untuk membuka; bintang = palet utama (tampil ringkas di tab lain).
        Row(verticalAlignment = Alignment.CenterVertically) {
            Row(
                Modifier.weight(1f).horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                state.palettes.forEachIndexed { i, p ->
                    val sel = i == state.paletteIndex
                    Text(
                        (if (i == state.defaultPaletteIndex) "★ " else "") + p.name,
                        color = if (sel) Color.White else Color(0xFFB8B8C0),
                        fontSize = 13.sp,
                        fontWeight = if (sel) FontWeight.SemiBold else FontWeight.Normal,
                        maxLines = 1,
                        modifier = Modifier
                            .clip(RoundedCornerShape(10.dp))
                            .background(if (sel) Color(0x333D8BFF) else Color(0x10FFFFFF))
                            .clickable { state.choosePalette(i) }
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                    )
                }
            }
            Box {
                IconButton(onClick = { addMenu = true }) { Icon(Icons.Filled.Add, "Palet baru", tint = Color.White) }
                DropdownMenu(expanded = addMenu, onDismissRequest = { addMenu = false }) {
                    DropdownMenuItem(text = { Text("Palet kosong") }, leadingIcon = { Icon(Icons.Filled.Add, null) },
                        onClick = { addMenu = false; state.newPalette("Palet baru") })
                    DropdownMenuItem(text = { Text("Dari foto") }, leadingIcon = { Icon(Icons.Filled.Image, null) },
                        onClick = { addMenu = false; runCatching { pickPhoto.launch("image/*") } })
                    DropdownMenuItem(text = { Text("Dari kamera") }, leadingIcon = { Icon(Icons.Filled.PhotoCamera, null) },
                        onClick = {
                            addMenu = false
                            val granted = androidx.core.content.ContextCompat.checkSelfPermission(ctx, android.Manifest.permission.CAMERA) ==
                                android.content.pm.PackageManager.PERMISSION_GRANTED
                            if (granted) runCatching { camera.launch(null) }.onFailure { state.showFlash("Kamera tidak tersedia") }
                            else camPerm.launch(android.Manifest.permission.CAMERA)
                        })
                    DropdownMenuItem(text = { Text("Impor berkas (ASE, ACO, .swatches, GPL)") }, leadingIcon = { Icon(Icons.Filled.FileOpen, null) },
                        onClick = { addMenu = false; runCatching { pickFile.launch(arrayOf("*/*")) } })
                }
            }
            Box {
                IconButton(onClick = { menu = true }) { Icon(Icons.Filled.MoreVert, "Menu palet", tint = Color.White) }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    DropdownMenuItem(text = { Text("Jadikan palet utama") }, leadingIcon = { Icon(Icons.Filled.Star, null) },
                        onClick = { menu = false; state.setDefaultPalette(state.paletteIndex) })
                    DropdownMenuItem(text = { Text("Ganti nama") }, leadingIcon = { Icon(Icons.Filled.Edit, null) },
                        onClick = { menu = false; renaming = true })
                    DropdownMenuItem(text = { Text("Simpan sebagai .swatches") }, leadingIcon = { Icon(Icons.Filled.Download, null) },
                        onClick = { menu = false; state.exportPalette(state.paletteIndex) })
                    DropdownMenuItem(text = { Text("Hapus palet", color = Color(0xFFFF8A80)) }, leadingIcon = { Icon(Icons.Filled.Delete, null, tint = Color(0xFFFF8A80)) },
                        onClick = { menu = false; confirmDelete = true })
                }
            }
        }
        Spacer(Modifier.height(6.dp))
        if (cur != null && cur.colors.isEmpty()) {
            Box(Modifier.fillMaxWidth().height(200.dp), contentAlignment = Alignment.Center) {
                Text("Palet ini masih kosong.\nKetuk \"+ Simpan warna ini\" untuk menambah warna.", color = Muted, fontSize = 13.sp,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center)
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(38.dp),
                modifier = Modifier.fillMaxWidth().height(200.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                items(cur?.colors?.toList() ?: emptyList()) { c -> Swatch(state, c, 38.dp, panelRect, menu = true) }
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Tahan kotak: hapus/ganti · tahan & seret ke kanvas: SwatchDrop", color = Muted, fontSize = 11.sp, modifier = Modifier.weight(1f))
            TextButton(onClick = { state.addToPalette() }) { Text("+ Simpan warna ini") }
        }
    }
    if (renaming && cur != null) {
        var name by remember { mutableStateOf(cur.name) }
        AlertDialog(
            onDismissRequest = { renaming = false },
            title = { Text("Ganti nama palet") },
            text = { OutlinedTextField(value = name, onValueChange = { name = it }, singleLine = true) },
            confirmButton = { TextButton(onClick = { state.renamePalette(state.paletteIndex, name); renaming = false }) { Text("Simpan") } },
            dismissButton = { TextButton(onClick = { renaming = false }) { Text("Batal") } },
        )
    }
    if (confirmDelete && cur != null) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Hapus palet \"${cur.name}\"?") },
            text = { Text("${cur.colors.size} warna di palet ini ikut terhapus.") },
            confirmButton = { TextButton(onClick = { state.deletePalette(state.paletteIndex); confirmDelete = false }) { Text("Hapus", color = Color(0xFFFF8A80)) } },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Batal") } },
        )
    }
}
