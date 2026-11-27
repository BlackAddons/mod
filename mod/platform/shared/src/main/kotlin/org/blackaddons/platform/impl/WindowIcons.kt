package org.blackaddons.platform.impl

import com.mojang.blaze3d.platform.IconSet
import net.minecraft.SharedConstants
import net.minecraft.server.packs.resources.IoSupplier
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.io.InputStream

/**
 * The game reads its window icon from a pack (`icons/...` at the root), so a custom icon is that pack with our files on
 * top. Going through the game's own setIcon keeps every OS path it knows, including the macOS dock.
 */
internal object WindowIcons {

    private val WINDOW_SIZES = listOf(16, 32, 48, 128, 256)

    // macOS icns entry types: PNG payloads of these sizes.
    private val ICNS_TYPES = linkedMapOf(128 to "ic07", 256 to "ic08", 512 to "ic09", 1024 to "ic10")

    /** Root paths the game asks for, for example `icons/icon_16x16.png`. */
    fun files(pngBySize: Map<Int, ByteArray>): Map<String, ByteArray> = buildMap {
        WINDOW_SIZES.forEach { size -> pngBySize[size]?.let { put("icons/icon_${size}x$size.png", it) } }
        icns(pngBySize)?.let { put("icons/minecraft.icns", it) }
    }

    fun iconSet(custom: Boolean): IconSet =
        if (custom || SharedConstants.getCurrentVersion().stable()) IconSet.RELEASE else IconSet.SNAPSHOT

    /** Our file at a root [path] the game asks a pack for, or null to let the pack answer. */
    fun rootResource(files: Map<String, ByteArray>, path: Array<out String>): IoSupplier<InputStream>? =
        files[path.joinToString("/")]?.let { bytes -> IoSupplier { ByteArrayInputStream(bytes) } }

    private fun icns(pngBySize: Map<Int, ByteArray>): ByteArray? {
        val entries = ICNS_TYPES.mapNotNull { (size, type) -> pngBySize[size]?.let { type to it } }
        if (entries.isEmpty()) return null
        val out = ByteArrayOutputStream()
        DataOutputStream(out).use { data ->
            data.writeBytes("icns")
            data.writeInt(ICNS_HEADER + entries.sumOf { ICNS_HEADER + it.second.size })
            entries.forEach { (type, png) ->
                data.writeBytes(type)
                data.writeInt(ICNS_HEADER + png.size)
                data.write(png)
            }
        }
        return out.toByteArray()
    }

    private const val ICNS_HEADER = 8
}
