package id.hananote.widget

import android.app.Activity
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.SpannableString
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.text.style.StrikethroughSpan
import android.view.View
import android.widget.RemoteViews
import android.widget.RemoteViewsService
import id.hananote.MainActivity
import id.hananote.R
import id.hananote.data.CrashLog
import id.hananote.data.IndoDate
import id.hananote.data.Note
import id.hananote.data.PageContent
import id.hananote.data.PageMeta
import id.hananote.data.Repository
import id.hananote.data.StickyItem
import id.hananote.data.TextLists
import id.hananote.ink.InkRenderer
import kotlinx.coroutines.launch
import java.util.Calendar
import java.util.concurrent.Executors
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min

/**
 * Widget layar utama: "Catatan terbaru" (daftar catatan yang bisa digulir + tombol catatan baru) dan "Satu catatan"
 * (isi satu catatan pilihan yang bisa digulir: daftar centang yang bisa dicentang langsung, lalu halaman-halamannya).
 */
object HanaWidgets {
    const val ACTION_OPEN = "id.hananote.action.OPEN_NOTE"
    const val ACTION_NEW = "id.hananote.action.NEW_NOTE"
    const val ACTION_TOGGLE = "id.hananote.action.TOGGLE_CHECK"
    const val ACTION_ITEM = "id.hananote.action.WIDGET_ITEM"
    const val EXTRA_NOTE = "note_id"
    /** Halaman yang dibuka (mulai 0), atau -1. */
    const val EXTRA_PAGE = "page_index"
    const val EXTRA_OP = "op"
    const val OP_OPEN = "open"
    const val OP_TOGGLE = "toggle"
    const val EXTRA_PAGE_ID = "page_id"
    const val EXTRA_STICKY = "sticky"
    const val EXTRA_INDEX = "index"
    const val EXTRA_ITEM = "item_id"
    const val EXTRA_AT = "at"
    const val EXTRA_TEXT = "text"

    private val worker = Executors.newSingleThreadExecutor { r -> Thread(r, "hana-widget").apply { isDaemon = true } }
    private val main = Handler(Looper.getMainLooper())
    private var pending: Runnable? = null

    private fun prefs(context: Context) = context.getSharedPreferences("hana_widgets", Context.MODE_PRIVATE)

    fun noteFor(context: Context, widgetId: Int): String? = prefs(context).getString("note_$widgetId", null)

    fun setNoteFor(context: Context, widgetId: Int, noteId: String) {
        prefs(context).edit().putString("note_$widgetId", noteId).apply()
    }

    fun forget(context: Context, widgetIds: IntArray) {
        val e = prefs(context).edit()
        for (id in widgetIds) e.remove("note_$id")
        e.apply()
    }

    /** Perbarui semua widget sebentar lagi (beberapa perubahan beruntun cukup sekali). */
    fun refreshSoon(context: Context) {
        val app = context.applicationContext
        main.post {
            pending?.let { main.removeCallbacks(it) }
            val r = Runnable { worker.execute { runCatching { refreshAll(app) } } }
            pending = r
            main.postDelayed(r, 700L)
        }
    }

    fun refreshAll(context: Context) {
        val mgr = AppWidgetManager.getInstance(context)
        val recent = mgr.getAppWidgetIds(ComponentName(context, RecentNotesWidget::class.java))
        for (id in recent) update(context, mgr, id, recent = true)
        val single = mgr.getAppWidgetIds(ComponentName(context, NoteWidget::class.java))
        for (id in single) update(context, mgr, id, recent = false)
    }

    /** Isi widget "Satu catatan" untuk catatan ini dimuat ulang sekarang (setelah dicentang). */
    fun refreshNote(context: Context, noteId: String) {
        val mgr = AppWidgetManager.getInstance(context)
        val ids = mgr.getAppWidgetIds(ComponentName(context, NoteWidget::class.java)).filter { noteFor(context, it) == noteId }
        if (ids.isNotEmpty()) mgr.notifyAppWidgetViewDataChanged(ids.toIntArray(), R.id.note_list)
        refreshSoon(context)
    }

