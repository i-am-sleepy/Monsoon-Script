package com.monsoonscript.keyboard.calc

/**
 * The last characters typed on the current line, kept by the keyboard so the calculator never has to ask the app for the text before the cursor on every key
 * (that is a blocking call into the app, see the other trackers). It is fed with every committed character like the shortcut tracker; a new line, a new field or a
 * moved cursor clear or rebuild it. At most [MAX] characters are kept (the engine looks at [CalcEngine.WINDOW]). Pure Kotlin.
 */
class CalcRun {
    private val buf = StringBuilder()

    /** The characters themselves (do not keep or change it). */
    val chars: CharSequence get() = buf

    val length: Int get() = buf.length

    fun clear() {
        buf.setLength(0)
    }

    /** One committed character; a line break starts the line again. */
    fun append(c: Char) {
        if (c == '\n' || c == '\r') {
            buf.setLength(0)
            return
        }
        buf.append(c)
        if (buf.length > MAX) buf.delete(0, buf.length - MAX)
    }

    fun append(s: CharSequence) {
        for (c in s) append(c)
    }

    /** One character was deleted; false when nothing is left to say what it was (the caller then rebuilds with [reset]). */
    fun backspace(): Boolean {
        if (buf.isEmpty()) return false
        buf.setLength(buf.length - 1)
        return true
    }

    /** Rebuild from the text before the cursor: its last line, at most [MAX] characters. */
    fun reset(before: CharSequence?) {
        buf.setLength(0)
        if (before == null) return
        var start = before.length
        while (start > 0 && before[start - 1] != '\n' && before.length - start < MAX) start--
        for (i in start until before.length) buf.append(before[i])
    }

    companion object {
        const val MAX = 96
    }
}
