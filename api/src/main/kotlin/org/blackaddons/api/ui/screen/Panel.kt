package org.blackaddons.api.ui.screen

import org.blackaddons.api.ui.widget.CharInput
import org.blackaddons.api.ui.widget.KeyInput
import org.blackaddons.api.ui.widget.Keymap
import org.blackaddons.api.ui.widget.MouseButtonInput
import org.blackaddons.api.ui.widget.MouseDragInput
import org.blackaddons.api.ui.widget.MouseScrollInput
import org.blackaddons.api.ui.widget.WidgetHost
import java.nio.file.Path

/**
 * Full-window screen in scaled GUI pixels, shown via [UiManager]. Input reaches [widgets] and [keys] by
 * default; override input methods only for screen-specific behavior and call `super`.
 */
abstract class Panel {

    protected val widgets = WidgetHost()

    /** Asked after the focused widget declines a key. */
    protected val keys = Keymap()

    /** Draw the panel beneath too. True for modal cards; [UiManager] draws down to the first false. */
    open val showsBelow: Boolean get() = false

    /** Draw in our own units, independent of GUI Scale. The HUD editor turns it off to share the game's coordinates. */
    open val normalizeScale: Boolean get() = true

    open fun onShow() {}

    abstract fun render(mouseX: Float, mouseY: Float, deltaTicks: Float)

    /** Above everything (dropdown lists, popups); runs after [render] and the host's own drawing. */
    open fun renderOverlay(mouseX: Float, mouseY: Float) {}

    open fun mouseClicked(mouse: MouseButtonInput): Boolean =
        widgets.mouseClicked(mouse)

    open fun mouseReleased(mouse: MouseButtonInput): Boolean =
        widgets.mouseReleased(mouse)

    open fun mouseDragged(drag: MouseDragInput): Boolean =
        widgets.mouseDragged(drag)

    open fun mouseScrolled(wheel: MouseScrollInput): Boolean =
        widgets.mouseScrolled(wheel)

    open fun keyPressed(key: KeyInput): Boolean =
        widgets.keyPressed(key) || keys.handle(key.code, key.modifiers)

    open fun charTyped(typed: CharInput): Boolean = widgets.charTyped(typed)

    /** Files dropped onto the window while this panel is on top. */
    open fun filesDropped(paths: List<Path>): Boolean = false

    open fun onClose() {}
}
