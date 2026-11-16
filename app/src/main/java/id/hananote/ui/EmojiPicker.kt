package id.hananote.ui

import android.content.Context
import android.content.SharedPreferences
import android.graphics.Paint
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Satu emoji beserta nama Inggrisnya (untuk pencarian) dan apakah bisa diberi warna kulit. */
class Emoji(val text: String, val name: String, val tones: List<String>)

class EmojiGroup(val key: String, val label: String, val icon: String, val items: List<Emoji>)

/**
 * Daftar emoji lengkap (Unicode) dari aset aplikasi. Emoji yang belum didukung font perangkat
 * disaring supaya tidak tampil sebagai kotak kosong.
 */
object EmojiData {
    private val labels = linkedMapOf(
        "smileys" to ("Smiley & orang" to "😀"),
        "animals" to ("Hewan & alam" to "🐻"),
        "food" to ("Makanan & minuman" to "🍔"),
        "activities" to ("Aktivitas" to "⚽"),
        "travel" to ("Perjalanan & tempat" to "🚗"),
        "objects" to ("Benda" to "💡"),
        "symbols" to ("Simbol" to "🔣"),
        "flags" to ("Bendera" to "🏳️"),
    )

    @Volatile private var cache: List<EmojiGroup>? = null

    private val skinTones = listOf("🏻", "🏼", "🏽", "🏾", "🏿")

    fun load(context: Context): List<EmojiGroup> {
        cache?.let { return it }
        val paint = Paint()
        val byGroup = LinkedHashMap<String, ArrayList<Emoji>>()
        var group = ""
        context.assets.open("emoji.txt").bufferedReader().useLines { lines ->
            for (line in lines) {
                if (line.isEmpty() || line.startsWith("#")) continue
                if (line.startsWith("@")) { group = line.substring(1); continue }
                val parts = line.split('\t')
                if (parts.size < 3) continue
                val e = parts[0]
                if (!paint.hasGlyph(e)) continue
                val tones = if (parts[2] == "1") skinTones.map { toned(e, it) }.filter { paint.hasGlyph(it) } else emptyList()
                byGroup.getOrPut(group) { ArrayList() }.add(Emoji(e, parts[1], tones))
            }
        }
        val out = labels.mapNotNull { (key, v) ->
            val items = byGroup[key] ?: return@mapNotNull null
            EmojiGroup(key, v.first, v.second, items)
        }
        cache = out
        return out
    }

    /** Sisipkan warna kulit setelah karakter pertama (penanda gaya emoji di belakangnya dibuang). */
    private fun toned(e: String, tone: String): String {
        val first = Character.charCount(e.codePointAt(0))
        var rest = e.substring(first)
        if (rest.startsWith("️")) rest = rest.substring(1)
        return e.substring(0, first) + tone + rest
    }

    /** Kata pencarian bahasa Indonesia yang umum, diterjemahkan ke nama emoji (bahasa Inggris). */
    private val indo = mapOf(
        "senyum" to "smil", "tersenyum" to "smil", "tertawa" to "laugh", "ketawa" to "joy", "ngakak" to "rolling",
        "sedih" to "sad", "menangis" to "cry", "nangis" to "cry", "marah" to "angry", "kesal" to "pout",
        "cinta" to "love", "sayang" to "heart", "hati" to "heart", "jantung" to "heart", "cium" to "kiss",
        "kaget" to "astonish", "takut" to "fear", "malu" to "flush", "bingung" to "confus", "tidur" to "sleep",
        "ngantuk" to "sleep", "sakit" to "sick", "dingin" to "cold", "panas" to "hot", "berpikir" to "think",
        "mikir" to "think", "keren" to "cool", "kacamata" to "glasses", "jempol" to "thumbs", "tangan" to "hand",
        "tepuk" to "clap", "doa" to "pray", "berdoa" to "pray", "otot" to "bicep", "mata" to "eye",
        "orang" to "person", "laki" to "man", "pria" to "man", "perempuan" to "woman", "wanita" to "woman",
        "bayi" to "baby", "anak" to "child", "keluarga" to "family", "guru" to "teacher", "dokter" to "health",
        "petani" to "farmer", "masak" to "cook", "kucing" to "cat", "anjing" to "dog", "burung" to "bird",
        "ikan" to "fish", "sapi" to "cow", "ayam" to "chicken", "kambing" to "goat", "kuda" to "horse",
        "bunga" to "flower", "pohon" to "tree", "daun" to "leaf", "jeruk" to "tangerine", "lemon" to "lemon",
        "matahari" to "sun", "bulan" to "moon", "bintang" to "star", "hujan" to "rain", "awan" to "cloud",
        "api" to "fire", "air" to "water", "salju" to "snow", "pelangi" to "rainbow", "petir" to "lightning",
        "makan" to "food", "nasi" to "rice", "roti" to "bread", "kopi" to "coffee", "teh" to "tea", "susu" to "milk",
        "kue" to "cake", "buah" to "fruit", "sayur" to "vegetable", "mie" to "noodle", "telur" to "egg",
        "bola" to "ball", "sepak" to "soccer", "hadiah" to "gift", "kado" to "gift", "pesta" to "party",
        "ulang tahun" to "birthday", "musik" to "music", "mobil" to "car", "motor" to "motorcycle",
        "pesawat" to "airplane", "kapal" to "ship", "kereta" to "train", "rumah" to "house", "masjid" to "mosque",
        "sekolah" to "school", "uang" to "money", "duit" to "money", "buku" to "book", "pensil" to "pencil",
        "pena" to "pen", "telepon" to "phone", "hp" to "phone", "jam" to "clock", "waktu" to "clock",
        "kunci" to "key", "lampu" to "bulb", "ide" to "bulb", "kalender" to "calendar", "surat" to "envelope",
        "centang" to "check", "silang" to "cross", "panah" to "arrow", "tanya" to "question", "seru" to "exclamation",
        "peringatan" to "warning", "bendera" to "flag", "indonesia" to "indonesia", "botol" to "bottle",
        "tanaman" to "plant", "obat" to "pill", "kamera" to "camera", "laptop" to "laptop", "komputer" to "computer",
    )

