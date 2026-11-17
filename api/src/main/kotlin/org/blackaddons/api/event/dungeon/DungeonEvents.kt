package org.blackaddons.api.event.dungeon

import org.blackaddons.api.event.Event
import org.blackaddons.api.skyblock.dungeon.DoorType
import org.blackaddons.api.skyblock.dungeon.DualTime
import org.blackaddons.api.skyblock.dungeon.DungeonFloor
import org.blackaddons.api.skyblock.dungeon.F7Phase
import org.blackaddons.api.skyblock.dungeon.RoomState

/** The run begins (e.g. Mort's dialogue). */
class DungeonRunStartedEvent(val startTime: DualTime) : Event()

class DungeonRunEndedEvent(val endTime: DualTime, val floor: DungeonFloor?) : Event()

class DungeonBossEnterEvent(val entryTime: DualTime) : Event()

/** F7/M7 moved into [phase], on the boss's opening line (Wither King: on his boss bar). */
class F7PhaseStartEvent(val phase: F7Phase, val time: DualTime) : Event()

class DungeonPlayerDeathEvent(val username: String, val reason: String) : Event()

/** A wither or blood door opened. */
class DungeonDoorOpenedEvent(val player: String?, val type: DoorType) : Event()

/** [type] is the door kind the key opens. */
class DungeonKeyPickedUpEvent(val type: DoorType, val amount: Int) : Event()

class DungeonRoomStateChangeEvent(
    val roomName: String,
    val oldState: RoomState,
    val newState: RoomState,
) : Event()

/** A map tile changed after a world or map scan; DungeonTree invalidates caches on it. */
class DungeonTileScannedEvent(val gridX: Int, val gridZ: Int) : Event()

object DungeonMimicKilledEvent : Event()

/**
 * A bonus-score kill was credited. [bonus] is `mimic`, `prince` or `bat`, spelled as the backend expects
 * since it travels with recorded runs.
 */
class DungeonBonusEarnedEvent(val bonus: String) : Event()

/**
 * The action bar's secret count changed but could not be credited to a room. [reason] is a stable code
 * (`no_room`, `unidentified_room`, `count_mismatch`) recorded with solo clears; [detail] is for a person.
 */
class DungeonSecretUncreditedEvent(
    val reason: String,
    val detail: String,
    val room: String?,
    val found: Int,
    val total: Int,
    val x: Double,
    val y: Double,
    val z: Double,
) : Event()

/** The current room's secret count rose. The position lets a recorded run be checked against where secrets are. */
class DungeonSecretFoundEvent(
    val room: String,
    val found: Int,
    val total: Int,
    val x: Double,
    val y: Double,
    val z: Double,
) : Event()

/** A spirit leap that clicks the head itself, for overlays that would otherwise show the menu. */
sealed class SpiritLeapEvent : Event() {
    class Started(val target: String) : SpiritLeapEvent()

    /** Done, failed or cancelled. */
    object Ended : SpiritLeapEvent()
}
