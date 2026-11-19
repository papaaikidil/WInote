package id.hananote.ink

import android.content.Context
import android.graphics.RectF
import android.os.Handler
import android.os.Looper
import android.util.LruCache
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.io.MemoryUsageSetting
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.interactive.form.PDCheckBox
import com.tom_roush.pdfbox.pdmodel.interactive.form.PDChoice
import com.tom_roush.pdfbox.pdmodel.interactive.form.PDPushButton
import com.tom_roush.pdfbox.pdmodel.interactive.form.PDRadioButton
import com.tom_roush.pdfbox.pdmodel.interactive.form.PDSignatureField
import com.tom_roush.pdfbox.pdmodel.interactive.form.PDTerminalField
import com.tom_roush.pdfbox.pdmodel.interactive.form.PDTextField
import com.tom_roush.pdfbox.text.PDFTextStripper
import com.tom_roush.pdfbox.text.TextPosition
import java.io.File
import java.util.concurrent.Executors
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * Teks satu halaman PDF: semua huruf dalam urutan baca beserta kotaknya. Koordinat dalam pecahan 0..1 dari lebar dan
 * tinggi halaman seperti tampil (sudah memperhitungkan /CropBox dan /Rotate PDF-nya), titik kiri atas = 0.
 */
class PdfPageText(
    val chars: String,
    /** Kotak tiap huruf: kiri, atas, kanan, bawah (4 angka per huruf). */
    val box: FloatArray,
    /** Nomor baris tiap huruf. */
    val line: IntArray,
    /** Ukuran huruf tiap huruf, pecahan tinggi halaman. */
    val size: FloatArray,
    /** Huruf berkait (serif) dan tebal, menurut nama font PDF-nya. */
    val serif: BooleanArray,
    val bold: BooleanArray,
) {
    val isEmpty: Boolean get() = chars.isEmpty()
}

/** Kolom formulir PDF (AcroForm) di satu halaman. Kotak dalam pecahan 0..1 seperti [PdfPageText]. */
class PdfField(
    val name: String,
    val kind: Kind,
    val l: Float,
    val t: Float,
    val r: Float,
    val b: Float,
    /** Isi yang sudah ada di PDF. */
    val value: String,
    /** Pilihan untuk kolom daftar/kotak pilihan. */
    val options: List<String>,
    val multiline: Boolean,
    /** Batas jumlah huruf (0 = bebas). */
    val maxLen: Int,
) {
    enum class Kind { TEXT, CHECK, RADIO, CHOICE, SIGNATURE }
}

/**
 * Membaca teks dan kolom formulir PDF dengan PdfBox, di utas latar. Satu dokumen dibuka sekali dan dipakai ulang;
 * hasil tiap halaman disimpan sementara.
 */
object PdfText {
    @Volatile private var ready = false
    private var docPath: String? = null
    private var doc: PDDocument? = null
    private val texts = LruCache<String, PdfPageText>(40)
    private val forms = LruCache<String, List<PdfField>>(40)
    private val worker = Executors.newSingleThreadExecutor { r -> Thread(r, "hana-pdftext").apply { isDaemon = true } }
    private val main = Handler(Looper.getMainLooper())

    fun init(context: Context) {
        if (ready) return
        synchronized(this) {
            if (!ready) {
                PDFBoxResourceLoader.init(context.applicationContext)
                ready = true
            }
        }
    }

    private fun key(file: File, index: Int) = "${file.path}#$index"

    fun cachedText(file: File, index: Int): PdfPageText? = texts.get(key(file, index))
    fun cachedFields(file: File, index: Int): List<PdfField>? = forms.get(key(file, index))

    /** Baca teks halaman [index] di latar; [done] dipanggil di utas utama (null bila gagal). */
    fun loadText(context: Context, file: File, index: Int, done: (PdfPageText?) -> Unit) {
        cachedText(file, index)?.let { done(it); return }
        init(context)
        worker.execute {
            val r = runCatching { readText(file, index) }.onFailure { id.hananote.data.CrashLog.note("teks PDF gagal: ${it.javaClass.simpleName}") }.getOrNull()
            if (r != null) texts.put(key(file, index), r)
            main.post { done(r) }
        }
    }

