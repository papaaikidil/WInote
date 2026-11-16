package id.hananote.ink

import android.graphics.RectF
import com.google.android.gms.tasks.Task
import com.google.mlkit.common.model.DownloadConditions
import com.google.mlkit.common.model.RemoteModelManager
import com.google.mlkit.vision.digitalink.DigitalInkRecognition
import com.google.mlkit.vision.digitalink.DigitalInkRecognitionModel
import com.google.mlkit.vision.digitalink.DigitalInkRecognitionModelIdentifier
import com.google.mlkit.vision.digitalink.DigitalInkRecognizer
import com.google.mlkit.vision.digitalink.DigitalInkRecognizerOptions
import com.google.mlkit.vision.digitalink.Ink
import com.google.mlkit.vision.digitalink.RecognitionContext
import com.google.mlkit.vision.digitalink.WritingArea
import id.hananote.data.PageMeta
import id.hananote.data.PaperSize
import id.hananote.data.PaperTemplate
import id.hananote.data.Stroke
import com.google.mlkit.nl.languageid.LanguageIdentification
import com.google.mlkit.nl.languageid.LanguageIdentificationOptions
import com.google.mlkit.nl.languageid.LanguageIdentifier
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min

/**
 * Satu baris tulisan tangan beserta ukurannya.
 * Ukuran baris dipakai pengenal untuk membedakan huruf besar dan kecil, dan untuk menaruh teks
 * hasil bacaan tepat di garis tulisannya.
 */
class InkLine(
    /** Goresan baris ini, sesuai urutan menulis. */
    val strokes: List<Stroke>,
    val box: RectF,
    val color: Int,
    /** Garis dasar tempat huruf "duduk". */
    val baseline: Float,
    /** Perkiraan tinggi huruf kecil seperti a, n, o. */
    val xHeight: Float,
    /** Tinggi satu baris: jarak garis pada kertas bergaris, atau perkiraan dari tulisannya. */
    val lineHeight: Float,
    /** Lebar bidang tulis (lebar kertas). */
    val areaWidth: Float,
)

/** Garis pada kertas bergaris. Nilainya sama dengan yang digambar di kertas. */
class RuledLines(val first: Float, val gap: Float) {
    fun baseline(band: Int) = first + band * gap

    /**
     * Baris tempat sebuah titik tulisan berada. Satu baris mencakup ruang di atas garis
     * dan sedikit di bawahnya, karena huruf berekor (g, j, y) turun melewati garis.
     */
    fun bandOf(y: Float): Int = ceil((y - gap * 0.22f - first) / gap).toInt()

    companion object {
        const val GAP_MM = 8f

        fun of(page: PageMeta?): RuledLines? {
            if (page == null || page.template != PaperTemplate.LINED) return null
            val gap = PaperSize.mmToPt(GAP_MM)
            return RuledLines(gap * 3f, gap)
        }
    }
}

/** Bahasa tulisan tangan yang dibaca. */
enum class InkLanguage(val label: String) {
    AUTO("Otomatis (Indonesia + Inggris)"),
    ID("Bahasa Indonesia"),
    EN("Bahasa Inggris"),
}

/**
 * Pengenal tulisan tangan yang berjalan di perangkat.
 * Kamusnya diunduh sekali (perlu internet), setelah itu bekerja tanpa internet.
 *
 * Cara yang dipakai mengikuti saran ML Kit dan aplikasi catatan lain:
 * satu baris dibaca utuh, ukuran bidang tulis setinggi satu baris (bukan setinggi tulisannya,
 * supaya huruf kecil tidak terbaca huruf besar), teks sebelumnya dikirim sebagai konteks,
 * dan urutan serta waktu tiap goresan ikut dikirim.
 *
 * Mode otomatis membaca tiap baris dengan kamus Indonesia dan Inggris sekaligus, lalu memilih
 * hasil yang lebih meyakinkan: dari nilai keyakinan pengenal dan dari deteksi bahasa hasil bacaannya.
 */
object Handwriting {

    private const val TAG_ID = "id"
    private const val TAG_EN = "en-US"

    @Volatile var language: InkLanguage = InkLanguage.AUTO

    private val models = HashMap<String, DigitalInkRecognitionModel>()
    private val recognizers = HashMap<String, DigitalInkRecognizer>()

    private fun model(tag: String): DigitalInkRecognitionModel = synchronized(models) {
        models.getOrPut(tag) {
            val id = runCatching { DigitalInkRecognitionModelIdentifier.fromLanguageTag(tag) }.getOrNull()
                ?: DigitalInkRecognitionModelIdentifier.fromLanguageTag(TAG_EN)
                ?: error("Model tulisan tangan tidak tersedia")
            DigitalInkRecognitionModel.builder(id).build()
        }
    }

