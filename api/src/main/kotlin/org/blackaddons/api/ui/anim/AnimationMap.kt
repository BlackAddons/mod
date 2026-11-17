package org.blackaddons.api.ui.anim

import org.blackaddons.api.ui.theme.Theme

/**
 * One [Animation] per key (hovered slots, selected rows); dropped once faded back to 0. Per cell per frame:
 * `val hover = hovers[slot, isHovered]`.
 */
class AnimationMap<K>(private val durationMs: Long = Theme.ANIM_HOVER.toLong()) {

    private val animations = HashMap<K, Animation>()

    /** Heads to 1 while [active], else to 0. */
    operator fun get(key: K, active: Boolean): Float {
        val animation = animations[key]
            ?: if (active) Animation(durationMs = durationMs).also { animations[key] = it } else return 0f
        animation.animateTo(if (active) 1f else 0f)
        val amount = animation.get()
        if (!active && !animation.isRunning) animations.remove(key)
        return amount
    }

    /** Keys still lit or fading. */
    val size: Int get() = animations.size

    fun clear() = animations.clear()
}
