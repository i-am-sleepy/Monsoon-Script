package com.monsoonscript.keyboard.predict

import java.util.TreeMap
import kotlin.math.exp
import kotlin.math.ln

/**
 * What the owner types, kept in memory (words and word pairs with a use count that fades over time) and handed to a
 * store for saving. Pure Kotlin; the Android SQLite store lives in `ime/`. Words are in [TextNorm.key] form.
 *
 * A use counts fully when it happens and loses half its weight every [HALF_LIFE_MS] (30 days), so recent habits win.
 */
class UserStats(private val now: () -> Long = { System.currentTimeMillis() }) {

    private class Entry(var count: Double, var last: Long)

    /** One saved fact: a word (w2 empty) or a pair (w1 followed by w2). */
    class Row(val w1: String, val w2: String, val count: Double, val last: Long)

    private val uni = TreeMap<String, Entry>()
    private val bi = HashMap<String, HashMap<String, Entry>>()
    private var pairCount = 0
    private var learnsSincePrune = 0
    private var totalCache = 0.0
    private var totalAt = Long.MIN_VALUE
    private val changed = LinkedHashMap<String, Row>()

    private fun weight(e: Entry, t: Long): Double = e.count * exp(-LN2 * (t - e.last).coerceAtLeast(0L) / HALF_LIFE_MS)

    /** Record that [word] was typed, after [prev] when the context is known. */
    fun learn(prev: String?, word: String) {
        if (word.isEmpty()) return
        val t = now()
        bump(uni.getOrPut(word) { Entry(0.0, t) }, t)
        changed[word] = row("", word, uni[word]!!)
        if (prev != null && prev.isNotEmpty()) {
            val m = bi.getOrPut(prev) { HashMap() }
            val e = m.getOrPut(word) { pairCount++; Entry(0.0, t) }
            bump(e, t)
            changed["$prev\u0000$word"] = row(prev, word, e)
        }
        totalCache += 1.0
        if (++learnsSincePrune >= PRUNE_EVERY) prune(t)
    }

    private fun bump(e: Entry, t: Long) {
        e.count = weight(e, t) + 1.0
        e.last = t
    }

    private fun row(w1: String, w2: String, e: Entry) = Row(w1, w2, e.count, e.last)

    // ---- reading (the predictor)

    /** Faded use count of a word, 0 when unknown. */
    fun wordWeight(word: String): Double = uni[word]?.let { weight(it, now()) } ?: 0.0

    /** Faded sum over all words: the denominator of the owner's own word probability. Recomputed now and then, updated cheaply in between. */
    fun total(): Double {
        val t = now()
        if (t - totalAt > TOTAL_REFRESH_MS) {
            var s = 0.0
            for (e in uni.values) s += weight(e, t)
            totalCache = s
            totalAt = t
        }
        return totalCache
    }

    /** The owner's words that start with [prefix], strongest first, at most [max]. */
    fun wordsWithPrefix(prefix: String, max: Int): List<String> {
        val t = now()
        val range = if (prefix.isEmpty()) uni else uni.subMap(prefix, prefix + '￿')
        return range.entries.sortedByDescending { weight(it.value, t) }.take(max).map { it.key }
    }

    fun strongestWords(max: Int): List<String> {
        val t = now()
        return uni.entries.sortedByDescending { weight(it.value, t) }.take(max).map { it.key }
    }

    fun pairWeight(prev: String, word: String): Double = bi[prev]?.get(word)?.let { weight(it, now()) } ?: 0.0

    /** Faded sum of all pairs that start with [prev]: the denominator for "what follows [prev]". */
    fun followTotal(prev: String): Double {
        val m = bi[prev] ?: return 0.0
        val t = now()
        var s = 0.0
        for (e in m.values) s += weight(e, t)
        return s
    }

    /** Words that followed [prev], strongest first, at most [max]. */
    fun followers(prev: String, max: Int): List<String> {
        val m = bi[prev] ?: return emptyList()
        val t = now()
        return m.entries.sortedByDescending { weight(it.value, t) }.take(max).map { it.key }
    }

    // ---- saving and loading

    /** Facts changed since the last call, for the store to save; empties the list. */
    fun takeChanges(): List<Row> {
        if (changed.isEmpty()) return emptyList()
        val r = ArrayList(changed.values)
        changed.clear()
        return r
    }

    /** Load a saved fact (does not mark it as changed). */
    fun restore(row: Row) {
        if (row.w2.isEmpty()) {
            uni[row.w1] = Entry(row.count, row.last)
        } else if (row.w1.isNotEmpty()) {
            val m = bi.getOrPut(row.w1) { HashMap() }
            if (m.put(row.w2, Entry(row.count, row.last)) == null) pairCount++
        }
        totalAt = Long.MIN_VALUE
    }

    fun clear() {
        uni.clear()
        bi.clear()
        pairCount = 0
        changed.clear()
        totalCache = 0.0
        totalAt = Long.MIN_VALUE
    }

    val wordCount: Int get() = uni.size
    val pairTotal: Int get() = pairCount

    /** Forget the weakest entries when the store grows large. Removed facts are reported as zero-count rows so the store deletes them. */
    private fun prune(t: Long) {
        learnsSincePrune = 0
        if (uni.size > MAX_WORDS) {
            val keep = uni.entries.sortedByDescending { weight(it.value, t) }.take(MAX_WORDS * 4 / 5).map { it.key }.toHashSet()
            val it = uni.entries.iterator()
            while (it.hasNext()) {
                val e = it.next()
                if (e.key !in keep) {
                    changed[e.key] = Row("", e.key, 0.0, t)
                    it.remove()
                }
            }
        }
        if (pairCount > MAX_PAIRS) {
            val all = ArrayList<Triple<String, String, Double>>(pairCount)
            for ((w1, m) in bi) for ((w2, e) in m) all.add(Triple(w1, w2, weight(e, t)))
            all.sortBy { it.third }
            for (k in 0 until (pairCount - MAX_PAIRS * 4 / 5)) {
                val (w1, w2, _) = all[k]
                bi[w1]?.remove(w2)
                if (bi[w1]?.isEmpty() == true) bi.remove(w1)
                changed["$w1\u0000$w2"] = Row(w1, w2, 0.0, t)
                pairCount--
            }
        }
    }

    companion object {
        const val HALF_LIFE_MS = 30.0 * 24 * 3600 * 1000
        private val LN2 = ln(2.0)
        private const val MAX_WORDS = 30_000
        private const val MAX_PAIRS = 60_000
        private const val PRUNE_EVERY = 500
        private const val TOTAL_REFRESH_MS = 5 * 60 * 1000L
    }
}
