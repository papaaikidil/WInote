package id.hananote.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import id.hananote.data.CrashLog
import java.io.File

/**
 * Tawarkan laporan galat untuk dibagikan. [fresh] true bila dibuka otomatis karena aplikasi baru saja
 * tertutup sendiri; false bila dibuka dari pengaturan untuk melihat semua laporan.
 */
@Composable
fun CrashDialog(fresh: Boolean, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val files: List<File> = remember { if (fresh) CrashLog.unseen(context) else CrashLog.all(context) }
    val text = remember(files) { CrashLog.combined(files) }
    fun done() {
        CrashLog.markSeen(context)
        onDismiss()
    }
    AlertDialog(
        onDismissRequest = { done() },
        title = { Text(if (fresh) "WInote sempat tertutup sendiri" else "Laporan galat") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (files.isEmpty()) {
                    Text("Belum ada laporan galat. Bagus!")
                } else {
                    Text(
                        if (fresh) "Maaf, ada galat. Penyebabnya sudah tersimpan. Bagikan laporan ini ke Claude (misalnya salin lalu tempel di obrolan) supaya bisa diperbaiki."
                        else "${files.size} laporan tersimpan di tablet. Bagikan ke Claude supaya penyebabnya bisa diperbaiki.",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Text(
                        text.take(1600) + if (text.length > 1600) "\n…" else "",
                        style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace, fontSize = 10.sp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.fillMaxWidth().heightIn(max = 220.dp).verticalScroll(rememberScrollState()),
                    )
                    Button(onClick = { share(context, text); done() }, modifier = Modifier.fillMaxWidth()) { Text("Bagikan laporan") }
                    OutlinedButton(onClick = { copy(context, text); done() }, modifier = Modifier.fillMaxWidth()) { Text("Salin teks laporan") }
                    if (!fresh) {
                        TextButton(onClick = { CrashLog.clear(context); onDismiss() }) { Text("Hapus semua laporan", color = MaterialTheme.colorScheme.error) }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = { done() }) { Text(if (fresh && files.isNotEmpty()) "Nanti saja" else "Tutup") } },
    )
}

private fun share(context: Context, text: String) {
    val send = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_SUBJECT, "Laporan galat WInote")
        putExtra(Intent.EXTRA_TEXT, text)
    }
    runCatching { context.startActivity(Intent.createChooser(send, "Bagikan laporan galat").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
}

private fun copy(context: Context, text: String) {
    val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager ?: return
    cm.setPrimaryClip(ClipData.newPlainText("Laporan galat WInote", text))
}
