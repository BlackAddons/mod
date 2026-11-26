package org.blackaddons.core.cosmetics.store

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import org.blackaddons.api.cosmetics.ColorSpec
import org.blackaddons.api.cosmetics.Cosmetic
import org.blackaddons.api.cosmetics.NickStyle
import org.blackaddons.api.cosmetics.Scale
import org.blackaddons.api.util.color.ColorStop
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.util.UUID

/**
 * Wire format and disk cache. A lossy change silently drops a player's cosmetic. Pins both wire shapes, the four color
 * modes, tint hex -> ARGB, and the cache round trip.
 */
class CosmeticJsonTest {

    private val uuid = UUID.fromString("11111111-1111-1111-1111-111111111111")

    private fun obj(json: String): JsonObject = JsonParser.parseString(json).asJsonObject

    @Test
    fun `resolve on an unknown schema version is ignored`() {
        val root = obj("""{ "version": 2, "players": { "$uuid": { "nick": { "displayName": "X" } } } }""")
        assertTrue(CosmeticJson.fromResolve(root).isEmpty())
    }

    @Test
    fun `resolve accepts version 1 and a missing version`() {
        val v1 = obj("""{ "version": 1, "players": { "$uuid": { "nick": { "displayName": "X" } } } }""")
        assertEquals("X", CosmeticJson.fromResolve(v1)[uuid]?.display)

        val noVersion = obj("""{ "players": { "$uuid": { "nick": { "displayName": "Y" } } } }""")
        assertEquals("Y", CosmeticJson.fromResolve(noVersion)[uuid]?.display)
    }

    @Test
    fun `styles are read, a nick without them is plain, and the cache keeps them`() {
        val root = obj(
            """
            { "version": 1, "players": {
                "$uuid": { "nick": { "prefix": "[VIP]", "style": { "bold": true, "obfuscated": true }, "prefixStyle": { "italic": true } } },
                "22222222-2222-2222-2222-222222222222": { "nick": { "displayName": "Old" } }
            } }
            """.trimIndent()
        )
        val resolved = CosmeticJson.fromResolve(root)
        val styled = resolved.getValue(uuid)
        assertEquals(NickStyle(bold = true, obfuscated = true), styled.style)
        assertEquals(NickStyle(italic = true), styled.prefixStyle)
        // A style alone changes the name: the vanilla name gets drawn bold.
        assertTrue(styled.copy(display = null).affectsName)
        assertEquals(NickStyle.NONE, resolved.getValue(UUID.fromString("22222222-2222-2222-2222-222222222222")).style)

        assertEquals(styled, CosmeticJson.fromCache(CosmeticJson.toCache(mapOf(uuid to styled))).getValue(uuid))
    }

    @Test
    fun `a font is read with its files and survives the cache, one without files is no font`() {
        val root = obj(
            """
            { "version": 1, "players": {
                "$uuid": { "nick": { "font": { "id": "comfortaa", "family": "Comfortaa", "weight": 300, "italic": true,
                    "files": [ { "subset": "latin", "url": "https://cdn.jsdelivr.net/fontsource/fonts/comfortaa@5.3.0/latin-300-italic.ttf", "range": "U+0000-00FF,U+0131" } ] } } },
                "22222222-2222-2222-2222-222222222222": { "nick": { "displayName": "X", "font": { "id": "a", "family": "A", "files": [] } } }
            } }
            """.trimIndent()
        )
        val resolved = CosmeticJson.fromResolve(root)
        val font = resolved.getValue(uuid).font!!
        assertEquals("comfortaa-300-italic", font.familyKey)
        assertTrue(font.files.single().covers('A'.code))
        assertTrue(font.files.single().covers(0x131))
        assertFalse(font.files.single().covers(0x416))
        assertEquals(null, resolved.getValue(UUID.fromString("22222222-2222-2222-2222-222222222222")).font)

        val styled = resolved.getValue(uuid)
        assertEquals(styled, CosmeticJson.fromCache(CosmeticJson.toCache(mapOf(uuid to styled))).getValue(uuid))
    }

    @Test
    fun `resolve parses a full solid-color nick`() {
        val root = obj(
            """
            { "version": 1, "players": { "$uuid": { "nick": {
                "displayName": "BLACKUM",
                "color":       { "type": "Solid", "rgb": 16711680 },
                "prefix":      "[VIP]",
                "prefixColor": { "type": "Solid", "rgb": 65280 },
                "scale":       { "x": 1.0, "y": 2.0, "z": 3.0 },
                "capeUrl":     "https://example/cape"
            } } } }
            """.trimIndent()
        )
        val c = CosmeticJson.fromResolve(root).getValue(uuid)
        assertEquals("BLACKUM", c.display)
        assertEquals(0xFF0000, c.name.solidColor)
        assertEquals("[VIP]", c.prefix)
        assertEquals(0x00FF00, c.prefixColor.solidColor)
        assertEquals(Scale(1f, 2f, 3f), c.size)
        assertEquals("https://example/cape", c.capeUrl)
    }

    @Test
    fun `solid rgb is masked to the low 24 bits`() {
        val root = obj("""{ "players": { "$uuid": { "nick": { "color": { "type": "Solid", "rgb": -1 } } } } }""")
        assertEquals(0xFFFFFF, CosmeticJson.fromResolve(root).getValue(uuid).name.solidColor)
    }