    /** Gambar ulang widget dan muat ulang isi daftarnya. */
    fun update(context: Context, mgr: AppWidgetManager, widgetId: Int, recent: Boolean) {
        try {
            mgr.updateAppWidget(widgetId, if (recent) recentViews(context, widgetId) else noteViews(context, widgetId))
            mgr.notifyAppWidgetViewDataChanged(widgetId, if (recent) R.id.recent_list else R.id.note_list)
        } catch (e: Throwable) {
            CrashLog.nonFatal("Widget gagal diperbarui", e)
        }
    }

    // ---------- tampilan ----------

    private fun serviceIntent(context: Context, cls: Class<*>, widgetId: Int): Intent {
        val i = Intent(context, cls).putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId)
        // Alamat unik per widget, supaya tiap widget punya isi daftarnya sendiri.
        i.data = Uri.parse(i.toUri(Intent.URI_INTENT_SCHEME))
        return i
    }

    @Suppress("DEPRECATION")
    private fun recentViews(context: Context, widgetId: Int): RemoteViews {
        val rv = RemoteViews(context.packageName, R.layout.widget_recent)
        rv.setOnClickPendingIntent(R.id.widget_new, newNoteIntent(context))
        rv.setOnClickPendingIntent(R.id.widget_header, openAppIntent(context))
        rv.setRemoteAdapter(R.id.recent_list, serviceIntent(context, RecentNotesService::class.java, widgetId))
        rv.setEmptyView(R.id.recent_list, R.id.widget_empty)
        rv.setOnClickPendingIntent(R.id.widget_empty, newNoteIntent(context))
        rv.setPendingIntentTemplate(R.id.recent_list, itemTemplate(context, 40_000 + widgetId))
        return rv
    }

    @Suppress("DEPRECATION")
    private fun noteViews(context: Context, widgetId: Int): RemoteViews {
        val repo = Repository.get(context)
        val rv = RemoteViews(context.packageName, R.layout.widget_note)
        val id = noteFor(context, widgetId)
        val n = id?.let { repo.note(it) }
        val pick = PendingIntent.getActivity(
            context, 30_000 + widgetId,
            Intent(context, NoteWidgetConfigActivity::class.java)
                .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        if (n == null) {
            rv.setTextViewText(R.id.note_title, if (id == null) "Pilih catatan" else "Catatan sudah dihapus")
            rv.setViewVisibility(R.id.note_open, View.GONE)
            rv.setViewVisibility(R.id.note_list, View.GONE)
            rv.setViewVisibility(R.id.note_empty, View.VISIBLE)
            rv.setOnClickPendingIntent(R.id.note_empty, pick)
            rv.setOnClickPendingIntent(R.id.note_header, pick)
            return rv
        }
        rv.setTextViewText(R.id.note_title, n.title)
        rv.setViewVisibility(R.id.note_open, View.VISIBLE)
        rv.setViewVisibility(R.id.note_list, View.VISIBLE)
        rv.setViewVisibility(R.id.note_empty, View.GONE)
        rv.setOnClickPendingIntent(R.id.note_header, openNoteIntent(context, n.id))
        rv.setOnClickPendingIntent(R.id.note_open, openNoteIntent(context, n.id))
        rv.setRemoteAdapter(R.id.note_list, serviceIntent(context, NoteWidgetService::class.java, widgetId))
        rv.setPendingIntentTemplate(R.id.note_list, itemTemplate(context, 50_000 + widgetId))
        return rv
    }

    /** Gambar kecil halaman pertama, sudut membulat dan bergaris tepi tipis; null bila belum ada. */
    fun thumbnail(repo: Repository, n: Note, maxW: Int): Bitmap? = runCatching {
        val f = repo.thumbFile(n.id)
        if (!f.exists()) return null
        val o = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(f.path, o)
        var sample = 1
        while (o.outWidth / (sample * 2) >= maxW) sample *= 2
        val src = BitmapFactory.decodeFile(f.path, BitmapFactory.Options().apply { inSampleSize = sample }) ?: return null
        val out = Bitmap.createBitmap(src.width, src.height, Bitmap.Config.ARGB_8888)
        val c = Canvas(out)
        val r = src.width * 0.06f
        val rect = RectF(0.5f, 0.5f, src.width - 0.5f, src.height - 0.5f)
        val p = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        p.shader = BitmapShader(src, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP)
        c.drawRoundRect(rect, r, r, p)
        p.shader = null
        p.style = Paint.Style.STROKE
        p.strokeWidth = maxOf(1f, src.width / 160f)
        p.color = 0x22000000
        c.drawRoundRect(rect, r, r, p)
        src.recycle()
        out
    }.getOrNull()

    /** "Hari ini, 10.21", "Kemarin, 08.05", atau "12 Okt 2026". */
    fun dateLabel(time: Long): String {
        val now = Calendar.getInstance()
        val c = Calendar.getInstance().apply { timeInMillis = time }
        val clock = String.format(java.util.Locale.US, "%02d.%02d", c.get(Calendar.HOUR_OF_DAY), c.get(Calendar.MINUTE))
        val sameYear = now.get(Calendar.YEAR) == c.get(Calendar.YEAR)
        val dayDiff = if (sameYear) now.get(Calendar.DAY_OF_YEAR) - c.get(Calendar.DAY_OF_YEAR) else 99
        return when (dayDiff) {
            0 -> "Hari ini, $clock"
            1 -> "Kemarin, $clock"
            else -> "${c.get(Calendar.DAY_OF_MONTH)} ${IndoDate.monthsShort[c.get(Calendar.MONTH)]}" +
                (if (sameYear) ", $clock" else " ${c.get(Calendar.YEAR)}")
        }
    }

    /** Lebar widget dalam piksel (untuk gambar halaman yang tajam). */
    fun widgetWidthPx(context: Context, widgetId: Int): Int {
        val opts = runCatching { AppWidgetManager.getInstance(context).getAppWidgetOptions(widgetId) }.getOrNull()
        val dp = max(opts?.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH) ?: 0, opts?.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH) ?: 0)
        val px = if (dp > 0) (dp * context.resources.displayMetrics.density).toInt() else 720
        return px.coerceIn(320, 1100)
    }

    // ---------- yang dibuka saat diketuk ----------

    private fun openAppIntent(context: Context): PendingIntent {
        val i = Intent(context, MainActivity::class.java)
            .setAction(Intent.ACTION_MAIN)
            .addCategory(Intent.CATEGORY_LAUNCHER)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return PendingIntent.getActivity(context, 1, i, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    }

    private fun newNoteIntent(context: Context): PendingIntent {
        val i = Intent(context, MainActivity::class.java)
            .setAction(ACTION_NEW)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        return PendingIntent.getActivity(context, 2, i, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    }

    fun openNoteIntent(context: Context, noteId: String): PendingIntent {
        val i = Intent(context, MainActivity::class.java)
            .setAction(ACTION_OPEN)
            .setData(Uri.parse("hananote://note/$noteId"))
            .putExtra(EXTRA_NOTE, noteId)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        return PendingIntent.getActivity(context, noteId.hashCode(), i, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    }

    /**
     * Templat ketukan butir daftar: lewat aktivitas tak terlihat, yang membuka catatan atau mencentang kotak
     * tanpa membuka aplikasi. Isi ketukannya ditambahkan tiap butir.
     */
    private fun itemTemplate(context: Context, request: Int): PendingIntent {
        val i = Intent(context, WidgetActionActivity::class.java)
            .setAction(ACTION_ITEM)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_ANIMATION)
        val mutable = if (Build.VERSION.SDK_INT >= 31) PendingIntent.FLAG_MUTABLE else 0
        return PendingIntent.getActivity(context, request, i, PendingIntent.FLAG_UPDATE_CURRENT or mutable)
    }

    fun openFill(noteId: String, page: Int = -1): Intent =
        Intent().putExtra(EXTRA_OP, OP_OPEN).putExtra(EXTRA_NOTE, noteId).putExtra(EXTRA_PAGE, page)

    fun toggleFill(e: Repository.CheckEdit): Intent = Intent()
        .putExtra(EXTRA_OP, OP_TOGGLE)
        .putExtra(EXTRA_NOTE, e.noteId)
        .putExtra(EXTRA_PAGE_ID, e.pageId)
        .putExtra(EXTRA_STICKY, e.sticky)
        .putExtra(EXTRA_INDEX, e.index)
        .putExtra(EXTRA_ITEM, e.itemId)
        .putExtra(EXTRA_AT, e.at)
        .putExtra(EXTRA_TEXT, e.text)

    fun editFrom(i: Intent): Repository.CheckEdit? {
        val note = i.getStringExtra(EXTRA_NOTE) ?: return null
        val page = i.getStringExtra(EXTRA_PAGE_ID) ?: return null
        val text = i.getStringExtra(EXTRA_TEXT) ?: return null
        return Repository.CheckEdit(
            note, page, i.getBooleanExtra(EXTRA_STICKY, false), i.getIntExtra(EXTRA_INDEX, -1),
            i.getLongExtra(EXTRA_ITEM, 0L), i.getIntExtra(EXTRA_AT, -1), text,
        )
    }

    /** Centang dari widget: ubah berkas halamannya, lalu perbarui widget dan gambar kecil catatannya. */
    fun toggle(context: Context, e: Repository.CheckEdit, done: () -> Unit) {
        val repo = Repository.get(context)
        repo.writeScope.launch {
            try {
                if (repo.toggleCheckInFile(e)) {
                    refreshNote(context, e.noteId)
                    val first = repo.loadPages(e.noteId).firstOrNull()
                    if (first != null && first.id == e.pageId) {
                        val content = repo.loadContent(e.noteId, first.id)
                        val bmp = InkRenderer.renderThumbnail(first, content, 360, repo.noteDir(e.noteId))
                        repo.saveThumb(e.noteId, bmp)
                        bmp.recycle()
                    }
                }
            } catch (t: Throwable) {
                CrashLog.nonFatal("Centang dari widget gagal", t)
            } finally {
                done()
            }
        }
    }
}

/** Widget "Catatan terbaru". */
class RecentNotesWidget : AppWidgetProvider() {
    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        val pending = goAsync()
        Executors.newSingleThreadExecutor().execute {
            try {
                for (id in appWidgetIds) HanaWidgets.update(context, appWidgetManager, id, recent = true)
            } finally {
                pending.finish()
            }
        }
    }
}

