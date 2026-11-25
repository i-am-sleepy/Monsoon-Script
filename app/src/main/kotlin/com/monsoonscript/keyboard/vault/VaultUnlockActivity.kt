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
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import com.monsoonscript.keyboard.R
import com.monsoonscript.keyboard.settings.SettingsUi
import com.monsoonscript.keyboard.voice.CrashLog

/**
 * The see-through unlock screen of the keyboard's password picker (E1.14, D37): the app in front stays visible behind a dim layer, one card asks for the master
 * password (typed with the keyboard itself), and when the vault is open it closes and tells the keyboard ([VaultHub.onUnlockFinished]), which comes back with the
 * picker open. Not a full screen of its own, like the clipboard's unlock prompt, so the keyboard returns to the app's field. Fingerprint unlock comes in Phase 3.
 *
 * A plain Activity (not [VaultBaseActivity]): that base paints an opaque screen. It still blocks screenshots (D43) and wipes the typed password right after use.
 */
internal class VaultUnlockActivity : Activity() {
    private lateinit var prefs: VaultPrefs
    private lateinit var ui: SettingsUi
    private lateinit var field: PasswordField
    private lateinit var error: TextView
    private lateinit var button: TextView
    private var busy = false
    private var done = false
    private var result = false
    private var notified = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        CrashLog.note(this, "vault unlock card: onCreate (recreated=${savedInstanceState != null})")
        window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_NOTHING or WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE)
        prefs = VaultPrefs(this)
        ui = SettingsUi(this, prefs.raw)
        if (prefs.fileUri == null || savedInstanceState != null) { // no vault chosen, or recreated while asking: start over from the keyboard
            finishWith(false)
            return
        }
        build()
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
        field = PasswordField(this, ui, getString(R.string.vault_master_password))
        field.edit.setOnEditorActionListener { _, action, _ ->
            if (action == EditorInfo.IME_ACTION_DONE) {
                unlock()
                true
            } else false
        }
        card.addView(field, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
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
        field.edit.requestFocus()
    }

    private fun unlock() {
        if (busy) return
        val pw = field.chars()
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
            if (isDestroyed) return@async
            r.onSuccess {
                field.clear()
                finishWith(true)
            }.onFailure {
                button.text = getString(R.string.vault_unlock)
                showError(VaultHub.message(this, it))
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
    }

    override fun onDestroy() {
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
}
