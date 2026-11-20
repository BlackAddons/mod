package org.blackaddons.core.features.impl.ui.inventorybuttons

/*
 * Snapshots taken before each change. A snapshot equal to the current state undoes nothing, so it's skipped, and
 * remembering before a change that never happens costs nothing but a slot.
 */
internal class EditHistory<T>(
    private val capture: () -> T,
    private val restore: (T) -> Unit,
    private val limit: Int = DEFAULT_LIMIT,
    private val now: () -> Long = System::currentTimeMillis,
) {
    private val states = ArrayDeque<T>()
    private var lastGroup: String? = null
    private var lastAtMs = 0L

    // Calls with the same group in quick succession (a held arrow key) share one snapshot.
    fun remember(group: String? = null) {
        val time = now()
        val joins = group != null && group == lastGroup && time - lastAtMs < GROUP_MS
        lastGroup = group
        lastAtMs = time
        if (joins) return
        states.addLast(capture())
        if (states.size > limit) states.removeFirst()
    }

    fun undo(): Boolean {
        lastGroup = null
        val current = capture()
        while (true) {
            val state = states.removeLastOrNull() ?: return false
            if (state != current) {
                restore(state)
                return true
            }
        }
    }

    fun clear() {
        states.clear()
        lastGroup = null
    }

    private companion object {
        const val DEFAULT_LIMIT = 50
        const val GROUP_MS = 1000L
    }
}
