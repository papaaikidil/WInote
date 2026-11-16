package id.goresan.ui

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Shader
import android.os.SystemClock
import android.view.MotionEvent
import android.view.View
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.view.LifecycleCameraController
import androidx.camera.view.PreviewView
import androidx.camera.view.transform.CoordinateTransform
import androidx.camera.view.transform.ImageProxyTransformFactory
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.facemesh.FaceMeshDetection
import com.google.mlkit.vision.facemesh.FaceMeshDetectorOptions
import id.goresan.Diag
import id.goresan.engine.RawPoint
import kotlinx.coroutines.delay
import org.json.JSONObject
import kotlin.math.abs
import kotlin.math.hypot

/** UV wajah kanonik (468 titik, v = 0 di bawah) dan segitiganya (MediaPipe, Apache 2.0; lihat assets/wajah). */
object FaceTopology {
    @Volatile private var data: Pair<FloatArray, IntArray>? = null

    fun get(ctx: Context): Pair<FloatArray, IntArray> = data ?: run {
        val j = JSONObject(ctx.assets.open("wajah/wajah.json").use { it.readBytes().toString(Charsets.UTF_8) })
        val u = j.getJSONArray("uv")
        val t = j.getJSONArray("tri")
        val uv = FloatArray(u.length()) { u.getDouble(it).toFloat() }
        val tri = IntArray(t.length()) { t.getInt(it) }
        (uv to tri).also { data = it }
    }
}

/**
 * Lapisan Face Paint: tekstur kanvas digambar di jaring wajah (titik dari ML Kit, koordinat tampilan), dan sentuhan
 * pada wajah diubah ke titik kanvas lewat UV segitiga yang disentuh, lalu dikirim ke mesin sebagai goresan biasa.
 */
@SuppressLint("ViewConstructor")
class FaceOverlay(ctx: Context, private val state: AppState) : View(ctx) {
    private val uv: FloatArray
    private val tri: IntArray
    private val n: Int

    /** Titik wajah (x, y, z) dalam piksel tampilan; null = wajah tidak terlihat. */
    private var pts: FloatArray? = null
    private var lastSeen = 0L
    private val verts: FloatArray
    private val texs: FloatArray
    private var idx = ShortArray(0)
    private var idxCount = 0
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private var tex: Bitmap? = null
    /** Tanda segitiga yang menghadap kamera (tanda luas di layar), dihitung tiap bingkai. */
    private var front = BooleanArray(0)
    private val triZ: FloatArray

    init {
        val (u, t) = FaceTopology.get(ctx)
        uv = u
        tri = t
        n = u.size / 2
        verts = FloatArray(n * 2)
        texs = FloatArray(n * 2)
        front = BooleanArray(t.size / 3)
        triZ = FloatArray(t.size / 3)
    }

    val faceVisible get() = pts != null

    fun setPoints(p: FloatArray?) {
        if (p != null) lastSeen = SystemClock.uptimeMillis()
        // Wajah hilang sebentar (kabur/bergerak cepat): pertahankan posisi terakhir sejenak supaya tidak berkedip.
        if (p == null && SystemClock.uptimeMillis() - lastSeen < 300) return
        pts = p
        if (p != null) prepare(p)
        invalidate()
    }

    fun setTexture(b: Bitmap?) {
        if (b === tex) return
        tex = b
        paint.shader = if (b != null) BitmapShader(b, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP) else null
        if (b != null) for (i in 0 until n) {
            texs[i * 2] = uv[i * 2] * b.width
            texs[i * 2 + 1] = (1f - uv[i * 2 + 1]) * b.height
        }
        invalidate()
    }

