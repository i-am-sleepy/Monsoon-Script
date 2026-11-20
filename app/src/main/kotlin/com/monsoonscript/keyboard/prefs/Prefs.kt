package com.monsoonscript.keyboard.prefs

import android.content.Context
import android.content.SharedPreferences
import com.monsoonscript.keyboard.lang.Language

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

    // appearance (G1.5)
    const val THEME = "appearance_theme" // Int: 0 follow the phone, 1 light, 2 dark
    const val NUMBER_ROW = "appearance_number_row" // default off
    const val HEIGHT_PORTRAIT = "appearance_height_portrait" // percent of the standard key height
    const val HEIGHT_LANDSCAPE = "appearance_height_landscape"
    const val KEY_GAP = "appearance_key_gap" // dp between keys
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

    // suggestion strip (G1.3): all default on
    const val SHOW_SUGGESTIONS = "suggest_show"
    const val NEXT_WORD = "suggest_next_word"
    const val TYPED_FIRST = "suggest_typed_first"

    // suggestions and learning (D42)
    const val LEARN = "learn_enabled" // default on
    const val CLEAR_EPOCH = "learn_clear_epoch" // bumped by "Clear learned words"

    fun of(context: Context): SharedPreferences = context.applicationContext.getSharedPreferences(NAME, Context.MODE_PRIVATE)

    fun languageKey(context: Context): Boolean = of(context).getBoolean(LANGUAGE_KEY, true)

    fun spaceSwipe(context: Context): Boolean = of(context).getBoolean(SPACE_SWIPE, true)

    /** True when the digit keys of the Bangla layouts type ০..৯ and keep 0..9 as the long-press hint (D61). */
    fun banglaDigits(context: Context): Boolean = of(context).getInt(DIGITS, 0) == 1

    fun theme(context: Context): Int = of(context).getInt(THEME, 0).coerceIn(0, 2)

    fun numberRow(context: Context): Boolean = of(context).getBoolean(NUMBER_ROW, false)

    fun heightPortrait(context: Context): Int = of(context).getInt(HEIGHT_PORTRAIT, HEIGHT_DEFAULT).coerceIn(HEIGHT_MIN, HEIGHT_MAX)

    fun heightLandscape(context: Context): Int = of(context).getInt(HEIGHT_LANDSCAPE, HEIGHT_DEFAULT).coerceIn(HEIGHT_MIN, HEIGHT_MAX)

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
