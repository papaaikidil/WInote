package id.goresan.ui

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import id.goresan.engine.SelMask
import androidx.compose.material.icons.filled.Deselect
import androidx.compose.material.icons.filled.FormatColorFill
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.InvertColors
import androidx.compose.material.icons.filled.IndeterminateCheckBox
import androidx.compose.material.icons.filled.AddBox
import androidx.compose.material.icons.filled.HighlightAlt
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.material3.Icon
import androidx.compose.material.icons.filled.BlurOn
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.automirrored.filled.Redo
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.Brush
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Colorize
import androidx.compose.material.icons.filled.Flip
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.OpenWith
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.RotateRight
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import id.goresan.canvas.CanvasView
import id.goresan.engine.ToolKind
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

@Composable
fun EditorScreen(state: AppState) {
    var dragHint by remember { mutableIntStateOf(0) }
    // Pemilih foto dipasang di sini (bukan di panel) supaya hasilnya tetap diterima setelah panel tertutup.
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) state.importPhoto(uri)
    }
    state.pickPhoto = { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }
    // Pemilih berkas kuas dari luar (.brush, .brushset, .abr, PNG, JPG).
    val brushPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) state.importBrush(uri)
    }
    state.pickBrushFile = { brushPicker.launch(arrayOf("*/*")) }
    // Kertas sendiri: foto kertas (PNG/JPG) atau pola .pat.
    val paperPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) state.importPaper(uri)
    }
    state.pickPaperFile = { paperPicker.launch(arrayOf("*/*")) }
    // Gambar untuk jendela referensi.
    val refPicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) state.loadRefImage(uri)
    }
    state.pickRefImage = { refPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }

    BackHandler {
        when {
            state.tlVideo != null -> state.tlVideo = null
            state.animSettingsOpen -> state.animSettingsOpen = false
            state.cropOpen -> { state.cropOpen = false; state.cropSnap = null }
            state.textEditing -> state.cancelText()
            state.guideEditing -> state.endGuideEdit(true)
            state.paperEditing -> state.endPaperEdit(true)
            state.panel != Panel.NONE -> { state.panel = Panel.NONE; state.studioBrushId = null }
            state.fillActive -> state.fillDone(false)
            state.xfActive -> state.transformDone(false)
            state.adjustKind >= 0 -> state.adjustDone(false)
            state.selTool -> state.closeSelTool()
            state.pickArmed -> state.cancelPick()
            else -> state.closeArtwork()
        }
    }

    val top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    Box(Modifier.fillMaxSize().background(Color(0xFF29292E))) {
        // Kanvas dibuat ulang (permukaan GL baru) saat ruang warna layar harus berganti.
        androidx.compose.runtime.key(state.canvasGen) {
            AndroidView(
                factory = { c -> CanvasView(c, state).also { state.attachCanvas(it) } },
                modifier = Modifier.fillMaxSize(),
                onRelease = { v -> state.detachCanvas(v) },
            )
        }
        // Face Paint: kamera depan menutupi kanvas; alat dan panel tetap di atasnya.
        if (state.isFace && state.faceView && !state.textEditing && !state.guideEditing && !state.paperEditing) FacePaintLayer(state)

        if (state.loading) {
            Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
                CircularProgressIndicator(color = Accent)
                Spacer(Modifier.height(12.dp))
                Text("Memuat kanvas…", color = Color.White)
            }
        }

        if (state.textEditing) {
            TextEditor(state)
        } else if (state.guideEditing) {
            GuideEditor(state)
        } else if (state.paperEditing) {
            PaperPanel(state)
        } else if (!state.uiHidden) {
            TopBar(state, Modifier.align(Alignment.TopCenter))
            SideBar(
                state,
                Modifier.align(if (state.prefs.sidebarRight) Alignment.CenterEnd else Alignment.CenterStart),
                onHint = { dragHint = it },
            )
        } else {
            Surface(
                color = BarBg,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.align(Alignment.TopEnd).padding(top = 10.dp + top, end = 10.dp).clickable { state.uiHidden = false },
            ) {
                Text("Tampilkan alat", color = Color.White, fontSize = 13.sp, modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp))
            }
        }

        when (state.panel) {
            Panel.BRUSHES -> BrushPanel(state, Modifier.align(Alignment.TopEnd).padding(top = 58.dp + top, end = 10.dp))
            Panel.LAYERS -> LayersPanel(state, Modifier.align(Alignment.TopEnd).padding(top = 58.dp + top, end = 10.dp))
            Panel.COLOR -> ColorPanel(state, Modifier.align(Alignment.TopEnd).padding(top = 58.dp + top, end = 10.dp))
            Panel.ACTIONS -> ActionsPanel(state, Modifier.align(Alignment.TopStart).padding(top = 58.dp + top, start = 10.dp))
            Panel.ADJUST -> AdjustMenu(state, Modifier.align(Alignment.TopStart).padding(top = 58.dp + top, start = 60.dp))
            Panel.NONE -> Unit
        }

        if (state.fillActive) FillBar(state, Modifier.align(Alignment.TopCenter).padding(top = 58.dp + top))
        if (state.xfActive) TransformBar(state, Modifier.align(Alignment.BottomCenter).padding(bottom = 16.dp))
        if (state.selTool) SelectBar(state, Modifier.align(Alignment.BottomCenter).padding(bottom = 16.dp))
        if (state.adjustKind >= 0) AdjustBar(state, Modifier.align(Alignment.TopCenter).padding(top = 58.dp + top))
        if (state.pickArmed && state.loupe == null) {
            Hint("Sentuh kanvas untuk mengambil warna", Modifier.align(Alignment.TopCenter).padding(top = 64.dp + top))
        }

        // Alat 2D (pilihan, transformasi, penyesuaian, teks, panduan, kertas) dipakai di tampilan tekstur.
        val tool2D = state.selTool || state.xfActive || state.adjustKind >= 0 || state.textEditing || state.guideEditing || state.paperEditing
        androidx.compose.runtime.LaunchedEffect(tool2D, state.view3D, state.faceView) {
            if (tool2D && (state.view3D || (state.isFace && state.faceView))) {
                state.switchView3D(false)
                state.switchFaceView(false)
                state.showFlash("Alat ini dipakai di tampilan tekstur 2D")
            }
        }
        if (state.isFace && !state.uiHidden && !state.textEditing && !state.guideEditing && !state.paperEditing) {
            FaceBar(state, Modifier.align(Alignment.BottomStart).padding(start = 76.dp, bottom = if (state.assist != 0) 112.dp else 14.dp))
        }
        if (state.is3D && !state.uiHidden && !state.textEditing && !state.guideEditing && !state.paperEditing) {
            Model3DBar(state, Modifier.align(Alignment.BottomStart).padding(start = 76.dp, bottom = if (state.assist != 0) 112.dp else 14.dp))
        }
        val timeline = state.assist != 0 && !state.uiHidden && !state.textEditing && !state.guideEditing &&
            !state.paperEditing && !state.xfActive && !state.selTool
        if (timeline) {
            AnimTimeline(state, Modifier.align(Alignment.BottomCenter).padding(start = 76.dp, end = 76.dp, bottom = 14.dp))
            if (state.assist == 1 && state.animSettingsOpen) {
                AnimSettingsPanel(state, Modifier.align(Alignment.BottomCenter).padding(bottom = 116.dp))
            }
        }
        if (!state.uiHidden && !state.textEditing && !state.guideEditing && !state.paperEditing) ReferenceWindow(state)
        if (dragHint != 0) SliderHint(state, dragHint, Modifier.align(Alignment.Center))
        LoupeOverlay(state)
        DropOverlay(state)
        FlashOverlay(state, Modifier.align(Alignment.TopCenter).padding(top = 70.dp + top))

        ReportDialog(state)
        CanvasInfoDialog(state)
        ProfileDialog(state)
        CropResizeScreen(state)
        TimeLapsePlayer(state)

        state.busy?.let { msg ->
            Box(Modifier.fillMaxSize().background(Color(0x88000000)).pointerInput(Unit) { detectTapGestures { } }) {
                Surface(color = PanelBg, shape = RoundedCornerShape(16.dp), modifier = Modifier.align(Alignment.Center)) {
                    Row(Modifier.padding(horizontal = 22.dp, vertical = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(Modifier.size(22.dp), color = Accent, strokeWidth = 2.dp)
                        Spacer(Modifier.width(14.dp))
                        Text(msg, color = Color.White)
                    }
                }
            }
        }
    }
}

