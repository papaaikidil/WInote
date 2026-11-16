package id.hananote.ink

import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Shader
import id.hananote.data.PageBackground
import id.hananote.data.PageMeta
import id.hananote.data.PaperTexture
import java.io.File

/** Latar halaman: tekstur kertas (diubin) atau gambar dari perangkat (memenuhi halaman). */
object PaperBackgrounds {

    private val tiles = HashMap<PaperTexture, Bitmap>()
    private val averages = HashMap<String, Int>()

    /** Lebar gambar halaman PDF yang dipakai untuk skala tampilan [scale]. */
    fun pdfWidth(page: PageMeta, scale: Float): Int {
        val need = page.width * scale
        return when {
            need <= 600f -> 600
            need <= 1200f || InkRenderer.drawingPdf() -> 1200
            else -> 2048
        }
    }

    /** Ukuran gambar latar yang dipakai untuk skala tampilan [scale]. */
    fun imageBucket(page: PageMeta, scale: Float): Int {
        val need = maxOf(page.width, page.height) * scale
        return when {
            need <= 700f -> 512
            need <= 1400f -> 1024
            else -> 2048
        }
    }

    /** Siapkan foto atau halaman PDF latar di latar belakang, supaya nanti langsung tampil. */
    fun prefetch(page: PageMeta, imageDir: File, scale: Float) {
        val bg = page.background
        PageBackground.pdfOf(bg)?.let { (name, index) -> PdfPages.prefetch(File(imageDir, name), index, pdfWidth(page, scale)); return }
        PageBackground.imageOf(bg)?.let { ImageCache.prefetch(File(imageDir, it), imageBucket(page, scale)) }
    }

    fun tile(t: PaperTexture): Bitmap = synchronized(tiles) {
        tiles.getOrPut(t) { Bitmap.createBitmap(TextureGen.pixels(t), t.tilePx, t.tilePx, Bitmap.Config.ARGB_8888) }
    }

    /**
     * Gambar latar di atas warna kertas yang sudah diisi. Hasil: warna yang mewakili latar, untuk
     * menentukan warna garis pola (gelap atau terang).
     */
    fun draw(c: Canvas, page: PageMeta, paint: Paint, imageDir: File?, scale: Float): Int {
        val bg = page.background
        PageBackground.textureOf(bg)?.let { t ->
            val bmp = tile(t)
            val shader = BitmapShader(bmp, Shader.TileMode.REPEAT, Shader.TileMode.REPEAT)
            val k = t.tilePt / t.tilePx
            shader.setLocalMatrix(Matrix().apply { setScale(k, k) })
            paint.reset()
            paint.isFilterBitmap = true
            paint.shader = shader
            c.drawRect(0f, 0f, page.width, page.height, paint)
            paint.shader = null
            return t.base
        }
        PageBackground.pdfOf(bg)?.let { (name, index) ->
            if (imageDir == null) return page.paperColor
            val px = pdfWidth(page, scale)
            val f = File(imageDir, name)
            val bmp = PdfPages.render(f, index, px) ?: (if (px > 1200) PdfPages.render(f, index, 1200) else null) ?: return page.paperColor
            paint.reset()
            paint.isFilterBitmap = true
            c.drawBitmap(bmp, null, RectF(0f, 0f, page.width, page.height), paint)
            return android.graphics.Color.WHITE
        }
        val file = PageBackground.imageOf(bg)
        if (file != null && imageDir != null) {
            val bucket = imageBucket(page, scale)
            val f = File(imageDir, file)
            val bmp = ImageCache.get(f, bucket) ?: return page.paperColor
            // Penuhi halaman tanpa mengubah perbandingan gambar; bagian yang lebih dipotong di tengah.
            val pageRatio = page.width / page.height
            val imgRatio = bmp.width.toFloat() / bmp.height
            val src = if (imgRatio > pageRatio) {
                val w = (bmp.height * pageRatio).toInt().coerceAtLeast(1)
                val l = (bmp.width - w) / 2
                Rect(l, 0, l + w, bmp.height)
            } else {
                val h = (bmp.width / pageRatio).toInt().coerceAtLeast(1)
                val t = (bmp.height - h) / 2
                Rect(0, t, bmp.width, t + h)
            }
            paint.reset()
            paint.isFilterBitmap = true
            c.drawBitmap(bmp, src, RectF(0f, 0f, page.width, page.height), paint)
            return average(f.path, bmp)
        }
        return page.paperColor
    }

    private fun average(key: String, bmp: Bitmap): Int = synchronized(averages) {
        averages.getOrPut(key) {
            runCatching {
                val one = Bitmap.createScaledBitmap(bmp, 1, 1, true)
                val px = one.getPixel(0, 0)
                if (one !== bmp) one.recycle()
                px
            }.getOrDefault(0xFFFFFFFF.toInt())
        }
    }
}
