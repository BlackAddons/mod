package org.blackaddons.api.skyblock.profile

/** The skills we know. Hypixel adds new ones from time to time, so a stat also carries its raw id. */
enum class Skill(val id: String, val displayName: String, val countsTowardAverage: Boolean) {
    FARMING("farming", "Farming", true),
    MINING("mining", "Mining", true),
    COMBAT("combat", "Combat", true),
    FORAGING("foraging", "Foraging", true),
    FISHING("fishing", "Fishing", true),
    ENCHANTING("enchanting", "Enchanting", true),
    ALCHEMY("alchemy", "Alchemy", true),
    TAMING("taming", "Taming", true),
    CARPENTRY("carpentry", "Carpentry", false),
    RUNECRAFTING("runecrafting", "Runecrafting", false),
    SOCIAL("social", "Social", false),
    HUNTING("hunting", "Hunting", false),
    ;

    companion object {
        /** Null for a skill that Hypixel added after this build. */
        fun of(id: String): Skill? = entries.firstOrNull { it.id.equals(id, ignoreCase = true) }
    }
}

/** Slayer quests by Hypixel's id (`zombie` is Revenant Horror). */
enum class Slayer(val id: String) {
    ZOMBIE("zombie"),
    SPIDER("spider"),
    WOLF("wolf"),
    ENDERMAN("enderman"),
    BLAZE("blaze"),
    VAMPIRE("vampire"),
    ;

    companion object {
        fun of(id: String): Slayer? = entries.firstOrNull { it.id.equals(id, ignoreCase = true) }
    }
}

enum class KuudraTier(val id: String, val displayName: String) {
    BASIC("none", "Basic"),
    HOT("hot", "Hot"),
    BURNING("burning", "Burning"),
    FIERY("fiery", "Fiery"),
    INFERNAL("infernal", "Infernal"),
    ;

    companion object {
        fun of(id: String?): KuudraTier? = entries.firstOrNull { it.id.equals(id, ignoreCase = true) || it.name.equals(id, ignoreCase = true) }
    }
}

enum class ProfileMode(val id: String?) {
    NORMAL(null),
    IRONMAN("ironman"),
    STRANDED("island"),
    BINGO("bingo"),

    /** A mode Hypixel added after this build. */
    OTHER(null),
    ;

    companion object {
        fun of(id: String?): ProfileMode =
            if (id.isNullOrBlank()) NORMAL else entries.firstOrNull { it.id.equals(id, ignoreCase = true) } ?: OTHER
    }
}

/**
 * Inventory sections that a player can hide in their API settings. A section missing from a member's pages was hidden.
 */
enum class InventoryPage { INVENTORY, PETS, WARDROBE, ENDER_CHEST, BACKPACKS, VAULT, ACCESSORIES, POTIONS, QUIVER, FISHING }
