package org.blackaddons.core.profile

import com.google.gson.JsonElement
import com.google.gson.JsonObject
import org.blackaddons.api.Platform
import org.blackaddons.api.skyblock.dungeon.DungeonClass
import org.blackaddons.api.skyblock.dungeon.DungeonFloor
import org.blackaddons.api.skyblock.profile.*
import org.blackaddons.api.util.Uuids
import org.blackaddons.api.util.io.*
import org.blackaddons.api.util.stripFormatting
import org.blackaddons.core.gamedata.SkillCaps
import java.util.UUID

/**
 * Parses `/v1/skyblock/profiles` (`{ profiles: [Hypixel as is], networth: {profile: {member: ...}}, computedAt }`) into
 * the api model. Levels come from XP through [SkyblockMath], networth from the backend, coop names from what we know
 * without a request. Transport and status checks are in [HypixelProfileSource].
 */
internal object RawHypixelParser {

    private const val SKILL_XP_PREFIX = "SKILL_"
    private const val LEGACY_SKILL_XP_PREFIX = "experience_skill_"

    /** Most slayers stop at IV, Revenant and Riftstalker at V. */
    private const val SLAYER_TIERS = 5

    // Skill and slayer ids are a single word. `SKILL_FORAGING_extra_level_cap` sits next to the XP and is a cap perk.
    private val UNKNOWN_ID = Regex("[a-z]+")

    // In Hypixel's order, which is the order the Profile Viewer shows.
    private val CLASSES = listOf(
        "healer" to DungeonClass.HEALER,
        "mage" to DungeonClass.MAGE,
        "berserk" to DungeonClass.BERSERK,
        "archer" to DungeonClass.ARCHER,
        "tank" to DungeonClass.TANK,
    )
    private val ARMOR_SLOTS = listOf("HELMET", "CHESTPLATE", "LEGGINGS", "BOOTS")

    private const val BLOB_KEY = "data"

    // Hypixel spells it `equippment` on some profiles.
    private val MAIN_CONTAINERS = listOf("inv_contents", "inv_armor", "equippment_contents", "equipment_contents")
    private const val STORAGE_PAGE_SIZE = 45
    private const val BAGS = "bag_contents"
    private const val ACCESSORY_BAG = "talisman_bag"
    private const val POTION_BAG = "potion_bag"
    private const val QUIVER = "quiver"
    private const val FISHING_BAG = "fishing_bag"
    private const val WARDROBE_SETS_PER_PAGE = 9
    private val ENDER_CHEST_ICON = ProfileItem("{\"id\":\"minecraft:ender_chest\",\"count\":1}")

    fun parse(root: JsonObject, id: MojangId): SourceResult {
        val profiles = root.get("profiles")?.takeIf { it.isJsonArray }?.asJsonArray
            ?: return SourceResult.Empty
        if (profiles.size() == 0) return SourceResult.Empty
        val uuid = Uuids.parse(id.uuid) ?: return SourceResult.Empty

        val networth = root.child("networth")

        val recent = mutableMapOf<String, List<RecentActivity>>()
        val parsed = profiles.mapNotNull { element ->
            val profile = element.takeIf { it.isJsonObject }?.asJsonObject ?: return@mapNotNull null
            val profileId = profile.str("profile_id") ?: return@mapNotNull null
            val members = profile.child("members") ?: return@mapNotNull null
            val member = members.child(id.uuid) ?: return@mapNotNull null
            recent[profileId] = recentActivities(member)
            SkyblockProfile(
                id = profileId,
                cuteName = profile.str("cute_name") ?: "?",
                mode = ProfileMode.of(profile.str("game_mode")),
                selected = profile.bool("selected"),
                member = parseMember(uuid, member, networth.child(profileId).child(id.uuid)),
                coop = coop(members, id.uuid, MojangResolver::knownName),
            )
        }
        if (parsed.isEmpty()) return SourceResult.Empty
        LoadTrace.note("${parsed.size} profiles")

        return SourceResult.Ok(PlayerProfiles(uuid, id.name, parsed, System.currentTimeMillis()), recent)
    }

