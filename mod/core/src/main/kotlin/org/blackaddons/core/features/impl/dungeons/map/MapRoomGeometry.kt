package org.blackaddons.core.features.impl.dungeons.map

import org.blackaddons.api.skyblock.dungeon.DoorType
import org.blackaddons.api.skyblock.dungeon.MapOverrides
import org.blackaddons.api.skyblock.dungeon.RoomShape
import org.blackaddons.api.skyblock.dungeon.RoomState
import org.blackaddons.api.skyblock.dungeon.RoomType
import org.blackaddons.api.ui.render.BatchRect
import org.blackaddons.api.ui.render.PathData
import org.blackaddons.api.ui.render.Render
import org.blackaddons.api.ui.render.ShapePath
import org.blackaddons.api.util.color.scaled
import org.blackaddons.core.dungeon.DungeonTracker
import org.blackaddons.core.dungeon.map.DoorTile
import org.blackaddons.core.dungeon.map.MapState
import org.blackaddons.core.dungeon.map.RoomPrediction
import org.blackaddons.core.dungeon.map.RoomTile
import org.blackaddons.core.dungeon.map.Tile
import java.awt.Color

internal object MapRoomGeometry {
    fun get(originX: Float, originY: Float): MapGeometry = MapGeometryCache.get(originX, originY)

    internal data class PredictionDraw(
        val x: Float,
        val y: Float,
        val width: Float,
        val height: Float,
        val radius: Float,
        val colors: List<Color>,
    )

    // Not a data class because I don't wanna override equals() for it.
    private class RawPolygon(val points: FloatArray, val color: Color)

    internal data class MapGeometry(
        val rooms: Map<Color, List<BatchRect>>,
        val lRooms: List<ColoredPolygon>,
        val directRooms: List<ColoredRect>,
        val fallbacks: Map<Color, List<BatchRect>>,
        val predictions: List<PredictionDraw>,
        val doors: Map<Color, List<BatchRect>>,
        val checkmarks: List<Checkmark>,
    ) {
        fun delete() = lRooms.forEach { Render.deleteShape(it.path) }
    }

