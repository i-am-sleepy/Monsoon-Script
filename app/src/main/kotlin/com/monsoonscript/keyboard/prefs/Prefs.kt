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
