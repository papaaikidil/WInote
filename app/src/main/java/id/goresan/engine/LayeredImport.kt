package id.goresan.engine

import java.io.File
import java.io.RandomAccessFile
import java.util.zip.Inflater
import java.util.zip.ZipFile
import kotlin.math.abs
import kotlin.math.roundToInt

/*
 * Impor berkas berlapis (PSD/PSB dan .procreate) tanpa pustaka Android, supaya bisa diuji di JVM biasa.
 * Pengurai mengirim layer satu per satu (dari paling bawah ke paling atas) ke [ImportSink]; piksel tidak
 * ditahan semuanya di memori sekaligus.
 */

/** Dokumen hasil impor. */
class ImportDoc(val w: Int, val h: Int) {
    var dpi = 0
    var name: String? = null
    /** Warna latar ARGB (null = tidak ada di berkas). */
    var bg: Int? = null
    var bgVisible = true
    /** Piksel berkas dalam ruang warna Display P3. */
    var p3 = false
    val groups = ArrayList<ImportGroup>()
    /** Catatan untuk pengguna (mis. bagian yang dilewati). */
    val notes = ArrayList<String>()
}

class ImportGroup(var name: String, var visible: Boolean = true, var collapsed: Boolean = false)

/** Sifat satu layer. Boleh berubah sesudah [ImportSink.layer] (mis. grup yang tersembunyi baru diketahui belakangan). */
class ImportLayer(
    var name: String,
    var opacity: Float = 1f,
    /** Mode campur WInote (urutan sama dengan kunci PSD di Psd.kt). */
    var blend: Int = 0,
    var visible: Boolean = true,
    var clip: Boolean = false,
    var alphaLock: Boolean = false,
    var locked: Boolean = false,
    /** Nomor grup (1 = ImportDoc.groups[0]), 0 = tanpa grup. */
    var group: Int = 0,
    /**
     * Masker layer seukuran kanvas (0..255 per piksel, baris 0 = atas), hanya bila pembaca diminta menyimpan masker;
     * null = tanpa masker (atau maskernya sudah diterapkan ke piksel).
     */
    var mask: ByteArray? = null,
    /** false = masker dimatikan di berkas asal. */
    var maskVisible: Boolean = true,
)

interface ImportSink {
    /** Sekali sebelum layer pertama; [layers] = jumlah layer yang akan dikirim. */
    fun begin(doc: ImportDoc, layers: Int)

    /** Piksel satu layer: RGBA premultiplied, baris 0 = atas, w·h·4 byte. Buffer dipakai ulang oleh pengurai. */
    fun layer(info: ImportLayer, px: ByteArray)
}

// ================= PSD / PSB =================

object PsdReader {
    private val keys = arrayOf(
        "norm", "mul ", "dark", "idiv", "lbrn", "dkCl", "lite", "scrn", "div ", "lddg", "lgCl",
        "over", "sLit", "hLit", "vLit", "lLit", "pLit", "hMix", "diff", "smud", "fsub", "fdiv",
        "hue ", "sat ", "colr", "lum ",
    )

    /** Layer penyesuaian/isian Photoshop: tidak punya piksel sendiri, dilewati. */
    private val adjustKeys = setOf(
        "levl", "curv", "brit", "hue2", "hue ", "blnc", "vibA", "expA", "mixr", "clrL", "nvrt", "post",
        "thrs", "grdm", "selc", "phfl", "SoCo", "GdFl", "PtFl", "CgEd", "blwh", "brst",
    )

    /** Di PSB, blok tambahan ini memakai panjang 8 byte. */
    private val longKeys = setOf(
        "LMsk", "Lr16", "Lr32", "Layr", "Mt16", "Mt32", "Mtrn", "Alph", "FMsk", "lnk2", "FEid", "FXid", "PxSD",
    )

    private class Rec {
        var top = 0; var left = 0; var bottom = 0; var right = 0
        var ids = IntArray(0)
        var lens = LongArray(0)
        var key = "norm"
        var opacity = 255
        var clip = 0
        var flags = 0
        /** Isi blok 'lspf' (kunci layer Photoshop), -1 bila tidak ada. */
        var lock = -1
        var name = ""
        var section = 0
        var fill = 255
        var adjust = false
        var mTop = 0; var mLeft = 0; var mBottom = 0; var mRight = 0
        var mDefault = 255
        var mOn = false
        val w get() = right - left
        val h get() = bottom - top
    }

