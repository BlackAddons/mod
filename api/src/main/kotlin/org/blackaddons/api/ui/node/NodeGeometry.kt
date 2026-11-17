package org.blackaddons.api.ui.node

import org.blackaddons.api.ui.layout.hit
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/** Single source for node sizes and socket positions, so drawing, indexing and hit-testing agree. */
object NodeMetrics {
    const val DEFAULT_WIDTH = 180f
    const val HEADER = 20f
    const val ROW = 16f
    const val PAD = 5f
    const val SUBTITLE = 14f
    const val RADIUS = 5f
    const val SOCKET_RADIUS = 4.5f

    /** Shift-drag snaps corners to it. */
    const val GRID = 25f

    /** Frame padding, and the title strip above. */
    const val GROUP_PAD = 14f
    const val GROUP_HEADER = 22f
    const val GROUP_FONT = 12f
    const val GROUP_RADIUS = 8f

    /** Viewport units. */
    const val SOCKET_GRAB = 7f

    const val TITLE_FONT = 10.5f
    const val LABEL_FONT = 9f
    const val SUBTITLE_FONT = 9f

    const val TOGGLE_W = 16f
    const val TOGGLE_H = 9f

    /** World units. */
    const val TOGGLE_GRAB = 2f

    const val PROPERTY_ROW = 16f
    const val FIELD_H = 13f

    /** Label share of a row. */
    const val LABEL_SHARE = 0.4f

    const val ARROW_W = 10f

    fun height(node: GraphNode): Float =
        height(max(node.inputs.size, node.outputs.size), node.subtitle.isNotEmpty(), node.properties.size)

    /** Height for a node that doesn't exist yet. */
    fun height(rows: Int, subtitle: Boolean, properties: Int): Float =
        HEADER + PAD + rows * ROW + (if (subtitle) SUBTITLE else 0f) + properties * PROPERTY_ROW + PAD

    fun propertiesTop(node: GraphNode): Float {
        val rows = max(node.inputs.size, node.outputs.size)
        val subtitle = if (node.subtitle.isEmpty()) 0f else SUBTITLE
        return node.y + HEADER + PAD + rows * ROW + subtitle
    }

    fun fieldY(node: GraphNode, index: Int): Float = propertiesTop(node) + index * PROPERTY_ROW + (PROPERTY_ROW - FIELD_H) / 2f

    fun fieldX(node: GraphNode, property: NodeProperty): Float =
        node.x + PAD + if (property.labelBeside) (node.width - PAD * 2f) * LABEL_SHARE else 0f

    fun fieldRight(node: GraphNode): Float = node.x + node.width - PAD

    const val BUTTON_GAP = 4f

    fun buttonWidth(node: GraphNode, property: NodeProperty.Buttons): Float {
        val count = property.buttons.size
        return (fieldRight(node) - fieldX(node, property) - BUTTON_GAP * (count - 1)) / count
    }

    /** -1 between buttons. */
    fun buttonAt(node: GraphNode, property: NodeProperty.Buttons, px: Float): Int {
        val each = buttonWidth(node, property)
        val offset = px - fieldX(node, property)
        val index = (offset / (each + BUTTON_GAP)).toInt()
        val inside = offset >= 0f && offset - index * (each + BUTTON_GAP) < each
        return if (inside && index < property.buttons.size) index else -1
    }

    /** -1 if none. */
    fun propertyAt(node: GraphNode, px: Float, py: Float): Int {
        if (node.properties.isEmpty() || px < node.x + PAD || px >= fieldRight(node)) return -1
        val offset = py - propertiesTop(node)
        if (offset < 0f) return -1
        val index = (offset / PROPERTY_ROW).toInt()
        return if (index < node.properties.size) index else -1
    }

    fun socketY(node: GraphNode, slot: Int): Float = node.y + HEADER + PAD + slot * ROW + ROW / 2f

    fun inputX(node: GraphNode): Float = node.x

    fun outputX(node: GraphNode): Float = node.x + node.width

    fun toggleX(node: GraphNode): Float = node.x + node.width - PAD - TOGGLE_W

    fun toggleY(node: GraphNode): Float = node.y + (HEADER - TOGGLE_H) / 2f

