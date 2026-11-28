package com.monsoonscript.keyboard.vault

import android.app.AlertDialog
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import com.monsoonscript.keyboard.R
import com.monsoonscript.keyboard.autofill.AutofillSetup
import com.monsoonscript.keyboard.settings.Icons

/**
 * Creating a vault (E1.13, D81): ONE scrolling screen with the name, the master password twice (with a rough strength hint), an optional key file and the place
 * to save it (the file picker, D35), then Create. The encryption is the upstream default (KDBX 4, Argon2id + AES) tuned to open in about a second on this phone.
 */
internal class VaultCreateActivity : VaultBaseActivity() {
    override val needsUnlocked: Boolean get() = false

    private var location: Uri? = null
    private var locationWritable = true
    private var keyFile: Uri? = null
    private lateinit var nameBox: android.widget.EditText
    private lateinit var password: PasswordField
    private lateinit var confirm: PasswordField
    private lateinit var strength: TextView
    private lateinit var error: TextView
    private lateinit var locationSummary: TextView
    private lateinit var keySummary: TextView
    private lateinit var keyClear: View

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.setSoftInputMode(android.view.WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
        val page = ui.page(getString(R.string.vault_create_title), onBack = { finish() })
        ui.addToBar(page.bar, ui.barAction(getString(R.string.vault_create_button)) { create() }) // in the bar, so it is never hidden below the keyboard
        nameBox = ui.textBox(getString(R.string.vault_name_hint)).apply { setText(getString(R.string.vault_default_name)) }
        password = PasswordField(this, ui, getString(R.string.vault_master_password))
        confirm = PasswordField(this, ui, getString(R.string.vault_master_password_again))
        strength = TextView(this).apply {
            textSize = 13f
            setTextColor(ui.colors.muted)
        }
        error = TextView(this).apply {
            textSize = 14f
            setTextColor(VAULT_RED)
            visibility = View.GONE
        }
        password.edit.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}

            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}

            override fun afterTextChanged(s: Editable?) {
                showStrength(s ?: "")
            }
        })

        val locationRow = ui.row()
        val locationTexts = ui.texts(getString(R.string.vault_location), getString(R.string.vault_location_none))
        locationSummary = locationTexts.getChildAt(1) as TextView
        locationRow.addView(ui.vaultChip(VaultIcons.Kind.SAVE_FILE), LinearLayout.LayoutParams(ui.dp(40), ui.dp(40)).apply { rightMargin = ui.dp(16) })
        locationRow.addView(locationTexts, ui.weighted())
        locationRow.addView(ui.chevron())
        locationRow.setOnClickListener {
            val base = nameBox.text.toString().trim().ifEmpty { getString(R.string.vault_default_name) }
            @Suppress("DEPRECATION")
            startActivityForResult(VaultHub.createIntent("$base.kdbx"), REQ_LOCATION)
        }

        val keyRow = ui.row()
        val keyTexts = ui.texts(getString(R.string.vault_key_file), getString(R.string.vault_key_file_none))
        keySummary = keyTexts.getChildAt(1) as TextView
        keyClear = ImageView(this).apply {
            setImageDrawable(ui.icon(Icons.Kind.CLOSE, ui.colors.muted, 20))
            scaleType = ImageView.ScaleType.CENTER
            contentDescription = getString(R.string.vault_remove_key_file)
            visibility = View.GONE
            setOnClickListener { setKeyFile(null) }
        }
        keyRow.addView(ui.vaultChip(VaultIcons.Kind.KEY), LinearLayout.LayoutParams(ui.dp(40), ui.dp(40)).apply { rightMargin = ui.dp(16) })
        keyRow.addView(keyTexts, ui.weighted())
        keyRow.addView(keyClear, LinearLayout.LayoutParams(ui.dp(40), ui.dp(40)))
        keyRow.setOnClickListener {
            @Suppress("DEPRECATION")
            startActivityForResult(VaultHub.keyFileIntent(), REQ_KEYFILE)
        }

        with(ui) {
            val c = page.content
            c.add(header(getString(R.string.vault_name_header)))
            c.add(padded(nameBox, 16, 4))
            c.add(header(getString(R.string.vault_master_password)))
            c.add(padded(password, 16, 4))
            c.add(padded(strength, 24, 0))
            c.add(padded(confirm, 16, 8))
            c.add(note(getString(R.string.vault_no_recovery)))
            c.add(header(getString(R.string.vault_options_header)))
            c.add(keyRow)
            c.add(locationRow)
            c.add(gap(12))
            c.add(padded(error, 20, 0))
            c.add(padded(filledButton(getString(R.string.vault_create_button)) { create() }))
            c.add(note(getString(R.string.vault_create_note)))
        }
        setContentView(page.root)
    }

    private fun showStrength(text: CharSequence) {
        val level = PasswordStrength.level(text)
        val (label, color) = when (level) {
            PasswordStrength.Level.EMPTY -> "" to ui.colors.muted
            PasswordStrength.Level.WEAK -> getString(R.string.vault_strength_weak) to VAULT_RED
            PasswordStrength.Level.FAIR -> getString(R.string.vault_strength_fair) to 0xFFE0A030.toInt()
            PasswordStrength.Level.GOOD -> getString(R.string.vault_strength_good) to 0xFF5DAE5D.toInt()
            PasswordStrength.Level.STRONG -> getString(R.string.vault_strength_strong) to 0xFF3E9E5A.toInt()
        }
        strength.text = if (label.isEmpty()) "" else getString(R.string.vault_strength, label)
        strength.setTextColor(color)
    }

    private fun setKeyFile(uri: Uri?) {
        keyFile = uri
        keySummary.text = if (uri == null) getString(R.string.vault_key_file_none) else VaultHub.displayName(this, uri).ifEmpty { getString(R.string.vault_key_file_none) }
        keyClear.visibility = if (uri == null) View.GONE else View.VISIBLE
    }

    private fun fail(text: String) {
        error.text = text
        error.visibility = View.VISIBLE
    }

    private fun create() {
        val pw = password.chars()
        val again = confirm.chars()
        val where = location
        val name = nameBox.text.toString().trim().ifEmpty { getString(R.string.vault_default_name) }
        val problem = when {
            pw.isEmpty() && keyFile == null -> getString(R.string.vault_need_password)
            !pw.contentEquals(again) -> getString(R.string.vault_passwords_differ)
            where == null -> getString(R.string.vault_need_location)
            !locationWritable -> getString(R.string.vault_location_read_only)
            else -> null
        }
        again.fill('\u0000')
        if (problem != null) {
            pw.fill('\u0000')
            fail(problem)
            return
        }
        error.visibility = View.GONE
        val key = keyFile
        val uri = where!!
        val autoLock = prefs.autoLockMs
        busy.show(getString(R.string.vault_creating))
        call({ v ->
            val keyBytes = key?.let { VaultHub.readKeyFile(this, it) }
            try {
                v.create(uri, name, pw, keyBytes, true)
                v.setAutoLock(autoLock)
            } finally {
                pw.fill('\u0000')
            }
        }, fail = { e ->
            busy.hide()
            fail(VaultHub.message(this, e))
        }) {
            busy.hide()
            prefs.fileUri = uri.toString()
            prefs.fileName = VaultHub.displayName(this, uri).ifEmpty { name }
            prefs.writable = true
            prefs.keyFileUri = key?.toString()
            password.clear()
            confirm.clear()
            offerAutofill()
        }
    }

    /** The vault is there: once, ask whether to turn on autofill (D100), then go to the vault. Not asked again, nor on phones where it is on or unsupported. */
    private fun offerAutofill() {
        if (prefs.autofillAsked || !AutofillSetup.supported(this) || AutofillSetup.isOn(this)) return openVault()
        prefs.autofillAsked = true
        val dialog = AlertDialog.Builder(this)
            .setTitle(R.string.autofill_card_title)
            .setMessage(R.string.autofill_card_text)
            .setPositiveButton(R.string.autofill_card_yes) { _, _ -> if (!AutofillSetup.request(this, REQ_AUTOFILL)) openVault() }
            .setNegativeButton(R.string.autofill_card_later) { _, _ -> openVault() }
            .setCancelable(false)
            .show()
        ui.styleDialog(dialog)
    }

    private fun openVault() {
        startActivity(Intent(this, VaultMainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP))
        finish()
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        @Suppress("DEPRECATION")
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQ_AUTOFILL) { // Android's question "make this your autofill service?" is answered (yes or no): on to the vault
            openVault()
            return
        }
        val uri = data?.data
        if (resultCode != RESULT_OK || uri == null || data == null) return
        when (requestCode) {
            REQ_LOCATION -> {
                locationWritable = VaultHub.keepAccess(this, uri, data)
                location = uri
                locationSummary.text = VaultHub.displayName(this, uri).ifEmpty { uri.toString() }
            }
            REQ_KEYFILE -> {
                try {
                    contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
                } catch (_: SecurityException) {
                }
                setKeyFile(uri)
            }
        }
    }

    private companion object {
        const val REQ_LOCATION = 1
        const val REQ_KEYFILE = 2
        const val REQ_AUTOFILL = 3
    }
}
