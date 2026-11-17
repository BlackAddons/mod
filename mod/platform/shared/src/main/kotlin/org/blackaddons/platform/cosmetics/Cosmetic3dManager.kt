package org.blackaddons.platform.cosmetics

import com.mojang.blaze3d.platform.NativeImage
import net.minecraft.client.Minecraft
import net.minecraft.client.renderer.entity.state.AvatarRenderState
import net.minecraft.resources.Identifier
import org.blackaddons.api.Platform
import org.blackaddons.api.cosmetics.Cosmetic3dItem
import org.blackaddons.api.cosmetics.CosmeticAttach
import org.blackaddons.api.cosmetics.CosmeticLayer
import org.blackaddons.api.event.ClientTickEvent
import org.blackaddons.api.event.EventListener
import org.blackaddons.api.event.EventPriority
import org.blackaddons.api.util.concurrent.Workers
import org.blackaddons.blackvertex.api.attach.Attachment
import org.blackaddons.blackvertex.api.attach.AttachmentPoint
import org.blackaddons.blackvertex.api.model.Model
import org.blackaddons.blackvertex.format.ModelFormats
import org.blackaddons.blackvertex.render.PlayerCosmetic
import org.blackaddons.blackvertex.render.PlayerCosmetics
import org.blackaddons.blackvertex.texture.DynamicTextures
import org.blackaddons.blackvertex.texture.TextureCompositor
import org.blackaddons.core.cosmetics.mesh.CosmeticAssets
import org.blackaddons.core.log.Log
import org.blackaddons.platform.cosmetics.Cosmetic3dManager.applyBaked
import org.blackaddons.platform.util.modId
import java.util.UUID
import org.blackaddons.core.cosmetics.PlayerCosmetics as OurCosmetics

/**
 * Reconciles the 3D cosmetics online players should wear ([Platform.cosmetics].itemsFor) with what BlackVertex has
 * registered: downloads assets (disk-cached CAS), composites tints, loads models and adds a [PlayerCosmetic] per
 * player.
 *
 * Client thread only, except asset builds, which come back via `Minecraft.execute`. Unknown attach points, formats or
 * roles get skipped with a debug log, so old clients stay compatible.
 */
object Cosmetic3dManager {

    /** Manifests change on a heartbeat, not per frame. */
    private const val RECONCILE_INTERVAL_TICKS = 20

    // Keyed "<uuid>/<cosmeticId>". Client thread only.
    private val entries = HashMap<String, Entry>()
    private var tickCounter = 0
    private var lastPreviewEpoch = 0

    // Preview targets (PV, wardrobe) don't have to be in the tab list. Set by MinecraftPlayerModelPreview.
    @Volatile private var previewTargets: Set<UUID> = emptySet()

    // Two workers bound a crowd's composites.
    private val buildExecutor = Workers.pool("3dcosmetic", 2)

    private class Entry(
        val item: Cosmetic3dItem,
        var cosmetic: PlayerCosmetic?, // null while assets download
        var textures: List<Identifier>,
        var building: Boolean,
    )

    /** Off-thread build output. [applyBaked] registers it on the client thread. */
    private class Baked(
        val model: Model,
        val attach: Attachment,
        val base: NativeImage,
        val emissive: NativeImage?,
    )

    /** Call once from client init. */
    fun start() {
        // Resolves the UUID from the render state so the off-world PV preview works, else BlackVertex's world lookup.
        PlayerCosmetics.uuidResolver = ::uuidOf
        EventListener(ClientTickEvent.Post::class.java, EventPriority.NORMAL) { onTick() }.register()
    }

    /** Players a screen is previewing (PV target, wardrobe tiles). Empty clears. */
    fun setPreviewTargets(uuids: Set<UUID>) {
        previewTargets = uuids
    }

    private fun uuidOf(state: AvatarRenderState): UUID? =
        (state as? BlackaddonsRenderState)?.getBlackaddonsUuid()
            ?: Minecraft.getInstance().level?.getEntity(state.id)?.uuid

