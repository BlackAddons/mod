package org.blackaddons.core.profile

import com.google.gson.JsonParser
import org.blackaddons.api.Platform
import org.blackaddons.core.backend.Backend
import org.blackaddons.core.backend.Http
import org.blackaddons.core.log.Log
import org.blackaddons.core.profile.pets.PetTextures
import org.blackaddons.core.skyblock.SkyblockItems

/**
 * `/v1/skyblock/profiles/{uuid}` (player JWT): 200 parses, 404 means "no profiles", everything else means "retry
 * later".
 */
internal object HypixelProfileSource : ProfileSource {

    override val name = "Backend Skyblock proxy"

    /**
     * Hypixel's 1.8 inventory format, with one stack for each path the fixers treat differently: an item with lore and
     * enchants, and a textured head.
     */
    private const val FIXER_SAMPLE =
        "H4sIAAAAAAAC/22PzUoDQRCEa3Y3yex40IfwKgQC8eQhaEBh1cCC99Zt4uDsD7MzGh/C58h75MnsXcxFUoe+NFX1lQFyKGsAJAkS" +
            "W6kzhcltG5ugEkzvqKYtAwZpoK3BrLJ95+hbI3uimpEf9suS6s5xjqxoPWsJUpgd9teFbVh85+td8LQKwdvXGLjXQwmm5epxU6yl" +
            "O+Pm7X2oV2M9LhKk7tNJiBCNQD8ngNIjkCk/onPPXw17SX6ocDn/09WJc5T4Nr7t2AfLfQ4deBei537k0Ji8kIssaIv5DYbx/2fL" +
            "PrpnquSJX9tDmgJCAQAA"

    @Volatile
    private var fixersWarm = false

    override fun fetch(id: MojangId): SourceResult {
        /*
         * Warm up the item table and pet textures alongside the fetch, because chips resolve their icons on the render
         * thread.
         */
        val itemTable = ProfileWorkers.submit { SkyblockItems.prefetch() }
        val petTextures = ProfileWorkers.submit { PetTextures.prefetch() }
        val fixers = ProfileWorkers.submit(::warmFixers)
        val response = LoadTrace.span("fetch") {
            Http.get("${Backend.SKYBLOCK_PROFILES_URL}/${id.uuid}", authenticated = true)
        } ?: return SourceResult.Unavailable
        LoadTrace.note("${response.body.length / 1024} KB")
        return when (response.status) {
            200 -> {
                LoadTrace.span("warm-up wait") { listOf(itemTable, petTextures, fixers).forEach { runCatching { it.get() } } }
                parseBody(response.body, id)
            }
            404 -> SourceResult.Empty
            else -> {
                // The one place that decides "unavailable", so log why.
                Log.main.warn("Profile lookup for {} ({}) unavailable: backend status {}", id.name, id.uuid, response.status)
                SourceResult.Unavailable
            }
        }
    }

    /**
     * The data fixers build their rules from 1.8 to the current version on the first upgrade. That took seconds and
     * stalled every decode thread at once. Upgrading one sample stack alongside the fetch pays that cost before the
     * parallel decode starts.
     */
    private fun warmFixers() {
        if (fixersWarm) return
        // Empty without a connection. Nothing got fixed, so try again on the next lookup.
        fixersWarm = runCatching { Platform.items.decodeLegacyInventory(FIXER_SAMPLE) }.getOrNull().orEmpty().isNotEmpty()
    }

    private fun parseBody(body: String, id: MojangId): SourceResult = runCatching {
        val root = LoadTrace.span("json") { JsonParser.parseString(body).asJsonObject }
        LoadTrace.span("parse") { RawHypixelParser.parse(root, id) }
    }.getOrElse {
        Log.main.warn("Profile lookup for {} unavailable: response parse failed", id.name, it)
        SourceResult.Unavailable
    }
}
