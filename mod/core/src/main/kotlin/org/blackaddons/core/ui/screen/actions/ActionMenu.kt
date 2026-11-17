package org.blackaddons.core.ui.screen.actions

import org.blackaddons.api.Platform
import org.blackaddons.api.ui.icon.LucideIcons
import org.blackaddons.api.ui.screen.ContextMenu
import org.blackaddons.api.ui.screen.UiManager
import org.blackaddons.api.util.I18n
import org.blackaddons.core.actions.ActionPortable
import org.blackaddons.core.actions.ActionProfile
import org.blackaddons.core.config.portable.PortableImports
import org.blackaddons.core.ui.screen.portable.PortableExportModal

/** Right-click menus for Actions. Shares as portable strings, so `/b import` works too. */
internal object ActionMenu {
    private val tr = I18n.scope("blackaddons.actions.menu")

    fun row(
        label: String, content: ActionProfile,
        edit: () -> Unit, steps: (() -> Unit)?, delete: () -> Unit,
    ) = UiManager.contextMenu(buildList<ContextMenu.Entry> {
        add(ContextMenu.Item(actionTr("edit", "Edit"), LucideIcons.settings, onClick = edit))
        steps?.let { add(ContextMenu.Item(actionTr("steps.button", "Steps"), LucideIcons.actions, onClick = it)) }
        add(ContextMenu.Separator)
        addAll(sharing(label, content))
        add(paste())
        add(ContextMenu.Separator)
        add(ContextMenu.Item(
            actionTr("delete", "Delete"), LucideIcons.circleX, danger = true,
            confirm = tr("delete_confirm", "Delete %s?", label), onClick = delete,
        ))
    })

    fun list() = UiManager.contextMenu(listOf(paste()))

    fun sharing(label: String, content: ActionProfile): List<ContextMenu.Entry> = listOf(
        ContextMenu.Item(PortableImports.tr("share", "Share"), LucideIcons.share) {
            PortableExportModal.share(label, ActionPortable.export(content, asProfile = false))
        },
        ContextMenu.Item(tr("duplicate", "Duplicate"), LucideIcons.copy) { ActionPortable.duplicate(content) },
    )

    private fun paste() = ContextMenu.Item(PortableImports.tr("paste", "Paste from clipboard"), LucideIcons.clipboardPaste) {
        PortableImports.open(ActionPortable, Platform.clipboard.paste())
    }
}
