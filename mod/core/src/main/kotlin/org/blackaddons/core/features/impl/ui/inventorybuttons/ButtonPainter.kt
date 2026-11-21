package org.blackaddons.core.features.impl.ui.inventorybuttons

import org.blackaddons.api.gui.GuiBox
import org.blackaddons.api.ui.anim.Animation
import org.blackaddons.api.ui.icon.LucideIcons
import org.blackaddons.api.ui.render.Render
import org.blackaddons.api.ui.theme.Fonts
import org.blackaddons.api.ui.theme.Theme
import org.blackaddons.api.ui.theme.Theme.withAlpha
import org.blackaddons.api.util.color.lerpColor
import java.awt.Color
import java.util.IdentityHashMap
import kotlin.math.min

// Theme glass like a popover card, accent on hover; everything in GUI units so it scales with the container.
internal object ButtonPainter {

    const val ICON_INSET = 1f

    val radius = Theme.RADIUS_SMALL

    private val pops = IdentityHashMap<InventoryButton, Animation>()
    private val hovers = IdentityHashMap<InventoryButton, Animation>()

    fun pop(button: InventoryButton) {
        pops.getOrPut(button) { Animation(initial = 1f) }.apply {
            set(POP_SCALE)
            animateTo(1f)
        }
    }

    fun clearPops() {
        pops.clear()
        hovers.clear()
    }

    fun forget(buttons: List<InventoryButton>) = buttons.forEach {
        pops.remove(it)
        hovers.remove(it)
    }

    fun drawButton(button: InventoryButton, box: GuiBox, hovered: Boolean, dropTarget: Boolean) {
        val scale = (pops[button]?.get() ?: 1f) * if (dropTarget) DROP_TARGET_SCALE else 1f
        val x = box.x.toFloat()
        val y = box.y.toFloat()
        val w = box.w.toFloat()
        val h = box.h.toFloat()
        val t = hover(button, hovered || dropTarget)
        Render.push()
        if (scale != 1f) {
            Render.translate(x + w / 2f, y + h / 2f)
            Render.scale(scale, scale)
            Render.translate(-(x + w / 2f), -(y + h / 2f))
        }
        try {
            drawGlass(x, y, w, h, t)
            drawContent(button, x, y, w, h, lerpColor(Theme.TEXT_SECONDARY, Theme.TEXT_PRIMARY, t))
        } finally {
            Render.pop()
        }
    }

    private fun drawGlass(x: Float, y: Float, w: Float, h: Float, t: Float) {
        Render.rect(x, y, w, h, Theme.BACKGROUND, radius)
        val soft = Theme.ACCENT_SOFT
        if (t > 0f) Render.rect(x, y, w, h, soft.withAlpha((soft.alpha * t).toInt()), radius)
        Render.hollowRect(x, y, w, h, 1f, lerpColor(Theme.BORDER, Theme.ACCENT, t), radius)
    }

    private fun hover(button: InventoryButton, hovered: Boolean): Float =
        hovers.getOrPut(button) { Animation(durationMs = Theme.ANIM_HOVER.toLong()) }.apply { animateTo(if (hovered) 1f else 0f) }.get()

    private fun drawContent(button: InventoryButton, x: Float, y: Float, w: Float, h: Float, textColor: Color) {
        val spec = ButtonIcons.spec(button.icon)
        val iconSize = (min(w, h) - ICON_INSET * 2f).coerceAtLeast(0f)
        val label = button.label.trim()
        if (label.isEmpty()) {
            spec?.let { Render.itemSpec(it, x + (w - iconSize) / 2f, y + (h - iconSize) / 2f, iconSize, iconSize) }
            return
        }
        var textX = x + ICON_INSET
        spec?.let {
            Render.itemSpec(it, textX, y + (h - iconSize) / 2f, iconSize, iconSize)
            textX += iconSize + LABEL_GAP
        }
        val maxW = (x + w - ICON_INSET - textX).coerceAtLeast(0f)
        if (maxW <= 0f) return
        val size = Theme.FONT_SMALL
        val textW = Render.textWidth(label, size, Fonts.medium).coerceAtMost(maxW)
        val tx = if (spec == null) x + (w - textW) / 2f else textX
        Render.text(label, tx, Render.centerY(y, h, size, Fonts.medium), size, textColor, Fonts.medium, maxW)
    }

    fun drawPencil(box: GuiBox, hovered: Boolean, editMode: Boolean) {
        val x = box.x.toFloat()
        val y = box.y.toFloat()
        val w = box.w.toFloat()
        val h = box.h.toFloat()
        val t = hover(InventoryButtonStore.pencil, hovered)
        if (editMode) Render.rect(x, y, w, h, Theme.ACCENT, radius) else drawGlass(x, y, w, h, t)
        val size = min(w, h) * PENCIL_ICON_FILL
        val color = if (editMode) Theme.TEXT_PRIMARY else lerpColor(Theme.TEXT_SECONDARY, Theme.TEXT_PRIMARY, t)
        Render.icon(LucideIcons.pencil, x + (w - size) / 2f, y + (h - size) / 2f, size, color)
    }

    private const val LABEL_GAP = 2f
    private const val PENCIL_ICON_FILL = 2f / 3f
    private const val POP_SCALE = 1.15f
    private const val DROP_TARGET_SCALE = 1.08f
}
