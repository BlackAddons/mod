package org.blackaddons.core.features.impl.chat

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class QuickJoinTest {

    @Test
    fun `every floor, master floor and kuudra tier has a command`() {
        val expected = listOf("f0") + (1..7).flatMap { listOf("f$it", "m$it") } + (1..5).map { "t$it" }
        assertEquals(expected.toSet(), quickJoinInstances.keys)
    }

    @Test
    fun `commands map to hypixel instance ids`() {
        assertEquals("catacombs_entrance", quickJoinInstances["f0"])
        assertEquals("catacombs_floor_seven", quickJoinInstances["f7"])
        assertEquals("master_catacombs_floor_one", quickJoinInstances["m1"])
        assertEquals("kuudra_infernal", quickJoinInstances["t5"])
    }
}
