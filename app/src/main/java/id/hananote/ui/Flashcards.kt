package id.hananote.ui

import android.graphics.Bitmap
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Style
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import id.hananote.data.StudyCard

/** Lama menunggu dalam kata-kata: "5 mnt", "12 jam", "3 hari". */
private fun waitLabel(ms: Long): String = when {
    ms <= 0L -> "sekarang"
    ms < 3_600_000L -> "${(ms / 60_000L).coerceAtLeast(1)} mnt"
    ms < 86_400_000L -> "${ms / 3_600_000L} jam"
    else -> "${ms / 86_400_000L} hari"
}

private fun StudyCard.isDue(now: Long) = due <= now

/** Keterangan kapan kartu perlu diulang. */
private fun dueLabel(card: StudyCard, now: Long): String = when {
    card.box == 0 && card.due == 0L -> "Baru"
    card.isDue(now) -> "Perlu diulang"
    else -> "Ulang lagi ${waitLabel(card.due - now)}"
}

/**
 * Kartu hafalan: tiap selotip hafalan otomatis jadi kartu (depannya tertutup, belakangnya terbuka), ditambah kartu
 * tanya-jawab yang ditulis sendiri. Kartu diulang dengan jarak yang makin lama bila sudah hafal.
 */
@Composable
fun FlashcardsSheet(c: EditorController, onDismiss: () -> Unit) {
    LaunchedEffect(Unit) { c.loadCards() }
    // 0 daftar kartu, 1 tulis/ubah kartu, 2 belajar, 3 selesai belajar.
    var screen by remember { mutableIntStateOf(0) }
    var editing by remember { mutableStateOf<StudyCard?>(null) }
    var queue by remember { mutableStateOf<List<String>>(emptyList()) }
    var at by remember { mutableIntStateOf(0) }
    var reviewed by remember { mutableIntStateOf(0) }
    val openPage: (Int) -> Unit = { i -> if (i >= 0) { c.showPage(i); onDismiss() } }
    fun start(all: Boolean) {
        val now = System.currentTimeMillis()
        val pick = c.cards.filter { all || it.isDue(now) }.sortedBy { it.due }
        if (pick.isEmpty()) return
        queue = pick.map { it.id }
        at = 0
        reviewed = 0
        screen = 2
    }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(
            Modifier.padding(16.dp).widthIn(max = 680.dp).fillMaxWidth().heightIn(max = 780.dp),
            shape = RoundedCornerShape(24.dp),
            tonalElevation = 6.dp,
        ) {
            when (screen) {
                1 -> CardEditor(
                    c, editing,
                    onDone = { screen = 0; editing = null },
                )
                2 -> {
                    val card = queue.getOrNull(at)?.let { id -> c.cards.firstOrNull { it.id == id } }
                    if (card == null) {
                        screen = 3
                    } else {
                        StudyView(
                            c, card, at, queue.size,
                            onBack = { screen = 0 },
                            onOpenPage = openPage,
                            onRate = { r ->
                                c.rateCard(card.id, r)
                                reviewed++
                                // Yang lupa ditanyakan lagi di akhir sesi ini.
                                if (r == 0) queue = queue + card.id
                                at++
                                if (at >= queue.size) screen = 3
                            },
                        )
                    }
                }
                3 -> StudyDone(
                    c, reviewed,
                    onList = { screen = 0 },
                    onClose = onDismiss,
                )
                else -> CardList(
                    c,
                    onClose = onDismiss,
                    onNew = { editing = null; screen = 1 },
                    onEdit = { editing = it; screen = 1 },
                    onStart = { all -> start(all) },
                    onOpenPage = openPage,
                )
            }
        }
    }
}

