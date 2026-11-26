package org.blackaddons.core.features.impl.visual.fme

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import org.blackaddons.api.util.I18n
import org.blackaddons.core.config.portable.*
import java.nio.file.Files
import java.nio.file.Path

/** A whole FME profile or one rule, in the profile file format. Read back through the same checks. */
internal object FmePortable : PortableKind<FmeProfileHeader>("fme") {
    private val tr = PortableImports.tr.scope("fme")
    private val fmeTr = I18n.scope("blackaddons.fme")

    private const val EMPTY = "-"

    override val title: String get() = tr("title", "Import FME edits")
    override val noun: String get() = tr("noun", "FME edits")

    fun export(group: FmeManagedGroup? = null): String? =
        Fme.sharedDocument(group)?.let { encode(it, asProfile = group == null) }

    fun readFile(path: Path): JsonObject? =
        JsonParser.parseString(Files.readString(path)).takeIf { it.isJsonObject }?.let { asProfile(it.asJsonObject) }

    override fun preview(json: JsonObject): ImportPreview<FmeProfileHeader> {
        val shared = Fme.readShared(json) ?: return ImportPreview.Failed(tr("err_read", "Those FME edits could not be read."))
        val rows = rows(shared)
        if (rows.isEmpty()) return ImportPreview.Failed(tr("err_empty", "That string has no FME edits in it."))
        return profilePreview(
            json, shared.name, Fme.activeProfile().name, rows,
            asNewProfile = { Fme.importProfile(shared) },
            addToActive = { Fme.addShared(shared) },
        )
    }

    fun previewConverted(shared: FmeShared, skippedNote: String?): ImportPreview<FmeProfileHeader> {
        if (!shared.fitsInProfile) return ImportPreview.Failed(tr("err_too_big", "That config is too big to fit in one profile."))
        val rows = rows(shared)
        if (rows.isEmpty()) return ImportPreview.Failed(tr("err_file_empty", "That file has no FME edits this game can show."))
        val note = PortableImports.tr("profile_note", "Imported as a profile of its own; yours stay as they are. %d items:", rows.size)
        return ImportPreview.Ready(
            headline = PortableImports.tr("new_profile", "New profile \"%s\"", shared.name),
            note = listOfNotNull(skippedNote, note).joinToString(" "),
            rows = rows,
            apply = { Fme.importProfile(shared) },
        )
    }

    private fun rows(shared: FmeShared): List<DiffRow> =
        shared.positions.groupBy { Triple(it.key.scopeKey, it.key.sourceBlockId, it.key.blockId) }
            .map { (rule, edits) -> row(rule.first, rule.second, rule.third, edits.sumOf { it.cells.size }) } +
            shared.types.map { row(it.scopeKey, it.sourceBlockId, it.blockId, blocks = null) }

    /** [blocks] for position rules, null for block-type rules. */
    private fun row(scopeKey: String, from: String, to: String, blocks: Int?): DiffRow {
        val source = if (from == FME_ANY_SOURCE) fmeTr("any_block", "Any block") else from.removePrefix("minecraft:")
        val rule = "$source → ${to.removePrefix("minecraft:")}"
        return DiffRow(
            scopeKey.ifEmpty { fmeTr("everywhere", "Everywhere") },
            DiffValue(EMPTY),
            DiffValue(if (blocks == null) rule else "$rule ×$blocks"),
        )
    }
}