    private fun buildGeometry(originX: Float, originY: Float, pixelGrid: MapPixelGrid?): MapGeometry {
        val roomSize = MapState.mapRoomSize.toFloat()
        val gap = (MapState.quarterRoom.takeUnless { it == -1 } ?: 4).toFloat()
        val stride = roomSize + gap
        val radius = DungeonMap.roomCornerRadius.value.toFloat()
        val showsUnrevealed = MapOverrides.showsUnrevealed

        val renderedTiles = BooleanArray(36)
        val checkmarks = ArrayList<Checkmark>()
        val roomBatches = linkedMapOf<Color, MutableList<BatchRect>>()
        val fallbackBatches = linkedMapOf<Color, MutableList<BatchRect>>()
        val doorBatches = linkedMapOf<Color, MutableList<BatchRect>>()
        val lRoomPolygons = ArrayList<RawPolygon>()
        val directRoomRects = ArrayList<ColoredRect>()
        val predictions = ArrayList<PredictionDraw>()
        val mimicColor = MapOverrides.mimicTint

        fun addRoomShape(roomCells: List<Pair<Int, Int>>, shape: RoomShape, color: Color) {
            for ((c, r) in roomCells) renderedTiles[r * 6 + c] = true

            val minC = roomCells.minOf { it.first }
            val maxC = roomCells.maxOf { it.first }
            val minR = roomCells.minOf { it.second }
            val maxR = roomCells.maxOf { it.second }

            val spanC = maxC - minC + 1
            val spanR = maxR - minR + 1

            when {
                spanC == 1 && spanR == 1 -> {
                    val x = originX + MapState.startCorner.first + minC * stride
                    val y = originY + MapState.startCorner.second + minR * stride
                    roomBatches.add(color, x, y, roomSize, roomSize)
                }

                spanR == 1 && spanC > 1 -> {
                    val x = originX + MapState.startCorner.first + minC * stride
                    val y = originY + MapState.startCorner.second + minR * stride
                    val w = spanC * roomSize + (spanC - 1) * gap
                    roomBatches.add(color, x, y, w, roomSize)
                }

                spanC == 1 && spanR > 1 -> {
                    val x = originX + MapState.startCorner.first + minC * stride
                    val y = originY + MapState.startCorner.second + minR * stride
                    val h = spanR * roomSize + (spanR - 1) * gap
                    roomBatches.add(color, x, y, roomSize, h)
                }

                spanC == 2 && spanR == 2 && roomCells.size == 4 -> {
                    val x = originX + MapState.startCorner.first + minC * stride
                    val y = originY + MapState.startCorner.second + minR * stride
                    val w = 2 * roomSize + gap
                    val h = 2 * roomSize + gap
                    roomBatches.add(color, x, y, w, h)
                }

                spanC == 2 && spanR == 2 && roomCells.size == 3 -> {
                    lRoomPolygon(roomCells, originX + MapState.startCorner.first + minC * stride, originY + MapState.startCorner.second + minR * stride, roomSize, gap)
                        ?.let { lRoomPolygons += RawPolygon(it, color) }
                }

                spanC == 2 && spanR == 2 && shape == RoomShape.SL -> {
                    val elbow = roomCells.find { (c, r) ->
                        val hasH = roomCells.contains((c + 1) to r) || roomCells.contains((c - 1) to r)
                        val hasV = roomCells.contains(c to (r + 1)) || roomCells.contains(c to (r - 1))
                        hasH && hasV
                    } ?: (minC to minR)

                    val hNeighbor = roomCells.find { it.second == elbow.second && it.first != elbow.first }
                    val vNeighbor = roomCells.find { it.first == elbow.first && it.second != elbow.second }

                    if (hNeighbor != null) {
                        val hMinC = minOf(elbow.first, hNeighbor.first)
                        val hX = originX + MapState.startCorner.first + hMinC * stride
                        val hY = originY + MapState.startCorner.second + elbow.second * stride
                        directRoomRects += ColoredRect(hX, hY, 2 * roomSize + gap, roomSize, color)
                    }

                    if (vNeighbor != null) {
                        val vMinR = minOf(elbow.second, vNeighbor.second)
                        val vX = originX + MapState.startCorner.first + elbow.first * stride
                        val vY = originY + MapState.startCorner.second + vMinR * stride
                        directRoomRects += ColoredRect(vX, vY, roomSize, 2 * roomSize + gap, color)
                    }
                }

                else -> for ((c, r) in roomCells) {
                    val cellX = originX + MapState.startCorner.first + c * stride
                    val cellY = originY + MapState.startCorner.second + r * stride
                    roomBatches.add(color, cellX, cellY, roomSize, roomSize)
                }
            }
        }

        for (unq in MapState.uniqueRooms.values) {
            val mainTile = unq.mainRoom
            if (mainTile.state == RoomState.UNDISCOVERED && !showsUnrevealed) continue

            var color = if (unq.hasMimic && mimicColor != null) mimicColor else mainTile.getColor()
            val isOpened = mainTile.state == RoomState.DISCOVERED || mainTile.state == RoomState.CLEARED || mainTile.state == RoomState.GREEN
            if (!isOpened && showsUnrevealed) color = undiscoveredColor(color)

            var roomCells = unq.mapCells()
            if (!showsUnrevealed) {
                roomCells = roomCells.filter { (c, r) ->
                    val tile = MapState.tileAt(c * 2, r * 2)
                    tile != null && tile.state != RoomState.UNDISCOVERED && tile.state != RoomState.UNOPENED
                }
            }

            if (roomCells.isEmpty()) continue
            addRoomShape(roomCells, unq.data.shape, color)
        }

        for (y in MapState.gridRange step 2) for (x in MapState.gridRange step 2) {
            if (renderedTiles[(y shr 1) * 6 + (x shr 1)]) continue
            val tile = MapState.tileAt(x, y) as? RoomTile ?: continue
            if (tile.state == RoomState.UNDISCOVERED || tile.state == RoomState.UNOPENED) continue
            val roomCells = MapState.unidentifiedRoomCells(x, y).map { (c, r) -> (c shr 1) to (r shr 1) }
            if (roomCells.size < 2) continue
            addRoomShape(roomCells, RoomShape.UNKNOWN, tile.getColor())

            val (checkC, checkR) = if (DungeonMap.centerCheckmark.value && roomCells.size != 3) {
                (roomCells.minOf { it.first } + roomCells.maxOf { it.first }) / 2f to
                    (roomCells.minOf { it.second } + roomCells.maxOf { it.second }) / 2f
            } else {
                roomCells.minWith(compareBy({ it.first }, { it.second })).let { (c, r) -> c.toFloat() to r.toFloat() }
            }
            val checkmarkSize = DungeonMap.checkmarkScale.value.toFloat() * 10
            checkmarks += Checkmark(
                tile,
                originX + MapState.startCorner.first + checkC * stride + roomSize / 2 - checkmarkSize / 2,
                originY + MapState.startCorner.second + checkR * stride + roomSize / 2 - checkmarkSize / 2,
                checkmarkSize,
            )
        }

        for (y in MapState.gridRange step 2) for (x in MapState.gridRange step 2) {
            if (renderedTiles[(y shr 1) * 6 + (x shr 1)]) continue

            val tile = MapState.tileAt(x, y) ?: continue
            if (tile !is RoomTile) continue
            if (!showsUnrevealed) {
                if (tile.state == RoomState.UNDISCOVERED) continue
                if (tile.state == RoomState.UNOPENED && !MapState.isVisibleRoomEntry(x, y)) continue
            }

            var color = tile.getColor()
            val isOpened = tile.state == RoomState.DISCOVERED || tile.state == RoomState.CLEARED || tile.state == RoomState.GREEN
            if (!isOpened && showsUnrevealed) color = undiscoveredColor(color)

            val xOffset = originX + MapState.startCorner.first + (x shr 1) * stride
            val yOffset = originY + MapState.startCorner.second + (y shr 1) * stride
            val prediction = if (!showsUnrevealed && DungeonMap.roomPrediction.value) {
                RoomPrediction.candidates(x, y, DungeonTracker.maxPuzzleCount).map(::predictionColor)
            } else {
                emptyList()
            }
            if (prediction.isEmpty()) {
                fallbackBatches.add(color, xOffset, yOffset, roomSize, roomSize)
            } else {
                predictions += PredictionDraw(xOffset, yOffset, roomSize, roomSize, radius, prediction)
            }

            if (tile.data.isUnknown() || tile.state == RoomState.UNOPENED) {
                val checkmarkSize = DungeonMap.checkmarkScale.value.toFloat() * 10
                checkmarks += Checkmark(
                    tile,
                    xOffset + roomSize / 2 - checkmarkSize / 2,
                    yOffset + roomSize / 2 - checkmarkSize / 2,
                    checkmarkSize,
                )
            }
        }

        for (y in MapState.gridRange) for (x in MapState.gridRange) {
            val tile = MapState.tileAt(x, y) ?: continue
            if (tile !is DoorTile) continue
            val state = tile.state
            if (state == RoomState.UNDISCOVERED && !showsUnrevealed) continue

            var color = tile.getColor()
            val isOpened = when (tile.type) {
                DoorType.ENTRANCE -> true
                DoorType.WITHER, DoorType.BLOOD -> tile.opened || state == RoomState.DISCOVERED
                DoorType.NORMAL -> {
                    val coloredRooms = tile.roomTiles.filter { it.data.type != RoomType.NORMAL }
                    val targetRoom = if (coloredRooms.size == 2) coloredRooms.find { it.data.type != RoomType.FAIRY }
                    else coloredRooms.firstOrNull()

                    if (targetRoom != null) {
                        targetRoom.state == RoomState.DISCOVERED || targetRoom.state == RoomState.CLEARED || targetRoom.state == RoomState.GREEN
                    } else {
                        tile.roomTiles.all { it.state == RoomState.DISCOVERED || it.state == RoomState.CLEARED || it.state == RoomState.GREEN }
                    }
                }
            }
            if (!isOpened && showsUnrevealed) color = undiscoveredColor(color)

            val xOffset = originX + MapState.startCorner.first + (x shr 1) * stride
            val yOffset = originY + MapState.startCorner.second + (y shr 1) * stride
            val bounds = roomConnectorBounds(xOffset, yOffset, gap, doorway = true, vertical = x and 1 != 0)
            doorBatches.add(color, bounds.x, bounds.y, bounds.width, bounds.height)
        }

        val recorder = PathData()
        return MapGeometry(
            pixelGrid.snapBatches(roomBatches),
            lRoomPolygons.map {
                recorder.clear()
                recorder.polygon(pixelGrid?.snap(it.points) ?: it.points, radius)
                ColoredPolygon(Render.createShape(recorder), it.color)
            },
            directRoomRects.map { rect -> pixelGrid?.snap(rect) ?: rect },
            pixelGrid.snapBatches(fallbackBatches),
            predictions.map { prediction -> pixelGrid?.snap(prediction) ?: prediction },
            pixelGrid.snapBatches(doorBatches),
            checkmarks,
        )
    }

