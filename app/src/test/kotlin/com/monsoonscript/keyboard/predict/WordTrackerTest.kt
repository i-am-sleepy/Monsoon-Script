package com.monsoonscript.keyboard.predict

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WordTrackerTest {
    private fun typed(s: String) = WordTracker().also { it.appendAll(s) }

    @Test fun followsTheWordInProgressAndTheTwoBefore() {
        val t = typed("How are yo")
        assertEquals("yo", t.prefix)
        assertEquals("are", t.w1)
        assertEquals("how", t.w2)
        assertFalse(t.atSentenceStart)
    }

    @Test fun typedKeepsCapitalsPrefixDoesNot() {
        val t = typed("Hel")
        assertEquals("Hel", t.typed)
        assertEquals("hel", t.prefix)
    }

    @Test fun sentenceEndForgetsTheContext() {
        val t = typed("I like it. Th")
        assertNull(t.w1)
        assertNull(t.w2)
        assertEquals("th", t.prefix)
        val s = typed("I like it. ")
        assertTrue(s.atSentenceStart)
        assertEquals("", s.prefix)
    }

    @Test fun bengaliDariEndsASentence() {
        val t = typed("আমি ভালো। ")
        assertNull(t.w1)
        assertTrue(t.atSentenceStart)
    }

    @Test fun apostropheStaysInsideAWord() {
        val t = typed("i don't")
        assertEquals("don't", t.prefix)
        val u = typed("i don't ")
        assertEquals("don't", u.w1)
        assertEquals("i", u.w2)
    }

    @Test fun backspaceShortensTheWordAndFlagsAResyncPastItsStart() {
        val t = typed("ab")
        t.backspace()
        assertEquals("a", t.prefix)
        assertFalse(t.needsResync)
        t.backspace()
        t.backspace() // nothing left: the deleted character was before this word
        assertTrue(t.needsResync)
    }

    @Test fun resetRebuildsFromTextBeforeTheCursor() {
        val t = typed("junk junk")
        t.reset("Hello there. How are yo")
        assertEquals("yo", t.prefix)
        assertEquals("are", t.w1)
        assertEquals("how", t.w2)
        assertFalse(t.needsResync)
        t.reset(null)
        assertEquals("", t.prefix)
        assertNull(t.w1)
        assertTrue(t.atSentenceStart)
    }

    @Test fun finishWordReturnsTheKeyAndMovesTheHistory() {
        val t = typed("Good")
        assertEquals("good", t.finishWord())
        assertEquals("good", t.w1)
        assertNull(t.finishWord())
    }

    @Test fun dropWordKeepsTheHistory() {
        val t = typed("see you")
        t.dropWord()
        assertEquals("", t.prefix)
        assertEquals("see", t.w1)
    }

    @Test fun bengaliWordsAndPrecomposedNukta() {
        val t = typed("আমি কায়")
        assertEquals("কায়", t.prefix) // decomposed য় typed -> precomposed key
        assertEquals("আমি", t.w1)
    }
}
