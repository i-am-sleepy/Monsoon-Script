/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Derived from Avro Keyboard (OmicronLab), clsPhoneticRegExBuilder.pas (TEnglishToRegEx).
 * Kotlin port for Monsoon Script; see third_party/avro-reference/NOTICE.md.
 */

package com.monsoonscript.keyboard.lang.avro

/**
 * Turns a typed roman word into a regular expression that matches every Bangla spelling Avro considers a phonetic
 * fit ("ami" -> আমি, আমী, ...). The expression is meant to be used with a full match against dictionary words.
 *
 * Pure Kotlin, not thread-safe (scratch state is reused between calls). [pos] is 1-based like the Pascal original.
 */
class AvroRegexBuilder {

    private var text: String = ""
    private var ln: Int = 0
    private var pos: Int = 1
    private val rs = StringBuilder(512)

    /** The regex for [english], or "" when there is nothing sensible to search for. */
    fun convert(english: String): String {
        if (english.isEmpty()) return ""
        // Four identical letters at the start ("aaaa...") means no real word: skip the search.
        if (english.length >= 4) {
            val c = english[0]
            if ((c in 'a'..'z' || c in 'A'..'Z') && english[1] == c && english[2] == c && english[3] == c) return ""
        }
        text = prepare(english)
        if (text.isEmpty()) return ""
        return myConvert()
    }

    /** Lower-cases (ASCII) and drops every character that would be a regex metacharacter or punctuation. */
    private fun prepare(input: String): String {
        val sb = StringBuilder(input.length)
        for (ch in input) {
            if (ch in IGNORED) continue
            sb.append(if (ch in 'A'..'Z') (ch.code + 32).toChar() else ch)
        }
        return sb.toString()
    }

    private fun myConvert(): String {
        ln = text.length
        pos = 1
        rs.setLength(0)

        while (pos <= ln) {
            val before = pos
            val tt = text[pos - 1]
            when (tt) {
                '0' -> addRs("((" + b_0 + ")|(0)|(" + b_Sh + b_UUkar + b_N + b_Hasanta + b_Z + "))")
                '1' -> addRs("((" + b_1 + ")|(1)|(" + b_E + b_K + "))")
                '2' -> addRs("((" + b_2 + ")|(2)|(" + b_D + b_Ukar + b_I + "))")
                '3' -> addRs("((" + b_3 + ")|(3)|(" + b_T + b_Ikar + b_N + "))")
                '4' -> addRs("((" + b_4 + ")|(4)|(" + b_C + b_AAkar + b_R + "))")
                '5' -> addRs("((" + b_5 + ")|(5)|(" + b_P + b_AAkar + b_Chandra + b_C + "))")
                '6' -> addRs("((6)|(" + b_6 + ")|(" + b_CH + b_Y + "))")
                '7' -> addRs("((" + b_7 + ")|(7)|(" + b_S + b_AAkar + b_T + "))")
                '8' -> addRs("((" + b_8 + ")|(8)|(" + b_AA + b_Tt + "))")
                '9' -> addRs("((" + b_9 + ")|(9)|(" + b_N + b_Y + "))")

                'a' -> applyRules(AvroRegexRules.A)
                'b' -> applyRules(AvroRegexRules.B)
                'c' -> applyRules(AvroRegexRules.C)
                'd' -> applyRules(AvroRegexRules.D)
                'e' -> applyRules(AvroRegexRules.E)
                'f' -> applyRules(AvroRegexRules.F)
                'g' -> applyRules(AvroRegexRules.G)
                'h' -> applyRules(AvroRegexRules.H)
                'i' -> applyRules(AvroRegexRules.I)
                'j' -> applyRules(AvroRegexRules.J)
                'k' -> applyRules(AvroRegexRules.K)
                'l' -> applyRules(AvroRegexRules.L)
                'm' -> applyRules(AvroRegexRules.M)
                'n' -> applyRules(AvroRegexRules.N)
                'o' -> oRule()
                'p' -> applyRules(AvroRegexRules.P)
                'q' -> applyRules(AvroRegexRules.Q)
                'r' -> applyRules(AvroRegexRules.R)
                's' -> applyRules(AvroRegexRules.S)
                't' -> applyRules(AvroRegexRules.T)
                'u' -> applyRules(AvroRegexRules.U)
                'v' -> applyRules(AvroRegexRules.V)
                'w' -> applyRules(AvroRegexRules.W)
                'x' -> applyRules(AvroRegexRules.X)
                'y' -> applyRules(AvroRegexRules.Y)
                'z' -> applyRules(AvroRegexRules.Z)

                '`' -> pos += 1 // only stops conjunct formation, no output
                else -> addRs(tt.toString())
            }
            if (pos == before) pos += 1 // safety: never loop forever
        }
        return rs.toString()
    }