    /** [keepMasks] true: masker layer dikirim lewat [ImportLayer.mask] (tidak diterapkan ke piksel). */
    fun read(file: File, sink: ImportSink, keepMasks: Boolean = false) {
        RandomAccessFile(file, "r").use { raf ->
            val r = Reader(raf)
            if (r.str(4) != "8BPS") error("Bukan berkas PSD")
            val ver = r.u16()
            if (ver != 1 && ver != 2) error("Versi PSD tidak dikenal")
            val psb = ver == 2
            r.skip(6)
            val nch = r.u16()
            val h = r.u32().toInt()
            val w = r.u32().toInt()
            val depth = r.u16()
            val mode = r.u16()
            if (w <= 0 || h <= 0) error("Ukuran PSD tidak sah")
            if (depth != 8 && depth != 16) error("PSD $depth-bit belum didukung (pakai 8 atau 16 bit)")
            if (mode != 1 && mode != 3 && mode != 4) error("Mode warna PSD ini belum didukung (pakai RGB, Grayscale, atau CMYK)")
            r.skip(r.u32())
            val resLen = r.u32()
            val resEnd = r.pos + resLen
            var dpi = 0
            while (r.pos + 12 <= resEnd) {
                if (r.str(4) != "8BIM") break
                val id = r.u16()
                val nl = r.u8()
                r.skip(nl.toLong())
                if ((nl + 1) % 2 != 0) r.skip(1)
                val sz = r.u32()
                val dEnd = r.pos + sz
                if (id == 1005 && sz >= 16) {
                    val hRes = r.s32()
                    val unit = r.u16()
                    dpi = (hRes / 65536.0 * (if (unit == 2) 2.54 else 1.0)).roundToInt()
                }
                r.seek(dEnd + (sz and 1L))
            }
            r.seek(resEnd)
            val doc = ImportDoc(w, h)
            doc.dpi = dpi
            doc.bg = 0xFFFFFFFF.toInt()
            doc.bgVisible = false
            val lmLen = if (psb) r.u64() else r.u32()
            val lmEnd = r.pos + lmLen
            var emitted = 0
            if (lmLen > 0) {
                val liLen = if (psb) r.u64() else r.u32()
                if (liLen > 0) emitted = readLayers(r, psb, doc, w, h, depth, mode, sink, keepMasks)
            }
            if (emitted == 0) {
                r.seek(lmEnd)
                readMerged(r, psb, doc, w, h, nch, depth, mode, sink)
            }
        }
    }

    private fun readLayers(r: Reader, psb: Boolean, doc: ImportDoc, w: Int, h: Int, depth: Int, mode: Int, sink: ImportSink, keepMasks: Boolean): Int {
        val count = abs(r.s16())
        if (count == 0) return 0
        val recs = ArrayList<Rec>(count)
        for (i in 0 until count) {
            val rec = Rec()
            rec.top = r.s32(); rec.left = r.s32(); rec.bottom = r.s32(); rec.right = r.s32()
            val nc = r.u16()
            rec.ids = IntArray(nc)
            rec.lens = LongArray(nc)
            for (c in 0 until nc) {
                rec.ids[c] = r.s16()
                rec.lens[c] = if (psb) r.u64() else r.u32()
            }
            r.str(4)
            rec.key = r.str(4)
            rec.opacity = r.u8()
            rec.clip = r.u8()
            rec.flags = r.u8()
            r.skip(1)
            val extraLen = r.u32()
            val extraEnd = r.pos + extraLen
            val maskLen = r.u32()
            val maskEnd = r.pos + maskLen
            if (maskLen >= 18) {
                rec.mTop = r.s32(); rec.mLeft = r.s32(); rec.mBottom = r.s32(); rec.mRight = r.s32()
                rec.mDefault = r.u8()
                val mf = r.u8()
                // bit 1 = masker dimatikan
                rec.mOn = mf and 2 == 0
            }
            r.seek(maskEnd)
            r.skip(r.u32())
            val nl = r.u8()
            rec.name = String(r.bytes(nl), Charsets.ISO_8859_1)
            var used = 1 + nl
            while (used % 4 != 0) { r.skip(1); used++ }
            while (r.pos + 12 <= extraEnd) {
                val sig = r.str(4)
                if (sig != "8BIM" && sig != "8B64") break
                val k = r.str(4)
                val len = if (psb && k in longKeys) r.u64() else r.u32()
                val dEnd = r.pos + len
                when (k) {
                    "luni" -> {
                        val n = r.u32().toInt().coerceIn(0, 4096)
                        val sb = StringBuilder()
                        for (j in 0 until n) sb.append(r.u16().toChar())
                        rec.name = sb.toString().trimEnd('\u0000')
                    }
                    "lsct", "lsdk" -> rec.section = r.u32().toInt()
                    "iOpa" -> rec.fill = r.u8()
                    "lspf" -> rec.lock = r.u32().toInt()
                    else -> if (k in adjustKeys) rec.adjust = true
                }
                r.seek(dEnd)
                // Sebagian penulis tidak menggenapkan panjang blok: cari tanda tangan berikutnya.
                if (len % 2 == 1L && r.pos + 4 <= extraEnd) {
                    val save = r.pos
                    val s2 = r.str(4)
                    r.seek(if (s2 == "8BIM" || s2 == "8B64") save else save + 1)
                }
            }
            r.seek(extraEnd)
            recs.add(rec)
        }

        val infos = ArrayList<ImportLayer?>()
        val levels = ArrayList<ArrayList<Int>>()
        var topGroup = 0
        val total = recs.count { it.section == 0 && !it.adjust }
        sink.begin(doc, total)
        val px = ByteArray(w * h * 4)
        var emitted = 0
        for (rec in recs) {
            // Data kanal tiap layer selalu dibaca berurutan, termasuk layer yang dilewati.
            val planes = HashMap<Int, ByteArray>()
            for (c in rec.ids.indices) {
                val start = r.pos
                val id = rec.ids[c]
                val cw: Int
                val ch: Int
                if (id == -2) { cw = rec.mRight - rec.mLeft; ch = rec.mBottom - rec.mTop } else { cw = rec.w; ch = rec.h }
                val want = rec.section == 0 && !rec.adjust && id >= -2 && cw > 0 && ch > 0
                if (want && rec.lens[c] >= 2) {
                    val comp = r.u16()
                    planes[id] = decodeChannel(r, comp, cw, ch, depth, psb, rec.lens[c] - 2)
                }
                r.seek(start + rec.lens[c])
            }
            when (rec.section) {
                3 -> {
                    levels.add(ArrayList())
                    if (levels.size == 1) {
                        doc.groups.add(ImportGroup("Grup"))
                        topGroup = doc.groups.size
                    }
                }
                1, 2 -> {
                    if (levels.isEmpty()) continue
                    val members = levels.removeAt(levels.size - 1)
                    val hidden = rec.flags and 2 != 0
                    val op = rec.opacity / 255f
                    if (levels.isEmpty()) {
                        val g = doc.groups[topGroup - 1]
                        g.name = rec.name.ifBlank { "Grup" }
                        g.visible = !hidden
                        g.collapsed = rec.section == 2
                        for (m in members) infos[m]?.let { it.opacity *= op }
                        topGroup = 0
                    } else {
                        for (m in members) infos[m]?.let { if (hidden) it.visible = false; it.opacity *= op }
                        levels[levels.size - 1].addAll(members)
                    }
                }
                else -> {
                    if (rec.adjust) {
                        infos.add(null)
                        continue
                    }
                    java.util.Arrays.fill(px, 0)
                    val mk = planes[-2]
                    val keep = keepMasks && mk != null && rec.mRight > rec.mLeft && rec.mBottom > rec.mTop
                    val mOn = rec.mOn
                    if (keep) rec.mOn = false
                    compose(rec, planes, mode, w, h, px)
                    rec.mOn = mOn
                    val maskArr = if (keep) maskCanvas(rec, mk!!, w, h) else null
                    val info = ImportLayer(
                        name = rec.name.ifBlank { "Layer" },
                        opacity = rec.opacity / 255f * (rec.fill / 255f),
                        blend = keys.indexOf(rec.key).coerceAtLeast(0),
                        visible = rec.flags and 2 == 0,
                        clip = rec.clip != 0,
                        // Bit 0 'flags' juga berarti kunci transparansi, tetapi sebagian penulis (mis. ImageMagick)
                        // selalu menyalakannya; yang dipercaya hanya blok 'lspf' yang selalu ditulis Photoshop.
                        alphaLock = rec.lock != -1 && (rec.lock and 1 != 0 || rec.lock < 0),
                        locked = rec.lock != -1 && (rec.lock and 2 != 0 || rec.lock < 0),
                        group = topGroup,
                        mask = maskArr,
                        maskVisible = mOn,
                    )
                    sink.layer(info, px)
                    infos.add(info)
                    levels.lastOrNull()?.add(infos.size - 1)
                    emitted++
                }
            }
        }
        if (recs.any { it.adjust }) doc.notes.add("Layer penyesuaian Photoshop dilewati")
        return emitted
    }

