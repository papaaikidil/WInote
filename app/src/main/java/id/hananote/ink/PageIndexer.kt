package id.hananote.ink

import id.hananote.data.PageContent
import id.hananote.data.PageIO
import id.hananote.data.PageMeta
import id.hananote.data.PageText
import id.hananote.data.Repository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * Membuat indeks pencarian tiap halaman: teks ketik ditambah hasil baca tulisan tangan.
 * Berjalan di latar, satu halaman demi satu halaman, supaya tidak mengganggu menulis.
 */
object PageIndexer {
    private val lock = Mutex()

    /** Indeks satu halaman dari isinya saat ini. */
    suspend fun index(repo: Repository, noteId: String, page: PageMeta, content: PageContent) {
        lock.withLock { indexLocked(repo, noteId, page, content) }
    }

    private suspend fun indexLocked(repo: Repository, noteId: String, page: PageMeta, content: PageContent) {
        val typed = PageText.typed(content)
        val ink = content.strokes.filter { it.tool.isWriting && it.size > 0 }
        var complete = true
        var hand = ""
        if (ink.isNotEmpty()) {
            val ready = runCatching { Handwriting.isReady() }.getOrDefault(false)
            if (ready) {
                val sb = StringBuilder()
                var before = ""
                for (line in LineGrouper.group(ink, page)) {
                    val pre = if (before.isEmpty()) "" else "$before "
                    val text = try {
                        Handwriting.recognizeAll(line, pre).firstOrNull()?.trim().orEmpty()
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        complete = false
                        ""
                    }
                    if (text.isNotEmpty()) {
                        sb.append(text).append('\n')
                        before = text
                    }
                }
                hand = sb.toString().trimEnd()
            } else {
                complete = false
            }
        }
        val composed = PageText.compose(typed, hand, complete)
        // Ditulis lewat jalur simpan, jadi selalu sesudah isi halaman yang sedang disimpan.
        withContext(repo.writeDispatcher) { repo.writeIndex(noteId, page.id, composed) }
    }

    /** Lengkapi indeks semua halaman yang belum punya atau sudah tertinggal. */
    suspend fun indexMissing(repo: Repository) {
        val ready = runCatching { Handwriting.isReady() }.getOrDefault(false)
        val todo = repo.pagesNeedingIndex(ready)
        for ((noteId, page) in todo) {
            val content = withContext(repo.writeDispatcher) { PageIO.read(repo.pageFile(noteId, page.id)) }
            lock.withLock { indexLocked(repo, noteId, page, content) }
        }
    }
}
