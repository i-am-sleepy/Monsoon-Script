package com.monsoonscript.keyboard.theme

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ThemeShapeTest {
    private fun custom() = Themes.DARK.copy(id = "custom:shape1", name = "Shape")

    @Test
    fun theDefaultShapeIsExactlyTheKeyboardsOldLook() {
        assertEquals(8f, ThemeSpec.cornerDp(ThemeSpec.DEFAULT_ROUNDNESS), 0.0001f)
        assertEquals(ThemeSpec.DEFAULT_ROUNDNESS, Themes.DARK.keyRoundness)
        assertFalse(Themes.DARK.keyBorder)
        assertFalse(Themes.DARK.keyShadow)
    }

    @Test
    fun roundnessGrowsFromSquareToPill() {
        assertEquals(0f, ThemeSpec.cornerDp(0), 0.0001f)
        assertTrue(ThemeSpec.cornerDp(100) > 24f) // more than half of a normal key: a pill
        assertEquals(ThemeSpec.cornerDp(100), ThemeSpec.cornerDp(900), 0.0001f)
        assertEquals(0f, ThemeSpec.cornerDp(-5), 0.0001f)
    }

    @Test
    fun theShapeRoundTripsThroughTheSavedText() {
        val t = custom().copy(keyRoundness = 75, keyBorder = true, keyShadow = true)
        val back = ThemeCodec.decodeList(ThemeCodec.encodeList(listOf(t))).single()
        assertEquals(75, back.keyRoundness)
        assertTrue(back.keyBorder)
        assertTrue(back.keyShadow)
    }

    @Test
    fun theDefaultShapeWritesNothingAndOldThemesLoadWithIt() {
        val text = ThemeCodec.encodeList(listOf(custom()))
        assertFalse(text.contains("\"kr\""))
        assertFalse(text.contains("\"kb\""))
        assertFalse(text.contains("\"ks\""))
        val back = ThemeCodec.decodeList(text).single()
        assertEquals(ThemeSpec.DEFAULT_ROUNDNESS, back.keyRoundness)
        assertFalse(back.keyBorder)
        assertFalse(back.keyShadow)
    }

    @Test
    fun aSquareThemeIsWrittenAsZeroNotForgotten() {
        val back = ThemeCodec.decodeList(ThemeCodec.encodeList(listOf(custom().copy(keyRoundness = 0)))).single()
        assertEquals(0, back.keyRoundness)
    }

    @Test
    fun numbersAndFlagsFromDamagedTextStayInsideTheirLimits() {
        val text = ThemeCodec.encodeList(listOf(custom().copy(keyRoundness = 50, keyBorder = true)))
            .replace("\"kr\":50", "\"kr\":9999").replace("\"kb\":true", "\"kb\":\"yes\"")
        val back = ThemeCodec.decodeList(text).single()
        assertEquals(100, back.keyRoundness)
        assertFalse(back.keyBorder)
        val copy = custom().copy(keyRoundness = -4)
        assertEquals(0, copy.keyRoundness)
    }

    @Test
    fun theShapeTravelsWithAThemeSharedAsText() {
        val t = custom().copy(keyRoundness = 90, keyBorder = true, keyShadow = true)
        val back = ThemeShare.decode(ThemeShare.encode(t))!!
        assertEquals(90, back.keyRoundness)
        assertTrue(back.keyBorder)
        assertTrue(back.keyShadow)
    }

    @Test
    fun copyingAThemeKeepsItsShape() {
        val t = custom().copy(keyRoundness = 10, keyBorder = true)
        val c = t.copy(name = "Other")
        assertEquals(10, c.keyRoundness)
        assertTrue(c.keyBorder)
        assertFalse(c.keyShadow)
    }
}
