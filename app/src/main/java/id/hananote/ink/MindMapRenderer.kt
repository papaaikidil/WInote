package id.hananote.ink

import android.graphics.Canvas
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Typeface
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.util.LruCache
import id.hananote.data.MindMapItem
import id.hananote.data.MindMaps
import id.hananote.data.MindNode
import kotlin.math.ceil
import kotlin.math.min

/**
 * Menggambar mind map: ide utama berbentuk pil berwarna, cabang utama berbentuk kotak lembut,
 * ide yang lebih dalam bergaris bawah, dan garis lengkung berwarna yang menyambungkannya.
 * Semua dalam satuan peta; di halaman diskalakan supaya tetap tajam.
 */
object MindMapRenderer {

    private class Style(val textSize: Float, val bold: Boolean, val maxW: Float, val padX: Float, val padY: Float, val minW: Float)

    private val styles = listOf(
        Style(20f, true, 220f, 18f, 11f, 96f),
        Style(15f, false, 190f, 13f, 8f, 72f),
        Style(13f, false, 180f, 6f, 5f, 56f),
    )

    private fun style(depth: Int) = styles[depth.coerceIn(0, 2)]

    private const val DARK = 0xFF262A33.toInt()
    private const val PLACEHOLDER = 0x99808590.toInt()

    private val layouts = LruCache<String, StaticLayout>(500)

