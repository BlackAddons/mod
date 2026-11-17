package org.blackaddons.api.ui.node

/** Flag per id for O(1) checks plus an id list for iteration. [version] changes on every edit for cached highlights. */
class NodeSelection {

    private var flags = BooleanArray(64)
    private val ids = IntList(16)

    var version = 0
        private set

    val size: Int get() = ids.size
    val isEmpty: Boolean get() = ids.isEmpty

    operator fun contains(id: Int): Boolean = id >= 0 && id < flags.size && flags[id]

    fun add(id: Int) {
        if (contains(id)) return
        ensure(id)
        flags[id] = true
        ids.add(id)
        version++
    }

    fun remove(id: Int) {
        if (!contains(id)) return
        flags[id] = false
        ids.removeValue(id)
        version++
    }

    fun toggle(id: Int) = if (contains(id)) remove(id) else add(id)

    fun set(id: Int) {
        if (ids.size == 1 && contains(id)) return
        clear()
        add(id)
    }

    fun clear() {
        if (ids.isEmpty) return
        for (i in 0 until ids.size) flags[ids[i]] = false
        ids.clear()
        version++
    }

    fun toIntArray(): IntArray = ids.toIntArray()

    inline fun forEach(action: (Int) -> Unit) {
        val snapshot = toIntArray()
        for (id in snapshot) action(id)
    }

    /** e.g. after an undo removed a selected node. */
    fun retainExisting(graph: NodeGraph) {
        val gone = toIntArray().filter { graph.node(it) == null }
        gone.forEach(::remove)
    }

    private fun ensure(id: Int) {
        if (id < flags.size) return
        flags = flags.copyOf(maxOf(id + 1, flags.size * 2))
    }
}
