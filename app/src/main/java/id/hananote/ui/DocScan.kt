package id.hananote.ui

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import com.google.mlkit.vision.documentscanner.GmsDocumentScannerOptions
import com.google.mlkit.vision.documentscanner.GmsDocumentScanning
import com.google.mlkit.vision.documentscanner.GmsDocumentScanningResult
import id.hananote.data.CrashLog

/**
 * Pindai dokumen dengan kamera: pemindai ML Kit (tepi kertas dikenali otomatis, bisa dipotong, diluruskan, dan
 * difilter) menghasilkan satu PDF. [onPdf] menerima PDF itu; [onError] pesan bila pemindai tidak bisa dipakai.
 * Hasil: fungsi untuk membuka pemindai.
 */
@Composable
fun rememberDocScanner(onPdf: (Uri, Int) -> Unit, onError: (String) -> Unit): () -> Unit {
    val context = LocalContext.current
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { result ->
        if (result.resultCode != Activity.RESULT_OK) return@rememberLauncherForActivityResult
        val scan = GmsDocumentScanningResult.fromActivityResultIntent(result.data)
        val pdf = scan?.pdf
        if (pdf == null) {
            onError("Hasil pindaian tidak terbaca.")
            return@rememberLauncherForActivityResult
        }
        CrashLog.note("pindai dokumen: ${pdf.pageCount} halaman")
        onPdf(pdf.uri, pdf.pageCount)
    }
    return {
        val activity = context.findActivity()
        if (activity == null) {
            onError("Pemindai tidak bisa dibuka dari sini.")
        } else {
            val options = GmsDocumentScannerOptions.Builder()
                .setGalleryImportAllowed(true)
                .setPageLimit(50)
                .setResultFormats(GmsDocumentScannerOptions.RESULT_FORMAT_PDF)
                .setScannerMode(GmsDocumentScannerOptions.SCANNER_MODE_FULL)
                .build()
            GmsDocumentScanning.getClient(options).getStartScanIntent(activity)
                .addOnSuccessListener { sender -> launcher.launch(IntentSenderRequest.Builder(sender).build()) }
                .addOnFailureListener { e ->
                    CrashLog.note("pemindai gagal: ${e.javaClass.simpleName}")
                    onError("Pemindai dokumen belum siap. Pastikan Layanan Google Play aktif dan ada internet untuk unduhan pertama.")
                }
        }
    }
}

private fun Context.findActivity(): Activity? {
    var c: Context? = this
    while (c is ContextWrapper) {
        if (c is Activity) return c
        c = c.baseContext
    }
    return null
}
