package id.hananote.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke as DrawStroke
import androidx.compose.ui.graphics.drawscope.translate
import id.hananote.data.ShapeKind
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import id.hananote.data.PaperTemplate
import id.hananote.data.Tool
import id.hananote.ink.InkRenderer
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

// ---------------- sampul buku ----------------

@Composable
fun BookCover(
    title: String,
    subtitle: String,
    color: Int,
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    /** Gambar sampul pilihan pengguna; null untuk sampul warna. */
    image: ImageBitmap? = null,
    /** Tampilkan label judul di sampul (di rak, judul ada di bawah sampul). */
    label: Boolean = true,
) {
    val shape = RoundedCornerShape(topStart = 4.dp, bottomStart = 4.dp, topEnd = 12.dp, bottomEnd = 12.dp)
    Box(
        modifier
            .aspectRatio(0.78f)
            .shadow(if (selected) 14.dp else 5.dp, shape)
            .clip(shape)
            .background(Color(color))
    ) {
        if (image != null) {
            Image(image, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.matchParentSize())
        } else {
            // Sampul warna: cahaya lembut dari atas supaya tidak datar.
            Box(
                Modifier.matchParentSize().background(
                    Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.16f), Color.Transparent, Color.Black.copy(alpha = 0.12f)))
                )
            )
            // Tali karet
            Box(
                Modifier
                    .align(Alignment.CenterEnd)
                    .padding(end = 16.dp)
                    .fillMaxHeight()
                    .width(5.dp)
                    .background(Color.Black.copy(alpha = 0.22f))
            )
        }
        // Punggung buku: bayangan lembut di tepi kiri.
        Box(
            Modifier.fillMaxHeight().width(16.dp).background(
                Brush.horizontalGradient(listOf(Color.Black.copy(alpha = 0.28f), Color.Black.copy(alpha = 0.06f), Color.Transparent))
            )
        )
        Box(Modifier.fillMaxHeight().padding(start = 12.dp).width(1.5.dp).background(Color.White.copy(alpha = 0.18f)))
        if (label) {
            Column(
                Modifier
                    .align(Alignment.TopCenter)
                    .padding(start = 24.dp, end = 28.dp, top = 24.dp)
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(6.dp))
                    .background(Color(0xFFFFFBF7))
                    .padding(horizontal = 10.dp, vertical = 8.dp)
            ) {
                Text(
                    title,
                    style = MaterialTheme.typography.titleSmall.copy(fontFamily = FontFamily.Serif, fontWeight = FontWeight.SemiBold),
                    color = Color(0xFF26262C),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                if (subtitle.isNotEmpty()) Text(subtitle, style = MaterialTheme.typography.labelSmall, color = Color(0xFF6B6B75), maxLines = 1)
            }
        }
    }
}

// ---------------- baki alat tulis ----------------

@Composable
fun ToolGlyph(tool: Tool, ink: Color, selected: Boolean, modifier: Modifier = Modifier) {
    val lift by animateFloatAsState(if (selected) 0f else 10f, label = "lift")
    Canvas(modifier.size(width = 30.dp, height = 46.dp).clipToBounds()) {
        translate(top = lift.dp.toPx()) { drawTool(tool, ink) }
    }
}

