package id.goresan.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AddBox
import androidx.compose.material.icons.filled.AddCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ColorLens
import androidx.compose.material.icons.filled.Contrast
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.EmojiObjects
import androidx.compose.material.icons.filled.FilterBAndW
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.Functions
import androidx.compose.material.icons.filled.Gradient
import androidx.compose.material.icons.filled.GridOn
import androidx.compose.material.icons.filled.Highlight
import androidx.compose.material.icons.filled.InvertColors
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.Opacity
import androidx.compose.material.icons.filled.OpenWith
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.RemoveCircle
import androidx.compose.material.icons.filled.Tonality
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material.icons.filled.Whatshot
import androidx.compose.material.icons.outlined.Lightbulb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FormatColorFill
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.zIndex
import kotlinx.coroutines.delay
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import id.goresan.engine.GroupData
import id.goresan.engine.LayerInfo
import id.goresan.engine.LayerProps
import kotlin.math.roundToInt

val BlendNames = listOf(
    "Normal", "Multiply", "Gelapkan", "Color Burn", "Linear Burn", "Warna Lebih Gelap",
    "Terangkan", "Screen", "Color Dodge", "Tambah", "Warna Lebih Terang",
    "Overlay", "Soft Light", "Hard Light", "Vivid Light", "Linear Light", "Pin Light", "Hard Mix",
    "Selisih", "Eksklusi", "Kurangi", "Bagi",
    "Rona", "Saturasi", "Warna", "Luminositas",
)

private val BlendShort = listOf(
    "N", "M", "Gl", "CB", "LB", "WG", "T", "S", "CD", "+", "WT",
    "O", "SL", "HL", "VL", "LL", "PL", "HM", "Sl", "E", "−", "÷", "R", "Sa", "W", "L",
)

/** Baris daftar layer: kepala grup atau layer (anggota grup ditampilkan menjorok). */
private sealed class PanelRow {
    data class Grp(val id: Int, val data: GroupData, val count: Int) : PanelRow()
    data class Lay(val info: LayerInfo, val inGroup: Boolean) : PanelRow()
}

private fun PanelRow.key(): String = when (this) {
    is PanelRow.Grp -> "g$id"
    is PanelRow.Lay -> "l${info.id}"
}

/** Baris yang sedang diseret (layer atau grup) beserta posisi awalnya di daftar. */
private class DragState(
    val key: String, val layerId: Int, val groupId: Int,
    val startOffset: Int, val startX: Float, val startY: Float,
)

/** Tempat jatuh: di celah sebelum baris ke-[k] (k = jumlah baris berarti paling bawah), atau tepat di atas baris. */
private sealed class DropTarget {
    data class Gap(val k: Int, val join: Boolean) : DropTarget()
    data class Onto(val index: Int) : DropTarget()
}

