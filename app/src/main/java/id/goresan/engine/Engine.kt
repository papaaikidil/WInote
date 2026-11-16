package id.goresan.engine

import android.app.ActivityManager
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.RectF
import android.os.Handler
import android.os.Looper
import id.goresan.Diag
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.nio.ByteBuffer
import java.nio.FloatBuffer
import java.util.concurrent.Callable
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.Executors
import java.util.concurrent.Future
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt
import android.opengl.GLES30 as G

object ToolKind {
    const val PAINT = 0
    const val SMUDGE = 1
    const val ERASE = 2
}

data class BrushSetting(val brush: Brush, val sizePx: Float, val opacity: Float, val dual: Brush? = null)

data class ToolState(
    val tool: Int,
    val paint: BrushSetting,
    val smudge: BrushSetting,
    val erase: BrushSetting,
    val color: Int,
    val gamma: Float,
    val prediction: Boolean,
    /** Penghalus garis global (Aksi › Tekanan & penghalusan). */
    val smoothing: Smoothing = Smoothing.NONE,
    /** Warna kedua (dinamika warna kuas: getaran/tekanan/kemiringan menuju warna kedua). */
    val color2: Int = color,
) {
    fun current(): BrushSetting = when (tool) {
        ToolKind.SMUDGE -> smudge
        ToolKind.ERASE -> erase
        else -> paint
    }
}

/** Titik mentah dari pena/jari, sudah dalam koordinat kanvas. */
class RawPoint(
    val kind: Int, val x: Float, val y: Float, val p: Float,
    val tilt: Float, val az: Float, val t: Long, val flags: Int,
) {
    companion object {
        const val BEGIN = 0
        const val MOVE = 1
        const val END = 2
        const val CANCEL = 3
        /** Pena ditahan diam di ujung goresan: coba jadikan bentuk rapi (QuickShape). */
        const val SHAPE = 4
        /** Jari kedua ditahan saat bentuk otomatis aktif: jadikan bentuk presisi (lingkaran sempurna, persegi, …). */
        const val CONSTRAIN = 5
        const val ERASER = 1
        const val PRESSURE = 2
        const val TILT = 4
        /** Koordinat masih koordinat layar tampilan 3D: dipetakan ke kanvas lewat UV model di thread GL. */
        const val SCREEN = 8
    }
}

data class LayerInfo(
    val id: Int, val name: String, val visible: Boolean, val opacity: Float, val blend: Int,
    val alphaLock: Boolean, val clip: Boolean, val locked: Boolean, val group: Int = 0,
    val assist: Boolean = false,
    val text: String? = null,
)

data class LayersSnapshot(
    val layers: List<LayerInfo>, val activeId: Int, val canUndo: Boolean, val canRedo: Boolean,
    val limit: Int, val bg: Int, val bgVisible: Boolean,
    val groups: Map<Int, GroupData> = emptyMap(),
)

class Layer(val id: Int, var tex: Tex) {
    var name = "Layer"
    var visible = true
    var opacity = 1f
    var blend = 0
    var alphaLock = false
    var clip = false
    var locked = false
    var group = 0
    var assist = false
    var text: String? = null
    var saveDirty = true
    var thumbDirty = true

    fun props() = LayerProps(name, visible, opacity, blend, alphaLock, clip, locked, group, assist, text)
    fun apply(p: LayerProps) {
        name = p.name; visible = p.visible; opacity = p.opacity; blend = p.blend
        alphaLock = p.alphaLock; clip = p.clip; locked = p.locked; group = p.group; assist = p.assist; text = p.text
    }
    fun info() = LayerInfo(id, name, visible, opacity, blend, alphaLock, clip, locked, group, assist, text)
}

/** Ringkasan karya untuk panel Info kanvas. */
data class CanvasInfo(
    val w: Int, val h: Int, val dpi: Int, val layers: Int, val limit: Int, val groups: Int,
    val created: Long, val timeMs: Long, val strokes: Int, val bytes: Long,
)

class Doc(val id: String, var name: String, val w: Int, val h: Int) {
    val layers = ArrayList<Layer>()
    var active = 0
    var bg = Color.WHITE
    var bgVisible = true
    /** Kerapatan cetak (titik per inci); hanya data, piksel kanvas tidak berubah. */
    var dpi = 300
    /** Tekstur kertas kanvas (Paper.*), kekuatannya, dan skala ubinnya; [paperVer] berubah tiap kertas sendiri diganti. */
    var paper = Paper.NONE
    var paperStrength = 0.55f
    var paperScale = 1f
    var paperVer = 0
    var nextId = 1
    var created = System.currentTimeMillis()
    /** Waktu mengerjakan karya (ms): jeda antar-sentuhan kurang dari semenit dihitung. */
    var timeMs = 0L
    /** Jumlah goresan yang pernah dibuat. */
    var strokes = 0
    /** Profil warna (ColorProfile.SRGB / P3 / CMYK). */
    var profile = ColorProfile.SRGB
    /** Model 3D yang dilukis (kanvas = teksturnya); null = karya 2D biasa. */
    var mesh: Mesh? = null
    /** Karya Face Paint: kanvas = tekstur wajah (UV wajah kanonik). */
    var face = false
    /** 0 = biasa, 1 = Animation Assist, 2 = Page Assist (halaman). */
    var assist = 0
    var anim = AnimSettings()
    /** Tahan frame (jumlah frame tambahan) per kunci frame (id layer atau -id grup). */
    val holds = HashMap<Int, Int>()
    /** Grup layer (urutan tidak penting; letak grup mengikuti letak anggotanya). */
    val groups = LinkedHashMap<Int, GroupData>()
    fun find(id: Int) = layers.firstOrNull { it.id == id }
    fun activeLayer() = find(active)
    fun indexOf(id: Int) = layers.indexOfFirst { it.id == id }
    fun groupVisible(g: Int) = g == 0 || groups[g]?.visible != false
    /** Layer benar-benar tampil: dirinya dan grupnya sama-sama terlihat. */
    fun shown(l: Layer) = l.visible && groupVisible(l.group)
}

/**
 * Mesin kanvas. Semua fungsi (kecuali yang diberi tanda) berjalan di thread OpenGL.
 * Perintah dari antarmuka masuk lewat [tasks], titik pena lewat [input].
 */
/** Berkas kertas sendiri di folder karya (abu-abu n×n). */
const val PAPER_FILE = "kertas.r8"

class Engine(private val ctx: Context, private val listener: Listener) {

    interface Listener {
        /** Status perekaman time-lapse karya yang dibuka. */
        fun onTimeLapse(on: Boolean) {}
        /** Profil warna karya yang dibuka. */
        fun onProfile(profile: Int) {}
        /** Model 3D karya ([tris] segitiga, 0 = karya 2D) dan apakah tampilan 3D aktif. */
        fun onModel(tris: Int, view3D: Boolean) {}
        /** Karya Face Paint. */
        fun onFace(face: Boolean) {}
        /** Mode Pena penyesuaian: aktif atau tidak, jumlah langkah urungkan/ulangi sapuan. */
        fun onPenMode(on: Boolean, undo: Int, redo: Int) {}
        /** Status Animation/Page Assist. */
        fun onAnim(assist: Int, s: AnimSettings, holds: Map<Int, Int>, playing: Boolean) {}
        fun onDocOpened(id: String, name: String, w: Int, h: Int)
        fun onDocFailed(msg: String)
        fun onLayers(s: LayersSnapshot)
        fun onThumb(id: Int, bmp: Bitmap)
        fun onContentChanged()
        fun onPicked(color: Int, sx: Float, sy: Float)
        fun onTransformBounds(r: RectF)
        fun onMessage(msg: String)
        fun onBrushPreview(id: String, bmp: Bitmap)
        fun onStrokeDone(color: Int)
        fun onReloading()
        /** Pilihan (Selection) berubah: [active] ada pilihan, [saved] jumlah pilihan tersimpan. */
        fun onSelectionChanged(active: Boolean, saved: Int) {}
        /** DPI dokumen (saat dibuka atau diubah). */
        fun onDocDpi(dpi: Int) {}
        /** Panduan menggambar dokumen (saat dibuka). */
        fun onGuide(on: Boolean, g: DrawingGuide) {}
        /** Tekstur kertas kanvas (saat dibuka atau diubah). */
        fun onPaper(type: Int, strength: Float, scale: Float) {}
        /** Urungkan ([redo] false) atau ulangi selesai; [label] = nama langkah, null = tidak ada langkah. */
        fun onHistory(redo: Boolean, label: String?) {}
    }

    private val main = Handler(Looper.getMainLooper())
    private fun post(r: () -> Unit) { main.post { r() } }

    val tasks = ConcurrentLinkedQueue<(Engine) -> Unit>()
    val input = ConcurrentLinkedQueue<RawPoint>()
    @Volatile var view: FloatArray = floatArrayOf(1f, 0f, 0f, 0f, 1f, 0f, 0f, 0f, 1f)
    @Volatile var hover: FloatArray? = null
    @Volatile var tool: ToolState? = null
    @Volatile var xfRequest: XfFrame? = null
    @Volatile var adjRequest: FloatArray? = null
    /** Tabel 256×1 RGBA untuk Curves / Gradient Map; diunggah bersama adjRequest berikutnya. */
    @Volatile var adjLut: ByteArray? = null
    /** Titik pusat penyesuaian (Perspective Blur, Chromatic Aberration) dalam piksel kanvas; digambar di layar. */
    @Volatile var marker: FloatArray? = null
    /** Liquify: titik sentuh [x, y, tekanan, fase 0/1/2, waktu ms]. */
    val liqInput = ConcurrentLinkedQueue<FloatArray>()
    /** Sapuan mode Pena penyesuaian: [x, y kanvas, tekanan, fase 0 mulai/1 gerak/2 selesai/3 batal, hapus 0/1]. */
    val penInput = ConcurrentLinkedQueue<FloatArray>()
    /** Liquify: [mode, ukuran px, tekanan 0..1, distorsi 0..1]. */
    @Volatile var liqParams = floatArrayOf(0f, 150f, 0.5f, 0.5f)
    /** Posisi sumber Clone (piksel kanvas), untuk antarmuka. Null bila Clone tidak aktif. */
    @Volatile var cloneMarker: FloatArray? = null
    /** Panduan menggambar (Drawing Guide) dokumen; diubah langsung dari antarmuka, ikut disimpan di data karya. */
    @Volatile var guide = DrawingGuide()
    @Volatile var guideOn = false
    /** Layar Edit Panduan Menggambar terbuka: titik pegangan ikut digambar. [guideSel] = titik hilang terpilih. */
    @Volatile var guideEditing = false
    @Volatile var guideSel = -1
    @Volatile var glReady = false
    /** Diisi oleh view: minta satu frame baru. */
    var requestRender: () -> Unit = {}

    private val density = ctx.resources.displayMetrics.density
    private var screenW = 1
    private var screenH = 1

    // Program shader
    private lateinit var pBg: Program
    private lateinit var pComposite: Program
    private lateinit var pPreview: Program
    private lateinit var pDab: Program
    private lateinit var pSmudge: Program
    private lateinit var pWet: Program
    private lateinit var pWetSpread: Program
    private lateinit var pWetWater: Program
    private lateinit var pWetIn: Program
    private lateinit var pWetInW: Program
    private lateinit var pBlur: Program
    private lateinit var pTransform: Program
    private lateinit var pScreen: Program
    private lateinit var pColor: Program
    private var r3d: Renderer3D? = null
    /** Tampilan 3D aktif (karya bermodel). Diubah lewat [setView3D]. */
    @Volatile var view3D = false
        private set
    /** Kamera tampilan 3D; boleh diganti dari thread antarmuka (lalu requestRender). */
    @Volatile var cam3D = Cam3D()
        set(v) { field = v; camAt = android.os.SystemClock.uptimeMillis() }
    @Volatile private var camAt = 0L
    /** LUT 3D proof CMYK (dibuat saat karya CMYK dibuka). */
    private var proofTex = 0
    /** Permukaan layar memakai ruang warna Display P3 (diatur CanvasView saat permukaan dibuat). */
    @Volatile var surfaceP3 = false
    private lateinit var pRing: Program
    private lateinit var pLine: Program
    private lateinit var pMesh: Program
    private lateinit var pDot: Program
    private lateinit var pSelOp: Program
    private lateinit var pPaper: Program
    // Tekstur kertas (RGBA: bayangan + tinggi permukaan) dan noise kuas cat air.
    private var paperTex = 0
    private var paperTexN = 1
    private var paperTexKey = ""
    private var paperPending = ""
    private var noiseTex = 0
    private val paperCache = HashMap<Int, ByteArray>()
    // Teks yang sedang diedit: gambarnya ditaruh di strokeBuf (tak ada goresan selama mengedit teks) dan ditumpuk
    // di atas layer aktif (teks baru) atau menggantikan layer teks yang sedang diedit.
    @Volatile var textOn = false
    private var textFor = -1
    private var textAbove = -1
    private val textRect = IRect()
    private var textDummy: Layer? = null
    /** Bingkai teks (4 sudut kanvas) dan jalur bebas yang sedang digambar, untuk tampilan di layar. */
    @Volatile var textBox: FloatArray? = null
    @Volatile var textPathDraft: FloatArray? = null
    // Goresan cat air yang sedang berjalan.
    private var strokeWet = false
    private var wetSeedX = 0f
    private var wetSeedY = 0f
    private var wetPad = 0
    private var meshVao = 0
    private var meshVbo = 0
    private var meshIbo = 0
    private val meshN = 40
    private val meshFloats: FloatBuffer = Buffers.direct((40 + 1) * (40 + 1) * 4 * 4).asFloatBuffer()
    /** Program filter per jenis penyesuaian, dikompilasi saat pertama dipakai. */
    private val filters = HashMap<Int, Program>()
    /** GPU perangkat lunak (emulator): jumlah sampel blur dibatasi supaya tidak macet. */
    private var softGpu = false
    private lateinit var pLiquify: Program
    private var lutTex = 0
    private val filterP = FloatArray(12)
    private var vao = 0
    private var dabVao = 0
    private var dabVbo = 0
    private var lineVao = 0
    private var lineVbo = 0
    private val maxBatch = 1024
    /** Angka per titik sudut cap: posisi (2), uv (2), warna (4), bleed (1). */
    private val vtxN = 9
    private val dabFloats: FloatBuffer = Buffers.direct(maxBatch * 6 * vtxN * 4).asFloatBuffer()
    private val lineFloats: FloatBuffer = Buffers.direct(1024 * 4).asFloatBuffer()
    // Garis panduan menggambar: segi empat tipis per garis (6 titik), dihitung ulang hanya bila panduan berubah.
    private val guideBatch = 2048
    private val guideFloats: FloatBuffer = Buffers.direct(guideBatch * 12 * 4).asFloatBuffer()
    private var guideVao = 0
    private var guideVbo = 0
    private var guideKey: Any? = null
    private var guideSegs = FloatArray(0)
    private var guideMajor = FloatArray(0)
    private var guideScreen = FloatArray(0)
    // Bantuan menggambar per goresan: arah garis yang boleh dipakai, arah terpilih, titik yang ditahan sebelum
    // arahnya jelas, dan salinan simetri.
    private var assistDirs: FloatArray? = null
    private var assistDecided = false
    private var assistDx = 0f
    private var assistDy = 0f
    private var assistSx = 0f
    private var assistSy = 0f
    private var assistThresh = 10f
    private val assistBuf = ArrayList<RawPoint>()
    private var strokeSym: FloatArray? = null
    private var symCx = 0f
    private var symCy = 0f
    private val tips = HashMap<TipShape, Int>()
    private val grains = HashMap<Grain, Int>()
    /** Tekstur ujung/kertas dari kuas impor, menurut nama berkasnya. */
    private val customTex = HashMap<String, Int>()

    // Dokumen
    var doc: Doc? = null
        private set
    private var W = 0
    private var H = 0
    private var comp: Tex? = null
    private var scratch: Tex? = null
    private var strokeBuf: Tex? = null
    // Kuas stempel: satu goresan = satu cap yang membentang dari titik awal ke titik akhir.
    private var stampOn = false
    private var stampPending = false
    private var stampX0 = 0f
    private var stampY0 = 0f
    private var stampX1 = 0f
    private var stampY1 = 0f
    private val stampBox = IRect()
    private val stampDabs = DabList()
    private val tipExtents = HashMap<String, Pair<Float, Float>>()
    private var predBuf: Tex? = null
    private var preview: Tex? = null
    private var backup: Tex? = null
    private var maskTex: Tex? = null
    private var smT: Tex? = null
    private var smCarry: Tex? = null
    private var prevTex: Tex? = null
    private var prevTex2: Tex? = null
    private var mipDirty = true
    var layerLimit = 0
        private set
    private var undoBudget = 256L shl 20

    private val undoStack = ArrayDeque<Edit>()
    private val redoStack = ArrayDeque<Edit>()
    private val packer = Executors.newSingleThreadExecutor { r -> Thread(r, "goresan-undo") }
    private val worker = Executors.newSingleThreadExecutor { r -> Thread(r, "goresan-isi") }

    // Goresan
    private val proc = StrokeProcessor()
    private val previewProc = StrokeProcessor()
    private val dabs = DabList()
    private val predDabs = DabList()
    private var strokeActive = false
    private var strokeTool = ToolKind.PAINT
    private var strokeSet: BrushSetting? = null
    private var strokeLayer: Layer? = null
    private var strokeColor = 0
    private var predOn = false
    private var predAhead = true
    private var smudgeFirst = true
    /** Goresan yang langsung mengubah layer (baur, atau cat basah Wet Mix), bukan lewat strokeBuf. */
    private var strokeDirect = false
    private var strokeWetMix = false
    /** Jarak tempuh goresan cat basah (untuk muatan cat yang menipis). */
    private var wetDist = 0f
    private var wetLastX = Float.NaN
    private var wetLastY = 0f
    private val wetRnd = java.util.Random()
    /** Tepi basah/terbakar membaca sekitar titik: wilayah yang dihitung ulang diperlebar sebesar ini. */
    private var rendPad = 0
    // ---- kuas ganda ----
    private val proc2 = StrokeProcessor()
    private val dabs2 = DabList()
    private val predDabs2 = DabList()
    private var dualBuf: Tex? = null
    private var dualPred: Tex? = null
    private var strokeDual: Brush? = null
    private val dualBox = IRect()
    private val dualPredBox = IRect()
    // ---- cat air hidup ----
    // Sesi basah: selama cat masih basah, layer = dasar (sebelum sesi) + cat basah yang terus merembes. Goresan cat air
    // berikutnya (kuas & opasitas sama, layer sama) ikut masuk ke cat basah dan bercampur. Selesai saat semua kering,
    // atau langsung dikeringkan bila ada perubahan lain (urungkan, alat/layer lain, mode lain).
    private var wetBase: Tex? = null
    private val wetF = arrayOfNulls<Tex>(2)
    private val wetW = arrayOfNulls<Tex>(2)
    private var wetFs: Tex? = null
    private var wetWs: Tex? = null
    private var wetC = 0
    private var wetOn = false
    private var wetLayerId = -1
    private var wetSet: BrushSetting? = null
    private class WetPatch(val r: IRect, var until: Long)
    private val wetPatches = ArrayList<WetPatch>()
    private var wetCur: WetPatch? = null
    private var wetLast = 0L
    /** Waktu simulasi (ms): bingkai yang lambat memperlambat simulasi, bukan memotongnya, jadi rembesan selalu tuntas. */
    private var wetSim = 0L
    /** Waktu nyata goresan terakhir masuk ke cat basah (batas aman di perangkat yang lambat). */
    private var wetLastReal = 0L
    private var wetEvapAcc = 0f
    private var wetStepN = 0
    private var wetPushing = false
    /** Goresan cat air hidup yang sedang digambar (isi strokeBuf dimasukkan sedikit demi sedikit ke cat basah). */
    private var strokeLive = false
    private val liveBox = IRect()
    private val strokeBox = IRect()
    private val predBox = IRect()
    private val dirty = IRect()
    private val tmpRect = IRect()
    /** Tekstur pengganti layer aktif saat pratinjau (goresan, isi, penyesuaian, transformasi). */
    private var substitute: Tex? = null
    private var substituteFor = -1

    // Bentuk otomatis (QuickShape)
    private var rawX = FloatArray(1024)
    private var rawY = FloatArray(1024)
    private var rawP = FloatArray(1024)
    private var rawTilt = FloatArray(1024)
    private var rawN = 0
    private var rawAz = 0f
    private var strokeFlags = 0
    private var strokeGamma = 1f
    private var shapeBase: QuickShape.Shape? = null
    private var shape: QuickShape.Shape? = null
    private var shapePenX = 0f
    private var shapePenY = 0f
    private var shapeCurX = 0f
    private var shapeCurY = 0f
    private var shapeMoved = false
    private var shapePending = false
    private var shapeConstrain = false
    /** Bentuk otomatis sedang aktif (pena masih ditahan); dibaca kanvas untuk jari kedua. */
    @Volatile var shapeActive = false
        private set
    private var shapeP = 1f
    private var shapeTilt = 0f
    private val shapeBox = IRect()
    private val freeBox = IRect()
    private var freeBuf: java.nio.ByteBuffer? = null
    private val shapeProc = StrokeProcessor()
    private val shapeDabs = DabList()

    // Mode khusus
    private var fillOn = false
    private var fillLayer: Layer? = null
    private var fillSx = 0
    private var fillSy = 0
    private var fillThr = 0.4f
    private var fillRefAll = false
    private var fillColor = Color.BLACK
    private var fillSrc: ByteArray? = null
    private val fillBox = IRect()
    @Volatile private var fillGen = 0
    private var fillRetried = false

    private var adjOn = false
    private var adjKind = 0
    private var adjLayer: Layer? = null

    // Blur bertingkat: piramida ½, ¼, … ukuran kanvas (dibuat saat perlu, dilepas setelah penyesuaian selesai).
    private val pyr = arrayOfNulls<Tex>(12)
    private var pyrTmp: Tex? = null
    private lateinit var pUp: Program
    private lateinit var pPen: Program

    // Mode Pena penyesuaian: efek hanya di bagian yang disapukan. adjFull = hasil penuh penyesuaian,
    // penMask = bagian yang sudah disapukan (R8), penStroke = sapuan yang sedang berjalan (R8).
    private var penOn = false
    private var adjFull: Tex? = null
    private var penMask: Tex? = null
    private var penStroke: Tex? = null
    private var penDown = false
    private var penErase = false
    private var penSize = 40f
    private var penOp = 1f
    private var penX = 0f
    private var penY = 0f
    private var penP = 1f
    private var penResid = 0f
    private val penBox = IRect()
    private var penDabBuf = FloatArray(384)
    private var penDabN = 0
    private class PenStep(val r: IRect, val data: ByteArray)
    private val penUndo = ArrayDeque<PenStep>()
    private val penRedo = ArrayDeque<PenStep>()

    // Liquify: medan pergeseran pada kisi kasar (tiap liqStep piksel), disimpan [dx, dy] per titik kisi.
    private var liqStep = 4
    private var liqGW = 0
    private var liqGH = 0
    private var liqD: FloatArray? = null
    private var liqTmp = FloatArray(0)
    private var liqTexId = 0
    private var liqUp: java.nio.FloatBuffer? = null
    private var liqAmt = 1f
    private var liqDown = false
    private var liqX = 0f
    private var liqY = 0f
    private var liqT = 0L
    private var liqDirX = 1f
    private var liqDirY = 0f
    private var liqSeed = 0

    // Clone: goresan kuas menyalin isi layer dari titik sumber (offset tetap sejak goresan pertama).
    private var cloneOn = false
    private var cloneSrcX = 0f
    private var cloneSrcY = 0f
    private var cloneDx = 0f
    private var cloneDy = 0f
    private var cloneFixed = false

    private var xfOn = false
    /** Layer-layer yang sedang ditransformasi bersama (satu layer, beberapa layer terpilih, atau satu grup). */
    private var xfLayers: List<Layer> = emptyList()
    private val xfIds = HashSet<Int>()
    private val xfInv = floatArrayOf(1f, 0f, 0f, 0f, 1f, 0f, 0f, 0f, 1f)
    private val xfOrig = IRect()
    private val xfPrev = IRect()
    private var xfMatrix: FloatArray? = null
    /** Keadaan terakhir dari antarmuka (untuk kotak, pegangan, kisi Warp, dan garis tempel di layar). */
    private var xfDraw: XfFrame? = null
    /** Warp aktif: layer dirender lewat jaring ke pratinjau (hanya untuk satu layer). */
    private var xfWarp = false
    private var xfInterp = 1

    // Pilihan (Selection). Masker di CPU dimiliki thread bantu (worker); salinannya diunggah ke tekstur R8.
    private var selTex: Tex? = null
    private var selOn = false
    private val selBox = IRect()
    private var selMask: ByteArray? = null
    private var selBase: ByteArray? = null
    private val selSaved = ArrayList<ByteArray>()
    private var selBefore: ByteArray? = null
    private var selAutoSrc: ByteArray? = null
    private var selAutoX = 0
    private var selAutoY = 0
    @Volatile private var selAutoGen = 0
    /** Garis pilihan yang sedang dibuat (titik kanvas berpasangan) dan apakah sudah tertutup; digambar di layar. */
    @Volatile var selDraft: FloatArray? = null
    @Volatile var selDraftClosed = false
    // Transformasi isi pilihan: isi terpilih "diangkat" (xfFloat), sisanya (xfRest) tetap di tempat.
    private var xfSel = false
    private var xfSelId = -1
    private var xfFloat: Tex? = null
    private var xfRest: Tex? = null

    val modal: Boolean get() = fillOn || adjOn || xfOn

    // ================= siklus hidup GL =================

    fun onSurfaceCreated() {
        initGl()
        glReady = true
        val d = doc
        if (d != null) {
            // Konteks GL hilang (aplikasi lama di latar belakang): muat ulang dari simpanan terakhir.
            Diag.log("GL: konteks lama hilang, memuat ulang karya")
            post { listener.onReloading() }
            val id = d.id
            doc = null
            comp = null; scratch = null; strokeBuf = null; predBuf = null; preview = null; dualBuf = null; dualPred = null
            backup = null; maskTex = null; smT = null; smCarry = null; prevTex = null; prevTex2 = null
            wetForget()
            undoStack.clear(); redoStack.clear()
            strokeActive = false; substitute = null
            fillOn = false; adjOn = false; xfOn = false
            liqD = null; liqTexId = 0; cloneOn = false; cloneMarker = null
            pyr.fill(null); pyrTmp = null; adjFull = null; penMask = null; penStroke = null; penOn = false
            selTex = null; selOn = false; xfFloat = null; xfRest = null; xfSel = false
            // Jangan memuat di sini: thread antarmuka sedang menunggu frame pertama selesai.
            // Tampilkan frame kosong dulu, baru muat ulang di frame berikutnya.
            main.postDelayed({
                tasks.add { it.openDoc(id) }
                requestRender()
            }, 60)
        }
    }

    fun onSurfaceChanged(w: Int, h: Int) {
        screenW = max(1, w)
        screenH = max(1, h)
    }

    private fun initGl() {
        pBg = Program(Shaders.RECT_VS, Shaders.BG_FS)
        pComposite = Program(Shaders.RECT_VS, Shaders.COMPOSITE_FS)
        pPreview = Program(Shaders.RECT_VS, Shaders.PREVIEW_FS)
        pDab = Program(Shaders.DAB_VS, Shaders.DAB_FS)
        pSmudge = Program(Shaders.DAB_VS, Shaders.SMUDGE_FS)
        pWet = Program(Shaders.DAB_VS, Shaders.WETMIX_FS)
        pWetSpread = Program(Shaders.RECT_VS, Shaders.WET_SPREAD_FS)
        pWetWater = Program(Shaders.RECT_VS, Shaders.WET_WATER_FS)
        pWetIn = Program(Shaders.RECT_VS, Shaders.WET_IN_FS)
        pWetInW = Program(Shaders.RECT_VS, Shaders.WET_IN_W_FS)
        pBlur = Program(Shaders.RECT_VS, Shaders.BLUR_FS)
        pUp = Program(Shaders.RECT_VS, Shaders.UPSAMPLE_FS)
        pPen = Program(Shaders.RECT_VS, Shaders.PEN_FS)
        pTransform = Program(Shaders.RECT_VS, Shaders.TRANSFORM_FS)
        pScreen = Program(Shaders.SCREEN_VS, Shaders.SCREEN_FS)
        pColor = Program(Shaders.RECT_VS, Shaders.COLOR_FS)
        proofTex = 0
        r3d = null
        pRing = Program(Shaders.SPRITE_VS, Shaders.RING_FS)
        pLine = Program(Shaders.LINE_VS, Shaders.LINE_FS)
        filters.clear()
        val renderer = G.glGetString(G.GL_RENDERER) ?: ""
        softGpu = renderer.contains("SwiftShader", true) || renderer.contains("llvmpipe", true)
        Diag.log("GL: $renderer")
        // MSAA tampilan 3D dimatikan di GPU perangkat lunak (emulator) supaya tidak terlalu lambat.
        r3d = Renderer3D(allowMsaa = !softGpu)
        pLiquify = Program(Shaders.RECT_VS, Shaders.LIQUIFY_FS)

        val a = IntArray(1)
        G.glGenVertexArrays(1, a, 0); vao = a[0]
        G.glGenVertexArrays(1, a, 0); dabVao = a[0]
        G.glGenBuffers(1, a, 0); dabVbo = a[0]
        G.glBindVertexArray(dabVao)
        G.glBindBuffer(G.GL_ARRAY_BUFFER, dabVbo)
        G.glBufferData(G.GL_ARRAY_BUFFER, maxBatch * 6 * vtxN * 4, null, G.GL_STREAM_DRAW)
        G.glEnableVertexAttribArray(0)
        G.glVertexAttribPointer(0, 2, G.GL_FLOAT, false, vtxN * 4, 0)
        G.glEnableVertexAttribArray(1)
        G.glVertexAttribPointer(1, 2, G.GL_FLOAT, false, vtxN * 4, 8)
        G.glEnableVertexAttribArray(2)
        G.glVertexAttribPointer(2, 4, G.GL_FLOAT, false, vtxN * 4, 16)
        G.glEnableVertexAttribArray(3)
        G.glVertexAttribPointer(3, 1, G.GL_FLOAT, false, vtxN * 4, 32)
        G.glGenVertexArrays(1, a, 0); lineVao = a[0]
        G.glGenBuffers(1, a, 0); lineVbo = a[0]
        G.glBindVertexArray(lineVao)
        G.glBindBuffer(G.GL_ARRAY_BUFFER, lineVbo)
        G.glBufferData(G.GL_ARRAY_BUFFER, 1024 * 4, null, G.GL_STREAM_DRAW)
        G.glEnableVertexAttribArray(0)
        G.glVertexAttribPointer(0, 2, G.GL_FLOAT, false, 8, 0)
        G.glGenVertexArrays(1, a, 0); guideVao = a[0]
        G.glGenBuffers(1, a, 0); guideVbo = a[0]
        G.glBindVertexArray(guideVao)
        G.glBindBuffer(G.GL_ARRAY_BUFFER, guideVbo)
        G.glBufferData(G.GL_ARRAY_BUFFER, guideBatch * 12 * 4, null, G.GL_STREAM_DRAW)
        G.glEnableVertexAttribArray(0)
        G.glVertexAttribPointer(0, 2, G.GL_FLOAT, false, 8, 0)
        // Jaring Warp: titik (posisi tujuan, posisi asal) + indeks segitiga tetap.
        pMesh = Program(Shaders.MESH_VS, Shaders.MESH_FS)
        pDot = Program(Shaders.SPRITE_VS, Shaders.DOT_FS)
        pSelOp = Program(Shaders.RECT_VS, Shaders.SELOP_FS)
        pPaper = Program(Shaders.RECT_VS, Shaders.PAPER_FS)
        noiseTex = uploadR8(Paper.noiseTile(), 256, true)
        paperTex = newRepeatTex()
        G.glBindTexture(G.GL_TEXTURE_2D, paperTex)
        val one = Buffers.direct(4).apply { put(0, (-1).toByte()); put(1, (-1).toByte()); put(2, (-1).toByte()); put(3, 0x80.toByte()) }
        G.glPixelStorei(G.GL_UNPACK_ALIGNMENT, 1)
        G.glTexImage2D(G.GL_TEXTURE_2D, 0, G.GL_RGBA8, 1, 1, 0, G.GL_RGBA, G.GL_UNSIGNED_BYTE, one)
        paperTexN = 1
        paperTexKey = ""
        paperPending = ""
        G.glGenVertexArrays(1, a, 0); meshVao = a[0]
        G.glGenBuffers(1, a, 0); meshVbo = a[0]
        G.glGenBuffers(1, a, 0); meshIbo = a[0]
        G.glBindVertexArray(meshVao)
        G.glBindBuffer(G.GL_ARRAY_BUFFER, meshVbo)
        G.glBufferData(G.GL_ARRAY_BUFFER, (meshN + 1) * (meshN + 1) * 4 * 4, null, G.GL_STREAM_DRAW)
        G.glEnableVertexAttribArray(0)
        G.glVertexAttribPointer(0, 2, G.GL_FLOAT, false, 16, 0)
        G.glEnableVertexAttribArray(1)
        G.glVertexAttribPointer(1, 2, G.GL_FLOAT, false, 16, 8)
        val idx = java.nio.ShortBuffer.allocate(meshN * meshN * 6)
        for (j in 0 until meshN) for (i in 0 until meshN) {
            val p0 = (j * (meshN + 1) + i).toShort()
            val p1 = (j * (meshN + 1) + i + 1).toShort()
            val p2 = ((j + 1) * (meshN + 1) + i).toShort()
            val p3 = ((j + 1) * (meshN + 1) + i + 1).toShort()
            idx.put(p0); idx.put(p1); idx.put(p2); idx.put(p1); idx.put(p3); idx.put(p2)
        }
        val ib = Buffers.direct(meshN * meshN * 6 * 2).asShortBuffer()
        idx.flip(); ib.put(idx); ib.position(0)
        G.glBindBuffer(G.GL_ELEMENT_ARRAY_BUFFER, meshIbo)
        G.glBufferData(G.GL_ELEMENT_ARRAY_BUFFER, meshN * meshN * 6 * 2, ib, G.GL_STATIC_DRAW)
        G.glBindVertexArray(0)
        G.glBindBuffer(G.GL_ARRAY_BUFFER, 0)

        G.glGenTextures(1, a, 0); lutTex = a[0]
        G.glBindTexture(G.GL_TEXTURE_2D, lutTex)
        G.glPixelStorei(G.GL_UNPACK_ALIGNMENT, 4)
        G.glTexImage2D(G.GL_TEXTURE_2D, 0, G.GL_RGBA8, 256, 1, 0, G.GL_RGBA, G.GL_UNSIGNED_BYTE, null)
        G.glTexParameteri(G.GL_TEXTURE_2D, G.GL_TEXTURE_MIN_FILTER, G.GL_LINEAR)
        G.glTexParameteri(G.GL_TEXTURE_2D, G.GL_TEXTURE_MAG_FILTER, G.GL_LINEAR)
        G.glTexParameteri(G.GL_TEXTURE_2D, G.GL_TEXTURE_WRAP_S, G.GL_CLAMP_TO_EDGE)
        G.glTexParameteri(G.GL_TEXTURE_2D, G.GL_TEXTURE_WRAP_T, G.GL_CLAMP_TO_EDGE)

        tips.clear()
        grains.clear()
        customTex.clear()
        G.glDisable(G.GL_DEPTH_TEST)
        G.glDisable(G.GL_DITHER)
    }

    private fun tipTex(t: TipShape): Int = tips.getOrPut(t) { uploadR8(Procedural.tip(t), 256, false) }

    /** Ujung kuas: gambar kuas impor bila ada, selain itu bentuk bawaan. */
    private fun tipTexFor(b: Brush): Int = customR8(b.tipFile, false) ?: tipTex(b.tip)

    /** Tekstur kuas impor (data R8 persegi di folder kuas, sisi dari ukuran berkas), diunggah sekali. Null bila tidak ada/rusak. */
    private fun customR8(name: String, repeat: Boolean): Int? {
        if (name.isEmpty()) return null
        customTex[name]?.let { return it }
        val (data, n) = BrushImport.readSquare(name) ?: return null
        val id = uploadR8(data, n, repeat)
        customTex[name] = id
        return id
    }
    private fun grainTex(g: Grain): Int = grains.getOrPut(g) { uploadR8(Procedural.grain(g), 512, true) }

