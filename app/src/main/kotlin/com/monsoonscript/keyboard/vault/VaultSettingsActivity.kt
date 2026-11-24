package com.monsoonscript.keyboard.vault

import android.app.AlertDialog
import android.os.Bundle
import com.monsoonscript.keyboard.R
import com.monsoonscript.vault.VaultInfo

/**
 * The vault's settings (E1.13): how long it stays unlocked (D38), facts about the open vault, locking it and choosing another one. Fingerprint unlock, backup
 * screens and so on come in later phases (D51).
 */
internal class VaultSettingsActivity : VaultBaseActivity() {
    override val needsUnlocked: Boolean get() = false
    private var info: VaultInfo? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        render()
    }

    override fun onStart() {
        super.onStart()
        call({ it.info() }, fail = { }) { i ->
            info = i
            render()
        }
    }

    private fun render() {
        val page = ui.page(getString(R.string.vault_settings), onBack = { finish() })
        with(ui) {
            val c = page.content
            c.add(header(getString(R.string.vault_security)))
            c.add(choiceRow(getString(R.string.vault_lock_after), "autolock",
                VaultPrefs.AUTO_LOCK_MINUTES.map { resources.getQuantityString(R.plurals.vault_minutes, it.toInt(), it.toInt()) }, VaultPrefs.DEFAULT_AUTO_LOCK))
            c.add(note(getString(R.string.vault_lock_after_note)))
            c.add(header(getString(R.string.vault_this_vault)))
            c.add(infoRow(getString(R.string.vault_file), prefs.fileName.ifEmpty { "-" }))
            info?.let { i ->
                c.add(infoRow(getString(R.string.vault_entries), i.entryCount.toString()))
                c.add(infoRow(getString(R.string.vault_encryption), i.cipher))
                c.add(infoRow(getString(R.string.vault_format), "KDBX ${i.formatVersion}"))
            }
            if (!prefs.writable) c.add(note(getString(R.string.vault_read_only_note)))
            c.add(gap(12))
            c.add(buttonRow(getString(R.string.vault_lock_now), null) { lockNow() })
            c.add(buttonRow(getString(R.string.vault_other_vault), null) { confirmForget() })
            c.add(note(getString(R.string.vault_backups_note)))
        }
        setContentView(page.root)
    }

    override fun onLocked() {
        finish() // the list behind this page shows the unlock page
    }

    override fun onPause() {
        vault.async({ it.setAutoLock(prefs.autoLockMs) }) { } // the new idle time applies to the open vault right away
        super.onPause()
    }

    private fun confirmForget() {
        val dialog = AlertDialog.Builder(this)
            .setTitle(R.string.vault_other_vault)
            .setMessage(R.string.vault_other_vault_text)
            .setPositiveButton(R.string.vault_forget_yes) { _, _ ->
                vault.async({ it.lock() }) { }
                prefs.forgetVault()
                finish()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
        ui.styleDialog(dialog)
    }
}
