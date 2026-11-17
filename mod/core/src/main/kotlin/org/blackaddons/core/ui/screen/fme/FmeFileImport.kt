package org.blackaddons.core.ui.screen.fme

import org.blackaddons.api.Platform
import org.blackaddons.api.ui.screen.UiManager
import org.blackaddons.api.util.I18n
import org.blackaddons.api.util.concurrent.OffThread
import org.blackaddons.core.config.portable.ImportPreview
import org.blackaddons.core.config.portable.PortableImports
import org.blackaddons.core.features.impl.visual.fme.FmeConversion
import org.blackaddons.core.features.impl.visual.fme.FmeFileFormat
import org.blackaddons.core.features.impl.visual.fme.FmeLegacyImport
import org.blackaddons.core.features.impl.visual.fme.FmePortable
import org.blackaddons.core.features.impl.visual.fme.FmeProfileHeader
import java.nio.file.Path

internal object FmeFileImport {
    private val tr = I18n.scope("blackaddons.fme")

    fun choose(onApplied: () -> Unit) {
        Platform.desktop.chooseFile(tr("import_file", "Import FME profile"), setOf("json")) { path ->
            OffThread.run(
                name = "fme-import-detect",
                work = { FmeLegacyImport.detect(path) },
                onFailure = { null },
                onResult = { format -> Platform.scheduler.schedule(0) { open(path, format, onApplied) } },
            )
        }
    }

    private fun open(path: Path, format: FmeFileFormat?, onApplied: () -> Unit) {
        val name = path.fileName.toString().substringBeforeLast('.')
        when (format) {
            null -> show(unreadable(), onApplied)
            FmeFileFormat.PROFILE -> read(onApplied) { FmePortable.readFile(path)?.let(FmePortable::preview) ?: unreadable() }
            FmeFileFormat.FUNNY_MAP_EXTRAS -> read(onApplied) {
                converted(FmeLegacyImport.funnyMapExtras(path, name, Platform.world::flattenLegacyBlock))
            }
            FmeFileFormat.COSMETIC_BLOCKS -> UiManager.push(FmeImportLocationModal { locationAt ->
                read(onApplied) { converted(FmeLegacyImport.cosmeticBlocks(path, name, locationAt)) }
            })
        }
    }

    private fun read(onApplied: () -> Unit, work: () -> ImportPreview<FmeProfileHeader>) = OffThread.run(
        name = "fme-import",
        work = work,
        onFailure = { unreadable() },
        onResult = { preview -> Platform.scheduler.schedule(0) { show(preview, onApplied) } },
    )

    private fun show(preview: ImportPreview<FmeProfileHeader>, onApplied: () -> Unit) =
        PortableImports.open(FmePortable, preview) { onApplied() }

    private fun converted(conversion: FmeConversion): ImportPreview<FmeProfileHeader> {
        val skipped = listOfNotNull(
            conversion.skippedRoomEdits.takeIf { it > 0 }
                ?.let { tr("import_skipped_rooms", "%d room edits were left out: those rooms are placed differently here.", it) },
            conversion.skippedBlocks.takeIf { it > 0 }
                ?.let { tr("import_skipped_blocks", "%d edits named blocks this game does not have.", it) },
        )
        return FmePortable.previewConverted(conversion.shared, skipped.joinToString(" ").ifEmpty { null })
    }

    private fun unreadable(): ImportPreview<FmeProfileHeader> =
        ImportPreview.Failed(PortableImports.tr("file_unreadable", "That file does not hold %s.", FmePortable.noun))
}
