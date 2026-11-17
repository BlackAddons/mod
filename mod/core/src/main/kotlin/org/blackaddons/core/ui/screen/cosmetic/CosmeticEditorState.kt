package org.blackaddons.core.ui.screen.cosmetic

import org.blackaddons.api.util.concurrent.OffThread
import org.blackaddons.core.cosmetics.PlayerCosmetics
import org.blackaddons.core.cosmetics.me.CosmeticDraft
import org.blackaddons.core.cosmetics.me.MeCosmeticService

/**
 * Fetch/save lifecycle for [CosmeticEditorScreen]. Network runs on daemon threads. Results go into `@Volatile` fields
 * that [applyPendingResults] drains on the render thread.
 */
internal class CosmeticEditorState {
    enum class Phase { LOADING, READY, NOT_OWNED, NOT_LINKED, ERROR }

    @Volatile var phase = Phase.LOADING
        private set
    @Volatile private var fetchResult: MeCosmeticService.Fetch? = null

    var draft: CosmeticDraft? = null
        private set
    @Volatile private var baseline: CosmeticDraft? = null

    @Volatile var saving = false
        private set

    /** The screen clears it on edit. */
    @Volatile var saveOutcome: MeCosmeticService.Save? = null

    val dirty: Boolean
        get() {
            val d = draft ?: return false
            val b = baseline ?: return false
            return !d.matches(b)
        }

    fun reload() {
        phase = Phase.LOADING
        fetchResult = null
        saveOutcome = null
        OffThread.run(
            name = "me-cosmetic",
            work = { MeCosmeticService.fetch() },
            onFailure = { MeCosmeticService.Fetch.Error(it.message) },
            onResult = { fetchResult = it },
        )
    }

    /** Render thread, every frame. */
    fun applyPendingResults() {
        fetchResult?.let { result ->
            fetchResult = null
            when (result) {
                is MeCosmeticService.Fetch.Loaded -> {
                    draft = result.draft
                    baseline = result.draft.copyOf()
                    phase = Phase.READY
                }
                MeCosmeticService.Fetch.NotOwned -> phase = Phase.NOT_OWNED
                MeCosmeticService.Fetch.NotLinked -> phase = Phase.NOT_LINKED
                is MeCosmeticService.Fetch.Error -> phase = Phase.ERROR
            }
        }
    }

    fun save() {
        val d = draft ?: return
        if (saving || !dirty) return
        val snapshot = d.copyOf()
        saving = true
        saveOutcome = null
        OffThread.run(
            name = "me-cosmetic-save",
            work = { MeCosmeticService.save(snapshot) },
            onFailure = { MeCosmeticService.Save.Error(it.message) },
            onResult = { outcome ->
                if (outcome is MeCosmeticService.Save.Ok) {
                    baseline = snapshot
                    // `Ok` means the server stored it, so show it now.
                    PlayerCosmetics.ownNameSaved(snapshot.toPreviewCosmetic())
                }
                saveOutcome = outcome
                // On every path, or the button stays dead.
                saving = false
            },
        )
    }

    fun nameApplied(name: String) {
        draft?.displayName = name
        baseline?.displayName = name
    }

    fun revert() {
        val b = baseline ?: return
        draft?.loadFrom(b)
        saveOutcome = null
    }
}
