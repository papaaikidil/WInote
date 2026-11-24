@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)

package id.hananote.ui

import androidx.activity.compose.BackHandler
import androidx.compose.material3.SuggestionChip
import androidx.compose.foundation.layout.imePadding
import androidx.compose.runtime.key
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.draw.alpha
import androidx.compose.foundation.layout.widthIn
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.unit.sp
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.BookmarkAdd
import androidx.compose.material.icons.filled.Gesture
import androidx.compose.material.icons.filled.Highlight
import androidx.compose.material.icons.filled.MergeType
import androidx.compose.material.icons.filled.Rotate90DegreesCw
import android.content.ClipData
import android.graphics.RectF
import id.hananote.data.CoverItem
import id.hananote.data.PageNumberSpec
import id.hananote.ink.PdfField
import id.hananote.ink.PdfFieldBox
import id.hananote.ink.PdfGeom
import id.hananote.ink.PdfPages
import id.hananote.ink.PdfSelection
import id.hananote.ink.NoteHit
import id.hananote.ink.NoteSearch
import id.hananote.ink.PdfText
import id.hananote.ink.PdfTextLayer
import androidx.core.content.FileProvider
import androidx.compose.material3.RadioButton
import androidx.compose.runtime.produceState
import id.hananote.data.IndoDate
import java.time.LocalDate
import java.time.YearMonth
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Translate
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.filled.AddCircle
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.GridOn
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.StayCurrentLandscape
import androidx.compose.material.icons.filled.StayCurrentPortrait
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.BorderAll
import androidx.compose.material.icons.filled.FormatColorFill
import androidx.compose.material.icons.filled.Adjust
import androidx.compose.material.icons.filled.Slideshow
import androidx.compose.material.icons.filled.Style
import androidx.compose.material.icons.filled.DocumentScanner
import androidx.compose.material.icons.filled.Straighten
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.automirrored.filled.Toc
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import id.hananote.data.StickyColors
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.Redo
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoStories
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.OutlinedButton
import androidx.compose.material.icons.filled.CropRotate
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.rotate
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.unit.toSize
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.zIndex
import androidx.compose.foundation.gestures.ScrollableState
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.runtime.withFrameNanos
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.Stable
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.material.icons.filled.DragIndicator
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.VerticalAlignTop
import androidx.compose.material.icons.filled.VerticalAlignBottom
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import android.content.Context
import android.content.Intent
import android.net.Uri
import id.hananote.data.FontEntry
import id.hananote.data.FontRegistry
import id.hananote.data.ImageItem
import id.hananote.data.InputMode
import id.hananote.data.InternalLink
import id.hananote.data.LinkItem
import id.hananote.data.PageItem
import id.hananote.data.ShapeKind
import id.hananote.data.TapeColors
import id.hananote.data.TapeShape
import id.hananote.data.StickyItem
import id.hananote.data.TableItem
import id.hananote.data.TableFillColors
import id.hananote.data.TableFillNames
import id.hananote.data.TableStyle
import id.hananote.ink.SelectionInfo
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import id.hananote.data.HighlighterColors
import id.hananote.data.InkColors
import id.hananote.data.WatercolorColors
import id.hananote.data.PageContent
import id.hananote.data.TextItem
import id.hananote.ink.Handwriting
import id.hananote.ink.ImageCache
import id.hananote.ink.InkLine
import id.hananote.ink.LineGrouper
import id.hananote.ink.RuledLines
import id.hananote.ink.DocContent
import id.hananote.ink.DocPage
import id.hananote.ink.ExportFormat
import id.hananote.ink.Exporter
import id.hananote.ink.InkLanguage
import id.hananote.ink.PageIndexer
import id.hananote.data.CalendarItem
import android.provider.DocumentsContract
import id.hananote.data.Crop
import id.hananote.data.Align
import id.hananote.data.TextLists
import id.hananote.data.TextRuns
import id.hananote.data.EraserMode
import id.hananote.data.OutlineEntry
import id.hananote.data.TapeItem
import id.hananote.data.StudyCard
import id.hananote.data.PageBackground
import id.hananote.data.CrashLog
import id.hananote.data.PaperTexture
import id.hananote.ink.PaperBackgrounds
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.FlipToFront
import androidx.compose.material.icons.filled.Mood
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.FullscreenExit
import androidx.compose.material.icons.filled.UnfoldLess
import androidx.compose.material.icons.filled.UnfoldMore
import androidx.compose.material.icons.filled.AutoFixNormal
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.compose.material.icons.filled.CheckBox
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.ContentPaste
import id.hananote.ink.InkClipboard
import androidx.compose.material.icons.filled.FlipToBack
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.KeyboardArrowDown
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import android.Manifest
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import androidx.compose.ui.window.DialogProperties
import androidx.compose.material.icons.filled.EmojiEmotions
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Transform
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.FormatSize
import androidx.compose.material.icons.filled.FormatBold
import androidx.compose.material.icons.filled.FormatItalic
import androidx.compose.material.icons.filled.FormatUnderlined
import androidx.compose.material.icons.filled.FormatStrikethrough
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.PostAdd
import id.hananote.voice.Dictation
import id.hananote.voice.VoiceRecorder
import id.hananote.voice.FileTranscriber
import android.media.MediaPlayer
import androidx.compose.runtime.mutableLongStateOf
import id.hananote.data.Sentences
import androidx.compose.ui.text.input.KeyboardCapitalization
import id.hananote.data.Shadow
import id.hananote.data.AudioItem
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import id.hananote.data.MindMapItem
import id.hananote.data.MindMaps
import id.hananote.data.MindNode
import id.hananote.ink.MindMapRenderer
import androidx.compose.material.icons.filled.AccountTree
import id.hananote.ink.BackgroundRemover
import android.graphics.Bitmap
import androidx.compose.material.icons.automirrored.filled.FormatAlignLeft
import androidx.compose.material.icons.automirrored.filled.FormatAlignRight
import androidx.compose.material.icons.filled.FormatAlignCenter
import androidx.compose.material.icons.automirrored.filled.FormatListBulleted
import androidx.compose.material.icons.filled.FormatListNumbered
import androidx.compose.material.icons.filled.FormatAlignJustify
import android.os.SystemClock
import java.io.File
import androidx.compose.foundation.Image
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke as DrawStroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import kotlinx.coroutines.CancellationException
import id.hananote.data.PageMeta
import id.hananote.data.PaperColors
import id.hananote.data.PaperSize
import id.hananote.data.PaperTemplate
import id.hananote.data.Repository
import id.hananote.data.Stroke
import id.hananote.data.Tool
import id.hananote.ink.InkRenderer
import id.hananote.ink.InkView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import java.util.Locale
import java.util.UUID
import kotlin.math.abs

// =====================================================================
// Pengendali editor: halaman, alat tulis, dan penyimpanan otomatis.
// =====================================================================

/** Yang sedang diketik langsung di halaman. */
enum class TypeKind { TEXT, STICKY, NODE, CELL }

const val MIN_TEXT_SIZE = 6f
const val MAX_TEXT_SIZE = 120f

/** Ukuran huruf untuk ditampilkan: bulat tanpa koma, selain itu satu angka di belakang koma. */
fun sizeLabel(size: Float): String {
    val r = kotlin.math.round(size)
    return if (kotlin.math.abs(size - r) < 0.05f) r.toInt().toString()
    else String.format(Locale.US, "%.1f", size).replace('.', ',')
}

/** Angka ukuran yang diketik (koma atau titik), atau null. */
fun parseSize(text: String): Float? = text.replace(',', '.').toFloatOrNull()?.takeIf { it > 0f }

/** Jumlah halaman yang isinya boleh tetap dipegang di memori sekaligus. */
private const val PAGE_CACHE = 14

class EditorController(private val repo: Repository, val noteId: String) {
    private val prefs = repo.prefs

    var title by mutableStateOf(repo.note(noteId)?.title ?: "Catatan")
        private set
    val pages = mutableStateListOf<PageMeta>()
    var index by mutableIntStateOf(0)
        private set
    var tool by mutableStateOf(Tool.PEN)
        private set
    private val colors = mutableStateMapOf<Tool, Int>()
    private val widths = mutableStateMapOf<Tool, Float>()
    var canUndo by mutableStateOf(false)
        private set
    var canRedo by mutableStateOf(false)
        private set
    var fingerDraws by mutableStateOf(prefs.getBoolean("finger_draws", true))
        private set
    /** Cara penghapus bekerja: kuas halus (bergradasi), sebagian, atau goresan utuh. */
    var eraserMode by mutableStateOf(
        runCatching {
            EraserMode.valueOf(prefs.getString("eraser_mode", null) ?: if (prefs.getBoolean("eraser_partial", true)) "PARTIAL" else "WHOLE")
        }.getOrDefault(EraserMode.PARTIAL)
    )
        private set

    /** Kekuatan kuas penghapus halus (0..1). */
    var eraserStrength by mutableFloatStateOf(prefs.getFloat("eraser_strength", 0.35f))
        private set

    fun chooseEraserMode(m: EraserMode) {
        CrashLog.note("penghapus: ${m.name}")
        eraserMode = m
        prefs.edit().putString("eraser_mode", m.name).apply()
    }

    /** Layar penuh: semua bilah disembunyikan, tinggal bilah alat kecil yang melayang. */
    var focusMode by mutableStateOf(false)
        private set

    fun toggleFocus() { focusMode = !focusMode; CrashLog.note("layar penuh: $focusMode") }

    /** Penghapus sebagian/utuh ikut menghapus gambar. */
    var eraseImages by mutableStateOf(prefs.getBoolean("eraser_images", true))
        private set

    fun toggleEraseImages() {
        eraseImages = !eraseImages
        prefs.edit().putBoolean("eraser_images", eraseImages).apply()
    }

    fun chooseEraserStrength(v: Float) {
        eraserStrength = v
        prefs.edit().putFloat("eraser_strength", v).apply()
    }
    var notice by mutableStateOf<String?>(null)

    /** Status jendela "Ubah jadi teks"; null berarti jendelanya tertutup. */
    var convert by mutableStateOf<ConvertState?>(null)
        private set

    /** Menu tahan tekan di kanvas (salin, potong, tempel); null bila tertutup. */
    var canvasMenu by mutableStateOf<CanvasMenu?>(null)
        private set

    /**
     * Mode baca: catatan hanya bisa dibaca (digulir, diperbesar, dibalik halamannya, rekaman diputar, link
     * dibuka); menulis, menghapus, memilih, mengetik, dan mengubah halaman dimatikan. Diingat per catatan.
     */
    var readMode by mutableStateOf(prefs.getBoolean("read_$noteId", false))
        private set

    fun toggleReadMode() = applyReadMode(!readMode)

    fun applyReadMode(on: Boolean) {
        if (readMode == on) return
        CrashLog.note("mode baca: $on")
        if (on) {
            autoJob?.cancel()
            finishEditing(commit = true)
            view?.endMapSession()
            mapState = null
            view?.clearSelection()
            convertPick = false
            convertBackToInk = false
            canvasMenu = null
            if (mode == InputMode.SELECT) {
                autoSelect = false
                mode = writingMode
                view?.inputMode = writingMode
            }
            if (!prefs.getBoolean("hint_read_mode", false)) {
                prefs.edit().putBoolean("hint_read_mode", true).apply()
                notice = "Mode baca: catatan hanya bisa dibaca. Geser untuk menggulir, sapu ke samping untuk membalik halaman."
            }
        }
        if (on) {
            // Mode Teks PDF tidak berlaku saat membaca.
            if (mode == InputMode.PDF) {
                mode = writingMode
                view?.inputMode = writingMode
                pdfForm = false
                view?.pdfFormMode = false
            }
            view?.clearPdfSelection()
            offerResume()
        } else {
            resumeIndex = null
            resumePageId = null
        }
        readMode = on
        view?.readOnly = on
        if (on) prefs.edit().putBoolean("read_$noteId", true).apply() else prefs.edit().remove("read_$noteId").apply()
        if (on) prefs.edit().putString("lastread_$noteId", page.id).apply()
        view?.bookmarkRibbon = on && ribbonPageId == page.id
    }

    fun dismissCanvasMenu() { canvasMenu = null }

    /** Menunggu pengguna melingkari tulisan yang akan diubah jadi teks. */
    var convertPick by mutableStateOf(false)
        private set
    /** Setelah selesai mengubah, kembali ke pena (karena mode pilih dinyalakan oleh "Jadi teks"). */
    private var convertBackToInk = false
    /** Goresan yang sedang diubah (untuk "Coba lagi"). */
    private var convertStrokes: List<Stroke> = emptyList()

    /** Mode Teks (mengetik seperti di pengolah kata) yang terakhir dipakai, bukan mode Pensil. */
    private var typeModeOn = prefs.getBoolean("type_mode", false)
    /** Tulis (INK), bentuk (SHAPE), pilih (SELECT), atau ketik (TYPE). */
    var mode by mutableStateOf(if (typeModeOn) InputMode.TYPE else InputMode.INK)
        private set
    /** Mode untuk kembali setelah memilih: Teks atau Pensil, mana yang terakhir dipakai. */
    private val writingMode: InputMode get() = if (typeModeOn) InputMode.TYPE else InputMode.INK
    /** Gaya huruf untuk teks baru di mode Teks (TextRuns.BOLD dan seterusnya). */
    var newTextFlags by mutableIntStateOf(0)
        private set
    /** Warna teks ketik baru; null: ikut warna pena. */
    var textColor by mutableStateOf<Int?>(if (prefs.contains("text_color")) prefs.getInt("text_color", 0) else null)
        private set
    fun newTextColor(): Int = textColor ?: colorOf(Tool.PEN)
    var shapeKind by mutableStateOf(
        runCatching { ShapeKind.valueOf(prefs.getString("shape", "RECT") ?: "RECT") }.getOrDefault(ShapeKind.RECT)
    )
        private set
    /** Bentuk selotip hafalan yang terakhir dipilih. */
    /** Tipe selotip: pita Lurus atau Bebas (bentuk seperti kotak dan bintang ada di alat Bentuk). */
    var tapeShape by mutableStateOf(
        runCatching { TapeShape.valueOf(prefs.getString("tape_shape", "STRAIGHT") ?: "STRAIGHT") }.getOrDefault(TapeShape.STRAIGHT)
            .takeIf { it.isBand } ?: TapeShape.STRAIGHT
    )
        private set
    var ruler by mutableStateOf(false)
        private set
    var selection by mutableStateOf<SelectionInfo?>(null)
        private set
    /** Link yang perlu dibuka di browser; dikosongkan setelah dibuka oleh layar. */
    var linkToOpen by mutableStateOf<String?>(null)

    /** Alat yang warna dan ketebalannya sedang diatur: bentuk memakai pengaturannya sendiri. */
    val styleTool: Tool get() = when (mode) {
        InputMode.SHAPE -> Tool.LINE
        InputMode.TAPE -> Tool.TAPE
        else -> tool
    }

    /**
     * Kelompok bilah fitur yang tombolnya sedang turun di bawah bilah (satu kelompok sekaligus), null bila
     * semua tersembunyi. Awalnya Sisipkan. Diingat antar catatan.
     */
    var openGroup by mutableStateOf<String?>(prefs.getString("feature_group", "Sisipkan")?.takeIf { it.isNotEmpty() })
        private set

    fun isGroupOpen(name: String): Boolean = openGroup == name

    /** Ketuk judul kelompok: tombolnya turun; ketuk lagi: naik dan tersembunyi. Kelompok lain langsung berganti. */
    fun toggleGroup(name: String) {
        openGroup = if (openGroup == name) null else name
        prefs.edit().putString("feature_group", openGroup ?: "").apply()
    }

    /** Huruf besar otomatis di awal kalimat untuk teks dari tulisan tangan dan suara. */
    var autoCaps by mutableStateOf(prefs.getBoolean("auto_caps", true))
        private set

    /** Bentuk otomatis: goresan yang ujungnya ditahan sebentar jadi garis, lingkaran, kotak, ... yang rapi. */
    var autoShape by mutableStateOf(prefs.getBoolean("auto_shape", true))
        private set

    fun toggleAutoShape() {
        autoShape = !autoShape
        prefs.edit().putBoolean("auto_shape", autoShape).apply()
        view?.autoShape = autoShape
        notice = if (autoShape) {
            "Bentuk otomatis aktif: gambar bentuk lalu tahan pena sebentar di ujungnya."
        } else {
            "Bentuk otomatis mati."
        }
    }

    /** Goresan baru saja berubah jadi bentuk rapi; petunjuk cara memakainya ditampilkan sekali. */
    fun autoShapeDone(kind: String) {
        if (!prefs.getBoolean("hint_auto_shape", false)) {
            prefs.edit().putBoolean("hint_auto_shape", true).apply()
            notice = "$kind dirapikan. Tahan pena sebentar di ujung goresan untuk bentuk otomatis; matikan di Alat → Bentuk otomatis."
        }
    }

    fun toggleAutoCaps() {
        autoCaps = !autoCaps
        prefs.edit().putBoolean("auto_caps", autoCaps).apply()
        notice = if (autoCaps) "Huruf besar otomatis di awal kalimat: aktif." else "Huruf besar otomatis: mati."
    }

    /** Terapkan huruf besar awal kalimat bila diaktifkan. [before] adalah teks tepat sebelumnya. */
    private fun caps(before: String, text: String): String = if (autoCaps) Sentences.fix(before, text) else text

    /** Mode teks otomatis: tulisan tangan langsung jadi teks ketik setelah jeda sebentar. */
    var autoText by mutableStateOf(prefs.getBoolean("auto_text", false))
        private set
    /** Font dan ukuran teks terakhir yang dipakai; juga dipakai untuk teks otomatis. */
    var lastFont by mutableStateOf(prefs.getString("last_font", FontRegistry.DEFAULT) ?: FontRegistry.DEFAULT)
        private set
    var lastTextSize by mutableStateOf(prefs.getFloat("last_text_size", 16f))
        private set
    val fonts get() = repo.fonts
    val stickers get() = repo.stickers

    /** Bahasa tulisan tangan yang dibaca: otomatis, Indonesia, atau Inggris. */
    var inkLanguage by mutableStateOf(Handwriting.language)
        private set

    /** Pesan kemajuan ekspor; null bila tidak sedang mengekspor. */
    var exportBusy by mutableStateOf<String?>(null)
        private set
    /** Hasil ekspor yang menunggu dibagikan atau disimpan. */
    var exportReady by mutableStateOf<ExportJob?>(null)

    /**
     * Yang sedang diketik langsung di halaman: isi, kursor/blok pilihan, dan kata yang sedang disusun keyboard.
     * null bila tidak sedang mengetik.
     */
    var typeValue by mutableStateOf<TextFieldValue?>(null)
        private set
    /** Jenis yang sedang diketik: teks, isi stiker, atau ide mind map. */
    var typeKind by mutableStateOf(TypeKind.TEXT)
        private set
    /** Isi teks yang sedang diketik; null bila tidak sedang mengetik. */
    val editingText: String? get() = typeValue?.text
    /** Letak (di kanvas) yang sedang diketik atau ide mind map terpilih, untuk bilah alat melayang. */
    var floatAnchor by mutableStateOf<android.graphics.RectF?>(null)
        private set
    /** Halaman sedang diputar atau digeser jauh ke samping (tampilkan tombol "Luruskan"). */
    var viewTilted by mutableStateOf(false)
        private set

    fun straighten() {
        CrashLog.note("luruskan halaman")
        view?.straighten()
    }

    /** Mind map yang sedang disunting langsung di halaman; null bila tidak ada. */
    var mapState by mutableStateOf<InkView.MapState?>(null)
        private set
    /** Naik untuk meminta keyboard muncul (lagi). */
    var keyboardTick by mutableIntStateOf(0)
        private set
    /** Naik setiap gaya teks yang sedang diketik berubah, supaya bilah alatnya ikut diperbarui. */
    var typeStyleTick by mutableIntStateOf(0)
        private set
    /** Sedang memilih berkas font: keyboard yang tertutup sementara tidak mengakhiri ketikan. */
    var pickingFont = false
    /** Sedang memilih berkas font untuk teks berikutnya (belum mengetik, di mode Teks). */
    var pickingNewFont = false
    /** Angka ukuran huruf yang sedang diketik di bilah alat teks; null bila tidak sedang mengetik angka. */
    var sizeEntry by mutableStateOf<TextFieldValue?>(null)
        private set
    /** Naik setiap kali mulai menyunting teks lain, supaya kolom ketik dimulai ulang. */
    var editSession by mutableIntStateOf(0)
        private set

    /** Alat Pilih yang aktif karena baru menyisipkan benda; kembali ke pena setelah selesai. */
    private var autoSelect = false
    private val pendingInk = ArrayList<Stroke>()
    /** Kemungkinan bacaan lain untuk teks hasil ubah: awalan teks + daftar kemungkinan bagian baru. */
    private val alternatives = HashMap<TextItem, Pair<String, List<String>>>()
    /** Pilihan bacaan lain untuk teks yang sedang disunting: (yang ditampilkan, teks lengkap). */
    var editSuggestions by mutableStateOf<List<Pair<String, String>>>(emptyList())
        private set
    private var autoJob: Job? = null
    private var autoBusy = false

    private var view: InkView? = null
    /**
     * Isi halaman yang sudah dimuat, paling lama dipakai di depan. Dibatasi supaya catatan tebal tidak
     * menghabiskan memori (yang membuat pembersih memori sering berjalan dan coretan tersendat).
     */
    private val pageContent = LinkedHashMap<String, PageContent>(16, 0.75f, true)
    private val dirtyPages = HashSet<String>()
    /** Halaman yang isinya berubah sejak indeks pencariannya dibuat. */
    private val indexDirty = HashSet<String>()
    private val main = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var saveJob: Job? = null
    private var loadJob: Job? = null
    private var convertJob: Job? = null

    init {
        pages.addAll(repo.loadPages(noteId))
        if (pages.isEmpty()) {
            pages.add(
                PageMeta(
                    UUID.randomUUID().toString(),
                    PaperSize.A4.widthPt(false), PaperSize.A4.heightPt(false),
                    PaperTemplate.LINED, PaperColors[0].second,
                )
            )
            repo.savePages(noteId, pages.toList())
        }
        for (t in Tool.entries) {
            colors[t] = prefs.getInt("color_${t.name}", defaultColor(t))
            widths[t] = prefs.getFloat("width_${t.name}", t.defaultWidth)
        }
        tool = runCatching { Tool.valueOf(prefs.getString("tool", "PEN") ?: "PEN") }.getOrDefault(Tool.PEN)
        index = prefs.getInt("page_$noteId", 0).coerceIn(0, pages.lastIndex)
        CrashLog.note("buka catatan (${pages.size} halaman)")
        prefetchHandwriting()
        // Dari widget: kotak centang diubah di sana, atau minta dibuka di halaman tertentu.
        main.launch { repo.externalEdits.collect { e -> if (e.noteId == noteId) applyExternalCheck(e) } }
        main.launch {
            repo.pageJump.collect { r ->
                if (r == null || r.first != noteId) return@collect
                repo.pageJump.value = null
                val target = r.second.coerceIn(0, pages.lastIndex)
                if (view != null) { if (target != index) showPage(target) } else index = target
            }
        }
    }

    /** Kotak centang diubah dari widget: isi halaman yang sudah dimuat ikut diubah (berkasnya sudah). */
    private fun applyExternalCheck(e: Repository.CheckEdit) {
        val content = pageContent[e.pageId] ?: return
        val v = view
        if (v != null && v.page?.id == e.pageId) {
            finishEditing(commit = true)
            v.applyExternalCheck(e.sticky, e.index, e.itemId, e.at, e.text)
            return
        }
        repo.applyCheck(content, e)
    }

    val page: PageMeta get() = pages[index.coerceIn(0, pages.lastIndex)]
    fun colorOf(t: Tool): Int = colors[t] ?: defaultColor(t)
    fun widthOf(t: Tool): Float = widths[t] ?: t.defaultWidth

    fun attach(v: InkView) {
        view = v
        v.readOnly = readMode
        v.laser = if (laserOn) laserTrail else null
        v.onStrokesChanged = {
            v.page?.let { dirtyPages.add(it.id); indexDirty.add(it.id); scheduleThumb(it.id) }
            scheduleSave()
            presentationFollow(changed = true)
        }
        v.onHistoryChanged = { u, r -> canUndo = u; canRedo = r }
        v.onStylusSeen = { onStylus() }
        v.onSelection = { info ->
            selection = info
            if (info == null && canvasMenu?.onObject == true) canvasMenu = null
            if (info == null && autoSelect && mode == InputMode.SELECT) {
                autoSelect = false
                mode = writingMode
            }
            // "Jadi teks": begitu tulisan dilingkari, langsung dibaca.
            if (convertPick && info != null) {
                if (info.ink) startConvert()
                else notice = "Yang dipilih bukan tulisan tangan. Lingkari tulisan tangannya."
            }
            if (info?.table == true && !info.multi && !prefs.getBoolean("hint_table_lines", false)) {
                prefs.edit().putBoolean("hint_table_lines", true).apply()
                notice = "Geser garis di dalam tabel untuk mengubah lebar kolom atau tinggi baris. Ketuk dua kali untuk mengetik."
            } else if (info?.rotatable == true && !prefs.getBoolean(if (info.multi) "hint_rotate_ink" else "hint_rotate", false)) {
                prefs.edit().putBoolean(if (info.multi) "hint_rotate_ink" else "hint_rotate", true).apply()
                notice = if (info.multi) {
                    "Seret lingkaran di atas kotak pilihan untuk memutar tulisan sesuka hati (menempel tiap 15°), " +
                        "atau ketuk Putar untuk 90°."
                } else {
                    "Seret lingkaran di atas ${info.kind.lowercase()} untuk memutarnya sesuka hati."
                }
            }
        }
        v.onOpenLink = { followLink(it) }
        v.onSwipePage = { dir -> turnPage(dir) }
        // Ketuk dua jari: urungkan; tiga jari: ulangi.
        v.onMultiTap = { n ->
            if (n == 2) {
                if (canUndo) { undo(); v.flash("Urungkan") } else v.flash("Tidak ada yang bisa diurungkan")
            } else if (n == 3) {
                if (canRedo) { redo(); v.flash("Ulangi") } else v.flash("Tidak ada yang bisa diulangi")
            }
        }
        if (!prefs.getBoolean("hint_multitap", false)) {
            prefs.edit().putBoolean("hint_multitap", true).apply()
            notice = "Tips: ketuk dua jari untuk urungkan, tiga jari untuk ulangi."
        }
        // Membalik dengan jari: halaman sebelah harus sudah dimuat supaya bisa digambar di bawah lembaran.
        v.peekPage = { dir ->
            pages.getOrNull(index + dir)?.let { m -> pageContent[m.id]?.let { m to it } }
        }
        v.onTurnCommit = { dir ->
            val target = index + dir
            if (target in pages.indices) showPage(target)
        }
        v.onContextMenu = { vx, vy, px, py, onObject ->
            if (onObject && mode != InputMode.SELECT) {
                // Objek dipilih dengan tahan tekan saat menulis: kembali ke pena begitu pilihannya dilepas.
                mode = InputMode.SELECT
                autoSelect = true
                v.inputMode = InputMode.SELECT
            }
            canvasMenu = if (onObject || InkClipboard.clip.value != null) CanvasMenu(vx, vy, px, py, onObject) else {
                notice = "Tahan objek untuk menyalin atau memotongnya; tahan tempat kosong untuk menempel."
                null
            }
        }
        v.onStrokeStarted = {
            canvasMenu = null
            autoJob?.cancel()
            if (editingText != null) finishEditing(commit = true)
        }
        v.onTextTapped = { t, at -> startEditing(t, at) }
        v.onCellTapped = { t, r, cc -> editCell(t, r, cc) }
        // Mode Pensil: teks diketuk sekali dipilih untuk digeser (kembali ke pena setelah dilepas),
        // diketuk dua kali langsung diketik.
        v.onTextPicked = {
            canvasMenu = null
            if (mode != InputMode.SELECT) {
                mode = InputMode.SELECT
                autoSelect = true
                v.inputMode = InputMode.SELECT
            }
            if (!prefs.getBoolean("hint_text_double_tap", false)) {
                prefs.edit().putBoolean("hint_text_double_tap", true).apply()
                notice = "Teks dipilih: geser untuk memindahkan. Ketuk dua kali untuk mengetik."
            }
        }
        v.onTypeCheck = { at -> toggleTypingCheck(at) }
        v.onTypeHere = { px, py -> typeHere(px, py) }
        v.onStickyTapped = { st -> editStickyInline(st) }
        v.onTypeCursor = { a, b -> moveCursor(a, b) }
        v.onTypeTap = { keyboardTick++ }
        v.onTypeOutside = { finishEditing(commit = true) }
        v.onFloatAnchor = { r -> floatAnchor = r }
        v.onMapState = { st -> mapState = st }
        v.onViewTilted = { viewTilted = it }
        v.onMapTypeRequest = { id, x, y -> typeNode(id, x, y) }
        v.onMindMapTapped = { m, node -> openMindMap(m, node) }
        v.onAudioTapped = { a -> togglePlayback(a) }
        v.onStrokeAdded = { s -> onInkForAutoText(s) }
        v.onPdfSelection = { sel -> pdfSel = sel }
        v.onPdfFieldTapped = { f -> pdfFieldTapped(f) }
        v.onPdfNoText = { pdfNoText() }
        v.autoShape = autoShape
        v.onAutoShape = { autoShapeDone(it) }
        v.pdfFormMode = pdfForm
        v.imageDir = repo.noteDir(noteId)
        refreshPageLabels()
        if (readMode) offerResume()
        v.post { showPage(index) }
    }

    fun showPage(i: Int) {
        val v = view ?: return
        if (pages.isEmpty()) return
        val target = i.coerceIn(0, pages.lastIndex)
        finishEditing(commit = true)
        v.endMapSession()
        alternatives.clear()
        stopPlayback()
        // Tulisan yang belum sempat jadi teks tetap sebagai coretan di halaman lamanya.
        autoJob?.cancel()
        pendingInk.clear()
        flush()
        reindexChanged()
        index = target
        prefs.edit().putInt("page_$noteId", target).apply()
        val meta = pages[target]
        CrashLog.note("buka halaman ${target + 1}/${pages.size}" + (if (meta.background.isNotEmpty()) " (latar ${meta.background.substringBefore(':')})" else ""))
        loadJob?.cancel()
        refreshPageLabels()
        val cached = pageContent[meta.id]
        if (cached != null) {
            v.setPage(meta, cached)
            onPageShown()
            preloadNeighbors()
            trimPageContent()
            return
        }
        loadJob = main.launch {
            // Dibaca lewat jalur tulis supaya simpanan yang tertunda selesai lebih dulu.
            val content = withContext(repo.writeDispatcher) { repo.loadContent(noteId, meta.id) }
            pageContent[meta.id] = content
            if (pages.getOrNull(index)?.id == meta.id) {
                v.setPage(meta, content)
                onPageShown()
            }
            preloadNeighbors()
            trimPageContent()
        }
    }

    /**
     * Lepaskan isi halaman yang paling lama tidak dibuka. Halaman yang sedang dibuka dan dua halaman di
     * kiri-kanannya, serta yang belum tersimpan atau belum masuk indeks, selalu tetap dipegang.
     */
    private fun trimPageContent() {
        if (pageContent.size <= PAGE_CACHE) return
        val keep = HashSet<String>()
        for (k in index - 2..index + 2) pages.getOrNull(k)?.let { keep.add(it.id) }
        val it = pageContent.keys.iterator()
        while (pageContent.size > PAGE_CACHE && it.hasNext()) {
            val id = it.next()
            if (id in keep || id in dirtyPages || id in indexDirty || id in thumbLoading) continue
            it.remove()
        }
    }

    /**
     * Muat isi halaman sebelum dan sesudahnya di latar, supaya bisa dibalik dengan jari. Foto dan halaman
     * PDF di sana juga disiapkan lebih dulu, jadi saat dibalik langsung tampil tanpa tersendat.
     */
    private fun preloadNeighbors() {
        for (k in intArrayOf(index + 1, index - 1)) {
            val m = pages.getOrNull(k) ?: continue
            val known = pageContent[m.id]
            if (known != null) { view?.prefetchMedia(m, known); continue }
            main.launch {
                val content = withContext(repo.writeDispatcher) { repo.loadContent(noteId, m.id) }
                if (!pageContent.containsKey(m.id)) pageContent[m.id] = content
                view?.prefetchMedia(m, pageContent[m.id] ?: content)
            }
        }
    }

    /** Pindah ke halaman sebelah dengan animasi membalik halaman (dari sapuan atau tombol panah). */
    fun turnPage(dir: Int) {
        val target = index + dir
        if (target !in pages.indices) {
            notice = if (dir > 0) "Ini halaman terakhir. Ketuk + untuk menambah halaman." else "Ini halaman pertama."
            return
        }
        CrashLog.note("balik halaman ${if (dir > 0) "maju" else "mundur"}")
        view?.beginPageTurn(dir)
        showPage(target)
    }

    private fun scheduleSave() {
        saveJob?.cancel()
        saveJob = main.launch {
            delay(900)
            flush()
        }
    }

    /** Simpan semua halaman yang berubah. Salinan diambil di sini, ditulis di latar. */
    fun flush() {
        saveJob?.cancel()
        saveJob = null
        if (dirtyPages.isEmpty()) return
        val work = dirtyPages.mapNotNull { id -> pageContent[id]?.let { id to it.copy() } }
        dirtyPages.clear()
        repo.writeScope.launch {
            for ((id, content) in work) repo.saveContent(noteId, id, content)
            repo.touchNote(noteId)
        }
    }

    fun makeThumb() {
        val first = pages.firstOrNull() ?: return
        val cached = pageContent[first.id]?.copy()
        repo.writeScope.launch {
            val content = cached ?: repo.loadContent(noteId, first.id)
            val bmp = InkRenderer.renderThumbnail(first, content, 360, repo.noteDir(noteId))
            repo.saveThumb(noteId, bmp)
            bmp.recycle()
        }
    }

    fun close() {
        stopPresentation()
        InkRenderer.PageNumbers.spec = null
        InkRenderer.PageNumbers.labels = emptyMap()
        PdfText.release()
        finishEditing(commit = true)
        stopPlayback()
        closeVoice()
        flush()
        makeThumb()
        reindexChanged()
        main.cancel()
        view = null
    }

    // ---------- ubah tulisan tangan jadi teks ----------

    /** Unduh kamus di latar saat ada Wi-Fi, supaya fitur ini siap dipakai tanpa internet. */
    fun prefetchHandwriting() {
        repo.appScope.launch {
            runCatching { if (!Handwriting.isReady()) Handwriting.download(wifiOnly = true) }
        }
    }

    /**
     * Tombol "Jadi teks": bila sudah ada tulisan tangan yang dipilih, langsung diubah. Bila belum,
     * masuk mode pilih dan minta pengguna melingkari tulisan yang ingin diubah.
     */
    fun requestConvert() {
        val v = view ?: return
        if (v.selectedInkStrokes().isNotEmpty()) {
            startConvert()
            return
        }
        CrashLog.note("jadi teks: pilih tulisan")
        convertPick = true
        convertBackToInk = mode != InputMode.SELECT
        autoSelect = false
        v.clearSelection()
        mode = InputMode.SELECT
        v.inputMode = InputMode.SELECT
    }

    /** Batal memilih tulisan untuk diubah jadi teks. */
    fun cancelConvertPick() {
        if (!convertPick) return
        convertPick = false
        backToInkAfterConvert()
    }

    /** Pilih semua tulisan tangan di halaman ini lalu ubah jadi teks. */
    fun convertWholePage() {
        val v = view ?: return
        if (!v.selectAllInk()) {
            notice = "Belum ada tulisan tangan di halaman ini."
            cancelConvertPick()
        }
    }

    private fun backToInkAfterConvert() {
        if (!convertBackToInk) return
        convertBackToInk = false
        view?.clearSelection()
        autoSelect = false
        mode = writingMode
        view?.inputMode = writingMode
    }

    /** Ubah tulisan tangan yang dipilih jadi teks. [retry] memakai tulisan yang sama seperti sebelumnya. */
    fun startConvert(retry: Boolean = false) {
        val v = view ?: return
        CrashLog.note("ubah jadi teks" + if (retry) " (coba lagi)" else "")
        val ink = if (retry) v.stillOnPage(convertStrokes) else v.selectedInkStrokes()
        if (ink.isEmpty()) {
            if (retry) convert = ConvertState.Failed("Tulisan yang dipilih sudah tidak ada di halaman ini.", canRetry = false)
            else requestConvert()
            return
        }
        convertPick = false
        convertStrokes = ink
        convertJob?.cancel()
        convert = ConvertState.Working("Menyiapkan…")
        convertJob = main.launch {
            try {
                if (!Handwriting.isReady()) {
                    convert = ConvertState.Working("Mengunduh kamus tulisan tangan bahasa Indonesia. Hanya sekali dan perlu internet; setelah itu bisa dipakai offline.")
                    try {
                        Handwriting.download()
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        convert = ConvertState.Failed("Kamus tulisan tangan belum bisa diunduh. Sambungkan tablet ke internet, lalu coba lagi.")
                        return@launch
                    }
                }
                convert = ConvertState.Working("Membaca tulisan…")
                val pg = page
                val lines = withContext(Dispatchers.Default) { LineGrouper.group(ink, pg) }
                val out = ArrayList<Pair<InkLine, String>>()
                var before = ""
                lines.forEachIndexed { i, line ->
                    if (lines.size > 1) convert = ConvertState.Working("Membaca tulisan… (${i + 1}/${lines.size})")
                    // Kalimat dari baris-baris sebelumnya membantu menebak kata berikutnya.
                    val pre = if (before.isEmpty()) "" else "$before "
                    val text = caps(pre, Handwriting.rank(Handwriting.recognizeAll(line, pre), pre).firstOrNull()?.trim().orEmpty())
                    if (text.isNotEmpty()) before = if (before.isEmpty()) text else "$before $text"
                    out.add(line to text)
                }
                applyJoined(out)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                convert = ConvertState.Failed("Tulisan belum bisa dibaca. Coba lagi sebentar lagi.")
            }
        }
    }

    fun closeConvert() {
        convertJob?.cancel()
        convert = null
        convertStrokes = emptyList()
        backToInkAfterConvert()
    }

    /**
     * Semua baris yang terbaca jadi SATU teks ketik yang bersambung, di tempat tulisan tangannya: baris-baris
     * disambung dengan spasi (kata yang terpotong tanda hubung disatukan lagi); hanya jarak kosong yang lebar atau
     * awal butir daftar yang jadi baris baru. Baris yang tidak terbaca tetap berupa coretan.
     */
    private fun applyJoined(read: List<Pair<InkLine, String>>) {
        val v = view ?: return
        val pg = page
        val parts = read.filter { it.second.isNotBlank() }
        if (parts.isEmpty()) {
            convert = ConvertState.Failed("Tidak ada tulisan yang terbaca dari yang dipilih.", canRetry = false)
            return
        }
        val sb = StringBuilder()
        var prev: InkLine? = null
        for ((line, raw) in parts) {
            val text = raw.trim()
            val last = prev
            if (last != null) {
                val gap = line.box.top - last.box.bottom
                val lh = maxOf(last.lineHeight, line.lineHeight, 1f)
                when {
                    gap > lh * 0.9f || TextLists.prefixOf(text) != null -> sb.append('\n')
                    sb.endsWith("-") && text.firstOrNull()?.isLowerCase() == true -> sb.setLength(sb.length - 1)
                    else -> sb.append(' ')
                }
            }
            sb.append(text)
            prev = line
        }
        val remove = HashSet<Stroke>()
        for ((line, _) in parts) remove.addAll(line.strokes)
        // Ukuran huruf mengikuti tinggi huruf kecil tulisan tangannya (nilai tengah semua baris).
        val sizes = parts.map { (it.first.xHeight * 2.1f).coerceIn(9f, 40f) }.sorted()
        val size = sizes[sizes.size / 2]
        val left = parts.minOf { it.first.box.left }
        val right = parts.maxOf { it.first.box.right }
        val x = left.coerceIn(0f, maxOf(0f, pg.width - size * 3f))
        val width = (right - x + size).coerceIn(size * 6f, maxOf(size * 6f, pg.width - x - 8f))
        val first = parts.first().first
        val item = textOnBaseline(x, first.baseline, width, sb.toString(), size, first.color, lastFont)
        v.clearSelection()
        v.applyTextChange(remove, emptyList(), listOf(item), animate = true)
        hintTapToEdit()
        convert = null
        convertStrokes = emptyList()
        backToInkAfterConvert()
    }

    private fun onStylus() {
        if (!prefs.getBoolean("stylus_seen", false)) {
            prefs.edit().putBoolean("stylus_seen", true).putBoolean("finger_draws", false).apply()
            fingerDraws = false
            notice = "Pena terdeteksi. Sekarang jari dipakai untuk menggeser dan memperbesar halaman."
        }
    }

    // ---------- alat ----------

    fun selectTool(t: Tool) {
        CrashLog.note("alat: ${t.name}")
        endInline()
        convertPick = false
        convertBackToInk = false
        tool = t
        setTypeMode(false)
        mode = InputMode.INK
        prefs.edit().putString("tool", t.name).apply()
    }

    private fun setTypeMode(on: Boolean) {
        if (typeModeOn == on) return
        typeModeOn = on
        prefs.edit().putBoolean("type_mode", on).apply()
    }

    /** Mode Pensil: kembali menulis dengan alat tulis terakhir. */
    fun selectPencil() = selectTool(tool)

    /** Mode sebelum tombol Pilih diketuk, untuk kembali ke alat itu saat Pilih diketuk lagi. */
    private var beforeSelect = InputMode.INK

    /**
     * Tombol Pilih di bilah (selalu ada, juga di mode Teks): masuk ke mode pilih, atau kembali ke alat/mode
     * sebelumnya bila sudah di mode pilih.
     */
    fun toggleSelect() {
        if (mode == InputMode.SELECT) {
            when (beforeSelect) {
                InputMode.SHAPE -> selectShapes()
                InputMode.TAPE -> selectTape()
                InputMode.TYPE -> selectTyping()
                else -> selectTool(tool)
            }
        } else {
            if (mode == InputMode.INK || mode == InputMode.SHAPE || mode == InputMode.TAPE || mode == InputMode.TYPE) beforeSelect = mode
            // Ketikan yang sedang berjalan diselesaikan dulu supaya hasilnya tersimpan sebelum memilih benda.
            if (mode == InputMode.TYPE) finishEditing(commit = true)
            selectPointer()
        }
    }

    /** Mode Teks: ketuk di mana saja di halaman lalu langsung mengetik, seperti di pengolah kata. */
    fun selectTyping() {
        CrashLog.note("mode teks")
        endInline()
        convertPick = false
        convertBackToInk = false
        autoSelect = false
        setTypeMode(true)
        mode = InputMode.TYPE
        view?.inputMode = InputMode.TYPE
        if (!prefs.getBoolean("hint_type_mode", false)) {
            prefs.edit().putBoolean("hint_type_mode", true).apply()
            notice = "Mode Teks: ketuk di mana saja di halaman lalu langsung ketik. Geser dengan jari atau pena untuk menggulir."
        }
    }

    /** Mode Teks: tempat kosong diketuk. Lanjutkan teks tepat di atasnya, atau mulai kotak teks baru di situ. */
    fun typeHere(px: Float, py: Float) {
        val v = view ?: return
        autoJob?.cancel()
        finishEditing(commit = true)
        v.endMapSession()
        val size = lastTextSize
        val line = size * 1.38f
        v.textAbove(px, py, line * 3.6f)?.let { t ->
            val l = InkRenderer.layoutFor(t)
            val lh = if (l.lineCount > 0) l.height.toFloat() / l.lineCount else t.size * 1.38f
            val gap = py - (t.y + l.height)
            // Seperti di pengolah kata: ketuk beberapa baris di bawah teks, baris kosongnya ditambahkan.
            val breaks = if (gap < lh * 0.3f) 0 else (floor(gap / lh).toInt() + 1).coerceAtMost(4)
            startEditing(t, t.text.length)
            if (breaks > 0) {
                val text = t.text + "\n".repeat(breaks)
                replaceTyping(TextFieldValue(text, TextRange(text.length)))
            }
            return
        }
        // Kertas bergaris: mulai di kanan garis margin merah, dan tiap baris duduk tepat di garis kertas.
        val ruled = RuledLines.of(page)
        val right = page.width * 0.07f
        val margin = if (ruled != null) PaperSize.mmToPt(28f) else right
        val left = if (px < page.width * 0.3f) margin else (px - size * 0.2f).coerceIn(margin, maxOf(margin, page.width - right - size * 6f))
        val w = (page.width - right - left).coerceAtLeast(size * 6f)
        val y = (py - line * 0.55f).coerceIn(0f, maxOf(0f, page.height - line))
        val item = if (ruled != null) ruledText(left, py, w, size, newTextColor(), lastFont, ruled)
        else TextItem(left, y, w, "", size, newTextColor(), lastFont)
        if (v.beginNewText(item)) {
            v.setTypingPending(newTextFlags)
            typeKind = TypeKind.TEXT
            applyTypeValue(TextFieldValue("", TextRange(0)))
            editSession++
            editSuggestions = emptyList()
            keyboardTick++
        }
    }

    /**
     * Teks baru di kertas bergaris: garis dasar baris pertamanya di garis kertas tepat di bawah titik [nearY],
     * dan jarak barisnya sama dengan jarak garis (atau kelipatannya untuk huruf besar).
     */
    private fun ruledText(x: Float, nearY: Float, w: Float, size: Float, color: Int, font: String, ruled: RuledLines, text: String = ""): TextItem {
        val pitch = InkRenderer.ruledPitch(size, font, ruled.gap)
        val probe = TextItem(x, 0f, w, text, size, color, font, linePitch = pitch)
        val b0 = InkRenderer.layoutFor(probe).getLineBaseline(0)
        val last = floor((page.height - ruled.gap * 0.5f - ruled.first) / ruled.gap).toInt().coerceAtLeast(0)
        val n = ceil((nearY - ruled.first - ruled.gap * 0.2f) / ruled.gap).toInt().coerceIn(0, last)
        return probe.at(x, ruled.first + n * ruled.gap - b0)
    }

    /** Tombol B/I/U/S aktif untuk pilihan atau kursor (atau untuk teks baru bila belum mengetik). */
    fun typeFlagActive(flag: Int): Boolean {
        val v = view ?: return false
        val value = typeValue
        if (value == null) return newTextFlags and flag != 0
        if (typeKind != TypeKind.TEXT) return false
        return v.typingFlagActive(flag, value.selection.min, value.selection.max)
    }

    fun typeToggle(flag: Int) {
        val v = view ?: return
        val value = typeValue
        if (value == null) {
            newTextFlags = newTextFlags xor flag
            return
        }
        if (typeKind != TypeKind.TEXT) return
        v.toggleTypingFlag(flag, value.selection.min, value.selection.max)
        typeStyleTick++
    }

    /** Huruf dan ukuran untuk teks berikutnya (belum mengetik). */
    fun setNewTextFont(font: String) = rememberTextStyle(font, lastTextSize)
    fun setNewTextSize(size: Float) = rememberTextStyle(lastFont, size.coerceIn(MIN_TEXT_SIZE, MAX_TEXT_SIZE))
    fun setNewTextColor(col: Int) {
        textColor = col
        prefs.edit().putInt("text_color", col).apply()
    }

    /** Kotak centang di teks yang sedang diketik diketuk: centang atau batalkan, kursor tetap. */
    private fun toggleTypingCheck(at: Int) {
        val v = typeValue ?: return
        val next = TextLists.toggleCheck(v.text, at) ?: return
        replaceTyping(v.copy(text = next))
    }

    fun selectShapes() { endInline(); convertPick = false; convertBackToInk = false; mode = InputMode.SHAPE; CrashLog.note("mode bentuk") }
    fun selectPointer() { endInline(); convertPick = false; convertBackToInk = false; mode = InputMode.SELECT; autoSelect = false; CrashLog.note("mode pilih") }

    /** Alat Selotip: tarik untuk menempel selotip hafalan, ketuk selotip untuk membuka atau menutupnya. */
    fun selectTape() {
        endInline(); convertPick = false; convertBackToInk = false
        mode = InputMode.TAPE
        CrashLog.note("mode selotip")
    }

    fun chooseTapeShape(k: TapeShape) {
        if (!k.isBand) return
        tapeShape = k
        selectTape()
        prefs.edit().putString("tape_shape", k.name).apply()
    }

    /** Tutup ([covered]) atau buka semua selotip di halaman ini. */
    fun setAllTapes(covered: Boolean) {
        val v = view ?: return
        val (all, _) = v.tapeCounts()
        if (all == 0) { notice = "Belum ada selotip di halaman ini."; return }
        v.setAllTapes(covered)
        notice = if (covered) "Semua selotip di halaman ini ditutup." else "Semua selotip di halaman ini dibuka."
    }

    /** Lepas semua selotip di halaman ini (bisa diurungkan). */
    fun removeAllTapes() {
        val v = view ?: return
        val n = v.removeAllTapes()
        notice = if (n == 0) "Belum ada selotip di halaman ini." else "$n selotip dilepas dari halaman ini. Ketuk Urungkan untuk mengembalikan."
    }

    fun chooseShape(k: ShapeKind) {
        shapeKind = k
        mode = InputMode.SHAPE
        prefs.edit().putString("shape", k.name).apply()
    }

    fun toggleRuler() { ruler = !ruler }

    fun setColor(c: Int) {
        val t = styleTool
        colors[t] = c
        prefs.edit().putInt("color_${t.name}", c).apply()
    }

    fun setWidth(w: Float) {
        val t = styleTool
        val v = w.coerceIn(t.minWidth, t.maxWidth)
        widths[t] = v
        prefs.edit().putFloat("width_${t.name}", v).apply()
    }

    // ---------- sisipkan benda ----------

    /** Posisi kiri-atas supaya benda berukuran w x h berada di tengah bagian halaman yang terlihat. */
    private fun centered(v: InkView, w: Float, h: Float): Pair<Float, Float> {
        val c = v.visibleCenter()
        val pg = page
        return (c.x - w / 2f).coerceIn(0f, maxOf(0f, pg.width - w)) to (c.y - h / 2f).coerceIn(0f, maxOf(0f, pg.height - h))
    }

    private fun place(item: (Float, Float) -> PageItem, w: Float, h: Float) {
        val v = view ?: return
        val (x, y) = centered(v, w, h)
        mode = InputMode.SELECT
        autoSelect = true
        v.inputMode = InputMode.SELECT
        v.addItem(item(x, y))
    }

    fun insertImage(context: Context, uri: Uri) {
        CrashLog.note("sisip gambar")
        main.launch {
            val res = withContext(Dispatchers.IO) {
                runCatching { repo.importImage(context.applicationContext, noteId, uri) }.getOrNull()
            }
            if (res == null) {
                notice = "Gambar tidak bisa dibuka. Coba pilih gambar lain."
                return@launch
            }
            val (file, iw, ih) = res
            val v = view ?: return@launch
            val pg = page
            var w = minOf(pg.width * 0.7f, v.visibleWidth() * 0.8f)
            var h = w * ih / iw.coerceAtLeast(1)
            val maxH = pg.height * 0.7f
            if (h > maxH) { w *= maxH / h; h = maxH }
            place({ x, y -> ImageItem(x, y, w, h, file) }, w, h)
        }
    }

    /** Stiker langsung siap ditulisi: alat kembali ke pena, stiker tidak dipilih. */
    fun insertSticker(color: Int) {
        val v = view ?: return
        val size = 150f
        val (x, y) = centered(v, size, size)
        if (mode != InputMode.INK) {
            setTypeMode(false)
            mode = InputMode.INK
            v.inputMode = InputMode.INK
        }
        v.addItem(StickyItem(x, y, size, size, color), selectIt = false)
        notice = "Stiker ditempel. Tulis langsung di atasnya, atau pilih stiker lalu ketuk “Isi teks”."
    }

    fun setStickyText(text: String, font: String, size: Float, align: Align) {
        val v = view ?: return
        val old = v.selectedItem() as? StickyItem ?: return
        rememberTextStyle(font, size)
        v.replaceSelected(old.withText(text.trim(), font, size, align), null)
    }

    /** Ratakan teks atau isi stiker yang sedang dipilih. */
    fun alignSelection(a: Align) {
        val v = view ?: return
        val t = v.selectedText()
        if (t != null) {
            if (t.align != a) v.replaceSelected(null, t.withAlign(a))
            return
        }
        val st = v.selectedItem() as? StickyItem ?: return
        if (st.align != a) v.replaceSelected(st.withText(st.text, st.font, st.textSize, a), null)
    }

    /** Jadikan teks atau isi stiker yang dipilih daftar berpoin/bernomor (tekan lagi untuk melepas). */
    fun toggleList(kind: TextLists.Kind) {
        val v = view ?: return
        val t = v.selectedText()
        if (t != null) {
            val next = TextLists.toggle(t.text, kind)
            if (next != t.text) v.replaceSelected(null, t.withText(next))
            return
        }
        val st = v.selectedItem() as? StickyItem ?: return
        val next = TextLists.toggle(st.text, kind)
        if (next != st.text) v.replaceSelected(st.withText(next, st.font, st.textSize), null)
    }

    fun selectedSticky(): StickyItem? = view?.selectedItem() as? StickyItem
    fun selectedText(): TextItem? = view?.selectedText()

    /** Sisipkan teks ketik di tengah layar. */
    fun insertText(text: String, font: String, size: Float, color: Int, align: Align = Align.LEFT) {
        val v = view ?: return
        if (text.isBlank()) return
        rememberTextStyle(font, size)
        val w = minOf(page.width * 0.7f, v.visibleWidth() * 0.8f)
        val (x, y) = centered(v, w, size * 1.4f)
        mode = InputMode.SELECT
        autoSelect = true
        v.inputMode = InputMode.SELECT
        v.addText(TextItem(x, y, w, text.trim(), size, color, font, align), selectIt = true)
    }

    private fun rememberTextStyle(font: String, size: Float) {
        lastFont = font
        lastTextSize = size
        prefs.edit().putString("last_font", font).putFloat("last_text_size", size).apply()
    }

    fun importFont(context: Context, uri: Uri, done: (FontEntry?) -> Unit) {
        main.launch {
            val r = withContext(Dispatchers.IO) {
                runCatching { repo.importFont(context.applicationContext, uri) }
                    .getOrElse { Repository.FontImport(emptyList(), "Berkas itu tidak bisa dibaca.") }
            }
            val entry = r.added.firstOrNull()
            notice = when {
                entry == null -> r.error ?: "Berkas itu bukan font yang bisa dipakai. Pilih berkas .ttf atau .otf."
                r.added.size == 1 -> "Font “${entry.name}” ditambahkan."
                else -> "${r.added.size} font ditambahkan: ${r.added.joinToString { it.name }}."
            }
            done(entry)
        }
    }

    // ---------- teks otomatis ----------

    fun toggleAutoText() {
        autoText = !autoText
        prefs.edit().putBoolean("auto_text", autoText).apply()
        if (autoText) {
            if (mode != InputMode.INK) { setTypeMode(false); mode = InputMode.INK }
            if (!tool.isWriting) selectTool(Tool.PEN)
            notice = "Teks otomatis aktif. Tulis, lalu jeda sebentar: tulisan akan berubah jadi teks."
            main.launch {
                val ready = runCatching { Handwriting.isReady() }.getOrDefault(false)
                if (!ready) {
                    notice = "Mengunduh kamus tulisan tangan (sekali saja, perlu internet)…"
                    val ok = runCatching { Handwriting.download() }.isSuccess
                    notice = if (ok) "Kamus siap. Silakan menulis." else "Kamus belum bisa diunduh. Sambungkan internet, lalu coba lagi."
                }
            }
        } else {
            autoJob?.cancel()
            pendingInk.clear()
        }
    }

    private fun onInkForAutoText(s: Stroke) {
        if (!autoText || mode != InputMode.INK) return
        if (!s.tool.isWriting) return
        pendingInk.add(s)
        autoJob?.cancel()
        autoJob = main.launch {
            waitUntilDoneWriting()
            runAutoText()
        }
    }

    /**
     * Tunggu sampai penulis selesai, seperti aplikasi catatan lain: pena yang diangkat menjauh
     * berarti selesai, pena yang masih melayang berarti mungkin masih berpikir (ditunggu lebih lama).
     * Tanpa info pena melayang, cukup jeda biasa. Makin utuh satu kalimat dibaca, makin tepat hasilnya.
     */
    private suspend fun waitUntilDoneWriting() {
        val start = SystemClock.uptimeMillis()
        while (true) {
            delay(100)
            val v = view ?: return
            val idle = SystemClock.uptimeMillis() - start
            when {
                v.penNear() -> if (idle >= AUTO_TEXT_PAUSE_MS + AUTO_TEXT_HOVER_EXTRA_MS) return
                v.penHoverSupported -> if (idle >= AUTO_TEXT_LIFT_MS) return
                else -> if (idle >= AUTO_TEXT_PAUSE_MS) return
            }
        }
    }

    /** Teks baru yang garis dasarnya tepat di garis tulisan tangannya. */
    private fun textOnBaseline(x: Float, baseline: Float, width: Float, text: String, size: Float, color: Int, font: String): TextItem {
        val probe = TextItem(x, 0f, width, text, size, color, font)
        val layout = InkRenderer.layoutFor(probe)
        val y = (baseline - layout.getLineBaseline(0)).coerceAtLeast(0f)
        return TextItem(x, y, width, text, size, color, font).also { it.layout = layout }
    }

    private suspend fun runAutoText() {
        if (autoBusy || pendingInk.isEmpty()) return
        val v = view ?: return
        // Hanya goresan yang masih ada (belum dihapus atau diurungkan).
        val batch = pendingInk.filter { v.hasStroke(it) }
        pendingInk.clear()
        if (batch.isEmpty()) return
        autoBusy = true
        try {
            if (!Handwriting.isReady()) {
                // Kamus belum ada: tulisan dibiarkan sebagai coretan.
                notice = "Kamus tulisan tangan belum terunduh. Sambungkan internet sebentar."
                runCatching { Handwriting.download() }
                return
            }
            val pageId = v.page?.id
            val pg = page
            val ruled = RuledLines.of(pg)
            val lines = withContext(Dispatchers.Default) { LineGrouper.group(batch, pg) }
            val remove = HashSet<Stroke>()
            val removeTexts = ArrayList<TextItem>()
            val add = ArrayList<TextItem>()
            var before = ""
            for (line in lines) {
                // Teks yang sudah ada di garis yang sama dipakai sebagai konteks dan disambung.
                val tolerance = ruled?.gap?.times(0.5f) ?: (line.lineHeight * 0.45f)
                val target = v.textOnLine(line.baseline, tolerance, line.box.left, removeTexts)
                val pre = when {
                    target != null -> target.text
                    before.isNotEmpty() -> "$before "
                    else -> v.textAbove(line.baseline, line.lineHeight, line.box.left, line.box.right)?.text?.let { "$it " } ?: ""
                }
                val ranked = Handwriting.rank(Handwriting.recognizeAll(line, pre), pre)
                val best = ranked.firstOrNull() ?: continue
                val candidates = ranked.map { it.trim() }
                remove.addAll(line.strokes)
                if (target != null) {
                    removeTexts.add(target)
                    val gapEm = (line.box.left - v.textEndX(target)) / target.size
                    val prefix = target.text + Handwriting.joiner(target.text, best, gapEm)
                    val options = candidates.map { caps(prefix, it) }
                    val joined = target.withText(prefix + options[0])
                    add.add(joined)
                    alternatives[joined] = prefix to options
                    before = joined.text
                } else {
                    val size = lastTextSize.coerceIn(9f, 48f)
                    val x = line.box.left.coerceIn(0f, maxOf(0f, pg.width - size * 3f))
                    val width = (pg.width - x - 8f).coerceAtLeast(size * 3f)
                    val options = candidates.map { caps(pre, it) }
                    val item = textOnBaseline(x, line.baseline, width, options[0], size, line.color, lastFont)
                    add.add(item)
                    alternatives[item] = "" to options
                    before = item.text
                }
            }
            if (add.isNotEmpty() && v.page?.id == pageId) {
                v.applyTextChange(remove, removeTexts, add, animate = true)
                hintTapToEdit()
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            notice = "Tulisan belum bisa dibaca. Coba tulis lebih rapi atau pakai “Ubah jadi teks”."
        } finally {
            autoBusy = false
            // Tulisan yang masuk selama sedang membaca diproses berikutnya.
            if (autoText && pendingInk.isNotEmpty()) {
                autoJob = main.launch {
                    delay(500)
                    runAutoText()
                }
            }
        }
    }

    fun selectedTable(): TableItem? = view?.selectedItem() as? TableItem

    fun recolorSelection(color: Int) { CrashLog.note("ubah warna pilihan"); view?.recolorSelection(color) }

    // ---------- salin dan tempel ----------

    fun copySelection(cut: Boolean = false) {
        val v = view ?: return
        val content = v.copySelection() ?: return
        CrashLog.note((if (cut) "potong" else "salin") + ": ${content.strokes.size} goresan, ${content.texts.size} teks, ${content.items.size} benda")
        InkClipboard.set(InkClipboard.Clip(content, repo.noteDir(noteId), page.id))
        if (cut) v.deleteSelection()
        notice = (if (cut) "Dipotong." else "Disalin.") + " Tahan di tempat kosong (halaman ini atau lainnya), lalu pilih Tempel."
    }

    /** Tempel isi papan klip di halaman ini; gambar dan rekaman dari catatan lain ikut disalin ke catatan ini. */
    fun paste(atX: Float = Float.NaN, atY: Float = Float.NaN) {
        val clip = InkClipboard.clip.value ?: return
        val v = view ?: return
        val targetDir = repo.noteDir(noteId)
        CrashLog.note("tempel" + if (clip.sourceDir != targetDir) " dari catatan lain" else "")
        main.launch {
            if (clip.sourceDir != targetDir) {
                withContext(Dispatchers.IO) {
                    for (it in clip.content.items) {
                        val name = when (it) {
                            is ImageItem -> it.file
                            is AudioItem -> it.file
                            else -> null
                        } ?: continue
                        val src = File(clip.sourceDir, name)
                        val dst = File(targetDir, name)
                        if (src.exists() && !dst.exists()) runCatching { src.copyTo(dst) }
                    }
                }
            }
            val step = if (clip.pageId == page.id && atX.isNaN()) { clip.pastes += 1; 14f * clip.pastes } else 0f
            mode = InputMode.SELECT
            autoSelect = true
            v.inputMode = InputMode.SELECT
            v.paste(clip.content, step, atX, atY)
        }
    }

    /** Terapkan susunan baru tabel terpilih (bisa diurungkan). */
    fun applyTableLayout(rows: Int, cols: Int, colFrac: FloatArray, rowFrac: FloatArray, header: Boolean, height: Float) {
        val v = view ?: return
        val t = v.selectedItem() as? TableItem ?: return
        CrashLog.note("atur tabel ${rows}x$cols")
        v.replaceSelectedItem(InkRenderer.fitTable(t.withLayout(rows, cols, colFrac, rowFrac, header, height)), moveInk = false)
    }

    /**
     * Bilah alat ringkas (tablet): alat tulis dan pengaturannya disembunyikan di dua tombol yang membuka daftar
     * tegak. Bila tidak, semua alat, warna, dan ketebalan berjajar di bilah. Diingat antar catatan.
     */
    var trayCompact by mutableStateOf(prefs.getBoolean("tray_compact", true))
        private set

    fun toggleTrayCompact() {
        trayCompact = !trayCompact
        prefs.edit().putBoolean("tray_compact", trayCompact).apply()
    }

    /**
     * Tabel yang garis dan warnanya sedang diatur (dialog terbuka). Disimpan saat dialog dibuka, karena membuka
     * dialog bisa mengakhiri ketikan di selnya.
     */
    var tableLook by mutableStateOf<TableItem?>(null)

    /** Tabel yang garis dan warnanya diatur: tabel yang selnya sedang diketik, atau tabel yang dipilih. */
    fun tableForStyle(): TableItem? = view?.typingCell()?.first ?: selectedTable()

    fun openTableLook() {
        tableLook = tableForStyle()
    }

    /** Terapkan garis, tebal garis, dan warna tabel (bisa diurungkan). */
    fun applyTableStyle(style: TableStyle, color: Int, header: Boolean, clearCellFills: Boolean) {
        val v = view ?: return
        val target = tableLook ?: return
        tableLook = null
        CrashLog.note("garis & warna tabel: garis ${style.borders}, tebal ${style.lineWidth}")
        if (!v.restyleTable(target.id) { it.withStyle(style, color, header, clearCellFills) }) {
            notice = "Tabelnya sudah tidak ada di halaman ini."
            return
        }
        typeStyleTick++
    }

    /** Sel yang akan diberi warna sendiri lewat pemilih warna (pemilihnya bisa mengakhiri ketikan di sel). */
    var cellFillPick by mutableStateOf<CellFillPick?>(null)

    fun pickCellFill(scope: Int) {
        val (t, r, col) = view?.typingCell() ?: return
        cellFillPick = CellFillPick(t.id, r, col, scope)
    }

    fun applyCellFillPick(fill: Int) {
        val p = cellFillPick ?: return
        cellFillPick = null
        val v = view ?: return
        CrashLog.note("warna sel tabel (warna sendiri)")
        if (v.typingCell()?.first?.id == p.tableId) {
            v.fillCellEdit(fill, p.scope)
        } else {
            v.restyleTable(p.tableId) { t -> t.withCellFill(t.scopeCells(p.r, p.c, p.scope), fill) }
        }
        typeStyleTick++
    }

    /** Warna isi sel yang sedang diketik: [scope] 0 sel ini, 1 barisnya, 2 kolomnya, 3 semua sel; 0 = tanpa warna. */
    fun typeCellFill(fill: Int, scope: Int) {
        if (typeKind != TypeKind.CELL) return
        CrashLog.note("warna sel tabel")
        view?.fillCellEdit(fill, scope)
        typeStyleTick++
    }

    fun prefs(): android.content.SharedPreferences = repo.prefs

    /** Sisipkan emoji sebagai teks besar di tengah layar; bisa digeser dan diperbesar seperti teks. */
    fun insertEmoji(emoji: String, size: Float) {
        val v = view ?: return
        val p = android.graphics.Paint().apply { textSize = size }
        val w = p.measureText(emoji) * 1.08f + 4f
        val (x, y) = centered(v, w, size * 1.3f)
        mode = InputMode.SELECT
        autoSelect = true
        v.inputMode = InputMode.SELECT
        v.addText(TextItem(x, y, w, emoji, size, 0xFF1F2430.toInt()), selectIt = true)
    }

    fun insertTable(rows: Int, cols: Int) {
        val v = view ?: return
        CrashLog.note("sisip tabel ${rows}x$cols")
        val w = minOf(page.width * 0.8f, v.visibleWidth() * 0.9f)
        val h = rows * 26f
        place({ x, y -> TableItem(x, y, w, h, rows, cols, 0xFF5A6070.toInt()) }, w, h)
    }

    // ---------- gambar kecil tiap halaman (daftar halaman) ----------

    /** Gambar kecil isi tiap halaman, ditampilkan samar di daftar halaman supaya mudah dikenali. */
    val pageThumbs = mutableStateMapOf<String, ImageBitmap>()
    private val thumbStale = HashSet<String>()
    private val thumbLoading = HashSet<String>()
    private val thumbLock = Mutex()
    private val thumbJobs = HashMap<String, Job>()

    /** Buat gambar kecil halaman ini bila belum ada atau sudah tertinggal. */
    fun requestThumb(meta: PageMeta) {
        if (meta.id in thumbLoading) return
        if (pageThumbs.containsKey(meta.id) && meta.id !in thumbStale) return
        thumbLoading.add(meta.id)
        thumbStale.remove(meta.id)
        main.launch {
            try {
                val content = pageContent[meta.id]?.copy()
                    ?: withContext(repo.writeDispatcher) { runCatching { repo.loadContent(noteId, meta.id) }.getOrNull() }
                    ?: return@launch
                // Satu per satu, supaya menggulir daftar halaman tetap ringan.
                val bmp = thumbLock.withLock {
                    withContext(Dispatchers.Default) {
                        runCatching { InkRenderer.renderThumbnail(meta, content, 160, repo.noteDir(noteId)) }.getOrNull()
                    }
                }
                if (bmp != null && pages.any { it.id == meta.id }) pageThumbs[meta.id] = bmp.asImageBitmap()
            } finally {
                thumbLoading.remove(meta.id)
                // Berubah lagi selagi digambar: buat ulang dari keadaan terbaru.
                if (meta.id in thumbStale) pages.firstOrNull { it.id == meta.id }?.let { requestThumb(it) }
            }
        }
    }

    /** Halaman berubah: perbarui gambar kecilnya setelah berhenti menulis sebentar. */
    private fun scheduleThumb(pageId: String) {
        thumbJobs[pageId]?.cancel()
        thumbJobs[pageId] = main.launch {
            delay(1200)
            thumbStale.add(pageId)
            pages.firstOrNull { it.id == pageId }?.let { requestThumb(it) }
        }
    }

    // ---------- suara: dikte, rekam, ubah rekaman jadi teks ----------

    /** Jendela suara yang sedang terbuka; null bila tertutup. */
    var voice by mutableStateOf<VoiceSession?>(null)
        private set
    private var dictation: Dictation? = null
    private var recorder: VoiceRecorder? = null
    private var recordFile: File? = null
    private var voiceJob: Job? = null
    private var player: MediaPlayer? = null
    private var playTick: Job? = null
    /** Berkas rekaman yang sedang diputar. */
    var playingFile by mutableStateOf<String?>(null)
        private set

    /** Bahasa suara mengikuti bahasa tulisan: Inggris bila dipilih, selain itu Indonesia. */
    private fun voiceLanguage(): String = if (Handwriting.language == InkLanguage.EN) "en-US" else "id-ID"

    /** Dikte: buka jendelanya dulu; mendengarkan baru dimulai saat tombol Mulai diketuk. */
    fun startDictation(@Suppress("UNUSED_PARAMETER") context: Context) {
        stopPlayback()
        finishEditing(commit = true)
        closeVoice()
        voice = VoiceSession(VoiceMode.DICTATE, voiceLanguage())
    }

    /** Tombol Mulai di jendela dikte atau rekam: mulai mendengarkan atau merekam sekarang. */
    fun beginVoice(context: Context) {
        val s = voice ?: return
        if (s.started) return
        when (s.mode) {
            VoiceMode.DICTATE -> {
                s.started = true
                beginDictation(context, s)
            }
            VoiceMode.RECORD -> beginRecording(context, s)
            VoiceMode.TRANSCRIBE -> Unit
        }
    }

    private fun beginDictation(context: Context, s: VoiceSession) {
        dictation?.destroy()
        s.status = null
        val d = Dictation(context.applicationContext, s.lang, object : Dictation.Listener {
            override fun onPartial(text: String) { if (voice === s) s.partial = text }
            override fun onFinal(text: String) {
                if (voice !== s) return
                s.text = if (s.text.isEmpty()) text else s.text + " " + text
                s.partial = ""
            }
            override fun onLevel(level: Float) { if (voice === s) s.level = level }
            override fun onProblem(message: String, fatal: Boolean) {
                if (voice !== s) return
                s.status = message
                if (fatal) s.listening = false
            }
            override fun onStopped() {
                if (voice !== s) return
                s.listening = false
                s.level = 0f
                if (s.partial.isNotBlank()) {
                    s.text = if (s.text.isEmpty()) s.partial else s.text + " " + s.partial
                    s.partial = ""
                }
            }
        })
        dictation = d
        s.listening = d.start()
    }

    fun setVoiceLanguage(context: Context, lang: String) {
        val s = voice ?: return
        if (s.lang == lang) return
        s.lang = lang
        if (s.mode == VoiceMode.DICTATE && s.listening) beginDictation(context, s)
    }

    fun pauseDictation() {
        dictation?.stop()
    }

    fun resumeDictation(context: Context) {
        val s = voice ?: return
        if (s.mode == VoiceMode.DICTATE) beginDictation(context, s)
    }

    /** Tempel hasil suara sebagai teks ketik: di bawah rekamannya, atau di tengah layar. */
    fun insertVoiceText() {
        val s = voice ?: return
        val raw = (s.text + " " + s.partial).trim()
        val anchor = s.anchor
        closeVoice()
        if (raw.isEmpty()) return
        val text = caps("", raw)
        val v = view ?: return
        if (anchor != null) {
            val size = lastTextSize
            val w = (page.width - anchor.x - 16f).coerceAtLeast(size * 6f)
            v.addText(TextItem(anchor.x, anchor.y + anchor.h + 10f, w, text, size, colorOf(Tool.PEN), lastFont), selectIt = mode == InputMode.SELECT)
        } else {
            insertText(text, lastFont, lastTextSize, colorOf(Tool.PEN))
        }
    }

    /** Rekam suara jadi benda rekaman di halaman: buka jendelanya dulu, merekam dimulai saat Mulai diketuk. */
    fun startRecording(@Suppress("UNUSED_PARAMETER") context: Context) {
        stopPlayback()
        finishEditing(commit = true)
        closeVoice()
        voice = VoiceSession(VoiceMode.RECORD, voiceLanguage())
    }

    private fun beginRecording(context: Context, s: VoiceSession) {
        val dir = repo.noteDir(noteId).apply { mkdirs() }
        val f = File(dir, "aud_" + UUID.randomUUID().toString() + ".m4a")
        val r = VoiceRecorder(context.applicationContext)
        try {
            r.start(f)
        } catch (e: Exception) {
            f.delete()
            notice = "Mikrofon tidak bisa dipakai. Tutup aplikasi lain yang sedang merekam, lalu coba lagi."
            return
        }
        recorder = r
        recordFile = f
        s.started = true
        s.listening = true
        voiceJob = main.launch {
            while (true) {
                s.elapsedMs = r.elapsedMs()
                s.level = if (r.paused) 0f else r.level()
                delay(100)
            }
        }
    }

    fun togglePauseRecording() {
        val r = recorder ?: return
        if (r.paused) r.resume() else r.pause()
        voice?.paused = r.paused
    }

    fun finishRecording() {
        val r = recorder ?: return
        val f = recordFile ?: return
        voiceJob?.cancel()
        recorder = null
        recordFile = null
        voice = null
        val ms = r.stop()
        if (ms < 0) {
            f.delete()
            notice = "Rekaman terlalu pendek, jadi tidak disimpan."
            return
        }
        place({ x, y -> AudioItem(x, y, 210f, 40f, f.name, ms) }, 210f, 40f)
        notice = "Rekaman ditempel. Ketuk untuk memutar, atau pilih lalu \u201cUbah jadi teks\u201d."
    }

    /** Ubah rekaman yang dipilih jadi teks. */
    fun transcribeSelectedAudio(context: Context) {
        val a = view?.selectedItem() as? AudioItem ?: return
        stopPlayback()
        closeVoice()
        val s = VoiceSession(VoiceMode.TRANSCRIBE, voiceLanguage(), anchor = a)
        s.listening = true
        s.status = "Membaca rekaman…"
        voice = s
        voiceJob = main.launch {
            val res = FileTranscriber.transcribe(context.applicationContext, File(repo.noteDir(noteId), a.file), s.lang) { p ->
                if (voice === s) s.text = p
            }
            if (voice !== s) return@launch
            s.listening = false
            if (res.text != null) {
                s.text = res.text
                s.status = null
            } else {
                s.status = res.problem
            }
        }
    }

    /** Tutup jendela suara; rekaman yang belum selesai dibuang. */
    fun closeVoice() {
        voiceJob?.cancel()
        voiceJob = null
        dictation?.destroy()
        dictation = null
        recorder?.cancel()
        recorder = null
        recordFile?.delete()
        recordFile = null
        voice = null
    }

    fun togglePlayback(a: AudioItem) {
        if (playingFile == a.file) {
            stopPlayback()
            return
        }
        stopPlayback()
        val f = File(repo.noteDir(noteId), a.file)
        val mp = MediaPlayer()
        try {
            mp.setDataSource(f.path)
            mp.prepare()
            mp.start()
        } catch (e: Exception) {
            mp.release()
            notice = "Rekaman tidak bisa diputar."
            return
        }
        player = mp
        playingFile = a.file
        mp.setOnCompletionListener { stopPlayback() }
        playTick = main.launch {
            while (true) {
                val p = player ?: break
                val d = p.duration.coerceAtLeast(1)
                view?.setPlayback(a.file, runCatching { p.currentPosition.toFloat() / d }.getOrDefault(0f))
                delay(80)
            }
        }
    }

    fun stopPlayback() {
        playTick?.cancel()
        playTick = null
        runCatching { player?.stop() }
        runCatching { player?.release() }
        player = null
        playingFile = null
        view?.setPlayback(null, 0f)
    }

    fun selectedAudio(): AudioItem? = view?.selectedItem() as? AudioItem

    fun playSelectedAudio() {
        val a = view?.selectedItem() as? AudioItem ?: return
        togglePlayback(a)
    }

    // ---------- mind map ----------

    /**
     * Mind map baru langsung di halaman: ide utama berwarna tema dengan tiga cabang kosong. Ide utama langsung
     * siap diketik.
     */
    fun newMindMap() {
        val v = view ?: return
        CrashLog.note("mind map baru")
        finishEditing(commit = true)
        v.endMapSession()
        val color = repo.theme.value.accent.light.toInt()
        val nodes = MindMaps.starter(color, MindMapRenderer.sizeOf)
        val probe = MindMapRenderer.toItem(nodes, 0f, 0f, 1f, true)
        val maxW = minOf(page.width * 0.92f, v.visibleWidth() * 0.9f)
        val maxH = page.height * 0.8f
        val s = minOf(1f, maxW / probe.mapW, maxH / probe.mapH)
        val (x, y) = centered(v, probe.mapW * s, probe.mapH * s)
        val item = MindMapRenderer.toItem(nodes, x, y, s, true)
        v.addItem(item, selectIt = false)
        val root = MindMaps.root(nodes)?.id ?: return
        v.beginMapSession(item, root)
        typeNode(root, Float.NaN, Float.NaN, selectAll = true)
        hintMindMap()
    }

    private fun hintMindMap() {
        if (!prefs.getBoolean("hint_mindmap_inline", false)) {
            prefs.edit().putBoolean("hint_mindmap_inline", true).apply()
            notice = "Ketuk ide untuk memilih, ketuk lagi untuk mengetik. Seret ide untuk memindahkan, Enter untuk ide berikutnya."
        }
    }

    /** Sunting mind map yang dipilih langsung di halaman. */
    fun editSelectedMindMap() {
        val m = view?.selectedItem() as? MindMapItem ?: return
        openMindMap(m, -1)
    }

    private fun openMindMap(m: MindMapItem, node: Int) {
        val v = view ?: return
        finishEditing(commit = true)
        v.beginMapSession(m, node)
        hintMindMap()
    }

    /** Mulai mengetik di ide [id] (kursor di titik layar [x],[y] bila ada). */
    private fun typeNode(id: Int, x: Float, y: Float, selectAll: Boolean = false) {
        val v = view ?: return
        finishEditing(commit = true)
        val (text, at) = v.beginNodeTyping(id, x, y) ?: return
        typeKind = TypeKind.NODE
        applyTypeValue(TextFieldValue(text, if (selectAll) TextRange(0, text.length) else TextRange(at.coerceIn(0, text.length))))
        editSession++
        editSuggestions = emptyList()
        keyboardTick++
    }

    fun mapTypeSelected() {
        val st = mapState ?: return
        if (st.selected >= 0) typeNode(st.selected, Float.NaN, Float.NaN)
    }

    /** Tambah ide di bawah ide terpilih ([sibling]: sejajar), lalu langsung mengetik di sana. */
    fun mapAdd(sibling: Boolean) {
        val v = view ?: return
        finishEditing(commit = true)
        val id = v.mapAdd(sibling)
        if (id >= 0) typeNode(id, Float.NaN, Float.NaN)
    }

    fun mapDelete() {
        finishEditing(commit = true)
        view?.mapDelete()
    }

    fun mapRecolor(color: Int) {
        finishEditing(commit = true)
        view?.mapRecolor(color)
    }

    fun mapTidy() {
        finishEditing(commit = true)
        view?.mapTidy()
    }

    /** Selesai menyunting mind map. */
    fun endMindMap() = endInline()

    // ---------- hapus latar gambar ----------

    /** Jendela hapus latar yang sedang terbuka; null bila tertutup. */
    var bgRemoval by mutableStateOf<BgRemoval?>(null)
        private set
    private var bgJob: Job? = null
    private var bgColorJob: Job? = null

    /** Siapkan dua cara sekaligus: otomatis (objek utama) dan latar polos (warna), supaya bisa dibandingkan. */
    fun startRemoveBackground(context: Context) {
        val img = selectedImage() ?: return
        val state = BgRemoval(img)
        bgRemoval = state
        bgJob?.cancel()
        bgJob = main.launch {
            val src = withContext(Dispatchers.IO) { runCatching { repo.loadNoteImage(noteId, img.file) }.getOrNull() }
            if (src == null) {
                state.status = null
                state.aiProblem = "Gambar tidak bisa dibuka."
                return@launch
            }
            state.source = src
            computeColorRemoval(state, immediate = true)
            state.status = "Mencari objek utama…"
            val res = BackgroundRemover.auto(context, src) { msg -> state.status = msg }
            state.status = null
            if (res.bitmap != null) {
                state.ai = res.bitmap
                state.useAi = true
            } else {
                state.aiProblem = res.problem
                state.useAi = false
            }
        }
    }

    private fun computeColorRemoval(state: BgRemoval, immediate: Boolean = false) {
        val src = state.source ?: return
        bgColorJob?.cancel()
        bgColorJob = main.launch {
            if (!immediate) delay(150)
            state.colorBusy = true
            val tol = state.tolerance
            val out = withContext(Dispatchers.Default) { runCatching { BackgroundRemover.byColor(src, tol) }.getOrNull() }
            if (out != null) state.colorResult = out
            state.colorBusy = false
        }
    }

    fun setBgTolerance(t: Float) {
        val s = bgRemoval ?: return
        s.tolerance = t
        computeColorRemoval(s)
    }

    fun chooseBgMethod(ai: Boolean) {
        val s = bgRemoval ?: return
        if (ai && s.ai == null) return
        s.useAi = ai
    }

    /** Simpan hasilnya sebagai gambar baru; gambar lama tetap ada, jadi bisa diurungkan. */
    fun applyRemoveBackground() {
        val s = bgRemoval ?: return
        val bmp = (if (s.useAi) s.ai else s.colorResult) ?: return
        val img = s.image
        closeRemoveBackground()
        main.launch {
            val name = withContext(Dispatchers.IO) { runCatching { repo.saveNoteImage(noteId, bmp) }.getOrNull() }
            val v = view
            if (name == null || v == null) {
                notice = "Gambar tidak bisa disimpan."
                return@launch
            }
            val next = img.withFile(name)
            if (v.replaceItem(img, next)) notice = "Latar gambar dihapus. Tombol urung mengembalikannya."
            else notice = "Gambarnya sudah berubah. Pilih lagi, lalu coba hapus latar sekali lagi."
        }
    }

    fun closeRemoveBackground() {
        bgJob?.cancel()
        bgColorJob?.cancel()
        bgRemoval = null
    }

    // ---------- stiker gambar ----------

    fun addStickers(context: Context, uris: List<Uri>) {
        if (uris.isEmpty()) return
        main.launch {
            val added = withContext(Dispatchers.IO) {
                uris.count { runCatching { repo.addSticker(context.applicationContext, it) }.getOrDefault(false) }
            }
            notice = if (added > 0) "$added gambar ditambahkan ke menu stiker." else "Gambar tidak bisa dibuka. Coba pilih gambar lain."
        }
    }

    fun deleteSticker(f: File) { repo.deleteSticker(f) }

    /** Simpan gambar yang dipilih ke menu stiker, supaya bisa ditempel lagi di catatan lain. */
    fun saveImageAsSticker() {
        val img = selectedImage() ?: return
        val src = File(repo.noteDir(noteId), img.file)
        main.launch {
            val ok = withContext(Dispatchers.IO) { runCatching { repo.addStickerFromFile(src) }.getOrDefault(false) }
            notice = if (ok) "Gambar ditambahkan ke menu stiker." else "Gambar ini tidak bisa dijadikan stiker."
        }
    }

    /** Tempel stiker gambar berukuran kecil di tengah layar; bisa langsung digeser dan diputar. */
    fun insertImageSticker(f: File) {
        main.launch {
            val res = withContext(Dispatchers.IO) { runCatching { repo.placeSticker(noteId, f) }.getOrNull() }
            if (res == null) {
                notice = "Stiker tidak bisa ditempel."
                return@launch
            }
            val (file, iw, ih) = res
            val longest = 150f
            val k = longest / maxOf(iw, ih).coerceAtLeast(1)
            val w = (iw * k).coerceAtLeast(24f)
            val h = (ih * k).coerceAtLeast(24f)
            place({ x, y -> ImageItem(x, y, w, h, file) }, w, h)
        }
    }

    // ---------- tanggal dan kalender ----------

    fun insertDate(text: String) = insertText(text, lastFont, lastTextSize, colorOf(Tool.PEN))

    /** Kalender satu bulan yang bisa ditulisi, berwarna sesuai warna utama aplikasi. */
    fun insertCalendar(year: Int, month: Int, mark: Int) {
        val v = view ?: return
        val w = minOf(page.width * 0.8f, v.visibleWidth() * 0.85f).coerceAtLeast(160f)
        val h = w * 0.8f
        val color = repo.theme.value.accent.light.toInt()
        place({ x, y -> CalendarItem(x, y, w, h, year, month, color, mark) }, w, h)
    }

    // ---------- bahasa tulisan dan pencarian ----------

    fun chooseInkLanguage(l: InkLanguage) {
        if (l == inkLanguage) return
        inkLanguage = l
        Handwriting.language = l
        prefs.edit().putString("hw_lang", l.name).apply()
        main.launch {
            val ready = runCatching { Handwriting.isReady() }.getOrDefault(false)
            if (ready) {
                notice = "Bahasa tulisan: ${l.label}."
                return@launch
            }
            notice = "Mengunduh kamus tulisan tangan (sekali saja, perlu internet)…"
            val ok = runCatching { Handwriting.download() }.isSuccess
            notice = if (ok) "Kamus siap. Bahasa tulisan: ${l.label}." else "Kamus belum bisa diunduh. Sambungkan internet, lalu coba lagi."
        }
    }

    /** Perbarui indeks pencarian halaman yang berubah (di latar, termasuk membaca tulisan tangan). */
    fun reindexChanged() {
        if (indexDirty.isEmpty()) return
        val work = indexDirty.mapNotNull { id ->
            val meta = pages.firstOrNull { it.id == id }
            val content = pageContent[id]
            if (meta != null && content != null) meta to content.copy() else null
        }
        indexDirty.clear()
        repo.appScope.launch {
            for ((meta, content) in work) runCatching { PageIndexer.index(repo, noteId, meta, content) }
        }
    }

    // ---------- ekspor ----------

    /** Buat berkas ekspor di latar, lalu serahkan ke layar untuk dibagikan atau disimpan. */
    /**
     * Kirim catatan ke Google Keep: tiap halaman jadi gambar, sedangkan teks ketik dan tulisan tangan yang terbaca
     * jadi isi catatan Keep (judulnya nama catatan ini). Keep tidak punya jalur sinkron untuk akun pribadi, jadi
     * ini membuat catatan baru di Keep setiap kali dikirim.
     */
    fun exportToKeep(context: Context, allPages: Boolean) {
        if (exportBusy != null) return
        finishEditing(commit = true)
        endInline()
        flush()
        val targets = if (allPages) pages.toList() else listOf(page)
        if (targets.isEmpty()) return
        CrashLog.note("kirim ke Keep, ${targets.size} halaman")
        exportBusy = "Menyiapkan…"
        main.launch {
            try {
                val dir = File(context.cacheDir, "export")
                withContext(Dispatchers.IO) { dir.deleteRecursively(); dir.mkdirs() }
                val base = Exporter.safeName(title)
                val imageDir = repo.noteDir(noteId)
                var ready = runCatching { Handwriting.isReady() }.getOrDefault(false)
                if (!ready) {
                    exportBusy = "Mengunduh kamus tulisan tangan…"
                    ready = runCatching { Handwriting.download() }.isSuccess
                }
                val files = ArrayList<File>()
                val body = StringBuilder()
                var allRead = true
                targets.forEachIndexed { i, meta ->
                    val content = pageContent[meta.id]?.copy() ?: withContext(repo.writeDispatcher) { repo.loadContent(noteId, meta.id) }
                    val number = if (allPages) pages.indexOfFirst { it.id == meta.id } + 1 else index + 1
                    exportBusy = "Membaca tulisan halaman ${i + 1} dari ${targets.size}…"
                    val (paras, complete) = runCatching { Exporter.pageParagraphs(meta, content, ready) }.getOrDefault(emptyList<String>() to false)
                    if (!complete) allRead = false
                    val text = paras.filter { it.isNotBlank() }.joinToString("\n")
                    if (text.isNotBlank()) {
                        if (targets.size > 1) {
                            if (body.isNotEmpty()) body.append("\n\n")
                            body.append("— Halaman ").append(number).append(" —\n")
                        } else if (body.isNotEmpty()) body.append("\n\n")
                        body.append(text)
                    }
                    exportBusy = "Menggambar halaman ${i + 1} dari ${targets.size}…"
                    val f = File(dir, if (targets.size == 1) "$base.jpg" else "$base - hal $number.jpg")
                    withContext(Dispatchers.Default) {
                        val bmp = Exporter.renderPage(meta, content, imageDir)
                        try { Exporter.writeImage(bmp, ExportFormat.JPG, f) } finally { bmp.recycle() }
                    }
                    files.add(f)
                }
                if (!allRead) notice = "Sebagian tulisan tangan belum terbaca jadi teks (kamus belum terunduh). Gambar halamannya tetap ikut."
                exportReady = ExportJob(files, ExportFormat.JPG, ExportAction.KEEP, title, body.toString())
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                CrashLog.nonFatal("Kirim ke Keep gagal", e)
                notice = if (e is OutOfMemoryError) "Halaman terlalu besar. Coba kirim per halaman." else "Gagal menyiapkan catatan untuk Google Keep. Coba lagi."
            } finally {
                exportBusy = null
            }
        }
    }

    fun export(context: Context, format: ExportFormat, allPages: Boolean, doc: DocContent, action: ExportAction, pick: List<Int>? = null) {
        if (exportBusy != null) return
        finishEditing(commit = true)
        flush()
        // [pick]: halaman terpilih saja (mis. mengambil sebagian halaman PDF jadi PDF baru).
        val targets = pick?.mapNotNull { pages.getOrNull(it) } ?: if (allPages) pages.toList() else listOf(page)
        val several = allPages || pick != null
        if (targets.isEmpty()) return
        CrashLog.note("ekspor ${format.name}, ${targets.size} halaman")
        exportBusy = "Menyiapkan…"
        main.launch {
            try {
                val dir = File(context.cacheDir, "export")
                withContext(Dispatchers.IO) { dir.deleteRecursively(); dir.mkdirs() }
                val base = Exporter.safeName(title)
                val imageDir = repo.noteDir(noteId)
                val files = ArrayList<File>()
                suspend fun contentOf(meta: PageMeta): PageContent =
                    pageContent[meta.id]?.copy() ?: withContext(repo.writeDispatcher) { repo.loadContent(noteId, meta.id) }

                when {
                    format == ExportFormat.PDF -> {
                        // Satu berkas PDF berisi semua halaman yang dipilih.
                        val parts = ArrayList<Pair<PageMeta, PageContent>>()
                        for (meta in targets) parts.add(meta to contentOf(meta))
                        val name = when {
                            pick != null && targets.size < pages.size -> "$base - hal ${pick.joinToString(",") { (it + 1).toString() }.take(40)}.pdf"
                            targets.size == 1 && pages.size > 1 -> "$base - hal ${index + 1}.pdf"
                            else -> "$base.pdf"
                        }
                        val f = File(dir, name)
                        withContext(Dispatchers.Default) {
                            Exporter.writePdf(parts, imageDir, f) { i -> exportBusy = "Menyusun PDF: halaman ${i + 1} dari ${targets.size}…" }
                        }
                        files.add(f)
                    }
                    format != ExportFormat.DOCX -> {
                        targets.forEachIndexed { i, meta ->
                            exportBusy = "Menggambar halaman ${i + 1} dari ${targets.size}…"
                            val content = contentOf(meta)
                            val number = if (several) pages.indexOfFirst { it.id == meta.id } + 1 else index + 1
                            val name = if (targets.size == 1 && pages.size == 1) "$base.${format.ext}" else "$base - hal $number.${format.ext}"
                            val f = File(dir, name)
                            withContext(Dispatchers.Default) {
                                val bmp = Exporter.renderPage(meta, content, imageDir)
                                try { Exporter.writeImage(bmp, format, f) } finally { bmp.recycle() }
                            }
                            files.add(f)
                        }
                    }
                    else -> {
                        val wantText = doc != DocContent.PAGES
                        val wantImage = doc != DocContent.TEXT
                        var ready = false
                        if (wantText) {
                            ready = runCatching { Handwriting.isReady() }.getOrDefault(false)
                            if (!ready) {
                                exportBusy = "Mengunduh kamus tulisan tangan…"
                                ready = runCatching { Handwriting.download() }.isSuccess
                            }
                        }
                        val parts = ArrayList<DocPage>()
                        var allRead = true
                        targets.forEachIndexed { i, meta ->
                            val content = contentOf(meta)
                            val paras = if (wantText) {
                                exportBusy = "Membaca tulisan halaman ${i + 1} dari ${targets.size}…"
                                val (p, complete) = Exporter.pageParagraphs(meta, content, ready)
                                if (!complete) allRead = false
                                p
                            } else null
                            val jpeg = if (wantImage) {
                                exportBusy = "Menggambar halaman ${i + 1} dari ${targets.size}…"
                                withContext(Dispatchers.Default) {
                                    val bmp = Exporter.renderPage(meta, content, imageDir)
                                    try { Exporter.jpegBytes(bmp) } finally { bmp.recycle() }
                                }
                            } else null
                            parts.add(DocPage(meta, paras, jpeg))
                        }
                        exportBusy = "Menyusun dokumen Word…"
                        val f = File(dir, "$base.docx")
                        withContext(Dispatchers.IO) { Exporter.writeDocx(title, parts, f) }
                        files.add(f)
                        if (!allRead) notice = "Sebagian tulisan tangan belum terbaca (kamus belum terunduh). Teks ketik dan gambar halaman tetap ikut."
                    }
                }
                exportReady = ExportJob(files, format, action)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                CrashLog.nonFatal("Ekspor ${format.name} gagal", e)
                notice = if (e is OutOfMemoryError) "Halaman terlalu besar untuk diekspor sekaligus. Coba ekspor per halaman."
                else "Ekspor gagal. Coba lagi."
            } finally {
                exportBusy = null
            }
        }
    }

    /** Salin hasil ekspor ke berkas yang dipilih pengguna. */
    fun saveExportTo(context: Context, uri: Uri?) {
        val job = exportReady ?: return
        exportReady = null
        if (uri == null) return
        main.launch {
            val ok = withContext(Dispatchers.IO) {
                runCatching {
                    context.contentResolver.openOutputStream(uri)?.use { out -> job.files[0].inputStream().use { it.copyTo(out) } } != null
                }.getOrDefault(false)
            }
            notice = if (ok) "Tersimpan: ${job.files[0].name}" else "Berkas tidak bisa disimpan di sana."
        }
    }

    /** Salin semua hasil ekspor ke folder yang dipilih pengguna. */
    fun saveExportToFolder(context: Context, tree: Uri?) {
        val job = exportReady ?: return
        exportReady = null
        if (tree == null) return
        main.launch {
            val saved = withContext(Dispatchers.IO) {
                val cr = context.contentResolver
                val parent = runCatching {
                    DocumentsContract.buildDocumentUriUsingTree(tree, DocumentsContract.getTreeDocumentId(tree))
                }.getOrNull() ?: return@withContext 0
                job.files.count { f ->
                    runCatching {
                        val target = DocumentsContract.createDocument(cr, parent, job.format.mime, f.name) ?: return@runCatching false
                        cr.openOutputStream(target)?.use { out -> f.inputStream().use { it.copyTo(out) } } != null
                    }.getOrDefault(false)
                }
            }
            notice = if (saved == job.files.size) "$saved berkas tersimpan." else "Hanya $saved dari ${job.files.size} berkas yang tersimpan."
        }
    }

    fun insertLink(url: String, label: String) {
        val u = normalizeUrl(url)
        if (u.isEmpty()) return
        val text = label.ifBlank { u.removePrefix("https://").removePrefix("http://") }
        val w = linkWidth(text)
        place({ x, y -> LinkItem(x, y, w, 26f, u, text) }, w, 26f)
    }

    private fun linkWidth(label: String) = (label.length * 6.6f + 46f).coerceIn(90f, page.width * 0.8f)

    fun editLink(url: String, label: String) {
        val v = view ?: return
        val old = v.selectedItem() as? LinkItem ?: return
        val u = normalizeUrl(url)
        if (u.isEmpty()) return
        val text = label.ifBlank { u.removePrefix("https://").removePrefix("http://") }
        v.replaceSelected(LinkItem(old.x, old.y, linkWidth(text), old.h, u, text), null)
    }

    fun editText(text: String, font: String, size: Float, color: Int, align: Align) {
        val v = view ?: return
        val old = v.selectedText() ?: return
        if (text.isBlank()) return
        rememberTextStyle(font, size)
        val clean = text.trim()
        val runs = if (clean == old.text) old.runs else old.runs.edited(old.text, clean, null)
        v.replaceSelected(null, TextItem(old.x, old.y, old.width, clean, size, color, font, align, runs))
    }

    fun selectedLink(): LinkItem? = view?.selectedItem() as? LinkItem

    /** Catatan lain yang diminta dibuka lewat link (id catatan, halaman ke-, atau -1). */
    var noteToOpen by mutableStateOf<Pair<String, Int>?>(null)

    /** Ikuti link: website dibuka di browser, link WInote langsung ke halaman atau catatannya. */
    fun followLink(url: String) {
        val target = InternalLink.parse(url)
        if (target == null) {
            linkToOpen = url
            return
        }
        val (otherNote, pageId) = target
        if (otherNote == null || otherNote == noteId) {
            val i = if (pageId == null) 0 else pages.indexOfFirst { it.id == pageId }
            if (i < 0) {
                notice = "Halaman tujuan link ini sudah dihapus."
                return
            }
            CrashLog.note("link ke halaman")
            if (i != index) showPage(i)
            notice = "Halaman ${i + 1}"
            return
        }
        val n = repo.note(otherNote)
        if (n == null) {
            notice = "Catatan tujuan link ini sudah dihapus."
            return
        }
        val i = pageId?.let { pid -> repo.loadPages(otherNote).indexOfFirst { it.id == pid } } ?: -1
        CrashLog.note("link ke catatan lain")
        flush()
        noteToOpen = otherNote to i
    }

    /** Teks pendek isi halaman ke-[i], untuk daftar tujuan link. */
    fun pageLabel(i: Int): String {
        val p = pages.getOrNull(i) ?: return ""
        return repo.firstLine(noteId, p.id)
    }

    fun otherNotes(): List<id.hananote.data.Note> = repo.notes.value.filter { it.id != noteId && it.art.isEmpty() }
    fun bookTitle(bookId: String): String = repo.books.value.firstOrNull { it.id == bookId }?.title.orEmpty()
    fun pagesOf(otherNote: String): List<PageMeta> = repo.loadPages(otherNote)
    fun selectedImage(): ImageItem? = view?.selectedItem() as? ImageItem
    fun imageDir(): File = repo.noteDir(noteId)

    /** Putar gambar atau stiker 90° searah jarum jam, beserta tulisan di atasnya. */
    fun rotateSelection() {
        val v = view ?: return
        val item = v.selectedItem()
        if (item == null) {
            // Tulisan tangan (dan benda) yang dilingkari: putar 90° searah jarum jam tiap ketukan.
            v.rotateSelectionBy(90f)
            return
        }
        if (!item.canRotate) return
        // Ke kelipatan 90° berikutnya, jadi benda yang miring sedikit langsung lurus kembali.
        val next = PageItem.normalizeDeg(kotlin.math.floor(item.rotation / 90f + 0.01f) * 90f + 90f)
        v.replaceSelectedItem(item.placed(item.x, item.y, item.w, item.h, next), moveInk = true)
    }

    /** Balik gambar kiri-kanan atau atas-bawah. */
    fun flipSelection(horizontal: Boolean) {
        val img = selectedImage() ?: return
        view?.replaceSelectedItem(img.flipped(horizontal), moveInk = false)
    }

    /** Pasang, ubah, atau lepas bayangan gambar yang dipilih. */
    fun setImageShadow(shadow: Shadow?) {
        val img = selectedImage() ?: return
        if (img.shadow == shadow) return
        view?.replaceSelectedItem(img.withShadow(shadow), moveInk = false)
    }

    fun cropSelection(crop: Crop) {
        val img = selectedImage() ?: return
        if (crop == img.crop) return
        view?.replaceSelectedItem(img.cropped(crop), moveInk = false)
    }

    fun deleteSelection() { CrashLog.note("hapus pilihan"); view?.deleteSelection() }
    fun groupSelection() {
        CrashLog.note("kelompokkan")
        view?.groupSelection()
        notice = "Dikelompokkan. Ketuk salah satunya untuk memilih seluruh kelompok."
    }
    fun ungroupSelection() { CrashLog.note("pisahkan kelompok"); view?.ungroupSelection() }
    fun arrange(op: InkView.Arrange) {
        val v = view ?: return
        CrashLog.note("urutan: ${op.name}")
        if (!v.arrangeSelection(op)) {
            notice = if (op == InkView.Arrange.FRONT || op == InkView.Arrange.FORWARD) "Sudah paling depan." else "Sudah paling belakang."
        }
    }
    fun duplicateSelection() { CrashLog.note("duplikat pilihan"); view?.duplicateSelection() }
    fun clearSelection() { view?.clearSelection() }

    /** Tombol Selesai: lepas pilihan dan kembali menulis dengan pena. */
    fun finishSelection() {
        view?.clearSelection()
        if (mode == InputMode.SELECT) {
            autoSelect = false
            mode = writingMode
        }
    }

    private fun normalizeUrl(raw: String): String {
        val t = raw.trim()
        if (t.isEmpty()) return ""
        if (InternalLink.isInternal(t)) return t
        return if (t.startsWith("http://", true) || t.startsWith("https://", true)) t else "https://$t"
    }

    fun toggleFinger() {
        fingerDraws = !fingerDraws
        prefs.edit().putBoolean("finger_draws", fingerDraws).putBoolean("stylus_seen", true).apply()
    }

    fun undo() { if (readMode) return; CrashLog.note("urungkan"); finishEditing(commit = true); view?.undo() }
    fun redo() { if (readMode) return; CrashLog.note("ulangi"); finishEditing(commit = true); view?.redo() }

    // ---------- sunting teks dengan keyboard ----------

    /** Mulai mengetik di teks [t] langsung di halaman, kursor di huruf ke-[cursor] (-1: di akhir). */
    fun startEditing(t: TextItem, cursor: Int = -1) {
        val v = view ?: return
        autoJob?.cancel()
        finishEditing(commit = true)
        v.endMapSession()
        if (v.beginTextEdit(t)) {
            val at = if (cursor in 0..t.text.length) cursor else t.text.length
            typeKind = TypeKind.TEXT
            applyTypeValue(TextFieldValue(t.text, TextRange(at)))
            editSession++
            editSuggestions = alternatives[t]?.let { (prefix, cands) ->
                cands.map { it to prefix + it }.filter { it.second != t.text }
            } ?: emptyList()
            keyboardTick++
        }
    }

    /** Kotak teks baru di tengah layar, langsung diketik di halaman (tanpa jendela). */
    fun newText(initial: String = "") {
        val v = view ?: return
        CrashLog.note("teks baru di halaman")
        finishEditing(commit = true)
        v.endMapSession()
        val size = lastTextSize
        val w = minOf(page.width * 0.7f, v.visibleWidth() * 0.8f)
        val (x, y) = centered(v, w, size * 1.4f)
        val ruled = RuledLines.of(page)
        val item = if (ruled != null) ruledText(x, y + size, w, size, newTextColor(), lastFont, ruled, initial)
        else TextItem(x, y, w, initial, size, newTextColor(), lastFont)
        if (v.beginNewText(item)) {
            typeKind = TypeKind.TEXT
            applyTypeValue(TextFieldValue(initial, TextRange(initial.length)))
            editSession++
            editSuggestions = emptyList()
            keyboardTick++
        }
    }

    /** Ketik isi stiker langsung di atasnya. */
    fun editStickyInline(st: StickyItem? = selectedSticky()) {
        val v = view ?: return
        val target = st ?: return
        finishEditing(commit = true)
        v.endMapSession()
        if (v.beginStickyEdit(target)) {
            typeKind = TypeKind.STICKY
            applyTypeValue(TextFieldValue(target.text, TextRange(target.text.length)))
            editSession++
            editSuggestions = emptyList()
            keyboardTick++
        }
    }

    /** Ketik isi sel tabel langsung di dalam selnya (ketuk dua kali tabel, atau ketuk di mode Teks). */
    fun editCell(t: TableItem, r: Int, c: Int) {
        if (readMode) return
        val v = view ?: return
        finishEditing(commit = true)
        v.endMapSession()
        if (v.beginCellEdit(t, r, c)) {
            CrashLog.note("ketik sel tabel")
            typeKind = TypeKind.CELL
            val text = v.typingCell()?.let { (tt, rr, cc) -> tt.cell(rr, cc) } ?: ""
            applyTypeValue(TextFieldValue(text, TextRange(text.length)))
            editSession++
            editSuggestions = emptyList()
            keyboardTick++
            if (!prefs.getBoolean("hint_table_cell", false)) {
                prefs.edit().putBoolean("hint_table_cell", true).apply()
                notice = "Mengetik di sel tabel: ketuk sel lain untuk pindah, atau pakai tombol ‹ › di bilah atas."
            }
        }
    }

    /** Pindah ke sel sebelumnya/berikutnya; setelah sel terakhir, baris baru ditambahkan. */
    fun cellStep(dir: Int) {
        val v = view ?: return
        val (t, r, c) = v.typingCell() ?: return
        var k = r * t.cols + c + dir
        val id = t.id
        if (k < 0) k = 0
        if (k >= t.rows * t.cols) {
            finishEditing(commit = true)
            val grown = v.addTableRow(id, t.rows - 1) ?: return
            editCell(grown, grown.rows - 1, 0)
            return
        }
        editCell(t, k / t.cols, k % t.cols)
    }

    /** Tambah baris kosong di bawah sel yang sedang diketik, lalu lanjut mengetik di baris itu. */
    fun cellAddRow() {
        val v = view ?: return
        val (t, r, c) = v.typingCell() ?: return
        finishEditing(commit = true)
        val grown = v.addTableRow(t.id, r) ?: return
        editCell(grown, r + 1, c)
    }

    private fun applyTypeValue(value: TextFieldValue) {
        typeValue = value
        view?.setTypeSelection(value.selection.start, value.selection.end, value.composition?.start ?: -1, value.composition?.end ?: -1)
    }

    /** Ketikan dari keyboard. */
    fun onTypeInput(new: TextFieldValue) {
        val old = typeValue ?: return
        if (typeKind == TypeKind.CELL && new.text.length == old.text.length + 1 && new.text.count { it == '\t' } > old.text.count { it == '\t' }) {
            // Tab di sel tabel (keyboard fisik): pindah ke sel berikutnya, seperti di pengolah kata.
            cellStep(1)
            return
        }
        if (typeKind == TypeKind.NODE && new.text.contains('\n')) {
            // Enter di ide mind map: lanjut ke ide sejajar berikutnya.
            val clean = new.text.replace("\n", "")
            if (clean != old.text) view?.updateTyping(clean)
            typeValue = TextFieldValue(clean, TextRange(clean.length))
            mapAdd(sibling = true)
            return
        }
        val next = if (typeKind == TypeKind.NODE) new else continueList(old, new)
        if (next.text != old.text) view?.updateTyping(next.text)
        applyTypeValue(next)
    }

    /** Ganti isi dan kursor sekaligus (tempel, potong, daftar, bacaan lain). */
    fun replaceTyping(value: TextFieldValue) {
        val old = typeValue ?: return
        val clean = if (typeKind == TypeKind.NODE) value.copy(text = value.text.replace("\n", " ")) else value
        if (clean.text != old.text) view?.updateTyping(clean.text)
        applyTypeValue(clean)
    }

    /** Kursor atau blok pilihan dipindah dengan ketukan/seretan di halaman. */
    private fun moveCursor(a: Int, b: Int) {
        if (sizeEntry != null) commitSizeEntry()
        val v = typeValue ?: return
        val n = v.text.length
        applyTypeValue(TextFieldValue(v.text, TextRange(a.coerceIn(0, n), b.coerceIn(0, n))))
    }

    /** Keyboard ditutup: ketikan selesai (mind map tetap terbuka untuk disunting). */
    fun onKeyboardHidden() {
        if (pickingFont) return
        if (sizeEntry != null) {
            commitSizeEntry()
            return
        }
        if (typeValue != null) finishEditing(commit = true)
    }

    /** Gaya teks yang sedang diketik, untuk bilah alat; null bila bukan teks atau stiker. */
    class TypeStyle(val font: String, val size: Float, val color: Int?, val align: Align)

    fun typingStyle(): TypeStyle? {
        val v = view ?: return null
        return when (typeKind) {
            TypeKind.TEXT -> v.typingText()?.let { TypeStyle(it.font, it.size, it.color, it.align) }
            TypeKind.STICKY -> v.typingSticky()?.let { TypeStyle(it.font, it.textSize, null, it.align) }
            TypeKind.CELL -> v.typingCell()?.first?.let { TypeStyle(it.font, it.textSize, null, it.align) }
            TypeKind.NODE -> null
        }
    }

    fun typeFont(font: String) {
        val v = view ?: return
        when (typeKind) {
            TypeKind.TEXT -> v.styleTextEdit(font = font)
            TypeKind.STICKY -> v.styleStickyEdit(font = font)
            TypeKind.CELL -> v.styleCellEdit(font = font)
            TypeKind.NODE -> return
        }
        // Gaya tabel tidak mengubah gaya bawaan teks baru.
        if (typeKind != TypeKind.CELL) typingStyle()?.let { rememberTextStyle(it.font, it.size) }
        typeStyleTick++
    }

    /** Mulai mengetik angka ukuran huruf (keyboard angka), dari bilah alat teks. */
    fun beginSizeEntry() {
        val st = typingStyle() ?: return
        val t = sizeLabel(st.size)
        sizeEntry = TextFieldValue(t, TextRange(0, t.length))
        keyboardTick++
    }

    /** Angka ukuran diketik: langsung dicoba di teksnya bila sudah masuk akal. */
    fun onSizeEntry(v: TextFieldValue) {
        val clean = v.text.filter { it.isDigit() || it == ',' || it == '.' }.take(5)
        sizeEntry = if (clean == v.text) v else TextFieldValue(clean, TextRange(clean.length))
        parseSize(clean)?.let { if (it in MIN_TEXT_SIZE..MAX_TEXT_SIZE) typeSize(it) }
    }

    /** Selesai mengetik angka ukuran: pakai, lalu kembali mengetik teksnya. */
    fun commitSizeEntry() {
        val e = sizeEntry ?: return
        sizeEntry = null
        parseSize(e.text)?.let { typeSize(it) }
        keyboardTick++
    }

    /** Ukuran huruf teks atau isi stiker yang sedang dipilih (bukan sedang diketik). */
    fun setSelectedTextSize(size: Float) {
        val v = view ?: return
        val s = size.coerceIn(MIN_TEXT_SIZE, MAX_TEXT_SIZE)
        v.selectedText()?.let { t ->
            if (t.size != s) v.replaceSelected(null, t.moved(t.x, t.y, t.width, s))
            rememberTextStyle(t.font, s)
            return
        }
        (v.selectedItem() as? StickyItem)?.let { st ->
            if (st.textSize != s) v.replaceSelected(st.withText(st.text, st.font, s, st.align), null)
        }
    }

    fun typeSize(size: Float) {
        val v = view ?: return
        val s = size.coerceIn(MIN_TEXT_SIZE, MAX_TEXT_SIZE)
        when (typeKind) {
            TypeKind.TEXT -> v.styleTextEdit(size = s)
            TypeKind.STICKY -> v.styleStickyEdit(size = s)
            TypeKind.CELL -> v.styleCellEdit(size = s)
            TypeKind.NODE -> return
        }
        // Gaya tabel tidak mengubah gaya bawaan teks baru.
        if (typeKind != TypeKind.CELL) typingStyle()?.let { rememberTextStyle(it.font, it.size) }
        typeStyleTick++
    }

    fun typeColor(color: Int) {
        if (typeKind != TypeKind.TEXT) return
        view?.styleTextEdit(color = color)
        setNewTextColor(color)
        typeStyleTick++
    }

    fun typeAlign(a: Align) {
        val v = view ?: return
        when (typeKind) {
            TypeKind.TEXT -> v.styleTextEdit(align = a)
            TypeKind.STICKY -> v.styleStickyEdit(align = a)
            TypeKind.CELL -> v.styleCellEdit(align = a)
            TypeKind.NODE -> return
        }
        typeStyleTick++
    }

    fun typeList(k: TextLists.Kind) {
        val cur = typeValue ?: return
        if (typeKind == TypeKind.NODE) return
        // Hanya paragraf di kursor atau pilihan, seperti tombol daftar di pengolah kata.
        val (next, a, b) = TextLists.toggleAt(cur.text, cur.selection.min, cur.selection.max, k)
        replaceTyping(TextFieldValue(next, TextRange(a, b)))
    }

    fun editSelectedText() {
        val t = view?.selectedText() ?: return
        startEditing(t)
    }

    /** Minta keyboard muncul lagi untuk yang sedang diketik. */
    fun showKeyboard() { keyboardTick++ }

    /** Selesaikan ketikan dan sesi mind map (misalnya saat ganti alat). */
    fun endInline() {
        finishEditing(commit = true)
        view?.endMapSession()
    }

    fun finishEditing(commit: Boolean) {
        if (typeValue == null) return
        val kind = typeKind
        sizeEntry?.let { e -> sizeEntry = null; parseSize(e.text)?.let { typeSize(it) } }
        typeValue = null
        pickingFont = false
        view?.endTyping(commit)
        editSuggestions = emptyList()
        // Tulisan tangan yang menunggu diubah jadi teks diproses setelah menyunting.
        if (kind == TypeKind.TEXT && autoText && pendingInk.isNotEmpty()) {
            autoJob?.cancel()
            autoJob = main.launch {
                delay(400)
                runAutoText()
            }
        }
    }

    /** Beri tahu sekali bahwa teks hasil ubah bisa diketuk untuk diperbaiki. */
    private fun hintTapToEdit() {
        if (!prefs.getBoolean("hint_tap_edit", false)) {
            prefs.edit().putBoolean("hint_tap_edit", true).apply()
            notice = "Ada yang salah baca? Ketuk dua kali teksnya untuk memperbaiki dengan keyboard."
        }
    }
    fun clearPage() { CrashLog.note("bersihkan halaman"); view?.clearPage() }

    fun rename(t: String) {
        if (t.isBlank()) return
        title = t
        repo.renameNote(noteId, t)
    }

    // ---------- PDF: pilih teks, stabilo, ubah teks, formulir, tanda tangan, putar ----------

    /** Pilihan teks PDF yang aktif (bilah aksinya tampil di atas kanvas). */
    var pdfSel by mutableStateOf<PdfSelection?>(null)
        private set
    /** Mode isi formulir PDF: kolom-kolom formulir tampil dan bisa diketuk. */
    var pdfForm by mutableStateOf(false)
        private set
    /** Warna stabilo untuk teks PDF. */
    var pdfMarkColor by mutableIntStateOf(prefs.getInt("pdf_mark_color", HighlighterColors[0]))
        private set
    /** Teks PDF yang sedang diubah (dialog); null bila tidak ada. */
    var pdfEditing by mutableStateOf<String?>(null)
    /** Kolom formulir yang sedang diisi (dialog). */
    var pdfFieldEditing by mutableStateOf<PdfFieldBox?>(null)
    /** Dialog tanda tangan terbuka; [signatureTarget] kotak kolom tanda tangan bila dibuka dari formulir. */
    var signatureOpen by mutableStateOf(false)
    private var signatureTarget: RectF? = null
    private var pdfNoTextNoticeAt = 0L

    fun dismissPdfSelection() {
        view?.clearPdfSelection()
        pdfSel = null
    }

    fun choosePdfMarkColor(c: Int) {
        pdfMarkColor = c
        prefs.edit().putInt("pdf_mark_color", c).apply()
    }

    /** Berkas PDF dan nomor halaman di balik halaman [meta], bila halaman PDF. */
    private fun pdfSource(meta: PageMeta): Pair<File, Int>? =
        PageBackground.pdfOf(meta.background)?.let { (name, i) -> File(repo.noteDir(noteId), name) to i }

    /** Alat Teks PDF: pilih teks PDF untuk distabilo, digaris bawah, dicoret, disalin, atau diubah. */
    fun selectPdfText() {
        if (readMode) return
        endInline(); convertPick = false; convertBackToInk = false
        mode = InputMode.PDF
        pdfForm = false
        view?.pdfFormMode = false
        CrashLog.note("mode teks PDF")
        loadPdfLayer(announce = true)
    }

    /** Isi formulir PDF: kolom formulir ditampilkan dan bisa diketuk untuk diisi. */
    fun togglePdfForm() {
        if (readMode) return
        if (pdfForm && mode == InputMode.PDF) {
            pdfForm = false
            view?.pdfFormMode = false
            notice = "Isi formulir selesai."
            return
        }
        endInline(); convertPick = false; convertBackToInk = false
        mode = InputMode.PDF
        pdfForm = true
        view?.pdfFormMode = true
        CrashLog.note("mode isi formulir PDF")
        loadPdfLayer(announce = false)
        loadPdfFields(announce = true)
    }

    /** Siapkan teks PDF halaman ini untuk kanvas (dibaca di latar). */
    private fun loadPdfLayer(announce: Boolean) {
        val v = view ?: return
        val meta = page
        val src = pdfSource(meta)
        if (src == null) {
            v.pdfLayer = null
            if (announce) notice = "Halaman ini bukan halaman PDF. Buka PDF lewat Halaman › Impor PDF, lalu pakai Pilih teks di sana."
            return
        }
        val rot = PageBackground.pdfRotation(meta.background)
        PdfText.loadText(v.context, src.first, src.second) { t ->
            if (view !== v || page.id != meta.id) return@loadText
            v.pdfLayer = t?.let { PdfTextLayer(it, meta.width, meta.height, rot) }
            if (announce) notice = when {
                t == null -> "Teks di PDF ini tidak bisa dibaca."
                t.isEmpty -> "Halaman PDF ini tidak berisi teks (mungkin hasil pindaian), jadi teksnya tidak bisa dipilih."
                else -> "Ketuk kata untuk memilihnya (ketuk lagi: satu baris), atau tarik di atas teks. Geser halaman dengan dua jari."
            }
        }
    }

    private fun loadPdfFields(announce: Boolean) {
        val v = view ?: return
        val meta = page
        val src = pdfSource(meta)
        if (src == null) {
            v.pdfFields = emptyList()
            if (announce) notice = "Halaman ini bukan halaman PDF."
            return
        }
        val rot = PageBackground.pdfRotation(meta.background)
        PdfText.loadFields(v.context, src.first, src.second) { list ->
            if (view !== v || page.id != meta.id) return@loadFields
            v.pdfFields = list.map { f -> PdfFieldBox(f, PdfGeom.toPage(f.l, f.t, f.r, f.b, meta.width, meta.height, rot)) }
            if (announce) notice = if (list.isEmpty()) {
                "Halaman ini tidak punya kolom formulir PDF. Isi dengan Teks atau tulisan tangan, dan pakai Tanda tangan untuk menandatangani."
            } else {
                "${list.size} kolom formulir. Ketuk kolom untuk mengisinya; kotak centang diketuk untuk mencentang."
            }
        }
    }

    /** Dipanggil tiap halaman tampil: teks/kolom PDF halaman baru, penanda baca, dan nomor halaman. */
    private fun onPageShown() {
        val v = view ?: return
        pdfSel = null
        markSearchPage()
        presentationFollow(changed = false)
        if (mode == InputMode.PDF) {
            loadPdfLayer(announce = false)
            if (pdfForm) loadPdfFields(announce = false) else v.pdfFields = emptyList()
        } else {
            v.pdfLayer = null
            v.pdfFields = emptyList()
        }
        if (readMode) {
            prefs.edit().putString("lastread_$noteId", page.id).apply()
            if (resumePageId == page.id) resumeIndex = null
        }
        v.bookmarkRibbon = readMode && ribbonPageId == page.id
    }

    private fun pdfNoText() {
        val now = System.currentTimeMillis()
        if (now - pdfNoTextNoticeAt < 4000) return
        pdfNoTextNoticeAt = now
        notice = if (pdfSource(page) == null) "Halaman ini bukan halaman PDF." else "Tidak ada teks PDF di sini. Ketuk tepat di atas tulisan PDF."
    }

    /** Stabilo (0), garis bawah (1), atau coret (2) teks PDF yang dipilih. */
    fun pdfMark(kind: Int) {
        val v = view ?: return
        val layer = v.pdfLayer ?: return
        val r = v.pdfSelectionRange() ?: return
        val line = colorOf(Tool.PEN)
        val marks = layer.rects(r.first, r.last).map { rc ->
            when (kind) {
                0 -> {
                    val h = rc.height()
                    val inset = min(h / 2f, rc.width() / 2f)
                    Stroke(Tool.HIGHLIGHTER, pdfMarkColor, h, floatArrayOf(rc.left + inset, rc.right - inset), floatArrayOf(rc.centerY(), rc.centerY()), floatArrayOf(0.5f, 0.5f))
                }
                1 -> {
                    val w = max(0.8f, rc.height() * 0.07f)
                    val y = rc.bottom - rc.height() * 0.12f
                    Stroke(Tool.LINE, line, w, floatArrayOf(rc.left, rc.right), floatArrayOf(y, y), floatArrayOf(0.5f, 0.5f))
                }
                else -> {
                    val w = max(0.8f, rc.height() * 0.07f)
                    val y = rc.top + rc.height() * 0.5f
                    Stroke(Tool.LINE, line, w, floatArrayOf(rc.left, rc.right), floatArrayOf(y, y), floatArrayOf(0.5f, 0.5f))
                }
            }
        }
        CrashLog.note("tanda teks PDF $kind: ${marks.size} baris")
        v.addMarks(marks, emptyList(), emptyList())
        v.clearPdfSelection()
    }

    fun pdfCopy(context: Context) {
        val s = pdfSel ?: return
        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as? android.content.ClipboardManager ?: return
        cm.setPrimaryClip(ClipData.newPlainText("Teks PDF", s.text))
        notice = "Teks disalin."
        view?.clearPdfSelection()
    }

    /** Perluas pilihan ke seluruh baris yang tersentuh. */
    fun pdfSelectLines() {
        val v = view ?: return
        val layer = v.pdfLayer ?: return
        val r = v.pdfSelectionRange() ?: return
        v.selectPdfRange(layer.lineRange(r.first).first, layer.lineRange(r.last).last)
    }

    fun pdfBeginEdit() {
        pdfEditing = pdfSel?.text ?: return
    }

    /**
     * Ganti teks PDF yang dipilih dengan [newText]: tulisan aslinya ditutup bidang berwarna kertas, lalu teks baru
     * ditaruh di garis dasar yang sama dengan ukuran, jenis huruf, dan warna yang mirip. Penutup dan teksnya
     * dikelompokkan, jadi bisa digeser, diubah, atau dihapus bersama (dan diurungkan).
     */
    fun pdfApplyEdit(newText: String) {
        pdfEditing = null
        val v = view ?: return
        val layer = v.pdfLayer ?: return
        val r = v.pdfSelectionRange() ?: return
        val rects = layer.rects(r.first, r.last)
        if (rects.isEmpty()) return
        val meta = page
        val (paper, ink) = sampleColors(meta, rects)
        val pad = 0.8f
        val covers = rects.map { rc -> CoverItem(rc.left - pad, rc.top - pad, rc.width() + pad * 2f, rc.height() + pad * 2f, paper) }
        val size = layer.typicalSize(r.first, r.last).coerceIn(4f, 200f)
        val font = if (layer.serifAt(r.first)) "sys:serif" else FontRegistry.DEFAULT
        val text = newText.trimEnd()
        val texts = ArrayList<TextItem>()
        if (text.isNotBlank()) {
            val first = rects[0]
            // Jarak baris mengikuti PDF bila lebih dari satu baris.
            val pitch = if (rects.size > 1) (rects.last().bottom - rects.first().bottom) / (rects.size - 1) else 0f
            val runs = if (layer.boldAt(r.first)) TextRuns.EMPTY.styled(0, text.length, TextRuns.BOLD, true, text.length) else TextRuns.EMPTY
            val original = rects.maxOf { it.width() }
            val room = max(original, meta.width - first.left - size)
            val width = if (rects.size == 1) {
                // Satu baris tetap satu baris: lebar kotak mengikuti panjang teks baru (sampai tepi kanan halaman),
                // supaya teks yang lebih panjang tidak turun menimpa baris di bawahnya.
                val wide = InkRenderer.layoutFor(TextItem(first.left, 0f, 100_000f, text, size, ink, font, Align.LEFT, runs, pitch))
                val desired = (0 until wide.lineCount).maxOfOrNull { wide.getLineWidth(it) } ?: original
                (max(desired, original) + size * 0.6f).coerceAtMost(room)
            } else {
                // Paragraf: dibungkus selebar paragraf aslinya.
                (original + size * 0.6f).coerceAtMost(room)
            }
            val probe = TextItem(first.left, 0f, width, text, size, ink, font, Align.LEFT, runs, pitch)
            val baseOffset = InkRenderer.layoutFor(probe).getLineBaseline(0).toFloat()
            // Garis dasar PDF: sedikit di atas tepi bawah kotak huruf (ruang untuk kaki huruf).
            val baseline = first.bottom - size * 0.22f
            texts.add(TextItem(first.left, baseline - baseOffset, width, text, size, ink, font, Align.LEFT, runs, pitch))
        }
        CrashLog.note("ubah teks PDF: ${rects.size} baris")
        v.addMarks(emptyList(), texts, covers, groupIt = true)
        v.clearPdfSelection()
        notice = if (texts.isEmpty()) "Teks PDF ditutup." else "Teks diganti. Ketuk Pilih untuk menggeser atau mengubahnya lagi."
    }

    /**
     * Warna kertas di sekitar kotak-kotak [rects] dan warna tulisan di dalamnya, dari gambar halaman PDF yang sudah
     * tergambar. Bila belum ada gambarnya: putih dan hitam.
     */
    private fun sampleColors(meta: PageMeta, rects: List<RectF>): Pair<Int, Int> {
        val src = pdfSource(meta) ?: return 0xFFFFFFFF.toInt() to 0xFF111111.toInt()
        val bmp = PdfPages.anyCached(src.first, src.second) ?: return 0xFFFFFFFF.toInt() to 0xFF111111.toInt()
        val rot = PageBackground.pdfRotation(meta.background)
        fun px(x: Float, y: Float): Int? {
            // Titik halaman → pecahan halaman PDF sebelum diputar → piksel gambar.
            val u = x / meta.width
            val w = y / meta.height
            val (sx, sy) = when (rot) {
                90 -> w to (1f - u)
                180 -> (1f - u) to (1f - w)
                270 -> (1f - w) to u
                else -> u to w
            }
            val ix = (sx * bmp.width).toInt()
            val iy = (sy * bmp.height).toInt()
            if (ix !in 0 until bmp.width || iy !in 0 until bmp.height) return null
            return runCatching { bmp.getPixel(ix, iy) }.getOrNull()
        }
        val outside = ArrayList<Int>()
        val inside = ArrayList<Int>()
        for (rc in rects) {
            val d = max(1.5f, rc.height() * 0.12f)
            for (k in 0..8) {
                val x = rc.left + rc.width() * k / 8f
                px(x, rc.top - d)?.let { outside.add(it) }
                px(x, rc.bottom + d)?.let { outside.add(it) }
            }
            px(rc.left - d, rc.centerY())?.let { outside.add(it) }
            px(rc.right + d, rc.centerY())?.let { outside.add(it) }
            for (yy in 1..5) for (xx in 0..24) {
                px(rc.left + rc.width() * xx / 24f, rc.top + rc.height() * yy / 6f)?.let { inside.add(it) }
            }
        }
        if (outside.isEmpty()) return 0xFFFFFFFF.toInt() to 0xFF111111.toInt()
        fun lum(c: Int) = 0.299f * android.graphics.Color.red(c) + 0.587f * android.graphics.Color.green(c) + 0.114f * android.graphics.Color.blue(c)
        // Warna kertas: median menurut terang (tulisan yang kebetulan tersentuh tidak ikut).
        val paper = outside.sortedBy { lum(it) }[outside.size * 2 / 3]
        fun dist(a: Int) = abs(android.graphics.Color.red(a) - android.graphics.Color.red(paper)) +
            abs(android.graphics.Color.green(a) - android.graphics.Color.green(paper)) + abs(android.graphics.Color.blue(a) - android.graphics.Color.blue(paper))
        val strong = inside.sortedByDescending { dist(it) }.take(max(1, inside.size / 12))
        val ink = if (strong.isEmpty() || dist(strong[0]) < 60) 0xFF111111.toInt() else {
            var rr = 0; var gg = 0; var bb = 0
            for (c in strong) { rr += android.graphics.Color.red(c); gg += android.graphics.Color.green(c); bb += android.graphics.Color.blue(c) }
            android.graphics.Color.rgb(rr / strong.size, gg / strong.size, bb / strong.size)
        }
        return (paper or 0xFF000000.toInt()) to ink
    }

    private fun pdfFieldTapped(f: PdfFieldBox) {
        val v = view ?: return
        when (f.field.kind) {
            PdfField.Kind.CHECK, PdfField.Kind.RADIO -> {
                val old = v.textInside(f.rect)
                if (old != null) {
                    v.swapText(old, null)
                } else {
                    val h = f.rect.height()
                    val size = (min(h, f.rect.width()) * 0.9f).coerceIn(4f, 40f)
                    val probe = TextItem(0f, 0f, size * 2f, "✓", size, 0xFF1F2430.toInt(), FontRegistry.DEFAULT, Align.CENTER)
                    val lay = InkRenderer.layoutFor(probe)
                    val y = f.rect.centerY() - lay.height / 2f
                    v.swapText(null, TextItem(f.rect.centerX() - size, y, size * 2f, "✓", size, 0xFF1F2430.toInt(), FontRegistry.DEFAULT, Align.CENTER))
                }
            }
            PdfField.Kind.SIGNATURE -> {
                signatureTarget = RectF(f.rect)
                signatureOpen = true
            }
            else -> pdfFieldEditing = f
        }
    }

    /** Isi teks kolom formulir [f] yang sedang diisi (dialog) dengan [value]. */
    fun pdfFillField(f: PdfFieldBox, value: String) {
        pdfFieldEditing = null
        val v = view ?: return
        val old = v.textInside(f.rect)
        val text = value.trimEnd()
        if (text.isEmpty()) {
            if (old != null) v.swapText(old, null)
            return
        }
        val h = f.rect.height()
        val size = if (f.field.multiline) (h * 0.3f).coerceIn(5f, 12f) else (h * 0.6f).coerceIn(4f, 14f)
        val ink = 0xFF1F2430.toInt()
        val probe = TextItem(0f, 0f, max(8f, f.rect.width() - 4f), text, size, ink, FontRegistry.DEFAULT)
        val lay = InkRenderer.layoutFor(probe)
        val y = if (f.field.multiline) f.rect.top + 2f else f.rect.centerY() - lay.getLineBottom(0) / 2f
        val t = TextItem(f.rect.left + 2f, y, max(8f, f.rect.width() - 4f), text, size, ink, FontRegistry.DEFAULT)
        // Isi lama dari PDF-nya sendiri ditutup dulu supaya tidak bertumpuk dengan isian baru.
        if (old == null && f.field.value.isNotBlank() && f.field.kind != PdfField.Kind.CHECK) {
            v.addMarks(emptyList(), emptyList(), listOf(CoverItem(f.rect.left + 0.5f, f.rect.top + 0.5f, f.rect.width() - 1f, f.rect.height() - 1f, 0xFFFFFFFF.toInt())))
        }
        v.swapText(old, t)
    }

    /** Isi lama kolom [f]: isian yang sudah ditulis di catatan, atau isi dari PDF-nya. */
    fun pdfFieldValue(f: PdfFieldBox): String = view?.textInside(f.rect)?.text ?: f.field.value

    fun openSignature() {
        if (readMode) return
        signatureTarget = null
        signatureOpen = true
    }

    /** Sisipkan gambar tanda tangan [file] (PNG tembus pandang) ke halaman: di kolom tanda tangan, atau di tengah layar. */
    fun insertSignature(file: File) {
        signatureOpen = false
        val target = signatureTarget
        signatureTarget = null
        main.launch {
            val res = withContext(Dispatchers.IO) { runCatching { repo.placeSticker(noteId, file) }.getOrNull() }
            if (res == null) {
                notice = "Tanda tangan tidak bisa disisipkan."
                return@launch
            }
            val (name, iw, ih) = res
            val v = view ?: return@launch
            CrashLog.note("sisip tanda tangan")
            if (target != null) {
                val k = min(target.width() / iw.coerceAtLeast(1), target.height() / ih.coerceAtLeast(1))
                val w = iw * k
                val h = ih * k
                v.addItem(ImageItem(target.centerX() - w / 2f, target.centerY() - h / 2f, w, h, name), selectIt = false)
                notice = "Tanda tangan dibubuhkan di kolomnya."
            } else {
                val w = 170f
                val h = w * ih / iw.coerceAtLeast(1)
                place({ x, y -> ImageItem(x, y, w, h, name) }, w, h)
                notice = "Tanda tangan ditempel. Geser dan ubah ukurannya sesuai tempat tanda tangan."
            }
        }
    }

    /** Folder tanda tangan tersimpan (berlaku untuk semua catatan). */
    fun signatureDir(context: Context): File = File(context.filesDir, "signatures").apply { mkdirs() }

    /**
     * Putar halaman PDF ini 90° searah jarum jam. Ukuran halaman bertukar, dan isi yang sudah ditulis ikut berputar
     * supaya tetap di tempatnya di PDF.
     */
    /** Minta persetujuan memutar halaman yang sudah berisi teks ketik (teks tidak ikut berputar). */
    var rotateAsk by mutableStateOf(false)

    fun rotatePage(confirmed: Boolean = false) {
        rotateAsk = false
        if (readMode) return
        val v = view ?: return
        val meta = page
        val src = PageBackground.pdfOf(meta.background)
        if (src == null) {
            notice = "Putar halaman berlaku untuk halaman PDF."
            return
        }
        finishEditing(commit = true)
        if (!confirmed && v.contentSnapshot().texts.isNotEmpty()) {
            rotateAsk = true
            return
        }
        v.clearSelection()
        val rot = (PageBackground.pdfRotation(meta.background) + 90) % 360
        val nm = meta.copy(width = meta.height, height = meta.width, background = PageBackground.pdf(src.first, src.second, rot))
        val rotated = rotateContent(v.contentSnapshot(), meta.height)
        CrashLog.note("putar halaman ${index + 1} → $rot°")
        pages[index] = nm
        pageContent[nm.id] = rotated
        dirtyPages.add(nm.id)
        indexDirty.add(nm.id)
        repo.savePages(noteId, pages.toList())
        v.setPage(nm, rotated)
        pdfSel = null
        onPageShown()
        scheduleThumb(nm.id)
        scheduleSave()
        notice = "Halaman diputar 90°. Ketuk Putar lagi untuk terus memutarnya."
    }

    /** Isi halaman setinggi [oldH] diputar 90° searah jarum jam: titik (x, y) → (oldH − y, x). */
    private fun rotateContent(c: PageContent, oldH: Float): PageContent {
        fun turnStroke(s: Stroke): Stroke {
            val xs = FloatArray(s.size) { oldH - s.ys[it] }
            val ys = FloatArray(s.size) { s.xs[it] }
            return Stroke(s.tool, s.color, s.width, xs, ys, s.ps).also { n ->
                n.group = s.group
                n.z = s.z
                n.times = s.times
                n.masks = s.masks?.map { turnStroke(it) }
            }
        }
        val ss = c.strokes.mapTo(ArrayList()) { turnStroke(it) }
        val ts = c.texts.mapTo(ArrayList()) { t ->
            // Teks ketik tetap tegak; pusat kotaknya yang dipindah.
            val h = runCatching { InkRenderer.layoutFor(t).height.toFloat() }.getOrDefault(t.size * 1.3f)
            val cx = oldH - (t.y + h / 2f)
            val cy = t.x + t.width / 2f
            t.moved(cx - t.width / 2f, cy - h / 2f, t.width, t.size)
        }
        val its = c.items.mapTo(ArrayList()) { it ->
            val ncx = oldH - it.cy
            val ncy = it.cx
            when {
                it is CoverItem -> CoverItem(ncx - it.h / 2f, ncy - it.w / 2f, it.h, it.w, it.color).also { n -> n.id = it.id; n.group = it.group; n.z = it.z }
                it.canRotate -> it.placed(ncx - it.w / 2f, ncy - it.h / 2f, it.w, it.h, PageItem.normalizeDeg(it.rotation + 90f))
                else -> it.placed(ncx - it.w / 2f, ncy - it.h / 2f, it.w, it.h)
            }
        }
        return PageContent(ss, ts, its)
    }

    /** Nomor halaman halaman tertentu, untuk pilihan "Pilih halaman" di ekspor: "1-3, 5" → [0, 1, 2, 4]. */
    fun parsePageList(text: String): List<Int>? {
        val out = LinkedHashSet<Int>()
        for (part in text.split(',', ';', ' ').map { it.trim() }.filter { it.isNotEmpty() }) {
            val m = Regex("^(\\d+)\\s*-\\s*(\\d+)$").find(part)
            if (m != null) {
                val a = m.groupValues[1].toInt()
                val b = m.groupValues[2].toInt()
                for (k in min(a, b)..max(a, b)) if (k in 1..pages.size) out.add(k - 1)
            } else {
                val k = part.toIntOrNull() ?: return null
                if (k in 1..pages.size) out.add(k - 1)
            }
        }
        return out.toList().takeIf { it.isNotEmpty() }
    }

    // ---------- nomor halaman ----------

    var pageNumberSpec by mutableStateOf(PageNumberSpec.decode(repo.note(noteId)?.pageNumbers ?: ""))
        private set

    fun setPageNumbers(spec: PageNumberSpec?) {
        pageNumberSpec = spec
        repo.setPageNumbers(noteId, spec?.encode() ?: "")
        CrashLog.note("nomor halaman: ${spec?.encode() ?: "mati"}")
        refreshPageLabels()
        view?.invalidateCache()
        // Posisi, format, atau ukuran bisa berubah tanpa mengubah teks nomornya: semua gambar kecil dibuat ulang.
        pages.forEach { refreshThumb(it) }
        makeThumb()
        notice = if (spec == null) "Nomor halaman dimatikan." else "Nomor halaman dipasang di semua halaman (ikut ekspor dan cetak)."
    }

    /** Teks nomor tiap halaman untuk penggambar kertas. */
    private fun refreshPageLabels() {
        val spec = pageNumberSpec
        val old = InkRenderer.PageNumbers.labels
        InkRenderer.PageNumbers.spec = spec
        val now: Map<String, String> = if (spec == null) emptyMap() else {
            val m = HashMap<String, String>()
            pages.forEachIndexed { i, p -> spec.label(i, pages.size)?.let { m[p.id] = it } }
            m
        }
        InkRenderer.PageNumbers.labels = now
        // Nomor berubah (halaman ditambah, dipindah, dihapus): gambar ulang halaman dan gambar kecil yang terkena.
        if (old != now) {
            view?.invalidateCache()
            for (p in pages) if (old[p.id] != now[p.id]) refreshThumb(p)
        }
    }

    /** Gambar kecil halaman ini dibuat ulang; yang lama tetap tampil sampai yang baru siap. */
    private fun refreshThumb(p: PageMeta) {
        thumbStale.add(p.id)
        requestThumb(p)
    }

    // ---------- penanda bacaan terakhir ----------

    /** Halaman terakhir dibaca yang ditawarkan saat masuk mode baca (indeks), atau null. */
    var resumeIndex by mutableStateOf<Int?>(null)
        private set
    private var resumePageId: String? = null
    /** Halaman yang diberi pita penanda di mode baca. */
    private var ribbonPageId: String? = null

    /** Masuk mode baca: tawarkan lanjut dari halaman terakhir dibaca bila bukan halaman ini. */
    private fun offerResume() {
        val saved = prefs.getString("lastread_$noteId", null)
        val i = if (saved == null) -1 else pages.indexOfFirst { it.id == saved }
        ribbonPageId = if (i >= 0) saved else null
        if (i >= 0 && i != index) {
            resumePageId = saved
            resumeIndex = i
        } else {
            resumePageId = null
            resumeIndex = null
        }
    }

    fun resumeReading() {
        val i = resumeIndex ?: return
        resumeIndex = null
        resumePageId = null
        if (i in pages.indices && i != index) {
            view?.beginPageTurn(if (i > index) 1 else -1)
            showPage(i)
        }
    }

    fun dismissResume() {
        resumeIndex = null
        resumePageId = null
    }

    /** Tandai halaman ini sebagai bacaan terakhir (tombol pita di mode baca). */
    fun markReadHere() {
        prefs.edit().putString("lastread_$noteId", page.id).apply()
        ribbonPageId = page.id
        view?.bookmarkRibbon = readMode
        notice = "Halaman ${index + 1} ditandai sebagai bacaan terakhir."
    }

    // ---------- cari di dalam catatan ----------

    var searchOpen by mutableStateOf(false)
        private set
    var searchQuery by mutableStateOf("")
        private set
    /** Semua kata yang ketemu, urut halaman lalu dari atas ke bawah. */
    var searchHits by mutableStateOf<List<NoteHit>>(emptyList())
        private set
    var searchAt by mutableIntStateOf(-1)
        private set
    var searchBusy by mutableStateOf(false)
        private set
    private var searchJob: Job? = null
    private var searchReveal: RectF? = null

    fun openSearch() {
        searchOpen = true
        CrashLog.note("cari di catatan")
    }

    fun closeSearch() {
        searchJob?.cancel()
        searchOpen = false
        searchQuery = ""
        searchHits = emptyList()
        searchAt = -1
        searchBusy = false
        searchReveal = null
        view?.searchMarks = emptyList()
        view?.searchCurrent = null
    }

    /** Kata yang dicari berubah: cari ulang (sesudah jeda singkat) di semua halaman catatan ini. */
    fun setSearch(q: String) {
        searchQuery = q
        searchJob?.cancel()
        searchHits = emptyList()
        searchAt = -1
        val query = q.trim()
        if (query.isEmpty()) {
            searchBusy = false
            markSearchPage()
            return
        }
        searchBusy = true
        searchJob = main.launch {
            delay(250)
            val all = pages.toList()
            val cur = page.id
            val live = view?.contentSnapshot()
            val ctx = view?.context
            val found = ArrayList<NoteHit>()
            for ((i, p) in all.withIndex()) {
                val hits = ArrayList<NoteHit>()
                val content = if (p.id == cur && live != null) live else withContext(repo.writeDispatcher) {
                    runCatching { repo.loadContent(noteId, p.id) }.getOrNull()
                }
                withContext(Dispatchers.Default) {
                    content?.let { NoteSearch.inContent(it, query, i, p.id, hits) }
                    NoteSearch.inHandwriting(repo.readLines(noteId, p.id), query, i, p.id, hits)
                }
                val src = pdfSource(p)
                if (src != null && ctx != null) {
                    val text = PdfText.cachedText(src.first, src.second) ?: suspendCancellableCoroutine<id.hananote.ink.PdfPageText?> { cont ->
                        PdfText.loadText(ctx, src.first, src.second) { t -> if (cont.isActive) cont.resume(t) }
                    }
                    if (text != null && !text.isEmpty) {
                        val layer = PdfTextLayer(text, p.width, p.height, PageBackground.pdfRotation(p.background))
                        NoteSearch.inPdf(layer, query, i, p.id, hits)
                    }
                }
                if (hits.isEmpty()) continue
                hits.sortWith(compareBy({ (it.rect.top / 6f).toInt() }, { it.rect.left }))
                found.addAll(hits)
                searchHits = found.toList()
                // Hasil pertama di halaman ini atau sesudahnya langsung dituju.
                if (searchAt < 0 && i >= index) goToHit(found.indexOfFirst { it.pageIndex >= index })
                markSearchPage()
            }
            searchBusy = false
            if (searchAt < 0 && found.isNotEmpty()) goToHit(0)
            markSearchPage()
        }
    }

    fun nextHit() {
        val n = searchHits.size
        if (n == 0) return
        goToHit(if (searchAt < 0) 0 else (searchAt + 1) % n)
    }

    fun prevHit() {
        val n = searchHits.size
        if (n == 0) return
        goToHit(if (searchAt <= 0) n - 1 else searchAt - 1)
    }

    fun goToHit(k: Int) {
        val h = searchHits.getOrNull(k) ?: return
        searchAt = k
        val target = pages.indexOfFirst { it.id == h.pageId }
        if (target < 0) return
        if (target != index) {
            searchReveal = h.rect
            showPage(target)
        } else {
            markSearchPage()
            revealSearch(h.rect)
        }
    }

    private fun revealSearch(r: RectF) {
        val v = view ?: return
        val dp = v.resources.displayMetrics.density
        v.reveal(r, (if (readMode) 140f else 84f) * dp)
    }

    /** Sorotan kata yang ketemu di halaman yang sedang tampil. */
    private fun markSearchPage() {
        val v = view ?: return
        if (!searchOpen) {
            v.searchMarks = emptyList()
            v.searchCurrent = null
            return
        }
        val pid = page.id
        v.searchMarks = searchHits.filter { it.pageId == pid }.map { it.rect }
        v.searchCurrent = searchHits.getOrNull(searchAt)?.takeIf { it.pageId == pid }?.rect
        searchReveal?.let { r ->
            if (v.searchCurrent === r) {
                searchReveal = null
                revealSearch(r)
            }
        }
    }

    // ---------- daftar isi ----------

    /** Daftar isi buatan sendiri, urut halaman. */
    val outline = mutableStateListOf<OutlineEntry>()
    /** Daftar isi bawaan PDF di catatan ini, sudah menunjuk ke halaman catatan. */
    var pdfOutline by mutableStateOf<List<OutlineEntry>>(emptyList())
        private set
    private var outlineLoaded = false

    /** Muat daftar isi (dan daftar isi bawaan PDF) saat panelnya dibuka. */
    fun refreshOutline() {
        val ids = pages.map { it.id }.toSet()
        outline.clear()
        outline.addAll(repo.loadOutline(noteId).filter { it.pageId in ids })
        outlineLoaded = true
        val ctx = view?.context ?: return
        val files = pages.mapNotNull { PageBackground.pdfOf(it.background)?.first }.distinct()
        if (files.isEmpty()) { pdfOutline = emptyList(); return }
        val acc = ArrayList<Pair<Int, OutlineEntry>>()
        var left = files.size
        for (name in files) {
            PdfText.loadOutline(ctx, File(repo.noteDir(noteId), name)) { items ->
                for (it in items) {
                    val at = pages.indexOfFirst { p -> PageBackground.pdfOf(p.background)?.let { (n, i) -> n == name && i == it.page } == true }
                    if (at >= 0) acc.add(at to OutlineEntry(it.title, pages[at].id, it.level.coerceAtMost(2)))
                }
                left--
                if (left == 0) pdfOutline = acc.sortedBy { it.first }.map { it.second }
            }
        }
    }

    fun outlinePage(e: OutlineEntry): Int = pages.indexOfFirst { it.id == e.pageId }

    /**
     * Usulan judul bab untuk halaman ke-[i]: judul di daftar isi bawaan PDF, baris pertama teks PDF-nya, baris
     * pertama tulisan/ketikan di halaman itu, atau "Halaman N".
     */
    suspend fun outlineSuggestion(i: Int): String {
        val p = pages.getOrNull(i) ?: return ""
        pdfOutline.firstOrNull { it.pageId == p.id }?.let { return it.title }
        val src = pdfSource(p)
        val ctx = view?.context
        if (src != null && ctx != null) {
            val text = PdfText.cachedText(src.first, src.second) ?: suspendCancellableCoroutine<id.hananote.ink.PdfPageText?> { cont ->
                PdfText.loadText(ctx, src.first, src.second) { t -> if (cont.isActive) cont.resume(t) }
            }
            if (text != null && !text.isEmpty) {
                // Baris pertama yang cukup panjang (huruf satu baris PDF punya nomor baris yang sama).
                val first = StringBuilder()
                var line = -1
                for (k in text.chars.indices) {
                    val ln = text.line.getOrElse(k) { 0 }
                    if (ln != line) {
                        if (first.trim().count { it.isLetter() } >= 3) break
                        first.clear()
                        line = ln
                    }
                    first.append(text.chars[k])
                }
                val t = first.trim().toString()
                if (t.count { it.isLetter() } >= 3) return t.take(60)
            }
        }
        val own = withContext(repo.writeDispatcher) { repo.firstLine(noteId, p.id) }
        return own.ifEmpty { "Halaman ${i + 1}" }
    }

    private fun saveOutlineNow() {
        repo.saveOutline(noteId, outline.toList())
    }

    /** Tambahkan halaman ke-[i] ke daftar isi, di urutan halamannya. */
    fun addOutline(title: String, i: Int, level: Int = 0) {
        val p = pages.getOrNull(i) ?: return
        if (!outlineLoaded) refreshOutline()
        val entry = OutlineEntry(title.trim().ifEmpty { "Halaman ${i + 1}" }, p.id, level.coerceIn(0, 2))
        val at = outline.indexOfFirst { outlinePage(it) > i }.let { if (it < 0) outline.size else it }
        outline.add(at, entry)
        saveOutlineNow()
        CrashLog.note("daftar isi: tambah")
        notice = "Halaman ${i + 1} masuk daftar isi: ${entry.title}"
    }

    fun renameOutline(k: Int, title: String) {
        val e = outline.getOrNull(k) ?: return
        outline[k] = e.copy(title = title.trim().ifEmpty { e.title })
        saveOutlineNow()
    }

    fun setOutlineLevel(k: Int, level: Int) {
        val e = outline.getOrNull(k) ?: return
        outline[k] = e.copy(level = level.coerceIn(0, 2))
        saveOutlineNow()
    }

    fun removeOutline(k: Int) {
        if (k !in outline.indices) return
        outline.removeAt(k)
        saveOutlineNow()
    }

    /** Buka halaman yang ditunjuk entri daftar isi. */
    fun goOutline(e: OutlineEntry) {
        val i = outlinePage(e)
        if (i >= 0) showPage(i)
    }

    // ---------- presentasi dan laser ----------

    private val laserTrail = id.hananote.ink.LaserTrail()

    /** Laser aktif: pena atau satu jari menunjuk dengan jejak merah yang hilang sendiri (tidak disimpan). */
    var laserOn by mutableStateOf(false)
        private set

    /** Sedang presentasi: ke layar lain (proyektor/monitor) bila ada, atau layar penuh di tablet ini. */
    var presenting by mutableStateOf(false)
        private set

    /** Nama layar presentasi yang sedang dipakai; kosong bila hanya tampil di tablet. */
    var presentScreen by mutableStateOf("")
        private set

    private var presentation: PagePresentation? = null
    private var presentJob: Job? = null
    private var displayWatch: android.hardware.display.DisplayManager.DisplayListener? = null
    private var focusBeforePresent = false

    fun toggleLaser() = setLaser(!laserOn)

    fun setLaser(on: Boolean) {
        laserOn = on
        laserTrail.clear()
        view?.laser = if (on) laserTrail else null
        CrashLog.note("laser: $on")
        if (on && !presenting && !prefs.getBoolean("hint_laser", false)) {
            prefs.edit().putBoolean("hint_laser", true).apply()
            notice = "Laser aktif: tunjuk dengan pena atau satu jari, jejaknya hilang sendiri dan tidak disimpan. " +
                "Dua jari tetap menggeser halaman."
        }
    }

    /** Mulai presentasi. Halaman tampil di layar lain yang tersambung; laser langsung aktif. */
    fun startPresentation(context: Context) {
        if (presenting) return
        CrashLog.note("presentasi mulai")
        finishEditing(commit = true)
        presenting = true
        focusBeforePresent = focusMode
        setLaser(true)
        val dm = context.getSystemService(android.hardware.display.DisplayManager::class.java)
        if (dm != null) {
            val watch = object : android.hardware.display.DisplayManager.DisplayListener {
                override fun onDisplayAdded(displayId: Int) {
                    if (presenting && presentation == null) showOnScreen(context)
                }
                override fun onDisplayRemoved(displayId: Int) {
                    if (presentation?.display?.displayId == displayId) {
                        presentation = null
                        screenGone()
                    }
                }
                override fun onDisplayChanged(displayId: Int) {
                    if (presentation?.display?.displayId == displayId) refreshPresentation()
                }
            }
            dm.registerDisplayListener(watch, android.os.Handler(android.os.Looper.getMainLooper()))
            displayWatch = watch
        }
        if (!showOnScreen(context)) {
            if (!focusMode) toggleFocus()
            notice = "Belum ada layar lain yang tersambung, jadi halaman tampil layar penuh di tablet dengan laser. " +
                "Sambungkan proyektor atau monitor (kabel USB-C ke HDMI, atau layar nirkabel) dan halaman langsung tampil di sana."
        }
    }

    /** Tampilkan di layar presentasi pertama yang ada. Hasil false bila tidak ada layar lain. */
    private fun showOnScreen(context: Context): Boolean {
        val dm = context.getSystemService(android.hardware.display.DisplayManager::class.java) ?: return false
        val d = dm.getDisplays(android.hardware.display.DisplayManager.DISPLAY_CATEGORY_PRESENTATION).firstOrNull() ?: return false
        val p = runCatching { PagePresentation(context, d, laserTrail).also { it.show() } }.getOrElse {
            CrashLog.note("presentasi gagal: ${it.message}")
            return false
        }
        p.setOnDismissListener {
            if (presentation === p) {
                presentation = null
                screenGone()
            }
        }
        presentation = p
        presentScreen = d.name?.takeIf { it.isNotBlank() } ?: "layar lain"
        // Ada layar lain: tablet kembali ke tampilan biasa supaya tombol dan halaman lain tetap terjangkau.
        if (focusMode && !focusBeforePresent) toggleFocus()
        notice = "Presentasi tampil di $presentScreen. Yang tampil di sana hanya halaman dan laser; tombol tetap di tablet."
        refreshPresentation()
        return true
    }

    private fun screenGone() {
        presentScreen = ""
        presentJob?.cancel()
        if (presenting) {
            notice = "Layar presentasi terputus. Halaman tampil layar penuh di tablet."
            if (!focusMode) toggleFocus()
        }
    }

    fun stopPresentation() {
        if (!presenting) return
        CrashLog.note("presentasi selesai")
        presenting = false
        presentJob?.cancel()
        val ctx = view?.context
        displayWatch?.let { w -> ctx?.getSystemService(android.hardware.display.DisplayManager::class.java)?.unregisterDisplayListener(w) }
        displayWatch = null
        val p = presentation
        presentation = null
        presentScreen = ""
        runCatching { p?.dismiss() }
        setLaser(false)
        if (focusMode != focusBeforePresent) toggleFocus()
    }

    /** Gambar ulang halaman di layar presentasi (setelah pindah halaman atau isinya berubah). */
    private fun refreshPresentation(wait: Long = 0L) {
        val pr = presentation ?: return
        val p = pages.getOrNull(index) ?: return
        val v = view
        val live = if (v?.page?.id == p.id) v.contentSnapshot() else pageContent[p.id]?.copy()
        presentJob?.cancel()
        presentJob = main.launch {
            if (wait > 0) delay(wait)
            val w = pr.targetWidth(p.width, p.height)
            val bmp = runCatching {
                val content = live ?: withContext(repo.writeDispatcher) { repo.loadContent(noteId, p.id) }
                withContext(Dispatchers.Default) { InkRenderer.renderThumbnail(p, content, w, repo.noteDir(noteId)) }
            }.getOrNull() ?: return@launch
            if (presentation === pr) pr.showPage(bmp, p.width, p.height) else bmp.recycle()
        }
    }

    /** Dipanggil tiap pindah halaman atau isi berubah. */
    private fun presentationFollow(changed: Boolean) {
        if (!changed) laserTrail.clear()
        if (presentation != null) refreshPresentation(if (changed) 160L else 0L)
    }

    // ---------- kartu hafalan ----------

    /** Semua kartu hafalan catatan ini: kartu tanya-jawab ditambah satu kartu untuk tiap selotip. */
    val cards = mutableStateListOf<StudyCard>()
    var cardsLoading by mutableStateOf(false)
        private set

    /** Muat kartu. Kartu selotip diambil dari semua halaman; tingkat hafalnya disimpan di cards.json. */
    fun loadCards() {
        flush()
        cardsLoading = true
        val metas = pages.toList()
        val v = view
        val live = HashMap<String, PageContent>()
        v?.page?.let { live[it.id] = v.contentSnapshot() }
        for (p in metas) if (p.id !in live) pageContent[p.id]?.let { live[p.id] = it.copy() }
        main.launch {
            val merged = withContext(repo.writeDispatcher) {
                val saved = repo.loadCards(noteId)
                val tapes = LinkedHashMap<String, String>()
                for (p in metas) {
                    val content = live[p.id] ?: runCatching { repo.loadContent(noteId, p.id) }.getOrNull() ?: continue
                    content.items.filterIsInstance<TapeItem>()
                        .sortedWith(compareBy({ it.y }, { it.x }))
                        .forEach { tapes["${p.id}:${it.id}"] = p.id }
                }
                val known = saved.filter { it.isTape }.associateBy { it.tapeKey }
                val out = ArrayList<StudyCard>()
                for ((key, pid) in tapes) out.add(known[key] ?: StudyCard(UUID.randomUUID().toString(), "", "", pid, key))
                val ids = metas.map { it.id }.toSet()
                for (c in saved) if (!c.isTape) out.add(if (c.pageId.isEmpty() || c.pageId in ids) c else c.copy(pageId = ""))
                repo.saveCards(noteId, out)
                out
            }
            cards.clear()
            cards.addAll(merged)
            cardsLoading = false
        }
    }

    private fun saveCardsNow() {
        val list = cards.toList()
        repo.writeScope.launch { repo.saveCards(noteId, list) }
    }

    /** Kartu tanya-jawab baru; bila [here] true, kartu ditautkan ke halaman yang sedang dibuka. */
    fun addCard(front: String, back: String, here: Boolean) {
        val f = front.trim()
        if (f.isEmpty()) return
        cards.add(StudyCard(UUID.randomUUID().toString(), f, back.trim(), if (here) page.id else ""))
        saveCardsNow()
    }

    fun editCard(id: String, front: String, back: String) {
        val i = cards.indexOfFirst { it.id == id }
        if (i < 0 || front.isBlank()) return
        cards[i] = cards[i].copy(front = front.trim(), back = back.trim())
        saveCardsNow()
    }

    fun deleteCard(id: String) {
        val i = cards.indexOfFirst { it.id == id }
        if (i < 0 || cards[i].isTape) return
        cards.removeAt(i)
        saveCardsNow()
    }

    /** Hafalan kartu diulang dari awal (jadi kartu baru lagi). */
    fun resetCard(id: String) {
        val i = cards.indexOfFirst { it.id == id }
        if (i < 0) return
        cards[i] = cards[i].copy(box = 0, due = 0L)
        saveCardsNow()
    }

    /** Nilai kartu setelah jawabannya dilihat: 0 lupa, 1 ragu, 2 ingat. */
    fun rateCard(id: String, rating: Int): StudyCard? {
        val i = cards.indexOfFirst { it.id == id }
        if (i < 0) return null
        val r = cards[i].rated(rating)
        cards[i] = r
        saveCardsNow()
        return r
    }

    fun cardPage(card: StudyCard): Int = if (card.pageId.isEmpty()) -1 else pages.indexOfFirst { it.id == card.pageId }

    /**
     * Gambar kartu selotip: potongan halaman di sekitar selotipnya. [open] false: selotipnya tertutup (pertanyaan);
     * true: terbuka (jawaban). Selotip lain di potongan itu selalu tertutup.
     */
    suspend fun tapeCardImage(card: StudyCard, open: Boolean, widthPx: Int): Bitmap? {
        val parts = card.tapeKey.split(':', limit = 2)
        val pid = parts[0]
        val tid = parts.getOrNull(1)?.toLongOrNull() ?: return null
        val p = pages.firstOrNull { it.id == pid } ?: return null
        val v = view
        val live = if (v?.page?.id == pid) v.contentSnapshot() else pageContent[pid]?.copy()
        val content = live ?: withContext(repo.writeDispatcher) { runCatching { repo.loadContent(noteId, pid) }.getOrNull() } ?: return null
        val dir = repo.noteDir(noteId)
        return withContext(Dispatchers.Default) {
            val target = content.items.firstOrNull { it is TapeItem && it.id == tid } ?: return@withContext null
            val items = content.items.mapTo(ArrayList<PageItem>()) { item ->
                if (item is TapeItem) (item.placed(item.x, item.y, item.w, item.h) as TapeItem).also { it.covered = !(open && item.id == tid) }
                else item
            }
            val shown = PageContent(ArrayList(content.strokes), ArrayList(content.texts), items)
            val box = RectF()
            InkRenderer.itemBounds(target, box)
            val padY = maxOf(box.height() * 1.5f, p.width * 0.1f)
            val crop = RectF(0f, box.top - padY, p.width, box.bottom + padY)
            if (box.width() > p.width) { crop.left = box.left - 20f; crop.right = box.right + 20f }
            if (!crop.intersect(0f, 0f, p.width, p.height)) return@withContext null
            val s = widthPx / crop.width()
            val h = (crop.height() * s).toInt().coerceIn(1, 4096)
            val bmp = Bitmap.createBitmap(widthPx, h, Bitmap.Config.ARGB_8888)
            val cv = android.graphics.Canvas(bmp)
            cv.scale(s, s)
            cv.translate(-crop.left, -crop.top)
            InkRenderer.drawPage(cv, p, shown, dir, s)
            bmp
        }
    }

    // ---------- halaman ----------

    fun addPage(after: Int = index) {
        if (readMode) return
        // Halaman baru setelah halaman PDF adalah kertas kosong, bukan salinan halaman PDF itu.
        val bg = if (page.background.startsWith("pdf:")) "" else page.background
        val p = page.copy(id = UUID.randomUUID().toString(), background = bg)
        val at = (after + 1).coerceIn(0, pages.size)
        CrashLog.note("tambah halaman di ${at + 1}")
        pages.add(at, p)
        pageContent[p.id] = PageContent()
        repo.savePages(noteId, pages.toList())
        if (at == index + 1) view?.beginPageTurn(1)
        showPage(at)
    }

    /** Tambahkan halaman-halaman PDF setelah halaman ini, siap ditulisi. */
    fun importPdfPages(context: Context, uri: Uri, scanned: Boolean = false) {
        flush()
        notice = if (scanned) "Menambahkan hasil pindaian…" else "Membuka PDF…"
        main.launch {
            val res = withContext(Dispatchers.IO) { runCatching { repo.importPdf(context.applicationContext, noteId, uri) }.getOrNull() }
            if (res == null) {
                notice = "PDF tidak bisa dibuka. Mungkin rusak atau dikunci kata sandi."
                return@launch
            }
            val (file, sizes) = res
            CrashLog.note("impor PDF: ${sizes.size} halaman")
            val at = (index + 1).coerceIn(0, pages.size)
            sizes.forEachIndexed { i, (w, h) ->
                val p = PageMeta(UUID.randomUUID().toString(), w, h, PaperTemplate.BLANK, 0xFFFFFFFF.toInt(), PageBackground.pdf(file, i))
                pages.add(at + i, p)
                pageContent[p.id] = PageContent()
            }
            repo.savePages(noteId, pages.toList())
            showPage(at)
            notice = if (scanned) "${sizes.size} halaman pindaian ditambahkan. Tulisi langsung di atasnya."
            else "${sizes.size} halaman PDF ditambahkan. Tulisi langsung di atasnya."
        }
    }

    fun duplicatePage(i: Int = index) {
        if (readMode) return
        val src = pages.getOrNull(i) ?: return
        CrashLog.note("duplikat halaman ${i + 1}")
        flush()
        val p = src.copy(id = UUID.randomUUID().toString())
        pages.add(i + 1, p)
        val cached = pageContent[src.id]
        if (cached != null) {
            pageContent[p.id] = cached.deepCopy()
            dirtyPages.add(p.id)
            indexDirty.add(p.id)
        } else {
            repo.writeScope.launch { repo.copyPageStrokes(noteId, src.id, p.id) }
        }
        repo.savePages(noteId, pages.toList())
        showPage(i + 1)
    }

    /** Pindahkan halaman [from] ke urutan [to] (keduanya mulai 0). Halaman yang sedang dibuka tetap terbuka. */
    fun movePage(from: Int, to: Int) {
        if (readMode) return
        if (from !in pages.indices) return
        val target = to.coerceIn(0, pages.lastIndex)
        if (target == from) return
        CrashLog.note("pindah halaman ${from + 1} → ${target + 1}")
        finishEditing(commit = true)
        flush()
        val currentId = page.id
        val moved = pages.removeAt(from)
        pages.add(target, moved)
        index = pages.indexOfFirst { it.id == currentId }.coerceAtLeast(0)
        prefs.edit().putInt("page_$noteId", index).apply()
        repo.savePages(noteId, pages.toList())
        refreshPageLabels()
        // Sampul catatan memakai halaman pertama.
        if (from == 0 || target == 0) makeThumb()
        notice = "Halaman ${from + 1} dipindah ke urutan ${target + 1}."
    }

    fun deletePage(i: Int) {
        if (readMode) return
        if (i !in pages.indices) return
        if (pages.size <= 1) {
            notice = "Catatan harus punya minimal satu halaman. Pakai “Bersihkan halaman” untuk mengosongkannya."
            return
        }
        CrashLog.note("hapus halaman ${i + 1}/${pages.size}")
        val removed = pages.removeAt(i)
        pageContent.remove(removed.id)
        dirtyPages.remove(removed.id)
        pageThumbs.remove(removed.id)
        repo.savePages(noteId, pages.toList())
        repo.writeScope.launch { repo.deletePageFile(noteId, removed.id) }
        refreshPageLabels()
        when {
            i == index -> showPage(i.coerceAtMost(pages.lastIndex))
            i < index -> index -= 1
            else -> Unit
        }
    }

    /** Halaman [i] mendatar (lebar lebih dari tinggi). */
    fun isLandscape(i: Int = index): Boolean = pages.getOrNull(i)?.let { it.width > it.height } ?: false

    /**
     * Arah kertas: tegak atau mendatar, untuk halaman [at] saja atau semua halaman. Ukuran kertasnya tetap,
     * hanya lebar dan tingginya bertukar, dan tersimpan bersama catatan (tidak kembali saat ditutup).
     * Isi halaman tidak ikut berputar. Halaman PDF dilewati: arahnya mengikuti PDF, ubah dengan Putar.
     */
    fun setOrientation(landscape: Boolean, toAll: Boolean, at: Int = index) {
        if (readMode) return
        finishEditing(commit = true)
        var changed = 0
        var pdf = 0
        for (k in pages.indices) {
            if (!toAll && k != at) continue
            val p = pages[k]
            if (p.width == p.height || (p.width > p.height) == landscape) continue
            if (PageBackground.pdfOf(p.background) != null) { pdf++; continue }
            pages[k] = p.copy(width = p.height, height = p.width)
            refreshThumb(pages[k])
            changed++
        }
        val arah = if (landscape) "mendatar" else "tegak"
        CrashLog.note("arah kertas $arah: $changed halaman" + if (toAll) " (semua)" else "")
        if (changed > 0) {
            repo.savePages(noteId, pages.toList())
            view?.updatePageMeta(page)
            makeThumb()
        }
        val skip = if (pdf > 0) " Halaman PDF ($pdf) dilewati, putar lewat PDF → Putar." else ""
        notice = when {
            changed == 0 && pdf > 0 -> "Halaman PDF mengikuti arah PDF-nya; putar lewat PDF → Putar."
            changed == 0 -> if (toAll) "Semua halaman sudah $arah." else "Halaman ini sudah $arah."
            toAll -> "$changed halaman jadi $arah.$skip"
            else -> "Halaman ${at + 1} jadi $arah.$skip"
        }
    }

    fun applyPaper(w: Float, h: Float, template: PaperTemplate, color: Int, background: String, toAll: Boolean) {
        CrashLog.note("kertas: ${template.name}, latar ${background.substringBefore(':').ifEmpty { "polos" }}" + if (toAll) " (semua)" else "")
        for (k in pages.indices) {
            if (toAll || k == index) {
                pages[k] = pages[k].copy(width = w, height = h, template = template, paperColor = color, background = background)
                thumbStale.add(pages[k].id)
            }
        }
        repo.savePages(noteId, pages.toList())
        view?.updatePageMeta(page)
    }

    /** Salin gambar dari perangkat untuk dijadikan latar halaman. */
    fun importBackground(context: Context, uri: Uri, done: (String) -> Unit) {
        main.launch {
            val res = withContext(Dispatchers.IO) {
                runCatching { repo.importImage(context.applicationContext, noteId, uri) }.getOrNull()
            }
            if (res == null) {
                notice = "Gambar tidak bisa dibuka. Coba pilih gambar lain."
                return@launch
            }
            done(res.first)
        }
    }

    fun noteImageDir(): File = repo.noteDir(noteId)

    companion object {
        /** Jeda setelah berhenti menulis sebelum tulisan diubah jadi teks. */
        const val AUTO_TEXT_PAUSE_MS = 1400L
        /** Jeda bila pena sudah diangkat menjauh dari layar. */
        const val AUTO_TEXT_LIFT_MS = 900L
        /** Tambahan tunggu paling lama bila pena masih melayang di atas layar. */
        const val AUTO_TEXT_HOVER_EXTRA_MS = 2000L

        fun defaultColor(t: Tool): Int = when (t) {
            Tool.PEN, Tool.BRUSH, Tool.LINE, Tool.QALAM -> 0xFF1F2430.toInt()
            Tool.PENCIL -> 0xFF4A4D55.toInt()
            Tool.MARKER -> 0xFF2F43B5.toInt()
            Tool.HIGHLIGHTER -> 0xFFFFE14D.toInt()
            Tool.WATERCOLOR -> WatercolorColors[0]
            Tool.ERASER -> 0xFF000000.toInt()
            Tool.TAPE -> TapeColors[0]
        }
    }
}

enum class ExportAction { SHARE, SAVE, KEEP }

/**
 * Berkas hasil ekspor beserta yang akan dilakukan: dibagikan, disimpan, atau dikirim ke Google Keep (dengan judul
 * dan teks catatannya).
 */
class ExportJob(
    val files: List<File>,
    val format: ExportFormat,
    val action: ExportAction,
    val title: String? = null,
    val text: String? = null,
)

enum class VoiceMode { DICTATE, RECORD, TRANSCRIBE }

/** Keadaan jendela suara: teks yang sudah dikenali, teks sementara, kekerasan suara, dan lamanya. */
class VoiceSession(val mode: VoiceMode, lang: String, val anchor: AudioItem? = null) {
    var lang by mutableStateOf(lang)
    var text by mutableStateOf("")
    var partial by mutableStateOf("")
    var level by mutableFloatStateOf(0f)
    var elapsedMs by mutableLongStateOf(0L)
    var status by mutableStateOf<String?>(null)
    var listening by mutableStateOf(false)
    var paused by mutableStateOf(false)
    /** Sudah mulai merekam atau mendengarkan (sebelum itu jendela menunggu tombol Mulai). */
    var started by mutableStateOf(mode == VoiceMode.TRANSCRIBE)
}

/** Mind map yang sedang disunting: isinya dan benda aslinya di halaman (null bila baru). */

/** Keadaan jendela hapus latar: gambar asli, hasil cara otomatis, dan hasil cara warna. */
class BgRemoval(val image: ImageItem) {
    var source by mutableStateOf<Bitmap?>(null)
    var ai by mutableStateOf<Bitmap?>(null)
    var aiProblem by mutableStateOf<String?>(null)
    var status by mutableStateOf<String?>("Membuka gambar…")
    var useAi by mutableStateOf(true)
    var tolerance by mutableFloatStateOf(0.18f)
    var colorResult by mutableStateOf<Bitmap?>(null)
    var colorBusy by mutableStateOf(false)
}

sealed interface ConvertState {
    data class Working(val message: String) : ConvertState
    data class Failed(val message: String, val canRetry: Boolean = true) : ConvertState
}

/** Menu tahan tekan di kanvas: titik di layar (koordinat kanvas) dan di halaman. */
class CanvasMenu(val x: Float, val y: Float, val pageX: Float, val pageY: Float, val onObject: Boolean)

/** Letakkan menu di atas titik yang ditahan (atau di bawahnya bila tidak cukup ruang). */
private class AtPoint(val x: Int, val y: Int, val gap: Int) : PopupPositionProvider {
    override fun calculatePosition(anchorBounds: IntRect, windowSize: IntSize, layoutDirection: LayoutDirection, popupContentSize: IntSize): IntOffset {
        val ax = anchorBounds.left + x
        val ay = anchorBounds.top + y
        var px = ax - popupContentSize.width / 2
        var py = ay - popupContentSize.height - gap
        if (py < 0) py = ay + gap
        px = px.coerceIn(0, maxOf(0, windowSize.width - popupContentSize.width))
        py = py.coerceIn(0, maxOf(0, windowSize.height - popupContentSize.height))
        return IntOffset(px, py)
    }
}

/** Pilihan salin, potong, tempel, duplikat, dan hapus yang muncul saat kanvas ditahan. */
@Composable
private fun CanvasMenuPopup(c: EditorController, menu: CanvasMenu) {
    val clip by InkClipboard.clip.collectAsState()
    val gap = with(LocalDensity.current) { 28.dp.roundToPx() }
    Popup(
        popupPositionProvider = remember(menu) { AtPoint(menu.x.toInt(), menu.y.toInt(), gap) },
        onDismissRequest = { c.dismissCanvasMenu() },
        properties = PopupProperties(focusable = true),
    ) {
        Surface(shape = RoundedCornerShape(50), tonalElevation = 6.dp, shadowElevation = 10.dp) {
            Row(Modifier.padding(horizontal = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                if (menu.onObject) {
                    TextButton(onClick = { c.dismissCanvasMenu(); c.copySelection() }) { Text("Salin") }
                    TextButton(onClick = { c.dismissCanvasMenu(); c.copySelection(cut = true) }) { Text("Potong") }
                }
                if (clip != null) {
                    TextButton(onClick = { c.dismissCanvasMenu(); c.paste(menu.pageX, menu.pageY) }) { Text("Tempel") }
                }
                if (menu.onObject) {
                    TextButton(onClick = { c.dismissCanvasMenu(); c.duplicateSelection() }) { Text("Duplikat") }
                    TextButton(onClick = { c.dismissCanvasMenu(); c.deleteSelection() }) { Text("Hapus", color = MaterialTheme.colorScheme.error) }
                }
            }
        }
    }
}

private sealed interface EditorDialog {
    data object Paper : EditorDialog
    data object Rename : EditorDialog
    data object CustomColor : EditorDialog
    data object ClearPage : EditorDialog
    data class DeletePage(val index: Int) : EditorDialog
    data object InsertLink : EditorDialog
    data object InsertTable : EditorDialog
    data object InsertSticker : EditorDialog
    data object EditLink : EditorDialog
    data object CropImage : EditorDialog
    data object ImageShadow : EditorDialog
    data object Export : EditorDialog
    data object InsertDate : EditorDialog
    data object InkLang : EditorDialog
    data object TableLayout : EditorDialog
    data object Emoji : EditorDialog
    data object PageNumbers : EditorDialog
    data class Orientation(val index: Int) : EditorDialog
    /** Daftar isi; [addFor]: langsung tambahkan halaman ke-[addFor] sebagai bab. */
    data class Outline(val addFor: Int? = null) : EditorDialog
    data object Flashcards : EditorDialog
}

private enum class EditorSheet { ToolOptions, Pages }

// =====================================================================
// Layar editor
// =====================================================================

@Composable
fun EditorScreen(
    repo: Repository,
    noteId: String,
    isTablet: Boolean,
    onBack: () -> Unit,
    onOpenNote: (String, Int) -> Unit = { _, _ -> },
) {
    val c = remember(noteId) { EditorController(repo, noteId) }
    // Link ke catatan lain: buka catatan itu (di halaman tujuannya).
    val jump = c.noteToOpen
    LaunchedEffect(jump) {
        if (jump != null) {
            c.noteToOpen = null
            onOpenNote(jump.first, jump.second)
        }
    }
    val owner = LocalLifecycleOwner.current
    DisposableEffect(owner, c) {
        val obs = LifecycleEventObserver { _, e ->
            if (e == Lifecycle.Event.ON_STOP) { c.flush(); c.makeThumb(); c.reindexChanged() }
        }
        owner.lifecycle.addObserver(obs)
        onDispose {
            owner.lifecycle.removeObserver(obs)
            c.close()
        }
    }
    BackHandler(onBack = onBack)
    // Di layar penuh, tombol kembali keluar dari layar penuh dulu.
    BackHandler(enabled = c.focusMode) { c.toggleFocus() }
    // Layar penuh juga menyembunyikan bilah status dan navigasi Android (geser dari tepi untuk memunculkannya).
    val hostView = LocalView.current
    DisposableEffect(c.focusMode) {
        var ctx = hostView.context
        while (ctx is android.content.ContextWrapper && ctx !is android.app.Activity) ctx = ctx.baseContext
        val window = (ctx as? android.app.Activity)?.window
        val bars = window?.let { WindowCompat.getInsetsController(it, hostView) }
        if (bars != null) {
            if (c.focusMode) {
                bars.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                bars.hide(WindowInsetsCompat.Type.systemBars())
            } else {
                bars.show(WindowInsetsCompat.Type.systemBars())
            }
        }
        onDispose { bars?.show(WindowInsetsCompat.Type.systemBars()) }
    }
    // Saat menyunting teks, tombol kembali menyelesaikan suntingan dulu.
    BackHandler(enabled = c.editingText != null || c.mapState != null) {
        if (c.editingText != null) c.finishEditing(commit = true) else c.endMindMap()
    }

    val context = LocalContext.current
    val uriHandler = LocalUriHandler.current
    val dark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    val desk = if (dark) 0xFF0E0F12.toInt() else 0xFFE4E5EA.toInt()
    val accent = MaterialTheme.colorScheme.primary.toArgb()
    var dialog by remember { mutableStateOf<EditorDialog?>(null) }
    var sheet by remember { mutableStateOf<EditorSheet?>(null) }
    val snackbar = remember { SnackbarHostState() }
    val notice = c.notice
    LaunchedEffect(notice) {
        if (notice != null) {
            snackbar.showSnackbar(notice)
            c.notice = null
        }
    }
    val link = c.linkToOpen
    LaunchedEffect(link) {
        if (link != null) {
            runCatching { uriHandler.openUri(link) }.onFailure { c.notice = "Link tidak bisa dibuka." }
            c.linkToOpen = null
        }
    }
    val pickImage = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) c.insertImage(context, uri)
    }
    val pickPdf = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) c.importPdfPages(context, uri)
    }
    val pickStickers = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(20)) { uris ->
        c.addStickers(context, uris)
    }
    // Simpan hasil ekspor: satu berkas lewat "Simpan sebagai", beberapa berkas ke folder pilihan.
    val savePng = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument(ExportFormat.PNG.mime)) { c.saveExportTo(context, it) }
    val saveJpg = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument(ExportFormat.JPG.mime)) { c.saveExportTo(context, it) }
    val saveDocx = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument(ExportFormat.DOCX.mime)) { c.saveExportTo(context, it) }
    val savePdf = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument(ExportFormat.PDF.mime)) { c.saveExportTo(context, it) }
    val saveFolder = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { c.saveExportToFolder(context, it) }
    val exportJob = c.exportReady
    LaunchedEffect(exportJob) {
        val job = exportJob ?: return@LaunchedEffect
        if (dialog == EditorDialog.Export) dialog = null
        when {
            job.action == ExportAction.SHARE -> {
                shareFiles(context, job)
                c.exportReady = null
            }
            job.action == ExportAction.KEEP -> {
                if (!sendToKeep(context, job)) {
                    c.notice = "Google Keep belum terpasang. Pilih aplikasi lain untuk menyimpan catatannya."
                    shareFiles(context, job)
                }
                c.exportReady = null
            }
            job.files.size == 1 -> {
                val launcher = when (job.format) {
                    ExportFormat.PNG -> savePng
                    ExportFormat.JPG -> saveJpg
                    ExportFormat.DOCX -> saveDocx
                    ExportFormat.PDF -> savePdf
                }
                runCatching { launcher.launch(job.files[0].name) }.onFailure {
                    c.exportReady = null
                    c.notice = "Tidak ada aplikasi untuk menyimpan berkas."
                }
            }
            else -> runCatching { saveFolder.launch(null) }.onFailure {
                c.exportReady = null
                c.notice = "Tidak ada aplikasi untuk memilih folder."
            }
        }
    }
    val fonts by c.fonts.collectAsState()
    var addedFont by remember { mutableStateOf<String?>(null) }
    val pickFont = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) c.importFont(context, uri) { entry -> if (entry != null) addedFont = entry.id }
    }
    // Semua jenis berkas: di beberapa perangkat .otf dilaporkan sebagai dokumen sehingga tidak bisa dipilih bila
    // disaring menurut jenis. Isinya diperiksa saat ditambahkan.
    val addFont = { pickFont.launch(arrayOf("*/*")) }

    val canvas: @Composable (Modifier) -> Unit = { m ->
        Box(m) {
        AndroidView(
            factory = { ctx -> InkView(ctx).also { c.attach(it) } },
            modifier = Modifier.fillMaxSize().clipToBounds(),
            update = { v ->
                v.tool = c.tool
                v.inputMode = c.mode
                v.shapeKind = c.shapeKind
                v.tapeShape = c.tapeShape
                v.rulerVisible = c.ruler && !c.readMode
                v.readOnly = c.readMode
                v.color = c.colorOf(c.styleTool)
                v.baseWidth = c.widthOf(c.styleTool)
                v.eraserWidth = c.widthOf(Tool.ERASER)
                v.eraserMode = c.eraserMode
                v.eraserStrength = c.eraserStrength
                v.eraseImages = c.eraseImages
                v.fingerDraws = c.fingerDraws
                v.deskColor = desk
                v.accentColor = accent
                v.selectionBarTop = if (isTablet && c.focusMode) 80f else 12f
                v.autoShape = c.autoShape
            },
        )
        c.canvasMenu?.let { CanvasMenuPopup(c, it) }
        if (c.viewTilted) StraightenChip(c, Modifier.align(Alignment.TopStart).padding(12.dp))
        if (c.readMode) ReadModeChip(c, Modifier.align(Alignment.TopCenter).padding(top = if (c.focusMode) 14.dp else 10.dp))
        if (c.readMode && !c.searchOpen && !c.presenting) ResumeReadingChip(c, Modifier.align(Alignment.TopCenter).padding(top = if (c.focusMode) 72.dp else 66.dp))
        if (c.searchOpen) NoteSearchBar(c, Modifier.align(Alignment.TopCenter).padding(top = if (c.readMode) 64.dp else 10.dp))
        if (c.presenting) {
            // Di atas (di bawah bilah layar penuh / chip mode baca), supaya tidak tertutup pesan di bawah.
            val top = when {
                c.readMode -> if (c.focusMode) 72.dp else 66.dp
                c.focusMode && isTablet -> 80.dp
                else -> 12.dp
            }
            PresentationChip(c, Modifier.align(Alignment.TopCenter).padding(top = top))
        }
        if (!c.readMode && c.mode == InputMode.PDF && c.pdfSel != null) PdfActionBar(c, Modifier.align(Alignment.TopCenter).padding(top = 10.dp))
        (c.convert as? ConvertState.Working)?.let { ConvertProgress(c, it.message, Modifier.align(Alignment.TopCenter).padding(top = 72.dp)) }
        InlineEditing(c, fonts, addedFont, addFont, ribbon = isTablet && !c.focusMode && c.mode == InputMode.TYPE)
        }
    }
    // Fitur suara butuh izin mikrofon; aksinya ditunda sampai izin diberikan.
    var pendingMic by remember { mutableStateOf<(() -> Unit)?>(null) }
    val micLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        val action = pendingMic
        pendingMic = null
        if (ok) action?.invoke() else c.notice = "Izinkan mikrofon di pengaturan supaya fitur suara bisa dipakai."
    }
    val withMic: (() -> Unit) -> Unit = { action ->
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
            action()
        } else {
            pendingMic = action
            micLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }
    // Pindai dokumen: halaman hasil pindaian ditambahkan sesudah halaman ini.
    val scanDoc = rememberDocScanner(onPdf = { uri, _ -> c.importPdfPages(context, uri, scanned = true) }, onError = { c.notice = it })
    val insert = InsertActions(
        image = { pickImage.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
        link = { dialog = EditorDialog.InsertLink },
        sticker = { dialog = EditorDialog.InsertSticker },
        emoji = { dialog = EditorDialog.Emoji },
        pdf = { runCatching { pickPdf.launch(arrayOf("application/pdf")) } },
        scan = scanDoc,
        table = { dialog = EditorDialog.InsertTable },
        text = { c.newText() },
        date = { dialog = EditorDialog.InsertDate },
        mindMap = { c.newMindMap() },
        dictate = { withMic { c.startDictation(context) } },
        record = { withMic { c.startRecording(context) } },
        transcribe = { withMic { c.transcribeSelectedAudio(context) } },
    )

    if (isTablet) {
        TabletEditor(c, canvas, snackbar, onBack, insert, onDialog = { dialog = it }, typeRibbon = { TypeRibbon(c, fonts, addFont) })
    } else {
        PhoneEditor(c, canvas, snackbar, onBack, insert, onDialog = { dialog = it }, onSheet = { sheet = it })
    }

    when (sheet) {
        EditorSheet.ToolOptions -> ModalBottomSheet(onDismissRequest = { sheet = null }) {
            ToolOptions(c, onCustom = { dialog = EditorDialog.CustomColor })
        }
        EditorSheet.Pages -> ModalBottomSheet(onDismissRequest = { sheet = null }) {
            PagesSheet(c, onDialog = { dialog = it }, onDone = { sheet = null })
        }
        null -> Unit
    }

    (c.convert as? ConvertState.Failed)?.let { ConvertSheet(c, it) }
    c.bgRemoval?.let { RemoveBgDialog(c, it) }
    c.voice?.let { VoiceDialog(c, it) }
    c.pdfEditing?.let { t -> PdfEditDialog(t, onDismiss = { c.pdfEditing = null }) { c.pdfApplyEdit(it) } }
    c.cellFillPick?.let {
        ColorPickerDialog(0xFFDEEAF6.toInt(), onDismiss = { c.cellFillPick = null }) { col -> c.applyCellFillPick(col) }
    }
    c.tableLook?.let { t ->
        TableStyleDialog(t, onDismiss = { c.tableLook = null }) { style, color, header, clear ->
            c.applyTableStyle(style, color, header, clear)
        }
    }
    if (c.rotateAsk) {
        AlertDialog(
            onDismissRequest = { c.rotateAsk = false },
            title = { Text("Putar halaman?") },
            text = {
                Text(
                    "Teks ketik di halaman ini tidak ikut berputar: tetap tegak dan hanya digeser. Teks PDF yang diubah dan " +
                        "isian formulir jadi tidak pas lagi dengan tulisan aslinya. Sebaiknya putar halaman dulu sebelum mengisi " +
                        "atau mengubah teks. Coretan, stabilo, gambar, dan tanda tangan tetap ikut berputar.",
                )
            },
            confirmButton = { Button(onClick = { c.rotatePage(confirmed = true) }) { Text("Tetap putar") } },
            dismissButton = { TextButton(onClick = { c.rotateAsk = false }) { Text("Batal") } },
        )
    }
    c.pdfFieldEditing?.let { f -> PdfFieldDialog(c, f) }
    if (c.signatureOpen) SignatureDialog(c)

    when (val d = dialog) {
        null -> Unit
        EditorDialog.Paper -> PaperDialog(c, onDismiss = { dialog = null })
        EditorDialog.Rename -> TextInputDialog(
            title = "Ganti nama catatan",
            initial = c.title,
            label = "Nama catatan",
            confirm = "Simpan",
            onDismiss = { dialog = null },
        ) { c.rename(it); dialog = null }
        EditorDialog.CustomColor -> ColorPickerDialog(
            initial = c.colorOf(c.styleTool),
            onDismiss = { dialog = null },
        ) { c.setColor(it); dialog = null }
        EditorDialog.ClearPage -> ConfirmDialog(
            title = "Bersihkan halaman ini?",
            message = "Semua isi halaman ini dihapus. Kamu masih bisa mengurungkannya.",
            confirm = "Bersihkan",
            onDismiss = { dialog = null },
        ) { c.clearPage(); dialog = null }
        is EditorDialog.DeletePage -> ConfirmDialog(
            title = "Hapus halaman ${d.index + 1}?",
            message = "Halaman ini beserta isinya akan dihapus permanen.",
            confirm = "Hapus",
            onDismiss = { dialog = null },
        ) { c.deletePage(d.index); dialog = null }
        EditorDialog.InsertLink -> LinkTargetDialog(
            c = c,
            heading = "Sisipkan link",
            initialUrl = "",
            initialLabel = "",
            confirm = "Sisipkan",
            onDismiss = { dialog = null },
        ) { url, label -> c.insertLink(url, label); dialog = null }
        EditorDialog.EditLink -> {
            val l = c.selectedLink()
            LinkTargetDialog(
                c = c,
                heading = "Ubah link",
                initialUrl = l?.url ?: "",
                initialLabel = l?.label ?: "",
                confirm = "Simpan",
                onDismiss = { dialog = null },
            ) { url, label -> c.editLink(url, label); dialog = null }
        }
        EditorDialog.InsertTable -> TableDialog(onDismiss = { dialog = null }) { r, k -> c.insertTable(r, k); dialog = null }
        EditorDialog.InsertSticker -> {
            val stickers by c.stickers.collectAsState()
            StickerDialog(
                stickers = stickers,
                onDismiss = { dialog = null },
                onPickColor = { col -> c.insertSticker(col); dialog = null },
                onPickImage = { f -> c.insertImageSticker(f); dialog = null },
                onAddImages = { pickStickers.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                onDelete = { f -> c.deleteSticker(f) },
            )
        }
        EditorDialog.Export -> ExportDialog(c, onDismiss = { dialog = null })
        EditorDialog.PageNumbers -> PageNumbersDialog(c, onDismiss = { dialog = null })
        is EditorDialog.Orientation -> OrientationDialog(c, d.index, onDismiss = { dialog = null })
        is EditorDialog.Outline -> OutlineSheet(c, d.addFor, onDismiss = { dialog = null })
        EditorDialog.Flashcards -> FlashcardsSheet(c, onDismiss = { dialog = null })
        EditorDialog.InsertDate -> DateDialog(
            onDismiss = { dialog = null },
            onDate = { text -> c.insertDate(text); dialog = null },
            onCalendar = { y, m, d -> c.insertCalendar(y, m, d); dialog = null },
        )
        EditorDialog.TableLayout -> {
            val t = c.selectedTable()
            if (t == null) {
                LaunchedEffect(Unit) { dialog = null }
            } else {
                TableLayoutDialog(t, onDismiss = { dialog = null }) { rows, cols, cf, rf, header, h ->
                    c.applyTableLayout(rows, cols, cf, rf, header, h)
                    dialog = null
                }
            }
        }
        EditorDialog.Emoji -> EmojiPicker(
            prefs = c.prefs(),
            onDismiss = { dialog = null },
            onPick = { e, size -> c.insertEmoji(e, size); dialog = null },
        )
        EditorDialog.InkLang -> InkLanguageDialog(c.inkLanguage, onDismiss = { dialog = null }) { l -> c.chooseInkLanguage(l); dialog = null }
        EditorDialog.ImageShadow -> {
            val img = c.selectedImage()
            if (img == null) {
                LaunchedEffect(Unit) { dialog = null }
            } else {
                ShadowDialog(img, c.imageDir(), onDismiss = { dialog = null }) { sh -> c.setImageShadow(sh); dialog = null }
            }
        }
        EditorDialog.CropImage -> {
            val img = c.selectedImage()
            if (img == null) {
                LaunchedEffect(Unit) { dialog = null }
            } else {
                CropDialog(img, c.imageDir(), onDismiss = { dialog = null }) { crop -> c.cropSelection(crop); dialog = null }
            }
        }
    }
}

private class InsertActions(
    val image: () -> Unit,
    val link: () -> Unit,
    val sticker: () -> Unit,
    val emoji: () -> Unit,
    val pdf: () -> Unit,
    val scan: () -> Unit,
    val table: () -> Unit,
    val text: () -> Unit,
    val date: () -> Unit,
    val mindMap: () -> Unit,
    val dictate: () -> Unit,
    val record: () -> Unit,
    val transcribe: () -> Unit,
)

// ---------------- tablet: dua baris alat, daftar halaman di kiri ----------------

@Composable
private fun TabletEditor(
    c: EditorController,
    canvas: @Composable (Modifier) -> Unit,
    snackbar: SnackbarHostState,
    onBack: () -> Unit,
    insert: InsertActions,
    onDialog: (EditorDialog) -> Unit,
    typeRibbon: @Composable () -> Unit,
) {
    var showPages by rememberSaveable { mutableStateOf(true) }
    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        containerColor = MaterialTheme.colorScheme.surface,
    ) { pad ->
        Column(Modifier.padding(pad).fillMaxSize()) {
            val focus = c.focusMode
            if (!focus) Row(
                Modifier.fillMaxWidth().height(58.dp).padding(horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Kembali ke rak") }
                Column(
                    Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(8.dp))
                        .then(if (c.readMode) Modifier else Modifier.clickable(onClickLabel = "Ganti nama") { onDialog(EditorDialog.Rename) })
                        .padding(horizontal = 8.dp, vertical = 2.dp)
                ) {
                    Text(c.title, style = MaterialTheme.typography.titleLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(
                        "Halaman ${c.index + 1} dari ${c.pages.size}",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                NoteNavButtons(c, onDialog)
                ReadModeButton(c)
                if (!c.readMode) {
                    IconButton(onClick = { c.undo() }, enabled = c.canUndo) { Icon(Icons.AutoMirrored.Filled.Undo, contentDescription = "Urungkan") }
                    IconButton(onClick = { c.redo() }, enabled = c.canRedo) { Icon(Icons.AutoMirrored.Filled.Redo, contentDescription = "Ulangi") }
                }
                IconButton(onClick = { c.toggleFocus() }) {
                    Icon(Icons.Filled.Fullscreen, contentDescription = if (c.readMode) "Layar penuh untuk membaca" else "Layar penuh untuk menulis")
                }
                IconButton(onClick = { showPages = !showPages }) {
                    Icon(
                        Icons.Filled.AutoStories,
                        contentDescription = if (showPages) "Sembunyikan daftar halaman" else "Tampilkan daftar halaman",
                        tint = if (showPages) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (c.readMode) {
                    IconButton(onClick = { onDialog(EditorDialog.Export) }) { Icon(Icons.Filled.Share, contentDescription = "Bagikan atau ekspor") }
                } else {
                    MoreMenu(c, onDialog, includePaper = true, includeRuler = false)
                }
            }
            val tools = !focus && !c.readMode
            if (tools) FeatureBar(c, insert, onDialog)
            if (tools) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
            if (tools) Row(
                Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp)
                    .height(62.dp),
                verticalAlignment = Alignment.Bottom,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                ModeSwitch(c, Modifier.align(Alignment.CenterVertically))
                // Pilih selalu ada di tempat yang sama, di mode tulis maupun mode Teks.
                Box(Modifier.align(Alignment.CenterVertically)) { SelectQuickButton(c) }
                BarDivider(Modifier.align(Alignment.CenterVertically))
                if (c.mode == InputMode.TYPE) {
                    // Mode Teks: pita format seperti di pengolah kata, menggantikan alat tulis.
                    Row(Modifier.align(Alignment.CenterVertically), verticalAlignment = Alignment.CenterVertically) { typeRibbon() }
                    return@Row
                }
                // Bilah ringkas: kuas dan pengaturannya di dua tombol yang membuka daftar tegak ke bawah.
                IconButton(onClick = { c.toggleTrayCompact() }, modifier = Modifier.align(Alignment.CenterVertically)) {
                    Icon(
                        if (c.trayCompact) Icons.Filled.UnfoldMore else Icons.Filled.UnfoldLess,
                        contentDescription = if (c.trayCompact) "Jajarkan semua alat di bilah" else "Ringkas bilah alat",
                        modifier = Modifier.rotate(90f),
                    )
                }
                if (c.trayCompact) {
                    Row(
                        Modifier.align(Alignment.CenterVertically),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        BrushPicker(c)
                        if (c.mode != InputMode.SELECT && c.mode != InputMode.PDF) ToolSettingsPicker(c, onDialog)
                    }
                    when (c.mode) {
                        InputMode.PDF -> {
                            BarDivider(Modifier.align(Alignment.CenterVertically))
                            Row(Modifier.align(Alignment.CenterVertically), verticalAlignment = Alignment.CenterVertically) { PdfToolOptions(c) }
                        }
                        InputMode.SELECT -> {
                            BarDivider(Modifier.align(Alignment.CenterVertically))
                            Text(
                                "Ketuk untuk memilih. Lingkari dengan laso untuk memilih banyak.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.align(Alignment.CenterVertically),
                            )
                        }
                        else -> Unit
                    }
                    return@Row
                }
                ToolTray(c)
                BarDivider(Modifier.align(Alignment.CenterVertically))
                when (c.mode) {
                    InputMode.PDF -> Row(Modifier.align(Alignment.CenterVertically), verticalAlignment = Alignment.CenterVertically) {
                        PdfToolOptions(c)
                    }
                    InputMode.SELECT -> Text(
                        "Ketuk untuk memilih. Lingkari tulisan atau objek dengan laso untuk memilih banyak. Geser untuk memindahkan, tarik sudutnya untuk mengubah ukuran.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.align(Alignment.CenterVertically),
                    )
                    else -> {
                        if (c.mode == InputMode.SHAPE) {
                            Row(Modifier.align(Alignment.CenterVertically), verticalAlignment = Alignment.CenterVertically) {
                                ShapeChoices(c)
                            }
                            BarDivider(Modifier.align(Alignment.CenterVertically))
                        }
                        if (c.mode == InputMode.TAPE) {
                            // Selotip: bentuk (menu ringkas) dan tutup/buka/lepas semua, lalu warna dan ketebalan.
                            Row(Modifier.align(Alignment.CenterVertically), verticalAlignment = Alignment.CenterVertically) {
                                TapeTypePicker(c)
                                TapeActionIcons(c)
                            }
                            BarDivider(Modifier.align(Alignment.CenterVertically))
                        }
                        if (c.styleTool != Tool.ERASER) {
                            Row(Modifier.align(Alignment.CenterVertically), verticalAlignment = Alignment.CenterVertically) {
                                ColorChoices(c, dot = 22, onCustom = { onDialog(EditorDialog.CustomColor) })
                            }
                            BarDivider(Modifier.align(Alignment.CenterVertically))
                        } else {
                            Row(Modifier.align(Alignment.CenterVertically), verticalAlignment = Alignment.CenterVertically) {
                                EraserModeChoices(c)
                            }
                            BarDivider(Modifier.align(Alignment.CenterVertically))
                        }
                        Row(Modifier.align(Alignment.CenterVertically), verticalAlignment = Alignment.CenterVertically) {
                            WidthChoices(c, withSlider = true)
                        }
                    }
                }
            }
            if (!focus) HorizontalDivider()
            Row(Modifier.weight(1f).fillMaxWidth()) {
                // (Mode baca: daftar halaman hanya untuk berpindah halaman.)
                if (showPages && !focus) {
                    PagesRail(c, onDialog, Modifier.width(132.dp).fillMaxHeight())
                    VerticalDivider()
                }
                val editing = c.editingText != null
                Box(Modifier.weight(1f).fillMaxHeight().then(if (editing) Modifier.imePadding() else Modifier)) {
                    canvas(Modifier.fillMaxSize())
                    if (focus && !editing && !c.readMode) FocusToolbar(c, onDialog, Modifier.align(Alignment.TopCenter).padding(top = 10.dp))
                    SelectionBar(c, onDialog, insert, Modifier.align(Alignment.TopCenter).padding(top = if (focus) 80.dp else 12.dp))
                    if (!editing && c.mapState == null) {
                        PagePill(c, Modifier.align(Alignment.BottomEnd).padding(16.dp), onPages = null)
                    }
                }
            }
        }
    }
}

/**
 * Bilah fitur di atas: semua fitur dalam satu baris berlabel yang bisa digeser, dikelompokkan menjadi
 * Sisipkan, Suara, Tulisan, Alat, dan Halaman. Tiap kelompok bisa dilipat dengan mengetuk judulnya,
 * supaya bilah tidak penuh; pilihan buka/tutup tiap kelompok diingat.
 */
@Composable
private fun FeatureBar(c: EditorController, insert: InsertActions, onDialog: (EditorDialog) -> Unit) {
    Column(Modifier.fillMaxWidth()) {
        // Baris judul kelompok. Tombol kelompok yang dibuka turun di baris bawahnya, dan naik lagi saat disembunyikan.
        Row(
            Modifier
                .fillMaxWidth()
                .height(52.dp)
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 8.dp, vertical = 3.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            FeaturePill(c, "Sisipkan", Icons.Filled.AddCircle)
            FeaturePill(c, "Suara", Icons.Filled.Mic)
            FeaturePill(c, "Tulisan", Icons.Filled.TextFields)
            FeaturePill(c, "Alat", Icons.Filled.Straighten)
            FeaturePill(c, "Halaman", Icons.Filled.AutoStories)
            FeaturePill(c, "PDF", Icons.Filled.PictureAsPdf)
            Spacer(Modifier.width(8.dp))
        }
        // Saat menutup, isi kelompok terakhir tetap tampil sampai baris selesai naik.
        var shown by remember { mutableStateOf(c.openGroup) }
        LaunchedEffect(c.openGroup) { c.openGroup?.let { shown = it } }
        val target = c.openGroup ?: shown
        AnimatedVisibility(
            visible = c.openGroup != null,
            enter = expandVertically(tween(240), expandFrom = Alignment.Top) +
                slideInVertically(tween(240)) { -it } + fadeIn(tween(200)),
            exit = shrinkVertically(tween(220), shrinkTowards = Alignment.Top) +
                slideOutVertically(tween(220)) { -it } + fadeOut(tween(160)),
        ) {
            Column {
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))
                Crossfade(targetState = target, animationSpec = tween(160), label = "kelompok") { g ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .height(58.dp)
                            .horizontalScroll(rememberScrollState())
                            .padding(horizontal = 8.dp, vertical = 3.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        FeatureGroupButtons(c, g, insert, onDialog)
                    }
                }
            }
        }
    }
}

/** Tombol-tombol satu kelompok fitur. */
@Composable
private fun FeatureGroupButtons(c: EditorController, group: String?, insert: InsertActions, onDialog: (EditorDialog) -> Unit) {
    when (group) {
        "Sisipkan" -> {
            FeatureButton(Icons.Filled.TextFields, "Teks", onClick = insert.text)
            FeatureButton(Icons.Filled.CheckBox, "Centang", onClick = { c.newText(TextLists.BOX) })
            FeatureButton(Icons.Filled.Image, "Gambar", onClick = insert.image)
            FeatureButton(Icons.Filled.DocumentScanner, "Pindai", onClick = insert.scan)
            FeatureButton(Icons.Filled.EmojiEmotions, "Stiker", onClick = insert.sticker)
            FeatureButton(Icons.Filled.Mood, "Emoji", onClick = insert.emoji)
            val clip by InkClipboard.clip.collectAsState()
            FeatureButton(Icons.Filled.Link, "Link", onClick = insert.link)
            FeatureButton(Icons.Filled.GridOn, "Tabel", onClick = insert.table)
            FeatureButton(Icons.Filled.CalendarMonth, "Tanggal", onClick = insert.date)
            FeatureButton(Icons.Filled.AccountTree, "Mind map", onClick = insert.mindMap)
        }
        "Suara" -> {
            FeatureButton(Icons.Filled.RecordVoiceOver, "Dikte", onClick = insert.dictate)
            FeatureButton(Icons.Filled.Mic, "Rekam", onClick = insert.record)
        }
        "Tulisan" -> {
            FeatureButton(Icons.Filled.Transform, "Jadi teks", active = c.convertPick, onClick = { if (c.convertPick) c.cancelConvertPick() else c.requestConvert() })
            FeatureButton(Icons.Filled.AutoFixHigh, "Otomatis", active = c.autoText, onClick = { c.toggleAutoText() })
            FeatureButton(Icons.Filled.FormatSize, "Huruf besar", active = c.autoCaps, onClick = { c.toggleAutoCaps() })
            FeatureButton(Icons.Filled.Translate, "Bahasa", onClick = { onDialog(EditorDialog.InkLang) })
        }
        "Alat" -> {
            FeatureButton(Icons.Filled.Straighten, "Penggaris", active = c.ruler, onClick = { c.toggleRuler() })
            FeatureButton(Icons.Filled.Category, "Bentuk otomatis", active = c.autoShape, onClick = { c.toggleAutoShape() })
            FeatureButton(Icons.Filled.Style, "Kartu hafalan", onClick = { onDialog(EditorDialog.Flashcards) })
            val ctx = LocalContext.current
            FeatureButton(Icons.Filled.Slideshow, "Presentasi", active = c.presenting, onClick = { if (c.presenting) c.stopPresentation() else c.startPresentation(ctx) })
            FeatureButton(Icons.Filled.Adjust, "Laser", active = c.laserOn, onClick = { c.toggleLaser() })
            FeatureButton(Icons.Filled.TouchApp, "Jari", active = c.fingerDraws, onClick = { c.toggleFinger() })
            FeatureButton(Icons.Filled.Tune, "Kertas", onClick = { onDialog(EditorDialog.Paper) })
            FeatureButton(Icons.Filled.Share, "Ekspor", onClick = { onDialog(EditorDialog.Export) })
        }
        "Halaman" -> {
            FeatureButton(Icons.Filled.PostAdd, "Tambah", onClick = { c.addPage() })
            FeatureButton(Icons.Filled.ContentCopy, "Duplikat", onClick = { c.duplicatePage() })
            FeatureButton(Icons.Filled.PictureAsPdf, "Impor PDF", onClick = insert.pdf)
            FeatureButton(Icons.Filled.FormatListNumbered, "Nomor hal.", active = c.pageNumberSpec != null, onClick = { onDialog(EditorDialog.PageNumbers) })
            FeatureButton(
                if (c.isLandscape()) Icons.Filled.StayCurrentLandscape else Icons.Filled.StayCurrentPortrait,
                "Orientasi",
                onClick = { onDialog(EditorDialog.Orientation(c.index)) },
            )
        }
        "PDF" -> {
            FeatureButton(Icons.Filled.Highlight, "Pilih teks", active = c.mode == InputMode.PDF && !c.pdfForm, onClick = { c.selectPdfText() })
            FeatureButton(Icons.Filled.CheckBox, "Formulir", active = c.mode == InputMode.PDF && c.pdfForm, onClick = { c.togglePdfForm() })
            FeatureButton(Icons.Filled.Gesture, "Tanda tangan", onClick = { c.openSignature() })
            FeatureButton(Icons.Filled.Rotate90DegreesCw, "Putar", onClick = { c.rotatePage() })
            FeatureButton(Icons.Filled.MergeType, "Gabung PDF", onClick = insert.pdf)
            FeatureButton(Icons.Filled.Share, "Ekspor hal.", onClick = { onDialog(EditorDialog.Export) })
        }
        else -> Unit
    }
}

/**
 * Judul satu kelompok fitur berbentuk pil. Diketuk: tombol-tombolnya turun di bawah bilah; diketuk lagi:
 * naik dan tersembunyi.
 */
@Composable
private fun FeaturePill(c: EditorController, label: String, icon: androidx.compose.ui.graphics.vector.ImageVector) {
    val open = c.isGroupOpen(label)
    val bg = if (open) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f)
    val fg = if (open) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
    val turn by animateFloatAsState(if (open) 180f else 0f, tween(220), label = "panah")
    Row(
        Modifier
            .height(40.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(bg)
            .toolClick(if (open) "Sembunyikan $label" else "Tampilkan $label") { c.toggleGroup(label) }
            .padding(start = 10.dp, end = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = fg, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(6.dp))
        Text(label, style = MaterialTheme.typography.labelLarge, color = fg, maxLines = 1)
        // Panah ke bawah: tombolnya akan turun; berputar ke atas saat terbuka.
        Icon(
            Icons.Filled.KeyboardArrowDown,
            contentDescription = null,
            tint = fg,
            modifier = Modifier.size(20.dp).graphicsLayer { rotationZ = turn },
        )
    }
}

@Composable
private fun FeatureButton(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, active: Boolean = false, onClick: () -> Unit) {
    val bg = if (active) MaterialTheme.colorScheme.primaryContainer else Color.Transparent
    val fg = if (active) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
    Column(
        Modifier
            // Label panjang (Bentuk otomatis, Kartu hafalan) tampil utuh: tombolnya ikut melebar.
            .widthIn(min = 64.dp)
            .height(52.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(bg)
            .toolClick(label, onClick)
            .padding(top = 6.dp, start = 6.dp, end = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(icon, contentDescription = null, tint = fg, modifier = Modifier.size(22.dp))
        Spacer(Modifier.height(2.dp))
        Text(label, style = MaterialTheme.typography.labelSmall, color = fg, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/** Jendela suara: dikte, rekam, atau ubah rekaman jadi teks. */
@Composable
private fun VoiceDialog(c: EditorController, s: VoiceSession) {
    val context = LocalContext.current
    AlertDialog(
        onDismissRequest = {},
        properties = DialogProperties(dismissOnClickOutside = false, dismissOnBackPress = false),
        title = {
            Text(
                when (s.mode) {
                    VoiceMode.DICTATE -> "Dikte jadi teks"
                    VoiceMode.RECORD -> "Rekam suara"
                    VoiceMode.TRANSCRIBE -> "Ubah rekaman jadi teks"
                }
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (s.mode == VoiceMode.DICTATE) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        ToolChip(selected = s.lang == "id-ID", onClick = { c.setVoiceLanguage(context, "id-ID") }, label = { Text("Indonesia") })
                        ToolChip(selected = s.lang == "en-US", onClick = { c.setVoiceLanguage(context, "en-US") }, label = { Text("Inggris") })
                    }
                }
                if (!s.started) {
                    // Belum mulai: tunggu sampai siap, lalu ketuk tombol besar ini.
                    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                        Box(
                            Modifier
                                .padding(top = 8.dp)
                                .size(96.dp)
                                .clip(CircleShape)
                                .background(if (s.mode == VoiceMode.RECORD) Color(0xFFD64545) else MaterialTheme.colorScheme.primary)
                                .toolClick(onClickLabel = if (s.mode == VoiceMode.RECORD) "Mulai merekam" else "Mulai dikte") { c.beginVoice(context) },
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(Icons.Filled.Mic, contentDescription = null, tint = Color.White, modifier = Modifier.size(44.dp))
                        }
                        Spacer(Modifier.height(12.dp))
                        Text(
                            if (s.mode == VoiceMode.RECORD) "Ketuk tombol merah saat siap merekam." else "Ketuk tombol saat siap bicara.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                        )
                    }
                } else if (s.mode == VoiceMode.RECORD) {
                    Text(
                        InkRenderer.durationLabel(s.elapsedMs),
                        style = MaterialTheme.typography.displaySmall,
                        modifier = Modifier.fillMaxWidth(),
                        textAlign = TextAlign.Center,
                    )
                    LevelBar(s.level)
                    Text(
                        if (s.paused) "Dijeda. Ketuk Lanjut untuk merekam lagi." else "Merekam… Ketuk Selesai untuk menempelkan rekaman di halaman.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    if (s.mode == VoiceMode.DICTATE) LevelBar(if (s.listening) s.level else 0f)
                    val grey = MaterialTheme.colorScheme.onSurfaceVariant
                    val shown = buildAnnotatedString {
                        append(s.text)
                        if (s.partial.isNotBlank()) {
                            if (s.text.isNotEmpty()) append(" ")
                            pushStyle(SpanStyle(color = grey))
                            append(s.partial)
                            pop()
                        }
                    }
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        modifier = Modifier.fillMaxWidth().heightIn(min = 90.dp, max = 240.dp),
                    ) {
                        Box(Modifier.verticalScroll(rememberScrollState()).padding(12.dp)) {
                            if (shown.isEmpty()) {
                                Text(
                                    when {
                                        s.mode == VoiceMode.TRANSCRIBE -> "Teks rekaman muncul di sini."
                                        s.listening -> "Silakan bicara…"
                                        else -> "Dijeda."
                                    },
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = grey,
                                )
                            } else {
                                Text(shown, style = MaterialTheme.typography.bodyLarge)
                            }
                        }
                    }
                    if (s.mode == VoiceMode.TRANSCRIBE && s.listening) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                            Spacer(Modifier.width(10.dp))
                            Text("Membaca rekaman…", style = MaterialTheme.typography.bodySmall, color = grey)
                        }
                    }
                }
                val status = s.status
                if (status != null && !(s.mode == VoiceMode.TRANSCRIBE && s.listening)) {
                    Text(status, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                }
            }
        },
        confirmButton = {
            if (!s.started) {
                Button(onClick = { c.beginVoice(context) }) {
                    Icon(Icons.Filled.Mic, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(if (s.mode == VoiceMode.RECORD) "Mulai rekam" else "Mulai dikte")
                }
            } else if (s.mode == VoiceMode.RECORD) {
                Button(onClick = { c.finishRecording() }) { Text("Selesai") }
            } else {
                Button(onClick = { c.insertVoiceText() }, enabled = (s.text + s.partial).isNotBlank()) { Text("Sisipkan") }
            }
        },
        dismissButton = {
            Row {
                if (s.started) when (s.mode) {
                    VoiceMode.RECORD -> TextButton(onClick = { c.togglePauseRecording() }) { Text(if (s.paused) "Lanjut" else "Jeda") }
                    VoiceMode.DICTATE -> TextButton(onClick = { if (s.listening) c.pauseDictation() else c.resumeDictation(context) }) {
                        Text(if (s.listening) "Jeda" else "Lanjut")
                    }
                    VoiceMode.TRANSCRIBE -> Unit
                }
                TextButton(onClick = { c.closeVoice() }) { Text("Batal") }
            }
        },
    )
}

/** Penunjuk kekerasan suara. */
@Composable
private fun LevelBar(level: Float) {
    val shown by androidx.compose.animation.core.animateFloatAsState(level.coerceIn(0f, 1f), label = "level")
    val track = MaterialTheme.colorScheme.surfaceVariant
    val fill = MaterialTheme.colorScheme.primary
    Canvas(Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp))) {
        drawRect(track)
        drawRect(fill, size = Size(size.width * (0.04f + 0.96f * shown), size.height))
    }
}

@Composable
private fun BarDivider(modifier: Modifier = Modifier) {
    Box(modifier.padding(horizontal = 6.dp).width(1.dp).height(30.dp).background(MaterialTheme.colorScheme.outlineVariant))
}

@Composable
private fun PagesRail(c: EditorController, onDialog: (EditorDialog) -> Unit, modifier: Modifier) {
    val state = rememberLazyListState()
    val reorder = remember(c) { ReorderState() }
    reorder.onMove = { from, to -> c.movePage(from, to) }
    // Ikuti halaman yang dibuka (bukan nomor urutnya, supaya tidak meloncat setelah halaman dipindah).
    val openId = c.pages.getOrNull(c.index)?.id
    LaunchedEffect(openId) {
        val target = c.index
        if (target !in c.pages.indices) return@LaunchedEffect
        // Gulir hanya bila halamannya tidak tampil utuh, dan secukupnya (tanpa melompat ke atas daftar).
        val info = state.layoutInfo
        val item = info.visibleItemsInfo.firstOrNull { it.index == target }
        if (item == null) {
            state.animateScrollToItem(target)
        } else {
            val top = item.offset
            val bottom = item.offset + item.size
            val start = info.viewportStartOffset + 12
            val end = info.viewportEndOffset - 12
            when {
                top < start -> state.animateScrollBy((top - start).toFloat())
                bottom > end -> state.animateScrollBy((bottom - end).toFloat())
            }
        }
    }
    val edge = with(LocalDensity.current) { 56.dp.toPx() }
    LaunchedEffect(reorder.dragKey) { reorder.autoScroll(state, edge) }
    Column(modifier.background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f))) {
        LazyColumn(
            Modifier.weight(1f).onGloballyPositioned { reorder.listBounds = rectInRoot(it) },
            state = state,
            contentPadding = PaddingValues(horizontal = 18.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            itemsIndexed(c.pages, key = { _, p -> p.id }) { i, p ->
                PageThumb(
                    i, p, i == c.index, c, onDialog, onOpened = {}, reorder = reorder,
                    modifier = if (reorder.dragKey == p.id) Modifier else Modifier.animateItem(fadeInSpec = null, fadeOutSpec = null),
                )
            }
        }
        if (!c.readMode) TextButton(onClick = { c.addPage() }, modifier = Modifier.fillMaxWidth().padding(8.dp)) {
            Icon(Icons.Filled.Add, contentDescription = null)
            Spacer(Modifier.width(4.dp))
            Text("Halaman")
        }
    }
}

@Composable
private fun PageThumb(
    i: Int,
    p: PageMeta,
    selected: Boolean,
    c: EditorController,
    onDialog: (EditorDialog) -> Unit,
    onOpened: () -> Unit,
    reorder: ReorderState,
    modifier: Modifier = Modifier,
) {
    var menu by remember { mutableStateOf(false) }
    // Isi halaman ditampilkan samar; halaman yang sedang dibuka sedikit lebih jelas.
    LaunchedEffect(p) { c.requestThumb(p) }
    val thumb = c.pageThumbs[p.id]
    val dragging = reorder.dragKey == p.id
    val isTarget = reorder.dragging && !dragging && reorder.target == i
    // Mode baca: halaman hanya bisa dibuka, tidak bisa dipindah, ditambah, atau dihapus.
    val locked = c.readMode
    val last = if (locked) 0 else c.pages.lastIndex
    Column(
        modifier.reorderItem(reorder, p.id, i),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box {
            Box(
                Modifier
                    .fillMaxWidth()
                    .aspectRatio(p.width / p.height)
                    .clip(RoundedCornerShape(3.dp))
                    .background(Color(p.paperColor))
                    .border(
                        when {
                            isTarget -> 3.dp
                            selected -> 2.5.dp
                            else -> 1.dp
                        },
                        if (selected || isTarget || dragging) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                        RoundedCornerShape(3.dp),
                    )
                    // Tahan lalu geser untuk memindah; tahan lalu lepas untuk pilihan lain.
                    .then(
                        if (locked) {
                            Modifier.clickable(onClickLabel = "Buka halaman ${i + 1}") { c.showPage(i); onOpened() }
                        } else if (last > 0) {
                            Modifier
                                .clickable(onClickLabel = "Buka halaman ${i + 1}") { c.showPage(i); onOpened() }
                                .reorderOnLongPress(reorder, p.id) { menu = true }
                        } else {
                            Modifier.combinedClickable(
                                onClickLabel = "Buka halaman ${i + 1}",
                                onClick = { c.showPage(i); onOpened() },
                                onLongClick = { menu = true },
                            )
                        }
                    ),
            ) {
                if (thumb != null) {
                    Image(
                        thumb,
                        contentDescription = null,
                        contentScale = ContentScale.FillBounds,
                        alpha = if (selected || dragging) 0.9f else 0.6f,
                        modifier = Modifier.fillMaxSize(),
                    )
                } else {
                    PaperPreview(p.template, p.paperColor, Modifier.fillMaxSize())
                }
                if (isTarget) {
                    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)))
                }
            }
            if (last > 0) {
                Box(
                    Modifier
                        .align(Alignment.TopEnd)
                        .padding(3.dp)
                        .size(26.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surface.copy(alpha = if (dragging) 1f else 0.85f))
                        .reorderHandle(reorder, p.id)
                        .semantics { contentDescription = "Seret untuk memindah halaman ${i + 1}" },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Filled.DragIndicator, contentDescription = null, modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                DropdownMenuItem(text = { Text("Tambah halaman sesudahnya") }, leadingIcon = { Icon(Icons.Filled.Add, null) }, onClick = { menu = false; c.addPage(i); onOpened() })
                DropdownMenuItem(text = { Text("Duplikat") }, leadingIcon = { Icon(Icons.Filled.ContentCopy, null) }, onClick = { menu = false; c.duplicatePage(i); onOpened() })
                DropdownMenuItem(
                    text = { Text("Masukkan ke daftar isi") },
                    leadingIcon = { Icon(Icons.AutoMirrored.Filled.Toc, null) },
                    onClick = { menu = false; onOpened(); onDialog(EditorDialog.Outline(addFor = i)) },
                )
                if (PageBackground.pdfOf(p.background) == null && p.width != p.height) {
                    val land = p.width > p.height
                    DropdownMenuItem(
                        text = { Text(if (land) "Jadikan tegak" else "Jadikan mendatar") },
                        leadingIcon = { Icon(if (land) Icons.Filled.StayCurrentPortrait else Icons.Filled.StayCurrentLandscape, null) },
                        onClick = { menu = false; c.setOrientation(!land, toAll = false, at = i) },
                    )
                }
                if (i > 0) {
                    DropdownMenuItem(text = { Text("Pindah ke sebelumnya") }, leadingIcon = { Icon(Icons.Filled.ArrowUpward, null) }, onClick = { menu = false; c.movePage(i, i - 1) })
                }
                if (i < last) {
                    DropdownMenuItem(text = { Text("Pindah ke sesudahnya") }, leadingIcon = { Icon(Icons.Filled.ArrowDownward, null) }, onClick = { menu = false; c.movePage(i, i + 1) })
                }
                if (i > 1) {
                    DropdownMenuItem(text = { Text("Jadikan halaman pertama") }, leadingIcon = { Icon(Icons.Filled.VerticalAlignTop, null) }, onClick = { menu = false; c.movePage(i, 0) })
                }
                if (i < last - 1) {
                    DropdownMenuItem(text = { Text("Jadikan halaman terakhir") }, leadingIcon = { Icon(Icons.Filled.VerticalAlignBottom, null) }, onClick = { menu = false; c.movePage(i, last) })
                }
                DropdownMenuItem(text = { Text("Hapus") }, leadingIcon = { Icon(Icons.Filled.Delete, null) }, onClick = { menu = false; onDialog(EditorDialog.DeletePage(i)) })
            }
        }
        Spacer(Modifier.height(4.dp))
        Text(
            if (dragging && reorder.target != i) "${i + 1} → ${reorder.target + 1}" else "${i + 1}",
            style = MaterialTheme.typography.labelMedium,
            color = if (selected || dragging) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

// ---------------- HP: bilah atas ringkas, baki alat di bawah ----------------

@Composable
private fun PhoneEditor(
    c: EditorController,
    canvas: @Composable (Modifier) -> Unit,
    snackbar: SnackbarHostState,
    onBack: () -> Unit,
    insert: InsertActions,
    onDialog: (EditorDialog) -> Unit,
    onSheet: (EditorSheet) -> Unit,
) {
    Scaffold(
        topBar = {
            if (!c.focusMode) Column {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Kembali ke rak") }
                },
                title = {
                    Column(if (c.readMode) Modifier else Modifier.clickable(onClickLabel = "Ganti nama") { onDialog(EditorDialog.Rename) }) {
                        Text(c.title, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(
                            "Hlm ${c.index + 1} dari ${c.pages.size}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
                actions = {
                    NoteNavButtons(c, onDialog)
                    ReadModeButton(c)
                    IconButton(onClick = { c.toggleFocus() }) {
                        Icon(Icons.Filled.Fullscreen, contentDescription = if (c.readMode) "Layar penuh untuk membaca" else "Layar penuh untuk menulis")
                    }
                    if (c.readMode) {
                        IconButton(onClick = { onDialog(EditorDialog.Export) }) { Icon(Icons.Filled.Share, contentDescription = "Bagikan atau ekspor") }
                    } else {
                        MoreMenu(c, onDialog, includePaper = true, includeRuler = false)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface),
            )
            if (c.editingText == null && !c.readMode) {
                Column(Modifier.background(MaterialTheme.colorScheme.surface)) {
                    FeatureBar(c, insert, onDialog)
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                }
            }
            }
        },
        bottomBar = { if (c.editingText == null && !c.focusMode && !c.readMode) PhoneDock(c, onOptions = { onSheet(EditorSheet.ToolOptions) }) },
        snackbarHost = { SnackbarHost(snackbar) },
        containerColor = MaterialTheme.colorScheme.surface,
    ) { pad ->
        val editing = c.editingText != null
        Box(Modifier.padding(pad).fillMaxSize().then(if (editing) Modifier.imePadding() else Modifier)) {
            canvas(Modifier.fillMaxSize())
            SelectionBar(c, onDialog, insert, Modifier.align(Alignment.TopCenter).padding(top = 10.dp))
            if (editing) {
                // Mengetik langsung di halaman: tidak ada kolom ketik; keyboard di bawah.
            } else if (c.focusMode && !c.readMode) {
                FocusToolbar(c, onDialog, Modifier.align(Alignment.BottomCenter).padding(bottom = 14.dp, start = 8.dp, end = 8.dp))
            } else if (c.readMode) {
                PagePill(c, Modifier.align(Alignment.BottomEnd).padding(12.dp).navigationBarsPadding(), onPages = { onSheet(EditorSheet.Pages) })
            } else Row(
                Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(horizontal = 12.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // Urungkan dan ulangi di dekat ibu jari.
                Surface(shape = RoundedCornerShape(50), tonalElevation = 3.dp, shadowElevation = 4.dp) {
                    Row {
                        IconButton(onClick = { c.undo() }, enabled = c.canUndo) { Icon(Icons.AutoMirrored.Filled.Undo, contentDescription = "Urungkan") }
                        IconButton(onClick = { c.redo() }, enabled = c.canRedo) { Icon(Icons.AutoMirrored.Filled.Redo, contentDescription = "Ulangi") }
                    }
                }
                PagePill(c, Modifier, onPages = { onSheet(EditorSheet.Pages) })
            }
        }
    }
}

@Composable
private fun PhoneDock(c: EditorController, onOptions: () -> Unit) {
    Surface(tonalElevation = 3.dp, shadowElevation = 8.dp) {
        Row(
            Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 4.dp)
                .height(62.dp),
            verticalAlignment = Alignment.Bottom,
        ) {
            Tool.tray.forEach { t ->
                val selected = c.mode == InputMode.INK && t == c.tool
                Box(
                    Modifier
                        .weight(1f)
                        .height(56.dp)
                        .toolClick { if (selected) onOptions() else c.selectTool(t) }
                        .semantics { contentDescription = t.label },
                    contentAlignment = Alignment.BottomCenter,
                ) {
                    ToolGlyph(t, Color(c.colorOf(t)), selected)
                }
            }
            Box(
                Modifier
                    .weight(1f)
                    .height(56.dp)
                    .toolClick { if (c.mode == InputMode.SHAPE) onOptions() else c.selectShapes() }
                    .semantics { contentDescription = "Bentuk" },
                contentAlignment = Alignment.BottomCenter,
            ) {
                ShapeGlyph(Color(c.colorOf(Tool.LINE)), c.mode == InputMode.SHAPE)
            }
            Box(
                Modifier
                    .weight(1f)
                    .height(56.dp)
                    .toolClick { if (c.mode == InputMode.TAPE) onOptions() else c.selectTape() }
                    .semantics { contentDescription = "Selotip" },
                contentAlignment = Alignment.BottomCenter,
            ) {
                TapeGlyph(Color(c.colorOf(Tool.TAPE)), c.mode == InputMode.TAPE)
            }
            Box(
                Modifier
                    .weight(1f)
                    .height(56.dp)
                    .toolClick { c.selectPointer() }
                    .semantics { contentDescription = "Pilih" },
                contentAlignment = Alignment.BottomCenter,
            ) {
                SelectGlyph(MaterialTheme.colorScheme.onSurface, c.mode == InputMode.SELECT)
            }
            Box(
                Modifier
                    .weight(1f)
                    .height(56.dp)
                    .toolClick { c.selectTyping() }
                    .semantics { contentDescription = "Mode teks" },
                contentAlignment = Alignment.BottomCenter,
            ) {
                TypeGlyph(MaterialTheme.colorScheme.onSurface, c.mode == InputMode.TYPE)
            }
            Box(
                Modifier
                    .align(Alignment.CenterVertically)
                    .padding(start = 2.dp, end = 4.dp)
                    .size(40.dp)
                    .clip(CircleShape)
                    .toolClick("Warna dan ketebalan", onOptions),
                contentAlignment = Alignment.Center,
            ) {
                val dot = if (c.styleTool == Tool.ERASER || c.mode == InputMode.SELECT) MaterialTheme.colorScheme.outline else Color(c.colorOf(c.styleTool))
                Box(Modifier.size(24.dp).clip(CircleShape).background(dot).border(2.dp, MaterialTheme.colorScheme.surface, CircleShape))
            }
        }
    }
}

@Composable
private fun ToolOptions(c: EditorController, onCustom: () -> Unit) {
    Column(
        Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        if (c.mode == InputMode.SELECT) {
            Text("Pilih", style = MaterialTheme.typography.titleLarge)
            Text(
                "Ketuk benda untuk memilih. Geser untuk memindahkan, tarik sudutnya untuk mengubah ukuran.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            return@Column
        }
        Text(
            when (c.mode) { InputMode.SHAPE -> "Bentuk"; InputMode.TAPE -> "Selotip"; else -> c.tool.label },
            style = MaterialTheme.typography.titleLarge,
        )
        if (c.mode == InputMode.SHAPE) {
            Row(Modifier.horizontalScroll(rememberScrollState()), verticalAlignment = Alignment.CenterVertically) {
                ShapeChoices(c)
            }
        }
        if (c.mode == InputMode.TAPE) {
            Text(
                "Tempel selotip di atas tulisan untuk menghafal. Ketuk selotip untuk membuka, ketuk lagi untuk menutup.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text("Tipe", style = MaterialTheme.typography.labelLarge)
            Row(Modifier.horizontalScroll(rememberScrollState()), verticalAlignment = Alignment.CenterVertically) {
                TapeShapeChoices(c, TapeShape.BANDS)
            }
            Row(Modifier.horizontalScroll(rememberScrollState()), verticalAlignment = Alignment.CenterVertically) {
                TapeActions(c)
            }
        }
        if (c.styleTool != Tool.ERASER) {
            Text("Warna", style = MaterialTheme.typography.labelLarge)
            Row(Modifier.horizontalScroll(rememberScrollState()), verticalAlignment = Alignment.CenterVertically) {
                ColorChoices(c, dot = 28, onCustom = onCustom)
            }
        } else {
            Text("Cara menghapus", style = MaterialTheme.typography.labelLarge)
            Row(Modifier.horizontalScroll(rememberScrollState()), verticalAlignment = Alignment.CenterVertically) { EraserModeChoices(c) }
        }
        Text("Ketebalan · ${mmLabel(c.widthOf(c.styleTool))}", style = MaterialTheme.typography.labelLarge)
        Row(verticalAlignment = Alignment.CenterVertically) { WidthChoices(c, withSlider = false) }
        Slider(
            value = c.widthOf(c.styleTool),
            onValueChange = { c.setWidth(it) },
            valueRange = c.styleTool.minWidth..c.styleTool.maxWidth,
        )
        if (c.mode != InputMode.TAPE) StrokePreview(c, Modifier.fillMaxWidth().height(44.dp))
    }
}

@Composable
private fun PagesSheet(c: EditorController, onDialog: (EditorDialog) -> Unit, onDone: () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, bottom = 24.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Halaman", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
            if (!c.readMode) TextButton(onClick = { c.addPage(); onDone() }) {
                Icon(Icons.Filled.Add, contentDescription = null)
                Spacer(Modifier.width(4.dp))
                Text("Tambah")
            }
        }
        Spacer(Modifier.height(8.dp))
        if (c.pages.size > 1 && !c.readMode) {
            Text(
                "Tahan halaman lalu geser (atau seret pegangan di pojoknya) untuk memindah urutan. Tahan lalu lepas untuk pilihan lain.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(8.dp))
        }
        val grid = rememberLazyGridState()
        val reorder = remember(c) { ReorderState() }
        reorder.onMove = { from, to -> c.movePage(from, to) }
        val edge = with(LocalDensity.current) { 48.dp.toPx() }
        LaunchedEffect(reorder.dragKey) { reorder.autoScroll(grid, edge) }
        LazyVerticalGrid(
            columns = GridCells.Adaptive(84.dp),
            state = grid,
            modifier = Modifier.fillMaxWidth().heightIn(max = 440.dp).onGloballyPositioned { reorder.listBounds = rectInRoot(it) },
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            itemsIndexed(c.pages, key = { _, p -> p.id }) { i, p ->
                PageThumb(
                    i, p, i == c.index, c, onDialog, onOpened = onDone, reorder = reorder,
                    modifier = if (reorder.dragKey == p.id) Modifier else Modifier.animateItem(fadeInSpec = null, fadeOutSpec = null),
                )
            }
        }
    }
}

// ---------------- bagian bersama ----------------

/**
 * Bilah alat kecil yang melayang saat layar penuh: keluar dari layar penuh, urungkan/ulangi, dan semua
 * alat tulis. Ketuk alat yang sedang aktif untuk warna dan ketebalan. Bisa dikecilkan jadi satu tombol.
 */
@Composable
private fun FocusToolbar(c: EditorController, onDialog: (EditorDialog) -> Unit, modifier: Modifier) {
    var collapsed by rememberSaveable { mutableStateOf(false) }
    var options by remember { mutableStateOf(false) }
    Surface(modifier, shape = RoundedCornerShape(28.dp), tonalElevation = 4.dp, shadowElevation = 8.dp) {
        if (collapsed) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { collapsed = false }) {
                    ToolGlyph(c.tool, Color(c.colorOf(c.tool)), true, Modifier.size(28.dp))
                }
            }
        } else {
            Row(
                Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = { c.toggleFocus() }) { Icon(Icons.Filled.FullscreenExit, contentDescription = "Keluar dari layar penuh") }
                IconButton(onClick = { c.undo() }, enabled = c.canUndo) { Icon(Icons.AutoMirrored.Filled.Undo, contentDescription = "Urungkan") }
                IconButton(onClick = { c.redo() }, enabled = c.canRedo) { Icon(Icons.AutoMirrored.Filled.Redo, contentDescription = "Ulangi") }
                BarDivider()
                Box {
                    Row(verticalAlignment = Alignment.Bottom) {
                        Tool.tray.forEach { t ->
                            val selected = c.mode == InputMode.INK && t == c.tool
                            Box(
                                Modifier
                                    .size(width = 38.dp, height = 50.dp)
                                    .clip(RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp))
                                    .toolClick { if (selected) options = true else c.selectTool(t) }
                                    .semantics { contentDescription = t.label },
                                contentAlignment = Alignment.BottomCenter,
                            ) { ToolGlyph(t, Color(c.colorOf(t)), selected) }
                        }
                        Box(
                            Modifier
                                .size(width = 38.dp, height = 50.dp)
                                .toolClick { if (c.mode == InputMode.SHAPE) options = true else c.selectShapes() }
                                .semantics { contentDescription = "Bentuk" },
                            contentAlignment = Alignment.BottomCenter,
                        ) { ShapeGlyph(Color(c.colorOf(Tool.LINE)), c.mode == InputMode.SHAPE) }
                        Box(
                            Modifier
                                .size(width = 38.dp, height = 50.dp)
                                .toolClick { if (c.mode == InputMode.TAPE) options = true else c.selectTape() }
                                .semantics { contentDescription = "Selotip" },
                            contentAlignment = Alignment.BottomCenter,
                        ) { TapeGlyph(Color(c.colorOf(Tool.TAPE)), c.mode == InputMode.TAPE) }
                        Box(
                            Modifier
                                .size(width = 38.dp, height = 50.dp)
                                .toolClick { c.selectPointer() }
                                .semantics { contentDescription = "Pilih" },
                            contentAlignment = Alignment.BottomCenter,
                        ) { SelectGlyph(MaterialTheme.colorScheme.onSurface, c.mode == InputMode.SELECT) }
                        Box(
                            Modifier
                                .size(width = 38.dp, height = 50.dp)
                                .toolClick { c.selectTyping() }
                                .semantics { contentDescription = "Mode teks" },
                            contentAlignment = Alignment.BottomCenter,
                        ) { TypeGlyph(MaterialTheme.colorScheme.onSurface, c.mode == InputMode.TYPE) }
                    }
                    DropdownMenu(expanded = options, onDismissRequest = { options = false }) {
                        Column(Modifier.padding(horizontal = 12.dp, vertical = 4.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            if (c.mode == InputMode.SHAPE) {
                                Row(Modifier.horizontalScroll(rememberScrollState()), verticalAlignment = Alignment.CenterVertically) { ShapeChoices(c) }
                            }
                            if (c.mode == InputMode.TAPE) {
                                Row(Modifier.horizontalScroll(rememberScrollState()), verticalAlignment = Alignment.CenterVertically) {
                                    TapeShapeChoices(c, TapeShape.BANDS)
                                }
                                Row(Modifier.horizontalScroll(rememberScrollState()), verticalAlignment = Alignment.CenterVertically) { TapeActions(c) }
                            }
                            if (c.styleTool != Tool.ERASER) {
                                Row(Modifier.horizontalScroll(rememberScrollState()), verticalAlignment = Alignment.CenterVertically) {
                                    ColorChoices(c, dot = 26, onCustom = { options = false; onDialog(EditorDialog.CustomColor) })
                                }
                            } else {
                                Row(Modifier.horizontalScroll(rememberScrollState()), verticalAlignment = Alignment.CenterVertically) { EraserModeChoices(c) }
                            }
                            Row(verticalAlignment = Alignment.CenterVertically) { WidthChoices(c, withSlider = true) }
                        }
                    }
                }
                IconButton(onClick = { options = false; collapsed = true }) {
                    Icon(Icons.Filled.UnfoldLess, contentDescription = "Kecilkan bilah alat")
                }
            }
        }
    }
}

@Composable
private fun ToolTray(c: EditorController) {
    Row(verticalAlignment = Alignment.Bottom) {
        Tool.tray.forEach { t ->
            val selected = c.mode == InputMode.INK && t == c.tool
            Box(
                Modifier
                    .size(width = 42.dp, height = 56.dp)
                    .clip(RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp))
                    .toolClick { c.selectTool(t) }
                    .semantics { contentDescription = t.label },
                contentAlignment = Alignment.BottomCenter,
            ) {
                ToolGlyph(t, Color(c.colorOf(t)), selected)
            }
        }
        Box(
            Modifier
                .size(width = 42.dp, height = 56.dp)
                .clip(RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp))
                .toolClick { c.selectShapes() }
                .semantics { contentDescription = "Bentuk" },
            contentAlignment = Alignment.BottomCenter,
        ) {
            ShapeGlyph(Color(c.colorOf(Tool.LINE)), c.mode == InputMode.SHAPE)
        }
        Box(
            Modifier
                .size(width = 42.dp, height = 56.dp)
                .clip(RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp))
                .toolClick { c.selectTape() }
                .semantics { contentDescription = "Selotip" },
            contentAlignment = Alignment.BottomCenter,
        ) {
            TapeGlyph(Color(c.colorOf(Tool.TAPE)), c.mode == InputMode.TAPE)
        }
        // (Pilih punya tombol sendiri yang selalu ada di bilah.)
    }
}

/** Nama alat yang sedang dipakai, untuk tombol pilihan kuas. */
private fun currentToolLabel(c: EditorController): String = when (c.mode) {
    InputMode.SHAPE -> "Bentuk"
    InputMode.TAPE -> "Selotip"
    InputMode.PDF -> "PDF"
    // Di mode pilih, tombol kuas tetap menunjukkan alat tulis terakhir (Pilih punya tombol sendiri).
    else -> c.tool.label
}

/** Gambar kecil alat yang sedang dipakai, untuk tombol dan daftar pilihan kuas. */
@Composable
private fun SmallToolGlyph(c: EditorController, which: String) {
    Box(Modifier.size(width = 26.dp, height = 36.dp).clipToBounds(), contentAlignment = Alignment.BottomCenter) {
        val m = Modifier.size(width = 26.dp, height = 40.dp)
        when (which) {
            "Bentuk" -> ShapeGlyph(Color(c.colorOf(Tool.LINE)), true, m)
            "Selotip" -> TapeGlyph(Color(c.colorOf(Tool.TAPE)), true, m)
            "Pilih", "PDF" -> SelectGlyph(MaterialTheme.colorScheme.onSurface, true, m)
            else -> Tool.entries.firstOrNull { it.name == which }?.let { ToolGlyph(it, Color(c.colorOf(it)), true, m) }
        }
    }
}

/** Kunci gambar alat yang sedang dipakai (nama Tool, atau Bentuk/Selotip/Pilih). */
private fun currentGlyphKey(c: EditorController): String = when (c.mode) {
    InputMode.SHAPE -> "Bentuk"
    InputMode.TAPE -> "Selotip"
    InputMode.PDF -> "PDF"
    else -> c.tool.name
}

/** Tombol bilah ringkas: isi singkat, lalu panah; ketuk untuk membuka daftar tegak. */
@Composable
private fun TrayDropButton(description: String, onClick: () -> Unit, content: @Composable () -> Unit) {
    Row(
        Modifier
            .height(46.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f))
            .toolClick(onClickLabel = description, onClick = onClick)
            .semantics { contentDescription = description }
            .padding(start = 8.dp, end = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        content()
        Icon(Icons.Filled.ArrowDropDown, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** Satu baris pilihan di daftar tegak, disorot bila sedang dipakai. */
@Composable
private fun TrayMenuRow(label: String, selected: Boolean, onClick: () -> Unit, leading: @Composable () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 46.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(if (selected) scheme.primaryContainer else Color.Transparent)
            .clickable(onClick = onClick)
            .semantics { contentDescription = label; this.selected = selected }
            .padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.width(36.dp), contentAlignment = Alignment.Center) { leading() }
        Spacer(Modifier.width(10.dp))
        Text(
            label,
            style = MaterialTheme.typography.bodyLarge,
            color = if (selected) scheme.onPrimaryContainer else scheme.onSurface,
            modifier = Modifier.weight(1f),
        )
        if (selected) Icon(Icons.Filled.Check, contentDescription = null, tint = scheme.onPrimaryContainer, modifier = Modifier.size(18.dp))
    }
}

@Composable
private fun TraySection(title: String) {
    Text(
        title,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 12.dp, top = 10.dp, bottom = 4.dp),
    )
}

/**
 * Tombol Pilih sendiri di samping tombol kuas: sekali ketuk langsung memilih benda (ketuk atau laso), ketuk lagi
 * untuk kembali ke alat tulis sebelumnya.
 */
@Composable
private fun SelectQuickButton(c: EditorController) {
    val scheme = MaterialTheme.colorScheme
    val on = c.mode == InputMode.SELECT
    Row(
        Modifier
            .height(46.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(if (on) scheme.primaryContainer else scheme.surfaceVariant.copy(alpha = 0.7f))
            .border(1.dp, if (on) scheme.primary else Color.Transparent, RoundedCornerShape(12.dp))
            .toolClick(onClickLabel = if (on) "Kembali ke alat tulis" else "Pilih benda", onClick = { c.toggleSelect() })
            .semantics { contentDescription = "Pilih"; selected = on }
            .padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(width = 22.dp, height = 32.dp).clipToBounds(), contentAlignment = Alignment.BottomCenter) {
            SelectGlyph(if (on) scheme.onPrimaryContainer else scheme.onSurface, true, Modifier.size(width = 22.dp, height = 36.dp))
        }
        Spacer(Modifier.width(6.dp))
        Text("Pilih", style = MaterialTheme.typography.labelLarge, color = if (on) scheme.onPrimaryContainer else scheme.onSurface)
    }
}

/** Pilihan kuas: satu tombol berisi alat yang sedang dipakai; ketuk untuk daftar semua alat, tegak ke bawah. */
@Composable
private fun BrushPicker(c: EditorController) {
    var open by remember { mutableStateOf(false) }
    Box {
        TrayDropButton("Pilihan kuas: ${currentToolLabel(c)}", onClick = { open = true }) {
            SmallToolGlyph(c, currentGlyphKey(c))
            Spacer(Modifier.width(6.dp))
            Text(currentToolLabel(c), style = MaterialTheme.typography.labelLarge)
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            Column(Modifier.width(240.dp).padding(horizontal = 6.dp)) {
                TraySection("Alat tulis")
                Tool.tray.forEach { t ->
                    TrayMenuRow(t.label, c.mode == InputMode.INK && c.tool == t, onClick = { open = false; c.selectTool(t) }) {
                        SmallToolGlyph(c, t.name)
                    }
                }
                HorizontalDivider(Modifier.padding(vertical = 4.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                TrayMenuRow("Bentuk", c.mode == InputMode.SHAPE, onClick = { open = false; c.selectShapes() }) { SmallToolGlyph(c, "Bentuk") }
                TrayMenuRow("Selotip", c.mode == InputMode.TAPE, onClick = { open = false; c.selectTape() }) { SmallToolGlyph(c, "Selotip") }
            }
        }
    }
}

/**
 * Pengaturan alat yang sedang dipakai dalam satu tombol: warna dan ketebalan (atau cara menghapus, bentuk, tipe
 * selotip). Ketuk untuk membuka semua pilihannya, tegak ke bawah.
 */
@Composable
private fun ToolSettingsPicker(c: EditorController, onDialog: (EditorDialog) -> Unit) {
    var open by remember { mutableStateOf(false) }
    val t = c.styleTool
    val eraser = t == Tool.ERASER
    Box {
        TrayDropButton("Pengaturan ${currentToolLabel(c)}", onClick = { open = true }) {
            when {
                eraser -> {
                    Icon(Icons.Filled.AutoFixNormal, contentDescription = null, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(c.eraserMode.label, style = MaterialTheme.typography.labelLarge)
                }
                c.mode == InputMode.SHAPE -> {
                    ShapeKindIcon(c.shapeKind, Color(c.colorOf(t)))
                    Spacer(Modifier.width(6.dp))
                    Text(c.shapeKind.label, style = MaterialTheme.typography.labelLarge)
                }
                c.mode == InputMode.TAPE -> {
                    TapeShapeIcon(c.tapeShape, Color(c.colorOf(t)))
                    Spacer(Modifier.width(6.dp))
                    Text(c.tapeShape.label, style = MaterialTheme.typography.labelLarge)
                }
                else -> {
                    Box(Modifier.size(20.dp).clip(CircleShape).background(Color(c.colorOf(t))).border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f), CircleShape))
                }
            }
            if (!eraser || c.eraserMode != EraserMode.WHOLE) {
                Spacer(Modifier.width(8.dp))
                Text(mmLabel(c.widthOf(t)), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            Column(Modifier.width(300.dp).padding(horizontal = 6.dp)) {
                when {
                    eraser -> {
                        TraySection("Cara menghapus")
                        EraserMode.entries.forEach { m ->
                            TrayMenuRow(m.label, c.eraserMode == m, onClick = { c.chooseEraserMode(m) }) {
                                RadioButton(selected = c.eraserMode == m, onClick = null)
                            }
                        }
                        if (c.eraserMode != EraserMode.BRUSH) {
                            TrayMenuRow("Termasuk gambar", c.eraseImages, onClick = { c.toggleEraseImages() }) {
                                Checkbox(checked = c.eraseImages, onCheckedChange = null)
                            }
                        } else {
                            TraySection("Kekuatan kuas")
                            listOf("Tipis" to 0.18f, "Sedang" to 0.35f, "Kuat" to 0.65f).forEach { (label, v) ->
                                val on = kotlin.math.abs(c.eraserStrength - v) < 0.01f
                                TrayMenuRow(label, on, onClick = { c.chooseEraserStrength(v) }) {
                                    RadioButton(selected = on, onClick = null)
                                }
                            }
                        }
                    }
                    c.mode == InputMode.SHAPE -> {
                        TraySection("Bentuk")
                        ShapeKind.entries.forEach { k ->
                            TrayMenuRow(k.label, c.shapeKind == k, onClick = { c.chooseShape(k) }) {
                                ShapeKindIcon(k, if (c.shapeKind == k) Color(c.colorOf(t)) else MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                    c.mode == InputMode.TAPE -> {
                        TraySection("Tipe selotip")
                        TapeShape.BANDS.forEach { k ->
                            TrayMenuRow(k.label, c.tapeShape == k, onClick = { c.chooseTapeShape(k) }) {
                                TapeShapeIcon(k, if (c.tapeShape == k) Color(c.colorOf(t)) else MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                        TraySection("Semua selotip di halaman ini")
                        TrayMenuRow("Tutup semua", false, onClick = { open = false; c.setAllTapes(true) }) { Icon(Icons.Filled.VisibilityOff, null) }
                        TrayMenuRow("Buka semua", false, onClick = { open = false; c.setAllTapes(false) }) { Icon(Icons.Filled.Visibility, null) }
                        TrayMenuRow("Lepas semua", false, onClick = { open = false; c.removeAllTapes() }) { Icon(Icons.Filled.DeleteSweep, null) }
                    }
                }
                if (!eraser) {
                    TraySection("Warna")
                    val palette = when (t) {
                        Tool.HIGHLIGHTER -> HighlighterColors
                        Tool.WATERCOLOR -> WatercolorColors
                        Tool.TAPE -> TapeColors
                        else -> InkColors
                    }
                    val current = c.colorOf(t)
                    val dots = palette + (if (current !in palette) listOf(current) else emptyList())
                    dots.chunked(6).forEachIndexed { row, part ->
                        Row(Modifier.padding(horizontal = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                            part.forEach { col -> ColorDot(Color(col), col == current, { c.setColor(col) }, size = 26.dp) }
                            if (row == (dots.size - 1) / 6) CustomColorDot({ open = false; onDialog(EditorDialog.CustomColor) }, size = 26.dp)
                        }
                    }
                }
                if (!eraser || c.eraserMode != EraserMode.WHOLE) {
                    TraySection("Ketebalan · ${mmLabel(c.widthOf(t))}")
                    val ink = if (eraser) MaterialTheme.colorScheme.onSurfaceVariant else Color(c.colorOf(t))
                    t.presets.forEachIndexed { k, w ->
                        val on = abs(w - c.widthOf(t)) < 0.05f
                        TrayMenuRow(mmLabel(w), on, onClick = { c.setWidth(w) }) {
                            val d = (7 + k * 6).dp
                            Box(Modifier.size(d).clip(CircleShape).background(ink).border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.4f), CircleShape))
                        }
                    }
                    Slider(
                        value = c.widthOf(t),
                        onValueChange = { c.setWidth(it) },
                        valueRange = t.minWidth..t.maxWidth,
                        modifier = Modifier.padding(horizontal = 12.dp),
                    )
                    if (!eraser && c.mode != InputMode.TAPE) StrokePreview(c, Modifier.fillMaxWidth().height(40.dp).padding(horizontal = 12.dp))
                }
                Spacer(Modifier.height(6.dp))
            }
        }
    }
}

@Composable
private fun ShapeChoices(c: EditorController) {
    ShapeKind.entries.forEach { k ->
        val selected = k == c.shapeKind
        Box(
            Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(if (selected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent)
                .clickable { c.chooseShape(k) }
                .semantics { contentDescription = k.label },
            contentAlignment = Alignment.Center,
        ) {
            ShapeKindIcon(k, if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** Pilihan selotip hafalan sebagai ikon: [shapes] berisi tipe pita (Lurus, Bebas) atau bentuk bidang. */
@Composable
private fun TapeShapeChoices(c: EditorController, shapes: List<TapeShape>) {
    shapes.forEach { k ->
        val selected = k == c.tapeShape
        Box(
            Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(if (selected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent)
                .clickable { c.chooseTapeShape(k) }
                .semantics { contentDescription = "Selotip " + k.label },
            contentAlignment = Alignment.Center,
        ) {
            TapeShapeIcon(k, if (selected) Color(c.colorOf(Tool.TAPE)) else MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** Tipe selotip: pita Lurus atau Bebas (untuk bilah tablet). */
@Composable
private fun TapeTypePicker(c: EditorController) {
    TapeMenuButton(
        c = c,
        shown = c.tapeShape,
        label = c.tapeShape.label,
        active = true,
        description = "Tipe selotip",
        choices = TapeShape.BANDS,
    )
}

/** Tombol yang membuka daftar pilihan selotip; disorot bila pilihan di dalamnya sedang dipakai. */
@Composable
private fun TapeMenuButton(
    c: EditorController,
    shown: TapeShape,
    label: String,
    active: Boolean,
    description: String,
    choices: List<TapeShape>,
) {
    var open by remember { mutableStateOf(false) }
    val fg = if (active) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
    Box {
        Row(
            Modifier
                .height(40.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(if (active) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
                .clickable { open = true }
                .semantics { contentDescription = description }
                .padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TapeShapeIcon(shown, if (active) Color(c.colorOf(Tool.TAPE)) else MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.width(6.dp))
            Text(label, style = MaterialTheme.typography.labelLarge, color = fg)
            Icon(Icons.Filled.ArrowDropDown, contentDescription = null, tint = fg)
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            choices.forEach { k ->
                DropdownMenuItem(
                    text = { Text(k.label) },
                    leadingIcon = { TapeShapeIcon(k, if (k == c.tapeShape) Color(c.colorOf(Tool.TAPE)) else MaterialTheme.colorScheme.onSurfaceVariant) },
                    onClick = { open = false; c.chooseTapeShape(k) },
                )
            }
        }
    }
}

/** Tutup, buka, atau lepas semua selotip di halaman ini, sebagai tombol ikon (untuk bilah tablet). */
@Composable
private fun TapeActionIcons(c: EditorController) {
    IconButton(onClick = { c.setAllTapes(true) }) { Icon(Icons.Filled.VisibilityOff, contentDescription = "Tutup semua selotip") }
    IconButton(onClick = { c.setAllTapes(false) }) { Icon(Icons.Filled.Visibility, contentDescription = "Buka semua selotip") }
    IconButton(onClick = { c.removeAllTapes() }) { Icon(Icons.Filled.DeleteSweep, contentDescription = "Lepas semua selotip") }
}

/** Tutup, buka, atau lepas semua selotip di halaman ini. */
@Composable
private fun TapeActions(c: EditorController) {
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
        ToolChip(
            selected = false,
            onClick = { c.setAllTapes(true) },
            label = { Text("Tutup semua") },
            leadingIcon = { Icon(Icons.Filled.VisibilityOff, contentDescription = null, modifier = Modifier.size(18.dp)) },
        )
        ToolChip(
            selected = false,
            onClick = { c.setAllTapes(false) },
            label = { Text("Buka semua") },
            leadingIcon = { Icon(Icons.Filled.Visibility, contentDescription = null, modifier = Modifier.size(18.dp)) },
        )
        ToolChip(
            selected = false,
            onClick = { c.removeAllTapes() },
            label = { Text("Lepas semua") },
            leadingIcon = { Icon(Icons.Filled.DeleteSweep, contentDescription = null, modifier = Modifier.size(18.dp)) },
        )
    }
}

@Composable
private fun ColorChoices(c: EditorController, dot: Int, onCustom: () -> Unit) {
    val t = c.styleTool
    val palette = when (t) {
        Tool.HIGHLIGHTER -> HighlighterColors
        Tool.WATERCOLOR -> WatercolorColors
        Tool.TAPE -> TapeColors
        else -> InkColors
    }
    val current = c.colorOf(t)
    palette.forEach { col -> ColorDot(Color(col), col == current, { c.setColor(col) }, size = dot.dp) }
    if (current !in palette) ColorDot(Color(current), true, {}, size = dot.dp, description = "Warna sendiri")
    CustomColorDot(onCustom, size = dot.dp)
}

/** Penghapus sebagian atau goresan utuh. */
@Composable
private fun EraserModeChoices(c: EditorController) {
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        EraserMode.entries.forEach { m ->
            ToolChip(selected = c.eraserMode == m, onClick = { c.chooseEraserMode(m) }, label = { Text(m.label) })
        }
        if (c.eraserMode != EraserMode.BRUSH) {
            Spacer(Modifier.width(6.dp))
            ToolChip(
                selected = c.eraseImages,
                onClick = { c.toggleEraseImages() },
                label = { Text("Termasuk gambar") },
                leadingIcon = { Icon(Icons.Filled.Image, contentDescription = null, modifier = Modifier.size(18.dp)) },
            )
        }
        if (c.eraserMode == EraserMode.BRUSH) {
            Spacer(Modifier.width(6.dp))
            // Kekuatan kuas: tipis memudarkan pelan-pelan, kuat langsung bersih.
            listOf("Tipis" to 0.18f, "Sedang" to 0.35f, "Kuat" to 0.65f).forEach { (label, v) ->
                ToolChip(
                    selected = kotlin.math.abs(c.eraserStrength - v) < 0.01f,
                    onClick = { c.chooseEraserStrength(v) },
                    label = { Text(label) },
                )
            }
        }
    }
}

@Composable
private fun WidthChoices(c: EditorController, withSlider: Boolean) {
    val t = c.styleTool
    val current = c.widthOf(t)
    val ink = if (t == Tool.ERASER) MaterialTheme.colorScheme.onSurfaceVariant else Color(c.colorOf(t))
    t.presets.forEachIndexed { k, w ->
        val selected = abs(w - current) < 0.05f
        Box(
            Modifier
                .size(40.dp)
                .clip(CircleShape)
                .clickable { c.setWidth(w) }
                .semantics { contentDescription = "Ketebalan ${k + 1}" },
            contentAlignment = Alignment.Center,
        ) {
            if (selected) Box(Modifier.size(34.dp).border(2.dp, MaterialTheme.colorScheme.onSurface, CircleShape))
            val d = (7 + k * 6).dp
            Box(Modifier.size(d).clip(CircleShape).background(ink).border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.4f), CircleShape))
        }
    }
    if (withSlider) {
        var open by remember { mutableStateOf(false) }
        Box {
            TextButton(onClick = { open = true }) { Text(mmLabel(current)) }
            DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
                Column(Modifier.width(280.dp).padding(horizontal = 16.dp, vertical = 8.dp)) {
                    Text("Ketebalan · ${mmLabel(current)}", style = MaterialTheme.typography.labelLarge)
                    Slider(
                        value = current,
                        onValueChange = { c.setWidth(it) },
                        valueRange = t.minWidth..t.maxWidth,
                    )
                    StrokePreview(c, Modifier.fillMaxWidth().height(40.dp))
                }
            }
        }
    }
}

/** Contoh garis dengan alat, warna, dan ketebalan yang sedang dipilih. */
@Composable
private fun StrokePreview(c: EditorController, modifier: Modifier) {
    val tool = c.styleTool
    val color = if (tool == Tool.ERASER) MaterialTheme.colorScheme.outline else Color(c.colorOf(tool))
    val w = c.widthOf(tool)
    Canvas(modifier) {
        val px = (w * 1.6f).dp.toPx().coerceAtMost(size.height * 0.9f)
        val alpha = when (tool) { Tool.HIGHLIGHTER -> 0.38f; Tool.WATERCOLOR -> 0.4f; Tool.PENCIL -> 0.75f; else -> 1f }
        val y = size.height / 2f
        val steps = 24
        var prev = Offset(size.width * 0.06f, y)
        for (s in 1..steps) {
            val t = s / steps.toFloat()
            val x = size.width * (0.06f + 0.88f * t)
            val yy = y + kotlin.math.sin(t * 6.28f) * size.height * 0.18f
            val next = Offset(x, yy)
            drawLine(
                color.copy(alpha = alpha),
                prev, next,
                strokeWidth = px,
                cap = if (tool == Tool.HIGHLIGHTER) StrokeCap.Square else StrokeCap.Round,
            )
            prev = next
        }
    }
}

@Composable
private fun PagePill(c: EditorController, modifier: Modifier, onPages: (() -> Unit)?) {
    Surface(modifier, shape = RoundedCornerShape(50), tonalElevation = 3.dp, shadowElevation = 4.dp) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { c.turnPage(-1) }, enabled = c.index > 0) {
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = "Halaman sebelumnya")
            }
            Text(
                "${c.index + 1} / ${c.pages.size}",
                style = MaterialTheme.typography.labelLarge,
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .then(if (onPages != null) Modifier.clickable(onClickLabel = "Daftar halaman", onClick = onPages) else Modifier)
                    .padding(horizontal = 6.dp, vertical = 6.dp),
            )
            IconButton(onClick = { c.turnPage(1) }, enabled = c.index < c.pages.lastIndex) {
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = "Halaman berikutnya")
            }
            if (!c.readMode) {
                IconButton(onClick = { c.addPage() }) {
                    Icon(Icons.Filled.Add, contentDescription = "Tambah halaman")
                }
            }
        }
    }
}

/** Tombol Mode baca di bilah atas: buku terbuka, menyala saat aktif. */
@Composable
private fun ReadModeButton(c: EditorController) {
    IconButton(onClick = { c.toggleReadMode() }) {
        Icon(
            Icons.AutoMirrored.Filled.MenuBook,
            contentDescription = if (c.readMode) "Keluar dari mode baca" else "Mode baca (hanya membaca)",
            tint = if (c.readMode) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** Tanda Mode baca di atas halaman, dengan tombol untuk kembali menyunting (dan keluar layar penuh). */
@Composable
private fun ReadModeChip(c: EditorController, modifier: Modifier) {
    Surface(modifier, shape = RoundedCornerShape(50), tonalElevation = 4.dp, shadowElevation = 6.dp) {
        Row(Modifier.padding(start = 14.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.AutoMirrored.Filled.MenuBook, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text("Mode baca", style = MaterialTheme.typography.labelLarge)
            if (c.focusMode) {
                IconButton(onClick = { c.toggleFocus() }) { Icon(Icons.Filled.FullscreenExit, contentDescription = "Keluar dari layar penuh") }
            } else {
                Spacer(Modifier.width(6.dp))
            }
            Text("· hal. ${c.index + 1}/${c.pages.size}", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            IconButton(onClick = { c.markReadHere() }) { Icon(Icons.Filled.BookmarkAdd, contentDescription = "Tandai bacaan terakhir di sini") }
            val ctx = LocalContext.current
            if (!c.presenting) {
                IconButton(onClick = { c.startPresentation(ctx) }) { Icon(Icons.Filled.Slideshow, contentDescription = "Presentasi") }
            }
            TextButton(onClick = { c.applyReadMode(false) }) {
                Icon(Icons.Filled.Edit, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
                Text("Sunting")
            }
        }
    }
}

/** Kendali presentasi di tablet: pindah halaman, laser, dan selesai. */
@Composable
private fun PresentationChip(c: EditorController, modifier: Modifier) {
    Surface(modifier, shape = RoundedCornerShape(50), tonalElevation = 4.dp, shadowElevation = 8.dp) {
        Row(
            Modifier.horizontalScroll(rememberScrollState()).padding(start = 14.dp, end = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Filled.Slideshow, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text(
                if (c.presentScreen.isNotEmpty()) "Presentasi di ${c.presentScreen}" else "Presentasi di tablet",
                style = MaterialTheme.typography.labelLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.widthIn(max = 220.dp),
            )
            Spacer(Modifier.width(4.dp))
            IconButton(onClick = { c.showPage(c.index - 1) }, enabled = c.index > 0) {
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = "Halaman sebelumnya")
            }
            Text("${c.index + 1}/${c.pages.size}", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            IconButton(onClick = { c.showPage(c.index + 1) }, enabled = c.index < c.pages.lastIndex) {
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = "Halaman berikutnya")
            }
            FilterChip(
                selected = c.laserOn,
                onClick = { c.toggleLaser() },
                label = { Text("Laser") },
                leadingIcon = { Icon(Icons.Filled.Adjust, contentDescription = null, modifier = Modifier.size(16.dp)) },
            )
            Spacer(Modifier.width(4.dp))
            TextButton(onClick = { c.stopPresentation() }) { Text("Selesai") }
        }
    }
}

/** Bilah aksi untuk benda yang sedang dipilih. */
@Composable
private fun SelectionBar(c: EditorController, onDialog: (EditorDialog) -> Unit, insert: InsertActions, modifier: Modifier) {
    val context = LocalContext.current
    val sel = c.selection
    val clip by InkClipboard.clip.collectAsState()
    if (sel == null) {
        if (c.convertPick) {
            Surface(modifier.padding(horizontal = 8.dp), shape = RoundedCornerShape(50), tonalElevation = 4.dp, shadowElevation = 6.dp) {
                Row(
                    Modifier.horizontalScroll(rememberScrollState()).padding(start = 16.dp, end = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Filled.Transform, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Lingkari tulisan yang ingin diubah jadi teks", style = MaterialTheme.typography.labelLarge)
                    Spacer(Modifier.width(6.dp))
                    TextButton(onClick = { c.convertWholePage() }) { Text("Semua di halaman") }
                    TextButton(onClick = { c.cancelConvertPick() }) { Text("Batal") }
                }
            }
        } else if (c.autoText) {
            AutoTextChip(c, modifier)
        }
        return
    }
    Surface(modifier.padding(horizontal = 8.dp), shape = RoundedCornerShape(50), tonalElevation = 4.dp, shadowElevation = 6.dp) {
        Row(
            Modifier.horizontalScroll(rememberScrollState()).padding(start = 16.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(sel.kind, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(6.dp))
            if (sel.url != null) {
                TextButton(onClick = { c.followLink(sel.url) }) { Text("Buka") }
                TextButton(onClick = { onDialog(EditorDialog.EditLink) }) { Text("Ubah") }
            }
            if (sel.text != null) {
                TextButton(onClick = { c.editSelectedText() }) { Text("Ubah") }
            }
            if (sel.sticky) {
                TextButton(onClick = { c.editStickyInline() }) { Text("Isi teks") }
            }
            sel.textSize?.let { sz -> SizeField(sz) { c.setSelectedTextSize(it) } }
            if (sel.align != null) {
                AlignButtons(sel.align, onPick = { c.alignSelection(it) })
            }
            if (sel.list != null) {
                ListButtons(sel.list, onPick = { c.toggleList(it) })
            }
            if (sel.table) {
                TextButton(onClick = { onDialog(EditorDialog.TableLayout) }) { Text("Atur tabel") }
                TextButton(onClick = { c.openTableLook() }) { Text("Garis & warna") }
            }
            if (sel.recolor) {
                Box {
                    var colorOpen by remember { mutableStateOf(false) }
                    TextButton(onClick = { colorOpen = true }) { Text("Warna") }
                    DropdownMenu(expanded = colorOpen, onDismissRequest = { colorOpen = false }) {
                        Row(Modifier.padding(horizontal = 8.dp)) {
                            InkColors.forEach { col ->
                                ColorDot(Color(col), false, { colorOpen = false; c.recolorSelection(col) }, size = 26.dp)
                            }
                        }
                    }
                }
            }
            if (sel.ink) {
                TextButton(onClick = { c.startConvert() }) { Text("Jadi teks") }
            }
            if (sel.mindMap) {
                TextButton(onClick = { c.editSelectedMindMap() }) { Text("Ubah mind map") }
            }
            if (sel.audio) {
                val playing = c.selectedAudio()?.file == c.playingFile && c.playingFile != null
                TextButton(onClick = { c.playSelectedAudio() }) { Text(if (playing) "Hentikan" else "Putar") }
                TextButton(onClick = insert.transcribe) { Text("Ubah jadi teks") }
            }
            if (sel.rotatable) {
                TextButton(onClick = { c.rotateSelection() }) { Text(if (sel.multi) "Putar 90°" else "Putar") }
            }
            if (sel.image) {
                Box {
                    var flipOpen by remember { mutableStateOf(false) }
                    TextButton(onClick = { flipOpen = true }) { Text("Balik") }
                    DropdownMenu(expanded = flipOpen, onDismissRequest = { flipOpen = false }) {
                        DropdownMenuItem(
                            text = { Text("Kiri ↔ kanan") },
                            onClick = { flipOpen = false; c.flipSelection(horizontal = true) },
                        )
                        DropdownMenuItem(
                            text = { Text("Atas ↕ bawah") },
                            onClick = { flipOpen = false; c.flipSelection(horizontal = false) },
                        )
                    }
                }
                TextButton(onClick = { onDialog(EditorDialog.CropImage) }) { Text("Potong") }
                TextButton(onClick = { onDialog(EditorDialog.ImageShadow) }) { Text("Bayangan") }
                TextButton(onClick = { c.startRemoveBackground(context) }) { Text("Hapus latar") }
                TextButton(onClick = { c.saveImageAsSticker() }) { Text("Jadikan stiker") }
            }
            if (sel.multi && !sel.grouped) {
                TextButton(onClick = { c.groupSelection() }) { Text("Kelompokkan") }
            }
            if (sel.grouped) {
                TextButton(onClick = { c.ungroupSelection() }) { Text("Pisahkan") }
            }
            if (sel.arrangeable) {
                Box {
                    var orderOpen by remember { mutableStateOf(false) }
                    TextButton(onClick = { orderOpen = true }) { Text("Urutan") }
                    DropdownMenu(expanded = orderOpen, onDismissRequest = { orderOpen = false }) {
                        DropdownMenuItem(
                            text = { Text("Paling depan") },
                            leadingIcon = { Icon(Icons.Filled.FlipToFront, null) },
                            onClick = { orderOpen = false; c.arrange(InkView.Arrange.FRONT) },
                        )
                        DropdownMenuItem(
                            text = { Text("Maju") },
                            leadingIcon = { Icon(Icons.Filled.KeyboardArrowUp, null) },
                            onClick = { orderOpen = false; c.arrange(InkView.Arrange.FORWARD) },
                        )
                        DropdownMenuItem(
                            text = { Text("Mundur") },
                            leadingIcon = { Icon(Icons.Filled.KeyboardArrowDown, null) },
                            onClick = { orderOpen = false; c.arrange(InkView.Arrange.BACKWARD) },
                        )
                        DropdownMenuItem(
                            text = { Text("Paling belakang") },
                            leadingIcon = { Icon(Icons.Filled.FlipToBack, null) },
                            onClick = { orderOpen = false; c.arrange(InkView.Arrange.BACK) },
                        )
                    }
                }
            }
            TextButton(onClick = { c.duplicateSelection() }) { Text("Duplikat") }
            TextButton(onClick = { c.deleteSelection() }) { Text("Hapus", color = MaterialTheme.colorScheme.error) }
            IconButton(onClick = { c.finishSelection() }) { Icon(Icons.Filled.Check, contentDescription = "Selesai") }
        }
    }
}

@Composable
private fun InsertMenu(insert: InsertActions, labeled: Boolean) {
    var open by remember { mutableStateOf(false) }
    Box {
        if (labeled) {
            TextButton(onClick = { open = true }) {
                Icon(Icons.Filled.AddCircle, contentDescription = null)
                Spacer(Modifier.width(6.dp))
                Text("Sisipkan")
            }
        } else {
            IconButton(onClick = { open = true }) { Icon(Icons.Filled.AddCircle, contentDescription = "Sisipkan") }
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            DropdownMenuItem(
                text = { Text("Teks") },
                leadingIcon = { Icon(Icons.Filled.TextFields, null) },
                onClick = { open = false; insert.text() },
            )
            DropdownMenuItem(
                text = { Text("Gambar") },
                leadingIcon = { Icon(Icons.Filled.Image, null) },
                onClick = { open = false; insert.image() },
            )
            DropdownMenuItem(
                text = { Text("Link website") },
                leadingIcon = { Icon(Icons.Filled.Link, null) },
                onClick = { open = false; insert.link() },
            )
            DropdownMenuItem(
                text = { Text("Stiker") },
                leadingIcon = { Box(Modifier.size(20.dp).clip(RoundedCornerShape(3.dp)).background(Color(StickyColors[0]))) },
                onClick = { open = false; insert.sticker() },
            )
            DropdownMenuItem(
                text = { Text("Emoji") },
                leadingIcon = { Icon(Icons.Filled.Mood, null) },
                onClick = { open = false; insert.emoji() },
            )
            DropdownMenuItem(
                text = { Text("Tanggal & kalender") },
                leadingIcon = { Icon(Icons.Filled.CalendarMonth, null) },
                onClick = { open = false; insert.date() },
            )
            DropdownMenuItem(
                text = { Text("Mind map") },
                leadingIcon = { Icon(Icons.Filled.AccountTree, null) },
                onClick = { open = false; insert.mindMap() },
            )
            DropdownMenuItem(
                text = { Text("Pindai dokumen") },
                leadingIcon = { Icon(Icons.Filled.DocumentScanner, null) },
                onClick = { open = false; insert.scan() },
            )
            DropdownMenuItem(
                text = { Text("Tabel") },
                leadingIcon = { Icon(Icons.Filled.GridOn, null) },
                onClick = { open = false; insert.table() },
            )
        }
    }
}

@Composable
private fun MoreMenu(c: EditorController, onDialog: (EditorDialog) -> Unit, includePaper: Boolean, includeRuler: Boolean) {
    var open by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { open = true }) { Icon(Icons.Filled.MoreVert, contentDescription = "Pilihan lain") }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            if (includePaper) {
                DropdownMenuItem(
                    text = { Text("Pengaturan kertas") },
                    leadingIcon = { Icon(Icons.Filled.Tune, null) },
                    onClick = { open = false; onDialog(EditorDialog.Paper) },
                )
            }
            if (includeRuler) {
                DropdownMenuItem(
                    text = { Text("Teks otomatis") },
                    leadingIcon = { Icon(Icons.Filled.TextFields, null) },
                    trailingIcon = { Switch(checked = c.autoText, onCheckedChange = null) },
                    onClick = { open = false; c.toggleAutoText() },
                )
                DropdownMenuItem(
                    text = { Text("Penggaris") },
                    leadingIcon = { Icon(Icons.Filled.Straighten, null) },
                    trailingIcon = { Switch(checked = c.ruler, onCheckedChange = null) },
                    onClick = { open = false; c.toggleRuler() },
                )
            }
            DropdownMenuItem(
                text = { Text("Ekspor (PNG, JPG, Word)") },
                leadingIcon = { Icon(Icons.Filled.Share, null) },
                onClick = { open = false; onDialog(EditorDialog.Export) },
            )
            DropdownMenuItem(
                text = { Text("Kartu hafalan") },
                leadingIcon = { Icon(Icons.Filled.Style, null) },
                onClick = { open = false; onDialog(EditorDialog.Flashcards) },
            )
            val ctx = LocalContext.current
            DropdownMenuItem(
                text = { Text(if (c.presenting) "Selesai presentasi" else "Presentasi") },
                leadingIcon = { Icon(Icons.Filled.Slideshow, null) },
                onClick = { open = false; if (c.presenting) c.stopPresentation() else c.startPresentation(ctx) },
            )
            DropdownMenuItem(
                text = { Text("Laser") },
                leadingIcon = { Icon(Icons.Filled.Adjust, null) },
                trailingIcon = { Switch(checked = c.laserOn, onCheckedChange = null) },
                onClick = { open = false; c.toggleLaser() },
            )
            DropdownMenuItem(
                text = {
                    Column {
                        Text("Bahasa tulisan")
                        Text(c.inkLanguage.label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                },
                leadingIcon = { Icon(Icons.Filled.Translate, null) },
                onClick = { open = false; onDialog(EditorDialog.InkLang) },
            )
            HorizontalDivider()
            DropdownMenuItem(
                text = { Text("Tambah halaman") },
                leadingIcon = { Icon(Icons.Filled.Add, null) },
                onClick = { open = false; c.addPage() },
            )
            DropdownMenuItem(
                text = { Text("Duplikat halaman") },
                leadingIcon = { Icon(Icons.Filled.ContentCopy, null) },
                onClick = { open = false; c.duplicatePage() },
            )
            DropdownMenuItem(
                text = { Text("Hapus halaman") },
                leadingIcon = { Icon(Icons.Filled.Delete, null) },
                onClick = { open = false; onDialog(EditorDialog.DeletePage(c.index)) },
            )
            DropdownMenuItem(
                text = { Text("Bersihkan halaman") },
                leadingIcon = { Icon(Icons.Filled.DeleteSweep, null) },
                onClick = { open = false; onDialog(EditorDialog.ClearPage) },
            )
            HorizontalDivider()
            DropdownMenuItem(
                text = { Text("Jari untuk menulis") },
                leadingIcon = { Icon(Icons.Filled.TouchApp, null) },
                trailingIcon = { Switch(checked = c.fingerDraws, onCheckedChange = null) },
                onClick = { c.toggleFinger() },
            )
        }
    }
}

@Composable
private fun PaperDialog(c: EditorController, onDismiss: () -> Unit) {
    var paper by remember { mutableStateOf(PaperChoice.fromPage(c.page.width, c.page.height)) }
    var template by remember { mutableStateOf(c.page.template) }
    var color by remember { mutableStateOf(c.page.paperColor) }
    var background by remember { mutableStateOf(c.page.background) }
    var toAll by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val pickBackground = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) c.importBackground(context, uri) { background = PageBackground.image(it) }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Pengaturan kertas") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                PaperOptions(
                    paper = paper, template = template, color = color,
                    onPaper = { paper = it }, onTemplate = { template = it },
                    // Memilih warna berarti kertas polos berwarna itu.
                    onColor = { color = it; background = PageBackground.PLAIN },
                )
                Text("Latar", style = MaterialTheme.typography.labelLarge)
                BackgroundPicker(
                    current = background,
                    plainColor = color,
                    imageDir = c.noteImageDir(),
                    pdfPage = c.page.background.takeIf { it.startsWith("pdf:") },
                    onPdf = { background = c.page.background },
                    onPlain = { background = PageBackground.PLAIN },
                    onTexture = { t -> background = PageBackground.texture(t); color = t.base },
                    onImage = { pickBackground.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                )
                Text("Terapkan ke", style = MaterialTheme.typography.labelLarge)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ToolChip(selected = !toAll, onClick = { toAll = false }, label = { Text("Halaman ini") })
                    ToolChip(selected = toAll, onClick = { toAll = true }, label = { Text("Semua halaman") })
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val w = paper.widthPt()
                    val h = paper.heightPt()
                    if (w != null && h != null) c.applyPaper(w, h, template, color, background, toAll)
                    onDismiss()
                },
                enabled = paper.valid,
            ) { Text("Terapkan") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Batal") } },
    )
}

/** Pilihan latar: polos, tekstur kertas, atau gambar dari perangkat. */
@Composable
private fun BackgroundPicker(
    current: String,
    plainColor: Int,
    imageDir: File,
    pdfPage: String?,
    onPdf: () -> Unit,
    onPlain: () -> Unit,
    onTexture: (PaperTexture) -> Unit,
    onImage: () -> Unit,
) {
    val selTexture = PageBackground.textureOf(current)
    val selImage = PageBackground.imageOf(current)
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        if (pdfPage != null) {
            BackgroundSwatch("Halaman PDF", current == pdfPage, onPdf) {
                Box(Modifier.fillMaxSize().background(Color.White), contentAlignment = Alignment.Center) {
                    Icon(Icons.Filled.PictureAsPdf, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                }
            }
        }
        BackgroundSwatch("Polos", current.isEmpty(), onPlain) {
            Box(Modifier.fillMaxSize().background(Color(plainColor)))
        }
        PaperTexture.entries.forEach { t ->
            BackgroundSwatch(t.label, t == selTexture, { onTexture(t) }) {
                val tile by produceState<ImageBitmap?>(null, t) {
                    value = withContext(Dispatchers.Default) { PaperBackgrounds.tile(t).asImageBitmap() }
                }
                Box(Modifier.fillMaxSize().background(Color(t.base))) {
                    tile?.let { Image(it, contentDescription = null, contentScale = ContentScale.None, modifier = Modifier.fillMaxSize()) }
                }
            }
        }
        BackgroundSwatch(if (selImage != null) "Ganti gambar" else "Dari galeri", selImage != null, onImage) {
            val thumb by produceState<ImageBitmap?>(null, selImage) {
                value = selImage?.let { f ->
                    withContext(Dispatchers.IO) { ImageCache.get(File(imageDir, f), 256)?.asImageBitmap() }
                }
            }
            val shown = thumb
            if (shown != null) {
                Image(shown, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            } else {
                Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surfaceVariant), contentAlignment = Alignment.Center) {
                    Icon(Icons.Filled.AddPhotoAlternate, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                }
            }
        }
    }
}

@Composable
private fun BackgroundSwatch(label: String, selected: Boolean, onClick: () -> Unit, content: @Composable () -> Unit) {
    Column(
        Modifier.clip(RoundedCornerShape(8.dp)).clickable(onClickLabel = "Pakai latar $label", onClick = onClick).padding(4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier
                .width(52.dp)
                .aspectRatio(0.72f)
                .clip(RoundedCornerShape(4.dp))
                .border(
                    if (selected) 2.dp else 1.dp,
                    if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                    RoundedCornerShape(4.dp),
                ),
        ) { content() }
        Spacer(Modifier.height(4.dp))
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
        )
    }
}

@Composable
private fun TableDialog(onDismiss: () -> Unit, onConfirm: (Int, Int) -> Unit) {
    var rows by remember { mutableIntStateOf(4) }
    var cols by remember { mutableIntStateOf(3) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Sisipkan tabel") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Stepper("Baris", rows, 1..20) { rows = it }
                Stepper("Kolom", cols, 1..8) { cols = it }
                val line = MaterialTheme.colorScheme.onSurfaceVariant
                val head = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                Canvas(Modifier.fillMaxWidth().height(110.dp)) {
                    val w = size.width
                    val h = size.height
                    val rh = h / rows
                    val cw = w / cols
                    drawRect(head, Offset.Zero, Size(w, rh))
                    for (k in 0..rows) drawLine(line, Offset(0f, rh * k), Offset(w, rh * k), strokeWidth = 1.5f)
                    for (k in 0..cols) drawLine(line, Offset(cw * k, 0f), Offset(cw * k, h), strokeWidth = 1.5f)
                }
                Text(
                    "Tulis isi tabel dengan pena. Tulisan di dalam tabel ikut pindah saat tabel digeser.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = { TextButton(onClick = { onConfirm(rows, cols) }) { Text("Sisipkan") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Batal") } },
    )
}

@Composable
private fun Stepper(label: String, value: Int, range: IntRange, onChange: (Int) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        IconButton(onClick = { onChange((value - 1).coerceIn(range)) }, enabled = value > range.first) {
            Icon(Icons.Filled.Remove, contentDescription = "Kurangi $label")
        }
        Text("$value", style = MaterialTheme.typography.titleMedium, modifier = Modifier.width(32.dp), textAlign = TextAlign.Center)
        IconButton(onClick = { onChange((value + 1).coerceIn(range)) }, enabled = value < range.last) {
            Icon(Icons.Filled.Add, contentDescription = "Tambah $label")
        }
    }
}

/** Menu stiker: kertas tempel berwarna dan gambar-gambar yang sudah ditambahkan sendiri. */
@Composable
private fun StickerDialog(
    stickers: List<File>,
    onDismiss: () -> Unit,
    onPickColor: (Int) -> Unit,
    onPickImage: (File) -> Unit,
    onAddImages: () -> Unit,
    onDelete: (File) -> Unit,
) {
    var toDelete by remember { mutableStateOf<File?>(null) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Stiker") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Kertas tempel", style = MaterialTheme.typography.labelLarge)
                Text(
                    "Bisa ditulisi pena seperti biasa.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    StickyColors.forEach { col ->
                        Box(
                            Modifier
                                .size(40.dp)
                                .shadow(2.dp, RoundedCornerShape(4.dp))
                                .clip(RoundedCornerShape(4.dp))
                                .background(Color(col))
                                .clickable { onPickColor(col) }
                                .semantics { contentDescription = "Kertas tempel" }
                        )
                    }
                }
                HorizontalDivider()
                Text("Stiker gambar", style = MaterialTheme.typography.labelLarge)
                Text(
                    if (stickers.isEmpty()) "Tambahkan gambar dari galeri sekali, lalu tinggal ketuk untuk menempelkannya kapan saja. Gambar PNG tembus pandang paling cocok."
                    else "Ketuk untuk menempel. Tekan lama untuk menghapus dari menu.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                val rows = (stickers.size + 1 + 3) / 4
                for (r in 0 until rows) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        for (k in 0 until 4) {
                            val i = r * 4 + k
                            Box(Modifier.weight(1f).aspectRatio(1f)) {
                                when {
                                    i == 0 -> AddStickerTile(onAddImages)
                                    i - 1 < stickers.size -> {
                                        val f = stickers[i - 1]
                                        StickerTile(f, onClick = { onPickImage(f) }, onLongClick = { toDelete = f })
                                    }
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Tutup") } },
    )
    toDelete?.let { f ->
        AlertDialog(
            onDismissRequest = { toDelete = null },
            title = { Text("Hapus stiker ini dari menu?") },
            text = { Text("Stiker yang sudah ditempel di catatan tidak ikut terhapus.") },
            confirmButton = { TextButton(onClick = { onDelete(f); toDelete = null }) { Text("Hapus", color = MaterialTheme.colorScheme.error) } },
            dismissButton = { TextButton(onClick = { toDelete = null }) { Text("Batal") } },
        )
    }
}

@Composable
private fun AddStickerTile(onClick: () -> Unit) {
    Box(
        Modifier
            .fillMaxSize()
            .clip(RoundedCornerShape(10.dp))
            .border(1.5.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(10.dp))
            .clickable(onClickLabel = "Tambah gambar stiker", onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(Icons.Filled.Add, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Text("Tambah", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
        }
    }
}

@Composable
private fun StickerTile(f: File, onClick: () -> Unit, onLongClick: () -> Unit) {
    val bmp by produceState<ImageBitmap?>(initialValue = null, f) {
        value = withContext(Dispatchers.IO) { runCatching { ImageCache.get(f, 256)?.asImageBitmap() }.getOrNull() }
    }
    Box(
        Modifier
            .fillMaxSize()
            .clip(RoundedCornerShape(10.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
            .combinedClickable(onClickLabel = "Tempel stiker", onClick = onClick, onLongClick = onLongClick)
            .padding(6.dp),
        contentAlignment = Alignment.Center,
    ) {
        val b = bmp
        if (b != null) Image(b, contentDescription = "Stiker", contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize())
        else CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
    }
}

/** Sisipkan tanggal (beberapa gaya tulisan) atau kalender satu bulan yang bisa ditulisi. */
@Composable
private fun DateDialog(onDismiss: () -> Unit, onDate: (String) -> Unit, onCalendar: (Int, Int, Int) -> Unit) {
    val today = remember { LocalDate.now() }
    var picked by remember { mutableStateOf(today) }
    var shown by remember { mutableStateOf(YearMonth.from(today)) }
    var asCalendar by rememberSaveable { mutableStateOf(false) }
    var style by rememberSaveable { mutableIntStateOf(1) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Tanggal & kalender") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                MiniCalendar(shown, picked, today, onMonth = { shown = it }, onPick = { picked = it; shown = YearMonth.from(it) })
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ToolChip(selected = !asCalendar, onClick = { asCalendar = false }, label = { Text("Tanggal") })
                    ToolChip(selected = asCalendar, onClick = { asCalendar = true }, label = { Text("Kalender bulan") })
                }
                if (!asCalendar) {
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        for (k in 0..3) {
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .clickable { style = k }
                                    .padding(vertical = 2.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                RadioButton(selected = style == k, onClick = { style = k })
                                Text(formatDate(picked, k), style = MaterialTheme.typography.bodyLarge)
                            }
                        }
                    }
                } else {
                    Text(
                        "Kalender ${IndoDate.months[shown.monthValue - 1]} ${shown.year} ditempel di halaman. " +
                            "Tanggal yang dipilih diberi lingkaran, dan tiap kotak tanggal bisa ditulisi.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                if (asCalendar) {
                    val mark = if (YearMonth.from(picked) == shown) picked.dayOfMonth else 0
                    onCalendar(shown.year, shown.monthValue, mark)
                } else {
                    onDate(formatDate(picked, style))
                }
            }) { Text("Sisipkan") }
        },
        dismissButton = {
            Row {
                if (!asCalendar) TextButton(onClick = { onDate(formatDate(today, style)) }) { Text("Hari ini") }
                TextButton(onClick = onDismiss) { Text("Batal") }
            }
        },
    )
}

private fun formatDate(d: LocalDate, style: Int): String {
    val month = IndoDate.months[d.monthValue - 1]
    return when (style) {
        0 -> "${d.dayOfMonth} $month ${d.year}"
        1 -> "${IndoDate.days[d.dayOfWeek.value - 1]}, ${d.dayOfMonth} $month ${d.year}"
        2 -> String.format(Locale.ROOT, "%02d/%02d/%04d", d.dayOfMonth, d.monthValue, d.year)
        else -> "${d.dayOfMonth} ${IndoDate.monthsShort[d.monthValue - 1]} ${d.year}"
    }
}

/** Kalender kecil untuk memilih tanggal: Senin di kiri, Minggu merah, hari ini dilingkari. */
@Composable
private fun MiniCalendar(month: YearMonth, picked: LocalDate, today: LocalDate, onMonth: (YearMonth) -> Unit, onPick: (LocalDate) -> Unit) {
    val red = Color(0xFFC62828)
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { onMonth(month.minusMonths(1)) }) {
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = "Bulan sebelumnya")
            }
            Text(
                "${IndoDate.months[month.monthValue - 1]} ${month.year}",
                style = MaterialTheme.typography.titleMedium,
                textAlign = TextAlign.Center,
                modifier = Modifier.weight(1f),
            )
            IconButton(onClick = { onMonth(month.plusMonths(1)) }) {
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = "Bulan berikutnya")
            }
        }
        Row {
            IndoDate.daysShort.forEachIndexed { k, d ->
                Text(
                    d,
                    style = MaterialTheme.typography.labelSmall,
                    color = if (k == 6) red else MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f),
                )
            }
        }
        val offset = month.atDay(1).dayOfWeek.value - 1
        val days = month.lengthOfMonth()
        val rows = (offset + days + 6) / 7
        for (r in 0 until rows) {
            Row {
                for (k in 0..6) {
                    val d = r * 7 + k - offset + 1
                    Box(Modifier.weight(1f).aspectRatio(1f).padding(2.dp), contentAlignment = Alignment.Center) {
                        if (d in 1..days) {
                            val date = month.atDay(d)
                            val selected = date == picked
                            Box(
                                Modifier
                                    .fillMaxSize()
                                    .clip(CircleShape)
                                    .background(if (selected) MaterialTheme.colorScheme.primary else Color.Transparent)
                                    .then(if (date == today && !selected) Modifier.border(1.dp, MaterialTheme.colorScheme.primary, CircleShape) else Modifier)
                                    .clickable { onPick(date) },
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    "$d",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = when {
                                        selected -> MaterialTheme.colorScheme.onPrimary
                                        k == 6 -> red
                                        else -> MaterialTheme.colorScheme.onSurface
                                    },
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/** Pilih format dan halaman, lalu bagikan atau simpan. */
@Composable
private fun ExportDialog(c: EditorController, onDismiss: () -> Unit) {
    val context = LocalContext.current
    var format by rememberSaveable { mutableStateOf(ExportFormat.PNG) }
    var all by rememberSaveable { mutableStateOf(false) }
    var doc by rememberSaveable { mutableStateOf(DocContent.BOTH) }
    // Pilih halaman tertentu (mis. "1-3, 5"): mengambil sebagian halaman jadi berkas baru.
    var picking by rememberSaveable { mutableStateOf(false) }
    var pickText by rememberSaveable { mutableStateOf("") }
    val pick = if (picking) c.parsePageList(pickText) else null
    val busy = c.exportBusy
    AlertDialog(
        onDismissRequest = { if (busy == null) onDismiss() },
        title = { Text("Ekspor catatan") },
        text = {
            if (busy != null) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 3.dp)
                    Spacer(Modifier.width(14.dp))
                    Text(busy, style = MaterialTheme.typography.bodyMedium)
                }
            } else {
                Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Format", style = MaterialTheme.typography.labelLarge)
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        ExportFormat.entries.forEach { f ->
                            ToolChip(selected = format == f, onClick = { format = f }, label = { Text(f.label) })
                        }
                    }
                    Text("Halaman", style = MaterialTheme.typography.labelLarge)
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        ToolChip(selected = !all && !picking, onClick = { all = false; picking = false }, label = { Text("Halaman ini (${c.index + 1})") })
                        ToolChip(selected = all && !picking, onClick = { all = true; picking = false }, label = { Text("Semua (${c.pages.size})") })
                        ToolChip(selected = picking, onClick = { picking = true }, label = { Text("Pilih halaman…") })
                    }
                    if (picking) {
                        OutlinedTextField(
                            value = pickText,
                            onValueChange = { pickText = it },
                            label = { Text("Nomor halaman, mis. 1-3, 5") },
                            singleLine = true,
                            isError = pickText.isNotBlank() && pick == null,
                            supportingText = {
                                Text(
                                    if (pick == null) "Ketik nomor halaman dari 1 sampai ${c.pages.size}."
                                    else "${pick.size} halaman: ${pick.joinToString(", ") { (it + 1).toString() }.take(60)}",
                                )
                            },
                            modifier = Modifier.fillMaxWidth().semantics { contentDescription = "Nomor halaman yang diekspor" },
                        )
                    }
                    if (format == ExportFormat.DOCX) {
                        Text("Isi dokumen", style = MaterialTheme.typography.labelLarge)
                        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            DocContent.entries.forEach { d ->
                                ToolChip(selected = doc == d, onClick = { doc = d }, label = { Text(d.label) })
                            }
                        }
                        Text(
                            when (doc) {
                                DocContent.BOTH -> "Tiap halaman: teks ketik dan tulisan tangan yang terbaca jadi teks yang bisa diedit, lalu gambar halamannya persis seperti di layar."
                                DocContent.PAGES -> "Tiap halaman jadi gambar utuh di dokumen Word, persis seperti di layar."
                                DocContent.TEXT -> "Teks ketik dan tulisan tangan dibaca jadi teks biasa yang bisa diedit. Gambar dan coretan tidak ikut."
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    } else if (format == ExportFormat.PDF) {
                        Text(
                            "Satu berkas PDF, satu halaman PDF untuk tiap halaman catatan seukuran kertasnya. Tulisan tetap tajam saat diperbesar atau dicetak.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    } else if (all && c.pages.size > 1) {
                        Text(
                            "Tiap halaman jadi satu berkas gambar.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    HorizontalDivider(Modifier.padding(vertical = 4.dp))
                    OutlinedButton(onClick = { c.exportToKeep(context, all) }, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Filled.Lightbulb, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Simpan ke Google Keep")
                    }
                    Text(
                        "Jadi catatan baru di Keep: judulnya nama catatan ini, isinya teks ketik dan tulisan tangan yang " +
                            "terbaca, ditambah gambar ${if (all && c.pages.size > 1) "tiap halaman" else "halamannya"}.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        },
        confirmButton = {
            if (busy == null) {
                Row {
                    val ok = !picking || pick != null
                    TextButton(enabled = ok, onClick = { c.export(context, format, all, doc, ExportAction.SAVE, pick) }) { Text("Simpan") }
                    Button(enabled = ok, onClick = { c.export(context, format, all, doc, ExportAction.SHARE, pick) }) { Text("Bagikan") }
                }
            }
        },
        dismissButton = { if (busy == null) TextButton(onClick = onDismiss) { Text("Batal") } },
    )
}

@Composable
private fun InkLanguageDialog(current: InkLanguage, onDismiss: () -> Unit, onPick: (InkLanguage) -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Bahasa tulisan tangan") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                InkLanguage.entries.forEach { l ->
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).clickable { onPick(l) }.padding(vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = l == current, onClick = { onPick(l) })
                        Text(l.label, style = MaterialTheme.typography.bodyLarge)
                    }
                }
                Text(
                    "Otomatis membaca tiap baris dengan kamus Indonesia dan Inggris, lalu memakai yang paling cocok. " +
                        "Kamus diunduh sekali (perlu internet), setelah itu bekerja tanpa internet.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Tutup") } },
    )
}

/** Buka Google Keep dengan catatan baru berisi judul, teks, dan gambar halaman. Hasil: false bila Keep tidak ada. */
private fun sendToKeep(context: Context, job: ExportJob): Boolean {
    val authority = context.packageName + ".files"
    val uris = job.files.map { FileProvider.getUriForFile(context, authority, it) }
    val send = when {
        uris.isEmpty() -> Intent(Intent.ACTION_SEND).apply { type = "text/plain" }
        uris.size == 1 -> Intent(Intent.ACTION_SEND).apply { putExtra(Intent.EXTRA_STREAM, uris[0]); type = "image/jpeg" }
        else -> Intent(Intent.ACTION_SEND_MULTIPLE).apply {
            putParcelableArrayListExtra(Intent.EXTRA_STREAM, ArrayList(uris))
            type = "image/jpeg"
        }
    }
    job.title?.let { send.putExtra(Intent.EXTRA_SUBJECT, it); send.putExtra(Intent.EXTRA_TITLE, it) }
    send.putExtra(Intent.EXTRA_TEXT, job.text?.takeIf { it.isNotBlank() } ?: job.title ?: "")
    if (uris.isNotEmpty()) {
        send.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        val clip = ClipData.newRawUri(job.files[0].name, uris[0])
        for (k in 1 until uris.size) clip.addItem(ClipData.Item(uris[k]))
        send.clipData = clip
    }
    send.setPackage("com.google.android.keep")
    return try {
        context.startActivity(send)
        true
    } catch (e: android.content.ActivityNotFoundException) {
        false
    } catch (e: Throwable) {
        CrashLog.nonFatal("Buka Google Keep gagal", e)
        false
    }
}

/** Bagikan hasil ekspor ke aplikasi lain (WhatsApp, Drive, email, dan lainnya). */
private fun shareFiles(context: Context, job: ExportJob) {
    val authority = context.packageName + ".files"
    val uris = job.files.map { FileProvider.getUriForFile(context, authority, it) }
    if (uris.isEmpty()) return
    val send = if (uris.size == 1) {
        Intent(Intent.ACTION_SEND).apply { putExtra(Intent.EXTRA_STREAM, uris[0]) }
    } else {
        Intent(Intent.ACTION_SEND_MULTIPLE).apply { putParcelableArrayListExtra(Intent.EXTRA_STREAM, ArrayList(uris)) }
    }
    send.type = job.format.mime
    send.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    val clip = ClipData.newRawUri(job.files[0].name, uris[0])
    for (k in 1 until uris.size) clip.addItem(ClipData.Item(uris[k]))
    send.clipData = clip
    runCatching { context.startActivity(Intent.createChooser(send, "Bagikan catatan")) }
}

/** Bayangan gambar: pilihan cepat dan pengatur halus, dengan pratinjau persis seperti di halaman. */
@Composable
private fun ShadowDialog(img: ImageItem, dir: File, onDismiss: () -> Unit, onApply: (Shadow?) -> Unit) {
    val start = img.shadow
    var on by remember { mutableStateOf(start != null) }
    var distance by remember { mutableFloatStateOf(start?.distance ?: Shadow.RAISED.distance) }
    var blur by remember { mutableFloatStateOf(start?.blur ?: Shadow.RAISED.blur) }
    var opacity by remember { mutableFloatStateOf(start?.opacity ?: Shadow.RAISED.opacity) }
    fun preset(s: Shadow?) {
        on = s != null
        if (s != null) { distance = s.distance; blur = s.blur; opacity = s.opacity }
    }
    val current = if (on) Shadow(distance, blur, opacity) else null
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Bayangan gambar") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ToolChip(selected = !on, onClick = { preset(null) }, label = { Text("Tanpa") })
                    ToolChip(selected = on && current == Shadow.RAISED, onClick = { preset(Shadow.RAISED) }, label = { Text("Timbul") })
                    ToolChip(selected = on && current == Shadow.FLOATING, onClick = { preset(Shadow.FLOATING) }, label = { Text("Melayang") })
                    ToolChip(selected = on && current == Shadow.STRONG, onClick = { preset(Shadow.STRONG) }, label = { Text("Tegas") })
                }
                val paper = Color(0xFFFBFAF7)
                Canvas(
                    Modifier
                        .fillMaxWidth()
                        .height(190.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(paper)
                        .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(10.dp)),
                ) {
                    val previewItem = img.withShadow(current)
                    // Skala pratinjau: gambar memenuhi sekitar separuh kotak, bayangan ikut skala yang sama.
                    val k = minOf(size.width * 0.5f / img.w, size.height * 0.6f / img.h)
                    drawIntoCanvas { cv ->
                        val nc = cv.nativeCanvas
                        nc.save()
                        nc.translate(size.width / 2f - img.cx * k, size.height / 2f - img.cy * k)
                        nc.scale(k, k)
                        InkRenderer.drawItem(nc, previewItem, android.graphics.Paint(), dir, k)
                        nc.restore()
                    }
                }
                if (on) {
                    Text("Tinggi melayang · ${distance.roundToInt()}", style = MaterialTheme.typography.labelLarge)
                    Slider(value = distance, onValueChange = { distance = it }, valueRange = 0f..30f)
                    Text("Kelembutan · ${blur.roundToInt()}", style = MaterialTheme.typography.labelLarge)
                    Slider(value = blur, onValueChange = { blur = it }, valueRange = 0f..36f)
                    Text("Kepekatan · ${(opacity * 100).roundToInt()}%", style = MaterialTheme.typography.labelLarge)
                    Slider(value = opacity, onValueChange = { opacity = it }, valueRange = 0.05f..0.8f)
                } else {
                    Text(
                        "Pilih Timbul untuk kesan sedikit terangkat, atau Melayang untuk bayangan jauh dan lembut. Gambar tembus pandang berbayang sesuai bentuknya.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        },
        confirmButton = { TextButton(onClick = { onApply(current) }) { Text("Terapkan") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Batal") } },
    )
}

/** Hapus latar: pratinjau di atas papan catur, pilih cara otomatis atau latar polos. */
@Composable
private fun RemoveBgDialog(c: EditorController, s: BgRemoval) {
    val shown = if (s.useAi) s.ai else s.colorResult
    val img = s.image
    AlertDialog(
        onDismissRequest = { c.closeRemoveBackground() },
        title = { Text("Hapus latar") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ToolChip(
                        selected = s.useAi && s.ai != null,
                        onClick = { c.chooseBgMethod(true) },
                        enabled = s.ai != null,
                        label = { Text("Otomatis") },
                    )
                    ToolChip(selected = !s.useAi || s.ai == null, onClick = { c.chooseBgMethod(false) }, label = { Text("Latar polos") })
                }
                val src = s.source
                val ratio = if (src != null) src.width.toFloat() / src.height.coerceAtLeast(1) else 1f
                BoxWithConstraints(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    val maxH = 300.dp
                    val w = if (maxWidth / ratio > maxH) maxH * ratio else maxWidth
                    Box(Modifier.size(w, w / ratio).clip(RoundedCornerShape(8.dp)), contentAlignment = Alignment.Center) {
                        Canvas(Modifier.fillMaxSize()) {
                            val cell = 10.dp.toPx()
                            drawRect(Color.White)
                            var y = 0f
                            var row = 0
                            while (y < size.height) {
                                var x = if (row % 2 == 0) 0f else cell
                                while (x < size.width) {
                                    drawRect(Color(0xFFD6D6DA), Offset(x, y), Size(cell, cell))
                                    x += cell * 2
                                }
                                y += cell
                                row++
                            }
                        }
                        if (shown != null) {
                            val bmp = remember(shown) { shown.asImageBitmap() }
                            Image(
                                bmp,
                                contentDescription = "Pratinjau hapus latar",
                                contentScale = ContentScale.FillBounds,
                                modifier = Modifier.fillMaxSize().graphicsLayer {
                                    scaleX = if (img.flipH) -1f else 1f
                                    scaleY = if (img.flipV) -1f else 1f
                                },
                            )
                        }
                        if (shown == null || (!s.useAi && s.colorBusy)) {
                            CircularProgressIndicator(Modifier.size(28.dp), strokeWidth = 3.dp)
                        }
                    }
                }
                val status = s.status
                when {
                    status != null -> Text(status, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    s.ai == null && s.aiProblem != null -> Text(
                        s.aiProblem + " Cara latar polos tetap bisa dipakai untuk latar satu warna.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (!s.useAi || s.ai == null) {
                    Text("Kepekaan · ${(s.tolerance * 100).roundToInt()}", style = MaterialTheme.typography.labelLarge)
                    Slider(value = s.tolerance, onValueChange = { c.setBgTolerance(it) }, valueRange = 0.04f..0.45f)
                    Text(
                        "Geser ke kanan bila masih ada sisa latar, ke kiri bila bagian objek ikut terhapus.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    Text(
                        "Objek utama dikenali otomatis. Bila hasilnya kurang pas, coba \u201cLatar polos\u201d.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        },
        confirmButton = { TextButton(onClick = { c.applyRemoveBackground() }, enabled = shown != null) { Text("Terapkan") } },
        dismissButton = { TextButton(onClick = { c.closeRemoveBackground() }) { Text("Batal") } },
    )
}

/** Potong gambar: geser sudut, sisi, atau seluruh kotak untuk memilih bagian yang tampil. */
@Composable
private fun CropDialog(img: ImageItem, dir: File, onDismiss: () -> Unit, onConfirm: (Crop) -> Unit) {
    val bitmap = remember(img.file) { ImageCache.getNow(File(dir, img.file), 1024)?.asImageBitmap() }
    // Kotak potong seperti terlihat (gambar sudah dibalik seperti di halaman).
    var shown by remember(img) { mutableStateOf(img.crop.mirrored(img.flipH, img.flipV)) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Potong gambar") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (bitmap == null) {
                    Text("Gambar tidak bisa dibuka.", style = MaterialTheme.typography.bodyMedium)
                } else {
                    CropArea(bitmap, img.flipH, img.flipV, shown) { shown = it }
                }
                Text(
                    "Geser sudut atau sisi kotak. Seret bagian tengahnya untuk memindahkan kotak.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(shown.mirrored(img.flipH, img.flipV)) }, enabled = bitmap != null) { Text("Potong") }
        },
        dismissButton = {
            Row {
                TextButton(onClick = { shown = Crop.FULL }, enabled = !shown.isFull) { Text("Utuh") }
                TextButton(onClick = onDismiss) { Text("Batal") }
            }
        },
    )
}

@Composable
private fun CropArea(bitmap: ImageBitmap, flipH: Boolean, flipV: Boolean, rect: Crop, onChange: (Crop) -> Unit) {
    val ratio = bitmap.width.toFloat() / bitmap.height.coerceAtLeast(1)
    val accent = MaterialTheme.colorScheme.primary
    val current by rememberUpdatedState(rect)
    val change by rememberUpdatedState(onChange)
    BoxWithConstraints(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        val maxH = 360.dp
        val w = if (maxWidth / ratio > maxH) maxH * ratio else maxWidth
        val h = w / ratio
        Box(Modifier.size(w, h)) {
            Image(
                bitmap = bitmap,
                contentDescription = "Gambar yang dipotong",
                contentScale = ContentScale.FillBounds,
                modifier = Modifier.fillMaxSize().graphicsLayer {
                    scaleX = if (flipH) -1f else 1f
                    scaleY = if (flipV) -1f else 1f
                },
            )
            Canvas(
                Modifier.fillMaxSize().pointerInput(Unit) {
                    var grab = -1
                    detectDragGestures(
                        onDragStart = { o -> grab = cropGrab(o, size.width.toFloat(), size.height.toFloat(), current, 26.dp.toPx()) },
                        onDragEnd = { grab = -1 },
                        onDragCancel = { grab = -1 },
                    ) { ch, d ->
                        if (grab >= 0) {
                            ch.consume()
                            change(cropMove(current, grab, d.x / size.width, d.y / size.height))
                        }
                    }
                },
            ) {
                val c = current
                val l = c.l * size.width
                val t = c.t * size.height
                val r = c.r * size.width
                val b = c.b * size.height
                val dim = Color.Black.copy(alpha = 0.5f)
                drawRect(dim, Offset.Zero, Size(size.width, t))
                drawRect(dim, Offset(0f, b), Size(size.width, size.height - b))
                drawRect(dim, Offset(0f, t), Size(l, b - t))
                drawRect(dim, Offset(r, t), Size(size.width - r, b - t))
                val thin = Color.White.copy(alpha = 0.6f)
                for (k in 1..2) {
                    val gx = l + (r - l) * k / 3f
                    val gy = t + (b - t) * k / 3f
                    drawLine(thin, Offset(gx, t), Offset(gx, b), strokeWidth = 1f)
                    drawLine(thin, Offset(l, gy), Offset(r, gy), strokeWidth = 1f)
                }
                drawRect(accent, Offset(l, t), Size(r - l, b - t), style = DrawStroke(width = 2.dp.toPx()))
                val hr = 7.dp.toPx()
                for ((hx, hy) in listOf(l to t, r to t, r to b, l to b)) {
                    drawCircle(Color.White, hr, Offset(hx, hy))
                    drawCircle(accent, hr, Offset(hx, hy), style = DrawStroke(width = 2.dp.toPx()))
                }
            }
        }
    }
}

/** Bagian kotak potong yang disentuh: 0..3 sudut, 4..7 sisi (kiri, atas, kanan, bawah), 8 tengah, -1 di luar. */
private fun cropGrab(o: Offset, w: Float, h: Float, c: Crop, reach: Float): Int {
    val l = c.l * w
    val t = c.t * h
    val r = c.r * w
    val b = c.b * h
    val xs = floatArrayOf(l, r, r, l)
    val ys = floatArrayOf(t, t, b, b)
    for (k in 0..3) if (kotlin.math.hypot(o.x - xs[k], o.y - ys[k]) <= reach) return k
    val inY = o.y >= t - reach && o.y <= b + reach
    val inX = o.x >= l - reach && o.x <= r + reach
    return when {
        abs(o.x - l) <= reach && inY -> 4
        abs(o.y - t) <= reach && inX -> 5
        abs(o.x - r) <= reach && inY -> 6
        abs(o.y - b) <= reach && inX -> 7
        o.x in l..r && o.y in t..b -> 8
        else -> -1
    }
}

private fun cropMove(c: Crop, grab: Int, dx: Float, dy: Float): Crop {
    val min = 0.06f
    if (grab == 8) {
        val mx = dx.coerceIn(-c.l, 1f - c.r)
        val my = dy.coerceIn(-c.t, 1f - c.b)
        return Crop(c.l + mx, c.t + my, c.r + mx, c.b + my)
    }
    var l = c.l
    var t = c.t
    var r = c.r
    var b = c.b
    val movesL = grab == 0 || grab == 3 || grab == 4
    val movesT = grab == 0 || grab == 1 || grab == 5
    val movesR = grab == 1 || grab == 2 || grab == 6
    val movesB = grab == 2 || grab == 3 || grab == 7
    if (movesL) l = (l + dx).coerceIn(0f, maxOf(0f, r - min))
    if (movesR) r = (r + dx).coerceIn(minOf(1f, l + min), 1f)
    if (movesT) t = (t + dy).coerceIn(0f, maxOf(0f, b - min))
    if (movesB) b = (b + dy).coerceIn(minOf(1f, t + min), 1f)
    return Crop(l, t, r, b)
}

@Composable
private fun ConvertSheet(c: EditorController, state: ConvertState) {
    ModalBottomSheet(onDismissRequest = { c.closeConvert() }) {
        Column(
            Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("Ubah tulisan jadi teks", style = MaterialTheme.typography.titleLarge)
            when (state) {
                is ConvertState.Working -> Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 3.dp)
                    Spacer(Modifier.width(14.dp))
                    Text(state.message, style = MaterialTheme.typography.bodyMedium)
                }
                is ConvertState.Failed -> {
                    Text(state.message, style = MaterialTheme.typography.bodyMedium)
                    if (state.canRetry) {
                        Button(onClick = { c.startConvert(retry = true) }) { Text("Coba lagi") }
                    }
                }
            }
        }
    }
}

/** Tombol daftar berpoin dan bernomor. Yang sedang aktif disorot; tekan lagi untuk melepas. */
@Composable
private fun ListButtons(current: TextLists.Kind, onPick: (TextLists.Kind) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
        listOf(TextLists.Kind.BULLET, TextLists.Kind.NUMBER, TextLists.Kind.CHECK).forEach { k ->
            val selected = k == current
            IconButton(
                onClick = { onPick(k) },
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(if (selected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent),
            ) {
                Icon(
                    when (k) {
                        TextLists.Kind.BULLET -> Icons.AutoMirrored.Filled.FormatListBulleted
                        TextLists.Kind.NUMBER -> Icons.Filled.FormatListNumbered
                        else -> Icons.Filled.CheckBox
                    },
                    contentDescription = when (k) {
                        TextLists.Kind.BULLET -> "Daftar berpoin"
                        TextLists.Kind.NUMBER -> "Daftar bernomor"
                        else -> "Daftar centang"
                    },
                    tint = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/** Empat tombol perataan: kiri, tengah, kanan, kanan-kiri. */
@Composable
private fun AlignButtons(current: Align?, onPick: (Align) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
        Align.entries.forEach { a ->
            val selected = a == current
            IconButton(
                onClick = { onPick(a) },
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(if (selected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent),
            ) {
                Icon(
                    when (a) {
                        Align.LEFT -> Icons.AutoMirrored.Filled.FormatAlignLeft
                        Align.CENTER -> Icons.Filled.FormatAlignCenter
                        Align.RIGHT -> Icons.AutoMirrored.Filled.FormatAlignRight
                        Align.JUSTIFY -> Icons.Filled.FormatAlignJustify
                    },
                    contentDescription = a.label,
                    tint = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun FontChip(f: FontEntry, selected: Boolean, onClick: () -> Unit) {
    val fg = MaterialTheme.colorScheme.onSurface.toArgb()
    val shape = RoundedCornerShape(10.dp)
    Column(
        Modifier
            .clip(shape)
            .border(
                if (selected) 2.dp else 1.dp,
                if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                shape,
            )
            .clickable(onClickLabel = "Pakai font ${f.name}", onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Canvas(Modifier.size(width = 48.dp, height = 28.dp)) {
            val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                typeface = FontRegistry.typeface(f.id)
                textSize = 20.sp.toPx()
                color = fg
                textAlign = android.graphics.Paint.Align.CENTER
            }
            drawContext.canvas.nativeCanvas.drawText("Aa", size.width / 2f, size.height * 0.8f, paint)
        }
        Text(f.name, style = MaterialTheme.typography.labelSmall, maxLines = 1)
    }
}

@Composable
private fun AddFontChip(onClick: () -> Unit) {
    val shape = RoundedCornerShape(10.dp)
    Column(
        Modifier
            .clip(shape)
            .border(1.dp, MaterialTheme.colorScheme.primary, shape)
            .clickable(onClickLabel = "Tambah font dari berkas", onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(Modifier.size(width = 48.dp, height = 28.dp), contentAlignment = Alignment.Center) {
            Icon(Icons.Filled.Add, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        }
        Text("Tambah font", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary, maxLines = 1)
    }
}

/** Contoh teks dengan font dan ukuran pilihan, digambar seperti di halaman. */
@Composable
private fun TextSample(text: String, font: String, sizePt: Float, color: Int, modifier: Modifier) {
    Canvas(modifier) {
        val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
            typeface = FontRegistry.typeface(font)
            textSize = (sizePt * 1.1f).dp.toPx().coerceAtMost(size.height * 0.7f)
            this.color = color
        }
        val y = size.height / 2f - (paint.descent() + paint.ascent()) / 2f
        drawContext.canvas.nativeCanvas.drawText(text, 12.dp.toPx(), y, paint)
    }
}

/** Penanda bahwa teks otomatis sedang aktif, dengan tombol untuk mematikannya. */
@Composable
private fun AutoTextChip(c: EditorController, modifier: Modifier) {
    val fg = MaterialTheme.colorScheme.onPrimaryContainer
    Surface(modifier, shape = RoundedCornerShape(50), color = MaterialTheme.colorScheme.primaryContainer, shadowElevation = 3.dp) {
        Row(Modifier.padding(start = 14.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.TextFields, contentDescription = null, modifier = Modifier.size(18.dp), tint = fg)
            Spacer(Modifier.width(6.dp))
            Text("Teks otomatis aktif", style = MaterialTheme.typography.labelLarge, color = fg)
            IconButton(onClick = { c.toggleAutoText() }) {
                Icon(Icons.Filled.Close, contentDescription = "Matikan teks otomatis", tint = fg)
            }
        }
    }
}

/**
 * Mengetik dan menyunting mind map langsung di halaman, tanpa jendela atau kolom ketik: keyboard terhubung ke
 * kolom tersembunyi, sedangkan teks, kursor, dan pilihannya tampil di kanvas. Bilah alat kecil melayang di dekat
 * yang sedang disunting.
 */
@Composable
private fun InlineEditing(c: EditorController, fonts: List<FontEntry>, addedFont: String?, onAddFont: () -> Unit, ribbon: Boolean) {
    val value = c.typeValue
    if (value != null) HiddenTypeField(c, value)
    LaunchedEffect(addedFont) {
        // Font yang baru ditambahkan langsung dipakai untuk teks yang sedang diketik.
        if (addedFont != null && c.typeValue != null && c.pickingFont) {
            c.pickingFont = false
            c.typeFont(addedFont)
            c.showKeyboard()
        } else if (addedFont != null && c.pickingNewFont) {
            c.pickingNewFont = false
            c.setNewTextFont(addedFont)
        }
    }
    val anchor = c.floatAnchor ?: return
    val map = c.mapState
    when {
        // Di mode Teks pada tablet, pilihan gaya ada di pita atas; bilah melayang tidak perlu.
        value != null && c.typeKind != TypeKind.NODE -> if (!ribbon) FloatingBar(anchor) { TextTypeBar(c, value, fonts, onAddFont) }
        map != null -> FloatingBar(anchor) { MindMapBar(c, map) }
    }
}

/** Kolom ketik tak terlihat: menerima ketikan keyboard (termasuk saran dan koreksi otomatis). */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun HiddenTypeField(c: EditorController, value: TextFieldValue) {
    val focus = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    val sizeFocus = remember { FocusRequester() }
    val sizeValue = c.sizeEntry
    LaunchedEffect(c.keyboardTick, sizeValue != null) {
        delay(30)
        runCatching { if (sizeValue != null) sizeFocus.requestFocus() else focus.requestFocus() }
        keyboard?.show()
    }
    // Keyboard ditutup sendiri (misalnya tombol turunkan keyboard): ketikan selesai.
    val imeVisible = WindowInsets.isImeVisible
    var seen by remember { mutableStateOf(false) }
    LaunchedEffect(imeVisible) {
        if (imeVisible) {
            seen = true
            return@LaunchedEffect
        }
        if (!seen) return@LaunchedEffect
        delay(350)
        seen = false
        c.onKeyboardHidden()
    }
    BasicTextField(
        value = value,
        onValueChange = { c.onTypeInput(it) },
        modifier = Modifier.size(1.dp).alpha(0f).focusRequester(focus),
        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
    )
    if (sizeValue != null) {
        // Angka ukuran huruf diketik lewat keyboard angka; tampil di bilah alat teks.
        BasicTextField(
            value = sizeValue,
            onValueChange = { c.onSizeEntry(it) },
            singleLine = true,
            modifier = Modifier.size(1.dp).alpha(0f).focusRequester(sizeFocus),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { c.commitSizeEntry() }),
        )
    }
}

/**
 * Ukuran huruf yang bisa diketik angkanya (keyboard angka), dengan tombol kecilkan dan besarkan. Angkanya dipakai
 * saat menekan Selesai di keyboard atau saat kolomnya ditinggalkan.
 */
@Composable
private fun SizeField(size: Float, onSet: (Float) -> Unit) {
    var text by remember(size) { mutableStateOf(sizeLabel(size)) }
    var focused by remember { mutableStateOf(false) }
    val focusManager = LocalFocusManager.current
    fun apply() {
        val v = parseSize(text)
        if (v == null) text = sizeLabel(size) else if (kotlin.math.abs(v - size) > 0.01f) onSet(v)
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = { onSet(size - 1f) }) { Icon(Icons.Filled.Remove, contentDescription = "Kecilkan huruf") }
        BasicTextField(
            value = text,
            onValueChange = { v -> text = v.filter { it.isDigit() || it == ',' || it == '.' }.take(5) },
            singleLine = true,
            textStyle = MaterialTheme.typography.titleSmall.copy(textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurface),
            cursorBrush = androidx.compose.ui.graphics.SolidColor(MaterialTheme.colorScheme.primary),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = {
                apply()
                focusManager.clearFocus()
            }),
            modifier = Modifier
                .width(56.dp)
                .border(
                    if (focused) 2.dp else 1.dp,
                    if (focused) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                    RoundedCornerShape(8.dp),
                )
                .padding(vertical = 8.dp)
                .onFocusChanged { f ->
                    if (focused && !f.isFocused) apply()
                    focused = f.isFocused
                },
        )
        Text(" pt", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        IconButton(onClick = { onSet(size + 1f) }) { Icon(Icons.Filled.Add, contentDescription = "Besarkan huruf") }
    }
}

/** Tanda kecil "sedang membaca tulisan" di atas kanvas (tidak menutupi halaman), dengan tombol batal. */
@Composable
private fun ConvertProgress(c: EditorController, message: String, modifier: Modifier) {
    Surface(modifier.padding(horizontal = 16.dp), shape = RoundedCornerShape(24.dp), tonalElevation = 4.dp, shadowElevation = 6.dp) {
        Row(Modifier.padding(start = 16.dp, end = 4.dp, top = 4.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
            Spacer(Modifier.width(10.dp))
            Text(message, style = MaterialTheme.typography.labelLarge, modifier = Modifier.widthIn(max = 420.dp))
            TextButton(onClick = { c.closeConvert() }) { Text("Batal") }
        }
    }
}

/** Tombol kecil untuk mengembalikan halaman yang diputar atau digeser ke posisi tegak di tengah. */
@Composable
private fun StraightenChip(c: EditorController, modifier: Modifier) {
    Surface(modifier, shape = RoundedCornerShape(50), tonalElevation = 4.dp, shadowElevation = 6.dp) {
        Row(
            Modifier.toolClick(onClickLabel = "Luruskan halaman") { c.straighten() }.padding(horizontal = 14.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Filled.CropRotate, contentDescription = null, modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(6.dp))
            Text("Luruskan", style = MaterialTheme.typography.labelLarge)
        }
    }
}

/** Letak bilah alat melayang: di atas kotak [l],[t],[r],[b] (satuan piksel kanvas), atau di bawahnya. */
private class NearRect(val l: Int, val t: Int, val r: Int, val b: Int, val gap: Int) : PopupPositionProvider {
    override fun calculatePosition(anchorBounds: IntRect, windowSize: IntSize, layoutDirection: LayoutDirection, popupContentSize: IntSize): IntOffset {
        val w = popupContentSize.width
        val h = popupContentSize.height
        var px = anchorBounds.left + (l + r) / 2 - w / 2
        var py = anchorBounds.top + t - h - gap
        if (py < anchorBounds.top) py = anchorBounds.top + b + gap
        py = py.coerceIn(anchorBounds.top, maxOf(anchorBounds.top, anchorBounds.bottom - h))
        px = px.coerceIn(0, maxOf(0, windowSize.width - w))
        return IntOffset(px, py)
    }
}

@Composable
private fun FloatingBar(anchor: android.graphics.RectF, content: @Composable () -> Unit) {
    val gap = with(LocalDensity.current) { 14.dp.roundToPx() }
    val provider = remember(anchor) {
        NearRect(anchor.left.roundToInt(), anchor.top.roundToInt(), anchor.right.roundToInt(), anchor.bottom.roundToInt(), gap)
    }
    // Tidak mengambil fokus, jadi keyboard tetap terbuka saat tombolnya diketuk.
    Popup(popupPositionProvider = provider, properties = PopupProperties(focusable = false)) {
        Surface(shape = RoundedCornerShape(28.dp), tonalElevation = 6.dp, shadowElevation = 10.dp) {
            Row(
                Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 6.dp, vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) { content() }
        }
    }
}

private enum class BarPanel { FONT, COLOR, CELL_FILL }

/** Sel tabel yang akan diberi warna sendiri: nomor tabel, baris, kolom, dan cakupannya (lihat [TableItem.scopeCells]). */
class CellFillPick(val tableId: Long, val r: Int, val c: Int, val scope: Int)

/** Warna isi sel yang sedang diketik: untuk sel ini, barisnya, kolomnya, atau semua sel. */
@Composable
private fun CellFillPanel(c: EditorController) {
    var scope by remember { mutableIntStateOf(0) }
    val scopes = listOf("Sel ini", "Baris", "Kolom", "Semua sel")
    scopes.forEachIndexed { i, label ->
        FilterChip(
            selected = scope == i,
            onClick = { scope = i },
            label = { Text(label) },
            modifier = Modifier.padding(end = 4.dp),
        )
    }
    BarDivider()
    NoFillDot(onClick = { c.typeCellFill(0, scope) })
    TableFillColors.forEachIndexed { i, col ->
        ColorDot(Color(col), false, { c.typeCellFill(col, scope) }, size = 24.dp, description = "Sel ${TableFillNames[i]}")
    }
    CustomColorDot(onClick = { c.pickCellFill(scope) }, size = 24.dp)
    Spacer(Modifier.width(6.dp))
}

/** Bilah alat teks yang sedang diketik: huruf, ukuran, warna, perataan, daftar, salin/tempel, selesai. */
@Composable
private fun TextTypeBar(c: EditorController, value: TextFieldValue, fonts: List<FontEntry>, onAddFont: () -> Unit) {
    @Suppress("UNUSED_VARIABLE") val tick = c.typeStyleTick
    val style = c.typingStyle()
    val clipboard = LocalClipboardManager.current
    var panel by remember { mutableStateOf<BarPanel?>(null) }
    when (panel) {
        BarPanel.FONT -> {
            IconButton(onClick = { panel = null }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Kembali") }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(end = 6.dp, top = 4.dp, bottom = 4.dp)) {
                fonts.forEach { f -> FontChip(f, f.id == style?.font) { c.typeFont(f.id) } }
                AddFontChip { c.pickingFont = true; onAddFont() }
            }
            return
        }
        BarPanel.COLOR -> {
            IconButton(onClick = { panel = null }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Kembali") }
            InkColors.forEach { col ->
                ColorDot(Color(col), col == style?.color, { c.typeColor(col); panel = null }, size = 26.dp)
            }
            Spacer(Modifier.width(6.dp))
            return
        }
        BarPanel.CELL_FILL -> {
            IconButton(onClick = { panel = null }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Kembali") }
            CellFillPanel(c)
            return
        }
        null -> Unit
    }
    if (c.typeKind == TypeKind.CELL) {
        IconButton(onClick = { c.cellStep(-1) }) { Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = "Sel sebelumnya") }
        IconButton(onClick = { c.cellStep(1) }) { Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = "Sel berikutnya") }
        TextButton(onClick = { c.cellAddRow() }) {
            Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(2.dp))
            Text("Baris")
        }
        TextButton(onClick = { panel = BarPanel.CELL_FILL }) {
            Icon(Icons.Filled.FormatColorFill, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(4.dp))
            Text("Warna sel")
        }
        TextButton(onClick = { c.openTableLook() }) {
            Icon(Icons.Filled.BorderAll, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(4.dp))
            Text("Garis & warna")
        }
        BarDivider()
    }
    if (c.editSuggestions.isNotEmpty()) {
        Text(
            "Mungkin:",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 10.dp),
        )
        c.editSuggestions.forEach { (shown, full) ->
            TextButton(onClick = { c.replaceTyping(TextFieldValue(full, TextRange(full.length))) }) { Text(shown, maxLines = 1) }
        }
        BarDivider()
    }
    if (style != null) {
        TextButton(onClick = { panel = BarPanel.FONT }) {
            Text(fonts.firstOrNull { it.id == style.font }?.name ?: "Huruf", maxLines = 1)
        }
        IconButton(onClick = { c.typeSize(style.size - 1f) }) { Icon(Icons.Filled.Remove, contentDescription = "Kecilkan huruf") }
        val entry = c.sizeEntry
        if (entry != null) {
            // Sedang mengetik angka ukuran.
            Row(
                Modifier
                    .border(2.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(8.dp))
                    .padding(horizontal = 10.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(entry.text, style = MaterialTheme.typography.labelLarge)
                Box(Modifier.padding(start = 1.dp).width(2.dp).height(16.dp).background(MaterialTheme.colorScheme.primary))
                Text(" pt", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else {
            Box(
                Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(8.dp))
                    .toolClick(onClickLabel = "Ketik ukuran huruf") { c.beginSizeEntry() }
                    .padding(horizontal = 10.dp, vertical = 6.dp),
            ) {
                Text(sizeLabel(style.size), style = MaterialTheme.typography.labelLarge)
            }
        }
        IconButton(onClick = { c.typeSize(style.size + 1f) }) { Icon(Icons.Filled.Add, contentDescription = "Besarkan huruf") }
        if (entry != null) TextButton(onClick = { c.commitSizeEntry() }) { Text("OK") }
        style.color?.let { col ->
            ColorDot(Color(col), false, { panel = BarPanel.COLOR }, size = 24.dp, description = "Warna teks")
            Spacer(Modifier.width(4.dp))
        }
        if (c.typeKind == TypeKind.TEXT) {
            StyleButtons(c)
            Spacer(Modifier.width(4.dp))
        }
        AlignButtons(style.align, onPick = { c.typeAlign(it) })
        ListButtons(TextLists.kindAt(value.text, value.selection.min, value.selection.max), onPick = { c.typeList(it) })
        BarDivider()
    }
    val a = value.selection.min
    val b = value.selection.max
    if (a < b) {
        TextButton(onClick = {
            clipboard.setText(AnnotatedString(value.text.substring(a, b)))
        }) { Text("Salin") }
        TextButton(onClick = {
            clipboard.setText(AnnotatedString(value.text.substring(a, b)))
            c.replaceTyping(TextFieldValue(value.text.removeRange(a, b), TextRange(a)))
        }) { Text("Potong") }
    }
    if (clipboard.hasText()) {
        TextButton(onClick = {
            val t = clipboard.getText()?.text ?: return@TextButton
            c.replaceTyping(TextFieldValue(value.text.replaceRange(a, b, t), TextRange(a + t.length)))
        }) { Text("Tempel") }
    }
    if (value.text.isNotEmpty() && b - a < value.text.length) {
        TextButton(onClick = { c.replaceTyping(TextFieldValue(value.text, TextRange(0, value.text.length))) }) { Text("Pilih semua") }
    }
    Button(onClick = { c.finishEditing(commit = true) }, modifier = Modifier.padding(start = 4.dp, end = 2.dp)) {
        Icon(Icons.Filled.Check, contentDescription = null, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(6.dp))
        Text("Selesai")
    }
}

/** Saklar mode Pensil (menulis tangan) dan mode Teks (mengetik seperti di pengolah kata). */
@Composable
private fun ModeSwitch(c: EditorController, modifier: Modifier = Modifier) {
    val typing = c.mode == InputMode.TYPE
    val pencil = c.mode == InputMode.INK || c.mode == InputMode.SHAPE || c.mode == InputMode.TAPE
    Row(
        modifier
            .height(42.dp)
            .clip(RoundedCornerShape(21.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f))
            .padding(3.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ModeSegment(Icons.Filled.Edit, "Pensil", pencil) { c.selectPencil() }
        ModeSegment(Icons.Filled.Keyboard, "Teks", typing) { if (!typing) c.selectTyping() }
    }
}

@Composable
private fun ModeSegment(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, active: Boolean, onClick: () -> Unit) {
    val bg = if (active) MaterialTheme.colorScheme.primary else Color.Transparent
    val fg = if (active) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
    Row(
        Modifier
            .fillMaxHeight()
            .clip(RoundedCornerShape(18.dp))
            .background(bg)
            .toolClick(onClickLabel = "Mode $label", onClick = onClick)
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = fg, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(6.dp))
        Text(label, style = MaterialTheme.typography.labelLarge, color = fg)
    }
}

/** Tanda mode Teks di baki alat (naik saat aktif, seperti alat tulis). */
@Composable
private fun TypeGlyph(ink: Color, selected: Boolean, modifier: Modifier = Modifier) {
    val lift by animateFloatAsState(if (selected) 0f else 10f, label = "lift")
    Box(modifier.size(width = 30.dp, height = 46.dp).clipToBounds(), contentAlignment = Alignment.TopCenter) {
        Icon(
            Icons.Filled.Keyboard,
            contentDescription = null,
            tint = if (selected) MaterialTheme.colorScheme.primary else ink,
            modifier = Modifier.padding(top = 8.dp).offset(y = lift.dp).size(28.dp),
        )
    }
}

/** Tombol tebal, miring, garis bawah, dan coret. */
@Composable
private fun StyleButtons(c: EditorController) {
    @Suppress("UNUSED_VARIABLE") val tick = c.typeStyleTick
    Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
        StyleToggle(Icons.Filled.FormatBold, "Tebal", c.typeFlagActive(TextRuns.BOLD)) { c.typeToggle(TextRuns.BOLD) }
        StyleToggle(Icons.Filled.FormatItalic, "Miring", c.typeFlagActive(TextRuns.ITALIC)) { c.typeToggle(TextRuns.ITALIC) }
        StyleToggle(Icons.Filled.FormatUnderlined, "Garis bawah", c.typeFlagActive(TextRuns.UNDERLINE)) { c.typeToggle(TextRuns.UNDERLINE) }
        StyleToggle(Icons.Filled.FormatStrikethrough, "Coret", c.typeFlagActive(TextRuns.STRIKE)) { c.typeToggle(TextRuns.STRIKE) }
    }
}

@Composable
private fun StyleToggle(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, active: Boolean, onClick: () -> Unit) {
    IconButton(
        onClick = onClick,
        modifier = Modifier
            .size(40.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(if (active) MaterialTheme.colorScheme.primaryContainer else Color.Transparent),
    ) {
        Icon(
            icon,
            contentDescription = label,
            tint = if (active) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** Pita format mode Teks di tablet: gaya teks yang sedang diketik, atau gaya untuk teks berikutnya. */
@Composable
private fun TypeRibbon(c: EditorController, fonts: List<FontEntry>, onAddFont: () -> Unit) {
    val value = c.typeValue
    if (value != null && c.typeKind != TypeKind.NODE) {
        TextTypeBar(c, value, fonts, onAddFont)
    } else {
        NewTextBar(c, fonts, onAddFont)
    }
}

/** Mode Teks, belum mengetik: huruf, ukuran, gaya, dan warna untuk teks berikutnya. */
@Composable
private fun NewTextBar(c: EditorController, fonts: List<FontEntry>, onAddFont: () -> Unit) {
    var panel by remember { mutableStateOf<BarPanel?>(null) }
    when (panel) {
        BarPanel.FONT -> {
            IconButton(onClick = { panel = null }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Kembali") }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(end = 6.dp, top = 4.dp, bottom = 4.dp)) {
                fonts.forEach { f -> FontChip(f, f.id == c.lastFont) { c.setNewTextFont(f.id); panel = null } }
                AddFontChip { c.pickingNewFont = true; onAddFont() }
            }
            return
        }
        BarPanel.COLOR -> {
            IconButton(onClick = { panel = null }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Kembali") }
            InkColors.forEach { col ->
                ColorDot(Color(col), col == c.newTextColor(), { c.setNewTextColor(col); panel = null }, size = 26.dp)
            }
            Spacer(Modifier.width(6.dp))
            return
        }
        BarPanel.CELL_FILL, null -> Unit
    }
    TextButton(onClick = { panel = BarPanel.FONT }) {
        Text(fonts.firstOrNull { it.id == c.lastFont }?.name ?: "Huruf", maxLines = 1)
    }
    SizeField(c.lastTextSize) { c.setNewTextSize(it) }
    StyleButtons(c)
    Spacer(Modifier.width(4.dp))
    ColorDot(Color(c.newTextColor()), false, { panel = BarPanel.COLOR }, size = 24.dp, description = "Warna teks")
    BarDivider()
    Text(
        "Ketuk di mana saja di halaman, lalu langsung ketik.",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 8.dp),
    )
}

/** Bilah alat mind map yang sedang disunting di halaman. */
@Composable
private fun MindMapBar(c: EditorController, st: InkView.MapState) {
    val accent = MaterialTheme.colorScheme.primary.toArgb()
    var picking by remember(st.selected) { mutableStateOf(false) }
    if (picking && st.selected >= 0) {
        IconButton(onClick = { picking = false }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Kembali") }
        (listOf(accent) + MindMaps.BranchColors).distinct().forEach { col ->
            ColorDot(Color(col), col == st.color, { picking = false; c.mapRecolor(col) }, size = 26.dp)
        }
        Spacer(Modifier.width(6.dp))
        return
    }
    if (st.selected < 0) {
        Text(
            "Ketuk ide untuk memilih",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 10.dp),
        )
        if (!st.tidy) TextButton(onClick = { c.mapTidy() }) { Text("Rapikan") }
        Button(onClick = { c.endMindMap() }, modifier = Modifier.padding(end = 2.dp)) { Text("Selesai") }
        return
    }
    if (!st.typing) TextButton(onClick = { c.mapTypeSelected() }) { Text("Ketik") }
    TextButton(onClick = { c.mapAdd(sibling = false) }) { Text("+ Cabang") }
    if (!st.isRoot) TextButton(onClick = { c.mapAdd(sibling = true) }) { Text("+ Sejajar") }
    if (st.typing) {
        IconButton(onClick = { c.finishEditing(commit = true) }) { Icon(Icons.Filled.Check, contentDescription = "Selesai mengetik") }
        return
    }
    TextButton(onClick = { picking = true }) { Text("Warna") }
    TextButton(onClick = { c.mapDelete() }) {
        Text(if (st.isRoot) "Hapus mind map" else "Hapus", color = MaterialTheme.colorScheme.error)
    }
    if (!st.tidy) TextButton(onClick = { c.mapTidy() }) { Text("Rapikan") }
    Button(onClick = { c.endMindMap() }, modifier = Modifier.padding(start = 4.dp, end = 2.dp)) { Text("Selesai") }
}

/** Enter di butir daftar langsung membuat butir berikutnya; Enter di butir kosong mengakhiri daftar. */
private fun continueList(old: TextFieldValue, new: TextFieldValue): TextFieldValue {
    if (!new.selection.collapsed) return new
    val (text, cursor) = TextLists.continueOnEnter(old.text, new.text, new.selection.start) ?: return new
    return TextFieldValue(text, selection = TextRange(cursor))
}

/** Ketebalan dalam milimeter, satuan yang biasa dipakai untuk mata pena. */
private fun mmLabel(pt: Float): String =
    String.format(Locale.US, "%.1f", pt * 25.4f / 72f).replace('.', ',') + " mm"


/** Arah kertas: tegak atau mendatar, untuk halaman ini atau semua halaman. */
@Composable
private fun OrientationDialog(c: EditorController, at: Int, onDismiss: () -> Unit) {
    var land by remember { mutableStateOf(c.isLandscape(at)) }
    var toAll by remember { mutableStateOf(false) }
    val isPdf = c.pages.getOrNull(at)?.let { PageBackground.pdfOf(it.background) != null } == true
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Orientasi halaman") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OrientationChoice("Tegak", landscape = false, selected = !land) { land = false }
                    OrientationChoice("Mendatar", landscape = true, selected = land) { land = true }
                }
                Text("Terapkan ke", style = MaterialTheme.typography.labelLarge)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ToolChip(selected = !toAll, onClick = { toAll = false }, label = { Text("Halaman ini") })
                    ToolChip(selected = toAll, onClick = { toAll = true }, label = { Text("Semua halaman") })
                }
                Text(
                    "Ukuran kertas tetap, hanya arahnya yang diputar, dan tersimpan bersama catatan. Halaman baru " +
                        "mengikuti arah halaman sebelumnya. Tulisan yang sudah ada tidak ikut berputar; yang jatuh di luar " +
                        "kertas tetap tersimpan dan muncul lagi bila arahnya dikembalikan." +
                        if (isPdf) " Halaman PDF mengikuti arah PDF-nya: putar lewat PDF → Putar." else "",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = {
            Button(onClick = { c.setOrientation(land, toAll, at); onDismiss() }) { Text("Terapkan") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Batal") } },
    )
}

/** Satu pilihan arah: gambar kertas kecil bergaris dan namanya. */
@Composable
private fun OrientationChoice(label: String, landscape: Boolean, selected: Boolean, onClick: () -> Unit) {
    val accent = MaterialTheme.colorScheme.primary
    val line = MaterialTheme.colorScheme.outlineVariant
    Column(
        Modifier
            .width(120.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
            .padding(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(Modifier.size(72.dp), contentAlignment = Alignment.Center) {
            androidx.compose.foundation.Canvas(
                Modifier
                    .then(if (landscape) Modifier.size(width = 70.dp, height = 50.dp) else Modifier.size(width = 50.dp, height = 70.dp)),
            ) {
                val r = 4.dp.toPx()
                drawRoundRect(Color.White, cornerRadius = androidx.compose.ui.geometry.CornerRadius(r))
                drawRoundRect(
                    if (selected) accent else line,
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(r),
                    style = androidx.compose.ui.graphics.drawscope.Stroke(width = 2.dp.toPx()),
                )
                val gap = 8.dp.toPx()
                var y = gap * 1.6f
                while (y < size.height - gap * 0.6f) {
                    drawLine(line, androidx.compose.ui.geometry.Offset(6.dp.toPx(), y), androidx.compose.ui.geometry.Offset(size.width - 6.dp.toPx(), y), strokeWidth = 1.dp.toPx())
                    y += gap
                }
            }
        }
        Text(
            label,
            style = MaterialTheme.typography.labelLarge,
            color = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}


/** Tombol cari di catatan dan daftar isi di bilah judul. */
@Composable
private fun NoteNavButtons(c: EditorController, onDialog: (EditorDialog) -> Unit) {
    IconButton(onClick = { if (c.searchOpen) c.closeSearch() else c.openSearch() }) {
        Icon(
            Icons.Filled.Search,
            contentDescription = if (c.searchOpen) "Tutup pencarian" else "Cari di catatan",
            tint = if (c.searchOpen) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
    IconButton(onClick = { onDialog(EditorDialog.Outline()) }) {
        Icon(Icons.AutoMirrored.Filled.Toc, contentDescription = "Daftar isi", tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
