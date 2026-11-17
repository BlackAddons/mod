package org.blackaddons.api.hud

import org.blackaddons.api.HostApi
import org.blackaddons.api.hud.HudEditorMode.active


/** While [active], [HudElement.renderElement] skips toggle/shouldDraw so every HUD shows in the editor. */
object HudEditorMode {
    var active: Boolean = false
        @HostApi set
}
