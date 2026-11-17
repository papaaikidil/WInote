package id.goresan.canvas

import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Matrix
import android.graphics.PointF
import android.opengl.GLSurfaceView
import android.os.Build
import android.os.SystemClock
import android.view.MotionEvent
import android.view.Surface
import android.view.SurfaceHolder
import android.view.ViewConfiguration
import id.goresan.Diag
import id.goresan.engine.Adj
import id.goresan.engine.Cam3D
import id.goresan.engine.DrawingGuide
import id.goresan.engine.Engine
import id.goresan.engine.RawPoint
import id.goresan.engine.ToolKind
import id.goresan.ui.AppState
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * Kanvas OpenGL. Menangani pena (tekanan, kemiringan, tombol), jari (geser, cubit, putar, ketukan),
 * dan meneruskan titik pena ke mesin tanpa menunggu frame (unbuffered dispatch).
 */
@SuppressLint("ViewConstructor")
class CanvasView(ctx: Context, private val state: AppState) : GLSurfaceView(ctx) {

    val engine = Engine(ctx.applicationContext, state)
    private val density = resources.displayMetrics.density
    private val touchSlop = ViewConfiguration.get(ctx).scaledTouchSlop.toFloat()

    /** Kanvas → layar. */
    private val view = Matrix()
    private val inv = Matrix()
    private val vals = FloatArray(9)
    private var canvasW = 0
    private var canvasH = 0
    private var fitted = false

