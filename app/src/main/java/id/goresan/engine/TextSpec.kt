package id.goresan.engine

import org.json.JSONArray
import org.json.JSONObject

/**
 * Isi dan gaya satu layer teks. Disimpan di layer (LayerProps.text) supaya teks bisa diedit lagi.
 * Ukuran dalam piksel kanvas; posisi [cx], [cy] = titik tengah teks; [rot] radian; [scale] pengali ukuran.
 */
data class TextSpec(
    val text: String = "",
    val font: String = "bangers",
    val size: Float = 120f,
    val color: Int = 0xFF1C2030.toInt(),
    val bold: Boolean = false,
    val italic: Boolean = false,
    /** 0 kiri, 1 tengah, 2 kanan. */
    val align: Int = 1,
    /** Jarak antarhuruf (em). */
    val spacing: Float = 0f,
    /** Jarak baris (kali tinggi huruf). */
    val lineHeight: Float = 1.15f,
    val caps: Boolean = false,
    val underline: Boolean = false,
    /** Garis tepi huruf (px kanvas, 0 = tanpa). */
    val outline: Float = 0f,
    val outlineColor: Int = 0xFFFFFFFF.toInt(),
    val shadow: Boolean = false,
    val shadowDx: Float = 0.06f,
    val shadowDy: Float = 0.08f,
    val shadowBlur: Float = 0.08f,
    val shadowColor: Int = 0x88000000.toInt(),
    /** Bentuk baris: 0 lurus, 1 lengkung, 2 gelombang, 3 lingkaran, 4 jalur bebas. */
    val shape: Int = 0,
    /** Lengkung: -1 (melengkung ke bawah, ∪) … 1 (melengkung ke atas, ∩). */
    val curve: Float = 0.5f,
    /** Gelombang: tinggi (kali ukuran huruf) dan panjang gelombang (kali ukuran huruf). */
    val waveAmp: Float = 0.35f,
    val waveLen: Float = 4f,
    /** Balik sisi (huruf di sisi dalam lengkung/lingkaran/jalur). */
    val flip: Boolean = false,
    /** Jalur bebas: titik x, y relatif terhadap titik tengah (sebelum putar/skala). */
    val path: List<Float> = emptyList(),
    val cx: Float = 0f,
    val cy: Float = 0f,
    val rot: Float = 0f,
    val scale: Float = 1f,
) {
    fun toJson(): String = JSONObject().apply {
        put("text", text); put("font", font); put("size", size.toDouble()); put("color", color)
        put("bold", bold); put("italic", italic); put("align", align); put("spacing", spacing.toDouble())
        put("lineHeight", lineHeight.toDouble()); put("caps", caps); put("underline", underline)
        put("outline", outline.toDouble()); put("outlineColor", outlineColor)
        put("shadow", shadow); put("shadowDx", shadowDx.toDouble()); put("shadowDy", shadowDy.toDouble())
        put("shadowBlur", shadowBlur.toDouble()); put("shadowColor", shadowColor)
        put("shape", shape); put("curve", curve.toDouble()); put("waveAmp", waveAmp.toDouble())
        put("waveLen", waveLen.toDouble()); put("flip", flip)
        put("path", JSONArray().apply { path.forEach { put(it.toDouble()) } })
        put("cx", cx.toDouble()); put("cy", cy.toDouble()); put("rot", rot.toDouble()); put("scale", scale.toDouble())
    }.toString()

    companion object {
        fun fromJson(s: String?): TextSpec? {
            if (s.isNullOrBlank()) return null
            return runCatching {
                val o = JSONObject(s)
                val d = TextSpec()
                fun f(k: String, v: Float) = if (o.has(k)) o.optDouble(k, v.toDouble()).toFloat() else v
                val arr = o.optJSONArray("path")
                TextSpec(
                    text = o.optString("text", ""),
                    font = o.optString("font", d.font),
                    size = f("size", d.size).coerceIn(4f, 4000f),
                    color = o.optInt("color", d.color),
                    bold = o.optBoolean("bold", false),
                    italic = o.optBoolean("italic", false),
                    align = o.optInt("align", 1).coerceIn(0, 2),
                    spacing = f("spacing", 0f),
                    lineHeight = f("lineHeight", d.lineHeight),
                    caps = o.optBoolean("caps", false),
                    underline = o.optBoolean("underline", false),
                    outline = f("outline", 0f),
                    outlineColor = o.optInt("outlineColor", d.outlineColor),
                    shadow = o.optBoolean("shadow", false),
                    shadowDx = f("shadowDx", d.shadowDx),
                    shadowDy = f("shadowDy", d.shadowDy),
                    shadowBlur = f("shadowBlur", d.shadowBlur),
                    shadowColor = o.optInt("shadowColor", d.shadowColor),
                    shape = o.optInt("shape", 0).coerceIn(0, 4),
                    curve = f("curve", d.curve),
                    waveAmp = f("waveAmp", d.waveAmp),
                    waveLen = f("waveLen", d.waveLen),
                    flip = o.optBoolean("flip", false),
                    path = if (arr == null) emptyList() else List(arr.length()) { arr.optDouble(it, 0.0).toFloat() },
                    cx = f("cx", 0f),
                    cy = f("cy", 0f),
                    rot = f("rot", 0f),
                    scale = f("scale", 1f).coerceIn(0.02f, 50f),
                )
            }.getOrNull()
        }
    }
}