    private fun onTick() {
        // The fitting room has to answer the click itself, not on the next interval.
        val epoch = OurCosmetics.previewEpoch
        if (epoch != lastPreviewEpoch) lastPreviewEpoch = epoch
        else if (++tickCounter < RECONCILE_INTERVAL_TICKS) return

        tickCounter = 0
        reconcile()
    }

    private fun reconcile() {
        val connection = Minecraft.getInstance().connection
        val desired = HashMap<String, Pair<UUID, Cosmetic3dItem>>()
        if (connection != null) {
            for (info in connection.onlinePlayers) {
                val uuid = info.profile.id ?: continue
                for (item in Platform.cosmetics.itemsFor(uuid)) desired["$uuid/${item.id}"] = uuid to item
            }
        }
        // Hypixel drops an NPC's tab entry after spawning, so world players count too.
        for (player in Minecraft.getInstance().level?.players().orEmpty()) {
            for (item in Platform.cosmetics.itemsFor(player.uuid)) desired["${player.uuid}/${item.id}"] = player.uuid to item
        }
        for (uuid in previewTargets) {
            for (item in Platform.cosmetics.itemsFor(uuid)) desired["$uuid/${item.id}"] = uuid to item
        }
        // Drop what's no longer worn or changed (a tint edit lands here).
        val it = entries.iterator()
        while (it.hasNext()) {
            val (key, entry) = it.next()
            val want = desired[key]
            if (want == null || want.second != entry.item) {
                teardown(entry)
                it.remove()
            }
        }
        for ((key, want) in desired) {
            if (entries.containsKey(key)) continue
            val (uuid, item) = want
            entries[key] = Entry(item, cosmetic = null, textures = emptyList(), building = true)
            startBuild(key, uuid, item)
        }
    }

    private fun startBuild(key: String, uuid: UUID, item: Cosmetic3dItem) {
        /*
         * Download, parse, decode and composite run off-thread. Only the cheap GL registration comes back, so cosmetics
         * don't stall the frame.
         */
        buildExecutor.execute {
            val baked = runCatching { bake(item) }.getOrElse {
                Log.main.debug("3D cosmetic {} build failed: {}", item.id, it.toString()); null
            }
            Minecraft.getInstance().execute { applyBaked(key, uuid, item, baked) }
        }
    }

    /**
     * Off-thread: no GL or main-thread state. Null when an asset is missing or undecodable, the attach point is
     * unknown, or there are no layers.
     */
    private fun bake(item: Cosmetic3dItem): Baked? {
        // A missing asset fails silently in game, so log which one.
        val modelBytes = CosmeticAssets.bytes(item.model.sha256, item.model.url) ?: run {
            Log.main.warn("3D cosmetic {} model asset {} unavailable", item.id, item.model.sha256)
            return null
        }
        val decoded = ArrayList<Pair<CosmeticLayer, NativeImage>>(item.layers.size)
        for (layer in item.layers) {
            val bytes = CosmeticAssets.bytes(layer.asset.sha256, layer.asset.url)
            val img = bytes?.let { runCatching { NativeImage.read(it) }.getOrNull() }
            if (img == null) {
                Log.main.warn(
                    "3D cosmetic {} layer '{}' asset {} unavailable or undecodable - skipping the whole cosmetic",
                    item.id, layer.role, layer.asset.sha256,
                )
                decoded.forEach { it.second.close() }
                return null
            }
            decoded += layer to img
        }
        if (decoded.isEmpty()) return null
        val attach = attachmentOf(item.attach) ?: run {
            Log.main.debug("3D cosmetic {} has unknown attach point '{}', skipping", item.id, item.attach.point)
            decoded.forEach { it.second.close() }
            return null
        }
        try {
            val model = ModelFormats.load("model.${item.model.format ?: "bobj"}", String(modelBytes, Charsets.UTF_8))
            val (base, emissive) = composeTextures(item, decoded)
            return Baked(model, attach, base, emissive)
        } finally {
            // The sources were read into the new images, or a bad model threw. Free them either way.
            decoded.forEach { it.second.close() }
        }
    }

