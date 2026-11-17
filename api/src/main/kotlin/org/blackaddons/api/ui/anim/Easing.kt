package org.blackaddons.api.ui.anim

object Easing {
    val linear: (Float) -> Float = { it }

    val easeOut: (Float) -> Float = {
        val inv = 1f - it
        1f - inv * inv
    }

    val easeIn: (Float) -> Float = { it * it }

    val easeInOut: (Float) -> Float = {
        if (it < 0.5f) 2f * it * it
        else 1f - 2f * (1f - it) * (1f - it)
    }

    val easeOutCubic: (Float) -> Float = {
        val inv = 1f - it
        1f - inv * inv * inv
    }

    val easeOutBack: (Float) -> Float = {
        val c1 = 1.70158f
        val c3 = c1 + 1f
        val inv = it - 1f
        1f + c3 * inv * inv * inv + c1 * inv * inv
    }
}
