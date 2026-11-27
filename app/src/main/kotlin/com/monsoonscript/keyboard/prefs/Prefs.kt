package com.monsoonscript.keyboard.prefs

import android.content.Context
import android.content.SharedPreferences
import com.monsoonscript.keyboard.lang.Language
import com.monsoonscript.keyboard.view.SpaceDrag

/**
 * The keyboard's saved settings: ONE SharedPreferences file read by the keyboard service and written by the settings screens (same process, so
 * the keyboard can listen for changes). Android caches the file in memory, so the getters are cheap; the keyboard reads them when an input
 * starts, never per key press. Keys and defaults live here, nowhere else.
 */
object Prefs {
    const val NAME = "monsoon_prefs"

    // languages (saved by enum NAME; an unknown name falls back, claude-e may add values to [Language])
    const val LANGUAGE = "language"
    const val LAST_BANGLA = "last_bangla"

    // typing
    const val LANGUAGE_KEY = "language_key" // default on (D15)
    const val SPACE_SWIPE = "space_swipe" // default on (D15)
    const val DIGITS = "typing_digits" // Int: 0 English digits typed in the Bangla layouts (default, D18), 1 Bangla digits (T11, D61)

    // keyboard behavior (G2, G3)
    const val AUTO_SHOW = "typing_auto_show" // default on: Android may open the keyboard by itself when a field gets focus
    const val VOLUME_CURSOR = "typing_volume_cursor" // Int: 0 Off (default), 1 volume up = left / down = right, 2 up = right / down = left
    const val VOLUME_OFF = 0
    const val VOLUME_UP_LEFT = 1
    const val VOLUME_UP_RIGHT = 2

    // appearance (G1.5)
    const val THEME = "appearance_theme" // Int: 0 follow the phone, 1 light, 2 dark
    const val NUMBER_ROW = "appearance_number_row" // default off
    const val EMOJI_KEY = "appearance_emoji_key" // default on: a smiley key right of ?123 / ABC
    const val HEIGHT_PORTRAIT = "appearance_height_portrait" // percent of the standard key height
    const val HEIGHT_LANDSCAPE = "appearance_height_landscape"
    const val KEY_GAP = "appearance_key_gap" // dp between keys
    const val KEY_TEXT_SIZE = "appearance_key_text" // percent of the standard size of the letters on the keys (L10 step 4, D96)
    const val STRIP_TEXT_SIZE = "appearance_strip_text" // percent of the standard size of the text in the suggestion bar
    const val TEXT_SIZE_MIN = 80
    const val TEXT_SIZE_MAX = 130
    const val TEXT_SIZE_DEFAULT = 100
    const val HEIGHT_MIN = 70
    const val HEIGHT_MAX = 130
    const val HEIGHT_DEFAULT = 100
    const val GAP_MIN = 0
    const val GAP_MAX = 6
    const val GAP_DEFAULT = 3

    // typing feel (G1.4)
    const val VIBRATION = "typing_vibration" // Int: 0 Off, 1 Light, 2 Normal (Android's own key tap, as before G1.4), 3 Strong
    const val KEY_SOUND = "typing_key_sound" // default off
    const val SOUND_SOURCE = "typing_sound_source" // Int: 0 Android's click (needs system Touch sounds), 1 the keyboard's own click
    const val SOUND_ANDROID = 0
    const val SOUND_BUILTIN = 1
    const val LONG_PRESS = "typing_long_press" // Int: 0 Short, 1 Normal, 2 Long
    const val VIB_NORMAL = 2
    const val LONG_PRESS_NORMAL = 1

    // voice typing (G5, D66)
    const val VOICE_NOTICE_OK = "voice_notice_ok" // the one-time notice about Google's speech service was accepted
    const val VOICE_ENGINE_EN = "voice_engine_en" // Int: ENGINE_GOOGLE (default) or ENGINE_LOCAL, per language (D66)
    const val VOICE_ENGINE_BN = "voice_engine_bn"
    const val ENGINE_GOOGLE = 0
    const val ENGINE_LOCAL = 1
    const val VOICE_BN_PUNCT = "voice_bn_punct" // Bangla: understand spoken marks (দাঁড়ি, কমা ...) and end a sentence with a danda, default on
    const val VOICE_OFFLINE = "voice_offline_mode" // D10: never use the internet (blocks the model download and Google voice), default off

    // suggestion strip (G1.3): all default on
    const val SHOW_SUGGESTIONS = "suggest_show"
    const val NEXT_WORD = "suggest_next_word"
    const val TYPED_FIRST = "suggest_typed_first"
    const val CALC = "suggest_calc" // the calculator on the suggestion strip (L13, D99), on by default
    const val CALC_ANGLE = "suggest_calc_angle" // 0 = degrees (default), 1 = radians

    // space bar cursor drag (L9, D72): hold the space bar, then slide
    const val SPACE_DRAG = "typing_space_drag" // Boolean, default ON
    const val DRAG_DELAY = "typing_drag_delay" // Int, units of SpaceDrag.DELAY_UNIT_MS
    const val DRAG_SPEED = "typing_drag_speed" // Int 1 (slow) .. 10 (fast)

