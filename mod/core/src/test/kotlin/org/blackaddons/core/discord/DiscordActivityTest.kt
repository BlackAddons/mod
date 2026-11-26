package org.blackaddons.core.discord

import com.google.gson.JsonParser
import org.blackaddons.api.util.io.Gsons
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class DiscordActivityTest {

    @Test
    fun `text Discord would reject is fixed instead of losing the whole activity`() {
        val activity = DiscordActivity.of(details = "x".repeat(200), state = "F")

        assertEquals(128, activity.details!!.length)
        assertEquals("F ", activity.state)
        assertNull(DiscordActivity.of(details = "   ").details)
    }

    @Test
    fun `a party without a state is dropped, since Discord has nowhere to draw it`() {
        val party = DiscordActivity.Party("id", listOf(3, 5))

        assertNull(DiscordActivity.of(details = "Catacombs", party = party).party)
        assertEquals(party, DiscordActivity.of(details = "Catacombs", state = "Mage 50", party = party).party)
    }

    @Test
    fun `the payload uses Discord's field names and leaves unset fields out`() {
        val json = JsonParser.parseString(
            Gsons.compact.toJson(DiscordActivity.of(details = "Hub", image = "https://x/logo.png", imageText = "BA")),
        ).asJsonObject

        assertEquals("https://x/logo.png", json.getAsJsonObject("assets").get("large_image").asString)
        assertEquals("BA", json.getAsJsonObject("assets").get("large_text").asString)
        assertFalse(json.has("state"))
        assertFalse(json.has("party"))
        assertFalse(json.has("buttons"))
    }
}
