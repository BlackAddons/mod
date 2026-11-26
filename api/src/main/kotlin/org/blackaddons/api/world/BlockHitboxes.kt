package org.blackaddons.api.world

import org.blackaddons.api.Platform

/** Enlarged outlines for small click targets; the crosshair pick tests the outline. */
interface BlockHitboxOverride {
    /** Button face side in blocks (0..1), or null for vanilla. */
    val buttonSize: Double?

    /** 0..1, or null for vanilla. */
    fun leverSize(x: Int, y: Int, z: Int): Double?

    /** Wither essence / redstone key skulls. */
    val fullSecretSkulls: Boolean

    val fullMushrooms: Boolean
}

/** The one override shape mixins read on every `getShape`. A plain slot keeps the platform free of the cheat addon. */
object BlockHitboxes {
    @Volatile
    @JvmStatic
    var active: BlockHitboxOverride? = null

    /** Inside [vanilla], world and raycast hosts use vanilla's shape tables, so no mod's enlarged hitboxes leak in. */
    @Volatile
    @JvmStatic
    var realShapes = false

    /** Against real shapes - where the block truly is. Client thread only. */
    inline fun <T> vanilla(action: () -> T): T {
        val outer = realShapes
        realShapes = true
        try {
            return action()
        } finally {
            realShapes = outer
        }
    }

    /**
     * Crosshair block using real shapes (the game's target counts enlarged outlines). Ask this before
     * deciding a feature is aimed. Cast from the tick eye position like the game's click target; the
     * render-frame position can miss a button while walking.
     */
    fun realCrosshairBlock(): BlockPos3? = vanilla {
        val reach = Platform.player.blockReach
        val eye = Platform.player.eyePos
        val look = ViewAngles.direction(Platform.player.yaw, Platform.player.pitch)
        val target = Vec3d(eye.x + look.x * reach, eye.y + look.y * reach, eye.z + look.z * reach)
        Platform.raycast.blockToward(target, reach)?.pos
    }
}
