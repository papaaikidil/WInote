package id.hananote.ink

import android.content.Context
import android.graphics.Bitmap
import com.google.android.gms.common.moduleinstall.ModuleInstall
import com.google.android.gms.common.moduleinstall.ModuleInstallRequest
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.segmentation.subject.SubjectSegmentation
import com.google.mlkit.vision.segmentation.subject.SubjectSegmenterOptions
import kotlinx.coroutines.delay

/**
 * Hapus latar gambar.
 *
 * Cara otomatis memakai ML Kit Subject Segmentation: objek utama (orang, hewan, barang) dikenali
 * walau latarnya ramai. Komponennya diunduh sekali lewat Layanan Google Play. Bila tidak tersedia,
 * [ColorEraser] dipakai untuk latar polos.
 */
object BackgroundRemover {

    /** Hasil cara otomatis, atau null beserta alasannya bila tidak bisa. */
    class AiResult(val bitmap: Bitmap?, val problem: String?)

    suspend fun auto(context: Context, source: Bitmap, onStatus: (String) -> Unit): AiResult {
        val segmenter = try {
            SubjectSegmentation.getClient(SubjectSegmenterOptions.Builder().enableForegroundBitmap().build())
        } catch (e: Throwable) {
            return AiResult(null, "Hapus latar otomatis butuh Layanan Google Play.")
        }
        try {
            val installer = ModuleInstall.getClient(context.applicationContext)
            val available = runCatching { installer.areModulesAvailable(segmenter).awaitResult().areModulesAvailable() }
                .getOrElse { return AiResult(null, "Hapus latar otomatis butuh Layanan Google Play.") }
            if (!available) {
                onStatus("Mengunduh komponen hapus latar (sekali saja, perlu internet)…")
                runCatching {
                    installer.installModules(ModuleInstallRequest.newBuilder().addApi(segmenter).build()).awaitResult()
                }.onFailure { return AiResult(null, "Komponen hapus latar belum bisa diunduh. Sambungkan internet, lalu coba lagi.") }
                var ready = false
                for (i in 0 until 90) {
                    ready = runCatching { installer.areModulesAvailable(segmenter).awaitResult().areModulesAvailable() }.getOrDefault(false)
                    if (ready) break
                    delay(1000)
                }
                if (!ready) return AiResult(null, "Komponen hapus latar masih diunduh. Coba lagi sebentar lagi.")
            }
            onStatus("Mencari objek utama…")
            val result = segmenter.process(InputImage.fromBitmap(source, 0)).awaitResult()
            val fg = result.foregroundBitmap ?: return AiResult(null, "Objek utama tidak ditemukan di gambar ini.")
            return AiResult(fg, null)
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Throwable) {
            return AiResult(null, "Hapus latar otomatis gagal di gambar ini.")
        } finally {
            runCatching { segmenter.close() }
        }
    }

    /** Hapus latar polos berdasarkan warna (tanpa internet). */
    fun byColor(source: Bitmap, tolerance: Float): Bitmap {
        val w = source.width
        val h = source.height
        val px = IntArray(w * h)
        source.getPixels(px, 0, w, 0, 0, w, h)
        val out = ColorEraser.erase(px, w, h, tolerance)
        return Bitmap.createBitmap(out, w, h, Bitmap.Config.ARGB_8888)
    }
}
