package com.monsoonscript.keyboard.layout

/** Special key codes. [CHAR] means "commit [Key.text]". */
object Codes {
    const val NONE = 0
    const val CHAR = 1
    const val SHIFT = -1
    const val BACKSPACE = -2
    const val ENTER = -3
    const val SYMBOLS = -4
    const val LETTERS = -5
    const val SYMBOLS2 = -6
    const val TAB = -7
    const val SPACE = -8
    const val GLOBE = -9
    const val EMOJI = -10
    const val CLIPBOARD = -11
    const val MIC = -12
}

/** How a key is drawn. */
object Kind {
    const val CHAR = 0
    const val SPECIAL = 1
    const val ACTION = 2
    const val SPACE = 3
    const val SPACER = 4
    const val STRIP = 5
}

/**
 * One key. [text] is what gets committed, [label] what is drawn (defaults to text),
 * [hint] is the small corner label that a long press commits instead.
 */
class Key(
    val code: Int,
    val text: String = "",
    val weight: Float = 1f,
    val kind: Int = Kind.CHAR,
    val hint: String? = null,
    val label: String = text,
) {
    val upper: String = text.uppercase()
    val isLetter: Boolean = kind == Kind.CHAR && text.length == 1 && text[0].isLetter()
}
