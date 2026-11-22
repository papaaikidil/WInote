package id.hananote.data

import android.graphics.RectF
import android.text.StaticLayout
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

/**
 * Jenis goresan. Urutannya tersimpan di berkas, jadi jenis baru selalu ditambahkan di akhir.
 * LINE dipakai untuk bentuk (garis lurus, kotak, lingkaran, dan lainnya) dan tidak tampil di baki alat.
 */
enum class Tool(val label: String) {
    PEN("Pena"),
    PENCIL("Pensil"),
    BRUSH("Kuas"),
    MARKER("Spidol"),
    HIGHLIGHTER("Stabilo"),
    ERASER("Penghapus"),
    LINE("Bentuk"),
    WATERCOLOR("Cat air"),
    /** Selotip hafalan: bukan goresan, hanya menyimpan warna dan ketebalan alat Selotip. */
    TAPE("Selotip"),
    /** Qalam untuk khat Arab: ujung pipih ±70°, tarikan mendatar tebal dan alif ramping. */
    QALAM("Qalam");

    val minWidth: Float
        get() = when (this) {
            PEN -> 0.8f; PENCIL -> 0.6f; BRUSH -> 2f; MARKER -> 2f; HIGHLIGHTER -> 6f; ERASER -> 4f; LINE -> 0.8f
            WATERCOLOR -> 4f; TAPE -> 4f; QALAM -> 2f
        }
    val maxWidth: Float
        get() = when (this) {
            PEN -> 8f; PENCIL -> 6f; BRUSH -> 24f; MARKER -> 16f; HIGHLIGHTER -> 30f; ERASER -> 40f; LINE -> 12f
            WATERCOLOR -> 48f; TAPE -> 60f; QALAM -> 30f
        }
    val defaultWidth: Float
        get() = when (this) {
            PEN -> 2f; PENCIL -> 1.6f; BRUSH -> 6f; MARKER -> 5f; HIGHLIGHTER -> 14f; ERASER -> 12f; LINE -> 2f
            WATERCOLOR -> 16f; TAPE -> 16f; QALAM -> 5f
        }
    /** Tiga ukuran cepat yang ditampilkan di bilah alat. */
    val presets: List<Float>
        get() = when (this) {
            PEN -> listOf(1.2f, 2f, 3.5f)
            PENCIL -> listOf(1f, 1.6f, 3f)
            BRUSH -> listOf(4f, 8f, 14f)
            MARKER -> listOf(3f, 5f, 9f)
            HIGHLIGHTER -> listOf(9f, 14f, 22f)
            ERASER -> listOf(6f, 12f, 24f)
            LINE -> listOf(1.2f, 2f, 4f)
            WATERCOLOR -> listOf(8f, 16f, 30f)
            TAPE -> listOf(10f, 16f, 28f)
            QALAM -> listOf(3.5f, 5f, 8f)
        }

    /** Alat yang digambar di bawah teks dan tulisan (seperti cat di atas kertas). */
    val underlay: Boolean get() = this == HIGHLIGHTER || this == WATERCOLOR

    /** Goresan alat ini adalah tulisan yang bisa dibaca jadi teks. */
    val isWriting: Boolean get() = this == PEN || this == PENCIL || this == BRUSH || this == MARKER

    companion object {
        /** Alat yang tampil di baki, berurutan. */
        val tray = listOf(PEN, PENCIL, BRUSH, QALAM, WATERCOLOR, MARKER, HIGHLIGHTER, ERASER)
    }
}

/**
 * Cara penghapus bekerja. BRUSH: kuas lembut yang memudarkan apa pun di bawahnya sedikit demi sedikit
 * (tepi bergradasi, makin ditekan makin kuat); PARTIAL: memotong bagian goresan yang tersapu;
 * WHOLE: menghapus goresan utuh.
 */
enum class EraserMode(val label: String) { BRUSH("Kuas halus"), PARTIAL("Sebagian"), WHOLE("Goresan utuh") }

/** Cara kanvas menanggapi sentuhan pena. */
/** Cara menyentuh halaman: menulis (pensil), bentuk, memilih, mengetik (mode teks), atau menempel selotip. */
enum class InputMode { INK, SHAPE, SELECT, TYPE, TAPE, PDF }

/**
 * Bentuk selotip hafalan. Urutannya tersimpan di berkas, jadi bentuk baru selalu ditambahkan di akhir.
 * STRAIGHT: pita lurus mengikuti arah tarikan; FREE: pita mengikuti jalur tarikan; lainnya: bidang penuh
 * seukuran kotak yang ditarik.
 */
enum class TapeShape(val label: String) {
    STRAIGHT("Lurus"), FREE("Bebas"), RECT("Kotak"), ROUNDED("Kotak tumpul"), ELLIPSE("Lingkaran"),
    TRIANGLE("Segitiga"), DIAMOND("Belah ketupat"), HEXAGON("Segi enam"), STAR("Bintang"), HEART("Hati"),
    ;

    /** Pita (ditarik seperti goresan): Lurus atau Bebas. Selain itu bidang berbentuk. */
    val isBand: Boolean get() = this == STRAIGHT || this == FREE

    companion object {
        /** Tipe pita. */
        val BANDS: List<TapeShape> = entries.filter { it.isBand }
        /** Bentuk bidang (kotak, lingkaran, bintang, ...). */
        val AREAS: List<TapeShape> = entries.filter { !it.isBand }
    }
}

