package org.blackaddons.api.skyblock.profile

import com.google.gson.JsonObject
import org.blackaddons.api.Platform
import org.blackaddons.api.skyblock.dungeon.DungeonClass
import org.blackaddons.api.skyblock.dungeon.DungeonFloor
import org.blackaddons.api.world.ItemInfo
import java.util.UUID

// Plain classes, not data classes: the host builds them, and a field added later must not break a compiled addon.

/** Every Skyblock profile of one player, as of [fetchedAt] (epoch ms). */
class PlayerProfiles(
    val uuid: UUID,
    val name: String,
    val profiles: List<SkyblockProfile>,
    val fetchedAt: Long,
) {
    /** The profile the player has selected, or the first one. */
    val selected: SkyblockProfile? get() = profiles.firstOrNull { it.selected } ?: profiles.firstOrNull()
}

class SkyblockProfile(
    val id: String,
    val cuteName: String,
    val mode: ProfileMode,
    val selected: Boolean,
    /** The player that was looked up. */
    val member: ProfileMember,
    /** The other members, without stats. Look a member up to get those. Empty for a solo profile. */
    val coop: List<CoopMember>,
)

/**
 * [name] when it is known without a request (seen this session, or in the game's name cache). Otherwise use
 * `Platform.mojang.byUuid`.
 */
class CoopMember(val uuid: UUID, val name: String?)

/** One player's data on one profile. [raw] is Hypixel's member object, for what is not modeled here yet. */
class ProfileMember(
    val uuid: UUID,
    val skyblockLevel: Int,
    /** From 0 to 1 toward the next level. */
    val skyblockProgress: Double,
    val purse: Double,
    /** Includes skills added after this build, with [SkillStat.skill] set to null. */
    val skills: List<SkillStat>,
    /** Averaged over the [Skill.countsTowardAverage] skills only. */
    val skillAverage: Double,
    val dungeons: DungeonStats,
    val slayers: List<SlayerStat>,
    /** Completions per tier. Tiers that were never cleared are missing. */
    val kuudraRuns: Map<KuudraTier, Int>,
    /** Null when the backend sent no networth. */
    val networth: Networth?,
    val fairySouls: Int,
    /** Epoch ms. */
    val firstJoin: Long,
    val cookieBuff: Boolean,
    val magicalPower: Int,
    val bestiary: List<BestiaryMobStat>,
    val pets: List<ProfilePet>,
    /** What the player's API settings allow. The items come from [SkyblockProfiles.inventory]. */
    val inventoryPages: Set<InventoryPage>,
    val raw: JsonObject,
) {
    fun skill(skill: Skill): SkillStat? = skills.firstOrNull { it.skill == skill }

    fun slayer(slayer: Slayer): SlayerStat? = slayers.firstOrNull { it.slayer == slayer }
}

/** [cap] is this player's own cap (farming and taming go up with perks). [maxed] means the absolute ceiling. */
class SkillStat(
    val id: String,
    val skill: Skill?,
    val xp: Double,
    val level: Int,
    val progress: Double,
    val cap: Int,
    val maxed: Boolean,
) {
    val name: String get() = skill?.displayName ?: id.replaceFirstChar(Char::uppercase)
    val countsTowardAverage: Boolean get() = skill?.countsTowardAverage == true
}

class SlayerStat(
    val id: String,
    val slayer: Slayer?,
    val xp: Double,
    val level: Int,
    val progress: Double,
    val cap: Int,
    /** Index 0 is tier I. */
    val kills: List<Int>,
) {
    val name: String get() = id.replaceFirstChar(Char::uppercase)
}

class DungeonStats(
    val catacombsXp: Double,
    /** Counts overflow levels past 50. */
    val catacombsLevel: Int,
    val catacombsProgress: Double,
    /** All five classes. A class that was never played is at level 0. */
    val classes: List<DungeonClassStat>,
    val secretsFound: Int,
    val bloodMobKills: Int,
    /** Only floors completed at least once, hardest first. */
    val floors: List<FloorStats>,
    /** The recorded best runs Hypixel keeps per floor. */
    val bestRuns: List<BestRun>,
) {
    val selectedClass: DungeonClass? get() = classes.firstOrNull { it.selected }?.dungeonClass

    fun floor(floor: DungeonFloor): FloorStats? = floors.firstOrNull { it.floor == floor }
}

class DungeonClassStat(
    val dungeonClass: DungeonClass,
    val xp: Double,
    val level: Int,
    val progress: Double,
    val selected: Boolean,
)

/** Times in ms. Null when the floor was never done at that rank. */
class FloorStats(
    val floor: DungeonFloor,
    val runs: Int,
    val bestScore: Int,
    val fastestS: Long?,
    val fastestSPlus: Long?,
)

class BestRun(
    val floor: DungeonFloor,
    /** Epoch ms. */
    val completedAt: Long,
    val elapsedMillis: Long,
    val teammates: Set<UUID>,
)

/** Categories that the player hides are in [unavailable]. Show those differently from a zero. */
class Networth(
    val total: Double,
    val purse: Double,
    val bank: Double,
    val categories: Map<String, Double>,
    val unavailable: Set<String>,
)

class BestiaryMobStat(val id: String, val kills: Long, val deaths: Long)

/** From [SkyblockProfiles.inventory]. Pages that the player hides have empty lists, and empty slots are null. */
class ProfileInventory(
    val main: List<ProfileItem?>,
    val armor: List<ProfileItem?>,
    val equipment: List<ProfileItem?>,
    val enderChest: List<InventoryStorage>,
    /** Pages of nine sets each, with one row per armor piece. */
    val wardrobe: List<InventoryStorage>,
    val personalVault: List<ProfileItem?>,
    val backpacks: List<InventoryStorage>,
    val accessoryBag: List<ProfileItem?> = emptyList(),
    val potionBag: List<ProfileItem?> = emptyList(),
    val quiver: List<ProfileItem?> = emptyList(),
    val fishingBag: List<ProfileItem?> = emptyList(),
)

/** [index] starts at 1, the way the game numbers pages and backpacks. */
class InventoryStorage(val index: Int, val slots: List<ProfileItem?>, val icon: ProfileItem? = null)

/** [raw] is the host's item stack format, which `ScreenDraw.item` and `Render` can draw. */
class ProfileItem(val raw: String) {
    /** Name, lore and Skyblock id. Null when the stack cannot be read. */
    val info: ItemInfo? by lazy { Platform.items.describe(raw) }
}

class ProfilePet(
    /** e.g. `GOLDEN_DRAGON`. */
    val type: String,
    /** e.g. `LEGENDARY`. */
    val tier: String,
    val experience: Double,
    val active: Boolean,
    val heldItem: String? = null,
    val candyUsed: Int = 0,
    val skin: String? = null,
)
