package id.hananote.ui

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FormatStrikethrough
import androidx.compose.material.icons.filled.FormatUnderlined
import androidx.compose.material.icons.filled.Highlight
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import id.hananote.data.HighlighterColors
import id.hananote.data.PageNumberSpec
import id.hananote.ink.PdfField
import id.hananote.ink.PdfFieldBox
import java.io.File
import java.io.FileOutputStream
import kotlin.math.max
import kotlin.math.min

/** Isi bilah alat saat alat Teks PDF aktif: petunjuk singkat, warna stabilo, dan sakelar isi formulir. */
@Composable
internal fun PdfToolOptions(c: EditorController) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        ToolChip(selected = !c.pdfForm, onClick = { if (c.pdfForm) c.togglePdfForm() }, label = { Text("Pilih teks") })
        ToolChip(selected = c.pdfForm, onClick = { if (!c.pdfForm) c.togglePdfForm() }, label = { Text("Isi formulir") })
        Spacer(Modifier.width(6.dp))
        if (!c.pdfForm) {
            Text("Stabilo", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            HighlighterColors.forEach { col ->
                ColorDot(Color(col), col == c.pdfMarkColor, { c.setPdfMarkColor(col) }, size = 22.dp, description = "Warna stabilo")
            }
        } else {
            Text(
                "Ketuk kolom formulir untuk mengisi; kotak centang diketuk untuk mencentang.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** Bilah aksi untuk teks PDF yang dipilih. */
@Composable
internal fun PdfActionBar(c: EditorController, modifier: Modifier) {
    val sel = c.pdfSel ?: return
    val context = LocalContext.current
    Surface(modifier.padding(horizontal = 8.dp), shape = RoundedCornerShape(50), tonalElevation = 4.dp, shadowElevation = 6.dp) {
        Row(
            Modifier.horizontalScroll(rememberScrollState()).padding(start = 14.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "“" + sel.text.replace('\n', ' ').take(28) + (if (sel.text.length > 28) "…" else "") + "”",
                style = MaterialTheme.typography.labelLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(end = 6.dp),
            )
            ActionIcon(Icons.Filled.Highlight, "Stabilo") { c.pdfMark(0) }
            ActionIcon(Icons.Filled.FormatUnderlined, "Garis bawah") { c.pdfMark(1) }
            ActionIcon(Icons.Filled.FormatStrikethrough, "Coret") { c.pdfMark(2) }
            ActionIcon(Icons.Filled.ContentCopy, "Salin") { c.pdfCopy(context) }
            ActionIcon(Icons.Filled.SelectAll, "Satu baris") { c.pdfSelectLines() }
            TextButton(onClick = { c.pdfBeginEdit() }) {
                Icon(Icons.Filled.Edit, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(4.dp))
                Text("Ubah teks")
            }
            IconButton(onClick = { c.dismissPdfSelection() }) { Icon(Icons.Filled.Close, contentDescription = "Batal pilih") }
        }
    }
}

@Composable
private fun ActionIcon(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, onClick: () -> Unit) {
    IconButton(onClick = onClick) { Icon(icon, contentDescription = label) }
}

/** Dialog ubah teks PDF: teks asli sudah terisi; disimpan sebagai penutup + teks pengganti. */
@Composable
internal fun PdfEditDialog(initial: String, onDismiss: () -> Unit, onApply: (String) -> Unit) {
    var text by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Ubah teks PDF") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    label = { Text("Teks baru") },
                    minLines = 2,
                    maxLines = 8,
                    modifier = Modifier.fillMaxWidth().semantics { contentDescription = "Teks baru" },
                )
                Text(
                    "Tulisan aslinya ditutup warna kertas, lalu teks baru ditaruh di tempat yang sama dengan ukuran dan warna yang mirip. Kosongkan untuk menghapus tulisannya saja.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = { Button(onClick = { onApply(text) }) { Text("Ganti") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Batal") } },
    )
}

