package com.monsoonscript.keyboard.vault

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OtpFormatTest {
    @Test
    fun codesAreGroupedForReading() {
        assertEquals("123 456", OtpFormat.grouped("123456"))
        assertEquals("1234 5678", OtpFormat.grouped("12345678"))
        assertEquals("2B7QK", OtpFormat.grouped("2B7QK")) // Steam: as it is
        assertEquals("1234567", OtpFormat.grouped("1234567"))
        assertEquals("", OtpFormat.grouped(""))
    }

    @Test
    fun aCodeAboutToChangeWaitsForTheNextOne() {
        assertTrue(OtpFormat.shouldWait(4, 30))
        assertTrue(OtpFormat.shouldWait(1, 30))
        assertFalse(OtpFormat.shouldWait(5, 30))
        assertFalse(OtpFormat.shouldWait(30, 30))
        assertTrue(OtpFormat.shouldWait(2, 15))
        assertFalse(OtpFormat.shouldWait(2, 10)) // very short periods never wait
    }

    @Test
    fun theCountdownRoundsUpAndTicksOnTheSecond() {
        assertEquals(0, OtpFormat.secondsShown(0))
        assertEquals(0, OtpFormat.secondsShown(-50))
        assertEquals(1, OtpFormat.secondsShown(1))
        assertEquals(1, OtpFormat.secondsShown(1000))
        assertEquals(2, OtpFormat.secondsShown(1001))
        assertEquals(30, OtpFormat.secondsShown(30_000))
        assertEquals(1005L, OtpFormat.nextTickDelay(2000)) // exactly on a second: a full second to the next
        assertEquals(505L, OtpFormat.nextTickDelay(2500))
        assertEquals(1005L, OtpFormat.nextTickDelay(0))
    }
}
