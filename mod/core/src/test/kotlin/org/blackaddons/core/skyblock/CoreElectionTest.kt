package org.blackaddons.core.skyblock

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class CoreElectionTest {

    @Test
    fun `reads the mayor, the perks and the minister`() {
        val mayor = CoreElection.parse(
            """
            {"success":true,"mayor":{"key":"fishing","name":"Marina","perks":[
              {"name":"Fishing XP Buff","description":"§7Gain §3+50☯ §7Fishing Wisdom.","minister":false},
              {"name":"Luck of the Sea 2.0","description":"§7Gain §a+15α §7Sea Creature Chance."}],
             "minister":{"key":"dungeons","name":"Paul","perk":{"name":"EZPZ","description":"§7Gain §a10 §7bonus score.","minister":true}},
             "election":{"year":420,"candidates":[]}}}
            """.trimIndent(),
        )!!
        assertEquals("fishing", mayor.key)
        assertEquals("Marina", mayor.name)
        assertEquals(listOf("Fishing XP Buff", "Luck of the Sea 2.0"), mayor.perks.map { it.name })
        assertEquals("Paul", mayor.minister?.name)
        assertTrue(mayor.hasPerk("ezpz"))
        assertFalse(mayor.hasPerk("Marauder"))
    }

    @Test
    fun `a failed lookup or a broken body holds no mayor`() {
        assertNull(CoreElection.parse("""{"success":false}"""))
        assertNull(CoreElection.parse("""{"mayor":{"name":"Paul"}}"""))
        assertNull(CoreElection.parse("not json"))
    }

    @Test
    fun `missing pieces drop only themselves`() {
        val mayor = CoreElection.parse("""{"mayor":{"key":"paul","perks":[null,{"description":"x"},{"name":"EZPZ"}],"minister":{"key":"x"}}}""")!!
        assertEquals("paul", mayor.name)
        assertEquals(listOf("EZPZ"), mayor.perks.map { it.name })
        assertNull(mayor.minister)
    }
}
