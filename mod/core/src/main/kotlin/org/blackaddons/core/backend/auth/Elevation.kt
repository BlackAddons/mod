package org.blackaddons.core.backend.auth

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import org.blackaddons.api.Platform
import org.blackaddons.api.chat.ChatClick
import org.blackaddons.api.chat.ChatMessage
import org.blackaddons.api.util.I18n
import org.blackaddons.api.util.concurrent.OffThread
import org.blackaddons.api.util.io.Gsons
import org.blackaddons.core.backend.Backend
import org.blackaddons.core.backend.Http
import org.blackaddons.core.backend.auth.Elevation.possible
import org.blackaddons.core.backend.auth.Elevation.withheld
import org.blackaddons.core.chat.Brand
import org.blackaddons.core.chat.brand
import org.blackaddons.core.ui.screen.StaffSignInModal
import java.awt.Color
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean

/**
 * In-game 2FA. Raises the Mojang-handshake session (player permissions) to the account's permissions. [possible] means
 * a code would be accepted. [withheld] means raising adds something. They're separate, because a normal player with 2FA
 * has the first but not the second. Today it unlocks `chat.moderate`. On expiry the session drops back to one factor,
 * unless the device is remembered ([TrustedDevice]).
 */
internal object Elevation {

    enum class Outcome { RAISED, BAD_CODE, THROTTLED, NOT_POSSIBLE, FAILED }

    private enum class Gate { READY, ALREADY_RAISED, NO_SECOND_FACTOR, UNAVAILABLE }

    /** No leading slash. */
    private const val ELEVATE_COMMAND = "blackaddons elevate"

    /** The only thing elevation unlocks in game today. */
    const val CHAT_MODERATE = "chat.moderate"

    @Volatile
    var elevated = false
        private set

    /** An account backs this handshake and has a second factor. */
    @Volatile
    var possible = false
        private set

    @Volatile
    var withheld = false
        private set

    @Volatile
    private var claims = SessionClaims.NONE

    private val offered = AtomicBoolean(false)

    /** Reads from the token, so buttons only show for players the backend would allow. */
    fun allows(permission: String): Boolean = claims.allows(permission)

    /** Called when the token stops being ours, so an account switch can't keep moderator buttons. */
    fun clear() {
        claims = SessionClaims.NONE
        elevated = false
        possible = false
        withheld = false
        offered.set(false)
    }

    /**
     * [presentedDevice]: the backend doesn't refuse a dead device token, the session just comes back with one factor.
     * That's the only sign the device isn't trusted anymore.
     */
    fun onSession(json: JsonObject, uuid: UUID, presentedDevice: Boolean) {
        claims = json.string("token")?.let(SessionClaims::of) ?: SessionClaims.NONE
        elevated = json.flag("elevated")
        possible = json.flag("elevationPossible")
        withheld = json.flag("permissionsWithheld")

        if (presentedDevice && !elevated) TrustedDevice.forget(uuid)

        // Makes the offer available again for a later one-factor session.
        if (!withheld) {
            offered.set(false)
            return
        }
        if (offered.compareAndSet(false, true)) offer()
    }

    /** Off-thread. Before a session exists, the correct answer is "not asked yet", not "no second factor". */
    fun requestSignIn() {
        OffThread.run(
            name = "elevation-check",
            work = { gate() },
            onFailure = { Gate.UNAVAILABLE },
            onResult = { gate -> Platform.scheduler.schedule(0) { act(gate) } },
        )
    }

    /** TOTP or recovery code. [onDone] runs on the worker. */
    fun elevate(code: String, remember: Boolean, onDone: (Outcome) -> Unit) {
        OffThread.run(
            name = "session-elevate",
            work = { request(code.trim(), remember) },
            onFailure = { Outcome.FAILED },
            onResult = onDone,
        )
    }

    private fun gate(): Gate {
        if (BackendSession.validToken() == null) return Gate.UNAVAILABLE
        return when {
            elevated -> Gate.ALREADY_RAISED
            possible -> Gate.READY
            else -> Gate.NO_SECOND_FACTOR
        }
    }

