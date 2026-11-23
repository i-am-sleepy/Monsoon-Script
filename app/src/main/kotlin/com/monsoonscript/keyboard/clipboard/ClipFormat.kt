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

    /** How many calendar days (in [zone]) [then] lies before [now]: 0 = today, 1 = yesterday; negative for a clock that moved back. */
    fun daysAgo(then: Long, now: Long, zone: TimeZone = TimeZone.getDefault()): Long = dayNumber(now, zone) - dayNumber(then, zone)

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

    /**
     * One short line of a text for the keyboard strip (a shortcut candidate): the first line with something on it, runs of white space turned into single
     * spaces, at most [max] characters (never cut inside a surrogate pair). The strip itself shrinks and cuts what still does not fit.
     */
    fun stripLabel(text: String, max: Int = 40): String {
        var lineStart = 0
        while (lineStart <= text.length) {
            var lineEnd = text.indexOf('\n', lineStart)
            if (lineEnd < 0) lineEnd = text.length
            val line = text.substring(lineStart, lineEnd)
            if (line.isNotBlank()) {
                val sb = StringBuilder()
                var pendingSpace = false
                for (ch in line) {
                    if (ch.isWhitespace()) {
                        pendingSpace = sb.isNotEmpty()
                        continue
                    }
                    if (pendingSpace) {
                        sb.append(' ')
                        pendingSpace = false
                    }
                    sb.append(ch)
                }
                if (sb.length <= max) return sb.toString()
                var end = max
                if (Character.isHighSurrogate(sb[end - 1])) end--
                return sb.substring(0, end)
            }
            lineStart = lineEnd + 1
        }
        return ""
    }

    /** A size for people: "340 KB", "8.4 MB" (one decimal from 1 MB up; no locale-dependent formatting on purpose). */
    fun bytes(n: Long): String {
        if (n < 1024L) return "$n B"
        if (n < 1024L * 1024L) return "${(n + 512L) / 1024L} KB"
        val tenths = (n * 10L + 524_288L) / 1_048_576L
        return "${tenths / 10L}.${tenths % 10L} MB"
    }

    /** The picture clips of a selection, in the order given (they are shared together with [exportText], D77). */
    fun imageClips(clips: List<ClipItem>): List<ClipItem> {
        val out = ArrayList<ClipItem>()
        for (c in clips) if (c.isImage && c.image != null) out.add(c)
        return out
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
