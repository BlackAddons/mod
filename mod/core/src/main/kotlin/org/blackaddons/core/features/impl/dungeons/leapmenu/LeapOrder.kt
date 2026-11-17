package org.blackaddons.core.features.impl.dungeons.leapmenu

import org.blackaddons.api.skyblock.dungeon.DungeonClass
import org.blackaddons.api.skyblock.dungeon.DungeonPlayer
import org.blackaddons.api.util.equalsIgnoreCase

internal object LeapOrder {

    const val CLASS = "A-Z Class"
    const val NAME = "A-Z Name"
    const val ODIN = "Odin"
    const val CUSTOM = "Custom"
    const val NONE = "None"
    val MODES = listOf(CLASS, NAME, ODIN, CUSTOM, NONE)

    const val CORNERS = 4

    private val ODIN_CORNER = mapOf(
        DungeonClass.ARCHER to 0,
        DungeonClass.BERSERK to 1,
        DungeonClass.HEALER to 2,
        DungeonClass.MAGE to 3,
        DungeonClass.TANK to 3,
    )
    private val ODIN_PRIORITY = mapOf(DungeonClass.BERSERK to 0, DungeonClass.TANK to 1)
    private const val ODIN_DEFAULT_PRIORITY = 2

    fun arrange(players: List<DungeonPlayer>, mode: String, customOrder: List<String> = emptyList()): List<DungeonPlayer?> {
        if (mode == ODIN) return odin(players)
        val sorted = when (mode) {
            CLASS -> players.sortedWith(compareBy({ it.dungeonClass.ordinal }, { it.name.lowercase() }))
            NAME -> players.sortedBy { it.name.lowercase() }
            CUSTOM -> players.sortedWith(compareBy({ customRank(it, customOrder) }, { it.name.lowercase() }))
            else -> players
        }
        return List(CORNERS) { sorted.getOrNull(it) }
    }

    private fun odin(players: List<DungeonPlayer>): List<DungeonPlayer?> {
        val corners = arrayOfNulls<DungeonPlayer>(CORNERS)
        val rest = ArrayDeque<DungeonPlayer>()
        for (player in players.sortedBy { ODIN_PRIORITY[it.dungeonClass] ?: ODIN_DEFAULT_PRIORITY }) {
            val corner = ODIN_CORNER[player.dungeonClass]
            if (corner != null && corners[corner] == null) corners[corner] = player else rest.add(player)
        }
        return corners.map { it ?: rest.removeFirstOrNull() }
    }

    private fun customRank(player: DungeonPlayer, order: List<String>): Int =
        order.indexOfFirst {
            val entry = it.trim()
            entry.equalsIgnoreCase(player.name) ||
                player.dungeonClass != DungeonClass.EMPTY && DungeonClass.fromName(entry) == player.dungeonClass
        }.takeIf { it >= 0 } ?: Int.MAX_VALUE
}
