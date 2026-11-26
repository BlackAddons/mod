package org.blackaddons.core.features.impl.visual.fme

import com.google.gson.JsonParser
import org.blackaddons.api.skyblock.DungeonGeometry
import org.blackaddons.api.testing.TestPlatform
import org.blackaddons.core.dungeon.map.RoomTransform
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path

class FmeStorageTest {

    @TempDir
    lateinit var tempDir: Path

    @BeforeEach
    fun setUp() {
        TestPlatform.configDir = tempDir
    }

    @Test
    fun `empty storage creates readable default profile`() {
        val storage = FmeStorage(FmeReplacementStore(), FmeTypeReplacementStore())

        storage.load()

        val directory = tempDir.resolve("fme")
        assertTrue(Files.exists(directory.resolve("profiles.json")))
        val profilePath = directory.resolve("default.json")
        assertTrue(Files.exists(profilePath))
        val profile = JsonParser.parseString(Files.readString(profilePath)).asJsonObject
        assertEquals("default", profile.get("id").asString)
        assertTrue(profile.getAsJsonArray("positionEdits").isEmpty)
        assertTrue(profile.getAsJsonArray("typeRules").isEmpty)
    }

    @Test
    fun `profile document round trips edits favorites and readable tint`() {
        val positions = FmeReplacementStore()
        val types = FmeTypeReplacementStore()
        val storage = FmeStorage(positions, types)
        storage.load()
        positions.put(
            FmeReplacement(
                scope = FmeScope.LOCATION,
                scopeKey = "Hub",
                x = -4,
                y = 70,
                z = 9,
                sourceBlockId = "minecraft:stone",
                blockId = "minecraft:glass",
                tintRgb = 0x336699,
            ),
        )
        types.put(
            FmeTypeReplacement(
                scope = FmeScope.GLOBAL,
                sourceBlockId = "minecraft:dirt",
                blockId = "minecraft:gold_block",
            ),
        )
        storage.toggleFavorite("default", "minecraft:glass")
        storage.saveProfile("default")

        val text = Files.readString(tempDir.resolve("fme/default.json"))
        assertTrue("\"tint\": \"#336699\"" in text)
        assertTrue("\"positionEdits\"" in text)
        assertTrue("\"typeRules\"" in text)

        val restoredPositions = FmeReplacementStore()
        val restoredTypes = FmeTypeReplacementStore()
        val restored = FmeStorage(restoredPositions, restoredTypes)
        restored.load()

        val context = FmeContext(FmeScope.LOCATION, "Hub")
        assertEquals(0x336699, restoredPositions.get("default", context, -4, 70, 9)?.tintRgb)
        assertEquals("minecraft:gold_block", restoredTypes.get("default", FmeContext(FmeScope.GLOBAL), "minecraft:dirt")?.blockId)
        assertEquals(listOf("minecraft:glass"), restored.favorites("default"))
    }

    @Test
    fun `placement state is stored per rule and survives save, load and undo`() {
        val positions = FmeReplacementStore()
        val storage = FmeStorage(positions, FmeTypeReplacementStore())
        storage.load()
        val north = FmeReplacement(scopeKey = "Hub", x = 1, y = 70, z = 2, blockId = "minecraft:oak_stairs", ghost = true)
        val east = north.copy(x = 2, properties = mapOf("facing" to "east", "half" to "top"))
        positions.put(north)
        positions.put(east)

        storage.saveProfile("default")

        val profile = JsonParser.parseString(Files.readString(tempDir.resolve("fme/default.json"))).asJsonObject
        val groups = profile.getAsJsonArray("positionEdits").map { it.asJsonObject }
        assertFalse(groups.single { it.getAsJsonArray("cells").single().asString == "1,70,2" }.has("state"))
        groups.single { it.has("state") }.getAsJsonObject("state").apply {
            addProperty("Bad Name", "x")
            addProperty("waterlogged", "no spaces allowed")
        }
        Files.writeString(tempDir.resolve("fme/default.json"), profile.toString())

        val restored = FmeReplacementStore()
        FmeStorage(restored, FmeTypeReplacementStore()).load()
        val context = FmeContext(FmeScope.LOCATION, "Hub")
        assertEquals(emptyMap<String, String>(), restored.get("default", context, 1, 70, 2)?.properties)
        assertEquals(east.properties, restored.get("default", context, 2, 70, 2)?.properties)

        val undo = FmeReplacementStore()
        undo.restore(positions.snapshot())
        assertEquals(east.properties, undo.get("default", context, 2, 70, 2)?.properties)
    }