    /** Masker layer PSD dipetakan ke seluruh kanvas (di luar kotak masker = warna bawaannya). */
    private fun maskCanvas(rec: Rec, mk: ByteArray, w: Int, h: Int): ByteArray {
        val out = ByteArray(w * h)
        java.util.Arrays.fill(out, rec.mDefault.toByte())
        val mw = rec.mRight - rec.mLeft
        val mh = rec.mBottom - rec.mTop
        for (my in 0 until mh) {
            val y = rec.mTop + my
            if (y !in 0 until h) continue
            for (mx in 0 until mw) {
                val x = rec.mLeft + mx
                if (x !in 0 until w) continue
                out[y * w + x] = mk[my * mw + mx]
            }
        }
        return out
    }

    private fun compose(rec: Rec, planes: Map<Int, ByteArray>, mode: Int, w: Int, h: Int, px: ByteArray) {
        val rw = rec.w
        val rh = rec.h
        if (rw <= 0 || rh <= 0) return
        val c0 = planes[0]
        val c1 = planes[1]
        val c2 = planes[2]
        val c3 = planes[3]
        val al = planes[-1]
        val mk = if (rec.mOn) planes[-2] else null
        val mw = rec.mRight - rec.mLeft
        for (y in 0 until rh) {
            val cy = rec.top + y
            if (cy < 0 || cy >= h) continue
            for (x in 0 until rw) {
                val cx = rec.left + x
                if (cx < 0 || cx >= w) continue
                val i = y * rw + x
                var a = if (al != null) al[i].toInt() and 0xFF else 255
                if (rec.mOn) {
                    val my = cy - rec.mTop
                    val mx = cx - rec.mLeft
                    val mv = if (mk != null && mx in 0 until mw && my in 0 until (rec.mBottom - rec.mTop)) mk[my * mw + mx].toInt() and 0xFF else rec.mDefault
                    a = a * mv / 255
                }
                if (a == 0) continue
                var rr: Int
                var gg: Int
                var bb: Int
                when (mode) {
                    1 -> { rr = c0?.get(i)?.toInt()?.and(0xFF) ?: 0; gg = rr; bb = rr }
                    4 -> {
                        // CMYK di PSD disimpan terbalik (255 = tanpa tinta).
                        val k = c3?.get(i)?.toInt()?.and(0xFF) ?: 255
                        rr = (c0?.get(i)?.toInt()?.and(0xFF) ?: 255) * k / 255
                        gg = (c1?.get(i)?.toInt()?.and(0xFF) ?: 255) * k / 255
                        bb = (c2?.get(i)?.toInt()?.and(0xFF) ?: 255) * k / 255
                    }
                    else -> {
                        rr = c0?.get(i)?.toInt()?.and(0xFF) ?: 0
                        gg = c1?.get(i)?.toInt()?.and(0xFF) ?: 0
                        bb = c2?.get(i)?.toInt()?.and(0xFF) ?: 0
                    }
                }
                val o = (cy * w + cx) * 4
                px[o] = ((rr * a + 127) / 255).toByte()
                px[o + 1] = ((gg * a + 127) / 255).toByte()
                px[o + 2] = ((bb * a + 127) / 255).toByte()
                px[o + 3] = a.toByte()
            }
        }
    }

