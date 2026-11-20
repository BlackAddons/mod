package org.blackaddons.core.dungeon

import org.blackaddons.api.Platform
import org.blackaddons.api.event.ClientTickEvent
import org.blackaddons.api.event.EventBus
import org.blackaddons.api.event.WorldChangeEvent
import org.blackaddons.api.event.chat.ActionBarReceiveEvent
import org.blackaddons.api.event.chat.ChatReceiveEvent
import org.blackaddons.api.event.dungeon.DungeonBonusEarnedEvent
import org.blackaddons.api.event.dungeon.DungeonMimicKilledEvent
import org.blackaddons.api.event.dungeon.DungeonPlayerDeathEvent
import org.blackaddons.api.event.dungeon.DungeonSecretFoundEvent
import org.blackaddons.api.event.dungeon.DungeonSecretUncreditedEvent
import org.blackaddons.api.event.subscribe
import org.blackaddons.api.skyblock.LocationUtils
import org.blackaddons.api.skyblock.dungeon.MapOverrides
import org.blackaddons.api.skyblock.dungeon.Puzzle
import org.blackaddons.api.skyblock.dungeon.PuzzleStatus
import org.blackaddons.api.skyblock.dungeon.RoomState
import org.blackaddons.api.skyblock.dungeon.RoomType
import org.blackaddons.api.util.stripFormatting
import org.blackaddons.core.dungeon.ScoreCalculation.board
import org.blackaddons.core.dungeon.ScoreCalculation.target
import org.blackaddons.core.dungeon.map.MapState
import org.blackaddons.core.features.impl.dungeons.map.DungeonMap
import org.blackaddons.core.features.impl.dungeons.soloclears.SoloClears
import org.blackaddons.core.gamedata.DungeonScoring
import kotlin.math.floor

object ScoreCalculation {

    private val cryptsPattern = Regex("""Crypts:\s*\(?(\d+)\)?""", RegexOption.IGNORE_CASE)
    private val completedRoomsRegex = Regex("""Completed Rooms:\s*(\d+)""", RegexOption.IGNORE_CASE)
    private val secretsFoundPattern = Regex("""Secrets Found:\s*(\d+)""", RegexOption.IGNORE_CASE)
    private val secretsFoundPercentagePattern = Regex("""Secrets Found:\s*([\d.]+)%""", RegexOption.IGNORE_CASE)
    private val dungeonClearedPattern = Regex("""Cleared:\s*(\d+)%""")
    private val timeElapsedPattern = Regex("""(?:Time )?Elapsed:\s*(?:(\d+)h\s*)?(?:(\d+)m\s*)?(?:(\d+)s)?""", RegexOption.IGNORE_CASE)
    private val mimicCharmRegex = Regex("""charm you charmed a mimic and captured \d+ shards from it\.""", RegexOption.IGNORE_CASE)
    private val partyChatRegex = Regex("""^party > (?:\[[^]]+] )?(?<name>\w+): (?<message>.+)$""", RegexOption.IGNORE_CASE)
    private val actionBarSecretsRegex = Regex("""(\d+)/(\d+) Secrets""")

    /** Puzzles the solo clears board forgives. They cost time, not skill. */
    private val SKIPPABLE = listOf(Puzzle.QUIZ, Puzzle.ICE_FILL)

    private val EMPTY_PARTS = ScoreParts(secrets = 0, rooms = 0, skill = 20, speed = 100, bonus = 0)

    /** Backend spelling. They travel with recorded runs. */
    const val BONUS_MIMIC = "mimic"
    const val BONUS_PRINCE = "prince"
    const val BONUS_BAT = "bat"

    private val mimicMessages = setOf(
        "mimic dead!", "mimic dead", "mimic killed!", "mimic killed", "mimic slain",
        "\$skytils-dungeon-score-mimic$", "child destroyed!", "mimic obliterated!",
        "mimic exorcised!", "mimic destroyed!", "mimic annhilated!",
        "breefing killed", "breefing dead"
    )

