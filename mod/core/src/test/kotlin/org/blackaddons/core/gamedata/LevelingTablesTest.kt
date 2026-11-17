package org.blackaddons.core.gamedata

import com.google.gson.JsonParser
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class LevelingTablesTest {

    private val bundled = LevelingTables.BUNDLED

    private fun parse(json: String) = LevelingTables.parse(JsonParser.parseString(json).asJsonObject)

    private fun longer(table: LongArray, step: Long) = table + (table.last() + step)

    @Test
    fun `the bundled tables are the cumulative curves the profile math expects`() {
        for (table in listOf(bundled.skill, bundled.catacombs, bundled.slayer, bundled.vampire)) {
            assertEquals(0L, table.first())
            assertTrue((1 until table.size).all { table[it] > table[it - 1] })
        }
        assertTrue(bundled.pet.all { it > 0 })
    }

    @Test
    fun `a valid served table replaces the bundled one and leaves the others alone`() {
        val skill = longer(bundled.skill, 1_000_000)
        val tables = parse("""{ "skill": ${skill.joinToString(prefix = "[", postfix = "]")} }""")

        assertArrayEquals(skill, tables.skill)
        assertArrayEquals(bundled.catacombs, tables.catacombs)
        assertArrayEquals(bundled.pet, tables.pet)
    }

    @Test
    fun `a served table that would lose levels or break the curve is ignored`() {
        val shorter = bundled.skill.dropLast(1)
        val decreasing = bundled.catacombs.copyOf().also { it[5] = it[4] - 1 }
        val notFromZero = bundled.slayer.copyOf().also { it[0] = 1 }
        val tables = parse(
            """{
                "skill": ${shorter.joinToString(prefix = "[", postfix = "]")},
                "catacombs": ${decreasing.joinToString(prefix = "[", postfix = "]")},
                "slayer": ${notFromZero.joinToString(prefix = "[", postfix = "]")},
                "vampire": ["0", 20, 75, 240, 840, 2400],
                "pet": ${bundled.pet.copyOf().also { it[0] = 0 }.joinToString(prefix = "[", postfix = "]")}
            }""",
        )

        assertArrayEquals(bundled.skill, tables.skill)
        assertArrayEquals(bundled.catacombs, tables.catacombs)
        assertArrayEquals(bundled.slayer, tables.slayer)
        assertArrayEquals(bundled.vampire, tables.vampire)
        assertArrayEquals(bundled.pet, tables.pet)
    }
}
