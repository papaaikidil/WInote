package id.goresan

import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import id.goresan.ui.AppState
import id.goresan.ui.GoresanApp
import id.goresan.ui.GoresanTheme

/**
 * Ruang gambar WInote (mesin gambar Goresan): kanvas berlapis dengan kuas, layer, Alpha Lock, ColorDrop, dan
 * ekspor. Dibuka dari tombol + di beranda ([EXTRA_NEW]), dari gambar di beranda ([EXTRA_OPEN]), atau galerinya.
 */
class DrawActivity : ComponentActivity() {
    private lateinit var state: AppState

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Diag.install(applicationContext)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        hideSystemBars()
        requestHighRefreshRate()
        state = AppState(applicationContext)
        state.onExit = { finish() }
        // Karya Display P3: jendela masuk mode warna gamut luas (hanya di layar yang mendukung).
        state.wideScreen = resources.configuration.isScreenWideColorGamut
        state.onWideColor = { on ->
            runCatching {
                window.colorMode = if (on) android.content.pm.ActivityInfo.COLOR_MODE_WIDE_COLOR_GAMUT
                else android.content.pm.ActivityInfo.COLOR_MODE_DEFAULT
            }
        }
        if (savedInstanceState == null) {
            val open = intent.getStringExtra(EXTRA_OPEN)
            when {
                open != null -> {
                    // Dari beranda: selesai menggambar langsung kembali ke beranda.
                    state.exitOnClose = true
                    state.openArtwork(open)
                }
                // Hanya untuk uji otomatis di emulator (APK debug): langsung buat kanvas tanpa dialog.
                intent.getBooleanExtra("uji_buat", false) &&
                    (applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE) != 0 -> {
                    state.exitOnClose = true
                    val model = intent.getStringExtra("uji_model")
                    if (intent.getBooleanExtra("uji_wajah", false)) {
                        state.createFacePaint()
                    } else if (model != null) {
                        // Uji model 3D: berkas di folder data aplikasi (GLB/OBJ), diimpor seperti dari pemilih berkas.
                        state.import3D(model.split(',').map { android.net.Uri.fromFile(java.io.File(filesDir, it)) })
                    } else if (!intent.hasExtra("uji_impor")) {
                        state.createArtwork(
                            "Uji", intent.getIntExtra("uji_w", 1600), intent.getIntExtra("uji_h", 1200),
                            profile = intent.getIntExtra("uji_profil", 0),
                        )
                    }
                    // Uji impor berkas berlapis (PSD/.procreate/PDF/gambar) dari folder data aplikasi: jadi karya baru.
                    intent.getStringExtra("uji_impor")?.let { f ->
                        state.importFile(android.net.Uri.fromFile(java.io.File(filesDir, f)))
                    }
                    // Uji impor kuas: berkas di folder data aplikasi (mis. .abr), diimpor seperti dari pemilih berkas.
                    intent.getStringExtra("uji_kuas")?.let { f ->
                        state.importBrush(android.net.Uri.fromFile(java.io.File(filesDir, f)))
                    }
                    // Uji: warna awal (RRGGBB) dan tekstur kertas (Paper.*).
                    intent.getStringExtra("uji_warna")?.let { c ->
                        runCatching { state.setColor(android.graphics.Color.parseColor(if (c.startsWith("#")) c else "#$c")) }
                    }
                    if (intent.hasExtra("uji_kertas")) {
                        state.pendingPaper = intent.getIntExtra("uji_kertas", 0)
                    }
                    // Uji palet: berkas palet (ASE/ACO/.swatches/GPL, dipisah koma) dan foto untuk palet otomatis.
                    intent.getStringExtra("uji_palet")?.split(',')?.forEach { f ->
                        state.importPalette(android.net.Uri.fromFile(java.io.File(filesDir, f)))
                    }
                    intent.getStringExtra("uji_palet_foto")?.let { f ->
                        state.paletteFromUri(android.net.Uri.fromFile(java.io.File(filesDir, f)))
                    }
                }
                intent.getBooleanExtra(EXTRA_NEW, false) -> {
                    state.exitOnClose = true
                    state.askNew = true
                }
            }
        }
        setContent {
            GoresanTheme {
                GoresanApp(state)
            }
        }
    }

    /** Layar penuh seperti Procreate; bilah sistem muncul sementara bila diusap dari tepi. */
    private fun hideSystemBars() {
        WindowInsetsControllerCompat(window, window.decorView).apply {
            hide(WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
    }

    /** Minta mode layar dengan refresh rate tertinggi (144 Hz di Xiaomi Pad 7). */
    private fun requestHighRefreshRate() {
        runCatching {
            val d = if (Build.VERSION.SDK_INT >= 30) display else {
                @Suppress("DEPRECATION")
                windowManager.defaultDisplay
            }
            if (d != null) {
                val cur = d.mode
                val best = d.supportedModes
                    .filter { it.physicalWidth == cur.physicalWidth && it.physicalHeight == cur.physicalHeight }
                    .maxByOrNull { it.refreshRate }
                if (best != null) {
                    val lp = window.attributes
                    lp.preferredDisplayModeId = best.modeId
                    window.attributes = lp
                }
            }
        }
    }

    /**
     * Enter/OK dari papan ketik fisik tidak boleh "mengetuk" tombol yang kebetulan terfokus (mis. Galeri, yang
     * menutup karya) setelah kolom teks melepas fokusnya. Kolom teks memakai Enter saat tombol ditekan, tombol biasa
     * baru bereaksi saat dilepas, jadi hanya pelepasan Enter yang dibuang.
     */
    override fun dispatchKeyEvent(event: android.view.KeyEvent): Boolean {
        val k = event.keyCode
        if (event.action == android.view.KeyEvent.ACTION_UP &&
            (k == android.view.KeyEvent.KEYCODE_ENTER || k == android.view.KeyEvent.KEYCODE_NUMPAD_ENTER || k == android.view.KeyEvent.KEYCODE_DPAD_CENTER)
        ) return true
        return super.dispatchKeyEvent(event)
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) hideSystemBars()
    }

    override fun onPause() {
        super.onPause()
        Diag.log("Aktivitas: jeda (pindah aplikasi)")
        state.onActivityPause()
    }

    override fun onResume() {
        super.onResume()
        Diag.log("Aktivitas: lanjut")
        hideSystemBars()
        state.onActivityResume()
    }

    override fun onStop() {
        super.onStop()
        Diag.log("Aktivitas: berhenti (di latar belakang)")
        Diag.flush()
    }

    companion object {
        /** Buka karya ini langsung. */
        const val EXTRA_OPEN = "open_art"
        /** Langsung tampilkan pilihan kanvas baru. */
        const val EXTRA_NEW = "new_art"

        /** Mesin gambar butuh Android 10 ke atas (OpenGL ES 3). */
        fun supported(): Boolean = Build.VERSION.SDK_INT >= 29
    }

    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        Diag.log("Sistem minta hemat memori (level $level)")
    }
}