    @Test
    fun `a version 1 profile with one entry per place still loads`() {
        Files.createDirectories(tempDir.resolve("fme"))
        Files.writeString(
            tempDir.resolve("fme/profiles.json"),
            """{"activeProfileId": "default", "profiles": [{"id": "default", "name": "Old"}]}""",
        )
        Files.writeString(
            tempDir.resolve("fme/default.json"),
            """
            {"version": 1, "id": "default", "name": "Old", "positionEdits": [{
              "scope": "LOCATION", "scopeKey": "Hub", "sourceBlock": "minecraft:stone", "replacementBlock": "minecraft:oak_stairs",
              "positions": [{"x": 1, "y": 70, "z": 2}, {"x": 2, "y": 70, "z": 2, "state": {"facing": "east"}}]
            }]}
            """.trimIndent(),
        )

        val positions = FmeReplacementStore()
        FmeStorage(positions, FmeTypeReplacementStore()).load()

        val context = FmeContext(FmeScope.LOCATION, "Hub")
        assertEquals("minecraft:stone", positions.get("default", context, 1, 70, 2)?.sourceBlockId)
        assertEquals(mapOf("facing" to "east"), positions.get("default", context, 2, 70, 2)?.properties)
        assertEquals(2, positions.size("default"))
    }

    @Test
    fun `a profile holding more than a profile may is loaded in part and never overwritten`() {
        Files.createDirectories(tempDir.resolve("fme"))
        Files.writeString(
            tempDir.resolve("fme/profiles.json"),
            """{"activeProfileId": "default", "profiles": [{"id": "default", "name": "Huge"}]}""",
        )
        val original = """
            {"version": 2, "id": "default", "name": "Huge", "positionEdits": [{
              "scope": "LOCATION", "scopeKey": "Hub", "sourceBlock": "*", "replacementBlock": "minecraft:glass",
              "cells": ["5,70,5", "0,0,0,2999,0,2999"]
            }]}
        """.trimIndent()
        Files.writeString(tempDir.resolve("fme/default.json"), original)

        val positions = FmeReplacementStore()
        val storage = FmeStorage(positions, FmeTypeReplacementStore())
        storage.load()
        storage.saveProfile("default")

        assertEquals(1, positions.size("default"))
        assertEquals(original, Files.readString(tempDir.resolve("fme/default.json")))
    }

    @Test
    fun `a shared box too big to be real is refused`() {
        val storage = FmeStorage(FmeReplacementStore(), FmeTypeReplacementStore())
        storage.load()
        val document = JsonParser.parseString(
            """
            {"version": 2, "name": "Huge", "positionEdits": [{
              "scope": "LOCATION", "scopeKey": "Hub", "sourceBlock": "*", "replacementBlock": "minecraft:glass",
              "cells": ["0,0,0,100000,255,100000", "5,70,5"]
            }]}
            """.trimIndent(),
        ).asJsonObject

        val shared = requireNotNull(storage.readShared(document))

        assertEquals(1, shared.positions.single().cells.size)
        assertEquals(FME_ANY_SOURCE, shared.positions.single().key.sourceBlockId)
    }

    @Test
    fun `room quarter turns match the clockwise direction of toWorld`() {
        val east = 1 to 0
        listOf(0 to (1 to 0), 90 to (0 to 1), 180 to (-1 to 0), 270 to (0 to -1)).forEach { (rotation, world) ->
            val transform = RoomTransform(0, 0, rotation)
            assertEquals(world, transform.toWorld(east.first, east.second))
            assertEquals(rotation / 90, transform.quarterTurns)
        }
    }

    @Test
    fun `profile document groups positions and omits default fields`() {
        val positions = FmeReplacementStore()
        val storage = FmeStorage(positions, FmeTypeReplacementStore())
        storage.load()
        positions.put(
            FmeReplacement(
                scopeKey = "Hub", x = 1, y = 70, z = 2,
                sourceBlockId = "minecraft:stone", blockId = "minecraft:glass", updatedAt = 1234L,
            ),
        )
        positions.put(
            FmeReplacement(
                scopeKey = "Hub", x = 3, y = 71, z = 4,
                sourceBlockId = "minecraft:stone", blockId = "minecraft:glass", updatedAt = 5678L,
            ),
        )
        positions.put(
            FmeReplacement(
                scopeKey = "Hub", x = 5, y = 72, z = 6,
                blockId = "minecraft:gold_block", ghost = true, updatedAt = 9012L,
            ),
        )

        storage.saveProfile("default")

        val profile = JsonParser.parseString(Files.readString(tempDir.resolve("fme/default.json"))).asJsonObject
        assertEquals(2, profile.get("version").asInt)
        val groups = profile.getAsJsonArray("positionEdits")
        assertEquals(2, groups.size())
        val normal = groups.map { it.asJsonObject }.single { it.get("sourceBlock").asString == "minecraft:stone" }
        assertEquals(listOf("1,70,2", "3,71,4"), normal.getAsJsonArray("cells").map { it.asString }.sorted())
        assertFalse(normal.has("positions"))
        assertEquals(5678L, normal.get("updatedAt").asLong)
        assertFalse(normal.has("ghost"))
        assertFalse(normal.has("tint"))
        assertFalse(normal.has("enabled"))
        assertFalse(normal.has("x"))
        val ghost = groups.map { it.asJsonObject }.single { it.get("sourceBlock").asString == "minecraft:air" }
        assertTrue(ghost.get("ghost").asBoolean)

        val restoredPositions = FmeReplacementStore()
        FmeStorage(restoredPositions, FmeTypeReplacementStore()).load()
        val context = FmeContext(FmeScope.LOCATION, "Hub")
        assertEquals(5678L, restoredPositions.get("default", context, 1, 70, 2)?.updatedAt)
        assertEquals(5678L, restoredPositions.get("default", context, 3, 71, 4)?.updatedAt)
        assertEquals(true, restoredPositions.get("default", context, 5, 72, 6)?.ghost)
    }

