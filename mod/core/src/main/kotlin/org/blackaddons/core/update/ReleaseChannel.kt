package org.blackaddons.core.update

import org.blackaddons.api.Platform

/**
 * This build's release track, sent with `GET /v1/version` and gateway `IDENTIFY`. Without it the backend assumes
 * [DEFAULT], and it judged betas against the stable track. It comes from the version's prerelease tag, so
 * `1.1.0-beta.3` has to be published on `beta`.
 */
internal object ReleaseChannel {

    /** Untagged builds, and what the backend assumes. */
    const val DEFAULT = "release"

    /** Lowercase, max 16 characters. The backend knows nothing else. */
    private val SLUG = Regex("^[a-z0-9][a-z0-9-]{0,15}$")

    val current: String by lazy { of(Platform.blackaddonsVersion) }

    fun of(version: String?): String {
        val tag = ModVersion.parse(version)?.prerelease?.firstOrNull()?.lowercase() ?: return DEFAULT
        // An unknown channel would return nothing, which looks like being up to date.
        return if (SLUG.matches(tag)) tag else DEFAULT
    }
}