    private fun client(tag: String): DigitalInkRecognizer = synchronized(recognizers) {
        recognizers.getOrPut(tag) { DigitalInkRecognition.getClient(DigitalInkRecognizerOptions.builder(model(tag)).build()) }
    }

    private fun tags(lang: InkLanguage = language): List<String> = when (lang) {
        InkLanguage.AUTO -> listOf(TAG_ID, TAG_EN)
        InkLanguage.ID -> listOf(TAG_ID)
        InkLanguage.EN -> listOf(TAG_EN)
    }

    /** Kamus yang sudah pasti terunduh, supaya tidak ditanyakan berulang kali. */
    private val readyTags = java.util.concurrent.ConcurrentHashMap.newKeySet<String>()
    private val background = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    @Volatile private var extraDownloadStarted = false

    private suspend fun downloaded(tag: String): Boolean {
        if (tag in readyTags) return true
        val ok = runCatching { RemoteModelManager.getInstance().isModelDownloaded(model(tag)).awaitResult() == true }.getOrDefault(false)
        if (ok) readyTags.add(tag)
        return ok
    }

    /**
     * Kamus utama sudah siap. Pada mode otomatis kamus utamanya Indonesia; kamus Inggris dipakai
     * begitu tersedia, jadi menulis tetap bisa walau kamus Inggris belum terunduh.
     */
    suspend fun isReady(): Boolean = downloaded(tags()[0])

    suspend fun download(wifiOnly: Boolean = false) {
        val conditions = DownloadConditions.Builder().apply { if (wifiOnly) requireWifi() }.build()
        val list = tags()
        val first = list[0]
        if (!downloaded(first)) {
            RemoteModelManager.getInstance().download(model(first), conditions).awaitResult()
            readyTags.add(first)
        }
        for (tag in list.drop(1)) downloadInBackground(tag, wifiOnly)
    }

    /** Unduh kamus tambahan tanpa membuat penulis menunggu. */
    private fun downloadInBackground(tag: String, wifiOnly: Boolean) {
        if (tag in readyTags) return
        background.launch {
            if (downloaded(tag)) return@launch
            val conditions = DownloadConditions.Builder().apply { if (wifiOnly) requireWifi() }.build()
            runCatching {
                RemoteModelManager.getInstance().download(model(tag), conditions).awaitResult()
                readyTags.add(tag)
            }
        }
    }

    private class Reading(val texts: List<String>, val score: Float?)

    /**
     * Baca satu baris dan kembalikan beberapa kemungkinan, dari yang paling mungkin.
     *
     * [preContext] adalah teks tepat sebelum tulisan ini. Hasil yang diawali spasi berarti
     * pengenal menganggapnya kata baru; tanpa spasi berarti lanjutan kata sebelumnya.
     */
    suspend fun recognizeAll(line: InkLine, preContext: String): List<String> {
        // Bidang tulis setinggi satu baris, garis dasar di sekitar tiga perempat tingginya.
        val top = line.baseline - line.lineHeight * 0.72f
        val timed = line.strokes.all { it.times?.size == it.size }
        val builder = Ink.builder()
        for (s in line.strokes) {
            val sb = Ink.Stroke.builder()
            val t = s.times
            for (i in 0 until s.size) {
                val x = s.xs[i]
                val y = s.ys[i] - top
                sb.addPoint(if (timed && t != null) Ink.Point.create(x, y, t[i]) else Ink.Point.create(x, y))
            }
            builder.addStroke(sb.build())
        }
        val ink = builder.build()
        val context = RecognitionContext.builder()
            .setPreContext(preContext.takeLast(20))
            .setWritingArea(WritingArea(line.areaWidth.coerceAtLeast(1f), line.lineHeight.coerceAtLeast(1f)))
            .build()
        val lang = language
        if (lang != InkLanguage.AUTO) return read(tags(lang)[0], ink, context).texts
        if (!downloaded(TAG_EN)) {
            // Kamus Inggris belum ada: baca dengan kamus Indonesia dulu, kamus Inggris diunduh di latar.
            if (!extraDownloadStarted) {
                extraDownloadStarted = true
                downloadInBackground(TAG_EN, wifiOnly = false)
            }
            return read(TAG_ID, ink, context).texts
        }
        val pair = coroutineScope {
            val a = async { runCatching { read(TAG_ID, ink, context) }.getOrNull() }
            val b = async { runCatching { read(TAG_EN, ink, context) }.getOrNull() }
            a.await() to b.await()
        }
        val idR = pair.first
        val enR = pair.second
        if (idR == null || enR == null) return (idR ?: enR)?.texts ?: emptyList()
        val chosen = if (preferEnglish(idR, enR, preContext)) enR to idR else idR to enR
        // Pilihan utama dulu, lalu dua tebakan teratas dari bahasa lainnya sebagai pilihan lain.
        return (chosen.first.texts + chosen.second.texts.take(2)).distinctBy { it.trim() }.take(6)
    }

