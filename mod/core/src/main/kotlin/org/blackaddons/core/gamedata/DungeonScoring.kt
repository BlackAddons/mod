package org.blackaddons.core.gamedata

import com.google.gson.JsonObject
import org.blackaddons.api.util.io.child
import org.blackaddons.api.util.io.double
import org.blackaddons.api.util.io.int
import org.blackaddons.core.backend.Backend
import org.blackaddons.core.log.Log
import org.blackaddons.core.storage.ServedDocument

internal data class FloorRules(
    val number: Int,
    val requiredSecretPercentage: Double,
    val timeLimitSeconds: Int,
    /** 0.7 on Entrance, 1 elsewhere. Applied per component, like the game. */
    val scoreMultiplier: Double = 1.0,
)

/**
 * Bonus points outside the four components (game values). Solo clears removes [paul], see
 * [DungeonScoringTable.withoutMayorPerk].
 */
internal data class BonusRules(
    val maxCrypts: Int = 5,
    /** The mimic's chest counts as a secret, separately. */
    val mimic: Int = 2,
    val mimicFloor: Int = 6,
    val prince: Int = 1,
    val bat: Int = 1,
    val paul: Int = 10,
)

/** Score coefficients as served data. Hypixel changes them, and the backend reads the same document. */
internal data class DungeonScoringTable(
    val floors: Map<String, FloorRules>,
    val bonus: BonusRules = BonusRules(),
    /** Caps a component that got parsed wrong. */
    val cap: Int = 320,
    val unknownFloor: FloorRules = FloorRules(0, 1.0, 600),
) {

    fun rulesFor(floor: String?): FloorRules {
        if (floor == null) return unknownFloor
        floors[floor]?.let { return it }
        return floors.entries.firstOrNull { it.key.equals(floor, ignoreCase = true) }?.value ?: unknownFloor
    }

    /**
     * The solo clears board ignores the mayor perk, so 300 means the same every week. Everything else counts it, like
     * the game.
     */
    fun withoutMayorPerk(): DungeonScoringTable =
        if (bonus.paul == 0) this
        else copy(bonus = bonus.copy(paul = 0), cap = (cap - bonus.paul).coerceAtLeast(0))
}

object DungeonScoring {

    private const val KEY = "dungeon_scoring"

    /** From Hypixel 0.27.0.18. `E` and `F0` are both the entrance. */
    internal val COMPILED = DungeonScoringTable(
        floors = buildMap {
            val entrance = FloorRules(0, 0.3, 600, scoreMultiplier = 0.7)
            put("Entrance", entrance)
            put("E", entrance)
            put("F0", entrance)
            put("F1", FloorRules(1, 0.3, 600))
            put("F2", FloorRules(2, 0.4, 600))
            put("F3", FloorRules(3, 0.5, 600))
            put("F4", FloorRules(4, 0.6, 720))
            put("F5", FloorRules(5, 0.7, 600))
            put("F6", FloorRules(6, 0.85, 720))
            put("F7", FloorRules(7, 1.0, 840))
            for (master in 1..5) put("M$master", FloorRules(master, 1.0, 480))
            put("M6", FloorRules(6, 1.0, 600))
            put("M7", FloorRules(7, 1.0, 840))
        },
    )

    @Volatile
    internal var current: DungeonScoringTable = COMPILED
        private set

    fun load() = ServedDocument.load("${Backend.GAMEDATA_URL}/$KEY", "gamedata/$KEY") { document ->
        current = parse(document)
        Log.main.debug("Dungeon scoring loaded from game data: {} floors", current.floors.size)
    }

    /** Merged over the compiled table per floor, so a stale document can't undo or delete floors. */
    internal fun parse(document: JsonObject): DungeonScoringTable {
        val served = document.child("floors")?.entrySet().orEmpty()
            .mapNotNull { (name, value) -> (value as? JsonObject)?.let { name to floor(it) } }
            .toMap()

        return DungeonScoringTable(
            floors = COMPILED.floors + served,
            bonus = document.child("bonus")?.let(::bonus) ?: COMPILED.bonus,
            cap = document.int("cap", COMPILED.cap).takeIf { it > 0 } ?: COMPILED.cap,
            unknownFloor = document.child("unknownFloor")?.let(::floor) ?: COMPILED.unknownFloor,
        )
    }

    /** A fraction (`0.7`). Values above 1 get rejected as a percent mistake. */
    private fun floor(rules: JsonObject): FloorRules {
        val required = rules.double("requiredSecretPercentage", 1.0)
        return FloorRules(
            number = rules.int("number", 0),
            requiredSecretPercentage = if (required > 0.0 && required <= 1.0) required else 1.0,
            timeLimitSeconds = rules.int("timeLimitSeconds", 600).takeIf { it > 0 } ?: 600,
            scoreMultiplier = rules.double("scoreMultiplier", 1.0).takeIf { it > 0.0 } ?: 1.0,
        )
    }

    private fun bonus(rules: JsonObject): BonusRules = BonusRules(
        maxCrypts = rules.int("maxCrypts", COMPILED.bonus.maxCrypts),
        mimic = rules.int("mimic", COMPILED.bonus.mimic),
        mimicFloor = rules.int("mimicFloor", COMPILED.bonus.mimicFloor),
        prince = rules.int("prince", COMPILED.bonus.prince),
        bat = rules.int("bat", COMPILED.bonus.bat),
        paul = rules.int("paul", COMPILED.bonus.paul),
    )
}
