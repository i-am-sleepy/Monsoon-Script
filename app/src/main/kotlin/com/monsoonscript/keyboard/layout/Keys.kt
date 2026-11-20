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
    const val LANG = -13
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
 *
 * Shift: a Latin letter shows and types its capital; any key can say what Shift types with [shiftText] (the fixed Bangla layouts
 * put a second letter there). [shiftHint] is what a long press types while Shift is on (null = the same as [hint]). Combining
 * signs and invisible characters cannot be drawn alone, so [face] and the `hintDrawn*` strings are what is painted: a no-break space
 * under a vowel sign (so no dotted circle is drawn), "ZWNJ" / "ZWJ" for the joiners (or the explicit [hintLabel] / [shiftHintLabel]).
 */
class Key(
    val code: Int,
    val text: String = "",
    val weight: Float = 1f,
    val kind: Int = Kind.CHAR,
    val hint: String? = null,
    val label: String = text,
    shiftText: String? = null,
    val shiftHint: String? = null,
    hintLabel: String? = null,
    shiftHintLabel: String? = null,
    /** A hint that exists only in the Bangla layouts (L6: rare signs on the ?123 pages); a key with a normal [hint] never needs it. */
    val bnHint: String? = null,
) {
    val upper: String = text.uppercase()
    val isLetter: Boolean = kind == Kind.CHAR && text.length == 1 && text[0].isLetter()

    /** 0..9 when this is a plain digit key (the ?123 digits and the number row), else -1. The view swaps it for a Bangla digit (T11). */
    val digit: Int = if (kind == Kind.CHAR && text.length == 1 && text[0] in '0'..'9') text[0] - '0' else -1

    /** What the key shows and types while Shift is on. */
    val shifted: String = shiftText ?: if (isLetter) upper else text

    /** A long press types this while Shift is on. */
    val hintShifted: String? = shiftHint ?: hint

    /** Painted on the key face (normal / Shift on). */
    val face: String = drawable(text)
    val faceShifted: String = drawable(shifted)

    /** Painted in the key's corner (normal / Shift on). */
    val hintDrawn: String? = hintLabel ?: hint?.let { drawable(it) }
    val hintDrawnShifted: String? = if (shiftHint != null) (shiftHintLabel ?: drawable(shiftHint)) else hintDrawn

    /** Painted in the corner for [bnHint]. */
    val bnHintDrawn: String? = bnHint?.let { drawable(it) }

    companion object {
        const val ZWNJ = "‌"
        const val ZWJ = "‍"

        /** The no-break space: a combining sign standing on it is drawn alone, and the text shaper adds no dotted circle (L5). */
        const val NBSP = "\u00A0"

        /** The Bangla digits ০..৯ (T11), shared constants so drawing and typing allocate nothing. */
        val BN_DIGITS: Array<String> = arrayOf("০", "১", "২", "৩", "৪", "৫", "৬", "৭", "৮", "৯")

        /** [s] as it can be painted on its own: joiners get a name, a combining sign stands on a no-break space (no dotted circle), everything else is unchanged. */
        fun drawable(s: String): String {
            if (s.isEmpty()) return s
            if (s == ZWNJ) return "ZWNJ"
            if (s == ZWJ) return "ZWJ"
            val t = Character.getType(s[0])
            return if (t == Character.NON_SPACING_MARK.toInt() || t == Character.COMBINING_SPACING_MARK.toInt()) NBSP + s else s
        }
    }
}
