package com.monsoonscript.keyboard.clipboard

import android.content.Context
import android.content.SharedPreferences

/**
 * The clipboard settings (E3, D65), in their own SharedPreferences file `monsoon_clipboard` (local only). The settings page and the panel both read
 * these; [ClipHub] copies the history rules into the store whenever one changes.
 */
class ClipPrefs(context: Context) {
    val sp: SharedPreferences = context.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    /** Record what is copied (the history). Off = nothing new is saved; what is saved stays until deleted. */
    var historyEnabled: Boolean
        get() = sp.getBoolean(K_ENABLED, true)
        set(v) = sp.edit().putBoolean(K_ENABLED, v).apply()

    /** 50, 100, 200 or 500 loose clips. */
    var maxItems: Int
        get() = sp.getInt(K_MAX, 100).let { if (it in LIMITS) it else 100 }
        set(v) = sp.edit().putInt(K_MAX, v).apply()

    /** Auto-delete after this many milliseconds; 0 = never. */
    var autoDeleteMs: Long
        get() = sp.getLong(K_AUTO, 0L).let { if (it in AUTO_DELETE) it else 0L }
        set(v) = sp.edit().putLong(K_AUTO, v).apply()

    var keepPinned: Boolean
        get() = sp.getBoolean(K_KEEP_PINNED, true)
        set(v) = sp.edit().putBoolean(K_KEEP_PINNED, v).apply()

    /** Copying the same text again moves the clip up instead of adding a second one. */
    var dedupe: Boolean
        get() = sp.getBoolean(K_DEDUPE, true)
        set(v) = sp.edit().putBoolean(K_DEDUPE, v).apply()

    var pinnedOnTop: Boolean
        get() = sp.getBoolean(K_PINNED_TOP, true)
        set(v) = sp.edit().putBoolean(K_PINNED_TOP, v).apply()

    /** Cards per row in the keyboard panel: 2 (default) or 1 (the toggle in the panel's toolbar changes it). */
    var columns: Int
        get() = if (sp.getInt(K_COLUMNS, 2) == 1) 1 else 2
        set(v) = sp.edit().putInt(K_COLUMNS, if (v == 1) 1 else 2).apply()

    /** Lines of text shown per card, 1-5. */
    var previewLines: Int
        get() = sp.getInt(K_LINES, 3).coerceIn(1, 5)
        set(v) = sp.edit().putInt(K_LINES, v.coerceIn(1, 5)).apply()

    /** Compact cards: less padding. */
    var compact: Boolean
        get() = sp.getBoolean(K_COMPACT, false)
        set(v) = sp.edit().putBoolean(K_COMPACT, v).apply()

    var showTime: Boolean
        get() = sp.getBoolean(K_TIME, true)
        set(v) = sp.edit().putBoolean(K_TIME, v).apply()

    /** The app's History screen shows Pinned / Today / Yesterday / Earlier headers (D67). */
    var groupByDate: Boolean
        get() = sp.getBoolean(K_GROUP, true)
        set(v) = sp.edit().putBoolean(K_GROUP, v).apply()

    /** Close the panel (back to the keys) after a clip was pasted. */
    var hideAfterPaste: Boolean
        get() = sp.getBoolean(K_HIDE_PASTE, false)
        set(v) = sp.edit().putBoolean(K_HIDE_PASTE, v).apply()

    companion object {
        const val FILE = "monsoon_clipboard"
        const val K_ENABLED = "enabled"
        const val K_MAX = "max_items"
        const val K_AUTO = "auto_delete_ms"
        const val K_KEEP_PINNED = "keep_pinned"
        const val K_DEDUPE = "dedupe"
        const val K_PINNED_TOP = "pinned_top"
        const val K_COLUMNS = "columns"
        const val K_LINES = "preview_lines"
        const val K_COMPACT = "compact"
        const val K_TIME = "show_time"
        const val K_HIDE_PASTE = "hide_after_paste"
        const val K_GROUP = "group_by_date"

        val LIMITS = intArrayOf(50, 100, 200, 500)
        private const val HOUR = 3_600_000L
        val AUTO_DELETE = longArrayOf(0L, HOUR, 24 * HOUR, 7 * 24 * HOUR, 30 * 24 * HOUR)
    }
}
