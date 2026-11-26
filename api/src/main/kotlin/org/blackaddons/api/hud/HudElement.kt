package org.blackaddons.api.hud

import org.blackaddons.api.Platform
import org.blackaddons.api.ui.UiScaling
import org.blackaddons.api.ui.layout.LayoutNode
import org.blackaddons.api.ui.theme.FontChoice
import org.blackaddons.api.ui.widget.MouseButtonInput
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.TimeSource

/** Draggable HUD element: one of nine anchors plus an offset, so it stays put across resolutions and GUI scales. */
abstract class HudElement {
    abstract val name: String
    open val toggle: Boolean get() = true
    open val shouldDraw: Boolean get() = true
    /** False disables scroll-scaling in the editor. */
    open val resizable: Boolean get() = true

    /** False hides the editor's "T" font handle. */
    open val hasText: Boolean get() = true

    /** Used on first registration and by [resetToDefault]. */
    open val defaultAnchorX: AnchorX get() = AnchorX.LEFT
    open val defaultAnchorY: AnchorY get() = AnchorY.TOP
    open val defaultOffsetX: Float get() = 0f
    open val defaultOffsetY: Float get() = 0f
    open val defaultScale: Float get() = 1f

    /** [FontChoice.INHERIT] follows the global HUD font. */
    open val defaultFontOverride: FontChoice get() = FontChoice.INHERIT

    var anchorX: AnchorX = AnchorX.LEFT
    var anchorY: AnchorY = AnchorY.TOP
    var offsetX: Float = 0f
    var offsetY: Float = 0f

    /** A [FontChoice] name or an installed font key; INHERIT or a missing font uses the HUD default. */
    var fontOverride: String = FontChoice.INHERIT.name

    /** Independent axes for corner-drag stretching; scroll scales both. */
    var scaleX: Float = 1f
    var scaleY: Float = 1f

    fun resetToDefault() {
        anchorX = defaultAnchorX
        anchorY = defaultAnchorY
        offsetX = defaultOffsetX
        offsetY = defaultOffsetY
        scaleX = defaultScale
        scaleY = defaultScale
        fontOverride = defaultFontOverride.name
    }

    /** Drawn above open screens (e.g. notifications), so it can be clicked over any screen; others only via chat. */
    open val drawsOverScreens: Boolean get() = false

    /** Cursor in content space while reachable, null in gameplay. Set around [content]. */
    var pointer: HudPointer? = null
        private set

    /** In content space. True consumes the click. */
    open fun mouseClicked(mouse: MouseButtonInput): Boolean = false

    // Stale bounds must not catch clicks.
    private var lastDrawnAt: TimeSource.Monotonic.ValueTimeMark? = null
    private var lastScaleX = 1f
    private var lastScaleY = 1f

    /** Screen pixels, scale applied. */
    var lastWidth: Float = 0f
        private set
    var lastHeight: Float = 0f
        private set

    /**
     * Anchor plus offset scaled like the element, so layouts keep their proportions on a smaller window.
     * Clamped here only - never write the clamp back to [offsetX], or saved positions get destroyed.
     */
    val x: Float get() = onScreenX(anchorBaseX() + offsetX * UiScaling.hudFactor)
    val y: Float get() = onScreenY(anchorBaseY() + offsetY * UiScaling.hudFactor)

    /**
     * This frame's content as a layout tree ([hudPanel], [hudText], `column`/`row`).
     * Build here, draw in the node: raw `Render` calls made while building land outside the element's
     * transform. With [example] (HUD editor) return sample content so there's something to grab.
     */
    abstract fun content(example: Boolean): LayoutNode

