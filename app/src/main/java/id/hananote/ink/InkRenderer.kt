package id.hananote.ink

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.BitmapShader
import android.graphics.BlurMaskFilter
import android.graphics.Matrix
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import id.hananote.data.CalendarItem
import id.hananote.data.MindMapItem
import id.hananote.data.AudioItem
import id.hananote.data.IndoDate
import java.time.LocalDate
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt
import android.text.StaticLayout
import android.text.Layout
import id.hananote.data.Align
import android.text.TextPaint
import android.text.TextUtils
import id.hananote.data.FontRegistry
import id.hananote.data.ImageItem
import id.hananote.data.LinkItem
import id.hananote.data.PageItem
import id.hananote.data.StickyItem
import id.hananote.data.TableItem
import id.hananote.data.TapeItem
import id.hananote.data.CoverItem
import id.hananote.data.TapeShape
import java.io.File
import id.hananote.data.PageContent
import id.hananote.data.PageMeta
import id.hananote.data.PaperSize
import id.hananote.data.PaperTemplate
import id.hananote.data.Stroke
import id.hananote.data.TextItem
import id.hananote.data.Tool
import id.hananote.data.layerZ
import id.hananote.data.TextLists
import android.text.SpannableString
import android.text.Spanned
import android.text.style.LeadingMarginSpan
import android.text.style.LineHeightSpan
import android.text.style.ReplacementSpan
import android.text.style.StrikethroughSpan
import android.text.style.StyleSpan
import android.text.style.UnderlineSpan
import id.hananote.data.TextRuns
import android.text.style.ForegroundColorSpan

/** Menggambar kertas dan goresan. Semua koordinat dalam satuan halaman (poin). */
object InkRenderer {

    fun widthAt(tool: Tool, base: Float, p: Float): Float = when (tool) {
        Tool.PEN -> base * (0.55f + 0.6f * p)
        Tool.BRUSH -> base * (0.12f + 1.7f * p * p)
        Tool.PENCIL -> base * (0.75f + 0.35f * p)
        Tool.MARKER, Tool.HIGHLIGHTER, Tool.ERASER, Tool.LINE -> base
        Tool.WATERCOLOR -> base * (0.45f + 0.85f * p)
        Tool.TAPE -> base
        Tool.QALAM -> base * (0.72f + 0.4f * p)
    }

    private val darkenMode = PorterDuffXfermode(PorterDuff.Mode.DARKEN)
    /** Lapisan goresan yang menggelapkan (dipakai bila goresannya punya bekas penghapus). */
    private val layerDarken = Paint().apply { xfermode = PorterDuffXfermode(PorterDuff.Mode.DARKEN) }

    /** Bekas kuas penghapus lembut (koordinat halaman) pada lapisan yang sedang dibuka. */
    private fun drawMasks(c: Canvas, masks: List<Stroke>, paint: Paint) {
        for (k in masks) drawSoftErase(c, Color.alpha(k.color) / 255f, k.width, k.xs, k.ys, k.ps, k.size, paint, null)
    }
    private val dstOut = PorterDuffXfermode(PorterDuff.Mode.DST_OUT)

    /** Cap kuas penghapus: lingkaran yang pudar halus ke tepinya. */
    private val softDab: Bitmap by lazy {
        val n = 64
        val px = IntArray(n * n)
        val c = (n - 1) / 2f
        for (y in 0 until n) for (x in 0 until n) {
            val d = sqrt((x - c) * (x - c) + (y - c) * (y - c)) / (c + 0.5f)
            val a = if (d >= 1f) 0f else (1f - d * d).let { it * it }
            px[y * n + x] = ((a * 255).toInt().coerceIn(0, 255) shl 24)
        }
        Bitmap.createBitmap(px, n, n, Bitmap.Config.ARGB_8888)
    }

    /**
     * Sapuan kuas penghapus lembut: cap-cap pudar yang mengurangi isi di bawahnya sedikit demi sedikit,
     * jadi tepinya bergradasi dan sapuan berulang makin bersih. Bila [previewColor] diisi, sapuan
     * digambar dengan warna itu (pratinjau saat masih menyapu).
     */
    fun drawSoftErase(
        canvas: Canvas, strength: Float, width: Float,
        xs: FloatArray, ys: FloatArray, ps: FloatArray, n: Int,
        paint: Paint, previewColor: Int?,
    ) {
        if (n == 0 || vectorSafe.get() == true) return
        paint.reset()
        paint.isAntiAlias = true
        paint.isFilterBitmap = true
        if (previewColor != null) {
            paint.colorFilter = android.graphics.PorterDuffColorFilter(previewColor or 0xFF000000.toInt(), PorterDuff.Mode.SRC_IN)
        } else {
            paint.xfermode = dstOut
        }
        val bmp = softDab
        val rect = RectF()
        val s = strength.coerceIn(0.05f, 1f)
        fun dab(x: Float, y: Float, p: Float) {
            val r = width / 2f * (0.6f + 0.4f * p)
            rect.set(x - r, y - r, x + r, y + r)
            paint.alpha = (255f * s * (0.35f + 0.65f * p)).toInt().coerceIn(1, 255)
            canvas.drawBitmap(bmp, null, rect, paint)
        }
        dab(xs[0], ys[0], ps[0])
        val spacing = max(width * 0.14f, 0.5f)
        var carry = 0f
        for (i in 1 until n) {
            val dx = xs[i] - xs[i - 1]
            val dy = ys[i] - ys[i - 1]
            val len = sqrt(dx * dx + dy * dy)
            if (len <= 0f) continue
            var t = spacing - carry
            while (t <= len) {
                val f = t / len
                dab(xs[i - 1] + dx * f, ys[i - 1] + dy * f, ps[i - 1] + (ps[i] - ps[i - 1]) * f)
                t += spacing
            }
            carry = len - (t - spacing)
        }
        paint.colorFilter = null
        paint.xfermode = null
    }
    private val lightPaper = ThreadLocal<Boolean>()

    /** Di kertas terang, stabilo dan cat air digambar dengan cara menggelapkan saja. */
    private fun darkens(): Boolean = vectorSafe.get() != true && lightPaper.get() != false

    private fun usesPath(tool: Tool) =
        tool == Tool.MARKER || tool == Tool.HIGHLIGHTER || tool == Tool.PENCIL || tool == Tool.LINE

    fun drawStroke(canvas: Canvas, s: Stroke, paint: Paint, path: Path) {
        drawPoints(canvas, s.tool, s.color, s.width, s.xs, s.ys, s.ps, s.size, paint, path)
    }

    /** Ukuran gambar yang dipakai untuk [item] pada skala tampilan [scale]. */
    fun imageBucket(item: ImageItem, scale: Float): Int {
        val crop = item.crop
        val cw = (crop.r - crop.l).coerceAtLeast(0.01f)
        val ch = (crop.b - crop.t).coerceAtLeast(0.01f)
        // Gambar yang dipotong perlu resolusi lebih tinggi supaya bagian yang tampil tetap tajam.
        val need = maxOf(item.w / cw, item.h / ch) * scale
        return when {
            need <= 600f -> 512
            need <= 1200f -> 1024
            else -> 2048
        }
    }