    private fun readMerged(r: Reader, psb: Boolean, doc: ImportDoc, w: Int, h: Int, nch: Int, depth: Int, mode: Int, sink: ImportSink) {
        val comp = r.u16()
        val color = if (mode == 1) 1 else if (mode == 4) 4 else 3
        val bpc = depth / 8
        val planes = ArrayList<ByteArray>()
        when (comp) {
            0 -> repeat(nch) { planes.add(to8(r.bytes(w * h * bpc), depth)) }
            1 -> {
                val counts = IntArray(nch * h) { if (psb) r.u32().toInt() else r.u16() }
                for (c in 0 until nch) {
                    val out = ByteArray(w * h * bpc)
                    for (y in 0 until h) unpackBits(r.bytes(counts[c * h + y]), out, y * w * bpc, w * bpc)
                    planes.add(to8(out, depth))
                }
            }
            else -> error("Kompresi gambar PSD ini belum didukung")
        }
        val rec = Rec()
        rec.right = w; rec.bottom = h
        val map = HashMap<Int, ByteArray>()
        for (c in 0 until minOf(color, planes.size)) map[c] = planes[c]
        if (planes.size > color) map[-1] = planes[color]
        sink.begin(doc, 1)
        val px = ByteArray(w * h * 4)
        compose(rec, map, mode, w, h, px)
        sink.layer(ImportLayer("Gambar"), px)
    }

    private fun decodeChannel(r: Reader, comp: Int, w: Int, h: Int, depth: Int, psb: Boolean, len: Long): ByteArray {
        val bpc = depth / 8
        val rowBytes = w * bpc
        val out = ByteArray(rowBytes * h)
        when (comp) {
            0 -> r.read(out, 0, out.size)
            1 -> {
                val counts = IntArray(h) { if (psb) r.u32().toInt() else r.u16() }
                var buf = ByteArray(0)
                for (y in 0 until h) {
                    val n = counts[y]
                    if (buf.size < n) buf = ByteArray(n)
                    r.read(buf, 0, n)
                    unpackBits(buf, out, y * rowBytes, rowBytes, n)
                }
            }
            2, 3 -> {
                val z = r.bytes(len.toInt())
                val inf = Inflater()
                inf.setInput(z)
                var got = 0
                while (got < out.size && !inf.finished()) {
                    val n = inf.inflate(out, got, out.size - got)
                    if (n == 0 && (inf.needsInput() || inf.needsDictionary())) break
                    got += n
                }
                inf.end()
                if (comp == 3) {
                    if (depth == 8) {
                        for (y in 0 until h) {
                            val o = y * rowBytes
                            for (x in 1 until w) out[o + x] = (out[o + x] + out[o + x - 1]).toByte()
                        }
                    } else {
                        for (y in 0 until h) {
                            val o = y * rowBytes
                            var prev = ((out[o].toInt() and 0xFF) shl 8) or (out[o + 1].toInt() and 0xFF)
                            for (x in 1 until w) {
                                val p = o + x * 2
                                val v = ((((out[p].toInt() and 0xFF) shl 8) or (out[p + 1].toInt() and 0xFF)) + prev) and 0xFFFF
                                out[p] = (v shr 8).toByte()
                                out[p + 1] = v.toByte()
                                prev = v
                            }
                        }
                    }
                }
            }
            else -> error("Kompresi kanal PSD tidak dikenal")
        }
        return to8(out, depth)
    }

    /** 16 bit → 8 bit: ambil byte atas (big-endian). */
    private fun to8(src: ByteArray, depth: Int): ByteArray {
        if (depth == 8) return src
        val out = ByteArray(src.size / 2)
        for (i in out.indices) out[i] = src[i * 2]
        return out
    }

    private fun unpackBits(src: ByteArray, out: ByteArray, off: Int, n: Int, srcLen: Int = src.size) {
        var i = 0
        var o = off
        val end = off + n
        while (i < srcLen && o < end) {
            val c = src[i++].toInt()
            if (c >= 0) {
                val cnt = minOf(c + 1, end - o, srcLen - i)
                System.arraycopy(src, i, out, o, cnt)
                i += c + 1
                o += cnt
            } else if (c != -128) {
                val cnt = minOf(1 - c, end - o)
                if (i >= srcLen) break
                val v = src[i++]
                java.util.Arrays.fill(out, o, o + cnt, v)
                o += cnt
            }
        }
    }

    /** Pembaca big-endian dengan penyangga, di atas RandomAccessFile. */
    private class Reader(val f: RandomAccessFile) {
        private val buf = ByteArray(64 * 1024)
        private var bufPos = 0L
        private var bufLen = 0
        var pos = 0L
            private set

