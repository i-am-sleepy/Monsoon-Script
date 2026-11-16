/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Derived from Avro Keyboard (OmicronLab), clsEnglishToBangla.pas (TEnglishToBangla).
 * Kotlin port for Monsoon Script; see third_party/avro-reference/NOTICE.md.
 */

package com.monsoonscript.keyboard.lang.avro

/**
 * Avro Phonetic: converts a roman-letter word ("ami") into Bangla ("আমি").
 *
 * Pure Kotlin, no Android classes. NOT thread-safe (it keeps scratch state between calls so that typing does not
 * allocate); use one instance from the IME's main thread. [pos] is 1-based on purpose, to stay line-for-line
 * comparable with the Pascal original.
 *
 * @param autoCorrect the Avro autocorrect dictionary (see [AvroAutoCorrect]); may be empty.
 */
class AvroPhoneticConverter(private val autoCorrect: Map<String, String> = emptyMap()) {

    /** Use the autocorrect dictionary (Avro default: on). */
    var autoCorrectEnabled: Boolean = true

    /** Capital J gives ja-nukta (য়-like ज़). Avro's registry setting `EnableJoNukta`, default off. */
    var joNukta: Boolean = false

    private var text: String = ""
    private var ln: Int = 0
    private var pos: Int = 1
    private val rs = StringBuilder(64)

    // ---------------------------------------------------------------------------------------------- public API

    /** Convert one typed word (may include leading/trailing punctuation) to Bangla. */
    fun convert(english: String): String {
        if (english.isEmpty()) return ""

        // "As is" support: an autocorrect entry that maps a word to itself keeps it unconverted.
        if (autoCorrectEnabled) {
            val d = autoCorrect[english]
            if (d != null && d == english) return english
        }

        val eng = correctCase(english)
        if (!autoCorrectEnabled) return myConvert(eng)

        val whole = autoCorrect[eng]
        if (whole != null) return myConvert(whole)

        // Whole word not in the dictionary: retry ignoring leading/trailing punctuation.
        val cut = cutText(eng)
        val start = cut[0]
        val middle = cut[1]
        val end = cut[2]
        val found = autoCorrect[middle]
        if (found == null) return myConvert(eng)

        val out = StringBuilder()
        if (start.isNotEmpty()) out.append(myConvert(start))
        if (middle.isNotEmpty()) out.append(myConvert(found))
        if (end.isNotEmpty()) out.append(myConvert(end))
        return out.toString()
    }

    /**
     * Phonetic case rules: only some capital letters mean something (O, I, U, D, G, N, R, S, T, Y, Z and, when
     * [joNukta] is on, J). All other capitals are lowered.
     */
    fun correctCase(input: String): String {
        val sb = StringBuilder(input.length)
        for (ch in input) {
            when (ch) {
                'o', 'O', 'i', 'I', 'u', 'U',
                'd', 'D', 'g', 'G', 'j', 'n', 'N', 'r', 'R', 's', 'S', 't', 'T', 'y', 'Y', 'z', 'Z' -> sb.append(ch)
                'J' -> sb.append(if (joNukta) 'J' else 'j')
                else -> sb.append(lower(ch))
            }
        }
        return sb.toString()
    }

