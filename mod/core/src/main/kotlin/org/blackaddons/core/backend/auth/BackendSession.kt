package org.blackaddons.core.backend.auth

import com.google.gson.JsonElement
import com.google.gson.JsonParser
import org.blackaddons.api.host.SessionOwnership
import org.blackaddons.api.util.Uuids
import org.blackaddons.api.util.io.Gsons
import org.blackaddons.core.backend.Backend
import org.blackaddons.core.backend.BackendNotifier
import org.blackaddons.core.backend.Http
import org.blackaddons.core.backend.NetLog
import org.blackaddons.core.backend.Timestamps
import org.blackaddons.core.backend.auth.BackendSession.token
import org.blackaddons.core.log.Log
import java.util.UUID

/**
 * Player JWT from the Mojang handshake: backend `serverId` -> [MojangSession.joinServer] -> `POST /auth/session`.
 * There's no refresh token, so it runs again on expiry. Blocking and serialized, so call it off the client thread.
 */
internal object BackendSession {
    private const val EXPIRY_SKEW_MS = 60_000L
    private const val FALLBACK_TTL_MS = 2 * 60 * 60 * 1000L

    private val lock = Any()

    @Volatile private var token: String? = null
    @Volatile private var expiresAtMs: Long = 0L

    /**
     * The account [token] belongs to. The account switcher changes the player without expiring anything. Without this,
     * calls go out as the previous player.
     */
    @Volatile private var tokenAccount: UUID? = null

    /**
     * Latched: an account with no Mojang session never gets one, so don't run the handshake again on every heartbeat.
     * Keyed by account, so a switch resets it.
     */
    @Volatile private var offlineAccount: UUID? = null

    /** Blacklisted. Latched, because a retry costs a Mojang round trip and one of ten `/v1/auth` requests a minute. */
    @Volatile private var refusedAccount: UUID? = null

    /** Null when auth isn't possible right now (offline, backend down, blacklisted). Skip and retry later. Blocking. */
    fun validToken(): String? {
        unexpiredToken()?.let { return it }
        synchronized(lock) {
            // Another thread might have refreshed the token while we waited.
            unexpiredToken()?.let { return it }
            return if (authenticate() == SessionOwnership.VERIFIED) token else null
        }
    }

    /** Blocking. Answers for a known offline account without network. Addons use the async [SessionVerification]. */
    fun ownership(): SessionOwnership {
        if (unexpiredToken() != null) return SessionOwnership.VERIFIED
        synchronized(lock) {
            if (unexpiredToken() != null) return SessionOwnership.VERIFIED
            return authenticate()
        }
    }

    // Only the refused token, so concurrent refusals don't each wipe a renewed or elevated token.
    fun invalidate(stale: String) {
        synchronized(lock) {
            if (token != stale) return
            dropToken()
        }
    }

    // Manual retry: also clears the blacklist latch, the one refusal a person can lift.
    fun forget() {
        synchronized(lock) {
            dropToken()
            refusedAccount = null
        }
    }

    fun reset() {
        synchronized(lock) { offlineAccount = null }
        forget()
        NetLog.trace("Session reset: token and latches cleared")
    }

    private fun dropToken() {
        token = null
        expiresAtMs = 0L
        tokenAccount = null
        Elevation.clear()
    }

    /** Uses a token we didn't fetch ([Elevation]'s), so every caller gets the raised session at once. */
    fun install(newToken: String, expiresAt: JsonElement?) {
        synchronized(lock) {
            token = newToken
            expiresAtMs = parseExpiry(expiresAt)
            tokenAccount = HostMojangSession.session.uuid
        }
    }

    private fun unexpiredToken(): String? {
        val current = token ?: return null
        if (tokenAccount != null && tokenAccount != HostMojangSession.session.uuid) return null
        return current.takeIf { System.currentTimeMillis() < expiresAtMs - EXPIRY_SKEW_MS }
    }

