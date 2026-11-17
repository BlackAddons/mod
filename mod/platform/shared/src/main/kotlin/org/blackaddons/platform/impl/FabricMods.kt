package org.blackaddons.platform.impl

import net.fabricmc.loader.api.FabricLoader
import net.fabricmc.loader.api.ModContainer
import net.fabricmc.loader.api.metadata.CustomValue
import net.fabricmc.loader.api.metadata.ModDependency
import org.blackaddons.api.host.ModEntry
import org.blackaddons.api.host.ModsHost

/**
 * [ModsHost] over Fabric Loader, read once. Library detection only trusts positive signals (Mod Menu `library` badge,
 * Fabric API module marker, builtin, nested jar). The predecessor's `api`/`lib` name match demoted real mods.
 */
object FabricMods : ModsHost {

    override val loaded: List<ModEntry> by lazy {
        FabricLoader.getInstance().allMods.map { mod ->
            val metadata = mod.metadata
            ModEntry(
                id = metadata.id,
                name = metadata.name,
                version = metadata.version.friendlyString,
                isLibrary = isLibrary(mod),
                parentId = parentOf(mod),
                dependencies = metadata.dependencies
                    .filter { it.kind == ModDependency.Kind.DEPENDS || it.kind == ModDependency.Kind.RECOMMENDS }
                    .mapTo(mutableSetOf()) { it.modId },
            )
        }
    }

    private fun isLibrary(mod: ModContainer): Boolean {
        val metadata = mod.metadata
        // `java`, `minecraft`, `fabricloader`: synthesized, never installed.
        if (metadata.type == "builtin") return true
        if (metadata.containsCustomValue(FABRIC_API_MODULE)) return true
        // Nested jar: bundled in a user-installed mod.
        if (mod.containingMod.isPresent) return true
        return modMenu(mod)?.get("badges")
            ?.takeIf { it.type == CustomValue.CvType.ARRAY }
            ?.asArray
            ?.any { it.type == CustomValue.CvType.STRING && it.asString == "library" }
            ?: false
    }

    /** Mod Menu parent, then containing jar. Fabric API modules have neither, so they're pinned to the umbrella mod. */
    private fun parentOf(mod: ModContainer): String? {
        modMenuParent(mod)?.let { return it }
        mod.containingMod.orElse(null)?.let { return it.metadata.id }
        if (mod.metadata.containsCustomValue(FABRIC_API_MODULE)) {
            return FABRIC_API_IDS.firstOrNull { FabricLoader.getInstance().isModLoaded(it) }
        }
        return null
    }

    /** `"parent": "id"` or `"parent": { "id": "..." }`. */
    private fun modMenuParent(mod: ModContainer): String? {
        val parent = modMenu(mod)?.get("parent") ?: return null
        return when (parent.type) {
            CustomValue.CvType.STRING -> parent.asString
            CustomValue.CvType.OBJECT -> parent.asObject.get("id")
                ?.takeIf { it.type == CustomValue.CvType.STRING }
                ?.asString
            else -> null
        }
    }

    private fun modMenu(mod: ModContainer): CustomValue.CvObject? =
        mod.metadata.getCustomValue("modmenu")
            ?.takeIf { it.type == CustomValue.CvType.OBJECT }
            ?.asObject

    private const val FABRIC_API_MODULE = "fabric-api:module-lifecycle"

    /** `fabric-api` on modern loaders, `fabric` on older ones. */
    private val FABRIC_API_IDS = listOf("fabric-api", "fabric")
}