    fun drawPoints(
        canvas: Canvas,
        tool: Tool,
        color: Int,
        width: Float,
        xs: FloatArray,
        ys: FloatArray,
        ps: FloatArray,
        n: Int,
        paint: Paint,
        path: Path,
    ) {
        if (n == 0) return
        if (tool == Tool.ERASER) {
            // Bekas kuas penghapus lembut (kekuatannya disimpan di alfa warna).
            drawSoftErase(canvas, Color.alpha(color) / 255f, width, xs, ys, ps, n, paint, null)
            return
        }
        if (tool == Tool.WATERCOLOR) {
            drawWatercolor(canvas, color, width, xs, ys, ps, n, paint)
            return
        }
        if (tool == Tool.QALAM) {
            drawQalam(canvas, color, width, xs, ys, ps, n, paint, path)
            return
        }
        paint.reset()
        paint.isAntiAlias = true
        paint.isDither = true

        if (usesPath(tool)) {
            var avgP = 0f
            for (i in 0 until n) avgP += ps[i]
            avgP /= n
            paint.style = Paint.Style.STROKE
            paint.strokeJoin = Paint.Join.ROUND
            when (tool) {
                Tool.HIGHLIGHTER -> {
                    paint.color = withAlpha(color, 0.38f)
                    // Hanya menggelapkan: tulisan yang tertimpa stabilo tetap pekat, di lapisan mana pun.
                    if (darkens()) paint.xfermode = darkenMode
                    paint.strokeCap = Paint.Cap.SQUARE
                    paint.strokeWidth = width
                }
                Tool.PENCIL -> {
                    paint.color = withAlpha(color, 0.5f + 0.35f * avgP)
                    paint.strokeCap = Paint.Cap.ROUND
                    paint.strokeWidth = widthAt(tool, width, avgP)
                }
                else -> {
                    paint.color = withAlpha(color, 0.94f)
                    paint.strokeCap = Paint.Cap.ROUND
                    paint.strokeWidth = width
                }
            }
            path.rewind()
            path.moveTo(xs[0], ys[0])
            if (n == 1) {
                path.lineTo(xs[0] + 0.01f, ys[0])
            } else if (tool == Tool.LINE) {
                // Bentuk: sudut tetap tajam, tanpa penghalusan.
                for (i in 1 until n) path.lineTo(xs[i], ys[i])
            } else {
                for (i in 1 until n - 1) {
                    val mx = (xs[i] + xs[i + 1]) / 2f
                    val my = (ys[i] + ys[i + 1]) / 2f
                    path.quadTo(xs[i], ys[i], mx, my)
                }
                path.lineTo(xs[n - 1], ys[n - 1])
            }
            canvas.drawPath(path, paint)
            return
        }

        // Pena dan kuas: lebar mengikuti tekanan, digambar ruas demi ruas.
        paint.color = color
        if (n == 1) {
            paint.style = Paint.Style.FILL
            canvas.drawCircle(xs[0], ys[0], widthAt(tool, width, ps[0]) / 2f, paint)
            return
        }
        paint.style = Paint.Style.STROKE
        paint.strokeCap = Paint.Cap.ROUND
        for (i in 1 until n) {
            paint.strokeWidth = (widthAt(tool, width, ps[i - 1]) + widthAt(tool, width, ps[i])) / 2f
            canvas.drawLine(xs[i - 1], ys[i - 1], xs[i], ys[i], paint)
        }
    }

    // ---------- qalam (khat Arab) ----------

    /** Arah tepi ujung qalam: ±70° dari garis tulis, condong ke kanan atas (sumbu y halaman ke bawah). */
    private const val QALAM_DEG = 70.0
    private val qalamCos = kotlin.math.cos(Math.toRadians(QALAM_DEG)).toFloat()
    private val qalamSin = kotlin.math.sin(Math.toRadians(QALAM_DEG)).toFloat()

    /**
     * Goresan qalam: jejak ujung pipih yang disapukan. Tiap ruas jadi segi empat antara tepi ujung di titik awal dan
     * akhirnya, ditambah bentuk ujung (pipih, sedikit tebal) di tiap titik; semuanya disatukan dalam satu jalur
     * supaya warna transparan tetap rata. Tebal tarikan mengikuti arahnya terhadap tepi ujung, seperti qalam asli.
     */
    private fun drawQalam(
        canvas: Canvas, color: Int, width: Float, xs: FloatArray, ys: FloatArray, ps: FloatArray, n: Int,
        paint: Paint, path: Path,
    ) {
        paint.color = color
        paint.style = Paint.Style.FILL
        path.rewind()
        path.fillType = Path.FillType.WINDING
        // Setengah tepi ujung (ex, ey) dan setengah tebal ujung (tx, ty) per titik.
        fun half(i: Int) = widthAt(Tool.QALAM, width, ps[i]) / 2f
        fun poly(ax: Float, ay: Float, bx: Float, by: Float, cx: Float, cy: Float, dx: Float, dy: Float) {
            // Semua segi empat searah jarum jam supaya gabungannya tidak berlubang.
            val area = (bx - ax) * (cy - ay) - (cx - ax) * (by - ay) + (cx - ax) * (dy - ay) - (dx - ax) * (cy - ay)
            path.moveTo(ax, ay)
            if (area >= 0f) { path.lineTo(bx, by); path.lineTo(cx, cy); path.lineTo(dx, dy) }
            else { path.lineTo(dx, dy); path.lineTo(cx, cy); path.lineTo(bx, by) }
            path.close()
        }
        val ux = qalamCos
        val uy = -qalamSin
        for (i in 0 until n) {
            val h = half(i)
            val ex = ux * h; val ey = uy * h
            // Ujung qalam punya tebal kecil (±12% lebarnya) supaya tarikan searah tepi tidak hilang.
            val t = max(h * 0.12f, 0.25f)
            val tx = -uy * t; val ty = ux * t
            poly(
                xs[i] - ex - tx, ys[i] - ey - ty, xs[i] + ex - tx, ys[i] + ey - ty,
                xs[i] + ex + tx, ys[i] + ey + ty, xs[i] - ex + tx, ys[i] - ey + ty,
            )
            if (i == 0) continue
            val h0 = half(i - 1)
            poly(
                xs[i - 1] - ux * h0, ys[i - 1] - uy * h0, xs[i - 1] + ux * h0, ys[i - 1] + uy * h0,
                xs[i] + ex, ys[i] + ey, xs[i] - ex, ys[i] - ey,
            )
        }
        canvas.drawPath(path, paint)
    }

    // ---------- cat air ----------

    /** Butiran kertas: bintik acak yang sedikit menggumpal, diulang di seluruh halaman. */
    private val grainBitmap: Bitmap by lazy {
        val n = 64
        val rnd = java.util.Random(11)
        val raw = FloatArray(n * n) { rnd.nextFloat() }
        val px = IntArray(n * n)
        for (y in 0 until n) for (x in 0 until n) {
            var sum = 0f
            for (dy in -1..1) for (dx in -1..1) sum += raw[((y + dy + n) % n) * n + (x + dx + n) % n]
            val a = ((sum / 9f - 0.44f) * 3f).coerceIn(0f, 1f)
            px[y * n + x] = (a * 255).toInt() shl 24
        }
        Bitmap.createBitmap(px, n, n, Bitmap.Config.ARGB_8888)
    }