/** Warna selotip: lembut tapi cukup pekat untuk menutupi tulisan. */
val TapeColors: List<Int> = listOf(
    0xFFE9746C.toInt(), // merah salem
    0xFFF2B33D.toInt(), // kuning mustard
    0xFF6CC08B.toInt(), // hijau mint
    0xFF5CA8E0.toInt(), // biru langit
    0xFFA58BDB.toInt(), // ungu lavender
    0xFFF19AC0.toInt(), // merah muda
    0xFF8A8F98.toInt(), // abu-abu
    0xFF2E3138.toInt(), // hitam
)

enum class ShapeKind(val label: String) {
    LINE("Garis"), ARROW("Panah"), RECT("Kotak"), ELLIPSE("Lingkaran"), TRIANGLE("Segitiga")
}

val StickyColors: List<Int> = listOf(
    0xFFFFE98A.toInt(), 0xFFFFC7D9.toInt(), 0xFFCBEDB8.toInt(),
    0xFFC2E4FF.toInt(), 0xFFFFD4A6.toInt(), 0xFFE3D3FF.toInt(),
)

/**
 * Benda yang ditempel di halaman. Posisi dan ukuran dalam poin, untuk kotak sebelum diputar.
 * [rotation] dalam derajat searah jarum jam, berporos di tengah kotak.
 * Benda tidak diubah setelah dibuat; menggeser, memutar, atau mengubah ukuran membuat salinan baru.
 */
sealed class PageItem(val x: Float, val y: Float, val w: Float, val h: Float, val rotation: Float) {
    /** Nomor tetap benda: tetap sama walau benda digeser, diputar, atau diubah. Tulisan yang menempel memakainya. */
    var id: Long = newId()
    /** Kelompok benda; 0 bila tidak dikelompokkan. */
    var group: Long = 0L
    /** Urutan lapisan di halaman (bersama teks dan coretan): makin besar makin di depan. */
    var z: Long = 0L

    /**
     * Bekas kuas penghapus lembut yang menempel pada benda ini, dalam koordinat isi benda (0..1; untuk gambar:
     * seluruh gambar aslinya). Ikut tergeser, terputar, terbalik, dan terpotong bersama bendanya.
     */
    var masks: List<Stroke>? = null
    /** Bekas penghapus dalam koordinat halaman (dihitung sekali, tidak disimpan). */
    @Volatile var pageMasks: List<Stroke>? = null

    /** Salinan dengan satu bekas penghapus lembut tambahan ([m] dalam koordinat isi benda). */
    fun withMask(m: Stroke): PageItem = placed(x, y, w, h).also { it.masks = masks.orEmpty() + m }

    protected abstract fun copyAt(x: Float, y: Float, w: Float, h: Float, rotation: Float): PageItem

    /** Salinan di tempat atau ukuran lain, dengan nomor dan kelompok yang sama. */
    fun placed(x: Float, y: Float, w: Float, h: Float, rotation: Float = this.rotation): PageItem =
        copyAt(x, y, w, h, rotation).keepFrom(this)

    /** Salinan membawa nomor, kelompok, dan lapisan benda asalnya. */
    protected fun <T : PageItem> T.keepFrom(src: PageItem): T {
        id = src.id
        group = src.group
        z = src.z
        masks = src.masks
        return this
    }

    /** Titik ini berada di dalam benda (memperhitungkan putaran), dengan kelonggaran [pad]. */
    fun containsPoint(px: Float, py: Float, pad: Float = 0f): Boolean {
        var dx = px - cx
        var dy = py - cy
        if (rotation != 0f) {
            val r = Math.toRadians(rotation.toDouble())
            val c = cos(r).toFloat()
            val s = sin(r).toFloat()
            val lx = dx * c + dy * s
            val ly = -dx * s + dy * c
            dx = lx
            dy = ly
        }
        return abs(dx) <= w / 2f + pad && abs(dy) <= h / 2f + pad
    }

    abstract val kindLabel: String
    /** Tulisan tangan di atas benda ini ikut pindah dan terhapus bersamanya. */
    open val carriesInk: Boolean get() = true
    /** Gambar menjaga perbandingan sisi saat diubah ukurannya. */
    open val keepAspect: Boolean get() = false
    /** Benda ini bisa diputar. */
    open val canRotate: Boolean get() = false
    val cx: Float get() = x + w / 2f
    val cy: Float get() = y + h / 2f

    companion object {
        private val counter = java.util.concurrent.atomic.AtomicLong(System.currentTimeMillis() * 1000L)

        /** Nomor baru yang belum pernah dipakai. */
        fun newId(): Long = counter.incrementAndGet()

        /** Sudut dalam rentang -180..180 derajat. */
        fun normalizeDeg(d: Float): Float {
            var r = d % 360f
            if (r > 180f) r -= 360f
            if (r <= -180f) r += 360f
            return if (abs(r) < 0.01f) 0f else r
        }
    }
}

/**
 * Bayangan gambar. [distance] seberapa jauh gambar seolah terangkat dari kertas (poin), [blur] kelembutan
 * tepi bayangan (poin), [opacity] kepekatan 0..1. Cahaya datang dari kiri atas.
 */
data class Shadow(val distance: Float, val blur: Float, val opacity: Float) {
    companion object {
        val RAISED = Shadow(3f, 4f, 0.32f)
        val FLOATING = Shadow(14f, 18f, 0.28f)
        val STRONG = Shadow(6f, 2f, 0.5f)
    }
}

/** Rekaman suara di halaman: diketuk untuk diputar. [durationMs] lama rekaman. */
class AudioItem(x: Float, y: Float, w: Float, h: Float, val file: String, val durationMs: Long) : PageItem(x, y, w, h, 0f) {
    override fun copyAt(x: Float, y: Float, w: Float, h: Float, rotation: Float) = AudioItem(x, y, w, h, file, durationMs)
    override val kindLabel get() = "Rekaman"
    override val carriesInk get() = false
    override val keepAspect get() = true
}

