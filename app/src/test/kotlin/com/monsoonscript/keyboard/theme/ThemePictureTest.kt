package com.monsoonscript.keyboard.theme

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ThemePictureTest {
    private fun custom(picture: String? = null, fade: Int = 35, keys: Int = 85) =
        Themes.DARK.copy(id = "custom:pic1", name = "Pic", picture = picture, pictureFade = fade, keyOpacity = keys)

    @Test
    fun aPictureRoundTripsThroughTheSavedText() {
        val back = ThemeCodec.decodeList(ThemeCodec.encodeList(listOf(custom("pabc123.jpg", 50, 60)))).single()
        assertEquals("pabc123.jpg", back.picture)
        assertEquals(50, back.pictureFade)
        assertEquals(60, back.keyOpacity)
    }

    @Test
    fun aThemeWithoutAPictureWritesNoPictureFields() {
        val text = ThemeCodec.encodeList(listOf(custom(null)))
        assertFalse(text.contains("\"pic\""))
        assertNull(ThemeCodec.decodeList(text).single().picture)
    }

    @Test
    fun anOldSavedThemeWithoutThePictureFieldsStillLoads() {
        val old = "{\"v\":1,\"themes\":[{\"id\":\"custom:old\",\"name\":\"Old\",\"bg\":\"#102030\",\"key\":\"#203040\",\"special\":\"#304050\",\"accent\":\"#FFD600\",\"text\":\"#FFFFFF\"}]}"
        val t = ThemeCodec.decodeList(old).single()
        assertNull(t.picture)
        assertEquals(ThemeSpec.DEFAULT_FADE, t.pictureFade)
        assertEquals(ThemeSpec.DEFAULT_KEY_OPACITY, t.keyOpacity)
    }

    @Test
    fun aPictureNameThatIsAPathIsIgnored() {
        for (bad in listOf("../secret.jpg", "/etc/passwd", "a/b.jpg", "x.png", ".jpg", "", "a b.jpg", "a".repeat(60) + ".jpg")) {
            val text = ThemeCodec.encodeList(listOf(custom("pok.jpg"))).replace("pok.jpg", bad.replace("\\", "\\\\"))
            assertNull("name <$bad> must be dropped", ThemeCodec.decodeList(text).single().picture)
        }
    }

    @Test
    fun numbersStayInsideTheirLimits() {
        val text = ThemeCodec.encodeList(listOf(custom("p1.jpg"))).replace("\"fade\":35", "\"fade\":500").replace("\"keys\":85", "\"keys\":-9")
        val t = ThemeCodec.decodeList(text).single()
        assertEquals(ThemeSpec.MAX_FADE, t.pictureFade)
        assertEquals(ThemeSpec.MIN_KEY_OPACITY, t.keyOpacity)
        val c = custom("p1.jpg").copy(pictureFade = 200, keyOpacity = 1)
        assertEquals(ThemeSpec.MAX_FADE, c.pictureFade)
        assertEquals(ThemeSpec.MIN_KEY_OPACITY, c.keyOpacity)
    }

    @Test
    fun copyKeepsAndCanDropThePicture() {
        val t = custom("p1.jpg", 40, 70)
        assertEquals("p1.jpg", t.copy(name = "Other").picture)
        assertTrue(t.hasPicture)
        assertNull(t.copy(picture = null).picture)
        assertFalse(t.copy(picture = null).hasPicture)
    }

    @Test
    fun fileNamesAreChecked() {
        assertTrue(PictureMath.isValidFileName("pk3j9a1234.jpg"))
        assertFalse(PictureMath.isValidFileName(null))
        assertFalse(PictureMath.isValidFileName("a.jpg.exe"))
        assertFalse(PictureMath.isValidFileName("..jpg"))
    }

    @Test
    fun bigPhotosAreDecodedSmallerAndNeverEnlarged() {
        assertEquals(1, PictureMath.sampleSize(1000, 800))
        assertEquals(1, PictureMath.sampleSize(3199, 2000))
        assertEquals(2, PictureMath.sampleSize(3200, 2400))
        assertEquals(4, PictureMath.sampleSize(6400, 4800))
        assertEquals(800 to 600, PictureMath.scaledSize(800, 600))
        assertEquals(1600 to 1200, PictureMath.scaledSize(4000, 3000))
        assertEquals(900 to 1600, PictureMath.scaledSize(2250, 4000))
    }

    @Test
    fun theCropFillsTheViewAndStaysInTheMiddle() {
        // a wide picture in a squarer view: full height, sides cut
        assertEquals(listOf(300, 0, 700, 400), PictureMath.cropRect(1000, 400, 400, 400).toList())
        // a tall picture in a wide view: full width, top and bottom cut
        assertEquals(listOf(0, 300, 1000, 700), PictureMath.cropRect(1000, 1000, 1000, 400).toList())
        // the same shape: the whole picture
        assertEquals(listOf(0, 0, 1600, 900), PictureMath.cropRect(1600, 900, 1280, 720).toList())
        // nonsense sizes never crash
        assertEquals(4, PictureMath.cropRect(0, 0, 10, 10).size)
    }

    @Test
    fun theFadeIsAVeilAlpha() {
        assertEquals(0, PictureMath.veilAlpha(0))
        assertEquals(255 * 90 / 100, PictureMath.veilAlpha(90) - (if (PictureMath.veilAlpha(90) > 255 * 90 / 100) 1 else 0))
        assertEquals(PictureMath.veilAlpha(ThemeSpec.MAX_FADE), PictureMath.veilAlpha(1000))
    }

    @Test
    fun seeThroughKeepsTheColorAndSetsTheAlpha() {
        val c = ColorMath.withAlpha(0xFF336699.toInt(), 50)
        assertEquals(0x336699, c and 0xFFFFFF)
        assertEquals(128, c ushr 24)
        assertEquals(0xFF, ColorMath.withAlpha(0x123456, 100) ushr 24)
        assertEquals(0, ColorMath.withAlpha(0x123456, 0) ushr 24)
        assertEquals(0xFF, ColorMath.withAlpha(0x123456, 500) ushr 24)
    }
}