private fun DrawScope.drawTool(tool: Tool, ink: Color) {
    val h = size.height
    val cx = size.width / 2f
    fun d(v: Float) = v.dp.toPx()
    when (tool) {
        Tool.PEN -> {
            val bw = d(11f)
            drawRoundRect(Color(0xFF34373F), Offset(cx - bw / 2, d(17f)), Size(bw, h), CornerRadius(d(3f)))
            drawRect(ink, Offset(cx - bw / 2, d(22f)), Size(bw, d(4f)))
            drawPath(Path().apply {
                moveTo(cx - bw / 2, d(17f)); lineTo(cx + bw / 2, d(17f)); lineTo(cx + d(1.6f), d(6f)); lineTo(cx - d(1.6f), d(6f)); close()
            }, Color(0xFFCFD1D8))
            drawPath(Path().apply {
                moveTo(cx - d(1.6f), d(6f)); lineTo(cx + d(1.6f), d(6f)); lineTo(cx, d(1.5f)); close()
            }, ink)
        }
        Tool.PENCIL -> {
            val bw = d(11f)
            drawRect(Color(0xFFF2B93B), Offset(cx - bw / 2, d(17f)), Size(bw, h))
            drawRect(Color(0xFFD99A1E), Offset(cx - d(1f), d(17f)), Size(d(2f), h))
            drawPath(Path().apply {
                moveTo(cx - bw / 2, d(17f)); lineTo(cx + bw / 2, d(17f)); lineTo(cx + d(1.8f), d(6f)); lineTo(cx - d(1.8f), d(6f)); close()
            }, Color(0xFFF3D9B1))
            drawPath(Path().apply {
                moveTo(cx - d(1.8f), d(6f)); lineTo(cx + d(1.8f), d(6f)); lineTo(cx, d(1.5f)); close()
            }, ink)
        }
        Tool.BRUSH -> {
            drawRoundRect(Color(0xFF9A6B45), Offset(cx - d(3.5f), d(22f)), Size(d(7f), h), CornerRadius(d(3f)))
            drawRect(Color(0xFFC4C7CE), Offset(cx - d(4.5f), d(15f)), Size(d(9f), d(8f)))
            drawPath(Path().apply {
                moveTo(cx - d(4.5f), d(15f))
                lineTo(cx - d(4.8f), d(9f))
                lineTo(cx - d(2.2f), d(4f))
                lineTo(cx, d(1.5f))
                lineTo(cx + d(2.2f), d(4f))
                lineTo(cx + d(4.8f), d(9f))
                lineTo(cx + d(4.5f), d(15f))
                close()
            }, ink)
        }
        Tool.MARKER -> {
            val bw = d(15f)
            drawRoundRect(Color(0xFF2E3138), Offset(cx - bw / 2, d(19f)), Size(bw, h), CornerRadius(d(4f)))
            drawRect(ink, Offset(cx - bw / 2, d(24f)), Size(bw, d(6f)))
            drawPath(Path().apply {
                moveTo(cx - d(3.5f), d(19f)); lineTo(cx + d(3.5f), d(19f)); lineTo(cx + d(3.5f), d(4f)); lineTo(cx - d(3.5f), d(7f)); close()
            }, ink)
        }
        Tool.HIGHLIGHTER -> {
            val bw = d(17f)
            drawRoundRect(ink, Offset(cx - bw / 2, d(19f)), Size(bw, h), CornerRadius(d(4f)))
            drawRect(Color.White.copy(alpha = 0.28f), Offset(cx - bw / 2 + d(3f), d(21f)), Size(d(3f), h))
            drawPath(Path().apply {
                moveTo(cx - d(6f), d(19f)); lineTo(cx + d(6f), d(19f)); lineTo(cx + d(4.5f), d(6f)); lineTo(cx - d(4.5f), d(9f)); close()
            }, ink.copy(alpha = 0.85f))
        }
        Tool.ERASER -> {
            val bw = d(18f)
            drawRoundRect(Color(0xFFF4A9B8), Offset(cx - bw / 2, d(8f)), Size(bw, h), CornerRadius(d(4f)))
            drawRect(Color(0xFF5A6CC9), Offset(cx - bw / 2, d(24f)), Size(bw, h))
        }
        Tool.WATERCOLOR -> {
            // Bercak cat di belakang, lalu kuas bulat bergagang biru.
            drawCircle(ink.copy(alpha = 0.28f), radius = d(6f), center = Offset(cx + d(7f), d(33f)))
            drawCircle(ink.copy(alpha = 0.22f), radius = d(4f), center = Offset(cx - d(8f), d(38f)))
            drawRoundRect(Color(0xFF3F6B8C), Offset(cx - d(3f), d(23f)), Size(d(6f), h), CornerRadius(d(3f)))
            drawRect(Color(0xFFC4C7CE), Offset(cx - d(4f), d(16f)), Size(d(8f), d(8f)))
            drawPath(Path().apply {
                moveTo(cx - d(4f), d(16f))
                cubicTo(cx - d(6.5f), d(10f), cx - d(2f), d(4f), cx, d(1.5f))
                cubicTo(cx + d(2f), d(4f), cx + d(6.5f), d(10f), cx + d(4f), d(16f))
                close()
            }, ink.copy(alpha = 0.85f))
        }
        Tool.LINE -> Unit // bentuk punya ikon sendiri (ShapeGlyph)
    }
}

/** Penggaris segitiga untuk alat Bentuk. */
@Composable
fun ShapeGlyph(ink: Color, selected: Boolean, modifier: Modifier = Modifier) {
    val lift by animateFloatAsState(if (selected) 0f else 10f, label = "lift")
    Canvas(modifier.size(width = 30.dp, height = 46.dp).clipToBounds()) {
        translate(top = lift.dp.toPx()) {
            val cx = size.width / 2f
            fun d(v: Float) = v.dp.toPx()
            drawPath(Path().apply {
                moveTo(cx - d(11f), d(48f)); lineTo(cx - d(11f), d(6f)); lineTo(cx + d(12f), d(48f)); close()
            }, Color(0xFF9CC3E0))
            drawPath(Path().apply {
                moveTo(cx - d(6.5f), d(40f)); lineTo(cx - d(6.5f), d(22f)); lineTo(cx + d(3.5f), d(40f)); close()
            }, Color.White.copy(alpha = 0.75f))
            drawCircle(ink, radius = d(3.4f), center = Offset(cx + d(6f), d(15f)))
        }
    }
}

