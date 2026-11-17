package org.blackaddons.core.features.impl.dungeons.map

import org.blackaddons.api.ui.render.BatchRect
import org.blackaddons.api.ui.render.Render
import org.blackaddons.core.features.impl.dungeons.map.MapRoomGeometry.MapGeometry
import java.awt.Color

internal object MapRoomRenderer {
    fun draw(originX: Float, originY: Float) {
        val geometry = MapRoomGeometry.get(originX, originY)
        val antiAliasRooms = DungeonMap.antiAliasRooms.value
        Render.withAntiAlias(antiAliasRooms) { drawRoomGeometry(geometry) }
        drawDoors(geometry.doors)

        geometry.checkmarks.forEach { MapOverlayRenderer.drawTileCheckmark(it.tile, it.x, it.y, it.size) }
    }

    private fun drawRoomGeometry(geometry: MapGeometry) {
        drawBatches(geometry.rooms, DungeonMap.roomCornerRadius.value.toFloat())
        geometry.lRooms.forEach { Render.fillShape(it.path, it.color) }
        geometry.directRooms.forEach { Render.rect(it.x, it.y, it.width, it.height, it.color, DungeonMap.roomCornerRadius.value) }
        geometry.predictions.forEach { drawPrediction(it.x, it.y, it.width, it.height, it.radius, it.colors) }
        drawBatches(geometry.fallbacks, DungeonMap.roomCornerRadius.value.toFloat())
    }

    private fun drawPrediction(x: Float, y: Float, width: Float, height: Float, radius: Float, colors: List<Color>) {
        when (colors.size) {
            1 -> Render.rect(x, y, width, height, colors[0], radius = radius)
            2 -> {
                val halfHeight = height / 2f
                drawPredictionSlice(x, y, width, height, radius, colors[0], x, y, width, halfHeight)
                drawPredictionSlice(x, y, width, height, radius, colors[1], x, y + halfHeight, width, height - halfHeight)
            }
            else -> {
                val fiveSixteenthsWidth = width * 5f / 16f
                val tenSixteenthsWidth = width * 10f / 16f
                val sixSixteenthsWidth = width * 6f / 16f
                val fiveSixteenthsHeight = height * 5f / 16f
                val tenSixteenthsHeight = height * 10f / 16f
                val sixSixteenthsHeight = height * 6f / 16f
                drawPredictionSlice(x, y, width, height, radius, colors[0], x, y, width, fiveSixteenthsHeight)
                drawPredictionSlice(x, y, width, height, radius, colors[0], x, y, fiveSixteenthsWidth, tenSixteenthsHeight)
                drawPredictionSlice(
                    x, y, width, height, radius, colors[1],
                    x + tenSixteenthsWidth, y + fiveSixteenthsHeight, width - tenSixteenthsWidth, height - fiveSixteenthsHeight,
                )
                drawPredictionSlice(
                    x, y, width, height, radius, colors[1],
                    x, y + tenSixteenthsHeight, width, height - tenSixteenthsHeight,
                )
                drawPredictionSlice(
                    x, y, width, height, radius, colors[2],
                    x + fiveSixteenthsWidth, y + fiveSixteenthsHeight, sixSixteenthsWidth, sixSixteenthsHeight,
                )
            }
        }
    }

    private fun drawPredictionSlice(
        roomX: Float,
        roomY: Float,
        roomWidth: Float,
        roomHeight: Float,
        radius: Float,
        color: Color,
        clipX: Float,
        clipY: Float,
        clipWidth: Float,
        clipHeight: Float,
    ) {
        Render.pushScissor(clipX, clipY, clipWidth, clipHeight)
        try {
            Render.rect(roomX, roomY, roomWidth, roomHeight, color, radius = radius)
        } finally {
            Render.popScissor()
        }
    }

    fun drawBatches(batches: Map<Color, List<BatchRect>>, radius: Float = 0f) {
        batches.forEach { (color, rects) -> Render.rects(rects, color, radius) }
    }

    private fun drawDoors(batches: Map<Color, List<BatchRect>>) {
        Render.withAntiAlias(false) { batches.forEach { (color, rects) -> Render.rects(rects, color) } }
    }

    fun drawDoorRects(rects: List<BatchRect>, color: Color) {
        Render.withAntiAlias(false) { Render.rects(rects, color) }
    }


    fun snapPreviewBatches(pixelGrid: MapPixelGrid?, batches: Map<Color, MutableList<BatchRect>>) =
        if (pixelGrid == null) batches else batches.mapValues { (_, rects) -> rects.map(pixelGrid::snap) }

    fun snapPreviewBounds(pixelGrid: MapPixelGrid?, bounds: List<BatchRect>) =
        if (pixelGrid == null) bounds else bounds.map(pixelGrid::snap)
}
