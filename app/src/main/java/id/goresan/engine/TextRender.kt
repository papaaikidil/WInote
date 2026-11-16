package id.goresan.engine

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PathMeasure
import android.graphics.RectF
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import java.text.BreakIterator
import java.util.Locale
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/**
 * Menggambar [TextSpec] jadi gambar (premultiplied ARGB) di koordinat kanvas: baris lurus (rata kiri/tengah/kanan),
 * melengkung, bergelombang, melingkar, atau mengikuti jalur bebas; dengan garis tepi, bayangan, dan garis bawah.
 */
object TextRender {
    class Result(
        val bmp: Bitmap?,
        val x: Int,
        val y: Int,
        /** Kotak teks (4 sudut, x/y bergantian) di koordinat kanvas, untuk bingkai dan pegangan di layar. */
        val box: FloatArray,
    )

    private class Placed(val s: String, val x: Float, val y: Float, val deg: Float, val adv: Float)

    private fun paintFor(ctx: Context, s: TextSpec): TextPaint = TextPaint(Paint.ANTI_ALIAS_FLAG or Paint.SUBPIXEL_TEXT_FLAG).apply {
        typeface = Fonts.typeface(ctx, s.font, s.bold, s.italic)
        textSize = s.size.coerceIn(2f, 4000f)
        letterSpacing = s.spacing
        isUnderlineText = s.underline
        color = s.color
    }

    private fun graphemes(line: String): List<String> {
        val out = ArrayList<String>()
        val it = BreakIterator.getCharacterInstance()
        it.setText(line)
        var a = it.first()
        var b = it.next()
        while (b != BreakIterator.DONE) {
            out.add(line.substring(a, b))
            a = b
            b = it.next()
        }
        return out
    }

    fun render(ctx: Context, s: TextSpec, canvasW: Int, canvasH: Int): Result {
        val paint = paintFor(ctx, s)
        val raw = if (s.caps) s.text.uppercase(Locale("id")) else s.text
        val empty = raw.isBlank()
        val text = if (empty) "Teks" else raw
        val fm = paint.fontMetrics
        val asc = -fm.ascent
        val desc = fm.descent
        val lh = (asc + desc) * s.lineHeight
        // Tengah huruf (antara puncak dan dasar) diletakkan tepat di jalur.
        val yOff = (asc - desc) / 2f
        val lines = text.split('\n')

        // ---- tata letak di koordinat lokal (titik tengah teks = 0, 0) ----
        var layout: StaticLayout? = null
        var placed: List<Placed> = emptyList()
        val tight = RectF()
        if (s.shape == 0 || (s.shape == 4 && s.path.size < 4)) {
            var w = 1f
            for (l in lines) w = max(w, Layout.getDesiredWidth(l, paint))
            val wi = ceil(w).toInt() + 2
            val align = when (s.align) {
                0 -> Layout.Alignment.ALIGN_NORMAL
                2 -> Layout.Alignment.ALIGN_OPPOSITE
                else -> Layout.Alignment.ALIGN_CENTER
            }
            layout = StaticLayout.Builder.obtain(text, 0, text.length, paint, wi)
                .setAlignment(align)
                .setLineSpacing(0f, s.lineHeight.coerceIn(0.5f, 4f))
                .setIncludePad(false)
                .build()
            tight.set(-wi / 2f, -layout.height / 2f, wi / 2f, layout.height / 2f)
        } else {
            placed = place(s, paint, lines, asc, desc, lh, yOff)
            val pts = FloatArray(8)
            var first = true
            for (g in placed) {
                glyphCorners(g, yOff - asc, yOff + desc, pts)
                for (k in 0 until 4) {
                    if (first) { tight.set(pts[0], pts[1], pts[0], pts[1]); first = false }
                    tight.union(pts[2 * k], pts[2 * k + 1])
                }
            }
            if (first || tight.width() < 1f || tight.height() < 1f) tight.inset(-1f, -1f)
            // Pusatkan supaya titik tengah teks = tengah kotaknya.
            val ox = tight.centerX()
            val oy = tight.centerY()
            placed = placed.map { Placed(it.s, it.x - ox, it.y - oy, it.deg, it.adv) }
            tight.offset(-ox, -oy)
        }

        // ---- ke koordinat kanvas ----
        val m = Matrix().apply {
            postScale(s.scale, s.scale)
            postRotate(Math.toDegrees(s.rot.toDouble()).toFloat())
            postTranslate(s.cx, s.cy)
        }
        val box = floatArrayOf(tight.left, tight.top, tight.right, tight.top, tight.right, tight.bottom, tight.left, tight.bottom)
        m.mapPoints(box)
        if (empty) return Result(null, 0, 0, box)

        // Ruang tambahan untuk ekor huruf tulisan tangan, miring, garis tepi, dan bayangan.
        val size = paint.textSize
        var mx = size * 0.35f + s.outline
        var my = size * 0.3f + s.outline
        if (s.shadow) {
            val blur = s.shadowBlur * size
            mx += abs(s.shadowDx * size) + blur * 2f
            my += abs(s.shadowDy * size) + blur * 2f
        }
        val big = RectF(tight.left - mx, tight.top - my, tight.right + mx, tight.bottom + my)
        m.mapRect(big)
        val x0 = max(0, floor(big.left).toInt())
        val y0 = max(0, floor(big.top).toInt())
        val x1 = min(canvasW, ceil(big.right).toInt())
        val y1 = min(canvasH, ceil(big.bottom).toInt())
        if (x1 <= x0 || y1 <= y0) return Result(null, 0, 0, box)
        val bmp = try {
            Bitmap.createBitmap(x1 - x0, y1 - y0, Bitmap.Config.ARGB_8888)
        } catch (e: OutOfMemoryError) {
            return Result(null, 0, 0, box)
        }
        val c = Canvas(bmp)
        c.translate(-x0.toFloat(), -y0.toFloat())
        c.concat(m)

        // Lintasan gambar: (bayangan +) garis tepi, lalu isi.
        val passes = ArrayList<Int>()
        if (s.outline > 0f) passes.add(1)
        passes.add(0)
        for ((i, pass) in passes.withIndex()) {
            if (pass == 1) {
                paint.style = Paint.Style.STROKE
                paint.strokeWidth = s.outline * 2f
                paint.strokeJoin = Paint.Join.ROUND
                paint.strokeMiter = 2f
                paint.color = s.outlineColor
            } else {
                paint.style = Paint.Style.FILL
                paint.color = s.color
            }
            if (i == 0 && s.shadow) {
                paint.setShadowLayer(max(0.5f, s.shadowBlur * size), s.shadowDx * size, s.shadowDy * size, s.shadowColor)
            } else {
                paint.clearShadowLayer()
            }
            val lay = layout
            if (lay != null) {
                c.save()
                c.translate(tight.left, tight.top)
                lay.draw(c)
                c.restore()
            } else {
                for (g in placed) {
                    c.save()
                    c.translate(g.x, g.y)
                    c.rotate(g.deg)
                    c.drawText(g.s, -g.adv / 2f, yOff, paint)
                    c.restore()
                }
            }
        }
        return Result(bmp, x0, y0, box)
    }

