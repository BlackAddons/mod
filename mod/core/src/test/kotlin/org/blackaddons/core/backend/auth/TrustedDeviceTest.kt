package org.blackaddons.core.backend.auth

import org.blackaddons.api.testing.TestPlatform
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import java.time.Instant
import java.util.UUID

/** A device token answers for one account and only while it's alive. Anything else gives nothing. */
class TrustedDeviceTest {

    @TempDir
    lateinit var tempDir: Path

    private val alice = UUID.randomUUID()
    private val bob = UUID.randomUUID()

    @BeforeEach
    fun setUp() {
        TestPlatform.configDir = tempDir
    }

    @Test
    fun `a remembered token survives a round trip`() {
        TrustedDevice.remember(alice, "device-a", inDays(30))
        assertEquals("device-a", TrustedDevice.tokenFor(alice))
    }

    @Test
    fun `one account's token is never offered for another`() {
        TrustedDevice.remember(alice, "device-a", inDays(30))
        assertNull(TrustedDevice.tokenFor(bob))
    }

    @Test
    fun `an expired token is neither offered nor kept`() {
        TrustedDevice.remember(alice, "device-a", inDays(-1))
        assertNull(TrustedDevice.tokenFor(alice))
        // Removed from the file too, so it doesn't get refused again on the next launch.
        assertNull(TrustedDevice.tokenFor(alice))
        assertEquals("{}", tempDir.resolve("device.json").toFile().readText())
    }

    @Test
    fun `forgetting one account leaves the other alone`() {
        TrustedDevice.remember(alice, "device-a", inDays(30))
        TrustedDevice.remember(bob, "device-b", inDays(30))

        TrustedDevice.forget(alice)

        assertNull(TrustedDevice.tokenFor(alice))
        assertEquals("device-b", TrustedDevice.tokenFor(bob))
    }

    /** An unreadable expiry shouldn't shorten the token's life. */
    @Test
    fun `an unreadable expiry falls back to a lifetime rather than to expired`() {
        TrustedDevice.remember(alice, "device-a", "whenever")
        assertEquals("device-a", TrustedDevice.tokenFor(alice))
    }

    private fun inDays(days: Long) = Instant.now().plusSeconds(days * 24 * 60 * 60).toString()
}