/** Bagian gambar yang ditampilkan, dalam pecahan 0..1 dari lebar dan tinggi gambar asli. */
data class Crop(val l: Float, val t: Float, val r: Float, val b: Float) {
    val isFull: Boolean get() = l <= 0.0005f && t <= 0.0005f && r >= 0.9995f && b >= 0.9995f

    /** Cerminkan kotak potong (dipakai bila gambar ditampilkan terbalik). */
    fun mirrored(horizontal: Boolean, vertical: Boolean) = Crop(
        if (horizontal) 1f - r else l,
        if (vertical) 1f - b else t,
        if (horizontal) 1f - l else r,
        if (vertical) 1f - t else b,
    )

    companion object {
        val FULL = Crop(0f, 0f, 1f, 1f)
    }
}

class ImageItem(
    x: Float,
    y: Float,
    w: Float,
    h: Float,
    val file: String,
    rotation: Float = 0f,
    val flipH: Boolean = false,
    val flipV: Boolean = false,
    val crop: Crop = Crop.FULL,
    /** Bayangan di bawah gambar supaya tampak timbul atau melayang; null bila tanpa bayangan. */
    val shadow: Shadow? = null,
) : PageItem(x, y, w, h, rotation) {
    override fun copyAt(x: Float, y: Float, w: Float, h: Float, rotation: Float) =
        ImageItem(x, y, w, h, file, rotation, flipH, flipV, crop, shadow)

    fun withShadow(s: Shadow?) = ImageItem(x, y, w, h, file, rotation, flipH, flipV, crop, s).keepFrom(this)
    fun withFile(f: String) = ImageItem(x, y, w, h, f, rotation, flipH, flipV, crop, shadow).keepFrom(this)
    override val kindLabel get() = "Gambar"
    override val keepAspect get() = true
    override val canRotate get() = true

    /** Balik gambar seperti terlihat di layar: kiri-kanan atau atas-bawah. */
    fun flipped(horizontal: Boolean) = ImageItem(
        x, y, w, h, file, PageItem.normalizeDeg(0f - rotation),
        if (horizontal) !flipH else flipH,
        if (horizontal) flipV else !flipV,
        crop,
        shadow,
    ).keepFrom(this)

    /**
     * Potong ulang gambar. Skala gambar tetap sama dan bagian yang masih tampil tidak bergeser
     * di halaman, jadi memotong terasa seperti menggunting tepi foto.
     */
    fun cropped(next: Crop): ImageItem {
        val cw = crop.r - crop.l
        val ch = crop.b - crop.t
        if (cw <= 0f || ch <= 0f) return this
        val kx = w / cw
        val ky = h / ch
        val nw = (next.r - next.l) * kx
        val nh = (next.b - next.t) * ky
        var lx = ((next.l + next.r) / 2f - (crop.l + crop.r) / 2f) * kx
        var ly = ((next.t + next.b) / 2f - (crop.t + crop.b) / 2f) * ky
        if (flipH) lx = -lx
        if (flipV) ly = -ly
        val rad = Math.toRadians(rotation.toDouble())
        val c = cos(rad).toFloat()
        val s = sin(rad).toFloat()
        val ncx = cx + lx * c - ly * s
        val ncy = cy + lx * s + ly * c
        return ImageItem(ncx - nw / 2f, ncy - nh / 2f, nw, nh, file, rotation, flipH, flipV, next, shadow).keepFrom(this)
    }
}

/** Stiker catatan: bisa ditulisi pena, dan bisa diberi teks ketik dengan font pilihan. */
class StickyItem(
    x: Float,
    y: Float,
    w: Float,
    h: Float,
    val color: Int,
    val text: String = "",
    val font: String = FontRegistry.DEFAULT,
    val textSize: Float = 14f,
    rotation: Float = 0f,
    val align: Align = Align.LEFT,
) : PageItem(x, y, w, h, rotation) {
    override fun copyAt(x: Float, y: Float, w: Float, h: Float, rotation: Float) =
        StickyItem(x, y, w, h, color, text, font, textSize, rotation, align)
    fun withText(text: String, font: String, textSize: Float, align: Align = this.align) =
        StickyItem(x, y, w, h, color, text, font, textSize, rotation, align).keepFrom(this)
    override val kindLabel get() = "Stiker"
    override val canRotate get() = true
}

class LinkItem(x: Float, y: Float, w: Float, h: Float, val url: String, val label: String) : PageItem(x, y, w, h, 0f) {
    override fun copyAt(x: Float, y: Float, w: Float, h: Float, rotation: Float) = LinkItem(x, y, w, h, url, label)
    override val kindLabel get() = "Link"
    override val carriesInk get() = false
}

/**
 * Tabel untuk ditulisi atau diketik. Lebar tiap kolom dan tinggi tiap baris disimpan sebagai bagian dari
 * ukuran tabel (jumlahnya 1), jadi tetap sebanding saat tabel diperbesar atau diperkecil. Teks ketik tiap sel
 * menjadi bagian dari tabel, jadi ikut tergeser dan mengalir ulang mengikuti lebar kolom.
 */
