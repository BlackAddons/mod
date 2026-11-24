package org.blackaddons.api.ui.screen

import org.blackaddons.api.input.Keys
import org.blackaddons.api.ui.anim.Animation
import org.blackaddons.api.ui.layout.hit
import org.blackaddons.api.ui.render.Render
import org.blackaddons.api.ui.screen.ModalPanel.Companion.FOOTER_GAP
import org.blackaddons.api.ui.theme.Fonts
import org.blackaddons.api.ui.theme.Glass
import org.blackaddons.api.ui.theme.Theme
import org.blackaddons.api.ui.theme.Theme.withAlpha
import org.blackaddons.api.ui.widget.Button
import org.blackaddons.api.ui.widget.MouseButtonInput
import org.blackaddons.api.ui.widget.text.AnimatedText

/**
 * Centered card over a dimmed screen. Subclasses fill the content rect in [renderContent], footer included.
 * Escape pops; bind your own in [keys] to change that.
 */
abstract class ModalPanel(private val title: String) : Panel() {

    init {
        keys.on(Keys.ESCAPE) {
            onEscape()
            UiManager.pop()
        }
    }

    // The dim needs the screen it came from.
    final override val showsBelow: Boolean get() = true

    /** Override with a getter for a resizing modal. */
    protected open val panelWidth: Float = 460f
    protected open val panelHeight: Float = 360f

    /**
     * [preferred] px, shrunk to fit the screen minus [margin], but never below [min]. Use it in a [panelWidth] getter.
     */
    protected fun fitWidth(preferred: Float, min: Float = 0f, margin: Float = SCREEN_MARGIN): Float =
        minOf(preferred, (UiManager.screenWidth - margin).coerceAtLeast(min))

    /**
     * [preferred] px, shrunk to fit the screen minus [margin], but never below [min]. Use it in a [panelHeight] getter.
     */
    protected fun fitHeight(preferred: Float, min: Float = 0f, margin: Float = SCREEN_MARGIN): Float =
        minOf(preferred, (UiManager.screenHeight - margin).coerceAtLeast(min))

    /** [fraction] of the screen width, and at least [min] px as long as the screen minus [margin] has room. */
    protected fun widthShare(fraction: Float, min: Float, margin: Float = SCREEN_MARGIN): Float =
        fitWidth(maxOf(UiManager.screenWidth * fraction, min), margin = margin)

    /** [fraction] of the screen height, and at least [min] px as long as the screen minus [margin] has room. */
    protected fun heightShare(fraction: Float, min: Float, margin: Float = SCREEN_MARGIN): Float =
        fitHeight(maxOf(UiManager.screenHeight * fraction, min), margin = margin)

    /** Override for dynamic titles. */
    open val modalTitle: String get() = title

    private val headerTitleText = AnimatedText(title, fontSize = Theme.FONT_TITLE, font = Fonts.bold)

    protected var contentX = 0f
        private set
    protected var contentY = 0f
        private set
    protected var contentW = 0f
        private set
    protected var contentH = 0f
        private set

    /** Valid after [render]. */
    protected var panelX = 0f
        private set
    protected var panelY = 0f
        private set
    protected var panelW = 0f
        private set
    protected var panelH = 0f
        private set

    private val entrance = Animation(durationMs = Theme.ANIM_NORMAL.toLong())

