package org.blackaddons.api.event.network

import org.blackaddons.api.event.Event

/** You joined a server and play has started. [address] is what you connected to; null in singleplayer. */
class ServerJoinEvent(val address: String?) : Event()

/** You left the server, or the connection was lost. A [org.blackaddons.api.event.WorldChangeEvent] follows. */
object ServerLeaveEvent : Event()
