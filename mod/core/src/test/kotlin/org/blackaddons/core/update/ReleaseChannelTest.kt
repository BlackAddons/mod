package org.blackaddons.core.update

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/** The gateway can close the socket because of this, so it has to be right. */
class ReleaseChannelTest {

    private infix fun String.isOn(channel: String) = assertEquals(channel, ReleaseChannel.of(this))

    @Test
    fun `a plain release is on the default track`() {
        "1.1.0" isOn "release"
        "1.1.0+ci.7" isOn "release"
    }

    @Test
    fun `the prerelease tag names the track`() {
        "1.1.0-beta.3" isOn "beta"
        "1.1.0-rc.1" isOn "rc"
        "1.1.0-Beta.3" isOn "beta"
    }

    @Test
    fun `a version that isn't one is on the default track`() {
        assertEquals("release", ReleaseChannel.of(null))
        "" isOn "release"
        "some-dev-build" isOn "release"
    }

    @Test
    fun `a tag the backend could not have is not asked for`() {
        // Underscores or long tags aren't valid slugs and would look like "nothing published" (silence).
        "1.1.0-pre_view" isOn "release"
        "1.1.0-averylongchannelname" isOn "release"
    }
}