    private val princeMessages = setOf(
        "prince dead", "prince dead!", "\$skytils-dungeon-score-prince$",
        "prince killed", "prince slain", "prince killed!",
        "a prince falls. +1 bonus score"
    )

    private val batMessages = setOf(
        "bat dead", "bat dead!", "\$skytils-dungeon-score-bat$",
        "bat killed", "bat slain", "bat killed!",
        "a bat has been slain. +1 bonus score"
    )

    var bloodDone: Boolean = false
    var mimicKilled: Boolean = false
    var princeKilled: Boolean = false
    var batKilled: Boolean = false
    val batKillers: MutableSet<String> = mutableSetOf()

    var deathCount: Int = 0
    var foundSecrets: Int = 0
    var cryptsCount: Int = 0
    var secretPercentage: Double = 0.0
    var clearedPercentage: Int = 0
    var completedRooms: Int = 0
    var secondsElapsed: Int = 0

    var score: Int = 120
        private set

    data class ScoreParts(
        val secrets: Int,
        val rooms: Int,
        val skill: Int,
        val speed: Int,
        val bonus: Int,
        val cap: Int = Int.MAX_VALUE,
    ) {
        val total: Int get() = (secrets + rooms + skill + speed + bonus).coerceIn(0, cap)
    }

    /**
     * Solo clears board score: no mayor perk, quiz and ice fill always forgiven, like `SoloClearScoring.Evaluate`. A
     * recording claims this score, and it has to match the backend within one point (`score_claim`). So it ignores the
     * board the player picked ([target]).
     */
    var board: ScoreParts = EMPTY_PARTS
        private set

    /** Score against the player's target board, shown on the map. Same as [board] when skips are allowed (default). */
    var target: ScoreParts = EMPTY_PARTS
        private set

    val boardScore: Int get() = board.total

    val targetScore: Int get() = target.total

    internal val secretFeed = SecretFeed()

    private var initialized = false

    fun init() {
        if (initialized) return
        initialized = true
        EventBus.subscribe<WorldChangeEvent> { reset() }
        EventBus.subscribe<DungeonPlayerDeathEvent> {
            deathCount++
            recalculate()
        }
        EventBus.subscribe<DungeonMimicKilledEvent> {
            if (!mimicKilled) {
                mimicKilled = true
                earned(BONUS_MIMIC)
                recalculate()
            }
        }
        // From the packet, same reason as in DungeonTracker.
        EventBus.subscribe<ChatReceiveEvent> { onChatMessage(event.unformatted.trim()) }
        EventBus.subscribe<ActionBarReceiveEvent> { onActionBar(event.unformatted) }
        EventBus.subscribe<ClientTickEvent.Pre> { onTick() }
    }

    private fun onActionBar(text: String) {
        secretFeed.sawActionBar()
        val match = actionBarSecretsRegex.find(text) ?: return
        secretFeed.sawSecrets()
        if (!LocationUtils.inDungeon) {
            secretFeed.skip("not_in_dungeon")
            return secretsSkipped("not in a dungeon")
        }
        val secrets = match.groupValues[1].toIntOrNull() ?: return secretFeed.skip("unreadable")
        val maxSecrets = match.groupValues[2].toIntOrNull() ?: return secretFeed.skip("unreadable")
        val px = Platform.player.x
        val pz = Platform.player.z
        // The action bar repeats every tick. Only a new count goes to a recording.
        val counted = secrets to maxSecrets
        val changed = counted != lastActionBarSecrets
        lastActionBarSecrets = counted

        val room = MapState.getRoomAt(px, pz)
            ?: return uncredited(changed, "no_room", "no room at grid ${MapState.worldToGrid(px, pz)}",
                null, secrets, maxSecrets)
        if (room.data.name == "Unknown") {
            return uncredited(changed, "unidentified_room", "the room here is not identified", null, secrets, maxSecrets)
        }
        if (room.data.secrets != maxSecrets) {
            return uncredited(changed, "count_mismatch",
                "'${room.name}' holds ${room.data.secrets}, the action bar says $maxSecrets",
                room.name, secrets, maxSecrets)
        }
        secretsSkipped(null)
        secretFeed.credit()
        if (room.foundSecrets != secrets) {
            val gained = secrets > room.foundSecrets
            DungeonMap.debug { "[ScoreCalculation] '${room.name}' secrets ${room.foundSecrets} -> $secrets/$maxSecrets" }
            room.foundSecrets = secrets
            if (gained) {
                EventBus.post(
                    DungeonSecretFoundEvent(
                        room.name, secrets, maxSecrets, px, Platform.player.y, pz,
                    ),
                )
            }
        }
    }

