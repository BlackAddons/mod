package org.blackaddons.core.ui.screen.pv.tabs
import org.blackaddons.api.skyblock.dungeon.DungeonClass
import org.blackaddons.core.profile.SkyblockMath
import java.math.BigDecimal
import java.math.RoundingMode
import kotlin.math.ceil

internal enum class Ca50Floor(val label: String, val baseXp: Double) {
    M7("M7", 300_000.0),
    M6("M6", 100_000.0),
    M5("M5", 70_000.0),
    M4("M4", 55_000.0),
    M3("M3", 35_000.0),
    M2("M2", 20_000.0),
    M1("M1", 15_000.0),
    F7("F7", 28_000.0),
    F6("F6", 4_880.0),
    F5("F5", 2_400.0),
    F4("F4", 1_420.0),
    F3("F3", 560.0),
    F2("F2", 220.0),
    F1("F1", 110.0),
    ENTRANCE("Entrance", 55.0),
}

internal enum class Ca50ScarfAccessory(val bonus: Double) {
    NONE(0.0),
    STUDIES(0.02),
    THESIS(0.04),
    GRIMOIRE(0.06),
}

internal enum class Ca50GlobalBoost(val multiplier: Double) {
    NONE(1.0),
    FIVE(1.05),
    TEN(1.10),
    FIFTEEN(1.15),
    TWENTY(1.20),
    THIRTY(1.30),
}

internal enum class Ca50Mayor(val multiplier: Double) {
    NONE(1.0),
    DERPY(1.5),
    AURA(1.55),
}

internal data class Ca50Settings(
    val targetLevel: Double = 50.0,
    val floor: Ca50Floor = Ca50Floor.M7,
    val expertRing: Boolean = true,
    val hecatombLevel: Int = 10,
    val scarfAccessory: Ca50ScarfAccessory = Ca50ScarfAccessory.GRIMOIRE,
    val scarfAttributeLevel: Int = 10,
    val globalBoost: Ca50GlobalBoost = Ca50GlobalBoost.NONE,
    val mayor: Ca50Mayor = Ca50Mayor.NONE,
)

internal data class Ca50ClassResult(
    val dungeonClass: DungeonClass,
    val currentXp: Double,
    val remainingXp: Double,
    val runs: Long,
)

internal data class Ca50Result(
    val targetLevel: Double,
    val targetXp: Double,
    val catacombsXpPerRun: Double,
    val catacombsRemainingXp: Double,
    val catacombsRuns: Long,
    val classXpPerRun: Double,
    val totalClassRuns: Long,
    val classes: List<Ca50ClassResult>,
)

/** The old RTCA assumptions, evaluated without its 200,000-iteration ceiling. */
internal object PvCa50Calculator {

    const val MIN_TARGET_LEVEL = 1.0
    const val MAX_TARGET_LEVEL = 200.0

    fun calculate(catacombsXp: Double, classXp: Map<DungeonClass, Double>, settings: Ca50Settings): Ca50Result {
        require(settings.targetLevel in MIN_TARGET_LEVEL..MAX_TARGET_LEVEL) {
            "CA50 target level must be between ${MIN_TARGET_LEVEL.toInt()} and ${MAX_TARGET_LEVEL.toInt()}"
        }
        require(settings.hecatombLevel in 0..10) { "Hecatomb level must be between 0 and 10" }
        require(settings.scarfAttributeLevel in 0..10) { "Scarf attribute level must be between 0 and 10" }

        val targetXp = SkyblockMath.catacombsXpForLevel(settings.targetLevel)
        val cataPerRun = catacombsXpPerRun(settings)
        val cataRemaining = (targetXp - catacombsXp).coerceAtLeast(0.0)
        val cataRuns = if (cataRemaining == 0.0) 0L else ceil(cataRemaining / cataPerRun).toLong()

        val classPerRun = classXpPerRun(settings)
        val current = CLASSES.map { (classXp[it] ?: 0.0).coerceAtLeast(0.0) }
        val remaining = current.map { (targetXp - it).coerceAtLeast(0.0) }
        val totalRuns = minimumClassRuns(remaining, classPerRun)
        val classRuns = allocateClassRuns(remaining, classPerRun, totalRuns)

        return Ca50Result(
            targetLevel = settings.targetLevel,
            targetXp = targetXp,
            catacombsXpPerRun = cataPerRun,
            catacombsRemainingXp = cataRemaining,
            catacombsRuns = cataRuns,
            classXpPerRun = classPerRun,
            totalClassRuns = totalRuns,
            classes = CLASSES.indices.map { index ->
                Ca50ClassResult(CLASSES[index], current[index], remaining[index], classRuns[index])
            },
        )
    }

