package org.blackaddons.platform.dungeon.map

import net.minecraft.SharedConstants
import net.minecraft.client.Minecraft
import net.minecraft.core.BlockPos
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.world.level.Level
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.level.chunk.status.ChunkStatus
import org.blackaddons.api.Platform
import org.blackaddons.api.event.ClientTickEvent
import org.blackaddons.api.event.EventBus
import org.blackaddons.api.event.WorldChangeEvent
import org.blackaddons.api.event.subscribe
import org.blackaddons.api.util.concurrent.OffThread
import org.blackaddons.api.util.io.Disk
import org.blackaddons.api.util.io.Gsons
import org.blackaddons.core.dungeon.map.ModernRoomCore
import org.blackaddons.core.log.Log
import java.nio.file.Files
import java.nio.file.Path
import kotlin.math.abs
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.TimeSource

/** Captures rooms on the client thread, writes validated Sponge v2/v3 NBT off it. The worker never reads the world. */
internal object RoomSnapshotCollector {
    private val logger = Log.area("unknown-room-snapshots")
    private val BUDGET = 2.milliseconds
    private const val SNAPSHOT_MIN_Y = 0
    private const val SNAPSHOT_MAX_Y = 256
    private const val METADATA_FILE = "metadata.json"
    private val queued = ArrayDeque<Request>()
    private var active: Active? = null
    private var initialized = false

    fun init() {
        if (initialized) return
        initialized = true
        EventBus.subscribe<WorldChangeEvent> {
            active = null
            queued.clear()
        }
        EventBus.subscribe<ClientTickEvent.Post> {
            val level = Minecraft.getInstance().level ?: return@subscribe
            tick(level)
        }
    }

    fun queueUnknown(core: ModernRoomCore, worldX: Int, worldZ: Int) {
        init()
        val snapshotDir = Platform.configDir.resolve("room-snapshots").resolve(core.signature)
        if (Files.exists(snapshotDir.resolve(METADATA_FILE))) {
            return
        }
        if (active?.request?.key == core.signature || queued.any { it.key == core.signature }) return
        val request = Request(core.signature, core.hash, listOf(worldX to worldZ))
        queued.addLast(request)
    }

    private fun tick(level: Level) {
        if (active == null) startNext()
        val capture = active ?: return
        if (!capture.allChunksHaveBlocks(level)) {
            return
        }
        val deadline = TimeSource.Monotonic.markNow() + BUDGET
        val pos = BlockPos.MutableBlockPos()
        while (!deadline.hasPassedNow() && !capture.complete) {
            capture.read(level, pos)
        }
        if (!capture.complete) return
        active = null
        val data = capture.finish(SharedConstants.getCurrentVersion().dataVersion().version(), Platform.minecraftVersion)
        OffThread.run(
            name = "room-snapshot",
            work = { write(data) },
            onFailure = { t -> Result.failure(t) },
            onResult = { result -> result.onFailure { error -> logger.error("Could not save unknown room snapshot", error) } },
        )
    }

    private fun startNext() {
        val request = queued.removeFirstOrNull() ?: return
        val capture = Active(request)
        active = capture
    }

    private fun write(data: SnapshotData): Result<Path> = runCatching {
        val dir = Platform.configDir.resolve("room-snapshots").resolve(data.request.key)
        val metadata = SnapshotMetadata(data.request.hash, data.request.key, data.origin, data.mask, data.minecraftVersion)
        // Metadata marks a snapshot as taken, so it only commits with both schematics.
        Disk.writeAll {
            SpongeSchematicWriter.stage(this, dir, data.toSchematic())
            file(dir.resolve(METADATA_FILE)) { Files.writeString(it, Gsons.pretty.toJson(metadata)) }
        }
        dir.resolve(SpongeSchematicWriter.V2_FILE)
    }

    private data class Request(val key: String, val hash: Int, val cells: List<Pair<Int, Int>>)
    private data class SnapshotMetadata(val hash: Int, val signature: String, val origin: IntArray, val mask: List<IntArray>, val minecraftVersion: String)
    private data class SnapshotData(val request: Request, val origin: IntArray, val width: Int, val height: Int, val length: Int, val palette: List<String>, val blockData: IntArray, val blockEntities: List<SchematicBlockEntity>, val mask: List<IntArray>, val dataVersion: Int, val minecraftVersion: String) {
        fun toSchematic() = CapturedSchematic("Unknown native room ${request.hash}", dataVersion, width, height, length, palette, blockData, blockEntities)
    }