class TableItem(
    x: Float, y: Float, w: Float, h: Float,
    val rows: Int, val cols: Int, val color: Int,
    colSizes: FloatArray? = null,
    rowSizes: FloatArray? = null,
    /** Baris pertama diberi warna latar sebagai judul. */
    val header: Boolean = true,
    /** Isi tiap sel, baris demi baris (rows × cols); kosong bila belum diisi. */
    cells: List<String>? = null,
    /** Ukuran, huruf, dan perataan teks di semua sel. */
    val textSize: Float = CELL_TEXT_SIZE,
    val font: String = FontRegistry.DEFAULT,
    val align: Align = Align.LEFT,
) : PageItem(x, y, w, h, 0f) {
    val colFrac: FloatArray = normalized(colSizes, cols)
    val rowFrac: FloatArray = normalized(rowSizes, rows)
    val cells: List<String> = when {
        cells == null || cells.isEmpty() -> List(rows * cols) { "" }
        cells.size == rows * cols -> cells
        else -> List(rows * cols) { cells.getOrNull(it) ?: "" }
    }

    /** Ada sel yang sudah berisi teks. */
    val hasText: Boolean get() = cells.any { it.isNotEmpty() }

    fun cell(r: Int, c: Int): String = cells.getOrNull(r * cols + c) ?: ""

    override fun copyAt(x: Float, y: Float, w: Float, h: Float, rotation: Float) =
        TableItem(x, y, w, h, rows, cols, color, colFrac, rowFrac, header, cells, textSize, font, align)

    /** Susunan baru: jumlah baris/kolom, ukurannya, dan tinggi tabel. Posisi dan lebar tetap; isi sel tetap di tempatnya. */
    fun withLayout(rows: Int, cols: Int, colSizes: FloatArray, rowSizes: FloatArray, header: Boolean, height: Float): TableItem {
        val moved = List(rows * cols) { k ->
            val r = k / cols
            val c = k % cols
            if (r < this.rows && c < this.cols) cell(r, c) else ""
        }
        return TableItem(x, y, w, height, rows, cols, color, colSizes, rowSizes, header, moved, textSize, font, align).keepFrom(this)
    }

    /** Isi satu sel diganti. */
    fun withCell(r: Int, c: Int, text: String): TableItem {
        val next = cells.toMutableList()
        val k = r * cols + c
        if (k in next.indices) next[k] = text
        return TableItem(x, y, w, h, rows, cols, color, colFrac, rowFrac, header, next, textSize, font, align).keepFrom(this)
    }

    /** Lebar tiap kolom (poin, atau sebanding) diganti; lebar tabel tetap. */
    fun withColumnWidths(widths: FloatArray): TableItem =
        TableItem(x, y, w, h, rows, cols, color, widths, rowFrac, header, cells, textSize, font, align).keepFrom(this)

    /** Tinggi tiap baris (poin) diganti; tinggi tabel ikut. */
    fun withRowHeights(heights: FloatArray): TableItem =
        TableItem(x, y, w, heights.sum(), rows, cols, color, colFrac, heights, header, cells, textSize, font, align).keepFrom(this)

    fun withTextStyle(font: String, size: Float, align: Align): TableItem =
        TableItem(x, y, w, h, rows, cols, color, colFrac, rowFrac, header, cells, size, font, align).keepFrom(this)

    /** Satu baris kosong ditambahkan di bawah baris [after], setinggi baris itu. */
    fun withRowAdded(after: Int): TableItem {
        val at = (after + 1).coerceIn(0, rows)
        val heights = ArrayList<Float>()
        for (k in 0 until rows) heights.add(rowEdge(k + 1) - rowEdge(k))
        val newH = heights.getOrNull(after.coerceIn(0, rows - 1)) ?: (h / rows)
        heights.add(at, newH)
        val next = ArrayList<String>()
        for (r in 0 until rows + 1) for (c in 0 until cols) {
            next.add(
                when {
                    r < at -> cell(r, c)
                    r == at -> ""
                    else -> cell(r - 1, c)
                }
            )
        }
        return TableItem(x, y, w, h + newH, rows + 1, cols, color, colFrac, heights.toFloatArray(), header, next, textSize, font, align)
            .keepFrom(this)
    }

    /** Jarak tepi kiri kolom [i] (0..cols) dari tepi kiri tabel. */
    fun colEdge(i: Int): Float {
        var s = 0f
        for (k in 0 until i.coerceAtMost(cols)) s += colFrac[k]
        return s * w
    }

    /** Jarak tepi atas baris [i] (0..rows) dari tepi atas tabel. */
    fun rowEdge(i: Int): Float {
        var s = 0f
        for (k in 0 until i.coerceAtMost(rows)) s += rowFrac[k]
        return s * h
    }

    override val kindLabel get() = "Tabel"

    companion object {
        /** Ukuran teks sel bawaan (poin), pas untuk baris setinggi 26 poin. */
        const val CELL_TEXT_SIZE = 12f

        fun normalized(f: FloatArray?, n: Int): FloatArray {
            val count = n.coerceAtLeast(1)
            if (f == null || f.size != count || f.any { !(it > 0f) }) return FloatArray(count) { 1f / count }
            val sum = f.sum()
            return FloatArray(count) { f[it] / sum }
        }
    }
}

/**
 * Kalender satu bulan yang bisa ditulisi di tiap tanggalnya.
 * [month] 1..12, [mark] tanggal yang ditandai (0 bila tidak ada).
 */
class CalendarItem(
    x: Float,
    y: Float,
    w: Float,
    h: Float,
    val year: Int,
    val month: Int,
    val color: Int,
    val mark: Int = 0,
) : PageItem(x, y, w, h, 0f) {
    override fun copyAt(x: Float, y: Float, w: Float, h: Float, rotation: Float) = CalendarItem(x, y, w, h, year, month, color, mark)
    override val kindLabel get() = "Kalender"
}

