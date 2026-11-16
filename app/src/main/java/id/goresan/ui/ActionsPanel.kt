package id.goresan.ui

import android.content.Intent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt

@Composable
private fun SectionTitle(t: String) {
    Text(t, color = Accent, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 12.dp, bottom = 4.dp))
}

@Composable
private fun ActionRow(title: String, sub: String? = null, onClick: () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .clickable { onClick() }
            .padding(horizontal = 10.dp, vertical = 9.dp),
    ) {
        Text(title, color = Color.White, fontSize = 14.sp)
        if (sub != null) Text(sub, color = Muted, fontSize = 12.sp)
    }
}

@Composable
private fun ToggleRow(title: String, sub: String?, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .clickable { onChange(!checked) }
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, color = Color.White, fontSize = 14.sp)
            if (sub != null) Text(sub, color = Muted, fontSize = 12.sp)
        }
        Switch(checked = checked, onCheckedChange = onChange, colors = SwitchDefaults.colors(checkedTrackColor = Accent))
    }
}

@Composable
fun ActionsPanel(state: AppState, modifier: Modifier) {
    val ctx = LocalContext.current
    var rename by remember { mutableStateOf(false) }
    var dpiDialog by remember { mutableStateOf(false) }
    Surface(
        color = PanelBg,
        shape = RoundedCornerShape(18.dp),
        shadowElevation = 8.dp,
        modifier = modifier.width(380.dp).heightIn(max = 680.dp),
    ) {
        Column(Modifier.verticalScroll(rememberScrollState()).padding(14.dp)) {
            Text("Aksi", color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 17.sp)

            SectionTitle("Tambah")
            ActionRow("Tambah teks", "Font komik, artistik, eksklusif; bisa melengkung, bergelombang, melingkar, atau mengikuti jalur") {
                state.startText()
            }
            ActionRow("Sisipkan foto", "Jadi layer baru, lalu bisa dipindah dan diubah ukurannya") {
                state.panel = Panel.NONE
                state.pickPhoto?.invoke()
            }

            SectionTitle("Salin & tempel")
            Text(
                "Juga lewat geser tiga jari ke bawah di kanvas. Dengan pilihan aktif, hanya isi pilihan yang disalin.",
                color = Muted, fontSize = 11.sp, modifier = Modifier.padding(horizontal = 10.dp, vertical = 2.dp),
            )
            ActionRow("Potong", "Isi layer aktif dipindah ke papan klip") { state.panel = Panel.NONE; state.clipCopy(cut = true) }
            ActionRow("Salin", "Isi layer aktif ke papan klip (juga bisa ditempel di aplikasi lain)") { state.panel = Panel.NONE; state.clipCopy() }
            ActionRow("Salin semua", "Gabungan semua layer yang terlihat") { state.panel = Panel.NONE; state.clipCopy(merged = true) }
            ActionRow("Tempel", "Jadi layer baru; gambar dari aplikasi lain bisa langsung dipindah dan diubah ukurannya") {
                state.panel = Panel.NONE
                state.clipPaste()
            }

            SectionTitle("Bagikan & ekspor")
            ActionRow("Simpan PNG ke Galeri", "Kualitas penuh") { state.panel = Panel.NONE; state.exportImage(jpg = false, transparent = false) }
            ActionRow("Simpan PNG transparan", "Tanpa warna latar") { state.panel = Panel.NONE; state.exportImage(jpg = false, transparent = true) }
            ActionRow("Simpan JPG ke Galeri", "File lebih kecil, tanpa transparansi") { state.panel = Panel.NONE; state.exportImage(jpg = true, transparent = false) }
            ActionRow("Simpan PDF", "Satu halaman, resolusi penuh. Disimpan di Download/WInote") { state.panel = Panel.NONE; state.exportPdf() }
            ActionRow("Ekspor PSD berlapis", "Untuk Photoshop, Krita, dll. Disimpan di Download/WInote") { state.panel = Panel.NONE; state.exportPsd() }
            ActionRow("Simpan TIFF", "8 bit, DPI karya; transparan bila latar disembunyikan. Disimpan di Download/WInote") { state.exportTiff() }
            ActionRow("Ekspor tiap layer: PNG", "Satu PNG transparan per layer ke Galeri › Pictures/WInote") { state.exportLayers(pdf = false) }
            ActionRow("Ekspor tiap layer: PDF", "Satu halaman per layer (paling bawah = halaman 1). Disimpan di Download/WInote") {
                state.exportLayers(pdf = true)
            }
            ActionRow("Ekspor animasi GIF", "Tiap layer atau grup jadi satu frame (pengaturan dari Animation Assist), ke Galeri › Pictures/WInote") {
                state.exportAnim(gif = true)
            }
            ActionRow("Ekspor animasi MP4", "Tiap layer atau grup jadi satu frame, video ke Galeri › Movies/WInote") { state.exportAnim(gif = false) }
            ActionRow("Ekspor animasi PNG (APNG)", "Warna penuh, tepi halus, transparan bila latar disembunyikan; ke Galeri › Pictures/WInote") {
                state.exportApng()
            }
            if (state.is3D) {
                ActionRow("Ekspor model 3D (GLB)", "Model + tekstur dalam satu berkas, untuk Blender dan penampil 3D. Disimpan di Download/WInote") {
                    state.exportGlb()
                }
            }
            if (state.profile == id.goresan.engine.ColorProfile.CMYK) {
                ActionRow("Ekspor TIFF CMYK", "Untuk percetakan: tinta C, M, Y, K, DPI karya. Disimpan di Download/WInote") { state.exportCmykTiff() }
            }
            ActionRow("Bagikan gambar…", "Kirim ke WhatsApp, Drive, dan lainnya") {
                state.panel = Panel.NONE
                state.sharePng { intent: Intent -> ctx.startActivity(intent) }
            }

            SectionTitle("Time-lapse")
            ToggleRow(
                "Rekam time-lapse",
                "Tiap goresan direkam jadi video proses menggambar (tersimpan bersama karya)",
                state.tlOn,
            ) { state.setTimeLapse(it) }
            ActionRow("Putar ulang time-lapse", "Lihat proses menggambar dari awal") { state.playTimeLapse() }
            ActionRow("Ekspor time-lapse lengkap", "Video MP4 ke Galeri › Movies/WInote") { state.panel = Panel.NONE; state.exportTimeLapse(null) }
            ActionRow("Ekspor time-lapse 30 detik", "Dipercepat jadi paling lama 30 detik") { state.panel = Panel.NONE; state.exportTimeLapse(30) }
            ActionRow("Hapus rekaman time-lapse", "Rekaman mulai lagi dari keadaan sekarang") { state.clearTimeLapse() }

            SectionTitle("Animasi & halaman")
            ToggleRow(
                "Animation Assist",
                "Tiap layer atau grup jadi satu frame: garis waktu, onion skin, putar animasi",
                state.assist == 1,
            ) { state.switchAssist(if (it) 1 else 0) }
            ToggleRow(
                "Page Assist",
                "Tiap layer atau grup jadi satu halaman, untuk komik dan buku",
                state.assist == 2,
            ) { state.switchAssist(if (it) 2 else 0) }
            if (state.assist == 2) {
                ActionRow("Ekspor PDF semua halaman", "Satu PDF berisi tiap halaman. Disimpan di Download/WInote") { state.exportPagesPdf() }
            }

            SectionTitle("Kanvas")
            ToggleRow(
                "Panduan menggambar",
                "Grid 2D, isometrik, perspektif, atau simetri di atas kanvas",
                state.guideOn,
            ) { state.switchGuide(it) }
            ActionRow(
                "Edit panduan menggambar",
                when (state.guide.type) {
                    id.goresan.engine.DrawingGuide.GRID -> "Sekarang: Grid 2D"
                    id.goresan.engine.DrawingGuide.ISO -> "Sekarang: Isometrik"
                    id.goresan.engine.DrawingGuide.PERSP -> "Sekarang: Perspektif"
                    else -> "Sekarang: Simetri"
                } + if (state.activeAssist) " · Bantuan menggambar aktif di layer ini" else "",
            ) { state.startGuideEdit() }
            ActionRow("Kertas kanvas", "Tekstur kertas cat air, sketsa, kanvas, bergaris, atau gambar sendiri · sekarang: " +
                id.goresan.engine.Paper.names[state.paperType.coerceIn(0, id.goresan.engine.Paper.CUSTOM)]) {
                state.startPaperEdit()
            }
            ActionRow("Pangkas & ubah ukuran", "Potong, perluas, atau ubah skala kanvas · sekarang ${state.docW} × ${state.docH} px") {
                state.openCrop()
            }
            ToggleRow(
                "Jendela referensi",
                "Jendela kecil mengambang: kanvas ini atau gambar contekan; tahan di dalamnya untuk mengambil warna",
                state.refOn,
            ) { state.toggleRef(it) }
            ActionRow("Info kanvas", "Ukuran, layer, waktu menggambar, jumlah goresan") { state.openInfo() }
            ActionRow("Ganti nama karya", state.docName) { rename = true }
            ActionRow("Balik tampilan kanvas", "Cermin kiri-kanan untuk mengecek proporsi; gambar tidak berubah") {
                state.canvasView?.flipView()
            }
            ActionRow("Pas ke layar") { state.canvasView?.fit(true) }
            ActionRow("Latar kanvas", "Warna latar dan sembunyikan latar: ketuk Warna latar di panel Layer") {
                state.panel = Panel.LAYERS
            }
            ActionRow("Ubah DPI", "Sekarang ${state.docDpi} DPI · piksel tetap, hanya ukuran cetak & ekspor") { dpiDialog = true }
            val cmW = state.docW / state.docDpi.coerceAtLeast(1).toFloat() * 2.54f
            val cmH = state.docH / state.docDpi.coerceAtLeast(1).toFloat() * 2.54f
            Text(
                "Ukuran ${state.docW} × ${state.docH} px · ${state.docDpi} DPI · cetak " +
                    "${String.format(java.util.Locale.US, "%.1f", cmW).replace('.', ',')} × " +
                    "${String.format(java.util.Locale.US, "%.1f", cmH).replace('.', ',')} cm · maks ${state.layerLimit} layer",
                color = Muted, fontSize = 12.sp, modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
            )

            SectionTitle("Preferensi")
            ToggleRow("Menggambar dengan jari", "Bila mati, satu jari untuk menggeser kanvas", state.prefs.fingerDraws) { state.prefs.fingerDraws = it }
            ToggleRow(
                "Bentuk otomatis",
                "Tahan pena sebentar di ujung goresan: garis, lingkaran, elips, kotak, segitiga, busur jadi rapi",
                state.prefs.quickShape,
            ) { state.prefs.quickShape = it }
            if (state.prefs.quickShape) {
                ToggleRow(
                    "Tahan lebih lama = bentuk presisi",
                    "Sesudah bentuk muncul, tahan pena ±1 detik lagi: lingkaran sempurna, persegi, segitiga sama sisi, sudut per 15°",
                    state.prefs.quickShapeHold,
                ) { state.prefs.quickShapeHold = it }
                ToggleRow(
                    "Bentuk otomatis untuk penghapus",
                    "Hapus mengikuti garis/lingkaran/kotak rapi. Matikan bila penghapusan terasa berhenti saat pena diam",
                    state.prefs.quickShapeErase,
                ) { state.prefs.quickShapeErase = it }
            }
            ToggleRow("Tombol pena = penghapus", "Tahan tombol di pena saat menggores", state.prefs.penButtonErase) { state.prefs.penButtonErase = it }
            ToggleRow("Lingkaran kuas saat pena melayang", null, state.prefs.showCursor) { state.prefs.showCursor = it }
            ToggleRow("Prediksi goresan", "Goresan terasa lebih menempel di ujung pena", state.prefs.prediction) { state.prefs.prediction = it; state.syncTool() }
            ToggleRow("Putar kanvas dengan dua jari", null, state.prefs.rotateCanvas) { state.prefs.rotateCanvas = it }
            ToggleRow("Bilah samping di kanan", "Untuk pengguna tangan kiri", state.prefs.sidebarRight) { state.prefs.sidebarRight = it }
            SectionTitle("Gestur")
            ToggleRow(
                "Gosok tiga jari = bersihkan layer",
                "Gosok tiga jari ke kiri-kanan di kanvas untuk mengosongkan layer aktif (bisa diurungkan)",
                state.prefs.scrubClear,
            ) { state.prefs.scrubClear = it }
            ToggleRow(
                "Geser tiga jari ke bawah = Salin & Tempel",
                "Matikan bila bentrok dengan tangkapan layar tiga jari di HyperOS",
                state.prefs.swipe3Menu,
            ) { state.prefs.swipe3Menu = it }
            ToggleRow(
                "Ketuk empat jari = QuickMenu",
                "Bila mati, ketuk empat jari menyembunyikan alat. QuickMenu juga terbuka dengan menahan tombol pipet",
                state.prefs.fourFingerQuick,
            ) { state.prefs.fourFingerQuick = it }
            ActionRow("Buka QuickMenu", "Enam tombol aksi cepat; tahan tombolnya untuk mengganti isinya") { state.openQuickMenu() }
            LabeledSlider(
                "Kepekaan tekanan pena",
                2f - state.prefs.pressureGamma,
                { state.prefs.pressureGamma = 2f - it; state.syncTool() },
                0.3f..1.6f,
                { v -> if (v < 0.9f) "Berat" else if (v > 1.1f) "Ringan" else "Normal" },
            )
            SectionTitle("Tekanan & penghalusan")
            Text(
                "Berlaku untuk semua kuas, ditambahkan pada pengaturan masing-masing kuas (Studio kuas › Stabilisasi).",
                color = Muted, fontSize = 11.sp, modifier = Modifier.padding(horizontal = 10.dp, vertical = 2.dp),
            )
            LabeledSlider("Jumlah stabilisasi", state.prefs.smoothStab, { state.prefs.smoothStab = it; state.syncTool() })
            LabeledSlider("Penyaring gerak", state.prefs.smoothMotion, { state.prefs.smoothMotion = it; state.syncTool() })
            if (state.prefs.smoothMotion > 0.001f) {
                LabeledSlider("Ekspresi penyaring gerak", state.prefs.smoothExpr, { state.prefs.smoothExpr = it; state.syncTool() })
            }
            SectionTitle("Bantuan")
            ActionRow("Laporan teknis", "Catatan untuk dikirim ke Claude bila ada masalah") {
                state.panel = Panel.NONE
                state.showReport = true
            }
            Text(
                "Gestur: ketuk 2 jari = urungkan · 3 jari = ulangi · 4 jari = sembunyikan alat (atau QuickMenu) · " +
                    "geser 3 jari ke bawah = salin & tempel · gosok 3 jari = bersihkan layer · cubit cepat = pas layar · " +
                    "tahan jari = pipet · tahan tombol pipet = QuickMenu",
                color = Muted, fontSize = 11.sp, modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
            )
        }
    }
    if (dpiDialog) {
        var t by remember { mutableStateOf(state.docDpi.toString()) }
        val v = t.toIntOrNull()
        val ok = v != null && v in 1..9600
        AlertDialog(
            onDismissRequest = { dpiDialog = false },
            title = { Text("Ubah DPI") },
            text = {
                Column {
                    DialogImmersive()
                    OutlinedTextField(
                        value = t, onValueChange = { t = it.filter(Char::isDigit).take(4) }, singleLine = true,
                        label = { Text("DPI") },
                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                            keyboardType = androidx.compose.ui.text.input.KeyboardType.Number,
                        ),
                    )
                    Row(Modifier.padding(top = 8.dp)) {
                        for (p in listOf(72, 150, 300, 600)) {
                            TextButton(onClick = { t = p.toString() }) { Text("$p") }
                        }
                    }
                    Text(
                        "Jumlah piksel tidak berubah. DPI menentukan ukuran cetak, ukuran halaman PDF, dan DPI di file PNG/JPG/PSD.",
                        color = Muted, fontSize = 12.sp,
                    )
                }
            },
            confirmButton = {
                TextButton(enabled = ok, onClick = { state.setDpi(v!!); dpiDialog = false }) { Text("Simpan") }
            },
            dismissButton = { TextButton(onClick = { dpiDialog = false }) { Text("Batal") } },
        )
    }
    if (rename) {
        var name by remember { mutableStateOf(state.docName) }
        AlertDialog(
            onDismissRequest = { rename = false },
            title = { Text("Ganti nama karya") },
            text = { OutlinedTextField(value = name, onValueChange = { name = it }, singleLine = true) },
            confirmButton = {
                TextButton(onClick = { state.renameArtwork(name.ifBlank { state.docName }); rename = false }) { Text("Simpan") }
            },
            dismissButton = { TextButton(onClick = { rename = false }) { Text("Batal") } },
        )
    }
}

@Suppress("unused")
private fun pct(v: Float) = "${(v * 100).roundToInt()}%"
