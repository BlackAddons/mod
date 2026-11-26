package org.blackaddons.core.control

import org.blackaddons.api.event.input.MovementInputEvent
import org.blackaddons.api.player.*
import org.blackaddons.core.log.Log
import java.util.concurrent.CopyOnWriteArrayList

/**
 * The policy behind [MovementHost.drive] and [MovementHost.hold]. Drives aren't exclusive leases: they all stay live,
 * and the strongest one that steers this tick wins, so a preempted drive doesn't have to ask again. Requests come from
 * any thread, while [apply] and [tick] run on the client thread.
 */
class MovementControl {

    /**
     * Strongest first, and oldest first within a priority. Writes happen under the list's lock, and apply reads a
     * snapshot.
     */
    private val drives = CopyOnWriteArrayList<Drive>()
    private val holds = Holds<MoveKey>()

    fun drive(owner: Any, priority: DrivePriority, steer: () -> Set<MoveKey>?): Lease {
        val drive = Drive(owner, priority, steer)
        synchronized(drives) {
            drives.filter { it.owner === owner }.forEach { it.end(LeaseState.RELEASED) }
            val index = drives.indexOfFirst { it.priority < priority }
            if (index < 0) drives += drive else drives.add(index, drive)
        }
        return drive
    }

    fun hold(key: MoveKey, owner: Any): Lease = holds.hold(key, owner)

    fun apply(input: MovementInputEvent) {
        drives.firstNotNullOfOrNull { it.steerOrNull() }?.let(input::press)
        for (key in MoveKey.entries) if (holds.isHeld(key)) input.press(key)
    }

    fun tick() {
        drives.filter { isSwitchedOff(it.owner) }.forEach { it.end(LeaseState.REVOKED) }
        holds.tick()
    }

    // A drive decides each tick whether it steers, so only holds (like the sneak for one click) end with the world.
    fun onWorldChange() = holds.clear()

    private inner class Drive(
        val owner: Any,
        val priority: DrivePriority,
        private val steer: () -> Set<MoveKey>?,
    ) : Lease {
        @Volatile
        override var state = LeaseState.ACTIVE
            private set

        override fun release() = end(LeaseState.RELEASED)

        fun end(outcome: LeaseState) = synchronized(drives) {
            if (isEnded) return@synchronized
            state = outcome
            drives.remove(this)
        }

        // A steer that throws would throw every tick, so it gets revoked once instead.
        fun steerOrNull(): Set<MoveKey>? = try {
            steer()
        } catch (t: Throwable) {
            Log.main.error("Movement drive of {} threw and was revoked", owner::class.simpleName, t)
            end(LeaseState.REVOKED)
            null
        }
    }
}
