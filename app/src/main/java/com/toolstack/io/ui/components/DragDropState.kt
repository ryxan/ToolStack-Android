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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex

/**
 * State object driving long-press drag-to-reorder on a LazyColumn.
 *
 * Usage:
 * ```
 * val dragState = rememberDragDropState(listState) { from, to -> reorder(from, to) }
 * LazyColumn(state = listState, modifier = Modifier.dragContainer(dragState)) {
 *     itemsIndexed(items, key = { _, item -> item.id }) { index, item ->
 *         val itemDragModifier = Modifier.draggedItem(dragState, index)
 *         Card(modifier = itemDragModifier) { … }
 *     }
 * }
 * ```
 *
 * [onMove] is called every time the dragged item crosses into a new slot (not just on drop),
 * so the list animates live while dragging. The caller should update its backing list immediately
 * in [onMove] for the visual swap to work.
 */
class DragDropState(
    val lazyListState: LazyListState,
    private val onMove: (from: Int, to: Int) -> Unit
) {
    // Index in the current list of the item being dragged; null when idle.
    var draggingItemIndex by mutableStateOf<Int?>(null)
        private set

    // Raw cumulative drag delta since the drag started.
    private var draggingItemDraggedDelta by mutableFloatStateOf(0f)

    // Y-offset of the pointer at the start of the drag relative to the list start.
    private var draggingItemInitialOffset by mutableIntStateOf(0)

    /** Y-pixel offset to apply to the dragged item's graphicsLayer. */
    internal val draggingItemOffset: Float
        get() {
            val startInfo = lazyListState.layoutInfo.visibleItemsInfo
                .firstOrNull { it.index == draggingItemIndex } ?: return 0f
            return draggingItemInitialOffset + draggingItemDraggedDelta - startInfo.offset
        }

    /** True while a drag is in progress. */
    val isDragging: Boolean get() = draggingItemIndex != null

    internal fun onDragStart(offset: Offset) {
        val hit = lazyListState.layoutInfo.visibleItemsInfo
            .firstOrNull { item ->
                offset.y.toInt() in item.offset..(item.offset + item.size)
            } ?: return
        draggingItemIndex = hit.index
        draggingItemInitialOffset = hit.offset
        draggingItemDraggedDelta = 0f
    }

    internal fun onDragEnd() {
        draggingItemIndex = null
        draggingItemDraggedDelta = 0f
    }

    internal fun onDrag(delta: Float) {
        draggingItemDraggedDelta += delta

        val draggingIndex = draggingItemIndex ?: return
        val currentDraggedItem: LazyListItemInfo = lazyListState.layoutInfo.visibleItemsInfo
            .firstOrNull { it.index == draggingIndex } ?: return

        // Centre of the dragged item as currently drawn (original position + drag delta).
        val startOffset = currentDraggedItem.offset + draggingItemOffset
        val endOffset = startOffset + currentDraggedItem.size
        val middleOffset = (startOffset + endOffset) / 2f

        val target = lazyListState.layoutInfo.visibleItemsInfo
            .firstOrNull { item ->
                middleOffset.toInt() in item.offset..(item.offset + item.size) &&
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
 * Attach long-press drag detection to the LazyColumn container.
 */
fun Modifier.dragContainer(dragDropState: DragDropState): Modifier = this.pointerInput(dragDropState) {
    detectDragGesturesAfterLongPress(
        onDragStart = { offset -> dragDropState.onDragStart(offset) },
        onDrag = { change, dragAmount ->
            change.consume()
            dragDropState.onDrag(dragAmount.y)
        },
        onDragEnd = { dragDropState.onDragEnd() },
        onDragCancel = { dragDropState.onDragEnd() }
    )
}

/**
 * Apply to each list item composable.
 *
 * When this item is the one being dragged it:
 *  - floats above the list (zIndex elevation)
 *  - translates by the live drag delta
 *  - renders with a subtle elevation shadow hint
 *
 * When another item is being dragged, this item renders normally (the live reorder
 * in the backing list takes care of the swap animation via LazyColumn's built-in
 * item placement animation).
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
}
