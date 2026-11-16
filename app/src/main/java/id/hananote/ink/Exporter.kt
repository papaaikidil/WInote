package id.hananote.ink

import android.graphics.Bitmap
import android.graphics.pdf.PdfDocument
import kotlinx.coroutines.yield
import kotlin.math.roundToInt
import id.hananote.data.CalendarItem
import id.hananote.data.MindMapItem
import id.hananote.data.IndoDate
import id.hananote.data.LinkItem
import id.hananote.data.PageContent
import id.hananote.data.PageMeta
import id.hananote.data.StickyItem
import kotlinx.coroutines.CancellationException
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.math.min
import kotlin.math.roundToLong

enum class ExportFormat(val label: String, val ext: String, val mime: String) {
    PNG("PNG", "png", "image/png"),
    JPG("JPG", "jpg", "image/jpeg"),
    PDF("PDF", "pdf", "application/pdf"),
    DOCX("Word (.docx)", "docx", "application/vnd.openxmlformats-officedocument.wordprocessingml.document"),
}

/** Isi berkas Word: teks yang bisa disunting, gambar halaman persis seperti di layar, atau keduanya. */
enum class DocContent(val label: String) { BOTH("Teks + gambar"), PAGES("Gambar saja"), TEXT("Teks saja") }

/** Satu halaman catatan di dokumen Word: teksnya (null bila tidak disertakan) dan gambarnya (JPEG). */
class DocPage(val meta: PageMeta, val paragraphs: List<String>?, val jpeg: ByteArray?)

/**
 * Mengekspor catatan ke gambar (PNG/JPG) dan dokumen Word (.docx).
 * Berkas Word dibuat langsung (format Office Open XML), tanpa pustaka tambahan.
 */
object Exporter {

    /** Gambar satu halaman dalam resolusi cetak, sekitar 240 dpi untuk A4. */
    fun renderPage(page: PageMeta, content: PageContent, imageDir: File): Bitmap {
        val scale = min(3.4f, 3000f / maxOf(page.width, page.height))
        return InkRenderer.renderThumbnail(page, content, (page.width * scale).toInt().coerceAtLeast(1), imageDir)
    }

    fun writeImage(bmp: Bitmap, format: ExportFormat, file: File) {
        FileOutputStream(file).use { out ->
            if (format == ExportFormat.JPG) bmp.compress(Bitmap.CompressFormat.JPEG, 92, out)
            else bmp.compress(Bitmap.CompressFormat.PNG, 100, out)
        }
    }

    /**
     * PDF dengan satu halaman PDF untuk tiap halaman catatan, seukuran kertasnya. Tulisan, teks, dan
     * bentuk disimpan sebagai garis (tajam saat diperbesar atau dicetak), gambar dalam resolusi penuh.
     */
    suspend fun writePdf(pages: List<Pair<PageMeta, PageContent>>, imageDir: File, file: File, onPage: (Int) -> Unit) {
        val doc = PdfDocument()
        try {
            pages.forEachIndexed { i, (meta, content) ->
                onPage(i)
                val info = PdfDocument.PageInfo.Builder(
                    meta.width.roundToInt().coerceAtLeast(1),
                    meta.height.roundToInt().coerceAtLeast(1),
                    i + 1,
                ).create()
                val page = doc.startPage(info)
                val softErased = content.strokes.any { it.tool == id.hananote.data.Tool.ERASER || !it.masks.isNullOrEmpty() } ||
                    content.texts.any { !it.masks.isNullOrEmpty() } || content.items.any { !it.masks.isNullOrEmpty() }
                if (softErased) {
                    // Bekas kuas penghapus lembut tidak bisa ditulis sebagai vektor PDF: halaman ini jadi gambar.
                    val bmp = InkRenderer.renderThumbnail(meta, content, (meta.width * 3f).roundToInt().coerceIn(1, 2400), imageDir)
                    try {
                        page.canvas.drawBitmap(bmp, null, android.graphics.RectF(0f, 0f, meta.width, meta.height), android.graphics.Paint(android.graphics.Paint.FILTER_BITMAP_FLAG))
                    } finally {
                        bmp.recycle()
                    }
                } else {
                    InkRenderer.forPdf { InkRenderer.drawPage(page.canvas, meta, content, imageDir, 4f) }
                }
                doc.finishPage(page)
                yield()
            }
            FileOutputStream(file).use { doc.writeTo(it) }
        } finally {
            doc.close()
        }
    }

