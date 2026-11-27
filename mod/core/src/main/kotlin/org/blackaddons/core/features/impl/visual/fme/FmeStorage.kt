package org.blackaddons.core.features.impl.visual.fme

import com.google.gson.JsonObject
import org.blackaddons.api.Platform
import org.blackaddons.api.skyblock.DungeonGeometry
import org.blackaddons.api.util.io.Gsons
import org.blackaddons.core.log.Log
import org.blackaddons.core.storage.GuardedFile
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executor

internal class FmeShared(
    val name: String,
    val positions: List<FmePositionRule>,
    val types: List<FmeTypeReplacement>,
    val favorites: List<String>,
) {
    val fitsInProfile: Boolean
        get() = positions.sumOf { it.cells.size.toLong() } <= MAX_PROFILE_CELLS &&
            positions.sumOf { it.cells.boxCount.toLong() } <= MAX_PROFILE_BOXES
}

private const val MAX_PROFILE_CELLS = 8_000_000L
private const val MAX_PROFILE_BOXES = 400_000L

private const val MAX_PROFILE_BYTES = 32L * 1024L * 1024L

internal class FmeStorage(
    private val replacements: FmeReplacementStore,
    private val typeReplacements: FmeTypeReplacementStore,
    private val writer: Executor = Executor(Runnable::run),
) {
    private val profileStore = FmeProfileStore()
    private val favorites = mutableMapOf<String, List<String>>()
    private val profileGuards = mutableMapOf<String, GuardedFile>()
    private val pendingWrites = ConcurrentHashMap<String, () -> Unit>()

    private val directory get() = Platform.configDir.resolve("fme")
    private val manifestPath get() = directory.resolve("profiles.json")
    private var manifestFile: GuardedFile? = null

    var activeProfileId: String = FmeProfileStore.DEFAULT_PROFILE_ID
        private set

    fun profiles(): List<FmeProfileHeader> = profileStore.values()

    fun activeProfile(): FmeProfileHeader = profileStore.get(activeProfileId) ?: profileStore.values().first().also {
        activeProfileId = it.id
    }

    fun favorites(profileId: String): List<String> = favorites[profileId].orEmpty()

    fun load() {
        profileGuards.clear()
        favorites.clear()
        replacements.clear()
        typeReplacements.clear()

        val guard = manifestGuard()
        val text = guard.read()
        if (text == null && guard.writable) {
            profileStore.replaceAll(listOf(DEFAULT_HEADER))
            activeProfileId = DEFAULT_HEADER.id
            saveAll()
            return
        }

        val manifest = runCatching { Gsons.pretty.fromJson(text, StoredManifest::class.java) }
            .onFailure {
                Log.main.error("Failed to read FME profile manifest {}", manifestPath, it)
                guard.refuseWrites("it could not be parsed")
            }
            .getOrNull()
        val headers = manifest?.profiles.orEmpty().mapNotNull { stored ->
            val id = stored.id?.takeIf(::isValidFmeProfileId) ?: return@mapNotNull null
            val name = stored.name?.trim()?.takeIf(String::isNotEmpty) ?: return@mapNotNull null
            FmeProfileHeader(id, name)
        }.distinctBy(FmeProfileHeader::id)
        profileStore.replaceAll(headers)
        var manifestChanged = manifest?.profiles.orEmpty().size != profileStore.values().size
        activeProfileId = manifest?.activeProfileId
            ?.takeIf { profileStore.get(it) != null }
            ?: profileStore.values().first().id.also { manifestChanged = true }

        profileStore.values().forEach { header ->
            loadProfile(header.id)?.let { loaded ->
                if (loaded.name != header.name) {
                    profileStore.rename(header.id, loaded.name)
                    manifestChanged = true
                }
            }
        }
        if (discoverSharedProfiles()) manifestChanged = true
        if (manifestChanged) saveManifest()
    }

    fun selectProfile(id: String): Boolean {
        if (profileStore.get(id) == null || activeProfileId == id) return false
        activeProfileId = id
        saveManifest()
        return true
    }

    /** Without the id. Trimmed to [positions]/[types] when sharing a part of it. */
    fun document(
        profileId: String,
        positions: List<FmePositionRule> = replacements.all(profileId),
        types: List<FmeTypeReplacement> = typeReplacements.all(profileId),
    ): JsonObject? {
        val header = profileStore.get(profileId) ?: return null
        return Gsons.pretty.toJsonTree(stored(header, favorites(profileId), positions, types)).asJsonObject.apply { remove("id") }
    }

    fun readShared(json: JsonObject): FmeShared? {
        val stored = runCatching { Gsons.pretty.fromJson(json, StoredProfile::class.java) }.getOrNull() ?: return null
        if (stored.version !in READABLE_VERSIONS) return null
        return parse(stored, profileId = "", CellBudget())
    }

    /** Replaces edits of the same block at the same place. */
    fun add(profileId: String, shared: FmeShared) {
        shared.positions.forEach { replacements.putRule(it.copy(key = it.key.copy(profileId = profileId))) }
        shared.types.forEach { typeReplacements.put(it.copy(profileId = profileId)) }
        favorites[profileId] = (favorites(profileId) + shared.favorites).distinct()
        saveProfile(profileId)
    }

    fun importProfile(shared: FmeShared): FmeProfileHeader {
        val created = profileStore.create(shared.name)
        activeProfileId = created.id
        add(created.id, shared)
        saveManifest()
        return created
    }

    fun createProfile(name: String, copyFromId: String?): FmeProfileHeader {
        val created = profileStore.create(name)
        if (copyFromId != null) {
            replacements.all(copyFromId).forEach { replacements.putRule(it.copy(key = it.key.copy(profileId = created.id))) }
            typeReplacements.all(copyFromId).forEach { typeReplacements.put(it.copy(profileId = created.id)) }
            favorites[created.id] = favorites(copyFromId)
        }
        activeProfileId = created.id
        saveProfile(created.id)
        saveManifest()
        return created
    }

    fun renameProfile(id: String, name: String): Boolean {
        if (!profileStore.rename(id, name)) return false
        saveProfile(id)
        saveManifest()
        return true
    }

    fun deleteProfile(id: String): Boolean {
        if (!profileStore.remove(id)) return false
        replacements.removeProfile(id)
        typeReplacements.removeProfile(id)
        favorites.remove(id)
        profileGuards.remove(id)
        profilePath(id)?.let { path -> write(id) { runCatching { Files.deleteIfExists(path) } } }
        if (activeProfileId == id) activeProfileId = profileStore.values().first().id
        saveManifest()
        return true
    }

    fun toggleFavorite(profileId: String, blockId: String): Boolean {
        val current = favorites(profileId)
        val added = blockId !in current
        favorites[profileId] = if (added) current + blockId else current - blockId
        saveProfile(profileId)
        return added
    }

    fun saveProfile(profileId: String) {
        val header = profileStore.get(profileId) ?: return
        val guard = profileGuard(profileId) ?: return
        val favorites = favorites(profileId)
        val positions = replacements.all(profileId)
        val types = typeReplacements.all(profileId)
        write(profileId) { guard.write(Gsons.pretty.toJson(stored(header, favorites, positions, types))) }
    }

    private fun write(profileId: String, job: () -> Unit) {
        pendingWrites[profileId] = job
        writer.execute { pendingWrites.remove(profileId)?.invoke() }
    }

    private fun stored(
        header: FmeProfileHeader,
        favorites: List<String>,
        positions: List<FmePositionRule>,
        types: List<FmeTypeReplacement>,
    ) = StoredProfile(
        version = FORMAT_VERSION,
        id = header.id,
        name = header.name,
        favorites = favorites,
        positionEdits = positions
            .sortedWith(compareBy(
                { it.key.scope }, { it.key.scopeKey }, { it.key.sourceBlockId }, { it.key.blockId },
                { it.key.ghost }, { it.key.tintRgb }, { it.key.enabled }, { it.key.properties.toString() },
            ))
            .map { it.stored() },
        typeRules = types
            .sortedWith(compareBy(FmeTypeReplacement::scope, FmeTypeReplacement::scopeKey, FmeTypeReplacement::sourceBlockId))
            .map { it.stored() },
    )

    fun saveAll() {
        profileStore.values().forEach { saveProfile(it.id) }
        saveManifest()
    }

    fun openFolder() = Platform.desktop.openPath(directory)

    private fun loadProfile(id: String): FmeProfileHeader? {
        val guard = profileGuard(id) ?: return null
        val text = guard.read() ?: return null
        val stored = runCatching { Gsons.pretty.fromJson(text, StoredProfile::class.java) }
            .onFailure {
                Log.main.error("Failed to read FME profile {}", guard.path, it)
                guard.refuseWrites("it could not be parsed")
            }
            .getOrNull() ?: return null
        val budget = CellBudget()
        val loaded = stored.takeIf { it.version in READABLE_VERSIONS && it.id == id }?.let { parse(it, id, budget) } ?: run {
            guard.refuseWrites("its header is invalid or does not match its file name")
            return null
        }
        if (budget.exceeded) guard.refuseWrites("it holds more edits than one profile may, and saving what fit would drop the rest")
        replacements.removeProfile(id)
        typeReplacements.removeProfile(id)
        loaded.positions.forEach(replacements::putRule)
        loaded.types.forEach(typeReplacements::put)
        favorites[id] = loaded.favorites
        return FmeProfileHeader(id, loaded.name)
    }

    private fun parse(stored: StoredProfile, profileId: String, budget: CellBudget): FmeShared? {
        val name = stored.name?.trim()?.takeIf(String::isNotEmpty) ?: return null
        val positions = FmeReplacementStore()
        stored.positionEdits.orEmpty().flatMap { it.rules(profileId, budget) }
            .flatMap { it.migrateBossScope() }
            .sortedBy(FmePositionRule::updatedAt)
            .forEach(positions::putRule)
        return FmeShared(
            name = name,
            positions = positions.all(profileId),
            types = stored.typeRules.orEmpty().mapNotNull { it.replacement(profileId) }
                .flatMap { it.migrateBossScope() }
                .sortedByDescending(FmeTypeReplacement::updatedAt)
                .distinctBy(FmeTypeReplacement::key),
            favorites = stored.favorites.orEmpty().filter(::isValidFmeBlockId).distinct(),
        )
    }

    private fun discoverSharedProfiles(): Boolean {
        if (!Files.isDirectory(directory)) return false
        var changed = false
        Files.list(directory).use { files ->
            files.filter { it.fileName.toString().endsWith(".json") && it.fileName.toString() != MANIFEST_NAME }
                .forEach { path ->
                    val id = path.fileName.toString().removeSuffix(".json")
                    if (!isValidFmeProfileId(id) || profileStore.get(id) != null) return@forEach
                    val header = loadProfile(id) ?: return@forEach
                    if (profileStore.add(header)) changed = true
                }
        }
        return changed
    }

    private fun saveManifest() {
        val document = StoredManifest(
            activeProfileId = activeProfileId,
            profiles = profileStore.values().map { StoredHeader(it.id, it.name) },
        )
        manifestGuard().write(Gsons.pretty.toJson(document))
    }

    private fun manifestGuard(): GuardedFile = manifestFile
        ?.takeIf { it.path == manifestPath }
        ?: GuardedFile(manifestPath).also { manifestFile = it }

    private fun profileGuard(id: String): GuardedFile? {
        val path = profilePath(id) ?: return null
        return profileGuards[id]?.takeIf { it.path == path } ?: GuardedFile(path, MAX_PROFILE_BYTES).also { profileGuards[id] = it }
    }

    private fun profilePath(id: String): Path? = if (isValidFmeProfileId(id)) directory.resolve("$id.json") else null

    private data class StoredManifest(
        val activeProfileId: String? = null,
        val profiles: List<StoredHeader>? = null,
    )

    private data class StoredHeader(val id: String? = null, val name: String? = null)

    private data class StoredProfile(
        val version: Int? = null,
        val id: String? = null,
        val name: String? = null,
        val favorites: List<String>? = null,
        val positionEdits: List<StoredPositionEdit>? = null,
        val typeRules: List<StoredTypeRule>? = null,
    )

    private data class StoredPositionEdit(
        val scope: String? = null,
        val scopeKey: String? = null,
        val sourceBlock: String? = null,
        val replacementBlock: String? = null,
        val ghost: Boolean? = null,
        val tint: String? = null,
        val enabled: Boolean? = null,
        val updatedAt: Long? = null,
        val state: JsonObject? = null,
        val cells: List<String>? = null,
        val positions: List<StoredPosition>? = null,
    ) {
        fun rules(profileId: String, budget: CellBudget): List<FmePositionRule> {
            val parsedScope = scope?.let { runCatching { FmeScope.valueOf(it) }.getOrNull() }
                ?.takeIf { it != FmeScope.GLOBAL } ?: return emptyList()
            val key = FmeRuleKey(
                profileId = profileId,
                scope = parsedScope,
                scopeKey = scopeKey ?: return emptyList(),
                sourceBlockId = sourceBlock?.takeIf(::isValidFmeSource) ?: return emptyList(),
                blockId = replacementBlock?.takeIf(::isValidFmeBlockId) ?: return emptyList(),
                ghost = ghost ?: false,
                tintRgb = parseTint(tint) ?: DEFAULT_TINT_RGB,
                enabled = enabled ?: true,
                properties = state?.let(::parseFmeProperties).orEmpty(),
            )
            val rules = positions.orEmpty()
                .groupBy { it.state?.let(::parseFmeProperties).orEmpty() }
                .map { (properties, placed) ->
                    val cells = FmeCells.Builder()
                    placed.forEach { position ->
                        if (budget.take(cells = 1, boxes = 1)) {
                            cells.add(position.x ?: return@forEach, position.y ?: return@forEach, position.z ?: return@forEach)
                        }
                    }
                    FmePositionRule(key.copy(properties = key.properties + properties), cells.build(), updatedAt ?: 0L)
                }
            val boxes = FmeCells.Builder()
            cells.orEmpty().forEach { text -> parseCell(text)?.let { box -> if (budget.take(box.volume, boxes = 1)) box.addTo(boxes) } }
            return (rules + FmePositionRule(key, boxes.build(), updatedAt ?: 0L)).filterNot { it.cells.isEmpty() }
        }
    }

    private data class StoredPosition(
        val x: Int? = null,
        val y: Int? = null,
        val z: Int? = null,
        val state: JsonObject? = null,
    )

    private data class StoredTypeRule(
        val scope: String? = null,
        val scopeKey: String? = null,
        val sourceBlock: String? = null,
        val replacementBlock: String? = null,
        val tint: String? = null,
        val enabled: Boolean? = null,
        val updatedAt: Long? = null,
        val state: JsonObject? = null,
    ) {
        fun replacement(profileId: String): FmeTypeReplacement? {
            val parsedScope = scope?.let { runCatching { FmeScope.valueOf(it) }.getOrNull() } ?: return null
            val source = sourceBlock?.takeIf(::isValidFmeSource) ?: return null
            val replacement = replacementBlock?.takeIf(::isValidFmeSource) ?: return null
            return FmeTypeReplacement(
                profileId = profileId,
                scope = parsedScope,
                scopeKey = scopeKey.orEmpty(),
                sourceBlockId = source,
                blockId = replacement,
                tintRgb = parseTint(tint) ?: DEFAULT_TINT_RGB,
                enabled = enabled ?: true,
                updatedAt = updatedAt ?: 0L,
                properties = state?.let(::parseFmeProperties).orEmpty(),
            )
        }
    }

    private fun FmePositionRule.stored() = StoredPositionEdit(
        scope = key.scope.name,
        scopeKey = key.scopeKey,
        sourceBlock = key.sourceBlockId,
        replacementBlock = key.blockId,
        ghost = key.ghost.takeIf { it },
        tint = key.tintRgb.takeUnless { it == DEFAULT_TINT_RGB }?.hex(),
        enabled = key.enabled.takeUnless { it },
        updatedAt = updatedAt.takeUnless { it == 0L },
        state = key.properties.takeIf(Map<String, String>::isNotEmpty)?.let(::fmePropertiesJson),
        cells = buildList {
            cells.forEachBox { minX, minY, minZ, maxX, maxY, maxZ ->
                add(if (minX == maxX && minY == maxY && minZ == maxZ) "$minX,$minY,$minZ" else "$minX,$minY,$minZ,$maxX,$maxY,$maxZ")
            }
        },
    )

    private fun FmeTypeReplacement.stored() = StoredTypeRule(
        scope = scope.name,
        scopeKey = scopeKey.takeIf(String::isNotEmpty),
        sourceBlock = sourceBlockId,
        replacementBlock = blockId,
        tint = tintRgb.takeUnless { it == DEFAULT_TINT_RGB }?.hex(),
        enabled = enabled.takeUnless { it },
        updatedAt = updatedAt.takeUnless { it == 0L },
        state = properties.takeIf(Map<String, String>::isNotEmpty)?.let(::fmePropertiesJson),
    )

    private class CellBudget {
        private var cellsLeft = MAX_PROFILE_CELLS
        private var boxesLeft = MAX_PROFILE_BOXES
        var exceeded = false
            private set

        fun take(cells: Long, boxes: Long): Boolean {
            if (cells > cellsLeft || boxes > boxesLeft) {
                exceeded = true
                return false
            }
            cellsLeft -= cells
            boxesLeft -= boxes
            return true
        }
    }

    private class StoredBox(val minX: Int, val minY: Int, val minZ: Int, val maxX: Int, val maxY: Int, val maxZ: Int) {
        val volume: Long get() = (maxX - minX + 1L) * (maxY - minY + 1L) * (maxZ - minZ + 1L)

        fun addTo(cells: FmeCells.Builder) = cells.addBox(minX, minY, minZ, maxX, maxY, maxZ)
    }

    private companion object {
        const val FORMAT_VERSION = 2
        val READABLE_VERSIONS = 1..FORMAT_VERSION
        const val MANIFEST_NAME = "profiles.json"
        val DEFAULT_HEADER = FmeProfileHeader(FmeProfileStore.DEFAULT_PROFILE_ID, "Default")

        fun Int.hex(): String = "#%06X".format(this and DEFAULT_TINT_RGB)
        fun parseTint(value: String?): Int? = value?.takeIf { it.matches(Regex("#[0-9A-Fa-f]{6}")) }?.drop(1)?.toInt(16)

        fun parseCell(text: String): StoredBox? {
            val values = text.split(',').map { it.trim().toIntOrNull() ?: return null }
            return when (values.size) {
                3 -> StoredBox(values[0], values[1], values[2], values[0], values[1], values[2])
                6 -> StoredBox(
                    minOf(values[0], values[3]), minOf(values[1], values[4]), minOf(values[2], values[5]),
                    maxOf(values[0], values[3]), maxOf(values[1], values[4]), maxOf(values[2], values[5]),
                )
                else -> null
            }
        }
    }
}