/**
 * Selotip hafalan: menutupi bagian catatan supaya bisa dipakai menghafal. Ketuk untuk membuka (jadi tembus
 * pandang, isi di bawahnya terlihat) dan ketuk lagi untuk menutup. Selalu tampil di atas isi halaman.
 *
 * STRAIGHT: pita lurus, [w] = panjang, [h] = tebal, diputar mengikuti arah tarikan. FREE: pita setebal [thick]
 * mengikuti [path] (pasangan x, y dalam 0..1 terhadap kotak benda). Bentuk lain: bidang penuh seukuran kotak.
 */
class TapeItem(
    x: Float,
    y: Float,
    w: Float,
    h: Float,
    val shape: TapeShape,
    val color: Int,
    val thick: Float = 0f,
    val path: FloatArray? = null,
    rotation: Float = 0f,
    covered: Boolean = true,
) : PageItem(x, y, w, h, rotation) {
    /** Tertutup (menyembunyikan isi di bawahnya) atau terbuka. Diubah langsung oleh ketukan, tanpa salinan baru. */
    @Volatile var covered: Boolean = covered

    override fun copyAt(x: Float, y: Float, w: Float, h: Float, rotation: Float) =
        TapeItem(x, y, w, h, shape, color, thick, path, rotation, covered)
    fun withColor(c: Int) = TapeItem(x, y, w, h, shape, c, thick, path, rotation, covered).keepFrom(this)
    override val kindLabel get() = "Selotip"
    override val carriesInk get() = false
    override val canRotate get() = true
}

/**
 * Penutup: bidang berwarna polos (biasanya warna kertas PDF) untuk menutupi tulisan asli PDF yang diubah. Teks
 * penggantinya kotak teks biasa yang dikelompokkan dengan penutup ini.
 */
class CoverItem(x: Float, y: Float, w: Float, h: Float, val color: Int) : PageItem(x, y, w, h, 0f) {
    override fun copyAt(x: Float, y: Float, w: Float, h: Float, rotation: Float) = CoverItem(x, y, w, h, color)
    fun withColor(c: Int) = CoverItem(x, y, w, h, c).keepFrom(this)
    override val kindLabel get() = "Penutup"
    override val carriesInk get() = false
}

/** Satu ide di mind map. [x], [y] pusatnya dalam satuan peta; [parent] -1 untuk ide utama. */
class MindNode(val id: Int, val parent: Int, val x: Float, val y: Float, val text: String, val color: Int) {
    fun moved(x: Float, y: Float) = MindNode(id, parent, x, y, text, color)
    fun withText(text: String) = MindNode(id, parent, x, y, text, color)
    fun withColor(color: Int) = MindNode(id, parent, x, y, text, color)
}

/**
 * Mind map: ide utama dan cabang-cabangnya. Simpul disimpan dalam satuan peta; kotak isinya
 * ([mapL], [mapT], [mapW], [mapH]) ditampilkan memenuhi kotak benda di halaman, jadi tetap tajam
 * berapa pun ukurannya. [tidy] berarti susunannya masih diatur otomatis (belum digeser tangan).
 */
class MindMapItem(
    x: Float,
    y: Float,
    w: Float,
    h: Float,
    val nodes: List<MindNode>,
    val mapL: Float,
    val mapT: Float,
    val mapW: Float,
    val mapH: Float,
    val tidy: Boolean = true,
) : PageItem(x, y, w, h, 0f) {
    override fun copyAt(x: Float, y: Float, w: Float, h: Float, rotation: Float) =
        MindMapItem(x, y, w, h, nodes, mapL, mapT, mapW, mapH, tidy)
    override val kindLabel get() = "Mind map"
    override val keepAspect get() = true

    /** Isi teks dari atas ke bawah sebagai kerangka berjenjang (untuk pencarian dan ekspor). */
    fun outline(): List<String> {
        val byParent = nodes.groupBy { it.parent }
        val out = ArrayList<String>()
        fun walk(n: MindNode, depth: Int) {
            val t = n.text.trim()
            if (t.isNotEmpty()) out.add("    ".repeat(depth) + (if (depth == 0) "" else "• ") + t)
            for (c in byParent[n.id].orEmpty().sortedBy { it.y }) walk(c, depth + 1)
        }
        for (r in byParent[-1].orEmpty()) walk(r, 0)
        return out
    }
}

/** Perataan teks. */
enum class Align(val label: String) {
    LEFT("Rata kiri"), CENTER("Rata tengah"), RIGHT("Rata kanan"), JUSTIFY("Rata kanan-kiri")
}

/** Nama bulan dan hari dalam bahasa Indonesia, tidak bergantung pada bahasa perangkat. */
object IndoDate {
    val months = listOf(
        "Januari", "Februari", "Maret", "April", "Mei", "Juni",
        "Juli", "Agustus", "September", "Oktober", "November", "Desember",
    )
    val monthsShort = listOf("Jan", "Feb", "Mar", "Apr", "Mei", "Jun", "Jul", "Agu", "Sep", "Okt", "Nov", "Des")
    /** Senin sampai Minggu (urutan ISO, Senin = 1). */
    val days = listOf("Senin", "Selasa", "Rabu", "Kamis", "Jumat", "Sabtu", "Minggu")
    val daysShort = listOf("Sen", "Sel", "Rab", "Kam", "Jum", "Sab", "Min")
}