    fun jpegBytes(bmp: Bitmap): ByteArray {
        val bos = ByteArrayOutputStream()
        bmp.compress(Bitmap.CompressFormat.JPEG, 90, bos)
        return bos.toByteArray()
    }

    /**
     * Teks satu halaman dari atas ke bawah: teks ketik, isi stiker, link, kalender, dan tulisan tangan
     * yang dibaca pengenal (bila kamusnya sudah terunduh). Hasil kedua: apakah tulisan tangan ikut terbaca.
     */
    suspend fun pageParagraphs(page: PageMeta, content: PageContent, readHandwriting: Boolean): Pair<List<String>, Boolean> {
        val blocks = ArrayList<Pair<Float, String>>()
        for (t in content.texts) if (t.text.isNotBlank()) blocks.add(t.y to t.text)
        for (item in content.items) {
            when (item) {
                is StickyItem -> if (item.text.isNotBlank()) blocks.add(item.y to item.text)
                is LinkItem -> blocks.add(item.y to "${item.label} (${item.url})")
                is CalendarItem -> blocks.add(item.y to "Kalender ${IndoDate.months[(item.month - 1).coerceIn(0, 11)]} ${item.year}")
                is MindMapItem -> blocks.add(item.y to ("Mind map:\n" + item.outline().joinToString("\n")))
                else -> Unit
            }
        }
        val ink = content.strokes.filter { it.tool.isWriting && it.size > 0 }
        var complete = ink.isEmpty()
        if (ink.isNotEmpty() && readHandwriting) {
            complete = true
            var before = ""
            for (line in LineGrouper.group(ink, page)) {
                val pre = if (before.isEmpty()) "" else "$before "
                val text = try {
                    Handwriting.rank(Handwriting.recognizeAll(line, pre), pre).firstOrNull()?.trim().orEmpty()
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    complete = false
                    ""
                }
                if (text.isNotEmpty()) {
                    blocks.add(line.box.top to text)
                    before = text
                }
            }
        }
        val paragraphs = blocks.sortedBy { it.first }.flatMap { it.second.split('\n') }.map { it.trimEnd() }
        return paragraphs to complete
    }

    // ---------------- Word (.docx) ----------------

    private const val EMU_PER_TWIP = 635L
    private const val MARGIN_TWIPS = 567 // 1 cm

