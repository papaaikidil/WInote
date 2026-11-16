package id.hananote.ink

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.LruCache
import id.hananote.data.ShapeKind
import id.hananote.data.Stroke
import id.hananote.data.Tool
import java.io.File
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/** Membuat bentuk sebagai goresan biasa, supaya bisa dihapus, diurungkan, dan disimpan seperti tulisan. */
object Shapes {

    fun build(kind: ShapeKind, x0: Float, y0: Float, x1: Float, y1: Float, color: Int, width: Float): List<Stroke> {
        val l = min(x0, x1)
        val r = max(x0, x1)
        val t = min(y0, y1)
        val b = max(y0, y1)
        return when (kind) {
            ShapeKind.LINE -> listOf(stroke(floatArrayOf(x0, x1), floatArrayOf(y0, y1), color, width))
            ShapeKind.ARROW -> {
                val len = (hypot(x1 - x0, y1 - y0) * 0.22f).coerceIn(width * 3f, 28f)
                val a = atan2(y1 - y0, x1 - x0)
                val spread = 0.5f
                val lx = x1 - len * cos(a - spread)
                val ly = y1 - len * sin(a - spread)
                val rx = x1 - len * cos(a + spread)
                val ry = y1 - len * sin(a + spread)
                listOf(
                    stroke(floatArrayOf(x0, x1), floatArrayOf(y0, y1), color, width),
                    stroke(floatArrayOf(lx, x1, rx), floatArrayOf(ly, y1, ry), color, width),
                )
            }
            ShapeKind.RECT -> listOf(
                stroke(floatArrayOf(l, r, r, l, l), floatArrayOf(t, t, b, b, t), color, width)
            )
            ShapeKind.TRIANGLE -> {
                val cx = (l + r) / 2f
                listOf(stroke(floatArrayOf(cx, r, l, cx), floatArrayOf(t, b, b, t), color, width))
            }
            ShapeKind.ELLIPSE -> {
                val n = 73
                val cx = (l + r) / 2f
                val cy = (t + b) / 2f
                val rx = (r - l) / 2f
                val ry = (b - t) / 2f
                val xs = FloatArray(n)
                val ys = FloatArray(n)
                for (i in 0 until n) {
                    val ang = (i.toFloat() / (n - 1)) * (Math.PI * 2).toFloat()
                    xs[i] = cx + rx * cos(ang)
                    ys[i] = cy + ry * sin(ang)
                }
                listOf(stroke(xs, ys, color, width))
            }
        }
    }

    private fun stroke(xs: FloatArray, ys: FloatArray, color: Int, width: Float) =
        Stroke(Tool.LINE, color, width, xs, ys, FloatArray(xs.size) { 0.5f })
}

/**
 * Simpanan gambar yang sudah dibuka, supaya tidak dibaca ulang dari berkas tiap kali digambar.
 *
 * Membuka foto besar bisa makan puluhan sampai ratusan milidetik. Bila diminta dari utas utama (saat halaman
 * digambar), gambar dibuka di latar dan sementara dipakai ukuran lain yang sudah ada (atau dilewati), lalu
 * [onLoaded] memberi tahu kanvas untuk menggambar ulang bagian gambar itu. Dari utas latar (gambar kecil
 * halaman, ekspor) gambar tetap dibuka langsung.
 */
object ImageCache {
    private val cache = object : LruCache<String, Bitmap>((Runtime.getRuntime().maxMemory() / 6).toInt()) {
        override fun sizeOf(key: String, value: Bitmap): Int = value.byteCount
    }
    private val buckets = intArrayOf(256, 512, 1024, 2048)
    private val loading = java.util.Collections.synchronizedSet(HashSet<String>())
    private val worker = java.util.concurrent.Executors.newFixedThreadPool(2) { r ->
        Thread(r, "hana-gambar").apply { isDaemon = true; priority = Thread.NORM_PRIORITY - 1 }
    }
    private val main = android.os.Handler(android.os.Looper.getMainLooper())

    /** Dipanggil di utas utama setiap ada gambar selesai dibuka di latar. */
    val onLoaded = java.util.concurrent.CopyOnWriteArrayList<(File) -> Unit>()

    private fun key(file: File, maxDim: Int) = file.path + "@" + maxDim

    private fun onMain() = android.os.Looper.myLooper() == android.os.Looper.getMainLooper()

    /**
     * Saat halaman dibalik atau baru dibuka: gambar yang belum terbuka sama sekali dibuka saat itu juga, supaya
     * tidak muncul belakangan (berkedip). Hanya berlaku di utas utama.
     */
    @Volatile var blockingOnMain = false

    fun get(file: File, maxDim: Int): Bitmap? {
        cache.get(key(file, maxDim))?.let { return it }
        if (!onMain()) return decode(file, maxDim)
        // Sementara: ukuran lain yang sudah terbuka (lebih tajam dulu, lalu yang lebih kecil).
        for (b in buckets.reversed()) if (b != maxDim) cache.get(key(file, b))?.let { request(file, maxDim); return it }
        if (blockingOnMain) return decode(file, maxDim)
        request(file, maxDim)
        return null
    }

    /** Selalu dibuka saat itu juga (untuk jendela yang butuh gambarnya langsung). */
    fun getNow(file: File, maxDim: Int): Bitmap? = cache.get(key(file, maxDim)) ?: decode(file, maxDim)

    /** Buka di latar lebih dulu, misalnya gambar di halaman sebelah. */
    fun prefetch(file: File, maxDim: Int) {
        if (cache.get(key(file, maxDim)) == null) request(file, maxDim)
    }

    private fun request(file: File, maxDim: Int) {
        val k = key(file, maxDim)
        if (!loading.add(k)) return
        worker.execute {
            val ok = runCatching { decode(file, maxDim) }.getOrNull() != null
            loading.remove(k)
            if (ok) main.post { for (l in onLoaded) runCatching { l(file) } }
        }
    }

    private fun decode(file: File, maxDim: Int): Bitmap? {
        val k = key(file, maxDim)
        cache.get(k)?.let { return it }
        if (!file.exists()) return null
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.path, bounds)
        if (bounds.outWidth <= 0) return null
        var sample = 1
        while (max(bounds.outWidth, bounds.outHeight) / (sample * 2) >= maxDim) sample *= 2
        val bmp = BitmapFactory.decodeFile(file.path, BitmapFactory.Options().apply { inSampleSize = sample }) ?: return null
        // Gambar siap pakai di GPU lebih cepat digambar berulang kali.
        bmp.prepareToDraw()
        cache.put(k, bmp)
        return bmp
    }
}