    private class Active(val request: Request) {
        private val minY = SNAPSHOT_MIN_Y
        private val maxY = SNAPSHOT_MAX_Y
        private val minX = request.cells.minOf { it.first } - 15
        private val maxX = request.cells.maxOf { it.first } + 15
        private val minZ = request.cells.minOf { it.second } - 15
        private val maxZ = request.cells.maxOf { it.second } + 15
        private val width = maxX - minX + 1
        private val height = maxY - minY + 1
        private val length = maxZ - minZ + 1
        private val owned = buildOwnedColumns(request.cells)
        private val chunks = owned.map { (x, z) -> (x shr 4) to (z shr 4) }.distinct()
        private val states = arrayOfNulls<String>(width * height * length)
        private val entities = mutableListOf<SchematicBlockEntity>()
        private var columnIndex = 0
        private var y = minY

        val complete get() = columnIndex >= owned.size

        fun allChunksHaveBlocks(level: Level): Boolean = chunks.all { (x, z) ->
            level.getChunk(x, z, ChunkStatus.FULL, false)?.sections?.any { !it.hasOnlyAir() } == true
        }

        fun read(level: Level, pos: BlockPos.MutableBlockPos) {
            val (x, z) = owned[columnIndex]
            pos.set(x, y, z)
            val state = level.getBlockState(pos)
            states[indexOf(x, y, z)] = stateString(state).takeUnless { state.isAir }
            level.getBlockEntity(pos)?.let { entity ->
                val extra = entity.saveWithFullMetadata(level.registryAccess()).copy().apply {
                    remove("id")
                    remove("x")
                    remove("y")
                    remove("z")
                }
                entities += SchematicBlockEntity(
                    BuiltInRegistries.BLOCK_ENTITY_TYPE.getKey(entity.type).toString(),
                    intArrayOf(x - minX, y - minY, z - minZ),
                    extra,
                )
            }
            y++
            if (y > maxY) {
                y = minY
                columnIndex++
            }
        }

        fun finish(dataVersion: Int, minecraftVersion: String): SnapshotData {
            val yBounds = states.indices.asSequence()
                .filter { states[it] != null }
                .map { it / (width * length) + minY }
                .let { positions -> positions.minOrNull()?.let { it to positions.maxOrNull()!! } }
                ?: minY to maxY
            val trimmedMinY = yBounds.first
            val trimmedMaxY = yBounds.second
            val trimmedHeight = trimmedMaxY - trimmedMinY + 1
            val palette = linkedMapOf("minecraft:air" to 0)
            val data = IntArray(width * trimmedHeight * length)
            for (y in trimmedMinY..trimmedMaxY) for (z in minZ..maxZ) for (x in minX..maxX) {
                val source = states[indexOf(x, y, z)] ?: "minecraft:air"
                val target = ((y - trimmedMinY) * length + (z - minZ)) * width + (x - minX)
                data[target] = palette.getOrPut(source) { palette.size }
            }
            val trimmedEntities = entities.asSequence()
                .filter { it.pos[1] in trimmedMinY - minY..trimmedMaxY - minY }
                .map { it.copy(pos = it.pos.copyOf().apply { this[1] -= trimmedMinY - minY }) }
                .toList()
            return SnapshotData(request, intArrayOf(minX, trimmedMinY, minZ), width, trimmedHeight, length, palette.keys.toList(), data, trimmedEntities, owned.map { intArrayOf(it.first - minX, it.second - minZ) }, dataVersion, minecraftVersion)
        }

        private fun indexOf(x: Int, y: Int, z: Int): Int = ((y - minY) * length + (z - minZ)) * width + (x - minX)

        private fun buildOwnedColumns(cells: List<Pair<Int, Int>>): List<Pair<Int, Int>> {
            val result = linkedSetOf<Pair<Int, Int>>()
            for ((cx, cz) in cells) for (x in cx - 15..cx + 15) for (z in cz - 15..cz + 15) result += x to z
            for ((ax, az) in cells) for ((bx, bz) in cells) {
                if (ax == bx && abs(az - bz) == 32) for (x in ax - 15..ax + 15) result += x to ((az + bz) / 2)
                if (az == bz && abs(ax - bx) == 32) for (z in az - 15..az + 15) result += ((ax + bx) / 2) to z
            }
            return result.toList().sortedWith(compareBy<Pair<Int, Int>> { it.first }.thenBy { it.second })
        }

        private fun stateString(state: BlockState): String {
            val name = BuiltInRegistries.BLOCK.getKey(state.block).toString()
            val values = state.values.toList().map { "${it.property().name}=${it.valueName()}" }.sorted()
            return if (values.isEmpty()) name else "$name[${values.joinToString(",")}]"
        }
    }
}
