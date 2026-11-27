package org.blackaddons.core.features.impl.visual.fme

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import org.blackaddons.api.util.io.bool
import org.blackaddons.api.util.io.int
import org.blackaddons.api.util.io.long
import org.blackaddons.api.util.io.str
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

internal enum class FmeScope {
    GLOBAL,
    LOCATION,
    DUNGEON,
}

internal data class FmeProfileHeader(val id: String, val name: String)

internal class FmeProfileStore {
    private val profiles = linkedMapOf(DEFAULT_PROFILE_ID to FmeProfileHeader(DEFAULT_PROFILE_ID, "Default"))

    fun values(): List<FmeProfileHeader> = profiles.values.toList()

    fun get(id: String): FmeProfileHeader? = profiles[id]

    fun create(name: String): FmeProfileHeader {
        val id = UUID.randomUUID().toString()
        return FmeProfileHeader(id, name.trim().ifEmpty { "New Profile" }).also { profiles[id] = it }
    }

    fun rename(id: String, name: String): Boolean {
        val existing = profiles[id] ?: return false
        val renamed = existing.copy(name = name.trim().ifEmpty { "Unnamed Profile" })
        if (renamed == existing) return false
        profiles[id] = renamed
        return true
    }

    fun remove(id: String): Boolean = profiles.size > 1 && profiles.remove(id) != null

    fun add(profile: FmeProfileHeader): Boolean {
        if (!isValidFmeProfileId(profile.id) || profile.id in profiles) return false
        profiles[profile.id] = profile
        return true
    }

    fun replaceAll(values: List<FmeProfileHeader>) {
        profiles.clear()
        values.filter { isValidFmeProfileId(it.id) && it.name.isNotBlank() }
            .forEach { profiles.putIfAbsent(it.id, it.copy(name = it.name.trim())) }
        if (profiles.isEmpty()) {
            profiles[DEFAULT_PROFILE_ID] = FmeProfileHeader(DEFAULT_PROFILE_ID, "Default")
        }
    }

    companion object {
        const val DEFAULT_PROFILE_ID = "default"
    }
}

internal data class FmeContext(val scope: FmeScope, val key: String = "")

internal data class FmeReplacement(
    val profileId: String = FmeProfileStore.DEFAULT_PROFILE_ID,
    val scope: FmeScope = FmeScope.LOCATION,
    val scopeKey: String = "",
    val x: Int,
    val y: Int,
    val z: Int,
    val sourceBlockId: String = "minecraft:air",
    val blockId: String,
    val ghost: Boolean = false,
    val tintRgb: Int = DEFAULT_TINT_RGB,
    val enabled: Boolean = true,
    val updatedAt: Long = 0L,
    val properties: Map<String, String> = emptyMap(),
) {
    val ruleKey: FmeRuleKey
        get() = FmeRuleKey(profileId, scope, scopeKey, sourceBlockId, blockId, ghost, tintRgb, enabled, properties)
}

internal data class FmeRuleKey(
    val profileId: String,
    val scope: FmeScope,
    val scopeKey: String,
    val sourceBlockId: String,
    val blockId: String,
    val ghost: Boolean = false,
    val tintRgb: Int = DEFAULT_TINT_RGB,
    val enabled: Boolean = true,
    /** Non-default state. Room edits keep it room-local. */
    val properties: Map<String, String> = emptyMap(),
) {
    fun at(x: Int, y: Int, z: Int, updatedAt: Long) = FmeReplacement(
        profileId, scope, scopeKey, x, y, z, sourceBlockId, blockId, ghost, tintRgb, enabled, updatedAt, properties,
    )

    fun sameContext(other: FmeRuleKey): Boolean =
        profileId == other.profileId && scope == other.scope && scopeKey == other.scopeKey
}

internal data class FmePositionRule(val key: FmeRuleKey, val cells: FmeCells, val updatedAt: Long = 0L)

internal data class FmePositionKey(
    val profileId: String,
    val scope: FmeScope,
    val scopeKey: String,
    val x: Int,
    val y: Int,
    val z: Int,
)

internal enum class FmePositionMoveResult {
    MOVED,
    UNCHANGED,
    OCCUPIED,
    MISSING,
}

internal enum class FmePositionAddResult {
    ADDED,
    OCCUPIED,
    MISSING,
}

internal class FmeReplacementStore {
    @Volatile private var rules: Map<FmeRuleKey, FmePositionRule> = emptyMap()

