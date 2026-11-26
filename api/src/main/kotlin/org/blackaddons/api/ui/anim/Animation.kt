package org.blackaddons.api.ui.anim

import org.blackaddons.api.ui.theme.Theme
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.TimeSource

/**
 * Frame-rate-independent tween. Defaults ([Theme.ANIM_FAST], [Easing.easeOutCubic]) are the house style;
 * only pass them when deliberately different.
 * ```
 * private val hover = Animation(durationMs = Theme.ANIM_HOVER.toLong())
 * hover.animateTo(if (isHovered) 1f else 0f)
 * val brightness = hover.get()
 * ```
 */
class Animation(
    initial: Float = 0f,
    val durationMs: Long = Theme.ANIM_FAST.toLong(),
    val easing: (Float) -> Float = Easing.easeOutCubic,
) {
    private var from: Float = initial
    private var to: Float = initial

    /** Null = idle. */
    private var startedAt: TimeSource.Monotonic.ValueTimeMark? = null

    fun set(value: Float) {
        from = value
        to = value
        startedAt = null
    }

    /** No-op if already heading there. */
    fun animateTo(target: Float) {
        if (target == to) return
        from = get()
        to = target
        startedAt = TimeSource.Monotonic.markNow()
    }

    fun get(): Float {
        val elapsed = startedAt?.elapsedNow() ?: return to
        val duration = durationMs.milliseconds
        if (elapsed >= duration) {
            from = to
            startedAt = null
            return to
        }
        val t = (elapsed / duration).toFloat()
        return from + (to - from) * easing(t)
    }

    val isRunning: Boolean get() = startedAt != null
    val target: Float get() = to
}

/** One 8-bit alpha step; anything below changes no pixel. */
const val INVISIBLE = 1f / 255f

val Float.isVisible: Boolean get() = this > INVISIBLE
