package org.blackaddons.core.skyblock

import com.google.gson.JsonParser
import org.blackaddons.api.util.io.child
import org.blackaddons.api.util.io.int
import org.blackaddons.api.util.io.str
import org.blackaddons.core.backend.Backend
import org.blackaddons.core.backend.Http
import org.blackaddons.core.log.Log
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * Skyblock id (e.g. `TARANTULA_SILK`) -> item JSON for `Render.itemSpec`. Built like the server's stack (vanilla base,
 * `custom_data` id, `item_model`, head profile), so resource packs retexture it correctly. Data comes from Hypixel's
 * items resource through the proxy and stays unresolved until fetched. Off Hypixel, `item_model` gets dropped, so items
 * use vanilla textures.
 */
internal object SkyblockItems {

    // The anonymous route isn't deployed.
    private const val URL = "${Backend.HYPIXEL_URL}/v2/resources/skyblock/items"

    private class Entry(
        val material: String,
        val durability: Int,
        val itemModel: String?,
        val skin: String?,
        /** `"r,g,b"` leather dye, otherwise null. */
        val color: String?,
        val name: String?,
        /** Rarity, e.g. `LEGENDARY`. */
        val tier: String?,
    )

    @Volatile private var items: Map<String, Entry>? = null

    private val specCache = ConcurrentHashMap<String, String>()

    /**
     * Non-blocking and safe while rendering. `namespace:path` ids resolve right away, Skyblock ids wait for the fetch.
     * [components] is a raw component body for vanilla items that need one (e.g. a potion's `potion_contents`). Ignored
     * for Skyblock ids.
     */
    fun spec(id: String, components: String? = null): String? {
        val key = if (components == null) id else "$id|$components"
        specCache[key]?.let { return it }
        // Skyblock ids can hold a colon too (WOOD:4), but never lowercase.
        if (NAMESPACED.matches(id)) {
            val body = components?.let { ""","components":{$it}""" } ?: ""
            return """{"id":"$id","count":1$body}""".also { specCache[key] = it }
        }
        val entry = items?.get(id) ?: return null
        return build(id, entry).also { specCache[key] = it }
    }

    fun skinTexture(id: String): String? = items?.get(id)?.skin

    // value is the base64 "textures" property.
    fun headSpec(value: String): String = """{"id":"minecraft:player_head","count":1,"components":{${headProfile(value)}}}"""

    /** The item's display name, e.g. `Tier Boost`, or null until the table is in. */
    fun name(id: String): String? = items?.get(id)?.name

    /** The item's rarity, e.g. `LEGENDARY`, or null until the table is in. */
    fun tier(id: String): String? = items?.get(id)?.tier

    /** Every Skyblock id, or null until the table is in. */
    fun ids(): Set<String>? = items?.keys

    /** Once. Blocking. A failure leaves it null, so ids stay unresolved instead of wrong. */
    fun prefetch() {
        if (items != null) return
        val response = Http.get(URL, authenticated = true) ?: return
        if (response.status != 200) {
            Log.main.warn("Skyblock item table unavailable: backend status {}", response.status)
            return
        }
        val table = runCatching {
            val array = JsonParser.parseString(response.body).asJsonObject.getAsJsonArray("items") ?: return
            buildMap {
                for (element in array) {
                    val item = element.takeIf { it.isJsonObject }?.asJsonObject ?: continue
                    val id = item.str("id") ?: continue
                    put(
                        id,
                        Entry(
                            material = item.str("material") ?: "PAPER",
                            durability = item.int("durability"),
                            itemModel = item.str("item_model"),
                            skin = item.child("skin").str("value"),
                            color = item.str("color"),
                            name = item.str("name"),
                            tier = item.str("tier"),
                        ),
                    )
                }
            }
        }.onFailure { Log.main.warn("Skyblock item table unreadable", it) }.getOrNull() ?: return
        if (table.isNotEmpty()) items = table
    }