    fun get(profileId: String, context: FmeContext, x: Int, y: Int, z: Int): FmeReplacement? =
        get(FmePositionKey(profileId, context.scope, context.key, x, y, z))

    fun get(key: FmePositionKey): FmeReplacement? = ruleAt(key)?.let { it.key.at(key.x, key.y, key.z, it.updatedAt) }

    fun rule(key: FmeRuleKey): FmePositionRule? = rules[key]

    fun put(replacement: FmeReplacement) = with(replacement) {
        putRule(FmePositionRule(ruleKey, FmeCells.of(x, y, z), updatedAt))
    }

    fun putRule(rule: FmePositionRule) {
        if (rule.cells.isEmpty()) return
        val next = HashMap<FmeRuleKey, FmePositionRule>(rules.size + 1)
        rules.forEach { (key, existing) ->
            val kept = if (key != rule.key && key.sameContext(rule.key)) existing.copy(cells = existing.cells - rule.cells) else existing
            if (!kept.cells.isEmpty()) next[key] = kept
        }
        next[rule.key] = next[rule.key]
            ?.let { it.copy(cells = it.cells + rule.cells, updatedAt = maxOf(it.updatedAt, rule.updatedAt)) }
            ?: rule
        rules = next
    }

    fun remove(key: FmePositionKey): FmeReplacement? {
        val rule = ruleAt(key) ?: return null
        val cells = rule.cells.minus(key.x, key.y, key.z)
        rules = if (cells.isEmpty()) rules - rule.key else rules + (rule.key to rule.copy(cells = cells))
        return rule.key.at(key.x, key.y, key.z, rule.updatedAt)
    }

    fun removeRules(keys: Set<FmeRuleKey>): List<FmePositionRule> =
        keys.mapNotNull(rules::get).also { removed -> if (removed.isNotEmpty()) rules = rules - keys }

    fun removeProfile(profileId: String) {
        rules = rules.filterKeys { it.profileId != profileId }
    }

    fun clear() {
        rules = emptyMap()
    }

    fun rules(profileId: String, scope: FmeScope): List<FmePositionRule> = rules.values
        .filter { it.key.profileId == profileId && it.key.scope == scope }
        .sortedWith(compareBy({ it.key.scopeKey }, { it.key.sourceBlockId }, { it.key.blockId }))

    fun all(profileId: String): List<FmePositionRule> = rules.values.filter { it.key.profileId == profileId }

    fun size(profileId: String): Int = all(profileId).sumOf { it.cells.size }

    fun clearProfile(profileId: String): List<FmePositionRule> = all(profileId).also(::drop)

    fun clear(profileId: String, context: FmeContext): List<FmePositionRule> = rules.values
        .filter { it.key.profileId == profileId && it.key.scope == context.scope && it.key.scopeKey == context.key }
        .also(::drop)

    fun updateTint(keys: Set<FmeRuleKey>, tintRgb: Int): Boolean = rekey(keys) { it.copy(tintRgb = tintRgb) }

    fun updateEnabled(keys: Set<FmeRuleKey>, enabled: Boolean): Boolean = rekey(keys) { it.copy(enabled = enabled) }

    fun updateSource(keys: Set<FmeRuleKey>, sourceBlockId: String): Boolean = rekey(keys) { it.copy(sourceBlockId = sourceBlockId) }

    fun updateBlock(keys: Set<FmeRuleKey>, blockId: String, pinned: Map<String, String> = emptyMap()): Boolean =
        rekey(keys) { it.copy(blockId = blockId, properties = it.properties + pinned) }

    fun updateProperties(keys: Set<FmeRuleKey>, change: (FmeRuleKey) -> Map<String, String>): Boolean =
        rekey(keys) { it.copy(properties = change(it)) }

    fun snapshot(): Map<FmeRuleKey, FmePositionRule> = rules

    fun restore(snapshot: Map<FmeRuleKey, FmePositionRule>): Boolean {
        if (rules == snapshot) return false
        rules = snapshot
        return true
    }

    private fun rekey(keys: Set<FmeRuleKey>, change: (FmeRuleKey) -> FmeRuleKey): Boolean {
        val now = System.currentTimeMillis()
        var changed = false
        keys.forEach { key ->
            val rule = rules[key] ?: return@forEach
            val target = change(key)
            if (target == key) return@forEach
            rules = rules - key
            putRule(FmePositionRule(target, rule.cells, now))
            changed = true
        }
        return changed
    }