    @Test
    fun `gradient color inverts perCharacter into wholeTextGradient`() {
        val root = obj(
            """
            { "players": { "$uuid": { "nick": { "color": {
                "type": "Gradient",
                "gradient": { "stops": [ { "pos": 0.0, "rgb": 16711680 }, { "pos": 1.0, "rgb": 255 } ] },
                "perCharacter": false, "animated": true, "speed": 2.0
            } } } } }
            """.trimIndent()
        )
        val spec = CosmeticJson.fromResolve(root).getValue(uuid).name
        assertEquals(listOf(ColorStop(0xFF0000, 0f), ColorStop(0x0000FF, 1f)), spec.gradient)
        assertTrue(spec.animated)
        assertEquals(2f, spec.speed)
        assertTrue(spec.wholeTextGradient) // perCharacter false -> whole-text
    }

    @Test
    fun `chroma color is flagged and keeps its speed`() {
        val root = obj(
            """{ "players": { "$uuid": { "nick": { "color": { "type": "Chroma", "animated": true, "speed": 0.5 } } } } }"""
        )
        val spec = CosmeticJson.fromResolve(root).getValue(uuid).name
        assertTrue(spec.chroma)
        assertTrue(spec.animated)
        assertEquals(0.5f, spec.speed)
    }

    @Test
    fun `an absent or unknown color type is vanilla`() {
        val unknown = obj("""{ "players": { "$uuid": { "nick": { "color": { "type": "Rainbow" } } } } }""")
        assertEquals(ColorSpec.VANILLA, CosmeticJson.fromResolve(unknown).getValue(uuid).name)

        val vanilla = obj("""{ "players": { "$uuid": { "nick": { "color": { "type": "Vanilla" } } } } }""")
        assertFalse(CosmeticJson.fromResolve(vanilla).getValue(uuid).name.overrides)
    }

    @Test
    fun `a player with neither nick nor items is skipped, an invalid uuid too`() {
        val empty = obj("""{ "players": { "$uuid": { } } }""")
        assertTrue(CosmeticJson.fromResolve(empty).isEmpty())

        val badKey = obj("""{ "players": { "not-a-uuid": { "nick": { "displayName": "X" } } } }""")
        assertTrue(CosmeticJson.fromResolve(badKey).isEmpty())
    }

    @Test
    fun `resolve parses 3D items, dropping ones missing a model`() {
        val root = obj(
            """
            { "players": { "$uuid": { "items": [
                {
                    "id": "ears",
                    "model": { "sha256": "modelhash", "url": "https://m", "format": "bbmodel" },
                    "attach": { "point": "HEAD", "offset": [1.0, 2.0, 3.0], "rotation": [10.0, 20.0, 30.0], "scale": 2.0 },
                    "layers": [ { "role": "overlay", "sha256": "s", "url": "https://l", "tintSlot": "primary", "supportsAlpha": true } ],
                    "tints": { "primary": "#FF8800", "secondary": "#11223344" }
                },
                { "id": "no-model" }
            ] } } }
            """.trimIndent()
        )
        val items = CosmeticJson.fromResolve(root).getValue(uuid).items
        assertEquals(1, items.size)
        val ears = items.single()
        assertEquals("ears", ears.id)
        assertEquals("modelhash", ears.model.sha256)
        assertEquals("bbmodel", ears.model.format)

        assertEquals("HEAD", ears.attach.point)
        assertEquals(1f, ears.attach.offsetX)
        assertEquals(30f, ears.attach.roll)
        assertEquals(2f, ears.attach.scale)

        val layer = ears.layers.single()
        assertEquals("overlay", layer.role)
        assertEquals("primary", layer.tintSlot)
        assertTrue(layer.supportsAlpha)

        // #RRGGBB gets alpha FF. #RRGGBBAA moves alpha to the top byte.
        assertEquals(0xFFFF8800.toInt(), ears.tints["primary"])
        assertEquals(0x44112233, ears.tints["secondary"])
    }

    @Test
    fun `a nick record survives the disk-cache round trip`() {
        val original = mapOf(
            uuid to Cosmetic(
                display = "BLACKUM",
                name = ColorSpec(gradient = listOf(ColorStop(0xFF0000, 0f), ColorStop(0x0000FF, 1f)), animated = true, speed = 2f),
                prefix = "[VIP]",
                prefixColor = ColorSpec(solidColor = 0x00FF00),
                size = Scale(1f, 2f, 3f),
                capeUrl = "https://example/cape",
            )
        )
        val restored = CosmeticJson.fromCache(CosmeticJson.toCache(original))
        assertEquals(original, restored)
    }

    @Test
    fun `items are transient and do not reach the disk cache`() {
        val root = obj(
            """{ "players": { "$uuid": { "items": [ { "id": "ears", "model": { "sha256": "h", "url": "u" } } ] } } }"""
        )
        val resolved = CosmeticJson.fromResolve(root)
        assertFalse(resolved.getValue(uuid).items.isEmpty())
        val restored = CosmeticJson.fromCache(CosmeticJson.toCache(resolved))
        assertTrue(restored.getValue(uuid).items.isEmpty()) // items are never cached
    }
}
