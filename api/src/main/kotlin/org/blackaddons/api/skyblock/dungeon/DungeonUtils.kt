package org.blackaddons.api.skyblock.dungeon

object DungeonUtils {
    val floorEnterRegex = Regex("""-+\s.+ entered.+The Catacombs, Floor [IVX]+!\s-+""")

    val WITHER_ESSENCE = setOf(
        "2865274b-3097-394e-8149-ec629c72d850",
        "e0f3e929-869e-3dca-9504-54c666ee6f23",
    )

    val REDSTONE_KEY = setOf(
        "fed95410-aba1-39df-9b95-1d4f361eb66e",
    )

    /** Wither essence or redstone key skull. */
    fun isSecretSkull(profileId: String?): Boolean =
        profileId != null && (profileId in WITHER_ESSENCE || profileId in REDSTONE_KEY)

    val dungeonItemDrops = listOf(
        "Health Potion VIII Splash Potion", "Healing Potion 8 Splash Potion",
        "Healing Potion VIII Splash Potion", "Healing VIII Splash Potion",
        "Healing 8 Splash Potion", "Decoy", "Inflatable Jerry", "Spirit Leap",
        "Trap", "Training Weights", "Defuse Kit", "Dungeon Chest Key",
        "Treasure Talisman", "Revive Stone", "Architect's First Draft",
        "Secret Dye", "Candycomb",
    )
}