    @Test
    fun `dropping a shared profile file into the folder discovers it`() {
        val sourceDir = tempDir.resolve("source")
        TestPlatform.configDir = sourceDir
        val source = FmeStorage(FmeReplacementStore(), FmeTypeReplacementStore())
        source.load()
        val shared = source.createProfile("M7 | Gold = Route", copyFromId = null)
        val sharedJson = Files.readString(sourceDir.resolve("fme/${shared.id}.json"))

        val targetDir = tempDir.resolve("target")
        TestPlatform.configDir = targetDir
        val target = FmeStorage(FmeReplacementStore(), FmeTypeReplacementStore())
        target.load()
        Files.writeString(targetDir.resolve("fme/${shared.id}.json"), sharedJson)

        val reloaded = FmeStorage(FmeReplacementStore(), FmeTypeReplacementStore())
        reloaded.load()

        assertEquals("M7 | Gold = Route", reloaded.profiles().single { it.id == shared.id }.name)
        val manifest = Files.readString(targetDir.resolve("fme/profiles.json"))
        assertTrue(shared.id in manifest)
    }

    @Test
    fun `a shared profile imports as a new profile and a shared rule adds to the active one`() {
        val positions = FmeReplacementStore()
        val types = FmeTypeReplacementStore()
        val storage = FmeStorage(positions, types)
        storage.load()
        val rule = FmeReplacement(scope = FmeScope.LOCATION, scopeKey = "Hub", x = 1, y = 2, z = 3, sourceBlockId = "minecraft:stone", blockId = "minecraft:glass")
        positions.put(rule)
        val source = storage.activeProfile().id
        val document = requireNotNull(storage.document(source))
        assertFalse(document.has("id"))

        val shared = requireNotNull(storage.readShared(document))
        val created = storage.importProfile(shared)

        assertEquals(created.id, storage.activeProfileId)
        assertEquals(rule.copy(profileId = created.id), positions.get(FmePositionKey(created.id, FmeScope.LOCATION, "Hub", 1, 2, 3)))
        assertEquals(1, positions.size(created.id))
        assertEquals(1, positions.size(source))

        storage.add(created.id, requireNotNull(storage.readShared(requireNotNull(storage.document(source)))))
        assertEquals(1, positions.size(created.id), "the same edit at the same place replaces, it does not stack")

        document.addProperty("version", 99)
        assertEquals(null, storage.readShared(document))
    }

    @Test
    fun `old boss room edits load as phase locations shared by floor and master`() {
        val positions = FmeReplacementStore()
        val types = FmeTypeReplacementStore()
        val storage = FmeStorage(positions, types)
        storage.load()
        val profile = storage.activeProfile().id
        val p3 = FmeReplacement(scope = FmeScope.DUNGEON, scopeKey = "Boss: F7", x = 100, y = 120, z = 50, sourceBlockId = "minecraft:stone", blockId = "minecraft:glass")
        positions.put(p3)
        positions.put(p3.copy(scopeKey = "Boss: M7", blockId = "minecraft:dirt", updatedAt = 5))
        positions.put(p3.copy(scopeKey = "Boss: P3Sim P5", y = 10))
        positions.put(p3.copy(scopeKey = "Boss: F3", y = 70))
        positions.put(p3.copy(scopeKey = "Blaze", y = 70))
        types.put(FmeTypeReplacement(scope = FmeScope.DUNGEON, scopeKey = "Boss: M7", sourceBlockId = "minecraft:stone", blockId = "minecraft:glass"))

        val shared = requireNotNull(storage.readShared(requireNotNull(storage.document(profile))))

        assertEquals(
            setOf(
                "LOCATION F7 P${DungeonGeometry.f7Phase(120.0).number} minecraft:dirt",
                "LOCATION F7 P5 minecraft:glass",
                "LOCATION F3 Boss minecraft:glass",
                "DUNGEON Blaze minecraft:glass",
            ),
            shared.positions.mapTo(mutableSetOf()) { "${it.key.scope} ${it.key.scopeKey} ${it.key.blockId}" },
        )
        assertEquals(
            (1..5).map { "F7 P$it" },
            shared.types.map { it.scopeKey }.sorted(),
        )
        assertTrue(shared.types.all { it.scope == FmeScope.LOCATION })
    }
}