/**
 * Boss rooms used to be dungeon rooms (`Boss: F7`, `Boss: M7 P3`, `Boss: P3Sim`). Now they're locations, with Floor and
 * Master merged, so the newest edit wins.
 *
 * TODO: delete on release, together with the calls in [FmeStorage.parse], [legacyBoss] and its test.
 */
private fun FmePositionRule.migrateBossScope(): List<FmePositionRule> {
    val (floor, phase) = legacyBoss(key.scope, key.scopeKey) ?: return listOf(this)
    fun at(f7Phase: Int?, cells: FmeCells) =
        FmePositionRule(key.copy(scope = FmeScope.LOCATION, scopeKey = fmeBossLocationKey(floor, f7Phase)), cells, updatedAt)
    if (floor != 7 || phase != null) return listOf(at(phase, cells))
    return cells.partition { _, y, _ -> DungeonGeometry.f7Phase(y.toDouble()).number }.map { (f7Phase, cells) -> at(f7Phase, cells) }
}

/** There's no altitude to pick a phase, so it goes to every phase. */
private fun FmeTypeReplacement.migrateBossScope(): List<FmeTypeReplacement> {
    val (floor, phase) = legacyBoss(scope, scopeKey) ?: return listOf(this)
    val phases = if (floor == 7 && phase == null) F7_PHASES.toList() else listOf(phase)
    return phases.map { copy(scope = FmeScope.LOCATION, scopeKey = fmeBossLocationKey(floor, it)) }
}

private fun legacyBoss(scope: FmeScope, scopeKey: String): Pair<Int, Int?>? {
    if (scope != FmeScope.DUNGEON) return null
    val match = LEGACY_BOSS_KEY.matchEntire(scopeKey) ?: return null
    return (match.groupValues[1].toIntOrNull() ?: 7) to match.groupValues[2].toIntOrNull()
}

private val LEGACY_BOSS_KEY = Regex("""Boss: (?:[FM](\d)|P3Sim)(?: P(\d))?""")
