package org.blackaddons.core.features.impl.visual.fme

import org.blackaddons.api.Platform
import org.blackaddons.api.host.Cancellation

internal class FmeRules(
    private val replacements: FmeReplacementStore,
    private val typeReplacements: FmeTypeReplacementStore,
    private val profileId: () -> String,
    private val isKnownBlock: (String) -> Boolean,
    private val snapshot: () -> FmeSnapshot,
    private val remember: (FmeSnapshot) -> Unit,
    private val persistAll: () -> Unit,
    private val persistReplacements: () -> Unit,
    private val persistTypeReplacements: () -> Unit,
    private val refreshRendering: () -> Unit,
) {
    private val cache = mutableMapOf<Pair<String, FmeScope>, List<FmeManagedGroup>>()
    private var tintApply: Cancellation? = null

    fun invalidate() = cache.clear()

    fun groups(scope: FmeScope, scopeKey: String): List<FmeManagedGroup> = groups(scope)
        .filter { it.scopeKey == scopeKey }

    fun setTypeReplacement(scope: FmeScope, scopeKey: String, from: String, to: String): Boolean {
        cancelPendingTint()
        if (scope != FmeScope.GLOBAL && scopeKey.isBlank()) return false
        if (!isKnownBlock(from) || !isKnownBlock(to)) return false
        val before = snapshot()
        val context = FmeContext(scope, scopeKey)
        val existing = typeReplacements.get(profileId(), context, from)
        val changed = typeReplacements.put(
            FmeTypeReplacement(
                profileId = profileId(), scope = scope, scopeKey = scopeKey,
                sourceBlockId = from, blockId = to,
                tintRgb = existing?.tintRgb ?: DEFAULT_TINT_RGB,
                enabled = existing?.enabled ?: true,
                updatedAt = System.currentTimeMillis(),
            ),
        )
        if (!changed) return false
        invalidate()
        remember(before)
        persistTypeReplacements()
        refreshRendering()
        return true
    }

    fun clearProfile(): Int {
        cancelPendingTint()
        val id = profileId()
        val count = replacements.size(id) + typeReplacements.size(id)
        if (count == 0) return 0
        val before = snapshot()
        replacements.clearProfile(id)
        typeReplacements.clearProfile(id)
        invalidate()
        remember(before)
        persistAll()
        refreshRendering()
        return count
    }

    fun clearContext(scope: FmeScope, scopeKey: String): Int {
        cancelPendingTint()
        val context = FmeContext(scope, scopeKey)
        val before = snapshot()
        val removedPositions = replacements.clear(profileId(), context).sumOf { it.cells.size }
        val removedTypes = typeReplacements.entries(profileId(), scope).count { it.scopeKey == scopeKey }
        typeReplacements.clear(profileId(), context)
        val count = removedPositions + removedTypes
        if (count == 0) return 0
        invalidate()
        remember(before)
        persistAll()
        refreshRendering()
        return count
    }

    /** Replaces [toKey]'s edits for the same block or place. */
    fun copyContext(scope: FmeScope, fromKey: String, toKey: String): Int {
        if (scope == FmeScope.GLOBAL || fromKey == toKey || toKey.isBlank()) return 0
        cancelPendingTint()
        val types = typeReplacements.entries(profileId(), scope).filter { it.scopeKey == fromKey }
        val positions = replacements.rules(profileId(), scope).filter { it.key.scopeKey == fromKey }
        if (types.isEmpty() && positions.isEmpty()) return 0
        val before = snapshot()
        val now = System.currentTimeMillis()
        types.forEach { typeReplacements.put(it.copy(scopeKey = toKey, updatedAt = now)) }
        positions.forEach { replacements.putRule(it.copy(key = it.key.copy(scopeKey = toKey), updatedAt = now)) }
        invalidate()
        remember(before)
        persistAll()
        refreshRendering()
        return types.size + positions.sumOf { it.cells.size }
    }

    fun setTint(group: FmeManagedGroup, tintRgb: Int, recordHistory: Boolean): Boolean {
        val normalized = tintRgb and DEFAULT_TINT_RGB
        val before = if (recordHistory) snapshot() else null
        val changed = when (group.kind) {
            FmeManagedKind.TYPE -> group.typeKey?.let { typeReplacements.updateTint(it, normalized) } == true
            FmeManagedKind.POSITION -> replacements.updateTint(group.ruleKeys, normalized)
        }
        if (!changed) return false
        invalidate()
        if (before != null) remember(before)
        tintApply?.cancel()
        tintApply = Platform.scheduler.schedule(TINT_APPLY_DELAY) {
            tintApply = null
            persistAll()
            refreshRendering()
        }
        return true
    }

    fun remove(group: FmeManagedGroup): Boolean {
        cancelPendingTint()
        val before = snapshot()
        val changed = when (group.kind) {
            FmeManagedKind.TYPE -> group.typeKey?.let(typeReplacements::remove) == true
            FmeManagedKind.POSITION -> replacements.removeRules(group.ruleKeys).isNotEmpty()
        }
        if (!changed) return false
        invalidate()
        remember(before)
        persistAll()
        refreshRendering()
        return true
    }

    fun setEnabled(group: FmeManagedGroup, enabled: Boolean): Boolean {
        cancelPendingTint()
        val before = snapshot()
        val changed = when (group.kind) {
            FmeManagedKind.TYPE -> group.typeKey?.let { typeReplacements.updateEnabled(it, enabled) } == true
            FmeManagedKind.POSITION -> replacements.updateEnabled(group.ruleKeys, enabled)
        }
        if (!changed) return false
        invalidate()
        remember(before)
        persistAll()
        refreshRendering()
        return true
    }

    fun setPositionReplacement(group: FmeManagedGroup, blockId: String): Boolean {
        if (group.kind != FmeManagedKind.POSITION || !isKnownBlock(blockId)) return false
        cancelPendingTint()
        val before = snapshot()
        if (!replacements.updateBlock(group.ruleKeys, blockId)) return false
        invalidate()
        remember(before)
        persistReplacements()
        refreshRendering()
        return true
    }

    fun matchAnyBlock(group: FmeManagedGroup): Boolean {
        if (group.kind != FmeManagedKind.POSITION || group.ghost) return false
        cancelPendingTint()
        val before = snapshot()
        if (!replacements.updateSource(group.ruleKeys, FME_ANY_SOURCE)) return false
        invalidate()
        remember(before)
        persistReplacements()
        refreshRendering()
        return true
    }

    fun transferType(group: FmeManagedGroup, targetScope: FmeScope, targetScopeKey: String, move: Boolean): Boolean {
        if (group.kind != FmeManagedKind.TYPE || (targetScope != FmeScope.GLOBAL && targetScopeKey.isBlank())) return false
        val targetKey = FmeTypeKey(profileId(), targetScope, targetScopeKey, group.sourceBlockId)
        if (targetKey == group.typeKey) return false
        cancelPendingTint()
        val before = snapshot()
        typeReplacements.put(
            FmeTypeReplacement(
                profileId = profileId(), scope = targetScope, scopeKey = targetScopeKey,
                sourceBlockId = group.sourceBlockId, blockId = group.blockId,
                tintRgb = group.tintRgb, enabled = group.enabled, updatedAt = System.currentTimeMillis(),
            ),
        )
        if (move) group.typeKey?.let(typeReplacements::remove)
        invalidate()
        remember(before)
        persistTypeReplacements()
        refreshRendering()
        return true
    }

    fun removePosition(key: FmePositionKey): Boolean {
        cancelPendingTint()
        val before = snapshot()
        val removed = replacements.remove(key) ?: return false
        invalidate()
        remember(before)
        persistReplacements()
        refreshRendering()
        return true
    }

    fun movePosition(key: FmePositionKey, x: Int, y: Int, z: Int): FmePositionMoveResult {
        cancelPendingTint()
        val existing = replacements.get(key) ?: return FmePositionMoveResult.MISSING
        val targetKey = key.copy(x = x, y = y, z = z)
        if (targetKey == key) return FmePositionMoveResult.UNCHANGED
        if (replacements.get(targetKey) != null) return FmePositionMoveResult.OCCUPIED

        val before = snapshot()
        replacements.remove(key)
        val moved = existing.copy(x = x, y = y, z = z, updatedAt = System.currentTimeMillis())
        replacements.put(moved)
        invalidate()
        remember(before)
        persistReplacements()
        refreshRendering()
        return FmePositionMoveResult.MOVED
    }

    fun addPosition(group: FmeManagedGroup, x: Int, y: Int, z: Int, anyBlock: Boolean): FmePositionAddResult {
        cancelPendingTint()
        if (group.kind != FmeManagedKind.POSITION) return FmePositionAddResult.MISSING
        val template = group.ruleKeys.firstOrNull { replacements.rule(it) != null }
            ?.let { if (anyBlock && !it.ghost) it.copy(sourceBlockId = FME_ANY_SOURCE) else it }
            ?: return FmePositionAddResult.MISSING
        val targetKey = FmePositionKey(template.profileId, template.scope, template.scopeKey, x, y, z)
        if (replacements.get(targetKey) != null) return FmePositionAddResult.OCCUPIED

        val before = snapshot()
        replacements.put(template.at(x, y, z, System.currentTimeMillis()))
        invalidate()
        remember(before)
        persistReplacements()
        refreshRendering()
        return FmePositionAddResult.ADDED
    }

    fun cancelPendingTint(): Boolean {
        if (tintApply == null) return false
        tintApply?.cancel()
        tintApply = null
        persistAll()
        refreshRendering()
        return true
    }

    private fun groups(scope: FmeScope): List<FmeManagedGroup> = cache.getOrPut(profileId() to scope) {
        val types = typeReplacements.entries(profileId(), scope).map { replacement ->
            FmeManagedGroup(
                kind = FmeManagedKind.TYPE, scope = replacement.scope, scopeKey = replacement.scopeKey,
                sourceBlockId = replacement.sourceBlockId, blockId = replacement.blockId, ghost = false,
                tintRgb = replacement.tintRgb, enabled = replacement.enabled, updatedAt = replacement.updatedAt,
                typeKey = replacement.key,
            )
        }
        val positions = replacements.rules(profileId(), scope)
            .groupBy { it.key.copy(properties = emptyMap()) }
            .map { (first, grouped) ->
                FmeManagedGroup(
                    kind = FmeManagedKind.POSITION, scope = first.scope, scopeKey = first.scopeKey,
                    sourceBlockId = first.sourceBlockId, blockId = first.blockId, ghost = first.ghost,
                    tintRgb = first.tintRgb, enabled = first.enabled,
                    updatedAt = grouped.maxOf(FmePositionRule::updatedAt),
                    ruleKeys = grouped.mapTo(linkedSetOf(), FmePositionRule::key),
                    positions = grouped.sumOf { it.cells.size },
                )
            }
        (types + positions).sortedByDescending(FmeManagedGroup::updatedAt)
    }

    private companion object {
        const val TINT_APPLY_DELAY = 12
    }
}
