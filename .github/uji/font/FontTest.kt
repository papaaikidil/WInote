import id.goresan.engine.FontFiles
import java.io.File

/** Jalankan FontFiles.extract pada tiap berkas uji, cetak hasilnya, dan simpan font yang keluar. */
fun main(args: Array<String>) {
    val dir = File(args[0])
    val out = File(args[1]).apply { mkdirs() }
    for (f in dir.listFiles()!!.sortedBy { it.name }) {
        try {
            val fonts = FontFiles.extract(f.name, f.readBytes())
            println("${f.name} => " + fonts.joinToString(" | ") { "${it.name}.${it.ext} ${it.bytes.size}" })
            fonts.forEachIndexed { i, ff -> File(out, "${f.name}.$i.${ff.ext}").writeBytes(ff.bytes) }
        } catch (e: FontFiles.NotFont) {
            println("${f.name} !! ${e.message}")
        }
    }
}
