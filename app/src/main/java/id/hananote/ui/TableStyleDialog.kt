package id.hananote.ui

import android.graphics.Paint
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BorderAll
import androidx.compose.material.icons.filled.BorderBottom
import androidx.compose.material.icons.filled.BorderClear
import androidx.compose.material.icons.filled.BorderHorizontal
import androidx.compose.material.icons.filled.BorderInner
import androidx.compose.material.icons.filled.BorderLeft
import androidx.compose.material.icons.filled.BorderOuter
import androidx.compose.material.icons.filled.BorderRight
import androidx.compose.material.icons.filled.BorderTop
import androidx.compose.material.icons.filled.BorderVertical
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import id.hananote.data.InkColorNames
import id.hananote.data.InkColors
import id.hananote.data.TableBorders
import id.hananote.data.TableFillColors
import id.hananote.data.TableFillNames
import id.hananote.data.TableItem
import id.hananote.data.TableStyle
import id.hananote.ink.InkRenderer

/** Tebal garis dalam kata-kata: "½ pt", "2¼ pt", "Bawaan". */
private fun widthLabel(w: Float): String = when (w) {
    0f -> "Bawaan"
    0.5f -> "½ pt"
    0.75f -> "¾ pt"
    1.5f -> "1½ pt"
    2.25f -> "2¼ pt"
    4.5f -> "4½ pt"
    else -> if (w == w.toInt().toFloat()) "${w.toInt()} pt" else "$w pt"
}

/** Pilihan cepat garis tabel, seperti menu Batas di Word. */
private val BorderPresets = listOf(
    Triple(Icons.Filled.BorderAll, "Semua garis", TableBorders.ALL),
    Triple(Icons.Filled.BorderOuter, "Garis luar saja", TableBorders.OUTSIDE),
    Triple(Icons.Filled.BorderInner, "Garis dalam saja", TableBorders.INSIDE),
    Triple(Icons.Filled.BorderClear, "Tanpa garis", TableBorders.NONE),
)

/** Tiap garis yang bisa dinyalakan atau dimatikan sendiri-sendiri. */
private val BorderEdges = listOf(
    Triple(Icons.Filled.BorderTop, "Atas", TableBorders.TOP),
    Triple(Icons.Filled.BorderBottom, "Bawah", TableBorders.BOTTOM),
    Triple(Icons.Filled.BorderLeft, "Kiri", TableBorders.LEFT),
    Triple(Icons.Filled.BorderRight, "Kanan", TableBorders.RIGHT),
    Triple(Icons.Filled.BorderHorizontal, "Dalam mendatar", TableBorders.INSIDE_H),
    Triple(Icons.Filled.BorderVertical, "Dalam tegak", TableBorders.INSIDE_V),
)

/**
 * Garis dan warna tabel, seperti Batas dan Bayangan di Word: garis mana yang tampil, tebal garis, warna garis,
 * warna isi sel, warna baris judul, dan baris selang-seling. Pratinjau di atas langsung mengikuti pilihan.
 */
