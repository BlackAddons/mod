package org.blackaddons.core.features.impl.visual

import org.blackaddons.api.feature.Feature

object EntityQol : Feature(
    description = "Quality of life adjustments for entity visuals.",
    name = "Entity QoL",
) {
    val hideEntityFire by boolean("Hide Entity Fire", default = true)
        .withDescription("Hides flames rendered around burning entities, including players.")

    val hideArmor by boolean("Hide Armor", default = false)
        .withDescription("Hides worn armor on players, mobs, and armored animals. Elytra and saddles stay visible.")

    val hideStuckArrowsAndStingers by boolean("Hide Stuck Arrows and Stingers", default = true)
        .withDescription("Hides arrows and bee stingers embedded in player models.")
}
