package com.monsoonscript.keyboard.clipboard

/**
 * Geometry of the clipboard card list: equal-size cards in [cols] columns, filled row by row. Equal sizes keep scrolling, hit-testing and layout pure
 * arithmetic (no text measuring while scrolling). Pure Kotlin, unit tested. All y values are CONTENT coordinates (scroll not applied).
 */
class CardGrid {
    var cols = 2
        private set
    var count = 0
        private set
    var cardW = 0f
        private set
    var cardH = 0f
        private set
    private var gap = 0f
    private var pad = 0f

    /** [width] = the list's width; [pad] = space around the cards and [gap] = between them. */
    fun layout(count: Int, cols: Int, width: Float, cardH: Float, gap: Float, pad: Float) {
        this.count = if (count < 0) 0 else count
        this.cols = if (cols < 1) 1 else cols
        this.gap = gap
        this.pad = pad
        this.cardH = cardH
        val w = (width - 2f * pad - (this.cols - 1) * gap) / this.cols
        this.cardW = if (w < 1f) 1f else w
    }

    val rows: Int get() = if (count == 0) 0 else (count + cols - 1) / cols

    val contentHeight: Float get() = if (rows == 0) 0f else pad * 2f + rows * cardH + (rows - 1) * gap

    fun maxScroll(viewH: Float): Float = (contentHeight - viewH).coerceAtLeast(0f)

    fun left(i: Int): Float = pad + (i % cols) * (cardW + gap)

    fun top(i: Int): Float = pad + (i / cols) * (cardH + gap)

    /** The card under a point (content coordinates), or -1 for a gap, the padding or empty space. */
    fun hit(x: Float, y: Float): Int {
        if (count == 0) return -1
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
    fun firstRow(y0: Float): Int = ((y0 - pad) / (cardH + gap)).toInt().coerceAtLeast(0)

    fun lastRow(y1: Float): Int = ((y1 - pad) / (cardH + gap)).toInt().coerceAtMost(rows - 1)
}
