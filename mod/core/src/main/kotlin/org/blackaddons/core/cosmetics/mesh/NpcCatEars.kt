package org.blackaddons.core.cosmetics.mesh
import org.blackaddons.api.cosmetics.Cosmetic3dItem
import org.blackaddons.api.event.world.EntitySnapshot
import org.blackaddons.api.util.Uuids
import org.blackaddons.api.util.concurrent.OffThread
import org.blackaddons.core.cosmetics.me.Me3dService
import org.blackaddons.core.features.impl.visual.Cosmetics
import java.util.Base64
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Credits egg on the About page: Cat Ears on one Hypixel NPC. Recognized by skin, because its UUID and name change on
 * respawn.
 */
internal object NpcCatEars {

    private const val SKIN_HASH = "9b56895b9659896ad647f58599238af532d46db9c1b0389b8bbeb70999dab33d"
    private const val ITEM_ID = "cat_ears"

    private val wearers = ConcurrentHashMap.newKeySet<UUID>()

    @Volatile
    private var item: Cosmetic3dItem? = null
    private val fetching = AtomicBoolean()

    fun itemsFor(uuid: UUID): List<Cosmetic3dItem>? {
        if (!Cosmetics.npcCatEars || uuid !in wearers) return null
        return item?.let(::listOf)
    }

    fun onSpawn(entity: EntitySnapshot) {
        if (!isTheNpc(entity)) return
        wearers += entity.uuid
        if (Cosmetics.npcCatEars) fetchItem()
    }

    fun onRemove(uuid: UUID) {
        wearers -= uuid
    }

    fun clear() = wearers.clear()

    fun isTheNpc(entity: EntitySnapshot): Boolean {
        if (!entity.isPlayer || Uuids.isAccount(entity.uuid)) return false
        val textures = entity.skinTextures ?: return false
        return runCatching { String(Base64.getDecoder().decode(textures)) }.getOrNull()?.contains(SKIN_HASH) == true
    }

    /** Needs a backend session. On failure it retries on the next trigger. */
    fun fetchItem() {
        if (item != null || !fetching.compareAndSet(false, true)) return
        OffThread.run(
            name = "npc-cat-ears",
            work = {
                val entries = (Me3dService.fetch() as? Me3dService.Fetch.Loaded)?.entries
                entries?.firstOrNull { it.id == ITEM_ID }?.let { CosmeticModifiers.previewItem(it, emptyMap(), emptyMap()) }
            },
            onFailure = { null },
            onResult = {
                item = it
                fetching.set(false)
            },
        )
    }
}
