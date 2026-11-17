package org.blackaddons.core.profile

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import org.blackaddons.api.skyblock.dungeon.DungeonClass
import org.blackaddons.api.skyblock.dungeon.DungeonFloor
import org.blackaddons.api.skyblock.profile.InventoryPage
import org.blackaddons.api.skyblock.profile.KuudraTier
import org.blackaddons.api.skyblock.profile.ProfileMode
import org.blackaddons.api.skyblock.profile.Skill
import org.blackaddons.api.skyblock.profile.Slayer
import org.blackaddons.api.util.Uuids
import org.blackaddons.core.gamedata.SkillCaps
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.UUID

/**
 * Pins [RawHypixelParser.parseMember]'s field paths (Hypixel keeps moving them), legacy fallbacks and skill caps per
 * profile. With no fetch, [SkillCaps] uses its hardcoded table.
 */
class RawHypixelParserTest {

    private val self = "243d3358d4754458b7548955c7564a28"
    private val coop = "f69ad8c2ba8f44e7a1e35126c565676f"
    private val pending = "0123456789abcdef0123456789abcdef"
    private val left = "fedcba9876543210fedcba9876543210"

    private fun obj(json: String): JsonObject = JsonParser.parseString(json).asJsonObject

    private fun member(json: String, networth: JsonObject? = null) =
        RawHypixelParser.parseMember(UUID.randomUUID(), obj(json), networth)

    @Test
    fun `parse returns Empty for missing or empty profiles`() {
        val id = MojangId(self, "Name")
        assertEquals(SourceResult.Empty, RawHypixelParser.parse(obj("{ }"), id))
        assertEquals(SourceResult.Empty, RawHypixelParser.parse(obj("""{ "profiles": [] }"""), id))
    }

    @Test
    fun `a profile carries the player in full and the confirmed coop members by name`() {
        val root = obj(
            """
            { "profiles": [{
                "profile_id": "p1", "cute_name": "Tomato", "game_mode": "island", "selected": true,
                "members": {
                  "$self": { "profile": { "coop_invitation": { "confirmed": true } } },
                  "$coop": { "profile": { } },
                  "$pending": { "profile": { "coop_invitation": { "confirmed": false } } },
                  "$left": { "profile": { "deletion_notice": { "timestamp": 1 } } }
                }
              }] }
            """.trimIndent(),
        )
        val ok = RawHypixelParser.parse(root, MojangId(self, "DreamcastERA20")) as SourceResult.Ok
        val profile = ok.profiles.profiles.single()

        assertEquals(Uuids.parse(self), ok.profiles.uuid)
        assertEquals("DreamcastERA20", ok.profiles.name)
        assertEquals(ProfileMode.STRANDED, profile.mode)
        assertEquals(Uuids.parse(self), profile.member.uuid)
        assertEquals(listOf(Uuids.parse(coop)), profile.coop.map { it.uuid })
        assertTrue(ok.recent.containsKey("p1"))
    }

    @Test
    fun `a coop member's name is whatever is known without a request, else null`() {
        val members = obj("""{ "$self": { }, "$coop": { } }""")
        val murz = Uuids.parse(coop)
        assertEquals("Murz474", RawHypixelParser.coop(members, self) { if (it == murz) "Murz474" else null }.single().name)
        assertNull(RawHypixelParser.coop(members, self) { null }.single().name)
    }

    @Test
    fun `skills come from player_data xp and the average counts only core skills`() {
        val stats = member("""{ "player_data": { "experience": { "SKILL_COMBAT": 175 } } }""")
        val combat = stats.skill(Skill.COMBAT)!!
        assertEquals(2, combat.level) // 175 XP is exactly level 2
        assertEquals(175.0, combat.xp)
        assertFalse(combat.maxed)
        assertTrue(combat.countsTowardAverage)
        // Eight skills count (carpentry, runecrafting, social, hunting excluded): 2 / 8.
        assertEquals(0.25, stats.skillAverage, 1e-9)
    }

    @Test
    fun `a skill Hypixel added after this build is kept, not dropped or thrown on`() {
        val stats = member("""{ "player_data": { "experience": { "SKILL_SAILING": 175 } } }""")
        val sailing = stats.skills.single { it.id == "sailing" }
        assertNull(sailing.skill)
        assertEquals("Sailing", sailing.name)
        assertEquals(2, sailing.level)
        assertFalse(sailing.countsTowardAverage)
        assertEquals(Skill.entries.size + 1, stats.skills.size)
    }

