package org.blackaddons.core.theme

import com.google.gson.JsonObject
import org.blackaddons.api.ui.theme.ThemeBases
import org.blackaddons.api.ui.theme.ThemeSlot
import org.blackaddons.api.ui.theme.ThemeSpec
import org.blackaddons.api.ui.theme.contrastFailures
import org.blackaddons.api.util.io.Portable
import org.blackaddons.api.util.io.PortableRead
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.awt.Color

class ThemeCatalogTest {

    @Test
    fun `every theme we ship is readable`() {
        BuiltInThemes.all.forEach { theme ->
            assertEquals(
                emptyList<Any>(),
                theme.spec.resolve().contrastFailures(),
                "built-in theme '${theme.id}' fails its own validator",
            )
        }
    }

    @Test
    fun `the festive themes keep danger and success away from their accent`() {
        val pumpkin = BuiltInThemes.all.first { it.id == "pumpkin" }.spec.resolve()
        val evergreen = BuiltInThemes.all.first { it.id == "evergreen" }.spec.resolve()

        // Different at a glance means a hue apart.
        assertHueApart(pumpkin[ThemeSlot.ACCENT], pumpkin[ThemeSlot.DANGER], 40f)
        assertHueApart(evergreen[ThemeSlot.ACCENT], evergreen[ThemeSlot.SUCCESS], 40f)
    }

    @Test
    fun `a theme survives disk json`() {
        val original = ThemeDocument(
            id = "mine",
            name = "Mine",
            spec = ThemeSpec(
                ThemeBases(Color(30, 32, 40), Color(228, 235, 246), Color(120, 190, 255)),
                overrides = mapOf(ThemeSlot.DANGER to Color(242, 51, 77)),
            ),
        )

        val read = ThemeJson.read(ThemeJson.write(original), fallbackId = "ignored")

        assertEquals(original, read)
    }

    @Test
    fun `an override for a slot this version does not know is dropped, not fatal`() {
        val json = ThemeJson.write(BuiltInThemes.default).apply {
            add("overrides", JsonObject().apply { addProperty("glitter", "#FFFFFFFF") })
        }

        val read = ThemeJson.read(json, fallbackId = "midnight")

        assertEquals(emptyMap<ThemeSlot, Color>(), requireNotNull(read).spec.overrides)
    }

    @Test
    fun `a file without bases is not a theme`() {
        val json = JsonObject().apply { addProperty("name", "Nope") }

        assertNull(ThemeJson.read(json, fallbackId = "nope"))
    }

    @Test
    fun `an exported theme announces itself as one`() {
        val read = Portable.decode(ThemeCatalog.export(BuiltInThemes.default))

        val document = assertInstanceOf(PortableRead.Document::class.java, read)
        assertEquals(ThemeCatalog.KIND, document.kind)
        assertEquals(BuiltInThemes.default.spec, requireNotNull(ThemeJson.read(document.json, "x")).spec)
    }

    @Test
    fun `a theme the catalog would refuse to read is refused on the way in`() {
        // Unreadable palettes get dropped on read. Writing one used to report "saved", and then it was just gone.
        val invisible = ThemeDocument(
            id = "invisible",
            name = "Invisible",
            spec = ThemeSpec(
                ThemeBases(Color(30, 32, 40), Color(230, 237, 248), Color(180, 109, 242)),
                overrides = mapOf(ThemeSlot.TEXT_PRIMARY to Color(31, 33, 41)),
            ),
        )

        assertTrue(invisible.spec.resolve().contrastFailures().isNotEmpty(), "the fixture has to be broken")
        assertFalse(ThemeCatalog.save(invisible))
    }

    @Test
    fun `an id is slugged, and never lands on a theme that already exists`() {
        assertEquals("my-warm-theme", ThemeCatalog.freeId("My Warm Theme!"))
        assertEquals("theme", ThemeCatalog.freeId("***"))
        assertEquals("midnight-2", ThemeCatalog.freeId("Midnight"))
    }

    private fun assertHueApart(a: Color, b: Color, degrees: Float) {
        val ha = Color.RGBtoHSB(a.red, a.green, a.blue, null)[0] * 360f
        val hb = Color.RGBtoHSB(b.red, b.green, b.blue, null)[0] * 360f
        val gap = kotlin.math.abs(ha - hb).let { minOf(it, 360f - it) }
        assertTrue(gap >= degrees, "hues $ha and $hb are only $gap apart")
    }
}