/** Dialog isi kolom formulir PDF: teks bebas, atau pilihan untuk kolom daftar. */
@Composable
internal fun PdfFieldDialog(c: EditorController, f: PdfFieldBox) {
    var text by remember(f) { mutableStateOf(c.pdfFieldValue(f)) }
    val label = f.field.name.substringAfterLast('.').ifBlank { "Isian" }
    AlertDialog(
        onDismissRequest = { c.pdfFieldEditing = null },
        title = { Text(label) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (f.field.kind == PdfField.Kind.CHOICE && f.field.options.isNotEmpty()) {
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        f.field.options.forEach { o -> ToolChip(selected = text == o, onClick = { text = o }, label = { Text(o) }) }
                    }
                }
                OutlinedTextField(
                    value = text,
                    onValueChange = { v -> text = if (f.field.maxLen > 0) v.take(f.field.maxLen) else v },
                    label = { Text("Isi kolom") },
                    singleLine = !f.field.multiline,
                    minLines = if (f.field.multiline) 3 else 1,
                    modifier = Modifier.fillMaxWidth().semantics { contentDescription = "Isi kolom" },
                )
            }
        },
        confirmButton = { Button(onClick = { c.pdfFillField(f, text) }) { Text("Isi") } },
        dismissButton = { TextButton(onClick = { c.pdfFieldEditing = null }) { Text("Batal") } },
    )
}

/**
 * Dialog tanda tangan: tanda tangan tersimpan (ketuk untuk menempel, ikon hapus untuk membuang), dan papan untuk
 * membuat tanda tangan baru dengan pena atau jari. Disimpan sebagai PNG tembus pandang, berlaku untuk semua catatan.
 */
@Composable
internal fun SignatureDialog(c: EditorController) {
    val context = LocalContext.current
    val dir = remember { c.signatureDir(context) }
    var saved by remember { mutableStateOf(listSignatures(dir)) }
    var drawing by remember { mutableStateOf(saved.isEmpty()) }
    val lines = remember { mutableStateListOf<List<Offset>>() }
    val current = remember { mutableStateListOf<Offset>() }
    var inkIdx by remember { mutableIntStateOf(0) }
    val inks = listOf(0xFF1F2430.toInt(), 0xFF1E3A8A.toInt(), 0xFFB91C1C.toInt())
    var padW by remember { mutableIntStateOf(1) }
    var padH by remember { mutableIntStateOf(1) }
    AlertDialog(
        onDismissRequest = { c.signatureOpen = false },
        title = { Text("Tanda tangan") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (!drawing) {
                    Text("Ketuk tanda tangan untuk menempelkannya.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        saved.forEach { f ->
                            val bmp = remember(f) { runCatching { BitmapFactory.decodeFile(f.path) }.getOrNull() }
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(Color.White)
                                    .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(10.dp))
                                    .clickable { c.insertSignature(f) }
                                    .semantics { contentDescription = "Tanda tangan tersimpan" }
                                    .padding(8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                if (bmp != null) {
                                    Image(bmp.asImageBitmap(), contentDescription = null, contentScale = ContentScale.Fit, modifier = Modifier.weight(1f).height(56.dp))
                                } else {
                                    Spacer(Modifier.weight(1f))
                                }
                                IconButton(onClick = { f.delete(); saved = listSignatures(dir); if (saved.isEmpty()) drawing = true }) {
                                    Icon(Icons.Filled.Delete, contentDescription = "Hapus tanda tangan", tint = Color(0xFF6B7280))
                                }
                            }
                        }
                    }
                    TextButton(onClick = { lines.clear(); current.clear(); drawing = true }) { Text("Buat tanda tangan baru") }
                } else {
                    Text("Tanda tangani di kotak ini dengan pena atau jari.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .height(170.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color.White)
                            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(12.dp))
                            .onSizeChanged { padW = it.width.coerceAtLeast(1); padH = it.height.coerceAtLeast(1) }
                            .semantics { contentDescription = "Papan tanda tangan" },
                    ) {
                        Canvas(
                            Modifier
                                .matchParentSize()
                                .pointerInput(Unit) {
                                    detectDragGestures(
                                        onDragStart = { o -> current.clear(); current.add(o) },
                                        onDrag = { ch, _ -> current.add(ch.position) },
                                        onDragEnd = { if (current.size > 1) lines.add(current.toList()); current.clear() },
                                        onDragCancel = { current.clear() },
                                    )
                                }
                                .pointerInput(Unit) { detectTapGestures { o -> lines.add(listOf(o, Offset(o.x + 0.5f, o.y))) } },
                        ) {
                            // Garis tempat tanda tangan.
                            drawLine(Color(0x33000000), Offset(size.width * 0.06f, size.height * 0.78f), Offset(size.width * 0.94f, size.height * 0.78f), strokeWidth = 2f)
                            val ink = Color(inks[inkIdx])
                            for (l in lines + listOf(current.toList())) {
                                if (l.size < 2) continue
                                val path = androidx.compose.ui.graphics.Path()
                                path.moveTo(l[0].x, l[0].y)
                                for (k in 1 until l.size) path.lineTo(l[k].x, l[k].y)
                                drawPath(path, ink, style = Stroke(width = 5f, cap = StrokeCap.Round, join = StrokeJoin.Round))
                            }
                        }
                    }
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        inks.forEachIndexed { i, col -> ColorDot(Color(col), i == inkIdx, { inkIdx = i }, size = 24.dp, description = "Warna tinta") }
                        Spacer(Modifier.weight(1f))
                        TextButton(onClick = { lines.clear(); current.clear() }) { Text("Ulangi") }
                        if (saved.isNotEmpty()) TextButton(onClick = { drawing = false }) { Text("Tersimpan") }
                    }
                }
            }
        },
        confirmButton = {
            if (drawing) {
                Button(
                    enabled = lines.isNotEmpty(),
                    onClick = {
                        val f = saveSignature(dir, lines.toList(), padW, padH, inks[inkIdx])
                        if (f != null) c.insertSignature(f) else c.signatureOpen = false
                    },
                ) { Text("Simpan & tempel") }
            }
        },
        dismissButton = { TextButton(onClick = { c.signatureOpen = false }) { Text("Tutup") } },
    )
}

