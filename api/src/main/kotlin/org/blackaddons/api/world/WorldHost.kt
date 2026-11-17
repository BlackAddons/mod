package org.blackaddons.api.world

import org.blackaddons.api.event.world.EntitySnapshot
import org.blackaddons.api.player.BlockFace
import java.util.UUID

/** A block and the item that previews it. */
data class BlockCatalogEntry(val blockId: String, val itemId: String)

/** Only non-default state properties, e.g. `facing=north`. */
data class BlockPlacement(val blockId: String, val properties: Map<String, String>)

/** Loaded world, computed on read; null/0 without a world. Redraw methods only touch client render data. */
interface WorldHost {
    val isLoaded: Boolean

    /** e.g. `"minecraft:overworld"`. */
    val dimensionId: String?

    /** `ip:port`; null in singleplayer or on the title screen. */
    val serverAddress: String?

    /** From the server's `minecraft:brand` packet, e.g. `"Hypixel BungeeCord (...)"`; survives proxies, unlike [serverAddress]. */
    val serverBrand: String?

    /** Integrated server (singleplayer/LAN host). Unlike a null [serverAddress], known during login/configuration. */
    val isSingleplayer: Boolean

    /** 0..24000 within a day, increasing across days. */
    val dayTime: Long

    /** Level game time; 0 without a world. */
    val gameTime: Long

    /** e.g. `"minecraft:sea_lantern"`, or `"minecraft:air"`. Cheap enough per tick for a few positions. */
    fun blockIdAt(x: Int, y: Int, z: Int): String?

    /**
     * Center of the block's outline box - the point to aim at. Not the block center: buttons, levers and
     * slabs fill only part of it. Null without a world or for shapeless blocks.
     */
    fun blockAimPoint(x: Int, y: Int, z: Int): Vec3d?

    /** Includes shape changes from other mods. */
    fun blockOutlineBoxes(x: Int, y: Int, z: Int): List<Box3d> = emptyList()

    /**
     * The box between two corners (inclusive, any order) in one client-thread pass. Null without a world. Throws
     * over [TerrainSnapshot.MAX_CELLS]; split bigger areas, e.g. by [chunkSnapshot].
     */
    fun snapshot(corner: BlockPos3, opposite: BlockPos3): TerrainSnapshot? = null

    /** A whole chunk column, bottom to top of the world. Null without a world or when the chunk isn't loaded. */
    fun chunkSnapshot(chunkX: Int, chunkZ: Int): TerrainSnapshot? = null

    /**
     * Whether [box] would hit blocks, solid entities or the world border, the same way the player's own movement checks
     * it.
     */
    fun collides(box: Box3d): Boolean = false

    /** State property as the game spells it (`"true"`, `"north"`); null if absent. */
    fun blockProperty(x: Int, y: Int, z: Int, name: String): String? = null

    /** Profile UUID of a player head's owner; null for other blocks or an unowned head. */
    fun skullOwnerId(x: Int, y: Int, z: Int): String? = null

    /** Snapshots at call time. Used e.g. for the "Device Active" armor stand. */
    fun entitiesNear(x: Double, y: Double, z: Double, radius: Double): List<EntitySnapshot>

    /** Null when not loaded. */
    fun entityBox(uuid: UUID): Box3d?

    /** A loaded player entity by account name, ignoring case; null when not loaded. */
    fun playerUuid(name: String): UUID? = null

    /** Where a loaded player looks, as a yaw to pitch pair, by account name ignoring case; null when not loaded. */
    fun playerRotation(name: String): Pair<Float, Float>? = null

    /** Head slot (an armor stand's display item), or null. */
    fun headItem(uuid: UUID): ItemInfo?

    fun itemFrames(box: Box3d): List<ItemFrameSnapshot> = emptyList()

    /** Sorted; decorative rows (Hypixel's scoreboard) with invalid account names are dropped. */
    val onlinePlayerNames: List<String>

    /** Decorative tab rows excluded. */
    val onlinePlayerUuids: List<UUID>

    /** Sorted, e.g. `"minecraft:flame"`. */
    val registeredParticleIds: List<String>

    /** Creative tab order. */
    val registeredBlocks: List<BlockCatalogEntry> get() = emptyList()

    /**
     * What vanilla would place for [blockId] at [pos] when clicking [face] at [point], without placing. May
     * return a wall variant. Null if it can't be placed.
     */
    fun placementAt(blockId: String, pos: BlockPos3, face: BlockFace, point: Vec3d): BlockPlacement? = null

    /** Pre-1.13 state (e.g. `minecraft:stained_glass[color=pink]`) as its modern block, or null. Safe off-thread. */
    fun flattenLegacyBlock(state: String): BlockPlacement? = null

    /** Clockwise from above. */
    fun rotateProperties(blockId: String, properties: Map<String, String>, quarterTurns: Int): Map<String, String> = properties

    /** Rebuilds the render mesh around one block; world state is untouched. */
    fun redrawBlock(x: Int, y: Int, z: Int) {}

    /** By chunk coordinates. */
    fun redrawChunk(chunkX: Int, chunkZ: Int) {}

    /** Old meshes stay until rebuilds are ready, so nothing blanks out. */
    fun redrawBlockTypes(blockIds: Set<String>) {}

    /** After a render-only rule changes. */
    fun redrawWorld() {}
}

/** [pos] is the block it hangs in; [rotation] is eighth turns (0..7); [itemId] null when empty. */
data class ItemFrameSnapshot(val uuid: UUID, val pos: BlockPos3, val rotation: Int, val itemId: String?)
