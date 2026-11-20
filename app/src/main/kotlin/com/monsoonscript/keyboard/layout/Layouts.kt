package com.monsoonscript.keyboard.layout

import com.monsoonscript.keyboard.lang.Language
import com.monsoonscript.keyboard.lang.avro.AvroLayoutTables

/**
 * Key layouts as plain data (no XML inflation). Rows normally have total weight 10, spacers shift keys sideways
 * (the Probhat rows 2 and 3 have 11 keys: the view divides the width by each row's own total).
 */
object Layouts {
    const val PAGE_LETTERS = 0
    const val PAGE_SYMBOLS = 1
    const val PAGE_SYMBOLS2 = 2
    const val PAGE_COUNT = 3

    private fun c(t: String, w: Float = 1f, hint: String? = null) = Key(Codes.CHAR, t, w, Kind.CHAR, hint)
    private fun spacer(w: Float) = Key(Codes.NONE, "", w, Kind.SPACER)
    private fun special(code: Int, label: String, w: Float) = Key(code, label, w, Kind.SPECIAL)
    private fun shift(w: Float) = Key(Codes.SHIFT, "", w, Kind.SPECIAL)
    private fun backspace(w: Float) = Key(Codes.BACKSPACE, "", w, Kind.SPECIAL)
    private fun enter(w: Float) = Key(Codes.ENTER, "", w, Kind.ACTION)
    private fun space(w: Float) = Key(Codes.SPACE, " ", w, Kind.SPACE, null, "English")
    private fun strip(code: Int, w: Float = 1f) = Key(code, "", w, Kind.STRIP)

    /** Strip cell width is `weight * 44dp`; the language key is a text pill, so it is wider. */
    val stripLeft: Array<Key> = arrayOf(strip(Codes.GLOBE), strip(Codes.LANG, 1.25f), strip(Codes.EMOJI), strip(Codes.CLIPBOARD))
    val stripRight: Array<Key> = arrayOf(strip(Codes.MIC))

    private val letters: Array<Array<Key>> = arrayOf(
        arrayOf(
            c("q", 1f, "1"), c("w", 1f, "2"), c("e", 1f, "3"), c("r", 1f, "4"), c("t", 1f, "5"),
            c("y", 1f, "6"), c("u", 1f, "7"), c("i", 1f, "8"), c("o", 1f, "9"), c("p", 1f, "0"),
        ),
        arrayOf(
            spacer(0.5f), c("a"), c("s"), c("d"), c("f"), c("g"), c("h"), c("j"), c("k"), c("l"), spacer(0.5f),
        ),
        arrayOf(
            shift(1.5f), c("z"), c("x"), c("c"), c("v"), c("b"), c("n"), c("m"), backspace(1.5f),
        ),
        arrayOf(
            special(Codes.SYMBOLS, "?123", 1.5f), c(","), space(5f), c("."), enter(1.5f),
        ),
    )

    private val symbols: Array<Array<Key>> = arrayOf(
        arrayOf(c("1"), c("2"), c("3"), c("4"), c("5"), c("6"), c("7"), c("8"), c("9"), c("0")),
        arrayOf(c("@"), c("#"), c("৳"), c("%"), c("&"), c("*"), c("-"), c("+"), c("("), c(")")),
        arrayOf(
            special(Codes.SYMBOLS2, "=\\<", 1.5f), c("!"), c("\""), c("'"), c(":"), c(";"), c("/"), c("?"), backspace(1.5f),
        ),
        arrayOf(
            special(Codes.LETTERS, "ABC", 1.5f), c(","), space(5f), c("."), enter(1.5f),
        ),
    )

    private val symbols2: Array<Array<Key>> = arrayOf(
        arrayOf(c("~"), c("`"), c("|"), c("•"), c("√"), c("π"), c("÷"), c("×"), c("{"), c("}")),
        arrayOf(
            Key(Codes.TAB, "", 1f, Kind.SPECIAL, null, "Tab"),
            c("£"), c("$"), c("¥"), c("^"), c("°"), c("_"), c("="), c("["), c("]"),
        ),
        arrayOf(
            special(Codes.SYMBOLS, "?123", 1.5f), c("®"), c("©"), c("™"), c("\\"), c("<"), c(">"), c("…"), backspace(1.5f),
        ),
        arrayOf(
            special(Codes.LETTERS, "ABC", 1.5f), c(","), space(5f), c("."), enter(1.5f),
        ),
    )

    val pages: Array<Array<Array<Key>>> = arrayOf(letters, symbols, symbols2)

    /** The optional number row (G1.5, a setting): KeyboardView puts it above the rows of the letters page. */
    val numberRow: Array<Key> = arrayOf(c("1"), c("2"), c("3"), c("4"), c("5"), c("6"), c("7"), c("8"), c("9"), c("0"))

    // ---- fixed Bangla layouts (L4, D58-D60): the PC key tables are in AvroLayoutTables, this is how they sit on a phone