        private fun fill() {
            f.seek(pos)
            bufPos = pos
            bufLen = maxOf(0, f.read(buf, 0, buf.size))
        }

        fun u8(): Int {
            if (pos < bufPos || pos >= bufPos + bufLen) fill()
            if (bufLen <= 0) error("Berkas PSD terpotong")
            val v = buf[(pos - bufPos).toInt()].toInt() and 0xFF
            pos++
            return v
        }

        fun u16(): Int = (u8() shl 8) or u8()
        fun s16(): Int = u16().toShort().toInt()
        fun u32(): Long = ((u16().toLong() shl 16) or u16().toLong())
        fun s32(): Int = u32().toInt()
        fun u64(): Long = (u32() shl 32) or u32()
        fun str(n: Int): String = String(bytes(n), Charsets.ISO_8859_1)
        fun skip(n: Long) { pos += n }
        fun seek(p: Long) { pos = p }

        fun read(dst: ByteArray, off: Int, n: Int) {
            var o = off
            var left = n
            while (left > 0) {
                if (pos < bufPos || pos >= bufPos + bufLen) fill()
                if (bufLen <= 0) error("Berkas PSD terpotong")
                val k = minOf(left, (bufPos + bufLen - pos).toInt())
                System.arraycopy(buf, (pos - bufPos).toInt(), dst, o, k)
                o += k
                left -= k
                pos += k
            }
        }

        fun bytes(n: Int): ByteArray = ByteArray(n).also { read(it, 0, n) }
    }
}

// ================= .procreate =================

object ProcreateReader {
    /** Mode campur Procreate → mode campur WInote (indeks kunci PSD). */
    private fun blendOf(p: Int): Int = when (p) {
        1 -> 1; 2 -> 7; 3 -> 9; 4 -> 6; 5 -> 19; 6 -> 18; 7 -> 20; 8 -> 4; 9 -> 8; 10 -> 3
        11 -> 11; 12 -> 13; 13 -> 24; 14 -> 25; 15 -> 22; 16 -> 23; 17 -> 12; 19 -> 2; 20 -> 17
        21 -> 14; 22 -> 15; 23 -> 16; 24 -> 10; 25 -> 5; 26 -> 21
        else -> 0
    }

    private class Tile(val col: Int, val row: Int, val name: String, val lz4: Boolean)

    private class Item(val dict: Map<String, Any?>, val group: Int, val hidden: Boolean, val opacity: Float)

