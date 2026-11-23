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

    // ---- Auto fit: one column, a height per card (E3.13)

    private fun fitGrid(vararg h: Float): CardGrid = CardGrid().also { it.layoutFit(h.size, 400f, h, 8f, 10f) }

    @Test
    fun fitStacksCardsOfDifferentHeights() {
        val g = fitGrid(50f, 120f, 70f)
        assertEquals(1, g.cols)
        assertEquals(3, g.rows)
        assertEquals(380f, g.cardW, 0.001f)
        assertEquals(10f, g.left(2), 0.001f)
        assertEquals(10f, g.top(0), 0.001f)
        assertEquals(68f, g.top(1), 0.001f) // 10 + 50 + 8
        assertEquals(196f, g.top(2), 0.001f) // 68 + 120 + 8
        assertEquals(50f, g.heightOf(0), 0.001f)
        assertEquals(120f, g.heightOf(1), 0.001f)
        assertEquals(10f + 50f + 8f + 120f + 8f + 70f + 10f, g.contentHeight, 0.001f)
        assertEquals(g.contentHeight - 100f, g.maxScroll(100f), 0.001f)
        assertEquals(0f, g.maxScroll(10_000f), 0.001f)
    }

    @Test
    fun fitHitFindsTheTallCardsAndIgnoresGaps() {
        val g = fitGrid(50f, 120f, 70f)
        assertEquals(0, g.hit(20f, 10f))
        assertEquals(0, g.hit(20f, 60f)) // the last pixel of card 0
        assertEquals(-1, g.hit(20f, 64f)) // gap
        assertEquals(1, g.hit(200f, 68f))
        assertEquals(1, g.hit(200f, 188f))
        assertEquals(-1, g.hit(200f, 192f)) // gap
        assertEquals(2, g.hit(390f, 200f))
        assertEquals(-1, g.hit(20f, 5f)) // top padding
        assertEquals(-1, g.hit(5f, 30f)) // left padding
        assertEquals(-1, g.hit(395f, 30f)) // right padding
        assertEquals(-1, g.hit(20f, 276f)) // below the last card
        assertEquals(-1, g.hit(20f, 5000f))
    }

    @Test
    fun fitVisibleRowsFollowTheScroll() {
        val g = fitGrid(50f, 120f, 70f, 40f)
        assertEquals(0, g.firstRow(0f))
        assertEquals(0, g.firstRow(30f))
        assertEquals(1, g.firstRow(70f)) // card 1 starts at 68
        assertEquals(2, g.firstRow(200f)) // card 2 starts at 196
        assertEquals(0, g.lastRow(30f))
        assertEquals(1, g.lastRow(190f))
        assertEquals(2, g.lastRow(196f))
        assertEquals(3, g.lastRow(10_000f)) // clamped to the last card
    }

    @Test
    fun fitHandlesOneCardAndNoCards() {
        val one = fitGrid(33f)
        assertEquals(10f + 33f + 10f, one.contentHeight, 0.001f)
        assertEquals(0, one.hit(20f, 20f))
        val none = CardGrid().also { it.layoutFit(0, 400f, FloatArray(0), 8f, 10f) }
        assertEquals(0, none.rows)
        assertEquals(0f, none.contentHeight, 0.001f)
        assertEquals(-1, none.hit(20f, 20f))
        assertEquals(-1, none.lastRow(100f))
    }

    @Test
    fun fitReusesItsArraysAndCanSwitchBackToEqualCards() {
        val g = CardGrid()
        g.layoutFit(2, 400f, floatArrayOf(40f, 60f), 8f, 10f)
        g.layoutFit(150, 400f, FloatArray(150) { 30f }, 8f, 10f) // grows
        assertEquals(150, g.count)
        assertEquals(10f + 149 * 38f, g.top(149), 0.001f)
        g.layoutFit(3, 400f, floatArrayOf(20f, 20f, 20f), 8f, 10f) // shrinks again
        assertEquals(3, g.rows)
        assertEquals(10f + 3 * 20f + 2 * 8f + 10f, g.contentHeight, 0.001f)
        g.layout(5, 2, 400f, 80f, 8f, 10f) // equal mode again
        assertEquals(2, g.cols)
        assertEquals(80f, g.heightOf(3), 0.001f)
        assertEquals(10f + 88f, g.top(2), 0.001f)
        assertEquals(1, g.hit(300f, 50f))
    }

    @Test
    fun fitClampsAnAbsurdHeightAndAShortList() {
        val g = CardGrid()
        g.layoutFit(5, 400f, floatArrayOf(0f, -3f), 8f, 10f) // count larger than the array: only what is there
        assertEquals(2, g.count)
        assertEquals(1f, g.heightOf(0), 0.001f)
        assertEquals(1f, g.heightOf(1), 0.001f)
    }
}
