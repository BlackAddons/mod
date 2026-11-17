package org.blackaddons.core.ui.screen.cosmetic

import org.blackaddons.api.ui.render.Render
import org.blackaddons.api.ui.screen.ModalPanel
import org.blackaddons.api.ui.screen.UiManager
import org.blackaddons.api.ui.theme.Fonts
import org.blackaddons.api.ui.theme.TextStyle
import org.blackaddons.api.ui.theme.Theme
import org.blackaddons.api.ui.widget.ScrollableList
import org.blackaddons.api.ui.widget.text.TextInput
import org.blackaddons.api.util.I18n
import org.blackaddons.core.font.FontCatalog
import org.blackaddons.core.font.FontCatalogEntry
import org.blackaddons.core.font.FontCatalogState

private val tr = I18n.scope("blackaddons.cosmetic_editor")

/** Fontsource, the same catalog the global font picks from, minus the symbol fonts no name can be read in. */
internal class NickFontPicker(private val onPick: (FontCatalogEntry) -> Unit) :
    ModalPanel(tr("font_pick_title", "Name font")) {

    override val panelWidth: Float get() = fitWidth(360f, margin = 24f)
    override val panelHeight: Float get() = fitHeight(420f, margin = 24f)

    private val search = widgets.add(TextInput(placeholder = tr("font_search", "Search fonts...")))
    private val list = widgets.add(ScrollableList<FontCatalogEntry>().apply {
        rowHeight = ROW_H
        rowGap = 1f
        drawRow = { entry, _, x, y, w -> drawRow(entry, x, y, w) }
    })
    private var lastSearch = ""

    override fun onShow() = FontCatalog.load()

    override fun renderContent(mouseX: Float, mouseY: Float) {
        if (search.text != lastSearch) {
            lastSearch = search.text
            list.resetScroll()
        }
        val term = search.text.trim()
        list.items = (FontCatalog.state as? FontCatalogState.Ready)?.fonts.orEmpty()
            .filter { it.category != SYMBOL_CATEGORY && (term.isEmpty() || it.family.contains(term, ignoreCase = true)) }
        list.emptyMessage = when (val state = FontCatalog.state) {
            FontCatalogState.Loading -> tr("font_loading", "Loading Fontsource...")
            is FontCatalogState.Failed -> state.message
            is FontCatalogState.Ready -> tr("font_none", "No matching fonts.")
        }
        search.render(contentX, contentY, contentW, SEARCH_H, mouseX, mouseY)
        val listY = contentY + SEARCH_H + Theme.PADDING
        list.render(contentX, listY, contentW, contentY + contentH - listY)
    }

    private fun drawRow(entry: FontCatalogEntry, x: Float, y: Float, w: Float) {
        val hovered = widgets.clickable(x, y, w, ROW_H) {
            onPick(entry)
            UiManager.pop()
        }
        if (hovered) Render.rect(x + 2f, y, w - 4f, ROW_H, Theme.SURFACE_HOVER, Theme.RADIUS_SMALL)
        val tagW = Render.textWidth(entry.category, Theme.FONT_SMALL, Fonts.regular)
        Render.text(entry.family, x + 8f, Render.centerY(y, ROW_H, TextStyle.SMALL_PRIMARY), TextStyle.SMALL_PRIMARY, maxWidth = w - tagW - 24f)
        Render.text(entry.category, x + w - tagW - 8f, Render.centerY(y, ROW_H, TextStyle.SMALL_MUTED), TextStyle.SMALL_MUTED)
    }

    private companion object {
        const val ROW_H = 22f
        const val SEARCH_H = 22f
        // The backend refuses these too, so offering them would just lead to an error.
        const val SYMBOL_CATEGORY = "icons"
    }
}
