package org.blackaddons.core.dungeon

import org.blackaddons.api.Platform
import org.blackaddons.api.event.ClientTickEvent
import org.blackaddons.api.event.EventBus
import org.blackaddons.api.event.WorldChangeEvent
import org.blackaddons.api.event.chat.ChatReceiveEvent
import org.blackaddons.api.event.dungeon.*
import org.blackaddons.api.event.subscribe
import org.blackaddons.api.skyblock.LocationUtils
import org.blackaddons.api.skyblock.dungeon.*
import org.blackaddons.api.util.equalsIgnoreCase
import org.blackaddons.core.dungeon.map.MapState

object DungeonTracker {

    var dungeonStarted: Boolean = false
    var dungeonStartTime: DualTime? = null
    var dungeonEnded: Boolean = false
    var dungeonEndTime: Long? = null

    var bloodOpenTime: DualTime? = null
    var watcherClearTime: DualTime? = null
    var watcherFinishSpawnTime: Long? = null
    var bossEntryTime: DualTime? = null

    var lastDoorOpener: DungeonPlayer? = null
    var currentTime: Long = 0L

    var witherKeys: Int = 0
    var bloodKeys: Int = 0

    val dungeonTeammates: MutableList<DungeonPlayer> = mutableListOf()
    val dungeonTeammatesNoSelf: List<DungeonPlayer>
        get() = dungeonTeammates.filter { !it.name.equalsIgnoreCase(Platform.player.name) }
    val thePlayer: DungeonPlayer?
        get() = Platform.player.name?.let(::teammate)

    var maxPuzzleCount: Int = 0
    val puzzles: MutableList<PuzzleStatus> = mutableListOf()

    private val TABLIST_PLAYER_REGEX = Regex("""^\[\d+] (?:\[[^]]+] )*([A-Za-z0-9_]{1,16}) .*\((\w+)(?: (\w+))?\)$""")
    private val PUZZLE_COUNT_REGEX = Regex("""Puzzles: \((?<count>\d)\)""")
    private val PUZZLE_REGEX = Regex("""\s*(.+):\s*\[([✦✔✖]).*]""")
    private val DEATH_REGEX = Regex("""^ ☠ (?:You were|(?<username>\w+)) (?<reason>.+?)(?: and became a ghost)?\.$""")
    private val KEY_PICKUP_REGEX = Regex("""RIGHT CLICK on .+ to open it\. This key can only be used to open (?<num>\d+) door!""")
    private val WITHER_DOOR_REGEX = Regex("""^(?:\[.+?] )?(?<name>\w+) opened a WITHER door!$""")
    private val RUN_END_REGEX = Regex("""^\s*(Master Mode)? ?(?:The)? Catacombs - (Floor (.{1,3})|Entrance)$""")

    fun start() {
        // Read from the packet: chat cleaners hide lines like Mort's, and then we lose the run start and telemetry.
        EventBus.subscribe<ChatReceiveEvent> { onChatMessage(event) }
        EventBus.subscribe<ClientTickEvent.Pre> { onTick() }
        EventBus.subscribe<WorldChangeEvent> { reset() }
        ScoreCalculation.init()
        F7Boss.start()
    }

    fun teammate(name: String): DungeonPlayer? =
        dungeonTeammates.firstOrNull { it.name.equalsIgnoreCase(name) }

    private fun now(): DualTime = DualTime(currentTime)

    private fun onTick() {
        if (!LocationUtils.inDungeon) {
            if (dungeonStarted || currentTime > 0L || dungeonTeammates.isNotEmpty()) reset()
            return
        }
        currentTime++

        // Backup start from the sidebar clock. A missed Mort line once cost three F7 runs.
        if (!dungeonStarted && ScoreCalculation.secondsElapsed > 0) handleRunStart()

        if (LocationUtils.inBoss && bossEntryTime == null) {
            val entry = now()
            bossEntryTime = entry
            EventBus.post(DungeonBossEnterEvent(entry))
        }

        updateTabListState()
    }

    private fun onChatMessage(event: ChatReceiveEvent) {
        if (!LocationUtils.inDungeon) return
        val text = event.unformatted.trimEnd()

        when {
            text.startsWith("[NPC] Mort:") && "found this map" in text -> handleRunStart()
            text.matches(RUN_END_REGEX) -> handleRunEnd()
            "BLOOD DOOR" in text && "opened" in text -> handleBloodDoor()
            text.startsWith("[BOSS] The Watcher:") -> handleWatcherMessage(text)
            text.startsWith(" ☠") && "reconnected" !in text -> handlePlayerDeath(text)
            "opened a WITHER door!" in text -> handleWitherDoor(text)
            "RIGHT CLICK on" in text && "door!" in text -> handleKeyPickup(text)
        }
    }

    private fun handleRunStart() {
        if (dungeonStarted) return

        val start = now()
        dungeonStartTime = start
        dungeonStarted = true
        EventBus.post(DungeonRunStartedEvent(start))
    }

    private fun handleRunEnd() {
        dungeonEnded = true
        dungeonEndTime = currentTime
        EventBus.post(DungeonRunEndedEvent(now(), LocationUtils.dungeonFloor))
    }

