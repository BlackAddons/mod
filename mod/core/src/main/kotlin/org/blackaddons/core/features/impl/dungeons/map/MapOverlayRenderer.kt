package org.blackaddons.core.features.impl.dungeons.map

import org.blackaddons.api.Platform
import org.blackaddons.api.hud.HudFontContext
import org.blackaddons.api.skyblock.dungeon.MapOverrides
import org.blackaddons.api.skyblock.dungeon.RoomState
import org.blackaddons.api.skyblock.dungeon.RoomType
import org.blackaddons.api.ui.render.CosmeticName
import org.blackaddons.api.ui.render.Render
import org.blackaddons.api.util.Uuids
import org.blackaddons.api.util.equalsIgnoreCase
import org.blackaddons.api.util.lerp
import org.blackaddons.api.world.ViewAngles
import org.blackaddons.core.cosmetics.PlayerCosmetics
import org.blackaddons.core.dungeon.DungeonTracker
import org.blackaddons.core.dungeon.ScoreCalculation
import org.blackaddons.core.dungeon.map.*
import java.awt.Color
import kotlin.time.Duration.Companion.milliseconds

internal object MapOverlayRenderer {
    fun drawLabels(originX: Float, originY: Float) {
        val roomSize = MapState.mapRoomSize.toFloat()
        val gap = (MapState.quarterRoom.takeUnless { it == -1 } ?: 4).toFloat()
        val halfRoom = MapState.halfRoom.toFloat()
        val stride = roomSize + gap
        val showsUnrevealed = MapOverrides.showsUnrevealed
        val font = HudFontContext.font

        for (unq in MapState.uniqueRooms.values) {
            val roomTile = unq.mainRoom
            if (unq.data.isUnknown()) continue
            if (unq.data.type == RoomType.ENTRANCE) continue
            if (!isVisible(roomTile, showsUnrevealed)) continue

            val checkPos = unq.getCheckmarkPosition(DungeonMap.centerCheckmark.value)
            val cX = originX + MapState.startCorner.first + (checkPos.first / 2f) * stride + halfRoom
            val cY = originY + MapState.startCorner.second + (checkPos.second / 2f) * stride + halfRoom

            val color = when {
                unq.isFullyCleared -> Color(85, 255, 85)
                roomTile.state == RoomState.FAILED -> Color(255, 0, 0)
                roomTile.state == RoomState.CLEARED -> Color.WHITE
                else -> Color(170, 170, 170)
            }

            val style = DungeonMap.checkmarkStyle.value
            when (style) {
                "Room Name", "Room Name + Secrets" -> {
                    val showSecrets = style == "Room Name + Secrets" &&
                        shouldShowRoomSecrets(unq.data, unq.foundSecrets)
                    val baseFontSize = 8.0f
                    var scale = DungeonMap.textScale.value.toFloat()

                    if (DungeonMap.limitRoomNameSize.value) {
                        unq.updateBounds(roomSize, gap)
                        val secretsText = if (showSecrets) "${unq.foundSecrets}/${roomTile.data.secrets}" else ""
                        var maxLineW = 0f
                        for (line in unq.cacheSplitName) {
                            val w = Render.textWidth(line, baseFontSize, font)
                            if (w > maxLineW) maxLineW = w
                        }
                        if (showSecrets) {
                            val w = Render.textWidth(secretsText, baseFontSize, font)
                            if (w > maxLineW) maxLineW = w
                        }

                        val lineH = Render.textHeight(baseFontSize, font)
                        val totalLines = unq.cacheSplitName.size + (if (showSecrets) 1 else 0)
                        val totalH = totalLines * lineH

                        if (maxLineW > 0 && totalH > 0) {
                            val sW = (unq.cachedMaxWidth * 0.9f) / maxLineW
                            val sH = (unq.cachedMaxHeight * 0.9f) / totalH
                            scale = minOf(sW, sH, DungeonMap.textScale.value.toFloat())
                        }
                    }

                    val finalFontSize = baseFontSize * scale
                    val lineH = Render.textHeight(finalFontSize, font)
                    val totalLines = unq.cacheSplitName.size + (if (showSecrets) 1 else 0)
                    val totalH = totalLines * lineH
                    var currentY = cY - totalH / 2f

                    for (line in unq.cacheSplitName) {
                        Render.textCenteredShadow(line, cX, currentY, finalFontSize, color, font)
                        currentY += lineH
                    }

                    if (showSecrets) {
                        val secStr = "${unq.foundSecrets}/${roomTile.data.secrets}"
                        Render.textCenteredShadow(secStr, cX, currentY, finalFontSize, color, font)
                    }
                }

                "Secrets" -> {
                    if (!shouldShowRoomSecrets(unq.data, unq.foundSecrets)) continue
                    val fontSize = 8.0f * DungeonMap.textScale.value.toFloat()
                    val text = "${unq.foundSecrets}/${roomTile.data.secrets}"
                    Render.textCenteredShadow(text, cX, cY, fontSize, color, font)
                }

                "Checkmarks" -> {
                    if (unq.data.type != RoomType.FAIRY && unq.data.type != RoomType.ENTRANCE) {
                        val checkmarkSize = DungeonMap.checkmarkScale.value.toFloat() * 10
                        drawCheckmark(
                            unq, cX - checkmarkSize / 2, cY - checkmarkSize / 2,
                            checkmarkSize,
                        )
                    }
                }
            }
        }
    }

