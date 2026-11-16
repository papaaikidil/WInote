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
    var tab by remember { mutableIntStateOf(0) }
    Surface(
        color = PanelBg,
        shape = RoundedCornerShape(18.dp),
        shadowElevation = 8.dp,
        modifier = modifier.width(360.dp),
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
                listOf("Cakram", "Klasik", "Nilai", "Palet").forEachIndexed { i, n ->
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
                            .clickable { tab = i }
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
                    2 -> ColorValues(state)
                    else -> ColorPalette(state)
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
            Text("Seret lingkaran warna di bilah atas ke kanvas untuk ColorDrop", color = Muted, fontSize = 11.sp)
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

@Composable
private fun ColorPalette(state: AppState) {
    Column(Modifier.fillMaxWidth()) {
        LazyVerticalGrid(
            columns = GridCells.Adaptive(38.dp),
            modifier = Modifier.fillMaxWidth().height(220.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            items(state.palette.toList()) { c ->
                Box(
                    Modifier
                        .size(38.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(c))
                        .border(
                            if (c == state.color) 2.dp else 1.dp,
                            if (c == state.color) Color.White else Color(0x44FFFFFF),
                            RoundedCornerShape(8.dp),
                        )
                        .pointerInput(c) {
                            detectTapGestures(
                                onTap = { state.setColor(c) },
                                onLongPress = { state.removeFromPalette(c) },
                            )
                        },
                )
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Tahan kotak warna untuk menghapusnya", color = Muted, fontSize = 11.sp, modifier = Modifier.weight(1f))
            TextButton(onClick = { state.addToPalette() }) { Text("+ Simpan warna ini") }
        }
    }
}
