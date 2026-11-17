package org.blackaddons.core.features.impl.visual.fme

import org.blackaddons.api.player.BlockFace
import org.blackaddons.api.player.CameraRay
import org.blackaddons.api.world.BlockAppearance
import org.blackaddons.api.world.BlockPos3
import org.blackaddons.api.world.Vec3d
import org.blackaddons.core.dungeon.map.MapState
import org.blackaddons.core.dungeon.map.RoomTransform
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class FmeReplacementStoreTest {

    @Test
    fun `undo snapshots restore negative coordinates`() {
        val source = FmeReplacementStore().apply {
            put(FmeReplacement(scopeKey = "Hub", x = -31, y = 70, z = 42, sourceBlockId = "minecraft:stone", blockId = "minecraft:sea_lantern"))
            put(FmeReplacement(scopeKey = "Hub", x = 12, y = -64, z = -7, blockId = "minecraft:polished_blackstone", ghost = true, tintRgb = 0x7F4020))
        }

        val restored = FmeReplacementStore()
        restored.restore(source.snapshot())
        val context = FmeContext(FmeScope.LOCATION, "Hub")

        assertEquals("minecraft:sea_lantern", restored.get("default", context, -31, 70, 42)?.blockId)
        assertEquals("minecraft:polished_blackstone", restored.get("default", context, 12, -64, -7)?.blockId)
        assertEquals(false, restored.get("default", context, -31, 70, 42)?.ghost)
        assertEquals(true, restored.get("default", context, 12, -64, -7)?.ghost)
        assertEquals(0x7F4020, restored.get("default", context, 12, -64, -7)?.tintRgb)
        assertNull(restored.get("default", context, 31, 70, 42))
    }

    @Test
    fun `global replacement rules round trip and reject malformed ids`() {
        val source = FmeTypeReplacementStore()
        val rule = FmeTypeReplacement(sourceBlockId = "minecraft:stone", blockId = "minecraft:glass")
        assertTrue(source.put(rule))
        assertFalse(source.put(rule))

        val restored = FmeTypeReplacementStore()
        assertTrue(restored.replaceAll(source.lines() + "not a rule" + "minecraft:dirt=not an id"))
        val context = FmeContext(FmeScope.GLOBAL)

        assertEquals("minecraft:glass", restored.get("default", context, "minecraft:stone")?.blockId)
        assertNull(restored.get("default", context, "minecraft:dirt"))
        assertTrue(restored.remove(rule.key))
        assertFalse(restored.remove(rule.key))
    }

    @Test
    fun `scoped replacement tint round trips`() {
        val source = FmeTypeReplacementStore().apply {
            put(
                FmeTypeReplacement(
                    scope = FmeScope.DUNGEON,
                    scopeKey = "Blaze",
                    sourceBlockId = "minecraft:stone",
                    blockId = "minecraft:glass",
                    tintRgb = 0xC08040,
                ),
            )
        }

        val restored = FmeTypeReplacementStore()
        restored.replaceAll(source.lines())

        val rule = restored.get("default", FmeContext(FmeScope.DUNGEON, "Blaze"), "minecraft:stone")
        assertEquals("minecraft:glass", rule?.blockId)
        assertEquals(0xC08040, rule?.tintRgb)
        assertNull(restored.get("default", FmeContext(FmeScope.DUNGEON, "Trap"), "minecraft:stone"))
    }

    @Test
    fun `replacement state and edit order round trip`() {
        val context = FmeContext(FmeScope.LOCATION, "Hub")
        val positions = FmeReplacementStore().apply {
            put(
                FmeReplacement(
                    scopeKey = context.key,
                    x = 1,
                    y = 70,
                    z = 2,
                    blockId = "minecraft:glass",
                    enabled = false,
                    updatedAt = 1234L,
                ),
            )
        }
        val rules = FmeTypeReplacementStore().apply {
            put(
                FmeTypeReplacement(
                    scope = context.scope,
                    scopeKey = context.key,
                    sourceBlockId = "minecraft:stone",
                    blockId = "minecraft:glass",
                    enabled = false,
                    updatedAt = 5678L,
                ),
            )
        }

        val restoredPositions = FmeReplacementStore().apply { restore(positions.snapshot()) }
        val restoredRules = FmeTypeReplacementStore().apply { replaceAll(rules.lines()) }

        assertEquals(false, restoredPositions.get("default", context, 1, 70, 2)?.enabled)
        assertEquals(1234L, restoredPositions.get("default", context, 1, 70, 2)?.updatedAt)
        assertEquals(false, restoredRules.get("default", context, "minecraft:stone")?.enabled)
        assertEquals(5678L, restoredRules.get("default", context, "minecraft:stone")?.updatedAt)
    }

    @Test
    fun `moving a position preserves its edit and rejects an occupied destination`() {
        val context = FmeContext(FmeScope.LOCATION, "Hub")
        val original = FmeReplacement(
            scopeKey = context.key,
            x = 1,
            y = 70,
            z = 2,
            sourceBlockId = "minecraft:stone",
            blockId = "minecraft:glass",
            ghost = true,
            tintRgb = 0xC08040,
            enabled = false,
        )
        val positions = FmeReplacementStore().apply { put(original) }
        val types = FmeTypeReplacementStore()
        var remembered = 0
        var persisted = 0
        val rules = FmeRules(
            replacements = positions,
            typeReplacements = types,
            profileId = { "default" },
            isKnownBlock = { true },
            snapshot = { FmeSnapshot(positions.snapshot(), types.lines()) },
            remember = { remembered++ },
            persistAll = {},
            persistReplacements = { persisted++ },
            persistTypeReplacements = {},
            refreshRendering = {},
        )
        val originalKey = FmePositionKey("default", FmeScope.LOCATION, "Hub", 1, 70, 2)

        assertEquals(FmePositionMoveResult.MOVED, rules.movePosition(originalKey, 4, 71, -3))
        assertNull(positions.get(originalKey))
        val moved = positions.get("default", context, 4, 71, -3)
        assertNotNull(moved)
        assertEquals(original.sourceBlockId, moved?.sourceBlockId)
        assertEquals(original.blockId, moved?.blockId)
        assertEquals(original.ghost, moved?.ghost)
        assertEquals(original.tintRgb, moved?.tintRgb)
        assertEquals(original.enabled, moved?.enabled)
        assertEquals(1, remembered)
        assertEquals(1, persisted)

        positions.put(FmeReplacement(scopeKey = context.key, x = 8, y = 80, z = 8, blockId = "minecraft:dirt"))
        val movedKey = originalKey.copy(x = 4, y = 71, z = -3)
        assertEquals(FmePositionMoveResult.OCCUPIED, rules.movePosition(movedKey, 8, 80, 8))
        assertEquals("minecraft:glass", positions.get(movedKey)?.blockId)
        assertEquals("minecraft:dirt", positions.get("default", context, 8, 80, 8)?.blockId)
        assertEquals(1, remembered)
        assertEquals(1, persisted)

        val group = requireNotNull(moved).managedGroup()
        assertEquals(FmePositionAddResult.ADDED, rules.addPosition(group, -5, 72, 6, anyBlock = false))
        val added = positions.get("default", context, -5, 72, 6)
        assertEquals(original.sourceBlockId, added?.sourceBlockId)
        assertEquals(original.blockId, added?.blockId)
        assertEquals(original.tintRgb, added?.tintRgb)
        assertEquals(FmePositionAddResult.OCCUPIED, rules.addPosition(group, 8, 80, 8, anyBlock = false))
        assertEquals(2, remembered)
        assertEquals(2, persisted)

        assertEquals(FmePositionAddResult.ADDED, rules.addPosition(group, 0, 73, 0, anyBlock = true))
        assertEquals(original.sourceBlockId, positions.get("default", context, 0, 73, 0)?.sourceBlockId)
        val solid = requireNotNull(positions.get("default", context, 8, 80, 8)).managedGroup()
        assertEquals(FmePositionAddResult.ADDED, rules.addPosition(solid, 1, 74, 1, anyBlock = true))
        assertEquals(FME_ANY_SOURCE, positions.get("default", context, 1, 74, 1)?.sourceBlockId)
    }

    @Test
    fun `room coordinates survive every scanner rotation`() {
        val canonical = 9 to 17
        for (rotation in listOf(0, 90, 180, 270)) {
            val transform = RoomTransform(-31, 47, rotation)
            val world = transform.toWorld(canonical.first, canonical.second)
            assertEquals(canonical, transform.toLocal(world.first, world.second))
        }
    }

    @Test
    fun `render index skips unrelated blocks for position edits`() {
        val appearance = BlockAppearance("minecraft:glass", DEFAULT_TINT_RGB)
        val index = FmeRenderIndex(
            positions = FmePositionIndex.Builder().apply {
                set(12, 70, -9, FmeRenderPosition("minecraft:stone", false, appearance))
            }.build(),
            globalTypes = emptyMap(),
            locationTypes = emptyMap(),
            roomTypes = emptyMap(),
        )

        assertEquals(1, index.lookupKind(12, 70, -9))
        assertEquals(0, index.lookupKind(13, 70, -9))
    }

    @Test
    fun `render index limits dungeon type rules to matching room cells`() {
        val appearance = BlockAppearance("minecraft:glass", DEFAULT_TINT_RGB)
        val roomX = MapState.START_X
        val roomZ = MapState.START_Z
        val index = FmeRenderIndex(
            positions = FmePositionIndex.EMPTY,
            globalTypes = emptyMap(),
            locationTypes = emptyMap(),
            roomTypes = mapOf(packFmeRoomCell(roomX, roomZ) to mapOf("minecraft:stone" to appearance)),
        )

        assertEquals(2, index.lookupKind(roomX + 10, 70, roomZ - 10))
        assertEquals(0, index.lookupKind(roomX + MapState.ROOM_SIZE, 70, roomZ))
    }

    @Test
    fun `room discovery invalidates only newly affected chunks and positions`() {
        val appearance = BlockAppearance("minecraft:glass", DEFAULT_TINT_RGB)
        val roomX = MapState.START_X
        val roomZ = MapState.START_Z
        val position = BlockPos3(roomX + MapState.ROOM_SIZE * 2, -64, roomZ)
        val updated = FmeRenderIndex(
            positions = FmePositionIndex.Builder().apply {
                set(position.x, position.y, position.z, FmeRenderPosition("minecraft:stone", false, appearance))
            }.build(),
            globalTypes = emptyMap(),
            locationTypes = emptyMap(),
            roomTypes = mapOf(packFmeRoomCell(roomX, roomZ) to mapOf("minecraft:stone" to appearance)),
        )

        val invalidation = updated.invalidationFrom(FmeRenderIndex.EMPTY)

        assertTrue(invalidation.blockTypes.isEmpty())
        assertEquals(
            setOf(
                -13 to -13, -13 to -12, -13 to -11,
                -12 to -13, -12 to -12, -12 to -11,
                -11 to -13, -11 to -12, -11 to -11,
            ),
            invalidation.chunks,
        )
        assertEquals(setOf(position), invalidation.blocks)
    }

    @Test
    fun `world scoped rule changes invalidate only the changed block types`() {
        val appearance = BlockAppearance("minecraft:glass", DEFAULT_TINT_RGB)
        val previous = FmeRenderIndex(
            positions = FmePositionIndex.EMPTY,
            globalTypes = mapOf("minecraft:stone" to appearance),
            locationTypes = emptyMap(),
            roomTypes = emptyMap(),
        )
        val updated = previous.copy(
            locationTypes = mapOf("minecraft:dirt" to appearance),
            globalTypes = mapOf("minecraft:stone" to appearance.copy(tintRgb = 0xC08040)),
        )

        assertEquals(setOf("minecraft:stone"), previous.invalidationFrom(FmeRenderIndex.EMPTY).blockTypes)
        assertEquals(setOf("minecraft:dirt", "minecraft:stone"), updated.invalidationFrom(previous).blockTypes)
        assertEquals(FmeRenderInvalidation(), updated.invalidationFrom(updated))
    }

    @Test
    fun `copying a location pastes all its edits over the target and leaves the source`() {
        val positions = FmeReplacementStore()
        val types = FmeTypeReplacementStore()
        var remembered = 0
        val rules = FmeRules(
            replacements = positions,
            typeReplacements = types,
            profileId = { "default" },
            isKnownBlock = { true },
            snapshot = { FmeSnapshot(positions.snapshot(), types.lines()) },
            remember = { remembered++ },
            persistAll = {},
            persistReplacements = {},
            persistTypeReplacements = {},
            refreshRendering = {},
        )
        val edit = FmeReplacement(scopeKey = "F7 P1", x = 1, y = 2, z = 3, sourceBlockId = "minecraft:stone", blockId = "minecraft:glass", tintRgb = 0xC08040)
        positions.put(edit)
        positions.put(edit.copy(scopeKey = "F7 P2", blockId = "minecraft:dirt"))
        positions.put(edit.copy(scope = FmeScope.DUNGEON, scopeKey = "F7 P1", blockId = "minecraft:sand"))
        types.put(FmeTypeReplacement(scope = FmeScope.LOCATION, scopeKey = "F7 P1", sourceBlockId = "minecraft:stone", blockId = "minecraft:gold_block"))

        assertEquals(2, rules.copyContext(FmeScope.LOCATION, "F7 P1", "F7 P2"))

        val pasted = positions.get("default", FmeContext(FmeScope.LOCATION, "F7 P2"), 1, 2, 3)
        assertEquals("minecraft:glass", pasted?.blockId)
        assertEquals(0xC08040, pasted?.tintRgb)
        assertEquals("minecraft:gold_block", types.get("default", FmeContext(FmeScope.LOCATION, "F7 P2"), "minecraft:stone")?.blockId)
        assertEquals("minecraft:glass", positions.get("default", FmeContext(FmeScope.LOCATION, "F7 P1"), 1, 2, 3)?.blockId)
        assertEquals(1, remembered)
        assertEquals(0, rules.copyContext(FmeScope.LOCATION, "F7 P1", "F7 P1"))
        assertEquals(0, rules.copyContext(FmeScope.LOCATION, "Hub", "F7 P3"))
        assertEquals(1, remembered)
    }

    @Test
    fun `ray hits exposed face of visual block`() {
        val hit = rayBoxHit(
            CameraRay(Vec3d(0.5, 3.0, 0.5), Vec3d(0.5, -3.0, 0.5)),
            BlockPos3(0, 0, 0),
        )

        assertEquals(BlockFace.UP, hit?.face)
        assertEquals(4.0, hit?.distanceSq)
    }

    @Test
    fun `ray misses visual block outside segment`() {
        val hit = rayBoxHit(
            CameraRay(Vec3d(0.5, 3.0, 0.5), Vec3d(0.5, 2.0, 0.5)),
            BlockPos3(0, 0, 0),
        )

        assertNull(hit)
    }

    @Test
    fun `changing a position group's replacement keeps every cell and its state`() {
        val store = FmeReplacementStore().apply {
            put(FmeReplacement(scopeKey = "Hub", x = 1, y = 70, z = 2, sourceBlockId = "minecraft:gold_block", blockId = "minecraft:gray_concrete"))
            put(FmeReplacement(scopeKey = "Hub", x = 3, y = 70, z = 2, sourceBlockId = "minecraft:gold_block", blockId = "minecraft:gray_concrete"))
            put(FmeReplacement(scopeKey = "Hub", x = 5, y = 71, z = 2, sourceBlockId = "minecraft:stone", blockId = "minecraft:oak_stairs", properties = mapOf("facing" to "east")))
        }
        val context = FmeContext(FmeScope.LOCATION, "Hub")
        val gold = store.get("default", context, 1, 70, 2)!!.ruleKey
        val stairs = store.get("default", context, 5, 71, 2)!!.ruleKey

        assertTrue(store.updateBlock(setOf(gold), "minecraft:iron_block"))
        assertTrue(store.updateBlock(setOf(stairs), "minecraft:spruce_stairs"))

        assertEquals("minecraft:iron_block", store.get("default", context, 1, 70, 2)?.blockId)
        assertEquals("minecraft:iron_block", store.get("default", context, 3, 70, 2)?.blockId)
        assertEquals(mapOf("facing" to "east"), store.get("default", context, 5, 71, 2)?.properties)
        assertFalse(store.updateBlock(setOf(gold), "minecraft:diamond_block"))
    }
}
