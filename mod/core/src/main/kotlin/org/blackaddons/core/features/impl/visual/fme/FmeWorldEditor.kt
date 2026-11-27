package org.blackaddons.core.features.impl.visual.fme

import org.blackaddons.api.Platform
import org.blackaddons.api.input.Keys
import org.blackaddons.api.player.BlockFace
import org.blackaddons.api.player.RaycastHit
import org.blackaddons.api.skyblock.DungeonGeometry
import org.blackaddons.api.skyblock.LocationUtils
import org.blackaddons.api.ui.layout.LayoutNode
import org.blackaddons.api.ui.layout.column
import org.blackaddons.api.ui.render.Render
import org.blackaddons.api.ui.theme.Fonts
import org.blackaddons.api.ui.theme.TextStyle
import org.blackaddons.api.ui.theme.Theme
import org.blackaddons.api.ui.theme.Theme.withAlpha
import org.blackaddons.api.ui.toast.ToastKind
import org.blackaddons.api.ui.toast.Toasts
import org.blackaddons.api.util.I18n
import org.blackaddons.api.world.BlockCatalogEntry
import org.blackaddons.api.world.BlockPos3
import org.blackaddons.api.world.Vec3d
import org.blackaddons.core.dungeon.map.MapState
import org.blackaddons.core.dungeon.map.RoomTransform
import java.awt.Color

