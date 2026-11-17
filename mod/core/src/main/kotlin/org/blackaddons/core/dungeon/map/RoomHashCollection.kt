package org.blackaddons.core.dungeon.map

import com.google.gson.GsonBuilder
import com.google.gson.reflect.TypeToken
import org.blackaddons.api.Platform
import org.blackaddons.api.util.Hashes
import org.blackaddons.api.util.concurrent.Workers
import org.blackaddons.api.util.io.Disk
import org.blackaddons.core.log.Log
import java.nio.file.Files

data class ModernRoomCore(val blocks: List<String>) {
    init {
        require(blocks.size == CORE_HEIGHT) { "Room core must contain $CORE_HEIGHT positions" }
    }

    val hash: Int by lazy { hash(blocks) }
    val signature: String by lazy {
        Hashes.hex(Hashes.sha256(blocks.joinToString("\u0000").toByteArray(Charsets.UTF_8)))
    }

    companion object {
        const val ALGORITHM = "polynomial-31:blackaddons:v5"
        const val CORE_HEIGHT = 129

        private val ignoredBlocks = setOf(
            "minecraft:chest", "minecraft:trapped_chest",
            "minecraft:piston_head", "minecraft:moving_piston",
            "minecraft:water", "minecraft:lava",
            "minecraft:fire", "minecraft:soul_fire",
        )

        /** Removes state properties and blocks that change during a run. */
        fun cleanToken(rawToken: String): String {
            val name = rawToken.substringBefore('[')
            if (name in ignoredBlocks || name.endsWith("_planks")) return "minecraft:air"
            return name
        }

        fun hash(blocks: List<String>): Int {
            var h = 1
            for (block in blocks) h = h * 31 + block.hashCode()
            return h
        }

    }
}

internal data class UnknownRoomHash(
    val signature: String,
    val hash: Int,
    val blocks: List<String>,
    val minecraftVersion: String,
)

internal data class UnknownRoomHashCollectionFile(
    val schema: Int = 1,
    val algorithm: String = ModernRoomCore.ALGORITHM,
    val hashes: MutableList<UnknownRoomHash> = mutableListOf(),
)

/**
 * Stores signatures the bundled catalog doesn't have. Compared whole, so a 32-bit hash collision doesn't count as a
 * capture.
 */
object RoomHashCollection {
    private val logger = Log.area("unknown-room-hashes")
    private val gson = GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create()
    private val saveExecutor = Workers.single("unknown-room-hash-save")
    private const val MAX_BYTES = 20L * 1024L * 1024L

    private val path get() = Platform.configDir.resolve("unknown-room-hash-collection.json")
    private var hashesBySignature: MutableMap<String, UnknownRoomHash> = mutableMapOf()
    private var writable = true

    fun init() {
        if (!Files.exists(path)) return
        runCatching {
            require(Files.size(path) <= MAX_BYTES) { "collection exceeds ${MAX_BYTES / 1024 / 1024} MiB" }
            val loaded = Files.newBufferedReader(path).use { reader ->
                gson.fromJson<UnknownRoomHashCollectionFile>(
                    reader,
                    object : TypeToken<UnknownRoomHashCollectionFile>() {}.type,
                )
            } ?: error("collection is empty")
            require(loaded.schema == 1) { "unsupported collection schema ${loaded.schema}" }
            val rehash = loaded.algorithm != ModernRoomCore.ALGORITHM
            hashesBySignature = loaded.hashes.associateTo(mutableMapOf()) { hash ->
                val core = ModernRoomCore(hash.blocks)
                if (!rehash) require(core.hash == hash.hash) { "invalid unknown room hash ${hash.hash}" }
                if (!rehash) require(core.signature == hash.signature) {
                    "invalid unknown room signature ${hash.hash}"
                }
                core.signature to hash.copy(signature = core.signature, hash = core.hash)
            }
            if (rehash) save()
        }.onFailure { error ->
            writable = false
            logger.error("Unknown room hash collection is invalid; preserving {}", path, error)
        }
    }

    fun recordIfNew(core: ModernRoomCore, minecraftVersion: String): Boolean {
        if (core.signature in hashesBySignature) return false

        hashesBySignature[core.signature] = UnknownRoomHash(
            signature = core.signature,
            hash = core.hash,
            blocks = core.blocks,
            minecraftVersion = minecraftVersion,
        )
        save()
        return true
    }

    private fun save() {
        if (!writable) return
        val snapshot = UnknownRoomHashCollectionFile(
            hashes = hashesBySignature.values.sortedBy(UnknownRoomHash::signature).toMutableList(),
        )
        saveExecutor.execute {
            runCatching { Disk.write(path, gson.toJson(snapshot)) }
                .onFailure { error -> logger.error("Could not save unknown room hashes", error) }
        }
    }
}
