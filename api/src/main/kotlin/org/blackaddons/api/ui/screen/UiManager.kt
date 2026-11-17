package org.blackaddons.api.ui.screen

import org.blackaddons.api.Platform
import org.blackaddons.api.log.Log
import org.blackaddons.api.ui.UiScaling
import org.blackaddons.api.ui.render.Render
import org.blackaddons.api.ui.screen.UiManager.REFERENCE_GUI_SCALE
import org.blackaddons.api.ui.screen.UiManager.renderOverlay
import org.blackaddons.api.ui.widget.CharInput
import org.blackaddons.api.ui.widget.KeyInput
import org.blackaddons.api.ui.widget.MouseButtonInput
import org.blackaddons.api.ui.widget.MouseDragInput
import org.blackaddons.api.ui.widget.MouseScrollInput
import org.blackaddons.api.ui.widget.UiFrame
import org.blackaddons.api.ui.widget.UiMouse
import org.blackaddons.api.ui.widget.dropdown.DropdownManager
import org.blackaddons.api.util.I18n
import java.nio.file.Path

/** The mod's [Panel] stack, hosted inside one thin game screen ([org.blackaddons.api.gui.ScreenHost]). */
object UiManager {

    private val stack = ArrayDeque<Panel>()

    /**
     * Latched from press to release: the Interface Scale slider lives in a panel it rescales, so a live value
     * would move the track under the cursor.
     */
    private var latchedInterfaceFactor = 1f

    /**
     * Refreshed on every read between interactions, not only when a panel draws: the title screen reads it with no
     * panel open, and a value latched at startup was the size of the first, tiny window.
     */
    private val interfaceFactor: Float
        get() {
            if (!pointerHeld) latchedInterfaceFactor = UiScaling.interfaceFactor
            return latchedInterfaceFactor
        }

    /** A button is down, so the latch must not refresh. */
    private var pointerHeld = false

    /** Null when the UI is closed. */
    val current: Panel? get() = stack.lastOrNull()

    val isOpen: Boolean get() = stack.isNotEmpty()

    /**
     * Draws from the deepest visible panel up (see [Panel.showsBelow]), then the host draws its extras, then
     * [renderOverlay]. Only the top panel gets the real cursor.
     */
    fun render(mouseX: Float, mouseY: Float, deltaTicks: Float) {
        var first = stack.lastIndex
        while (first > 0 && stack[first].showsBelow) first--

        // Removed after the loop so the indices stay valid.
        val failed = ArrayList<Pair<Panel, Throwable>>(0)
        UiFrame.drawing(next = true) {
            for (i in first until stack.lastIndex) {
                val panel = stack[i]
                guarded(panel, failed) { draw(panel, OFFSCREEN, OFFSCREEN) { mx, my -> panel.render(mx, my, deltaTicks) } }
            }
            current?.let { panel ->
                guarded(panel, failed) { draw(panel, mouseX, mouseY) { mx, my -> panel.render(mx, my, deltaTicks) } }
            }
        }
        failed.forEach { (panel, error) -> discard(panel, "render", error) }
    }

    private fun guarded(panel: Panel, failed: MutableList<Pair<Panel, Throwable>>, body: () -> Unit) {
        try {
            Render.isolated(body)
        } catch (t: Throwable) {
            failed += panel to t
        }
    }

    // Minecraft crash-reports a screen exception, so a throwing panel (likely an addon's) is logged and closed instead.
    private fun discard(panel: Panel, what: String, error: Throwable) {
        Log.main.error("Panel ${panel::class.java.name} threw during $what - closing it", error)
        if (!stack.remove(panel)) return
        DropdownManager.closeAll()
        Tooltip.reset()
        try {
            panel.onClose()
        } catch (t: Throwable) {
            Log.main.error("Panel ${panel::class.java.name} threw while closing", t)
        }
        if (stack.isEmpty()) Platform.screenHost.hide()
    }

