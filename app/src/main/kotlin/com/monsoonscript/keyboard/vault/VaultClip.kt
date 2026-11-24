package com.monsoonscript.keyboard.vault

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.PersistableBundle
import android.widget.Toast
import com.monsoonscript.keyboard.R
import com.monsoonscript.keyboard.clipboard.ClipCapture

/**
 * Copying a secret out of the vault (D43): the clip is marked sensitive (Android 13+ hides the preview) and "no history" (the keyboard's own clipboard history
 * never records it, on any Android version), and it is removed again after 30 seconds, but only if it is still OUR copy: something the owner copied in the
 * meantime is left alone. Android lets a background app write and clear the clipboard but not read it, so "still ours" is judged from the change callbacks:
 * a change that is not our own copy means it was replaced.
 */
internal object VaultClip {
    const val CLEAR_AFTER_MS = 30_000L

    private val handler = Handler(Looper.getMainLooper())
    private var token: String? = null
    private var replaced = false
    private var expectOwnChange = false
    private var listening = false
    private var clear: Runnable? = null

    private val listener = ClipboardManager.OnPrimaryClipChangedListener {
        if (expectOwnChange) expectOwnChange = false else replaced = true
    }

    /** Copies [text]; returns false when the system refused. [what] is only for the confirmation ("Password copied"). */
    fun copy(context: Context, text: CharSequence, what: String, secret: Boolean = true): Boolean {
        val app = context.applicationContext
        val cm = app.getSystemService(ClipboardManager::class.java) ?: return false
        if (!listening) {
            cm.addPrimaryClipChangedListener(listener)
            listening = true
        }
        val mine = "monsoon-vault-" + System.nanoTime()
        val data = ClipData.newPlainText(if (secret) mine else "", text)
        data.description.extras = PersistableBundle().apply {
            putBoolean(ClipCapture.EXTRA_SENSITIVE, true)
            putBoolean(ClipCapture.EXTRA_NO_HISTORY, true)
        }
        clear?.let { handler.removeCallbacks(it) }
        replaced = false
        expectOwnChange = true
        try {
            cm.setPrimaryClip(data)
        } catch (_: RuntimeException) {
            expectOwnChange = false
            return false
        }
        if (secret) {
            token = mine
            val r = Runnable { clearIfStillOurs(app) }
            clear = r
            handler.postDelayed(r, CLEAR_AFTER_MS)
        }
        if (Build.VERSION.SDK_INT < 33) Toast.makeText(app, app.getString(R.string.vault_copied, what), Toast.LENGTH_SHORT).show()
        return true
    }

    private fun clearIfStillOurs(context: Context) {
        val mine = token ?: return
        token = null
        clear = null
        if (replaced) return
        val cm = context.getSystemService(ClipboardManager::class.java) ?: return
        try {
            val label = cm.primaryClip?.description?.label?.toString()
            if (label != null && label != mine) return // readable, and it is something else
            expectOwnChange = true
            if (Build.VERSION.SDK_INT >= 28) cm.clearPrimaryClip() else cm.setPrimaryClip(ClipData.newPlainText("", ""))
        } catch (_: RuntimeException) {
            expectOwnChange = false
        }
    }
}