    private fun glyphCorners(g: Placed, top: Float, bottom: Float, out: FloatArray) {
        val a = Math.toRadians(g.deg.toDouble())
        val ca = cos(a).toFloat()
        val sa = sin(a).toFloat()
        val xs = floatArrayOf(-g.adv / 2f, g.adv / 2f, g.adv / 2f, -g.adv / 2f)
        val ys = floatArrayOf(top, top, bottom, bottom)
        for (k in 0 until 4) {
            out[2 * k] = g.x + xs[k] * ca - ys[k] * sa
            out[2 * k + 1] = g.y + xs[k] * sa + ys[k] * ca
        }
    }

    /** Letakkan tiap huruf di sepanjang bentuk baris (lengkung, gelombang, lingkaran, jalur). */
    private fun place(s: TextSpec, paint: TextPaint, lines: List<String>, asc: Float, desc: Float, lh: Float, yOff: Float): List<Placed> {
        val glyphs = lines.map { l -> graphemes(l).map { g -> g to paint.measureText(g) } }
        val widths = glyphs.map { l -> l.sumOf { it.second.toDouble() }.toFloat() }
        val lmax = max(1f, widths.maxOrNull() ?: 1f)
        val out = ArrayList<Placed>()
        fun startOf(k: Int) = when (s.align) {
            0 -> -lmax / 2f
            2 -> lmax / 2f - widths[k]
            else -> -widths[k] / 2f
        }
        when (s.shape) {
            1 -> {
                // Lengkung: busur lingkaran; curve 1 = setengah lingkaran.
                val c = s.curve.coerceIn(-1f, 1f)
                if (abs(c) < 0.01f) return straightPlaced(glyphs, ::startOf, lh)
                val r0 = lmax / (abs(c) * PI.toFloat())
                for ((k, line) in glyphs.withIndex()) {
                    val rk = if (c > 0) max(lh * 0.5f, r0 - k * lh) else r0 + k * lh
                    var t = startOf(k)
                    for ((g, adv) in line) {
                        val th = (t + adv / 2f) / rk
                        if (c > 0) {
                            out.add(Placed(g, rk * sin(th), r0 - rk * cos(th), Math.toDegrees(th.toDouble()).toFloat(), adv))
                        } else {
                            out.add(Placed(g, rk * sin(th), -r0 + rk * cos(th), -Math.toDegrees(th.toDouble()).toFloat(), adv))
                        }
                        t += adv
                    }
                }
            }
            3 -> {
                // Lingkaran: teks mengelilingi lingkaran, mulai dari atas (atau dari bawah bila dibalik).
                val r0 = max(asc + desc, lmax / (2f * PI.toFloat()))
                for ((k, line) in glyphs.withIndex()) {
                    val rk = if (s.flip) r0 + k * lh else max(lh * 0.5f, r0 - k * lh)
                    var t = startOf(k)
                    for ((g, adv) in line) {
                        val ph = (t + adv / 2f) / rk
                        if (!s.flip) {
                            out.add(Placed(g, rk * sin(ph), -rk * cos(ph), Math.toDegrees(ph.toDouble()).toFloat(), adv))
                        } else {
                            out.add(Placed(g, rk * sin(ph), rk * cos(ph), -Math.toDegrees(ph.toDouble()).toFloat(), adv))
                        }
                        t += adv
                    }
                }
            }
            else -> {
                // Gelombang atau jalur bebas: huruf diletakkan menurut panjang lintasan.
                val path = Path()
                if (s.shape == 2) {
                    val amp = s.waveAmp * paint.textSize
                    val len = max(paint.textSize * 0.5f, s.waveLen * paint.textSize)
                    val x0 = -lmax
                    val steps = max(16, (2f * lmax / 3f).toInt())
                    for (i in 0..steps) {
                        val x = x0 + 2f * lmax * i / steps
                        val y = amp * sin(2f * PI.toFloat() * x / len)
                        if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
                    }
                } else {
                    val p = s.path
                    path.moveTo(p[0], p[1])
                    var i = 2
                    while (i + 3 < p.size) {
                        // Kurva halus lewat titik tengah antartitik.
                        path.quadTo(p[i], p[i + 1], (p[i] + p[i + 2]) / 2f, (p[i + 1] + p[i + 3]) / 2f)
                        i += 2
                    }
                    path.lineTo(p[p.size - 2], p[p.size - 1])
                }
                val pm = PathMeasure(path, false)
                val plen = pm.length
                val pos = FloatArray(2)
                val tan = FloatArray(2)
                for ((k, line) in glyphs.withIndex()) {
                    // Rata tengah/kiri/kanan diukur di sepanjang lintasan.
                    var t = when (s.align) {
                        0 -> if (s.shape == 2) (plen - lmax) / 2f else 0f
                        2 -> if (s.shape == 2) (plen + lmax) / 2f - widths[k] else plen - widths[k]
                        else -> (plen - widths[k]) / 2f
                    }
                    for ((g, adv) in line) {
                        var d = t + adv / 2f
                        if (s.flip) d = plen - d
                        posTan(pm, plen, d, pos, tan)
                        var ang = Math.toDegrees(kotlin.math.atan2(tan[1], tan[0]).toDouble()).toFloat()
                        if (s.flip) ang += 180f
                        val a = Math.toRadians(ang.toDouble())
                        // Baris berikutnya bergeser tegak lurus jalur.
                        val nx = -sin(a).toFloat() * k * lh
                        val ny = cos(a).toFloat() * k * lh
                        out.add(Placed(g, pos[0] + nx, pos[1] + ny, ang, adv))
                        t += adv
                    }
                }
            }
        }
        return out
    }

    private fun straightPlaced(
        glyphs: List<List<Pair<String, Float>>>,
        startOf: (Int) -> Float,
        lh: Float,
    ): List<Placed> {
        val out = ArrayList<Placed>()
        for ((k, line) in glyphs.withIndex()) {
            var t = startOf(k)
            for ((g, adv) in line) {
                out.add(Placed(g, t + adv / 2f, k * lh, 0f, adv))
                t += adv
            }
        }
        return out
    }

    /** Posisi dan arah di jarak [d] sepanjang lintasan; di luar ujung, diteruskan lurus searah ujungnya. */
    private fun posTan(pm: PathMeasure, len: Float, d: Float, pos: FloatArray, tan: FloatArray) {
        when {
            d < 0f -> {
                pm.getPosTan(0f, pos, tan)
                pos[0] += tan[0] * d
                pos[1] += tan[1] * d
            }
            d > len -> {
                pm.getPosTan(len, pos, tan)
                pos[0] += tan[0] * (d - len)
                pos[1] += tan[1] * (d - len)
            }
            else -> pm.getPosTan(d, pos, tan)
        }
        if (hypot(tan[0], tan[1]) < 1e-6f) { tan[0] = 1f; tan[1] = 0f }
    }
}