    private var lastActionBarSecrets: Pair<Int, Int>? = null

    // A recording that claims secrets with none credited goes to review. This puts the reason in the recording.
    private fun uncredited(changed: Boolean, reason: String, detail: String, room: String?, found: Int, total: Int) {
        secretFeed.skip(reason)
        secretsSkipped(detail)
        if (!changed || found == 0) return
        EventBus.post(
            DungeonSecretUncreditedEvent(
                reason, detail, room, found, total, Platform.player.x, Platform.player.y, Platform.player.z,
            ),
        )
    }

    private var lastSecretsSkip: String? = null

    private fun secretsSkipped(reason: String?) {
        if (reason == lastSecretsSkip) return
        lastSecretsSkip = reason
        if (reason != null) DungeonMap.debug { "[ScoreCalculation] Action-bar secrets ignored: $reason" }
    }

    fun onTick() {
        if (!LocationUtils.inDungeon) return
        updateTabListState()
        updateSidebarState()
        recalculate()
    }

    fun onChatMessage(text: String) {
        if (!LocationUtils.inDungeon) return
        val lower = text.lowercase()

        if (!princeKilled) {
            if (lower == "a prince falls. +1 bonus score" || princeMessages.any { lower.contains(it) }) {
                princeKilled = true
                earned(BONUS_PRINCE)
                recalculate()
            }
        }

        val floorNum = LocationUtils.dungeonFloorNumber ?: 0
        if (!mimicKilled && floorNum > 5 && !LocationUtils.inBoss) {
            val isMimicChat = mimicCharmRegex.matches(lower) ||
                lower.contains("charm you charmed a mimic") ||
                mimicMessages.any { lower.contains(it) } ||
                partyChatRegex.matchEntire(text)?.let { match ->
                    val message = match.groups["message"]?.value?.lowercase() ?: return@let false
                    mimicMessages.any { message.contains(it) } || message.contains("mimic")
                } ?: false

            if (isMimicChat) {
                mimicKilled = true
                earned(BONUS_MIMIC)
                recalculate()
            }
        }

        if (lower == "a bat has been slain. +1 bonus score") {
            val first = !batKilled
            batKilled = true
            val selfName = Platform.player.name?.lowercase()
            if (selfName != null) batKillers.add(selfName)
            if (first) earned(BONUS_BAT)
            recalculate()
        } else {
            partyChatRegex.matchEntire(text)?.let { match ->
                val sender = match.groups["name"]?.value ?: return@let
                val message = match.groups["message"]?.value?.lowercase() ?: return@let
                if (batMessages.any { message.contains(it) }) {
                    val first = !batKilled
                    batKilled = true
                    batKillers.add(sender.lowercase())
                    if (first) earned(BONUS_BAT)
                    recalculate()
                }
            }
        }
    }

    private fun earned(bonus: String) = EventBus.post(DungeonBonusEarnedEvent(bonus))

    private fun updateTabListState() {
        for (line in Platform.skyblock.tabLines) {
            val text = line.stripFormatting().trim()
            when {
                text.contains("Crypts:", ignoreCase = true) -> cryptsPattern.find(text)?.let {
                    cryptsCount = it.groupValues[1].toIntOrNull() ?: cryptsCount
                }
                text.contains("Completed Rooms:", ignoreCase = true) -> completedRoomsRegex.find(text)?.let {
                    completedRooms = it.groupValues[1].toIntOrNull() ?: completedRooms
                }
                text.contains("Secrets Found:", ignoreCase = true) -> {
                    if (text.contains('%')) {
                        secretsFoundPercentagePattern.find(text)?.let {
                            secretPercentage = it.groupValues[1].toDoubleOrNull() ?: secretPercentage
                        }
                    } else {
                        secretsFoundPattern.find(text)?.let {
                            foundSecrets = it.groupValues[1].toIntOrNull() ?: foundSecrets
                        }
                    }
                }
            }
        }
    }

