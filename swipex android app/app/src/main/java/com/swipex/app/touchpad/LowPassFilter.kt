package com.swipex.app.touchpad

class LowPassFilter(private var alpha: Double = 0.0) {
    private var lastValue: Double? = null

    fun filter(value: Double, alpha: Double = this.alpha): Double {
        val result = if (lastValue == null) {
            value
        } else {
            alpha * value + (1.0 - alpha) * lastValue!!
        }
        lastValue = result
        return result
    }

    fun lastValue(): Double? = lastValue
    
    fun reset() {
        lastValue = null
    }
}
