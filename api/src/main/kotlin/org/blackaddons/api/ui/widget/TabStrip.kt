package org.blackaddons.api.ui.widget

import org.blackaddons.api.ui.anim.Animation
import org.blackaddons.api.ui.anim.Easing
import org.blackaddons.api.ui.icon.VectorIcon
import org.blackaddons.api.ui.layout.centered
import org.blackaddons.api.ui.layout.hit
import org.blackaddons.api.ui.render.Render
import org.blackaddons.api.ui.screen.Tooltip
import org.blackaddons.api.ui.theme.Fonts
import org.blackaddons.api.ui.theme.TextStyle
import org.blackaddons.api.ui.theme.Theme
import org.blackaddons.api.ui.theme.Theme.withAlpha
import java.awt.Color

/**
 * Browser-style tabs above a panel's top edge (no content height). The active tab uses the panel color;
 * disabled tabs explain themselves on hover. Icon tabs collapse to the icon unless active.
 */
class TabStrip(
    private val tabs: List<Tab>,
    private var activeId: String,
    private val onSelect: (String) -> Unit,
    private val panelColor: Color? = null,
    private val tabColor: Color? = null,
) : InputTarget {

    // Per frame, so theme changes apply.
    private val panel: Color get() = panelColor ?: Theme.BACKGROUND
    private val tab: Color get() = tabColor ?: Theme.SURFACE

    /** No [icon] shows [label]. [disabledHint] ("Coming soon") joins a disabled tab's tooltip. [badge] > 0 = count. */
    class Tab(
        val id: String,
        val label: String,
        val enabled: Boolean = true,
        val icon: VectorIcon? = null,
        val disabledHint: String? = null,
        val badge: () -> Int = { 0 },
    ) {
    }

    private class Slot(val tab: Tab, val x: Float, val y: Float, val w: Float, val h: Float, val t: Float)

    private var slots = emptyList<Slot>()
    private val widthAnims = mutableMapOf<String, Animation>()
    private val barXAnim = Animation(durationMs = Theme.ANIM_NORMAL.toLong(), easing = Easing.easeOutCubic)
    private val barWAnim = Animation(durationMs = Theme.ANIM_NORMAL.toLong(), easing = Easing.easeOutCubic)
    private var indicatorInitialized = false

    val active: String get() = activeId

    fun render(panelX: Float, panelY: Float, mouseX: Float, mouseY: Float) {
        val built = ArrayList<Slot>(tabs.size)
        var x = panelX + INSET
        val y = panelY - HEIGHT
        for (tab in tabs) {
            val isActive = tab.id == activeId
            val expanded = expandedWidth(tab)
            // Label-only tabs never collapse.
            val t = if (tab.hasIcon) widthAnim(tab.id, isActive).also { it.animateTo(if (isActive) 1f else 0f) }.get() else 1f
            val w = COLLAPSED_W + (expanded - COLLAPSED_W) * t

            built += Slot(tab, x, y, w, HEIGHT, t)
            x += w + GAP
        }
        slots = built

        val activeSlot = built.firstOrNull { it.tab.id == activeId } ?: built.firstOrNull()
        if (activeSlot != null) {
            val targetX = activeSlot.x + BAR_INSET
            val targetW = activeSlot.w - BAR_INSET * 2f
            if (!indicatorInitialized) {
                barXAnim.set(targetX)
                barWAnim.set(targetW)
                indicatorInitialized = true
            } else {
                barXAnim.animateTo(targetX)
                barWAnim.animateTo(targetW)
            }
        }

        for (slot in built) drawTab(slot, mouseX, mouseY)
        // After every tab, or the next tab paints over a wide count.
        for (slot in built) drawBadge(slot, slot.tab.badge())

        if (indicatorInitialized) {
            Render.rect(barXAnim.get(), panelY - 1f, barWAnim.get(), 2f, Theme.ACCENT, 1f)
        }
    }

    /** True if the click hit the bar. Left button only; right-click belongs to the screen (see [idAt]). */
    override fun mouseClicked(mouse: MouseButtonInput): Boolean {
        if (!mouse.isLeft) return false
        val slot = slots.firstOrNull { contains(it, mouse.x, mouse.y) } ?: return false
        if (slot.tab.enabled && slot.tab.id != activeId) {
            activeId = slot.tab.id
            onSelect(slot.tab.id)
        }
        return true
    }

    /** For screens attaching context menus to tabs. */
    fun idAt(mouseX: Float, mouseY: Float): String? =
        slots.firstOrNull { contains(it, mouseX, mouseY) }?.tab?.id

    private fun widthAnim(tabId: String, active: Boolean): Animation = widthAnims.getOrPut(tabId) {
        Animation(if (active) 1f else 0f, Theme.ANIM_NORMAL.toLong(), Easing.easeOutCubic)
    }

    private fun expandedWidth(tab: Tab): Float {
        val textW = Render.textWidth(tab.label, Theme.FONT_BODY, Fonts.medium)
        val icon = if (tab.hasIcon) ICON_SIZE + ICON_GAP else 0f
        return PADDING * 2f + icon + textW
    }

    private fun drawTab(slot: Slot, mouseX: Float, mouseY: Float) {
        val tab = slot.tab
        val hovered = contains(slot, mouseX, mouseY)
        val isActive = tab.id == activeId

        // Tabs stop flush at the panel edge; the active one uses the panel color, so no translucent overlap seam.
        val fill = when {
            isActive -> panel
            !tab.enabled -> this.tab.withAlpha(120)
            hovered -> Theme.SURFACE_HOVER
            else -> this.tab
        }
        Render.halfRoundedRect(slot.x, slot.y, slot.w, slot.h, fill, Theme.RADIUS, roundTop = true)

        val color = when {
            isActive -> Theme.ACCENT
            !tab.enabled -> Theme.TEXT_DISABLED
            else -> Theme.TEXT_SECONDARY
        }

        if (tab.hasIcon) {
            val collapsedX = centered(slot.x, COLLAPSED_W, ICON_SIZE)
            val expandedX = slot.x + PADDING
            val ix = collapsedX + (expandedX - collapsedX) * slot.t
            val iy = centered(slot.y, slot.h, ICON_SIZE)
            Render.icon(requireNotNull(tab.icon), ix, iy, ICON_SIZE, color)
            drawRevealedLabel(tab.label, expandedX + ICON_SIZE + ICON_GAP, slot, color)
        } else {
            val textW = Render.textWidth(tab.label, Theme.FONT_BODY, Fonts.medium)
            Render.text(
                tab.label, centered(slot.x, slot.w, textW),
                Render.centerY(slot.y, slot.h, Theme.FONT_BODY, Fonts.medium),
                Theme.FONT_BODY, color, Fonts.medium,
            )
        }

        if (hovered) {
            val hint = tab.disabledHint
            Tooltip.show(if (tab.enabled || hint == null) tab.label else "${tab.label} — $hint")
        }
    }

    /** Wipes in while expanding; one draw once open to avoid per-letter layout. */
    private fun drawRevealedLabel(label: String, x: Float, slot: Slot, color: Color) {
        val t = slot.t
        if (t <= COLLAPSED_LABEL_T) {
            return
        }

        val y = Render.centerY(slot.y, slot.h, Theme.FONT_BODY, Fonts.medium)
        if (t >= 1f) {
            Render.text(label, x, y, Theme.FONT_BODY, color, Fonts.medium)
            return
        }

        var cx = x
        for (i in label.indices) {
            val char = label[i].toString()
            val start = i.toFloat() / label.length
            val end = (i + 1).toFloat() / label.length
            val charT = ((t - start) / (end - start)).coerceIn(0f, 1f)
            if (charT > 0f) {
                Render.text(char, cx, y, Theme.FONT_BODY, color.withAlpha((charT * 255).toInt()), Fonts.medium)
            }
            cx += Render.textWidth(char, Theme.FONT_BODY, Fonts.medium)
        }
    }

    private fun drawBadge(slot: Slot, count: Int) {
        if (count <= 0) return
        val text = if (count > BADGE_MAX) "$BADGE_MAX+" else count.toString()
        val w = maxOf(BADGE_H, Render.textWidth(text, Theme.FONT_SMALL, Fonts.medium) + BADGE_PAD * 2f)
        // Inside the tab's top-right corner, so a "99+" stays over its own tab instead of the neighbor.
        val x = slot.x + slot.w - w
        val y = slot.y - BADGE_H / 2f + BADGE_PAD
        Render.rect(x, y, w, BADGE_H, Theme.ACCENT, BADGE_H / 2f)
        Render.text(
            text, x + (w - Render.textWidth(text, TextStyle.SMALL_PRIMARY_MEDIUM)) / 2f,
            Render.centerY(y, BADGE_H, TextStyle.SMALL_PRIMARY_MEDIUM),
            TextStyle.SMALL_PRIMARY_MEDIUM,
        )
    }

    private fun contains(slot: Slot, mx: Float, my: Float) =
        hit(mx, my, slot.x, slot.y, slot.w, slot.h)

    private val Tab.hasIcon get() = icon != null

    companion object {
        const val HEIGHT = 26f
        private const val COLLAPSED_W = 36f
        private const val GAP = 3f
        private const val INSET = 12f
        private const val PADDING = 10f
        private const val ICON_SIZE = 14f
        private const val ICON_GAP = 6f

        /** Below this, icon-only. */
        private const val COLLAPSED_LABEL_T = 0.05f

        private const val BAR_INSET = 5f

        private const val BADGE_H = 12f
        private const val BADGE_PAD = 3f
        private const val BADGE_MAX = 99
    }
}