    private fun layout(text: String, depth: Int, placeholder: Boolean): StaticLayout {
        val d = depth.coerceIn(0, 2)
        val shown = if (text.isBlank()) (if (placeholder) "…" else " ") else text
        val key = "$d|$placeholder|$shown"
        layouts.get(key)?.let { return it }
        val st = style(d)
        val p = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = st.textSize
            typeface = if (st.bold) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
            color = when {
                text.isBlank() -> PLACEHOLDER
                d == 0 -> 0xFFFFFFFF.toInt()
                else -> DARK
            }
        }
        val l = StaticLayout.Builder.obtain(shown, 0, shown.length, p, st.maxW.toInt())
            .setAlignment(Layout.Alignment.ALIGN_CENTER)
            .setIncludePad(false)
            .setLineSpacing(0f, 1.08f)
            .setMaxLines(6)
            .build()
        layouts.put(key, l)
        return l
    }

    /** Tata letak teks sebuah ide seperti yang digambar di halaman (tanpa tanda "…"). */
    fun textLayout(text: String, depth: Int): StaticLayout = layout(text, depth, placeholder = false)

    /** Titik kiri atas tata letak teks ide [n] (satuan peta). */
    fun textOrigin(n: MindNode, depth: Int, lay: StaticLayout): FloatArray {
        val d = depth.coerceIn(0, 2)
        val st = style(d)
        return floatArrayOf(n.x - st.maxW / 2f, n.y - lay.height / 2f - if (d >= 2) 1.5f else 0f)
    }

    /** Tanda "…" pada ide yang belum diisi, saat mind map disunting di halaman. */
    fun drawPlaceholder(c: Canvas, n: MindNode, depth: Int) {
        val lay = layout("", depth, placeholder = true)
        val o = textOrigin(n, depth, lay)
        c.save()
        c.translate(o[0], o[1])
        lay.draw(c)
        c.restore()
    }

    private fun textWidth(l: StaticLayout): Float {
        var w = 0f
        for (i in 0 until l.lineCount) w = maxOf(w, l.getLineWidth(i))
        return ceil(w)
    }

    /** Lebar dan tinggi kotak sebuah ide. */
    fun size(n: MindNode, depth: Int): FloatArray {
        val st = style(depth)
        val l = layout(n.text, depth, placeholder = false)
        val w = maxOf(st.minW, textWidth(l) + st.padX * 2f)
        val h = l.height + st.padY * 2f
        return floatArrayOf(w, h)
    }

    val sizeOf = MindMaps.SizeOf { n, d -> size(n, d) }

    /** Ide di titik ini (satuan peta), atau -1. Ide yang digambar terakhir (paling atas) didahulukan. */
    fun hit(nodes: List<MindNode>, x: Float, y: Float, slop: Float): Int {
        val depths = MindMaps.depths(nodes)
        for (n in nodes.asReversed()) {
            val s = size(n, depths[n.id] ?: 0)
            if (x >= n.x - s[0] / 2f - slop && x <= n.x + s[0] / 2f + slop &&
                y >= n.y - s[1] / 2f - slop && y <= n.y + s[1] / 2f + slop
            ) return n.id
        }
        return -1
    }

    private fun anchorY(n: MindNode, depth: Int, h: Float) = if (depth >= 2) n.y + h / 2f - 1f else n.y

    /**
     * Gambar mind map. [selected] diberi bingkai putus-putus berwarna [accent];
     * [editing] menampilkan "…" pada ide yang belum diisi.
     */
    fun draw(c: Canvas, nodes: List<MindNode>, selected: Int = -1, accent: Int = 0, editing: Boolean = false) {
        if (nodes.isEmpty()) return
        val depths = MindMaps.depths(nodes)
        val byId = nodes.associateBy { it.id }
        val sizes = HashMap<Int, FloatArray>(nodes.size * 2)
        for (n in nodes) sizes[n.id] = size(n, depths[n.id] ?: 0)

        val line = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeCap = Paint.Cap.ROUND
        }
        val path = Path()
        for (n in nodes) {
            val p = byId[n.parent] ?: continue
            val d = depths[n.id] ?: 1
            val pd = depths[p.id] ?: 0
            val ps = sizes[p.id]!!
            val ns = sizes[n.id]!!
            val dir = if (n.x >= p.x) 1f else -1f
            val sx = p.x + dir * ps[0] / 2f
            val sy = anchorY(p, pd, ps[1])
            val ex = n.x - dir * ns[0] / 2f
            val ey = anchorY(n, d, ns[1])
            val dx = (ex - sx) * 0.5f
            path.rewind()
            path.moveTo(sx, sy)
            path.cubicTo(sx + dx, sy, ex - dx, ey, ex, ey)
            line.color = InkRenderer.withAlpha(n.color, 0.9f)
            line.strokeWidth = when (d) { 1 -> 3.4f; 2 -> 2.2f; else -> 1.6f }
            c.drawPath(path, line)
        }

        val fill = Paint(Paint.ANTI_ALIAS_FLAG)
        for (n in nodes) {
            val d = depths[n.id] ?: 0
            val s = sizes[n.id]!!
            val l = n.x - s[0] / 2f
            val t = n.y - s[1] / 2f
            val r = n.x + s[0] / 2f
            val b = n.y + s[1] / 2f
            when {
                d == 0 -> {
                    fill.style = Paint.Style.FILL
                    fill.color = 0x26000000
                    c.drawRoundRect(l + 1f, t + 2f, r + 1f, b + 2f, s[1] / 2f, s[1] / 2f, fill)
                    fill.color = n.color
                    c.drawRoundRect(l, t, r, b, s[1] / 2f, s[1] / 2f, fill)
                }
                d == 1 -> {
                    fill.style = Paint.Style.FILL
                    fill.color = 0xFFFFFFFF.toInt()
                    c.drawRoundRect(l, t, r, b, 10f, 10f, fill)
                    fill.color = InkRenderer.withAlpha(n.color, 0.14f)
                    c.drawRoundRect(l, t, r, b, 10f, 10f, fill)
                    fill.style = Paint.Style.STROKE
                    fill.strokeWidth = 1.6f
                    fill.color = n.color
                    c.drawRoundRect(l, t, r, b, 10f, 10f, fill)
                }
                else -> {
                    fill.style = Paint.Style.STROKE
                    fill.strokeWidth = 1.8f
                    fill.strokeCap = Paint.Cap.ROUND
                    fill.color = n.color
                    c.drawLine(l + 1f, b - 1f, r - 1f, b - 1f, fill)
                }
            }
            if (n.text.isNotBlank() || editing) {
                val lay = layout(n.text, d, placeholder = editing)
                val st = style(d)
                c.save()
                c.translate(n.x - st.maxW / 2f, n.y - lay.height / 2f - if (d >= 2) 1.5f else 0f)
                lay.draw(c)
                c.restore()
            }
            if (n.id == selected) {
                fill.style = Paint.Style.STROKE
                fill.strokeWidth = 2f
                fill.color = accent
                fill.pathEffect = DashPathEffect(floatArrayOf(6f, 4f), 0f)
                c.drawRoundRect(l - 5f, t - 5f, r + 5f, b + 5f, 12f, 12f, fill)
                fill.pathEffect = null
            }
        }
    }

    /** Gambar mind map di halaman, memenuhi kotak bendanya. */
    fun drawItem(c: Canvas, item: MindMapItem) {
        if (item.mapW <= 0f || item.mapH <= 0f) return
        val s = min(item.w / item.mapW, item.h / item.mapH)
        c.save()
        c.translate(item.x, item.y)
        c.scale(s, s)
        c.translate(-item.mapL, -item.mapT)
        draw(c, item.nodes)
        c.restore()
    }

    /** Benda mind map dari daftar ide, dengan skala tampilan [scale] (poin halaman per satuan peta). */
    fun toItem(nodes: List<MindNode>, x: Float, y: Float, scale: Float, tidy: Boolean): MindMapItem {
        val b = MindMaps.bounds(nodes, sizeOf)
        val mw = (b[2] - b[0]).coerceAtLeast(1f)
        val mh = (b[3] - b[1]).coerceAtLeast(1f)
        return MindMapItem(x, y, mw * scale, mh * scale, nodes, b[0], b[1], mw, mh, tidy)
    }
}