    /** Baca kolom formulir halaman [index] di latar; [done] dipanggil di utas utama (daftar kosong bila tidak ada). */
    fun loadFields(context: Context, file: File, index: Int, done: (List<PdfField>) -> Unit) {
        cachedFields(file, index)?.let { done(it); return }
        init(context)
        worker.execute {
            val r = runCatching { readFields(file, index) }.onFailure { id.hananote.data.CrashLog.note("formulir PDF gagal: ${it.javaClass.simpleName}") }.getOrNull()
            if (r != null) forms.put(key(file, index), r)
            main.post { done(r.orEmpty()) }
        }
    }

    /** Tutup dokumen yang sedang terbuka (mis. saat catatan ditutup). */
    fun release() {
        worker.execute {
            synchronized(this) {
                runCatching { doc?.close() }
                doc = null
                docPath = null
            }
        }
    }

    private fun document(file: File): PDDocument {
        val d = doc
        if (d != null && docPath == file.path) return d
        runCatching { doc?.close() }
        doc = null
        docPath = null
        val nd = PDDocument.load(file, MemoryUsageSetting.setupMainMemoryOnly())
        doc = nd
        docPath = file.path
        return nd
    }

    /** Lebar dan tinggi halaman seperti tampil (poin), serta putarannya. */
    private class Disp(val w: Float, val h: Float, val rot: Int, val llx: Float, val ury: Float, val w0: Float, val h0: Float)

    private fun disp(page: PDPage): Disp {
        val cb = page.cropBox
        val rot = ((page.rotation % 360) + 360) % 360
        val w0 = cb.width
        val h0 = cb.height
        val swap = rot == 90 || rot == 270
        return Disp(if (swap) h0 else w0, if (swap) w0 else h0, rot, cb.lowerLeftX, cb.upperRightY, w0, h0)
    }

    private fun readText(file: File, index: Int): PdfPageText = synchronized(this) {
        val d = document(file)
        if (index !in 0 until d.numberOfPages) return PdfPageText("", FloatArray(0), IntArray(0), FloatArray(0), BooleanArray(0), BooleanArray(0))
        val ds = disp(d.getPage(index))
        val c = Collector(ds.rot)
        c.sortByPosition = true
        c.startPage = index + 1
        c.endPage = index + 1
        c.getText(d)
        val n = c.chars.length
        val box = FloatArray(n * 4)
        for (i in 0 until n) {
            box[i * 4] = (c.boxes[i * 4] / ds.w).coerceIn(0f, 1f)
            box[i * 4 + 1] = (c.boxes[i * 4 + 1] / ds.h).coerceIn(0f, 1f)
            box[i * 4 + 2] = (c.boxes[i * 4 + 2] / ds.w).coerceIn(0f, 1f)
            box[i * 4 + 3] = (c.boxes[i * 4 + 3] / ds.h).coerceIn(0f, 1f)
        }
        val size = FloatArray(n) { c.sizes[it] / ds.h }
        return PdfPageText(
            c.chars.toString(), box, c.lines.toIntArray(), size,
            BooleanArray(n) { c.serifs[it] }, BooleanArray(n) { c.bolds[it] },
        )
    }

    /** Pengumpul huruf beserta kotaknya (satuan poin, koordinat tampil). */
    private class Collector(private val rot: Int) : PDFTextStripper() {
        val chars = StringBuilder()
        val boxes = ArrayList<Float>()
        val lines = ArrayList<Int>()
        val sizes = ArrayList<Float>()
        val serifs = ArrayList<Boolean>()
        val bolds = ArrayList<Boolean>()
        private var lineNo = 0
        private var pendingSpace = false

        private fun add(ch: Char, l: Float, t: Float, r: Float, b: Float, size: Float, serif: Boolean, bold: Boolean) {
            chars.append(ch)
            boxes.add(l); boxes.add(t); boxes.add(r); boxes.add(b)
            lines.add(lineNo)
            sizes.add(size)
            serifs.add(serif)
            bolds.add(bold)
        }

