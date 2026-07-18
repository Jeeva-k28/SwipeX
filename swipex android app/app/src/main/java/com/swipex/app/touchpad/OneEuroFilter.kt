package com.swipex.app.touchpad

import kotlin.math.PI
import kotlin.math.abs

/**
 * One Euro Filter implementation for smooth tracking.
 * @param freq Sampling frequency (Hz).
 * @param minCutoff Minimum cutoff frequency (Hz).
 * @param beta Speed coefficient.
 * @param dCutoff Cutoff frequency for the derivative (Hz).
 */
class OneEuroFilter(
    private val freq: Double = 60.0,
    private val minCutoff: Double = 1.0,
    private val beta: Double = 0.007,
    private val dCutoff: Double = 1.0
) {
    private val xFilter = LowPassFilter()
    private val dxFilter = LowPassFilter()
    private var lastTime: Long = -1

    private fun getAlpha(cutoff: Double, dt: Double): Double {
        val tau = 1.0 / (2.0 * PI * cutoff)
        return 1.0 / (1.0 + tau / dt)
    }

    fun filter(value: Double, timestamp: Long): Double {
        val dt = if (lastTime == -1L) 1.0 / freq else (timestamp - lastTime) / 1000.0
        lastTime = timestamp

        val prevX = xFilter.lastValue()
        val dValue = if (prevX == null) 0.0 else (value - prevX) / dt
        val alphaD = getAlpha(dCutoff, dt)
        val edValue = dxFilter.filter(dValue, alphaD)

        val cutoff = minCutoff + beta * abs(edValue)

        val alpha = getAlpha(cutoff, dt)
        return xFilter.filter(value, alpha)
    }
    
    fun reset() {
        xFilter.reset()
        dxFilter.reset()
        lastTime = -1
    }
}
