package com.monsoonscript.keyboard.clipboard

import java.util.Calendar
import java.util.TimeZone

/** Small text helpers for the clipboard screens. Pure Kotlin (tested on the JVM). */
object ClipFormat {
    private val MONTHS = arrayOf("Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec")

    /** "Just now", "5 min ago", "3 h ago" (same day), "Yesterday", "3 days ago" (under a week), then "12 Nov" or "12 Nov 2025". */
    fun ago(then: Long, now: Long, zone: TimeZone = TimeZone.getDefault()): String {
        val diff = now - then
        if (diff < 60_000L) return "Just now" // also a clock that moved back
        val minutes = diff / 60_000L
        if (minutes < 60L) return "$minutes min ago"
        val days = dayNumber(now, zone) - dayNumber(then, zone)
        if (days <= 0L) return "${minutes / 60L} h ago"
        if (days == 1L) return "Yesterday"
        if (days < 7L) return "$days days ago"
        val c = Calendar.getInstance(zone).apply { timeInMillis = then }
        val sameYear = c.get(Calendar.YEAR) == Calendar.getInstance(zone).apply { timeInMillis = now }.get(Calendar.YEAR)
        val base = "${c.get(Calendar.DAY_OF_MONTH)} ${MONTHS[c.get(Calendar.MONTH)]}"
        return if (sameYear) base else "$base ${c.get(Calendar.YEAR)}"
    }

    /** The calendar day a moment falls on, counted from the epoch in [zone] (so "yesterday" follows the phone's midnight, not 24 hours). */
    private fun dayNumber(t: Long, zone: TimeZone): Long = Math.floorDiv(t + zone.getOffset(t), 86_400_000L)

    /**
     * The start of a text for a card: leading blank space dropped, at most [max] characters, never cut inside a surrogate pair (emoji).
     * The card itself wraps and ellipsizes it to its line count.
     */
    fun previewSource(text: String, max: Int = 300): String {
        var start = 0
        while (start < text.length && text[start].isWhitespace()) start++
        if (text.length - start <= max) return text.substring(start)
        var end = start + max
        if (Character.isHighSurrogate(text[end - 1])) end--
        return text.substring(start, end)
    }

    /** Selected text clips as one text: clips separated by a blank line, in the order given. Image clips are left out. */
    fun exportText(clips: List<ClipItem>): String {
        val sb = StringBuilder()
        for (c in clips) {
            if (c.kind != ClipKind.TEXT) continue
            if (sb.isNotEmpty()) sb.append("\n\n")
            sb.append(c.text)
        }
        return sb.toString()
    }
}
