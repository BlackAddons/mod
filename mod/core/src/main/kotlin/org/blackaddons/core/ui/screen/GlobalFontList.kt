package org.blackaddons.core.ui.screen

import org.blackaddons.api.ui.theme.Theme
import org.blackaddons.api.ui.widget.ProgressBar
import org.blackaddons.core.font.FontCatalog
import org.blackaddons.core.font.FontCatalogState
import org.blackaddons.core.font.InstalledFont
import org.blackaddons.core.ui.screen.GlobalFontScreen.Companion.PROGRESS_H
import org.blackaddons.core.ui.screen.GlobalFontScreen.FontRow

/* GlobalFontScreen's font list: search matches, rows and the library status line. */

internal fun GlobalFontScreen.matchingFonts(): List<FontRow> {
    val installed = (listOf(InstalledFont.VANILLA, InstalledFont.BUNDLED) + FontCatalog.installed())
        .distinctBy(InstalledFont::key)
        .map { FontRow(installed = it) }
    val installedKeys = installed.mapTo(HashSet()) { it.key }
    val catalog = (FontCatalog.state as? FontCatalogState.Ready)?.fonts.orEmpty()
        .filterNot { "${FontCatalog.FONTSOURCE_KEY_PREFIX}${it.id}" in installedKeys }
        .map { FontRow(catalog = it) }
    val term = search.text.trim()
    return (installed + catalog).filter { term.isEmpty() || it.name.contains(term, ignoreCase = true) }
}

internal fun GlobalFontScreen.drawLibraryStatus(x: Float, y: Float, w: Float, rows: List<FontRow>) {
    val state = FontCatalog.state
    libraryStatusText.text = when (state) {
        FontCatalogState.Loading -> tr("library_loading", "Loading font library...")
        is FontCatalogState.Failed -> tr("library_unavailable", "Font library unavailable")
        is FontCatalogState.Ready -> {
            val files = rows.filter { it.installed?.isFile != false }
            tr("library_count", "%d fonts · %d installed", files.size, files.count { it.installed != null })
        }
    }
    libraryStatusText.color = if (state is FontCatalogState.Failed) Theme.DANGER else Theme.TEXT_MUTED
    libraryStatusText.render(x + 2f, y)
    if (state == FontCatalogState.Loading) ProgressBar.drawIndeterminate(x, y + 12f, w, PROGRESS_H)
}