    fun search(groups: List<EmojiGroup>, query: String): List<Emoji> {
        val q = query.trim().lowercase()
        if (q.isEmpty()) return emptyList()
        val terms = buildList {
            add(q)
            indo[q]?.let { add(it) }
            indo.forEach { (k, v) -> if (k.startsWith(q) && q.length >= 3) add(v) }
        }.distinct()
        val out = ArrayList<Emoji>()
        val seen = HashSet<String>()
        for (g in groups) for (e in g.items) {
            if (e.text in seen) continue
            if (terms.any { e.name.contains(it, ignoreCase = true) }) { out.add(e); seen.add(e.text) }
        }
        return out
    }
}

private sealed interface EmojiSlot {
    class Header(val label: String) : EmojiSlot
    class Item(val emoji: Emoji) : EmojiSlot
}

/**
 * Pemilih emoji seperti di WhatsApp: kategori, terakhir dipakai, pencarian (Indonesia/Inggris),
 * dan warna kulit (tekan lama). Emoji disisipkan sebagai teks besar yang bisa digeser dan diperbesar.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun EmojiPicker(prefs: SharedPreferences, onDismiss: () -> Unit, onPick: (String, Float) -> Unit) {
    val context = LocalContext.current
    val groups by produceState<List<EmojiGroup>?>(null) {
        value = withContext(Dispatchers.Default) { runCatching { EmojiData.load(context.applicationContext) }.getOrDefault(emptyList()) }
    }
    var query by remember { mutableStateOf("") }
    var size by remember { mutableFloatStateOf(prefs.getFloat("emoji_size", 40f)) }
    val recent = remember { prefs.getString("emoji_recent", "").orEmpty().split('\n').filter { it.isNotEmpty() } }
    val grid = rememberLazyGridState()
    val scope = rememberCoroutineScope()

    fun pick(e: String) {
        val next = (listOf(e) + recent.filter { it != e }).take(32)
        prefs.edit().putString("emoji_recent", next.joinToString("\n")).putFloat("emoji_size", size).apply()
        onPick(e, size)
    }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(
            Modifier.fillMaxWidth(0.94f).widthIn(max = 640.dp).fillMaxHeight(0.82f).heightIn(max = 720.dp),
            shape = RoundedCornerShape(24.dp),
            tonalElevation = 6.dp,
        ) {
            Column(Modifier.padding(top = 12.dp)) {
                Row(Modifier.padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = query,
                        onValueChange = { query = it },
                        singleLine = true,
                        leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                        placeholder = { Text("Cari emoji (senyum, hati, kopi…)") },
                        modifier = Modifier.weight(1f),
                    )
                    IconButton(onClick = onDismiss) { Icon(Icons.Filled.Close, contentDescription = "Tutup") }
                }
                val all = groups
                if (all == null) {
                    Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                } else {
                    // Susun isi kisi: judul kategori diikuti emojinya.
                    val recentItems = remember(all) {
                        val index = HashMap<String, Emoji>()
                        all.forEach { g -> g.items.forEach { e -> index[e.text] = e; e.tones.forEach { t -> index.putIfAbsent(t, Emoji(t, e.name, emptyList())) } } }
                        recent.mapNotNull { index[it] }
                    }
                    val sections = remember(all, recentItems) {
                        val cells = ArrayList<EmojiSlot>()
                        val starts = ArrayList<Pair<String, Int>>()
                        if (recentItems.isNotEmpty()) {
                            starts.add("🕘" to cells.size)
                            cells.add(EmojiSlot.Header("Terakhir dipakai"))
                            recentItems.forEach { cells.add(EmojiSlot.Item(it)) }
                        }
                        all.forEach { g ->
                            starts.add(g.icon to cells.size)
                            cells.add(EmojiSlot.Header(g.label))
                            g.items.forEach { cells.add(EmojiSlot.Item(it)) }
                        }
                        cells to starts
                    }
                    val results = remember(all, query) { EmojiData.search(all, query) }
                    val searching = query.isNotBlank()

                    if (!searching) {
                        val current by remember(sections) {
                            derivedStateOf {
                                val first = grid.firstVisibleItemIndex
                                sections.second.indexOfLast { it.second <= first }.coerceAtLeast(0)
                            }
                        }
                        Row(
                            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 8.dp, vertical = 6.dp),
                            horizontalArrangement = Arrangement.spacedBy(2.dp),
                        ) {
                            sections.second.forEachIndexed { i, (icon, start) ->
                                val sel = i == current
                                Box(
                                    Modifier
                                        .size(40.dp)
                                        .clip(CircleShape)
                                        .background(if (sel) MaterialTheme.colorScheme.primaryContainer else Color.Transparent)
                                        .clickable { scope.launch { grid.scrollToItem(start) } },
                                    contentAlignment = Alignment.Center,
                                ) { Text(icon, fontSize = 20.sp) }
                            }
                        }
                    }

                    val cells: List<EmojiSlot> = if (searching) results.map { EmojiSlot.Item(it) } else sections.first
                    if (searching && cells.isEmpty()) {
                        Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                            Text("Tidak ada emoji untuk “$query”.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    } else {
                        LazyVerticalGrid(
                            columns = GridCells.Adaptive(48.dp),
                            state = if (searching) rememberLazyGridState() else grid,
                            modifier = Modifier.fillMaxWidth().weight(1f).padding(horizontal = 6.dp),
                        ) {
                            items(
                                count = cells.size,
                                span = { i -> if (cells[i] is EmojiSlot.Header) GridItemSpan(maxLineSpan) else GridItemSpan(1) },
                            ) { i ->
                                when (val cell = cells[i]) {
                                    is EmojiSlot.Header -> Text(
                                        cell.label,
                                        style = MaterialTheme.typography.labelLarge,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.padding(start = 8.dp, top = 12.dp, bottom = 4.dp),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                    is EmojiSlot.Item -> EmojiCell(cell.emoji, onPick = { pick(it) })
                                }
                            }
                        }
                    }
                }
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text("Ukuran", style = MaterialTheme.typography.labelLarge)
                    listOf("Kecil" to 24f, "Sedang" to 40f, "Besar" to 72f).forEach { (label, pt) ->
                        FilterChip(selected = size == pt, onClick = { size = pt }, label = { Text(label) })
                    }
                    Spacer(Modifier.width(4.dp))
                    Text(
                        "Tekan lama untuk warna kulit",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun EmojiCell(e: Emoji, onPick: (String) -> Unit) {
    var tones by remember { mutableStateOf(false) }
    Box(
        Modifier
            .size(48.dp)
            .clip(RoundedCornerShape(10.dp))
            .combinedClickable(
                onClickLabel = e.name,
                onClick = { onPick(e.text) },
                onLongClick = { if (e.tones.isNotEmpty()) tones = true else onPick(e.text) },
            ),
        contentAlignment = Alignment.Center,
    ) {
        Text(e.text, fontSize = 28.sp)
        if (e.tones.isNotEmpty()) {
            DropdownMenu(expanded = tones, onDismissRequest = { tones = false }) {
                Row(Modifier.padding(horizontal = 6.dp)) {
                    (listOf(e.text) + e.tones).forEach { t ->
                        Box(
                            Modifier.size(46.dp).clip(RoundedCornerShape(10.dp)).clickable { tones = false; onPick(t) },
                            contentAlignment = Alignment.Center,
                        ) { Text(t, fontSize = 28.sp) }
                    }
                }
            }
        }
    }
}
