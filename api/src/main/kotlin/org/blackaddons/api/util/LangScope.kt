package org.blackaddons.api.util

/** `val tr = I18n.scope("blackaddons.fme")`, then `tr("title", "Title")` reads `blackaddons.fme.title`. */
class LangScope internal constructor(private val prefix: String) {

    operator fun invoke(key: String, fallback: String): String = I18n.trOr("$prefix.$key", fallback)

    operator fun invoke(key: String, fallback: String, vararg args: Any): String =
        I18n.trOr("$prefix.$key", fallback, *args)

    /** `scope("attach")` of `blackaddons.bachat` reads `blackaddons.bachat.attach.*`. */
    fun scope(sub: String): LangScope = LangScope("$prefix.$sub")
}
