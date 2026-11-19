package com.monsoonscript.keyboard.emoji

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.io.StringReader

/** Tests on the real asset (unit tests run with app/ as the working directory). Emoji are written as code points so the file encoding cannot matter. */
class EmojiDataTest {

    private fun cp(vararg c: Int): String = String(c, 0, c.size)

    private val data: EmojiData by lazy { File("src/main/assets/emoji/emoji.tsv").bufferedReader(Charsets.UTF_8).use { EmojiData.parse(it) } }

    private fun find(s: String): Int = data.emoji.indexOf(s)

    private fun top(query: String, n: Int = 5): List<String> {
        val out = IntArray(n)
        val c = data.search(query, out)
        return (0 until c).map { data.emoji[out[it]] }
    }

    @Test
    fun theListIsComplete() {
        assertEquals(1914, data.size) // Unicode 17: 1,914 base emoji
        assertEquals(2030, data.variants.sumOf { it.size }) // and 2,030 skin-tone variants
        assertEquals(3944, data.flatCount)
        for (t in 0 until EmojiTabs.COUNT) assertTrue("tab $t is empty", data.tabStart[t + 1] > data.tabStart[t])
        assertEquals(data.size, data.tabStart[EmojiTabs.COUNT])
        for (i in 1 until data.size) assertTrue("entries are sorted by tab", data.tab[i] >= data.tab[i - 1])
    }

    @Test
    fun everyEmojiIsOneUniqueWholeString() {
        val seen = HashSet<String>()
        for (i in 0 until data.size) {
            assertTrue("duplicate ${data.emoji[i]}", seen.add(data.emoji[i]))
            for (v in data.variants[i]) {
                assertTrue("duplicate variant $v", seen.add(v))
                assertEquals(i, data.baseOf(v))
            }
            assertEquals(i, data.baseOf(data.emoji[i]))
        }
        assertEquals(-1, data.baseOf("not an emoji"))
    }

    @Test
    fun sequencesStayWhole() {
        val grin = cp(0x1F600)
        assertEquals(0, data.tab[find(grin)])
        val flagBd = cp(0x1F1E7, 0x1F1E9) // regional indicators B + D
        assertEquals(7, data.tab[find(flagBd)])
        val family = cp(0x1F468, 0x200D, 0x1F469, 0x200D, 0x1F467, 0x200D, 0x1F466)
        assertTrue("ZWJ family is one entry", find(family) >= 0)
        val heart = cp(0x2764, 0xFE0F)
        assertTrue("fully-qualified red heart keeps its variation selector", find(heart) >= 0)
        val keycap = cp(0x31, 0xFE0F, 0x20E3)
        assertTrue(find(keycap) >= 0)
    }

    @Test
    fun skinTones() {
        val thumbs = find(cp(0x1F44D))
        assertEquals(5, data.variants[thumbs].size)
        assertTrue(cp(0x1F44D, 0x1F3FD) in data.variants[thumbs])
        // two-person emoji: 25 combinations (same tone and mixed tones)
        val handshake = find(cp(0x1F91D))
        assertEquals(25, data.variants[handshake].size)
        assertTrue(cp(0x1FAF1, 0x1F3FB, 0x200D, 0x1FAF2, 0x1F3FC) in data.variants[handshake])
        val holdingHands = find(cp(0x1F9D1, 0x200D, 0x1F91D, 0x200D, 0x1F9D1))
        assertEquals(25, data.variants[holdingHands].size)
        // a ZWJ job with a tone
        val firefighter = find(cp(0x1F9D1, 0x200D, 0x1F692))
        assertEquals(5, data.variants[firefighter].size)
        // no tones on a face
        assertEquals(0, data.variants[find(cp(0x1F600))].size)
    }

