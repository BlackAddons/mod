package org.blackaddons.core.features.impl.dungeons.map

import org.blackaddons.api.Platform
import org.blackaddons.api.feature.Feature
import org.blackaddons.api.hud.AnchorY
import org.blackaddons.api.hud.HudElement
import org.blackaddons.api.hud.HudFontContext
import org.blackaddons.api.skyblock.LocationUtils
import org.blackaddons.api.skyblock.dungeon.MapOverrides
import org.blackaddons.api.ui.layout.Cell
import org.blackaddons.api.ui.layout.LayoutNode
import org.blackaddons.api.ui.layout.centered
import org.blackaddons.api.ui.render.BatchRect
import org.blackaddons.api.ui.render.Render
import org.blackaddons.api.ui.theme.FontChoice
import org.blackaddons.core.dungeon.DungeonTracker
import org.blackaddons.core.dungeon.ScoreCalculation
import org.blackaddons.core.dungeon.map.MapState
import org.blackaddons.core.dungeon.map.Unknown
import org.blackaddons.core.features.impl.dungeons.soloclears.SoloClears
import java.awt.Color
import kotlin.math.roundToInt

object MapRenderer {

    fun register(feature: Feature) {
        val element = object : HudElement() {
            override val name = "Dungeon Map"
            override val toggle: Boolean get() = feature.enabled
            override val shouldDraw: Boolean
                get() = LocationUtils.inDungeon && (!DungeonMap.hideInBoss.value || !LocationUtils.inBoss || isBossPeeking())
            override val resizable = true
            override val defaultAnchorY = AnchorY.MIDDLE
            override val defaultOffsetX = 70f
            override val defaultOffsetY = -70f
            override val defaultScale = 1.331f
            override val defaultFontOverride: FontChoice get() = FontChoice.INHERIT
            override fun content(example: Boolean): LayoutNode = buildMap(example)
        }
        element.resetToDefault()
        feature.hudElements.add(element)
    }

    const val MAP_SIZE = 128f

    fun drawLayers(x: Float, y: Float) {
        val cx = x + centerOffsetX
        val cy = y + centerOffsetY
        MapRoomRenderer.draw(cx, cy)
        MapOverlayRenderer.drawRoomIcons(cx, cy)
        MapOverlayRenderer.drawLabels(cx, cy)
        MapOverlayRenderer.drawPlayerHeads(cx, cy)
    }

    val centerOffsetX: Float get() = centeringOffset(MapState.startCorner.first, MapState.mapRoomSize)
    val centerOffsetY: Float get() = centeringOffset(MapState.startCorner.second, MapState.mapRoomSize)

    internal fun centeringOffset(corner: Int, roomSize: Int): Float {
        val stride = roomSize + ROOM_GAP
        val rooms = (MAP_SIZE.toInt() - 2 * corner + ROOM_GAP) / stride
        return (MAP_SIZE - (rooms * stride - ROOM_GAP)) / 2f - corner
    }

    private const val ROOM_GAP = 4

    private fun isBossPeeking(): Boolean =
        DungeonMap.bossPeekKey.isPressed && !Platform.screenHost.anyScreenOpen

    private fun buildMap(example: Boolean): LayoutNode {
        val mapWidth = MAP_SIZE
        val mapHeight = MAP_SIZE
        val hasLine1 = DungeonMap.showExtraInfo.value && (DungeonMap.extraInfoSecrets.value || DungeonMap.extraInfoCrypts.value)
        val hasLine2 = DungeonMap.showExtraInfo.value && (DungeonMap.extraInfoScore.value || DungeonMap.extraInfoDeaths.value || DungeonMap.extraInfoBonusStyle.value != "Off")
        val isCustomIcons = DungeonMap.extraInfoBonusStyle.value == "Custom Icons"
        val extraHeight = when {
            hasLine1 && hasLine2 -> if (isCustomIcons) 17f else 15f
            hasLine1 -> 9f
            hasLine2 -> if (isCustomIcons) 11f else 9f
            else -> 0f
        }
        val totalHeight = mapHeight + extraHeight

        return Cell(mapWidth, totalHeight) { x, y ->
            drawBackground(x, y, mapWidth, totalHeight)

            val hasDungeonMap = MapState.calibrated || MapState.dungeonList.any { it !is Unknown }
            if (example) {
                drawExampleMap(x, y, mapWidth, "3x3")
            } else if (hasDungeonMap) {
                drawLayers(x, y)
            } else {
                val font = HudFontContext.font
                val text = "?"
                val tw = Render.textWidth(text, 24f, font)
                val th = Render.textHeight(24f, font)
                Render.text(text, x + mapWidth / 2 - tw / 2, y + mapHeight / 2 - th / 2, 24f, Color.GRAY, font)
            }

            if (DungeonMap.showExtraInfo.value) {
                drawExtraInfo(x, y, mapWidth, mapHeight, example, hasLine1, hasLine2)
            }
        }
    }

