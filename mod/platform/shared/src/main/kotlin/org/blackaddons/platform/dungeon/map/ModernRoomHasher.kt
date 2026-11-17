package org.blackaddons.platform.dungeon.map

import net.minecraft.core.BlockPos
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.world.level.Level
import net.minecraft.world.level.block.Block
import org.blackaddons.core.dungeon.map.ModernRoomCore
import java.util.IdentityHashMap

/** Canonical room signature from the sampled center column. */
internal object ModernRoomHasher {
    private val tokenHashCache = IdentityHashMap<Block, Int>()

    fun hash(level: Level, worldX: Int, worldZ: Int): Int {
        val pos = BlockPos.MutableBlockPos(worldX, 0, worldZ)
        var hash = 1

        for (y in 140 downTo 12) {
            val block = level.getBlockState(pos.setY(y)).block
            val tokenHash = tokenHashCache.getOrPut(block) {
                ModernRoomCore.cleanToken(BuiltInRegistries.BLOCK.getKey(block).toString()).hashCode()
            }
            hash = hash * 31 + tokenHash
        }
        return hash
    }

    fun sample(level: Level, worldX: Int, worldZ: Int): ModernRoomCore {
        val blocks = ArrayList<String>(ModernRoomCore.CORE_HEIGHT)
        val pos = BlockPos.MutableBlockPos(worldX, 0, worldZ)

        for (y in 140 downTo 12) {
            val block = level.getBlockState(pos.setY(y)).block
            blocks += ModernRoomCore.cleanToken(BuiltInRegistries.BLOCK.getKey(block).toString())
        }
        return ModernRoomCore(blocks)
    }
}