    private fun uploadR8(data: ByteArray, n: Int, repeat: Boolean): Int {
        val a = IntArray(1)
        G.glGenTextures(1, a, 0)
        G.glBindTexture(G.GL_TEXTURE_2D, a[0])
        G.glPixelStorei(G.GL_UNPACK_ALIGNMENT, 1)
        val bb = Buffers.direct(data.size)
        bb.put(data)
        bb.position(0)
        G.glTexImage2D(G.GL_TEXTURE_2D, 0, G.GL_R8, n, n, 0, G.GL_RED, G.GL_UNSIGNED_BYTE, bb)
        G.glGenerateMipmap(G.GL_TEXTURE_2D)
        G.glTexParameteri(G.GL_TEXTURE_2D, G.GL_TEXTURE_MIN_FILTER, G.GL_LINEAR_MIPMAP_LINEAR)
        G.glTexParameteri(G.GL_TEXTURE_2D, G.GL_TEXTURE_MAG_FILTER, G.GL_LINEAR)
        val wrap = if (repeat) G.GL_REPEAT else G.GL_CLAMP_TO_EDGE
        G.glTexParameteri(G.GL_TEXTURE_2D, G.GL_TEXTURE_WRAP_S, wrap)
        G.glTexParameteri(G.GL_TEXTURE_2D, G.GL_TEXTURE_WRAP_T, wrap)
        return a[0]
    }

    fun onDrawFrame() {
        while (true) {
            val t = tasks.poll() ?: break
            try {
                t(this)
            } catch (e: OutOfMemoryError) {
                Diag.log("Memori tidak cukup: ${e.message}")
                post { listener.onMessage("Memori tidak cukup: ${e.message ?: ""}") }
            } catch (e: Throwable) {
                Diag.log("Galat perintah: ${e.javaClass.simpleName}: ${e.message}")
                e.stackTrace.take(8).forEach { Diag.log("    $it") }
                post { listener.onMessage("Terjadi kesalahan: ${e.message ?: e.javaClass.simpleName}") }
            }
        }
        val d = doc
        if (d == null) {
            G.glBindFramebuffer(G.GL_FRAMEBUFFER, 0)
            G.glViewport(0, 0, screenW, screenH)
            G.glClearColor(0.11f, 0.11f, 0.12f, 1f)
            G.glClear(G.GL_COLOR_BUFFER_BIT)
            return
        }
        try {
            xfRequest?.let { xfRequest = null; applyTransform(it) }
            adjRequest?.let {
                adjRequest = null
                adjLut?.let { t -> adjLut = null; uploadLut(t) }
                applyAdjust(it)
            }
            if (adjOn && adjKind == Adj.LIQUIFY) liqDrain()
            if (adjOn && penOn) penDrain()
            drainInput()
            // Pena diam tetapi masih menempel: goresan yang dihaluskan terus menyusul ke ujung pena.
            if (strokeActive && !stampOn && shapeBase == null && assistBuf.isEmpty()) {
                val now = android.os.SystemClock.uptimeMillis()
                if (proc.idle(now, dabs)) requestRender()
                if (strokeDual != null) proc2.idle(now, dabs2)
                flushDabs()
            }
            while (!strokeActive && pendingHistory.isNotEmpty()) if (pendingHistory.removeFirst()) redo() else undo()
            animTick()
            wetUpdate()
            if (strokeActive && predOn) updatePrediction()
            if (!dirty.isEmpty) {
                // Cat air: tepi gelap bergantung pada sekitar titik, jadi wilayah yang dihitung ulang diperlebar.
                if (strokeActive && strokeWet) dirty.inset(wetPad).clampTo(W, H)
                if (strokeActive && rendPad > 0) dirty.inset(rendPad).clampTo(W, H)
                if (strokeActive && !strokeDirect) updateStrokePreview(dirty, predOn && (!predBox.isEmpty || !dualPredBox.isEmpty))
                recomposite(dirty)
                dirty.clear()
            }
            tlMaybeCapture()
        } catch (e: Throwable) {
            Diag.log("Galat frame: ${e.javaClass.simpleName}: ${e.message}")
            e.stackTrace.take(8).forEach { Diag.log("    $it") }
            post { listener.onMessage("Terjadi kesalahan: ${e.message ?: e.javaClass.simpleName}") }
        }
        drawScreen(d)
    }

    // ================= dokumen =================