        override fun writeString(text: String?, textPositions: MutableList<TextPosition>?) {
            for (tp in textPositions ?: return) {
                val u = tp.unicode ?: continue
                if (u.isEmpty()) continue
                val size = (if (tp.fontSizeInPt > 0.5f) tp.fontSizeInPt else tp.heightDir * 1.4f).coerceIn(1f, 400f)
                val fd = runCatching { tp.font?.fontDescriptor }.getOrNull()
                val asc = ((fd?.ascent ?: 0f) / 1000f).let { if (it in 0.5f..1.2f) it else 0.8f }
                val desc = (abs(fd?.descent ?: 0f) / 1000f).let { if (it in 0.05f..0.5f) it else 0.22f }
                val fontName = runCatching { tp.font?.name }.getOrNull().orEmpty().lowercase()
                val serif = listOf("times", "serif", "roman", "georgia", "cambria", "garamond", "book", "minion", "palatino")
                    .any { fontName.contains(it) } && !fontName.contains("sans")
                val bold = fontName.contains("bold") || fontName.contains("black") || fontName.contains("heavy")
                // Posisi menurut putaran halaman (x kiri, y garis dasar, titik kiri atas = 0).
                val x0 = tp.x
                val base = tp.y
                val w = tp.widthDirAdj.let { if (it > 0f) it else size * 0.5f }
                val top = base - asc * size
                val bot = base + desc * size
                if (pendingSpace && chars.isNotEmpty() && lines.last() == lineNo) {
                    // Spasi antarkata: kotaknya dari huruf sebelumnya sampai huruf ini.
                    val pr = boxes[boxes.size - 2]
                    add(' ', pr, top, max(x0, pr + 0.01f), bot, size, serif, bold)
                }
                pendingSpace = false
                if (u.length == 1) {
                    add(u[0], x0, top, x0 + w, bot, size, serif, bold)
                } else {
                    // Ligatur ("fi", "ffl"): lebarnya dibagi rata.
                    val step = w / u.length
                    for ((k, ch) in u.withIndex()) add(ch, x0 + k * step, top, x0 + (k + 1) * step, bot, size, serif, bold)
                }
            }
        }

        override fun writeWordSeparator() {
            pendingSpace = true
        }

        override fun writeLineSeparator() {
            if (lines.isNotEmpty() && lines.last() == lineNo) lineNo++
            pendingSpace = false
        }

        override fun writeParagraphStart() = Unit
        override fun writeParagraphEnd() = Unit
        override fun writePageStart() = Unit
        override fun writePageEnd() = Unit
    }

    private fun readFields(file: File, index: Int): List<PdfField> = synchronized(this) {
        val d = document(file)
        if (index !in 0 until d.numberOfPages) return emptyList()
        val form = d.documentCatalog?.acroForm ?: return emptyList()
        val page = d.getPage(index)
        val ds = disp(page)
        val pageAnnots = runCatching { page.annotations }.getOrNull().orEmpty()
        val out = ArrayList<PdfField>()
        for (field in form.fieldTree) {
            if (field !is PDTerminalField) continue
            if (field is PDPushButton) continue
            val kind = when (field) {
                is PDTextField -> PdfField.Kind.TEXT
                is PDCheckBox -> PdfField.Kind.CHECK
                is PDRadioButton -> PdfField.Kind.RADIO
                is PDChoice -> PdfField.Kind.CHOICE
                is PDSignatureField -> PdfField.Kind.SIGNATURE
                else -> continue
            }
            val value = runCatching { field.valueAsString }.getOrNull().orEmpty()
            val options = if (field is PDChoice) runCatching { field.optionsDisplayValues }.getOrNull().orEmpty() else emptyList()
            val multiline = field is PDTextField && runCatching { field.isMultiline }.getOrDefault(false)
            val maxLen = if (field is PDTextField) runCatching { field.maxLen }.getOrDefault(0).coerceAtLeast(0) else 0
            for (w in field.widgets) {
                val wp = w.page
                val onPage = if (wp != null) wp.cosObject == page.cosObject else pageAnnots.any { it.cosObject == w.cosObject }
                if (!onPage) continue
                val rect = w.rectangle ?: continue
                val (ax, ay) = map(ds, rect.lowerLeftX, rect.lowerLeftY)
                val (bx, by) = map(ds, rect.upperRightX, rect.upperRightY)
                val l = (min(ax, bx) / ds.w).coerceIn(0f, 1f)
                val r = (max(ax, bx) / ds.w).coerceIn(0f, 1f)
                val t = (min(ay, by) / ds.h).coerceIn(0f, 1f)
                val b = (max(ay, by) / ds.h).coerceIn(0f, 1f)
                if (r - l < 0.002f || b - t < 0.002f) continue
                out.add(PdfField(field.fullyQualifiedName ?: field.partialName ?: "", kind, l, t, r, b, value, options, multiline, maxLen))
            }
        }
        // Urutan baca: atas ke bawah, kiri ke kanan.
        out.sortWith(compareBy<PdfField> { (it.t * 200).toInt() }.thenBy { it.l })
        return out
    }

