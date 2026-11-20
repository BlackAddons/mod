package org.blackaddons.platform.impl.world
import com.google.gson.JsonElement
import com.google.gson.JsonParser
import com.mojang.brigadier.StringReader
import com.mojang.serialization.Dynamic
import com.mojang.serialization.DynamicOps
import com.mojang.serialization.JsonOps
import net.minecraft.SharedConstants
import net.minecraft.client.Minecraft
import net.minecraft.commands.CommandBuildContext
import net.minecraft.commands.arguments.item.ItemArgument
import net.minecraft.core.RegistryAccess
import net.minecraft.core.component.DataComponents
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.nbt.CompoundTag
import net.minecraft.nbt.NbtAccounter
import net.minecraft.nbt.NbtIo
import net.minecraft.nbt.NbtOps
import net.minecraft.nbt.Tag
import net.minecraft.util.datafix.DataFixers
import net.minecraft.util.datafix.fixes.References
import net.minecraft.world.flag.FeatureFlags
import net.minecraft.world.item.ItemStack
import org.blackaddons.api.world.ItemHost
import org.blackaddons.api.world.ItemInfo
import org.blackaddons.core.log.Log
import org.blackaddons.platform.impl.chat.toChatMessage
import org.blackaddons.platform.impl.gui.MinecraftOpenContainer
import org.blackaddons.platform.impl.gui.MinecraftScreenDraw
import org.blackaddons.platform.impl.world.MinecraftItems.describe
import org.blackaddons.platform.impl.world.MinecraftItems.deserialize
import org.blackaddons.platform.impl.world.MinecraftItems.serialize
import org.blackaddons.platform.mixin.world.CustomDataAccessor
import java.io.ByteArrayInputStream
import java.util.Base64

/**
 * [ItemHost] via [ItemStack.CODEC] over [JsonOps]. [serialize]/[deserialize] also back [MinecraftOpenContainer] and
 * [MinecraftScreenDraw] so the format lives in one place.
 */
object MinecraftItems : ItemHost {

    private val mc get() = Minecraft.getInstance()

    private fun ops(): DynamicOps<JsonElement>? = connectionOps()?.json

    /** Null when empty or no world. */
    internal fun serialize(stack: ItemStack): String? {
        if (stack.isEmpty) return null
        val ops = ops() ?: return null
        return serialize(stack, ops)
    }

    private fun serialize(stack: ItemStack, ops: DynamicOps<JsonElement>): String? =
        ItemStack.CODEC.encodeStart(ops, stack).result().orElse(null)?.toString()

    /**
     * Cached: a parse plus codec decode per stack per frame halved FPS on large browsers, and a raw string fully
     * describes a stack. Client thread only. The stack is shared, not copied, and never written.
     */
    internal fun deserialize(raw: String): ItemStack? {
        if (raw.isBlank()) return null
        decoded[raw]?.let { return it }
        val ops = ops() ?: return null
        val stack = try {
            ItemStack.CODEC.parse(ops, JsonParser.parseString(raw)).result().orElse(null)
        } catch (e: Exception) {
            null
        }
        // Failures aren't cached: usually it's "no world yet", which changes soon.
        if (stack != null) decoded[raw] = stack
        return stack
    }

    /** A full storage browser with headroom. */
    private const val MAX_DECODED = 1024

