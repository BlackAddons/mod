package org.blackaddons.api.skyblock.profile

import java.util.UUID

/** What a profile lookup found. */
sealed interface ProfileLookup {
    class Found(val profiles: PlayerProfiles) : ProfileLookup

    /** No Minecraft account has that name or id. */
    object PlayerNotFound : ProfileLookup

    /** The account exists but has never played Skyblock. */
    class NoProfiles(val name: String) : ProfileLookup

    /** Hypixel or the backend did not answer; try again later. */
    object Unavailable : ProfileLookup
}

/**
 * Skyblock profiles through the BlackAddons backend, parsed into [PlayerProfiles]. One cache serves every caller,
 * including the Profile Viewer, for a few minutes per player. [onResult] runs on the client thread.
 */
interface SkyblockProfiles {
    /** [name] is matched case-insensitively. */
    fun byName(name: String, onResult: (ProfileLookup) -> Unit)

    fun byUuid(uuid: UUID, onResult: (ProfileLookup) -> Unit)

    /** What an earlier lookup already loaded, without a request. Null when nothing is cached or it expired. */
    fun cached(uuid: UUID): PlayerProfiles?

    /**
     * [member]'s inventories, decoded once per member and kept. Decoding a full set takes a second or more, which is
     * why the lookup does not include it. Ask for it when you are about to show it.
     */
    fun inventory(member: ProfileMember, onResult: (ProfileInventory) -> Unit)
}
