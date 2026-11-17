package org.blackaddons.core.font

import org.blackaddons.api.ui.render.Font
import org.blackaddons.api.ui.render.Render
import org.blackaddons.api.ui.theme.Fonts
import org.blackaddons.api.util.I18n

internal data class InstalledFont(
    val key: String,
    val displayName: String,
    val cacheKeys: List<String>,
) {
    val isBundled: Boolean get() = key == BUNDLED.key

    val label: String
        get() = if (isFile) displayName else I18n.trOr("blackaddons.global_font_screen.font_vanilla", displayName)

    val isFile: Boolean get() = key != VANILLA.key

    fun uiFont(bold: Boolean = false): Font {
        if (key == VANILLA.key) return Fonts.minecraft
        val path = if (isBundled) null else FontCatalog.paths(cacheKeys)?.firstOrNull()
        return path?.let { Font(Render.FILE_SCHEME + it) } ?: if (bold) Fonts.bold else Fonts.regular
    }

    fun hudFont(draft: GlobalFontDraft): Font {
        val base = uiFont(draft.forceBold)
        val size = draft.rasterSize
        return base.copy(
            scale = (size / GlobalFontDraft.DEFAULT.rasterSize).toFloat(),
            shiftX = (draft.shiftX / size).toFloat(),
            shiftY = (draft.shiftY / size).toFloat(),
            bold = draft.forceBold && base.path != Fonts.bold.path,
            italic = draft.forceItalic,
            letterSpacing = (draft.letterSpacing / size).toFloat(),
        )
    }

    companion object {
        val BUNDLED = InstalledFont("bundled:jetbrains-mono", "JetBrains Mono", emptyList())
        const val BUNDLED_RESOURCE = "/assets/blackaddons/font/jetbrainsmono-regular.ttf"

        val VANILLA = InstalledFont("builtin:vanilla", "Vanilla", emptyList())
    }
}
