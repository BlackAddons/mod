package org.blackaddons.api.ui.anim

import org.blackaddons.api.ui.theme.Theme

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

    /** 0 = idle. */
    private var startedAt: Long = 0L

    fun set(value: Float) {
        from = value
        to = value
        startedAt = 0L
    }

    /** No-op if already heading there. */
    fun animateTo(target: Float) {
        if (target == to) return
        from = get()
        to = target
        startedAt = System.currentTimeMillis().let { if (it == 0L) 1L else it }
    }

    fun get(): Float {
        if (startedAt == 0L) return to
        val elapsed = System.currentTimeMillis() - startedAt
        if (elapsed >= durationMs) {
            from = to
            startedAt = 0L
            return to
        }
        val t = (elapsed.toDouble() / durationMs).toFloat()
        return from + (to - from) * easing(t)
    }

    val isRunning: Boolean get() = startedAt != 0L
    val target: Float get() = to
}

/** One 8-bit alpha step; anything below changes no pixel. */
const val INVISIBLE = 1f / 255f

val Float.isVisible: Boolean get() = this > INVISIBLE
