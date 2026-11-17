package org.blackaddons.api.skyblock

import org.blackaddons.api.skyblock.dungeon.DungeonFloor
import org.blackaddons.api.util.containsIgnoreCase

/** Version-neutral parsing of the sidebar and tab list; the platform passes in raw strings. */
object HypixelSidebar {

    private val AREA_PREFIXES = listOf("Area: ", "Dungeon: ")
    private val PROFILE_REGEX = Regex("""Profile:\s*([A-Za-z]+)""")
    private val FLOOR_REGEX = Regex("""\(([MF][1-7]|F0|E|Entrance)\)""", RegexOption.IGNORE_CASE)

    /** Floor from the first sidebar line with a floor tag (e.g. `"(F7)"`). Queued-run lines are skipped. */
    fun dungeonFloor(sidebarLines: List<String>): DungeonFloor? {
        for (line in sidebarLines) {
            if (line.containsIgnoreCase("Queue")) continue
            val match = FLOOR_REGEX.find(line) ?: continue
            val raw = match.groupValues[1].uppercase()
            return if (raw.startsWith("E") || raw == "F0") DungeonFloor.ENTRANCE else DungeonFloor.valueOf(raw)
        }
        return null
    }

    /** From the `Area: <x>` / `Dungeon: <x>` tab line. */
    fun area(tabLines: List<String>): String? {
        for (text in tabLines) {
            for (prefix in AREA_PREFIXES) {
                if (text.startsWith(prefix)) return text.removePrefix(prefix).trim()
            }
        }
        return null
    }

    /** Hypixel titles it "THE RIFT". */
    fun isRift(sidebarTitle: String?): Boolean = sidebarTitle?.containsIgnoreCase("RIFT") == true

    /** Cute name from the `Profile: <name>` tab line. */
    fun profile(tabLines: List<String>): String? {
        for (text in tabLines) {
            PROFILE_REGEX.find(text)?.let { return it.groupValues[1] }
        }
        return null
    }
}
