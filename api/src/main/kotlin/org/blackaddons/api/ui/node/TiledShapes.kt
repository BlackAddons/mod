package org.blackaddons.api.ui.node

import org.blackaddons.api.ui.render.PathData
import org.blackaddons.api.ui.render.Render
import org.blackaddons.api.ui.render.ShapePath
import org.blackaddons.api.util.packInts
import java.awt.Color
import kotlin.math.floor

/**
 * Many small items drawn as a few cached shapes: filed into world tiles by center, one native shape per tile
 * and color, rebuilt only when an item in it changes. [emit] reads [mode]; changing it rebuilds all tiles.
 * Render thread only.
 */
class TiledShapes(
    private val tileSize: Float = DEFAULT_TILE,
    private val emit: (id: Int, mode: Int, out: Buckets) -> Unit,
) {

    class Buckets internal constructor() {
        internal val byColor = LinkedHashMap<Color, PathData>()
        internal val pool = ArrayList<PathData>()

        fun of(color: Color): PathData = byColor.getOrPut(color) { pool.removeLastOrNull() ?: PathData(64) }

        internal fun reset() {
            byColor.values.forEach { it.clear(); pool += it }
            byColor.clear()
        }

        /** Empties the buckets; the caller deletes the shapes. */
        internal fun take(): List<Pair<ShapePath, Color>> {
            val shapes = byColor.mapNotNull { (color, data) ->
                if (data.isEmpty) null else Render.createShape(data) to color
            }
            reset()
            return shapes
        }
    }

    private class Tile {
        val items = IntList(8)
        var dirty = true
        var builtMode = Int.MIN_VALUE
        val shapes = ArrayList<ShapePath>(2)
        val colors = ArrayList<Color>(2)

        var minX = Float.MAX_VALUE
        var minY = Float.MAX_VALUE
        var maxX = -Float.MAX_VALUE
        var maxY = -Float.MAX_VALUE

        fun grow(x0: Float, y0: Float, x1: Float, y1: Float) {
            if (x0 < minX) minX = x0
            if (y0 < minY) minY = y0
            if (x1 > maxX) maxX = x1
            if (y1 > maxY) maxY = y1
        }
    }

    private val tiles = HashMap<Long, Tile>()
    private val buckets = Buckets()

    private var tileOf = LongArray(64)
    private var boxes = FloatArray(64 * 4)
    private var filed = BooleanArray(64)

    /** Changing it rebuilds every tile lazily. */
    var mode: Int = 0

    /** Profiling hook. */
    var lastRebuilds: Int = 0
        private set

    val tileCount: Int get() = tiles.size

    /** Its tile rebuilds. */
    fun put(id: Int, minX: Float, minY: Float, maxX: Float, maxY: Float) {
        ensure(id)
        val key = key((minX + maxX) / 2f, (minY + maxY) / 2f)
        if (filed[id] && tileOf[id] != key) unfile(id)
        val tile = tiles.getOrPut(key) { Tile() }
        if (!filed[id] || tileOf[id] != key) tile.items.add(id)
        filed[id] = true
        tileOf[id] = key
        boxes[id * 4] = minX; boxes[id * 4 + 1] = minY; boxes[id * 4 + 2] = maxX; boxes[id * 4 + 3] = maxY
        tile.grow(minX, minY, maxX, maxY)
        tile.dirty = true
    }

    fun remove(id: Int) {
        if (id < filed.size && filed[id]) unfile(id)
    }

    fun contains(id: Int): Boolean = id < filed.size && filed[id]

    /** Look changed but not the box. */
    fun invalidate(id: Int) {
        if (id < filed.size && filed[id]) tiles[tileOf[id]]?.dirty = true
    }

    fun invalidateAll() {
        tiles.values.forEach { it.dirty = true }
    }

    /** Rebuilds stale tiles first; [paint] decides how to draw each shape. */
    fun draw(view: FloatArray, paint: (ShapePath, Color) -> Unit) {
        lastRebuilds = 0
        val it = tiles.values.iterator()
        while (it.hasNext()) {
            val tile = it.next()
            if (tile.items.isEmpty) {
                release(tile)
                it.remove()
                continue
            }
            if (tile.maxX < view[0] || tile.minX > view[2] || tile.maxY < view[1] || tile.minY > view[3]) continue
            if (tile.dirty || tile.builtMode != mode) rebuild(tile)
            for (i in tile.shapes.indices) paint(tile.shapes[i], tile.colors[i])
        }
    }

    /** Items stay filed and rebuild on the next draw. */
    fun releaseShapes() {
        tiles.values.forEach { release(it); it.dirty = true }
    }

    fun clear() {
        tiles.values.forEach(::release)
        tiles.clear()
        filed.fill(false)
    }

    private fun rebuild(tile: Tile) {
        lastRebuilds++
        release(tile)
        tile.minX = Float.MAX_VALUE; tile.minY = Float.MAX_VALUE
        tile.maxX = -Float.MAX_VALUE; tile.maxY = -Float.MAX_VALUE
        for (i in 0 until tile.items.size) {
            val id = tile.items[i]
            val b = id * 4
            tile.grow(boxes[b], boxes[b + 1], boxes[b + 2], boxes[b + 3])
            emit(id, mode, buckets)
        }
        for ((color, data) in buckets.byColor) {
            if (data.isEmpty) continue
            tile.shapes += Render.createShape(data)
            tile.colors += color
        }
        buckets.reset()
        tile.dirty = false
        tile.builtMode = mode
    }

    private fun release(tile: Tile) {
        tile.shapes.forEach(Render::deleteShape)
        tile.shapes.clear()
        tile.colors.clear()
    }

    private fun unfile(id: Int) {
        val tile = tiles[tileOf[id]]
        if (tile != null) {
            tile.items.removeValue(id)
            tile.dirty = true
        }
        filed[id] = false
    }

    private fun ensure(id: Int) {
        if (id < filed.size) return
        val n = maxOf(id + 1, filed.size * 2)
        tileOf = tileOf.copyOf(n)
        boxes = boxes.copyOf(n * 4)
        filed = filed.copyOf(n)
    }

    private fun key(x: Float, y: Float): Long = packInts(floor(x / tileSize).toInt(), floor(y / tileSize).toInt())

    companion object {
        /** Four grid cells a side. */
        const val DEFAULT_TILE = 1024f
    }
}