    private suspend fun read(tag: String, ink: Ink, context: RecognitionContext): Reading {
        val result = client(tag).recognize(ink, context).awaitResult()
        val texts = result.candidates
            .map { it.text.trimEnd() }
            .filter { it.isNotBlank() }
            .distinctBy { it.trim() }
            .take(6)
        return Reading(texts, result.candidates.firstOrNull()?.score)
    }

    /**
     * Bandingkan bacaan Indonesia dan Inggris. Bahasa Indonesia sedikit didahulukan,
     * jadi bacaan Inggris dipakai hanya bila jelas lebih meyakinkan.
     */
    private suspend fun preferEnglish(idR: Reading, enR: Reading, preContext: String): Boolean {
        val idTop = idR.texts.firstOrNull()?.trim() ?: return true
        val enTop = enR.texts.firstOrNull()?.trim() ?: return false
        if (idTop == enTop) return false
        val ctx = preContext.takeLast(60)
        val enConf = LanguageGuess.confidence("$ctx $enTop".trim(), setOf("en"))
        val idConf = LanguageGuess.confidence("$ctx $idTop".trim(), setOf("id", "ms"))
        val langVote = enConf - idConf
        val si = idR.score
        val se = enR.score
        val scoreVote = if (si != null && se != null) {
            ((si - se) / max(1f, max(abs(si), abs(se)))).coerceIn(-1f, 1f)
        } else 0f
        return langVote * 0.6f + scoreVote * 0.4f > 0.12f
    }

    /**
     * Urutan kemungkinan bacaan yang dipakai: yang pertama adalah pilihan utama.
     * Di tengah kalimat, huruf besar di awal kata lebih sering salah baca daripada benar,
     * jadi versi huruf kecil didahulukan bila pengenal juga menebaknya.
     */
    fun rank(candidates: List<String>, preContext: String): List<String> {
        if (candidates.size < 2) return candidates
        val pre = preContext.trimEnd()
        val midSentence = pre.isNotEmpty() && pre.last() !in ".!?:\n"
        if (!midSentence) return candidates
        val top = candidates[0].trimStart()
        if (top.isEmpty() || !top[0].isUpperCase()) return candidates
        val lowerIdx = (1 until minOf(candidates.size, 4)).firstOrNull { k ->
            val c = candidates[k].trimStart()
            c.isNotEmpty() && c[0].isLowerCase() && c.equals(top, ignoreCase = true)
        } ?: return candidates
        return listOf(candidates[lowerIdx]) + candidates.filterIndexed { k, _ -> k != lowerIdx }
    }

    /**
     * Sambungkan bacaan baru ke teks yang sudah ada di baris yang sama.
     * [gapEm] adalah jarak tulisan baru dari ujung teks, dalam ukuran huruf teks itu.
     */
    fun joiner(prefix: String, candidate: String, gapEm: Float): String {
        val c = candidate.trimStart()
        if (prefix.isEmpty()) return ""
        if (c.isNotEmpty() && c[0] in ".,!?;:)]}%'\"") return ""
        val last = prefix.last()
        if (last.isWhitespace() || last == '-' || last == '(' || last == '/') return ""
        // Pengenal menganggapnya kata baru, atau tulisannya jelas berjarak dari teks.
        if (candidate.startsWith(" ") || gapEm > 0.6f) return " "
        return ""
    }
}

/**
 * Kelompokkan goresan menjadi baris tulisan, dari atas ke bawah.
 *
 * Tiap goresan diwakili titik "badan"-nya (persentil ke-30 dari tinggi titik-titiknya), sehingga
 * ekor huruf g, j, y dan tiang huruf b, d, k tidak menariknya ke baris lain. Pada kertas bergaris,
 * garis kertas dipakai sebagai patokan baris. Kelompok yang ternyata satu baris (misalnya ekor
 * huruf yang terpisah) digabung kembali.
 */
object LineGrouper {