    fun drawRoomIcons(originX: Float, originY: Float) {
        if (!DungeonMap.princeRoomIcon.value || ScoreCalculation.princeKilled) return
        val roomSize = MapState.mapRoomSize.toFloat()
        val gap = (MapState.quarterRoom.takeUnless { it == -1 } ?: 4).toFloat()
        val stride = roomSize + gap
        val showsUnrevealed = MapOverrides.showsUnrevealed
        val iconSize = MapIcons.roomIconSize(roomSize)
        for (room in MapState.uniqueRooms.values) {
            if (!room.data.containsPrince || !isVisible(room.mainRoom, showsUnrevealed)) continue
            val cell = room.mapCells().maxWithOrNull(
                compareBy<Pair<Int, Int>> { it.first }.thenBy { it.second },
            ) ?: continue
            val roomX = originX + MapState.startCorner.first + cell.first * stride
            val roomY = originY + MapState.startCorner.second + cell.second * stride
            MapIcons.drawPrinceRoomIcon(
                roomX + roomSize - iconSize - ROOM_ICON_INSET,
                roomY + roomSize - iconSize - ROOM_ICON_INSET,
                iconSize,
            )
        }
    }

    private fun isVisible(room: RoomTile, showsUnrevealed: Boolean) =
        showsUnrevealed || (room.state != RoomState.UNDISCOVERED && room.state != RoomState.UNOPENED)

    internal fun shouldShowRoomSecrets(data: RoomData, foundSecrets: Int) =
        foundSecrets > 0 && data.secrets > 0 && data.type != RoomType.FAIRY && data.type != RoomType.ENTRANCE

    private fun drawCheckmark(unq: UniqueRoom, x: Float, y: Float, size: Float) {
        if (unq.data.type == RoomType.FAIRY || unq.data.type == RoomType.ENTRANCE) return
        val checkmark = when {
            unq.isFullyCleared -> MapIcons.checkmarkGreen
            unq.mainRoom.state == RoomState.FAILED -> MapIcons.checkmarkFail
            unq.mainRoom.state == RoomState.CLEARED -> MapIcons.checkmarkWhite
            unq.mainRoom.state == RoomState.UNOPENED -> MapIcons.checkmarkUnknown
            else -> return
        }
        if (checkmark == MapIcons.checkmarkUnknown && DungeonMap.hideUnknownCheckmark.value) return
        Render.image(checkmark, x, y, size, size)
    }

    fun drawPlayerHeads(originX: Float, originY: Float) {
        var drewSelf = false
        for (player in MapState.mapPlayers) {
            if (player.isDead) continue
            if (player.name.equalsIgnoreCase(Platform.player.name ?: "")) drewSelf = true
            updatePlayerLerp(player)
            drawPlayerHead(originX, originY, player)
        }
        if (!drewSelf && MapState.calibrated) {
            val selfName = Platform.player.name ?: return
            val fallback = MapPlayer(selfName, selfName, null)
            drawPlayerHead(originX, originY, fallback)
        }
    }