    /** Members that are still on the profile. Invites that weren't accepted yet and members who left get skipped. */
    internal fun coop(members: JsonObject, self: String, knownName: (UUID) -> String?): List<CoopMember> =
        members.entrySet().mapNotNull { (key, value) ->
            if (key.equals(self, ignoreCase = true)) return@mapNotNull null
            val profile = value.takeIf { it.isJsonObject }?.asJsonObject.child("profile")
            if (profile.child("deletion_notice") != null) return@mapNotNull null
            val invitation = profile.child("coop_invitation")
            if (invitation != null && !invitation.bool("confirmed")) return@mapNotNull null
            val uuid = Uuids.parse(key) ?: return@mapNotNull null
            CoopMember(uuid, knownName(uuid))
        }

    internal fun parseMember(uuid: UUID, member: JsonObject, memberNetworth: JsonObject?): ProfileMember {
        val skills = skills(member)
        val average = skills.filter(SkillStat::countsTowardAverage)
            .map(SkillStat::level)
            .let { if (it.isEmpty()) 0.0 else it.average() }

        val purse = member.child("currencies")?.double("coin_purse") ?: member.double("coin_purse")
        val levelingXp = member.child("leveling").double("experience")
        // The member's own block for this profile, not the enclosing profile.
        val memberProfile = member.child("profile")
        return ProfileMember(
            uuid = uuid,
            skyblockLevel = SkyblockMath.skyblockLevel(levelingXp),
            skyblockProgress = (levelingXp % 100.0) / 100.0,
            purse = purse,
            skills = skills,
            skillAverage = average,
            dungeons = dungeons(member),
            slayers = slayers(member),
            kuudraRuns = kuudraRuns(member),
            networth = parseNetworth(memberNetworth),
            fairySouls = member.child("fairy_soul").int("total_collected"),
            firstJoin = memberProfile.long("first_join"),
            cookieBuff = memberProfile.bool("cookie_buff_active"),
            magicalPower = member.child("accessory_bag_storage").int("highest_magical_power"),
            bestiary = bestiary(member),
            pets = pets(member),
            inventoryPages = inventoryPages(member),
            raw = member,
        )
    }

    /**
     * Known skills in game order, then any skills Hypixel added since, so a new skill shows up instead of breaking
     * things.
     */
    private fun skills(member: JsonObject): List<SkillStat> {
        val current = member.child("player_data").child("experience")
        val seen = buildSet {
            current?.keySet()?.filter { it.startsWith(SKILL_XP_PREFIX) }
                ?.forEach { add(it.removePrefix(SKILL_XP_PREFIX).lowercase()) }
            member.keySet().filter { it.startsWith(LEGACY_SKILL_XP_PREFIX) }
                .forEach { add(it.removePrefix(LEGACY_SKILL_XP_PREFIX).lowercase()) }
        }
        val known = Skill.entries.map(Skill::id)
        val ids = known + (seen - known.toSet()).filter(UNKNOWN_ID::matches).sorted()
        return ids.map { id ->
            val skill = Skill.of(id)
            val ceiling = SkillCaps.ceiling(id)
            val cap = when (skill) {
                Skill.FARMING -> farmingCap(member)
                Skill.FORAGING -> foragingCap(member)
                Skill.TAMING -> tamingCap(member)
                else -> ceiling
            }
            val xp = skillXp(member, id)
            val level = SkyblockMath.skillLevel(xp, cap)
            SkillStat(id, skill, xp, level, SkyblockMath.skillProgress(xp, cap), cap, maxed = level >= ceiling)
        }
    }

    private fun slayers(member: JsonObject): List<SlayerStat> {
        val bosses = member.child("slayer")?.child("slayer_bosses") ?: member.child("slayer_bosses")
        val known = Slayer.entries.map(Slayer::id)
        val ids = known + (bosses?.keySet().orEmpty().map(String::lowercase) - known.toSet())
            .filter(UNKNOWN_ID::matches).sorted()
        return ids.map { id ->
            val boss = bosses.child(id)
            val xp = boss.double("xp")
            val vampire = Slayer.of(id) == Slayer.VAMPIRE
            SlayerStat(
                id = id,
                slayer = Slayer.of(id),
                xp = xp,
                level = SkyblockMath.slayerLevel(xp, vampire),
                progress = SkyblockMath.slayerProgress(xp, vampire),
                cap = SkyblockMath.slayerCap(vampire),
                // `boss_kills_tier_0..4`. Missing tiers read as 0.
                kills = (0 until SLAYER_TIERS).map { boss.int("boss_kills_tier_$it") },
            )
        }
    }

