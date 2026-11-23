package com.monsoonscript.keyboard.theme

import com.monsoonscript.keyboard.view.KeyboardView

/** The colors in the form the keyboard view and the panels draw with. */
fun ThemeColors.toPalette(): KeyboardView.Palette =
    KeyboardView.Palette(bg, key, keyPressed, special, specialPressed, accent, accentPressed, text, textMuted, onAccent, bubble)
