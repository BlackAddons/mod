package org.blackaddons.core.gamedata

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Test

/**
 * Island names change, so they're served and merged over the compiled table ([SkyblockAreas.parse] explains why dungeon
 * geometry replaces instead).
 */
class SkyblockAreasTest {

    private fun parse(json: String): Map<String, String> =
        SkyblockAreas.parse(JsonParser.parseString(json).asJsonObject)

    @Test
    fun `a served name wins over the compiled one`() {
        val areas = parse("""{ "areas": { "hub": "The Hub" } }""")
        assertEquals("The Hub", areas["hub"])
    }

    @Test
    fun `a stale document cannot mask islands a newer build knows`() {
        // An old document shouldn't change a newer island back to its tidied id.
        val areas = parse("""{ "areas": { "hub": "Hub" } }""")
        assertEquals("Spider's Den", areas["combat_1"])
        assertEquals(SkyblockAreas.COMPILED.size, areas.size)
    }

    @Test
    fun `a new island arrives without a mod release`() {
        val areas = parse("""{ "areas": { "foraging_2": "The Thicket" } }""")
        assertEquals("The Thicket", areas["foraging_2"])
    }

    @Test
    fun `a blank or non-string name is dropped, not taken`() {
        // An empty name looks worse than the tidied id.
        val areas = parse("""{ "areas": { "hub": "  ", "combat_1": 7, "dungeon": null } }""")
        assertEquals("Hub", areas["hub"])
        assertEquals("Spider's Den", areas["combat_1"])
        assertEquals("Catacombs", areas["dungeon"])
    }

    @Test
    fun `a document with no areas block keeps the compiled table exactly`() {
        assertSame(SkyblockAreas.COMPILED, parse("""{ "dungeons": {} }"""))
        assertSame(SkyblockAreas.COMPILED, parse("""{ "areas": {} }"""))
        assertSame(SkyblockAreas.COMPILED, SkyblockAreas.parse(JsonObject()))
    }

    @Test
    fun `an id nobody has named is tidied up rather than dropped`() {
        assertEquals("Brand New Zone", SkyblockAreas.prettify("brand_new_zone"))
        assertEquals("Galatea", SkyblockAreas.prettify("galatea"))
    }

    @Test
    fun `the ids that lie are exactly why this is a table`() {
        // Can't be derived, so it's in the table.
        assertEquals("Private Island", SkyblockAreas.COMPILED["dynamic"])
        assertEquals("Spider's Den", SkyblockAreas.COMPILED["combat_1"])
        assertEquals("Kuudra", SkyblockAreas.COMPILED["instanced"])
    }
}
