package com.monsoonscript.keyboard.predict

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LangPackTest {
    private val en = Packs.en
    private val bn = Packs.bn
    private val rom = Packs.rom

    @Test fun sizesMatchTheStandardTier() {
        assertEquals(150_000, en.wordCount)
        assertEquals(150_000, bn.wordCount)
        assertTrue(rom.wordCount in 100_000..140_000)
        assertTrue(en.bigramCount > 100_000 && en.trigramCount > 50_000)
        assertEquals(0, rom.bigramCount) // the Banglish pair lists are not used (D41)
        assertEquals(0, rom.trigramCount)
    }

    @Test fun wordsRoundTripAndAreSorted() {
        var prev = ""
        for (i in 0 until en.wordCount step 97) {
            val w = en.word(i)
            assertEquals(i, en.indexOf(w))
            assertTrue("$prev < $w", prev < w)
            prev = w
        }
        assertEquals(-1, en.indexOf("zzzzzzzzqq"))
    }

    @Test fun prefixRangeHoldsExactlyTheWordsWithThatPrefix() {
        val s = en.prefixStart("thr")
        val e = en.prefixEnd("thr")
        assertTrue(e - s > 20)
        for (i in s until e) assertTrue(en.word(i).startsWith("thr"))
        assertFalse(en.word(s - 1).startsWith("thr"))
        assertFalse(en.word(e).startsWith("thr"))
    }

    @Test fun commonWordsAreFrequentAndBlockListMistakesAreRepaired() {
        assertTrue(en.count(en.indexOf("the")) > en.count(en.indexOf("zebra")))
        assertTrue("i" in listOf("i").filter { en.indexOf(it) >= 0 })
        assertTrue(en.indexOf("us") >= 0)
        assertTrue(bn.indexOf("বাংলাদেশ") >= 0) // বাংলাদেশ
    }

    @Test fun pairsAreFound() {
        val how = en.indexOf("how")
        assertTrue(en.bigramCount(how, en.indexOf("to")) > 0f)
        assertEquals(0f, en.bigramCount(how, en.indexOf("zebra").coerceAtLeast(0)), 0f)
        val i = en.indexOf("i")
        val dont = en.indexOf("don't")
        assertTrue(en.trigramCount(i, dont, en.indexOf("know")) > 0f)
    }

    @Test fun tatoebaNamesAreNotInThePairs() {
        val tom = en.indexOf("tom")
        assertTrue(tom >= 0) // still a word
        var i = en.bigramLowerBound(tom, 0)
        assertFalse(i < en.bigramCount && en.bigramW1(i) == tom)
        i = en.bigramLowerBound(en.indexOf("thank"), 0)
        while (i < en.bigramCount && en.bigramW1(i) == en.indexOf("thank")) {
            assertTrue(en.bigramW2(i) != tom)
            i++
        }
    }

    @Test fun bengaliWordsUseThePrecomposedNukta() {
        for (i in 0 until bn.wordCount) {
            val w = bn.word(i)
            assertFalse("decomposed nukta in word $i", w.contains("য়") || w.contains("ড়") || w.contains("ঢ়"))
        }
    }

    @Test fun banglishWordsAreLatinOnly() {
        for (i in 0 until rom.wordCount step 31) assertTrue(rom.word(i).all { it in 'a'..'z' || it == '\'' })
    }
}
