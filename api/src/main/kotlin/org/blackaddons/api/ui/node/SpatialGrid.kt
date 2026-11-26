package org.blackaddons.api.ui.node

import org.blackaddons.api.util.packInts
import org.blackaddons.api.util.packedHigh
import org.blackaddons.api.util.packedLow
import kotlin.math.floor

/**
 * Uniform-grid spatial hash keeping the canvas O(visible). A grid because node boxes are similar in size;
 * moves within the same cells are free. Boxes over [MAX_CELLS] cells go on an always-checked list.
 */
class SpatialGrid(val cellSize: Float = DEFAULT_CELL) {

    private val cells = HashMap<Long, IntList>()
    private val large = IntList()

    private var cx0 = IntArray(64)
    private var cy0 = IntArray(64)
    private var cx1 = IntArray(64)
    private var cy1 = IntArray(64)
    private var present = BooleanArray(64)
    private var isLarge = BooleanArray(64)

    private var stamps = IntArray(64)
    private var stamp = 0

    var count: Int = 0
        private set

    fun contains(id: Int): Boolean = id < present.size && present[id]

    /** Adds or moves. */
    fun put(id: Int, minX: Float, minY: Float, maxX: Float, maxY: Float) {
        ensure(id)
        val nx0 = cell(minX)
        val ny0 = cell(minY)
        val nx1 = cell(maxX)
        val ny1 = cell(maxY)
        if (present[id]) {
            if (nx0 == cx0[id] && ny0 == cy0[id] && nx1 == cx1[id] && ny1 == cy1[id]) return
            unlink(id)
        } else {
            count++
        }
        present[id] = true
        cx0[id] = nx0; cy0[id] = ny0; cx1[id] = nx1; cy1[id] = ny1
        val span = (nx1.toLong() - nx0 + 1) * (ny1.toLong() - ny0 + 1)
        if (span > MAX_CELLS) {
            isLarge[id] = true
            large.add(id)
            return
        }
        isLarge[id] = false
        for (x in nx0..nx1) for (y in ny0..ny1) cells.getOrPut(packInts(x, y)) { IntList(4) }.add(id)
    }

    fun remove(id: Int) {
        if (!contains(id)) return
        unlink(id)
        present[id] = false
        count--
    }

    fun clear() {
        cells.clear()
        large.clear()
        present.fill(false)
        count = 0
    }

    /** Coarse: may include near misses, the caller tests exact boxes. Each id once. */
    fun query(minX: Float, minY: Float, maxX: Float, maxY: Float, out: IntList) {
        val qx0 = cell(minX)
        val qy0 = cell(minY)
        val qx1 = cell(maxX)
        val qy1 = cell(maxY)
        nextStamp()
        val area = (qx1.toLong() - qx0 + 1) * (qy1.toLong() - qy0 + 1)
        if (area > cells.size) {
            for ((k, list) in cells) {
                val x = k.packedHigh()
                val y = k.packedLow()
                if (x in qx0..qx1 && y in qy0..qy1) collect(list, out)
            }
        } else {
            for (x in qx0..qx1) for (y in qy0..qy1) cells[packInts(x, y)]?.let { collect(it, out) }
        }
        for (i in 0 until large.size) {
            val id = large[i]
            if (cx1[id] < qx0 || cx0[id] > qx1 || cy1[id] < qy0 || cy0[id] > qy1) continue
            if (stamps[id] != stamp) {
                stamps[id] = stamp
                out.add(id)
            }
        }
    }

    private fun collect(list: IntList, out: IntList) {
        for (i in 0 until list.size) {
            val id = list[i]
            if (stamps[id] == stamp) continue
            stamps[id] = stamp
            out.add(id)
        }
    }

    private fun unlink(id: Int) {
        if (isLarge[id]) {
            large.removeValue(id)
            return
        }
        for (x in cx0[id]..cx1[id]) for (y in cy0[id]..cy1[id]) {
            val k = packInts(x, y)
            val list = cells[k] ?: continue
            list.removeValue(id)
            if (list.isEmpty) cells.remove(k)
        }
    }

    private fun nextStamp() {
        stamp++
        if (stamp == Int.MAX_VALUE) {
            stamps.fill(0)
            stamp = 1
        }
    }

    private fun ensure(id: Int) {
        require(id >= 0) { "negative id $id" }
        if (id < present.size) return
        val n = maxOf(id + 1, present.size * 2)
        cx0 = cx0.copyOf(n); cy0 = cy0.copyOf(n); cx1 = cx1.copyOf(n); cy1 = cy1.copyOf(n)
        present = present.copyOf(n)
        isLarge = isLarge.copyOf(n)
        stamps = stamps.copyOf(n)
    }

    private fun cell(v: Float): Int = floor(v / cellSize).toInt()

    companion object {
        /** A node touches 1-4 cells. */
        const val DEFAULT_CELL = 256f

        const val MAX_CELLS = 64L
    }
}