    /** Titik ruang PDF → koordinat tampil (poin, titik kiri atas = 0). */
    private fun map(ds: Disp, ux: Float, uy: Float): Pair<Float, Float> {
        val x = ux - ds.llx
        val y = ds.ury - uy
        return when (ds.rot) {
            90 -> (ds.h0 - y) to x
            180 -> (ds.w0 - x) to (ds.h0 - y)
            270 -> y to (ds.w0 - x)
            else -> x to y
        }
    }
}

/**
 * Teks PDF dalam satuan halaman catatan: kotak tiap huruf sudah dikali ukuran halaman (dan diputar bila halamannya
 * diputar), siap untuk mengenai, memilih, dan menyorot.
 */
class PdfTextLayer(val src: PdfPageText, pageW: Float, pageH: Float, rotation: Int) {
    val n = src.chars.length
    val l = FloatArray(n)
    val t = FloatArray(n)
    val r = FloatArray(n)
    val b = FloatArray(n)
    /** Ukuran huruf dalam satuan halaman. */
    val size = FloatArray(n)
    /** Huruf pertama dan sesudah terakhir tiap baris. */
    val lineStart: IntArray
    val lineEnd: IntArray

    init {
        val rot = ((rotation % 360) + 360) % 360
        val swap = rot == 90 || rot == 270
        // Tinggi halaman sebelum diputar (ukuran huruf mengikuti tinggi PDF aslinya).
        val srcH = if (swap) pageW else pageH
        for (i in 0 until n) {
            val x0 = src.box[i * 4]; val y0 = src.box[i * 4 + 1]; val x1 = src.box[i * 4 + 2]; val y1 = src.box[i * 4 + 3]
            val (ax, ay) = turn(x0, y0, rot)
            val (bx, by) = turn(x1, y1, rot)
            l[i] = min(ax, bx) * pageW; r[i] = max(ax, bx) * pageW
            t[i] = min(ay, by) * pageH; b[i] = max(ay, by) * pageH
            size[i] = src.size[i] * srcH
        }
        val lines = if (n == 0) 0 else src.line[n - 1] + 1
        lineStart = IntArray(lines) { -1 }
        lineEnd = IntArray(lines)
        for (i in 0 until n) {
            val k = src.line[i]
            if (lineStart[k] < 0) lineStart[k] = i
            lineEnd[k] = i + 1
        }
        for (k in 0 until lines) if (lineStart[k] < 0) { lineStart[k] = 0; lineEnd[k] = 0 }
    }

    private fun turn(x: Float, y: Float, rot: Int): Pair<Float, Float> = when (rot) {
        90 -> (1f - y) to x
        180 -> (1f - x) to (1f - y)
        270 -> y to (1f - x)
        else -> x to y
    }

    val lineCount: Int get() = lineStart.size

    fun lineOf(i: Int): Int = src.line[i]

    /** Kotak baris [k] (gabungan kotak hurufnya). */
    fun lineBox(k: Int, out: RectF): RectF {
        out.setEmpty()
        for (i in lineStart[k] until lineEnd[k]) if (!src.chars[i].isWhitespace()) out.union(l[i], t[i], r[i], b[i])
        return out
    }

    /**
     * Huruf di dekat titik (x, y): bila [maxDist] terbatas, null bila tidak ada huruf sedekat itu. Baris yang tinggi
     * hurufnya meliputi y diutamakan, lalu huruf terdekat di baris itu.
     */
    fun charAt(x: Float, y: Float, maxDist: Float = Float.MAX_VALUE): Int? {
        if (n == 0) return null
        var best = -1
        var bestD = Float.MAX_VALUE
        for (i in 0 until n) {
            val dx = if (x < l[i]) l[i] - x else if (x > r[i]) x - r[i] else 0f
            val dy = if (y < t[i]) t[i] - y else if (y > b[i]) y - b[i] else 0f
            // Jarak tegak lebih berat supaya titik di antara dua baris memilih baris yang tepat.
            val d = dx + dy * 3f
            if (d < bestD) { bestD = d; best = i }
        }
        if (best < 0) return null
        val dx = if (x < l[best]) l[best] - x else if (x > r[best]) x - r[best] else 0f
        val dy = if (y < t[best]) t[best] - y else if (y > b[best]) y - b[best] else 0f
        if (dx > maxDist || dy > maxDist) return null
        return best
    }

