package org.blackaddons.core.features.impl.visual

import org.blackaddons.api.event.WorldChangeEvent
import org.blackaddons.api.event.world.EntityEvent
import org.blackaddons.api.feature.Categories
import org.blackaddons.api.feature.Feature
import org.blackaddons.core.backend.Backend
import org.blackaddons.core.cosmetics.mesh.NpcCatEars
import org.blackaddons.core.cosmetics.store.CosmeticStore
import org.blackaddons.core.cosmetics.store.HttpCosmeticsSource

/**
 * Shows server ("paid") cosmetics: names, prefixes, sizes. Resolved per player when seen, cached to disk, refreshed
 * through the gateway or polling. Off (or "Show Other Players" off) hides other people's cosmetics.
 */
internal object Cosmetics : Feature(
    description = "Show server cosmetics (custom names, prefixes and sizes) for players who own them.",
    toggled = true,
) {

    override val section = Categories.Visual.Self
    private val applyToOthers by boolean("Show Other Players", true, id = "ShowOthers")
        .withDescription("When off, only your own server cosmetic is applied — other players stay vanilla.")

    val showOthers: Boolean get() = applyToOthers.value

    private val npcCatEarsSetting by boolean("NPC Cat Ears", false).hidden()

    val npcCatEars: Boolean get() = npcCatEarsSetting.value

    fun toggleNpcCatEars(): Boolean {
        npcCatEarsSetting.value = !npcCatEarsSetting.value
        if (npcCatEarsSetting.value) NpcCatEars.fetchItem()
        return npcCatEarsSetting.value
    }

    override fun init() {
        register<EntityEvent.Spawn> { NpcCatEars.onSpawn(event.entity) }
        register<EntityEvent.Remove> { NpcCatEars.onRemove(event.entity.uuid) }
        register<WorldChangeEvent> { NpcCatEars.clear() }
        CosmeticStore.setSource(HttpCosmeticsSource(Backend.COSMETICS_RESOLVE_URL))
        CosmeticStore.startRefreshing()
    }

    override fun onEnable() {
        super.onEnable()
        CosmeticStore.warmUp()
    }
}
