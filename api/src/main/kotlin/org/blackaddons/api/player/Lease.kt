package org.blackaddons.api.player

/**
 * A feature's hold on a shared control (view, mouse button). The host decides whose is live and can revoke
 * it; a feature only ever releases its own.
 */
interface Lease {
    val state: LeaseState
    val isActive: Boolean get() = state == LeaseState.ACTIVE

    /** A new request is needed to hold the control again. */
    val isEnded: Boolean get() = state == LeaseState.RELEASED || state == LeaseState.REVOKED

    /** Harmless if already ended. */
    fun release()
}

enum class LeaseState {
    WAITING,

    ACTIVE,

    /** By its owner, or replaced by the owner's newer request. */
    RELEASED,

    /** Owner disabled, world changed, or a stronger claim arrived. */
    REVOKED,
}