/** Widget "Satu catatan". */
class NoteWidget : AppWidgetProvider() {
    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        val pending = goAsync()
        Executors.newSingleThreadExecutor().execute {
            try {
                for (id in appWidgetIds) HanaWidgets.update(context, appWidgetManager, id, recent = false)
            } finally {
                pending.finish()
            }
        }
    }

    override fun onAppWidgetOptionsChanged(context: Context, appWidgetManager: AppWidgetManager, appWidgetId: Int, newOptions: Bundle?) {
        // Ukuran widget diubah: gambar halaman dibuat ulang selebar widget.
        appWidgetManager.notifyAppWidgetViewDataChanged(intArrayOf(appWidgetId), R.id.note_list)
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == HanaWidgets.ACTION_TOGGLE) {
            val e = HanaWidgets.editFrom(intent) ?: return
            val pending = goAsync()
            HanaWidgets.toggle(context.applicationContext, e) { pending.finish() }
            return
        }
        super.onReceive(context, intent)
    }

    override fun onDeleted(context: Context, appWidgetIds: IntArray) {
        HanaWidgets.forget(context, appWidgetIds)
    }
}

/**
 * Aktivitas tak terlihat untuk ketukan butir widget: mencentang kotak (tanpa membuka aplikasi) atau membuka
 * catatan di halaman yang diketuk. Langsung selesai.
 */
class WidgetActionActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val i = intent
        try {
            when (i?.getStringExtra(HanaWidgets.EXTRA_OP)) {
                HanaWidgets.OP_TOGGLE -> {
                    val b = Intent(this, NoteWidget::class.java).setAction(HanaWidgets.ACTION_TOGGLE)
                    i.extras?.let { b.putExtras(it) }
                    sendBroadcast(b)
                }
                HanaWidgets.OP_OPEN -> {
                    val note = i.getStringExtra(HanaWidgets.EXTRA_NOTE)
                    if (note != null) {
                        startActivity(
                            Intent(this, MainActivity::class.java)
                                .setAction(HanaWidgets.ACTION_OPEN)
                                .setData(Uri.parse("hananote://note/$note"))
                                .putExtra(HanaWidgets.EXTRA_NOTE, note)
                                .putExtra(HanaWidgets.EXTRA_PAGE, i.getIntExtra(HanaWidgets.EXTRA_PAGE, -1))
                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                        )
                    }
                }
            }
        } catch (t: Throwable) {
            CrashLog.nonFatal("Ketukan widget gagal", t)
        }
        finish()
        @Suppress("DEPRECATION")
        overridePendingTransition(0, 0)
    }
}

// ---------- isi daftar "Catatan terbaru" ----------

