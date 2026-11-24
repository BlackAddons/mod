package org.blackaddons.core.ui.screen

import org.blackaddons.api.Platform
import org.blackaddons.api.feature.settings.impl.ListSetting
import org.blackaddons.api.host.ModEntry
import org.blackaddons.api.ui.anim.AnimationMap
import org.blackaddons.api.ui.layout.centered
import org.blackaddons.api.ui.render.Render
import org.blackaddons.api.ui.screen.ModalPanel
import org.blackaddons.api.ui.screen.UiManager
import org.blackaddons.api.ui.theme.Fonts
import org.blackaddons.api.ui.theme.GlassCheck
import org.blackaddons.api.ui.theme.TextStyle
import org.blackaddons.api.ui.theme.Theme
import org.blackaddons.api.ui.theme.Theme.withAlpha
import org.blackaddons.api.ui.widget.Button
import org.blackaddons.api.ui.widget.Chevron
import org.blackaddons.api.ui.widget.ScrollableList
import org.blackaddons.api.ui.widget.text.TextInput
import org.blackaddons.api.util.I18n
import org.blackaddons.api.util.containsIgnoreCase
import org.blackaddons.api.util.splitWhitespace
import org.blackaddons.core.mods.ModTree

private val tr = I18n.scope("blackaddons.modpicker")

/**
 * Picks allowed mods from the loaded ones (see [ModTree]). The setting only changes on a toggle, so opening the screen
 * never messes with a hand-tuned list.
 */
