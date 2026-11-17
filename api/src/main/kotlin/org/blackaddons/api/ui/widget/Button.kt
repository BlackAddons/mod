package org.blackaddons.api.ui.widget

import org.blackaddons.api.ui.anim.Animation
import org.blackaddons.api.ui.layout.Rect
import org.blackaddons.api.ui.render.Render
import org.blackaddons.api.ui.screen.Tooltip
import org.blackaddons.api.ui.theme.Fonts
import org.blackaddons.api.ui.theme.Theme
import org.blackaddons.api.ui.theme.Theme.withAlpha
import org.blackaddons.api.util.color.lerpColor

/** Plain button. [render] stamps bounds, so an undrawn button isn't clickable. Long labels ellipsize with a tooltip. */
class Button(var label: String, var onClick: () -> Unit = {}) : Widget {

    private val bounds = Rect()
    private val hover = Animation(durationMs = Theme.ANIM_HOVER.toLong())

    var enabled: Boolean = true

    /** Accent-filled primary action. */
    var accent: Boolean = false

    /** Danger-filled; wins over [accent]. */
    var danger: Boolean = false

    /** Null uses [naturalWidth]. */
    var boxWidth: Float? = null
    override val width: Float get() = boxWidth ?: naturalWidth()
    override var height: Float = DEFAULT_HEIGHT

    fun naturalWidth(): Float =
        Render.textWidth(label, Theme.FONT_SMALL, Fonts.medium) + CONTENT_PAD * 2f

    /** Reads [UiMouse] for hover. */
    override fun draw(x: Float, y: Float) = render(x, y, width, height, UiMouse.x, UiMouse.y)

    fun render(x: Float, y: Float, w: Float, h: Float, mouseX: Float, mouseY: Float) {
        bounds.set(x, y, w, h)
        val hovered = enabled && bounds.has(mouseX, mouseY)
        hover.animateTo(if (hovered) 1f else 0f)
        val t = hover.get()

        val bg = when {
            !enabled -> Theme.SURFACE.withAlpha(50)
            danger -> lerpColor(Theme.DANGER.withAlpha(170), Theme.DANGER, t)
            accent -> lerpColor(Theme.ACCENT.withAlpha(170), Theme.ACCENT, t)
            else -> lerpColor(Theme.SURFACE_ACTIVE, Theme.ACCENT, t)
        }
        Render.rect(x, y, w, h, bg, Theme.RADIUS_SMALL)

        val text = Render.ellipsize(label, w - CONTENT_PAD * 2f, Theme.FONT_SMALL, Fonts.medium)
        if (hovered && text != label) Tooltip.show(label)
        val textColor = when {
            !enabled -> Theme.TEXT_DISABLED
            danger || accent -> Theme.TEXT_PRIMARY
            else -> lerpColor(Theme.TEXT_SECONDARY, Theme.TEXT_PRIMARY, t)
        }
        Render.text(
            text,
            x + (w - Render.textWidth(text, Theme.FONT_SMALL, Fonts.medium)) / 2f,
            Render.centerY(y, h, Theme.FONT_SMALL, Fonts.medium),
            Theme.FONT_SMALL,
            textColor,
            Fonts.medium,
        )
    }

    override fun mouseClicked(mouse: MouseButtonInput): Boolean {
        if (!mouse.isLeft || !enabled || !bounds.has(mouse.x, mouse.y)) return false
        onClick()
        return true
    }

    /** For screens that conditionally stop drawing it. */
    fun clear() = bounds.clear()

    companion object {
        const val CONTENT_PAD = 8f

        const val DEFAULT_HEIGHT = 18f
    }
}
