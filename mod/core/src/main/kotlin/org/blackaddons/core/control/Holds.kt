package org.blackaddons.core.control

import org.blackaddons.api.player.Lease
import org.blackaddons.api.player.LeaseState

/**
 * Shared holds on keys or buttons: one stays down while any lease holds it. [apply] only gets the changes and runs
 * under the lock, so it should only hand them off. Thread-safe.
 */
class Holds<K>(private val apply: (K, Boolean) -> Unit = { _, _ -> }) {

    private val holds = ArrayList<Hold>()
    private val down = HashSet<K>()

    @Synchronized
    fun hold(key: K, owner: Any): Lease {
        holds.filter { it.owner === owner && it.key == key }.forEach { it.end(LeaseState.RELEASED, refresh = false) }
        return Hold(key, owner).also {
            holds += it
            refresh(key)
        }
    }

    @Synchronized
    fun isHeld(key: K): Boolean = key in down

    fun tick() = revokeWhere { isSwitchedOff(it.owner) }

    fun clear() = revokeWhere { true }

    @Synchronized
    private fun revokeWhere(predicate: (Hold) -> Boolean) {
        holds.filter(predicate).forEach { it.end(LeaseState.REVOKED) }
    }

    private fun refresh(key: K) {
        val held = holds.any { it.key == key }
        if (held == key in down) return
        if (held) down += key else down -= key
        apply(key, held)
    }

    private inner class Hold(val key: K, val owner: Any) : Lease {
        @Volatile
        override var state = LeaseState.ACTIVE
            private set

        override fun release() = end(LeaseState.RELEASED)

        fun end(outcome: LeaseState, refresh: Boolean = true) = synchronized(this@Holds) {
            if (isEnded) return@synchronized
            state = outcome
            holds.remove(this)
            if (refresh) refresh(key)
        }
    }
}
