package org.blackaddons.core.ui.screen.cosmetic

import org.blackaddons.api.Platform
import org.blackaddons.api.ui.layout.Rect
import org.blackaddons.api.ui.layout.centered
import org.blackaddons.api.ui.render.Render
import org.blackaddons.api.ui.theme.Fonts
import org.blackaddons.api.ui.theme.TextStyle
import org.blackaddons.api.ui.theme.Theme
import org.blackaddons.api.ui.theme.Theme.withAlpha
import org.blackaddons.api.ui.widget.InputTarget
import org.blackaddons.api.ui.widget.MouseButtonInput
import org.blackaddons.api.ui.widget.MouseDragInput
import org.blackaddons.api.util.I18n

/** Draggable player model both editor tabs share, so the turntable state lives here. */
internal class ModelPreviewBox : InputTarget {

    private val box = Rect()
    private var yaw = 0f
    private var pitch = 0f
    private var dragging = false

    fun render(x: Float, y: Float, w: Float, h: Float, scaleX: Float, scaleY: Float, scaleZ: Float) {
        Render.rect(x, y, w, h, Theme.SURFACE.withAlpha(80), Theme.RADIUS_SMALL)
        Render.hollowRect(x, y, w, h, 1f, Theme.BORDER, Theme.RADIUS_SMALL)

        val preview = Platform.playerPreview
        if (preview == null) {
            val msg = I18n.trOr("blackaddons.cosmetic_editor.no_model", "Model preview unavailable")
            val tw = Render.textWidth(msg, Theme.FONT_SMALL, Fonts.regular)
            Render.text(msg, centered(x, w, tw), y + h / 2f, TextStyle.SMALL_MUTED)
            return
        }
        // Set while drawing, so the hitbox matches the last paint.
        box.set(x, y, w, h)
        preview.request(x, y, w, h, scaleX, scaleY, scaleZ, yaw, pitch)
    }

    override fun mouseClicked(mouse: MouseButtonInput): Boolean {
        if (!mouse.isLeft || Platform.playerPreview == null || !box.has(mouse.x, mouse.y)) return false
        dragging = true
        return true
    }

    override fun mouseDragged(drag: MouseDragInput): Boolean {
        if (!dragging) return false
        yaw -= drag.dx * DRAG_SENS
        pitch = (pitch - drag.dy * DRAG_SENS).coerceIn(-PITCH_LIMIT, PITCH_LIMIT)
        return true
    }

    override fun mouseReleased(mouse: MouseButtonInput): Boolean {
        dragging = false
        return false
    }

    private companion object {
        const val DRAG_SENS = 0.6f
        const val PITCH_LIMIT = 30f
    }
}