    @Test
    fun `perk counters beside the skill xp are not skills`() {
        val stats = member(
            """{ "player_data": { "experience": { "SKILL_FORAGING": 175, "SKILL_FORAGING_extra_level_cap": 3 } } }"""
        )
        assertEquals(Skill.entries.map(Skill::id), stats.skills.map { it.id })
    }

    @Test
    fun `foraging's cap rises with the extra levels unlocked`() {
        val xp = """"SKILL_FORAGING": 999999999"""
        val full = member("""{ "player_data": { "experience": { $xp, "SKILL_FORAGING_extra_level_cap": 3 } } }""")
            .skill(Skill.FORAGING)!!
        assertEquals(57, full.cap)
        assertTrue(full.maxed)

        val none = member("""{ "player_data": { "experience": { $xp } } }""").skill(Skill.FORAGING)!!
        assertEquals(54, none.cap)
        assertEquals(54, none.level)
        assertFalse(none.maxed)
    }

    @Test
    fun `a skill at its absolute ceiling is maxed`() {
        val mining = member("""{ "player_data": { "experience": { "SKILL_MINING": 999999999 } } }""")
            .skill(Skill.MINING)!!
        assertEquals(SkillCaps.COMPILED.getValue("mining"), mining.level)
        assertTrue(mining.maxed)
    }

    @Test
    fun `skill xp falls back to the legacy flat field`() {
        assertEquals(2, member("""{ "experience_skill_mining": 175 }""").skill(Skill.MINING)!!.level)
    }

    @Test
    fun `farming's cap floats up with Jacob's perk`() {
        val xp = """"SKILL_FARMING": 999999999"""
        val withPerk = member("""{ "player_data": { "experience": { $xp } }, "jacobs_contest": { "perks": { "farming_level_cap": 10 } } }""")
            .skill(Skill.FARMING)!!
        assertEquals(60, withPerk.level)
        assertEquals(60, withPerk.cap)
        assertTrue(withPerk.maxed)

        val noPerk = member("""{ "player_data": { "experience": { $xp } } }""").skill(Skill.FARMING)!!
        assertEquals(50, noPerk.level) // base cap 50 without the perk
        assertEquals(50, noPerk.cap)
        assertFalse(noPerk.maxed) // absolute ceiling is 60
    }

    @Test
    fun `slayers use the standard table, vampire the short one, and kills read per tier`() {
        val stats = member(
            """
            { "slayer": { "slayer_bosses": {
                "zombie":  { "xp": 1000, "boss_kills_tier_0": 10, "boss_kills_tier_1": 5 },
                "vampire": { "xp": 2400 }
            } } }
            """.trimIndent()
        )
        val zombie = stats.slayer(Slayer.ZOMBIE)!!
        assertEquals("Zombie", zombie.name)
        assertEquals(4, zombie.level)
        assertEquals(9, zombie.cap)
        assertEquals(0.0, zombie.progress)
        assertEquals(listOf(10, 5, 0, 0, 0), zombie.kills)
        val vampire = stats.slayer(Slayer.VAMPIRE)!!
        assertEquals(5, vampire.level) // vampire caps at 5
        assertEquals(5, vampire.cap)
        assertEquals(1.0, vampire.progress)
    }

    @Test
    fun `bestiary combines kill and death maps without accepting invalid counters`() {
        val stats = member(
            """
            { "bestiary": {
                "kills": { "zombie_1": 12, "zombie_2": 3, "zero": 0, "ignored": -1 },
                "deaths": { "zombie_1": 2, "skeleton": 4, "bad": "not a number" }
            } }
            """.trimIndent(),
        )

        assertEquals(
            listOf(Triple("skeleton", 0L, 4L), Triple("zombie_1", 12L, 2L), Triple("zombie_2", 3L, 0L)),
            stats.bestiary.map { Triple(it.id, it.kills, it.deaths) },
        )
    }

