package org.blackaddons.extras.features.impl.extras.storage

import com.google.gson.JsonNull
import com.google.gson.JsonParser
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class CapturedContainerJsonTest {

    @Test
    fun `a saved page reads back unchanged, empty slots included`() {
        val page = CapturedContainer(
            ContainerType.BACKPACK, 3, "Backpack (Slot #3)", 2, 9,
            listOf(CapturedItem("""{"id":"minecraft:stone","count":64}""", "Stone", 64), null),
            1234L,
        )
        assertEquals(page, CapturedContainer.fromJson(page.toJson()))
    }

    @Test
    fun `a slot without a stack reads as empty and keeps the grid aligned`() {
        val json = JsonParser.parseString(
            """{"type":"ENDER_CHEST","page":1,"title":"Ender Chest","rows":1,"items":[{"name":"lost"},null]}""",
        ).asJsonObject
        val page = CapturedContainer.fromJson(json)
        assertEquals(listOf(null, null), page.items)
        assertEquals(StorageLayout.GRID_COLS, page.cols)
    }

    @Test
    fun `an unknown container type is rejected so the cache skips the entry`() {
        val json = CapturedContainer(ContainerType.ENDER_CHEST, 1, "", 0, 9, emptyList(), 0L).toJson()
        json.addProperty("type", "SHULKER")
        assertThrows<IllegalArgumentException> { CapturedContainer.fromJson(json) }
        json.add("type", JsonNull.INSTANCE)
        assertThrows<IllegalArgumentException> { CapturedContainer.fromJson(json) }
    }

    @Test
    fun `an item without a raw stack is dropped`() {
        assertNull(CapturedItem.fromJson(JsonParser.parseString("""{"name":"x","count":2}""").asJsonObject))
    }
}
