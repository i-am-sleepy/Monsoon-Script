package com.monsoonscript.keyboard.lang

import com.monsoonscript.keyboard.lang.avro.AvroPhoneticConverter

/**
 * The word being typed in Avro Phonetic: keeps the raw Latin letters and shows them as live Bangla
 * (the IME displays [text] as the editor's composing text). Pure Kotlin, main thread only.
 */
class PhoneticComposer(private val converter: AvroPhoneticConverter) {

    private val raw = StringBuilder(24)

    val isComposing: Boolean get() = raw.isNotEmpty()

    /**
     * True for characters that belong to the word: letters, plus the backtick (separator), `^` (chandrabindu) and
     * `:` (visarga, see [text]) once a word has started. Everything else (space, digits, punctuation) ends the word.
     */
    fun accepts(ch: Char): Boolean =
        ch in 'a'..'z' || ch in 'A'..'Z' || ((ch == '`' || ch == '^' || ch == ':') && raw.isNotEmpty())

    fun append(ch: Char) {
        raw.append(ch)
    }

    fun deleteLast() {
        if (raw.isNotEmpty()) raw.setLength(raw.length - 1)
    }

    fun clear() {
        raw.setLength(0)
    }

    /** The Latin letters typed so far. */
    fun rawText(): String = raw.toString()

    /**
     * The word as Bangla (with Avro autocorrect), or "" when nothing is typed.
     *
     * Smart colon (D24): the engine makes every `:` a visarga (ঃ), like Avro on Windows. Here a colon that is still the
     * last character stays a plain `:` (a colon after a word, or a time like "ami:"), and becomes a visarga as soon as a
     * letter follows it ("du:" shows দু: and "du:kho" shows দুঃখ). A backtick after the colon also keeps it plain.
     */
    fun text(): String {
        val n = raw.length
        if (n == 0) return ""
        if (raw[n - 1] == ':') return converter.convert(raw.substring(0, n - 1)) + ":"
        return converter.convert(raw.toString())
    }

    /** True when the word ends with a colon that is still a plain `:` (nothing for the dictionary to look up). */
    val endsWithColon: Boolean get() = raw.isNotEmpty() && raw[raw.length - 1] == ':'

    companion object {
        /** An editor update older than this (relative to our last edit) may still be an echo of an earlier state. */
        const val STALE_MS = 250L

        /**
         * Decide from an `onUpdateSelection` callback whether the user moved the cursor away from the word being
         * composed (so the IME must forget its buffer). While the editor still reports a composing region, the cursor
         * must sit at its end. Without a region it is either the app having dropped it or a late echo from before our
         * last edit; only treat it as a move when our last edit is not recent.
         */
        fun externalMove(selStart: Int, selEnd: Int, candStart: Int, candEnd: Int, msSinceOurLastEdit: Long): Boolean =
            if (candStart >= 0 && candEnd >= 0) selStart != candEnd || selEnd != candEnd
            else msSinceOurLastEdit > STALE_MS
    }
}
