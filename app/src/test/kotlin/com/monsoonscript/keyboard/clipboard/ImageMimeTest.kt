package com.monsoonscript.keyboard.clipboard

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ImageMimeTest {

    @Test
    fun extensionsAndTypesRoundTrip() {
        assertEquals("png", ImageMime.extFor("image/png"))
        assertEquals("jpg", ImageMime.extFor("IMAGE/JPEG"))
        assertEquals("img", ImageMime.extFor("image/x-weird"))
        assertEquals("image/jpeg", ImageMime.mimeFor("jpg"))
        assertEquals("image/gif", ImageMime.mimeFor("GIF"))
        assertEquals("image/*", ImageMime.mimeFor("img"))
    }

    @Test
    fun labelsForTheCard() {
        assertEquals("PNG", ImageMime.label("image/png"))
        assertEquals("JPEG", ImageMime.label("image/jpeg"))
        assertEquals("WEBP", ImageMime.label("image/webp"))
        assertEquals("IMAGE", ImageMime.label(null))
        assertEquals("IMAGE", ImageMime.label("image/*"))
    }

    @Test
    fun editorPatternsCoverTheTypesTheyShould() {
        assertTrue(ImageMime.matches("image/*", "image/png"))
        assertTrue(ImageMime.matches("image/png", "image/png"))
        assertTrue(ImageMime.matches("IMAGE/PNG", "image/png"))
        assertTrue(ImageMime.matches("*/*", "image/gif"))
        assertTrue(ImageMime.matches("*", "image/gif"))
        assertFalse(ImageMime.matches("image/png", "image/gif"))
        assertFalse(ImageMime.matches("text/*", "image/png"))
        assertFalse(ImageMime.matches("", "image/png"))
        assertFalse(ImageMime.matches("image/*", ""))
    }

    @Test
    fun anEditorWithNoListTakesNoImages() {
        assertFalse(ImageMime.accepted(null, "image/png"))
        assertFalse(ImageMime.accepted(arrayOf(), "image/png"))
        assertTrue(ImageMime.accepted(arrayOf("text/plain", "image/gif", "image/png"), "image/png"))
        assertFalse(ImageMime.accepted(arrayOf("image/gif"), "image/png"))
    }

    @Test
    fun onlyOurOwnFileNamesAreServed() {
        val hash = "0123456789abcdef".repeat(4)
        assertTrue(ImageMime.isStoredName("$hash.png"))
        assertTrue(ImageMime.isStoredName("$hash.img"))
        assertFalse(ImageMime.isStoredName("$hash.png.t")) // the thumbnail is never handed out
        assertFalse(ImageMime.isStoredName(hash))
        assertFalse(ImageMime.isStoredName("$hash."))
        assertFalse(ImageMime.isStoredName("../$hash.png"))
        assertFalse(ImageMime.isStoredName(hash.uppercase() + ".png"))
        assertFalse(ImageMime.isStoredName(hash.substring(1) + ".png"))
        assertFalse(ImageMime.isStoredName("$hash.pn g"))
        assertFalse(ImageMime.isStoredName("$hash.toolong"))
        assertFalse(ImageMime.isStoredName("tmp-12345"))
    }
}
