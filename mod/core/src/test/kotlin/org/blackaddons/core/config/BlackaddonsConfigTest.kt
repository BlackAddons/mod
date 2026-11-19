package org.blackaddons.core.config

import com.google.gson.JsonParser
import org.blackaddons.api.feature.Feature
import org.blackaddons.api.feature.annotations.Dev
import org.blackaddons.api.feature.settings.impl.BooleanSetting
import org.blackaddons.api.feature.settings.impl.StringSetting
import org.blackaddons.api.input.InputBinding
import org.blackaddons.api.testing.TestPlatform
import org.blackaddons.core.feature.FeatureManager
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path

class BlackaddonsConfigTest {

    @Dev
    private class KnownFeature : Feature() {
        override val id = "KnownFeature"
    }

    @TempDir
    lateinit var tempDir: Path

    @BeforeEach
    fun setUp() {
        TestPlatform.configDir = tempDir
    }

    @Test
    fun `save retains entries for an unavailable addon`() {
        Files.writeString(
            tempDir.resolve("blackaddons.json"),
            """
            {
              "features": [
                {
                  "id": "RemovedAddonFeature",
                  "addon": "removed-addon",
                  "enabled": true,
                  "settings": { "answer": 42 }
                }
              ],
              "huds": [
                {
                  "name": "Removed Addon HUD",
                  "addon": "removed-addon",
                  "anchorX": "RIGHT",
                  "anchorY": "BOTTOM",
                  "offsetX": -4.0,
                  "offsetY": -8.0,
                  "scaleX": 1.25,
                  "scaleY": 1.0,
                  "font": "INHERIT"
                }
              ]
            }
            """.trimIndent(),
        )

        BlackaddonsConfig.load()
        BlackaddonsConfig.save()

        val root = JsonParser.parseString(Files.readString(tempDir.resolve("blackaddons.json"))).asJsonObject
        val feature = root.getAsJsonArray("features").single().asJsonObject
        val hud = root.getAsJsonArray("huds").single().asJsonObject
        assertEquals("removed-addon", feature.get("addon").asString)
        assertTrue(feature.get("enabled").asBoolean)
        assertEquals(42, feature.getAsJsonObject("settings").get("answer").asInt)
        assertEquals("removed-addon", hud.get("addon").asString)
        assertEquals(-8f, hud.get("offsetY").asFloat)
    }

    @Test
    fun `save leaves an unreadable config untouched`() {
        val path = tempDir.resolve("blackaddons.json")
        val broken = "{ not valid json"
        Files.writeString(path, broken)

        BlackaddonsConfig.load()
        BlackaddonsConfig.save()

        assertEquals(broken, Files.readString(path))
    }

    @Test
    fun `a shared config carries no secret or missing addon, and importing it keeps both of the player's`() {
        val flag = BooleanSetting("Flag", id = "flag")
        val token = StringSetting("Token", "", id = "token", isMasked = true)
        val feature = KnownFeature().also {
            it.registerSetting(flag)
            it.registerSetting(token)
            FeatureManager.features += it
        }
        Files.writeString(
            tempDir.resolve("blackaddons.json"),
            """{ "features": [ { "id": "Gone", "addon": "removed-addon", "settings": { "a": 1 } } ], "huds": [] }""",
        )

        try {
            BlackaddonsConfig.load()
            token.value = "mine"
            val shared = BlackaddonsConfig.shareable()
            val features = shared.getAsJsonArray("features").map { it.asJsonObject }
            assertTrue(features.none { it.get("id").asString == "Gone" })
            assertTrue(features.single { it.get("id").asString == "KnownFeature" }.getAsJsonObject("settings").let { !it.has("token") && it.has("flag") })

            val incoming = JsonParser.parseString(
                """{ "features": [ { "id": "KnownFeature", "settings": { "flag": true, "token": "theirs" } } ], "huds": [] }""",
            ).asJsonObject
            BlackaddonsConfig.importShared(incoming)

            assertTrue(flag.value)
            assertEquals("mine", token.value)
            val saved = JsonParser.parseString(Files.readString(tempDir.resolve("blackaddons.json"))).asJsonObject
            assertTrue(saved.getAsJsonArray("features").any { it.asJsonObject.get("id").asString == "Gone" })
        } finally {
            FeatureManager.features -= feature
        }
    }

    @Test
    fun `save retains unavailable settings on an available feature`() {
        val knownSetting = BooleanSetting("Known", id = "known")
        val feature = KnownFeature().also {
            it.registerSetting(knownSetting)
            FeatureManager.features += it
        }
        Files.writeString(
            tempDir.resolve("blackaddons.json"),
            """
            {
              "features": [
                {
                  "id": "KnownFeature",
                  "enabled": false,
                  "settings": { "known": true, "cheat-only": 42 }
                }
              ],
              "huds": []
            }
            """.trimIndent(),
        )

        try {
            BlackaddonsConfig.load()
            BlackaddonsConfig.save()

            val root = JsonParser.parseString(Files.readString(tempDir.resolve("blackaddons.json"))).asJsonObject
            val settings = root.getAsJsonArray("features")
                .first { it.asJsonObject.get("id").asString == "KnownFeature" }
                .asJsonObject
                .getAsJsonObject("settings")
            assertTrue(knownSetting.value)
            assertEquals(42, settings.get("cheat-only").asInt)
        } finally {
            FeatureManager.features -= feature
        }
    }

    @Test
    fun `the toggle key stays in the player's file and out of shared configs`() {
        val feature = KnownFeature().also { FeatureManager.features += it }
        Files.writeString(
            tempDir.resolve("blackaddons.json"),
            """{ "features": [ { "id": "KnownFeature", "toggleKey": { "type": "MOUSE", "key": 3 } } ], "huds": [] }""",
        )

        try {
            BlackaddonsConfig.load()
            assertEquals(InputBinding.mouse(3), feature.toggleBind.binding)
            assertTrue(BlackaddonsConfig.shareable().getAsJsonArray("features").none { it.asJsonObject.has("toggleKey") })

            BlackaddonsConfig.importShared(JsonParser.parseString(
                """{ "features": [ { "id": "KnownFeature", "toggleKey": { "type": "MOUSE", "key": 4 } } ], "huds": [] }""",
            ).asJsonObject)
            assertEquals(InputBinding.mouse(3), feature.toggleBind.binding)

            val saved = JsonParser.parseString(Files.readString(tempDir.resolve("blackaddons.json"))).asJsonObject
            val entry = saved.getAsJsonArray("features").single { it.asJsonObject.get("id").asString == "KnownFeature" }.asJsonObject
            assertEquals(InputBinding.mouse(3), InputBinding.fromJson(entry.get("toggleKey")))
        } finally {
            FeatureManager.features -= feature
        }
    }
}
