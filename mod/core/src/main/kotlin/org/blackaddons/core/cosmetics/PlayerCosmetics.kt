package org.blackaddons.core.cosmetics

import org.blackaddons.api.Platform
import org.blackaddons.api.cosmetics.Cosmetic
import org.blackaddons.api.cosmetics.Cosmetic3dItem
import org.blackaddons.api.cosmetics.CosmeticHost
import org.blackaddons.api.cosmetics.NickFont
import org.blackaddons.api.cosmetics.NickFontFile
import org.blackaddons.api.cosmetics.Scale
import org.blackaddons.core.backend.auth.HostMojangSession
import org.blackaddons.core.cosmetics.mesh.NpcCatEars
import org.blackaddons.core.cosmetics.store.CosmeticStore
import org.blackaddons.core.features.impl.visual.Cosmetics
import org.blackaddons.core.features.impl.visual.CustomNickname
import org.blackaddons.core.font.NickFontFiles
import org.blackaddons.core.profile.PlayerNames
import java.util.UUID

/**
 * The mod's [CosmeticHost]. Merges local features ([CustomNickname], [PlayerScale]) with the server catalog
 * ([CosmeticStore]). [Cosmetics] gates other people's cosmetics. Registered as [Platform.cosmetics], the one source
 * everything asks.
 */
object PlayerCosmetics : CosmeticHost {

    override fun scaleFor(uuid: UUID?): Scale? = PlayerScale.of(uuid)

    override fun nameFor(uuid: UUID?): Cosmetic? {
        if (uuid != null && uuid == Platform.player.uuid) {
            CustomNickname.local()?.let { return it }
        }
        return server(uuid)
    }

    /** For UIs that know a player by name (dungeon map, leap menu): [uuid] if known, else one seen this session. */
    fun nameFor(name: String, uuid: UUID?): Cosmetic? = nameFor(
        uuid ?: PlayerNames.uuidFor(name)
            ?: Platform.player.uuid?.takeIf { name.equals(Platform.player.name, ignoreCase = true) },
    )

    override fun capeUrlFor(uuid: UUID?): String? = server(uuid)?.capeUrl

    override fun fontFile(file: NickFontFile): String? = NickFontFiles.path(file)?.toAbsolutePath()?.toString()

    /** Preview-only items (wardrobe fitting room on your UUID, mannequins on fake UUIDs). Nobody else sees them. */
    @Volatile var previewItems: Map<UUID, List<Cosmetic3dItem>> = emptyMap()
        set(value) {
            field = value
            bumpEpoch()
        }

    /** Incremented on every change, so the renderer picks it up next tick. */
    @Volatile var previewEpoch = 0
        private set

    /**
     * Own cosmetic the server confirmed. Fills the gap (up to one heartbeat) until a resolve catches up. Only written
     * from accepted saves, never from drafts. Each editor merges only its own facets, so a nick edit doesn't remove
     * your cape. [CosmeticStore] drops it when a resolve answers for us.
     */
    @Volatile
    private var own: Cosmetic? = null

    // The player [own] belongs to. The account switcher changes the player without telling us.
    @Volatile
    private var ownAccount: UUID? = null

    internal fun ownItemsSaved(items: List<Cosmetic3dItem>) = mergeOwn { it.copy(items = items) }

    internal fun ownFontSaved(font: NickFont?) = mergeOwn { it.copy(font = font) }

    internal fun ownNameSaved(saved: Cosmetic) =
        mergeOwn { it.copy(display = saved.display, name = saved.name, size = saved.size, style = saved.style) }

    // Writers run on three threads. Without the lock, two merges dropped each other's facets.
    @Synchronized
    internal fun clearOwn() {
        own = null
        ownAccount = null
        bumpEpoch()
    }

    @Synchronized
    private fun bumpEpoch() {
        previewEpoch++
    }

    @Synchronized
    private fun mergeOwn(edit: (Cosmetic) -> Cosmetic) {
        val account = HostMojangSession.session.uuid
        val base = own?.takeIf { ownAccount == account } ?: account?.let { CosmeticStore.lookup(it) } ?: Cosmetic()
        own = edit(base)
        ownAccount = account
        bumpEpoch()
    }

    override fun itemsFor(uuid: UUID?): List<Cosmetic3dItem> {
        uuid ?: return emptyList()
        previewItems[uuid]?.let { return it }
        if (shown(uuid)) NpcCatEars.itemsFor(uuid)?.let { return it }
        return server(uuid)?.items ?: emptyList()
    }

    /**
     * Null if [Cosmetics] is off, or others are hidden and it's not u. Public, because [PlayerScale] needs the same
     * gate.
     */
    fun server(uuid: UUID?): Cosmetic? {
        uuid ?: return null
        if (!shown(uuid)) return null
        // Behind the same gates, so turning cosmetics off hides yours too.
        if (uuid == Platform.player.uuid && uuid == ownAccount) own?.let { return it }
        return CosmeticStore.lookup(uuid)
    }

    private fun shown(uuid: UUID): Boolean =
        Cosmetics.enabled && (uuid == Platform.player.uuid || Cosmetics.showOthers)
}
