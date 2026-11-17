package org.blackaddons.core.features.impl.visual.fme

import com.google.gson.JsonParser
import com.google.gson.stream.JsonReader
import com.google.gson.stream.JsonToken
import org.blackaddons.api.skyblock.DungeonGeometry
import org.blackaddons.api.world.BlockPlacement
import java.nio.file.Files
import java.nio.file.Path

internal enum class FmeFileFormat { PROFILE, COSMETIC_BLOCKS, FUNNY_MAP_EXTRAS }

internal class FmeConversion(val shared: FmeShared, val skippedRoomEdits: Int, val skippedBlocks: Int)

internal object FmeLegacyImport {

    val f7ByPhase: (Int) -> String = { y -> fmeBossLocationKey(7, DungeonGeometry.f7Phase(y.toDouble()).number) }

    fun detect(path: Path): FmeFileFormat? = JsonReader(Files.newBufferedReader(path)).use { reader ->
        if (reader.peek() != JsonToken.BEGIN_OBJECT) return null
        reader.beginObject()
        if (!reader.hasNext()) return null
        val first = reader.nextName()
        when {
            first in PROFILE_KEYS -> FmeFileFormat.PROFILE
            ':' in first -> FmeFileFormat.COSMETIC_BLOCKS
            else -> FmeFileFormat.FUNNY_MAP_EXTRAS
        }
    }

    fun cosmeticBlocks(path: Path, name: String, locationAt: (y: Int) -> String): FmeConversion {
        val keys = HashMap<String, FmeRuleKey?>()
        val builders = HashMap<FmeRuleKey, HashMap<String, FmeCells.Builder>>()
        var skipped = 0
        JsonReader(Files.newBufferedReader(path)).use { reader ->
            reader.beginObject()
            while (reader.hasNext()) {
                reader.nextName()
                reader.beginObject()
                while (reader.hasNext()) {
                    val position = parsePosition(reader.nextName())
                    val value = if (reader.peek() == JsonToken.STRING) reader.nextString() else null.also { reader.skipValue() }
                    val key = value?.let { keys.getOrPut(it) { cosmeticBlockKey(it) } }
                    if (position == null || key == null) {
                        skipped++
                        continue
                    }
                    builders.getOrPut(key, ::HashMap).getOrPut(locationAt(position.y), FmeCells::Builder).add(position.x, position.y, position.z)
                }
                reader.endObject()
            }
            reader.endObject()
        }
        val rules = builders.flatMap { (key, byLocation) ->
            byLocation.map { (location, cells) -> FmePositionRule(key.copy(scopeKey = location), cells.build()) }
        }
        return FmeConversion(FmeShared(name, rules, emptyList(), emptyList()), skippedRoomEdits = 0, skippedBlocks = skipped)
    }

    fun funnyMapExtras(path: Path, name: String, flatten: (String) -> BlockPlacement?): FmeConversion {
        val document = Files.newBufferedReader(path).use(JsonParser::parseReader)
        val flattened = HashMap<String, BlockPlacement?>()
        val builders = HashMap<FmeRuleKey, FmeCells.Builder>()
        var skippedRoomEdits = 0
        var skippedBlocks = 0
        document.asJsonObject.entrySet().forEach { (area, value) ->
            val states = value.takeIf { it.isJsonObject }?.asJsonObject ?: return@forEach
            if (states.has(ROOM_BLOCKS)) {
                skippedRoomEdits += states.get(ROOM_BLOCKS).takeIf { it.isJsonObject }?.asJsonObject?.entrySet()
                    ?.sumOf { (_, places) -> places.takeIf { it.isJsonArray }?.asJsonArray?.size() ?: 0 } ?: 0
                return@forEach
            }
            val locationAt = funnyMapLocation(area)
            states.entrySet().forEach { (state, places) ->
                val block = if (state in flattened) flattened[state] else flatten(state).also { flattened[state] = it }
                places.takeIf { it.isJsonArray }?.asJsonArray?.forEach places@{ place ->
                    val position = place.takeIf { it.isJsonPrimitive }?.asString?.let(::parsePosition)
                    if (block == null || position == null || !isValidFmeBlockId(block.blockId)) {
                        skippedBlocks++
                        return@places
                    }
                    val key = FmeRuleKey(
                        profileId = "", scope = FmeScope.LOCATION, scopeKey = locationAt(position.y),
                        sourceBlockId = FME_ANY_SOURCE, blockId = block.blockId,
                        properties = block.properties.filter { (property, setting) -> isValidFmeProperty(property, setting) },
                    )
                    builders.getOrPut(key, FmeCells::Builder).add(position.x, position.y, position.z)
                }
            }
        }
        val rules = builders.map { (key, cells) -> FmePositionRule(key, cells.build()) }
        return FmeConversion(FmeShared(name, rules, emptyList(), emptyList()), skippedRoomEdits, skippedBlocks)
    }

    private fun cosmeticBlockKey(value: String): FmeRuleKey? {
        val state = value.removePrefix("!").substringBefore('|').trim()
        val blockId = state.substringBefore('[').lowercase().let { if (':' in it) it else "minecraft:$it" }
        if (!isValidFmeBlockId(blockId)) return null
        val properties = state.substringAfter('[', "").substringBefore(']').split(',')
            .map { it.substringBefore('=').trim() to it.substringAfter('=', "").trim() }
            .filter { (property, setting) -> isValidFmeProperty(property, setting) }
            .toMap()
        return FmeRuleKey(
            profileId = "", scope = FmeScope.LOCATION, scopeKey = "",
            sourceBlockId = FME_ANY_SOURCE, blockId = blockId, ghost = value.startsWith('!'), properties = properties,
        )
    }

    private fun funnyMapLocation(area: String): (Int) -> String {
        val floor = area.toIntOrNull()
        if (floor == 7) return f7ByPhase
        val key = if (floor != null) fmeBossLocationKey(floor, null) else area.replace(CAMEL_CASE_GAP, " ")
        return { key }
    }

    private fun parsePosition(text: String): FmeBlock? {
        val parts = text.split(',')
        if (parts.size != 3) return null
        return FmeBlock(
            parts[0].trim().toIntOrNull() ?: return null,
            parts[1].trim().toIntOrNull() ?: return null,
            parts[2].trim().toIntOrNull() ?: return null,
        )
    }

    private class FmeBlock(val x: Int, val y: Int, val z: Int)

    private val PROFILE_KEYS = setOf("version", "id", "name", "favorites", "positionEdits", "typeRules", "profile")
    private const val ROOM_BLOCKS = "preBlocks"
    private val CAMEL_CASE_GAP = Regex("(?<=[a-z])(?=[A-Z])")
}