    /**
     * Mojang -> backend handshake. Stores [token] on success. Returns OFFLINE for an account that's surely offline and
     * UNAVAILABLE for transient failures. Known offline accounts skip the network.
     */
    private fun authenticate(): SessionOwnership {
        // There's no usable token here, so any earlier elevation is already void.
        Elevation.clear()
        val session = HostMojangSession.session
        val username = session.username
        if (username.isNullOrEmpty()) return SessionOwnership.UNAVAILABLE
        val uuid = session.uuid ?: return SessionOwnership.UNAVAILABLE

        if (!Uuids.isAccount(uuid)) return markOffline(uuid, "the account is offline (not a Mojang UUID)")
        if (uuid == offlineAccount) return SessionOwnership.OFFLINE
        if (uuid == refusedAccount) return SessionOwnership.UNAVAILABLE

        NetLog.trace("Session: requesting a challenge for {}", username)
        val serverId = requestChallenge() ?: return SessionOwnership.UNAVAILABLE.also { NetLog.trace("Session: no challenge from the backend") }
        NetLog.trace("Session: joining the Mojang session server")
        when (session.joinServer(serverId).also { NetLog.trace("Session: Mojang join answered {}", it) }) {
            JoinResult.REJECTED -> return markOffline(uuid, "Mojang rejected the session join")
            JoinResult.UNAVAILABLE -> {
                Log.main.warn("Mojang session service unreachable - can't verify right now")
                return SessionOwnership.UNAVAILABLE
            }
            JoinResult.JOINED -> { /* Proven to Mojang, confirm with the backend. */ }
        }

        // A remembered device replaces a 2FA code for staff.
        val device = TrustedDevice.tokenFor(uuid)
        val body = Gsons.compact.toJson(
            buildMap {
                put("username", username)
                put("serverId", serverId)
                device?.let { put("deviceToken", it) }
            },
        )
        // Not `authenticated = true`, because this call is what makes the token.
        val response = Http.send(
            Backend.SESSION_URL,
            method = Http.Method.POST,
            body = body,
            contentType = "application/json",
        )

        // Mojang succeeded, so the backend failed. Tell the player.
        if (response == null) {
            BackendNotifier.notifyAuthFailure()
            return SessionOwnership.UNAVAILABLE
        }
        if (response.status != 200) {
            explainRefusal(response, uuid)
            return SessionOwnership.UNAVAILABLE
        }

        val json = runCatching { JsonParser.parseString(response.body).asJsonObject }.getOrNull()
        if (json == null) {
            BackendNotifier.notifyAuthFailure()
            return SessionOwnership.UNAVAILABLE
        }
        val newToken = json.get("token")?.takeIf { !it.isJsonNull }?.asString
        if (newToken.isNullOrEmpty()) {
            BackendNotifier.notifyAuthFailure()
            return SessionOwnership.UNAVAILABLE
        }

        token = newToken
        expiresAtMs = parseExpiry(json.get("expiresAt"))
        tokenAccount = uuid
        Elevation.onSession(json, uuid, device != null)
        BackendNotifier.onAuthSuccess()
        return SessionOwnership.VERIFIED
    }

    /** Always UNAVAILABLE, only the message changes. 503/429 explain themselves. 403 is latched. */
    private fun explainRefusal(response: Http.Response, uuid: UUID) {
        if (response.busy) return

        if (response.status == 403) {
            if (refusedAccount != uuid) {
                refusedAccount = uuid
                Log.main.warn("Backend refused the account - it is blacklisted. Not retrying.")
            }
            return
        }

        Log.main.warn("Backend session rejected: HTTP {}", response.status)
        BackendNotifier.notifyAuthFailure()
    }

    private fun markOffline(uuid: UUID, reason: String): SessionOwnership {
        if (offlineAccount != uuid) {
            offlineAccount = uuid
            Log.main.warn("Backend auth skipped - {}. Not retrying unless the signed-in account changes.", reason)
        }
        return SessionOwnership.OFFLINE
    }

    private fun parseExpiry(el: JsonElement?): Long =
        Timestamps.parse(el?.takeIf { !it.isJsonNull }?.asString)
            ?: (System.currentTimeMillis() + FALLBACK_TTL_MS)

    private fun requestChallenge(): String? {
        val response = Http.send(Backend.CHALLENGE_URL, method = Http.Method.POST)
        if (response == null) {
            BackendNotifier.notifyAuthFailure()
            return null
        }
        if (response.status != 200) {
            // Throttled or degraded stays quiet.
            if (!response.busy) {
                Log.main.warn("Session challenge refused: HTTP {}", response.status)
                BackendNotifier.notifyAuthFailure()
            }
            return null
        }
        val serverId = runCatching {
            JsonParser.parseString(response.body).asJsonObject.get("serverId")?.takeIf { !it.isJsonNull }?.asString
        }.getOrNull()
        if (serverId.isNullOrEmpty()) {
            Log.main.warn("Session challenge carried no serverId")
            BackendNotifier.notifyAuthFailure()
            return null
        }
        return serverId
    }
}
