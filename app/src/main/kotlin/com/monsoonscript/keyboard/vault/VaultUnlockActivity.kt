package com.monsoonscript.keyboard.vault

import android.app.Activity
import android.graphics.Typeface
import android.net.Uri
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import com.monsoonscript.keyboard.R
import com.monsoonscript.keyboard.settings.SettingsUi
import com.monsoonscript.keyboard.voice.CrashLog

/**
 * The see-through unlock screen of the keyboard's password picker (E1.14, D37): the app in front stays visible behind a dim layer, one card asks for the master
 * password (typed with the keyboard itself), and when the vault is open it closes and tells the keyboard ([VaultHub.onUnlockFinished]), which comes back with the
 * picker open. Not a full screen of its own, like the clipboard's unlock prompt, so the keyboard returns to the app's field. With fingerprint unlock set up
 * (E1.15) the phone's fingerprint prompt shows first and the soft keyboard stays down until the owner chooses the master password.
 *
 * A plain Activity (not [VaultBaseActivity]): that base paints an opaque screen. It still blocks screenshots (D43) and wipes the typed password right after use.
 */
internal class VaultUnlockActivity : Activity() {
    private lateinit var prefs: VaultPrefs
    private lateinit var ui: SettingsUi
    private lateinit var fp: FingerprintUnlock
    private lateinit var field: PasswordField
    private lateinit var info: TextView
    private lateinit var fpButton: View
    private lateinit var error: TextView
    private lateinit var button: TextView
    private var busy = false
    private var done = false
    private var result = false
    private var notified = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        notified = intent.getBooleanExtra(EXTRA_QUIET, false) // Autofill asks quietly: the keyboard has nothing to bring back
        window.decorView.importantForAutofill = View.IMPORTANT_FOR_AUTOFILL_NO_EXCLUDE_DESCENDANTS // no other Autofill service may offer to save the master password
        CrashLog.note(this, "vault unlock card: onCreate (recreated=${savedInstanceState != null})")
        window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        prefs = VaultPrefs(this)
        ui = SettingsUi(this, prefs.raw)
        fp = FingerprintUnlock(this, prefs)
        if (prefs.fileUri == null || savedInstanceState != null) { // no vault chosen, or recreated while asking: start over from the keyboard
            finishWith(false)
            return
        }
        // With the fingerprint prompt coming, the soft keyboard stays down until the owner chooses the master password (D91).
        val keyboardNow = if (fp.willPrompt()) WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN else WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE
        window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_NOTHING or keyboardNow)
        build()
        startFingerprint()
    }

    /** The fingerprint prompt (again): at the start, and from the "Use fingerprint" button for the owner who missed or dismissed it. */
    private fun startFingerprint() {
        if (busy || done) return
        info.visibility = View.GONE
        error.visibility = View.GONE
        field.edit.windowToken?.let { getSystemService(InputMethodManager::class.java)?.hideSoftInputFromWindow(it, 0) } // the prompt is not covered by the keyboard
        fp.begin(onPassword = { pw -> open(pw, typed = false) }, onTyping = { showTyping(it) })
    }

    /** The master password field gets the focus and the soft keyboard (at once, or as soon as the fingerprint prompt has closed), with a line saying why when there is one. */
    private fun showTyping(note: String?) {
        if (isDestroyed || done) return
        if (note != null) {
            info.text = note
            info.visibility = View.VISIBLE
        }
        fpButton.visibility = if (fp.willPrompt()) View.VISIBLE else View.GONE // only while the fingerprint can still be used
        SoftKeyboard.show(field.edit)
    }

    private fun build() {
        val c = ui.colors
        val root = FrameLayout(this)
        root.setBackgroundColor(0x99000000.toInt()) // dims the app behind; it stays visible
        root.setOnClickListener { finishWith(false) }

        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = ui.rounded(c.bg, 20, c.cardStroke)
            setPadding(ui.dp(20), ui.dp(18), ui.dp(20), ui.dp(18))
            isClickable = true // taps on the card do not reach the dim layer
        }
        val name = prefs.fileName.ifEmpty { getString(R.string.vault_title) }
        card.addView(TextView(this).apply {
            text = getString(R.string.picker_unlock_title)
            textSize = 18f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(c.text)
        })
        card.addView(TextView(this).apply {
            text = getString(R.string.vault_unlock_for, name)
            textSize = 14f
            setTextColor(c.muted)
            setPadding(0, ui.dp(4), 0, ui.dp(12))
        })
        info = TextView(this).apply {
            textSize = 14f
            setTextColor(c.accent)
            visibility = View.GONE
            setPadding(0, 0, 0, ui.dp(10))
        }
        card.addView(info)
        field = PasswordField(this, ui, getString(R.string.vault_master_password))
        field.edit.setOnEditorActionListener { _, action, _ ->
            if (action == EditorInfo.IME_ACTION_DONE) {
                unlock()
                true
            } else false
        }
        card.addView(field, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        // next to the password: the way back to the fingerprint when its prompt was missed or dismissed (shown only when the fingerprint is set up and allowed now)
        fpButton = ui.outlineButton(getString(R.string.fp_button)) { startFingerprint() }.apply { visibility = if (fp.willPrompt()) View.VISIBLE else View.GONE }
        card.addView(fpButton, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = ui.dp(10) })
        error = TextView(this).apply {
            textSize = 14f
            setTextColor(VAULT_RED)
            visibility = View.GONE
            setPadding(0, ui.dp(8), 0, 0)
        }
        card.addView(error)
        // right under the field, so the keyboard cannot hide the buttons
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, ui.dp(14), 0, 0)
        }
        val cancel = ui.outlineButton(getString(R.string.picker_cancel)) { finishWith(false) }
        button = ui.filledButton(getString(R.string.vault_unlock)) { unlock() } as TextView
        row.addView(cancel, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply { rightMargin = ui.dp(8) })
        row.addView(button, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        card.addView(row)

        val lp = FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.TOP or Gravity.CENTER_HORIZONTAL)
        lp.setMargins(ui.dp(16), ui.dp(64), ui.dp(16), 0)
        val maxW = ui.dp(440)
        if (resources.displayMetrics.widthPixels - ui.dp(32) > maxW) lp.width = maxW
        root.addView(card, lp)
        setContentView(root)
    }

    private fun unlock() {
        if (busy) return
        open(field.chars(), typed = true)
    }

    /** Opens the vault with [pw] (wiped here). [typed] = the owner typed it; false = it came out of the fingerprint's storage. */
    private fun open(pw: CharArray, typed: Boolean) {
        val keyUri = prefs.keyFileUri
        if (pw.isEmpty() && keyUri == null) {
            showError(getString(R.string.vault_need_password))
            return
        }
        val file = Uri.parse(prefs.fileUri ?: return)
        val readOnly = !prefs.writable
        val autoLock = prefs.autoLockMs
        busy = true
        error.visibility = View.GONE
        button.text = getString(R.string.vault_unlocking)
        val keep = if (typed && pw.isNotEmpty()) pw.copyOf() else CharArray(0) // for the fingerprint set-up after a typed unlock; wiped on every path
        VaultHub.client(this).async({ v ->
            val key = keyUri?.let { VaultHub.readKeyFile(this, Uri.parse(it)) }
            try {
                v.open(file, pw, key, readOnly)
                v.setAutoLock(autoLock)
            } finally {
                pw.fill('\u0000')
            }
        }) { r ->
            busy = false
            if (isDestroyed) {
                keep.fill('\u0000')
                return@async
            }
            r.onSuccess {
                field.clear()
                if (typed) fp.afterTyped(keep) { finishWith(true) } else finishWith(true)
            }.onFailure { e ->
                keep.fill('\u0000')
                button.text = getString(R.string.vault_unlock)
                if (!typed) { // the fingerprint's password failed: back to typing, saying why when the stored password is out of date
                    if (fp.passwordRejected(e)) showTyping(fp.passwordNote) else {
                        showError(VaultHub.message(this, e))
                        showTyping(null)
                    }
                } else {
                    showError(VaultHub.message(this, e))
                }
            }
        }
    }

    private fun showError(text: String) {
        error.text = text
        error.visibility = View.VISIBLE
    }

    private fun finishWith(ok: Boolean) {
        if (done) return
        done = true
        result = ok
        setResult(if (ok) RESULT_OK else RESULT_CANCELED)
        // The keyboard is told in onDestroy, when the card (and its own text field) is really gone, so that it never opens the picker inside the card's field.
        finishAndRemoveTask()
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        finishWith(false)
    }

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        if (!done && ::error.isInitialized) error.visibility = View.GONE // asked again while it is still up (singleInstance): nothing else to do
        if (!intent.getBooleanExtra(EXTRA_QUIET, false)) notified = false // the keyboard asked too: it wants to hear the end
    }

    override fun onDestroy() {
        if (::fp.isInitialized) fp.stop()
        if (::field.isInitialized) field.clear()
        if (!notified) {
            notified = true
            CrashLog.note(this, "vault unlock card: onDestroy (result=$result, finishing=$isFinishing)")
            VaultHub.onUnlockFinished?.invoke(if (done) result else false) // the keyboard brings itself and its picker back
        }
        super.onDestroy()
    }

    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        CrashLog.note(this, "vault unlock card: memory warning (trim level $level)")
    }

    companion object {
        /** Set by Autofill: the card does not report its end to the keyboard ([VaultHub.onUnlockFinished]). */
        const val EXTRA_QUIET = "quiet"
    }
}
