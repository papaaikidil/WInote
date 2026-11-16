package id.hananote.data

/**
 * Daftar berpoin (•) dan bernomor (1. 2. 3.) di teks ketik. Penanda disimpan di dalam teksnya,
 * jadi ikut dicari, diekspor, dan disunting seperti teks biasa.
 */
object TextLists {

    enum class Kind { NONE, BULLET, NUMBER, CHECK }

    const val BULLET = "• "
    /** Kotak centang kosong dan yang sudah dicentang (daftar tugas). */
    const val BOX = "\u2610 "
    const val DONE = "\u2611 "
    private val numbered = Regex("^(\\d{1,3})[.)] ")

    /** Penanda di awal baris ("• " atau "12. "), atau null. */
    fun prefixOf(line: String): String? {
        if (line.startsWith(BULLET)) return BULLET
        if (line.startsWith(BOX)) return BOX
        if (line.startsWith(DONE)) return DONE
        return numbered.find(line)?.value
    }

    /** Centang atau hapus centang kotak di posisi [at] (awal baris). Hasil: teks baru, atau null bila bukan kotak. */
    fun toggleCheck(text: String, at: Int): String? {
        if (at < 0 || at >= text.length) return null
        val c = text[at]
        val flipped = when (c) {
            BOX[0] -> DONE[0]
            DONE[0] -> BOX[0]
            else -> return null
        }
        return text.substring(0, at) + flipped + text.substring(at + 1)
    }

    /** Pemeriksaan cepat: adakah baris yang memakai penanda daftar. */
    fun hasMarkers(text: String): Boolean {
        if (text.isEmpty()) return false
        var start = 0
        while (start < text.length) {
            if (prefixOf(text.substring(start, minOf(text.length, start + 6))) != null) return true
            val nl = text.indexOf('\n', start)
            if (nl < 0) break
            start = nl + 1
        }
        return false
    }

    private fun strip(line: String): String {
        val p = prefixOf(line) ?: return line
        return line.substring(p.length)
    }

    /** Jenis daftar seluruh teks: hanya bila semua baris yang berisi memakai penanda yang sama jenisnya. */
    fun kindOf(text: String): Kind {
        val lines = text.split('\n').filter { it.isNotBlank() }
        if (lines.isEmpty()) return Kind.NONE
        if (lines.all { it.startsWith(BOX) || it.startsWith(DONE) }) return Kind.CHECK
        if (lines.all { it.startsWith(BULLET) }) return Kind.BULLET
        if (lines.all { numbered.containsMatchIn(it) }) return Kind.NUMBER
        return Kind.NONE
    }

    /** Ubah semua baris yang berisi menjadi daftar jenis [kind] (atau hapus penandanya bila NONE). */
    fun apply(text: String, kind: Kind): String {
        var n = 0
        return text.split('\n').joinToString("\n") { line ->
            if (line.isBlank()) {
                line
            } else {
                val body = strip(line)
                when (kind) {
                    Kind.NONE -> body
                    Kind.BULLET -> BULLET + body
                    Kind.NUMBER -> { n++; "$n. $body" }
                    Kind.CHECK -> (if (line.startsWith(DONE)) DONE else BOX) + body
                }
            }
        }
    }

    /** Tekan sekali untuk memasang, sekali lagi untuk melepas. */
    fun toggle(text: String, kind: Kind): String = if (kindOf(text) == kind) apply(text, Kind.NONE) else apply(text, kind)

    /** Awal dan akhir paragraf-paragraf yang tersentuh kursor atau pilihan [a, b). */
    private fun paragraphs(text: String, a: Int, b: Int): Pair<Int, Int> {
        val lo = a.coerceIn(0, text.length)
        val hi = b.coerceIn(lo, text.length)
        val start = if (lo == 0) 0 else text.lastIndexOf('\n', lo - 1) + 1
        val nl = text.indexOf('\n', hi)
        return start to (if (nl < 0) text.length else nl)
    }

    /** Jenis daftar paragraf di kursor atau pilihan (seperti tombol daftar di pengolah kata). */
    fun kindAt(text: String, a: Int, b: Int): Kind {
        val (start, end) = paragraphs(text, a, b)
        return kindOf(text.substring(start, end))
    }

    /**
     * Pasang atau lepas daftar hanya di paragraf yang tersentuh kursor atau pilihan [a, b), bukan seluruh teks.
     * Hasil: teks baru, lalu awal dan akhir kursor/pilihan baru.
     */
    fun toggleAt(text: String, a: Int, b: Int, kind: Kind): Triple<String, Int, Int> {
        val (start, end) = paragraphs(text, a, b)
        val part = text.substring(start, end)
        val next = if (part.isBlank() && !part.contains('\n')) {
            // Baris kosong: penanda dipasang supaya bisa langsung mengetik butir pertama.
            when (kind) {
                Kind.NONE -> part
                Kind.BULLET -> BULLET
                Kind.NUMBER -> "1. "
                Kind.CHECK -> BOX
            }
        } else toggle(part, kind)
        val out = text.substring(0, start) + next + text.substring(end)
        if (!part.contains('\n') && !next.contains('\n')) {
            // Satu paragraf: kursor tetap di huruf yang sama (bergeser sepanjang penandanya).
            val oldPrefix = prefixOf(part)?.length ?: 0
            val newPrefix = prefixOf(next)?.length ?: 0
            val d = newPrefix - oldPrefix
            val lo = start + newPrefix
            val hi = start + next.length
            return Triple(out, (a + d).coerceIn(lo, hi), (b + d).coerceIn(lo, hi))
        }
        return Triple(out, start, start + next.length)
    }

    /**
     * Enter baru saja ditekan (teks bertambah satu baris baru di [cursor]): lanjutkan daftar di baris baru.
     * Bila butir sebelumnya kosong, daftar diakhiri. Hasil: teks dan posisi kursor baru, atau null bila
     * tidak ada yang perlu diubah.
     */
    fun continueOnEnter(old: String, new: String, cursor: Int): Pair<String, Int>? {
        if (new.length != old.length + 1 || cursor <= 0 || cursor > new.length) return null
        if (new[cursor - 1] != '\n') return null
        if (old != new.removeRange(cursor - 1, cursor)) return null
        val lineEnd = cursor - 1
        val lineStart = new.lastIndexOf('\n', lineEnd - 1) + 1
        val prev = new.substring(lineStart, lineEnd)
        val prefix = prefixOf(prev) ?: return null
        if (prev.length == prefix.length) {
            // Butir kosong: akhiri daftar.
            val text = new.substring(0, lineStart) + new.substring(cursor)
            return text to lineStart
        }
        val next = if (prefix == BULLET) BULLET else if (prefix == BOX || prefix == DONE) BOX else {
            val n = numbered.find(prev)?.groupValues?.get(1)?.toIntOrNull() ?: 0
            "${n + 1}. "
        }
        val text = new.substring(0, cursor) + next + new.substring(cursor)
        return text to cursor + next.length
    }
}