    private fun drawPlayerHead(originX: Float, originY: Float, player: MapPlayer) {
        val headSize = 10f
        val halfHead = headSize / 2f

        val isSelf = player.name.equalsIgnoreCase(Platform.player.name ?: "")
        val (px, py, yaw) = if (isSelf) {
            val (mx, mz) = MapState.worldToMap(Platform.player.x, Platform.player.z)
            Triple(originX + mx, originY + mz, Platform.player.yaw)
        } else {
            Triple(originX + player.mapX, originY + player.mapZ, player.yaw)
        }

        val headYaw = Math.toRadians((ViewAngles.wrapYaw(yaw) + 180).toDouble()).toFloat()

        Render.push()
        Render.translate(px, py)
        Render.rotate(headYaw)
        val scale = DungeonMap.playerHeadScale.value.toFloat()
        Render.scale(scale, scale)

        if (DungeonMap.customMarker.value && isSelf) {
            Render.image(MapIcons.ownPlayerMarker, -halfHead, -halfHead, headSize, headSize)
        } else {
            val tm = DungeonTracker.teammate(player.name)
            val borderColor = if (DungeonMap.headBorderClassBased.value) {
                DungeonMap.classColor(tm?.dungeonClass)
            } else {
                DungeonMap.headBorderColor.value
            }

            Render.rect(-halfHead - 1f, -halfHead - 1f, headSize + 2f, headSize + 2f, borderColor, radius = 1f)
            Render.playerFace(player.name, -halfHead, -halfHead, headSize, headSize)
            if (DungeonMap.headDirectionIndicator.value) {
                Render.rect(-1.5f, -halfHead - 2.5f, 3f, 2f, borderColor, radius = 0.5f)
            }
        }

        val shouldDrawName = DungeonMap.playerNames.value == "Always"
            || (DungeonMap.playerNames.value == "Holding Leap" && isHoldingLeap())

        if (shouldDrawName) {
            Render.rotate(-headYaw)
            Render.translate(0f, headSize / 2f + 3f)
            val nameScale = DungeonMap.playerNameScale.value.toFloat()
            val fontSize = 6.0f * nameScale
            val font = HudFontContext.font
            val tm = DungeonTracker.teammate(player.name)
            val nameColor = if (DungeonMap.playerNameClassBased.value) {
                DungeonMap.classColor(tm?.dungeonClass)
            } else Color.WHITE
            val cosmetic = PlayerCosmetics.nameFor(player.name, Uuids.parse(player.uuid))
            val nameW = CosmeticName.width(cosmetic, player.name, fontSize, font)
            CosmeticName.draw(cosmetic, player.name, -nameW / 2f, 0f, fontSize, font, nameColor)
        }

        Render.pop()
    }

    private fun updatePlayerLerp(player: MapPlayer) {
        val t = player.lerpStartedAt?.let { (it.elapsedNow() / 350.milliseconds).toFloat().coerceAtMost(1f) } ?: 1f

        player.mapX = lerp(player.lerpStartX, player.targetX, t)
        player.mapZ = lerp(player.lerpStartZ, player.targetZ, t)
        player.yaw = interpolateYaw(player.lerpStartYaw, player.targetYaw, t)
    }


    fun drawTileCheckmark(tile: Tile, x: Float, y: Float, size: Float) {
        val roomType = (tile as? RoomTile)?.data?.type
        if (roomType == RoomType.FAIRY || roomType == RoomType.ENTRANCE) return
        val isFullyCleared = (tile as? RoomTile)?.uniqueRoom?.isFullyCleared ?: (tile.state == RoomState.GREEN)
        val image = when {
            isFullyCleared -> MapIcons.checkmarkGreen
            tile.state == RoomState.FAILED -> MapIcons.checkmarkFail
            tile.state == RoomState.CLEARED -> MapIcons.checkmarkWhite
            tile.state == RoomState.UNOPENED -> if (!DungeonMap.hideUnknownCheckmark.value) MapIcons.checkmarkUnknown else return
            else -> return
        }
        Render.image(image, x, y, size, size)
    }

    private fun isHoldingLeap(): Boolean {
        val name = Platform.player.mainHandItem?.name ?: return false
        return name.contains("Spirit Leap", ignoreCase = true) ||
            name.contains("Infinileap", ignoreCase = true) ||
            name.contains("Haunt", ignoreCase = true)
    }

    private fun interpolateYaw(start: Float, end: Float, t: Float): Float {
        val diff = ((end - start) % 360 + 540) % 360 - 180
        return start + diff * t
    }

    private const val ROOM_ICON_INSET = 1f
}
