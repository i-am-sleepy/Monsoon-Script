package com.monsoonscript.keyboard.settings

import android.app.Activity
import android.content.Context
import android.content.res.Configuration
import android.graphics.drawable.ColorDrawable
import android.os.Build
import android.view.View
import android.view.Window
import android.view.WindowInsetsController
import android.view.WindowManager
import com.monsoonscript.keyboard.prefs.Prefs
import com.monsoonscript.keyboard.theme.ThemeStore

/**
 * Paints the app like the keyboard (L12, D83): which theme the app screens use (the keyboard's day or night theme, by the same "follow the phone / light /
 * dark" choice as the keyboard) and the window around them: its background, the status and navigation bars in the same color (no black strip at the top or
 * at a camera notch), dark or light bar icons, and room for a notch at the side in landscape.
 */
internal object AppStyle {
    /** The colors of the theme in use right now. */
    fun colors(context: Context): AppColors {
        val night = when (Prefs.theme(context)) {
            1 -> false
            2 -> true
            else -> (context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
        }
        return AppColors(if (night) ThemeStore.night(context) else ThemeStore.day(context))
    }

    /** The system theme that fits [colors] (dialogs, switches and the keyboard of the phone follow it). */
    fun systemTheme(colors: AppColors): Int =
        if (colors.isLight) android.R.style.Theme_Material_Light_NoActionBar else android.R.style.Theme_Material_NoActionBar

    fun applyWindow(activity: Activity, colors: AppColors) {
        val w = activity.window
        w.setBackgroundDrawable(ColorDrawable(colors.bg))
        w.statusBarColor = colors.bg
        w.navigationBarColor = colors.bg
        if (Build.VERSION.SDK_INT >= 28) {
            val lp = w.attributes
            lp.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            w.attributes = lp
        }
        if (Build.VERSION.SDK_INT >= 29) {
            w.isStatusBarContrastEnforced = false
            w.isNavigationBarContrastEnforced = false
        }
        lightBars(w, colors.isLight)
        keepClearOfCutout(w)
    }

    /** A light theme gets dark bar icons, a dark one light icons. */
    private fun lightBars(w: Window, light: Boolean) {
        if (Build.VERSION.SDK_INT >= 30) {
            val mask = WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS or WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS
            w.insetsController?.setSystemBarsAppearance(if (light) mask else 0, mask)
        } else {
            @Suppress("DEPRECATION")
            val flags = View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR or View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR
            @Suppress("DEPRECATION")
            w.decorView.systemUiVisibility = if (light) w.decorView.systemUiVisibility or flags else w.decorView.systemUiVisibility and flags.inv()
        }
    }

    /** With the window drawn into the cutout area, the content keeps clear of a camera notch at the left or right edge (landscape). */
    private fun keepClearOfCutout(w: Window) {
        if (Build.VERSION.SDK_INT < 28) return
        val decor = w.decorView
        val content = decor.findViewById<View>(android.R.id.content) ?: return
        decor.setOnApplyWindowInsetsListener { v, insets ->
            val result = v.onApplyWindowInsets(insets)
            val cut = insets.displayCutout
            content.setPadding(cut?.safeInsetLeft ?: 0, 0, cut?.safeInsetRight ?: 0, 0)
            result
        }
        decor.requestApplyInsets()
    }
}
