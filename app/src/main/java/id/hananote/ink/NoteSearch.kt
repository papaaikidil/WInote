package id.hananote.ink

import android.graphics.RectF
import id.hananote.data.CalendarItem
import id.hananote.data.IndoDate
import id.hananote.data.LinkItem
import id.hananote.data.MindMapItem
import id.hananote.data.PageContent
import id.hananote.data.StickyItem
import id.hananote.data.TableItem
import id.hananote.data.TextItem

/** Satu kata yang ketemu saat mencari di dalam catatan: halaman ke-[pageIndex], kotaknya dalam satuan halaman. */
class NoteHit(val pageIndex: Int, val pageId: String, val rect: RectF, val snippet: String)

/**
 * Mencari kata di dalam satu halaman: teks ketik (tepat per kata), isi stiker/tabel/mind map/link (kotak bendanya),
 * tulisan tangan yang sudah terbaca (perkiraan letak di barisnya), dan teks asli PDF (tepat per huruf).
 */
object NoteSearch {

    /** Semua posisi [q] di [text], tanpa membedakan huruf besar-kecil. */
    fun occurrences(text: String, q: String): List<Int> {
        if (q.isEmpty() || text.length < q.length) return emptyList()
        val out = ArrayList<Int>()
        var at = text.indexOf(q, 0, ignoreCase = true)
        while (at >= 0) {
            out.add(at)
            at = text.indexOf(q, at + q.length, ignoreCase = true)
        }
        return out
    }

    /** Cuplikan teks di sekitar kata yang ketemu, untuk daftar hasil. */
    fun snippet(text: String, at: Int, len: Int): String {
        val from = (at - 30).coerceAtLeast(0)
        val to = (at + len + 40).coerceAtMost(text.length)
        var s = text.substring(from, to).replace('\n', ' ').trim()
        if (from > 0) s = "…$s"
        if (to < text.length) s = "$s…"
        return s
    }

    /** Teks ketik dan benda berisi teks di [content]. */
    fun inContent(content: PageContent, q: String, page: Int, pageId: String, out: MutableList<NoteHit>) {
        for (t in content.texts) inText(t, q, page, pageId, out)
        val box = RectF()
        for (item in content.items) {
            val text = when (item) {
                is StickyItem -> item.text
                is LinkItem -> item.label + " " + item.url
                is CalendarItem -> IndoDate.months[(item.month - 1).coerceIn(0, 11)] + " " + item.year
                is MindMapItem -> item.outline().joinToString("\n")
                is TableItem -> if (item.hasText) {
                    (0 until item.rows).joinToString("\n") { r -> (0 until item.cols).joinToString(" | ") { c -> item.cell(r, c) } }
                } else ""
                else -> ""
            }
            if (text.isBlank()) continue
            val found = occurrences(text, q)
            if (found.isEmpty()) continue
            InkRenderer.itemBounds(item, box)
            // Satu sorotan untuk bendanya, walau katanya muncul beberapa kali di dalamnya.
            out.add(NoteHit(page, pageId, RectF(box), snippet(text, found[0], q.length)))
        }
    }

    /** Kata di teks ketik: kotak tepat per baris dari tata letak teksnya. */
    private fun inText(t: TextItem, q: String, page: Int, pageId: String, out: MutableList<NoteHit>) {
        if (t.text.isBlank()) return
        val layout = InkRenderer.layoutFor(t)
        // Cari di teks yang benar-benar digambar (poin dan penomoran bisa menambah tanda di awal baris).
        val shown = layout.text.toString()
        for (at in occurrences(shown, q)) {
            val end = at + q.length
            val l0 = layout.getLineForOffset(at)
            val l1 = layout.getLineForOffset((end - 1).coerceAtLeast(at))
            for (ln in l0..l1) {
                val a = if (ln == l0) at else layout.getLineStart(ln)
                val z = if (ln == l1) end else layout.getLineEnd(ln)
                val x0 = layout.getPrimaryHorizontal(a)
                val x1 = if (z >= layout.getLineEnd(ln)) layout.getLineRight(ln) else layout.getPrimaryHorizontal(z)
                val r = RectF(
                    t.x + minOf(x0, x1), t.y + layout.getLineTop(ln),
                    t.x + maxOf(x0, x1), t.y + layout.getLineBottom(ln),
                )
                if (r.width() < 1f) r.right = r.left + t.size * 0.4f
                out.add(NoteHit(page, pageId, r, snippet(shown, at, q.length)))
            }
        }
    }

    /**
     * Tulisan tangan: [lines] berisi kotak tiap baris dan teks hasil bacanya. Letak kata diperkirakan sebanding
     * dengan urutan hurufnya di baris itu.
     */
    fun inHandwriting(lines: List<Pair<RectF, String>>, q: String, page: Int, pageId: String, out: MutableList<NoteHit>) {
        for ((box, text) in lines) {
            if (text.isEmpty()) continue
            for (at in occurrences(text, q)) {
                val f0 = at.toFloat() / text.length
                val f1 = (at + q.length).toFloat() / text.length
                val w = box.width()
                val r = RectF(box.left + w * f0, box.top, box.left + w * f1, box.bottom)
                // Perkiraan: diberi sedikit kelonggaran supaya kata tulisan tangan tetap tercakup.
                val slack = w * 0.03f
                r.left = (r.left - slack).coerceAtLeast(box.left)
                r.right = (r.right + slack).coerceAtMost(box.right)
                out.add(NoteHit(page, pageId, r, snippet(text, at, q.length)))
            }
        }
    }

    /** Teks asli halaman PDF: kotak tepat per baris. */
    fun inPdf(layer: PdfTextLayer, q: String, page: Int, pageId: String, out: MutableList<NoteHit>) {
        val chars = layer.src.chars
        for (at in occurrences(chars, q)) {
            val rects = layer.rects(at, at + q.length - 1)
            for (r in rects) out.add(NoteHit(page, pageId, RectF(r), snippet(chars, at, q.length)))
        }
    }
}
