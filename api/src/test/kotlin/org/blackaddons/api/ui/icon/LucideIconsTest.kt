package org.blackaddons.api.ui.icon

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.nio.file.Path
import kotlin.io.path.readText

class LucideIconsTest {

    /** Read from source, so a new icon is covered the moment it is declared. */
    @Test
    fun `every icon declares SVG path data`() {
        val source = Path.of("src/main/kotlin/org/blackaddons/api/ui/icon/LucideIcons.kt").readText()
        val icons = ICON.findAll(source).toList()
        assertTrue(icons.size > 1, "no icons found - the scan no longer matches how LucideIcons declares them")
        assertEquals(source.split("VectorIcon(").size - 1, icons.size, "an icon is declared in a shape the scan does not read")
        icons.forEach { icon ->
            val (name, stroke, fill) = icon.destructured
            assertTrue(PATH.matches(stroke), "$name stroke is not SVG path data")
            assertTrue(fill.isEmpty() || PATH.matches(fill), "$name fill is not SVG path data")
        }
    }

    private companion object {
        val ICON = Regex("""val (\w+) = VectorIcon\("([^"]*)"(?:, "([^"]*)")?""")
        val PATH = Regex("""[Mm][MmLlHhVvCcSsQqTtAaZz0-9.,\s-]*""")
    }
}