/** Warna cat air: lembut dan sedikit kusam seperti pigmen sungguhan. */
val WatercolorColors: List<Int> = listOf(
    0xFF2B5BA8.toInt(), // biru ultramarin
    0xFFD0435B.toInt(), // merah mawar
    0xFF3E8E5A.toInt(), // hijau daun
    0xFFE0A526.toInt(), // kuning oker
    0xFF9A5B34.toInt(), // coklat sienna
    0xFF7A4FA0.toInt(), // ungu
    0xFF2A9D9A.toInt(), // toska
    0xFF4A505C.toInt(), // abu tinta
)

enum class PaperTemplate(val label: String) {
    BLANK("Polos"), LINED("Bergaris"), GRID("Kotak"), DOTS("Titik"),
    TODO("Daftar tugas"), DAILY("Agenda harian"), WEEKLY("Mingguan"), MEETING("Rapat"), CORNELL("Cornell"),
}

/** Ukuran kertas dalam milimeter, termasuk F4 yang umum di Indonesia. */
enum class PaperSize(val label: String, val wMm: Float, val hMm: Float) {
    A4("A4", 210f, 297f),
    A5("A5", 148f, 210f),
    B5("B5", 176f, 250f),
    F4("F4", 215f, 330f),
    LETTER("Letter", 215.9f, 279.4f),
    SQUARE("Persegi", 210f, 210f);

    fun widthPt(landscape: Boolean) = mmToPt(if (landscape) hMm else wMm)
    fun heightPt(landscape: Boolean) = mmToPt(if (landscape) wMm else hMm)

    companion object {
        fun mmToPt(mm: Float) = mm * 72f / 25.4f

        fun ptToMm(pt: Float) = pt * 25.4f / 72f

        /** Tebak ukuran kertas dari dimensi halaman. Hasil null berarti ukuran custom. */
        fun match(w: Float, h: Float): Pair<PaperSize?, Boolean> {
            for (s in PaperSize.entries) {
                if (kotlin.math.abs(s.widthPt(false) - w) < 1f && kotlin.math.abs(s.heightPt(false) - h) < 1f) return s to false
                if (kotlin.math.abs(s.widthPt(true) - w) < 1f && kotlin.math.abs(s.heightPt(true) - h) < 1f) return s to true
            }
            return null to (w > h)
        }

        /** Batas ukuran custom, dalam milimeter. */
        const val CUSTOM_MIN_MM = 50f
        const val CUSTOM_MAX_MM = 1000f
    }
}

val PaperColors: List<Pair<String, Int>> = listOf(
    "Putih" to 0xFFFFFFFF.toInt(),
    "Krem" to 0xFFFBF5E6.toInt(),
    "Abu" to 0xFFEEF0F3.toInt(),
    "Hijau" to 0xFFE8F2EA.toInt(),
    "Gelap" to 0xFF23262C.toInt(),
)

val BookColors: List<Int> = listOf(
    0xFF6E3A5E.toInt(), // plum
    0xFF2F43B5.toInt(), // biru tinta
    0xFF2E6B4F.toInt(), // hijau hutan
    0xFFB4532A.toInt(), // bata
    0xFFC9962B.toInt(), // kunyit
    0xFF46505E.toInt(), // batu
    0xFFD9798F.toInt(), // mawar
    0xFF1F7A80.toInt(), // toska
)

val InkColors: List<Int> = listOf(
    0xFF1F2430.toInt(), 0xFF2F43B5.toInt(), 0xFFC0392B.toInt(), 0xFF1E7B52.toInt(),
    0xFF6E3A5E.toInt(), 0xFFE07B00.toInt(), 0xFF6B7280.toInt(), 0xFFFFFFFF.toInt(),
)

val HighlighterColors: List<Int> = listOf(
    0xFFFFE14D.toInt(), 0xFF8BE38B.toInt(), 0xFFFF9FC6.toInt(),
    0xFF8ECFFF.toInt(), 0xFFFFB86B.toInt(), 0xFFC9A7FF.toInt(),
)

data class Book(
    val id: String,
    val title: String,
    val color: Int,
    val created: Long,
    val updated: Long,
    /** Urutan di rak (kecil di depan). Buku lama memakai waktu dibuat. */
    val order: Long = created,
    /** Disematkan: selalu di depan rak. */
    val pinned: Boolean = false,
    /** Catatan di buku ini diurutkan sendiri (diseret), bukan menurut terakhir diubah. */
    val manualNotes: Boolean = false,
    /** Gambar sampul (nama berkas di folder sampul), atau kosong untuk sampul warna. */
    val cover: String = "",
)

data class Note(
    val id: String,
    val bookId: String,
    val title: String,
    val created: Long,
    val updated: Long,
    val pageCount: Int,
    /** Urutan di bukunya bila bukunya diurutkan sendiri (kecil di atas). */
    val order: Long = 0L,
    /** Disematkan: selalu paling atas di bukunya. */
    val pinned: Boolean = false,
    /** Bila tidak kosong: entri ini adalah gambar dari ruang gambar (id karyanya), bukan catatan berhalaman. */
    val art: String = "",
    /** Nomor halaman yang dicetak di tiap halaman (lihat [PageNumberSpec]); kosong = tanpa nomor. */
    val pageNumbers: String = "",
)

/**
 * Pengaturan nomor halaman sebuah catatan. [position]: 0 bawah tengah, 1 bawah kanan, 2 bawah kiri, 3 atas kanan,
 * 4 atas tengah. [format]: 0 "1", 1 "Hal. 1", 2 "1 / 12", 3 "- 1 -". [start]: nomor halaman pertama.
 * [skipFirst]: halaman pertama (sampul) tidak diberi nomor.
 */
data class PageNumberSpec(val position: Int = 0, val format: Int = 0, val start: Int = 1, val skipFirst: Boolean = false, val size: Float = 10f) {
    fun encode(): String = "$position,$format,$start,${if (skipFirst) 1 else 0},$size"