    private fun updateSidebarState() {
        for (line in Platform.skyblock.sidebarLines) {
            val text = line.stripFormatting().trim()
            when {
                text.contains("Cleared:", ignoreCase = true) -> dungeonClearedPattern.find(text)?.let {
                    val newCompleted = it.groupValues[1].toIntOrNull() ?: clearedPercentage
                    if (newCompleted != clearedPercentage && DungeonTracker.watcherClearTime != null) {
                        bloodDone = true
                    }
                    clearedPercentage = newCompleted
                }
                text.contains("Elapsed:", ignoreCase = true) -> timeElapsedPattern.find(text)?.let { matcher ->
                    val hours = matcher.groupValues.getOrNull(1)?.toIntOrNull() ?: 0
                    val minutes = matcher.groupValues.getOrNull(2)?.toIntOrNull() ?: 0
                    val seconds = matcher.groupValues.getOrNull(3)?.toIntOrNull() ?: 0
                    val totalSec = hours * 3600 + minutes * 60 + seconds
                    if (totalSec > 0) secondsElapsed = totalSec
                }
            }
        }
    }

    fun recalculate() {
        if (!LocationUtils.inDungeon) return

        val floor = LocationUtils.dungeonFloor?.label ?: "F7"
        val floorNum = LocationUtils.dungeonFloorNumber ?: 7
        val rules = DungeonScoring.current.rulesFor(floor)

        score = when (DungeonMap.calculationMode.value) {
            DungeonMap.CALCULATION_OPTIMISTIC -> calculateOptimisticScore(floorNum, rules.requiredSecretPercentage)
            else -> conservative(rules, floorNum, unresolvedPuzzles(), includePaul = true).total
        }
        // Skipped puzzles credit both the penalty and the room, like the backend.
        val skipped = skippedPuzzles().size.coerceAtMost(unresolvedPuzzles())
        board = conservative(rules, floorNum, unresolvedPuzzles() - skipped, includePaul = false, creditedRooms = skipped)
        target = if (SoloClears.forgivesSkips) board
        else conservative(rules, floorNum, unresolvedPuzzles(), includePaul = false)
    }

    /** Each one still costs 10 points. */
    internal fun unresolvedPuzzles(): Int = DungeonTracker.puzzles.count { !it.isResolved }

    /** Backend names. They decide which board a run goes to. */
    internal fun skippedPuzzles(): List<String> =
        SKIPPABLE.filter { puzzle -> DungeonTracker.puzzles.any { it.puzzle == puzzle && !it.isResolved } }
            .map { it.displayName }

    internal fun puzzleNames(): List<String> = named().map { it.puzzle.displayName }

    /** The backend picks the forgivable ones itself. */
    internal fun unresolvedPuzzleNames(): List<String> = named().filter { !it.isResolved }.map { it.puzzle.displayName }

    private fun named(): List<PuzzleStatus> = DungeonTracker.puzzles.filter { it.puzzle != Puzzle.UNKNOWN }

    fun getSpeedDeduction(percentage: Float): Float {
        var percentageOver = percentage
        var deduction = 0f

        fun dedu(cap: Float, div: Float) {
            if (percentageOver <= 0f) return
            deduction += percentageOver.coerceAtMost(cap) / div
            percentageOver -= cap
        }

        dedu(20f, 2f)
        dedu(20f, 3.5f)
        dedu(10f, 4f)
        dedu(10f, 5f)

        if (percentageOver > 0f) deduction += (percentageOver / 6f)
        return deduction
    }

    internal fun timeLimitFor(floor: String): Int = DungeonScoring.current.rulesFor(floor).timeLimitSeconds

