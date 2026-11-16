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
