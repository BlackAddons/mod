package org.blackaddons.core.profile

import org.blackaddons.api.skyblock.profile.PlayerProfiles

internal interface ProfileSource {
    val name: String

    fun fetch(id: MojangId): SourceResult
}

internal sealed interface SourceResult {
    /** [recent] is keyed by profile id, for [TeammateHistory]. */
    class Ok(val profiles: PlayerProfiles, val recent: Map<String, List<RecentActivity>>) : SourceResult

    /** No profiles. Don't fall through. */
    object Empty : SourceResult

    /** Down, rate-limited or unparseable. Try the next one. */
    object Unavailable : SourceResult
}
