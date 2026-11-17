package org.blackaddons.core.font

import org.blackaddons.api.cosmetics.NickFont
import org.blackaddons.api.cosmetics.NickFontFile
import org.blackaddons.api.util.concurrent.LoadOnce
import java.nio.file.Path

/**
 * The files of the fonts cosmetic names use, downloaded the first time someone wearing one is drawn. Safe to ask every
 * frame from any thread: it answers from memory and starts at most one download per file.
 */
object NickFontFiles {

    private const val RETRY_AFTER_MS = 5L * 60L * 1000L

    private val files = LoadOnce("nick-font", RETRY_AFTER_MS, load = FontCatalog::nickFile)

    /** Null while the file is still downloading or after it failed. Asking starts the download. */
    fun path(file: NickFontFile): Path? = files[file.url]

    /**
     * The files that arrived, once none of them are still downloading. A name only draws in its font when the whole
     * font is there, so it doesn't switch letter by letter. Files that failed are left out, and their letters fall back
     * to the base font.
     */
    fun settled(font: NickFont): Map<NickFontFile, Path>? {
        val paths = font.files.associateWith(::path)
        if (font.files.any { files.isLoading(it.url) }) return null
        return paths.filterValues { it != null }.mapValues { it.value!! }.takeIf { it.isNotEmpty() }
    }
}
