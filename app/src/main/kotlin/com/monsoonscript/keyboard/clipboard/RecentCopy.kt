package com.monsoonscript.keyboard.clipboard

/**
 * The text that was copied a moment ago, for the chip in the suggestion strip (E3.10, D74). It is offered for [WINDOW_MS] after the copy, until the owner taps it,
 * starts typing, or copies something else. Memory only; the clock is handed in so the rule is unit tested. Pure Kotlin.
 */
class RecentCopy(private val clock: () -> Long) {
    private var text: String? = null
    private var at = 0L

    /** A text was copied (and recorded: the history's own privacy rules were applied before this is called). */
    fun note(copied: String) {
        if (copied.isBlank()) return
        text = copied
        at = clock()
    }

    /** The copied text while it is fresh, else null. */
    fun current(): String? {
        val t = text ?: return null
        if (clock() - at >= WINDOW_MS) {
            text = null
            return null
        }
        return t
    }

    /** Milliseconds the chip may still be shown (0 when there is none). */
    fun remainingMs(): Long = if (current() == null) 0L else WINDOW_MS - (clock() - at)

    /** The chip was used or is not wanted any more. */
    fun dismiss() {
        text = null
    }

    companion object {
        const val WINDOW_MS = 60_000L
    }
}
