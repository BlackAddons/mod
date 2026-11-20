package org.blackaddons.core.features.impl.ui.inventorybuttons

import org.blackaddons.api.Platform
import org.blackaddons.api.gui.ContainerMetrics
import org.blackaddons.api.ui.icon.LucideIcons
import org.blackaddons.api.ui.screen.ContextMenu
import org.blackaddons.api.ui.screen.UiManager
import org.blackaddons.api.ui.widget.MouseButtonInput
import org.blackaddons.api.util.I18n
import org.blackaddons.core.config.portable.PortableImports
import org.blackaddons.core.ui.screen.portable.PortableExportModal
import org.blackaddons.core.ui.screen.portable.PortableImportModal
import org.blackaddons.core.ui.screen.profile.ProfileMenu
import org.blackaddons.core.ui.screen.profile.ProfilesModal

private val tr = I18n.scope("blackaddons.inventory_buttons")

internal object InventoryButtonMenus {

    private val editor get() = InventoryButtonEditor

    fun forButton(button: InventoryButton, selected: List<InventoryButton>, metrics: ContainerMetrics, mouse: MouseButtonInput) {
        val many = selected.size > 1
        UiManager.contextMenuOver(mouse.x, mouse.y, buildList {
            if (!many) add(ContextMenu.Item(tr("menu.edit", "Edit..."), icon = LucideIcons.pencil) { editor.openEditor(button) })
            add(ContextMenu.Item(tr("menu.duplicate", "Duplicate"), icon = LucideIcons.copy) { editor.duplicate(selected, metrics) })
            add(share(selected))
            add(ContextMenu.Separator)
            add(ContextMenu.Item(
                if (many) tr("menu.delete_many", "Delete %d buttons", selected.size) else tr("menu.delete", "Delete"),
                danger = true,
                confirm = if (many) tr("menu.delete_many_confirm", "Delete these %d buttons?", selected.size) else tr("menu.delete_confirm", "Delete this button?"),
            ) { editor.delete(selected) })
        })
    }

    fun forEmpty(metrics: ContainerMetrics, mouse: MouseButtonInput, mx: Int, my: Int) {
        val here = InventoryButtonsOverlay.placed.map { it.first }
        val active = InventoryButtonStore.active
        UiManager.contextMenuOver(mouse.x, mouse.y, listOf(
            ContextMenu.Item(tr("menu.new", "New button here")) { editor.create(metrics, mx, my) },
            ContextMenu.Item(tr("menu.always_grid", "Always show grid"), checked = { editor.alwaysGrid }) { editor.alwaysGrid = !editor.alwaysGrid },
            ContextMenu.Item(tr("menu.snap", "Snap to buttons"), checked = { InventoryButtonStore.snapToButtons }) {
                InventoryButtonStore.snapToButtons = !InventoryButtonStore.snapToButtons
                InventoryButtonStore.save()
            },
            ContextMenu.Separator,
        ) + InventoryButtonStore.profiles.map { profile ->
            ContextMenu.Item(profile.name, checked = { profile.id == InventoryButtonStore.active.id }) { InventoryButtonProfiles.select(profile.id) }
        } + listOf(
            ContextMenu.Item(tr("menu.manage_profiles", "Manage profiles..."), LucideIcons.settings) {
                UiManager.overlay(ProfilesModal(InventoryButtonProfiles) {})
            },
            ContextMenu.Separator,
            share(here, tr("menu.share_here", "Share buttons on this screen")),
            ContextMenu.Item(PortableImports.tr("share_profile", "Share profile"), LucideIcons.share, enabled = active.buttons.isNotEmpty()) {
                PortableExportModal.share(active.name, InventoryButtonPortable.exportProfile(active), over = true)
            },
            ContextMenu.Item(PortableImports.tr("paste", "Paste from clipboard"), LucideIcons.clipboardPaste) { paste() },
            ContextMenu.Separator,
            ContextMenu.Item(
                tr("menu.remove_all", "Remove all buttons here"),
                danger = true,
                enabled = here.isNotEmpty(),
                confirm = tr("menu.remove_all_confirm", "Remove every button shown on this screen?"),
            ) { editor.delete(here) },
        ))
    }

    private fun share(buttons: List<InventoryButton>, label: String = PortableImports.tr("share", "Share")) =
        ContextMenu.Item(label, LucideIcons.share, enabled = buttons.isNotEmpty()) {
            PortableExportModal.share(tr("share_label", "The share string"), InventoryButtonPortable.export(buttons), over = true)
        }

    fun forPencil(mouse: MouseButtonInput) =
        UiManager.contextMenuOver(mouse.x, mouse.y, ProfileMenu.entries(InventoryButtonProfiles, changed = {}))

    // Imported buttons come in selected, so they can be dragged off yours as one.
    private fun paste() {
        val preview = PortableImports.preview(InventoryButtonPortable, Platform.clipboard.paste())
        UiManager.overlay(PortableImportModal(InventoryButtonPortable.title, preview, editor::selectOnly))
    }
}
