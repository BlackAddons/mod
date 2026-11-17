package org.blackaddons.core.dungeon.map

import com.google.gson.JsonObject
import com.google.gson.reflect.TypeToken
import org.blackaddons.api.skyblock.dungeon.RoomShape
import org.blackaddons.api.skyblock.dungeon.RoomType
import org.blackaddons.api.util.io.Gsons
import org.blackaddons.core.backend.Backend
import org.blackaddons.core.log.Log
import org.blackaddons.core.storage.ServedDocument

object RoomRegistry {
    private const val KEY = "rooms"

    private val logger = Log.area("room-registry")
    private val roomListType = object : TypeToken<List<RoomData>>() {}.type

    private data class Snapshot(
        val rooms: List<RoomData>,
        val coreIndex: Map<Int, RoomData>,
    )

    @Volatile
    private var snapshot = Snapshot(emptyList(), emptyMap())

    internal fun init() {
        val loaded = loadBundled()
        if (loaded.isEmpty()) {
            logger.warn("rooms.json is empty or missing - dungeon map room identification will be unavailable")
            return
        }

        runCatching { install(loaded, "bundled") }
            .onFailure { logger.error("Bundled rooms.json is invalid", it) }
    }

    fun load() {
        init()
        ServedDocument.load("${Backend.GAMEDATA_URL}/$KEY", "gamedata/$KEY") { document ->
            runCatching { install(parse(document), "game data") }
                .onFailure { logger.warn("Ignoring invalid rooms game-data document", it) }
        }
    }

    fun getRoomData(hash: Int): RoomData? = snapshot.coreIndex[hash]
    internal fun allRooms(): List<RoomData> = snapshot.rooms

    internal fun parse(document: JsonObject): List<RoomData> {
        val algorithm = document.get("algorithm")
            ?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isString }
            ?.asString
        require(algorithm == ModernRoomCore.ALGORITHM) {
            "expected hash algorithm ${ModernRoomCore.ALGORITHM}, got ${algorithm ?: "none"}"
        }

        val rooms = document.getAsJsonArray("rooms") ?: error("missing rooms array")
        return Gsons.compact.fromJson<List<RoomData>>(rooms, roomListType) ?: error("rooms array is null")
    }

    private fun install(rooms: List<RoomData>, source: String) {
        require(rooms.isNotEmpty()) { "rooms array is empty" }

        val coreIndex = LinkedHashMap<Int, RoomData>()
        for (room in rooms) {
            require(room.name.isNotBlank()) { "room name is blank" }
            require(room.cores.isNotEmpty()) { "room ${room.name} has no core hashes" }
            require(room.type in RoomType.entries) { "room ${room.name} has no valid type" }
            require(room.shape in RoomShape.entries) { "room ${room.name} has no valid shape" }
            for (core in room.cores) {
                val previous = coreIndex.putIfAbsent(core, room)
                require(previous == null) { "core $core belongs to both ${previous?.name} and ${room.name}" }
            }
        }

        snapshot = Snapshot(rooms.toList(), coreIndex.toMap())
        logger.debug("Loaded {} room definitions ({} core hashes) from {}", rooms.size, coreIndex.size, source)
    }

    private fun loadBundled(): List<RoomData> {
        val stream = RoomRegistry::class.java.getResourceAsStream("/assets/blackaddons/rooms.json")
        if (stream == null) {
            logger.error("Could not find bundled rooms.json on the classpath")
            return emptyList()
        }
        return try {
            stream.bufferedReader().use { reader ->
                Gsons.compact.fromJson(reader, roomListType)
            }
        } catch (e: Exception) {
            logger.error("Failed to parse rooms.json", e)
            emptyList()
        }
    }
}
