package org.blackaddons.api.event.world

import org.blackaddons.api.event.Cancellable
import org.blackaddons.api.event.Event

/**
 * The server opens a container, before any screen exists. Posted on the client thread. [title] is unformatted and
 * [menuType] is a registry id such as `minecraft:generic_9x3`. Cancelling refuses it: no screen opens and the server
 * is told it was closed. Setting [headless] keeps the menu open without a screen. `Platform.container` reads and
 * clicks it as usual, and closing it sends the close packet.
 */
class ContainerOpenEvent(
    val title: String,
    val menuType: String,
    override var cancelled: Boolean = false,
) : Event(), Cancellable {
    var headless: Boolean = false
}
