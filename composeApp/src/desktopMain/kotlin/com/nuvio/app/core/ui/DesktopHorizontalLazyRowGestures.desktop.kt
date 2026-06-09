package com.nuvio.app.core.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.ui.Modifier
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.PointerType
import androidx.compose.ui.input.pointer.onPointerEvent
import androidx.compose.ui.input.pointer.pointerInput
import kotlin.math.abs

@OptIn(ExperimentalComposeUiApi::class, ExperimentalFoundationApi::class)
actual fun Modifier.desktopHorizontalLazyRowGestures(listState: LazyListState): Modifier =
    this
        // Handle trackpad horizontal scroll → horizontal scroll on LazyRow
        // Only intercept horizontal scroll delta; let vertical scroll pass through
        // to the parent so page scrolling still works when cursor is over a shelf
        .onPointerEvent(PointerEventType.Scroll) {
            val scrollDelta = it.changes.firstOrNull()?.scrollDelta ?: return@onPointerEvent
            if (scrollDelta.x != 0f) {
                listState.dispatchRawDelta(scrollDelta.x * 50f)
                it.changes.forEach { c -> c.consume() }
            }
        }
        // Handle mouse click-drag
        .pointerInput(listState) {
            awaitEachGesture {
                val down = awaitFirstDown(pass = PointerEventPass.Initial)
                if (down.type != PointerType.Mouse) return@awaitEachGesture

                var totalDx = 0f
                var totalDy = 0f
                var dragging = false

                while (true) {
                    val event = awaitPointerEvent(pass = PointerEventPass.Initial)
                    val change = event.changes.firstOrNull { it.id == down.id } ?: break
                    if (!change.pressed) break

                    val delta = change.position - change.previousPosition
                    totalDx += delta.x
                    totalDy += delta.y

                    if (!dragging) {
                        val verticalDrag =
                            abs(totalDy) > viewConfiguration.touchSlop && abs(totalDy) > abs(totalDx)
                        val horizontalDrag =
                            abs(totalDx) > viewConfiguration.touchSlop && abs(totalDx) > abs(totalDy)
                        when {
                            verticalDrag -> break
                            horizontalDrag -> dragging = true
                            else -> continue
                        }
                    }

                    listState.dispatchRawDelta(-delta.x)
                    change.consume()
                }
            }
        }
