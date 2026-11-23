package id.hananote.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import id.hananote.ink.NoteSearch

/** Bilah cari di dalam catatan: kata dicari di semua halaman, hasilnya disorot dan bisa dituju satu per satu. */
@Composable
fun NoteSearchBar(c: EditorController, modifier: Modifier = Modifier) {
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
    var showList by remember { mutableStateOf(false) }
    val scheme = MaterialTheme.colorScheme
    val hasHits = c.searchHits.isNotEmpty()
    Surface(
        modifier.widthIn(max = 680.dp).fillMaxWidth().padding(horizontal = 12.dp),
        shape = RoundedCornerShape(22.dp),
        tonalElevation = 4.dp,
        shadowElevation = 6.dp,
    ) {
        Column {
            Row(Modifier.height(52.dp).padding(start = 14.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.Search, contentDescription = null, tint = scheme.onSurfaceVariant)
                Spacer(Modifier.width(8.dp))
                BasicTextField(
                    value = c.searchQuery,
                    onValueChange = { c.setSearch(it) },
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodyLarge.copy(color = scheme.onSurface),
                    cursorBrush = SolidColor(scheme.primary),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { c.nextHit() }),
                    modifier = Modifier
                        .weight(1f)
                        .focusRequester(focus)
                        .semantics { contentDescription = "Kata yang dicari" },
                    decorationBox = { inner ->
                        if (c.searchQuery.isEmpty()) {
                            Text("Cari di catatan ini", style = MaterialTheme.typography.bodyLarge, color = scheme.onSurfaceVariant)
                        }
                        inner()
                    },
                )
                val label = when {
                    c.searchQuery.isBlank() -> ""
                    !hasHits && c.searchBusy -> "Mencari…"
                    !hasHits -> "Tidak ketemu"
                    else -> "${c.searchAt.coerceAtLeast(0) + 1}/${c.searchHits.size}" + if (c.searchBusy) "…" else ""
                }
                Text(label, style = MaterialTheme.typography.labelLarge, color = scheme.onSurfaceVariant, maxLines = 1)
                IconButton(onClick = { c.prevHit() }, enabled = hasHits) {
                    Icon(Icons.Filled.KeyboardArrowUp, contentDescription = "Hasil sebelumnya")
                }
                IconButton(onClick = { c.nextHit() }, enabled = hasHits) {
                    Icon(Icons.Filled.KeyboardArrowDown, contentDescription = "Hasil berikutnya")
                }
                IconButton(onClick = { showList = !showList }, enabled = hasHits) {
                    Icon(
                        Icons.AutoMirrored.Filled.List,
                        contentDescription = if (showList) "Sembunyikan daftar hasil" else "Daftar hasil",
                        tint = if (showList) scheme.primary else scheme.onSurfaceVariant,
                    )
                }
                IconButton(onClick = { c.closeSearch() }) { Icon(Icons.Filled.Close, contentDescription = "Tutup pencarian") }
            }
            if (showList && hasHits) {
                HorizontalDivider(color = scheme.outlineVariant.copy(alpha = 0.5f))
                LazyColumn(Modifier.heightIn(max = 300.dp)) {
                    itemsIndexed(c.searchHits) { k, h ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .background(if (k == c.searchAt) scheme.primaryContainer.copy(alpha = 0.6f) else scheme.surface)
                                .clickable { c.goToHit(k) }
                                .padding(horizontal = 16.dp, vertical = 10.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                "Hal. ${h.pageIndex + 1}",
                                style = MaterialTheme.typography.labelMedium,
                                color = scheme.primary,
                                modifier = Modifier.width(56.dp),
                            )
                            Text(
                                marked(h.snippet, c.searchQuery.trim()),
                                style = MaterialTheme.typography.bodyMedium,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
            }
        }
    }
}

/** Cuplikan dengan kata yang dicari ditebalkan. */
private fun marked(text: String, q: String): AnnotatedString = buildAnnotatedString {
    append(text)
    for (at in NoteSearch.occurrences(text, q)) addStyle(SpanStyle(fontWeight = FontWeight.Bold), at, at + q.length)
}

/**
 * Daftar isi catatan: bab buatan sendiri (bisa diubah nama, dijadikan sub-bab, dihapus) dan daftar isi bawaan PDF.
 * Ketuk baris untuk langsung ke halamannya. [addFor]: panel dibuka untuk menambahkan halaman itu sebagai bab.
 */
@Composable
fun OutlineSheet(c: EditorController, addFor: Int?, onDismiss: () -> Unit) {
    LaunchedEffect(Unit) { c.refreshOutline() }
    var adding by remember { mutableStateOf(addFor) }
    var draft by remember { mutableStateOf("") }
    LaunchedEffect(adding) { adding?.let { draft = c.outlineSuggestion(it) } }
    var editing by remember { mutableStateOf(-1) }
    val scheme = MaterialTheme.colorScheme
    androidx.compose.ui.window.Dialog(onDismissRequest = onDismiss) {
        Surface(
            Modifier.widthIn(max = 560.dp).fillMaxWidth().heightIn(max = 640.dp),
            shape = RoundedCornerShape(24.dp),
            tonalElevation = 6.dp,
        ) {
            Column(Modifier.padding(vertical = 16.dp)) {
                Row(Modifier.padding(start = 24.dp, end = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("Daftar isi", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                    IconButton(onClick = onDismiss) { Icon(Icons.Filled.Close, contentDescription = "Tutup daftar isi") }
                }
                LazyColumn(Modifier.weight(1f, fill = false)) {
                    if (c.outline.isEmpty() && c.pdfOutline.isEmpty()) {
                        item {
                            Text(
                                "Belum ada bab. Tambahkan halaman ini sebagai bab, atau tahan gambar kecil halaman lalu " +
                                    "pilih \"Masukkan ke daftar isi\".",
                                style = MaterialTheme.typography.bodyMedium,
                                color = scheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp),
                            )
                        }
                    }
                    itemsIndexed(c.outline.toList()) { k, e ->
                        if (editing == k) {
                            OutlineEditRow(
                                initial = e.title,
                                onSave = { c.renameOutline(k, it); editing = -1 },
                                onCancel = { editing = -1 },
                            )
                        } else {
                            OutlineRow(
                                c, e,
                                onOpen = { c.goOutline(e); onDismiss() },
                                menu = { close ->
                                    androidx.compose.material3.DropdownMenuItem(
                                        text = { Text("Ubah nama") },
                                        onClick = { close(); editing = k },
                                    )
                                    androidx.compose.material3.DropdownMenuItem(
                                        text = { Text(if (e.level == 0) "Jadikan sub-bab" else "Jadikan bab") },
                                        onClick = { close(); c.setOutlineLevel(k, if (e.level == 0) 1 else 0) },
                                    )
                                    androidx.compose.material3.DropdownMenuItem(
                                        text = { Text("Hapus dari daftar isi") },
                                        onClick = { close(); c.removeOutline(k) },
                                    )
                                },
                            )
                        }
                    }
                    if (c.pdfOutline.isNotEmpty()) {
                        item {
                            Text(
                                "Daftar isi bawaan PDF",
                                style = MaterialTheme.typography.labelLarge,
                                color = scheme.primary,
                                modifier = Modifier.padding(start = 24.dp, top = 16.dp, bottom = 4.dp),
                            )
                        }
                        itemsIndexed(c.pdfOutline) { _, e ->
                            OutlineRow(c, e, onOpen = { c.goOutline(e); onDismiss() }, menu = null)
                        }
                    }
                }
                HorizontalDivider(color = scheme.outlineVariant.copy(alpha = 0.5f), modifier = Modifier.padding(top = 8.dp))
                val target = adding
                if (target != null) {
                    Column(Modifier.padding(horizontal = 24.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Judul bab untuk halaman ${target + 1}", style = MaterialTheme.typography.labelLarge)
                        androidx.compose.material3.OutlinedTextField(
                            value = draft,
                            onValueChange = { draft = it },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth().semantics { contentDescription = "Judul bab" },
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            androidx.compose.material3.Button(onClick = { c.addOutline(draft, target); adding = null }) { Text("Simpan bab") }
                            androidx.compose.material3.TextButton(onClick = { adding = null }) { Text("Batal") }
                        }
                    }
                } else {
                    androidx.compose.material3.TextButton(
                        onClick = { adding = c.index },
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                    ) { Text("+ Jadikan halaman ini bab (hal. ${c.index + 1})") }
                }
            }
        }
    }
}

@Composable
private fun OutlineRow(
    c: EditorController,
    e: id.hananote.data.OutlineEntry,
    onOpen: () -> Unit,
    menu: (@Composable (close: () -> Unit) -> Unit)?,
) {
    val scheme = MaterialTheme.colorScheme
    val page = c.outlinePage(e)
    val here = page == c.index
    var open by remember { mutableStateOf(false) }
    Row(
        Modifier
            .fillMaxWidth()
            .background(if (here) scheme.primaryContainer.copy(alpha = 0.5f) else scheme.surface)
            .clickable(onClick = onOpen)
            .padding(start = 24.dp + 20.dp * e.level, end = 8.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            e.title,
            style = if (e.level == 0) MaterialTheme.typography.bodyLarge else MaterialTheme.typography.bodyMedium,
            fontWeight = if (e.level == 0) FontWeight.Medium else FontWeight.Normal,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f).padding(vertical = 6.dp),
        )
        Text(
            if (page >= 0) "hal. ${page + 1}" else "",
            style = MaterialTheme.typography.labelMedium,
            color = scheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 8.dp),
        )
        if (menu != null) {
            androidx.compose.foundation.layout.Box {
                IconButton(onClick = { open = true }) {
                    Icon(Icons.Filled.MoreVert, contentDescription = "Atur bab ${e.title}")
                }
                androidx.compose.material3.DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
                    menu { open = false }
                }
            }
        } else {
            Spacer(Modifier.width(48.dp))
        }
    }
}

@Composable
private fun OutlineEditRow(initial: String, onSave: (String) -> Unit, onCancel: () -> Unit) {
    var text by remember { mutableStateOf(initial) }
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        androidx.compose.material3.OutlinedTextField(
            value = text,
            onValueChange = { text = it },
            singleLine = true,
            modifier = Modifier.weight(1f).semantics { contentDescription = "Nama bab" },
        )
        androidx.compose.material3.TextButton(onClick = { onSave(text) }) { Text("Simpan") }
        IconButton(onClick = onCancel) { Icon(Icons.Filled.Close, contentDescription = "Batal ubah nama") }
    }
}

