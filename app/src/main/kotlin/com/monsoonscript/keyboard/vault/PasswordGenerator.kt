package com.monsoonscript.keyboard.vault

import java.security.SecureRandom
import java.util.Random
import kotlin.math.ln

/**
 * Random passwords for the vault (E1.13, D81): characters only, 8 to 64 long, any mix of upper case, lower case, digits and symbols. Pure Kotlin (no Android
 * classes), so it is unit-tested. Every switched-on kind is guaranteed to appear at least once; the choice of each character is uniform ([Random.nextInt]
 * with a bound has no modulo bias) and the final order is shuffled with Fisher-Yates.
 */
internal object PasswordGenerator {
    const val MIN_LENGTH = 8
    const val MAX_LENGTH = 64
    const val DEFAULT_LENGTH = 20

    const val UPPER = "ABCDEFGHIJKLMNOPQRSTUVWXYZ"
    const val LOWER = "abcdefghijklmnopqrstuvwxyz"
    const val DIGITS = "0123456789"
    const val SYMBOLS = "!@#$%^&*()-_=+[]{};:,.?/"

    /** With every kind switched off the result still has to be a password: lower case and digits are used then. */
    fun generate(
        length: Int,
        upper: Boolean = true,
        lower: Boolean = true,
        digits: Boolean = true,
        symbols: Boolean = true,
        random: Random = SecureRandom(),
    ): String {
        val pools = ArrayList<String>(4)
        if (upper) pools += UPPER
        if (lower) pools += LOWER
        if (digits) pools += DIGITS
        if (symbols) pools += SYMBOLS
        if (pools.isEmpty()) {
            pools += LOWER
            pools += DIGITS
        }
        val n = length.coerceIn(maxOf(MIN_LENGTH, pools.size), MAX_LENGTH)
        val all = pools.joinToString("")
        val out = CharArray(n)
        for (i in pools.indices) out[i] = pools[i][random.nextInt(pools[i].length)]
        for (i in pools.size until n) out[i] = all[random.nextInt(all.length)]
        for (i in n - 1 downTo 1) { // Fisher-Yates
            val j = random.nextInt(i + 1)
            val t = out[i]
            out[i] = out[j]
            out[j] = t
        }
        return String(out)
    }
}

/**
 * A rough strength hint for a master password (D81: "strength hint"): the size of the alphabet the characters come from, times the length, counting a repeated
 * character for less. It is an ESTIMATE of how hard a plain random guess would be, not a promise; the screen words it that way.
 */
internal object PasswordStrength {
    enum class Level { EMPTY, WEAK, FAIR, GOOD, STRONG }

    fun bits(password: CharSequence): Double {
        if (password.isEmpty()) return 0.0
        var lower = false
        var upper = false
        var digit = false
        var symbol = false
        var other = false
        for (c in password) {
            when {
                c in 'a'..'z' -> lower = true
                c in 'A'..'Z' -> upper = true
                c in '0'..'9' -> digit = true
                c.code in 33..126 -> symbol = true
                else -> other = true // spaces and every other script (Bangla, accents ...)
            }
        }
        var pool = 0
        if (lower) pool += 26
        if (upper) pool += 26
        if (digit) pool += 10
        if (symbol) pool += 33
        if (other) pool += 100
        val distinct = password.toSet().size
        val effective = distinct + 0.3 * (password.length - distinct)
        return effective * ln(pool.toDouble()) / ln(2.0)
    }

    fun level(password: CharSequence): Level {
        if (password.isEmpty()) return Level.EMPTY
        val b = bits(password)
        return when {
            b < 36 -> Level.WEAK
            b < 56 -> Level.FAIR
            b < 80 -> Level.GOOD
            else -> Level.STRONG
        }
    }
}
