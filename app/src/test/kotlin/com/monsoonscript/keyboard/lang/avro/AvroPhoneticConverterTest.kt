/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Tests for the Kotlin port of Avro Keyboard's phonetic converter; see third_party/avro-reference/NOTICE.md.
 */

package com.monsoonscript.keyboard.lang.avro

import java.io.File
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AvroPhoneticConverterTest {

    // Unit tests run with the module directory (app/) as working directory.
    private val dictFile = File("src/main/assets/avro/autodict.dct")
    private val dict: Map<String, String> by lazy { AvroAutoCorrect.load(dictFile.inputStream()) }

    private fun plain() = AvroPhoneticConverter().also { it.autoCorrectEnabled = false }
    private fun withDict() = AvroPhoneticConverter(dict)

    private fun check(c: AvroPhoneticConverter, vararg pairs: Pair<String, String>) {
        for ((input, expected) in pairs) assertEquals("convert(\"$input\")", expected, c.convert(input))
    }

    @Test fun commonWords() = check(
        plain(),
        "ami" to "আমি",
        "tumi" to "তুমি",
        "amar" to "আমার",
        "bangla" to "বাংলা",
        "bangladesh" to "বাংলাদেশ",
        "kothay" to "\u0995\u09A5\u09BE\u09DF", // ক থ া য় (U+09DF, the single-code-point য়)
        "bhalobasha" to "ভালবাশা",
        "korbo" to "করব",
    )

    @Test fun capitalsMeanSomething() = check(
        plain(),
        "Dhaka" to "ঢাকা", // Dh = ঢ
        "TaKa" to "টাকা", // T = ট, K is just k
        "bhalo" to "ভাল", // final o after a consonant is silent
        "bhalO" to "ভালো", // capital O gives the o-kar
        "OI" to "ঐ",
        "oi" to "অই",
    )

    @Test fun conjunctsAndRaFola() = check(
        plain(),
        "shikkha" to "শিক্ষা", // kkh = ক্ষ
        "krishno" to "ক্রিশ্ন", // r after a consonant = ra-fola
        "rri" to "ঋ",
        "khkh" to "খখ",
    )

    @Test fun digitsSymbolsAndDari() = check(
        plain(),
        "123" to "১২৩",
        "$" to "৳",
        "ami." to "আমি।",
        "1.5" to "১.৫", // a dot before a digit is a decimal mark, not a dari
    )

    @Test fun emptyInputGivesEmpty() {
        assertEquals("", plain().convert(""))
        assertEquals("", withDict().convert(""))
    }

    @Test fun joNuktaIsOptIn() {
        val c = plain()
        assertEquals("জ", c.convert("J"))
        c.joNukta = true
        assertEquals("জ়", c.convert("J"))
    }

    @Test fun correctCaseLowersOnlyMeaninglessCapitals() {
        val c = plain()
        assertEquals("kaRi", c.correctCase("KaRi"))
        assertEquals("jaj", c.correctCase("JaJ"))
        c.joNukta = true
        assertEquals("JaJ", c.correctCase("JaJ"))
    }

    @Test fun cutTextSplitsPunctuation() {
        val c = plain()
        assertArrayEquals(arrayOf("(", "ami", "),"), c.cutText("(ami),"))
        assertArrayEquals(arrayOf("", "ami", ""), c.cutText("ami"))
    }

    @Test fun autoCorrectFileParses() {
        assertTrue("autodict.dct should have thousands of entries", dict.size > 2000)
        val m = AvroAutoCorrect.parse(sequenceOf("/ comment", "", "  abc xyz  ", "key two words", "broken"))
        assertEquals(mapOf("abc" to "xyz", "key" to "two words"), m)
    }

    @Test fun autoCorrectChangesTheResult() {
        // "je" is in the dictionary as "ze" (য), plain phonetics would give জে.
        assertEquals("যে", withDict().convert("je"))
        assertEquals("জে", plain().convert("je"))
        // Punctuation around a dictionary word is ignored for the lookup.
        assertEquals("(যে)", withDict().convert("(je)"))
    }

    /** Regression net: many random inputs with outputs from an independent Python translation of the Pascal. */
    @Test fun goldenFile() {
        val stream = javaClass.getResourceAsStream("/avro/golden.tsv")!!
        val on = withDict()
        val off = plain()
        val nuk = plain().also { it.joNukta = true }
        var n = 0
        stream.bufferedReader(Charsets.UTF_8).useLines { lines ->
            for (line in lines) {
                if (line.isEmpty() || line[0] == '#') continue
                val f = line.split('\t')
                assertEquals("bad golden line: $line", 4, f.size)
                assertEquals("on: ${f[0]}", f[1], on.convert(f[0]))
                assertEquals("off: ${f[0]}", f[2], off.convert(f[0]))
                assertEquals("nukta: ${f[0]}", f[3], nuk.convert(f[0]))
                n++
            }
        }
        assertTrue("golden file should hold hundreds of cases (had $n)", n > 500)
    }
}
