package com.monsoonscript.keyboard.theme

/** The 11 colors of the keyboard. The first five are the "main" ones the owner picks; the rest follow by themselves unless overridden (D75). */
enum class Slot(val main: Boolean) {
    BG(true), KEY(true), SPECIAL(true), ACCENT(true), TEXT(true),
    KEY_PRESSED(false), SPECIAL_PRESSED(false), ACCENT_PRESSED(false), TEXT_MUTED(false), ON_ACCENT(false), BUBBLE(false),
}

/** All 11 colors of a theme, resolved. */
class ThemeColors(
    val bg: Int, val key: Int, val keyPressed: Int, val special: Int, val specialPressed: Int, val accent: Int, val accentPressed: Int,
    val text: Int, val textMuted: Int, val onAccent: Int, val bubble: Int,
)

/** What may be wrong with the colors of a theme (a warning only: the owner is never stopped). */
enum class Warning { LETTERS_ON_KEYS, LETTERS_ON_SPECIAL, LETTERS_ON_BACKGROUND, ENTER_LABEL, KEYS_VS_BACKGROUND }

/**
 * A keyboard theme (L10, D75): [name], the five main colors and the overrides of the derived ones. Immutable: every change makes a new one.
 * [id] is "builtin:..." for the ready-made presets (read-only) and "custom:..." for the owner's own.
 */
class ThemeSpec(
    val id: String,
    val name: String,
    val bg: Int,
    val key: Int,
    val special: Int,
    val accent: Int,
    val text: Int,
    val overrides: Map<Slot, Int> = emptyMap(),
    /** The file name of the background picture (in [ThemePictures]' folder), or null for none (L10 step 2, D87). */
    val picture: String? = null,
    /** How much the picture is faded towards the theme's background color, in percent (0 = as it is, [MAX_FADE] = nearly hidden). */
    val pictureFade: Int = DEFAULT_FADE,
    /** How solid the keys are on top of the picture, in percent ([MIN_KEY_OPACITY]..100); it only matters while there is a picture. */
    val keyOpacity: Int = DEFAULT_KEY_OPACITY,
    /** Where the picture sits in the keyboard held upright / on its side (D90). */
    val cropPortrait: PictureCrop = PictureCrop.AUTO,
    val cropLandscape: PictureCrop = PictureCrop.AUTO,
) {
    val builtIn: Boolean get() = id.startsWith(BUILTIN_PREFIX)

    val hasPicture: Boolean get() = picture != null

    /** A light keyboard: its window and navigation bar get dark icons. */
    val isLight: Boolean get() = ColorMath.isLight(bg)

    fun isOverridden(slot: Slot): Boolean = overrides.containsKey(slot)

    /** The color of [slot]: the override if there is one, else what follows from the five main colors. */
    fun color(slot: Slot): Int = when (slot) {
        Slot.BG -> bg
        Slot.KEY -> key
        Slot.SPECIAL -> special
        Slot.ACCENT -> accent
        Slot.TEXT -> text
        else -> overrides[slot] ?: automatic(slot)
    }

    /** What the derived [slot] would be without an override (the main slots return their color). */
    fun automatic(slot: Slot): Int = when (slot) {
        Slot.KEY_PRESSED -> ColorMath.mix(color(Slot.KEY), text, 0.14f)
        Slot.SPECIAL_PRESSED -> ColorMath.mix(color(Slot.SPECIAL), text, 0.16f)
        Slot.ACCENT_PRESSED -> if (ColorMath.isLight(accent)) ColorMath.mix(accent, ColorMath.WHITE, 0.25f) else ColorMath.mix(accent, ColorMath.NEAR_BLACK, 0.22f)
        Slot.TEXT_MUTED -> ColorMath.mix(text, bg, 0.42f)
        Slot.ON_ACCENT -> ColorMath.readableOn(accent)
        Slot.BUBBLE -> if (isLight) key else color(Slot.KEY_PRESSED)
        else -> color(slot)
    }

    fun colors(): ThemeColors = ThemeColors(
        bg, key, color(Slot.KEY_PRESSED), special, color(Slot.SPECIAL_PRESSED), accent, color(Slot.ACCENT_PRESSED),
        text, color(Slot.TEXT_MUTED), color(Slot.ON_ACCENT), color(Slot.BUBBLE),
    )

    /** A main color changes (the derived ones follow, except those with an override); a derived color becomes an override. */
    fun withColor(slot: Slot, c: Int): ThemeSpec {
        val v = ColorMath.rgb(ColorMath.red(c), ColorMath.green(c), ColorMath.blue(c))
        return when (slot) {
            Slot.BG -> copy(bg = v)
            Slot.KEY -> copy(key = v)
            Slot.SPECIAL -> copy(special = v)
            Slot.ACCENT -> copy(accent = v)
            Slot.TEXT -> copy(text = v)
            else -> copy(overrides = overrides + (slot to v))
        }
    }

    /** The derived [slot] follows the main colors again. */
    fun withAutomatic(slot: Slot): ThemeSpec = copy(overrides = overrides - slot)

    fun clearOverrides(): ThemeSpec = copy(overrides = emptyMap())

    fun copy(
        id: String = this.id, name: String = this.name, bg: Int = this.bg, key: Int = this.key, special: Int = this.special,
        accent: Int = this.accent, text: Int = this.text, overrides: Map<Slot, Int> = this.overrides,
        picture: String? = this.picture, pictureFade: Int = this.pictureFade, keyOpacity: Int = this.keyOpacity,
        cropPortrait: PictureCrop = this.cropPortrait, cropLandscape: PictureCrop = this.cropLandscape,
    ): ThemeSpec = ThemeSpec(
        id, name, bg, key, special, accent, text, overrides, picture,
        pictureFade.coerceIn(0, MAX_FADE), keyOpacity.coerceIn(MIN_KEY_OPACITY, 100), cropPortrait, cropLandscape,
    )

    /** Colors that are hard to read together (D75: shown as a note, never blocked). */
    fun warnings(): List<Warning> {
        val out = ArrayList<Warning>()
        if (ColorMath.contrast(text, key) < MIN_TEXT_CONTRAST) out += Warning.LETTERS_ON_KEYS
        if (ColorMath.contrast(text, special) < MIN_TEXT_CONTRAST) out += Warning.LETTERS_ON_SPECIAL
        if (ColorMath.contrast(text, bg) < MIN_TEXT_CONTRAST) out += Warning.LETTERS_ON_BACKGROUND
        if (ColorMath.contrast(color(Slot.ON_ACCENT), accent) < MIN_TEXT_CONTRAST) out += Warning.ENTER_LABEL
        if (ColorMath.contrast(key, bg) < MIN_KEY_CONTRAST && ColorMath.contrast(special, bg) < MIN_KEY_CONTRAST) out += Warning.KEYS_VS_BACKGROUND
        return out
    }

    companion object {
        const val BUILTIN_PREFIX = "builtin:"
        const val CUSTOM_PREFIX = "custom:"
        const val MIN_TEXT_CONTRAST = 3.0
        const val MIN_KEY_CONTRAST = 1.08
        const val MAX_NAME = 40
        const val DEFAULT_FADE = 35
        const val MAX_FADE = 90
        const val DEFAULT_KEY_OPACITY = 85
        const val MIN_KEY_OPACITY = 20
    }
}
