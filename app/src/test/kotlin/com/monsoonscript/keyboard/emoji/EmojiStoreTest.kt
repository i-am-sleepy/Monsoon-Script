package com.monsoonscript.keyboard.emoji

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class EmojiStoreTest {

    private class MapStore : TextStore {
        val map = HashMap<String, String>()
        var writes = 0
        override fun get(key: String): String? = map[key]
        override fun put(key: String, value: String) {
            map[key] = value
            writes++
        }
    }

    @Test
    fun recentsAreNewestFirstWithoutDuplicates() {
        val disk = MapStore()
        val s = EmojiStore(disk, 3)
        s.recordUse("a")
        s.recordUse("b")
        s.recordUse("c")
        assertEquals(listOf("c", "b", "a"), s.recent)
        s.recordUse("a") // moves to the front
        assertEquals(listOf("a", "c", "b"), s.recent)
        s.recordUse("d") // the oldest falls off
        assertEquals(listOf("d", "a", "c"), s.recent)
        val writes = disk.writes
        s.recordUse("d") // already first: nothing to save
        assertEquals(writes, disk.writes)
    }

    @Test
    fun recentsSurviveARestart() {
        val disk = MapStore()
        val a = EmojiStore(disk)
        val family = "👨‍👩‍👧" // a ZWJ sequence stays one entry
        a.recordUse("😀")
        a.recordUse(family)
        val b = EmojiStore(disk)
        assertEquals(listOf(family, "😀"), b.recent)
        b.clearRecent()
        assertEquals(emptyList<String>(), EmojiStore(disk).recent)
    }

    @Test
    fun skinTonesAreRememberedPerEmoji() {
        val disk = MapStore()
        val s = EmojiStore(disk)
        val thumbs = "👍"
        val medium = "👍🏽"
        assertNull(s.toneOf(thumbs))
        s.rememberTone(thumbs, medium)
        s.rememberTone("👋", "👋🏿")
        assertEquals(medium, EmojiStore(disk).toneOf(thumbs))
        s.rememberTone(thumbs, null) // back to the plain emoji
        assertNull(EmojiStore(disk).toneOf(thumbs))
        assertEquals("👋🏿", EmojiStore(disk).toneOf("👋"))
        s.rememberTone("👋", "👋") // choosing the base itself also means plain
        assertNull(EmojiStore(disk).toneOf("👋"))
    }

    @Test
    fun damagedSavedDataIsIgnored() {
        val disk = MapStore()
        disk.map[EmojiStore.KEY_RECENT] = "a\n\n a \nb\nb"
        disk.map[EmojiStore.KEY_TONES] = "no tab here\n\tstartsWithTab\nendsWithTab\t\nok\tfine"
        val s = EmojiStore(disk)
        assertEquals(listOf("a", " a ", "b"), s.recent)
        assertEquals("fine", s.toneOf("ok"))
        assertNull(s.toneOf("endsWithTab"))
        assertNull(s.toneOf(""))
    }
}
