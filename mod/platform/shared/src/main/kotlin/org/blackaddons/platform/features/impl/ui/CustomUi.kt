package org.blackaddons.platform.features.impl.ui

import org.blackaddons.api.feature.Feature
import org.blackaddons.platform.ui.skin.*

object CustomUi : Feature(
    description = "Repaint vanilla screens in the BlackAddons look. Follows your theme.",
    name = "Custom UI",
) {
    private val tint by boolean("Tint", default = true)
        .withDescription("Color the glass with your theme. Off leaves it a neutral smoke.")
        .group(APPEARANCE)
        .onChange { sync() }
    private val opacity by number("Opacity", 70.0, 20.0, 100.0, 5.0, unit = "%")
        .withDescription("How much of the world shows through the glass.")
        .group(APPEARANCE)
        .onChange { sync() }
    private val blur by boolean("Blur", default = true)
        .withDescription("Blur the world behind in-game screens. Follows Minecraft's Menu Background Blur.")
        .group(APPEARANCE)
        .onChange { sync() }
    private val containers by boolean("Containers", default = true)
        .withDescription("Every container screen, the creative inventory and the recipe book.")
        .group(SCREENS)
        .onChange { sync() }
    private val hideFillerPanes by boolean("Hide Filler Panes", default = true)
        .withDescription("Hide the blank gray and black glass panes that fill Skyblock menus. Clicking them still works.")
        .showIf { containers.value }
        .group(SCREENS)
        .onChange { sync() }
    private val buttons by boolean("Buttons & Inputs", default = true)
        .withDescription("Buttons, text fields, sliders, checkboxes, tabs, scroll bars and page arrows.")
        .group(SCREENS)
        .onChange { sync() }
    private val buttonScope by mode("Button Scope", default = ALL_SCREENS, modes = listOf(ALL_SCREENS, MINECRAFT_SCREENS))
        .withDescription("Minecraft Screens leaves other mods' screens as they are.")
        .showIf { buttons.value }
        .group(SCREENS)
        .onChange { sync() }
    private val menus by boolean("Menus", default = true)
        .withDescription("Menu backgrounds and lists, in-game backdrops, popups, game mode switcher and advancements.")
        .group(SCREENS)
        .onChange { sync() }
    private val tooltips by boolean("Tooltips", default = true)
        .withDescription("Item and button tooltips.")
        .group(SCREENS)
        .onChange { sync() }
    private val deathScreen by boolean("Death Screen", default = true)
        .withDescription("The death screen's red backdrop in your theme.")
        .group(SCREENS)
        .onChange { sync() }
    private val chat by boolean("Chat", default = true)
        .withDescription("Chat messages, the chat input and command suggestions.")
        .group(CHAT)
        .onChange { sync() }
    private val chatStyle by mode("Chat Style", default = CARD, modes = listOf(BUBBLES, CARD))
        .withDescription("A bubble per message, or one card behind the whole chat.")
        .showIf { chat.value }
        .group(CHAT)
        .onChange { sync() }
    private val toasts by boolean("Toasts", default = true)
        .withDescription("Advancement, recipe, system and tutorial toasts.")
        .group(HUD)
        .onChange { sync() }
    private val tabList by boolean("Tab List", default = true)
        .withDescription("The player list's header, body and footer.")
        .group(HUD)
        .onChange { sync() }
    private val scoreboard by boolean("Scoreboard", default = true)
        .withDescription("The scoreboard sidebar.")
        .group(HUD)
        .onChange { sync() }
    private val bossBar by boolean("Boss Bar", default = true)
        .withDescription("Boss health bars.")
        .group(HUD)
        .onChange { sync() }
    private val actionBar by boolean("Action Bar", default = true)
        .withDescription("The message above the hotbar.")
        .group(HUD)
        .onChange { sync() }
    private val hotbar by boolean("Hotbar & Effects", default = true)
        .withDescription("The hotbar, offhand slot and status effect backgrounds.")
        .group(HUD)
        .onChange { sync() }

    override fun onEnable() {
        super.onEnable()
        sync()
    }

    override fun onDisable() {
        super.onDisable()
        sync()
    }

    private fun sync() {
        ContainerGlass.enabled = enabled && containers.value
        ContainerGlass.hideFillers = hideFillerPanes.value
        GuiSkin.widgets = enabled && buttons.value
        GuiSkin.minecraftScreensOnly = buttonScope.value == MINECRAFT_SCREENS
        ChatGlass.enabled = enabled && chat.value
        ChatGlass.bubbles = chatStyle.value == BUBBLES
        GuiSkin.toasts = enabled && toasts.value
        SkinGlass.tint = tint.value
        SkinGlass.opacity = (opacity.value / 100.0).toFloat()
        SkinGlass.blur = blur.value
        SkinGlass.backdrops = enabled && menus.value
        MenuGlass.enabled = enabled && menus.value
        GuiSkin.menus = enabled && menus.value
        GuiSkin.tooltips = enabled && tooltips.value
        GuiSkin.hotbar = enabled && hotbar.value
        GuiSkin.bossBar = enabled && bossBar.value
        HudGlass.tabList = enabled && tabList.value
        HudGlass.scoreboard = enabled && scoreboard.value
        HudGlass.actionBar = enabled && actionBar.value
        HudGlass.deathScreen = enabled && deathScreen.value
    }

    private const val APPEARANCE = "Appearance"
    private const val SCREENS = "Screens"
    private const val CHAT = "Chat"
    private const val HUD = "HUD"
    private const val ALL_SCREENS = "All Screens"
    private const val MINECRAFT_SCREENS = "Minecraft Screens"
    private const val BUBBLES = "Bubbles"
    private const val CARD = "Card"
}
