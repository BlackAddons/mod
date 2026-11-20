package org.blackaddons.core.dungeon

import org.blackaddons.api.skyblock.LocationUtils
import org.blackaddons.api.skyblock.dungeon.RoomState
import org.blackaddons.core.dungeon.ScoreCalculation.ScoreParts
import org.blackaddons.core.dungeon.map.MapState
import org.blackaddons.core.features.impl.dungeons.map.DungeonMap
import org.blackaddons.core.gamedata.DungeonScoring
import org.blackaddons.core.gamedata.FloorRules
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.roundToInt

// The optimistic and conservative score and secret projections behind ScoreCalculation.

/** Exactly the same as the backend's `DungeonScoring.Evaluate`. */
internal fun ScoreCalculation.conservative(
    rules: FloorRules,
    floorNum: Int,
    unresolvedPuzzles: Int,
    includePaul: Boolean,
    creditedRooms: Int = 0,
): ScoreParts {
    val inBoss = LocationUtils.inBoss
    val effectiveCompletedRooms =
        completedRooms + creditedRooms + (if (!bloodDone) 1 else 0) + (if (!inBoss) 1 else 0)
    val roomShare = if (completedRooms > 0) effectiveCompletedRooms / totalRooms() else 0.0
    val secretsScore =
        floor((secretPercentage / rules.requiredSecretPercentage) / 100.0 * 40.0).coerceIn(0.0, 40.0).toInt()
    val completedRoomScore = (roomShare * 60.0).coerceIn(0.0, 60.0).toInt()
    val skillRooms = floor(roomShare * 80.0).coerceIn(0.0, 80.0).toInt()
    val deathPenalty = (deathCount * 2 - 1).coerceAtLeast(0)
    val skillScore = (20 + skillRooms - unresolvedPuzzles * 10 - deathPenalty).coerceIn(20, 100)

    val limit = rules.timeLimitSeconds
    val speedScore = if (secondsElapsed <= limit) {
        100
    } else {
        val percentageOver = ((secondsElapsed - limit) * 100f) / limit.toFloat()
        (100 - getSpeedDeduction(percentageOver)).toInt().coerceAtLeast(0)
    }

    val table = DungeonScoring.current.let { if (includePaul) it else it.withoutMayorPerk() }
    val scale = rules.scoreMultiplier
    fun scaled(component: Int): Int =
        if (scale <= 0.0 || scale == 1.0) component else (component * scale).roundToInt()
    return ScoreParts(
        secrets = scaled(secretsScore),
        rooms = scaled(completedRoomScore),
        skill = scaled(skillScore),
        speed = scaled(speedScore),
        bonus = scaled(getBonusScore(floorNum, includePaul)),
        cap = table.cap,
    )
}

/** From "Cleared: N%" on the sidebar, otherwise from identified map rooms, like the backend. */
internal fun ScoreCalculation.totalRooms(): Double {
    val fallback = maxOf(completedRooms, MapState.uniqueRooms.size, 1).toDouble()
    if (clearedPercentage <= 0 || completedRooms <= 0) return fallback
    val estimate = floor(completedRooms / (clearedPercentage / 100.0) + 0.4)
    return if (estimate > 0.0 && estimate.isFinite()) estimate else fallback
}