    fun renderElement(example: Boolean) {
        if (!example) {
            if (!toggle) return
            if (!shouldDraw) return
        }
        // Set for measuring as well as drawing; always cleared, even on a throw.
        HudFontContext.current = fontOverride
        // Uses last frame's bounds, so a resizing element lags the cursor by at most one frame.
        pointer = if (example) null else pointerInContent()
        try {
            val node = content(example)
            // Global multiplier is composed, not baked in, so saved offsets never move.
            val factor = UiScaling.hudFactor
            val sx = scaleX * factor
            val sy = scaleY * factor
            lastScaleX = sx
            lastScaleY = sy
            if (!example) lastDrawnAt = TimeSource.Monotonic.markNow()
            // CENTER/RIGHT anchors depend on size, so measure this frame's tree first.
            lastWidth = node.width * sx
            lastHeight = node.height * sy
            Platform.renderer.push()
            try {
                Platform.renderer.translate(x, y)
                if (sx != 1f || sy != 1f) Platform.renderer.scale(sx, sy)
                node.draw(0f, 0f)
            } finally {
                Platform.renderer.pop()
            }
        } finally {
            HudFontContext.current = FontChoice.INHERIT.name
            pointer = null
        }
    }

    /** Called by the host for every element; true if drawn recently, reachable, hit and [mouseClicked] consumed it. */
    fun offerClick(button: Int): Boolean {
        if (lastDrawnAt?.let { it.elapsedNow() <= STALE } != true) return false
        val at = pointerInContent() ?: return false
        return mouseClicked(MouseButtonInput(at.x, at.y, button))
    }

    private fun pointerInContent(): HudPointer? {
        if (HudEditorMode.active || lastWidth <= 0f || lastHeight <= 0f) return null
        val screens = Platform.screenHost
        if (!screens.chatOpen && !(drawsOverScreens && screens.anyScreenOpen)) return null
        val mx = Platform.window.mouseX - x
        val my = Platform.window.mouseY - y
        if (mx < 0f || my < 0f || mx > lastWidth || my > lastHeight) return null
        return HudPointer(mx / lastScaleX, my / lastScaleY)
    }

    /** Re-anchors to the nearest 3x3 cell, keeping the absolute position. */
    fun reanchorToNearest() {
        val sw = Platform.window.scaledWidth
        val sh = Platform.window.scaledHeight
        val cx = x + lastWidth / 2f
        val cy = y + lastHeight / 2f

        val newAnchorX = when {
            cx < sw / 3f -> AnchorX.LEFT
            cx > sw * 2f / 3f -> AnchorX.RIGHT
            else -> AnchorX.CENTER
        }
        val newAnchorY = when {
            cy < sh / 3f -> AnchorY.TOP
            cy > sh * 2f / 3f -> AnchorY.BOTTOM
            else -> AnchorY.MIDDLE
        }
        setPosition(x, y, newAnchorX, newAnchorY)
    }

    /** Stores the offset with scale divided out - the inverse of [x]. */
    fun setPosition(absX: Float, absY: Float, ax: AnchorX = anchorX, ay: AnchorY = anchorY) {
        anchorX = ax
        anchorY = ay
        val factor = UiScaling.hudFactor.takeIf { it > 0f } ?: 1f
        offsetX = (absX - anchorBaseX()) / factor
        offsetY = (absY - anchorBaseY()) / factor
    }

    private fun anchorBaseX(): Float {
        val sw = Platform.window.scaledWidth
        return when (anchorX) {
            AnchorX.LEFT -> 0f
            AnchorX.CENTER -> (sw - lastWidth) / 2f
            AnchorX.RIGHT -> sw - lastWidth
        }
    }

    private fun anchorBaseY(): Float {
        val sh = Platform.window.scaledHeight
        return when (anchorY) {
            AnchorY.TOP -> 0f
            AnchorY.MIDDLE -> (sh - lastHeight) / 2f
            AnchorY.BOTTOM -> sh - lastHeight
        }
    }

    private fun onScreenX(absX: Float): Float {
        if (lastWidth <= 0f) return absX
        return absX.coerceIn(0f, (Platform.window.scaledWidth - lastWidth).coerceAtLeast(0f))
    }

    private fun onScreenY(absY: Float): Float {
        if (lastHeight <= 0f) return absY
        return absY.coerceIn(0f, (Platform.window.scaledHeight - lastHeight).coerceAtLeast(0f))
    }

    val visibleInEditor: Boolean get() = HudEditorMode.active || toggle

    private companion object {
        /** A few frames at the lowest frame rate worth caring about. */
        val STALE = 250.milliseconds
    }
}

data class HudPointer(val x: Float, val y: Float)
