package org.blackaddons.core.profile.pets

import com.google.gson.JsonParser
import org.blackaddons.api.Platform
import org.blackaddons.api.host.HttpHost
import org.blackaddons.core.log.Log
import java.util.Locale

internal object PetTextures {

    // The second URL is a backup for Cloudflare outages. NEU's constants have no textures in this schema.
    private val URLS = listOf(
        "https://skyblock-api-repo.thatgravyboat.tech/1_21_5/pets.min.json",
        "https://raw.githubusercontent.com/SkyblockAPI/Repo/refs/heads/main/cloudflare/1_21_5/pets.min.json",
    )

    @Volatile private var textures: Map<String, String>? = null

    fun prefetch() {
        if (textures != null) return
        fetchTextures { Platform.http.getBlocking(it) }?.let { textures = it }
    }

    internal fun fetchTextures(fetch: (String) -> HttpHost.Response?): Map<String, String>? {
        for (url in URLS) {
            val response = fetch(url) ?: continue
            if (response.status != 200) {
                Log.main.warn("Pet texture table unavailable from {}: status {}", url, response.status)
                continue
            }
            val parsed = parse(response.body)
            if (parsed.isNotEmpty()) return parsed
            Log.main.warn("Pet texture table from {} was invalid or empty", url)
        }
        return null
    }

    fun texture(type: String, tier: String): String? = textures?.get(key(type, tier))

    internal fun parse(json: String): Map<String, String> = runCatching {
        val root = JsonParser.parseString(json).asJsonObject
        buildMap {
            root.entrySet().forEach pet@{ (type, element) ->
                val tiers = element.takeIf { it.isJsonObject }?.asJsonObject
                    ?.get("tiers")?.takeIf { it.isJsonObject }?.asJsonObject ?: return@pet
                tiers.entrySet().forEach tier@{ (tier, value) ->
                    val texture = value.takeIf { it.isJsonObject }?.asJsonObject
                        ?.get("texture")?.takeIf { it.isJsonPrimitive }?.asString
                        ?.takeIf(String::isNotBlank) ?: return@tier
                    put(key(type, tier), texture)
                }
            }
        }
    }.getOrDefault(emptyMap())

    private fun key(type: String, tier: String): String =
        "${type.uppercase(Locale.ROOT)}|${tier.uppercase(Locale.ROOT)}"
}