    private fun handleBloodDoor() {
        bloodKeys = (bloodKeys - 1).coerceAtLeast(0)
        EventBus.post(DungeonDoorOpenedEvent(null, DoorType.BLOOD))
    }

    private fun handleWatcherMessage(text: String) {
        when {
            "You have proven yourself" in text -> {
                watcherClearTime = now()
                MapState.setBloodRoomState(RoomState.GREEN)
            }
            "That will be enough for now" in text -> {
                watcherFinishSpawnTime = currentTime
                MapState.setBloodRoomState(RoomState.CLEARED)
            }
            bloodOpenTime == null -> bloodOpenTime = now()
        }
    }

    private fun handlePlayerDeath(text: String) {
        DEATH_REGEX.find(text)?.let { match ->
            val name = match.groups["username"]?.value ?: Platform.player.name.orEmpty()
            val reason = match.groups["reason"]?.value.orEmpty()
            updatePlayer(name) { it.copy(isDead = true) }
            EventBus.post(DungeonPlayerDeathEvent(name, reason))
        }
    }

    private fun handleWitherDoor(text: String) {
        WITHER_DOOR_REGEX.find(text)?.let { match ->
            val name = match.groups["name"]?.value
            lastDoorOpener = name?.let(::teammate)
            witherKeys = (witherKeys - 1).coerceAtLeast(0)
            EventBus.post(DungeonDoorOpenedEvent(name, DoorType.WITHER))
        }
    }

    private fun handleKeyPickup(text: String) {
        KEY_PICKUP_REGEX.find(text)?.let { match ->
            val num = match.groups["num"]?.value?.toIntOrNull() ?: 1
            val type = DoorType.entries.firstOrNull { it.needsKey && it.name in text } ?: return
            if (type == DoorType.WITHER) witherKeys += num else bloodKeys += num
            EventBus.post(DungeonKeyPickedUpEvent(type, num))
        }
    }

    private fun updateTabListState() {
        for (line in Platform.skyblock.tabLines) {
            val text = line.trim()
            when {
                text.startsWith("[") -> updateTeammate(text)
                "Puzzles:" in text -> updatePuzzleCount(text)
                "[" in text -> updatePuzzleStatus(text)
            }
        }
    }

    internal fun updateTeammate(text: String) {
        val match = TABLIST_PLAYER_REGEX.matchEntire(text) ?: return
        val name = match.groupValues[1]
        val classStr = match.groupValues[2]
        val lvl = match.groupValues.getOrNull(3)?.toIntOrNull() ?: 0
        val dClass = DungeonClass.fromName(classStr)
        val isDead = classStr == "DEAD"

        val updated = updatePlayer(name) {
            it.copy(
                dungeonClass = if (dClass != DungeonClass.EMPTY) dClass else it.dungeonClass,
                classLevel = if (lvl > 0) lvl else it.classLevel,
                isDead = isDead,
            )
        }
        if (!updated) dungeonTeammates.add(DungeonPlayer(name, dClass, lvl, isDead))
    }

    private fun updatePlayer(name: String, change: (DungeonPlayer) -> DungeonPlayer): Boolean {
        val index = dungeonTeammates.indexOfFirst { it.name.equalsIgnoreCase(name) }
        if (index < 0) return false
        dungeonTeammates[index] = change(dungeonTeammates[index])
        return true
    }

    private fun updatePuzzleCount(text: String) {
        if (maxPuzzleCount != 0) return
        PUZZLE_COUNT_REGEX.find(text)?.let { match ->
            val count = match.groups["count"]?.value?.toIntOrNull() ?: 0
            if (count > 0) {
                maxPuzzleCount = count
                repeat(count) { puzzles.add(PuzzleStatus(Puzzle.UNKNOWN)) }
            }
        }
    }

    private fun updatePuzzleStatus(text: String) {
        val match = PUZZLE_REGEX.find(text) ?: return
        val name = match.groupValues[1].trim()
        val icon = match.groupValues[2]
        val newState = when (icon) {
            "✔" -> RoomState.GREEN
            "✖" -> RoomState.FAILED
            "✦" -> RoomState.DISCOVERED
            else -> RoomState.UNOPENED
        }

        val detected = Puzzle.fromName(name) ?: return
        if (detected == Puzzle.UNKNOWN) {
            if (puzzles.none { it.puzzle == Puzzle.UNKNOWN }) puzzles.add(PuzzleStatus(Puzzle.UNKNOWN))
            return
        }

        val slot = puzzles.indexOfFirst { it.puzzle == detected }.takeIf { it >= 0 }
            ?: puzzles.indexOfFirst { it.puzzle == Puzzle.UNKNOWN }.takeIf { it >= 0 }
        val status = PuzzleStatus(detected, newState)
        if (slot == null) puzzles.add(status) else puzzles[slot] = status
    }

    fun reset() {
        dungeonStarted = false
        dungeonStartTime = null
        dungeonEnded = false
        dungeonEndTime = null
        bloodOpenTime = null
        watcherClearTime = null
        watcherFinishSpawnTime = null
        bossEntryTime = null
        lastDoorOpener = null
        currentTime = 0L
        witherKeys = 0
        bloodKeys = 0
        dungeonTeammates.clear()
        maxPuzzleCount = 0
        puzzles.clear()
        ScoreCalculation.reset()
        F7Boss.reset()
    }
}