@Composable
private fun CardList(
    c: EditorController,
    onClose: () -> Unit,
    onNew: () -> Unit,
    onEdit: (StudyCard) -> Unit,
    onStart: (all: Boolean) -> Unit,
    onOpenPage: (Int) -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val now = remember(c.cards.toList()) { System.currentTimeMillis() }
    val due = c.cards.count { it.isDue(now) }
    val tapes = c.cards.filter { it.isTape }
    val written = c.cards.filter { !it.isTape }
    Column(Modifier.padding(vertical = 16.dp)) {
        Row(Modifier.padding(start = 24.dp, end = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.Style, contentDescription = null, tint = scheme.primary)
            Spacer(Modifier.width(10.dp))
            Text("Kartu hafalan", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
            IconButton(onClick = onClose) { Icon(Icons.Filled.Close, contentDescription = "Tutup kartu hafalan") }
        }
        Text(
            if (c.cardsLoading) "Memuat kartu…"
            else "${c.cards.size} kartu · $due perlu diulang sekarang",
            style = MaterialTheme.typography.bodyMedium,
            color = scheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 24.dp),
        )
        Row(
            Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (due > 0 || c.cards.isEmpty()) {
                Button(onClick = { onStart(false) }, enabled = due > 0) { Text("Mulai belajar ($due)") }
            } else {
                Button(onClick = { onStart(true) }) { Text("Ulangi semua kartu") }
            }
            OutlinedButton(onClick = onNew) { Text("+ Kartu") }
        }
        HorizontalDivider(color = scheme.outlineVariant.copy(alpha = 0.5f))
        LazyColumn(Modifier.weight(1f, fill = false)) {
            if (c.cardsLoading && c.cards.isEmpty()) {
                item {
                    Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(Modifier.size(28.dp))
                    }
                }
            } else if (c.cards.isEmpty()) {
                item {
                    Text(
                        "Belum ada kartu. Tempel selotip hafalan di atas jawaban (Sisipkan → Selotip): tiap selotip " +
                            "otomatis jadi kartu. Atau tulis kartu tanya-jawab sendiri dengan \"+ Kartu\".",
                        style = MaterialTheme.typography.bodyMedium,
                        color = scheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 24.dp, vertical = 16.dp),
                    )
                }
            }
            if (written.isNotEmpty()) {
                item { SectionLabel("Kartu tanya-jawab (${written.size})") }
                items(written, key = { it.id }) { card ->
                    CardRow(
                        title = card.front,
                        subtitle = card.back.ifBlank { "(jawaban kosong)" },
                        page = c.cardPage(card),
                        status = dueLabel(card, now),
                        due = card.isDue(now),
                        menuLabel = "Atur kartu ${card.front}",
                    ) { close ->
                        DropdownMenuItem(text = { Text("Ubah kartu") }, onClick = { close(); onEdit(card) })
                        val p = c.cardPage(card)
                        if (p >= 0) DropdownMenuItem(text = { Text("Buka halaman ${p + 1}") }, onClick = { close(); onOpenPage(p) })
                        DropdownMenuItem(text = { Text("Hafalkan dari awal") }, onClick = { close(); c.resetCard(card.id) })
                        DropdownMenuItem(text = { Text("Hapus kartu") }, onClick = { close(); c.deleteCard(card.id) })
                    }
                }
            }
            if (tapes.isNotEmpty()) {
                item { SectionLabel("Dari selotip hafalan (${tapes.size})") }
                var lastPage = -2
                var nth = 0
                val labels = tapes.map { card ->
                    val p = c.cardPage(card)
                    if (p != lastPage) { lastPage = p; nth = 0 }
                    nth++
                    p to nth
                }
                items(tapes.size, key = { tapes[it].id }) { k ->
                    val card = tapes[k]
                    val (p, n) = labels[k]
                    CardRow(
                        title = "Selotip $n di halaman ${p + 1}",
                        subtitle = "Isi yang tertutup selotip",
                        page = -1,
                        status = dueLabel(card, now),
                        due = card.isDue(now),
                        menuLabel = "Atur selotip $n halaman ${p + 1}",
                    ) { close ->
                        if (p >= 0) DropdownMenuItem(text = { Text("Buka halaman ${p + 1}") }, onClick = { close(); onOpenPage(p) })
                        DropdownMenuItem(text = { Text("Hafalkan dari awal") }, onClick = { close(); c.resetCard(card.id) })
                    }
                }
            }
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 24.dp, top = 14.dp, bottom = 4.dp),
    )
}

