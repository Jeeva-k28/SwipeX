package com.swipex.app.touchpad

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.*
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.hypot

enum class GestureMode {
    NONE,
    ONE_FINGER_MOVE,
    ONE_FINGER_DRAG,
    TWO_FINGER_SCROLL,
    TWO_FINGER_ZOOM,
    THREE_FINGER_SWIPE
}

fun Modifier.touchpadInput(
    onMove: (Float, Float) -> Unit,
    onClick: (String, String) -> Unit,
    onScroll: (Float, Float) -> Unit,   // dy, dx
    onZoom: (String) -> Unit,           // "in" / "out"
    onGesture: (String) -> Unit,        // "taskview", "desktop", "prevdesktop", "nextdesktop"
    vibrate: () -> Unit
): Modifier = this.pointerInput(Unit) {
    coroutineScope {
        var lastTapTime = 0L
        
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false)
            val startPos = down.position
            var lastTouchTime = System.currentTimeMillis()
            var gestureMode = GestureMode.NONE
            
            // Double tap / tap-and-hold check
            val timeSinceLastTap = System.currentTimeMillis() - lastTapTime
            var isDoubleTapHoldCandidate = timeSinceLastTap < 300L
            var isDragging = false
            
            // Pinch tracking variables
            var startPinchDistance = 0f
            var lastPinchDistance = 0f
            
            // 3-finger swipe variables
            var threeFingerStartPos = androidx.compose.ui.geometry.Offset.Zero
            var threeFingerSwipeTriggered = false
            
            // Start a coroutine to check for tap-hold drag
            val dragHoldJob = launch {
                if (isDoubleTapHoldCandidate) {
                    delay(150) // 150ms hold transitions to drag state
                    gestureMode = GestureMode.ONE_FINGER_DRAG
                    isDragging = true
                    vibrate()
                    onClick("l", "d") // Send left click down
                }
            }

            while (true) {
                val event = awaitPointerEvent()
                val pointers = event.changes
                val activePointers = pointers.filter { it.pressed }

                // Palm rejection: if more than 4 fingers down, ignore
                if (activePointers.size > 4) {
                    dragHoldJob.cancel()
                    if (isDragging) {
                        onClick("l", "u")
                    }
                    break
                }

                if (activePointers.isEmpty()) {
                    // All fingers lifted
                    dragHoldJob.cancel()
                    val duration = System.currentTimeMillis() - lastTouchTime
                    
                    val liftedPointer = pointers.firstOrNull()
                    val totalDistance = if (liftedPointer != null) {
                        (liftedPointer.position - startPos).getDistance()
                    } else {
                        0f
                    }

                    if (gestureMode == GestureMode.ONE_FINGER_DRAG || isDragging) {
                        onClick("l", "u") // Release left down
                    } else if (gestureMode == GestureMode.NONE) {
                        if (pointers.size == 1 && totalDistance < 15.0f && duration < 300) {
                            if (isDoubleTapHoldCandidate) {
                                // Trigger Double Click
                                onClick("l", "db")
                            } else {
                                // Trigger Left Click
                                onClick("l", "t")
                            }
                            lastTapTime = System.currentTimeMillis()
                        } else if (pointers.size == 2 && totalDistance < 15.0f && duration < 300) {
                            // Two-finger tap registers a right click
                            vibrate()
                            onClick("r", "t")
                        }
                    }
                    
                    // Update tap timing
                    if (pointers.size == 1 && totalDistance < 15.0f && duration < 300 && !isDoubleTapHoldCandidate) {
                        lastTapTime = System.currentTimeMillis()
                    } else {
                        lastTapTime = 0L // reset
                    }
                    break
                }

                lastTouchTime = System.currentTimeMillis()

                if (activePointers.size == 1) {
                    val pointer = activePointers.first()
                    val delta = pointer.position - pointer.previousPosition
                    val totalDistance = (pointer.position - startPos).getDistance()

                    // Cancel candidate check if moved too far
                    if (totalDistance > 15.0f && !isDragging) {
                        dragHoldJob.cancel()
                        isDoubleTapHoldCandidate = false
                    }

                    if (gestureMode == GestureMode.NONE) {
                        if (totalDistance > 10.0f) {
                            gestureMode = GestureMode.ONE_FINGER_MOVE
                        }
                    }

                    if (gestureMode == GestureMode.ONE_FINGER_MOVE) {
                        if (pointer.previousPressed) {
                            onMove(delta.x, delta.y)
                            pointer.consume()
                        }
                    } else if (gestureMode == GestureMode.ONE_FINGER_DRAG) {
                        if (pointer.previousPressed) {
                            onMove(delta.x, delta.y)
                            pointer.consume()
                        }
                    }

                } else if (activePointers.size == 2) {
                    dragHoldJob.cancel()
                    if (isDragging) {
                        onClick("l", "u")
                        isDragging = false
                    }

                    val p1 = activePointers[0]
                    val p2 = activePointers[1]
                    
                    val distance = hypot(p1.position.x - p2.position.x, p1.position.y - p2.position.y)
                    val p1Delta = p1.position - p1.previousPosition
                    val p2Delta = p2.position - p2.previousPosition

                    if (gestureMode == GestureMode.NONE) {
                        startPinchDistance = distance
                        lastPinchDistance = distance
                        
                        val moveDistance = (p1Delta + p2Delta).getDistance() / 2f
                        val pinchDelta = abs(distance - startPinchDistance)
                        
                        if (pinchDelta > 20.0f) {
                            gestureMode = GestureMode.TWO_FINGER_ZOOM
                        } else if (moveDistance > 8.0f) {
                            gestureMode = GestureMode.TWO_FINGER_SCROLL
                        }
                    }

                    if (gestureMode == GestureMode.TWO_FINGER_SCROLL) {
                        val dy = (p1Delta.y + p2Delta.y) / 2f
                        val dx = (p1Delta.x + p2Delta.x) / 2f
                        
                        if (abs(dy) > 0.05f || abs(dx) > 0.05f) {
                            onScroll(dy, dx)
                            p1.consume()
                            p2.consume()
                        }
                    } else if (gestureMode == GestureMode.TWO_FINGER_ZOOM) {
                        val pinchDelta = distance - lastPinchDistance
                        if (abs(pinchDelta) > 5.0f) {
                            if (pinchDelta > 0) {
                                onZoom("in")
                            } else {
                                onZoom("out")
                            }
                            lastPinchDistance = distance
                            p1.consume()
                            p2.consume()
                        }
                    }

                } else if (activePointers.size == 3) {
                    dragHoldJob.cancel()
                    if (isDragging) {
                        onClick("l", "u")
                        isDragging = false
                    }

                    val p1 = activePointers[0]
                    val p2 = activePointers[1]
                    val p3 = activePointers[2]

                    if (gestureMode == GestureMode.NONE) {
                        gestureMode = GestureMode.THREE_FINGER_SWIPE
                        threeFingerStartPos = (p1.position + p2.position + p3.position) / 3f
                        threeFingerSwipeTriggered = false
                    }

                    if (gestureMode == GestureMode.THREE_FINGER_SWIPE && !threeFingerSwipeTriggered) {
                        val currentPos = (p1.position + p2.position + p3.position) / 3f
                        val displacement = currentPos - threeFingerStartPos
                        
                        val threshold = 70.0f
                        if (abs(displacement.y) > threshold) {
                            if (displacement.y < 0) {
                                vibrate()
                                onGesture("taskview") // Swiped Up
                            } else {
                                vibrate()
                                onGesture("desktop")  // Swiped Down
                            }
                            threeFingerSwipeTriggered = true
                            p1.consume()
                            p2.consume()
                            p3.consume()
                        } else if (abs(displacement.x) > threshold) {
                            if (displacement.x < 0) {
                                vibrate()
                                onGesture("prevdesktop") // Swiped Left
                            } else {
                                vibrate()
                                onGesture("nextdesktop") // Swiped Right
                            }
                            threeFingerSwipeTriggered = true
                            p1.consume()
                            p2.consume()
                            p3.consume()
                        }
                    }
                }
            }
        }
    }
}
