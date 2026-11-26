package org.blackaddons.core.features.impl.visual.fme

import com.google.gson.JsonObject
import org.blackaddons.api.Platform
import org.blackaddons.api.event.ClientStoppingEvent
import org.blackaddons.api.event.ClientTickEvent
import org.blackaddons.api.event.EventBus
import org.blackaddons.api.event.EventPriority
import org.blackaddons.api.event.WorldChangeEvent
import org.blackaddons.api.event.dungeon.DungeonTileScannedEvent
import org.blackaddons.api.event.input.KeyInputEvent
import org.blackaddons.api.event.input.MouseClickEvent
import org.blackaddons.api.event.render.RenderWorldEvent
import org.blackaddons.api.event.subscribe
import org.blackaddons.api.event.world.GuiEvent
import org.blackaddons.api.feature.Categories
import org.blackaddons.api.feature.Feature
import org.blackaddons.api.feature.matches
import org.blackaddons.api.host.Cancellation
import org.blackaddons.api.hud.AnchorX
import org.blackaddons.api.hud.AnchorY
import org.blackaddons.api.input.InputType
import org.blackaddons.api.input.Keys
import org.blackaddons.api.ui.toast.Toasts
import org.blackaddons.api.util.I18n
import org.blackaddons.api.util.concurrent.Workers
import org.blackaddons.api.world.BlockAppearance
import org.blackaddons.api.world.BlockAppearanceSource
import org.blackaddons.api.world.BlockAppearances
import org.blackaddons.api.world.BlockPos3
import org.blackaddons.core.ui.screen.fme.FmeScreen
import java.util.concurrent.TimeUnit

