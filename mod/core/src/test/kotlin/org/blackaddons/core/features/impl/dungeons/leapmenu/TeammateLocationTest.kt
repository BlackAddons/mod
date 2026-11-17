package org.blackaddons.core.features.impl.dungeons.leapmenu

import org.blackaddons.api.skyblock.F7SubLocation
import org.blackaddons.api.skyblock.dungeon.DungeonFloor
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class TeammateLocationTest {

    @Test
    fun `floor 7 boss names the phase and the P3 section`() {
        assertEquals("P3 S1", TeammateLocation.boss(DungeonFloor.M7, p3Sim = false, x = 100.0, y = 120.0, z = 80.0))
        assertEquals("P1", TeammateLocation.boss(DungeonFloor.F7, p3Sim = false, x = 60.0, y = 220.0, z = 60.0))
    }

    @Test
    fun `a sublocation replaces the P3 section`() {
        assertEquals("P3 Core", TeammateLocation.boss(DungeonFloor.F7, p3Sim = false, x = 54.5, y = 115.0, z = 52.5))
        assertEquals("P3 EE3", TeammateLocation.boss(DungeonFloor.M7, p3Sim = false, x = 2.5, y = 109.0, z = 101.0))
        assertEquals("P5 Red", TeammateLocation.boss(null, p3Sim = true, x = 27.0, y = 20.0, z = 58.0))
    }

    @Test
    fun `P3Sim reads phases without a floor`() {
        assertEquals("P3 S1", TeammateLocation.boss(null, p3Sim = true, x = 100.0, y = 120.0, z = 80.0))
    }

    @Test
    fun `party pos messages name the sender and the F7 spot`() {
        val ss = F7SubLocation.SS
        val core = F7SubLocation.CORE
        assertEquals("Foo" to ss, TeammateLocation.posMessage("Party > [MVP+] Foo: at ss"))
        assertEquals("Bar_2" to core, TeammateLocation.posMessage("Party > Bar_2: At Core!"))
        assertEquals("Bobamintt" to F7SubLocation.EE3, TeammateLocation.posMessage("Party > Bobamintt: At Early Enter 3!"))
        assertNull(TeammateLocation.posMessage("Party > Foo: going ss soon"))
        assertNull(TeammateLocation.posMessage("Guild > Foo: at ss"))
    }

    @Test
    fun `other floors name the floor and outside the boss room is null`() {
        assertEquals("M5 Boss", TeammateLocation.boss(DungeonFloor.M5, p3Sim = false, x = 0.0, y = 80.0, z = 50.0))
        assertNull(TeammateLocation.boss(DungeonFloor.F7, p3Sim = false, x = 200.0, y = 80.0, z = 200.0))
        assertNull(TeammateLocation.boss(null, p3Sim = false, x = 0.0, y = 80.0, z = 0.0))
    }
}
