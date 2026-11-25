package org.blackaddons.core.ui.screen

import org.blackaddons.api.Platform
import org.blackaddons.api.feature.settings.impl.ListSetting
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
import org.blackaddons.api.ui.widget.ScrollableList
import org.blackaddons.api.ui.widget.text.TextInput
import org.blackaddons.api.util.I18n
import org.blackaddons.api.util.containsIgnoreCase
import org.blackaddons.api.util.splitWhitespace

private val tr = I18n.scope("blackaddons.particlepicker")

internal class ParticlePickerScreen(
    private val setting: ListSetting,
    title: String = setting.name,
) : ModalPanel(title) {

    override val panelHeight = 380f


    private val search = widgets.add(TextInput(placeholder = tr("search", "Search particles...")).apply {
        onTextChanged = { list.resetScroll() }
    })
    private val allButton = widgets.add(Button(tr("all", "All")) { setAll(true) })
    private val noneButton = widgets.add(Button(tr("none", "None")) { setAll(false) })
    private val doneButton = widgets.add(Button(I18n.trOr("blackaddons.list.done", "Done")) { UiManager.pop() }.apply { accent = true })

    private val list = widgets.add(ScrollableList<String>().apply {
        rowHeight = 22f
        emptyMessage = tr("empty", "No particles match.")
        drawRow = { id, _, rx, ry, rw -> renderRow(id, rx, ry, rw) }
    })

    private var selection: MutableSet<String> = setting.value.toMutableSet()
    private val allParticleIds: List<String> = Platform.world.registeredParticleIds
    private val checks = AnimationMap<String>(GlassCheck.DURATION_MS, settled = true)

    private fun rows(): List<String> {
        val terms = search.text.lowercase().splitWhitespace()
        if (terms.isEmpty()) return allParticleIds
        return allParticleIds.filter { id ->
            val simple = id.substringAfter(':')
            terms.all { term -> id.containsIgnoreCase(term) || simple.containsIgnoreCase(term) }
        }
    }

    private fun apply(next: Set<String>) {
        selection = next.toMutableSet()
        setting.value = next.toList()
    }

    private fun toggle(id: String) {
        val next = selection.toMutableSet()
        if (!next.add(id)) next.remove(id)
        apply(next)
    }

    private fun setAll(enable: Boolean) {
        apply(if (enable) allParticleIds.toSet() else emptySet())
    }

    override fun renderContent(mouseX: Float, mouseY: Float) {
        renderCounter()

        val topH = 20f
        val btnW = 56f
        val searchW = contentW - (btnW + Theme.PADDING_SMALL) * 2f
        search.render(contentX, contentY, searchW, topH, mouseX, mouseY)
        val allX = contentX + searchW + Theme.PADDING_SMALL
        allButton.render(allX, contentY, btnW, topH, mouseX, mouseY)
        noneButton.render(allX + btnW + Theme.PADDING_SMALL, contentY, btnW, topH, mouseX, mouseY)

        val doneW = 80f
        val doneH = 24f
        val doneX = contentX + contentW - doneW
        val doneY = contentY + contentH - doneH

        val listY = contentY + topH + Theme.PADDING
        val listH = doneY - Theme.PADDING - listY
        list.items = rows()
        list.render(contentX, listY, contentW, listH)

        doneButton.render(doneX, doneY, doneW, doneH, mouseX, mouseY)
    }

    private fun renderCounter() {
        val total = allParticleIds.size
        val count = selection.size
        val counter = I18n.trOr(
            "blackaddons.particlepicker.count",
            "$count / $total selected",
            count, total,
        )
        val counterW = Render.textWidth(counter, Theme.FONT_SMALL, Fonts.regular)
        Render.text(
            counter,
            panelX + panelW - Theme.PADDING_LARGE - counterW,
            panelY + Theme.PADDING_LARGE + 3f,
            TextStyle.SMALL_MUTED,
        )
    }

    private fun renderRow(id: String, x: Float, ry: Float, w: Float) {
        if (widgets.clickable(x, ry, w, list.rowHeight) { toggle(id) }) {
            Render.rect(x, ry, w, list.rowHeight, Theme.SURFACE_HOVER.withAlpha(140), Theme.RADIUS_SMALL)
        }

        val cursor = x + 6f
        val checked = id in selection
        Render.checkbox(cursor, centered(ry, list.rowHeight, 13f), 13f, checks[id, checked])

        val textX = cursor + 13f + 8f
        val simpleName = id.substringAfter(':').replace('_', ' ')
        val label = "$simpleName ($id)"
        val color = if (checked) Theme.TEXT_PRIMARY else Theme.TEXT_SECONDARY
        Render.text(
            label,
            textX,
            Render.centerY(ry, list.rowHeight, Theme.FONT_SMALL, Fonts.medium),
            Theme.FONT_SMALL,
            color,
            Fonts.medium,
        )
    }
}
