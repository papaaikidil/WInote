package id.goresan.engine

import org.json.JSONObject

/**
 * Pengaturan Animation Assist (seperti Procreate). Frame = layer teratas (tanpa grup) atau satu grup utuh,
 * berurutan dari bawah ke atas; layer clipping ikut frame layer dasarnya.
 */
data class AnimSettings(
    /** 0 = putar berulang, 1 = bolak-balik, 2 = sekali. */
    val mode: Int = 0,
    val fps: Int = 12,
    /** Jumlah frame onion skin di tiap sisi (0 = mati). */
    val onion: Int = 2,
    val onionOpacity: Float = 0.45f,
    /** Frame utama dibuat sedikit transparan supaya onion skin di bawahnya tampak. */
    val blendPrimary: Boolean = false,
    /** Onion skin diberi warna: frame sebelumnya merah, sesudahnya hijau. */
    val colorOnion: Boolean = true,
    /** Frame pertama jadi latar di semua frame. */
    val bgFrame: Boolean = false,
    /** Frame terakhir jadi latar depan di semua frame. */
    val fgFrame: Boolean = false,
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("mode", mode); put("fps", fps); put("onion", onion); put("onionOpacity", onionOpacity.toDouble())
        put("blendPrimary", blendPrimary); put("colorOnion", colorOnion); put("bgFrame", bgFrame); put("fgFrame", fgFrame)
    }

    companion object {
        fun fromJson(o: JSONObject?): AnimSettings {
            if (o == null) return AnimSettings()
            return AnimSettings(
                mode = o.optInt("mode", 0).coerceIn(0, 2),
                fps = o.optInt("fps", 12).coerceIn(1, 60),
                onion = o.optInt("onion", 2).coerceIn(0, 12),
                onionOpacity = o.optDouble("onionOpacity", 0.45).toFloat().coerceIn(0f, 1f),
                blendPrimary = o.optBoolean("blendPrimary", false),
                colorOnion = o.optBoolean("colorOnion", true),
                bgFrame = o.optBoolean("bgFrame", false),
                fgFrame = o.optBoolean("fgFrame", false),
            )
        }
    }
}

/** Satu frame/halaman: [key] = id layer (tanpa grup) atau -id grup. */
class AnimFrame(val key: Int, val layers: MutableList<Int>)

object Frames {
    /** Susun frame dari daftar layer (bawah ke atas). [ids], [groups], [clips] sejajar. */
    fun build(ids: List<Int>, groups: List<Int>, clips: List<Boolean>): List<AnimFrame> {
        val out = ArrayList<AnimFrame>()
        for (i in ids.indices) {
            if (clips[i] && out.isNotEmpty()) { out.last().layers.add(ids[i]); continue }
            val key = if (groups[i] != 0) -groups[i] else ids[i]
            if (out.isNotEmpty() && out.last().key == key) out.last().layers.add(ids[i]) else out.add(AnimFrame(key, mutableListOf(ids[i])))
        }
        return out
    }

    /** Urutan frame yang diputar (indeks frame), sudah termasuk tahan (hold) dan mode putar. */
    fun sequence(n: Int, s: AnimSettings, hold: (Int) -> Int): List<Int> {
        val first = if (s.bgFrame && n > 1) 1 else 0
        val last = if (s.fgFrame && n - 1 > first) n - 2 else n - 1
        val base = ArrayList<Int>()
        for (i in first..last) repeat(1 + hold(i).coerceIn(0, 60)) { base.add(i) }
        if (base.isEmpty()) return listOf(0)
        if (s.mode != 1) return base
        // Bolak-balik: maju lalu mundur tanpa mengulang frame ujungnya.
        val back = base.asReversed().dropWhile { it == base.last() }.dropLastWhile { it == base.first() }
        return base + back
    }
}