@Composable
private fun CardRow(
    title: String,
    subtitle: String,
    page: Int,
    status: String,
    due: Boolean,
    menuLabel: String,
    menu: @Composable (close: () -> Unit) -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    var open by remember { mutableStateOf(false) }
    Row(
        Modifier.fillMaxWidth().padding(start = 24.dp, end = 8.dp, top = 4.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f).padding(vertical = 4.dp)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(
                if (page >= 0) "$subtitle · hal. ${page + 1}" else subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = scheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Text(
            status,
            style = MaterialTheme.typography.labelMedium,
            color = if (due) scheme.primary else scheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 8.dp),
        )
        Box {
            IconButton(onClick = { open = true }) { Icon(Icons.Filled.MoreVert, contentDescription = menuLabel) }
            DropdownMenu(expanded = open, onDismissRequest = { open = false }) { menu { open = false } }
        }
    }
}

@Composable
private fun CardEditor(c: EditorController, card: StudyCard?, onDone: () -> Unit) {
    var front by remember { mutableStateOf(card?.front ?: "") }
    var back by remember { mutableStateOf(card?.back ?: "") }
    var here by remember { mutableStateOf(true) }
    Column(
        Modifier.verticalScroll(rememberScrollState()).padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(if (card == null) "Kartu baru" else "Ubah kartu", style = MaterialTheme.typography.titleLarge)
        OutlinedTextField(
            value = front,
            onValueChange = { front = it },
            label = { Text("Pertanyaan") },
            minLines = 2,
            modifier = Modifier.fillMaxWidth().semantics { contentDescription = "Pertanyaan" },
        )
        OutlinedTextField(
            value = back,
            onValueChange = { back = it },
            label = { Text("Jawaban") },
            minLines = 3,
            modifier = Modifier.fillMaxWidth().semantics { contentDescription = "Jawaban" },
        )
        if (card == null) {
            Row(
                Modifier.clickable { here = !here },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Checkbox(checked = here, onCheckedChange = { here = it })
                Text("Tautkan ke halaman ini (hal. ${c.index + 1})", style = MaterialTheme.typography.bodyMedium)
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                onClick = {
                    if (card == null) c.addCard(front, back, here) else c.editCard(card.id, front, back)
                    onDone()
                },
                enabled = front.isNotBlank(),
            ) { Text("Simpan kartu") }
            TextButton(onClick = onDone) { Text("Batal") }
        }
    }
}

