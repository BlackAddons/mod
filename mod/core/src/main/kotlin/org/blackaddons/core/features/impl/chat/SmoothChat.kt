package org.blackaddons.core.features.impl.chat

import org.blackaddons.api.chat.ChatMotion
import org.blackaddons.api.chat.ChatMotions
import org.blackaddons.api.feature.Feature
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.sin

/** Ported from ChatAnimation (MIT, (c) Ezzenix). */
object SmoothChat : Feature(
    description = "Animates chat: messages fade in, the input box slides in, and scrolling is smooth.",
    name = "Smooth Chat",
), ChatMotion {

    private val fadeIn by boolean("Fade In Messages", true)
        .withDescription("New messages slide up and fade in")
    private val fadeTime by number("Fade Time", 150.0, 10.0, 800.0, 10.0, unit = "ms")
        .showIf { fadeIn.value }
    private val slideInput by boolean("Slide Input Box", true)
        .withDescription("The input box slides up when chat opens")
    private val inputTime by number("Input Slide Time", 170.0, 10.0, 800.0, 10.0, unit = "ms")
        .showIf { slideInput.value }
    private val smoothScroll by boolean("Smooth Scrolling", true)
        .withDescription("Scrolling through chat history glides instead of jumping")
    private val hideIndicator by boolean("Hide Message Indicator", false)
        .withDescription("Hides the colored bar at the left of system and unsigned messages")

    override fun onEnable() {
        ChatMotions.register(this)
    }

    override fun onDisable() {
        ChatMotions.release(this)
        super.onDisable()
    }

    override fun lineOpacity(ageMs: Double): Float =
        if (fadeIn.value) easeOutSine(progress(ageMs, fadeTime.value)) else 1f

    override fun slide(sinceNewestMs: Double): Float =
        if (fadeIn.value) SLIDE_LINES * (1f - easeOutSine(progress(sinceNewestMs, fadeTime.value))) else 0f

    override fun scroll(drawn: Double, target: Int, seconds: Double): Double {
        if (!smoothScroll.value) return target.toDouble()
        val next = drawn + (target - drawn) * (1.0 - exp(-seconds * SCROLL_RATE))
        return if (abs(target - next) < SCROLL_SNAP) target.toDouble() else next
    }

    override fun inputOffset(sinceOpenMs: Double): Float {
        if (!slideInput.value) return 0f
        val left = 1f - progress(sinceOpenMs, inputTime.value)
        return (BACK_C3 * left * left * left - BACK_C1 * left * left) * INPUT_SLIDE_PX
    }

    override val hidesIndicator: Boolean get() = hideIndicator.value

    private fun progress(elapsedMs: Double, durationMs: Double): Float = (elapsedMs / durationMs).toFloat().coerceIn(0f, 1f)

    private fun easeOutSine(t: Float): Float = sin(t * PI / 2).toFloat()

    private const val SLIDE_LINES = 0.8f
    private const val INPUT_SLIDE_PX = 8f
    private const val BACK_C1 = 1.70158f
    private const val BACK_C3 = BACK_C1 + 1f
    private const val SCROLL_RATE = 20.0
    private const val SCROLL_SNAP = 0.01
}
