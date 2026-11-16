package id.goresan.ui

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.RectF
import android.net.Uri
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.content.FileProvider
import id.goresan.Diag
import id.goresan.canvas.CanvasView
import id.goresan.engine.Adj
import id.goresan.engine.Brush
import id.goresan.engine.BrushSetting
import id.goresan.engine.Smoothing
import id.goresan.engine.Buffers
import id.goresan.engine.Curves
import id.goresan.engine.DpiOutputStream
import id.goresan.engine.Gradients
import id.goresan.engine.SelMask
import id.goresan.engine.DrawingGuide
import id.goresan.engine.Paper
import id.goresan.engine.PaperImport
import id.goresan.engine.Engine
import id.goresan.engine.IRect
import id.goresan.engine.TextRender
import id.goresan.engine.TextSpec
import id.goresan.engine.LayerInfo
import id.goresan.engine.LayersSnapshot
import id.goresan.engine.ColorProfile
import id.goresan.engine.LayerProps
import id.goresan.engine.ModelLoader
import id.goresan.engine.Cam3D
import id.goresan.engine.Storage
import id.goresan.engine.ToolKind
import id.goresan.engine.ToolState
import java.io.File
import java.io.FileInputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.Executors
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt

enum class Panel { NONE, BRUSHES, LAYERS, COLOR, ACTIONS, ADJUST }

const val SEL_AUTO = 0
const val SEL_FREE = 1
const val SEL_RECT = 2
const val SEL_ELLIPSE = 3

class Loupe(val x: Float, val y: Float, val color: Int)

/** Seluruh keadaan antarmuka. Hidup di thread utama; perintah ke kanvas dikirim lewat [gl]. */
class AppState(val app: Context) : Engine.Listener {
    val prefs = Prefs(app)
    val brushes = BrushStore(app)
    private val main = Handler(Looper.getMainLooper())
    private val bg = Executors.newSingleThreadExecutor { r -> Thread(r, "goresan-kerja") }

    // Layar
    var openDocId by mutableStateOf<String?>(null)
    /** Dibuka dari beranda WInote: menutup karya (atau batal membuat kanvas) kembali ke beranda. */
    var exitOnClose = false
    /** Langsung tampilkan pilihan kanvas baru saat galeri muncul. */
    var askNew = false
    var onExit: (() -> Unit)? = null
    var galleryVersion by mutableIntStateOf(0)
    var canvasView: CanvasView? = null

    // Dokumen
    var docName by mutableStateOf("")
    var docW by mutableIntStateOf(0)
    /** DPI karya yang terbuka (untuk ukuran cetak dan ekspor). */
    var docDpi by mutableIntStateOf(300)
    var docH by mutableIntStateOf(0)
    var layers by mutableStateOf<List<LayerInfo>>(emptyList())
    /** Mode "Pilih" di panel layer dan layer-layer yang dipilih (bisa dipindah/ditransformasi bersama). */
    var layerSelecting by mutableStateOf(false)
    val layerSelection = androidx.compose.runtime.mutableStateListOf<Int>()
    var groups by mutableStateOf<Map<Int, id.goresan.engine.GroupData>>(emptyMap())
    var activeLayerId by mutableIntStateOf(-1)
    var canUndo by mutableStateOf(false)
    var canRedo by mutableStateOf(false)
    var layerLimit by mutableIntStateOf(0)
    var bgColor by mutableIntStateOf(Color.WHITE)
    var bgVisible by mutableStateOf(true)
    val thumbs = mutableStateMapOf<Int, ImageBitmap>()
    var loading by mutableStateOf(false)
    var viewZoom by mutableFloatStateOf(1f)

    // Alat
    var tool by mutableIntStateOf(ToolKind.PAINT)
    var paintBrushId by mutableStateOf(prefs.getString("paintBrush", "pensil6b"))
    var smudgeBrushId by mutableStateOf(prefs.getString("smudgeBrush", "airlembut"))
    var eraseBrushId by mutableStateOf(prefs.getString("eraseBrush", "bulatkeras"))
    val sizes = mutableStateMapOf<String, Float>().apply { putAll(prefs.getFloatMap("sizes")) }
    val opacities = mutableStateMapOf<String, Float>().apply { putAll(prefs.getFloatMap("opacities")) }

    // Warna
    var hue by mutableFloatStateOf(0f)
    var sat by mutableFloatStateOf(0f)
    var value by mutableFloatStateOf(0f)
    var prevColor by mutableIntStateOf(Color.BLACK)
    /** Warna kedua: dipakai dinamika warna kuas (getaran/tekanan/kemiringan menuju warna kedua). */
    var color2 by mutableIntStateOf(prefs.getInt("color2", Color.WHITE))
    val history = mutableStateListOf<Int>().apply { addAll(prefs.getIntList("history")) }
    val palette = mutableStateListOf<Int>()

    // Panel & mode
    var panel by mutableStateOf(Panel.NONE)
    var studioBrushId by mutableStateOf<String?>(null)
    var uiHidden by mutableStateOf(false)
    var pickArmed by mutableStateOf(false)
    var loupe by mutableStateOf<Loupe?>(null)
    private var pickColor: Int? = null
    var fillActive by mutableStateOf(false)
    var fillThreshold by mutableFloatStateOf(prefs.fillThreshold)
    var fillRefAll by mutableStateOf(false)
    var xfActive by mutableStateOf(false)
    /** Keadaan transformasi yang sedang terbuka (null sebelum mesin mengirim kotak isinya). */
    private var xfModel: XfModel? = null
    /** Cara transformasi: Bebas, Seragam, Distorsi, Warp (XfModel.*). */
    var xfMode by mutableIntStateOf(XfModel.UNIFORM)
    var xfSnap by mutableStateOf(prefs.getBool("xfSnap", false))
    var xfInterp by mutableIntStateOf(prefs.getInt("xfInterp", XfModel.BILINEAR))
    private var xfCount = 1
    private var xfSoft = false
    // Pilihan (Selection)
    var selTool by mutableStateOf(false)
    var selMode by mutableIntStateOf(SEL_FREE)
    var selOpMode by mutableIntStateOf(SelMask.OP_ADD)
    var selActive by mutableStateOf(false)
    var selSaved by mutableIntStateOf(0)
    var selFeatherPx by mutableFloatStateOf(0f)
    /** Panel tambahan di bilah pilihan: 0 tidak ada, 1 Perhalus, 2 Simpan & Muat. */
    var selPanel by mutableIntStateOf(0)
    var selThreshold by mutableFloatStateOf(prefs.getInt("selThr", 30) / 100f)
    private val selPath = ArrayList<Float>()
    private var selPoly = false
    private var selX0 = 0f
    private var selY0 = 0f
    private var selThrStart = 0.3f

    var adjustKind by mutableIntStateOf(-1)
    /** Nilai penggeser penyesuaian yang sedang terbuka (arti tiap indeks: lihat adjParams). */
    val adjV = mutableStateListOf<Float>()
    /** Sub-mode penyesuaian (jenis noise/glitch/halftone, preset gradien, mode liquify, dst.). */
    var adjMode by mutableIntStateOf(0)
    /** Tab tampilan saja: rentang Color Balance atau kanal Curves. */
    var adjTab by mutableIntStateOf(0)
    var adjCx by mutableFloatStateOf(0f)
    var adjCy by mutableFloatStateOf(0f)
    /** Curves: titik kendali per kanal (0 gamma, 1 merah, 2 hijau, 3 biru), x/y 0..1 (y ke atas). */
    val curvePts = List(4) { mutableStateListOf<Offset>() }
    /** Clone: ketukan berikutnya di kanvas memindah sumber. */
    var cloneArmed by mutableStateOf(false)
    /** Mode Pena penyesuaian: efek hanya di bagian yang disapukan pena (seperti mode Pencil Procreate). */
    var adjPen by mutableStateOf(false)
    /** Mode Pena: sapuan menghapus efek (bukan menambah). */
    var adjPenErase by mutableStateOf(false)
    var penUndo by mutableIntStateOf(0)
    var penRedo by mutableIntStateOf(0)
    /** Mode Pena sedang dipakai (Liquify dan Klon punya sapuan sendiri). */
    val adjPenActive: Boolean get() = adjPen && adjustKind >= 0 && adjustKind != Adj.LIQUIFY && adjustKind != Adj.CLONE
    /**
     * Ukuran (0..1, skala kuadrat sampai sepertiga sisi kanvas) dan kekuatan sapuan mode Pena. Diatur dengan penggeser
     * samping selama mode Pena dipakai — terpisah dari ukuran kuas, karena sapuan efek biasanya jauh lebih lebar.
     */
    var penSizeV by mutableFloatStateOf(prefs.getInt("penSize", 450) / 1000f)
    var penOpV by mutableFloatStateOf(prefs.getInt("penOp", 1000) / 1000f)
    var flash by mutableStateOf<String?>(null)
    var flashId by mutableIntStateOf(0)
    var busy by mutableStateOf<String?>(null)

    // ---- Pangkas & ubah ukuran, info kanvas, jendela referensi ----
    var cropOpen by mutableStateOf(false)
    var cropSnap by mutableStateOf<Bitmap?>(null)
    var infoOpen by mutableStateOf(false)
    var info by mutableStateOf<id.goresan.engine.CanvasInfo?>(null)
    var refOn by mutableStateOf(false)
    /** 0 = kanvas ini (diperbarui setelah menggores), 1 = gambar sendiri. */
    var refMode by mutableIntStateOf(0)
    var refCanvas by mutableStateOf<Bitmap?>(null)
    var refImage by mutableStateOf<Bitmap?>(null)
    var pickRefImage: (() -> Unit)? = null
    private val refRunnable = Runnable { refreshRef() }

    // ---- Face Paint ----
    var isFace by mutableStateOf(false)
    /** Tampilan Face Paint (kamera depan + tekstur di wajah); false = tekstur 2D. */
    var faceView by mutableStateOf(false)
    /** Tekstur wajah terbaru (gabungan kanvas, sisi terpanjang 1024). */
    var faceTex by mutableStateOf<Bitmap?>(null)
    /** Ambil foto Face Paint (dipasang oleh tampilan kamera). */
    var facePhoto: (() -> Unit)? = null
    /** Kanvas berubah sejak tekstur wajah terakhir diambil. */
    @Volatile var faceDirty = false

    override fun onPenMode(on: Boolean, undo: Int, redo: Int) {
        if (adjustKind < 0) return
        adjPen = on
        penUndo = undo
        penRedo = redo
    }

    override fun onFace(face: Boolean) {
        isFace = face
        faceView = face
        faceDirty = true
    }

    fun switchFaceView(on: Boolean) {
        if (!isFace) return
        faceView = on
        faceDirty = true
    }

    private var faceBusy = false

    /** Ambil tekstur wajah dari kanvas bila berubah (dipanggil berkala oleh tampilan Face Paint). */
    fun refreshFaceTex() {
        if (!faceDirty || faceBusy || docW <= 0) return
        faceDirty = false
        faceBusy = true
        gl { e -> e.snapshot(1024) { b -> faceTex = b; faceBusy = false } }
    }

    /** Karya Face Paint baru: tekstur 1024 × 1024 transparan, layer "Peta wajah" (tersembunyi) dan "Cat". */
    fun createFacePaint() {
        busy = "Menyiapkan Face Paint…"
        bg.execute {
            val res = runCatching {
                val id = Storage.create(app, "Face Paint", 1024, 1024, Color.WHITE, false, 300)
                val dir = Storage.dir(app, id)
                val meta = Storage.readMeta(dir) ?: error("Karya gagal dibuat")
                meta.put("face", true)
                val map = LayerProps("Peta wajah", false, 0.6f, 0, false, false, true)
                val paint = LayerProps("Cat", true, 1f, 0, false, false, false)
                meta.put("layers", org.json.JSONArray().put(Storage.layerJson(1, map)).put(Storage.layerJson(2, paint)))
                meta.put("active", 2)
                meta.put("nextId", 3)
                val bmp = app.assets.open("wajah/peta-wajah.png").use { BitmapFactory.decodeStream(it) }
                val sc = if (bmp.width == 1024 && bmp.height == 1024) bmp else Bitmap.createScaledBitmap(bmp, 1024, 1024, true)
                val buf = java.nio.ByteBuffer.allocateDirect(1024 * 1024 * 4)
                sc.copyPixelsToBuffer(buf)
                buf.position(0)
                Storage.writeLayer(dir, 1, 1024, 1024, buf)
                Storage.writeMeta(dir, meta)
                id
            }
            main.post {
                busy = null
                res.onSuccess { id ->
                    galleryVersion++
                    openArtwork(id)
                    showFlash("Lukis langsung di wajah Anda; tampilkan layer Peta wajah untuk panduan di tekstur 2D")
                }.onFailure { showFlash("Face Paint gagal disiapkan: ${it.message}") }
            }
        }
    }

