package id.goresan.ui

import android.content.Context
import android.os.Handler
import android.os.Looper
import androidx.compose.runtime.mutableStateListOf
import id.goresan.engine.Brush
import id.goresan.engine.BrushImport
import id.goresan.engine.Brushes
import org.json.JSONArray
import java.io.File

/** Pustaka kuas: kuas bawaan (bisa diubah dan dikembalikan) plus kuas buatan sendiri. */
class BrushStore(ctx: Context) {
    private val file = File(ctx.filesDir, "kuas.json")
    /** Daftar buatan sendiri (termasuk yang masih kosong), berurutan. */
    private val setsFile = File(ctx.filesDir, "kuas-daftar.json")
    val list = mutableStateListOf<Brush>()
    val customSets = mutableStateListOf<String>()

    init {
        BrushImport.init(ctx)
        val saved = HashMap<String, Brush>()
        val customs = ArrayList<Brush>()
        runCatching {
            if (file.exists()) {
                val a = JSONArray(file.readText())
                for (i in 0 until a.length()) {
                    val b = Brush.fromJson(a.getJSONObject(i))
                    if (Brushes.default(b.id) != null) saved[b.id] = b.copy(builtIn = true) else customs.add(b.copy(builtIn = false))
                }
            }
        }
        for (b in Brushes.builtIn) list.add(saved[b.id] ?: b)
        list.addAll(customs)
        runCatching {
            if (setsFile.exists()) {
                val a = JSONArray(setsFile.readText())
                for (i in 0 until a.length()) a.optString(i).takeIf { it.isNotBlank() && it !in Brushes.categories }?.let { customSets.add(it) }
            }
        }
        for (b in list) if (!b.builtIn && b.category !in Brushes.categories && b.category !in customSets) customSets.add(b.category)
    }

    /** Daftar bawaan (kuas yang ikut aplikasi). */
    fun builtInSets(): List<String> = Brushes.categories

    /** Buat daftar baru yang masih kosong; mengembalikan nama yang dipakai (dibuat unik). */
    fun createSet(name: String): String {
        var n = name.trim().take(40).ifBlank { "Daftar baru" }
        val base = n
        var k = 2
        while (n in Brushes.categories || n in customSets) n = "$base $k".also { k++ }
        customSets.add(n)
        save()
        return n
    }

    /** Pindahkan kuas buatan sendiri/impor ke daftar lain. */
    fun moveTo(id: String, set: String) {
        val i = list.indexOfFirst { it.id == id }
        if (i < 0 || list[i].builtIn) return
        list[i] = list[i].copy(category = set)
        if (set !in customSets && set !in Brushes.categories) customSets.add(set)
        save()
    }

    /** Salin kuas (juga kuas bawaan) ke daftar lain. */
    fun copyTo(b: Brush, set: String): Brush {
        val nb = b.copy(id = "k" + System.currentTimeMillis().toString(36), category = set, builtIn = false)
        list.add(nb)
        if (set !in customSets && set !in Brushes.categories) customSets.add(set)
        save()
        return nb
    }

    /** Cari kuas menurut nama atau nama daftar. */
    fun search(q: String): List<Brush> {
        val t = q.trim()
        if (t.isEmpty()) return emptyList()
        return list.filter { it.name.contains(t, ignoreCase = true) || it.category.contains(t, ignoreCase = true) }
    }

    /** Tulis satu daftar ke zip WInote (.wkuas). */
    fun exportSet(set: String, out: java.io.OutputStream) = BrushImport.exportSet(list.filter { it.category == set }, out)

    fun get(id: String): Brush = list.firstOrNull { it.id == id } ?: list.first()

    fun categories(): List<String> {
        val out = ArrayList(Brushes.categories)
        for (c in customSets) if (c !in out) out.add(c)
        for (b in list) if (b.category !in out) out.add(b.category)
        return out
    }

    private val handler = Handler(Looper.getMainLooper())
    private val saveLater = Runnable { save() }

    fun update(b: Brush) {
        val i = list.indexOfFirst { it.id == b.id }
        if (i >= 0) list[i] = b
        handler.removeCallbacks(saveLater)
        handler.postDelayed(saveLater, 700)
    }

    fun duplicate(b: Brush): Brush {
        val nb = b.copy(
            id = "k" + System.currentTimeMillis().toString(36),
            name = b.name + " salinan",
            category = "Kuasku",
            builtIn = false,
        )
        list.add(nb)
        if ("Kuasku" !in customSets) customSets.add("Kuasku")
        save()
        return nb
    }

