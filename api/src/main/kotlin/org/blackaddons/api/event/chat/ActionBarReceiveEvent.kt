package org.blackaddons.api.event.chat

import org.blackaddons.api.event.Event

/** Action-bar text off the packet, even if another mod hides the overlay. Not cancellable - use [ActionBarEvent]. */
class ActionBarReceiveEvent(val raw: String, val unformatted: String) : Event()
