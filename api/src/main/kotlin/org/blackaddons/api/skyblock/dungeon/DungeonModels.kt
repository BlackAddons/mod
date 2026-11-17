package org.blackaddons.api.skyblock.dungeon

import com.google.gson.annotations.SerializedName
import org.blackaddons.api.util.equalsIgnoreCase
import java.awt.Color

// Stolen from noamm... I mean borrowed ;)

enum class DungeonClass(val displayName: String, val color: Color) {
    ARCHER("Archer", Color(230, 40, 40)),
    BERSERK("Berserk", Color(255, 170, 20)),
    HEALER("Healer", Color(240, 70, 240)),
    MAGE("Mage", Color(30, 200, 210)),
    TANK("Tank", Color(40, 220, 60)),
    EMPTY("Empty", Color.WHITE);

    companion object {
        fun fromName(name: String): DungeonClass =
            entries.firstOrNull { it.name.equalsIgnoreCase(name) || it.displayName.equalsIgnoreCase(name) } ?: EMPTY
    }
}

data class DungeonPlayer(
    val name: String,
    val dungeonClass: DungeonClass = DungeonClass.EMPTY,
    val classLevel: Int = 0,
    val isDead: Boolean = false,
)

/** Ticks plus epoch ms. */
data class DualTime(val ticks: Long, val real: Long = System.currentTimeMillis()) {
    operator fun minus(other: DualTime) = DualTime(ticks - other.ticks, real - other.real)
}

/** Ordered most advanced first: the scanner only moves a room to a lower ordinal, so GREEN must precede CLEARED. */
enum class RoomState {
    GREEN,
    CLEARED,
    FAILED,
    DISCOVERED,
    UNOPENED,
    UNDISCOVERED,
}

/** Only [WITHER] and [BLOOD] take keys, which is why key pickups use this type. */
enum class DoorType {
    WITHER,
    BLOOD,
    NORMAL,
    ENTRANCE;

    val needsKey: Boolean get() = this == WITHER || this == BLOOD
}

enum class RoomType {
    BLOOD, FAIRY, RARE, CHAMPION, PUZZLE, TRAP, NORMAL, ENTRANCE,
}

enum class RoomShape(val tileCount: Int) {
    @SerializedName("Unknown") UNKNOWN(0),
    @SerializedName("L") SL(3),
    @SerializedName("1x1") S1x1(1),
    @SerializedName("1x2") S2x1(2),
    @SerializedName("1x3") S3x1(3),
    @SerializedName("1x4") S4x1(4),
    @SerializedName("2x2") S2x2(4),
}

enum class Puzzle(val displayName: String, val tabName: String) {
    HIGHER_OR_LOWER("Higher Or Lower", "Higher Or Lower"),
    WATER_BOARD("Water Board", "Water Board"),
    TIC_TAC_TOE("Tic Tac Toe", "Tic Tac Toe"),
    BOULDER("Boulder", "Boulder"),
    ICE_PATH("Ice Path", "Ice Path"),
    TELEPORT_MAZE("Teleport Maze", "Teleport Maze"),
    ICE_FILL("Ice Fill", "Ice Fill"),
    QUIZ("Quiz", "Quiz"),
    THREE_WEIRDOS("Three Weirdos", "Three Weirdos"),
    CREEPER_BEAMS("Creeper Beams", "Creeper Beams"),
    UNKNOWN("Unknown", "Unknown");

    companion object {
        fun fromName(name: String): Puzzle? =
            entries.firstOrNull { it.tabName.equalsIgnoreCase(name) || it.displayName.equalsIgnoreCase(name) }
    }
}

/** [Puzzle.UNKNOWN] until the tab list names it. */
data class PuzzleStatus(val puzzle: Puzzle, val state: RoomState = RoomState.UNOPENED) {
    val isResolved: Boolean get() = state == RoomState.GREEN || state == RoomState.CLEARED
}