    /**
     * Gabungkan dua kuas jadi satu kuas ganda, seperti Combine di Brush Library Procreate: [primary] jadi kuas utama
     * dan [secondary] tersimpan di dalamnya sebagai kuas kedua (bisa diatur sendiri di Studio Kuas). Seperti Procreate,
     * kuas buatan sendiri yang digabung diganti kuas gabungannya; kuas bawaan tetap ada (yang dipakai salinannya).
     */
    fun combine(primary: Brush, secondary: Brush): Brush {
        val cat = when {
            !primary.builtIn -> primary.category
            !secondary.builtIn -> secondary.category
            else -> "Kuasku"
        }
        val nb = primary.copy(
            id = "k" + System.currentTimeMillis().toString(36),
            category = cat,
            builtIn = false,
            dualId = "",
            dualMode = 0,
            dualSize = 1f,
            dual = secondary.copy(dual = null, dualId = "", builtIn = false, stamp = false),
        )
        var at = list.indexOfFirst { it.id == primary.id && !it.builtIn }
        if (at < 0) at = list.indexOfFirst { it.id == secondary.id && !it.builtIn }
        if (at >= 0) list.add(at, nb) else list.add(nb)
        list.removeAll { !it.builtIn && it.id != nb.id && (it.id == primary.id || it.id == secondary.id) }
        if (cat !in Brushes.categories && cat !in customSets) customSets.add(cat)
        save()
        return nb
    }

    /**
     * Pisahkan kuas gabungan jadi dua kuas lagi (Uncombine di Procreate): kuas utama tetap di tempatnya (id sama),
     * kuas kedua muncul tepat di bawahnya. Mengembalikan kuas kedua, atau null bila [b] bukan kuas gabungan.
     */
    fun uncombine(b: Brush): Brush? {
        val d = b.dual ?: return null
        val i = list.indexOfFirst { it.id == b.id }
        if (i < 0) return null
        val second = d.copy(
            id = "k" + System.currentTimeMillis().toString(36) + "b",
            category = if (b.builtIn) "Kuasku" else b.category,
            builtIn = false,
            dual = null,
            dualId = "",
        )
        list[i] = b.copy(dual = null, dualId = "", dualMode = 0, dualSize = 1f)
        list.add(i + 1, second)
        if (second.category !in Brushes.categories && second.category !in customSets) customSets.add(second.category)
        save()
        return second
    }

    fun delete(id: String) {
        val b = list.firstOrNull { it.id == id } ?: return
        if (b.builtIn) return
        list.remove(b)
        save()
        dropFiles(b)
    }

    /** Gambar ujung dan tekstur kuas impor ikut dihapus bila tidak dipakai kuas lain (salinannya, atau pola bersama). */
    private fun dropFiles(b: Brush) {
        val files = listOfNotNull(b.tipFile, b.grainFile, b.dual?.tipFile, b.dual?.grainFile).filter { it.isNotEmpty() }.toSet()
        for (f in files) if (list.none { usesFile(it, f) }) BrushImport.deleteFile(f)
    }

    /** Kuas [b] (atau kuas kedua di dalamnya) memakai berkas gambar [f]. */
    private fun usesFile(b: Brush, f: String): Boolean =
        b.tipFile == f || b.grainFile == f || b.dual?.let { it.tipFile == f || it.grainFile == f } == true

    /** Daftar yang isinya kuas buatan sendiri/impor saja (bukan daftar bawaan), jadi boleh dihapus atau diganti nama. */
    fun isCustomCategory(c: String): Boolean = c !in Brushes.categories && list.none { it.category == c && it.builtIn }

    fun countIn(c: String): Int = list.count { it.category == c }

    /** Hapus satu daftar beserta semua kuas buatan sendiri di dalamnya; mengembalikan id kuas yang terhapus. */
    fun deleteCategory(c: String): List<String> {
        val gone = list.filter { it.category == c && !it.builtIn }
        if (gone.isEmpty()) {
            if (customSets.remove(c)) save()
            return emptyList()
        }
        val ids = gone.map { it.id }.toSet()
        list.removeAll { it.id in ids }
        customSets.remove(c)
        save()
        for (b in gone) dropFiles(b)
        return ids.toList()
    }

    fun renameCategory(old: String, new: String) {
        val name = new.trim().take(40)
        if (name.isEmpty() || name == old) return
        for (i in list.indices) if (list[i].category == old && !list[i].builtIn) list[i] = list[i].copy(category = name)
        val k = customSets.indexOf(old)
        if (k >= 0) customSets[k] = name else if (name !in customSets) customSets.add(name)
        save()
    }

    /** Tambahkan kuas hasil impor. */
    fun add(bs: List<Brush>) {
        if (bs.isEmpty()) return
        list.addAll(bs)
        for (b in bs) if (b.category !in Brushes.categories && b.category !in customSets) customSets.add(b.category)
        save()
    }

    fun reset(id: String) {
        val d = Brushes.default(id) ?: return
        update(d)
    }

    fun isModified(b: Brush): Boolean = b.builtIn && Brushes.default(b.id) != b

    private fun save() {
        val a = JSONArray()
        for (b in list) {
            if (!b.builtIn || Brushes.default(b.id) != b) a.put(b.toJson())
        }
        runCatching {
            val tmp = File(file.parentFile, "kuas.json.tmp")
            tmp.writeText(a.toString())
            tmp.renameTo(file)
        }
        runCatching {
            val tmp = File(setsFile.parentFile, "kuas-daftar.json.tmp")
            tmp.writeText(JSONArray().apply { customSets.forEach { put(it) } }.toString())
            tmp.renameTo(setsFile)
        }
    }
}
