package org.blackaddons.core.features.impl.visual

import org.blackaddons.api.Platform
import org.blackaddons.api.camera.CameraControllers
import org.blackaddons.api.event.render.RenderWorldEvent
import org.blackaddons.api.feature.Categories
import org.blackaddons.api.feature.Feature
import org.blackaddons.api.player.InventorySlots
import java.awt.Color
import kotlin.math.cos
import kotlin.math.sin

internal object ChinaHat : Feature(
    description = "Renders a beautiful rotating rainbow China Hat on your head. This hat doesn't know what happened on Tiananmen Square in 1989.",
    name = "China Hat"
) {

    override val section = Categories.Visual.Self
    private const val SEGMENTS = 64 // Seems optimal to me. Maybe a lower value gives it more of a "China" feeling.
    private const val HELMET_LIFT = 0.15
    private const val SNEAK_SHIFT = 0.15
    private val heightOffsetSetting by number("Height Offset", 0.0, -1.0, 1.0, 0.05)
        .withDescription("Additional height offset for the hat relative to the player's head.")
    private val hatColor by color("Color", Color(255, 0, 0, 150), allowAlpha = true)
        .withDescription("Color of the hat. Enable Chroma for a rotating rainbow.")
        .apply {
            chroma = true
            chromaSpeed = 0.25f
        }
    private val peakSetting by number("Peak Height", 0.35, 0.0, 1.0, 0.05)
        .withDescription("How tall/pointy the China Hat is.")
    private val radiusSetting by number("Radius", 0.6, 0.1, 2.0, 0.05)
        .withDescription("Radius (width) of the China Hat in blocks.")

    override fun init() {
        register<RenderWorldEvent> { render() }
    }

    private fun render() {
        val player = Platform.player
        if (!player.isInGame) return

        if (Platform.camera.perspective.isFirstPerson && CameraControllers.active() == null) return

        var heightOffset = heightOffsetSetting.value
        if (player.inventoryItem(InventorySlots.HEAD) != null) heightOffset += HELMET_LIFT

        var x = player.renderX
        var y = player.renderY + player.height + heightOffset
        var z = player.renderZ
        if (player.isSneaking) {
            y -= SNEAK_SHIFT
            val bodyYaw = Math.toRadians(player.bodyYaw.toDouble())
            x -= sin(bodyYaw) * SNEAK_SHIFT
            z += cos(bodyYaw) * SNEAK_SHIFT
        }

        Platform.worldRenderer.cone(
            x, y, z,
            radiusSetting.value, peakSetting.value,
            player.renderHeadYaw,
            SEGMENTS,
            { hatColor.colorAt(-it) },
            throughBlocks = false,
        )
    }
}
