package org.blackaddons.core.backend.auth

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.Base64

/**
 * Every claim shape the backend sends has to read the same: one permission as a string, more as an array, root with
 * none but allowing everything. An unreadable token gives nothing and doesn't throw (a throw on the auth path costs the
 * session).
 */
class SessionClaimsTest {

    @Test
    fun `several permissions arrive as an array`() {
        val claims = SessionClaims.of(token("""{"perm":["chat.staff","chat.moderate"]}"""))
        assertEquals(setOf("chat.staff", "chat.moderate"), claims.permissions)
        assertTrue(claims.allows("chat.moderate"))
    }

    @Test
    fun `a single permission arrives as a bare string`() {
        val claims = SessionClaims.of(token("""{"perm":"chat.staff"}"""))
        assertEquals(setOf("chat.staff"), claims.permissions)
        assertFalse(claims.allows("chat.moderate"))
    }

    @Test
    fun `root satisfies a permission it does not list`() {
        assertTrue(SessionClaims.of(token("""{"root":"1"}""")).allows("chat.moderate"))
    }

    @Test
    fun `a one-factor token carrying no permissions allows nothing`() {
        val claims = SessionClaims.of(token("""{"type":"player"}"""))
        assertTrue(claims.permissions.isEmpty())
        assertFalse(claims.allows("chat.moderate"))
    }

    @Test
    fun `anything that is not a readable token allows nothing`() {
        assertEquals(SessionClaims.NONE, SessionClaims.of("not-a-jwt"))
        assertEquals(SessionClaims.NONE, SessionClaims.of("header.%%%.signature"))
        assertEquals(SessionClaims.NONE, SessionClaims.of(""))
    }

    private fun token(payload: String): String {
        val encoded = Base64.getUrlEncoder().withoutPadding().encodeToString(payload.toByteArray())
        return "header.$encoded.signature"
    }
}
