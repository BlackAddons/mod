package org.blackaddons.core.features.impl.dungeons.map

import org.blackaddons.api.ui.render.Image
import org.blackaddons.api.ui.render.Render
import java.awt.Color

internal object MapIcons {
    val checkmarkGreen = Image("assets/blackaddons/textures/dungeonmap/checkmarks/green_check.png")
    val checkmarkWhite = Image("assets/blackaddons/textures/dungeonmap/checkmarks/white_check.png")
    val checkmarkFail = Image("assets/blackaddons/textures/dungeonmap/checkmarks/cross.png")
    val checkmarkUnknown = Image("assets/blackaddons/textures/dungeonmap/checkmarks/question.png")
    val ownPlayerMarker = Image("assets/blackaddons/textures/dungeonmap/marker.png")
    val mimicGreen = Image("assets/blackaddons/textures/dungeonmap/bonus/mimic_green.png")
    val mimicRed = Image("assets/blackaddons/textures/dungeonmap/bonus/mimic_red.png")
    val princeGreen = Image("assets/blackaddons/textures/dungeonmap/bonus/prince_green.png")
    val princeRed = Image("assets/blackaddons/textures/dungeonmap/bonus/prince_red.png")
    val batGreen = Image("assets/blackaddons/textures/dungeonmap/bonus/bat_green.png")
    val batRed = Image("assets/blackaddons/textures/dungeonmap/bonus/bat_red.png")

    fun drawPrinceRoomIcon(x: Float, y: Float, size: Float) {
        Render.icon(PRINCE_CROWN, x, y, size, Color(83, 49, 0), thickness = 3)
        Render.icon(PRINCE_CROWN, x, y, size, Color(255, 207, 64), thickness = 0)
    }

    fun roomIconSize(roomSize: Float) = minOf(7f, roomSize * 0.44f)

    private const val PRINCE_CROWN = "M3 6L7.5 10L12 4L16.5 10L21 6L19 19H5L3 6Z" // yeah fuck that I'm not gonna make a png for ts and I didn't like how the existing prince icon looks
}
