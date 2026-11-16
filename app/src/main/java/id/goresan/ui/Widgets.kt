package id.goresan.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Ikon penghapus (tidak ada di set ikon standar). */
val EraserIcon: ImageVector by lazy {
    ImageVector.Builder(
        name = "Penghapus",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f,
    ).path(fill = SolidColor(Color.White)) {
        moveTo(14.2f, 3.2f)
        lineTo(20.8f, 9.8f)
        lineTo(12.4f, 18.2f)
        lineTo(5.8f, 11.6f)
        close()
    }.path(fill = SolidColor(Color.White), fillAlpha = 0.45f) {
        moveTo(5.0f, 12.4f)
        lineTo(11.6f, 19.0f)
        lineTo(9.6f, 21.0f)
        lineTo(5.6f, 21.0f)
        lineTo(3.0f, 18.4f)
        lineTo(3.0f, 14.4f)
        close()
    }.path(fill = SolidColor(Color.White)) {
        moveTo(13.0f, 20.0f)
        lineTo(21.0f, 20.0f)
        lineTo(21.0f, 21.6f)
        lineTo(13.0f, 21.6f)
        close()
    }.build()
}

/** Penggeser tegak ala Procreate: geser relatif (tidak melompat ke titik sentuh). */
@Composable
fun VerticalSlider(
    value: Float,
    onChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    onStart: () -> Unit = {},
    onEnd: () -> Unit = {},
) {
    val cur by rememberUpdatedState(value)
    val change by rememberUpdatedState(onChange)
    val start by rememberUpdatedState(onStart)
    val end by rememberUpdatedState(onEnd)
    Box(
        modifier
            .width(36.dp)
            .pointerInput(Unit) {
                awaitEachGesture {
                    val down = awaitFirstDown()
                    down.consume()
                    start()
                    val v0 = cur
                    val y0 = down.position.y
                    val h = size.height.toFloat().coerceAtLeast(1f)
                    while (true) {
                        val ev = awaitPointerEvent()
                        val c = ev.changes.firstOrNull { it.id == down.id } ?: break
                        if (!c.pressed) break
                        c.consume()
                        change((v0 - (c.position.y - y0) / h).coerceIn(0f, 1f))
                    }
                    end()
                }
            },
    ) {
        Canvas(Modifier.fillMaxSize().padding(vertical = 4.dp)) {
            val w = 6.dp.toPx()
            val x = size.width / 2f
            drawRoundRect(
                color = Color(0x55FFFFFF),
                topLeft = Offset(x - w / 2f, 0f),
                size = Size(w, size.height),
                cornerRadius = CornerRadius(w / 2f),
            )
            val fillH = size.height * value
            drawRoundRect(
                color = Color(0xCCFFFFFF),
                topLeft = Offset(x - w / 2f, size.height - fillH),
                size = Size(w, fillH),
                cornerRadius = CornerRadius(w / 2f),
            )
            val ty = size.height * (1f - value)
            val tw = 26.dp.toPx()
            val th = 14.dp.toPx()
            drawRoundRect(
                color = Color.White,
                topLeft = Offset(x - tw / 2f, (ty - th / 2f).coerceIn(0f, size.height - th)),
                size = Size(tw, th),
                cornerRadius = CornerRadius(th / 2f),
            )
        }
    }
}

/** Latar papan catur untuk menunjukkan transparansi. */
fun Modifier.checker(cell: Float = 6f): Modifier = this.drawBehind {
    val c = cell * density
    val a = Color(0xFFBDBDC2)
    val b = Color(0xFFE6E6EA)
    drawRect(b)
    var y = 0f
    var row = 0
    while (y < size.height) {
        var x = if (row % 2 == 0) 0f else c
        while (x < size.width) {
            drawRect(a, topLeft = Offset(x, y), size = Size(c, c))
            x += 2 * c
        }
        y += c
        row++
    }
}

