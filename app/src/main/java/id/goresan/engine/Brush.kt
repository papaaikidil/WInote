package id.goresan.engine

import org.json.JSONArray
import org.json.JSONObject

enum class TipShape(val label: String) {
    HARD_ROUND("Bulat keras"),
    SOFT_ROUND("Bulat lembut"),
    AIRBRUSH("Semprot"),
    PENCIL("Pensil"),
    CHARCOAL("Arang"),
    INK_ROUGH("Tinta kasar"),
    FLAT("Bulu pipih"),
    SPLATTER("Percikan"),
    CHALK("Kapur"),
    WC_BLOB("Bercak cat air"),
}

enum class Grain(val label: String) {
    NONE("Tanpa tekstur"),
    PAPER("Kertas"),
    CANVAS("Kanvas"),
    ROUGH("Kasar"),
    NOISE("Bintik"),
    WC_CLOUD("Awan cat air"),
}

enum class AngleMode(val label: String) {
    FIXED("Tetap"),
    FOLLOW("Ikuti arah goresan"),
    TILT("Ikuti arah pena"),
}

/**
 * Pengaturan satu kuas. Ukuran dalam piksel kanvas; nilai 0..1 untuk dinamika.
 * Kuas yang sama dipakai untuk melukis, membaur, dan menghapus (seperti Procreate).
 */