    /** [keepMasks] true: masker layer dikirim lewat [ImportLayer.mask] (tidak diterapkan ke piksel). */
    fun read(file: File, sink: ImportSink, keepMasks: Boolean = false) {
        ZipFile(file).use { zip ->
            val docEntry = zip.getEntry("Document.archive") ?: error("Bukan berkas .procreate")
            @Suppress("UNCHECKED_CAST")
            val plist = BPlist.parse(zip.getInputStream(docEntry).use { it.readBytes() }) as? Map<String, Any?>
                ?: error("Document.archive tidak bisa dibaca")
            val ns = NsArchive(plist)
            val root = ns.root() ?: error("Document.archive tidak lengkap")
            val sizeStr = ns.str(root["size"]) ?: error("Ukuran kanvas tidak ditemukan")
            val nums = Regex("-?\\d+(\\.\\d+)?").findAll(sizeStr).map { it.value.toDouble().roundToInt() }.toList()
            if (nums.size < 2) error("Ukuran kanvas tidak sah")
            val w = nums[0]
            val h = nums[1]
            val ts = ns.num(root["tileSize"])?.toInt() ?: 256
            if (w <= 0 || h <= 0 || ts <= 0) error("Ukuran kanvas tidak sah")
            val doc = ImportDoc(w, h)
            doc.name = ns.str(root["name"])
            doc.dpi = ns.num(root["SilicaDocumentArchiveDPIKey"])?.roundToInt() ?: 0
            (ns.deref(root["backgroundColor"]) as? ByteArray)?.takeIf { it.size >= 16 }?.let { b ->
                fun f(i: Int) = java.nio.ByteBuffer.wrap(b, i * 4, 4).order(java.nio.ByteOrder.LITTLE_ENDIAN).float
                fun c(v: Float) = (v.coerceIn(0f, 1f) * 255f + 0.5f).toInt()
                doc.bg = (0xFF shl 24) or (c(f(0)) shl 16) or (c(f(1)) shl 8) or c(f(2))
            }
            doc.bgVisible = ns.bool(root["backgroundHidden"]) != true
            val flipH = ns.bool(root["flippedHorizontally"]) == true
            val flipV = ns.bool(root["flippedVertically"]) == true
            (ns.deref(root["colorProfile"]) as? Map<*, *>)?.let { cp ->
                @Suppress("UNCHECKED_CAST")
                val name = ns.str((cp as Map<String, Any?>)["SiColorProfileArchiveICCNameKey"]) ?: ""
                if (name.contains("P3", ignoreCase = true)) doc.p3 = true
            }

            // Ubin per UUID layer.
            val tiles = HashMap<String, ArrayList<Tile>>()
            val tileRe = Regex("^([^/]+)/(\\d+)~(\\d+)\\.(chunk|lz4)$")
            for (e in zip.entries()) {
                val m = tileRe.find(e.name) ?: continue
                tiles.getOrPut(m.groupValues[1]) { ArrayList() }
                    .add(Tile(m.groupValues[2].toInt(), m.groupValues[3].toInt(), e.name, m.groupValues[4] == "lz4"))
            }

            // Hierarki disimpan dari atas ke bawah; dikirim dari bawah ke atas. Grup bertingkat dilebur ke grup teratas.
            val items = ArrayList<Item>()
            fun walk(list: List<Any?>, group: Int, hidden: Boolean, opacity: Float) {
                for (o in list.asReversed()) {
                    @Suppress("UNCHECKED_CAST")
                    val d = o as? Map<String, Any?> ?: continue
                    when (ns.className(d)) {
                        "SilicaGroup" -> {
                            val gHidden = ns.bool(d["isHidden"]) == true
                            val gOp = ns.num(d["opacity"])?.toFloat() ?: 1f
                            if (group == 0) {
                                doc.groups.add(ImportGroup(ns.str(d["name"]) ?: "Grup", !gHidden, ns.bool(d["isCollapsed"]) == true))
                                walk(ns.list(d["children"]), doc.groups.size, hidden, opacity * gOp)
                            } else {
                                walk(ns.list(d["children"]), group, hidden || gHidden, opacity * gOp)
                            }
                        }
                        else -> {
                            val type = ns.num(d["type"])?.toInt() ?: 0
                            if (type == 0) items.add(Item(d, group, hidden, opacity))
                        }
                    }
                }
            }
            walk(ns.list(root["unwrappedLayers"]), 0, false, 1f)
            @Suppress("UNCHECKED_CAST")
            val composite = ns.deref(root["composite"]) as? Map<String, Any?>
            if (items.isEmpty() && composite == null) error("Tidak ada layer di berkas ini")

            val cols = (w + ts - 1) / ts
            val rows = (h + ts - 1) / ts
            val px = ByteArray(w * h * 4)
            var mask: ByteArray? = null
            fun paint(uuid: String, bpp: Int, out: ByteArray) {
                for (t in tiles[uuid].orEmpty()) {
                    if (t.col >= cols || t.row >= rows) continue
                    val tw = if (t.col == cols - 1) w - t.col * ts else ts
                    val th = if (t.row == rows - 1) h - t.row * ts else ts
                    val raw = zip.getInputStream(zip.getEntry(t.name)).use { it.readBytes() }
                    val need = tw * th * bpp
                    val data = if (t.lz4) Lz4Apple.decompress(raw, need) else ByteArray(need).also { Lzo.decompress(raw, it) }
                    for (dy in 0 until th) {
                        val yb = t.row * ts + dy
                        // Baris ubin dihitung dari bawah kanvas (kecuali kanvas dibalik tegak).
                        val y = if (flipV) yb else h - 1 - yb
                        if (y !in 0 until h) continue
                        for (dx in 0 until tw) {
                            val xb = t.col * ts + dx
                            val x = if (flipH) w - 1 - xb else xb
                            if (x !in 0 until w) continue
                            val si = (dy * tw + dx) * bpp
                            if (bpp == 4) {
                                System.arraycopy(data, si, out, (y * w + x) * 4, 4)
                            } else {
                                out[y * w + x] = data[si]
                            }
                        }
                    }
                }
            }

            if (items.isEmpty()) {
                sink.begin(doc, 1)
                paint(ns.str(composite!!["UUID"]) ?: "", 4, px)
                sink.layer(ImportLayer("Gambar"), px)
                doc.notes.add("Layer tidak terbaca; yang diimpor gambar gabungannya")
                return
            }
            sink.begin(doc, items.size)
            for (item in items) {
                val d = item.dict
                java.util.Arrays.fill(px, 0)
                paint(ns.str(d["UUID"]) ?: "", 4, px)
                @Suppress("UNCHECKED_CAST")
                val md = ns.deref(d["mask"]) as? Map<String, Any?>
                var keptMask: ByteArray? = null
                if (md != null && keepMasks) {
                    val m = ByteArray(w * h)
                    java.util.Arrays.fill(m, 0xFF.toByte())
                    paint(ns.str(md["UUID"]) ?: "", 1, m)
                    keptMask = m
                } else if (md != null && ns.bool(md["hidden"]) != true) {
                    val m = mask ?: ByteArray(w * h).also { mask = it }
                    java.util.Arrays.fill(m, 0xFF.toByte())
                    paint(ns.str(md["UUID"]) ?: "", 1, m)
                    for (i in 0 until w * h) {
                        val mv = m[i].toInt() and 0xFF
                        if (mv == 255) continue
                        val o = i * 4
                        for (c in 0 until 4) px[o + c] = (((px[o + c].toInt() and 0xFF) * mv + 127) / 255).toByte()
                    }
                }
                var blend = 0
                for (k in arrayOf("extendedBlend2", "extendedBlend", "blend")) {
                    val v = ns.num(d[k])?.toInt() ?: continue
                    blend = blendOf(v)
                    break
                }
                val info = ImportLayer(
                    name = ns.str(d["name"]) ?: "Layer",
                    opacity = (ns.num(d["opacity"])?.toFloat() ?: 1f) * item.opacity,
                    blend = blend,
                    visible = ns.bool(d["hidden"]) != true && !item.hidden,
                    clip = ns.bool(d["clipped"]) == true,
                    alphaLock = ns.bool(d["preserve"]) == true,
                    locked = ns.bool(d["locked"]) == true,
                    group = item.group,
                    mask = keptMask,
                    maskVisible = md == null || ns.bool(md["hidden"]) != true,
                )
                sink.layer(info, px)
            }
        }
    }
}

