package org.blackaddons.api.host

/**
 * A loaded mod, including the game and libraries. [parentId]/[isLibrary] let a mod list fold `fabric-*`
 * modules into the mod that ships them.
 */
data class ModEntry(
    val id: String,
    val name: String,
    val version: String,

    /** Not chosen by the user (API module, bundled dependency, builtin). Pickers hide these. */
    val isLibrary: Boolean,

    /** Shipping mod for nested jars / API modules. */
    val parentId: String?,

    /** May name mods that aren't installed. */
    val dependencies: Set<String>,
)

interface ModsHost {
    /** In no particular order. */
    val loaded: List<ModEntry>
}
