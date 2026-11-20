package org.blackaddons.core.features.impl.visual.fme

import org.blackaddons.api.Platform
import org.blackaddons.api.skyblock.LocationUtils

/* Fme's undo history, saving and render refresh. */

internal fun Fme.hasDungeonRules(): Boolean {
    val profileId = activeProfile().id
    return replacements.rules(profileId, FmeScope.DUNGEON).isNotEmpty() || typeReplacements.entries(profileId, FmeScope.DUNGEON).isNotEmpty()
}

internal fun Fme.snapshot() = FmeSnapshot(replacements.snapshot(), typeReplacements.lines())

internal fun Fme.remember(before: FmeSnapshot) {
    if (before == snapshot()) return
    undoHistory.addLast(before)
    while (undoHistory.size > HISTORY_LIMIT) undoHistory.removeFirst()
    redoHistory.clear()
}

internal fun Fme.restore(target: FmeSnapshot) {
    replacements.restore(target.replacements)
    typeReplacements.replaceAll(target.typeReplacements)
    rules.invalidate()
    persistAll()
    refreshRendering()
}

internal fun Fme.persistReplacements() {
    storage.saveProfile(activeProfile().id)
}

internal fun Fme.persistTypeReplacements() {
    storage.saveProfile(activeProfile().id)
}

internal fun Fme.persistAll() {
    storage.saveProfile(activeProfile().id)
}

internal fun Fme.refreshRendering(context: FmeActiveContext = activeContext(), reusePreloaded: Boolean = false) {
    val previous = renderIndex
    val previousContext = lastRenderContext
    if (!reusePreloaded) dropPreloaded()
    renderIndex = if (renderingEnabled) {
        preloaded.remove(context) ?: buildRenderIndex(context)
    } else {
        FmeRenderIndex.EMPTY
    }
    if (reusePreloaded && renderingEnabled && previousContext != null) preloaded[previousContext] = previous
    lastRenderContext = context
    redraw(renderIndex.invalidationFrom(previous))
    if (renderingEnabled) preloadNeighbours(context)
}

internal fun Fme.buildRenderIndex(context: FmeActiveContext) =
    buildFmeRenderIndex(activeProfile().id, replacements, typeReplacements, context)

internal fun Fme.preloadNeighbours(context: FmeActiveContext) {
    val neighbours = neighbourContexts(context)
    preloaded.keys.retainAll(neighbours.toSet())
    preloading?.cancel()
    preloading = null
    val missing = neighbours.filterNot(preloaded::containsKey)
    fun next(index: Int) {
        if (index >= missing.size) return
        preloading = Platform.scheduler.schedule(PRELOAD_INTERVAL_TICKS) {
            preloading = null
            if (lastRenderContext != context || !renderingEnabled) return@schedule
            preloaded.getOrPut(missing[index]) { buildRenderIndex(missing[index]) }
            next(index + 1)
        }
    }
    next(0)
}

internal fun Fme.neighbourContexts(context: FmeActiveContext): List<FmeActiveContext> {
    if (!context.inDungeon) return emptyList()
    val floor = if (LocationUtils.hasF7Boss) 7 else LocationUtils.dungeonFloorNumber ?: return emptyList()
    if (context.location != null) return emptyList()
    return listOf(FmeActiveContext(fmeBossLocationKey(floor, if (floor == 7) 1 else null), inDungeon = true))
}

internal fun Fme.dropPreloaded() {
    preloading?.cancel()
    preloading = null
    preloaded.clear()
}

internal fun Fme.redraw(invalidation: FmeRenderInvalidation) {
    if (!Platform.world.isLoaded) return
    if (invalidation.blockTypes.isNotEmpty()) Platform.world.redrawBlockTypes(invalidation.blockTypes)
    invalidation.chunks.forEach { (chunkX, chunkZ) -> Platform.world.redrawChunk(chunkX, chunkZ) }
    invalidation.blocks.forEach { pos -> Platform.world.redrawBlock(pos.x, pos.y, pos.z) }
}

internal fun Fme.activeContext() = FmeActiveContext(
    worldEditor.currentLocationKey()?.let { fmePositionLocations(it).first() },
    LocationUtils.inDungeon,
)

internal fun Fme.renderEditTarget() {
    if (!editMode.value || Platform.screenHost.anyScreenOpen) return
    worldEditor.renderTarget()
}

internal fun Fme.cancelTintApply() {
    rules.cancelPendingTint()
}

internal fun Fme.normalizeBlockId(id: String): String = if (':' in id) id.lowercase() else "minecraft:${id.lowercase()}"
