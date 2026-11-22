package com.monsoonscript.keyboard.predict

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Typo fixing on the real packs (L8, D69). The numbers behind the rules were measured over the whole word lists, see Corrector. */
class CorrectorTest {
    private fun fix(
        typed: String, w1: String? = null, capitalizeStart: Boolean = false, stats: UserStats? = null, rejected: Set<String> = emptySet(),
    ): Corrector.Fix? = Corrector.correct(Packs.en, Packs.rom, stats, typed, w1, capitalizeStart, rejected)

    private fun auto(typed: String, w1: String? = null): String? = fix(typed, w1)?.takeIf { it.auto }?.word

    @Test fun fixesWellKnownMisspellings() {
        val cases = listOf(
            "teh" to "the", "adn" to "and", "thier" to "their", "recieve" to "receive", "becuase" to "because",
            "definately" to "definitely", "seperate" to "separate", "occured" to "occurred", "wierd" to "weird",
            "beleive" to "believe", "adress" to "address", "wich" to "which",
        )
        for ((typo, word) in cases) assertEquals(typo, word, auto(typo))
    }

    @Test fun putsTheApostropheBackIntoContractions() {
        val cases = listOf(
            "dont" to "don't", "cant" to "can't", "wont" to "won't", "didnt" to "didn't", "doesnt" to "doesn't",
            "couldnt" to "couldn't", "isnt" to "isn't",
        )
        for ((typo, word) in cases) assertEquals(typo, word, auto(typo))
    }

    @Test fun fixesAClearSlipInAWordNoListKnows() {
        assertEquals("friend", auto("freind")) // two letters swapped
        assertEquals("until", auto("untill")) // a doubled letter
        assertEquals("almost", auto("allmost"))
        assertEquals("challenge", auto("chalenge"))
    }

    @Test fun leavesRealWordsAlone() {
        for (w in listOf("hello", "form", "bit", "hat", "its", "were", "well", "defer", "tomorrow", "asap", "the")) assertNull(w, fix(w))
    }

    @Test fun leavesBanglishWordsAlone() {
        for (w in listOf("khub", "kemon", "accha", "apni", "valo", "vai", "bhai", "tumi", "ami", "amar", "korbo", "kotha", "ekhon")) {
            assertNull(w, fix(w))
        }
    }

    @Test fun staysSilentAfterABanglishWord() {
        // the same unknown word is fixed after an English word and left alone after a Banglish one
        assertEquals("challenge", auto("chalenge", "the"))
        assertNull(fix("chalenge", "ami"))
        // a well-known English misspelling is still fixed in a Banglish sentence
        assertEquals("the", auto("teh", "ami"))
    }

    @Test fun isBanglishTellsTheTwoListsApart() {
        for (w in listOf("ami", "tumi", "valo", "kore", "hobe", "bhai", "khub", "kotha")) assertTrue(w, Corrector.isBanglish(Packs.en, Packs.rom, w))
        for (w in listOf("the", "and", "you", "hello", "please", "tomorrow", "meeting")) assertFalse(w, Corrector.isBanglish(Packs.en, Packs.rom, w))
    }

    @Test fun leavesShortWordsNamesCodesAndSlangAlone() {
        for (w in listOf("ab", "teh1", "x-y", "NASA", "iPhone", "eBay", "thx", "ive", "pls", "tmrw", "kubernetes")) assertNull(w, fix(w))
        assertNull(fix("a"))
        assertNull(fix("a".repeat(30)))
    }

    @Test fun aCapitalLetterOnlyCountsAtTheStartOfASentence() {
        assertEquals("the", fix("Teh", capitalizeStart = true)?.word) // sentence start: the owner's capital is kept by the worker
        assertNull(fix("Teh", capitalizeStart = false)) // a capital in the middle of a sentence means a name
        assertNull(fix("TEH", capitalizeStart = true)) // ALL CAPS is never touched
    }

    @Test fun leavesAnUnfinishedWordAlone() {
        // the letters so far start a common word: the owner is still typing
        for (w in listOf("importa", "wher", "pleas", "tha", "hel", "tomorro", "beaut", "intere")) assertNull(w, fix(w))
    }

    @Test fun neverTouchesAWordTheOwnerTypedOrUndid() {
        val stats = UserStats()
        assertEquals("the", auto("teh"))
        assertNull(Corrector.correct(Packs.en, Packs.rom, stats, "teh", null, false, setOf("teh"))) // undone
        stats.learn(null, "teh")
        assertNull(Corrector.correct(Packs.en, Packs.rom, stats, "teh", null, false)) // typed before
    }

    @Test fun aWeaklyListedRealWordIsOnlyOfferedNeverApplied() {
        val f = fix("arising")
        assertNotNull(f)
        assertFalse(f!!.auto)
    }

    @Test fun neverDeletesTheLastLetterOfAWordExceptADoubledOne() {
        assertFalse(Corrector.edits("emojis").containsKey("emoji")) // a valid plural, not a typo
        assertTrue(Corrector.edits("untill").containsKey("until"))
        assertNull(fix("emojis"))
    }