/**
 * Sisipkan atau ubah link: ke website, ke halaman lain di catatan ini, atau ke catatan lain (dan halamannya).
 * [onConfirm] menerima alamat (website atau link WInote) dan nama yang ditampilkan.
 */
@Composable
fun LinkTargetDialog(
    c: EditorController,
    heading: String,
    initialUrl: String,
    initialLabel: String,
    confirm: String,
    onDismiss: () -> Unit,
    onConfirm: (String, String) -> Unit,
) {
    val parsed = id.hananote.data.InternalLink.parse(initialUrl)
    var tab by remember {
        mutableStateOf(
            when {
                parsed == null -> 0
                parsed.first == null || parsed.first == c.noteId -> 1
                else -> 2
            }
        )
    }
    var url by remember { mutableStateOf(if (parsed == null) initialUrl else "") }
    var label by remember { mutableStateOf(initialLabel) }
    var labelTouched by remember { mutableStateOf(initialLabel.isNotEmpty()) }
    var pageSel by remember { mutableStateOf(if (parsed != null && (parsed.first == null || parsed.first == c.noteId)) c.pages.indexOfFirst { it.id == parsed.second } else -1) }
    var noteSel by remember { mutableStateOf(if (parsed != null && parsed.first != null && parsed.first != c.noteId) parsed.first else null) }
    var notePage by remember { mutableStateOf(-1) }
    var query by remember { mutableStateOf("") }
    val scheme = MaterialTheme.colorScheme
    val otherNotes = remember { c.otherNotes() }
    val notePages = remember(noteSel) { noteSel?.let { c.pagesOf(it) }.orEmpty() }
    LaunchedEffect(notePages) {
        if (parsed != null && noteSel == parsed.first && parsed.second != null) {
            notePage = notePages.indexOfFirst { it.id == parsed.second }
        }
    }
    fun suggest(text: String) { if (!labelTouched) label = text }
    val target: String? = when (tab) {
        0 -> url.trim().ifEmpty { null }
        1 -> c.pages.getOrNull(pageSel)?.let { id.hananote.data.InternalLink.page(it.id) }
        else -> noteSel?.let { id.hananote.data.InternalLink.note(it, notePages.getOrNull(notePage)?.id) }
    }
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(heading) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ToolChip(selected = tab == 0, onClick = { tab = 0 }, label = { Text("Website") })
                    ToolChip(selected = tab == 1, onClick = { tab = 1 }, label = { Text("Halaman") })
                    ToolChip(selected = tab == 2, onClick = { tab = 2 }, label = { Text("Catatan lain") })
                }
                when (tab) {
                    0 -> androidx.compose.material3.OutlinedTextField(
                        value = url,
                        onValueChange = { url = it },
                        label = { Text("Alamat website") },
                        placeholder = { Text("contoh: titikbotanika.com") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Uri),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    1 -> LazyColumn(Modifier.heightIn(max = 260.dp)) {
                        itemsIndexed(c.pages.toList()) { i, _ ->
                            val line = remember(i) { c.pageLabel(i) }
                            PickRow(
                                title = "Halaman ${i + 1}",
                                subtitle = line,
                                selected = i == pageSel,
                                onClick = { pageSel = i; suggest(line.ifEmpty { "Halaman ${i + 1}" }) },
                            )
                        }
                    }
                    else -> {
                        if (noteSel == null) {
                            androidx.compose.material3.OutlinedTextField(
                                value = query,
                                onValueChange = { query = it },
                                label = { Text("Cari judul catatan") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth().semantics { contentDescription = "Cari judul catatan" },
                            )
                            val shown = otherNotes.filter { query.isBlank() || it.title.contains(query.trim(), ignoreCase = true) }
                            LazyColumn(Modifier.heightIn(max = 240.dp)) {
                                itemsIndexed(shown) { _, n ->
                                    PickRow(
                                        title = n.title,
                                        subtitle = c.bookTitle(n.bookId),
                                        selected = false,
                                        onClick = { noteSel = n.id; notePage = -1; suggest(n.title) },
                                    )
                                }
                            }
                        } else {
                            val n = otherNotes.firstOrNull { it.id == noteSel }
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(n?.title ?: "Catatan", style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                                androidx.compose.material3.TextButton(onClick = { noteSel = null }) { Text("Ganti") }
                            }
                            Text("Ke halaman", style = MaterialTheme.typography.labelLarge, color = scheme.onSurfaceVariant)
                            Row(
                                Modifier.horizontalScroll(androidx.compose.foundation.rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                            ) {
                                ToolChip(selected = notePage < 0, onClick = { notePage = -1; suggest(n?.title ?: "") }, label = { Text("Awal") })
                                for (k in notePages.indices) {
                                    ToolChip(
                                        selected = notePage == k,
                                        onClick = { notePage = k; suggest((n?.title ?: "Catatan") + " hal. ${k + 1}") },
                                        label = { Text("${k + 1}") },
                                    )
                                }
                            }
                        }
                    }
                }
                androidx.compose.material3.OutlinedTextField(
                    value = label,
                    onValueChange = { label = it; labelTouched = true },
                    label = { Text("Nama yang ditampilkan (boleh kosong)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            androidx.compose.material3.TextButton(
                onClick = { target?.let { onConfirm(it, label.trim()) } },
                enabled = target != null,
            ) { Text(confirm) }
        },
        dismissButton = { androidx.compose.material3.TextButton(onClick = onDismiss) { Text("Batal") } },
    )
}

@Composable
private fun PickRow(title: String, subtitle: String, selected: Boolean, onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Column(
        Modifier
            .fillMaxWidth()
            .background(if (selected) scheme.primaryContainer else scheme.surface, RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp),
    ) {
        Text(title, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
        if (subtitle.isNotEmpty()) {
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}
