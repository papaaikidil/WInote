package id.goresan.ui

import android.widget.MediaController
import android.widget.VideoView
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView

/** Putar ulang time-lapse karya (video berulang) dengan tombol ekspor. */
@Composable
fun TimeLapsePlayer(state: AppState) {
    val f = state.tlVideo ?: return
    val top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    Box(
        Modifier
            .fillMaxSize()
            .background(Color(0xF0000000))
            .pointerInput(Unit) { detectTapGestures { } },
    ) {
        Column(Modifier.fillMaxSize().padding(top = top)) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                TextButton(onClick = { state.tlVideo = null }) { Text("Tutup", color = Color.White) }
                Spacer(Modifier.weight(1f))
                Text("Time-lapse", color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 17.sp)
                Spacer(Modifier.weight(1f))
                TextButton(onClick = { state.exportTimeLapse(30) }) { Text("Ekspor 30 detik", color = Accent) }
                TextButton(onClick = { state.exportTimeLapse(null) }) { Text("Ekspor lengkap", color = Accent) }
            }
            Box(Modifier.weight(1f).fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                AndroidView(
                    factory = { c ->
                        VideoView(c).apply {
                            setMediaController(MediaController(c).also { it.setAnchorView(this) })
                            setOnPreparedListener { mp -> mp.isLooping = true; start() }
                            setVideoPath(f.path)
                        }
                    },
                    update = { v -> if (v.tag != f.path) { v.tag = f.path; v.setVideoPath(f.path) } },
                    onRelease = { v -> v.stopPlayback() },
                )
            }
        }
    }
}
