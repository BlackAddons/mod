package org.blackaddons.api.ui.anim

import org.blackaddons.api.ui.theme.Theme

/**
 * One [Animation] per key (hovered slots, selected rows); dropped once faded back to 0. Per cell per frame:
 * `val hover = hovers[slot, isHovered]`.
 */
class AnimationMap<K>(private val durationMs: Long = Theme.ANIM_HOVER.toLong()) {

    private val animations = HashMap<K, Animation>()
    private var settled = false

    /**
     * With [settled], a key seen for the first time starts at its state instead of easing in, so a checkbox already
     * checked when its screen opens doesn't play its check. Keys then stay until [clear].
     */
    constructor(durationMs: Long, settled: Boolean) : this(durationMs) {
        this.settled = settled
    }

    /** Heads to 1 while [active], else to 0. */
    operator fun get(key: K, active: Boolean): Float {
        val target = if (active) 1f else 0f
        val animation = animations[key] ?: when {
            settled -> Animation(target, durationMs)
            active -> Animation(durationMs = durationMs)
            else -> return 0f
        }.also { animations[key] = it }
        animation.animateTo(target)
        val amount = animation.get()
        if (!settled && !active && !animation.isRunning) animations.remove(key)
        return amount
    }

    /** Keys still lit or fading. */
    val size: Int get() = animations.size

    fun clear() = animations.clear()
}