    private fun dungeons(member: JsonObject): DungeonStats {
        val dungeons = member.child("dungeons")
        val types = dungeons.child("dungeon_types")
        val catacombs = types.child("catacombs")
        val cataXp = catacombs.double("experience")
        val selectedClass = dungeons.str("selected_dungeon_class")
        val legacyStats = dungeons.child("stats")
        return DungeonStats(
            catacombsXp = cataXp,
            catacombsLevel = SkyblockMath.catacombsLevel(cataXp),
            catacombsProgress = SkyblockMath.catacombsProgress(cataXp),
            classes = CLASSES.map { (key, dungeonClass) ->
                val xp = dungeons.child("player_classes").child(key).double("experience")
                DungeonClassStat(
                    dungeonClass = dungeonClass,
                    xp = xp,
                    level = SkyblockMath.catacombsLevel(xp),
                    progress = SkyblockMath.catacombsProgress(xp),
                    selected = key == selectedClass,
                )
            },
            secretsFound = firstPositive(
                dungeons.int("secrets"),
                member.child("player_stats").int("secrets_found"),
                member.child("achievements").int("skyblock_treasure_hunter"),
                legacyStats.child("secrets").int("found"),
            ),
            bloodMobKills = firstPositive(
                member.child("player_stats").child("kills").int("watcher_summon_undead"),
                member.child("player_stats").int("kills_watcher_summon_undead"),
                catacombs.int("blood_mob_kills"),
                legacyStats.int("bloodMobKills"),
            ),
            floors = floors(catacombs, master = false) + floors(types.child("master_catacombs"), master = true),
            bestRuns = bestRuns(types),
        )
    }

    /** Only completed floors, because score/time maps can hold stale keys. */
    private fun floors(type: JsonObject?, master: Boolean): List<FloorStats> {
        val fields = listOf("tier_completions", "best_score", "fastest_time_s", "fastest_time_s_plus")
        val tiers = fields.flatMap { type.child(it)?.keySet().orEmpty() }
            .mapNotNull(String::toIntOrNull)
            .distinct()
            .sortedDescending()
        return tiers.mapNotNull { tier ->
            val floor = floor(tier, master) ?: return@mapNotNull null
            val key = tier.toString()
            val runs = type.child("tier_completions").int(key).coerceAtLeast(0)
            if (runs == 0) return@mapNotNull null
            FloorStats(
                floor = floor,
                runs = runs,
                bestScore = type.child("best_score").int(key).coerceAtLeast(0),
                fastestS = type.child("fastest_time_s").long(key).takeIf { it > 0L },
                fastestSPlus = type.child("fastest_time_s_plus").long(key).takeIf { it > 0L },
            )
        }
    }

    private fun floor(tier: Int, master: Boolean): DungeonFloor? =
        DungeonFloor.entries.firstOrNull { it.number == tier && it.isMasterMode == master }

    private fun firstPositive(vararg values: Int): Int = values.firstOrNull { it > 0 } ?: 0

    private fun bestiary(member: JsonObject): List<BestiaryMobStat> {
        val data = member.child("bestiary")
        val kills = bestiaryCounts(data.child("kills"))
        val deaths = bestiaryCounts(data.child("deaths"))
        return (kills.keys + deaths.keys).sorted().map { id ->
            BestiaryMobStat(id, kills[id] ?: 0L, deaths[id] ?: 0L)
        }
    }

    private fun bestiaryCounts(counters: JsonObject?): Map<String, Long> = counters?.entrySet()
        ?.mapNotNull { (id, value) ->
            val count = value.takeIf { it.isJsonPrimitive }?.runCatching { asLong }?.getOrNull()
            count?.takeIf { it > 0L }?.let { id to it }
        }
        ?.toMap()
        .orEmpty()

    private fun kuudraRuns(member: JsonObject): Map<KuudraTier, Int> {
        val tiers = member.child("nether_island_player_data").child("kuudra_completed_tiers") ?: return emptyMap()
        return KuudraTier.entries
            .associateWith { tiers.int(it.id) }
            .filterValues { it > 0 }
    }

