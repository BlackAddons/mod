package org.blackaddons.api.skyblock.dungeon

import org.blackaddons.api.HostApi

/**
 * Current Catacombs run, read on the client thread; empty outside a dungeon. Changes also arrive as
 * `org.blackaddons.api.event.dungeon` events.
 */
object Dungeon {

    @Volatile
    private var bound: DungeonProvider? = null

    private val provider: DungeonProvider
        get() = bound ?: error("Dungeon was read before the host bound its DungeonProvider")

    /** Host only. */
    @HostApi
    fun bind(provider: DungeonProvider) {
        synchronized(this) {
            bound?.let { error("Dungeon is already bound to ${it::class.java.name}") }
            bound = provider
        }
    }

    val run: DungeonRun get() = provider.run
    val team: DungeonTeam get() = provider.team
    val score: DungeonScore get() = provider.score
    val layout: DungeonLayout get() = provider.layout
}

enum class F7Phase(val number: Int, val boss: String) {
    MAXOR(1, "Maxor"),
    STORM(2, "Storm"),
    GOLDOR(3, "Goldor"),
    NECRON(4, "Necron"),
    WITHER_KING(5, "Wither King"),
}

enum class DungeonFloor(val number: Int, val isMasterMode: Boolean) {
    ENTRANCE(0, false),
    F1(1, false), F2(2, false), F3(3, false), F4(4, false), F5(5, false), F6(6, false), F7(7, false),
    M1(1, true), M2(2, true), M3(3, true), M4(4, true), M5(5, true), M6(6, true), M7(7, true),
    ;

    /** As the sidebar shows it: `"Entrance"`, `"F7"`, `"M7"`. */
    val label: String get() = if (this == ENTRANCE) "Entrance" else name

    /** The only floors with boss phases P1-P5. */
    val isF7orM7: Boolean get() = number == 7
}