    /** Huruf awal dan sesudah akhir kata di sekitar huruf [i]. */
    fun wordRange(i: Int): IntRange {
        var a = i
        var z = i
        val line = src.line[i]
        while (a > 0 && src.line[a - 1] == line && !src.chars[a - 1].isWhitespace()) a--
        while (z < n - 1 && src.line[z + 1] == line && !src.chars[z + 1].isWhitespace()) z++
        return a..z
    }

    fun lineRange(i: Int): IntRange {
        val k = src.line[i]
        return lineStart[k] until lineEnd[k]
    }

    /** Kotak sorotan untuk huruf [from]..[to] (inklusif): satu kotak per baris. */
    fun rects(from: Int, to: Int): List<RectF> {
        if (n == 0) return emptyList()
        val a = min(from, to).coerceIn(0, n - 1)
        val z = max(from, to).coerceIn(0, n - 1)
        val out = ArrayList<RectF>()
        var cur: RectF? = null
        var curLine = -1
        for (i in a..z) {
            val ln = src.line[i]
            if (ln != curLine) {
                cur?.let { if (!it.isEmpty) out.add(it) }
                cur = RectF()
                curLine = ln
            }
            if (!src.chars[i].isWhitespace() || (i in (a + 1) until z)) cur!!.union(l[i], t[i], r[i], b[i])
        }
        cur?.let { if (!it.isEmpty) out.add(it) }
        return out
    }

    /** Teks huruf [from]..[to] (inklusif), baris dipisah ganti baris. */
    fun text(from: Int, to: Int): String {
        if (n == 0) return ""
        val a = min(from, to).coerceIn(0, n - 1)
        val z = max(from, to).coerceIn(0, n - 1)
        val sb = StringBuilder()
        for (i in a..z) {
            if (i > a && src.line[i] != src.line[i - 1]) sb.append('\n')
            sb.append(src.chars[i])
        }
        return sb.toString().trim()
    }

    /** Ukuran huruf yang paling umum di huruf [from]..[to]. */
    fun typicalSize(from: Int, to: Int): Float {
        val a = min(from, to).coerceIn(0, n - 1)
        val z = max(from, to).coerceIn(0, n - 1)
        val xs = (a..z).filter { !src.chars[it].isWhitespace() }.map { size[it] }.sorted()
        return if (xs.isEmpty()) 12f else xs[xs.size / 2]
    }

    fun serifAt(i: Int) = src.serif[i]
    fun boldAt(i: Int) = src.bold[i]
}

/** Kolom formulir beserta kotaknya dalam satuan halaman catatan. */
class PdfFieldBox(val field: PdfField, val rect: RectF)

/** Pilihan teks PDF: huruf [from]..[to] (inklusif), isinya, dan kotaknya di layar (untuk menaruh bilah aksi). */
class PdfSelection(val from: Int, val to: Int, val text: String, val anchor: RectF)

/** Ubah kotak pecahan halaman PDF (seperti tampil di PDF-nya) ke satuan halaman catatan yang mungkin diputar. */
object PdfGeom {
    fun toPage(l: Float, t: Float, r: Float, b: Float, pageW: Float, pageH: Float, rotation: Int): RectF {
        val rot = ((rotation % 360) + 360) % 360
        fun turn(x: Float, y: Float): Pair<Float, Float> = when (rot) {
            90 -> (1f - y) to x
            180 -> (1f - x) to (1f - y)
            270 -> y to (1f - x)
            else -> x to y
        }
        val (ax, ay) = turn(l, t)
        val (bx, by) = turn(r, b)
        return RectF(min(ax, bx) * pageW, min(ay, by) * pageH, max(ax, bx) * pageW, max(ay, by) * pageH)
    }
}