    private class Cluster(val members: ArrayList<Int>) {
        val box = RectF()
        var anchor = 0f
    }

    fun group(strokes: List<Stroke>, page: PageMeta? = null): List<InkLine> {
        if (strokes.isEmpty()) return emptyList()
        val n = strokes.size
        val boxes = Array(n) { rawBox(strokes[it]) }
        val anchors = FloatArray(n) { percentile(strokes[it].ys, 0.3f) }
        val heights = boxes.map { it.height() }.sorted()
        val unit = heights[heights.size / 2].coerceAtLeast(4f)
        var tiny = BooleanArray(n) { boxes[it].height() < unit * 0.35f && boxes[it].width() < unit * 0.8f }
        if (tiny.all { it }) tiny = BooleanArray(n)

        val ruled = RuledLines.of(page)?.takeIf { unit < it.gap * 1.4f }
        val big = (0 until n).filter { !tiny[it] }

        // 1. Kelompok awal.
        val clusters = ArrayList<Cluster>()
        if (ruled != null) {
            val byBand = sortedMapOf<Int, ArrayList<Int>>()
            for (i in big) byBand.getOrPut(ruled.bandOf(anchors[i])) { ArrayList() }.add(i)
            for (members in byBand.values) clusters.add(Cluster(members))
        } else {
            val order = big.sortedBy { anchors[it] }
            var cur = ArrayList<Int>()
            var prev = Float.NaN
            for (i in order) {
                if (cur.isNotEmpty() && anchors[i] - prev > unit * 0.6f) {
                    clusters.add(Cluster(cur)); cur = ArrayList()
                }
                cur.add(i)
                prev = anchors[i]
            }
            if (cur.isNotEmpty()) clusters.add(Cluster(cur))
        }
        for (c in clusters) refresh(c, boxes, anchors)

        // 2. Gabungkan kelompok bertetangga yang sebenarnya satu baris.
        var merged = true
        while (merged && clusters.size > 1) {
            merged = false
            clusters.sortBy { it.anchor }
            for (k in 0 until clusters.size - 1) {
                val a = clusters[k]
                val b = clusters[k + 1]
                val small = if (a.members.size <= b.members.size) a else b
                val large = if (small === a) b else a
                // Kelompok kecil (ekor huruf, palang huruf T) yang menumpang di baris lain,
                // atau dua kelompok yang badannya saling masuk ke baris satunya.
                val overlap = (min(small.box.bottom, large.box.bottom) - max(small.box.top, large.box.top)) /
                    small.box.height().coerceAtLeast(0.001f)
                val same = (small.members.size <= 2 && (inside(small.anchor, large.box) || overlap >= 0.4f)) ||
                    (inside(small.anchor, large.box) && inside(large.anchor, small.box))
                if (same) {
                    a.members.addAll(b.members)
                    refresh(a, boxes, anchors)
                    clusters.removeAt(k + 1)
                    merged = true
                    break
                }
            }
        }

        // 3. Titik, koma, dan coretan kecil ditempelkan ke baris yang paling cocok.
        val owner = HashMap<Int, Cluster>()
        for (c in clusters) for (i in c.members) owner[i] = c
        val bigMembers = big.filter { it in owner }
        for (i in 0 until n) {
            if (!tiny[i]) continue
            val dx = boxes[i].centerX()
            val cy = boxes[i].centerY()
            // Titik huruf i dan j: ada tiang huruf tepat di bawahnya.
            var target: Cluster? = bigMembers
                .filter { j ->
                    val b = boxes[j]
                    dx >= b.left - unit * 0.3f && dx <= b.right + unit * 0.3f &&
                        b.top - cy >= 0f && b.top - cy <= unit * 0.7f
                }
                .minByOrNull { boxes[it].top - cy }
                ?.let { owner[it] }
            if (target == null && ruled != null) {
                val band = ruled.bandOf(cy)
                target = clusters.firstOrNull { ruled.bandOf(it.anchor) == band && it.members.any { m -> m in owner } }
            }
            if (target == null) {
                // Selain itu: baris dengan goresan terdekat, diutamakan yang badannya setinggi titik ini.
                var bestScore = Float.MAX_VALUE
                var bestDist = Float.MAX_VALUE
                for (c in clusters) {
                    var d = Float.MAX_VALUE
                    for (j in c.members) {
                        if (j !in owner) continue
                        val b = boxes[j]
                        d = min(d, hypot(distToRange(dx, b.left, b.right), distToRange(cy, b.top, b.bottom)))
                    }
                    if (d == Float.MAX_VALUE) continue
                    val score = d + abs(c.anchor - cy) * 0.25f
                    if (score < bestScore) { bestScore = score; bestDist = d; target = c }
                }
                if (bestDist >= unit * 1.5f) target = null
            }
            if (target != null) {
                target.members.add(i)
            } else {
                clusters.add(Cluster(arrayListOf(i)).also { refresh(it, boxes, anchors) })
            }
        }

        return clusters.sortedBy { it.anchor }.map { c -> build(c, strokes, boxes, ruled, page) }
    }

