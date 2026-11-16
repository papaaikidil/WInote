package id.hananote

import android.content.Intent
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import id.hananote.data.Backups
import id.hananote.data.Repository
import kotlinx.coroutines.launch
import id.hananote.ink.Handwriting
import id.hananote.ink.InkLanguage
import id.hananote.ui.HanaApp
import id.hananote.ui.theme.HanaTheme
import id.hananote.ui.theme.isDarkTheme
import id.hananote.widget.HanaWidgets
import kotlinx.coroutines.flow.MutableStateFlow

class MainActivity : ComponentActivity() {
    /** Catatan yang diminta dibuka dari widget (diambil oleh layar utama lalu dikosongkan). */
    private val openRequest = MutableStateFlow<String?>(null)

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleWidgetIntent(intent)
    }

    /** Dari widget: buka catatan tertentu, atau buat catatan baru lalu buka. */
    private fun handleWidgetIntent(intent: Intent?) {
        when (intent?.action) {
            HanaWidgets.ACTION_OPEN -> intent.getStringExtra(HanaWidgets.EXTRA_NOTE)?.let { id ->
                // Dari widget satu catatan: langsung ke halaman yang diketuk.
                val page = intent.getIntExtra(HanaWidgets.EXTRA_PAGE, -1)
                if (page >= 0) {
                    val repo = Repository.get(applicationContext)
                    repo.prefs.edit().putInt("page_$id", page).apply()
                    repo.pageJump.value = id to page
                }
                openRequest.value = id
            }
            HanaWidgets.ACTION_NEW -> {
                val n = runCatching { Repository.get(applicationContext).createQuickNote() }.getOrNull()
                if (n != null) openRequest.value = n.id
            }
            else -> return
        }
        // Sudah ditangani: jangan diulang bila layar dibuat ulang.
        setIntent(Intent(this, MainActivity::class.java))
    }

    override fun onStop() {
        super.onStop()
        // Aplikasi ditinggalkan: buat cadangan otomatis bila sudah waktunya dan ada perubahan.
        val app = applicationContext
        Repository.get(app).appScope.launch { Backups.maybeAuto(app) }
    }

    override fun onResume() {
        super.onResume()
        preferHighRefreshRate()
    }

    /**
     * Minta laju layar tertinggi yang didukung (Xiaomi Pad 7: 144 Hz) dengan resolusi yang sama. Tanpa ini
     * banyak sistem, termasuk HyperOS, menjalankan aplikasi pada 60 Hz sehingga tulisan terasa tertinggal pena.
     */
    @Suppress("DEPRECATION")
    private fun preferHighRefreshRate() {
        runCatching {
            val d = (if (Build.VERSION.SDK_INT >= 30) display else null)
                ?: windowManager.defaultDisplay
                ?: return
            val cur = d.mode
            val best = d.supportedModes
                .filter { it.physicalWidth == cur.physicalWidth && it.physicalHeight == cur.physicalHeight }
                .maxByOrNull { it.refreshRate } ?: return
            val lp = window.attributes
            if (lp.preferredDisplayModeId == best.modeId) return
            lp.preferredDisplayModeId = best.modeId
            lp.preferredRefreshRate = best.refreshRate
            window.attributes = lp
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        preferHighRefreshRate()
        val repo = Repository.get(applicationContext)
        if (savedInstanceState == null) handleWidgetIntent(intent)
        Handwriting.language = runCatching {
            InkLanguage.valueOf(repo.prefs.getString("hw_lang", InkLanguage.AUTO.name) ?: InkLanguage.AUTO.name)
        }.getOrDefault(InkLanguage.AUTO)
        setContent {
            val theme by repo.theme.collectAsState()
            val dark = isDarkTheme(theme)
            // Ikon bilah status ikut terang/gelap sesuai tema yang dipilih, bukan tema sistem.
            LaunchedEffect(dark) {
                val style = if (dark) SystemBarStyle.dark(Color.TRANSPARENT)
                else SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT)
                enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
            }
            HanaTheme(theme) {
                HanaApp(repo, openRequest)
            }
        }
    }
}
