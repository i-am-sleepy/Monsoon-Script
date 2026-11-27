package com.monsoonscript.keyboard.calc

import java.math.BigDecimal
import java.math.MathContext
import java.math.RoundingMode
import kotlin.math.abs

/** Numbers in and out of the calculator: the written form of an answer (12 significant digits, no binary noise), and Bangla digits. Pure Kotlin. */
object CalcFormat {
    private const val BANGLA_ZERO = '\u09E6'
    private val MC = MathContext(12, RoundingMode.HALF_EVEN)

    fun isBanglaDigit(c: Char): Boolean = c in '\u09E6'..'\u09EF'

    fun hasBanglaDigit(s: CharSequence): Boolean {
        for (c in s) if (isBanglaDigit(c)) return true
        return false
    }

    /** The ASCII digit for a Bangla one; every other character stays. */
    fun asciiDigit(c: Char): Char = if (isBanglaDigit(c)) '0' + (c - BANGLA_ZERO) else c

    /** [s] with its ASCII digits written as Bangla digits (letters, signs, the point and the exponent stay as they are). */
    fun toBangla(s: String): String {
        val sb = StringBuilder(s.length)
        for (c in s) sb.append(if (c in '0'..'9') BANGLA_ZERO + (c - '0') else c)
        return sb.toString()
    }

    /**
     * The answer as it is written: whole numbers up to 15 digits in full, other numbers rounded to 12 significant digits with the trailing zeros dropped
     * (0.1 + 0.2 is 0.3, 1 / 3 is 0.333333333333), very large or small ones as 1.5e21 / 2.5e-7. Null for NaN and infinity.
     */
    fun number(v: Double): String? {
        if (v.isNaN() || v.isInfinite()) return null
        if (v == 0.0) return "0"
        if (abs(v) < 1e15 && v == Math.rint(v)) return v.toLong().toString()
        val bd = BigDecimal(v).round(MC).stripTrailingZeros()
        val exp10 = bd.precision() - bd.scale() - 1 // the power of ten of the first digit
        if (exp10 in -5..14) return bd.toPlainString()
        val digits = bd.unscaledValue().abs().toString()
        val sign = if (bd.signum() < 0) "-" else ""
        val mantissa = if (digits.length > 1) digits[0] + "." + digits.substring(1) else digits
        return "$sign${mantissa}e$exp10"
    }
}