    private fun refresh(c: Cluster, boxes: Array<RectF>, anchors: FloatArray) {
        c.box.set(boxes[c.members[0]])
        for (k in c.members) c.box.union(boxes[k])
        val a = c.members.map { anchors[it] }.sorted()
        c.anchor = a[a.size / 2]
    }

    private fun build(c: Cluster, strokes: List<Stroke>, boxes: Array<RectF>, ruled: RuledLines?, page: PageMeta?): InkLine {
        val idx = c.members.sorted() // urutan menulis
        val box = RectF(boxes[idx[0]])
        for (k in idx) box.union(boxes[k])
        val colorCount = idx.groupingBy { strokes[it].color }.eachCount()
        val color = colorCount.maxByOrNull { it.value }!!.key

        var total = 0
        for (k in idx) total += strokes[k].size
        val ys = FloatArray(total)
        var p = 0
        for (k in idx) {
            val s = strokes[k]
            System.arraycopy(s.ys, 0, ys, p, s.size)
            p += s.size
        }
        ys.sort()
        val q25 = ys[(ys.size * 0.25f).toInt().coerceIn(0, ys.lastIndex)]
        val q75 = ys[(ys.size * 0.75f).toInt().coerceIn(0, ys.lastIndex)]
        val q85 = ys[(ys.size * 0.85f).toInt().coerceIn(0, ys.lastIndex)]
        val xh = ((q75 - q25) * 1.6f).coerceIn(3f, max(3f, box.height()))

        val baseline: Float
        val lineHeight: Float
        if (ruled != null) {
            baseline = ruled.baseline(ruled.bandOf(c.anchor))
            lineHeight = ruled.gap
        } else {
            baseline = q85
            lineHeight = max(xh * 3f, box.height() * 1.05f)
        }
        val areaWidth = max(page?.width ?: 0f, box.right + 8f)
        return InkLine(idx.map { strokes[it] }, box, color, baseline, xh, lineHeight, areaWidth)
    }

    private fun inside(y: Float, box: RectF) = y >= box.top && y <= box.bottom

    private fun distToRange(v: Float, lo: Float, hi: Float): Float = when {
        v < lo -> lo - v
        v > hi -> v - hi
        else -> 0f
    }

    private fun percentile(values: FloatArray, q: Float): Float {
        if (values.isEmpty()) return 0f
        val sorted = values.copyOf()
        sorted.sort()
        return sorted[(sorted.size * q).toInt().coerceIn(0, sorted.lastIndex)]
    }

    private fun rawBox(s: Stroke): RectF {
        var l = s.xs[0]; var t = s.ys[0]; var r = s.xs[0]; var b = s.ys[0]
        for (i in 1 until s.size) {
            val x = s.xs[i]; val y = s.ys[i]
            if (x < l) l = x
            if (x > r) r = x
            if (y < t) t = y
            if (y > b) b = y
        }
        return RectF(l, t, r, b)
    }
}

/** Deteksi bahasa sebuah teks (di perangkat, tanpa internet). */
object LanguageGuess {
    private val client: LanguageIdentifier by lazy {
        LanguageIdentification.getClient(LanguageIdentificationOptions.Builder().setConfidenceThreshold(0.01f).build())
    }

    /** Keyakinan 0..1 bahwa [text] ditulis dalam salah satu bahasa [tags]. */
    suspend fun confidence(text: String, tags: Set<String>): Float {
        if (text.isBlank()) return 0f
        val list = runCatching { client.identifyPossibleLanguages(text).awaitResult() }.getOrNull() ?: return 0f
        return list.filter { it.languageTag in tags }.sumOf { it.confidence.toDouble() }.toFloat().coerceIn(0f, 1f)
    }
}

/** Menunggu hasil Task Google tanpa memblokir layar. */
suspend fun <T> Task<T>.awaitResult(): T = suspendCancellableCoroutine { cont ->
    addOnSuccessListener { cont.resume(it) }
    addOnFailureListener { cont.resumeWithException(it) }
    addOnCanceledListener { cont.cancel() }
}