private fun listSignatures(dir: File): List<File> =
    (dir.listFiles { f -> f.isFile && f.name.endsWith(".png") }?.toList() ?: emptyList()).sortedByDescending { it.lastModified() }

/** Gambar tanda tangan jadi PNG tembus pandang, dipangkas ke goresannya. */
private fun saveSignature(dir: File, lines: List<List<Offset>>, w: Int, h: Int, color: Int): File? {
    if (lines.isEmpty()) return null
    var l = Float.MAX_VALUE; var t = Float.MAX_VALUE; var r = -Float.MAX_VALUE; var b = -Float.MAX_VALUE
    for (ln in lines) for (o in ln) { l = min(l, o.x); t = min(t, o.y); r = max(r, o.x); b = max(b, o.y) }
    val pad = 10f
    l = max(0f, l - pad); t = max(0f, t - pad); r = min(w.toFloat(), r + pad); b = min(h.toFloat(), b + pad)
    if (r - l < 4f || b - t < 4f) return null
    // Digambar dua kali lebih besar supaya tetap tajam saat diperbesar di halaman.
    val k = 2f
    val bw = ((r - l) * k).toInt().coerceAtLeast(1)
    val bh = ((b - t) * k).toInt().coerceAtLeast(1)
    val bmp = Bitmap.createBitmap(bw, bh, Bitmap.Config.ARGB_8888)
    val cv = android.graphics.Canvas(bmp)
    val p = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
        style = android.graphics.Paint.Style.STROKE
        strokeWidth = 5f * k
        strokeCap = android.graphics.Paint.Cap.ROUND
        strokeJoin = android.graphics.Paint.Join.ROUND
        this.color = color
    }
    for (ln in lines) {
        if (ln.size < 2) continue
        val path = android.graphics.Path()
        path.moveTo((ln[0].x - l) * k, (ln[0].y - t) * k)
        for (q in 1 until ln.size) path.lineTo((ln[q].x - l) * k, (ln[q].y - t) * k)
        cv.drawPath(path, p)
    }
    val f = File(dir, "ttd_${System.currentTimeMillis()}.png")
    return runCatching {
        FileOutputStream(f).use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bmp.recycle()
        f
    }.getOrNull()
}

