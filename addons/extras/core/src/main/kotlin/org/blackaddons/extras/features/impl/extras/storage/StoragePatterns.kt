package org.blackaddons.extras.features.impl.extras.storage

/**
 * Hypixel storage menu titles (the ones NEU keys off). The stripped title gives container and page, which is the cache
 * key.
 */
internal object StoragePatterns {

    // "Ender Chest (3/9)", optional ✦.
    private val ENDER_CHEST = Regex("""Ender Chest (?:✦ )?\((\d+)/\d+\)""")

    // "Large Backpack (Slot #3)": the first word is the size; ✦ when upgraded.
    private val BACKPACK = Regex("""([A-Za-z]+) Backpack ?✦? \(Slot #(\d+)\)""")

    // The overview that lists every page and backpack.
    private val STORAGE = Regex("""^Storage$""")

    /**
     * Overview item names: "Ender Chest Page 4", "Backpack Slot 13", and the not-openable "Empty/Locked Backpack Slot
     * N" (NEU's prefixes).
     */
    private val OVERVIEW_ENDER = Regex("""Ender Chest Page (\d+)""")
    private val OVERVIEW_BACKPACK = Regex("""Backpack Slot (\d+)""")
    private val OVERVIEW_EMPTY_BACKPACK = Regex("""Empty Backpack Slot (\d+)""")
    private val OVERVIEW_LOCKED_BACKPACK = Regex("""Locked Backpack Slot (\d+)""")

    /** Null for filler and navigation. Empty and locked slots get tested first, they contain "Backpack Slot N" too. */
    fun parseOverviewSlot(name: String): StorageRef? {
        OVERVIEW_ENDER.find(name)?.let {
            return StorageRef(ContainerType.ENDER_CHEST, it.groupValues[1].toInt(), name)
        }
        OVERVIEW_EMPTY_BACKPACK.find(name)?.let {
            return StorageRef(ContainerType.BACKPACK, it.groupValues[1].toInt(), name, state = SlotState.EMPTY)
        }
        OVERVIEW_LOCKED_BACKPACK.find(name)?.let {
            return StorageRef(ContainerType.BACKPACK, it.groupValues[1].toInt(), name, state = SlotState.LOCKED)
        }
        OVERVIEW_BACKPACK.find(name)?.let {
            return StorageRef(ContainerType.BACKPACK, it.groupValues[1].toInt(), name)
        }
        return null
    }

    fun matchPage(strippedTitle: String): OpenPage? {
        ENDER_CHEST.find(strippedTitle)?.let {
            return OpenPage(ContainerType.ENDER_CHEST, it.groupValues[1].toInt(), strippedTitle)
        }
        BACKPACK.find(strippedTitle)?.let {
            return OpenPage(ContainerType.BACKPACK, it.groupValues[2].toInt(), strippedTitle)
        }
        return null
    }

    fun isStorageOverview(strippedTitle: String): Boolean = STORAGE.matches(strippedTitle)

    /** e.g. `enderchest:3`, `backpack:5`. */
    fun containerKey(type: ContainerType, page: Int): String = when (type) {
        ContainerType.ENDER_CHEST -> "enderchest:$page"
        ContainerType.BACKPACK -> "backpack:$page"
    }
}
