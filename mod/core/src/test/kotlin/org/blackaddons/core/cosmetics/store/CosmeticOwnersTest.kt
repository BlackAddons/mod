package org.blackaddons.core.cosmetics.store
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.UUID

/** A malformed field costs one entry, never the whole directory. */
class CosmeticOwnersTest {

    private fun document(json: String): JsonObject = JsonParser.parseString(json).asJsonObject

    @Test
    fun `reads owners and keys them by lowercased name`() {
        val parsed = CosmeticOwners.parse(
            document(
                """
                { "owners": [
                    { "name": "AtkLxve",        "uuid": "11111111-2222-3333-4444-555555555555" },
                    { "name": "DreamcastERA20", "uuid": "66666666-7777-8888-9999-000000000000" }
                ] }
                """,
            ),
        )

        assertEquals(2, parsed.size)
        assertEquals(UUID.fromString("11111111-2222-3333-4444-555555555555"), parsed["atklxve"])
        assertEquals(UUID.fromString("66666666-7777-8888-9999-000000000000"), parsed["dreamcastera20"])
    }

    @Test
    fun `a broken entry costs only itself`() {
        val parsed = CosmeticOwners.parse(
            document(
                """
                { "owners": [
                    { "name": "NoUuid" },
                    { "uuid": "11111111-2222-3333-4444-555555555555" },
                    { "name": "BadUuid", "uuid": "not-a-uuid" },
                    { "name": "",        "uuid": "11111111-2222-3333-4444-555555555555" },
                    "not even an object",
                    { "name": "Survivor", "uuid": "99999999-8888-7777-6666-555555555555" }
                ] }
                """,
            ),
        )

        assertEquals(1, parsed.size)
        assertEquals(UUID.fromString("99999999-8888-7777-6666-555555555555"), parsed["survivor"])
    }

    @Test
    fun `a document without the owners array is simply empty`() {
        assertTrue(CosmeticOwners.parse(document("""{ "version": "2026-08-04" }""")).isEmpty())
        assertTrue(CosmeticOwners.parse(document("""{ "owners": [] }""")).isEmpty())
    }
}