    private fun predictionColor(type: RoomType): Color {
        val color = when (type) {
            RoomType.CHAMPION -> DungeonMap.colorMiniboss.value
            RoomType.PUZZLE -> DungeonMap.colorPuzzle.value
            RoomType.TRAP -> DungeonMap.colorTrap.value
            else -> DungeonMap.colorUnopened.value
        }
        return undiscoveredColor(color)
    }

    private fun undiscoveredColor(color: Color) = color.scaled(UNDISCOVERED_DARKEN_MULTIPLIER)

    private const val UNDISCOVERED_DARKEN_MULTIPLIER = 0.45f
    private fun roomConnectorBounds(x: Float, y: Float, doorWidth: Float, doorway: Boolean, vertical: Boolean): BatchRect {
        val doorSize = DungeonMap.doorWidth.value.toFloat()
        val doorwayOffset = (MapState.mapRoomSize - doorSize) / 2f
        val doorHeight = if (doorway) doorSize else MapState.mapRoomSize.toFloat()
        var x1 = if (vertical) x + MapState.mapRoomSize else x
        var y1 = if (vertical) y else y + MapState.mapRoomSize
        if (doorway) {
            if (vertical) y1 += doorwayOffset else x1 += doorwayOffset
        }
        val w = if (vertical) doorWidth else doorHeight
        val h = if (vertical) doorHeight else doorWidth
        return BatchRect(x1, y1, w, h)
    }