    /** Teks nomor untuk halaman ke-[index] (mulai 0) dari [total] halaman, atau null bila tanpa nomor. */
    fun label(index: Int, total: Int): String? {
        if (skipFirst && index == 0) return null
        val n = start + index - (if (skipFirst) 1 else 0)
        val last = start + total - 1 - (if (skipFirst) 1 else 0)
        return when (format) {
            1 -> "Hal. $n"
            2 -> "$n / $last"
            3 -> "- $n -"
            else -> "$n"
        }
    }

    companion object {
        val POSITIONS = listOf("Bawah tengah", "Bawah kanan", "Bawah kiri", "Atas kanan", "Atas tengah")
        val FORMATS = listOf("1", "Hal. 1", "1 / 12", "- 1 -")

        fun decode(s: String): PageNumberSpec? {
            if (s.isBlank()) return null
            val p = s.split(',')
            return runCatching {
                PageNumberSpec(
                    p[0].toInt().coerceIn(0, POSITIONS.lastIndex), p[1].toInt().coerceIn(0, FORMATS.lastIndex),
                    p[2].toInt(), p.getOrNull(3) == "1", p.getOrNull(4)?.toFloatOrNull()?.coerceIn(6f, 28f) ?: 10f,
                )
            }.getOrNull()
        }
    }
}

/** Urutan rak: buku yang disematkan dulu, lalu menurut urutan yang diatur. */
fun shelfOrder(books: List<Book>): List<Book> =
    books.sortedWith(compareByDescending<Book> { it.pinned }.thenBy { it.order }.thenBy { it.created })

/**
 * Urutan catatan di satu buku: yang disematkan dulu, lalu menurut urutan yang diseret ([manual]) atau
 * yang terakhir diubah.
 */
fun notesOrder(notes: List<Note>, manual: Boolean): List<Note> =
    if (manual) notes.sortedWith(compareByDescending<Note> { it.pinned }.thenBy { it.order }.thenByDescending { it.updated })
    else notes.sortedWith(compareByDescending<Note> { it.pinned }.thenByDescending { it.updated })

/** Satu lembar kertas. Ukuran dalam poin (1/72 inci). */
data class PageMeta(
    val id: String,
    val width: Float,
    val height: Float,
    val template: PaperTemplate,
    val paperColor: Int,
    /** Latar: "" polos, "tex:…" tekstur kertas, "img:…" gambar dari perangkat (lihat [PageBackground]). */
    val background: String = PageBackground.PLAIN,
)

/** Lapisan benda, teks, atau coretan (untuk mengurutkan gambarnya). */
fun layerZ(o: Any): Long = when (o) {
    is PageItem -> o.z
    is TextItem -> o.z
    is Stroke -> o.z
    else -> 0L
}

/** Satu goresan. Koordinat dalam satuan halaman, tekanan 0..1. Tidak diubah setelah dibuat. */
class Stroke(
    val tool: Tool,
    val color: Int,
    val width: Float,
    val xs: FloatArray,
    val ys: FloatArray,
    val ps: FloatArray,
) {
    val size: Int get() = xs.size
    val bounds = RectF()

    /** Waktu tiap titik (ms). Hanya ada untuk goresan yang baru ditulis; membantu pengenal tulisan. */
    var times: LongArray? = null

    /** Kelompok; 0 bila tidak dikelompokkan. */
    var group: Long = 0L
    /** Urutan lapisan di halaman: makin besar makin di depan. */
    var z: Long = 0L

    /** Bekas kuas penghapus lembut yang menempel pada goresan ini (koordinat halaman). */
    var masks: List<Stroke>? = null

    /** Salinan dengan titik yang sama, lengkap dengan kelompok, lapisan, dan bekas penghapusnya. */
    fun copyAll(): Stroke = Stroke(tool, color, width, xs, ys, ps).also {
        it.group = group
        it.z = z
        it.times = times
        it.masks = masks
    }

    /** Salinan dengan satu bekas penghapus lembut tambahan. */
    fun withMask(m: Stroke): Stroke = copyAll().also { it.masks = masks.orEmpty() + m }

    init {
        if (xs.isNotEmpty()) {
            var l = xs[0]; var t = ys[0]; var r = xs[0]; var b = ys[0]
            for (i in 1 until xs.size) {
                val x = xs[i]; val y = ys[i]
                if (x < l) l = x; if (x > r) r = x
                if (y < t) t = y; if (y > b) b = y
            }
            val pad = width * 1.2f + 1f
            bounds.set(l - pad, t - pad, r + pad, b + pad)
        }
    }
}

