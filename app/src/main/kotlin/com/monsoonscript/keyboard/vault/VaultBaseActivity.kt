package com.monsoonscript.keyboard.vault

import android.app.AlertDialog
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.WindowManager
import android.widget.Toast
import com.monsoonscript.keyboard.R
import com.monsoonscript.keyboard.settings.SettingsUi
import com.monsoonscript.keyboard.settings.ThemedActivity
import com.monsoonscript.vault.Change
import com.monsoonscript.vault.IVault
import com.monsoonscript.vault.Resolve
import com.monsoonscript.vault.VaultClient

/**
 * The base of every vault screen (E1.13): themed like the app (D30), protected against screenshots and the recent-apps preview (D43), locked again after the
 * idle time (D38: the screen shows nothing once the vault is locked), and with the shared ways to call the vault, report its errors and answer the
 * "file changed elsewhere" question (D50).
 */
internal abstract class VaultBaseActivity : ThemedActivity() {
    protected lateinit var ui: SettingsUi
    protected lateinit var prefs: VaultPrefs
    protected val vault: VaultClient get() = VaultHub.client(this)
    protected lateinit var busy: BusyDialog

    /** Screens that show vault content need it unlocked; the main screen decides for itself. */
    protected open val needsUnlocked: Boolean get() = true

    /** True while the screen shows vault content: only then the idle timer runs. */
    protected open val idleLockActive: Boolean get() = needsUnlocked

    private val handler = Handler(Looper.getMainLooper())
    private val idleLock = Runnable { lockNow() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_SECURE) // D43: no screenshots, no preview in the recent apps
        prefs = VaultPrefs(this)
        ui = SettingsUi(this, prefs.raw)
        busy = BusyDialog(this, ui)
    }

    override fun onStart() {
        super.onStart()
        if (needsUnlocked) {
            vault.async({ it.status() }) { r ->
                if (r.getOrNull()?.unlocked != true) onLocked()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        armIdle()
    }

    override fun onPause() {
        handler.removeCallbacks(idleLock)
        super.onPause()
    }

    override fun onDestroy() {
        busy.hide()
        super.onDestroy()
    }

    override fun onUserInteraction() {
        super.onUserInteraction()
        armIdle()
    }

    /** Restarts the idle countdown; the vault process runs its own countdown too (D38), this one also clears what is on this screen. */
    protected fun armIdle() {
        handler.removeCallbacks(idleLock)
        if (idleLockActive) handler.postDelayed(idleLock, prefs.autoLockMs)
    }

    /** The vault is (or just became) locked: show the unlock page. The main screen overrides this. */
    protected open fun onLocked() {
        startActivity(Intent(this, VaultMainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP))
        finish()
    }

    protected fun lockNow() {
        handler.removeCallbacks(idleLock)
        vault.async({ it.lock() }) { }
        onLocked()
    }

    protected val alive: Boolean get() = !isFinishing && !isDestroyed

    /** Runs [block] in the vault process off the main thread; [ok] gets the result on the main thread, errors go to [fail]. */
    protected fun <T> call(block: (IVault) -> T, fail: (Throwable) -> Unit = { report(it) }, ok: (T) -> Unit) {
        vault.async(block) { r ->
            if (!alive) return@async
            r.fold(ok, fail)
        }
    }

    /** Shows an error; a locked vault goes to the unlock page instead. */
    protected fun report(e: Throwable) {
        busy.hide()
        if (VaultHub.isLocked(e)) {
            onLocked()
            return
        }
        Toast.makeText(this, VaultHub.message(this, e), Toast.LENGTH_LONG).show()
    }

    /**
     * Calls [block] with a spinner, then [afterwards] with its [Change]. A change that could not be written because the file was changed elsewhere
     * (D50) first goes through the three-way question.
     */
    protected fun change(spinner: Int, block: (IVault) -> Change, afterwards: (Change) -> Unit) {
        busy.show(getString(spinner))
        call(block, fail = { report(it) }) { c ->
            busy.hide()
            if (c.saved) afterwards(c) else askConflict { afterwards(c) }
        }
    }

    /** D50: the file changed elsewhere; the owner chooses what happens. [done] runs after a successful answer. */
    protected fun askConflict(done: () -> Unit) {
        val options = arrayOf(getString(R.string.vault_conflict_merge), getString(R.string.vault_conflict_overwrite), getString(R.string.vault_conflict_keep_file))
        val dialog = AlertDialog.Builder(this)
            .setTitle(R.string.vault_conflict_title)
            .setMessage(R.string.vault_conflict_text)
            .setItems(options) { _, which ->
                when (which) {
                    0 -> askMasterPassword(R.string.vault_conflict_merge) { pw -> resolve(Resolve.MERGE, pw, done) }
                    1 -> resolve(Resolve.OVERWRITE, null, done)
                    else -> askMasterPassword(R.string.vault_conflict_keep_file) { pw -> resolve(Resolve.KEEP_FILE, pw, done) }
                }
            }
            .setNegativeButton(R.string.vault_decide_later, null)
            .setCancelable(true)
            .show()
        ui.styleDialog(dialog)
    }

    private fun resolve(choice: Int, password: CharArray?, done: () -> Unit) {
        busy.show(getString(R.string.vault_saving))
        val keyUri = prefs.keyFileUri
        call({ v ->
            val key = if (password != null && keyUri != null) VaultHub.readKeyFile(this, Uri.parse(keyUri)) else null
            try {
                v.resolve(choice, password, key)
            } finally {
                password?.fill('\u0000')
            }
        }) { c ->
            busy.hide()
            if (c.saved) done() else Toast.makeText(this, R.string.vault_err_changed, Toast.LENGTH_LONG).show()
        }
    }

    /** Asks for the master password again (merging with, or reloading, the other copy needs it: the vault does not keep it). */
    protected fun askMasterPassword(title: Int, onPassword: (CharArray) -> Unit) {
        val field = PasswordField(this, ui, getString(R.string.vault_master_password))
        val dialog = AlertDialog.Builder(this)
            .setTitle(title)
            .setView(ui.padded(field, 20, 8))
            .setPositiveButton(android.R.string.ok) { _, _ ->
                val pw = field.chars()
                field.clear()
                onPassword(pw)
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
        ui.styleDialog(dialog)
    }
}
