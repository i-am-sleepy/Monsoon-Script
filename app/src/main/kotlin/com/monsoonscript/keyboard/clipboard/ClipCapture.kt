package com.monsoonscript.keyboard.clipboard

import android.content.ClipData
import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.Context
import android.net.Uri
import android.os.PersistableBundle

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
        val item = clip.getItemAt(0)
        val uri = item.uri
        if (uri != null && uri.scheme == "content" && uri.authority != hub.images.authority && imageType(clip.description) != null) {
            if (hub.prefs.saveImages) {
                hub.recordImage(uri)
                return
            }
        }
        val text = item.text?.toString() ?: return
        hub.record(text)
    }

    /** The first image type the clip announces, or null when it is not a picture. */
    private fun imageType(d: ClipDescription): String? {
        for (i in 0 until d.mimeTypeCount) {
            val t = d.getMimeType(i)
            if (t.startsWith("image/")) return t
        }
        return null
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

        /** Put [text] on the system clipboard marked with [EXTRA_NO_HISTORY], so the history does not record it again (copy-again from the panel or the app). */
        fun copyWithoutRecording(context: Context, text: String): Boolean {
            val cm = context.getSystemService(ClipboardManager::class.java) ?: return false
            val data = ClipData.newPlainText("", text)
            data.description.extras = PersistableBundle().apply { putBoolean(EXTRA_NO_HISTORY, true) }
            return try {
                cm.setPrimaryClip(data)
                true
            } catch (e: RuntimeException) {
                false // the system refused (rare)
            }
        }

        /** "Copy again" for a saved picture: its address goes on the system clipboard, marked so the history does not record it a second time. */
        fun copyImageWithoutRecording(context: Context, uri: Uri, mime: String): Boolean {
            val cm = context.getSystemService(ClipboardManager::class.java) ?: return false
            val data = ClipData(ClipDescription("", arrayOf(mime)), ClipData.Item(uri))
            data.description.extras = PersistableBundle().apply { putBoolean(EXTRA_NO_HISTORY, true) }
            return try {
                cm.setPrimaryClip(data)
                true
            } catch (e: RuntimeException) {
                false
            }
        }

        fun isSensitive(d: ClipDescription): Boolean {
            val x = d.extras ?: return false
            return x.getBoolean(EXTRA_SENSITIVE, false) || x.getBoolean(EXTRA_NO_HISTORY, false)
        }
    }
}
