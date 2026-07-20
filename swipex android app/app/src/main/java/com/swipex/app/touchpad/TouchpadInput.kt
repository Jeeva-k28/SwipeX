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
    onMove: (Float, Float, Long) -> Unit,
    onClick: (String, String) -> Unit,
    onScroll: (Float, Float) -> Unit,   // dy, dx
    onZoom: (String) -> Unit,           // "in" / "out"
    onGesture: (String) -> Unit,        // "taskview", "desktop", "prevdesktop", "nextdesktop"
    onPositionChange: ((androidx.compose.ui.geometry.Offset) -> Unit)? = null,
    vibrate: () -> Unit
): Modifier = this.pointerInput(Unit) {
    val width = size.width.toFloat()
    val height = size.height.toFloat()
    val buttonWidth = width * 0.18f
    val buttonMidY = height / 2f

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

            // Button tracking
            var buttonFingerId: PointerId? = null
            var buttonPressedSent = false
            val isButtonZone = startPos.x < buttonWidth
            val localButtonType = if (isButtonZone) {
                if (startPos.y > buttonMidY) "r" else "l"
            } else {
                null
            }

            if (isButtonZone) {
                buttonFingerId = down.id
            }
            
            // Start a coroutine to check for tap-hold drag (only if NOT in button zone)
            val dragHoldJob = launch {
                if (!isButtonZone && isDoubleTapHoldCandidate) {
                    delay(150) // 150ms hold transitions to drag state
                    gestureMode = GestureMode.ONE_FINGER_DRAG
                    isDragging = true
                    vibrate()
                    onClick("l", "d") // Send left click down
                }
            }

            // Start a coroutine to check for button hold
            val buttonHoldJob = launch {
                if (isButtonZone && localButtonType != null) {
                    delay(180) // 180ms hold triggers click down
                    onClick(localButtonType, "d")
                    buttonPressedSent = true
                    vibrate()
                }
            }

            while (true) {
                val event = awaitPointerEvent()
                event.changes.forEach { it.consume() }
                val pointers = event.changes
                val activePointers = pointers.filter { it.pressed }

                // Palm rejection
                if (activePointers.size > 4) {
                    dragHoldJob.cancel()
                    buttonHoldJob.cancel()
                    if (isDragging) {
                        onClick("l", "u")
                    }
                    if (buttonPressedSent && localButtonType != null) {
                        onClick(localButtonType, "u")
                    }
                    break
                }

                // Update button finger status
                if (buttonFingerId != null) {
                    val buttonPointer = pointers.find { it.id == buttonFingerId }
                    if (buttonPointer != null) {
                        if (!buttonPointer.pressed) {
                            // Button finger released!
                            buttonHoldJob.cancel()
                            if (buttonPressedSent) {
                                if (localButtonType != null) onClick(localButtonType, "u")
                            } else {
                                // Tap in button zone
                                if (localButtonType != null) onClick(localButtonType, "t")
                            }
                            buttonFingerId = null
                            buttonPressedSent = false
                        } else {
                            // Check if moved too far
                            val dist = (buttonPointer.position - startPos).getDistance()
                            if (dist > 15.0f) {
                                buttonHoldJob.cancel()
                                if (buttonPressedSent) {
                                    if (localButtonType != null) onClick(localButtonType, "u")
                                }
                                buttonFingerId = null
                                buttonPressedSent = false
                            }
                        }
                    }
                }

                if (activePointers.isEmpty()) {
                    // All fingers lifted
                    dragHoldJob.cancel()
                    buttonHoldJob.cancel()
                    val duration = System.currentTimeMillis() - lastTouchTime
                    
                    val liftedPointer = pointers.firstOrNull()
                    val totalDistance = if (liftedPointer != null) {
                        (liftedPointer.position - startPos).getDistance()
                    } else {
                        0f
                    }

                    if (buttonPressedSent && localButtonType != null) {
                        onClick(localButtonType, "u")
                    } else if (gestureMode == GestureMode.ONE_FINGER_DRAG || isDragging) {
                        onClick("l", "u")
                    } else if (gestureMode == GestureMode.NONE && buttonFingerId == null) {
                        if (pointers.size == 1 && totalDistance < 15.0f && duration < 300) {
                            if (isDoubleTapHoldCandidate) {
                                onClick("l", "db")
                            } else {
                                onClick("l", "t")
                            }
                            lastTapTime = System.currentTimeMillis()
                        } else if (pointers.size == 2 && totalDistance < 15.0f && duration < 300) {
                            vibrate()
                            onClick("r", "t")
                        }
                    }
                    
                    if (pointers.size == 1 && totalDistance < 15.0f && duration < 300 && !isDoubleTapHoldCandidate && buttonFingerId == null) {
                        lastTapTime = System.currentTimeMillis()
                    } else {
                        lastTapTime = 0L
                    }
                    break
                }

                lastTouchTime = System.currentTimeMillis()

                // If button finger is holding, handle movement with other fingers
                if (buttonFingerId != null) {
                    val otherPointers = activePointers.filter { it.id != buttonFingerId }
                    if (otherPointers.isNotEmpty()) {
                        val pointer = otherPointers.first()
                        val delta = pointer.position - pointer.previousPosition
                        val timeDeltaMs = (pointer.uptimeMillis - pointer.previousUptimeMillis).coerceAtLeast(1L)
                        if (pointer.previousPressed && (abs(delta.x) > 0.01f || abs(delta.y) > 0.01f)) {
                            onPositionChange?.invoke(pointer.position)
                            onMove(delta.y, -delta.x, timeDeltaMs)
                            pointer.consume()
                        }
                    }
                } else {
                    // Regular touchpad gesture logic
                    if (activePointers.size == 1) {
                        val pointer = activePointers.first()
                        val delta = pointer.position - pointer.previousPosition
                        val totalDistance = (pointer.position - startPos).getDistance()

                        if (totalDistance > 15.0f && !isDragging) {
                            dragHoldJob.cancel()
                            isDoubleTapHoldCandidate = false
                        }

                        if (gestureMode == GestureMode.NONE) {
                            if (totalDistance > 10.0f) {
                                gestureMode = GestureMode.ONE_FINGER_MOVE
                            }
                        }

                        val timeDeltaMs = (pointer.uptimeMillis - pointer.previousUptimeMillis).coerceAtLeast(1L)
                        if (gestureMode == GestureMode.ONE_FINGER_MOVE) {
                            if (pointer.previousPressed) {
                                onPositionChange?.invoke(pointer.position)
                                onMove(delta.y, -delta.x, timeDeltaMs)
                                pointer.consume()
                            }
                        } else if (gestureMode == GestureMode.ONE_FINGER_DRAG) {
                            if (pointer.previousPressed) {
                                onPositionChange?.invoke(pointer.position)
                                onMove(delta.y, -delta.x, timeDeltaMs)
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
                                onScroll(-dx, dy)
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
                            if (abs(displacement.x) > threshold) {
                                if (displacement.x > 0) {
                                    vibrate()
                                    onGesture("taskview")
                                } else {
                                    vibrate()
                                    onGesture("desktop")
                                }
                                threeFingerSwipeTriggered = true
                                p1.consume()
                                p2.consume()
                                p3.consume()
                            } else if (abs(displacement.y) > threshold) {
                                if (displacement.y < 0) {
                                    vibrate()
                                    onGesture("prevdesktop")
                                } else {
                                    vibrate()
                                    onGesture("nextdesktop")
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
}
