import id.goresan.engine.ImportDoc
import id.goresan.engine.ImportLayer
import id.goresan.engine.ImportSink
import id.goresan.engine.ProcreateReader
import id.goresan.engine.Psd
import id.goresan.engine.PsdReader
import java.awt.image.BufferedImage
import java.io.File
import java.nio.ByteBuffer
import javax.imageio.ImageIO

/** Menyimpan tiap layer hasil impor: <tag>.<i>.rgba (premultiplied) dan .png (untuk dilihat). */
class Collect(private val out: File, private val tag: String) : ImportSink {
    lateinit var doc: ImportDoc
    val infos = ArrayList<ImportLayer>()

    override fun begin(doc: ImportDoc, layers: Int) {
        this.doc = doc
        println("$tag: ${doc.w}x${doc.h} dpi=${doc.dpi} layer=$layers latar=${doc.bg?.let { Integer.toHexString(it) }} latarTampil=${doc.bgVisible} p3=${doc.p3} nama=${doc.name}")
    }

    override fun layer(info: ImportLayer, px: ByteArray) {
        val i = infos.size
        File(out, "$tag.$i.rgba").writeBytes(px)
        val img = BufferedImage(doc.w, doc.h, BufferedImage.TYPE_INT_ARGB)
        for (y in 0 until doc.h) for (x in 0 until doc.w) {
            val o = (y * doc.w + x) * 4
            val a = px[o + 3].toInt() and 0xFF
            fun un(c: Int) = if (a == 0) 0 else minOf(255, ((px[o + c].toInt() and 0xFF) * 255 + a / 2) / a)
            img.setRGB(x, y, (a shl 24) or (un(0) shl 16) or (un(1) shl 8) or un(2))
        }
        ImageIO.write(img, "png", File(out, "$tag.$i.png"))
        infos.add(info)
    }

    fun report() {
        infos.forEachIndexed { i, l ->
            println("  [$i] '${l.name}' opasitas=${"%.3f".format(l.opacity)} campur=${l.blend} tampil=${l.visible} klip=${l.clip} kunciAlfa=${l.alphaLock} kunci=${l.locked} grup=${l.group}")
        }
        doc.groups.forEachIndexed { i, g -> println("  grup ${i + 1}: '${g.name}' tampil=${g.visible} lipat=${g.collapsed}") }
        doc.notes.forEach { println("  catatan: $it") }
    }
}

