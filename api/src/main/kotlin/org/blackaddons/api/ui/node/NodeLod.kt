package org.blackaddons.api.ui.node

/** How much of a node is drawn at a zoom level. */
enum class NodeDetail {
    /** Everything. */
    FULL,

    /** Labels and subtitles dropped. */
    MEDIUM,

    /** Batched flat boxes and curved wires; no text or sockets. */
    LOW,

    /** Batched colored boxes and 1px straight wires. */
    OVERVIEW,
}

/** With hysteresis, so a zoom on a cut-off doesn't flicker. */
class NodeLod(
    private val fullAt: Double = 0.45,
    private val mediumAt: Double = 0.25,
    private val lowAt: Double = 0.15,
    private val band: Double = 0.05,
) {
    var detail: NodeDetail = NodeDetail.FULL
        private set

    /** Only crosses a cut-off once clearly past it. */
    fun update(zoom: Double): NodeDetail {
        val wanted = detailFor(zoom)
        if (wanted == detail) return detail
        val coarser = wanted.ordinal > detail.ordinal
        val confirmed = if (coarser) detailFor(zoom * (1.0 + band)) else detailFor(zoom * (1.0 - band))
        if (coarser && confirmed.ordinal > detail.ordinal) detail = confirmed
        if (!coarser && confirmed.ordinal < detail.ordinal) detail = confirmed
        return detail
    }

    fun detailFor(zoom: Double): NodeDetail = when {
        zoom >= fullAt -> NodeDetail.FULL
        zoom >= mediumAt -> NodeDetail.MEDIUM
        zoom >= lowAt -> NodeDetail.LOW
        else -> NodeDetail.OVERVIEW
    }
}
