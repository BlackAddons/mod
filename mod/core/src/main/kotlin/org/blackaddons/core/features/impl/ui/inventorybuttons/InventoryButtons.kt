package org.blackaddons.core.features.impl.ui.inventorybuttons

import org.blackaddons.api.Platform
import org.blackaddons.api.event.ClientTickEvent
import org.blackaddons.api.event.WorldChangeEvent
import org.blackaddons.api.feature.Feature
import org.blackaddons.core.ui.screen.profile.ProfileMenu

object InventoryButtons : Feature(
    description = "Buttons around your inventory that run a command or a shortcut action. Edit them in the inventory itself.",
    name = "Inventory Buttons",
) {
    val hoverTooltip by boolean("Hover Tooltip", default = true)
        .withDescription("Shows what a button runs when you hover it.")

    val skyblockOnly by boolean("Skyblock Only", default = true)
        .withDescription("Shows the buttons only on Skyblock. Edit mode works anywhere.")

    val pickerRarity by boolean("Picker Rarity Colors", default = true)
        .withDescription("Tints the item picker's cells by rarity.")

    private val editKey by keybind("Edit Key")
        .withDescription("Toggles edit mode in an open container, or opens your inventory in it.")

    private val editButtons by button("Edit Buttons", action = { edit() }, label = "Edit")
        .withDescription("Opens your inventory in edit mode. Also /b buttons.")

    private val profiles by button("Profiles", action = { ProfileMenu.open(InventoryButtonProfiles, changed = {}) }, label = "Manage")
        .withDescription("Switch, create, share or delete button profiles. Also on the pencil's right-click, or /b buttons profile <name>.")

    override fun init() {
        Platform.containerOverlays.register(InventoryButtonsOverlay)
        onKeybind(editKey, predicate = { isRunning && InventoryButtonsOverlay.keybindAllowed }, allowInScreens = true) {
            InventoryButtonsOverlay.toggleEdit()
        }
        register<ClientTickEvent.Post> { InventoryButtonsOverlay.tick() }
        register<WorldChangeEvent> { InventoryButtonsOverlay.reset() }
    }

    override fun onDisable() {
        InventoryButtonsOverlay.reset()
        super.onDisable()
    }

    internal fun edit() {
        if (isRunning) InventoryButtonsOverlay.requestEdit()
    }
}
