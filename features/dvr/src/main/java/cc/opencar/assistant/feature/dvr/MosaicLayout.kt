package cc.opencar.assistant.feature.dvr

/**
 * Mosaic grid geometry: tile native sizes → output canvas size.
 * Same column/row rules as [MosaicGlComposer] (1→1 col, ≤4→2×2, else 3 cols).
 */
object MosaicLayout {
    fun colsFor(tileCount: Int): Int = when {
        tileCount <= 1 -> 1
        tileCount <= 4 -> 2
        else -> 3
    }

    fun rowsFor(tileCount: Int, cols: Int = colsFor(tileCount)): Int =
        if (tileCount <= 0) 0 else (tileCount + cols - 1) / cols

    /**
     * Output width × height from per-tile preview sizes in mosaic order.
     * Column width = max tile width in that column; row height = max in that row.
     * Dimensions are rounded up to even (H.264 / GLES friendly).
     */
    fun canvasSize(tileSizes: List<Pair<Int, Int>>): Pair<Int, Int> {
        if (tileSizes.isEmpty()) return 640 to 480
        val n = tileSizes.size
        val cols = colsFor(n)
        val rows = rowsFor(n, cols)
        val colWidths = IntArray(cols)
        val rowHeights = IntArray(rows)
        for (i in tileSizes.indices) {
            val col = i % cols
            val row = i / cols
            val (w, h) = tileSizes[i]
            colWidths[col] = maxOf(colWidths[col], w.coerceAtLeast(1))
            rowHeights[row] = maxOf(rowHeights[row], h.coerceAtLeast(1))
        }
        return even(colWidths.sum()) to even(rowHeights.sum())
    }

    private fun even(v: Int): Int {
        val n = v.coerceAtLeast(2)
        return if (n % 2 == 0) n else n + 1
    }
}