@Composable
private fun StudyView(
    c: EditorController,
    card: StudyCard,
    at: Int,
    total: Int,
    onBack: () -> Unit,
    onOpenPage: (Int) -> Unit,
    onRate: (Int) -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    var revealed by remember(card.id, at) { mutableStateOf(false) }
    val page = c.cardPage(card)
    Column(Modifier.padding(vertical = 16.dp)) {
        Row(Modifier.padding(start = 8.dp, end = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Kembali ke daftar kartu") }
            Text("Kartu ${at + 1} dari $total", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            if (page >= 0) TextButton(onClick = { onOpenPage(page) }) { Text("Buka hal. ${page + 1}") }
        }
        Column(
            Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 8.dp),
        ) {
            Surface(
                Modifier.fillMaxWidth().heightIn(min = 220.dp).clickable(enabled = !revealed) { revealed = true },
                shape = RoundedCornerShape(18.dp),
                color = scheme.surface,
                border = BorderStroke(1.dp, scheme.outlineVariant),
                shadowElevation = 2.dp,
            ) {
                if (card.isTape) {
                    TapeCardFace(c, card, revealed)
                } else {
                    Column(
                        Modifier.fillMaxWidth().padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                    ) {
                        Text("Pertanyaan", style = MaterialTheme.typography.labelMedium, color = scheme.onSurfaceVariant)
                        Spacer(Modifier.height(8.dp))
                        Text(card.front, style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center)
                        if (revealed) {
                            HorizontalDivider(Modifier.padding(vertical = 18.dp), color = scheme.outlineVariant)
                            Text("Jawaban", style = MaterialTheme.typography.labelMedium, color = scheme.onSurfaceVariant)
                            Spacer(Modifier.height(8.dp))
                            Text(
                                card.back.ifBlank { "(jawaban kosong)" },
                                style = MaterialTheme.typography.titleLarge,
                                color = scheme.primary,
                                textAlign = TextAlign.Center,
                            )
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        if (!revealed) {
            Button(
                onClick = { revealed = true },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp).height(52.dp),
            ) { Text("Tampilkan jawaban") }
        } else {
            val now = System.currentTimeMillis()
            Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                RateButton("Lupa", waitLabel(card.rated(0, now).due - now), scheme.errorContainer, scheme.onErrorContainer, Modifier.weight(1f)) { onRate(0) }
                RateButton("Ragu", waitLabel(card.rated(1, now).due - now), scheme.tertiaryContainer, scheme.onTertiaryContainer, Modifier.weight(1f)) { onRate(1) }
                RateButton("Ingat", waitLabel(card.rated(2, now).due - now), scheme.primaryContainer, scheme.onPrimaryContainer, Modifier.weight(1f)) { onRate(2) }
            }
        }
    }
}

@Composable
private fun RateButton(
    label: String,
    wait: String,
    bg: androidx.compose.ui.graphics.Color,
    fg: androidx.compose.ui.graphics.Color,
    modifier: Modifier,
    onClick: () -> Unit,
) {
    Button(
        onClick = onClick,
        modifier = modifier.height(60.dp).semantics { contentDescription = label },
        colors = ButtonDefaults.buttonColors(containerColor = bg, contentColor = fg),
        shape = RoundedCornerShape(14.dp),
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(label, fontWeight = FontWeight.SemiBold)
            Text(wait, style = MaterialTheme.typography.labelSmall)
        }
    }
}

/** Potongan halaman di sekitar selotip: tertutup sebelum jawaban dilihat, terbuka sesudahnya. */
@Composable
private fun TapeCardFace(c: EditorController, card: StudyCard, open: Boolean) {
    val bmp by produceState<Bitmap?>(null, card.id, open) {
        value = runCatching { c.tapeCardImage(card, open, 1200) }.getOrNull()
    }
    Column(Modifier.fillMaxWidth().padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            if (open) "Jawaban (selotip dibuka)" else "Apa yang tertutup selotip? Ketuk untuk melihat jawaban.",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 8.dp),
        )
        val b = bmp
        if (b == null) {
            Box(Modifier.fillMaxWidth().height(180.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(Modifier.size(28.dp))
            }
        } else {
            Image(
                b.asImageBitmap(),
                contentDescription = if (open) "Halaman dengan selotip terbuka" else "Halaman dengan selotip tertutup",
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 380.dp)
                    .background(androidx.compose.ui.graphics.Color.White, RoundedCornerShape(8.dp)),
            )
        }
    }
}

@Composable
private fun StudyDone(c: EditorController, reviewed: Int, onList: () -> Unit, onClose: () -> Unit) {
    val now = System.currentTimeMillis()
    val next = c.cards.filter { !it.isDue(now) }.minOfOrNull { it.due }
    val left = c.cards.count { it.isDue(now) }
    Column(
        Modifier.fillMaxWidth().padding(28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Icon(Icons.Filled.Style, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(40.dp))
        Text("Selesai belajar", style = MaterialTheme.typography.headlineSmall)
        Text("$reviewed kartu diulang.", style = MaterialTheme.typography.bodyLarge)
        Text(
            when {
                left > 0 -> "$left kartu masih perlu diulang."
                next != null -> "Kartu berikutnya perlu diulang ${waitLabel(next - now)} lagi."
                else -> "Semua kartu sudah diulang."
            },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 8.dp)) {
            OutlinedButton(onClick = onList) { Text("Kembali ke daftar") }
            Button(onClick = onClose) { Text("Tutup") }
        }
    }
}
