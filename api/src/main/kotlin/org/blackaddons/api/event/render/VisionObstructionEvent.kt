package org.blackaddons.api.event.render

import org.blackaddons.api.event.Cancellable
import org.blackaddons.api.event.Event

enum class VisionObstruction {
    WATER,
    LAVA,
    BLINDNESS,
    DARKNESS,
    IN_BLOCK,
}

/** Cancel to remove [obstruction] (fog, screen overlay or darkness pulse). */
class VisionObstructionEvent(
    val obstruction: VisionObstruction,
    override var cancelled: Boolean = false,
) : Event(), Cancellable
