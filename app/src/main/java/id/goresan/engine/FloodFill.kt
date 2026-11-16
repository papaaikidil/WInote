package id.goresan.engine

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * ColorDrop: mengisi wilayah yang warnanya mirip dengan titik yang dijatuhi warna.
 * Hasilnya masker: 255 = inti wilayah (diisi di atas), 128 = pinggiran yang diisi "di belakang" garis
 * supaya tidak ada celah putih di tepi garis yang halus.
 *
 * Supaya garis pensil yang tipis/bertekstur tetap menahan isian:
 *  - beda keburaman (alfa) dihitung dua kali lebih berat daripada beda warna,
 *  - celah kecil di garis ditutup dulu (garis "ditebalkan" sementara sebesar [gap] piksel), lalu wilayah hasil
 *    isian dikembalikan ke ukuran aslinya sampai menyentuh garis.
 */
object FloodFill {
    class Result(val mask: ByteArray, val box: IRect)

    fun run(px: ByteArray, w: Int, h: Int, sx: Int, sy: Int, threshold: Float, fill: Int = 0): Result {
        val n = w * h
        val box = IRect()
        if (sx !in 0 until w || sy !in 0 until h) return Result(ByteArray(n), box)
        val si = (sy * w + sx) * 4
        val r0 = px[si].toInt() and 0xFF
        val g0 = px[si + 1].toInt() and 0xFF
        val b0 = px[si + 2].toInt() and 0xFF
        val a0 = px[si + 3].toInt() and 0xFF
        val tol = (threshold.coerceIn(0f, 1f) * 255f).toInt()

        // 1 = penghalang (warnanya beda dari titik awal).
        val blocked = ByteArray(n)
        for (i in 0 until n) {
            val o = i * 4
            val dr = abs((px[o].toInt() and 0xFF) - r0)
            val dg = abs((px[o + 1].toInt() and 0xFF) - g0)
            val db = abs((px[o + 2].toInt() and 0xFF) - b0)
            val da = abs((px[o + 3].toInt() and 0xFF) - a0) * 2
            if (max(max(dr, dg), max(db, da)) > tol) blocked[i] = 1
        }

        // Celah kecil ditutup: penghalang ditebalkan sementara.
        val gap = when {
            max(w, h) >= 2400 -> 3
            max(w, h) >= 1000 -> 2
            else -> 1
        }
        var wall = dilate(blocked, w, h, gap)
        val useGap = wall[sy * w + sx].toInt() == 0
        if (!useGap) wall = blocked
        // Masker dibuat sesudah penebalan supaya puncak pemakaian memori lebih rendah di kanvas besar.
        val mask = ByteArray(n)

        val core = 0xFF.toByte()
        var minX = sx; var maxX = sx; var minY = sy; var maxY = sy

        // Isi berbaris (scanline) dari titik awal di wilayah yang tidak terhalang.
        var stack = IntArray(4096)
        var sp = 0
        fun push(x: Int, y: Int) {
            if (sp + 2 > stack.size) stack = stack.copyOf(stack.size * 2)
            stack[sp++] = x
            stack[sp++] = y
        }
        fun open(i: Int) = wall[i].toInt() == 0 && mask[i] != core
        push(sx, sy)
        while (sp > 0) {
            val y = stack[--sp]
            val x = stack[--sp]
            val row = y * w
            if (!open(row + x)) continue
            var l = x
            while (l > 0 && open(row + l - 1)) l--
            var r = x
            while (r < w - 1 && open(row + r + 1)) r++
            for (i in l..r) mask[row + i] = core
            if (l < minX) minX = l
            if (r > maxX) maxX = r
            if (y < minY) minY = y
            if (y > maxY) maxY = y
            for (ny in intArrayOf(y - 1, y + 1)) {
                if (ny < 0 || ny >= h) continue
                val nrow = ny * w
                var i = l
                var inSpan = false
                while (i <= r) {
                    if (open(nrow + i)) {
                        if (!inSpan) { push(i, ny); inSpan = true }
                    } else {
                        inSpan = false
                    }
                    i++
                }
            }
        }

        // Penebalan tidak dipakai lagi: lepaskan sebelum tahap berikutnya meminta memori.
        wall = blocked

        // Wilayah dikembalikan ke ukuran asli (sampai menyentuh garis), lalu pinggiran di belakang garis.
        val grow = if (useGap) gap else 0
        val ring: Byte = 0x80.toByte()
        val steps = grow + 2
        var x0 = minX; var x1 = maxX; var y0 = minY; var y1 = maxY
        for (s in 0 until steps) {
            val ax = max(0, x0 - 1); val bx = min(w - 1, x1 + 1)
            val ay = max(0, y0 - 1); val by = min(h - 1, y1 + 1)
            val coreStep = s < grow
            // Tandai dulu (nilai sementara 1), baru diubah, supaya satu langkah = satu piksel.
            for (y in ay..by) {
                val row = y * w
                for (x in ax..bx) {
                    val i = row + x
                    if (mask[i].toInt() != 0) continue
                    if (coreStep && blocked[i].toInt() != 0) continue
                    var near = (x > 0 && isSet(mask[i - 1])) || (x < w - 1 && isSet(mask[i + 1])) ||
                        (y > 0 && isSet(mask[i - w])) || (y < h - 1 && isSet(mask[i + w]))
                    // Penghalang tadi ditebalkan berbentuk kotak, jadi pengembaliannya juga harus boleh serong;
                    // kalau hanya 4 arah, di bagian garis yang miring (mis. lingkaran arah 45°) tersisa celah
                    // 1 piksel yang tidak terisi dan tampak sebagai garis putih tipis.
                    if (!near && coreStep) {
                        near = diag(mask, blocked, w, h, x, y, -1, -1) || diag(mask, blocked, w, h, x, y, 1, -1) ||
                            diag(mask, blocked, w, h, x, y, -1, 1) || diag(mask, blocked, w, h, x, y, 1, 1)
                    }
                    if (near) mask[i] = 1
                }
            }
            val v = if (coreStep) core else ring
            for (y in ay..by) {
                val row = y * w
                for (x in ax..bx) if (mask[row + x].toInt() == 1) mask[row + x] = v
            }
            x0 = ax; x1 = bx; y0 = ay; y1 = by
        }

        // Isi di belakang garis: menembus sisi lembut garis (makin pekat ke dalam garis), dan bila warna garis sama
        // dengan warna isian, seluruh badan garis sampai sisi luarnya. Jadi tidak ada pita pucat di tepi dalam garis,
        // dan garis yang sewarna tampak menyatu dengan isiannya walau kuasnya tidak 100% pekat.
        val fr = (fill shr 16) and 0xFF
        val fg = (fill shr 8) and 0xFF
        val fb = fill and 0xFF
        fun strength(i: Int): Int {
            val o = i * 4
            val dr = abs((px[o].toInt() and 0xFF) - r0)
            val dg = abs((px[o + 1].toInt() and 0xFF) - g0)
            val db = abs((px[o + 2].toInt() and 0xFF) - b0)
            val da = abs((px[o + 3].toInt() and 0xFF) - a0) * 2
            return max(max(dr, dg), max(db, da))
        }
        fun similar(i: Int): Boolean {
            val o = i * 4
            val a = px[o + 3].toInt() and 0xFF
            if (a < 24) return false
            val ur = (px[o].toInt() and 0xFF) * 255 / a
            val ug = (px[o + 1].toInt() and 0xFF) * 255 / a
            val ub = (px[o + 2].toInt() and 0xFF) * 255 / a
            return max(max(abs(ur - fr), abs(ug - fg)), abs(ub - fb)) <= 48
        }
        val reach = (max(w, h) / 16).coerceIn(48, 160)
        val dist = ByteArray(n)
        // Kepekatan tertinggi di sepanjang jalan (dibagi 2 supaya muat 1 byte): menyusuri badan garis boleh, menuruni
        // sisi luarnya tidak, supaya tepi luar garis tetap halus.
        val peak = ByteArray(n)
        var q = IntArray(8192)
        var qt = 0
        fun enqueue(i: Int) {
            if (qt == q.size) q = q.copyOf(q.size * 2)
            q[qt++] = i
        }
        for (y in y0..y1) {
            val row = y * w
            for (x in x0..x1) {
                val i = row + x
                if (mask[i].toInt() == 0) continue
                val open = (x > 0 && mask[i - 1].toInt() == 0 && blocked[i - 1].toInt() != 0) ||
                    (x < w - 1 && mask[i + 1].toInt() == 0 && blocked[i + 1].toInt() != 0) ||
                    (y > 0 && mask[i - w].toInt() == 0 && blocked[i - w].toInt() != 0) ||
                    (y < h - 1 && mask[i + w].toInt() == 0 && blocked[i + w].toInt() != 0)
                if (open) {
                    peak[i] = (strength(i) / 2).coerceAtMost(255).toByte()
                    enqueue(i)
                }
            }
        }
        var head = 0
        while (head < qt) {
            val p = q[head++]
            val d = (dist[p].toInt() and 0xFF) + 1
            if (d > reach) continue
            val sp = strength(p)
            val x = p % w
            val y = p / w
            for (k in 0 until 4) {
                val nx = when (k) { 0 -> x - 1; 1 -> x + 1; else -> x }
                val ny = when (k) { 2 -> y - 1; 3 -> y + 1; else -> y }
                if (nx < 0 || ny < 0 || nx >= w || ny >= h) continue
                val ni = ny * w + nx
                if (mask[ni].toInt() != 0 || blocked[ni].toInt() == 0) continue
                val sn = strength(ni)
                val pk = max((peak[p].toInt() and 0xFF) * 2, sn)
                val climb = sn > sp + 3
                val flat = sn >= sp - 12 && sn >= pk - 24 && similar(ni)
                if (!climb && !flat) continue
                mask[ni] = ring
                dist[ni] = d.toByte()
                peak[ni] = (pk / 2).coerceAtMost(255).toByte()
                enqueue(ni)
                if (nx < x0) x0 = nx
                if (nx > x1) x1 = nx
                if (ny < y0) y0 = ny
                if (ny > y1) y1 = ny
            }
        }

        box.set(x0, y0, x1 + 1, y1 + 1)
        return Result(mask, box)
    }

