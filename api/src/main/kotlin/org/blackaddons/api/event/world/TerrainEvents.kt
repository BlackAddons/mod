package org.blackaddons.api.event.world

import org.blackaddons.api.event.Event
import org.blackaddons.api.world.BlockPos3
import org.blackaddons.api.world.BlockTerrain

/** Posted on the client thread when a chunk's blocks arrive (again, when the server resends it) or are dropped. */
sealed class ChunkEvent(val chunkX: Int, val chunkZ: Int) : Event() {
    class Load(chunkX: Int, chunkZ: Int) : ChunkEvent(chunkX, chunkZ)
    class Unload(chunkX: Int, chunkZ: Int) : ChunkEvent(chunkX, chunkZ)
}

/**
 * Posted on the client thread when one block changes in a loaded chunk, from the server or predicted by the client.
 * A whole chunk arriving is a [ChunkEvent.Load] instead. [before] and [after] are computed on first read.
 */
abstract class BlockChangeEvent(val pos: BlockPos3) : Event() {
    abstract val before: BlockTerrain
    abstract val after: BlockTerrain
}
