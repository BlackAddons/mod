package org.blackaddons.core.ui.screen.pv.parts

import org.blackaddons.api.Platform
import org.blackaddons.api.gui.PlayerModelPreview
import org.blackaddons.api.gui.PreviewGear
import org.blackaddons.api.skyblock.profile.PlayerProfiles
import org.blackaddons.api.ui.layout.Rect
import org.blackaddons.api.ui.render.Render
import org.blackaddons.api.ui.theme.TextStyle
import org.blackaddons.api.ui.theme.Theme
import org.blackaddons.api.ui.widget.InputTarget
import org.blackaddons.api.ui.widget.MouseButtonInput
import org.blackaddons.api.ui.widget.MouseDragInput
import org.blackaddons.api.util.I18n
import org.blackaddons.core.profile.SkinTextures
import org.blackaddons.core.profile.dashless

private val tr = I18n.scope("blackaddons.pv")

/** The player's 3D model in a framed box: spins slowly by itself, and dragging turns and tilts it. */
internal class PvTurntable(private val key: Any = PlayerModelPreview.MAIN) : InputTarget {

    private val box = Rect()
    private var dragging = false
    private var yaw = 0f
    private var pitch = 0f

    /** Drops the hitbox while the model isn't on screen, so nothing behind it stays draggable. */
    fun hide() {
        box.clear()
        dragging = false
    }

    fun draw(
        x: Float, y: Float, w: Float, h: Float,
        profiles: PlayerProfiles, textures: SkinTextures?, deltaTicks: Float, gear: PreviewGear? = null,
    ) {
        if (!dragging) yaw += AUTO_SPIN_PER_TICK * deltaTicks
        box.set(x, y, w, h)
        // Border only: the UI composites over the entity PiP, so a fill would dim the model.
        Render.hollowRect(x, y, w, h, 1f, Theme.BORDER, Theme.RADIUS)
        val preview = Platform.playerPreview
        if (preview == null) {
            Render.textCentered(tr("avatar", "Avatar"), x + w / 2f, Render.centerY(y, h, TextStyle.SMALL_MUTED), TextStyle.SMALL_MUTED)
            return
        }
        preview.requestPlayer(
            profiles.uuid.dashless, profiles.name, textures?.value, textures?.signature,
            x, y, w, h, yaw, pitch, key = key, gear = gear,
        )
    }

    override fun mouseClicked(mouse: MouseButtonInput): Boolean {
        if (Platform.playerPreview == null || !box.has(mouse.x, mouse.y)) return false
        dragging = true
        return true
    }

    override fun mouseReleased(mouse: MouseButtonInput): Boolean {
        dragging = false
        return false
    }

    override fun mouseDragged(drag: MouseDragInput): Boolean {
        if (!dragging) return false
        // Yaw has no limit. Pitch is clamped, so the model, anchored at its feet, can't swing away.
        yaw -= drag.dx * DRAG_SENS
        pitch = (pitch - drag.dy * DRAG_SENS).coerceIn(-PITCH_LIMIT, PITCH_LIMIT)
        return true
    }

    private companion object {
        const val AUTO_SPIN_PER_TICK = 0.33f
        const val DRAG_SENS = 0.6f
        const val PITCH_LIMIT = 30f
    }
}
