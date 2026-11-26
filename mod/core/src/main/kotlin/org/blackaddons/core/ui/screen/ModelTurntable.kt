package org.blackaddons.core.ui.screen

import org.blackaddons.api.Platform
import org.blackaddons.api.ui.layout.Rect
import org.blackaddons.api.ui.widget.InputTarget
import org.blackaddons.api.ui.widget.MouseButtonInput
import org.blackaddons.api.ui.widget.MouseDragInput

/** A player model box that dragging turns and tilts. Subclasses set [box] while drawing and draw at [yaw] and [pitch]. */
internal abstract class ModelTurntable : InputTarget {

    protected val box = Rect()
    protected var yaw = 0f
    protected var pitch = 0f
    protected var dragging = false

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
