package id.hananote.ui

import androidx.compose.foundation.gestures.ScrollableState
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.LocalPinnableContainer
import androidx.compose.ui.layout.PinnableContainer
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.toSize
import androidx.compose.ui.zIndex
import id.hananote.data.CrashLog

internal fun rectInRoot(coords: LayoutCoordinates): Rect = Rect(coords.positionInRoot(), coords.size.toSize())

/**
 * Seret untuk mengubah urutan di daftar (halaman, buku, catatan). Kotak tiap item yang tampil dicatat
 * dalam koordinat akar; tujuan dipilih dari kotak yang paling dekat dengan jari. Bila jari mendekati tepi
 * daftar, daftarnya bergulir sendiri.
 *
 * [onMove] dan [clamp] boleh diganti tiap kali digambar ulang supaya selalu memakai daftar terbaru.
 */
@Stable
internal class ReorderState(
    private val horizontal: Boolean = false,
    /** Seberapa besar item diperbesar saat diangkat. */
    val liftScale: Float = 1.06f,
) {
    class Slot(var index: Int, var rect: Rect)

    var onMove: (from: Int, to: Int) -> Unit = { _, _ -> }
    /** Batasi tujuan, misalnya supaya buku yang disematkan tetap di kelompoknya. */
    var clamp: (from: Int, to: Int) -> Int = { _, to -> to }

    /** Kunci item yang sedang diseret (null bila tidak ada). */
    var dragKey by mutableStateOf<Any?>(null)
        private set
    var from by mutableIntStateOf(-1)
        private set
    var target by mutableIntStateOf(-1)
        private set
    private var finger by mutableStateOf(Offset.Zero)
    private var dragRect by mutableStateOf(Rect.Zero)
    private var grab = Offset.Zero
    val slots = HashMap<Any, Slot>()
    var listBounds = Rect.Zero

    val dragging: Boolean get() = dragKey != null

    fun place(key: Any, index: Int, coords: LayoutCoordinates) {
        val r = rectInRoot(coords)
        val s = slots[key]
        if (s == null) slots[key] = Slot(index, r) else { s.index = index; s.rect = r }
        if (key == dragKey) dragRect = r
    }

    fun start(key: Any, pointInRoot: Offset): Boolean {
        val s = slots[key] ?: return false
        dragKey = key
        from = s.index
        target = s.index
        finger = pointInRoot
        dragRect = s.rect
        grab = pointInRoot - s.rect.topLeft
        CrashLog.note("seret urutan ${s.index + 1}")
        return true
    }

    /** [delta] gerakan jari di koordinat item yang sedang diperbesar; dikembalikan ke ukuran layar. */
    fun drag(delta: Offset) {
        finger += delta * liftScale
        updateTarget()
    }

    private fun updateTarget() {
        var best = target
        var bestD = Float.MAX_VALUE
        for (s in slots.values) {
            if (!s.rect.overlaps(listBounds)) continue
            val d = (s.rect.center - finger).getDistanceSquared()
            if (d < bestD) { bestD = d; best = s.index }
        }
        target = if (from >= 0) clamp(from, best) else best
    }

    /** Geseran item yang diseret dari tempat asalnya di daftar. */
    fun translation(): Offset = finger - grab - dragRect.topLeft

    fun end() {
        val f = from
        val t = target
        reset()
        if (f >= 0 && t >= 0 && f != t) onMove(f, t)
    }

    fun reset() {
        dragKey = null
        from = -1
        target = -1
    }

    /** Gulir daftar selama jari berada di dekat tepinya. */
    suspend fun autoScroll(state: ScrollableState, edge: Float) {
        while (dragKey != null) {
            withFrameNanos { }
            val p = if (horizontal) finger.x else finger.y
            val lo = if (horizontal) listBounds.left else listBounds.top
            val hi = if (horizontal) listBounds.right else listBounds.bottom
            val k = when {
                p < lo + edge -> -((lo + edge - p) / edge)
                p > hi - edge -> (p - (hi - edge)) / edge
                else -> 0f
            }.coerceIn(-1.5f, 1.5f)
            if (k != 0f) {
                state.scrollBy(k * edge * 0.35f)
                updateTarget()
            }
        }
    }
}