data class Brush(
    val id: String,
    val name: String,
    val category: String,
    val tip: TipShape = TipShape.HARD_ROUND,
    val grain: Grain = Grain.NONE,
    val grainScale: Float = 1f,
    val grainStrength: Float = 0.7f,
    val sizeMax: Float = 40f,
    val sizeMin: Float = 1f,
    val spacing: Float = 0.08f,
    val opacityMax: Float = 1f,
    val flow: Float = 1f,
    val streamline: Float = 0f,
    /** StreamLine › Tekanan: menghaluskan perubahan tekanan. */
    val streamlinePressure: Float = 0f,
    /** Stabilisasi: penghalus yang makin kuat saat pena bergerak cepat. */
    val stabilization: Float = 0f,
    /** Penyaring gerak (Motion filtering): getaran tangan dibuang, garis lurus dan mulus berapa pun kecepatannya. */
    val motionFilter: Float = 0f,
    /** Ekspresi penyaring gerak: sebagian karakter tangan dikembalikan. */
    val motionExpression: Float = 0f,
    val pressureSize: Float = 0.8f,
    val pressureOpacity: Float = 0f,
    val tiltSize: Float = 0f,
    val tiltOpacity: Float = 0f,
    val taperStart: Float = 0f,
    /** Runcing di akhir goresan (ujung terakhir menipis seperti Taper di Procreate). */
    val taperEnd: Float = 0f,
    val sizeJitter: Float = 0f,
    val scatter: Float = 0f,
    val angleJitter: Float = 0f,
    val opacityJitter: Float = 0f,
    val angleMode: AngleMode = AngleMode.FIXED,
    val angle: Float = 0f,
    val roundness: Float = 1f,
    val hueJitter: Float = 0f,
    val smudgeStrength: Float = 0.8f,
    /**
     * Larut saat dibaurkan (alat Baur, atau kuas pembaur): 0 = cat terseret searah tarikan (seperti digosok jari),
     * 1 = cat di bawah kuas larut dan menyatu halus seperti cat air yang dibasahi lagi. −1 = otomatis menurut jenis
     * kuas ([smudgeWetness]).
     */
    val smudgeWet: Float = -1f,
    /** Kuas pembaur: dengan alat Kuas pun tidak menambah warna, hanya membaurkan cat yang ada (seperti alat Baur). */
    val blender: Boolean = false,
    val defaultSize: Float = 0.3f,
    val builtIn: Boolean = true,
    /** Ujung kuas dari gambar (kuas impor): nama berkas 256×256 di folder kuas; kosong = bentuk bawaan [tip]. */
    val tipFile: String = "",
    /** Tekstur dari gambar (kuas impor): nama berkas 512×512 di folder kuas; kosong = [grain]. */
    val grainFile: String = "",
    /**
     * Stempel: satu goresan = satu cap utuh yang membentang dari titik awal ke titik akhir goresan (ikut arahnya);
     * ketukan = satu cap seukuran kuas. Untuk kuas gambar seperti garis bawah, ornamen, tanda tangan.
     */
    val stamp: Boolean = false,
    /**
     * Tekstur menyatu di seluruh goresan (seperti tekstur Photoshop tanpa "Texture Each Tip", atau pola): tekstur
     * dikenakan sekali pada goresan, jadi tidak tertutup walau cap saling menumpuk. Mati = tekstur per cap.
     */
    val grainStroke: Boolean = false,
    /**
     * Cat air: goresan diolah seperti cat air sungguhan saat digabung ke layer: tepi basah yang tak rata dan sedikit
     * lebih gelap, bercak (bloom), pigmen mengendap di lekukan kertas (granulasi), dan lapisan yang menumpuk.
     */
    val watercolor: Boolean = false,
    /** Kepekatan pigmen satu sapuan (0..1). */
    val wcDensity: Float = 0.6f,
    /** Tepi gelap (pigmen berkumpul di tepi saat mengering). */
    val wcEdge: Float = 0.7f,
    /** Tepi lembut (basah di atas basah): 0 tegas, 1 menyebar. */
    val wcSoft: Float = 0.1f,
    /** Bercak/belang di dalam sapuan. */
    val wcBloom: Float = 0.6f,
    /** Granulasi: pigmen mengendap di lekukan kertas. */
    val wcGrain: Float = 0.5f,
    /** Lapisan menumpuk (glazing): sapuan di atas cat yang sudah ada jadi lebih pekat, seperti cat air sungguhan. */
    val wcGlaze: Boolean = true,
    /**
     * Cat air hidup: setelah digores, cat yang masih basah terus merembes keluar mengikuti serat kertas lalu mengering
     * (0 = mati, seperti cat air biasa). Nilai = jauhnya rembesan (relatif terhadap ukuran kuas).
     */
    val wcSpread: Float = 0f,
    /** Lama cat tetap basah setelah digores (detik); selama basah, goresan baru bercampur (basah di atas basah). */
    val wcWetTime: Float = 2.5f,

    // ---- Jalur goresan (Stroke Path) ----
    /** Getaran jarak: celah antarcap makin besar dan acak. */
    val spacingJitter: Float = 0f,
    /** Getaran lateral: cap bergeser tegak lurus arah goresan. */
    val jitterLateral: Float = 0f,
    /** Getaran linear: cap bergeser searah goresan. */
    val jitterLinear: Float = 0f,
    /** Fall off: goresan mulai pekat lalu memudar sepanjang tarikan (0 = tidak memudar). */
    val fallOff: Float = 0f,

    // ---- Runcing (Taper) ----
    /** Opasitas runcing: ujung yang meruncing juga makin transparan. */
    val taperOpacity: Float = 0f,
    /** Ujung runcing: kecil = ujung lancip halus, besar = ujung tumpul. */
    val taperTip: Float = 0.1f,

    // ---- Bentuk (Shape) ----
    /** Jumlah cap di tiap titik (1–16). */
    val count: Int = 1,
    /** Getaran jumlah: jumlah cap tiap titik acak antara 1 dan [count]. */
    val countJitter: Float = 0f,
    /** Putaran awal acak di tiap goresan. */
    val randomized: Boolean = false,
    /** Balik bentuk mendatar / tegak. */
    val flipX: Boolean = false,
    val flipY: Boolean = false,
    /** Getaran kebulatan tegak / mendatar: cap dipipihkan acak. */
    val roundJitterV: Float = 0f,
    val roundJitterH: Float = 0f,
    /** Kebulatan mengikuti tekanan / kemiringan (makin pipih saat tekanan ringan / pena miring). */
    val pressureRound: Float = 0f,
    val tiltRound: Float = 0f,

    // ---- Dinamika (Dynamics) ----
    /** Ukuran menurut kecepatan: + = goresan cepat makin tipis, − = goresan lambat makin tipis. */
    val speedSize: Float = 0f,
    /** Opasitas menurut kecepatan: + = goresan cepat makin transparan, − = goresan lambat makin transparan. */
    val speedOpacity: Float = 0f,
    /** Jarak menurut kecepatan: makin cepat, cap makin renggang. */
    val speedSpacing: Float = 0f,

    // ---- Dinamika warna (Color Dynamics) ----
    /** Getaran warna per cap: saturasi, lebih terang, lebih gelap, warna kedua ([hueJitter] = rona). */
    val satJitter: Float = 0f,
    val lightJitter: Float = 0f,
    val darkJitter: Float = 0f,
    val color2Jitter: Float = 0f,
    /** Getaran warna per goresan: rona, saturasi, lebih terang, lebih gelap, warna kedua. */
    val strokeHue: Float = 0f,
    val strokeSat: Float = 0f,
    val strokeLight: Float = 0f,
    val strokeDark: Float = 0f,
    val strokeColor2: Float = 0f,
    /** Warna menurut tekanan: rona, saturasi, kecerahan (−1..1), menuju warna kedua (berlaku saat tekanan ringan). */
    val pressHue: Float = 0f,
    val pressSat: Float = 0f,
    val pressBright: Float = 0f,
    val pressColor2: Float = 0f,
    /** Warna menurut kemiringan pena: rona, saturasi, kecerahan (−1..1), menuju warna kedua. */
    val tiltHue: Float = 0f,
    val tiltSat: Float = 0f,
    val tiltBright: Float = 0f,
    val tiltColor2: Float = 0f,

    // ---- Pena (Apple Pencil di Procreate) ----
    /** Kurva tekanan kuas ini: titik (x0, y0, x1, y1, …) di antara (0,0) dan (1,1); kosong = lurus. */
    val pressureCurve: List<Float> = emptyList(),
    /** Bleed: tekanan ringan / pena miring hanya menampakkan bagian pekat bentuk dan tekstur (tepi "berdarah"). */
    val pressureBleed: Float = 0f,
    val tiltBleed: Float = 0f,
    /** Sudut mulai kemiringan (derajat dari permukaan layar): efek kemiringan bekerja di bawah sudut ini. */
    val tiltAngle: Float = 90f,

    // ---- Properti ----
    /** Bentuk kuas tetap menghadap ke atas layar walau kanvas diputar (Orient to screen). */
    val orientScreen: Boolean = false,

    // ---- Rendering ----
    /** Mode rendering: 0 glaze ringan, 1 glaze merata, 2 glaze intens, 3 glaze berat, 4 blending merata, 5 blending intens. */
    val render: Int = 0,
    /** Tepi basah: tepi goresan melembut dan sedikit meresap seperti pigmen di kertas. */
    val wetEdges: Float = 0f,
    /** Tepi terbakar: tepi goresan yang menimpa cat lain digelapkan dengan [burntMode]. */
    val burntEdges: Float = 0f,
    val burntMode: Int = 3,
    /**
     * Garis tepi menyatu (huruf gelembung): tebal garis tepi sebagai pecahan ukuran kuas (0 = mati). Goresan diisi
     * [outlineFill] dan diberi garis tepi berwarna kuas (warna aktif, atau [fixedColor] bila diatur). Garis tepi tidak
     * tampil di dalam bentuk yang sudah ada di layer, dan isinya menutup garis tepi lama, jadi goresan yang
     * bersilangan menyatu tanpa garis bertabrakan.
     */
    val outline: Float = 0f,
    /** Warna isi garis tepi menyatu (ARGB); 0 = ikut warna aktif. */
    val outlineFill: Int = 0xFFFFFFFF.toInt(),
    /** Mode campur goresan (indeks sama dengan mode campur layer; 0 = Normal). */
    val blendMode: Int = 0,
    /** Campur luminans: hanya terang-gelap goresan yang dipakai, rona/saturasi cat di bawahnya tetap. */
    val lumBlend: Boolean = false,
    /** Ambang alfa: piksel goresan jadi pekat penuh atau transparan penuh (0 = mati). */
    val alphaThreshold: Float = 0f,

    // ---- Wet Mix (cat basah yang bercampur) ----
    /** Air: makin tinggi makin encer/transparan. */
    val wmDilution: Float = 0f,
    /** Muatan cat di awal goresan (1 = tidak habis); cat menipis sepanjang tarikan. */
    val wmCharge: Float = 1f,
    /** Seberapa banyak cat menempel ke kanvas. */
    val wmAttack: Float = 1f,
    /** Tarikan: seberapa kuat kuas menyeret cat yang sudah ada di kanvas. */
    val wmPull: Float = 0f,
    /** Grade: kasar dan kontrasnya tekstur pada cat. */
    val wmGrade: Float = 0f,
    /** Blur: cat di bawah kuas dilembutkan. */
    val wmBlur: Float = 0f,
    val wmBlurJitter: Float = 0f,
    /** Getaran kebasahan: banyaknya air berubah acak sepanjang goresan. */
    val wmWetJitter: Float = 0f,

    // ---- Kuas ganda (Dual brush) ----
    /** Id kuas kedua yang digabung dengan kuas ini (kosong = tidak ada). */
    val dualId: String = "",
    /** Cara menggabung: 0 Kali (irisan), 1 Gabung, 2 Kurangi, 3 Selisih, 4 Gelapkan, 5 Terangkan. */
    val dualMode: Int = 0,
    /** Ukuran kuas kedua dibanding kuas ini. */
    val dualSize: Float = 1f,
    /**
     * Kuas kedua hasil "Gabungkan kuas" (seperti Combine di Procreate): pengaturannya ikut tersimpan di dalam kuas
     * ini, jadi bisa diatur sendiri tanpa mengubah kuas lain. Bila ada, dipakai sebagai ganti [dualId].
     */
    val dual: Brush? = null,
    /** Warna kuas sendiri (ARGB); 0 = ikut warna aktif. Dipakai bagian-bagian kuas gabungan supaya warnanya berbeda. */
    val fixedColor: Int = 0,
    /**
     * Kuas kedua ikut mewarnai dengan warnanya sendiri ([Brush.fixedColor] kuas kedua, atau warna aktif), di atas cat kuas
     * utama. Dengan cara gabung "Kurangi", bagian yang dikurangi terisi warna kuas kedua (garis tepi dengan isi berwarna).
     * Bila tidak, kuas kedua hanya menjadi topeng.
     */
    val dualPaint: Boolean = false,
) {
    /** Kuas ini gabungan dua kuas (kuas kedua tersimpan di dalamnya). */
    val combined: Boolean get() = dual != null

    /** Kuas melukis basah (Wet Mix aktif atau mode blending): cat langsung bercampur dengan cat di kanvas. */
    fun wetMix(): Boolean =
        wmDilution > 0.001f || wmPull > 0.001f || wmCharge < 0.999f || wmBlur > 0.001f || render >= 4

    /**
     * Seberapa larut cat saat kuas ini dipakai membaur. Otomatis: kuas cat air melarutkan (menyatu halus, tekstur
     * kertas tetap), cat basah (Wet Mix) sebagian menyeret sebagian melarut, airbrush agak lembut, lainnya menyeret.
     */
    fun smudgeWetness(): Float = if (smudgeWet >= 0f) smudgeWet.coerceIn(0f, 1f) else when {
        watercolor -> 0.85f
        wetMix() -> (0.2f + wmBlur * 0.6f).coerceIn(0f, 0.7f)
        tip == TipShape.AIRBRUSH -> 0.4f
        else -> 0f
    }
    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id); put("name", name); put("category", category)
        put("tip", tip.name); put("grain", grain.name)
        put("grainScale", grainScale.toDouble()); put("grainStrength", grainStrength.toDouble())
        put("sizeMax", sizeMax.toDouble()); put("sizeMin", sizeMin.toDouble())
        put("spacing", spacing.toDouble()); put("opacityMax", opacityMax.toDouble()); put("flow", flow.toDouble())
        put("streamline", streamline.toDouble()); put("stabilization", stabilization.toDouble())
        put("streamlinePressure", streamlinePressure.toDouble())
        put("motionFilter", motionFilter.toDouble()); put("motionExpression", motionExpression.toDouble())
        put("pressureSize", pressureSize.toDouble()); put("pressureOpacity", pressureOpacity.toDouble())
        put("tiltSize", tiltSize.toDouble()); put("tiltOpacity", tiltOpacity.toDouble())
        put("taperStart", taperStart.toDouble())
        put("taperEnd", taperEnd.toDouble())
        put("sizeJitter", sizeJitter.toDouble()); put("scatter", scatter.toDouble())
        put("angleJitter", angleJitter.toDouble()); put("opacityJitter", opacityJitter.toDouble())
        put("angleMode", angleMode.name); put("angle", angle.toDouble()); put("roundness", roundness.toDouble())
        put("hueJitter", hueJitter.toDouble()); put("smudgeStrength", smudgeStrength.toDouble())
        put("defaultSize", defaultSize.toDouble()); put("builtIn", builtIn)
        if (tipFile.isNotEmpty()) put("tipFile", tipFile)
        if (grainFile.isNotEmpty()) put("grainFile", grainFile)
        put("stamp", stamp)
        if (grainStroke) put("grainStroke", true)
        fun n(k: String, v: Float, d: Float) { if (v != d) put(k, v.toDouble()) }
        fun z(k: String, v: Boolean) { if (v) put(k, true) }
        n("outline", outline, 0f)
        if (outlineFill != 0xFFFFFFFF.toInt()) put("outlineFill", outlineFill)
        if (smudgeWet >= 0f) put("smudgeWet", smudgeWet.toDouble())
        z("blender", blender)
        n("spacingJitter", spacingJitter, 0f); n("jitterLateral", jitterLateral, 0f); n("jitterLinear", jitterLinear, 0f)
        n("fallOff", fallOff, 0f); n("taperOpacity", taperOpacity, 0f); n("taperTip", taperTip, 0.1f)
        if (count != 1) put("count", count)
        n("countJitter", countJitter, 0f); z("randomized", randomized); z("flipX", flipX); z("flipY", flipY)
        n("roundJitterV", roundJitterV, 0f); n("roundJitterH", roundJitterH, 0f)
        n("pressureRound", pressureRound, 0f); n("tiltRound", tiltRound, 0f)
        n("speedSize", speedSize, 0f); n("speedOpacity", speedOpacity, 0f); n("speedSpacing", speedSpacing, 0f)
        n("satJitter", satJitter, 0f); n("lightJitter", lightJitter, 0f); n("darkJitter", darkJitter, 0f)
        n("color2Jitter", color2Jitter, 0f)
        n("strokeHue", strokeHue, 0f); n("strokeSat", strokeSat, 0f); n("strokeLight", strokeLight, 0f)
        n("strokeDark", strokeDark, 0f); n("strokeColor2", strokeColor2, 0f)
        n("pressHue", pressHue, 0f); n("pressSat", pressSat, 0f); n("pressBright", pressBright, 0f)
        n("pressColor2", pressColor2, 0f)
        n("tiltHue", tiltHue, 0f); n("tiltSat", tiltSat, 0f); n("tiltBright", tiltBright, 0f); n("tiltColor2", tiltColor2, 0f)
        if (pressureCurve.isNotEmpty()) put("pressureCurve", JSONArray().apply { pressureCurve.forEach { put(it.toDouble()) } })
        n("pressureBleed", pressureBleed, 0f); n("tiltBleed", tiltBleed, 0f); n("tiltAngle", tiltAngle, 90f)
        z("orientScreen", orientScreen)
        if (render != 0) put("render", render)
        n("wetEdges", wetEdges, 0f); n("burntEdges", burntEdges, 0f)
        if (burntMode != 3) put("burntMode", burntMode)
        if (blendMode != 0) put("blendMode", blendMode)
        z("lumBlend", lumBlend); n("alphaThreshold", alphaThreshold, 0f)
        n("wmDilution", wmDilution, 0f); n("wmCharge", wmCharge, 1f); n("wmAttack", wmAttack, 1f)
        n("wmPull", wmPull, 0f); n("wmGrade", wmGrade, 0f); n("wmBlur", wmBlur, 0f)
        n("wmBlurJitter", wmBlurJitter, 0f); n("wmWetJitter", wmWetJitter, 0f)
        if (dualId.isNotEmpty() || dual != null) {
            if (dualId.isNotEmpty()) put("dualId", dualId)
            put("dualMode", dualMode); n("dualSize", dualSize, 1f)
        }
        dual?.let { put("dual", it.copy(dual = null, dualId = "").toJson()) }
        if (fixedColor != 0) put("fixedColor", fixedColor)
        z("dualPaint", dualPaint)
        if (watercolor) {
            put("watercolor", true)
            put("wcDensity", wcDensity.toDouble()); put("wcEdge", wcEdge.toDouble()); put("wcSoft", wcSoft.toDouble())
            put("wcBloom", wcBloom.toDouble()); put("wcGrain", wcGrain.toDouble()); put("wcGlaze", wcGlaze)
            n("wcSpread", wcSpread, 0f); n("wcWetTime", wcWetTime, 2.5f)
        }
    }

    companion object {
        fun fromJson(o: JSONObject): Brush {
            fun f(k: String, d: Float) = if (o.has(k)) o.optDouble(k, d.toDouble()).toFloat() else d
            val base = Brush(id = o.getString("id"), name = o.optString("name", "Kuas"), category = o.optString("category", "Kuasku"))
            return base.copy(
                tip = runCatching { TipShape.valueOf(o.optString("tip")) }.getOrDefault(base.tip),
                grain = runCatching { Grain.valueOf(o.optString("grain")) }.getOrDefault(base.grain),
                grainScale = f("grainScale", base.grainScale),
                grainStrength = f("grainStrength", base.grainStrength),
                sizeMax = f("sizeMax", base.sizeMax),
                sizeMin = f("sizeMin", base.sizeMin),
                spacing = f("spacing", base.spacing),
                opacityMax = f("opacityMax", base.opacityMax),
                flow = f("flow", base.flow),
                streamline = f("streamline", base.streamline),
                stabilization = f("stabilization", base.stabilization),
                streamlinePressure = f("streamlinePressure", base.streamlinePressure),
                motionFilter = f("motionFilter", base.motionFilter),
                motionExpression = f("motionExpression", base.motionExpression),
                pressureSize = f("pressureSize", base.pressureSize),
                pressureOpacity = f("pressureOpacity", base.pressureOpacity),
                tiltSize = f("tiltSize", base.tiltSize),
                tiltOpacity = f("tiltOpacity", base.tiltOpacity),
                taperStart = f("taperStart", base.taperStart),
                taperEnd = f("taperEnd", base.taperEnd),
                sizeJitter = f("sizeJitter", base.sizeJitter),
                scatter = f("scatter", base.scatter),
                angleJitter = f("angleJitter", base.angleJitter),
                opacityJitter = f("opacityJitter", base.opacityJitter),
                angleMode = runCatching { AngleMode.valueOf(o.optString("angleMode")) }.getOrDefault(base.angleMode),
                angle = f("angle", base.angle),
                roundness = f("roundness", base.roundness),
                hueJitter = f("hueJitter", base.hueJitter),
                smudgeStrength = f("smudgeStrength", base.smudgeStrength),
                smudgeWet = f("smudgeWet", -1f),
                blender = o.optBoolean("blender", false),
                defaultSize = f("defaultSize", base.defaultSize),
                builtIn = o.optBoolean("builtIn", false),
                tipFile = o.optString("tipFile", ""),
                grainFile = o.optString("grainFile", ""),
                // Kuas stempel hasil impor 0.1.95 belum punya tanda ini: dikenali dari jarak cap lebar tanpa tekanan.
                stamp = if (o.has("stamp")) o.optBoolean("stamp", false) else
                    o.optString("tipFile", "").isNotEmpty() && f("spacing", 0f) >= 1.49f && f("pressureSize", 1f) == 0f,
                grainStroke = o.optBoolean("grainStroke", false),
                watercolor = o.optBoolean("watercolor", false),
                wcDensity = f("wcDensity", base.wcDensity),
                wcEdge = f("wcEdge", base.wcEdge),
                wcSoft = f("wcSoft", base.wcSoft),
                wcBloom = f("wcBloom", base.wcBloom),
                wcGrain = f("wcGrain", base.wcGrain),
                wcGlaze = o.optBoolean("wcGlaze", base.wcGlaze),
                wcSpread = f("wcSpread", 0f),
                wcWetTime = f("wcWetTime", 2.5f),
                spacingJitter = f("spacingJitter", 0f),
                jitterLateral = f("jitterLateral", 0f),
                jitterLinear = f("jitterLinear", 0f),
                fallOff = f("fallOff", 0f),
                taperOpacity = f("taperOpacity", 0f),
                taperTip = f("taperTip", 0.1f),
                count = o.optInt("count", 1).coerceIn(1, 16),
                countJitter = f("countJitter", 0f),
                randomized = o.optBoolean("randomized", false),
                flipX = o.optBoolean("flipX", false),
                flipY = o.optBoolean("flipY", false),
                roundJitterV = f("roundJitterV", 0f),
                roundJitterH = f("roundJitterH", 0f),
                pressureRound = f("pressureRound", 0f),
                tiltRound = f("tiltRound", 0f),
                speedSize = f("speedSize", 0f),
                speedOpacity = f("speedOpacity", 0f),
                speedSpacing = f("speedSpacing", 0f),
                satJitter = f("satJitter", 0f),
                lightJitter = f("lightJitter", 0f),
                darkJitter = f("darkJitter", 0f),
                color2Jitter = f("color2Jitter", 0f),
                strokeHue = f("strokeHue", 0f),
                strokeSat = f("strokeSat", 0f),
                strokeLight = f("strokeLight", 0f),
                strokeDark = f("strokeDark", 0f),
                strokeColor2 = f("strokeColor2", 0f),
                pressHue = f("pressHue", 0f),
                pressSat = f("pressSat", 0f),
                pressBright = f("pressBright", 0f),
                pressColor2 = f("pressColor2", 0f),
                tiltHue = f("tiltHue", 0f),
                tiltSat = f("tiltSat", 0f),
                tiltBright = f("tiltBright", 0f),
                tiltColor2 = f("tiltColor2", 0f),
                pressureCurve = o.optJSONArray("pressureCurve")?.let { a ->
                    List(a.length()) { a.optDouble(it, 0.0).toFloat().coerceIn(0f, 1f) }.take(8).let { if (it.size % 2 == 0) it else it.dropLast(1) }
                } ?: emptyList(),
                pressureBleed = f("pressureBleed", 0f),
                tiltBleed = f("tiltBleed", 0f),
                tiltAngle = f("tiltAngle", 90f).coerceIn(20f, 90f),
                orientScreen = o.optBoolean("orientScreen", false),
                render = o.optInt("render", 0).coerceIn(0, 5),
                wetEdges = f("wetEdges", 0f),
                outline = f("outline", 0f).coerceIn(0f, 0.45f),
                outlineFill = o.optInt("outlineFill", 0xFFFFFFFF.toInt()),
                burntEdges = f("burntEdges", 0f),
                burntMode = o.optInt("burntMode", 3).coerceIn(0, 25),
                blendMode = o.optInt("blendMode", 0).coerceIn(0, 25),
                lumBlend = o.optBoolean("lumBlend", false),
                alphaThreshold = f("alphaThreshold", 0f),
                wmDilution = f("wmDilution", 0f),
                wmCharge = f("wmCharge", 1f),
                wmAttack = f("wmAttack", 1f),
                wmPull = f("wmPull", 0f),
                wmGrade = f("wmGrade", 0f),
                wmBlur = f("wmBlur", 0f),
                wmBlurJitter = f("wmBlurJitter", 0f),
                wmWetJitter = f("wmWetJitter", 0f),
                dualId = o.optString("dualId", ""),
                dualMode = o.optInt("dualMode", 0).coerceIn(0, 5),
                dualSize = f("dualSize", 1f).coerceIn(0.1f, 4f),
                fixedColor = o.optInt("fixedColor", 0).let { if (it == 0) 0 else it or 0xFF000000.toInt() },
                dualPaint = o.optBoolean("dualPaint", false),
                dual = o.optJSONObject("dual")?.let { d ->
                    runCatching { fromJson(d).copy(dual = null, dualId = "", builtIn = false, stamp = false) }.getOrNull()
                },
            )
        }
    }
}