    private fun drop(removed: List<FmePositionRule>) {
        if (removed.isNotEmpty()) rules = rules - removed.mapTo(HashSet(), FmePositionRule::key)
    }

    private fun ruleAt(key: FmePositionKey): FmePositionRule? = rules.values.firstOrNull {
        it.key.profileId == key.profileId && it.key.scope == key.scope && it.key.scopeKey == key.scopeKey &&
            it.cells.contains(key.x, key.y, key.z)
    }
}

internal data class FmeTypeReplacement(
    val profileId: String = FmeProfileStore.DEFAULT_PROFILE_ID,
    val scope: FmeScope = FmeScope.GLOBAL,
    val scopeKey: String = "",
    val sourceBlockId: String,
    val blockId: String,
    val tintRgb: Int = DEFAULT_TINT_RGB,
    val enabled: Boolean = true,
    val updatedAt: Long = 0L,
    val properties: Map<String, String> = emptyMap(),
) {
    val key get() = FmeTypeKey(profileId, scope, scopeKey, sourceBlockId)
}

internal data class FmeTypeKey(
    val profileId: String,
    val scope: FmeScope,
    val scopeKey: String,
    val sourceBlockId: String,
)

internal class FmeTypeReplacementStore {
    private val values = ConcurrentHashMap<FmeTypeKey, FmeTypeReplacement>()

    fun get(profileId: String, context: FmeContext, blockId: String): FmeTypeReplacement? =
        values[FmeTypeKey(profileId, context.scope, context.key, blockId)]

    fun get(key: FmeTypeKey): FmeTypeReplacement? = values[key]

    fun put(replacement: FmeTypeReplacement): Boolean = values.put(replacement.key, replacement) != replacement

    fun remove(key: FmeTypeKey): Boolean = values.remove(key) != null

    fun removeProfile(profileId: String) {
        values.keys.removeIf { it.profileId == profileId }
    }

    fun clear() = values.clear()

    fun entries(profileId: String, scope: FmeScope): List<FmeTypeReplacement> = values.values
        .filter { it.profileId == profileId && it.scope == scope }
        .sortedWith(compareBy(FmeTypeReplacement::scopeKey, FmeTypeReplacement::sourceBlockId))

    fun all(profileId: String): List<FmeTypeReplacement> = values.values.filter { it.profileId == profileId }

    fun size(profileId: String): Int = values.values.count { it.profileId == profileId }

    fun clearProfile(profileId: String): Boolean = values.keys.removeIf { it.profileId == profileId }

    fun clear(profileId: String, context: FmeContext): Boolean = values.keys.removeIf {
        it.profileId == profileId && it.scope == context.scope && it.scopeKey == context.key
    }

    fun updateTint(key: FmeTypeKey, tintRgb: Int): Boolean {
        val existing = values[key] ?: return false
        if (existing.tintRgb == tintRgb) return false
        values[key] = existing.copy(tintRgb = tintRgb, updatedAt = System.currentTimeMillis())
        return true
    }

    fun updateEnabled(key: FmeTypeKey, enabled: Boolean): Boolean {
        val existing = values[key] ?: return false
        if (existing.enabled == enabled) return false
        values[key] = existing.copy(enabled = enabled, updatedAt = System.currentTimeMillis())
        return true
    }

    fun updateProperties(key: FmeTypeKey, properties: Map<String, String>): Boolean {
        val existing = values[key] ?: return false
        if (existing.properties == properties) return false
        values[key] = existing.copy(properties = properties, updatedAt = System.currentTimeMillis())
        return true
    }

    fun lines(): List<String> = values.values
        .sortedWith(compareBy(FmeTypeReplacement::profileId, FmeTypeReplacement::scope, FmeTypeReplacement::scopeKey, FmeTypeReplacement::sourceBlockId))
        .map { it.toJson().toString() }

    fun replaceAll(lines: List<String>): Boolean {
        val next = lines.mapNotNull(::parse).associateBy(FmeTypeReplacement::key)
        if (values == next) return false
        values.clear()
        values.putAll(next)
        return true
    }

