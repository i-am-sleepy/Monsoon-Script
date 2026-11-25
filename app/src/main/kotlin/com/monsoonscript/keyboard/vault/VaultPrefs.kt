package com.monsoonscript.keyboard.vault

import android.content.Context
import android.content.SharedPreferences

/**
 * What the app remembers about the vault (D35, D52): where the file is (the owner picked it with the file picker; the permission is kept by Android), its name,
 * an optional key file, and the idle time before the vault locks (D38). Never a password. One vault at a time for now (D52).
 */
internal class VaultPrefs(context: Context) {
    val raw: SharedPreferences = context.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    var fileUri: String?
        get() = raw.getString("file", null)
        set(v) = raw.edit().putString("file", v).apply()

    /** The name shown on the unlock page (the file name, or the name the owner gave when creating the vault). */
    var fileName: String
        get() = raw.getString("name", "") ?: ""
        set(v) = raw.edit().putString("name", v).apply()

    /** False when the file picker gave only read access: the vault then opens read-only. */
    var writable: Boolean
        get() = raw.getBoolean("writable", true)
        set(v) = raw.edit().putBoolean("writable", v).apply()

    var keyFileUri: String?
        get() = raw.getString("key", null)
        set(v) = raw.edit().putString("key", v).apply()

    /** Index into [AUTO_LOCK_MINUTES]; the default is 5 minutes (D38). */
    var autoLockIndex: Int
        get() = raw.getInt("autolock", DEFAULT_AUTO_LOCK).coerceIn(0, AUTO_LOCK_MINUTES.size - 1)
        set(v) = raw.edit().putInt("autolock", v).apply()

    val autoLockMs: Long get() = AUTO_LOCK_MINUTES[autoLockIndex] * 60_000L

    val hasVault: Boolean get() = fileUri != null

    /**
     * The entries typed last, newest first, for the keyboard's "Recent" view (E1.14). Only ids (random numbers, no titles, no secrets). Kept here and not in the
     * vault so that using an entry never rewrites the vault file. Dropped with the vault.
     */
    var recentIds: List<String>
        get() = (raw.getString("recent", "") ?: "").split(',').filter { it.isNotEmpty() }
        set(v) = raw.edit().putString("recent", v.joinToString(",")).apply()

    /** Forget which file is the vault (the file itself is not touched). */
    fun forgetVault() {
        raw.edit().remove("file").remove("name").remove("key").remove("recent").apply()
    }

    companion object {
        const val FILE = "vault"
        val AUTO_LOCK_MINUTES = longArrayOf(1, 2, 5, 10, 30)
        const val DEFAULT_AUTO_LOCK = 2 // index of 5 minutes
    }
}
