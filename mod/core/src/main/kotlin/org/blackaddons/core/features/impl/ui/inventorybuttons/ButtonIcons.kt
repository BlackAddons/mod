package org.blackaddons.core.features.impl.ui.inventorybuttons

import com.google.gson.JsonParser
import org.blackaddons.api.Platform
import org.blackaddons.api.util.concurrent.Workers
import org.blackaddons.core.skyblock.SkyblockItems
import java.util.Base64

internal object ButtonIcons {

    internal sealed interface Icon {
        data class SkyblockId(val id: String) : Icon
        data class Vanilla(val id: String) : Icon

        data class Give(val argument: String) : Icon

        // Always the base64 textures value; a hash is converted while parsing.
        data class Skull(val textures: String) : Icon
    }

    // Only hits are cached, because the item table may still be loading.
    private val specs = LinkedHashMap<String, String>()

    @Volatile private var itemTableRequested = false

    // Null until it can be drawn: bad text, no world yet, or the item table is still loading.
    fun spec(icon: String): String? {
        val text = icon.trim()
        if (text.isEmpty()) return null
        specs[text]?.let { return it }
        val spec = when (val parsed = parse(text)) {
            is Icon.SkyblockId -> SkyblockItems.spec(parsed.id).also { if (it == null) requestItemTable() }
            is Icon.Vanilla -> SkyblockItems.spec(parsed.id)
            is Icon.Give -> Platform.items.parseItemArgument(parsed.argument)
            is Icon.Skull -> SkyblockItems.headSpec(parsed.textures)
            null -> null
        } ?: return null
        if (specs.size >= MAX_CACHED) specs.clear()
        specs[text] = spec
        return spec
    }

    fun parse(text: String): Icon? {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return null
        if (trimmed.startsWith(SKULL_PREFIX, ignoreCase = true)) {
            val value = trimmed.substring(SKULL_PREFIX.length).trim()
            if (value.isEmpty()) return null
            return Icon.Skull(if (TEXTURE_HASH.matches(value)) texturesFor(value) else value)
        }
        val give = GIVE.find(trimmed)
        if (give != null) return give.groupValues[1].trim().takeIf { it.isNotEmpty() }?.let(Icon::Give)
        if ('[' in trimmed) return Icon.Give(trimmed)
        if (':' in trimmed || trimmed != trimmed.uppercase()) {
            return Icon.Vanilla(if (':' in trimmed) trimmed.lowercase() else "minecraft:${trimmed.lowercase()}")
        }
        return Icon.SkyblockId(trimmed)
    }

    // A head keeps its skin; anything else is stored by Skyblock id, or by vanilla id.
    fun fromRaw(raw: String): String? {
        headTextures(raw)?.let { return SKULL_PREFIX + it }
        val info = Platform.items.describe(raw) ?: return null
        return info.skyblockId ?: info.id.removePrefix("minecraft:")
    }

    internal fun headTextures(raw: String): String? = runCatching {
        val profile = JsonParser.parseString(raw).asJsonObject
            .getAsJsonObject("components")?.getAsJsonObject("minecraft:profile") ?: return null
        profile.getAsJsonArray("properties")
            ?.map { it.asJsonObject }
            ?.firstOrNull { it.get("name")?.asString == "textures" }
            ?.get("value")?.asString
    }.getOrNull()

    private fun texturesFor(hash: String): String {
        val json = """{"textures":{"SKIN":{"url":"https://textures.minecraft.net/texture/$hash"}}}"""
        return Base64.getEncoder().encodeToString(json.toByteArray())
    }

    private fun requestItemTable() {
        if (itemTableRequested) return
        itemTableRequested = true
        Workers.start("inventory-button-items") { SkyblockItems.prefetch() }
    }

    private const val SKULL_PREFIX = "skull:"
    private const val MAX_CACHED = 256
    private val TEXTURE_HASH = Regex("[0-9a-fA-F]{32,64}")

    // "/give @s stone" or "give Steve stone"; the target is ignored.
    private val GIVE = Regex("^/?give\\s+\\S+\\s+(.+)$", RegexOption.IGNORE_CASE)
}
