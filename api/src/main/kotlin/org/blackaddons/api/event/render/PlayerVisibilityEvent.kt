package org.blackaddons.api.event.render

import org.blackaddons.api.event.Cancellable
import org.blackaddons.api.event.Event
import java.util.UUID

/** Another player is about to be drawn this frame; cancel to skip them. [name] is the account name. */
class PlayerVisibilityEvent(
    val uuid: UUID,
    val name: String,
    val x: Double,
    val y: Double,
    val z: Double,
    override var cancelled: Boolean = false,
) : Event(), Cancellable
