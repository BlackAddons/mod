package org.blackaddons.api.event.network

import org.blackaddons.api.event.Event

/** Time-sync packet; vanilla sends one every 20 server ticks. */
class ServerTimeUpdateEvent(val timestamp: Long = System.currentTimeMillis()) : Event()
