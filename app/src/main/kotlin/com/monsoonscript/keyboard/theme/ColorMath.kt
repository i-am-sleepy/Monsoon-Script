package com.monsoonscript.keyboard.theme

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt

/** Colors as plain ARGB ints, without any Android class, so the theme logic is unit-tested (L10, D75). Every color made here is opaque. */
object ColorMath {
    fun red(c: Int): Int = (c shr 16) and 0xFF
    fun green(c: Int): Int = (c shr 8) and 0xFF
    fun blue(c: Int): Int = c and 0xFF
    fun rgb(r: Int, g: Int, b: Int): Int = (0xFF shl 24) or (r.coerceIn(0, 255) shl 16) or (g.coerceIn(0, 255) shl 8) or b.coerceIn(0, 255)

    /** [a] moved [t] (0..1) of the way to [b]. */
    /** [c] with a see-through of [percent] % opaque (100 = solid, 0 = invisible). */
    fun withAlpha(c: Int, percent: Int): Int = (((percent.coerceIn(0, 100) * 255 + 50) / 100) shl 24) or (c and 0x00FFFFFF)

    fun mix(a: Int, b: Int, t: Float): Int {
        val k = t.coerceIn(0f, 1f)
        return rgb(
            (red(a) + (red(b) - red(a)) * k).roundToInt(),
            (green(a) + (green(b) - green(a)) * k).roundToInt(),
            (blue(a) + (blue(b) - blue(a)) * k).roundToInt(),
        )
    }

    private fun channel(v: Int): Double {
        val s = v / 255.0
        return if (s <= 0.03928) s / 12.92 else ((s + 0.055) / 1.055).pow(2.4)
    }

    /** WCAG relative luminance, 0 (black) .. 1 (white). */
    fun luminance(c: Int): Double = 0.2126 * channel(red(c)) + 0.7152 * channel(green(c)) + 0.0722 * channel(blue(c))

    /** WCAG contrast ratio, 1 (same) .. 21 (black on white). */
    fun contrast(a: Int, b: Int): Double {
        val la = luminance(a)
        val lb = luminance(b)
        return (max(la, lb) + 0.05) / (min(la, lb) + 0.05)
    }

    /** A light color: dark letters read well on it (and the system bar icons should be dark). */
    fun isLight(c: Int): Boolean = luminance(c) > 0.30

    val NEAR_BLACK = rgb(0x10, 0x10, 0x10)
    const val WHITE = -1 // 0xFFFFFFFF

    /** Near-black or white, whichever reads better on [c]. */
    fun readableOn(c: Int): Int = if (contrast(c, NEAR_BLACK) >= contrast(c, WHITE)) NEAR_BLACK else WHITE

    /** [h] 0..360, [s] and [v] 0..1. */
    fun fromHsv(h: Float, s: Float, v: Float): Int {
        val hh = ((h % 360f) + 360f) % 360f / 60f
        val ss = s.coerceIn(0f, 1f)
        val vv = v.coerceIn(0f, 1f)
        val i = hh.toInt()
        val f = hh - i
        val p = vv * (1 - ss)
        val q = vv * (1 - ss * f)
        val t = vv * (1 - ss * (1 - f))
        val (r, g, b) = when (i) {
            0 -> Triple(vv, t, p)
            1 -> Triple(q, vv, p)
            2 -> Triple(p, vv, t)
            3 -> Triple(p, q, vv)
            4 -> Triple(t, p, vv)
            else -> Triple(vv, p, q)
        }
        return rgb((r * 255).roundToInt(), (g * 255).roundToInt(), (b * 255).roundToInt())
    }

    /** Hue 0..360, saturation 0..1, value 0..1. */
    fun toHsv(c: Int): FloatArray {
        val r = red(c) / 255f
        val g = green(c) / 255f
        val b = blue(c) / 255f
        val mx = max(r, max(g, b))
        val mn = min(r, min(g, b))
        val d = mx - mn
        val h = when {
            d == 0f -> 0f
            mx == r -> 60f * (((g - b) / d) % 6f)
            mx == g -> 60f * ((b - r) / d + 2f)
            else -> 60f * ((r - g) / d + 4f)
        }
        return floatArrayOf(if (h < 0) h + 360f else h, if (mx == 0f) 0f else d / mx, mx)
    }

    /** "#RRGGBB". */
    fun hex(c: Int): String = "#%02X%02X%02X".format(red(c), green(c), blue(c))

    /** "#RGB", "#RRGGBB" or the same without the "#"; null when it is not a color. */
    fun parseHex(text: String): Int? {
        var s = text.trim()
        if (s.startsWith("#")) s = s.substring(1)
        if (s.length == 3) s = "" + s[0] + s[0] + s[1] + s[1] + s[2] + s[2]
        if (s.length != 6 || !s.all { it in '0'..'9' || it in 'a'..'f' || it in 'A'..'F' }) return null
        val v = s.toInt(16)
        return rgb((v shr 16) and 0xFF, (v shr 8) and 0xFF, v and 0xFF)
    }

    /** Two colors the same up to rounding? */
    fun near(a: Int, b: Int): Boolean = abs(red(a) - red(b)) <= 1 && abs(green(a) - green(b)) <= 1 && abs(blue(a) - blue(b)) <= 1
}
