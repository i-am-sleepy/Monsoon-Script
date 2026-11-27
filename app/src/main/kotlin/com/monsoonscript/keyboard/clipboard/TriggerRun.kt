package com.monsoonscript.keyboard.clipboard

/**
 * The run of characters typed since the last whitespace: what a text shortcut trigger (`;sig`) is matched against while typing (E3.7, D68).
 * The IME feeds it every character it commits, like the word tracker; it holds at most [ClipStore.MAX_TRIGGER] characters, because no trigger is longer
 * (a longer run can never match, so it is only remembered as "too long" until the next whitespace). Pure Kotlin.
 */
open class TriggerRun {
    private val run = StringBuilder()
    private var tooLong = false

    /** The characters typed since the last whitespace ("" when the run is empty or too long to be a trigger). */
    val text: String get() = if (tooLong) "" else run.toString()

    open fun clear() {
        run.setLength(0)
        tooLong = false
    }

    /** Feed one committed character; whitespace ends the run. */
    open fun append(c: Char) {
        if (c.isWhitespace()) {
            clear()
            return
        }
        if (tooLong) return
        run.append(c)
        if (run.length > ClipStore.MAX_TRIGGER) {
            tooLong = true
            run.setLength(0)
        }
    }

    /** Feed a whole committed string in order. */
    fun append(s: CharSequence) {
        for (c in s) append(c)
    }

    /**
     * One character was deleted. Returns false when the run cannot say what it is now (the run was empty, so the deleted character was whitespace or
     * from an earlier word, or the run was too long): the caller then rebuilds it with [reset] from the editor.
     */
    open fun backspace(): Boolean {
        if (tooLong || run.isEmpty()) return false
        run.setLength(run.length - 1)
        return true
    }

    /** Rebuild from the text before the cursor (field start, cursor moved, rebuilt after a selection delete): its last run without whitespace. */
    open fun reset(before: CharSequence?) {
        clear()
        if (before == null) return
        var start = before.length
        while (start > 0 && !before[start - 1].isWhitespace()) start--
        val n = before.length - start
        if (n > ClipStore.MAX_TRIGGER) {
            tooLong = true
            return
        }
        for (i in start until before.length) run.append(before[i])
    }
}
