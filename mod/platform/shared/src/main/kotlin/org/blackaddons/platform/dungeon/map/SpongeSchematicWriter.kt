package org.blackaddons.platform.dungeon.map

import net.minecraft.nbt.CompoundTag
import net.minecraft.nbt.ListTag
import net.minecraft.nbt.NbtAccounter
import net.minecraft.nbt.NbtIo
import org.blackaddons.api.util.io.Disk
import java.nio.file.Path

internal data class SchematicBlockEntity(val id: String, val pos: IntArray, val data: CompoundTag)

internal data class CapturedSchematic(
    val name: String,
    val dataVersion: Int,
    val width: Int,
    val height: Int,
    val length: Int,
    val palette: List<String>,
    val blockData: IntArray,
    val blockEntities: List<SchematicBlockEntity>,
    val createdAt: Long = System.currentTimeMillis(),
)

/** Writes both Sponge layouts separately so their NBT shapes can't mix. */
internal object SpongeSchematicWriter {
    const val V2_FILE = "room.schem"
    const val V3_FILE = "room-v3.schem"

    /** Each layout gets read back and checked before the batch commits. */
    fun stage(files: Disk.Batch, directory: Path, schematic: CapturedSchematic) {
        validateData(schematic)
        files.file(directory.resolve(V2_FILE)) {
            NbtIo.writeCompressed(v2Root(schematic), it)
            validate(it, schematic, 2)
        }
        files.file(directory.resolve(V3_FILE)) {
            NbtIo.writeCompressed(v3Root(schematic), it)
            validate(it, schematic, 3)
        }
    }

    private fun v2Root(data: CapturedSchematic) = CompoundTag().apply {
        putInt("Version", 2)
        putInt("DataVersion", data.dataVersion)
        put("Metadata", metadata(data))
        putShort("Width", data.width.toShort())
        putShort("Height", data.height.toShort())
        putShort("Length", data.length.toShort())
        putIntArray("Offset", intArrayOf(0, 0, 0))
        putInt("PaletteMax", data.palette.size)
        put("Palette", paletteTag(data.palette))
        putByteArray("BlockData", encode(data.blockData))
        put("BlockEntities", ListTag().apply { data.blockEntities.forEach { add(v2BlockEntity(it)) } })
    }

    private fun v3Root(data: CapturedSchematic) = CompoundTag().apply {
        put("Schematic", CompoundTag().apply {
            putInt("Version", 3)
            putInt("DataVersion", data.dataVersion)
            put("Metadata", metadata(data))
            putShort("Width", data.width.toShort())
            putShort("Height", data.height.toShort())
            putShort("Length", data.length.toShort())
            putIntArray("Offset", intArrayOf(0, 0, 0))
            put("Blocks", CompoundTag().apply {
                put("Palette", paletteTag(data.palette))
                putByteArray("Data", encode(data.blockData))
                put("BlockEntities", ListTag().apply { data.blockEntities.forEach { add(v3BlockEntity(it)) } })
            })
        })
    }

    private fun metadata(data: CapturedSchematic) = CompoundTag().apply {
        putString("Name", data.name)
        putString("Author", "BlackAddons")
        putLong("Date", data.createdAt)
    }

    private fun paletteTag(palette: List<String>) = CompoundTag().apply {
        palette.forEachIndexed { index, state -> putInt(state, index) }
    }

    private fun v2BlockEntity(entity: SchematicBlockEntity) = entity.data.copy().apply {
        putString("Id", entity.id)
        putIntArray("Pos", entity.pos)
    }

    private fun v3BlockEntity(entity: SchematicBlockEntity) = CompoundTag().apply {
        putString("Id", entity.id)
        putIntArray("Pos", entity.pos)
        put("Data", entity.data.copy())
    }

    private fun validate(path: Path, expected: CapturedSchematic, version: Int) {
        val root = NbtIo.readCompressed(path, NbtAccounter.unlimitedHeap())
        val schematic = if (version == 3) {
            require(root.contains("Schematic")) { "missing Sponge v3 Schematic compound" }
            root.getCompoundOrEmpty("Schematic")
        } else root
        require(schematic.getIntOr("Version", 0) == version) { "unexpected Sponge version" }
        require(schematic.getIntOr("DataVersion", 0) > 0) { "missing data version" }
        require(schematic.getShortOr("Width", 0).toInt() and 0xffff == expected.width) { "invalid width" }
        require(schematic.getShortOr("Height", 0).toInt() and 0xffff == expected.height) { "invalid height" }
        require(schematic.getShortOr("Length", 0).toInt() and 0xffff == expected.length) { "invalid length" }
        val blocks = if (version == 3) schematic.getCompoundOrEmpty("Blocks") else schematic
        val states = palette(blocks.getCompoundOrEmpty("Palette"), expected.palette.size)
        require(states == expected.palette) { "palette changed while writing" }
        val dataTag = if (version == 3) "Data" else "BlockData"
        require(decode(blocks.getByteArray(dataTag).orElseThrow { IllegalArgumentException("missing block data") }).contentEquals(expected.blockData)) { "block data changed while writing" }
    }

    private fun validateData(data: CapturedSchematic) {
        require(data.width > 0 && data.height > 0 && data.length > 0) { "invalid schematic dimensions" }
        require(data.blockData.size == data.width * data.height * data.length) { "wrong block data length" }
        require(data.palette.isNotEmpty()) { "empty palette" }
        require(data.blockData.all { it in data.palette.indices }) { "block data refers outside the palette" }
    }

    private fun palette(tag: CompoundTag, size: Int): List<String> {
        require(size > 0) { "empty palette" }
        val states = arrayOfNulls<String>(size)
        tag.keySet().forEach { state ->
            val index = tag.getIntOr(state, -1)
            require(index in states.indices && states[index] == null) { "invalid palette index" }
            states[index] = state
        }
        return states.map { requireNotNull(it) { "palette index missing" } }
    }

    private fun encode(values: IntArray): ByteArray {
        val out = ArrayList<Byte>(values.size)
        values.forEach { value ->
            var current = value
            do {
                var next = current and 0x7f
                current = current ushr 7
                if (current != 0) next = next or 0x80
                out += next.toByte()
            } while (current != 0)
        }
        return out.toByteArray()
    }

    private fun decode(bytes: ByteArray): IntArray {
        val values = ArrayList<Int>()
        var value = 0
        var shift = 0
        bytes.forEach { byte ->
            val bits = byte.toInt() and 0xff
            value = value or ((bits and 0x7f) shl shift)
            if (bits and 0x80 == 0) {
                values += value
                value = 0
                shift = 0
            } else {
                shift += 7
                require(shift < 35) { "invalid varint" }
            }
        }
        require(shift == 0) { "truncated varint" }
        return values.toIntArray()
    }
}
