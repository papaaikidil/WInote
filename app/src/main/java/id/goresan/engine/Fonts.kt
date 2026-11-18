package id.goresan.engine

import android.content.Context
import android.graphics.Typeface

/**
 * Daftar font teks: komik, artistik, eksklusif (font OFL/Apache di assets/fonts, lisensi di LISENSI-FONT.txt),
 * dan font bawaan sistem. Tebal/miring memakai berkas varian bila ada, font variabel diatur bobotnya, selain itu
 * ditebalkan/dimiringkan sintetis.
 */
object Fonts {
    class Def(
        val id: String,
        val name: String,
        val category: String,
        /** Berkas di assets/fonts, atau null untuk font sistem ([system]). */
        val file: String? = null,
        val boldFile: String? = null,
        val italicFile: String? = null,
        /** Font variabel dengan sumbu bobot (wght). */
        val variable: Boolean = false,
        val system: String? = null,
        /** Font impor pengguna: berkas di folder data aplikasi. */
        val path: String? = null,
    )

    const val USER = "Font saya"

    val categories = listOf("Komik", "Artistik", "Eksklusif", "Dasar", USER)

    /** Font yang diimpor pengguna (TTF/OTF/TTC di files/fonts). */
    @Volatile
    private var user: List<Def> = emptyList()

    val all: List<Def> get() = builtins + user

    private val builtins = listOf(
        Def("bangers", "Bangers", "Komik", "bangers.ttf"),
        Def("luckiestguy", "Luckiest Guy", "Komik", "luckiestguy.ttf"),
        Def("comicneue", "Comic Neue", "Komik", "comicneue.ttf", boldFile = "comicneue-bold.ttf"),
        Def("permanentmarker", "Permanent Marker", "Komik", "permanentmarker.ttf"),
        Def("chewy", "Chewy", "Komik", "chewy.ttf"),
        Def("titanone", "Titan One", "Komik", "titanone.ttf"),
        Def("bubblegumsans", "Bubblegum Sans", "Komik", "bubblegumsans.ttf"),
        Def("patrickhand", "Patrick Hand", "Komik", "patrickhand.ttf"),

        Def("pacifico", "Pacifico", "Artistik", "pacifico.ttf"),
        Def("lobster", "Lobster", "Artistik", "lobster.ttf"),
        Def("dancingscript", "Dancing Script", "Artistik", "dancingscript.ttf", variable = true),
        Def("caveat", "Caveat", "Artistik", "caveat.ttf", variable = true),
        Def("amaticsc", "Amatic SC", "Artistik", "amaticsc.ttf", boldFile = "amaticsc-bold.ttf"),
        Def("shadowsintolight", "Shadows Into Light", "Artistik", "shadowsintolight.ttf"),
        Def("fredericka", "Fredericka the Great", "Artistik", "fredericka.ttf"),
        Def("rubikwetpaint", "Rubik Wet Paint", "Artistik", "rubikwetpaint.ttf"),
        Def("monoton", "Monoton", "Artistik", "monoton.ttf"),
        Def("righteous", "Righteous", "Artistik", "righteous.ttf"),

        Def("playfair", "Playfair Display", "Eksklusif", "playfair.ttf", italicFile = "playfair-italic.ttf", variable = true),
        Def("cinzel", "Cinzel", "Eksklusif", "cinzel.ttf", variable = true),
        Def("greatvibes", "Great Vibes", "Eksklusif", "greatvibes.ttf"),
        Def("pinyonscript", "Pinyon Script", "Eksklusif", "pinyonscript.ttf"),
        Def("parisienne", "Parisienne", "Eksklusif", "parisienne.ttf"),
        Def("italiana", "Italiana", "Eksklusif", "italiana.ttf"),
        Def("marcellus", "Marcellus", "Eksklusif", "marcellus.ttf"),
        Def("abrilfatface", "Abril Fatface", "Eksklusif", "abrilfatface.ttf"),

        Def("sans", "Sans", "Dasar", system = "sans-serif"),
        Def("sansblack", "Sans Tebal", "Dasar", system = "sans-serif-black"),
        Def("condensed", "Sans Ramping", "Dasar", system = "sans-serif-condensed"),
        Def("serif", "Serif", "Dasar", system = "serif"),
        Def("mono", "Mono", "Dasar", system = "monospace"),
    )

