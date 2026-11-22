package com.monsoonscript.keyboard.clipboard

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RecentCopyTest {
    private var now = 1_000L
    private val recent = RecentCopy { now }

    @Test
    fun nothingCopiedMeansNoChip() {
        assertNull(recent.current())
        assertEquals(0L, recent.remainingMs())
    }

    @Test
    fun theChipLastsOneMinute() {
        recent.note("hello")
        now += 59_999
        assertEquals("hello", recent.current())
        assertEquals(1L, recent.remainingMs())
        now += 1
        assertNull(recent.current())
        assertEquals(0L, recent.remainingMs())
    }

    @Test
    fun aNewCopyReplacesTheOldAndRestartsTheTime() {
        recent.note("one")
        now += 50_000
        recent.note("two")
        now += 50_000
        assertEquals("two", recent.current())
    }

    @Test
    fun dismissAndBlankTextsEndOrSkipTheChip() {
        recent.note("keep")
        recent.note("  \n")
        assertEquals("keep", recent.current()) // a blank copy changes nothing
        recent.dismiss()
        assertNull(recent.current())
    }
}