@Composable
fun LayersPanel(state: AppState, modifier: Modifier) {
    var expanded by remember { mutableIntStateOf(-1) }
    var menuFor by remember { mutableIntStateOf(-1) }
    var groupMenuFor by remember { mutableIntStateOf(-1) }
    var renameFor by remember { mutableStateOf<LayerInfo?>(null) }
    var renameGroupFor by remember { mutableIntStateOf(-1) }
    var selecting by state::layerSelecting
    val selected = state.layerSelection

    // ---- tahan & seret ----
    val listState = rememberLazyListState()
    val haptic = LocalHapticFeedback.current
    val density = LocalDensity.current
    val indentPx = with(density) { 18.dp.toPx() }
    val edgePx = with(density) { 56.dp.toPx() }
    var drag by remember { mutableStateOf<DragState?>(null) }
    var dragDx by remember { mutableFloatStateOf(0f) }
    var dragDy by remember { mutableFloatStateOf(0f) }
    var target by remember { mutableStateOf<DropTarget?>(null) }
    var listWidth by remember { mutableIntStateOf(1) }
    val rows = buildRows(state, hideMembersOf = drag?.groupId ?: 0)
    val rowsRef = rememberUpdatedState(rows)
    val dragRef = rememberUpdatedState(drag)

    // ---- gestur di daftar layer (seperti Procreate) ----
    // Geser kanan = pilih beberapa layer; geser kiri = tombol Kunci/Duplikat/Hapus;
    // ketuk dua jari = Alpha Lock; cubit dua layer (atau lebih) = gabungkan.
    var swipeKey by remember { mutableStateOf<String?>(null) }
    var swipeDx by remember { mutableFloatStateOf(0f) }
    var revealed by remember { mutableIntStateOf(-1) }
    var pinchRange by remember { mutableStateOf<IntRange?>(null) }
    var maskMenuFor by remember { mutableIntStateOf(-1) }
    val headPx = with(density) { 62.dp.toPx() }
    val swipeMax = with(density) { 150.dp.toPx() }
    val swipeAct = with(density) { 64.dp.toPx() }
    val pinchMin = with(density) { 36.dp.toPx() }

    /** Id layer dari baris daftar (kepala grup = semua anggotanya). */
    fun idsOfRow(r: PanelRow): List<Int> = when (r) {
        is PanelRow.Lay -> listOf(r.info.id)
        is PanelRow.Grp -> state.layers.filter { it.group == r.id }.map { it.id }
    }

    val gestures = Modifier.pointerInput(Unit) {
        val slop = viewConfiguration.touchSlop
        fun itemAt(y: Float) = listState.layoutInfo.visibleItemsInfo.firstOrNull { y >= it.offset && y < it.offset + it.size }
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
            if (dragRef.value != null) return@awaitEachGesture
            val firstItem = itemAt(down.position.y)
            val firstKey = firstItem?.key as? String
            // Geser hanya dari bagian kepala baris (bukan penggeser opasitas di bawahnya).
            val swipeOk = firstItem != null && firstKey != null && firstKey.startsWith("l") && down.position.y - firstItem.offset < headPx
            var mode = 0 // 0 belum jelas, 1 geser satu jari, 2 dua jari, 3 abaikan (gulir/seret)
            var p2Id: androidx.compose.ui.input.pointer.PointerId? = null
            var p1Start = down.position
            var p2Start = Offset.Zero
            var startDist = 0f
            var twoStart = 0L
            var twoMoved = false
            var pinching = false
            var p1Last = Offset.Zero
            var p2Last = Offset.Zero
            var r1 = -1
            var r2 = -1
            while (true) {
                val ev = awaitPointerEvent(PointerEventPass.Initial)
                if (dragRef.value != null) { mode = 3 }
                val c1 = ev.changes.firstOrNull { it.id == down.id }
                if (mode == 0 || mode == 1) {
                    val other = ev.changes.firstOrNull { it.id != down.id && it.pressed }
                    if (other != null && mode == 0 && c1 != null && c1.pressed) {
                        mode = 2
                        p2Id = other.id
                        p1Start = c1.position
                        p2Start = other.position
                        p1Last = p1Start
                        p2Last = p2Start
                        startDist = (p1Start - p2Start).getDistance()
                        twoStart = ev.changes.maxOf { it.uptimeMillis }
                        r1 = itemAt(p1Start.y)?.index ?: -1
                        r2 = itemAt(p2Start.y)?.index ?: -1
                        ev.changes.forEach { it.consume() }
                        continue
                    }
                }
                when (mode) {
                    0 -> {
                        if (c1 == null || !c1.pressed) break
                        val d = c1.position - down.position
                        if (swipeOk && kotlin.math.abs(d.x) > slop * 1.2f && kotlin.math.abs(d.x) > kotlin.math.abs(d.y) * 2f) {
                            mode = 1
                            swipeKey = firstKey
                            c1.consume()
                        } else if (kotlin.math.abs(d.y) > slop) {
                            mode = 3
                        }
                    }
                    1 -> {
                        if (c1 == null) break
                        c1.consume()
                        swipeDx = (c1.position.x - down.position.x).coerceIn(-swipeMax, swipeMax)
                        if (!c1.pressed) {
                            val id = firstKey?.drop(1)?.toIntOrNull()
                            val dx = swipeDx
                            swipeKey = null
                            swipeDx = 0f
                            if (id != null) {
                                if (dx > swipeAct) {
                                    // Geser kanan: masuk/keluar pilihan beberapa layer.
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    revealed = -1
                                    if (!selecting) {
                                        selecting = true
                                        selected.clear()
                                        if (state.activeLayerId != id && state.layers.any { it.id == state.activeLayerId }) selected.add(state.activeLayerId)
                                    }
                                    if (id in selected) selected.remove(id) else selected.add(id)
                                    if (selected.isEmpty()) selecting = false
                                } else if (dx < -swipeAct) {
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    revealed = if (revealed == id) -1 else id
                                }
                            }
                            break
                        }
                    }
                    2 -> {
                        ev.changes.forEach { it.consume() }
                        val a = ev.changes.firstOrNull { it.id == down.id }
                        val b = ev.changes.firstOrNull { it.id == p2Id }
                        if (a != null && b != null && a.pressed && b.pressed) {
                            p1Last = a.position
                            p2Last = b.position
                            if ((a.position - p1Start).getDistance() > slop || (b.position - p2Start).getDistance() > slop) twoMoved = true
                            val dist = (a.position - b.position).getDistance()
                            val wasPinching = pinching
                            pinching = r1 >= 0 && r2 >= 0 && r1 != r2 && dist < startDist * 0.7f && startDist - dist > pinchMin
                            pinchRange = if (pinching) minOf(r1, r2)..maxOf(r1, r2) else null
                            if (pinching && !wasPinching) haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        }
                        if (ev.changes.none { it.pressed }) {
                            val range = pinchRange
                            pinchRange = null
                            val dur = ev.changes.maxOf { it.uptimeMillis } - twoStart
                            if (pinching && range != null) {
                                val rs = rowsRef.value
                                val ids = range.flatMap { i -> rs.getOrNull(i)?.let { idsOfRow(it) } ?: emptyList() }.distinct()
                                if (ids.size >= 2) state.layerCmd { it.mergeLayers(ids) }
                            } else {
                                val dx = ((p1Last.x - p1Start.x) + (p2Last.x - p2Start.x)) / 2f
                                val dy = ((p1Last.y - p1Start.y) + (p2Last.y - p2Start.y)) / 2f
                                val r = rowsRef.value.getOrNull(r1)
                                if (r is PanelRow.Lay && dx > swipeAct && kotlin.math.abs(dy) < dx * 0.6f) {
                                    // Geser dua jari ke kanan: Alpha Lock (seperti Procreate).
                                    val l = r.info
                                    state.setLayerProps(l.id, state.propsOf(l).copy(alphaLock = !l.alphaLock))
                                    state.showFlash(if (l.alphaLock) "Alpha Lock mati: ${l.name}" else "Alpha Lock aktif: ${l.name}")
                                } else if (r is PanelRow.Lay && !twoMoved && dur < 450) {
                                    // Ketuk dua jari: atur opasitas layer dengan menggeser jari di kanvas.
                                    state.startLayerOpacity(r.info.id)
                                }
                            }
                            break
                        }
                    }
                    else -> {
                        if (ev.changes.none { it.pressed }) break
                    }
                }
            }
            if (mode == 1) { swipeKey = null; swipeDx = 0f }
            if (mode == 2) pinchRange = null
        }
    }

    fun pointerY(d: DragState) = d.startOffset + d.startY + dragDy
    fun pointerX(d: DragState) = d.startX + dragDx

    fun computeTarget(): DropTarget? {
        val d = drag ?: return null
        val rs = rowsRef.value
        val items = listState.layoutInfo.visibleItemsInfo
        if (items.isEmpty()) return null
        val py = pointerY(d)
        val joinRight = pointerX(d) >= listWidth * 0.45f
        fun gap(k: Int): DropTarget {
            val u = rs.getOrNull(k - 1)
            val l = rs.getOrNull(k)
            val j = joinRight && u is PanelRow.Lay && u.inGroup && !(l is PanelRow.Lay && l.inGroup)
            return DropTarget.Gap(k, j)
        }
        for (it in items) {
            val top = it.offset
            val bottom = it.offset + it.size
            if (py < top || py >= bottom) continue
            if (it.index >= rs.size) return gap(rs.size)
            val rel = (py - top) / it.size.toFloat()
            val r = rs[it.index]
            if (d.layerId != 0 && rel in 0.28f..0.72f && r.key() != d.key) return DropTarget.Onto(it.index)
            return if (rel < 0.5f) gap(it.index) else gap(it.index + 1)
        }
        return if (py < items.first().offset) gap(items.first().index) else gap(rs.size)
    }

    fun dragModifier(key: String, layerId: Int, groupId: Int, indented: Boolean): Modifier =
        // Saat memilih, kepala grup tidak diseret (seret layernya); layer yang diseret ikut dipilih.
        if (selecting && groupId != 0) Modifier else Modifier.pointerInput(key, indented, selecting) {
            detectDragGesturesAfterLongPress(
                onDragStart = { off ->
                    val info = listState.layoutInfo.visibleItemsInfo.firstOrNull { it.key == key }
                    if (info != null) {
                        if (selecting && layerId != 0 && layerId !in selected) selected.add(layerId)
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        dragDx = 0f
                        dragDy = 0f
                        menuFor = -1
                        groupMenuFor = -1
                        drag = DragState(key, layerId, groupId, info.offset, (if (indented) indentPx else 0f) + off.x, off.y)
                        target = null
                    }
                },
                onDrag = { change, amount ->
                    if (drag != null) {
                        change.consume()
                        dragDx += amount.x
                        dragDy += amount.y
                        target = computeTarget()
                    }
                },
                onDragEnd = {
                    val d = drag
                    val t = target
                    drag = null
                    target = null
                    if (d != null && t != null) {
                        val many = if (selecting && d.layerId != 0 && selected.size > 1) {
                            state.layers.map { it.id }.filter { it in selected }
                        } else if (d.layerId != 0) listOf(d.layerId) else emptyList()
                        applyDrop(state, rowsRef.value, many, d.groupId, t)
                    }
                },
                onDragCancel = { drag = null; target = null },
            )
        }

    // Gulir sendiri bila layer diseret ke tepi atas/bawah daftar.
    LaunchedEffect(drag != null) {
        while (drag != null) {
            val d = drag ?: break
            val y = pointerY(d)
            val h = listState.layoutInfo.viewportSize.height
            val delta = when {
                y < edgePx -> -(edgePx - y) / 5f
                y > h - edgePx -> (y - (h - edgePx)) / 5f
                else -> 0f
            }
            if (delta != 0f) {
                listState.scrollBy(delta)
                target = computeTarget()
            }
            delay(16)
        }
    }

    Surface(
        color = PanelBg,
        shape = RoundedCornerShape(18.dp),
        shadowElevation = 8.dp,
        modifier = modifier.width(380.dp).heightIn(max = 680.dp),
    ) {
        Column(Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Layer", color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 17.sp)
                Spacer(Modifier.width(8.dp))
                Text("${state.layers.size}/${state.layerLimit}", color = Muted, fontSize = 13.sp)
                Spacer(Modifier.weight(1f))
                TextButton(onClick = {
                    selecting = !selecting
                    selected.clear()
                    if (selecting) selected.add(state.activeLayerId)
                }) { Text(if (selecting) "Batal" else "Pilih", color = Accent) }
                IconButton(onClick = { state.layerCmd { it.addLayer() } }) {
                    Icon(Icons.Filled.Add, "Tambah layer", tint = Color.White)
                }
            }
            Text(
                if (selecting) "Ketuk/geser kanan layer untuk memilih · tahan & seret untuk memindah semuanya"
                else "Ketuk layer aktif untuk pilihan · tahan & seret untuk memindah · geser kiri: kunci/duplikat/hapus · " +
                    "geser kanan: pilih · ketuk dua jari: opasitas · geser dua jari ke kanan: Alpha Lock · cubit beberapa layer: gabung",
                color = Muted, fontSize = 12.sp,
            )
            Spacer(Modifier.height(6.dp))
            Box(Modifier.onSizeChanged { listWidth = it.width.coerceAtLeast(1) }.then(gestures)) {
            LazyColumn(Modifier.heightIn(max = if (selecting) 500.dp else 560.dp), state = listState) {
                items(rows, key = { r -> r.key() }) { r ->
                    val rk = r.key()
                    val dragged = drag?.key == rk
                    val t = target
                    val pr = pinchRange
                    val rowIndex = rows.indexOf(r)
                    val dropHere = (t is DropTarget.Onto && rows.getOrNull(t.index)?.key() == rk) || (pr != null && rowIndex in pr)
                    Box(
                        Modifier
                            .zIndex(if (dragged) 1f else 0f)
                            .graphicsLayer {
                                val d = drag
                                if (d != null && d.key == rk) {
                                    val cur = listState.layoutInfo.visibleItemsInfo.firstOrNull { it.key == rk }?.offset ?: d.startOffset
                                    translationY = dragDy - (cur - d.startOffset)
                                    shadowElevation = 24f
                                    scaleX = 1.02f
                                    scaleY = 1.02f
                                    alpha = 0.92f
                                }
                                if (swipeKey == rk) translationX = swipeDx
                            }
                            .then(
                                when (r) {
                                    is PanelRow.Grp -> dragModifier(rk, 0, r.id, false)
                                    is PanelRow.Lay -> dragModifier(rk, r.info.id, 0, r.inGroup)
                                },
                            )
                            .then(if (dropHere) Modifier.border(2.dp, Accent, RoundedCornerShape(12.dp)) else Modifier),
                    ) {
                    if (dragged && selecting && selected.size > 1) {
                        Text(
                            "${selected.size} layer",
                            color = Color.White,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .zIndex(2f)
                                .padding(top = 2.dp, end = 4.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(Accent)
                                .padding(horizontal = 6.dp, vertical = 2.dp),
                        )
                    }
                    when (r) {
                        is PanelRow.Grp -> GroupRow(
                            state = state,
                            gid = r.id,
                            g = r.data,
                            count = r.count,
                            selecting = selecting,
                            allSelected = state.layers.filter { it.group == r.id }.all { it.id in selected },
                            menuOpen = groupMenuFor == r.id,
                            onTap = {
                                if (selecting) {
                                    val ids = state.layers.filter { it.group == r.id }.map { it.id }
                                    if (ids.all { it in selected }) selected.removeAll(ids) else ids.forEach { if (it !in selected) selected.add(it) }
                                } else {
                                    groupMenuFor = r.id
                                }
                            },
                            onMenuDismiss = { groupMenuFor = -1 },
                            onRename = { renameGroupFor = r.id; groupMenuFor = -1 },
                        )
                        is PanelRow.Lay -> {
                            val l = r.info
                            LayerRow(
                                state = state,
                                l = l,
                                inGroup = r.inGroup,
                                active = l.id == state.activeLayerId,
                                maskActive = l.mask != null && l.mask.id == state.activeLayerId,
                                expanded = expanded == l.id && !selecting,
                                menuOpen = menuFor == l.id,
                                maskMenuOpen = maskMenuFor == l.id,
                                revealed = revealed == l.id && !selecting,
                                selecting = selecting,
                                selected = l.id in selected,
                                onTap = {
                                    revealed = -1
                                    when {
                                        selecting -> if (l.id in selected) selected.remove(l.id) else selected.add(l.id)
                                        l.id == state.activeLayerId -> menuFor = l.id
                                        else -> state.layerCmd { it.selectLayer(l.id) }
                                    }
                                },
                                onMaskTap = {
                                    revealed = -1
                                    val m = l.mask
                                    if (m != null) {
                                        if (m.id == state.activeLayerId) maskMenuFor = l.id else state.layerCmd { it.selectLayer(m.id) }
                                    }
                                },
                                onMaskMenuDismiss = { maskMenuFor = -1 },
                                onHideActions = { revealed = -1 },
                                onToggleExpand = { expanded = if (expanded == l.id) -1 else l.id },
                                onMenuDismiss = { menuFor = -1 },
                                onRename = { renameFor = l; menuFor = -1 },
                                onSelectMany = { menuFor = -1; selecting = true; selected.clear(); selected.add(l.id) },
                            )
                        }
                    }
                    }
                }
                if (!selecting) item(key = "latar") { BackgroundRow(state) }
            }
            // Garis penanda tempat jatuh (menjorok bila layer akan masuk ke grup).
            val t = target
            if (drag != null && t is DropTarget.Gap) {
                val items = listState.layoutInfo.visibleItemsInfo
                val y = items.firstOrNull { it.index == t.k }?.offset
                    ?: items.lastOrNull()?.let { it.offset + it.size }
                val l = rows.getOrNull(t.k)
                val intoGroup = (l is PanelRow.Lay && l.inGroup && drag?.layerId != 0) || t.join
                if (y != null) {
                    Box(
                        Modifier
                            .offset { IntOffset(if (intoGroup) indentPx.toInt() else 0, y - 3) }
                            .fillMaxWidth()
                            .height(4.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(Accent),
                    )
                }
            }
            }
            if (selecting) {
                Row(
                    Modifier.fillMaxWidth().padding(top = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("${selected.size} dipilih", color = Color(0xFFDADAE0), fontSize = 13.sp, modifier = Modifier.weight(1f))
                    TextButton(
                        enabled = selected.isNotEmpty(),
                        onClick = { state.startTransform() },
                    ) { Text("Pindah bersama", fontWeight = FontWeight.SemiBold) }
                    TextButton(
                        enabled = selected.isNotEmpty(),
                        onClick = {
                            val ids = selected.toList()
                            state.layerCmd { it.groupLayers(ids) }
                            selecting = false
                            selected.clear()
                        },
                    ) { Text("Kelompokkan", fontWeight = FontWeight.SemiBold) }
                }
            }
        }
    }
    renameFor?.let { l ->
        var name by remember(l.id) { mutableStateOf(l.name) }
        AlertDialog(
            onDismissRequest = { renameFor = null },
            title = { Text("Ganti nama layer") },
            text = { OutlinedTextField(value = name, onValueChange = { name = it }, singleLine = true) },
            confirmButton = {
                TextButton(onClick = {
                    state.setLayerProps(l.id, state.propsOf(l).copy(name = name.ifBlank { l.name }))
                    renameFor = null
                }) { Text("Simpan") }
            },
            dismissButton = { TextButton(onClick = { renameFor = null }) { Text("Batal") } },
        )
    }
    if (renameGroupFor > 0) {
        val gid = renameGroupFor
        val cur = state.groups[gid]?.name ?: "Grup"
        var name by remember(gid) { mutableStateOf(cur) }
        AlertDialog(
            onDismissRequest = { renameGroupFor = -1 },
            title = { Text("Ganti nama grup") },
            text = { OutlinedTextField(value = name, onValueChange = { name = it }, singleLine = true) },
            confirmButton = {
                TextButton(onClick = {
                    val n = name.ifBlank { cur }
                    state.layerCmd { it.renameGroup(gid, n) }
                    renameGroupFor = -1
                }) { Text("Simpan") }
            },
            dismissButton = { TextButton(onClick = { renameGroupFor = -1 }) { Text("Batal") } },
        )
    }
}

/**
 * Daftar dari atas ke bawah: kepala grup muncul di atas anggota teratasnya; anggota grup terlipat (atau grup yang
 * sedang diseret, [hideMembersOf]) disembunyikan.
 */
private fun buildRows(state: AppState, hideMembersOf: Int = 0): List<PanelRow> {
    val out = ArrayList<PanelRow>()
    val seen = HashSet<Int>()
    for (l in state.layers.asReversed()) {
        val g = l.group
        val data = if (g != 0) state.groups[g] else null
        if (data == null) {
            out.add(PanelRow.Lay(l, false))
            continue
        }
        if (seen.add(g)) out.add(PanelRow.Grp(g, data, state.layers.count { it.group == g }))
        if (!data.collapsed && g != hideMembersOf) out.add(PanelRow.Lay(l, true))
    }
    return out
}

/** Jalankan hasil seret-lepas sesuai tempat jatuhnya. */
private fun applyDrop(state: AppState, rows: List<PanelRow>, ids: List<Int>, dragGroup: Int, t: DropTarget) {
    fun top(g: Int) = state.layers.lastOrNull { it.group == g }?.id
    fun bottom(g: Int) = state.layers.firstOrNull { it.group == g }?.id
    val dragLayer = ids.firstOrNull() ?: 0
    when (t) {
        is DropTarget.Onto -> {
            if (dragLayer == 0) return
            when (val r = rows.getOrNull(t.index)) {
                is PanelRow.Lay -> {
                    if (r.info.id in ids) return
                    val tid = r.info.id
                    if (r.inGroup) {
                        val g = r.info.group
                        state.layerCmd { it.placeLayers(ids, tid, true, g) }
                    } else {
                        state.layerCmd { it.groupWithMany(ids, tid) }
                    }
                }
                is PanelRow.Grp -> {
                    val a = top(r.id) ?: return
                    val g = r.id
                    state.layerCmd { it.placeLayers(ids, a, true, g) }
                }
                null -> Unit
            }
        }
        is DropTarget.Gap -> {
            val u = rows.getOrNull(t.k - 1)
            val l = rows.getOrNull(t.k)
            if (dragLayer != 0) {
                val anchor: Int
                val above: Boolean
                val group: Int
                when {
                    l is PanelRow.Lay && l.inGroup -> { anchor = l.info.id; above = true; group = l.info.group }
                    u is PanelRow.Lay && u.inGroup -> { anchor = u.info.id; above = false; group = if (t.join) u.info.group else 0 }
                    l is PanelRow.Grp -> { anchor = top(l.id) ?: return; above = true; group = 0 }
                    l is PanelRow.Lay -> { anchor = l.info.id; above = true; group = 0 }
                    u is PanelRow.Grp -> { anchor = bottom(u.id) ?: return; above = false; group = 0 }
                    u is PanelRow.Lay -> { anchor = u.info.id; above = false; group = 0 }
                    else -> return
                }
                state.layerCmd { it.placeLayers(ids, anchor, above, group) }
            } else if (dragGroup != 0) {
                val anchor: Int
                val above: Boolean
                when {
                    // Grup tidak bisa masuk ke grup lain: diletakkan di atas grup itu.
                    l is PanelRow.Lay && l.inGroup && l.info.group != dragGroup -> { anchor = top(l.info.group) ?: return; above = true }
                    l is PanelRow.Grp && l.id != dragGroup -> { anchor = top(l.id) ?: return; above = true }
                    l is PanelRow.Lay && !l.inGroup -> { anchor = l.info.id; above = true }
                    u is PanelRow.Lay && u.inGroup && u.info.group != dragGroup -> { anchor = bottom(u.info.group) ?: return; above = false }
                    u is PanelRow.Grp && u.id != dragGroup -> { anchor = bottom(u.id) ?: return; above = false }
                    u is PanelRow.Lay && !u.inGroup -> { anchor = u.info.id; above = false }
                    else -> return
                }
                state.layerCmd { it.placeGroup(dragGroup, anchor, above) }
            }
        }
    }
}

@Composable
private fun GroupRow(
    state: AppState,
    gid: Int,
    g: GroupData,
    count: Int,
    selecting: Boolean,
    allSelected: Boolean,
    menuOpen: Boolean,
    onTap: () -> Unit,
    onMenuDismiss: () -> Unit,
    onRename: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(Color(0x22FFFFFF))
            .clickable { onTap() }
            .padding(start = 2.dp, end = 4.dp, top = 2.dp, bottom = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (selecting) {
            Icon(
                if (allSelected) Icons.Filled.CheckCircle else Icons.Filled.RadioButtonUnchecked,
                null, tint = if (allSelected) Accent else Muted, modifier = Modifier.padding(horizontal = 8.dp).size(22.dp),
            )
        } else {
            IconButton(onClick = { state.gl { it.setGroupCollapsed(gid, !g.collapsed) } }) {
                Icon(if (g.collapsed) Icons.Filled.ChevronRight else Icons.Filled.ExpandMore, if (g.collapsed) "Buka grup" else "Lipat grup", tint = Color.White)
            }
        }
        Icon(Icons.Filled.Folder, null, tint = Color(0xFFF6C453), modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(g.name, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text("$count layer", color = Muted, fontSize = 11.sp)
        }
        IconButton(onClick = { state.layerCmd { it.setGroupVisible(gid, !g.visible) } }) {
            Icon(
                if (g.visible) Icons.Filled.Visibility else Icons.Filled.VisibilityOff,
                "Tampilkan grup",
                tint = if (g.visible) Color.White else Muted,
            )
        }
        Box {
            DropdownMenu(expanded = menuOpen, onDismissRequest = onMenuDismiss) {
                GroupMenu(state, gid, onMenuDismiss, onRename)
            }
        }
    }
}

@Composable
private fun GroupMenu(state: AppState, gid: Int, dismiss: () -> Unit, onRename: () -> Unit) {
    fun act(block: () -> Unit) { block(); dismiss() }
    DropdownMenuItem(text = { Text("Ganti nama grup") }, leadingIcon = { Icon(Icons.Filled.Edit, null) }, onClick = onRename)
    DropdownMenuItem(text = { Text("Pisahkan (bubarkan grup)") }, onClick = { act { state.layerCmd { it.ungroup(gid) } } })
    DropdownMenuItem(
        text = { Text("Pindah & ubah ukuran grup") },
        leadingIcon = { Icon(Icons.Filled.OpenWith, null) },
        onClick = { act { state.startTransform(state.layers.filter { it.group == gid }.map { it.id }, useSelection = false) } },
    )
    DropdownMenuItem(text = { Text("Ratakan jadi satu layer") }, onClick = { act { state.layerCmd { it.flattenGroup(gid) } } })
    DropdownMenuItem(
        text = { Text("Duplikat grup") },
        leadingIcon = { Icon(Icons.Filled.ContentCopy, null) },
        onClick = { act { state.layerCmd { it.duplicateGroup(gid) } } },
    )
    HorizontalDivider()
    DropdownMenuItem(
        text = { Text("Naikkan grup") },
        leadingIcon = { Icon(Icons.Filled.ArrowUpward, null) },
        onClick = { act { state.layerCmd { it.moveGroup(gid, up = true) } } },
    )
    DropdownMenuItem(
        text = { Text("Turunkan grup") },
        leadingIcon = { Icon(Icons.Filled.ArrowDownward, null) },
        onClick = { act { state.layerCmd { it.moveGroup(gid, up = false) } } },
    )
    HorizontalDivider()
    DropdownMenuItem(
        text = { Text("Hapus grup & isinya", color = Color(0xFFFF8A80)) },
        leadingIcon = { Icon(Icons.Filled.Delete, null, tint = Color(0xFFFF8A80)) },
        onClick = { act { state.layerCmd { it.deleteGroup(gid) } } },
    )
}

@Composable
private fun LayerRow(
    state: AppState,
    l: LayerInfo,
    inGroup: Boolean,
    active: Boolean,
    maskActive: Boolean,
    expanded: Boolean,
    menuOpen: Boolean,
    maskMenuOpen: Boolean,
    revealed: Boolean,
    selecting: Boolean,
    selected: Boolean,
    onTap: () -> Unit,
    onMaskTap: () -> Unit,
    onMaskMenuDismiss: () -> Unit,
    onHideActions: () -> Unit,
    onToggleExpand: () -> Unit,
    onMenuDismiss: () -> Unit,
    onRename: () -> Unit,
    onSelectMany: () -> Unit,
) {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(start = if (inGroup) 18.dp else 0.dp, top = 3.dp, bottom = 3.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(if (active || maskActive) Color(0x663D8BFF) else Color(0x14FFFFFF)),
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .clickable { onTap() }
                .padding(start = if (l.clip) 22.dp else 8.dp, end = 4.dp, top = 6.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (selecting) {
                Icon(
                    if (selected) Icons.Filled.CheckCircle else Icons.Filled.RadioButtonUnchecked,
                    null, tint = if (selected) Accent else Muted, modifier = Modifier.padding(end = 8.dp).size(22.dp),
                )
            }
            if (l.clip) {
                Text("↳", color = Muted, modifier = Modifier.padding(end = 4.dp))
            }
            val m = l.mask
            Box(
                Modifier
                    .size(width = if (m != null) 52.dp else 64.dp, height = 46.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .checker()
                    .border(if (m != null && active) 2.dp else 1.dp, if (m != null && active) Color.White else Color(0x44FFFFFF), RoundedCornerShape(6.dp)),
            ) {
                state.thumbs[l.id]?.let {
                    Image(it, contentDescription = null, contentScale = ContentScale.Fit, modifier = Modifier.size(width = if (m != null) 52.dp else 64.dp, height = 46.dp))
                }
            }
            if (m != null) {
                // Masker layer: ketuk untuk menggambar di masker, ketuk lagi untuk menunya.
                Icon(Icons.Filled.Link, null, tint = Muted, modifier = Modifier.padding(horizontal = 2.dp).size(12.dp))
                Box {
                    Box(
                        Modifier
                            .size(width = 40.dp, height = 46.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(Color.Black)
                            .border(if (maskActive) 2.dp else 1.dp, if (maskActive) Color.White else Color(0x44FFFFFF), RoundedCornerShape(6.dp))
                            .clickable { onMaskTap() },
                    ) {
                        state.thumbs[m.id]?.let {
                            Image(
                                it, contentDescription = "Masker layer", contentScale = ContentScale.Crop,
                                modifier = Modifier.size(width = 40.dp, height = 46.dp).graphicsLayer { alpha = if (m.visible) 1f else 0.35f },
                            )
                        }
                        if (!m.visible) Icon(Icons.Filled.Close, null, tint = Color(0xFFFF8A80), modifier = Modifier.align(Alignment.Center).size(18.dp))
                    }
                    DropdownMenu(expanded = maskMenuOpen, onDismissRequest = onMaskMenuDismiss) {
                        MaskMenu(state, l, m, onMaskMenuDismiss)
                    }
                }
            }
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(l.name, color = Color.White, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (maskActive) Text("Masker  ", color = Color(0xFF9FD2FF), fontSize = 11.sp)
                    if (l.reference) Text("Referensi  ", color = Color(0xFF6FD3A8), fontSize = 11.sp)
                    if (l.alphaLock) Text("Alpha lock  ", color = Color(0xFFF6C453), fontSize = 11.sp)
                    if (l.assist) Text("Bantuan  ", color = Color(0xFF6FB6FF), fontSize = 11.sp)
                    if (l.text != null) Text("Teks  ", color = Color(0xFFB59CFF), fontSize = 11.sp)
                    if (l.locked) Icon(Icons.Filled.Lock, null, tint = Muted, modifier = Modifier.size(12.dp))
                }
            }
            if (revealed) {
                // Tombol hasil geser kiri.
                SwipeAction(if (l.locked) Icons.Filled.LockOpen else Icons.Filled.Lock, if (l.locked) "Buka" else "Kunci", Color(0xFF5A6070)) {
                    onHideActions()
                    state.setLayerProps(l.id, state.propsOf(l).copy(locked = !l.locked))
                }
                SwipeAction(Icons.Filled.ContentCopy, "Duplikat", Color(0xFF3D6FD8)) {
                    onHideActions()
                    state.layerCmd { it.duplicateLayer(l.id) }
                }
                SwipeAction(Icons.Filled.Delete, "Hapus", Color(0xFFD9534F)) {
                    onHideActions()
                    state.layerCmd { it.deleteLayer(l.id) }
                }
            } else {
            Text(
                BlendShort.getOrElse(l.blend) { "N" } + if (l.opacity < 0.995f) " ${(l.opacity * 100).roundToInt()}" else "",
                color = if (expanded) Accent else Color(0xFFDADAE0),
                fontWeight = FontWeight.SemiBold,
                fontSize = 13.sp,
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .clickable { onToggleExpand() }
                    .padding(horizontal = 8.dp, vertical = 8.dp),
            )
            IconButton(onClick = { state.setLayerProps(l.id, state.propsOf(l).copy(visible = !l.visible)) }) {
                Icon(
                    if (l.visible) Icons.Filled.Visibility else Icons.Filled.VisibilityOff,
                    "Tampilkan",
                    tint = if (l.visible) Color.White else Muted,
                )
            }
            }
            Box {
                DropdownMenu(expanded = menuOpen, onDismissRequest = onMenuDismiss) {
                    LayerMenu(state, l, onMenuDismiss, onRename, onSelectMany)
                }
            }
        }
        if (expanded) BlendControls(state, l)
    }
}

@Composable
private fun SwipeAction(icon: ImageVector, label: String, bg: Color, onClick: () -> Unit) {
    Column(
        Modifier
            .padding(start = 4.dp)
            .size(width = 58.dp, height = 46.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(bg)
            .clickable { onClick() },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = androidx.compose.foundation.layout.Arrangement.Center,
    ) {
        Icon(icon, null, tint = Color.White, modifier = Modifier.size(18.dp))
        Text(label, color = Color.White, fontSize = 10.sp, maxLines = 1)
    }
}

@Composable
private fun MaskMenu(state: AppState, owner: LayerInfo, m: LayerInfo, dismiss: () -> Unit) {
    fun act(block: () -> Unit) { block(); dismiss() }
    DropdownMenuItem(
        text = { Text("Balik masker") },
        leadingIcon = { Icon(Icons.Filled.InvertColors, null) },
        onClick = { act { state.layerCmd { it.invertLayer(m.id) } } },
    )
    DropdownMenuItem(
        text = { Text(if (m.visible) "Matikan masker" else "Nyalakan masker") },
        leadingIcon = { Icon(if (m.visible) Icons.Filled.VisibilityOff else Icons.Filled.Visibility, null) },
        onClick = { act { state.setLayerProps(m.id, state.propsOf(m).copy(visible = !m.visible)) } },
    )
    DropdownMenuItem(
        text = { Text("Isi masker putih (tampilkan semua)") },
        leadingIcon = { Icon(Icons.Filled.FormatColorFill, null) },
        onClick = { act { state.layerCmd { it.fillWholeLayer(m.id, android.graphics.Color.WHITE) } } },
    )
    DropdownMenuItem(
        text = { Text("Pilih isi masker") },
        leadingIcon = { Icon(Icons.Filled.SelectAll, null) },
        onClick = { act { state.selectLayerContents(m.id) } },
    )
    HorizontalDivider()
    DropdownMenuItem(
        text = { Text("Terapkan masker") },
        leadingIcon = { Icon(Icons.Filled.FilterBAndW, null) },
        onClick = { act { state.layerCmd { it.applyMask(owner.id) } } },
    )
    DropdownMenuItem(
        text = { Text("Hapus masker", color = Color(0xFFFF8A80)) },
        leadingIcon = { Icon(Icons.Filled.Delete, null, tint = Color(0xFFFF8A80)) },
        onClick = { act { state.layerCmd { it.deleteMask(owner.id) } } },
    )
}

/** Kelompok mode campur seperti di Procreate (indeks ke [BlendNames]). */
private val BlendGroups = listOf(
    listOf(1, 2, 3, 4, 5),
    listOf(0),
    listOf(6, 7, 8, 9, 10),
    listOf(11, 12, 13, 14, 15, 16, 17),
    listOf(18, 19, 20, 21),
    listOf(22, 23, 24, 25),
)

private fun blendIcon(i: Int): ImageVector = when (i) {
    0 -> Icons.Filled.Layers
    1 -> Icons.Filled.Close
    2 -> Icons.Filled.DarkMode
    3 -> Icons.Filled.LocalFireDepartment
    4 -> Icons.Filled.Whatshot
    5 -> Icons.Filled.InvertColors
    6 -> Icons.Filled.LightMode
    7 -> Icons.Filled.Gradient
    8 -> Icons.Filled.FlashOn
    9 -> Icons.Filled.AddBox
    10 -> Icons.Filled.AddCircle
    11 -> Icons.Filled.Tonality
    12 -> Icons.Outlined.Lightbulb
    13 -> Icons.Filled.Lightbulb
    14 -> Icons.Filled.EmojiObjects
    15 -> Icons.Filled.Highlight
    16 -> Icons.Filled.PushPin
    17 -> Icons.Filled.GridOn
    18 -> Icons.Filled.Contrast
    19 -> Icons.Filled.FilterBAndW
    20 -> Icons.Filled.RemoveCircle
    21 -> Icons.Filled.Functions
    22 -> Icons.Filled.Palette
    23 -> Icons.Filled.Opacity
    24 -> Icons.Filled.ColorLens
    else -> Icons.Filled.WbSunny
}

/**
 * Pengaturan layer seperti di Procreate (ketuk huruf mode di baris layer): opasitas dengan persen, lalu daftar
 * semua mode campur berkelompok dengan ikon; yang terpilih disorot biru dan langsung diterapkan.
 */
@Composable
private fun BlendControls(state: AppState, l: LayerInfo) {
    var start by remember(l.id) { mutableStateOf<LayerProps?>(null) }
    Column(Modifier.padding(start = 8.dp, end = 8.dp, top = 2.dp, bottom = 8.dp)) {
        LabeledSlider(
            "Opasitas",
            l.opacity,
            onChange = {
                if (start == null) start = state.propsOf(l)
                state.setLayerProps(l.id, state.propsOf(l).copy(opacity = it), undoable = false)
            },
            onDone = {
                val s = start
                start = null
                if (s != null) state.gl { e -> e.commitProps(l.id, s); e.postLayers() }
            },
        )
        // Posisi baris mode yang sedang dipakai, supaya daftar langsung terbuka di situ.
        val flat = remember { BlendGroups.flatMapIndexed { gi, g -> (if (gi > 0) listOf(-1) else emptyList()) + g } }
        val listState = rememberLazyListState(initialFirstVisibleItemIndex = (flat.indexOf(l.blend) - 2).coerceAtLeast(0))
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxWidth()
                .height(292.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(Color(0x12FFFFFF)),
        ) {
            items(flat.size) { k ->
                val mode = flat[k]
                if (mode < 0) {
                    Box(Modifier.fillMaxWidth().height(6.dp).background(Color(0x44000000)))
                } else {
                    val sel = mode == l.blend
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .height(42.dp)
                            .padding(horizontal = 4.dp, vertical = 2.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (sel) Color(0xFF2F6FED) else Color.Transparent)
                            .clickable { if (!sel) state.setLayerProps(l.id, state.propsOf(l).copy(blend = mode)) }
                            .padding(horizontal = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            BlendNames[mode],
                            color = if (sel) Color.White else Color(0xFFDADAE0),
                            fontSize = 14.sp,
                            fontWeight = if (sel) FontWeight.SemiBold else FontWeight.Normal,
                            modifier = Modifier.weight(1f),
                        )
                        Icon(blendIcon(mode), null, tint = if (sel) Color.White else Muted, modifier = Modifier.size(18.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun LayerMenu(state: AppState, l: LayerInfo, dismiss: () -> Unit, onRename: () -> Unit, onSelectMany: () -> Unit) {
    val idx = state.layers.indexOfFirst { it.id == l.id }
    fun act(block: () -> Unit) { block(); dismiss() }
    DropdownMenuItem(text = { Text("Ganti nama") }, leadingIcon = { Icon(Icons.Filled.Edit, null) }, onClick = onRename)
    DropdownMenuItem(
        text = { Text("Isi layer dengan warna") },
        leadingIcon = { Icon(Icons.Filled.FormatColorFill, null) },
        onClick = { act { val c = state.color; state.layerCmd { it.fillWholeLayer(l.id, c) } } },
    )
    DropdownMenuItem(text = { Text("Bersihkan") }, onClick = { act { state.layerCmd { it.clearLayer(l.id) } } })
    DropdownMenuItem(
        text = { Text("Pilih isi layer") },
        leadingIcon = { Icon(Icons.Filled.SelectAll, null) },
        onClick = { act { state.selectLayerContents(l.id) } },
    )
    DropdownMenuItem(
        text = { Text("Balik warna (invert)") },
        leadingIcon = { Icon(Icons.Filled.InvertColors, null) },
        onClick = { act { state.layerCmd { it.invertLayer(l.id) } } },
    )
    HorizontalDivider()
    if (l.mask == null) {
        DropdownMenuItem(
            text = { Text("Masker layer") },
            leadingIcon = { Icon(Icons.Filled.Contrast, null) },
            enabled = l.text == null,
            onClick = { act { state.layerCmd { it.addMask(l.id) } } },
        )
    }
    DropdownMenuItem(
        text = { Text("Referensi (untuk ColorDrop)") },
        trailingIcon = { if (l.reference) Icon(Icons.Filled.Check, null) },
        onClick = { act { state.layerCmd { it.setReference(l.id, !l.reference) } } },
    )
    DropdownMenuItem(
        text = { Text("Alpha Lock") },
        trailingIcon = { if (l.alphaLock) Icon(Icons.Filled.Check, null) },
        onClick = { act { state.setLayerProps(l.id, state.propsOf(l).copy(alphaLock = !l.alphaLock)) } },
    )
    DropdownMenuItem(
        text = { Text("Clipping Mask") },
        trailingIcon = { if (l.clip) Icon(Icons.Filled.Check, null) },
        onClick = { act { state.setLayerProps(l.id, state.propsOf(l).copy(clip = !l.clip)) } },
    )
    DropdownMenuItem(
        text = { Text("Kunci layer") },
        trailingIcon = { if (l.locked) Icon(Icons.Filled.Check, null) },
        onClick = { act { state.setLayerProps(l.id, state.propsOf(l).copy(locked = !l.locked)) } },
    )
    if (l.text != null) {
        DropdownMenuItem(
            text = { Text("Edit teks") },
            onClick = { act { state.startText(l.id) } },
        )
    }
    DropdownMenuItem(
        text = { Text("Bantuan menggambar") },
        trailingIcon = { if (l.assist) Icon(Icons.Filled.Check, null) },
        onClick = { act { state.setLayerProps(l.id, state.propsOf(l).copy(assist = !l.assist)) } },
    )
    HorizontalDivider()
    if (l.group == 0) {
        DropdownMenuItem(text = { Text("Buat grup dari layer ini") }, leadingIcon = { Icon(Icons.Filled.Folder, null) }, onClick = { act { state.layerCmd { it.groupLayers(listOf(l.id)) } } })
    } else {
        DropdownMenuItem(text = { Text("Keluarkan dari grup") }, leadingIcon = { Icon(Icons.Filled.Folder, null) }, onClick = { act { state.layerCmd { it.leaveGroup(l.id) } } })
    }
    DropdownMenuItem(text = { Text("Pilih beberapa layer…") }, onClick = onSelectMany)
    HorizontalDivider()
    DropdownMenuItem(text = { Text("Gabung ke bawah") }, onClick = { act { state.layerCmd { it.mergeDown(l.id) } } })
    val below = state.layers.getOrNull(idx - 1)
    DropdownMenuItem(
        text = { Text("Satukan ke grup dengan layer bawah") },
        leadingIcon = { Icon(Icons.Filled.Folder, null) },
        enabled = below != null,
        onClick = { act { if (below != null) state.layerCmd { it.groupWith(l.id, below.id) } } },
    )
    DropdownMenuItem(
        text = { Text("Salin layer") },
        leadingIcon = { Icon(Icons.Filled.ContentCopy, null) },
        onClick = { act { state.copyLayer(l.id) } },
    )
    DropdownMenuItem(
        text = { Text("Duplikat") },
        leadingIcon = { Icon(Icons.Filled.ContentCopy, null) },
        onClick = { act { state.layerCmd { it.duplicateLayer(l.id) } } },
    )
    DropdownMenuItem(
        text = { Text("Naikkan") },
        leadingIcon = { Icon(Icons.Filled.ArrowUpward, null) },
        enabled = idx < state.layers.size - 1 || l.group != 0,
        onClick = { act { state.layerCmd { it.moveLayerStep(l.id, up = true) } } },
    )
    DropdownMenuItem(
        text = { Text("Turunkan") },
        leadingIcon = { Icon(Icons.Filled.ArrowDownward, null) },
        enabled = idx > 0 || l.group != 0,
        onClick = { act { state.layerCmd { it.moveLayerStep(l.id, up = false) } } },
    )
    HorizontalDivider()
    DropdownMenuItem(
        text = { Text("Hapus", color = Color(0xFFFF8A80)) },
        leadingIcon = { Icon(Icons.Filled.Delete, null, tint = Color(0xFFFF8A80)) },
        onClick = { act { state.layerCmd { it.deleteLayer(l.id) } } },
    )
}

@Composable
private fun BackgroundRow(state: AppState) {
    var settings by remember { mutableStateOf(false) }
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(Color(0x14FFFFFF))
            .clickable { settings = true }
            .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(width = 64.dp, height = 46.dp)
                .clip(RoundedCornerShape(6.dp))
                .then(if (state.bgVisible) Modifier.background(Color(state.bgColor)) else Modifier.checker())
                .border(1.dp, Color(0x44FFFFFF), RoundedCornerShape(6.dp)),
        )
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text("Warna latar", color = Color.White, fontSize = 14.sp)
            Text("Ketuk untuk mengatur latar", color = Muted, fontSize = 11.sp)
            Text(
                "Pakai warna aktif",
                color = Accent,
                fontSize = 12.sp,
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .clickable { val c = state.color; val v = state.bgVisible; state.gl { it.setBackground(c, v) } }
                    .padding(vertical = 2.dp),
            )
        }
        IconButton(onClick = { val c = state.bgColor; val v = !state.bgVisible; state.gl { it.setBackground(c, v) } }) {
            Icon(
                if (state.bgVisible) Icons.Filled.Visibility else Icons.Filled.VisibilityOff,
                "Tampilkan latar",
                tint = if (state.bgVisible) Color.White else Muted,
            )
        }
        Box(Modifier.size(1.dp).clip(CircleShape))
    }
    if (settings) BackgroundDialog(state) { settings = false }
}

/**
 * Pengaturan latar (seperti mengetuk layer Warna latar di Procreate): warna latar dengan pemilih warna,
 * sembunyikan/tampilkan latar. Perubahan langsung terlihat di kanvas; Selesai = satu langkah urungkan,
 * Batal = kembali seperti semula.
 */
@Composable
private fun BackgroundDialog(state: AppState, onClose: () -> Unit) {
    val c0 = remember { state.bgColor }
    val v0 = remember { state.bgVisible }
    var c by remember { mutableIntStateOf(state.bgColor) }
    var vis by remember { mutableStateOf(state.bgVisible) }
    fun finish() {
        state.commitBackground(c0, v0)
        onClose()
    }
    AlertDialog(
        onDismissRequest = { finish() },
        title = { Text("Latar kanvas") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                DialogImmersive()
                CompactColorPicker(
                    color = c,
                    onChange = {
                        c = it
                        vis = true
                        state.previewBackground(it, true)
                    },
                    swatches = listOf(
                        android.graphics.Color.WHITE, 0xFFF4E3CF.toInt(), 0xFFFFF8E7.toInt(), 0xFFD1D1D6.toInt(),
                        0xFF8E8E93.toInt(), 0xFF3A3A3C.toInt(), android.graphics.Color.BLACK,
                    ) + state.palette.toList().take(14),
                    width = 300.dp,
                )
                Spacer(Modifier.height(10.dp))
                Text(
                    "Tekstur kertas…",
                    color = Accent,
                    fontSize = 14.sp,
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .clickable {
                            state.commitBackground(c0, v0)
                            onClose()
                            state.startPaperEdit()
                        }
                        .padding(vertical = 6.dp),
                )
                Text(
                    "Pakai warna aktif",
                    color = Accent,
                    fontSize = 14.sp,
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .clickable {
                            c = state.color
                            vis = true
                            state.previewBackground(c, true)
                        }
                        .padding(vertical = 6.dp),
                )
                Row(
                    Modifier
                        .width(300.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { vis = !vis; state.previewBackground(c, vis) }
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("Sembunyikan latar", fontSize = 14.sp)
                        Text("Latar transparan (kotak-kotak); ekspor PNG ikut transparan", color = Muted, fontSize = 12.sp)
                    }
                    Switch(
                        checked = !vis,
                        onCheckedChange = { vis = !it; state.previewBackground(c, vis) },
                        colors = SwitchDefaults.colors(checkedTrackColor = Accent),
                    )
                }
            }
        },
        confirmButton = { TextButton(onClick = { finish() }) { Text("Selesai", fontWeight = FontWeight.SemiBold) } },
        dismissButton = {
            TextButton(onClick = {
                state.previewBackground(c0, v0)
                onClose()
            }) { Text("Batal") }
        },
    )
}
