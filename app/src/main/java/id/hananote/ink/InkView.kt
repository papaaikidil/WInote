package id.hananote.ink

import android.annotation.SuppressLint
import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Rect
import android.graphics.Color
import android.graphics.DashPathEffect
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PointF
import android.graphics.PorterDuff
import android.graphics.RectF
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.os.SystemClock
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import androidx.input.motionprediction.MotionEventPredictor
import id.hananote.data.ImageItem
import id.hananote.data.InputMode
import id.hananote.data.LinkItem
import id.hananote.data.StickyItem
import id.hananote.data.PageContent
import id.hananote.data.CrashLog
import id.hananote.data.PageBackground
import id.hananote.data.PageItem
import id.hananote.data.PageMeta
import id.hananote.data.PaperSize
import id.hananote.data.ShapeKind
import id.hananote.data.Stroke
import id.hananote.data.StrokeBuilder
import id.hananote.data.TextItem
import id.hananote.data.Tool
import java.io.File
import id.hananote.data.TextLists
import id.hananote.data.EraserMode
import id.hananote.data.TableItem
import id.hananote.data.TapeItem
import id.hananote.data.TapeShape
import id.hananote.data.Align
import id.hananote.data.MindMapItem
import id.hananote.data.MindMaps
import id.hananote.data.MindNode
import id.hananote.data.layerZ
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

/** Ringkasan benda yang sedang dipilih, untuk bilah aksi di atas kanvas. */
class SelectionInfo(
    val kind: String,
    val url: String?,
    val text: String?,
    val sticky: Boolean = false,
    val image: Boolean = false,
    val rotatable: Boolean = false,
    /** Perataan teks yang dipilih (teks ketik atau isi stiker), null bila tidak berlaku. */
    val align: id.hananote.data.Align? = null,
    val mindMap: Boolean = false,
    val audio: Boolean = false,
    /** Lebih dari satu objek (atau tulisan tangan) dipilih. */
    val multi: Boolean = false,
    /** Semua yang dipilih adalah satu kelompok. */
    val grouped: Boolean = false,
    /** Ada benda atau teks yang bisa dimajukan/dimundurkan. */
    val arrangeable: Boolean = false,
    /** Jenis daftar (poin/nomor) teks yang dipilih, null bila tidak berlaku. */
    val list: TextLists.Kind? = null,
    /** Satu tabel dipilih (bisa diatur ukuran kolom dan barisnya). */
    val table: Boolean = false,
    /** Ada coretan atau teks terpilih yang bisa diganti warnanya. */
    val recolor: Boolean = false,
    /** Ada tulisan tangan terpilih yang bisa diubah jadi teks. */
    val ink: Boolean = false,
    /** Ukuran huruf teks atau isi stiker yang dipilih (poin), null bila tidak berlaku. */
    val textSize: Float? = null,
)

/**
 * Kanvas tulis tangan.
 *
 * Supaya lancar: isi halaman yang sudah selesai digambar sekali ke bitmap cadangan, sehingga tiap
 * bingkai hanya menggambar ulang goresan yang sedang ditulis. Saat menggeser dan memperbesar,
 * bitmap cadangan ditransformasi langsung lalu digambar ulang tajam setelah jari diangkat.
 *
 * Pena aktif selalu menulis. Jari menulis hanya bila [fingerDraws] aktif; kalau tidak, jari
 * dipakai untuk menggeser (satu jari) dan memperbesar (dua jari). Sentuhan telapak tangan
 * diabaikan saat pena sedang menulis atau melayang di atas layar.
 *
 * Mode [InputMode.SHAPE] menggambar bentuk, [InputMode.SELECT] memilih, menggeser, dan mengubah
 * ukuran benda. Penggaris bisa digeser dengan satu jari dan diputar dengan dua jari; goresan yang
 * dimulai di dekat tepinya akan lurus mengikuti tepi itu.
 */
@SuppressLint("ViewConstructor", "ClickableViewAccessibility")
class InkView(context: Context) : View(context) {

    // ================= pengaturan dari editor =================

    var tool: Tool = Tool.PEN
    var color: Int = 0xFF1F2430.toInt()
    var baseWidth: Float = 2f
    var eraserWidth: Float = 12f
    /** Cara penghapus bekerja: kuas halus, sebagian, atau goresan utuh. */
    var eraserMode: EraserMode = EraserMode.PARTIAL
    /** Kekuatan kuas penghapus halus (0..1): makin besar makin cepat bersih. */
    var eraserStrength: Float = 0.35f
    /** Penghapus sebagian/utuh ikut menghapus gambar (bagian yang tersapu, atau gambar utuh). */
    var eraseImages: Boolean = true
    var fingerDraws: Boolean = true
    var shapeKind: ShapeKind = ShapeKind.RECT
    /** Bentuk selotip hafalan yang ditempel dengan alat Selotip. */
    var tapeShape: TapeShape = TapeShape.STRAIGHT
    var imageDir: File? = null
    var accentColor: Int = 0xFF6E3A5E.toInt()
        set(value) {
            if (field != value) { field = value; invalidate() }
        }
    var deskColor: Int = 0xFFE6E7EB.toInt()
        set(value) {
            if (field != value) { field = value; invalidateViewCache() }
        }
    var inputMode: InputMode = InputMode.INK
        set(value) {
            if (field != value) {
                field = value
                if (value != InputMode.SELECT) pickedForMove = false
                if (value != InputMode.SELECT) clearSelection()
                invalidate()
            }
        }
    var rulerVisible: Boolean = false
        set(value) {
            if (field != value) {
                field = value
                if (value) placeRuler()
                invalidate()
            }
        }

    var onStrokesChanged: (() -> Unit)? = null
    var onHistoryChanged: ((canUndo: Boolean, canRedo: Boolean) -> Unit)? = null
    var onStylusSeen: (() -> Unit)? = null
    var onSelection: ((SelectionInfo?) -> Unit)? = null
    var onOpenLink: ((String) -> Unit)? = null
    /** Dipanggil saat goresan tulisan mulai ditulis dan saat selesai (untuk teks otomatis). */
    var onStrokeStarted: (() -> Unit)? = null
    var onStrokeAdded: ((Stroke) -> Unit)? = null
    /** Teks ketik diketuk: buka penyunting keyboard. */
    /** Teks diketuk saat menulis: teksnya dan posisi huruf yang diketuk. */
    var onTextTapped: ((TextItem, Int) -> Unit)? = null
    /** Rekaman suara diketuk: putar atau hentikan. */
    var onAudioTapped: ((id.hananote.data.AudioItem) -> Unit)? = null

    // ---- rekaman yang sedang diputar ----
    private var playingFile: String? = null
    private var playProgress = 0f

    /** Tandai rekaman yang sedang diputar beserta kemajuannya (0..1), atau null bila tidak ada. */
    fun setPlayback(file: String?, progress: Float) {
        if (file == playingFile && kotlin.math.abs(progress - playProgress) < 0.002f) return
        playingFile = file
        playProgress = progress
        invalidate()
    }

    private fun audioAt(px: Float, py: Float): id.hananote.data.AudioItem? {
        for (i in items.indices.reversed()) {
            val it = items[i]
            if (it is id.hananote.data.AudioItem && px >= it.x && px <= it.x + it.w && py >= it.y && py <= it.y + it.h) return it
        }
        return null
    }

    // ================= isi halaman =================

    var page: PageMeta? = null
        private set
    private var strokes: MutableList<Stroke> = mutableListOf()
    private var texts: MutableList<TextItem> = mutableListOf()
    private var items: MutableList<PageItem> = mutableListOf()

    private val density = resources.displayMetrics.density
    private val margin = 20f * density
    private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop.toFloat()

    // ---- transformasi halaman -> layar ----
    private var scale = 1f
    private var tx = 0f
    private var ty = 0f
    /** Putaran halaman di layar (derajat), diputar dengan dua jari. */
    private var rot = 0f
    private var fitScale = 1f
    private var needsFit = true
    private val viewMatrix = Matrix()
    private val inverseMatrix = Matrix()
    private val pt = FloatArray(2)
    private val boxMatrix = Matrix()

    // ---- bitmap cadangan ----
    private var cache: Bitmap? = null
    private var cacheCanvas: Canvas? = null
    private var cacheValid = false
    private val cacheInverse = Matrix()
    /** Posisi dan skala halaman saat gambar cadangan dibuat. */
    private val cacheMatrix = Matrix()
    private val drawMatrix = Matrix()
    private val visible = RectF()
    private val box = RectF()
    private val renderPaint = Paint()
    private val livePaint = Paint()
    private val itemPaint = Paint()
    private val renderPath = Path()
    private val livePath = Path()

    /**
     * Perkiraan gerak pena. Layar selalu menampilkan sentuhan yang sudah sedikit lewat; dengan menggambar
     * ekor pendek ke arah pena bergerak, ujung tulisan terasa menempel pada ujung pena.
     */
    private var predictor: MotionEventPredictor? = null
    private var predictorTried = false
    private var lastInkMove = 0L
    private val tailXs = FloatArray(12)
    private val tailYs = FloatArray(12)
    private val tailPs = FloatArray(12)
    private val bitmapPaint = Paint(Paint.FILTER_BITMAP_FLAG)

    /** Naik setiap isi halaman berubah; hasil gambar di latar yang dibuat dari isi lama dibuang. */
    private var contentVersion = 0L
    /**
     * Gambar cadangan masih benar isinya, tapi untuk posisi/skala sebelum digeser. Sementara gambar yang tajam
     * untuk posisi baru disiapkan di latar, yang lama ditampilkan mengikuti posisi baru (seperti saat menggeser).
     */
    private var viewStale = false
    @Volatile private var viewToken = 0

