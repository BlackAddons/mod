package org.blackaddons.api.actions

import org.blackaddons.api.HostApi

/** The queue that runs action steps. Changes arrive as [org.blackaddons.api.event.actions.ActionStepEvent]s. */
object ActionQueue {

    @Volatile
    private var bound: Provider? = null

    private val provider: Provider
        get() = bound ?: error("ActionQueue was used before the host bound its provider")

    interface Provider {
        fun cancel()
    }

    /** Host only. */
    @HostApi
    fun bind(provider: Provider) {
        synchronized(this) {
            bound?.let { error("ActionQueue is already bound to ${it::class.java.name}") }
            bound = provider
        }
    }

    /** Drops every queued step and posts [org.blackaddons.api.event.actions.ActionStepEvent.Cancelled]. */
    fun cancel() = provider.cancel()
}