/** Panah penunjuk untuk alat Pilih. */
@Composable
fun SelectGlyph(ink: Color, selected: Boolean, modifier: Modifier = Modifier) {
    val lift by animateFloatAsState(if (selected) 0f else 10f, label = "lift")
    Canvas(modifier.size(width = 30.dp, height = 46.dp).clipToBounds()) {
        translate(top = lift.dp.toPx()) {
            val cx = size.width / 2f
            fun d(v: Float) = v.dp.toPx()
            drawPath(Path().apply {
                moveTo(cx - d(6f), d(6f))
                lineTo(cx - d(6f), d(31f))
                lineTo(cx - d(0.5f), d(25.5f))
                lineTo(cx + d(3.5f), d(34f))
                lineTo(cx + d(7f), d(32.5f))
                lineTo(cx + d(3f), d(24f))
                lineTo(cx + d(10f), d(24f))
                close()
            }, ink)
        }
    }
}

/** Ikon kecil jenis bentuk. */
@Composable
fun ShapeKindIcon(kind: ShapeKind, color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier.size(22.dp)) {
        val sw = 2.dp.toPx()
        val line = DrawStroke(width = sw, cap = StrokeCap.Round, join = StrokeJoin.Round)
        val w = size.width
        val h = size.height
        val p = 3.dp.toPx()
        when (kind) {
            ShapeKind.LINE -> drawLine(color, Offset(p, h - p), Offset(w - p, p), strokeWidth = sw, cap = StrokeCap.Round)
            ShapeKind.ARROW -> {
                drawLine(color, Offset(p, h - p), Offset(w - p, p), strokeWidth = sw, cap = StrokeCap.Round)
                drawPath(Path().apply {
                    moveTo(w - p - 7.dp.toPx(), p); lineTo(w - p, p); lineTo(w - p, p + 7.dp.toPx())
                }, color, style = line)
            }
            ShapeKind.RECT -> drawRect(color, Offset(p, p), Size(w - 2 * p, h - 2 * p), style = line)
            ShapeKind.ELLIPSE -> drawOval(color, Offset(p, p), Size(w - 2 * p, h - 2 * p), style = line)
            ShapeKind.TRIANGLE -> drawPath(Path().apply {
                moveTo(w / 2f, p); lineTo(w - p, h - p); lineTo(p, h - p); close()
            }, color, style = line)
        }
    }
}

// ---------------- warna ----------------

@Composable
fun ColorDot(color: Color, selected: Boolean, onClick: () -> Unit, size: Dp = 26.dp, description: String = "Warna") {
    val ring = MaterialTheme.colorScheme.onSurface
    val outline = MaterialTheme.colorScheme.outline
    Box(
        Modifier
            .size(size + 14.dp)
            .clip(CircleShape)
            .toolClick(onClick = onClick)
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        if (selected) Box(Modifier.size(size + 10.dp).border(2.dp, ring, CircleShape))
        Box(Modifier.size(size).clip(CircleShape).background(color).border(1.dp, outline.copy(alpha = 0.5f), CircleShape))
    }
}

@Composable
fun CustomColorDot(onClick: () -> Unit, size: Dp = 26.dp) {
    val rainbow = Brush.sweepGradient(
        listOf(Color(0xFFE53935), Color(0xFFFDD835), Color(0xFF43A047), Color(0xFF00ACC1), Color(0xFF3949AB), Color(0xFFD81B60), Color(0xFFE53935))
    )
    Box(
        Modifier
            .size(size + 14.dp)
            .clip(CircleShape)
            .toolClick(onClick = onClick)
            .semantics { contentDescription = "Pilih warna sendiri" },
        contentAlignment = Alignment.Center,
    ) {
        Box(Modifier.size(size).clip(CircleShape).background(rainbow))
        Box(Modifier.size(size * 0.42f).clip(CircleShape).background(MaterialTheme.colorScheme.surface))
    }
}

