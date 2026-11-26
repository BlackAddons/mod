package org.blackaddons.core.features.impl.ui.inventorybuttons

import com.google.gson.JsonParser
import org.blackaddons.api.gui.GuiBox
import org.blackaddons.api.util.io.Portable
import org.blackaddons.api.util.io.PortableRead
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class InventoryButtonEditingTest {

    @Test
    fun `a box near another button's edge lines up with it`() {
        val other = GuiBox(100, 50, 18, 18)
        // Left edge 2px right of the other's left edge, top 2px under the other's bottom edge.
        val snapped = snapToNeighbors(GuiBox(102, 70, 18, 18), listOf(other), reach = 3)

        assertEquals(Snapped(100, 68, guideX = 100, guideY = 68), snapped)
    }

    @Test
    fun `centers snap to centers`() {
        val wide = GuiBox(0, 0, 40, 10)
        val snapped = snapToNeighbors(GuiBox(12, 100, 18, 18), listOf(wide), reach = 3)

        assertEquals(11, snapped.x)
        assertEquals(20, snapped.guideX)
        assertNull(snapped.guideY)
    }

    @Test
    fun `nothing within reach leaves the box alone`() {
        val box = GuiBox(200, 200, 18, 18)
        assertEquals(Snapped(200, 200, null, null), snapToNeighbors(box, listOf(GuiBox(0, 0, 18, 18)), reach = 3))
        assertEquals(Snapped(200, 200, null, null), snapToNeighbors(box, emptyList(), reach = 3))
    }

    @Test
    fun `undo steps back and skips snapshots that change nothing`() {
        var state = 0
        val history = EditHistory(capture = { state }, restore = { state = it })

        history.remember()
        state = 1
        history.remember()
        // Remembered, then nothing changed.
        history.remember()

        assertTrue(history.undo())
        assertEquals(0, state)
        assertFalse(history.undo())
    }

    @Test
    fun `a held key shares one snapshot until it pauses`() {
        var state = 0
        var time = 0L
        val history = EditHistory(capture = { state }, restore = { state = it }, now = { time })

        repeat(5) {
            history.remember("nudge")
            state++
            time += 30
        }
        time += 5_000
        history.remember("nudge")
        state++

        assertTrue(history.undo())
        assertEquals(5, state)
        assertTrue(history.undo())
        assertEquals(0, state)
    }

    @Test
    fun `history keeps only the newest snapshots`() {
        var state = 0
        val history = EditHistory(capture = { state }, restore = { state = it }, limit = 2)
        repeat(4) {
            history.remember()
            state++
        }

        assertTrue(history.undo())
        assertTrue(history.undo())
        assertEquals(2, state)
        assertFalse(history.undo())
    }

    @Test
    fun `a shared string is clamped and unknown values fall back`() {
        val json = JsonParser.parseString(
            """{"buttons":[
                {"x":99999,"width":1,"height":500,"runs":"LAUNCH","scope":null,"command":"/warp hub","label":null},
                "not a button",
                {"runs":"SHORTCUT","scope":"TITLE","titleFilter":"Auction"}
            ]}""",
        ).asJsonObject

        val buttons = InventoryButtonPortable.read(json)

        assertEquals(2, buttons.size)
        val first = buttons[0]
        assertEquals(2000, first.x)
        assertEquals(InventoryButton.MIN_SIZE, first.width)
        assertEquals(InventoryButton.MAX_SIZE, first.height)
        assertEquals(ButtonRuns.COMMAND, first.runs)
        assertEquals(ButtonScope.INVENTORY, first.scope)
        assertEquals("warp hub", first.command)
        assertEquals("", first.label)
        assertEquals(ButtonScope.TITLE, buttons[1].scope)
        assertEquals(ButtonRuns.SHORTCUT, buttons[1].runs)
    }

    @Test
    fun `a share string decodes back to the same buttons`() {
        val buttons = listOf(
            InventoryButton(x = -20, y = 4, anchorRight = true, icon = "HYPERION", label = "Hub", command = "warp hub"),
            InventoryButton(width = InventoryButton.MAX_SIZE, scope = ButtonScope.TITLE, titleFilter = "Chest"),
        )

        val document = Portable.decode(InventoryButtonPortable.export(buttons)) as PortableRead.Document

        assertEquals(InventoryButtonPortable.kind, document.kind)
        assertEquals(buttons, InventoryButtonPortable.read(document.json))
    }

    @Test
    fun `a shared profile keeps its name and buttons`() {
        val profile = ButtonProfile("id", "Dungeons", mutableListOf(InventoryButton(command = "warp dungeon_hub")))

        val document = Portable.decode(InventoryButtonPortable.exportProfile(profile)) as PortableRead.Document

        assertEquals("Dungeons", document.json.get("name").asString)
        assertTrue(document.json.get("profile").asBoolean)
        assertEquals(profile.buttons, InventoryButtonPortable.read(document.json))
    }

    @Test
    fun `item search puts exact and prefix matches first`() {
        val items = listOf(
            PickerItem("ASPECT_OF_THE_END", "Aspect of the End", "RARE"),
            PickerItem("END_STONE_SWORD", "End Stone Sword", "EPIC"),
            PickerItem("ENDER_PEARL", "Ender Pearl", null),
            PickerItem("HYPERION", "Hyperion", "LEGENDARY"),
        )

        assertEquals(listOf("END_STONE_SWORD", "ENDER_PEARL", "ASPECT_OF_THE_END"), matchItems(items, "end").map { it.id })
        assertEquals(listOf("HYPERION"), matchItems(items, " hyperion ").map { it.id })
        assertEquals(listOf("ASPECT_OF_THE_END"), matchItems(items, "aspect end").map { it.id })
        assertEquals(items, matchItems(items, ""))
        assertTrue(matchItems(items, "zzz").isEmpty())
    }
}
