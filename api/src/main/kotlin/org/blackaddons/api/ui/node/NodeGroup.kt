package org.blackaddons.api.ui.node

import java.awt.Color

/** Colored frame behind its nodes. A node is in at most one group; removed nodes stay listed so undo restores them. */
class NodeGroup internal constructor(val id: Int, title: String, color: Color) {
    internal var graph: NodeGraph? = null
    internal val members = LinkedHashSet<Int>()

    val memberIds: Set<Int> get() = members

    var title: String = title
        set(value) {
            if (field == value) return
            field = value
            graph?.groupChanged()
        }

    var color: Color = color
        set(value) {
            if (field == value) return
            field = value
            graph?.groupChanged()
        }

    /** Header switch state; null hides it. Clicks call `NodeCanvas.onGroupToggle`. */
    var switchedOn: Boolean? = null
        set(value) {
            if (field == value) return
            field = value
            graph?.groupChanged()
        }

    /** Host data; the graph never reads it. */
    var payload: Any? = null

    val isAttached: Boolean get() = graph != null
}