    private fun drawBackground(x: Float, y: Float, width: Float, height: Float) {
        val bg = DungeonMap.mapBackground.value
        val border = DungeonMap.mapBorderColor.value
        val bw = DungeonMap.borderWidth.value.toFloat()
        val pad = if (bw > 0) BORDER_GAP else 0f
        val bx = x - pad
        val by = y - pad
        val bWidth = width + pad * 2f
        val bHeight = height + pad * 2f
        val radius = DungeonMap.roomCornerRadius.value.toFloat() + pad
        val opacity = backgroundOpacity(DungeonMap.backgroundTransparency.value)
        if (opacity > 0f) {
            Render.push()
            Render.globalAlpha(opacity)
            Render.rect(bx, by, bWidth, bHeight, bg, radius = radius)
            DungeonMap.selectedCustomBackgroundFile()?.let(::customBackgroundPath)?.let { Render.image(it, bx, by, bWidth, bHeight, radius) }
            backgroundDarkeningColor(DungeonMap.backgroundDarkness.value)?.let { Render.rect(bx, by, bWidth, bHeight, it, radius) }
            Render.pop()
        }
        if (bw > 0) Render.hollowRect(bx - bw, by - bw, bWidth + bw * 2f, bHeight + bw * 2f, bw, border, radius = radius + bw)
    }

    private const val BORDER_GAP = 3f

    internal fun customBackgroundPath(value: String): String? =
        value.trim().takeIf { it.isNotEmpty() }?.let { Render.FILE_SCHEME + it }

    internal fun backgroundDarkeningColor(percent: Double): Color? =
        (percent.coerceIn(0.0, 100.0) * 255 / 100).roundToInt().takeIf { it > 0 }?.let { Color(0, 0, 0, it) }

    internal fun backgroundOpacity(transparency: Double): Float =
        (1.0 - transparency.coerceIn(0.0, 100.0) / 100.0).toFloat()

    /**
     * On a board floor, shows the board total, with the in-game score in gray when they differ (mayor perk). The
     * separate Solo Clear indicator shows if the mode is on.
     */
    private fun soloClearScore(score: Int, soloClear: Boolean): String {
        val colored = ScoreCalculation.colorizeScore(score)
        val game = ScoreCalculation.score
        val aside = if (soloClear && game != score) " §8(§7$game§8)" else ""
        return "§eScore: $colored$aside§r"
    }

