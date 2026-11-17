package org.blackaddons.core.backend.gateway

import com.google.gson.JsonParser
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.util.UUID

/**
 * `PRESENCE` is both a subscription answer and a single transition, read the same way. A frame edits what's known.
 * Treating a transition as the whole truth would unmark the lobby.
 */
class PresenceTest {

    private val alice = UUID.fromString("069a79f4-44e9-4726-a5be-fca90e38aaf5")
    private val bob = UUID.fromString("6ab43178-89fd-4905-97f6-0f67d9d76fd9")

    private fun frame(json: String) = Presence.onFrame(JsonParser.parseString(json).asJsonObject)

    @BeforeEach
    fun clear() {
        // A session cache. A lost socket empties it in game too.
        Presence.onSocketLost()
    }

    @Test
    fun `a snapshot marks everyone it names`() {
        frame("""{ "players": [ { "playerUuid": "$alice", "online": true },
                                { "playerUuid": "$bob", "online": true } ] }""")

        assertTrue(Presence.isOnline(alice))
        assertTrue(Presence.isOnline(bob))
        assertTrue(Presence.anyOnline)
    }

    @Test
    fun `a transition changes only the player it names`() {
        frame("""{ "players": [ { "playerUuid": "$alice", "online": true },
                                { "playerUuid": "$bob", "online": true } ] }""")

        frame("""{ "players": [ { "playerUuid": "$bob", "online": false } ] }""")

        assertTrue(Presence.isOnline(alice)) { "a frame about Bob said nothing about Alice" }
        assertFalse(Presence.isOnline(bob))
    }

    @Test
    fun `a player who was never named is not online`() {
        frame("""{ "players": [ { "playerUuid": "$alice", "online": true } ] }""")
        assertFalse(Presence.isOnline(bob))
        assertFalse(Presence.isOnline(null))
    }

    @Test
    fun `an entry missing the flag is not taken for a yes`() {
        frame("""{ "players": [ { "playerUuid": "$alice" } ] }""")
        assertFalse(Presence.isOnline(alice))
    }

    @Test
    fun `an unreadable entry costs only itself`() {
        frame("""{ "players": [ { "playerUuid": "not-a-uuid", "online": true },
                                7,
                                { "playerUuid": "$alice", "online": true } ] }""")

        assertTrue(Presence.isOnline(alice))
    }

    @Test
    fun `a frame with nothing to say leaves what is known alone`() {
        frame("""{ "players": [ { "playerUuid": "$alice", "online": true } ] }""")

        frame("""{ }""")
        frame("""{ "players": "everyone" }""")

        assertTrue(Presence.isOnline(alice))
    }

    @Test
    fun `losing the socket forgets everybody`() {
        frame("""{ "players": [ { "playerUuid": "$alice", "online": true } ] }""")

        // The server keeps no subscriptions across sockets, so the mark would go stale.
        Presence.onSocketLost()

        assertFalse(Presence.isOnline(alice))
        assertFalse(Presence.anyOnline)
    }
}
