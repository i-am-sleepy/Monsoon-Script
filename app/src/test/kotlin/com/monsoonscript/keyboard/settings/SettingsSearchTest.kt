package com.monsoonscript.keyboard.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SettingsSearchTest {
    @Test
    fun emptyQueryMatchesNothing() {
        assertFalse(SettingsSearch.matches("", "Vibration"))
        assertFalse(SettingsSearch.matches("   ", "Vibration"))
    }

    @Test
    fun caseAndOrderDoNotMatter() {
        assertTrue(SettingsSearch.matches("VIBRA", "Vibration", "Strength of the buzz"))
        assertTrue(SettingsSearch.matches("buzz vibr", "Vibration", "Strength of the buzz"))
    }

    @Test
    fun everyWordMustBeFound() {
        assertTrue(SettingsSearch.matches("space drag", "Drag on the space bar", ""))
        assertFalse(SettingsSearch.matches("space volume", "Drag on the space bar", "move the cursor"))
    }

    @Test
    fun wordsAreSplitOnAnySpaces() {
        assertEquals(listOf("a", "b"), SettingsSearch.words("  A \t B  "))
    }
}
