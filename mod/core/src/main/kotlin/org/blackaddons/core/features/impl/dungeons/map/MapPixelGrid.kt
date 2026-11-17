package org.blackaddons.core.features.impl.dungeons.map

import org.blackaddons.api.ui.render.BatchRect
import org.blackaddons.api.ui.render.Render
import org.blackaddons.api.util.near
import org.blackaddons.api.util.nearZero
import kotlin.math.round

/** Snaps shared map edges to physical pixels when the HUD is axis-aligned. */
internal data class MapPixelGrid(
    private val screenOriginX: Float,
    private val screenOriginY: Float,
    private val screenScaleX: Float,
    private val screenScaleY: Float,
) {
    val cacheKey: Int
        get() {
            var result = 17
            result = 31 * result + screenOriginX.toBits()
            result = 31 * result + screenOriginY.toBits()
            result = 31 * result + screenScaleX.toBits()
            return 31 * result + screenScaleY.toBits()
        }

    fun snap(bounds: BatchRect): BatchRect {
        val left = snapX(bounds.x)
        val top = snapY(bounds.y)
        return BatchRect(left, top, snapX(bounds.x + bounds.width) - left, snapY(bounds.y + bounds.height) - top)
    }

    fun snap(points: FloatArray) = FloatArray(points.size) { index ->
        if (index and 1 == 0) snapX(points[index]) else snapY(points[index])
    }

    private fun snapX(value: Float) = (round(screenOriginX + value * screenScaleX) - screenOriginX) / screenScaleX

    private fun snapY(value: Float) = (round(screenOriginY + value * screenScaleY) - screenOriginY) / screenScaleY

    companion object {
        fun current(): MapPixelGrid? {
            val origin = Render.toScreen(0f, 0f)
            val xAxis = Render.toScreen(1f, 0f)
            val yAxis = Render.toScreen(0f, 1f)
            val scaleX = xAxis[0] - origin[0]
            val scaleY = yAxis[1] - origin[1]
            if (
                !xAxis[1].near(origin[1]) ||
                !yAxis[0].near(origin[0]) ||
                scaleX.nearZero() ||
                scaleY.nearZero()
            ) return null
            return MapPixelGrid(origin[0], origin[1], scaleX, scaleY)
        }
    }
}
