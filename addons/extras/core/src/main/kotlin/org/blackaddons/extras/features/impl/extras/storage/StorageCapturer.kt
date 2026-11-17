package org.blackaddons.extras.features.impl.extras.storage

import org.blackaddons.api.Platform
import org.blackaddons.api.gui.CapturedSlots

/**
 * Polls the open container each tick, since the overlay doesn't replace the screen and its title tells us what's open.
 * Saves once contents settle, because Hypixel fills slots gradually.
 */
internal object StorageCapturer {

    private enum class Target { NONE, PAGE, OVERVIEW }

    private var target = Target.NONE
    private var page: OpenPage? = null
    private var snapshot: List<String?>? = null
    private var stable = 0
    // Re-capture on change, so adding a backpack refreshes without reopening.
    private var lastCommitted: List<String?>? = null

    /** Ticks the contents have to hold steady (and non-empty) before we save. */
    private const val STABLE_TICKS = 8

    /** Call every client tick while capturing. */
    fun tick() {
        val title = Platform.container.title()
        if (title == null || !Platform.skyblock.onSkyblock) {
            reset()
            return
        }
        if (StoragePatterns.isStorageOverview(title)) {
            step(Target.OVERVIEW, null)
        } else {
            val open = StoragePatterns.matchPage(title)
            if (open == null) reset() else step(Target.PAGE, open)
        }
    }

    private fun step(newTarget: Target, open: OpenPage?) {
        if (target != newTarget || open != page) {
            target = newTarget; page = open; snapshot = null; stable = 0; lastCommitted = null
        }
        val slots = Platform.container.capture() ?: return
        if (slots.raw == lastCommitted) return
        if (!settled(slots)) return
        when (newTarget) {
            Target.OVERVIEW -> commitOverview(slots)
            Target.PAGE -> commitPage(open!!, slots)
            Target.NONE -> {}
        }
        lastCommitted = slots.raw
    }

    private fun commitPage(open: OpenPage, slots: CapturedSlots) {
        // Drop Hypixel's top nav row (NEU: `rows = slotCount/9 - 1`).
        val bodyRows = (slots.rows - 1).coerceAtLeast(0)
        val body = slots.raw.drop(slots.cols)
        StorageCache.put(
            CapturedContainer(
                type = open.type, page = open.page, title = open.title,
                rows = bodyRows, cols = slots.cols,
                items = body.map { it?.let(::describe) },
                capturedAt = System.currentTimeMillis(),
            ),
        )
    }

    private fun commitOverview(slots: CapturedSlots) {
        // Keep every slot, empty and locked included, with its icon and index for click relay.
        val refs = slots.raw.mapIndexedNotNull { index, raw ->
            raw?.let {
                Platform.items.describe(it)?.name
                    ?.let(StoragePatterns::parseOverviewSlot)
                    ?.copy(icon = it, slot = index)
            }
        }
        if (refs.isNotEmpty()) StorageCache.putManifest(refs)
    }

    private fun settled(slots: CapturedSlots): Boolean {
        if (slots.raw == snapshot) stable++ else { snapshot = slots.raw; stable = 0 }
        return stable >= STABLE_TICKS && slots.raw.any { it != null }
    }

    private fun describe(raw: String): CapturedItem? {
        val info = Platform.items.describe(raw) ?: return null
        return CapturedItem(raw, info.name, info.count)
    }

    private fun reset() {
        target = Target.NONE; page = null; snapshot = null; stable = 0; lastCommitted = null
    }
}
