package org.blackaddons.api.world

import java.awt.Color

/**
 * 3D drawing in absolute world coordinates; only works inside a `RenderWorldEvent` listener. `throughBlocks`
 * (default true) ignores depth like ESP. `depthPriority` biases depth so coplanar markers stay visible.
 * Colors carry alpha.
 */
interface WorldRenderer {
    fun line(
        x1: Double, y1: Double, z1: Double,
        x2: Double, y2: Double, z2: Double,
        color: Color,
        thickness: Float = 2f,
        throughBlocks: Boolean = true,
        depthPriority: Boolean = false,
    )

    /** Crosshair tracer from the camera eye; smooth at any frame rate. Prefer it over a manual [line]. */
    fun tracer(
        x: Double, y: Double, z: Double,
        color: Color,
        thickness: Float = 2f,
        throughBlocks: Boolean = true,
    )

    fun boxEdges(
        minX: Double, minY: Double, minZ: Double,
        maxX: Double, maxY: Double, maxZ: Double,
        color: Color,
        thickness: Float = 2f,
        throughBlocks: Boolean = true,
        depthPriority: Boolean = false,
    )

    /** Use a translucent color for a tint. */
    fun filledBox(
        minX: Double, minY: Double, minZ: Double,
        maxX: Double, maxY: Double, maxZ: Double,
        color: Color,
        throughBlocks: Boolean = true,
        depthPriority: Boolean = false,
    )

    /** Billboard text. [scale] 1 matches a vanilla name tag. */
    fun text(
        text: String,
        x: Double, y: Double, z: Double,
        color: Color,
        scale: Float = 1f,
        throughBlocks: Boolean = true,
    )

    /** Translucent waypoint beam at (x, z). */
    fun beacon(
        x: Double, z: Double,
        color: Color,
        fromY: Double = -64.0,
        toY: Double = 320.0,
        throughBlocks: Boolean = true,
    )

    /** Segment count scales with [radius]. */
    fun ring(
        x: Double, y: Double, z: Double,
        radius: Double,
        color: Color,
        thickness: Float = 2f,
        throughBlocks: Boolean = true,
        depthPriority: Boolean = false,
    )

    /** False only if the box is outside the view this frame; true outside a frame. */
    fun isVisible(minX: Double, minY: Double, minZ: Double, maxX: Double, maxY: Double, maxZ: Double): Boolean = true

    /**
     * Upright double-sided cone, base centered on (x, y, z). [yaw] turns it in the player's yaw degrees;
     * [color] gets each segment's position around the base as 0..1, so a gradient turns with it.
     */
    fun cone(
        x: Double, y: Double, z: Double,
        radius: Double, height: Double,
        yaw: Float,
        segments: Int,
        color: (Float) -> Color,
        throughBlocks: Boolean = true,
        depthPriority: Boolean = false,
    )

    /** Vertical cylinder at (x, z). */
    fun filledCylinder(
        x: Double, y1: Double, z: Double,
        y2: Double, radius: Double,
        color: Color,
        throughBlocks: Boolean = true,
        depthPriority: Boolean = false,
    )
}
