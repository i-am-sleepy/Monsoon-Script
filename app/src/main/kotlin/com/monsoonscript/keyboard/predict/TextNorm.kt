package com.monsoonscript.keyboard.predict

import java.util.Locale

/**
 * Text rules shared by the prediction code. Pure Kotlin.
 *
 * The packs and the user store use ONE form for every word, the "key": lowercase for Latin text, typographic apostrophe
 * turned into ', and Bengali nukta letters precomposed (য় = U+09DF, ড় = U+09DC, ঢ় = U+09DD) because that is the form
 * Avro commits (D42). Anything typed or read from the editor goes through [key] before it is looked up.
 */
object TextNorm {
    const val ZWNJ = '‌'
    const val ZWJ = '‍'

    /** Normal form used for lookups and storage. */
    fun key(word: String): String = precomposeBangla(lowerLatin(word.replace('’', '\'')))

    /** Decomposed nukta pairs (as in the WM data and in NFC text from other keyboards) become the precomposed letters Avro uses. */
    fun precomposeBangla(s: String): String {
        if (s.indexOf('়') < 0) return s // no nukta: nothing to do (the common case)
        return s.replace("ড়", "ড়").replace("ঢ়", "ঢ়").replace("য়", "য়")
    }

    private fun lowerLatin(s: String): String {
        for (c in s) if (c.isUpperCase()) return s.lowercase(Locale.ROOT)
        return s
    }

    /** Letters of any script, the combining marks that Bengali vowel signs and the hasanta are made of, and the joiners. */
    fun isWordChar(c: Char): Boolean {
        if (Character.isLetter(c) || c == ZWNJ || c == ZWJ) return true
        val t = Character.getType(c)
        return t == Character.NON_SPACING_MARK.toInt() || t == Character.COMBINING_SPACING_MARK.toInt() ||
            t == Character.ENCLOSING_MARK.toInt()
    }

    fun isApostrophe(c: Char): Boolean = c == '\'' || c == '’'

    /** Ends a sentence: the next word starts a new context (and gets a capital in English). */
    fun isSentenceEnd(c: Char): Boolean = c == '.' || c == '!' || c == '?' || c == '।' || c == '\n'

    /**
     * Give a suggestion the capitalization the user is using: ALL CAPS typed -> all caps; a typed capital first letter, or a
     * sentence start ([capitalizeStart]) -> capitalized; otherwise unchanged. Scripts without case are untouched.
     */
    fun matchCase(typedPrefix: String, word: String, capitalizeStart: Boolean): String {
        if (word.isEmpty()) return word
        if (typedPrefix.length >= 2 && typedPrefix.all { !it.isLetter() || it.isUpperCase() } && typedPrefix.any { it.isUpperCase() }) {
            return word.uppercase(Locale.ROOT)
        }
        val capital = (typedPrefix.isNotEmpty() && typedPrefix[0].isUpperCase()) || (typedPrefix.isEmpty() && capitalizeStart)
        if (capital && word[0].isLowerCase()) return word[0].uppercaseChar() + word.substring(1)
        return word
    }
}
