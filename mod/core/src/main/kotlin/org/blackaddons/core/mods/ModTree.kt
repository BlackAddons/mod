package org.blackaddons.core.mods

import org.blackaddons.api.host.ModEntry

/**
 * Mods arranged for a picker. Libraries are hidden. Selection only uses visible mods, and every result gets
 * [normalize]d (library ids computed again), so no stale id stays in the allowlist. Ids that name no loaded mod pass
 * through.
 */
internal class ModTree(entries: List<ModEntry>) {

    val byId: Map<String, ModEntry> = entries.associateBy { it.id }

    private val childrenById: Map<String, List<ModEntry>> = entries
        .filter { it.parentId != null && it.parentId in byId }
        .groupBy { it.parentId!! }

    private val dependentsById: Map<String, List<String>> = entries
        .flatMap { mod -> mod.dependencies.filter { it in byId }.map { dep -> dep to mod.id } }
        .groupBy({ it.first }, { it.second })

    val roots: List<ModEntry> = entries
        .filter { !it.isLibrary && (it.parentId == null || it.parentId !in byId) }
        .sortedBy { it.name.lowercase() }

    fun children(id: String): List<ModEntry> =
        childrenById[id].orEmpty().filter { !it.isLibrary }.sortedBy { it.name.lowercase() }

    /** Plus its visible dependencies and implied libraries. */
    fun withEnabled(selection: Set<String>, id: String): Set<String> =
        normalize(selection + visibleClosure(id) { dependenciesOf(it) })

    /** Minus every visible mod that needs it. */
    fun withDisabled(selection: Set<String>, id: String): Set<String> =
        normalize(selection - visibleClosure(id) { dependentsById[it].orEmpty() })

    /** Rebuilds the library ids from the visible ones. Loader builtins always stay, because they reveal nothing. */
    private fun normalize(selection: Set<String>): Set<String> {
        val unmanaged = selection.filter { it !in byId }
        val required = HashSet<String>()
        selection
            .filter { byId[it]?.isLibrary == false }
            .forEach { collectRequired(it, required) }
        required += ALWAYS_KEPT.filter { it in byId }
        return unmanaged.toSet() + required
    }

    /** Pulls nested jars, API modules and transitive dependencies. Visible nested mods are ticked separately. */
    private fun collectRequired(id: String, out: MutableSet<String>) {
        if (id !in byId || !out.add(id)) return
        childrenById[id].orEmpty().filter { it.isLibrary }.forEach { collectRequired(it.id, out) }
        dependenciesOf(id).forEach { collectRequired(it, out) }
    }

    private fun visibleClosure(id: String, next: (String) -> List<String>): Set<String> {
        val seen = HashSet<String>()
        fun walk(current: String) {
            if (byId[current]?.isLibrary != false || !seen.add(current)) return
            next(current).forEach(::walk)
        }
        walk(id)
        return seen
    }

    private fun dependenciesOf(id: String): List<String> =
        byId[id]?.dependencies?.filter { it in byId }.orEmpty()

    private companion object {
        val ALWAYS_KEPT = setOf("minecraft", "java", "fabricloader")
    }
}
