package org.blackaddons.core.ui.screen.fme

import org.blackaddons.api.ui.render.Render
import org.blackaddons.api.ui.screen.ModalPanel
import org.blackaddons.api.ui.screen.UiManager
import org.blackaddons.api.ui.theme.TextStyle
import org.blackaddons.api.ui.widget.Button
import org.blackaddons.api.ui.widget.dropdown.Dropdown
import org.blackaddons.api.util.I18n
import org.blackaddons.core.features.impl.visual.fme.Fme
import org.blackaddons.core.features.impl.visual.fme.FmeLegacyImport
import org.blackaddons.core.features.impl.visual.fme.FmeScope
import org.blackaddons.core.features.impl.visual.fme.fmeBossLocationKey

internal class FmeImportLocationModal(
    private val onChosen: (locationAt: (y: Int) -> String) -> Unit,
) : ModalPanel(tr("import_location_title", "Where were these blocks?")) {

    override val panelWidth: Float get() = fitWidth(380f, min = 300f)
    override val panelHeight: Float get() = fitHeight(176f, min = 150f)

    private class Target(val label: String, val locationAt: (Int) -> String)

    private val targets = buildList {
        val here = Fme.currentScopeKey(FmeScope.LOCATION)
        here?.let { add(Target(tr("import_location_here", "Here: %s", it)) { _ -> it }) }
        add(Target(tr("import_location_f7", "F7 boss, split by phase"), FmeLegacyImport.f7ByPhase))
        val keys = (1..6).map { fmeBossLocationKey(it, null) } + (1..5).map { fmeBossLocationKey(7, it) } + Fme.scopeKeys(FmeScope.LOCATION)
        keys.distinct().filter { it != here }.forEach { key -> add(Target(key) { _ -> key }) }
    }
    private val dropdown = widgets.add(Dropdown().apply { items = targets.map { Dropdown.Item(it.label) } })
    private val importButton = widgets.add(Button(tr("import_location_confirm", "Import")) { choose() }.apply { accent = true })
    private val cancelButton = widgets.add(Button(tr("cancel", "Cancel")) { UiManager.pop() })

    override fun renderContent(mouseX: Float, mouseY: Float) {
        Render.text(tr("import_location_label", "Location"), contentX, contentY, TextStyle.SMALL_MUTED_MEDIUM)
        dropdown.render(contentX, contentY + 18f, contentW, 22f, mouseX, mouseY)
        val y = contentY + contentH - 22f
        importButton.render(contentX, y, 72f, 22f, mouseX, mouseY)
        cancelButton.render(contentX + contentW - 76f, y, 76f, 22f, mouseX, mouseY)
        dropdown.renderOverlay(mouseX, mouseY)
    }

    private fun choose() {
        val target = targets.getOrNull(dropdown.selectedIndex) ?: return
        UiManager.pop()
        onChosen(target.locationAt)
    }

    private companion object {
        val tr = I18n.scope("blackaddons.fme")
    }
}
