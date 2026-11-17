package org.blackaddons.api.skyblock

import org.blackaddons.api.HostApi

/** A level computed from experience. [progress] goes from 0 to 1 toward the next level and is 1 at [cap]. */
data class SkyblockLevel(val level: Int, val progress: Double, val cap: Int) {
    val maxed: Boolean get() = level >= cap
}

/**
 * Skyblock levels from experience, using the game tables the backend serves (the bundled ones until it answers). This
 * is pure math: the experience can come from the Hypixel API or anywhere else.
 */
object SkyblockLevels {

    @Volatile
    private var bound: SkyblockLevelsProvider? = null

    private val provider: SkyblockLevelsProvider
        get() = bound ?: error("SkyblockLevels was read before the host bound its provider")

    /** Host only. */
    @HostApi
    fun bind(provider: SkyblockLevelsProvider) {
        synchronized(this) {
            bound?.let { error("SkyblockLevels is already bound to ${it::class.java.name}") }
            bound = provider
        }
    }

    /**
     * The highest level [skill] can reach, with the skill named as Hypixel names it (`"combat"`). 50 for a skill we do
     * not know.
     */
    fun skillCeiling(skill: String): Int = provider.skillCeiling(skill.lowercase())

    /**
     * [cap] is for skills that a perk raises (farming, taming): pass the player's own cap if you know it. Overflow XP
     * does not count.
     */
    fun skill(skill: String, xp: Double, cap: Int = skillCeiling(skill)): SkyblockLevel = provider.skill(xp, cap)

    /** Counts the overflow levels past 50, but [SkyblockLevel.cap] stays 50. */
    fun catacombs(xp: Double): SkyblockLevel = provider.catacombs(xp)

    /** Experience for a (fractional) Catacombs [level], overflow included. */
    fun catacombsXpFor(level: Double): Double = provider.catacombsXpFor(level)

    fun slayer(xp: Double, vampire: Boolean = false): SkyblockLevel = provider.slayer(xp, vampire)

    /** One level per 100 leveling experience. */
    fun skyblock(levelingExperience: Double): Int = (levelingExperience / 100.0).toInt()
}

/** Host side of [SkyblockLevels], bound once. */
interface SkyblockLevelsProvider {
    fun skillCeiling(skill: String): Int
    fun skill(xp: Double, cap: Int): SkyblockLevel
    fun catacombs(xp: Double): SkyblockLevel
    fun catacombsXpFor(level: Double): Double
    fun slayer(xp: Double, vampire: Boolean): SkyblockLevel
}
