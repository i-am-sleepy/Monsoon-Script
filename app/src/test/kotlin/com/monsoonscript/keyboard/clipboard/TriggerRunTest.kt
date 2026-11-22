package com.monsoonscript.keyboard.clipboard

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TriggerRunTest {
    private val run = TriggerRun()

    @Test
    fun collectsCharactersUntilWhitespace() {
        run.append(";si")
        assertEquals(";si", run.text)
        run.append('g')
        assertEquals(";sig", run.text)
        run.append(' ')
        assertEquals("", run.text)
        run.append("a\nb")
        assertEquals("b", run.text)
    }

    @Test
    fun backspaceShortensTheRunAndSaysWhenItCannot() {
        run.append(";s")
        assertTrue(run.backspace())
        assertEquals(";", run.text)
        assertTrue(run.backspace())
        assertEquals("", run.text)
        assertFalse(run.backspace()) // empty: the deleted character was not ours to know
    }

    @Test
    fun aRunLongerThanAnyTriggerNeverMatchesAndNeedsAResyncOnBackspace() {
        run.append("x".repeat(ClipStore.MAX_TRIGGER))
        assertEquals(ClipStore.MAX_TRIGGER, run.text.length)
        run.append('y')
        assertEquals("", run.text)
        run.append("zzz")
        assertEquals("", run.text)
        assertFalse(run.backspace())
        run.append(' ')
        run.append("ab")
        assertEquals("ab", run.text)
    }

    @Test
    fun resetTakesTheLastRunFromTheTextBeforeTheCursor() {
        run.reset("hello ;si")
        assertEquals(";si", run.text)
        run.reset("hello ")
        assertEquals("", run.text)
        run.reset(null)
        assertEquals("", run.text)
        run.reset("line one\nlast")
        assertEquals("last", run.text)
        run.reset("y".repeat(ClipStore.MAX_TRIGGER + 5))
        assertEquals("", run.text)
        assertFalse(run.backspace())
    }

    @Test
    fun stripLabelIsTheFirstNonBlankLineCollapsedAndShortened() {
        assertEquals("Best regards, Sam", ClipFormat.stripLabel("\n  Best   regards,\tSam  \nsecond line"))
        assertEquals("", ClipFormat.stripLabel("  \n \n"))
        assertEquals("", ClipFormat.stripLabel(""))
        assertEquals("abcde", ClipFormat.stripLabel("abcdefgh", 5))
        val emoji = "ab" + "😀" + "cd" // a smiley is two chars: never cut in half
        assertEquals("ab", ClipFormat.stripLabel(emoji, 3))
        assertEquals("ab😀", ClipFormat.stripLabel(emoji, 4))
    }
}