    /**
     * Panel units -> Minecraft scaled units. Panels are windows, so [Theme] sizes are pinned to
     * [REFERENCE_GUI_SCALE] regardless of GUI Scale; [UiScaling.interfaceFactor] goes on top.
     * [Panel.normalizeScale] opts out.
     */
    private fun scaleOf(panel: Panel): Float =
        if (panel.normalizeScale) REFERENCE_GUI_SCALE / Platform.window.guiScale * interfaceFactor else 1f

    /**
     * In the current panel's units. Size panels with these, not [org.blackaddons.api.gui.WindowMetrics], or
     * they're wrong at every non-reference GUI Scale.
     */
    val screenWidth: Float get() = Platform.window.scaledWidth / currentScale()

    val screenHeight: Float get() = Platform.window.scaledHeight / currentScale()

    /** Framebuffer pixels per panel unit. */
    val pixelScale: Float get() = Platform.window.guiScale * currentScale()

    private fun currentScale(): Float = current?.let(::scaleOf) ?: 1f

    /** The pointer moves with the canvas, since widgets read [UiMouse] for hover while drawing. */
    private inline fun draw(panel: Panel, mouseX: Float, mouseY: Float, body: (Float, Float) -> Unit) {
        val scale = scaleOf(panel)
        UiMouse.set(mouseX / scale, mouseY / scale)
        if (scale == 1f) {
            body(mouseX, mouseY)
            return
        }
        Render.push()
        Render.scale(scale, scale)
        try {
            body(mouseX / scale, mouseY / scale)
        } finally {
            Render.pop()
        }
    }

    /**
     * Input comes here in window space, so panel space is applied in one place; hit-testing uses Rects stamped in that
     * space.
     */

    fun mouseClicked(mouse: MouseButtonInput): Boolean {
        pointerHeld = true
        return withPanel("mouseClicked") { panel, k -> panel.mouseClicked(mouse.at(mouse.x / k, mouse.y / k)) }
    }

    fun mouseReleased(mouse: MouseButtonInput): Boolean {
        pointerHeld = false
        return withPanel("mouseReleased") { panel, k -> panel.mouseReleased(mouse.at(mouse.x / k, mouse.y / k)) }
    }

    /** Deltas are distances, so they convert too. */
    fun mouseDragged(drag: MouseDragInput): Boolean =
        withPanel("mouseDragged") { panel, k ->
            panel.mouseDragged(MouseDragInput(drag.x / k, drag.y / k, drag.button, drag.dx / k, drag.dy / k))
        }

    fun mouseScrolled(wheel: MouseScrollInput): Boolean =
        withPanel("mouseScrolled") { panel, k -> panel.mouseScrolled(wheel.at(wheel.x / k, wheel.y / k)) }

    fun keyPressed(key: KeyInput): Boolean =
        withPanel("keyPressed") { panel, _ -> panel.keyPressed(key) }

    fun charTyped(typed: CharInput): Boolean =
        withPanel("charTyped") { panel, _ -> panel.charTyped(typed) }

    fun filesDropped(paths: List<Path>): Boolean =
        withPanel("filesDropped") { panel, _ -> panel.filesDropped(paths) }

    // Consumed if the panel throws: the event was meant for it.
    private inline fun withPanel(what: String, event: (Panel, Float) -> Boolean): Boolean {
        val panel = current ?: return false
        return try {
            event(panel, scaleOf(panel))
        } catch (t: Throwable) {
            discard(panel, what, t)
            true
        }
    }

    /**
     * Open dropdowns, then the [Tooltip]. Top panel only, so a dropdown behind a modal can't float over it.
     */
    fun renderOverlay(mouseX: Float, mouseY: Float) {
        val panel = current ?: return
        // The tooltip is positioned in the space its widget was drawn in.
        val failed = ArrayList<Pair<Panel, Throwable>>(0)
        UiFrame.drawing(next = false) {
            guarded(panel, failed) {
                draw(panel, mouseX, mouseY) { mx, my ->
                    panel.renderOverlay(mx, my)
                    Tooltip.flush(mx, my)
                }
            }
        }
        failed.forEach { (failedPanel, error) -> discard(failedPanel, "renderOverlay", error) }
    }

