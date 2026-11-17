package org.blackaddons.api

import org.blackaddons.api.feature.Categories
import org.blackaddons.api.feature.FeatureCategory
import org.blackaddons.api.host.AddonLogger

/**
 * Addon entrypoint, registered as `blackaddons:addon` in the addon's `fabric.mod.json`:
 *
 * ```
 * "entrypoints": {
 *   "blackaddons:addon": [ { "value": "com.example.MyAddon", "adapter": "kotlin" } ]
 * }
 * ```
 *
 * The host scans this class's package (and subpackages) for `Feature` objects, so put it at the root
 * of the addon's sources.
 */
interface Addon {
    /**
     * The namespace for config and lang files, ideally the Fabric mod id. The addon declares it itself, so it is
     * not trusted: see [AddonContext.addonId].
     */
    val id: String

    /** This addon's logger. It is named after the loader's mod id, not [id], and works from feature objects too. */
    val log: AddonLogger get() = Platform.loggers.of(this)

    /**
     * Tabs this addon declares. Empty puts its features into host categories ([Categories]); an id that
     * clashes with a host category is refused.
     */
    val categories: List<FeatureCategory> get() = emptyList()

    /**
     * Called once after features are initialized and config is read, before any feature is enabled. Don't
     * pass [context] to code you don't control - it carries this addon's identity.
     */
    fun onLoad(context: AddonContext) {}

    /** Forward-compat hook: only fires if hot reload is ever added. */
    fun onUnload() {}
}
