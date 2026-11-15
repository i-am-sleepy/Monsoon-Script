package com.monsoonscript.keyboard.layout

/** Key layouts as plain data (no XML inflation). Rows have total weight 10, spacers shift keys sideways. */
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
    private fun strip(code: Int) = Key(code, "", 1f, Kind.STRIP)

    val stripLeft: Array<Key> = arrayOf(strip(Codes.GLOBE), strip(Codes.EMOJI), strip(Codes.CLIPBOARD))
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
}
