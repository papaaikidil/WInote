package id.hananote.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import id.hananote.data.Backups
import id.hananote.data.Repository
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Cadangan otomatis ke folder di tablet, cadangkan sekarang, simpan ke tempat lain, dan pulihkan. */
@Composable
fun BackupDialog(repo: Repository, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val app = context.applicationContext
    remember { Backups.init(app); 0 }
    val st by Backups.state.collectAsState()
    // Kerja cadangan memakai lingkup aplikasi supaya tetap selesai walau dialog ditutup.
    val work = repo.appScope
    var confirmRestore by remember { mutableStateOf<android.net.Uri?>(null) }

    DisposableEffect(Unit) { onDispose { Backups.clearMessage() } }

    val pickFolder = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { tree ->
        if (tree != null) {
            Backups.setFolder(app, tree)
            work.launch { Backups.backupNow(app) }
        }
    }
    val saveTo = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri ->
        if (uri != null) work.launch { Backups.saveTo(app, uri) }
    }
    val openBackup = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) confirmRestore = uri
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Cadangan") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Folder cadangan di tablet", style = MaterialTheme.typography.labelLarge)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Folder, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(8.dp))
                    Text(
                        st.folderName ?: "Belum dipilih",
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(onClick = { runCatching { pickFolder.launch(null) } }) {
                        Text(if (st.folderUri == null) "Pilih folder" else "Ganti")
                    }
                }
                if (st.folderUri == null) {
                    Text(
                        "Pilih atau buat folder, misalnya “WInote Cadangan” di Dokumen. Folder ini tetap ada walau aplikasi terhapus.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Cadangkan otomatis", style = MaterialTheme.typography.bodyLarge)
                        Text(
                            "Saat aplikasi ditinggalkan, bila ada perubahan.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(checked = st.auto, onCheckedChange = { Backups.setAuto(app, it) })
                }
                if (st.auto) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(selected = st.everyDays == 1, onClick = { Backups.setEveryDays(app, 1) }, label = { Text("Setiap hari") })
                        FilterChip(selected = st.everyDays == 7, onClick = { Backups.setEveryDays(app, 7) }, label = { Text("Setiap minggu") })
                    }
                }
                Text("Simpan berapa cadangan terakhir", style = MaterialTheme.typography.labelLarge)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(3, 5, 10).forEach { n ->
                        FilterChip(selected = st.keep == n, onClick = { Backups.setKeep(app, n) }, label = { Text("$n") })
                    }
                }

                val busy = st.busy
                if (busy != null) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 3.dp)
                        Spacer(Modifier.width(12.dp))
                        Text(busy, style = MaterialTheme.typography.bodyMedium)
                    }
                } else {
                    Text(
                        if (st.last > 0) "Terakhir: ${stamp(st.last)}" + (st.lastName?.let { "\n$it" } ?: "") else "Belum ada cadangan.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                st.message?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary) }
                st.error?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error) }

                Button(
                    onClick = {
                        if (st.folderUri == null) runCatching { pickFolder.launch(null) }
                        else work.launch { Backups.backupNow(app) }
                    },
                    enabled = busy == null,
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Cadangkan sekarang") }

                HorizontalDivider()
                Text(
                    "Supaya aman juga bila tablet hilang, sesekali simpan cadangan ke Google Drive: pilih Drive di layar simpan.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedButton(
                    onClick = { runCatching { saveTo.launch(Backups.suggestedName()) } },
                    enabled = busy == null,
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Simpan cadangan ke… (Drive, dll.)") }
                OutlinedButton(
                    onClick = { runCatching { openBackup.launch(arrayOf("application/zip", "application/x-zip-compressed", "application/octet-stream")) } },
                    enabled = busy == null,
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Pulihkan dari cadangan…") }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Tutup") } },
    )

    val pending = confirmRestore
    if (pending != null) {
        AlertDialog(
            onDismissRequest = { confirmRestore = null },
            title = { Text("Pulihkan cadangan?") },
            text = {
                Text(
                    "Catatan yang belum ada di tablet ditambahkan, dan catatan yang versi cadangannya lebih baru diperbarui. " +
                        "Tidak ada catatan yang dihapus.",
                )
            },
            confirmButton = {
                Button(onClick = {
                    confirmRestore = null
                    work.launch { Backups.restore(app, pending) }
                }) { Text("Pulihkan") }
            },
            dismissButton = { TextButton(onClick = { confirmRestore = null }) { Text("Batal") } },
        )
    }
}

private fun stamp(ms: Long): String = SimpleDateFormat("d MMM yyyy, HH.mm", Locale("id", "ID")).format(Date(ms))
