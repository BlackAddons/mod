package org.blackaddons.core.skyblock

import org.blackaddons.api.ui.theme.Theme
import java.awt.Color

internal fun rarityColor(tier: String): Color = when (tier.uppercase()) {
    "UNCOMMON" -> Color(85, 255, 85)
    "RARE" -> Color(85, 85, 255)
    "EPIC" -> Color(170, 85, 255)
    "LEGENDARY" -> Color(255, 170, 0)
    "MYTHIC" -> Color(255, 85, 255)
    "SPECIAL", "VERY_SPECIAL" -> Color(255, 85, 85)
    else -> Theme.TEXT_SECONDARY
}
