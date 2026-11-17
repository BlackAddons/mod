package org.blackaddons.core.gamedata

import com.google.gson.JsonParser
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class DungeonScoringTest {

    @Test
    fun `the compiled table matches what the backend ships as its own fallback`() {
        val table = DungeonScoring.COMPILED

        assertEquals(1.0, table.rulesFor("F7").requiredSecretPercentage)
        assertEquals(840, table.rulesFor("F7").timeLimitSeconds)
        assertEquals(480, table.rulesFor("M3").timeLimitSeconds)
        assertEquals(0.85, table.rulesFor("F6").requiredSecretPercentage)
        assertEquals(0.7, table.rulesFor("Entrance").scoreMultiplier)
        assertEquals(320, table.cap)
        assertEquals(6, table.bonus.mimicFloor)
        assertEquals(2, table.bonus.mimic)
    }

    @Test
    fun `a floor is found whatever case it is written in`() {
        assertEquals(840, DungeonScoring.COMPILED.rulesFor("f7").timeLimitSeconds)
        assertEquals(600, DungeonScoring.COMPILED.rulesFor("Fifteen").timeLimitSeconds)
        assertEquals(600, DungeonScoring.COMPILED.rulesFor(null).timeLimitSeconds)
    }

    @Test
    fun `the mayor's perk comes out of the table along with its share of the ceiling`() {
        val board = DungeonScoring.COMPILED.withoutMayorPerk()

        assertEquals(0, board.bonus.paul)
        assertEquals(310, board.cap)
        // The one bonus this board refuses.
        assertEquals(DungeonScoring.COMPILED.bonus.mimic, board.bonus.mimic)
    }

    @Test
    fun `a served document is read over the compiled table, floor by floor`() {
        val table = DungeonScoring.parse(
            document("""{"floors":{"F7":{"number":7,"requiredSecretPercentage":0.9,"timeLimitSeconds":900}}}"""),
        )

        assertEquals(0.9, table.rulesFor("F7").requiredSecretPercentage)
        assertEquals(900, table.rulesFor("F7").timeLimitSeconds)
        // Naming one floor must not delete the other fourteen.
        assertEquals(480, table.rulesFor("M3").timeLimitSeconds)
        assertEquals(10, table.bonus.paul)
    }

    /** Fractions only: a whole percent would score runs 100x too low, so the compiled value stays. */
    @Test
    fun `a required percentage above one is read as the mistake it is`() {
        val table = DungeonScoring.parse(
            document("""{"floors":{"F7":{"number":7,"requiredSecretPercentage":70,"timeLimitSeconds":840}}}"""),
        )

        assertEquals(1.0, table.rulesFor("F7").requiredSecretPercentage)
    }

    @Test
    fun `bonus rules are read whole, and a missing one keeps the compiled number`() {
        val table = DungeonScoring.parse(document("""{"bonus":{"mimic":3,"mimicFloor":5}}"""))

        assertEquals(3, table.bonus.mimic)
        assertEquals(5, table.bonus.mimicFloor)
        assertEquals(5, table.bonus.maxCrypts)
        assertEquals(10, table.bonus.paul)
    }

    private fun document(json: String) = JsonParser.parseString(json).asJsonObject
}
