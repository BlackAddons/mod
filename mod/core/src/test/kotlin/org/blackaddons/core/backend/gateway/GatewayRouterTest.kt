package org.blackaddons.core.backend.gateway

import com.google.gson.JsonParser
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import java.util.UUID

/**
 * A `cosmetics` event that names nobody means the catalog moved (resolve the lobby again). Naming only unreadable
 * players means do nothing. Both look like "no UUIDs", but they cost the backend very differently.
 */
class GatewayRouterTest {

    private fun named(json: String): List<UUID>? =
        GatewayRouter.playersNamed(JsonParser.parseString(json).asJsonObject)

    private val alice = UUID.fromString("069a79f4-44e9-4726-a5be-fca90e38aaf5")
    private val bob = UUID.fromString("6ab43178-89fd-4905-97f6-0f67d9d76fd9")

    @Test
    fun `named players come back in order`() {
        assertEquals(listOf(alice, bob), named("""{ "players": ["$alice", "$bob"] }"""))
    }

    @Test
    fun `an absent list means everyone`() {
        assertNull(named("""{ "type": "cosmetics", "version": "1a2b" }"""))
    }

    @Test
    fun `an empty list means everyone`() {
        assertNull(named("""{ "players": [] }"""))
    }

    @Test
    fun `a list that is not a list means everyone`() {
        assertNull(named("""{ "players": "everybody" }"""))
    }

    @Test
    fun `an unreadable name costs only itself`() {
        assertEquals(listOf(alice), named("""{ "players": ["$alice", "not-a-uuid"] }"""))
    }

    @Test
    fun `names that are all unreadable ask about nobody, not about everybody`() {
        assertEquals(emptyList<UUID>(), named("""{ "players": ["not-a-uuid", 7, null] }"""))
    }
}
