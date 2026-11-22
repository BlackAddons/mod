package org.blackaddons.cheat.features.impl.cheats

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class AutoFireVeilTest {

    @Test
    fun `reads the mana cost off the wand's lore`() {
        val lore = listOf("Ability: Fire Veil RIGHT CLICK", "Creates a 3.5 block ring of fire.", "Mana Cost: 1,200", "Cooldown: 5s")
        assertEquals(1200, manaCost(lore))
    }

    @Test
    fun `reads the mana cost through Hypixel's color codes`() {
        assertEquals(300, manaCost(listOf("§8Mana Cost: §3300")))
    }

    @Test
    fun `no mana cost line means no cost`() {
        assertNull(manaCost(listOf("Cooldown: 5s")))
        assertNull(manaCost(emptyList()))
    }
}
