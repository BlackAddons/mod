package org.blackaddons.core.backend.auth

import com.google.gson.JsonParser
import java.util.Base64

/**
 * Permissions from our session token. We read them but never verify them. They only decide what to offer, the backend
 * still has the final say. Root allows everything, same as on the backend.
 */
internal data class SessionClaims(val permissions: Set<String>, val root: Boolean) {

    fun allows(permission: String): Boolean = root || permission in permissions

    companion object {
        val NONE = SessionClaims(emptySet(), false)

        fun of(token: String): SessionClaims = runCatching {
            val payload = token.split('.').getOrNull(1) ?: return NONE
            // Unpadded base64url. The URL decoder accepts it.
            val json = JsonParser.parseString(String(Base64.getUrlDecoder().decode(payload))).asJsonObject

            val perm = json.get("perm")?.takeIf { !it.isJsonNull }
            val permissions = when {
                perm == null -> emptySet()
                // One permission is a string, more than one is an array.
                perm.isJsonArray -> perm.asJsonArray.mapNotNull { it.takeIf { e -> !e.isJsonNull }?.asString }.toSet()
                else -> setOf(perm.asString)
            }

            SessionClaims(permissions, json.get("root")?.takeIf { !it.isJsonNull }?.asString == "1")
        }.getOrDefault(NONE)
    }
}
