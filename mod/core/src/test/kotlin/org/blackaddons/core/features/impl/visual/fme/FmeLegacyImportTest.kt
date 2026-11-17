package org.blackaddons.core.features.impl.visual.fme

import org.blackaddons.api.skyblock.DungeonGeometry
import org.blackaddons.api.world.BlockPlacement
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path

class FmeLegacyImportTest {

    @TempDir
    lateinit var tempDir: Path

    @Test
    fun `the first key tells the three formats apart`() {
        assertEquals(FmeFileFormat.COSMETIC_BLOCKS, FmeLegacyImport.detect(file("""{"minecraft:overworld": {}}""")))
        assertEquals(FmeFileFormat.FUNNY_MAP_EXTRAS, FmeLegacyImport.detect(file("""{"7": {}}""")))
        assertEquals(FmeFileFormat.FUNNY_MAP_EXTRAS, FmeLegacyImport.detect(file("""{"Wizard": {"baseCore": 1}}""")))
        assertEquals(FmeFileFormat.PROFILE, FmeLegacyImport.detect(file("""{"version": 2, "name": "Mine"}""")))
        assertNull(FmeLegacyImport.detect(file("{}")))
        assertNull(FmeLegacyImport.detect(file("[]")))
    }

    @Test
    fun `a CB config becomes any-block edits at the chosen location, phantoms as ghosts`() {
        val path = file(
            """
            {"minecraft:overworld": {
              "1,70,2": "minecraft:stone",
              "2,70,2": "minecraft:stone",
              "3,70,2": "!minecraft:glass",
              "4,70,2": "minecraft:oak_stairs[facing=east,half=bottom]|shape=fence",
              "5,70,2": "not a block!",
              "bad": "minecraft:stone"
            }}
            """.trimIndent(),
        )

        val conversion = FmeLegacyImport.cosmeticBlocks(path, "Mine") { "Hub" }
        val rules = conversion.shared.positions.associateBy { it.key.blockId }

        assertEquals("Mine", conversion.shared.name)
        assertEquals(2, conversion.skippedBlocks)
        assertTrue(rules.values.all { it.key.sourceBlockId == FME_ANY_SOURCE && it.key.scopeKey == "Hub" })
        assertEquals(2, rules.getValue("minecraft:stone").cells.size)
        assertEquals(true, rules.getValue("minecraft:glass").key.ghost)
        assertEquals(false, rules.getValue("minecraft:stone").key.ghost)
        assertEquals(mapOf("facing" to "east", "half" to "bottom"), rules.getValue("minecraft:oak_stairs").key.properties)
    }

    @Test
    fun `CB blocks in the F7 boss split into the phase each height belongs to`() {
        val path = file("""{"minecraft:overworld": {"10,5,10": "minecraft:stone", "10,200,10": "minecraft:stone"}}""")

        val conversion = FmeLegacyImport.cosmeticBlocks(path, "F7", FmeLegacyImport.f7ByPhase)

        assertEquals(
            setOf("F7 P${DungeonGeometry.f7Phase(5.0).number}", "F7 P${DungeonGeometry.f7Phase(200.0).number}"),
            conversion.shared.positions.mapTo(mutableSetOf()) { it.key.scopeKey },
        )
    }

    @Test
    fun `FunnyMapExtras floors and islands convert, rooms are counted and left out`() {
        val path = file(
            """
            {
              "7": {"minecraft:stained_glass[color=pink]": ["19, 131, 136", "20, 131, 136"]},
              "4": {"minecraft:air": ["1, 70, 1"]},
              "DungeonHub": {"minecraft:fence[east=false,north=false,south=false,west=false]": ["5, 70, 5"]},
              "Gone": {"minecraft:no_such_block": ["0, 0, 0"]},
              "Wizard": {"baseCore": 1, "rotationCore": 2, "coreDistance": 1,
                "preBlocks": {"minecraft:stone": ["-72, 80, 0", "1, 70, 0"], "minecraft:air": []}}
            }
            """.trimIndent(),
        )
        val flattened = mapOf(
            "minecraft:stained_glass[color=pink]" to BlockPlacement("minecraft:pink_stained_glass", emptyMap()),
            "minecraft:air" to BlockPlacement("minecraft:air", emptyMap()),
            "minecraft:fence[east=false,north=false,south=false,west=false]" to BlockPlacement("minecraft:oak_fence", emptyMap()),
        )

        val conversion = FmeLegacyImport.funnyMapExtras(path, "Old", flattened::get)

        assertEquals(2, conversion.skippedRoomEdits)
        assertEquals(1, conversion.skippedBlocks)
        assertEquals(
            setOf(
                "F7 P${DungeonGeometry.f7Phase(131.0).number} minecraft:pink_stained_glass 2",
                "F4 Boss minecraft:air 1",
                "Dungeon Hub minecraft:oak_fence 1",
            ),
            conversion.shared.positions.mapTo(mutableSetOf()) { "${it.key.scopeKey} ${it.key.blockId} ${it.cells.size}" },
        )
        assertTrue(conversion.shared.positions.all { !it.key.ghost && it.key.sourceBlockId == FME_ANY_SOURCE })
    }

    private fun file(text: String): Path = Files.createTempFile(tempDir, "config", ".json").also { Files.writeString(it, text) }
}