/** Baris penggeser berlabel untuk panel pengaturan. */
@Composable
fun LabeledSlider(
    label: String,
    value: Float,
    onChange: (Float) -> Unit,
    range: ClosedFloatingPointRange<Float> = 0f..1f,
    display: (Float) -> String = { "${(it * 100).toInt()}%" },
    onDone: () -> Unit = {},
) {
    Column(Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(label, fontSize = 13.sp, color = Color(0xFFDADAE0))
            Text(display(value), fontSize = 13.sp, color = Muted, fontWeight = FontWeight.Medium)
        }
        Slider(
            value = value.coerceIn(range.start, range.endInclusive),
            onValueChange = onChange,
            valueRange = range,
            onValueChangeFinished = onDone,
            colors = SliderDefaults.colors(thumbColor = Color.White, activeTrackColor = Accent),
        )
    }
}

@Composable
fun BarIcon(
    icon: ImageVector,
    desc: String,
    selected: Boolean = false,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    val tint = when {
        !enabled -> Color(0x55FFFFFF)
        selected -> Accent
        else -> Color(0xFFEDEDF2)
    }
    Box(
        Modifier
            .size(44.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(if (selected) Color(0x223D8BFF) else Color.Transparent)
            .clickable(enabled = enabled) { onClick() },
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = desc, tint = tint, modifier = Modifier.size(24.dp))
    }
}

/**
 * Tombol bilah yang langsung bekerja saat disentuh (tidak menunggu jari diangkat) dan mengulang terus selama
 * ditahan, makin lama makin cepat. Dipakai untuk urungkan/ulangi seperti di Procreate.
 */
@Composable
fun RepeatBarIcon(
    icon: ImageVector,
    desc: String,
    enabled: Boolean = true,
    onAction: () -> Unit,
) {
    val act by rememberUpdatedState(onAction)
    val en by rememberUpdatedState(enabled)
    var pressed by remember { mutableStateOf(false) }
    val tint = if (!enabled) Color(0x55FFFFFF) else Color(0xFFEDEDF2)
    Box(
        Modifier
            .size(44.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(if (pressed) Color(0x33FFFFFF) else Color.Transparent)
            .pointerInput(Unit) {
                awaitEachGesture {
                    val down = awaitFirstDown()
                    down.consume()
                    pressed = true
                    try {
                    act()
                    var n = 0
                    var deadline = android.os.SystemClock.uptimeMillis() + 420L
                    while (true) {
                        val left = (deadline - android.os.SystemClock.uptimeMillis()).coerceAtLeast(1L)
                        val ev = withTimeoutOrNull(left) { awaitPointerEvent() }
                        if (ev == null) {
                            // Masih ditahan: ulangi, makin lama makin cepat.
                            if (en) { n++; act() }
                            deadline = android.os.SystemClock.uptimeMillis() + when {
                                n < 4 -> 160L
                                n < 12 -> 100L
                                else -> 65L
                            }
                            continue
                        }
                        val c = ev.changes.firstOrNull { it.id == down.id } ?: break
                        if (!c.pressed) { c.consume(); break }
                        val p = c.position
                        // Jari bergeser keluar tombol: berhenti mengulang.
                        if (p.x < -8f || p.y < -8f || p.x > size.width + 8f || p.y > size.height + 8f) break
                    }
                    } finally {
                        pressed = false
                    }
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = desc, tint = tint, modifier = Modifier.size(24.dp))
    }
}

/**
 * Dialog Compose punya jendela sendiri yang memunculkan lagi bilah sistem (navigasi/taskbar).
 * Panggil di dalam isi dialog supaya tetap layar penuh seperti ruang gambar.
 */
@Composable
fun DialogImmersive() {
    val view = androidx.compose.ui.platform.LocalView.current
    androidx.compose.runtime.LaunchedEffect(Unit) {
        val w = (view.parent as? androidx.compose.ui.window.DialogWindowProvider)?.window ?: return@LaunchedEffect
        androidx.core.view.WindowCompat.getInsetsController(w, w.decorView).apply {
            hide(androidx.core.view.WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior = androidx.core.view.WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
    }
}