/** Local-only block appearance edits per profile. The world state never changes. */
object Fme : Feature(
    description = "Replace how blocks look without changing the world.",
    name = "Legit FME",
) {
    private val tr = I18n.scope("blackaddons.fme")

    override val section = Categories.Visual.World

    internal val replacements = FmeReplacementStore()
    internal val typeReplacements = FmeTypeReplacementStore()
    private val saveWorker = Workers.single("fme-save").also { worker ->
        EventBus.subscribe<ClientStoppingEvent> {
            worker.shutdown()
            worker.awaitTermination(SAVE_DRAIN_SECONDS, TimeUnit.SECONDS)
        }
    }
    internal val storage = FmeStorage(replacements, typeReplacements, saveWorker)
    @Volatile internal var renderingEnabled = false
    @Volatile private var preserveShapeEnabled = true
    @Volatile internal var renderIndex = FmeRenderIndex.EMPTY
    private var scannerRefresh: Cancellation? = null
    internal var lastRenderContext: FmeActiveContext? = null
    internal val preloaded = HashMap<FmeActiveContext, FmeRenderIndex>()
    internal var preloading: Cancellation? = null
    internal val undoHistory = ArrayDeque<FmeSnapshot>()
    internal val redoHistory = ArrayDeque<FmeSnapshot>()
    private val blockCatalog by lazy(Platform.world::registeredBlocks)
    private val blocksById by lazy { blockCatalog.associateBy { it.blockId } }
    private val knownBlockIds by lazy { blocksById.keys }
    internal val rules: FmeRules by lazy {
        FmeRules(
            replacements = replacements,
            typeReplacements = typeReplacements,
            profileId = { activeProfile().id },
            isKnownBlock = { it in knownBlockIds },
            snapshot = ::snapshot,
            remember = ::remember,
            persistAll = ::persistAll,
            persistReplacements = ::persistReplacements,
            persistTypeReplacements = ::persistTypeReplacements,
            refreshRendering = { refreshRendering() },
        )
    }
    internal val worldEditor: FmeWorldEditor by lazy {
        FmeWorldEditor(
            replacements = replacements,
            profileId = { activeProfile().id },
            activeScope = ::activeScope,
            selectedBlockId = ::selectedBlockId,
            anyBlock = ::isAnyBlockMode,
            scopeLabel = ::currentScopeLabel,
            renderIndex = { renderIndex },
            blockEntry = blocksById::get,
            snapshot = ::snapshot,
            beforeMutation = ::cancelTintApply,
            selectBlock = ::selectBlock,
            onReplacementChanged = { before ->
                rules.invalidate()
                remember(before)
                persistReplacements()
                refreshRendering()
            },
        )
    }

    internal val editMode by boolean("Edit Mode")
        .withDescription("Right-click replaces and left-click restores the block you are looking at.")
    private val preserveBlockShape by boolean("Preserve Block Shape", default = true)
        .onChange { enabled ->
            preserveShapeEnabled = enabled
            if (renderingEnabled) redraw(renderIndex.invalidationFrom(FmeRenderIndex.EMPTY))
        }
        .withDescription("Keep the original block's visible shape while applying the replacement texture.")
    private val openEditor by button(
        name = "Editor",
        action = { Platform.scheduler.schedule(0) { FmeScreen.open() } },
        label = "Open",
    ).withDescription("Open the block appearance editor.")
    private val openEditorKey by keybind("Open Editor")
        .withDescription("Open the block appearance editor, even while FME is disabled.")
    private val editModeKey by keybind("Toggle Edit Mode")
        .withDescription("Turn Edit Mode on or off.")
    private val anyBlockKey by keybind("Toggle Any Block")
        .withDescription("In Edit Mode, switch between replacing only the clicked block type and any block at that position.")
    private val pickBlockKey by keybind("Pick Block", Keys.MOUSE_MIDDLE, InputType.MOUSE)
        .withDescription("In Edit Mode, select the block you are looking at.")
    private val ghostBlockKey by keybind("Place Ghost Block", Keys.G)
        .withDescription("In Edit Mode, place a visual block against the face you are looking at.")
    private val undoKey by keybind("Undo")
        .withDescription("In Edit Mode, take back the last FME edit.")
    private val redoKey by keybind("Redo")
        .withDescription("In Edit Mode, reapply the last undone FME edit.")
    private val selectedBlock by string("Selected Block", "minecraft:stone", maxLength = 128).hidden()
    private val editScopeSetting by string("Edit Scope", FmeScope.LOCATION.name, maxLength = 16).hidden()
    private val anyBlockMode by boolean("Any Block").hidden()

    @Suppress("unused")
    private val indicator = hudElement(
        name = "FME Edit Mode",
        shouldDraw = { editMode.value },
        defaultAnchorX = AnchorX.CENTER,
        defaultAnchorY = AnchorY.MIDDLE,
        defaultOffsetY = INDICATOR_OFFSET_Y,
    ) { worldEditor.indicator() }

    override fun init() {
        storage.load()
        BlockAppearances.register(appearanceSource)
        EventBus.subscribe<KeyInputEvent>(EventPriority.HIGHEST) {
            if (event.isRepeat || !openEditorKey.matches(event) || Platform.screenHost.anyScreenOpen) return@subscribe
            FmeScreen.open()
        }
        EventBus.subscribe<MouseClickEvent>(EventPriority.HIGHEST) {
            if (!openEditorKey.matches(event) || Platform.screenHost.anyScreenOpen) return@subscribe
            event.cancelled = true
            FmeScreen.open()
        }
        register<KeyInputEvent> {
            if (event.isRepeat || Platform.screenHost.anyScreenOpen) return@register
            when {
                editModeKey.matches(event) -> toggleEditMode()
                !editMode.value -> Unit
                anyBlockKey.matches(event) -> toggleAnyBlockMode()
                pickBlockKey.matches(event) -> worldEditor.pickBlock()
                ghostBlockKey.matches(event) -> worldEditor.placeGhostBlock()
                undoKey.matches(event) -> changeHistory(undo = true)
                redoKey.matches(event) -> changeHistory(undo = false)
            }
        }
        register<MouseClickEvent> {
            if (Platform.screenHost.anyScreenOpen) return@register
            if (editModeKey.matches(event)) {
                event.cancelled = true
                toggleEditMode()
                return@register
            }
            if (!editMode.value || !event.isPress) return@register
            when {
                anyBlockKey.matches(event) -> toggleAnyBlockMode()
                pickBlockKey.matches(event) -> worldEditor.pickBlock()
                ghostBlockKey.matches(event) -> worldEditor.placeGhostBlock()
                undoKey.matches(event) -> changeHistory(undo = true)
                redoKey.matches(event) -> changeHistory(undo = false)
                event.button in EDIT_BUTTONS -> worldEditor.handleClick(event.button)
                else -> return@register
            }
            event.cancelled = true
        }
        register<GuiEvent.Open> { setEditMode(false) }
        register<RenderWorldEvent> { renderEditTarget() }
        register<DungeonTileScannedEvent> {
            if (!renderingEnabled || !hasDungeonRules()) return@register
            scannerRefresh?.cancel()
            scannerRefresh = Platform.scheduler.schedule(SCANNER_REFRESH_DELAY) {
                scannerRefresh = null
                refreshRendering()
            }
        }
        register<ClientTickEvent.Post> {
            if (!renderingEnabled) return@register
            val context = activeContext()
            if (context != lastRenderContext) refreshRendering(context, reusePreloaded = true)
        }
        register<WorldChangeEvent> {
            setEditMode(false)
            scannerRefresh?.cancel()
            scannerRefresh = null
            lastRenderContext = null
            renderIndex = FmeRenderIndex.EMPTY
            dropPreloaded()
        }
    }

    override fun onEnable() {
        renderingEnabled = true
        refreshRendering()
    }

    override fun onDisable() {
        setEditMode(false)
        rules.cancelPendingTint()
        renderingEnabled = false
        scannerRefresh?.cancel()
        scannerRefresh = null
        refreshRendering()
    }

    fun selectedBlockId(): String = selectedBlock.value

    fun preserveBlockShape(): Boolean = preserveShapeEnabled

    fun selectBlock(id: String): Boolean {
        val normalized = normalizeBlockId(id)
        if (normalized !in knownBlockIds) return false
        selectedBlock.value = normalized
        Platform.config.save()
        return true
    }

    fun isEditMode(): Boolean = editMode.value

    fun toggleEditMode(): Boolean {
        setEditMode(!editMode.value)
        return editMode.value
    }

    fun startEditMode(): Boolean {
        if (!enabled) return false
        setEditMode(true)
        return true
    }

    fun isAnyBlockMode(): Boolean = anyBlockMode.value

    fun toggleAnyBlockMode(): Boolean {
        anyBlockMode.value = !anyBlockMode.value
        Platform.config.save()
        return anyBlockMode.value
    }

    private fun setEditMode(value: Boolean) {
        if (editMode.value == value) return
        editMode.value = value
        Platform.config.save()
    }

    fun toggleEnabled(): Boolean {
        toggle()
        Platform.config.save()
        return enabled
    }

    fun setEnabled(value: Boolean): Boolean {
        if (enabled != value) toggle()
        Platform.config.save()
        return enabled
    }

    internal fun profiles(): List<FmeProfileHeader> = storage.profiles()

    internal fun activeProfile(): FmeProfileHeader = storage.activeProfile()

    internal fun selectProfile(id: String): Boolean {
        if (activeProfile().id == id) return false
        cancelTintApply()
        if (!storage.selectProfile(id)) return false
        profileChanged()
        return true
    }

    internal fun createProfile(name: String, copyCurrent: Boolean): FmeProfileHeader {
        cancelTintApply()
        val sourceId = activeProfile().id
        val created = storage.createProfile(name, sourceId.takeIf { copyCurrent })
        profileChanged()
        return created
    }

    internal fun sharedDocument(group: FmeManagedGroup? = null): JsonObject? {
        val profileId = activeProfile().id
        if (group == null) return storage.document(profileId)
        return storage.document(
            profileId,
            positions = group.ruleKeys.mapNotNull(replacements::rule),
            types = listOfNotNull(group.typeKey?.let(typeReplacements::get)),
        )
    }

    internal fun readShared(json: JsonObject): FmeShared? = storage.readShared(json)

    internal fun importProfile(shared: FmeShared): FmeProfileHeader {
        cancelTintApply()
        val created = storage.importProfile(shared)
        profileChanged()
        return created
    }

    /** One undo step. */
    internal fun addShared(shared: FmeShared): FmeProfileHeader {
        cancelTintApply()
        val before = snapshot()
        storage.add(activeProfile().id, shared)
        remember(before)
        rules.invalidate()
        refreshRendering()
        return activeProfile()
    }

    internal fun renameProfile(id: String, name: String): Boolean {
        return storage.renameProfile(id, name)
    }

    internal fun deleteProfile(id: String): Boolean {
        cancelTintApply()
        if (!storage.deleteProfile(id)) return false
        profileChanged()
        return true
    }

    private fun profileChanged() {
        rules.invalidate()
        undoHistory.clear()
        redoHistory.clear()
        refreshRendering()
    }

    internal fun activeScope(): FmeScope = runCatching { FmeScope.valueOf(editScopeSetting.value) }
        .getOrDefault(FmeScope.LOCATION)

    internal fun selectScope(scope: FmeScope) {
        if (scope != FmeScope.GLOBAL) {
            editScopeSetting.value = scope.name
            Platform.config.save()
        }
    }

    internal fun currentScopeKey(scope: FmeScope): String? = when (scope) {
        FmeScope.GLOBAL -> ""
        FmeScope.LOCATION -> worldEditor.currentLocationKey()
        FmeScope.DUNGEON -> worldEditor.currentDungeonKey()
    }

    internal fun currentScopeLabel(scope: FmeScope): String = when (scope) {
        FmeScope.GLOBAL -> "Everywhere"
        FmeScope.LOCATION -> worldEditor.currentLocationKey() ?: "Location unavailable"
        FmeScope.DUNGEON -> currentScopeKey(scope) ?: "Stand inside a scanned dungeon room"
    }

    internal fun scopeKeys(scope: FmeScope): List<String> {
        if (scope == FmeScope.GLOBAL) return listOf("")
        val current = currentScopeKey(scope)
        val profileId = activeProfile().id
        return buildList {
            current?.let(::add)
            typeReplacements.entries(profileId, scope).mapTo(this) { it.scopeKey }
            replacements.rules(profileId, scope).mapTo(this) { it.key.scopeKey }
        }.distinct()
    }

    internal fun favoriteBlockIds(): List<String> = storage.favorites(activeProfile().id)

    internal fun toggleFavorite(blockId: String): Boolean {
        val normalized = normalizeBlockId(blockId)
        return storage.toggleFavorite(activeProfile().id, normalized)
    }

    internal fun openProfileFolder() = storage.openFolder()

    fun canUndo(): Boolean = undoHistory.isNotEmpty()
    fun canRedo(): Boolean = redoHistory.isNotEmpty()

    internal fun setTypeReplacement(scope: FmeScope, scopeKey: String, from: String, to: String): Boolean =
        rules.setTypeReplacement(scope, scopeKey, normalizeBlockId(from), normalizeBlockId(to))

    fun clearReplacements(): Int = rules.clearProfile()

    internal fun clearContext(scope: FmeScope, scopeKey: String): Int = rules.clearContext(scope, scopeKey)

    internal fun copyContext(scope: FmeScope, fromKey: String, toKey: String): Int = rules.copyContext(scope, fromKey, toKey)

    fun undo(): Boolean {
        cancelTintApply()
        val target = undoHistory.removeLastOrNull() ?: return false
        redoHistory.addLast(snapshot())
        restore(target)
        return true
    }

    fun redo(): Boolean {
        cancelTintApply()
        val target = redoHistory.removeLastOrNull() ?: return false
        undoHistory.addLast(snapshot())
        restore(target)
        return true
    }

    internal fun changeHistory(undo: Boolean) {
        val changed = if (undo) undo() else redo()
        if (changed) Toasts.show(if (undo) tr("undo_done", "Undid the last FME edit.") else tr("redo_done", "Redid the last FME edit."))
    }

    private val appearanceSource = object : BlockAppearanceSource {
        override fun covers(x: Int, y: Int, z: Int): Boolean = renderIndex.lookupKind(x, y, z) != 0

        override fun appearanceAt(x: Int, y: Int, z: Int, sourceBlockId: String, originalIsAir: Boolean): BlockAppearance? =
            this@Fme.appearanceAt(x, y, z, sourceBlockId, originalIsAir)
    }

    internal fun appearanceAt(x: Int, y: Int, z: Int, sourceBlockId: String, originalIsAir: Boolean): BlockAppearance? {
        val index = renderIndex
        if (!index.active) return null
        val position = index.positions[x, y, z]
        if (position != null && position.appliesTo(sourceBlockId, originalIsAir)) {
            return position.appearance
        }
        if (originalIsAir) return null
        return index.roomTypes[packFmeRoomCell(x, z)]?.typeAppearance(sourceBlockId)
            ?: index.locationTypesAt(y).typeAppearance(sourceBlockId)
            ?: index.globalTypes.typeAppearance(sourceBlockId)
    }

    @JvmStatic
    fun renderTinted(): Boolean = renderIndex.tinted

    /** Gets a new identity when rendering can change (the index gets replaced, never edited). For cache keys. */
    fun renderRevision(): Any = renderIndex

    internal fun managedGroups(scope: FmeScope, scopeKey: String): List<FmeManagedGroup> = rules.groups(scope, scopeKey)

    internal fun setTint(group: FmeManagedGroup, tintRgb: Int, recordHistory: Boolean = true): Boolean =
        rules.setTint(group, tintRgb, recordHistory)

    internal fun removeGroup(group: FmeManagedGroup): Boolean = rules.remove(group)

    internal fun setGroupEnabled(group: FmeManagedGroup, enabled: Boolean): Boolean = rules.setEnabled(group, enabled)

    internal fun transferTypeGroup(group: FmeManagedGroup, targetScope: FmeScope, targetScopeKey: String, move: Boolean): Boolean =
        rules.transferType(group, targetScope, targetScopeKey, move)

    internal fun removePosition(key: FmePositionKey): Boolean = rules.removePosition(key)

    internal fun movePosition(key: FmePositionKey, x: Int, y: Int, z: Int): FmePositionMoveResult =
        rules.movePosition(key, x, y, z)

    internal fun addPosition(group: FmeManagedGroup, x: Int, y: Int, z: Int): FmePositionAddResult =
        rules.addPosition(group, x, y, z, anyBlockMode.value)

    internal fun matchAnyBlock(group: FmeManagedGroup): Boolean = rules.matchAnyBlock(group)

    internal fun setPositionReplacement(group: FmeManagedGroup, blockId: String): Boolean =
        rules.setPositionReplacement(group, normalizeBlockId(blockId))

    internal fun nearestPosition(group: FmeManagedGroup): FmePositionKey? = worldEditor.nearestPosition(group)

    internal fun worldPosition(key: FmePositionKey): BlockPos3? = worldEditor.worldPosition(key)

    internal fun positions(group: FmeManagedGroup, limit: Int): List<FmePositionKey> = group.ruleKeys
        .mapNotNull(replacements::rule)
        .flatMap { rule -> rule.cells.take(limit).map { FmePositionKey(rule.key.profileId, rule.key.scope, rule.key.scopeKey, it.x, it.y, it.z) } }
        .take(limit)

    private val EDIT_BUTTONS = setOf(Keys.MOUSE_LEFT, Keys.MOUSE_RIGHT)
    internal const val HISTORY_LIMIT = 50
    private const val SCANNER_REFRESH_DELAY = 10
    internal const val PRELOAD_INTERVAL_TICKS = 1
    private const val SAVE_DRAIN_SECONDS = 10L
    private const val INDICATOR_OFFSET_Y = 35f
}
