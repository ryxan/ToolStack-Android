package com.toolstack.io.ui.components

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.lazy.LazyListItemInfo
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex

/**
 * State object driving long-press drag-to-reorder on a LazyColumn.
 *
 * The gesture is detected **per-item** (not on the LazyColumn container) so that
 * clickable Card children don't swallow the long-press before it can be recognised.
 *
 * Usage:
 * ```
 * val listState = rememberLazyListState()
 * val dragState = rememberDragDropState(listState) { from, to -> reorder(from, to) }
 *
 * LazyColumn(state = listState) {
 *     itemsIndexed(items, key = { _, item -> item.id }) { index, item ->
 *         Card(modifier = Modifier.draggedItem(dragState, index)) { … }
 *     }
 * }
 * ```
 *
 * [onMove] is called every time the dragged item crosses into a new slot (not just on
 * drop), so the list animates live. The caller must update its backing list in [onMove]
 * immediately for the visual swap to work.
 */
class DragDropState(
    val lazyListState: LazyListState,
    private val onMove: (from: Int, to: Int) -> Unit
) {
    /** Index of the item currently being dragged; null when idle. */
    var draggingItemIndex by mutableStateOf<Int?>(null)
        private set

    /** Cumulative drag delta (px) accumulated since the drag started. */
    private var draggingItemDraggedDelta by mutableFloatStateOf(0f)

    /** Pixel offset of the dragged item's top edge when the drag began. */
    private var draggingItemInitialOffset by mutableIntStateOf(0)

    /** True while a drag gesture is in progress. */
    val isDragging: Boolean get() = draggingItemIndex != null

    /**
     * Translation (px) to apply to the dragged item's graphicsLayer so it
     * follows the finger while the list re-slots items beneath it.
     */
    internal val draggingItemOffset: Float
        get() {
            val info = lazyListState.layoutInfo.visibleItemsInfo
                .firstOrNull { it.index == draggingItemIndex } ?: return 0f
            return draggingItemInitialOffset + draggingItemDraggedDelta - info.offset
        }

    /**
     * Called by [draggedItem] when a long-press starts on the item at [index].
     * [itemOffset] is the item's current top-edge offset within the LazyColumn viewport.
     */
    internal fun onDragStart(index: Int, itemOffset: Int) {
        draggingItemIndex = index
        draggingItemInitialOffset = itemOffset
        draggingItemDraggedDelta = 0f
    }

    internal fun onDragEnd() {
        draggingItemIndex = null
        draggingItemDraggedDelta = 0f
    }

    internal fun onDrag(delta: Float) {
        draggingItemDraggedDelta += delta

        val draggingIndex = draggingItemIndex ?: return
        val currentItem: LazyListItemInfo = lazyListState.layoutInfo.visibleItemsInfo
            .firstOrNull { it.index == draggingIndex } ?: return

        // Centre of the dragged item at its current visual (translated) position.
        val visualTop    = currentItem.offset + draggingItemOffset
        val visualBottom = visualTop + currentItem.size
        val visualMid    = (visualTop + visualBottom) / 2f

        val target = lazyListState.layoutInfo.visibleItemsInfo.firstOrNull { item ->
            visualMid.toInt() in item.offset..(item.offset + item.size) &&
                item.index != draggingIndex
        } ?: return

        onMove(draggingIndex, target.index)
        draggingItemIndex = target.index
    }
}

@Composable
fun rememberDragDropState(
    lazyListState: LazyListState = rememberLazyListState(),
    onMove: (from: Int, to: Int) -> Unit
): DragDropState = remember(lazyListState) {
    DragDropState(lazyListState, onMove)
}

/**
 * Apply this modifier to each list item composable.
 *
 * It attaches [detectDragGesturesAfterLongPress] directly to the item so that the
 * gesture competes at the item level, where it wins over the card's click handler
 * (long-press has higher priority than a tap once the threshold is exceeded).
 *
 * Visual effects while dragging:
 * - Item floats above peers via [zIndex]
 * - Item translates vertically to follow the finger
 * - Subtle drop-shadow via graphicsLayer shadowElevation
 */
@Composable
fun Modifier.draggedItem(
    dragDropState: DragDropState,
    index: Int
): Modifier {
    val isDraggingThis = dragDropState.draggingItemIndex == index

    val elevation by animateDpAsState(
        targetValue = if (isDraggingThis) 8.dp else 0.dp,
        label = "drag_elevation"
    )

    return this
        .zIndex(if (isDraggingThis) 1f else 0f)
        .graphicsLayer {
            translationY = if (isDraggingThis) dragDropState.draggingItemOffset else 0f
            shadowElevation = elevation.toPx()
        }
        .pointerInput(dragDropState, index) {
            detectDragGesturesAfterLongPress(
                onDragStart = {
                    // Resolve this item's current offset from the LazyList layout info.
                    val itemOffset = dragDropState.lazyListState.layoutInfo.visibleItemsInfo
                        .firstOrNull { it.index == index }?.offset ?: 0
                    dragDropState.onDragStart(index, itemOffset)
                },
                onDrag = { change, dragAmount ->
                    change.consume()
                    dragDropState.onDrag(dragAmount.y)
                },
                onDragEnd = { dragDropState.onDragEnd() },
                onDragCancel = { dragDropState.onDragEnd() }
            )
        }
}
