package org.blackaddons.api.event.render

import org.blackaddons.api.event.Cancellable
import org.blackaddons.api.event.Event

/** Cancel to hide the on-fire overlay. */
class FireOverlayRenderEvent(
    override var cancelled: Boolean = false,
) : Event(), Cancellable

enum class ToastType {
    TUTORIAL,
    UNSECURE_SERVER_WARNING,
    OTHER,
}

/** Cancel to suppress the toast. */
class ToastAddEvent(
    val type: ToastType,
    override var cancelled: Boolean = false,
) : Event(), Cancellable

/** Cancel to skip the confirm prompt for a clicked chat command. */
class CommandConfirmationEvent(
    val command: String,
    override var cancelled: Boolean = false,
) : Event(), Cancellable

/** Cancel to skip the confirm prompt for a clicked link. */
class LinkConfirmationEvent(
    val url: String,
    override var cancelled: Boolean = false,
) : Event(), Cancellable
