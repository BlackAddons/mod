package org.blackaddons.core.features.impl.settings

import org.blackaddons.api.feature.Categories
import org.blackaddons.api.feature.Feature
import org.blackaddons.api.feature.annotations.AlwaysActive
import org.blackaddons.api.ui.UiScaling

/** UI size preferences, always active. Separate screen and HUD multipliers, see [UiScaling]. */
@AlwaysActive
object Scaling : Feature(
    description = "How large the BlackAddons interface and HUD are drawn.",
    name = "Scaling",
) {

    override val section = Categories.Settings.Appearance

    private val autoScale by boolean("Auto Scale", default = true)
        .withDescription(
            "Keep the interface and the HUD at the same share of the screen whatever its size, so " +
                "resizing the window no longer changes what the HUD covers. The HUD then stops " +
                "following Minecraft's GUI Scale — raising that makes the hotbar bigger and leaves " +
                "this alone. Turn it off to size everything exactly the way Minecraft does.",
        )

    private val interfaceScale by number(
        "Interface Scale",
        default = 1.0,
        min = 0.5,
        max = 2.0,
        step = 0.05,
        unit = "x",
    ).withDescription("Multiplier for every BlackAddons screen — the ClickGUI, the chat window, the profile viewer.")

    private val hudScale by number(
        "HUD Scale",
        default = 1.0,
        min = 0.5,
        max = 2.0,
        step = 0.05,
        unit = "x",
    ).withDescription("Multiplier for every HUD element, on top of the size each one is set to in the HUD editor.")

    override fun init() {
        // Read at draw time, so a slider resizes what's under it live.
        UiScaling.autoScale = { autoScale.value }
        UiScaling.interfaceScale = { interfaceScale.value.toFloat() }
        UiScaling.hudScale = { hudScale.value.toFloat() }
    }
}
