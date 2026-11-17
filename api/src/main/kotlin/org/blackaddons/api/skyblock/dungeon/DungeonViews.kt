package org.blackaddons.api.skyblock.dungeon

import org.blackaddons.api.Platform
import org.blackaddons.api.util.equalsIgnoreCase
import org.blackaddons.api.world.BlockPos3

interface DungeonRun {
    val started: Boolean
    val ended: Boolean

    /** Client ticks. */
    val ticks: Long
    val startTime: DualTime?
    val bossEntryTime: DualTime?
    val bloodOpenTime: DualTime?
    val watcherClearTime: DualTime?

    val witherKeys: Int
    val bloodKeys: Int

    /** Who opened the last wither door, from its chat line. */
    val lastDoorOpener: DungeonPlayer?

    /** Unnamed ones read [Puzzle.UNKNOWN]. */
    val puzzles: List<PuzzleStatus>

    /** From the bosses' lines, so it follows the fight; `LocationUtils.f7Phase` is where the player stands. */
    val bossPhase: F7Phase?
}

/** Player included. */
interface DungeonTeam {
    val players: List<DungeonPlayer>

    fun player(name: String): DungeonPlayer? = players.firstOrNull { it.name.equalsIgnoreCase(name) }

    val self: DungeonPlayer? get() = Platform.player.name?.let(::player)

    val others: List<DungeonPlayer> get() = players.filter { !it.name.equalsIgnoreCase(Platform.player.name) }
}

interface DungeonScore {
    /** In the Dungeon Map's calculation mode. */
    val score: Int
    val foundSecrets: Int
    val secretPercentage: Double

    /** Estimated from [foundSecrets] and [secretPercentage]. */
    val totalSecrets: Int
    val crypts: Int
    val deaths: Int
    val completedRooms: Int
    val clearedPercentage: Int
    val secondsElapsed: Int
    val mimicKilled: Boolean
    val princeKilled: Boolean
}

interface DungeonRoom {
    val name: String
    val type: RoomType
    val shape: RoomShape
    val state: RoomState
    val secrets: Int
    val foundSecrets: Int
    val crypts: Int
    val isFullyCleared: Boolean
    val hasMimic: Boolean

    /** The mimic is in one of them when [hasMimic]. */
    val mimicChests: List<BlockPos3>

    /** World positions of the room's secrets, from room data. Empty until the room's rotation is known. */
    val secretSpots: List<SecretSpot> get() = emptyList()

    val secretLevers: List<BlockPos3> get() = secretSpots.filter { it.kind == SecretKind.LEVER }.map { it.pos }

    /**
     * Room-local to world, null until the room's rotation is known. Local positions are what room data and routes
     * are written in: from the room's clay anchor, in its default rotation, so they hold for every copy of the room.
     */
    fun toWorld(local: BlockPos3): BlockPos3? = null

    /** World to room-local, the inverse of [toWorld]. */
    fun toLocal(world: BlockPos3): BlockPos3? = null
}

/** Where a room's secret is, and what has to be done there. */
data class SecretSpot(val kind: SecretKind, val pos: BlockPos3)

enum class SecretKind {
    CHEST,
    ITEM,
    BAT,
    WITHER_ESSENCE,
    REDSTONE_KEY,

    /** A lever that opens a secret, not a secret of its own. */
    LEVER,
}

/** At the world column of its center. */
interface DungeonDoor {
    val x: Int
    val z: Int
    val type: DoorType
    val state: RoomState
    val opened: Boolean
}

/** As far as the server revealed (or further, see [MapOverride]). */
interface DungeonLayout {
    val rooms: Collection<DungeonRoom>
    val doors: List<DungeonDoor>

    fun roomAt(x: Double, z: Double): DungeonRoom?

    /**
     * The door out of the fairy room on the blood rush path. Set while the player is in the room before it or has just
     * left it.
     */
    fun isFairyExit(door: DungeonDoor): Boolean

    val currentRoom: DungeonRoom? get() = if (Platform.player.isInGame) roomAt(Platform.player.x, Platform.player.z) else null

    val mimicRoom: DungeonRoom? get() = rooms.firstOrNull { it.hasMimic }

    /** One of [DungeonRoom.mimicChests]. Which of them holds the mimic is unknown. */
    fun isMimicChest(pos: BlockPos3): Boolean = mimicRoom?.mimicChests?.contains(pos) == true

    fun isSecretLever(pos: BlockPos3): Boolean =
        roomAt(pos.x.toDouble(), pos.z.toDouble())?.secretLevers?.contains(pos) == true
}

/** Host side of [Dungeon], bound once. */
interface DungeonProvider {
    val run: DungeonRun
    val team: DungeonTeam
    val score: DungeonScore
    val layout: DungeonLayout
}
