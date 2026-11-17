package org.blackaddons.api.event.chat

import org.blackaddons.api.event.Event

/**
 * The chat screen has drawn; draw on top here, under its tooltip. Input box geometry is in GUI units. While
 * [covered], command suggestions sit above the input.
 */
class ChatScreenRenderEvent(
    val inputX: Float,
    val inputY: Float,
    val inputWidth: Float,
    val mouseX: Float,
    val mouseY: Float,
    val covered: Boolean,
) : Event()
