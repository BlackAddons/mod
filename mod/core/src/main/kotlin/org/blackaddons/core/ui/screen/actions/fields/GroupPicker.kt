package org.blackaddons.core.ui.screen.actions.fields
import org.blackaddons.api.ui.layout.LayoutBuilder
import org.blackaddons.api.ui.theme.TextStyle
import org.blackaddons.api.ui.widget.WidgetHost
import org.blackaddons.api.ui.widget.dropdown.Dropdown
import org.blackaddons.core.actions.ActionGroup
import org.blackaddons.core.ui.screen.actions.actionTr
import java.util.UUID

internal class GroupPicker(widgets: WidgetHost, private val groups: List<ActionGroup>, initial: UUID?) {

    var selected: UUID? = initial
        private set

    private val dropdown = widgets.add(Dropdown()).apply {
        items = listOf(Dropdown.Item(actionTr("waypoint.ungrouped", "None (Ungrouped)"))) + groups.map { Dropdown.Item(it.name) }
        /* Index 0 is "ungrouped". */
        selectedIndex = initial?.let { id -> groups.indexOfFirst { it.id == id }.takeIf { it >= 0 }?.plus(1) } ?: 0
        onSelect = { idx -> selected = if (idx <= 0) null else groups.getOrNull(idx - 1)?.id }
    }

    fun section(builder: LayoutBuilder, formW: Float, mouseX: Float, mouseY: Float) = with(builder) {
        text(actionTr("group.label", "Group:"), TextStyle.SMALL_MUTED_MEDIUM)
        cell(formW, 22f) { cx, cy -> dropdown.render(cx, cy, formW, 22f, mouseX, mouseY) }
    }

    fun renderOverlay(mouseX: Float, mouseY: Float) = dropdown.renderOverlay(mouseX, mouseY)
}