@Composable
fun ColorPickerDialog(initial: Int, onDismiss: () -> Unit, onPick: (Int) -> Unit) {
    val hsv = remember { FloatArray(3).also { android.graphics.Color.colorToHSV(initial, it) } }
    var h by remember { mutableFloatStateOf(hsv[0]) }
    var s by remember { mutableFloatStateOf(hsv[1]) }
    var v by remember { mutableFloatStateOf(hsv[2]) }
    val c = android.graphics.Color.HSVToColor(floatArrayOf(h, s, v))
    val sliderColors = SliderDefaults.colors(thumbColor = Color(c), activeTrackColor = Color(c))
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Warna sendiri") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(44.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(Color(c))
                        .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(10.dp))
                )
                Spacer(Modifier.height(6.dp))
                Text("Warna", style = MaterialTheme.typography.labelLarge)
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(8.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(
                            Brush.horizontalGradient(
                                listOf(Color(0xFFFF0000), Color(0xFFFFFF00), Color(0xFF00FF00), Color(0xFF00FFFF), Color(0xFF0000FF), Color(0xFFFF00FF), Color(0xFFFF0000))
                            )
                        )
                )
                Slider(value = h, onValueChange = { h = it }, valueRange = 0f..360f, colors = sliderColors)
                Text("Kepekatan", style = MaterialTheme.typography.labelLarge)
                Slider(value = s, onValueChange = { s = it }, valueRange = 0f..1f, colors = sliderColors)
                Text("Kecerahan", style = MaterialTheme.typography.labelLarge)
                Slider(value = v, onValueChange = { v = it }, valueRange = 0f..1f, colors = sliderColors)
            }
        },
        confirmButton = { TextButton(onClick = { onPick(c) }) { Text("Pakai warna ini") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Batal") } },
    )
}

// ---------------- kertas ----------------

@Composable
fun PaperPreview(template: PaperTemplate, paperColor: Int, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        drawRect(Color(paperColor))
        val dark = InkRenderer.isDark(paperColor)
        val line = if (dark) Color.White.copy(alpha = 0.25f) else Color(0xFF33415C).copy(alpha = 0.25f)
        val gap = size.width / 9f
        when (template) {
            PaperTemplate.BLANK -> Unit
            PaperTemplate.LINED -> {
                var y = gap * 2f
                while (y < size.height) {
                    drawLine(line, Offset(0f, y), Offset(size.width, y), strokeWidth = 1f); y += gap
                }
                drawLine(Color(0x88D9534F), Offset(gap * 1.4f, 0f), Offset(gap * 1.4f, size.height), strokeWidth = 1f)
            }
            PaperTemplate.GRID -> {
                val g = gap * 0.7f
                var x = g
                while (x < size.width) { drawLine(line, Offset(x, 0f), Offset(x, size.height), strokeWidth = 1f); x += g }
                var y = g
                while (y < size.height) { drawLine(line, Offset(0f, y), Offset(size.width, y), strokeWidth = 1f); y += g }
            }
            PaperTemplate.DOTS -> {
                val g = gap * 0.7f
                var y = g
                while (y < size.height) {
                    var x = g
                    while (x < size.width) { drawCircle(line.copy(alpha = 0.6f), radius = 1.3f, center = Offset(x, y)); x += g }
                    y += g
                }
            }
            else -> {
                // Templat siap pakai digambar seperti di halaman A4, diperkecil.
                val nc = drawContext.canvas.nativeCanvas
                val k = size.width / 595f
                nc.save()
                nc.scale(k, size.height / 842f)
                id.hananote.ink.PageTemplates.draw(nc, template, 595f, 842f, dark, hair = 1f / k)
                nc.restore()
            }
        }
    }
}

// ---------------- dialog umum ----------------

@Composable
fun TextInputDialog(
    title: String,
    initial: String,
    label: String,
    confirm: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var text by remember { mutableStateOf(TextFieldValue(initial, selection = TextRange(0, initial.length))) }
    val focus = remember { FocusRequester() }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                label = { Text(label) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().focusRequester(focus),
            )
            LaunchedEffect(Unit) {
                delay(120)
                runCatching { focus.requestFocus() }
            }
        },
        confirmButton = { TextButton(onClick = { onConfirm(text.text.trim()) }) { Text(confirm) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Batal") } },
    )
}

@Composable
fun ConfirmDialog(
    title: String,
    message: String,
    confirm: String,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(message) },
        confirmButton = {
            TextButton(onClick = onConfirm) { Text(confirm, color = MaterialTheme.colorScheme.error) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Batal") } },
    )
}

// ---------------- tanggal ----------------

private val indo: Locale = Locale.forLanguageTag("id-ID")

fun formatWhen(ts: Long): String {
    val now = Calendar.getInstance()
    val then = Calendar.getInstance().apply { timeInMillis = ts }
    val sameDay = now.get(Calendar.YEAR) == then.get(Calendar.YEAR) && now.get(Calendar.DAY_OF_YEAR) == then.get(Calendar.DAY_OF_YEAR)
    return when {
        sameDay -> "Hari ini, " + SimpleDateFormat("HH.mm", indo).format(ts)
        now.get(Calendar.YEAR) == then.get(Calendar.YEAR) -> SimpleDateFormat("d MMM", indo).format(ts)
        else -> SimpleDateFormat("d MMM yyyy", indo).format(ts)
    }
}

fun formatLongDate(ts: Long): String = SimpleDateFormat("EEEE, d MMMM yyyy", indo).format(ts)

/** Warna kontras untuk teks di atas warna tertentu. */
fun Color.onColor(): Color = if (InkRenderer.isDark(toArgb())) Color.White else Color(0xFF1A1C21)