internal class FmeWorldEditor(
    private val replacements: FmeReplacementStore,
    private val profileId: () -> String,
    private val activeScope: () -> FmeScope,
    private val selectedBlockId: () -> String,
    private val selectedProperties: () -> Map<String, String>,
    private val anyBlock: () -> Boolean,
    private val scopeLabel: (FmeScope) -> String,
    private val renderIndex: () -> FmeRenderIndex,
    private val blockEntry: (String) -> BlockCatalogEntry?,
    private val snapshot: () -> FmeSnapshot,
    private val beforeMutation: () -> Unit,
    private val selectBlockState: (String, Map<String, String>) -> Boolean,
    private val onReplacementChanged: (FmeSnapshot) -> Unit,
) {
    fun handleClick(button: Int) {
        val target = editableTarget() ?: return
        when (button) {
            Keys.MOUSE_LEFT -> remove(target.pos.x, target.pos.y, target.pos.z)
            Keys.MOUSE_RIGHT -> put(target, selectedBlockId())
        }
    }

    fun placeGhostBlock() {
        val target = editableTarget() ?: return
        if (!placeVisualBlock(target)) {
            Toasts.show(tr("position_blocked", "A visual block cannot be placed there."), kind = ToastKind.ERROR)
        }
    }

    /**
     * Picks by what's drawn, so clicking an edited block picks its replacement. [withState] copies its drawn state too.
     */
    fun pickBlock(withState: Boolean) {
        val target = pickTarget() ?: return
        val appearance = Fme.appearanceAt(
            target.pos.x, target.pos.y, target.pos.z, if (target.ghost) AIR_ID else target.blockId, originalIsAir = target.ghost,
        )
        val shown = if (target.ghost) target.blockId else appearance?.blockId ?: target.blockId
        if (!withState) {
            selectBlockState(shown, emptyMap())
            return
        }
        val real = if (target.ghost) emptyMap() else Platform.world.blockProperties(target.pos.x, target.pos.y, target.pos.z)
        selectBlockState(shown, real + appearance?.properties.orEmpty())
    }

    /** In a dungeon, only the boss room counts as a location. */
    fun currentLocationKey(): String? {
        if (LocationUtils.inDungeon) return currentFmeBossLocationKey()
        return LocationUtils.area?.trim()?.takeIf(String::isNotEmpty)
            ?: Platform.world.serverAddress?.trim()?.takeIf(String::isNotEmpty)
    }

    fun roomAt(x: Int, z: Int): FmeResolvedRoom? {
        if (!LocationUtils.inDungeon) return null
        val room = fmeRoomAt(x, z) ?: return null
        return FmeResolvedRoom(room.name, room.transform)
    }

    fun currentDungeonKey(): String? = roomAt(Platform.player.x.toInt(), Platform.player.z.toInt())?.name

    fun positionAt(x: Int, y: Int, z: Int): FmeReplacement? {
        val room = roomAt(x, z)
        if (room?.transform != null) {
            val (localX, localZ) = room.transform.toLocal(x, z)
            replacements.get(profileId(), FmeContext(FmeScope.DUNGEON, room.name), localX, y, localZ)?.let { return it }
        }
        val location = currentLocationKey() ?: return null
        return fmePositionLocations(location)
            .mapNotNull { drawn -> replacements.get(profileId(), FmeContext(FmeScope.LOCATION, drawn), x, y, z) }
            .maxByOrNull { fmeOwnsHeight(it.scopeKey, y) }
    }

    fun nearestPosition(group: FmeManagedGroup): FmePositionKey? {
        var nearest: FmePositionKey? = null
        var nearestSq = Double.MAX_VALUE
        group.ruleKeys.mapNotNull(replacements::rule).forEach { rule ->
            val toWorld = worldTransform(rule.key.scope, rule.key.scopeKey) ?: return@forEach
            rule.cells.forEach { x, y, z ->
                val (worldX, worldZ) = toWorld(x, z)
                val dx = worldX - Platform.player.x
                val dy = y - Platform.player.y
                val dz = worldZ - Platform.player.z
                val distanceSq = dx * dx + dy * dy + dz * dz
                if (distanceSq < nearestSq) {
                    nearestSq = distanceSq
                    nearest = FmePositionKey(rule.key.profileId, rule.key.scope, rule.key.scopeKey, x, y, z)
                }
            }
        }
        return nearest
    }

    fun quarterTurns(scope: FmeScope, scopeKey: String): Int {
        if (scope != FmeScope.DUNGEON) return 0
        val room = MapState.uniqueRooms[scopeKey] ?: MapState.uniqueRooms.values.find { it.name == scopeKey }
        return room?.transform?.quarterTurns ?: 0
    }

    fun worldPosition(key: FmePositionKey): BlockPos3? = worldTransform(key.scope, key.scopeKey)
        ?.invoke(key.x, key.z)
        ?.let { (x, z) -> BlockPos3(x, key.y, z) }

    private fun worldTransform(scope: FmeScope, scopeKey: String): ((Int, Int) -> Pair<Int, Int>)? = when (scope) {
        FmeScope.GLOBAL -> null
        FmeScope.LOCATION -> if (currentLocationKey()?.let(::fmePositionLocations)?.contains(scopeKey) == true) { x, z -> x to z } else null
        FmeScope.DUNGEON -> {
            val room = MapState.uniqueRooms[scopeKey] ?: MapState.uniqueRooms.values.find { it.name == scopeKey }
            room?.transform?.let { transform -> transform::toWorld }
        }
    }

    fun renderTarget() {
        val target = pickTarget() ?: return
        val valid = activeScope() != FmeScope.GLOBAL && currentScopeKey(activeScope()) != null
        val color = when {
            !valid -> TARGET_INVALID
            target.ghost -> TARGET_VISUAL
            else -> TARGET_REAL
        }
        Platform.worldRenderer.boxEdges(
            target.pos.x - TARGET_PAD, target.pos.y - TARGET_PAD, target.pos.z - TARGET_PAD,
            target.pos.x + 1.0 + TARGET_PAD, target.pos.y + 1.0 + TARGET_PAD, target.pos.z + 1.0 + TARGET_PAD,
            color, thickness = 2f, throughBlocks = false, depthPriority = true,
        )
    }

    fun indicator(): LayoutNode {
        val selected = selectedBlockId()
        val entry = blockEntry(selected)
        val name = selected.substringAfter(':').split('_').joinToString(" ") { word -> word.replaceFirstChar { it.uppercase() } }
        val scope = scopeLabel(activeScope())
        val mode = if (anyBlock()) tr("any_block", "Any block") else null
        val state = selectedProperties().entries.joinToString(", ") { "${it.key}=${it.value}" }.takeIf(String::isNotEmpty)
        val nameW = Render.textWidth(name, Theme.FONT_SMALL, Fonts.medium)
        val scopeW = Render.textWidth(scope, Theme.FONT_SMALL, Fonts.regular)
        val modeW = mode?.let { Render.textWidth(it, Theme.FONT_SMALL, Fonts.medium) } ?: 0f
        val stateW = state?.let { Render.textWidth(it, Theme.FONT_SMALL, Fonts.regular) } ?: 0f
        val w = maxOf(nameW, scopeW, modeW, stateW) + if (entry == null) 16f else 38f
        val h = 34f + (if (mode == null) 0f else 13f) + (if (state == null) 0f else 13f)
        return column {
            cell(w, h) { x, y ->
                Render.rect(x, y, w, h, Theme.BACKGROUND.withAlpha(205), Theme.RADIUS_SMALL)
                if (entry != null) Render.item(entry.itemId, x + 6f, y + 7f, 20f, 20f, Theme.RADIUS_SMALL)
                val textX = x + if (entry == null) 8f else 32f
                Render.text(name, textX, y + 6f, TextStyle.SMALL_PRIMARY_MEDIUM)
                Render.text(scope, textX, y + 19f, TextStyle.SMALL_MUTED)
                if (mode != null) Render.text(mode, textX, y + 32f, Theme.FONT_SMALL, Theme.ACCENT, Fonts.medium)
                if (state != null) Render.text(state, textX, y + if (mode == null) 32f else 45f, TextStyle.SMALL_MUTED)
            }
        }
    }

    private fun currentScopeKey(scope: FmeScope): String? = when (scope) {
        FmeScope.GLOBAL -> ""
        FmeScope.LOCATION -> currentLocationKey()
        FmeScope.DUNGEON -> currentDungeonKey()
    }

    private fun editableTarget(): FmeTarget? {
        val target = pickTarget() ?: return null
        if (activeScope() == FmeScope.GLOBAL || currentScopeKey(activeScope()) == null) {
            Toasts.show(tr("scope_unavailable", "The active FME edit scope is unavailable."), kind = ToastKind.ERROR)
            return null
        }
        return target
    }

    private fun pickTarget(): FmeTarget? {
        val worldHit = Platform.raycast.lookingAtCamera(PICK_DISTANCE, includeEntities = false) as? RaycastHit.Block
        val normalTarget = worldHit?.let { FmeTarget(it.pos, it.blockId, it.face, false, it.distanceSq, it.point) }
        val ray = Platform.raycast.cameraRay(PICK_DISTANCE) ?: return normalTarget
        val ghostTarget = renderIndex().ghosts.asSequence()
            .mapNotNull { replacement -> rayBoxHit(ray, replacement.pos)?.let { replacement to it } }
            .minByOrNull { it.second.distanceSq }
            ?.let { (replacement, hit) -> FmeTarget(replacement.pos, replacement.blockId, hit.face, true, hit.distanceSq, hit.point) }
        return if (ghostTarget != null && ghostTarget.distanceSq <= (normalTarget?.distanceSq ?: Double.MAX_VALUE)) ghostTarget else normalTarget
    }

    private fun placeVisualBlock(target: FmeTarget): Boolean {
        val pos = BlockPos3(target.pos.x + target.face.offsetX, target.pos.y + target.face.offsetY, target.pos.z + target.face.offsetZ)
        if (Platform.world.blockIdAt(pos.x, pos.y, pos.z) != AIR_ID) return false
        return put(target.copy(pos = pos, blockId = AIR_ID, ghost = true), selectedBlockId())
    }

    private fun put(target: FmeTarget, selected: String): Boolean {
        beforeMutation()
        val placement = Platform.world.placementAt(selected, target.pos, target.face, target.point)
        val blockId = placement?.blockId ?: selected
        // Pinned keys win. A wall variant only keeps the ones it has.
        val names = Platform.world.blockStateProperties(blockId).mapTo(HashSet()) { it.name }
        val properties = placement?.properties.orEmpty() + selectedProperties().filterKeys(names::contains)
        val stored = when (val scope = activeScope()) {
            FmeScope.GLOBAL -> return false
            FmeScope.LOCATION -> FmeReplacement(
                profileId = profileId(), scope = scope, scopeKey = currentLocationKey() ?: return false,
                x = target.pos.x, y = target.pos.y, z = target.pos.z,
                sourceBlockId = target.source(),
                blockId = blockId, ghost = target.ghost, updatedAt = System.currentTimeMillis(),
                properties = properties,
            )
            FmeScope.DUNGEON -> {
                val room = roomAt(target.pos.x, target.pos.z) ?: return false
                val transform = room.transform ?: return false
                val (localX, localZ) = transform.toLocal(target.pos.x, target.pos.z)
                FmeReplacement(
                    profileId = profileId(), scope = scope, scopeKey = room.name,
                    x = localX, y = target.pos.y, z = localZ,
                    sourceBlockId = target.source(),
                    blockId = blockId, ghost = target.ghost, updatedAt = System.currentTimeMillis(),
                    properties = Platform.world.rotateProperties(blockId, properties, -transform.quarterTurns),
                )
            }
        }
        return putStored(stored, target)
    }

    private fun putStored(stored: FmeReplacement, target: FmeTarget): Boolean {
        val existing = replacements.get(stored.profileId, FmeContext(stored.scope, stored.scopeKey), stored.x, stored.y, stored.z)
        if (existing?.copy(updatedAt = stored.updatedAt) == stored) return false
        val before = snapshot()
        replacements.put(stored)
        onReplacementChanged(before)
        return true
    }

    private fun FmeTarget.source(): String = when {
        ghost -> AIR_ID
        anyBlock() -> FME_ANY_SOURCE
        else -> blockId
    }

    private fun remove(x: Int, y: Int, z: Int): Boolean {
        beforeMutation()
        val existing = positionAt(x, y, z) ?: return false
        val before = snapshot()
        replacements.remove(FmePositionKey(existing.profileId, existing.scope, existing.scopeKey, existing.x, existing.y, existing.z))
        onReplacementChanged(before)
        return true
    }

    private data class FmeTarget(
        val pos: BlockPos3,
        val blockId: String,
        val face: BlockFace,
        val ghost: Boolean,
        val distanceSq: Double,
        val point: Vec3d,
    )

    private companion object {
        val tr = I18n.scope("blackaddons.fme")

        const val AIR_ID = "minecraft:air"
        const val PICK_DISTANCE = 6.0
        const val TARGET_PAD = 0.002
        val TARGET_REAL = Color(170, 75, 255, 230)
        val TARGET_VISUAL = Color(65, 210, 235, 230)
        val TARGET_INVALID = Color(235, 75, 75, 230)
    }
}

internal data class FmeResolvedRoom(val name: String, val transform: RoomTransform?)

internal fun currentFmeBossLocationKey(): String? {
    if (!LocationUtils.inBoss) return null
    val floor = if (LocationUtils.hasF7Boss) 7 else LocationUtils.dungeonFloorNumber ?: return null
    return fmeBossLocationKey(floor, LocationUtils.f7Phase?.number)
}

internal val F7_PHASES = 1..5

private val F7_PHASE_KEYS = F7_PHASES.map { fmeBossLocationKey(7, it) }

internal fun fmePositionLocations(location: String): List<String> =
    if (location in F7_PHASE_KEYS) F7_PHASE_KEYS else listOf(location)

internal fun fmeOwnsHeight(location: String, y: Int): Boolean =
    location !in F7_PHASE_KEYS || fmeBossLocationKey(7, DungeonGeometry.f7Phase(y.toDouble()).number) == location

/** Floor and Master (and P3Sim) share the Floor key: `F7 P3` per Necron phase, otherwise `F3 Boss`. */
internal fun fmeBossLocationKey(floor: Int, f7Phase: Int?): String =
    if (floor == 7 && f7Phase != null) "F7 P$f7Phase" else "F$floor Boss"
