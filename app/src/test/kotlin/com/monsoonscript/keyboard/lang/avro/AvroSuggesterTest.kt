/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Tests for the Kotlin port of Avro Keyboard's phonetic dictionary search; see third_party/avro-reference/NOTICE.md.
 */

package com.monsoonscript.keyboard.lang.avro

import java.io.File
import java.util.regex.Pattern
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AvroSuggesterTest {

    // Unit tests run with the module directory (app/) as working directory.
    private val assets = File("src/main/assets/avro")
    private val plainConverter = AvroPhoneticConverter().also { it.autoCorrectEnabled = false }
    private val loaded = HashMap<String, Array<String>>()

    private val suggester: AvroSuggester by lazy {
        val suffixes = AvroSuffixes.load(File(assets, "suffixes.tsv").inputStream())
        AvroSuggester({ b -> loaded.getOrPut(b) { File(assets, "dict/$b.txt").readLines(Charsets.UTF_8).toTypedArray() } }, suffixes)
    }

    private fun suggest(english: String) = suggester.suggest(english, plainConverter.convert(english))

    private fun suggesterWithRank(rank: (String) -> Int): AvroSuggester {
        val suffixes = AvroSuffixes.load(File(assets, "suffixes.tsv").inputStream())
        return AvroSuggester({ b -> loaded.getOrPut(b) { File(assets, "dict/$b.txt").readLines(Charsets.UTF_8).toTypedArray() } }, suffixes, rank)
    }

    @Test fun rankOnlyOrdersWordsThatAreEquallyClose() {
        val plain = plainConverter.convert("kam")
        val base = suggester.suggest("kam", plain, 200)
        val up = suggesterWithRank { w -> w.hashCode() and 0xFFFF }.suggest("kam", plain, 200)
        assertEquals(base.toSet(), up.toSet()) // same words, only the order may change
        var last = -1
        for (w in up) { // still sorted by distance to the plain conversion
            val d = AvroSuggester.distance(plain, w)
            assertTrue("$w distance $d after $last", d >= last)
            last = d
        }
        // among words at the best distance the higher rank comes first
        val bestD = AvroSuggester.distance(plain, up.first())
        val tied = up.takeWhile { AvroSuggester.distance(plain, it) == bestD }
        val ranks = tied.map { it.hashCode() and 0xFFFF }
        assertEquals(ranks.sortedDescending(), ranks)
    }

    @Test fun everyBucketFileExists() {
        for (b in AvroBucketMap.ALL) assertTrue("missing dict/$b.txt", File(assets, "dict/$b.txt").isFile)
    }

    @Test fun regexMatchesPhoneticSpellings() {
        val q = AvroRegexBuilder().convert("ami")
        val p = Pattern.compile(q)
        assertTrue(p.matcher("আমি").matches())
        assertTrue(p.matcher("আমী").matches())
        assertTrue(!p.matcher("তুমি").matches())
    }

    @Test fun repeatedLeadingLettersGiveNoQuery() {
        assertEquals("", AvroRegexBuilder().convert("aaaab"))
        assertEquals("", AvroRegexBuilder().convert(""))
        assertEquals("", AvroRegexBuilder().convert("..."))
    }

    @Test fun commonWordsAreFoundAndTheDirectConversionComesFirst() {
        assertEquals("তুমি", suggest("tumi").first())
        assertEquals("আমাদের", suggest("amader").first())
        // য় is written as the single code point U+09DF, the form the dictionary and the converter use.
        assertTrue(suggest("kothay").contains("\u0995\u09CB\u09A5\u09BE\u09DF"))
        assertTrue(suggest("ekta").contains("একটা"))
        assertTrue(suggest("sonar").contains("সোনার"))
        assertTrue(suggest("bhalobasha").contains("ভালোবাসা"))
    }

    @Test fun colonInsideAWordFindsVisargaWords() {
        // "du:kho" -> দুঃখ (দ + উ-কার + visarga + খ); the smart colon (D24) relies on the search understanding a colon between letters.
        assertTrue("got ${suggest("du:kho")}", suggest("du:kho").contains("দুঃখ"))
    }

    @Test fun suffixFormsAreBuiltFromTheStem() {
        // "bhalobashar" = ভালোবাসা + র; the stem "bhalobasha" is searched and the suffix table adds the ending.
        val r = suggest("bhalobashar")
        assertTrue("got $r", r.any { it.startsWith("ভালোবাস") && it.endsWith("র") })
    }

    @Test fun unknownOrEmptyInputGivesNothing() {
        assertTrue(suggest("").isEmpty())
        assertTrue(suggest("zzzzzzzz").isEmpty())
        assertTrue(suggester.suggest("a".repeat(60), "").isEmpty())
    }

    @Test fun limitIsHonoured() {
        assertTrue(suggester.suggest("kar", plainConverter.convert("kar"), 3).size <= 3)
    }

    @Test fun levenshteinDistance() {
        assertEquals(3, AvroSuggester.distance("kitten", "sitting"))
        assertEquals(0, AvroSuggester.distance("আমি", "আমি"))
        assertEquals(3, AvroSuggester.distance("", "abc"))
        assertEquals(3, AvroSuggester.distance("abc", ""))
    }

    @Test fun suffixFileParses() {
        val m = AvroSuffixes.parse(sequenceOf("ar\tার", "bad line", "x\t", "E\tে"))
        assertEquals(mapOf("ar" to "ার", "e" to "ে"), m)
        assertTrue(AvroSuffixes.load(File(assets, "suffixes.tsv").inputStream()).size > 500)
    }
}
