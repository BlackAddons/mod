package org.blackaddons.platform.util

import java.util.IdentityHashMap

/**
 * Identity lookup, falling back to `equals`. Command nodes hash their whole subtree (slow in a `HashMap`), but mods
 * like Odin (Commodore) redirect aliases to an equal copy.
 */
class IdentityFirstMap<K, V> : IdentityHashMap<K, V>() {
    override fun get(key: K): V? = super.get(key) ?: entries.firstOrNull { it.key == key }?.value
}