    /** Base image plus optional emissive. Mirrors the front's cosmeticRig.retint. */
    private fun composeTextures(
        item: Cosmetic3dItem, decoded: List<Pair<CosmeticLayer, NativeImage>>,
    ): Pair<NativeImage, NativeImage?> {
        // First 'base' layer (otherwise the first one) is the base, 'emissive' feeds the glow pass, the rest overlay.
        val basePair = decoded.firstOrNull { it.first.role.equals("base", ignoreCase = true) } ?: decoded.first()
        val rest = decoded.filter { it !== basePair }
        val overlays = rest.filterNot { it.first.role.equals("emissive", ignoreCase = true) }
            .map { (layer, img) -> TextureCompositor.Layer(img, tintOf(item, layer.tintSlot), supportsAlpha = layer.supportsAlpha) }
        val emissiveLayers = rest.filter { it.first.role.equals("emissive", ignoreCase = true) }
            .map { (layer, img) -> TextureCompositor.Layer(img, tintOf(item, layer.tintSlot), supportsAlpha = layer.supportsAlpha) }
        val base = TextureCompositor.compose(basePair.second, tintOf(item, basePair.first.tintSlot), overlays)
        val emissive = TextureCompositor.composeEmissive(emissiveLayers)
        return base to emissive
    }

    /**
     * Client thread: register textures (GL upload deferred to first bind) and add the cosmetic. Discards results if the
     * entry was removed or superseded.
     */
    private fun applyBaked(key: String, uuid: UUID, item: Cosmetic3dItem, baked: Baked?) {
        val current = entries[key]
        if (current == null || !current.building || current.item != item || baked == null) {
            baked?.let { it.base.close(); it.emissive?.close() }
            // Build failure drops the placeholder so a later reconcile retries.
            if (baked == null && current != null && current.building && current.item == item) entries.remove(key)
            return
        }
        val texId = DynamicTextures.register(idFor(key, item, "base"), baked.base)
        val emissiveId = baked.emissive?.let { DynamicTextures.register(idFor(key, item, "emissive"), it) }
        val cosmetic = PlayerCosmetic(
            model = baked.model, clip = item.clip, texture = texId, emissive = emissiveId, attach = baked.attach, uuid = uuid,
        )
        PlayerCosmetics.add(cosmetic)
        current.cosmetic = cosmetic
        current.textures = listOfNotNull(texId, emissiveId)
        current.building = false
    }

    private fun attachmentOf(a: CosmeticAttach): Attachment? {
        val preset = runCatching { AttachmentPoint.valueOf(a.point) }.getOrNull() ?: return null
        return preset.effective(
            offsetX = a.offsetX, offsetY = a.offsetY, offsetZ = a.offsetZ,
            pitchDeg = a.pitch, yawDeg = a.yaw, rollDeg = a.roll,
            scale = a.scale,
        )
    }

    private fun tintOf(item: Cosmetic3dItem, slot: String?): Int =
        slot?.let { item.tints[it] } ?: 0xFFFFFFFF.toInt()

    /**
     * Includes an appearance hash: BlackVertex caches a `RenderSetup` per texture id forever, so new pixels under an
     * old id would keep drawing the old ones.
     */
    private fun idFor(key: String, item: Cosmetic3dItem, suffix: String): Identifier =
        modId("cosmetic3d/${key.lowercase()}/${appearanceOf(item)}/$suffix")

    /** What decides the pixels: images, order, tints. */
    private fun appearanceOf(item: Cosmetic3dItem): String {
        var hash = 1
        for (layer in item.layers) {
            hash = 31 * hash + layer.asset.sha256.hashCode()
            hash = 31 * hash + layer.role.hashCode()
            hash = 31 * hash + (layer.tintSlot?.let { item.tints[it] } ?: 0)
            hash = 31 * hash + layer.supportsAlpha.hashCode()
        }
        return Integer.toHexString(hash)
    }

    private fun teardown(entry: Entry) {
        entry.cosmetic?.let { PlayerCosmetics.remove(it) }
        val textures = Minecraft.getInstance().textureManager
        for (id in entry.textures) runCatching { textures.release(id) }
    }
}
