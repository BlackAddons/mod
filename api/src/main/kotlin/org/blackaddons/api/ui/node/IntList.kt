package org.blackaddons.api.ui.node

/** Growable primitive int list, so per-frame visibility queries don't box or allocate. */
class IntList(capacity: Int = 16) {

    private var items = IntArray(capacity.coerceAtLeast(1))

    var size: Int = 0
        private set

    val isEmpty: Boolean get() = size == 0

    operator fun get(index: Int): Int {
        if (index !in 0 until size) throw IndexOutOfBoundsException("$index of $size")
        return items[index]
    }

    fun add(value: Int) {
        if (size == items.size) items = items.copyOf(items.size * 2)
        items[size++] = value
    }

    fun addAll(other: IntList) {
        for (i in 0 until other.size) add(other.items[i])
    }

    /** Swap-remove; order isn't kept. */
    fun removeValue(value: Int): Boolean {
        for (i in 0 until size) {
            if (items[i] == value) {
                items[i] = items[--size]
                return true
            }
        }
        return false
    }

    fun contains(value: Int): Boolean {
        for (i in 0 until size) if (items[i] == value) return true
        return false
    }

    fun clear() {
        size = 0
    }

    fun sort() = items.sort(0, size)

    fun toIntArray(): IntArray = items.copyOf(size)

    inline fun forEach(action: (Int) -> Unit) {
        for (i in 0 until size) action(get(i))
    }
}
