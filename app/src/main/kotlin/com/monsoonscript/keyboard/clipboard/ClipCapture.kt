package com.monsoonscript.keyboard.clipboard

import android.content.ClipData
import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.Context

/**
 * Records what is copied on the phone into the history (E3, D65). Android lets only the default keyboard read the clipboard in the background, so this works
 * while Monsoon is the active keyboard (the same limit Gboard has).
 *
 * Never recorded (D65, D43): clips marked sensitive (Android 13+ flag that KeePassDX and other password managers set), clips our own vault marks with
 * [EXTRA_NO_HISTORY], anything copied while a password / hidden / "no personalized learning" field has the keyboard ([fieldAllowed] = false), and
 * everything while the history is switched off. No guessing from the content.
 */
class ClipCapture(context: Context, private val hub: ClipHub) {
    private val cm: ClipboardManager? = context.getSystemService(ClipboardManager::class.java)
    private val listener = ClipboardManager.OnPrimaryClipChangedListener { onChanged() }
    private var running = false

    /** The IME sets this to false while the field in front is a password / incognito one. */
    var fieldAllowed = true

    fun start() {
        if (running) return
        running = true
        cm?.addPrimaryClipChangedListener(listener)
    }

    fun stop() {
        if (!running) return
        running = false
        cm?.removePrimaryClipChangedListener(listener)
    }

    private fun onChanged() {
        if (!fieldAllowed || !hub.prefs.historyEnabled) return
        val clip = readClip() ?: return
        if (clip.itemCount < 1 || isSensitive(clip.description)) return
        val text = clip.getItemAt(0).text?.toString() ?: return // image clips come with E3.8
        hub.record(text)
    }

    private fun readClip(): ClipData? =
        try {
            cm?.primaryClip
        } catch (e: RuntimeException) {
            null // not allowed to read right now
        }

    companion object {
        /** Same key as ClipDescription.EXTRA_IS_SENSITIVE (Android 13); written out so it is also read on older versions. */
        const val EXTRA_SENSITIVE = "android.content.extra.IS_SENSITIVE"

        /** Our own marker: the vault puts it on every secret it copies (D43), so the history never sees it, whatever the Android version. */
        const val EXTRA_NO_HISTORY = "com.monsoonscript.keyboard.extra.NO_HISTORY"

        fun isSensitive(d: ClipDescription): Boolean {
            val x = d.extras ?: return false
            return x.getBoolean(EXTRA_SENSITIVE, false) || x.getBoolean(EXTRA_NO_HISTORY, false)
        }
    }
}
