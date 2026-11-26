package org.blackaddons.platform.cosmetics

import com.mojang.blaze3d.vertex.PoseStack
import java.util.UUID

/**
 * Keeps geometry the right way out on an upside-down player.
 *
 * A negative height scale makes the pose determinant negative, which reverses triangle winding. Culling is baked into
 * the pipeline, so the inside shows. Minecraft fixes normals but not winding. Closed boxes survive, open geometry
 * (cape, held item) doesn't, so [evenOut] adds a mirror across the width: two mirrors make a rotation.
 *
 * Width and depth never go negative, so height is the only mirror. Armor, trims and elytra use `armorCutoutNoCull` and
 * don't care.
 */
object Mirroring {

    /**
     * Turns an upside-down pose into the equivalent half turn, in place. It's its own inverse: call it again afterwards
     * to restore the pose.
     */
    @JvmStatic
    fun evenOut(poseStack: PoseStack, uuid: UUID?) {
        val height = PlayerCustomization.scaleFor(uuid)?.get(1) ?: return
        if (height < 0f) poseStack.scale(-1f, 1f, 1f)
    }
}