class RecentNotesService : RemoteViewsService() {
    override fun onGetViewFactory(intent: Intent): RemoteViewsFactory = RecentFactory(applicationContext)
}

private class RecentFactory(private val context: Context) : RemoteViewsService.RemoteViewsFactory {
    private class Row(val note: Note, val snippet: String)
    private var rows: List<Row> = emptyList()

    override fun onCreate() = Unit
    override fun onDestroy() = Unit

    override fun onDataSetChanged() {
        rows = runCatching {
            val repo = Repository.get(context)
            repo.notes.value.sortedByDescending { it.updated }.take(40).map { n ->
                Row(n, runCatching { repo.snippet(n.id) }.getOrDefault(""))
            }
        }.getOrDefault(emptyList())
    }

    override fun getCount() = rows.size

    override fun getViewAt(position: Int): RemoteViews {
        val rv = RemoteViews(context.packageName, R.layout.widget_recent_item)
        val r = rows.getOrNull(position) ?: return rv
        val repo = Repository.get(context)
        rv.setTextViewText(R.id.item_title, r.note.title)
        val pages = if (r.note.pageCount > 1) " · ${r.note.pageCount} halaman" else ""
        rv.setTextViewText(R.id.item_date, HanaWidgets.dateLabel(r.note.updated) + pages)
        if (r.snippet.isBlank()) {
            rv.setViewVisibility(R.id.item_snippet, View.GONE)
        } else {
            rv.setViewVisibility(R.id.item_snippet, View.VISIBLE)
            rv.setTextViewText(R.id.item_snippet, r.snippet)
        }
        val bmp = HanaWidgets.thumbnail(repo, r.note, 200)
        if (bmp != null) rv.setImageViewBitmap(R.id.item_thumb, bmp) else rv.setImageViewResource(R.id.item_thumb, R.drawable.widget_page)
        rv.setOnClickFillInIntent(R.id.item_root, HanaWidgets.openFill(r.note.id))
        return rv
    }

    override fun getLoadingView(): RemoteViews? = null
    override fun getViewTypeCount() = 1
    override fun getItemId(position: Int): Long = rows.getOrNull(position)?.note?.id?.hashCode()?.toLong() ?: position.toLong()
    override fun hasStableIds() = true
}

// ---------- isi daftar "Satu catatan" ----------

class NoteWidgetService : RemoteViewsService() {
    override fun onGetViewFactory(intent: Intent): RemoteViewsFactory =
        NoteFactory(applicationContext, intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID))
}

/**
 * Isi widget satu catatan: daftar centang (bisa dicentang), lalu halaman-halamannya sebagai gambar selebar widget.
 * Gambar halaman dipotong jadi beberapa lajur supaya tiap butir tetap kecil (batas kiriman antar-aplikasi).
 */
private class NoteFactory(private val context: Context, private val widgetId: Int) : RemoteViewsService.RemoteViewsFactory {
    private sealed class Row
    private class Check(val edit: Repository.CheckEdit, val label: String, val done: Boolean) : Row()
    private class Label(val text: String, val page: Int) : Row()
    private class Strip(val page: Int, val y0: Int, val y1: Int) : Row()

