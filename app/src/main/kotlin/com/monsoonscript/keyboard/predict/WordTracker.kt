package com.monsoonscript.keyboard.predict

/**
 * Follows what is being typed so the predictor knows the word in progress ([prefix]) and the two words before it
 * ([w1] is the last finished word, [w2] the one before). Pure Kotlin; the IME feeds it every character it commits.
 *
 * A sentence end (. ! ? । or a new line) forgets the earlier words: the next word starts a new context.
 * Deleting past the start of the current word makes the history unreliable ([needsResync]); the IME then calls
 * [reset] with the text before the cursor, which it also does on field changes and when the cursor was moved elsewhere.
 * All words are returned in [TextNorm.key] form.
 */
class WordTracker {
    private val word = StringBuilder()
    private var prev1: String? = null
    private var prev2: String? = null

    /** True right after a sentence end or at the start of the field. */
    var atSentenceStart = true
        private set

    /** The history may be wrong (the user deleted into an earlier word): rebuild it with [reset]. */
    var needsResync = false
        private set

    /** What is typed of the current word exactly as it stands in the editor (capitals, apostrophes and a trailing ' kept). */
    val typed: String get() = word.toString()

    /** The current word in lookup form (a trailing apostrophe is not part of the prefix yet: "don'" looks up "don"). */
    val prefix: String get() = TextNorm.key(trimApostrophes(word))

    /** Called with (the word before, the word that just ended) every time a word ends; the IME uses it for learning. */
    var onWord: ((prev: String?, word: String) -> Unit)? = null

    val w1: String? get() = prev1
    val w2: String? get() = prev2

    /** Feed one committed character. */
    fun append(c: Char) {
        if (TextNorm.isWordChar(c)) {
            word.append(c)
            atSentenceStart = false
            return
        }
        if (TextNorm.isApostrophe(c) && word.isNotEmpty()) { // don't, dogs'
            word.append(c)
            return
        }
        finishWord()
        if (TextNorm.isSentenceEnd(c)) {
            prev1 = null
            prev2 = null
            atSentenceStart = true
        }
    }

    /** Feed a whole committed string (a tapped suggestion, pasted text): every character in order. */
    fun appendAll(s: CharSequence) {
        for (c in s) append(c)
    }

    fun backspace() {
        if (word.isNotEmpty()) {
            word.setLength(word.length - 1)
        } else {
            needsResync = true // the character deleted was a space or punctuation: an earlier word may be open again
        }
    }

    /** Forget the typed word (it was replaced by a suggestion or committed otherwise) without touching the history. */
    fun dropWord() {
        word.setLength(0)
    }

    /** Rebuild everything from the text before the cursor (the last few dozen characters are enough). */
    fun reset(textBeforeCursor: CharSequence?) {
        word.setLength(0)
        prev1 = null
        prev2 = null
        atSentenceStart = true
        needsResync = false
        if (textBeforeCursor == null) return
        var i = 0
        // Only text after the last sentence end counts as context, so start there.
        for (k in textBeforeCursor.length - 1 downTo 0) {
            if (TextNorm.isSentenceEnd(textBeforeCursor[k])) { i = k + 1; break }
        }
        if (i > 0) atSentenceStart = true
        while (i < textBeforeCursor.length) append(textBeforeCursor[i++])
    }

    /** The finished word the user is about to leave behind is [w1] after this call; returns it (null when nothing was typed). */
    fun finishWord(): String? {
        if (word.isEmpty()) return null
        val w = TextNorm.key(trimApostrophes(word))
        word.setLength(0)
        if (w.isEmpty()) return null
        onWord?.invoke(prev1, w)
        prev2 = prev1
        prev1 = w
        return w
    }

    private fun trimApostrophes(sb: CharSequence): String {
        var end = sb.length
        while (end > 0 && TextNorm.isApostrophe(sb[end - 1])) end--
        return sb.substring(0, end)
    }
}