    private fun parseNetworth(nw: JsonObject?): Networth? {
        nw ?: return null
        val categories = nw.child("categories")?.entrySet()
            ?.associate { (name, value) -> name to (value.takeIf { it.isJsonPrimitive }?.asDouble ?: 0.0) }
            ?: emptyMap()
        val unavailable = nw.get("unavailable")?.takeIf { it.isJsonArray }?.asJsonArray
            ?.mapNotNull { it.takeIf { e -> e.isJsonPrimitive }?.asString }?.toSet()
            ?: emptySet()
        return Networth(
            total = nw.double("total"),
            purse = nw.double("purse"),
            bank = nw.double("bank"),
            categories = categories,
            unavailable = unavailable,
        )
    }

    /** Worked out from which fields exist, without decoding a single blob. */
    private fun inventoryPages(member: JsonObject): Set<InventoryPage> {
        val inventory = member.child("inventory")
        fun has(key: String) = inventory.child(key).str(BLOB_KEY) != null
        fun hasBag(key: String) = inventory.child(BAGS).child(key).str(BLOB_KEY) != null
        return buildSet {
            if (hasBag(ACCESSORY_BAG)) add(InventoryPage.ACCESSORIES)
            if (hasBag(POTION_BAG)) add(InventoryPage.POTIONS)
            if (hasBag(QUIVER)) add(InventoryPage.QUIVER)
            if (hasBag(FISHING_BAG)) add(InventoryPage.FISHING)
            if (MAIN_CONTAINERS.any(::has)) add(InventoryPage.INVENTORY)
            if (has("ender_chest_contents")) add(InventoryPage.ENDER_CHEST)
            if (has("personal_vault_contents")) add(InventoryPage.VAULT)
            if (inventory.child("backpack_contents") != null) add(InventoryPage.BACKPACKS)
            if (member.child("loadout").child("armor") != null) add(InventoryPage.WARDROBE)
            if (petArray(member) != null) add(InventoryPage.PETS)
        }
    }

    private fun petArray(member: JsonObject) =
        member.child("pets_data")?.get("pets")?.takeIf { it.isJsonArray }?.asJsonArray
            ?: member.get("pets")?.takeIf { it.isJsonArray }?.asJsonArray

    private fun pets(member: JsonObject): List<ProfilePet> = petArray(member)?.mapNotNull { element ->
        val pet = element.takeIf { it.isJsonObject }?.asJsonObject ?: return@mapNotNull null
        val type = pet.str("type")?.takeIf(String::isNotBlank) ?: return@mapNotNull null
        ProfilePet(
            type = type,
            tier = pet.str("tier") ?: "",
            experience = pet.double("exp"),
            active = pet.bool("active"),
            heldItem = pet.str("heldItem"),
            candyUsed = pet.int("candyUsed").coerceAtLeast(0),
            skin = pet.str("skin"),
        )
    }.orEmpty()

    /** The slow part, kept out of the lookup: the platform runs every stack through the data fixers. Blocking. */
    internal fun inventory(member: JsonObject): ProfileInventory {
        val decoded = decodeAll(member)
        val decode = { blob: String -> decoded[blob].orEmpty() }
        val inventory = member.child("inventory")
        fun container(key: String): List<ProfileItem?> = inventory.child(key).str(BLOB_KEY)?.let(decode).orEmpty()
        fun bag(key: String): List<ProfileItem?> = inventory.child(BAGS).child(key).str(BLOB_KEY)?.let(decode).orEmpty()

        val enderChest = container("ender_chest_contents")
            .chunked(STORAGE_PAGE_SIZE)
            .filter { slots -> slots.any { it != null } }
            // Normal profiles have no page-icon map. The Rift field is a different container.
            .mapIndexed { index, slots -> InventoryStorage(index + 1, slots, ENDER_CHEST_ICON) }
        return ProfileInventory(
            main = container("inv_contents"),
            armor = container("inv_armor"),
            equipment = container("equippment_contents").ifEmpty { container("equipment_contents") },
            enderChest = enderChest,
            wardrobe = wardrobe(member, decode),
            personalVault = container("personal_vault_contents"),
            backpacks = backpacks(inventory, decode),
            accessoryBag = bag(ACCESSORY_BAG),
            potionBag = bag(POTION_BAG),
            quiver = bag(QUIVER),
            fishingBag = bag(FISHING_BAG),
        )
    }

