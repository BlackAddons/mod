package org.blackaddons.core.profile

import org.blackaddons.api.skyblock.SkyblockLevel
import org.blackaddons.api.skyblock.SkyblockLevelsProvider
import org.blackaddons.core.gamedata.SkillCaps

internal object CoreSkyblockLevels : SkyblockLevelsProvider {

    private const val CATACOMBS_CAP = 50

    override fun skillCeiling(skill: String): Int = SkillCaps.ceiling(skill)

    override fun skill(xp: Double, cap: Int): SkyblockLevel =
        SkyblockLevel(SkyblockMath.skillLevel(xp, cap), SkyblockMath.skillProgress(xp, cap), cap)

    override fun catacombs(xp: Double): SkyblockLevel =
        SkyblockLevel(SkyblockMath.catacombsLevel(xp), SkyblockMath.catacombsProgress(xp), CATACOMBS_CAP)

    override fun catacombsXpFor(level: Double): Double = SkyblockMath.catacombsXpForLevel(level)

    override fun slayer(xp: Double, vampire: Boolean): SkyblockLevel = SkyblockLevel(
        SkyblockMath.slayerLevel(xp, vampire), SkyblockMath.slayerProgress(xp, vampire), SkyblockMath.slayerCap(vampire),
    )
}