    @Test
    fun searchEnglish() {
        assertEquals(cp(0x1F600), top("grinning face")[0])
        assertEquals(cp(0x1F1E7, 0x1F1E9), top("bangladesh")[0])
        assertEquals(cp(0x1F1E7, 0x1F1E9), top("FLAG bangla")[0]) // case-insensitive, word starts, several words
        assertEquals(cp(0x2764, 0xFE0F), top("red heart")[0])
        assertEquals(cp(0x1F4AF), top("100")[0])
        assertTrue(top("happy").size >= 5)
        assertEquals(0, top("xyzzyx").size)
        assertEquals(0, top("").size)
        assertEquals(0, top("  ").size)
        assertEquals(0, top("🙂").size) // no word in it
    }

    @Test
    fun searchBangla() {
        assertEquals(cp(0x1F1E7, 0x1F1E9), top("বাংলাদেশ")[0]) // the name of the flag
        assertTrue(cp(0x1F1E7, 0x1F1E9) in top("বাংলা", 20)) // start of a word
        assertTrue(top("হাসি").isNotEmpty())
        // decomposed nukta typed by another keyboard finds the same words as the precomposed form Avro commits
        assertEquals(top("সায়েন্স", 10), top("সা" + "য" + "়" + "েন্স", 10))
    }

    @Test
    fun wordsAreNormalized() {
        assertEquals(listOf("womens", "fine"), EmojiData.words("Women's  FINE"))
        assertEquals(listOf("a", "b", "100"), EmojiData.words("a-b, 100!"))
        assertEquals(listOf("য়"), EmojiData.words("য়")) // YA + NUKTA becomes the precomposed U+09DF that Avro commits
        assertEquals(emptyList<String>(), EmojiData.words("  ... "))
    }

    @Test
    fun searchResultsAreCappedAndBestFirst() {
        val out = IntArray(3)
        assertEquals(3, data.search("face", out))
        // a name word typed in full ranks before keywords: "face" is in many names
        assertTrue(data.emoji[out[0]].isNotEmpty())
        val all = IntArray(5000)
        val n = data.search("a", all)
        assertTrue(n > 100)
        for (k in 1 until n) assertNotEquals(all[k - 1], all[k])
    }

    @Test
    fun unsupportedEmojiAreRemoved() {
        val flags = data.flatStrings()
        assertEquals(data.flatCount, flags.size)
        val unsupported = BooleanArray(flags.size)
        val thumbs = cp(0x1F44D)
        for (i in flags.indices) {
            if (flags[i] == cp(0x1F600)) unsupported[i] = true // a whole entry
            if (flags[i] == cp(0x1F44D, 0x1F3FD)) unsupported[i] = true // one variant
        }
        val f = data.withoutUnsupported(unsupported)
        assertEquals(data.size - 1, f.size)
        assertEquals(-1, f.baseOf(cp(0x1F600)))
        assertEquals(4, f.variants[f.baseOf(thumbs)].size)
        assertEquals(-1, f.baseOf(cp(0x1F44D, 0x1F3FD)))
        for (t in 0 until EmojiTabs.COUNT) assertTrue(f.tabStart[t + 1] > f.tabStart[t])
        assertEquals(f.size, f.tabStart[EmojiTabs.COUNT])
        assertTrue(f.search("grinning face", IntArray(3)) > 0) // other grinning emoji are still found
        assertFalse(f.emoji.contains(cp(0x1F600)))
    }

    @Test
    fun parserRejectsOtherFormats() {
        try {
            EmojiData.parse(StringReader("#emoji\t2\tunicode 99\n").buffered())
            throw AssertionError("should have thrown")
        } catch (e: IllegalArgumentException) {
            // expected
        }
        val small = EmojiData.parse(StringReader("#emoji\t1\tunicode 17.0\n0\tA\ta|b\tx|y\t\n1\tB\tc|d\tz|\tB1 B2\n").buffered())
        assertEquals(2, small.size)
        assertEquals(listOf("B1", "B2"), small.variants[1].toList())
        assertEquals(0, small.variants[0].size)
    }
}
