package org.blackaddons.core.profile

import org.blackaddons.api.Platform
import org.blackaddons.api.skyblock.profile.PlayerProfiles
import org.blackaddons.api.skyblock.profile.ProfileInventory
import org.blackaddons.api.skyblock.profile.ProfileLookup
import org.blackaddons.api.skyblock.profile.ProfileMember
import org.blackaddons.api.skyblock.profile.SkyblockProfiles
import org.blackaddons.api.util.concurrent.OffThread
import java.util.UUID

internal object CoreSkyblockProfiles : SkyblockProfiles {

    override fun byName(name: String, onResult: (ProfileLookup) -> Unit) = lookUp(onResult) { ProfileService.load(name) }

    override fun byUuid(uuid: UUID, onResult: (ProfileLookup) -> Unit) = lookUp(onResult) { ProfileService.load(uuid) }

    override fun cached(uuid: UUID): PlayerProfiles? = ProfileService.cachedProfiles(uuid)

    override fun inventory(member: ProfileMember, onResult: (ProfileInventory) -> Unit) =
        ProfileInventories.load(member, onResult)

    private fun lookUp(onResult: (ProfileLookup) -> Unit, load: () -> LoadResult) = OffThread.run(
        name = "profile-lookup",
        work = { answer(load()) },
        onFailure = { ProfileLookup.Unavailable },
        onResult = { lookup -> Platform.screenHost.onClientThread { onResult(lookup) } },
    )

    private fun answer(result: LoadResult): ProfileLookup = when (result) {
        is LoadResult.Loaded -> ProfileLookup.Found(result.profiles)
        LoadResult.PlayerNotFound -> ProfileLookup.PlayerNotFound
        is LoadResult.NoProfiles -> ProfileLookup.NoProfiles(result.username)
        LoadResult.Unavailable -> ProfileLookup.Unavailable
    }
}
