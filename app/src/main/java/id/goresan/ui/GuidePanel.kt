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
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import id.goresan.engine.DrawingGuide
import kotlin.math.max
import kotlin.math.roundToInt

private val GuideTypes = listOf(
    DrawingGuide.GRID to "Grid 2D",
    DrawingGuide.ISO to "Isometrik",
    DrawingGuide.PERSP to "Perspektif",
    DrawingGuide.SYM to "Simetri",
)

private val SymOptions = listOf("Vertikal", "Horizontal", "Kuadran", "Radial")

/**
 * Layar Edit Panduan Menggambar seperti di Procreate: pita warna di atas, jenis panduan dan pengaturannya di bawah.
 * Kanvas tetap bisa disentuh: seret titik hijau (pindah) dan biru (putar), atau ketuk untuk titik hilang perspektif;
 * dua jari menggeser/memperbesar kanvas.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun GuideEditor(state: AppState) {
    val g = state.guide
    Box(Modifier.fillMaxSize()) {
        Column(
            Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .background(BarBg)
                .windowInsetsPadding(WindowInsets.statusBars)
                .pointerInput(Unit) { detectTapGestures { } },
        ) {
            Row(
                Modifier.fillMaxWidth().height(52.dp).padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Panduan Menggambar", color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
                Spacer(Modifier.weight(1f))
                TextButton(onClick = { state.resetGuide() }) { Text("Atur ulang", color = Color.White) }
                TextButton(onClick = { state.endGuideEdit(false) }) { Text("Batal", color = Muted) }
                Spacer(Modifier.width(4.dp))
                Button(
                    onClick = { state.endGuideEdit(true) },
                    colors = ButtonDefaults.buttonColors(containerColor = Accent),
                ) { Text("Selesai") }
            }
            GuideColorBar(g.color, onChange = { state.updateGuide(state.guide.copy(color = it)) }, onDone = { state.guideChanged() })
        }

        Surface(
            color = PanelBg,
            shape = RoundedCornerShape(18.dp),
            shadowElevation = 8.dp,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .windowInsetsPadding(WindowInsets.navigationBars)
                .padding(12.dp)
                .widthIn(max = 760.dp)
                .fillMaxWidth()
                .pointerInput(Unit) { detectTapGestures { } },
        ) {
            BoxWithConstraints {
                val wide = maxWidth >= 560.dp
                Column(Modifier.padding(horizontal = 14.dp, vertical = 12.dp)) {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        for ((t, label) in GuideTypes) Seg(label, g.type == t) { state.setGuideType(t) }
                    }
                    Spacer(Modifier.height(8.dp))
                    val sliders = ArrayList<@Composable () -> Unit>()
                    sliders.apply {
                        add {
                            LabeledSlider("Opasitas", g.opacity, { state.updateGuide(state.guide.copy(opacity = it)) }, 0.05f..1f, onDone = { state.guideChanged() })
                        }
                        add {
                            LabeledSlider("Ketebalan", g.thickness, { state.updateGuide(state.guide.copy(thickness = it)) }, 0f..1f, onDone = { state.guideChanged() })
                        }
                        if (g.type == DrawingGuide.GRID || g.type == DrawingGuide.ISO) add {
                            val hi = max(60f, minOf(1500f, max(state.docW, state.docH) / 2f))
                            LabeledSlider(
                                "Ukuran grid", g.gridSize.coerceIn(10f, hi),
                                { state.updateGuide(state.guide.copy(gridSize = it.roundToInt().toFloat())) },
                                10f..hi, { "${it.roundToInt()} px" }, onDone = { state.guideChanged() },
                            )
                        }
                    }
                    if (wide) {
                        Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                            for (sl in sliders) Box(Modifier.weight(1f)) { sl() }
                        }
                    } else {
                        for (sl in sliders) sl()
                    }
                    when (g.type) {
                        DrawingGuide.SYM -> {
                            Spacer(Modifier.height(6.dp))
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                SymOptions.forEachIndexed { i, label ->
                                    Seg(label, g.symmetry == i) { state.updateGuide(state.guide.copy(symmetry = i)); state.guideChanged() }
                                }
                            }
                            GuideToggle("Simetri putar", "Salinan diputar mengelilingi titik pusat, bukan dicerminkan", g.rotational) {
                                state.updateGuide(state.guide.copy(rotational = it))
                                state.guideChanged()
                            }
                        }
                        DrawingGuide.PERSP -> {
                            Row(Modifier.fillMaxWidth().padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    when (g.vpCount) {
                                        0 -> "Ketuk kanvas untuk membuat titik hilang (1, 2, atau 3 titik)."
                                        1 -> "Perspektif 1 titik. Ketuk lagi untuk 2 titik; seret titik untuk memindah."
                                        2 -> "Perspektif 2 titik. Ketuk lagi untuk 3 titik; seret titik untuk memindah."
                                        else -> "Perspektif 3 titik. Seret titik untuk memindah; ketuk titik lalu Hapus titik."
                                    },
                                    color = Muted, fontSize = 12.sp, modifier = Modifier.weight(1f),
                                )
                                TextButton(enabled = state.guideSel >= 0, onClick = { state.deleteSelectedVp(); state.guideChanged() }) {
                                    Text("Hapus titik", color = if (state.guideSel >= 0) Color(0xFFFF8A80) else Muted)
                                }
                            }
                        }
                        else -> Text(
                            "Seret titik hijau untuk memindah, titik biru untuk memutar. Dua jari: geser/perbesar kanvas.",
                            color = Muted, fontSize = 12.sp, modifier = Modifier.padding(top = 4.dp),
                        )
                    }
                    GuideToggle(
                        "Bantuan menggambar",
                        if (g.type == DrawingGuide.SYM) "Goresan di layer aktif dicerminkan" else "Goresan di layer aktif dikunci mengikuti garis panduan",
                        state.activeAssist,
                    ) { state.setAssist(it) }
                }
            }
        }
    }
}

@Composable
private fun Seg(label: String, on: Boolean, onClick: () -> Unit) {
    Box(
        Modifier
            .clip(RoundedCornerShape(10.dp))
            .background(if (on) Accent else Color(0x22FFFFFF))
            .clickable { onClick() }
            .padding(horizontal = 14.dp, vertical = 8.dp),
    ) {
        Text(label, color = Color.White, fontSize = 13.sp, fontWeight = if (on) FontWeight.SemiBold else FontWeight.Normal)
    }
}

@Composable
private fun GuideToggle(title: String, sub: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .clickable { onChange(!checked) }
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, color = Color.White, fontSize = 14.sp)
            Text(sub, color = Muted, fontSize = 11.sp)
        }
        Switch(checked = checked, onCheckedChange = onChange, colors = SwitchDefaults.colors(checkedTrackColor = Accent))
    }
}

/** Pita warna garis panduan (seperti Procreate) ditambah putih dan hitam. */
@Composable
private fun GuideColorBar(color: Int, onChange: (Int) -> Unit, onDone: () -> Unit) {
    val emit by rememberUpdatedState(onChange)
    val done by rememberUpdatedState(onDone)
    Row(
        Modifier.fillMaxWidth().padding(start = 12.dp, end = 12.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val hsv = FloatArray(3).also { android.graphics.Color.colorToHSV(color, it) }
        val grey = hsv[1] < 0.15f
        Canvas(
            Modifier
                .weight(1f)
                .height(22.dp)
                .clip(RoundedCornerShape(11.dp))
                .pointerInput(Unit) {
                    awaitEachGesture {
                        val down = awaitFirstDown()
                        down.consume()
                        fun handle(p: Offset) {
                            val h = (p.x / size.width).coerceIn(0f, 1f) * 360f
                            emit(android.graphics.Color.HSVToColor(floatArrayOf(h, 0.85f, 1f)))
                        }
                        handle(down.position)
                        while (true) {
                            val ev = awaitPointerEvent()
                            val ch = ev.changes.firstOrNull { it.id == down.id } ?: break
                            if (!ch.pressed) break
                            ch.consume()
                            handle(ch.position)
                        }
                        done()
                    }
                },
        ) {
            drawRect(Brush.horizontalGradient(hueStops()))
            if (!grey) {
                val x = hsv[0] / 360f * size.width
                drawCircle(Color.White, radius = size.height / 2f - 2.dp.toPx(), center = Offset(x.coerceIn(size.height / 2f, size.width - size.height / 2f), size.height / 2f), style = Stroke(3.dp.toPx()))
            }
        }
        Spacer(Modifier.width(10.dp))
        for (c in listOf(0xFFFFFFFF.toInt(), 0xFF000000.toInt())) {
            val on = (color or 0xFF000000.toInt()) == c
            Box(
                Modifier
                    .padding(start = 6.dp)
                    .size(24.dp)
                    .clip(CircleShape)
                    .background(Color(c))
                    .border(if (on) 2.5.dp else 1.dp, if (on) Accent else Color(0x66FFFFFF), CircleShape)
                    .clickable { emit(c); done() },
            )
        }
    }
}

private fun hueStops(): List<Color> = List(13) { Color(android.graphics.Color.HSVToColor(floatArrayOf(it * 30f, 0.85f, 1f))) }
