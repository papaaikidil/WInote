package id.hananote.ink

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import android.util.LruCache
import java.io.File

/**
 * Halaman PDF sebagai latar halaman catatan. Berkas PDF dibuka sekali dan gambarnya disimpan
 * sementara, karena merender halaman PDF cukup berat. Semua akses lewat satu kunci, sebab satu
 * PdfRenderer hanya boleh membuka satu halaman pada satu waktu.
 */
object PdfPages {
    private class Doc(val pfd: ParcelFileDescriptor, val renderer: PdfRenderer)

    private val docs = object : LinkedHashMap<String, Doc>(4, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Doc>?): Boolean {
            if (size <= 3) return false
            eldest?.value?.let { d -> runCatching { d.renderer.close() }; runCatching { d.pfd.close() } }
            return true
        }
    }

    private val bitmaps = object : LruCache<String, Bitmap>((Runtime.getRuntime().maxMemory() / 6).toInt()) {
        override fun sizeOf(key: String, value: Bitmap): Int = value.byteCount
    }

    private fun open(file: File): Doc {
        docs[file.path]?.let { return it }
        val pfd = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
        val d = try {
            Doc(pfd, PdfRenderer(pfd))
        } catch (e: Exception) {
            pfd.close()
            throw e
        }
        docs[file.path] = d
        return d
    }

    /** Ukuran tiap halaman dalam poin (1/72 inci). Melempar galat bila PDF tidak bisa dibuka (misalnya terkunci). */
    fun pageSizes(file: File): List<Pair<Float, Float>> = synchronized(this) {
        val d = open(file)
        (0 until d.renderer.pageCount).map { i ->
            d.renderer.openPage(i).use { p -> p.width.toFloat() to p.height.toFloat() }
        }
    }

    private val widths = intArrayOf(600, 1200, 2048)
    private val loading = java.util.Collections.synchronizedSet(HashSet<String>())
    private val worker = java.util.concurrent.Executors.newSingleThreadExecutor { r ->
        Thread(r, "hana-pdf").apply { isDaemon = true; priority = Thread.NORM_PRIORITY - 1 }
    }
    private val main = android.os.Handler(android.os.Looper.getMainLooper())

    /** Dipanggil di utas utama setiap halaman PDF selesai digambar di latar. */
    val onRendered = java.util.concurrent.CopyOnWriteArrayList<(File, Int) -> Unit>()

    /**
     * Gambar halaman [index] selebar [widthPx] piksel (dari simpanan bila ada), atau null bila gagal. Dari utas
     * utama, halaman yang belum ada digambar di latar; sementara dipakai ukuran lain yang sudah ada.
     */
    /** Lihat [ImageCache.blockingOnMain]. */
    @Volatile var blockingOnMain = false

    fun render(file: File, index: Int, widthPx: Int): Bitmap? {
        val key = "${file.path}#$index@$widthPx"
        bitmaps.get(key)?.let { return it }
        if (android.os.Looper.myLooper() == android.os.Looper.getMainLooper()) {
            for (w in widths.reversed()) if (w != widthPx) bitmaps.get("${file.path}#$index@$w")?.let { request(file, index, widthPx); return it }
            if (blockingOnMain) return renderNow(file, index, widthPx)
            request(file, index, widthPx)
            return null
        }
        return renderNow(file, index, widthPx)
    }

    /** Gambar halaman [index] yang sudah tersimpan, ukuran terbesar yang ada; null bila belum pernah digambar. */
    fun anyCached(file: File, index: Int): Bitmap? {
        for (w in widths.reversed()) bitmaps.get("${file.path}#$index@$w")?.let { return it }
        return null
    }

    /** Siapkan di latar lebih dulu, misalnya halaman sebelah. */
    fun prefetch(file: File, index: Int, widthPx: Int) {
        if (bitmaps.get("${file.path}#$index@$widthPx") == null) request(file, index, widthPx)
    }

    private fun request(file: File, index: Int, widthPx: Int) {
        val key = "${file.path}#$index@$widthPx"
        if (!loading.add(key)) return
        worker.execute {
            val ok = runCatching { renderNow(file, index, widthPx) }.getOrNull() != null
            loading.remove(key)
            if (ok) main.post { for (l in onRendered) runCatching { l(file, index) } }
        }
    }

    private fun renderNow(file: File, index: Int, widthPx: Int): Bitmap? {
        val key = "${file.path}#$index@$widthPx"
        bitmaps.get(key)?.let { return it }
        return synchronized(this) {
            bitmaps.get(key) ?: runCatching {
                val d = open(file)
                if (index !in 0 until d.renderer.pageCount) return@runCatching null
                d.renderer.openPage(index).use { p ->
                    val h = (widthPx.toFloat() * p.height / p.width.coerceAtLeast(1)).toInt().coerceAtLeast(1)
                    val bmp = Bitmap.createBitmap(widthPx, h, Bitmap.Config.ARGB_8888)
                    bmp.eraseColor(Color.WHITE)
                    p.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                    // Latar putih penuh: tandai tidak tembus pandang supaya ekspor PDF lebih kecil.
                    bmp.setHasAlpha(false)
                    bmp.prepareToDraw()
                    bitmaps.put(key, bmp)
                    bmp
                }
            }.getOrNull()
        }
    }
}