@Composable
private fun TopBar(state: AppState, modifier: Modifier) {
    Row(
        modifier
            .fillMaxWidth()
            .background(BarBg)
            .windowInsetsPadding(WindowInsets.statusBars)
            .height(52.dp)
            .pointerInput(Unit) { detectTapGestures { } }
            .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TextButton(onClick = { state.closeArtwork() }) {
            Text("Galeri", color = Color.White, fontWeight = FontWeight.SemiBold)
        }
        BarIcon(Icons.Filled.Build, "Aksi", selected = state.panel == Panel.ACTIONS) {
            if (!state.modalActive()) state.panel = if (state.panel == Panel.ACTIONS) Panel.NONE else Panel.ACTIONS
        }
        BarIcon(Icons.Filled.AutoFixHigh, "Penyesuaian", selected = state.panel == Panel.ADJUST || state.adjustKind >= 0) {
            if (!state.modalActive()) state.panel = if (state.panel == Panel.ADJUST) Panel.NONE else Panel.ADJUST
        }
        BarIcon(Icons.Filled.HighlightAlt, "Pilihan", selected = state.selTool) { state.toggleSelTool() }
        BarIcon(Icons.Filled.OpenWith, "Transformasi", selected = state.xfActive) {
            if (state.xfActive) state.transformDone(true) else state.startTransform()
        }
        Spacer(Modifier.width(6.dp))
        // Masukan: pena saja (jari menggeser kanvas) atau jari juga menggambar.
        val finger = state.prefs.fingerDraws
        TextButton(onClick = {
            state.prefs.fingerDraws = !finger
            state.showFlash(if (!finger) "Jari ikut menggambar" else "Hanya pena yang menggambar; jari menggeser kanvas")
        }) {
            androidx.compose.material3.Icon(
                if (finger) Icons.Filled.TouchApp else Icons.Filled.Edit,
                contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp),
            )
            Spacer(Modifier.width(6.dp))
            Text(if (finger) "Jari + pena" else "Pena saja", color = Color.White, fontSize = 13.sp)
        }
        Spacer(Modifier.weight(1f))
        Text(state.docName, color = Muted, fontSize = 13.sp, maxLines = 1)
        Spacer(Modifier.weight(1f))
        BarIcon(Icons.Filled.Brush, "Kuas", selected = state.tool == ToolKind.PAINT) { state.onToolIcon(ToolKind.PAINT) }
        BarIcon(Icons.Filled.TouchApp, "Baur", selected = state.tool == ToolKind.SMUDGE) { state.onToolIcon(ToolKind.SMUDGE) }
        BarIcon(EraserIcon, "Penghapus", selected = state.tool == ToolKind.ERASE) { state.onToolIcon(ToolKind.ERASE) }
        BarIcon(Icons.Filled.Layers, "Layer", selected = state.panel == Panel.LAYERS) {
            if (!state.modalActive()) state.panel = if (state.panel == Panel.LAYERS) Panel.NONE else Panel.LAYERS
        }
        Spacer(Modifier.width(6.dp))
        ColorButton(state)
        Spacer(Modifier.width(6.dp))
    }
}

