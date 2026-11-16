package id.hananote.data

import android.graphics.Typeface
import java.io.File
import java.util.concurrent.ConcurrentHashMap

/** Satu pilihan font: bawaan sistem ("sys:…") atau berkas yang ditambahkan pengguna ("file:…"). */
data class FontEntry(val id: String, val name: String, val imported: Boolean = false)

object FontRegistry {
    const val DEFAULT = "sys:sans-serif"

    /** Font bawaan Android. Bila perangkat tidak punya salah satunya, otomatis jatuh ke font biasa. */
    val builtIn: List<FontEntry> = listOf(
        FontEntry("sys:sans-serif", "Sans"),
        FontEntry("sys:serif", "Serif"),
        FontEntry("sys:monospace", "Mono"),
        FontEntry("sys:sans-serif-light", "Tipis"),
        FontEntry("sys:sans-serif-condensed", "Ramping"),
        FontEntry("sys:sans-serif-black", "Tebal"),
        FontEntry("sys:casual", "Santai"),
        FontEntry("sys:cursive", "Sambung"),
    )

    /** Folder tempat font tambahan disimpan; diisi saat aplikasi dibuka. */
    @Volatile var dir: File? = null

    private val cache = ConcurrentHashMap<String, Typeface>()

    fun typeface(id: String): Typeface = cache.getOrPut(id) { load(id) }

    fun forget(id: String) {
        cache.remove(id)
    }

    private fun load(id: String): Typeface = when {
        id.startsWith("sys:") -> Typeface.create(id.removePrefix("sys:"), Typeface.NORMAL)
        id.startsWith("file:") -> {
            val f = dir?.let { File(it, id.removePrefix("file:")) }
            if (f != null && f.exists()) runCatching { Typeface.createFromFile(f) }.getOrDefault(Typeface.DEFAULT)
            else Typeface.DEFAULT
        }
        else -> Typeface.DEFAULT
    }
}