    /**
     * Seluruh halaman dalam satu gambar (resolusinya dibatasi), disiapkan di latar. Saat halaman digeser atau
     * diperbesar, gambar ini menjadi alas di bawah gambar cadangan layar, jadi bagian yang baru masuk layar
     * langsung tampil berisi, bukan kosong lalu muncul setelah jari diangkat.
     */
    private var backdrop: Bitmap? = null
    /** Alas lama yang sudah tidak ditampilkan, dipakai ulang supaya tidak terus-menerus memesan memori besar. */
    private var backdropSpare: Bitmap? = null
    private var backdropPage: String? = null
    private var backdropScale = 0f
    /** [contentVersion] saat alas dibuat: sama berarti alas persis sama dengan isi halaman sekarang. */
    private var backdropVersion = -1L
    /** Alas yang sedang digambar di latar (versi isi dan halamannya), supaya tidak dimulai ulang percuma. */
    private var backdropInFlight = -1L
    private var backdropInFlightPage: String? = null
    @Volatile private var backdropToken = 0
    private val backdropPaint = Paint(Paint.FILTER_BITMAP_FLAG)
    private val backdropMatrix = Matrix()
    private val backdropBudget = min(9_000_000L, Runtime.getRuntime().maxMemory() / 40L).toFloat()
    private val backdropJob = Runnable { requestBackdrop() }
    private val framePaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val cursorPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 1.5f * density
        color = 0x99707480.toInt()
    }
    private val selPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 1.5f * density
        pathEffect = DashPathEffect(floatArrayOf(6f * density, 4f * density), 0f)
    }
    private val handlePaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val rulerPaint = Paint(Paint.ANTI_ALIAS_FLAG)

    // ---- riwayat urungkan ----
    /** Satu langkah yang bisa diurungkan: isi yang dihapus (beserta posisinya) dan yang ditambah. */
    private class Change(
        val removedStrokes: List<Pair<Int, Stroke>> = emptyList(),
        val addedStrokes: List<Stroke> = emptyList(),
        val removedTexts: List<Pair<Int, TextItem>> = emptyList(),
        val addedTexts: List<TextItem> = emptyList(),
        val removedItems: List<Pair<Int, PageItem>> = emptyList(),
        val addedItems: List<PageItem> = emptyList(),
        /** Posisi lapisan yang ditambah, supaya ulangi menaruhnya di tempat yang sama (null: paling atas). */
        val addedStrokesAt: List<Int>? = null,
        val addedTextsAt: List<Int>? = null,
        val addedItemsAt: List<Int>? = null,
        /** Perubahan lapisan (maju/mundur): objek, z sebelum, z sesudah. */
        val zChange: List<Triple<Any, Long, Long>>? = null,
        /** Perubahan kelompok: objek, kelompok sebelum, kelompok sesudah. */
        val regroup: List<Triple<Any, Long, Long>>? = null,
    ) {
        val isEmpty: Boolean get() = removedStrokes.isEmpty() && addedStrokes.isEmpty() && removedTexts.isEmpty() &&
            addedTexts.isEmpty() && removedItems.isEmpty() && addedItems.isEmpty() && zChange == null && regroup == null
    }
    private val undoStack = ArrayList<Change>()
    private val redoStack = ArrayList<Change>()
    /** Hapusan gambar yang masih disimpan di latar; urungkan/ulangi menunggu sampai selesai supaya urutannya benar. */
    private var bakesPending = 0
    private val afterBake = ArrayList<Runnable>()

    // ---- sentuhan ----
    private enum class Mode { NONE, DRAW, ERASE, SHAPE, PAN, ZOOM, IGNORE, RULER, SELECT, TURN, TYPESEL, TYPEOUT, MAPNODE, MAPBLANK, CHECKTAP, TABLELINE, TAPE, TAPETAP }
    private var mode = Mode.NONE
    private var activePointer = -1
    private var activeIsFinger = false
    private var builder: StrokeBuilder? = null
    private var smoothP = 0.5f
    private var erased = ArrayList<Pair<Int, Stroke>>()
    private var erasedTexts = ArrayList<Pair<Int, TextItem>>()
    /** Potongan goresan yang tersisa dari penghapus sebagian dalam satu sapuan. */
    private var erasePieces = LinkedHashSet<Stroke>()
    /** Gabungan kotak goresan yang terpotong dalam sapuan ini (untuk gambar ulang akhir yang rapi). */
    private val eraseCutBounds = RectF()
    private var eraseCutEmpty = true
    /** Gambar yang dihapus utuh dalam satu sapuan (mode goresan utuh). */
    private var erasedItems = ArrayList<Pair<Int, PageItem>>()
    /** Jejak penghapus sebagian di atas gambar dalam sapuan ini (satuan halaman), dan gambar yang tersentuh. */
    private var eraseImgPath: Path? = null
    private var eraseImgWidth = 0f
    private val eraseImgHit = LinkedHashSet<ImageItem>()
    /** Bekas penghapus di gambar yang sedang disimpan ke berkas gambar baru. */
    private val imageMasks = HashMap<PageItem, MutableList<InkRenderer.EraseMask>>()
    private var eraseLastX = Float.NaN
    private var eraseLastY = Float.NaN
    /** Posisi penghapus terakhir yang diminta (belum tentu sudah diproses). */
    private var eraseReqX = Float.NaN
    private var eraseReqY = Float.NaN
    private val erasePieceSet = HashSet<Stroke>()
    /** Bagian halaman yang berubah oleh penghapus sejak digambar terakhir (satuan halaman). */
    private val eraseDirty = RectF()
    private var eraseDirtyEmpty = true
    /** Ada perubahan dalam sapuan ini (dikabarkan ke editor sekali saja saat pena diangkat). */
    private var eraseChanged = false
    private val regionRect = RectF()
    private val regionClip = Rect()
    private var showEraser = false
    private var eraserX = 0f
    private var eraserY = 0f
    private var lastStylusHover = 0L

    private var gestureActive = false
    /**
     * Tinggi tampilan sedang berubah terus (misalnya keyboard sedang naik/turun): gambar halaman yang sudah
     * ada dipakai dengan transformasi, dan baru digambar ulang penuh setelah ukurannya tenang. Tanpa ini,
     * tiap bingkai animasi keyboard menggambar ulang seluruh halaman sehingga keyboard naik tersendat.
     */
    private var settling = false
    private val settle = Runnable {
        if (settling) {
            settling = false
            invalidateViewCache()
        }
    }

    /** Selesaikan masa tenang sekarang juga (sebelum gambar cadangan dipakai apa adanya). */
    private fun settleNow() {
        if (!settling) return
        removeCallbacks(settle)
        settling = false
        cacheValid = false
    }
    private var transformDirty = false
    private var panMoved = false
    private var downX = 0f
    /** Posisi jari terakhir saat menggeser, dan kapan geseran dimulai (untuk mengenali sapuan ganti halaman). */
    private var panLastX = 0f
    private var panLastY = 0f
    private var panStartTime = 0L

    /** Sapuan ke kiri (+1, halaman berikutnya) atau ke kanan (-1, halaman sebelumnya) di tepi halaman. */
    var onSwipePage: ((Int) -> Unit)? = null

    /**
     * Tahan tekan di kanvas: tampilkan pilihan salin/potong/tempel. [vx]/[vy] titik di layar (koordinat kanvas),
     * [px]/[py] titik di halaman, [onObject] true bila ada objek yang dipilih di titik itu.
     */
    var onContextMenu: ((vx: Float, vy: Float, px: Float, py: Float, onObject: Boolean) -> Unit)? = null
    private var lpX = 0f
    private var lpY = 0f
    private var lpPointer = -1
    private val longPress = Runnable { fireLongPress() }

    private fun armLongPress(e: MotionEvent, i: Int) {
        lpX = e.getX(i)
        lpY = e.getY(i)
        lpPointer = e.getPointerId(i)
        removeCallbacks(longPress)
        postDelayed(longPress, ViewConfiguration.getLongPressTimeout().toLong())
    }

    private fun disarmLongPress() {
        if (lpPointer < 0) return
        lpPointer = -1
        removeCallbacks(longPress)
    }

    /** Masih ditahan di tempat yang sama: batalkan laso atau geser, lalu tawarkan pilihan salin/tempel. */
    private fun fireLongPress() {
        if (lpPointer < 0 || page == null) return
        lpPointer = -1
        when (mode) {
            Mode.SELECT -> {
                if (dragging) return
                lasso = null
                lassoTap = null
            }
            Mode.PAN -> endPanZoom()
            else -> return
        }
        mode = Mode.IGNORE
        val pp = toPage(lpX, lpY)
        var onObject = hasSelection && insideSelection(pp.x, pp.y, 6f / scale)
        if (!onObject) {
            val hit = hitTest(pp.x, pp.y)
            if (hit != null) {
                select(hit)
                onObject = true
            }
        }
        performHapticFeedback(android.view.HapticFeedbackConstants.LONG_PRESS)
        invalidate()
        onContextMenu?.invoke(lpX, lpY, pp.x, pp.y, onObject)
    }

    // ---- animasi membalik halaman ----
    private var turnSnap: Bitmap? = null
    private val turnRect = RectF()
    private val turnNow = RectF()
    private var turnDir = 0
    private var turnArmed = false
    private var turnStart = 0L
    private var turnAsked = 0L
    private val turnCamera = android.graphics.Camera()
    private val turnMatrix = Matrix()
    private val turnPaint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG)
    private val turnShade = Paint()
    private val turnPts = FloatArray(4)
    private val turnDuration = 460L
    /** Lembaran melengkung (Android 10 ke atas: butuh drawVertices di akselerasi perangkat keras). */
    private val canCurl = android.os.Build.VERSION.SDK_INT >= 29
    private val curlDuration = 640L
    private val curl = CurlMesh()
    /** Gambar halaman yang dibalik (dengan tepi 1 px transparan supaya tepinya halus) dan kotaknya di layar. */
    private var curlSheet: Bitmap? = null
    private var curlTried = false
    private val curlSheetRect = RectF()
    private var curlPaper = Color.WHITE
    private var turnPaper = Color.WHITE
    private val curlFrontPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val curlBackPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val curlGradPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    /** Tekstur lembaran yang dibalik, dan warna kertas pucat untuk punggungnya. */
    private var curlFrontTex: android.graphics.Shader? = null
    private var curlBackTex: android.graphics.Shader? = null
    private val shadeU = FloatArray(64)
    private val shadeColors = IntArray(64)
    private val shadePos = FloatArray(64)
    /** Halaman baru dipasang: gambar pertamanya menunggu foto/PDF terbuka supaya tidak muncul belakangan. */
    private var freshPage = false
    private val curlClip = RectF()
    private var downY = 0f
    private var startTx = 0f
    private var startTy = 0f
    private var startScale = 1f
    private var startDist = 1f
    private var startFx = 0f
    private var startFy = 0f
    private var focusX = 0f
    private var focusY = 0f
    private var focusDist = 1f

    // ---- selotip hafalan ----
    private var tapeX0 = 0f
    private var tapeY0 = 0f
    private var tapeX1 = 0f
    private var tapeY1 = 0f
    /** Jalur selotip bebas (x, y bergantian, satuan halaman) dan panjangnya. */
    private var tapePts = FloatArray(256)
    private var tapeN = 0
    private var tapeLen = 0f
    /** Selotip yang disentuh pena saat menulis: diketuk = buka/tutup, digeser = menulis seperti biasa. */
    private var tapeHit: TapeItem? = null
    private var tapeDownP = 0.5f
    private var tapeDownT = 0L
    private val tapePaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val tapeBox = RectF()

    // ---- bentuk ----
    private var shapeX0 = 0f
    private var shapeY0 = 0f
    private var shapeX1 = 0f
    private var shapeY1 = 0f

    // ---- penggaris (koordinat layar) ----
    private var rulerCx = 0f
    private var rulerCy = 0f
    private var rulerAngle = 0f // radian
    private val rulerThickness get() = 76f * density
    private val rulerSnap get() = 26f * density
    private var rulerEdge: Float? = null // jarak tepi dari garis tengah penggaris, bila goresan sedang menempel
    private var rulerStartCx = 0f
    private var rulerStartCy = 0f
    private var rulerStartAngle = 0f
    private var rulerTwoFinger = false
    private var rulerStartVecAngle = 0f

    // ---- pilihan ----
    /** Yang sedang dipilih: benda, teks, dan goresan bebas. Satu benda atau satu teks saja = pilihan tunggal. */
    private var selItems: List<PageItem> = emptyList()
    private var selTexts: List<TextItem> = emptyList()
    private var selStrokes: List<Stroke> = emptyList()
    private val hasSelection: Boolean get() = selItems.isNotEmpty() || selTexts.isNotEmpty() || selStrokes.isNotEmpty()
    private val isSingle: Boolean get() = selStrokes.isEmpty() && selItems.size + selTexts.size == 1
    private val selItem: PageItem? get() = if (isSingle) selItems.firstOrNull() else null
    private val selText: TextItem? get() = if (isSingle) selTexts.firstOrNull() else null
    /** Kotak pilihan: untuk satu benda, kotaknya sebelum diputar; untuk pilihan banyak, kotak gabungannya. */
    private val selRect = RectF()
    /** Garis laso yang sedang digambar (x, y bergantian, satuan halaman), null bila tidak ada. */
    private var lasso: ArrayList<Float>? = null
    /** Coretan yang tersentuh saat laso dimulai: dipilih bila ternyata hanya diketuk. */
    private var lassoTap: Stroke? = null
    private val lassoFill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val lassoLine = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    private val lassoPath = Path()
    private var dragging = false
    /** -1 geser, 0 kiri-atas, 1 kanan-atas, 2 kanan-bawah, 3 kiri-bawah, 4 pegangan putar. */
    private var dragCorner = -1
    private var dragStartX = 0f
    private var dragStartY = 0f
    private val dragFrom = RectF()
    private val dragTo = RectF()
    private var dragRot0 = 0f
    private var dragRot1 = 0f
    private var dragAngle0 = 0f
    private var dragOffX = 0f
    private var dragOffY = 0f
    private val liftMatrix = Matrix()
    private val local = FloatArray(2)
    private val corners = FloatArray(8)
    private val handlePos = FloatArray(4) // titik tengah sisi atas, lalu pegangan putar (layar)
    /** Pegangan samping kotak teks (kanan, lalu kiri) di layar, untuk mengubah lebar tanpa mengubah ukuran huruf. */
    private val sidePos = FloatArray(4)
    /** Teks dengan lebar baru yang sedang diseret lewat pegangan samping. */
    private var widthPreview: TextItem? = null
    private val selPath = Path()
    private val anglePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER }

    // ---- pena melayang (untuk menentukan kapan selesai menulis) ----
    private var penHovering = false
    private var lastHoverEvent = 0L
    /** Perangkat ini melaporkan pena yang melayang di atas layar. */
    var penHoverSupported = false
        private set

    /** Benda yang sedang digeser, sementara dikeluarkan dari halaman supaya bisa digambar bergerak. */
    private class Lifted(
        val items: List<Pair<Int, PageItem>>,
        val texts: List<Pair<Int, TextItem>>,
        val strokes: List<Pair<Int, Stroke>>,
    )
    private var lifted: Lifted? = null

    // ================= API untuk editor =================

    fun setPage(p: PageMeta, content: PageContent) {
        finishTextAnim()
        typeTarget?.end(commit = true)
        endMapSession()
        cancelInteraction()
        clearSelection()
        page = p
        strokes = content.strokes
        texts = content.texts
        items = content.items
        imageMasks.clear()
        afterBake.clear()
        if (turnDrag != null) clearTurnDrag()
        if (turnSnap != null) turnArmed = true
        zTop = maxOf(strokes.maxOfOrNull { it.z } ?: 0L, texts.maxOfOrNull { it.z } ?: 0L, items.maxOfOrNull { it.z } ?: 0L)
        undoStack.clear(); redoStack.clear()
        notifyHistory()
        needsFit = true
        freshPage = true
        invalidateCache()
    }

    fun updatePageMeta(p: PageMeta) {
        page = p
        needsFit = true
        invalidateCache()
    }

    /** Goresan tulisan (tanpa stabilo dan bentuk) untuk dibaca jadi teks, sesuai urutan menulis. */
    fun inkStrokes(): List<Stroke> = strokes.filter { it.tool.isWriting && it.size > 0 }

    /** Goresan tulisan yang sedang dipilih, sesuai urutan menulis. */
    fun selectedInkStrokes(): List<Stroke> {
        if (selStrokes.isEmpty()) return emptyList()
        val set = selStrokes.toHashSet()
        return strokes.filter { it in set && it.tool.isWriting && it.size > 0 }
    }

    /** Dari daftar goresan, yang masih ada di halaman ini. */
    fun stillOnPage(ss: List<Stroke>): List<Stroke> {
        val set = strokes.toHashSet()
        return ss.filter { it in set }
    }

    /** Pilih semua tulisan tangan di halaman ini. Hasil: false bila tidak ada tulisan. */
    fun selectAllInk(): Boolean {
        val ink = inkStrokes()
        if (ink.isEmpty()) return false
        cancelInteraction()
        selectObjects(emptyList(), emptyList(), ink, expand = false)
        return true
    }

    /** Salinan isi halaman ini (untuk ekspor dan indeks pencarian). */
    fun contentSnapshot(): PageContent = PageContent(ArrayList(strokes), ArrayList(texts), ArrayList(items))

    /** Ganti goresan tertentu dengan teks ketik, sebagai satu langkah yang bisa diurungkan. */
    fun replaceWithText(remove: Set<Stroke>, add: List<TextItem>) {
        cancelInteraction()
        clearSelection()
        applyTextChange(remove, emptyList(), add)
    }

    /**
     * Ganti goresan dan teks lama dengan teks baru dalam satu langkah urungkan.
     * Tidak mengganggu goresan yang sedang ditulis, jadi aman dipakai teks otomatis.
     */
    fun applyTextChange(removeStrokes: Set<Stroke>, removeTexts: List<TextItem>, add: List<TextItem>, animate: Boolean = false) {
        finishTextAnim()
        val removed = ArrayList<Pair<Int, Stroke>>()
        for (i in strokes.indices.reversed()) {
            val s = strokes[i]
            if (s in removeStrokes) {
                strokes.removeAt(i)
                removed.add(i to s)
            }
        }
        val removedTexts = ArrayList<Pair<Int, TextItem>>()
        for (t in removeTexts) {
            val idx = texts.indexOf(t)
            if (idx >= 0) {
                texts.removeAt(idx)
                removedTexts.add(idx to t)
            }
        }
        if (removed.isEmpty() && removedTexts.isEmpty() && add.isEmpty()) return
        for (t in add) t.z = nextZ()
        texts.addAll(add)
        if (animate && ValueAnimator.areAnimatorsEnabled()) {
            // Pasangkan teks baru dengan teks lama yang disambung, supaya hanya bagian barunya yang muncul.
            val incoming = add.map { n ->
                n to removedTexts.firstOrNull { (_, o) -> o.x == n.x && o.y == n.y && n.text.startsWith(o.text) }?.second
            }
            textAnim = TextAnim(removed.map { it.second }, incoming, SystemClock.uptimeMillis())
            animSkip.clear()
            animSkip.addAll(add)
        }
        pushUndo(Change(removedStrokes = removed, removedTexts = removedTexts, addedTexts = add))
        contentChanged()
    }

    // ---- animasi tulisan berubah jadi teks ----

    private class TextAnim(
        val outStrokes: List<Stroke>,
        val incoming: List<Pair<TextItem, TextItem?>>,
        val start: Long,
    )
    private var textAnim: TextAnim? = null
    private val animSkip = HashSet<TextItem>()
    private val animDuration = 520L
    private val animClip = Path()
    private val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG)

    private fun finishTextAnim() {
        if (textAnim == null) return
        textAnim = null
        animSkip.clear()
        invalidateCache()
    }

    /** Tinta memudar, teks baru naik perlahan dengan kilau lembut di belakangnya. */
    private fun drawTextAnim(canvas: Canvas) {
        val a = textAnim ?: return
        val raw = ((SystemClock.uptimeMillis() - a.start).toFloat() / animDuration).coerceIn(0f, 1f)
        val p = 1f - (1f - raw) * (1f - raw) * (1f - raw) // perlambatan di akhir
        if (raw < 1f && a.outStrokes.isNotEmpty()) {
            canvas.saveLayerAlpha(null, ((1f - p) * 255).toInt())
            for (s in a.outStrokes) InkRenderer.drawStroke(canvas, s, livePaint, livePath)
            canvas.restore()
        }
        val glow = sin(Math.PI * raw).toFloat()
        for ((n, base) in a.incoming) {
            val nl = InkRenderer.layoutFor(n)
            animClip.rewind()
            if (base == null) {
                val last = (nl.lineCount - 1).coerceAtLeast(0)
                var right = n.x
                for (k in 0 until nl.lineCount) right = max(right, n.x + nl.getLineRight(k))
                animClip.addRect(n.x - 4f, n.y - 4f, right + 4f, n.y + nl.getLineBottom(last) + 4f, Path.Direction.CW)
            } else {
                InkRenderer.drawText(canvas, base)
                val bl = InkRenderer.layoutFor(base)
                val last = (bl.lineCount - 1).coerceAtLeast(0)
                val endX = base.x + bl.getLineRight(last)
                val top = base.y + bl.getLineTop(last)
                val bottom = base.y + bl.getLineBottom(last)
                animClip.addRect(endX, top, n.x + n.width + 8f, bottom, Path.Direction.CW)
                animClip.addRect(n.x - 4f, bottom, n.x + n.width + 8f, n.y + nl.height + 8f, Path.Direction.CW)
            }
            canvas.save()
            canvas.clipPath(animClip)
            if (glow > 0f) {
                glowPaint.color = InkRenderer.withAlpha(accentColor, 0.22f * glow)
                canvas.drawRect(n.x - 6f, n.y - 4f, n.x + n.width + 8f, n.y + nl.height + 6f, glowPaint)
            }
            canvas.translate(0f, (1f - p) * n.size * 0.35f)
            canvas.saveLayerAlpha(null, (p * 255).toInt())
            InkRenderer.drawText(canvas, n)
            canvas.restore()
            canvas.restore()
        }
        if (raw >= 1f) post { finishTextAnim() } else postInvalidateOnAnimation()
    }

    /**
     * Teks ketik yang baris terakhirnya duduk di garis yang sama dengan tulisan baru (garis dasar
     * berselisih paling banyak [tolerance]) dan berakhir tidak jauh di kirinya, supaya tulisan baru
     * disambung ke teks itu.
     */
    fun textOnLine(baseline: Float, tolerance: Float, left: Float, exclude: Collection<TextItem>): TextItem? {
        for (i in texts.indices.reversed()) {
            val t = texts[i]
            if (t in exclude) continue
            val l = InkRenderer.layoutFor(t)
            val last = l.lineCount - 1
            if (last < 0) continue
            val tb = t.y + l.getLineBaseline(last)
            val endX = t.x + l.getLineRight(last)
            val tol = max(tolerance, t.size * 0.6f)
            if (abs(tb - baseline) <= tol && left >= t.x - t.size && left - endX < max(t.size * 5f, tol * 2f)) return t
        }
        return null
    }

    /** Ujung kanan baris terakhir sebuah teks. */
    fun textEndX(t: TextItem): Float {
        val l = InkRenderer.layoutFor(t)
        val last = l.lineCount - 1
        return if (last < 0) t.x else t.x + l.getLineRight(last)
    }

    /** Teks ketik di baris tepat di atas (kalimat sebelumnya), untuk konteks membaca. */
    fun textAbove(baseline: Float, lineHeight: Float, left: Float, right: Float): TextItem? {
        var best: TextItem? = null
        var bestD = Float.MAX_VALUE
        for (t in texts) {
            val l = InkRenderer.layoutFor(t)
            val last = l.lineCount - 1
            if (last < 0) continue
            val d = baseline - (t.y + l.getLineBaseline(last))
            if (d < lineHeight * 0.5f || d > lineHeight * 2.2f) continue
            if (t.x + l.getLineRight(last) < left - lineHeight * 2f || t.x > right) continue
            if (d < bestD) { bestD = d; best = t }
        }
        return best
    }

    /** Pena aktif sedang melayang dekat layar: penulis kemungkinan belum selesai. */
    fun penNear(): Boolean = penHovering && SystemClock.uptimeMillis() - lastHoverEvent < 1500

    fun hasStroke(s: Stroke): Boolean = strokes.contains(s)

    // ---- sunting teks dengan keyboard ----

    private var editOriginal: TextItem? = null
    private var editIndex = -1
    private var editCurrent: TextItem? = null

    /** Kotak teks baru yang belum masuk riwayat urungkan (baru dicatat bila selesai dan berisi). */
    private var editIsNew = false

    /** Mulai menyunting teks: perubahan tampil langsung, dicatat sebagai satu langkah saat selesai. */
    fun beginTextEdit(t: TextItem): Boolean {
        finishTextAnim()
        typeTarget?.end(commit = true)
        val idx = texts.indexOf(t)
        if (idx < 0) return false
        cancelInteraction()
        editOriginal = t
        editIndex = idx
        editCurrent = t
        editIsNew = false
        clearSelection()
        startTyping(TextTarget())
        bringTypingIntoView()
        return true
    }

    /** Kotak teks baru, langsung diketik di halaman. */
    fun beginNewText(t: TextItem): Boolean {
        finishTextAnim()
        typeTarget?.end(commit = true)
        cancelInteraction()
        clearSelection()
        t.z = nextZ()
        texts.add(t)
        editOriginal = t
        editIndex = texts.size - 1
        editCurrent = t
        editIsNew = true
        startTyping(TextTarget())
        bringTypingIntoView()
        return true
    }

    /** Ganti gaya teks yang sedang diketik; dicatat bersama isinya saat selesai. */
    fun styleTextEdit(font: String? = null, size: Float? = null, color: Int? = null, align: Align? = null) {
        val cur = editCurrent ?: return
        val idx = texts.indexOf(cur)
        if (idx < 0) return
        val f = font ?: cur.font
        val sz = size ?: cur.size
        // Teks di kertas bergaris: jarak baris ikut ukuran huruf, garis dasar baris pertama tetap di garisnya.
        val ruled = if (cur.linePitch > 0f) RuledLines.of(page) else null
        val pitch = if (ruled != null) InkRenderer.ruledPitch(sz, f, ruled.gap) else cur.linePitch
        var next = TextItem(cur.x, cur.y, cur.width, cur.text, sz, color ?: cur.color, f, align ?: cur.align, cur.runs, pitch)
        if (cur.linePitch > 0f) {
            val base = cur.y + InkRenderer.layoutFor(cur).getLineBaseline(0)
            next = next.at(cur.x, base - InkRenderer.layoutFor(next).getLineBaseline(0))
        }
        next.group = cur.group
        next.z = cur.z
        next.masks = cur.masks
        texts[idx] = next
        editCurrent = next
        val r = RectF()
        val b = RectF()
        objBounds(cur, r)
        objBounds(next, b)
        r.union(b)
        refreshRegion(r)
        bringTypingIntoView()
    }

    /** Teks yang sedang diketik (untuk menampilkan gayanya di bilah alat). */
    fun typingText(): TextItem? = editCurrent

    /** Gaya untuk huruf berikutnya yang diketik di kursor (-1: ikut huruf sebelumnya). */
    private var typePending = -1
    private var typePendingAt = -1
    private var typeJustEdited = false

    /** Gaya [flag] (TextRuns.BOLD dan seterusnya) aktif di pilihan [a, b) atau di kursor. */
    fun typingFlagActive(flag: Int, a: Int, b: Int): Boolean {
        val t = editCurrent ?: return false
        if (a < b) return t.runs.allHave(a, b.coerceAtMost(t.text.length), flag)
        val f = if (typePending >= 0) typePending else t.runs.flagsForInsert(a, t.text.length)
        return f and flag != 0
    }

    /** Tombol B/I/U/S: pasang/lepas di pilihan, atau untuk huruf berikutnya bila tidak ada pilihan. */
    fun toggleTypingFlag(flag: Int, a: Int, b: Int) {
        val cur = editCurrent ?: return
        if (a < b) {
            val end = b.coerceAtMost(cur.text.length)
            val on = !cur.runs.allHave(a, end, flag)
            replaceEditing(cur.withRuns(cur.runs.styled(a, end, flag, on, cur.text.length)))
            return
        }
        val f = if (typePending >= 0) typePending else cur.runs.flagsForInsert(a, cur.text.length)
        typePending = f xor flag
        typePendingAt = a
    }

    /** Gaya awal huruf yang akan diketik di kotak teks baru. */
    fun setTypingPending(flags: Int) {
        typePending = if (flags != 0) flags else -1
        typePendingAt = selB
    }

    private fun replaceEditing(next: TextItem) {
        val cur = editCurrent ?: return
        val idx = texts.indexOf(cur)
        if (idx < 0) return
        texts[idx] = next
        editCurrent = next
        val r = RectF()
        val b = RectF()
        objBounds(cur, r)
        objBounds(next, b)
        r.union(b)
        refreshRegion(r)
    }

    fun updateTextEdit(value: String) {
        val cur = editCurrent ?: return
        val idx = texts.indexOf(cur)
        if (idx < 0) return
        // Gaya huruf ikut bergeser; huruf baru memakai gaya yang dipilih (B/I/U/S) atau gaya huruf sebelumnya.
        val pending = typePending
        if (value != cur.text) {
            typePending = -1
            typeJustEdited = true
        }
        val next = cur.withText(value, cur.runs.edited(cur.text, value, if (pending >= 0) pending else null))
        texts[idx] = next
        editCurrent = next
        // Tiap ketukan huruf: gambar ulang kotak teks itu saja.
        val r = RectF()
        val b = RectF()
        objBounds(cur, r)
        objBounds(next, b)
        r.union(b)
        refreshRegion(r)
    }

    fun endTextEdit(commit: Boolean) {
        val orig = editOriginal ?: return
        val cur = editCurrent
        val isNew = editIsNew
        editOriginal = null
        editCurrent = null
        editIsNew = false
        if (typeTarget is TextTarget) stopTyping()
        val idx = if (cur != null) texts.indexOf(cur) else -1
        if (idx < 0) {
            invalidateCache(); return
        }
        if (isNew) {
            val c = cur ?: return
            if (!commit || c.text.isBlank()) {
                // Kotak baru yang tidak jadi diisi: hilang tanpa jejak di riwayat.
                texts.removeAt(idx)
                val r = RectF()
                objBounds(c, r)
                refreshRegion(r)
                return
            }
            val fin = if (c.text != c.text.trimEnd()) c.withText(c.text.trimEnd()) else c
            texts[idx] = fin
            pushUndo(Change(addedTexts = listOf(fin)))
            contentChanged()
            return
        }
        // Baris kosong yang ditambahkan di akhir (ketuk di bawah teks) tapi tidak jadi diisi: dibuang.
        val c0 = cur
        val c = if (c0 != null && c0.text.length > orig.text.length && c0.text.startsWith(orig.text) &&
            c0.text.substring(orig.text.length).isBlank()
        ) c0.withText(orig.text) else c0
        if (!commit) {
            texts[idx] = orig
            invalidateCache()
            return
        }
        if (c == null) return
        if (c !== cur) texts[idx] = c
        if (c.text.isBlank()) {
            // Teks dikosongkan: hapus dari halaman.
            texts.removeAt(idx)
            pushUndo(Change(removedTexts = listOf(editIndex to orig)))
            contentChanged()
            return
        }
        if (c.text == orig.text && c.font == orig.font && c.size == orig.size && c.color == orig.color && c.align == orig.align && c.runs == orig.runs) {
            texts[idx] = orig
            invalidateCache()
            return
        }
        pushUndo(Change(removedTexts = listOf(editIndex to orig), addedTexts = listOf(c), addedTextsAt = listOf(idx)))
        contentChanged()
    }

    /**
     * Kotak centang di titik (lx, ly) pada tata letak teks: posisinya di teks, atau -1. Bidang ketuknya
     * sedikit lebih lebar dari kotaknya supaya mudah dikenai jari atau pena.
     */
    private fun boxOffsetIn(text: String, l: Layout, lx: Float, ly: Float, slop: Float): Int {
        if (text.indexOf(TextLists.BOX[0]) < 0 && text.indexOf(TextLists.DONE[0]) < 0) return -1
        if (l.lineCount == 0 || ly < -slop || ly > l.height + slop) return -1
        val line = l.getLineForVertical(ly.roundToInt().coerceIn(0, max(0, l.height - 1)))
        val at = l.getLineStart(line)
        if (at >= text.length) return -1
        val c = text[at]
        if (c != TextLists.BOX[0] && c != TextLists.DONE[0]) return -1
        if (at > 0 && text[at - 1] != '\n') return -1
        val size = l.paint.textSize
        val left = l.getPrimaryHorizontal(at)
        val right = left + InkRenderer.CheckboxSpan.widthFor(size) + size * 0.3f
        return if (lx >= left - slop && lx <= right + slop) at else -1
    }

    /** Kotak centang di titik halaman ini: teks ketik atau stiker pemiliknya, dan posisinya di teks. */
    private fun checkboxAt(px: Float, py: Float): Pair<Any, Int>? {
        val slop = max(5f, 10f * density / scale)
        val owners = ArrayList<Any>()
        for (t in texts) if (t.text.indexOf(TextLists.BOX[0]) >= 0 || t.text.indexOf(TextLists.DONE[0]) >= 0) owners.add(t)
        for (it in items) if (it is StickyItem && (it.text.indexOf(TextLists.BOX[0]) >= 0 || it.text.indexOf(TextLists.DONE[0]) >= 0)) owners.add(it)
        if (owners.isEmpty()) return null
        owners.sortByDescending { if (it is TextItem) it.z else (it as PageItem).z }
        for (o in owners) {
            if (o is TextItem) {
                val at = boxOffsetIn(o.text, InkRenderer.layoutFor(o), px - o.x, py - o.y, slop)
                if (at >= 0) return o to at
            } else if (o is StickyItem) {
                // Balik putaran stiker, lalu ke koordinat tata letak isinya.
                pt[0] = px
                pt[1] = py
                if (o.rotation != 0f) {
                    boxMatrix.setRotate(-o.rotation, o.cx, o.cy)
                    boxMatrix.mapPoints(pt)
                }
                val at = boxOffsetIn(
                    o.text, InkRenderer.stickyLayout(o),
                    pt[0] - InkRenderer.stickyTextLeft(o), pt[1] - InkRenderer.stickyTextTop(o), slop,
                )
                if (at >= 0) return o to at
            }
        }
        return null
    }

    /** Centang atau batalkan kotak di titik ini (bisa diurungkan). Hasil: ada kotak yang diubah. */
    private fun toggleCheckboxAt(px: Float, py: Float): Boolean {
        val (o, at) = checkboxAt(px, py) ?: return false
        val done = toggleCheckOf(o, at)
        if (done) performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
        return done
    }

    /**
     * Kotak centang diubah dari luar (widget): teks ke-[index] (atau stiker [itemId]) yang isinya masih [text].
     * Dicatat di riwayat urungkan seperti ketukan biasa.
     */
    fun applyExternalCheck(sticky: Boolean, index: Int, itemId: Long, at: Int, text: String): Boolean {
        val o: Any = if (sticky) {
            items.firstOrNull { it is StickyItem && it.id == itemId && it.text == text }
                ?: items.firstOrNull { it is StickyItem && it.text == text } ?: return false
        } else {
            texts.getOrNull(index)?.takeIf { it.text == text } ?: texts.firstOrNull { it.text == text } ?: return false
        }
        return toggleCheckOf(o, at)
    }

    private fun toggleCheckOf(o: Any, at: Int): Boolean {
        finishTextAnim()
        return when (o) {
            is TextItem -> {
                val next = TextLists.toggleCheck(o.text, at) ?: return false
                val idx = texts.indexOf(o)
                if (idx < 0) return false
                val n = o.withText(next)
                texts[idx] = n
                pushUndo(Change(removedTexts = listOf(idx to o), addedTexts = listOf(n), addedTextsAt = listOf(idx)))
                contentChanged()
                if (o in selTexts) selectObjects(selItems, selTexts.map { if (it === o) n else it }, selStrokes, expand = false)
                true
            }
            is StickyItem -> {
                val next = TextLists.toggleCheck(o.text, at) ?: return false
                replaceItem(o, o.withText(next, o.font, o.textSize, o.align))
            }
            else -> false
        }
    }

    /** Kotak centang di teks yang sedang diketik, pada titik layar ini (-1: tidak ada). */
    private fun typingBoxAt(vx: Float, vy: Float): Int {
        val tt = typeTarget ?: return -1
        if (tt is NodeTarget) return -1
        tt.toPage(typeMatrix)
        if (!typeMatrix.invert(typeInverse)) return -1
        pt[0] = vx
        pt[1] = vy
        inverseMatrix.mapPoints(pt)
        typeInverse.mapPoints(pt)
        val k = (typeMatrix.mapRadius(1f) * scale).coerceAtLeast(0.01f)
        return boxOffsetIn(tt.text, tt.layout(), pt[0], pt[1], 10f * density / k)
    }

    /** Teks yang kotaknya (selebar kotak teks, setinggi isinya) memuat titik ini; yang paling depan. */
    private fun textBoxAt(px: Float, py: Float): TextItem? {
        val slop = max(4f, 10f * density / scale)
        var best: TextItem? = null
        for (t in texts) {
            if (best != null && t.z <= best.z) continue
            val h = max(InkRenderer.layoutFor(t).height.toFloat(), t.size * 1.2f)
            if (px >= t.x - slop && px <= t.x + t.width + slop && py >= t.y - slop && py <= t.y + h + slop) best = t
        }
        return best
    }

    /**
     * Teks di atas titik ini yang bisa dilanjutkan dengan mengetik di bawahnya (seperti di pengolah kata):
     * kotaknya menaungi titik secara mendatar dan ujung bawahnya tidak jauh di atas titik.
     */
    fun textAbove(px: Float, py: Float, maxGap: Float): TextItem? {
        var best: TextItem? = null
        var bestGap = Float.MAX_VALUE
        for (t in texts) {
            if (px < t.x - t.size || px > t.x + t.width + t.size) continue
            val bottom = t.y + InkRenderer.layoutFor(t).height
            val gap = py - bottom
            if (gap < -t.size * 0.2f || gap > maxGap) continue
            if (gap < bestGap) { best = t; bestGap = gap }
        }
        return best
    }

    /** Teks yang hurufnya berada di titik ini (bukan sekadar kotak lebarnya). */
    private fun textGlyphAt(px: Float, py: Float): TextItem? {
        val slop = max(4f, 8f * density / scale)
        for (i in texts.indices.reversed()) {
            val t = texts[i]
            val l = InkRenderer.layoutFor(t)
            for (line in 0 until l.lineCount) {
                val left = t.x + l.getLineLeft(line) - slop
                val right = t.x + l.getLineRight(line) + slop
                val top = t.y + l.getLineTop(line) - slop
                val bottom = t.y + l.getLineBottom(line) + slop
                if (px in left..right && py in top..bottom) return t
            }
        }
        return null
    }

    /** Tempel teks ketik baru. */
    fun addText(t: TextItem, selectIt: Boolean) {
        cancelInteraction()
        t.z = nextZ()
        texts.add(t)
        pushUndo(Change(addedTexts = listOf(t)))
        contentChanged()
        if (selectIt) select(t)
    }

    /** Titik tengah bagian halaman yang sedang terlihat, dalam satuan halaman. */
    fun visibleCenter(): PointF {
        val p = page ?: return PointF(0f, 0f)
        pt[0] = width / 2f; pt[1] = height / 2f
        inverseMatrix.mapPoints(pt)
        return PointF(pt[0].coerceIn(0f, p.width), pt[1].coerceIn(0f, p.height))
    }

    /** Lebar bagian halaman yang terlihat, dalam satuan halaman. */
    fun visibleWidth(): Float {
        val p = page ?: return 0f
        return min(p.width, width / scale)
    }

    /** Tempel benda baru di halaman, lalu pilih bila diminta. */
    fun addItem(item: PageItem, selectIt: Boolean = true) {
        cancelInteraction()
        item.z = nextZ()
        items.add(item)
        pushUndo(Change(addedItems = listOf(item)))
        contentChanged()
        if (selectIt) select(item)
    }

    fun undo() {
        finishTextAnim()
        if (mode != Mode.NONE) return
        if (bakesPending > 0) { afterBake.add(Runnable { undo() }); return }
        val a = undoStack.removeLastOrNull() ?: return
        clearSelection()
        a.regroup?.forEach { (o, before, _) -> setGroup(o, before) }
        a.zChange?.forEach { (o, before, _) -> setZ(o, before) }
        for (s in a.addedStrokes) strokes.remove(s)
        for (t in a.addedTexts) texts.remove(t)
        for (i in a.addedItems) items.remove(i)
        for ((i, s) in a.removedStrokes.asReversed()) strokes.add(i.coerceAtMost(strokes.size), s)
        for ((i, t) in a.removedTexts.asReversed()) texts.add(i.coerceAtMost(texts.size), t)
        for ((i, item) in a.removedItems.asReversed()) items.add(i.coerceAtMost(items.size), item)
        sortLayers()
        redoStack.add(a)
        notifyHistory()
        pendingChange = a
        contentChanged()
        resyncMap()
    }

    fun redo() {
        finishTextAnim()
        if (mode != Mode.NONE) return
        if (bakesPending > 0) { afterBake.add(Runnable { redo() }); return }
        val a = redoStack.removeLastOrNull() ?: return
        clearSelection()
        for ((_, s) in a.removedStrokes) strokes.remove(s)
        for ((_, t) in a.removedTexts) texts.remove(t)
        for ((_, item) in a.removedItems) items.remove(item)
        insertAt(strokes, a.addedStrokes, a.addedStrokesAt)
        insertAt(texts, a.addedTexts, a.addedTextsAt)
        insertAt(items, a.addedItems, a.addedItemsAt)
        a.zChange?.forEach { (o, _, after) -> setZ(o, after) }
        a.regroup?.forEach { (o, _, after) -> setGroup(o, after) }
        sortLayers()
        undoStack.add(a)
        notifyHistory()
        pendingChange = a
        contentChanged()
        resyncMap()
    }

    /** Hapus semua isi halaman ini (bisa diurungkan). */
    fun clearPage() {
        finishTextAnim()
        if (strokes.isEmpty() && texts.isEmpty() && items.isEmpty()) return
        clearSelection()
        val change = Change(
            removedStrokes = strokes.indices.reversed().map { it to strokes[it] },
            removedTexts = texts.indices.reversed().map { it to texts[it] },
            removedItems = items.indices.reversed().map { it to items[it] },
        )
        strokes.clear(); texts.clear(); items.clear()
        pushUndo(change)
        contentChanged()
    }

    // ---- pilihan ----

    private fun <T> insertAt(list: MutableList<T>, added: List<T>, at: List<Int>?) {
        if (at == null || at.size != added.size) {
            list.addAll(added)
            return
        }
        for (k in added.indices.sortedBy { at[it] }) list.add(at[k].coerceIn(0, list.size), added[k])
    }

    private fun setGroup(o: Any, g: Long) {
        when (o) {
            is PageItem -> o.group = g
            is TextItem -> o.group = g
            is Stroke -> o.group = g
        }
    }

    // ---- urutan lapisan ----

    /** Lapisan teratas yang sudah terpakai; benda, teks, atau coretan baru selalu di atasnya. */
    private var zTop = 0L

    private fun nextZ(): Long = ++zTop

    private fun setZ(o: Any, z: Long) {
        when (o) {
            is PageItem -> o.z = z
            is TextItem -> o.z = z
            is Stroke -> o.z = z
        }
    }

    /** Daftar benda, teks, dan coretan masing-masing diurutkan menurut lapisannya. */
    private fun sortLayers() {
        strokes.sortBy { it.z }
        texts.sortBy { it.z }
        items.sortBy { it.z }
    }

    private fun boundsOf(o: Any, out: RectF) {
        when (o) {
            is PageItem -> InkRenderer.itemBounds(o, out)
            is TextItem -> InkRenderer.textBounds(o, out)
            is Stroke -> out.set(o.bounds)
        }
    }

    fun clearSelection() {
        if (!hasSelection) return
        selItems = emptyList()
        selTexts = emptyList()
        selStrokes = emptyList()
        onSelection?.invoke(null)
        invalidate()
    }

    /** Keluarkan semua yang dipilih dari halaman; posisinya dicatat dari indeks terbesar. */
    private fun removeSelected(): Lifted {
        val strokeSet = selStrokes.toHashSet()
        val rs = ArrayList<Pair<Int, Stroke>>()
        for (i in strokes.indices.reversed()) if (strokes[i] in strokeSet) { rs.add(i to strokes[i]); strokes.removeAt(i) }
        val textSet = selTexts.toHashSet()
        val rt = ArrayList<Pair<Int, TextItem>>()
        for (i in texts.indices.reversed()) if (texts[i] in textSet) { rt.add(i to texts[i]); texts.removeAt(i) }
        val itemSet = selItems.toHashSet()
        val ri = ArrayList<Pair<Int, PageItem>>()
        for (i in items.indices.reversed()) if (items[i] in itemSet) { ri.add(i to items[i]); items.removeAt(i) }
        return Lifted(ri, rt, rs)
    }

    /** Kembalikan yang dikeluarkan ke tempat semula. */
    private fun restore(l: Lifted) {
        for ((i, st) in l.strokes.asReversed()) strokes.add(i.coerceAtMost(strokes.size), st)
        for ((i, t) in l.texts.asReversed()) texts.add(i.coerceAtMost(texts.size), t)
        for ((i, it) in l.items.asReversed()) items.add(i.coerceAtMost(items.size), it)
    }

    fun deleteSelection() {
        if (!hasSelection) return
        finishTextAnim()
        val l = removeSelected()
        clearSelection()
        pushUndo(Change(removedStrokes = l.strokes, removedTexts = l.texts, removedItems = l.items))
        contentChanged()
    }

    /** Salin yang dipilih sedikit bergeser. Salinan kelompok menjadi kelompok baru. */
    fun duplicateSelection() {
        val p = page ?: return
        if (!hasSelection) return
        finishTextAnim()
        val off = 14f
        val d = if (selRect.right + off > p.width || selRect.bottom + off > p.height) -off else off
        val shift = Matrix().apply { setTranslate(d, d) }
        val groupMap = HashMap<Long, Long>()
        fun newGroup(g: Long) = if (g == 0L) 0L else groupMap.getOrPut(g) { PageItem.newId() }
        val newItems = selItems.map { old ->
            old.placed(old.x + d, old.y + d, old.w, old.h).also { n ->
                n.id = PageItem.newId()
                n.group = newGroup(old.group)
            }
        }
        val newStrokes = selStrokes.map { old -> transformStroke(old, shift, 1f).also { n -> n.group = newGroup(old.group) } }
        val newTexts = selTexts.map { old -> old.moved(old.x + d, old.y + d, old.width, old.size).also { n -> n.group = newGroup(old.group) } }
        // Salinan di atas semuanya, dengan urutan antar-salinan sama seperti aslinya.
        val copies = ArrayList<Any>(newItems.size + newTexts.size + newStrokes.size)
        copies.addAll(newItems); copies.addAll(newTexts); copies.addAll(newStrokes)
        copies.sortBy { layerZ(it) }
        for (o in copies) setZ(o, nextZ())
        strokes.addAll(newStrokes)
        texts.addAll(newTexts)
        items.addAll(newItems)
        pushUndo(Change(addedStrokes = newStrokes, addedTexts = newTexts, addedItems = newItems))
        contentChanged()
        selectObjects(newItems, newTexts, newStrokes, expand = false)
    }

    /** Ganti benda atau teks yang dipilih dengan versi barunya (misalnya link atau teks yang diubah), di lapisan yang sama. */
    fun replaceSelected(newItem: PageItem?, newText: TextItem?) {
        finishTextAnim()
        val item = selItem
        val text = selText
        if (item != null && newItem != null) {
            val idx = items.indexOf(item)
            if (idx < 0) return
            newItem.id = item.id
            newItem.group = item.group
            newItem.z = item.z
            items[idx] = newItem
            pushUndo(Change(removedItems = listOf(idx to item), addedItems = listOf(newItem), addedItemsAt = listOf(idx)))
            contentChanged()
            select(newItem)
        } else if (text != null && newText != null) {
            val idx = texts.indexOf(text)
            if (idx < 0) return
            newText.group = text.group
            newText.z = text.z
            texts[idx] = newText
            pushUndo(Change(removedTexts = listOf(idx to text), addedTexts = listOf(newText), addedTextsAt = listOf(idx)))
            contentChanged()
            select(newText)
        }
    }

    fun selectedItem(): PageItem? = selItem
    fun selectedText(): TextItem? = selText

    /**
     * Ganti benda terpilih dengan versi barunya (diputar, dibalik, atau dipotong) di lapisan yang sama.
     * Coretan di atasnya tidak ikut; kelompokkan dulu bila ingin bergerak bersama.
     */
    @Suppress("UNUSED_PARAMETER")
    fun replaceSelectedItem(newItem: PageItem, moveInk: Boolean) {
        finishTextAnim()
        val old = selItem ?: return
        val idx = items.indexOf(old)
        if (idx < 0) return
        newItem.id = old.id
        newItem.group = old.group
        newItem.z = old.z
        items[idx] = newItem
        pushUndo(Change(removedItems = listOf(idx to old), addedItems = listOf(newItem), addedItemsAt = listOf(idx)))
        contentChanged()
        select(newItem)
    }

    /** Ganti satu benda di halaman dengan versi barunya (dipilih atau tidak), sebagai satu langkah urungkan. */
    fun replaceItem(old: PageItem, newItem: PageItem): Boolean {
        finishTextAnim()
        val idx = items.indexOf(old)
        if (idx < 0) return false
        newItem.id = old.id
        newItem.group = old.group
        newItem.z = old.z
        items[idx] = newItem
        pushUndo(Change(removedItems = listOf(idx to old), addedItems = listOf(newItem), addedItemsAt = listOf(idx)))
        contentChanged()
        if (old in selItems) selectObjects(selItems.map { if (it === old) newItem else it }, selTexts, selStrokes, expand = false)
        return true
    }

    private fun select(target: Any) {
        when (target) {
            is PageItem -> selectObjects(listOf(target), emptyList(), emptyList())
            is TextItem -> selectObjects(emptyList(), listOf(target), emptyList())
            is Stroke -> selectObjects(emptyList(), emptyList(), listOf(target))
        }
    }

    /** Pilih sekumpulan objek; bila [expand], anggota kelompok yang sama ikut terpilih. */
    private fun selectObjects(its: List<PageItem>, ts: List<TextItem>, ss: List<Stroke>, expand: Boolean = true) {
        var a = its
        var b = ts
        var c = ss
        if (expand) {
            val groups = HashSet<Long>()
            for (x in its) if (x.group != 0L) groups.add(x.group)
            for (x in ts) if (x.group != 0L) groups.add(x.group)
            for (x in ss) if (x.group != 0L) groups.add(x.group)
            if (groups.isNotEmpty()) {
                val ia = its.toHashSet()
                val ta = ts.toHashSet()
                val sa = ss.toHashSet()
                a = items.filter { it in ia || it.group in groups }
                b = texts.filter { it in ta || it.group in groups }
                c = strokes.filter { it in sa || it.group in groups }
            }
        }
        selItems = a
        selTexts = b
        selStrokes = c
        if (!hasSelection) {
            onSelection?.invoke(null)
            invalidate()
            return
        }
        updateSelRect()
        onSelection?.invoke(selectionInfo())
        invalidate()
    }

    private fun updateSelRect() {
        val item = selItem
        val text = selText
        when {
            item != null -> selRect.set(item.x, item.y, item.x + item.w, item.y + item.h)
            text != null -> InkRenderer.textBounds(text, selRect)
            else -> {
                var first = true
                fun add(r: RectF) {
                    if (first) { selRect.set(r); first = false } else selRect.union(r)
                }
                for (it in selItems) { InkRenderer.itemBounds(it, box); add(box) }
                for (t in selTexts) { InkRenderer.textBounds(t, box); add(box) }
                for (st in selStrokes) add(st.bounds)
            }
        }
    }

    private fun selectionInfo(): SelectionInfo {
        val groups = HashSet<Long>()
        var ungrouped = false
        fun note(g: Long) { if (g == 0L) ungrouped = true else groups.add(g) }
        selItems.forEach { note(it.group) }
        selTexts.forEach { note(it.group) }
        selStrokes.forEach { note(it.group) }
        val grouped = !ungrouped && groups.size == 1
        val arrangeable = hasSelection
        val item = selItem
        val text = selText
        val ink = selStrokes.any { it.tool.isWriting && it.size > 0 }
        return when {
            item is LinkItem -> SelectionInfo("Link", item.url, null, grouped = grouped, arrangeable = true, ink = ink)
            item != null -> SelectionInfo(
                item.kindLabel, null, null,
                sticky = item is StickyItem,
                image = item is ImageItem,
                rotatable = item.canRotate,
                align = (item as? StickyItem)?.takeIf { it.text.isNotBlank() }?.align,
                list = (item as? StickyItem)?.takeIf { it.text.isNotBlank() }?.let { TextLists.kindOf(it.text) },
                mindMap = item is id.hananote.data.MindMapItem,
                audio = item is id.hananote.data.AudioItem,
                grouped = grouped,
                arrangeable = true,
                table = item is TableItem,
                ink = ink,
                textSize = (item as? StickyItem)?.textSize,
                recolor = item is TapeItem,
            )
            text != null -> SelectionInfo(
                "Teks", null, text.text, align = text.align, grouped = grouped, arrangeable = true,
                list = TextLists.kindOf(text.text), recolor = true, ink = ink, textSize = text.size,
            )
            else -> {
                val n = selItems.size + selTexts.size
                val kind = when {
                    n == 0 -> "Tulisan"
                    selStrokes.isEmpty() -> "$n objek"
                    else -> "$n objek + tulisan"
                }
                SelectionInfo(
                    kind, null, null, multi = true, grouped = grouped, arrangeable = arrangeable,
                    recolor = selStrokes.isNotEmpty() || selTexts.isNotEmpty() || selItems.any { it is TapeItem }, ink = ink,
                )
            }
        }
    }

    /** Jadikan yang dipilih satu kelompok: berikutnya selalu terpilih dan bergeser bersama. */
    fun groupSelection() {
        if (!hasSelection) return
        val g = PageItem.newId()
        regroup(g)
    }

    /** Pisahkan kelompok yang dipilih menjadi objek-objek lepas. */
    fun ungroupSelection() {
        if (!hasSelection) return
        regroup(0L)
    }

    private fun regroup(g: Long) {
        val changes = ArrayList<Triple<Any, Long, Long>>()
        for (it in selItems) if (it.group != g) { changes.add(Triple(it, it.group, g)); it.group = g }
        for (t in selTexts) if (t.group != g) { changes.add(Triple(t, t.group, g)); t.group = g }
        for (st in selStrokes) if (st.group != g) { changes.add(Triple(st, st.group, g)); st.group = g }
        if (changes.isEmpty()) return
        pushUndo(Change(regroup = changes))
        contentChanged()
        onSelection?.invoke(selectionInfo())
        invalidate()
    }

    enum class Arrange { FRONT, FORWARD, BACKWARD, BACK }

    /**
     * Majukan atau mundurkan yang dipilih (benda, teks, dan coretan) seperti di Canva.
     * Maju dan Mundur melewati satu objek yang bertumpuk dengannya; coretan yang berurutan dianggap
     * satu lapisan, jadi satu tulisan tangan dilewati sekaligus. Hasil: ada yang berubah atau tidak.
     */
    fun arrangeSelection(op: Arrange): Boolean {
        if (!hasSelection) return false
        finishTextAnim()
        val all = ArrayList<Any>(items.size + texts.size + strokes.size)
        all.addAll(items); all.addAll(texts); all.addAll(strokes)
        all.sortWith(compareBy { layerZ(it) })
        val sel = HashSet<Any>()
        sel.addAll(selItems); sel.addAll(selTexts); sel.addAll(selStrokes)
        val moved = all.filter { it in sel }
        if (moved.isEmpty()) return false
        val rest = all.filter { it !in sel }

        val area = RectF()
        moved.forEachIndexed { k, o -> boundsOf(o, box); if (k == 0) area.set(box) else area.union(box) }
        fun overlaps(o: Any): Boolean { boundsOf(o, box); return RectF.intersects(box, area) }
        val firstSel = all.indexOfFirst { it in sel }
        val lastSel = all.indexOfLast { it in sel }
        // Banyaknya objek lain di bawah objek terpilih paling atas / paling bawah.
        val belowTop = (0 until lastSel).count { all[it] !in sel }
        val belowBottom = (0 until firstSel).count { all[it] !in sel }
        val insertAt = when (op) {
            Arrange.FRONT -> rest.size
            Arrange.BACK -> 0
            Arrange.FORWARD -> {
                var j = belowTop
                while (j < rest.size && !overlaps(rest[j])) j++
                if (j >= rest.size) rest.size else {
                    if (rest[j] is Stroke) while (j + 1 < rest.size && rest[j + 1] is Stroke) j++
                    j + 1
                }
            }
            Arrange.BACKWARD -> {
                var j = belowBottom - 1
                while (j >= 0 && !overlaps(rest[j])) j--
                if (j < 0) 0 else {
                    if (rest[j] is Stroke) while (j - 1 >= 0 && rest[j - 1] is Stroke) j--
                    j
                }
            }
        }
        val result = ArrayList<Any>(all.size)
        result.addAll(rest.subList(0, insertAt))
        result.addAll(moved)
        result.addAll(rest.subList(insertAt, rest.size))
        if (result.indices.all { result[it] === all[it] }) return false

        val changes = ArrayList<Triple<Any, Long, Long>>()
        for ((i, o) in result.withIndex()) {
            val nz = (i + 1).toLong()
            val oz = layerZ(o)
            if (oz != nz) { changes.add(Triple(o, oz, nz)); setZ(o, nz) }
        }
        // Jangan turunkan batas atas: urungkan bisa mengembalikan nilai lama yang lebih besar.
        zTop = max(zTop, result.size.toLong())
        sortLayers()
        pushUndo(Change(zChange = changes))
        contentChanged()
        updateSelRect()
        onSelection?.invoke(selectionInfo())
        invalidate()
        return true
    }

    private fun selRotation(): Float = selItem?.rotation ?: 0f

    /** Titik halaman ke koordinat kotak yang diputar (pusat kotak di 0,0), hasilnya di [local]. */
    private fun toLocal(px: Float, py: Float, cx: Float, cy: Float, deg: Float) {
        val dx = px - cx
        val dy = py - cy
        if (deg == 0f) { local[0] = dx; local[1] = dy; return }
        val r = Math.toRadians(deg.toDouble())
        val c = cos(r).toFloat()
        val s = sin(r).toFloat()
        local[0] = dx * c + dy * s
        local[1] = -dx * s + dy * c
    }

    /** Kebalikan [toLocal]: koordinat kotak ke titik halaman, hasilnya di [local]. */
    private fun fromLocal(lx: Float, ly: Float, cx: Float, cy: Float, deg: Float) {
        val r = Math.toRadians(deg.toDouble())
        val c = cos(r).toFloat()
        val s = sin(r).toFloat()
        local[0] = cx + lx * c - ly * s
        local[1] = cy + lx * s + ly * c
    }

    private fun itemContains(item: PageItem, px: Float, py: Float, pad: Float): Boolean =
        if (item is TapeItem) Tapes.hit(item, px, py, pad) else item.containsPoint(px, py, pad)

    private fun insideSelection(px: Float, py: Float, pad: Float): Boolean {
        toLocal(px, py, selRect.centerX(), selRect.centerY(), selRotation())
        return abs(local[0]) <= selRect.width() / 2f + pad && abs(local[1]) <= selRect.height() / 2f + pad
    }

    /** Matriks yang memindahkan isi kotak lama (pusat, ukuran, putaran) ke kotak baru. */
    private fun itemMatrix(
        c0x: Float, c0y: Float, w0: Float, h0: Float, r0: Float,
        c1x: Float, c1y: Float, w1: Float, h1: Float, r1: Float,
        out: Matrix,
    ) {
        out.reset()
        out.postTranslate(-c0x, -c0y)
        if (r0 != 0f) out.postRotate(-r0)
        out.postScale(if (w0 > 0f) w1 / w0 else 1f, if (h0 > 0f) h1 / h0 else 1f)
        if (r1 != 0f) out.postRotate(r1)
        out.postTranslate(c1x, c1y)
    }

    private fun dragMatrix(out: Matrix) {
        itemMatrix(
            dragFrom.centerX(), dragFrom.centerY(), dragFrom.width(), dragFrom.height(), dragRot0,
            dragTo.centerX(), dragTo.centerY(), dragTo.width(), dragTo.height(), dragRot1,
            out,
        )
    }

    // ================= riwayat =================

    /**
     * Isi halaman berubah. Bila perubahannya baru saja dicatat ([pendingChange]), cukup bagian halaman yang
     * tersentuh yang digambar ulang; menggambar ulang seluruh halaman yang padat bisa membuat tersendat.
     */
    private fun contentChanged() {
        val a = pendingChange
        pendingChange = null
        val r = a?.let { changeBounds(it) }
        if (a != null && r == null && a.regroup != null) invalidate() else refreshRegion(r)
        onStrokesChanged?.invoke()
    }

    /** Perubahan terakhir yang belum digambar ulang. */
    private var pendingChange: Change? = null

    private fun pushUndo(a: Change) {
        undoStack.add(a)
        if (undoStack.size > 300) undoStack.removeAt(0)
        redoStack.clear()
        notifyHistory()
        pendingChange = a
    }

    /** Gambar ulang hanya bagian halaman [r] (satuan halaman) bila bisa; selain itu seluruh halaman. */
    private fun refreshRegion(r: RectF?) {
        if (r == null || r.isEmpty || !cacheValid || gestureActive || settling || viewStale || cacheCanvas == null ||
            turnDrag != null || turnSnap != null || textAnim != null
        ) {
            invalidateCache()
            return
        }
        redrawCacheRegion(r)
        invalidate()
    }

    /** Kotak yang mungkin tergambar oleh objek [o], termasuk bayangan dan pinggiran cat air. */
    private fun objBounds(o: Any, out: RectF) {
        when (o) {
            is Stroke -> {
                out.set(o.bounds)
                val pad = o.width * 2f + 4f
                out.inset(-pad, -pad)
            }
            is TextItem -> {
                InkRenderer.textBounds(o, out)
                out.inset(-6f, -6f)
            }
            is PageItem -> {
                InkRenderer.itemBounds(o, out)
                val sh = (o as? ImageItem)?.shadow
                val pad = 6f + (sh?.let { it.distance + it.blur * 3f } ?: 0f)
                out.inset(-pad, -pad)
            }
            else -> out.setEmpty()
        }
    }

    private fun changeBounds(a: Change): RectF? {
        val r = RectF()
        val b = RectF()
        var any = false
        fun add(o: Any) {
            objBounds(o, b)
            if (b.isEmpty) return
            if (!any) { r.set(b); any = true } else r.union(b)
        }
        a.removedStrokes.forEach { add(it.second) }
        a.addedStrokes.forEach { add(it) }
        a.removedTexts.forEach { add(it.second) }
        a.addedTexts.forEach { add(it) }
        a.removedItems.forEach { add(it.second) }
        a.addedItems.forEach { add(it) }
        a.zChange?.forEach { add(it.first) }
        return if (any) r else null
    }

    private fun liftedBounds(l: Lifted): RectF? {
        val r = RectF()
        val b = RectF()
        var any = false
        fun add(o: Any) {
            objBounds(o, b)
            if (!any) { r.set(b); any = true } else r.union(b)
        }
        l.strokes.forEach { add(it.second) }
        l.texts.forEach { add(it.second) }
        l.items.forEach { add(it.second) }
        return if (any) r else null
    }

    private fun notifyHistory() {
        onHistoryChanged?.invoke(undoStack.isNotEmpty(), redoStack.isNotEmpty())
    }

    /** Isi halaman berubah (atau halamannya diganti): gambar cadangan dan alas seluruh halaman digambar ulang. */
    fun invalidateCache() {
        contentVersion++
        markBackdropDirty()
        invalidateViewCache()
    }

    /** Hanya posisi, ukuran, atau warna meja yang berubah; isi halaman tetap. */
    private fun invalidateViewCache() {
        pendingChange = null
        cacheValid = false
        invalidate()
    }

    // ================= ukuran & transformasi =================

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        if (oldw == 0 || w != oldw) {
            needsFit = true
            if (rulerVisible) placeRuler()
        } else {
            // Hanya tinggi yang berubah (misalnya keyboard muncul): pertahankan posisi halaman.
            clamp()
            updateMatrix()
            bringTypingIntoView(refresh = false)
            if (cache != null && cacheValid) {
                // Jangan gambar ulang tiap bingkai animasi; tunggu sampai ukurannya berhenti berubah.
                settling = true
                removeCallbacks(settle)
                postDelayed(settle, 160)
                invalidate()
                return
            }
        }
        invalidateViewCache()
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        ImageCache.onLoaded.add(imageReady)
        PdfPages.onRendered.add(pdfReady)
    }

    /** Gambar selesai dibuka di latar: gambar ulang bagian gambar itu (atau seluruh halaman bila latarnya). */
    private val imageReady: (java.io.File) -> Unit = { f -> onImageReady(f) }
    private val pdfReady: (java.io.File, Int) -> Unit = { f, i -> onPdfReady(f, i) }

    private fun onImageReady(f: java.io.File) {
        val p = page ?: return
        val dir = imageDir ?: return
        if (f.parentFile?.path != dir.path) return
        if (PageBackground.imageOf(p.background) == f.name) { redrawWhenIdle(p.id, null); return }
        val r = RectF()
        val b = RectF()
        var any = false
        for (it in items) {
            if (it !is ImageItem || it.file != f.name) continue
            objBounds(it, b)
            if (!any) { r.set(b); any = true } else r.union(b)
        }
        if (any) redrawWhenIdle(p.id, r)
    }

    private fun onPdfReady(f: java.io.File, index: Int) {
        val p = page ?: return
        val dir = imageDir ?: return
        val pdf = PageBackground.pdfOf(p.background) ?: return
        if (pdf.second == index && java.io.File(dir, pdf.first).path == f.path) redrawWhenIdle(p.id, null)
    }

    /**
     * Gambar yang baru siap ditampilkan setelah goresan yang sedang ditulis selesai, supaya menulis tidak
     * pernah tersendat karena halaman digambar ulang di tengah sapuan. [r] null: seluruh halaman.
     */
    private fun redrawWhenIdle(pageId: String, r: RectF?) {
        if (page?.id != pageId || !isAttachedToWindow) return
        if (builder != null || mode == Mode.ERASE || mode == Mode.SHAPE || mode == Mode.TAPE || gestureActive) {
            postDelayed({ redrawWhenIdle(pageId, r) }, 150)
            return
        }
        if (r == null) invalidateCache() else refreshRegion(r)
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        ImageCache.onLoaded.remove(imageReady)
        PdfPages.onRendered.remove(pdfReady)
        removeCallbacks(settle)
        removeCallbacks(backdropJob)
        settling = false
        viewToken++
        viewStale = false
        backdropToken++
        backdrop = null
        backdropSpare = null
        backdropPage = null
        backdropInFlight = -1L
        neighborSheets.clear()
        neighborJobs.clear()
        cache?.recycle()
        cache = null
        cacheCanvas = null
        cacheValid = false
    }

    private fun fit() {
        val p = page ?: return
        if (width == 0 || height == 0) return
        val byWidth = (width - margin * 2) / p.width
        val byHeight = (height - margin * 2) / p.height
        fitScale = min(byWidth, byHeight * 2.2f)
        scale = fitScale
        rot = 0f
        tx = (width - p.width * scale) / 2f
        ty = margin
        needsFit = false
        clamp(Clamp.NORMAL)
        updateMatrix()
    }

    private fun updateMatrix() {
        viewMatrix.reset()
        viewMatrix.setScale(scale, scale)
        if (rot != 0f) viewMatrix.postRotate(rot)
        viewMatrix.postTranslate(tx, ty)
        viewMatrix.invert(inverseMatrix)
        notifyViewState()
    }

    /**
     * Cara menjepit posisi halaman. NORMAL: seperti biasa (di tengah bila muat). KEEP: posisi yang sudah digeser
     * jauh dibiarkan (tidak tiba-tiba kembali ke tengah). PAN: geser satu jari (ke samping hanya sampai batas biasa,
     * supaya sapuan ke samping langsung membalik halaman; ke atas-bawah boleh lebih jauh). FREE: geser dua jari,
     * kertas boleh dibawa jauh ke kiri, kanan, atas, atau bawah supaya pinggirnya mudah ditulisi.
     */
    private enum class Clamp { NORMAL, KEEP, PAN, FREE }

    private val clampBox = RectF()
    private val clampM = Matrix()
    /** Tepi kiri halaman di layar saat geser satu jari dimulai. */
    private var panKeepLeft = 0f

    /** Kotak halaman di layar (setelah diputar) untuk posisi dan skala sekarang. */
    private fun pageBox(out: RectF) {
        val p = page ?: run { out.setEmpty(); return }
        clampM.setScale(scale, scale)
        if (rot != 0f) clampM.postRotate(rot)
        clampM.postTranslate(tx, ty)
        out.set(0f, 0f, p.width, p.height)
        clampM.mapRect(out)
    }

    /** Rentang biasa tepi kiri/atas kotak halaman: di tengah bila muat, selain itu sampai tepinya terlihat. */
    private fun normalRange(len: Float, view: Int): FloatArray =
        if (len + margin * 2 <= view) {
            val c = (view - len) / 2f
            floatArrayOf(c, c)
        } else {
            floatArrayOf(view - len - margin, margin)
        }

    private fun clamp(how: Clamp = Clamp.KEEP) {
        if (page == null) return
        pageBox(clampBox)
        val rx = normalRange(clampBox.width(), width)
        val ry = normalRange(clampBox.height(), height)
        val sx = width * 0.42f
        val sy = height * 0.42f
        val left = clampBox.left
        val top = clampBox.top
        val loX: Float
        val hiX: Float
        when (how) {
            Clamp.NORMAL -> { loX = rx[0]; hiX = rx[1] }
            Clamp.FREE -> { loX = rx[0] - sx; hiX = rx[1] + sx }
            Clamp.PAN -> { loX = min(rx[0], panKeepLeft); hiX = max(rx[1], panKeepLeft) }
            Clamp.KEEP -> { loX = max(rx[0] - sx, min(rx[0], left)); hiX = min(rx[1] + sx, max(rx[1], left)) }
        }
        val loY: Float
        val hiY: Float
        when (how) {
            Clamp.NORMAL -> { loY = ry[0]; hiY = ry[1] }
            Clamp.KEEP -> { loY = max(ry[0] - sy, min(ry[0], top)); hiY = min(ry[1] + sy, max(ry[1], top)) }
            else -> { loY = ry[0] - sy; hiY = ry[1] + sy }
        }
        tx += left.coerceIn(loX, max(loX, hiX)) - left
        ty += top.coerceIn(loY, max(loY, hiY)) - top
    }

    /** Halaman diputar atau digeser ke samping lebih jauh dari biasa (untuk tombol "Luruskan"). */
    var onViewTilted: ((Boolean) -> Unit)? = null
    private var tiltedShown = false

    private fun notifyViewState() {
        if (page == null || width == 0) return
        pageBox(clampBox)
        val rx = normalRange(clampBox.width(), width)
        val tilted = rot != 0f || clampBox.left < rx[0] - 2f || clampBox.left > rx[1] + 2f
        if (tilted != tiltedShown) {
            tiltedShown = tilted
            onViewTilted?.invoke(tilted)
        }
    }

    /** Kembalikan halaman tegak dan di tengah (skala tetap). */
    fun straighten() {
        if (page == null || width == 0) return
        pt[0] = width / 2f
        pt[1] = height / 2f
        inverseMatrix.mapPoints(pt)
        rot = 0f
        tx = width / 2f - pt[0] * scale
        ty = height / 2f - pt[1] * scale
        clamp(Clamp.NORMAL)
        updateMatrix()
        viewMoved()
    }

    /** Selesai memutar: luruskan ke kelipatan 90° bila sudah dekat, dengan titik tengah layar tetap di tempatnya. */
    private fun snapRotation() {
        if (rot == 0f) return
        var r = ((rot % 360f) + 540f) % 360f - 180f
        val near = (r / 90f).roundToInt() * 90f
        if (abs(r - near) <= 7f) r = near
        if (abs(r) < 0.01f) r = 0f
        if (r == rot) return
        pt[0] = width / 2f
        pt[1] = height / 2f
        inverseMatrix.mapPoints(pt)
        rot = r
        clampM.setScale(scale, scale)
        if (rot != 0f) clampM.postRotate(rot)
        val q = floatArrayOf(pt[0], pt[1])
        clampM.mapPoints(q)
        tx = width / 2f - q[0]
        ty = height / 2f - q[1]
        clamp()
        updateMatrix()
        transformDirty = true
    }

    private fun toPage(vx: Float, vy: Float): PointF {
        pt[0] = vx; pt[1] = vy
        inverseMatrix.mapPoints(pt)
        return PointF(pt[0], pt[1])
    }

    // ================= menggambar =================

    private fun ensureCache(): Boolean {
        if (width <= 0 || height <= 0) return false
        val c = cache
        // Saat keyboard muncul tampilan memendek; bitmap tetap setinggi semula supaya bagian di balik keyboard
        // sudah tergambar ketika keyboard turun lagi (tidak ada jalur kosong).
        val needH = if (c != null && c.width == width) max(height, c.height) else height
        if (c == null || c.width != width || c.height != needH) {
            c?.recycle()
            val bmp = Bitmap.createBitmap(width, needH, Bitmap.Config.ARGB_8888)
            cache = bmp
            cacheCanvas = Canvas(bmp)
        }
        return true
    }

    private fun renderCache() {
        val p = page ?: return
        if (!ensureCache()) return
        val c = cacheCanvas ?: return
        // Gambar yang sedang disiapkan di latar tidak dipakai lagi.
        viewToken++
        viewStale = false
        c.drawColor(deskColor, PorterDuff.Mode.SRC)
        if (freshPage || turnSnap != null || turnDrag != null) blockingImages { drawPageInto(c, p, null) } else drawPageInto(c, p, null)
        freshPage = false
        viewMatrix.invert(cacheInverse)
        cacheMatrix.set(viewMatrix)
        cacheValid = true
        maybeRefreshBackdrop()
    }

    /** Jalankan [block] dengan foto dan halaman PDF dibuka saat itu juga bila belum ada sama sekali. */
    private inline fun <T> blockingImages(block: () -> T): T {
        val a = ImageCache.blockingOnMain
        val b = PdfPages.blockingOnMain
        ImageCache.blockingOnMain = true
        PdfPages.blockingOnMain = true
        try {
            return block()
        } finally {
            ImageCache.blockingOnMain = a
            PdfPages.blockingOnMain = b
        }
    }

    // ---- gambar di latar: alas seluruh halaman dan gambar layar setelah digeser ----

    private fun markBackdropDirty(delay: Long = 700L) {
        removeCallbacks(backdropJob)
        val fresh = backdropPage != null && backdropPage == page?.id
        postDelayed(backdropJob, if (fresh) delay else 60L)
    }

    /** Skala alas: setajam layar bila muat dalam batas memori, selain itu sedikit lebih kasar. */
    private fun backdropScaleFor(p: PageMeta): Float {
        var bs = min(scale, sqrt(backdropBudget / (p.width * p.height).coerceAtLeast(1f)))
        // Batas ukuran tekstur kartu grafis.
        bs = min(bs, 8000f / max(p.width, p.height).coerceAtLeast(1f))
        return bs
    }

    /** Alas perlu dibuat ulang bila halamannya lain, atau sekarang diperbesar jauh lebih tajam. */
    private fun maybeRefreshBackdrop() {
        val p = page ?: return
        if (backdropPage != p.id) { markBackdropDirty(); return }
        if (backdropScaleFor(p) > backdropScale * 1.15f) markBackdropDirty(150L)
    }

    private fun requestBackdrop() {
        removeCallbacks(backdropJob)
        val p = page ?: return
        if (!isAttachedToWindow || p.width <= 0f || p.height <= 0f) return
        // Sedang menulis atau menghapus: tunggu sampai sapuannya selesai.
        if (builder != null || mode == Mode.ERASE) { postDelayed(backdropJob, 250L); return }
        val bs = backdropScaleFor(p)
        if (bs <= 0f) return
        val w = (p.width * bs).roundToInt().coerceAtLeast(1)
        val h = (p.height * bs).roundToInt().coerceAtLeast(1)
        if (backdropInFlight == contentVersion && backdropInFlightPage == p.id) return
        val token = ++backdropToken
        val version = contentVersion
        backdropInFlight = version
        backdropInFlightPage = p.id
        val content = PageContent(ArrayList(strokes), ArrayList(texts), ArrayList(items))
        val dir = imageDir
        // Tidak memakai ulang gambar yang sedang jadi lembaran yang dibalik.
        val reuse = backdropSpare?.takeIf { !it.isRecycled && it.width == w + 2 && it.height == h + 2 && it !== curlSheet }
        backdropSpare = null
        CanvasWorkers.backdrop.execute {
            val bmp = runCatching {
                val b = reuse ?: Bitmap.createBitmap(w + 2, h + 2, Bitmap.Config.ARGB_8888)
                if (token == backdropToken) renderSheet(b, p, content, dir)
                b
            }.getOrNull()
            CanvasWorkers.main.post {
                if (token == backdropToken) backdropInFlight = -1L
                if (bmp == null) return@post
                if (token != backdropToken || page?.id != p.id || !isAttachedToWindow) {
                    if (isAttachedToWindow) backdropSpare = bmp
                    return@post
                }
                // Yang lama disimpan untuk dipakai ulang nanti (bukan sekarang: bisa jadi masih dipakai bingkai
                // yang sedang digambar; permintaan berikutnya paling cepat beberapa ratus milidetik lagi).
                backdrop?.let { if (it !== bmp) backdropSpare = it }
                backdrop = bmp
                backdropPage = p.id
                backdropScale = bs
                backdropVersion = version
                if (gestureActive || settling || viewStale) invalidate()
            }
        }
    }

    private fun drawBackdrop(canvas: Canvas, p: PageMeta) {
        val bd = backdrop ?: return
        if (backdropPage != p.id) return
        canvas.save()
        canvas.concat(viewMatrix)
        val sh = 3f * density / scale
        framePaint.style = Paint.Style.FILL
        framePaint.color = 0x1A000000
        canvas.drawRect(sh * 0.3f, sh, p.width + sh * 0.3f, p.height + sh, framePaint)
        sheetToPage(bd, p, backdropMatrix)
        canvas.drawBitmap(bd, backdropMatrix, backdropPaint)
        canvas.restore()
    }

    /**
     * Gambar seluruh halaman ke [b] (utas latar), dengan tepi 1 piksel transparan di sekelilingnya supaya tepi
     * kertas tetap halus saat gambarnya dilengkungkan.
     */
    private fun renderSheet(b: Bitmap, p: PageMeta, content: PageContent, dir: java.io.File?) {
        val w = b.width - 2
        val h = b.height - 2
        b.eraseColor(0)
        val c = Canvas(b)
        c.translate(1f, 1f)
        c.clipRect(0, 0, w, h)
        c.scale(w / p.width, h / p.height)
        InkRenderer.drawPage(c, p, content, dir, w / p.width)
        b.setHasMipMap(true)
        b.prepareToDraw()
    }

    /** Dari piksel gambar halaman (dengan tepi 1 piksel) ke satuan halaman. */
    private fun sheetToPage(b: Bitmap, p: PageMeta, out: Matrix) {
        out.setTranslate(-1f, -1f)
        out.postScale(p.width / (b.width - 2).coerceAtLeast(1), p.height / (b.height - 2).coerceAtLeast(1))
    }

    /** Alas halaman ini sudah sama persis dengan isinya sekarang. */
    private fun backdropFresh(p: PageMeta): Boolean =
        backdrop != null && backdropPage == p.id && backdropVersion == contentVersion

    // ---- halaman sebelah, disiapkan di latar untuk dibalik dengan jari ----

    private class NeighborSheet(val meta: PageMeta, val sig: Int, val bmp: Bitmap, val bs: Float)

    private val neighborSheets = object : LinkedHashMap<String, NeighborSheet>(4, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, NeighborSheet>?) = size > 2
    }
    private val neighborJobs = HashSet<String>()

    /** Skala dan letak kiri-atas halaman [meta] saat nanti dibuka (pas lebar layar, seperti fit). */
    private fun peekFit(meta: PageMeta): FloatArray {
        val sc = min((width - margin * 2) / meta.width, (height - margin * 2) / meta.height * 2.2f)
        val pw = meta.width * sc
        val ph = meta.height * sc
        val ptx = (width - pw) / 2f
        val pty = if (ph + margin * 2 <= height) (height - ph) / 2f else margin
        return floatArrayOf(sc, ptx, pty)
    }

    private fun contentSig(c: PageContent): Int {
        var h = 17
        for (o in c.strokes) h = h * 31 + System.identityHashCode(o)
        for (o in c.texts) h = h * 31 + System.identityHashCode(o)
        for (o in c.items) h = h * 31 + System.identityHashCode(o)
        return h
    }

    /** Siapkan gambar halaman sebelah di latar, supaya membalik dengan jari langsung mulai tanpa jeda. */
    fun prepareNeighbor(meta: PageMeta, content: PageContent) {
        if (!canCurl || width <= 0 || height <= 0 || meta.width <= 0f || meta.height <= 0f) return
        val sc = peekFit(meta)[0]
        var bs = min(sc, sqrt(backdropBudget / (meta.width * meta.height)))
        bs = min(bs, 8000f / max(meta.width, meta.height))
        if (bs <= 0f) return
        val sig = contentSig(content)
        neighborSheets[meta.id]?.let { if (it.meta == meta && it.sig == sig && abs(it.bs - bs) < 0.01f) return }
        if (!neighborJobs.add(meta.id)) return
        val w = (meta.width * bs).roundToInt().coerceAtLeast(1)
        val h = (meta.height * bs).roundToInt().coerceAtLeast(1)
        val snap = PageContent(ArrayList(content.strokes), ArrayList(content.texts), ArrayList(content.items))
        val dir = imageDir
        CanvasWorkers.backdrop.execute {
            val bmp = runCatching { Bitmap.createBitmap(w + 2, h + 2, Bitmap.Config.ARGB_8888).also { renderSheet(it, meta, snap, dir) } }.getOrNull()
            CanvasWorkers.main.post {
                neighborJobs.remove(meta.id)
                if (bmp != null && isAttachedToWindow && page?.id != meta.id) neighborSheets[meta.id] = NeighborSheet(meta, sig, bmp, bs)
            }
        }
    }

    /**
     * Setelah halaman digeser/diperbesar, gambar layar yang tajam disiapkan di latar sementara gambar lama tetap
     * tampil mengikuti posisi baru, jadi jari diangkat tidak membuat layar tersendat. Hasil: false bila harus
     * digambar langsung (misalnya ada animasi atau sapuan penghapus yang belum tersimpan).
     */
    private fun requestViewRender(): Boolean {
        val p = page ?: return false
        val old = cache ?: return false
        if (!cacheValid || needsFit || settling || animSkip.isNotEmpty() || imageMasks.isNotEmpty() ||
            textAnim != null || turnDrag != null || turnSnap != null || old.width != width
        ) return false
        val w = old.width
        val h = old.height
        val token = ++viewToken
        val version = contentVersion
        val m = Matrix(viewMatrix)
        val s = scale
        val desk = deskColor
        val dens = density
        val dir = imageDir
        val content = PageContent(ArrayList(strokes), ArrayList(texts), ArrayList(items))
        viewStale = true
        CanvasWorkers.view.execute {
            if (token != viewToken) return@execute
            val bmp = runCatching {
                val b = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
                val c = Canvas(b)
                c.drawColor(desk, PorterDuff.Mode.SRC)
                paintPageOffThread(c, p, m, s, dens, dir, content)
                b.prepareToDraw()
                b
            }.getOrNull()
            CanvasWorkers.main.post { onViewRendered(token, version, p, m, bmp) }
        }
        return true
    }

    private fun onViewRendered(token: Int, version: Long, p: PageMeta, m: Matrix, bmp: Bitmap?) {
        if (token != viewToken || !viewStale) return
        val cur = cache
        if (bmp == null || version != contentVersion || page !== p || !cacheValid || cur == null ||
            cur.width != bmp.width || cur.height != bmp.height
        ) {
            // Ada yang berubah selagi disiapkan: gambar langsung saja.
            viewStale = false
            invalidateViewCache()
            return
        }
        // Yang lama tidak di-recycle: bisa jadi masih dipakai bingkai yang sedang digambar.
        cache = bmp
        cacheCanvas = Canvas(bmp)
        m.invert(cacheInverse)
        cacheMatrix.set(m)
        if (!gestureActive && !settling) {
            if (m == viewMatrix) viewStale = false
            else if (!requestViewRender()) { viewStale = false; invalidateViewCache() }
        }
        invalidate()
    }

    /** Sama seperti [drawPageInto], tapi untuk utas latar: semua yang dipakai diberikan, cat dibuat sendiri. */
    private fun paintPageOffThread(c: Canvas, p: PageMeta, m: Matrix, s: Float, dens: Float, dir: java.io.File?, content: PageContent) {
        val frame = Paint(Paint.ANTI_ALIAS_FLAG)
        c.save()
        c.concat(m)
        val sh = 3f * dens / s
        frame.style = Paint.Style.FILL
        frame.color = 0x1A000000
        c.drawRect(sh * 0.3f, sh, p.width + sh * 0.3f, p.height + sh, frame)
        InkRenderer.drawPaper(c, p, Paint(), dir, s)
        frame.style = Paint.Style.STROKE
        frame.strokeWidth = 1f / s
        frame.color = 0x22000000
        c.drawRect(0f, 0f, p.width, p.height, frame)
        c.clipRect(0f, 0f, p.width, p.height)
        val inv = Matrix()
        m.invert(inv)
        val vis = RectF(0f, 0f, c.width.toFloat(), c.height.toFloat())
        inv.mapRect(vis)
        InkRenderer.drawLayers(c, content.strokes, content.texts, content.items, dir, s, vis, Paint(), Paint(), Path(), null)
        c.restore()
    }

    /** Gambar ulang sebagian bitmap cadangan (satuan halaman), misalnya bekas sapuan penghapus. */
    private fun redrawCacheRegion(pageRect: RectF) {
        contentVersion++
        markBackdropDirty()
        val p = page ?: return
        val c = cacheCanvas ?: return
        regionRect.set(pageRect)
        regionRect.inset(-2f / scale, -2f / scale)
        viewMatrix.mapRect(regionRect)
        regionRect.roundOut(regionClip)
        if (!regionClip.intersect(0, 0, width, height)) return
        c.save()
        c.clipRect(regionClip)
        c.drawColor(deskColor, PorterDuff.Mode.SRC)
        drawPageInto(c, p, regionClip)
        c.restore()
    }

    /** Kertas, latar, dan isi halaman ke bitmap cadangan; [clip] membatasi ke bagian layar tertentu. */
    private fun drawPageInto(c: Canvas, p: PageMeta, clip: Rect?) {
        c.save()
        c.concat(viewMatrix)

        val sh = 3f * density / scale
        framePaint.style = Paint.Style.FILL
        framePaint.color = 0x1A000000
        c.drawRect(sh * 0.3f, sh, p.width + sh * 0.3f, p.height + sh, framePaint)

        InkRenderer.drawPaper(c, p, renderPaint, imageDir, scale)

        framePaint.style = Paint.Style.STROKE
        framePaint.strokeWidth = 1f / scale
        framePaint.color = 0x22000000
        c.drawRect(0f, 0f, p.width, p.height, framePaint)

        c.clipRect(0f, 0f, p.width, p.height)
        if (clip != null) visible.set(clip) else visible.set(0f, 0f, c.width.toFloat(), c.height.toFloat())
        inverseMatrix.mapRect(visible)
        drawContent(c, strokes, texts, items, visible)
        c.restore()
    }

    /** Urutan lapisan: benda tempelan, stabilo, teks ketik, lalu tulisan tangan paling atas. */
    private fun drawContent(c: Canvas, ss: List<Stroke>, ts: List<TextItem>, its: List<PageItem>, vis: RectF?) {
        InkRenderer.drawLayers(
            c, ss, ts, its, imageDir, scale, vis, itemPaint, renderPaint, renderPath,
            animSkip.takeIf { it.isNotEmpty() }, imageMasks.takeIf { it.isNotEmpty() },
        )
    }

    private fun drawIntoCache(s: Stroke) {
        pendingChange = null
        contentVersion++
        markBackdropDirty()
        val p = page
        val c = cacheCanvas
        if (p == null || c == null || !cacheValid || gestureActive || settling || viewStale) {
            settleNow()
            invalidateCache(); return
        }
        c.save()
        c.concat(viewMatrix)
        c.clipRect(0f, 0f, p.width, p.height)
        InkRenderer.drawStroke(c, s, renderPaint, renderPath)
        c.restore()
        invalidate()
    }

    private fun recordForPrediction(e: MotionEvent) {
        if (!predictorTried) {
            // Dibuat begitu pena pertama kali menyentuh; setelah itu semua sentuhan dicatat supaya urutannya utuh.
            val down = e.actionMasked == MotionEvent.ACTION_DOWN || e.actionMasked == MotionEvent.ACTION_POINTER_DOWN
            if (!down || !isStylus(e, e.actionIndex)) return
            predictorTried = true
            predictor = runCatching { MotionEventPredictor.newInstance(this) }.getOrNull()
        }
        val pr = predictor ?: return
        runCatching { pr.record(e) }
    }

    /**
     * Ekor perkiraan dari titik terakhir ke arah pena bergerak. Hanya untuk pena dan kuas yang ditulis dengan
     * stylus (warnanya pekat, jadi sambungannya tidak terlihat), tidak saat mengikuti penggaris. Panjangnya
     * dibatasi supaya tidak meleset jauh saat pena berhenti mendadak; ekor ini hilang di bingkai berikutnya.
     */
    private fun drawPrediction(canvas: Canvas, b: StrokeBuilder) {
        if (mode != Mode.DRAW || activeIsFinger || rulerEdge != null || b.size < 2) return
        if (b.tool != Tool.PEN && b.tool != Tool.BRUSH) return
        val pr = predictor ?: return
        // Pena berhenti (tidak ada gerakan baru): jangan tinggalkan ekor yang menjorok ke depan.
        if (SystemClock.uptimeMillis() - lastInkMove > 30) return
        val ev = runCatching { pr.predict() }.getOrNull() ?: return
        var n = 1
        try {
            var k = ev.findPointerIndex(activePointer)
            if (k < 0) { if (ev.pointerCount == 1) k = 0 else return }
            tailXs[0] = b.lastX; tailYs[0] = b.lastY
            val maxD = 22f * density / scale
            val total = ev.historySize + 1
            for (h in 0 until total) {
                if (n >= tailXs.size) break
                pt[0] = if (h < ev.historySize) ev.getHistoricalX(k, h) else ev.getX(k)
                pt[1] = if (h < ev.historySize) ev.getHistoricalY(k, h) else ev.getY(k)
                inverseMatrix.mapPoints(pt)
                val dx = pt[0] - b.lastX
                val dy = pt[1] - b.lastY
                val d = hypot(dx, dy)
                if (d > maxD) {
                    val f = maxD / d
                    tailXs[n] = b.lastX + dx * f; tailYs[n] = b.lastY + dy * f
                    n++
                    break
                }
                tailXs[n] = pt[0]; tailYs[n] = pt[1]
                n++
            }
        } finally {
            ev.recycle()
        }
        if (n < 2) return
        val p = b.ps[b.size - 1]
        for (i in 0 until n) tailPs[i] = p
        InkRenderer.drawPoints(canvas, b.tool, b.color, b.width, tailXs, tailYs, tailPs, n, livePaint, livePath)
        // Bila pena berhenti, bingkai berikut menggambar tanpa ekor.
        postInvalidateDelayed(40)
    }

    /** Siapkan foto dan halaman PDF di halaman lain (biasanya halaman sebelah) pada skala tampilan sekarang. */
    fun prefetchMedia(meta: PageMeta, content: PageContent) {
        val dir = imageDir ?: return
        runCatching {
            PaperBackgrounds.prefetch(meta, dir, scale)
            for (item in content.items) if (item is ImageItem) ImageCache.prefetch(File(dir, item.file), InkRenderer.imageBucket(item, scale))
        }
        runCatching { prepareNeighbor(meta, content) }
    }

    override fun onDraw(canvas: Canvas) {
        // Di dalam Compose, gambar yang meluber keluar kotak kanvas menutupi bilah alat dan daftar halaman.
        val save = canvas.save()
        canvas.clipRect(0f, 0f, width.toFloat(), height.toFloat())
        drawView(canvas)
        canvas.restoreToCount(save)
    }

    private fun drawView(canvas: Canvas) {
        val p = page
        if (p == null) {
            canvas.drawColor(deskColor); return
        }
        if (needsFit) fit()
        if (!eraseDirtyEmpty && cacheValid && !gestureActive && !settling && !viewStale) flushEraseDirty()
        if (!cacheValid && !gestureActive) renderCache()

        val bmp = cache
        if (gestureActive || settling || viewStale) {
            // Halaman sedang bergerak: alas seluruh halaman di bawah, gambar layar terakhir (tajam) di atasnya.
            canvas.drawColor(deskColor)
            drawBackdrop(canvas, p)
            if (bmp != null && cacheValid) {
                drawMatrix.set(cacheInverse)
                drawMatrix.postConcat(viewMatrix)
                canvas.drawBitmap(bmp, drawMatrix, bitmapPaint)
            }
        } else if (bmp != null && cacheValid) {
            canvas.drawBitmap(bmp, 0f, 0f, null)
        } else {
            canvas.drawColor(deskColor)
        }
        turnDrag?.let { drawTurnDrag(canvas, it, p) }
        if (turnSnap != null) drawPageTurn(canvas, p)

        canvas.save()
        canvas.concat(viewMatrix)
        canvas.clipRect(0f, 0f, p.width, p.height)
        val b = builder
        if (b != null && b.size > 0) {
            if (b.tool == Tool.ERASER) {
                // Pratinjau kuas penghapus: sapuan berwarna kertas; hasil sebenarnya tampil saat diangkat.
                InkRenderer.drawSoftErase(canvas, Color.alpha(b.color) / 255f, b.width, b.xs, b.ys, b.ps, b.size, livePaint, p.paperColor)
            } else {
                InkRenderer.drawPoints(canvas, b.tool, b.color, b.width, b.xs, b.ys, b.ps, b.size, livePaint, livePath)
                drawPrediction(canvas, b)
            }
        }
        if (mode == Mode.SHAPE) {
            for (s in Shapes.build(shapeKind, shapeX0, shapeY0, shapeX1, shapeY1, color, baseWidth)) {
                InkRenderer.drawStroke(canvas, s, livePaint, livePath)
            }
        }
        if (mode == Mode.TAPE && tapeMovedEnough()) buildTape()?.let { tp ->
            canvas.save()
            if (tp.rotation != 0f) canvas.rotate(tp.rotation, tp.cx, tp.cy)
            tapePaint.reset()
            InkRenderer.drawTape(canvas, tp, tapePaint, 0.85f)
            canvas.restore()
        }
        lifted?.let { l ->
            val preview = widthPreview
            if (preview != null && (dragCorner == 5 || dragCorner == 6)) {
                // Lebar kotak teks berubah: barisnya mengalir ulang, ukuran huruf tetap.
                InkRenderer.drawText(canvas, preview)
            } else {
                dragMatrix(liftMatrix)
                canvas.concat(liftMatrix)
                drawContent(canvas, l.strokes.map { it.second }, l.texts.map { it.second }, l.items.map { it.second }, null)
            }
        }
        canvas.restore()
        val pf = playingFile
        if (pf != null) {
            val a = items.firstOrNull { it is id.hananote.data.AudioItem && it.file == pf } as? id.hananote.data.AudioItem
            if (a != null) {
                canvas.save()
                canvas.concat(viewMatrix)
                InkRenderer.drawAudio(canvas, a, livePaint, playing = true, progress = playProgress)
                canvas.restore()
            }
        }
        if (textAnim != null) {
            canvas.save()
            canvas.concat(viewMatrix)
            canvas.clipRect(0f, 0f, p.width, p.height)
            drawTextAnim(canvas)
            canvas.restore()
        }

        if (showEraser) {
            canvas.drawCircle(eraserX, eraserY, max(eraserWidth / 2f * scale, 4f * density), cursorPaint)
        }
        drawSelection(canvas)
        drawFlash(canvas)
        drawMapSession(canvas)
        drawTyping(canvas)
        if (typeTarget == null && mapId == 0L) reportAnchor(null, null)
        if (rulerVisible) drawRuler(canvas)
    }

    /**
     * Empat sudut kotak pilihan di layar (kiri-atas, kanan-atas, kanan-bawah, kiri-bawah), sudah
     * diputar, ke [corners]. Bila bisa diputar, titik tengah sisi atas dan pegangan putar ke [handlePos].
     */
    private fun selectionGeometry(): Boolean {
        if (!hasSelection) return false
        val rect = if (lifted != null) dragTo else selRect
        val rot = if (lifted != null) dragRot1 else selRotation()
        val pad = 4f * density / scale
        val hw = rect.width() / 2f + pad
        val hh = rect.height() / 2f + pad
        val lx = floatArrayOf(-hw, hw, hw, -hw)
        val ly = floatArrayOf(-hh, -hh, hh, hh)
        for (k in 0..3) {
            fromLocal(lx[k], ly[k], rect.centerX(), rect.centerY(), rot)
            corners[k * 2] = local[0]
            corners[k * 2 + 1] = local[1]
        }
        viewMatrix.mapPoints(corners)
        val mx = (corners[0] + corners[2]) / 2f
        val my = (corners[1] + corners[3]) / 2f
        val cx = (corners[0] + corners[4]) / 2f
        val cy = (corners[1] + corners[5]) / 2f
        var ux = mx - cx
        var uy = my - cy
        val len = hypot(ux, uy)
        if (len < 0.5f) { ux = 0f; uy = -1f } else { ux /= len; uy /= len }
        handlePos[0] = mx
        handlePos[1] = my
        handlePos[2] = mx + ux * 30f * density
        handlePos[3] = my + uy * 30f * density
        val off = 14f * density
        sidePos[0] = (corners[2] + corners[4]) / 2f + off
        sidePos[1] = (corners[3] + corners[5]) / 2f
        sidePos[2] = (corners[0] + corners[6]) / 2f - off
        sidePos[3] = (corners[1] + corners[7]) / 2f
        return true
    }

    private fun selectionRotatable() = selItem?.canRotate == true

    private fun drawSelection(canvas: Canvas) {
        lasso?.let { pts ->
            if (pts.size >= 4) {
                lassoPath.rewind()
                val q = FloatArray(pts.size)
                for (k in pts.indices) q[k] = pts[k]
                viewMatrix.mapPoints(q)
                lassoPath.moveTo(q[0], q[1])
                var k = 2
                while (k < q.size) { lassoPath.lineTo(q[k], q[k + 1]); k += 2 }
                lassoFill.style = Paint.Style.FILL
                lassoFill.color = InkRenderer.withAlpha(accentColor, 0.07f)
                canvas.drawPath(lassoPath, lassoFill)
                lassoLine.color = accentColor
                lassoLine.strokeWidth = 1.6f * density
                lassoLine.pathEffect = android.graphics.DashPathEffect(floatArrayOf(7f * density, 5f * density), 0f)
                canvas.drawPath(lassoPath, lassoLine)
            }
        }
        if (!selectionGeometry()) return
        selPaint.color = accentColor
        selPath.rewind()
        selPath.moveTo(corners[0], corners[1])
        for (k in 1..3) selPath.lineTo(corners[k * 2], corners[k * 2 + 1])
        selPath.close()
        canvas.drawPath(selPath, selPaint)

        if (selectionRotatable()) {
            val hx = handlePos[2]
            val hy = handlePos[3]
            handlePaint.style = Paint.Style.STROKE
            handlePaint.strokeWidth = 1.5f * density
            handlePaint.color = accentColor
            canvas.drawLine(handlePos[0], handlePos[1], hx, hy, handlePaint)
            handlePaint.style = Paint.Style.FILL
            canvas.drawCircle(hx, hy, 11f * density, handlePaint)
            // Ikon panah melingkar.
            val r = 5.5f * density
            handlePaint.style = Paint.Style.STROKE
            handlePaint.strokeWidth = 1.8f * density
            handlePaint.strokeCap = Paint.Cap.ROUND
            handlePaint.color = 0xFFFFFFFF.toInt()
            canvas.drawArc(hx - r, hy - r, hx + r, hy + r, -60f, 270f, false, handlePaint)
            val th = Math.toRadians(210.0)
            val ex = hx + r * cos(th).toFloat()
            val ey = hy + r * sin(th).toFloat()
            val back = atan2(-cos(th), sin(th))
            val wing = 3.6f * density
            for (sgn in intArrayOf(-1, 1)) {
                val a = back + sgn * 0.75
                canvas.drawLine(ex, ey, ex + wing * cos(a).toFloat(), ey + wing * sin(a).toFloat(), handlePaint)
            }
            handlePaint.strokeCap = Paint.Cap.BUTT
            if (lifted != null && dragCorner == 4) {
                anglePaint.color = accentColor
                anglePaint.textSize = 13f * density
                anglePaint.isFakeBoldText = true
                val deg = dragRot1.roundToInt()
                canvas.drawText("$deg°", hx, hy - 18f * density, anglePaint)
            }
        }

        if (selText != null) {
            // Pegangan samping berbentuk pil: geser untuk melebarkan atau menyempitkan kotak teks.
            val pw = 3.5f * density
            val ph = 11f * density
            for (k in 0..1) {
                val sx = sidePos[k * 2]
                val sy = sidePos[k * 2 + 1]
                handlePaint.style = Paint.Style.FILL
                handlePaint.color = accentColor
                canvas.drawRoundRect(sx - pw, sy - ph, sx + pw, sy + ph, pw, pw, handlePaint)
            }
        }

        (selItem as? TableItem)?.takeIf { selTexts.isEmpty() && selStrokes.isEmpty() }?.let { drawTableLineHandles(canvas, it) }

        val hr = 7f * density
        for (k in 0..3) {
            val hx = corners[k * 2]
            val hy = corners[k * 2 + 1]
            handlePaint.style = Paint.Style.FILL
            handlePaint.color = 0xFFFFFFFF.toInt()
            canvas.drawCircle(hx, hy, hr, handlePaint)
            handlePaint.style = Paint.Style.STROKE
            handlePaint.strokeWidth = 2f * density
            handlePaint.color = accentColor
            canvas.drawCircle(hx, hy, hr, handlePaint)
        }
    }


    /**
     * Tanda bahwa garis dalam tabel terpilih bisa digeser: pil kecil di atas tiap garis kolom dan di kiri tiap
     * garis baris; garis yang sedang digeser disorot penuh.
     */
    private fun drawTableLineHandles(canvas: Canvas, t: TableItem) {
        val q = FloatArray(4)
        val pw = 3f * density
        val pl = 9f * density
        handlePaint.style = Paint.Style.FILL
        handlePaint.color = accentColor
        val dragging = lineTable != null && lineTable?.id == t.id
        for (k in 1 until t.cols) {
            q[0] = t.x + t.colEdge(k); q[1] = t.y
            q[2] = q[0]; q[3] = t.y + t.h
            viewMatrix.mapPoints(q)
            if (dragging && lineIsCol && lineIndex == k) {
                handlePaint.strokeWidth = 2.5f * density
                handlePaint.style = Paint.Style.STROKE
                canvas.drawLine(q[0], q[1], q[2], q[3], handlePaint)
                handlePaint.style = Paint.Style.FILL
            }
            val cy = q[1] - pl - 3f * density
            canvas.drawRoundRect(q[0] - pw, cy - pl, q[0] + pw, cy + pl, pw, pw, handlePaint)
        }
        for (k in 1 until t.rows) {
            q[0] = t.x; q[1] = t.y + t.rowEdge(k)
            q[2] = t.x + t.w; q[3] = q[1]
            viewMatrix.mapPoints(q)
            if (dragging && !lineIsCol && lineIndex == k) {
                handlePaint.strokeWidth = 2.5f * density
                handlePaint.style = Paint.Style.STROKE
                canvas.drawLine(q[0], q[1], q[2], q[3], handlePaint)
                handlePaint.style = Paint.Style.FILL
            }
            val cx = q[0] - pl - 3f * density
            canvas.drawRoundRect(cx - pl, q[1] - pw, cx + pl, q[1] + pw, pw, pw, handlePaint)
        }
    }

    // ================= mengetik langsung di halaman =================

    /**
     * Yang sedang diketik langsung di halaman, tanpa kolom ketik terpisah: teks biasa, isi stiker, atau satu ide
     * mind map. Teksnya tetap digambar seperti biasa; di atasnya kursor berkedip, blok pilihan, dan garis bawah
     * kata yang sedang disusun keyboard.
     */
    private abstract inner class TypeTarget {
        abstract val text: String
        abstract fun layout(): StaticLayout
        /** Dari koordinat tata letak teks ke halaman. */
        abstract fun toPage(out: Matrix)
        /** Kotak sasaran dalam koordinat tata letak (bingkai, ketukan, dan letak bilah alat). */
        abstract fun box(out: RectF)
        abstract fun setText(value: String)
        abstract fun end(commit: Boolean)
        open val showFrame: Boolean get() = true
        open val caretColor: Int get() = accentColor
        /** Teks di garis kertas: kursor berhenti di garisnya, tidak menembus ke bawah. */
        open val onRule: Boolean get() = false
    }

    private inner class TextTarget : TypeTarget() {
        private fun cur(): TextItem? = editCurrent
        override val text: String get() = cur()?.text ?: ""
        override fun layout(): StaticLayout = cur()?.let { InkRenderer.layoutFor(it) } ?: emptyLayout
        override fun toPage(out: Matrix) {
            val t = cur()
            if (t == null) out.reset() else out.setTranslate(t.x, t.y)
        }
        override fun box(out: RectF) {
            val t = cur() ?: run { out.setEmpty(); return }
            out.set(0f, 0f, t.width, max(layout().height.toFloat(), t.size * 1.25f))
        }
        override fun setText(value: String) = updateTextEdit(value)
        override fun end(commit: Boolean) = endTextEdit(commit)
        // Mode Teks: mengetik seperti di pengolah kata, tanpa kotak di sekeliling teks.
        override val showFrame: Boolean get() = inputMode != InputMode.TYPE
        override val onRule: Boolean get() = (editCurrent?.linePitch ?: 0f) > 0f
    }

    private inner class StickyTarget : TypeTarget() {
        override val text: String get() = stickyNow()?.text ?: ""
        override fun layout(): StaticLayout = stickyNow()?.let { InkRenderer.stickyLayout(it) } ?: emptyLayout
        override fun toPage(out: Matrix) {
            val it = stickyNow() ?: run { out.reset(); return }
            out.setTranslate(InkRenderer.stickyTextLeft(it), InkRenderer.stickyTextTop(it))
            if (it.rotation != 0f) out.postRotate(it.rotation, it.cx, it.cy)
        }
        override fun box(out: RectF) {
            val it = stickyNow() ?: run { out.setEmpty(); return }
            val l = InkRenderer.stickyTextLeft(it) - it.x
            val t = InkRenderer.stickyTextTop(it) - it.y
            out.set(-l, -t, it.w - l, it.h - t)
        }
        override fun setText(value: String) {
            val c = stickyNow() ?: return
            setStickyLive(c.withText(value, c.font, c.textSize, c.align))
        }
        override fun end(commit: Boolean) = endStickyEdit(commit)
    }

    private inner class CellTarget : TypeTarget() {
        override val text: String get() = cellTable()?.cell(cellR, cellC) ?: ""
        override fun layout(): StaticLayout = cellTable()?.let { InkRenderer.cellLayout(it, cellR, cellC) } ?: emptyLayout
        override fun toPage(out: Matrix) {
            val t = cellTable() ?: run { out.reset(); return }
            out.setTranslate(InkRenderer.cellTextLeft(t, cellC), InkRenderer.cellTextTop(t, cellR, layout()))
        }
        override fun box(out: RectF) {
            val t = cellTable() ?: run { out.setEmpty(); return }
            val left = InkRenderer.cellTextLeft(t, cellC)
            val top = InkRenderer.cellTextTop(t, cellR, layout())
            out.set(
                t.x + t.colEdge(cellC) - left, t.y + t.rowEdge(cellR) - top,
                t.x + t.colEdge(cellC + 1) - left, t.y + t.rowEdge(cellR + 1) - top,
            )
        }
        override fun setText(value: String) {
            val t = cellTable() ?: return
            setTableLive(InkRenderer.fitTable(t.withCell(cellR, cellC, value)))
        }
        override fun end(commit: Boolean) = endCellEdit(commit)
    }

    private inner class NodeTarget : TypeTarget() {
        private fun node(): Pair<MindMapItem, MindNode>? {
            val m = mapItem() ?: return null
            val n = m.nodes.firstOrNull { it.id == mapTyping } ?: return null
            return m to n
        }
        private fun depthOf(m: MindMapItem, n: MindNode) = MindMaps.depths(m.nodes)[n.id] ?: 0
        override val text: String get() = node()?.second?.text ?: ""
        override fun layout(): StaticLayout {
            val (m, n) = node() ?: return emptyLayout
            return MindMapRenderer.textLayout(n.text, depthOf(m, n))
        }
        override fun toPage(out: Matrix) {
            val (m, n) = node() ?: run { out.reset(); return }
            val d = depthOf(m, n)
            val o = MindMapRenderer.textOrigin(n, d, MindMapRenderer.textLayout(n.text, d))
            mapMatrix(m, out)
            out.preTranslate(o[0], o[1])
        }
        override fun box(out: RectF) {
            val (m, n) = node() ?: run { out.setEmpty(); return }
            val d = depthOf(m, n)
            val o = MindMapRenderer.textOrigin(n, d, MindMapRenderer.textLayout(n.text, d))
            val sz = MindMapRenderer.size(n, d)
            out.set(n.x - sz[0] / 2f - o[0], n.y - sz[1] / 2f - o[1], n.x + sz[0] / 2f - o[0], n.y + sz[1] / 2f - o[1])
        }
        override fun setText(value: String) = setNodeText(value)
        override fun end(commit: Boolean) = endNodeTyping(commit)
        override val showFrame: Boolean get() = false
        override val caretColor: Int
            get() {
                val (m, n) = node() ?: return accentColor
                // Ide utama berwarna pekat dengan tulisan putih.
                return if (depthOf(m, n) == 0) 0xFFFFFFFF.toInt() else accentColor
            }
    }

    private val emptyLayout: StaticLayout by lazy {
        StaticLayout.Builder.obtain("", 0, 0, TextPaint(), 10).build()
    }

    private var typeTarget: TypeTarget? = null
    private var selA = 0
    private var selB = 0
    private var compA = -1
    private var compB = -1
    private var caretOn = true
    private val caretBlink: Runnable = object : Runnable {
        override fun run() {
            if (typeTarget == null) return
            caretOn = !caretOn
            invalidate()
            postDelayed(this, 530L)
        }
    }
    private val typeMatrix = Matrix()
    private val typeInverse = Matrix()
    private val typeBox = RectF()
    private val typePath = Path()
    private val typePaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val anchorRect = RectF()
    private val anchorLast = RectF()
    private var anchorKnown = false
    private var typeAnchorOff = 0
    private var typeWord = false
    private var typeWordA = 0
    private var typeWordB = 0
    private var typeMoved = false
    private var lastTypeTap = 0L
    private var lastTypeTapX = 0f
    private var lastTypeTapY = 0f
    /** Jari menyentuh di luar yang sedang disunting: bila hanya diketuk, suntingan selesai. */
    private var tapOutside = false
    /** Mode baca: halaman hanya bisa dibaca, digulir, diperbesar, dan dibalik. */
    var readOnly = false
        set(value) {
            if (field == value) return
            field = value
            if (value) {
                disarmLongPress()
                clearSelection()
            }
            invalidate()
        }

    /** Ketukan pena juga dihitung sebagai ketukan (mode teks). */
    private var panTapAny = false
    private var checkTapX = 0f
    private var checkTapY = 0f
    /** Mode teks: tempat kosong di halaman diketuk (koordinat halaman), untuk mulai mengetik di sana. */
    var onTypeHere: ((Float, Float) -> Unit)? = null
    /** Mode teks: stiker diketuk, untuk mengetik isinya. */
    var onStickyTapped: ((StickyItem) -> Unit)? = null
    /** Jari menyentuh bagian kosong di dalam mind map: bila hanya diketuk, pilihan ide dilepas. */
    private var tapBlank = false

    /** Kursor (awal = akhir) atau blok pilihan dipindah dengan ketukan/seretan di teks. */
    var onTypeCursor: ((Int, Int) -> Unit)? = null
    /** Teks yang sedang diketik diketuk (misalnya supaya keyboard muncul lagi). */
    var onTypeTap: (() -> Unit)? = null
    /** Kotak centang di teks yang sedang diketik diketuk (posisinya di teks). */
    var onTypeCheck: ((Int) -> Unit)? = null
    private var typeCheckAt = -1
    /** Disentuh di luar yang sedang diketik: selesaikan ketikannya. */
    var onTypeOutside: (() -> Unit)? = null
    /** Letak di layar yang sedang diketik atau disunting, untuk bilah alat melayang; null bila tidak ada. */
    var onFloatAnchor: ((RectF?) -> Unit)? = null

    val isTyping: Boolean get() = typeTarget != null

    /** Kursor, blok pilihan, dan kata yang sedang disusun keyboard (-1: tidak ada). */
    fun setTypeSelection(start: Int, end: Int, compStart: Int, compEnd: Int) {
        val moved = end != selB
        // Kursor dipindah (bukan karena mengetik): gaya yang dipilih untuk huruf berikutnya batal.
        if (typePending >= 0 && !typeJustEdited && (start != end || start != typePendingAt)) typePending = -1
        typeJustEdited = false
        selA = start
        selB = end
        compA = compStart
        compB = compEnd
        caretOn = true
        removeCallbacks(caretBlink)
        if (typeTarget != null) postDelayed(caretBlink, 530L)
        if (moved) bringTypingIntoView()
        invalidate()
    }

    /** Isi yang sedang diketik diganti dari keyboard. */
    fun updateTyping(value: String) {
        typeTarget?.setText(value)
    }

    /** Selesaikan yang sedang diketik ([commit] false: kembalikan seperti semula). */
    fun endTyping(commit: Boolean) {
        typeTarget?.end(commit)
    }

    private fun startTyping(t: TypeTarget) {
        typeTarget = t
        selA = t.text.length
        selB = selA
        compA = -1
        compB = -1
        caretOn = true
        removeCallbacks(caretBlink)
        postDelayed(caretBlink, 530L)
        invalidate()
    }

    private fun stopTyping() {
        typeTarget = null
        removeCallbacks(caretBlink)
        invalidate()
    }

    /** Posisi huruf pada titik layar ini di teks yang sedang diketik. */
    private fun typeOffsetAt(vx: Float, vy: Float): Int {
        val tt = typeTarget ?: return 0
        val lay = tt.layout()
        tt.toPage(typeMatrix)
        if (!typeMatrix.invert(typeInverse)) return tt.text.length
        pt[0] = vx
        pt[1] = vy
        inverseMatrix.mapPoints(pt)
        typeInverse.mapPoints(pt)
        val line = lay.getLineForVertical(pt[1].roundToInt())
        return lay.getOffsetForHorizontal(line, pt[0]).coerceIn(0, tt.text.length)
    }

    private fun insideType(vx: Float, vy: Float): Boolean {
        val tt = typeTarget ?: return false
        tt.toPage(typeMatrix)
        if (!typeMatrix.invert(typeInverse)) return false
        tt.box(typeBox)
        pt[0] = vx
        pt[1] = vy
        inverseMatrix.mapPoints(pt)
        typeInverse.mapPoints(pt)
        val k = (typeMatrix.mapRadius(1f) * scale).coerceAtLeast(0.01f)
        val slop = 14f * density / k
        return pt[0] >= typeBox.left - slop && pt[0] <= typeBox.right + slop &&
            pt[1] >= typeBox.top - slop && pt[1] <= typeBox.bottom + slop
    }

    /** Posisi huruf pada titik halaman ini di teks [t]. */
    private fun textOffsetAt(t: TextItem, px: Float, py: Float): Int {
        val l = InkRenderer.layoutFor(t)
        val line = l.getLineForVertical((py - t.y).roundToInt())
        return l.getOffsetForHorizontal(line, px - t.x).coerceIn(0, t.text.length)
    }

    /** Sentuhan jari yang kemungkinan telapak tangan (pena sedang melayang, atau bidang sentuhnya lebar). */
    private fun palmLike(e: MotionEvent, i: Int): Boolean {
        if (isStylus(e, i)) return false
        return SystemClock.uptimeMillis() - lastStylusHover < 400 || e.getTouchMajor(i) > 60f * density
    }

    private fun wordAt(text: String, off: Int): Pair<Int, Int> {
        var a = off.coerceIn(0, text.length)
        var b = a
        while (a > 0 && text[a - 1].isLetterOrDigit()) a--
        while (b < text.length && text[b].isLetterOrDigit()) b++
        return a to b
    }

    private fun typeDown(e: MotionEvent, i: Int, stylus: Boolean) {
        val x = e.getX(i)
        val y = e.getY(i)
        activePointer = e.getPointerId(i)
        activeIsFinger = !stylus
        downX = x
        downY = y
        typeMoved = false
        // Sedang mengetik di sel tabel: garis dalam tabelnya tetap bisa digeser.
        cellTable()?.let { ct ->
            val pp = toPage(x, y)
            val line = tableLineAt(ct, pp.x, pp.y)
            if (line != null) {
                mode = Mode.TABLELINE
                lineTable = ct
                lineIsCol = line.first
                lineIndex = line.second
                lineOffset = if (lineIsCol) pp.x - (ct.x + ct.colEdge(lineIndex)) else pp.y - (ct.y + ct.rowEdge(lineIndex))
                lineMoved = false
                lineDownX = pp.x
                lineDownY = pp.y
                invalidate()
                return
            }
        }
        if (insideType(x, y)) {
            typeDownInside(e, i)
            return
        }
        // Di luar: jari tetap bisa menggeser halaman (ketukan menyelesaikan), pena langsung menyelesaikan.
        // Di mode teks pena juga menggeser, dan ketukannya memindah kursor ke tempat lain.
        if (!stylus || inputMode == InputMode.TYPE) {
            startPan(e, i)
            tapOutside = true
            panTapAny = true
            return
        }
        mode = Mode.TYPEOUT
    }

    private fun typeDownInside(e: MotionEvent, i: Int) {
        val x = e.getX(i)
        val y = e.getY(i)
        mode = Mode.TYPESEL
        activePointer = e.getPointerId(i)
        downX = x
        downY = y
        typeMoved = false
        // Kotak centang di teks yang sedang diketik: ketukan mencentangnya, bukan memindah kursor.
        typeCheckAt = typingBoxAt(x, y)
        if (typeCheckAt >= 0) {
            typeWord = false
            lastTypeTap = 0L
            return
        }
        val off = typeOffsetAt(x, y)
        val now = e.eventTime
        val dbl = now - lastTypeTap < 330L && hypot(x - lastTypeTapX, y - lastTypeTapY) < 30f * density
        lastTypeTap = if (dbl) 0L else now
        lastTypeTapX = x
        lastTypeTapY = y
        val tt = typeTarget ?: return
        if (dbl) {
            // Ketuk dua kali: pilih satu kata.
            val (a, b) = wordAt(tt.text, off)
            typeWord = true
            typeWordA = a
            typeWordB = b
            onTypeCursor?.invoke(a, b)
        } else {
            typeWord = false
            typeAnchorOff = off
            onTypeCursor?.invoke(off, off)
        }
    }

    private fun typeMove(x: Float, y: Float) {
        if (!typeMoved && hypot(x - downX, y - downY) < touchSlop) return
        typeMoved = true
        if (typeCheckAt >= 0) {
            // Diseret dari kotak centang: jadi blok pilihan biasa.
            typeCheckAt = -1
            typeAnchorOff = typeOffsetAt(downX, downY)
        }
        val off = typeOffsetAt(x, y)
        if (typeWord) {
            onTypeCursor?.invoke(min(typeWordA, off), max(typeWordB, off))
        } else {
            onTypeCursor?.invoke(typeAnchorOff, off)
        }
    }

    private fun typeUp() {
        val check = typeCheckAt
        typeCheckAt = -1
        if (check >= 0) {
            if (!typeMoved && onTypeCheck != null) {
                onTypeCheck?.invoke(check)
                performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
            }
            return
        }
        if (!typeMoved && !typeWord) onTypeTap?.invoke()
    }

    /** Selesaikan ketikan dan sesi mind map (sentuhan di luar). */
    private fun endInlineEditing() {
        if (typeTarget != null) onTypeOutside?.invoke()
        if (typeTarget != null) typeTarget?.end(commit = true)
        endMapSession()
    }

    /** Ketukan di luar yang sedang disunting: selesaikan, lalu ketukannya berlaku seperti biasa. */
    private fun tapOutsideEditing(vx: Float, vy: Float) {
        // Sedang mengetik di sel tabel dan sel lain di tabel yang sama diketuk: langsung pindah ke sel itu.
        val table = cellTableId
        if (table != 0L && onCellTapped != null) {
            val pp = toPage(vx, vy)
            val hit = tableCellAt(pp.x, pp.y)
            if (hit != null && hit.first.id == table) {
                onCellTapped?.invoke(hit.first, hit.second, hit.third)
                return
            }
        }
        endInlineEditing()
        tapAt(vx, vy)
    }

    /** Ketukan biasa di halaman: rekaman, kotak centang, teks, mind map, atau tautan. */
    private fun tapAt(vx: Float, vy: Float) {
        val pp = toPage(vx, vy)
        val px = pp.x
        val py = pp.y
        val audio = audioAt(px, py)
        if (audio != null) { onAudioTapped?.invoke(audio); return }
        // Selotip hafalan diketuk (juga di mode baca): buka atau tutup.
        tapeAt(px, py)?.let { toggleTape(it); return }
        // Mode baca: rekaman bisa diputar dan link bisa dibuka, selebihnya tidak bisa diubah.
        if (readOnly) { openLinkAt(vx, vy); return }
        if (toggleCheckboxAt(px, py)) return
        if (inputMode == InputMode.TYPE) {
            // Mode teks: ketuk teks untuk menaruh kursor di sana, ketuk tempat kosong untuk mulai mengetik.
            textBoxAt(px, py)?.let { onTextTapped?.invoke(it, textOffsetAt(it, px, py)); return }
            val st = items.lastOrNull { it is StickyItem && itemContains(it, px, py, 4f / scale) } as? StickyItem
            if (st != null && onStickyTapped != null) { onStickyTapped?.invoke(st); return }
            mindMapNodeAt(px, py)?.let { (m, n) -> onMindMapTapped?.invoke(m, n); return }
            // Mode teks: ketuk sel tabel langsung mengetik di dalamnya.
            tableCellAt(px, py)?.let { (t, r, c) -> if (onCellTapped != null) { onCellTapped?.invoke(t, r, c); return } }
            if (items.any { it is LinkItem && px >= it.x && px <= it.x + it.w && py >= it.y && py <= it.y + it.h }) {
                openLinkAt(vx, vy)
                return
            }
            onTypeHere?.invoke(px, py)
            return
        }
        textGlyphAt(px, py)?.let { pickText(it, px, py); return }
        mindMapNodeAt(px, py)?.let { (m, n) -> onMindMapTapped?.invoke(m, n); return }
        if (tableTap(px, py, null)) return
        openLinkAt(vx, vy)
    }

    /** Mode tulis: teks diketuk sekali dipilih (bisa digeser), diketuk dua kali langsung diketik. */
    var onTextPicked: (() -> Unit)? = null
    private var selTapText: TextItem? = null
    /** Teks dipilih dengan ketukan saat menulis: sentuhan di luar pilihan langsung menulis lagi. */
    private var pickedForMove = false
    private var selTapTime = 0L
    private var selTapX = 0f
    private var selTapY = 0f

    private fun pickText(t: TextItem, px: Float, py: Float) {
        if (onTextPicked == null) {
            onTextTapped?.invoke(t, textOffsetAt(t, px, py))
            return
        }
        onTextPicked?.invoke()
        select(t)
        pickedForMove = true
        selTapText = t
        selTapTime = SystemClock.uptimeMillis()
        selTapX = px
        selTapY = py
        invalidate()
    }

    /** Ketukan (tanpa geser) pada teks yang sedang dipilih: ketukan kedua yang cepat membuka pengetikan. */
    private fun selectedTextTap(px: Float, py: Float) {
        // Tabel terpilih diketuk lagi: ketukan ganda membuka selnya untuk diketik.
        val tbl = selItems.singleOrNull() as? TableItem
        if (tbl != null && selTexts.isEmpty() && selStrokes.isEmpty()) {
            if (tableCellAt(px, py)?.first?.id == tbl.id) tableTap(px, py, null) else cellTapId = 0L
            selTapText = null
            return
        }
        val t = selTexts.singleOrNull()
        if (t == null || selItems.isNotEmpty() || selStrokes.isNotEmpty()) { selTapText = null; return }
        InkRenderer.textBounds(t, box)
        box.inset(-8f / scale, -8f / scale)
        if (!box.contains(px, py)) { selTapText = null; return }
        val now = SystemClock.uptimeMillis()
        if (selTapText === t && now - selTapTime < 450L && hypot(px - selTapX, py - selTapY) * scale < 40f * density) {
            selTapText = null
            onTextTapped?.invoke(t, textOffsetAt(t, px, py))
            return
        }
        selTapText = t
        selTapTime = now
        selTapX = px
        selTapY = py
    }

    /** Jaga kursor tetap terlihat (misalnya di atas keyboard). */
    private fun bringTypingIntoView(refresh: Boolean = true) {
        val tt = typeTarget ?: return
        if (width == 0 || height == 0) return
        val lay = tt.layout()
        tt.toPage(typeMatrix)
        val off = selB.coerceIn(0, tt.text.length)
        val line = lay.getLineForOffset(off)
        val x = lay.getPrimaryHorizontal(off)
        val r = RectF(x - 2f, lay.getLineTop(line).toFloat(), x + 2f, lay.getLineBottom(line).toFloat())
        typeMatrix.mapRect(r)
        viewMatrix.mapRect(r)
        val top = margin + 64f * density
        val bottom = height - margin
        var dx = 0f
        var dy = 0f
        if (r.bottom > bottom) dy = bottom - r.bottom else if (r.top < top) dy = top - r.top
        if (r.right > width - margin) dx = width - margin - r.right else if (r.left < margin) dx = margin - r.left
        if (abs(dx) < 0.5f && abs(dy) < 0.5f) return
        tx += dx
        ty += dy
        clamp()
        updateMatrix()
        if (!refresh || settling) invalidate() else viewMoved()
    }

    /** Posisi atau skala halaman berubah di luar gerakan jari: gambar ulang (bila bisa di latar). */
    private fun viewMoved() {
        if (requestViewRender()) invalidate() else invalidateViewCache()
    }

    private fun drawTyping(canvas: Canvas) {
        val tt = typeTarget ?: return
        val lay = tt.layout()
        tt.toPage(typeMatrix)
        tt.box(typeBox)
        val k = (typeMatrix.mapRadius(1f) * scale).coerceAtLeast(0.01f)
        canvas.save()
        canvas.concat(viewMatrix)
        canvas.concat(typeMatrix)
        if (tt.showFrame) {
            val pad = 6f * density / k
            val rr = 8f * density / k
            anchorRect.set(typeBox)
            anchorRect.inset(-pad, -pad * 0.7f)
            typePaint.pathEffect = null
            typePaint.style = Paint.Style.FILL
            typePaint.color = InkRenderer.withAlpha(accentColor, 0.07f)
            canvas.drawRoundRect(anchorRect, rr, rr, typePaint)
            typePaint.style = Paint.Style.STROKE
            typePaint.strokeWidth = 1.5f * density / k
            typePaint.color = accentColor
            canvas.drawRoundRect(anchorRect, rr, rr, typePaint)
        }
        val n = tt.text.length
        val a = min(selA, selB).coerceIn(0, n)
        val b = max(selA, selB).coerceIn(0, n)
        if (a < b) {
            typePath.rewind()
            lay.getSelectionPath(a, b, typePath)
            typePaint.style = Paint.Style.FILL
            typePaint.color = InkRenderer.withAlpha(accentColor, 0.30f)
            canvas.drawPath(typePath, typePaint)
        }
        if (compA in 0 until compB && compB <= n) {
            // Garis bawah kata yang sedang disusun keyboard.
            typePaint.style = Paint.Style.STROKE
            typePaint.strokeWidth = 1.5f * density / k
            typePaint.color = InkRenderer.withAlpha(tt.caretColor, 0.8f)
            val l0 = lay.getLineForOffset(compA)
            val l1 = lay.getLineForOffset(compB)
            for (line in l0..l1) {
                val ls = lay.getLineStart(line)
                val le = lay.getLineEnd(line)
                val s0 = max(compA, ls)
                val e0 = min(compB, le)
                if (e0 <= s0) continue
                val x0 = lay.getPrimaryHorizontal(s0)
                val x1 = if (e0 >= le && line < lay.lineCount - 1) lay.getLineRight(line) else lay.getPrimaryHorizontal(e0)
                val y = lay.getLineBaseline(line) + 2.5f * density / k
                canvas.drawLine(min(x0, x1), y, max(x0, x1), y, typePaint)
            }
        }
        if (a == b && caretOn) {
            val line = lay.getLineForOffset(a)
            val x = lay.getPrimaryHorizontal(a)
            val base = lay.getLineBaseline(line).toFloat()
            val top = max(lay.getLineTop(line).toFloat(), base + lay.paint.ascent() * (if (tt.onRule) 0.92f else 1f))
            val bottom = base + lay.paint.descent() * (if (tt.onRule) 0.2f else 1f)
            typePaint.style = Paint.Style.STROKE
            typePaint.strokeCap = Paint.Cap.ROUND
            typePaint.strokeWidth = 2f * density / k
            typePaint.color = tt.caretColor
            canvas.drawLine(x, top, x, bottom, typePaint)
        }
        canvas.restore()
        reportAnchor(typeBox, typeMatrix)
    }

    /** Beri tahu letak bilah alat melayang bila berubah ([box] dalam koordinat [m], lalu halaman). */
    private fun reportAnchor(box: RectF?, m: Matrix?) {
        if (box == null || box.isEmpty) {
            if (anchorKnown) {
                anchorKnown = false
                onFloatAnchor?.invoke(null)
            }
            return
        }
        anchorRect.set(box)
        m?.mapRect(anchorRect)
        viewMatrix.mapRect(anchorRect)
        if (anchorKnown && abs(anchorRect.left - anchorLast.left) < 0.5f && abs(anchorRect.top - anchorLast.top) < 0.5f &&
            abs(anchorRect.right - anchorLast.right) < 0.5f && abs(anchorRect.bottom - anchorLast.bottom) < 0.5f
        ) return
        anchorLast.set(anchorRect)
        anchorKnown = true
        onFloatAnchor?.invoke(RectF(anchorRect))
    }

    // ---- isi stiker diketik langsung ----

    private var stickyId = 0L
    private var stickyBefore: StickyItem? = null

    private fun stickyNow(): StickyItem? = if (stickyId == 0L) null else items.firstOrNull { it.id == stickyId } as? StickyItem

    /** Mulai mengetik isi stiker langsung di atasnya. */
    fun beginStickyEdit(item: StickyItem): Boolean {
        finishTextAnim()
        typeTarget?.end(commit = true)
        val cur = items.firstOrNull { it.id == item.id } as? StickyItem ?: return false
        cancelInteraction()
        clearSelection()
        stickyId = cur.id
        stickyBefore = cur
        startTyping(StickyTarget())
        bringTypingIntoView()
        return true
    }

    fun typingSticky(): StickyItem? = stickyNow()

    private fun setStickyLive(next: StickyItem) {
        val cur = stickyNow() ?: return
        val idx = items.indexOf(cur)
        if (idx < 0) return
        items[idx] = next
        val r = RectF()
        val b = RectF()
        objBounds(cur, r)
        objBounds(next, b)
        r.union(b)
        refreshRegion(r)
    }

    /** Ganti huruf, ukuran, atau perataan isi stiker yang sedang diketik. */
    fun styleStickyEdit(font: String? = null, size: Float? = null, align: Align? = null) {
        val c = stickyNow() ?: return
        setStickyLive(c.withText(c.text, font ?: c.font, size ?: c.textSize, align ?: c.align))
        bringTypingIntoView()
    }

    private fun endStickyEdit(commit: Boolean) {
        val before = stickyBefore
        val cur = stickyNow()
        stickyId = 0L
        stickyBefore = null
        if (typeTarget is StickyTarget) stopTyping()
        if (before == null || cur == null) return
        val idx = items.indexOf(cur)
        if (idx < 0) return
        val r = RectF()
        val b = RectF()
        objBounds(cur, r)
        objBounds(before, b)
        r.union(b)
        if (!commit) {
            items[idx] = before
            refreshRegion(r)
            return
        }
        val fin = if (cur.text != cur.text.trim()) cur.withText(cur.text.trim(), cur.font, cur.textSize, cur.align) else cur
        if (fin.text == before.text && fin.font == before.font && fin.textSize == before.textSize && fin.align == before.align) {
            items[idx] = before
            refreshRegion(r)
            return
        }
        items[idx] = fin
        pushUndo(Change(removedItems = listOf(idx to before), addedItems = listOf(fin), addedItemsAt = listOf(idx)))
        pendingChange = null
        refreshRegion(r)
        onStrokesChanged?.invoke()
    }

    // ---- isi sel tabel diketik langsung ----

    private var cellTableId = 0L
    private var cellR = 0
    private var cellC = 0
    private var cellBefore: TableItem? = null

    private fun cellTable(): TableItem? = if (cellTableId == 0L) null else items.firstOrNull { it.id == cellTableId } as? TableItem

    /** Mulai mengetik isi sel [r],[c] tabel langsung di dalam selnya. */
    fun beginCellEdit(item: TableItem, r: Int, c: Int): Boolean {
        finishTextAnim()
        val sameTable = cellTableId == item.id
        typeTarget?.end(commit = true)
        val cur = items.firstOrNull { it.id == item.id } as? TableItem ?: return false
        if (r !in 0 until cur.rows || c !in 0 until cur.cols) return false
        if (!sameTable) cancelInteraction()
        clearSelection()
        cellTableId = cur.id
        cellR = r
        cellC = c
        cellBefore = cur
        startTyping(CellTarget())
        bringTypingIntoView()
        return true
    }

    /** Tabel, baris, dan kolom sel yang sedang diketik. */
    fun typingCell(): Triple<TableItem, Int, Int>? = cellTable()?.let { Triple(it, cellR, cellC) }

    private fun setTableLive(next: TableItem) {
        val cur = cellTable() ?: return
        val idx = items.indexOf(cur)
        if (idx < 0) return
        items[idx] = next
        val r = RectF()
        val b = RectF()
        objBounds(cur, r)
        objBounds(next, b)
        r.union(b)
        refreshRegion(r)
    }

    /** Ganti huruf, ukuran, atau perataan teks tabel yang selnya sedang diketik (berlaku untuk semua sel). */
    fun styleCellEdit(font: String? = null, size: Float? = null, align: Align? = null) {
        val t = cellTable() ?: return
        setTableLive(InkRenderer.fitTable(t.withTextStyle(font ?: t.font, size ?: t.textSize, align ?: t.align)))
        bringTypingIntoView()
    }

    /** Tambah baris kosong di bawah baris [after] tabel ini (bisa diurungkan). */
    fun addTableRow(tableId: Long, after: Int): TableItem? {
        val t = items.firstOrNull { it.id == tableId } as? TableItem ?: return null
        val next = t.withRowAdded(after)
        return if (replaceItem(t, next)) next else null
    }

    private fun endCellEdit(commit: Boolean) {
        val before = cellBefore
        val cur = cellTable()
        cellTableId = 0L
        cellBefore = null
        if (typeTarget is CellTarget) stopTyping()
        if (before == null || cur == null) return
        val idx = items.indexOf(cur)
        if (idx < 0) return
        val r = RectF()
        val b = RectF()
        objBounds(cur, r)
        objBounds(before, b)
        r.union(b)
        if (!commit) {
            items[idx] = before
            refreshRegion(r)
            return
        }
        // Baris kosong yang tertinggal di akhir sel dibuang.
        val text = cur.cell(cellR, cellC)
        val fin = if (text != text.trimEnd()) cur.withCell(cellR, cellC, text.trimEnd()) else cur
        if (fin.cells == before.cells && fin.textSize == before.textSize && fin.font == before.font &&
            fin.align == before.align && fin.h == before.h && fin.rows == before.rows &&
            fin.colFrac.contentEquals(before.colFrac) && fin.rowFrac.contentEquals(before.rowFrac)
        ) {
            items[idx] = before
            refreshRegion(r)
            return
        }
        items[idx] = fin
        pushUndo(Change(removedItems = listOf(idx to before), addedItems = listOf(fin), addedItemsAt = listOf(idx)))
        pendingChange = null
        refreshRegion(r)
        onStrokesChanged?.invoke()
    }

    // ---- garis dalam tabel digeser ----

    /** Tabel saat garisnya mulai digeser (untuk dibatalkan atau dicatat di riwayat). */
    private var lineTable: TableItem? = null
    private var lineIsCol = false
    private var lineIndex = 0
    /** Selisih titik sentuh ke garis, supaya garis tidak melompat ke ujung jari. */
    private var lineOffset = 0f
    private var lineMoved = false
    private var lineDownX = 0f
    private var lineDownY = 0f

    /** Garis dalam tabel di titik halaman ini: (garis kolom?, nomor batas), atau null. */
    private fun tableLineAt(t: TableItem, px: Float, py: Float): Pair<Boolean, Int>? {
        val slop = max(3f, 8f * density / scale)
        // Sedikit lebih luas di kiri dan atas, tempat pegangan garis baris dan kolom.
        if (px < t.x - slop * 3f || px > t.x + t.w + slop || py < t.y - slop * 3f || py > t.y + t.h + slop) return null
        var best: Pair<Boolean, Int>? = null
        var bestD = slop
        for (k in 1 until t.cols) {
            val d = abs(px - (t.x + t.colEdge(k)))
            // Pegangan kecil di atas garis kolom juga bisa dipegang.
            val onHandle = py < t.y && d < slop * 1.5f
            if (d < bestD || onHandle) { bestD = min(d, bestD); best = true to k }
        }
        if (py >= t.y - slop) for (k in 1 until t.rows) {
            val d = abs(py - (t.y + t.rowEdge(k)))
            if (d < bestD) { bestD = d; best = false to k }
        }
        return best
    }

    /** Tinggi terkecil baris [r] supaya teks di dalamnya tetap muat. */
    private fun rowNeed(t: TableItem, r: Int): Float {
        var need = 10f
        val pad = InkRenderer.cellPad(t)
        for (c in 0 until t.cols) if (t.cell(r, c).isNotEmpty()) need = max(need, InkRenderer.cellLayout(t, r, c).height + pad * 2)
        return need
    }

    private fun tableLineMove(vx: Float, vy: Float) {
        val t0 = lineTable ?: return
        if (!lineMoved && hypot(vx - downX, vy - downY) < touchSlop) return
        lineMoved = true
        val pp = toPage(vx, vy)
        val k = lineIndex
        val next = if (lineIsCol) {
            // Dua kolom di kiri-kanan garis bertukar lebar; lebar tabel tetap.
            val left = t0.colEdge(k - 1)
            val right = t0.colEdge(k + 1)
            val minW = max(12f, t0.textSize * 1.4f).coerceAtMost((right - left) / 2f)
            val at = (pp.x - lineOffset - t0.x).coerceIn(left + minW, right - minW)
            val widths = FloatArray(t0.cols) { t0.colEdge(it + 1) - t0.colEdge(it) }
            widths[k - 1] = at - left
            widths[k] = right - at
            // Teks yang jadi lebih sempit membuat barisnya lebih tinggi bila perlu.
            InkRenderer.fitTable(t0.withColumnWidths(widths))
        } else {
            // Dua baris di atas-bawah garis bertukar tinggi; tinggi tabel tetap, teks tetap muat.
            val top = t0.rowEdge(k - 1)
            val bottom = t0.rowEdge(k + 1)
            val minA = rowNeed(t0, k - 1)
            val minB = rowNeed(t0, k)
            if (bottom - top < minA + minB) return
            val at = (pp.y - lineOffset - t0.y).coerceIn(top + minA, bottom - minB)
            val heights = FloatArray(t0.rows) { t0.rowEdge(it + 1) - t0.rowEdge(it) }
            heights[k - 1] = at - top
            heights[k] = bottom - at
            t0.withRowHeights(heights)
        }
        setLineLive(next)
    }

    private fun setLineLive(next: TableItem) {
        val cur = items.firstOrNull { it.id == next.id } as? TableItem ?: return
        val idx = items.indexOf(cur)
        if (idx < 0) return
        val r = RectF()
        val b = RectF()
        objBounds(cur, r)
        items[idx] = next
        objBounds(next, b)
        r.union(b)
        if (selItems.any { it.id == next.id }) {
            selItems = listOf(next)
            updateSelRect()
        }
        refreshRegion(r)
    }

    private fun tableLineUp(commit: Boolean) {
        val t0 = lineTable ?: return
        lineTable = null
        val cur = items.firstOrNull { it.id == t0.id } as? TableItem
        if (cur == null) { invalidate(); return }
        val idx = items.indexOf(cur)
        val typing = cellTableId == t0.id
        if (!lineMoved || cur === t0) {
            invalidate()
            // Hanya diketuk: berlaku seperti ketukan biasa (pindah sel saat mengetik; ketuk dua kali untuk mengetik).
            if (commit && !lineMoved) {
                if (typing) tapOutsideEditing(downX, downY) else selectedTextTap(lineDownX, lineDownY)
            }
            return
        }
        if (!commit) {
            setLineLive(t0)
            return
        }
        if (typing) {
            // Bagian dari sesi mengetik sel: dicatat sekali di riwayat saat mengetik selesai.
            bringTypingIntoView()
            invalidate()
            return
        }
        pushUndo(Change(removedItems = listOf(idx to t0), addedItems = listOf(cur), addedItemsAt = listOf(idx)))
        contentChanged()
        onSelection?.invoke(selectionInfo())
        invalidate()
    }

    /** Sel tabel di titik halaman ini (tabel paling depan), atau null. */
    private fun tableCellAt(px: Float, py: Float): Triple<TableItem, Int, Int>? {
        val t = items.filterIsInstance<TableItem>().filter { px >= it.x && px <= it.x + it.w && py >= it.y && py <= it.y + it.h }
            .maxByOrNull { it.z } ?: return null
        var c = 0
        while (c < t.cols - 1 && px > t.x + t.colEdge(c + 1)) c++
        var r = 0
        while (r < t.rows - 1 && py > t.y + t.rowEdge(r + 1)) r++
        return Triple(t, r, c)
    }

    /** Tabel diketuk dua kali dengan cepat di tempat yang sama: selnya dibuka untuk diketik. */
    var onCellTapped: ((TableItem, Int, Int) -> Unit)? = null
    private var cellTapId = 0L
    private var cellTapTime = 0L
    private var cellTapX = 0f
    private var cellTapY = 0f
    /** Titik dari ketukan pena pertama di tabel; dibuang bila ternyata ketukan ganda. */
    private var cellTapDot: Stroke? = null
    private var tapDotInTable = false

    /** Ketukan ini ketukan kedua di tabel yang sama? (koordinat halaman) */
    private fun isSecondTableTap(px: Float, py: Float): Boolean {
        val hit = tableCellAt(px, py) ?: return false
        return cellTapId == hit.first.id && SystemClock.uptimeMillis() - cellTapTime < 450L &&
            hypot(px - cellTapX, py - cellTapY) * scale < 40f * density
    }

    /**
     * Ketukan di tabel (koordinat halaman). Ketukan kedua yang cepat di tempat yang sama membuka sel itu untuk
     * diketik; ketukan pertama hanya dicatat ([dot]: titik pena yang tertinggal olehnya). Hasil: sel dibuka.
     */
    private fun tableTap(px: Float, py: Float, dot: Stroke?): Boolean {
        val hit = tableCellAt(px, py)
        if (hit == null || onCellTapped == null) { cellTapId = 0L; cellTapDot = null; return false }
        if (isSecondTableTap(px, py)) {
            cellTapId = 0L
            cellTapDot?.let { dropTapDot(it) }
            cellTapDot = null
            onCellTapped?.invoke(hit.first, hit.second, hit.third)
            return true
        }
        cellTapId = hit.first.id
        cellTapTime = SystemClock.uptimeMillis()
        cellTapX = px
        cellTapY = py
        cellTapDot = dot
        return false
    }

    /** Buang titik pena dari ketukan pertama, bila masih langkah terakhir di riwayat. */
    private fun dropTapDot(s: Stroke) {
        val top = undoStack.lastOrNull() ?: return
        if (top.addedStrokes.size != 1 || top.addedStrokes[0] !== s || top.removedStrokes.isNotEmpty() ||
            top.addedTexts.isNotEmpty() || top.removedTexts.isNotEmpty() || top.addedItems.isNotEmpty() || top.removedItems.isNotEmpty()
        ) return
        if (!strokes.remove(s)) return
        undoStack.removeAt(undoStack.lastIndex)
        notifyHistory()
        val r = RectF()
        objBounds(s, r)
        refreshRegion(r)
        onStrokesChanged?.invoke()
    }

    // ================= mind map disunting langsung di halaman =================

    /** Keadaan sesi mind map untuk bilah alatnya: ide terpilih (-1: tidak ada). */
    class MapState(val selected: Int, val isRoot: Boolean, val color: Int, val typing: Boolean, val tidy: Boolean)

    private var mapId = 0L
    private var mapSel = -1
    private var mapTyping = -1
    private var mapTypeBefore: MindMapItem? = null
    private var mapDragBefore: MindMapItem? = null
    private var mapDownNode = -1
    private var mapDownMapX = 0f
    private var mapDownMapY = 0f
    private var mapDragging = false
    private val mapPt = FloatArray(2)
    private val mapM = Matrix()
    private val mapRect = RectF()

    var onMapState: ((MapState?) -> Unit)? = null
    /** Ide terpilih diketuk lagi: mulai mengetik di dalamnya (nomor ide, titik layar yang diketuk). */
    var onMapTypeRequest: ((Int, Float, Float) -> Unit)? = null
    /** Mind map diketuk saat menulis: benda dan ide yang diketuk (-1: bagian kosong). */
    var onMindMapTapped: ((MindMapItem, Int) -> Unit)? = null

    val inMapSession: Boolean get() = mapId != 0L

    private fun mapItem(): MindMapItem? = if (mapId == 0L) null else items.firstOrNull { it.id == mapId } as? MindMapItem

    private fun mapScale(m: MindMapItem) = if (m.mapW > 0f && m.mapH > 0f) min(m.w / m.mapW, m.h / m.mapH) else 1f

    /** Dari satuan peta ke halaman. */
    private fun mapMatrix(m: MindMapItem, out: Matrix) {
        val k = mapScale(m)
        out.setTranslate(-m.mapL, -m.mapT)
        out.postScale(k, k)
        out.postTranslate(m.x, m.y)
    }

    private fun pageToMap(m: MindMapItem, px: Float, py: Float) {
        val k = mapScale(m)
        mapPt[0] = (px - m.x) / k + m.mapL
        mapPt[1] = (py - m.y) / k + m.mapT
    }

    /** Mind map dan ide di titik halaman ini (-1: bagian kosong di dalam kotaknya), atau null. */
    private fun mindMapNodeAt(px: Float, py: Float): Pair<MindMapItem, Int>? {
        for (it in items.asReversed()) {
            if (it !is MindMapItem) continue
            if (px < it.x || px > it.x + it.w || py < it.y || py > it.y + it.h) continue
            pageToMap(it, px, py)
            val slop = 8f * density / (scale * mapScale(it))
            return it to MindMapRenderer.hit(it.nodes, mapPt[0], mapPt[1], slop)
        }
        return null
    }

    /** Mulai menyunting mind map langsung di halaman, dengan ide [select] terpilih (-1: belum ada). */
    fun beginMapSession(item: MindMapItem, select: Int) {
        finishTextAnim()
        typeTarget?.end(commit = true)
        val cur = items.firstOrNull { it.id == item.id } as? MindMapItem ?: return
        cancelInteraction()
        clearSelection()
        mapId = cur.id
        mapSel = if (cur.nodes.any { it.id == select }) select else -1
        mapTyping = -1
        notifyMap()
        invalidate()
    }

    fun endMapSession() {
        if (mapId == 0L) return
        if (mapTyping >= 0) endNodeTyping(commit = true)
        if (mapDragging) mapNodeUp(commit = true)
        mapId = 0L
        mapSel = -1
        mapTyping = -1
        notifyMap()
        invalidate()
    }

    private fun notifyMap() {
        val m = mapItem()
        if (m == null) {
            onMapState?.invoke(null)
            return
        }
        val n = m.nodes.firstOrNull { it.id == mapSel }
        onMapState?.invoke(MapState(mapSel, n != null && n.parent < 0, n?.color ?: 0, mapTyping >= 0, m.tidy))
    }

    /** Setelah urungkan/ulangi: sesuaikan sesi dengan mind map yang sekarang ada. */
    private fun resyncMap() {
        if (mapId == 0L) return
        val m = mapItem()
        if (m == null) {
            mapTyping = -1
            if (typeTarget is NodeTarget) stopTyping()
            endMapSession()
            return
        }
        if (m.nodes.none { it.id == mapSel }) mapSel = -1
        notifyMap()
        invalidate()
    }

    /** Mind map dengan susunan [nodes]: skalanya sama dan ide utamanya tetap di tempat yang sama di halaman. */
    private fun mapLike(old: MindMapItem, nodes: List<MindNode>, tidy: Boolean): MindMapItem {
        val k = mapScale(old)
        val probe = MindMapRenderer.toItem(nodes, 0f, 0f, k, tidy)
        var x = old.x
        var y = old.y
        val oldRoot = MindMaps.root(old.nodes)
        val newRoot = MindMaps.root(nodes)
        if (oldRoot != null && newRoot != null) {
            val rx = old.x + (oldRoot.x - old.mapL) * k
            val ry = old.y + (oldRoot.y - old.mapT) * k
            x = rx - (newRoot.x - probe.mapL) * k
            y = ry - (newRoot.y - probe.mapT) * k
        }
        return MindMapRenderer.toItem(nodes, x, y, k, tidy)
    }

    /** Pasang versi baru mind map yang sedang disunting (tampil langsung, belum masuk riwayat). */
    private fun mapSetLive(next: MindMapItem): MindMapItem? {
        val old = mapItem() ?: return null
        val idx = items.indexOf(old)
        if (idx < 0) return null
        next.id = old.id
        next.group = old.group
        next.z = old.z
        items[idx] = next
        val r = RectF()
        val b = RectF()
        objBounds(old, r)
        objBounds(next, b)
        r.union(b)
        refreshRegion(r)
        return next
    }

    /** Catat satu langkah urungkan dari [before] ke mind map yang sekarang. */
    private fun mapRecord(before: MindMapItem) {
        val now = mapItem() ?: return
        if (now === before) return
        val idx = items.indexOf(now)
        if (idx < 0) return
        pushUndo(Change(removedItems = listOf(idx to before), addedItems = listOf(now), addedItemsAt = listOf(idx)))
        pendingChange = null
        val r = RectF()
        val b = RectF()
        objBounds(before, r)
        objBounds(now, b)
        r.union(b)
        refreshRegion(r)
        onStrokesChanged?.invoke()
    }

    /** Ubah susunan ide sebagai satu langkah urungkan. */
    private fun mapApply(nodes: List<MindNode>, tidy: Boolean) {
        val before = mapItem() ?: return
        val arranged = if (tidy) MindMaps.arrange(nodes, MindMapRenderer.sizeOf) else nodes
        mapSetLive(mapLike(before, arranged, tidy)) ?: return
        mapRecord(before)
    }

    fun mapSelectNode(id: Int) {
        mapSel = id
        notifyMap()
        invalidate()
    }

    /** Tambah cabang di bawah ide terpilih ([sibling]: sejajar dengannya). Hasil: nomor ide baru, atau -1. */
    fun mapAdd(sibling: Boolean): Int {
        val m = mapItem() ?: return -1
        val sel = m.nodes.firstOrNull { it.id == mapSel } ?: MindMaps.root(m.nodes) ?: return -1
        val parentId = if (sibling && sel.parent >= 0) sel.parent else sel.id
        val (next, id) = MindMaps.addChild(m.nodes, parentId, MindMapRenderer.sizeOf)
        if (id < 0) return -1
        mapApply(next, m.tidy)
        mapSel = id
        notifyMap()
        return id
    }

    /** Hapus ide terpilih beserta cabangnya; ide utama: hapus seluruh mind map. */
    fun mapDelete() {
        val m = mapItem() ?: return
        val sel = m.nodes.firstOrNull { it.id == mapSel } ?: return
        if (sel.parent < 0) {
            val idx = items.indexOf(m)
            if (idx < 0) return
            endMapSession()
            items.removeAt(idx)
            pushUndo(Change(removedItems = listOf(idx to m)))
            contentChanged()
            return
        }
        mapApply(MindMaps.remove(m.nodes, sel.id), m.tidy)
        mapSel = sel.parent
        notifyMap()
    }

    fun mapRecolor(color: Int) {
        val m = mapItem() ?: return
        val sel = m.nodes.firstOrNull { it.id == mapSel } ?: return
        mapApply(MindMaps.recolor(m.nodes, sel.id, color), m.tidy)
        notifyMap()
    }

    /** Rapikan susunan (kembali diatur otomatis). */
    fun mapTidy() {
        val m = mapItem() ?: return
        mapApply(m.nodes, tidy = true)
        notifyMap()
    }

    /** Mulai mengetik di ide [id]. Hasil: isi teksnya dan posisi kursor (di titik layar [vx],[vy] bila ada). */
    fun beginNodeTyping(id: Int, vx: Float = Float.NaN, vy: Float = Float.NaN): Pair<String, Int>? {
        val m = mapItem() ?: return null
        val n = m.nodes.firstOrNull { it.id == id } ?: return null
        if (mapTyping >= 0 && mapTyping != id) endNodeTyping(commit = true)
        if (typeTarget != null && typeTarget !is NodeTarget) typeTarget?.end(commit = true)
        mapSel = id
        mapTyping = id
        mapTypeBefore = mapItem()
        startTyping(NodeTarget())
        val off = if (!vx.isNaN() && insideType(vx, vy)) typeOffsetAt(vx, vy) else n.text.length
        selA = off
        selB = off
        notifyMap()
        bringTypingIntoView()
        return n.text to off
    }

    private fun setNodeText(value: String) {
        val m = mapItem() ?: return
        val nodes = m.nodes.map { if (it.id == mapTyping) it.withText(value) else it }
        val arranged = if (m.tidy) MindMaps.arrange(nodes, MindMapRenderer.sizeOf) else nodes
        mapSetLive(mapLike(m, arranged, m.tidy))
    }

    private fun endNodeTyping(commit: Boolean) {
        if (mapTyping < 0) return
        val before = mapTypeBefore
        val id = mapTyping
        mapTyping = -1
        mapTypeBefore = null
        if (typeTarget is NodeTarget) stopTyping()
        val m = mapItem()
        if (m != null && before != null) {
            if (!commit) {
                mapSetLive(before)
            } else {
                val n = m.nodes.firstOrNull { it.id == id }
                val trimmed = n?.text?.trim()
                if (n != null && trimmed != null && trimmed != n.text) {
                    val nodes = m.nodes.map { if (it.id == id) it.withText(trimmed) else it }
                    val arranged = if (m.tidy) MindMaps.arrange(nodes, MindMapRenderer.sizeOf) else nodes
                    mapSetLive(mapLike(m, arranged, m.tidy))
                }
                val was = before.nodes.firstOrNull { it.id == id }?.text
                if (was == trimmed) mapSetLive(before) else mapRecord(before)
            }
        }
        notifyMap()
    }

    private fun mapDown(e: MotionEvent, i: Int, stylus: Boolean) {
        val m = mapItem()
        if (m == null) {
            endMapSession()
            mode = Mode.IGNORE
            return
        }
        val x = e.getX(i)
        val y = e.getY(i)
        activePointer = e.getPointerId(i)
        activeIsFinger = !stylus
        downX = x
        downY = y
        typeMoved = false
        // Sedang mengetik di sebuah ide: sentuhan di teksnya memindahkan kursor.
        if (typeTarget != null && insideType(x, y)) {
            typeDownInside(e, i)
            return
        }
        val pp = toPage(x, y)
        val px = pp.x
        val py = pp.y
        pageToMap(m, px, py)
        val slop = 10f * density / (scale * mapScale(m))
        val hit = MindMapRenderer.hit(m.nodes, mapPt[0], mapPt[1], slop)
        if (hit >= 0) {
            mode = Mode.MAPNODE
            mapDownNode = hit
            mapDragging = false
            mapDownMapX = mapPt[0]
            mapDownMapY = mapPt[1]
            return
        }
        val inside = px >= m.x && px <= m.x + m.w && py >= m.y && py <= m.y + m.h
        if (!stylus) {
            startPan(e, i)
            tapOutside = !inside
            tapBlank = inside
            return
        }
        mode = if (inside) Mode.MAPBLANK else Mode.TYPEOUT
    }

    private fun mapMove(x: Float, y: Float) {
        if (!mapDragging) {
            if (hypot(x - downX, y - downY) < touchSlop) return
            // Mulai menggeser ide ini beserta cabangnya (ide utama: seluruh mind map).
            if (typeTarget != null) {
                onTypeOutside?.invoke()
                typeTarget?.end(commit = true)
            }
            val m = mapItem() ?: return
            mapDragging = true
            mapDragBefore = m
            if (mapSel != mapDownNode) {
                mapSel = mapDownNode
                notifyMap()
            }
        }
        val before = mapDragBefore ?: return
        val pp = toPage(x, y)
        pageToMap(before, pp.x, pp.y)
        val dx = mapPt[0] - mapDownMapX
        val dy = mapPt[1] - mapDownMapY
        val root = MindMaps.root(before.nodes)
        if (root != null && mapDownNode == root.id) {
            val k = mapScale(before)
            mapSetLive(before.placed(before.x + dx * k, before.y + dy * k, before.w, before.h) as MindMapItem)
        } else {
            mapSetLive(mapLike(before, MindMaps.moveSubtree(before.nodes, mapDownNode, dx, dy), tidy = false))
        }
    }

    private fun mapNodeUp(commit: Boolean) {
        if (mapDragging) {
            val before = mapDragBefore
            mapDragging = false
            mapDragBefore = null
            if (before != null) {
                if (commit) mapRecord(before) else mapSetLive(before)
            }
            notifyMap()
            return
        }
        if (!commit) return
        val id = mapDownNode
        if (id < 0 || id == mapTyping) return
        if (id == mapSel) {
            // Ketuk ide yang sudah terpilih: langsung mengetik di dalamnya.
            onMapTypeRequest?.invoke(id, downX, downY)
        } else {
            if (typeTarget != null) {
                onTypeOutside?.invoke()
                typeTarget?.end(commit = true)
            }
            mapSel = id
            notifyMap()
            invalidate()
        }
    }

    private fun mapBlankTap() {
        if (typeTarget != null) {
            onTypeOutside?.invoke()
            typeTarget?.end(commit = true)
        }
        if (mapSel != -1) {
            mapSel = -1
            notifyMap()
            invalidate()
        }
    }

    /** Bingkai mind map yang sedang disunting, tanda "…" pada ide kosong, dan ide terpilih. */
    private fun drawMapSession(canvas: Canvas) {
        val m = mapItem() ?: return
        mapMatrix(m, mapM)
        val k = (scale * mapScale(m)).coerceAtLeast(0.01f)
        canvas.save()
        canvas.concat(viewMatrix)
        mapRect.set(m.x, m.y, m.x + m.w, m.y + m.h)
        mapRect.inset(-4f * density / scale, -4f * density / scale)
        typePaint.style = Paint.Style.STROKE
        typePaint.strokeWidth = 1.2f * density / scale
        typePaint.color = InkRenderer.withAlpha(accentColor, 0.45f)
        typePaint.pathEffect = DashPathEffect(floatArrayOf(5f * density / scale, 4f * density / scale), 0f)
        canvas.drawRoundRect(mapRect, 8f * density / scale, 8f * density / scale, typePaint)
        canvas.concat(mapM)
        val depths = MindMaps.depths(m.nodes)
        for (n in m.nodes) {
            if (n.text.isBlank() && n.id != mapTyping) MindMapRenderer.drawPlaceholder(canvas, n, depths[n.id] ?: 0)
        }
        val sel = m.nodes.firstOrNull { it.id == mapSel }
        if (sel != null) {
            val sz = MindMapRenderer.size(sel, depths[sel.id] ?: 0)
            val pad = 5f * density / k
            mapRect.set(sel.x - sz[0] / 2f - pad, sel.y - sz[1] / 2f - pad, sel.x + sz[0] / 2f + pad, sel.y + sz[1] / 2f + pad)
            typePaint.strokeWidth = 2f * density / k
            typePaint.color = accentColor
            typePaint.pathEffect = DashPathEffect(floatArrayOf(6f * density / k, 4f * density / k), 0f)
            canvas.drawRoundRect(mapRect, 10f * density / k, 10f * density / k, typePaint)
        }
        typePaint.pathEffect = null
        canvas.restore()
        if (typeTarget == null) {
            if (sel != null) {
                val sz = MindMapRenderer.size(sel, depths[sel.id] ?: 0)
                mapRect.set(sel.x - sz[0] / 2f, sel.y - sz[1] / 2f, sel.x + sz[0] / 2f, sel.y + sz[1] / 2f)
                reportAnchor(mapRect, mapM)
            } else {
                mapRect.set(m.x, m.y, m.x + m.w, m.y + m.h)
                reportAnchor(mapRect, null)
            }
        }
    }

    // ================= penggaris =================

    private fun placeRuler() {
        rulerCx = width / 2f
        rulerCy = height * 0.42f
        rulerAngle = 0f
    }

    /** Jarak tegak lurus titik dari garis tengah penggaris (positif ke arah bawah penggaris). */
    private fun rulerOffset(x: Float, y: Float): Float {
        val dx = x - rulerCx
        val dy = y - rulerCy
        return -dx * sin(rulerAngle) + dy * cos(rulerAngle)
    }

    private fun insideRuler(x: Float, y: Float) = rulerVisible && abs(rulerOffset(x, y)) <= rulerThickness / 2f

    /** Bila titik dekat tepi penggaris, kembalikan tepi mana yang dipakai. */
    private fun nearRulerEdge(x: Float, y: Float): Float? {
        if (!rulerVisible) return null
        val v = rulerOffset(x, y)
        val half = rulerThickness / 2f
        if (abs(v) > half + rulerSnap) return null
        return if (v < 0f) -half else half
    }

    /** Proyeksikan titik ke tepi penggaris. */
    private fun snapToRuler(x: Float, y: Float, edge: Float, out: FloatArray) {
        val c = cos(rulerAngle)
        val s = sin(rulerAngle)
        val dx = x - rulerCx
        val dy = y - rulerCy
        val u = dx * c + dy * s
        out[0] = rulerCx + u * c - edge * s
        out[1] = rulerCy + u * s + edge * c
    }

    private fun drawRuler(canvas: Canvas) {
        val dark = InkRenderer.isDark(deskColor)
        val half = rulerThickness / 2f
        val len = hypot(width.toFloat(), height.toFloat())
        canvas.save()
        canvas.translate(rulerCx, rulerCy)
        canvas.rotate(Math.toDegrees(rulerAngle.toDouble()).toFloat())

        rulerPaint.style = Paint.Style.FILL
        rulerPaint.color = if (dark) 0xD82B2E36.toInt() else 0xDDF7F8FA.toInt()
        canvas.drawRect(-len, -half, len, half, rulerPaint)
        rulerPaint.style = Paint.Style.STROKE
        rulerPaint.strokeWidth = 1f * density
        rulerPaint.color = if (dark) 0x66FFFFFF else 0x55000000
        canvas.drawLine(-len, -half, len, -half, rulerPaint)
        canvas.drawLine(-len, half, len, half, rulerPaint)

        // Garis skala milimeter sesuai perbesaran halaman, angka tiap sentimeter.
        val mmPx = PaperSize.mmToPt(1f) * scale
        val step = if (mmPx >= 4f) 1 else 5
        val ink = if (dark) 0xCCFFFFFF.toInt() else 0xCC2A2D35.toInt()
        rulerPaint.color = ink
        rulerPaint.strokeWidth = 1f
        val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = ink
            textSize = 10f * density
            textAlign = Paint.Align.CENTER
        }
        val count = (len / mmPx).toInt()
        var mm = -count - (-count % step)
        while (mm <= count) {
            val x = mm * mmPx
            val tick = when {
                mm % 10 == 0 -> 16f * density
                mm % 5 == 0 -> 10f * density
                else -> 6f * density
            }
            canvas.drawLine(x, -half, x, -half + tick, rulerPaint)
            canvas.drawLine(x, half, x, half - tick, rulerPaint)
            if (mm % 10 == 0 && mmPx * 10 >= 24f * density) {
                canvas.drawText((abs(mm) / 10).toString(), x, -half + tick + 11f * density, textPaint)
            }
            mm += step
        }

        // Sudut penggaris di tengah.
        var deg = Math.toDegrees(rulerAngle.toDouble()).roundToInt() % 180
        if (deg < 0) deg += 180
        textPaint.textSize = 13f * density
        textPaint.isFakeBoldText = true
        canvas.drawText("$deg°", 0f, 6f * density, textPaint)
        canvas.restore()
    }

    // ================= sentuhan =================

    private fun isStylus(e: MotionEvent, i: Int): Boolean {
        val t = e.getToolType(i)
        return t == MotionEvent.TOOL_TYPE_STYLUS || t == MotionEvent.TOOL_TYPE_ERASER
    }

    private fun wantsErase(e: MotionEvent, i: Int): Boolean =
        (tool == Tool.ERASER && inputMode == InputMode.INK) ||
            e.getToolType(i) == MotionEvent.TOOL_TYPE_ERASER ||
            (e.buttonState and MotionEvent.BUTTON_STYLUS_PRIMARY) != 0

    override fun onHoverEvent(event: MotionEvent): Boolean {
        if (event.pointerCount > 0 && isStylus(event, 0)) {
            lastStylusHover = SystemClock.uptimeMillis()
            lastHoverEvent = lastStylusHover
            penHoverSupported = true
            penHovering = event.actionMasked != MotionEvent.ACTION_HOVER_EXIT
        }
        return super.onHoverEvent(event)
    }

    // ---- ketuk dua jari (urungkan) dan tiga jari (ulangi) ----

    /** Ketukan beberapa jari sekaligus: [n] 2 atau 3. */
    var onMultiTap: ((Int) -> Unit)? = null
    private var mtActive = false
    private var mtStart = 0L
    private var mtMax = 0
    private val mtDown = HashMap<Int, PointF>()

    /**
     * Ketukan dianggap sah bila semua jari mendarat hampir bersamaan, tidak bergeser, dan cepat diangkat; tanpa
     * pena dan tanpa telapak tangan. Geser, perbesar, atau menulis dengan jari membatalkannya.
     */
    private fun trackMultiTap(e: MotionEvent) {
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                mtDown.clear()
                mtActive = !isStylus(e, 0) && e.getTouchMajor(0) <= 60f * density &&
                    SystemClock.uptimeMillis() - lastStylusHover >= 400
                mtStart = e.eventTime
                mtMax = 1
                mtDown[e.getPointerId(0)] = PointF(e.getX(0), e.getY(0))
            }
            MotionEvent.ACTION_POINTER_DOWN -> {
                if (!mtActive) return
                val i = e.actionIndex
                if (isStylus(e, i) || e.getTouchMajor(i) > 60f * density || e.eventTime - mtStart > 400L) {
                    mtActive = false
                    return
                }
                mtDown[e.getPointerId(i)] = PointF(e.getX(i), e.getY(i))
                mtMax = max(mtMax, e.pointerCount)
            }
            MotionEvent.ACTION_MOVE -> {
                if (!mtActive) return
                val slop = touchSlop * 2.2f
                for (k in 0 until e.pointerCount) {
                    val d = mtDown[e.getPointerId(k)] ?: continue
                    if (hypot(e.getX(k) - d.x, e.getY(k) - d.y) > slop) { mtActive = false; return }
                }
            }
            MotionEvent.ACTION_UP -> {
                if (mtActive && e.eventTime - mtStart < 700L && mtMax in 2..3) {
                    val n = mtMax
                    // Setelah gerakan selesai dibereskan (geser/perbesar tanpa perubahan).
                    post { onMultiTap?.invoke(n) }
                }
                mtActive = false
            }
            MotionEvent.ACTION_CANCEL -> {
                // Banyak tablet (mis. Xiaomi/HyperOS) memakai sentuhan tiga jari untuk tangkapan layar: begitu jari
                // ketiga mendarat, sistem merebut sentuhannya dan aplikasi hanya menerima "batal". Bila saat itu
                // sudah ada dua jari atau lebih yang diam dan baru saja mendarat, anggap itu ketukan tiga jari.
                if (mtActive && mtMax >= 2 && SystemClock.uptimeMillis() - mtStart < 700L) {
                    post { onMultiTap?.invoke(3) }
                }
                mtActive = false
            }
        }
    }

    /** Tulisan singkat di atas kanvas, misalnya "Urungkan", lalu memudar. */
    private var flashText: String? = null
    private var flashAt = 0L
    private val flashBg = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xD8303036.toInt() }
    private val flashPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textSize = 15f * resources.displayMetrics.scaledDensity
        textAlign = Paint.Align.CENTER
    }

    fun flash(text: String) {
        flashText = text
        flashAt = SystemClock.uptimeMillis()
        invalidate()
    }

    private fun drawFlash(canvas: Canvas) {
        val t = flashText ?: return
        val age = SystemClock.uptimeMillis() - flashAt
        if (age > 900L) { flashText = null; return }
        val a = if (age < 600L) 1f else 1f - (age - 600L) / 300f
        val w = flashPaint.measureText(t) + 36f * density
        val h = 38f * density
        val cx = width / 2f
        val top = 18f * density
        flashBg.alpha = (0xD8 * a).toInt()
        flashPaint.alpha = (255 * a).toInt()
        canvas.drawRoundRect(cx - w / 2f, top, cx + w / 2f, top + h, h / 2f, h / 2f, flashBg)
        val fm = flashPaint.fontMetrics
        canvas.drawText(t, cx, top + h / 2f - (fm.ascent + fm.descent) / 2f, flashPaint)
        postInvalidateOnAnimation()
    }

    override fun onTouchEvent(e: MotionEvent): Boolean {
        if (page == null) return false
        recordForPrediction(e)
        trackMultiTap(e)
        if (e.actionMasked == MotionEvent.ACTION_DOWN && turnSnap != null && turnArmed) endPageTurn()
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> onFirstDown(e)
            MotionEvent.ACTION_POINTER_DOWN -> onExtraDown(e)
            MotionEvent.ACTION_MOVE -> onMove(e)
            MotionEvent.ACTION_POINTER_UP -> onPointerUp(e)
            MotionEvent.ACTION_UP -> finishGesture(e, commit = true)
            MotionEvent.ACTION_CANCEL -> finishGesture(e, commit = false)
        }
        return true
    }

    private fun onFirstDown(e: MotionEvent) {
        val stylus = isStylus(e, 0)
        if (stylus) onStylusSeen?.invoke()
        tapOutside = false
        tapBlank = false
        // Mind map atau teks sedang disunting langsung di halaman: sentuhan untuk kursor, pilihan, dan ide.
        if (mapId != 0L || typeTarget != null) {
            if (palmLike(e, 0)) { mode = Mode.IGNORE; return }
            if (mapId != 0L) mapDown(e, 0, stylus) else typeDown(e, 0, stylus)
            return
        }
        // Mode baca: pena dan jari hanya menggulir, memperbesar, dan membalik halaman; tidak ada yang berubah.
        if (readOnly) {
            if (palmLike(e, 0)) { mode = Mode.IGNORE; return }
            startPan(e, 0)
            panTapAny = true
            return
        }
        // Jari di atas penggaris: geser atau putar penggaris.
        if (!stylus && insideRuler(e.getX(0), e.getY(0))) {
            startRuler(e)
            return
        }
        if (inputMode == InputMode.TYPE) {
            // Mode teks: pena dan jari tidak menulis. Geser untuk menggulir, ketuk untuk menaruh kursor.
            if (palmLike(e, 0)) { mode = Mode.IGNORE; return }
            startPan(e, 0)
            panTapAny = true
            return
        }
        if (inputMode == InputMode.SELECT) {
            selectDown(e, 0, finger = !stylus)
            // Tahan di tempat kosong, di atas objek, atau di dalam pilihan (bukan pegangan ukuran/putar).
            if (mode == Mode.PAN || (mode == Mode.SELECT && (lasso != null || dragCorner < 0))) armLongPress(e, 0)
            return
        }
        if (stylus) {
            requestUnbufferedDispatch(e)
            begin(e, 0, wantsErase(e, 0), finger = false)
            return
        }
        // Jari menyentuh saat pena masih melayang: hampir pasti telapak tangan.
        if (SystemClock.uptimeMillis() - lastStylusHover < 400) {
            mode = Mode.IGNORE; return
        }
        if (fingerDraws) {
            requestUnbufferedDispatch(e)
            begin(e, 0, wantsErase(e, 0), finger = true)
        } else {
            if (e.getTouchMajor(0) > 60f * density) {
                mode = Mode.IGNORE; return
            }
            startPan(e, 0)
            armLongPress(e, 0)
        }
    }

    private fun onExtraDown(e: MotionEvent) {
        disarmLongPress()
        val i = e.actionIndex
        val stylus = isStylus(e, i)
        when (mode) {
            Mode.DRAW, Mode.ERASE, Mode.SHAPE, Mode.TAPE -> {
                if (activeIsFinger) {
                    cancelStroke()
                    if (stylus) {
                        onStylusSeen?.invoke()
                        begin(e, i, wantsErase(e, i), finger = false)
                    } else {
                        startZoom(e, skip = -1)
                    }
                }
                // Pena sedang menulis: sentuhan jari lain diabaikan.
            }
            Mode.RULER -> {
                if (stylus) {
                    // Satu jari menahan penggaris, pena menulis mengikuti tepinya.
                    onStylusSeen?.invoke()
                    requestUnbufferedDispatch(e)
                    begin(e, i, wantsErase(e, i), finger = false)
                } else if (e.pointerCount >= 2) {
                    startRulerRotate(e)
                }
            }
            Mode.SELECT, Mode.TURN -> Unit
            Mode.CHECKTAP -> if (!stylus) startZoom(e, skip = -1)
            Mode.TAPETAP -> if (!stylus) { tapeHit = null; startZoom(e, skip = -1) }
            Mode.TABLELINE -> Unit
            Mode.TYPESEL, Mode.TYPEOUT, Mode.MAPNODE, Mode.MAPBLANK -> {
                if (!stylus) {
                    // Jari kedua: batalkan ketukan/seretan, perbesar atau geser halaman.
                    if (mode == Mode.MAPNODE) mapNodeUp(commit = false)
                    startZoom(e, skip = -1)
                }
            }
            Mode.PAN, Mode.ZOOM, Mode.NONE, Mode.IGNORE -> {
                if (stylus) {
                    endPanZoom()
                    onStylusSeen?.invoke()
                    if (inputMode == InputMode.SELECT) {
                        selectDown(e, i, finger = false)
                    } else if (inputMode == InputMode.TYPE || readOnly) {
                        startPan(e, i)
                        panTapAny = true
                    } else {
                        requestUnbufferedDispatch(e)
                        begin(e, i, wantsErase(e, i), finger = false)
                    }
                } else if (mode != Mode.IGNORE) {
                    startZoom(e, skip = -1)
                }
            }
        }
    }

    private fun onMove(e: MotionEvent) {
        if (lpPointer >= 0) {
            val k = e.findPointerIndex(lpPointer)
            if (k < 0 || hypot(e.getX(k) - lpX, e.getY(k) - lpY) > touchSlop) disarmLongPress()
        }
        when (mode) {
            Mode.DRAW -> {
                val i = e.findPointerIndex(activePointer)
                if (i < 0) return
                for (h in 0 until e.historySize) addPoint(e.getHistoricalX(i, h), e.getHistoricalY(i, h), e.getHistoricalPressure(i, h), false, e.getHistoricalEventTime(h))
                addPoint(e.getX(i), e.getY(i), e.getPressure(i), false, e.eventTime)
                lastInkMove = SystemClock.uptimeMillis()
                invalidate()
            }
            Mode.ERASE -> {
                val i = e.findPointerIndex(activePointer)
                if (i < 0) return
                for (h in 0 until e.historySize) eraseAt(e.getHistoricalX(i, h), e.getHistoricalY(i, h))
                eraseAt(e.getX(i), e.getY(i))
                // Daerah yang berubah digambar ulang sekali per bingkai layar (di onDraw), bukan tiap titik.
                invalidate()
            }
            Mode.SHAPE -> {
                val i = e.findPointerIndex(activePointer)
                if (i < 0) return
                val pp = toPage(e.getX(i), e.getY(i))
                shapeX1 = pp.x; shapeY1 = pp.y
                invalidate()
            }
            Mode.TAPE -> {
                val i = e.findPointerIndex(activePointer)
                if (i < 0) return
                for (h in 0 until e.historySize) tapeMove(e.getHistoricalX(i, h), e.getHistoricalY(i, h))
                tapeMove(e.getX(i), e.getY(i))
                invalidate()
            }
            Mode.TAPETAP -> {
                val i = e.findPointerIndex(activePointer)
                if (i < 0) return
                if (hypot(e.getX(i) - downX, e.getY(i) - downY) > touchSlop) inkFromTapeTap(e, i)
            }
            Mode.SELECT -> {
                val i = e.findPointerIndex(activePointer)
                if (i < 0) return
                selectMove(e.getX(i), e.getY(i))
            }
            Mode.RULER -> rulerMove(e)
            Mode.TURN -> {
                val i = e.findPointerIndex(activePointer)
                if (i < 0) return
                updateTurn(e.getX(i), e.getY(i), e.eventTime)
            }
            Mode.TYPESEL -> {
                val i = e.findPointerIndex(activePointer)
                if (i < 0) return
                typeMove(e.getX(i), e.getY(i))
            }
            Mode.TYPEOUT, Mode.MAPBLANK -> {
                val i = e.findPointerIndex(activePointer)
                if (i < 0) return
                if (hypot(e.getX(i) - downX, e.getY(i) - downY) > touchSlop) typeMoved = true
            }
            Mode.TABLELINE -> {
                val i = e.findPointerIndex(activePointer)
                if (i < 0) return
                tableLineMove(e.getX(i), e.getY(i))
            }
            Mode.CHECKTAP -> {
                val i = e.findPointerIndex(activePointer)
                if (i < 0) return
                if (hypot(e.getX(i) - downX, e.getY(i) - downY) > touchSlop) mode = Mode.IGNORE
            }
            Mode.MAPNODE -> {
                val i = e.findPointerIndex(activePointer)
                if (i < 0) return
                mapMove(e.getX(i), e.getY(i))
            }
            Mode.PAN -> {
                val i = e.findPointerIndex(activePointer)
                if (i < 0) return
                val dx = e.getX(i) - downX
                val dy = e.getY(i) - downY
                panLastX = e.getX(i)
                panLastY = e.getY(i)
                if (!panMoved && hypot(dx, dy) < touchSlop) return
                panMoved = true
                // Halaman sudah di tepi dan jari terus menggeser ke samping: lembaran ikut terangkat mengikuti jari.
                if (tryStartTurn(e, i)) return
                tx = startTx + dx
                ty = startTy + dy
                clamp(Clamp.PAN)
                updateMatrix()
                transformDirty = true
                invalidate()
            }
            Mode.ZOOM -> {
                if (!measureFocus(e, skip = -1)) return
                val s = (startScale * focusDist / startDist).coerceIn(fitScale * 0.4f, fitScale * 5f)
                // Putar dengan dua jari: baru ikut berputar setelah jari jelas diputar, supaya mencubit biasa tidak memiringkan.
                var da = focusAngle - startAngle
                while (da > 180f) da -= 360f
                while (da < -180f) da += 360f
                if (!rotEngaged && abs(da) > 10f && rotAllowed) {
                    rotEngaged = true
                    startAngle += if (da > 0f) 10f else -10f
                    da = focusAngle - startAngle
                    while (da > 180f) da -= 360f
                    while (da < -180f) da += 360f
                }
                val r = if (rotEngaged) startRot + da else startRot
                scale = s
                rot = r
                // Titik halaman di bawah jari tetap di bawah jari.
                clampM.setScale(s, s)
                if (r != 0f) clampM.postRotate(r)
                zoomPt[0] = zoomPageX
                zoomPt[1] = zoomPageY
                clampM.mapPoints(zoomPt)
                tx = focusX - zoomPt[0]
                ty = focusY - zoomPt[1]
                clamp(Clamp.FREE)
                updateMatrix()
                transformDirty = true
                invalidate()
            }
            else -> Unit
        }
    }

    private fun onPointerUp(e: MotionEvent) {
        disarmLongPress()
        val upIndex = e.actionIndex
        val id = e.getPointerId(upIndex)
        when (mode) {
            Mode.DRAW -> if (id == activePointer) { commitStroke(); mode = Mode.IGNORE }
            Mode.ERASE -> if (id == activePointer) { commitErase(); mode = Mode.IGNORE }
            Mode.SHAPE -> if (id == activePointer) { commitShape(); mode = Mode.IGNORE }
            Mode.TAPE -> if (id == activePointer) { commitTape(); mode = Mode.IGNORE }
            Mode.TAPETAP -> if (id == activePointer) { tapeHit?.let { toggleTape(it) }; tapeHit = null; mode = Mode.IGNORE }
            Mode.SELECT -> if (id == activePointer) { selectUp(commit = true); mode = Mode.IGNORE }
            Mode.TURN -> if (id == activePointer) { releaseTurn(commit = true); mode = Mode.IGNORE }
            Mode.TYPESEL -> if (id == activePointer) { typeUp(); mode = Mode.IGNORE }
            Mode.MAPNODE -> if (id == activePointer) { mapNodeUp(commit = true); mode = Mode.IGNORE }
            Mode.TYPEOUT, Mode.MAPBLANK -> if (id == activePointer) mode = Mode.IGNORE
            Mode.RULER -> {
                // Kembali ke geser satu jari dengan jari yang tersisa.
                val other = (0 until e.pointerCount).firstOrNull { it != upIndex }
                if (other != null) startRuler(e, other)
            }
            Mode.ZOOM -> {
                val left = e.pointerCount - 1
                if (left >= 2) {
                    startZoom(e, skip = upIndex)
                } else if (left == 1) {
                    // Sapuan dua jari ke samping: ganti halaman; selain itu lanjut menggeser dengan satu jari.
                    val dir = swipeDirection(pan = false)
                    if (dir != 0) {
                        finishPanZoom()
                        mode = Mode.IGNORE
                        post { onSwipePage?.invoke(dir) }
                    } else {
                        val other = if (upIndex == 0) 1 else 0
                        startPan(e, other)
                    }
                }
            }
            else -> Unit
        }
    }

    private fun finishGesture(e: MotionEvent, commit: Boolean) {
        disarmLongPress()
        when (mode) {
            Mode.DRAW -> {
                if (commit) {
                    val i = e.findPointerIndex(activePointer)
                    if (i >= 0) addPoint(e.getX(i), e.getY(i), e.getPressure(i), false, e.eventTime)
                    commitStroke()
                } else {
                    builder = null
                }
            }
            Mode.ERASE -> commitErase()
            Mode.SHAPE -> if (commit) commitShape()
            Mode.TAPE -> if (commit) commitTape() else invalidate()
            Mode.TAPETAP -> {
                if (commit) tapeHit?.let { toggleTape(it) }
                tapeHit = null
            }
            Mode.SELECT -> selectUp(commit)
            Mode.CHECKTAP -> if (commit) toggleCheckboxAt(checkTapX, checkTapY)
            Mode.TABLELINE -> tableLineUp(commit)
            Mode.PAN -> {
                val tapped = !panMoved && (activeIsFinger || panTapAny) && commit
                val outside = tapOutside
                val blank = tapBlank
                tapOutside = false
                tapBlank = false
                endPanZoom()
                if (tapped) {
                    when {
                        blank -> mapBlankTap()
                        outside -> tapOutsideEditing(downX, downY)
                        else -> tapAt(downX, downY)
                    }
                }
            }
            Mode.TYPESEL -> typeUp()
            Mode.TYPEOUT -> if (commit) {
                if (typeMoved) endInlineEditing() else tapOutsideEditing(downX, downY)
            }
            Mode.MAPBLANK -> if (commit && !typeMoved) mapBlankTap()
            Mode.MAPNODE -> mapNodeUp(commit)
            Mode.ZOOM -> endPanZoom()
            Mode.TURN -> releaseTurn(commit)
            else -> Unit
        }
        mode = Mode.NONE
        activePointer = -1
        showEraser = false
        rulerEdge = null
        invalidate()
    }

    private fun cancelInteraction() {
        disarmLongPress()
        if (mode == Mode.ERASE) commitErase()
        if (mode == Mode.SELECT) selectUp(commit = false)
        builder = null
        if (gestureActive) endPanZoom()
        mode = Mode.NONE
        activePointer = -1
        showEraser = false
        rulerEdge = null
    }

    private fun begin(e: MotionEvent, i: Int, erase: Boolean, finger: Boolean) {
        activePointer = e.getPointerId(i)
        activeIsFinger = finger
        if (erase && eraserMode == EraserMode.BRUSH) {
            // Kuas penghapus halus: disapukan seperti goresan, lalu memudarkan isi di bawahnya.
            mode = Mode.DRAW
            rulerEdge = null
            val alpha = (eraserStrength.coerceIn(0.05f, 1f) * 255).toInt() shl 24
            builder = StrokeBuilder(Tool.ERASER, alpha, eraserWidth)
            smoothP = if (finger) 0.5f else e.getPressure(i).coerceIn(0.05f, 1f)
            addPoint(e.getX(i), e.getY(i), e.getPressure(i), true, e.eventTime)
            invalidate()
            return
        }
        if (erase) {
            mode = Mode.ERASE
            erased = ArrayList()
            erasedTexts = ArrayList()
            erasePieces = LinkedHashSet()
            erasedItems = ArrayList()
            erasePieceSet.clear()
            eraseCutEmpty = true
            eraseImgPath = null
            eraseImgHit.clear()
            eraseChanged = false
            eraseDirtyEmpty = true
            eraseLastX = Float.NaN
            eraseLastY = Float.NaN
            eraseAt(e.getX(i), e.getY(i))
            flushEraseDirty()
            invalidate()
            return
        }
        if (inputMode == InputMode.TAPE) {
            mode = Mode.TAPE
            val pp = toPage(e.getX(i), e.getY(i))
            tapeX0 = pp.x; tapeY0 = pp.y; tapeX1 = pp.x; tapeY1 = pp.y
            tapeN = 0
            tapeLen = 0f
            tapeAddPoint(pp.x, pp.y)
            invalidate()
            return
        }
        if (inputMode == InputMode.INK) {
            // Pena di atas selotip hafalan: tunggu dulu, diketuk berarti buka/tutup, digeser berarti menulis.
            val pp = toPage(e.getX(i), e.getY(i))
            val hit = tapeAt(pp.x, pp.y)
            if (hit != null) {
                mode = Mode.TAPETAP
                tapeHit = hit
                downX = e.getX(i)
                downY = e.getY(i)
                tapeDownP = e.getPressure(i)
                tapeDownT = e.eventTime
                return
            }
        }
        if (inputMode == InputMode.SHAPE) {
            mode = Mode.SHAPE
            val pp = toPage(e.getX(i), e.getY(i))
            shapeX0 = pp.x; shapeY0 = pp.y; shapeX1 = pp.x; shapeY1 = pp.y
            invalidate()
            return
        }
        mode = Mode.DRAW
        onStrokeStarted?.invoke()
        rulerEdge = nearRulerEdge(e.getX(i), e.getY(i))
        builder = StrokeBuilder(tool, color, baseWidth)
        smoothP = if (finger) 0.5f else e.getPressure(i).coerceIn(0.05f, 1f)
        addPoint(e.getX(i), e.getY(i), e.getPressure(i), true, e.eventTime)
        invalidate()
    }

    private fun addPoint(vx: Float, vy: Float, rawP: Float, force: Boolean, time: Long) {
        val b = builder ?: return
        val edge = rulerEdge
        if (edge != null) snapToRuler(vx, vy, edge, pt) else { pt[0] = vx; pt[1] = vy }
        inverseMatrix.mapPoints(pt)
        val p = if (activeIsFinger) 0.5f else rawP.coerceIn(0.05f, 1f)
        smoothP = smoothP * 0.6f + p * 0.4f
        if (!force && b.size > 0) {
            val md = 0.75f / scale
            val dx = pt[0] - b.lastX
            val dy = pt[1] - b.lastY
            if (dx * dx + dy * dy < md * md) return
        }
        b.add(pt[0], pt[1], smoothP, time)
    }

    /**
     * Tempelkan bekas kuas penghapus lembut [m] ke setiap goresan, teks, dan benda yang tersentuh, sebagai satu
     * langkah yang bisa diurungkan. Objeknya diganti salinan yang membawa bekas itu.
     */
    private fun attachSoftErase(m: Stroke) {
        clearSelection()
        val rs = ArrayList<Pair<Int, Stroke>>()
        val ns = ArrayList<Stroke>()
        val nsAt = ArrayList<Int>()
        for (i in strokes.indices.reversed()) {
            val s = strokes[i]
            if (s.tool == Tool.ERASER || !SoftMasks.touches(s, m)) continue
            val n = s.withMask(m)
            strokes[i] = n
            rs.add(i to s); ns.add(n); nsAt.add(i)
        }
        val rt = ArrayList<Pair<Int, TextItem>>()
        val nt = ArrayList<TextItem>()
        val ntAt = ArrayList<Int>()
        for (i in texts.indices.reversed()) {
            val t = texts[i]
            InkRenderer.textBounds(t, box)
            if (!RectF.intersects(box, m.bounds)) continue
            val n = t.withMask(SoftMasks.textToLocal(t, m))
            texts[i] = n
            rt.add(i to t); nt.add(n); ntAt.add(i)
        }
        val ri = ArrayList<Pair<Int, PageItem>>()
        val ni = ArrayList<PageItem>()
        val niAt = ArrayList<Int>()
        for (i in items.indices.reversed()) {
            val it = items[i]
            if (it is TapeItem) continue
            InkRenderer.itemBounds(it, box)
            if (!RectF.intersects(box, m.bounds)) continue
            val local = SoftMasks.itemToLocal(it, m) ?: continue
            val n = it.withMask(local)
            items[i] = n
            ri.add(i to it); ni.add(n); niAt.add(i)
        }
        if (rs.isEmpty() && rt.isEmpty() && ri.isEmpty()) return
        pushUndo(
            Change(
                removedStrokes = rs, addedStrokes = ns, addedStrokesAt = nsAt,
                removedTexts = rt, addedTexts = nt, addedTextsAt = ntAt,
                removedItems = ri, addedItems = ni, addedItemsAt = niAt,
            )
        )
        contentChanged()
    }

    private fun commitStroke() {
        val b = builder ?: return
        builder = null
        if (b.size == 0) return
        if (b.tool == Tool.ERASER) {
            // Bekas kuas penghapus menempel pada objek yang disapunya, jadi tetap terhapus walau objeknya digeser.
            attachSoftErase(b.build())
            invalidate()
            return
        }
        // Ketukan singkat di atas teks ketik membuka penyunting, bukan membuat titik. Pena yang cepat
        // (240 titik per detik) menghasilkan puluhan titik walau hanya diketuk sebentar.
        if (b.size <= 48) {
            var minX = b.xs[0]; var maxX = b.xs[0]; var minY = b.ys[0]; var maxY = b.ys[0]
            for (i in 1 until b.size) {
                minX = min(minX, b.xs[i]); maxX = max(maxX, b.xs[i])
                minY = min(minY, b.ys[i]); maxY = max(maxY, b.ys[i])
            }
            if (max(maxX - minX, maxY - minY) * scale < 10f * density) {
                // Ketukan pena di rekaman: putar, bukan membuat titik.
                val audio = audioAt(b.xs[0], b.ys[0])
                if (audio != null && onAudioTapped != null) {
                    invalidate()
                    onAudioTapped?.invoke(audio)
                    return
                }
                // Ketukan pena di kotak centang: centang atau batalkan.
                if (toggleCheckboxAt(b.xs[0], b.ys[0])) {
                    invalidate()
                    return
                }
                val hit = if (onTextTapped != null) textGlyphAt(b.xs[0], b.ys[0]) else null
                if (hit != null) {
                    invalidate()
                    pickText(hit, b.xs[0], b.ys[0])
                    return
                }
                // Ketukan pena di mind map: sunting langsung di halaman.
                val mm = if (onMindMapTapped != null) mindMapNodeAt(b.xs[0], b.ys[0]) else null
                if (mm != null) {
                    invalidate()
                    onMindMapTapped?.invoke(mm.first, mm.second)
                    return
                }
                // Ketukan pena kedua di tabel: titik dari ketukan pertama dibuang, selnya dibuka untuk diketik.
                if (onCellTapped != null && isSecondTableTap(b.xs[0], b.ys[0])) {
                    invalidate()
                    tableTap(b.xs[0], b.ys[0], null)
                    return
                }
                tapDotInTable = onCellTapped != null && tableCellAt(b.xs[0], b.ys[0]) != null
            }
        }
        val s = b.build()
        // Coretan baru selalu di lapisan paling depan dan tidak menempel ke benda di bawahnya.
        s.z = nextZ()
        strokes.add(s)
        pushUndo(Change(addedStrokes = listOf(s)))
        if (tapDotInTable) {
            // Ketukan pena pertama di tabel: titiknya tetap (bisa jadi memang titik), tapi dicatat.
            tapDotInTable = false
            tableTap(b.xs[0], b.ys[0], s)
        } else {
            cellTapId = 0L
        }
        if (touchesTape(s.bounds)) {
            // Selotip selalu di atas tulisan: bagian yang tertimpa digambar ulang lengkap, bukan sekadar ditambah.
            contentChanged()
        } else {
            drawIntoCache(s)
            onStrokesChanged?.invoke()
        }
        onStrokeAdded?.invoke(s)
    }

    /** Ada selotip hafalan yang bersinggungan dengan kotak [r] (satuan halaman). */
    private fun touchesTape(r: RectF): Boolean {
        for (it in items) {
            if (it !is TapeItem) continue
            InkRenderer.itemBounds(it, box)
            if (RectF.intersects(box, r)) return true
        }
        return false
    }

    private fun commitShape() {
        if (hypot(shapeX1 - shapeX0, shapeY1 - shapeY0) * scale < 6f * density) {
            invalidate(); return
        }
        val added = Shapes.build(shapeKind, shapeX0, shapeY0, shapeX1, shapeY1, color, baseWidth)
        for (st in added) st.z = nextZ()
        strokes.addAll(added)
        pushUndo(Change(addedStrokes = added))
        contentChanged()
    }

    // ---- selotip hafalan ----

    private fun tapeAddPoint(x: Float, y: Float) {
        if (tapeN * 2 + 2 > tapePts.size) tapePts = tapePts.copyOf(tapePts.size * 2)
        tapePts[tapeN * 2] = x
        tapePts[tapeN * 2 + 1] = y
        tapeN++
    }

    private fun tapeMove(vx: Float, vy: Float) {
        val pp = toPage(vx, vy)
        tapeX1 = pp.x; tapeY1 = pp.y
        if (tapeShape == TapeShape.FREE && tapeN > 0) {
            val lx = tapePts[tapeN * 2 - 2]
            val ly = tapePts[tapeN * 2 - 1]
            val d = hypot(pp.x - lx, pp.y - ly)
            if (d >= Tapes.minStep(scale)) {
                tapeAddPoint(pp.x, pp.y)
                tapeLen += d
            }
        }
    }

    /** Tarikan cukup jauh untuk jadi selotip (bukan ketukan). */
    private fun tapeMovedEnough(): Boolean {
        val far = if (tapeShape == TapeShape.FREE) tapeLen else hypot(tapeX1 - tapeX0, tapeY1 - tapeY0)
        return far * scale >= 6f * density
    }

    private fun buildTape(): TapeItem? = when (tapeShape) {
        TapeShape.STRAIGHT -> Tapes.straight(tapeX0, tapeY0, tapeX1, tapeY1, baseWidth, color)
        TapeShape.FREE -> Tapes.free(tapePts, tapeN, baseWidth, color)
        else -> Tapes.shape(tapeShape, tapeX0, tapeY0, tapeX1, tapeY1, max(baseWidth, 8f), color)
    }

    private fun commitTape() {
        if (!tapeMovedEnough()) {
            // Ketukan dengan alat Selotip: buka atau tutup selotip di titik itu.
            tapeAt(tapeX0, tapeY0)?.let { toggleTape(it) }
            invalidate()
            return
        }
        val tp = buildTape()
        mode = Mode.NONE
        if (tp == null) { invalidate(); return }
        addItem(tp, selectIt = false)
    }

    /** Selotip paling atas di titik ini (satuan halaman). */
    private fun tapeAt(px: Float, py: Float): TapeItem? {
        val pad = 4f / scale
        for (k in items.indices.reversed()) {
            val it = items[k]
            if (it is TapeItem && Tapes.hit(it, px, py, pad)) return it
        }
        return null
    }

    /** Buka atau tutup satu selotip. Tidak masuk riwayat urungkan (seperti membalik kartu hafalan). */
    private fun toggleTape(t: TapeItem) {
        t.covered = !t.covered
        InkRenderer.itemBounds(t, tapeBox)
        tapeBox.inset(-3f, -3f)
        refreshRegion(RectF(tapeBox))
        onStrokesChanged?.invoke()
        performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
    }

    /** Pena yang menyentuh selotip ternyata digeser: tulis seperti biasa mulai dari titik sentuh pertamanya. */
    private fun inkFromTapeTap(e: MotionEvent, i: Int) {
        tapeHit = null
        mode = Mode.DRAW
        onStrokeStarted?.invoke()
        rulerEdge = nearRulerEdge(downX, downY)
        builder = StrokeBuilder(tool, color, baseWidth)
        smoothP = if (activeIsFinger) 0.5f else tapeDownP.coerceIn(0.05f, 1f)
        addPoint(downX, downY, tapeDownP, true, tapeDownT)
        addPoint(e.getX(i), e.getY(i), e.getPressure(i), false, e.eventTime)
        invalidate()
    }

    /** Jumlah selotip hafalan di halaman ini, dan berapa yang sedang tertutup. */
    fun tapeCounts(): Pair<Int, Int> {
        var all = 0
        var closed = 0
        for (it in items) if (it is TapeItem) { all++; if (it.covered) closed++ }
        return all to closed
    }

    /** Tutup ([covered] true) atau buka semua selotip di halaman. Hasil: jumlah yang berubah. */
    fun setAllTapes(covered: Boolean): Int {
        var n = 0
        for (it in items) if (it is TapeItem && it.covered != covered) { it.covered = covered; n++ }
        if (n > 0) {
            invalidateCache()
            onStrokesChanged?.invoke()
        }
        return n
    }

    /** Lepas semua selotip di halaman (bisa diurungkan). Hasil: jumlahnya. */
    fun removeAllTapes(): Int {
        cancelInteraction()
        val removed = ArrayList<Pair<Int, PageItem>>()
        for (i in items.indices.reversed()) if (items[i] is TapeItem) { removed.add(i to items[i]); items.removeAt(i) }
        if (removed.isEmpty()) return 0
        if (selItems.any { it is TapeItem }) clearSelection()
        pushUndo(Change(removedItems = removed))
        contentChanged()
        return removed.size
    }

    /** Penghapus (sebagian atau utuh) mengenai selotip: lepas selotip itu seluruhnya. */
    private fun eraseTapesAt(px: Float, py: Float, r: Float) {
        for (idx in items.indices.reversed()) {
            val it = items[idx]
            if (it is TapeItem && Tapes.hit(it, px, py, r)) {
                items.removeAt(idx)
                erasedItems.add(idx to it)
                InkRenderer.itemBounds(it, box)
                box.inset(-4f, -4f)
                markEraseDirty(box)
                eraseChanged = true
            }
        }
    }

    private fun cancelStroke() {
        if (mode == Mode.ERASE) commitErase()
        builder = null
        mode = Mode.NONE
        showEraser = false
        rulerEdge = null
        invalidate()
    }

    // ---- penghapus ----

    private fun eraseAt(vx: Float, vy: Float) {
        eraserX = vx; eraserY = vy
        showEraser = true
        pt[0] = vx; pt[1] = vy
        inverseMatrix.mapPoints(pt)
        val px = pt[0]
        val py = pt[1]
        val r = max(eraserWidth / 2f, 3f / scale)
        if (eraserMode == EraserMode.PARTIAL) {
            eraseReqX = px
            eraseReqY = py
            // Titik yang terlalu rapat tidak perlu diproses satu per satu: jalur penghapus berbentuk kapsul
            // dari titik terakhir yang diproses tetap menutup semuanya.
            if (!eraseLastX.isNaN() && hypot(px - eraseLastX, py - eraseLastY) < r * 0.35f) return
            eraseTapesAt(px, py, r)
            erasePartial(px, py, r)
            return
        }
        eraseTapesAt(px, py, r)
        var removed = false
        for (idx in strokes.indices.reversed()) {
            val s = strokes[idx]
            if (s.tool == Tool.ERASER) continue
            val bnd = s.bounds
            if (px < bnd.left - r || px > bnd.right + r || py < bnd.top - r || py > bnd.bottom + r) continue
            if (hits(s, px, py, r)) {
                strokes.removeAt(idx)
                erased.add(idx to s)
                markEraseDirty(s.bounds)
                removed = true
            }
        }
        for (idx in texts.indices.reversed()) {
            val t = texts[idx]
            InkRenderer.textBounds(t, box)
            if (px >= box.left - r && px <= box.right + r && py >= box.top - r && py <= box.bottom + r) {
                texts.removeAt(idx)
                erasedTexts.add(idx to t)
                markEraseDirty(box)
                removed = true
            }
        }
        if (eraseImages) {
            for (idx in items.indices.reversed()) {
                val it = items[idx]
                if (it is ImageItem && itemContains(it, px, py, r)) {
                    items.removeAt(idx)
                    erasedItems.add(idx to it)
                    InkRenderer.itemBounds(it, box)
                    box.inset(-40f, -40f) // bayangan gambar
                    markEraseDirty(box)
                    removed = true
                }
            }
        }
        if (removed) eraseChanged = true
    }

    private fun markEraseDirty(r: RectF) {
        if (eraseDirtyEmpty) { eraseDirty.set(r); eraseDirtyEmpty = false } else eraseDirty.union(r)
    }

    /** Gambar ulang hanya bagian halaman yang berubah oleh penghapus, bukan seluruh halaman. */
    private fun flushEraseDirty() {
        if (eraseDirtyEmpty) return
        eraseDirtyEmpty = true
        if (!cacheValid || gestureActive || settling || viewStale || cacheCanvas == null) { settleNow(); invalidateCache(); return }
        redrawCacheRegion(eraseDirty)
    }

    private fun hits(s: Stroke, px: Float, py: Float, r: Float): Boolean {
        val tol = r + s.width * 0.6f
        if (s.size == 1) return hypot(px - s.xs[0], py - s.ys[0]) <= tol
        for (i in 1 until s.size) {
            if (distToSegment(px, py, s.xs[i - 1], s.ys[i - 1], s.xs[i], s.ys[i]) <= tol) return true
        }
        return false
    }

    private fun distToSegment(px: Float, py: Float, ax: Float, ay: Float, bx: Float, by: Float): Float {
        val dx = bx - ax
        val dy = by - ay
        val len2 = dx * dx + dy * dy
        if (len2 == 0f) return hypot(px - ax, py - ay)
        val t = (((px - ax) * dx + (py - ay) * dy) / len2).coerceIn(0f, 1f)
        val cx = ax + t * dx
        val cy = ay + t * dy
        return sqrt((px - cx) * (px - cx) + (py - cy) * (py - cy))
    }

    /** Penghapus sebagian: potong goresan yang tersapu kapsul dari posisi sebelumnya ke posisi sekarang. */
    private fun erasePartial(px: Float, py: Float, r: Float) {
        val ax = if (eraseLastX.isNaN()) px else eraseLastX
        val ay = if (eraseLastY.isNaN()) py else eraseLastY
        eraseLastX = px
        eraseLastY = py
        val minX = min(ax, px); val maxX = max(ax, px)
        val minY = min(ay, py); val maxY = max(ay, py)
        val step = max(r / 3f, 0.4f)
        var changed = false
        var idx = strokes.size - 1
        while (idx >= 0) {
            val s = strokes[idx]
            if (s.tool == Tool.ERASER) { idx--; continue }
            val reach = r + s.width * 0.4f
            val b = s.bounds
            if (b.right < minX - reach || b.left > maxX + reach || b.bottom < minY - reach || b.top > maxY + reach) { idx--; continue }
            val pieces = StrokeCut.cut(s.xs, s.ys, s.ps, s.size, ax, ay, px, py, reach, step)
            if (pieces == null) { idx--; continue }
            strokes.removeAt(idx)
            // Potongan dari sapuan ini yang terpotong lagi cukup dibuang; goresan asli dicatat untuk urungkan.
            if (erasePieceSet.remove(s)) erasePieces.remove(s) else erased.add(idx to s)
            val made = pieces.map { p ->
                Stroke(s.tool, s.color, s.width, p.xs, p.ys, p.ps).also { it.group = s.group; it.z = s.z; it.masks = s.masks }
            }
            if (made.isNotEmpty()) strokes.addAll(idx, made)
            erasePieces.addAll(made)
            erasePieceSet.addAll(made)
            // Selama menyapu cukup gambar ulang daerah yang tersapu; seluruh goresan dirapikan saat pena diangkat.
            val pad = reach + s.width * 1.7f + 2f
            regionRect.set(minX - pad, minY - pad, maxX + pad, maxY + pad)
            markEraseDirty(regionRect)
            if (eraseCutEmpty) { eraseCutBounds.set(s.bounds); eraseCutEmpty = false } else eraseCutBounds.union(s.bounds)
            changed = true
            idx--
        }
        if (eraseImages) {
            // Gambar yang tersapu: catat jejak penghapus; berkas gambarnya diperbarui saat pena diangkat.
            val path = eraseImgPath ?: Path().also {
                it.moveTo(ax, ay)
                eraseImgPath = it
                eraseImgWidth = r * 2f
            }
            path.lineTo(px + 0.01f, py)
            for (it in items) {
                if (it !is ImageItem) continue
                InkRenderer.itemBounds(it, box)
                if (box.right < minX - r || box.left > maxX + r || box.bottom < minY - r || box.top > maxY + r) continue
                if (!itemContains(it, px, py, r) && !itemContains(it, ax, ay, r) && !itemContains(it, (ax + px) / 2f, (ay + py) / 2f, r)) continue
                if (eraseImgHit.add(it)) {
                    imageMasks.getOrPut(it) { ArrayList() }.add(InkRenderer.EraseMask(path, eraseImgWidth))
                }
                regionRect.set(minX - r - 2f, minY - r - 2f, maxX + r + 2f, maxY + r + 2f)
                markEraseDirty(regionRect)
                changed = true
            }
        }
        if (changed) eraseChanged = true
    }

    /**
     * Simpan jejak penghapus ke berkas gambar baru (di latar), lalu ganti gambar di halaman dengan versi
     * barunya. Perubahan coretan dan gambar dari sapuan yang sama dicatat sebagai satu langkah urungkan.
     */
    private fun bakeImageErase(hit: List<ImageItem>, path: Path, width: Float, strokeChange: Change) {
        val dir = imageDir
        val target = items
        if (dir == null) {
            for (it in hit) imageMasks.remove(it)
            pushUndo(strokeChange)
            return
        }
        bakesPending++
        Thread {
            val results = HashMap<ImageItem, String>()
            for (img in hit) {
                runCatching { eraseIntoFile(dir, img, path, width) }.getOrNull()?.let { results[img] = it }
            }
            post {
                bakesPending = (bakesPending - 1).coerceAtLeast(0)
                if (target !== items) {
                    // Halaman sudah berganti: langkah halaman lama tidak boleh masuk riwayat halaman ini.
                    runQueuedHistory()
                    return@post
                }
                val ri = ArrayList<Pair<Int, PageItem>>()
                val ai = ArrayList<PageItem>()
                val at = ArrayList<Int>()
                for (img in hit) {
                    val masks = imageMasks[img]
                    masks?.removeAll { it.path === path }
                    if (masks != null && masks.isEmpty()) imageMasks.remove(img)
                    val name = results[img] ?: continue
                    if (target !== items) continue
                    val idx = items.indexOf(img)
                    if (idx < 0) continue
                    val n = img.withFile(name)
                    items[idx] = n
                    ri.add(idx to img); ai.add(n); at.add(idx)
                    if (img in selItems) selectObjects(selItems.map { if (it === img) n else it }, selTexts, selStrokes, expand = false)
                }
                if (ri.isEmpty()) {
                    if (!strokeChange.isEmpty) pushUndo(strokeChange)
                } else {
                    ri.sortByDescending { it.first }
                    pushUndo(
                        Change(
                            removedStrokes = strokeChange.removedStrokes, addedStrokes = strokeChange.addedStrokes,
                            removedTexts = strokeChange.removedTexts,
                            removedItems = ri, addedItems = ai, addedItemsAt = at,
                        )
                    )
                }
                pendingChange = null
                contentChanged()
                runQueuedHistory()
            }
        }.start()
    }

    /** Jalankan urungkan/ulangi yang diketuk selagi hapusan gambar masih disimpan. */
    private fun runQueuedHistory() {
        if (bakesPending > 0 || afterBake.isEmpty()) return
        val queued = ArrayList(afterBake)
        afterBake.clear()
        queued.forEach { it.run() }
    }

    /** Kosongkan bagian gambar yang tersapu penghapus dan simpan sebagai berkas PNG baru. Hasil: nama berkasnya. */
    private fun eraseIntoFile(dir: java.io.File, img: ImageItem, pagePath: Path, width: Float): String? {
        val src = java.io.File(dir, img.file)
        val opts = android.graphics.BitmapFactory.Options().apply {
            inMutable = true
            inPreferredConfig = android.graphics.Bitmap.Config.ARGB_8888
        }
        val bmp = android.graphics.BitmapFactory.decodeFile(src.path, opts) ?: return null
        try {
            bmp.setHasAlpha(true)
            val crop = img.crop
            val srcL = crop.l * bmp.width
            val srcT = crop.t * bmp.height
            val srcW = (crop.r - crop.l).coerceAtLeast(0.01f) * bmp.width
            val srcH = (crop.b - crop.t).coerceAtLeast(0.01f) * bmp.height
            val sx = srcW / img.w.coerceAtLeast(0.01f)
            val sy = srcH / img.h.coerceAtLeast(0.01f)
            // Halaman → piksel gambar: batalkan putaran dan balikan, lalu petakan kotak gambar ke bagian berkas.
            val m = Matrix()
            m.postRotate(-img.rotation, img.cx, img.cy)
            m.postScale(if (img.flipH) -1f else 1f, if (img.flipV) -1f else 1f, img.cx, img.cy)
            m.postTranslate(-img.x, -img.y)
            m.postScale(sx, sy)
            m.postTranslate(srcL, srcT)
            val p = Path(pagePath)
            p.transform(m)
            val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE
                strokeCap = Paint.Cap.ROUND
                strokeJoin = Paint.Join.ROUND
                strokeWidth = width * (sx + sy) / 2f
                xfermode = android.graphics.PorterDuffXfermode(android.graphics.PorterDuff.Mode.CLEAR)
            }
            Canvas(bmp).drawPath(p, paint)
            val name = "img_" + java.util.UUID.randomUUID().toString() + ".png"
            java.io.FileOutputStream(java.io.File(dir, name)).use { bmp.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
            return name
        } finally {
            bmp.recycle()
        }
    }

    private fun commitErase() {
        if (eraserMode == EraserMode.PARTIAL && !eraseReqX.isNaN() && (eraseReqX != eraseLastX || eraseReqY != eraseLastY)) {
            erasePartial(eraseReqX, eraseReqY, max(eraserWidth / 2f, 3f / scale))
        }
        eraseReqX = Float.NaN
        eraseReqY = Float.NaN
        if (!eraseCutEmpty) {
            // Potongan goresan (misalnya cat air dan pensil) bisa sedikit berubah di luar daerah yang tersapu.
            markEraseDirty(eraseCutBounds)
            eraseCutEmpty = true
        }
        flushEraseDirty()
        if (eraseChanged) {
            eraseChanged = false
            onStrokesChanged?.invoke()
        }
        erasePieceSet.clear()
        // Urutan indeks dari besar ke kecil, seperti yang diharapkan urungkan.
        erased.sortByDescending { it.first }
        erasedItems.sortByDescending { it.first }
        val change = Change(removedStrokes = erased, removedTexts = erasedTexts, addedStrokes = erasePieces.toList(), removedItems = erasedItems)
        val imgPath = eraseImgPath
        if (imgPath != null && eraseImgHit.isNotEmpty()) {
            bakeImageErase(eraseImgHit.toList(), imgPath, eraseImgWidth, change)
        } else if (!change.isEmpty) {
            pushUndo(change)
        }
        erased = ArrayList()
        erasedTexts = ArrayList()
        erasePieces = LinkedHashSet()
        erasedItems = ArrayList()
        eraseImgPath = null
        eraseImgHit.clear()
        eraseLastX = Float.NaN
        eraseLastY = Float.NaN
        showEraser = false
    }

    // ---- pilih, geser, ubah ukuran ----

    /** Objek paling depan di titik ini: benda, teks, atau coretan. */
    private fun hitTest(px: Float, py: Float): Any? {
        val slop = 6f / scale
        val r = max(6f / scale, 3f)
        var best: Any? = null
        var bestZ = Long.MIN_VALUE
        for (t in texts) {
            if (best != null && t.z <= bestZ) continue
            InkRenderer.textBounds(t, box)
            box.inset(-slop, -slop)
            if (box.contains(px, py)) { best = t; bestZ = t.z }
        }
        for (it in items) {
            if (best != null && it.z <= bestZ) continue
            if (itemContains(it, px, py, slop)) { best = it; bestZ = it.z }
        }
        for (st in strokes) {
            if (st.tool == Tool.ERASER) continue
            if (best != null && st.z <= bestZ) continue
            val b = st.bounds
            if (px < b.left - r || px > b.right + r || py < b.top - r || py > b.bottom + r) continue
            if (hits(st, px, py, r)) { best = st; bestZ = st.z }
        }
        return best
    }

    /** Pegangan yang disentuh (koordinat layar): 0..3 sudut, 4 pegangan putar, atau -1. */
    private fun cornerAt(vx: Float, vy: Float): Int {
        if (!selectionGeometry()) return -1
        if (selectionRotatable() && hypot(vx - handlePos[2], vy - handlePos[3]) <= 22f * density) return 4
        if (selText != null) {
            if (hypot(vx - sidePos[0], vy - sidePos[1]) <= 16f * density) return 5
            if (hypot(vx - sidePos[2], vy - sidePos[3]) <= 16f * density) return 6
        }
        val reach = 24f * density
        for (k in 0..3) {
            if (hypot(vx - corners[k * 2], vy - corners[k * 2 + 1]) <= reach) return k
        }
        return -1
    }

    private fun selectDown(e: MotionEvent, i: Int, finger: Boolean) {
        val vx = e.getX(i)
        val vy = e.getY(i)
        activePointer = e.getPointerId(i)
        activeIsFinger = finger
        val pp = toPage(vx, vy)
        val corner = cornerAt(vx, vy)
        if (corner < 0 && checkboxAt(pp.x, pp.y) != null) {
            // Kotak centang diketuk di mode Pilih: centang, bukan memilih.
            mode = Mode.CHECKTAP
            checkTapX = pp.x
            checkTapY = pp.y
            downX = vx
            downY = vy
            return
        }
        // Tabel terpilih: garis di dalamnya bisa digeser untuk mengubah lebar kolom atau tinggi baris.
        val tsel = selItem as? TableItem
        if (corner < 0 && tsel != null && selTexts.isEmpty() && selStrokes.isEmpty()) {
            val line = tableLineAt(tsel, pp.x, pp.y)
            if (line != null) {
                mode = Mode.TABLELINE
                lineTable = tsel
                lineIsCol = line.first
                lineIndex = line.second
                lineOffset = if (lineIsCol) pp.x - (tsel.x + tsel.colEdge(lineIndex)) else pp.y - (tsel.y + tsel.rowEdge(lineIndex))
                lineMoved = false
                lineDownX = pp.x
                lineDownY = pp.y
                downX = vx
                downY = vy
                invalidate()
                return
            }
        }
        val inside = hasSelection && insideSelection(pp.x, pp.y, 6f / scale)
        if (corner < 0 && !inside) {
            val hit = hitTest(pp.x, pp.y)
            if (pickedForMove && (hit == null || hit is Stroke)) {
                // Selesai memindah teks: sentuhan di luarnya langsung menulis (atau menggeser) seperti biasa.
                pickedForMove = false
                inputMode = InputMode.INK
                if (!finger || fingerDraws) {
                    requestUnbufferedDispatch(e)
                    begin(e, i, wantsErase(e, i), finger)
                } else {
                    startPan(e, i)
                }
                return
            }
            // Di tempat kosong atau di atas tulisan tangan: laso bebas untuk memilih (dengan pena, atau
            // dengan jari bila jari dipakai menulis). Ketukan saja di atas tulisan memilih goresan itu.
            val lassoAllowed = !finger || fingerDraws
            if (hit == null || (hit is Stroke && lassoAllowed)) {
                clearSelection()
                if (lassoAllowed) {
                    mode = Mode.SELECT
                    lasso = arrayListOf(pp.x, pp.y)
                    lassoTap = hit as? Stroke
                    dragging = false
                    return
                }
                startPan(e, i)
                return
            }
            select(hit)
        }
        mode = Mode.SELECT
        dragging = false
        dragCorner = corner
        widthPreview = null
        dragStartX = pp.x
        dragStartY = pp.y
        dragFrom.set(selRect)
        dragTo.set(selRect)
        dragRot0 = selRotation()
        dragRot1 = dragRot0
        val cx = selRect.centerX()
        val cy = selRect.centerY()
        if (corner == 4) {
            dragAngle0 = Math.toDegrees(atan2((pp.y - cy).toDouble(), (pp.x - cx).toDouble())).toFloat()
        } else if (corner in 0..3) {
            // Simpan selisih sudut dan titik sentuh supaya sudut tidak melompat ke ujung jari.
            toLocal(pp.x, pp.y, cx, cy, dragRot0)
            val kx = if (corner == 1 || corner == 2) selRect.width() / 2f else -selRect.width() / 2f
            val ky = if (corner == 2 || corner == 3) selRect.height() / 2f else -selRect.height() / 2f
            dragOffX = kx - local[0]
            dragOffY = ky - local[1]
        }
    }

    private fun selectMove(vx: Float, vy: Float) {
        val p = page ?: return
        val pp = toPage(vx, vy)
        lasso?.let { pts ->
            val lx = pts[pts.size - 2]
            val ly = pts[pts.size - 1]
            if (hypot(pp.x - lx, pp.y - ly) * scale >= 3f * density) {
                pts.add(pp.x); pts.add(pp.y)
                invalidate()
            }
            return
        }
        val dx = pp.x - dragStartX
        val dy = pp.y - dragStartY
        if (!dragging) {
            if (hypot(dx, dy) * scale < touchSlop) return
            dragging = true
            lift()
        }
        val c0x = dragFrom.centerX()
        val c0y = dragFrom.centerY()
        if (dragCorner == 4) {
            // Putar mengikuti jari, menempel ke kelipatan 15° bila sudah dekat.
            val a = Math.toDegrees(atan2((pp.y - c0y).toDouble(), (pp.x - c0x).toDouble())).toFloat()
            var r = PageItem.normalizeDeg(dragRot0 + (a - dragAngle0))
            val near = (r / 15f).roundToInt() * 15f
            if (abs(r - near) < 3f) r = near
            dragRot1 = PageItem.normalizeDeg(r)
            dragTo.set(dragFrom)
        } else if (dragCorner < 0) {
            dragTo.set(dragFrom)
            dragTo.offset(dx, dy)
        } else if (dragCorner == 5 || dragCorner == 6) {
            // Lebar kotak teks saja; tinggi mengikuti baris yang mengalir ulang.
            val t = lifted?.texts?.singleOrNull()?.second
            if (t != null) {
                val minW = max(t.size * 2f, 24f)
                var l = dragFrom.left
                var r = dragFrom.right
                if (dragCorner == 5) r = (dragFrom.right + dx).coerceIn(l + minW, p.width) else l = (dragFrom.left + dx).coerceIn(0f, r - minW)
                val preview = t.withBox(l, r - l)
                widthPreview = preview
                dragTo.set(l, dragFrom.top, r, dragFrom.top + InkRenderer.layoutFor(preview).height)
            }
            dragRot1 = dragRot0
        } else {
            // Ubah ukuran di sumbu benda itu sendiri; sudut seberang tetap di tempat.
            val hw = dragFrom.width() / 2f
            val hh = dragFrom.height() / 2f
            toLocal(pp.x, pp.y, c0x, c0y, dragRot0)
            var mx = local[0] + dragOffX
            var my = local[1] + dragOffY
            val ax = if (dragCorner == 0 || dragCorner == 3) hw else -hw
            val ay = if (dragCorner == 0 || dragCorner == 1) hh else -hh
            val cx0 = -ax
            val cy0 = -ay
            val minSize = 16f
            // Pilihan banyak dan gambar menjaga perbandingan sisi.
            val keep = !isSingle || selText != null || selItem?.keepAspect == true
            if (keep && dragFrom.width() > 0f && dragFrom.height() > 0f) {
                val s = max(abs(mx - ax) / dragFrom.width(), abs(my - ay) / dragFrom.height())
                    .coerceAtLeast(minSize / min(dragFrom.width(), dragFrom.height()))
                mx = ax + (if (cx0 >= ax) 1f else -1f) * dragFrom.width() * s
                my = ay + (if (cy0 >= ay) 1f else -1f) * dragFrom.height() * s
            } else {
                if (abs(mx - ax) < minSize) mx = ax + (if (cx0 >= ax) minSize else -minSize)
                if (abs(my - ay) < minSize) my = ay + (if (cy0 >= ay) minSize else -minSize)
            }
            val nw = abs(mx - ax)
            val nh = abs(my - ay)
            fromLocal((ax + mx) / 2f, (ay + my) / 2f, c0x, c0y, dragRot0)
            dragTo.set(local[0] - nw / 2f, local[1] - nh / 2f, local[0] + nw / 2f, local[1] + nh / 2f)
            dragRot1 = dragRot0
        }
        // Jangan sampai keluar dari kertas sepenuhnya.
        val ox = when {
            dragTo.right < 12f -> 12f - dragTo.right
            dragTo.left > p.width - 12f -> p.width - 12f - dragTo.left
            else -> 0f
        }
        val oy = when {
            dragTo.bottom < 12f -> 12f - dragTo.bottom
            dragTo.top > p.height - 12f -> p.height - 12f - dragTo.top
            else -> 0f
        }
        dragTo.offset(ox, oy)
        invalidate()
    }

    /** Teks bergaris: garis dasar baris pertamanya dipindah ke garis kertas terdekat. */
    private fun snapToRule(t: TextItem, r: RuledLines): TextItem {
        val b0 = InkRenderer.layoutFor(t).getLineBaseline(0)
        val base = t.y + b0
        val n = Math.round((base - r.first) / r.gap).coerceAtLeast(0)
        val target = r.first + n * r.gap
        if (abs(target - base) < 0.01f) return t
        return t.at(t.x, target - b0)
    }

    /** Keluarkan yang dipilih (beserta tulisan di atas bendanya) dari halaman selama digeser. */
    private fun lift() {
        val l = removeSelected()
        lifted = l
        refreshRegion(liftedBounds(l))
    }

    private fun selectUp(commit: Boolean) {
        val lassoPts = lasso
        if (lassoPts != null) {
            lasso = null
            val tap = lassoTap
            lassoTap = null
            if (commit) finishLasso(lassoPts, tap)
            invalidate()
            return
        }
        val l = lifted
        if (l == null) {
            dragging = false
            if (commit && dragCorner < 0) selectedTextTap(dragStartX, dragStartY)
            return
        }
        lifted = null
        dragging = false
        val moved = abs(dragTo.left - dragFrom.left) + abs(dragTo.top - dragFrom.top) +
            abs(dragTo.width() - dragFrom.width()) + abs(dragTo.height() - dragFrom.height()) > 0.5f ||
            abs(dragRot1 - dragRot0) > 0.05f
        if (!commit || !moved) {
            // Kembalikan ke tempat semula.
            widthPreview = null
            restore(l)
            refreshRegion(liftedBounds(l))
            return
        }
        dragMatrix(liftMatrix)
        val sx = if (dragFrom.width() > 0f) dragTo.width() / dragFrom.width() else 1f
        val sy = if (dragFrom.height() > 0f) dragTo.height() / dragFrom.height() else 1f
        val ws = sqrt(abs(sx * sy))
        val turn = dragRot1 - dragRot0
        val pts = FloatArray(2)
        val newStrokes = l.strokes.map { transformStroke(it.second, liftMatrix, ws) }
        val newItems = l.items.map { (_, it) ->
            pts[0] = it.cx; pts[1] = it.cy
            liftMatrix.mapPoints(pts)
            val nw = it.w * sx
            val nh = it.h * sy
            val placed = it.placed(pts[0] - nw / 2f, pts[1] - nh / 2f, nw, nh, PageItem.normalizeDeg(it.rotation + turn))
            // Tabel diubah ukurannya: teks sel mengalir ulang, baris yang tidak muat dipertinggi.
            if (placed is TableItem && (abs(sx - 1f) > 0.01f || abs(sy - 1f) > 0.01f)) InkRenderer.fitTable(placed) else placed
        }
        val preview = widthPreview
        widthPreview = null
        // Teks bergaris yang digeser (tanpa diubah ukurannya) kembali duduk di garis kertas terdekat.
        val ruledNow = if (abs(sx - 1f) < 0.01f && abs(sy - 1f) < 0.01f) RuledLines.of(page) else null
        val newTexts = l.texts.map { (_, t) ->
            if (preview != null && (dragCorner == 5 || dragCorner == 6) && l.texts.size == 1) {
                preview
            } else {
                pts[0] = t.x; pts[1] = t.y
                liftMatrix.mapPoints(pts)
                val m = t.moved(pts[0], pts[1], t.width * sx, t.size * sx)
                if (ruledNow != null && m.linePitch > 0f) snapToRule(m, ruledNow) else m
            }
        }
        // Kembali ke lapisan semula (urutan dari indeks terkecil).
        val strokeAt = l.strokes.map { it.first }
        val textAt = l.texts.map { it.first }
        val itemAt = l.items.map { it.first }
        insertAt(strokes, newStrokes, strokeAt)
        insertAt(texts, newTexts, textAt)
        insertAt(items, newItems, itemAt)
        pushUndo(
            Change(
                removedStrokes = l.strokes, addedStrokes = newStrokes, addedStrokesAt = strokeAt,
                removedTexts = l.texts, addedTexts = newTexts, addedTextsAt = textAt,
                removedItems = l.items, addedItems = newItems, addedItemsAt = itemAt,
            )
        )
        contentChanged()
        // Pilihan berpindah ke versi barunya.
        val wasFree = selStrokes.toHashSet()
        val newFree = newStrokes.filterIndexed { k, _ -> l.strokes[k].second in wasFree }
        selectObjects(newItems, newTexts, newFree, expand = false)
    }

    /**
     * Laso dilepas: pilih benda dan teks yang tengahnya di dalam laso, dan coretan yang sebagian besar
     * titiknya di dalam laso. Bila laso terlalu kecil (hanya ketukan), pilih coretan yang diketuk.
     */
    private fun finishLasso(pts: ArrayList<Float>, tap: Stroke?) {
        var minX = Float.MAX_VALUE; var maxX = -Float.MAX_VALUE
        var minY = Float.MAX_VALUE; var maxY = -Float.MAX_VALUE
        var k = 0
        while (k < pts.size) {
            minX = min(minX, pts[k]); maxX = max(maxX, pts[k])
            minY = min(minY, pts[k + 1]); maxY = max(maxY, pts[k + 1])
            k += 2
        }
        if ((maxX - minX) * scale < touchSlop && (maxY - minY) * scale < touchSlop || pts.size < 6) {
            if (tap != null && tap in strokes) select(tap)
            return
        }
        val poly = FloatArray(pts.size)
        for (j in pts.indices) poly[j] = pts[j]
        fun inside(x: Float, y: Float): Boolean {
            if (x < minX || x > maxX || y < minY || y > maxY) return false
            var c = false
            var j = poly.size - 2
            var i = 0
            while (i < poly.size) {
                val xi = poly[i]; val yi = poly[i + 1]
                val xj = poly[j]; val yj = poly[j + 1]
                if ((yi > y) != (yj > y) && x < (xj - xi) * (y - yi) / (yj - yi) + xi) c = !c
                j = i
                i += 2
            }
            return c
        }
        val its = items.filter { InkRenderer.itemBounds(it, box); inside(box.centerX(), box.centerY()) }
        val ts = texts.filter { InkRenderer.textBounds(it, box); inside(box.centerX(), box.centerY()) }
        val ss = strokes.filter { st ->
            val n = st.size
            if (n == 0) return@filter false
            val stride = max(1, n / 24)
            var tot = 0
            var hit = 0
            var i = 0
            while (i < n) {
                tot++
                if (inside(st.xs[i], st.ys[i])) hit++
                i += stride
            }
            hit * 2 >= tot
        }
        selectObjects(its, ts, ss)
    }

    /** Salinan lepas dari yang dipilih, untuk papan klip. */
    fun copySelection(): InkClipboard.Content? {
        if (!hasSelection) return null
        finishTextAnim()
        val same = Matrix()
        return InkClipboard.Content(
            selStrokes.sortedBy { it.z }.map { transformStroke(it, same, 1f) },
            selTexts.sortedBy { it.z }.map { it.moved(it.x, it.y, it.width, it.size) },
            selItems.sortedBy { it.z }.map { it.placed(it.x, it.y, it.w, it.h) },
        )
    }

    /**
     * Tempel isi papan klip di atas semuanya. Bila [offsetStep] > 0, salinan digeser sejauh itu (supaya tidak
     * menutupi aslinya di halaman yang sama). Posisi dijaga tetap di dalam halaman. Hasilnya langsung terpilih.
     */
    fun paste(content: InkClipboard.Content, offsetStep: Float, atX: Float = Float.NaN, atY: Float = Float.NaN) {
        val p = page ?: return
        if (content.isEmpty) return
        cancelInteraction()
        finishTextAnim()
        // Kotak gabungan isi papan klip.
        val all = RectF()
        var first = true
        fun add(r: RectF) { if (first) { all.set(r); first = false } else all.union(r) }
        content.items.forEach { InkRenderer.itemBounds(it, box); add(box) }
        content.texts.forEach { InkRenderer.textBounds(it, box); add(box) }
        content.strokes.forEach { add(it.bounds) }
        var dx = offsetStep
        var dy = offsetStep
        if (!atX.isNaN() && !atY.isNaN()) {
            // Tempel di titik yang ditahan: isi papan klip dipusatkan di sana.
            dx = atX - all.centerX()
            dy = atY - all.centerY()
        }
        if (all.right + dx > p.width) dx = p.width - all.right
        if (all.bottom + dy > p.height) dy = p.height - all.bottom
        if (all.left + dx < 0f) dx = -all.left
        if (all.top + dy < 0f) dy = -all.top
        val shift = Matrix().apply { setTranslate(dx, dy) }
        val groupMap = HashMap<Long, Long>()
        fun newGroup(g: Long) = if (g == 0L) 0L else groupMap.getOrPut(g) { PageItem.newId() }
        val newItems = content.items.map { old ->
            old.placed(old.x + dx, old.y + dy, old.w, old.h).also { n -> n.id = PageItem.newId(); n.group = newGroup(old.group) }
        }
        val newStrokes = content.strokes.map { old -> transformStroke(old, shift, 1f).also { n -> n.group = newGroup(old.group) } }
        val newTexts = content.texts.map { old -> old.moved(old.x + dx, old.y + dy, old.width, old.size).also { n -> n.group = newGroup(old.group) } }
        val order = ArrayList<Any>()
        order.addAll(newItems); order.addAll(newTexts); order.addAll(newStrokes)
        order.sortBy { layerZ(it) }
        for (o in order) setZ(o, nextZ())
        strokes.addAll(newStrokes)
        texts.addAll(newTexts)
        items.addAll(newItems)
        pushUndo(Change(addedStrokes = newStrokes, addedTexts = newTexts, addedItems = newItems))
        contentChanged()
        selectObjects(newItems, newTexts, newStrokes, expand = false)
    }

    /** Ganti warna coretan dan teks yang dipilih (bisa diurungkan). */
    fun recolorSelection(color: Int) {
        val selTapes = selItems.filterIsInstance<TapeItem>().toHashSet()
        if (selStrokes.isEmpty() && selTexts.isEmpty() && selTapes.isEmpty()) return
        finishTextAnim()
        val sSet = selStrokes.toHashSet()
        val tSet = selTexts.toHashSet()
        val rs = ArrayList<Pair<Int, Stroke>>()
        val ns = ArrayList<Stroke>()
        val nsAt = ArrayList<Int>()
        for (i in strokes.indices.reversed()) {
            val s = strokes[i]
            if (s !in sSet || s.color == color || s.tool == Tool.ERASER) continue
            val n = Stroke(s.tool, color, s.width, s.xs, s.ys, s.ps).also { it.group = s.group; it.z = s.z; it.masks = s.masks }
            strokes[i] = n
            rs.add(i to s); ns.add(n); nsAt.add(i)
        }
        val rt = ArrayList<Pair<Int, TextItem>>()
        val nt = ArrayList<TextItem>()
        val ntAt = ArrayList<Int>()
        for (i in texts.indices.reversed()) {
            val t = texts[i]
            if (t !in tSet || t.color == color) continue
            val n = t.withColor(color)
            texts[i] = n
            rt.add(i to t); nt.add(n); ntAt.add(i)
        }
        // Selotip hafalan yang terpilih ikut berganti warna.
        val ri = ArrayList<Pair<Int, PageItem>>()
        val ni = ArrayList<PageItem>()
        val niAt = ArrayList<Int>()
        for (i in items.indices.reversed()) {
            val it = items[i]
            if (it !is TapeItem || it !in selTapes || it.color == color) continue
            val n = it.withColor(color)
            items[i] = n
            ri.add(i to it); ni.add(n); niAt.add(i)
        }
        if (rs.isEmpty() && rt.isEmpty() && ri.isEmpty()) return
        pushUndo(
            Change(
                removedStrokes = rs, addedStrokes = ns, addedStrokesAt = nsAt, removedTexts = rt, addedTexts = nt, addedTextsAt = ntAt,
                removedItems = ri, addedItems = ni, addedItemsAt = niAt,
            )
        )
        contentChanged()
        val swapS = HashMap<Stroke, Stroke>()
        rs.forEachIndexed { k, (_, old) -> swapS[old] = ns[k] }
        val swapT = HashMap<TextItem, TextItem>()
        rt.forEachIndexed { k, (_, old) -> swapT[old] = nt[k] }
        val swapI = HashMap<PageItem, PageItem>()
        ri.forEachIndexed { k, (_, old) -> swapI[old] = ni[k] }
        selectObjects(selItems.map { swapI[it] ?: it }, selTexts.map { swapT[it] ?: it }, selStrokes.map { swapS[it] ?: it }, expand = false)
    }

    private fun transformStroke(s: Stroke, m: Matrix, widthScale: Float): Stroke {
        val pts = FloatArray(s.size * 2)
        for (i in 0 until s.size) {
            pts[i * 2] = s.xs[i]
            pts[i * 2 + 1] = s.ys[i]
        }
        m.mapPoints(pts)
        val xs = FloatArray(s.size) { pts[it * 2] }
        val ys = FloatArray(s.size) { pts[it * 2 + 1] }
        return Stroke(s.tool, s.color, s.width * widthScale, xs, ys, s.ps.copyOf()).also {
            it.group = s.group
            it.z = s.z
            // Bekas penghapus ikut berpindah, membesar, dan berputar bersama goresannya.
            it.masks = s.masks?.map { k -> transformMask(k, m, widthScale) }
        }
    }

    private fun transformMask(k: Stroke, m: Matrix, widthScale: Float): Stroke {
        val pts = FloatArray(k.size * 2)
        for (i in 0 until k.size) { pts[i * 2] = k.xs[i]; pts[i * 2 + 1] = k.ys[i] }
        m.mapPoints(pts)
        return Stroke(Tool.ERASER, k.color, k.width * widthScale, FloatArray(k.size) { pts[it * 2] }, FloatArray(k.size) { pts[it * 2 + 1] }, k.ps)
    }

    private fun openLinkAt(vx: Float, vy: Float) {
        val pp = toPage(vx, vy)
        val hit = items.lastOrNull { it is LinkItem && pp.x >= it.x && pp.x <= it.x + it.w && pp.y >= it.y && pp.y <= it.y + it.h }
        if (hit is LinkItem) onOpenLink?.invoke(hit.url)
    }

    // ---- penggaris: geser dan putar ----

    private fun startRuler(e: MotionEvent, index: Int = 0) {
        mode = Mode.RULER
        activePointer = e.getPointerId(index)
        activeIsFinger = true
        rulerTwoFinger = false
        downX = e.getX(index)
        downY = e.getY(index)
        rulerStartCx = rulerCx
        rulerStartCy = rulerCy
        rulerStartAngle = rulerAngle
    }

    private fun startRulerRotate(e: MotionEvent) {
        if (!measureFocus(e, skip = -1)) return
        rulerTwoFinger = true
        rulerStartCx = rulerCx
        rulerStartCy = rulerCy
        rulerStartAngle = rulerAngle
        startFx = focusX
        startFy = focusY
        rulerStartVecAngle = pointerVecAngle(e)
    }

    private fun pointerVecAngle(e: MotionEvent): Float =
        atan2(e.getY(1) - e.getY(0), e.getX(1) - e.getX(0))

    private fun rulerMove(e: MotionEvent) {
        if (rulerTwoFinger && e.pointerCount >= 2) {
            if (!measureFocus(e, skip = -1)) return
            rulerCx = rulerStartCx + (focusX - startFx)
            rulerCy = rulerStartCy + (focusY - startFy)
            var a = rulerStartAngle + (pointerVecAngle(e) - rulerStartVecAngle)
            // Menempel ke sudut kelipatan 15° bila sudah dekat.
            val deg = Math.toDegrees(a.toDouble())
            val nearest = Math.round(deg / 15.0) * 15.0
            if (abs(deg - nearest) < 2.0) a = Math.toRadians(nearest).toFloat()
            rulerAngle = a
        } else {
            val i = e.findPointerIndex(activePointer)
            if (i < 0) return
            rulerCx = rulerStartCx + (e.getX(i) - downX)
            rulerCy = rulerStartCy + (e.getY(i) - downY)
        }
        invalidate()
    }

    // ---- membalik halaman ----

    /**
     * Siapkan animasi membalik halaman: tampilan halaman sekarang disimpan, lalu setelah halaman baru
     * dipasang ([setPage]) halaman lama terangkat dan terbalik ke kiri (maju), atau halaman sebelumnya
     * datang dari kiri menutupnya (mundur), seperti membuka lembaran buku.
     */
    fun beginPageTurn(dir: Int) {
        val p = page ?: return
        if (gestureActive || width == 0 || height == 0 || rot != 0f) return
        settleNow()
        if (!cacheValid || viewStale) blockingImages { renderCache() }
        val c = cache ?: return
        if (!cacheValid) return
        endPageTurn()
        turnSnap = runCatching { c.copy(Bitmap.Config.ARGB_8888, false) }.getOrNull() ?: return
        turnRect.set(0f, 0f, p.width, p.height)
        viewMatrix.mapRect(turnRect)
        turnDir = dir
        turnArmed = false
        turnStart = 0L
        turnAsked = SystemClock.uptimeMillis()
        turnPaper = p.paperColor
        // Maju: lembaran yang dibalik adalah halaman lama, jadi disiapkan sekarang.
        if (canCurl && dir > 0) makeCurlSheet(c, turnRect, p.paperColor)
    }

    private fun endPageTurn() {
        turnSnap?.recycle()
        turnSnap = null
        turnArmed = false
        // Tidak di-recycle: bisa jadi masih dipakai bingkai yang sedang digambar.
        curlSheet = null
        curlFrontPaint.shader = null
        curlBackPaint.shader = null
        curlFrontTex = null
        curlBackTex = null
        curlTried = false
        invalidate()
    }

    // ---- membalik halaman mengikuti jari ----

    /** Isi halaman sebelah ([dir] +1 berikutnya, -1 sebelumnya), bila sudah dimuat. */
    var peekPage: ((Int) -> Pair<PageMeta, PageContent>?)? = null
    /** Lembaran selesai dibalik dengan jari: ganti ke halaman sebelah tanpa animasi lagi. */
    var onTurnCommit: ((Int) -> Unit)? = null

    private class TurnDrag(
        val dir: Int,
        /** Halaman sebelah yang sudah digambar seukuran layar (cara lama), atau null. */
        val peek: Bitmap?,
        /** Atau: gambar halaman sebelah yang disiapkan di latar, dan matriks dari pikselnya ke layar. */
        val sheet: Bitmap?,
        val sheetMatrix: Matrix?,
        val peekRect: RectF,
        val peekPaper: Int,
    ) {
        /** Yang tampil, dan yang dituju jari (0 datar .. 1 terbalik). Awal membalik menyusul jari dengan cepat. */
        var target = 0f
        var catching = true
        var lastFrame = 0L
        var e = 0f
        var tilt = 0f
        var lastX = 0f
        var lastT = 0L
        var vx = 0f
        var animating = false
        var animFrom = 0f
        var animTo = 0f
        var animStart = 0L
        var animDur = 1L
        var commit = false
        var awaiting = false
        var awaitSince = 0L
    }

    private var turnDrag: TurnDrag? = null

    /** Mulai membalik halaman dengan jari bila halaman tidak bisa digeser lagi ke arah itu. */
    private fun tryStartTurn(e: MotionEvent, i: Int): Boolean {
        if (!canCurl || turnDrag != null || turnSnap != null) return false
        val p = page ?: return false
        val x = e.getX(i)
        val y = e.getY(i)
        val dx = x - downX
        val dy = y - downY
        if (abs(dx) < abs(dy) * 1.4f || abs(scale / startScale - 1f) > 0.08f) return false
        // Halaman yang sedang diputar tidak dibalik dengan sapuan (geser saja).
        if (rot != 0f) return false
        val dir = if (dx < 0f) 1 else -1
        pageBox(clampBox)
        val rx = normalRange(clampBox.width(), width)
        val lo = min(rx[0], panKeepLeft)
        val hi = max(rx[1], panKeepLeft)
        val atEdge = (dir > 0 && clampBox.left <= lo + 0.5f) || (dir < 0 && clampBox.left >= hi - 0.5f)
        if (!atEdge) return false
        // Seberapa jauh jari melewati batas geser halaman.
        val over = (startTx + dx) - tx
        if (abs(over) < touchSlop) return false
        val (meta, content) = peekPage?.invoke(dir) ?: return false
        val nb = neighborSheets[meta.id]?.takeIf { it.meta == meta && it.sig == contentSig(content) }
        if (nb != null && (dir < 0 || backdropFresh(p))) {
            // Cepat: halaman sebelah dan halaman ini sudah tergambar di latar; tidak ada yang digambar ulang di sini.
            gestureActive = false
            transformDirty = false
            if (cacheMatrix != viewMatrix && cacheValid && !viewStale) {
                if (!requestViewRender()) blockingImages { renderCache() }
            }
            val fit = peekFit(meta)
            val nbToScreen = Matrix()
            sheetToPage(nb.bmp, meta, nbToScreen)
            nbToScreen.postScale(fit[0], fit[0])
            nbToScreen.postTranslate(fit[1], fit[2])
            val peekRect = RectF(0f, 0f, meta.width, meta.height)
            nbToScreen.mapRect(peekRect, RectF(1f, 1f, nb.bmp.width - 1f, nb.bmp.height - 1f))
            if (dir > 0) {
                val bd = backdrop ?: return false
                val curToScreen = Matrix()
                sheetToPage(bd, p, curToScreen)
                curToScreen.postConcat(viewMatrix)
                useCurlSheet(bd, curToScreen, p.paperColor)
            } else {
                useCurlSheet(nb.bmp, nbToScreen, meta.paperColor)
            }
            turnDrag = TurnDrag(dir, null, nb.bmp, nbToScreen, peekRect, meta.paperColor)
        } else {
            // Halaman sekarang digambar ulang di posisinya sekarang supaya lembarannya tepat.
            gestureActive = false
            transformDirty = false
            blockingImages { renderCache() }
            val cur = cache ?: return false
            val peekRect = RectF()
            val peek = runCatching { renderPeek(meta, content, peekRect) }.getOrNull() ?: return false
            val pageRect = RectF(0f, 0f, p.width, p.height).also { viewMatrix.mapRect(it) }
            val ok = if (dir > 0) makeCurlSheet(cur, pageRect, p.paperColor) else makeCurlSheet(peek, peekRect, meta.paperColor)
            if (!ok) return false
            turnDrag = TurnDrag(dir, peek, null, null, peekRect, meta.paperColor)
        }
        turnDrag?.let { it.e = if (dir > 0) 0f else 1f; it.target = it.e; it.lastFrame = SystemClock.uptimeMillis() }
        mode = Mode.TURN
        CrashLog.note("balik halaman dengan jari ${if (dir > 0) "maju" else "mundur"}")
        updateTurn(x, y, e.eventTime)
        return true
    }

    /** Pojok lembaran mengikuti jari: sumbu lipatan bergerak setengah jarak jari. */
    private fun updateTurn(x: Float, y: Float, t: Long) {
        val td = turnDrag ?: return
        if (td.animating || td.awaiting) return
        // Puncak gulungan tepat di bawah jari, jadi lembaran langsung mengikuti jari ke mana pun digeser.
        val sheet = curlSheetRect
        val radius = curlRadius()
        val span = sheet.width() + Math.PI.toFloat() * radius
        td.target = if (span > 1f) ((sheet.right - (x - radius)) / span).coerceIn(0f, 1f) else 0f
        if (!td.catching) td.e = td.target
        val r = curlSheetRect
        if (r.height() > 0f) td.tilt = ((y - r.centerY()) / (r.height() / 2f)).coerceIn(-1f, 1f) * 0.32f
        if (td.lastT > 0L) {
            val dt = (t - td.lastT).coerceAtLeast(1L).toFloat()
            td.vx = td.vx * 0.6f + 0.4f * ((x - td.lastX) / dt)
        }
        td.lastX = x
        td.lastT = t
        invalidate()
    }

    /** Jari dilepas: lanjutkan membalik bila sudah cukup jauh atau disentak, selain itu kembalikan. */
    private fun releaseTurn(commit: Boolean) {
        val td = turnDrag ?: return
        if (td.animating || td.awaiting) return
        td.catching = false
        val progress = if (td.dir > 0) td.e else 1f - td.e
        // Kecepatan ke arah membalik (piksel per milidetik).
        val fling = -td.vx * td.dir
        val complete = commit && ((progress > 0.14f && fling > -0.3f) || fling > 0.6f)
        td.commit = complete
        td.animFrom = td.e
        td.animTo = if (complete == (td.dir > 0)) 1f else 0f
        td.animStart = SystemClock.uptimeMillis()
        td.animDur = (150f + 450f * abs(td.animTo - td.animFrom)).toLong()
        td.animating = true
        invalidate()
    }

    private fun drawTurnDrag(canvas: Canvas, td: TurnDrag, p: PageMeta) {
        val now = SystemClock.uptimeMillis()
        if (td.animating) {
            val k = ((now - td.animStart).toFloat() / td.animDur).coerceIn(0f, 1f)
            val ease = 1f - (1f - k) * (1f - k) * (1f - k)
            td.e = td.animFrom + (td.animTo - td.animFrom) * ease
            if (k >= 1f) {
                td.animating = false
                if (td.commit) {
                    td.awaiting = true
                    td.awaitSince = now
                    post { onTurnCommit?.invoke(td.dir) }
                } else {
                    clearTurnDrag()
                    return
                }
            } else {
                postInvalidateOnAnimation()
            }
        }
        if (td.awaiting) {
            // Menunggu halaman baru dipasang: tampilkan halaman tujuan apa adanya.
            drawPeek(canvas, td)
            if (now - td.awaitSince > 1500L) clearTurnDrag() else postInvalidateOnAnimation()
            return
        }
        if (!td.animating && td.catching) {
            // Awal membalik: lembaran menyusul jari dengan cepat dan mulus, lalu menempel pada jari.
            val dt = (now - td.lastFrame).coerceIn(1L, 50L).toFloat()
            val k = 1f - kotlin.math.exp(-dt / 26f)
            td.e += (td.target - td.e) * k
            if (abs(td.target - td.e) < 0.004f) {
                td.e = td.target
                td.catching = false
            } else {
                postInvalidateOnAnimation()
            }
        }
        td.lastFrame = now
        if (td.dir > 0) {
            // Maju: halaman berikutnya di bawah, halaman sekarang terangkat.
            drawPeek(canvas, td)
            drawCurl(canvas, td.e, td.peekRect, td.tilt)
        } else {
            // Mundur: halaman sebelumnya datang menutupi halaman sekarang.
            val under = RectF(0f, 0f, p.width, p.height).also { viewMatrix.mapRect(it) }
            drawCurl(canvas, td.e, under, td.tilt)
        }
    }

    /** Halaman tujuan di tempatnya nanti: dari gambar seukuran layar, atau dari gambar halaman yang disiapkan. */
    private fun drawPeek(canvas: Canvas, td: TurnDrag) {
        td.peek?.let { canvas.drawBitmap(it, 0f, 0f, null); return }
        val bmp = td.sheet ?: return
        val m = td.sheetMatrix ?: return
        canvas.drawColor(deskColor)
        val r = td.peekRect
        val sh = 3f * density
        framePaint.style = Paint.Style.FILL
        framePaint.color = 0x1A000000
        canvas.drawRect(r.left + sh * 0.3f, r.top + sh, r.right + sh * 0.3f, r.bottom + sh, framePaint)
        canvas.drawBitmap(bmp, m, backdropPaint)
        framePaint.style = Paint.Style.STROKE
        framePaint.strokeWidth = 1f
        framePaint.color = 0x22000000
        canvas.drawRect(r, framePaint)
    }

    private fun clearTurnDrag() {
        turnDrag = null
        curlSheet = null
        curlFrontPaint.shader = null
        curlBackPaint.shader = null
        curlFrontTex = null
        curlBackTex = null
        curlTried = false
        invalidate()
    }

    /** Gambar halaman sebelah seukuran layar, persis seperti nanti tampil saat halaman itu dibuka. */
    private fun renderPeek(meta: PageMeta, content: PageContent, out: RectF): Bitmap? {
        if (width <= 0 || height <= 0 || meta.width <= 0f || meta.height <= 0f) return null
        val sc = min((width - margin * 2) / meta.width, (height - margin * 2) / meta.height * 2.2f)
        val pw = meta.width * sc
        val ph = meta.height * sc
        val ptx = (width - pw) / 2f
        val pty = if (ph + margin * 2 <= height) (height - ph) / 2f else margin
        val m = Matrix().apply { setScale(sc, sc); postTranslate(ptx, pty) }
        val bmp = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        c.drawColor(deskColor)
        c.save()
        c.concat(m)
        val sh = 3f * density / sc
        framePaint.style = Paint.Style.FILL
        framePaint.color = 0x1A000000
        c.drawRect(sh * 0.3f, sh, meta.width + sh * 0.3f, meta.height + sh, framePaint)
        blockingImages { InkRenderer.drawPaper(c, meta, renderPaint, imageDir, sc) }
        framePaint.style = Paint.Style.STROKE
        framePaint.strokeWidth = 1f / sc
        framePaint.color = 0x22000000
        c.drawRect(0f, 0f, meta.width, meta.height, framePaint)
        c.clipRect(0f, 0f, meta.width, meta.height)
        blockingImages {
            InkRenderer.drawLayers(c, content.strokes, content.texts, content.items, imageDir, sc, null, itemPaint, renderPaint, renderPath, null, null)
        }
        c.restore()
        out.set(0f, 0f, meta.width, meta.height)
        m.mapRect(out)
        return bmp
    }

    /** Salin bagian halaman [rect] dari [src] (seukuran layar) untuk dilengkungkan. */
    private fun makeCurlSheet(src: Bitmap, rect: RectF, paper: Int): Boolean {
        curlTried = true
        val l = kotlin.math.ceil(max(rect.left, 0f)).toInt()
        val t = kotlin.math.ceil(max(rect.top, 0f)).toInt()
        val r = kotlin.math.floor(min(rect.right, min(width.toFloat(), src.width.toFloat()))).toInt()
        val b = kotlin.math.floor(min(rect.bottom, min(height.toFloat(), src.height.toFloat()))).toInt()
        if (r - l < 16 || b - t < 16) return false
        val bmp = runCatching { Bitmap.createBitmap(r - l + 2, b - t + 2, Bitmap.Config.ARGB_8888) }.getOrNull() ?: return false
        Canvas(bmp).drawBitmap(src, Rect(l, t, r, b), Rect(1, 1, r - l + 1, b - t + 1), null)
        curlSheet = bmp
        curlSheetRect.set(l - 1f, t - 1f, r + 1f, b + 1f)
        curlPaper = paper
        setCurlTexture(android.graphics.BitmapShader(bmp, android.graphics.Shader.TileMode.CLAMP, android.graphics.Shader.TileMode.CLAMP), paper)
        bmp.prepareToDraw()
        return true
    }

    /**
     * Tekstur lembaran: sisi depan apa adanya; punggung = warna kertas 80% di atas isi (isi tembus samar), hanya
     * di bagian yang ada kertasnya (tepi transparan tetap transparan).
     */
    private fun setCurlTexture(tex: android.graphics.Shader, paper: Int) {
        val c = Color.argb(204, Color.red(paper), Color.green(paper), Color.blue(paper))
        val tint = android.graphics.LinearGradient(0f, 0f, 1f, 0f, c, c, android.graphics.Shader.TileMode.CLAMP)
        curlFrontTex = tex
        curlBackTex = android.graphics.ComposeShader(tex, tint, PorterDuff.Mode.SRC_ATOP)
        curlFrontPaint.colorFilter = null
        curlBackPaint.colorFilter = null
    }

    /**
     * Lembaran dari gambar halaman yang sudah disiapkan ([bmp] bertepi 1 piksel; [toScreen] dari pikselnya ke
     * layar), tanpa menyalin apa pun: langsung siap dibalik.
     */
    private fun useCurlSheet(bmp: Bitmap, toScreen: Matrix, paper: Int) {
        curlTried = true
        curlSheetRect.set(0f, 0f, bmp.width.toFloat(), bmp.height.toFloat())
        toScreen.mapRect(curlSheetRect)
        val local = Matrix(toScreen)
        local.postTranslate(-curlSheetRect.left, -curlSheetRect.top)
        val tex = android.graphics.BitmapShader(bmp, android.graphics.Shader.TileMode.CLAMP, android.graphics.Shader.TileMode.CLAMP)
        tex.setLocalMatrix(local)
        curlSheet = bmp
        curlPaper = paper
        setCurlTexture(tex, paper)
    }

    /**
     * Gelap-terang gulungan sebagai gradasi di koordinat gambar lembaran (sejajar sumbu lipatan), digabung
     * dengan teksturnya. Dulu digambar sebagai lapisan warna per titik terpisah, yang di sebagian perangkat
     * kadang ikut tergambar kadang tidak, sehingga punggung kertas tampak berkedip.
     */
    private fun curlShade(): android.graphics.Shader? {
        val a = curl.uStart
        val b = curl.uEnd
        if (b - a < 1f) return null
        val s = curl.foldU
        val half = (Math.PI * curl.rollRadius).toFloat()
        var n = 0
        fun add(u: Float) {
            if (u < a || u > b || n >= shadeU.size) return
            if (n > 0 && u - shadeU[n - 1] < 0.05f) return
            shadeU[n++] = u
        }
        add(a)
        add(s - 0.5f)
        val steps = 44
        for (i in 0..steps) add(s + half * i / steps)
        add(s + half + 0.5f)
        add(b)
        if (n < 2) return null
        for (i in 0 until n) {
            val sh = curl.shadeAt(shadeU[i]).coerceIn(-1f, 1f)
            val al = (kotlin.math.abs(sh) * 255f).roundToInt().coerceIn(0, 255)
            shadeColors[i] = if (sh < 0f) Color.argb(al, 0, 0, 0) else Color.argb(al, 255, 255, 255)
            shadePos[i] = (shadeU[i] - a) / (b - a)
        }
        val c0 = curl.texOffsetU
        val nx = curl.nx
        val ny = curl.ny
        return android.graphics.LinearGradient(
            nx * (a - c0), ny * (a - c0), nx * (b - c0), ny * (b - c0),
            shadeColors.copyOf(n), shadePos.copyOf(n), android.graphics.Shader.TileMode.CLAMP,
        )
    }

    /**
     * Gambar lembaran yang melengkung seperti kertas dibalik. [e] 0 = masih datar, 1 = sudah terbalik.
     * [under] kotak halaman di bawahnya (tempat bayangan lengkungan jatuh).
     */
    private fun curlRadius(): Float = max(16f * density, curlSheetRect.width() * 0.085f)

    private fun drawCurl(canvas: Canvas, e: Float, under: RectF, tilt: Float = 0.36f) {
        if (curlSheet == null) return
        val r = curlSheetRect
        val radius = curlRadius()
        curl.build(
            r.left, r.top, r.right, r.bottom, r.left, r.top, e, radius, tilt,
            width / 2f, height / 2f, 3f * max(width, height),
        )
        val fade = ((1f - e) / 0.22f).coerceIn(0f, 1f)
        val alpha = (fade * 255f).roundToInt()
        val nx = curl.nx
        val ny = curl.ny
        val shade = curlShade()
        val ft = curlFrontTex
        val bt = curlBackTex
        if (ft != null) {
            curlFrontPaint.shader = if (shade != null) android.graphics.ComposeShader(ft, shade, PorterDuff.Mode.SRC_ATOP) else ft
        }
        if (bt != null) {
            curlBackPaint.shader = if (shade != null) android.graphics.ComposeShader(bt, shade, PorterDuff.Mode.SRC_ATOP) else bt
        }
        // Bayangan lengkungan di halaman bawah.
        val sa = 0.38f * curl.lift * fade
        if (sa > 0.005f && curl.shadowTo > curl.shadowFrom) {
            val peak = ((curl.shadowPeak - curl.shadowFrom) / (curl.shadowTo - curl.shadowFrom)).coerceIn(0.01f, 0.99f)
            curlGradPaint.shader = android.graphics.LinearGradient(
                nx * curl.shadowFrom, ny * curl.shadowFrom, nx * curl.shadowTo, ny * curl.shadowTo,
                intArrayOf(Color.TRANSPARENT, Color.argb((sa * 255).roundToInt(), 0, 0, 0), Color.TRANSPARENT),
                floatArrayOf(0f, peak, 1f),
                android.graphics.Shader.TileMode.CLAMP,
            )
            curlClip.set(under)
            if (curlClip.intersect(0f, 0f, width.toFloat(), height.toFloat())) {
                canvas.save()
                canvas.clipRect(curlClip)
                canvas.drawPaint(curlGradPaint)
                canvas.restore()
            }
            curlGradPaint.shader = null
        }
        // Sisi depan: bagian yang masih datar dan gulungan bawah.
        drawCurlPart(canvas, curl.front, curlFrontPaint, alpha, fade)
        // Bayangan punggung kertas yang sudah rebah.
        val be = curl.backEdge
        if (!be.isNaN() && fade > 0.01f) {
            val w = 22f * density
            curlGradPaint.shader = android.graphics.LinearGradient(
                nx * (be - w), ny * (be - w), nx * (be + 0.5f), ny * (be + 0.5f),
                intArrayOf(Color.TRANSPARENT, Color.argb((0.26f * fade * 255).roundToInt(), 0, 0, 0), Color.TRANSPARENT),
                floatArrayOf(0f, w / (w + 0.5f), 1f),
                android.graphics.Shader.TileMode.CLAMP,
            )
            canvas.drawPaint(curlGradPaint)
            curlGradPaint.shader = null
        }
        // Punggung kertas di atasnya.
        drawCurlPart(canvas, curl.back, curlBackPaint, alpha, fade)
    }

    @Suppress("UNUSED_PARAMETER")
    private fun drawCurlPart(canvas: Canvas, part: CurlMesh.Part, paint: Paint, alpha: Int, fade: Float) {
        if (part.indexCount == 0 || alpha <= 0) return
        paint.alpha = alpha
        // Satu kali gambar: tekstur kertas beserta gelap-terangnya (lihat curlShade).
        canvas.drawVertices(
            Canvas.VertexMode.TRIANGLES, part.count * 2, part.verts, 0, part.texs, 0,
            null, 0, part.indices, 0, part.indexCount, paint,
        )
    }

    private fun drawPageTurn(canvas: Canvas, p: PageMeta) {
        val snap = turnSnap ?: return
        val now = SystemClock.uptimeMillis()
        if (!turnArmed) {
            // Halaman baru masih dibaca: tetap tampilkan halaman lama sebentar.
            if (now - turnAsked > 1500L) { endPageTurn(); return }
            canvas.drawBitmap(snap, 0f, 0f, null)
            postInvalidateOnAnimation()
            return
        }
        if (turnStart == 0L) turnStart = now
        turnNow.set(0f, 0f, p.width, p.height)
        viewMatrix.mapRect(turnNow)
        // Mundur: lembaran yang kembali adalah halaman baru, disiapkan begitu halamannya siap.
        if (canCurl && turnDir < 0 && curlSheet == null && !curlTried) {
            val cur = cache
            if (cur != null && cacheValid && !viewStale) makeCurlSheet(cur, turnNow, p.paperColor)
        }
        val curling = canCurl && curlSheet != null
        val raw = ((now - turnStart).toFloat() / (if (curling) curlDuration else turnDuration)).coerceIn(0f, 1f)
        val t = raw * raw * (3f - 2f * raw)
        if (turnDir > 0) {
            // Maju: halaman lama terangkat dari pojok kanan bawah dan menggulung ke kiri, memperlihatkan halaman baru.
            if (curling) drawCurl(canvas, t, turnNow)
            else drawFlip(canvas, snap, turnRect, t * 90f, t, shadowOnUnder = 1f - t)
        } else {
            // Mundur: halaman sebelumnya datang dari kiri dan rebah menutup halaman lama.
            canvas.drawBitmap(snap, 0f, 0f, null)
            if (curling) {
                drawCurl(canvas, 1f - t, turnRect)
            } else {
                val cur = cache
                if (cur != null) drawFlip(canvas, cur, turnNow, (1f - t) * 90f, 1f - t, shadowOnUnder = t)
            }
        }
        if (raw >= 1f) endPageTurn() else postInvalidateOnAnimation()
    }

    /** Gambar [bmp] (seukuran layar) yang terlipat [angle] derajat pada tepi kiri halaman [rect]. */
    private fun drawFlip(canvas: Canvas, bmp: Bitmap, rect: RectF, angle: Float, shade: Float, shadowOnUnder: Float) {
        turnCamera.save()
        turnCamera.setLocation(0f, 0f, -max(width, height) * 1.6f / 72f)
        turnCamera.rotateY(-angle)
        turnCamera.getMatrix(turnMatrix)
        turnCamera.restore()
        val px = rect.left
        val py = rect.centerY()
        turnMatrix.preTranslate(-px, -py)
        turnMatrix.postTranslate(px, py)
        // Bayangan yang jatuh ke halaman di bawahnya, di sebelah kanan tepi lembaran yang terangkat.
        turnPts[0] = rect.right; turnPts[1] = rect.top; turnPts[2] = rect.right; turnPts[3] = rect.bottom
        turnMatrix.mapPoints(turnPts)
        val edge = max(turnPts[0], turnPts[2])
        val reach = 46f * density
        if (shadowOnUnder > 0.01f) {
            turnShade.shader = android.graphics.LinearGradient(
                edge, 0f, edge + reach, 0f,
                Color.argb((70 * shadowOnUnder).toInt().coerceIn(0, 255), 0, 0, 0), Color.TRANSPARENT,
                android.graphics.Shader.TileMode.CLAMP,
            )
            canvas.drawRect(edge, rect.top, min(edge + reach, rect.right + reach), rect.bottom, turnShade)
            turnShade.shader = null
        }
        canvas.save()
        canvas.concat(turnMatrix)
        canvas.clipRect(rect)
        canvas.drawBitmap(bmp, 0f, 0f, turnPaint)
        // Lembaran makin gelap saat makin miring, dengan kilau lembut di dekat lipatan.
        turnShade.color = Color.argb((120 * shade).toInt().coerceIn(0, 255), 0, 0, 0)
        canvas.drawRect(rect, turnShade)
        turnShade.shader = android.graphics.LinearGradient(
            rect.left, 0f, rect.left + rect.width() * 0.18f, 0f,
            Color.argb((60 * shade).toInt().coerceIn(0, 255), 0, 0, 0), Color.TRANSPARENT,
            android.graphics.Shader.TileMode.CLAMP,
        )
        canvas.drawRect(rect.left, rect.top, rect.left + rect.width() * 0.18f, rect.bottom, turnShade)
        turnShade.shader = null
        canvas.restore()
    }

    // ---- geser & perbesar halaman ----

    private fun startPan(e: MotionEvent, i: Int) {
        panTapAny = false
        if (!cacheValid) renderCache()
        pageBox(clampBox)
        panKeepLeft = clampBox.left
        page?.let { if (canCurl && !backdropFresh(it) && builder == null) requestBackdrop() }
        activePointer = e.getPointerId(i)
        activeIsFinger = e.getToolType(i) == MotionEvent.TOOL_TYPE_FINGER
        downX = e.getX(i)
        downY = e.getY(i)
        panLastX = downX
        panLastY = downY
        panStartTime = SystemClock.uptimeMillis()
        startTx = tx
        startTy = ty
        startScale = scale
        panMoved = false
        gestureActive = true
        mode = Mode.PAN
    }

    private fun startZoom(e: MotionEvent, skip: Int) {
        if (!measureFocus(e, skip)) return
        if (!cacheValid) renderCache()
        startScale = scale
        startTx = tx
        startTy = ty
        startFx = focusX
        startFy = focusY
        startDist = max(focusDist, 1f)
        startRot = rot
        startAngle = focusAngle
        rotEngaged = false
        zoomPt[0] = focusX
        zoomPt[1] = focusY
        inverseMatrix.mapPoints(zoomPt)
        zoomPageX = zoomPt[0]
        zoomPageY = zoomPt[1]
        panStartTime = SystemClock.uptimeMillis()
        gestureActive = true
        mode = Mode.ZOOM
    }

    /** Titik tengah dan jarak dua jari pertama (selain [skip]). */
    private fun measureFocus(e: MotionEvent, skip: Int): Boolean {
        var a = -1
        var b = -1
        for (i in 0 until e.pointerCount) {
            if (i == skip) continue
            if (a < 0) a = i else if (b < 0) { b = i; break }
        }
        if (a < 0 || b < 0) return false
        val ax = e.getX(a); val ay = e.getY(a)
        val bx = e.getX(b); val by = e.getY(b)
        focusX = (ax + bx) / 2f
        focusY = (ay + by) / 2f
        focusDist = hypot(ax - bx, ay - by)
        focusAngle = Math.toDegrees(atan2((by - ay).toDouble(), (bx - ax).toDouble())).toFloat()
        return true
    }

    private var focusAngle = 0f
    private var startAngle = 0f
    private var startRot = 0f
    private var rotEngaged = false
    /** Halaman boleh diputar dengan dua jari (bisa dimatikan dari pengaturan). */
    var rotAllowed = true
    private var zoomPageX = 0f
    private var zoomPageY = 0f
    private val zoomPt = FloatArray(2)

    /**
     * Sapuan mendatar yang tidak bisa lagi menggeser halaman (sudah di tepi) berarti ganti halaman.
     * Hasil: +1 (berikutnya), -1 (sebelumnya), atau 0. Posisi halaman sekaligus dijepit ke batasnya.
     */
    private fun swipeDirection(pan: Boolean): Int {
        val attemptX = if (pan) panLastX - downX else focusX - startFx
        val attemptY = if (pan) panLastY - downY else focusY - startFy
        val sameZoom = abs(scale / startScale - 1f) < 0.08f && rot == 0f && startRot == 0f
        clamp()
        updateMatrix()
        if (pan && !panMoved) return 0
        val over = attemptX - (tx - startTx)
        val quick = SystemClock.uptimeMillis() - panStartTime < 300L
        val far = abs(over) > max(width * 0.16f, 72f * density) || (quick && abs(over) > 44f * density)
        return if (sameZoom && abs(attemptX) > abs(attemptY) * 1.4f && far) (if (over < 0f) 1 else -1) else 0
    }

    private fun finishPanZoom() {
        gestureActive = false
        snapRotation()
        if (transformDirty) {
            transformDirty = false
            if (requestViewRender()) invalidate() else invalidateViewCache()
            maybeRefreshBackdrop()
        } else {
            invalidate()
        }
    }

    private fun endPanZoom() {
        if (!gestureActive) return
        val dir = swipeDirection(mode == Mode.PAN)
        finishPanZoom()
        if (dir != 0) post { onSwipePage?.invoke(dir) }
    }
}

/** Utas latar untuk menggambar halaman tanpa menahan layar (dipakai bersama oleh semua kanvas). */
private object CanvasWorkers {
    val view: java.util.concurrent.ExecutorService = java.util.concurrent.Executors.newSingleThreadExecutor { r ->
        Thread(r, "hana-layar").apply { isDaemon = true }
    }
    val backdrop: java.util.concurrent.ExecutorService = java.util.concurrent.Executors.newSingleThreadExecutor { r ->
        Thread(r, "hana-alas").apply { isDaemon = true; priority = Thread.NORM_PRIORITY - 1 }
    }
    val main = android.os.Handler(android.os.Looper.getMainLooper())
}
