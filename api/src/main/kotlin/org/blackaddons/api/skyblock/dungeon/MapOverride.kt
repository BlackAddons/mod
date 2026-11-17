package org.blackaddons.api.skyblock.dungeon

import java.awt.Color

/** Lets an addon widen what the dungeon map shows without the map knowing about it. Defaults are the plain behavior. */
interface MapOverride {

    val showsUnrevealed: Boolean get() = false

    /** Reveals the entered room and neighbors ahead of the map item, and keeps the guess against stale updates. */
    val keepsOptimisticRooms: Boolean get() = false

    /** Null leaves it alone. */
    val mimicTint: Color? get() = null
}

/** Single slot: two overrides disagreeing has no sensible answer, so last registration wins. */
object MapOverrides {

    @Volatile
    private var current: MapOverride? = null

    fun register(override: MapOverride) {
        current = override
    }

    fun release(override: MapOverride) {
        if (current === override) current = null
    }

    val showsUnrevealed: Boolean get() = current?.showsUnrevealed == true

    val keepsOptimisticRooms: Boolean get() = current?.keepsOptimisticRooms == true

    val mimicTint: Color? get() = current?.mimicTint
}