    /** Simpan foto Face Paint (kamera + cat) ke Galeri › Pictures/WInote. */
    fun saveFacePhoto(bmp: Bitmap) {
        busy = "Menyimpan foto…"
        bg.execute {
            val ok = runCatching {
                val values = ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, "${safeName()}-wajah-${stamp()}.png")
                    put(MediaStore.MediaColumns.MIME_TYPE, "image/png")
                    put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/WInote")
                    put(MediaStore.MediaColumns.IS_PENDING, 1)
                }
                val resolver = app.contentResolver
                val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values) ?: error("Galeri menolak")
                resolver.openOutputStream(uri)?.use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
                values.clear()
                values.put(MediaStore.MediaColumns.IS_PENDING, 0)
                resolver.update(uri, values, null, null)
            }.isSuccess
            bmp.recycle()
            main.post {
                busy = null
                showFlash(if (ok) "Foto tersimpan di Galeri › Pictures/WInote" else "Gagal menyimpan foto")
            }
        }
    }

    // ---- model 3D ----
    var is3D by mutableStateOf(false)
    var view3D by mutableStateOf(false)
    var modelTris by mutableIntStateOf(0)

    override fun onModel(tris: Int, view3D: Boolean) {
        is3D = tris > 0
        modelTris = tris
        this.view3D = view3D
    }

    /** Tampilan 3D (lukis di model) atau tampilan tekstur 2D. */
    fun switchView3D(on: Boolean) {
        if (!is3D || on == view3D) return
        view3D = on
        gl { it.switchView3D(on) }
    }

    fun reset3D() {
        val v = canvasView ?: return
        v.engine.cam3D = Cam3D()
        v.requestRender()
    }

    private fun nameOf(u: Uri): String {
        val n = runCatching {
            app.contentResolver.query(u, arrayOf(android.provider.OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
                if (c.moveToFirst()) c.getString(0) else null
            }
        }.getOrNull()
        return n ?: u.lastPathSegment?.substringAfterLast('/') ?: "model"
    }

    private fun readUri(u: Uri, limit: Long = 512L shl 20): ByteArray =
        app.contentResolver.openInputStream(u)?.use { inp ->
            val out = java.io.ByteArrayOutputStream()
            val buf = ByteArray(1 shl 16)
            var total = 0L
            while (true) {
                val n = inp.read(buf)
                if (n < 0) break
                total += n
                if (total > limit) error("Berkas terlalu besar")
                out.write(buf, 0, n)
            }
            out.toByteArray()
        } ?: error("Berkas tidak bisa dibaca")

    /**
     * Buat karya dari model 3D: GLB, atau OBJ (+ MTL + gambar tekstur bila ikut dipilih). Tekstur bawaan jadi layer
     * "Tekstur", di atasnya layer "Cat" untuk melukis; ukuran kanvas = ukuran tekstur (maks 4096).
     */
    fun import3D(uris: List<Uri>) {
        if (uris.isEmpty()) return
        busy = "Membaca model 3D…"
        bg.execute {
            var auto = false
            val res = runCatching {
                val files = uris.map { nameOf(it) to it }
                fun ext(n: String) = n.substringAfterLast('.', "").lowercase()
                val model = files.firstOrNull { ext(it.first) == "glb" } ?: files.firstOrNull { ext(it.first) == "obj" }
                    ?: error(
                        if (files.any { ext(it.first) == "usdz" || ext(it.first) == "gltf" }) "USDZ dan glTF terpisah belum didukung; ubah dulu ke GLB atau OBJ"
                        else "Pilih berkas .obj atau .glb",
                    )
                val glb = ext(model.first) == "glb"
                val bytes = readUri(model.second)
                val imp = if (glb) {
                    ModelLoader.parseGlb(bytes)
                } else {
                    val mtl = files.firstOrNull { ext(it.first) == "mtl" }?.let { String(readUri(it.second), Charsets.UTF_8) }
                    ModelLoader.parseObj(String(bytes, Charsets.UTF_8), mtl)
                }
                auto = imp.autoUv
                var texBytes = imp.texture
                if (texBytes == null) {
                    val imgs = files.filter { ext(it.first) in setOf("png", "jpg", "jpeg", "webp") }
                    val pick = imp.textureName?.let { n -> imgs.firstOrNull { it.first.equals(n, ignoreCase = true) } } ?: imgs.firstOrNull()
                    texBytes = pick?.let { readUri(it.second, 128L shl 20) }
                }
                val tex = texBytes?.let { BitmapFactory.decodeByteArray(it, 0, it.size) }
                var tw = 2048
                var th = 2048
                if (tex != null) {
                    val k = minOf(1f, 4096f / maxOf(tex.width, tex.height))
                    tw = maxOf(64, (tex.width * k).toInt())
                    th = maxOf(64, (tex.height * k).toInt())
                }
                val name = model.first.substringBeforeLast('.').ifBlank { "Model 3D" }
                val bgc = imp.baseColor ?: Color.WHITE
                val id = Storage.create(app, name, tw, th, bgc, true, 300)
                val dir = Storage.dir(app, id)
                imp.mesh.write(File(dir, "model.bin"))
                val meta = Storage.readMeta(dir) ?: error("Karya gagal dibuat")
                meta.put("model3d", true)
                if (tex != null) {
                    val p = LayerProps("Tekstur", true, 1f, 0, false, false, false)
                    val q = LayerProps("Cat", true, 1f, 0, false, false, false)
                    meta.put("layers", org.json.JSONArray().put(Storage.layerJson(1, p)).put(Storage.layerJson(2, q)))
                    meta.put("active", 2)
                    meta.put("nextId", 3)
                    val sc = if (tex.width == tw && tex.height == th && tex.config == Bitmap.Config.ARGB_8888) tex
                    else Bitmap.createScaledBitmap(tex.copy(Bitmap.Config.ARGB_8888, false), tw, th, true)
                    val buf = java.nio.ByteBuffer.allocateDirect(tw * th * 4)
                    sc.copyPixelsToBuffer(buf)
                    buf.position(0)
                    Storage.writeLayer(dir, 1, tw, th, buf)
                }
                Storage.writeMeta(dir, meta)
                id to imp.mesh.triangles
            }
            main.post {
                busy = null
                res.onSuccess { (id, tris) ->
                    Diag.log("Model 3D diimpor: $tris segitiga")
                    galleryVersion++
                    openArtwork(id)
                    showFlash(
                        if (auto) "Model tanpa peta UV: memakai proyeksi kotak otomatis"
                        else "Lukis langsung di model; geser di luar model untuk memutar",
                    )
                }.onFailure {
                    Diag.log("Impor model 3D gagal: ${it.message}")
                    showFlash("Model 3D gagal dibuka: ${it.message ?: it.javaClass.simpleName}")
                }
            }
        }
    }

    /** Ekspor model + tekstur (gabungan semua layer) sebagai GLB ke Download/WInote. */
    fun exportGlb() {
        if (!is3D || modalActive()) return
        panel = Panel.NONE
        busy = "Menyimpan model 3D…"
        val bgc = if (bgVisible) bgColor else Color.WHITE
        gl { e ->
            val mesh = e.model()
            val bmp = e.exportBitmap(bgc)
            if (mesh == null || bmp == null) {
                main.post { busy = null }
                return@gl
            }
            bg.execute {
                val ok = runCatching {
                    val png = java.io.ByteArrayOutputStream().also { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }.toByteArray()
                    val values = ContentValues().apply {
                        put(MediaStore.MediaColumns.DISPLAY_NAME, "${safeName()}-${stamp()}.glb")
                        put(MediaStore.MediaColumns.MIME_TYPE, "model/gltf-binary")
                        put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/WInote")
                        put(MediaStore.MediaColumns.IS_PENDING, 1)
                    }
                    val resolver = app.contentResolver
                    val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values) ?: error("Folder menolak")
                    resolver.openOutputStream(uri)?.use { out ->
                        val bo = out.buffered(1 shl 16)
                        ModelLoader.writeGlb(bo, mesh, png, docName.ifBlank { "Model" })
                        bo.flush()
                    } ?: error("Tidak bisa menulis")
                    values.clear()
                    values.put(MediaStore.MediaColumns.IS_PENDING, 0)
                    resolver.update(uri, values, null, null)
                }.onFailure { Diag.log("Ekspor GLB gagal: ${it.message}") }.isSuccess
                bmp.recycle()
                main.post {
                    busy = null
                    showFlash(if (ok) "Model GLB tersimpan di Download/WInote" else "Gagal menyimpan model 3D")
                }
            }
        }
    }

    // ---- profil warna ----
    var profile by mutableIntStateOf(ColorProfile.SRGB)
    var profileOpen by mutableStateOf(false)
    /** Layar perangkat mendukung gamut luas (diisi DrawActivity). */
    var wideScreen = false
    /** Permukaan kanvas berikutnya dibuat dalam Display P3. */
    var wantP3 = false
    /** Minta jendela masuk/keluar mode warna gamut luas. */
    var onWideColor: ((Boolean) -> Unit)? = null
    /** Naik untuk membuat ulang kanvas (permukaan GL baru). */
    var canvasGen by mutableIntStateOf(0)

    override fun onProfile(profile: Int) { this.profile = profile }

    fun changeProfile(p: Int) {
        val id = openDocId ?: return
        if (docW <= 0 || modalActive() || p == profile) return
        val from = profile
        busy = "Mengganti profil warna…"
        gl { e ->
            e.setProfile(p) { ok ->
                busy = null
                if (!ok) return@setProfile
                if (ColorProfile.rgbSpace(from) != ColorProfile.rgbSpace(p)) {
                    setColor(ColorProfile.convert(color, from, p))
                }
                info = null
                if (infoOpen) openInfo()
                showFlash("Profil warna: ${ColorProfile.name(p)}")
                val needP3 = p == ColorProfile.P3 && wideScreen
                if (needP3 != wantP3) {
                    // Ruang warna permukaan layar hanya bisa diatur saat dibuat: kanvas dibuka ulang.
                    gl { it.save { canvasGen++; openArtwork(id) } }
                }
            }
        }
    }

    /** Warna sekarang di luar gamut cetak (karya CMYK). */
    fun outOfPrint(c: Int): Boolean =
        profile == ColorProfile.CMYK && runCatching { ColorProfile.outOfGamut(app, c) }.getOrDefault(false)

    fun printable(c: Int): Int = runCatching { ColorProfile.proof(app, c) }.getOrDefault(c)
    fun inks(c: Int): IntArray = runCatching { ColorProfile.separate(app, c) }.getOrDefault(IntArray(4))

    /** Karya CMYK: TIFF CMYK (PackBits, DPI karya) di Download/WInote untuk percetakan. */
    fun exportCmykTiff() {
        if (docW <= 0 || modalActive()) return
        panel = Panel.NONE
        busy = "Membuat TIFF CMYK…"
        val bgc = if (bgVisible) bgColor else Color.WHITE
        val dpi = docDpi
        gl { e ->
            val buf = e.compositePixels(bgc)
            val w = docW
            val h = docH
            if (buf == null) { main.post { busy = null }; return@gl }
            bg.execute {
                val dir = File(app.cacheDir, "ekspor").apply { mkdirs() }
                val file = File(dir, "${safeName()}-cmyk-${stamp()}.tif")
                val saved = runCatching {
                    ColorProfile.writeCmykTiff(app, file, w, h, dpi, buf) { f ->
                        main.post { if (busy != null) busy = "Membuat TIFF CMYK… ${(f * 100).roundToInt()}%" }
                    }
                    val values = ContentValues().apply {
                        put(MediaStore.MediaColumns.DISPLAY_NAME, file.name)
                        put(MediaStore.MediaColumns.MIME_TYPE, "image/tiff")
                        put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/WInote")
                        put(MediaStore.MediaColumns.IS_PENDING, 1)
                    }
                    val resolver = app.contentResolver
                    val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values) ?: error("Folder menolak")
                    resolver.openOutputStream(uri)?.use { out -> FileInputStream(file).use { it.copyTo(out, 256 * 1024) } } ?: error("Tidak bisa menulis")
                    values.clear()
                    values.put(MediaStore.MediaColumns.IS_PENDING, 0)
                    resolver.update(uri, values, null, null)
                }.onFailure { Diag.log("TIFF CMYK gagal: ${it.message}") }.isSuccess
                file.delete()
                main.post {
                    busy = null
                    showFlash(if (saved) "TIFF CMYK tersimpan di Download/WInote" else "Gagal membuat TIFF CMYK")
                }
            }
        }
    }

    // ---- Animation Assist / Page Assist ----
    /** 0 = biasa, 1 = Animation Assist, 2 = Page Assist. */
    var assist by mutableIntStateOf(0)
    var animSet by mutableStateOf(id.goresan.engine.AnimSettings())
    var holds by mutableStateOf<Map<Int, Int>>(emptyMap())
    var animPlaying by mutableStateOf(false)
    var animSettingsOpen by mutableStateOf(false)
    /** Kunci frame yang menu-nya terbuka (tahan, duplikat, hapus). */
    var frameMenu by mutableStateOf<Int?>(null)

    override fun onAnim(assist: Int, s: id.goresan.engine.AnimSettings, holds: Map<Int, Int>, playing: Boolean) {
        this.assist = assist
        animSet = s
        this.holds = holds
        animPlaying = playing
    }

    /** Frame/halaman dari daftar layer (bawah ke atas). */
    fun frames(): List<id.goresan.engine.AnimFrame> =
        id.goresan.engine.Frames.build(layers.map { it.id }, layers.map { it.group }, layers.map { it.clip })

    fun switchAssist(mode: Int) {
        assist = mode
        animPlaying = false
        panel = Panel.NONE
        gl { it.setAssist(mode) }
    }

    fun setAnim(st: id.goresan.engine.AnimSettings) {
        animSet = st
        gl { it.setAnim(st) }
    }

    fun setHold(key: Int, n: Int) = gl { it.setHold(key, n) }
    fun animPlay(on: Boolean) { animPlaying = on; gl { it.animPlay(on) } }
    fun selectFrame(key: Int) = gl { it.selectFrame(key) }
    fun addFrame() = gl { it.addFrame() }
    fun duplicateFrame(key: Int) = gl { if (key < 0) it.duplicateGroup(-key) else it.duplicateLayer(key) }
    fun deleteFrame(key: Int) = gl { if (key < 0) it.deleteGroup(-key) else it.deleteLayer(key) }

    /** Ekspor animasi: GIF (sisi terpanjang 800) atau MP4 (muat 1920×1080). */
    fun exportAnim(gif: Boolean) {
        if (docW <= 0 || modalActive()) return
        panel = Panel.NONE
        animPlay(false)
        busy = "Mengekspor animasi…"
        val bgc = if (bgVisible) bgColor else Color.WHITE
        val st = animSet
        gl { e ->
            val tmp = java.io.File(app.cacheDir, if (gif) "animasi.gif" else "animasi.mp4")
            tmp.delete()
            val ok = runCatching {
                if (gif) {
                    tmp.outputStream().buffered().use { os ->
                        var w: id.goresan.engine.GifWriter? = null
                        val done = e.animExport(800, bgc, 2) { buf, ow, oh, ms ->
                            val gw = w ?: id.goresan.engine.GifWriter(os, ow, oh, st.mode != 2).also { w = it }
                            gw.put(buf, ms)
                        }
                        w?.finish()
                        done && w != null
                    }
                } else {
                    val box = id.goresan.engine.TimeLapse.sizeFor(docW, docH)
                    var vw: id.goresan.engine.VideoWriter? = null
                    val done = e.animExport(maxOf(box.first, box.second), bgc, 16) { buf, ow, oh, ms ->
                        val v = vw ?: id.goresan.engine.VideoWriter(tmp, ow, oh, st.fps).also { vw = it }
                        v.put(buf, maxOf(1, (ms * st.fps + 500) / 1000))
                    }
                    vw?.finish()
                    done && vw != null
                }
            }.getOrDefault(false)
            if (!ok) {
                main.post { busy = null; showFlash("Animasi gagal diekspor") }
                return@gl
            }
            bg.execute {
                val saved = runCatching {
                    val name = "${safeName()}-animasi-${stamp()}." + if (gif) "gif" else "mp4"
                    val values = ContentValues().apply {
                        put(MediaStore.MediaColumns.DISPLAY_NAME, name)
                        put(MediaStore.MediaColumns.MIME_TYPE, if (gif) "image/gif" else "video/mp4")
                        put(MediaStore.MediaColumns.RELATIVE_PATH, (if (gif) Environment.DIRECTORY_PICTURES else Environment.DIRECTORY_MOVIES) + "/WInote")
                        put(MediaStore.MediaColumns.IS_PENDING, 1)
                    }
                    val resolver = app.contentResolver
                    val col = if (gif) MediaStore.Images.Media.EXTERNAL_CONTENT_URI else MediaStore.Video.Media.EXTERNAL_CONTENT_URI
                    val uri = resolver.insert(col, values) ?: error("Galeri menolak")
                    resolver.openOutputStream(uri)?.use { out -> tmp.inputStream().use { it.copyTo(out) } }
                    values.clear()
                    values.put(MediaStore.MediaColumns.IS_PENDING, 0)
                    resolver.update(uri, values, null, null)
                }.isSuccess
                main.post {
                    busy = null
                    showFlash(
                        if (!saved) "Gagal menyimpan animasi"
                        else if (gif) "GIF tersimpan di Galeri › Pictures/WInote" else "Video tersimpan di Galeri › Movies/WInote",
                    )
                }
            }
        }
    }

    /** Page Assist: semua halaman jadi satu PDF di Download/WInote. */
    fun exportPagesPdf() {
        if (docW <= 0 || modalActive()) return
        panel = Panel.NONE
        busy = "Membuat PDF…"
        val bgc = if (bgVisible) bgColor else Color.WHITE
        val k = 72f / docDpi.coerceAtLeast(1)
        gl { e ->
            val pdf = android.graphics.pdf.PdfDocument()
            val ok = runCatching {
                e.pagesExport(bgc) { bmp, i, n ->
                    val pw = maxOf(1, (bmp.width * k).roundToInt())
                    val ph = maxOf(1, (bmp.height * k).roundToInt())
                    val page = pdf.startPage(android.graphics.pdf.PdfDocument.PageInfo.Builder(pw, ph, i + 1).create())
                    page.canvas.drawBitmap(
                        bmp, null, android.graphics.RectF(0f, 0f, pw.toFloat(), ph.toFloat()),
                        android.graphics.Paint(android.graphics.Paint.FILTER_BITMAP_FLAG),
                    )
                    pdf.finishPage(page)
                    bmp.recycle()
                    main.post { busy = "Membuat PDF… halaman ${i + 1} dari $n" }
                }
            }.getOrDefault(false)
            if (!ok) {
                pdf.close()
                main.post { busy = null; showFlash("PDF gagal dibuat") }
                return@gl
            }
            bg.execute {
                val saved = runCatching {
                    val values = ContentValues().apply {
                        put(MediaStore.MediaColumns.DISPLAY_NAME, "${safeName()}-halaman-${stamp()}.pdf")
                        put(MediaStore.MediaColumns.MIME_TYPE, "application/pdf")
                        put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/WInote")
                        put(MediaStore.MediaColumns.IS_PENDING, 1)
                    }
                    val resolver = app.contentResolver
                    val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values) ?: error("Folder menolak")
                    resolver.openOutputStream(uri)?.use { out -> pdf.writeTo(out) } ?: error("Tidak bisa menulis")
                    values.clear()
                    values.put(MediaStore.MediaColumns.IS_PENDING, 0)
                    resolver.update(uri, values, null, null)
                }.isSuccess
                pdf.close()
                main.post {
                    busy = null
                    showFlash(if (saved) "PDF semua halaman tersimpan di Download/WInote" else "Gagal menyimpan PDF")
                }
            }
        }
    }

    // ---- time-lapse ----
    var tlOn by mutableStateOf(true)
    /** Video time-lapse yang sedang diputar ulang (berkas sementara). */
    var tlVideo by mutableStateOf<java.io.File?>(null)

    override fun onTimeLapse(on: Boolean) { tlOn = on }

    fun setTimeLapse(on: Boolean) {
        tlOn = on
        gl { it.setTimeLapse(on) }
    }

    /** Susun video time-lapse (semua bingkai) ke berkas sementara, lalu putar. */
    fun playTimeLapse() {
        if (docW <= 0 || modalActive()) return
        panel = Panel.NONE
        buildTimeLapse(null) { f -> tlVideo = f }
    }

    private fun buildTimeLapse(seconds: Int?, done: (java.io.File) -> Unit) {
        busy = "Menyusun time-lapse…"
        gl { e ->
            e.timeLapseFlush { dir ->
                bg.execute {
                    val out = java.io.File(app.cacheDir, if (seconds == null) "timelapse-penuh.mp4" else "timelapse-${seconds}s.mp4")
                    var last = -1
                    val ok = runCatching {
                        id.goresan.engine.TimeLapse.export(dir, out, seconds, 2) { p ->
                            val pc = (p * 100).toInt()
                            if (pc != last) { last = pc; main.post { busy = "Menyusun time-lapse… $pc%" } }
                        }
                    }.getOrDefault(false)
                    main.post {
                        busy = null
                        if (ok) done(out) else showFlash("Belum ada rekaman time-lapse untuk karya ini")
                    }
                }
            }
        }
    }

    /** Ekspor time-lapse ke Galeri › Movies/WInote. [seconds] = null: lengkap. */
    fun exportTimeLapse(seconds: Int?) {
        if (docW <= 0) return
        buildTimeLapse(seconds) { f ->
            busy = "Menyimpan video…"
            bg.execute {
                val ok = runCatching {
                    val name = "${safeName()}-timelapse-${stamp()}.mp4"
                    val values = ContentValues().apply {
                        put(MediaStore.MediaColumns.DISPLAY_NAME, name)
                        put(MediaStore.MediaColumns.MIME_TYPE, "video/mp4")
                        put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_MOVIES + "/WInote")
                        put(MediaStore.MediaColumns.IS_PENDING, 1)
                    }
                    val resolver = app.contentResolver
                    val uri = resolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, values) ?: error("Galeri menolak")
                    resolver.openOutputStream(uri)?.use { out -> f.inputStream().use { it.copyTo(out) } }
                    values.clear()
                    values.put(MediaStore.MediaColumns.IS_PENDING, 0)
                    resolver.update(uri, values, null, null)
                }.isSuccess
                main.post {
                    busy = null
                    showFlash(if (ok) "Video tersimpan di Galeri › Movies/WInote" else "Gagal menyimpan video")
                }
            }
        }
    }

    fun clearTimeLapse() {
        gl { e -> e.clearTimeLapse { showFlash("Rekaman time-lapse dihapus") } }
    }

    fun openCrop() {
        if (docW <= 0 || modalActive()) return
        if (is3D) {
            showFlash("Kanvas model 3D adalah teksturnya, jadi tidak bisa dipangkas atau diubah ukurannya")
            return
        }
        panel = Panel.NONE
        cropSnap = null
        cropOpen = true
        gl { it.snapshot(1400) { b -> cropSnap = b } }
    }

    /** Terapkan pangkas/ubah ukuran; riwayat urungkan dikosongkan oleh mesin. */
    fun applyCrop(nw: Int, nh: Int, ox: Int, oy: Int, scale: Boolean) {
        busy = "Mengubah ukuran kanvas…"
        gl {
            it.resizeCanvas(nw, nh, ox, oy, scale) { ok ->
                busy = null
                if (ok) {
                    cropOpen = false
                    cropSnap = null
                    showFlash("Kanvas sekarang $nw × $nh px")
                    // Gambar gabungan baru tersusun di bingkai berikutnya.
                    main.removeCallbacks(refRunnable)
                    main.postDelayed(refRunnable, 700)
                }
            }
        }
    }

    fun openInfo() {
        if (docW <= 0) return
        panel = Panel.NONE
        info = null
        infoOpen = true
        gl { it.canvasInfo { i -> info = i } }
    }

    fun toggleRef(on: Boolean) {
        refOn = on
        if (on) {
            panel = Panel.NONE
            if (refMode == 0) refreshRef()
        }
    }

    fun refreshRef() {
        if (!refOn || refMode != 0 || docW <= 0) return
        gl { it.snapshot(900) { b -> refCanvas = b } }
    }

    /** Gambar referensi sendiri dari galeri (diperkecil seperlunya). */
    fun loadRefImage(uri: Uri) {
        bg.execute {
            val bmp = runCatching {
                val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                app.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, opts) }
                var sample = 1
                while (opts.outWidth / (sample * 2) >= 1600 || opts.outHeight / (sample * 2) >= 1600) sample *= 2
                app.contentResolver.openInputStream(uri)?.use {
                    BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample })
                }
            }.getOrNull()
            main.post {
                if (bmp == null) {
                    showFlash("Gambar tidak bisa dibuka")
                } else {
                    refImage = bmp
                    refMode = 1
                    refOn = true
                }
            }
        }
    }
    var dropPos by mutableStateOf<Offset?>(null)
    val previews = mutableStateMapOf<String, ImageBitmap>()
    var sizeDragging by mutableStateOf(false)
    var stylusSeen by mutableStateOf(false)
    var pickPhoto: (() -> Unit)? = null
    var showReport by mutableStateOf(false)
    // Panduan menggambar (Drawing Guide)
    var guide by mutableStateOf(DrawingGuide())
    var guideOn by mutableStateOf(false)
    var guideEditing by mutableStateOf(false)
    /** Titik hilang perspektif yang sedang dipilih di layar Edit Panduan (-1 = tidak ada). */
    var guideSel by mutableIntStateOf(-1)
    private var guideBefore: Pair<Boolean, DrawingGuide>? = null
    // Tekstur kertas kanvas
    var paperType by mutableIntStateOf(Paper.NONE)
    var paperStrength by mutableFloatStateOf(0.55f)
    var paperScale by mutableFloatStateOf(1f)
    var paperEditing by mutableStateOf(false)
    private var paperBefore: Triple<Int, Float, Float>? = null
    val paperThumbs = mutableStateMapOf<Int, ImageBitmap>()
    var pickPaperFile: (() -> Unit)? = null
    // Teks
    var textEditing by mutableStateOf(false)
    var textSpec by mutableStateOf(TextSpec())
    /** Bingkai teks di kanvas (4 sudut), untuk sentuhan geser/putar. */
    var textBox: FloatArray? = null
    /** Menggambar jalur bebas untuk bentuk baris "Jalur". */
    var textDrawPath by mutableStateOf(false)
    private var textLayerId = -1
    private var textOldRect: IRect? = null
    private val textExec = Executors.newSingleThreadExecutor()
    @Volatile private var textGen = 0
    /** Uji: kertas yang dipasang begitu karya baru terbuka. */
    var pendingPaper: Int? = null

    init {
        val c = prefs.getInt("color", Color.rgb(28, 32, 48))
        setColorQuiet(c)
        val pal = prefs.getIntList("palette")
        palette.addAll(if (pal.isNotEmpty()) pal else defaultPalette())
        if (Diag.takeCrashFlag()) {
            showFlash("Ruang gambar sempat tertutup sendiri. Catatannya ada di Aksi › Laporan teknis")
        }
    }

    private fun defaultPalette(): List<Int> = listOf(
        0xFF000000, 0xFF3A3A3C, 0xFF8E8E93, 0xFFD1D1D6, 0xFFFFFFFF, 0xFFF4E3CF,
        0xFFE53935, 0xFFFF7043, 0xFFFFB300, 0xFFFDD835, 0xFF7CB342, 0xFF2E7D32,
        0xFF00897B, 0xFF00ACC1, 0xFF1E88E5, 0xFF3949AB, 0xFF8E24AA, 0xFFD81B60,
        0xFF6D4C41, 0xFFA1887F, 0xFFFFCCBC, 0xFFE8B996, 0xFFC68B59, 0xFF8D5524,
    ).map { it.toInt() }

    // ================= utilitas =================

    fun gl(block: (Engine) -> Unit) {
        canvasView?.gl(block)
    }

    fun showFlash(msg: String) {
        flash = msg
        flashId++
    }

    // ================= galeri & dokumen =================

    fun createArtwork(
        name: String, w: Int, h: Int, dpi: Int = 300, bgc: Int = Color.WHITE, bgShown: Boolean = true, paper: Int = Paper.NONE,
        profile: Int = ColorProfile.SRGB,
    ) {
        bg.execute {
            val id = Storage.create(app, name.ifBlank { "Tanpa judul" }, w, h, bgc, bgShown, dpi, paper, profile)
            main.post { openArtwork(id) }
        }
    }

    fun openArtwork(id: String) {
        Diag.log("Buka karya $id")
        // Karya Display P3 di layar gamut luas: permukaan kanvas dibuat dalam P3 dan jendela masuk mode warna lebar.
        val prof = runCatching { Storage.readMeta(Storage.dir(app, id))?.optInt("profile", 0) ?: 0 }.getOrDefault(0)
        profile = prof
        wantP3 = prof == ColorProfile.P3 && wideScreen
        onWideColor?.invoke(wantP3)
        panel = Panel.NONE
        layers = emptyList()
        thumbs.clear()
        docW = 0
        docH = 0
        loading = true
        openDocId = id
    }

    /** Dipanggil saat CanvasView baru dibuat untuk dokumen terbuka. */
    fun attachCanvas(v: CanvasView) {
        canvasView = v
        syncTool()
        val id = openDocId ?: return
        v.gl { it.openDoc(id) }
    }

    fun closeArtwork() {
        Diag.log("Tutup karya, kembali ke galeri")
        layerSelecting = false
        layerSelection.clear()
        val v = canvasView
        if (v == null) {
            if (exitOnClose) onExit?.invoke() else openDocId = null
            onWideColor?.invoke(false)
            return
        }
        cancelModes()
        busy = "Menyimpan…"
        v.gl { e ->
            e.save {
                busy = null
                galleryVersion++
                onWideColor?.invoke(false)
                if (exitOnClose) onExit?.invoke() else openDocId = null
            }
        }
    }

    fun detachCanvas(v: CanvasView) {
        v.engine.shutdown()
        if (canvasView === v) canvasView = null
    }

    fun onActivityPause() {
        val v = canvasView ?: return
        // Goresan yang sedang berjalan dianggap selesai, lalu karya disimpan sebelum kanvas dijeda.
        if (v.engine.glReady) {
            v.queueEvent {
                runCatching { v.engine.save(null) }.onFailure { Diag.log("Simpan saat jeda gagal: ${it.message}") }
            }
        }
        v.onPause()
        Diag.log("Kanvas dijeda")
    }

    fun onActivityResume() {
        val v = canvasView ?: return
        v.onResume()
        v.requestRender()
        Diag.log("Kanvas dilanjutkan")
    }

    override fun onReloading() {
        loading = true
    }

    override fun onSelectionChanged(active: Boolean, saved: Int) {
        selActive = active
        selSaved = saved
    }

    // Simpan otomatis tidak boleh memotong goresan yang sedang berjalan: bila sedang menggores, dicoba lagi nanti.
    private val saveRunnable = Runnable { gl { if (!it.autoSave()) main.post { onContentChanged() } } }
    private val thumbRunnable = Runnable { gl { it.makeThumbs() } }

    // ================= Engine.Listener =================

    override fun onDocOpened(id: String, name: String, w: Int, h: Int) {
        docName = name
        docW = w
        docH = h
        loading = false
        canvasView?.setCanvasSize(w, h)
        faceDirty = true
    }

    override fun onDocDpi(dpi: Int) {
        docDpi = dpi
    }

    fun setDpi(dpi: Int) = gl { it.setDpi(dpi) }

    /** Pratinjau latar selama panel Latar dibuka (belum masuk riwayat). */
    fun previewBackground(c: Int, visible: Boolean) = gl { it.bgPreview(c, visible) }

    /** Panel Latar ditutup: perubahan sejak [c0]/[v0] jadi satu langkah urungkan. */
    fun commitBackground(c0: Int, v0: Boolean) = gl { it.bgCommit(c0, v0) }

    override fun onPaper(type: Int, strength: Float, scale: Float) {
        paperType = type
        paperStrength = strength
        paperScale = scale
        pendingPaper?.let { pendingPaper = null; setPaper(it, Paper.defaultStrength(it), 1f) }
    }

    // ================= kertas kanvas =================

    fun setPaper(type: Int, strength: Float = paperStrength, scale: Float = paperScale) {
        paperType = type
        paperStrength = strength
        paperScale = scale
        gl { it.setPaper(type, strength, scale) }
    }

    /** Pilih jenis kertas; kekuatan diatur ke bawaan jenis itu bila jenisnya berganti. */
    fun choosePaper(type: Int) {
        if (type == Paper.CUSTOM) {
            pickPaperFile?.invoke()
            return
        }
        setPaper(type, if (type != paperType) Paper.defaultStrength(type) else paperStrength, paperScale)
    }

    fun startPaperEdit() {
        if (modalActive()) return
        if (selTool) closeSelTool()
        panel = Panel.NONE
        paperBefore = Triple(paperType, paperStrength, paperScale)
        paperEditing = true
        loadPaperThumbs()
    }

    fun endPaperEdit(keep: Boolean) {
        if (!keep) paperBefore?.let { (t, st, sc) -> setPaper(t, st, sc) }
        paperBefore = null
        paperEditing = false
    }

    private fun loadPaperThumbs() {
        val bgc = bgColor
        bg.execute {
            for (t in 1 until Paper.CUSTOM) {
                if (paperThumbs.containsKey(t)) continue
                val n = 192
                val data = runCatching { Paper.generate(t, n) }.getOrNull() ?: continue
                val px = IntArray(n * n)
                val s = Paper.defaultStrength(t)
                for (i in 0 until n * n) {
                    fun ch(c: Int, k: Int): Int {
                        val v = (data[i * 4 + k].toInt() and 0xFF) / 255f
                        return (c * (1f - s * (1f - v))).toInt().coerceIn(0, 255)
                    }
                    px[i] = Color.rgb(ch(Color.red(bgc), 0), ch(Color.green(bgc), 1), ch(Color.blue(bgc), 2))
                }
                val bmp = Bitmap.createBitmap(px, n, n, Bitmap.Config.ARGB_8888).asImageBitmap()
                main.post { paperThumbs[t] = bmp }
            }
        }
    }

    /** Kertas dari foto/gambar sendiri (PNG/JPG) atau pola Photoshop/GIMP (.pat): dibuat bersambung dan jadi ubin. */
    fun importPaper(uri: android.net.Uri) {
        busy = "Menyiapkan kertas…"
        bg.execute {
            val res = runCatching { PaperImport.read(app, uri) }.getOrNull()
            main.post {
                busy = null
                if (res == null) {
                    showFlash("Gambar kertas tidak bisa dibaca")
                    return@post
                }
                val (gray, n) = res
                val st = if (paperType == Paper.CUSTOM) paperStrength else Paper.defaultStrength(Paper.CUSTOM)
                paperType = Paper.CUSTOM
                paperStrength = st
                gl { it.setCustomPaper(gray, n, st, paperScale) }
                showFlash("Kertas dari gambar sendiri dipasang")
            }
        }
    }

    // ================= teks =================

    /** Tambah teks baru ([layerId] -1) atau edit layer teks yang ada. */
    fun startText(layerId: Int = -1) {
        if (docW <= 0 || modalActive()) return
        if (selTool) closeSelTool()
        panel = Panel.NONE
        val l = layers.firstOrNull { it.id == layerId }
        val old = l?.text?.let { TextSpec.fromJson(it) }
        val spec = old ?: TextSpec(
            text = "",
            font = textSpec.font,
            size = (min(docW, docH) * 0.09f).coerceIn(24f, 600f),
            color = color,
            cx = docW / 2f,
            // Agak ke atas supaya tidak tertutup papan ketik saat mengetik.
            cy = docH * 0.38f,
        )
        textLayerId = if (old != null) layerId else -1
        textOldRect = old?.let { lastRectOf(l.text) }
        textSpec = spec
        textDrawPath = false
        textEditing = true
        val id = textLayerId
        gl { it.textBegin(id) }
        renderText()
    }

    private fun lastRectOf(json: String?): IRect? = runCatching {
        val o = org.json.JSONObject(json ?: return null)
        if (!o.has("rx")) return null
        IRect(o.getInt("rx"), o.getInt("ry"), o.getInt("rx") + o.getInt("rw"), o.getInt("ry") + o.getInt("rh"))
    }.getOrNull()

    fun updateText(s: TextSpec) {
        textSpec = s
        renderText()
    }

    /** Gambar ulang teks di thread sendiri; hanya permintaan terakhir yang dikerjakan. */
    private fun renderText(after: ((TextRender.Result) -> Unit)? = null) {
        val s = textSpec
        val gen = ++textGen
        val w = docW
        val h = docH
        textExec.execute {
            if (gen != textGen && after == null) return@execute
            val r = runCatching { TextRender.render(app, s, w, h) }.getOrNull() ?: return@execute
            val bmp = r.bmp
            val buf = bmp?.let { b ->
                val bb = Buffers.direct(b.width * b.height * 4)
                b.copyPixelsToBuffer(bb)
                bb.position(0)
                bb
            }
            val rect = if (bmp != null) IRect(r.x, r.y, r.x + bmp.width, r.y + bmp.height) else IRect()
            bmp?.recycle()
            gl { e -> e.textUpdate(buf, rect); e.textBox = r.box }
            main.post {
                if (gen == textGen || after != null) textBox = r.box
                after?.invoke(r)
            }
        }
    }

    fun commitText() {
        if (!textEditing) return
        val s = textSpec
        if (s.text.isBlank()) {
            cancelText()
            return
        }
        renderText { r ->
            val name = s.text.replace('\n', ' ').trim().take(24).ifBlank { "Teks" }
            val json = org.json.JSONObject(s.toJson()).apply {
                // Kotak gambar terakhir, supaya saat diedit lagi gambar lamanya bisa dihapus.
                val b = r.box
                val xs = listOf(b[0], b[2], b[4], b[6])
                val ys = listOf(b[1], b[3], b[5], b[7])
                val pad = s.size * s.scale * 0.6f + s.outline * s.scale + (if (s.shadow) s.size * s.scale * 0.4f else 0f)
                val x0 = (xs.min() - pad).toInt().coerceAtLeast(0)
                val y0 = (ys.min() - pad).toInt().coerceAtLeast(0)
                val x1 = (xs.max() + pad).toInt().coerceAtMost(docW)
                val y1 = (ys.max() + pad).toInt().coerceAtMost(docH)
                put("rx", x0); put("ry", y0); put("rw", (x1 - x0).coerceAtLeast(0)); put("rh", (y1 - y0).coerceAtLeast(0))
            }.toString()
            val old = textOldRect
            gl { it.textCommit(name, json, old) }
            textEditing = false
            textDrawPath = false
            textBox = null
        }
    }

    fun cancelText() {
        textGen++
        gl { it.textCancel() }
        textEditing = false
        textDrawPath = false
        textBox = null
    }

    /** Jalur bebas selesai digambar (koordinat kanvas): teks mengikuti jalur ini. */
    fun setTextPath(pts: FloatArray) {
        textDrawPath = false
        if (pts.size < 4) return
        var cx = 0f
        var cy = 0f
        val n = pts.size / 2
        for (i in 0 until n) { cx += pts[2 * i]; cy += pts[2 * i + 1] }
        cx /= n
        cy /= n
        val local = ArrayList<Float>(pts.size)
        for (i in 0 until n) { local.add(pts[2 * i] - cx); local.add(pts[2 * i + 1] - cy) }
        updateText(textSpec.copy(shape = 4, path = local, cx = cx, cy = cy, rot = 0f, scale = 1f))
    }

    override fun onGuide(on: Boolean, g: DrawingGuide) {
        guide = g
        guideOn = on
        guideEditing = false
        guideSel = -1
    }

    // ================= panduan menggambar =================

    private fun guideEngine(block: (Engine) -> Unit) {
        val v = canvasView ?: return
        block(v.engine)
        v.requestRender()
    }

    /** Panduan berubah: simpan data karya beberapa detik kemudian. */
    fun guideChanged() = onContentChanged()

    fun switchGuide(on: Boolean) {
        guideOn = on
        guideEngine { it.guideOn = on }
        guideChanged()
    }

    fun updateGuide(g: DrawingGuide) {
        guide = g
        guideEngine { it.guide = g }
    }

    /** Buka layar Edit Panduan Menggambar (panduan otomatis dinyalakan). */
    fun startGuideEdit() {
        if (modalActive()) return
        if (selTool) closeSelTool()
        panel = Panel.NONE
        guideBefore = guideOn to guide
        guideOn = true
        guideEditing = true
        selectVp(-1)
        guideEngine { it.guideOn = true; it.guideEditing = true }
    }

    /** Tutup layar Edit Panduan; [keep] false = kembalikan panduan seperti sebelum dibuka. */
    fun endGuideEdit(keep: Boolean) {
        if (!keep) guideBefore?.let { (on, g) -> updateGuide(g); guideOn = on; guideEngine { it.guideOn = on } }
        guideBefore = null
        guideEditing = false
        selectVp(-1)
        guideEngine { it.guideEditing = false }
        guideChanged()
    }

    /** Atur ulang posisi/putaran/titik hilang (jenis, warna, dan tampilan garis tetap). */
    fun resetGuide() {
        val g = guide
        updateGuide(DrawingGuide(type = g.type, color = g.color, opacity = g.opacity, thickness = g.thickness, symmetry = g.symmetry, rotational = g.rotational))
        selectVp(-1)
    }

    fun setGuideType(t: Int) {
        if (guide.type == t) return
        updateGuide(guide.copy(type = t))
        selectVp(-1)
        // Seperti Procreate: memilih Simetri langsung menyalakan Bantuan menggambar di layer aktif.
        if (t == DrawingGuide.SYM && !activeAssist) setAssist(true)
    }

    fun selectVp(i: Int) {
        guideSel = i
        guideEngine { it.guideSel = i }
    }

    fun deleteSelectedVp() {
        val i = guideSel
        val g = guide
        if (i < 0 || i >= g.vpCount) return
        updateGuide(g.copy(vps = g.vps.filterIndexed { k, _ -> k / 2 != i }))
        selectVp(-1)
    }

    /** Bantuan menggambar layer aktif. */
    val activeAssist: Boolean get() = layers.firstOrNull { it.id == activeLayerId }?.assist == true

    fun setAssist(on: Boolean) {
        val l = layers.firstOrNull { it.id == activeLayerId } ?: return
        setLayerProps(l.id, propsOf(l).copy(assist = on))
    }

    override fun onDocFailed(msg: String) {
        loading = false
        showFlash(msg)
        openDocId = null
    }

    override fun onLayers(s: LayersSnapshot) {
        layers = s.layers
        groups = s.groups
        // Pilihan layer yang sudah tidak ada (dihapus/digabung) dibuang.
        val alive = s.layers.mapTo(HashSet()) { it.id }
        if (layerSelection.any { it !in alive }) layerSelection.retainAll(alive)
        activeLayerId = s.activeId
        canUndo = s.canUndo
        canRedo = s.canRedo
        layerLimit = s.limit
        bgColor = s.bg
        bgVisible = s.bgVisible
    }

    override fun onThumb(id: Int, bmp: Bitmap) {
        thumbs[id] = bmp.asImageBitmap()
    }

    override fun onContentChanged() {
        faceDirty = true
        if (refOn && refMode == 0) {
            main.removeCallbacks(refRunnable)
            main.postDelayed(refRunnable, 500)
        }
        main.removeCallbacks(saveRunnable)
        main.postDelayed(saveRunnable, 4000)
        main.removeCallbacks(thumbRunnable)
        main.postDelayed(thumbRunnable, 350)
    }

    override fun onPicked(color: Int, sx: Float, sy: Float) {
        pickColor = color
        loupe = Loupe(sx, sy, color)
    }

    override fun onTransformBounds(r: RectF) {
        if (r.isEmpty) {
            xfActive = false
            xfModel = null
        } else {
            xfModel = XfModel(r)
            pushTransform()
        }
    }

    override fun onMessage(msg: String) = showFlash(msg)

    override fun onBrushPreview(id: String, bmp: Bitmap) {
        previews[id] = bmp.asImageBitmap()
    }

    override fun onStrokeDone(color: Int) {
        if (history.firstOrNull() == color) return
        history.remove(color)
        history.add(0, color)
        while (history.size > 12) history.removeAt(history.size - 1)
        prefs.putIntList("history", history.toList())
    }

    // ================= alat & kuas =================

    fun brushIdFor(t: Int) = when (t) {
        ToolKind.SMUDGE -> smudgeBrushId
        ToolKind.ERASE -> eraseBrushId
        else -> paintBrushId
    }

    fun currentBrush(): Brush = brushes.get(brushIdFor(tool))

    private fun key(t: Int, id: String) = "$t:$id"

    fun sizeSlider(t: Int = tool): Float {
        val b = brushes.get(brushIdFor(t))
        return sizes[key(t, b.id)] ?: b.defaultSize
    }

    fun opacitySlider(t: Int = tool): Float {
        val b = brushes.get(brushIdFor(t))
        return opacities[key(t, b.id)] ?: 1f
    }

    fun setSizeSlider(v: Float) {
        sizes[key(tool, brushIdFor(tool))] = v.coerceIn(0f, 1f)
        syncTool()
    }

    fun setOpacitySlider(v: Float) {
        opacities[key(tool, brushIdFor(tool))] = v.coerceIn(0f, 1f)
        syncTool()
    }

    fun persistSliders() {
        prefs.putFloatMap("sizes", sizes.toMap())
        prefs.putFloatMap("opacities", opacities.toMap())
        prefs.putInt("penSize", (penSizeV * 1000).roundToInt())
        prefs.putInt("penOp", (penOpV * 1000).roundToInt())
    }

    fun penSizePx(): Float = 4f + (docMax / 3f - 4f) * penSizeV * penSizeV

    /** Penggeser samping: ukuran/opasitas kuas, atau ukuran/kekuatan sapuan saat mode Pena penyesuaian. */
    fun sideSize(): Float = if (adjPenActive) penSizeV else sizeSlider()
    fun sideOpacity(): Float = if (adjPenActive) penOpV else opacitySlider()

    fun setSideSize(v: Float) {
        if (adjPenActive) { penSizeV = v.coerceIn(0f, 1f); pushPen() } else setSizeSlider(v)
    }

    fun setSideOpacity(v: Float) {
        if (adjPenActive) { penOpV = v.coerceIn(0.02f, 1f); pushPen() } else setOpacitySlider(v)
    }

    private fun pushPen() {
        canvasView?.engine?.let {
            it.penSizePx = penSizePx()
            it.penOpacity = penOpV
        }
    }

    fun sizePx(t: Int = tool): Float {
        val b = brushes.get(brushIdFor(t))
        val s = sizeSlider(t)
        return b.sizeMin + (b.sizeMax - b.sizeMin) * s.pow(1.8f)
    }

    private fun setting(t: Int): BrushSetting {
        val b = brushes.get(brushIdFor(t))
        // Kuas ganda: kuas kedua dari pustaka (bukan dirinya sendiri, dan bukan kuas stempel).
        val dual = if (b.dualId.isNotEmpty() && b.dualId != b.id) brushes.list.firstOrNull { it.id == b.dualId && !it.stamp } else null
        return BrushSetting(b, sizePx(t), opacitySlider(t) * b.opacityMax, dual)
    }

    fun syncTool() {
        val v = canvasView ?: return
        v.engine.tool = ToolState(
            tool = tool,
            paint = setting(ToolKind.PAINT),
            smudge = setting(ToolKind.SMUDGE),
            erase = setting(ToolKind.ERASE),
            color = color,
            gamma = prefs.pressureGamma,
            prediction = prefs.prediction,
            smoothing = Smoothing(prefs.smoothStab, prefs.smoothMotion, prefs.smoothExpr),
            color2 = color2,
        )
    }

    /** Tukar warna utama dan warna kedua. */
    fun swapColor2() {
        val c = color
        setColorQuiet(color2)
        color2 = c
        prefs.putInt("color2", color2)
        colorChanged()
    }

    /** Jadikan warna sekarang sebagai warna kedua. */
    fun setColor2FromCurrent() {
        color2 = color
        prefs.putInt("color2", color2)
        syncTool()
        showFlash("Warna kedua diatur")
    }

    /** Ketuk ikon alat: pilih alat; ketuk lagi untuk membuka pustaka kuas. */
    fun onToolIcon(t: Int) {
        if (modalActive()) return
        if (selTool) closeSelTool()
        if (tool == t) {
            panel = if (panel == Panel.BRUSHES) Panel.NONE else Panel.BRUSHES
            studioBrushId = null
        } else {
            tool = t
            if (panel == Panel.BRUSHES) studioBrushId = null
            syncTool()
        }
    }

    fun selectBrush(id: String) {
        when (tool) {
            ToolKind.SMUDGE -> { smudgeBrushId = id; prefs.putString("smudgeBrush", id) }
            ToolKind.ERASE -> { eraseBrushId = id; prefs.putString("eraseBrush", id) }
            else -> { paintBrushId = id; prefs.putString("paintBrush", id) }
        }
        syncTool()
    }

    /** Pemilih berkas kuas (dipasang oleh layar kanvas). */
    var pickBrushFile: (() -> Unit)? = null

    /** Kuas yang sedang dipakai alat mana pun ikut terhapus: alat itu kembali ke kuas bawaannya. */
    private fun forgetBrushes(ids: Collection<String>) {
        if (paintBrushId in ids) { paintBrushId = "pensil6b"; prefs.putString("paintBrush", paintBrushId) }
        if (smudgeBrushId in ids) { smudgeBrushId = "airlembut"; prefs.putString("smudgeBrush", smudgeBrushId) }
        if (eraseBrushId in ids) { eraseBrushId = "bulatkeras"; prefs.putString("eraseBrush", eraseBrushId) }
        if (studioBrushId in ids) studioBrushId = null
        for (id in ids) previews.remove(id)
        syncTool()
    }

    fun deleteBrush(id: String) {
        val name = brushes.list.firstOrNull { it.id == id }?.name ?: return
        brushes.delete(id)
        forgetBrushes(listOf(id))
        showFlash("Kuas \"$name\" dihapus")
    }

    /** Hapus satu daftar kuas (kategori) beserta isinya. */
    fun deleteBrushCategory(c: String) {
        if (brushes.countIn(c) == 0) {
            brushes.deleteCategory(c)
            showFlash("Daftar \"$c\" dihapus")
            return
        }
        val ids = brushes.deleteCategory(c)
        forgetBrushes(ids)
        showFlash(if (ids.isEmpty()) "Daftar ini tidak bisa dihapus" else "Daftar \"$c\" dihapus (${ids.size} kuas)")
    }

    fun renameBrushCategory(old: String, new: String) = brushes.renameCategory(old, new)

    /** Ekspor satu daftar kuas ke Download/WInote sebagai .wkuas (bisa diimpor lagi di tablet lain). */
    fun exportBrushSet(set: String) {
        busy = "Mengekspor kuas…"
        bg.execute {
            val name = set.replace(Regex("[^A-Za-z0-9 _-]"), "").trim().ifBlank { "Kuas" } + "-" + stamp() + ".wkuas"
            val saved = runCatching {
                val values = ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, name)
                    put(MediaStore.MediaColumns.MIME_TYPE, "application/octet-stream")
                    put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/WInote")
                    put(MediaStore.MediaColumns.IS_PENDING, 1)
                }
                val resolver = app.contentResolver
                val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values) ?: error("Folder menolak")
                resolver.openOutputStream(uri)?.use { out -> brushes.exportSet(set, out) }
                values.clear()
                values.put(MediaStore.MediaColumns.IS_PENDING, 0)
                resolver.update(uri, values, null, null)
            }.isSuccess
            main.post {
                busy = null
                showFlash(if (saved) "Daftar \"$set\" tersimpan di Download/WInote/$name" else "Gagal mengekspor kuas")
            }
        }
    }

    fun createBrushSet(name: String): String = brushes.createSet(name)

    fun moveBrushTo(id: String, set: String) {
        brushes.moveTo(id, set)
        showFlash("Kuas dipindah ke \"$set\"")
    }

    fun copyBrushTo(b: Brush, set: String) {
        val nb = brushes.copyTo(b, set)
        selectBrush(nb.id)
        showFlash("Kuas disalin ke \"$set\"")
    }

    /** Impor kuas dari berkas .brush/.brushset Procreate, .abr Photoshop, pola .pat, atau gambar PNG/JPG; kuas pertama langsung dipilih. */
    fun importBrush(uri: android.net.Uri) {
        busy = "Mengimpor kuas…"
        bg.execute {
            val r = runCatching { id.goresan.engine.BrushImport.import(app, uri) }
                .getOrElse { id.goresan.engine.BrushImport.Result(emptyList(), "Gagal mengimpor kuas.") }
            main.post {
                busy = null
                brushes.add(r.brushes)
                r.brushes.firstOrNull()?.let { selectBrush(it.id) }
                showFlash(r.message)
            }
        }
    }

    fun updateBrush(b: Brush) {
        brushes.update(b)
        previews.remove(b.id)
        requestPreview(b)
        syncTool()
    }

    fun requestPreview(b: Brush) {
        if (previews.containsKey(b.id)) return
        val d = if (b.dualId.isNotEmpty() && b.dualId != b.id) brushes.list.firstOrNull { it.id == b.dualId && !it.stamp } else null
        gl { it.brushPreview(b, d) }
    }

    // ================= warna =================

    val color: Int get() = Color.HSVToColor(floatArrayOf(hue, sat, value))

    private fun setColorQuiet(c: Int) {
        val hsv = FloatArray(3)
        Color.colorToHSV(c, hsv)
        // Pertahankan rona saat warna abu-abu supaya roda tidak melompat.
        if (hsv[1] > 0.001f && hsv[2] > 0.001f) hue = hsv[0]
        sat = hsv[1]
        value = hsv[2]
    }

    fun setColor(c: Int) {
        setColorQuiet(c)
        colorChanged()
    }

    fun setHsv(h: Float, s: Float, v: Float) {
        hue = h.coerceIn(0f, 360f)
        sat = s.coerceIn(0f, 1f)
        value = v.coerceIn(0f, 1f)
        colorChanged()
    }

    private fun colorChanged() {
        prefs.putInt("color", color)
        syncTool()
    }

    fun openColorPanel() {
        if (panel == Panel.COLOR) {
            panel = Panel.NONE
        } else {
            prevColor = color
            panel = Panel.COLOR
        }
    }

    fun addToPalette() {
        val c = color
        if (c !in palette) {
            palette.add(c)
            prefs.putIntList("palette", palette.toList())
        }
    }

    fun removeFromPalette(c: Int) {
        palette.remove(c)
        prefs.putIntList("palette", palette.toList())
    }

    // ================= pipet =================

    fun armPick() {
        if (modalActive()) return
        pickArmed = !pickArmed
        panel = Panel.NONE
    }

    fun showLoupe(sx: Float, sy: Float) {
        val l = loupe
        loupe = Loupe(sx, sy, l?.color ?: color)
        if (pickColor == null) prevColor = color
    }

    fun finishPick() {
        pickColor?.let { setColor(it) }
        pickColor = null
        loupe = null
        pickArmed = false
    }

    fun cancelPick() {
        pickColor = null
        loupe = null
        pickArmed = false
    }

    // ================= gestur =================

    fun onStylusSeen() {
        if (!stylusSeen) stylusSeen = true
        if (prefs.autoDisableFinger()) {
            showFlash("Pena terdeteksi: jari sekarang untuk menggeser kanvas (bisa diubah di Aksi › Preferensi)")
        }
    }

    fun onMultiTap(n: Int) {
        when (n) {
            2 -> undo()
            3 -> redo()
            4 -> uiHidden = !uiHidden
        }
    }

    fun closePanelsFromCanvas(): Boolean {
        if (panel != Panel.NONE) {
            panel = Panel.NONE
            studioBrushId = null
            persistSliders()
            return true
        }
        return false
    }

    /**
     * True bila transformasi atau penyesuaian sedang terbuka (aksi lain ditahan dulu).
     * ColorDrop tidak menahan apa-apa: isian yang masih terbuka langsung disimpan, seperti di Procreate.
     */
    fun modalActive(): Boolean {
        if (fillActive) fillDone(true)
        return xfActive || guideEditing || paperEditing || textEditing || (adjustKind >= 0 && adjustKind != Adj.CLONE)
    }

    fun undo() {
        if (adjPenActive) { gl { it.penStep(redo = false) }; return }
        if (modalActive()) return
        gl { it.undo() }
    }

    fun redo() {
        if (adjPenActive) { gl { it.penStep(redo = true) }; return }
        if (modalActive()) return
        gl { it.redo() }
    }

    /** Pesan singkat setelah urungkan/ulangi benar-benar terjadi (atau tidak ada langkah lagi). */
    override fun onHistory(redo: Boolean, label: String?) {
        showFlash(
            when {
                label == null && redo -> "Tidak ada yang bisa diulangi"
                label == null -> "Tidak ada yang bisa diurungkan"
                redo -> "Ulangi $label"
                else -> "Urungkan $label"
            }
        )
    }

    // ================= layer =================

    fun layerCmd(block: (Engine) -> Unit) {
        if (modalActive()) return
        gl(block)
    }

    fun setLayerProps(id: Int, p: LayerProps, undoable: Boolean = true) {
        gl { it.setProps(id, p, undoable) }
    }

    fun propsOf(l: LayerInfo) = LayerProps(l.name, l.visible, l.opacity, l.blend, l.alphaLock, l.clip, l.locked, l.group, l.assist, l.text)

    // ================= ColorDrop =================

    fun dropColorAt(x: Float, y: Float) {
        val v = canvasView ?: return
        if (modalActive() || docW <= 0) return
        val c = v.toCanvas(x - v.left, y - v.top)
        id.goresan.Diag.log("ColorDrop di layar (${x.toInt()}, ${y.toInt()}) → kanvas (${c.x.toInt()}, ${c.y.toInt()})")
        if (c.x < 0 || c.y < 0 || c.x >= docW || c.y >= docH) return
        panel = Panel.NONE
        fillActive = true
        val col = color
        val thr = fillThreshold
        val ref = fillRefAll
        gl { it.fillStart(c.x, c.y, col, thr, ref) }
    }

    fun fillChanged() {
        prefs.fillThreshold = fillThreshold
        val thr = fillThreshold
        val ref = fillRefAll
        val col = color
        gl { it.fillUpdate(thr, ref, col) }
    }

    fun fillReseed(cx: Float, cy: Float) {
        gl { it.fillReseed(cx, cy) }
    }

    fun fillDone(commit: Boolean) {
        if (!fillActive) return
        fillActive = false
        gl { if (commit) it.fillCommit() else it.fillCancel() }
    }

    // ================= transformasi =================

    /**
     * Mulai transformasi: [ids] tertentu (mis. isi satu grup), atau layer-layer yang sedang dipilih di panel
     * (bila [useSelection] dan ada pilihan), atau layer aktif.
     */
    fun startTransform(ids: List<Int>? = null, useSelection: Boolean = true) {
        if (adjustKind == Adj.CLONE) adjustDone(true)
        if (selTool) closeSelTool()
        if (modalActive()) return
        val targets = ids ?: if (useSelection && layerSelecting && layerSelection.isNotEmpty()) {
            layers.map { it.id }.filter { it in layerSelection }
        } else null
        panel = Panel.NONE
        xfModel = null
        xfCount = targets?.size ?: 1
        if (xfMode == XfModel.WARP && xfCount > 1) xfMode = XfModel.UNIFORM
        xfActive = true
        gl { it.transformStart(targets) }
    }

    fun transformPost(m: Matrix) {
        val x = xfModel ?: return
        if (!xfActive) return
        x.post(m)
        pushTransform()
    }

    private fun pushTransform() {
        val x = xfModel ?: return
        canvasView?.let {
            it.engine.xfRequest = x.frame(xfMode, xfInterp)
            it.requestRender()
        }
    }

    fun transformFlip(horizontal: Boolean) {
        xfModel?.flip(horizontal) ?: return
        pushTransform()
    }

    fun transformRotate(deg: Float) {
        xfModel?.rotate(deg) ?: return
        pushTransform()
    }

    fun transformFit() {
        if (docW <= 0) return
        xfModel?.fit(docW, docH) ?: return
        pushTransform()
    }

    fun transformReset() {
        xfModel?.reset() ?: return
        pushTransform()
    }

    fun chooseXfMode(m: Int) {
        if (m == XfModel.WARP && xfCount > 1) {
            showFlash("Warp hanya untuk satu layer")
            return
        }
        xfMode = m
        pushTransform()
    }

    fun toggleXfSnap() {
        xfSnap = !xfSnap
        prefs.putBool("xfSnap", xfSnap)
    }

    /** Terdekat → Bilinear → Bikubik → … */
    fun cycleXfInterp() {
        xfInterp = (xfInterp + 1) % 3
        prefs.putInt("xfInterp", xfInterp)
        pushTransform()
    }

    /** Pegangan di bawah titik layar ([sx], [sy]); -1 = tidak ada (geser/Warp lembut). */
    fun xfHit(sx: Float, sy: Float, toScreen: (FloatArray) -> Unit, density: Float): Int {
        val x = xfModel ?: return -1
        var best = -1
        var bestD = 26f * density
        for ((id, hx, hy) in x.handles(xfMode, toScreen, 30f * density)) {
            val d = kotlin.math.hypot(hx - sx, hy - sy)
            if (d < bestD) { bestD = d; best = id }
        }
        return best
    }

    fun xfDragStart(id: Int, cx: Float, cy: Float, sx: Float, sy: Float, toScreen: (FloatArray) -> Unit) {
        val x = xfModel ?: return
        var pts: FloatArray? = null
        var sigma = 1f
        xfSoft = false
        if (xfMode == XfModel.WARP && id < 0) {
            val q = x.quad
            xfSoft = pointInQuad(q, cx, cy)
            pts = x.netCanvas()
            toScreen(pts)
            val sq = q.copyOf()
            toScreen(sq)
            sigma = max(20f, kotlin.math.hypot(sq[4] - sq[0], sq[5] - sq[1]) / 5f)
        }
        x.dragStart(id, cx, cy, pts, sx, sy, sigma)
    }

    fun xfDragTo(cx: Float, cy: Float, zoom: Float, density: Float) {
        val x = xfModel ?: return
        x.drag(cx, cy, xfMode, xfSnap, 10f * density / max(zoom, 0.01f), docW, docH, xfSoft)
        pushTransform()
    }

    fun xfDragEnd() {
        xfModel?.dragEnd() ?: return
        pushTransform()
    }

    private fun pointInQuad(q: FloatArray, x: Float, y: Float): Boolean {
        var inside = false
        var j = 3
        for (i in 0..3) {
            val xi = q[2 * i]; val yi = q[2 * i + 1]; val xj = q[2 * j]; val yj = q[2 * j + 1]
            if ((yi > y) != (yj > y) && x < (xj - xi) * (y - yi) / (yj - yi) + xi) inside = !inside
            j = i
        }
        return inside
    }

    fun transformDone(commit: Boolean) {
        if (!xfActive) return
        xfActive = false
        xfModel = null
        gl { if (commit) it.transformCommit() else it.transformCancel() }
    }

    // ================= pilihan (selection) =================

    fun toggleSelTool() {
        if (selTool) { closeSelTool(); return }
        if (modalActive()) return
        if (adjustKind == Adj.CLONE) adjustDone(true)
        panel = Panel.NONE
        selTool = true
        selPanel = 0
    }

    /** Tutup alat pilihan; pilihan yang ada tetap aktif (kuas, penyesuaian, transformasi hanya di dalamnya). */
    fun closeSelTool() {
        finishSelPoly()
        selTool = false
        selPanel = 0
        canvasView?.engine?.selDraft = null
        canvasView?.requestRender()
    }

    fun chooseSelMode(m: Int) {
        finishSelPoly()
        selMode = m
    }

    fun chooseSelOp(op: Int) { selOpMode = op }

    fun selInvert() = gl { it.selInvert() }

    fun selClear() {
        finishSelPoly(cancel = true)
        selFeatherPx = 0f
        gl { it.selClear() }
    }

    fun selCopyPaste() = gl { it.selCopyPaste() }

    fun selFill() {
        val c = color
        gl { it.selFill(c) }
    }

    fun selSave() = gl { it.selSave() }

    fun selLoad(i: Int) = gl { it.selLoad(i) }

    fun setSelFeather(v: Float) {
        selFeatherPx = v
        val px = v.roundToInt()
        gl { it.selFeather(px) }
    }

    fun changeSelThreshold(v: Float) {
        selThreshold = v.coerceIn(0f, 1f)
        prefs.putInt("selThr", (selThreshold * 100).roundToInt())
    }

    private fun draft(closed: Boolean) {
        canvasView?.let {
            it.engine.selDraft = if (selPath.isEmpty()) null else selPath.toFloatArray()
            it.engine.selDraftClosed = closed
            it.requestRender()
        }
    }

    private fun boxDraft(x1: Float, y1: Float) {
        selPath.clear()
        if (selMode == SEL_RECT) {
            selPath.addAll(listOf(selX0, selY0, x1, selY0, x1, y1, selX0, y1))
        } else {
            val cx = (selX0 + x1) / 2f; val cy = (selY0 + y1) / 2f
            val rx = kotlin.math.abs(x1 - selX0) / 2f; val ry = kotlin.math.abs(y1 - selY0) / 2f
            for (k in 0 until 64) {
                val t = k * 2.0 * Math.PI / 64
                selPath.add(cx + rx * kotlin.math.cos(t).toFloat()); selPath.add(cy + ry * kotlin.math.sin(t).toFloat())
            }
        }
        draft(true)
    }

    /** Jari/pena menyentuh kanvas saat alat pilihan terbuka ([x], [y] = titik kanvas, [zoom] untuk jarak layar). */
    fun selDown(x: Float, y: Float, zoom: Float, density: Float) {
        selX0 = x; selY0 = y
        when (selMode) {
            SEL_AUTO -> {
                selThrStart = selThreshold
                val thr = selThreshold
                val op = selOpMode
                gl { it.selAutoBegin(x, y, thr, op) }
                showFlash("Ambang ${(thr * 100).roundToInt()}% · geser ke kiri/kanan untuk mengubah")
            }
            SEL_FREE -> {
                if (!selPoly) selPath.clear()
                if (selPoly && selPath.size >= 6 && nearFirst(x, y, zoom, density)) return
                selPath.add(x); selPath.add(y)
                draft(false)
            }
            else -> boxDraft(x, y)
        }
    }

    fun selMove(x: Float, y: Float, dxScreen: Float, zoom: Float, density: Float) {
        when (selMode) {
            SEL_AUTO -> {
                val thr = (selThrStart + dxScreen / (500f * density)).coerceIn(0f, 1f)
                if ((thr * 100).roundToInt() != (selThreshold * 100).roundToInt()) {
                    selThreshold = thr
                    val op = selOpMode
                    gl { it.selAutoThreshold(thr, op) }
                    showFlash("Ambang ${(thr * 100).roundToInt()}%")
                }
            }
            SEL_FREE -> {
                val n = selPath.size
                if (n >= 2 && kotlin.math.hypot(x - selPath[n - 2], y - selPath[n - 1]) * zoom < 3f * density) return
                selPath.add(x); selPath.add(y)
                draft(false)
            }
            else -> boxDraft(x, y)
        }
    }

    /** Sentuhan dilepas. [tap] = ketukan singkat tanpa geser. */
    fun selUp(x: Float, y: Float, tap: Boolean, zoom: Float, density: Float) {
        when (selMode) {
            SEL_AUTO -> {
                prefs.putInt("selThr", (selThreshold * 100).roundToInt())
                gl { it.selAutoEnd() }
            }
            SEL_FREE -> {
                if (tap) {
                    if (selPoly && selPath.size >= 6 && nearFirst(x, y, zoom, density)) {
                        finishSelPoly()
                    } else {
                        // Ketukan: titik sudut segi banyak; ketuk titik pertama untuk menutup.
                        selPoly = true
                        draft(false)
                    }
                } else if (!selPoly) {
                    finishSelPoly()
                }
            }
            else -> {
                val pts = floatArrayOf(selX0, selY0, x, y)
                selPath.clear()
                draft(false)
                if (kotlin.math.abs(x - selX0) * zoom > 4f * density && kotlin.math.abs(y - selY0) * zoom > 4f * density) {
                    val kind = if (selMode == SEL_RECT) 1 else 2
                    val op = selOpMode
                    gl { it.selShape(kind, pts, op) }
                }
            }
        }
    }

    /** Gerakan dibatalkan (mis. jari kedua untuk geser/perbesar kanvas). */
    fun selCancelGesture() {
        if (selMode == SEL_AUTO) gl { it.selAutoEnd() }
        if (selMode != SEL_FREE || !selPoly) {
            selPath.clear()
            draft(false)
        }
    }

    private fun nearFirst(x: Float, y: Float, zoom: Float, density: Float): Boolean =
        selPath.size >= 2 && kotlin.math.hypot(x - selPath[0], y - selPath[1]) * zoom < 24f * density

    /** Tutup garis bebas/segi banyak yang sedang dibuat dan jadikan pilihan. */
    private fun finishSelPoly(cancel: Boolean = false) {
        val pts = selPath.toFloatArray()
        selPath.clear()
        selPoly = false
        canvasView?.engine?.selDraft = null
        canvasView?.requestRender()
        if (cancel || pts.size < 6 || selMode != SEL_FREE) return
        val op = selOpMode
        gl { it.selShape(0, pts, op) }
    }

    // ================= penyesuaian =================

    fun startAdjust(kind: Int) {
        if (adjustKind == Adj.CLONE) adjustDone(true)
        if (selTool) closeSelTool()
        if (modalActive()) return
        panel = Panel.NONE
        adjustKind = kind
        adjMode = 0
        // Color Balance dibuka di nada tengah, seperti Procreate.
        adjTab = if (kind == Adj.BALANCE) 1 else 0
        adjV.clear()
        adjV.addAll(adjDefaults(kind).toList())
        adjCx = docW / 2f
        adjCy = docH / 2f
        cloneArmed = false
        adjPen = false
        adjPenErase = false
        penUndo = 0
        penRedo = 0
        if (kind == Adj.CURVES) curvePts.forEach { it.clear(); it.add(Offset(0f, 0f)); it.add(Offset(1f, 1f)) }
        val en = canvasView?.engine
        en?.marker = if (kind == Adj.PERSP || kind == Adj.CHROMA) floatArrayOf(adjCx, adjCy) else null
        if (kind == Adj.LIQUIFY) liquifyParamsChanged()
        // Gradient Map langsung tampil (100%); yang lain mulai dari 0 sampai penggeser digeser.
        val first = if (kind == Adj.GMAP) adjParams() else null
        val lut = if (kind == Adj.GMAP) adjLut() else null
        gl {
            it.adjustStart(kind)
            if (first != null) {
                it.adjLut = lut
                it.adjRequest = first
            }
        }
        if (kind == Adj.CLONE) showFlash("Geser lingkaran sumber dengan jari, lalu gambar")
    }

    private fun adjDefaults(kind: Int): FloatArray = when (kind) {
        Adj.BALANCE -> FloatArray(9)
        Adj.GMAP -> floatArrayOf(1f)
        Adj.MOTION -> floatArrayOf(0f, 0f)
        Adj.NOISE -> floatArrayOf(0f, (max(docW, docH) / 24f).coerceIn(8f, 300f), 4f, 0.5f, 7f)
        Adj.BLOOM -> floatArrayOf(0f, 0.5f, (max(docW, docH) / 60f).coerceIn(6f, 120f), 0.3f)
        Adj.GLITCH -> floatArrayOf(0f, (max(docW, docH) / 50f).coerceIn(4f, 150f), 30f)
        Adj.HALFTONE -> floatArrayOf(0f)
        Adj.SHARPEN -> floatArrayOf(0f, 3.5f)
        Adj.CHROMA -> floatArrayOf(0f, 0.5f, 0f)
        Adj.LIQUIFY -> floatArrayOf((max(docW, docH) / 10f).coerceIn(20f, 800f), 0.5f, 0.5f, 1f)
        else -> FloatArray(3)
    }

    private fun av(i: Int): Float = adjV.getOrElse(i) { 0f }
    private fun rad(deg: Float) = Math.toRadians(deg.toDouble()).toFloat()

    /** Ukuran terpanjang kanvas: dasar skala penggeser yang bergantung ukuran gambar. */
    val docMax: Float get() = max(1, max(docW, docH)).toFloat()

    private fun adjParams(): FloatArray? = when (adjustKind) {
        Adj.HSB -> floatArrayOf(av(0) / 360f, av(1), av(2))
        Adj.GAUSS -> floatArrayOf(av(0))
        Adj.BALANCE -> FloatArray(9) { av(it) }
        Adj.CURVES -> floatArrayOf(0f)
        Adj.GMAP -> floatArrayOf(av(0))
        Adj.MOTION -> floatArrayOf(av(0), rad(av(1)))
        Adj.PERSP -> floatArrayOf(av(0), adjMode.toFloat(), adjCx, adjCy)
        Adj.NOISE -> floatArrayOf(av(0), av(1), av(2), av(3), adjMode.toFloat(), av(4))
        Adj.SHARPEN -> floatArrayOf(av(0), av(1))
        Adj.BLOOM -> floatArrayOf(av(0), av(1), av(2), av(3))
        Adj.GLITCH -> floatArrayOf(av(0), av(1), av(2), adjMode.toFloat())
        Adj.HALFTONE -> floatArrayOf(av(0), adjMode.toFloat())
        Adj.CHROMA -> floatArrayOf(av(0) * docMax / 10f, av(1), rad(av(2)), adjMode.toFloat(), adjCx, adjCy)
        Adj.LIQUIFY -> floatArrayOf(av(3))
        else -> null
    }

    private fun adjLut(): ByteArray? = when (adjustKind) {
        Adj.CURVES -> Curves.lut(curveTable(0), curveTable(1), curveTable(2), curveTable(3))
        Adj.GMAP -> Gradients.lut(gradientPreset(adjMode))
        else -> null
    }

    /** Preset Gradient Map; indeks terakhir = hitam → warna kuas → putih. */
    fun gradientPreset(i: Int): Gradients.Preset =
        if (i in Gradients.presets.indices) Gradients.presets[i] else Gradients.fromColor(color)

    private fun curveTable(ch: Int): FloatArray {
        val pts = curvePts[ch]
        return Curves.table(FloatArray(pts.size) { pts[it].x }, FloatArray(pts.size) { pts[it].y })
    }

    fun setAdj(i: Int, v: Float) {
        if (i !in adjV.indices) return
        adjV[i] = v
        if (adjustKind == Adj.LIQUIFY) {
            liquifyParamsChanged()
            if (i == 3) adjustChanged()
        } else {
            adjustChanged()
        }
    }

    fun chooseAdjMode(m: Int) {
        adjMode = m
        // Chromatic Aberration mode Geser tidak memakai titik pusat: penandanya disembunyikan.
        if (adjustKind == Adj.CHROMA) canvasView?.engine?.marker = if (m == 0) floatArrayOf(adjCx, adjCy) else null
        if (adjustKind == Adj.LIQUIFY) liquifyParamsChanged() else adjustChanged()
    }

    fun adjustChanged() {
        val p = adjParams() ?: return
        val lut = adjLut()
        canvasView?.let {
            if (lut != null) it.engine.adjLut = lut
            it.engine.adjRequest = p
            it.requestRender()
        }
    }

    /** Titik pusat Perspective Blur / Chromatic Aberration dipindah (ketuk/seret di kanvas). */
    fun setAdjustCenter(x: Float, y: Float) {
        adjCx = x.coerceIn(0f, docW.toFloat())
        adjCy = y.coerceIn(0f, docH.toFloat())
        if (adjustKind == Adj.PERSP || (adjustKind == Adj.CHROMA && adjMode == 0)) canvasView?.engine?.marker = floatArrayOf(adjCx, adjCy)
        adjustChanged()
    }

    /** Ganti mode Layer (false) / Pena (true) penyesuaian yang sedang terbuka. */
    fun switchAdjPen(on: Boolean) {
        if (adjustKind < 0 || adjustKind == Adj.LIQUIFY || adjustKind == Adj.CLONE) return
        adjPen = on
        adjPenErase = false
        penUndo = 0
        penRedo = 0
        pushPen()
        gl { it.adjustPen(on) }
        if (on) showFlash("Sapukan pena di bagian yang ingin diberi efek")
    }

    /** Hapus semua sapuan mode Pena. */
    fun penClear() = gl { it.penClear() }

    fun liquifyParamsChanged() {
        canvasView?.engine?.liqParams = floatArrayOf(adjMode.toFloat(), av(0), av(1), av(2))
    }

    fun liquifyReset() = gl { it.liquifyReset() }

    fun setCloneSource(x: Float, y: Float) {
        cloneArmed = false
        gl { it.cloneSource(x, y) }
    }

    fun adjustDone(commit: Boolean) {
        if (adjustKind < 0) return
        val k = adjustKind
        adjustKind = -1
        cloneArmed = false
        adjPen = false
        adjPenErase = false
        penUndo = 0
        penRedo = 0
        canvasView?.engine?.marker = null
        if (k == Adj.CLONE) {
            gl { it.cloneStop() }
            return
        }
        gl { if (commit) it.adjustCommit() else it.adjustCancel() }
    }

    fun cancelModes() {
        if (selTool) closeSelTool()
        if (fillActive) fillDone(true)
        if (xfActive) transformDone(false)
        if (adjustKind >= 0) adjustDone(false)
        pickArmed = false
        loupe = null
    }

    // ================= sisipkan foto =================

    fun importPhoto(uri: Uri) {
        if (docW <= 0 || modalActive()) return
        val w = docW
        val h = docH
        busy = "Menyisipkan foto…"
        bg.execute {
            val result = runCatching {
                val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                app.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, opts) }
                var sample = 1
                while (opts.outWidth / (sample * 2) >= w || opts.outHeight / (sample * 2) >= h) sample *= 2
                val src = app.contentResolver.openInputStream(uri)?.use {
                    BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample })
                } ?: error("Foto tidak bisa dibuka")
                val canvasBmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
                val s = min(1f, min(w.toFloat() / src.width, h.toFloat() / src.height))
                val dw = src.width * s
                val dh = src.height * s
                val dst = RectF((w - dw) / 2f, (h - dh) / 2f, (w + dw) / 2f, (h + dh) / 2f)
                Canvas(canvasBmp).drawBitmap(src, null, dst, Paint(Paint.FILTER_BITMAP_FLAG))
                src.recycle()
                val buf = Buffers.direct(w * h * 4)
                canvasBmp.copyPixelsToBuffer(buf)
                canvasBmp.recycle()
                buf.position(0)
                buf
            }
            main.post {
                busy = null
                result.onSuccess { buf ->
                    gl { it.importLayer(buf, "Foto") }
                    main.postDelayed({ startTransform(useSelection = false) }, 120)
                }.onFailure { showFlash(it.message ?: "Foto tidak bisa dibuka") }
            }
        }
    }

    // ================= ekspor =================

    private fun stamp(): String = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date())

    private fun safeName(): String = docName.replace(Regex("[^A-Za-z0-9 _-]"), "").trim().ifBlank { "Gambar" }

    fun exportImage(jpg: Boolean, transparent: Boolean) {
        if (modalActive()) return
        busy = "Mengekspor…"
        val bgc = if (jpg) (if (bgVisible) bgColor else Color.WHITE) else if (transparent) null else (if (bgVisible) bgColor else null)
        gl { e ->
            val bmp = e.exportBitmap(bgc)
            if (bmp == null) {
                main.post { busy = null }
                return@gl
            }
            bg.execute {
                val ok = runCatching {
                    val name = "${safeName()}-${stamp()}." + if (jpg) "jpg" else "png"
                    val values = ContentValues().apply {
                        put(MediaStore.MediaColumns.DISPLAY_NAME, name)
                        put(MediaStore.MediaColumns.MIME_TYPE, if (jpg) "image/jpeg" else "image/png")
                        put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/WInote")
                        put(MediaStore.MediaColumns.IS_PENDING, 1)
                    }
                    val resolver = app.contentResolver
                    val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values) ?: error("Galeri menolak")
                    val dpi = docDpi
                    resolver.openOutputStream(uri)?.let { DpiOutputStream(it, dpi, jpg) }?.use { out ->
                        if (jpg) bmp.compress(Bitmap.CompressFormat.JPEG, 95, out) else bmp.compress(Bitmap.CompressFormat.PNG, 100, out)
                    }
                    values.clear()
                    values.put(MediaStore.MediaColumns.IS_PENDING, 0)
                    resolver.update(uri, values, null, null)
                }.isSuccess
                bmp.recycle()
                main.post {
                    busy = null
                    showFlash(if (ok) "Tersimpan di Galeri › Pictures/WInote" else "Gagal menyimpan gambar")
                }
            }
        }
    }

    /**
     * Simpan karya sebagai PDF satu halaman di Download/WInote. Gambarnya disimpan utuh (resolusi penuh);
     * ukuran halaman mengikuti DPI karya (mis. A4 2480 × 3508 px pada 300 DPI = halaman A4).
     */
    fun exportPdf() {
        if (modalActive()) return
        busy = "Membuat PDF…"
        val bgc = if (bgVisible) bgColor else Color.WHITE
        gl { e ->
            val bmp = e.exportBitmap(bgc)
            if (bmp == null) {
                main.post { busy = null }
                return@gl
            }
            bg.execute {
                val ok = runCatching {
                    val k = 72f / docDpi.coerceAtLeast(1)
                    val pw = max(1, (bmp.width * k).roundToInt())
                    val ph = max(1, (bmp.height * k).roundToInt())
                    val doc = android.graphics.pdf.PdfDocument()
                    try {
                        val page = doc.startPage(android.graphics.pdf.PdfDocument.PageInfo.Builder(pw, ph, 1).create())
                        page.canvas.drawBitmap(
                            bmp, null, android.graphics.RectF(0f, 0f, pw.toFloat(), ph.toFloat()),
                            android.graphics.Paint(android.graphics.Paint.FILTER_BITMAP_FLAG),
                        )
                        doc.finishPage(page)
                        val values = ContentValues().apply {
                            put(MediaStore.MediaColumns.DISPLAY_NAME, "${safeName()}-${stamp()}.pdf")
                            put(MediaStore.MediaColumns.MIME_TYPE, "application/pdf")
                            put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/WInote")
                            put(MediaStore.MediaColumns.IS_PENDING, 1)
                        }
                        val resolver = app.contentResolver
                        val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values) ?: error("Folder menolak")
                        resolver.openOutputStream(uri)?.use { out -> doc.writeTo(out) } ?: error("Tidak bisa menulis")
                        values.clear()
                        values.put(MediaStore.MediaColumns.IS_PENDING, 0)
                        resolver.update(uri, values, null, null)
                    } finally {
                        doc.close()
                    }
                }.isSuccess
                bmp.recycle()
                main.post {
                    busy = null
                    showFlash(if (ok) "PDF tersimpan di Download/WInote" else "Gagal membuat PDF")
                }
            }
        }
    }

    fun exportPsd() {
        if (modalActive()) return
        busy = "Membuat file PSD…"
        val dir = File(app.cacheDir, "ekspor").apply { mkdirs() }
        val file = File(dir, "${safeName()}-${stamp()}.psd")
        gl { e ->
            val ok = runCatching { e.exportPsd(file) }.getOrDefault(false)
            if (!ok) {
                main.post { busy = null; showFlash("Gagal membuat PSD") }
                return@gl
            }
            bg.execute {
                val saved = runCatching {
                    val values = ContentValues().apply {
                        put(MediaStore.MediaColumns.DISPLAY_NAME, file.name)
                        put(MediaStore.MediaColumns.MIME_TYPE, "image/vnd.adobe.photoshop")
                        put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/WInote")
                        put(MediaStore.MediaColumns.IS_PENDING, 1)
                    }
                    val resolver = app.contentResolver
                    val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values) ?: error("Folder menolak")
                    resolver.openOutputStream(uri)?.use { out -> FileInputStream(file).use { it.copyTo(out, 256 * 1024) } }
                    values.clear()
                    values.put(MediaStore.MediaColumns.IS_PENDING, 0)
                    resolver.update(uri, values, null, null)
                }.isSuccess
                file.delete()
                main.post {
                    busy = null
                    showFlash(if (saved) "PSD tersimpan di Download/WInote" else "Gagal menyimpan PSD")
                }
            }
        }
    }

    fun sharePng(launch: (Intent) -> Unit) {
        if (modalActive()) return
        busy = "Menyiapkan…"
        val bgc = if (bgVisible) bgColor else null
        gl { e ->
            val bmp = e.exportBitmap(bgc)
            if (bmp == null) {
                main.post { busy = null }
                return@gl
            }
            bg.execute {
                val dir = File(app.cacheDir, "bagikan").apply { mkdirs(); listFiles()?.forEach { it.delete() } }
                val f = File(dir, "${safeName()}.png")
                val dpi = docDpi
                val ok = runCatching { DpiOutputStream(f.outputStream(), dpi, false).use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) } }.isSuccess
                bmp.recycle()
                main.post {
                    busy = null
                    if (!ok) {
                        showFlash("Gagal menyiapkan gambar")
                        return@post
                    }
                    val uri = FileProvider.getUriForFile(app, app.packageName + ".files", f)
                    val send = Intent(Intent.ACTION_SEND).apply {
                        type = "image/png"
                        putExtra(Intent.EXTRA_STREAM, uri)
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }
                    launch(Intent.createChooser(send, "Bagikan karya"))
                }
            }
        }
    }

    fun renameArtwork(name: String) {
        docName = name
        gl { it.renameDoc(name) }
        onContentChanged()
    }

    fun estimateLayerLimit(w: Int, h: Int): Int {
        val am = app.getSystemService(Context.ACTIVITY_SERVICE) as android.app.ActivityManager
        val mi = android.app.ActivityManager.MemoryInfo()
        am.getMemoryInfo(mi)
        val per = w.toLong() * h * 4
        if (per <= 0) return 0
        return (((mi.totalMem * 0.22).toLong() - per * 7) / per).toInt().coerceIn(3, 120)
    }

    fun clampZoom(): Float = max(0.01f, viewZoom)
}
