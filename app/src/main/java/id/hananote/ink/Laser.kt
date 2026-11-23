package id.hananote.ink

import android.graphics.Canvas
import android.graphics.Paint
import android.os.SystemClock

/**
 * Jejak laser untuk presentasi: titik-titik (satuan halaman) yang memudar sendiri, tidak pernah disimpan ke catatan.
 * Dipakai bersama oleh kanvas di tablet dan layar presentasi (proyektor), jadi keduanya menampilkan laser yang sama.
 */
class LaserTrail {
    private var xs = FloatArray(512)
    private var ys = FloatArray(512)
    private var ts = LongArray(512)
    private var n = 0
    /** Pena/jari sedang menempel (titik terang di ujung). */
    var down = false
        private set
    private val listeners = ArrayList<() -> Unit>()

    fun addListener(l: () -> Unit) { listeners.add(l) }
    fun removeListener(l: () -> Unit) { listeners.remove(l) }
    private fun changed() { for (l in listeners.toList()) l() }

    fun start(x: Float, y: Float) {
        down = true
        add(x, y)
    }

    fun add(x: Float, y: Float) {
        if (n == xs.size) {
            // Buang separuh yang paling tua.
            val keep = n / 2
            System.arraycopy(xs, n - keep, xs, 0, keep)
            System.arraycopy(ys, n - keep, ys, 0, keep)
            System.arraycopy(ts, n - keep, ts, 0, keep)
            n = keep
        }
        xs[n] = x; ys[n] = y; ts[n] = SystemClock.uptimeMillis()
        n++
        changed()
    }

    fun end() {
        down = false
        changed()
    }

    fun clear() {
        n = 0
        down = false
        changed()
    }

    /** Masih ada yang perlu digambar (belum pudar semua). */
    fun visible(now: Long = SystemClock.uptimeMillis()): Boolean = down || (n > 0 && now - ts[n - 1] < FADE_MS)

    private val glow = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    private val dot = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }

    /**
     * Gambar jejak di [canvas] yang sudah memakai satuan halaman; [px] = ukuran satu piksel layar dalam satuan
     * halaman (supaya tebal laser tetap sama di layar mana pun).
     */
    fun draw(canvas: Canvas, px: Float, now: Long = SystemClock.uptimeMillis()) {
        // Titik yang sudah pudar dibuang.
        var first = 0
        while (first < n && now - ts[first] > FADE_MS && !(down && first == n - 1)) first++
        if (first > 0) {
            System.arraycopy(xs, first, xs, 0, n - first)
            System.arraycopy(ys, first, ys, 0, n - first)
            System.arraycopy(ts, first, ts, 0, n - first)
            n -= first
        }
        if (n == 0) return
        for (i in 1 until n) {
            val a = 1f - ((now - ts[i]).toFloat() / FADE_MS).coerceIn(0f, 1f)
            if (a <= 0f) continue
            glow.color = 0xFFFF1744.toInt()
            glow.alpha = (a * 90).toInt()
            glow.strokeWidth = 14f * px
            canvas.drawLine(xs[i - 1], ys[i - 1], xs[i], ys[i], glow)
            glow.color = 0xFFFF5252.toInt()
            glow.alpha = (a * 255).toInt()
            glow.strokeWidth = 5f * px
            canvas.drawLine(xs[i - 1], ys[i - 1], xs[i], ys[i], glow)
        }
        if (down) {
            val x = xs[n - 1]; val y = ys[n - 1]
            dot.color = 0x66FF1744
            canvas.drawCircle(x, y, 12f * px, dot)
            dot.color = 0xFFFF1744.toInt()
            canvas.drawCircle(x, y, 5f * px, dot)
            dot.color = 0xFFFFFFFF.toInt()
            canvas.drawCircle(x, y, 2f * px, dot)
        }
    }

    companion object {
        /** Lama jejak laser memudar. */
        const val FADE_MS = 900L
    }
}
