package org.blackaddons.api.ui.screen

import org.blackaddons.api.input.Keys
import org.blackaddons.api.ui.anim.Animation
import org.blackaddons.api.ui.layout.Rect
import org.blackaddons.api.ui.render.Render
import org.blackaddons.api.ui.theme.Theme
import org.blackaddons.api.ui.theme.Theme.withAlpha
import org.blackaddons.api.ui.widget.MouseButtonInput
import java.awt.Color

/**
 * A card over the live screen, without a dim. Escape or a click outside the card closes it, and that click is
 * swallowed. Each frame, size and place [card] (with [placeUnder], for example), then draw inside [drawCard].
 */
abstract class Popover : Panel() {

    override val showsBelow: Boolean get() = true

    /** Where the card is this frame. Clicks outside it close the popover. */
    protected val card = Rect()

    private val entrance = Animation(durationMs = Theme.ANIM_FAST.toLong())

    init {
        keys.on(Keys.ESCAPE) { UiManager.pop() }
    }

    /** Centers the card on the anchor box, [gap] below it, or above it when there is no room below. */
    protected fun placeUnder(anchorX: Float, anchorY: Float, anchorW: Float, anchorH: Float, w: Float, h: Float, gap: Float) {
        val screenW = UiManager.screenWidth
        val screenH = UiManager.screenHeight
        val centerX = if (anchorW > 0f) anchorX + anchorW / 2f else anchorX
        val x = (centerX - w / 2f).coerceIn(MARGIN, maxOf(MARGIN, screenW - w - MARGIN))
        val below = anchorY + anchorH + gap
        val y = if (below + h + MARGIN <= screenH) below else (anchorY - gap - h).coerceIn(MARGIN, maxOf(MARGIN, screenH - h - MARGIN))
        card.set(x, y, w, h)
    }

    /** Fades the card in while it rises by [rise] px, draws its background and border, then draws [content]. */
    protected fun drawCard(
        rise: Float = 0f,
        radius: Float = Theme.RADIUS,
        border: Color = Theme.BORDER.withAlpha(140),
        borderWidth: Float = 1f,
        content: () -> Unit,
    ) {
        entrance.animateTo(1f)
        val progress = entrance.get()
        Render.globalAlpha(progress)
        Render.push()
        Render.translate(0f, (1f - progress) * rise)
        try {
            Render.rect(card, Theme.BACKGROUND, radius)
            Render.hollowRect(card, borderWidth, border, radius)
            content()
        } finally {
            Render.pop()
            Render.globalAlpha(1f)
        }
    }

    override fun mouseClicked(mouse: MouseButtonInput): Boolean {
        if (super.mouseClicked(mouse)) return true
        if (!card.has(mouse.x, mouse.y)) UiManager.pop()
        return true
    }

    protected companion object {
        /** The gap between the screen edge and the card. */
        const val MARGIN = 4f
    }
}