    @Test
    fun `pages and pets are read without decoding, and a blob that cannot decode leaves an empty page`() {
        val json =
            """
            { "inventory": {
                "inv_contents": { "data": "not-nbt" },
                "ender_chest_contents": { "data": "not-nbt" },
                "personal_vault_contents": { "data": "not-nbt" },
                "backpack_contents": { "4": { "data": "not-nbt" } },
                "backpack_icons": { "4": { "data": "not-nbt" } },
                "bag_contents": {
                  "talisman_bag": { "data": "not-nbt" },
                  "potion_bag": { "data": "not-nbt" },
                  "quiver": { "data": "not-nbt" },
                  "fishing_bag": { "data": "not-nbt" }
                }
              },
              "loadout": { "armor": {
                "1": {
                  "HELMET": { "data": "not-nbt" },
                  "CHESTPLATE": { "data": "not-nbt" },
                  "LEGGINGS": { "data": "not-nbt" },
                  "BOOTS": { "data": "not-nbt" }
                }
              } },
              "pets_data": { "pets": [{
                "type": "SHEEP", "tier": "LEGENDARY", "exp": 123.0, "active": true,
                "heldItem": "PET_ITEM_LUCKY_CLOVER", "candyUsed": 2, "skin": "PET_SKIN_SHEEP_WHITE"
              }] }
            }
            """.trimIndent()
        val stats = member(json)

        assertEquals(InventoryPage.entries.toSet(), stats.inventoryPages)
        val pet = stats.pets.single()
        assertEquals(
            listOf("SHEEP", "LEGENDARY", 123.0, true, "PET_ITEM_LUCKY_CLOVER", 2, "PET_SKIN_SHEEP_WHITE"),
            listOf(pet.type, pet.tier, pet.experience, pet.active, pet.heldItem, pet.candyUsed, pet.skin),
        )

        // No platform in a unit test, so every blob fails to decode.
        val inventory = RawHypixelParser.inventory(obj(json))
        assertTrue(inventory.main.isEmpty())
        assertTrue(inventory.backpacks.isEmpty())
        assertTrue(inventory.wardrobe.isEmpty())
    }

    @Test
    fun `legacy top-level pets remain readable`() {
        val stats = member("""{ "pets": [{ "type": "ROCK", "tier": "RARE", "exp": 42 }] }""")

        assertEquals(setOf(InventoryPage.PETS), stats.inventoryPages)
        val pet = stats.pets.single()
        assertEquals(listOf("ROCK", "RARE", 42.0, false), listOf(pet.type, pet.tier, pet.experience, pet.active))
    }

    @Test
    fun `catacombs level and the selected dungeon class are read`() {
        val dungeons = member(
            """
            { "dungeons": {
                "selected_dungeon_class": "healer",
                "dungeon_types": { "catacombs": { "experience": 4385 } },
                "player_classes": { "healer": { "experience": 50 } }
            } }
            """.trimIndent()
        ).dungeons
        assertEquals(4385.0, dungeons.catacombsXp)
        assertEquals(10, dungeons.catacombsLevel)
        assertEquals(DungeonClass.HEALER, dungeons.selectedClass)
        val healer = dungeons.classes.single { it.dungeonClass == DungeonClass.HEALER }
        assertEquals(50.0, healer.xp)
        assertEquals(1, healer.level)
        assertTrue(healer.selected)
        val mage = dungeons.classes.single { it.dungeonClass == DungeonClass.MAGE }
        assertEquals(0.0, mage.xp)
        assertFalse(mage.selected)
        assertEquals(5, dungeons.classes.size)
    }

    @Test
    fun `dungeon stats retain all completed-floor fields and current counter paths`() {
        val dungeons = member(
            """
            { "player_stats": { "kills": { "watcher_summon_undead": 75 } },
              "dungeons": { "dungeon_types": {
                "catacombs": {
                  "tier_completions": { "0": 4, "7": 12, "3": 0 },
                  "best_score": { "0": 290, "7": 311 },
                  "fastest_time_s": { "0": 12345, "7": 98765 },
                  "fastest_time_s_plus": { "0": 12000, "7": 90000, "3": 95000 }
                },
                "master_catacombs": {
                  "tier_completions": { "3": 6 },
                  "best_score": { "3": 302 },
                  "fastest_time_s": { "3": 22345 },
                  "fastest_time_s_plus": { "3": 21000 }
                }
              }, "secrets": 1200 }
            }
            """.trimIndent(),
        ).dungeons

        assertEquals(1200, dungeons.secretsFound)
        assertEquals(75, dungeons.bloodMobKills)
        assertEquals(
            listOf(
                listOf(DungeonFloor.F7, 12, 311, 98765L, 90000L),
                listOf(DungeonFloor.ENTRANCE, 4, 290, 12345L, 12000L),
                listOf(DungeonFloor.M3, 6, 302, 22345L, 21000L),
            ),
            dungeons.floors.map { listOf(it.floor, it.runs, it.bestScore, it.fastestS, it.fastestSPlus) },
        )
        assertNull(dungeons.floor(DungeonFloor.F3)) // a time with no completion is stale
    }

