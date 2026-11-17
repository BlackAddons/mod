package org.blackaddons.api.event.network

import org.blackaddons.api.event.Cancellable
import org.blackaddons.api.event.Event

/**
 * Every outgoing custom-payload packet; cancel to drop it. For `minecraft:register`/`unregister`
 * ([isChannelRegistration]) set [announceChannels] to announce fewer channels - the server then sends nothing
 * on the hidden ones. The brand payload goes through [ClientBrandEvent].
 */
class CustomPayloadSendEvent(
    val channel: String,
    val isChannelRegistration: Boolean,
    /** Channels the announcement carries, if readable. Filter this list rather than inventing ids. */
    val registeredChannels: List<String> = emptyList(),
    override var cancelled: Boolean = false,
) : Event(), Cancellable {

    /** Ignored unless [isChannelRegistration]. */
    var announceChannels: List<String>? = null
}