    /** Alat gambar cat air per jalur (layar dan latar bisa menggambar bersamaan). */
    private class WcTools {
        val bounds = RectF()
        val path = Path()
        val outer = Paint()
        val hollow = Paint().apply { xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_OUT) }
        val grain = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_OUT)
            shader = BitmapShader(grainBitmap, Shader.TileMode.REPEAT, Shader.TileMode.REPEAT).apply {
                setLocalMatrix(Matrix().apply { setScale(0.6f, 0.6f) })
            }
        }
    }

    private val wcTools = ThreadLocal.withInitial { WcTools() }

    /**
     * Sapuan cat air: satu lapisan warna transparan yang rata (tidak menumpuk di dalam satu sapuan),
     * tepinya sedikit lebih pekat seperti pigmen yang mengumpul saat mengering, lebarnya bergelombang
     * halus, dan ada butiran kertas. Sapuan yang saling tumpang tindih makin pekat seperti cat sungguhan.
     */
    private fun drawWatercolor(canvas: Canvas, color: Int, width: Float, xs: FloatArray, ys: FloatArray, ps: FloatArray, n: Int, paint: Paint) {
        val t = wcTools.get() ?: return
        var l = xs[0]; var tp = ys[0]; var r = xs[0]; var b = ys[0]
        for (i in 1 until n) {
            if (xs[i] < l) l = xs[i]
            if (xs[i] > r) r = xs[i]
            if (ys[i] < tp) tp = ys[i]
            if (ys[i] > b) b = ys[i]
        }
        val pad = width * 1.6f + 2f
        t.bounds.set(l - pad, tp - pad, r + pad, b + pad)
        val seed = xs[0] * 13.37f + ys[0] * 7.31f

        t.outer.alpha = (0.36f * Color.alpha(color)).toInt().coerceIn(0, 255)
        // Cat air hanya menggelapkan, jadi tulisan yang tertimpa tetap terbaca seperti tinta di bawah cat.
        t.outer.xfermode = if (darkens()) darkenMode else null
        val outerLayer = canvas.saveLayer(t.bounds, t.outer)
        paint.reset()
        paint.isAntiAlias = true
        paint.style = Paint.Style.STROKE
        paint.strokeCap = Paint.Cap.ROUND
        paint.strokeJoin = Paint.Join.ROUND
        paint.color = color or 0xFF000000.toInt()
        wcBody(canvas, xs, ys, ps, n, width, 1f, seed, paint)
        if (vectorSafe.get() == true) {
            // PDF: cukup lapisan warna transparan yang rata.
            canvas.restoreToCount(outerLayer)
            return
        }

        // Bagian tengah dibuat lebih tipis supaya tepinya tampak lebih pekat.
        t.hollow.alpha = 118
        val hollowLayer = canvas.saveLayer(t.bounds, t.hollow)
        paint.color = 0xFF000000.toInt()
        if (!canvas.isHardwareAccelerated && width > 3f && n > 1) {
            // Satu jalur dengan satu kali pengaburan (jauh lebih ringan daripada mengaburkan tiap ruas).
            var avg = 0f
            for (i in 0 until n) avg += widthAt(Tool.WATERCOLOR, width, ps[i])
            avg /= n
            val hollow = t.path
            hollow.rewind()
            hollow.moveTo(xs[0], ys[0])
            for (i in 1 until n) hollow.lineTo(xs[i], ys[i])
            paint.strokeWidth = (avg * 0.6f).coerceAtLeast(0.5f)
            paint.maskFilter = BlurMaskFilter(width * 0.22f, BlurMaskFilter.Blur.NORMAL)
            canvas.drawPath(hollow, paint)
            paint.maskFilter = null
        } else {
            wcBody(canvas, xs, ys, ps, n, width, 0.6f, seed + 17f, paint)
        }
        canvas.restoreToCount(hollowLayer)

        t.grain.alpha = 80
        canvas.drawRect(t.bounds, t.grain)
        canvas.restoreToCount(outerLayer)
    }

    private fun wcBody(canvas: Canvas, xs: FloatArray, ys: FloatArray, ps: FloatArray, n: Int, width: Float, k: Float, seed: Float, paint: Paint) {
        if (n == 1) {
            paint.style = Paint.Style.FILL
            canvas.drawCircle(xs[0], ys[0], widthAt(Tool.WATERCOLOR, width, ps[0]) * k / 2f, paint)
            paint.style = Paint.Style.STROKE
            return
        }
        var dist = 0f
        for (i in 1 until n) {
            val dx = xs[i] - xs[i - 1]
            val dy = ys[i] - ys[i - 1]
            dist += sqrt(dx * dx + dy * dy)
            val wobble = 1f + 0.22f * noise(seed + dist / (width * 0.9f))
            paint.strokeWidth = (widthAt(Tool.WATERCOLOR, width, (ps[i - 1] + ps[i]) / 2f) * k * wobble).coerceAtLeast(0.5f)
            canvas.drawLine(xs[i - 1], ys[i - 1], xs[i], ys[i], paint)
        }
    }

    private fun hash(i: Int): Float {
        var h = i * 374761393
        h = (h xor (h ushr 13)) * 1274126177
        h = h xor (h ushr 16)
        return (h and 0xFFFF) / 65535f * 2f - 1f
    }

    /** Derau halus -1..1 yang selalu sama untuk masukan yang sama, supaya gambar tidak berubah tiap digambar ulang. */
    private fun noise(x: Float): Float {
        val i = floor(x).toInt()
        val f = x - i
        val u = f * f * (3f - 2f * f)
        return hash(i) * (1f - u) + hash(i + 1) * u
    }

    fun withAlpha(color: Int, factor: Float): Int {
        val a = (Color.alpha(color) * factor).toInt().coerceIn(0, 255)
        return (a shl 24) or (color and 0x00FFFFFF)
    }

    fun isDark(color: Int): Boolean {
        val l = 0.2126 * Color.red(color) + 0.7152 * Color.green(color) + 0.0722 * Color.blue(color)
        return l < 110
    }

    /**
     * Nomor halaman catatan yang sedang dibuka: diisi penyunting (id halaman → teks nomor) bersama pengaturannya,
     * lalu ikut tergambar di kertas (layar, gambar kecil, dan ekspor).
     */
    object PageNumbers {
        @Volatile var labels: Map<String, String> = emptyMap()
        @Volatile var spec: id.hananote.data.PageNumberSpec? = null
    }

    private val numberPaint = ThreadLocal.withInitial { TextPaint(Paint.ANTI_ALIAS_FLAG) }

    private fun drawPageNumber(c: Canvas, page: PageMeta, paint: Paint, dark: Boolean) {
        val spec = PageNumbers.spec ?: return
        val label = PageNumbers.labels[page.id] ?: return
        val tp = numberPaint.get()!!
        tp.reset()
        tp.isAntiAlias = true
        tp.textSize = spec.size
        tp.color = if (dark) 0xCCFFFFFF.toInt() else 0xCC33415C.toInt()
        tp.typeface = android.graphics.Typeface.DEFAULT
        val w = tp.measureText(label)
        val margin = maxOf(18f, spec.size * 2.2f)
        val x = when (spec.position) {
            1, 3 -> page.width - margin - w
            2 -> margin
            else -> (page.width - w) / 2f
        }
        val y = when (spec.position) {
            3, 4 -> margin + spec.size * 0.4f
            else -> page.height - margin + spec.size * 0.35f
        }
        c.drawText(label, x, y, tp)
    }

    /** Kertas beserta latar (tekstur atau gambar) dan polanya: garis 8 mm, kotak 5 mm, atau titik 5 mm. */
    fun drawPaper(canvas: Canvas, page: PageMeta, paint: Paint, imageDir: File? = null, scale: Float = 1f) {
        paint.reset()
        paint.isAntiAlias = true
        paint.style = Paint.Style.FILL
        paint.color = page.paperColor
        canvas.drawRect(0f, 0f, page.width, page.height, paint)
        val tone = if (page.background.isEmpty()) page.paperColor
        else PaperBackgrounds.draw(canvas, page, paint, imageDir, scale)
        paint.reset()
        paint.isAntiAlias = true
        paint.style = Paint.Style.FILL

        val dark = isDark(tone)
        lightPaper.set(!dark)
        drawPageNumber(canvas, page, paint, dark)
        paint.reset()
        paint.isAntiAlias = true
        paint.style = Paint.Style.FILL
        val line = if (dark) 0x33FFFFFF else 0x2E33415C
        val mm = PaperSize.mmToPt(1f)
        when (page.template) {
            PaperTemplate.BLANK -> Unit
            PaperTemplate.LINED -> {
                // Jarak garis ini juga dipakai pengenal tulisan untuk menentukan baris (RuledLines).
                val gap = RuledLines.GAP_MM * mm
                paint.color = line
                paint.strokeWidth = 0.6f
                var y = gap * 3f
                while (y < page.height - gap * 0.5f) {
                    canvas.drawLine(0f, y, page.width, y, paint)
                    y += gap
                }
                // Garis margin merah seperti buku tulis.
                paint.color = if (dark) 0x55E57373 else 0x66D9534F
                paint.strokeWidth = 0.8f
                val mx = 25f * mm
                canvas.drawLine(mx, 0f, mx, page.height, paint)
            }
            PaperTemplate.GRID -> {
                val gap = 5f * mm
                paint.color = line
                paint.strokeWidth = 0.5f
                var x = gap
                while (x < page.width) { canvas.drawLine(x, 0f, x, page.height, paint); x += gap }
                var y = gap
                while (y < page.height) { canvas.drawLine(0f, y, page.width, y, paint); y += gap }
            }
            PaperTemplate.DOTS -> {
                val gap = 5f * mm
                paint.color = if (dark) 0x55FFFFFF else 0x5533415C
                var y = gap
                while (y < page.height) {
                    var x = gap
                    while (x < page.width) { canvas.drawCircle(x, y, 0.75f, paint); x += gap }
                    y += gap
                }
            }
            else -> PageTemplates.draw(canvas, page.template, page.width, page.height, dark)
        }
    }

    // ---------- teks ketik ----------

    fun layoutFor(t: TextItem): StaticLayout {
        t.layout?.let { return it }
        // Setiap teks punya cat sendiri supaya aman digambar dari dua jalur sekaligus.
        val p = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = t.size
            color = t.color
            typeface = FontRegistry.typeface(t.font)
        }
        val w = t.width.toInt().coerceAtLeast(8)
        val pitch = t.linePitch
        // Teks bergaris: baris kosong di akhir diberi huruf tak terlihat, supaya ikut tinggi baris tetap
        // (kursor di baris baru langsung duduk di garis, tidak meloncat saat huruf pertama diketik).
        val source = if (pitch > 0f && (t.text.isEmpty() || t.text.endsWith('\n'))) t.text + "\u200B" else t.text
        var body = styled(listText(source, p), t.runs)
        if (pitch > 0f) {
            val s = body as? SpannableString ?: SpannableString(body)
            s.setSpan(LinePitchSpan(pitch), 0, s.length, Spanned.SPAN_INCLUSIVE_INCLUSIVE)
            body = s
        }
        val l = aligned(StaticLayout.Builder.obtain(body, 0, body.length, p, w), t.align)
            .setLineSpacing(0f, if (pitch > 0f) 1f else 1.15f)
            .setIncludePad(false)
            .build()
        t.layout = l
        return l
    }

    /**
     * Tinggi baris tetap seperti garis kertas. Tinggi tiap baris dibulatkan bergantian supaya garis dasarnya tidak
     * bergeser menumpuk (garis kertas tidak jatuh di angka bulat), dan ruang lebihnya di atas huruf.
     */
    private class LinePitchSpan(private val pitch: Float) : LineHeightSpan {
        override fun chooseHeight(text: CharSequence?, start: Int, end: Int, spanstartv: Int, v: Int, fm: Paint.FontMetricsInt) {
            val rel = v - spanstartv
            val k = Math.round(rel / pitch)
            val h = (Math.round((k + 1) * pitch) - rel).coerceAtLeast(1)
            fm.ascent = fm.descent - h
            fm.top = fm.ascent
            fm.bottom = fm.descent
        }
    }

    /** Jarak baris untuk teks di kertas bergaris: satu jarak garis, atau kelipatannya bila hurufnya besar. */
    fun ruledPitch(size: Float, font: String, gap: Float): Float {
        val p = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { textSize = size; typeface = FontRegistry.typeface(font) }
        val fm = p.fontMetrics
        val natural = fm.descent - fm.ascent
        return gap * ceil(natural / gap * 0.97f).coerceAtLeast(1f)
    }

    /** Huruf tebal, miring, bergaris bawah, dan dicoret sesuai gaya per huruf teks. */
    fun styled(body: CharSequence, runs: TextRuns): CharSequence {
        if (runs.isEmpty || body.isEmpty()) return body
        val s = body as? SpannableString ?: SpannableString(body)
        for (i in 0 until runs.count) {
            val a = runs.start(i).coerceIn(0, s.length)
            val b = runs.end(i).coerceIn(0, s.length)
            if (a >= b) continue
            val f = runs.flags(i)
            val st = (if (f and TextRuns.BOLD != 0) Typeface.BOLD else 0) or (if (f and TextRuns.ITALIC != 0) Typeface.ITALIC else 0)
            if (st != 0) s.setSpan(StyleSpan(st), a, b, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            if (f and TextRuns.UNDERLINE != 0) s.setSpan(UnderlineSpan(), a, b, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            if (f and TextRuns.STRIKE != 0) s.setSpan(StrikethroughSpan(), a, b, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
        return s
    }

    /**
     * Baris daftar (• atau 1.) diberi indentasi gantung: bila butir panjang dan terlipat, baris
     * lanjutannya sejajar dengan isi butir, bukan dengan penandanya.
     */
    fun listText(text: String, paint: TextPaint): CharSequence {
        if (!TextLists.hasMarkers(text)) return text
        var out: SpannableString? = null
        var start = 0
        while (start < text.length) {
            val nl = text.indexOf('\n', start)
            val end = if (nl < 0) text.length else nl
            val prefix = TextLists.prefixOf(text.substring(start, end))
            if (prefix != null) {
                val s = out ?: SpannableString(text).also { out = it }
                val box = prefix == TextLists.BOX || prefix == TextLists.DONE
                val indent = if (box) {
                    // Kotak centang digambar sendiri supaya rapi di semua huruf.
                    val done = prefix == TextLists.DONE
                    s.setSpan(CheckboxSpan(done), start, start + 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                    if (done && end > start + prefix.length) {
                        s.setSpan(StrikethroughSpan(), start + prefix.length, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                        s.setSpan(ForegroundColorSpan(withAlpha(paint.color, 0.5f)), start + prefix.length, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                    }
                    ceil(CheckboxSpan.widthFor(paint.textSize) + paint.measureText(" ")).toInt()
                } else {
                    ceil(paint.measureText(prefix)).toInt()
                }
                s.setSpan(LeadingMarginSpan.Standard(0, indent), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            }
            start = end + 1
        }
        return out ?: text
    }

    /** Kotak centang yang digambar sebagai pengganti karakter ☐ / ☑. */
    class CheckboxSpan(private val done: Boolean) : ReplacementSpan() {
        override fun getSize(paint: Paint, text: CharSequence?, start: Int, end: Int, fm: Paint.FontMetricsInt?): Int {
            if (fm != null) paint.getFontMetricsInt(fm)
            return ceil(widthFor(paint.textSize)).toInt()
        }

        override fun draw(canvas: Canvas, text: CharSequence?, start: Int, end: Int, x: Float, top: Int, y: Int, bottom: Int, paint: Paint) {
            val size = paint.textSize * 0.78f
            val left = x + paint.textSize * 0.06f
            val b = y + paint.descent() * 0.15f
            val t = b - size
            val oldStyle = paint.style
            val oldWidth = paint.strokeWidth
            val oldColor = paint.color
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = (paint.textSize * 0.07f).coerceAtLeast(0.8f)
            val r = size * 0.18f
            canvas.drawRoundRect(left, t, left + size, b, r, r, paint)
            if (done) {
                paint.strokeWidth = (paint.textSize * 0.1f).coerceAtLeast(1f)
                paint.strokeCap = Paint.Cap.ROUND
                paint.strokeJoin = Paint.Join.ROUND
                val p = Path()
                p.moveTo(left + size * 0.2f, t + size * 0.52f)
                p.lineTo(left + size * 0.42f, t + size * 0.74f)
                p.lineTo(left + size * 0.82f, t + size * 0.26f)
                canvas.drawPath(p, paint)
            }
            paint.style = oldStyle
            paint.strokeWidth = oldWidth
            paint.color = oldColor
        }

        companion object {
            fun widthFor(textSize: Float) = textSize * 0.92f
        }
    }

    /** Terapkan perataan: kiri, tengah, kanan, atau kanan-kiri (baris terakhir paragraf tetap rata kiri). */
    fun aligned(b: StaticLayout.Builder, align: Align): StaticLayout.Builder {
        b.setAlignment(
            when (align) {
                Align.CENTER -> Layout.Alignment.ALIGN_CENTER
                Align.RIGHT -> Layout.Alignment.ALIGN_OPPOSITE
                else -> Layout.Alignment.ALIGN_NORMAL
            }
        )
        if (align == Align.JUSTIFY) b.setJustificationMode(Layout.JUSTIFICATION_MODE_INTER_WORD)
        return b
    }

    fun textBounds(t: TextItem, out: RectF) {
        out.set(t.x, t.y, t.x + t.width, t.y + layoutFor(t).height)
    }

    // ---------- isi stiker ----------

    private const val STICKY_PAD = 9f

    /** Tata letak teks di dalam stiker (sebelum diputar). */
    fun stickyLayout(item: StickyItem): StaticLayout {
        val tp = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = item.textSize
            color = 0xFF2A2A30.toInt()
            typeface = FontRegistry.typeface(item.font)
        }
        val lw = (item.w - STICKY_PAD * 2).toInt().coerceAtLeast(8)
        val body = listText(item.text, tp)
        return aligned(StaticLayout.Builder.obtain(body, 0, body.length, tp, lw), item.align)
            .setLineSpacing(0f, 1.1f)
            .setIncludePad(false)
            .build()
    }

    // ---------- teks di dalam sel tabel ----------

    private const val CELL_INK = 0xFF23242B.toInt()

    /** Jarak teks dari garis sel. */
    fun cellPad(t: TableItem) = max(2.5f, t.textSize * 0.4f)

    /** Tata letak teks satu sel: mengalir sesuai lebar kolomnya; baris judul ditebalkan. */
    fun cellLayout(t: TableItem, r: Int, c: Int): StaticLayout {
        val pad = cellPad(t)
        val bold = t.header && r == 0
        val tp = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = t.textSize
            color = CELL_INK
            val face = FontRegistry.typeface(t.font)
            typeface = if (bold) Typeface.create(face, Typeface.BOLD) else face
        }
        val w = (t.colEdge(c + 1) - t.colEdge(c) - pad * 2).toInt().coerceAtLeast(4)
        val body = listText(t.cell(r, c), tp)
        return aligned(StaticLayout.Builder.obtain(body, 0, body.length, tp, w), t.align)
            .setLineSpacing(0f, 1.1f)
            .setIncludePad(false)
            .build()
    }

    fun cellTextLeft(t: TableItem, c: Int) = t.x + t.colEdge(c) + cellPad(t)

    /** Teks sel di tengah tinggi barisnya; bila tidak muat, mulai dari atas. */
    fun cellTextTop(t: TableItem, r: Int, l: Layout): Float {
        val top = t.y + t.rowEdge(r)
        val rh = t.rowEdge(r + 1) - t.rowEdge(r)
        val pad = cellPad(t)
        val free = rh - l.height
        return top + if (free > pad * 2) free / 2f else pad
    }

    /** Baris yang teksnya tidak muat dipertinggi (tidak pernah diperpendek), seperti tabel di pengolah kata. */
    fun fitTable(t: TableItem): TableItem {
        if (!t.hasText) return t
        val heights = FloatArray(t.rows) { t.rowEdge(it + 1) - t.rowEdge(it) }
        val pad = cellPad(t)
        var changed = false
        for (r in 0 until t.rows) for (c in 0 until t.cols) {
            if (t.cell(r, c).isEmpty()) continue
            val need = cellLayout(t, r, c).height + pad * 2
            if (need > heights[r] + 0.5f) {
                heights[r] = need
                changed = true
            }
        }
        return if (changed) t.withRowHeights(heights) else t
    }

    private fun drawCells(c: Canvas, t: TableItem) {
        if (!t.hasText) return
        for (r in 0 until t.rows) for (k in 0 until t.cols) {
            if (t.cell(r, k).isEmpty()) continue
            val l = cellLayout(t, r, k)
            c.save()
            c.clipRect(t.x + t.colEdge(k), t.y + t.rowEdge(r), t.x + t.colEdge(k + 1), t.y + t.rowEdge(r + 1))
            c.translate(cellTextLeft(t, k), cellTextTop(t, r, l))
            l.draw(c)
            c.restore()
        }
    }

    fun stickyTextLeft(item: StickyItem) = item.x + STICKY_PAD
    fun stickyTextTop(item: StickyItem) = item.y + item.h * 0.12f + STICKY_PAD * 0.7f

    fun drawText(canvas: Canvas, t: TextItem) {
        val l = layoutFor(t)
        canvas.save()
        canvas.translate(t.x, t.y)
        l.draw(canvas)
        canvas.restore()
    }

    // ---------- benda tempelan ----------

    private val LINK_BLUE = 0xFF2F43B5.toInt()

    /** Kotak batas benda setelah diputar. */
    fun itemBounds(item: PageItem, out: RectF) {
        if (item.rotation == 0f) {
            out.set(item.x, item.y, item.x + item.w, item.y + item.h)
            return
        }
        val rad = Math.toRadians(item.rotation.toDouble())
        val c = abs(cos(rad)).toFloat()
        val s = abs(sin(rad)).toFloat()
        val hw = (item.w * c + item.h * s) / 2f
        val hh = (item.w * s + item.h * c) / 2f
        out.set(item.cx - hw, item.cy - hh, item.cx + hw, item.cy + hh)
    }

    fun drawItem(c: Canvas, item: PageItem, paint: Paint, imageDir: File?, scale: Float) {
        if (item is ImageItem && item.shadow != null) Shadows.draw(c, item, imageDir, paint)
        if (item.rotation != 0f) {
            c.save()
            c.rotate(item.rotation, item.cx, item.cy)
            drawItemUpright(c, item, paint, imageDir, scale)
            c.restore()
        } else {
            drawItemUpright(c, item, paint, imageDir, scale)
        }
    }

    private fun drawItemUpright(c: Canvas, item: PageItem, paint: Paint, imageDir: File?, scale: Float) {
        paint.reset()
        paint.isAntiAlias = true
        val l = item.x
        val t = item.y
        val r = item.x + item.w
        val b = item.y + item.h
        when (item) {
            is StickyItem -> {
                paint.style = Paint.Style.FILL
                paint.color = 0x24000000
                c.drawRoundRect(l + 1.2f, t + 2.4f, r + 1.2f, b + 2.4f, 3f, 3f, paint)
                paint.color = item.color
                c.drawRoundRect(l, t, r, b, 3f, 3f, paint)
                // Pita perekat di bagian atas.
                paint.color = 0x14000000
                c.drawRect(l, t, r, t + item.h * 0.12f, paint)
                if (item.text.isNotEmpty()) {
                    val layout = stickyLayout(item)
                    c.save()
                    c.clipRect(l, t, r, b)
                    c.translate(stickyTextLeft(item), stickyTextTop(item))
                    layout.draw(c)
                    c.restore()
                }
            }
            is CalendarItem -> drawCalendar(c, item, paint)
            is TapeItem -> drawTape(c, item, paint)
            is CoverItem -> {
                paint.style = Paint.Style.FILL
                paint.color = item.color
                paint.isAntiAlias = false
                c.drawRect(l, t, r, b, paint)
                paint.isAntiAlias = true
            }
            is MindMapItem -> MindMapRenderer.drawItem(c, item)
            is AudioItem -> drawAudio(c, item, paint, playing = false)
            is TableItem -> {
                if (item.header) {
                    paint.style = Paint.Style.FILL
                    paint.color = withAlpha(item.color, 0.10f)
                    c.drawRect(l, t, r, t + item.rowEdge(1), paint)
                }
                paint.style = Paint.Style.STROKE
                paint.color = item.color
                paint.strokeWidth = 1.2f
                c.drawRect(l, t, r, b, paint)
                paint.strokeWidth = 0.8f
                for (k in 1 until item.rows) { val yy = t + item.rowEdge(k); c.drawLine(l, yy, r, yy, paint) }
                for (k in 1 until item.cols) { val xx = l + item.colEdge(k); c.drawLine(xx, t, xx, b, paint) }
                drawCells(c, item)
            }
            is LinkItem -> {
                val radius = item.h / 2f
                paint.style = Paint.Style.FILL
                paint.color = withAlpha(LINK_BLUE, 0.10f)
                c.drawRoundRect(l, t, r, b, radius, radius, paint)
                paint.style = Paint.Style.STROKE
                paint.strokeWidth = 0.8f
                paint.color = withAlpha(LINK_BLUE, 0.45f)
                c.drawRoundRect(l, t, r, b, radius, radius, paint)
                val tp = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                    textSize = item.h * 0.46f
                    color = LINK_BLUE
                    isUnderlineText = true
                }
                val pad = item.h * 0.45f
                val shown = TextUtils.ellipsize("🔗 " + item.label, tp, item.w - pad * 2, TextUtils.TruncateAt.END)
                val baseline = t + item.h / 2f - (tp.descent() + tp.ascent()) / 2f
                c.drawText(shown, 0, shown.length, l + pad, baseline, tp)
            }
            is ImageItem -> {
                val crop = item.crop
                val bucket = imageBucket(item, scale)
                val bmp = imageDir?.let { ImageCache.get(File(it, item.file), bucket) }
                val dst = RectF(l, t, r, b)
                if (bmp != null) {
                    paint.isFilterBitmap = true
                    val flipped = item.flipH || item.flipV
                    if (flipped) {
                        c.save()
                        c.scale(if (item.flipH) -1f else 1f, if (item.flipV) -1f else 1f, item.cx, item.cy)
                    }
                    if (crop.isFull) {
                        c.drawBitmap(bmp, null, dst, paint)
                    } else {
                        val sl = (crop.l * bmp.width).toInt()
                        val st = (crop.t * bmp.height).toInt()
                        val src = Rect(
                            sl, st,
                            (crop.r * bmp.width).toInt().coerceAtLeast(sl + 1),
                            (crop.b * bmp.height).toInt().coerceAtLeast(st + 1),
                        )
                        c.drawBitmap(bmp, src, dst, paint)
                    }
                    if (flipped) c.restore()
                } else {
                    paint.style = Paint.Style.FILL
                    paint.color = 0x1F7A7F8F
                    c.drawRect(dst, paint)
                }
            }
        }
    }


    private val SUNDAY_RED = 0xFFC62828.toInt()

    /** Kalender satu bulan seperti kartu kalender cetak, Senin di kiri dan Minggu berwarna merah. */
    private val tapePath = ThreadLocal.withInitial { Path() }

    /**
     * Selotip hafalan (belum diputar). Tertutup: warna pekat menutupi isi di bawahnya, dengan tepi sedikit lebih
     * gelap seperti pita kertas. Terbuka: sisa warna tipis dan garis tepi samar, supaya isinya terbaca tetapi
     * letak selotipnya tetap kelihatan. [alpha] untuk pratinjau saat sedang ditarik.
     */
    fun drawTape(c: Canvas, tp: TapeItem, paint: Paint, alpha: Float = 1f) {
        val path = tapePath.get() ?: Path()
        Tapes.outline(tp, path)
        val dark = darken(tp.color, 0.82f)
        paint.isAntiAlias = true
        paint.pathEffect = null
        if (tp.shape == TapeShape.FREE) {
            paint.style = Paint.Style.STROKE
            paint.strokeCap = Paint.Cap.ROUND
            paint.strokeJoin = Paint.Join.ROUND
            paint.strokeWidth = max(tp.thick, 1f)
            paint.color = withAlpha(tp.color, (if (tp.covered) 1f else 0.16f) * alpha)
            c.drawPath(path, paint)
            paint.strokeCap = Paint.Cap.BUTT
            paint.strokeJoin = Paint.Join.MITER
            return
        }
        paint.style = Paint.Style.FILL
        paint.color = withAlpha(tp.color, (if (tp.covered) 1f else 0.16f) * alpha)
        c.drawPath(path, paint)
        paint.style = Paint.Style.STROKE
        paint.strokeJoin = Paint.Join.ROUND
        paint.strokeWidth = if (tp.covered) 0.7f else 0.8f
        paint.color = withAlpha(dark, (if (tp.covered) 0.9f else 0.45f) * alpha)
        c.drawPath(path, paint)
        paint.strokeJoin = Paint.Join.MITER
    }

    private fun darken(color: Int, k: Float): Int {
        val r = ((color shr 16 and 0xFF) * k).toInt()
        val g = ((color shr 8 and 0xFF) * k).toInt()
        val b = ((color and 0xFF) * k).toInt()
        return (color and 0xFF000000.toInt()) or (r shl 16) or (g shl 8) or b
    }

    private fun drawCalendar(c: Canvas, item: CalendarItem, paint: Paint) {
        val l = item.x
        val t = item.y
        val w = item.w
        val h = item.h
        val first = runCatching { LocalDate.of(item.year, item.month.coerceIn(1, 12), 1) }.getOrNull() ?: return
        val offset = first.dayOfWeek.value - 1
        val days = first.lengthOfMonth()
        val rows = ceil((offset + days) / 7f).toInt().coerceAtLeast(4)
        val headH = h * 0.15f
        val dayH = h * 0.085f
        val gridTop = t + headH + dayH
        val cellW = w / 7f
        val cellH = (t + h - gridTop) / rows

        paint.style = Paint.Style.FILL
        paint.color = 0x22000000
        c.drawRoundRect(l + 1f, t + 2f, l + w + 1f, t + h + 2f, 4f, 4f, paint)
        paint.color = 0xFFFFFDF8.toInt()
        c.drawRoundRect(l, t, l + w, t + h, 4f, 4f, paint)
        paint.color = withAlpha(item.color, 0.16f)
        c.drawRect(l, t, l + w, t + headH, paint)

        val tp = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = item.color
            textSize = headH * 0.5f
            typeface = Typeface.create(Typeface.SERIF, Typeface.BOLD)
            textAlign = Paint.Align.CENTER
        }
        val title = IndoDate.months[item.month.coerceIn(1, 12) - 1] + " " + item.year
        c.drawText(title, l + w / 2f, t + headH / 2f - (tp.descent() + tp.ascent()) / 2f, tp)

        tp.typeface = Typeface.DEFAULT_BOLD
        tp.textSize = min(dayH * 0.55f, cellW * 0.32f)
        for (k in 0..6) {
            tp.color = if (k == 6) SUNDAY_RED else withAlpha(item.color, 0.9f)
            c.drawText(IndoDate.daysShort[k], l + cellW * (k + 0.5f), t + headH + dayH / 2f - (tp.descent() + tp.ascent()) / 2f, tp)
        }

        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 0.6f
        paint.color = withAlpha(item.color, 0.4f)
        for (r in 0 until rows) c.drawLine(l, gridTop + r * cellH, l + w, gridTop + r * cellH, paint)
        for (k in 1..6) c.drawLine(l + k * cellW, gridTop, l + k * cellW, t + h, paint)
        paint.strokeWidth = 1.1f
        paint.color = withAlpha(item.color, 0.7f)
        c.drawRoundRect(l, t, l + w, t + h, 4f, 4f, paint)

        tp.typeface = Typeface.DEFAULT
        tp.textAlign = Paint.Align.LEFT
        tp.textSize = min(cellH * 0.26f, cellW * 0.28f)
        for (d in 1..days) {
            val idx = offset + d - 1
            val r = idx / 7
            val k = idx % 7
            val tx = l + k * cellW + cellW * 0.1f
            val ty = gridTop + r * cellH + tp.textSize * 1.15f
            val label = d.toString()
            if (d == item.mark) {
                paint.style = Paint.Style.FILL
                paint.color = item.color
                c.drawCircle(tx + tp.measureText(label) / 2f, ty - tp.textSize * 0.36f, tp.textSize * 0.82f, paint)
                tp.color = 0xFFFFFFFF.toInt()
            } else {
                tp.color = if (k == 6) SUNDAY_RED else 0xFF2A2D35.toInt()
            }
            c.drawText(label, tx, ty, tp)
        }
    }

    private val AUDIO_COLOR = 0xFFC2410C.toInt()

    /** Lama rekaman sebagai m:ss. */
    fun durationLabel(ms: Long): String {
        val s = (ms / 1000).coerceAtLeast(0)
        return "%d:%02d".format(s / 60, s % 60)
    }

    /**
     * Rekaman suara: pil dengan tombol putar bulat, nama, dan lamanya.
     * Saat diputar, [progress] 0..1 mengisi pil dan tombolnya berubah jadi jeda.
     */
    fun drawAudio(c: Canvas, item: AudioItem, paint: Paint, playing: Boolean, progress: Float = 0f) {
        val l = item.x
        val t = item.y
        val r = item.x + item.w
        val b = item.y + item.h
        val rad = item.h / 2f
        paint.reset()
        paint.isAntiAlias = true
        paint.style = Paint.Style.FILL
        paint.color = 0xFFFFFFFF.toInt()
        c.drawRoundRect(l, t, r, b, rad, rad, paint)
        paint.color = withAlpha(AUDIO_COLOR, 0.10f)
        c.drawRoundRect(l, t, r, b, rad, rad, paint)
        if (playing && progress > 0f) {
            c.save()
            c.clipRect(l, t, l + item.w * progress.coerceIn(0f, 1f), b)
            paint.color = withAlpha(AUDIO_COLOR, 0.22f)
            c.drawRoundRect(l, t, r, b, rad, rad, paint)
            c.restore()
        }
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 1f
        paint.color = withAlpha(AUDIO_COLOR, 0.55f)
        c.drawRoundRect(l, t, r, b, rad, rad, paint)
        // Tombol bulat.
        val cr = item.h * 0.36f
        val cx = l + rad
        val cy = t + item.h / 2f
        paint.style = Paint.Style.FILL
        paint.color = AUDIO_COLOR
        c.drawCircle(cx, cy, cr, paint)
        paint.color = 0xFFFFFFFF.toInt()
        if (playing) {
            val bw = cr * 0.26f
            c.drawRect(cx - bw * 1.6f, cy - cr * 0.42f, cx - bw * 0.4f, cy + cr * 0.42f, paint)
            c.drawRect(cx + bw * 0.4f, cy - cr * 0.42f, cx + bw * 1.6f, cy + cr * 0.42f, paint)
        } else {
            val p = Path()
            p.moveTo(cx - cr * 0.3f, cy - cr * 0.45f)
            p.lineTo(cx + cr * 0.5f, cy)
            p.lineTo(cx - cr * 0.3f, cy + cr * 0.45f)
            p.close()
            c.drawPath(p, paint)
        }
        val tp = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = item.h * 0.36f
            color = 0xFF3A2A22.toInt()
            typeface = Typeface.DEFAULT_BOLD
        }
        val tx = cx + cr + item.h * 0.28f
        val base = cy - (tp.descent() + tp.ascent()) / 2f
        val dur = durationLabel(item.durationMs)
        val durW = tp.measureText(dur)
        val name = TextUtils.ellipsize("Rekaman suara", tp, (r - rad * 0.6f - durW - item.h * 0.3f) - tx, TextUtils.TruncateAt.END)
        c.drawText(name, 0, name.length, tx, base, tp)
        tp.typeface = Typeface.DEFAULT
        tp.color = withAlpha(0xFF3A2A22.toInt(), 0.7f)
        c.drawText(dur, r - rad * 0.6f - durW, base, tp)
    }

    /** Sampul kecil halaman pertama untuk ditampilkan di rak. */
    fun renderThumbnail(page: PageMeta, content: PageContent, targetWidth: Int, imageDir: File?): Bitmap {
        val scale = targetWidth / page.width
        val h = (page.height * scale).toInt().coerceAtLeast(1)
        val bmp = Bitmap.createBitmap(targetWidth, h, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        c.scale(scale, scale)
        drawPage(c, page, content, imageDir, scale)
        return bmp
    }

    /**
     * Gambar satu halaman utuh (kertas, benda, stabilo/cat air, teks, tulisan) dalam satuan halaman.
     * [scale] dipakai untuk memilih resolusi gambar sisipan.
     */
    fun drawPage(c: Canvas, page: PageMeta, content: PageContent, imageDir: File?, scale: Float) {
        val paint = Paint()
        val path = Path()
        drawPaper(c, page, paint, imageDir, scale)
        c.save()
        c.clipRect(0f, 0f, page.width, page.height)
        drawLayers(c, content.strokes, content.texts, content.items, imageDir, scale, null, paint, Paint(), path, null)
        c.restore()
    }

    private val layerBox = ThreadLocal.withInitial { RectF() }

    /** Sapuan penghapus di atas gambar yang belum disimpan ke berkas gambarnya (satuan halaman). */
    class EraseMask(val path: Path, val width: Float)

    private val maskPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
        xfermode = PorterDuffXfermode(PorterDuff.Mode.CLEAR)
    }

    /**
     * Gambar benda tempelan, teks ketik, dan coretan menurut urutan lapisannya (z): yang lebih besar
     * di depan. Hanya yang bersinggungan dengan [vis] yang digambar (null: semua).
     */
    fun drawLayers(
        c: Canvas,
        ss: List<Stroke>,
        ts: List<TextItem>,
        its: List<PageItem>,
        imageDir: File?,
        scale: Float,
        vis: RectF?,
        itemPaint: Paint,
        strokePaint: Paint,
        path: Path,
        skipTexts: Set<TextItem>?,
        masks: Map<PageItem, List<EraseMask>>? = null,
    ) {
        val n = ss.size + ts.size + its.size
        if (n == 0) return
        val box = layerBox.get() ?: RectF()
        // Kuas penghapus lembut memudarkan isi di bawahnya, bukan kertasnya: isi digambar di lapisan sendiri.
        val soft = vectorSafe.get() != true && ss.any { it.tool == Tool.ERASER }
        val layer = if (soft) c.saveLayer(null, null) else -1
        val all = ArrayList<Any>(n)
        all.addAll(its)
        all.addAll(ts)
        all.addAll(ss)
        // Urutan stabil: bila z sama, benda lalu teks lalu coretan seperti tampilan lama.
        all.sortWith(compareBy { layerZ(it) })
        // Bekas kuas penghapus lembut tidak bisa ditulis sebagai vektor PDF (halaman seperti itu dijadikan gambar).
        val useMasks = vectorSafe.get() != true
        for (o in all) {
            when (o) {
                is PageItem -> {
                    // Selotip hafalan selalu di atas semua isi halaman, digambar paling akhir.
                    if (o is TapeItem) continue
                    itemBounds(o, box)
                    if (vis == null || RectF.intersects(box, vis)) {
                        val m = masks?.get(o)
                        val soft = if (useMasks) o.masks else null
                        if (m.isNullOrEmpty() && soft.isNullOrEmpty()) {
                            drawItem(c, o, itemPaint, imageDir, scale)
                        } else {
                            // Bagian yang tersapu penghapus dikosongkan, hanya pada benda ini.
                            val pad = 4f + ((o as? ImageItem)?.shadow?.let { it.distance + it.blur * 3f } ?: 0f)
                            val save = c.saveLayer(RectF(box).apply { inset(-pad, -pad) }, null)
                            drawItem(c, o, itemPaint, imageDir, scale)
                            if (m != null) for (k in m) {
                                maskPaint.strokeWidth = k.width
                                c.drawPath(k.path, maskPaint)
                            }
                            if (!soft.isNullOrEmpty()) drawMasks(c, SoftMasks.itemPageMasks(o), strokePaint)
                            c.restoreToCount(save)
                        }
                    }
                }
                is TextItem -> {
                    if (skipTexts != null && o in skipTexts) continue
                    textBounds(o, box)
                    if (vis == null || RectF.intersects(box, vis)) {
                        if (!useMasks || o.masks.isNullOrEmpty()) {
                            drawText(c, o)
                        } else {
                            val save = c.saveLayer(RectF(box).apply { inset(-4f, -4f) }, null)
                            drawText(c, o)
                            drawMasks(c, SoftMasks.textPageMasks(o), strokePaint)
                            c.restoreToCount(save)
                        }
                    }
                }
                is Stroke -> if (vis == null || RectF.intersects(o.bounds, vis)) {
                    val soft = if (useMasks) o.masks else null
                    if (soft.isNullOrEmpty()) {
                        drawStroke(c, o, strokePaint, path)
                    } else {
                        // Stabilo dan cat air yang hanya menggelapkan tetap menggelapkan setelah dihapus sebagian.
                        val pad = o.width * 2f + 4f
                        val blend = if (darkens() && (o.tool == Tool.HIGHLIGHTER || o.tool == Tool.WATERCOLOR)) layerDarken else null
                        val save = c.saveLayer(RectF(o.bounds).apply { inset(-pad, -pad) }, blend)
                        drawStroke(c, o, strokePaint, path)
                        drawMasks(c, soft, strokePaint)
                        c.restoreToCount(save)
                    }
                }
            }
        }
        if (layer >= 0) c.restoreToCount(layer)
        for (o in its) {
            if (o !is TapeItem) continue
            itemBounds(o, box)
            if (vis == null || RectF.intersects(box, vis)) drawItem(c, o, itemPaint, imageDir, scale)
        }
    }

    /**
     * Mode gambar untuk PDF: efek yang memakai pencampuran warna khusus (tepi dan butiran cat air)
     * disederhanakan, karena tidak semua pembaca PDF menampilkannya dengan benar.
     */
    private val vectorSafe = ThreadLocal<Boolean>()

    /** Sedang menggambar untuk berkas PDF (gambar latar memakai resolusi sedang supaya berkas tidak terlalu besar). */
    fun drawingPdf(): Boolean = vectorSafe.get() == true

    fun <T> forPdf(block: () -> T): T {
        vectorSafe.set(true)
        try {
            return block()
        } finally {
            vectorSafe.set(false)
        }
    }
}
