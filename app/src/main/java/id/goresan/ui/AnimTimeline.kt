package id.goresan.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import id.goresan.engine.AnimSettings
import kotlin.math.roundToInt

/** Garis waktu Animation Assist / jajaran halaman Page Assist, di bawah kanvas. */
@Composable
fun AnimTimeline(state: AppState, modifier: Modifier) {
    val anim = state.assist == 1
    val frames = state.frames()
    val cur = frames.indexOfFirst { state.activeLayerId in it.layers }.coerceAtLeast(0)
    val list = rememberLazyListState()
    LaunchedEffect(cur, frames.size) { if (frames.isNotEmpty()) list.animateScrollToItem(cur) }
    val aspect = if (state.docH > 0) state.docW.toFloat() / state.docH else 1f
    val th = 54.dp
    val tw = (54f * aspect).coerceIn(34f, 110f).dp
    val paper = if (state.bgVisible) Color(state.bgColor) else Color.White

    Surface(color = PanelBg, shape = RoundedCornerShape(18.dp), shadowElevation = 6.dp, modifier = modifier.widthIn(max = 980.dp)) {
        Row(Modifier.padding(horizontal = 8.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            if (anim) {
                IconButton(
                    onClick = { state.animPlay(!state.animPlaying) },
                    modifier = Modifier.semantics { contentDescription = if (state.animPlaying) "Jeda animasi" else "Putar animasi" },
                ) {
                    Icon(if (state.animPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow, null, tint = Color.White)
                }
                IconButton(
                    onClick = { state.animSettingsOpen = !state.animSettingsOpen },
                    modifier = Modifier.semantics { contentDescription = "Pengaturan animasi" },
                ) {
                    Icon(Icons.Filled.Settings, null, tint = if (state.animSettingsOpen) Accent else Color.White)
                }
            } else {
                Text("Halaman", color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, modifier = Modifier.padding(horizontal = 8.dp))
            }
            TextButton(onClick = { state.addFrame() }) {
                Icon(Icons.Filled.Add, null, tint = Accent, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(4.dp))
                Text(if (anim) "Frame" else "Halaman", color = Accent)
            }
            LazyRow(
                state = list,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.weight(1f, fill = false),
            ) {
                itemsIndexed(frames, key = { _, f -> f.key }) { i, f ->
                    val selected = i == cur
                    val hold = state.holds[f.key] ?: 0
                    Box {
                        Column(
                            Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .clickable {
                                    if (selected) state.frameMenu = f.key else state.selectFrame(f.key)
                                }
                                .semantics { contentDescription = (if (anim) "Frame " else "Halaman ") + (i + 1) }
                                .padding(2.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Box(
                                Modifier
                                    .size(tw, th)
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(paper)
                                    .border(2.dp, if (selected) Accent else Color(0x33FFFFFF), RoundedCornerShape(6.dp)),
                            ) {
                                for (id in f.layers) {
                                    val vis = state.layers.firstOrNull { it.id == id }?.visible ?: true
                                    val bmp = state.thumbs[id]
                                    if (bmp != null && vis) {
                                        Image(bmp, null, contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize().padding(2.dp))
                                    }
                                }
                                if (hold > 0 && anim) {
                                    Text(
                                        "+$hold",
                                        color = Color.White,
                                        fontSize = 10.sp,
                                        modifier = Modifier
                                            .align(Alignment.TopEnd)
                                            .padding(2.dp)
                                            .background(Color(0xCC3D8BFF), RoundedCornerShape(4.dp))
                                            .padding(horizontal = 3.dp),
                                    )
                                }
                            }
                            Text(
                                if (anim) "${i + 1}" else "Hal ${i + 1}",
                                color = if (selected) Accent else Muted,
                                fontSize = 11.sp,
                            )
                        }
                        DropdownMenu(expanded = state.frameMenu == f.key, onDismissRequest = { state.frameMenu = null }) {
                            if (anim) {
                                Row(Modifier.padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Text("Tahan", modifier = Modifier.width(56.dp))
                                    TextButton(onClick = { state.setHold(f.key, hold - 1) }, enabled = hold > 0) { Text("−", fontSize = 18.sp) }
                                    Text("$hold", modifier = Modifier.width(28.dp), fontWeight = FontWeight.SemiBold)
                                    TextButton(onClick = { state.setHold(f.key, hold + 1) }, enabled = hold < 60) { Text("+", fontSize = 18.sp) }
                                }
                                Text(
                                    if (hold == 0) "Tampil 1 ketukan (normal)" else "Tampil ${hold + 1} ketukan",
                                    fontSize = 12.sp,
                                    color = Color.Gray,
                                    modifier = Modifier.padding(horizontal = 16.dp),
                                )
                            }
                            DropdownMenuItem(text = { Text("Duplikat") }, onClick = { state.frameMenu = null; state.duplicateFrame(f.key) })
                            DropdownMenuItem(
                                text = { Text("Hapus") },
                                enabled = frames.size > 1,
                                onClick = { state.frameMenu = null; state.deleteFrame(f.key) },
                            )
                        }
                    }
                }
            }
        }
    }
}

/** Pengaturan Animation Assist: mode putar, FPS, onion skin, frame latar. */
@Composable
fun AnimSettingsPanel(state: AppState, modifier: Modifier) {
    val s = state.animSet
    fun set(n: AnimSettings) = state.setAnim(n)
    Surface(color = PanelBg, shape = RoundedCornerShape(18.dp), shadowElevation = 8.dp, modifier = modifier.width(420.dp)) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Pengaturan animasi", color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
                Spacer(Modifier.weight(1f))
                IconButton(onClick = { state.animSettingsOpen = false }, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Filled.Close, "Tutup pengaturan", tint = Muted)
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(vertical = 6.dp)) {
                listOf("Ulang", "Bolak-balik", "Sekali").forEachIndexed { i, label ->
                    FilterChip(
                        selected = s.mode == i,
                        onClick = { set(s.copy(mode = i)) },
                        label = { Text(label) },
                        colors = FilterChipDefaults.filterChipColors(selectedContainerColor = Color(0x553D8BFF), labelColor = Color.White),
                    )
                }
            }
            SliderLine("Frame per detik", "${s.fps}", (s.fps - 1) / 59f) { set(s.copy(fps = 1 + (it * 59).roundToInt())) }
            SliderLine("Frame onion skin", if (s.onion == 0) "Mati" else "${s.onion}", s.onion / 12f) {
                set(s.copy(onion = (it * 12).roundToInt()))
            }
            SliderLine("Kepekatan onion skin", "${(s.onionOpacity * 100).roundToInt()}%", s.onionOpacity) {
                set(s.copy(onionOpacity = it))
            }
            SwitchLine("Campur frame utama", "Frame aktif agak tembus supaya onion skin terlihat", s.blendPrimary) { set(s.copy(blendPrimary = it)) }
            SwitchLine("Warnai onion skin", "Sebelumnya merah, sesudahnya hijau", s.colorOnion) { set(s.copy(colorOnion = it)) }
            SwitchLine("Frame pertama jadi latar", "Tampil di belakang semua frame", s.bgFrame) { set(s.copy(bgFrame = it)) }
            SwitchLine("Frame terakhir jadi latar depan", "Tampil di depan semua frame", s.fgFrame) { set(s.copy(fgFrame = it)) }
        }
    }
}

@Composable
private fun SliderLine(title: String, value: String, v: Float, onChange: (Float) -> Unit) {
    Column(Modifier.fillMaxWidth().padding(top = 4.dp)) {
        Row {
            Text(title, color = Color.White, fontSize = 14.sp)
            Spacer(Modifier.weight(1f))
            Text(value, color = Muted, fontSize = 13.sp)
        }
        Slider(
            value = v.coerceIn(0f, 1f),
            onValueChange = onChange,
            colors = SliderDefaults.colors(thumbColor = Color.White, activeTrackColor = Accent),
            modifier = Modifier.height(32.dp),
        )
    }
}

@Composable
private fun SwitchLine(title: String, sub: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .clickable { onChange(!checked) }
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, color = Color.White, fontSize = 14.sp)
            Text(sub, color = Muted, fontSize = 12.sp)
        }
        Switch(checked = checked, onCheckedChange = onChange, colors = SwitchDefaults.colors(checkedTrackColor = Accent))
    }
}
