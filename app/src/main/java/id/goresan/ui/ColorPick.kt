package id.goresan.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

private val PickHues = listOf(
    Color(0xFFFF0000), Color(0xFFFFFF00), Color(0xFF00FF00), Color(0xFF00FFFF),
    Color(0xFF0000FF), Color(0xFFFF00FF), Color(0xFFFF0000),
)

/**
 * Pemilih warna ringkas (kotak saturasi–kecerahan, pita rona, dan contoh warna) untuk warna selain warna kuas,
 * misalnya warna latar kanvas. [onChange] dipanggil terus selama jari digeser.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CompactColorPicker(color: Int, onChange: (Int) -> Unit, swatches: List<Int>, width: Dp = 300.dp) {
    var hue by remember { mutableFloatStateOf(0f) }
    var sat by remember { mutableFloatStateOf(0f) }
    var value by remember { mutableFloatStateOf(1f) }
    val emit by rememberUpdatedState(onChange)
    fun current() = android.graphics.Color.HSVToColor(floatArrayOf(hue, sat, value))
    // Warna diubah dari luar (contoh warna, "warna aktif"): ikuti, tetapi rona tetap bila warnanya abu-abu.
    LaunchedEffect(color) {
        if (current() != (color or 0xFF000000.toInt())) {
            val a = FloatArray(3)
            android.graphics.Color.colorToHSV(color, a)
            if (a[1] > 0.001f && a[2] > 0.001f) hue = a[0]
            sat = a[1]
            value = a[2]
        }
    }
    Column {
        Canvas(
            Modifier
                .size(width = width, height = width * 0.62f)
                .clip(RoundedCornerShape(10.dp))
                .pointerInput(Unit) {
                    awaitEachGesture {
                        val down = awaitFirstDown()
                        down.consume()
                        fun handle(p: Offset) {
                            sat = (p.x / size.width).coerceIn(0f, 1f)
                            value = 1f - (p.y / size.height).coerceIn(0f, 1f)
                            emit(current())
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
            drawRect(Brush.horizontalGradient(listOf(Color.White, Color.hsv(hue, 1f, 1f))))
            drawRect(Brush.verticalGradient(listOf(Color.Transparent, Color.Black)))
            val sp = Offset(sat * size.width, (1f - value) * size.height)
            drawCircle(Color(0x88000000), radius = 10.dp.toPx(), center = sp, style = Stroke(1.dp.toPx()))
            drawCircle(Color.White, radius = 8.dp.toPx(), center = sp, style = Stroke(2.5.dp.toPx()))
        }
        Spacer(Modifier.height(10.dp))
        Canvas(
            Modifier
                .size(width = width, height = 26.dp)
                .clip(RoundedCornerShape(13.dp))
                .pointerInput(Unit) {
                    awaitEachGesture {
                        val down = awaitFirstDown()
                        down.consume()
                        fun handle(p: Offset) {
                            hue = (p.x / size.width).coerceIn(0f, 1f) * 360f
                            emit(current())
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
            drawRect(Brush.horizontalGradient(PickHues))
            val x = hue / 360f * size.width
            drawCircle(
                Color.White, radius = size.height / 2f - 2.dp.toPx(),
                center = Offset(x.coerceIn(size.height / 2f, size.width - size.height / 2f), size.height / 2f),
                style = Stroke(3.dp.toPx()),
            )
        }
        if (swatches.isNotEmpty()) {
            Spacer(Modifier.height(10.dp))
            FlowRow(
                Modifier.width(width),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                for (c in swatches.distinct()) {
                    val on = (c or 0xFF000000.toInt()) == (color or 0xFF000000.toInt())
                    Spacer(
                        Modifier
                            .size(30.dp)
                            .clip(RoundedCornerShape(7.dp))
                            .background(Color(c or 0xFF000000.toInt()))
                            .border(if (on) 2.5.dp else 1.dp, if (on) Accent else Color(0x44FFFFFF), RoundedCornerShape(7.dp))
                            .clickable { emit(c or 0xFF000000.toInt()) },
                    )
                }
            }
        }
    }
}