    private fun drawExtraInfo(
        originX: Float,
        originY: Float,
        width: Float,
        mapHeight: Float,
        example: Boolean,
        hasLine1: Boolean,
        hasLine2: Boolean,
    ) {
        if (!hasLine1 && !hasLine2) return

        val foundSecrets = if (example) 5 else maxOf(ScoreCalculation.foundSecrets, MapState.uniqueRooms.values.sumOf { it.foundSecrets })
        val scannedSecrets = if (MapState.secretCount > 0) MapState.secretCount else MapState.uniqueRooms.values.sumOf { it.data.secrets }
        val totalSecrets = if (example) 56 else visibleSecretTotal(
            foundSecrets = foundSecrets,
            scannedSecrets = scannedSecrets,
            estimatedSecrets = ScoreCalculation.estimatedTotalSecrets(),
            showsUnrevealed = MapOverrides.showsUnrevealed,
        )
        val secretsFor300 = if (example) 40 else ScoreCalculation.getSecretsFor300(totalSecrets)
        val remainingSecrets = if (example) 35 else ScoreCalculation.getRemainingSecretsFor300(foundSecrets, totalSecrets)
        val displayedSecrets = if (example) {
            if (DungeonMap.calculationMode.value == DungeonMap.CALCULATION_OPTIMISTIC && DungeonMap.showNeededSecrets.value) 40 else 35
        } else {
            ScoreCalculation.getDisplayedSecretsFor300(foundSecrets, totalSecrets)
        }
        val crypts = if (example) 0 else ScoreCalculation.cryptsCount
        val deaths = if (example) 0 else maxOf(ScoreCalculation.deathCount, DungeonTracker.dungeonTeammates.count { it.isDead })
        val soloClear = !example && SoloClears.enabled && LocationUtils.isF7orM7
        val shown = if (soloClear) ScoreCalculation.targetScore else ScoreCalculation.score
        val score = if (example) 120 else (if (shown > 0) shown else 120)
        val mimic = if (example) false else ScoreCalculation.mimicKilled
        val prince = if (example) false else ScoreCalculation.princeKilled
        val bat = if (example) false else ScoreCalculation.batKilled

        val font = HudFontContext.font
        val fontSize = 6.2f

        if (hasLine1) {
            val foundColor = if (remainingSecrets == 0 && secretsFor300 > 0) "§a" else "§b"
            val secretsStr = if (DungeonMap.extraInfoSecrets.value) "§7Secrets: $foundColor$foundSecrets§8-§e$displayedSecrets§8-§c$totalSecrets" else null
            val cryptsStr = if (DungeonMap.extraInfoCrypts.value) ScoreCalculation.colorCodeCrypts(crypts) + "Crypts: $crypts" else null
            val line1 = listOfNotNull(secretsStr, cryptsStr).joinToString("    ")
            val tw1 = Render.formattedTextWidth(line1, fontSize, font)
            val line1Y = if (hasLine2) (originY + mapHeight + 1.0f) else (originY + mapHeight + 2.0f)
            Render.formattedText(line1, centered(originX, width, tw1), line1Y, fontSize, font, Color.WHITE)
        }

        if (hasLine2) {
            val bonusMode = DungeonMap.extraInfoBonusStyle.value
            val line2Y = if (hasLine1) (originY + mapHeight + if (bonusMode == "Custom Icons") 7.5f else 7.0f) else (originY + mapHeight + 2.0f)
            val scoreStr = if (DungeonMap.extraInfoScore.value) soloClearScore(score, soloClear) else null
            val deathsStr = if (DungeonMap.extraInfoDeaths.value) "§cDeaths: " + ScoreCalculation.colorCodeByPercent(deaths, 4, reversed = true) + "$deaths§r" else null

            when (bonusMode) {
                "Letters" -> {
                    val bonusStr = ScoreCalculation.formatBonus(mimic, prince, bat)
                    val fullLine2 = listOfNotNull(scoreStr, deathsStr, bonusStr).joinToString("   ")
                    val tw2 = Render.formattedTextWidth(fullLine2, fontSize, font)
                    Render.formattedText(fullLine2, centered(originX, width, tw2), line2Y, fontSize, font, Color.WHITE)
                }
                "Custom Icons" -> {
                    val scoreW = scoreStr?.let { Render.formattedTextWidth(it, fontSize, font) }
                    val deathsW = deathsStr?.let { Render.formattedTextWidth(it, fontSize, font) }

                    val iconSize = 8.0f
                    val iconGap = 2.0f
                    val bonusW = 3f * iconSize + 2f * iconGap
                    val sectionGap = 4.0f

                    var totalLine2W = bonusW
                    if (scoreW != null) totalLine2W += scoreW + sectionGap
                    if (deathsW != null) totalLine2W += deathsW + sectionGap

                    var curX = centered(originX, width, totalLine2W)

                    if (scoreStr != null && scoreW != null) {
                        Render.formattedText(scoreStr, curX, line2Y, fontSize, font, Color.WHITE)
                        curX += scoreW + sectionGap
                    }
                    if (deathsStr != null && deathsW != null) {
                        Render.formattedText(deathsStr, curX, line2Y, fontSize, font, Color.WHITE)
                        curX += deathsW + sectionGap
                    }

                    val iconY = line2Y - 1.0f
                    val mimicImg = if (mimic) MapIcons.mimicGreen else MapIcons.mimicRed
                    val princeImg = if (prince) MapIcons.princeGreen else MapIcons.princeRed
                    val batImg = if (bat) MapIcons.batGreen else MapIcons.batRed

                    Render.image(mimicImg, curX, iconY, iconSize, iconSize)
                    curX += iconSize + iconGap
                    Render.image(princeImg, curX, iconY, iconSize, iconSize)
                    curX += iconSize + iconGap
                    Render.image(batImg, curX, iconY, iconSize, iconSize)
                }
                "Off" -> {
                    val fullLine2 = listOfNotNull(scoreStr, deathsStr).joinToString("   ")
                    if (fullLine2.isNotEmpty()) {
                        val tw2 = Render.formattedTextWidth(fullLine2, fontSize, font)
                        Render.formattedText(fullLine2, centered(originX, width, tw2), line2Y, fontSize, font, Color.WHITE)
                    }
                }
            }
        }
    }