    /** Segitiga yang menghadap kamera, diurutkan dari yang terjauh (dilukis lebih dulu). */
    private fun prepare(p: FloatArray) {
        for (i in 0 until n) { verts[i * 2] = p[i * 3]; verts[i * 2 + 1] = p[i * 3 + 1] }
        val nt = tri.size / 3
        var pos = 0
        val area = FloatArray(nt)
        for (t in 0 until nt) {
            val a = tri[t * 3]; val b = tri[t * 3 + 1]; val c = tri[t * 3 + 2]
            area[t] = (p[b * 3] - p[a * 3]) * (p[c * 3 + 1] - p[a * 3 + 1]) - (p[c * 3] - p[a * 3]) * (p[b * 3 + 1] - p[a * 3 + 1])
            if (area[t] > 0) pos++
            triZ[t] = p[a * 3 + 2] + p[b * 3 + 2] + p[c * 3 + 2]
        }
        // Sebagian besar segitiga menghadap kamera: tanda luas mayoritas = depan.
        val sign = if (pos * 2 >= nt) 1f else -1f
        val order = ArrayList<Int>(nt)
        for (t in 0 until nt) {
            front[t] = area[t] * sign > 0
            if (front[t]) order.add(t)
        }
        order.sortByDescending { triZ[it] }
        if (idx.size < order.size * 3) idx = ShortArray(order.size * 3)
        var k = 0
        for (t in order) { idx[k++] = tri[t * 3].toShort(); idx[k++] = tri[t * 3 + 1].toShort(); idx[k++] = tri[t * 3 + 2].toShort() }
        idxCount = k
    }

    override fun onDraw(canvas: Canvas) {
        drawFace(canvas)
    }

    fun drawFace(canvas: Canvas) {
        if (pts == null || tex == null || idxCount == 0) return
        canvas.drawVertices(Canvas.VertexMode.TRIANGLES, n * 2, verts, 0, texs, 0, null, 0, idx, 0, idxCount, paint)
    }

    /** Titik tampilan → titik kanvas (lewat UV segitiga terdepan yang memuatnya), null bila di luar wajah. */
    private fun toCanvas(x: Float, y: Float): FloatArray? {
        val p = pts ?: return null
        var best = -1
        var bz = Float.MAX_VALUE
        var bu = 0f
        var bv = 0f
        for (t in 0 until tri.size / 3) {
            if (!front[t]) continue
            val a = tri[t * 3]; val b = tri[t * 3 + 1]; val c = tri[t * 3 + 2]
            val ax = p[a * 3]; val ay = p[a * 3 + 1]
            val v0x = p[b * 3] - ax; val v0y = p[b * 3 + 1] - ay
            val v1x = p[c * 3] - ax; val v1y = p[c * 3 + 1] - ay
            val v2x = x - ax; val v2y = y - ay
            val den = v0x * v1y - v1x * v0y
            if (abs(den) < 1e-6f) continue
            val s = (v2x * v1y - v1x * v2y) / den
            val r = (v0x * v2y - v2x * v0y) / den
            if (s < -0.01f || r < -0.01f || s + r > 1.02f) continue
            if (triZ[t] < bz) {
                bz = triZ[t]
                best = t
                val w0 = 1f - s - r
                bu = w0 * uv[a * 2] + s * uv[b * 2] + r * uv[c * 2]
                bv = w0 * uv[a * 2 + 1] + s * uv[b * 2 + 1] + r * uv[c * 2 + 1]
            }
        }
        if (best < 0) return null
        return floatArrayOf(bu * state.docW, (1f - bv) * state.docH)
    }

    // ---- goresan ----
    private var drawing = false
    private var drawId = -1
    private var downAt = 0L
    private var stylus = false
    private var lastX = 0f
    private var lastY = 0f
    private var twoAt = 0L

    private fun send(kind: Int, cx: Float, cy: Float, e: MotionEvent, i: Int) {
        val v = state.canvasView ?: return
        var flags = 0
        if (stylus) flags = flags or RawPoint.PRESSURE
        if (e.getToolType(i) == MotionEvent.TOOL_TYPE_ERASER) flags = flags or RawPoint.ERASER
        v.engine.input.add(
            RawPoint(
                kind, cx, cy, e.getPressure(i), e.getAxisValue(MotionEvent.AXIS_TILT, i),
                e.getAxisValue(MotionEvent.AXIS_ORIENTATION, i), e.eventTime, flags,
            ),
        )
        v.requestRender()
        state.faceDirty = true
    }

