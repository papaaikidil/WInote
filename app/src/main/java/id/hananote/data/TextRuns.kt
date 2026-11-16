package id.hananote.data

/**
 * Gaya per huruf pada teks ketik: tebal, miring, garis bawah, dan coret (seperti di pengolah kata).
 * Disimpan sebagai potongan [awal, akhir, gaya] yang tidak tumpang tindih dan berurutan. Tidak bisa diubah:
 * setiap perubahan menghasilkan salinan baru, jadi aman dipakai bersama riwayat urungkan.
 */
class TextRuns private constructor(private val data: IntArray) {

    val isEmpty: Boolean get() = data.isEmpty()
    val count: Int get() = data.size / 3
    fun start(i: Int) = data[i * 3]
    fun end(i: Int) = data[i * 3 + 1]
    fun flags(i: Int) = data[i * 3 + 2]

    /** Gaya huruf di posisi [pos] (0 bila polos). */
    fun flagsAt(pos: Int): Int {
        for (i in 0 until count) {
            if (pos < start(i)) return 0
            if (pos < end(i)) return flags(i)
        }
        return 0
    }

    /** Gaya yang dipakai huruf baru di kursor [pos]: ikut huruf sebelumnya (atau huruf pertama di awal teks). */
    fun flagsForInsert(pos: Int, length: Int): Int = when {
        pos > 0 -> flagsAt(pos - 1)
        length > 0 -> flagsAt(0)
        else -> 0
    }

    /** Semua huruf di rentang [a, b) punya gaya [flag]. */
    fun allHave(a: Int, b: Int, flag: Int): Boolean {
        if (a >= b) return false
        val per = perChar(b)
        for (k in a until b) if (per[k] and flag == 0) return false
        return true
    }

    /** Gaya [flag] dipasang ([on]) atau dilepas di rentang [a, b) dari teks sepanjang [length]. */
    fun styled(a: Int, b: Int, flag: Int, on: Boolean, length: Int): TextRuns {
        val per = perChar(length)
        for (k in a.coerceAtLeast(0) until b.coerceAtMost(length)) {
            per[k] = if (on) per[k] or flag else per[k] and flag.inv()
        }
        return fromPerChar(per)
    }

    /**
     * Teks berubah dari [old] menjadi [new] (diketik, dihapus, ditempel): gaya ikut bergeser. Huruf baru memakai
     * [insertFlags], atau gaya huruf sebelumnya bila null.
     */
    fun edited(old: String, new: String, insertFlags: Int?): TextRuns {
        if (old == new) return this
        if (isEmpty && (insertFlags == null || insertFlags == 0)) return this
        val max = minOf(old.length, new.length)
        var p = 0
        while (p < max && old[p] == new[p]) p++
        var s = 0
        while (s < max - p && old[old.length - 1 - s] == new[new.length - 1 - s]) s++
        val per = perChar(old.length)
        val inserted = new.length - s - p
        val f = insertFlags ?: flagsForInsert(p, old.length)
        val out = IntArray(new.length)
        for (k in 0 until p) out[k] = per[k]
        for (k in 0 until inserted) out[p + k] = f
        for (k in 0 until s) out[new.length - s + k] = per[old.length - s + k]
        return fromPerChar(out)
    }

    /** Dipotong supaya tidak melewati panjang teks. */
    fun clamp(length: Int): TextRuns {
        if (isEmpty) return this
        if (end(count - 1) <= length) return this
        return fromPerChar(perChar(length))
    }

    /** Gaya untuk potongan teks [a, b) (misalnya teks yang disalin), mulai dari 0. */
    fun slice(a: Int, b: Int): TextRuns {
        if (isEmpty || a >= b) return EMPTY
        val per = perChar(b)
        return fromPerChar(per.copyOfRange(a.coerceAtLeast(0), b))
    }

    fun toIntArray(): IntArray = data.copyOf()

    private fun perChar(length: Int): IntArray {
        val per = IntArray(length)
        for (i in 0 until count) {
            val a = start(i).coerceIn(0, length)
            val b = end(i).coerceIn(0, length)
            for (k in a until b) per[k] = flags(i)
        }
        return per
    }

    override fun equals(other: Any?): Boolean = other is TextRuns && other.data.contentEquals(data)
    override fun hashCode(): Int = data.contentHashCode()

    companion object {
        const val BOLD = 1
        const val ITALIC = 2
        const val UNDERLINE = 4
        const val STRIKE = 8

        val EMPTY = TextRuns(IntArray(0))

        /** Dari potongan [awal, akhir, gaya] yang tersimpan; potongan yang rusak diabaikan. */
        fun of(triples: IntArray): TextRuns {
            if (triples.size < 3) return EMPTY
            val out = ArrayList<Int>()
            var last = 0
            var i = 0
            while (i + 2 < triples.size) {
                val a = triples[i]
                val b = triples[i + 1]
                val f = triples[i + 2] and 0xF
                if (a >= last && b > a && f != 0) {
                    out.add(a); out.add(b); out.add(f)
                    last = b
                }
                i += 3
            }
            return if (out.isEmpty()) EMPTY else TextRuns(out.toIntArray())
        }

        fun fromPerChar(per: IntArray): TextRuns {
            val out = ArrayList<Int>()
            var k = 0
            while (k < per.size) {
                val f = per[k]
                var e = k + 1
                while (e < per.size && per[e] == f) e++
                if (f != 0) {
                    out.add(k); out.add(e); out.add(f)
                }
                k = e
            }
            return if (out.isEmpty()) EMPTY else TextRuns(out.toIntArray())
        }
    }
}
