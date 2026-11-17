package org.blackaddons.core.gamedata

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Checks the uploaded `locations` document (`src/test/resources/gamedata/locations.json`) against the compiled table.
 * The served table merges over the compiled one, so a missing entry doesn't show at runtime. Add islands to both in one
 * commit.
 */
class LocationsDocumentTest {

    private val document: JsonObject = JsonParser.parseString(
        checkNotNull(javaClass.getResource("/gamedata/locations.json")) {
            "the locations fixture is missing"
        }.readText(),
    ).asJsonObject

    private val areas = SkyblockAreas.parse(document)

    @Test
    fun `the document names every island the mod ships with`() {
        for ((slug, name) in SkyblockAreas.COMPILED) {
            assertEquals(name, areas[slug], "the document disagrees about '$slug'")
        }
    }

    @Test
    fun `the document adds nothing the mod doesn't already know`() {
        // A fixture rule, not a served-doc rule: an entry missing from COMPILED gives offline players the tidied id.
        for (slug in areas.keys) {
            assertTrue(slug in SkyblockAreas.COMPILED, "'$slug' is in the document but not compiled in")
        }
    }

    @Test
    fun `the document actually carries a table`() {
        // Checks the check: an empty `areas` block falls back to COMPILED and everything passes.
        assertTrue(document.getAsJsonObject("areas").size() > 0)
    }
}