    /**
     * A key of a fixed layout. [pc] names the PC key whose Normal and Shift layers it shows and types. A long press types the
     * AltGr layer (Shift on: Shift+AltGr), drawn as the corner hint (D59). [digit] makes the key a top-row key that keeps the
     * digit hint of D14 instead; [hint] / [shiftHint] replace the AltGr characters with ones of our own.
     */
    private fun fk(
        t: FixedLayout, pc: String, w: Float = 1f, digit: String? = null, hint: String? = null, shiftHint: String? = null,
    ): Key {
        val normal = t.text(pc, FixedLayout.NORMAL)
        val shift = t.text(pc, FixedLayout.SHIFT).ifEmpty { null }
        val altGr = t.text(pc, FixedLayout.ALTGR).ifEmpty { null }
        val altGrShift = t.text(pc, FixedLayout.SHIFT_ALTGR).ifEmpty { null }
        val h: String?
        val sh: String?
        when {
            digit != null -> { h = digit; sh = null }
            hint != null -> { h = hint; sh = shiftHint }
            else -> { h = altGr; sh = shiftHint ?: altGrShift }
        }
        return Key(Codes.CHAR, normal, w, Kind.CHAR, h, normal, shift, sh)
    }

    private const val TA_KHANDA = "ৎ" // ৎ
    private const val NYA = "ঞ" // ঞ

    /** D58 Probhat: 11 keys in rows 2 and 3, ে ো ্ on keys of their own, ৃ and ঁ on Shift of the comma and ।, rare signs on long press. */
    private val probhatLetters: Array<Array<Key>> by lazy {
        val t = AvroLayoutTables.PROBHAT
        arrayOf(
            arrayOf(
                fk(t, "Q", digit = "1"), fk(t, "W", digit = "2"), fk(t, "E", digit = "3"), fk(t, "R", digit = "4"), fk(t, "T", digit = "5"),
                fk(t, "Y", digit = "6"), fk(t, "U", digit = "7"), fk(t, "I", digit = "8"), fk(t, "O", digit = "9"), fk(t, "P", digit = "0"),
            ),
            arrayOf(
                fk(t, "A"), fk(t, "S"), fk(t, "D"), fk(t, "F", hint = TA_KHANDA), fk(t, "G"), fk(t, "H"), fk(t, "J"), fk(t, "K"), fk(t, "L"),
                fk(t, "OEM4"), fk(t, "OEM6"),
            ),
            arrayOf(
                shift(1.5f), fk(t, "Z"), fk(t, "X"), fk(t, "C"), fk(t, "V"), fk(t, "B"), fk(t, "N", hint = NYA), fk(t, "M"),
                fk(t, "OEM2", hint = Key.ZWNJ, shiftHint = Key.ZWJ), backspace(1.5f),
            ),
            arrayOf(
                special(Codes.SYMBOLS, "?123", 1.5f), fk(t, "COMMA"), space(5f), fk(t, "PERIOD"), enter(1.5f),
            ),
        )
    }

    /** D59 National (Jatiya): the plain 10/9/7 grid; the independent vowels sit on the AltGr layer (long press). */
    private val nationalLetters: Array<Array<Key>> by lazy {
        val t = AvroLayoutTables.NATIONAL
        arrayOf(
            arrayOf(
                fk(t, "Q", digit = "1"), fk(t, "W", digit = "2"), fk(t, "E", digit = "3"), fk(t, "R", digit = "4"), fk(t, "T", digit = "5"),
                fk(t, "Y", digit = "6"), fk(t, "U", digit = "7"), fk(t, "I", digit = "8"), fk(t, "O", digit = "9"), fk(t, "P", digit = "0"),
            ),
            arrayOf(
                spacer(0.5f), fk(t, "A"), fk(t, "S"), fk(t, "D"), fk(t, "F"), fk(t, "G", hint = Key.ZWNJ, shiftHint = Key.ZWJ),
                fk(t, "H"), fk(t, "J"), fk(t, "K", hint = TA_KHANDA), fk(t, "L"), spacer(0.5f),
            ),
            arrayOf(
                shift(1.5f), fk(t, "Z"), fk(t, "X"), fk(t, "C"), fk(t, "V"), fk(t, "B"), fk(t, "N"), fk(t, "M"), backspace(1.5f),
            ),
            arrayOf(
                special(Codes.SYMBOLS, "?123", 1.5f), c(","), space(5f), c("."), enter(1.5f),
            ),
        )
    }

    private val probhatPages: Array<Array<Array<Key>>> by lazy { arrayOf(probhatLetters, symbols, symbols2) }
    private val nationalPages: Array<Array<Array<Key>>> by lazy { arrayOf(nationalLetters, symbols, symbols2) }

    /** The pages for [language]: the letters page follows the layout, the two symbol pages are shared. The same array comes back every time. */
    fun pages(language: Language): Array<Array<Array<Key>>> = when (language) {
        Language.PROBHAT -> probhatPages
        Language.NATIONAL -> nationalPages
        else -> pages
    }
}