@Composable
private fun ColorButton(state: AppState) {
    var origin by remember { mutableStateOf(Offset.Zero) }
    Box(
        Modifier
            .size(36.dp)
            .onGloballyPositioned { origin = it.positionInRoot() }
            .clip(CircleShape)
            .background(Color(state.color))
            .border(2.dp, Color(0xDDFFFFFF), CircleShape)
            .pointerInput(Unit) {
                // Ketuk = buka panel warna; seret ke kanvas = ColorDrop. Posisi jatuh diambil dari titik saat
                // jari/pena diangkat (bukan gerakan terakhir), supaya tepat walau seretannya cepat dan jauh.
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    var dragging = false
                    while (true) {
                        val ev = awaitPointerEvent()
                        val ch = ev.changes.firstOrNull { it.id == down.id } ?: break
                        val pos = ch.position
                        if (!dragging && (pos - down.position).getDistance() > viewConfiguration.touchSlop) dragging = true
                        if (dragging) {
                            ch.consume()
                            state.dropPos = origin + pos
                        }
                        if (!ch.pressed) {
                            if (dragging) {
                                val p = origin + pos
                                state.dropPos = null
                                state.dropColorAt(p.x, p.y)
                            } else if ((pos - down.position).getDistance() <= viewConfiguration.touchSlop) {
                                state.openColorPanel()
                            }
                            break
                        }
                    }
                    state.dropPos = null
                }
            },
    )
}

