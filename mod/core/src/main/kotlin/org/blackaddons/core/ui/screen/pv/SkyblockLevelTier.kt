package org.blackaddons.core.ui.screen.pv

import org.blackaddons.api.ui.theme.MinecraftColors
import java.awt.Color

/** Hypixel's Skyblock level colors: one chat color per 40 levels, gray up to dark red past 480. */
internal object SkyblockLevelTier {

    private const val TIER_SIZE = 40

    private val TIERS = with(MinecraftColors) {
        listOf(GRAY, WHITE, YELLOW, GREEN, DARK_GREEN, AQUA, DARK_AQUA, BLUE, LIGHT_PURPLE, DARK_PURPLE, GOLD, RED, DARK_RED)
    }

    fun color(level: Int): Color = TIERS[(level / TIER_SIZE).coerceIn(TIERS.indices)]
}
