package org.blackaddons.core.theme

import com.google.gson.JsonObject
import org.blackaddons.api.ui.theme.ThemeSlot
import org.blackaddons.api.ui.theme.contrastFailures
import org.blackaddons.api.util.I18n
import org.blackaddons.api.util.color.toHex
import org.blackaddons.core.config.portable.DiffRow
import org.blackaddons.core.config.portable.DiffValue
import org.blackaddons.core.config.portable.ImportPreview
import org.blackaddons.core.config.portable.PortableKind
import org.blackaddons.core.features.impl.settings.UiTheme
import java.awt.Color

/** A new theme shows a preview, one replacing an existing theme shows a diff first. */
internal object ThemePortable : PortableKind<ThemeDocument>(ThemeCatalog.KIND) {
    private val tr = I18n.scope("blackaddons.theme_screen")

    override val title: String get() = tr("import_title", "Import theme")
    override val noun: String get() = tr("noun", "a theme")

    override fun preview(json: JsonObject): ImportPreview<ThemeDocument> {
        val imported = ThemeJson.read(json, ThemeCatalog.freeId("theme"))
            ?: return ImportPreview.Failed(tr("err_no_bases", "That theme has no colors in it."))

        val palette = imported.spec.resolve()
        palette.contrastFailures().firstOrNull()?.let { pair ->
            return ImportPreview.Failed(tr("err_unreadable", "That theme is unreadable: %s on %s.", pair.foreground.id, pair.background.id))
        }

        // Same name as a built-in: it becomes the player's own copy.
        val existing = ThemeCatalog.byId(imported.id)
        if (existing == null || existing.builtIn) {
            val free = imported.copy(id = if (existing == null) imported.id else ThemeCatalog.freeId(imported.name))
            return ImportPreview.Ready(
                headline = tr("import_new", "New theme \"%s\"", free.name),
                note = tr("import_preview", "Its colors, in the order the interface uses them:"),
                swatches = ThemeSlot.entries.map { palette[it] },
            ) { save(free) }
        }

        val before = existing.spec.resolve()
        val rows = ThemeSlot.entries.mapNotNull { slot ->
            if (before[slot] == palette[slot]) return@mapNotNull null
            DiffRow(slotName(slot), color(before[slot]), color(palette[slot]))
        }
        return ImportPreview.Ready(
            headline = tr("import_replaces", "Replaces your theme \"%s\"", existing.name),
            note = if (rows.isEmpty()) tr("import_same", "Identical to the theme you already have.") else tr("import_changed", "%d colors change:", rows.size),
            rows = rows,
            swatches = if (rows.isEmpty()) ThemeSlot.entries.map { palette[it] } else emptyList(),
        ) { save(imported) }
    }

    override fun afterImport(value: ThemeDocument) = UiTheme.refresh()

    private fun save(document: ThemeDocument): ThemeDocument? = document.takeIf(ThemeCatalog::save)

    private fun color(color: Color) = DiffValue(color.toHex(false), color)

    private fun slotName(slot: ThemeSlot): String = I18n.trOr("blackaddons.theme_slot.${slot.id}", slot.label)
}
