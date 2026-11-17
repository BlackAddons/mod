package org.blackaddons.core.font

import com.google.gson.JsonObject
import org.blackaddons.api.util.I18n
import org.blackaddons.api.util.color.toHex
import org.blackaddons.api.util.formatTrimmed
import org.blackaddons.api.util.io.Portable
import org.blackaddons.core.config.portable.DiffRow
import org.blackaddons.core.config.portable.DiffValue
import org.blackaddons.core.config.portable.FeatureSettingsKind
import org.blackaddons.core.config.portable.ImportPreview
import org.blackaddons.core.config.portable.PortableImports
import org.blackaddons.core.config.portable.PortableKind
import org.blackaddons.core.features.impl.visual.FontSettings
import java.awt.Color

internal object FontPortable : PortableKind<Unit>("font") {
    private val tr = PortableImports.tr.scope("font")
    private val screenTr = I18n.scope("blackaddons.global_font_screen")

    override val title: String get() = tr("title", "Import fonts")
    override val noun: String get() = tr("noun", "fonts")

    fun export(): String = Portable.encode(kind, GlobalFontStore.writeAll(FontSettings.document))

    override fun preview(json: JsonObject): ImportPreview<Unit> {
        val read = GlobalFontStore.readAll(json)
        val installed = FontCatalog.installed()
        read.fonts.firstOrNull { font ->
            font.isFile && !font.isBundled && installed.none { it.key == font.key } &&
                !font.key.startsWith(FontCatalog.FONTSOURCE_KEY_PREFIX)
        }?.let { return ImportPreview.Failed(tr("err_local", "%s was imported from a file on their computer, so it cannot be shared. Ask them for the file.", it.displayName)) }
        val shared = read.fonts.fold(read) { document, font ->
            installed.firstOrNull { it.key == font.key }?.let { document.replacing(font.key, it) } ?: document
        }

        val current = FontSettings.document
        val rows = buildList {
            target(screenTr("tab_minecraft", "Minecraft"), current.minecraft, shared.minecraft)
            target(screenTr("tab_hud", "HUD"), current.hud, shared.hud)
            target(screenTr("tab_world", "World"), current.world, shared.world)
        }
        val downloads = shared.fonts
            .filter { it.key.startsWith(FontCatalog.FONTSOURCE_KEY_PREFIX) && FontCatalog.paths(it.cacheKeys) == null }
            .distinctBy { it.key }
        return ImportPreview.Ready(
            headline = tr("headline", "Shared fonts"),
            note = when {
                rows.isEmpty() -> tr("same", "These are the fonts you already have.")
                downloads.isNotEmpty() -> tr("downloads", "%s is downloaded from Fontsource first. %d changes:", downloads.joinToString { it.displayName }, rows.size)
                else -> tr("changed", "%d changes:", rows.size)
            },
            rows = rows,
            canApply = rows.isNotEmpty(),
        ) { FontSettings.applyShared(shared) }
    }

    private fun MutableList<DiffRow>.target(target: String, before: GlobalFontDocument, after: GlobalFontDocument) {
        if (before.font.key != after.font.key) {
            add(DiffRow("$target / ${tr("font", "Font")}", DiffValue(before.font.label), DiffValue(after.font.label)))
        }
        fields.forEach { (label, read) ->
            val was = read(before.draft)
            val now = read(after.draft)
            if (was != now) add(DiffRow("$target / ${label()}", was, now))
        }
    }

    private fun number(value: Double) = DiffValue(value.formatTrimmed())

    private fun color(value: Color) = DiffValue(value.toHex(true), value)

    private val fields: List<Pair<() -> String, (GlobalFontDraft) -> DiffValue>> = listOf(
        { screenTr("raster_size", "Raster size") } to { d -> number(d.rasterSize) },
        { screenTr("oversampling_2d", "2D oversampling") } to { d -> number(d.oversample2d) },
        { screenTr("oversampling_world", "World oversampling") } to { d -> number(d.oversampleWorld) },
        { screenTr("letter_spacing", "Letter spacing") } to { d -> number(d.letterSpacing) },
        { screenTr("glyph_x_offset", "Glyph X offset") } to { d -> number(d.shiftX) },
        { screenTr("glyph_y_offset", "Glyph Y offset") } to { d -> number(d.shiftY) },
        { screenTr("bold", "Bold") } to { d -> FeatureSettingsKind.onOff(d.forceBold) },
        { screenTr("italic", "Italic") } to { d -> FeatureSettingsKind.onOff(d.forceItalic) },
        { screenTr("shadow", "Shadow") } to { d -> FeatureSettingsKind.onOff(d.shadowEnabled) },
        { screenTr("shadow_color", "Shadow color") } to { d -> color(d.shadowColor) },
        { screenTr("shadow_x", "Shadow X") } to { d -> number(d.shadowX) },
        { screenTr("shadow_y", "Shadow Y") } to { d -> number(d.shadowY) },
        { screenTr("outline", "Outline") } to { d -> FeatureSettingsKind.onOff(d.outlineEnabled) },
        { screenTr("outline_color", "Outline color") } to { d -> color(d.outlineColor) },
        { screenTr("outline_width", "Outline width") } to { d -> number(d.outlineWidth) },
    )
}
