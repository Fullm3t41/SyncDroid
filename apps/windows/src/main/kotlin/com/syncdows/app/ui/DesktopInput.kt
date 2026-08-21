package com.syncdows.app.ui

import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.ScrollableDefaults
import androidx.compose.foundation.gestures.ScrollableState
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * Adds drag-to-scroll for Windows touchscreens that AWT exposes as mouse input.
 * Compose's normal scrollable modifier intentionally rejects mouse drags, while
 * retaining wheel, trackpad, keyboard, accessibility, and native touch support.
 */
@Composable
internal fun Modifier.windowsTouchDragScroll(state: ScrollableState): Modifier {
    val dragState = rememberDraggableState { dragAmount ->
        state.dispatchRawDelta(-dragAmount)
    }
    val flingBehavior = ScrollableDefaults.flingBehavior()
    return draggable(
        state = dragState,
        orientation = Orientation.Vertical,
        onDragStopped = { velocity ->
            state.scroll {
                with(flingBehavior) { performFling(-velocity) }
            }
        },
    )
}

@Composable
internal fun WindowsTouchLazyColumn(
    modifier: Modifier = Modifier,
    state: LazyListState = rememberLazyListState(),
    contentPadding: PaddingValues = PaddingValues(0.dp),
    verticalArrangement: Arrangement.Vertical = Arrangement.Top,
    horizontalAlignment: Alignment.Horizontal = Alignment.Start,
    content: LazyListScope.() -> Unit,
) {
    LazyColumn(
        modifier = modifier.windowsTouchDragScroll(state),
        state = state,
        contentPadding = contentPadding,
        verticalArrangement = verticalArrangement,
        horizontalAlignment = horizontalAlignment,
        content = content,
    )
}
