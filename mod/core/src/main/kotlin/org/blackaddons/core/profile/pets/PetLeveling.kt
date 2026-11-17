package org.blackaddons.core.profile.pets

import org.blackaddons.api.skyblock.profile.ProfilePet
import org.blackaddons.core.gamedata.LevelingTables

/**
 * Pet level from profile XP. The curve and special pets match NotEnoughUpdates-REPO's `constants/pets.json` (MIT, see
 * bundled notice). Kept local, so there's no third-party request.
 */
internal object PetLeveling {

    internal data class Progress(
        val level: Int?,
        val cap: Int?,
        val toNext: Double?,
        val toMax: Double?,
    ) {
        val available get() = level != null
        val maxed get() = available && level == cap
    }

    fun resolve(pet: ProfilePet): Progress {
        if (!pet.experience.isFinite() || pet.experience < 0.0) return Progress(null, null, null, null)
        val costs = costs(pet) ?: return Progress(null, null, null, null)
        var spent = 0.0
        var level = 1
        for (cost in costs) {
            if (pet.experience < spent + cost) {
                return Progress(level, costs.size + 1, (pet.experience - spent) / cost, pet.experience / costs.sum())
            }
            spent += cost
            level++
        }
        return Progress(level, costs.size + 1, 1.0, 1.0)
    }

    private fun costs(pet: ProfilePet): DoubleArray? {
        val offset = when (pet.type.uppercase()) {
            "BINGO" -> 0
            else -> RARITY_OFFSETS[pet.tier.uppercase()] ?: return null
        }
        val multiplier = if (pet.type.uppercase() == "REINDEER") 2 else 1
        val base = DEFAULT_COSTS.drop(offset).take(DEFAULT_CAP - 1).map { it * multiplier }
        val costs = if (pet.type.uppercase() in LONG_CURVE_PETS) {
            base + List(LONG_CURVE_CAP - DEFAULT_CAP) { LONG_CURVE_COST }
        } else {
            base
        }
        return costs.map { it.toDouble() }.toDoubleArray()
    }

    private const val DEFAULT_CAP = 100
    private const val LONG_CURVE_CAP = 200
    private const val LONG_CURVE_COST = 1_886_700L

    private val LONG_CURVE_PETS = setOf("GOLDEN_DRAGON", "JADE_DRAGON", "ROSE_DRAGON")
    private val RARITY_OFFSETS = mapOf(
        "COMMON" to 0, "UNCOMMON" to 6, "RARE" to 11, "EPIC" to 16, "LEGENDARY" to 20, "MYTHIC" to 20,
        "SPECIAL" to 16, "VERY_SPECIAL" to 20,
    )

    // NEU-REPO pet_levels as costs per level.
    private val DEFAULT_COSTS: LongArray get() = LevelingTables.current.pet
}
