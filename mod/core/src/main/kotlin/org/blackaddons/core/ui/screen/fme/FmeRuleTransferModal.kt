package org.blackaddons.core.ui.screen.fme

import org.blackaddons.api.ui.render.Render
import org.blackaddons.api.ui.screen.ModalPanel
import org.blackaddons.api.ui.screen.UiManager
import org.blackaddons.api.ui.theme.TextStyle
import org.blackaddons.api.ui.toast.ToastKind
import org.blackaddons.api.ui.toast.Toasts
import org.blackaddons.api.ui.widget.Button
import org.blackaddons.api.ui.widget.dropdown.Dropdown
import org.blackaddons.api.util.I18n
import org.blackaddons.core.features.impl.visual.fme.Fme
import org.blackaddons.core.features.impl.visual.fme.FmeManagedGroup
import org.blackaddons.core.features.impl.visual.fme.FmeScope

/** Picks another saved scope for a type rule, so its context menu stays short. */
internal class FmeRuleTransferModal(private val group: FmeManagedGroup) : ModalPanel(tr("move_copy", "Move / Copy rule")) {

    override val panelWidth: Float get() = fitWidth(380f, min = 300f)
    override val panelHeight: Float get() = fitHeight(176f, min = 150f)

    private data class Target(val scope: FmeScope, val key: String, val label: String)

    private val targets = buildList {
        FmeScope.entries.forEach { scope ->
            Fme.scopeKeys(scope).forEach { key ->
                if (scope != group.scope || key != group.scopeKey) {
                    val scopeName = when (scope) {
                        FmeScope.GLOBAL -> tr("tab_global", "Global")
                        FmeScope.LOCATION -> tr("tab_locations", "Locations")
                        FmeScope.DUNGEON -> tr("tab_rooms", "Dungeon Rooms")
                    }
                    add(Target(scope, key, if (key.isEmpty()) scopeName else "$scopeName: $key"))
                }
            }
        }
    }
    private val dropdown = widgets.add(Dropdown().apply { items = targets.map { Dropdown.Item(it.label) } })
    private val copyButton = widgets.add(Button(tr("copy_rule", "Copy")) { transfer(move = false) })
    private val moveButton = widgets.add(Button(tr("move_rule", "Move")) { transfer(move = true) }.apply { accent = true })
    private val cancelButton = widgets.add(Button(tr("cancel", "Cancel")) { UiManager.pop() })

    override fun renderContent(mouseX: Float, mouseY: Float) {
        Render.text(tr("destination", "Destination"), contentX, contentY, TextStyle.SMALL_MUTED_MEDIUM)
        dropdown.render(contentX, contentY + 18f, contentW, 22f, mouseX, mouseY)
        val y = contentY + contentH - 22f
        copyButton.enabled = targets.isNotEmpty()
        moveButton.enabled = targets.isNotEmpty()
        copyButton.render(contentX, y, 72f, 22f, mouseX, mouseY)
        moveButton.render(contentX + 78f, y, 72f, 22f, mouseX, mouseY)
        cancelButton.render(contentX + contentW - 76f, y, 76f, 22f, mouseX, mouseY)
        dropdown.renderOverlay(mouseX, mouseY)
    }

    private fun transfer(move: Boolean) {
        val target = targets.getOrNull(dropdown.selectedIndex) ?: return
        if (!Fme.transferTypeGroup(group, target.scope, target.key, move)) return
        UiManager.pop()
        Toasts.show(
            if (move) tr("rule_moved", "Rule moved to %s.", target.label) else tr("rule_copied", "Rule copied to %s.", target.label),
            kind = ToastKind.SUCCESS,
        )
    }

    private companion object {
        val tr = I18n.scope("blackaddons.fme")
    }
}