    /**
     * Decodes every blob at once, because each one costs tens of ms in the data fixers and a member has up to about 90.
     */
    private fun decodeAll(member: JsonObject): Map<String, List<ProfileItem?>> {
        val blobs = buildSet {
            collectBlobs(member.child("inventory"), this)
            collectBlobs(member.child("loadout"), this)
        }
        val pending = blobs.associateWith { blob -> ProfileWorkers.submit { decodeInventory(blob) } }
        return pending.mapValues { (_, task) -> runCatching { task.get() }.getOrDefault(emptyList()) }
    }

    /** Hypixel wraps every inventory blob as `{ "type": 0, "data": "<base64>" }`, at any depth. */
    private fun collectBlobs(node: JsonElement?, into: MutableSet<String>) {
        when {
            node == null -> Unit
            node.isJsonArray -> node.asJsonArray.forEach { collectBlobs(it, into) }
            node.isJsonObject -> node.asJsonObject.entrySet().forEach { (key, value) ->
                if (key == BLOB_KEY && value.isJsonPrimitive) into += value.asString else collectBlobs(value, into)
            }
        }
    }

    private fun decodeInventory(blob: String): List<ProfileItem?> =
        runCatching { Platform.items.decodeLegacyInventory(blob) }.getOrDefault(emptyList()).map { it?.let(::ProfileItem) }

    /** Ender chest and backpacks are page maps. Wardrobe sets are under `loadout`. */
    private fun backpacks(inventory: JsonObject?, decode: (String) -> List<ProfileItem?>): List<InventoryStorage> {
        val icons = inventory.child("backpack_icons")
        return inventory.child("backpack_contents")?.entrySet()
            ?.mapNotNull { (key, value) ->
                val index = key.toIntOrNull() ?: return@mapNotNull null
                val contents = value.takeIf { it.isJsonObject }?.asJsonObject ?: return@mapNotNull null
                val slots = contents.str(BLOB_KEY)?.let(decode) ?: return@mapNotNull null
                val icon = icons.child(key).str(BLOB_KEY)?.let(decode)?.firstOrNull()
                InventoryStorage(index + 1, slots, icon).takeIf { it.slots.any { slot -> slot != null } }
            }
            ?.sortedBy(InventoryStorage::index)
            .orEmpty()
    }

    private fun wardrobe(member: JsonObject, decode: (String) -> List<ProfileItem?>): List<InventoryStorage> {
        val armorSets = member.child("loadout").child("armor") ?: return emptyList()
        val sets = armorSets.entrySet()
            .mapNotNull { (key, value) ->
                val index = key.toIntOrNull() ?: return@mapNotNull null
                val set = value.takeIf { it.isJsonObject }?.asJsonObject ?: return@mapNotNull null
                val slots = ARMOR_SLOTS.map { slot -> set.child(slot).str(BLOB_KEY)?.let(decode)?.firstOrNull() }
                InventoryStorage(index, slots).takeIf { it.slots.any { slot -> slot != null } }
            }
            .sortedBy(InventoryStorage::index)
        return sets.chunked(WARDROBE_SETS_PER_PAGE).mapIndexed { page, rows ->
            InventoryStorage(page + 1, ARMOR_SLOTS.indices.flatMap { row ->
                rows.map { it.slots[row] } + List(WARDROBE_SETS_PER_PAGE - rows.size) { null }
            })
        }
    }

