package org.blackaddons.platform.impl.world
import net.minecraft.client.Minecraft
import net.minecraft.client.multiplayer.ClientLevel
import net.minecraft.core.SectionPos
import net.minecraft.world.item.BlockItem
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.chunk.status.ChunkStatus
import org.blackaddons.api.event.EventBus
import org.blackaddons.api.event.WorldChangeEvent
import org.blackaddons.api.event.render.RenderWorldEvent
import org.blackaddons.api.event.subscribe

internal object SectionRedraws {
    private const val SECTIONS_PER_FRAME = 8
    private const val CHUNKS_SCANNED_PER_FRAME = 8

    private val urgent = LinkedHashSet<Long>()
    private val pending = LinkedHashSet<Long>()
    private val scans = ArrayDeque<TypeScan>()
    private val ringOffsets = HashMap<Int, IntArray>()

    private class TypeScan(val blocks: Set<Block>, val centerX: Int, val centerZ: Int, val offsets: IntArray) {
        var next = 0
    }

    init {
        EventBus.subscribe<RenderWorldEvent> { drain() }
        EventBus.subscribe<WorldChangeEvent> { clear() }
    }

    fun block(x: Int, y: Int, z: Int) {
        urgent += SectionPos.asLong(SectionPos.blockToSectionCoord(x), SectionPos.blockToSectionCoord(y), SectionPos.blockToSectionCoord(z))
        addRange(
            urgent,
            SectionPos.blockToSectionCoord(x - 1), SectionPos.blockToSectionCoord(y - 1), SectionPos.blockToSectionCoord(z - 1),
            SectionPos.blockToSectionCoord(x + 1), SectionPos.blockToSectionCoord(y + 1), SectionPos.blockToSectionCoord(z + 1),
        )
    }

    fun chunk(level: ClientLevel, chunkX: Int, chunkZ: Int) =
        addRange(pending, chunkX, level.minSectionY, chunkZ, chunkX, level.maxSectionY, chunkZ)

    fun types(blocks: Set<Block>, centerX: Int, centerZ: Int, radius: Int) {
        scans += TypeScan(blocks, centerX, centerZ, ringOffsets.getOrPut(radius) { ring(radius) })
    }

    private fun drain() {
        val level = Minecraft.getInstance().level ?: return clear()
        var chunks = CHUNKS_SCANNED_PER_FRAME
        while (chunks > 0 && scans.isNotEmpty()) {
            val scan = scans.first()
            scanNext(level, scan)
            if (scan.next >= scan.offsets.size) scans.removeFirst()
            chunks--
        }
        dirty(level, urgent, Int.MAX_VALUE)
        dirty(level, pending, SECTIONS_PER_FRAME)
    }

    private fun scanNext(level: ClientLevel, scan: TypeScan) {
        val offset = scan.offsets[scan.next++]
        val chunkX = scan.centerX + (offset shr 16)
        val chunkZ = scan.centerZ + offset.toShort()
        val chunk = level.chunkSource.getChunk(chunkX, chunkZ, ChunkStatus.FULL, false) ?: return
        chunk.sections.forEachIndexed { index, section ->
            if (section.hasOnlyAir()) return@forEachIndexed
            if (!section.maybeHas { it.block in scan.blocks || (it.block.asItem() as? BlockItem)?.block in scan.blocks }) {
                return@forEachIndexed
            }
            val sectionY = level.getSectionYFromSectionIndex(index)
            addRange(pending, chunkX - 1, sectionY - 1, chunkZ - 1, chunkX + 1, sectionY + 1, chunkZ + 1)
        }
    }

    private fun dirty(level: ClientLevel, sections: LinkedHashSet<Long>, budget: Int) {
        val iterator = sections.iterator()
        var left = budget
        while (left > 0 && iterator.hasNext()) {
            val section = iterator.next()
            iterator.remove()
            val x = SectionPos.x(section)
            val y = SectionPos.y(section)
            val z = SectionPos.z(section)
            level.setSectionRangeDirty(x, y, z, x, y, z)
            left--
        }
    }

    private fun addRange(target: MutableSet<Long>, minX: Int, minY: Int, minZ: Int, maxX: Int, maxY: Int, maxZ: Int) {
        for (x in minX..maxX) for (z in minZ..maxZ) for (y in minY..maxY) target += SectionPos.asLong(x, y, z)
    }

    private fun ring(radius: Int): IntArray {
        val offsets = ArrayList<IntArray>()
        for (dx in -radius..radius) for (dz in -radius..radius) offsets += intArrayOf(dx, dz)
        offsets.sortBy { it[0] * it[0] + it[1] * it[1] }
        return IntArray(offsets.size) { (offsets[it][0] shl 16) or (offsets[it][1] and 0xFFFF) }
    }

    private fun clear() {
        urgent.clear()
        pending.clear()
        scans.clear()
    }
}
