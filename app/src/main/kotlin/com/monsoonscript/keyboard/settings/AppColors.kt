package com.monsoonscript.keyboard.settings

import com.monsoonscript.keyboard.theme.ColorMath
import com.monsoonscript.keyboard.theme.Slot
import com.monsoonscript.keyboard.theme.ThemeSpec

/**
 * The colors of the app screens (L12, D83): they come from the keyboard theme in use ([spec]), so the app looks like the keyboard. Pure Int math (no Android
 * classes), so it is unit-tested; [AppStyle] picks the right theme for day or night and [SettingsUi] paints with these.
 */
class AppColors(val spec: ThemeSpec) {
    val isLight: Boolean = spec.isLight
    val bg: Int = spec.bg
    val text: Int = spec.text
    val muted: Int = spec.color(Slot.TEXT_MUTED)
    val accent: Int = spec.accent
    val onAccent: Int = spec.color(Slot.ON_ACCENT)

    /** The card color: the theme's key color, or a light mix of text over background when the key color would not stand out from the background. */
    val card: Int = if (ColorMath.contrast(spec.key, spec.bg) >= MIN_CARD_CONTRAST) spec.key else ColorMath.mix(spec.bg, spec.text, 0.10f)

    /** A thin outline around a card (keeps the cards visible on very dark or very light themes). */
    val cardStroke: Int = ColorMath.mix(card, text, 0.16f)
    val divider: Int = ColorMath.mix(card, text, 0.12f)

    /** The highlighted card (the setup card): the card color with some accent in it. */
    val highlight: Int = ColorMath.mix(card, accent, 0.22f)

    /** The small rounded square behind a row's icon. */
    val chip: Int = ColorMath.mix(card, accent, 0.20f)

    /** Touch feedback: the text color, see-through (alpha 0x2A). */
    val ripple: Int = (text and 0x00FFFFFF) or 0x2A000000

    /** Changes when anything the screens are painted with changes (the screens check it when they come back, to recolor themselves). */
    val signature: String = listOf(bg, card, text, muted, accent, onAccent).joinToString(",") + if (isLight) "L" else "D"

    companion object {
        const val MIN_CARD_CONTRAST = 1.08
    }
}