    final override fun render(mouseX: Float, mouseY: Float, deltaTicks: Float) {
        val sw = UiManager.screenWidth
        val sh = UiManager.screenHeight

        // Snapshot once so every derived value agrees this frame.
        val pw = panelWidth
        val ph = panelHeight

        entrance.animateTo(1f)
        val progress = entrance.get()

        val dimColor = Theme.OVERLAY_DIM.withAlpha(MODAL_DIM_ALPHA)
        Render.rect(0f, 0f, sw, sh, dimColor.withAlpha((dimColor.alpha * progress).toInt()))

        val px = (sw - pw) / 2f
        val py = (sh - ph) / 2f
        panelX = px
        panelY = py
        panelW = pw
        panelH = ph

        Render.globalAlpha(progress)
        Render.push()
        val cx = px + pw / 2f
        val cy = py + ph / 2f
        val scale = 0.9f + 0.1f * progress
        Render.translate(cx, cy)
        Render.scale(scale, scale)
        Render.translate(-cx, -cy)

        Render.glass(px, py, pw, ph, Glass.WINDOW)
        val title = modalTitle
        if (title.isNotEmpty()) {
            val maxTitleW = pw - Theme.PADDING_LARGE * 2f
            headerTitleText.text = Render.ellipsize(title, maxTitleW, Theme.FONT_TITLE, Fonts.bold)
            headerTitleText.render(px + Theme.PADDING_LARGE, py + Theme.PADDING_LARGE)
        }

        contentX = px + Theme.PADDING_LARGE
        contentY = py + Theme.PADDING_LARGE + if (title.isEmpty()) 0f else Theme.FONT_TITLE + Theme.PADDING
        contentW = pw - Theme.PADDING_LARGE * 2f
        contentH = py + ph - contentY - Theme.PADDING_LARGE

        renderContent(mouseX, mouseY)

        Render.pop()
        Render.globalAlpha(1f)
    }

    /** Dim, card and title are already drawn. */
    protected abstract fun renderContent(mouseX: Float, mouseY: Float)

    /** Content should stop [FOOTER_GAP] above it. */
    protected val footerTop: Float get() = contentY + contentH - FOOTER_H

    /**
     * Right-aligned buttons, primary last, under a hairline. [leading] sits alone on the left. Returns the
     * left edge of the right-hand group.
     */
    protected fun footer(mouseX: Float, mouseY: Float, vararg buttons: Button, leading: Button? = null): Float {
        Render.rect(panelX, footerTop - FOOTER_GAP / 2f, panelW, 1f, Theme.BORDER.withAlpha(120))
        leading?.render(contentX, footerTop, footerButtonWidth(leading), FOOTER_H, mouseX, mouseY)
        var x = contentX + contentW
        for (button in buttons.reversed()) {
            val w = footerButtonWidth(button)
            x -= w
            button.render(x, footerTop, w, FOOTER_H, mouseX, mouseY)
            x -= Theme.PADDING_SMALL
        }
        return x + Theme.PADDING_SMALL
    }

    private fun footerButtonWidth(button: Button) = maxOf(button.naturalWidth() + Theme.PADDING, FOOTER_BUTTON_MIN_W)

    /** Card height for [content] of room. */
    protected fun heightFor(content: Float, withFooter: Boolean = true): Float =
        Theme.PADDING_LARGE * 2f + Theme.FONT_TITLE + Theme.PADDING + content +
            if (withFooter) FOOTER_GAP + FOOTER_H else 0f

    /** True if used. An unused click outside the card dismisses the modal. */
    protected open fun contentClicked(mouse: MouseButtonInput): Boolean = widgets.mouseClicked(mouse)

    open val closeOnOutsideClick: Boolean get() = true

    final override fun mouseClicked(mouse: MouseButtonInput): Boolean {
        if (contentClicked(mouse)) return true
        if (closeOnOutsideClick && mouse.isLeft && !insidePanel(mouse.x, mouse.y)) UiManager.pop()
        // Swallowed: the screen behind is visible, not live.
        return true
    }

    private fun insidePanel(mouseX: Float, mouseY: Float): Boolean =
        hit(mouseX, mouseY, panelX, panelY, panelW, panelH)

    /** Override to save changes. */
    open fun onEscape() {}

    companion object {
        private const val MODAL_DIM_ALPHA = 195
        private const val SCREEN_MARGIN = 40f

        const val FOOTER_H = 22f

        /** The hairline sits halfway. */
        const val FOOTER_GAP = 16f
        private const val FOOTER_BUTTON_MIN_W = 76f
    }
}
