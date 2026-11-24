package com.monsoonscript.keyboard.vault

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Random

class PasswordGeneratorTest {
    private fun has(s: String, set: String) = s.any { it in set }

    @Test
    fun lengthIsKeptInsideTheLimits() {
        assertEquals(20, PasswordGenerator.generate(20).length)
        assertEquals(8, PasswordGenerator.generate(3).length)
        assertEquals(64, PasswordGenerator.generate(500).length)
    }

    @Test
    fun everySwitchedOnKindAppears() {
        val rnd = Random(7)
        repeat(300) {
            val p = PasswordGenerator.generate(8, upper = true, lower = true, digits = true, symbols = true, random = rnd)
            assertTrue(p, has(p, PasswordGenerator.UPPER) && has(p, PasswordGenerator.LOWER) && has(p, PasswordGenerator.DIGITS) && has(p, PasswordGenerator.SYMBOLS))
        }
    }

    @Test
    fun switchedOffKindsNeverAppear() {
        val rnd = Random(11)
        repeat(200) {
            val p = PasswordGenerator.generate(30, upper = false, lower = true, digits = true, symbols = false, random = rnd)
            assertTrue(p, p.all { it in PasswordGenerator.LOWER + PasswordGenerator.DIGITS })
            assertTrue(p, has(p, PasswordGenerator.LOWER) && has(p, PasswordGenerator.DIGITS))
        }
    }

    @Test
    fun nothingSwitchedOnFallsBackToLowerCaseAndDigits() {
        val p = PasswordGenerator.generate(16, upper = false, lower = false, digits = false, symbols = false, random = Random(1))
        assertEquals(16, p.length)
        assertTrue(p.all { it in PasswordGenerator.LOWER + PasswordGenerator.DIGITS })
    }

    @Test
    fun sameSeedSameResultAndDifferentSeedsDiffer() {
        assertEquals(PasswordGenerator.generate(24, random = Random(5)), PasswordGenerator.generate(24, random = Random(5)))
        assertTrue(PasswordGenerator.generate(24, random = Random(5)) != PasswordGenerator.generate(24, random = Random(6)))
    }

    @Test
    fun charactersAreRoughlyUniform() {
        val rnd = Random(3)
        val counts = IntArray(10)
        repeat(20000) { counts[PasswordGenerator.generate(8, upper = false, lower = false, digits = true, symbols = false, random = rnd)[0] - '0']++ }
        // only digits are switched on, and the shuffle makes position 0 a uniform pick: all ten digits turn up there
        assertTrue(counts.all { it > 0 })
    }

    @Test
    fun strengthGrowsWithLengthAndAlphabet() {
        assertEquals(PasswordStrength.Level.EMPTY, PasswordStrength.level(""))
        assertEquals(PasswordStrength.Level.WEAK, PasswordStrength.level("password"))
        assertEquals(PasswordStrength.Level.WEAK, PasswordStrength.level("aaaaaaaaaaaa"))
        assertTrue(PasswordStrength.bits("correcthorse") < PasswordStrength.bits("correcthorsebatterystaple"))
        assertTrue(PasswordStrength.bits("abcdefghij") < PasswordStrength.bits("aB3\$efghij"))
        assertEquals(PasswordStrength.Level.STRONG, PasswordStrength.level(PasswordGenerator.generate(20, random = Random(9))))
        assertTrue(PasswordStrength.level("Tr0ub4dor&3") >= PasswordStrength.Level.FAIR)
    }

    @Test
    fun otherScriptsCountAsALargeAlphabet() {
        assertTrue(PasswordStrength.bits("আমারসোনারবাংলা") > PasswordStrength.bits("abcdefghijklmn"))
    }
}
