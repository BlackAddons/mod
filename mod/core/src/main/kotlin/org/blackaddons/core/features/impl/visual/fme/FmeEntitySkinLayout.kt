package org.blackaddons.core.features.impl.visual.fme

object FmeEntitySkinLayout {
    data class Blit(
        val sourceX: Int,
        val sourceY: Int,
        val targetX: Int,
        val targetY: Int,
        val width: Int,
        val height: Int,
        val flipY: Boolean = false,
    )

    @JvmStatic
    fun blits(kind: String, face: String): List<Blit> {
        require(kind == "shulker" || kind == "chest")
        val side = if (kind == "shulker") 16 else 14
        if (face == "UP") return listOf(
            if (kind == "shulker") Blit(2 * side, 28, 0, 0, side, side)
            else Blit(2 * side, 0, 0, 0, side, side),
        )
        if (face == "DOWN") return listOf(
            if (kind == "shulker") Blit(side, 0, 0, 0, side, side)
            else Blit(side, 19, 0, 0, side, side),
        )

        val u = when (face) {
            "WEST" -> 0
            "NORTH" -> side
            "EAST" -> 2 * side
            "SOUTH" -> 3 * side
            else -> error("Unexpected face: $face")
        }
        val layers = if (kind == "shulker") mutableListOf(
            Blit(u, 44, 0, 8, side, 8),
            Blit(u, 16, 0, 0, side, 12),
        ) else mutableListOf(
            Blit(u, 15, 0, 0, side, 4, flipY = true),
            Blit(u, 33, 0, 4, side, 10, flipY = true),
        )
        if (kind == "chest" && face == "SOUTH") layers += Blit(4, 1, 6, 3, 2, 4, flipY = true)
        return layers
    }
}
