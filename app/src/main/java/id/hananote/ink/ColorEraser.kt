package id.hananote.ink

import kotlin.math.sqrt

/**
 * Menghapus latar polos dari gambar (misalnya foto barang di atas meja putih, atau logo berlatar warna),
 * tanpa internet dan tanpa Layanan Google Play.
 *
 * Cara kerjanya seperti tongkat ajaib: warna latar ditebak dari tepi gambar, lalu piksel yang mirip warna
 * itu dan tersambung ke tepi dibuat tembus pandang. Gradasi latar ikut terbawa karena piksel juga boleh
 * diterima bila mirip tetangganya yang sudah terhapus. Pinggiran objek dibuat halus supaya tidak bergerigi.
 *
 * Bekerja pada larik piksel ARGB biasa, jadi bisa diuji tanpa Android.
 */
object ColorEraser {

    private const val MAX_DIST = 765f // sqrt(2+4+3) * 255

    private fun dist(a: Int, b: Int): Float {
        val dr = ((a shr 16) and 0xFF) - ((b shr 16) and 0xFF)
        val dg = ((a shr 8) and 0xFF) - ((b shr 8) and 0xFF)
        val db = (a and 0xFF) - (b and 0xFF)
        return sqrt((2 * dr * dr + 4 * dg * dg + 3 * db * db).toFloat()) / MAX_DIST
    }

    /** Warna-warna latar yang paling sering muncul di tepi gambar (paling banyak tiga). */
    fun borderColors(px: IntArray, w: Int, h: Int): List<Int> {
        val counts = HashMap<Int, IntArray>() // kunci warna kasar -> [jumlah, r, g, b]
        var total = 0
        fun add(c: Int) {
            if ((c ushr 24) < 24) return
            val key = ((c shr 20) and 0xF shl 8) or ((c shr 12) and 0xF shl 4) or ((c shr 4) and 0xF)
            val e = counts.getOrPut(key) { IntArray(4) }
            e[0]++; e[1] += (c shr 16) and 0xFF; e[2] += (c shr 8) and 0xFF; e[3] += c and 0xFF
            total++
        }
        for (x in 0 until w) { add(px[x]); add(px[(h - 1) * w + x]) }
        for (y in 1 until h - 1) { add(px[y * w]); add(px[y * w + w - 1]) }
        if (total == 0) return emptyList()
        return counts.values
            .sortedByDescending { it[0] }
            .filter { it[0] >= total * 0.08f }
            .take(3)
            .map { e -> (0xFF shl 24) or ((e[1] / e[0]) shl 16) or ((e[2] / e[0]) shl 8) or (e[3] / e[0]) }
    }

    /**
     * Hapus latar. [tolerance] 0..1: makin besar makin banyak warna yang dianggap latar.
     * Hasil: larik piksel baru dengan latar tembus pandang.
     */
    fun erase(src: IntArray, w: Int, h: Int, tolerance: Float): IntArray {
        val out = src.copyOf()
        if (w < 3 || h < 3) return out
        val refs = borderColors(src, w, h)
        val tol = tolerance.coerceIn(0.01f, 0.9f)
        val n = w * h
        // 0 = belum, 1 = latar, 2 = objek.
        val state = ByteArray(n)
        val refDist = FloatArray(n) { -1f }
        fun nearestRef(i: Int): Float {
            var d = refDist[i]
            if (d >= 0f) return d
            val c = src[i]
            d = if ((c ushr 24) < 24) 0f else refs.minOfOrNull { dist(c, it) } ?: 1f
            refDist[i] = d
            return d
        }
        val queue = IntArray(n)
        var head = 0
        var tail = 0
        fun seed(i: Int) {
            if (state[i] != 0.toByte()) return
            if (nearestRef(i) <= tol) {
                state[i] = 1; queue[tail++] = i
            }
        }
        for (x in 0 until w) { seed(x); seed((h - 1) * w + x) }
        for (y in 1 until h - 1) { seed(y * w); seed(y * w + w - 1) }

        val local = tol * 0.35f
        val far = tol * 1.8f
        while (head < tail) {
            val i = queue[head++]
            val x = i % w
            val y = i / w
            for (k in 0..3) {
                val nx = x + if (k == 0) -1 else if (k == 1) 1 else 0
                val ny = y + if (k == 2) -1 else if (k == 3) 1 else 0
                if (nx < 0 || ny < 0 || nx >= w || ny >= h) continue
                val j = ny * w + nx
                if (state[j] != 0.toByte()) continue
                val d = nearestRef(j)
                // Mirip warna latar, atau mirip tetangganya yang sudah latar (gradasi, bayangan lembut).
                if (d <= tol || (d <= far && dist(src[j], src[i]) <= local)) {
                    state[j] = 1
                    queue[tail++] = j
                }
            }
        }

        // Latar jadi tembus pandang; pinggiran objek yang menyentuh latar dibuat setengah tembus pandang.
        for (i in 0 until n) {
            if (state[i] == 1.toByte()) {
                out[i] = 0
                continue
            }
            val x = i % w
            val y = i / w
            var touches = false
            if (x > 0 && state[i - 1] == 1.toByte()) touches = true
            else if (x < w - 1 && state[i + 1] == 1.toByte()) touches = true
            else if (y > 0 && state[i - w] == 1.toByte()) touches = true
            else if (y < h - 1 && state[i + w] == 1.toByte()) touches = true
            if (!touches) continue
            val d = nearestRef(i)
            val keep = ((d - tol * 0.5f) / (tol * 1.5f)).coerceIn(0.25f, 1f)
            val a = ((out[i] ushr 24) * keep).toInt().coerceIn(0, 255)
            out[i] = (a shl 24) or (out[i] and 0x00FFFFFF)
        }
        return out
    }

    /** Bagian gambar yang terhapus (0..1), untuk memberi tahu bila hasilnya kemungkinan keliru. */
    fun removedFraction(px: IntArray): Float {
        if (px.isEmpty()) return 0f
        var c = 0
        for (p in px) if ((p ushr 24) == 0) c++
        return c.toFloat() / px.size
    }
}
