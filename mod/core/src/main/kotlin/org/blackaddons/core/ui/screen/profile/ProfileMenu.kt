package org.blackaddons.core.ui.screen.profile

import org.blackaddons.api.Platform
import org.blackaddons.api.ui.icon.LucideIcons
import org.blackaddons.api.ui.screen.ContextMenu
import org.blackaddons.api.ui.screen.UiManager
import org.blackaddons.api.util.I18n
import org.blackaddons.core.config.portable.PortableImports
import org.blackaddons.core.ui.screen.portable.PortableExportModal

/**
 * Profile button menu: switch, create, manage, share, paste, delete. [extra] goes after paste (e.g. FME file import).
 * [changed] refreshes the screen.
 */
internal object ProfileMenu {
    val tr = I18n.scope("blackaddons.profiles")

    fun open(store: ProfileStore, changed: () -> Unit, extra: List<ContextMenu.Entry> = emptyList()) =
        UiManager.contextMenu(entries(store, changed, extra))

    fun entries(store: ProfileStore, changed: () -> Unit, extra: List<ContextMenu.Entry> = emptyList()): List<ContextMenu.Entry> {
        val active = store.active
        return buildList {
            store.profiles.forEach { profile ->
                add(ContextMenu.Item(profile.name, LucideIcons.usersRound, enabled = profile.id != active.id) {
                    store.select(profile.id)
                    changed()
                })
            }
            add(ContextMenu.Separator)
            add(ContextMenu.Item(tr("new", "New Profile"), LucideIcons.usersRound) {
                store.create(tr("new_name", "New Profile"), copyCurrent = false)
                changed()
                // Straight to naming, because nobody keeps "New Profile".
                UiManager.overlay(ProfilesModal(store, changed))
            })
            add(ContextMenu.Item(tr("duplicate", "Duplicate Profile"), LucideIcons.copy) {
                store.create(tr("copy_name", "%s (Copy)", active.name), copyCurrent = true)
                changed()
            })
            add(ContextMenu.Item(tr("manage", "Rename / Manage..."), LucideIcons.settings) {
                UiManager.overlay(ProfilesModal(store, changed))
            })
            add(ContextMenu.Separator)
            add(ContextMenu.Item(PortableImports.tr("share_profile", "Share profile"), LucideIcons.share) {
                store.export()?.let { PortableExportModal.share(active.name, it, over = true) }
            })
            add(ContextMenu.Item(PortableImports.tr("paste", "Paste from clipboard"), LucideIcons.clipboardPaste) {
                store.paste(Platform.clipboard.paste(), changed)
            })
            addAll(extra)
            if (store.profiles.size > 1) {
                add(ContextMenu.Item(
                    tr("delete", "Delete Profile"), LucideIcons.circleX, danger = true,
                    confirm = tr("delete_confirm", "Delete profile %s?", active.name),
                ) {
                    store.delete(active.id)
                    changed()
                })
            }
        }
    }
}