    private fun point(e: MotionEvent, i: Int, kind: Int) {
        val c = toCanvas(e.getX(i), e.getY(i))
        when {
            kind == RawPoint.BEGIN -> if (c != null) { send(RawPoint.BEGIN, c[0], c[1], e, i); drawing = true; lastX = c[0]; lastY = c[1] }
            kind == RawPoint.END -> { if (drawing) send(RawPoint.END, c?.get(0) ?: lastX, c?.get(1) ?: lastY, e, i); drawing = false }
            c == null -> if (drawing) { send(RawPoint.END, lastX, lastY, e, i); drawing = false }
            !drawing -> { send(RawPoint.BEGIN, c[0], c[1], e, i); drawing = true; lastX = c[0]; lastY = c[1] }
            else -> {
                // Lompatan UV besar (melewati tepi segitiga ke bagian lain tekstur): goresan baru.
                if (hypot(c[0] - lastX, c[1] - lastY) > state.docW * 0.12f) {
                    send(RawPoint.END, lastX, lastY, e, i)
                    send(RawPoint.BEGIN, c[0], c[1], e, i)
                } else send(RawPoint.MOVE, c[0], c[1], e, i)
                lastX = c[0]; lastY = c[1]
            }
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(e: MotionEvent): Boolean {
        if (state.closePanelsFromCanvas()) return true
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                drawId = e.getPointerId(0)
                downAt = SystemClock.uptimeMillis()
                stylus = e.getToolType(0) == MotionEvent.TOOL_TYPE_STYLUS || e.getToolType(0) == MotionEvent.TOOL_TYPE_ERASER
                drawing = false
                point(e, 0, RawPoint.BEGIN)
            }
            MotionEvent.ACTION_POINTER_DOWN -> {
                // Jari kedua cepat setelah yang pertama: gerakan dua jari (ketuk = urungkan), goresan dibatalkan.
                if (!stylus && SystemClock.uptimeMillis() - downAt < 300) {
                    if (drawing) state.canvasView?.engine?.input?.add(RawPoint(RawPoint.CANCEL, 0f, 0f, 0f, 0f, 0f, e.eventTime, 0))
                    drawing = false
                    drawId = -1
                    twoAt = SystemClock.uptimeMillis()
                }
            }
            MotionEvent.ACTION_MOVE -> {
                val i = e.findPointerIndex(drawId)
                if (i >= 0) {
                    for (h in 0 until e.historySize) {
                        val c = toCanvas(e.getHistoricalX(i, h), e.getHistoricalY(i, h))
                        if (c != null && drawing && hypot(c[0] - lastX, c[1] - lastY) <= state.docW * 0.12f) {
                            send(RawPoint.MOVE, c[0], c[1], e, i)
                            lastX = c[0]; lastY = c[1]
                        }
                    }
                    point(e, i, RawPoint.MOVE)
                }
            }
            MotionEvent.ACTION_POINTER_UP -> if (e.getPointerId(e.actionIndex) == drawId) {
                point(e, e.actionIndex, RawPoint.END)
                drawId = -1
            }
            MotionEvent.ACTION_UP -> {
                val i = e.findPointerIndex(drawId)
                if (i >= 0) point(e, i, RawPoint.END)
                if (twoAt > 0 && SystemClock.uptimeMillis() - twoAt < 400) state.undo()
                twoAt = 0
                drawId = -1
            }
            MotionEvent.ACTION_CANCEL -> {
                if (drawing) state.canvasView?.engine?.input?.add(RawPoint(RawPoint.CANCEL, 0f, 0f, 0f, 0f, 0f, e.eventTime, 0))
                drawing = false
                drawId = -1
            }
        }
        return true
    }
}

/** Tampilan Face Paint: kamera depan, jaring wajah dari ML Kit, dan cat dari kanvas di atas wajah. */
@Composable
fun FacePaintLayer(state: AppState) {
    val ctx = LocalContext.current
    var granted by remember {
        mutableStateOf(ContextCompat.checkSelfPermission(ctx, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED)
    }
    val ask = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted = it }
    LaunchedEffect(Unit) { if (!granted) ask.launch(Manifest.permission.CAMERA) }
    // Tekstur wajah diambil ulang dari kanvas bila berubah.
    LaunchedEffect(Unit) {
        state.faceDirty = true
        while (true) {
            state.refreshFaceTex()
            delay(110)
        }
    }
    Box(Modifier.fillMaxSize().background(Color.Black)) {
        if (!granted) {
            Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Face Paint memakai kamera depan", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(8.dp))
                Text(
                    "Gambar kamera hanya diolah di tablet ini untuk menemukan wajah, tidak disimpan atau dikirim.",
                    color = Muted, fontSize = 13.sp, textAlign = TextAlign.Center, modifier = Modifier.width(420.dp),
                )
                Spacer(Modifier.height(14.dp))
                Button(
                    onClick = { ask.launch(Manifest.permission.CAMERA) },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFF6C453), contentColor = Color(0xFF3A2A00)),
                ) { Text("Izinkan kamera") }
            }
            return@Box
        }
        FaceCamera(state)
    }
}

