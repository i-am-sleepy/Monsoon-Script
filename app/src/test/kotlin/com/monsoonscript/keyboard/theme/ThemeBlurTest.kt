package com.monsoonscript.keyboard.theme

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ThemeBlurTest {
    private fun custom() = Themes.DARK.copy(id = "custom:blur1", name = "Blur", picture = "pabc.jpg")

    private fun px(a: Int, r: Int, g: Int, b: Int) = (a shl 24) or (r shl 16) or (g shl 8) or b

    @Test
    fun theRadiusFollowsTheIntensity() {
        assertEquals(0, PictureBlur.radius(0))
        assertEquals(PictureBlur.MAX_RADIUS, PictureBlur.radius(100))
        assertEquals(PictureBlur.MAX_RADIUS / 2, PictureBlur.radius(50))
        assertEquals(PictureBlur.MAX_RADIUS, PictureBlur.radius(900))
        assertEquals(0, PictureBlur.radius(-3))
        assertTrue(PictureBlur.radius(1) <= PictureBlur.radius(2))
    }

    @Test
    fun noRadiusLeavesThePictureAlone() {
        val a = IntArray(16) { px(255, it * 10, it * 5, 7) }
        val copy = a.copyOf()
        PictureBlur.blurInPlace(a, 4, 4, 0)
        assertTrue(a.contentEquals(copy))
    }

    @Test
    fun aFlatPictureStaysFlatAndOpaque() {
        val a = IntArray(30 * 20) { px(255, 40, 80, 120) }
        PictureBlur.blurInPlace(a, 30, 20, 5)
        for (p in a) assertEquals(px(255, 40, 80, 120), p)
    }

    @Test
    fun aSharpPatternIsSmoothedOut() {
        val w = 40
        val h = 40
        val a = IntArray(w * h) { i -> if ((i % w + i / w) % 2 == 0) px(255, 0, 0, 0) else px(255, 255, 255, 255) }
        PictureBlur.blurInPlace(a, w, h, 3)
        var lo = 255
        var hi = 0
        for (i in a.indices) {
            val v = (a[i] shr 8) and 0xFF
            if (v < lo) lo = v
            if (v > hi) hi = v
        }
        assertTrue("range after blur ${hi - lo}", hi - lo < 40)
    }

    @Test
    fun aBrightPatchSpreadsAndKeepsAboutTheSameLight() {
        val w = 41
        val h = 41
        val a = IntArray(w * h) { px(255, 0, 0, 0) }
        for (y in 17..23) for (x in 17..23) a[y * w + x] = px(255, 255, 255, 255)
        PictureBlur.blurInPlace(a, w, h, 4)
        var sum = 0
        for (p in a) sum += (p shr 16) and 0xFF
        val before = 49 * 255
        assertTrue("light kept: $sum of $before", sum in before * 9 / 10..before * 11 / 10)
        assertTrue(((a[20 * w + 20] shr 16) and 0xFF) < 255)
        assertTrue(((a[20 * w + 26] shr 16) and 0xFF) > 0)
        assertEquals(255, a[0] ushr 24)
    }

    @Test
    fun tinyPicturesAndHugeRadiiNeverCrash() {
        PictureBlur.blurInPlace(IntArray(1) { px(255, 9, 9, 9) }, 1, 1, 14)
        val two = IntArray(2) { px(255, it * 100, 0, 0) }
        PictureBlur.blurInPlace(two, 2, 1, 14)
        PictureBlur.blurInPlace(IntArray(0), 0, 0, 5)
        PictureBlur.blurInPlace(IntArray(3), 2, 2, 5) // array too small: left alone
    }

    @Test
    fun theSmallWindowsAreBlurredByDefaultAndTheKeysAreNot() {
        val t = custom()
        assertEquals(0, t.blurMain)
        assertEquals(ThemeSpec.DEFAULT_PANEL_BLUR, t.blurPanels)
        assertTrue(t.blurPanels > 0)
        assertTrue(t.panelCropPortrait.isAuto)
        assertTrue(t.panelCropLandscape.isAuto)
    }

    @Test
    fun blurAndSmallWindowPlacementRoundTrip() {
        val t = custom().copy(blurMain = 35, blurPanels = 80, panelCropPortrait = PictureCrop(150, 20, 60), panelCropLandscape = PictureCrop(220, 80, 30))
        val back = ThemeCodec.decodeList(ThemeCodec.encodeList(listOf(t))).single()
        assertEquals(35, back.blurMain)
        assertEquals(80, back.blurPanels)
        assertEquals(PictureCrop(150, 20, 60), back.panelCropPortrait)
        assertEquals(PictureCrop(220, 80, 30), back.panelCropLandscape)
    }

    @Test
    fun defaultsAreNotWrittenAndOldThemesLoadWithThem() {
        val text = ThemeCodec.encodeList(listOf(custom()))
        for (key in listOf("\"bm\"", "\"bp\"", "\"pp\"", "\"pl\"")) assertFalse(key, text.contains(key))
        val back = ThemeCodec.decodeList(text).single()
        assertEquals(0, back.blurMain)
        assertEquals(ThemeSpec.DEFAULT_PANEL_BLUR, back.blurPanels)
    }

    @Test
    fun aSmallWindowBlurOfZeroIsKept() {
        val back = ThemeCodec.decodeList(ThemeCodec.encodeList(listOf(custom().copy(blurPanels = 0)))).single()
        assertEquals(0, back.blurPanels)
    }

    @Test
    fun blurNumbersStayInsideTheirLimits() {
        val text = ThemeCodec.encodeList(listOf(custom().copy(blurMain = 10, blurPanels = 20))).replace("\"bm\":10", "\"bm\":900").replace("\"bp\":20", "\"bp\":-4")
        val back = ThemeCodec.decodeList(text).single()
        assertEquals(ThemeSpec.MAX_BLUR, back.blurMain)
        assertEquals(0, back.blurPanels)
        assertEquals(ThemeSpec.MAX_BLUR, custom().copy(blurMain = 5000).blurMain)
        assertEquals(0, custom().copy(blurPanels = -1).blurPanels)
    }

    @Test
    fun copyingAThemeKeepsBlurAndPlacement() {
        val t = custom().copy(blurMain = 40, panelCropPortrait = PictureCrop(130, 10, 90))
        val c = t.copy(name = "Other")
        assertEquals(40, c.blurMain)
        assertEquals(PictureCrop(130, 10, 90), c.panelCropPortrait)
    }
}
