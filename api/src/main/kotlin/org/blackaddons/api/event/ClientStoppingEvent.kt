package org.blackaddons.api.event

/** Normal shutdown; posted once on the client thread. Not posted on a crash. */
object ClientStoppingEvent : Event()