@Composable
private fun SideBar(state: AppState, modifier: Modifier, onHint: (Int) -> Unit) {
    Column(
        modifier
            .padding(horizontal = 8.dp)
            .width(48.dp)
            .clip(RoundedCornerShape(24.dp))
            .background(BarBg)
            .pointerInput(Unit) { detectTapGestures { } }
            .padding(vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        VerticalSlider(
            value = state.sizeSlider(),
            onChange = { state.setSizeSlider(it) },
            modifier = Modifier.height(180.dp),
            onStart = { onHint(1) },
            onEnd = { onHint(0); state.persistSliders() },
        )
        Spacer(Modifier.height(10.dp))
        Box(
            Modifier
                .size(34.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(if (state.pickArmed) Accent else Color(0x33FFFFFF))
                .clickable { state.armPick() },
            contentAlignment = Alignment.Center,
        ) {
            androidx.compose.material3.Icon(Icons.Filled.Colorize, "Pipet", tint = Color.White, modifier = Modifier.size(20.dp))
        }
        Spacer(Modifier.height(10.dp))
        VerticalSlider(
            value = state.opacitySlider(),
            onChange = { state.setOpacitySlider(it) },
            modifier = Modifier.height(180.dp),
            onStart = { onHint(2) },
            onEnd = { onHint(0); state.persistSliders() },
        )
        Spacer(Modifier.height(10.dp))
        RepeatBarIcon(Icons.AutoMirrored.Filled.Undo, "Urungkan", enabled = state.canUndo) { state.undo() }
        RepeatBarIcon(Icons.AutoMirrored.Filled.Redo, "Ulangi", enabled = state.canRedo) { state.redo() }
    }
}

@Composable
private fun SliderHint(state: AppState, kind: Int, modifier: Modifier) {
    val density = LocalDensity.current
    Surface(color = PanelBg, shape = RoundedCornerShape(18.dp), modifier = modifier) {
        Column(Modifier.padding(18.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            if (kind == 1) {
                val px = state.sizePx()
                val screenPx = (px * state.viewZoom).coerceIn(2f, 600f)
                val d = with(density) { screenPx.toDp() }
                Box(Modifier.size(maxOf(d, 8.dp)).clip(CircleShape).background(Color(state.color).copy(alpha = 0.9f)).border(1.dp, Color.White, CircleShape))
                Spacer(Modifier.height(10.dp))
                Text("Ukuran ${(state.sizeSlider() * 100).roundToInt()}% · ${px.roundToInt()} px", color = Color.White, fontSize = 14.sp)
            } else {
                Text("Opasitas ${(state.opacitySlider() * 100).roundToInt()}%", color = Color.White, fontSize = 14.sp)
            }
        }
    }
}

@Composable
private fun Hint(text: String, modifier: Modifier) {
    Surface(color = PanelBg, shape = RoundedCornerShape(16.dp), modifier = modifier) {
        Text(text, color = Color.White, fontSize = 13.sp, modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp))
    }
}

@Composable
private fun LoupeOverlay(state: AppState) {
    val l = state.loupe ?: return
    val density = LocalDensity.current
    val sizeDp = 96.dp
    val sizePx = with(density) { sizeDp.toPx() }
    val lift = with(density) { 84.dp.toPx() }
    Canvas(
        Modifier
            .offset { IntOffset((l.x - sizePx / 2f).roundToInt(), (l.y - lift - sizePx / 2f).roundToInt()) }
            .size(sizeDp),
    ) {
        val stroke = 14.dp.toPx()
        val r = size.minDimension / 2f - stroke / 2f
        val tl = Offset(size.width / 2f - r, size.height / 2f - r)
        val s = Size(r * 2, r * 2)
        drawArc(Color(l.color), 180f, 180f, false, tl, s, style = Stroke(stroke))
        drawArc(Color(state.prevColor), 0f, 180f, false, tl, s, style = Stroke(stroke))
        drawCircle(Color.White, radius = r + stroke / 2f, style = Stroke(1.5.dp.toPx()))
        drawCircle(Color(0x88000000), radius = r - stroke / 2f, style = Stroke(1.dp.toPx()))
    }
}

@Composable
private fun DropOverlay(state: AppState) {
    val p = state.dropPos ?: return
    val density = LocalDensity.current
    val half = with(density) { 20.dp.toPx() }
    Box(
        Modifier
            .offset { IntOffset((p.x - half).roundToInt(), (p.y - half).roundToInt()) }
            .size(40.dp)
            .clip(CircleShape)
            .background(Color(state.color))
            .border(2.dp, Color.White, CircleShape),
    )
}

@Composable
private fun FlashOverlay(state: AppState, modifier: Modifier) {
    val msg = state.flash ?: return
    val id = state.flashId
    LaunchedEffect(id) {
        delay(1600)
        if (state.flashId == id) state.flash = null
    }
    Hint(msg, modifier)
}

@Composable
private fun BarSurface(modifier: Modifier, content: @Composable () -> Unit) {
    Surface(color = PanelBg, shape = RoundedCornerShape(18.dp), modifier = modifier, shadowElevation = 6.dp) {
        content()
    }
}

@Composable
private fun FillBar(state: AppState, modifier: Modifier) {
    BarSurface(modifier.width(620.dp)) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("ColorDrop", color = Color.White, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.width(12.dp))
                Text("Ambang ${(state.fillThreshold * 100).roundToInt()}%", color = Muted, fontSize = 13.sp)
                Spacer(Modifier.weight(1f))
                FilterChip(
                    selected = !state.fillRefAll,
                    onClick = { state.fillRefAll = false; state.fillChanged() },
                    label = { Text("Acuan: layer ini") },
                    colors = FilterChipDefaults.filterChipColors(selectedContainerColor = Color(0x553D8BFF)),
                )
                Spacer(Modifier.width(6.dp))
                FilterChip(
                    selected = state.fillRefAll,
                    onClick = { state.fillRefAll = true; state.fillChanged() },
                    label = { Text("Semua layer") },
                    colors = FilterChipDefaults.filterChipColors(selectedContainerColor = Color(0x553D8BFF)),
                )
            }
            Slider(
                value = state.fillThreshold,
                onValueChange = { state.fillThreshold = it; state.fillChanged() },
                colors = SliderDefaults.colors(thumbColor = Color.White, activeTrackColor = Accent),
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Atur ambang bila isian bocor · mulai menggores untuk lanjut", color = Muted, fontSize = 12.sp)
                Spacer(Modifier.weight(1f))
                TextButton(onClick = { state.fillDone(false) }) { Text("Batal") }
                TextButton(onClick = { state.fillDone(true) }) { Text("Selesai", fontWeight = FontWeight.SemiBold) }
            }
        }
    }
}

