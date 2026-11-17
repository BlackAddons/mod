package org.blackaddons.api.skyblock.dungeon

/** How a dungeon secret chest looks in the world and when its window opens. */
object SecretChests {
    private val BLOCKS = setOf("minecraft:chest", "minecraft:trapped_chest")
    private val TITLES = setOf("Chest", "Large Chest")
    private val MENUS = setOf("minecraft:generic_9x3", "minecraft:generic_9x6")

    fun isChestBlock(blockId: String?): Boolean = blockId in BLOCKS

    /** Matches by an unformatted [title] and a menu registry id, as in `ContainerOpenEvent`. */
    fun isChestWindow(title: String, menuType: String): Boolean = title in TITLES && menuType in MENUS
}