    private fun act(gate: Gate) = when (gate) {
        Gate.READY -> StaffSignInModal.open()
        Gate.ALREADY_RAISED -> say("blackaddons.elevation.already", Brand.OK)
        Gate.NO_SECOND_FACTOR -> postEnable2fa()
        Gate.UNAVAILABLE -> say("blackaddons.elevation.unavailable", Brand.FAIL)
    }

    private fun request(code: String, remember: Boolean): Outcome {
        val uuid = HostMojangSession.session.uuid ?: return Outcome.FAILED
        val token = BackendSession.validToken() ?: return Outcome.FAILED
        var response = post(code, remember, token) ?: return Outcome.FAILED

        /*
         * A 401 is a bad code or a stale bearer. Only a bad code has an error body. Without this check, a stale session
         * rejects every code.
         */
        if (response.status == 401 && !saysBadCode(response)) {
            BackendSession.invalidate(token)
            val fresh = BackendSession.validToken() ?: return Outcome.FAILED
            response = post(code, remember, fresh) ?: return Outcome.FAILED
        }

        when (response.status) {
            200 -> Unit
            // Both 400s mean there's nothing to raise. Don't retry.
            400 -> return Outcome.NOT_POSSIBLE
            401 -> return Outcome.BAD_CODE
            429 -> return Outcome.THROTTLED
            else -> return Outcome.FAILED
        }

        val json = runCatching { JsonParser.parseString(response.body).asJsonObject }.getOrNull()
            ?: return Outcome.FAILED
        val raised = json.string("token") ?: return Outcome.FAILED

        BackendSession.install(raised, json.get("expiresAt"))
        claims = SessionClaims.of(raised)
        elevated = true
        withheld = false
        // Reset: without a remembered device, the session drops to one factor on expiry.
        offered.set(false)
        json.string("deviceToken")?.let { TrustedDevice.remember(uuid, it, json.string("deviceTokenExpiresAt")) }
        return Outcome.RAISED
    }

    /**
     * Attaches the token by hand. The `authenticated` path answers a 401 with a new handshake, which wastes a
     * rate-limited auth request on a typo.
     */
    private fun post(code: String, remember: Boolean, token: String) = Http.send(
        Backend.ELEVATE_URL,
        method = Http.Method.POST,
        body = Gsons.compact.toJson(mapOf("code" to code, "rememberDevice" to remember)),
        contentType = "application/json",
        headers = mapOf("Authorization" to "Bearer $token"),
    )

    /** The backend gives a reason. The auth pipeline never does. */
    private fun saysBadCode(response: Http.Response): Boolean = response.error != null

    private fun offer() {
        if (!possible) {
            postEnable2fa()
            return
        }
        Platform.chat.local(ChatMessage.build {
            brand()
            append(I18n.tr("blackaddons.elevation.offer") + " ", Brand.BODY)
            append(
                I18n.tr("blackaddons.elevation.sign_in"),
                Brand.OK, underlined = true,
                click = ChatClick.RunCommand(ELEVATE_COMMAND),
                hover = I18n.tr("blackaddons.elevation.sign_in_hover"),
            )
        })
    }

    fun postEnable2fa() {
        Platform.chat.local(ChatMessage.build {
            brand()
            append(I18n.tr("blackaddons.elevation.needs_2fa") + " ", Brand.BODY)
            append(
                I18n.tr("blackaddons.elevation.enable_2fa"),
                Brand.OK, underlined = true,
                click = ChatClick.OpenUrl(Backend.TWO_FACTOR_URL),
                hover = Backend.TWO_FACTOR_URL,
            )
        })
    }

    private fun say(key: String, color: Color) {
        Platform.chat.local(ChatMessage.build {
            brand()
            append(I18n.tr(key), color)
        })
    }

    private fun JsonObject.flag(name: String): Boolean =
        get(name)?.takeIf { !it.isJsonNull }?.asBoolean ?: false

    private fun JsonObject.string(name: String): String? =
        get(name)?.takeIf { !it.isJsonNull }?.asString?.takeIf { it.isNotBlank() }
}