    private fun MapPixelGrid?.snapBatches(batches: Map<Color, MutableList<BatchRect>>): Map<Color, List<BatchRect>> =
        if (this == null) batches else batches.mapValues { (_, rects) -> rects.map(::snap) }

    private fun MapPixelGrid?.snap(bounds: List<BatchRect>): List<BatchRect> =
        if (this == null) bounds else bounds.map(::snap)

    private fun MapPixelGrid?.snap(rect: ColoredRect): ColoredRect {
        if (this == null) return rect
        val bounds = snap(BatchRect(rect.x, rect.y, rect.width, rect.height))
        return rect.copy(x = bounds.x, y = bounds.y, width = bounds.width, height = bounds.height)
    }

    private fun MapPixelGrid?.snap(prediction: PredictionDraw): PredictionDraw {
        if (this == null) return prediction
        val bounds = snap(BatchRect(prediction.x, prediction.y, prediction.width, prediction.height))
        return prediction.copy(x = bounds.x, y = bounds.y, width = bounds.width, height = bounds.height)
    }

    private fun MutableMap<Color, MutableList<BatchRect>>.add(color: Color, x: Float, y: Float, width: Float, height: Float) {
        getOrPut(color) { ArrayList() }.add(BatchRect(x, y, width, height))
    }

    private object MapGeometryCache {

        private var key = 0
        private var geometry: MapGeometry? = null

        fun get(originX: Float, originY: Float): MapGeometry {
            val pixelGrid = MapPixelGrid.current()
            val nextKey = fingerprint(originX, originY, pixelGrid)
            geometry?.takeIf { key == nextKey }?.let { return it }
            val nextGeometry = buildGeometry(originX, originY, pixelGrid)
            geometry?.delete()
            key = nextKey
            geometry = nextGeometry
            return nextGeometry
        }