/** Pembaca NSKeyedArchiver (hasil BPlist.parse). */
class NsArchive(plist: Map<String, Any?>) {
    @Suppress("UNCHECKED_CAST")
    private val objects = plist["\$objects"] as? List<Any?> ?: emptyList()
    @Suppress("UNCHECKED_CAST")
    private val top = plist["\$top"] as? Map<String, Any?> ?: emptyMap()

    @Suppress("UNCHECKED_CAST")
    fun root(): Map<String, Any?>? = deref(top["root"]) as? Map<String, Any?>

    fun deref(v: Any?): Any? = if (v is BPlist.Uid) (if (v.v == 0) null else objects.getOrNull(v.v)) else v

    fun str(v: Any?): String? = when (val x = deref(v)) {
        is String -> if (x == "\$null") null else x
        is Map<*, *> -> deref(x["NS.string"]) as? String
        else -> null
    }

    fun num(v: Any?): Double? = when (val x = deref(v)) {
        is Number -> x.toDouble()
        is Boolean -> if (x) 1.0 else 0.0
        else -> null
    }

    fun bool(v: Any?): Boolean? = when (val x = deref(v)) {
        is Boolean -> x
        is Number -> x.toDouble() != 0.0
        else -> null
    }

    fun list(v: Any?): List<Any?> {
        val x = deref(v)
        val raw = when (x) {
            is Map<*, *> -> x["NS.objects"] as? List<*>
            is List<*> -> x
            else -> null
        } ?: return emptyList()
        return raw.map { deref(it) }
    }

    fun className(d: Map<String, Any?>): String? = (deref(d["\$class"]) as? Map<*, *>)?.get("\$classname") as? String
}

/** Pengurai property list biner Apple (bplist00). */
object BPlist {
    class Uid(val v: Int)

    fun parse(b: ByteArray): Any? {
        if (b.size < 40 || String(b, 0, 8, Charsets.ISO_8859_1) != "bplist00") error("Bukan bplist")
        val t = b.size - 32
        val offSize = b[t + 6].toInt() and 0xFF
        val refSize = b[t + 7].toInt() and 0xFF
        val numObj = be(b, t + 8, 8).toInt()
        val topObj = be(b, t + 16, 8).toInt()
        val offTable = be(b, t + 24, 8).toInt()
        val offsets = IntArray(numObj) { be(b, offTable + it * offSize, offSize).toInt() }
        val cache = arrayOfNulls<Any>(numObj)
        val done = BooleanArray(numObj)

        fun obj(i: Int, depth: Int): Any? {
            if (i !in 0 until numObj) return null
            if (done[i]) return cache[i]
            if (depth > 512) error("bplist terlalu dalam")
            var o = offsets[i]
            val m = b[o].toInt() and 0xFF
            val type = m shr 4
            val info = m and 0xF
            o++
            fun count(): Int {
                if (info != 0xF) return info
                val im = b[o].toInt() and 0xFF
                val n = 1 shl (im and 0xF)
                val v = be(b, o + 1, n).toInt()
                o += 1 + n
                return v
            }
            val v: Any? = when (type) {
                0x0 -> when (info) { 0x8 -> false; 0x9 -> true; else -> null }
                0x1 -> {
                    val n = 1 shl info
                    if (n == 8) be(b, o, 8) else be(b, o, n)
                }
                0x2 -> {
                    val n = 1 shl info
                    if (n == 4) java.lang.Float.intBitsToFloat(be(b, o, 4).toInt()).toDouble() else java.lang.Double.longBitsToDouble(be(b, o, 8))
                }
                0x3 -> java.lang.Double.longBitsToDouble(be(b, o, 8))
                0x4 -> { val n = count(); b.copyOfRange(o, o + n) }
                0x5 -> { val n = count(); String(b, o, n, Charsets.US_ASCII) }
                0x6 -> { val n = count(); String(b, o, n * 2, Charsets.UTF_16BE) }
                0x8 -> Uid(be(b, o, info + 1).toInt())
                0xA, 0xC -> {
                    val n = count()
                    val refs = IntArray(n) { be(b, o + it * refSize, refSize).toInt() }
                    refs.map { obj(it, depth + 1) }
                }
                0xD -> {
                    val n = count()
                    val ks = IntArray(n) { be(b, o + it * refSize, refSize).toInt() }
                    val vs = IntArray(n) { be(b, o + (n + it) * refSize, refSize).toInt() }
                    val map = LinkedHashMap<String, Any?>()
                    for (k in 0 until n) map[obj(ks[k], depth + 1)?.toString() ?: ""] = obj(vs[k], depth + 1)
                    map
                }
                else -> null
            }
            cache[i] = v
            done[i] = true
            return v
        }
        return obj(topObj, 0)
    }

    private fun be(b: ByteArray, o: Int, n: Int): Long {
        var v = 0L
        for (k in 0 until n) v = (v shl 8) or (b[o + k].toLong() and 0xFF)
        return v
    }
}

