package org.blackaddons.core.ui.screen.cosmetic.wardrobe

import org.blackaddons.api.cosmetics.Cosmetic3dItem
import org.blackaddons.api.util.concurrent.OffThread
import org.blackaddons.core.cosmetics.me.Me3dService
import org.blackaddons.core.cosmetics.mesh.Cosmetic3dEntry
import org.blackaddons.core.cosmetics.mesh.CosmeticModifiers

/**
 * Async catalog/equip lifecycle, kept apart from drawing like [CosmeticEditorState]. Network runs on daemon threads
 * into `@Volatile` fields that [applyPendingResults] drains on the render thread. A reload follows every write, because
 * the server decides what's worn.
 */
internal class WardrobeState {
    enum class Phase { LOADING, READY, NOT_LINKED, ERROR }

    @Volatile var phase = Phase.LOADING
        private set
    @Volatile private var fetchResult: Me3dService.Fetch? = null

    var entries: List<Cosmetic3dEntry> = emptyList()
        private set

    @Volatile var busy = false
        private set
    @Volatile private var writeResult: Me3dService.Write? = null

    /** Last write result, for the footer. The tab clears it when the player moves on. */
    @Volatile var outcome: Me3dService.Write? = null

    /**
     * True when the read after a successful write lands. Publishing [equippedItems] sooner would keep the replaced
     * state.
     */
    @Volatile var wroteAndReloaded = false
        private set

    private var awaitingWriteReload = false

    val equipped: List<Cosmetic3dEntry>
        get() = entries.filter { it.equipped }

    /** Everything u wear, as the server last reported it. These are the entry's stored choices, not a draft. */
    fun equippedItems(): List<Cosmetic3dItem> = equipped.mapNotNull(::resolveStored)

    fun resolveStored(entry: Cosmetic3dEntry): Cosmetic3dItem? =
        CosmeticModifiers.previewItem(entry, entry.modifiers, entry.tints, entry.adjust)

    fun reload() = load(showLoading = true)

    /** Reads again in place, so a write doesn't blank the list. */
    private fun refresh() = load(showLoading = false)

    private fun load(showLoading: Boolean) {
        if (showLoading) phase = Phase.LOADING
        fetchResult = null
        OffThread.run(
            name = "me-cosmetics3d",
            work = { Me3dService.fetch() },
            onFailure = { Me3dService.Fetch.Error(it.message) },
            onResult = { fetchResult = it },
        )
    }

    /** Drains completed work into [entries]/[phase]/[outcome]. Call on the render thread each frame. */
    fun applyPendingResults() {
        fetchResult?.let { result ->
            fetchResult = null
            when (result) {
                is Me3dService.Fetch.Loaded -> {
                    entries = result.entries
                    phase = Phase.READY
                    if (awaitingWriteReload) {
                        awaitingWriteReload = false
                        wroteAndReloaded = true
                    }
                }
                Me3dService.Fetch.NotLinked -> phase = Phase.NOT_LINKED
                is Me3dService.Fetch.Error -> phase = Phase.ERROR
            }
        }
        writeResult?.let { result ->
            writeResult = null
            outcome = result
            busy = false
            // The server owns the equipped set, so read it again.
            if (result is Me3dService.Write.Ok) {
                awaitingWriteReload = true
                refresh()
            }
        }
    }

    fun equip(
        id: String,
        tints: Map<String, String>,
        modifiers: Map<String, String>,
        adjust: Map<String, Double>,
    ) = write { Me3dService.equip(id, tints, modifiers, adjust) }

    fun unequip(entry: Cosmetic3dEntry) = write { Me3dService.unequip(entry.id) }

    private fun write(call: () -> Me3dService.Write) {
        if (busy) return
        busy = true
        outcome = null
        OffThread.run(
            name = "me-cosmetics3d-write",
            work = call,
            onFailure = { Me3dService.Write.Error(it.message) },
            onResult = { writeResult = it },
        )
    }
}
