package com.monsoonscript.keyboard.calc

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CalcEngineTest {
    /** The answer shown for [text] (degrees), or null when nothing is offered. */
    private fun ans(text: String, radians: Boolean = false): String? = CalcEngine.offer(text, radians)?.value

    private fun same(expected: String, text: String) = assertEquals("for <$text>", expected, ans(text))

    private fun none(text: String) = assertNull("for <$text>", ans(text))

    // ---------------------------------------------------------------- the basics

    @Test
    fun theFourOperations() {
        same("24", "16+8")
        same("128", "16*8")
        same("128", "16\u00D78")
        same("25", "100/4")
        same("25", "100\u00F74")
        same("8", "16-8=")
        same("15", "5 + 5 + 5")
        same("12.5", "100/8")
    }

    @Test
    fun precedenceAndBrackets() {
        same("1", "7-2*3")
        same("14", "2+3*4")
        same("20", "(2+3)*4")
        same("15", "(2+3)*(4-1)")
        same("-2", "-5+3")
        same("1024", "2^10")
        same("8", "2**3")
        same("-4", "-2^2")
        same("512", "2^3^2")
        same("0.25", "2^-2")
        same("-5", "5 - 10")
    }

    @Test
    fun decimalsAreExact() {
        same("0.3", "0.1+0.2")
        same("0", "0.1+0.2-0.3")
        same("0.333333333333", "1 / 3")
        same("0.666666666667", "2 / 3")
        same("2.5", "10 / 4")
        same("1001", "1e3+1")
        same("0.005", "2.5e-3*2")
        same("1005", "1,000+5")
        same("12345679", "12,345,678+1")
        same("0.75", ".5+.25")
    }

    @Test
    fun implicitMultiplicationAndConstants() {
        same("14", "2(3+4)")
        same("21", "(1+2)(3+4)")
        same("6.28318530718", "2pi")
        same("6.28318530718", "pi*2")
        same("6.28318530718", "2\u03C0")
        same("5.43656365692", "2*e")
        same("1.5", "3 sin(30)")
        same("3.14159265359", "pi=")
        none("2e")
        none("pi")
    }

    @Test
    fun percent() {
        same("30", "20% of 150")
        same("30", "20 % of 150")
        same("165", "150+10%")
        same("135", "150-10%")
        same("180", "200 - 10%")
        same("15", "150*10%")
        same("0.5", "50%=")
        none("50%")
        same("10", "what is 20% of 50")
    }

    @Test
    fun moduloPowersRootsAndFactorials() {
        same("1", "10 mod 3")
        same("2", "-7 mod 3")
        same("2", "17 % 5")
        same("25", "5\u00B2")
        same("9", "2\u00B3+1")
        same("4", "sqrt(16)")
        same("4", "\u221A16")
        same("1.41421356237", "\u221A(2)")
        same("3", "cbrt(27)")
        same("3", "root(27,3)")
        same("120", "5!=")
        same("3628800", "10!=")
        same("123", "3+5!")
        same("2.43290200818e18", "20!=")
        none("5!")
        none("sqrt(-1)")
    }

    @Test
    fun trigInDegreesAndRadians() {
        same("0.5", "sin(30)")
        same("0.5", "sin 30")
        same("0.5", "cos(60)")
        same("1", "tan(45)")
        same("1", "sin(90)")
        same("0", "cos(90)")
        same("0", "sin(180)")
        same("-1", "cos(180)")
        same("90", "asin(1)")
        same("45", "atan(1)")
        same("60", "acos(0.5)")
        none("tan(90)")
        assertEquals("0.785398163397", ans("atan(1)", radians = true))
        assertEquals("-1", ans("cos(pi)", radians = true))
        assertEquals("0", ans("cos(pi/2)", radians = true))
        assertEquals("0.841470984808", ans("sin(1)", radians = true))
        same("0.5", "sin(30\u00B0)")
    }

    @Test
    fun logsAndFunctions() {
        same("2", "log(100)")
        same("1", "ln(e)")
        same("3", "log2(8)")
        same("3", "log(8,2)")
        same("1", "exp(0)")
        same("5", "hypot(3,4)")
        same("5", "abs(-5)")
        same("2", "floor(2.7)")
        same("3", "ceil(2.1)")
        same("3", "round(2.5)")
        same("3.14", "round(3.14159,2)")
        same("6", "gcd(12,18)")
        same("12", "lcm(4,6)")
        same("10", "ncr(5,2)")
        same("20", "npr(5,2)")
        same("9", "max(3,9,2)")
        same("2", "min(3,9,2)")
        same("4", "avg(2,4,6)")
        same("6", "sum(1,2,3)")
        same("0.5", "sinh(0)+0.5")
        none("ln(0)")
        none("log(-1)")
        none("asin(2)")
    }

    @Test
    fun bigAndSmallNumbers() {
        same("1.84467440737e19", "2^64")
        same("1e15", "10^15")
        same("1e-7", "10^-7")
        same("0.00001", "10^-5")
        same("100000000000000", "10^14")
    }

    @Test
    fun baseNumbersAndConversion() {
        same("256", "0xFF+1")
        same("10", "0b101*2")
        same("8", "0o7+1")
        same("FF", "255 to hex")
        same("11111111", "255 in binary")
        same("10", "8 to oct")
        same("255", "0xFF to dec")
        same("-FF", "-255 to hex")
        none("1.5 to hex")
        none("0x1F")
    }

    @Test
    fun unitConversion() {
        same("3.10685596119 mi", "5 km in miles")
        same("212 \u00B0F", "100 c to f")
        same("100 \u00B0C", "212 f to c")
        same("37 \u00B0C", "98.6 f to c")
        same("2.20462262185 lb", "1 kg to lb")
        same("1.609344 km", "1 mi to km")
        same("96.56064 km/h", "60 mph in km/h")
        same("1000 MB", "1 gb to mb")
        same("1024 MiB", "1 gib to mib")
        same("1.57079632679 rad", "90 deg to rad")
        same("24 h", "1 day to hours")
        same("60 min", "1 hour in minutes")
        same("10000 m", "10 km to m")
        same("8.04672 km", "5 miles in km")
        same("4.92892159375 ml", "1 tsp to ml")
        same("4046.8564224 m\u00B2", "1 acre to m2")
        same("273.15 K", "0 c to k")
        same("62.1371192237 mph", "100 kmh in mph")
        none("1 km to kg")
        none("5 km in town")
        none("5 miles")
    }

    // ---------------------------------------------------------------- what is not a calculation

    @Test
    fun datesRangesTimesAndPhoneNumbersAreLeftAlone() {
        none("5-10")
        none("2020-2024")
        none("01711-222333")
        none("880-1711-222333")
        none("12/5")
        none("1/2")
        none("31/12")
        none("12/5/2026")
        none("05/12")
        none("10:30")
        none("10.30")
        none("3-4 days")
        none("I will be there 5-10")
    }

    @Test
    fun yetAnEqualsSignOrSpacesOrAnotherOperatorMakeItASum() {
        same("-5", "5-10=")
        same("2.4", "12 / 5")
        same("2.4", "12/5=")
        same("25", "100/4")
        same("2", "16\u00F78")
        same("26", "5-10+31")
    }

    @Test
    fun plainTextAndNumbersAreNeverAnswered() {
        none("")
        none("hello")
        none("hello world 5")
        none("I have 2 apples")
        none("call 01711222333")
        none("16")
        none("-5")
        none("(5)")
        none("see you at 5!")
        none("version 2.0.1")
        none("abc12*3")
        none("x16+8")
        none("5 6")
    }

    @Test
    fun anUnfinishedOrBrokenCalculationShowsNothing() {
        none("16+")
        none("16+8 ")
        none("5+3)")
        none("5/0")
        none("5 / 0")
        none("0/0")
        none("sqrt(")
        none("2^^3")
        none("16+8+")
        none("1 + * 2")
        none("sin()")
    }

    @Test
    fun anOpenBracketStillAnswersTheCalculationInsideIt() {
        val o = CalcEngine.offer("(5+3")!!
        assertEquals("8", o.value)
        assertEquals(1, o.start)
        assertEquals("5+3", o.typed)
    }

    @Test
    fun deepBracketsAreRefusedNotCrashed() {
        none("(".repeat(30) + "1" + ")".repeat(30) + "+1")
        assertNotNull(ans("(".repeat(10) + "1" + ")".repeat(10) + "+1"))
    }

    // ---------------------------------------------------------------- the calculation inside normal text

    @Test
    fun theCalculationAtTheEndOfTheTextIsFound() {
        val o = CalcEngine.offer("I paid 16+8")!!
        assertEquals(7, o.start)
        assertEquals("16+8", o.typed)
        assertEquals("24", o.value)
        assertFalse(o.endsWithEquals)
        assertFalse(o.spaced)
        val p = CalcEngine.offer("total: 5 + 3")!!
        assertEquals(7, p.start)
        assertEquals("5 + 3", p.typed)
        assertEquals("8", p.value)
        assertTrue(p.spaced)
        same("7", "2 3+4")
        assertEquals(2, CalcEngine.offer("2 3+4")!!.start)
    }

    @Test
    fun onlyTheLastLineCounts() {
        val o = CalcEngine.offer("first 1+1\n16+8")!!
        assertEquals("16+8", o.typed)
        assertEquals(10, o.start)
        none("16+8\nhello")
    }

    @Test
    fun theChipsTextAndLabels() {
        val a = CalcEngine.offer("16+8")!!
        assertEquals("= 24", a.appendLabel)
        assertEquals("\u2192 24", a.replaceLabel)
        assertEquals("=24", a.appendText)
        assertEquals("24", a.replaceText)
        val b = CalcEngine.offer("16 + 8")!!
        assertEquals(" = 24", b.appendText)
        val c = CalcEngine.offer("16+8=")!!
        assertTrue(c.endsWithEquals)
        assertEquals("24", c.appendLabel)
        assertEquals("24", c.appendText)
        assertEquals("16+8=", c.typed)
        val d = CalcEngine.offer("16 + 8 =")!!
        assertEquals(" 24", d.appendText)
        val e = CalcEngine.offer("5 km in miles")!!
        assertEquals(" = 3.10685596119 mi", e.appendText)
    }

    @Test
    fun anAnswerThatWasInsertedIsNotAnsweredAgainButTheNextOneIs() {
        none("16+8=24")
        same("25", "16+8=24+1")
        none("24")
    }

    // ---------------------------------------------------------------- Bangla digits

    @Test
    fun banglaDigitsAreUnderstoodAndAnsweredInBangla() {
        same("\u09E8\u09EA", "\u09E7\u09EC+\u09EE")
        same("\u09E7\u09E8\u09EE", "\u09E7\u09EC*\u09EE")
        same("\u09E8\u09EB", "\u09E7\u09E8.\u09EB*\u09E8")
        val o = CalcEngine.offer("\u09E7\u09EC+\u09EE=")!!
        assertEquals("\u09E8\u09EA", o.value)
        assertEquals("\u09E7\u09EC+\u09EE=", o.typed)
        same("\u09E7\u09E6", "\u09EB+\u09EB")
        same("\u09EB\u09ED.\u09E8 \u00B0F", "\u09E7\u09EA c to f")
        // a mixed typing: English digits and Bangla digits count as Bangla
        same("\u09E8\u09EA", "\u09E7\u09EC+8")
    }

    // ---------------------------------------------------------------- speed

    @Test
    fun normalTypingCostsNothing() {
        val lines = listOf("this is a long sentence with a number 5 and more words", "hello world", "16+8", "call 01711222333 now", "(1+2)*3")
        val t0 = System.nanoTime()
        var hits = 0
        repeat(20000) { i ->
            val l = lines[i % lines.size]
            if (CalcEngine.offer(l) != null) hits++
        }
        val ms = (System.nanoTime() - t0) / 1_000_000
        assertTrue("20000 offers took $ms ms", ms < 3000)
        assertEquals(8000, hits)
    }
}