        private fun fingerprint(originX: Float, originY: Float, pixelGrid: MapPixelGrid?): Int {
            var result = 17
            fun add(value: Int) { result = 31 * result + value }

            add(originX.toBits()); add(originY.toBits()); add(pixelGrid?.cacheKey ?: 0)
            add(MapState.mapRoomSize); add(MapState.quarterRoom)
            add(MapState.startCorner.first); add(MapState.startCorner.second)
            add(DungeonTracker.maxPuzzleCount)
            add(MapOverrides.showsUnrevealed.hashCode())
            add(DungeonMap.roomPrediction.value.hashCode())
            add(DungeonMap.roomCornerRadius.value.hashCode())
            add(DungeonMap.doorWidth.value.hashCode())
            add(DungeonMap.checkmarkScale.value.hashCode())
            add(DungeonMap.centerCheckmark.value.hashCode())
            add(DungeonMap.colorRoom.value.hashCode())
            add(DungeonMap.colorUnopened.value.hashCode())
            add(DungeonMap.colorEntrance.value.hashCode())
            add(DungeonMap.colorBlood.value.hashCode())
            add(DungeonMap.colorFairy.value.hashCode())
            add(DungeonMap.colorRare.value.hashCode())
            add(DungeonMap.colorMiniboss.value.hashCode())
            add(DungeonMap.colorPuzzle.value.hashCode())
            add(DungeonMap.colorTrap.value.hashCode())
            add(DungeonMap.colorRoomDoor.value.hashCode())
            add(DungeonMap.colorUnopenedDoor.value.hashCode())
            add(DungeonMap.colorEntranceDoor.value.hashCode())
            add(DungeonMap.colorBloodDoor.value.hashCode())
            add(DungeonMap.colorWitherDoor.value.hashCode())
            add(MapOverrides.mimicTint?.hashCode() ?: 0)

            for (tile in MapState.dungeonList) {
                add(tile.state.ordinal)
                when (tile) {
                    is RoomTile -> {
                        add(tile.data.type.ordinal)
                        add(tile.data.shape.ordinal)
                        add(tile.isSeparator.hashCode())
                    }
                    is DoorTile -> {
                        add(tile.type.ordinal)
                        add(tile.opened.hashCode())
                    }
                    else -> add(0)
                }
            }
            for (room in MapState.uniqueRooms.values) {
                add(room.name.hashCode())
                add(room.hasMimic.hashCode())
                add(room.tiles.size)
            }
            for ((gridX, gridZ) in MapState.visibleRoomEntries().sortedWith(compareBy<Pair<Int, Int>> { it.first }.thenBy { it.second })) {
                add(gridX)
                add(gridZ)
            }
            return result
        }
    }

    /** One continuous L, so antialiasing leaves no seam at the corner. */
    internal fun lRoomPolygon(cells: List<Pair<Int, Int>>, x: Float, y: Float, roomSize: Float, gap: Float): FloatArray? {
        if (cells.size != 3) return null
        val minColumn = cells.minOf { it.first }
        val minRow = cells.minOf { it.second }
        if (cells.maxOf { it.first } != minColumn + 1 || cells.maxOf { it.second } != minRow + 1) return null

        val occupied = cells.toSet()
        val missing = sequenceOf(
            minColumn to minRow,
            minColumn + 1 to minRow,
            minColumn + 1 to minRow + 1,
            minColumn to minRow + 1,
        ).singleOrNull { it !in occupied } ?: return null

        val innerRight = x + roomSize
        val outerRight = innerRight + gap + roomSize
        val innerBottom = y + roomSize
        val outerBottom = innerBottom + gap + roomSize
        val bridgeRight = innerRight + gap
        val bridgeBottom = innerBottom + gap

        return when (missing) {
            minColumn to minRow -> floatArrayOf(
                bridgeRight, y, outerRight, y, outerRight, outerBottom, x, outerBottom, x, bridgeBottom, bridgeRight, bridgeBottom,
            )
            minColumn + 1 to minRow -> floatArrayOf(
                x, y, innerRight, y, innerRight, bridgeBottom, outerRight, bridgeBottom, outerRight, outerBottom, x, outerBottom,
            )
            minColumn + 1 to minRow + 1 -> floatArrayOf(
                x, y, outerRight, y, outerRight, innerBottom, innerRight, innerBottom, innerRight, outerBottom, x, outerBottom,
            )
            minColumn to minRow + 1 -> floatArrayOf(
                x, y, outerRight, y, outerRight, outerBottom, bridgeRight, outerBottom, bridgeRight, innerBottom, x, innerBottom,
            )
            else -> null
        }
    }

    internal data class ColoredPolygon(val path: ShapePath, val color: Color)
    internal data class ColoredRect(val x: Float, val y: Float, val width: Float, val height: Float, val color: Color)
    internal data class Checkmark(val tile: Tile, val x: Float, val y: Float, val size: Float)
}