@Composable
private fun FaceCamera(state: AppState) {
    val ctx = LocalContext.current
    val owner = LocalLifecycleOwner.current
    val preview = remember { PreviewView(ctx).apply { implementationMode = PreviewView.ImplementationMode.COMPATIBLE; scaleType = PreviewView.ScaleType.FILL_CENTER } }
    val overlay = remember { FaceOverlay(ctx, state) }
    var seen by remember { mutableStateOf(false) }
    var started by remember { mutableStateOf(false) }
    var noCam by remember { mutableStateOf(false) }
    LaunchedEffect(state.faceTex) { overlay.setTexture(state.faceTex) }
    DisposableEffect(owner) {
        val main = ContextCompat.getMainExecutor(ctx)
        val detector = FaceMeshDetection.getClient(
            FaceMeshDetectorOptions.Builder().setUseCase(FaceMeshDetectorOptions.FACE_MESH).build(),
        )
        val factory = ImageProxyTransformFactory().apply { isUsingRotationDegrees = true }
        val controller = LifecycleCameraController(ctx)
        controller.cameraSelector = CameraSelector.DEFAULT_FRONT_CAMERA
        controller.setImageAnalysisAnalyzer(main) { proxy ->
            val media = proxy.image
            val target = preview.outputTransform
            if (media == null || target == null) {
                proxy.close()
                return@setImageAnalysisAnalyzer
            }
            val source = factory.getOutputTransform(proxy)
            val input = InputImage.fromMediaImage(media, proxy.imageInfo.rotationDegrees)
            detector.process(input)
                .addOnSuccessListener { meshes ->
                    val m = meshes.firstOrNull()
                    if (m == null) {
                        overlay.setPoints(null)
                        seen = false
                    } else {
                        val all = m.allPoints
                        val p = FloatArray(468 * 3)
                        val xy = FloatArray(468 * 2)
                        for (q in all) {
                            val i = q.index
                            if (i !in 0 until 468) continue
                            xy[i * 2] = q.position.x; xy[i * 2 + 1] = q.position.y
                            p[i * 3 + 2] = q.position.z
                        }
                        CoordinateTransform(source, target).mapPoints(xy)
                        for (i in 0 until 468) { p[i * 3] = xy[i * 2]; p[i * 3 + 1] = xy[i * 2 + 1] }
                        overlay.setPoints(p)
                        seen = true
                    }
                }
                .addOnCompleteListener { proxy.close() }
        }
        val ok = runCatching { controller.bindToLifecycle(owner); preview.controller = controller }
        ok.onFailure { Diag.log("Kamera Face Paint gagal: ${it.message}") }
        started = ok.isSuccess
        controller.initializationFuture.addListener({
            val has = runCatching { controller.hasCamera(CameraSelector.DEFAULT_FRONT_CAMERA) }.getOrDefault(false)
            noCam = !has
            Diag.log(if (has) "Face Paint: kamera depan siap" else "Face Paint: kamera depan tidak ditemukan")
        }, main)
        state.facePhoto = {
            val cam = preview.bitmap
            if (cam != null) {
                val out = cam.copy(Bitmap.Config.ARGB_8888, true)
                overlay.drawFace(Canvas(out))
                state.saveFacePhoto(out)
            } else state.showFlash("Kamera belum siap")
        }
        onDispose {
            state.facePhoto = null
            runCatching { controller.unbind() }
            runCatching { detector.close() }
        }
    }
    Box(Modifier.fillMaxSize()) {
        AndroidView(factory = { preview }, modifier = Modifier.fillMaxSize())
        AndroidView(factory = { overlay }, modifier = Modifier.fillMaxSize())
        if (!seen) {
            Text(
                when {
                    !started -> "Kamera tidak bisa dibuka"
                    noCam -> "Kamera depan tidak ditemukan di perangkat ini"
                    else -> "Arahkan wajah ke kamera depan"
                },
                color = Color.White,
                fontSize = 15.sp,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 90.dp)
                    .background(Color(0x99000000), androidx.compose.foundation.shape.RoundedCornerShape(12.dp))
                    .padding(horizontal = 14.dp, vertical = 8.dp),
            )
        }
    }
}

@Suppress("unused")
private val keepArrangement = Arrangement.Center
