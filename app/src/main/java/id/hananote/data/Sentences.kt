package id.hananote.data

/** Huruf besar otomatis di awal kalimat, untuk teks dari tulisan tangan dan suara. */
object Sentences {

    private val afterStop = Regex("([.!?…]['\")\\]]*\\s+)(\\p{Ll})")

    /** Teks sebelumnya kosong atau diakhiri tanda akhir kalimat: teks berikutnya memulai kalimat baru. */
    fun startsSentence(before: String): Boolean {
        val t = before.trimEnd()
        if (t.isEmpty()) return true
        if (before.endsWith("\n")) return true
        return t.last() in ".!?…"
    }

    /** Huruf pertama (melewati spasi dan tanda kutip/kurung pembuka) dijadikan huruf besar. */
    fun capitalizeFirst(s: String): String {
        for (i in s.indices) {
            val ch = s[i]
            if (ch.isWhitespace() || ch in "\"'([“‘") continue
            if (!ch.isLowerCase()) return s
            return s.substring(0, i) + ch.uppercaseChar() + s.substring(i + 1)
        }
        return s
    }

    /**
     * Rapikan huruf besar: awal [text] bila memulai kalimat (menurut [before]), dan setiap huruf
     * setelah titik, tanda tanya, atau tanda seru di dalam [text].
     */
    fun fix(before: String, text: String): String {
        var out = if (startsSentence(before)) capitalizeFirst(text) else text
        out = afterStop.replace(out) { m -> m.groupValues[1] + m.groupValues[2].uppercase() }
        return out
    }
}
