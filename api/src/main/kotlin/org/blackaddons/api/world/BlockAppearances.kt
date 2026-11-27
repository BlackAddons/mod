package org.blackaddons.api.world

/**
 * Render-only block replacement; everything else sees the original. [properties]: non-default properties, in world
 * space. [reshape]: draw the state even over the same block, so it connects to drawn neighbors instead of only
 * retexturing the real one.
 */
data class BlockAppearance(
    val blockId: String,
    val tintRgb: Int = UNTINTED,
    val properties: Map<String, String> = emptyMap(),
    val reshape: Boolean = false,
) {
    companion object {
        const val UNTINTED = 0xFFFFFF
    }
}

/**
 * Called from chunk-meshing threads for every block, so keep it cheap and read only safely published state
 * (immutable snapshot behind `@Volatile`).
 */
interface BlockAppearanceSource {

    /** Fast reject before the block id is resolved. */
    fun covers(x: Int, y: Int, z: Int): Boolean

    /** Null leaves it alone. */
    fun appearanceAt(x: Int, y: Int, z: Int, sourceBlockId: String, originalIsAir: Boolean): BlockAppearance?
}

/**
 * Read by the chunk mesher; first registered wins. Changed answers need
 * [WorldHost.redrawBlock]/[WorldHost.redrawChunk] - built sections aren't re-read.
 */
object BlockAppearances {

    @Volatile
    private var sources: Array<BlockAppearanceSource> = emptyArray()

    @Synchronized
    fun register(source: BlockAppearanceSource) {
        if (source !in sources) sources += source
    }

    @Synchronized
    fun release(source: BlockAppearanceSource) {
        sources = sources.filter { it !== source }.toTypedArray()
    }

    @JvmStatic
    fun covers(x: Int, y: Int, z: Int): Boolean {
        for (source in sources) if (source.covers(x, y, z)) return true
        return false
    }

    @JvmStatic
    fun appearanceAt(x: Int, y: Int, z: Int, sourceBlockId: String, originalIsAir: Boolean): BlockAppearance? {
        for (source in sources) {
            if (!source.covers(x, y, z)) continue
            source.appearanceAt(x, y, z, sourceBlockId, originalIsAir)?.let { return it }
        }
        return null
    }
}
