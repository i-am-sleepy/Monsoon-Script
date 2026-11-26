package com.monsoonscript.keyboard.theme

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Base64

class ThemeShareTest {
    private val mine = Themes.DARK.copy(id = "custom:mine", name = "Monsoon night", accent = 0xFF00E5FF.toInt(), picture = "pabc.jpg", cropPortrait = PictureCrop(200, 10, 90))

    private fun code(json: String) = ThemeShare.PREFIX + Base64.getUrlEncoder().withoutPadding().encodeToString(json.toByteArray())

    @Test
    fun aThemeSurvivesTheTripThroughText() {
        val back = ThemeShare.decode(ThemeShare.encode(mine))!!
        assertEquals("Monsoon night", back.name)
        assertEquals(mine.bg, back.bg)
        assertEquals(mine.key, back.key)
        assertEquals(mine.special, back.special)
        assertEquals(0xFF00E5FF.toInt(), back.accent)
        assertEquals(mine.text, back.text)
    }

    @Test
    fun overridesAreKept() {
        val withOverride = mine.copy(overrides = mapOf(Slot.KEY_PRESSED to 0xFF123456.toInt()))
        val back = ThemeShare.decode(ThemeShare.encode(withOverride))!!
        assertEquals(0xFF123456.toInt(), back.overrides[Slot.KEY_PRESSED])
    }

    @Test
    fun theLineIsPlainTextOnOneLine() {
        val line = ThemeShare.encode(mine)
        assertTrue(line.startsWith(ThemeShare.PREFIX))
        assertTrue(line.all { it.isLetterOrDigit() || it == ':' || it == '-' || it == '_' })
        assertTrue("short enough for a chat: ${line.length}", line.length < 900)
    }

    @Test
    fun noPictureAndNoPlacementTravel() {
        val back = ThemeShare.decode(ThemeShare.encode(mine))!!
        assertNull(back.picture)
        assertTrue(back.cropPortrait.isAuto)
        assertTrue(back.cropLandscape.isAuto)
        assertFalse_(ThemeShare.encode(mine).contains("pabc"))
    }

    private fun assertFalse_(b: Boolean) = assertTrue(!b)

    @Test
    fun theWholeMessageCanBePasted() {
        val message = "My Monsoon Script keyboard theme “Monsoon night”.\nTo use it: open the app.\n\n${ThemeShare.encode(mine)}\n\nenjoy!"
        assertEquals("Monsoon night", ThemeShare.decode(message)!!.name)
    }

    @Test
    fun damagedOrMissingTextGivesNullAndNeverThrows() {
        assertNull(ThemeShare.decode(null))
        assertNull(ThemeShare.decode(""))
        assertNull(ThemeShare.decode("   "))
        assertNull(ThemeShare.decode("just words"))
        assertNull(ThemeShare.decode("MST1:"))
        assertNull(ThemeShare.decode("MST1:!!!!!!!!!!"))
        assertNull(ThemeShare.decode("MST1:AAAAAAAAAAAAAAAA")) // valid letters, no theme inside
        assertNull(ThemeShare.decode(code("not json at all")))
        assertNull(ThemeShare.decode(code("{\"v\":1,\"themes\":[]}")))
        assertNull(ThemeShare.decode("MST1:" + "A".repeat(50000)))
        val cut = ThemeShare.encode(mine).dropLast(40)
        assertNull(ThemeShare.decode(cut))
    }

    @Test
    fun aTextThatTriesToBeABuiltInOrNamesAPathIsRefused() {
        val colors = "\"bg\":\"#102030\",\"key\":\"#203040\",\"special\":\"#304050\",\"accent\":\"#FFD600\",\"text\":\"#FFFFFF\""
        assertNull(ThemeShare.decode(code("{\"v\":1,\"themes\":[{\"id\":\"builtin:dark\",\"name\":\"x\",$colors}]}")))
        assertNull(ThemeShare.decode(code("{\"v\":1,\"themes\":[{\"id\":\"custom:a\",\"name\":\"x\",\"bg\":\"zzz\",\"key\":\"#203040\",\"special\":\"#304050\",\"accent\":\"#FFD600\",\"text\":\"#FFFFFF\"}]}")))
        val sneaky = ThemeShare.decode(code("{\"v\":1,\"themes\":[{\"id\":\"custom:a\",\"name\":\"x\",$colors,\"pic\":\"../../etc/passwd\"}]}"))
        assertNotNull(sneaky)
        assertNull(sneaky!!.picture)
    }

    @Test
    fun aHugeNameIsCut() {
        val colors = "\"bg\":\"#102030\",\"key\":\"#203040\",\"special\":\"#304050\",\"accent\":\"#FFD600\",\"text\":\"#FFFFFF\""
        val t = ThemeShare.decode(code("{\"v\":1,\"themes\":[{\"id\":\"custom:a\",\"name\":\"${"N".repeat(900)}\",$colors}]}"))!!
        assertTrue(t.name.length <= ThemeSpec.MAX_NAME)
    }

    @Test
    fun theWallpaperThemeIsReadOnlyLikeEveryReadyMadeTheme() {
        assertTrue((ThemeSpec.BUILTIN_PREFIX + "wallpaper").startsWith(ThemeSpec.BUILTIN_PREFIX))
        val like = Themes.DARK.copy(id = ThemeSpec.BUILTIN_PREFIX + "wallpaper", name = "Wallpaper colors")
        assertTrue(like.builtIn)
    }
}
