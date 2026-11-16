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
     * True for characters that belong to the word: letters, plus the backtick (separator) and `^` (chandrabindu)
     * once a word has started. Everything else (space, digits, punctuation) ends the word.
     */
    fun accepts(ch: Char): Boolean =
        ch in 'a'..'z' || ch in 'A'..'Z' || ((ch == '`' || ch == '^') && raw.isNotEmpty())

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

    /** The word as Bangla (with Avro autocorrect), or "" when nothing is typed. */
    fun text(): String = if (raw.isEmpty()) "" else converter.convert(raw.toString())

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
