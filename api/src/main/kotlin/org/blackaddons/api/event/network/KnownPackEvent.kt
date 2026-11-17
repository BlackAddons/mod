package org.blackaddons.api.event.network

import org.blackaddons.api.event.Event

/**
 * One per pack acknowledged in the "known packs" negotiation (a `fabric` entry reveals Fabric API). Set
 * [hidden] to report it absent; the server then sends that pack's data in full.
 */
class KnownPackEvent(
    val namespace: String,
    val id: String,
    val version: String,
) : Event() {
    var hidden: Boolean = false
}