internal class ModPickerScreen(
    private val setting: ListSetting,
    title: String = setting.name,
) : ModalPanel(title) {

    override val panelHeight = 380f

    private val tree = ModTree(Platform.mods.loaded)
    private val search = widgets.add(TextInput(placeholder = tr("search", "Search mods...")).apply {
        onTextChanged = { list.resetScroll() }
    })
    private val list = widgets.add(ScrollableList<Row>().apply {
        rowHeight = 20f
        rowGap = 0f
        emptyMessage = tr("empty", "No mods match.")
        drawRow = { row, _, rx, ry, rw -> renderRow(row, rx, ry, rw) }
    })
    private val allButton = widgets.add(Button(tr("all", "All")) { setAll(enable = true) })
    private val noneButton = widgets.add(Button(tr("none", "None")) { setAll(enable = false) })
    private val doneButton = widgets.add(Button(I18n.trOr("blackaddons.list.done", "Done")) { UiManager.pop() })

    private var selection: Set<String> = setting.value.toSet()
    private val collapsed = HashSet<String>()
    private val checks = AnimationMap<Row>(GlassCheck.DURATION_MS, settled = true)

    private var listX = 0f

    private sealed interface Row {
        val entry: ModEntry

        data class Group(override val entry: ModEntry, val members: List<ModEntry>) : Row
        data class Mod(override val entry: ModEntry, val nested: Boolean) : Row
    }

    private fun rows(): List<Row> {
        val terms = search.text.lowercase().splitWhitespace()
        val searching = terms.isNotEmpty()

        fun matches(mod: ModEntry): Boolean = terms.all { term ->
            mod.name.containsIgnoreCase(term) || mod.id.containsIgnoreCase(term)
        }

        val out = mutableListOf<Row>()
        for (root in tree.roots) {
            val children = tree.children(root.id)
            if (children.isEmpty()) {
                if (!searching || matches(root)) out += Row.Mod(root, nested = false)
                continue
            }

            val hits = children.filter { !searching || matches(it) }
            if (searching && hits.isEmpty() && !matches(root)) continue

            out += Row.Group(root, children)
            if (searching || root.id !in collapsed) {
                out += Row.Mod(root, nested = true)
                hits.forEach { out += Row.Mod(it, nested = true) }
            }
        }
        return out
    }

    private fun apply(next: Set<String>) {
        selection = next
        setting.value = next.toList()
    }

    private fun toggle(mod: ModEntry) =
        apply(if (mod.id in selection) tree.withDisabled(selection, mod.id) else tree.withEnabled(selection, mod.id))

    /** All on, unless already all on, then all off. */
    private fun toggleGroup(group: Row.Group) {
        val members = listOf(group.entry) + group.members
        val enable = !members.all { it.id in selection }
        var next = selection
        for (member in members) {
            next = if (enable) tree.withEnabled(next, member.id) else tree.withDisabled(next, member.id)
        }
        apply(next)
    }

    private fun setAll(enable: Boolean) {
        var next = selection
        for (root in tree.roots) {
            for (mod in listOf(root) + tree.children(root.id)) {
                next = if (enable) tree.withEnabled(next, mod.id) else tree.withDisabled(next, mod.id)
            }
        }
        apply(next)
    }

    override fun renderContent(mouseX: Float, mouseY: Float) {
        renderCounter()

        val searchW = contentW - (BUTTON_W + Theme.PADDING_SMALL) * 2f
        search.render(contentX, contentY, searchW, SEARCH_H, mouseX, mouseY)
        val allX = contentX + searchW + Theme.PADDING_SMALL
        allButton.render(allX, contentY, BUTTON_W, BUTTON_H, mouseX, mouseY)
        noneButton.render(allX + BUTTON_W + Theme.PADDING_SMALL, contentY, BUTTON_W, BUTTON_H, mouseX, mouseY)

        val doneX = contentX + contentW - DONE_W
        val doneY = contentY + contentH - DONE_H

        listX = contentX
        val listY = contentY + SEARCH_H + Theme.PADDING
        val listH = doneY - Theme.PADDING - listY
        list.items = rows()
        list.render(listX, listY, contentW, listH)

        doneButton.render(doneX, doneY, DONE_W, DONE_H, mouseX, mouseY)
    }

    private fun renderCounter() {
        val visibleCount = tree.roots.sumOf { 1 + tree.children(it.id).size }
        val selectedCount = tree.roots.sumOf { root ->
            (listOf(root) + tree.children(root.id)).count { it.id in selection }
        }
        val counter = I18n.trOr(
            "blackaddons.modpicker.count",
            "$selectedCount / $visibleCount allowed",
            selectedCount, visibleCount,
        )
        val counterW = Render.textWidth(counter, Theme.FONT_SMALL, Fonts.regular)
        Render.text(
            counter,
            panelX + panelW - Theme.PADDING_LARGE - counterW,
            panelY + Theme.PADDING_LARGE + 3f,
            TextStyle.SMALL_MUTED,
        )
    }

    private fun renderRow(row: Row, x: Float, ry: Float, w: Float) {
        val hovered = widgets.clickable(x, ry, w, list.rowHeight) {
            when (row) {
                is Row.Group -> toggleGroup(row)
                is Row.Mod -> toggle(row.entry)
            }
        }
        // Only the chevron folds. The rest of the row toggles the group.
        if (row is Row.Group) {
            widgets.clickable(x, ry, listX + 4f + 6f + INDENT - x, list.rowHeight) {
                if (!collapsed.remove(row.entry.id)) collapsed += row.entry.id
            }
        }
        if (hovered) {
            Render.rect(x, ry, w, list.rowHeight, Theme.SURFACE_HOVER.withAlpha(140), Theme.RADIUS_SMALL)
        }

        val nested = row is Row.Mod && row.nested
        var cursor = x + 6f
        if (row is Row.Group) {
            val expanded = search.text.isNotBlank() || row.entry.id !in collapsed
            Chevron.draw(cursor + 3f, ry + list.rowHeight / 2f, 3.5f, 1.4f, if (expanded) 1f else 0f)
            cursor += INDENT
        } else if (nested) {
            cursor += INDENT
        }

        val checked = when (row) {
            is Row.Group -> (listOf(row.entry) + row.members).all { it.id in selection }
            is Row.Mod -> row.entry.id in selection
        }
        Render.checkbox(cursor, centered(ry, list.rowHeight, BOX_SIZE), BOX_SIZE, checks[row, checked])
        cursor += BOX_SIZE + 6f

        val bold = row is Row.Group
        val label = if (row is Row.Group) row.entry.name else "${row.entry.name}  ${row.entry.id}"
        val font = if (bold) Fonts.medium else Fonts.regular
        val color = if (checked) Theme.TEXT_PRIMARY else Theme.TEXT_SECONDARY
        Render.text(label, cursor, Render.centerY(ry, list.rowHeight, Theme.FONT_SMALL, font), Theme.FONT_SMALL, color, font)
    }

    private companion object {
        const val SEARCH_H = 20f
        const val BUTTON_W = 56f
        const val BUTTON_H = 20f
        const val DONE_W = 80f
        const val DONE_H = 24f
        const val BOX_SIZE = 13f
        const val INDENT = 14f
    }
}
