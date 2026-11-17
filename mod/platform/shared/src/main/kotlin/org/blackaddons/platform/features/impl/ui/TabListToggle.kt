package org.blackaddons.platform.features.impl.ui

import net.minecraft.client.Minecraft
import org.blackaddons.api.Platform
import org.blackaddons.api.event.ClientTickEvent
import org.blackaddons.api.event.render.TabListVisibilityEvent
import org.blackaddons.api.feature.Feature

object TabListToggle : Feature(
    description = "Toggles or holds display of player tab list.",
    name = "Tab List Toggle",
) {
    val toggleMode by boolean("Toggle Mode", default = false)
        .withDescription("If enabled, tapping the player list key toggles the tab list display instead of requiring you to hold it.")

    private val toggleKey by keybind("Toggle Keybind")
        .withDescription("Press this key in-world to toggle the tab list display.")

    var tabListToggled = false
        private set

    private var tabKeyWasPressed = false

    override fun init() {
        register<TabListVisibilityEvent> {
            if (toggleMode.value) event.visible = tabListToggled
        }

        register<ClientTickEvent.Pre> {
            val mc = Minecraft.getInstance()
            if (mc.player != null && !Platform.screenHost.anyScreenOpen) {
                val isDown = mc.options.keyPlayerList.isDown
                if (isDown && !tabKeyWasPressed) {
                    if (toggleMode.value) {
                        tabListToggled = !tabListToggled
                    }
                }
                tabKeyWasPressed = isDown
            } else {
                tabListToggled = false
                tabKeyWasPressed = false
            }
        }

        onKeybind(toggleKey) {
            tabListToggled = !tabListToggled
        }
    }

    override fun onDisable() {
        super.onDisable()
        tabListToggled = false
        tabKeyWasPressed = false
    }
}
