package org.blackaddons.core.ui.screen.pv.parts
import org.blackaddons.api.skyblock.profile.ProfilePet
import org.blackaddons.core.profile.pets.PetTextures
import org.blackaddons.core.skyblock.SkyblockItems
import java.nio.charset.StandardCharsets
import java.util.UUID

/** How a chip draws its icon: [Item] is an item JSON spec (resource packs retexture it), [Head] a head texture. */
internal sealed interface ChipIcon {
    class Item(val spec: String) : ChipIcon
    class Head(val uuid: String, val name: String, val texture: String) : ChipIcon
}

internal object PvIcons {

    // Skills have no signature Skyblock item. Vanilla items let packs retexture them.
    fun skill(name: String): ChipIcon? =
        SKILL_ICONS[name]?.let { SkyblockItems.spec(it) }?.let { ChipIcon.Item(it) }

    fun slayer(name: String): ChipIcon? =
        SLAYER_HEAD_TEXTURES[name]?.let { head(name, it) }

    /** Selected skin, otherwise the rarity's base pet head from Skyblock PV's repo. */
    fun pet(pet: ProfilePet): ChipIcon {
        val texture = pet.skin?.let(SkyblockItems::skinTexture) ?: PetTextures.texture(pet.type, pet.tier)
        if (texture != null) return head(pet.type, texture)
        return ChipIcon.Item(SkyblockItems.spec("minecraft:player_head")!!)
    }

    private fun head(name: String, texture: String): ChipIcon.Head {
        val uuid = UUID.nameUUIDFromBytes(texture.toByteArray(StandardCharsets.UTF_8)).toString()
        return ChipIcon.Head(uuid, name, texture)
    }

    /** Class item, with a vanilla stand-in until the item table loads (or if it never loads). */
    fun dungeonClass(name: String): ChipIcon? {
        val spec = CLASS_ITEMS[name]?.let { SkyblockItems.spec(it) }
            ?: CLASS_FALLBACK_ICONS[name]?.let { SkyblockItems.spec(it.id, it.components) }
        return spec?.let { ChipIcon.Item(it) }
    }

    private class VanillaIcon(val id: String, val components: String? = null)

    private val SKILL_ICONS = mapOf(
        "farming" to "minecraft:golden_hoe",
        "mining" to "minecraft:stone_pickaxe",
        "combat" to "minecraft:stone_sword",
        "foraging" to "minecraft:jungle_sapling",
        "fishing" to "minecraft:fishing_rod",
        "enchanting" to "minecraft:enchanting_table",
        "alchemy" to "minecraft:brewing_stand",
        "taming" to "minecraft:polar_bear_spawn_egg",
        "carpentry" to "minecraft:crafting_table",
        "runecrafting" to "minecraft:magma_cream",
        "social" to "minecraft:emerald",
        "hunting" to "minecraft:lead",
    )

    private val CLASS_ITEMS = mapOf(
        "healer" to "MENDER_CROWN",
        "mage" to "BONZO_STAFF",
        "berserk" to "DARK_CLAYMORE",
        "archer" to "TERMINATOR",
        "tank" to "TANK_WITHER_CHESTPLATE",
    )

    /**
     * Same shape as the real item, so the swap doesn't change the look. The healer potion needs potion_contents, or it
     * draws as a clear flask.
     */
    private val CLASS_FALLBACK_ICONS = mapOf(
        "healer" to VanillaIcon("minecraft:potion", """"minecraft:potion_contents":{"potion":"minecraft:healing"}"""),
        "mage" to VanillaIcon("minecraft:blaze_rod"),
        "berserk" to VanillaIcon("minecraft:iron_sword"),
        "archer" to VanillaIcon("minecraft:bow"),
        "tank" to VanillaIcon("minecraft:leather_chestplate"),
    )

    private val SLAYER_HEAD_TEXTURES = mapOf(
        "zombie" to "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvZDhiZWUyM2I1YzcyNmFlOGUzZDAyMWU4YjRmNzUyNTYxOWFiMTAyYTRlMDRiZTk4M2I2MTQxNDM0OWFhYWM2NyJ9fX0=",
        "spider" to "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvY2Q1NDE1NDFkYWFmZjUwODk2Y2QyNThiZGJkZDRjZjgwYzNiYTgxNjczNTcyNjA3OGJmZTM5MzkyN2U1N2YxIn19fQ==",
        "wolf" to "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvMmU3M2ZmMmNhNjNlNzY2NjJkOGEwNjczYTQ4YjAyZWFjNmE2NjczM2U3OTRhYjU4MjEwNjJiMmZhN2ZiZTdhOCJ9fX0=",
        "enderman" to "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvZDQ2NDNmOWY1NzhjM2JmMmNhZDU4NTlkZDA0YWU5NDlkZTA4MTIyZGYxM2MzZjAyYzk0MTVkYTYzY2ViYWFlNiJ9fX0=",
        "blaze" to "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvYjc4ZWYyZTRjZjJjNDFhMmQxNGJmZGU5Y2FmZjEwMjE5ZjViMWJmNWIzNWE0OWViNTFjNjQ2Nzg4MmNiNWYwIn19fQ==",
        "vampire" to "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvNWFhMjllYTk2MTc1N2RjM2M5MGJmYWJmMzAyYzVhYmU5ZDMwOGZiNGE3ZDM4NjRlNTc4OGFkMmNjOTE2MGFhMiJ9fX0=",
    )
}