/** Pengurai LZO1X (ubin .chunk di berkas .procreate). */
object Lzo {
    /** Mengembalikan jumlah byte hasil di [dst]. */
    fun decompress(src: ByteArray, dst: ByteArray): Int {
        var ip = 0
        var op = 0
        var t: Int
        var m: Int
        fun u(i: Int) = src[i].toInt() and 0xFF
        fun copyMatch(from: Int, n: Int) {
            var s = from
            dst[op++] = dst[s++]
            dst[op++] = dst[s++]
            var k = n
            do { dst[op++] = dst[s++] } while (--k > 0)
        }
        val loop = 0
        val firstLit = 1
        val match = 2
        val matchDone = 3
        val matchNext = 4
        var st: Int
        t = 0
        if (u(0) > 17) {
            t = u(ip++) - 17
            if (t < 4) {
                st = matchNext
            } else {
                System.arraycopy(src, ip, dst, op, t)
                ip += t; op += t
                st = firstLit
            }
        } else {
            st = loop
        }
        while (true) {
            when (st) {
                loop -> {
                    t = u(ip++)
                    if (t >= 16) { st = match; continue }
                    if (t == 0) {
                        while (src[ip].toInt() == 0) { t += 255; ip++ }
                        t += 15 + u(ip++)
                    }
                    System.arraycopy(src, ip, dst, op, t + 3)
                    ip += t + 3; op += t + 3
                    st = firstLit
                }
                firstLit -> {
                    t = u(ip++)
                    if (t >= 16) { st = match; continue }
                    m = op - (1 + 0x0800) - (t shr 2) - (u(ip++) shl 2)
                    dst[op++] = dst[m++]; dst[op++] = dst[m++]; dst[op++] = dst[m]
                    st = matchDone
                }
                match -> {
                    if (t >= 64) {
                        m = op - 1 - ((t shr 2) and 7) - (u(ip++) shl 3)
                        t = (t shr 5) - 1
                        copyMatch(m, t)
                    } else if (t >= 32) {
                        t = t and 31
                        if (t == 0) {
                            while (src[ip].toInt() == 0) { t += 255; ip++ }
                            t += 31 + u(ip++)
                        }
                        m = op - 1 - ((u(ip) shr 2) + (u(ip + 1) shl 6))
                        ip += 2
                        copyMatch(m, t)
                    } else if (t >= 16) {
                        m = op - ((t and 8) shl 11)
                        t = t and 7
                        if (t == 0) {
                            while (src[ip].toInt() == 0) { t += 255; ip++ }
                            t += 7 + u(ip++)
                        }
                        m -= (u(ip) shr 2) + (u(ip + 1) shl 6)
                        ip += 2
                        if (m == op) return op
                        m -= 0x4000
                        copyMatch(m, t)
                    } else {
                        m = op - 1 - (t shr 2) - (u(ip++) shl 2)
                        dst[op++] = dst[m++]; dst[op++] = dst[m]
                    }
                    st = matchDone
                }
                matchDone -> {
                    t = u(ip - 2) and 3
                    st = if (t == 0) loop else matchNext
                }
                else -> {
                    repeat(t) { dst[op++] = src[ip++] }
                    t = u(ip++)
                    st = match
                }
            }
        }
    }
}

/** Pengurai LZ4 berbingkai Apple ("bv41"/"bv4-"/"bv4$"), dipakai ubin .lz4 di Procreate baru. */
object Lz4Apple {
    fun decompress(src: ByteArray, expected: Int): ByteArray {
        val out = ByteArray(expected)
        var ip = 0
        var op = 0
        fun le32(i: Int) = (src[i].toInt() and 0xFF) or ((src[i + 1].toInt() and 0xFF) shl 8) or
            ((src[i + 2].toInt() and 0xFF) shl 16) or ((src[i + 3].toInt() and 0xFF) shl 24)
        while (ip + 4 <= src.size) {
            val magic = String(src, ip, 4, Charsets.ISO_8859_1)
            when (magic) {
                "bv41" -> {
                    val dec = le32(ip + 4)
                    val enc = le32(ip + 8)
                    ip += 12
                    val n = block(src, ip, enc, out, op)
                    if (n != dec) error("Ubin LZ4 rusak")
                    ip += enc
                    op += n
                }
                "bv4-" -> {
                    val dec = le32(ip + 4)
                    ip += 8
                    System.arraycopy(src, ip, out, op, dec)
                    ip += dec
                    op += dec
                }
                "bv4$" -> return out
                else -> {
                    // Bukan bingkai Apple: anggap satu blok LZ4 polos.
                    block(src, 0, src.size, out, 0)
                    return out
                }
            }
        }
        return out
    }

    /** Blok LZ4; pencocokan boleh merujuk keluaran blok sebelumnya (kamus = isi [dst] sebelum [dOff]). */
    private fun block(src: ByteArray, sOff: Int, sLen: Int, dst: ByteArray, dOff: Int): Int {
        var ip = sOff
        val end = sOff + sLen
        var op = dOff
        fun u(i: Int) = src[i].toInt() and 0xFF
        while (ip < end) {
            val token = u(ip++)
            var lit = token ushr 4
            if (lit == 15) {
                var b: Int
                do { b = u(ip++); lit += b } while (b == 255)
            }
            System.arraycopy(src, ip, dst, op, lit)
            ip += lit
            op += lit
            if (ip >= end) break
            val off = u(ip) or (u(ip + 1) shl 8)
            ip += 2
            var ml = token and 15
            if (ml == 15) {
                var b: Int
                do { b = u(ip++); ml += b } while (b == 255)
            }
            ml += 4
            var mpos = op - off
            repeat(ml) { dst[op++] = dst[mpos++] }
        }
        return op - dOff
    }
}