    /**
     * Dokumen Word dari halaman-halaman catatan. Tiap halaman bisa berisi teks yang bisa diedit,
     * gambar halaman persis seperti di layar, atau keduanya (teks dulu, lalu gambarnya).
     */
    fun writeDocx(title: String, pages: List<DocPage>, file: File) {
        val imagesOnly = pages.all { it.paragraphs == null }
        val first = pages.firstOrNull()?.meta
        // Gambar saja: ukuran kertas Word mengikuti kertas catatan. Ada teks: A4 dengan batas 2 cm.
        val pgW = if (imagesOnly) ((first?.width ?: 595f) * 20f).toInt() else 11906
        val pgH = if (imagesOnly) ((first?.height ?: 842f) * 20f).toInt() else 16838
        val margin = if (imagesOnly) MARGIN_TWIPS else 1134
        val boxW = (pgW - margin * 2).toFloat()
        val boxH = (pgH - margin * 2).toFloat()
        val body = StringBuilder()
        val rels = StringBuilder()
        if (!imagesOnly) body.append(paragraph(title, bold = true, sizeHalfPt = 36, spacingAfter = 240))
        val many = pages.size > 1
        var imageNo = 0
        pages.forEachIndexed { i, p ->
            var breakNext = i > 0
            // Perkiraan tinggi teks di atas gambar (twip), supaya gambar bisa muat di halaman yang sama.
            var used = if (i == 0 && !imagesOnly) 720f else 0f
            if (!imagesOnly && many) used += 460f
            p.paragraphs?.forEach { t -> used += (t.length / 80 + 1) * 330f + 120f }
            if (!imagesOnly && many) {
                body.append(paragraph("Halaman ${i + 1}", bold = true, sizeHalfPt = 26, spacingAfter = 120, pageBreak = breakNext, color = "6B6B75"))
                breakNext = false
            }
            val paras = p.paragraphs
            if (paras != null) {
                if (paras.isEmpty() && p.jpeg == null) body.append(paragraph("", spacingAfter = 120, pageBreak = breakNext))
                for ((k, t) in paras.withIndex()) body.append(paragraph(t, spacingAfter = 120, pageBreak = breakNext && k == 0))
                if (paras.isNotEmpty()) breakNext = false
            }
            val jpeg = p.jpeg ?: return@forEachIndexed
            imageNo++
            val w = p.meta.width * 20f
            val h = p.meta.height * 20f
            // Bila sisa halaman masih cukup lega, gambar diperkecil supaya tetap di bawah teksnya;
            // bila tidak, gambar memakai satu halaman penuh berikutnya.
            val rest = boxH - used - 360f
            val maxH = if (used > 0f && rest >= boxH * 0.55f) rest else boxH
            val s = min(boxW / w, maxH / h) * 0.995f
            val cx = (w * s).roundToLong() * EMU_PER_TWIP
            val cy = (h * s).roundToLong() * EMU_PER_TWIP
            val n = imageNo
            body.append("<w:p><w:pPr>")
            if (breakNext) body.append("<w:pageBreakBefore/>")
            body.append("<w:spacing w:before=\"120\" w:after=\"120\"/><w:jc w:val=\"center\"/></w:pPr><w:r><w:drawing>")
            body.append("<wp:inline distT=\"0\" distB=\"0\" distL=\"0\" distR=\"0\">")
            body.append("<wp:extent cx=\"$cx\" cy=\"$cy\"/>")
            body.append("<wp:docPr id=\"$n\" name=\"Halaman ${i + 1}\"/>")
            body.append("<wp:cNvGraphicFramePr><a:graphicFrameLocks noChangeAspect=\"1\"/></wp:cNvGraphicFramePr>")
            body.append("<a:graphic><a:graphicData uri=\"http://schemas.openxmlformats.org/drawingml/2006/picture\">")
            body.append("<pic:pic><pic:nvPicPr><pic:cNvPr id=\"$n\" name=\"halaman-$n.jpeg\"/><pic:cNvPicPr/></pic:nvPicPr>")
            body.append("<pic:blipFill><a:blip r:embed=\"rIdImg$n\"/><a:stretch><a:fillRect/></a:stretch></pic:blipFill>")
            body.append("<pic:spPr><a:xfrm><a:off x=\"0\" y=\"0\"/><a:ext cx=\"$cx\" cy=\"$cy\"/></a:xfrm>")
            body.append("<a:prstGeom prst=\"rect\"><a:avLst/></a:prstGeom></pic:spPr></pic:pic>")
            body.append("</a:graphicData></a:graphic></wp:inline></w:drawing></w:r></w:p>")
            rels.append("<Relationship Id=\"rIdImg$n\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/image\" Target=\"media/halaman-$n.jpeg\"/>")
        }
        body.append(sectPr(pgW, pgH, margin))
        ZipOutputStream(FileOutputStream(file)).use { zip ->
            writePackage(zip, title, body.toString(), rels.toString())
            var k = 0
            for (p in pages) {
                val bytes = p.jpeg ?: continue
                k++
                zip.putNextEntry(ZipEntry("word/media/halaman-$k.jpeg"))
                zip.write(bytes)
                zip.closeEntry()
            }
        }
    }

    private fun paragraph(
        text: String,
        bold: Boolean = false,
        sizeHalfPt: Int = 24,
        spacingAfter: Int = 0,
        pageBreak: Boolean = false,
        color: String? = null,
    ): String {
        val sb = StringBuilder("<w:p><w:pPr>")
        if (pageBreak) sb.append("<w:pageBreakBefore/>")
        sb.append("<w:spacing w:after=\"$spacingAfter\"/></w:pPr>")
        if (text.isNotEmpty()) {
            sb.append("<w:r><w:rPr>")
            if (bold) sb.append("<w:b/>")
            if (color != null) sb.append("<w:color w:val=\"$color\"/>")
            sb.append("<w:sz w:val=\"$sizeHalfPt\"/></w:rPr><w:t xml:space=\"preserve\">")
            sb.append(xml(text))
            sb.append("</w:t></w:r>")
        }
        sb.append("</w:p>")
        return sb.toString()
    }

