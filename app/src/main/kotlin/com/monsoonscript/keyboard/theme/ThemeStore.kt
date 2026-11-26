package com.monsoonscript.keyboard.theme

import android.content.Context
import com.monsoonscript.keyboard.prefs.Prefs

/**
 * The owner's themes and the choice for day and night (L10, D75), kept in the keyboard's [Prefs] file so the keyboard hears every change through its
 * normal settings listener. The ready-made themes are code ([Themes]); only the custom ones are saved (as [ThemeCodec] text). Reading is cached: the text is
 * parsed again only when it changed.
 */
object ThemeStore {
    const val KEY_CUSTOM = "themes_custom"
    const val KEY_DAY = "theme_day"
    const val KEY_NIGHT = "theme_night"

    private var cachedRaw: String? = null
    private var cachedList: List<ThemeSpec> = emptyList()

    @Synchronized
    fun custom(context: Context): List<ThemeSpec> {
        val raw = Prefs.of(context).getString(KEY_CUSTOM, null)
        if (raw != cachedRaw) {
            cachedList = ThemeCodec.decodeList(raw)
            cachedRaw = raw
        }
        return cachedList
    }

    /** The ready-made themes (and, on Android 12+, the live Wallpaper theme), then the owner's own. */
    fun all(context: Context): List<ThemeSpec> = presets(context) + custom(context)

    fun presets(context: Context): List<ThemeSpec> = Themes.ALL + listOfNotNull(WallpaperTheme.spec(context, night = false))

    /** The theme [id]; the live Wallpaper theme comes in its day (light) form here, see [day] / [night] for the slots. */
    fun find(context: Context, id: String?): ThemeSpec? = resolve(context, id, night = false)

    private fun resolve(context: Context, id: String?, night: Boolean): ThemeSpec? = when {
        id == null -> null
        id == WallpaperTheme.ID -> WallpaperTheme.spec(context, night)
        else -> Themes.builtIn(id) ?: custom(context).firstOrNull { it.id == id }
    }

    fun dayId(context: Context): String = Prefs.of(context).getString(KEY_DAY, null) ?: Themes.DEFAULT_ID
    fun nightId(context: Context): String = Prefs.of(context).getString(KEY_NIGHT, null) ?: Themes.DEFAULT_ID

    /** The theme for day (a missing one, for example a deleted theme, gives the default, High contrast, D83). */
    fun day(context: Context): ThemeSpec = resolve(context, dayId(context), night = false) ?: Themes.DEFAULT
    fun night(context: Context): ThemeSpec = resolve(context, nightId(context), night = true) ?: Themes.DEFAULT

    fun setDay(context: Context, id: String) {
        if (find(context, id) != null) Prefs.of(context).edit().putString(KEY_DAY, id).apply()
    }

    fun setNight(context: Context, id: String) {
        if (find(context, id) != null) Prefs.of(context).edit().putString(KEY_NIGHT, id).apply()
    }

    /** Add or replace the custom theme [spec] (a ready-made one is never saved). */
    @Synchronized
    fun save(context: Context, spec: ThemeSpec) {
        if (spec.builtIn) return
        val list = custom(context).toMutableList()
        val i = list.indexOfFirst { it.id == spec.id }
        if (i >= 0) list[i] = spec else if (list.size < ThemeCodec.MAX_THEMES) list += spec else return
        write(context, list)
    }

    /** Delete the custom theme [id]; day or night that used it go back to the default theme (High contrast, D83). */
    @Synchronized
    fun delete(context: Context, id: String) {
        ThemePictures.delete(context, find(context, id)?.picture) // a deleted theme takes its picture file along
        val list = custom(context).filter { it.id != id }
        val edit = Prefs.of(context).edit()
        if (dayId(context) == id) edit.putString(KEY_DAY, Themes.DEFAULT_ID)
        if (nightId(context) == id) edit.putString(KEY_NIGHT, Themes.DEFAULT_ID)
        edit.putString(KEY_CUSTOM, ThemeCodec.encodeList(list)).apply()
    }

    private fun write(context: Context, list: List<ThemeSpec>) {
        Prefs.of(context).edit().putString(KEY_CUSTOM, ThemeCodec.encodeList(list)).apply()
    }

    fun newId(context: Context): String {
        val taken = custom(context).map { it.id }.toSet()
        while (true) {
            val id = ThemeSpec.CUSTOM_PREFIX + java.lang.Long.toString(System.currentTimeMillis(), 36) + (1000 + (Math.random() * 9000).toInt())
            if (id !in taken) return id
        }
    }

    /** A new custom theme with the colors of [source]; [baseName] gets a number when the name is taken. */
    fun copyOf(context: Context, source: ThemeSpec, baseName: String): ThemeSpec =
        ThemeSpec(
            newId(context), uniqueName(context, baseName), source.bg, source.key, source.special, source.accent, source.text, source.overrides,
            ThemePictures.copy(context, source.picture), source.pictureFade, source.keyOpacity, source.cropPortrait, source.cropLandscape,
        )

    /** Add a theme that came from shared text: a new id, a name that is not taken, no picture. Null when there is no room for another theme. */
    fun importShared(context: Context, shared: ThemeSpec): ThemeSpec? {
        if (custom(context).size >= ThemeCodec.MAX_THEMES) return null
        val t = copyOf(context, shared.copy(picture = null), shared.name)
        save(context, t)
        return t
    }

    fun uniqueName(context: Context, base: String): String {
        val names = all(context).map { it.name.lowercase() }.toSet()
        val clean = ThemeCodec.cleanName(base)
        if (clean.lowercase() !in names) return clean
        var n = 2
        while (true) {
            val candidate = "$clean $n"
            if (candidate.lowercase() !in names) return candidate
            n++
        }
    }
}
