package org.blackaddons.core.ui.screen.cosmetic

import org.blackaddons.api.Platform
import org.blackaddons.api.ui.layout.centered
import org.blackaddons.api.ui.render.Render
import org.blackaddons.api.ui.theme.Fonts
import org.blackaddons.api.ui.theme.TextStyle
import org.blackaddons.api.ui.theme.Theme
import org.blackaddons.api.ui.theme.Theme.withAlpha
import org.blackaddons.api.util.I18n
import org.blackaddons.core.ui.screen.ModelTurntable

/** Draggable player model both editor tabs share, so the turntable state lives here. */
internal class ModelPreviewBox : ModelTurntable() {

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
}
