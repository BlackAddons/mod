package org.blackaddons.core.features.impl.ui.inventorybuttons

import org.blackaddons.api.gui.GuiBox
import kotlin.math.abs

internal data class Snapped(val x: Int, val y: Int, val guideX: Int?, val guideY: Int?)

// Edges snap to edges and centers to centers, per axis, to the nearest target within reach.
internal fun snapToNeighbors(box: GuiBox, targets: List<GuiBox>, reach: Int): Snapped {
    val (x, guideX) = snapAxis(box.x, box.w, targets.map { Span(it.x, it.w) }, reach)
    val (y, guideY) = snapAxis(box.y, box.h, targets.map { Span(it.y, it.h) }, reach)
    return Snapped(x, y, guideX, guideY)
}

private class Span(val start: Int, val size: Int)

private fun snapAxis(start: Int, size: Int, targets: List<Span>, reach: Int): Pair<Int, Int?> {
    var best = start
    var guide: Int? = null
    var bestDistance = reach + 1
    fun consider(line: Int, offset: Int) {
        val distance = abs(start + offset - line)
        if (distance < bestDistance) {
            bestDistance = distance
            best = line - offset
            guide = line
        }
    }
    for (target in targets) {
        for (line in intArrayOf(target.start, target.start + target.size)) {
            consider(line, 0)
            consider(line, size)
        }
        consider(target.start + target.size / 2, size / 2)
    }
    return best to guide
}
