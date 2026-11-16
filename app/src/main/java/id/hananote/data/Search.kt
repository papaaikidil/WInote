package id.hananote.data

/** Satu hasil pencarian: catatan, halaman (-1 bila cocok di judul), dan potongan teks di sekitarnya. */
class SearchHit(
    val note: Note,
    val bookTitle: String,
    val pageIndex: Int,
    val snippet: String,
    val matchStart: Int,
    val matchLength: Int,
)

/**
 * Teks yang bisa dicari dari satu halaman.
 *
 * Indeks halaman disimpan di <page>.idx: baris pertama keterangan, lalu teks ketik,
 * lalu pemisah, lalu hasil baca tulisan tangan. Keterangan "partial" berarti tulisan tangannya
 * belum terbaca (kamus belum terunduh) dan perlu dibaca ulang nanti.
 */
object PageText {
    const val COMPLETE = "hana-idx 1 complete"
    const val PARTIAL = "hana-idx 1 partial"
    const val SEPARATOR = "\u001E"

    /** Teks ketik, isi stiker, label link, dan judul kalender, dari atas ke bawah. */
    fun typed(content: PageContent): String {
        val parts = ArrayList<Pair<Float, String>>()
        for (t in content.texts) if (t.text.isNotBlank()) parts.add(t.y to t.text)
        for (item in content.items) {
            when (item) {
                is StickyItem -> if (item.text.isNotBlank()) parts.add(item.y to item.text)
                is LinkItem -> parts.add(item.y to (item.label + " " + item.url))
                is CalendarItem -> parts.add(item.y to (IndoDate.months[(item.month - 1).coerceIn(0, 11)] + " " + item.year))
                is MindMapItem -> parts.add(item.y to item.outline().joinToString("\n") { it.trim().removePrefix("• ") })
                // Isi tabel: tiap baris jadi satu baris teks, sel dipisah " | ".
                is TableItem -> if (item.hasText) parts.add(
                    item.y to (0 until item.rows).mapNotNull { r ->
                        val row = (0 until item.cols).map { c -> item.cell(r, c).replace('\n', ' ').trim() }
                        if (row.all { it.isEmpty() }) null else row.joinToString(" | ")
                    }.joinToString("\n")
                )
                else -> Unit
            }
        }
        return parts.sortedBy { it.first }.joinToString("\n") { it.second }
    }

    fun compose(typed: String, handwriting: String?, complete: Boolean): String =
        (if (complete) COMPLETE else PARTIAL) + "\n" + typed + "\n" + SEPARATOR + "\n" + (handwriting ?: "")

    /** Bagian tulisan tangan dari isi indeks. */
    fun handwritingPart(index: String): String = index.substringAfter("\n$SEPARATOR\n", "")

    /** Isi indeks tanpa baris keterangan, untuk dicari. */
    fun searchable(index: String): String = index.substringAfter('\n', "").replace(SEPARATOR, "")
}