    /**
     * Splits a word into [leading punctuation, main part, trailing punctuation], used for the autocorrect lookup.
     * Returns a fresh 3-element array.
     */
    fun cutText(input: String): Array<String> {
        val n = input.length
        var p = 0
        // Leading ignorable characters.
        var i = 1
        loop@ while (i <= n) {
            val c = input[i - 1]
            when (c) {
                '~', '!', '@', '#', '%', '&', '*', '(', ')', '-', '_', '=', '+', '[', ']', '{', '}', '\'', '"', ';',
                '<', '>', '/', '?', '|', '\\', '.' -> p = i
                ',' -> if (charAt(input, i + 1) == ',') break@loop else p = i
                ':' -> if (charAt(input, i + 1) == '`') p = i else break@loop
                '`' -> if (i - 1 >= 1) {
                    val b = charAt(input, i - 1)
                    if (b == '.' || b == ':') p = i else break@loop
                } else break@loop
                else -> break@loop
            }
            i++
        }
        val sIgnore = input.substring(0, p)

        // Trailing ignorable characters, scanned on the reversed word.
        val rev = input.reversed()
        var q = 0
        i = 1
        loop2@ while (i <= n - p) {
            val c = rev[i - 1]
            when (c) {
                '~', '!', '@', '#', '%', '&', '*', '(', ')', '-', '_', '=', '+', '[', ']', '{', '}', '\'', '"', ';',
                '<', '>', '/', '.', '?', '|', '\\' -> q = i
                ',' -> if (charAt(rev, i + 1) == ',') break@loop2 else q = i
                '`' -> {
                    val nx = charAt(rev, i + 1)
                    if (nx == ':' || nx == '.') q = i else break@loop2
                }
                ':' -> if (i - 1 >= 1) {
                    if (charAt(rev, i - 1) == '`') q = i else break@loop2
                } else break@loop2
                else -> break@loop2
            }
            i++
        }
        val eIgnore = input.substring(n - q)
        val middle = input.substring(p, n - q)
        return arrayOf(sIgnore, middle, eIgnore)
    }

    // ---------------------------------------------------------------------------------------------- main loop

    private fun myConvert(eng: String): String {
        if (eng.isEmpty()) return ""
        text = eng
        ln = eng.length
        pos = 1
        rs.setLength(0)

        while (pos <= ln) {
            val tt = text[pos - 1]
            when (tt) {
                '0' -> addRs(b_0)
                '1' -> addRs(b_1)
                '2' -> addRs(b_2)
                '3' -> addRs(b_3)
                '4' -> addRs(b_4)
                '5' -> addRs(b_5)
                '6' -> addRs(b_6)
                '7' -> addRs(b_7)
                '8' -> addRs(b_8)
                '9' -> addRs(b_9)

                'o' -> smallO()

                'a', 'A' -> {
                    val prev = prevT()
                    val next = nextT()
                    if (next == 'Z') addRsEx(b_A + b_Hasanta + b_Z + b_AAkar, 2)
                    else if (begining() && next != '`') addRs(b_AA)
                    else if (!consonent(prev) && prev != 'a' && next != '`') addRs(b_Y + b_AAkar)
                    else if (next == '`') addRsEx(b_AAkar, 2)
                    else if (prev == 'a' && next != '`') addRs(b_AA)
                    else addRs(b_AAkar)
                }

                'i' -> vowelSign(b_I, b_Ikar)
                'I' -> vowelSign(b_II, b_IIkar)
                'u' -> vowelSign(b_U, b_Ukar)
                'U' -> vowelSign(b_UU, b_UUkar)

                'e', 'E' -> {
                    val next = nextT()
                    if ((!consonent(prevT()) || begining()) && next != '`') {
                        if (next == 'e') addRsEx(b_II, 2) else addRs(b_E)
                    } else if (next == '`') {
                        addRsEx(b_Ekar, 2)
                    } else {
                        if (next == 'e') addRsEx(b_IIkar, 2) else addRs(b_Ekar)
                    }
                }

                'O' -> bigO()

                'k' -> applyRules(AvroRules.K)
                'G', 'g' -> applyRules(AvroRules.G)
                'N', 'n' -> applyRules(AvroRules.N)
                'c' -> applyRules(AvroRules.C)
                'J', 'j' -> jRule()
                'T', 't' -> applyRules(AvroRules.T)
                'D', 'd' -> applyRules(AvroRules.D)
                'p', 'f' -> applyRules(AvroRules.P)
                'b', 'v' -> applyRules(AvroRules.B)
                'm' -> applyRules(AvroRules.M)
                'z' -> addRs(b_Z)
                'Z' -> if (prevT() == 'r') {
                    val p2 = prevTEx(2)
                    if (consonent(p2) && p2 != 'r' && p2 != 'y' && p2 != 'w' && p2 != 'x') {
                        addRs(b_Hasanta + b_Z) // previous is a ra-fola: no ZWJ
                    } else {
                        addRs(ZWJ + b_Hasanta + b_Z)
                    }
                } else {
                    addRs(b_Hasanta + b_Z)
                }

                'R', 'r' -> rRule()
                'l' -> applyRules(AvroRules.L)
                'S', 's' -> applyRules(AvroRules.S)
                'h' -> applyRules(AvroRules.H)

                'y' -> {
                    val prev = prevT()
                    if (!consonent(prev) && !begining()) {
                        addRs(b_Y)
                    } else if (begining()) {
                        addRs(b_I + b_Y)
                    } else if (prev == 'r') {
                        val p2 = prevTEx(2)
                        if (consonent(p2) && p2 != 'r' && p2 != 'y' && p2 != 'w' && p2 != 'x') {
                            addRs(b_Hasanta + b_Z)
                        } else {
                            addRs(ZWJ + b_Hasanta + b_Z)
                        }
                    } else {
                        addRs(b_Hasanta + b_Z)
                    }
                }

                'Y' -> addRs(b_Y) // forced Y
                'w' -> if (begining() && vowel(nextT())) addRs(b_O + b_Y)
                else if (consonent(prevT())) addRs(b_Hasanta + b_B)
                else addRs(b_O)

                'q' -> addRs(b_K)
                'x' -> if (begining()) addRs(b_E + b_K + b_Hasanta + b_S) else addRs(b_K + b_Hasanta + b_S)

                '.' -> dot()
                ':' -> if (nextT() != '`') addRs(b_Bisharga) else addRsEx(":", 2)
                '^' -> if (nextT() != '`') addRs(b_Chandra) else addRsEx("^", 2)
                ',' -> if (nextT() == ',') addRsEx(b_Hasanta + ZWNJ, 2) else addRs(",")
                '$' -> addRs(b_Taka)

                // The backtick only separates letters (stops conjunct formation); nothing is output.
                '`' -> pos += 1

                else -> {
                    rs.append(tt)
                    pos += 1
                }
            }
        }
        return rs.toString()
    }

