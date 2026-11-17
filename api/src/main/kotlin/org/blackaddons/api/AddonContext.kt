package org.blackaddons.api

import org.blackaddons.api.util.io.Disk
import java.nio.file.Path

/**
 * Signed proof that the player owns their Minecraft account, for one addon. [jws] is verified by the addon's
 * backend against our JWKS. Short-lived - request one per sign-in, don't store it.
 */
data class Assertion(val jws: String, val expiresInSeconds: Int)

/** Given to exactly one addon at load and not fetchable by id, so capabilities can't be requested for another addon. */
interface AddonContext {

    /** Loader-reported mod id. Unlike [Addon.id] it can't be forged, so addons can't impersonate each other. */
    val addonId: String

    /**
     * This addon's own folder for data of any size and shape, like caches or recordings. It is created on first
     * read, and the layout inside is up to the addon. Write through [Disk] so a crash cannot leave a
     * half-written file.
     */
    val dataDir: Path

    /**
     * Asks the backend to vouch for the player's account, with [addonId] as the audience (no audience
     * parameter on purpose). [nonce] is echoed back as a claim to bind the assertion to one sign-in.
     * Non-blocking; [onResult] runs on the client thread with null if not signed in, unreachable or refused.
     */
    fun requestAssertion(nonce: String? = null, onResult: (Assertion?) -> Unit)
}
