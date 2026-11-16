package id.hananote.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import id.hananote.data.TableItem
import kotlin.math.abs

/**
 * Atur tabel: jumlah baris dan kolom, lebar tiap kolom, tinggi tiap baris (diseret langsung di
 * gambar tabel), dan baris judul berwarna.
 *
 * Lebar tabel tetap: menambah kolom membagi ulang lebarnya. Tinggi tabel mengikuti jumlah tinggi
 * barisnya, jadi menambah baris memanjangkan tabel ke bawah.
 */
@Composable
fun TableLayoutDialog(
    table: TableItem,
    onDismiss: () -> Unit,
    onApply: (rows: Int, cols: Int, colFrac: FloatArray, rowFrac: FloatArray, header: Boolean, height: Float) -> Unit,
) {
    val totalW = table.w
    // Ukuran dalam poin halaman.
    val colW = remember { mutableStateListOf<Float>().apply { table.colFrac.forEach { add(it * totalW) } } }
    val rowH = remember { mutableStateListOf<Float>().apply { table.rowFrac.forEach { add(it * table.h) } } }
    var header by remember { mutableStateOf(table.header) }
    // Garis yang sedang diseret: >0 kolom (nomor garis), <0 baris (minus nomor garis), 0 tidak ada.
    var drag by remember { mutableIntStateOf(0) }

    fun setCols(n: Int) {
        val target = n.coerceIn(1, 12)
        while (colW.size < target) colW.add(colW.average().toFloat())
        while (colW.size > target) colW.removeAt(colW.lastIndex)
        val sum = colW.sum()
        for (i in colW.indices) colW[i] = colW[i] / sum * totalW
    }

    fun setRows(n: Int) {
        val target = n.coerceIn(1, 40)
        while (rowH.size < target) rowH.add(rowH.average().toFloat())
        while (rowH.size > target) rowH.removeAt(rowH.lastIndex)
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Atur tabel") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Counter("Baris", rowH.size, 1..40) { setRows(it) }
                Counter("Kolom", colW.size, 1..12) { setCols(it) }
                Text(
                    "Seret garis tabel di bawah ini untuk mengubah lebar kolom atau tinggi baris.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                val line = MaterialTheme.colorScheme.onSurface
                val accent = MaterialTheme.colorScheme.primary
                val headFill = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                val density = LocalDensity.current
                val grab = with(density) { 26.dp.toPx() }
                // Pratinjau seperbandingan tabel, dibatasi supaya tetap muat di layar.
                val sumH = rowH.sum()
                val ratio = (sumH / totalW).coerceIn(0.25f, 1.6f)
                Canvas(
                    Modifier
                        .fillMaxWidth()
                        .height(with(density) { (260.dp.toPx() * ratio).coerceIn(90.dp.toPx(), 380.dp.toPx()).toDp() })
                        .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(6.dp))
                        .pointerInput(Unit) {
                            detectDragGestures(
                                onDragStart = { pos ->
                                    val w = size.width.toFloat()
                                    val h = size.height.toFloat()
                                    var best = 0
                                    var bestD = grab
                                    var acc = 0f
                                    val sw = colW.sum()
                                    for (i in 0 until colW.size - 1) {
                                        acc += colW[i]
                                        val d = abs(pos.x - acc / sw * w)
                                        if (d < bestD) { bestD = d; best = i + 1 }
                                    }
                                    acc = 0f
                                    val sh = rowH.sum()
                                    for (i in 0 until rowH.size - 1) {
                                        acc += rowH[i]
                                        val d = abs(pos.y - acc / sh * h)
                                        if (d < bestD) { bestD = d; best = -(i + 1) }
                                    }
                                    drag = best
                                },
                                onDragEnd = { drag = 0 },
                                onDragCancel = { drag = 0 },
                                onDrag = { change, amount ->
                                    change.consume()
                                    val d = drag
                                    if (d > 0) {
                                        val sw = colW.sum()
                                        val delta = amount.x / size.width * sw
                                        val minW = sw * 0.05f
                                        val a = d - 1
                                        val pair = colW[a] + colW[d]
                                        val na = (colW[a] + delta).coerceIn(minW, pair - minW)
                                        colW[a] = na
                                        colW[d] = pair - na
                                    } else if (d < 0) {
                                        val i = -d
                                        val sh = rowH.sum()
                                        val delta = amount.y / size.height * sh
                                        val minH = 10f
                                        val pair = rowH[i - 1] + rowH[i]
                                        val na = (rowH[i - 1] + delta).coerceIn(minH, pair - minH)
                                        rowH[i - 1] = na
                                        rowH[i] = pair - na
                                    }
                                },
                            )
                        },
                ) {
                    val w = size.width
                    val h = size.height
                    val sw = colW.sum()
                    val sh = rowH.sum()
                    if (header) drawRect(headFill, Offset.Zero, Size(w, rowH[0] / sh * h))
                    var acc = 0f
                    for (i in 0 until rowH.size - 1) {
                        acc += rowH[i]
                        val y = acc / sh * h
                        val hot = drag == -(i + 1)
                        drawLine(if (hot) accent else line.copy(alpha = 0.55f), Offset(0f, y), Offset(w, y), strokeWidth = if (hot) 5f else 2f)
                    }
                    acc = 0f
                    for (i in 0 until colW.size - 1) {
                        acc += colW[i]
                        val x = acc / sw * w
                        val hot = drag == i + 1
                        drawLine(if (hot) accent else line.copy(alpha = 0.55f), Offset(x, 0f), Offset(x, h), strokeWidth = if (hot) 5f else 2f)
                    }
                    drawRect(line.copy(alpha = 0.7f), Offset.Zero, Size(w, h), style = androidx.compose.ui.graphics.drawscope.Stroke(3f))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { val each = totalW / colW.size; for (i in colW.indices) colW[i] = each }) { Text("Samakan kolom") }
                    OutlinedButton(onClick = { val each = rowH.sum() / rowH.size; for (i in rowH.indices) rowH[i] = each }) { Text("Samakan baris") }
                }
                FilterChip(selected = header, onClick = { header = !header }, label = { Text("Baris judul berwarna") })
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val sw = colW.sum()
                val sh = rowH.sum()
                onApply(
                    rowH.size, colW.size,
                    FloatArray(colW.size) { colW[it] / sw },
                    FloatArray(rowH.size) { rowH[it] / sh },
                    header, sh,
                )
            }) { Text("Terapkan") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Batal") } },
    )
}

@Composable
private fun Counter(label: String, value: Int, range: IntRange, onChange: (Int) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.width(72.dp))
        IconButton(onClick = { onChange(value - 1) }, enabled = value > range.first) {
            Icon(Icons.Filled.Remove, contentDescription = "Kurangi $label")
        }
        Text("$value", style = MaterialTheme.typography.titleMedium, modifier = Modifier.width(36.dp), color = Color.Unspecified)
        IconButton(onClick = { onChange(value + 1) }, enabled = value < range.last) {
            Icon(Icons.Filled.Add, contentDescription = "Tambah $label")
        }
        Spacer(Modifier.width(4.dp))
    }
}
