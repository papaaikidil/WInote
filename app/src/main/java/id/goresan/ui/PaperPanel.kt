package id.goresan.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import id.goresan.engine.Paper
import java.util.Locale

/**
 * Panel Kertas kanvas: jenis tekstur kertas (cat air, sketsa, kanvas, bergaris, …) atau gambar sendiri, kekuatan,
 * dan skala. Kanvas tetap terlihat (dan bisa digeser/diperbesar) supaya hasilnya langsung tampak.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PaperPanel(state: AppState) {
    Box(Modifier.fillMaxSize()) {
        Surface(
            color = PanelBg,
            shape = RoundedCornerShape(18.dp),
            shadowElevation = 8.dp,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .windowInsetsPadding(WindowInsets.navigationBars)
                .padding(12.dp)
                .widthIn(max = 820.dp)
                .fillMaxWidth()
                .pointerInput(Unit) { detectTapGestures { } },
        ) {
            BoxWithConstraints {
                val wide = maxWidth >= 600.dp
                Column(Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 14.dp, vertical = 12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Kertas kanvas", color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
                        Spacer(Modifier.weight(1f))
                        TextButton(onClick = { state.endPaperEdit(false) }) { Text("Batal", color = Muted) }
                        Button(
                            onClick = { state.endPaperEdit(true) },
                            colors = ButtonDefaults.buttonColors(containerColor = Accent),
                        ) { Text("Selesai") }
                    }
                    Spacer(Modifier.height(6.dp))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        for (t in 0..Paper.CUSTOM) PaperTile(state, t)
                    }
                    Spacer(Modifier.height(8.dp))
                    val strength: @Composable () -> Unit = {
                        LabeledSlider(
                            "Kekuatan tekstur", state.paperStrength,
                            { state.setPaper(state.paperType, it, state.paperScale) }, 0f..1f,
                        )
                    }
                    val scale: @Composable () -> Unit = {
                        LabeledSlider(
                            "Skala", state.paperScale,
                            { state.setPaper(state.paperType, state.paperStrength, (it * 20f).let { v -> kotlin.math.round(v) } / 20f) },
                            0.25f..4f,
                            { "×" + String.format(Locale.US, "%.2f", it).trimEnd('0').trimEnd('.').replace('.', ',') },
                        )
                    }
                    if (wide) {
                        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                            Box(Modifier.weight(1f)) { strength() }
                            Box(Modifier.weight(1f)) { scale() }
                        }
                    } else {
                        strength()
                        scale()
                    }
                    Text(
                        if (!state.bgVisible) {
                            "Latar sedang disembunyikan, jadi kertas tidak tampak. Tampilkan latar di panel Layer."
                        } else {
                            "Kertas ikut terlihat di hasil ekspor PNG/JPG/PDF. Kuas cat air mengendap mengikuti lekukan kertas. " +
                                "Gambar sendiri: foto kertas (PNG/JPG) atau pola .pat."
                        },
                        color = Muted, fontSize = 12.sp, modifier = Modifier.padding(top = 4.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun PaperTile(state: AppState, t: Int) {
    val on = state.paperType == t
    Column(
        Modifier
            .width(76.dp)
            .clip(RoundedCornerShape(10.dp))
            .clickable { state.choosePaper(t) }
            .padding(2.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier
                .size(68.dp)
                .clip(RoundedCornerShape(9.dp))
                .background(Color(state.bgColor or 0xFF000000.toInt()))
                .border(if (on) 2.5.dp else 1.dp, if (on) Accent else Color(0x44FFFFFF), RoundedCornerShape(9.dp)),
            contentAlignment = Alignment.Center,
        ) {
            val img = state.paperThumbs[t]
            when {
                t == Paper.CUSTOM -> Icon(Icons.Filled.AddPhotoAlternate, null, tint = Color(0xFF6B6B73), modifier = Modifier.size(30.dp))
                img != null -> Image(img, null, contentScale = ContentScale.Crop, modifier = Modifier.size(68.dp))
            }
        }
        Text(
            Paper.names[t], color = if (on) Color.White else Muted, fontSize = 11.sp, maxLines = 2,
            textAlign = TextAlign.Center, modifier = Modifier.padding(top = 3.dp),
        )
    }
}