/**
 * Pasang pada akar tiap item daftar: mencatat kotaknya, menjaga item tetap hidup selama diseret walau
 * daftarnya bergulir, dan menggambar item yang diseret melayang mengikuti jari.
 */
@Composable
internal fun Modifier.reorderItem(state: ReorderState, key: Any, index: Int): Modifier {
    val dragging = state.dragKey == key
    SideEffect { state.slots[key]?.index = index }
    DisposableEffect(key) { onDispose { state.slots.remove(key) } }
    return this
        .onGloballyPositioned { state.place(key, index, it) }
        .zIndex(if (dragging) 1f else 0f)
        .graphicsLayer {
            if (dragging) {
                val t = state.translation()
                translationX = t.x
                translationY = t.y
                scaleX = state.liftScale
                scaleY = state.liftScale
                alpha = 0.94f
                shadowElevation = 16.dp.toPx()
                shape = RoundedCornerShape(8.dp)
            }
        }
}

/** Penahan agar item yang diseret tidak dibuang daftar saat bergulir jauh. */
private class PinHolder { var handle: PinnableContainer.PinnedHandle? = null }

/**
 * Tahan lalu seret untuk memindah item. Bila ditahan lalu dilepas tanpa digeser, [onLongPress] dipanggil
 * (misalnya membuka menu).
 */
@Composable
internal fun Modifier.reorderOnLongPress(state: ReorderState, key: Any, onLongPress: () -> Unit): Modifier {
    val pinnable = LocalPinnableContainer.current
    val haptic = LocalHapticFeedback.current
    val pin = remember { PinHolder() }
    val press by rememberUpdatedState(onLongPress)
    var coords by remember { mutableStateOf<LayoutCoordinates?>(null) }
    DisposableEffect(Unit) { onDispose { pin.handle?.release(); pin.handle = null } }
    return this
        .onGloballyPositioned { coords = it }
        .pointerInput(key, state) {
            var moved = 0f
            var started = false
            detectDragGesturesAfterLongPress(
                onDragStart = { at ->
                    moved = 0f
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    val c = coords
                    started = c != null && c.isAttached && state.start(key, c.localToRoot(at))
                    if (started) { pin.handle?.release(); pin.handle = pinnable?.pin() }
                },
                onDragEnd = {
                    pin.handle?.release(); pin.handle = null
                    if (!started || moved < viewConfiguration.touchSlop) {
                        if (started) state.reset()
                        press()
                    } else {
                        state.end()
                    }
                    started = false
                },
                onDragCancel = {
                    pin.handle?.release(); pin.handle = null
                    if (started) state.reset()
                    started = false
                },
            ) { change, amount ->
                change.consume()
                moved += amount.getDistance()
                if (started && state.dragKey == key) state.drag(amount)
            }
        }
}

/** Pegangan seret (langsung, tanpa ditahan) untuk memindah item. */
@Composable
internal fun Modifier.reorderHandle(state: ReorderState, key: Any): Modifier {
    val pinnable = LocalPinnableContainer.current
    val pin = remember { PinHolder() }
    var coords by remember { mutableStateOf<LayoutCoordinates?>(null) }
    DisposableEffect(Unit) { onDispose { pin.handle?.release(); pin.handle = null } }
    return this
        .onGloballyPositioned { coords = it }
        .pointerInput(key, state) {
            detectDragGestures(
                onDragStart = { at ->
                    val c = coords
                    if (c != null && c.isAttached && state.start(key, c.localToRoot(at))) {
                        pin.handle?.release()
                        pin.handle = pinnable?.pin()
                    }
                },
                onDragEnd = { pin.handle?.release(); pin.handle = null; state.end() },
                onDragCancel = { pin.handle?.release(); pin.handle = null; state.reset() },
            ) { change, amount ->
                change.consume()
                if (state.dragKey == key) state.drag(amount)
            }
        }
}
