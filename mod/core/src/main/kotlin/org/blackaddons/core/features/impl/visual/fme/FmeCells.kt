package org.blackaddons.core.features.impl.visual.fme

import org.blackaddons.api.world.BlockPos3
import java.util.BitSet

internal class FmeCells private constructor(private val sections: Map<Long, IntArray>, val size: Int) {

    fun isEmpty(): Boolean = size == 0

    val boxCount: Int get() = sections.values.sumOf { it.size }

    fun contains(x: Int, y: Int, z: Int): Boolean {
        val boxes = sections[sectionKey(x shr 4, y shr 4, z shr 4)] ?: return false
        val lx = x and 15
        val ly = y and 15
        val lz = z and 15
        return boxes.any { box ->
            lx in boxMinX(box)..boxMaxX(box) && ly in boxMinY(box)..boxMaxY(box) && lz in boxMinZ(box)..boxMaxZ(box)
        }
    }

    fun plus(x: Int, y: Int, z: Int): FmeCells = if (contains(x, y, z)) this else edit(x, y, z) { it.set(cellIndex(x, y, z)) }

    fun minus(x: Int, y: Int, z: Int): FmeCells = if (!contains(x, y, z)) this else edit(x, y, z) { it.clear(cellIndex(x, y, z)) }

    operator fun plus(other: FmeCells): FmeCells = combine(other) { mine, theirs -> mine.or(theirs) }

    operator fun minus(other: FmeCells): FmeCells = combine(other) { mine, theirs -> mine.andNot(theirs) }

    fun forEachBox(action: (minX: Int, minY: Int, minZ: Int, maxX: Int, maxY: Int, maxZ: Int) -> Unit) {
        sections.forEach { (key, boxes) ->
            val baseX = sectionX(key) shl 4
            val baseY = sectionY(key) shl 4
            val baseZ = sectionZ(key) shl 4
            boxes.forEach { box ->
                action(
                    baseX + boxMinX(box), baseY + boxMinY(box), baseZ + boxMinZ(box),
                    baseX + boxMaxX(box), baseY + boxMaxY(box), baseZ + boxMaxZ(box),
                )
            }
        }
    }

    fun forEach(action: (x: Int, y: Int, z: Int) -> Unit) = forEachBox { minX, minY, minZ, maxX, maxY, maxZ ->
        for (y in minY..maxY) for (z in minZ..maxZ) for (x in minX..maxX) action(x, y, z)
    }

    fun take(limit: Int): List<BlockPos3> {
        val taken = ArrayList<BlockPos3>(minOf(limit, size))
        forEachBox { minX, minY, minZ, maxX, maxY, maxZ ->
            for (y in minY..maxY) for (z in minZ..maxZ) for (x in minX..maxX) {
                if (taken.size >= limit) return@forEachBox
                taken += BlockPos3(x, y, z)
            }
        }
        return taken
    }

