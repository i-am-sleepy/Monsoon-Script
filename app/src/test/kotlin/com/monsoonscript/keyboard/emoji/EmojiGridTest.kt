package com.monsoonscript.keyboard.emoji

import org.junit.Assert.assertEquals
import org.junit.Test

class EmojiGridTest {

    // 3 sections, 8 columns, cells 10 px, headers 5 px: [recent: empty, one row kept] [20 emoji] [9 emoji, no header]
    private fun grid(): EmojiGrid = EmojiGrid().also {
        it.layout(intArrayOf(0, 20, 9), booleanArrayOf(true, true, false), intArrayOf(1, 0, 0), 8, 10f, 5f)
    }

    @Test
    fun sectionsStackWithHeadersAndKeptRows() {
        val g = grid()
        assertEquals(0f, g.sectionTop(0), 0f)
        assertEquals(15f, g.sectionTop(1), 0f) // header 5 + 1 kept row of 10
        assertEquals(50f, g.sectionTop(2), 0f) // header 5 + 3 rows of 10
        assertEquals(70f, g.contentH, 0f) // 9 emoji = 2 rows, no header
        assertEquals(5f, g.itemsTop(0), 0f)
        assertEquals(20f, g.itemsTop(1), 0f)
        assertEquals(50f, g.itemsTop(2), 0f)
        assertEquals(1, g.rows(0))
        assertEquals(3, g.rows(1))
        assertEquals(2, g.rows(2))
    }

    @Test
    fun sectionAtFindsTheOwner() {
        val g = grid()
        assertEquals(0, g.sectionAt(0f))
        assertEquals(0, g.sectionAt(14.9f))
        assertEquals(1, g.sectionAt(15f))
        assertEquals(1, g.sectionAt(49f))
        assertEquals(2, g.sectionAt(50f))
        assertEquals(2, g.sectionAt(500f))
    }

    @Test
    fun hitFindsTheEmojiUnderAFinger() {
        val g = grid()
        // second section: items start at y = 20; the third row (index 16..) is at y = 40
        val h = g.hit(25f, 21f)
        assertEquals(1, EmojiGrid.section(h))
        assertEquals(2, EmojiGrid.index(h)) // column 2 of row 0
        val h2 = g.hit(75f, 49f) // last column of the last row of section 1: index 2*8 + 7 = 23 does not exist (20 emoji)
        assertEquals(-1L, h2)
        val h3 = g.hit(35f, 45f) // row 2, column 3 = index 19, the last one
        assertEquals(19, EmojiGrid.index(h3))
        assertEquals(1, EmojiGrid.section(h3))
        // headers, the kept row of an empty section, outside
        assertEquals(-1L, g.hit(10f, 2f))
        assertEquals(-1L, g.hit(10f, 8f))
        assertEquals(-1L, g.hit(-1f, 30f))
        assertEquals(-1L, g.hit(81f, 30f))
        assertEquals(-1L, g.hit(10f, 70f))
        // section without header
        val h4 = g.hit(5f, 51f)
        assertEquals(2, EmojiGrid.section(h4))
        assertEquals(0, EmojiGrid.index(h4))
    }

    @Test
    fun scrollRange() {
        val g = grid()
        assertEquals(20f, g.maxScroll(50f), 0f)
        assertEquals(0f, g.maxScroll(100f), 0f)
        assertEquals(30f, g.itemTop(1, 8) + 0f - 0f + 0f + 0f - 0f + 0f, 0f) // row 1 of section 1: 20 + 10
        assertEquals(30f, g.itemLeft(11) , 0f) // index 11 = column 3
    }
}
