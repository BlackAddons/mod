package org.blackaddons.cheat.features.impl.cheats.autoRelic

import org.blackaddons.api.skyblock.dungeon.DungeonClass
import org.blackaddons.api.util.cleanName
import org.blackaddons.api.world.BlockPos3

/**
 * M7 phase 5 Necron relics. [carrier] is the class the meta assigns to each cauldron. Red and orange spawn by their
 * cauldrons and get walked, the rest get leapt with.
 */
internal enum class Relic(val cauldron: BlockPos3, val carrier: DungeonClass) {
    RED(BlockPos3(51, 7, 42), DungeonClass.ARCHER),
    ORANGE(BlockPos3(57, 7, 42), DungeonClass.BERSERK),
    GREEN(BlockPos3(49, 7, 44), DungeonClass.ARCHER),
    BLUE(BlockPos3(59, 7, 44), DungeonClass.BERSERK),
    PURPLE(BlockPos3(54, 7, 41), DungeonClass.BERSERK),
    ;

    val carriedOnFoot: Boolean get() = this == RED || this == ORANGE

    /** The on-foot relic of the player this one leaps to: red for green, orange for blue and purple. */
    val partner: Relic? get() = if (carriedOnFoot) null else entries.first { it.carriedOnFoot && it.carrier == carrier }

    companion object {
        private val ITEM_NAME = Regex("corrupted (\\w+) relic")

        /** From an item name like "Corrupted Red Relic". */
        fun fromItemName(name: String): Relic? {
            val color = ITEM_NAME.find(name.cleanName())?.groupValues?.get(1) ?: return null
            return fromColor(color)
        }

        fun fromColor(color: String): Relic? = entries.firstOrNull { it.name.equals(color, ignoreCase = true) }
    }
}
