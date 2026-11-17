package org.blackaddons.api.skyblock.player

import org.blackaddons.api.world.ItemInfo
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class InvincibilityAndPetTest {

    @Test
    fun `proc lines name their item, the starred Bonzo's Mask included`() {
        assertEquals(InvincibilityItem.SPIRIT, InvincibilityItem.fromMessage("Second Wind Activated! Your Spirit Mask saved your life!"))
        assertEquals(InvincibilityItem.BONZO, InvincibilityItem.fromMessage("Your Bonzo's Mask saved your life!"))
        assertEquals(InvincibilityItem.BONZO, InvincibilityItem.fromMessage("Your ⚚ Bonzo's Mask saved your life!"))
        assertEquals(InvincibilityItem.PHOENIX, InvincibilityItem.fromMessage("Your Phoenix Pet saved you from certain death!"))
        assertNull(InvincibilityItem.fromMessage("Party > someone: Your Bonzo's Mask saved your life!"))
    }

    @Test
    fun `masks match by Skyblock id, whatever the name says`() {
        val bonzo = InvincibilityItem.BONZO.mask!!
        assertTrue(bonzo(ItemInfo("minecraft:player_head", "Renamed", emptyList(), 1, skyblockId = "STARRED_BONZO_MASK")))
        assertFalse(bonzo(ItemInfo("minecraft:player_head", "Bonzo's Mask", emptyList(), 1, skyblockId = "SPIRIT_MASK")))
    }

    @Test
    fun `a pet item gives its name, level and uuid, anything else gives null`() {
        val item = ItemInfo(
            "minecraft:player_head", "§7[Lvl 100] §6Golden Dragon ✦", emptyList(), 1,
            skyblockUuid = "abc", petInfo = """{"type":"GOLDEN_DRAGON","tier":"LEGENDARY"}""",
        )
        assertEquals(Pet("Golden Dragon", 100, "abc"), Pet.of(item))
        val fromType = Pet.of(item.copy(name = "Something odd", skyblockUuid = null))
        assertEquals(Pet("Golden Dragon", null, null), fromType)
        assertNull(Pet.of(item.copy(petInfo = null)))
        assertNull(Pet.of(item.copy(petInfo = "not json")))
    }

    @Test
    fun `pets compare by uuid when both know one, else by name`() {
        assertTrue(Pet("Phoenix", uuid = "a").sameAs(Pet("phoenix")))
        assertFalse(Pet("Phoenix", uuid = "a").sameAs(Pet("Phoenix", uuid = "b")))
        assertTrue(Pet("Phoenix ✦").matches("phoenix"))
    }
}
