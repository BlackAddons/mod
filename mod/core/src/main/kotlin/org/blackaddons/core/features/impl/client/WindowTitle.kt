package org.blackaddons.core.features.impl.client

import org.blackaddons.api.Platform
import org.blackaddons.api.feature.Categories
import org.blackaddons.api.feature.Feature

object WindowTitle : Feature(
    description = "Shows BlackAddons in the window title.",
) {
    override val section = Categories.Client.Menu

    private const val MOD = "{mod}"
    private const val MC = "{mc}"

    private val format by string(
        name = "Format",
        default = "$MOD - $MC",
        placeholder = "$MOD - $MC",
    ).withDescription("$MOD is BlackAddons and its version, $MC is Minecraft and its version. Empty = vanilla title.")
        .onChange { if (enabled) apply() }

    override fun onEnable() = apply()

    override fun onDisable() = Platform.desktop.setWindowTitle(null)

    private fun apply() {
        val title = format.value.trim()
            .replace(MOD, "BlackAddons ${Platform.blackaddonsVersion}")
            .replace(MC, "Minecraft ${Platform.minecraftVersion}")
        Platform.desktop.setWindowTitle(title.ifEmpty { null })
    }
}
