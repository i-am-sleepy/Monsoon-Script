package com.monsoonscript.keyboard.predict

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TextNormTest {
    @Test fun keyLowercasesLatinAndFixesApostrophes() {
        assertEquals("don't", TextNorm.key("Don’t"))
        assertEquals("hello", TextNorm.key("HELLO"))
    }

    @Test fun keyPrecomposesBengaliNukta() {
        assertEquals("য়", TextNorm.key("য়")) // য + ় -> য়
        assertEquals("ড়ঢ়", TextNorm.key("ড়ঢ়"))
        val already = "কায়"
        assertEquals(already, TextNorm.key(already))
    }

    @Test fun bengaliVowelSignsAndHasantaAreWordCharacters() {
        for (c in "আাি্ুংঃঁ‌‍abc") assertTrue(c.code.toString(16), TextNorm.isWordChar(c))
        for (c in " .,!?:;()।-1১") assertFalse(c.code.toString(16), TextNorm.isWordChar(c))
    }

    @Test fun sentenceEnds() {
        for (c in ".!?।\n") assertTrue(TextNorm.isSentenceEnd(c))
        assertFalse(TextNorm.isSentenceEnd(','))
    }

    @Test fun matchCaseFollowsWhatIsTyped() {
        assertEquals("Hello", TextNorm.matchCase("He", "hello", false))
        assertEquals("HELLO", TextNorm.matchCase("HE", "hello", false))
        assertEquals("hello", TextNorm.matchCase("he", "hello", false))
        assertEquals("Hello", TextNorm.matchCase("", "hello", true)) // sentence start
        assertEquals("hello", TextNorm.matchCase("", "hello", false))
        assertEquals("আমি", TextNorm.matchCase("", "আমি", true)) // no case in Bangla
    }
}
