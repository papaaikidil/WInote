package id.goresan.ui

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import id.goresan.Diag

/** Catatan teknis untuk dikirim ke Claude saat ada masalah. */
@Composable
fun ReportDialog(state: AppState) {
    if (!state.showReport) return
    val ctx = LocalContext.current
    val clip = LocalClipboardManager.current
    var text by remember { mutableStateOf(Diag.text()) }
    val scroll = rememberScrollState()
    LaunchedEffect(Unit) { scroll.scrollTo(Int.MAX_VALUE) }
    AlertDialog(
        onDismissRequest = { state.showReport = false },
        title = { Text("Laporan teknis") },
        text = {
            Column {
                Text(
                    "Kalau ada masalah (macet, tertutup sendiri, lambat), ketuk Salin lalu tempel di percakapan dengan Claude.",
                    color = Muted, fontSize = 13.sp,
                )
                Spacer(Modifier.height(8.dp))
                Box(
                    Modifier
                        .fillMaxWidth()
                        .heightIn(max = 420.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0xFF0E0E10))
                        .verticalScroll(scroll)
                        .padding(8.dp),
                ) {
                    Text(text.takeLast(30_000), color = Color(0xFFCFCFD6), fontSize = 11.sp, fontFamily = FontFamily.Monospace)
                }
            }
        },
        confirmButton = {
            Row {
                TextButton(onClick = {
                    clip.setText(AnnotatedString(text.takeLast(30_000)))
                    state.showFlash("Laporan disalin")
                }) { Text("Salin") }
                TextButton(onClick = {
                    val send = Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(Intent.EXTRA_TEXT, text.takeLast(30_000))
                    }
                    ctx.startActivity(Intent.createChooser(send, "Bagikan laporan"))
                }) { Text("Bagikan") }
                TextButton(onClick = { state.showReport = false }) { Text("Tutup") }
            }
        },
        dismissButton = {
            TextButton(onClick = { Diag.clear(); text = Diag.text() }) { Text("Kosongkan", color = Color(0xFFFF8A80)) }
        },
    )
}
