package org.blackaddons.api.event.input

import org.blackaddons.api.event.Cancellable
import org.blackaddons.api.event.Event

class MouseScrollEvent(
    val xOffset: Double,
    val yOffset: Double,
    override var cancelled: Boolean = false,
) : Event(), Cancellable
