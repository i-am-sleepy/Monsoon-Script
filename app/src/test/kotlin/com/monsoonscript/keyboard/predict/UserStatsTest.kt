package com.monsoonscript.keyboard.predict

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class UserStatsTest {
    private var clock = 1_000_000_000_000L
    private val stats = UserStats { clock }

    @Test fun countsWordsAndPairs() {
        stats.learn(null, "hello")
        stats.learn("hello", "world")
        stats.learn("hello", "world")
        assertEquals(1.0, stats.wordWeight("hello"), 1e-9)
        assertEquals(2.0, stats.wordWeight("world"), 1e-9)
        assertEquals(2.0, stats.pairWeight("hello", "world"), 1e-9)
        assertEquals(2.0, stats.followTotal("hello"), 1e-9)
        assertEquals(listOf("world"), stats.followers("hello", 5))
        assertEquals(0.0, stats.wordWeight("missing"), 0.0)
    }

    @Test fun useFadesWithAHalfLifeOfThirtyDays() {
        stats.learn(null, "old")
        clock += 30L * 24 * 3600 * 1000
        assertEquals(0.5, stats.wordWeight("old"), 1e-6)
        stats.learn(null, "old") // new use on top of the faded one
        assertEquals(1.5, stats.wordWeight("old"), 1e-6)
    }

    @Test fun prefixSearchOrdersByWeight() {
        repeat(3) { stats.learn(null, "monsoon") }
        stats.learn(null, "monday")
        stats.learn(null, "other")
        assertEquals(listOf("monsoon", "monday"), stats.wordsWithPrefix("mon", 5))
        assertEquals(listOf("monsoon"), stats.wordsWithPrefix("mon", 1))
    }

    @Test fun changesAreHandedOverOnceAndRestoreIsNotAChange() {
        stats.learn("a", "b")
        val rows = stats.takeChanges()
        assertEquals(2, rows.size) // the word "b" and the pair a -> b
        assertTrue(stats.takeChanges().isEmpty())
        val other = UserStats { clock }
        for (r in rows) other.restore(r)
        assertEquals(1.0, other.pairWeight("a", "b"), 1e-9)
        assertEquals(1.0, other.wordWeight("b"), 1e-9) // the word row must come back too
        assertTrue(rows.any { it.w1 == "b" && it.w2.isEmpty() }) // a word row is (word, "")
        assertTrue(other.takeChanges().isEmpty())
    }

    @Test fun clearForgetsEverything() {
        stats.learn("a", "b")
        stats.clear()
        assertEquals(0, stats.wordCount)
        assertEquals(0, stats.pairTotal)
        assertEquals(0.0, stats.total(), 0.0)
        assertTrue(stats.takeChanges().isEmpty())
    }

    @Test fun growthIsCappedAndForgottenEntriesAreReportedForDeletion() {
        for (i in 0 until 31_000) stats.learn(null, "w$i")
        assertTrue("words ${stats.wordCount}", stats.wordCount <= 30_000)
        val rows = stats.takeChanges()
        assertTrue(rows.any { it.count == 0.0 }) // the store must delete what was pruned
    }
}
