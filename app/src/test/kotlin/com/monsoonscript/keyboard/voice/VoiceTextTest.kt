package com.monsoonscript.keyboard.voice

import org.junit.Assert.assertEquals
import org.junit.Test

class VoiceTextTest {

    @Test
    fun englishOnlyTrimsAndCollapsesSpaces() {
        assertEquals("Hello , world", VoiceText.clean("  Hello ,   world \n", VoiceLang.ENGLISH))
    }

    @Test
    fun banglaDandaAndCommaStickToTheWord() {
        assertEquals("\u0986\u09AE\u09BF \u09AF\u09BE\u0987\u0964 \u0995\u09C7\u09A8, \u09AD\u09BE\u09B2\u09CB?", VoiceText.clean("\u0986\u09AE\u09BF \u09AF\u09BE\u0987 \u0964 \u0995\u09C7\u09A8 , \u09AD\u09BE\u09B2\u09CB ?", VoiceLang.BANGLA))
    }

    @Test
    fun banglaNuktaLettersBecomeSingleCodePoints() {
        // letter + nukta -> the single code point the keyboard types (D42)
        assertEquals("\u09DF \u09DC \u09DD", VoiceText.clean("\u09AF\u09BC \u09A1\u09BC \u09A2\u09BC", VoiceLang.BANGLA))
        // already single code points stay
        assertEquals("\u09DF", VoiceText.clean("\u09DF", VoiceLang.BANGLA))
    }

    @Test
    fun brackets() {
        assertEquals("(\u0995\u0996)", VoiceText.clean("( \u0995\u0996 )", VoiceLang.BANGLA))
    }
}