    private val decoded = object : LinkedHashMap<String, ItemStack>(256, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, ItemStack>): Boolean =
            size > MAX_DECODED
    }

    /** Call on connection change, because decoding uses that connection's registries. */
    internal fun forgetDecoded() = decoded.clear()

    override fun describe(raw: String): ItemInfo? = deserialize(raw)?.let(::info)

    /** Shared by [describe] and live inventory reads. */
    internal fun info(stack: ItemStack): ItemInfo? {
        if (stack.isEmpty) return null
        val name = stack.hoverName
        val lore = stack.get(DataComponents.LORE)?.lines().orEmpty()
        val id = BuiltInRegistries.ITEM.getKey(stack.item).toString()
        val custom = skyblockTag(stack)
        return ItemInfo(
            id = id,
            name = name.string,
            loreLines = lore.map { it.string },
            count = stack.count,
            formattedName = name.toChatMessage(),
            formattedLoreLines = lore.map { it.toChatMessage() },
            skyblockId = custom?.getStringOr(SKYBLOCK_ID, "")?.ifEmpty { null },
            skyblockUuid = custom?.getStringOr(SKYBLOCK_UUID, "")?.ifEmpty { null },
            petInfo = custom?.getStringOr(PET_INFO, "")?.ifEmpty { null },
        )
    }

    internal fun skyblockId(stack: ItemStack): String? = skyblockTag(stack)?.getStringOr(SKYBLOCK_ID, "")?.ifEmpty { null }

    /**
     * Where Hypixel keeps an item's Skyblock data: at the top of `custom_data` on a live stack, but under
     * `ExtraAttributes` on one from the profile API, because the data fixers move a 1.8 item's whole tag there as is.
     */
    private fun skyblockTag(stack: ItemStack): CompoundTag? {
        val custom = customTag(stack) ?: return null
        if (custom.contains(SKYBLOCK_ID)) return custom
        return custom.getCompound(LEGACY_ATTRIBUTES).orElse(custom)
    }

    // Accessor mixed in at runtime.
    @Suppress("CAST_NEVER_SUCCEEDS")
    private fun customTag(stack: ItemStack): CompoundTag? {
        val data = stack.get(DataComponents.CUSTOM_DATA) ?: return null
        return (data as CustomDataAccessor).`blackaddons$getTag`()
    }

    private const val SKYBLOCK_ID = "id"
    private const val LEGACY_ATTRIBUTES = "ExtraAttributes"
    private const val SKYBLOCK_UUID = "uuid"
    private const val PET_INFO = "petInfo"

    /** 1.8 predates `DataVersion` (starts at 100), so 99 means "before all fixers". */
    private const val LEGACY_DATA_VERSION = 99

    /** Hypixel's slot list `i`: one compound per slot, empty ones blank. */
    private const val SLOT_LIST = "i"

    override fun decodeLegacyInventory(base64: String): List<String?> {
        val root = try {
            val bytes = Base64.getDecoder().decode(base64.trim())
            NbtIo.readCompressed(ByteArrayInputStream(bytes), NbtAccounter.unlimitedHeap())
        } catch (e: Exception) {
            Log.main.warn("Failed to read a legacy inventory blob", e)
            return emptyList()
        }

        val slots = root.getListOrEmpty(SLOT_LIST)
        val target = SharedConstants.getCurrentVersion().dataVersion().version()
        val ops = connectionOps() ?: return emptyList()
        return slots.indices.map { i ->
            val tag = slots.getCompoundOrEmpty(i)
            if (tag.isEmpty) null else upgrade(tag, target, ops)
        }
    }

    /** 1.8 compound through the data fixers, re-encoded as [raw]. */
    private fun upgrade(tag: CompoundTag, target: Int, ops: ConnectionOps): String? = try {
        val fixed: Tag = DataFixers.getDataFixer()
            .update(References.ITEM_STACK, Dynamic(NbtOps.INSTANCE, tag), LEGACY_DATA_VERSION, target)
            .value
        val stack = ItemStack.CODEC.parse(ops.nbt, fixed).result().orElse(null) ?: return null
        serialize(stack, ops.json)
    } catch (e: Exception) {
        // One bad stack shouldn't lose the whole inventory.
        Log.main.warn("Data fixers rejected a legacy stack", e)
        null
    }

    /**
     * Built once per connection. The registries are frozen, so the codec and fixer passes can run on any worker. Asking
     * the client thread for them on every call cost a frame each time, and one profile decodes about 90 inventory
     * blobs.
     */
    private fun connectionOps(): ConnectionOps? {
        val registries = mc.connection?.registryAccess() ?: return null
        cachedOps?.takeIf { it.registries === registries }?.let { return it }
        return ConnectionOps(
            registries,
            registries.createSerializationContext(NbtOps.INSTANCE),
            registries.createSerializationContext(JsonOps.INSTANCE),
        ).also { cachedOps = it }
    }

    override fun parseItemArgument(text: String): String? {
        val ops = connectionOps() ?: return null
        return runCatching {
            val input = ItemArgument.item(CommandBuildContext.simple(ops.registries, FeatureFlags.VANILLA_SET)).parse(StringReader(text))
            serialize(input.createItemStack(1), ops.json)
        }.getOrNull()
    }

    @Volatile
    private var cachedOps: ConnectionOps? = null

    private class ConnectionOps(val registries: RegistryAccess, val nbt: DynamicOps<Tag>, val json: DynamicOps<JsonElement>)
}
