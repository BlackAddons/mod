package org.blackaddons.core.features.impl.ui.inventorybuttons

import org.blackaddons.api.gui.GuiBox
import org.blackaddons.api.ui.anim.Animation
import org.blackaddons.api.ui.icon.LucideIcons
import org.blackaddons.api.ui.render.Render
import org.blackaddons.api.ui.theme.Fonts
import org.blackaddons.api.ui.theme.Theme
import java.awt.Color
import java.util.IdentityHashMap
import kotlin.math.min

internal object ButtonPainter {

    const val RADIUS = 3f
    const val ICON_INSET = 1f

    // Keyed by identity, since a duplicated button equals its original.
    private val pops = IdentityHashMap<InventoryButton, Animation>()

    fun pop(button: InventoryButton) {
        pops.getOrPut(button) { Animation(initial = 1f) }.apply {
            set(POP_SCALE)
            animateTo(1f)
        }
    }

    fun clearPops() = pops.clear()

    fun drawButton(button: InventoryButton, box: GuiBox, hovered: Boolean, dropTarget: Boolean) {
        val scale = (pops[button]?.get() ?: 1f) * if (dropTarget) DROP_TARGET_SCALE else 1f
        val x = box.x.toFloat()
        val y = box.y.toFloat()
        val w = box.w.toFloat()
        val h = box.h.toFloat()
        Render.push()
        if (scale != 1f) {
            Render.translate(x + w / 2f, y + h / 2f)
            Render.scale(scale, scale)
            Render.translate(-(x + w / 2f), -(y + h / 2f))
        }
        try {
            Render.rect(x, y, w, h, if (hovered) Theme.SURFACE_HOVER else Theme.SURFACE, RADIUS)
            val border = when {
                dropTarget -> Theme.ACCENT
                hovered -> Theme.TEXT_MUTED
                else -> Theme.BORDER
            }
            Render.hollowRect(x, y, w, h, 1f, border, RADIUS)
            drawContent(button, x, y, w, h)
        } finally {
            Render.pop()
        }
    }

    private fun drawContent(button: InventoryButton, x: Float, y: Float, w: Float, h: Float) {
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
        val textW = Render.textWidth(label, LABEL_SIZE, Fonts.medium).coerceAtMost(maxW)
        val tx = if (spec == null) x + (w - textW) / 2f else textX
        Render.text(label, tx, Render.centerY(y, h, LABEL_SIZE, Fonts.medium), LABEL_SIZE, Theme.TEXT_PRIMARY, Fonts.medium, maxW)
    }

    fun drawPencil(box: GuiBox, hovered: Boolean, editMode: Boolean) {
        val x = box.x.toFloat()
        val y = box.y.toFloat()
        val w = box.w.toFloat()
        val h = box.h.toFloat()
        val bg = when {
            editMode -> Theme.ACCENT
            hovered -> Theme.SURFACE_HOVER
            else -> Theme.SURFACE
        }
        Render.rect(x, y, w, h, bg, RADIUS)
        Render.hollowRect(x, y, w, h, 1f, if (editMode) Theme.ACCENT else Theme.BORDER, RADIUS)
        val iconColor = when {
            editMode -> Color.WHITE
            hovered -> Theme.TEXT_PRIMARY
            else -> Theme.TEXT_SECONDARY
        }
        val size = min(w, h) * PENCIL_ICON_FILL
        Render.icon(LucideIcons.pencil, x + (w - size) / 2f, y + (h - size) / 2f, size, iconColor)
    }

    private const val LABEL_GAP = 2f
    private const val LABEL_SIZE = 8f
    private const val PENCIL_ICON_FILL = 2f / 3f
    private const val POP_SCALE = 1.15f
    private const val DROP_TARGET_SCALE = 1.08f
}
