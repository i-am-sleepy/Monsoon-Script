package com.monsoonscript.keyboard.predict

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PredictorTest {
    private val predictor = Predictor()

    private fun en(prefix: String, w1: String? = null, w2: String? = null, limit: Int = 5, p: Predictor = predictor) =
        p.predict(Packs.english(w1, w2), prefix, w1, w2, limit)

    private fun bn(prefix: String, w1: String? = null, w2: String? = null, limit: Int = 5) =
        predictor.predict(listOf(WeightedPack(Packs.bn)), prefix, w1, w2, limit)

    @Test fun completesAPrefixWithTheMostFrequentWordsFirst() {
        assertEquals("the", en("th").first())
        assertTrue(en("hel").containsAll(listOf("help", "hello")))
    }

    @Test fun neverSuggestsWhatIsAlreadyTyped() {
        assertFalse(en("hello").contains("hello"))
        assertFalse(en("the").contains("the"))
    }

    @Test fun nextWordUsesTheBigram() {
        assertEquals("to", en("", "how").first())
        assertTrue(en("", "thank").contains("you"))
        assertTrue(en("", "i").containsAll(listOf("don't", "think")))
    }

    @Test fun nextWordUsesTheTrigramBeforeTheBigram() {
        val r = en("", "don't", "i")
        assertTrue("got $r", r.contains("know") || r.contains("think"))
        assertFalse(r.contains("don't"))
    }

    @Test fun prefixAndContextWorkTogether() {
        // after "how", the words that start with "wo" that follow it come first
        val r = en("wo", "how")
        assertTrue(r.all { it.startsWith("wo") })
        assertTrue("got $r", r.contains("would") || r.contains("work"))
    }

    @Test fun fallsBackToCommonWordsWithoutContext() {
        val r = en("")
        assertEquals(5, r.size)
        assertTrue(r.contains("you") || r.contains("the"))
        // an unknown context word behaves like no context
        assertEquals(r.toSet(), en("", "qqqzzxx").toSet())
    }

    @Test fun resultsAreUniqueAndLimited() {
        val r = en("s", limit = 3)
        assertEquals(3, r.size)
        assertEquals(r.size, r.toSet().size)
        assertTrue(predictor.predict(emptyList(), "a", null, null).isEmpty())
        assertTrue(en("xqzv").isEmpty())
    }

    @Test fun banglishWordsAreSuggestedAndFollowABanglishContext() {
        assertTrue(en("bha").contains("bhalo") || en("bha").contains("bhai"))
        val r = en("tum", "ami")
        assertTrue("got $r", r.contains("tumi") || r.contains("tumar"))
        val next = en("", "ami")
        assertTrue("got $next", next.any { it in listOf("na", "to", "ki", "e") })
    }

    @Test fun banglaCompletionAndNextWord() {
        assertTrue(bn("বা").contains("বাংলা")) // বা -> বাংলা
        assertTrue(bn("", "আমি").contains("আমার")) // আমি -> আমার
        assertEquals(5, bn("").size)
    }

    @Test fun learnedWordsAndHabitsRankHigher() {
        val stats = UserStats()
        val p = Predictor(stats)
        assertFalse(en("monsoo", p = p).contains("monsoonx"))
        repeat(4) { stats.learn(null, "monsoonx") }
        assertEquals("monsoonx", en("monsoo", p = p).first())
        repeat(5) { stats.learn("ami", "tomake") }
        assertEquals("tomake", en("", "ami", p = p).first())
    }

    @Test fun learnedUseFadesAway() {
        var clock = 1_000_000_000_000L
        val stats = UserStats { clock }
        val p = Predictor(stats)
        repeat(4) { stats.learn(null, "monsoonx") }
        assertEquals("monsoonx", en("monsoo", p = p).first())
        clock += 400L * 24 * 3600 * 1000 // more than a year later
        val r = en("monsoo", p = p)
        assertTrue("got $r", r.isEmpty() || r.first() != "monsoonx" || stats.wordWeight("monsoonx") < 0.001)
    }

    @Test fun answersAreFast() {
        en("") // builds the most-frequent-words list once
        val t = System.nanoTime()
        repeat(50) { en("s") ; en("", "how") ; bn("ক") }
        val ms = (System.nanoTime() - t) / 1e6 / 150
        assertTrue("average $ms ms", ms < 25.0) // generous: slow CI machines, cold JIT
    }
}