    /** Nilai masker yang sudah final (inti atau pinggiran), bukan tanda sementara. */
    private fun isSet(b: Byte) = b.toInt() != 0 && b.toInt() != 1

    /**
     * Tetangga serong ([dx], [dy]) sudah terisi dan langkah ke sana tidak menyelip di antara dua penghalang
     * (supaya garis serong yang tipis tidak ditembus).
     */
    private fun diag(mask: ByteArray, blocked: ByteArray, w: Int, h: Int, x: Int, y: Int, dx: Int, dy: Int): Boolean {
        val nx = x + dx
        val ny = y + dy
        if (nx < 0 || ny < 0 || nx >= w || ny >= h) return false
        if (!isSet(mask[ny * w + nx])) return false
        return blocked[y * w + nx].toInt() == 0 || blocked[ny * w + x].toInt() == 0
    }

    /** Penghalang ditebalkan [r] piksel (kotak), dua lintasan terpisah: baris lalu kolom. */
    private fun dilate(src: ByteArray, w: Int, h: Int, r: Int): ByteArray {
        val tmp = ByteArray(w * h)
        for (y in 0 until h) {
            val row = y * w
            var last = -1_000_000
            for (x in 0 until w) {
                if (src[row + x].toInt() != 0) last = x
                if (x - last <= r) tmp[row + x] = 1
            }
            last = 1_000_000
            for (x in w - 1 downTo 0) {
                if (src[row + x].toInt() != 0) last = x
                if (last - x <= r) tmp[row + x] = 1
            }
        }
        val out = ByteArray(w * h)
        for (x in 0 until w) {
            var last = -1_000_000
            for (y in 0 until h) {
                if (tmp[y * w + x].toInt() != 0) last = y
                if (y - last <= r) out[y * w + x] = 1
            }
            last = 1_000_000
            for (y in h - 1 downTo 0) {
                if (tmp[y * w + x].toInt() != 0) last = y
                if (last - y <= r) out[y * w + x] = 1
            }
        }
        return out
    }
}
