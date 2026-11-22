package com.monsoonscript.keyboard.clipboard

import java.time.ZoneOffset
import java.time.ZonedDateTime
import java.util.TimeZone
import org.junit.Assert.assertEquals
import org.junit.Test

class ClipFormatTest {

    private val utc = TimeZone.getTimeZone("UTC")
    private fun at(y: Int, mo: Int, d: Int, h: Int, mi: Int): Long = ZonedDateTime.of(y, mo, d, h, mi, 0, 0, ZoneOffset.UTC).toInstant().toEpochMilli()
    private val now = at(2026, 11, 21, 10, 0)

    @Test
    fun recentTimesCountMinutesAndHours() {
        assertEquals("Just now", ClipFormat.ago(now - 30_000, now, utc))
        assertEquals("Just now", ClipFormat.ago(now + 5_000, now, utc)) // the clock moved back
        assertEquals("1 min ago", ClipFormat.ago(now - 60_000, now, utc))
        assertEquals("59 min ago", ClipFormat.ago(at(2026, 11, 21, 9, 1), now, utc))
        assertEquals("3 h ago", ClipFormat.ago(at(2026, 11, 21, 7, 0), now, utc))
    }

    @Test
    fun yesterdayFollowsTheCalendarNotTwentyFourHours() {
        assertEquals("Yesterday", ClipFormat.ago(at(2026, 11, 20, 23, 30), now, utc)) // only 10.5 hours ago
        assertEquals("Yesterday", ClipFormat.ago(at(2026, 11, 20, 1, 0), now, utc))
        assertEquals("2 days ago", ClipFormat.ago(at(2026, 11, 19, 23, 0), now, utc))
        assertEquals("6 days ago", ClipFormat.ago(at(2026, 11, 15, 12, 0), now, utc))
    }

    @Test
    fun olderTimesShowTheDate() {
        assertEquals("14 Nov", ClipFormat.ago(at(2026, 11, 14, 12, 0), now, utc))
        assertEquals("3 Jan", ClipFormat.ago(at(2026, 1, 3, 12, 0), now, utc))
        assertEquals("31 Dec 2025", ClipFormat.ago(at(2025, 12, 31, 12, 0), now, utc))
    }

    @Test
    fun theTimeZoneDecidesWhereMidnightIs() {
        val dhaka = TimeZone.getTimeZone("Asia/Dhaka") // UTC+6
        val then = at(2026, 11, 20, 20, 0) // 02:00 on the 21st in Dhaka: the same day as "now" (16:00) there, but yesterday in UTC
        assertEquals("14 h ago", ClipFormat.ago(then, now, dhaka))
        assertEquals("Yesterday", ClipFormat.ago(then, now, utc))
    }

    @Test
    fun previewDropsLeadingSpaceAndCutsAtTheLimit() {
        assertEquals("hello", ClipFormat.previewSource("  \n\t hello"))
        assertEquals("abc", ClipFormat.previewSource("abcdef", 3))
        assertEquals("", ClipFormat.previewSource("   "))
        val emoji = "a😀b" // 😀 is two chars
        assertEquals("a", ClipFormat.previewSource(emoji, 2)) // never cut between the two halves
        assertEquals("a😀", ClipFormat.previewSource(emoji, 3))
    }

    @Test
    fun exportJoinsTextClipsWithABlankLineAndSkipsImages() {
        val a = ClipItem(1, ClipKind.TEXT, "one", null, null, false, 0, 0, 0)
        val img = ClipItem(2, ClipKind.IMAGE, "", "f.png", "image/png", false, 0, 0, 0)
        val b = ClipItem(3, ClipKind.TEXT, "two\nlines", null, null, false, 0, 0, 0)
        assertEquals("one\n\ntwo\nlines", ClipFormat.exportText(listOf(a, img, b)))
        assertEquals("", ClipFormat.exportText(listOf(img)))
    }

    @Test
    fun sizesAreShortAndReadable() {
        assertEquals("0 B", ClipFormat.bytes(0))
        assertEquals("1023 B", ClipFormat.bytes(1023))
        assertEquals("1 KB", ClipFormat.bytes(1024))
        assertEquals("340 KB", ClipFormat.bytes(340L * 1024))
        assertEquals("1.0 MB", ClipFormat.bytes(1024L * 1024))
        assertEquals("8.4 MB", ClipFormat.bytes((8.4 * 1024 * 1024).toLong()))
        assertEquals("100.0 MB", ClipFormat.bytes(100L * 1024 * 1024))
    }
}
