package org.blackaddons.extras.features.impl.extras.storage

import org.blackaddons.api.ui.render.Render
import org.blackaddons.api.ui.theme.Glass
import org.blackaddons.api.ui.theme.Theme
import org.blackaddons.api.ui.theme.Theme.withAlpha
import java.awt.Color

/** Shared metrics and [Theme] colors, read each frame so themes apply live. Sizes are GUI-scaled container pixels. */
internal object StorageTheme {

    const val MARGIN = 8
    const val TOP_MARGIN = 6
    const val SEARCH_H = 16
    const val SLOT = StorageLayout.SLOT
    const val ITEM = 16

    const val CARD_PAD = 6
    const val LIST_TOP_INSET = CARD_PAD + 2

    /** Longer names spill onto the next tile. */
    const val NAME_LIMIT = 24

    val dim: Color get() = Theme.BACKGROUND.withAlpha(110)

    /** [hover] eases in vanilla's white wash, under the item. */
    fun cell(x: Int, y: Int, hover: Float = 0f) = Render.glass(x, y, ITEM, ITEM, Glass.CELL, hover)

    fun match(x: Int, y: Int) = Render.rect(x, y, ITEM, ITEM, Theme.ACCENT.withAlpha(130), Glass.CELL.radius)

    fun item(raw: String, x: Int, y: Int) = Render.itemSpec(raw, x, y, ITEM, ITEM)
}
