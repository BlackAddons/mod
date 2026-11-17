package org.blackaddons.core.theme

import org.blackaddons.api.ui.theme.ThemeBases
import org.blackaddons.api.ui.theme.ThemeSlot
import org.blackaddons.api.ui.theme.ThemeSpec
import org.blackaddons.api.util.I18n
import java.awt.Color

/** Shipped themes. Overrides keep seasonal accents from clashing with semantic colors. */
internal object BuiltInThemes {

    val all: List<ThemeDocument> get() = listOf(midnight, pumpkin, evergreen)

    val default: ThemeDocument get() = midnight

    private val midnight
        get() = document(
            id = "midnight",
            fallbackName = "Midnight",
            spec = ThemeSpec.MIDNIGHT,
        )

    // Violet surfaces, so hover and selection stay readable under an orange accent.
    private val pumpkin
        get() = document(
            id = "pumpkin",
            fallbackName = "Pumpkin",
            spec = ThemeSpec(
                ThemeBases(
                    surface = Color(0x23, 0x1A, 0x2B),
                    text = Color(0xF2, 0xE9, 0xDF),
                    accent = Color(0xFF, 0x8A, 0x1F),
                ),
                // Orange-red danger was too close to the accent.
                overrides = mapOf(ThemeSlot.DANGER to Color(0xF2, 0x30, 0x64)),
            ),
        )

    // Gold accent, because a green one would hide the green success mark.
    private val evergreen
        get() = document(
            id = "evergreen",
            fallbackName = "Evergreen",
            spec = ThemeSpec(
                ThemeBases(
                    surface = Color(0x17, 0x24, 0x20),
                    text = Color(0xE8, 0xF2, 0xEC),
                    accent = Color(0xF2, 0xC1, 0x4E),
                ),
            ),
        )

    private fun document(id: String, fallbackName: String, spec: ThemeSpec) = ThemeDocument(
        id = id,
        name = I18n.trOr("blackaddons.theme.$id", fallbackName),
        spec = spec,
        builtIn = true,
    )
}
