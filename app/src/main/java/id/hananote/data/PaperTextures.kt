package id.hananote.data

/** Tekstur kertas yang bisa dipakai sebagai latar halaman. Warna dasarnya dipakai juga sebagai warna kertas. */
enum class PaperTexture(
    val id: String,
    val label: String,
    val base: Int,
    /** Ukuran satu ubin tekstur dalam piksel. */
    val tilePx: Int = 256,
) {
    RECYCLED("daur", "Daur ulang", 0xFFECE7DA.toInt()),
    KRAFT("kraft", "Kraft", 0xFFC8A274.toInt()),
    LINEN("linen", "Linen", 0xFFF4F0E6.toInt()),
    WATERCOLOR("catair", "Cat air", 0xFFFBF9F3.toInt()),
    PARCHMENT("perkamen", "Perkamen", 0xFFF0E2C0.toInt(), tilePx = 512),
    CANVAS("kanvas", "Kanvas", 0xFFEFEAE0.toInt()),
    WASHI("washi", "Washi", 0xFFF8F4EA.toInt()),
    BLACK("hitam", "Karton hitam", 0xFF2A2A2E.toInt());

    /** Satu piksel tekstur = setengah poin halaman, jadi butirannya halus seperti kertas sungguhan. */
    val tilePt: Float get() = tilePx / 2f

    companion object {
        fun byId(id: String): PaperTexture? = entries.firstOrNull { it.id == id }
    }
}

/** Latar halaman disimpan sebagai teks: "" (polos), "tex:<id>" (tekstur), atau "img:<berkas>" (gambar). */
object PageBackground {
    const val PLAIN = ""

    fun texture(t: PaperTexture) = "tex:" + t.id
    fun image(file: String) = "img:$file"

    fun textureOf(bg: String): PaperTexture? =
        if (bg.startsWith("tex:")) PaperTexture.byId(bg.substring(4)) else null

    fun imageOf(bg: String): String? =
        if (bg.startsWith("img:")) bg.substring(4).takeIf { it.isNotBlank() && !it.contains('/') } else null

    /**
     * Halaman PDF: "pdf:<berkas>#<nomor halaman mulai 0>", ditambah "@<derajat>" bila halamannya diputar searah
     * jarum jam (90, 180, 270).
     */
    fun pdf(file: String, index: Int, rotation: Int = 0) =
        "pdf:$file#$index" + (((rotation % 360) + 360) % 360).let { if (it == 0) "" else "@$it" }

    fun pdfOf(bg: String): Pair<String, Int>? {
        if (!bg.startsWith("pdf:")) return null
        val body = bg.substring(4).substringBefore('@')
        val file = body.substringBeforeLast('#')
        val index = body.substringAfterLast('#', "").toIntOrNull() ?: return null
        if (file.isBlank() || file.contains('/')) return null
        return file to index
    }

    /** Putaran halaman PDF (0, 90, 180, 270). */
    fun pdfRotation(bg: String): Int {
        if (!bg.startsWith("pdf:")) return 0
        val r = bg.substringAfterLast('@', "").toIntOrNull() ?: return 0
        return if (r == 90 || r == 180 || r == 270) r else 0
    }
}
