package org.blackaddons.core.features.impl.ui

import org.blackaddons.api.Platform
import org.blackaddons.api.feature.Feature
import org.blackaddons.api.hud.VanillaHudPart

object ScoreboardToggle : Feature(
    description = "Allows toggling the visibility of the scoreboard sidebar.",
    name = "Scoreboard Toggle",
) {
    val showScoreboard by boolean("Show Scoreboard", default = true)
        .withDescription("Whether the scoreboard sidebar is visible.")

    private val toggleKey by keybind("Toggle Keybind")
        .withDescription("Press this key in-world to toggle scoreboard visibility.")

    override fun init() {
        Platform.vanillaHud.hideWhen(VanillaHudPart.SCOREBOARD) { enabled && !showScoreboard.value }

        onKeybind(toggleKey) {
            showScoreboard.value = !showScoreboard.value
        }
    }
}
