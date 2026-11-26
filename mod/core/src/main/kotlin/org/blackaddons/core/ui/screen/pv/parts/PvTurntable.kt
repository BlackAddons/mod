package org.blackaddons.core.ui.screen.pv.parts

import org.blackaddons.api.Platform
import org.blackaddons.api.gui.PlayerModelPreview
import org.blackaddons.api.gui.PreviewGear
import org.blackaddons.api.skyblock.profile.PlayerProfiles
import org.blackaddons.api.ui.render.Render
import org.blackaddons.api.ui.theme.TextStyle
import org.blackaddons.api.ui.theme.Theme
import org.blackaddons.api.util.I18n
import org.blackaddons.core.profile.SkinTextures
import org.blackaddons.core.profile.dashless
import org.blackaddons.core.ui.screen.ModelTurntable

private val tr = I18n.scope("blackaddons.pv")

/** The player's 3D model in a framed box: spins slowly by itself, and dragging turns and tilts it. */
internal class PvTurntable(private val key: Any = PlayerModelPreview.MAIN) : ModelTurntable() {

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

    private companion object {
        const val AUTO_SPIN_PER_TICK = 0.33f
    }
}