/** Pustaka kuas bawaan. */
object Brushes {
    val categories = listOf(
        "Sketsa", "Tinta", "Pena", "Spidol", "Melukis", "Cat Air", "Pastel & Arang", "Airbrush", "Tekstur",
        "Kaligrafi", "Khat Arab", "Lettering", "Komik", "Desain", "Grunge", "Kreatif",
    )

    val builtIn: List<Brush> = listOf(
        Brush(
            id = "pensil6b", name = "Pensil 6B", category = "Sketsa",
            tip = TipShape.PENCIL, grain = Grain.PAPER, grainScale = 0.6f, grainStrength = 0.85f,
            sizeMax = 26f, spacing = 0.06f, flow = 0.6f,
            pressureSize = 0.45f, pressureOpacity = 0.85f, tiltSize = 1f, tiltOpacity = 0.6f,
            streamline = 0.05f, defaultSize = 0.35f,
        ),
        Brush(
            id = "pensilhb", name = "Pensil HB", category = "Sketsa",
            tip = TipShape.PENCIL, grain = Grain.PAPER, grainScale = 0.5f, grainStrength = 0.9f,
            sizeMax = 14f, spacing = 0.06f, flow = 0.45f,
            pressureSize = 0.3f, pressureOpacity = 0.95f, tiltSize = 0.8f, tiltOpacity = 0.5f,
            defaultSize = 0.35f,
        ),
        Brush(
            id = "pensilwarna", name = "Pensil Warna", category = "Sketsa",
            tip = TipShape.PENCIL, grain = Grain.ROUGH, grainScale = 0.7f, grainStrength = 0.7f,
            sizeMax = 30f, spacing = 0.06f, flow = 0.7f,
            pressureSize = 0.35f, pressureOpacity = 0.7f, tiltSize = 1.2f, tiltOpacity = 0.4f,
            defaultSize = 0.35f,
        ),
        Brush(
            id = "tintastudio", name = "Tinta Studio", category = "Tinta",
            tip = TipShape.HARD_ROUND, sizeMax = 40f, spacing = 0.03f,
            pressureSize = 0.9f, streamline = 0.35f, taperStart = 0.15f, taperEnd = 0.15f, defaultSize = 0.25f,
        ),
        Brush(
            id = "penateknis", name = "Pena Teknis", category = "Tinta",
            tip = TipShape.HARD_ROUND, sizeMax = 20f, spacing = 0.03f,
            pressureSize = 0f, streamline = 0.25f, defaultSize = 0.25f,
        ),
        Brush(
            id = "tintakering", name = "Tinta Kering", category = "Tinta",
            tip = TipShape.INK_ROUGH, grain = Grain.ROUGH, grainStrength = 0.35f,
            sizeMax = 40f, spacing = 0.05f, flow = 0.95f, pressureSize = 0.8f, angleJitter = 1f,
            streamline = 0.15f, defaultSize = 0.3f,
        ),
        Brush(
            id = "monoline", name = "Monoline", category = "Tinta",
            tip = TipShape.HARD_ROUND, sizeMax = 30f, spacing = 0.03f, pressureSize = 0f,
            streamline = 0.55f, defaultSize = 0.3f,
        ),
        Brush(
            id = "spidol", name = "Spidol", category = "Tinta",
            tip = TipShape.HARD_ROUND, roundness = 0.7f, angle = 30f,
            sizeMax = 44f, spacing = 0.05f, flow = 0.35f, opacityMax = 0.9f, pressureSize = 0.15f,
            defaultSize = 0.4f,
        ),
        Brush(
            id = "kuasbulat", name = "Kuas Bulat", category = "Melukis",
            tip = TipShape.SOFT_ROUND, grain = Grain.CANVAS, grainStrength = 0.3f,
            sizeMax = 120f, spacing = 0.06f, flow = 0.45f, pressureSize = 0.6f, pressureOpacity = 0.5f,
            defaultSize = 0.35f,
        ),
        Brush(
            id = "kuaspipih", name = "Kuas Pipih", category = "Melukis",
            tip = TipShape.FLAT, grain = Grain.CANVAS, grainStrength = 0.35f, roundness = 0.35f,
            angleMode = AngleMode.FOLLOW, angle = 90f,
            sizeMax = 120f, spacing = 0.04f, flow = 0.6f, pressureSize = 0.4f, pressureOpacity = 0.4f,
            defaultSize = 0.35f,
        ),
        Brush(
            id = "catminyak", name = "Cat Minyak", category = "Melukis",
            tip = TipShape.FLAT, grain = Grain.CANVAS, grainStrength = 0.5f,
            angleMode = AngleMode.FOLLOW, angle = 90f,
            sizeMax = 150f, spacing = 0.04f, flow = 0.8f, pressureSize = 0.5f, pressureOpacity = 0.3f,
            defaultSize = 0.35f,
        ),
        // Cat air: sapuan diolah saat digabung (tepi basah, bercak, granulasi, lapisan menumpuk). Aliran (flow) dan
        // jarak cap mengatur seberapa rata sapuan terisi sebelum diolah.
        Brush(
            id = "wcutama", name = "Cat Air Utama", category = "Cat Air",
            tip = TipShape.SOFT_ROUND, sizeMax = 220f, sizeMin = 2f, spacing = 0.04f, flow = 0.3f,
            pressureSize = 0.6f, pressureOpacity = 0.25f, streamline = 0.1f, defaultSize = 0.55f,
            watercolor = true, wcDensity = 0.62f, wcEdge = 0.75f, wcSoft = 0.08f, wcBloom = 0.6f, wcGrain = 0.55f,
            wcSpread = 0.35f, wcWetTime = 2.5f,
        ),
        Brush(
            id = "catair", name = "Cat Air Basah", category = "Cat Air",
            tip = TipShape.SOFT_ROUND, sizeMax = 320f, sizeMin = 4f, spacing = 0.04f, flow = 0.22f,
            pressureSize = 0.5f, pressureOpacity = 0.3f, defaultSize = 0.55f,
            watercolor = true, wcDensity = 0.45f, wcEdge = 0.3f, wcSoft = 0.75f, wcBloom = 0.85f, wcGrain = 0.45f,
            wcSpread = 0.6f, wcWetTime = 4f,
        ),
        Brush(
            id = "wcdetail", name = "Detail Cat Air", category = "Cat Air",
            tip = TipShape.SOFT_ROUND, sizeMax = 36f, sizeMin = 1f, spacing = 0.04f, flow = 0.45f,
            pressureSize = 0.85f, pressureOpacity = 0.3f, streamline = 0.2f, defaultSize = 0.35f,
            watercolor = true, wcDensity = 0.75f, wcEdge = 0.55f, wcSoft = 0f, wcBloom = 0.35f, wcGrain = 0.4f,
            wcSpread = 0.15f, wcWetTime = 1.5f,
        ),
        Brush(
            id = "wclapis", name = "Lapisan Tipis", category = "Cat Air",
            tip = TipShape.SOFT_ROUND, sizeMax = 500f, sizeMin = 10f, spacing = 0.05f, flow = 0.25f,
            pressureSize = 0.3f, pressureOpacity = 0.2f, defaultSize = 0.45f,
            watercolor = true, wcDensity = 0.3f, wcEdge = 0.6f, wcSoft = 0.2f, wcBloom = 0.5f, wcGrain = 0.35f,
            wcSpread = 0.4f, wcWetTime = 3.5f,
        ),
        Brush(
            id = "wckering", name = "Kuas Kering", category = "Cat Air",
            tip = TipShape.CHALK, grain = Grain.ROUGH, grainScale = 0.8f, grainStrength = 0.75f,
            sizeMax = 120f, sizeMin = 3f, spacing = 0.04f, flow = 0.5f, angleMode = AngleMode.FOLLOW,
            pressureSize = 0.4f, pressureOpacity = 0.4f, defaultSize = 0.4f,
            watercolor = true, wcDensity = 0.7f, wcEdge = 0.35f, wcSoft = 0f, wcBloom = 0.4f, wcGrain = 1f,
        ),
        // Pembaur cat air (seperti Wet Blender): tidak menambah warna, cat di bawahnya larut dan menyatu halus,
        // tekstur kertas tetap. Bisa dipakai dengan alat Kuas maupun alat Baur.
        Brush(
            id = "wcpembaur", name = "Pembaur Cat Air", category = "Cat Air",
            tip = TipShape.SOFT_ROUND, sizeMax = 320f, sizeMin = 8f, spacing = 0.04f, flow = 1f,
            pressureSize = 0.35f, pressureOpacity = 0.6f, defaultSize = 0.5f,
            watercolor = true, wcDensity = 0.5f, wcEdge = 0.4f, wcSoft = 0.6f, wcBloom = 0.5f, wcGrain = 0.45f,
            smudgeStrength = 0.85f, smudgeWet = 1f, blender = true,
        ),
        Brush(
            id = "wcpercik", name = "Percikan Cat Air", category = "Cat Air",
            tip = TipShape.SPLATTER, sizeMax = 260f, spacing = 0.55f, flow = 1f,
            scatter = 0.7f, sizeJitter = 0.6f, angleJitter = 1f, pressureSize = 0.3f, defaultSize = 0.4f,
            watercolor = true, wcDensity = 0.6f, wcEdge = 0.8f, wcSoft = 0f, wcBloom = 0.3f, wcGrain = 0.5f,
        ),
        // Latar cat air (seperti kuas "background" cat air Procreate): kuas sangat besar berujung bercak bertepi
        // kembang kol dengan tekstur awan cat air yang menyatu di kanvas (awan pekat-pucat, bercak backrun, urat air),
        // diolah sebagai cat air (tepi gelap, granulasi kertas, lapisan menumpuk). Beberapa sapuan menutup kanvas.
        Brush(
            id = "wclatar", name = "Latar Cat Air", category = "Cat Air",
            tip = TipShape.WC_BLOB, grain = Grain.WC_CLOUD, grainStrength = 0.8f, grainScale = 2.6f, grainStroke = true,
            sizeMax = 2400f, sizeMin = 40f, spacing = 0.1f, flow = 0.45f,
            angleJitter = 1f, sizeJitter = 0.2f, scatter = 0.1f,
            pressureSize = 0.25f, pressureOpacity = 0.35f, streamline = 0.1f, defaultSize = 0.6f,
            watercolor = true, wcDensity = 0.72f, wcEdge = 0.85f, wcSoft = 0.12f, wcBloom = 0.7f, wcGrain = 0.6f,
        ),
        Brush(
            id = "wcbercak", name = "Bercak Cat Air", category = "Cat Air",
            tip = TipShape.WC_BLOB, grain = Grain.WC_CLOUD, grainStrength = 0.7f, grainScale = 1.6f, grainStroke = true,
            sizeMax = 900f, sizeMin = 10f, spacing = 0.12f, flow = 0.4f,
            angleJitter = 1f, sizeJitter = 0.25f, scatter = 0.12f,
            pressureSize = 0.4f, pressureOpacity = 0.4f, streamline = 0.1f, defaultSize = 0.45f,
            watercolor = true, wcDensity = 0.68f, wcEdge = 0.85f, wcSoft = 0.1f, wcBloom = 0.65f, wcGrain = 0.6f,
        ),
        Brush(
            id = "gouache", name = "Gouache", category = "Melukis",
            tip = TipShape.CHALK, grain = Grain.PAPER, grainStrength = 0.4f,
            sizeMax = 100f, spacing = 0.05f, flow = 0.85f, pressureSize = 0.5f, pressureOpacity = 0.2f,
            angleJitter = 1f, defaultSize = 0.35f,
        ),
        Brush(
            id = "airlembut", name = "Airbrush Lembut", category = "Airbrush",
            tip = TipShape.AIRBRUSH, sizeMax = 600f, spacing = 0.04f, flow = 0.08f,
            pressureSize = 0f, pressureOpacity = 0.9f, defaultSize = 0.35f,
        ),
        Brush(
            id = "airkeras", name = "Airbrush Sedang", category = "Airbrush",
            tip = TipShape.SOFT_ROUND, sizeMax = 300f, spacing = 0.05f, flow = 0.25f,
            pressureSize = 0.2f, pressureOpacity = 0.8f, defaultSize = 0.3f,
        ),
        Brush(
            id = "bulatkeras", name = "Bulat Keras", category = "Airbrush",
            tip = TipShape.HARD_ROUND, sizeMax = 220f, spacing = 0.04f, flow = 1f,
            pressureSize = 0.5f, defaultSize = 0.25f,
        ),
        Brush(
            id = "arang", name = "Arang", category = "Tekstur",
            tip = TipShape.CHARCOAL, grain = Grain.ROUGH, grainStrength = 0.7f,
            sizeMax = 60f, spacing = 0.06f, flow = 0.7f, pressureSize = 0.5f, pressureOpacity = 0.6f,
            angleJitter = 1f, tiltSize = 1f, defaultSize = 0.35f,
        ),
        Brush(
            id = "kapur", name = "Kapur", category = "Tekstur",
            tip = TipShape.CHALK, grain = Grain.PAPER, grainScale = 1.2f, grainStrength = 0.8f,
            sizeMax = 50f, spacing = 0.06f, flow = 0.8f, pressureSize = 0.4f, pressureOpacity = 0.4f,
            angleJitter = 1f, defaultSize = 0.35f,
        ),
        Brush(
            id = "percik", name = "Percik", category = "Tekstur",
            tip = TipShape.SPLATTER, sizeMax = 220f, spacing = 0.45f, flow = 1f,
            scatter = 0.8f, sizeJitter = 0.6f, angleJitter = 1f, opacityJitter = 0.4f, pressureSize = 0.3f,
            defaultSize = 0.35f,
        ),
        Brush(
            id = "bintik", name = "Bintik Halus", category = "Tekstur",
            tip = TipShape.AIRBRUSH, grain = Grain.NOISE, grainScale = 0.5f, grainStrength = 1f,
            sizeMax = 300f, spacing = 0.06f, flow = 0.3f, pressureSize = 0.1f, pressureOpacity = 0.6f,
            defaultSize = 0.3f,
        ),
        Brush(
            id = "kaligrafi", name = "Kaligrafi", category = "Kaligrafi",
            tip = TipShape.HARD_ROUND, roundness = 0.2f, angle = 45f,
            sizeMax = 50f, spacing = 0.03f, pressureSize = 0.5f, streamline = 0.3f, defaultSize = 0.4f,
        ),
        Brush(
            id = "kaligrafibasah", name = "Kaligrafi Basah", category = "Kaligrafi",
            tip = TipShape.INK_ROUGH, roundness = 0.25f, angle = 40f,
            sizeMax = 60f, spacing = 0.03f, flow = 0.9f, pressureSize = 0.7f, streamline = 0.3f,
            taperStart = 0.2f, taperEnd = 0.2f, defaultSize = 0.4f,
        ),

        // ---- Khat Arab ----
        // Ujung qalam pipih yang dipotong miring, dipegang ±70° terhadap garis tulis (tepinya condong ke kanan
        // atas): tarikan mendatar paling tebal, alif lebih ramping, dan tarikan miring ke kiri bawah menipis,
        // seperti khat Naskhi dan Tsuluts. Sudut -70° di kanvas (sumbu y ke bawah).
        Brush(
            id = "qalamnaskhi", name = "Qalam Naskhi", category = "Khat Arab",
            tip = TipShape.HARD_ROUND, roundness = 0.16f, angle = -70f,
            sizeMax = 40f, sizeMin = 2f, spacing = 0.02f, pressureSize = 0.3f, streamline = 0.35f, defaultSize = 0.45f,
        ),
        Brush(
            id = "qalamtsuluts", name = "Qalam Tsuluts", category = "Khat Arab",
            tip = TipShape.HARD_ROUND, roundness = 0.13f, angle = -70f,
            sizeMax = 90f, sizeMin = 4f, spacing = 0.015f, pressureSize = 0.35f, streamline = 0.45f,
            taperEnd = 0.08f, defaultSize = 0.45f,
        ),
        Brush(
            id = "qalambasah", name = "Qalam Tinta Basah", category = "Khat Arab",
            tip = TipShape.INK_ROUGH, roundness = 0.2f, angle = -70f,
            sizeMax = 60f, sizeMin = 3f, spacing = 0.02f, flow = 0.95f, pressureSize = 0.45f, pressureOpacity = 0.15f,
            streamline = 0.35f, taperStart = 0.05f, taperEnd = 0.12f, defaultSize = 0.45f,
        ),
        Brush(
            id = "spidolkhat", name = "Spidol Khat", category = "Khat Arab",
            tip = TipShape.HARD_ROUND, roundness = 0.25f, angle = -70f,
            sizeMax = 30f, sizeMin = 2f, spacing = 0.02f, streamline = 0.3f, defaultSize = 0.5f,
        ),

        // ---- Pena ----
        Brush(
            id = "penagel", name = "Pena Gel", category = "Pena",
            tip = TipShape.HARD_ROUND, sizeMax = 14f, spacing = 0.03f,
            pressureSize = 0.45f, streamline = 0.3f, speedSize = 0.15f, taperStart = 0.06f, taperEnd = 0.06f,
            defaultSize = 0.3f,
        ),
        Brush(
            id = "fineliner", name = "Fineliner", category = "Pena",
            tip = TipShape.HARD_ROUND, sizeMax = 8f, spacing = 0.03f,
            pressureSize = 0.12f, streamline = 0.4f, defaultSize = 0.4f,
        ),
        Brush(
            id = "penacelup", name = "Pena Celup", category = "Pena",
            tip = TipShape.HARD_ROUND, roundness = 0.6f, angle = 45f, sizeMax = 26f, spacing = 0.02f,
            pressureSize = 0.95f, pressureCurve = listOf(0.45f, 0.25f), streamline = 0.35f, speedSize = 0.35f,
            taperStart = 0.2f, taperEnd = 0.25f, taperTip = 0.02f, defaultSize = 0.35f,
        ),
        Brush(
            id = "penakuas", name = "Pena Kuas", category = "Pena",
            tip = TipShape.HARD_ROUND, sizeMax = 40f, spacing = 0.02f,
            pressureSize = 0.95f, streamline = 0.3f, speedSize = 0.45f, pressureBleed = 0.15f,
            taperStart = 0.2f, taperEnd = 0.3f, taperTip = 0.02f, defaultSize = 0.35f,
        ),
        Brush(
            id = "pensilmekanik", name = "Pensil Mekanik", category = "Sketsa",
            tip = TipShape.PENCIL, grain = Grain.PAPER, grainScale = 0.5f, grainStrength = 0.7f,
            sizeMax = 8f, spacing = 0.05f, flow = 0.75f, pressureSize = 0.25f, pressureOpacity = 0.7f,
            pressureBleed = 0.3f, streamline = 0.1f, defaultSize = 0.4f,
        ),
        Brush(
            id = "pensilarsir", name = "Pensil Arsir", category = "Sketsa",
            tip = TipShape.PENCIL, grain = Grain.PAPER, grainScale = 0.8f, grainStrength = 0.9f,
            sizeMax = 40f, spacing = 0.05f, flow = 0.5f, pressureSize = 0.3f, pressureOpacity = 0.8f,
            tiltSize = 1.6f, tiltOpacity = 0.5f, tiltBleed = 0.6f, tiltAngle = 60f, defaultSize = 0.35f,
        ),
        // ---- Spidol ----
        Brush(
            id = "spidolalkohol", name = "Spidol Alkohol", category = "Spidol",
            tip = TipShape.HARD_ROUND, roundness = 0.55f, angle = 30f, sizeMax = 60f, spacing = 0.03f,
            flow = 0.35f, opacityMax = 0.9f, pressureSize = 0.2f, speedOpacity = 0.4f, defaultSize = 0.4f,
        ),
        Brush(
            id = "stabilo", name = "Stabilo", category = "Spidol",
            tip = TipShape.FLAT, roundness = 0.35f, angle = 90f, orientScreen = true, sizeMax = 60f, spacing = 0.03f,
            flow = 1f, opacityMax = 0.6f, pressureSize = 0f, streamline = 0.4f, defaultSize = 0.45f,
            // Seperti stabilo sungguhan: goresan rata (tidak menumpuk) dan menembus tulisan di bawahnya (multiply).
            render = 1, blendMode = 1,
        ),
        Brush(
            id = "spidolbasah", name = "Spidol Tepi Basah", category = "Spidol",
            tip = TipShape.HARD_ROUND, roundness = 0.8f, sizeMax = 60f, spacing = 0.03f, flow = 0.55f,
            opacityMax = 0.85f, pressureSize = 0.2f, streamline = 0.2f, render = 1, wetEdges = 0.6f, defaultSize = 0.4f,
        ),
        Brush(
            id = "spidolkering", name = "Spidol Kering", category = "Spidol",
            tip = TipShape.CHALK, grain = Grain.ROUGH, grainStrength = 0.5f, sizeMax = 50f, spacing = 0.04f,
            flow = 0.85f, pressureSize = 0.25f, speedOpacity = 0.6f, pressureBleed = 0.45f, defaultSize = 0.4f,
        ),
        // ---- Pastel & arang ----
        Brush(
            id = "pastelkapur", name = "Pastel Kapur", category = "Pastel & Arang",
            tip = TipShape.CHALK, grain = Grain.PAPER, grainScale = 1.1f, grainStrength = 0.9f,
            sizeMax = 60f, spacing = 0.05f, flow = 0.85f, pressureSize = 0.35f, pressureBleed = 0.6f,
            tiltSize = 1f, tiltBleed = 0.5f, angleJitter = 1f, satJitter = 0.05f, defaultSize = 0.35f,
        ),
        Brush(
            id = "pastelminyak", name = "Pastel Minyak", category = "Pastel & Arang",
            tip = TipShape.CHARCOAL, grain = Grain.CANVAS, grainStrength = 0.6f,
            sizeMax = 60f, spacing = 0.04f, flow = 0.95f, pressureSize = 0.4f, pressureBleed = 0.4f,
            angleJitter = 1f, darkJitter = 0.06f, defaultSize = 0.35f,
        ),
        Brush(
            id = "arangbatang", name = "Arang Batang", category = "Pastel & Arang",
            tip = TipShape.CHARCOAL, grain = Grain.ROUGH, grainStrength = 0.8f, roundness = 0.45f,
            angleMode = AngleMode.TILT, sizeMax = 70f, spacing = 0.05f, flow = 0.8f, pressureSize = 0.4f,
            pressureOpacity = 0.5f, tiltSize = 1.5f, tiltBleed = 0.8f, tiltAngle = 60f, defaultSize = 0.35f,
        ),
        // ---- Lettering ----
        Brush(
            id = "monoletter", name = "Monoline Lettering", category = "Lettering",
            tip = TipShape.HARD_ROUND, sizeMax = 40f, spacing = 0.02f, pressureSize = 0f,
            streamline = 0.6f, taperStart = 0.04f, taperEnd = 0.04f, taperTip = 0.4f, defaultSize = 0.35f,
        ),
        // Huruf gelembung: goresan berisi putih dengan garis tepi warna kuas; goresan yang bersilangan menyatu.
        Brush(
            id = "gelembung", name = "Huruf Gelembung", category = "Lettering",
            tip = TipShape.HARD_ROUND, sizeMax = 140f, sizeMin = 4f, spacing = 0.03f, pressureSize = 0.15f,
            streamline = 0.5f, outline = 0.12f, defaultSize = 0.35f,
        ),
        Brush(
            id = "gelembungtebal", name = "Gelembung Garis Tebal", category = "Lettering",
            tip = TipShape.HARD_ROUND, sizeMax = 160f, sizeMin = 6f, spacing = 0.03f, pressureSize = 0.1f,
            streamline = 0.5f, outline = 0.22f, defaultSize = 0.35f,
        ),
        Brush(
            id = "kuasletter", name = "Kuas Lettering", category = "Lettering",
            tip = TipShape.HARD_ROUND, sizeMax = 50f, spacing = 0.02f, pressureSize = 0.95f,
            pressureCurve = listOf(0.35f, 0.15f, 0.7f, 0.85f), streamline = 0.55f,
            taperStart = 0.1f, taperEnd = 0.15f, taperTip = 0.05f, defaultSize = 0.35f,
        ),
        Brush(
            id = "lettergradasi", name = "Lettering Gradasi", category = "Lettering",
            tip = TipShape.HARD_ROUND, sizeMax = 50f, spacing = 0.02f, pressureSize = 0.9f,
            streamline = 0.5f, pressColor2 = 1f, taperStart = 0.1f, taperEnd = 0.15f, defaultSize = 0.35f,
        ),
        // ---- Komik ----
        Brush(
            id = "tintakomik", name = "Tinta Komik", category = "Komik",
            tip = TipShape.HARD_ROUND, sizeMax = 30f, spacing = 0.02f, pressureSize = 0.95f,
            streamline = 0.45f, speedSize = 0.3f, taperStart = 0.25f, taperEnd = 0.3f, taperTip = 0.02f,
            defaultSize = 0.35f,
        ),
        Brush(
            id = "arsirkomik", name = "Arsir Komik", category = "Komik",
            tip = TipShape.HARD_ROUND, sizeMax = 12f, spacing = 0.03f, pressureSize = 0.6f,
            streamline = 0.2f, taperStart = 0.3f, taperEnd = 0.35f, taperTip = 0.02f, defaultSize = 0.35f,
        ),
        Brush(
            id = "rastertitik", name = "Raster Titik", category = "Komik",
            tip = TipShape.HARD_ROUND, sizeMax = 30f, spacing = 1.3f, pressureSize = 0.8f, defaultSize = 0.3f,
        ),
        Brush(
            id = "garislaju", name = "Garis Laju", category = "Komik",
            tip = TipShape.HARD_ROUND, sizeMax = 20f, spacing = 0.03f, pressureSize = 0.5f,
            streamline = 0.5f, fallOff = 0.6f, taperStart = 0.08f, defaultSize = 0.35f,
        ),
        // ---- Desain ----
        Brush(
            id = "garisteknik", name = "Garis Teknik", category = "Desain",
            tip = TipShape.HARD_ROUND, sizeMax = 16f, spacing = 0.02f, pressureSize = 0f,
            streamline = 0.8f, motionFilter = 0.3f, defaultSize = 0.3f,
        ),
        Brush(
            id = "tepilembut", name = "Bayangan Lembut", category = "Desain",
            tip = TipShape.SOFT_ROUND, sizeMax = 300f, spacing = 0.05f, flow = 0.12f, pressureSize = 0.2f,
            pressureOpacity = 0.8f, speedOpacity = 0.3f, defaultSize = 0.4f,
        ),
        // ---- Grunge ----
        Brush(
            id = "semprotkasar", name = "Semprotan Kasar", category = "Grunge",
            tip = TipShape.SPLATTER, sizeMax = 160f, spacing = 0.5f, count = 4, countJitter = 0.7f,
            scatter = 0.9f, sizeJitter = 0.6f, opacityJitter = 0.5f, angleJitter = 1f, pressureSize = 0.3f,
            defaultSize = 0.35f,
        ),
        Brush(
            id = "bercaktinta", name = "Bercak Tinta", category = "Grunge",
            tip = TipShape.SPLATTER, sizeMax = 260f, spacing = 0.9f, spacingJitter = 1f, scatter = 1f,
            sizeJitter = 0.8f, angleJitter = 1f, randomized = true, flipX = false, pressureSize = 0.4f,
            defaultSize = 0.35f,
        ),
        Brush(
            id = "goreskering", name = "Goresan Kering", category = "Grunge",
            tip = TipShape.CHARCOAL, grain = Grain.ROUGH, grainStrength = 0.9f, sizeMax = 80f, spacing = 0.04f,
            flow = 0.9f, pressureSize = 0.4f, pressureBleed = 0.8f, jitterLateral = 0.1f, speedOpacity = 0.3f,
            defaultSize = 0.35f,
        ),
        // ---- Kreatif ----
        Brush(
            id = "konfeti", name = "Konfeti", category = "Kreatif",
            tip = TipShape.HARD_ROUND, sizeMax = 40f, spacing = 0.8f, count = 3, countJitter = 0.6f,
            scatter = 1f, sizeJitter = 0.7f, hueJitter = 1f, satJitter = 0.2f, pressureSize = 0.3f,
            defaultSize = 0.35f,
        ),
        Brush(
            id = "kelip", name = "Kelip Bintang", category = "Kreatif",
            tip = TipShape.SOFT_ROUND, sizeMax = 40f, spacing = 1f, count = 2, scatter = 1.2f,
            sizeJitter = 0.9f, lightJitter = 0.6f, opacityJitter = 0.4f, pressureSize = 0.3f, defaultSize = 0.35f,
        ),
        Brush(
            id = "duawarna", name = "Dua Warna", category = "Kreatif",
            tip = TipShape.HARD_ROUND, sizeMax = 40f, spacing = 0.25f, count = 2, scatter = 0.4f,
            color2Jitter = 0.5f, sizeJitter = 0.3f, pressureSize = 0.4f, defaultSize = 0.35f,
        ),
        Brush(
            id = "rumput", name = "Rumput", category = "Kreatif",
            tip = TipShape.FLAT, roundness = 0.12f, angle = 90f, sizeMax = 80f, spacing = 0.35f, count = 3,
            countJitter = 0.5f, jitterLateral = 0.6f, angleJitter = 0.08f, sizeJitter = 0.5f, darkJitter = 0.3f,
            color2Jitter = 0.25f, pressureSize = 0.5f, defaultSize = 0.35f,
        ),
        Brush(
            id = "warnaacak", name = "Warna Tiap Goresan", category = "Kreatif",
            tip = TipShape.HARD_ROUND, sizeMax = 40f, spacing = 0.03f, strokeHue = 0.5f, strokeLight = 0.2f,
            strokeDark = 0.2f, pressureSize = 0.7f, streamline = 0.3f, defaultSize = 0.35f,
        ),
        // ---- Rendering, Wet Mix, kuas ganda (0.1.108) ----
        Brush(
            id = "cahaya", name = "Cahaya", category = "Kreatif",
            tip = TipShape.SOFT_ROUND, sizeMax = 200f, spacing = 0.05f, flow = 0.35f, pressureSize = 0.4f,
            pressureOpacity = 0.6f, render = 1, blendMode = 9, defaultSize = 0.4f,
        ),
        Brush(
            id = "gouachebasah", name = "Gouache Basah", category = "Melukis",
            tip = TipShape.HARD_ROUND, grain = Grain.CANVAS, grainStrength = 0.25f, sizeMax = 100f, spacing = 0.05f,
            flow = 0.9f, pressureSize = 0.5f, pressureOpacity = 0.3f, render = 4,
            wmDilution = 0.08f, wmCharge = 0.85f, wmAttack = 0.9f, wmPull = 0.2f, defaultSize = 0.35f,
        ),
        Brush(
            id = "minyakbasah", name = "Minyak Basah", category = "Melukis",
            tip = TipShape.FLAT, roundness = 0.4f, angleMode = AngleMode.FOLLOW, angle = 90f,
            grain = Grain.CANVAS, grainStrength = 0.45f, sizeMax = 150f, spacing = 0.04f, flow = 0.9f,
            pressureSize = 0.45f, pressureOpacity = 0.3f, render = 5,
            wmDilution = 0.15f, wmCharge = 0.5f, wmAttack = 0.7f, wmPull = 0.5f, wmGrade = 0.4f, defaultSize = 0.35f,
        ),
        Brush(
            id = "akrilikkering", name = "Akrilik Kering", category = "Melukis",
            tip = TipShape.FLAT, roundness = 0.5f, angleMode = AngleMode.FOLLOW, angle = 90f,
            grain = Grain.ROUGH, grainStrength = 0.8f, sizeMax = 120f, spacing = 0.04f, flow = 0.9f,
            pressureSize = 0.4f, pressureBleed = 0.5f, render = 4,
            wmCharge = 0.3f, wmAttack = 0.9f, wmPull = 0.15f, wmGrade = 0.5f, defaultSize = 0.35f,
        ),
        Brush(
            id = "pembaurbasah", name = "Pembaur Basah", category = "Melukis",
            tip = TipShape.SOFT_ROUND, sizeMax = 160f, spacing = 0.05f, flow = 0.8f, pressureSize = 0.4f,
            render = 4, wmCharge = 0f, wmPull = 0.85f, wmBlur = 0.35f, wmDilution = 0.3f, defaultSize = 0.4f,
        ),
        Brush(
            id = "catairwm", name = "Cat Air Campur", category = "Cat Air",
            tip = TipShape.SOFT_ROUND, sizeMax = 200f, sizeMin = 3f, spacing = 0.05f, flow = 0.5f,
            pressureSize = 0.5f, pressureOpacity = 0.3f, render = 4,
            wmDilution = 0.65f, wmCharge = 0.6f, wmAttack = 0.6f, wmPull = 0.35f, wmBlur = 0.25f, wmWetJitter = 0.4f,
            defaultSize = 0.5f,
        ),
        Brush(
            id = "tintaterbakar", name = "Tinta Terbakar", category = "Tinta",
            tip = TipShape.INK_ROUGH, sizeMax = 50f, spacing = 0.04f, flow = 0.8f, pressureSize = 0.8f,
            render = 2, burntEdges = 0.7f, burntMode = 3, streamline = 0.2f, defaultSize = 0.3f,
        ),
        Brush(
            id = "tintategas", name = "Tinta Tegas Bertekstur", category = "Komik",
            tip = TipShape.INK_ROUGH, grain = Grain.ROUGH, grainStrength = 0.6f, sizeMax = 40f, spacing = 0.03f,
            pressureSize = 0.85f, streamline = 0.35f, render = 3, alphaThreshold = 0.45f, defaultSize = 0.3f,
        ),
        Brush(
            id = "bayangankali", name = "Bayangan Multiply", category = "Desain",
            tip = TipShape.SOFT_ROUND, sizeMax = 300f, spacing = 0.05f, flow = 0.4f, opacityMax = 0.5f,
            pressureSize = 0.2f, pressureOpacity = 0.6f, render = 1, blendMode = 1, defaultSize = 0.4f,
        ),
        Brush(
            id = "kapurganda", name = "Kapur Ganda", category = "Pastel & Arang",
            tip = TipShape.CHALK, grain = Grain.PAPER, grainStrength = 0.6f, sizeMax = 70f, spacing = 0.04f,
            flow = 0.9f, pressureSize = 0.35f, pressureBleed = 0.3f, dualId = "semprotkasar", dualMode = 2, dualSize = 0.6f,
            defaultSize = 0.35f,
        ),
        Brush(
            id = "kuasganda", name = "Kuas Bertekstur Ganda", category = "Tekstur",
            tip = TipShape.SOFT_ROUND, sizeMax = 120f, spacing = 0.05f, flow = 0.8f, pressureSize = 0.5f,
            dualId = "goreskering", dualMode = 0, dualSize = 1f, defaultSize = 0.35f,
        ),
    )

    fun default(id: String): Brush? = builtIn.firstOrNull { it.id == id }
}
