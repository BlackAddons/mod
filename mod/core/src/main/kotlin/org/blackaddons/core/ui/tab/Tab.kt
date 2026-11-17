package org.blackaddons.core.ui.tab

import org.blackaddons.api.ui.icon.VectorIcon

/**
 * A sidebar row.
 *
 * - [ContentTab] swaps the content area to its [TabController].
 * - [TriggerTab] fires a callback without changing the active tab.
 * - [HeaderTab] is a non-interactive group label.
 * - [DividerTab] is a rule. [TabRegistry] drops leading, trailing and doubled ones.
 *
 * [icon] is a vector, not a name, because a typo used to lose the glyph silently.
 */
sealed class Tab {
    abstract val label: String
    open val icon: VectorIcon? = null
}

internal class ContentTab(
    override val label: String,
    val controller: TabController,
    override val icon: VectorIcon? = null,
) : Tab()

internal class TriggerTab(
    override val label: String,
    override val icon: VectorIcon? = null,
    val onClick: () -> Unit,
) : Tab()

internal class HeaderTab(override val label: String) : Tab()

internal object DividerTab : Tab() {
    override val label: String = ""
}