internal fun ScoreCalculation.calculateOptimisticScore(floorNum: Int, reqSecret: Double): Int {
    val calculatedTotalRooms = if (completedRooms == 0 || clearedPercentage == 0) 0 else
        floor(completedRooms / (clearedPercentage * 0.01) + 0.4).toInt()
    val totalRooms = calculatedTotalRooms.takeIf { it != 0 } ?: 36
    val effectiveCompletedRooms = completedRooms + (if (!bloodDone) 1 else 0) +
        (if (!LocationUtils.inBoss) 1 else 0)
    val totalSecrets = if (foundSecrets == 0 || secretPercentage == 0.0) 0 else
        floor(100 / secretPercentage * foundSecrets + 0.5).toInt()

    val secretScore = if (totalSecrets > 0) {
        floor(foundSecrets.toDouble() / (totalSecrets * reqSecret) * 40.0).toInt().coerceIn(0, 40)
    } else 0
    val exploration = secretScore +
        floor(effectiveCompletedRooms.toDouble() / totalRooms * 60.0).toInt().coerceIn(0, 60)
    val skillRooms = floor(effectiveCompletedRooms.toDouble() / totalRooms * 80.0).toInt().coerceIn(0, 80)
    val completedPuzzles = DungeonTracker.puzzles.count {
        it.state == RoomState.GREEN || it.state == RoomState.CLEARED
    }
    val puzzleCount = maxOf(DungeonTracker.maxPuzzleCount, DungeonTracker.puzzles.size)
    val puzzlePenalty = (puzzleCount - completedPuzzles).coerceAtLeast(0) * 10
    val deathPenalty = (deathCount * 2 - 1).coerceAtLeast(0)

    return exploration + (20 + skillRooms - puzzlePenalty - deathPenalty).coerceIn(20, 100) +
        100 + getBonusScore(floorNum)
}

internal fun ScoreCalculation.getOptimisticSecretsFor300(totalSecrets: Int, reqSecret: Double, floorNum: Int): Int {
    val deathPenalty = (deathCount * 2 - 1).coerceAtLeast(0)
    var bonusScore = if (DungeonMap.maxBonusForSecretGoal.value) {
        getProjectedBonusScore(floorNum)
    } else {
        getBonusScore(floorNum, includePaul = false)
    }
    if (DungeonMap.includePaulInSecretGoal.value && !DungeonMap.forceDisablePaul.value) {
        bonusScore += DungeonScoring.current.bonus.paul
    }
    val neededSecretScore = 40 - bonusScore + deathPenalty
    return ceil(totalSecrets * reqSecret.toFloat() * neededSecretScore / 40f).toInt()
}

internal fun ScoreCalculation.getConservativeSecretsFor300(totalSecrets: Int, reqSecret: Double, floorNum: Int): Int {
    val puzzlePenalty = unresolvedPuzzles() * 10
    val deathPenalty = (deathCount * 2 - 1).coerceAtLeast(0)
    val expectedSkill = (100 - puzzlePenalty - deathPenalty).coerceIn(20, 100)
    val nonSecretScore = 60 + 100 + expectedSkill + getBonusScore(floorNum)
    val neededSecretScore = (300 - nonSecretScore).coerceIn(0, 40)
    return ceil(totalSecrets * reqSecret * neededSecretScore / 40.0).toInt().coerceIn(0, totalSecrets)
}

internal fun ScoreCalculation.getBonusScore(floorNum: Int, includePaul: Boolean = true): Int {
    val rules = DungeonScoring.current.bonus
    var bonus = cryptsCount.coerceIn(0, rules.maxCrypts) + getEarnedSpecialBonus(floorNum)
    if (includePaul && PaulBonus.active(DungeonMap.forcePaul.value, DungeonMap.forceDisablePaul.value)) {
        bonus += rules.paul
    }
    return bonus
}

internal fun ScoreCalculation.getProjectedBonusScore(floorNum: Int): Int {
    val rules = DungeonScoring.current.bonus
    var bonus = if (DungeonMap.dynamicCryptsForSecretGoal.value) {
        cryptsCount.coerceIn(0, rules.maxCrypts)
    } else {
        rules.maxCrypts
    }
    if (mimicKilled || (DungeonMap.assumeMimicForSecretGoal.value && floorNum >= rules.mimicFloor)) {
        bonus += rules.mimic
    }
    if (princeKilled || (DungeonMap.assumePrinceForSecretGoal.value && princeBonusPossible())) bonus += rules.prince
    if (batKilled || DungeonMap.assumeBatForSecretGoal.value) bonus += rules.bat
    return bonus
}

internal fun ScoreCalculation.getEarnedSpecialBonus(floorNum: Int): Int {
    val rules = DungeonScoring.current.bonus
    var bonus = 0
    if (mimicKilled && floorNum >= rules.mimicFloor) bonus += rules.mimic
    if (princeKilled) bonus += rules.prince
    if (batKilled) bonus += rules.bat
    return bonus
}
