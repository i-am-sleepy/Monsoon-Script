package com.monsoonscript.keyboard.calc

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CalcSupportTest {
    @Test
    fun runKeepsTheLastLineOnly() {
        val r = CalcRun()
        r.append("hello 16+8")
        assertEquals("hello 16+8", r.chars.toString())
        r.append('\n')
        assertEquals(0, r.length)
        r.append("2*3")
        assertEquals("2*3", r.chars.toString())
    }

    @Test
    fun runBackspaceAndReset() {
        val r = CalcRun()
        r.append("12+3")
        assertTrue(r.backspace())
        assertEquals("12+", r.chars.toString())
        r.clear()
        assertFalse(r.backspace())
        r.reset("first line\nsecond 4*5")
        assertEquals("second 4*5", r.chars.toString())
        r.reset(null)
        assertEquals(0, r.length)
    }

    @Test
    fun runIsBounded() {
        val r = CalcRun()
        r.append("x".repeat(500))
        assertEquals(CalcRun.MAX, r.length)
        r.reset("y".repeat(500))
        assertEquals(CalcRun.MAX, r.length)
        r.append("+1")
        assertTrue(r.chars.toString().endsWith("y+1"))
    }

    @Test
    fun numbersAreWrittenShortAndExact() {
        assertEquals("0", CalcFormat.number(0.0))
        assertEquals("0", CalcFormat.number(-0.0))
        assertEquals("0.3", CalcFormat.number(0.1 + 0.2))
        assertEquals("0.666666666667", CalcFormat.number(2.0 / 3.0))
        assertEquals("-0.5", CalcFormat.number(-0.5))
        assertEquals("123456.789", CalcFormat.number(123456.789))
        assertEquals("123456789012345", CalcFormat.number(123456789012345.0))
        assertEquals("1e15", CalcFormat.number(1e15))
        assertEquals("1.5e-7", CalcFormat.number(1.5e-7))
        assertEquals("0.00001", CalcFormat.number(1e-5))
        assertEquals("-2.5e20", CalcFormat.number(-2.5e20))
        assertNull(CalcFormat.number(Double.NaN))
        assertNull(CalcFormat.number(Double.POSITIVE_INFINITY))
    }

    @Test
    fun banglaDigits() {
        assertEquals("\u09E7\u09E8.\u09EB", CalcFormat.toBangla("12.5"))
        assertEquals("-\u09E9e\u09EA", CalcFormat.toBangla("-3e4"))
        assertTrue(CalcFormat.hasBanglaDigit("a\u09E7"))
        assertFalse(CalcFormat.hasBanglaDigit("a1"))
        assertEquals('7', CalcFormat.asciiDigit('\u09ED'))
        assertEquals('x', CalcFormat.asciiDigit('x'))
    }

    @Test
    fun unitsConvertOnlyWithinTheirKind() {
        val km = CalcUnits.find("km")!!
        val mi = CalcUnits.find("miles")!!
        val kg = CalcUnits.find("kg")!!
        assertEquals(1.609344, CalcUnits.convert(1.0, mi, km)!!, 1e-12)
        assertNull(CalcUnits.convert(1.0, km, kg))
        assertEquals(212.0, CalcUnits.convert(100.0, CalcUnits.find("c")!!, CalcUnits.find("f")!!)!!, 1e-9)
        assertEquals(-40.0, CalcUnits.convert(-40.0, CalcUnits.find("f")!!, CalcUnits.find("celsius")!!)!!, 1e-9)
        assertTrue(CalcUnits.isSlashUnit("km/h"))
        assertFalse(CalcUnits.isSlashUnit("a/b"))
        assertNull(CalcUnits.find("town"))
        assertEquals("km", CalcUnits.find("KM")!!.symbol)
    }

    // ---- typing it key by key, the way the keyboard feeds the buffer

    private fun typed(run: CalcRun, text: String, out: MutableList<String?>) {
        for (c in text) {
            run.append(c)
            out.add(CalcEngine.offer(run.chars)?.value)
        }
    }

    @Test
    fun theChipComesAndGoesWhileTyping() {
        val run = CalcRun()
        val seen = ArrayList<String?>()
        typed(run, "16+8*2", seen)
        // 1, 16, 16+, 16+8, 16+8*, 16+8*2
        assertEquals(listOf(null, null, null, "24", null, "32"), seen)
    }

    @Test
    fun backspaceBringsTheAnswerBackAndTheEqualsSignKeepsIt() {
        val run = CalcRun()
        val seen = ArrayList<String?>()
        typed(run, "16+8=", seen)
        assertEquals("24", seen.last())
        assertTrue(run.backspace())
        assertEquals("24", CalcEngine.offer(run.chars)?.value)
        assertTrue(run.backspace()) // 16+
        assertNull(CalcEngine.offer(run.chars))
    }

    @Test
    fun aSpaceEndsTheOfferAndTheNextWordIsPlainText() {
        val run = CalcRun()
        val seen = ArrayList<String?>()
        typed(run, "5+3 and", seen)
        assertEquals("8", seen[2])
        assertNull(seen[3]) // the space
        assertNull(seen.last())
    }

    @Test
    fun theOfferFollowsTheLineAcrossLineBreaks() {
        val run = CalcRun()
        run.append("note\n")
        run.append("7*6")
        assertEquals("42", CalcEngine.offer(run.chars)?.value)
        run.append('\n')
        assertNull(CalcEngine.offer(run.chars))
    }

    @Test
    fun aRebuiltBufferAnswersLikeALiveOne() {
        val live = CalcRun()
        live.append("I paid 16+8")
        val rebuilt = CalcRun()
        rebuilt.reset("old line\nI paid 16+8")
        assertEquals(CalcEngine.offer(live.chars)?.typed, CalcEngine.offer(rebuilt.chars)?.typed)
        assertEquals("24", CalcEngine.offer(rebuilt.chars)?.value)
    }
}

