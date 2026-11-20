package org.blackaddons.core.features.impl.ui.inventorybuttons

import org.blackaddons.api.Platform
import org.blackaddons.api.ui.screen.UiManager
import org.blackaddons.api.util.I18n
import org.blackaddons.core.config.portable.PortableImports
import org.blackaddons.core.ui.screen.portable.PortableImportModal
import org.blackaddons.core.ui.screen.profile.ProfileStore

private val tr = I18n.scope("blackaddons.inventory_buttons.profiles")

internal object InventoryButtonProfiles : ProfileStore {

    override val title: String get() = tr("title", "Inventory Button Profiles")

    override val profiles: List<ProfileStore.Entry> get() = InventoryButtonStore.profiles.map { ProfileStore.Entry(it.id, it.name) }

    override val active: ProfileStore.Entry get() = InventoryButtonStore.active.let { ProfileStore.Entry(it.id, it.name) }

    override fun select(id: String) = changed { InventoryButtonStore.select(id) }

    override fun create(name: String, copyCurrent: Boolean) = changed {
        InventoryButtonStore.create(name, if (copyCurrent) InventoryButtonStore.buttons.map { it.copy() } else emptyList())
    }

    fun create(name: String, buttons: List<InventoryButton>) = changed { InventoryButtonStore.create(name, buttons) }

    override fun rename(id: String, name: String) {
        InventoryButtonStore.rename(id, name)
        InventoryButtonStore.save()
    }

    override fun delete(id: String) = changed { InventoryButtonStore.delete(id) }

    override fun openFolder() = Platform.desktop.openPath(Platform.configDir)

    override fun export(): String = InventoryButtonPortable.exportProfile(InventoryButtonStore.active)

    // Over the game's screen, since this also opens from the inventory.
    override fun paste(text: String, applied: () -> Unit) = UiManager.overlay(
        PortableImportModal(InventoryButtonPortable.title, PortableImports.preview(InventoryButtonPortable, text)) { applied() },
    )

    // False when no profile has that name.
    fun selectNamed(name: String): Boolean {
        val profile = InventoryButtonStore.profiles.firstOrNull { it.name.equals(name.trim(), ignoreCase = true) } ?: return false
        select(profile.id)
        return true
    }

    // Undo snapshots and selections belong to the profile they were made in.
    private inline fun changed(block: () -> Unit) {
        block()
        InventoryButtonEditor.reset()
        InventoryButtonEditor.clearHistory()
        ButtonPainter.clearPops()
        InventoryButtonStore.save()
    }
}