@Composable
fun TableStyleDialog(
    table: TableItem,
    onDismiss: () -> Unit,
    onApply: (style: TableStyle, color: Int, header: Boolean, clearCellFills: Boolean) -> Unit,
) {
    var borders by remember { mutableIntStateOf(table.style.borders) }
    var lineWidth by remember { mutableFloatStateOf(table.style.lineWidth) }
    var lineColor by remember { mutableIntStateOf(table.color) }
    var fill by remember { mutableIntStateOf(table.style.fill) }
    var headerFill by remember { mutableIntStateOf(table.style.headerFill) }
    var header by remember { mutableStateOf(table.header) }
    var banded by remember { mutableStateOf(table.style.banded) }
    var clearFills by remember { mutableStateOf(false) }
    // Warna sendiri sedang dipilih untuk: 1 garis, 2 isi sel, 3 baris judul.
    var picking by remember { mutableIntStateOf(0) }

    val style = TableStyle(borders, lineWidth, fill, headerFill, banded)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Garis & warna tabel") },
        text = {
            Column(
                Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                TablePreview(table.withStyle(style, lineColor, header, clearFills))

                SectionTitle("Garis yang tampil")
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.horizontalScroll(rememberScrollState())) {
                    BorderPresets.forEach { (icon, label, value) ->
                        BorderToggle(icon, label, selected = borders == value) { borders = value }
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.horizontalScroll(rememberScrollState())) {
                    BorderEdges.forEach { (icon, label, edge) ->
                        BorderToggle(icon, label, selected = borders and edge == edge) { borders = borders xor edge }
                    }
                }

                SectionTitle("Tebal garis")
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.horizontalScroll(rememberScrollState())) {
                    (floatArrayOf(0f) + TableStyle.WIDTHS).forEach { w ->
                        WidthChip(w, selected = lineWidth == w, color = Color(lineColor)) { lineWidth = w }
                    }
                }

                SectionTitle("Warna garis")
                Row(Modifier.horizontalScroll(rememberScrollState()), verticalAlignment = Alignment.CenterVertically) {
                    val names = listOf("Abu tua") + InkColorNames
                    (listOf(0xFF5A6070.toInt()) + InkColors).forEachIndexed { i, col ->
                        ColorDot(Color(col), col == lineColor, { lineColor = col }, size = 24.dp, description = "Garis ${names.getOrElse(i) { "" }}")
                    }
                    CustomColorDot(onClick = { picking = 1 }, size = 24.dp)
                }

                SectionTitle("Warna isi sel")
                Row(Modifier.horizontalScroll(rememberScrollState()), verticalAlignment = Alignment.CenterVertically) {
                    NoFillDot(onClick = { fill = 0 }, selected = fill == 0, description = "Isi sel tanpa warna")
                    TableFillColors.forEachIndexed { i, col ->
                        ColorDot(Color(col), col == fill, { fill = col }, size = 24.dp, description = "Isi ${TableFillNames[i]}")
                    }
                    CustomColorDot(onClick = { picking = 2 }, size = 24.dp)
                }
                if (table.hasCellFills) {
                    Row(
                        Modifier.clickable { clearFills = !clearFills },
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Checkbox(checked = clearFills, onCheckedChange = { clearFills = it })
                        Text("Hapus juga warna sel yang diatur satu per satu", style = MaterialTheme.typography.bodyMedium)
                    }
                }

                SwitchRow("Baris judul (baris pertama ditebalkan dan diberi warna)", header) { header = it }
                if (header) {
                    SectionTitle("Warna baris judul")
                    Row(Modifier.horizontalScroll(rememberScrollState()), verticalAlignment = Alignment.CenterVertically) {
                        AutoDot(selected = headerFill == 0, tint = Color(lineColor)) { headerFill = 0 }
                        TableFillColors.forEachIndexed { i, col ->
                            ColorDot(Color(col), col == headerFill, { headerFill = col }, size = 24.dp, description = "Judul ${TableFillNames[i]}")
                        }
                        CustomColorDot(onClick = { picking = 3 }, size = 24.dp)
                    }
                }
                SwitchRow("Baris selang-seling (baris isi berganti warna)", banded) { banded = it }
                Text(
                    "Garis yang dimatikan tetap tampil putus-putus samar saat menyunting supaya sel mudah ditemukan; " +
                        "garis itu tidak ikut diekspor atau dicetak. Warna satu sel saja: ketuk dua kali selnya, lalu Warna sel.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = { TextButton(onClick = { onApply(style, lineColor, header, clearFills) }) { Text("Terapkan") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Batal") } },
    )

    if (picking != 0) {
        val start = when (picking) {
            1 -> lineColor
            2 -> if (fill != 0) fill else 0xFFDEEAF6.toInt()
            else -> if (headerFill != 0) headerFill else 0xFFDEEAF6.toInt()
        }
        ColorPickerDialog(start, onDismiss = { picking = 0 }) { col ->
            when (picking) {
                1 -> lineColor = col
                2 -> fill = col
                else -> headerFill = col
            }
            picking = 0
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(text, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
}

@Composable
private fun SwitchRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable { onChange(!checked) }.padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        Spacer(Modifier.width(8.dp))
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

/** Tombol garis dengan ikon seperti di Word dan namanya di bawah. */
@Composable
private fun BorderToggle(icon: ImageVector, label: String, selected: Boolean, onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Column(
        Modifier
            .widthIn(min = 64.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(if (selected) scheme.primaryContainer else Color.Transparent)
            .border(1.dp, if (selected) scheme.primary else scheme.outlineVariant, RoundedCornerShape(10.dp))
            .clickable(role = Role.Checkbox, onClick = onClick)
            .semantics { contentDescription = label; this.selected = selected }
            .padding(horizontal = 6.dp, vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(icon, contentDescription = null, tint = if (selected) scheme.onPrimaryContainer else scheme.onSurfaceVariant)
        Spacer(Modifier.height(2.dp))
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            textAlign = TextAlign.Center,
            maxLines = 2,
            color = if (selected) scheme.onPrimaryContainer else scheme.onSurfaceVariant,
        )
    }
}

/** Pilihan tebal garis dengan contoh garisnya. */
@Composable
private fun WidthChip(w: Float, selected: Boolean, color: Color, onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Column(
        Modifier
            .width(64.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(if (selected) scheme.primaryContainer else Color.Transparent)
            .border(1.dp, if (selected) scheme.primary else scheme.outlineVariant, RoundedCornerShape(10.dp))
            .clickable(role = Role.RadioButton, onClick = onClick)
            .semantics { contentDescription = "Tebal garis ${widthLabel(w)}"; this.selected = selected }
            .padding(horizontal = 8.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(Modifier.fillMaxWidth().height(10.dp), contentAlignment = Alignment.Center) {
            // Contoh garis: 1 pt di halaman kira-kira 1 dp di sini, paling tipis 1 dp supaya tetap terlihat.
            val px = if (w == 0f) 1.2f else w
            Box(Modifier.fillMaxWidth().height(px.coerceIn(1f, 6f).dp).background(color))
        }
        Spacer(Modifier.height(4.dp))
        Text(widthLabel(w), style = MaterialTheme.typography.labelSmall, color = if (selected) scheme.onPrimaryContainer else scheme.onSurfaceVariant)
    }
}

/** Pilihan "bawaan" untuk baris judul: warna garis yang dibuat tipis. */
@Composable
private fun AutoDot(selected: Boolean, tint: Color, onClick: () -> Unit) {
    val ring = MaterialTheme.colorScheme.onSurface
    Box(
        Modifier
            .size(38.dp)
            .clip(RoundedCornerShape(50))
            .clickable(onClick = onClick)
            .semantics { contentDescription = "Warna baris judul bawaan" },
        contentAlignment = Alignment.Center,
    ) {
        if (selected) Box(Modifier.size(34.dp).border(2.dp, ring, RoundedCornerShape(50)))
        Box(
            Modifier.size(24.dp).clip(RoundedCornerShape(50)).background(Color.White)
                .border(1.dp, tint, RoundedCornerShape(50)),
            contentAlignment = Alignment.Center,
        ) {
            Box(Modifier.size(24.dp).background(tint.copy(alpha = 0.12f)))
            Text("A", style = MaterialTheme.typography.labelSmall, color = tint)
        }
    }
}

/** Pratinjau tabel dengan pilihan yang sedang dibuat, pas di dalam kotak. */
@Composable
private fun TablePreview(t: TableItem) {
    val ratio = (t.h / t.w).coerceIn(0.2f, 1.2f)
    Box(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(Color.White)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(8.dp))
            .padding(12.dp),
    ) {
        Canvas(Modifier.fillMaxWidth().heightIn(min = 60.dp).height((260f * ratio).coerceIn(60f, 240f).dp)) {
            val pad = 4f
            val s = minOf((size.width - pad * 2) / t.w, (size.height - pad * 2) / t.h)
            val dx = (size.width - t.w * s) / 2f
            val dy = (size.height - t.h * s) / 2f
            drawIntoCanvas { cv ->
                val nc = cv.nativeCanvas
                nc.save()
                nc.translate(dx, dy)
                nc.scale(s, s)
                nc.translate(-t.x, -t.y)
                InkRenderer.drawItem(nc, t, Paint(Paint.ANTI_ALIAS_FLAG), null, s)
                nc.restore()
            }
        }
    }
}