/** Teks ketik di halaman, misalnya hasil ubah tulisan tangan. Posisi dan ukuran dalam poin. */
class TextItem(
    val x: Float,
    val y: Float,
    val width: Float,
    val text: String,
    val size: Float,
    val color: Int,
    val font: String = FontRegistry.DEFAULT,
    val align: Align = Align.LEFT,
    /** Huruf tebal, miring, bergaris bawah, atau dicoret. */
    val runs: TextRuns = TextRuns.EMPTY,
    /**
     * Jarak antarbaris tetap (poin), supaya tiap baris duduk tepat di garis kertas bergaris; 0 = jarak biasa.
     */
    val linePitch: Float = 0f,
) {
    /** Kelompok; 0 bila tidak dikelompokkan. */
    var group: Long = 0L
    /** Urutan lapisan di halaman: makin besar makin di depan. */
    var z: Long = 0L

    /**
     * Bekas kuas penghapus lembut yang menempel pada teks ini, dalam koordinat teks (titik kiri atas = 0,
     * satuan = ukuran huruf), jadi ikut tergeser dan membesar bersama teksnya.
     */
    var masks: List<Stroke>? = null
    /** Bekas penghapus dalam koordinat halaman (dihitung sekali, tidak disimpan). */
    @Volatile var pageMasks: List<Stroke>? = null

    private fun keep(t: TextItem): TextItem {
        t.group = group
        t.z = z
        t.masks = masks
        return t
    }

    /** Salinan dengan satu bekas penghapus lembut tambahan ([m] dalam koordinat teks). */
    fun withMask(m: Stroke) = keep(TextItem(x, y, width, text, size, color, font, align, runs, linePitch)).also { it.masks = masks.orEmpty() + m }

    /** Isi lain; gaya hurufnya dipotong bila teksnya lebih pendek (atau diberikan sendiri lewat [runs]). */
    fun withText(text: String, runs: TextRuns = this.runs.clamp(text.length)) =
        keep(TextItem(x, y, width, text, size, color, font, align, runs, linePitch))
    fun withRuns(r: TextRuns) = keep(TextItem(x, y, width, text, size, color, font, align, r, linePitch))
    fun withAlign(a: Align) = keep(TextItem(x, y, width, text, size, color, font, a, runs, linePitch))
    fun withColor(c: Int) = keep(TextItem(x, y, width, text, size, c, font, align, runs, linePitch))
    /** Posisi lain (misalnya diselaraskan ke garis kertas), isi dan gaya sama. */
    fun at(x: Float, y: Float) = keep(TextItem(x, y, width, text, size, color, font, align, runs, linePitch))
    /** Salinan dengan posisi, lebar, dan ukuran huruf lain. */
    fun moved(x: Float, y: Float, width: Float, size: Float) =
        keep(TextItem(x, y, width, text, size, color, font, align, runs, if (this.size > 0f) linePitch * size / this.size else linePitch))
    /** Kotak teks dengan lebar lain; ukuran huruf tetap, barisnya mengalir ulang. */
    fun withBox(x: Float, width: Float) = keep(TextItem(x, y, width, text, size, color, font, align, runs, linePitch))
    /** Tata letak teks yang sudah dihitung, disimpan supaya tidak dihitung ulang tiap bingkai. */
    @Volatile var layout: StaticLayout? = null
}

/** Isi satu halaman: goresan, teks, dan benda tempelan. Daftar ini diubah langsung oleh kanvas. */
class PageContent(
    val strokes: MutableList<Stroke> = mutableListOf(),
    val texts: MutableList<TextItem> = mutableListOf(),
    val items: MutableList<PageItem> = mutableListOf(),
) {
    fun copy() = PageContent(ArrayList(strokes), ArrayList(texts), ArrayList(items))

    /** Salinan lepas (untuk halaman duplikat): kelompok di salinan bisa diubah tanpa memengaruhi aslinya. */
    fun deepCopy(): PageContent {
        val ss = strokes.mapTo(ArrayList()) { s -> s.copyAll() }
        val ts = texts.mapTo(ArrayList()) { it.moved(it.x, it.y, it.width, it.size) }
        val its = items.mapTo(ArrayList()) { it.placed(it.x, it.y, it.w, it.h) }
        return PageContent(ss, ts, its)
    }
}

// ---------------- tema aplikasi ----------------

enum class Accent(val label: String, val light: Long, val lightContainer: Long, val dark: Long, val darkContainer: Long) {
    HANA("Anggur", 0xFF6E3A5E, 0xFFF4DDEA, 0xFFE8B6D3, 0xFF5A2B4C),
    TINTA("Tinta", 0xFF2F43B5, 0xFFDDE1FF, 0xFFB9C3FF, 0xFF283593),
    DAUN("Daun", 0xFF2E6B4F, 0xFFCDEBD9, 0xFF9BD5B4, 0xFF1E4D38),
    SENJA("Senja", 0xFFB4532A, 0xFFFFDBCC, 0xFFFFB693, 0xFF7A3416),
    KUNYIT("Kunyit", 0xFF7F5A00, 0xFFFFE08F, 0xFFF2C14E, 0xFF5E4300),
    BATU("Batu", 0xFF46505E, 0xFFDCE2EC, 0xFFBCC6D3, 0xFF333B47),
}

enum class ThemeMode(val label: String) { SYSTEM("Ikuti sistem"), LIGHT("Terang"), DARK("Gelap") }

data class ThemeSettings(
    val accent: Accent = Accent.HANA,
    val mode: ThemeMode = ThemeMode.SYSTEM,
    val serifTitles: Boolean = true,
)

/** Penampung titik selama goresan masih ditulis. */
class StrokeBuilder(val tool: Tool, val color: Int, val width: Float) {
    var xs = FloatArray(256); private set
    var ys = FloatArray(256); private set
    var ps = FloatArray(256); private set
    private var ts = LongArray(256)
    var size = 0; private set

    val lastX get() = xs[size - 1]
    val lastY get() = ys[size - 1]

    fun add(x: Float, y: Float, p: Float, t: Long = 0L) {
        if (size == xs.size) {
            val n = size * 2
            xs = xs.copyOf(n); ys = ys.copyOf(n); ps = ps.copyOf(n); ts = ts.copyOf(n)
        }
        xs[size] = x; ys[size] = y; ps[size] = p; ts[size] = t
        size++
    }

    fun build() = Stroke(tool, color, width, xs.copyOf(size), ys.copyOf(size), ps.copyOf(size)).also {
        if (size > 0 && ts[0] > 0L) it.times = ts.copyOf(size)
    }
}