    private fun build(id: String, entry: Entry): String {
        val customData = """"minecraft:custom_data":{"id":"$id"}"""
        entry.skin?.let { return """{"id":"minecraft:player_head","count":1,"components":{${headProfile(it)},$customData}}""" }
        val model = entry.itemModel?.let { ""","minecraft:item_model":"$it"""" } ?: ""
        // Leather armor needs its dye, or it shows the wrong color.
        val dye = entry.color?.let { rgb(it) }?.let { ""","minecraft:dyed_color":$it""" } ?: ""
        return """{"id":"${material(entry.material, entry.durability)}","count":1,"components":{$customData$model$dye}}"""
    }

    private fun rgb(color: String): Int? {
        val parts = color.split(',')
        if (parts.size != 3) return null
        val channels = parts.map { it.trim().toIntOrNull() ?: return null }
        if (channels.any { it !in 0..255 }) return null
        return (channels[0] shl 16) or (channels[1] shl 8) or channels[2]
    }

    /**
     * UUID made from the skin, so the skull cache doesn't merge heads. Four-int array form. No signature, it only
     * matters for server auth.
     */
    private fun headProfile(value: String): String {
        val uuid = UUID.nameUUIDFromBytes(value.toByteArray())
        val hi = uuid.mostSignificantBits
        val lo = uuid.leastSignificantBits
        val id = "${(hi shr 32).toInt()},${hi.toInt()},${(lo shr 32).toInt()},${lo.toInt()}"
        return """"minecraft:profile":{"id":[$id],"properties":[{"name":"textures","value":"$value"}]}"""
    }

    private val NAMESPACED = Regex("[a-z0-9_.-]+:[a-z0-9_./-]+")

    private fun material(name: String, durability: Int): String {
        val keyed = if (durability != 0) "$name:$durability" else name
        (LEGACY[keyed] ?: LEGACY[name])?.let { return "minecraft:$it" }
        return "minecraft:${name.lowercase()}"
    }

    // Only renamed or flattened names need entries. Colored variants only map their base value for now.
	// Cancer, hypixel should refactor their stuff, not my problem.
    private val LEGACY = mapOf(
        "SULPHUR" to "gunpowder",
        "RAW_FISH" to "cod", "RAW_FISH:1" to "salmon", "RAW_FISH:2" to "tropical_fish", "RAW_FISH:3" to "pufferfish",
        "COOKED_FISH" to "cooked_cod", "COOKED_FISH:1" to "cooked_salmon",
        "SKULL_ITEM" to "player_head", "SKULL_ITEM:3" to "player_head",
        "INK_SACK" to "ink_sac", "INK_SACK:3" to "cocoa_beans", "INK_SACK:4" to "lapis_lazuli",
        "NETHER_STALK" to "nether_wart", "SEEDS" to "wheat_seeds", "POTATO_ITEM" to "potato", "CARROT_ITEM" to "carrot",
        "MELON" to "melon_slice", "MELON_BLOCK" to "melon", "SPECKLED_MELON" to "glistering_melon_slice",
        "WATCH" to "clock", "EXP_BOTTLE" to "experience_bottle", "EYE_OF_ENDER" to "ender_eye",
        "FIREWORK" to "firework_rocket", "FIREBALL" to "fire_charge",
        "BOOK_AND_QUILL" to "writable_book", "EMPTY_MAP" to "map", "MAP" to "filled_map",
        "RAW_BEEF" to "beef", "PORK" to "porkchop", "GRILLED_PORK" to "cooked_porkchop", "RAW_CHICKEN" to "chicken",
        "WORKBENCH" to "crafting_table", "ENCHANTMENT_TABLE" to "enchanting_table", "BREWING_STAND_ITEM" to "brewing_stand",
        "CAULDRON_ITEM" to "cauldron", "FLOWER_POT_ITEM" to "flower_pot",
        "THIN_GLASS" to "glass_pane", "IRON_FENCE" to "iron_bars", "NETHER_FENCE" to "nether_brick_fence",
        "STEP" to "stone_slab", "WOOD_STEP" to "oak_slab", "SLIME_BALL" to "slime_ball",
        "STAINED_CLAY" to "terracotta", "HARD_CLAY" to "terracotta",
        "COAL:1" to "charcoal", "REDSTONE_ORE" to "redstone_ore", "GLOWING_REDSTONE_ORE" to "redstone_ore",
        "LOG" to "oak_log", "LOG_2" to "acacia_log", "WOOD" to "oak_planks",
        "SAPLING" to "oak_sapling", "LEAVES" to "oak_leaves", "LEAVES_2" to "acacia_leaves",
        "WOOL" to "white_wool", "CARPET" to "white_carpet",
        "STAINED_GLASS" to "white_stained_glass", "STAINED_GLASS_PANE" to "white_stained_glass_pane",
        "DOUBLE_PLANT" to "sunflower", "RED_ROSE" to "poppy", "YELLOW_FLOWER" to "dandelion", "LONG_GRASS" to "short_grass",
        "SUGAR_CANE_BLOCK" to "sugar_cane",
        "GOLD_SWORD" to "golden_sword", "WOOD_SWORD" to "wooden_sword", "GOLD_PICKAXE" to "golden_pickaxe",
        "WOOD_PICKAXE" to "wooden_pickaxe", "GOLD_AXE" to "golden_axe", "WOOD_AXE" to "wooden_axe",
        "GOLD_HOE" to "golden_hoe", "WOOD_HOE" to "wooden_hoe", "WOOD_SPADE" to "wooden_shovel",
        "GOLD_SPADE" to "golden_shovel", "STONE_SPADE" to "stone_shovel", "IRON_SPADE" to "iron_shovel",
        "DIAMOND_SPADE" to "diamond_shovel", "GOLD_HELMET" to "golden_helmet", "GOLD_CHESTPLATE" to "golden_chestplate",
        "GOLD_LEGGINGS" to "golden_leggings", "GOLD_BOOTS" to "golden_boots", "GOLD_BARDING" to "golden_horse_armor",
        "IRON_BARDING" to "iron_horse_armor", "DIAMOND_BARDING" to "diamond_horse_armor",
        "GOLD_PLATE" to "light_weighted_pressure_plate", "IRON_PLATE" to "heavy_weighted_pressure_plate",
        "STONE_PLATE" to "stone_pressure_plate", "WOOD_PLATE" to "oak_pressure_plate", "GOLD_RECORD" to "music_disc_13",
        "GREEN_RECORD" to "music_disc_cat", "RECORD_3" to "music_disc_blocks", "RECORD_4" to "music_disc_chirp",
        "RECORD_5" to "music_disc_far", "RECORD_6" to "music_disc_mall", "RECORD_7" to "music_disc_mellohi",
        "RECORD_8" to "music_disc_stal", "RECORD_9" to "music_disc_strad", "RECORD_10" to "music_disc_ward",
        "RECORD_11" to "music_disc_11", "RECORD_12" to "music_disc_wait", "LEASH" to "lead",
        "CARROT_STICK" to "carrot_on_a_stick", "MUSHROOM_SOUP" to "mushroom_stew", "SNOW_BALL" to "snowball",
        "WEB" to "cobweb", "HUGE_MUSHROOM_1" to "brown_mushroom_block", "HUGE_MUSHROOM_2" to "red_mushroom_block",
        "ENDER_STONE" to "end_stone", "MYCEL" to "mycelium", "NETHER_BRICK_ITEM" to "nether_brick",
        "NETHER_BRICK" to "nether_bricks", "QUARTZ_ORE" to "nether_quartz_ore", "CLAY_BRICK" to "brick",
        "BRICK" to "bricks", "SMOOTH_BRICK" to "stone_bricks", "SMOOTH_STAIRS" to "stone_brick_stairs",
        "WOOD_STAIRS" to "oak_stairs", "SPRUCE_WOOD_STAIRS" to "spruce_stairs", "BIRCH_WOOD_STAIRS" to "birch_stairs",
        "JUNGLE_WOOD_STAIRS" to "jungle_stairs", "FIREWORK_CHARGE" to "firework_star",
        "ENDER_PORTAL_FRAME" to "end_portal_frame", "REDSTONE_TORCH_ON" to "redstone_torch", "DIODE" to "repeater",
        "REDSTONE_COMPARATOR" to "comparator", "PISTON_BASE" to "piston", "PISTON_STICKY_BASE" to "sticky_piston",
        "TRAP_DOOR" to "oak_trapdoor", "FENCE" to "oak_fence", "FENCE_GATE" to "oak_fence_gate",
        "WOOD_DOOR" to "oak_door", "WOOD_BUTTON" to "oak_button", "BED" to "red_bed", "SIGN" to "oak_sign",
        "BOAT" to "oak_boat", "STORAGE_MINECART" to "chest_minecart", "POWERED_MINECART" to "furnace_minecart",
        "EXPLOSIVE_MINECART" to "tnt_minecart", "COMMAND" to "command_block", "MOB_SPAWNER" to "spawner",
        "WATER_LILY" to "lily_pad", "BANNER" to "white_banner", "RAILS" to "rail", "GRASS" to "grass_block",
        "BURNING_FURNACE" to "furnace", "GOLDEN_APPLE:1" to "enchanted_golden_apple", "SKULL" to "player_head",
        "ACACIA_DOOR_ITEM" to "acacia_door", "BIRCH_DOOR_ITEM" to "birch_door", "DARK_OAK_DOOR_ITEM" to "dark_oak_door",
        "JUNGLE_DOOR_ITEM" to "jungle_door", "SPRUCE_DOOR_ITEM" to "spruce_door", "REDSTONE_LAMP_OFF" to "redstone_lamp",
        "COBBLE_WALL" to "cobblestone_wall", "MONSTER_EGG" to "pig_spawn_egg",
    )
}
