package com.monsoonscript.keyboard.theme

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ThemeCodecTest {
    private fun c(hex: String) = ColorMath.parseHex(hex)!!
    private fun custom(id: String = "custom:1", name: String = "Mine") =
        ThemeSpec(id, name, c("#102030"), c("#304050"), c("#203040"), c("#A0B0C0"), c("#F0F0F0"), mapOf(Slot.KEY_PRESSED to c("#112233"), Slot.BUBBLE to c("#445566")))

    @Test fun aListRoundTrips() {
        val a = custom("custom:1", "Mine")
        val b = custom("custom:2", "Zwei \"quoted\" \\ and ümlaut বাংলা").copy(overrides = emptyMap())
        val back = ThemeCodec.decodeList(ThemeCodec.encodeList(listOf(a, b)))
        assertEquals(2, back.size)
        for ((x, y) in listOf(a to back[0], b to back[1])) {
            assertEquals(x.id, y.id); assertEquals(x.name, y.name)
            for (s in Slot.values()) assertEquals(s.name, x.color(s), y.color(s))
            assertEquals(x.overrides, y.overrides)
        }
    }

    @Test fun builtInThemesAreNeverSaved() {
        val text = ThemeCodec.encodeList(listOf(Themes.LIGHT, custom()))
        assertEquals(listOf("custom:1"), ThemeCodec.decodeList(text).map { it.id })
        assertFalse(text.contains("builtin"))
    }

    @Test fun damagedInputNeverThrows() {
        for (bad in listOf(null, "", "  ", "{", "[]", "null", "{\"themes\":5}", "{\"themes\":[1,\"x\",null]}", "not json at all", "{\"v\":1,\"themes\":[{\"id\":\"custom:1\"}]}")) {
            assertTrue(bad.toString(), ThemeCodec.decodeList(bad).isEmpty())
        }
    }

    @Test fun badEntriesAreSkippedAndGoodOnesKept() {
        val good = ThemeCodec.toMap(custom("custom:ok", "Ok"))
        val noColor = good + mapOf("id" to "custom:b1", "bg" to "nonsense")
        val builtin = good + mapOf("id" to "builtin:light")
        val noId = good - "id"
        val longId = good + mapOf("id" to "custom:" + "x".repeat(80))
        val text = com.monsoonscript.keyboard.clipboard.MiniJson.write(mapOf("v" to 1, "themes" to listOf(noColor, builtin, noId, longId, good, good)))
        val back = ThemeCodec.decodeList(text)
        assertEquals(listOf("custom:ok"), back.map { it.id }) // the duplicate is dropped too
    }

    @Test fun namesAreCleaned() {
        assertEquals("My theme", ThemeCodec.cleanName(null))
        assertEquals("My theme", ThemeCodec.cleanName("   "))
        assertEquals("a b c", ThemeCodec.cleanName("  a   b\n c "))
        assertEquals(ThemeSpec.MAX_NAME, ThemeCodec.cleanName("x".repeat(100)).length)
    }

    @Test fun aBadOverrideIsIgnoredNotFatal() {
        val m = ThemeCodec.toMap(custom()).toMutableMap()
        m["o"] = mapOf("keyPressed" to "zzz", "bubble" to "#445566", "bg" to "#000000", "unknown" to "#123456")
        val t = ThemeCodec.fromMap(m)!!
        assertFalse(t.isOverridden(Slot.KEY_PRESSED))
        assertEquals(c("#445566"), t.color(Slot.BUBBLE))
        assertEquals(c("#102030"), t.bg) // a main color can not be set through the overrides
    }

    @Test fun thereIsAHardLimit() {
        val many = (1..100).map { custom("custom:$it", "T$it") }
        assertEquals(ThemeCodec.MAX_THEMES, ThemeCodec.decodeList(ThemeCodec.encodeList(many)).size)
        assertNull(ThemeCodec.fromMap(mapOf("id" to "custom:")))
    }
}
