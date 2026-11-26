package com.monsoonscript.keyboard.theme

import java.util.Base64

/**
 * Sharing a theme as text (L10 step 3, D91): a theme becomes one line of letters and digits that starts with [PREFIX] and survives being pasted into any chat; the
 * other person pastes the whole message (or just the line) into "Import a theme". The line holds the colors and the two sliders' numbers, never the picture or its
 * placement (a photo does not fit in text), and never an id (the importer makes a new one). Whatever is pasted is treated as untrusted: it goes through the same
 * checked reading as the saved themes ([ThemeCodec]), so a damaged or hostile text gives null, never a crash and never a path or an out-of-range number.
 */
object ThemeShare {
    const val PREFIX = "MST1:"
    private const val MAX_CODE = 6000
    private const val MAX_PASTE = 20000
    private val CODE = Regex(Regex.escape(PREFIX) + "([A-Za-z0-9_-]{8,$MAX_CODE})")

    /** The shareable line for [spec]. */
    fun encode(spec: ThemeSpec): String {
        val plain = spec.copy(id = ThemeSpec.CUSTOM_PREFIX + "shared", picture = null, cropPortrait = PictureCrop.AUTO, cropLandscape = PictureCrop.AUTO)
        val json = ThemeCodec.encodeList(listOf(plain))
        return PREFIX + Base64.getUrlEncoder().withoutPadding().encodeToString(json.toByteArray(Charsets.UTF_8))
    }

    /** The theme in [text] (anything that contains a shared line), or null when there is none or it is damaged. Its id is a placeholder: import it with a new one. */
    fun decode(text: String?): ThemeSpec? {
        if (text.isNullOrBlank() || text.length > MAX_PASTE) return null
        val code = CODE.find(text)?.groupValues?.get(1) ?: return null
        val json = try {
            String(Base64.getUrlDecoder().decode(code), Charsets.UTF_8)
        } catch (e: IllegalArgumentException) {
            return null
        }
        val t = ThemeCodec.decodeList(json).firstOrNull() ?: return null
        return t.copy(picture = null, cropPortrait = PictureCrop.AUTO, cropLandscape = PictureCrop.AUTO)
    }
}
