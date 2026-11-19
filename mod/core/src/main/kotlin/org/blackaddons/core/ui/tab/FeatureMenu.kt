package org.blackaddons.core.ui.tab

import org.blackaddons.api.Platform
import org.blackaddons.api.feature.Feature
import org.blackaddons.api.feature.translatedConfirm
import org.blackaddons.api.feature.translatedLabel
import org.blackaddons.api.feature.translatedName
import org.blackaddons.api.ui.icon.LucideIcons
import org.blackaddons.api.ui.screen.ContextMenu
import org.blackaddons.api.ui.screen.UiManager
import org.blackaddons.api.util.I18n
import org.blackaddons.core.config.portable.FeatureSettingsKind
import org.blackaddons.core.config.portable.PortableImports
import org.blackaddons.core.ui.screen.HudEditorScreen
import org.blackaddons.core.ui.screen.portable.PortableExportModal

/** Feature context menu: declared actions, toggle key, HUD and settings string, then reset. Empty groups and menus get skipped. */
internal object FeatureMenu {
    private val tr = I18n.scope("blackaddons.feature.menu")

    fun entries(feature: Feature): List<ContextMenu.Entry> =
        listOf(declared(feature), shared(feature), reset(feature))
            .filter { it.isNotEmpty() }
            .flatMapIndexed { index, group -> if (index == 0) group else listOf(ContextMenu.Separator) + group }

    private fun declared(feature: Feature): List<ContextMenu.Item> = feature.menuActions.map { action ->
        ContextMenu.Item(action.translatedLabel, action.icon, action.danger, confirm = action.translatedConfirm, onClick = action.run)
    }

    private fun shared(feature: Feature): List<ContextMenu.Entry> = buildList {
        if (!feature.isAlwaysActive) add(ContextMenu.KeybindItem(tr("toggle_key", "Toggle key"), feature.toggleBind, LucideIcons.power))
        feature.hudElements.firstOrNull()?.let { hud ->
            add(ContextMenu.Item(tr("hud_editor", "Edit HUD"), LucideIcons.hudEditor) { HudEditorScreen.open(hud) })
        }
        if (FeatureSettingsKind.hasTransferable(feature)) {
            add(ContextMenu.Item(tr("export", "Copy settings"), LucideIcons.copy) { export(feature) })
            add(ContextMenu.Item(tr("import", "Paste settings"), LucideIcons.clipboardPaste) {
                PortableImports.open(FeatureSettingsKind(feature), Platform.clipboard.paste())
            })
        }
    }

    private fun reset(feature: Feature): List<ContextMenu.Item> {
        if (!feature.isResettable) return emptyList()
        return listOf(ContextMenu.Item(
            tr("reset", "Reset to defaults"), LucideIcons.rotateCcw, danger = true,
            confirm = tr("reset_confirm", "Reset %s to its defaults?", feature.translatedName),
            onClick = feature::resetSettings,
        ))
    }

    private fun export(feature: Feature) = UiManager.push(PortableExportModal(
        tr("exported", "Settings copied"),
        tr("export_done", "The settings of %s are on your clipboard.", feature.translatedName),
        FeatureSettingsKind.export(feature),
    ))
}
