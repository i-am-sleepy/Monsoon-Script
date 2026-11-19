package com.monsoonscript.keyboard.emoji

/**
 * Geometry of the emoji grid: sections stacked top to bottom, each with an optional header line and its emoji in rows of [cols] square cells.
 * Pure arithmetic (no Android classes), so the touch and scroll math is unit-tested. All y values are CONTENT coordinates (scroll not applied).
 */
class EmojiGrid {
    var cols = 8
        private set
    var cell = 0f
        private set
    var headerH = 0f
        private set
    var contentH = 0f
        private set
    var sectionCount = 0
        private set

    private var top = FloatArray(0)
    private var count = IntArray(0)
    private var headed = BooleanArray(0)
    private var minRows = IntArray(0)

    /**
     * [counts] = emoji per section, [headers] = does the section start with a header line, [emptyRows] = rows kept for a section even when it has
     * fewer emoji (the "no recent emoji yet" message needs one).
     */
    fun layout(counts: IntArray, headers: BooleanArray, emptyRows: IntArray, cols: Int, cell: Float, headerH: Float) {
        this.cols = if (cols < 1) 1 else cols
        this.cell = cell
        this.headerH = headerH
        sectionCount = counts.size
        count = counts.copyOf()
        headed = headers.copyOf()
        minRows = emptyRows.copyOf()
        top = FloatArray(counts.size)
        var y = 0f
        for (s in counts.indices) {
            top[s] = y
            y += (if (headed[s]) headerH else 0f) + rows(s) * cell
        }
        contentH = y
    }

    fun rows(s: Int): Int {
        val r = (count[s] + cols - 1) / cols
        return if (r < minRows[s]) minRows[s] else r
    }

    fun sectionTop(s: Int): Float = top[s]

    /** Top of the first emoji row of section [s]. */
    fun itemsTop(s: Int): Float = top[s] + if (headed[s]) headerH else 0f

    fun itemLeft(i: Int): Float = (i % cols) * cell

    fun itemTop(s: Int, i: Int): Float = itemsTop(s) + (i / cols) * cell

    fun maxScroll(viewH: Float): Float = if (contentH > viewH) contentH - viewH else 0f

    /** The section that contains content position [y] (the last one that starts at or above it). */
    fun sectionAt(y: Float): Int {
        var s = 0
        for (i in 1 until sectionCount) if (top[i] <= y) s = i
        return s
    }

    /** The emoji under content point (x, y) as `section shl 32 or index`, or -1 (header, empty cell, outside). */
    fun hit(x: Float, y: Float): Long {
        if (x < 0f || y < 0f || cell <= 0f || y >= contentH) return -1L
        val s = sectionAt(y)
        val rel = y - itemsTop(s)
        if (rel < 0f) return -1L
        val col = (x / cell).toInt()
        if (col >= cols) return -1L
        val idx = (rel / cell).toInt() * cols + col
        if (idx >= count[s]) return -1L
        return (s.toLong() shl 32) or idx.toLong()
    }

    companion object {
        fun section(hit: Long): Int = (hit shr 32).toInt()
        fun index(hit: Long): Int = (hit and 0xFFFFFFFFL).toInt()
    }
}
