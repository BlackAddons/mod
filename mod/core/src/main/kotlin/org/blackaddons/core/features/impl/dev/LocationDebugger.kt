package org.blackaddons.core.features.impl.dev

import org.blackaddons.api.Platform
import org.blackaddons.api.event.render.RenderWorldEvent
import org.blackaddons.api.feature.Feature
import org.blackaddons.api.hud.hudPanel
import org.blackaddons.api.hud.hudText
import org.blackaddons.api.skyblock.DungeonGeometry
import org.blackaddons.api.skyblock.F7SubLocation
import org.blackaddons.api.skyblock.LocationUtils
import org.blackaddons.api.world.Box3d
import java.awt.Color
import java.util.Locale

/**
 * Dev tool: draws the compiled [DungeonGeometry] regions and lists the live [LocationUtils] state. Off P3Sim with an
 * unknown floor (test servers, singleplayer), it shows every floor's boss room.
 */
internal object LocationDebugger : Feature(
    description = "Draw dungeon location regions in the world and show the current location state.",
    name = "Location Debugger",
) {
    private val INSIDE = Color(80, 220, 120)
    private val OUTSIDE = Color(255, 255, 255, 140)
    private val PHASE = Color(240, 190, 60)
    private const val WALL_ALPHA = 60

    private val walls by boolean("Render Location Walls", default = true)
        .withDescription("Fill location regions with half-transparent walls.")

    override fun init() {
        register<RenderWorldEvent> {
            if (Platform.player.isInGame) renderRegions()
        }
    }

    @Suppress("unused")
    private val hud = hudElement("Location Debugger", defaultOffsetX = 5f, defaultOffsetY = 60f) { example ->
        hudPanel(background = Color(0, 0, 0, 110)) {
            for (line in lines(example)) hudText(line, Color.WHITE)
        }
    }

    private fun floors(): List<Int> =
        LocationUtils.dungeonFloorNumber?.let(::listOf) ?: if (LocationUtils.isP3Sim) listOf(7) else (1..7).toList()

    private fun renderRegions() {
        val floors = floors()
        for (floor in floors) {
            val room = DungeonGeometry.bossRoom(floor) ?: continue
            region(room, "F$floor Boss")
        }
        if (7 !in floors) return
        DungeonGeometry.p3Sections.forEachIndexed { i, box -> region(box, "P3 S${i + 1}") }
        for (spot in F7SubLocation.entries) region(spot.region, spot.label, labelY = spot.region.max.y + 0.5)
        val room = DungeonGeometry.bossRoom(7) ?: return
        DungeonGeometry.phaseFloors.forEachIndexed { i, y ->
            Platform.worldRenderer.boxEdges(room.min.x, y, room.min.z, room.max.x, y, room.max.z, PHASE)
            Platform.worldRenderer.text("P${i + 1} / P${i + 2}  y=${y.toInt()}", room.center().x, y + 0.5, room.center().z, PHASE, 1.5f)
        }
    }

    private fun region(box: Box3d, label: String, labelY: Double = box.max.y - 1.0) {
        val inside = box.contains(Platform.player.x, Platform.player.y, Platform.player.z)
        val color = if (inside) INSIDE else OUTSIDE
        Platform.worldRenderer.boxEdges(box.min.x, box.min.y, box.min.z, box.max.x, box.max.y, box.max.z, color)
        if (walls.value) {
            val wall = Color(color.red, color.green, color.blue, WALL_ALPHA)
            Platform.worldRenderer.filledBox(box.min.x, box.min.y, box.min.z, box.max.x, box.max.y, box.max.z, wall)
        }
        Platform.worldRenderer.text(label, box.center().x, labelY, box.center().z, color, 1.5f)
    }

    private fun lines(example: Boolean): List<String> {
        if (example || !Platform.player.isInGame) return listOf(
            "Pos: 73.5 161.0 51.2",
            "Area: The Catacombs | Floor: F7",
            "Hypixel: true | P3Sim: false",
            "Dungeon: true | Boss: true",
            "Phase: 3 | P3 Section: 1",
            "Sub: core",
        )
        val pos = "%.1f %.1f %.1f".format(Locale.ROOT, Platform.player.x, Platform.player.y, Platform.player.z)
        return listOf(
            "Pos: $pos",
            "Area: ${LocationUtils.area} | Floor: ${LocationUtils.dungeonFloor}",
            "Hypixel: ${LocationUtils.onHypixel} | P3Sim: ${LocationUtils.isP3Sim}",
            "Dungeon: ${LocationUtils.inDungeon} | Boss: ${LocationUtils.inBoss}",
            "Phase: ${LocationUtils.f7Phase} | P3 Section: ${LocationUtils.p3Section}",
            "Sub: ${LocationUtils.f7SubLocation}",
        )
    }
}
