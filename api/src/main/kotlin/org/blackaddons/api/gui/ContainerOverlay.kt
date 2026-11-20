package org.blackaddons.api.gui

import org.blackaddons.api.ui.layout.hit
import org.blackaddons.api.ui.widget.KeyInput
import org.blackaddons.api.ui.widget.MouseButtonInput
import org.blackaddons.api.ui.widget.MouseDragInput
import org.blackaddons.api.ui.widget.MouseScrollInput

/** UIs drawn over container screens while the real container stays open underneath, so slots stay native. */
interface ContainerOverlayRegistry {
    /** Active whenever [ContainerOverlay.appliesTo] matches the open container. */
    fun register(overlay: ContainerOverlay)
}

interface ContainerOverlay {
    /** [title] is unformatted. */
    fun appliesTo(title: String): Boolean

    /** Replace the container instead of drawing over it. */
    fun suppressesVanilla(title: String): Boolean = true

    /** Leave the cursor where it was before the container opened; false lets vanilla center it. */
    fun keepsCursor(title: String): Boolean = suppressesVanilla(title)

    /** Drop the whole GUI so the inventory anchors to the bottom. For full replacements; the chest grid won't show. */
    fun repositionInventory(title: String): Boolean = false

    /** Toolkit layer via [org.blackaddons.api.ui.render.Render]; drawn under [render], items and tooltips. */
    fun renderUi(metrics: ContainerMetrics, mouseX: Int, mouseY: Int) {}

    /** Vanilla layer over [renderUi]. Pass [ctx] to [org.blackaddons.api.Platform.screenDraw]. */
    fun render(ctx: Any, metrics: ContainerMetrics, mouseX: Int, mouseY: Int)

    /** In GUI coordinates, like [metrics]. True consumes it. */
    fun mouseClicked(metrics: ContainerMetrics, mouse: MouseButtonInput): Boolean = false

    /** True consumes it. */
    fun mouseScrolled(metrics: ContainerMetrics, wheel: MouseScrollInput): Boolean = false

    /** Control keys (Esc/Enter/Backspace). True consumes it. */
    fun keyPressed(metrics: ContainerMetrics, key: KeyInput): Boolean = false

    /** Layout/IME-resolved codepoint - text entry goes here, not [keyPressed]. */
    fun charTyped(codepoint: Int): Boolean = false

    fun mouseDragged(metrics: ContainerMetrics, drag: MouseDragInput): Boolean = false

    fun mouseReleased(mouse: MouseButtonInput) {}

    /** What this overlay draws outside the container, so REI can keep clear of it. */
    fun exclusionZones(metrics: ContainerMetrics): List<GuiBox> = emptyList()
}

/**
 * Open container geometry in GUI pixels. `inventory*` bound the player inventory - leave it visible by
 * drawing around it. The platform may move it toward the bottom first.
 */
data class ContainerMetrics(
    val width: Int,
    val height: Int,
    val inventoryTop: Int,
    val inventoryLeft: Int,
    val inventoryRight: Int,
    val inventoryBottom: Int,
    /** The container's background box: `leftPos`, `topPos` and image size. */
    val gui: GuiBox,
    /** Another overlay hides the vanilla GUI and draws its own instead. */
    val replaced: Boolean,
)

/** In GUI pixels. */
data class GuiBox(val x: Int, val y: Int, val w: Int, val h: Int) {
    val right: Int get() = x + w
    val bottom: Int get() = y + h

    fun intersects(other: GuiBox): Boolean = x < other.right && other.x < right && y < other.bottom && other.y < bottom

    fun has(px: Int, py: Int): Boolean = hit(px, py, x, y, w, h)
}
