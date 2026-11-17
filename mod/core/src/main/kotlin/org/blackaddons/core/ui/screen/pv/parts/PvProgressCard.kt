package org.blackaddons.core.ui.screen.pv.parts
import org.blackaddons.api.ui.anim.Animation
import org.blackaddons.api.ui.layout.Rect
import org.blackaddons.api.ui.render.Render
import org.blackaddons.api.ui.screen.Tooltip
import org.blackaddons.api.ui.theme.Fonts
import org.blackaddons.api.ui.theme.TextStyle
import org.blackaddons.api.ui.theme.Theme
import org.blackaddons.api.ui.widget.MouseButtonInput
import org.blackaddons.api.ui.widget.ProgressBar
import org.blackaddons.api.ui.widget.UiMouse
import org.blackaddons.api.ui.widget.Widget
import org.blackaddons.api.util.color.lerpColor
import java.awt.Color
import kotlin.math.floor

internal class PvProgressCard(private val onClick: () -> Unit) : Widget {

    data class Content(
        val label: String,
        val value: String,
        val progress: Double,
        val icon: ChipIcon?,
        val detail: String,
        val accent: Color = Theme.ACCENT,
    )

    private val bounds = Rect()
    private val hover = Animation(durationMs = Theme.ANIM_HOVER.toLong())

    private var content = Content("", "", 0.0, null, "")
    private var boxWidth = 0f
    private var boxHeight = 0f
    var selected = false

    fun set(content: Content, selected: Boolean) {
        this.content = content
        this.selected = selected
    }

    fun render(x: Float, y: Float, width: Float, height: Float) {
        boxWidth = width
        boxHeight = height
        bounds.set(x, y, width, height)
        val hovered = bounds.has(UiMouse.x, UiMouse.y)
        hover.animateTo(if (hovered) 1f else 0f)
        val fill = if (selected) Theme.SURFACE_ACTIVE else lerpColor(Theme.SURFACE, Theme.SURFACE_HOVER, hover.get())
        Render.rect(x, y, width, height, fill, Theme.RADIUS)
        if (selected) Render.hollowRect(x, y, width, height, 1f, content.accent, Theme.RADIUS)

        val iconSize = floor(height - Theme.PADDING * 2f - BAR_H)
        val iconX = floor(x + Theme.PADDING)
        val iconY = floor(y + (height - BAR_H - iconSize) / 2f)
        drawIcon(content.icon, iconX, iconY, iconSize)

        val textX = iconX + iconSize + Theme.PADDING_SMALL
        val textWidth = (width - (textX - x) - Theme.PADDING).coerceAtLeast(0f)
        Render.text(content.label, textX, y + Theme.PADDING_SMALL, TextStyle.BODY_PRIMARY_MEDIUM, maxWidth = textWidth)
        val barY = y + height - BAR_H - Theme.PADDING_SMALL
        Render.text(content.value, textX, barY - Theme.PADDING_SMALL - Theme.FONT_SMALL, Theme.FONT_SMALL, content.accent, Fonts.bold, maxWidth = textWidth)
        ProgressBar.draw(textX, barY, textWidth, BAR_H, content.progress, fill = content.accent)

        if (hovered) Tooltip.show(content.detail)
    }

    override fun mouseClicked(mouse: MouseButtonInput): Boolean {
        if (!mouse.isLeft || !bounds.has(mouse.x, mouse.y)) return false
        onClick()
        return true
    }

    fun clear() = bounds.clear()

    override val width: Float get() = boxWidth
    override val height: Float get() = boxHeight

    override fun draw(x: Float, y: Float) = render(x, y, width, height)

    companion object {
        private const val BAR_H = 3f

        fun drawIcon(icon: ChipIcon?, x: Float, y: Float, size: Float) {
            when (icon) {
                is ChipIcon.Item -> Render.itemSpec(icon.spec, x, y, size, size)
                is ChipIcon.Head -> Render.playerHead(icon.uuid, icon.name, icon.texture, x, y, size, size)
                null -> Unit
            }
        }
    }
}