    private fun parse(line: String): FmeTypeReplacement? {
        val json = parseObject(line) ?: return null
        val profileId = json.str("profile")?.takeIf(::isValidFmeProfileId) ?: return null
        val scope = json.str("scope")?.let { runCatching { FmeScope.valueOf(it) }.getOrNull() } ?: return null
        val scopeKey = json.str("scopeKey") ?: return null
        val source = json.str("source")?.takeIf(::isValidFmeSource) ?: return null
        val block = json.str("block")?.takeIf(::isValidFmeSource) ?: return null
        return FmeTypeReplacement(
            profileId = profileId,
            scope = scope,
            scopeKey = scopeKey,
            sourceBlockId = source,
            blockId = block,
            tintRgb = json.int("tint", DEFAULT_TINT_RGB) and DEFAULT_TINT_RGB,
            enabled = json.bool("enabled", true),
            updatedAt = json.long("updated"),
            properties = json.get("state")?.takeIf { it.isJsonObject }?.asJsonObject?.let(::parseFmeProperties).orEmpty(),
        )
    }
}

internal enum class FmeManagedKind { TYPE, POSITION }

internal data class FmeManagedGroup(
    val kind: FmeManagedKind,
    val scope: FmeScope,
    val scopeKey: String,
    val sourceBlockId: String,
    val blockId: String,
    val ghost: Boolean,
    val tintRgb: Int,
    val enabled: Boolean,
    val updatedAt: Long,
    val ruleKeys: Set<FmeRuleKey> = emptySet(),
    val typeKey: FmeTypeKey? = null,
    val positions: Int = 0,
) {
    val count: Int get() = if (kind == FmeManagedKind.TYPE) 1 else positions
}

private fun FmeTypeReplacement.toJson() = JsonObject().apply {
    addProperty("profile", profileId)
    addProperty("scope", scope.name)
    addProperty("scopeKey", scopeKey)
    addProperty("source", sourceBlockId)
    addProperty("block", blockId)
    addProperty("tint", tintRgb)
    addProperty("enabled", enabled)
    addProperty("updated", updatedAt)
    if (properties.isNotEmpty()) add("state", fmePropertiesJson(properties))
}

private fun parseObject(line: String): JsonObject? = runCatching {
    JsonParser.parseString(line).takeIf { it.isJsonObject }?.asJsonObject
}.getOrNull()

internal fun parseFmeProperties(json: JsonObject): Map<String, String> = json.entrySet()
    .mapNotNull { (name, value) ->
        val text = value.takeIf { it.isJsonPrimitive }?.asString ?: return@mapNotNull null
        if (isValidFmeProperty(name, text)) name to text else null
    }
    .toMap()

/** `charges=4,lit=true`. Invalid pairs get skipped. */
internal fun parseFmeState(text: String): Map<String, String> = text.split(',')
    .mapNotNull { pair ->
        val name = pair.substringBefore('=', "").trim()
        val value = pair.substringAfter('=', "").trim()
        if (isValidFmeProperty(name, value)) name to value else null
    }
    .toMap()

internal fun formatFmeState(properties: Map<String, String>): String =
    properties.entries.filter { isValidFmeProperty(it.key, it.value) }.joinToString(",") { "${it.key}=${it.value}" }

/** A null value unpins that property. */
internal fun Map<String, String>.withFmeEdits(edits: Map<String, String?>): Map<String, String> {
    val next = LinkedHashMap(this)
    edits.forEach { (name, value) -> if (value == null) next.remove(name) else next[name] = value }
    return next
}

internal fun fmePropertiesJson(properties: Map<String, String>) = JsonObject().apply { properties.forEach(::addProperty) }

internal fun isValidFmeProperty(name: String, value: String): Boolean = PROPERTY_TOKEN.matches(name) && PROPERTY_TOKEN.matches(value)

private val PROPERTY_TOKEN = Regex("[a-z0-9_]+")

internal const val DEFAULT_TINT_RGB = 0xFFFFFF
private val BLOCK_ID = Regex("[a-z0-9_.-]+:[a-z0-9_./-]+")
private val PROFILE_ID = Regex("default|[0-9a-fA-F-]{36}")
internal fun isValidFmeBlockId(id: String): Boolean = BLOCK_ID.matches(id)

internal const val FME_ANY_SOURCE = "*"
internal fun isValidFmeSource(id: String): Boolean = id == FME_ANY_SOURCE || BLOCK_ID.matches(id)
internal fun isValidFmeProfileId(id: String): Boolean = PROFILE_ID.matches(id)
