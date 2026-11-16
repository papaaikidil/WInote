package id.hananote.ink

import id.hananote.data.PageItem
import id.hananote.data.Stroke
import id.hananote.data.TextItem
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File

/**
 * Papan klip aplikasi untuk tulisan, teks, dan benda: disalin di satu halaman, ditempel di halaman
 * atau catatan mana pun selama aplikasi terbuka.
 */
object InkClipboard {
    /** Salinan lepas (bukan objek yang ada di halaman), urut dari lapisan paling belakang. */
    class Content(val strokes: List<Stroke>, val texts: List<TextItem>, val items: List<PageItem>) {
        val isEmpty: Boolean get() = strokes.isEmpty() && texts.isEmpty() && items.isEmpty()
    }

    /** [sourceDir] folder catatan asal (untuk menyalin gambar dan rekaman), [pageId] halaman asal. */
    class Clip(val content: Content, val sourceDir: File, val pageId: String) {
        /** Berapa kali sudah ditempel di halaman asal (supaya salinan tidak menumpuk persis). */
        var pastes = 0
    }

    private val _clip = MutableStateFlow<Clip?>(null)
    val clip: StateFlow<Clip?> = _clip.asStateFlow()

    fun set(c: Clip?) { _clip.value = c }
}
