package org.blackaddons.core.features.impl.ui.inventorybuttons

import org.blackaddons.api.gui.GuiBox
import kotlin.math.min
import kotlin.math.roundToInt

internal enum class ButtonRuns { COMMAND, SHORTCUT }

internal enum class ButtonScope { INVENTORY, ALL_CONTAINERS, TITLE }

// x and y are offsets from the container's left or right and top or bottom edges.
internal data class InventoryButton(
    var x: Int = 0,
    var y: Int = 0,
    var anchorRight: Boolean = false,
    var anchorBottom: Boolean = false,
    var width: Int = DEFAULT_SIZE,
    var height: Int = DEFAULT_SIZE,
    // A Skyblock id, skull:<hash or textures value>, a vanilla id, or /give syntax.
    var icon: String = "",
    var label: String = "",
    var runs: ButtonRuns = ButtonRuns.COMMAND,
    // Without the leading slash.
    var command: String = "",
    var shortcutId: String = "",
    var scope: ButtonScope = ButtonScope.INVENTORY,
    // For ButtonScope.TITLE: part of the container title, any case.
    var titleFilter: String = "",
    // Placed on the GUI on purpose, so it isn't pushed off it.
    var insideGui: Boolean = false,
) {
    companion object {
        const val DEFAULT_SIZE = 18
        const val MIN_SIZE = 10
        const val MAX_SIZE = 256
    }
}

// Pushed off the GUI unless it was placed on it, then kept on screen.
internal fun placeButton(button: InventoryButton, gui: GuiBox, screenW: Int, screenH: Int): GuiBox {
    val x = (if (button.anchorRight) gui.right else gui.x) + button.x
    val y = (if (button.anchorBottom) gui.bottom else gui.y) + button.y
    val box = GuiBox(x, y, button.width, button.height)
    return if (button.insideGui) keepOnScreen(box, screenW, screenH) else placeOutside(box, gui, screenW, screenH)
}

/*
 * Beside the GUI on the nearest side it fits; with no room on any side, shrunk into the side where it can be biggest,
 * so a big button in a small window doesn't land on the slots. Display only, the saved size stays.
 */
internal fun placeOutside(box: GuiBox, gui: GuiBox, screenW: Int, screenH: Int): GuiBox {
    val kept = keepOnScreen(pushOut(box, gui), screenW, screenH)
    if (!kept.intersects(gui)) return kept
    val sides = freeSides(box, gui, screenW, screenH)
    sides.firstOrNull { box.w <= it.w && box.h <= it.h }?.let { return clampInto(box, it) }
    val best = sides.maxBy { fitScale(box, it) }
    val scale = fitScale(box, best)
    if (scale <= 0f) return kept
    val w = (box.w * scale).roundToInt()
    val h = (box.h * scale).roundToInt()
    return clampInto(GuiBox(box.x + (box.w - w) / 2, box.y + (box.h - h) / 2, w, h), best)
}

// The screen strips above, below, left and right of the GUI, nearest to the box first.
private fun freeSides(box: GuiBox, gui: GuiBox, screenW: Int, screenH: Int): List<GuiBox> = listOf(
    box.bottom - gui.y to GuiBox(0, 0, screenW, gui.y),
    gui.bottom - box.y to GuiBox(0, gui.bottom, screenW, screenH - gui.bottom),
    box.right - gui.x to GuiBox(0, 0, gui.x, screenH),
    gui.right - box.x to GuiBox(gui.right, 0, screenW - gui.right, screenH),
).sortedBy { it.first }.map { it.second }

private fun fitScale(box: GuiBox, area: GuiBox): Float = min(area.w.toFloat() / box.w, area.h.toFloat() / box.h)

private fun clampInto(box: GuiBox, area: GuiBox): GuiBox = box.copy(
    x = box.x.coerceIn(area.x, (area.right - box.w).coerceAtLeast(area.x)),
    y = box.y.coerceIn(area.y, (area.bottom - box.h).coerceAtLeast(area.y)),
)

// A button dropped with its center on the GUI stays there; otherwise it is pushed off.
internal fun settleButton(button: InventoryButton, at: GuiBox, gui: GuiBox, screenW: Int, screenH: Int) {
    button.insideGui = gui.has(at.x + at.w / 2, at.y + at.h / 2)
    val box = if (button.insideGui) at else pushOut(at, gui)
    anchorTo(button, keepOnScreen(box, screenW, screenH), gui)
}

// The shortest way off the GUI, so a button made next to a small GUI never covers a bigger one.
internal fun pushOut(box: GuiBox, gui: GuiBox): GuiBox {
    if (!box.intersects(gui)) return box
    val toLeft = box.right - gui.x
    val toRight = gui.right - box.x
    val up = box.bottom - gui.y
    val down = gui.bottom - box.y
    return when (minOf(toLeft, toRight, up, down)) {
        up -> box.copy(y = gui.y - box.h)
        down -> box.copy(y = gui.bottom)
        toLeft -> box.copy(x = gui.x - box.w)
        else -> box.copy(x = gui.right)
    }
}

internal fun keepOnScreen(box: GuiBox, screenW: Int, screenH: Int): GuiBox = box.copy(
    x = box.x.coerceIn(0, (screenW - box.w).coerceAtLeast(0)),
    y = box.y.coerceIn(0, (screenH - box.h).coerceAtLeast(0)),
)

// Anchors to the GUI edges nearest the button's center.
internal fun anchorTo(button: InventoryButton, at: GuiBox, gui: GuiBox) {
    button.anchorRight = at.x * 2 + at.w > gui.x * 2 + gui.w
    button.anchorBottom = at.y * 2 + at.h > gui.y * 2 + gui.h
    button.x = at.x - if (button.anchorRight) gui.right else gui.x
    button.y = at.y - if (button.anchorBottom) gui.bottom else gui.y
}

// Grid lines start at the GUI's corner, so buttons line up with it.
internal fun snapToGrid(value: Int, origin: Int, grid: Int): Int = origin + ((value - origin).toFloat() / grid).roundToInt() * grid
