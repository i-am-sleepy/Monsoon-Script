package com.monsoonscript.keyboard.theme

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ColorMathTest {
    private val black = ColorMath.rgb(0, 0, 0)
    private val white = ColorMath.WHITE

    @Test fun contrastOfBlackOnWhiteIsTwentyOne() {
        assertEquals(21.0, ColorMath.contrast(black, white), 0.01)
        assertEquals(1.0, ColorMath.contrast(white, white), 1e-9)
    }

    @Test fun mixMovesAlongTheLine() {
        assertEquals(black, ColorMath.mix(black, white, 0f))
        assertEquals(white, ColorMath.mix(black, white, 1f))
        assertEquals(ColorMath.rgb(128, 128, 128), ColorMath.mix(black, white, 0.5f))
        assertEquals(white, ColorMath.mix(black, white, 9f)) // clamped
    }

    @Test fun lightAndDarkAreTold() {
        assertTrue(ColorMath.isLight(white))
        assertTrue(ColorMath.isLight(ColorMath.parseHex("#E3E6EA")!!))
        assertFalse(ColorMath.isLight(black))
        assertFalse(ColorMath.isLight(ColorMath.parseHex("#1B1C1E")!!))
        assertFalse(ColorMath.isLight(ColorMath.parseHex("#1B6B63")!!)) // the dark teal accent is a dark color
    }

    @Test fun readableOnPicksTheBetterOfBlackAndWhite() {
        assertEquals(ColorMath.NEAR_BLACK, ColorMath.readableOn(ColorMath.parseHex("#80CBC4")!!))
        assertEquals(white, ColorMath.readableOn(ColorMath.parseHex("#1B6B63")!!))
        assertEquals(white, ColorMath.readableOn(black))
    }

    @Test fun hexRoundTripsAndRejectsGarbage() {
        assertEquals("#1B6B63", ColorMath.hex(ColorMath.parseHex("#1b6b63")!!))
        assertEquals(ColorMath.parseHex("#FFAA00"), ColorMath.parseHex("fa0"))
        assertEquals(ColorMath.parseHex("#112233"), ColorMath.parseHex("  112233 "))
        for (bad in listOf("", "#", "#12", "#12345", "#1234567", "#GGGGGG", "red", "#12 456")) assertNull(bad, ColorMath.parseHex(bad))
    }

    @Test fun hsvRoundTrips() {
        for (hex in listOf("#FF0000", "#00FF00", "#0000FF", "#80CBC4", "#1B1C1E", "#FFFFFF", "#000000", "#C2185B", "#FFD600")) {
            val c = ColorMath.parseHex(hex)!!
            val h = ColorMath.toHsv(c)
            assertTrue(hex, ColorMath.near(c, ColorMath.fromHsv(h[0], h[1], h[2])))
        }
        assertEquals(ColorMath.parseHex("#FF0000"), ColorMath.fromHsv(0f, 1f, 1f))
        assertEquals(ColorMath.parseHex("#00FF00"), ColorMath.fromHsv(120f, 1f, 1f))
        assertEquals(ColorMath.parseHex("#0000FF"), ColorMath.fromHsv(240f, 1f, 1f))
        assertEquals(ColorMath.fromHsv(0f, 1f, 1f), ColorMath.fromHsv(360f, 1f, 1f)) // the hue wraps
        assertEquals(240f, ColorMath.toHsv(ColorMath.parseHex("#0000FF")!!)[0], 0.01f)
    }
}
