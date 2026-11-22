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

    /** Save copied images (E3.8, D70). Off = images are not recorded; the ones saved stay until deleted. */
    var saveImages: Boolean
        get() = sp.getBoolean(K_IMAGES, true)
        set(v) = sp.edit().putBoolean(K_IMAGES, v).apply()

    /** Typing a trigger and pressing Space puts the full text in (E3.10, D74). Off = the strip offers it as a candidate (D68). */
    var directShortcuts: Boolean
        get() = sp.getBoolean(K_SHORTCUT_DIRECT, false)
        set(v) = sp.edit().putBoolean(K_SHORTCUT_DIRECT, v).apply()

    /** The latest copied text shows as a chip in the suggestion strip for a minute (E3.10, D74). */
    var recentChip: Boolean
        get() = sp.getBoolean(K_RECENT_CHIP, true)
        set(v) = sp.edit().putBoolean(K_RECENT_CHIP, v).apply()

    /** Automatic backup (E3.9, D71): [BACKUP_OFF], [BACKUP_DAILY] or [BACKUP_WEEKLY]. */
    var autoBackup: Int
        get() = sp.getInt(K_AUTO_BACKUP, BACKUP_OFF).let { if (it in BACKUP_OFF..BACKUP_WEEKLY) it else BACKUP_OFF }
        set(v) = sp.edit().putInt(K_AUTO_BACKUP, v).apply()

    /** The folder (a document tree address) the automatic backups go to, empty = not chosen. */
    var backupFolder: String
        get() = sp.getString(K_BACKUP_FOLDER, "") ?: ""
        set(v) = sp.edit().putString(K_BACKUP_FOLDER, v).apply()

    /** Pictures in the automatic backups (off by default: they make the files big). */
    var backupImages: Boolean
        get() = sp.getBoolean(K_BACKUP_IMAGES, false)
        set(v) = sp.edit().putBoolean(K_BACKUP_IMAGES, v).apply()

    /** When the last automatic backup was written (0 = never), and when one was last tried. */
    var lastBackup: Long
        get() = sp.getLong(K_LAST_BACKUP, 0L)
        set(v) = sp.edit().putLong(K_LAST_BACKUP, v).apply()
    var lastBackupTry: Long
        get() = sp.getLong(K_LAST_TRY, 0L)
        set(v) = sp.edit().putLong(K_LAST_TRY, v).apply()

    /** True while the last automatic backup attempt failed. */
    var backupFailed: Boolean
        get() = sp.getBoolean(K_BACKUP_FAILED, false)
        set(v) = sp.edit().putBoolean(K_BACKUP_FAILED, v).apply()

    /** The biometric lock on the clipboard (E3.9, D71). */
    var lockEnabled: Boolean
        get() = sp.getBoolean(K_LOCK, false)
        set(v) = sp.edit().putBoolean(K_LOCK, v).apply()

    /** Minutes the clipboard stays unlocked after it is left: one of [LOCK_MINUTES], 0 = lock at once. */
    var lockMinutes: Int
        get() = sp.getInt(K_LOCK_MIN, 1).let { if (it in LOCK_MINUTES) it else 1 }
        set(v) = sp.edit().putInt(K_LOCK_MIN, v).apply()

    val lockTimeoutMs: Long get() = lockMinutes * 60_000L

    /** The room the saved images may take, in MB: 50, 100 (default) or 250. */
    var imageCapMb: Int
        get() = sp.getInt(K_IMAGE_CAP, 100).let { if (it in IMAGE_CAPS) it else 100 }
        set(v) = sp.edit().putInt(K_IMAGE_CAP, v).apply()

    val imageCapBytes: Long get() = imageCapMb * 1024L * 1024L

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
        const val K_IMAGES = "save_images"
        const val K_IMAGE_CAP = "image_cap_mb"
        const val K_LOCK = "lock_enabled"
        const val K_SHORTCUT_DIRECT = "shortcut_direct"
        const val K_RECENT_CHIP = "recent_chip"
        const val K_AUTO_BACKUP = "auto_backup"
        const val K_BACKUP_FOLDER = "backup_folder"
        const val K_BACKUP_IMAGES = "backup_images"
        const val K_LAST_BACKUP = "last_backup"
        const val K_LAST_TRY = "last_backup_try"
        const val K_BACKUP_FAILED = "backup_failed"
        const val BACKUP_OFF = 0
        const val BACKUP_DAILY = 1
        const val BACKUP_WEEKLY = 2
        const val K_LOCK_MIN = "lock_minutes"

        val LIMITS = intArrayOf(50, 100, 200, 500)
        val IMAGE_CAPS = intArrayOf(50, 100, 250)
        val LOCK_MINUTES = intArrayOf(0, 1, 5, 15, 30, 60, 240)
        private const val HOUR = 3_600_000L
        val AUTO_DELETE = longArrayOf(0L, HOUR, 24 * HOUR, 7 * 24 * HOUR, 30 * 24 * HOUR)
    }
}
