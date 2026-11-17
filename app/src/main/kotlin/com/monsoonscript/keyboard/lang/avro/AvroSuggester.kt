/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Derived from Avro Keyboard (OmicronLab), clsE2BCharBased.pas (ParseAndSend, AddSuffix), uRegExPhoneticSearch.pas
 * and uSimilarSort.pas. Kotlin port for Monsoon Script; see third_party/avro-reference/NOTICE.md.
 */

package com.monsoonscript.keyboard.lang.avro

import java.io.InputStream
import java.util.TreeSet
import java.util.regex.Pattern
import java.util.regex.PatternSyntaxException

/** Supplies the dictionary words of one bucket (see [AvroBucketMap]). Called from the suggester's thread only. */
fun interface AvroWordSource {
    fun load(bucket: String): Array<String>
}

/** The English -> Bangla suffix table (`suffixes.tsv`: `english<TAB>bangla` per line). */
object AvroSuffixes {
    fun parse(lines: Sequence<String>): HashMap<String, String> {
        val map = HashMap<String, String>(1024)
        for (line in lines) {
            val tab = line.indexOf('\t')
            if (tab < 1 || tab == line.length - 1) continue
            map[line.substring(0, tab).lowercase()] = line.substring(tab + 1).trim()
        }
        return map
    }

    fun load(input: InputStream): HashMap<String, String> =
        input.bufferedReader(Charsets.UTF_8).use { parse(it.lineSequence()) }
}

/**
 * Dictionary suggestions for the word being typed, the way Avro Keyboard does it: the typed letters become a regular
 * expression of all plausible Bangla spellings, every dictionary word of the matching first-letter buckets is tested
 * against it, suffixed forms are built from matches for the stem, and the result is ordered by how close each word is
 * to the plain phonetic conversion.
 *
 * Pure Kotlin, NOT thread-safe: use from one background thread. Slow on purpose-built data (tens of ms), so never
 * call it from the touch path.
 */
class AvroSuggester(
    private val words: AvroWordSource,
    private val suffixes: Map<String, String>,
) {
    private val builder = AvroRegexBuilder()

    /** Recent search results by lower-case typed word: typing one more letter re-uses the stems for suffix forms. */
    private val cache = object : LinkedHashMap<String, List<String>>(32, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, List<String>>?): Boolean = size > CACHE_SIZE
    }

    /**
     * Dictionary words for [english] (the typed word, letters only), best match first, at most [limit].
     * [plainBangla] is the direct phonetic conversion without autocorrect; closeness to it decides the order.
     */
    fun suggest(english: String, plainBangla: String, limit: Int = DEFAULT_LIMIT): List<String> {
        if (english.isEmpty() || english.length > MAX_ENGLISH_LENGTH) return emptyList()
        val found = TreeSet<String>() // sorted + no duplicates, like Avro's sorted TStringList with dupIgnore
        found.addAll(search(english))
        addSuffixForms(english, found)
        if (found.isEmpty()) return emptyList()

        val ranked = ArrayList<Scored>(found.size)
        for (w in found) ranked.add(Scored(w, distance(plainBangla, w)))
        ranked.sortWith(compareBy { it.distance }) // stable: ties stay alphabetical
        val n = if (ranked.size < limit) ranked.size else limit
        val out = ArrayList<String>(n)
        for (i in 0 until n) out.add(ranked[i].word)
        return out
    }

    private class Scored(val word: String, val distance: Int)

    /** Stem results + suffix, for every split of the typed word whose tail is a known suffix. */
    private fun addSuffixForms(english: String, into: MutableSet<String>) {
        val len = english.length
        if (len < 2) return
        for (j in 2..len) {
            val tail = english.substring(j - 1).lowercase()
            val suffix = suffixes[tail] ?: continue
            val stem = english.substring(0, j - 1)
            for (r in search(stem)) {
                if (r.isEmpty()) continue
                val last = r[r.length - 1]
                if (isVowel(last) && isKar(suffix[0])) {
                    into.add(r + b_Y + suffix)
                } else if (last == b_Khandatta[0]) {
                    into.add(r.substring(0, r.length - 1) + b_T + suffix)
                } else if (last == b_Anushar[0]) {
                    into.add(r.substring(0, r.length - 1) + b_NGA + suffix)
                } else {
                    into.add(r + suffix)
                }
            }
        }
    }

    /** All dictionary words that fully match the phonetic regex of [english] (cached). */
    private fun search(english: String): List<String> {
        val key = english.lowercase()
        cache[key]?.let { return it }
        val result = doSearch(english)
        cache[key] = result
        return result
    }

    private fun doSearch(english: String): List<String> {
        val query = builder.convert(english)
        if (query.isEmpty()) return emptyList()
        val pattern = try {
            Pattern.compile(query)
        } catch (e: PatternSyntaxException) {
            return emptyList()
        }
        val first = english[0].let { if (it in 'A'..'Z') (it.code + 32).toChar() else it }
        val matcher = pattern.matcher("")
        val out = ArrayList<String>()
        for (bucket in AvroBucketMap.forLetter(first)) {
            for (w in words.load(bucket)) {
                if (matcher.reset(w).matches()) out.add(w)
            }
        }
        return out
    }

    private fun isVowel(c: Char): Boolean = when (c) {
        b_A[0], b_AA[0], b_AAkar[0], b_I[0], b_II[0], b_IIkar[0], b_Ikar[0], b_U[0], b_Ukar[0], b_UU[0], b_UUkar[0],
        b_RRI[0], b_RRIkar[0], b_E[0], b_Ekar[0], b_OI[0], b_OIkar[0], b_O[0], b_Okar[0], b_OU[0], b_OUkar[0],
        b_Vocalic_L[0], b_Vocalic_LL[0], b_Vocalic_RR[0], b_Vocalic_RR_Kar[0], b_Vocalic_L_Kar[0], b_Vocalic_LL_Kar[0] -> true
        else -> false
    }

    private fun isKar(c: Char): Boolean = when (c) {
        b_AAkar[0], b_IIkar[0], b_Ikar[0], b_Ukar[0], b_UUkar[0], b_RRIkar[0], b_Ekar[0], b_OIkar[0], b_OUkar[0],
        b_Vocalic_RR_Kar[0], b_Vocalic_L_Kar[0], b_Vocalic_LL_Kar[0] -> true
        else -> false
    }

    companion object {
        const val DEFAULT_LIMIT = 12
        const val MAX_ENGLISH_LENGTH = 50
        private const val CACHE_SIZE = 24

        /** Levenshtein distance between two strings (edit distance in UTF-16 units). */
        fun distance(s: String, t: String): Int {
            val n = s.length
            val m = t.length
            if (n == 0) return m
            if (m == 0) return n
            var prev = IntArray(m + 1) { it }
            var cur = IntArray(m + 1)
            for (i in 1..n) {
                cur[0] = i
                val si = s[i - 1]
                for (j in 1..m) {
                    val cost = if (si == t[j - 1]) 0 else 1
                    var v = prev[j] + 1
                    val ins = cur[j - 1] + 1
                    if (ins < v) v = ins
                    val sub = prev[j - 1] + cost
                    if (sub < v) v = sub
                    cur[j] = v
                }
                val tmp = prev; prev = cur; cur = tmp
            }
            return prev[m]
        }
    }
}
