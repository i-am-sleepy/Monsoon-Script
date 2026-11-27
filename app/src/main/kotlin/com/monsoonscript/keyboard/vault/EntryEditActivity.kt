package com.monsoonscript.keyboard.vault

import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.content.res.ColorStateList
import android.net.Uri
import android.os.Bundle
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.text.format.Formatter
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import com.monsoonscript.keyboard.R
import com.monsoonscript.vault.AttachmentItem
import com.monsoonscript.vault.EntryDetail
import com.monsoonscript.vault.EntryDraft
import com.monsoonscript.vault.FieldItem
import com.monsoonscript.vault.NewAttachment
import com.monsoonscript.vault.OtpKind

/**
 * Adding and editing an entry (E1.13): title, user name, password (with the generator), address, notes and any number of custom fields (each can be protected).
 * A stored password or protected field is never loaded into the form (D43): an empty box with "Unchanged" keeps it, anything typed replaces it.
 * Saving writes the vault (about a second) and goes back.
 */
internal class EntryEditActivity : VaultBaseActivity() {
    private var entryId: String? = null
    private lateinit var groupId: String
    private var detail: EntryDetail? = null
    private lateinit var titleBox: EditText
    private lateinit var username: EditText
    private lateinit var password: PasswordField
    private lateinit var url: EditText
    private lateinit var notes: EditText
    private lateinit var fieldsBox: LinearLayout
    private val editors = ArrayList<FieldEditor>()

    // attachments (E1.17, D97): what the entry has, what the owner removed, and the files picked to add (read by the vault process when saving)
    private lateinit var attachBox: LinearLayout
    private var stored: List<AttachmentItem> = emptyList()
    private val removed = LinkedHashSet<String>()
    private val picked = ArrayList<Picked>()

    private class Picked(val name: String, val size: Long, val uri: Uri)
    private var passwordTouched = false
    private var startSnapshot = ""

    // one-time code (E1.18, D98): a typed key replaces the stored one, empty keeps it, "remove" deletes it
    private lateinit var otpBox: EditText
    private lateinit var otpResult: TextView
    private var otpRemove = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.setSoftInputMode(android.view.WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
        entryId = intent.getStringExtra(EXTRA_ID)
        groupId = intent.getStringExtra(EXTRA_GROUP) ?: run { finish(); return }
        val id = entryId
        if (id == null) {
            build(null)
        } else {
            setContentView(View(this).apply { setBackgroundColor(ui.colors.bg) })
            call({ it.entry(id) }, fail = { e ->
                report(e)
                if (!VaultHub.isLocked(e)) finish()
            }) { d ->
                detail = d
                build(d)
            }
        }
    }