/** Pengaturan nomor halaman catatan. */
@Composable
internal fun PageNumbersDialog(c: EditorController, onDismiss: () -> Unit) {
    val cur = c.pageNumberSpec
    var on by remember { mutableStateOf(cur != null) }
    var pos by remember { mutableIntStateOf(cur?.position ?: 0) }
    var fmt by remember { mutableIntStateOf(cur?.format ?: 0) }
    var start by remember { mutableStateOf((cur?.start ?: 1).toString()) }
    var skip by remember { mutableStateOf(cur?.skipFirst ?: false) }
    var big by remember { mutableStateOf((cur?.size ?: 10f) > 11f) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Nomor halaman") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Tampilkan nomor halaman", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                    Switch(checked = on, onCheckedChange = { on = it }, modifier = Modifier.semantics { contentDescription = "Tampilkan nomor halaman" })
                }
                if (on) {
                    Text("Letak", style = MaterialTheme.typography.labelLarge)
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        PageNumberSpec.POSITIONS.forEachIndexed { i, s -> ToolChip(selected = pos == i, onClick = { pos = i }, label = { Text(s) }) }
                    }
                    Text("Bentuk", style = MaterialTheme.typography.labelLarge)
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        PageNumberSpec.FORMATS.forEachIndexed { i, s -> ToolChip(selected = fmt == i, onClick = { fmt = i }, label = { Text(s) }) }
                    }
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        OutlinedTextField(
                            value = start,
                            onValueChange = { v -> start = v.filter { it.isDigit() }.take(4) },
                            label = { Text("Mulai dari") },
                            singleLine = true,
                            modifier = Modifier.width(130.dp).semantics { contentDescription = "Nomor mulai" },
                        )
                        ToolChip(selected = big, onClick = { big = !big }, label = { Text("Huruf besar") })
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Halaman pertama (sampul) tanpa nomor", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                        Switch(checked = skip, onCheckedChange = { skip = it })
                    }
                    HorizontalDivider()
                    Text(
                        "Nomor tampil di tiap halaman catatan ini (juga halaman PDF), ikut ekspor PDF/gambar dan cetak.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        },
        confirmButton = {
            Button(onClick = {
                c.setPageNumbers(if (on) PageNumberSpec(pos, fmt, start.toIntOrNull()?.coerceIn(0, 9999) ?: 1, skip, if (big) 14f else 10f) else null)
                onDismiss()
            }) { Text("Simpan") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Batal") } },
    )
}

/** Petunjuk kecil di pojok kanvas saat mode baca: lanjut dari halaman terakhir dibaca. */
@Composable
internal fun ResumeReadingChip(c: EditorController, modifier: Modifier) {
    val i = c.resumeIndex ?: return
    Surface(modifier, shape = RoundedCornerShape(50), tonalElevation = 4.dp, shadowElevation = 6.dp, color = MaterialTheme.colorScheme.primaryContainer) {
        Row(Modifier.padding(start = 14.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(10.dp, 16.dp).background(Color(0xFFD84343)))
            Spacer(Modifier.width(8.dp))
            Text("Terakhir dibaca: halaman ${i + 1}", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onPrimaryContainer)
            TextButton(onClick = { c.resumeReading() }) { Text("Lanjutkan") }
            IconButton(onClick = { c.dismissResume() }) { Icon(Icons.Filled.Close, contentDescription = "Tutup penanda", tint = MaterialTheme.colorScheme.onPrimaryContainer) }
        }
    }
}
