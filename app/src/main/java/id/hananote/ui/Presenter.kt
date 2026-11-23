package id.hananote.ui

import android.app.Presentation
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.os.Bundle
import android.view.Display
import android.view.View
import id.hananote.ink.LaserTrail

/**
 * Layar presentasi (proyektor, monitor, atau layar nirkabel): hanya halaman yang sedang dibuka, pas selayar,
 * tanpa tombol apa pun, ditambah jejak laser yang sama dengan di tablet.
 */
class PagePresentation(context: Context, display: Display, laser: LaserTrail) : Presentation(context, display) {
    private val stage = PresentView(context, laser)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(stage)
    }

    /** Lebar gambar halaman yang pas untuk layar ini (piksel), untuk halaman berukuran [pageW] x [pageH]. */
    fun targetWidth(pageW: Float, pageH: Float): Int {
        val d = display
        val m = android.util.DisplayMetrics()
        @Suppress("DEPRECATION")
        d.getRealMetrics(m)
        val w = m.widthPixels.coerceAtLeast(320)
        val h = m.heightPixels.coerceAtLeast(240)
        val fit = minOf(w / pageW, h / pageH)
        return (pageW * fit).toInt().coerceIn(200, 4096)
    }

    /** Ganti gambar halaman yang ditampilkan. [pageW]/[pageH] ukuran halaman dalam satuan halaman. */
    fun showPage(bmp: Bitmap?, pageW: Float, pageH: Float) = stage.set(bmp, pageW, pageH)

    override fun onStop() {
        stage.release()
        super.onStop()
    }
}

/** Isi layar presentasi: latar gelap, halaman di tengah, laser di atasnya. */
private class PresentView(context: Context, private val laser: LaserTrail) : View(context) {
    private var bmp: Bitmap? = null
    private var pageW = 1f
    private var pageH = 1f
    private val paint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG)
    private val shadow = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0x33000000 }
    private val dst = RectF()
    private val onLaser: () -> Unit = { postInvalidateOnAnimation() }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        laser.addListener(onLaser)
    }

    override fun onDetachedFromWindow() {
        laser.removeListener(onLaser)
        super.onDetachedFromWindow()
    }

    fun set(b: Bitmap?, w: Float, h: Float) {
        val old = bmp
        bmp = b
        pageW = w.coerceAtLeast(1f)
        pageH = h.coerceAtLeast(1f)
        if (old != null && old !== b) old.recycle()
        invalidate()
    }

    fun release() {
        bmp?.recycle()
        bmp = null
    }

    override fun onDraw(canvas: Canvas) {
        canvas.drawColor(0xFF1E1F22.toInt())
        if (width == 0 || height == 0) return
        val fit = minOf(width / pageW, height / pageH)
        val dw = pageW * fit
        val dh = pageH * fit
        val left = (width - dw) / 2f
        val top = (height - dh) / 2f
        dst.set(left, top, left + dw, top + dh)
        val b = bmp
        if (b != null && !b.isRecycled) {
            canvas.drawRect(dst.left + 4f, dst.top + 6f, dst.right + 4f, dst.bottom + 6f, shadow)
            canvas.drawBitmap(b, null, dst, paint)
        }
        canvas.save()
        canvas.translate(left, top)
        canvas.scale(fit, fit)
        canvas.clipRect(0f, 0f, pageW, pageH)
        // Laser selalu setebal yang sama di layar presentasi, apa pun ukuran halamannya.
        val px = 1f / fit * (resources.displayMetrics.density / 2f).coerceAtLeast(1f)
        laser.draw(canvas, px)
        canvas.restore()
        if (laser.visible()) postInvalidateOnAnimation()
    }
}