    /** False when it has no switch. */
    fun toggleHit(node: GraphNode, px: Float, py: Float): Boolean = node.toggleable && hit(
        px, py, toggleX(node) - TOGGLE_GRAB, toggleY(node) - TOGGLE_GRAB, TOGGLE_W + TOGGLE_GRAB * 2f, TOGGLE_H + TOGGLE_GRAB * 2f,
    )

    /** [frame] as `NodeGraph.groupBounds` fills it. */
    fun groupToggleX(frame: FloatArray): Float = frame[2] - GROUP_PAD - TOGGLE_W

    fun groupToggleY(frame: FloatArray): Float = frame[1] + (GROUP_HEADER - TOGGLE_H) / 2f

    /** False when it has no switch. */
    fun groupToggleHit(group: NodeGroup, frame: FloatArray, px: Float, py: Float): Boolean = group.switchedOn != null && hit(
        px, py, groupToggleX(frame) - TOGGLE_GRAB, groupToggleY(frame) - TOGGLE_GRAB, TOGGLE_W + TOGGLE_GRAB * 2f, TOGGLE_H + TOGGLE_GRAB * 2f,
    )
}

/** Handles grow with distance; backwards wires get wider handles so they loop around rather than cut through nodes. */
object WireGeometry {

    private const val MIN_HANDLE = 24f
    private const val MAX_HANDLE = 180f

    /** For hit-testing. */
    const val FLATTEN_STEPS = 16

    /** `x0 y0 c1x c1y c2x c2y x3 y3` into [out] (size >= 8). */
    fun controls(x0: Float, y0: Float, x3: Float, y3: Float, out: FloatArray): FloatArray {
        val dx = x3 - x0
        var handle = (abs(dx) * 0.5f).coerceIn(MIN_HANDLE, MAX_HANDLE)
        if (dx < 0f) handle = min(MAX_HANDLE, handle + abs(y3 - y0) * 0.25f + MIN_HANDLE)
        out[0] = x0; out[1] = y0
        out[2] = x0 + handle; out[3] = y0
        out[4] = x3 - handle; out[5] = y3
        out[6] = x3; out[7] = y3
        return out
    }

    /** Hull of all four control points, since a doubled-back wire bulges past its ends. */
    fun bounds(c: FloatArray, margin: Float, out: FloatArray): FloatArray {
        out[0] = min(min(c[0], c[2]), min(c[4], c[6])) - margin
        out[1] = min(min(c[1], c[3]), min(c[5], c[7])) - margin
        out[2] = max(max(c[0], c[2]), max(c[4], c[6])) + margin
        out[3] = max(max(c[1], c[3]), max(c[5], c[7])) + margin
        return out
    }

    fun pointX(c: FloatArray, t: Float): Float = cubic(c[0], c[2], c[4], c[6], t)

    fun pointY(c: FloatArray, t: Float): Float = cubic(c[1], c[3], c[5], c[7], t)

    /** Against the flattened polyline. */
    fun distanceSq(c: FloatArray, px: Float, py: Float): Float {
        var best = Float.MAX_VALUE
        var ax = c[0]
        var ay = c[1]
        for (i in 1..FLATTEN_STEPS) {
            val t = i.toFloat() / FLATTEN_STEPS
            val bx = pointX(c, t)
            val by = pointY(c, t)
            best = min(best, segmentDistanceSq(px, py, ax, ay, bx, by))
            ax = bx
            ay = by
        }
        return best
    }

    fun segmentDistanceSq(px: Float, py: Float, ax: Float, ay: Float, bx: Float, by: Float): Float {
        val dx = bx - ax
        val dy = by - ay
        val lengthSq = dx * dx + dy * dy
        val t = if (lengthSq == 0f) 0f else (((px - ax) * dx + (py - ay) * dy) / lengthSq).coerceIn(0f, 1f)
        val cx = ax + dx * t - px
        val cy = ay + dy * t - py
        return cx * cx + cy * cy
    }

    private fun cubic(p0: Float, p1: Float, p2: Float, p3: Float, t: Float): Float {
        val u = 1f - t
        return u * u * u * p0 + 3f * u * u * t * p1 + 3f * u * t * t * p2 + t * t * t * p3
    }
}
