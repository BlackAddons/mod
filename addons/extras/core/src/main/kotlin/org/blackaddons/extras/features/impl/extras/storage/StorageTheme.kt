package org.blackaddons.extras.features.impl.extras.storage

import org.blackaddons.api.ui.anim.isVisible
import org.blackaddons.api.ui.render.Render
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

    private const val CELL_RADIUS = 2f
    private const val HOVER_ALPHA = 128

    val panel: Color get() = Theme.BACKGROUND.withAlpha(204)
    val cell: Color get() = Theme.BACKGROUND.withAlpha(170)
    val dim: Color get() = Theme.BACKGROUND.withAlpha(110)
    val match: Color get() = Theme.ACCENT.withAlpha(130)

    fun cell(x: Int, y: Int, color: Color = cell) = Render.rect(x, y, ITEM, ITEM, color, CELL_RADIUS)

    /** Vanilla's hover wash, scaled by [amount]. Over the cell, under its item. */
    fun hover(x: Int, y: Int, amount: Float) {
        if (amount.isVisible) cell(x, y, Theme.TEXT_PRIMARY.withAlpha((HOVER_ALPHA * amount).toInt()))
    }

    fun item(raw: String, x: Int, y: Int) = Render.itemSpec(raw, x, y, ITEM, ITEM)
}
