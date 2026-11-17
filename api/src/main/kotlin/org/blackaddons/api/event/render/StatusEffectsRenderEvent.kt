package org.blackaddons.api.event.render

import org.blackaddons.api.event.Cancellable
import org.blackaddons.api.event.Event

enum class StatusEffectTarget { HUD, INVENTORY }

/** Cancel to hide status effect icons (HUD or inventory). */
class StatusEffectsRenderEvent(
    val target: StatusEffectTarget,
    override var cancelled: Boolean = false,
) : Event(), Cancellable
