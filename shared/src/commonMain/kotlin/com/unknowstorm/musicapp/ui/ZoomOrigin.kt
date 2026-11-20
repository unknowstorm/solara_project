package com.unknowstorm.musicapp.ui

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.unit.toSize
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.unit.IntSize

/** Bounds (window coordinates) of the last tapped "zoom source": the opened screen expands out of it. */
object ZoomOrigin {
    var rect: Rect = Rect.Zero
}

/** Put it on a card that opens a screen: it remembers the card's bounds and does not consume the gesture. */
@Composable
fun Modifier.zoomSource(): Modifier {
    var pos by remember { mutableStateOf(Offset.Zero) }
    var size by remember { mutableStateOf(IntSize.Zero) }
    return this
        .onGloballyPositioned { pos = it.positionInWindow(); size = it.size }
        .pointerInput(Unit) {
            awaitEachGesture {
                awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                ZoomOrigin.rect = Rect(pos, size.toSize())
            }
        }
}
