package org.blackaddons.api.feature

/**
 * A ClickGUI warning on a feature or extension; informational only. The backend can override labels
 * per mod version range.
 * ```
 * override val labels = listOf(FeatureLabel.untested("The cancel fix has not been run in game yet."))
 * ```
 */
data class FeatureLabel(val kind: Kind, val reason: String? = null) {

    enum class Kind {
        /** Not run in game since its last change. */
        UNTESTED,

        /** Works, but can get the account banned. */
        UNSAFE,

        /** Known broken, usually after a game update. */
        BROKEN,
    }

    companion object {
        fun untested(reason: String? = null) = FeatureLabel(Kind.UNTESTED, reason)
        fun unsafe(reason: String? = null) = FeatureLabel(Kind.UNSAFE, reason)
        fun broken(reason: String? = null) = FeatureLabel(Kind.BROKEN, reason)
    }
}
