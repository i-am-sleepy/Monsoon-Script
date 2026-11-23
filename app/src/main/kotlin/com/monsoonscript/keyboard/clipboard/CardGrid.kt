package com.monsoonscript.keyboard.clipboard

/**
 * Geometry of the clipboard card list. Two modes, both pure arithmetic so scrolling and hit-testing never measure text:
 *  - [layout]: equal-size cards in [cols] columns, filled row by row;
 *  - [layoutFit] (Auto fit, E3.13): ONE column, every card has its own height (measured by the view); tops are a prefix sum and the row / hit
 *    lookups are a binary search.
 * Pure Kotlin, unit tested. All y values are CONTENT coordinates (scroll not applied). Use [heightOf], not [cardH], when the mode is not known.
 */
class CardGrid {
    var cols = 2
        private set
    var count = 0
        private set
    var cardW = 0f
        private set

    /** Height of every card in equal mode; 0 in fit mode (see [heightOf]). */
    var cardH = 0f
        private set
    private var gap = 0f
    private var pad = 0f
    private var fit = false
    private var tops = FloatArray(0)
    private var heights = FloatArray(0)

    /** [width] = the list's width; [pad] = space around the cards and [gap] = between them. */
    fun layout(count: Int, cols: Int, width: Float, cardH: Float, gap: Float, pad: Float) {
        this.fit = false
        this.count = if (count < 0) 0 else count
        this.cols = if (cols < 1) 1 else cols
        this.gap = gap
        this.pad = pad
        this.cardH = cardH
        val w = (width - 2f * pad - (this.cols - 1) * gap) / this.cols
        this.cardW = if (w < 1f) 1f else w
    }

    /** One column; card i is [h][i] tall (only the first [count] entries are read). The arrays are reused, so a re-layout allocates only when the list grows. */
    fun layoutFit(count: Int, width: Float, h: FloatArray, gap: Float, pad: Float) {
        this.fit = true
        this.count = if (count < 0) 0 else if (count > h.size) h.size else count
        this.cols = 1
        this.gap = gap
        this.pad = pad
        this.cardH = 0f
        val w = width - 2f * pad
        this.cardW = if (w < 1f) 1f else w
        if (heights.size < this.count) {
            heights = FloatArray(this.count + 64)
            tops = FloatArray(this.count + 64)
        }
        var y = pad
        for (i in 0 until this.count) {
            val hi = if (h[i] < 1f) 1f else h[i]
            heights[i] = hi
            tops[i] = y
            y += hi + gap
        }
    }

    val rows: Int get() = if (count == 0) 0 else (count + cols - 1) / cols

    val contentHeight: Float
        get() = when {
            count == 0 -> 0f
            fit -> tops[count - 1] + heights[count - 1] + pad
            else -> pad * 2f + rows * cardH + (rows - 1) * gap
        }

    fun maxScroll(viewH: Float): Float = (contentHeight - viewH).coerceAtLeast(0f)

    fun left(i: Int): Float = pad + (i % cols) * (cardW + gap)

    fun top(i: Int): Float = if (fit) tops[i] else pad + (i / cols) * (cardH + gap)

    /** The height of card [i]. */
    fun heightOf(i: Int): Float = if (fit) heights[i] else cardH

    /** The card under a point (content coordinates), or -1 for a gap, the padding or empty space. */
    fun hit(x: Float, y: Float): Int {
        if (count == 0) return -1
        if (fit) {
            if (x < pad || x > pad + cardW) return -1
            val i = rowAt(y)
            return if (i >= 0 && y <= tops[i] + heights[i]) i else -1
        }
        val cx = x - pad
        val cy = y - pad
        if (cx < 0f || cy < 0f) return -1
        val col = (cx / (cardW + gap)).toInt()
        val row = (cy / (cardH + gap)).toInt()
        if (col >= cols) return -1
        if (cx - col * (cardW + gap) > cardW || cy - row * (cardH + gap) > cardH) return -1
        val i = row * cols + col
        return if (i < count) i else -1
    }

    /** The first row that is at least partly inside [y0, y1] and the last one; first > last when nothing is visible. */
    fun firstRow(y0: Float): Int = if (fit) rowAt(y0).coerceAtLeast(0) else ((y0 - pad) / (cardH + gap)).toInt().coerceAtLeast(0)

    fun lastRow(y1: Float): Int = if (fit) rowAt(y1).coerceAtMost(rows - 1) else ((y1 - pad) / (cardH + gap)).toInt().coerceAtMost(rows - 1)

    /** Fit mode: the last card whose top is at or above [y] (a binary search), -1 when [y] is above the first card. */
    private fun rowAt(y: Float): Int {
        var lo = 0
        var hi = count - 1
        var found = -1
        while (lo <= hi) {
            val mid = (lo + hi) ushr 1
            if (tops[mid] <= y) {
                found = mid
                lo = mid + 1
            } else {
                hi = mid - 1
            }
        }
        return found
    }
}
