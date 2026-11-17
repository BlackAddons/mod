package org.blackaddons.core.control

import kotlin.math.min

/* Frames of the last 0.5 s or so, so the hand can read what the eye saw one visual delay ago. */
internal class PursuitBuffer {
    private val time = DoubleArray(SIZE)
    val yaw = DoubleArray(SIZE)
    val pitch = DoubleArray(SIZE)
    private var head = -1
    private var count = 0

    fun push(t: Double, yaw: Double, pitch: Double) {
        head = (head + 1) % SIZE
        count = min(count + 1, SIZE)
        time[head] = t
        this.yaw[head] = yaw
        this.pitch[head] = pitch
    }

    /* Newest frame not later than [t], or the oldest kept frame. */
    fun at(t: Double): Int {
        var i = head
        repeat(count - 1) {
            if (time[i] <= t) return i
            i = (i - 1 + SIZE) % SIZE
        }
        return i
    }

    private companion object {
        const val SIZE = 512
    }
}
