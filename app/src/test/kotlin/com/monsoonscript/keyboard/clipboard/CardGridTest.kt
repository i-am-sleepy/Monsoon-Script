package com.monsoonscript.keyboard.clipboard

import org.junit.Assert.assertEquals
import org.junit.Test

class CardGridTest {

    private fun grid(count: Int, cols: Int): CardGrid = CardGrid().also { it.layout(count, cols, 400f, 80f, 8f, 10f) }

    @Test
    fun twoColumnsShareTheWidth() {
        val g = grid(5, 2)
        assertEquals(186f, g.cardW, 0.001f) // (400 - 2*10 - 8) / 2
        assertEquals(3, g.rows)
        assertEquals(10f, g.left(0), 0.001f)
        assertEquals(204f, g.left(1), 0.001f)
        assertEquals(10f, g.top(1), 0.001f)
        assertEquals(10f + 88f, g.top(2), 0.001f)
        assertEquals(2 * 10f + 3 * 80f + 2 * 8f, g.contentHeight, 0.001f)
    }

    @Test
    fun oneColumnIsFullWidth() {
        val g = grid(3, 1)
        assertEquals(380f, g.cardW, 0.001f)
        assertEquals(3, g.rows)
        assertEquals(10f, g.left(2), 0.001f)
    }

    @Test
    fun hitFindsTheCardAndIgnoresGapsAndPadding() {
        val g = grid(5, 2)
        assertEquals(0, g.hit(20f, 20f))
        assertEquals(1, g.hit(300f, 50f))
        assertEquals(2, g.hit(20f, 10f + 88f + 5f))
        assertEquals(4, g.hit(20f, 10f + 2 * 88f + 5f))
        assertEquals(-1, g.hit(300f, 10f + 2 * 88f + 5f)) // slot 5 does not exist
        assertEquals(-1, g.hit(5f, 20f)) // left padding
        assertEquals(-1, g.hit(197f, 20f)) // the gap between the columns
        assertEquals(-1, g.hit(20f, 10f + 82f)) // the gap between the rows
        assertEquals(-1, g.hit(395f, 20f)) // right padding
        assertEquals(-1, g.hit(20f, 5f)) // top padding
        assertEquals(-1, g.hit(20f, 5000f)) // far below
    }

    @Test
    fun scrollRangeAndVisibleRows() {
        val g = grid(10, 2)
        assertEquals(5, g.rows)
        val content = g.contentHeight
        assertEquals(content - 150f, g.maxScroll(150f), 0.001f)
        assertEquals(0f, g.maxScroll(10_000f), 0.001f)
        assertEquals(0, g.firstRow(0f))
        assertEquals(1, g.firstRow(100f)) // row 1 starts at 98
        assertEquals(1, g.lastRow(160f)) // row 2 starts at 186
        assertEquals(4, g.lastRow(10_000f)) // clamped to the last row
    }

    @Test
    fun emptyListHasNoHeightOrHits() {
        val g = grid(0, 2)
        assertEquals(0, g.rows)
        assertEquals(0f, g.contentHeight, 0.001f)
        assertEquals(-1, g.hit(20f, 20f))
        assertEquals(-1, g.lastRow(100f))
    }
}
