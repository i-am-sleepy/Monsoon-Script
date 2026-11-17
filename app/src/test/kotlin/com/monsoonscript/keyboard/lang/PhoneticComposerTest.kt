package com.monsoonscript.keyboard.lang

import com.monsoonscript.keyboard.lang.avro.AvroPhoneticConverter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PhoneticComposerTest {

    private fun composer() = PhoneticComposer(AvroPhoneticConverter().also { it.autoCorrectEnabled = false })

    private fun PhoneticComposer.type(s: String) = s.forEach { append(it) }

    @Test fun showsLiveBangla() {
        val c = composer()
        assertEquals("", c.text())
        c.type("a")
        assertEquals("আ", c.text())
        c.append('m')
        c.append('i')
        assertEquals("আমি", c.text())
        assertEquals("ami", c.rawText())
    }

    @Test fun backspaceRemovesOneLatinLetter() {
        val c = composer()
        c.type("ami")
        c.deleteLast()
        assertEquals("আম", c.text())
        c.deleteLast()
        c.deleteLast()
        assertFalse(c.isComposing)
        c.deleteLast() // harmless when empty
        assertEquals("", c.text())
    }

    @Test fun clearForgetsTheWord() {
        val c = composer()
        c.type("tumi")
        c.clear()
        assertFalse(c.isComposing)
        assertEquals("", c.rawText())
    }

    @Test fun acceptsLettersAndWordMarksOnlyInsideAWord() {
        val c = composer()
        assertTrue(c.accepts('a'))
        assertTrue(c.accepts('Z'))
        assertFalse(c.accepts('^')) // nothing typed yet
        assertFalse(c.accepts('`'))
        assertFalse(c.accepts(' '))
        assertFalse(c.accepts('.'))
        assertFalse(c.accepts('1'))
        c.append('a')
        assertTrue(c.accepts('^'))
        assertTrue(c.accepts('`'))
        assertFalse(c.accepts(' '))
        assertFalse(c.accepts('1'))
    }

    @Test fun colonIsOnlyPartOfAWordAfterALetter() {
        val c = composer()
        assertFalse(c.accepts(':')) // a colon on its own is just a colon
        c.append('a')
        assertTrue(c.accepts(':'))
    }

    @Test fun smartColonStaysPlainAtTheEndOfTheWord() {
        val c = composer()
        c.type("ami:")
        assertEquals("আমি:", c.text()) // আমি: with an ASCII colon, not visarga
        assertTrue(c.endsWithColon)
    }

    @Test fun smartColonBecomesVisargaWhenALetterFollows() {
        val c = composer()
        c.type("du:")
        assertEquals("দু:", c.text()) // দু: still plain
        c.append('k')
        assertEquals("দুঃক", c.text()) // দুঃক: the colon turned into visarga
        c.append('h')
        c.append('o')
        assertEquals("দুঃখ", c.text().take(4)) // দুঃখ...
        assertFalse(c.endsWithColon)
    }

    @Test fun backspaceOverTheLetterAfterAColonBringsThePlainColonBack() {
        val c = composer()
        c.type("du:k")
        c.deleteLast()
        assertEquals("দু:", c.text())
    }

    @Test fun backtickAfterColonKeepsItPlainEvenBeforeALetter() {
        val c = composer()
        c.type("ami:`")
        assertEquals("আমি:", c.text())
        assertFalse(c.endsWithColon)
    }

    @Test fun capitalsKeepTheirPhoneticMeaning() {
        val c = composer()
        c.type("Dhaka")
        assertEquals("ঢাকা", c.text())
    }

    @Test fun cursorLeavingTheWordIsDetected() {
        // region 5..9 reported, cursor at its end: our own edit echoing back
        assertFalse(PhoneticComposer.externalMove(9, 9, 5, 9, 0))
        // cursor moved inside the word, or a selection appeared
        assertTrue(PhoneticComposer.externalMove(7, 7, 5, 9, 0))
        assertTrue(PhoneticComposer.externalMove(5, 9, 5, 9, 0))
        // no region right after our edit: probably a late echo of an older state, ignore
        assertFalse(PhoneticComposer.externalMove(4, 4, -1, -1, 40))
        // no region long after our last edit: the app dropped the composition (user tapped elsewhere)
        assertTrue(PhoneticComposer.externalMove(20, 20, -1, -1, PhoneticComposer.STALE_MS + 1))
    }
}
