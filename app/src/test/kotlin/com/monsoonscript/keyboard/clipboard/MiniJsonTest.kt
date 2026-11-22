package com.monsoonscript.keyboard.clipboard

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class MiniJsonTest {

    private fun bad(text: String) {
        try {
            MiniJson.parse(text)
            fail("should not parse: $text")
        } catch (e: IllegalArgumentException) {
            // expected
        }
    }

    @Test
    fun writesAndReadsEveryKindOfValue() {
        val v = linkedMapOf<String, Any?>(
            "s" to "text",
            "n" to 42L,
            "neg" to -7L,
            "d" to 1.5,
            "t" to true,
            "f" to false,
            "z" to null,
            "list" to listOf(1L, "two", listOf<Any?>(), linkedMapOf<String, Any?>()),
        )
        val back = MiniJson.parse(MiniJson.write(v)) as Map<*, *>
        assertEquals(v.toMap(), back.toMap())
    }

    @Test
    fun stringsKeepEveryCharacter() {
        val tricky = "quote \" backslash \\ slash / tab \t newline \n return \r nul \u0000 bell \u0007 unit \u001f é বাংলা 😀  "
        val json = MiniJson.write(tricky)
        assertEquals(tricky, MiniJson.parse(json))
        assertTrue(json.none { it < ' ' }) // no raw control characters in the file
    }

    @Test
    fun readsEscapesAndSurrogatePairs() {
        assertEquals("é😀\n/", MiniJson.parse("\"\\u00e9\\ud83d\\ude00\\n\\/\""))
        assertEquals("a", MiniJson.parse("  \"a\"\n "))
    }

    @Test
    fun numbersAreLongOrDouble() {
        assertEquals(12L, MiniJson.parse("12"))
        assertEquals(-3L, MiniJson.parse("-3"))
        assertEquals(1.25, MiniJson.parse("1.25"))
        assertEquals(1000.0, MiniJson.parse("1e3"))
        assertEquals(1_700_000_000_000L, MiniJson.parse("1700000000000"))
    }

    @Test
    fun literalsAndEmptyContainers() {
        assertEquals(true, MiniJson.parse("true"))
        assertNull(MiniJson.parse("null"))
        assertEquals(emptyList<Any?>(), MiniJson.parse("[]"))
        assertEquals(emptyMap<String, Any?>(), MiniJson.parse("{ }"))
    }

    @Test
    fun brokenTextIsRefusedNotGuessed() {
        bad("")
        bad("{")
        bad("[1,]")
        bad("{\"a\":1,}")
        bad("{\"a\" 1}")
        bad("\"unterminated")
        bad("\"bad \\x escape\"")
        bad("\"bad \\u12 escape\"")
        bad("\"raw\ncontrol\"")
        bad("tru")
        bad("1 2")
        bad("-")
        bad("1.")
        bad("{1:2}")
    }

    @Test
    fun veryDeepNestingIsRefusedInsteadOfOverflowingTheStack() {
        bad("[".repeat(100) + "]".repeat(100))
        MiniJson.parse("[".repeat(30) + "]".repeat(30)) // within the limit
    }
}
