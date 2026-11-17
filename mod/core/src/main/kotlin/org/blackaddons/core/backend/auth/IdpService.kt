package org.blackaddons.core.backend.auth

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import org.blackaddons.api.Assertion
import org.blackaddons.core.backend.Backend
import org.blackaddons.core.backend.Http
import org.blackaddons.core.log.Log

/**
 * "Sign in with BlackAddons" assertions for addons. The audience is the mod id from the loader, never the addon's own
 * claim.
 */
internal object IdpService {

    /** Same rule as the backend, so a bad mod id shows up as an authoring mistake. */
    private val AUDIENCE = Regex("^[a-z0-9][a-z0-9._-]{2,63}$")

    fun isValidAudience(audience: String): Boolean = AUDIENCE.matches(audience)

    /** Null if not signed in, unreachable or refused. Blocking. */
    fun assertion(audience: String, nonce: String?): Assertion? {
        if (!isValidAudience(audience)) {
            Log.main.warn(
                "Addon '{}' can't be issued identity assertions: a mod id has to be 3-64 characters of " +
                    "lowercase letters, digits, dots, dashes or underscores to be a valid audience",
                audience,
            )
            return null
        }

        val body = JsonObject().apply {
            addProperty("audience", audience)
            nonce?.let { addProperty("nonce", it) }
        }

        val response = Http.send(
            Backend.IDP_ASSERTION_URL,
            method = Http.Method.POST,
            body = body.toString(),
            contentType = "application/json",
            authenticated = true,
        ) ?: return null

        if (response.status != 200) {
            // Addons only need "no proof today". The status is for our logs.
            Log.main.debug("Assertion for '{}' refused with HTTP {}", audience, response.status)
            return null
        }

        return parse(response.body)
    }

    private fun parse(body: String): Assertion? = runCatching {
        val o = JsonParser.parseString(body).asJsonObject
        val jws = o.get("assertion")?.takeIf { it.isJsonPrimitive }?.asString?.takeIf { it.isNotBlank() }
            ?: return null
        Assertion(jws, o.get("expiresIn")?.takeIf { it.isJsonPrimitive }?.asInt ?: 0)
    }.getOrElse {
        Log.main.warn("Couldn't read the assertion response: {}", it.toString())
        null
    }
}
