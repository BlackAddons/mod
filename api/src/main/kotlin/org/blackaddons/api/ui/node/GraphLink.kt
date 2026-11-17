package org.blackaddons.api.ui.node

/** Stable across undo, unlike the link id. */
data class LinkSpec(val from: Int, val fromSlot: Int, val to: Int, val toSlot: Int)

/** Output ([from], [fromSlot]) -> input ([to], [toSlot]). */
class GraphLink internal constructor(
    val id: Int,
    val from: Int,
    val fromSlot: Int,
    val to: Int,
    val toSlot: Int,
    val type: SocketType,
) {
    /** Control points in world units, kept current as nodes move. */
    val curve = FloatArray(8)

    val bounds = FloatArray(4)

    val spec: LinkSpec get() = LinkSpec(from, fromSlot, to, toSlot)
}
