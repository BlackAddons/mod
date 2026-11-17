package org.blackaddons.cheat.features.impl.cheats.simonSays

import org.blackaddons.api.Platform
import org.blackaddons.api.util.containsIgnoreCase
import org.blackaddons.core.features.impl.dungeons.simonSays.SimonSaysDevice

/** `ACTIVE` means passed, a running device reads `INACTIVE`. */
internal enum class DeviceHologram {
    NONE, INACTIVE, ACTIVE;

    companion object {
        /**
         * Each poll snapshots nearby armor stands. We only have to catch the edge before anyone can press, and a miss
         * fails safe.
         */
        const val POLL_TICKS = 5

        /** Tight, because nearby terminals use the same labels. */
        private const val RADIUS = 5.0

        /* "Inactive" contains "active", so we check it first. */
        fun read(): DeviceHologram {
            val at = SimonSaysDevice.START_BUTTON
            var active = false
            for (e in Platform.world.entitiesNear(at.x.toDouble(), at.y.toDouble(), at.z.toDouble(), RADIUS)) {
                if (!e.isArmorStand) continue
                if (e.name.containsIgnoreCase("inactive")) return INACTIVE
                if (e.name.containsIgnoreCase("active")) active = true
            }
            return if (active) ACTIVE else NONE
        }
    }
}