    fun def(id: String): Def = all.firstOrNull { it.id == id } ?: builtins.first { it.id == "sans" }

    private fun dir(ctx: Context) = java.io.File(ctx.filesDir, "fonts")

    /** Baca ulang daftar font impor. */
    fun refresh(ctx: Context) {
        val files = dir(ctx).listFiles()?.filter { it.isFile && it.extension.lowercase() in setOf("ttf", "otf", "ttc") }.orEmpty()
        user = files.sortedBy { it.name.lowercase() }.map { f ->
            Def("user:" + f.name, f.nameWithoutExtension.replace('_', ' '), USER, path = f.absolutePath)
        }
    }

    /**
     * Simpan font impor [bytes] bernama [name]. Isinya dikenali dari bait awalnya (TTF/OTF/TTC, font web WOFF, atau
     * ZIP berisi font), lihat [FontFiles]. Mengembalikan font yang ditambahkan, atau melempar galat berpesan jelas
     * bila tidak ada font yang bisa dibaca Android.
     */
    fun importFont(ctx: Context, name: String, bytes: ByteArray): List<Def> {
        val fonts = FontFiles.extract(name, bytes)
        val d = dir(ctx).apply { mkdirs() }
        val saved = ArrayList<java.io.File>()
        for (ff in fonts) {
            val base = ff.name.replace(Regex("[^A-Za-z0-9 _-]"), "_").trim().ifBlank { "Font" }
            var f = java.io.File(d, "$base.${ff.ext}")
            var k = 2
            while (f.exists()) f = java.io.File(d, "$base $k.${ff.ext}").also { k++ }
            f.writeBytes(ff.bytes)
            val tf = runCatching { Typeface.Builder(f).build() }.getOrNull()
            if (tf == null) f.delete() else saved.add(f)
        }
        if (saved.isEmpty()) error("Font ini tidak bisa dibaca Android. Berkasnya mungkin rusak atau tidak lengkap.")
        refresh(ctx)
        synchronized(cache) { cache.keys.removeAll { it.startsWith("user:") } }
        return saved.map { def("user:" + it.name) }
    }

    /** Hapus font impor [id]. Teks yang memakainya beralih ke Sans saat digambar ulang. */
    fun delete(ctx: Context, id: String) {
        val d = user.firstOrNull { it.id == id } ?: return
        d.path?.let { java.io.File(it).delete() }
        refresh(ctx)
        synchronized(cache) { cache.keys.removeAll { it.startsWith("$id:") } }
    }

    private val cache = HashMap<String, Typeface>()

    /** Typeface untuk [id] dengan tebal/miring; selalu berhasil (jatuh ke sans-serif bila berkas rusak). */
    fun typeface(ctx: Context, id: String, bold: Boolean, italic: Boolean): Typeface = synchronized(cache) {
        val key = "$id:$bold:$italic"
        cache.getOrPut(key) { load(ctx, def(id), bold, italic) }
    }

    private fun load(ctx: Context, d: Def, bold: Boolean, italic: Boolean): Typeface {
        val style = (if (bold) Typeface.BOLD else 0) or (if (italic) Typeface.ITALIC else 0)
        if (d.system != null) return Typeface.create(d.system, style)
        if (d.path != null) {
            return runCatching {
                val base = Typeface.Builder(java.io.File(d.path)).build() ?: error("font rusak")
                if (style != 0) Typeface.create(base, style) else base
            }.getOrElse { Typeface.create(Typeface.SANS_SERIF, style) }
        }
        return runCatching {
            val am = ctx.assets
            var file = d.file!!
            var synthBold = bold
            var synthItalic = italic
            if (bold && d.boldFile != null) { file = d.boldFile; synthBold = false }
            if (italic && d.italicFile != null) { file = d.italicFile; synthItalic = false }
            val base = if (d.variable && bold) {
                synthBold = false
                Typeface.Builder(am, "fonts/$file").setFontVariationSettings("'wght' 700").build()
            } else {
                Typeface.createFromAsset(am, "fonts/$file")
            }
            val s = (if (synthBold) Typeface.BOLD else 0) or (if (synthItalic) Typeface.ITALIC else 0)
            if (s != 0) Typeface.create(base, s) else base
        }.getOrElse { Typeface.create(Typeface.SANS_SERIF, style) }
    }
}
