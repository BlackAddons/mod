package org.blackaddons.api.player

import org.blackaddons.api.world.ItemInfo
import org.blackaddons.api.world.Vec3d
import java.util.UUID

/** Local player, read live. Outside a world ([isInGame] false) values are defaults: null name/uuid, 0 coordinates. */
interface PlayerHost {
    val isInGame: Boolean

    val name: String?
    val uuid: UUID?

    /** Base64 `textures` property from the tab list; null when disconnected or the server sends no skins. */
    val skinTextures: String?

    /** Tick position; use for logic. Use [renderX] etc. for drawing. */
    val x: Double
    val y: Double
    val z: Double

    val pos: Vec3d get() = Vec3d(x, y, z)

    /** Where rays, reach and aim are measured from. Default assumes standing. */
    val eyePos: Vec3d get() = Vec3d(x, y + STANDING_EYE_HEIGHT, z)

    /** From [eyePos] to the hitbox; vanilla survival is 3. */
    val entityReach: Double get() = SURVIVAL_ENTITY_REACH

    /** Vanilla survival is 4.5. */
    val blockReach: Double get() = SURVIVAL_BLOCK_REACH

    /** Frame-interpolated position for rendering; tick positions jitter at high FPS. */
    val renderX: Double
    val renderY: Double
    val renderZ: Double

    /** Degrees; 0 = south, clockwise. */
    val yaw: Float
    /** Degrees; -90 = up, 90 = down. */
    val pitch: Float

    /** Frame-interpolated head yaw for rendering, in [yaw]'s degrees. */
    val renderHeadYaw: Float get() = yaw

    /** Body yaw in [yaw]'s degrees; lags the head while standing still. */
    val bodyYaw: Float get() = yaw

    /** Hitbox height in blocks; shorter while sneaking. */
    val height: Double get() = STANDING_HEIGHT

    /** Hitbox width in blocks, the same on x and z. */
    val width: Double get() = PLAYER_WIDTH

    /** The highest ledge the player walks up without jumping. Vanilla's is 0.6. */
    val stepHeight: Double get() = PLAYER_STEP_HEIGHT

    /** Sneak key held (input state). */
    val isSneaking: Boolean

    val onGround: Boolean

    val health: Float
    val maxHealth: Float

    /** 0..20. */
    val food: Int

    val gameMode: GameMode

    val mainHandItem: ItemInfo?

    /** True when the player has a fishing line out. */
    val isFishing: Boolean get() = false

    /** Hotbar `0..8`, main `9..35`, armor/offhand above. Null if empty or out of range. */
    fun inventoryItem(slot: Int): ItemInfo?

    /** e.g. `"minecraft:overworld"`. */
    val dimensionId: String?
}

private const val STANDING_EYE_HEIGHT = 1.62
private const val STANDING_HEIGHT = 1.8
private const val PLAYER_WIDTH = 0.6
private const val PLAYER_STEP_HEIGHT = 0.6
private const val SURVIVAL_ENTITY_REACH = 3.0
private const val SURVIVAL_BLOCK_REACH = 4.5

enum class GameMode { SURVIVAL, CREATIVE, ADVENTURE, SPECTATOR, UNKNOWN }