fun main(args: Array<String>) {
    val fx = File(args[0])
    val out = File(args[1]).apply { mkdirs() }
    var fail = 0
    for (f in fx.listFiles()!!.sortedBy { it.name }) {
        val tag = f.nameWithoutExtension
        val c = Collect(out, tag)
        try {
            when (f.extension.lowercase()) {
                "psd", "psb" -> PsdReader.read(f, c)
                "procreate" -> ProcreateReader.read(f, c)
                else -> continue
            }
            c.report()
        } catch (e: Throwable) {
            fail++
            println("GAGAL $tag: $e")
            e.printStackTrace(System.out)
        }
    }
    // Pulang-pergi: tulis PSD dengan Psd.write (seperti Ekspor PSD), baca lagi, harus sama persis.
    val w = 97
    val h = 61
    fun layerPx(seed: Int): ByteBuffer {
        val b = ByteBuffer.allocate(w * h * 4)
        for (y in 0 until h) for (x in 0 until w) {
            val a = if ((x + y + seed) % 7 == 0) 0 else if ((x * seed + y) % 5 == 0) 128 else 255
            val r = (x * 3 + seed * 50) % 256
            val g = (y * 4 + seed * 30) % 256
            val bl = (x + y + seed * 20) % 256
            b.put((r * a / 255).toByte()); b.put((g * a / 255).toByte()); b.put((bl * a / 255).toByte()); b.put(a.toByte())
        }
        b.position(0)
        return b
    }
    val srcs = listOf(layerPx(1), layerPx(2), layerPx(3))
    val outs = listOf(
        Psd.LayerOut("Bawah", 1f, 0, true, false, false) { srcs[0] },
        Psd.LayerOut("</Layer group>", 1f, 0, true, false, false, section = 3) { ByteBuffer.allocate(0) },
        Psd.LayerOut("Dalam grup", 0.5f, 1, false, true, false) { srcs[1] },
        Psd.LayerOut("Grup Satu", 1f, 0, false, false, false, section = 2) { ByteBuffer.allocate(0) },
        Psd.LayerOut("Atas klip", 1f, 11, true, false, true) { srcs[2] },
    )
    val pf = File(out, "pulang-pergi.psd")
    Psd.write(pf, w, h, outs, srcs[0], 300)
    val c = Collect(out, "pulang-pergi")
    PsdReader.read(pf, c)
    c.report()
    for ((i, s) in srcs.withIndex()) {
        val got = File(out, "pulang-pergi.$i.rgba").readBytes()
        var maxd = 0
        for (k in got.indices) maxd = maxOf(maxd, kotlin.math.abs((got[k].toInt() and 0xFF) - (s.get(k).toInt() and 0xFF)))
        println("  pulang-pergi layer $i: selisih maks $maxd")
        if (maxd > 2) fail++
    }
    val g = c.doc.groups.firstOrNull()
    if (c.infos.size != 3 || g == null || g.name != "Grup Satu" || g.visible || c.infos[1].group != 1 || !c.infos[2].clip ||
        c.infos[1].blend != 1 || c.infos[1].visible || !c.infos[1].alphaLock || c.doc.dpi != 300
    ) {
        println("GAGAL pulang-pergi: sifat layer/grup tidak sesuai")
        fail++
    }
    // Masker layer: PSD pulang-pergi dengan masker; dibaca dengan masker dipisah dan dengan masker diterapkan.
    val maskBuf = ByteBuffer.allocate(w * h * 4)
    val maskVal = ByteArray(w * h)
    for (y in 0 until h) for (x in 0 until w) {
        val v = if (x < w / 3) 0 else if (x < 2 * w / 3) (y * 255 / (h - 1)) else 255
        maskVal[y * w + x] = v.toByte()
        maskBuf.put(v.toByte()); maskBuf.put(v.toByte()); maskBuf.put(v.toByte()); maskBuf.put(255.toByte())
    }
    maskBuf.position(0)
    val mf = File(out, "masker.psd")
    Psd.write(mf, w, h, listOf(
        Psd.LayerOut("Bermasker", 1f, 0, true, false, false, mask = { maskBuf.position(0); maskBuf }) { srcs[0].position(0); srcs[0] },
        Psd.LayerOut("Masker mati", 1f, 0, true, false, false, mask = { maskBuf.position(0); maskBuf }, maskEnabled = false) { srcs[1].position(0); srcs[1] },
    ), srcs[0], 72)
    val keep = Collect(out, "masker-pisah")
    PsdReader.read(mf, keep, keepMasks = true)
    keep.report()
    val m0 = keep.infos.getOrNull(0)?.mask
    val m1 = keep.infos.getOrNull(1)
    if (m0 == null || !m0.contentEquals(maskVal) || m1?.mask == null || m1.maskVisible || keep.infos[0].maskVisible != true) {
        println("GAGAL masker PSD dipisah: masker=${m0 != null} sama=${m0?.contentEquals(maskVal)} mati=${m1?.maskVisible}")
        fail++
    } else println("  masker PSD dipisah: sama persis")
    val p0 = File(out, "masker-pisah.0.rgba").readBytes()
    var md0 = 0
    for (k in p0.indices) md0 = maxOf(md0, kotlin.math.abs((p0[k].toInt() and 0xFF) - (srcs[0].get(k).toInt() and 0xFF)))
    println("  masker-pisah layer 0 (tanpa masker): selisih maks $md0")
    if (md0 > 2) fail++
    val baked = Collect(out, "masker-terap")
    PsdReader.read(mf, baked)
    val b0 = File(out, "masker-terap.0.rgba").readBytes()
    var mdb = 0
    for (k in b0.indices) {
        val want = ((srcs[0].get(k).toInt() and 0xFF) * (maskVal[k / 4].toInt() and 0xFF) + 127) / 255
        mdb = maxOf(mdb, kotlin.math.abs((b0[k].toInt() and 0xFF) - want))
    }
    println("  masker-terap layer 0: selisih maks $mdb")
    if (mdb > 4) fail++
    // .procreate dengan masker dipisah.
    val pf2 = File(fx, "uji-procreate.procreate")
    if (pf2.exists()) {
        val pc = Collect(out, "procreate-pisah")
        ProcreateReader.read(pf2, pc, keepMasks = true)
        val wantPx = File(fx, "uji-procreate.1u.rgba").readBytes()
        val wantMask = File(fx, "uji-procreate.mask.gray").readBytes()
        val gotPx = File(out, "procreate-pisah.1.rgba").readBytes()
        val gm = pc.infos.getOrNull(1)?.mask
        if (gm == null || !gm.contentEquals(wantMask) || !gotPx.contentEquals(wantPx)) {
            println("GAGAL .procreate masker dipisah: masker=${gm != null} samaMasker=${gm?.contentEquals(wantMask)} samaPiksel=${gotPx.contentEquals(wantPx)}")
            fail++
        } else println("  .procreate masker dipisah: sama persis")
    }
    // Palet: ASE, ACO, .swatches, GPL, hex.
    val palDir = File(fx, "palet")
    val harapan = File(palDir, "harapan.txt")
    if (harapan.exists()) {
        fun hex(c: Int) = String.format("%06X", c and 0xFFFFFF)
        fun near(a: Int, b: Int) = (0..2).all { s2 -> kotlin.math.abs(((a shr (s2 * 8)) and 0xFF) - ((b shr (s2 * 8)) and 0xFF)) <= 2 }
        val parsed = HashMap<String, List<id.goresan.engine.Palette>>()
        for (line in harapan.readLines().filter { it.isNotBlank() }) {
            val (fn, pname, cols) = line.split("|")
            val list = parsed.getOrPut(fn) { id.goresan.engine.Palettes.parse(File(palDir, fn).readBytes(), fn) }
            val pl = list.firstOrNull { it.name == pname }
            val want = cols.split(",").map { it.toInt(16) or 0xFF000000.toInt() }
            val got = pl?.colors ?: emptyList()
            val ok = pl != null && got.size == want.size && got.indices.all { near(got[it], want[it]) }
            println("  palet $fn '$pname': ${got.joinToString(",") { hex(it) }} ${if (ok) "OK" else "GAGAL (harapan ${want.joinToString(",") { hex(it) }})"}")
            if (!ok) fail++
        }
        // Ekspor .swatches lalu baca lagi.
        val orig = id.goresan.engine.Palette("Pulang Pergi", mutableListOf(0xFFFF0000.toInt(), 0xFF123456.toInt(), 0xFFFFFFFF.toInt(), 0xFF000000.toInt(), 0xFF7F3FBF.toInt()))
        val bytes = id.goresan.engine.Palettes.toSwatches(orig)
        File(out, "pulang-pergi.swatches").writeBytes(bytes)
        val back = id.goresan.engine.Palettes.parse(bytes, "x.swatches").firstOrNull()
        val rtOk = back != null && back.name == orig.name && back.colors.size == orig.colors.size && back.colors.indices.all { near(back.colors[it], orig.colors[it]) }
        println("  .swatches pulang-pergi: ${back?.colors?.joinToString(",") { hex(it) }} ${if (rtOk) "OK" else "GAGAL"}")
        if (!rtOk) fail++
        // Palet dari foto: 4 blok warna → 4 warna utama.
        val blocks = intArrayOf(0xFFE53935.toInt(), 0xFF1E88E5.toInt(), 0xFFFDD835.toInt(), 0xFF2E7D32.toInt())
        val img = IntArray(80 * 80) { i -> blocks[((i % 80) / 40) + 2 * ((i / 80) / 40)] }
        val got = id.goresan.engine.Palettes.fromImage(img, 30)
        val fOk = got.size == 4 && blocks.all { b -> got.any { near(it, b) } }
        println("  palet dari foto: ${got.joinToString(",") { hex(it) }} ${if (fOk) "OK" else "GAGAL"}")
        if (!fOk) fail++
        // Harmoni: komplementer merah = sian.
        val comp = id.goresan.engine.Palettes.harmony(id.goresan.engine.Palettes.COMPLEMENTARY, 0f, 1f, 1f)
        val tri = id.goresan.engine.Palettes.harmony(id.goresan.engine.Palettes.TRIADIC, 0f, 1f, 1f)
        val hOk = comp.size == 1 && near(comp[0], 0xFF00FFFF.toInt()) && tri.size == 2 && near(tri[0], 0xFF00FF00.toInt()) && near(tri[1], 0xFF0000FF.toInt())
        println("  harmoni: komplementer ${comp.joinToString { hex(it) }}, triadik ${tri.joinToString { hex(it) }} ${if (hOk) "OK" else "GAGAL"}")
        if (!hOk) fail++
    }
    // TIFF: tulis dengan Tiff.write (RGBA dan RGB), dibandingkan Python/PIL di banding.py.
    val tw = 203
    val th = 77
    val tb = ByteBuffer.allocate(tw * th * 4)
    val straight = ByteArray(tw * th * 4)
    for (y in 0 until th) for (x in 0 until tw) {
        val a = when { x % 9 == 0 -> 0; (x + y) % 4 == 0 -> 100; else -> 255 }
        val r = if (x in 20..60) 200 else (x * 7) % 256
        val g = if (y % 3 == 0) 17 else (y * 5) % 256
        val b = if (x % 2 == 0) 255 else 0
        val o = (y * tw + x) * 4
        straight[o] = r.toByte(); straight[o + 1] = g.toByte(); straight[o + 2] = b.toByte(); straight[o + 3] = a.toByte()
        tb.put((r * a / 255).toByte()); tb.put((g * a / 255).toByte()); tb.put((b * a / 255).toByte()); tb.put(a.toByte())
    }
    tb.position(0)
    File(out, "tiff.straight.rgba").writeBytes(straight)
    File(out, "tiff.premul.rgba").writeBytes(ByteArray(tw * th * 4).also { tb.get(it); tb.position(0) })
    id.goresan.engine.Tiff.write(File(out, "uji-rgba.tif"), tw, th, 150, tb, alpha = true)
    id.goresan.engine.Tiff.write(File(out, "uji-rgb.tif"), tw, th, 150, tb, alpha = false)
    println("TIFF ditulis ${tw}x$th")
    println(if (fail == 0) "SEMUA LULUS (Kotlin)" else "ADA $fail KEGAGALAN (Kotlin)")
}
