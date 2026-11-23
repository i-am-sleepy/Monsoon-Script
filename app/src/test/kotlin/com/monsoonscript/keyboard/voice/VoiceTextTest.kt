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

    @Test
    fun spokenPunctuationBecomesMarks() {
        // "আমি যাই দাঁড়ি তুমি কেমন আছো প্রশ্নবোধক চিহ্ন" -> "আমি যাই। তুমি কেমন আছো?"
        val said = "\u0986\u09AE\u09BF \u09AF\u09BE\u0987 \u09A6\u09BE\u0981\u09A1\u09BC\u09BF \u09A4\u09C1\u09AE\u09BF \u0995\u09C7\u09AE\u09A8 \u0986\u099B\u09CB \u09AA\u09CD\u09B0\u09B6\u09CD\u09A8\u09AC\u09CB\u09A7\u0995 \u099A\u09BF\u09B9\u09CD\u09A8"
        val want = "\u0986\u09AE\u09BF \u09AF\u09BE\u0987\u0964 \u09A4\u09C1\u09AE\u09BF \u0995\u09C7\u09AE\u09A8 \u0986\u099B\u09CB?"
        assertEquals(want, VoiceText.clean(said, VoiceLang.BANGLA, punct = true))
    }

    @Test
    fun commaWordAndAutoDanda() {
        // "আমি ভাত কমা মাছ খাই" -> "আমি ভাত, মাছ খাই।"
        assertEquals("\u0986\u09AE\u09BF \u09AD\u09BE\u09A4, \u09AE\u09BE\u099B \u0996\u09BE\u0987\u0964", VoiceText.clean("\u0986\u09AE\u09BF \u09AD\u09BE\u09A4 \u0995\u09AE\u09BE \u09AE\u09BE\u099B \u0996\u09BE\u0987", VoiceLang.BANGLA, punct = true))
        // a word that merely contains the command letters is left alone
        assertEquals("\u09B8\u09AE\u0995\u09AE\u09BE\u09A8\u0964", VoiceText.clean("\u09B8\u09AE\u0995\u09AE\u09BE\u09A8", VoiceLang.BANGLA, punct = true))
    }

    @Test
    fun punctuationHelpIsOffByDefaultAndNeverTouchesEnglish() {
        assertEquals("hello", VoiceText.clean("hello", VoiceLang.ENGLISH, punct = true))
        assertEquals("\u0986\u09AE\u09BF", VoiceText.clean("\u0986\u09AE\u09BF", VoiceLang.BANGLA))
    }
}

