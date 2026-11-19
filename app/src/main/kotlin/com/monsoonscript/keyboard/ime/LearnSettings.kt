package com.monsoonscript.keyboard.ime

import android.content.Context
import android.content.SharedPreferences
import com.monsoonscript.keyboard.prefs.Prefs

/**
 * The two settings of "learn from my typing" (D42), read by the IME and written by the settings screen:
 *  - [KEY_ENABLED] (default ON): the keyboard remembers the words and word pairs the owner types, on the phone only.
 *  - [clearLearnedWords]: forget everything learned so far.
 * The names come from [Prefs] (one preferences file for all settings).
 */
object LearnSettings {
    const val PREFS = Prefs.NAME
    const val KEY_ENABLED = Prefs.LEARN
    const val KEY_CLEAR_EPOCH = Prefs.CLEAR_EPOCH

    private fun prefs(context: Context): SharedPreferences = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun enabled(context: Context): Boolean = prefs(context).getBoolean(KEY_ENABLED, true)

    fun setEnabled(context: Context, on: Boolean) {
        prefs(context).edit().putBoolean(KEY_ENABLED, on).apply()
    }

    /**
     * Forget all learned words and pairs. Works whether or not the keyboard service is running: the database is emptied here, and a
     * running keyboard hears the epoch change (see `MonsoonIme`) and empties its copy in memory too.
     */
    fun clearLearnedWords(context: Context) {
        val p = prefs(context)
        p.edit().putInt(KEY_CLEAR_EPOCH, p.getInt(KEY_CLEAR_EPOCH, 0) + 1).apply()
        UserStore.deleteAll(context)
    }
}
