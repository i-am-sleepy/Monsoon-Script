package com.monsoonscript.keyboard.theme

import android.content.Context
import com.monsoonscript.keyboard.view.Backdrop
import com.monsoonscript.keyboard.view.KeyboardView

/**
 * The colors in the form the keyboard view and the panels draw with. With a [backdrop] (a background picture) the letter and special keys become see-through by
 * [keyOpacity] % (pressed keys a bit more solid, so a press can still be seen); the accent colors (Enter, selections) and the bubble stay solid. [roundness] / [border] /
 * [shadow] are the key shape of the theme (L10 step 4, D94).
 */
fun ThemeColors.toPalette(backdrop: Backdrop? = null, keyOpacity: Int = 100, roundness: Int = ThemeSpec.DEFAULT_ROUNDNESS, border: Boolean = false, shadow: Boolean = false): KeyboardView.Palette {
    val pressedOpacity = keyOpacity + (100 - keyOpacity) / 2
    fun see(c: Int, percent: Int) = if (backdrop == null || percent >= 100) c else ColorMath.withAlpha(c, percent)
    return KeyboardView.Palette(
        bg, see(key, keyOpacity), see(keyPressed, pressedOpacity), see(special, keyOpacity), see(specialPressed, pressedOpacity),
        accent, accentPressed, text, textMuted, onAccent, bubble, backdrop,
        ThemeSpec.cornerDp(roundness), if (border) (text and 0x00FFFFFF) or 0x66000000 else 0, shadow,
    )
}

/** The palette of this theme, with its background picture if it has one (L10 step 2, D87). */
fun ThemeSpec.palette(context: Context): KeyboardView.Palette =
    colors().toPalette(ThemePictures.backdrop(context, this), keyOpacity, keyRoundness, keyBorder, keyShadow)