    @Test
    fun `kuudra runs skip tiers never cleared`() {
        val stats = member(
            """{ "nether_island_player_data": { "kuudra_completed_tiers": { "none": 3, "hot": 0, "infernal": 2 } } }"""
        )
        assertEquals(mapOf(KuudraTier.BASIC to 3, KuudraTier.INFERNAL to 2), stats.kuudraRuns)
    }

    @Test
    fun `purse reads currencies then falls back to the legacy field`() {
        assertEquals(1234.5, member("""{ "currencies": { "coin_purse": 1234.5 } }""").purse)
        assertEquals(777.0, member("""{ "coin_purse": 777.0 }""").purse)
    }

    @Test
    fun `skyblock level and progress come from the leveling xp`() {
        val stats = member("""{ "leveling": { "experience": 12345 } }""")
        assertEquals(123, stats.skyblockLevel)
        assertEquals(0.45, stats.skyblockProgress, 1e-9)
    }

    @Test
    fun `networth is read from the backend block, and null when absent`() {
        val nw = obj("""{ "total": 100.0, "purse": 10.0, "bank": 20.0, "categories": { "weapons": 50.0 }, "unavailable": ["accessories"] }""")
        val networth = member("{ }", nw).networth!!
        assertEquals(100.0, networth.total)
        assertEquals(mapOf("weapons" to 50.0), networth.categories)
        assertEquals(setOf("accessories"), networth.unavailable)

        assertNull(member("{ }").networth)
    }

    @Test
    fun `the raw member object is kept for fields not modeled yet`() {
        assertEquals(42, member("""{ "garden_player_data": { "copper": 42 } }""").raw
            .getAsJsonObject("garden_player_data").get("copper").asInt)
    }

    @Test
    fun `recent activities keep UUID teammates and classify Dungeon and Kuudra tiers`() {
        val activities = RawHypixelParser.recentActivities(
            obj(
                """
                { "dungeons": { "treasures": { "runs": [
                    {
                        "run_id": "dungeon-run", "completion_ts": 1000, "type": "DUNGEON",
                        "dungeon_type": "master_catacombs", "dungeon_tier": 7,
                        "participants": [
                            { "player_uuid": "owner", "display_name": "Owner: Mage (50)" },
                            { "player_uuid": "friend", "display_name": "Friend_Name: Archer (42)" }
                        ]
                    },
                    {
                        "run_id": "kuudra-run", "completion_ts": 2000, "type": "KUUDRA", "tier_id": "INFERNAL",
                        "participants": [{ "player_uuid": "friend", "display_name": "Friend_Name: Specialist" }]
                    }
                ] } } }
                """.trimIndent(),
            ),
        )

        assertEquals(listOf("M7", "Infernal"), activities.map(RecentActivity::activity))
        assertEquals(listOf(false, true), activities.map(RecentActivity::kuudra))
        val teammate = activities.first().participants.single { it.uuid == "friend" }
        assertEquals("Friend_Name", teammate.name)
        assertEquals("Archer (42)", teammate.role)
    }

    @Test
    fun `recorded best runs retain durations by floor and teammate UUIDs`() {
        val runs = RawHypixelParser.bestRuns(
            obj(
                """
                { "catacombs": { "best_runs": { "7": [
                    { "timestamp": 1000, "elapsed_time": 123456, "teammates": ["$coop", "not-a-uuid"] }
                ] } }, "master_catacombs": { "best_runs": { "3": [
                    { "timestamp": 2000, "elapsed_time": 234567, "teammates": [] }
                ] } } }
                """.trimIndent(),
            ),
        )

        assertEquals(listOf(DungeonFloor.F7, DungeonFloor.M3), runs.map { it.floor })
        assertEquals(listOf(123456L, 234567L), runs.map { it.elapsedMillis })
        assertEquals(setOf(Uuids.parse(coop)), runs.first().teammates)
    }
}