    fun <K> partition(keyOf: (x: Int, y: Int, z: Int) -> K): Map<K, FmeCells> {
        val builders = HashMap<K, Builder>()
        forEach { x, y, z -> builders.getOrPut(keyOf(x, y, z), ::Builder).add(x, y, z) }
        return builders.mapValues { it.value.build() }
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is FmeCells || size != other.size || sections.size != other.sections.size) return false
        return sections.all { (key, boxes) -> other.sections[key]?.contentEquals(boxes) == true }
    }

    override fun hashCode(): Int = 31 * size + sections.keys.hashCode()

    override fun toString(): String = "FmeCells(size=$size, sections=${sections.size})"

    private inline fun edit(x: Int, y: Int, z: Int, change: (BitSet) -> Unit): FmeCells {
        val key = sectionKey(x shr 4, y shr 4, z shr 4)
        val before = sections[key]
        val bits = before?.let(::raster) ?: BitSet(SECTION_CELLS)
        change(bits)
        return replaced(key, before, bits)
    }

    private fun combine(other: FmeCells, operation: (BitSet, BitSet) -> Unit): FmeCells {
        if (other.isEmpty()) return this
        var result = this
        other.sections.forEach { (key, theirs) ->
            val mine = result.sections[key]
            val bits = mine?.let(::raster) ?: BitSet(SECTION_CELLS)
            operation(bits, raster(theirs))
            result = result.replaced(key, mine, bits)
        }
        return result
    }

    private fun replaced(key: Long, before: IntArray?, bits: BitSet): FmeCells {
        val boxes = boxes(bits)
        if (if (before == null) boxes.isEmpty() else before.contentEquals(boxes)) return this
        val next = HashMap(sections)
        if (boxes.isEmpty()) next.remove(key) else next[key] = boxes
        return FmeCells(next, size - (before?.let(::volume) ?: 0) + bits.cardinality())
    }

    class Builder {
        private val bits = HashMap<Long, BitSet>()

        fun add(x: Int, y: Int, z: Int): Builder = apply {
            bits.getOrPut(sectionKey(x shr 4, y shr 4, z shr 4)) { BitSet(SECTION_CELLS) }.set(cellIndex(x, y, z))
        }

        fun addBox(minX: Int, minY: Int, minZ: Int, maxX: Int, maxY: Int, maxZ: Int): Builder = apply {
            for (sy in (minY shr 4)..(maxY shr 4)) for (sz in (minZ shr 4)..(maxZ shr 4)) for (sx in (minX shr 4)..(maxX shr 4)) {
                val section = bits.getOrPut(sectionKey(sx, sy, sz)) { BitSet(SECTION_CELLS) }
                for (y in maxOf(minY, sy shl 4)..minOf(maxY, (sy shl 4) + 15)) {
                    for (z in maxOf(minZ, sz shl 4)..minOf(maxZ, (sz shl 4) + 15)) {
                        val row = cellIndex(0, y, z)
                        section.set(row + (maxOf(minX, sx shl 4) and 15), row + (minOf(maxX, (sx shl 4) + 15) and 15) + 1)
                    }
                }
            }
        }

        fun build(): FmeCells {
            var size = 0
            val sections = HashMap<Long, IntArray>(bits.size)
            bits.forEach { (key, section) ->
                if (section.isEmpty) return@forEach
                sections[key] = boxes(section)
                size += section.cardinality()
            }
            return FmeCells(sections, size)
        }
    }

    companion object {
        val EMPTY = FmeCells(emptyMap(), 0)

        fun of(x: Int, y: Int, z: Int): FmeCells = Builder().add(x, y, z).build()

        internal const val SECTION_CELLS = 4096

        internal fun sectionKey(sx: Int, sy: Int, sz: Int): Long =
            (sx.toLong() and 0x3FFFFF) shl 42 or ((sz.toLong() and 0x3FFFFF) shl 20) or (sy.toLong() and 0xFFFFF)

        internal fun sectionX(key: Long): Int = (key shr 42).toInt()
        internal fun sectionZ(key: Long): Int = (key shl 22 shr 42).toInt()
        internal fun sectionY(key: Long): Int = (key shl 44 shr 44).toInt()

        internal fun cellIndex(x: Int, y: Int, z: Int): Int = (y and 15 shl 8) or (z and 15 shl 4) or (x and 15)

        private fun box(minX: Int, minY: Int, minZ: Int, maxX: Int, maxY: Int, maxZ: Int): Int =
            minX or (minY shl 4) or (minZ shl 8) or (maxX shl 12) or (maxY shl 16) or (maxZ shl 20)

        private fun boxMinX(box: Int) = box and 15
        private fun boxMinY(box: Int) = box shr 4 and 15
        private fun boxMinZ(box: Int) = box shr 8 and 15
        private fun boxMaxX(box: Int) = box shr 12 and 15
        private fun boxMaxY(box: Int) = box shr 16 and 15
        private fun boxMaxZ(box: Int) = box shr 20 and 15

        private fun volume(boxes: IntArray): Int = boxes.sumOf { box ->
            (boxMaxX(box) - boxMinX(box) + 1) * (boxMaxY(box) - boxMinY(box) + 1) * (boxMaxZ(box) - boxMinZ(box) + 1)
        }

        private fun raster(boxes: IntArray): BitSet = BitSet(SECTION_CELLS).apply {
            boxes.forEach { box ->
                for (y in boxMinY(box)..boxMaxY(box)) for (z in boxMinZ(box)..boxMaxZ(box)) {
                    val row = (y shl 8) or (z shl 4)
                    set(row + boxMinX(box), row + boxMaxX(box) + 1)
                }
            }
        }

        private fun boxes(source: BitSet): IntArray {
            val left = source.clone() as BitSet
            val result = ArrayList<Int>()
            var start = left.nextSetBit(0)
            while (start >= 0) {
                val x0 = start and 15
                val z0 = start shr 4 and 15
                val y0 = start shr 8
                var x1 = x0
                while (x1 < 15 && left[(y0 shl 8) or (z0 shl 4) or (x1 + 1)]) x1++
                var z1 = z0
                while (z1 < 15 && rowFull(left, x0, x1, y0, z1 + 1)) z1++
                var y1 = y0
                while (y1 < 15 && (z0..z1).all { rowFull(left, x0, x1, y1 + 1, it) }) y1++
                for (y in y0..y1) for (z in z0..z1) left.clear((y shl 8) + (z shl 4) + x0, (y shl 8) + (z shl 4) + x1 + 1)
                result += box(x0, y0, z0, x1, y1, z1)
                start = left.nextSetBit(start)
            }
            return result.toIntArray()
        }

        private fun rowFull(bits: BitSet, x0: Int, x1: Int, y: Int, z: Int): Boolean {
            val row = (y shl 8) or (z shl 4)
            return bits.nextClearBit(row + x0) > row + x1
        }
    }
}
