package org.blackaddons.api.ui.theme

import org.blackaddons.api.ui.theme.Theme.withAlpha
import org.blackaddons.api.util.color.lerpColor
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Test

// Glass replaced hand-written colors at each call site; these pin it to the formulas it replaced.
class GlassTest {

    private val steps = listOf(0f, 0.013f, 0.25f, 0.5f, 0.731f, 1f)

    @Test
    fun `button matches the Inventory Buttons glass at every hover step`() {
        assertEquals(Theme.BACKGROUND.rgb, Glass.BUTTON.fill())
        for (t in steps) {
            val soft = Theme.ACCENT_SOFT
            assertEquals(soft.withAlpha((soft.alpha * t).toInt()).rgb, Glass.BUTTON.wash(t), "wash at $t")
            assertEquals(lerpColor(Theme.BORDER, Theme.ACCENT, t).rgb, Glass.BUTTON.border(t), "border at $t")
        }
    }

    @Test
    fun `tooltip keeps its near-opaque fill and plain border`() {
        assertEquals(Theme.BACKGROUND.withAlpha(240).rgb, Glass.TOOLTIP.fill())
        assertEquals(Theme.BORDER.rgb, Glass.TOOLTIP.border(0f))
        assertEquals(Theme.BORDER.rgb, Glass.TOOLTIP.border(1f))
        assertEquals(0, Glass.TOOLTIP.wash(1f) ushr 24)
    }

    @Test
    fun `storage cell keeps vanilla's white hover wash`() {
        assertEquals(Theme.BACKGROUND.withAlpha(170).rgb, Glass.CELL.fill())
        for (t in steps) {
            assertEquals(Theme.TEXT_PRIMARY.withAlpha((128 * t).toInt()).rgb, Glass.CELL.wash(t), "wash at $t")
        }
    }

    @Test
    fun `window, card, backdrop and HUD keep their fills`() {
        assertEquals(Theme.BACKGROUND.rgb, Glass.WINDOW.fill())
        assertEquals(Theme.SURFACE.rgb, Glass.CARD.fill())
        assertEquals(Theme.BACKGROUND.withAlpha(204).rgb, Glass.BACKDROP.fill())
        assertEquals(Theme.BACKGROUND.withAlpha(170).rgb, Glass.HUD.fill())
        // The card's hover lands on SURFACE_HOVER, which the storage page switched to.
        assertEquals(Theme.SURFACE_HOVER.rgb, Glass.CARD.wash(1f))
    }

    @Test
    fun `borderless styles report no border`() {
        for (style in listOf(Glass.WINDOW, Glass.CARD, Glass.CELL, Glass.BACKDROP, Glass.HUD)) {
            assertFalse(style.hasBorder, style.name)
            assertEquals(0, style.border(1f), style.name)
        }
    }

    @Test
    fun `hover outside 0 to 1 is clamped`() {
        assertEquals(Glass.BUTTON.wash(1f), Glass.BUTTON.wash(3f))
        assertEquals(Glass.BUTTON.border(0f), Glass.BUTTON.border(-2f))
    }
}
