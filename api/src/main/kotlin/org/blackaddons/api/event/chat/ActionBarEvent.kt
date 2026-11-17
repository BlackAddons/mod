package org.blackaddons.api.event.chat

import org.blackaddons.api.event.Cancellable
import org.blackaddons.api.event.Event

/**
 * Every action-bar message about to be shown. Cancel to hide it, or set [replacement] (§-formatted) to show
 * something else; blank equals cancelling.
 */
class ActionBarEvent(
    val raw: String,
    val unformatted: String,
    override var cancelled: Boolean = false,
    var replacement: String? = null,
) : Event(), Cancellable
