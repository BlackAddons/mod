package org.blackaddons.api.chat

/** Shapes how the chat animates; the chat owns the clocks. Defaults are vanilla. */
interface ChatMotion {

    /** Opacity 0..1 for a line [ageMs] old. */
    fun lineOpacity(ageMs: Double): Float = 1f

    /**
     * How many lines below its resting place the chat sits [sinceNewestMs] after the newest line, while scrolled to the
     * bottom.
     */
    fun slide(sinceNewestMs: Double): Float = 0f

    /** Scroll position after [seconds], moving from [drawn] toward [target]. */
    fun scroll(drawn: Double, target: Int, seconds: Double): Double = target.toDouble()

    /** GUI pixels the input box sits below its resting place [sinceOpenMs] after opening. */
    fun inputOffset(sinceOpenMs: Double): Float = 0f

    /** Hides vanilla's colored bar left of system/unsigned messages. */
    val hidesIndicator: Boolean get() = false
}

/** The registered [ChatMotion]; last registration wins. */
object ChatMotions {

    @Volatile
    private var current: ChatMotion? = null

    fun register(motion: ChatMotion) {
        current = motion
    }

    fun release(motion: ChatMotion) {
        if (current === motion) current = null
    }

    fun lineOpacity(ageMs: Double): Float = current?.lineOpacity(ageMs) ?: 1f

    fun slide(sinceNewestMs: Double): Float = current?.slide(sinceNewestMs) ?: 0f

    fun scroll(drawn: Double, target: Int, seconds: Double): Double =
        current?.scroll(drawn, target, seconds) ?: target.toDouble()

    fun inputOffset(sinceOpenMs: Double): Float = current?.inputOffset(sinceOpenMs) ?: 0f

    val hidesIndicator: Boolean get() = current?.hidesIndicator == true
}