    fun catacombsXpPerRun(settings: Ca50Settings): Double {
        val base = settings.floor.baseXp
        val maxCompletions = when {
            base >= 15_000.0 -> 26
            base == 4_880.0 -> 51
            else -> 76
        }
        val completions = maxCompletions - 1
        val ring = if (settings.expertRing) 0.1 else 0.0
        val hecatomb = hecatombBonus(settings.hecatombLevel)
        val multiplier = when {
            settings.expertRing && settings.mayor.multiplier > 1.0 ->
                0.95 + (settings.mayor.multiplier - 1.0) + completions / 100.0 + ring + hecatomb +
                    completions * (0.024 + hecatomb / 50.0)
            settings.expertRing ->
                0.95 + ring + hecatomb + completions * (0.024 + hecatomb / 50.0)
            else ->
                0.95 + hecatomb + completions * (0.022 + hecatomb / 50.0)
        }
        return ceil(base * multiplier * settings.globalBoost.multiplier)
    }

    fun classXpPerRun(settings: Ca50Settings): Double {
        val hecatomb = hecatombBonus(settings.hecatombLevel)
        val scarfAttribute = settings.scarfAttributeLevel * 0.02
        return settings.floor.baseXp *
            (1.0 + hecatomb * 2.0 + settings.scarfAccessory.bonus + scarfAttribute) *
            settings.globalBoost.multiplier * settings.mayor.multiplier
    }

    private fun hecatombBonus(level: Int): Double = if (level == 0) 0.0 else 0.004 + level * 0.0016

    private fun minimumClassRuns(remaining: List<Double>, xpPerRun: Double): Long {
        if (remaining.all { it == 0.0 }) return 0L

        var upper = 1L
        while (!isFeasible(remaining, xpPerRun, upper)) upper *= 2
        var lower = 0L
        while (lower + 1 < upper) {
            val middle = lower + (upper - lower) / 2
            if (isFeasible(remaining, xpPerRun, middle)) upper = middle else lower = middle
        }
        return upper
    }

    private fun isFeasible(remaining: List<Double>, xpPerRun: Double, totalRuns: Long): Boolean {
        var needed = 0L
        for (xp in remaining) {
            needed += requiredClassRuns(xp, xpPerRun, totalRuns)
            if (needed > totalRuns) return false
        }
        return true
    }

    private fun allocateClassRuns(remaining: List<Double>, xpPerRun: Double, totalRuns: Long): List<Long> {
        val runs = remaining.mapTo(mutableListOf()) { requiredClassRuns(it, xpPerRun, totalRuns) }
        var unassigned = totalRuns - runs.sum()
        while (unassigned > 0) {
            val index = runs.indices.maxBy { i ->
                remaining[i] - xpPerRun * (totalRuns * PASSIVE_SHARE + runs[i] * ACTIVE_EXTRA_SHARE)
            }
            runs[index]++
            unassigned--
        }
        return runs
    }

    private fun requiredClassRuns(remainingXp: Double, xpPerRun: Double, totalRuns: Long): Long {
        val perRun = BigDecimal.valueOf(xpPerRun)
        val passiveXp = perRun.multiply(BigDecimal.valueOf(totalRuns)).multiply(BigDecimal.valueOf(PASSIVE_SHARE))
        val afterPassive = BigDecimal.valueOf(remainingXp).subtract(passiveXp)
        if (afterPassive.signum() <= 0) return 0L
        return afterPassive.divide(perRun.multiply(BigDecimal.valueOf(ACTIVE_EXTRA_SHARE)), 0, RoundingMode.CEILING).longValueExact()
    }

    private const val PASSIVE_SHARE = 0.25
    private const val ACTIVE_EXTRA_SHARE = 0.75
    private val CLASSES = DungeonClass.entries.filterNot { it == DungeonClass.EMPTY }
}