    /** Only treasure history has stable run ids and participant UUIDs. Parse display text here, never in a screen. */
    internal fun recentActivities(member: JsonObject): List<RecentActivity> {
        val treasures = member.child("dungeons")?.get("treasures") ?: return emptyList()
        val runs = when {
            treasures.isJsonArray -> treasures.asJsonArray
            treasures.isJsonObject -> treasures.asJsonObject.get("runs")?.takeIf { it.isJsonArray }?.asJsonArray
            else -> null
        } ?: return emptyList()

        return runs.mapNotNull { element ->
            val run = element.takeIf { it.isJsonObject }?.asJsonObject ?: return@mapNotNull null
            val id = run.str("run_id") ?: return@mapNotNull null
            val completedAt = run.long("completion_ts")
            if (completedAt <= 0) return@mapNotNull null
            val kuudra = run.str("type").equals("KUUDRA", ignoreCase = true)
            val activity = if (kuudra) (KuudraTier.of(run.str("tier_id")) ?: KuudraTier.BASIC).displayName else dungeonFloor(run)
            RecentActivity(
                id = id,
                completedAt = completedAt,
                activity = activity,
                kuudra = kuudra,
                participants = run.get("participants")?.takeIf { it.isJsonArray }?.asJsonArray
                    ?.mapNotNull(::participant)
                    ?: emptyList(),
            )
        }
    }

    private fun participant(element: JsonElement): RecentParticipant? {
        val raw = element.takeIf { it.isJsonObject }?.asJsonObject ?: return null
        val uuid = raw.str("player_uuid") ?: return null
        val text = raw.str("display_name")?.stripFormatting()?.trim().orEmpty()
        val name = text.substringBefore(':').trim().substringAfterLast(' ')
            .takeIf { it.matches(IGN) }
        val role = text.substringAfter(':', missingDelimiterValue = "").trim().ifEmpty { null }
        return RecentParticipant(uuid, name, role)
    }

    internal fun bestRuns(dungeonTypes: JsonObject?): List<BestRun> = buildList {
        for ((key, master) in listOf("catacombs" to false, "master_catacombs" to true)) {
            val floors = dungeonTypes.child(key).child("best_runs") ?: continue
            for ((floorKey, value) in floors.entrySet()) {
                val floor = floorKey.toIntOrNull()?.let { floor(it, master) } ?: continue
                val runs = value.takeIf { it.isJsonArray }?.asJsonArray ?: continue
                for (element in runs) {
                    val run = element.takeIf { it.isJsonObject }?.asJsonObject ?: continue
                    val completedAt = run.long("timestamp")
                    val elapsed = run.long("elapsed_time")
                    if (completedAt <= 0 || elapsed <= 0) continue
                    val teammates = run.get("teammates")?.takeIf { it.isJsonArray }?.asJsonArray
                        ?.mapNotNull { it.takeIf { e -> e.isJsonPrimitive }?.asString?.let(Uuids::parse) }
                        ?.toSet()
                        ?: emptySet()
                    add(BestRun(floor, completedAt, elapsed, teammates))
                }
            }
        }
    }

    private fun dungeonFloor(run: JsonObject): String {
        val tier = run.int("dungeon_tier")
        val master = run.str("dungeon_type")?.contains("master", ignoreCase = true) == true
        return if (tier == 0 && !master) "Entrance" else if (master) "M$tier" else "F$tier"
    }

    /** Current `player_data.experience.SKILL_X`, or legacy `experience_skill_x`. */
    private fun skillXp(member: JsonObject, id: String): Double {
        val current = member.child("player_data").child("experience").double("$SKILL_XP_PREFIX${id.uppercase()}")
        if (current > 0.0) return current
        return member.double("$LEGACY_SKILL_XP_PREFIX$id")
    }

    /** 50 + Jacob's `farming_level_cap` perk (0..10). */
    private fun farmingCap(member: JsonObject): Int =
        50 + member.child("jacobs_contest").child("perks").int("farming_level_cap")

    /** 54 plus the extra levels unlocked so far (`SKILL_FORAGING_extra_level_cap`, 0..3), which sits next to the XP. */
    private fun foragingCap(member: JsonObject): Int =
        54 + member.child("player_data").child("experience").int("${SKILL_XP_PREFIX}FORAGING_extra_level_cap")

    /** 50 + one per unique pet sacrificed (max 60). */
    private fun tamingCap(member: JsonObject): Int {
        val sacrificed = member.child("pets_data").child("pet_care")
            ?.get("pet_types_sacrificed")?.takeIf { it.isJsonArray }?.asJsonArray
        return 50 + (sacrificed?.size() ?: 0)
    }

    private val IGN = Regex("[A-Za-z0-9_]{1,16}")
}
