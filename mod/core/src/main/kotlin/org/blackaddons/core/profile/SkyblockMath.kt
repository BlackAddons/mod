package org.blackaddons.core.profile

import org.blackaddons.core.gamedata.LevelingTables

/** Skyblock level math from public game tables, written from scratch, not copied. */
internal object SkyblockMath {

    private val SKILL_XP: LongArray get() = LevelingTables.current.skill

    /** Capped at [maxLevel]. Overflow XP doesn't count. */
    fun skillLevel(xp: Double, maxLevel: Int = 60): Int {
        val cap = maxLevel.coerceIn(0, SKILL_XP.lastIndex)
        var level = 0
        while (level < cap && xp >= SKILL_XP[level + 1]) level++
        return level
    }

    private val CATACOMBS_XP: LongArray get() = LevelingTables.current.catacombs
    private val SLAYER_XP: LongArray get() = LevelingTables.current.slayer
    private val VAMPIRE_XP: LongArray get() = LevelingTables.current.vampire

    fun skyblockLevel(levelingExperience: Double): Int = (levelingExperience / 100.0).toInt()

    /** Flat XP per cosmetic level past 50. */
    private const val CATACOMBS_OVERFLOW_XP = 200_000_000.0

    /** Includes overflow levels past 50. */
    fun catacombsLevel(xp: Double): Int {
        val capXp = CATACOMBS_XP.last().toDouble()
        if (xp < capXp) return levelFor(xp, CATACOMBS_XP)
        return CATACOMBS_XP.lastIndex + ((xp - capXp) / CATACOMBS_OVERFLOW_XP).toInt()
    }

    fun catacombsXpForLevel(level: Double): Double {
        require(level.isFinite() && level >= 0.0) { "Catacombs level must be finite and non-negative" }
        val cap = CATACOMBS_XP.lastIndex
        if (level >= cap) return CATACOMBS_XP.last().toDouble() + (level - cap) * CATACOMBS_OVERFLOW_XP

        val whole = level.toInt()
        val current = CATACOMBS_XP[whole].toDouble()
        return current + (CATACOMBS_XP[whole + 1] - CATACOMBS_XP[whole]) * (level - whole)
    }

    fun slayerLevel(xp: Double, vampire: Boolean = false): Int =
        levelFor(xp, if (vampire) VAMPIRE_XP else SLAYER_XP)

    fun slayerCap(vampire: Boolean = false): Int = (if (vampire) VAMPIRE_XP else SLAYER_XP).lastIndex

    fun slayerProgress(xp: Double, vampire: Boolean = false): Double {
        val table = if (vampire) VAMPIRE_XP else SLAYER_XP
        return progressWithin(xp, table, table.lastIndex)
    }

    private fun levelFor(xp: Double, table: LongArray): Int {
        var level = 0
        while (level < table.lastIndex && xp >= table[level + 1]) level++
        return level
    }

    fun skillProgress(xp: Double, maxLevel: Int = 60): Double =
        progressWithin(xp, SKILL_XP, maxLevel.coerceIn(0, SKILL_XP.lastIndex))

    fun catacombsProgress(xp: Double): Double {
        val capXp = CATACOMBS_XP.last().toDouble()
        if (xp < capXp) return progressWithin(xp, CATACOMBS_XP, CATACOMBS_XP.lastIndex)
        return ((xp - capXp) % CATACOMBS_OVERFLOW_XP) / CATACOMBS_OVERFLOW_XP
    }

    private fun progressWithin(xp: Double, table: LongArray, cap: Int): Double {
        var level = 0
        while (level < cap && xp >= table[level + 1]) level++
        if (level >= cap) return 1.0
        val into = xp - table[level]
        val span = table[level + 1] - table[level]
        return if (span <= 0) 0.0 else (into / span).coerceIn(0.0, 1.0)
    }
}
