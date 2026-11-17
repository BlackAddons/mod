package org.blackaddons.extras.features.impl.extras.storage

/**
 * Pure geometry for the storage browser, in content space: labeled tiles in up to `maxPerRow` columns (NEU uses 3). A
 * search keeps cached tiles with hits and flags matching cells.
 */
internal object StorageLayout {

    const val SLOT = 18
    const val PADDING = 8

    // Label height plus a 4px gap.
    const val HEADER_H = 15

    const val GRID_COLS = 9
    private const val COL_GAP = 16
    private const val ROW_GAP = 18
    private const val PLACEHOLDER_ROWS = 5

    private const val TILE_W = GRID_COLS * SLOT

    /** [matched]: satisfies the search. */
    data class Cell(val x: Int, val y: Int, val raw: String, val matched: Boolean)

    /** Header at ([x], [y]), [cols]x[rows] grid from ([x], [gridY]). Uncached tiles have no [cells]. */
    data class Tile(
        val x: Int,
        val y: Int,
        val gridY: Int,
        val cols: Int,
        val rows: Int,
        val ref: StorageRef,
        val cached: Boolean,
        val cells: List<Cell>,
    ) {
        /** For hit-testing and culling. */
        val w: Int get() = cols * SLOT
        val h: Int get() = HEADER_H + rows * SLOT
    }

    class Layout(val tiles: List<Tile>, val contentHeight: Int)

    fun build(
        entries: List<StorageRef>,
        cached: (String) -> CapturedContainer?,
        query: String,
        areaX: Int,
        areaWidth: Int,
        originY: Int,
        maxPerRow: Int,
    ): Layout {
        val q = query.trim().lowercase()
        // Uncached pages can't match a search.
        val visible = if (q.isEmpty()) entries else entries.filter { ref ->
            cached(ref.key)?.items?.any { it != null && it.matches(q) } == true
        }

        val perRow = ((areaWidth - PADDING) / (TILE_W + COL_GAP)).coerceIn(1, maxPerRow.coerceAtLeast(1))
        val totalW = perRow * TILE_W + (perRow - 1) * COL_GAP
        val startX = areaX + (areaWidth - totalW) / 2

        val tiles = ArrayList<Tile>(visible.size)
        var y = originY
        var i = 0
        while (i < visible.size) {
            val row = visible.subList(i, minOf(i + perRow, visible.size))
            var rowHeight = 0
            for ((j, ref) in row.withIndex()) {
                val container = cached(ref.key)
                val tileX = startX + j * (TILE_W + COL_GAP)
                val gridY = y + HEADER_H
                val cols = container?.cols ?: GRID_COLS
                // Always full height (nav row is already dropped), so empty rows still draw.
                val rows = container?.rows ?: PLACEHOLDER_ROWS
                val cells = container?.items?.mapIndexedNotNull { slot, item ->
                    item?.let {
                        Cell(
                            x = tileX + (slot % cols) * SLOT,
                            y = gridY + (slot / cols) * SLOT,
                            raw = it.raw,
                            matched = q.isNotEmpty() && it.matches(q),
                        )
                    }
                } ?: emptyList()
                val tile = Tile(tileX, y, gridY, cols, rows, ref, container != null, cells)
                tiles += tile
                rowHeight = maxOf(rowHeight, tile.h)
            }
            y += rowHeight + ROW_GAP
            i += perRow
        }
        return Layout(tiles, (y - originY).coerceAtLeast(0))
    }
}

