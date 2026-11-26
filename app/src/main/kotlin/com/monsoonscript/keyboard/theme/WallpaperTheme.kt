package com.monsoonscript.keyboard.theme

import android.annotation.TargetApi
import android.content.Context
import android.os.Build
import com.monsoonscript.keyboard.R

/**
 * The live "Wallpaper" theme (L10 step 3, D91): a ready-made theme (Android 12 and newer only) whose colors are the system's own palette made from the wallpaper
 * ("Material You"), light for the day slot and dark for the night slot. It is read from the system every time (the keyboard asks each time it opens), so it follows
 * the wallpaper without anything being saved. Like every ready-made theme it is read-only; "Copy" makes a normal theme with today's colors.
 */
object WallpaperTheme {
    const val ID = ThemeSpec.BUILTIN_PREFIX + "wallpaper"

    fun available(): Boolean = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

    /** The theme for the day slot ([night] false, light) or the night slot (true, dark), or null before Android 12 or when the system gives no palette. */
    fun spec(context: Context, night: Boolean): ThemeSpec? {
        if (!available()) return null
        return try {
            read(context, night)
        } catch (e: RuntimeException) { // a phone without the palette resources: the theme is simply not offered
            null
        }
    }

    @TargetApi(Build.VERSION_CODES.S)
    private fun read(context: Context, night: Boolean): ThemeSpec {
        fun c(id: Int): Int = context.getColor(id) or 0xFF000000.toInt()
        val name = context.getString(R.string.themes_wallpaper_name)
        // the system palette names its steps 0..1000 from light to dark: 50 = tone 95, 100 = 90, 200 = 80, 600 = 40, 700 = 30, 800 = 20, 900 = 10
        return if (!night) {
            fromColors(name, c(android.R.color.system_neutral2_100), c(android.R.color.system_neutral2_10), c(android.R.color.system_accent2_200),
                c(android.R.color.system_accent1_600), c(android.R.color.system_neutral1_900))
        } else {
            fromColors(name, c(android.R.color.system_neutral1_900), c(android.R.color.system_neutral1_800), c(android.R.color.system_accent2_700),
                c(android.R.color.system_accent1_200), c(android.R.color.system_neutral1_100))
        }
    }

    fun fromColors(name: String, bg: Int, key: Int, special: Int, accent: Int, text: Int): ThemeSpec = ThemeSpec(ID, name, bg, key, special, accent, text)
}
