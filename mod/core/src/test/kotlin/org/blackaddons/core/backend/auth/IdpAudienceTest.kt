package org.blackaddons.core.backend.auth

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** The audience is the mod id, so the backend's rule (its exact regex) gets checked here, not as a 400 at sign-in. */
class IdpAudienceTest {

    @Test
    fun `the mod ids we ship are usable audiences`() {
        // One id per addon, not per Minecraft version, because a suffixed id would make two relying parties.
        for (id in listOf("blackaddons", "blackaddons-cheat", "blackaddons-extras")) {
            assertTrue(IdpService.isValidAudience(id), "$id should be a valid audience")
        }
    }

    @Test
    fun `dots, dashes and underscores are allowed after the first character`() {
        assertTrue(IdpService.isValidAudience("com.example.myaddon"))
        assertTrue(IdpService.isValidAudience("my_addon"))
        assertTrue(IdpService.isValidAudience("a1b"))
    }

    @Test
    fun `it must start with a letter or digit`() {
        assertFalse(IdpService.isValidAudience("-addon"))
        assertFalse(IdpService.isValidAudience(".addon"))
        assertFalse(IdpService.isValidAudience("_addon"))
    }

    @Test
    fun `uppercase is rejected`() {
        // Valid in a Fabric mod id, but not in an audience.
        assertFalse(IdpService.isValidAudience("MyAddon"))
    }

    @Test
    fun `it must be 3 to 64 characters`() {
        assertFalse(IdpService.isValidAudience(""))
        assertFalse(IdpService.isValidAudience("ab"))
        assertTrue(IdpService.isValidAudience("abc"))
        assertTrue(IdpService.isValidAudience("a".repeat(64)))
        assertFalse(IdpService.isValidAudience("a".repeat(65)))
    }

    @Test
    fun `a mod id with a space or slash is not an audience`() {
        assertFalse(IdpService.isValidAudience("my addon"))
        assertFalse(IdpService.isValidAudience("my/addon"))
    }
}
