package org.blackaddons.platform.features.impl.ui

import org.blackaddons.api.feature.Categories
import org.blackaddons.api.feature.Feature
import org.blackaddons.platform.impl.gui.TitleMenu

object MainMenu : Feature(
    description = "A clean main menu: the main entries in the middle, the rest as icons under them.",
) {
    override val section = Categories.Client.Menu

    private val title by mode("Title", default = "BlackAddons", modes = listOf("BlackAddons", "Minecraft Logo", "None"))
        .withDescription("What sits above the menu.")
        .onChange { apply() }
    private val singleplayer by boolean("Singleplayer in List", default = true)
        .withDescription("Off = Singleplayer moves to the icon row.")
        .onChange { apply() }
    private val hypixel by boolean("Hypixel Button", default = true)
        .withDescription("Joins Hypixel straight from the menu.")
        .onChange { apply() }
    private val splash by boolean("Splash Text", default = false)
        .withDescription("The yellow line next to the title.")
        .onChange { apply() }
    private val confirmQuit by boolean("Confirm Quit", default = false)
        .withDescription("Ask before quitting, in a small card over the quit button.")
        .onChange { apply() }

    override fun onEnable() {
        apply()
        TitleMenu.enabled = true
    }

    override fun onDisable() {
        TitleMenu.enabled = false
    }

    private fun apply() {
        TitleMenu.options = TitleMenu.Options(
            title = when (title.value) {
                "Minecraft Logo" -> TitleMenu.Title.MINECRAFT
                "None" -> TitleMenu.Title.NONE
                else -> TitleMenu.Title.BLACKADDONS
            },
            singleplayerText = singleplayer.value,
            hypixel = hypixel.value,
            splash = splash.value,
            confirmQuit = confirmQuit.value,
        )
    }
}
