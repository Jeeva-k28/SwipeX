package com.swipex.app.touchpad

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.*
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs

fun Modifier.touchpadInput(
    onMove: (Float, Float) -> Unit,
    onClick: (String, String) -> Unit, // button: "l"/"r", action: "d"/"u"/"t"
    onScroll: (Float) -> Unit,
    vibrate: () -> Unit
): Modifier = this.pointerInput(Unit) {
    coroutineScope {
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false)
            var lastTouchTime = System.currentTimeMillis()
            var startPos = down.position
            var longPressTriggered = false
            
            // Start a coroutine to check for long press
            val longPressJob = launch {
                delay(400) // 400ms hold time
                longPressTriggered = true
                vibrate()
                onClick("l", "d") // Send left down for dragging
            }

            while (true) {
                val event = awaitPointerEvent()
                val pointers = event.changes
                val activePointers = pointers.filter { it.pressed }

                if (activePointers.isEmpty()) {
                    // All fingers lifted
                    longPressJob.cancel()
                    val duration = System.currentTimeMillis() - lastTouchTime
                    val totalDistance = (pointers.first().position - startPos).getDistance()
                    
                    if (longPressTriggered) {
                        onClick("l", "u") // Release left down
                    } else if (pointers.size == 1 && totalDistance < 15.0f && duration < 300) {
                        // Quick tap with 1 finger registers a left click
                        onClick("l", "t")
                    } else if (pointers.size == 2 && totalDistance < 15.0f && duration < 300) {
                        // Quick tap with 2 fingers registers a right click
                        onClick("r", "t")
                    }
                    break
                }

                lastTouchTime = System.currentTimeMillis()

                if (activePointers.size == 1) {
                    val pointer = activePointers.first()
                    val delta = pointer.position - pointer.previousPosition
                    val totalDistance = (pointer.position - startPos).getDistance()

                    // If dragged, cancel long press detection (unless already triggered)
                    if (totalDistance > 15.0f && !longPressTriggered) {
                        longPressJob.cancel()
                    }

                    if (pointer.previousPressed) {
                        val dx = delta.x
                        val dy = delta.y
                        if (abs(dx) > 0.01f || abs(dy) > 0.01f) {
                            pointer.consume()
                            onMove(dx, dy)
                        }
                    }
                } else if (activePointers.size == 2) {
                    // Two fingers scroll
                    longPressJob.cancel()
                    if (longPressTriggered) {
                        onClick("l", "u")
                        longPressTriggered = false
                    }
                    
                    val p1 = activePointers[0]
                    val p2 = activePointers[1]
                    
                    val p1Delta = p1.position.y - p1.previousPosition.y
                    val p2Delta = p2.position.y - p2.previousPosition.y
                    
                    val dy = (p1Delta + p2Delta) / 2f
                    if (abs(dy) > 0.1f) {
                        p1.consume()
                        p2.consume()
                        onScroll(-dy) // Scroll delta
                    }
                } else {
                    // More than 2 fingers down
                    longPressJob.cancel()
                    if (longPressTriggered) {
                        onClick("l", "u")
                        longPressTriggered = false
                    }
                }
            }
        }
    }
}
