package org.blackaddons.api.ui.theme

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.awt.Color

class PaletteTest {

    @Test
    fun `the shipped palette passes its own contrast checks`() {
        assertEquals(emptyList<ContrastFailure>(), ThemeSpec.MIDNIGHT.resolve().contrastFailures())
    }

    @Test
    fun `an override wins over the derived color and leaves the rest alone`() {
        val pinned = Color(10, 200, 90)
        val spec = ThemeSpec.MIDNIGHT.copy(overrides = mapOf(ThemeSlot.SUCCESS to pinned))
        val palette = spec.resolve()

        assertEquals(pinned, palette[ThemeSlot.SUCCESS])
        assertEquals(ThemeSpec.MIDNIGHT.resolve()[ThemeSlot.SURFACE], palette[ThemeSlot.SURFACE])
    }

    @Test
    fun `an override that hides text is reported, naming the pair`() {
        val spec = ThemeSpec.MIDNIGHT.copy(
            overrides = mapOf(ThemeSlot.TEXT_PRIMARY to Color(30, 32, 40)),
        )
        val failures = spec.resolve().contrastFailures()

        assertTrue(failures.any { it.foreground == ThemeSlot.TEXT_PRIMARY && it.background == ThemeSlot.SURFACE })
        assertTrue(failures.all { it.ratio < it.required })
    }

    @Test
    fun `a light base flips the hover lift and keeps the page under its cards`() {
        val spec = ThemeSpec(
            ThemeBases(
                surface = Color(250, 250, 252),
                text = Color(26, 28, 34),
                accent = Color(104, 48, 176),
            )
        )
        val palette = spec.resolve()

        assertTrue(
            brightness(palette[ThemeSlot.SURFACE_HOVER]) < brightness(palette[ThemeSlot.SURFACE]),
            "hover has to move toward the text, which is darker here",
        )
        assertTrue(
            brightness(palette[ThemeSlot.BACKGROUND]) < brightness(palette[ThemeSlot.SURFACE]),
            "the page sits under its cards in a light theme too",
        )
        assertEquals(emptyList<ContrastFailure>(), palette.contrastFailures())
    }

    @Test
    fun `slot ids round trip`() {
        ThemeSlot.entries.forEach { assertEquals(it, ThemeSlot.byId(it.id)) }
        assertEquals(null, ThemeSlot.byId("nothingNamedThis"))
    }

    private fun brightness(color: Color): Float =
        Color.RGBtoHSB(color.red, color.green, color.blue, null)[2]
}