    @Test fun editsHaveTheRightPrices() {
        val e = Corrector.edits("teh")
        assertEquals(Corrector.C_TRANSPOSE, e["the"]!!, 1e-9)
        assertEquals(Corrector.C_NEIGHBOUR, e["ten"]!!, 1e-9) // h and n are next to each other
        assertFalse(e.containsKey("tehx")) // a letter missing at the end is a completion
        assertEquals(Corrector.C_APOSTROPHE, Corrector.edits("dont")["don't"]!!, 1e-9)
        assertEquals(Corrector.C_VOWEL, Corrector.edits("seperate")["separate"]!!, 1e-9)
        assertFalse(Corrector.edits("thx").containsKey("the")) // a far-away consonant for a vowel is not a slip
    }

    @Test fun everyListedMisspellingPointsToACommonWord() {
        for ((typo, word) in Misspellings.all()) {
            val i = Packs.en.indexOf(word)
            assertTrue("$typo -> $word is not in the list", i >= 0)
            assertTrue("$typo -> $word is too rare", Packs.en.countLevel(i) >= Corrector.MIN_FIX_LEVEL)
            assertNotNull("$typo is not typable as a word", Corrector.eligibleKey(typo, false))
        }
    }

    @Test fun noListedMisspellingIsARealWordOrBanglish() {
        for (typo in Misspellings.all().keys) {
            val e = Packs.en.indexOf(typo)
            assertTrue("$typo is too common in English to be a typo", e < 0 || Packs.en.countLevel(e) < 150)
            val r = Packs.rom.indexOf(typo)
            assertTrue("$typo is a Banglish word", r < 0 || Packs.rom.countLevel(r) < Corrector.TABLE_ROM_MAX)
        }
    }

    @Test fun aWordThatFollowsThePreviousWordScoresHigher() {
        val plain = Corrector.debugCandidates(Packs.en, "thier", null).first { it.word == "their" }.score
        val inContext = Corrector.debugCandidates(Packs.en, "thier", "in").first { it.word == "their" }.score
        assertTrue(inContext >= plain)
    }

    // ---- D73: the stronger corrector (the owner's test sentence: "It woks but miltales loke tgid get overlioked")

    @Test fun fixesTheOwnersTestSentence() {
        // each word after the word before it, as the keyboard sees it (the previous word is the FIXED one)
        assertEquals("works", auto("woks", "it")) // a rare listed word, the pair "it works" decides against "woke"
        assertEquals("mistakes", auto("miltales", "but")) // two slips
        assertEquals("like", auto("loke", "mistakes")) // also a Banglish word, but the English context and a very common fix decide
        assertEquals("this", auto("tgid", "like")) // two neighbouring keys
        assertEquals("overlooked", auto("overlioked", "get")) // one slip in a long word
    }

    @Test fun aWeaklyBanglishWordIsOnlyFixedWithEnglishContextAndAVeryCommonFix() {
        assertNull(fix("loke")) // no context: could be the Banglish word
        assertNull(fix("loke", "ami")) // Banglish context
        for (w in listOf("baraber", "islame", "horin", "jage", "chabir")) assertNull(w, fix(w, "the")) // real Banglish words, the fixes are not common enough
    }

    @Test fun twoSlipsAreFoundAndPriced() {
        assertEquals(Corrector.C_TRANSPOSE, Corrector.editCost("teh", "the", 2.1), 1e-9)
        assertEquals(Corrector.C_APOSTROPHE, Corrector.editCost("dont", "don't", 2.1), 1e-9)
        assertEquals(2 * Corrector.C_NEIGHBOUR, Corrector.editCost("tgid", "this", 2.1), 1e-9) // g next to h, d next to s
        assertEquals(Corrector.C_FAR + Corrector.C_NEIGHBOUR, Corrector.editCost("miltales", "mistakes", 2.1), 1e-9)
        assertTrue(Corrector.editCost("thx", "tomorrow", 2.1) > 2.1) // too far: never offered
        assertTrue(Corrector.candidates(Packs.en, "miltales", Corrector.MAX_COST).containsKey("mistakes"))
        assertFalse(Corrector.candidates(Packs.en, "miltales", Corrector.C_LETTER).containsKey("mistakes")) // one slip only: not found
    }

    @Test fun aRareListedWordNeedsAMuchMoreCommonFix() {
        assertEquals("works", auto("woks", "it"))
        assertNull(auto("arising")) // fairly common real word: never applied (a tap candidate at most)
        assertNull(auto("form")) // common real word
    }

    @Test fun aWordWithTwoSlipsNeedsFourLettersAndAClearLead() {
        assertNull(auto("thx")) // far-away slip, three letters
        assertNull(auto("pls"))
        assertEquals("this", auto("tgid", "like")) // two neighbouring keys in four letters is allowed
    }

    @Test fun strongerSearchStillLeavesRealWordsAndNamesAlone() {
        for (w in listOf("whatsapp", "bluetooth", "instagram", "youtube", "google", "iphone", "android", "emoji", "rahman", "karim", "sultana", "mehedi", "tanvir", "tamim", "sylhet")) {
            assertFalse(w, fix(w, "the")?.auto == true)
        }
    }
}
