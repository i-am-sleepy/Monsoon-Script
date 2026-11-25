package com.monsoonscript.keyboard.theme

import com.monsoonscript.keyboard.clipboard.MiniJson

/**
 * The saved form of the owner's own themes (L10, D75): text made with [MiniJson], so it is unit-tested without Android and a later "share a theme as text" (step 3)
 * can use the same format. Reading never throws: a damaged entry is skipped, a damaged file gives an empty list.
 */
object ThemeCodec {
    const val VERSION = 1
    const val MAX_THEMES = 60
    private const val MAX_ID = 64
    private const val DEFAULT_NAME = "My theme"

    private val SLOT_KEYS = mapOf(
        Slot.KEY_PRESSED to "keyPressed", Slot.SPECIAL_PRESSED to "specialPressed", Slot.ACCENT_PRESSED to "accentPressed",
        Slot.TEXT_MUTED to "textMuted", Slot.ON_ACCENT to "onAccent", Slot.BUBBLE to "bubble",
    )

    fun toMap(t: ThemeSpec): Map<String, Any?> {
        val m = LinkedHashMap<String, Any?>()
        m["id"] = t.id
        m["name"] = t.name
        m["bg"] = ColorMath.hex(t.bg)
        m["key"] = ColorMath.hex(t.key)
        m["special"] = ColorMath.hex(t.special)
        m["accent"] = ColorMath.hex(t.accent)
        m["text"] = ColorMath.hex(t.text)
        val o = LinkedHashMap<String, Any?>()
        for ((slot, key) in SLOT_KEYS) t.overrides[slot]?.let { o[key] = ColorMath.hex(it) }
        if (o.isNotEmpty()) m["o"] = o
        if (t.picture != null) {
            m["pic"] = t.picture
            m["fade"] = t.pictureFade
            m["keys"] = t.keyOpacity
        }
        return m
    }

    /** The theme in [m], or null when it is not a usable custom theme. */
    fun fromMap(m: Map<*, *>): ThemeSpec? {
        val id = m["id"] as? String ?: return null
        if (!id.startsWith(ThemeSpec.CUSTOM_PREFIX) || id.length > MAX_ID || id.length <= ThemeSpec.CUSTOM_PREFIX.length) return null
        val name = cleanName(m["name"] as? String)
        fun color(key: String): Int? = (m[key] as? String)?.let { ColorMath.parseHex(it) }
        val bg = color("bg") ?: return null
        val key = color("key") ?: return null
        val special = color("special") ?: return null
        val accent = color("accent") ?: return null
        val text = color("text") ?: return null
        val overrides = LinkedHashMap<Slot, Int>()
        val o = m["o"] as? Map<*, *>
        if (o != null) for ((slot, k) in SLOT_KEYS) (o[k] as? String)?.let { s -> ColorMath.parseHex(s)?.let { overrides[slot] = it } }
        // the picture: only a plain file name made by this app (never a path), the two numbers are kept inside their limits
        val picture = (m["pic"] as? String)?.takeIf { PictureMath.isValidFileName(it) }
        val fade = (m["fade"] as? Number)?.toInt() ?: ThemeSpec.DEFAULT_FADE
        val keys = (m["keys"] as? Number)?.toInt() ?: ThemeSpec.DEFAULT_KEY_OPACITY
        return ThemeSpec(id, name, bg, key, special, accent, text, overrides, picture, fade.coerceIn(0, ThemeSpec.MAX_FADE), keys.coerceIn(ThemeSpec.MIN_KEY_OPACITY, 100))
    }

    fun cleanName(raw: String?): String {
        val s = raw?.trim()?.replace(Regex("\\s+"), " ").orEmpty()
        return if (s.isEmpty()) DEFAULT_NAME else if (s.length > ThemeSpec.MAX_NAME) s.substring(0, ThemeSpec.MAX_NAME).trim() else s
    }

    fun encodeList(list: List<ThemeSpec>): String =
        MiniJson.write(mapOf("v" to VERSION, "themes" to list.filter { !it.builtIn }.take(MAX_THEMES).map { toMap(it) }))

    fun decodeList(text: String?): List<ThemeSpec> {
        if (text.isNullOrBlank()) return emptyList()
        return try {
            val root = MiniJson.parse(text) as? Map<*, *> ?: return emptyList()
            val arr = root["themes"] as? List<*> ?: return emptyList()
            val seen = HashSet<String>()
            val out = ArrayList<ThemeSpec>()
            for (e in arr) {
                val t = (e as? Map<*, *>)?.let { fromMap(it) } ?: continue
                if (seen.add(t.id)) out += t
                if (out.size >= MAX_THEMES) break
            }
            out
        } catch (e: IllegalArgumentException) {
            emptyList()
        }
    }
}
