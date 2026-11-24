package com.monsoonscript.keyboard.settings

import com.monsoonscript.keyboard.theme.ColorMath
import com.monsoonscript.keyboard.theme.Themes
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AppColorsTest {
    @Test
    fun everyReadyMadeThemeGivesVisibleCardsAndReadableText() {
        for (t in Themes.ALL) {
            val c = AppColors(t)
            assertTrue("${t.name}: card must stand out from the background", ColorMath.contrast(c.card, c.bg) >= AppColors.MIN_CARD_CONTRAST)
            assertTrue("${t.name}: text on the card", ColorMath.contrast(c.text, c.card) >= 3.0)
            assertTrue("${t.name}: text on the background", ColorMath.contrast(c.text, c.bg) >= 3.0)
            assertTrue("${t.name}: accent on the background", ColorMath.contrast(c.accent, c.bg) >= 3.0)
        }
    }

    @Test
    fun theDefaultIsHighContrast() {
        assertEquals("builtin:contrast", Themes.DEFAULT_ID)
        assertEquals(Themes.DEFAULT_ID, Themes.DEFAULT.id)
        val c = AppColors(Themes.DEFAULT)
        assertEquals(0xFF000000.toInt(), c.bg)
        assertEquals(0xFFFFFFFF.toInt(), c.text)
        assertEquals(0xFFFFD600.toInt(), c.accent)
        assertTrue(!c.isLight)
    }

    @Test
    fun aCardThatWouldVanishGetsAMixOfTextOverBackground() {
        val flat = Themes.DARK.copy(id = "custom:flat", key = Themes.DARK.bg)
        val c = AppColors(flat)
        assertTrue(flat.key != c.card)
        assertTrue(ColorMath.contrast(c.card, c.bg) >= AppColors.MIN_CARD_CONTRAST)
    }

    @Test
    fun theSignatureChangesWithTheColorsOnly() {
        val a = AppColors(Themes.DEFAULT).signature
        assertEquals(a, AppColors(Themes.DEFAULT).signature)
        assertTrue(a != AppColors(Themes.LIGHT).signature)
        assertTrue(a != AppColors(Themes.DEFAULT.copy(id = "custom:x", accent = 0xFF00FF00.toInt())).signature)
    }
}