@Composable
private fun TransformBar(state: AppState, modifier: Modifier) {
    BarSurface(modifier) {
        Column(Modifier.padding(horizontal = 12.dp, vertical = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                listOf(
                    "Bebas" to XfModel.FREEFORM,
                    "Seragam" to XfModel.UNIFORM,
                    "Distorsi" to XfModel.DISTORT,
                    "Warp" to XfModel.WARP,
                ).forEach { (label, m) ->
                    FilterChip(
                        selected = state.xfMode == m,
                        onClick = { state.chooseXfMode(m) },
                        label = { Text(label) },
                        colors = FilterChipDefaults.filterChipColors(selectedContainerColor = Color(0x553D8BFF)),
                    )
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                XfTool(Icons.Filled.FlashOn, "Snapping", selected = state.xfSnap) { state.toggleXfSnap() }
                XfTool(Icons.Filled.Flip, "Balik H") { state.transformFlip(true) }
                XfTool(Icons.Filled.Flip, "Balik V", rotate = 90f) { state.transformFlip(false) }
                XfTool(Icons.Filled.RotateRight, "Putar 45°") { state.transformRotate(45f) }
                XfTool(Icons.Filled.Fullscreen, "Pas kanvas") { state.transformFit() }
                val interp = when (state.xfInterp) {
                    XfModel.NEAREST -> "Terdekat"
                    XfModel.BICUBIC -> "Bikubik"
                    else -> "Bilinear"
                }
                XfTool(Icons.Filled.BlurOn, interp) { state.cycleXfInterp() }
                XfTool(Icons.Filled.Refresh, "Atur ulang") { state.transformReset() }
                Spacer(Modifier.width(8.dp))
                BarIcon(Icons.Filled.Close, "Batal") { state.transformDone(false) }
                BarIcon(Icons.Filled.Check, "Selesai", selected = true) { state.transformDone(true) }
            }
            val hint = when (state.xfMode) {
                XfModel.FREEFORM -> "Seret sudut/tepi: ubah ukuran bebas · bulatan atas: putar · dua jari: geser, perbesar, putar"
                XfModel.DISTORT -> "Seret sudut: perspektif · seret tepi: miringkan · bulatan atas: putar"
                XfModel.WARP -> "Seret titik atau bagian dalam kotak untuk melengkungkan · di luar kotak: geser"
                else -> "Seret sudut/tepi: ubah ukuran (proporsi tetap) · bulatan atas: putar · dua jari: geser, perbesar, putar"
            }
            Text(hint, color = Muted, fontSize = 12.sp)
        }
    }
}

@Composable
private fun SelectBar(state: AppState, modifier: Modifier) {
    BarSurface(modifier) {
        Column(Modifier.padding(horizontal = 12.dp, vertical = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            if (state.selPanel == 1) {
                Box(Modifier.width(520.dp)) {
                    LabeledSlider("Perhalus tepi", state.selFeatherPx, { state.setSelFeather(it) }, 0f..100f, { "${it.roundToInt()} px" })
                }
            }
            if (state.selPanel == 2) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    TextButton(enabled = state.selActive, onClick = { state.selSave() }) { Text("Simpan pilihan ini") }
                    if (state.selSaved == 0) Text("Belum ada pilihan tersimpan", color = Muted, fontSize = 12.sp)
                    for (i in 0 until state.selSaved) {
                        FilterChip(
                            selected = false,
                            onClick = { state.selLoad(i) },
                            label = { Text("Pilihan ${i + 1}") },
                        )
                    }
                }
            }
            if (state.selMode == SEL_AUTO) {
                Box(Modifier.width(520.dp)) {
                    LabeledSlider("Ambang", state.selThreshold, { state.changeSelThreshold(it) }, 0f..1f, { "${(it * 100).roundToInt()}%" })
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                listOf(
                    "Otomatis" to SEL_AUTO,
                    "Bebas" to SEL_FREE,
                    "Persegi" to SEL_RECT,
                    "Elips" to SEL_ELLIPSE,
                ).forEach { (label, m) ->
                    FilterChip(
                        selected = state.selMode == m,
                        onClick = { state.chooseSelMode(m) },
                        label = { Text(label) },
                        colors = FilterChipDefaults.filterChipColors(selectedContainerColor = Color(0x553D8BFF)),
                    )
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                XfTool(Icons.Filled.AddBox, "Tambah", selected = state.selOpMode == SelMask.OP_ADD) { state.chooseSelOp(SelMask.OP_ADD) }
                XfTool(Icons.Filled.IndeterminateCheckBox, "Kurangi", selected = state.selOpMode == SelMask.OP_REMOVE) { state.chooseSelOp(SelMask.OP_REMOVE) }
                XfTool(Icons.Filled.InvertColors, "Balik") { state.selInvert() }
                XfTool(Icons.Filled.ContentCopy, "Salin & Tempel", width = 70) { state.selCopyPaste() }
                XfTool(Icons.Filled.BlurOn, "Perhalus", selected = state.selPanel == 1) { state.selPanel = if (state.selPanel == 1) 0 else 1 }
                XfTool(Icons.Filled.Favorite, "Simpan & Muat", selected = state.selPanel == 2, width = 70) {
                    state.selPanel = if (state.selPanel == 2) 0 else 2
                }
                XfTool(Icons.Filled.FormatColorFill, "Isi warna") { state.selFill() }
                XfTool(Icons.Filled.Deselect, "Hapus") { state.selClear() }
                Spacer(Modifier.width(8.dp))
                BarIcon(Icons.Filled.Check, "Selesai", selected = true) { state.closeSelTool() }
            }
            val hint = when (state.selMode) {
                SEL_AUTO -> "Ketuk area untuk memilih warna yang mirip · tahan lalu geser kiri/kanan untuk mengubah ambang"
                SEL_RECT -> "Seret untuk membuat kotak pilihan"
                SEL_ELLIPSE -> "Seret untuk membuat elips pilihan"
                else -> "Seret untuk melingkari area · atau ketuk titik-titik sudut, lalu ketuk titik pertama untuk menutup"
            }
            Text(hint, color = Muted, fontSize = 12.sp)
            if (state.selActive) {
                Text("Pilihan aktif: kuas, penyesuaian, dan transformasi hanya berlaku di dalamnya", color = Accent, fontSize = 11.sp)
            }
        }
    }
}

@Composable
private fun XfTool(
    icon: ImageVector,
    label: String,
    selected: Boolean = false,
    rotate: Float = 0f,
    width: Int = 66,
    onClick: () -> Unit,
) {
    Column(
        Modifier
            .width(width.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(if (selected) Color(0x223D8BFF) else Color.Transparent)
            .clickable { onClick() }
            .padding(vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            icon,
            contentDescription = label,
            tint = if (selected) Accent else Color(0xFFEDEDF2),
            modifier = Modifier.size(22.dp).rotate(rotate),
        )
        Text(
            label,
            fontSize = 11.sp,
            lineHeight = 12.sp,
            color = if (selected) Accent else Color(0xFFCFCFD6),
            maxLines = 2,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        )
    }
}

/** Pengalih tampilan model 3D / tekstur 2D. */
@Composable
private fun Model3DBar(state: AppState, modifier: Modifier) {
    BarSurface(modifier) {
        Row(Modifier.padding(horizontal = 8.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            FilterChip(
                selected = state.view3D,
                onClick = { state.switchView3D(true) },
                label = { Text("Model 3D") },
                colors = FilterChipDefaults.filterChipColors(selectedContainerColor = Color(0x553D8BFF), labelColor = Color.White),
            )
            Spacer(Modifier.width(6.dp))
            FilterChip(
                selected = !state.view3D,
                onClick = { state.switchView3D(false) },
                label = { Text("Tekstur 2D") },
                colors = FilterChipDefaults.filterChipColors(selectedContainerColor = Color(0x553D8BFF), labelColor = Color.White),
            )
            if (state.view3D) {
                Spacer(Modifier.width(4.dp))
                TextButton(onClick = { state.reset3D() }) { Text("Atur ulang") }
            }
        }
    }
}

/** Pengalih Face Paint (kamera) / tekstur 2D, dan foto. */
@Composable
private fun FaceBar(state: AppState, modifier: Modifier) {
    BarSurface(modifier) {
        Row(Modifier.padding(horizontal = 8.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            FilterChip(
                selected = state.faceView,
                onClick = { state.switchFaceView(true) },
                label = { Text("Face Paint") },
                colors = FilterChipDefaults.filterChipColors(selectedContainerColor = Color(0x553D8BFF), labelColor = Color.White),
            )
            Spacer(Modifier.width(6.dp))
            FilterChip(
                selected = !state.faceView,
                onClick = { state.switchFaceView(false) },
                label = { Text("Tekstur 2D") },
                colors = FilterChipDefaults.filterChipColors(selectedContainerColor = Color(0x553D8BFF), labelColor = Color.White),
            )
            if (state.faceView) {
                Spacer(Modifier.width(4.dp))
                TextButton(onClick = { state.facePhoto?.invoke() }) { Text("Foto") }
            }
        }
    }
}
