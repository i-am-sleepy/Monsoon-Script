package com.monsoonscript.keyboard.theme

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ThemeSpecTest {
    private fun c(hex: String) = ColorMath.parseHex(hex)!!

    /** The two palettes the keyboard had before themes existed: the presets must reproduce them exactly (D75). */
    @Test fun lightAndDarkAreExactlyTheOriginalPalettes() {
        val dark = Themes.DARK.colors()
        assertEquals(c("#1B1C1E"), dark.bg); assertEquals(c("#3C3F43"), dark.key); assertEquals(c("#5A5E63"), dark.keyPressed)
        assertEquals(c("#2B2D30"), dark.special); assertEquals(c("#46494D"), dark.specialPressed); assertEquals(c("#80CBC4"), dark.accent)
        assertEquals(c("#A7DDD8"), dark.accentPressed); assertEquals(c("#E8EAED"), dark.text); assertEquals(c("#9AA0A6"), dark.textMuted)
        assertEquals(c("#00302C"), dark.onAccent); assertEquals(c("#5A5E63"), dark.bubble)
        val light = Themes.LIGHT.colors()
        assertEquals(c("#E3E6EA"), light.bg); assertEquals(c("#FFFFFF"), light.key); assertEquals(c("#D3D8DD"), light.keyPressed)
        assertEquals(c("#C9CFD5"), light.special); assertEquals(c("#B4BBC2"), light.specialPressed); assertEquals(c("#1B6B63"), light.accent)
        assertEquals(c("#155650"), light.accentPressed); assertEquals(c("#202124"), light.text); assertEquals(c("#70757A"), light.textMuted)
        assertEquals(c("#FFFFFF"), light.onAccent); assertEquals(c("#FFFFFF"), light.bubble)
    }

    @Test fun lightIsLightAndDarkIsDark() {
        assertTrue(Themes.LIGHT.isLight)
        assertFalse(Themes.DARK.isLight)
    }

    @Test fun everyPresetHasAUniqueBuiltInIdAndReadableColors() {
        assertEquals(Themes.ALL.size, Themes.ALL.map { it.id }.toSet().size)
        for (t in Themes.ALL) {
            assertTrue(t.id, t.builtIn)
            assertNotNull(t.id, Themes.builtIn(t.id))
            assertTrue("${t.name}: ${t.warnings()}", t.warnings().isEmpty())
            assertTrue(t.name, t.name.length in 1..ThemeSpec.MAX_NAME)
        }
        assertNull(Themes.builtIn("builtin:nope"))
    }

    @Test fun derivedColorsFollowTheMainOnesUntilOverridden() {
        val t = Themes.ALL.first { it.id == "builtin:forest" }
        val auto = t.color(Slot.KEY_PRESSED)
        val changed = t.withColor(Slot.KEY, c("#336655"))
        assertTrue(changed.color(Slot.KEY_PRESSED) != auto) // follows the new key color
        val fixed = changed.withColor(Slot.KEY_PRESSED, c("#112233"))
        assertTrue(fixed.isOverridden(Slot.KEY_PRESSED))
        assertEquals(c("#112233"), fixed.color(Slot.KEY_PRESSED))
        assertEquals(c("#112233"), fixed.withColor(Slot.KEY, c("#445566")).color(Slot.KEY_PRESSED)) // an override stays
        val back = fixed.withAutomatic(Slot.KEY_PRESSED)
        assertFalse(back.isOverridden(Slot.KEY_PRESSED))
        assertEquals(back.automatic(Slot.KEY_PRESSED), back.color(Slot.KEY_PRESSED))
        assertTrue(fixed.clearOverrides().overrides.isEmpty())
    }

    @Test fun mainSlotsNeverBecomeOverrides() {
        val t = Themes.DARK.withColor(Slot.TEXT, c("#FFEEDD")).withColor(Slot.BG, c("#010203"))
        assertEquals(c("#FFEEDD"), t.text)
        assertEquals(c("#010203"), t.bg)
        assertFalse(t.overrides.containsKey(Slot.TEXT))
        assertFalse(t.overrides.containsKey(Slot.BG))
        for (s in Slot.values()) if (s.main) assertEquals(s.name, t.color(s), t.automatic(s))
    }

    @Test fun editsMakeNewThemesAndNeverChangeTheOld() {
        val t = Themes.LIGHT
        val e = t.withColor(Slot.ACCENT, c("#FF0000"))
        assertEquals(c("#1B6B63"), t.accent)
        assertEquals(c("#FF0000"), e.accent)
        assertEquals(t.id, e.id)
    }

    @Test fun badColorChoicesGetWarningsButAreAllowed() {
        val bad = Themes.DARK.copy(id = "custom:x", text = c("#3D4044")) // letters almost the color of the keys
        assertTrue(bad.warnings().contains(Warning.LETTERS_ON_KEYS))
        assertTrue(bad.warnings().contains(Warning.LETTERS_ON_BACKGROUND))
        val flat = Themes.DARK.copy(id = "custom:y", key = c("#1B1C1E"), special = c("#1B1C1E"))
        assertTrue(flat.warnings().contains(Warning.KEYS_VS_BACKGROUND))
        val badEnter = Themes.DARK.copy(id = "custom:z").withColor(Slot.ON_ACCENT, c("#80CBC4"))
        assertTrue(badEnter.warnings().contains(Warning.ENTER_LABEL))
    }

    @Test fun automaticEnterLabelIsAlwaysReadable() {
        for (hex in listOf("#000000", "#FFFFFF", "#FFD600", "#1B6B63", "#80CBC4", "#C2185B", "#808080", "#FF00FF")) {
            val t = Themes.LIGHT.copy(id = "custom:a", accent = c(hex), overrides = emptyMap())
            assertTrue(hex, ColorMath.contrast(t.color(Slot.ON_ACCENT), c(hex)) >= 3.0)
        }
    }
}
