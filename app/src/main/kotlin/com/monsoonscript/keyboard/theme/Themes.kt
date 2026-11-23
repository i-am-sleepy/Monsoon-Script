package com.monsoonscript.keyboard.theme

/**
 * The ready-made themes (L10, D75): read-only starting points; the owner copies one to change it. [LIGHT] and [DARK] reproduce the original two palettes
 * of the keyboard EXACTLY (every one of the 11 colors is set), so nothing changes until the owner picks another theme; the others give the five main colors and let the
 * rest follow.
 */
object Themes {
    const val LIGHT_ID = "builtin:light"
    const val DARK_ID = "builtin:dark"

    private fun c(hex: String): Int = ColorMath.parseHex(hex) ?: error(hex)

    val LIGHT = ThemeSpec(
        LIGHT_ID, "Light", c("#E3E6EA"), c("#FFFFFF"), c("#C9CFD5"), c("#1B6B63"), c("#202124"),
        mapOf(
            Slot.KEY_PRESSED to c("#D3D8DD"), Slot.SPECIAL_PRESSED to c("#B4BBC2"), Slot.ACCENT_PRESSED to c("#155650"),
            Slot.TEXT_MUTED to c("#70757A"), Slot.ON_ACCENT to c("#FFFFFF"), Slot.BUBBLE to c("#FFFFFF"),
        ),
    )

    val DARK = ThemeSpec(
        DARK_ID, "Dark", c("#1B1C1E"), c("#3C3F43"), c("#2B2D30"), c("#80CBC4"), c("#E8EAED"),
        mapOf(
            Slot.KEY_PRESSED to c("#5A5E63"), Slot.SPECIAL_PRESSED to c("#46494D"), Slot.ACCENT_PRESSED to c("#A7DDD8"),
            Slot.TEXT_MUTED to c("#9AA0A6"), Slot.ON_ACCENT to c("#00302C"), Slot.BUBBLE to c("#5A5E63"),
        ),
    )

    private fun preset(id: String, name: String, bg: String, key: String, special: String, accent: String, text: String) =
        ThemeSpec(ThemeSpec.BUILTIN_PREFIX + id, name, c(bg), c(key), c(special), c(accent), c(text))

    val ALL: List<ThemeSpec> = listOf(
        LIGHT,
        DARK,
        preset("black", "Pure black", "#000000", "#1C1C1E", "#0E0E10", "#80CBC4", "#FFFFFF"),
        preset("midnight", "Midnight blue", "#0D1B2A", "#1F3048", "#16263B", "#4CC9F0", "#E4EBF2"),
        preset("forest", "Forest", "#14281D", "#2F4D3C", "#1F3A2B", "#95D5B2", "#E9F5EC"),
        preset("plum", "Plum", "#241530", "#3E2A52", "#2F1E40", "#E0A6F5", "#F3E9FA"),
        preset("rose", "Rose", "#F8E1E7", "#FFFFFF", "#EFC3CF", "#C2185B", "#3B1F2B"),
        preset("sand", "Sand", "#EDE0D0", "#FFF8EE", "#D9C5AE", "#B5651D", "#3A2E22"),
        preset("lavender", "Lavender", "#E6E0F5", "#FFFFFF", "#CFC6EA", "#6A4FC1", "#2A2147"),
        preset("sky", "Sky", "#DCEBF7", "#FFFFFF", "#BFD7EC", "#1565C0", "#12263A"),
        preset("contrast", "High contrast", "#000000", "#262626", "#4D4D4D", "#FFD600", "#FFFFFF"),
    )

    fun builtIn(id: String): ThemeSpec? = ALL.firstOrNull { it.id == id }
}
