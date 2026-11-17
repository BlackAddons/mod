package org.blackaddons.api.ui.node

import java.awt.Color

/**
 * Inputs on the left, outputs on the right, in world units. Move through [NodeGraph.moveTo] /
 * [NodeGraph.moveBy] so the index and wires follow; visual fields can be set directly.
 */
class GraphNode internal constructor(
    val id: Int,
    inputs: List<SocketSpec>,
    outputs: List<SocketSpec>,
    x: Float,
    y: Float,
    width: Float,
) {
    internal var graph: NodeGraph? = null

    var inputs: List<SocketSpec> = inputs
        internal set
    var outputs: List<SocketSpec> = outputs
        internal set

    var x: Float = x
        internal set
    var y: Float = y
        internal set
    var width: Float = width
        set(value) {
            if (field == value) return
            field = value
            graph?.geometryChanged(this)
        }
    var height: Float = 0f
        internal set

    var title: String = ""
        set(value) {
            if (field == value) return
            field = value
            graph?.styleChanged(this)
        }

    /** Empty for none. Changes the height. */
    var subtitle: String = ""
        set(value) {
            if (field == value) return
            val rowChange = field.isEmpty() != value.isEmpty()
            field = value
            if (rowChange) graph?.geometryChanged(this) else graph?.styleChanged(this)
        }

    /** Changes the height. [NodeProperty.Note]s are dropped, because a node body has no room for help text. */
    var properties: List<NodeProperty> = emptyList()
        set(value) {
            field = value.filterNot { it is NodeProperty.Note }
            graph?.geometryChanged(this)
        }

    var headerColor: Color = Color(0x4A4F6B)
        set(value) {
            if (field == value) return
            field = value
            graph?.styleChanged(this)
        }

    /** Drawn faded. */
    var dimmed: Boolean = false
        set(value) {
            if (field == value) return
            field = value
            graph?.styleChanged(this)
        }

    /** Header switch, on while not [dimmed]; clicks call `NodeCanvas.onToggle`. */
    var toggleable: Boolean = false
        set(value) {
            if (field == value) return
            field = value
            graph?.styleChanged(this)
        }

    var warning: String? = null
        set(value) {
            if (field == value) return
            field = value
            graph?.styleChanged(this)
        }

    var deletable: Boolean = true

    /** Host data; the graph never reads it. */
    var payload: Any? = null

    /** Bumped on every visual change for caches. */
    var revision: Int = 0
        internal set

    internal val links = IntList(4)

    val isAttached: Boolean get() = graph != null

    val centerX: Float get() = x + width / 2f
    val centerY: Float get() = y + height / 2f

    fun contains(px: Float, py: Float): Boolean = px >= x && py >= y && px < x + width && py < y + height
}