    private fun build(d: EntryDetail?) {
        val page = ui.page(getString(if (d == null) R.string.vault_new_entry else R.string.vault_edit), onBack = { leave() })
        ui.addToBar(page.bar, ui.barAction(getString(R.string.vault_save)) { save() }) // in the bar, so it is never hidden below the keyboard
        titleBox = ui.textBox(getString(R.string.vault_title_hint), inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES)
        username = ui.textBox(getString(R.string.vault_username), inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS)
        val generate = ui.iconButton(VaultIcons.Kind.REFRESH, getString(R.string.vault_generator), ui.colors.accent) {
            GeneratorDialog.show(this, ui) { text ->
                password.edit.setText(text) // counts as a change by the owner (the watcher below)
                password.edit.setSelection(text.length)
            }
        }
        password = PasswordField(this, ui, getString(if (d == null) R.string.vault_password else R.string.vault_password_unchanged), generate)
        password.edit.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}

            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}

            override fun afterTextChanged(s: Editable?) {
                passwordTouched = true
            }
        })
        url = ui.textBox(getString(R.string.vault_url), inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI)
        notes = ui.textBox(getString(R.string.vault_notes), multiLine = true)
        val hasCode = d != null && (d.otp == OtpKind.TOTP || d.otp == OtpKind.STEAM)
        otpBox = ui.textBox(getString(if (hasCode) R.string.vault_otp_key_set_hint else R.string.vault_otp_key_hint),
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS)
        otpResult = ui.note("") as TextView
        otpResult.visibility = View.GONE
        fieldsBox = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        if (d != null) {
            titleBox.setText(d.summary.title)
            username.setText(d.summary.username)
            url.setText(d.summary.url)
            notes.setText(d.notes)
            d.fields.forEach { addEditor(FieldEditor(it)) }
            stored = d.attachments
        }
        attachBox = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        with(ui) {
            val c = page.content
            c.add(padded(titleBox, 16, 6))
            c.add(padded(username, 16, 6))
            c.add(padded(password, 16, 6))
            c.add(padded(url, 16, 6))
            c.add(padded(notes, 16, 6))
            c.add(header(getString(R.string.vault_otp_label)))
            c.add(padded(otpBox, 16, 6))
            c.add(padded(outlineButton(getString(R.string.vault_otp_test_button)) { testKey() }))
            c.add(otpResult)
            if (hasCode) c.add(padded(outlineButton(getString(R.string.vault_otp_remove)) { toggleRemoveCode() }))
            c.add(note(getString(R.string.vault_otp_edit_hint)))
            c.add(header(getString(R.string.vault_custom_fields)))
            c.add(fieldsBox)
            c.add(padded(outlineButton(getString(R.string.vault_add_field)) { addEditor(FieldEditor(null)) }))
            c.add(header(getString(R.string.vault_attachments)))
            c.add(attachBox)
            c.add(padded(outlineButton(getString(R.string.vault_attach_add)) { pickFile() }))
            c.add(note(getString(R.string.vault_attach_edit_hint)))
            c.add(gap(8))
            c.add(padded(filledButton(getString(R.string.vault_save)) { save() }))
        }
        setContentView(page.root)
        renderAttachments()
        passwordTouched = false // the text watcher fires for the initial empty text only in some versions
        startSnapshot = snapshot()
        if (d == null) titleBox.requestFocus()
    }

    /** Shows the code the typed key gives right now, so a wrong paste is noticed before saving. */
    private fun testKey() {
        val typed = otpBox.text.toString().trim()
        if (typed.isEmpty()) {
            Toast.makeText(this, R.string.vault_otp_key_hint, Toast.LENGTH_SHORT).show()
            return
        }
        call({ it.otpCheck(typed) }, fail = { e ->
            if (VaultHub.isLocked(e)) report(e) else showOtpResult(VaultHub.message(this, e), VAULT_RED)
        }) { c ->
            showOtpResult(getString(R.string.vault_otp_test, OtpFormat.grouped(c.code), c.secondsLeft), ui.colors.accent)
        }
    }

    private fun showOtpResult(text: String, color: Int) {
        otpResult.text = text
        otpResult.setTextColor(color)
        otpResult.visibility = View.VISIBLE
    }

    private fun toggleRemoveCode() {
        otpRemove = !otpRemove
        otpBox.isEnabled = !otpRemove
        if (otpRemove) otpBox.setText("")
        showOtpResult(if (otpRemove) getString(R.string.vault_otp_will_remove) else "", VAULT_RED)
        otpResult.visibility = if (otpRemove) View.VISIBLE else View.GONE
    }

    private fun addEditor(e: FieldEditor) {
        editors += e
        fieldsBox.addView(e.view, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
    }

    /** One custom field: name, value, a "protected" switch and a remove button. [stored] is null for a new one. */
    private inner class FieldEditor(private val stored: FieldItem?) {
        private val keepsSecret = stored != null && stored.value == null
        private var touched = false
        private val name = ui.textBox(getString(R.string.vault_field_name)).apply { setText(stored?.name ?: "") }
        private val value = ui.textBox(getString(if (keepsSecret) R.string.vault_password_unchanged else R.string.vault_field_value), multiLine = false)
        private val protect = Switch(this@EntryEditActivity).apply {
            isChecked = stored?.protect ?: false
            val states = arrayOf(intArrayOf(android.R.attr.state_checked), intArrayOf())
            thumbTintList = ColorStateList(states, intArrayOf(ui.colors.accent, ui.colors.muted))
            trackTintList = ColorStateList(states, intArrayOf(ui.colors.accent, ui.colors.muted))
        }
        val view = LinearLayout(this@EntryEditActivity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(ui.dp(16), ui.dp(6), ui.dp(16), ui.dp(6))
        }

        init {
            if (stored?.value != null) value.setText(stored.value)
            value.addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}

                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}

                override fun afterTextChanged(s: Editable?) {
                    touched = true
                }
            })
            touched = false
            val bottom = LinearLayout(this@EntryEditActivity).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                addView(TextView(this@EntryEditActivity).apply {
                    text = getString(R.string.vault_field_protected)
                    textSize = 15f
                    setTextColor(ui.colors.text)
                }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
                addView(protect)
                addView(ui.iconButton(VaultIcons.Kind.TRASH, getString(R.string.vault_remove_field), VAULT_RED) {
                    editors.remove(this@FieldEditor)
                    fieldsBox.removeView(view)
                })
            }
            view.addView(name, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
            view.addView(value, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = ui.dp(6) })
            view.addView(bottom)
        }

        /** Null while the name is empty (such a field is simply dropped). */
        fun toItem(): FieldItem? {
            val n = name.text.toString().trim()
            if (n.isEmpty()) return null
            val v: String? = if (keepsSecret && !touched) null else value.text.toString()
            return FieldItem(n, v, protect.isChecked)
        }

        fun text(): String = name.text.toString() + "\u0001" + value.text + "\u0001" + protect.isChecked + "\u0001" + touched
    }

    private fun snapshot(): String =
        listOf(titleBox.text, username.text, url.text, notes.text, passwordTouched, otpBox.text, otpRemove, editors.joinToString("\u0002") { it.text() },
            removed.joinToString("\u0002"), picked.joinToString("\u0002") { it.name + "\u0001" + it.uri }).joinToString("\u0003")

    // ------------------------------------------------------------------------------------------------------------------------ attachments

    /** The rows: the entry's files that are still attached (not removed, not replaced by a picked one of the same name), then the picked ones. */
    private fun renderAttachments() {
        attachBox.removeAllViews()
        val pickedNames = picked.map { it.name }.toSet()
        stored.filter { it.name !in removed && it.name !in pickedNames }.forEach { a ->
            attachBox.addView(attachmentRow(a.name, Formatter.formatShortFileSize(this, a.size)) { removed += a.name })
        }
        picked.forEach { p ->
            attachBox.addView(attachmentRow(p.name, if (p.size >= 0) Formatter.formatShortFileSize(this, p.size) else "") { picked.remove(p) })
        }
    }

    private fun attachmentRow(name: String, size: String, remove: () -> Unit): View = ui.row().apply {
        addView(ui.texts(name, size.ifEmpty { null }), ui.weighted())
        addView(ui.iconButton(VaultIcons.Kind.TRASH, getString(R.string.vault_attach_remove), VAULT_RED) {
            remove()
            renderAttachments()
        })
    }

    private fun pickFile() {
        @Suppress("DEPRECATION")
        startActivityForResult(VaultHub.readFileIntent(), REQ_ATTACH)
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        @Suppress("DEPRECATION")
        super.onActivityResult(requestCode, resultCode, data)
        val uri = data?.data
        if (requestCode != REQ_ATTACH || resultCode != RESULT_OK || uri == null) return
        val name = VaultHub.displayName(this, uri).trim().ifEmpty { getString(R.string.vault_attach_default_name) }
        val size = VaultHub.sizeOf(this, uri)
        if (size > MAX_ATTACHMENT_BYTES) { // the vault checks again while reading; this just says it at once
            Toast.makeText(this, getString(R.string.vault_attach_too_big, name), Toast.LENGTH_LONG).show()
            return
        }
        picked.removeAll { it.name == name } // the same name picked twice: the newer file wins
        picked += Picked(name, size, uri)
        renderAttachments()
    }

    private fun leave() {
        if (snapshot() == startSnapshot) {
            finish()
            return
        }
        val dialog = AlertDialog.Builder(this)
            .setTitle(R.string.vault_discard_title)
            .setMessage(R.string.vault_discard_text)
            .setPositiveButton(R.string.vault_discard) { _, _ -> finish() }
            .setNegativeButton(R.string.vault_keep_editing, null)
            .show()
        ui.styleDialog(dialog)
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        leave()
    }

    private fun save() {
        val newPassword: String? = if (entryId == null || passwordTouched) String(password.chars()) else null
        val draft = EntryDraft(
            id = entryId,
            groupId = groupId,
            title = titleBox.text.toString().trim(),
            username = username.text.toString().trim(),
            password = newPassword,
            url = url.text.toString().trim(),
            notes = notes.text.toString(),
            fields = editors.mapNotNull { it.toItem() },
            removeAttachments = removed.toList(),
            newAttachments = picked.map { NewAttachment(it.name, it.uri) },
            otp = if (otpRemove) "" else otpBox.text.toString().trim().ifEmpty { null },
        )
        change(R.string.vault_saving, { it.saveEntry(draft) }) {
            setResult(RESULT_OK)
            finish()
        }
    }

    companion object {
        const val EXTRA_ID = "id"
        const val EXTRA_GROUP = "group"
        private const val REQ_ATTACH = 31
        private const val MAX_ATTACHMENT_BYTES = 10L * 1024 * 1024 // D97; the vault process enforces it too

        fun intent(context: Context, id: String?, groupId: String): Intent =
            Intent(context, EntryEditActivity::class.java).putExtra(EXTRA_ID, id).putExtra(EXTRA_GROUP, groupId)
    }
}
