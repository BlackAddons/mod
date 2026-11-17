package org.blackaddons.platform.font

import net.minecraft.client.Minecraft
import org.blackaddons.core.font.GlobalFontRequest
import org.joml.Matrix4f
import org.joml.Matrix4fc
import kotlin.math.sqrt
import kotlin.math.tan

object GlobalFontEffects {
    @JvmStatic
    var suppressed = false
        private set

    @JvmStatic
    fun beginVanillaOutline() {
        suppressed = true
    }

    @JvmStatic
    fun endVanillaOutline() {
        suppressed = false
    }

    @JvmStatic
    var glowOutline = 0
}

object GlobalFontRenderContext {
    const val UNKNOWN = 0
    const val SCREEN = 1
    const val WORLD = 2
    const val SCREEN_2X = 3
    const val SCREEN_4X = 4

    @JvmStatic
    var current = UNKNOWN
        private set

    @JvmStatic
    fun begin(next: Int): Int = current.also { current = next }

    @JvmStatic
    fun beginScreen(scale: Float): Int = begin(when {
        !scale.isFinite() || scale <= 1f -> SCREEN
        scale <= 2f -> SCREEN_2X
        else -> SCREEN_4X
    })

    @JvmStatic
    fun restore(previous: Int) {
        current = previous
    }

    @JvmStatic
    var worldPixelsPerUnit = Float.NaN
        private set

    @JvmStatic
    fun beginWorldText(): Int = begin(if (current == UNKNOWN) WORLD else current)

    @JvmStatic
    fun beginWorldDensity(pose: Matrix4fc): Float {
        val distance = length(pose.m30(), pose.m31(), pose.m32()).coerceAtLeast(MIN_DISTANCE)
        val unit = length(pose.m00(), pose.m01(), pose.m02())
        if (!lastPose.equals(pose, 0f)) {
            lastPose.set(pose)
            worldGlyphs = 0
        }
        return worldPixelsPerUnit.also { worldPixelsPerUnit = unit / distance * pixelsPerRadian() }
    }

    @JvmStatic
    fun restoreWorldDensity(previous: Float) {
        worldPixelsPerUnit = previous
    }

    @JvmStatic
    fun resetWorldGlyphOrder() {
        worldGlyphs = 0
        lastPose.zero()
    }

    var worldGlyphs = 0
        private set
    private val lastPose = Matrix4f().zero()

    fun nextWorldGlyphDepth(): Float = worldGlyphs++ * GLYPH_DEPTH_STEP

    fun skipWorldGlyphs(count: Int) {
        worldGlyphs += count
    }

    /* Using a coarser bucket while the sharper bake is still in progress. */
    var provisional = false

    private var bucketRequest: GlobalFontRequest? = null
    private var bucketDensity = Float.NaN
    private var bucket = 0

    fun worldBucket(request: GlobalFontRequest): Int {
        val density = worldPixelsPerUnit
        if (request !== bucketRequest || density.toRawBits() != bucketDensity.toRawBits()) {
            bucketRequest = request
            bucketDensity = density
            bucket = request.worldBucket(density)
        }
        return bucket
    }

    private var radianHeight = -1
    private var radianFov = -1
    private var radianPixels = 0f

    private fun pixelsPerRadian(): Float {
        val mc = Minecraft.getInstance()
        val height = mc.window.height
        val fov = mc.options.fov().get()
        if (height != radianHeight || fov != radianFov) {
            radianHeight = height
            radianFov = fov
            radianPixels = height / (2f * tan(Math.toRadians(fov / 2.0)).toFloat())
        }
        return radianPixels
    }

    private fun length(x: Float, y: Float, z: Float) = sqrt(x * x + y * y + z * z)

    private const val MIN_DISTANCE = 0.05f
    private const val GLYPH_DEPTH_STEP = 0.003f
}