    fun openDoc(id: String) {
        val t0 = android.os.SystemClock.uptimeMillis()
        val dir = Storage.dir(ctx, id)
        val meta = Storage.readMeta(dir)
        if (meta == null) {
            post { listener.onDocFailed("Karya tidak ditemukan") }
            return
        }
        closeDoc()
        val w = meta.optInt("w")
        val h = meta.optInt("h")
        val maxTex = IntArray(1)
        G.glGetIntegerv(G.GL_MAX_TEXTURE_SIZE, maxTex, 0)
        if (w <= 0 || h <= 0 || w > maxTex[0] || h > maxTex[0]) {
            post { listener.onDocFailed("Ukuran kanvas tidak didukung") }
            return
        }
        val d = Doc(id, meta.optString("name", "Tanpa judul"), w, h)
        d.bg = meta.optInt("bg", Color.WHITE)
        d.bgVisible = meta.optBoolean("bgVisible", true)
        d.dpi = meta.optInt("dpi", 300).coerceIn(1, 9600)
        d.paper = meta.optInt("paper", Paper.NONE).coerceIn(0, Paper.CUSTOM)
        d.paperStrength = meta.optDouble("paperStrength", 0.55).toFloat().coerceIn(0f, 1f)
        d.paperScale = meta.optDouble("paperScale", 1.0).toFloat().coerceIn(0.25f, 4f)
        d.nextId = meta.optInt("nextId", 2)
        d.created = meta.optLong("created", System.currentTimeMillis())
        d.timeMs = meta.optLong("timeMs", 0L)
        d.strokes = meta.optInt("strokes", 0)
        d.profile = meta.optInt("profile", 0).coerceIn(0, 2)
        if (meta.optBoolean("model3d", false)) {
            d.mesh = Mesh.read(File(dir, "model.bin"))
            if (d.mesh == null) Diag.log("Model 3D gagal dibaca")
        }
        runCatching { r3d?.setMesh(d.mesh) }.onFailure { Diag.log("Model 3D gagal dimuat ke GPU: ${it.message}"); d.mesh = null }
        view3D = d.mesh != null
        cam3D = Cam3D()
        d.face = meta.optBoolean("face", false)
        val fc = d.face
        post { listener.onFace(fc) }
        val tris = d.mesh?.triangles ?: 0
        val v3 = view3D
        post { listener.onModel(tris, v3) }
        val prof = d.profile
        post { listener.onProfile(prof) }
        d.assist = meta.optInt("assist", 0).coerceIn(0, 2)
        d.anim = AnimSettings.fromJson(meta.optJSONObject("anim"))
        meta.optJSONObject("holds")?.let { ho -> ho.keys().forEach { k -> k.toIntOrNull()?.let { d.holds[it] = ho.optInt(k) } } }
        animPlaying = false
        animShow = -1
        animLastCur = -1
        actLast = 0L
        tlOn = meta.optBoolean("tlOn", true)
        tl = null
        tlDirty = true
        val tlv = tlOn
        post { listener.onTimeLapse(tlv) }
        postAnim(d)
        W = w
        H = h
        comp = Tex(w, h)
        scratch = Tex(w, h)
        strokeBuf = Tex(w, h)
        predBuf = Tex(w, h)
        preview = Tex(w, h)

        val am = ctx.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val mi = ActivityManager.MemoryInfo()
        am.getMemoryInfo(mi)
        val per = w.toLong() * h * 4
        val budget = (mi.totalMem * 0.22).toLong() - per * 7
        layerLimit = (budget / per).toInt().coerceIn(3, 120)
        // Riwayat urungkan paling banyak seperempat memori; sisanya untuk ColorDrop, pilihan, dan lain-lain
        // (di kanvas besar satu salinan layer saja sudah puluhan MB).
        undoBudget = (am.largeMemoryClass.toLong() shl 20) * 25 / 100

        val arr = meta.optJSONArray("layers") ?: JSONArray()
        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)
            val lid = o.optInt("id", d.nextId++)
            val layer = Layer(lid, Tex(w, h))
            layer.apply(Storage.propsFrom(o))
            val fut: Future<ByteBuffer?> = Storage.io.submit(Callable<ByteBuffer?> { Storage.readLayer(dir, lid, w, h) })
            val buf = fut.get()
            if (buf != null) writeRegion(layer.tex, IRect(0, 0, w, h), buf)
            layer.saveDirty = false
            d.layers.add(layer)
            if (lid >= d.nextId) d.nextId = lid + 1
        }
        if (d.layers.isEmpty()) {
            val layer = Layer(d.nextId++, Tex(w, h))
            layer.name = "Layer 1"
            d.layers.add(layer)
        }
        val ga = meta.optJSONArray("groups") ?: JSONArray()
        for (i in 0 until ga.length()) {
            val o = ga.getJSONObject(i)
            val gid = o.optInt("id", 0)
            if (gid <= 0) continue
            d.groups[gid] = GroupData(o.optString("name", "Grup"), o.optBoolean("visible", true), o.optBoolean("collapsed", false))
            if (gid >= d.nextId) d.nextId = gid + 1
        }
        sanitizeGroups(d)
        d.active = meta.optInt("active", d.layers.last().id)
        if (d.find(d.active) == null) d.active = d.layers.last().id
        guide = Storage.guideFrom(meta.optJSONObject("guide"))
        guideOn = meta.optBoolean("guideOn", false)
        guideEditing = false
        guideSel = -1
        guideKey = null
        doc = d
        dirty.set(0, 0, w, h)
        mipDirty = true
        Diag.log("Karya dibuka ${w}x$h, ${d.layers.size} layer, batas $layerLimit layer (${android.os.SystemClock.uptimeMillis() - t0} ms)")
        post { listener.onDocOpened(d.id, d.name, w, h) }
        val dpi = d.dpi
        post { listener.onDocDpi(dpi) }
        val g = guide
        val gOn = guideOn
        post { listener.onGuide(gOn, g) }
        val pt = d.paper
        val ps = d.paperStrength
        val pc = d.paperScale
        post { listener.onPaper(pt, ps, pc) }
        requestPaper(d)
        postLayers()
        makeThumbs()
    }

    private fun closeDoc() {
        val d = doc ?: return
        animPlaying = false; animShow = -1; animLastCur = -1
        runCatching { r3d?.setMesh(null) }
        view3D = false
        tl?.shutdown()
        tl = null
        tlTex?.delete(); tlTex = null
        tlFit?.delete(); tlFit = null
        for (l in d.layers) l.tex.delete()
        listOf(comp, scratch, strokeBuf, predBuf, preview, backup, maskTex, smT, smCarry, prevTex, prevTex2, dualBuf, dualPred).forEach { it?.delete() }
        comp = null; scratch = null; strokeBuf = null; predBuf = null; preview = null; dualBuf = null; dualPred = null
        backup = null; maskTex = null; smT = null; smCarry = null; prevTex = null; prevTex2 = null
        listOf(wetBase, wetF[0], wetF[1], wetW[0], wetW[1], wetFs, wetWs).forEach { it?.delete() }
        wetForget()
        undoStack.clear(); redoStack.clear()
        doc = null
        strokeActive = false
        substitute = null
        fillOn = false; adjOn = false; xfOn = false
        liqFree(); cloneOn = false; cloneMarker = null; marker = null
        pyrFree(); penFree()
        selTex?.delete(); selTex = null; selOn = false; selDraft = null
        xfFloat?.delete(); xfFloat = null; xfRest?.delete(); xfRest = null; xfSel = false
        worker.execute { selMask = null; selBase = null; selSaved.clear(); selBefore = null; selAutoSrc = null }
        post { listener.onSelectionChanged(false, 0) }
    }

    /** Hentikan thread bantu; dipanggil saat kanvas ditutup (konteks GL ikut hilang bersama view). */
    fun shutdown() {
        packer.shutdown()
        worker.shutdown()
    }

    private val pending = ArrayDeque<Pair<Future<*>, Long>>()

    /**
     * Simpan otomatis: ditunda (mengembalikan false) bila goresan atau mode khusus sedang berjalan, supaya
     * goresan pena tidak terpotong di tengah jalan.
     */
    fun autoSave(): Boolean {
        if (strokeActive || modal) return false
        save(null)
        return true
    }

    /** Simpan layer yang berubah, data dokumen, dan gambar kecil galeri. */
    fun save(done: (() -> Unit)? = null) {
        val d = doc
        if (d == null) {
            done?.let { post(it) }
            return
        }
        if (strokeActive) commitStroke()
        val t0 = android.os.SystemClock.uptimeMillis()
        var n = 0
        val dir = Storage.dir(ctx, d.id)
        val w = W
        val h = H
        for (l in d.layers) {
            if (!l.saveDirty) continue
            while (pending.sumOf { it.second } > (160L shl 20) && pending.isNotEmpty()) {
                runCatching { pending.removeFirst().first.get() }
            }
            val buf = readRegion(l.tex.fb, IRect(0, 0, W, H))
            l.saveDirty = false
            val lid = l.id
            n++
            val f = Storage.io.submit(Runnable { Storage.writeLayer(dir, lid, w, h, buf) })
            pending.addLast(f to buf.capacity().toLong())
        }
        pending.clear()
        tl?.finishSegment()
        val thumb = downscale(comp!!.fb, W, H, 512)
        Diag.log("Simpan: $n layer dibaca dari GPU (${android.os.SystemClock.uptimeMillis() - t0} ms)")
        val meta = metaJson(d)
        val keep = d.layers.map { it.id }.toSet()
        Storage.io.execute {
            runCatching { Storage.writeThumb(dir, thumb) }
            Storage.writeMeta(dir, meta)
            Storage.cleanup(dir, keep)
            if (done != null) post(done)
        }
    }

    private fun metaJson(d: Doc): JSONObject = JSONObject().apply {
        put("name", d.name); put("w", d.w); put("h", d.h)
        put("bg", d.bg); put("bgVisible", d.bgVisible); put("dpi", d.dpi)
        put("paper", d.paper); put("paperStrength", d.paperStrength.toDouble()); put("paperScale", d.paperScale.toDouble())
        put("active", d.active); put("nextId", d.nextId)
        put("created", d.created); put("modified", System.currentTimeMillis())
        put("timeMs", d.timeMs); put("strokes", d.strokes)
        put("tlOn", tlOn)
        put("profile", d.profile)
        if (d.mesh != null) put("model3d", true)
        if (d.face) put("face", true)
        put("assist", d.assist)
        put("anim", d.anim.toJson())
        put("holds", JSONObject().apply { for ((k, v) in d.holds) if (v > 0) put(k.toString(), v) })
        val arr = JSONArray()
        for (l in d.layers) arr.put(Storage.layerJson(l.id, l.props()))
        put("layers", arr)
        val ga = JSONArray()
        for ((gid, g) in d.groups) {
            ga.put(JSONObject().apply { put("id", gid); put("name", g.name); put("visible", g.visible); put("collapsed", g.collapsed) })
        }
        put("groups", ga)
        put("guide", Storage.guideJson(guide))
        put("guideOn", guideOn)
    }

    fun renameDoc(name: String) {
        doc?.name = name
    }

    // ================= masukan pena =================

    private fun drainInput() {
        var penX = Float.NaN
        var penY = 0f
        while (true) {
            val raw = input.poll() ?: break
            val list = if (raw.flags and RawPoint.SCREEN != 0) map3D(raw) else listOf(raw)
            for (r in list) {
            if (r.kind == RawPoint.BEGIN || r.kind == RawPoint.MOVE || r.kind == RawPoint.END) { penX = r.x; penY = r.y }
            if (r.kind == RawPoint.BEGIN || r.kind == RawPoint.END) noteActivity()
            when (r.kind) {
                RawPoint.BEGIN -> {
                    if (strokeActive) commitStroke()
                    beginStroke(r)
                    if (strokeActive) record(r)
                }
                RawPoint.MOVE -> if (strokeActive) {
                    if (assistDirs != null && !assistDecided && shapeBase == null) {
                        // Bantuan menggambar: titik ditahan sampai arah goresan jelas, lalu semuanya dikunci ke garis.
                        assistBuf.add(r)
                        if (hypot(r.x - assistSx, r.y - assistSy) >= assistThresh) {
                            decideAssist(r.x - assistSx, r.y - assistSy)
                            for (q in assistBuf) strokeMove(assisted(q))
                            assistBuf.clear()
                        }
                    } else {
                        strokeMove(assisted(r))
                    }
                }
                RawPoint.END -> if (strokeActive) {
                    if (assistBuf.isNotEmpty()) {
                        // Goresan pendek (arah belum jelas): titik apa adanya.
                        for (q in assistBuf) strokeMove(q)
                        assistBuf.clear()
                    }
                    val q = assisted(r)
                    if (shapeBase != null) {
                        shapeMove(q.x, q.y)
                    } else if (stampOn) {
                        stampX1 = q.x
                        stampY1 = q.y
                        renderStamp()
                    } else {
                        proc.add(q.x, q.y, q.p, q.tilt, q.az, q.t, dabs)
                        proc.end(dabs)
                        if (strokeDual != null) {
                            proc2.add(q.x, q.y, q.p, q.tilt, q.az, q.t, dabs2)
                            proc2.end(dabs2)
                        }
                        flushDabs()
                    }
                    commitStroke()
                }
                RawPoint.CANCEL -> if (strokeActive) cancelStroke()
                RawPoint.SHAPE -> if (strokeActive && shapeBase == null) trySnapShape()
                RawPoint.CONSTRAIN -> constrainShape()
            }
            }
            if (dabs.size >= 1500 || dabs2.size >= 1500) flushDabs()
        }
        if (shapePending) renderShapeNow()
        if (stampOn && stampPending && strokeActive) renderStamp()
        flushDabs()
        // Sumber Clone ikut bergerak bersama pena (jaraknya tetap).
        if (cloneOn && cloneFixed && !penX.isNaN()) {
            cloneSrcX = penX + cloneDx
            cloneSrcY = penY + cloneDy
            cloneMarker = floatArrayOf(cloneSrcX, cloneSrcY)
        }
    }

    private fun strokeMove(r: RawPoint) {
        if (shapeBase != null) shapeMove(r.x, r.y) else if (stampOn) {
            stampX1 = r.x
            stampY1 = r.y
            stampPending = true
        } else {
            record(r)
            proc.add(r.x, r.y, r.p, r.tilt, r.az, r.t, dabs)
            if (strokeDual != null) proc2.add(r.x, r.y, r.p, r.tilt, r.az, r.t, dabs2)
        }
    }

    // ================= panduan menggambar =================

    /** Siapkan Bantuan menggambar untuk goresan baru di [layer] (hanya bila panduan menyala dan layer memakainya). */
    private fun beginAssist(layer: Layer, r: RawPoint) {
        assistDirs = null
        assistDecided = false
        assistBuf.clear()
        strokeSym = null
        val g = guide
        if (!guideOn || !layer.assist || guideEditing) return
        if (g.type == DrawingGuide.SYM) {
            // Alat baur (dan cat basah) membawa cat dari goresan sebelumnya, jadi tidak dicerminkan.
            if (!strokeDirect) {
                strokeSym = GuideGeom.symmetry(g)
                symCx = g.centerX(W)
                symCy = g.centerY(H)
            }
            return
        }
        val dirs = GuideGeom.directions(g, r.x, r.y)
        if (dirs.isEmpty()) return
        assistDirs = dirs
        assistSx = r.x
        assistSy = r.y
        val v = view
        val zoom = max(1e-3f, sqrt(v[0] * v[0] + v[3] * v[3]))
        assistThresh = 9f * density / zoom
    }

    private fun decideAssist(mx: Float, my: Float) {
        val dirs = assistDirs ?: return
        val k = GuideGeom.pick(dirs, mx, my)
        if (k < 0) return
        assistDx = dirs[2 * k]
        assistDy = dirs[2 * k + 1]
        assistDecided = true
    }

    /** Titik dikunci ke garis bantuan (lewat titik awal goresan, searah garis terpilih). */
    private fun assisted(r: RawPoint): RawPoint {
        if (!assistDecided || assistDirs == null) return r
        val t = (r.x - assistSx) * assistDx + (r.y - assistSy) * assistDy
        return RawPoint(r.kind, assistSx + assistDx * t, assistSy + assistDy * t, r.p, r.tilt, r.az, r.t, r.flags)
    }

    /** Salinan simetri dab goresan (bila Simetri + Bantuan menggambar aktif). */
    private fun symmetrize(list: DabList) {
        val sym = strokeSym ?: return
        if (list.size > 0) GuideGeom.mirrorDabs(list, sym, symCx, symCy)
    }

    /** Garis panduan, garis utama, dan (saat diedit) titik pegangannya di layar. */
    private fun drawGuide(v: FloatArray) {
        if (!guideOn && !guideEditing) return
        val g = guide
        val key = Triple(g, W, H)
        if (key != guideKey) {
            val major = ArrayList<Float>()
            guideSegs = GuideGeom.lines(g, W, H, major)
            guideMajor = major.toFloatArray()
            guideKey = key
        }
        val a = g.opacity.coerceIn(0f, 1f)
        val cr = Color.red(g.color) / 255f
        val cg = Color.green(g.color) / 255f
        val cb = Color.blue(g.color) / 255f
        val wpx = (0.6f + g.thickness.coerceIn(0f, 1f) * 4.4f) * density
        val vm = Matrix().apply { setValues(v) }
        guideQuads(guideSegs, vm, wpx, cr, cg, cb, a)
        guideQuads(guideMajor, vm, wpx * 1.7f, cr, cg, cb, a)
        if (!guideEditing) return
        val hr = 9f * density
        if (g.type == DrawingGuide.PERSP) {
            for (i in 0 until g.vpCount) {
                val p = floatArrayOf(g.vpX(i), g.vpY(i))
                vm.mapPoints(p)
                if (i == guideSel) dot(p[0], p[1], hr * 1.45f, 1f, 1f, 1f)
                dot(p[0], p[1], hr, 0.18f, 0.6f, 1f)
            }
            return
        }
        val c = floatArrayOf(g.centerX(W), g.centerY(H), g.centerX(W) + cos(g.angle), g.centerY(H) + sin(g.angle))
        vm.mapPoints(c)
        var dx = c[2] - c[0]
        var dy = c[3] - c[1]
        val l = hypot(dx, dy)
        if (l < 1e-6f) { dx = 1f; dy = 0f } else { dx /= l; dy /= l }
        val off = DrawingGuide.ROTATE_HANDLE_DP * density
        val rx = c[0] + dx * off
        val ry = c[1] + dy * off
        lines(floatArrayOf(c[0], c[1], rx, ry), G.GL_LINES, 1.5f, 0.18f, 0.6f, 1f)
        dot(c[0], c[1], hr * 1.1f, 0.2f, 0.82f, 0.35f)
        dot(rx, ry, hr, 0.18f, 0.6f, 1f)
    }

    /** Ruas garis (piksel kanvas) digambar sebagai segi empat setebal [width] piksel layar, tembus pandang. */
    private fun guideQuads(segs: FloatArray, vm: Matrix, width: Float, r: Float, g: Float, b: Float, a: Float) {
        if (segs.isEmpty()) return
        if (guideScreen.size < segs.size) guideScreen = FloatArray(segs.size)
        vm.mapPoints(guideScreen, 0, segs, 0, segs.size / 2)
        val hw = width / 2f
        G.glEnable(G.GL_BLEND)
        G.glBlendFunc(G.GL_ONE, G.GL_ONE_MINUS_SRC_ALPHA)
        pLine.use()
        pLine.f2("uScreen", screenW.toFloat(), screenH.toFloat())
        pLine.f4("uColor", r * a, g * a, b * a, a)
        G.glBindVertexArray(guideVao)
        G.glBindBuffer(G.GL_ARRAY_BUFFER, guideVbo)
        val n = segs.size / 4
        var i = 0
        while (i < n) {
            val m = min(guideBatch, n - i)
            guideFloats.clear()
            for (k in i until i + m) {
                val o = k * 4
                val x0 = guideScreen[o]; val y0 = guideScreen[o + 1]
                val x1 = guideScreen[o + 2]; val y1 = guideScreen[o + 3]
                var nx = y0 - y1
                var ny = x1 - x0
                val l = hypot(nx, ny)
                if (l < 1e-6f) { nx = 0f; ny = 0f } else { nx = nx / l * hw; ny = ny / l * hw }
                guideFloats.put(x0 + nx); guideFloats.put(y0 + ny)
                guideFloats.put(x1 + nx); guideFloats.put(y1 + ny)
                guideFloats.put(x0 - nx); guideFloats.put(y0 - ny)
                guideFloats.put(x1 + nx); guideFloats.put(y1 + ny)
                guideFloats.put(x1 - nx); guideFloats.put(y1 - ny)
                guideFloats.put(x0 - nx); guideFloats.put(y0 - ny)
            }
            guideFloats.flip()
            G.glBufferSubData(G.GL_ARRAY_BUFFER, 0, m * 12 * 4, guideFloats)
            G.glDrawArrays(G.GL_TRIANGLES, 0, m * 6)
            i += m
        }
        G.glBindVertexArray(0)
        G.glDisable(G.GL_BLEND)
    }

    // ================= bentuk otomatis =================

    private fun record(r: RawPoint) {
        if (rawN >= 20000) return
        if (rawN == rawX.size) {
            val n = rawX.size * 2
            rawX = rawX.copyOf(n); rawY = rawY.copyOf(n); rawP = rawP.copyOf(n); rawTilt = rawTilt.copyOf(n)
        }
        rawX[rawN] = r.x; rawY[rawN] = r.y; rawP[rawN] = r.p; rawTilt[rawN] = r.tilt
        rawAz = r.az
        rawN++
    }

    private fun resetShape() {
        shapeBase = null
        shapeConstrain = false
        shapeActive = false
        shape = null
        shapePending = false
        shapeMoved = false
        shapeBox.clear()
        freeBox.clear()
        freeBuf = null
    }

    /** Pena ditahan di ujung goresan: kenali bentuknya, simpan coretan asli, gambar versi rapi. */
    private fun trySnapShape() {
        // Kuas dan penghapus (penghapus bisa dimatikan di pengaturan); alat baur tidak pernah diubah jadi bentuk.
        if (strokeDirect || stampOn || strokeLive || rawN < 3 || assistDirs != null) return
        val s = runCatching { QuickShape.recognize(rawX, rawY, rawN) }.getOrNull() ?: return
        flushDabs()
        val fb = strokeBox.copy().clampTo(W, H)
        freeBox.set(fb.x0, fb.y0, fb.x1, fb.y1)
        freeBuf = if (fb.isEmpty) null else readRegion(strokeBuf!!.fb, fb)
        if (!fb.isEmpty) clearRect(strokeBuf!!, fb)
        dirty.union(fb)
        // Bentuk rapi digambar tanpa kuas kedua.
        clearDual()
        if (!predBox.isEmpty) {
            clearRect(predBuf!!, predBox)
            dirty.union(predBox)
            predBox.clear()
        }
        predOn = false
        shapeP = median(rawP, rawN)
        shapeTilt = median(rawTilt, rawN)
        shapeBase = s
        shape = s
        shapeActive = true
        shapePenX = rawX[rawN - 1]
        shapePenY = rawY[rawN - 1]
        shapeCurX = shapePenX
        shapeCurY = shapePenY
        shapeMoved = false
        shapeBox.clear()
        renderShapeNow()
        val label = s.kind.label
        post { listener.onMessage("$label · geser untuk ubah ukuran/putar") }
    }

    /** Jari kedua menyentuh layar: bentuk jadi presisi; putaran berikutnya ikut dibulatkan 15°. */
    private fun constrainShape() {
        val base = shapeBase ?: return
        if (!strokeActive || shapeConstrain) return
        shapeConstrain = true
        shapeBase = base.perfect()
        shapePending = true
        val label = when (base.kind) {
            QuickShape.Kind.ELLIPSE, QuickShape.Kind.CIRCLE -> "Lingkaran sempurna"
            QuickShape.Kind.RECT -> "Persegi"
            QuickShape.Kind.TRIANGLE -> "Segitiga sama sisi"
            QuickShape.Kind.POLYGON -> "Segi banyak beraturan"
            QuickShape.Kind.ARC -> "Busur presisi"
            else -> "Garis presisi (kelipatan 15°)"
        }
        post { listener.onMessage(label) }
    }

    private fun median(a: FloatArray, n: Int): Float {
        if (n <= 0) return 1f
        val c = a.copyOf(n)
        c.sort()
        return c[n / 2]
    }

    private fun shapeMove(x: Float, y: Float) {
        shapeCurX = x
        shapeCurY = y
        if (!shapeMoved && hypot(x - shapePenX, y - shapePenY) > 6f) shapeMoved = true
        if (shapeMoved) shapePending = true
    }

    /** Bentuk mengikuti pena: garis lurus = ujungnya ikut pena; bentuk lain diputar/diperbesar. */
    private fun renderShapeNow() {
        val base = shapeBase ?: return
        shapePending = false
        val s = if (!shapeMoved) base else when (base.kind) {
            QuickShape.Kind.LINE -> if (shapeConstrain) base.withEnd(shapeCurX, shapeCurY).perfect() else base.withEnd(shapeCurX, shapeCurY)
            else -> {
                val px: Float
                val py: Float
                when {
                    base.closed -> { px = base.cx; py = base.cy }
                    base.kind == QuickShape.Kind.ARC -> { px = base.cx + base.a * cos(base.ang); py = base.cy + base.a * sin(base.ang) }
                    else -> { px = base.v[0]; py = base.v[1] }
                }
                val ax = shapePenX - px; val ay = shapePenY - py
                val bx = shapeCurX - px; val by = shapeCurY - py
                val l0 = hypot(ax, ay)
                var rot = atan2(by, bx) - atan2(ay, ax)
                if (shapeConstrain) rot = QuickShape.snapAngle(rot, QuickShape.STEP15)
                if (l0 < 4f) base else base.transformed(px, py, (hypot(bx, by) / l0).coerceIn(0.05f, 20f), rot)
            }
        }
        shape = s
        val sb = strokeBuf ?: return
        if (!shapeBox.isEmpty) {
            clearRect(sb, shapeBox)
            dirty.union(shapeBox)
        }
        shapeBox.clear()
        drawShape(s)
    }

    private fun drawShape(s: QuickShape.Shape) {
        val set = strokeSet ?: return
        val sb = strokeBuf ?: return
        val pb = set.brush.copy(streamline = 0f, stabilization = 0f, motionFilter = 0f, taperStart = 0f, taperEnd = 0f)
        shapeProc.begin(
            pb, set.sizePx, strokeColor,
            pressure = strokeFlags and RawPoint.PRESSURE != 0,
            tilt = strokeFlags and RawPoint.TILT != 0,
            pressureGamma = strokeGamma,
            spacing = set.brush.spacing,
            maxDiameter = 6000f,
        )
        val path = s.path(3f)
        var t = 0L
        var i = 0
        while (i + 1 < path.size) {
            shapeProc.add(path[i], path[i + 1], shapeP, shapeTilt, rawAz, t, shapeDabs)
            t += 8
            i += 2
        }
        shapeProc.end(shapeDabs)
        symmetrize(shapeDabs)
        if (shapeDabs.size > 0) {
            renderStrokeDabs(sb, shapeDabs, set.brush)
            shapeDabs.bounds(tmpRect)
            tmpRect.clampTo(W, H)
            shapeBox.union(tmpRect)
            dirty.union(tmpRect)
        }
        shapeDabs.clear()
    }

    /**
     * Bentuk disimpan dalam dua langkah urungkan: dulu coretan asli, lalu bentuk rapi.
     * Jadi sekali "urungkan" mengembalikan coretan tangan aslinya.
     */
    private fun commitShape() {
        if (shapePending) renderShapeNow()
        val layer = strokeLayer
        val sb = strokeBuf!!
        val fb = freeBox.copy().clampTo(W, H)
        val sbox = shapeBox.copy()
        if (layer != null && doc?.find(layer.id) != null && !strokeDirect) {
            if (!sbox.isEmpty) clearRect(sb, sbox)
            // Langkah 1: coretan asli ditempel ke layer (piksel layer sebelumnya disimpan).
            val buf = freeBuf
            var orig: java.nio.ByteBuffer? = null
            if (buf != null && !fb.isEmpty) {
                orig = readRegion(layer.tex.fb, fb)
                writeRegion(sb, fb, buf)
                commitRegion(layer, fb)
            }
            // Langkah 2: coretan asli diganti bentuk rapi (layer dikembalikan dulu, lalu bentuk ditempel).
            val s = shape
            if (s != null) {
                shapeBox.clear()
                drawShape(s)
                val u = shapeBox.copy().clampTo(W, H)
                if (orig != null) u.union(fb)
                if (!u.isEmpty) {
                    val before = readRegion(layer.tex.fb, u)
                    if (orig != null) writeRegion(layer.tex, fb, orig)
                    updateStrokePreview(u, false)
                    blit(preview!!.fb, u.x0, u.y0, u.x1, u.y1, layer.tex.fb, u.x0, u.y0, u.x1, u.y1, false)
                    clearRect(sb, u)
                    pushEdit(Edit.Pixels(layer.id, u, RegionData(u.w, u.h, before)))
                    layerChanged(layer)
                }
                sbox.union(shapeBox)
            }
        } else if (!sbox.isEmpty) {
            clearRect(sb, sbox)
        }
        substitute = null
        dirty.union(fb)
        dirty.union(sbox)
        strokeActive = false
        strokeLayer = null
        resetShape()
        if (strokeTool == ToolKind.PAINT) {
            val c = strokeColor
            post { listener.onStrokeDone(c) }
        }
    }

    /** Isi strokeBuf di wilayah [r0] ditempelkan ke layer sebagai satu langkah urungkan. */
    private fun commitRegion(layer: Layer, r0: IRect) {
        val r = r0.copy().clampTo(W, H)
        if (r.isEmpty) return
        val before = readRegion(layer.tex.fb, r)
        updateStrokePreview(r, false)
        blit(preview!!.fb, r.x0, r.y0, r.x1, r.y1, layer.tex.fb, r.x0, r.y0, r.x1, r.y1, false)
        clearRect(strokeBuf!!, r)
        pushEdit(Edit.Pixels(layer.id, r, RegionData(r.w, r.h, before)))
        layerChanged(layer)
    }

    private fun beginStroke(r: RawPoint) {
        val d = doc ?: return
        val ts = tool ?: return
        // ColorDrop yang masih terbuka langsung disimpan begitu mulai menggores lagi.
        if (fillOn) fillCommit()
        if (modal) return
        val layer = d.activeLayer() ?: return
        if (!d.shown(layer)) {
            val msg = if (layer.visible) "Grup layer ini sedang disembunyikan" else "Layer ini sedang disembunyikan"
            post { listener.onMessage(msg) }
            return
        }
        if (layer.locked) {
            post { listener.onMessage("Layer ini terkunci") }
            return
        }
        strokeTool = if (r.flags and RawPoint.ERASER != 0) ToolKind.ERASE else ts.tool
        val set = when (strokeTool) {
            ToolKind.SMUDGE -> ts.smudge
            ToolKind.ERASE -> ts.erase
            else -> ts.paint
        }
        strokeSet = set
        strokeLayer = layer
        strokeColor = ts.color
        strokeFlags = r.flags
        strokeGamma = ts.gamma
        rawN = 0
        resetShape()
        val smudge = strokeTool == ToolKind.SMUDGE
        // Cat basah (Wet Mix / mode blending): dab langsung bercampur dengan cat di layer, seperti alat baur.
        strokeWetMix = strokeTool == ToolKind.PAINT && set.brush.wetMix() && !cloneActive
        strokeDirect = smudge || strokeWetMix
        // Cat air hidup: lanjutkan sesi basah bila kuas, opasitas, dan layer sama; selain itu cat yang basah langsung kering.
        val live = strokeTool == ToolKind.PAINT && !strokeDirect && !cloneActive && set.brush.watercolor &&
            set.brush.wcSpread > 0.001f && !set.brush.stamp
        val ws = wetSet
        if (wetOn && !(live && layer.id == wetLayerId && ws != null && ws.brush.id == set.brush.id && abs(ws.opacity - set.opacity) < 0.01f)) wetEnd()
        strokeLive = live && (wetOn || wetBegin(layer, set))
        liveBox.clear()
        if (strokeLive) wetSnapshot()
        // Dab sementara tiap bingkai: ekor goresan sampai ujung pena (selalu) + prediksi (bila diaktifkan).
        predOn = !strokeDirect
        predAhead = ts.prediction
        val spacing = if (smudge) min(set.brush.spacing, 0.08f) else if (strokeWetMix) min(set.brush.spacing, 0.12f) else set.brush.spacing
        proc.begin(
            set.brush, set.sizePx, ts.color,
            pressure = r.flags and RawPoint.PRESSURE != 0,
            tilt = r.flags and RawPoint.TILT != 0,
            pressureGamma = ts.gamma,
            spacing = spacing,
            maxDiameter = if (smudge) 700f else if (strokeWetMix) 1000f else 6000f,
            smoothing = ts.smoothing,
            canvasPerDp = density / max(1e-3f, sqrt(view[0] * view[0] + view[3] * view[3])),
            // Coreng (smudge) tidak memakai tampilan sementara, jadi dabnya tidak boleh ditahan untuk runcing akhir.
            allowHold = !strokeDirect,
            color2 = ts.color2,
            viewRot = atan2(view[3], view[0]),
        )
        strokeBox.clear()
        predBox.clear()
        wetDist = 0f
        wetLastX = Float.NaN
        // Kuas ganda: kuas kedua menggores di jalur yang sama ke topeng terpisah (penghalusnya disamakan).
        strokeDual = null
        val dualB = set.dual
        if (dualB != null && strokeTool == ToolKind.PAINT && !strokeDirect && !cloneActive && !strokeLive) {
            if (dualBuf == null) dualBuf = Tex(W, H)
            if (dualPred == null) dualPred = Tex(W, H)
            strokeDual = dualB
            dualBox.clear()
            dualPredBox.clear()
            val b2 = dualB.copy(
                streamline = set.brush.streamline, streamlinePressure = set.brush.streamlinePressure,
                stabilization = set.brush.stabilization, motionFilter = set.brush.motionFilter,
                motionExpression = set.brush.motionExpression, stamp = false,
            )
            proc2.begin(
                b2, set.sizePx * set.brush.dualSize.coerceIn(0.1f, 4f), Color.WHITE,
                pressure = r.flags and RawPoint.PRESSURE != 0,
                tilt = r.flags and RawPoint.TILT != 0,
                pressureGamma = ts.gamma,
                spacing = b2.spacing,
                maxDiameter = 6000f,
                smoothing = ts.smoothing,
                canvasPerDp = density / max(1e-3f, sqrt(view[0] * view[0] + view[3] * view[3])),
                allowHold = true,
                viewRot = atan2(view[3], view[0]),
            )
        }
        val rb = set.brush
        rendPad = if (strokeTool == ToolKind.PAINT && !strokeDirect && (rb.wetEdges > 0f || rb.burntEdges > 0f)) ceil(edgeRadius(set.sizePx)).toInt() + 2 else 0
        if (strokeDirect) {
            if (backup == null) backup = Tex(W, H)
            if (smT == null) smT = Tex(1024, 1024)
            if (smCarry == null) smCarry = Tex(256, 256)
            blit(layer.tex.fb, 0, 0, W, H, backup!!.fb, 0, 0, W, H, false)
            smudgeFirst = true
        } else {
            blit(layer.tex.fb, 0, 0, W, H, preview!!.fb, 0, 0, W, H, false)
            substitute = preview
            substituteFor = layer.id
        }
        strokeActive = true
        beginAssist(layer, r)
        if (cloneActive && !cloneFixed) {
            cloneDx = cloneSrcX - r.x
            cloneDy = cloneSrcY - r.y
            cloneFixed = true
        }
        strokeWet = set.brush.watercolor && !strokeDirect
        if (strokeWet) {
            // Sesi cat air hidup memakai pola noise yang sama sepanjang sesi (tepi cat yang basah tidak berubah bentuk).
            if (!strokeLive) {
                wetSeedX = (Math.random() * 256).toFloat()
                wetSeedY = (Math.random() * 256).toFloat()
            }
            wetPad = ceil(wetRadius(set.sizePx)).toInt() + 2
        }
        stampOn = set.brush.stamp && !strokeDirect
        if (stampOn) {
            strokeDual = null
            predOn = false
            stampX0 = r.x; stampY0 = r.y
            stampX1 = r.x; stampY1 = r.y
            stampBox.clear()
            stampPending = true
            return
        }
        proc.add(r.x, r.y, r.p, r.tilt, r.az, r.t, dabs)
        if (strokeDual != null) proc2.add(r.x, r.y, r.p, r.tilt, r.az, r.t, dabs2)
    }

    /** Jari-jari pembacaan sekitar titik untuk tepi basah/terbakar (piksel kanvas). */
    private fun edgeRadius(sizePx: Float) = max(1.5f, sizePx * 0.12f).coerceAtMost(60f)

    /**
     * Gambar ulang cap stempel: ketukan = cap seukuran kuas di titik itu; tarikan = cap membentang dari titik awal
     * ke titik akhir (lebar bagian bergambar = panjang tarikan), diputar mengikuti arah tarikan dan tetap tegak.
     */
    private fun renderStamp() {
        stampPending = false
        val set = strokeSet ?: return
        val sb = strokeBuf ?: return
        if (!stampBox.isEmpty) {
            clearRect(sb, stampBox)
            dirty.union(stampBox)
            stampBox.clear()
        }
        val b = set.brush
        val dx = stampX1 - stampX0
        val dy = stampY1 - stampY0
        val len = hypot(dx, dy)
        val ext = if (b.tipFile.isEmpty()) 1f to 1f else tipExtents.getOrPut(b.tipFile) { tipExtent(b.tipFile) ?: (1f to 1f) }
        var ang = Math.toRadians(b.angle.toDouble()).toFloat()
        val cx: Float
        val cy: Float
        val size: Float
        if (len < max(14f, set.sizePx * 0.06f)) {
            cx = stampX0; cy = stampY0; size = set.sizePx
        } else {
            cx = (stampX0 + stampX1) / 2f
            cy = (stampY0 + stampY1) / 2f
            size = len / ext.first.coerceAtLeast(0.05f)
            var a = atan2(dy, dx)
            if (dx < 0f) a += PI.toFloat() // ditarik dari kanan ke kiri: cap tetap tegak
            if (a > PI.toFloat()) a -= 2f * PI.toFloat()
            if (abs(a) < Math.toRadians(3.0).toFloat()) a = 0f // hampir mendatar = mendatar
            ang += a
        }
        val c = strokeColor
        stampDabs.clear()
        stampDabs.add(
            cx, cy, size.coerceIn(1f, 8000f), ang, b.roundness, b.flow.coerceIn(0f, 1f),
            Color.red(c) / 255f, Color.green(c) / 255f, Color.blue(c) / 255f,
        )
        symmetrize(stampDabs)
        renderStrokeDabs(sb, stampDabs, b)
        stampDabs.bounds(tmpRect)
        tmpRect.clampTo(W, H)
        stampBox.set(tmpRect.x0, tmpRect.y0, tmpRect.x1, tmpRect.y1)
        strokeBox.clear()
        strokeBox.union(stampBox)
        dirty.union(stampBox)
    }

    private fun flushDabs() {
        if (dabs2.size > 0) {
            if (strokeActive && strokeDual != null) {
                symmetrize(dabs2)
                renderDabs(dualBuf!!, dabs2, strokeDual!!, 0f, 0f)
                dabs2.bounds(tmpRect)
                tmpRect.clampTo(W, H)
                dualBox.union(tmpRect)
                strokeBox.union(tmpRect)
                dirty.union(tmpRect)
            }
            dabs2.clear()
        }
        if (dabs.size == 0) return
        if (!strokeActive) { dabs.clear(); return }
        if (!strokeDirect) symmetrize(dabs)
        dabs.bounds(tmpRect)
        tmpRect.clampTo(W, H)
        val set = strokeSet!!
        when {
            strokeTool == ToolKind.SMUDGE -> renderSmudge(dabs, set)
            strokeWetMix -> renderWetMix(dabs, set)
            else -> renderStrokeDabs(strokeBuf!!, dabs, set.brush)
        }
        strokeBox.union(tmpRect)
        if (strokeLive) liveBox.union(tmpRect)
        dirty.union(tmpRect)
        dabs.clear()
    }

    private fun updatePrediction() {
        if (!predBox.isEmpty) {
            clearRect(predBuf!!, predBox)
            dirty.union(predBox)
            predBox.clear()
        }
        predDabs.clear()
        proc.predict(if (predAhead) 9f else 0f, predDabs)
        symmetrize(predDabs)
        if (predDabs.size > 0) {
            renderStrokeDabs(predBuf!!, predDabs, strokeSet!!.brush)
            predDabs.bounds(predBox)
            predBox.clampTo(W, H)
            dirty.union(predBox)
        }
        if (!dualPredBox.isEmpty) {
            dualPred?.let { clearRect(it, dualPredBox) }
            dirty.union(dualPredBox)
            dualPredBox.clear()
        }
        val db = strokeDual
        if (db != null) {
            predDabs2.clear()
            proc2.predict(if (predAhead) 9f else 0f, predDabs2)
            symmetrize(predDabs2)
            if (predDabs2.size > 0) {
                renderDabs(dualPred!!, predDabs2, db, 0f, 0f)
                predDabs2.bounds(dualPredBox)
                dualPredBox.clampTo(W, H)
                dirty.union(dualPredBox)
            }
        }
    }

    /** Kosongkan topeng kuas ganda (setelah goresan selesai/batal, atau saat goresan jadi bentuk rapi). */
    private fun clearDual() {
        if (!dualBox.isEmpty) { dualBuf?.let { clearRect(it, dualBox) }; dirty.union(dualBox) }
        if (!dualPredBox.isEmpty) { dualPred?.let { clearRect(it, dualPredBox) }; dirty.union(dualPredBox) }
        dualBox.clear()
        dualPredBox.clear()
        dabs2.clear()
        predDabs2.clear()
        strokeDual = null
    }

    private fun commitStroke() {
        if (shapeBase != null) { commitShape(); return }
        flushDabs()
        val layer = strokeLayer
        val r = strokeBox.copy().clampTo(W, H)
        if (layer != null && doc?.find(layer.id) != null) {
            if (strokeDirect) {
                if (!r.isEmpty) {
                    val before = readRegion(backup!!.fb, r)
                    pushEdit(Edit.Pixels(layer.id, r, RegionData(r.w, r.h, before)))
                    layerChanged(layer)
                }
            } else {
                if (!predBox.isEmpty) {
                    clearRect(predBuf!!, predBox)
                    dirty.union(predBox)
                    predBox.clear()
                }
                if (strokeLive && wetOn) wetCommit(layer) else commitRegion(layer, r)
            }
        }
        strokeLive = false
        clearDual()
        substitute = null
        dirty.union(r)
        strokeActive = false
        strokeLayer = null
        if (!r.isEmpty) doc?.let { it.strokes++ }
        if (!r.isEmpty && strokeTool == ToolKind.PAINT) {
            val c = strokeColor
            post { listener.onStrokeDone(c) }
        }
    }

    private fun cancelStroke() {
        if (!shapeBox.isEmpty) strokeBox.union(shapeBox)
        resetShape()
        val layer = strokeLayer
        val r = strokeBox.copy().clampTo(W, H)
        if (strokeDirect) {
            if (layer != null && !r.isEmpty) {
                blit(backup!!.fb, r.x0, r.y0, r.x1, r.y1, layer.tex.fb, r.x0, r.y0, r.x1, r.y1, false)
                layerChanged(layer, notify = false)
            }
        } else {
            if (!r.isEmpty) clearRect(strokeBuf!!, r)
            if (!predBox.isEmpty) clearRect(predBuf!!, predBox)
            if (strokeLive && wetOn) wetCancel()
        }
        strokeLive = false
        dirty.union(r)
        dirty.union(predBox)
        predBox.clear()
        dabs.clear()
        clearDual()
        assistBuf.clear()
        substitute = null
        strokeActive = false
        strokeLayer = null
    }

    // ================= menggambar dab =================

    private fun putV(x: Float, y: Float, u: Float, v: Float, r: Float, g: Float, b: Float, a: Float, bl: Float) {
        dabFloats.put(x); dabFloats.put(y); dabFloats.put(u); dabFloats.put(v)
        dabFloats.put(r); dabFloats.put(g); dabFloats.put(b); dabFloats.put(a); dabFloats.put(bl)
    }

    private fun writeDab(list: DabList, k: Int) {
        val d = list.data
        val o = k * DabList.S
        val x = d[o]; val y = d[o + 1]; val s = d[o + 2]; val ang = d[o + 3]; val rnd = d[o + 4]
        val a = d[o + 5]; val r = d[o + 6]; val g = d[o + 7]; val b = d[o + 8]; val bl = d[o + 9]
        val hw = s * 0.5f
        val hh = hw * rnd
        val c = cos(ang)
        val sn = sin(ang)
        // Sudut lokal: (-hw,-hh) (hw,-hh) (-hw,hh) (hw,hh)
        val x0 = x + (-hw) * c - (-hh) * sn; val y0 = y + (-hw) * sn + (-hh) * c
        val x1 = x + hw * c - (-hh) * sn; val y1 = y + hw * sn + (-hh) * c
        val x2 = x + (-hw) * c - hh * sn; val y2 = y + (-hw) * sn + hh * c
        val x3 = x + hw * c - hh * sn; val y3 = y + hw * sn + hh * c
        putV(x0, y0, 0f, 0f, r, g, b, a, bl)
        putV(x1, y1, 1f, 0f, r, g, b, a, bl)
        putV(x2, y2, 0f, 1f, r, g, b, a, bl)
        putV(x1, y1, 1f, 0f, r, g, b, a, bl)
        putV(x3, y3, 1f, 1f, r, g, b, a, bl)
        putV(x2, y2, 0f, 1f, r, g, b, a, bl)
    }

    /** Tekstur kuas (pola impor atau tekstur bawaan), atau null bila kuas tanpa tekstur. */
    private fun grainTexFor(brush: Brush): Int? =
        customR8(brush.grainFile, true) ?: if (brush.grain != Grain.NONE) grainTex(brush.grain) else null

    /** [perDab] false: tekstur tidak dikenakan per cap karena nanti dikenakan pada seluruh goresan (lihat previewPass). */
    private fun bindBrush(p: Program, brush: Brush, perDab: Boolean = true) {
        p.tex("uTip", 0, tipTexFor(brush))
        val g = if (perDab) grainTexFor(brush) else null
        if (g != null) {
            p.tex("uGrain", 1, g)
            p.f("uGrainStrength", brush.grainStrength)
            p.f("uGrainScale", 512f * max(0.05f, brush.grainScale))
        } else {
            p.tex("uGrain", 1, tipTexFor(brush))
            p.f("uGrainStrength", 0f)
            p.f("uGrainScale", 512f)
        }
    }

    /** Goresan ke strokeBuf/predBuf: tekstur per goresan dikenakan saat digabung, bukan per cap. */
    private fun renderStrokeDabs(target: Tex, list: DabList, brush: Brush) =
        renderDabs(target, list, brush, 0f, 0f, perDab = !brush.grainStroke)

    private fun renderDabs(target: Tex, list: DabList, brush: Brush, offX: Float, offY: Float, perDab: Boolean = true) {
        if (list.size == 0) return
        G.glBindFramebuffer(G.GL_FRAMEBUFFER, target.fb)
        G.glViewport(0, 0, target.w, target.h)
        G.glEnable(G.GL_BLEND)
        G.glBlendFunc(G.GL_ONE, G.GL_ONE_MINUS_SRC_ALPHA)
        // Glaze merata: cap dalam satu goresan tidak menumpuk (ambil yang paling pekat), jadi goresan rata.
        val uniform = brush.render == 1
        if (uniform) G.glBlendEquation(G.GL_MAX)
        pDab.use()
        pDab.f2("uTarget", target.w.toFloat(), target.h.toFloat())
        pDab.f2("uOffset", offX, offY)
        pDab.f("uBoost", when (brush.render) { 2 -> 1.6f; 3 -> 2.4f; else -> 1f })
        bindBrush(pDab, brush, perDab)
        G.glBindVertexArray(dabVao)
        G.glBindBuffer(G.GL_ARRAY_BUFFER, dabVbo)
        var i = 0
        while (i < list.size) {
            val n = min(maxBatch, list.size - i)
            dabFloats.clear()
            for (k in i until i + n) writeDab(list, k)
            dabFloats.flip()
            G.glBufferData(G.GL_ARRAY_BUFFER, n * 6 * vtxN * 4, dabFloats, G.GL_STREAM_DRAW)
            G.glDrawArrays(G.GL_TRIANGLES, 0, n * 6)
            i += n
        }
        G.glBindVertexArray(0)
        if (uniform) G.glBlendEquation(G.GL_FUNC_ADD)
        G.glDisable(G.GL_BLEND)
    }

    /**
     * Wet Mix: tiap cap mencampur cat kuas (muatan menipis sepanjang goresan, diencerkan air) dengan cat yang
     * terseret dari kanvas, langsung di layer (seperti alat baur). Salinan layer awal ada di [backup].
     */
    private fun renderWetMix(list: DabList, set: BrushSetting) {
        val layer = strokeLayer ?: return
        val t = smT!!
        val carry = smCarry!!
        val brush = set.brush
        val d = list.data
        val r = IRect()
        val charge = brush.wmCharge.coerceIn(0f, 1f)
        // Panjang goresan sampai cat di kuas habis (muatan penuh = tidak pernah habis).
        val runLen = max(4f, set.sizePx) * (1.5f + 45f * charge * charge)
        val pull = max(brush.wmPull, when (brush.render) { 4 -> 0.3f; 5 -> 0.18f; else -> 0f }).coerceIn(0f, 1f)
        val attack = (brush.wmAttack * if (brush.render == 5) 1.3f else 1f).coerceIn(0f, 1.3f)
        val gt = grainTexFor(brush)
        for (k in 0 until list.size) {
            val o = k * DabList.S
            val x = d[o]; val y = d[o + 1]; val s = d[o + 2]
            if (!wetLastX.isNaN()) wetDist += hypot(x - wetLastX, y - wetLastY)
            wetLastX = x; wetLastY = y
            val blurJ = if (brush.wmBlurJitter > 0f) 1f + (wetRnd.nextFloat() * 2f - 1f) * brush.wmBlurJitter else 1f
            val blurR = (brush.wmBlur * s * 0.12f * blurJ).coerceIn(0f, 24f)
            val rad = s * 0.71f + 1f + blurR
            r.set(floor(x - rad).toInt(), floor(y - rad).toInt(), ceil(x + rad).toInt(), ceil(y + rad).toInt())
            r.clampTo(W, H)
            if (r.isEmpty || r.w > t.w || r.h > t.h) continue
            blit(layer.tex.fb, r.x0, r.y0, r.x1, r.y1, t.fb, 0, 0, r.w, r.h, false)
            if (smudgeFirst) {
                blit(layer.tex.fb, r.x0, r.y0, r.x1, r.y1, carry.fb, 0, 0, carry.w, carry.h, true)
                smudgeFirst = false
            }
            // Muatan 0 = kuas tanpa cat (hanya menyeret/mengaburkan cat yang ada), 50% ke atas = penuh di awal goresan.
            val load = if (charge >= 0.999f) 1f else min(1f, charge * 2f) * kotlin.math.exp(-wetDist / runLen)
            val dil = (brush.wmDilution + if (brush.wmWetJitter > 0f) (wetRnd.nextFloat() * 2f - 1f) * brush.wmWetJitter * 0.5f else 0f).coerceIn(0f, 1f)
            G.glBindFramebuffer(G.GL_FRAMEBUFFER, layer.tex.fb)
            G.glViewport(0, 0, W, H)
            G.glDisable(G.GL_BLEND)
            pWet.use()
            pWet.f2("uTarget", W.toFloat(), H.toFloat())
            pWet.f2("uOffset", 0f, 0f)
            pWet.tex("uTip", 0, tipTexFor(brush))
            pWet.tex("uCur", 1, t.id)
            pWet.tex("uCarry", 2, carry.id)
            if (gt != null) {
                pWet.tex("uGrain", 4, gt)
                pWet.f("uGrainStrength", brush.grainStrength)
                pWet.f("uGrainScale", 512f * max(0.05f, brush.grainScale))
            } else {
                pWet.tex("uGrain", 4, t.id)
                pWet.f("uGrainStrength", 0f)
                pWet.f("uGrainScale", 512f)
            }
            pWet.f2("uRectMin", r.x0.toFloat(), r.y0.toFloat())
            pWet.f2("uRectSize", r.w.toFloat(), r.h.toFloat())
            pWet.f2("uTSize", t.w.toFloat(), t.h.toFloat())
            pWet.f("uPull", pull)
            pWet.f("uDil", dil)
            pWet.f("uAttack", attack)
            pWet.f("uBlurR", blurR)
            pWet.f("uGrade", brush.wmGrade)
            pWet.f("uOpacity", set.opacity)
            pWet.i("uAlphaLock", if (layer.alphaLock) 1 else 0)
            pWet.tex("uSel", 3, (selTex ?: t).id)
            pWet.i("uUseSel", if (selOn) 1 else 0)
            pWet.f2("uCanvas", W.toFloat(), H.toFloat())
            G.glBindVertexArray(dabVao)
            G.glBindBuffer(G.GL_ARRAY_BUFFER, dabVbo)
            dabFloats.clear()
            val a0 = d[o + 5]
            d[o + 5] = a0 * load
            writeDab(list, k)
            d[o + 5] = a0
            dabFloats.flip()
            G.glBufferData(G.GL_ARRAY_BUFFER, 6 * vtxN * 4, dabFloats, G.GL_STREAM_DRAW)
            G.glDrawArrays(G.GL_TRIANGLES, 0, 6)
            G.glBindVertexArray(0)
            if (pull > 0f) blit(layer.tex.fb, r.x0, r.y0, r.x1, r.y1, carry.fb, 0, 0, carry.w, carry.h, true)
        }
        layerChanged(layer, notify = false)
    }

    private fun renderSmudge(list: DabList, set: BrushSetting) {
        val layer = strokeLayer ?: return
        val t = smT!!
        val carry = smCarry!!
        val brush = set.brush
        val spacing = min(brush.spacing, 0.08f)
        val strength = brush.smudgeStrength.coerceIn(0.01f, 0.995f)
        val d = list.data
        val r = IRect()
        for (k in 0 until list.size) {
            val o = k * DabList.S
            val x = d[o]; val y = d[o + 1]; val s = d[o + 2]; val a = d[o + 5]
            val rad = s * 0.71f + 1f
            r.set(floor(x - rad).toInt(), floor(y - rad).toInt(), ceil(x + rad).toInt(), ceil(y + rad).toInt())
            r.clampTo(W, H)
            if (r.isEmpty || r.w > t.w || r.h > t.h) continue
            blit(layer.tex.fb, r.x0, r.y0, r.x1, r.y1, t.fb, 0, 0, r.w, r.h, false)
            if (smudgeFirst) {
                blit(layer.tex.fb, r.x0, r.y0, r.x1, r.y1, carry.fb, 0, 0, carry.w, carry.h, true)
                smudgeFirst = false
            }
            G.glBindFramebuffer(G.GL_FRAMEBUFFER, layer.tex.fb)
            G.glViewport(0, 0, W, H)
            G.glDisable(G.GL_BLEND)
            pSmudge.use()
            pSmudge.f2("uTarget", W.toFloat(), H.toFloat())
            pSmudge.f2("uOffset", 0f, 0f)
            pSmudge.tex("uTip", 0, tipTexFor(brush))
            pSmudge.tex("uCur", 1, t.id)
            pSmudge.tex("uCarry", 2, carry.id)
            pSmudge.f2("uRectMin", r.x0.toFloat(), r.y0.toFloat())
            pSmudge.f2("uRectSize", r.w.toFloat(), r.h.toFloat())
            pSmudge.f2("uTSize", t.w.toFloat(), t.h.toFloat())
            val pressure = max(0.05f, a * set.opacity)
            pSmudge.f("uStrength", strength.pow(spacing * 1.5f / pressure))
            pSmudge.i("uAlphaLock", if (layer.alphaLock) 1 else 0)
            pSmudge.tex("uSel", 3, (selTex ?: t).id)
            pSmudge.i("uUseSel", if (selOn) 1 else 0)
            pSmudge.f2("uCanvas", W.toFloat(), H.toFloat())
            G.glBindVertexArray(dabVao)
            G.glBindBuffer(G.GL_ARRAY_BUFFER, dabVbo)
            dabFloats.clear()
            writeDab(list, k)
            dabFloats.flip()
            G.glBufferData(G.GL_ARRAY_BUFFER, 6 * vtxN * 4, dabFloats, G.GL_STREAM_DRAW)
            G.glDrawArrays(G.GL_TRIANGLES, 0, 6)
            G.glBindVertexArray(0)
            blit(layer.tex.fb, r.x0, r.y0, r.x1, r.y1, carry.fb, 0, 0, carry.w, carry.h, true)
        }
        layerChanged(layer, notify = false)
    }

    // ================= pratinjau & tumpukan =================

    private fun drawRect(p: Program, r: IRect, tw: Int = W, th: Int = H) {
        p.f4("uRect", r.x0.toFloat(), r.y0.toFloat(), r.x1.toFloat(), r.y1.toFloat())
        p.f2("uTarget", tw.toFloat(), th.toFloat())
        G.glBindVertexArray(vao)
        G.glDrawArrays(G.GL_TRIANGLE_STRIP, 0, 4)
        G.glBindVertexArray(0)
    }

    private fun previewPass(
        layer: Layer, r: IRect, op: Int, opacity: Float, usePred: Boolean = false,
        useMask: Boolean = false, fill: Int = 0, hsb: FloatArray? = null, grain: Brush? = null,
        wet: BrushSetting? = null, fx: BrushSetting? = null,
        base: Tex? = null, wetFTex: Tex? = null, noStroke: Boolean = false,
    ) {
        val pv = preview!!
        G.glBindFramebuffer(G.GL_FRAMEBUFFER, pv.fb)
        G.glViewport(0, 0, W, H)
        G.glDisable(G.GL_BLEND)
        pPreview.use()
        pPreview.tex("uLayer", 0, (base ?: layer.tex).id)
        pPreview.tex("uStroke", 1, strokeBuf!!.id)
        pPreview.i("uNoStroke", if (noStroke) 1 else 0)
        pPreview.tex("uWetF", 10, (wetFTex ?: strokeBuf!!).id)
        pPreview.i("uUseF", if (wetFTex != null) 1 else 0)
        pPreview.i("uLive", if (wetFTex != null) 1 else 0)
        pPreview.tex("uPred", 2, predBuf!!.id)
        pPreview.tex("uMask", 3, (maskTex ?: strokeBuf!!).id)
        pPreview.f2("uSize", W.toFloat(), H.toFloat())
        pPreview.i("uOp", op)
        pPreview.f("uOpacity", opacity)
        pPreview.i("uAlphaLock", if (layer.alphaLock) 1 else 0)
        pPreview.i("uUsePred", if (usePred) 1 else 0)
        pPreview.i("uUseMask", if (useMask) 1 else 0)
        pPreview.f3("uFill", Color.red(fill) / 255f, Color.green(fill) / 255f, Color.blue(fill) / 255f)
        if (hsb != null) pPreview.f3("uHsb", hsb[0], hsb[1], hsb[2]) else pPreview.f3("uHsb", 0f, 0f, 0f)
        pPreview.f2("uClone", cloneDx, cloneDy)
        pPreview.tex("uSel", 4, (selTex ?: strokeBuf!!).id)
        pPreview.i("uUseSel", if (selOn) 1 else 0)
        val g = grain?.let { grainTexFor(it) }
        if (grain != null && g != null) {
            pPreview.tex("uGrain", 5, g)
            pPreview.f("uGrainStrength", grain.grainStrength)
            pPreview.f("uGrainScale", 512f * max(0.05f, grain.grainScale))
        } else {
            pPreview.tex("uGrain", 5, strokeBuf!!.id)
            pPreview.f("uGrainStrength", 0f)
            pPreview.f("uGrainScale", 512f)
        }
        pPreview.tex("uNoise", 6, noiseTex)
        pPreview.tex("uPaper", 7, paperTex)
        pPreview.f("uPaperScale", paperTexN * (doc?.paperScale ?: 1f))
        val wb = wet?.brush
        if (wb != null && wb.watercolor) {
            pPreview.i("uWet", 1)
            pPreview.f4("uWetP", wb.wcEdge, wb.wcBloom, wb.wcGrain, wb.wcSoft)
            pPreview.f("uWetDens", wb.wcDensity)
            pPreview.f2("uWetSeed", wetSeedX, wetSeedY)
            pPreview.f("uWetR", wetRadius(wet.sizePx))
            pPreview.f("uWetK", (wet.sizePx / 90f).coerceIn(0.6f, 4f))
            pPreview.i("uGlaze", if (wb.wcGlaze && op == 0) 1 else 0)
        } else {
            pPreview.i("uWet", 0)
            pPreview.i("uGlaze", 0)
        }
        // Rendering kuas (tepi basah/terbakar, intensitas, mode campur, ambang alfa) dan kuas ganda.
        val fb = fx?.brush
        val dualOn = fx != null && strokeDual != null && dualBuf != null && dualPred != null
        pPreview.i("uDual", if (dualOn) 1 else 0)
        pPreview.tex("uDualTex", 8, (if (dualOn) dualBuf!! else strokeBuf!!).id)
        pPreview.tex("uDualPred", 9, (if (dualOn) dualPred!! else predBuf!!).id)
        pPreview.i("uDualMode", fb?.dualMode ?: 0)
        val sc = strokeColor
        pPreview.f3("uDualColor", Color.red(sc) / 255f, Color.green(sc) / 255f, Color.blue(sc) / 255f)
        val rendOn = fb != null && (fb.wetEdges > 0f || fb.burntEdges > 0f || fb.render == 2 || fb.render == 3 ||
            fb.alphaThreshold > 0f || fb.blendMode != 0 || fb.lumBlend)
        pPreview.i("uRend", if (rendOn) 1 else 0)
        if (fx != null && fb != null && rendOn) {
            pPreview.f("uRendK", when (fb.render) { 2 -> 1.15f; 3 -> 1.6f; else -> 1f })
            pPreview.f("uThreshold", fb.alphaThreshold)
            pPreview.f("uWetEdge", fb.wetEdges)
            pPreview.f("uBurnt", fb.burntEdges)
            pPreview.i("uBurntMode", fb.burntMode)
            pPreview.i("uBlendMode", if (fb.lumBlend && fb.blendMode == 0) 25 else fb.blendMode)
            pPreview.f("uEdgeR", edgeRadius(fx.sizePx))
        }
        drawRect(pPreview, r)
    }

    private fun updateStrokePreview(r: IRect, usePred: Boolean) {
        val layer = strokeLayer ?: return
        val set = strokeSet ?: return
        val op = when {
            strokeTool == ToolKind.ERASE -> 1
            cloneActive -> 4
            else -> 0
        }
        previewPass(
            layer, r, op, set.opacity, usePred = usePred,
            grain = set.brush.takeIf { it.grainStroke && strokeTool != ToolKind.SMUDGE },
            wet = (if (strokeLive && wetOn) wetSet else set).takeIf { strokeWet && op != 4 },
            fx = set.takeIf { op != 4 },
            base = if (strokeLive && wetOn) wetBase else null,
            wetFTex = if (strokeLive && wetOn) wetF[wetC] else null,
        )
    }

    // ================= cat air hidup =================

    /** Jauh rembesan (piksel kanvas): makin besar kuas dan makin besar nilai rembesan, makin jauh. */
    private fun wetSpreadPx(set: BrushSetting) =
        (set.sizePx * 0.8f * set.brush.wcSpread + 4f * set.brush.wcSpread).coerceIn(2f, 200f)

    /** Mulai sesi basah di [layer]; false bila memori grafis tidak cukup (cat air biasa). */
    private fun wetBegin(layer: Layer, set: BrushSetting): Boolean {
        try {
            if (wetBase == null) wetBase = Tex(W, H)
            for (k in 0..1) {
                if (wetF[k] == null) wetF[k] = Tex(W, H)
                if (wetW[k] == null) wetW[k] = Tex(W, H, single = true)
            }
            if (wetFs == null) wetFs = Tex(W, H)
            if (wetWs == null) wetWs = Tex(W, H, single = true)
        } catch (e: OutOfMemoryError) {
            listOf(wetBase, wetF[0], wetF[1], wetW[0], wetW[1], wetFs, wetWs).forEach { it?.delete() }
            wetForget()
            post { listener.onMessage("Memori grafis tidak cukup untuk cat air yang merembes; cat langsung kering") }
            return false
        }
        blit(layer.tex.fb, 0, 0, W, H, wetBase!!.fb, 0, 0, W, H, false)
        val all = IRect(0, 0, W, H)
        for (k in 0..1) { clearRect(wetF[k]!!, all); clearRect(wetW[k]!!, all) }
        wetOn = true
        wetLayerId = layer.id
        wetSet = set
        wetC = 0
        wetStepN = 0
        wetEvapAcc = 0f
        wetLast = android.os.SystemClock.uptimeMillis()
        wetSim = 0L
        wetLastReal = wetLast
        wetPatches.clear()
        wetCur = null
        wetSeedX = (Math.random() * 256).toFloat()
        wetSeedY = (Math.random() * 256).toFloat()
        Diag.log("Cat air hidup: sesi mulai layer ${layer.id}, kuas ${set.brush.id}, rembes ${wetSpreadPx(set)} px")
        return true
    }

    /** Lupakan sesi (tekstur sudah dihapus/hilang). */
    private fun wetForget() {
        wetBase = null; wetF[0] = null; wetF[1] = null; wetW[0] = null; wetW[1] = null; wetFs = null; wetWs = null
        wetOn = false; wetPatches.clear(); wetCur = null; wetSet = null; wetLayerId = -1; strokeLive = false
    }

    /**
     * Akhiri sesi: cat yang masih basah langsung kering apa adanya. Layer selalu sudah berisi keadaan terakhir
     * (ditulis tiap bingkai), jadi tidak ada yang perlu disalin.
     */
    private fun wetEnd() {
        if (!wetOn) return
        Diag.log("Cat air hidup: sesi selesai")
        wetOn = false
        wetPatches.clear()
        wetCur = null
        liveBox.clear()
        doc?.find(wetLayerId)?.let { layerChanged(it) }
        wetLayerId = -1
        wetSet = null
    }

    /** Salin cat basah saat goresan mulai, supaya goresan yang dibatalkan bisa dihapus lagi. */
    private fun wetSnapshot() {
        blit(wetF[wetC]!!.fb, 0, 0, W, H, wetFs!!.fb, 0, 0, W, H, false)
        blit(wetW[wetC]!!.fb, 0, 0, W, H, wetWs!!.fb, 0, 0, W, H, false)
    }

    private fun wetActive(): IRect {
        val r = IRect()
        for (p in wetPatches) r.union(p.r)
        return r.clampTo(W, H)
    }

    /**
     * Lepas semua tekstur dari unit 0..10 sebelum langkah cat basah: tekstur tujuan (penyangga bolak-balik) tidak boleh
     * masih terpasang di unit mana pun, kalau tidak sebagian GPU menulis hasil kosong (umpan balik).
     */
    private fun wetUnbind() {
        for (u in 0..10) {
            G.glActiveTexture(G.GL_TEXTURE0 + u)
            G.glBindTexture(G.GL_TEXTURE_2D, 0)
        }
        G.glActiveTexture(G.GL_TEXTURE0)
    }

    private fun wetPass(p: Program, target: Tex, r: IRect) {
        G.glBindFramebuffer(G.GL_FRAMEBUFFER, target.fb)
        G.glViewport(0, 0, W, H)
        G.glDisable(G.GL_BLEND)
        drawRect(p, r)
    }

    /** Masukkan isi strokeBuf (goresan yang sedang digambar) ke cat basah; strokeBuf di situ dikosongkan. */
    private fun wetInject(set: BrushSetting, now: Long) {
        if (liveBox.isEmpty) return
        val sr = liveBox.copy().clampTo(W, H)
        liveBox.clear()
        if (sr.isEmpty) return
        val sb = strokeBuf ?: return
        val pr = sr.copy().inset(ceil(wetSpreadPx(set) * 2.5f + wetRadius(set.sizePx) * 2f).toInt() + 28).clampTo(W, H)
        val until = now + (set.brush.wcWetTime.coerceIn(0.3f, 10f) * 1000f).toLong() + 300L
        val cur = wetCur
        if (cur == null) wetCur = WetPatch(pr, until).also { wetPatches.add(it) } else { cur.r.union(pr); cur.until = until }
        val u = wetActive()
        val c = wetC
        wetUnbind()
        pWetIn.use()
        pWetIn.tex("uStroke", 0, sb.id)
        pWetIn.tex("uF", 1, wetF[c]!!.id)
        pWetIn.f2("uSize", W.toFloat(), H.toFloat())
        val sc = strokeColor
        pWetIn.f3("uColor", Color.red(sc) / 255f, Color.green(sc) / 255f, Color.blue(sc) / 255f)
        wetPass(pWetIn, wetF[1 - c]!!, u)
        wetUnbind()
        pWetInW.use()
        pWetInW.tex("uStroke", 0, sb.id)
        pWetInW.tex("uW", 1, wetW[c]!!.id)
        pWetInW.f2("uSize", W.toFloat(), H.toFloat())
        wetPass(pWetInW, wetW[1 - c]!!, u)
        wetC = 1 - c
        clearRect(sb, sr)
    }

    /** Satu langkah simulasi di wilayah [r]: cat merembes, air mengering. */
    private fun wetStep(set: BrushSetting, r: IRect, dtMs: Float) {
        val b = set.brush
        wetEvapAcc += dtMs / (b.wcWetTime.coerceIn(0.3f, 10f) * 1000f) * 255f
        val lv = floor(wetEvapAcc)
        wetEvapAcc -= lv
        val c = wetC
        wetUnbind()
        pWetSpread.use()
        pWetSpread.tex("uF", 0, wetF[c]!!.id)
        pWetSpread.tex("uW", 1, wetW[c]!!.id)
        pWetSpread.tex("uNoise", 2, noiseTex)
        pWetSpread.tex("uPaper", 3, paperTex)
        pWetSpread.f("uPaperScale", paperTexN * (doc?.paperScale ?: 1f))
        pWetSpread.f2("uSize", W.toFloat(), H.toFloat())
        pWetSpread.f("uLoss", 1f / wetSpreadPx(set))
        pWetSpread.f("uNoiseK", 10f * (set.sizePx / 90f).coerceIn(0.6f, 4f))
        pWetSpread.f2("uSeed", wetSeedX, wetSeedY)
        pWetSpread.f2("uJit", (wetRnd.nextFloat() * 256f), (wetRnd.nextFloat() * 256f))
        pWetSpread.f("uMix", 0.12f)
        pWetSpread.f4("uBound", r.x0.toFloat(), r.y0.toFloat(), r.x1.toFloat(), r.y1.toFloat())
        wetPass(pWetSpread, wetF[1 - c]!!, r)
        wetUnbind()
        pWetWater.use()
        pWetWater.tex("uW", 0, wetW[c]!!.id)
        pWetWater.tex("uFOld", 1, wetF[c]!!.id)
        pWetWater.tex("uFNew", 2, wetF[1 - c]!!.id)
        pWetWater.f2("uSize", W.toFloat(), H.toFloat())
        pWetWater.f("uEvap", lv / 255f)
        wetPass(pWetWater, wetW[1 - c]!!, r)
        wetC = 1 - c
        wetStepN++
    }

    /** Tulis dasar + cat basah (dengan olahan cat air) ke layer di wilayah [r]. */
    private fun wetComposite(layer: Layer, set: BrushSetting, r: IRect) {
        previewPass(
            layer, r, 0, set.opacity,
            grain = set.brush.takeIf { it.grainStroke }, wet = set, fx = set,
            base = wetBase, wetFTex = wetF[wetC], noStroke = true,
        )
        blit(preview!!.fb, r.x0, r.y0, r.x1, r.y1, layer.tex.fb, r.x0, r.y0, r.x1, r.y1, false)
        layerChanged(layer, notify = false)
    }

    /** Tiap bingkai selama sesi basah: masukkan goresan baru, jalankan rembesan, perbarui layer. */
    private fun wetUpdate() {
        if (!wetOn) return
        val layer = doc?.find(wetLayerId)
        val set = wetSet
        if (layer == null || set == null || modal || textOn) { wetEnd(); return }
        val now = android.os.SystemClock.uptimeMillis()
        // Langkah simulasi tetap 8 ms; bingkai lambat menjalankan paling banyak 6 langkah (simulasi melambat, tidak meloncat).
        val steps = ((now - wetLast + 7L) / 8L).toInt().coerceIn(1, 6)
        wetLast = now
        wetSim += steps * 8L
        val drawing = strokeActive && strokeLive
        if (drawing) { wetInject(set, wetSim); wetLastReal = now }
        wetPatches.removeAll { it.until < wetSim && it !== wetCur }
        // Batas aman: di perangkat yang sangat lambat cat tetap kering paling lama ~3x lama basah (waktu nyata).
        val limit = (set.brush.wcWetTime.coerceIn(0.3f, 10f) * 3000f).toLong() + 5000L
        if (!drawing && (wetPatches.isEmpty() || now - wetLastReal > limit)) { wetEnd(); return }
        val r = wetActive()
        if (!r.isEmpty) {
            repeat(steps) { wetStep(set, r, 8f) }
            // Saat menggores, layer tidak diubah (keadaannya = sebelum goresan, untuk urungkan); tampilan dari pratinjau.
            if (!drawing) wetComposite(layer, set, r)
            dirty.union(r)
        }
        requestRender()
    }

    /** Goresan cat air hidup selesai: sisa goresan masuk ke cat basah, layer diperbarui, satu langkah urungkan. */
    private fun wetCommit(layer: Layer) {
        val set = wetSet ?: return
        wetInject(set, wetSim)
        val cur = wetCur ?: return
        wetCur = null
        val er = wetActive()
        if (er.isEmpty) return
        // Layer belum diubah sejak goresan mulai: itulah keadaan "sebelum" untuk urungkan. Wilayahnya mencakup semua
        // cat yang masih basah karena cat itu terus berubah sampai kering.
        val before = readRegion(layer.tex.fb, er)
        wetComposite(layer, set, er)
        wetPushing = true
        try {
            pushEdit(Edit.Pixels(layer.id, er, RegionData(er.w, er.h, before)))
        } finally {
            wetPushing = false
        }
        dirty.union(er)
    }

    /** Goresan cat air hidup dibatalkan: cat basah dikembalikan ke keadaan saat goresan mulai. */
    private fun wetCancel() {
        val r = wetActive()
        blit(wetFs!!.fb, 0, 0, W, H, wetF[wetC]!!.fb, 0, 0, W, H, false)
        blit(wetWs!!.fb, 0, 0, W, H, wetW[wetC]!!.fb, 0, 0, W, H, false)
        wetCur?.let { wetPatches.remove(it) }
        wetCur = null
        liveBox.clear()
        dirty.union(r)
    }

    /** Tumpuk ulang semua layer di wilayah [r0] ke tekstur gabungan. */
    private fun recomposite(r0: IRect) {
        val d = doc ?: return
        val r = r0.copy().clampTo(W, H)
        if (r.isEmpty) return
        compositeInto(d, r, comp!!, scratch!!, if (d.bgVisible) d.bg else null)
        mipDirty = true
    }

    /** [float]: potongan isi pilihan yang sedang ditransformasi (null = ikuti aturan biasa). */
    private class Item(
        val tex: Tex, val layer: Layer, val clipTex: Tex?, val clipBase: Layer? = null, val float: Boolean? = null,
        /** Animation Assist: 0 latar, 1 onion skin, 2 frame utama/biasa, 3 latar depan. */
        val bucket: Int = 2, val mul: Float = 1f, val tint: Int = 0,
    )

    /**
     * [frameView]: -2 = ikuti Animation/Page Assist karya (frame aktif + onion skin), -1 = semua layer,
     * >= 0 = hanya frame itu (beserta latar/latar depan, tanpa onion skin; untuk ekspor).
     */
    private fun compositeInto(d: Doc, r: IRect, out: Tex, other: Tex, bg: Int?, frameView: Int = -2) {
        var items = ArrayList<Item>()
        var base: Layer? = null
        var baseTex: Tex? = null
        // Animation/Page Assist: peran tiap layer menurut frame-nya.
        val assistOn = d.assist != 0 && frameView != -1
        var fidx: HashMap<Int, Int>? = null
        var cur = 0
        var nFrames = 0
        if (assistOn) {
            val fr = framesOf(d)
            nFrames = fr.size
            fidx = HashMap()
            fr.forEachIndexed { i, f -> for (id in f.layers) fidx[id] = i }
            cur = if (frameView >= 0) frameView else animCurrent(d, fr)
        }
        val s = d.anim
        val onionOn = assistOn && d.assist == 1 && frameView == -2 && !animPlaying && s.onion > 0
        fun role(l: Layer): IntArray? {
            if (!assistOn) return intArrayOf(2, 0, 0)
            val f = fidx!![l.id] ?: return intArrayOf(2, 0, 0)
            if (f == cur) return intArrayOf(2, 0, 0)
            if (d.assist == 1 && nFrames > 1) {
                if (s.bgFrame && f == 0) return intArrayOf(0, 0, 0)
                if (s.fgFrame && f == nFrames - 1) return intArrayOf(3, 0, 0)
            }
            if (onionOn) {
                val dist = kotlin.math.abs(f - cur)
                if (dist <= s.onion) return intArrayOf(1, dist, if (f < cur) 1 else 2)
            }
            return null
        }
        for (l in d.layers) {
            val ro = role(l) ?: continue
            val bucket = ro[0]
            val mul = when (bucket) {
                1 -> s.onionOpacity * (1f - (ro[1] - 1f) / (s.onion + 1f))
                2 -> if (onionOn && s.blendPrimary) 0.65f else 1f
                else -> 1f
            }
            val tint = if (bucket == 1 && s.colorOnion) ro[2] else 0
            val selXf = xfOn && xfSel && l.id == xfSelId && d === doc
            val tex = when {
                selXf -> xfRest!!
                textOn && l.id == textFor && d === doc -> strokeBuf!!
                substitute != null && l.id == substituteFor -> substitute!!
                else -> l.tex
            }
            if (!l.clip) {
                base = l
                baseTex = tex
                if (d.shown(l) && l.opacity > 0f) {
                    items.add(Item(tex, l, null, float = if (selXf) false else null, bucket = bucket, mul = mul, tint = tint))
                    if (selXf) items.add(Item(if (xfWarp) preview!! else xfFloat!!, l, null, float = !xfWarp, bucket = bucket, mul = mul, tint = tint))
                }
            } else {
                val b = base
                if (b == null || !d.shown(b) || !d.shown(l) || l.opacity <= 0f) continue
                items.add(Item(tex, l, baseTex, b, float = if (selXf) false else null, bucket = bucket, mul = mul, tint = tint))
                if (selXf) items.add(Item(if (xfWarp) preview!! else xfFloat!!, l, baseTex, b, float = !xfWarp, bucket = bucket, mul = mul, tint = tint))
            }
            if (textOn && textFor < 0 && l.id == textAbove && d === doc) textDummy?.let { items.add(Item(strokeBuf!!, it, null)) }
        }
        // Latar, onion skin, frame utama, latar depan (urutan stabil di tiap kelompok).
        if (assistOn) items = ArrayList(items.sortedBy { it.bucket })
        val xf = xfOn && !xfWarp && d === doc
        val paperOn = bg != null && d.paper != Paper.NONE && d.paperStrength > 0f && paperTexKey == paperKey(d)
        val count = 1 + items.size + (if (paperOn) 1 else 0)
        var write = if (count % 2 == 1) out else other
        var read = if (write === out) other else out
        G.glDisable(G.GL_BLEND)
        G.glBindFramebuffer(G.GL_FRAMEBUFFER, write.fb)
        G.glViewport(0, 0, W, H)
        pBg.use()
        if (bg != null) {
            pBg.f4("uColor", Color.red(bg) / 255f, Color.green(bg) / 255f, Color.blue(bg) / 255f, 1f)
        } else {
            pBg.f4("uColor", 0f, 0f, 0f, 0f)
        }
        drawRect(pBg, r)
        var t = read; read = write; write = t
        pComposite.use()
        pComposite.f2("uSize", W.toFloat(), H.toFloat())
        pComposite.m3("uInv", colMajor(xfInv))
        pComposite.i("uInterp", xfInterp)
        for (it in items) {
            pComposite.i("uXf", if (it.float != null) (if (it.float) 1 else 0) else if (xf && it.layer.id in xfIds) 1 else 0)
            pComposite.i("uClipXf", if (xf && it.clipBase != null && it.clipBase.id in xfIds) 1 else 0)
            G.glBindFramebuffer(G.GL_FRAMEBUFFER, write.fb)
            pComposite.tex("uBase", 0, read.id)
            pComposite.tex("uLayer", 1, it.tex.id)
            pComposite.tex("uClip", 2, (it.clipTex ?: it.tex).id)
            pComposite.f("uOpacity", it.layer.opacity * it.mul)
            when (it.tint) {
                1 -> pComposite.f4("uTint", 0.95f, 0.25f, 0.25f, 0.65f)
                2 -> pComposite.f4("uTint", 0.2f, 0.75f, 0.35f, 0.65f)
                else -> pComposite.f4("uTint", 0f, 0f, 0f, 0f)
            }
            pComposite.i("uMode", it.layer.blend)
            pComposite.i("uUseClip", if (it.clipTex != null) 1 else 0)
            drawRect(pComposite, r)
            t = read; read = write; write = t
        }
        if (paperOn) {
            // Kertas: seluruh tumpukan (latar + cat) dikali bayangan/pola kertas, seperti cat di atas kertas bertekstur.
            G.glBindFramebuffer(G.GL_FRAMEBUFFER, write.fb)
            pPaper.use()
            pPaper.tex("uBase", 0, read.id)
            pPaper.tex("uPaper", 1, paperTex)
            pPaper.f2("uSize", W.toFloat(), H.toFloat())
            pPaper.f("uPaperScale", paperTexN * d.paperScale)
            pPaper.f("uStrength", d.paperStrength)
            drawRect(pPaper, r)
        }
    }

    // ================= teks =================

    /** Mulai mengedit teks: [layerId] = layer teks yang diedit, atau -1 untuk teks baru di atas layer aktif. */
    fun textBegin(layerId: Int) {
        val d = doc ?: return
        if (strokeActive || modal) return
        val sb = strokeBuf ?: return
        clearRect(sb, IRect(0, 0, W, H))
        textRect.clear()
        textFor = if (layerId >= 0 && d.find(layerId) != null) layerId else -1
        textAbove = d.active
        textDummy = Layer(-77, sb)
        textOn = true
        dirty.set(0, 0, W, H)
    }

    /** Ganti gambar teks pratinjau ([buf] premultiplied RGBA seukuran [r], atau null bila kosong). */
    fun textUpdate(buf: ByteBuffer?, r: IRect) {
        if (!textOn) return
        val sb = strokeBuf ?: return
        if (!textRect.isEmpty) {
            clearRect(sb, textRect)
            dirty.union(textRect)
        }
        val c = r.copy().clampTo(W, H)
        if (buf != null && !c.isEmpty && c.w == r.w && c.h == r.h) {
            writeRegion(sb, c, buf)
            textRect.set(c.x0, c.y0, c.x1, c.y1)
            dirty.union(c)
        } else {
            textRect.clear()
        }
    }

    /**
     * Simpan teks: teks baru jadi layer baru di atas layer aktif; teks yang diedit menggantikan gambar teks lama di
     * layernya (coretan lain di luar kotak teks lama tetap). Satu langkah urungkan. [oldRect] = kotak teks lama.
     */
    fun textCommit(name: String, spec: String, oldRect: IRect?) {
        val d = doc ?: return
        if (!textOn) return
        val sb = strokeBuf ?: return
        val r = textRect.copy()
        val editing = if (textFor >= 0) d.find(textFor) else null
        textOn = false
        textBox = null
        textPathDraft = null
        if (editing == null) {
            if (r.isEmpty) { dirty.set(0, 0, W, H); return }
            val l = addLayer(name) ?: run { dirty.set(0, 0, W, H); return }
            l.text = spec
            blit(sb.fb, r.x0, r.y0, r.x1, r.y1, l.tex.fb, r.x0, r.y0, r.x1, r.y1, false)
            layerChanged(l)
            postLayers()
        } else {
            val u = r.copy()
            oldRect?.let { if (!it.isEmpty) u.union(it.copy().clampTo(W, H)) }
            val edits = ArrayList<Edit>()
            if (!u.isEmpty) {
                val before = readRegion(editing.tex.fb, u)
                oldRect?.let { clearRect(editing.tex, it.copy().clampTo(W, H)) }
                if (!r.isEmpty) blit(sb.fb, r.x0, r.y0, r.x1, r.y1, editing.tex.fb, r.x0, r.y0, r.x1, r.y1, false)
                edits.add(Edit.Pixels(editing.id, u, RegionData(u.w, u.h, before)))
            }
            val old = editing.props()
            editing.text = spec
            editing.name = name
            if (editing.props() != old) edits.add(Edit.Props(editing.id, old))
            if (edits.isNotEmpty()) pushEdit(if (edits.size == 1) edits[0] else Edit.Group(edits))
            layerChanged(editing)
            postLayers()
        }
        clearRect(sb, IRect(0, 0, W, H))
        textRect.clear()
        textFor = -1
        dirty.set(0, 0, W, H)
        makeThumbs()
        post { listener.onContentChanged() }
    }

    fun textCancel() {
        if (!textOn) return
        textOn = false
        textBox = null
        textPathDraft = null
        strokeBuf?.let { clearRect(it, IRect(0, 0, W, H)) }
        textRect.clear()
        textFor = -1
        dirty.set(0, 0, W, H)
    }

    /** Bingkai teks, pegangan putar/skala (sudut kanan bawah), dan jalur bebas yang sedang digambar. */
    private fun drawTextOverlay(v: FloatArray) {
        val vm = Matrix().apply { setValues(v) }
        textPathDraft?.let { p ->
            if (p.size >= 4) {
                // Paling banyak ~200 ruas (batas penyangga garis).
                val n = p.size / 2
                val step = (n + 199) / 200
                val m = (n - 1) / step + 1
                val q = FloatArray(m * 2) { i -> p[min(n - 1, (i / 2) * step) * 2 + i % 2] }
                vm.mapPoints(q)
                val segs = FloatArray((q.size / 2 - 1) * 4)
                for (i in 0 until q.size / 2 - 1) {
                    segs[i * 4] = q[i * 2]; segs[i * 4 + 1] = q[i * 2 + 1]
                    segs[i * 4 + 2] = q[i * 2 + 2]; segs[i * 4 + 3] = q[i * 2 + 3]
                }
                lines(segs, G.GL_LINES, 2.5f, 0.18f, 0.6f, 1f)
            }
        }
        val b = textBox ?: return
        val q = b.copyOf()
        vm.mapPoints(q)
        val segs = floatArrayOf(
            q[0], q[1], q[2], q[3], q[2], q[3], q[4], q[5],
            q[4], q[5], q[6], q[7], q[6], q[7], q[0], q[1],
        )
        lines(segs, G.GL_LINES, 1.5f, 0.18f, 0.6f, 1f)
        val hr = 9f * density
        dot(q[4], q[5], hr, 0.18f, 0.6f, 1f)
    }

    // ================= kertas kanvas =================

    private fun paperKey(d: Doc): String = if (d.paper == Paper.CUSTOM) "c${d.id}:${d.paperVer}" else "p${d.paper}"

    private fun newRepeatTex(): Int {
        val a = IntArray(1)
        G.glGenTextures(1, a, 0)
        G.glBindTexture(G.GL_TEXTURE_2D, a[0])
        G.glTexParameteri(G.GL_TEXTURE_2D, G.GL_TEXTURE_MIN_FILTER, G.GL_LINEAR)
        G.glTexParameteri(G.GL_TEXTURE_2D, G.GL_TEXTURE_MAG_FILTER, G.GL_LINEAR)
        G.glTexParameteri(G.GL_TEXTURE_2D, G.GL_TEXTURE_WRAP_S, G.GL_REPEAT)
        G.glTexParameteri(G.GL_TEXTURE_2D, G.GL_TEXTURE_WRAP_T, G.GL_REPEAT)
        return a[0]
    }

    /** Siapkan ubin kertas dokumen di thread bantu, lalu unggah di frame berikutnya. */
    private fun requestPaper(d: Doc) {
        val key = paperKey(d)
        if (key == paperTexKey || key == paperPending) return
        paperPending = key
        val type = d.paper
        val dir = Storage.dir(ctx, d.id)
        worker.execute {
            val res: Pair<ByteArray, Int>? = try {
                if (type == Paper.CUSTOM) {
                    val f = File(dir, PAPER_FILE)
                    val g = if (f.exists()) runCatching { f.readBytes() }.getOrNull() else null
                    val n = g?.let { sqrt(it.size.toDouble()).toInt() } ?: 0
                    if (g != null && n >= 16 && n * n == g.size) Paper.fromGray(g, n) to n else cachedPaper(Paper.COLD) to Paper.N
                } else {
                    // Tanpa kertas: ubin cat air tetap disiapkan untuk granulasi kuas cat air.
                    cachedPaper(if (type == Paper.NONE) Paper.COLD else type) to Paper.N
                }
            } catch (e: OutOfMemoryError) {
                null
            }
            tasks.add { e -> e.uploadPaper(key, res) }
            requestRender()
        }
    }

    private fun cachedPaper(type: Int): ByteArray = synchronized(paperCache) {
        paperCache.getOrPut(type) { Paper.generate(type) }
    }

    private fun uploadPaper(key: String, res: Pair<ByteArray, Int>?) {
        if (paperPending == key) paperPending = ""
        val d = doc ?: return
        if (paperKey(d) != key || res == null) return
        val (data, n) = res
        val buf = Buffers.direct(data.size)
        buf.put(data)
        buf.position(0)
        G.glBindTexture(G.GL_TEXTURE_2D, paperTex)
        G.glPixelStorei(G.GL_UNPACK_ALIGNMENT, 1)
        G.glTexImage2D(G.GL_TEXTURE_2D, 0, G.GL_RGBA8, n, n, 0, G.GL_RGBA, G.GL_UNSIGNED_BYTE, buf)
        paperTexN = n
        paperTexKey = key
        dirty.set(0, 0, W, H)
        mipDirty = true
    }

    /** Ganti tekstur kertas kanvas (bukan langkah urungkan; tersimpan bersama karya). */
    fun setPaper(type: Int, strength: Float, scale: Float) {
        val d = doc ?: return
        d.paper = type.coerceIn(0, Paper.CUSTOM)
        d.paperStrength = strength.coerceIn(0f, 1f)
        d.paperScale = scale.coerceIn(0.25f, 4f)
        requestPaper(d)
        dirty.set(0, 0, W, H)
        post { listener.onContentChanged() }
    }

    /** Kertas dari gambar sendiri (abu-abu [n]×[n]); disimpan di folder karya. */
    fun setCustomPaper(gray: ByteArray, n: Int, strength: Float, scale: Float) {
        val d = doc ?: return
        val dir = Storage.dir(ctx, d.id)
        runCatching { File(dir, PAPER_FILE).writeBytes(gray) }
        d.paperVer++
        setPaper(Paper.CUSTOM, strength, scale)
    }

    private fun wetRadius(size: Float) = (size * 0.035f).coerceIn(1.5f, 7f)

    private fun drawScreen(d: Doc) {
        val r = r3d
        if (view3D && d.mesh != null && r != null && r.hasMesh) {
            draw3D(r)
            return
        }
        G.glBindFramebuffer(G.GL_FRAMEBUFFER, 0)
        G.glViewport(0, 0, screenW, screenH)
        G.glDisable(G.GL_BLEND)
        G.glClearColor(0.16f, 0.16f, 0.18f, 1f)
        G.glClear(G.GL_COLOR_BUFFER_BIT)
        val v = view
        val zoom = sqrt(v[0] * v[0] + v[3] * v[3])
        val c = comp ?: return
        G.glBindTexture(G.GL_TEXTURE_2D, c.id)
        if (zoom < 0.6f) {
            if (mipDirty) {
                G.glGenerateMipmap(G.GL_TEXTURE_2D)
                mipDirty = false
            }
            G.glTexParameteri(G.GL_TEXTURE_2D, G.GL_TEXTURE_MIN_FILTER, G.GL_LINEAR_MIPMAP_LINEAR)
        } else {
            G.glTexParameteri(G.GL_TEXTURE_2D, G.GL_TEXTURE_MIN_FILTER, G.GL_LINEAR)
        }
        pScreen.use()
        pScreen.f4("uRect", 0f, 0f, W.toFloat(), H.toFloat())
        pScreen.m3("uView", colMajor(v))
        pScreen.f2("uScreen", screenW.toFloat(), screenH.toFloat())
        pScreen.tex("uComp", 0, c.id)
        pScreen.f2("uSize", W.toFloat(), H.toFloat())
        pScreen.i("uChecker", if (d.bgVisible) 0 else 1)
        pScreen.f("uCheckScale", 10f * density / max(zoom, 0.01f))
        pScreen.tex("uSel", 1, (selTex ?: c).id)
        pScreen.i("uUseSel", if (selOn && !(xfOn && xfSel)) 1 else 0)
        // Warna layar: proof CMYK dan/atau ubah ruang RGB karya ke ruang permukaan layar.
        val cmyk = d.profile == ColorProfile.CMYK && ensureLut()
        val space = if (cmyk) ColorProfile.SRGB else ColorProfile.rgbSpace(d.profile)
        val mat = ColorProfile.matrixGl(space, if (surfaceP3) ColorProfile.P3 else ColorProfile.SRGB)
        pScreen.i("uCm", (if (mat != null) 1 else 0) + (if (cmyk) 2 else 0))
        if (mat != null) pScreen.m3("uCmMat", mat)
        pScreen.i("uLut", 5)
        if (cmyk) {
            G.glActiveTexture(G.GL_TEXTURE5)
            G.glBindTexture(G.GL_TEXTURE_3D, proofTex)
        }
        G.glActiveTexture(G.GL_TEXTURE0)
        G.glBindVertexArray(vao)
        G.glDrawArrays(G.GL_TRIANGLE_STRIP, 0, 4)
        G.glBindVertexArray(0)
        if (cmyk) {
            G.glActiveTexture(G.GL_TEXTURE5)
            G.glBindTexture(G.GL_TEXTURE_3D, 0)
            G.glActiveTexture(G.GL_TEXTURE0)
        }

        drawGuide(v)
        if (textOn) drawTextOverlay(v)
        if (xfOn) drawXfOverlay(v)
        selDraft?.let { drawSelDraft(v, it, selDraftClosed) }

        val h = hover
        val ts = tool
        if (h != null && ts != null && !strokeActive && !modal) {
            ring(h[0], h[1], max(3f * density, ts.current().sizePx * zoom * 0.5f))
        }
        // Liquify: lingkaran ukuran sapuan di bawah pena/jari.
        if (h != null && adjOn && adjKind == Adj.LIQUIFY) ring(h[0], h[1], max(3f * density, liqParams[1] * zoom * 0.5f))
        if (h != null && adjOn && penOn) ring(h[0], h[1], max(3f * density, (ts?.current()?.sizePx ?: penSize) * zoom * 0.5f))
        val mk = if (adjOn) marker else null
        if (mk != null) {
            val sx = v[0] * mk[0] + v[1] * mk[1] + v[2]
            val sy = v[3] * mk[0] + v[4] * mk[1] + v[5]
            ring(sx, sy, 14f * density)
            ring(sx, sy, 3f * density)
        }
        val cm = cloneMarker
        if (cloneOn && cm != null) {
            val sx = v[0] * cm[0] + v[1] * cm[1] + v[2]
            val sy = v[3] * cm[0] + v[4] * cm[1] + v[5]
            ring(sx, sy, max(16f * density, (ts?.paint?.sizePx ?: 0f) * zoom * 0.5f))
            ring(sx, sy, 2.5f * density)
        }
    }

    /** Lingkaran tipis di layar (piksel layar). */
    private fun ring(x: Float, y: Float, rad: Float) {
        val pad = 4f * density
        G.glEnable(G.GL_BLEND)
        G.glBlendFunc(G.GL_ONE, G.GL_ONE_MINUS_SRC_ALPHA)
        pRing.use()
        pRing.f4("uRect", x - rad - pad, y - rad - pad, x + rad + pad, y + rad + pad)
        pRing.f2("uScreen", screenW.toFloat(), screenH.toFloat())
        pRing.f2("uCenter", x, y)
        pRing.f("uRadius", rad)
        pRing.f("uPx", density)
        G.glBindVertexArray(vao)
        G.glDrawArrays(G.GL_TRIANGLE_STRIP, 0, 4)
        G.glBindVertexArray(0)
        G.glDisable(G.GL_BLEND)
    }

    /** Kotak transformasi, pegangan, kisi Warp, dan garis tempel (Snapping) di layar. */
    private fun drawXfOverlay(v: FloatArray) {
        val f = xfDraw
        val q = f?.quad?.copyOf() ?: floatArrayOf(
            xfOrig.x0.toFloat(), xfOrig.y0.toFloat(), xfOrig.x1.toFloat(), xfOrig.y0.toFloat(),
            xfOrig.x1.toFloat(), xfOrig.y1.toFloat(), xfOrig.x0.toFloat(), xfOrig.y1.toFloat(),
        )
        val vm = Matrix().apply { setValues(v) }
        vm.mapPoints(q)
        val warpMode = f != null && f.mode == 3
        // Garis tempel.
        if (f != null) {
            if (!f.guideX.isNaN()) {
                val g = floatArrayOf(f.guideX, 0f, f.guideX, H.toFloat())
                vm.mapPoints(g)
                lines(g, G.GL_LINES, 1.5f, 1f, 0.55f, 0.2f)
            }
            if (!f.guideY.isNaN()) {
                val g = floatArrayOf(0f, f.guideY, W.toFloat(), f.guideY)
                vm.mapPoints(g)
                lines(g, G.GL_LINES, 1.5f, 1f, 0.55f, 0.2f)
            }
        }
        // Kisi Warp.
        f?.curves?.forEach { c ->
            val p = c.copyOf()
            vm.mapPoints(p)
            lines(p, G.GL_LINE_STRIP, 1.2f, 0.3f, 0.75f, 1f)
        }
        if (!warpMode) {
            lines(q, G.GL_LINE_LOOP, 3f, 0.1f, 0.1f, 0.12f)
            lines(q, G.GL_LINE_LOOP, 1.5f, 0.3f, 0.75f, 1f)
        }
        if (f == null) return
        val hr = 7f * density
        if (warpMode) {
            val p = f.netPts?.copyOf() ?: return
            vm.mapPoints(p)
            for (k in 0 until 16) dot(p[2 * k], p[2 * k + 1], hr * 0.85f, 0.25f, 0.6f, 1f)
            return
        }
        for (k in 0..3) dot(q[2 * k], q[2 * k + 1], hr, 0.25f, 0.6f, 1f)
        for (k in 0..3) {
            val j = (k + 1) % 4
            dot((q[2 * k] + q[2 * j]) / 2f, (q[2 * k + 1] + q[2 * j + 1]) / 2f, hr * 0.8f, 0.25f, 0.6f, 1f)
        }
        // Pegangan putar di luar tengah tepi atas.
        val mx = (q[0] + q[2]) / 2f
        val my = (q[1] + q[3]) / 2f
        val cx = (q[0] + q[2] + q[4] + q[6]) / 4f
        val cy = (q[1] + q[3] + q[5] + q[7]) / 4f
        var nx = mx - cx
        var ny = my - cy
        val len = hypot(nx, ny)
        if (len < 1e-3f) { nx = 0f; ny = -1f } else { nx /= len; ny /= len }
        val off = 30f * density
        val rx = mx + nx * off
        val ry = my + ny * off
        lines(floatArrayOf(mx, my, rx, ry), G.GL_LINES, 1.5f, 0.3f, 0.75f, 1f)
        dot(rx, ry, hr, 0.2f, 0.8f, 0.35f)
    }

    /** Titik pegangan di layar. */
    private fun dot(x: Float, y: Float, rad: Float, r: Float, g: Float, b: Float) {
        val pad = 4f * density
        G.glEnable(G.GL_BLEND)
        G.glBlendFunc(G.GL_ONE, G.GL_ONE_MINUS_SRC_ALPHA)
        pDot.use()
        pDot.f4("uRect", x - rad - pad, y - rad - pad, x + rad + pad, y + rad + pad)
        pDot.f2("uScreen", screenW.toFloat(), screenH.toFloat())
        pDot.f2("uCenter", x, y)
        pDot.f("uRadius", rad)
        pDot.f("uPx", density)
        pDot.f4("uColor", r, g, b, 1f)
        G.glBindVertexArray(vao)
        G.glDrawArrays(G.GL_TRIANGLE_STRIP, 0, 4)
        G.glBindVertexArray(0)
        G.glDisable(G.GL_BLEND)
    }

    /** Garis di layar (piksel layar). */
    private fun lines(pts: FloatArray, prim: Int, width: Float, r: Float, g: Float, b: Float) {
        val n = min(pts.size, 1024) / 2
        if (n < 2) return
        lineFloats.clear()
        lineFloats.put(pts, 0, n * 2)
        lineFloats.flip()
        G.glBindVertexArray(lineVao)
        G.glBindBuffer(G.GL_ARRAY_BUFFER, lineVbo)
        G.glBufferData(G.GL_ARRAY_BUFFER, n * 2 * 4, lineFloats, G.GL_STREAM_DRAW)
        pLine.use()
        pLine.f2("uScreen", screenW.toFloat(), screenH.toFloat())
        G.glLineWidth(width * density)
        pLine.f4("uColor", r, g, b, 1f)
        G.glDrawArrays(prim, 0, n)
        G.glBindVertexArray(0)
    }

    // ================= utilitas piksel =================

    private fun blit(
        src: Int, sx0: Int, sy0: Int, sx1: Int, sy1: Int,
        dst: Int, dx0: Int, dy0: Int, dx1: Int, dy1: Int, linear: Boolean,
    ) {
        G.glDisable(G.GL_SCISSOR_TEST)
        G.glBindFramebuffer(G.GL_READ_FRAMEBUFFER, src)
        G.glBindFramebuffer(G.GL_DRAW_FRAMEBUFFER, dst)
        G.glBlitFramebuffer(sx0, sy0, sx1, sy1, dx0, dy0, dx1, dy1, G.GL_COLOR_BUFFER_BIT, if (linear) G.GL_LINEAR else G.GL_NEAREST)
        G.glBindFramebuffer(G.GL_FRAMEBUFFER, 0)
    }

    private fun clearRect(t: Tex, r: IRect) {
        val c = r.copy().clampTo(t.w, t.h)
        if (c.isEmpty) return
        G.glBindFramebuffer(G.GL_FRAMEBUFFER, t.fb)
        G.glViewport(0, 0, t.w, t.h)
        G.glEnable(G.GL_SCISSOR_TEST)
        G.glScissor(c.x0, c.y0, c.w, c.h)
        G.glClearColor(0f, 0f, 0f, 0f)
        G.glClear(G.GL_COLOR_BUFFER_BIT)
        G.glDisable(G.GL_SCISSOR_TEST)
    }

    private fun readRegion(fb: Int, r: IRect): ByteBuffer {
        val buf = Buffers.direct(r.w * r.h * 4)
        G.glBindFramebuffer(G.GL_READ_FRAMEBUFFER, fb)
        G.glPixelStorei(G.GL_PACK_ALIGNMENT, 4)
        G.glReadPixels(r.x0, r.y0, r.w, r.h, G.GL_RGBA, G.GL_UNSIGNED_BYTE, buf)
        G.glBindFramebuffer(G.GL_FRAMEBUFFER, 0)
        buf.position(0)
        return buf
    }

    private fun writeRegion(t: Tex, r: IRect, buf: ByteBuffer) {
        buf.position(0)
        G.glBindTexture(G.GL_TEXTURE_2D, t.id)
        G.glPixelStorei(G.GL_UNPACK_ALIGNMENT, 4)
        G.glTexSubImage2D(G.GL_TEXTURE_2D, 0, r.x0, r.y0, r.w, r.h, G.GL_RGBA, G.GL_UNSIGNED_BYTE, buf)
    }

    private fun toBitmap(buf: ByteBuffer, w: Int, h: Int): Bitmap {
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        buf.position(0)
        bmp.copyPixelsFromBuffer(buf)
        return bmp
    }

    /** Perkecil tekstur menjadi Bitmap (sisi terpanjang [maxDim]). */
    private fun downscale(srcFb: Int, w: Int, h: Int, maxDim: Int): Bitmap {
        val s = min(1f, maxDim.toFloat() / max(w, h))
        val tw = max(1, (w * s).toInt())
        val th = max(1, (h * s).toInt())
        val mw = max(tw, w / 4)
        val mh = max(th, h / 4)
        val mid = Tex(mw, mh)
        val small = Tex(tw, th)
        blit(srcFb, 0, 0, w, h, mid.fb, 0, 0, mw, mh, true)
        blit(mid.fb, 0, 0, mw, mh, small.fb, 0, 0, tw, th, true)
        val buf = readRegion(small.fb, IRect(0, 0, tw, th))
        mid.delete()
        small.delete()
        return toBitmap(buf, tw, th)
    }

    // ================= riwayat =================

    private fun pushEdit(e: Edit) {
        // Perubahan lain pada karya: cat air yang masih basah langsung kering (berhenti merembes).
        if (wetOn && !wetPushing) wetEnd()
        tlDirty = true
        redoStack.clear()
        undoStack.addLast(e)
        managePacking()
        trimUndo()
        ensureHeap(heapReserve)
        postLayers()
    }

    /** Padatkan data riwayat di latar belakang; bila memori habis, data dibiarkan apa adanya (tidak menutup aplikasi). */
    private fun pack(rd: RegionData) {
        if (rd.queued) return
        rd.queued = true
        packer.execute {
            try { rd.packQueued() } catch (e: OutOfMemoryError) { lowMemory() }
        }
    }

    /**
     * Langkah riwayat yang paling dekat posisi sekarang (puncak urungkan dan ulangi) dibiarkan mentah supaya
     * urungkan/ulangi terasa seketika (tanpa membongkar data); yang lebih jauh dipadatkan di latar belakang.
     */
    private val rawBudget: Long get() = min(Runtime.getRuntime().maxMemory() / 10, 96L shl 20)

    private fun managePacking() {
        val budget = rawBudget
        var used = 0L
        val u = undoStack.size
        val r = redoStack.size
        for (i in 0 until max(u, r)) {
            for (e in listOfNotNull(if (i < u) undoStack[u - 1 - i] else null, if (i < r) redoStack[r - 1 - i] else null)) {
                e.forEachData { rd ->
                    val raw = rd.rawBytes()
                    if (raw > 0) {
                        if (used + raw <= budget) used += raw else pack(rd)
                    }
                }
            }
        }
    }

    /** Sisa memori Java yang selalu dijaga untuk kerja berat (ColorDrop, pilihan otomatis, transformasi pilihan). */
    private val heapReserve: Long get() = W.toLong() * H * 12

    /**
     * (thread GL) Pastikan sisa memori Java cukup untuk [need] byte. Bila kurang, riwayat urungkan/ulangi yang
     * paling lama dibuang lebih dulu (langkah terbaru tetap ada), supaya kerja berat di kanvas besar tidak membuat
     * aplikasi tertutup sendiri karena memori habis.
     */
    private fun ensureHeap(need: Long): Boolean {
        val rt = Runtime.getRuntime()
        fun avail() = rt.maxMemory() - (rt.totalMemory() - rt.freeMemory())
        if (avail() >= need) return true
        rt.gc()
        val a = avail()
        if (a >= need) return true
        var freed = 0L
        var dropped = 0
        while (a + freed < need && redoStack.isNotEmpty()) { freed += redoStack.removeFirst().bytes(); dropped++ }
        while (a + freed < need && undoStack.size > 1) { freed += undoStack.removeFirst().bytes(); dropped++ }
        if (dropped == 0) return false
        rt.gc()
        postLayers()
        val n = dropped
        post { listener.onMessage("Memori hampir penuh: $n langkah urungkan terlama dihapus") }
        return avail() >= need
    }

    /** (thread mana pun) Memori habis di thread bantu: kosongkan riwayat lama di thread GL, beri tahu pengguna. */
    private fun lowMemory() {
        tasks.add { e -> e.ensureHeap(e.W.toLong() * e.H * 24) }
        requestRender()
        post { listener.onMessage("Memori penuh, langkah tadi dibatalkan. Riwayat urungkan lama dihapus; coba lagi.") }
    }

    /** Kerja di thread bantu; bila memori habis, aplikasi tidak tertutup (lihat [lowMemory]). */
    private fun work(block: () -> Unit) {
        worker.execute {
            try { block() } catch (e: OutOfMemoryError) { lowMemory() }
        }
    }

    private fun trimUndo() {
        // Data mentah langkah terbaru (lihat managePacking) punya jatah sendiri, tidak mengurangi jumlah langkah.
        var raw = 0L
        for (e in undoStack) e.forEachData { raw += it.rawBytes() }
        var total = undoStack.sumOf { it.bytes() } - min(raw, rawBudget)
        while (undoStack.size > 1 && (undoStack.size > 250 || total > undoBudget)) {
            val e = undoStack.removeFirst()
            total -= e.bytes()
        }
    }

    /** Urungkan/ulangi yang datang saat goresan belum selesai diproses: dijalankan begitu goresan tersimpan. */
    private val pendingHistory = ArrayDeque<Boolean>()

    fun undo() {
        if (modal) return
        if (strokeActive) { pendingHistory.addLast(false); return }
        wetEnd()
        val e = undoStack.removeLastOrNull()
        if (e == null) {
            post { listener.onHistory(false, null) }
            return
        }
        swap(e, forward = false)
        redoStack.addLast(e)
        afterHistory(e)
        val label = e.label()
        post { listener.onHistory(false, label) }
    }

    fun redo() {
        if (modal) return
        if (strokeActive) { pendingHistory.addLast(true); return }
        wetEnd()
        val e = redoStack.removeLastOrNull()
        if (e == null) {
            post { listener.onHistory(true, null) }
            return
        }
        swap(e, forward = true)
        undoStack.addLast(e)
        afterHistory(e)
        val label = e.label()
        post { listener.onHistory(true, label) }
    }

    private fun afterHistory(e: Edit) {
        val d = doc ?: return
        if (d.find(d.active) == null && d.layers.isNotEmpty()) d.active = d.layers.last().id
        // Hanya wilayah yang berubah yang ditumpuk ulang (goresan kecil = sangat cepat).
        val a = e.area()
        if (a != null && !a.isEmpty) dirty.union(a.clampTo(W, H)) else dirty.set(0, 0, W, H)
        managePacking()
        postLayers()
        post { listener.onContentChanged() }
        thumbsSoon()
    }

    private var thumbSerial = 0

    /** Gambar mini layer dibuat sebentar setelah urungkan/ulangi berhenti (tidak memperlambat urungkan beruntun). */
    private fun thumbsSoon() {
        val s = ++thumbSerial
        main.postDelayed({
            tasks.add { e -> if (e.thumbSerial == s) e.makeThumbs() }
            requestRender()
        }, 280L)
    }

    private fun swap(e: Edit, forward: Boolean) {
        val d = doc ?: return
        when (e) {
            is Edit.Pixels -> {
                val l = d.find(e.layerId) ?: return
                val cur = readRegion(l.tex.fb, e.rect)
                val old = e.data.take()
                writeRegion(l.tex, e.rect, old)
                e.data.put(cur)
                layerChanged(l, notify = false)
            }
            is Edit.Props -> {
                val l = d.find(e.layerId) ?: return
                val cur = l.props()
                l.apply(e.props)
                e.props = cur
            }
            is Edit.Exist -> swapExist(e)
            is Edit.Move -> {
                val idx = d.indexOf(e.layerId)
                if (idx < 0) return
                val l = d.layers.removeAt(idx)
                val to = e.otherIndex.coerceIn(0, d.layers.size)
                d.layers.add(to, l)
                e.otherIndex = idx
            }
            is Edit.GroupMeta -> {
                val cur = d.groups[e.groupId]
                val next = e.data
                if (next == null) d.groups.remove(e.groupId) else d.groups[e.groupId] = next
                e.data = cur
            }
            is Edit.Order -> {
                val cur = d.layers.map { it.id }
                val byId = d.layers.associateBy { it.id }
                val nl = e.ids.mapNotNull { byId[it] } + d.layers.filter { it.id !in e.ids }
                d.layers.clear()
                d.layers.addAll(nl)
                e.ids = cur
            }
            is Edit.Bg -> {
                val c = d.bg
                val v = d.bgVisible
                d.bg = e.color
                d.bgVisible = e.visible
                e.color = c
                e.visible = v
            }
            is Edit.Group -> {
                if (forward) e.list.forEach { swap(it, true) } else e.list.asReversed().forEach { swap(it, false) }
            }
        }
    }

    /** Tambah atau hapus layer sesuai keadaan sekarang (lihat [Edit.Exist]). */
    private fun swapExist(e: Edit.Exist) {
        val d = doc ?: return
        val l = d.find(e.layerId)
        if (l != null) {
            val idx = d.indexOf(l.id)
            val buf = readRegion(l.tex.fb, IRect(0, 0, W, H))
            e.snapshot = LayerSnapshot(l.props(), RegionData(W, H, buf))
            e.index = idx
            d.layers.removeAt(idx)
            l.tex.delete()
            if (d.active == l.id && d.layers.isNotEmpty()) d.active = d.layers[(idx - 1).coerceIn(0, d.layers.size - 1)].id
        } else {
            val snap = e.snapshot ?: return
            val nl = Layer(e.layerId, Tex(W, H))
            nl.apply(snap.props)
            writeRegion(nl.tex, IRect(0, 0, W, H), snap.data.take())
            d.layers.add(e.index.coerceIn(0, d.layers.size), nl)
            d.active = nl.id
            e.snapshot = null
            nl.saveDirty = true
            nl.thumbDirty = true
        }
        dirty.set(0, 0, W, H)
    }

    // ================= layer =================

    private fun layerChanged(l: Layer, notify: Boolean = true) {
        tlDirty = true
        l.saveDirty = true
        l.thumbDirty = true
        if (notify) post { listener.onContentChanged() }
    }

    fun postLayers() {
        val d = doc ?: return
        val snap = LayersSnapshot(
            layers = d.layers.map { it.info() },
            activeId = d.active,
            canUndo = undoStack.isNotEmpty(),
            canRedo = redoStack.isNotEmpty(),
            limit = layerLimit,
            bg = d.bg,
            bgVisible = d.bgVisible,
            groups = LinkedHashMap(d.groups),
        )
        post { listener.onLayers(snap) }
    }

    private fun structChanged() {
        dirty.set(0, 0, W, H)
        postLayers()
        post { listener.onContentChanged() }
    }

    private fun newLayerTex(): Tex? {
        val d = doc ?: return null
        if (d.layers.size >= layerLimit) {
            post { listener.onMessage("Batas layer tercapai ($layerLimit layer untuk ukuran kanvas ini)") }
            return null
        }
        return try {
            Tex(W, H)
        } catch (e: OutOfMemoryError) {
            post { listener.onMessage("Memori grafis penuh, layer tidak bisa ditambah") }
            null
        }
    }

    /**
     * Nama urut untuk layer/grup baru: nomornya = jumlah yang ada + 1 (sama dengan hitungan di panel), dinaikkan bila
     * nama itu sudah dipakai. Jadi menambah layer berturut-turut menghasilkan Layer 2, Layer 3, Layer 4, dst.
     */
    private fun nextName(used: List<String>, base: String): String {
        val taken = used.toHashSet()
        var n = used.size + 1
        while ("$base $n" in taken) n++
        return "$base $n"
    }

    fun addLayer(name: String? = null): Layer? {
        val d = doc ?: return null
        if (modal || strokeActive) return null
        val tex = newLayerTex() ?: return null
        val l = Layer(d.nextId++, tex)
        l.name = name ?: nextName(d.layers.map { it.name }, "Layer")
        l.group = d.activeLayer()?.group ?: 0
        val idx = (d.indexOf(d.active) + 1).coerceIn(0, d.layers.size)
        d.layers.add(idx, l)
        d.active = l.id
        pushEdit(Edit.Exist(l.id, idx, null))
        structChanged()
        return l
    }

    fun importLayer(buf: ByteBuffer, name: String) {
        val l = addLayer(name) ?: return
        writeRegion(l.tex, IRect(0, 0, W, H), buf)
        layerChanged(l)
        dirty.set(0, 0, W, H)
        makeThumbs()
    }

    fun duplicateLayer(id: Int) {
        val d = doc ?: return
        if (modal || strokeActive) return
        val src = d.find(id) ?: return
        val tex = newLayerTex() ?: return
        val l = Layer(d.nextId++, tex)
        l.apply(src.props())
        l.name = src.name + " salinan"
        l.locked = false
        blit(src.tex.fb, 0, 0, W, H, tex.fb, 0, 0, W, H, false)
        val idx = d.indexOf(id) + 1
        d.layers.add(idx, l)
        d.active = l.id
        pushEdit(Edit.Exist(l.id, idx, null))
        structChanged()
        makeThumbs()
    }

    fun deleteLayer(id: Int) {
        val d = doc ?: return
        if (modal || strokeActive) return
        if (d.layers.size <= 1) {
            post { listener.onMessage("Kanvas perlu minimal satu layer") }
            return
        }
        val idx = d.indexOf(id)
        if (idx < 0) return
        val e = Edit.Exist(id, idx, null)
        swapExist(e)
        val edits = arrayListOf<Edit>(e)
        dropEmptyGroups(d, edits)
        pushEdit(if (edits.size == 1) e else Edit.Group(edits))
        structChanged()
    }

    fun mergeDown(id: Int) {
        val d = doc ?: return
        if (modal || strokeActive) return
        val idx = d.indexOf(id)
        if (idx <= 0) {
            post { listener.onMessage("Tidak ada layer di bawahnya") }
            return
        }
        val upper = d.layers[idx]
        val lower = d.layers[idx - 1]
        if (lower.locked) {
            post { listener.onMessage("Layer di bawahnya terkunci") }
            return
        }
        val full = IRect(0, 0, W, H)
        val before = readRegion(lower.tex.fb, full)
        val sc = scratch!!
        blit(lower.tex.fb, 0, 0, W, H, sc.fb, 0, 0, W, H, false)
        G.glBindFramebuffer(G.GL_FRAMEBUFFER, lower.tex.fb)
        G.glViewport(0, 0, W, H)
        G.glDisable(G.GL_BLEND)
        pComposite.use()
        pComposite.f2("uSize", W.toFloat(), H.toFloat())
        pComposite.tex("uBase", 0, sc.id)
        pComposite.tex("uLayer", 1, upper.tex.id)
        pComposite.tex("uClip", 2, sc.id)
        pComposite.f("uOpacity", if (d.shown(upper)) upper.opacity else 0f)
        pComposite.f4("uTint", 0f, 0f, 0f, 0f)
        pComposite.i("uMode", upper.blend)
        pComposite.i("uUseClip", if (upper.clip) 1 else 0)
        drawRect(pComposite, full)
        val ex = Edit.Exist(upper.id, idx, null)
        swapExist(ex)
        d.active = lower.id
        layerChanged(lower)
        val edits = arrayListOf<Edit>(Edit.Pixels(lower.id, full, RegionData(W, H, before)), ex)
        if (lower.text != null) {
            // Hasil gabungan bukan teks murni lagi: jadi layer gambar biasa.
            edits.add(Edit.Props(lower.id, lower.props()))
            lower.text = null
        }
        dropEmptyGroups(d, edits)
        pushEdit(Edit.Group(edits))
        structChanged()
        makeThumbs()
    }

    fun clearLayer(id: Int) {
        val d = doc ?: return
        if (modal || strokeActive) return
        val l = d.find(id) ?: return
        if (l.locked) { post { listener.onMessage("Layer ini terkunci") }; return }
        val full = IRect(0, 0, W, H)
        val before = readRegion(l.tex.fb, full)
        clearRect(l.tex, full)
        pushEdit(Edit.Pixels(l.id, full, RegionData(W, H, before)))
        layerChanged(l)
        dirty.set(0, 0, W, H)
        makeThumbs()
    }

    /** Isi layer dengan warna. Bila alpha lock aktif, hanya bagian yang sudah berisi yang diwarnai. */
    fun fillWholeLayer(id: Int, color: Int) {
        val d = doc ?: return
        if (modal || strokeActive) return
        val l = d.find(id) ?: return
        if (l.locked) { post { listener.onMessage("Layer ini terkunci") }; return }
        val full = IRect(0, 0, W, H)
        val before = readRegion(l.tex.fb, full)
        previewPass(l, full, 2, 1f, useMask = false, fill = color)
        blit(preview!!.fb, 0, 0, W, H, l.tex.fb, 0, 0, W, H, false)
        pushEdit(Edit.Pixels(l.id, full, RegionData(W, H, before)))
        layerChanged(l)
        dirty.set(0, 0, W, H)
        makeThumbs()
    }

    fun selectLayer(id: Int) {
        val d = doc ?: return
        if (strokeActive || modal) return
        if (d.find(id) != null) d.active = id
        postLayers()
    }

    fun setProps(id: Int, p: LayerProps, undoable: Boolean) {
        val d = doc ?: return
        val l = d.find(id) ?: return
        val old = l.props()
        l.apply(p)
        if (undoable && old != p) pushEdit(Edit.Props(id, old)) else postLayers()
        dirty.set(0, 0, W, H)
        post { listener.onContentChanged() }
    }

    /** Catat perubahan sifat yang sudah diterapkan bertahap (mis. menggeser opasitas). */
    fun commitProps(id: Int, start: LayerProps) {
        val d = doc ?: return
        val l = d.find(id) ?: return
        if (l.props() != start) pushEdit(Edit.Props(id, start))
    }

    fun moveLayer(id: Int, to: Int) {
        val d = doc ?: return
        if (strokeActive || modal) return
        val idx = d.indexOf(id)
        if (idx < 0) return
        val target = to.coerceIn(0, d.layers.size - 1)
        if (target == idx) return
        val l = d.layers.removeAt(idx)
        d.layers.add(target, l)
        pushEdit(Edit.Move(id, idx))
        structChanged()
    }

    // ================= grup layer =================

    /** Satuan urutan dari bawah ke atas: satu layer lepas, atau satu blok grup (anggotanya bersebelahan). */
    private fun units(d: Doc): List<IntRange> {
        val out = ArrayList<IntRange>()
        var i = 0
        while (i < d.layers.size) {
            val g = d.layers[i].group
            var j = i
            if (g != 0) while (j + 1 < d.layers.size && d.layers[j + 1].group == g) j++
            out.add(i..j)
            i = j + 1
        }
        return out
    }

    private fun reorder(d: Doc, ids: List<Int>, edits: MutableList<Edit>) {
        val cur = d.layers.map { it.id }
        if (cur == ids) return
        val byId = d.layers.associateBy { it.id }
        val nl = ids.mapNotNull { byId[it] }
        if (nl.size != d.layers.size) return
        d.layers.clear()
        d.layers.addAll(nl)
        edits.add(Edit.Order(cur))
    }

    private fun setGroupOf(l: Layer, g: Int, edits: MutableList<Edit>) {
        if (l.group == g) return
        val old = l.props()
        l.group = g
        edits.add(Edit.Props(l.id, old))
    }

    private fun putMeta(d: Doc, gid: Int, data: GroupData?, edits: MutableList<Edit>) {
        val old = d.groups[gid]
        if (old == data) return
        if (data == null) d.groups.remove(gid) else d.groups[gid] = data
        edits.add(Edit.GroupMeta(gid, old))
    }

    /** Grup yang tidak punya anggota lagi dihapus. */
    private fun dropEmptyGroups(d: Doc, edits: MutableList<Edit>) {
        val used = d.layers.mapTo(HashSet()) { it.group }
        for (gid in d.groups.keys.toList()) if (gid !in used) putMeta(d, gid, null, edits)
    }

    private fun commitEdits(edits: List<Edit>) {
        if (edits.isEmpty()) { postLayers(); return }
        pushEdit(if (edits.size == 1) edits[0] else Edit.Group(edits))
        structChanged()
    }

    /** Berkas lama/rusak: anggota grup tak dikenal dilepas, anggota yang terpisah dari bloknya dilepas. */
    private fun sanitizeGroups(d: Doc) {
        val done = HashSet<Int>()
        var prev = 0
        for (l in d.layers) {
            val g = l.group
            if (g != 0 && (g !in d.groups || (g != prev && g in done))) l.group = 0
            if (l.group != 0) done.add(l.group)
            prev = l.group
        }
        val used = d.layers.mapTo(HashSet()) { it.group }
        d.groups.keys.retainAll(used)
    }

    /** Kelompokkan layer-layer [ids] menjadi satu grup baru, diletakkan di tempat layer teratas yang dipilih. */
    fun groupLayers(ids: List<Int>) {
        val d = doc ?: return
        if (modal || strokeActive) return
        val sel = d.layers.filter { it.id in ids }
        if (sel.isEmpty()) return
        val edits = ArrayList<Edit>()
        val rest = d.layers.filter { it !in sel }
        val topIdx = d.layers.indexOf(sel.last())
        var at = (topIdx - (sel.size - 1)).coerceIn(0, rest.size)
        // Jangan menyisip di tengah blok grup lain: geser ke atas blok itu.
        while (at in 1 until rest.size && rest[at - 1].group != 0 && rest[at - 1].group == rest[at].group) at++
        val order = rest.subList(0, at).map { it.id } + sel.map { it.id } + rest.subList(at, rest.size).map { it.id }
        reorder(d, order, edits)
        val gid = d.nextId++
        for (l in sel) setGroupOf(l, gid, edits)
        putMeta(d, gid, GroupData(nextName(d.groups.values.map { it.name }, "Grup"), visible = true, collapsed = false), edits)
        dropEmptyGroups(d, edits)
        commitEdits(edits)
    }

    /** Bubarkan grup; layernya tetap di tempat. */
    fun ungroup(gid: Int) {
        val d = doc ?: return
        if (modal || strokeActive) return
        val edits = ArrayList<Edit>()
        for (l in d.layers) if (l.group == gid) setGroupOf(l, 0, edits)
        putMeta(d, gid, null, edits)
        commitEdits(edits)
    }

    fun renameGroup(gid: Int, name: String) {
        val d = doc ?: return
        val g = d.groups[gid] ?: return
        val edits = ArrayList<Edit>()
        putMeta(d, gid, g.copy(name = name), edits)
        commitEdits(edits)
    }

    fun setGroupVisible(gid: Int, visible: Boolean) {
        val d = doc ?: return
        val g = d.groups[gid] ?: return
        val edits = ArrayList<Edit>()
        putMeta(d, gid, g.copy(visible = visible), edits)
        commitEdits(edits)
    }

    /** Lipat/buka grup di panel (tidak masuk riwayat urungkan). */
    fun setGroupCollapsed(gid: Int, collapsed: Boolean) {
        val d = doc ?: return
        val g = d.groups[gid] ?: return
        d.groups[gid] = g.copy(collapsed = collapsed)
        postLayers()
        post { listener.onContentChanged() }
    }

    /** Pindahkan seluruh grup satu langkah ke atas/bawah (melewati satu layer atau satu grup lain). */
    fun moveGroup(gid: Int, up: Boolean) {
        val d = doc ?: return
        if (modal || strokeActive) return
        val u = units(d)
        val k = u.indexOfFirst { d.layers[it.first].group == gid }
        val j = if (up) k + 1 else k - 1
        if (k < 0 || j !in u.indices) return
        val blocks = u.map { r -> r.map { d.layers[it].id } }.toMutableList()
        val t = blocks[k]; blocks[k] = blocks[j]; blocks[j] = t
        val edits = ArrayList<Edit>()
        reorder(d, blocks.flatten(), edits)
        commitEdits(edits)
    }

    /**
     * Naikkan/turunkan satu layer dengan memperhatikan grup: di dalam grup bertukar dengan anggota lain, di ujung
     * grup keluar dari grup; layer lepas yang bertemu grup terbuka masuk ke grup itu, grup terlipat dilompati.
     */
    fun moveLayerStep(id: Int, up: Boolean) {
        val d = doc ?: return
        if (modal || strokeActive) return
        val idx = d.indexOf(id)
        if (idx < 0) return
        val l = d.layers[idx]
        val edits = ArrayList<Edit>()
        val ids = d.layers.map { it.id }.toMutableList()
        if (l.group != 0) {
            val nb = if (up) idx + 1 else idx - 1
            if (nb in d.layers.indices && d.layers[nb].group == l.group) {
                ids[idx] = ids[nb]; ids[nb] = id
                reorder(d, ids, edits)
            } else {
                setGroupOf(l, 0, edits)
                dropEmptyGroups(d, edits)
            }
        } else {
            val u = units(d)
            val k = u.indexOfFirst { idx in it }
            val j = if (up) k + 1 else k - 1
            if (j !in u.indices) return
            val ng = d.layers[u[j].first].group
            if (ng != 0 && d.groups[ng]?.collapsed != true) {
                setGroupOf(l, ng, edits)
            } else {
                val blocks = u.map { r -> r.map { d.layers[it].id } }.toMutableList()
                val t = blocks[k]; blocks[k] = blocks[j]; blocks[j] = t
                reorder(d, blocks.flatten(), edits)
            }
        }
        commitEdits(edits)
    }

    /** Anggota setiap grup harus tetap bersebelahan pada urutan [order] dengan keanggotaan [groupOf]. */
    private fun contiguous(order: List<Int>, groupOf: (Int) -> Int): Boolean {
        val seen = HashSet<Int>()
        var prev = 0
        for (id in order) {
            val g = groupOf(id)
            if (g != 0 && g != prev && g in seen) return false
            if (g != 0) seen.add(g)
            prev = g
        }
        return true
    }

    /**
     * Seret-lepas: pindahkan layer [id] tepat di atas/bawah layer [anchorId] dan jadikan anggota grup [group]
     * (0 = lepas). Bila hasilnya memecah grup lain, tidak dilakukan.
     */
    fun placeLayer(id: Int, anchorId: Int, above: Boolean, group: Int) {
        val d = doc ?: return
        if (modal || strokeActive) return
        val l = d.find(id) ?: return
        if (group != 0 && group !in d.groups) return
        val ids = d.layers.map { it.id }.toMutableList()
        if (anchorId != id) {
            if (d.find(anchorId) == null) return
            ids.remove(id)
            val ai = ids.indexOf(anchorId)
            ids.add(if (above) ai + 1 else ai, id)
        }
        if (!contiguous(ids) { x -> if (x == id) group else d.find(x)?.group ?: 0 }) {
            postLayers()
            return
        }
        val edits = ArrayList<Edit>()
        reorder(d, ids, edits)
        setGroupOf(l, group, edits)
        dropEmptyGroups(d, edits)
        commitEdits(edits)
    }

    /**
     * Seret-lepas beberapa layer bersama: semuanya (urutannya tetap) diletakkan sebagai satu blok tepat di atas/bawah
     * layer [anchorId] dan masuk grup [group]. Grup yang seluruh isinya ikut dipilih tetap utuh bila jatuh di luar grup.
     */
    fun placeLayers(ids: List<Int>, anchorId: Int, above: Boolean, group: Int) {
        val d = doc ?: return
        if (modal || strokeActive) return
        val sel = d.layers.filter { it.id in ids }
        if (sel.isEmpty()) return
        if (group != 0 && group !in d.groups) return
        val ai = d.indexOf(anchorId)
        if (ai < 0) return
        val rest = d.layers.filter { it !in sel }
        val pos = rest.count { d.indexOf(it.id) < ai || (above && it.id == anchorId) }
        val order = rest.subList(0, pos).map { it.id } + sel.map { it.id } + rest.subList(pos, rest.size).map { it.id }
        val selIds = sel.mapTo(HashSet()) { it.id }
        fun whole(g: Int) = g != 0 && d.layers.filter { it.group == g }.all { it.id in selIds }
        val newGroup = HashMap<Int, Int>()
        // Grup yang seluruh isinya ikut dipilih tetap utuh (hanya saat memindah beberapa layer sekaligus).
        for (l in sel) newGroup[l.id] = if (group == 0 && sel.size > 1 && whole(l.group)) l.group else group
        if (!contiguous(order) { x -> newGroup[x] ?: d.find(x)?.group ?: 0 }) {
            postLayers()
            return
        }
        val edits = ArrayList<Edit>()
        reorder(d, order, edits)
        for (l in sel) setGroupOf(l, newGroup[l.id] ?: 0, edits)
        dropEmptyGroups(d, edits)
        commitEdits(edits)
    }

    /** Beberapa layer dilepas di atas layer lepas [targetId]: semuanya jadi satu grup baru bersama layer itu. */
    fun groupWithMany(ids: List<Int>, targetId: Int) {
        val d = doc ?: return
        if (modal || strokeActive) return
        val t = d.find(targetId) ?: return
        if (t.group != 0) { placeLayers(ids, targetId, true, t.group); return }
        val sel = d.layers.filter { it.id in ids && it.id != targetId }
        if (sel.isEmpty()) return
        val rest = d.layers.filter { it !in sel }
        val pos = rest.indexOf(t) + 1
        val order = rest.subList(0, pos).map { it.id } + sel.map { it.id } + rest.subList(pos, rest.size).map { it.id }
        val gid = d.nextId++
        val members = sel.mapTo(HashSet()) { it.id }.apply { add(targetId) }
        if (!contiguous(order) { x -> if (x in members) gid else d.find(x)?.group ?: 0 }) {
            postLayers()
            return
        }
        val edits = ArrayList<Edit>()
        reorder(d, order, edits)
        setGroupOf(t, gid, edits)
        for (l in sel) setGroupOf(l, gid, edits)
        putMeta(d, gid, GroupData(nextName(d.groups.values.map { it.name }, "Grup"), visible = true, collapsed = false), edits)
        dropEmptyGroups(d, edits)
        commitEdits(edits)
    }

    /** Seret layer [id] ke atas layer lepas [targetId]: keduanya jadi grup baru ([id] di atas). */
    fun groupWith(id: Int, targetId: Int) {
        val d = doc ?: return
        if (modal || strokeActive || id == targetId) return
        val l = d.find(id) ?: return
        val t = d.find(targetId) ?: return
        if (t.group != 0) { placeLayer(id, targetId, true, t.group); return }
        val ids = d.layers.map { it.id }.toMutableList()
        ids.remove(id)
        ids.add(ids.indexOf(targetId) + 1, id)
        val gid = d.nextId++
        if (!contiguous(ids) { x -> if (x == id || x == targetId) gid else d.find(x)?.group ?: 0 }) {
            postLayers()
            return
        }
        val edits = ArrayList<Edit>()
        reorder(d, ids, edits)
        setGroupOf(t, gid, edits)
        setGroupOf(l, gid, edits)
        putMeta(d, gid, GroupData(nextName(d.groups.values.map { it.name }, "Grup"), visible = true, collapsed = false), edits)
        dropEmptyGroups(d, edits)
        commitEdits(edits)
    }

    /** Seret-lepas grup: seluruh blok grup [gid] diletakkan tepat di atas/bawah layer [anchorId]. */
    fun placeGroup(gid: Int, anchorId: Int, above: Boolean) {
        val d = doc ?: return
        if (modal || strokeActive) return
        val a = d.find(anchorId) ?: return
        if (a.group == gid) { postLayers(); return }
        val block = d.layers.filter { it.group == gid }.map { it.id }
        if (block.isEmpty()) return
        val ids = d.layers.map { it.id }.filter { it !in block }.toMutableList()
        val ai = ids.indexOf(anchorId)
        ids.addAll(if (above) ai + 1 else ai, block)
        if (!contiguous(ids) { x -> d.find(x)?.group ?: 0 }) {
            postLayers()
            return
        }
        val edits = ArrayList<Edit>()
        reorder(d, ids, edits)
        commitEdits(edits)
    }

    /** Keluarkan layer dari grupnya; diletakkan tepat di atas grup. */
    fun leaveGroup(id: Int) {
        val d = doc ?: return
        if (modal || strokeActive) return
        val l = d.find(id) ?: return
        val g = l.group
        if (g == 0) return
        val edits = ArrayList<Edit>()
        val top = d.layers.indexOfLast { it.group == g }
        val ids = d.layers.map { it.id }.toMutableList()
        ids.remove(id)
        ids.add(top, id)
        reorder(d, ids, edits)
        setGroupOf(l, 0, edits)
        dropEmptyGroups(d, edits)
        commitEdits(edits)
    }

    /** Gabungkan semua layer grup yang tampak menjadi satu layer bernama grup itu. */
    fun flattenGroup(gid: Int) {
        val d = doc ?: return
        if (modal || strokeActive) return
        val members = d.layers.filter { it.group == gid }
        if (members.isEmpty()) return
        val base = members.first()
        if (members.any { it.locked }) { post { listener.onMessage("Ada layer terkunci di grup ini") }; return }
        val meta = d.groups[gid] ?: GroupData("Grup", true, false)
        val full = IRect(0, 0, W, H)
        // Anggota grup ditumpuk ke tekstur sementara (latar transparan), dengan opasitas, mode campur, dan clipping masing-masing.
        val tmp = Doc(d.id, d.name, d.w, d.h)
        tmp.layers.addAll(members)
        val sub = substitute
        substitute = null
        compositeInto(tmp, full, preview!!, scratch!!, null)
        substitute = sub
        tmp.layers.clear()
        val edits = ArrayList<Edit>()
        val before = readRegion(base.tex.fb, full)
        blit(preview!!.fb, 0, 0, W, H, base.tex.fb, 0, 0, W, H, false)
        edits.add(Edit.Pixels(base.id, full, RegionData(W, H, before)))
        val old = base.props()
        base.apply(LayerProps(meta.name, true, 1f, 0, false, false, false, 0))
        edits.add(Edit.Props(base.id, old))
        for (m in members.drop(1)) {
            val e = Edit.Exist(m.id, d.indexOf(m.id), null)
            swapExist(e)
            edits.add(e)
        }
        putMeta(d, gid, null, edits)
        d.active = base.id
        layerChanged(base)
        commitEdits(edits)
        makeThumbs()
    }

    /** Hapus grup beserta semua layernya. */
    fun deleteGroup(gid: Int) {
        val d = doc ?: return
        if (modal || strokeActive) return
        val members = d.layers.filter { it.group == gid }
        if (members.size >= d.layers.size) {
            post { listener.onMessage("Kanvas perlu minimal satu layer di luar grup ini") }
            return
        }
        val edits = ArrayList<Edit>()
        for (m in members.asReversed()) {
            val e = Edit.Exist(m.id, d.indexOf(m.id), null)
            swapExist(e)
            edits.add(e)
        }
        putMeta(d, gid, null, edits)
        commitEdits(edits)
    }

    /** Duplikat grup beserta isinya, diletakkan tepat di atas grup asal. */
    fun duplicateGroup(gid: Int) {
        val d = doc ?: return
        if (modal || strokeActive) return
        val members = d.layers.filter { it.group == gid }
        if (members.isEmpty()) return
        if (d.layers.size + members.size > layerLimit) {
            post { listener.onMessage("Batas layer tercapai ($layerLimit layer untuk ukuran kanvas ini)") }
            return
        }
        val meta = d.groups[gid] ?: GroupData("Grup", true, false)
        val ng = d.nextId++
        val edits = ArrayList<Edit>()
        var at = d.layers.indexOfLast { it.group == gid } + 1
        for (m in members) {
            val tex = newLayerTex() ?: break
            val l = Layer(d.nextId++, tex)
            l.apply(m.props())
            l.group = ng
            l.locked = false
            blit(m.tex.fb, 0, 0, W, H, tex.fb, 0, 0, W, H, false)
            d.layers.add(at, l)
            edits.add(Edit.Exist(l.id, at, null))
            at++
        }
        putMeta(d, ng, meta.copy(name = meta.name + " salinan"), edits)
        commitEdits(edits)
        makeThumbs()
    }

    fun setBackground(color: Int, visible: Boolean) {
        val d = doc ?: return
        if (d.bg == color && d.bgVisible == visible) return
        pushEdit(Edit.Bg(d.bg, d.bgVisible))
        d.bg = color
        d.bgVisible = visible
        structChanged()
    }

    /** Pratinjau warna/tampilan latar tanpa langkah urungkan (selama panel Latar dibuka). */
    fun bgPreview(color: Int, visible: Boolean) {
        val d = doc ?: return
        if (d.bg == color && d.bgVisible == visible) return
        d.bg = color
        d.bgVisible = visible
        structChanged()
    }

    /** Selesai mengatur latar: perubahan dari keadaan awal ([color0], [visible0]) jadi satu langkah urungkan. */
    fun bgCommit(color0: Int, visible0: Boolean) {
        val d = doc ?: return
        if (d.bg == color0 && d.bgVisible == visible0) return
        pushEdit(Edit.Bg(color0, visible0))
    }

    /** Ubah DPI dokumen (ukuran piksel tetap); tersimpan bersama karya. */
    private var actLast = 0L

    // ---- time-lapse ----
    private var tl: TimeLapse? = null
    private var tlTex: Tex? = null
    private var tlFit: Tex? = null
    private var tlDirty = false
    private var tlLast = 0L
    private var tlOn = true

    /** Rekam satu bingkai time-lapse bila kanvas berubah (paling sering tiap 120 ms; saat cat air basah 300 ms). */
    private fun tlMaybeCapture() {
        if (!tlDirty || !tlOn || strokeActive || modal || textOn) return
        val d = doc ?: return
        val c = comp ?: return
        val now = android.os.SystemClock.uptimeMillis()
        val gap = if (wetOn) 300L else 120L
        if (now - tlLast < gap) { requestRender(); return }
        val rec = tl ?: TimeLapse(Storage.dir(ctx, d.id), W, H).also { tl = it }
        if (rec.busy()) { requestRender(); return }
        tlDirty = false
        tlLast = now
        try {
            val vw = rec.vw
            val vh = rec.vh
            val t = tlTex?.takeIf { it.w == vw && it.h == vh } ?: Tex(vw, vh).also { tlTex?.delete(); tlTex = it }
            clearRect(t, IRect(0, 0, vw, vh))
            val s = min(vw.toFloat() / W, vh.toFloat() / H)
            val dw = max(1, (W * s).roundToInt()).coerceAtMost(vw)
            val dh = max(1, (H * s).roundToInt()).coerceAtMost(vh)
            val f = tlFit?.takeIf { it.w == dw && it.h == dh } ?: Tex(dw, dh).also { tlFit?.delete(); tlFit = it }
            scaleInto(c, W, H, f, dw, dh)
            val ox = (vw - dw) / 2
            val oy = (vh - dh) / 2
            blit(f.fb, 0, 0, dw, dh, t.fb, ox, oy, ox + dw, oy + dh, false)
            val buf = rec.buffer()
            buf.position(0)
            G.glBindFramebuffer(G.GL_READ_FRAMEBUFFER, t.fb)
            G.glPixelStorei(G.GL_PACK_ALIGNMENT, 4)
            G.glReadPixels(0, 0, vw, vh, G.GL_RGBA, G.GL_UNSIGNED_BYTE, buf)
            G.glBindFramebuffer(G.GL_FRAMEBUFFER, 0)
            buf.position(0)
            rec.submit(buf)
        } catch (e: OutOfMemoryError) {
            Diag.log("Time-lapse: memori grafis tidak cukup, perekaman dimatikan")
            tlOn = false
        }
    }

    fun setTimeLapse(on: Boolean) {
        tlOn = on
        if (!on) tl?.finishSegment() else tlDirty = true
        post { listener.onTimeLapse(on) }
        post { listener.onContentChanged() }
    }

    /** Semua bingkai yang antre sudah tersimpan: [cb] menerima folder karya (di thread antarmuka). */
    fun timeLapseFlush(cb: (java.io.File) -> Unit) {
        val d = doc ?: return
        val dir = Storage.dir(ctx, d.id)
        val rec = tl
        if (rec == null) post { cb(dir) } else rec.afterPending { post { cb(dir) } }
    }

    /** Hapus rekaman time-lapse karya ini (perekaman berlanjut dari awal). */
    fun clearTimeLapse(done: () -> Unit) {
        val d = doc ?: return
        val dir = Storage.dir(ctx, d.id)
        val rec = tl
        tl = null
        if (rec == null) {
            Storage.io.execute { TimeLapse.clear(dir); post(done) }
        } else {
            rec.shutdown { TimeLapse.clear(dir); post(done) }
        }
    }

    /** Hitung waktu mengerjakan karya: jeda antar-sentuhan sampai satu menit ikut dihitung. */
    private fun noteActivity() {
        val d = doc ?: return
        val now = android.os.SystemClock.uptimeMillis()
        if (actLast != 0L) {
            val dt = now - actLast
            if (dt in 1L..60_000L) d.timeMs += dt
        }
        actLast = now
    }

    /** Info kanvas untuk panel Info. */
    fun canvasInfo(cb: (CanvasInfo) -> Unit) {
        val d = doc ?: return
        val dir = Storage.dir(ctx, d.id)
        val size = dir.listFiles()?.sumOf { it.length() } ?: 0L
        val info = CanvasInfo(
            w = W, h = H, dpi = d.dpi, layers = d.layers.size, limit = layerLimit,
            groups = d.groups.size, created = d.created, timeMs = d.timeMs, strokes = d.strokes, bytes = size,
        )
        post { cb(info) }
    }

    /** Gambar gabungan kanvas yang diperkecil (untuk jendela pangkas dan jendela referensi). */
    fun snapshot(maxDim: Int, cb: (Bitmap) -> Unit) {
        val c = comp ?: return
        val bmp = downscale(c.fb, W, H, maxDim)
        post { cb(bmp) }
    }

    /** Salin [src] ke [dst] dengan skala; pengecilan besar dilakukan bertahap (setengah-setengah) supaya tidak bergerigi. */
    private fun scaleInto(src: Tex, sw: Int, sh: Int, dst: Tex, dw: Int, dh: Int) {
        var cur = src
        var cw = sw
        var ch = sh
        val tmp = ArrayList<Tex>()
        try {
            while (cw >= dw * 2 || ch >= dh * 2) {
                val nw2 = if (cw >= dw * 2) cw / 2 else cw
                val nh2 = if (ch >= dh * 2) ch / 2 else ch
                val t = Tex(nw2, nh2)
                blit(cur.fb, 0, 0, cw, ch, t.fb, 0, 0, nw2, nh2, true)
                tmp.add(t)
                cur = t; cw = nw2; ch = nh2
            }
            blit(cur.fb, 0, 0, cw, ch, dst.fb, 0, 0, dw, dh, true)
        } finally {
            tmp.forEach { it.delete() }
        }
    }

    /**
     * Pangkas/perluas atau ubah skala kanvas. [scale] = isi ikut diskalakan ke ukuran baru; selain itu ukuran kanvas
     * berubah dan sudut kiri-atas kanvas lama diletakkan di (ox, oy) kanvas baru (negatif = terpotong).
     * Riwayat urungkan dikosongkan (tiap layer sudah berganti ukuran).
     */
    fun resizeCanvas(nw: Int, nh: Int, ox: Int, oy: Int, scale: Boolean, done: (Boolean) -> Unit) {
        val d = doc ?: return
        fun fail(m: String) { post { listener.onMessage(m); done(false) } }
        if (strokeActive || modal || textOn) { fail("Selesaikan dulu mode yang sedang aktif"); return }
        val maxTex = IntArray(1)
        G.glGetIntegerv(G.GL_MAX_TEXTURE_SIZE, maxTex, 0)
        if (nw < 16 || nh < 16 || nw > maxTex[0] || nh > maxTex[0]) { fail("Ukuran harus antara 16 dan ${maxTex[0]} piksel"); return }
        if (nw == W && nh == H && (scale || (ox == 0 && oy == 0))) { post { done(true) }; return }
        val am = ctx.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val mi = ActivityManager.MemoryInfo()
        am.getMemoryInfo(mi)
        val per = nw.toLong() * nh * 4
        val limit = (((mi.totalMem * 0.22).toLong() - per * 7) / per).toInt()
        if (d.layers.size > max(3, limit)) {
            fail("Karya punya ${d.layers.size} layer; di ukuran ${nw}×$nh paling banyak ${max(3, limit)} layer")
            return
        }
        val t0 = android.os.SystemClock.uptimeMillis()
        wetEnd()
        // Simpan dulu: bila memori grafis ternyata tidak cukup di tengah jalan, karya dimuat ulang dari simpanan ini.
        save(null)
        try {
            resizeLayers(d, nw, nh, ox, oy, scale, limit, t0, done)
        } catch (e: OutOfMemoryError) {
            Diag.log("Ubah ukuran kanvas gagal: ${e.message}")
            post { listener.onMessage("Memori grafis tidak cukup untuk ukuran ${nw}×$nh; karya dibuka lagi seperti semula"); done(false) }
            openDoc(d.id)
        }
    }

    private fun resizeLayers(d: Doc, nw: Int, nh: Int, ox: Int, oy: Int, scale: Boolean, limit: Int, t0: Long, done: (Boolean) -> Unit) {
        // Penyangga seukuran kanvas lama dilepas dulu supaya memori cukup untuk layer baru.
        listOf(comp, scratch, strokeBuf, predBuf, preview, backup, maskTex, dualBuf, dualPred).forEach { it?.delete() }
        comp = null; scratch = null; strokeBuf = null; predBuf = null; preview = null; backup = null; maskTex = null
        dualBuf = null; dualPred = null
        listOf(wetBase, wetF[0], wetF[1], wetW[0], wetW[1], wetFs, wetWs).forEach { it?.delete() }
        wetForget()
        liqFree(); cloneOn = false; cloneMarker = null; marker = null
        pyrFree(); penFree()
        selTex?.delete(); selTex = null; selOn = false; selDraft = null
        xfFloat?.delete(); xfFloat = null; xfRest?.delete(); xfRest = null; xfSel = false
        worker.execute { selMask = null; selBase = null; selSaved.clear(); selBefore = null; selAutoSrc = null }
        post { listener.onSelectionChanged(false, 0) }
        undoStack.clear(); redoStack.clear()
        substitute = null
        val ow = W
        val oh = H
        for (l in d.layers) {
            val t = Tex(nw, nh)
            clearRect(t, IRect(0, 0, nw, nh))
            if (scale) {
                scaleInto(l.tex, ow, oh, t, nw, nh)
            } else {
                val dx0 = max(0, ox); val dy0 = max(0, oy)
                val dx1 = min(nw, ox + ow); val dy1 = min(nh, oy + oh)
                if (dx1 > dx0 && dy1 > dy0) blit(l.tex.fb, dx0 - ox, dy0 - oy, dx1 - ox, dy1 - oy, t.fb, dx0, dy0, dx1, dy1, false)
            }
            l.tex.delete()
            l.tex = t
            l.saveDirty = true
            l.thumbDirty = true
        }
        val nd = Doc(d.id, d.name, nw, nh)
        nd.bg = d.bg; nd.bgVisible = d.bgVisible; nd.dpi = d.dpi
        nd.paper = d.paper; nd.paperStrength = d.paperStrength; nd.paperScale = d.paperScale; nd.paperVer = d.paperVer
        nd.nextId = d.nextId; nd.created = d.created; nd.timeMs = d.timeMs; nd.strokes = d.strokes
        nd.assist = d.assist; nd.anim = d.anim; nd.holds.putAll(d.holds); nd.profile = d.profile; nd.mesh = d.mesh; nd.face = d.face
        nd.layers.addAll(d.layers)
        nd.groups.putAll(d.groups)
        nd.active = d.active
        W = nw
        H = nh
        comp = Tex(nw, nh)
        scratch = Tex(nw, nh)
        strokeBuf = Tex(nw, nh)
        predBuf = Tex(nw, nh)
        preview = Tex(nw, nh)
        layerLimit = limit.coerceIn(3, 120)
        // Panduan menggambar ikut bergeser/diskalakan.
        val g = guide
        val sx = nw.toFloat() / ow
        val sy = nh.toFloat() / oh
        guide = if (scale) {
            g.copy(
                cx = if (g.cx.isNaN()) g.cx else g.cx * sx, cy = if (g.cy.isNaN()) g.cy else g.cy * sy,
                gridSize = (g.gridSize * (sx + sy) / 2f).coerceAtLeast(DrawingGuide.MIN_GRID),
                vps = g.vps.mapIndexed { i, v -> if (i % 2 == 0) v * sx else v * sy },
            )
        } else {
            g.copy(
                cx = if (g.cx.isNaN()) g.cx else g.cx + ox, cy = if (g.cy.isNaN()) g.cy else g.cy + oy,
                vps = g.vps.mapIndexed { i, v -> if (i % 2 == 0) v + ox else v + oy },
            )
        }
        doc = nd
        dirty.set(0, 0, nw, nh)
        mipDirty = true
        Diag.log("Kanvas diubah ${ow}x$oh -> ${nw}x$nh (${if (scale) "skala" else "pangkas $ox,$oy"}, ${android.os.SystemClock.uptimeMillis() - t0} ms)")
        val gg = guide
        val gOn = guideOn
        post {
            listener.onDocOpened(nd.id, nd.name, nw, nh)
            listener.onGuide(gOn, gg)
            listener.onContentChanged()
            done(true)
        }
        postLayers()
        makeThumbs()
        save(null)
    }

    // ================= Melukis model 3D =================

    private fun draw3D(r: Renderer3D) {
        val c = comp ?: return
        G.glBindTexture(G.GL_TEXTURE_2D, c.id)
        if (mipDirty) {
            G.glGenerateMipmap(G.GL_TEXTURE_2D)
            mipDirty = false
        }
        G.glTexParameteri(G.GL_TEXTURE_2D, G.GL_TEXTURE_MIN_FILTER, G.GL_LINEAR_MIPMAP_LINEAR)
        G.glBindTexture(G.GL_TEXTURE_2D, 0)
        val cam = cam3D
        r.draw(cam, screenW, screenH, c.id)
        // Penyangga pilih dibuat saat kamera sudah diam sebentar (bukan tiap bingkai saat diputar).
        if (r.pickKey != r.pickKeyFor(cam, screenW, screenH)) {
            val idle = android.os.SystemClock.uptimeMillis() - camAt
            if (idle >= 120) r.renderPick(cam, screenW, screenH) else main.postDelayed({ requestRender() }, 140L - idle)
        }
    }

    private fun ensurePick(r: Renderer3D) {
        val cam = cam3D
        if (r.pickKey != r.pickKeyFor(cam, screenW, screenH)) r.renderPick(cam, screenW, screenH)
    }

    // Pemetaan goresan layar → kanvas di tampilan 3D.
    private var m3Drop = true
    private var m3X = 0f
    private var m3Y = 0f
    private var m3Sx = 0f
    private var m3Sy = 0f

    private fun uvToCanvas(uv: FloatArray): FloatArray = floatArrayOf(uv[0] * W, (1f - uv[1]) * H)

    private fun at(r: RawPoint, kind: Int, x: Float, y: Float) =
        RawPoint(kind, x, y, r.p, r.tilt, r.az, r.t, r.flags and RawPoint.SCREEN.inv())

    /**
     * Titik layar → titik kanvas lewat UV model. Goresan yang keluar dari model berhenti, masuk lagi = goresan baru;
     * lompatan UV besar (melewati sambungan UV) memutus goresan supaya tidak menarik garis melintasi tekstur.
     */
    private fun map3D(raw: RawPoint): List<RawPoint> {
        val r = r3d
        val d = doc
        if (r == null || d?.mesh == null || !view3D) return if (raw.kind == RawPoint.CANCEL) listOf(raw) else emptyList()
        if (raw.kind == RawPoint.CANCEL || raw.kind == RawPoint.SHAPE || raw.kind == RawPoint.CONSTRAIN) {
            if (raw.kind == RawPoint.CANCEL) m3Drop = true
            return listOf(raw)
        }
        ensurePick(r)
        val uv = r.uvAt(raw.x, raw.y)?.let { uvToCanvas(it) }
        val out = ArrayList<RawPoint>(2)
        when (raw.kind) {
            RawPoint.BEGIN -> if (uv != null) {
                m3Drop = false
                out.add(at(raw, RawPoint.BEGIN, uv[0], uv[1]))
                Diag.log("3D: goresan di model (${uv[0].toInt()}, ${uv[1].toInt()})")
            } else {
                m3Drop = true
                Diag.log("3D: sentuhan di luar model")
            }
            RawPoint.MOVE -> when {
                m3Drop -> if (uv != null) {
                    m3Drop = false
                    out.add(at(raw, RawPoint.BEGIN, uv[0], uv[1]))
                }
                uv == null -> {
                    out.add(at(raw, RawPoint.END, m3X, m3Y))
                    m3Drop = true
                }
                else -> {
                    // Kerapatan tekstur di sekitar titik: berapa piksel kanvas per piksel layar.
                    val nb = r.uvAt(raw.x + 4f, raw.y)?.let { uvToCanvas(it) }
                    val dens = if (nb != null) hypot(nb[0] - uv[0], nb[1] - uv[1]) / 4f else 0f
                    val screenStep = hypot(raw.x - m3Sx, raw.y - m3Sy)
                    val jump = hypot(uv[0] - m3X, uv[1] - m3Y)
                    if (jump > 24f + 4f * dens * (screenStep + 2f) && jump > max(W, H) * 0.02f) {
                        out.add(at(raw, RawPoint.END, m3X, m3Y))
                        out.add(at(raw, RawPoint.BEGIN, uv[0], uv[1]))
                    } else {
                        out.add(at(raw, RawPoint.MOVE, uv[0], uv[1]))
                    }
                }
            }
            RawPoint.END -> {
                if (!m3Drop) out.add(at(raw, RawPoint.END, uv?.get(0) ?: m3X, uv?.get(1) ?: m3Y))
                m3Drop = true
            }
        }
        if (uv != null) { m3X = uv[0]; m3Y = uv[1] }
        m3Sx = raw.x; m3Sy = raw.y
        return out
    }

    /** Titik layar mengenai model (dari thread antarmuka; memakai penyangga pilih terakhir). */
    fun hit3D(sx: Float, sy: Float): Boolean {
        val r = r3d ?: return false
        if (r.pickKey != r.pickKeyFor(cam3D, screenW, screenH)) return true
        return r.uvAt(sx, sy) != null
    }

    /** Titik kanvas di bawah titik layar tampilan 3D, null bila di luar model. */
    fun canvasAt3D(sx: Float, sy: Float): FloatArray? {
        val uv = r3d?.uvAt(sx, sy) ?: return null
        return floatArrayOf(uv[0] * W, (1f - uv[1]) * H)
    }

    fun switchView3D(on: Boolean) {
        val d = doc ?: return
        if (strokeActive) commitStroke()
        view3D = on && d.mesh != null
        dirty.set(0, 0, W, H)
        mipDirty = true
        val tris = d.mesh?.triangles ?: 0
        val v = view3D
        post { listener.onModel(tris, v) }
        requestRender()
    }

    fun model(): Mesh? = doc?.mesh

    // ================= Animation Assist / Page Assist =================

    private var animPlaying = false
    private var animStart = 0L
    private var animSeq: List<Int> = emptyList()
    private var animShow = -1
    private var animLastCur = -1

    private fun framesOf(d: Doc): List<AnimFrame> =
        Frames.build(d.layers.map { it.id }, d.layers.map { it.group }, d.layers.map { it.clip })

    /** Frame yang tampil: saat diputar = frame urutan putar, selain itu frame layer aktif. */
    private fun animCurrent(d: Doc, fr: List<AnimFrame>): Int {
        if (animPlaying && animShow in fr.indices) return animShow
        val i = fr.indexOfFirst { d.active in it.layers }
        return if (i >= 0) i else 0
    }

    private fun postAnim(d: Doc) {
        val a = d.assist
        val s = d.anim
        val h = HashMap(d.holds)
        val p = animPlaying
        post { listener.onAnim(a, s, h, p) }
    }

    /** Tiap bingkai: jalankan pemutaran, dan susun ulang kanvas bila frame aktif berganti. */
    private fun animTick() {
        val d = doc ?: return
        if (d.assist == 0) return
        if (animPlaying) {
            if (d.assist != 1 || strokeActive || modal) { animStop(); return }
            val seq = animSeq
            if (seq.isEmpty()) { animStop(); return }
            val k = ((android.os.SystemClock.uptimeMillis() - animStart) * d.anim.fps / 1000L).toInt()
            val once = d.anim.mode == 2
            val pos = if (once) min(k, seq.size - 1) else k % seq.size
            val show = seq[pos]
            if (show != animShow) { animShow = show; dirty.set(0, 0, W, H) }
            if (once && k >= seq.size - 1) animStop() else requestRender()
            return
        }
        val c = animCurrent(d, framesOf(d))
        if (c != animLastCur) {
            animLastCur = c
            dirty.set(0, 0, W, H)
        }
    }

    private fun animStop() {
        animPlaying = false
        animShow = -1
        animLastCur = -1
        dirty.set(0, 0, W, H)
        doc?.let { postAnim(it) }
    }

    fun setAssist(mode: Int) {
        val d = doc ?: return
        if (strokeActive || modal) return
        d.assist = mode.coerceIn(0, 2)
        if (animPlaying) animStop()
        animLastCur = -1
        dirty.set(0, 0, W, H)
        postAnim(d)
        post { listener.onContentChanged() }
    }

    fun setAnim(st: AnimSettings) {
        val d = doc ?: return
        d.anim = st
        if (animPlaying) animSeq = Frames.sequence(framesOf(d).size, st) { i -> holdOf(d, i) }
        dirty.set(0, 0, W, H)
        postAnim(d)
        post { listener.onContentChanged() }
    }

    private fun holdOf(d: Doc, i: Int): Int {
        val fr = framesOf(d)
        return if (i in fr.indices) d.holds[fr[i].key] ?: 0 else 0
    }

    fun setHold(key: Int, hold: Int) {
        val d = doc ?: return
        if (hold <= 0) d.holds.remove(key) else d.holds[key] = hold.coerceAtMost(60)
        postAnim(d)
        post { listener.onContentChanged() }
    }

    fun animPlay(on: Boolean) {
        val d = doc ?: return
        if (on == animPlaying) return
        if (on) {
            if (d.assist != 1 || strokeActive || modal) return
            val fr = framesOf(d)
            animSeq = Frames.sequence(fr.size, d.anim) { i -> holdOf(d, i) }
            animStart = android.os.SystemClock.uptimeMillis()
            animShow = -1
            animPlaying = true
            postAnim(d)
            requestRender()
        } else {
            animStop()
        }
    }

    /** Pilih frame/halaman: layer teratas frame itu jadi layer aktif. */
    fun selectFrame(key: Int) {
        val d = doc ?: return
        if (strokeActive || modal) return
        if (animPlaying) animStop()
        val f = framesOf(d).firstOrNull { it.key == key } ?: return
        d.active = f.layers.last()
        postLayers()
    }

    /** Frame/halaman baru (layer kosong tanpa grup) tepat di atas frame aktif. */
    fun addFrame() {
        val d = doc ?: return
        if (modal || strokeActive) return
        if (animPlaying) animStop()
        val fr = framesOf(d)
        val c = animCurrent(d, fr)
        val lastId = fr.getOrNull(c)?.layers?.last() ?: d.layers.last().id
        val tex = newLayerTex() ?: return
        val l = Layer(d.nextId++, tex)
        l.name = nextName(d.layers.map { it.name }, if (d.assist == 2) "Halaman" else "Frame")
        l.group = 0
        val idx = (d.indexOf(lastId) + 1).coerceIn(0, d.layers.size)
        d.layers.add(idx, l)
        d.active = l.id
        pushEdit(Edit.Exist(l.id, idx, null))
        structChanged()
        makeThumbs()
    }

    /**
     * Ekspor urutan putar animasi: tiap bingkai disusun penuh, diperkecil ke sisi terpanjang [maxDim], lalu
     * diserahkan ke [sink] (RGBA premultiply, baris 0 = atas) beserta lama tampilnya (ms). Berjalan di thread GL.
     */
    fun animExport(maxDim: Int, bgColor: Int?, align: Int, sink: (ByteBuffer, Int, Int, Int) -> Unit): Boolean {
        val d = doc ?: return false
        if (modal) return false
        if (strokeActive) commitStroke()
        val fr = framesOf(d)
        if (fr.isEmpty()) return false
        // Urutan unik: frame berturut-turut yang sama digabung jadi satu bingkai yang lebih lama.
        val seq = Frames.sequence(fr.size, d.anim) { i -> holdOf(d, i) }
        val runs = ArrayList<Pair<Int, Int>>()
        for (f in seq) if (runs.isNotEmpty() && runs.last().first == f) runs[runs.size - 1] = f to runs.last().second + 1 else runs.add(f to 1)
        val s = min(1f, maxDim.toFloat() / max(W, H))
        val al = max(2, align)
        val ow = max(al, (W * s).roundToInt() / al * al)
        val oh = max(al, (H * s).roundToInt() / al * al)
        val full = IRect(0, 0, W, H)
        val sub = substitute
        substitute = null
        val small = Tex(ow, oh)
        try {
            for ((f, count) in runs) {
                compositeInto(d, full, scratch!!, preview!!, bgColor, f)
                scaleInto(outputTex(d, scratch!!, preview!!, true), W, H, small, ow, oh)
                val buf = readRegion(small.fb, IRect(0, 0, ow, oh))
                sink(buf, ow, oh, count * 1000 / d.anim.fps)
            }
        } finally {
            small.delete()
            substitute = sub
            dirty.set(0, 0, W, H)
        }
        return true
    }

    /** Ekspor halaman (Page Assist): tiap halaman penuh sebagai Bitmap ke [sink], berurutan. Thread GL. */
    fun pagesExport(bgColor: Int?, sink: (Bitmap, Int, Int) -> Unit): Boolean {
        val d = doc ?: return false
        if (modal) return false
        if (strokeActive) commitStroke()
        val fr = framesOf(d)
        val full = IRect(0, 0, W, H)
        val sub = substitute
        substitute = null
        try {
            fr.forEachIndexed { i, _ ->
                compositeInto(d, full, scratch!!, preview!!, bgColor, i)
                sink(outBitmap(d, readRegion(outputTex(d, scratch!!, preview!!, false).fb, full), W, H), i, fr.size)
            }
        } finally {
            substitute = sub
            dirty.set(0, 0, W, H)
        }
        return true
    }

    fun setDpi(dpi: Int) {
        val d = doc ?: return
        d.dpi = dpi.coerceIn(1, 9600)
        val v = d.dpi
        post { listener.onDocDpi(v) }
    }

    fun makeThumbs() {
        val d = doc ?: return
        for (l in d.layers) {
            if (!l.thumbDirty) continue
            l.thumbDirty = false
            val bmp = downscale(l.tex.fb, W, H, 144)
            val id = l.id
            post { listener.onThumb(id, bmp) }
        }
    }

    // ================= pipet =================

    fun pick(cx: Float, cy: Float, sx: Float, sy: Float) {
        val c = comp ?: return
        val x = cx.toInt().coerceIn(0, W - 1)
        val y = cy.toInt().coerceIn(0, H - 1)
        val buf = readRegion(c.fb, IRect(x, y, x + 1, y + 1))
        val r = buf.get(0).toInt() and 0xFF
        val g = buf.get(1).toInt() and 0xFF
        val b = buf.get(2).toInt() and 0xFF
        val a = buf.get(3).toInt() and 0xFF
        val color = if (a == 0) Color.WHITE else Color.rgb(
            min(255, r * 255 / a), min(255, g * 255 / a), min(255, b * 255 / a),
        )
        post { listener.onPicked(color, sx, sy) }
    }

    // ================= ColorDrop =================

    fun fillStart(cx: Float, cy: Float, color: Int, threshold: Float, refAll: Boolean) {
        val d = doc ?: return
        if (strokeActive || modal) return
        val l = d.activeLayer() ?: return
        if (l.locked) { post { listener.onMessage("Layer ini terkunci") }; return }
        if (!d.shown(l)) { post { listener.onMessage(if (l.visible) "Grup layer ini sedang disembunyikan" else "Layer ini sedang disembunyikan") }; return }
        if (maskTex == null) maskTex = Tex(W, H, single = true, withFbo = false)
        ensureHeap(W.toLong() * H * 16)
        fillRetried = false
        fillOn = true
        fillLayer = l
        fillSx = cx.toInt()
        fillSy = cy.toInt()
        fillThr = threshold
        fillRefAll = refAll
        fillColor = color
        fillSrc = null
        fillBox.clear()
        blit(l.tex.fb, 0, 0, W, H, preview!!.fb, 0, 0, W, H, false)
        substitute = preview
        substituteFor = l.id
        computeFill()
    }

    fun fillUpdate(threshold: Float, refAll: Boolean, color: Int) {
        if (!fillOn) return
        val refChanged = refAll != fillRefAll
        fillThr = threshold
        fillRefAll = refAll
        fillColor = color
        if (refChanged) fillSrc = null
        computeFill()
    }

    fun fillReseed(cx: Float, cy: Float) {
        if (!fillOn) return
        fillSx = cx.toInt()
        fillSy = cy.toInt()
        computeFill()
    }

    private fun computeFill() {
        val l = fillLayer ?: return
        if (fillSrc == null) {
            val full = IRect(0, 0, W, H)
            val buf = if (fillRefAll) {
                // Acuan semua layer: tumpuk tanpa pratinjau isi.
                val d = doc!!
                val sub = substitute
                substitute = null
                compositeInto(d, full, scratch!!, comp!!, null, if (d.assist != 0) animCurrent(d, framesOf(d)) else -1)
                substitute = sub
                val b = readRegion(scratch!!.fb, full)
                dirty.set(0, 0, W, H)
                b
            } else {
                readRegion(l.tex.fb, full)
            }
            val arr = ByteArray(W * H * 4)
            buf.get(arr)
            fillSrc = arr
        }
        val src = fillSrc!!
        val gen = ++fillGen
        val sx = fillSx
        val sy = fillSy
        val thr = fillThr
        val col = fillColor
        val w = W
        val h = H
        worker.execute {
            // Ambang digeser cepat: hitungan untuk nilai yang sudah lewat dilewati saja.
            if (gen != fillGen) return@execute
            val res = try {
                FloodFill.run(src, w, h, sx, sy, thr, col)
            } catch (e: OutOfMemoryError) {
                null
            }
            if (res == null) tasks.add { e -> e.fillOutOfMemory(gen) } else tasks.add { e -> e.applyFill(gen, res) }
            requestRender()
        }
    }

    /** Memori habis saat menghitung isian: riwayat lama dibuang lalu dicoba sekali lagi. */
    private fun fillOutOfMemory(gen: Int) {
        if (gen != fillGen || !fillOn) return
        if (!fillRetried) {
            fillRetried = true
            ensureHeap(W.toLong() * H * 24)
            computeFill()
        } else {
            post { listener.onMessage("Memori tidak cukup untuk ColorDrop ini. Ketuk Batal, lalu coba lagi.") }
        }
    }

    private fun applyFill(gen: Int, res: FloodFill.Result) {
        if (gen != fillGen || !fillOn) return
        val l = fillLayer ?: return
        val mt = maskTex ?: return
        val bb = Buffers.direct(W * H)
        bb.put(res.mask)
        bb.position(0)
        G.glBindTexture(G.GL_TEXTURE_2D, mt.id)
        G.glPixelStorei(G.GL_UNPACK_ALIGNMENT, 1)
        G.glTexSubImage2D(G.GL_TEXTURE_2D, 0, 0, 0, W, H, G.GL_RED, G.GL_UNSIGNED_BYTE, bb)
        val region = fillBox.copy()
        region.union(res.box)
        if (!region.isEmpty) previewPass(l, region, 2, 1f, useMask = true, fill = fillColor)
        fillBox.set(res.box)
        dirty.union(region)
    }

    fun fillCommit() {
        if (!fillOn) return
        val l = fillLayer
        val r = fillBox.copy().clampTo(W, H)
        if (l != null && !r.isEmpty) {
            val before = readRegion(l.tex.fb, r)
            blit(preview!!.fb, r.x0, r.y0, r.x1, r.y1, l.tex.fb, r.x0, r.y0, r.x1, r.y1, false)
            pushEdit(Edit.Pixels(l.id, r, RegionData(r.w, r.h, before)))
            layerChanged(l)
            makeThumbs()
        }
        endFill()
    }

    fun fillCancel() {
        if (!fillOn) return
        endFill()
    }

    private fun endFill() {
        fillGen++
        fillOn = false
        fillLayer = null
        fillSrc = null
        substitute = null
        dirty.set(0, 0, W, H)
        fillBox.clear()
    }

    // ================= penyesuaian =================

    fun adjustStart(kind: Int) {
        val d = doc ?: return
        if (strokeActive || modal) return
        val l = d.activeLayer() ?: return
        if (l.locked) { post { listener.onMessage("Layer ini terkunci") }; return }
        if (kind == Adj.CLONE) { cloneStart(); return }
        // Shader jenis ini disiapkan dulu; bila gagal, penyesuaian tidak dibuka.
        try {
            when (kind) {
                Adj.HSB, Adj.LIQUIFY -> Unit
                Adj.GAUSS -> filterProg(Adj.ALPHA_LOCK)
                Adj.MOTION, Adj.PERSP -> { filterProg(kind); filterProg(Adj.ALPHA_LOCK) }
                Adj.BLOOM -> { filterProg(Adj.BRIGHT); filterProg(Adj.BLOOM) }
                else -> filterProg(kind)
            }
        } catch (e: RuntimeException) {
            Diag.log("Penyesuaian $kind gagal: ${e.message}")
            post { listener.onMessage("Penyesuaian ini tidak didukung GPU perangkat") }
            return
        }
        adjOn = true
        adjKind = kind
        adjLayer = l
        blit(l.tex.fb, 0, 0, W, H, preview!!.fb, 0, 0, W, H, false)
        substitute = preview
        substituteFor = l.id
        if (kind == Adj.LIQUIFY) liqInit()
        dirty.set(0, 0, W, H)
    }

    private fun uploadLut(t: ByteArray) {
        if (t.size < 256 * 4) return
        val bb = Buffers.direct(256 * 4)
        bb.put(t, 0, 256 * 4)
        bb.position(0)
        G.glBindTexture(G.GL_TEXTURE_2D, lutTex)
        G.glPixelStorei(G.GL_UNPACK_ALIGNMENT, 4)
        G.glTexSubImage2D(G.GL_TEXTURE_2D, 0, 0, 0, 256, 1, G.GL_RGBA, G.GL_UNSIGNED_BYTE, bb)
    }

    /**
     * Parameter per jenis (lihat AppState.adjustChanged):
     * HSB [rona -0.5..0.5, saturasi, kecerahan]; GAUSS [radius px]; BALANCE [9 nilai -1..1];
     * GMAP [jumlah]; MOTION [panjang px, arah rad]; PERSP [jumlah, mode, cx, cy];
     * NOISE [jumlah, skala px, oktaf, turbulensi, mode, acak]; SHARPEN [jumlah]; BLOOM [jumlah, transisi, ukuran px, bakar];
     * GLITCH [jumlah, ukuran blok px, acak, mode]; HALFTONE [ukuran 0..1, mode];
     * CHROMA [jumlah px, falloff, arah rad, mode, cx, cy]; LIQUIFY [kekuatan keseluruhan].
     */
    private fun applyAdjust(p: FloatArray) {
        if (!adjOn) return
        val l = adjLayer ?: return
        val full = IRect(0, 0, W, H)
        val pv = preview!!
        when (adjKind) {
            Adj.HSB -> previewPass(l, full, 3, 1f, hsb = p)
            Adj.GAUSS -> {
                if (l.alphaLock) {
                    val tmp = strokeBuf!!
                    gaussian(l.tex, tmp, p[0] / 2.5f)
                    filterPassTex(Adj.ALPHA_LOCK, l.tex, tmp, p, pv, false)
                    clearRect(tmp, full)
                } else {
                    gaussian(l.tex, pv, p[0] / 2.5f)
                }
            }
            Adj.MOTION -> multiPass(l, Adj.MOTION, p, chain(p[0], 1f))
            Adj.PERSP -> {
                // Jarak terjauh dari titik pusat ke sudut kanvas: di sanalah sebaran sampel paling lebar.
                val cx = p.getOrElse(2) { W / 2f }
                val cy = p.getOrElse(3) { H / 2f }
                val far = max(max(hypot(cx, cy), hypot(W - cx, cy)), max(hypot(cx, H - cy), hypot(W - cx, H - cy)))
                multiPass(l, Adj.PERSP, p, chain(p[0], far * 0.5f))
            }
            Adj.SHARPEN -> {
                val tmp = strokeBuf!!
                gaussian(l.tex, tmp, max(0.6f, p.getOrElse(1) { 3.5f } / 2.5f))
                filterPass(Adj.SHARPEN, l, tmp, p, pv)
                clearRect(tmp, full)
            }
            Adj.BLOOM -> {
                val tmp = strokeBuf!!
                filterPass(Adj.BRIGHT, l, null, p, tmp)
                gaussian(tmp, tmp, max(1f, p[2] / 2.5f))
                filterPass(Adj.BLOOM, l, tmp, p, pv)
                clearRect(tmp, full)
            }
            Adj.LIQUIFY -> {
                liqAmt = p[0]
                liqRender(full)
            }
            else -> filterPass(adjKind, l, null, p, pv)
        }
        // Rona/Saturasi/Kecerahan sudah dibatasi pilihan di shader pratinjau; yang lain dicampur di sini.
        if (selOn && adjKind != Adj.HSB && adjKind != Adj.LIQUIFY) selMix(l, IRect(0, 0, W, H))
        // Mode Pena: hasil penuh disimpan, pratinjau hanya menampilkannya di bagian yang sudah disapukan.
        val af = adjFull
        if (penOn && af != null) {
            blit(pv.fb, 0, 0, W, H, af.fb, 0, 0, W, H, false)
            penComposite(full)
        }
        dirty.set(0, 0, W, H)
    }

    /**
     * Panjang tiap lintasan blur berlapis untuk sebaran [amount]·[reach] piksel. Satu lintasan hanya punya uTaps sampel;
     * bila sebarannya lebih panjang, sampelnya berjarak dan muncul garis-garis bayangan. Maka dibuat lintasan pendek dulu
     * yang panjangnya sama dengan jarak antar-sampel lintasan berikutnya — gabungannya sama dengan satu blur rapat.
     * Hasil: dari yang terpendek ke yang terpanjang ([amount] asli di akhir).
     */
    private fun chain(amount: Float, reach: Float): List<Float> {
        val taps = if (softGpu) 12f else 128f
        val out = ArrayList<Float>()
        var a = amount
        out.add(a)
        while (a * reach > taps * 1.5f && out.size < 6) {
            a /= taps
            out.add(a)
        }
        out.reverse()
        return out
    }

    /** Jalankan filter [kind] beberapa lintasan (jumlah per lintasan = [amounts], parameter lain dari [p]) ke pratinjau. */
    private fun multiPass(l: Layer, kind: Int, p: FloatArray, amounts: List<Float>) {
        val pv = preview!!
        val sb = strokeBuf!!
        val lock = l.alphaLock
        val total = amounts.size + (if (lock) 1 else 0)
        var src = l.tex
        val q = p.copyOf()
        for (i in amounts.indices) {
            // Tujuan berselang-seling supaya lintasan terakhir mendarat di pratinjau.
            val dst = if ((total - 1 - i) % 2 == 0) pv else sb
            q[0] = amounts[i]
            filterPassTex(kind, src, null, q, dst, false)
            src = dst
        }
        if (lock) filterPassTex(Adj.ALPHA_LOCK, l.tex, src, p, pv, false)
        clearRect(sb, IRect(0, 0, W, H))
    }

    /**
     * Blur Gaussian. [src] boleh sama dengan [dst].
     * Sigma kecil: dua lintasan langsung (mendatar ke scratch, lalu tegak ke [dst]).
     * Sigma besar: gambar diperkecil bertahap (½, ¼, …), diblur di ukuran kecil, lalu diperbesar lagi dengan B-spline.
     * Hasilnya membaur seperti gradien halus berapa pun radiusnya — tanpa jarak antar-sampel yang memunculkan
     * bayangan bergaris dan tepi yang masih kontras.
     */
    private fun gaussian(src: Tex, dst: Tex, sigma: Float) {
        if (sigma < 0.5f) {
            if (src !== dst) blit(src.fb, 0, 0, W, H, dst.fb, 0, 0, W, H, false)
            return
        }
        G.glDisable(G.GL_BLEND)
        var k = 0
        while (sigma / (1 shl k) > 5.3f && k < pyr.size - 1 && (W shr (k + 1)) >= 4 && (H shr (k + 1)) >= 4) k++
        if (k == 0) {
            val tmp = scratch!!
            blurPass(src, tmp, sigma, 1f, 0f)
            blurPass(tmp, dst, sigma, 0f, 1f)
            return
        }
        var prev = src
        for (j in 1..k) {
            val t = pyrLevel(j)
            blit(prev.fb, 0, 0, prev.w, prev.h, t.fb, 0, 0, t.w, t.h, true)
            prev = t
        }
        val low = pyr[k]!!
        val tmp = pyrTmpFor(low.w, low.h)
        // Sisa sigma di ukuran kecil (pengecilan dan pembesaran sendiri sudah sedikit memburamkan).
        val s = (sigma / (1 shl k)).let { sqrt(max(0.25f, it * it - 0.6f)) }
        blurPass(low, tmp, s, 1f, 0f)
        blurPass(tmp, low, s, 0f, 1f)
        for (j in k - 1 downTo 0) upsample(pyr[j + 1]!!, if (j == 0) dst else pyr[j]!!, j == 0)
    }

    private fun blurPass(src: Tex, dst: Tex, sigma: Float, dx: Float, dy: Float) {
        G.glBindFramebuffer(G.GL_FRAMEBUFFER, dst.fb)
        G.glViewport(0, 0, dst.w, dst.h)
        G.glDisable(G.GL_BLEND)
        pBlur.use()
        pBlur.f2("uSize", dst.w.toFloat(), dst.h.toFloat())
        pBlur.f("uSigma", sigma)
        pBlur.tex("uSrc", 0, src.id)
        pBlur.f2("uDir", dx, dy)
        drawRect(pBlur, IRect(0, 0, dst.w, dst.h), dst.w, dst.h)
    }

    private fun upsample(src: Tex, dst: Tex, dither: Boolean) {
        G.glBindFramebuffer(G.GL_FRAMEBUFFER, dst.fb)
        G.glViewport(0, 0, dst.w, dst.h)
        G.glDisable(G.GL_BLEND)
        pUp.use()
        pUp.tex("uSrc", 0, src.id)
        pUp.f2("uSrcSize", src.w.toFloat(), src.h.toFloat())
        pUp.f2("uDstSize", dst.w.toFloat(), dst.h.toFloat())
        pUp.i("uDither", if (dither) 1 else 0)
        drawRect(pUp, IRect(0, 0, dst.w, dst.h), dst.w, dst.h)
    }

    private fun pyrLevel(j: Int): Tex {
        val w = max(1, (W + (1 shl j) - 1) shr j)
        val h = max(1, (H + (1 shl j) - 1) shr j)
        val t = pyr[j]
        if (t != null && t.w == w && t.h == h) return t
        t?.delete()
        return Tex(w, h).also { pyr[j] = it }
    }

    private fun pyrTmpFor(w: Int, h: Int): Tex {
        val t = pyrTmp
        if (t != null && t.w == w && t.h == h) return t
        t?.delete()
        return Tex(w, h).also { pyrTmp = it }
    }

    private fun pyrFree() {
        for (i in pyr.indices) {
            pyr[i]?.delete()
            pyr[i] = null
        }
        pyrTmp?.delete()
        pyrTmp = null
    }

    private fun filterProg(kind: Int): Program = filters.getOrPut(kind) { Program(Shaders.RECT_VS, Shaders.filter(kind)) }

    private fun filterPass(kind: Int, l: Layer, aux: Tex?, p: FloatArray, dst: Tex) =
        filterPassTex(kind, l.tex, aux, p, dst, l.alphaLock)

    private fun filterPassTex(kind: Int, src: Tex, aux: Tex?, p: FloatArray, dst: Tex, alphaLock: Boolean) {
        val pFilter = filterProg(kind)
        G.glBindFramebuffer(G.GL_FRAMEBUFFER, dst.fb)
        G.glViewport(0, 0, W, H)
        G.glDisable(G.GL_BLEND)
        pFilter.use()
        pFilter.tex("uSrc", 0, src.id)
        pFilter.tex("uAux", 1, (aux ?: src).id)
        pFilter.tex("uLut", 2, lutTex)
        pFilter.f2("uSize", W.toFloat(), H.toFloat())
        pFilter.f("uTaps", if (softGpu) 12f else 128f)
        java.util.Arrays.fill(filterP, 0f)
        System.arraycopy(p, 0, filterP, 0, min(p.size, filterP.size))
        pFilter.fv("uP", filterP)
        pFilter.i("uAlphaLock", if (alphaLock) 1 else 0)
        drawRect(pFilter, IRect(0, 0, W, H))
    }

    fun adjustCommit() {
        if (!adjOn) return
        val l = adjLayer
        if (l != null) {
            val full = IRect(0, 0, W, H)
            val before = readRegion(l.tex.fb, full)
            blit(preview!!.fb, 0, 0, W, H, l.tex.fb, 0, 0, W, H, false)
            pushEdit(Edit.Pixels(l.id, full, RegionData(W, H, before)))
            layerChanged(l)
            makeThumbs()
        }
        adjOn = false
        adjLayer = null
        substitute = null
        liqFree()
        pyrFree()
        penFree()
        marker = null
        dirty.set(0, 0, W, H)
    }

    fun adjustCancel() {
        adjOn = false
        adjLayer = null
        substitute = null
        adjRequest = null
        adjLut = null
        liqFree()
        pyrFree()
        penFree()
        marker = null
        dirty.set(0, 0, W, H)
    }

    // ================= mode Pena penyesuaian =================

    /** Mode Pena (true): efek hanya muncul di bagian yang disapukan. Mode Layer (false): seluruh layer/pilihan. */
    fun adjustPen(on: Boolean) {
        if (!adjOn || adjKind == Adj.LIQUIFY || on == penOn) { penHistory(); return }
        val pv = preview ?: return
        val full = IRect(0, 0, W, H)
        if (on) {
            try {
                adjFull = Tex(W, H)
                penMask = Tex(W, H, single = true)
                penStroke = Tex(W, H, single = true)
            } catch (e: OutOfMemoryError) {
                Diag.log("Mode Pena: ${e.message}")
                penFree()
                post { listener.onMessage("Memori grafis tidak cukup untuk mode Pena") }
                penHistory()
                return
            }
            blit(pv.fb, 0, 0, W, H, adjFull!!.fb, 0, 0, W, H, false)
            penOn = true
            penComposite(full)
            Diag.log("Pena: mode Pena untuk penyesuaian $adjKind")
        } else {
            // Kembali ke mode Layer: hasil penuh tampil lagi di seluruh layer.
            adjFull?.let { blit(it.fb, 0, 0, W, H, pv.fb, 0, 0, W, H, false) }
            penFree()
        }
        penHistory()
        dirty.set(0, 0, W, H)
    }

    private fun penFree() {
        penOn = false
        penDown = false
        penDabN = 0
        adjFull?.delete(); adjFull = null
        penMask?.delete(); penMask = null
        penStroke?.delete(); penStroke = null
        penInput.clear()
        penUndo.clear()
        penRedo.clear()
    }

    private fun penHistory() {
        val on = penOn
        val u = penUndo.size
        val r = penRedo.size
        post { listener.onPenMode(on, u, r) }
    }

    /** Pratinjau di [r] = layer asli dicampur hasil penuh menurut masker sapuan (termasuk sapuan yang sedang berjalan). */
    private fun penComposite(r: IRect) {
        val l = adjLayer ?: return
        val pv = preview ?: return
        val af = adjFull ?: return
        val pm = penMask ?: return
        val ps = penStroke ?: return
        val c = r.copy().clampTo(W, H)
        if (c.isEmpty) return
        G.glBindFramebuffer(G.GL_FRAMEBUFFER, pv.fb)
        G.glViewport(0, 0, W, H)
        G.glDisable(G.GL_BLEND)
        pPen.use()
        pPen.i("uMode", 2)
        pPen.f2("uSize", W.toFloat(), H.toFloat())
        pPen.tex("uSrc", 0, l.tex.id)
        pPen.tex("uFull", 1, af.id)
        pPen.tex("uMask", 2, pm.id)
        pPen.tex("uStroke", 3, ps.id)
        pPen.f("uOp", if (penDown) penOp else 0f)
        pPen.i("uErase", if (penErase) 1 else 0)
        drawRect(pPen, c)
    }

    private fun penDrain() {
        val box = IRect()
        while (true) {
            val e = penInput.poll() ?: break
            val x = e[0]
            val y = e[1]
            val pr = e[2].coerceIn(0.05f, 1f)
            when (e[3].toInt()) {
                0 -> penBegin(x, y, pr, e[4] > 0.5f, box)
                1, 2 -> {
                    if (!penDown) penBegin(x, y, pr, e[4] > 0.5f, box) else penLine(x, y, pr)
                    if (e[3].toInt() == 2) penEnd(box)
                }
                else -> penCancel(box)
            }
        }
        penFlush(box)
        if (!box.isEmpty) {
            penComposite(box)
            dirty.union(box)
        }
    }

    private fun penBegin(x: Float, y: Float, pr: Float, erase: Boolean, box: IRect) {
        if (penDown) penEnd(box)
        val set = tool?.current()
        penDown = true
        penErase = erase
        penSize = max(2f, set?.sizePx ?: 40f)
        penOp = (set?.opacity ?: 1f).coerceIn(0.02f, 1f)
        penBox.clear()
        penX = x; penY = y; penP = pr
        penResid = 0f
        penAddDab(x, y, pr)
    }

    private fun penLine(x: Float, y: Float, pr: Float) {
        val dx = x - penX
        val dy = y - penY
        val d = hypot(dx, dy)
        val sp = max(1f, penSize * 0.08f)
        var t = sp - penResid
        while (t <= d) {
            val f = t / d
            penAddDab(penX + dx * f, penY + dy * f, penP + (pr - penP) * f)
            t += sp
        }
        penResid = d - (t - sp)
        penX = x; penY = y; penP = pr
    }

    private fun penAddDab(x: Float, y: Float, pr: Float) {
        if (penDabN * 3 + 3 > penDabBuf.size) penDabBuf = penDabBuf.copyOf(penDabBuf.size * 2)
        penDabBuf[penDabN * 3] = x
        penDabBuf[penDabN * 3 + 1] = y
        penDabBuf[penDabN * 3 + 2] = pr
        penDabN++
    }

    /** Tapak-tapak yang terkumpul digambar ke masker sapuan (campuran MAX: tumpang tindih tidak menebal). */
    private fun penFlush(box: IRect) {
        val ps = penStroke
        if (penDabN == 0 || ps == null) { penDabN = 0; return }
        G.glBindFramebuffer(G.GL_FRAMEBUFFER, ps.fb)
        G.glViewport(0, 0, W, H)
        G.glEnable(G.GL_BLEND)
        G.glBlendEquation(G.GL_MAX)
        pPen.use()
        pPen.i("uMode", 0)
        pPen.f2("uSize", W.toFloat(), H.toFloat())
        val r = penSize / 2f
        for (i in 0 until penDabN) {
            val x = penDabBuf[i * 3]
            val y = penDabBuf[i * 3 + 1]
            val pr = penDabBuf[i * 3 + 2]
            val rr = IRect(floor(x - r).toInt() - 1, floor(y - r).toInt() - 1, ceil(x + r).toInt() + 1, ceil(y + r).toInt() + 1).clampTo(W, H)
            if (rr.isEmpty) continue
            pPen.f3("uDab", x, y, r)
            pPen.f("uAmt", pr.pow(0.6f))
            drawRect(pPen, rr)
            box.union(rr)
            penBox.union(rr)
        }
        G.glBlendEquation(G.GL_FUNC_ADD)
        G.glBlendFunc(G.GL_ONE, G.GL_ONE_MINUS_SRC_ALPHA)
        G.glDisable(G.GL_BLEND)
        penDabN = 0
    }

    /** Sapuan selesai: digabung ke masker (bisa diurungkan). */
    private fun penEnd(box: IRect) {
        penFlush(box)
        if (!penDown) return
        val pm = penMask
        val ps = penStroke
        val r = penBox.copy().clampTo(W, H)
        if (pm == null || ps == null || r.isEmpty) { penDown = false; return }
        penPush(penUndo, r)
        penRedo.clear()
        G.glBindFramebuffer(G.GL_FRAMEBUFFER, pm.fb)
        G.glViewport(0, 0, W, H)
        G.glEnable(G.GL_BLEND)
        G.glBlendEquation(G.GL_FUNC_ADD)
        if (penErase) G.glBlendFunc(G.GL_ZERO, G.GL_ONE_MINUS_SRC_ALPHA) else G.glBlendFunc(G.GL_ONE, G.GL_ONE_MINUS_SRC_ALPHA)
        pPen.use()
        pPen.i("uMode", 1)
        pPen.f2("uSize", W.toFloat(), H.toFloat())
        pPen.tex("uStroke", 3, ps.id)
        pPen.f("uOp", penOp)
        drawRect(pPen, r)
        G.glBlendFunc(G.GL_ONE, G.GL_ONE_MINUS_SRC_ALPHA)
        G.glDisable(G.GL_BLEND)
        clearRect(ps, r)
        penDown = false
        box.union(r)
        Diag.log("Pena: sapuan $r${if (penErase) " (hapus efek)" else ""}, ukuran ${penSize.roundToInt()} px")
        penHistory()
    }

    /** Sapuan dibatalkan (mis. ternyata gerakan dua jari): dibuang tanpa mengubah masker. */
    private fun penCancel(box: IRect) {
        penDabN = 0
        if (!penDown) return
        penDown = false
        val r = penBox.copy().clampTo(W, H)
        penStroke?.let { clearRect(it, r) }
        box.union(r)
    }

    private fun readMask(r: IRect): ByteArray {
        val buf = readRegion(penMask!!.fb, r)
        val out = ByteArray(r.w * r.h)
        for (i in out.indices) out[i] = buf.get(i * 4)
        return out
    }

    private fun writeMask(r: IRect, data: ByteArray) {
        val bb = Buffers.direct(data.size)
        bb.put(data)
        bb.position(0)
        G.glBindTexture(G.GL_TEXTURE_2D, penMask!!.id)
        G.glPixelStorei(G.GL_UNPACK_ALIGNMENT, 1)
        G.glTexSubImage2D(G.GL_TEXTURE_2D, 0, r.x0, r.y0, r.w, r.h, G.GL_RED, G.GL_UNSIGNED_BYTE, bb)
    }

    /** Simpan isi masker di [r] ke [stack]; langkah tertua dibuang bila terlalu banyak/besar. */
    private fun penPush(stack: ArrayDeque<PenStep>, r: IRect) {
        stack.addLast(PenStep(r.copy(), readMask(r)))
        var bytes = penUndo.sumOf { it.data.size.toLong() } + penRedo.sumOf { it.data.size.toLong() }
        while (stack.size > 1 && (stack.size > 40 || bytes > 160L * 1024 * 1024)) {
            bytes -= stack.removeFirst().data.size
        }
    }

    /** Urungkan ([redo] false) atau ulangi satu sapuan mode Pena. */
    fun penStep(redo: Boolean) {
        if (!penOn || penDown || penMask == null) return
        val from = if (redo) penRedo else penUndo
        val to = if (redo) penUndo else penRedo
        val s = from.removeLastOrNull() ?: return
        penPush(to, s.r)
        writeMask(s.r, s.data)
        penComposite(s.r)
        dirty.union(s.r)
        penHistory()
    }

    /** Hapus semua sapuan mode Pena (bisa diurungkan). */
    fun penClear() {
        val pm = penMask ?: return
        if (!penOn || penDown) return
        val full = IRect(0, 0, W, H)
        penPush(penUndo, full)
        penRedo.clear()
        clearRect(pm, full)
        penComposite(full)
        dirty.set(0, 0, W, H)
        penHistory()
    }

    // ================= liquify =================

    private fun liqInit() {
        liqFree()
        liqStep = if (W.toLong() * H <= 4_200_000L) 2 else 4
        liqGW = W / liqStep + 2
        liqGH = H / liqStep + 2
        liqD = FloatArray(liqGW * liqGH * 2)
        liqAmt = 1f
        liqDown = false
        liqSeed = 0
        liqInput.clear()
        val a = IntArray(1)
        G.glGenTextures(1, a, 0)
        liqTexId = a[0]
        G.glBindTexture(G.GL_TEXTURE_2D, liqTexId)
        G.glPixelStorei(G.GL_UNPACK_ALIGNMENT, 4)
        G.glTexImage2D(G.GL_TEXTURE_2D, 0, G.GL_RG32F, liqGW, liqGH, 0, G.GL_RG, G.GL_FLOAT, Buffers.direct(liqGW * liqGH * 8))
        G.glTexParameteri(G.GL_TEXTURE_2D, G.GL_TEXTURE_MIN_FILTER, G.GL_NEAREST)
        G.glTexParameteri(G.GL_TEXTURE_2D, G.GL_TEXTURE_MAG_FILTER, G.GL_NEAREST)
        G.glTexParameteri(G.GL_TEXTURE_2D, G.GL_TEXTURE_WRAP_S, G.GL_CLAMP_TO_EDGE)
        G.glTexParameteri(G.GL_TEXTURE_2D, G.GL_TEXTURE_WRAP_T, G.GL_CLAMP_TO_EDGE)
    }

    private fun liqFree() {
        if (liqTexId != 0 && glReady) G.glDeleteTextures(1, intArrayOf(liqTexId), 0)
        liqTexId = 0
        liqD = null
        liqUp = null
        liqTmp = FloatArray(0)
        liqInput.clear()
    }

    /** Liquify: kembalikan semua ke bentuk asli. */
    fun liquifyReset() {
        val dd = liqD ?: return
        java.util.Arrays.fill(dd, 0f)
        liqUpload(IRect(0, 0, liqGW, liqGH))
        liqRender(IRect(0, 0, W, H))
    }

    /** Medan pergeseran di posisi kanvas (x, y), bilinear antartitik kisi. */
    private fun liqSample(dd: FloatArray, x: Float, y: Float, out: FloatArray) {
        val gx = (x / liqStep).coerceIn(0f, (liqGW - 1).toFloat())
        val gy = (y / liqStep).coerceIn(0f, (liqGH - 1).toFloat())
        val ix = min(gx.toInt(), liqGW - 2)
        val iy = min(gy.toInt(), liqGH - 2)
        val fx = gx - ix
        val fy = gy - iy
        val i00 = (iy * liqGW + ix) * 2
        val i10 = i00 + 2
        val i01 = i00 + liqGW * 2
        val i11 = i01 + 2
        for (c in 0..1) {
            val top = dd[i00 + c] + (dd[i10 + c] - dd[i00 + c]) * fx
            val bot = dd[i01 + c] + (dd[i11 + c] - dd[i01 + c]) * fx
            out[c] = top + (bot - top) * fy
        }
    }

    private val liqS = FloatArray(2)

    /**
     * Satu sapuan liquify di (cx, cy) berjari-jari R. Medan baru dihitung dari medan lama ("dibawa"): titik p mengambil
     * isi dari titik q, jadi pergeseran barunya = q + D(q) − p. [k] = kekuatan sapuan ini, (vx, vy) = gerak pena.
     */
    private fun liqDab(cx: Float, cy: Float, mode: Int, rad: Float, k: Float, vx: Float, vy: Float, distort: Float, box: IRect) {
        val dd = liqD ?: return
        val st = liqStep.toFloat()
        val gx0 = max(0, floor((cx - rad) / st).toInt())
        val gx1 = min(liqGW - 1, ceil((cx + rad) / st).toInt())
        val gy0 = max(0, floor((cy - rad) / st).toInt())
        val gy1 = min(liqGH - 1, ceil((cy + rad) / st).toInt())
        if (gx1 < gx0 || gy1 < gy0) return
        val nw = gx1 - gx0 + 1
        val nh = gy1 - gy0 + 1
        if (liqTmp.size < nw * nh * 2) liqTmp = FloatArray(nw * nh * 2)
        val tmp = liqTmp
        val r2 = rad * rad
        val cell = max(6f, rad * 0.22f)
        val nx = -liqDirY
        val ny = liqDirX
        for (gy in gy0..gy1) {
            for (gx in gx0..gx1) {
                val src = (gy * liqGW + gx) * 2
                val o = ((gy - gy0) * nw + (gx - gx0)) * 2
                val px = gx * st
                val py = gy * st
                val dx = px - cx
                val dy = py - cy
                val d2 = dx * dx + dy * dy
                if (d2 >= r2) {
                    tmp[o] = dd[src]; tmp[o + 1] = dd[src + 1]
                    continue
                }
                val t = 1f - d2 / r2
                val w = t * t * k
                if (mode == Adj.LQ_RECON) {
                    val f = 1f - min(1f, w)
                    tmp[o] = dd[src] * f; tmp[o + 1] = dd[src + 1] * f
                    continue
                }
                var qx: Float
                var qy: Float
                when (mode) {
                    Adj.LQ_PUSH -> { qx = px - vx * w; qy = py - vy * w }
                    Adj.LQ_TWIRL_R, Adj.LQ_TWIRL_L -> {
                        val ang = if (mode == Adj.LQ_TWIRL_R) w else -w
                        val c = cos(ang)
                        val sn = sin(ang)
                        qx = cx + dx * c + dy * sn
                        qy = cy - dx * sn + dy * c
                    }
                    Adj.LQ_PINCH -> { qx = cx + dx * (1f + w); qy = cy + dy * (1f + w) }
                    Adj.LQ_EXPAND -> { qx = cx + dx * (1f - w); qy = cy + dy * (1f - w) }
                    Adj.LQ_CRYSTAL -> {
                        val hx = floor(px / cell).toInt()
                        val hy = floor(py / cell).toInt()
                        var h = hx * 73856093 xor hy * 19349663 xor liqSeed * 83492791
                        h = h xor (h ushr 13)
                        h *= 0x5bd1e995
                        h = h xor (h ushr 15)
                        val a = (h and 0xFFFF) / 65535f * 6.2831855f
                        val m = w * rad * (0.15f + distort * 0.6f)
                        qx = px + cos(a) * m
                        qy = py + sin(a) * m
                    }
                    else -> {
                        // Tepi: isi ditarik ke garis yang dilalui pena (tegak lurus arah gerak).
                        val proj = dx * nx + dy * ny
                        val f = w * (0.5f + distort)
                        qx = px + nx * proj * f
                        qy = py + ny * proj * f
                    }
                }
                liqSample(dd, qx, qy, liqS)
                tmp[o] = qx + liqS[0] - px
                tmp[o + 1] = qy + liqS[1] - py
            }
        }
        for (gy in gy0..gy1) {
            System.arraycopy(tmp, (gy - gy0) * nw * 2, dd, (gy * liqGW + gx0) * 2, nw * 2)
        }
        box.union(IRect(gx0, gy0, gx1 + 1, gy1 + 1))
    }

    private fun liqDrain() {
        if (liqD == null) { liqInput.clear(); return }
        val box = IRect()
        val par = liqParams
        val mode = par[0].toInt()
        val rad = max(2f, par[1] / 2f)
        val strength = par[2].coerceIn(0f, 1f)
        val distort = par[3].coerceIn(0f, 1f)
        while (true) {
            val e = liqInput.poll() ?: break
            val x = e[0]
            val y = e[1]
            val pr = e[2].coerceIn(0.05f, 1f)
            val phase = e[3].toInt()
            val t = e[4].toLong()
            if (phase == 0 || !liqDown) {
                liqDown = true
                liqX = x; liqY = y; liqT = t
                liqSeed++
                if (phase == 2) liqDown = false
                continue
            }
            val vx = x - liqX
            val vy = y - liqY
            val dist = hypot(vx, vy)
            if (dist > 0.5f) { liqDirX = vx / dist; liqDirY = vy / dist }
            val dt = ((t - liqT).coerceIn(0L, 60L)) / 1000f
            if (mode == Adj.LQ_PUSH) {
                if (dist > 0.01f) {
                    val n = max(1, ceil(dist / max(2f, rad * 0.1f)).toInt())
                    val k = (0.15f + 0.85f * strength) * pr
                    for (i in 1..n) {
                        val f = i / n.toFloat()
                        liqDab(liqX + vx * f, liqY + vy * f, mode, rad, k, vx / n, vy / n, distort, box)
                    }
                }
            } else {
                val rate = when (mode) {
                    Adj.LQ_TWIRL_R, Adj.LQ_TWIRL_L -> 2.2f
                    Adj.LQ_PINCH, Adj.LQ_EXPAND -> 0.9f
                    Adj.LQ_CRYSTAL -> 1.2f
                    Adj.LQ_EDGE -> 1.4f
                    else -> 2.5f
                }
                // Gerakan cepat dipecah supaya jejaknya tidak putus-putus.
                val n = max(1, ceil(dist / max(2f, rad * 0.25f)).toInt())
                val k = rate * dt * (0.15f + 0.85f * strength) * pr / n
                if (k > 0f) for (i in 1..n) {
                    val f = i / n.toFloat()
                    liqDab(liqX + vx * f, liqY + vy * f, mode, rad, k, 0f, 0f, distort, box)
                }
            }
            liqX = x; liqY = y; liqT = t
            if (phase == 2) liqDown = false
        }
        if (box.isEmpty) return
        liqUpload(box)
        val st = liqStep
        liqRender(IRect((box.x0 - 1) * st, (box.y0 - 1) * st, (box.x1 + 1) * st, (box.y1 + 1) * st).clampTo(W, H))
    }

    private fun liqUpload(b: IRect) {
        val dd = liqD ?: return
        if (liqTexId == 0 || b.isEmpty) return
        val n = b.w * b.h * 2
        var buf = liqUp
        if (buf == null || buf.capacity() < n) {
            buf = Buffers.direct(max(n, 65536) * 4).asFloatBuffer()
            liqUp = buf
        }
        buf!!.clear()
        for (y in b.y0 until b.y1) buf.put(dd, (y * liqGW + b.x0) * 2, b.w * 2)
        buf.position(0)
        G.glBindTexture(G.GL_TEXTURE_2D, liqTexId)
        G.glPixelStorei(G.GL_UNPACK_ALIGNMENT, 4)
        G.glTexSubImage2D(G.GL_TEXTURE_2D, 0, b.x0, b.y0, b.w, b.h, G.GL_RG, G.GL_FLOAT, buf)
    }

    private fun liqRender(r: IRect) {
        val l = adjLayer ?: return
        if (liqTexId == 0 || r.isEmpty) return
        G.glBindFramebuffer(G.GL_FRAMEBUFFER, preview!!.fb)
        G.glViewport(0, 0, W, H)
        G.glDisable(G.GL_BLEND)
        pLiquify.use()
        pLiquify.tex("uSrc", 0, l.tex.id)
        pLiquify.tex("uField", 1, liqTexId)
        pLiquify.f2("uSize", W.toFloat(), H.toFloat())
        pLiquify.f2("uGrid", liqGW.toFloat(), liqGH.toFloat())
        pLiquify.f("uStep", liqStep.toFloat())
        pLiquify.f("uAmt", liqAmt)
        pLiquify.i("uAlphaLock", if (l.alphaLock) 1 else 0)
        drawRect(pLiquify, r)
        if (selOn) selMix(l, r)
        dirty.union(r)
    }

    // ================= clone =================

    private fun cloneStart() {
        cloneOn = true
        if (cloneMarker == null) {
            cloneSrcX = W * 0.3f
            cloneSrcY = H * 0.5f
        }
        cloneFixed = false
        cloneMarker = floatArrayOf(cloneSrcX, cloneSrcY)
    }

    fun cloneStop() {
        cloneOn = false
        cloneMarker = null
    }

    /** Pindahkan sumber Clone; jaraknya ke pena ditetapkan lagi saat goresan berikutnya dimulai. */
    fun cloneSource(x: Float, y: Float) {
        if (!cloneOn) return
        cloneSrcX = x.coerceIn(0f, W.toFloat())
        cloneSrcY = y.coerceIn(0f, H.toFloat())
        cloneFixed = false
        cloneMarker = floatArrayOf(cloneSrcX, cloneSrcY)
    }

    private val cloneActive: Boolean get() = cloneOn && strokeTool == ToolKind.PAINT

    // ================= pilihan (selection) =================

    /** Operasi masker di GPU: 0 = [a] × pilihan, 1 = [a] × (1 − pilihan), 2 = campur [a]→[b], 3 = isi warna. */
    private fun selOp(mode: Int, a: Tex, b: Tex?, dst: Tex, r: IRect, color: Int = 0, alphaLock: Boolean = false) {
        val st = selTex ?: return
        G.glBindFramebuffer(G.GL_FRAMEBUFFER, dst.fb)
        G.glViewport(0, 0, W, H)
        G.glDisable(G.GL_BLEND)
        pSelOp.use()
        pSelOp.tex("uA", 0, a.id)
        pSelOp.tex("uB", 1, (b ?: a).id)
        pSelOp.tex("uMask", 2, st.id)
        pSelOp.f2("uSize", W.toFloat(), H.toFloat())
        pSelOp.i("uMode", mode)
        pSelOp.f3("uColor", Color.red(color) / 255f, Color.green(color) / 255f, Color.blue(color) / 255f)
        pSelOp.i("uAlphaLock", if (alphaLock) 1 else 0)
        drawRect(pSelOp, r)
    }

    /** Penyesuaian hanya di dalam pilihan: pratinjau = campuran layer asli dan hasil penyesuaian menurut masker. */
    private fun selMix(l: Layer, r: IRect) {
        val sc = scratch ?: return
        val pv = preview ?: return
        selOp(2, l.tex, pv, sc, r)
        blit(sc.fb, r.x0, r.y0, r.x1, r.y1, pv.fb, r.x0, r.y0, r.x1, r.y1, false)
    }

    /** (thread bantu) Ganti masker pilihan dan kirim ke GPU. */
    private fun setSel(m: ByteArray?, keepBase: Boolean = false) {
        val w = W
        val h = H
        val box = if (m == null) IRect() else SelMask.bounds(m, w, h)
        val res = if (box.isEmpty) null else m
        selMask = res
        if (!keepBase) selBase = res
        val saved = selSaved.size
        tasks.add { e -> e.uploadSel(res, box) }
        requestRender()
        post { listener.onSelectionChanged(res != null, saved) }
    }

    private fun uploadSel(m: ByteArray?, box: IRect) {
        if (doc == null) return
        if (m == null || m.size != W * H) {
            selOn = false
            selBox.clear()
            return
        }
        val t = selTex ?: Tex(W, H, single = true, withFbo = false).also { selTex = it }
        val bb = Buffers.direct(W * H)
        bb.put(m)
        bb.position(0)
        G.glBindTexture(G.GL_TEXTURE_2D, t.id)
        G.glPixelStorei(G.GL_UNPACK_ALIGNMENT, 1)
        G.glTexSubImage2D(G.GL_TEXTURE_2D, 0, 0, 0, W, H, G.GL_RED, G.GL_UNSIGNED_BYTE, bb)
        selOn = true
        selBox.set(box)
    }

    /** Bentuk pilihan: [kind] 0 = segi banyak (titik berpasangan), 1 = persegi, 2 = elips (x0, y0, x1, y1). */
    fun selShape(kind: Int, pts: FloatArray, op: Int) {
        if (doc == null) return
        val w = W
        val h = H
        work {
            val shape = when (kind) {
                1 -> SelMask.rect(pts[0], pts[1], pts[2], pts[3], w, h)
                2 -> SelMask.ellipse(pts[0], pts[1], pts[2], pts[3], w, h)
                else -> {
                    val n = pts.size / 2
                    SelMask.polygon(FloatArray(n) { pts[2 * it] }, FloatArray(n) { pts[2 * it + 1] }, n, w, h)
                }
            }
            setSel(combined(selMask, shape, op))
        }
    }

    private fun combined(cur: ByteArray?, shape: ByteArray, op: Int): ByteArray? = when {
        op == SelMask.OP_REPLACE -> shape
        cur == null -> if (op == SelMask.OP_REMOVE) null else shape
        else -> cur.copyOf().also { SelMask.combine(it, shape, op) }
    }

    /** Pilihan otomatis: area berwarna mirip di layer aktif, mulai dari ([x], [y]). Ambang bisa diubah sambil ditahan. */
    fun selAutoBegin(x: Float, y: Float, threshold: Float, op: Int) {
        val d = doc ?: return
        val l = d.activeLayer() ?: return
        ensureHeap(W.toLong() * H * 16)
        val arr = try {
            val buf = readRegion(l.tex.fb, IRect(0, 0, W, H))
            ByteArray(W * H * 4).also { buf.get(it) }
        } catch (e: OutOfMemoryError) {
            lowMemory()
            return
        }
        val sx = x.toInt()
        val sy = y.toInt()
        val gen = ++selAutoGen
        work {
            selAutoSrc = arr
            selBefore = selMask
            selAutoX = sx
            selAutoY = sy
            selAutoRun(gen, threshold, op)
        }
    }

    fun selAutoThreshold(threshold: Float, op: Int) {
        val gen = ++selAutoGen
        work { selAutoRun(gen, threshold, op) }
    }

    private fun selAutoRun(gen: Int, threshold: Float, op: Int) {
        if (gen != selAutoGen) return
        val src = selAutoSrc ?: return
        val w = W
        val h = H
        val res = FloodFill.run(src, w, h, selAutoX, selAutoY, threshold)
        val m = res.mask
        for (i in m.indices) if (m[i].toInt() != 0) m[i] = 0xFF.toByte()
        if (gen != selAutoGen) return
        setSel(combined(selBefore, m, op))
    }

    fun selAutoEnd() {
        work { selAutoSrc = null; selBefore = null }
    }

    fun selInvert() {
        val w = W
        val h = H
        work {
            val m = selMask?.copyOf() ?: ByteArray(w * h)
            SelMask.invert(m)
            setSel(m)
        }
    }

    fun selClear() {
        work { setSel(null) }
    }

    /** Perhalus tepi pilihan sebesar [px] piksel (dihitung dari pilihan sebelum diperhalus). */
    fun selFeather(px: Int) {
        val w = W
        val h = H
        work {
            val base = selBase ?: return@work
            setSel(SelMask.feather(base, w, h, px), keepBase = true)
        }
    }

    fun selSave() {
        work {
            val m = selMask ?: return@work
            selSaved.add(m.copyOf())
            while (selSaved.size > 8) selSaved.removeAt(0)
            val n = selSaved.size
            post { listener.onSelectionChanged(true, n) }
            post { listener.onMessage("Pilihan disimpan ($n)") }
        }
    }

    fun selLoad(i: Int) {
        work {
            val m = selSaved.getOrNull(i) ?: return@work
            setSel(m.copyOf())
        }
    }

    /** Salin & Tempel: isi pilihan di layer aktif disalin ke layer baru di atasnya. */
    fun selCopyPaste() {
        val d = doc ?: return
        if (strokeActive || modal) return
        if (!selOn) { post { listener.onMessage("Belum ada pilihan") }; return }
        val src = d.activeLayer() ?: return
        val nl = addLayer("${src.name} salinan") ?: return
        selOp(0, src.tex, null, nl.tex, IRect(0, 0, W, H))
        layerChanged(nl)
        makeThumbs()
        dirty.set(0, 0, W, H)
        post { listener.onMessage("Isi pilihan disalin ke layer baru") }
    }

    /** Isi warna di dalam pilihan pada layer aktif (satu langkah urungkan). */
    fun selFill(color: Int) {
        val d = doc ?: return
        if (strokeActive || modal) return
        if (!selOn) { post { listener.onMessage("Belum ada pilihan") }; return }
        val l = d.activeLayer() ?: return
        if (l.locked) { post { listener.onMessage("Layer ini terkunci") }; return }
        val r = selBox.copy().clampTo(W, H)
        if (r.isEmpty) return
        val pv = preview!!
        selOp(3, l.tex, null, pv, r, color, l.alphaLock)
        val before = readRegion(l.tex.fb, r)
        blit(pv.fb, r.x0, r.y0, r.x1, r.y1, l.tex.fb, r.x0, r.y0, r.x1, r.y1, false)
        pushEdit(Edit.Pixels(l.id, r, RegionData(r.w, r.h, before)))
        layerChanged(l)
        makeThumbs()
        dirty.union(r)
    }

    /** Garis pilihan yang sedang dibuat: hitam lebar + putih tipis supaya terlihat di warna apa pun. */
    private fun drawSelDraft(v: FloatArray, pts: FloatArray, closed: Boolean) {
        if (pts.size < 4) {
            if (pts.size == 2) {
                val p = pts.copyOf()
                Matrix().apply { setValues(v) }.mapPoints(p)
                dot(p[0], p[1], 5f * density, 0.25f, 0.6f, 1f)
            }
            return
        }
        // Paling banyak 500 titik digambar (garis panjang dijarangkan).
        val n = pts.size / 2
        val step = max(1, (n + 499) / 500)
        val m = (n + step - 1) / step
        val p = FloatArray(m * 2)
        for (k in 0 until m) {
            val i = min(n - 1, k * step)
            p[2 * k] = pts[2 * i]; p[2 * k + 1] = pts[2 * i + 1]
        }
        Matrix().apply { setValues(v) }.mapPoints(p)
        val prim = if (closed) G.GL_LINE_LOOP else G.GL_LINE_STRIP
        lines(p, prim, 3f, 0.05f, 0.05f, 0.08f)
        lines(p, prim, 1.5f, 1f, 1f, 1f)
        if (!closed) dot(p[0], p[1], 5f * density, 0.25f, 0.6f, 1f)
    }

    // ================= transformasi =================

    /**
     * Mulai transformasi. [ids] null = layer aktif; selain itu semua layer itu digeser/diputar/diubah ukurannya
     * bersama (layer terpilih atau isi satu grup).
     */
    fun transformStart(ids: List<Int>? = null) {
        val d = doc ?: return
        if (strokeActive || modal) return
        val targets = if (ids.isNullOrEmpty()) listOfNotNull(d.activeLayer()) else d.layers.filter { it.id in ids }
        if (targets.isEmpty()) return
        if (targets.any { it.locked }) {
            post { listener.onMessage(if (targets.size == 1) "Layer ini terkunci" else "Ada layer terkunci di pilihan ini") }
            post { listener.onTransformBounds(RectF()) }
            return
        }
        val b = IRect()
        val useSel = selOn && targets.size == 1
        if (useSel) {
            // Hanya isi di dalam pilihan yang dipindah: dipisah jadi potongan (diangkat) dan sisanya.
            val l = targets[0]
            val fl = xfFloat ?: Tex(W, H).also { xfFloat = it }
            val rs = xfRest ?: Tex(W, H).also { xfRest = it }
            selOp(0, l.tex, null, fl, IRect(0, 0, W, H))
            selOp(1, l.tex, null, rs, IRect(0, 0, W, H))
            b.set(contentBoundsOf(fl))
            if (b.isEmpty) {
                freeXfSel()
                post { listener.onMessage("Tidak ada isi di dalam pilihan") }
                post { listener.onTransformBounds(RectF()) }
                return
            }
            xfSel = true
            xfSelId = l.id
        } else {
            for (l in targets) {
                val cb = contentBounds(l)
                if (!cb.isEmpty) b.union(cb)
            }
        }
        if (b.isEmpty) {
            post { listener.onMessage(if (targets.size == 1) "Layer ini masih kosong" else "Layer yang dipilih masih kosong") }
            post { listener.onTransformBounds(RectF()) }
            return
        }
        xfOn = true
        xfLayers = targets
        xfIds.clear()
        targets.forEach { xfIds.add(it.id) }
        xfOrig.set(b)
        xfPrev.set(b)
        xfMatrix = floatArrayOf(1f, 0f, 0f, 0f, 1f, 0f, 0f, 0f, 1f)
        floatArrayOf(1f, 0f, 0f, 0f, 1f, 0f, 0f, 0f, 1f).copyInto(xfInv)
        xfWarp = false
        xfDraw = null
        xfInterp = 1
        dirty.set(0, 0, W, H)
        val rf = RectF(b.x0.toFloat(), b.y0.toFloat(), b.x1.toFloat(), b.y1.toFloat())
        if (targets.size > 1) post { listener.onMessage("${targets.size} layer dipindah bersama") }
        post { listener.onTransformBounds(rf) }
    }

    private fun contentBounds(l: Layer): IRect = contentBoundsOf(l.tex)

    private fun contentBoundsOf(t: Tex): IRect {
        val buf = readRegion(t.fb, IRect(0, 0, W, H))
        var minX = W; var minY = H; var maxX = -1; var maxY = -1
        for (y in 0 until H) {
            val row = y * W * 4
            var x = 0
            while (x < W) {
                if (buf.get(row + x * 4 + 3).toInt() != 0) {
                    if (x < minX) minX = x
                    if (x > maxX) maxX = x
                    if (y < minY) minY = y
                    if (y > maxY) maxY = y
                    // Lompat ke ujung kanan baris untuk mencari maxX lebih cepat.
                    var xr = W - 1
                    while (xr > maxX && buf.get(row + xr * 4 + 3).toInt() == 0) xr--
                    if (xr > maxX) maxX = xr
                    break
                }
                x++
            }
        }
        return if (maxX < 0) IRect() else IRect(minX, minY, maxX + 1, maxY + 1)
    }

    private fun applyTransform(f: XfFrame) {
        if (!xfOn) return
        xfDraw = f
        xfInterp = f.interp
        val mat = Matrix().apply { setValues(f.m) }
        val inv = Matrix()
        if (!mat.invert(inv)) return
        val warp = f.net != null && xfLayers.size == 1
        val now = if (warp) buildMesh(f) else mappedBounds(f.m)
        now.clampTo(W, H)
        val region = xfPrev.copy()
        region.union(now)
        if (warp != xfWarp) region.set(0, 0, W, H)
        region.clampTo(W, H)
        if (warp) {
            val l = xfLayers[0]
            val pv = preview!!
            clearRect(pv, if (!xfWarp) IRect(0, 0, W, H) else region)
            drawMesh(if (xfSel) xfFloat!! else l.tex, pv)
            if (!xfSel) {
                substitute = pv
                substituteFor = l.id
            }
        } else if (xfWarp) {
            substitute = null
        }
        xfWarp = warp
        // Pratinjau tanpa Warp: komposit mengambil layer-layer ini lewat matriks kebalikan (tanpa salinan tiap layer).
        inv.getValues(xfInv)
        xfPrev.set(now)
        xfMatrix = f.m
        dirty.union(region)
    }

    /** Batas kotak isi setelah dipetakan matriks (proyektif). Seluruh kanvas bila ada sudut "di belakang". */
    private fun mappedBounds(m: FloatArray): IRect {
        val xs = floatArrayOf(xfOrig.x0.toFloat(), xfOrig.x1.toFloat(), xfOrig.x1.toFloat(), xfOrig.x0.toFloat())
        val ys = floatArrayOf(xfOrig.y0.toFloat(), xfOrig.y0.toFloat(), xfOrig.y1.toFloat(), xfOrig.y1.toFloat())
        var x0 = Float.MAX_VALUE; var y0 = Float.MAX_VALUE; var x1 = -Float.MAX_VALUE; var y1 = -Float.MAX_VALUE
        for (k in 0..3) {
            val w = m[6] * xs[k] + m[7] * ys[k] + m[8]
            if (w <= 1e-6f) return IRect(0, 0, W, H)
            val x = (m[0] * xs[k] + m[1] * ys[k] + m[2]) / w
            val y = (m[3] * xs[k] + m[4] * ys[k] + m[5]) / w
            x0 = min(x0, x); x1 = max(x1, x); y0 = min(y0, y); y1 = max(y1, y)
        }
        return IRect(floor(x0).toInt() - 2, floor(y0).toInt() - 2, ceil(x1).toInt() + 2, ceil(y1).toInt() + 2)
    }

    /** Isi jaring Warp (posisi tujuan + posisi asal tiap titik) dan kembalikan batasnya di kanvas. */
    private fun buildMesh(f: XfFrame): IRect {
        val net = f.net ?: return IRect(0, 0, W, H)
        val m = f.m
        val sx0 = f.src[0]; val sy0 = f.src[1]; val sx1 = f.src[2]; val sy1 = f.src[3]
        var x0 = Float.MAX_VALUE; var y0 = Float.MAX_VALUE; var x1 = -Float.MAX_VALUE; var y1 = -Float.MAX_VALUE
        var behind = false
        meshFloats.clear()
        for (j in 0..meshN) for (i in 0..meshN) {
            val u = i / meshN.toFloat()
            val w = j / meshN.toFloat()
            val p = XfFrame.bezier(net, u, w)
            var h = m[6] * p[0] + m[7] * p[1] + m[8]
            if (h <= 1e-6f) { behind = true; h = 1e-6f }
            val x = (m[0] * p[0] + m[1] * p[1] + m[2]) / h
            val y = (m[3] * p[0] + m[4] * p[1] + m[5]) / h
            meshFloats.put(x); meshFloats.put(y)
            meshFloats.put(sx0 + (sx1 - sx0) * u); meshFloats.put(sy0 + (sy1 - sy0) * w)
            x0 = min(x0, x); x1 = max(x1, x); y0 = min(y0, y); y1 = max(y1, y)
        }
        meshFloats.flip()
        if (behind) return IRect(0, 0, W, H)
        return IRect(floor(x0).toInt() - 2, floor(y0).toInt() - 2, ceil(x1).toInt() + 2, ceil(y1).toInt() + 2)
    }

    private fun drawMesh(src: Tex, target: Tex, blend: Boolean = false) {
        G.glBindFramebuffer(G.GL_FRAMEBUFFER, target.fb)
        G.glViewport(0, 0, W, H)
        if (blend) {
            G.glEnable(G.GL_BLEND)
            G.glBlendFunc(G.GL_ONE, G.GL_ONE_MINUS_SRC_ALPHA)
        } else {
            G.glDisable(G.GL_BLEND)
        }
        pMesh.use()
        pMesh.f2("uTarget", W.toFloat(), H.toFloat())
        pMesh.f2("uSize", W.toFloat(), H.toFloat())
        pMesh.i("uInterp", xfInterp)
        pMesh.tex("uLayer", 0, src.id)
        G.glBindVertexArray(meshVao)
        G.glBindBuffer(G.GL_ARRAY_BUFFER, meshVbo)
        meshFloats.position(0)
        G.glBufferData(G.GL_ARRAY_BUFFER, meshFloats.limit() * 4, meshFloats, G.GL_STREAM_DRAW)
        G.glDrawElements(G.GL_TRIANGLES, meshN * meshN * 6, G.GL_UNSIGNED_SHORT, 0)
        G.glBindVertexArray(0)
        G.glBindBuffer(G.GL_ARRAY_BUFFER, 0)
        G.glDisable(G.GL_BLEND)
    }

    /** Gambar [src] lewat matriks transformasi (kebalikan [xfInv]) ke [target] di wilayah [r]. */
    private fun drawTransformed(src: Tex, target: Tex, r: IRect, blend: Boolean) {
        G.glBindFramebuffer(G.GL_FRAMEBUFFER, target.fb)
        G.glViewport(0, 0, W, H)
        if (blend) {
            G.glEnable(G.GL_BLEND)
            G.glBlendFunc(G.GL_ONE, G.GL_ONE_MINUS_SRC_ALPHA)
        } else {
            G.glDisable(G.GL_BLEND)
        }
        pTransform.use()
        pTransform.tex("uLayer", 0, src.id)
        pTransform.m3("uInv", colMajor(xfInv))
        pTransform.f2("uSize", W.toFloat(), H.toFloat())
        pTransform.i("uInterp", xfInterp)
        drawRect(pTransform, r)
        G.glDisable(G.GL_BLEND)
    }

    /** Hasil transformasi ditulis ke tiap layer; semuanya satu langkah urungkan. */
    fun transformCommit() {
        if (!xfOn) return
        val d = doc
        val r = xfOrig.copy()
        r.union(xfPrev)
        r.clampTo(W, H)
        if (d != null && !r.isEmpty && xfSel) {
            // Isi pilihan: sisa layer + potongan yang sudah dipindah, lalu masker pilihan ikut dipindah.
            val l = xfLayers.firstOrNull()
            if (l != null && d.find(l.id) != null) {
                val before = readRegion(l.tex.fb, r)
                blit(xfRest!!.fb, r.x0, r.y0, r.x1, r.y1, l.tex.fb, r.x0, r.y0, r.x1, r.y1, false)
                if (xfWarp) drawMesh(xfFloat!!, l.tex, blend = true) else drawTransformed(xfFloat!!, l.tex, r, blend = true)
                pushEdit(Edit.Pixels(l.id, r, RegionData(r.w, r.h, before)))
                layerChanged(l)
                makeThumbs()
            }
            selTex?.let { st ->
                val sc = scratch!!
                clearRect(sc, IRect(0, 0, W, H))
                if (xfWarp) drawMesh(st, sc) else drawTransformed(st, sc, IRect(0, 0, W, H), blend = false)
                val buf = readRegion(sc.fb, IRect(0, 0, W, H))
                val m = ByteArray(W * H)
                for (i in 0 until W * H) m[i] = buf.get(i * 4)
                work { setSel(m) }
            }
            freeXfSel()
            endTransform()
            return
        }
        if (d != null && !r.isEmpty) {
            val edits = ArrayList<Edit>()
            for (l in xfLayers) {
                if (d.find(l.id) == null) continue
                val pv = preview!!
                if (xfWarp) {
                    clearRect(pv, r)
                    drawMesh(l.tex, pv)
                } else {
                    G.glBindFramebuffer(G.GL_FRAMEBUFFER, pv.fb)
                    G.glViewport(0, 0, W, H)
                    G.glDisable(G.GL_BLEND)
                    pTransform.use()
                    pTransform.tex("uLayer", 0, l.tex.id)
                    pTransform.m3("uInv", colMajor(xfInv))
                    pTransform.f2("uSize", W.toFloat(), H.toFloat())
                    pTransform.i("uInterp", xfInterp)
                    drawRect(pTransform, r)
                }
                val before = readRegion(l.tex.fb, r)
                blit(pv.fb, r.x0, r.y0, r.x1, r.y1, l.tex.fb, r.x0, r.y0, r.x1, r.y1, false)
                edits.add(Edit.Pixels(l.id, r, RegionData(r.w, r.h, before)))
                layerChanged(l)
            }
            if (edits.isNotEmpty()) pushEdit(if (edits.size == 1) edits[0] else Edit.Group(edits))
            makeThumbs()
        }
        endTransform()
    }

    fun transformCancel() {
        freeXfSel()
        endTransform()
    }

    private fun freeXfSel() {
        xfSel = false
        xfSelId = -1
        xfFloat?.delete(); xfFloat = null
        xfRest?.delete(); xfRest = null
    }

    private fun endTransform() {
        xfOn = false
        xfWarp = false
        xfDraw = null
        xfLayers = emptyList()
        xfIds.clear()
        xfMatrix = null
        xfRequest = null
        substitute = null
        dirty.set(0, 0, W, H)
    }

    // ================= ekspor =================

    /** Gambar gabungan sebagai Bitmap; [bg] null = latar transparan. */
    fun exportBitmap(bg: Int?): Bitmap? {
        val d = doc ?: return null
        if (modal) {
            post { listener.onMessage("Selesaikan dulu mode yang sedang aktif") }
            return null
        }
        if (strokeActive) commitStroke()
        val full = IRect(0, 0, W, H)
        val sub = substitute
        substitute = null
        compositeInto(d, full, scratch!!, preview!!, bg, if (d.assist != 0) animCurrent(d, framesOf(d)) else -1)
        substitute = sub
        val buf = readRegion(outputTex(d, scratch!!, preview!!, false).fb, full)
        // Pratinjau terpakai sebagai penampung; bangun ulang.
        dirty.set(0, 0, W, H)
        return outBitmap(d, buf, W, H)
    }

    /** Piksel gabungan RGBA (premultiply, baris 0 = atas) dalam ruang RGB karya, tanpa proof. Thread GL. */
    fun compositePixels(bg: Int?): ByteBuffer? {
        val d = doc ?: return null
        if (modal) {
            post { listener.onMessage("Selesaikan dulu mode yang sedang aktif") }
            return null
        }
        if (strokeActive) commitStroke()
        val full = IRect(0, 0, W, H)
        val sub = substitute
        substitute = null
        compositeInto(d, full, scratch!!, preview!!, bg, if (d.assist != 0) animCurrent(d, framesOf(d)) else -1)
        substitute = sub
        dirty.set(0, 0, W, H)
        return readRegion(scratch!!.fb, full)
    }

    /** Bitmap ekspor dengan ruang warna karya (Display P3 ikut tertanam di PNG/JPG). */
    private fun outBitmap(d: Doc, buf: ByteBuffer, w: Int, h: Int): Bitmap {
        if (d.profile != ColorProfile.P3) return toBitmap(buf, w, h)
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888, true, android.graphics.ColorSpace.get(android.graphics.ColorSpace.Named.DISPLAY_P3))
        buf.position(0)
        bmp.copyPixelsFromBuffer(buf)
        return bmp
    }

    /**
     * Warna keluaran dari hasil susun [src]: karya CMYK diubah seperti hasil cetak (sRGB); karya Display P3 diubah
     * ke sRGB bila [srgb] (GIF/MP4). Mengembalikan [src] atau [tmp] yang berisi hasilnya.
     */
    private fun outputTex(d: Doc, src: Tex, tmp: Tex, srgb: Boolean): Tex {
        val cmyk = d.profile == ColorProfile.CMYK && ensureLut()
        val mat = if (srgb && d.profile == ColorProfile.P3) ColorProfile.matrixGl(ColorProfile.P3, ColorProfile.SRGB) else null
        if (!cmyk && mat == null) return src
        colorPass(src, tmp, if (cmyk) 2 else 1, mat)
        return tmp
    }

    /** Ubah warna seluruh tekstur [src] ke [dst] (ukuran kanvas). [mode] 1 = matriks ruang RGB, 2 = LUT proof. */
    private fun colorPass(src: Tex, dst: Tex, mode: Int, mat: FloatArray?) {
        G.glBindFramebuffer(G.GL_FRAMEBUFFER, dst.fb)
        G.glViewport(0, 0, W, H)
        G.glDisable(G.GL_BLEND)
        G.glDisable(G.GL_SCISSOR_TEST)
        pColor.use()
        pColor.f2("uSize", W.toFloat(), H.toFloat())
        pColor.tex("uSrc", 0, src.id)
        pColor.i("uLut", 5)
        pColor.i("uMode", mode)
        if (mat != null) pColor.m3("uMat", mat)
        if (mode == 2) {
            G.glActiveTexture(G.GL_TEXTURE5)
            G.glBindTexture(G.GL_TEXTURE_3D, proofTex)
        }
        drawRect(pColor, IRect(0, 0, W, H))
        if (mode == 2) {
            G.glActiveTexture(G.GL_TEXTURE5)
            G.glBindTexture(G.GL_TEXTURE_3D, 0)
        }
        G.glActiveTexture(G.GL_TEXTURE0)
        G.glBindTexture(G.GL_TEXTURE_2D, 0)
        G.glBindFramebuffer(G.GL_FRAMEBUFFER, 0)
    }

    /** Siapkan LUT 3D proof CMYK. False bila gagal dimuat. */
    private fun ensureLut(): Boolean {
        if (proofTex != 0) return true
        val bytes = runCatching { ColorProfile.proofLut(ctx) }.getOrNull() ?: return false
        val n = ColorProfile.N
        val buf = ByteBuffer.allocateDirect(bytes.size)
        buf.put(bytes)
        buf.position(0)
        val ids = IntArray(1)
        G.glGenTextures(1, ids, 0)
        G.glActiveTexture(G.GL_TEXTURE5)
        G.glBindTexture(G.GL_TEXTURE_3D, ids[0])
        G.glPixelStorei(G.GL_UNPACK_ALIGNMENT, 1)
        G.glTexImage3D(G.GL_TEXTURE_3D, 0, G.GL_RGB8, n, n, n, 0, G.GL_RGB, G.GL_UNSIGNED_BYTE, buf)
        G.glPixelStorei(G.GL_UNPACK_ALIGNMENT, 4)
        G.glTexParameteri(G.GL_TEXTURE_3D, G.GL_TEXTURE_MIN_FILTER, G.GL_LINEAR)
        G.glTexParameteri(G.GL_TEXTURE_3D, G.GL_TEXTURE_MAG_FILTER, G.GL_LINEAR)
        G.glTexParameteri(G.GL_TEXTURE_3D, G.GL_TEXTURE_WRAP_S, G.GL_CLAMP_TO_EDGE)
        G.glTexParameteri(G.GL_TEXTURE_3D, G.GL_TEXTURE_WRAP_T, G.GL_CLAMP_TO_EDGE)
        G.glTexParameteri(G.GL_TEXTURE_3D, G.GL_TEXTURE_WRAP_R, G.GL_CLAMP_TO_EDGE)
        G.glBindTexture(G.GL_TEXTURE_3D, 0)
        G.glActiveTexture(G.GL_TEXTURE0)
        proofTex = ids[0]
        return true
    }

    /**
     * Ganti profil warna karya. Antara sRGB/CMYK dan Display P3 piksel tiap layer diubah supaya tampilannya tetap
     * (P3 → sRGB memotong warna di luar gamut sRGB); riwayat urungkan dikosongkan. sRGB ↔ CMYK hanya mengganti
     * cara tampil dan ekspor.
     */
    fun setProfile(p: Int, done: (Boolean) -> Unit) {
        val d = doc ?: return
        if (strokeActive || modal || textOn) {
            post { listener.onMessage("Selesaikan dulu mode yang sedang aktif"); done(false) }
            return
        }
        val to = p.coerceIn(0, 2)
        if (to == d.profile) { post { done(true) }; return }
        wetEnd()
        val mat = ColorProfile.matrixGl(d.profile, to)
        if (mat != null) {
            val sc = scratch!!
            for (l in d.layers) {
                colorPass(l.tex, sc, 1, mat)
                blit(sc.fb, 0, 0, W, H, l.tex.fb, 0, 0, W, H, false)
                layerChanged(l, false)
            }
            d.bg = ColorProfile.convert(d.bg, d.profile, to)
            undoStack.clear(); redoStack.clear()
        }
        d.profile = to
        dirty.set(0, 0, W, H)
        mipDirty = true
        postLayers()
        makeThumbs()
        post { listener.onProfile(to); listener.onContentChanged(); done(true) }
        save(null)
    }

    fun exportPsd(file: File): Boolean {
        val d = doc ?: return false
        if (modal) {
            post { listener.onMessage("Selesaikan dulu mode yang sedang aktif") }
            return false
        }
        if (strokeActive) commitStroke()
        val full = IRect(0, 0, W, H)
        compositeInto(d, full, scratch!!, preview!!, if (d.bgVisible) d.bg else Color.WHITE, -1)
        val merged = readRegion(scratch!!.fb, full)
        val out = ArrayList<Psd.LayerOut>()
        if (d.bgVisible) {
            val bgc = d.bg
            out.add(Psd.LayerOut("Latar", 1f, 0, true, false, false) {
                val bb = Buffers.direct(W * H * 4)
                val r = Color.red(bgc).toByte(); val g = Color.green(bgc).toByte(); val b = Color.blue(bgc).toByte()
                for (i in 0 until W * H) { bb.put(r); bb.put(g); bb.put(b); bb.put(255.toByte()) }
                bb.position(0)
                bb
            })
        }
        for ((i, l) in d.layers.withIndex()) {
            val g = l.group
            if (g != 0 && (i == 0 || d.layers[i - 1].group != g)) {
                out.add(Psd.LayerOut("</Layer group>", 1f, 0, true, false, false, section = 3) { Buffers.direct(0) })
            }
            val fb = l.tex.fb
            out.add(Psd.LayerOut(l.name, l.opacity, l.blend, l.visible, l.alphaLock, l.clip) { readRegion(fb, full) })
            if (g != 0 && (i == d.layers.size - 1 || d.layers[i + 1].group != g)) {
                val meta = d.groups[g] ?: GroupData("Grup", true, false)
                out.add(Psd.LayerOut(meta.name, 1f, 0, meta.visible, false, false, section = if (meta.collapsed) 2 else 1) { Buffers.direct(0) })
            }
        }
        Psd.write(file, W, H, out, merged, d.dpi, if (d.profile == ColorProfile.P3) ColorProfile.iccDisplayP3 else null)
        dirty.set(0, 0, W, H)
        return true
    }

    // ================= pratinjau kuas =================

    /** Lebar dan tinggi bagian bergambar pada ujung kuas impor (pecahan 0..1 dari sisi persegi). */
    private fun tipExtent(name: String): Pair<Float, Float>? {
        val (d, n) = BrushImport.readSquare(name) ?: return null
        var x0 = n; var x1 = -1; var y0 = n; var y1 = -1
        for (y in 0 until n) {
            val r = y * n
            for (x in 0 until n) {
                if ((d[r + x].toInt() and 0xFF) > 8) {
                    if (x < x0) x0 = x
                    if (x > x1) x1 = x
                    if (y < y0) y0 = y
                    y1 = y
                }
            }
        }
        if (x1 < 0) return null
        return (x1 - x0 + 1f) / n to (y1 - y0 + 1f) / n
    }

    fun brushPreview(b: Brush, dual: Brush? = null) {
        val w = 420
        val h = 96
        if (prevTex == null) prevTex = Tex(w, h)
        val t = prevTex!!
        clearRect(t, IRect(0, 0, w, h))
        val list = DabList()
        val pb = b.copy(streamline = 0f, stabilization = 0f, motionFilter = 0f)
        // Kuas stempel (gambar impor dengan jarak cap lebar): pratinjau satu cap utuh, dipaskan ke kotak pratinjau.
        if (b.stamp && b.tipFile.isNotEmpty()) {
            val ext = tipExtent(b.tipFile)
            if (ext != null) {
                val size = min(w * 0.92f / ext.first, h * 0.86f / ext.second)
                previewProc.begin(pb, size, Color.WHITE, pressure = false, tilt = false, pressureGamma = 1f, spacing = pb.spacing, maxDiameter = 4000f)
                previewProc.add(w / 2f, h / 2f, 1f, 0f, 0f, 0L, list)
                previewProc.end(list)
                renderDabs(t, list, pb, 0f, 0f, perDab = !pb.grainStroke)
                val bmp = toBitmap(previewGrain(readRegion(t.fb, IRect(0, 0, w, h)), w, h, pb), w, h)
                val id = b.id
                post { listener.onBrushPreview(id, bmp) }
                return
            }
        }
        val size = (b.sizeMax * 0.32f).coerceIn(3f, h * 0.34f)
        fun feed(l: DabList) {
            val n = 70
            for (i in 0..n) {
                val f = i.toFloat() / n
                val x = 22f + f * (w - 44f)
                val y = h / 2f + sin(f * 2f * Math.PI.toFloat()) * h * 0.22f
                // sin(PI) dalam float sedikit negatif; pangkat pecahan dari bilangan negatif = NaN.
                val p = max(0.06f, sin(f * Math.PI.toFloat()).coerceAtLeast(0f).pow(0.8f))
                previewProc.add(x, y, p, 0f, 0f, i * 8L, l)
            }
            previewProc.end(l)
        }
        previewProc.begin(pb, size, Color.WHITE, pressure = true, tilt = false, pressureGamma = 1f, spacing = pb.spacing, maxDiameter = 400f)
        feed(list)
        renderDabs(t, list, pb, 0f, 0f, perDab = !pb.grainStroke)
        var buf = readRegion(t.fb, IRect(0, 0, w, h))
        if (dual != null) {
            // Kuas ganda: goresan kuas kedua di jalur yang sama menjadi topeng.
            if (prevTex2 == null) prevTex2 = Tex(w, h)
            val t2 = prevTex2!!
            clearRect(t2, IRect(0, 0, w, h))
            val pd = dual.copy(streamline = 0f, stabilization = 0f, motionFilter = 0f, stamp = false)
            val l2 = DabList()
            previewProc.begin(pd, size * b.dualSize.coerceIn(0.1f, 4f), Color.WHITE, pressure = true, tilt = false, pressureGamma = 1f, spacing = pd.spacing, maxDiameter = 400f)
            feed(l2)
            renderDabs(t2, l2, pd, 0f, 0f)
            val m = readRegion(t2.fb, IRect(0, 0, w, h))
            for (i in 0 until w * h) {
                val a = (buf.get(i * 4 + 3).toInt() and 0xFF) / 255f
                val q = (m.get(i * 4 + 3).toInt() and 0xFF) / 255f
                val c = when (b.dualMode) {
                    0 -> a * q
                    1 -> a + q - a * q
                    2 -> a * (1f - q)
                    3 -> abs(a - q)
                    4 -> min(a, q)
                    else -> max(a, q)
                }
                val v = (c * 255f + 0.5f).toInt().toByte()
                for (k in 0 until 4) buf.put(i * 4 + k, v)
            }
            buf.position(0)
        }
        // Rendering: intensitas glaze dan ambang alfa (pratinjau putih: semua kanal = alfa).
        val rk = when (b.render) { 2 -> 1.15f; 3 -> 1.6f; else -> 1f }
        if (rk != 1f || b.alphaThreshold > 0f) {
            for (i in 0 until w * h) {
                var a = (buf.get(i * 4 + 3).toInt() and 0xFF) / 255f
                if (rk != 1f) a = 1f - (1f - a).pow(rk)
                if (b.alphaThreshold > 0f) a = if (a >= b.alphaThreshold) 1f else 0f
                val v = (a * 255f + 0.5f).toInt().toByte()
                for (k in 0 until 4) buf.put(i * 4 + k, v)
            }
            buf.position(0)
        }
        val bmp = toBitmap(previewWet(previewGrain(buf, w, h, pb), w, h, pb, size), w, h)
        val id = b.id
        post { listener.onBrushPreview(id, bmp) }
    }

    private var noiseCpu: ByteArray? = null

    /** Pratinjau kuas cat air: olahan yang sama dengan di kanvas (tepi tak rata, tepi gelap, bercak), dihitung di CPU. */
    private fun previewWet(buf: ByteBuffer, w: Int, h: Int, b: Brush, size: Float): ByteBuffer {
        if (!b.watercolor) return buf
        val nzt = noiseCpu ?: Paper.noiseTile().also { noiseCpu = it }
        val k = (size / 90f).coerceIn(0.6f, 4f)
        fun nz(x: Float, y: Float, scale: Float, off: Float): Float {
            val u = x / (scale * k) + off
            val v = y / (scale * k) + off * 0.7f
            val xi = Math.floorMod(kotlin.math.floor(u).toInt(), 256)
            val yi = Math.floorMod(kotlin.math.floor(v).toInt(), 256)
            return (nzt[yi * 256 + xi].toInt() and 0xFF) / 255f
        }
        val a = FloatArray(w * h) { (buf.get(it * 4 + 3).toInt() and 0xFF) / 255f }
        // Cat air hidup: pratinjau menampilkan hasil akhir rembesan (sebaran menipis menjauhi sapuan, tak rata).
        val live = b.wcSpread > 0.001f
        if (live) {
            val loss = 1f / (size * 0.8f * b.wcSpread + 4f * b.wcSpread).coerceIn(2f, 200f)
            val res = FloatArray(w * h) {
                val x = (it % w).toFloat()
                val y = (it / w).toFloat()
                (0.35f + 1.1f * nz(x, y, 10f, 5f) + 0.45f * nz(x, y, 3.1f, 9f)).coerceIn(0.25f, 2f) * loss
            }
            fun dec(v: Float, l: Float) = max(l * max(v, 0.25f), 1f / 255f)
            val dg = 1.4142f
            for (y in 0 until h) for (x in 0 until w) {
                val i = y * w + x
                val l = res[i]
                var v = a[i]
                if (x > 0) v = max(v, a[i - 1] - dec(a[i - 1], l))
                if (y > 0) {
                    v = max(v, a[i - w] - dec(a[i - w], l))
                    if (x > 0) v = max(v, a[i - w - 1] - dec(a[i - w - 1], l * dg))
                    if (x < w - 1) v = max(v, a[i - w + 1] - dec(a[i - w + 1], l * dg))
                }
                a[i] = v
            }
            for (y in h - 1 downTo 0) for (x in w - 1 downTo 0) {
                val i = y * w + x
                val l = res[i]
                var v = a[i]
                if (x < w - 1) v = max(v, a[i + 1] - dec(a[i + 1], l))
                if (y < h - 1) {
                    v = max(v, a[i + w] - dec(a[i + w], l))
                    if (x < w - 1) v = max(v, a[i + w + 1] - dec(a[i + w + 1], l * dg))
                    if (x > 0) v = max(v, a[i + w - 1] - dec(a[i + w - 1], l * dg))
                }
                a[i] = v
            }
        }
        fun cov(x: Int, y: Int): Float {
            if (x < 0 || y < 0 || x >= w || y >= h) return 0f
            val av = a[y * w + x]
            if (av < 0.003f) return 0f
            val e = ((nz(x.toFloat(), y.toFloat(), 30f, 31f) - 0.5f) * 0.85f + (nz(x.toFloat(), y.toFloat(), 6f, 53f) - 0.5f) * 0.45f) * (if (live) 0.35f else 1f)
            val ae = av + e * (1.2f - av).coerceIn(0f, 1f)
            val wd = 0.05f + b.wcSoft * 0.3f
            val th = if (live) 0.14f else 0.35f
            val t = ((ae - (th - wd)) / (2f * wd)).coerceIn(0f, 1f)
            return t * t * (3f - 2f * t)
        }
        val rr = 2
        for (y in 0 until h) for (x in 0 until w) {
            val c = cov(x, y)
            val i = (y * w + x) * 4
            if (c <= 0f) {
                for (q in 0 until 4) buf.put(i + q, 0)
                continue
            }
            val avg = (cov(x - rr, y) + cov(x + rr, y) + cov(x, y - rr) + cov(x, y + rr) +
                cov(x - rr, y - rr) + cov(x + rr, y + rr) + cov(x - rr, y + rr) + cov(x + rr, y - rr)) / 8f
            val rim = ((c - avg) * 2f).coerceIn(0f, 1f)
            val mott = (1f + b.wcBloom * (nz(x.toFloat(), y.toFloat(), 90f, 11f) - 0.5f) * 1.6f)
            val gran = 1f + b.wcGrain * (0.5f - nz(x.toFloat(), y.toFloat(), 3f, 77f)) * 1.2f
            val dl = if (live) {
                val t = ((a[y * w + x] - 0.14f) / 0.66f).coerceIn(0f, 1f)
                0.35f + 0.65f * t * t * (3f - 2f * t)
            } else 1f
            val al = (c * (b.wcDensity * dl * mott * gran + b.wcEdge * rim * 0.45f)).coerceIn(0f, 1f)
            val v = (al * 255f + 0.5f).toInt().toByte()
            buf.put(i, v); buf.put(i + 1, v); buf.put(i + 2, v); buf.put(i + 3, v)
        }
        buf.position(0)
        return buf
    }

    /** Pratinjau kuas bertekstur per goresan: tekstur dikenakan pada gambar goresan jadi (sama seperti di kanvas). */
    private fun previewGrain(buf: ByteBuffer, w: Int, h: Int, b: Brush): ByteBuffer {
        if (!b.grainStroke || b.grainStrength <= 0f) return buf
        val (g, n) = (if (b.grainFile.isNotEmpty()) BrushImport.readSquare(b.grainFile) else null)
            ?: if (b.grain != Grain.NONE) Procedural.grain(b.grain) to 512 else return buf
        val scale = 512f * max(0.05f, b.grainScale)
        val k = n / scale
        for (y in 0 until h) {
            val gy = (((y + 0.5f) * k).toInt() % n + n) % n
            for (x in 0 until w) {
                val gx = (((x + 0.5f) * k).toInt() % n + n) % n
                val v = (g[gy * n + gx].toInt() and 0xFF) / 255f
                val f = 1f + (v - 1f) * b.grainStrength
                val i = (y * w + x) * 4
                for (c in 0 until 4) buf.put(i + c, ((buf.get(i + c).toInt() and 0xFF) * f + 0.5f).toInt().coerceIn(0, 255).toByte())
            }
        }
        buf.position(0)
        return buf
    }
}