    // ---------------------------------------------------------------------------------------------- rule helpers

    /** i, I, u, U share one shape: independent vowel, or kar after a consonant; a following backtick forces the kar. */
    private fun vowelSign(independent: String, kar: String) {
        val next = nextT()
        if ((!consonent(prevT()) || begining()) && next != '`') addRs(independent)
        else if (next == '`') addRsEx(kar, 2)
        else addRs(kar)
    }

    /** First matching pattern of a flat [pattern, replacement, ...] table wins. */
    private fun applyRules(rules: Array<String>) {
        var i = 0
        while (i < rules.size) {
            if (cnv(rules[i], rules[i + 1])) return
            i += 2
        }
    }

    private fun smallO() {
        if ((!consonent(prevT()) || begining()) && nextT() != '`') {
            if (cnv("oo", b_U)) return
            if (cnv("oZ", b_A + b_Hasanta + b_Z)) return
            val prev = prevT()
            if (vowel(prev) && prev != 'o') {
                if (cnv("o", b_O)) return
            } else if (cnv("o", b_A)) return
        }
        if (cnv("oo", b_Ukar)) return
        if (cnv("o`", "")) return
        if (cnv("o", "")) return
    }

    private fun bigO() {
        if (cnv("OI`", b_OIkar)) return
        if (cnv("OU`", b_OUkar)) return
        if (cnv("O`", b_Okar)) return
        if (!consonent(prevT()) || begining()) {
            if (cnv("OI", b_OI)) return
            if (cnv("OU", b_OU)) return
            if (cnv("O", b_O)) return
        } else {
            if (cnv("OI", b_OIkar)) return
            if (cnv("OU", b_OUkar)) return
            if (cnv("O", b_Okar)) return
        }
    }

