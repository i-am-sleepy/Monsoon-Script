package com.monsoonscript.keyboard.vault

import android.app.AlertDialog
import android.content.res.ColorStateList
import android.os.Bundle
import android.view.View
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import com.monsoonscript.keyboard.R
import com.monsoonscript.vault.VaultInfo

/**
 * The vault's settings (E1.13): how long it stays unlocked (D38), the fingerprint switch (E1.15), facts about the open vault, locking it and choosing another one.
 * Backup screens and so on come in later phases (D51).
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
            val store = FingerprintStore(this@VaultSettingsActivity)
            if (store.availability() == FingerprintStore.Availability.UNSUPPORTED) {
                c.add(note(getString(R.string.fp_setting_no_sensor)))
            } else {
                c.add(fingerprintRow(store))
                c.add(note(getString(R.string.fp_setting_note)))
            }
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

    /**
     * "Unlock with fingerprint" (D91): turning it on only says "finish at the next master password unlock" (the password is not kept anywhere to set it up right
     * now); turning it off throws the key and the stored password away at once. Looks like the other switch rows.
     */
    private fun fingerprintRow(store: FingerprintStore): View {
        val ready = store.availability() == FingerprintStore.Availability.READY
        fun summary(): String = when {
            !ready -> getString(R.string.fp_setting_not_enrolled)
            !prefs.fingerprintOn -> getString(R.string.fp_setting_off)
            store.armed -> getString(R.string.fp_setting_on)
            else -> getString(R.string.fp_setting_pending)
        }
        val labels = ui.texts(getString(R.string.fp_setting_title), summary())
        val summaryView = labels.getChildAt(1) as TextView
        val sw = Switch(this)
        var quiet = true
        sw.isChecked = prefs.fingerprintOn && ready
        val states = arrayOf(intArrayOf(android.R.attr.state_checked), intArrayOf())
        sw.thumbTintList = ColorStateList(states, intArrayOf(ui.colors.accent, ui.colors.muted))
        sw.trackTintList = ColorStateList(states, intArrayOf(ui.colors.accent, ui.colors.muted))
        sw.setOnCheckedChangeListener { _, on ->
            if (quiet) return@setOnCheckedChangeListener
            if (on && !ready) {
                quiet = true
                sw.isChecked = false
                quiet = false
                Toast.makeText(this, R.string.fp_setting_not_enrolled, Toast.LENGTH_LONG).show()
                return@setOnCheckedChangeListener
            }
            prefs.fingerprintOn = on
            summaryView.text = summary()
        }
        quiet = false
        return ui.row().apply {
            addView(labels, ui.weighted())
            addView(sw)
            setOnClickListener { sw.toggle() }
        }
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
