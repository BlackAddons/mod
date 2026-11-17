package org.blackaddons.api.skyblock

import org.blackaddons.api.skyblock.dungeon.F7Phase
import org.blackaddons.api.world.Box3d
import org.blackaddons.api.world.Vec3d

// Corners in any order.
private fun Region(x1: Double, y1: Double, z1: Double, x2: Double, y2: Double, z2: Double) =
    Box3d.spanning(Vec3d(x1, y1, z1), Vec3d(x2, y2, z2))

private fun Spot(x: Int, y: Int, z: Int, radius: Double) = Region(
    x + 0.5 - radius, y.toDouble(), z + 0.5 - radius,
    x + 0.5 + radius, y + 2.0, z + 0.5 + radius,
)

/** Catacombs geometry (boss rooms, Necron phase heights, P3 quadrants). Compiled in: new floors need new code. */
object DungeonGeometry {

    /** False for a floor with no entry. */
    fun inBossRoom(floor: Int, x: Double, y: Double, z: Double): Boolean =
        BOSS_ROOMS.getOrNull(floor - 1)?.contains(x, y, z) == true

    /** Below every threshold is the last phase. */
    fun f7Phase(y: Double): F7Phase {
        val above = PHASE_FLOORS.indexOfFirst { y > it }
        return F7Phase.entries[if (above < 0) PHASE_FLOORS.size else above]
    }

    /** 1-based, or null. */
    fun p3Section(x: Double, y: Double, z: Double): Int? {
        val index = P3_SECTIONS.indexOfFirst { it.contains(x, y, z) }
        return if (index < 0) null else index + 1
    }

    /** Boss room of [floor] 1..7, or null. */
    fun bossRoom(floor: Int): Box3d? = BOSS_ROOMS.getOrNull(floor - 1)

    /** P3 quadrants, section 1 first. */
    val p3Sections: List<Box3d> get() = P3_SECTIONS

    /** Necron phase floor heights, descending. */
    val phaseFloors: List<Double> get() = PHASE_FLOORS

    fun f7SubLocation(x: Double, y: Double, z: Double): F7SubLocation? =
        F7SubLocation.entries.firstOrNull { it.region.contains(x, y, z) }

    // Index = floor - 1.
    private val BOSS_ROOMS = listOf(
        Region(-14.0, 55.0, 49.0, -72.0, 146.0, -40.0),
        Region(-40.0, 99.0, -40.0, 24.0, 54.0, 59.0),
        Region(-40.0, 118.0, -40.0, 42.0, 64.0, 37.0),
        Region(-40.0, 112.0, -40.0, 50.0, 53.0, 47.0),
        Region(-40.0, 112.0, -8.0, 50.0, 53.0, 118.0),
        Region(-40.0, 51.0, -8.0, 22.0, 110.0, 134.0),
        Region(-8.0, 0.0, -8.0, 134.0, 254.0, 147.0),
    )

    // Descending; above entry i is phase i + 1.
    private val PHASE_FLOORS = listOf(210.0, 155.0, 100.0, 45.0)

    private val P3_SECTIONS = listOf(
        Region(90.0, 158.0, 123.0, 111.0, 105.0, 32.0),
        Region(16.0, 158.0, 122.0, 111.0, 105.0, 143.0),
        Region(19.0, 158.0, 48.0, -3.0, 106.0, 142.0),
        Region(91.0, 158.0, 50.0, -3.0, 106.0, 30.0),
    )
}

/**
 * F7 boss spots; [label] is the visible and Pos Msg name. The first five grow from the default Pos Msg waypoints in
 * `ActionStorage` (block center, radius).
 * shortcut: square footprint around the waypoint's cylinder, switch to a cylinder test if the corners misfire.
 */
enum class F7SubLocation(val label: String, val region: Box3d) {
    SS("SS", Spot(108, 120, 94, 2.0)),
    EE2("EE2", Spot(60, 132, 139, 1.0)),
    EE3("EE3", Region(1.5, 109.0, 100.5, 3.5, 111.0, 106.5)),
    CORE("Core", Spot(54, 115, 52, 2.0)),
    MID("Mid", Region(46.5, 64.0, 68.5, 62.5, 67.0, 84.5)),
    I4("I4", Region(64.0, 127.0, 34.0, 62.0, 129.0, 36.0)),
    RED("Red", Region(40.0, 5.0, 67.0, 15.0, 45.0, 49.0)),
    GREEN("Green", Region(40.0, 5.0, 103.0, 15.0, 45.0, 85.0)),
    PURPLE("Purple", Region(69.0, 5.0, 111.0, 44.0, 45.0, 146.0)),
    BLUE("Blue", Region(71.0, 5.0, 104.0, 114.0, 45.0, 84.0)),
    ORANGE("Orange", Region(71.0, 5.0, 65.0, 114.0, 45.0, 47.0)),
    CAULDRONS("Cauldrons", Region(67.0, 5.0, 46.0, 41.0, 15.0, 39.0)),
}