    // The render loop walks the stack, so every mutation hops to the client thread.
    private inline fun onClient(crossinline block: () -> Unit) = Platform.screenHost.onClientThread { block() }

    /** Replaces anything already shown. Thread-safe. */
    fun open(panel: Panel) = onClient {
        clearStack()
        stack.addLast(panel)
        panel.onShow()
        Platform.screenHost.show()
    }

    /** Thread-safe. */
    fun push(panel: Panel) = onClient {
        val wasClosed = stack.isEmpty()
        stack.addLast(panel)
        panel.onShow()
        if (wasClosed) Platform.screenHost.show()
    }

    fun confirm(
        title: String,
        message: String = I18n.trOr("blackaddons.confirm.sure", "Are you sure?"),
        danger: Boolean = true,
        onConfirm: () -> Unit,
    ) {
        push(ConfirmModal(title = title, message = message, danger = danger, onConfirm = onConfirm))
    }

    /** Pinned to a control, or at the cursor. */
    fun confirmPopover(
        anchorX: Float = UiMouse.x,
        anchorY: Float = UiMouse.y,
        anchorW: Float = 0f,
        anchorH: Float = 0f,
        message: String = I18n.trOr("blackaddons.confirm.sure", "Are you sure?"),
        danger: Boolean = true,
        onConfirm: () -> Unit,
    ) {
        push(ConfirmPopover(anchorX, anchorY, anchorW, anchorH, message = message, danger = danger, onConfirm = onConfirm))
    }

    /**
     * Opens at the cursor. [UiMouse] is in the units of the panel that opens it, and a panel that is not normalized
     * does not share them with the menu.
     */
    fun contextMenu(entries: List<ContextMenu.Entry>) {
        val toMenu = currentScale() / normalizedScale
        push(ContextMenu(entries, UiMouse.x * toMenu, UiMouse.y * toMenu))
    }

    /**
     * Opens over the game's current screen without replacing it (e.g. a menu raised from chat). Thread-safe.
     */
    fun overlay(panel: Panel) = onClient {
        val wasClosed = stack.isEmpty()
        stack.addLast(panel)
        panel.onShow()
        if (wasClosed) Platform.screenHost.showOver()
    }

    /** [x], [y] in game GUI units; [UiMouse] doesn't know the cursor yet since nothing of ours has drawn. */
    fun contextMenuOver(x: Float, y: Float, entries: List<ContextMenu.Entry>) {
        val scale = normalizedScale
        overlay(ContextMenu(entries, x / scale, y / scale))
    }

    /** Game GUI units per normalized panel unit, for drawing toolkit widgets on vanilla screens. */
    val normalizedScale: Float get() = REFERENCE_GUI_SCALE / Platform.window.guiScale * interfaceFactor

    /** Without invoking the screen host. */
    fun pushSilently(panel: Panel) = onClient {
        stack.addLast(panel)
        panel.onShow()
    }

    /** Hides the host once the stack empties. Thread-safe. */
    fun pop() = onClient {
        DropdownManager.closeAll()
        stack.removeLastOrNull()?.onClose()
        if (stack.isEmpty()) Platform.screenHost.hide()
    }

    /** Thread-safe. */
    fun close() = onClient {
        clearStack()
        Platform.screenHost.hide()
    }

    /** Called by the host whenever the game removes it (Esc, or another screen replacing it). Doesn't re-hide. */
    fun onHostClosed() {
        clearStack()
    }

    private fun clearStack() {
        // A screen closing under a held button never delivers the release; a stuck latch would freeze the scale.
        pointerHeld = false
        DropdownManager.closeAll()
        while (stack.isNotEmpty()) stack.removeLast().onClose()
        Tooltip.reset()
    }

    /** Far enough outside any window that nothing reads it as a hover. */
    private const val OFFSCREEN = -10_000f

    /** The GUI Scale [org.blackaddons.api.ui.theme.Theme] sizes are authored for. */
    private const val REFERENCE_GUI_SCALE = 2f
}