    // auto-correction (L8, D69): default OFF; English typing only
    const val AUTOCORRECT = "suggest_autocorrect"

    // suggestions and learning (D42)
    const val LEARN = "learn_enabled" // default on
    const val CLEAR_EPOCH = "learn_clear_epoch" // bumped by "Clear learned words"

    fun of(context: Context): SharedPreferences = context.applicationContext.getSharedPreferences(NAME, Context.MODE_PRIVATE)

    fun languageKey(context: Context): Boolean = of(context).getBoolean(LANGUAGE_KEY, true)

    fun spaceSwipe(context: Context): Boolean = of(context).getBoolean(SPACE_SWIPE, true)

    /** True when the digit keys of the Bangla layouts type ০..৯ and keep 0..9 as the long-press hint (D61). */
    fun banglaDigits(context: Context): Boolean = of(context).getInt(DIGITS, 0) == 1

    /** False = the keyboard opens only when the owner taps a field (G3). */
    fun autoShow(context: Context): Boolean = of(context).getBoolean(AUTO_SHOW, true)

    fun volumeCursor(context: Context): Int = of(context).getInt(VOLUME_CURSOR, VOLUME_OFF).coerceIn(VOLUME_OFF, VOLUME_UP_RIGHT)

    fun theme(context: Context): Int = of(context).getInt(THEME, 0).coerceIn(0, 2)

    fun numberRow(context: Context): Boolean = of(context).getBoolean(NUMBER_ROW, false)

    fun emojiKey(context: Context): Boolean = of(context).getBoolean(EMOJI_KEY, true)

    fun heightPortrait(context: Context): Int = of(context).getInt(HEIGHT_PORTRAIT, HEIGHT_DEFAULT).coerceIn(HEIGHT_MIN, HEIGHT_MAX)

    fun heightLandscape(context: Context): Int = of(context).getInt(HEIGHT_LANDSCAPE, HEIGHT_DEFAULT).coerceIn(HEIGHT_MIN, HEIGHT_MAX)

    fun keyTextSize(context: Context): Int = of(context).getInt(KEY_TEXT_SIZE, TEXT_SIZE_DEFAULT).coerceIn(TEXT_SIZE_MIN, TEXT_SIZE_MAX)

    fun stripTextSize(context: Context): Int = of(context).getInt(STRIP_TEXT_SIZE, TEXT_SIZE_DEFAULT).coerceIn(TEXT_SIZE_MIN, TEXT_SIZE_MAX)

    fun keyGap(context: Context): Int = of(context).getInt(KEY_GAP, GAP_DEFAULT).coerceIn(GAP_MIN, GAP_MAX)

    fun vibration(context: Context): Int = of(context).getInt(VIBRATION, VIB_NORMAL).coerceIn(0, 3)

    fun keySound(context: Context): Boolean = of(context).getBoolean(KEY_SOUND, false)

    fun soundSource(context: Context): Int = of(context).getInt(SOUND_SOURCE, SOUND_ANDROID).coerceIn(SOUND_ANDROID, SOUND_BUILTIN)

    /** How long a key is held before its long-press (hint character, globe menu) fires. */
    fun longPressMs(context: Context): Long = when (of(context).getInt(LONG_PRESS, LONG_PRESS_NORMAL)) {
        0 -> 220L
        2 -> 450L
        else -> 300L
    }

    fun showSuggestions(context: Context): Boolean = of(context).getBoolean(SHOW_SUGGESTIONS, true)

    fun nextWord(context: Context): Boolean = of(context).getBoolean(NEXT_WORD, true)

    fun typedFirst(context: Context): Boolean = of(context).getBoolean(TYPED_FIRST, true)

    fun calc(context: Context): Boolean = of(context).getBoolean(CALC, true)

    fun calcRadians(context: Context): Boolean = of(context).getInt(CALC_ANGLE, 0) == 1

    fun autoCorrect(context: Context): Boolean = of(context).getBoolean(AUTOCORRECT, false)

    fun spaceDrag(context: Context): Boolean = of(context).getBoolean(SPACE_DRAG, true)

    fun dragDelayMs(context: Context): Long = SpaceDrag.delayMs(of(context).getInt(DRAG_DELAY, SpaceDrag.DELAY_DEFAULT))

    fun dragStepDp(context: Context): Float = SpaceDrag.stepDp(of(context).getInt(DRAG_SPEED, SpaceDrag.SPEED_DEFAULT))

    /** The language the keyboard was last left in; English when nothing or an unknown name is saved. */
    fun language(context: Context): Language = byName(of(context).getString(LANGUAGE, null), Language.ENGLISH)

    /** The Bangla layout the language pill returns to; Avro when nothing usable is saved. */
    fun lastBangla(context: Context): Language {
        val l = byName(of(context).getString(LAST_BANGLA, null), Language.AVRO)
        return if (l.isBangla) l else Language.AVRO
    }

    fun saveLanguage(context: Context, language: Language, lastBangla: Language) {
        of(context).edit().putString(LANGUAGE, language.name).putString(LAST_BANGLA, lastBangla.name).apply()
    }

    private fun byName(name: String?, fallback: Language): Language = Language.values().firstOrNull { it.name == name } ?: fallback
}
