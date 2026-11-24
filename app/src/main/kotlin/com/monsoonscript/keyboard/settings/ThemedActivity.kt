package com.monsoonscript.keyboard.settings

import android.app.Activity
import android.content.SharedPreferences
import android.os.Bundle
import com.monsoonscript.keyboard.prefs.Prefs

/**
 * The base of every screen of the app (L12, D83): it paints the screen with the keyboard theme in use ([colors], the window, the bars) before anything is built,
 * and builds the screen again when the theme changed while it was in the background. A screen that must follow a theme change at once, while it is open, overrides
 * [liveRecolor] (the themes list does; the theme editor does not, it would lose what is being edited).
 */
abstract class ThemedActivity : Activity() {
    internal lateinit var colors: AppColors
    private var shownSignature = ""
    private val themeListener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ -> if (liveRecolor) recolorIfChanged() }

    protected open val liveRecolor: Boolean get() = false

    override fun onCreate(savedInstanceState: Bundle?) {
        colors = AppStyle.colors(this)
        shownSignature = colors.signature
        setTheme(AppStyle.systemTheme(colors))
        super.onCreate(savedInstanceState)
    }

    override fun onPostCreate(savedInstanceState: Bundle?) {
        super.onPostCreate(savedInstanceState)
        AppStyle.applyWindow(this, colors)
    }

    override fun onResume() {
        super.onResume()
        Prefs.of(this).registerOnSharedPreferenceChangeListener(themeListener)
        recolorIfChanged()
    }

    override fun onPause() {
        Prefs.of(this).unregisterOnSharedPreferenceChangeListener(themeListener)
        super.onPause()
    }

    private fun recolorIfChanged() {
        if (AppStyle.colors(this).signature != shownSignature) recreate()
    }
}