    private var noteId: String? = null
    private var pages: List<PageMeta> = emptyList()
    private var rows: List<Row> = emptyList()
    private var width = 720
    /** Bagian halaman yang berisi (satuan halaman), per halaman. */
    private val crops = HashMap<String, RectF>()
    /** Gambar halaman utuh selebar widget, paling banyak dua yang disimpan. */
    // Tidak di-recycle sendiri: lajur lain mungkin sedang dipotong dari gambar yang sama.
    private val rendered = object : LinkedHashMap<String, Bitmap>(4, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Bitmap>?): Boolean = size > 2
    }

    override fun onCreate() = Unit

    override fun onDestroy() {
        synchronized(rendered) { rendered.clear() }
    }

    override fun onDataSetChanged() {
        synchronized(rendered) { rendered.clear() }
        crops.clear()
        val out = ArrayList<Row>()
        try {
            val repo = Repository.get(context)
            val id = HanaWidgets.noteFor(context, widgetId)
            val note = id?.let { repo.note(it) }
            noteId = note?.id
            if (note == null) {
                pages = emptyList()
                rows = emptyList()
                return
            }
            width = HanaWidgets.widgetWidthPx(context, widgetId)
            pages = repo.loadPages(note.id)
            val checks = ArrayList<Check>()
            val pageRows = ArrayList<Row>()
            val step = stripHeight(width)
            for ((i, p) in pages.withIndex()) {
                if (i >= MAX_PAGES) break
                val content = runCatching { repo.loadContent(note.id, p.id) }.getOrNull() ?: continue
                if (checks.size < MAX_CHECKS) collectChecks(note.id, p, content, checks)
                if (pages.size > 1) pageRows.add(Label("Halaman ${i + 1}", i))
                val crop = contentBounds(p, content)
                crops[p.id] = crop
                val k = width / p.width
                val top = (crop.top * k).toInt().coerceAtLeast(0)
                val bottom = ceil(crop.bottom * k).toInt().coerceAtMost(ceil(p.height * k).toInt())
                var y = top
                while (y < bottom) {
                    val e = min(bottom, y + step)
                    pageRows.add(Strip(i, y, e))
                    y = e
                }
            }
            if (checks.isNotEmpty()) {
                val open = checks.count { !it.done }
                out.add(Label(if (open == 0) "Daftar centang · semua selesai" else "Daftar centang · $open belum", -1))
                out.addAll(checks)
            }
            out.addAll(pageRows)
            if (pages.size > MAX_PAGES) out.add(Label("Ketuk untuk membuka ${pages.size - MAX_PAGES} halaman lainnya", MAX_PAGES))
        } catch (t: Throwable) {
            CrashLog.nonFatal("Isi widget catatan gagal dimuat", t)
        }
        rows = out
    }

    /** Tinggi lajur supaya satu gambar lajur tetap sekitar 450 KB. */
    private fun stripHeight(w: Int) = (450_000 / (w * 4)).coerceIn(60, 400)

    /** Butir daftar centang dari teks ketik dan stiker, urut dari atas ke bawah. */
    private fun collectChecks(noteId: String, p: PageMeta, content: PageContent, out: ArrayList<Check>) {
        class Src(val y: Float, val text: String, val sticky: Boolean, val index: Int, val itemId: Long)
        val srcs = ArrayList<Src>()
        content.texts.forEachIndexed { i, t -> if (hasBox(t.text)) srcs.add(Src(t.y, t.text, false, i, 0L)) }
        content.items.forEach { if (it is StickyItem && hasBox(it.text)) srcs.add(Src(it.y, it.text, true, -1, it.id)) }
        for (s in srcs.sortedBy { it.y }) {
            var start = 0
            while (start <= s.text.length) {
                val nl = s.text.indexOf('\n', start)
                val end = if (nl < 0) s.text.length else nl
                val line = s.text.substring(start, end)
                if (line.startsWith(TextLists.BOX) || line.startsWith(TextLists.DONE)) {
                    val label = line.substring(TextLists.BOX.length).trim()
                    if (label.isNotEmpty() && out.size < MAX_CHECKS) {
                        val e = Repository.CheckEdit(noteId, p.id, s.sticky, s.index, s.itemId, start, s.text)
                        out.add(Check(e, label, line.startsWith(TextLists.DONE)))
                    }
                }
                if (nl < 0) break
                start = nl + 1
            }
        }
    }

    private fun hasBox(t: String) = t.indexOf(TextLists.BOX[0]) >= 0 || t.indexOf(TextLists.DONE[0]) >= 0

    /** Bagian halaman yang berisi, dengan sedikit tepi; seluruh halaman bila kosong. */
    private fun contentBounds(p: PageMeta, c: PageContent): RectF {
        val r = RectF()
        var any = false
        fun add(l: Float, t: Float, rr: Float, b: Float) {
            if (!any) { r.set(l, t, rr, b); any = true } else r.union(l, t, rr, b)
        }
        for (s in c.strokes) {
            if (s.tool == id.hananote.data.Tool.ERASER) continue
            val b = s.bounds
            add(b.left, b.top, b.right, b.bottom)
        }
        val tb = RectF()
        for (t in c.texts) {
            InkRenderer.textBounds(t, tb)
            add(tb.left, tb.top, tb.right, tb.bottom)
        }
        for (it in c.items) add(it.x, it.y, it.x + it.w, it.y + it.h)
        // Halaman PDF atau gambar latar: tampilkan utuh.
        if (!any || p.background.startsWith("pdf:") || p.background.startsWith("img:")) {
            return RectF(0f, 0f, p.width, p.height)
        }
        val m = p.width * 0.04f
        return RectF(0f, (r.top - m).coerceAtLeast(0f), p.width, (r.bottom + m).coerceAtMost(p.height))
    }

    private fun pageBitmap(i: Int): Bitmap? {
        val p = pages.getOrNull(i) ?: return null
        val id = noteId ?: return null
        synchronized(rendered) {
            rendered[p.id]?.let { return it }
        }
        val repo = Repository.get(context)
        val content = runCatching { repo.loadContent(id, p.id) }.getOrNull() ?: return null
        val bmp = runCatching { InkRenderer.renderThumbnail(p, content, width, repo.noteDir(id)) }.getOrNull() ?: return null
        synchronized(rendered) { rendered[p.id] = bmp }
        return bmp
    }

    override fun getCount() = rows.size

    override fun getViewAt(position: Int): RemoteViews {
        val pkg = context.packageName
        val id = noteId
        return when (val r = rows.getOrNull(position)) {
            is Check -> {
                val rv = RemoteViews(pkg, R.layout.widget_note_check)
                rv.setImageViewResource(R.id.check_box, if (r.done) R.drawable.ic_widget_box_done else R.drawable.ic_widget_box)
                val s = SpannableString(r.label)
                if (r.done) {
                    s.setSpan(StrikethroughSpan(), 0, s.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                    s.setSpan(ForegroundColorSpan(0xFF8E8A94.toInt()), 0, s.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                }
                rv.setTextViewText(R.id.check_text, s)
                rv.setOnClickFillInIntent(R.id.check_root, HanaWidgets.toggleFill(r.edit))
                rv
            }
            is Label -> {
                val rv = RemoteViews(pkg, R.layout.widget_note_label)
                rv.setTextViewText(R.id.label_text, r.text)
                if (id != null) rv.setOnClickFillInIntent(R.id.label_text, HanaWidgets.openFill(id, r.page))
                rv
            }
            is Strip -> {
                val rv = RemoteViews(pkg, R.layout.widget_note_strip)
                val full = pageBitmap(r.page)
                if (full != null && r.y0 < full.height) {
                    val h = (min(r.y1, full.height) - r.y0).coerceAtLeast(1)
                    val part = runCatching { Bitmap.createBitmap(full, 0, r.y0, full.width, h) }.getOrNull()
                    if (part != null) rv.setImageViewBitmap(R.id.strip_image, part)
                }
                if (id != null) rv.setOnClickFillInIntent(R.id.strip_root, HanaWidgets.openFill(id, r.page))
                rv
            }
            null -> RemoteViews(pkg, R.layout.widget_note_label)
        }
    }

    override fun getLoadingView(): RemoteViews? = null
    override fun getViewTypeCount() = 3
    override fun getItemId(position: Int): Long = position.toLong()
    override fun hasStableIds() = false

    companion object {
        const val MAX_PAGES = 30
        const val MAX_CHECKS = 80
    }
}