    private fun rRule() {
        if (charAtPos(3) == '`') { // NextTEx(1, 2): the character after "rri"
            if (cnv("rri", b_RRIkar)) return
        }
        if (!consonent(prevT())) {
            if (cnv("rri", b_RRI)) return
        } else if (begining()) {
            if (cnv("rri", b_RRI)) return
        } else {
            if (cnv("rri", b_RRIkar)) return
        }

        val afterNext = charAtPos(2) // NextTEx(1, 1)
        if (!consonent(prevT()) && !vowel(afterNext) && afterNext != 'r' && afterNext != NONE) {
            if (cnv("rr", b_R + b_Hasanta)) return // reph
        }

        if (cnv("Rg", b_Rr + b_Hasanta + b_G)) return
        if (cnv("Rh", b_Rrh)) return

        val prev = prevT()
        if (consonent(prev) && prev != 'r' && prev != 'y' && prev != 'w' && prev != 'x' && prev != 'Z') {
            if (cnv("r", b_Hasanta + b_R)) return // ra-fola
        } else {
            if (cnv("r", b_R)) return
        }
        if (cnv("R", b_Rr)) return
    }

    private fun jRule() {
        if (cnv("jjh", b_J + b_Hasanta + b_JH)) return
        if (cnv("jNG", b_J + b_Hasanta + b_NYA)) return
        if (cnv("jh", b_JH)) return
        if (cnv("jj", b_J + b_Hasanta + b_J)) return
        if (cnv("j", b_J)) return
        if (joNukta) {
            if (cnv("J", b_J + b_Nukta)) return
        } else {
            if (cnv("J", b_J)) return
        }
    }

    private fun dot() {
        if (cnv("...", "...")) return
        if (cnv(".`", ".")) return
        if (cnv("..", b_Dari + b_Dari)) return
        if (isDigit(nextT())) {
            if (cnv(".", ".")) return // decimal mark
        } else if (cnv(".", b_Dari)) return
    }

    // ---------------------------------------------------------------------------------------------- primitives

    /** If the text at [pos] starts with [pattern], output [replacement] and skip the pattern. */
    private fun cnv(pattern: String, replacement: String): Boolean {
        if (!text.startsWith(pattern, pos - 1)) return false
        rs.append(replacement)
        pos += pattern.length
        return true
    }

    private fun addRs(t: String) {
        rs.append(t)
        pos += 1
    }

    private fun addRsEx(t: String, skip: Int) {
        rs.append(t)
        pos += skip
    }

    /** Character [offset] places after the current one (1 = next), or [NONE]. */
    private fun charAtPos(offset: Int): Char = charAt(text, pos + offset)

    private fun prevT(): Char = charAt(text, pos - 1)
    private fun prevTEx(back: Int): Char = charAt(text, pos - back)
    private fun nextT(): Char = charAt(text, pos + 1)

    /** True when the previous character is not a letter or digit (or there is none). */
    private fun begining(): Boolean {
        val t = prevT()
        return !(t in '0'..'9' || t in 'A'..'Z' || t in 'a'..'z')
    }

    private companion object {
        const val NONE = '\u0000'

        /** 1-based character access; [NONE] outside the string (Pascal MidStr returns '' there). */
        fun charAt(s: String, oneBased: Int): Char = if (oneBased in 1..s.length) s[oneBased - 1] else NONE

        fun lower(c: Char): Char = if (c in 'A'..'Z') (c.code + 32).toChar() else c

        fun isDigit(c: Char): Boolean = c in '0'..'9'

        fun vowel(c: Char): Boolean {
            return when (lower(c)) {
                'a', 'e', 'i', 'o', 'u' -> true
                else -> false
            }
        }

        fun consonent(c: Char): Boolean {
            return when (lower(c)) {
                'b', 'c', 'd', 'f', 'g', 'h', 'j', 'k', 'l', 'm', 'n', 'p', 'q', 'r', 's', 't', 'v', 'w', 'x', 'y', 'z' -> true
                else -> false
            }
        }
    }
}