    private fun sectPr(w: Int, h: Int, margin: Int): String {
        val orient = if (w > h) " w:orient=\"landscape\"" else ""
        return "<w:sectPr><w:pgSz w:w=\"$w\" w:h=\"$h\"$orient/>" +
            "<w:pgMar w:top=\"$margin\" w:right=\"$margin\" w:bottom=\"$margin\" w:left=\"$margin\" w:header=\"0\" w:footer=\"0\" w:gutter=\"0\"/></w:sectPr>"
    }

    private fun writePackage(zip: ZipOutputStream, title: String, body: String, imageRels: String) {
        fun put(name: String, text: String) {
            zip.putNextEntry(ZipEntry(name))
            zip.write(text.toByteArray(Charsets.UTF_8))
            zip.closeEntry()
        }
        val head = "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>\n"
        put(
            "[Content_Types].xml",
            head + "<Types xmlns=\"http://schemas.openxmlformats.org/package/2006/content-types\">" +
                "<Default Extension=\"rels\" ContentType=\"application/vnd.openxmlformats-package.relationships+xml\"/>" +
                "<Default Extension=\"xml\" ContentType=\"application/xml\"/>" +
                "<Default Extension=\"jpeg\" ContentType=\"image/jpeg\"/>" +
                "<Override PartName=\"/word/document.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.wordprocessingml.document.main+xml\"/>" +
                "<Override PartName=\"/docProps/core.xml\" ContentType=\"application/vnd.openxmlformats-package.core-properties+xml\"/>" +
                "</Types>",
        )
        put(
            "_rels/.rels",
            head + "<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">" +
                "<Relationship Id=\"rId1\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument\" Target=\"word/document.xml\"/>" +
                "<Relationship Id=\"rId2\" Type=\"http://schemas.openxmlformats.org/package/2006/relationships/metadata/core-properties\" Target=\"docProps/core.xml\"/>" +
                "</Relationships>",
        )
        val now = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.ROOT).apply { timeZone = TimeZone.getTimeZone("UTC") }.format(Date())
        put(
            "docProps/core.xml",
            head + "<cp:coreProperties xmlns:cp=\"http://schemas.openxmlformats.org/package/2006/metadata/core-properties\" " +
                "xmlns:dc=\"http://purl.org/dc/elements/1.1/\" xmlns:dcterms=\"http://purl.org/dc/terms/\" " +
                "xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\">" +
                "<dc:title>${xml(title)}</dc:title><dc:creator>WInote</dc:creator>" +
                "<dcterms:created xsi:type=\"dcterms:W3CDTF\">$now</dcterms:created>" +
                "<dcterms:modified xsi:type=\"dcterms:W3CDTF\">$now</dcterms:modified>" +
                "</cp:coreProperties>",
        )
        put(
            "word/_rels/document.xml.rels",
            head + "<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">$imageRels</Relationships>",
        )
        put(
            "word/document.xml",
            head + "<w:document xmlns:w=\"http://schemas.openxmlformats.org/wordprocessingml/2006/main\" " +
                "xmlns:r=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships\" " +
                "xmlns:wp=\"http://schemas.openxmlformats.org/drawingml/2006/wordprocessingDrawing\" " +
                "xmlns:a=\"http://schemas.openxmlformats.org/drawingml/2006/main\" " +
                "xmlns:pic=\"http://schemas.openxmlformats.org/drawingml/2006/picture\">" +
                "<w:body>$body</w:body></w:document>",
        )
    }

    /** Teks aman untuk XML: karakter khusus diganti, karakter kendali dibuang. */
    private fun xml(s: String): String {
        val sb = StringBuilder(s.length + 16)
        for (ch in s) {
            when {
                ch == '&' -> sb.append("&amp;")
                ch == '<' -> sb.append("&lt;")
                ch == '>' -> sb.append("&gt;")
                ch == '"' -> sb.append("&quot;")
                ch == '\t' -> sb.append(' ')
                ch.code < 0x20 -> Unit
                else -> sb.append(ch)
            }
        }
        return sb.toString()
    }

    /** Nama berkas yang aman dari judul catatan. */
    fun safeName(title: String): String =
        title.replace(Regex("[\\\\/:*?\"<>|\\n\\r\\t]"), " ").trim().take(60).ifEmpty { "Catatan" }
}
