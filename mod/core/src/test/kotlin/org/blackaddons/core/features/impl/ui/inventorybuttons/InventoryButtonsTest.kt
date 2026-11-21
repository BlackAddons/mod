package org.blackaddons.core.features.impl.ui.inventorybuttons

import org.blackaddons.api.gui.GuiBox
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.Base64

class InventoryButtonsTest {

    private val inventory = GuiBox(152, 45, 176, 166)
    private val chest = GuiBox(152, 20, 176, 222)

    @Test
    fun `a button made above the inventory stays above a taller chest`() {
        val button = InventoryButton()
        anchorTo(button, GuiBox(160, 25, 18, 18), inventory)

        assertFalse(placeButton(button, chest, SCREEN_W, SCREEN_H).intersects(chest))
    }

    @Test
    fun `offsets that land on the GUI are pushed out of it`() {
        // An imported or hand-edited button can point inside.
        val button = InventoryButton(x = 10, y = 10)

        assertEquals(GuiBox(162, inventory.y - 18, 18, 18), placeButton(button, inventory, SCREEN_W, SCREEN_H))
    }

    @Test
    fun `a button dropped on the GUI stays inside it`() {
        val button = InventoryButton()
        val at = GuiBox(200, 100, 18, 18)
        settleButton(button, at, inventory, SCREEN_W, SCREEN_H)

        assertTrue(button.insideGui)
        assertEquals(at, placeButton(button, inventory, SCREEN_W, SCREEN_H))
    }

    @Test
    fun `a button dropped mostly off the GUI is pushed off it`() {
        val button = InventoryButton()
        settleButton(button, GuiBox(inventory.x - 12, 100, 18, 18), inventory, SCREEN_W, SCREEN_H)

        assertFalse(button.insideGui)
        assertEquals(GuiBox(inventory.x - 18, 100, 18, 18), placeButton(button, inventory, SCREEN_W, SCREEN_H))
    }

    @Test
    fun `push out takes the shortest way`() {
        assertEquals(GuiBox(inventory.right, 100, 18, 18), pushOut(GuiBox(inventory.right - 4, 100, 18, 18), inventory))
        assertEquals(GuiBox(inventory.x - 18, 100, 18, 18), pushOut(GuiBox(inventory.x - 10, 100, 18, 18), inventory))
        assertEquals(GuiBox(200, inventory.bottom, 18, 18), pushOut(GuiBox(200, inventory.bottom - 3, 18, 18), inventory))
        val outside = GuiBox(0, 0, 18, 18)
        assertEquals(outside, pushOut(outside, inventory))
    }

    @Test
    fun `anchoring keeps the position and follows the nearest edges`() {
        val button = InventoryButton()
        val at = GuiBox(inventory.right + 2, inventory.bottom - 20, 18, 18)
        anchorTo(button, at, inventory)

        assertTrue(button.anchorRight)
        assertTrue(button.anchorBottom)
        assertEquals(at, placeButton(button, inventory, SCREEN_W, SCREEN_H))

        // A wider GUI carries a right-anchored button along.
        val wide = GuiBox(100, 45, 280, 166)
        assertEquals(wide.right + 2, placeButton(button, wide, SCREEN_W, SCREEN_H).x)
    }

    @Test
    fun `a button with no room on its side moves to a side it fits`() {
        // Kept on screen above, it would land on the GUI.
        val button = InventoryButton(y = -60, width = 100, height = 60)

        assertEquals(GuiBox(52, 0, 100, 60), placeButton(button, inventory, SCREEN_W, SCREEN_H))
    }

    @Test
    fun `a button too big for every side shrinks beside the GUI`() {
        val button = InventoryButton(x = -200, width = 200, height = 100)
        val box = placeButton(button, inventory, SCREEN_W, SCREEN_H)

        assertEquals(GuiBox(0, 57, 152, 76), box)
        assertFalse(box.intersects(inventory))
        assertEquals(200, button.width)
    }

    @Test
    fun `buttons stay on screen`() {
        assertEquals(GuiBox(0, SCREEN_H - 18, 18, 18), keepOnScreen(GuiBox(-30, SCREEN_H + 5, 18, 18), SCREEN_W, SCREEN_H))
    }

    @Test
    fun `grid counts from the GUI corner`() {
        assertEquals(162, snapToGrid(163, 152, 10))
        assertEquals(172, snapToGrid(168, 152, 10))
        assertEquals(-28, snapToGrid(-26, 152, 10))
    }

    @Test
    fun `icon text picks its kind`() {
        assertEquals(ButtonIcons.Icon.SkyblockId("HYPERION"), ButtonIcons.parse(" HYPERION "))
        assertEquals(ButtonIcons.Icon.SkyblockId("WOOD:4"), ButtonIcons.parse("WOOD:4"))
        assertEquals(ButtonIcons.Icon.Vanilla("minecraft:diamond"), ButtonIcons.parse("diamond"))
        assertEquals(ButtonIcons.Icon.Vanilla("minecraft:ender_chest"), ButtonIcons.parse("minecraft:ender_chest"))
        assertEquals(ButtonIcons.Icon.Give("stone[enchantment_glint_override=true]"), ButtonIcons.parse("/give @s stone[enchantment_glint_override=true]"))
        assertEquals(ButtonIcons.Icon.Give("bone"), ButtonIcons.parse("give Steve bone"))
        assertEquals(ButtonIcons.Icon.Give("stone[enchantment_glint_override=true]"), ButtonIcons.parse("stone[enchantment_glint_override=true]"))
        assertEquals(ButtonIcons.Icon.Skull("ZXlK"), ButtonIcons.parse("skull:ZXlK"))
        assertNull(ButtonIcons.parse("  "))
        assertNull(ButtonIcons.parse("skull:"))
    }

    @Test
    fun `a skull hash becomes a textures value`() {
        val hash = "d7cc6687423d0570d556ac53e0676cb563bbdd9717cd8269bdebed6f6d4e7bf8"
        val skull = ButtonIcons.parse("skull:$hash") as ButtonIcons.Icon.Skull
        val decoded = String(Base64.getDecoder().decode(skull.textures))
        assertTrue(decoded.contains("https://textures.minecraft.net/texture/$hash"), decoded)
    }

    @Test
    fun `a dropped head keeps its skin`() {
        val raw = """{"id":"minecraft:player_head","count":1,"components":{"minecraft:profile":{"properties":[{"name":"textures","value":"abc"}]}}}"""
        assertEquals("abc", ButtonIcons.headTextures(raw))
        assertNull(ButtonIcons.headTextures("""{"id":"minecraft:stone","count":1}"""))
        assertNull(ButtonIcons.headTextures("not json"))
    }

    private companion object {
        const val SCREEN_W = 480
        const val SCREEN_H = 270
    }
}
