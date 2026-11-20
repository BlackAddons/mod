package org.blackaddons.core.config.portable

import org.blackaddons.api.ui.screen.UiManager
import org.blackaddons.api.util.I18n
import org.blackaddons.api.util.io.Portable
import org.blackaddons.api.util.io.PortableRead
import org.blackaddons.core.ui.screen.portable.PortableImportModal

/** Imports pasted config strings. Each failure (empty clipboard, wrong text, cut-off string) gets its own message. */
internal object PortableImports {
    val tr = I18n.scope("blackaddons.portable")

    sealed interface AnyImport {
        class Refused(val message: String) : AnyImport
        class Opens(val open: () -> Unit) : AnyImport
    }

    /** The modal explains problems inside a valid document (e.g. an unknown feature). */
    fun importAny(text: String): AnyImport {
        val document = when (val read = Portable.decode(text)) {
            is PortableRead.Document -> read
            else -> return AnyImport.Refused(failureMessage(read))
        }
        val kind = PortableKinds.byKind(document.kind)
            ?: return AnyImport.Refused(tr("unknown_kind", "This version does not know how to import a \"%s\".", document.kind))
        return AnyImport.Opens { open(kind, document) }
    }

    fun <T> open(kind: PortableKind<T>, text: String, onApplied: (T) -> Unit = kind::afterImport) =
        UiManager.push(PortableImportModal(kind.title, preview(kind, text), onApplied))

    fun <T> preview(kind: PortableKind<T>, text: String): ImportPreview<T> = when (val read = Portable.decode(text)) {
        is PortableRead.Document -> previewAs(kind, read)
        else -> ImportPreview.Failed(failureMessage(read))
    }

    fun <T> open(kind: PortableKind<T>, preview: ImportPreview<T>, onApplied: (T) -> Unit = kind::afterImport) =
        UiManager.push(PortableImportModal(kind.title, preview, onApplied))

    private fun <T> open(kind: PortableKind<T>, document: PortableRead.Document) =
        UiManager.push(PortableImportModal(kind.title, kind.preview(document.json), kind::afterImport))

    private fun <T> previewAs(kind: PortableKind<T>, document: PortableRead.Document): ImportPreview<T> {
        if (document.kind == kind.kind) return kind.preview(document.json)
        val noun = PortableKinds.byKind(document.kind)?.noun ?: "\"${document.kind}\""
        return ImportPreview.Failed(tr("err_kind", "That is %s, not %s.", noun, kind.noun))
    }

    private fun failureMessage(read: PortableRead): String = when (read) {
        is PortableRead.Document -> error("a document is not a failure")
        PortableRead.Empty -> tr("empty", "There is nothing on your clipboard.")
        PortableRead.NotPortable -> tr("foreign", "That is not a BlackAddons config string.")
        PortableRead.Corrupt -> tr("corrupt", "That string is damaged. Copy it again, all of it.")
        PortableRead.TooLarge -> tr("large", "That string is far too big to be a config.")
        is PortableRead.NewerFormat -> tr("newer", "That was written by a newer BlackAddons than this one.")
    }
}
