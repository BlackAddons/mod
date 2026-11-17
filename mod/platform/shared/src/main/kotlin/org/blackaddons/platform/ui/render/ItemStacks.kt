package org.blackaddons.platform.ui.render

import com.google.common.collect.LinkedHashMultimap
import com.google.gson.JsonParser
import com.mojang.authlib.GameProfile
import com.mojang.authlib.properties.Property
import com.mojang.authlib.properties.PropertyMap
import com.mojang.serialization.JsonOps
import net.minecraft.client.Minecraft
import net.minecraft.core.component.DataComponents
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.resources.Identifier
import net.minecraft.world.item.Item
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import net.minecraft.world.item.component.ResolvableProfile
import org.blackaddons.api.util.Uuids
import org.blackaddons.core.log.Log
import org.blackaddons.platform.impl.world.MinecraftItems
import org.blackaddons.platform.util.modId
import java.util.UUID

/** No pack can define it, so [ItemStacks.modelLoaded] gets the missing model back. */
private val ABSENT_MODEL: Identifier = modId("absent_model")

/**
 * Resolves registry ids, `ItemStack.CODEC` specs and player heads into drawable [ItemStack]s. Render thread only.
 * Misses are cached too so they only get logged once.
 */
internal object ItemStacks {

    private const val TEXTURES = "textures"

    // A profile needs a name, the skull renderer only reads the textures.
    private const val HEAD_NAME = "SkyblockItem"

    // Null = no such item. Ids keep MC types out of :api.
    private val byId = HashMap<String, ItemStack?>()

    // Null = bad spec. Not cached before a world loads, since components need its registries.
    private val bySpec = HashMap<String, ItemStack?>()

    // Specs whose pack model is missing, with the model they draw instead.
    private val fixedStacks = HashMap<String, ItemStack>()

    // Built once. The skull renderer reads the profile's textures, so off-server players render.
    private val heads = HashMap<String, ItemStack>()

    fun forId(id: String): ItemStack? {
        byId[id]?.let { return it }
        if (byId.containsKey(id)) return null

        // Unknown ids yield AIR.
        val item = Identifier.tryParse(id)?.let { BuiltInRegistries.ITEM.getValue(it) }
        if (item != null && !componentsBound(item)) return null

        val stack = item?.let { ItemStack(it) }?.takeUnless { it.isEmpty }
        if (stack == null) Log.main.warn("ItemStacks: no item registered as '{}'", id)
        byId[id] = stack
        return stack
    }

    /**
     * Components bind with the world's registries, so in the main menu `ItemStack` throws (ClickGUI and HUD editor can
     * render first). Checked, not caught, and "not yet" isn't cached.
     */
    private fun componentsBound(item: Item): Boolean =
        BuiltInRegistries.ITEM.get(BuiltInRegistries.ITEM.getKey(item))
            .map { it.areComponentsBound() }
            .orElse(false) ?: false

    /** Fixed up for models this client can't resolve. */
    fun forSpec(json: String): ItemStack? = parseSpec(json)?.let { renderable(json, it) }

    fun forPlayerHead(uuid: String, name: String, textures: String?): ItemStack? {
        val key = "$uuid|$name|${textures?.hashCode() ?: 0}"
        heads[key]?.let { return it }
        if (!componentsBound(Items.PLAYER_HEAD)) return null

        val stack = ItemStack(Items.PLAYER_HEAD)
        val onlinePlayer = Minecraft.getInstance().connection?.onlinePlayers?.firstOrNull {
            it.profile.name.equals(name, ignoreCase = true)
        }
        /*
         * The live profile is up to date after a skin change, but it has no skin when the server's own lookup failed (a
         * session server timeout at login did that in singleplayer). Then the textures we were given beat a default
         * head.
         */
        val onlineProfile = onlinePlayer?.profile?.takeIf { textures == null || it.properties()[TEXTURES].isNotEmpty() }

        if (onlineProfile != null) {
            stack.set(DataComponents.PROFILE, ResolvableProfile.createResolved(onlineProfile))
        } else if (textures != null) {
            val id = Uuids.parse(uuid) ?: UUID.nameUUIDFromBytes(name.toByteArray())
            val properties = LinkedHashMultimap.create<String, Property>()
            properties.put(TEXTURES, Property(TEXTURES, textures))
            val profile = GameProfile(id, name, PropertyMap(properties))
            stack.set(DataComponents.PROFILE, ResolvableProfile.createResolved(profile))
        }
        heads[key] = stack
        return stack
    }

    private fun parseSpec(json: String): ItemStack? {
        bySpec[json]?.let { return it }
        if (bySpec.containsKey(json)) return null

        val connection = Minecraft.getInstance().connection ?: return null
        val ops = connection.registryAccess().createSerializationContext(JsonOps.INSTANCE)
        val stack = runCatching {
            ItemStack.CODEC.parse(ops, JsonParser.parseString(json)).result().orElse(null)
        }.getOrNull()
        if (stack == null) Log.main.warn("ItemStacks: could not parse item spec: {}", json)
        bySpec[json] = stack
        return stack
    }

    /**
     * Skyblock `item_model`s only exist in Hypixel's pack, and the profile API keeps every item that has one as plain
     * paper with only its Skyblock id to tell it apart. Either way the item draws as what it was before the pack (an
     * Ender Monocle as its head, an Aspect of the Void as a diamond shovel). A missing model the old table doesn't know
     * falls back to the base item. Reset, not removed: removing leaves no model. Decided per draw, since joining a
     * server changes the answer, and cached once the old table is in.
     */
    private fun renderable(json: String, stack: ItemStack): ItemStack {
        val model = stack.get(DataComponents.ITEM_MODEL) ?: return stack
        val missingModel = !modelLoaded(model)
        if (!missingModel && stack.item !== Items.PAPER) return stack
        fixedStacks[json]?.let { return it }
        val fallback = if (missingModel) baseModel(stack) else stack
        val legacy = LegacyItemModels.ready() ?: return fallback
        val look = MinecraftItems.skyblockId(stack)?.let(legacy::get)
        return (look?.let { legacyStack(stack, it) } ?: fallback).also { fixedStacks[json] = it }
    }

    private fun baseModel(stack: ItemStack): ItemStack =
        stack.copy().apply { set(DataComponents.ITEM_MODEL, item.components().get(DataComponents.ITEM_MODEL)) }

    // A head's skull renderer reads the profile from the stack, whatever its base item is.
    private fun legacyStack(stack: ItemStack, look: LegacyItemModels.Look): ItemStack? {
        if (!modelLoaded(look.model)) return null
        return stack.copy().apply {
            set(DataComponents.ITEM_MODEL, look.model)
            look.texture?.let { set(DataComponents.PROFILE, headProfile(it)) }
        }
    }

    private fun headProfile(texture: String): ResolvableProfile {
        val properties = LinkedHashMultimap.create<String, Property>()
        properties.put(TEXTURES, Property(TEXTURES, texture))
        return ResolvableProfile.createResolved(GameProfile(UUID.nameUUIDFromBytes(texture.toByteArray()), HEAD_NAME, PropertyMap(properties)))
    }

    // Unknown ids return ModelManager's missing-model instance, so compare against it.
    private fun modelLoaded(id: Identifier): Boolean {
        val models = Minecraft.getInstance().modelManager
        return models.getItemModel(id) !== models.getItemModel(ABSENT_MODEL)
    }
}
