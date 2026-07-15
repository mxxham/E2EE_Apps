package com.securechat.features.chat.ui

import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.offset
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.unit.IntOffset
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/**
 * Custom swipeToReply Modifier for Compose.
 * Tracks horizontal drag gestures, triggers onSwipeToReply once the threshold is crossed,
 * and animates back smoothly to the default offset.
 */
fun Modifier.swipeToReply(
    onSwipeToReply: () -> Unit,
    maxSwipeOffset: Float = 150f,
    coroutineScope: CoroutineScope
): Modifier = composed {
    var swipeOffset by remember { mutableFloatStateOf(0f) }

    val draggableState = rememberDraggableState { delta ->
        // Only allow swiping to the right (positive delta)
        val newOffset = (swipeOffset + delta).coerceIn(0f, maxSwipeOffset)
        swipeOffset = newOffset
    }

    this
        .offset { IntOffset(swipeOffset.roundToInt(), 0) }
        .draggable(
            state = draggableState,
            orientation = Orientation.Horizontal,
            onDragStopped = {
                if (swipeOffset >= maxSwipeOffset * 0.75f) {
                    onSwipeToReply()
                }
                // Animate slide-back on drag release
                coroutineScope.launch {
                    var current = swipeOffset
                    while (current > 0f) {
                        current -= 15f
                        swipeOffset = current.coerceAtLeast(0f)
                    }
                }
            }
        )
}