    private fun oRule() {
        if (cnv("oo", AvroRegex.r_OO)) return
        if (cnv("oi", AvroRegex.r_OI)) return
        if (cnv("ou", AvroRegex.r_OU)) return
        if (begining()) {
            if (cnv("o", AvroRegex.r_OFirst)) return
        } else {
            if (cnv("o", AvroRegex.r_O)) return
        }
    }

    private fun applyRules(rules: Array<String>) {
        var i = 0
        while (i < rules.size) {
            if (cnv(rules[i], rules[i + 1])) return
            i += 2
        }
    }

    /** Append a fragment followed by the optional-hasanta / fola / chandrabindu-or-bisharga allowances, depending on neighbours. */
    private fun cnv(pattern: String, fragment: String): Boolean {
        if (!text.startsWith(pattern, pos - 1)) return false
        val after = charAt(pos + pattern.length)
        val last = charAt(pos + pattern.length - 1)
        if (consonent(last) && consonent(after)) {
            rs.append(fragment).append(AvroRegex.r_InjectHasanta)
        } else if (after != NONE) {
            rs.append(fragment).append(AvroRegex.r_InjectFola).append(AvroRegex.r_InjectChandraBisharga)
        } else {
            rs.append(fragment).append(AvroRegex.r_InjectChandraBisharga)
        }
        pos += pattern.length
        return true
    }

    private fun addRs(fragment: String) {
        if (consonent(charAt(pos - 1)) && consonent(charAt(pos + 1))) {
            rs.append(fragment).append(AvroRegex.r_InjectHasanta)
        } else if (charAt(pos + 1) != NONE) {
            rs.append(fragment).append(AvroRegex.r_InjectFola).append(AvroRegex.r_InjectChandraBisharga)
        } else {
            rs.append(fragment).append(AvroRegex.r_InjectChandraBisharga)
        }
        pos += 1
    }

    /** True when the previous character is not a letter or digit (or there is none). */
    private fun begining(): Boolean {
        val t = charAt(pos - 1)
        return !(t in '0'..'9' || t in 'A'..'Z' || t in 'a'..'z')
    }

    private fun charAt(oneBased: Int): Char = if (oneBased in 1..ln) text[oneBased - 1] else NONE

    private fun consonent(c: Char): Boolean = when (c) {
        'b', 'c', 'd', 'f', 'g', 'h', 'j', 'k', 'l', 'm', 'n', 'p', 'q', 'r', 's', 't', 'v', 'w', 'x', 'y', 'z',
        'B', 'C', 'D', 'F', 'G', 'H', 'J', 'K', 'L', 'M', 'N', 'P', 'Q', 'R', 'S', 'T', 'V', 'W', 'X', 'Y', 'Z' -> true
        else -> false
    }

    private companion object {
        const val NONE = '\u0000'

        /** Characters Avro removes before building the expression (regex metacharacters and punctuation). */
        const val IGNORED = "\\|()[]{}^\$*+?.~!@#%&-_=\"';<>/,:`"
    }
}
