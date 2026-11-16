package id.goresan.engine

import android.content.Context
import org.json.JSONArray
import java.io.File

/**
 * Kuas ikut cadangan WInote (seperti sinkron pustaka kuas Procreate lewat iCloud): pengaturan kuas, daftar buatan
 * sendiri, dan gambar ujung/tekstur kuas impor. Saat dipulihkan digabung, tidak ada yang dihapus:
 *  - kuas buatan sendiri/impor yang belum ada di tablet ini ditambahkan (beserta gambarnya),
 *  - ubahan kuas bawaan dari cadangan dipakai bila kuas bawaan itu belum diubah di tablet ini,
 *  - daftar buatan sendiri yang belum ada ditambahkan.
 */
object BrushSync {
    private val SAFE = Regex("[A-Za-z0-9][A-Za-z0-9._-]{0,120}")

    /** Berkas untuk dimasukkan ke cadangan: (jalur di zip, berkas). */
    fun backupFiles(ctx: Context): List<Pair<String, File>> {
        val out = ArrayList<Pair<String, File>>()
        val json = File(ctx.filesDir, "kuas.json")
        if (json.exists()) out.add("kuas/kuas.json" to json)
        val sets = File(ctx.filesDir, "kuas-daftar.json")
        if (sets.exists()) out.add("kuas/kuas-daftar.json" to sets)
        File(ctx.filesDir, "kuas").listFiles { f -> f.isFile && SAFE.matches(f.name) && !f.name.endsWith(".tmp") }
            ?.forEach { out.add("kuas/files/${it.name}" to it) }
        return out
    }

    /** Gabungkan kuas dari folder pulihan [dir] (isi "kuas/" dari zip cadangan). Hasil: jumlah kuas yang ditambahkan. */
    fun restore(ctx: Context, dir: File): Int {
        val src = File(dir, "kuas.json")
        if (!src.exists()) return 0
        val localFile = File(ctx.filesDir, "kuas.json")
        val local = LinkedHashMap<String, Brush>()
        runCatching {
            if (localFile.exists()) {
                val a = JSONArray(localFile.readText())
                for (i in 0 until a.length()) Brush.fromJson(a.getJSONObject(i)).let { local[it.id] = it }
            }
        }
        val incoming = runCatching {
            val a = JSONArray(src.readText())
            List(a.length()) { Brush.fromJson(a.getJSONObject(it)) }
        }.getOrElse { return 0 }
        val filesDir = File(ctx.filesDir, "kuas").apply { mkdirs() }
        var added = 0
        for (b in incoming) {
            if (local.containsKey(b.id)) continue
            for (f in listOf(b.tipFile, b.grainFile)) {
                if (f.isEmpty() || !SAFE.matches(f)) continue
                val from = File(dir, "files/$f")
                val to = File(filesDir, f)
                if (from.exists() && !to.exists()) runCatching { from.copyTo(to) }
            }
            val isBuiltIn = Brushes.default(b.id) != null
            local[b.id] = if (isBuiltIn) b.copy(builtIn = true) else b.copy(builtIn = false)
            if (!isBuiltIn) added++
        }
        val arr = JSONArray()
        for (b in local.values) arr.put(b.toJson())
        runCatching {
            val tmp = File(ctx.filesDir, "kuas.json.tmp")
            tmp.writeText(arr.toString())
            tmp.renameTo(localFile)
        }
        // Daftar buatan sendiri.
        val setsSrc = File(dir, "kuas-daftar.json")
        if (setsSrc.exists()) {
            val setsFile = File(ctx.filesDir, "kuas-daftar.json")
            val names = LinkedHashSet<String>()
            runCatching { if (setsFile.exists()) JSONArray(setsFile.readText()).let { a -> for (i in 0 until a.length()) names.add(a.optString(i)) } }
            runCatching { JSONArray(setsSrc.readText()).let { a -> for (i in 0 until a.length()) names.add(a.optString(i)) } }
            runCatching { setsFile.writeText(JSONArray().apply { names.filter { it.isNotBlank() }.forEach { put(it) } }.toString()) }
        }
        return added
    }
}
