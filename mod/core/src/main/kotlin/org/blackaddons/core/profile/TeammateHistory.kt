package org.blackaddons.core.profile

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import org.blackaddons.api.Platform
import org.blackaddons.api.util.io.Disk
import org.blackaddons.api.util.io.Gsons
import org.blackaddons.core.log.Log

/**
 * The recent-runs API is a moving window. We keep it locally, so later lookups extend the history instead of replacing
 * it.
 */
internal object TeammateHistory {

    private const val FILE = "pv_teammate_history.json"
    private const val MAX_ACTIVITIES_PER_PROFILE = 500

    private val historyByProfile = mutableMapOf<String, List<RecentActivity>>()

    private var loaded = false
    private var writable = true

    /** On the lookup worker, never in a render pass. */
    @Synchronized
    fun remember(ownerUuid: String, recentByProfile: Map<String, List<RecentActivity>>) {
        loadIfNeeded()
        var changed = false
        recentByProfile.forEach { (profileId, recent) ->
            val key = key(ownerUuid, profileId)
            val merged = merge(historyByProfile[key].orEmpty(), recent)
            if (merged != historyByProfile[key]) {
                historyByProfile[key] = merged
                changed = true
            }
        }
        if (changed && writable) save()
    }

    @Synchronized
    fun activities(ownerUuid: String, profileId: String): List<RecentActivity> {
        loadIfNeeded()
        return historyByProfile[key(ownerUuid, profileId)].orEmpty()
    }

    internal fun merge(previous: List<RecentActivity>, incoming: List<RecentActivity>): List<RecentActivity> =
        (previous + incoming)
            .associateBy(RecentActivity::id)
            .values
            .sortedByDescending(RecentActivity::completedAt)
            .take(MAX_ACTIVITIES_PER_PROFILE)

    private fun loadIfNeeded() {
        if (loaded) return
        loaded = true
        runCatching {
            val root = JsonParser.parseString(Disk.read(Platform.configDir.resolve(FILE)) ?: return).asJsonObject
            root.entrySet().forEach { (key, value) ->
                val parsed = value.takeIf { it.isJsonArray }?.asJsonArray
                    ?.mapNotNull { Gsons.pretty.fromJson(it, RecentActivity::class.java) }
                    .orEmpty()
                if (parsed.isNotEmpty()) historyByProfile[key] = merge(emptyList(), parsed)
            }
        }.onFailure {
            // A malformed file might be recoverable, so never overwrite it.
            writable = false
            Log.main.warn("Failed to read Profile Viewer teammate history", it)
        }
    }

    private fun save() = runCatching {
        val root = JsonObject()
        historyByProfile.forEach { (key, value) -> root.add(key, Gsons.pretty.toJsonTree(value)) }
        Disk.write(Platform.configDir.resolve(FILE), Gsons.pretty.toJson(root))
    }.onFailure { Log.main.warn("Failed to save Profile Viewer teammate history", it) }

    private fun key(ownerUuid: String, profileId: String) = "$ownerUuid/$profileId"
}
