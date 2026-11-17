package org.blackaddons.core.control

import org.blackaddons.api.player.Lease
import org.blackaddons.api.player.LeaseState

/**
 * One exclusive control, one lease at a time. This is [AimControl]'s policy, split out for testing. Ordered by
 * priority, then by arrival. Higher priority takes over at once. A newer lease from an owner replaces its older one.
 * Client thread only.
 */
internal class LeaseArbiter<L : LeaseArbiter.Entry>(private val events: Events<L>) {

    interface Events<L> {
        fun granted(lease: L)
        fun ended(lease: L)
    }

    abstract class Entry(val owner: Any, val priority: Int) : Lease {
        // Written on the client thread, read by the holder.
        @Volatile
        final override var state: LeaseState = LeaseState.WAITING
            internal set
    }

    var active: L? = null
        private set

    private val waiting = ArrayList<L>()

    /** Nothing gets granted while the host gives the control back. See [resume]. */
    var held = false

    fun resume() {
        held = false
        promote()
    }

    /** [first] skips ahead of its own priority, for an owner that resumes a control it just had. */
    fun request(lease: L, first: Boolean = false) {
        waiting.filter { it.owner === lease.owner }.forEach { drop(it, LeaseState.RELEASED) }
        val current = active
        when {
            current != null && current.owner === lease.owner -> swap(current, lease, LeaseState.RELEASED)
            current != null && lease.priority > current.priority -> swap(current, lease, LeaseState.REVOKED)
            else -> {
                val index = waiting.indexOfFirst { if (first) it.priority <= lease.priority else it.priority < lease.priority }
                if (index < 0) waiting += lease else waiting.add(index, lease)
                promote()
            }
        }
    }

    fun release(lease: L) = end(lease, LeaseState.RELEASED)

    fun tick() = revokeWhere { isSwitchedOff(it.owner) }

    fun clear() = revokeWhere { true }

    private fun revokeWhere(predicate: (L) -> Boolean) {
        waiting.filter(predicate).forEach { drop(it, LeaseState.REVOKED) }
        active?.takeIf(predicate)?.let { end(it, LeaseState.REVOKED) }
    }

    private fun swap(current: L, next: L, outcome: LeaseState) {
        current.state = outcome
        active = null
        events.ended(current)
        grant(next)
    }

    private fun end(lease: L, outcome: LeaseState) {
        if (lease.isEnded) return
        if (lease !== active) {
            drop(lease, outcome)
            return
        }
        lease.state = outcome
        active = null
        events.ended(lease)
        promote()
    }

    private fun drop(lease: L, outcome: LeaseState) {
        if (waiting.remove(lease)) lease.state = outcome
    }

    private fun promote() {
        if (active != null || held || waiting.isEmpty()) return
        grant(waiting.removeAt(0))
    }

    private fun grant(lease: L) {
        lease.state = LeaseState.ACTIVE
        active = lease
        events.granted(lease)
    }
}