    init {
        setEGLContextClientVersion(3)
        setEGLConfigChooser(8, 8, 8, 8, 0, 0)
        // Karya Display P3 di layar gamut luas: permukaan dibuat dalam ruang warna Display P3 bila didukung.
        setEGLWindowSurfaceFactory(object : GLSurfaceView.EGLWindowSurfaceFactory {
            override fun createWindowSurface(
                egl: javax.microedition.khronos.egl.EGL10,
                display: javax.microedition.khronos.egl.EGLDisplay,
                config: EGLConfig,
                nativeWindow: Any?,
            ): javax.microedition.khronos.egl.EGLSurface? {
                var surf: javax.microedition.khronos.egl.EGLSurface? = null
                var p3 = false
                if (state.wantP3) {
                    val ext = runCatching { egl.eglQueryString(display, javax.microedition.khronos.egl.EGL10.EGL_EXTENSIONS) }.getOrNull().orEmpty()
                    if (ext.contains("EGL_EXT_gl_colorspace_display_p3")) {
                        val attrs = intArrayOf(0x309D, 0x3363, javax.microedition.khronos.egl.EGL10.EGL_NONE)
                        surf = runCatching { egl.eglCreateWindowSurface(display, config, nativeWindow, attrs) }.getOrNull()
                        if (surf == null || surf == javax.microedition.khronos.egl.EGL10.EGL_NO_SURFACE) surf = null else p3 = true
                    }
                }
                if (surf == null) {
                    surf = try {
                        egl.eglCreateWindowSurface(display, config, nativeWindow, null)
                    } catch (e: IllegalArgumentException) {
                        Diag.log("Permukaan kanvas gagal: ${e.message}")
                        null
                    }
                }
                engine.surfaceP3 = p3
                Diag.log("Permukaan kanvas: ${if (p3) "Display P3" else "sRGB"}")
                return surf
            }

            override fun destroySurface(
                egl: javax.microedition.khronos.egl.EGL10,
                display: javax.microedition.khronos.egl.EGLDisplay,
                surface: javax.microedition.khronos.egl.EGLSurface,
            ) {
                egl.eglDestroySurface(display, surface)
            }
        })
        preserveEGLContextOnPause = true
        engine.requestRender = { requestRender() }
        setRenderer(object : GLSurfaceView.Renderer {
            override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
                Diag.glThread = Thread.currentThread()
                Diag.log("GL: konteks dibuat")
                val t0 = SystemClock.uptimeMillis()
                Diag.glBusySince = t0
                try {
                    engine.onSurfaceCreated()
                } finally {
                    Diag.glBusySince = 0L
                }
                Diag.log("GL: siap (${SystemClock.uptimeMillis() - t0} ms)")
            }

            override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
                Diag.log("GL: ukuran permukaan ${width}x$height")
                engine.onSurfaceChanged(width, height)
            }

            override fun onDrawFrame(gl: GL10?) {
                val t0 = SystemClock.uptimeMillis()
                Diag.glBusySince = t0
                try {
                    engine.onDrawFrame()
                } finally {
                    Diag.glBusySince = 0L
                }
                val dt = SystemClock.uptimeMillis() - t0
                if (dt > 80) Diag.log("Frame lambat: $dt ms")
            }
        })
        renderMode = GLSurfaceView.RENDERMODE_WHEN_DIRTY
    }

    override fun surfaceDestroyed(holder: SurfaceHolder) {
        Diag.log("Permukaan kanvas dilepas")
        super.surfaceDestroyed(holder)
    }

    override fun surfaceCreated(holder: SurfaceHolder) {
        Diag.log("Permukaan kanvas dibuat")
        super.surfaceCreated(holder)
        if (Build.VERSION.SDK_INT >= 30) {
            runCatching { holder.surface.setFrameRate(144f, Surface.FRAME_RATE_COMPATIBILITY_DEFAULT) }
        }
    }

    /** Jalankan perintah di thread GL pada frame berikutnya. */
    fun gl(block: (Engine) -> Unit) {
        engine.tasks.add(block)
        requestRender()
    }

    // ================= tampilan =================

    fun setCanvasSize(w: Int, h: Int) {
        canvasW = w
        canvasH = h
        fitted = false
        if (width > 0 && height > 0) fit(false)
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        if (canvasW <= 0) return
        if (!fitted || oldw <= 0) {
            fit(false)
        } else {
            // Tablet diputar: pertahankan titik tengah kanvas di tengah layar.
            view.postTranslate((w - oldw) / 2f, (h - oldh) / 2f)
            pushView()
        }
    }

    val zoom: Float
        get() {
            view.getValues(vals)
            return sqrt(vals[0] * vals[0] + vals[3] * vals[3])
        }

    private val rotationDeg: Float
        get() {
            view.getValues(vals)
            return Math.toDegrees(atan2(vals[3].toDouble(), vals[0].toDouble())).toFloat()
        }

    private fun fitMatrix(out: Matrix) {
        val m = 24f * density
        val top = 56f * density
        val aw = width - 2 * m
        val ah = height - top - m
        val s = min(aw / canvasW, ah / canvasH)
        out.reset()
        out.postScale(s, s)
        out.postTranslate((width - canvasW * s) / 2f, top + (ah - canvasH * s) / 2f)
    }

    fun fit(animate: Boolean) {
        if (canvasW <= 0 || width <= 0) return
        val target = Matrix()
        fitMatrix(target)
        fitted = true
        if (!animate) {
            view.set(target)
            pushView()
            return
        }
        val a = FloatArray(9)
        val b = FloatArray(9)
        view.getValues(a)
        target.getValues(b)
        val tmp = FloatArray(9)
        ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 220
            addUpdateListener { an ->
                val f = an.animatedValue as Float
                for (i in 0 until 9) tmp[i] = a[i] + (b[i] - a[i]) * f
                view.setValues(tmp)
                pushView()
            }
            start()
        }
    }

    /** Balik tampilan kanvas kiri-kanan (hanya tampilan, gambar tidak berubah). */
    fun flipView() {
        view.postScale(-1f, 1f, width / 2f, height / 2f)
        pushView()
    }

    private fun pushView() {
        view.getValues(vals)
        engine.view = vals.copyOf()
        view.invert(inv)
        state.viewZoom = zoom
        requestRender()
    }

    private val mapTmp = FloatArray(2)
    fun toCanvas(x: Float, y: Float): PointF {
        if (state.view3D) {
            // Tampilan 3D: titik kanvas lewat UV model (di luar model = jauh di luar kanvas).
            val c = engine.canvasAt3D(x, y) ?: return PointF(-1e6f, -1e6f)
            return PointF(c[0], c[1])
        }
        mapTmp[0] = x; mapTmp[1] = y
        inv.mapPoints(mapTmp)
        return PointF(mapTmp[0], mapTmp[1])
    }

    // ================= sentuhan =================

    private enum class Mode { NONE, DRAW, NAV, PAN, HOLD_PICK, PICK, IGNORE, XF_DRAG, XF_PINCH, LIQ, CENTER, CLONE_SRC, SEL, GUIDE, TEXT }

    private var mode = Mode.NONE
    private var drawId = -1
    private var drawFinger = false
    private var drawStart = 0L
    private var lastStylus = 0L

    /** Sentuhan jari dalam selang ini setelah pena menyentuh/melayang dianggap telapak tangan. */
    private val palmMs = 1500L

    private fun penRecent() = SystemClock.uptimeMillis() - lastStylus < palmMs

    /** Saat terakhir pena benar-benar menyentuh layar (melayang tidak dihitung). */
    private var lastPenTouch = 0L

    /** Pena sedang melayang di atas layar (antara HOVER_ENTER dan HOVER_EXIT). */
    private var penHovering = false

    /** Pena baru saja menyentuh layar: ketukan jari dalam selang ini dianggap telapak tangan. */
    private fun penTouchRecent() = SystemClock.uptimeMillis() - lastPenTouch < 250L
    private var hasTilt = false
    private var drawEraser = false

    private fun isStylus(e: MotionEvent, i: Int): Boolean {
        val t = e.getToolType(i)
        return t == MotionEvent.TOOL_TYPE_STYLUS || t == MotionEvent.TOOL_TYPE_ERASER
    }

    /**
     * Penunjuk sistem untuk pena yang melayang (titik/bulatan dari Android/HyperOS) disembunyikan di atas kanvas;
     * lingkaran kuas milik aplikasi diatur sendiri lewat Aksi › "Lingkaran kuas saat pena melayang".
     */
    override fun onResolvePointerIcon(event: MotionEvent, pointerIndex: Int): android.view.PointerIcon? {
        val t = event.getToolType(pointerIndex)
        if (t == MotionEvent.TOOL_TYPE_STYLUS || t == MotionEvent.TOOL_TYPE_ERASER) {
            return android.view.PointerIcon.getSystemIcon(context, android.view.PointerIcon.TYPE_NULL)
        }
        return super.onResolvePointerIcon(event, pointerIndex)
    }

    override fun onHoverEvent(event: MotionEvent): Boolean {
        if (event.pointerCount > 0 && isStylus(event, 0)) {
            lastStylus = SystemClock.uptimeMillis()
            penHovering = event.actionMasked != MotionEvent.ACTION_HOVER_EXIT
            if (event.actionMasked == MotionEvent.ACTION_HOVER_EXIT || !state.prefs.showCursor) {
                if (engine.hover != null) { engine.hover = null; requestRender() }
            } else {
                engine.hover = floatArrayOf(event.x, event.y)
                requestRender()
            }
        }
        return true
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(e: MotionEvent): Boolean {
        if (canvasW <= 0) return true
        for (i in 0 until e.pointerCount) if (isStylus(e, i)) {
            lastPenTouch = SystemClock.uptimeMillis()
            penHovering = false
            break
        }
        // Pena sedang menggores, sementara sentuhan datang dari perangkat lain sebagai aliran terpisah (Android 15+):
        // itu jari/telapak tangan. Jangan ganggu goresan pena; jari yang menyentuh saat bentuk ditahan = bentuk presisi.
        if (mode == Mode.DRAW && !drawFinger && drawDevice != -1 && e.deviceId != drawDevice) {
            if (e.actionMasked == MotionEvent.ACTION_DOWN && !isStylus(e, 0) && e.getTouchMajor(0) <= 60f * density) {
                constrainShape()
            }
            return true
        }
        trackMultiTap(e)
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> onDown(e)
            MotionEvent.ACTION_POINTER_DOWN -> onPointerDown(e)
            MotionEvent.ACTION_MOVE -> onMove(e)
            MotionEvent.ACTION_POINTER_UP -> onPointerUp(e)
            MotionEvent.ACTION_UP -> onUp(e)
            MotionEvent.ACTION_CANCEL -> onCancel(e)
        }
        return true
    }

    private fun onDown(e: MotionEvent) {
        cancelHold()
        val stylus = isStylus(e, 0)
        if (engine.hover != null) { engine.hover = null; requestRender() }
        if (stylus) {
            lastStylus = SystemClock.uptimeMillis()
            state.onStylusSeen()
        }
        if (state.closePanelsFromCanvas()) {
            mode = Mode.IGNORE
            return
        }
        if (state.guideEditing) {
            guideDown(e, 0)
            return
        }
        if (state.textEditing) {
            textDown(e, 0)
            return
        }
        if (state.paperEditing) {
            // Panel kertas terbuka: kanvas hanya digeser/diperbesar.
            mode = Mode.PAN
            panId = e.getPointerId(0)
            panLast.set(e.getX(0), e.getY(0))
            return
        }
        if (state.pickArmed) {
            mode = Mode.PICK
            pick(e.getX(0), e.getY(0))
            return
        }
        if (state.view3D) {
            // Tampilan 3D: sentuh di luar model = putar model; di model = melukis.
            if (!engine.hit3D(e.getX(0), e.getY(0))) {
                mode = Mode.PAN
                panId = e.getPointerId(0)
                panLast.set(e.getX(0), e.getY(0))
                return
            }
            if (stylus) {
                requestUnbufferedDispatch(e)
                beginDraw(e, 0, finger = false)
                return
            }
            if (penRecent() || e.getTouchMajor(0) > 60f * density) {
                mode = Mode.IGNORE
                return
            }
            downX = e.getX(0); downY = e.getY(0)
            if (state.prefs.fingerDraws) {
                requestUnbufferedDispatch(e)
                beginDraw(e, 0, finger = true)
                armHold()
            } else {
                mode = Mode.PAN
                panId = e.getPointerId(0)
                panLast.set(e.getX(0), e.getY(0))
            }
            return
        }
        if (state.xfActive) {
            // Palm saat pena baru dipakai tidak ikut menggeser kotak.
            if (!stylus && (penRecent() || e.getTouchMajor(0) > 60f * density)) {
                mode = Mode.IGNORE
                return
            }
            xfBegin(e, 0, useHandles = true)
            return
        }
        // ColorDrop yang masih terbuka disimpan, lalu sentuhan ini diproses seperti biasa (langsung bisa menggores).
        if (state.fillActive) state.fillDone(true)
        val k = state.adjustKind
        if (k >= 0 && k != Adj.CLONE) {
            val palm = !stylus && (penRecent() || e.getTouchMajor(0) > 60f * density)
            when {
                palm -> mode = Mode.IGNORE
                k == Adj.LIQUIFY || state.adjPenActive -> beginLiq(e, 0, finger = !stylus)
                usesCenter() -> { mode = Mode.CENTER; centerAt(e.getX(0), e.getY(0)) }
                stylus -> mode = Mode.IGNORE
                else -> {
                    // Penyesuaian biasa: jari tetap bisa menggeser/memperbesar kanvas untuk melihat hasilnya.
                    mode = Mode.PAN
                    panId = e.getPointerId(0)
                    panLast.set(e.getX(0), e.getY(0))
                }
            }
            return
        }
        if (state.selTool) {
            // Alat pilihan: pena/jari membuat pilihan (telapak tangan diabaikan).
            if (!stylus && (penRecent() || e.getTouchMajor(0) > 60f * density)) {
                mode = Mode.IGNORE
                return
            }
            beginSel(e, 0)
            return
        }
        if (k == Adj.CLONE && (state.cloneArmed || (!stylus && nearCloneSource(e.getX(0), e.getY(0))))) {
            mode = Mode.CLONE_SRC
            cloneAt(e.getX(0), e.getY(0))
            return
        }
        if (stylus) {
            requestUnbufferedDispatch(e)
            beginDraw(e, 0, finger = false)
            return
        }
        // Jari menyentuh saat pena baru saja dipakai/melayang: anggap telapak tangan.
        if (penRecent() || e.getTouchMajor(0) > 60f * density) {
            mode = Mode.IGNORE
            return
        }
        downX = e.getX(0); downY = e.getY(0)
        if (state.prefs.fingerDraws) {
            requestUnbufferedDispatch(e)
            beginDraw(e, 0, finger = true)
        } else {
            mode = Mode.PAN
            panId = e.getPointerId(0)
            panLast.set(e.getX(0), e.getY(0))
        }
        armHold()
    }

    private fun onPointerDown(e: MotionEvent) {
        cancelHold()
        val i = e.actionIndex
        val stylus = isStylus(e, i)
        when (mode) {
            Mode.DRAW -> if (!stylus && engine.shapeActive && e.getTouchMajor(i) <= 60f * density) {
                // Bentuk otomatis sedang ditahan + satu jari menyentuh: jadikan presisi (seperti QuickShape Procreate).
                constrainShape()
            } else {
                if (drawFinger) {
                    // Jari kedua datang cepat (selang yang sama dengan ketukan dua jari): itu gerakan dua jari, bukan
                    // menggambar, jadi titik dari jari pertama dibuang (urungkan tidak "termakan" titik itu).
                    if (SystemClock.uptimeMillis() - drawStart < 400) push(RawPoint.CANCEL, e, 0, -1) else push(RawPoint.END, e, e.findPointerIndex(drawId).coerceAtLeast(0), -1)
                    if (stylus) {
                        requestUnbufferedDispatch(e)
                        beginDraw(e, i, finger = false)
                    } else {
                        startNav(e)
                    }
                }
            }
            Mode.PAN, Mode.NONE -> {
                if (stylus) {
                    requestUnbufferedDispatch(e)
                    beginDraw(e, i, finger = false)
                } else {
                    startNav(e)
                }
            }
            Mode.NAV -> startNav(e)
            Mode.IGNORE -> if (stylus) {
                when {
                    state.selTool -> beginSel(e, i)
                    state.adjustKind == Adj.LIQUIFY || state.adjPenActive -> beginLiq(e, i, finger = false)
                    usesCenter() -> { mode = Mode.CENTER; centerAt(e.getX(i), e.getY(i)) }
                    !state.modalActive() -> {
                        requestUnbufferedDispatch(e)
                        beginDraw(e, i, finger = false)
                    }
                }
            }
            Mode.LIQ -> if (liqFinger && !stylus) {
                // Jari kedua: ini gerakan dua jari (geser/perbesar), bukan liquify. Sapuan mode Pena yang baru saja
                // dimulai dibuang (jari kedua datang hampir bersamaan = gerakan dua jari sejak awal).
                if (liqPen && SystemClock.uptimeMillis() - liqBase < 400) cancelPen() else endLiq(e, e.findPointerIndex(liqId))
                startNav(e)
            }
            Mode.CENTER, Mode.CLONE_SRC -> if (!stylus) startNav(e)
            Mode.SEL -> if (!stylus && !selStylus) {
                // Jari kedua: geser/perbesar kanvas, gerakan pilihan dibatalkan.
                state.selCancelGesture()
                startNav(e)
            }
            Mode.HOLD_PICK, Mode.PICK -> if (stylus && !state.pickArmed) {
                // Telapak tangan yang diam sempat dianggap "tahan untuk pipet": batalkan, pena langsung menggambar.
                state.cancelPick()
                requestUnbufferedDispatch(e)
                beginDraw(e, i, finger = false)
            }
            Mode.XF_DRAG -> if (!stylus && !xfStylus && e.pointerCount >= 2 && xfHandle < 0) {
                // Dua jari: geser + perbesar + putar bersama.
                state.xfDragEnd()
                mode = Mode.XF_PINCH
                startXfPinch(e)
            }
            Mode.XF_PINCH -> startXfPinch(e)
            Mode.GUIDE -> {
                // Jari kedua: geser/perbesar kanvas; seretan titik panduan selesai.
                guideFinish(moved = guideMoved)
                startNav(e)
            }
            Mode.TEXT -> {
                textAbort()
                startNav(e)
            }
            else -> Unit
        }
    }

    private fun onMove(e: MotionEvent) {
        when (mode) {
            Mode.DRAW -> {
                val i = e.findPointerIndex(drawId)
                if (i < 0) return
                // Tombol pena ditekan saat bentuk ditahan: bentuk presisi.
                val btn = e.buttonState and (MotionEvent.BUTTON_STYLUS_PRIMARY or MotionEvent.BUTTON_STYLUS_SECONDARY)
                if (btn != 0 && drawButtons == 0 && !drawFinger) constrainShape()
                drawButtons = btn
                for (h in 0 until e.historySize) push(RawPoint.MOVE, e, i, h)
                push(RawPoint.MOVE, e, i, -1)
                watchShapeHold(e.getX(i), e.getY(i))
                if (holdArmed && hypot(e.getX(i) - downX, e.getY(i) - downY) > touchSlop) cancelHold()
                requestRender()
            }
            Mode.NAV -> updateNav(e)
            Mode.PAN -> {
                val i = e.findPointerIndex(panId)
                if (i < 0) return
                val x = e.getX(i)
                val y = e.getY(i)
                if (holdArmed && hypot(x - downX, y - downY) > touchSlop) cancelHold()
                if (!holdArmed && state.view3D) {
                    // Putar model: geser mendatar = putar kiri/kanan, tegak = condong.
                    val c = engine.cam3D
                    val k = 1f / (160f * density)
                    engine.cam3D = c.copy(
                        yaw = c.yaw + (x - panLast.x) * k,
                        pitch = (c.pitch + (y - panLast.y) * k).coerceIn(-1.55f, 1.55f),
                    )
                    requestRender()
                } else if (!holdArmed) {
                    view.postTranslate(x - panLast.x, y - panLast.y)
                    pushView()
                }
                panLast.set(x, y)
            }
            Mode.HOLD_PICK, Mode.PICK -> pick(e.getX(0), e.getY(0))
            Mode.XF_DRAG -> {
                val i = e.findPointerIndex(xfDragId)
                if (i < 0) return
                val c = toCanvas(e.getX(i), e.getY(i))
                state.xfDragTo(c.x, c.y, zoom, density)
            }
            Mode.XF_PINCH -> updateXfPinch(e)
            Mode.LIQ -> {
                val i = e.findPointerIndex(liqId)
                if (i < 0) return
                for (h in 0 until e.historySize) liqPush(e, i, h, 1)
                liqPush(e, i, -1, 1)
                requestRender()
            }
            Mode.CENTER -> centerAt(e.getX(0), e.getY(0))
            Mode.SEL -> {
                val i = e.findPointerIndex(selId)
                if (i < 0) return
                val x = e.getX(i)
                val y = e.getY(i)
                if (hypot(x - selSx, y - selSy) > touchSlop) selMoved = true
                val c = toCanvas(x, y)
                state.selMove(c.x, c.y, x - selSx, zoom, density)
            }
            Mode.CLONE_SRC -> cloneAt(e.getX(0), e.getY(0))
            Mode.GUIDE -> guideMove(e)
            Mode.TEXT -> textMove(e)
            else -> Unit
        }
    }

    private fun onPointerUp(e: MotionEvent) {
        val i = e.actionIndex
        val id = e.getPointerId(i)
        when (mode) {
            Mode.DRAW -> if (id == drawId) {
                push(RawPoint.END, e, i, -1)
                mode = Mode.IGNORE
            }
            Mode.LIQ -> if (id == liqId) {
                endLiq(e, i)
                mode = Mode.IGNORE
            }
            Mode.SEL -> if (id == selId) {
                endSel(e, i)
                mode = Mode.IGNORE
            }
            Mode.NAV -> {
                if (e.pointerCount - 1 >= 2) {
                    startNav(e, exclude = i)
                } else {
                    // Tinggal satu jari: lanjutkan menggeser.
                    val keep = if (i == 0) 1 else 0
                    finishNavGesture()
                    mode = Mode.PAN
                    panId = e.getPointerId(keep)
                    panLast.set(e.getX(keep), e.getY(keep))
                }
            }
            Mode.XF_PINCH -> {
                val keep = if (i == 0) 1 else 0
                if (e.pointerCount - 1 >= 2) startXfPinch(e, exclude = i) else xfBegin(e, keep, useHandles = false)
            }
            Mode.XF_DRAG -> if (id == xfDragId) {
                state.xfDragEnd()
                mode = Mode.IGNORE
            }
            Mode.GUIDE -> if (id == guideId) {
                guideUp(e, i)
                mode = Mode.IGNORE
            }
            Mode.TEXT -> if (id == textId) {
                textUp()
                mode = Mode.IGNORE
            }
            else -> Unit
        }
    }

    private fun onUp(e: MotionEvent) {
        cancelHold()
        when (mode) {
            Mode.DRAW -> {
                val i = e.findPointerIndex(drawId)
                if (i >= 0) push(RawPoint.END, e, i, -1)
                requestRender()
            }
            Mode.NAV -> finishNavGesture()
            Mode.HOLD_PICK, Mode.PICK -> state.finishPick()
            Mode.LIQ -> endLiq(e, e.findPointerIndex(liqId))
            Mode.XF_DRAG, Mode.XF_PINCH -> state.xfDragEnd()
            Mode.SEL -> endSel(e, e.findPointerIndex(selId))
            Mode.GUIDE -> guideUp(e, e.findPointerIndex(guideId))
            Mode.TEXT -> textUp()
            else -> Unit
        }
        mode = Mode.NONE
    }

    private fun onCancel(e: MotionEvent) {
        cancelHold()
        removeCallbacks(perfectRunnable)
        if (mode == Mode.DRAW) {
            val i = e.findPointerIndex(drawId)
            // Sistem merebut sentuhan pena saat bentuk sedang ditahan: hampir pasti karena jari menyentuh layar
            // (Android menahan sentuhan jari selama pena menempel). Anggap itu permintaan bentuk presisi.
            if (!drawFinger && engine.shapeActive) constrainShape()
            if (!drawFinger && i >= 0) {
                // Sistem (mis. gestur tiga jari HyperOS karena telapak tangan) merebut sentuhan di tengah goresan pena:
                // goresan yang sudah dibuat disimpan, bukan dibuang.
                push(RawPoint.END, e, i, -1)
            } else {
                engine.input.add(RawPoint(RawPoint.CANCEL, 0f, 0f, 0f, 0f, 0f, SystemClock.uptimeMillis(), 0))
            }
            requestRender()
        }
        if (mode == Mode.HOLD_PICK || mode == Mode.PICK) state.cancelPick()
        if (mode == Mode.LIQ) endLiq(e, -1)
        if (mode == Mode.XF_DRAG || mode == Mode.XF_PINCH) state.xfDragEnd()
        if (mode == Mode.SEL) state.selCancelGesture()
        mode = Mode.NONE
    }

    // ---- pilihan (selection) ----

    private var selId = -1
    private var selSx = 0f
    private var selSy = 0f
    private var selT = 0L
    private var selMoved = false
    private var selStylus = false

    private fun beginSel(e: MotionEvent, i: Int) {
        mode = Mode.SEL
        selId = e.getPointerId(i)
        selSx = e.getX(i)
        selSy = e.getY(i)
        selT = e.eventTime
        selMoved = false
        selStylus = isStylus(e, i)
        if (selStylus) requestUnbufferedDispatch(e)
        val c = toCanvas(selSx, selSy)
        state.selDown(c.x, c.y, zoom, density)
    }

    private fun endSel(e: MotionEvent, i: Int) {
        val x = if (i >= 0) e.getX(i) else selSx
        val y = if (i >= 0) e.getY(i) else selSy
        val tap = !selMoved && e.eventTime - selT < 400L
        val c = toCanvas(x, y)
        state.selUp(c.x, c.y, tap, zoom, density)
    }

    // ---- penyesuaian interaktif ----

    /** Perspective Blur dan Chromatic Aberration (mode perspektif) memakai titik pusat yang diketuk di kanvas. */
    private fun usesCenter(): Boolean {
        val k = state.adjustKind
        return k == Adj.PERSP || (k == Adj.CHROMA && state.adjMode == 0)
    }

    private fun centerAt(x: Float, y: Float) {
        val c = toCanvas(x, y)
        state.setAdjustCenter(c.x, c.y)
    }

    private fun cloneAt(x: Float, y: Float) {
        val c = toCanvas(x, y)
        state.setCloneSource(c.x, c.y)
        requestRender()
    }

    private fun nearCloneSource(x: Float, y: Float): Boolean {
        val m = engine.cloneMarker ?: return false
        val p = floatArrayOf(m[0], m[1])
        view.mapPoints(p)
        return hypot(p[0] - x, p[1] - y) < 40f * density
    }

    private var liqId = -1
    private var liqFinger = false
    /** Sapuan ini untuk mode Pena penyesuaian (bukan Liquify). */
    private var liqPen = false
    private var liqErase = false
    private var liqBase = 0L
    private var liqLastX = 0f
    private var liqLastY = 0f
    private var liqLastP = 1f

    private fun beginLiq(e: MotionEvent, i: Int, finger: Boolean) {
        mode = Mode.LIQ
        liqId = e.getPointerId(i)
        liqFinger = finger
        liqBase = e.eventTime
        liqPen = state.adjustKind != Adj.LIQUIFY
        liqErase = state.adjPenErase || e.getToolType(i) == MotionEvent.TOOL_TYPE_ERASER
        if (!finger) requestUnbufferedDispatch(e)
        liqPush(e, i, -1, 0)
        requestRender()
    }

    private fun liqPush(e: MotionEvent, i: Int, h: Int, phase: Int) {
        val sx = if (h >= 0) e.getHistoricalX(i, h) else e.getX(i)
        val sy = if (h >= 0) e.getHistoricalY(i, h) else e.getY(i)
        val pr = if (liqFinger) 1f else (if (h >= 0) e.getHistoricalPressure(i, h) else e.getPressure(i)).coerceIn(0.05f, 1f)
        val t = if (h >= 0) e.getHistoricalEventTime(h) else e.eventTime
        pts[0] = sx; pts[1] = sy
        inv.mapPoints(pts)
        liqLastX = pts[0]; liqLastY = pts[1]; liqLastP = pr
        if (liqPen) engine.penInput.add(floatArrayOf(pts[0], pts[1], pr, phase.toFloat(), if (liqErase) 1f else 0f))
        else engine.liqInput.add(floatArrayOf(pts[0], pts[1], pr, phase.toFloat(), (t - liqBase).toFloat()))
        engine.hover = floatArrayOf(sx, sy)
    }

    private fun endLiq(e: MotionEvent, i: Int) {
        if (i >= 0) liqPush(e, i, -1, 2)
        else if (liqPen) engine.penInput.add(floatArrayOf(liqLastX, liqLastY, liqLastP, 2f, if (liqErase) 1f else 0f))
        else engine.liqInput.add(floatArrayOf(liqLastX, liqLastY, liqLastP, 2f, (SystemClock.uptimeMillis() - liqBase).toFloat()))
        if (liqFinger) engine.hover = null
        requestRender()
    }

    private fun cancelPen() {
        engine.penInput.add(floatArrayOf(liqLastX, liqLastY, 1f, 3f, 0f))
        if (liqFinger) engine.hover = null
        requestRender()
    }

    // ---- menggambar ----

    private fun beginDraw(e: MotionEvent, i: Int, finger: Boolean) {
        if (state.paperEditing || state.guideEditing || state.textEditing) {
            mode = Mode.IGNORE
            return
        }
        mode = Mode.DRAW
        drawSerial++
        removeCallbacks(shapeRunnable)
        removeCallbacks(perfectRunnable)
        drawDevice = if (finger) -1 else e.deviceId
        drawButtons = e.buttonState and (MotionEvent.BUTTON_STYLUS_PRIMARY or MotionEvent.BUTTON_STYLUS_SECONDARY)
        shapeX = e.getX(i)
        shapeY = e.getY(i)
        shapeTravel = 0f
        drawId = e.getPointerId(i)
        drawFinger = finger
        drawStart = SystemClock.uptimeMillis()
        hasTilt = !finger && e.device?.getMotionRange(MotionEvent.AXIS_TILT) != null
        drawEraser = e.getToolType(i) == MotionEvent.TOOL_TYPE_ERASER ||
            (state.prefs.penButtonErase && (e.buttonState and MotionEvent.BUTTON_STYLUS_PRIMARY) != 0)
        push(RawPoint.BEGIN, e, i, -1)
        requestRender()
    }

    private val pts = FloatArray(2)

    private fun push(kind: Int, e: MotionEvent, i: Int, h: Int) {
        val x: Float
        val y: Float
        val p: Float
        val tilt: Float
        val az: Float
        val t: Long
        if (h >= 0) {
            x = e.getHistoricalX(i, h); y = e.getHistoricalY(i, h)
            p = e.getHistoricalPressure(i, h)
            tilt = e.getHistoricalAxisValue(MotionEvent.AXIS_TILT, i, h)
            az = e.getHistoricalAxisValue(MotionEvent.AXIS_ORIENTATION, i, h)
            t = e.getHistoricalEventTime(h)
        } else {
            x = e.getX(i); y = e.getY(i)
            p = e.getPressure(i)
            tilt = e.getAxisValue(MotionEvent.AXIS_TILT, i)
            az = e.getAxisValue(MotionEvent.AXIS_ORIENTATION, i)
            t = e.eventTime
        }
        pts[0] = x; pts[1] = y
        var flags = 0
        // Tampilan 3D: titik layar dipetakan ke kanvas oleh mesin (UV model) di thread GL.
        if (state.view3D) flags = flags or RawPoint.SCREEN else inv.mapPoints(pts)
        if (!drawFinger) flags = flags or RawPoint.PRESSURE
        if (hasTilt) flags = flags or RawPoint.TILT
        if (drawEraser) flags = flags or RawPoint.ERASER
        engine.input.add(RawPoint(kind, pts[0], pts[1], p, tilt, az, t, flags))
    }

    // ---- bentuk otomatis: pena ditahan diam di ujung goresan ----

    private var drawSerial = 0
    private var shapeSerial = -1
    private var shapeX = 0f
    private var shapeY = 0f
    private var shapeTravel = 0f
    private val shapeRunnable = Runnable {
        if (mode == Mode.DRAW && shapeSerial == drawSerial && shapeAllowed()) {
            engine.input.add(RawPoint(RawPoint.SHAPE, 0f, 0f, 0f, 0f, 0f, SystemClock.uptimeMillis(), 0))
            requestRender()
            // Pena tetap diam sekitar 1 detik lagi sesudah bentuk muncul: jadikan presisi.
            if (state.prefs.quickShapeHold) postDelayed(perfectRunnable, 1100)
        }
    }

    private val perfectRunnable = Runnable {
        if (mode == Mode.DRAW && shapeSerial == drawSerial) constrainShape()
    }

    private var drawDevice = -1
    private var drawButtons = 0

    /** Jadikan bentuk otomatis yang sedang ditahan presisi (jari kedua, tombol pena, atau ditahan lebih lama). */
    private fun constrainShape() {
        if (!engine.shapeActive) return
        engine.input.add(RawPoint(RawPoint.CONSTRAIN, 0f, 0f, 0f, 0f, 0f, SystemClock.uptimeMillis(), 0))
        requestRender()
    }

    /** Bentuk otomatis untuk kuas, dan untuk penghapus bila pengaturannya hidup (alat baur tidak pernah). */
    private fun shapeAllowed(): Boolean {
        if (!state.prefs.quickShape) return false
        val erasing = drawEraser || state.tool == ToolKind.ERASE
        if (erasing) return state.prefs.quickShapeErase
        return state.tool != ToolKind.SMUDGE
    }

    /**
     * Setiap kali pena bergeser lebih dari ~2 dp, hitungan "ditahan" diulang. Bentuk baru dikenali bila pena
     * benar-benar diam 0,7 detik, jadi goresan yang pelan tidak ikut berubah jadi bentuk.
     */
    private fun watchShapeHold(x: Float, y: Float) {
        val d = hypot(x - shapeX, y - shapeY)
        if (d <= 2.2f * density) return
        shapeTravel += d
        shapeX = x
        shapeY = y
        removeCallbacks(shapeRunnable)
        removeCallbacks(perfectRunnable)
        if (shapeAllowed() && shapeTravel > 28f * density) {
            shapeSerial = drawSerial
            postDelayed(shapeRunnable, 700)
        }
    }

    // ---- pipet (tahan jari atau tombol pipet) ----

    private var holdArmed = false
    private var downX = 0f
    private var downY = 0f
    private val holdRunnable = Runnable {
        holdArmed = false
        if (penRecent()) return@Runnable
        if (mode == Mode.DRAW && drawFinger) {
            engine.input.add(RawPoint(RawPoint.CANCEL, 0f, 0f, 0f, 0f, 0f, SystemClock.uptimeMillis(), 0))
        }
        if (mode == Mode.DRAW || mode == Mode.PAN) {
            mode = Mode.HOLD_PICK
            pick(downX, downY)
        }
    }

    private fun armHold() {
        holdArmed = true
        postDelayed(holdRunnable, 450)
    }

    private fun cancelHold() {
        if (holdArmed) removeCallbacks(holdRunnable)
        holdArmed = false
    }

    private var pickPending = false

    private fun pick(sx: Float, sy: Float) {
        state.showLoupe(sx, sy)
        if (pickPending) return
        pickPending = true
        val c = toCanvas(sx, sy)
        gl { en ->
            en.pick(c.x, c.y, sx, sy)
            post { pickPending = false }
        }
    }

    // ---- cubit, geser, putar ----

    private val navIds = IntArray(2)
    private val nav0 = arrayOf(PointF(), PointF())
    private val navStart = Matrix()
    private var navTime = 0L
    private var navScale0 = 1f
    private var panId = -1
    private val panLast = PointF()

    private fun startNav(e: MotionEvent, exclude: Int = -1) {
        mode = Mode.NAV
        var k = 0
        for (j in 0 until e.pointerCount) {
            if (j == exclude) continue
            if (k < 2) {
                navIds[k] = e.getPointerId(j)
                nav0[k].set(e.getX(j), e.getY(j))
                k++
            }
        }
        if (k < 2) { mode = Mode.NONE; return }
        navStart.set(view)
        navCam = engine.cam3D
        if (exclude < 0 && e.actionMasked == MotionEvent.ACTION_POINTER_DOWN && e.pointerCount == 2) {
            navTime = SystemClock.uptimeMillis()
            navScale0 = zoom
        }
    }

    private fun updateNav(e: MotionEvent) {
        val i0 = e.findPointerIndex(navIds[0])
        val i1 = e.findPointerIndex(navIds[1])
        if (i0 < 0 || i1 < 0) return
        val ax = (nav0[0].x + nav0[1].x) / 2f
        val ay = (nav0[0].y + nav0[1].y) / 2f
        val bx = (e.getX(i0) + e.getX(i1)) / 2f
        val by = (e.getY(i0) + e.getY(i1)) / 2f
        val d0 = hypot(nav0[1].x - nav0[0].x, nav0[1].y - nav0[0].y)
        val d1 = hypot(e.getX(i1) - e.getX(i0), e.getY(i1) - e.getY(i0))
        if (d0 < 1f) return
        if (state.view3D) {
            // Dua jari di tampilan 3D: cubit = dekat/jauh, geser = pindahkan model.
            val c0 = navCam
            val wpp = c0.worldPerPx(height)
            engine.cam3D = c0.copy(
                dist = (c0.dist * d0 / d1).coerceIn(0.4f, 25f),
                px = c0.px + (bx - ax) * wpp,
                py = c0.py - (by - ay) * wpp,
            )
            requestRender()
            return
        }
        var s = d1 / d0
        val baseZoom = run { navStart.getValues(vals); sqrt(vals[0] * vals[0] + vals[3] * vals[3]) }
        val minZoom = 0.03f
        val maxZoom = 40f
        s = s.coerceIn(minZoom / baseZoom, maxZoom / baseZoom)
        val m = Matrix(navStart)
        m.postTranslate(-ax, -ay)
        m.postScale(s, s)
        if (state.prefs.rotateCanvas) {
            val a0 = atan2(nav0[1].y - nav0[0].y, nav0[1].x - nav0[0].x)
            val a1 = atan2(e.getY(i1) - e.getY(i0), e.getX(i1) - e.getX(i0))
            m.postRotate(Math.toDegrees((a1 - a0).toDouble()).toFloat())
        }
        m.postTranslate(bx, by)
        view.set(m)
        // Menempel ke kelipatan 90° bila dekat.
        if (state.prefs.rotateCanvas) {
            val r = rotationDeg
            val near = (r / 90f).roundToInt() * 90f
            if (abs(r - near) < 4f) view.postRotate(near - r, bx, by)
        }
        pushView()
    }

    private var navCam = Cam3D()

    private fun finishNavGesture() {
        if (state.view3D) {
            // Cubit cepat ke dalam: tampilan model kembali semula.
            if (navTime > 0 && SystemClock.uptimeMillis() - navTime < 300 && engine.cam3D.dist > navCam.dist * 1.5f) {
                engine.cam3D = Cam3D()
                requestRender()
            }
            navTime = 0
            return
        }
        // Cubit cepat ke dalam: kanvas pas di layar.
        if (navTime > 0 && SystemClock.uptimeMillis() - navTime < 300 && zoom < navScale0 * 0.65f) {
            fit(true)
        }
        navTime = 0
    }

    // ---- Teks: geser, putar/skala lewat pegangan, atau gambar jalur bebas ----

    private var textId = -1
    /** 1 geser, 2 putar/skala (pegangan), 3 gambar jalur. */
    private var textDrag = 0
    private val textGrab = PointF()
    private var textStartAng = 0f
    private var textStartDist = 1f
    private var textStartRot = 0f
    private var textStartScale = 1f
    private val textPts = ArrayList<Float>()

    private fun textDown(e: MotionEvent, i: Int) {
        mode = Mode.TEXT
        textId = e.getPointerId(i)
        val x = e.getX(i)
        val y = e.getY(i)
        val s = state.textSpec
        if (state.textDrawPath) {
            textDrag = 3
            textPts.clear()
            val p = toCanvas(x, y)
            textPts.add(p.x); textPts.add(p.y)
            engine.textPathDraft = textPts.toFloatArray()
            requestRender()
            return
        }
        val b = state.textBox
        val c = guideToScreen(s.cx, s.cy)
        if (b != null) {
            val h = guideToScreen(b[4], b[5])
            if (hypot(h.x - x, h.y - y) < 34f * density) {
                textDrag = 2
                textStartAng = atan2(y - c.y, x - c.x)
                textStartDist = max(1f, hypot(x - c.x, y - c.y))
                textStartRot = s.rot
                textStartScale = s.scale
                return
            }
        }
        textDrag = 1
        val p = toCanvas(x, y)
        textGrab.set(s.cx - p.x, s.cy - p.y)
    }

    private fun textMove(e: MotionEvent) {
        val i = e.findPointerIndex(textId)
        if (i < 0) return
        val x = e.getX(i)
        val y = e.getY(i)
        val s = state.textSpec
        when (textDrag) {
            1 -> {
                val p = toCanvas(x, y)
                state.updateText(s.copy(cx = p.x + textGrab.x, cy = p.y + textGrab.y))
            }
            2 -> {
                val c = guideToScreen(s.cx, s.cy)
                val a = atan2(y - c.y, x - c.x)
                val d = max(1f, hypot(x - c.x, y - c.y))
                // Putaran di layar sama dengan di kanvas kecuali tampilan dicerminkan; menempel ke kelipatan 90°.
                var rot = textStartRot + (a - textStartAng) * (if (isMirrored()) -1f else 1f)
                val q = (Math.PI / 2).toFloat()
                val near = (rot / q).roundToInt() * q
                if (abs(rot - near) < Math.toRadians(3.0).toFloat()) rot = near
                state.updateText(s.copy(rot = rot, scale = (textStartScale * d / textStartDist).coerceIn(0.05f, 40f)))
            }
            3 -> {
                val p = toCanvas(x, y)
                val n = textPts.size
                val minD = 6f * density / max(0.01f, currentZoom())
                if (n < 2 || hypot(p.x - textPts[n - 2], p.y - textPts[n - 1]) >= minD) {
                    if (textPts.size < 1200) { textPts.add(p.x); textPts.add(p.y) }
                    engine.textPathDraft = textPts.toFloatArray()
                    requestRender()
                }
            }
        }
    }

    private fun textUp() {
        if (textDrag == 3) {
            engine.textPathDraft = null
            state.setTextPath(textPts.toFloatArray())
            requestRender()
        }
        textDrag = 0
    }

    private fun textAbort() {
        if (textDrag == 3) {
            engine.textPathDraft = null
            requestRender()
        }
        textDrag = 0
    }

    private fun currentZoom(): Float {
        val v = FloatArray(9)
        view.getValues(v)
        return sqrt(v[0] * v[0] + v[3] * v[3])
    }

    private fun isMirrored(): Boolean {
        val v = FloatArray(9)
        view.getValues(v)
        return v[0] * v[4] - v[1] * v[3] < 0f
    }

    // ---- Edit Panduan Menggambar ----

    private var guideId = -1
    /** 0 geser kanvas, 1 pindah titik pusat (hijau), 2 putar (biru), 3 pindah titik hilang, 4 ketuk kosong (perspektif). */
    private var guideDrag = 0
    private var guideVp = -1
    private var guideMoved = false
    private val guideDown0 = PointF()
    private val guideGrab = PointF()
    private val guideLast = PointF()

    private fun guideToScreen(x: Float, y: Float): PointF {
        mapTmp[0] = x; mapTmp[1] = y
        view.mapPoints(mapTmp)
        return PointF(mapTmp[0], mapTmp[1])
    }

    /** Posisi titik putar (biru) di layar, searah putaran panduan dari titik pusat. */
    private fun guideRotateHandle(g: DrawingGuide, c: PointF): PointF {
        val e = guideToScreen(g.centerX(canvasW) + kotlin.math.cos(g.angle), g.centerY(canvasH) + kotlin.math.sin(g.angle))
        var dx = e.x - c.x
        var dy = e.y - c.y
        val l = hypot(dx, dy)
        if (l < 1e-6f) { dx = 1f; dy = 0f } else { dx /= l; dy /= l }
        val off = DrawingGuide.ROTATE_HANDLE_DP * density
        return PointF(c.x + dx * off, c.y + dy * off)
    }

    private fun guideDown(e: MotionEvent, i: Int) {
        mode = Mode.GUIDE
        guideId = e.getPointerId(i)
        val x = e.getX(i)
        val y = e.getY(i)
        guideDown0.set(x, y)
        guideLast.set(x, y)
        guideMoved = false
        guideDrag = 0
        guideVp = -1
        val g = state.guide
        val hit = 30f * density
        if (g.type == DrawingGuide.PERSP) {
            var best = hit
            for (k in 0 until g.vpCount) {
                val p = guideToScreen(g.vpX(k), g.vpY(k))
                val d = hypot(p.x - x, p.y - y)
                if (d < best) { best = d; guideVp = k }
            }
            if (guideVp >= 0) {
                guideDrag = 3
                val c = toCanvas(x, y)
                guideGrab.set(g.vpX(guideVp) - c.x, g.vpY(guideVp) - c.y)
            } else {
                guideDrag = 4
            }
            return
        }
        val c = guideToScreen(g.centerX(canvasW), g.centerY(canvasH))
        val r = guideRotateHandle(g, c)
        when {
            hypot(r.x - x, r.y - y) < hit -> guideDrag = 2
            hypot(c.x - x, c.y - y) < hit -> {
                guideDrag = 1
                val p = toCanvas(x, y)
                guideGrab.set(g.centerX(canvasW) - p.x, g.centerY(canvasH) - p.y)
            }
        }
    }

    private fun guideMove(e: MotionEvent) {
        val i = e.findPointerIndex(guideId)
        if (i < 0) return
        val x = e.getX(i)
        val y = e.getY(i)
        if (!guideMoved && hypot(x - guideDown0.x, y - guideDown0.y) > touchSlop) guideMoved = true
        if (!guideMoved) return
        val g = state.guide
        when (guideDrag) {
            1 -> {
                val p = toCanvas(x, y)
                state.updateGuide(g.copy(cx = p.x + guideGrab.x, cy = p.y + guideGrab.y))
            }
            2 -> {
                // Sudut dihitung di koordinat kanvas (tampilan bisa diputar/dibalik); menempel ke kelipatan 15°.
                val p = toCanvas(x, y)
                var a = atan2(p.y - g.centerY(canvasH), p.x - g.centerX(canvasW))
                val step = (Math.PI / 12).toFloat()
                val near = (a / step).roundToInt() * step
                if (abs(a - near) < Math.toRadians(2.5).toFloat()) a = near
                state.updateGuide(g.copy(angle = a))
            }
            3 -> {
                val p = toCanvas(x, y)
                val v = g.vps.toMutableList()
                if (guideVp in 0 until g.vpCount) {
                    v[2 * guideVp] = p.x + guideGrab.x
                    v[2 * guideVp + 1] = p.y + guideGrab.y
                    state.updateGuide(g.copy(vps = v))
                    state.selectVp(guideVp)
                }
            }
            else -> {
                view.postTranslate(x - guideLast.x, y - guideLast.y)
                pushView()
            }
        }
        guideLast.set(x, y)
    }

    private fun guideUp(e: MotionEvent, i: Int) {
        if (i >= 0 && !guideMoved) {
            val g = state.guide
            if (guideDrag == 3) {
                // Ketuk titik hilang: pilih (ketuk lagi = batal pilih).
                state.selectVp(if (state.guideSel == guideVp) -1 else guideVp)
            } else if (guideDrag == 4) {
                if (g.vpCount < DrawingGuide.MAX_VP) {
                    val p = toCanvas(e.getX(i), e.getY(i))
                    state.updateGuide(g.copy(vps = g.vps + listOf(p.x, p.y)))
                    state.selectVp(-1)
                } else {
                    state.showFlash("Paling banyak 3 titik hilang. Ketuk satu titik lalu Hapus titik untuk menggantinya")
                }
            }
        }
        guideFinish(guideMoved)
    }

    private fun guideFinish(moved: Boolean) {
        if (moved && guideDrag in 1..3) state.guideChanged()
        guideDrag = 0
    }

    // ---- transformasi layer ----

    private var xfDragId = -1
    private var xfHandle = -1
    private var xfStylus = false
    private val xfScreen: (FloatArray) -> Unit = { view.mapPoints(it) }

    /** Mulai seret transformasi dengan jari/pena [i]: kena pegangan = ubah ukuran/putar/distorsi; selain itu geser. */
    private fun xfBegin(e: MotionEvent, i: Int, useHandles: Boolean) {
        mode = Mode.XF_DRAG
        xfDragId = e.getPointerId(i)
        xfStylus = isStylus(e, i)
        val sx = e.getX(i)
        val sy = e.getY(i)
        xfHandle = if (useHandles) state.xfHit(sx, sy, xfScreen, density) else -1
        val c = toCanvas(sx, sy)
        state.xfDragStart(xfHandle, c.x, c.y, sx, sy, xfScreen)
    }
    private val xfIds = IntArray(2)
    private val xfPrev = arrayOf(PointF(), PointF())

    private fun startXfPinch(e: MotionEvent, exclude: Int = -1) {
        var k = 0
        for (j in 0 until e.pointerCount) {
            if (j == exclude) continue
            if (k < 2) {
                xfIds[k] = e.getPointerId(j)
                val c = toCanvas(e.getX(j), e.getY(j))
                xfPrev[k].set(c.x, c.y)
                k++
            }
        }
        if (k < 2) mode = Mode.XF_DRAG
    }

    private fun updateXfPinch(e: MotionEvent) {
        val i0 = e.findPointerIndex(xfIds[0])
        val i1 = e.findPointerIndex(xfIds[1])
        if (i0 < 0 || i1 < 0) return
        val c0 = toCanvas(e.getX(i0), e.getY(i0))
        val c1 = toCanvas(e.getX(i1), e.getY(i1))
        val p0 = xfPrev[0]
        val p1 = xfPrev[1]
        val d0 = hypot(p1.x - p0.x, p1.y - p0.y)
        val d1 = hypot(c1.x - c0.x, c1.y - c0.y)
        if (d0 < 1f) return
        val s = d1 / d0
        val a0 = atan2(p1.y - p0.y, p1.x - p0.x)
        val a1 = atan2(c1.y - c0.y, c1.x - c0.x)
        val ax = (p0.x + p1.x) / 2f
        val ay = (p0.y + p1.y) / 2f
        val bx = (c0.x + c1.x) / 2f
        val by = (c0.y + c1.y) / 2f
        val m = Matrix()
        m.postTranslate(-ax, -ay)
        m.postScale(s, s)
        m.postRotate(Math.toDegrees((a1 - a0).toDouble()).toFloat())
        m.postTranslate(bx, by)
        state.transformPost(m)
        p0.set(c0.x, c0.y)
        p1.set(c1.x, c1.y)
    }

    // ---- ketukan banyak jari: 2 = urungkan, 3 = ulangi, 4 = sembunyikan antarmuka ----
    // Tahan dua jari = urungkan terus-menerus, tahan tiga jari = ulangi terus-menerus (seperti Procreate).

    private var mtActive = false
    private var mtStart = 0L
    private var mtMax = 0
    private val mtDown = HashMap<Int, PointF>()

    /** Ketukan dimulai saat pena masih melayang: syaratnya lebih ketat (supaya telapak tangan tidak mengurungkan). */
    private var mtStrict = false

    /** Sudah mengurungkan/mengulang berulang karena jari ditahan: ketukan saat diangkat tidak dihitung lagi. */
    private var mtRepeated = false
    private var mtRepeatN = 0
    private var mtRepeatCount = 0

    private val mtRepeat = object : Runnable {
        override fun run() {
            if (!mtActive) return
            mtRepeated = true
            mtRepeatCount++
            state.onMultiTap(mtRepeatN)
            // Makin lama ditahan makin cepat, seperti Procreate.
            val next = when {
                mtRepeatCount < 4 -> 170L
                mtRepeatCount < 12 -> 110L
                else -> 70L
            }
            postDelayed(this, next)
        }
    }

    private fun stopRepeat() {
        removeCallbacks(mtRepeat)
    }

    /** Jadwalkan urungkan/ulangi berulang bila dua/tiga jari ditahan diam. */
    private fun armRepeat(n: Int) {
        stopRepeat()
        if (n !in 2..3 || mtStrict || penHovering) return
        mtRepeatN = n
        mtRepeatCount = 0
        postDelayed(mtRepeat, 550L)
    }

    // Geser tiga jari ke bawah = menu Salin & Tempel (seperti Procreate).
    private var sw3 = false
    private var sw3T = 0L
    private var sw3X = 0f
    private var sw3Y = 0f
    private var sw3Dy = 0f

    private fun avg3(e: MotionEvent, y: Boolean): Float {
        var s = 0f
        for (k in 0 until 3) s += if (y) e.getY(k) else e.getX(k)
        return s / 3f
    }

    // Gosok tiga jari (kiri-kanan berulang) = bersihkan layer, seperti Procreate.
    private var scDir = 0
    private var scExt = 0f
    private var scTurns = 0

    /** Kanvas yang ikut tergeser selama gerakan tiga jari dikembalikan, sentuhan sisanya diabaikan. */
    private fun endSwipe3() {
        sw3 = false
        mtActive = false
        stopRepeat()
        if (mode == Mode.NAV) {
            view.set(navStart)
            pushView()
        }
        mode = Mode.IGNORE
    }

    private fun trackScrub(e: MotionEvent) {
        val x = avg3(e, false)
        val amp = 28f * density
        when (scDir) {
            0 -> if (kotlin.math.abs(x - sw3X) > amp) { scDir = if (x > sw3X) 1 else -1; scExt = x }
            1 -> if (x > scExt) scExt = x else if (scExt - x > amp) { scTurns++; scDir = -1; scExt = x }
            else -> if (x < scExt) scExt = x else if (x - scExt > amp) { scTurns++; scDir = 1; scExt = x }
        }
        val dy = kotlin.math.abs(avg3(e, true) - sw3Y)
        if (scTurns >= 3 && dy < 90f * density && e.eventTime - sw3T < 4000L) {
            Diag.log("Gosok tiga jari: ${e.eventTime - sw3T} ms, $scTurns kali balik")
            endSwipe3()
            post { state.scrubClear() }
        }
    }

    private fun trackSwipe3(e: MotionEvent) {
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> { sw3 = false; sw3Dy = 0f }
            MotionEvent.ACTION_POINTER_DOWN -> {
                var finger = true
                for (k in 0 until e.pointerCount) if (isStylus(e, k)) finger = false
                sw3 = finger && e.pointerCount == 3 && e.eventTime - mtStart < 700L
                if (sw3) {
                    sw3T = e.eventTime
                    sw3X = avg3(e, false)
                    sw3Y = avg3(e, true)
                    sw3Dy = 0f
                    scDir = 0
                    scTurns = 0
                } else if (e.pointerCount == 3) {
                    Diag.log("Tiga jari tidak dihitung: jeda ${e.eventTime - mtStart} ms, jari=$finger")
                }
            }
            MotionEvent.ACTION_MOVE -> {
                if (!sw3 || e.pointerCount < 3) return
                val dy = avg3(e, true) - sw3Y
                val dx = avg3(e, false) - sw3X
                sw3Dy = dy
                if (state.prefs.scrubClear) {
                    trackScrub(e)
                    if (!sw3) return
                }
                if (state.prefs.swipe3Menu && dy > 70f * density && kotlin.math.abs(dx) < dy * 0.7f && e.eventTime - sw3T < 1500L) {
                    endSwipe3()
                    post { state.openClipMenu() }
                }
            }
            MotionEvent.ACTION_POINTER_UP -> {
                if (sw3 && e.pointerCount == 3 && scTurns > 0) Diag.log("Gosok tiga jari berhenti: $scTurns kali balik, ${e.eventTime - sw3T} ms")
                if (e.pointerCount <= 3) sw3 = false
            }
        }
    }

    private fun trackMultiTap(e: MotionEvent) {
        trackSwipe3(e)
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                stopRepeat()
                mtDown.clear()
                // Pena yang sekadar melayang tidak lagi menahan ketukan; hanya sentuhan pena yang baru terjadi.
                mtActive = !isStylus(e, 0) && e.getTouchMajor(0) <= 60f * density &&
                    !penTouchRecent() && !state.xfActive
                mtStrict = penRecent()
                mtRepeated = false
                mtStart = e.eventTime
                mtMax = 1
                mtDown[e.getPointerId(0)] = PointF(e.getX(0), e.getY(0))
            }
            MotionEvent.ACTION_POINTER_DOWN -> {
                if (!mtActive) return
                val i = e.actionIndex
                val spread = if (mtStrict) 250L else 400L
                if (mtRepeated || isStylus(e, i) || e.getTouchMajor(i) > 60f * density || e.eventTime - mtStart > spread) {
                    mtActive = false
                    stopRepeat()
                    return
                }
                mtDown[e.getPointerId(i)] = PointF(e.getX(i), e.getY(i))
                mtMax = max(mtMax, e.pointerCount)
                armRepeat(e.pointerCount)
            }
            MotionEvent.ACTION_MOVE -> {
                if (!mtActive) return
                val slop = touchSlop * 2.2f
                for (k in 0 until e.pointerCount) {
                    val d = mtDown[e.getPointerId(k)] ?: continue
                    if (hypot(e.getX(k) - d.x, e.getY(k) - d.y) > slop) {
                        // Jari bergeser: ini cubit/geser kanvas, bukan ketukan (pengulangan yang sudah jalan berhenti).
                        mtActive = false
                        stopRepeat()
                        return
                    }
                }
            }
            MotionEvent.ACTION_POINTER_UP -> {
                // Satu jari diangkat: pengulangan berhenti; ketukan biasa tetap dihitung saat jari terakhir diangkat.
                stopRepeat()
                if (mtRepeated) mtActive = false
            }
            MotionEvent.ACTION_UP -> {
                stopRepeat()
                val limit = if (mtStrict) 500L else 700L
                if (mtActive && !mtRepeated && e.eventTime - mtStart < limit && mtMax in 2..4 && !penTouchRecent()) {
                    val n = mtMax
                    post { state.onMultiTap(n) }
                }
                mtActive = false
            }
            MotionEvent.ACTION_CANCEL -> {
                stopRepeat()
                // Sistem merebut geser tiga jari (mis. tangkapan layar HyperOS) setelah jari sempat turun: tetap buka menu.
                if (sw3 && sw3Dy > 30f * density && state.prefs.swipe3Menu) {
                    sw3 = false
                    mtActive = false
                    post { state.openClipMenu() }
                    return
                }
                // HyperOS memakai sentuhan tiga jari untuk tangkapan layar dan merebut sentuhannya;
                // bila dua jari atau lebih baru saja mendarat diam, anggap ketukan tiga jari.
                if (mtActive && !mtRepeated && mtMax >= 2 && SystemClock.uptimeMillis() - mtStart < 700L && !penTouchRecent()) {
                    post { state.onMultiTap(3) }
                }
                mtActive = false
            }
        }
    }
}
