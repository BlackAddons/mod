package org.blackaddons.api.world

import org.blackaddons.api.chat.ChatMessage
import org.blackaddons.api.gui.ScreenDraw

/**
 * Item stack inspection without `ItemStack`. The host owns the [raw] format end to end, so captured stacks
 * and [ScreenDraw.item] stay compatible across versions.
 */
interface ItemHost {
    /** Searchable metadata, or null if unparseable. */
    fun describe(raw: String): ItemInfo?

    /**
     * Decodes a Hypixel API inventory (gzipped base64 1.8 NBT) into one [raw] stack per slot; null for
     * empty or rejected slots, empty list if unreadable. Only the icon is faithful: name and lore keep
     * `§` codes, Skyblock data lands in custom data. Slow - call off the render thread and cache.
     * Needs a live connection for registries. Backpacks nest another blob; decode it the same way.
     */
    fun decodeLegacyInventory(base64: String): List<String?>

    /** Parses `/give` item syntax, such as `diamond_sword[enchantment_glint_override=true]`, into a [raw] stack. Null if it's invalid or there's no world. */
    fun parseItemArgument(text: String): String?
}

/** [id] is the registry id (e.g. `minecraft:barrier`). */
data class ItemInfo(
    val id: String,
    val name: String,
    val loreLines: List<String>,
    val count: Int,
    /** For rendering; [name] stays plain for matching. */
    val formattedName: ChatMessage = ChatMessage.of(name),
    /** For rendering; [loreLines] stay plain for matching. */
    val formattedLoreLines: List<ChatMessage> = loreLines.map(ChatMessage::of),
    /** e.g. `SPIRIT_LEAP`; null when absent. */
    val skyblockId: String? = null,
    /** Hypixel's per-item `uuid`. Null for stackable items and menu icons. */
    val skyblockUuid: String? = null,
    /** The raw `petInfo` JSON of pet items. Parse it with [org.blackaddons.api.skyblock.player.Pet.of]. */
    val petInfo: String? = null,
)
