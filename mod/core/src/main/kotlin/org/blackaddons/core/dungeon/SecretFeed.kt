package org.blackaddons.core.dungeon

/**
 * Per action bar message since the last world change, so a recording with no secrets can say whether the counts never
 * arrived, arrived and got refused, or got credited.
 */
internal class SecretFeed {

    var actionBars = 0
        private set
    var withSecrets = 0
        private set
    var credited = 0
        private set

    private val refused = linkedMapOf<String, Int>()
    val skipped: Map<String, Int> get() = refused

    fun sawActionBar() { actionBars++ }

    fun sawSecrets() { withSecrets++ }

    fun credit() { credited++ }

    fun skip(reason: String) { refused.merge(reason, 1, Int::plus) }

    fun reset() {
        actionBars = 0
        withSecrets = 0
        credited = 0
        refused.clear()
    }
}