    internal fun visibleSecretTotal(
        foundSecrets: Int,
        scannedSecrets: Int,
        estimatedSecrets: Int,
        showsUnrevealed: Boolean,
    ): Int {
        if (!showsUnrevealed && foundSecrets == 0) return 0
        return if (showsUnrevealed) scannedSecrets else estimatedSecrets
    }

    private fun drawExampleMap(x: Float, y: Float, size: Float, mode: String = "3x3") {
        val (grid, roomSize, gap) = when (mode) {
            "6x6" -> Triple(6, 16f, 3.5f)
            "5x5" -> Triple(5, 18f, 4.0f)
            "4x4" -> Triple(4, 22f, 5.0f)
            else -> Triple(3, 28f, 6.0f)
        }

        val radius = DungeonMap.roomCornerRadius.value.toFloat()
        val totalGridW = grid * roomSize + (grid - 1) * gap
        val startX = centered(x, size, totalGridW)
        val startY = centered(y, size, totalGridW)

        val poolColors = listOf(
            DungeonMap.colorEntrance.value, DungeonMap.colorRoom.value, DungeonMap.colorPuzzle.value,
            DungeonMap.colorRoom.value, DungeonMap.colorFairy.value, DungeonMap.colorRoom.value,
            DungeonMap.colorTrap.value, DungeonMap.colorRare.value, DungeonMap.colorBlood.value,
            DungeonMap.colorRoom.value, DungeonMap.colorMiniboss.value, DungeonMap.colorRoom.value,
        )

        val rooms = linkedMapOf<Color, MutableList<BatchRect>>()
        val doors = ArrayList<BatchRect>()
        val doorSize = DungeonMap.doorWidth.value.toFloat()
        for (row in 0 until grid) for (col in 0 until grid) {
            val rx = startX + col * (roomSize + gap)
            val ry = startY + row * (roomSize + gap)
            val color = poolColors[(row * grid + col) % poolColors.size]
            rooms.getOrPut(color) { ArrayList() }.add(BatchRect(rx, ry, roomSize, roomSize))

            if (col < grid - 1) doors += BatchRect(rx + roomSize, ry + (roomSize - doorSize) / 2f, gap, doorSize)
            if (row < grid - 1) doors += BatchRect(rx + (roomSize - doorSize) / 2f, ry + roomSize, doorSize, gap)
        }
        val pixelGrid = MapPixelGrid.current()
        val antiAliasRooms = DungeonMap.antiAliasRooms.value
        Render.withAntiAlias(antiAliasRooms) { MapRoomRenderer.drawBatches(MapRoomRenderer.snapPreviewBatches(pixelGrid, rooms), radius) }
        MapRoomRenderer.drawDoorRects(MapRoomRenderer.snapPreviewBounds(pixelGrid, doors), DungeonMap.colorRoomDoor.value)

        val font = HudFontContext.font
        val style = DungeonMap.checkmarkStyle.value
        val showSecrets = style == "Room Name + Secrets"

        val sampleNames = listOf(
            "Entrance" to "", "Mines" to "3/4", "Quiz" to "0/1",
            "Stairs" to "2/2", "Fairy" to "", "Rails" to "1/3",
            "Trap" to "0/1", "Crypt" to "4/4", "Blood" to "",
            "Altar" to "2/4", "Well" to "1/2", "Vault" to "0/1",
        )

        val sampleCheckmarks = listOf(
            null, MapIcons.checkmarkGreen, MapIcons.checkmarkWhite,
            MapIcons.checkmarkFail, MapIcons.checkmarkUnknown, MapIcons.checkmarkGreen,
            MapIcons.checkmarkWhite, MapIcons.checkmarkUnknown, MapIcons.checkmarkWhite,
            MapIcons.checkmarkGreen, MapIcons.checkmarkWhite, MapIcons.checkmarkFail,
        )

        for (row in 0 until grid) for (col in 0 until grid) {
            val idx = (row * grid + col) % sampleNames.size
            val rx = startX + col * (roomSize + gap)
            val ry = startY + row * (roomSize + gap)
            val cx = rx + roomSize / 2f
            val cy = ry + roomSize / 2f

            if (DungeonMap.princeRoomIcon.value && idx == 5) {
                val iconSize = MapIcons.roomIconSize(roomSize)
                MapIcons.drawPrinceRoomIcon(rx + roomSize - iconSize - 1f, ry + roomSize - iconSize - 1f, iconSize)
            }

            when (style) {
                "Checkmarks" -> {
                    if (row == 0 && col == 0) {
                        val markerSize = (roomSize * 0.65f) * DungeonMap.playerHeadScale.value.toFloat()
                        Render.image(MapIcons.ownPlayerMarker, cx - markerSize / 2f, cy - markerSize / 2f, markerSize, markerSize)
                    } else if (!MapOverrides.showsUnrevealed) {
                        val checkmark = sampleCheckmarks[idx] ?: continue
                        if (checkmark == MapIcons.checkmarkUnknown && DungeonMap.hideUnknownCheckmark.value) continue
                        val checkmarkSize = (roomSize * 0.6f) * DungeonMap.checkmarkScale.value.toFloat()
                        Render.image(checkmark, cx - checkmarkSize / 2f, cy - checkmarkSize / 2f, checkmarkSize, checkmarkSize)
                    }
                }
                "Secrets" -> {
                    val sec = sampleNames[idx].second
                    if (sec.isNotEmpty()) {
                        val fontSize = (roomSize * 0.35f) * DungeonMap.textScale.value.toFloat()
                        Render.textCenteredShadow(sec, cx, cy, fontSize, Color.WHITE, font)
                    }
                }
                "Room Name", "Room Name + Secrets" -> {
                    val (name, sec) = sampleNames[idx]
                    val maxW = roomSize - 2f
                    var fontSize = (roomSize * 0.28f) * DungeonMap.textScale.value.toFloat()
                    val baseW = Render.textWidth(name, fontSize, font)
                    if (baseW > maxW) {
                        fontSize *= (maxW / baseW).coerceIn(0.35f, 1.0f)
                    }
                    val lineH = Render.textHeight(fontSize, font)
                    if (showSecrets && sec.isNotEmpty()) {
                        val totalH = lineH * 2f
                        val startTextY = cy - totalH / 2f
                        Render.textCenteredShadow(name, cx, startTextY, fontSize, Color.WHITE, font)
                        Render.textCenteredShadow(sec, cx, startTextY + lineH, fontSize, Color.WHITE, font)
                    } else {
                        Render.textCenteredShadow(name, cx, cy - lineH / 2f, fontSize, Color.WHITE, font)
                    }
                }
            }

        }
    }
}