    fun colorizeScore(score: Int): String = when {
        score < 270 -> "§c$score"
        score < 300 -> "§e$score"
        else -> "§a$score"
    }

    fun colorizeBool(value: Boolean, trueCode: String = "§a", falseCode: String = "§c"): String =
        if (value) trueCode else falseCode

    fun formatBonus(mimic: Boolean, prince: Boolean, bat: Boolean): String =
        "${colorizeBool(mimic)}M §8| ${colorizeBool(prince)}P §8| ${colorizeBool(bat)}B"

    fun colorCodeCrypts(crypts: Int): String =
        if (crypts >= 5) "§a" else "§c"

    fun getSecretsFor300(totalSecrets: Int, floorName: String? = null): Int {
        if (totalSecrets <= 0) return 0
        val floor = floorName ?: LocationUtils.dungeonFloor?.label ?: "F7"
        val reqSecret = DungeonScoring.current.rulesFor(floor).requiredSecretPercentage
        val floorNum = floor.lastOrNull()?.digitToIntOrNull() ?: 0

        return when (DungeonMap.calculationMode.value) {
            DungeonMap.CALCULATION_OPTIMISTIC -> getOptimisticSecretsFor300(totalSecrets, reqSecret, floorNum)
            else -> getConservativeSecretsFor300(totalSecrets, reqSecret, floorNum)
        }
    }

    fun getRemainingSecretsFor300(foundSecrets: Int, totalSecrets: Int, floorName: String? = null): Int =
        maxOf(0, getSecretsFor300(totalSecrets, floorName) - foundSecrets)

    fun getDisplayedSecretsFor300(foundSecrets: Int, totalSecrets: Int, floorName: String? = null): Int =
        if (DungeonMap.calculationMode.value == DungeonMap.CALCULATION_OPTIMISTIC && DungeonMap.showNeededSecrets.value) {
            getSecretsFor300(totalSecrets, floorName)
        } else {
            getRemainingSecretsFor300(foundSecrets, totalSecrets, floorName)
        }

    internal fun princeBonusPossible(): Boolean {
        val rooms = MapState.uniqueRooms.values
        if (MapOverrides.showsUnrevealed) return rooms.any { it.data.containsPrince }
        if (rooms.isEmpty()) return true
        if (rooms.any { it.data.containsPrince && it.mainRoom.state != RoomState.UNDISCOVERED && it.mainRoom.state != RoomState.UNOPENED }) {
            return true
        }
        return rooms.any {
            it.data.type != RoomType.BLOOD &&
                (it.mainRoom.state == RoomState.UNDISCOVERED || it.mainRoom.state == RoomState.UNOPENED)
        }
    }

    internal fun estimatedTotalSecrets(): Int = estimateTotalSecrets(foundSecrets, secretPercentage)

    internal fun estimateTotalSecrets(foundSecrets: Int, secretPercentage: Double): Int =
        if (foundSecrets == 0 || secretPercentage == 0.0) 0
        else floor(100 / secretPercentage.toFloat() * foundSecrets + 0.5f).toInt()

    fun colorCodeByPercent(value: Number, maxValue: Number, reversed: Boolean = false): String {
        val max = maxValue.toFloat().coerceAtLeast(1f)
        val current = value.toFloat().coerceIn(0f, max)
        val percentage = (current / max) * 100f

        return when {
            percentage > 75 -> if (reversed) "§c" else "§a"
            percentage > 50 -> if (reversed) "§6" else "§e"
            percentage > 25 -> if (reversed) "§e" else "§6"
            else -> if (reversed) "§a" else "§c"
        }
    }

    fun reset() {
        secretFeed.reset()
        lastSecretsSkip = null
        lastActionBarSecrets = null
        bloodDone = false
        mimicKilled = false
        princeKilled = false
        batKilled = false
        batKillers.clear()
        deathCount = 0
        foundSecrets = 0
        cryptsCount = 0
        secretPercentage = 0.0
        clearedPercentage = 0
        completedRooms = 0
        secondsElapsed = 0
        score = 120
        board = EMPTY_PARTS
        target = EMPTY_PARTS
    }
}
