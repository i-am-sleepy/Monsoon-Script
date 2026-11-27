package com.monsoonscript.keyboard.vault

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.res.ColorStateList
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.graphics.Typeface
import android.text.format.Formatter
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import com.monsoonscript.keyboard.R
import com.monsoonscript.vault.AttachmentItem
import com.monsoonscript.vault.EntryDetail
import com.monsoonscript.vault.IVault
import com.monsoonscript.vault.OtpCode
import com.monsoonscript.vault.OtpKind
import java.text.DateFormat
import java.util.Date

/**
 * One entry (E1.13, D33): the fields as rows with a copy button, the password masked until the owner taps the eye (D43: never shown by default, hidden again
 * after 30 seconds and whenever the screen is left). Edit and delete are in the bar; delete is instant and the list offers Undo (D81).
 */
internal class EntryActivity : VaultBaseActivity() {
    private lateinit var entryId: String
    private lateinit var groupId: String
    private var detail: EntryDetail? = null
    private val secrets = ArrayList<SecretRow>()
    private var codeRow: CodeRow? = null
    private val main = Handler(Looper.getMainLooper())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        entryId = intent.getStringExtra(EXTRA_ID) ?: run { finish(); return }
        groupId = intent.getStringExtra(EXTRA_GROUP) ?: ""
        setContentView(View(this).apply { setBackgroundColor(ui.colors.bg) })
    }

    override fun onStart() {
        super.onStart()
        load()
    }

    override fun onPause() {
        secrets.forEach { it.hide() } // nothing revealed is left on screen when the owner comes back
        codeRow?.stop()
        super.onPause()
    }

    override fun onDestroy() {
        main.removeCallbacksAndMessages(null)
        super.onDestroy()
    }

    private fun load() {
        call({ it.entry(entryId) }, fail = { e ->
            report(e)
            if (!VaultHub.isLocked(e)) finish() // the entry is gone (changed by a merge or deleted)
        }) { d ->
            detail = d
            groupId = d.summary.groupId.ifEmpty { groupId }
            build(d)
        }
    }

    private fun build(d: EntryDetail) {
        secrets.clear()
        codeRow?.stop()
        codeRow = null
        val title = d.summary.title.ifEmpty { getString(R.string.vault_untitled) }
        val page = ui.page(title, onBack = { finish() })
        ui.addToBar(page.bar,
            ui.iconButton(VaultIcons.Kind.EDIT, getString(R.string.vault_edit), ui.colors.text) { edit() },
            ui.iconButton(VaultIcons.Kind.TRASH, getString(R.string.vault_delete_entry), VAULT_RED) { delete(d) })
        with(ui) {
            val c = page.content
            if (d.summary.username.isNotEmpty()) c.add(valueRow(getString(R.string.vault_username), d.summary.username, getString(R.string.vault_username_copied)))
            if (d.summary.hasPassword) {
                c.add(secretRow(getString(R.string.vault_password), getString(R.string.vault_password_copied)) { v -> passwordOf(v) })
            }
            if (d.otp == OtpKind.TOTP || d.otp == OtpKind.STEAM) { // E1.18: the live one-time code
                val r = CodeRow()
                codeRow = r
                c.add(r.row)
                r.start()
            } else if (d.otp == OtpKind.HOTP) {
                c.add(note(getString(R.string.vault_otp_unsupported)))
            }
            if (d.summary.url.isNotEmpty()) c.add(valueRow(getString(R.string.vault_url), d.summary.url, getString(R.string.vault_url_copied), open = true))
            for (f in d.fields) {
                val value = f.value
                if (f.protect || value == null) c.add(secretRow(f.name, getString(R.string.vault_field_copied, f.name)) { v -> v.fieldValue(entryId, f.name) ?: "" })
                else if (value.isNotEmpty()) c.add(valueRow(f.name, value, getString(R.string.vault_field_copied, f.name)))
            }
            if (d.notes.isNotEmpty()) c.add(valueRow(getString(R.string.vault_notes), d.notes, getString(R.string.vault_notes_copied)))
            if (c.childCount == 0 && d.attachments.isEmpty()) c.add(note(getString(R.string.vault_entry_empty)))
            if (d.attachments.isNotEmpty()) { // E1.17: a tap saves a decrypted copy to a place the owner picks
                c.add(header(getString(R.string.vault_attachments)))
                d.attachments.forEach { c.add(attachmentRow(it)) }
                c.add(note(getString(R.string.vault_attach_hint)))
            }
            c.add(header(getString(R.string.vault_details)))
            val fmt = DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT)
            c.add(infoRow(getString(R.string.vault_modified), fmt.format(Date(d.summary.modified))))
            c.add(infoRow(getString(R.string.vault_created_on), fmt.format(Date(d.created))))
            d.expires?.let { c.add(infoRow(getString(R.string.vault_expires), fmt.format(Date(it)))) }
            if (d.historyCount > 0) c.add(infoRow(getString(R.string.vault_history), resources.getQuantityString(R.plurals.vault_old_versions, d.historyCount, d.historyCount)))
        }
        setContentView(page.root)
    }

    private fun passwordOf(v: IVault): String {
        val chars = v.password(entryId)
        try {
            return String(chars)
        } finally {
            chars.fill('\u0000')
        }
    }

    // ------------------------------------------------------------------------------------------------------------------------ rows

    /** A plain field: label, value, copy (and an open-in-browser button for the address). */
    private fun valueRow(label: String, value: String, copiedText: String, open: Boolean = false): View {
        val row = ui.row()
        val texts = ui.texts(label, null)
        texts.getChildAt(0).let { (it as TextView).textSize = 13f; it.setTextColor(ui.colors.muted) }
        texts.addView(TextView(this).apply {
            text = value
            textSize = 17f
            setTextColor(ui.colors.text)
            setTextIsSelectable(true)
        })
        row.addView(texts, ui.weighted())
        if (open) row.addView(ui.iconButton(VaultIcons.Kind.LINK, getString(R.string.vault_open_link), ui.colors.muted) { openLink(value) })
        row.addView(ui.iconButton(VaultIcons.Kind.COPY, getString(R.string.vault_copy), ui.colors.muted) { VaultClip.copy(this, value, copiedText, secret = false) })
        return row
    }

    /** A secret field: masked, an eye to show it for a while, copy (the clipboard is cleared after 30 seconds, D43). */
    private fun secretRow(label: String, copiedText: String, fetch: (IVault) -> String): View {
        val s = SecretRow(label, copiedText, fetch)
        secrets += s
        return s.row
    }

    private inner class SecretRow(label: String, private val copiedText: String, private val fetch: (IVault) -> String) {
        val row = ui.row()
        private val value = TextView(this@EntryActivity).apply {
            text = MASK
            textSize = 17f
            setTextColor(ui.colors.text)
        }
        private val eye: ImageView
        private var shown = false
        private val hideLater = Runnable { hide() }

        init {
            val texts = ui.texts(label, null)
            texts.getChildAt(0).let { (it as TextView).textSize = 13f; it.setTextColor(ui.colors.muted) }
            texts.addView(value)
            row.addView(texts, ui.weighted())
            eye = ui.iconButton(VaultIcons.Kind.EYE, getString(R.string.vault_show_hide), ui.colors.muted) { toggle() }
            row.addView(eye)
            row.addView(ui.iconButton(VaultIcons.Kind.COPY, getString(R.string.vault_copy), ui.colors.muted) { copy() })
            row.setOnClickListener { toggle() }
        }

        private fun toggle() {
            if (shown) {
                hide()
                return
            }
            call({ fetch(it) }) { text ->
                value.text = if (text.isEmpty()) getString(R.string.vault_empty_value) else text
                shown = true
                eye.setImageDrawable(VaultIcons.drawable(VaultIcons.Kind.EYE_OFF, ui.colors.muted, ui.dp(24)))
                main.removeCallbacks(hideLater)
                main.postDelayed(hideLater, REVEAL_MS)
            }
        }

        fun hide() {
            main.removeCallbacks(hideLater)
            shown = false
            value.text = MASK
            eye.setImageDrawable(VaultIcons.drawable(VaultIcons.Kind.EYE, ui.colors.muted, ui.dp(24)))
        }

        private fun copy() {
            call({ fetch(it) }) { text ->
                if (text.isEmpty()) Toast.makeText(this@EntryActivity, R.string.vault_no_password, Toast.LENGTH_SHORT).show()
                else VaultClip.copy(this@EntryActivity, text, copiedText)
            }
        }
    }

    /**
     * The one-time code (E1.18, D98): big grouped digits, the seconds left with a bar, tap to copy. The vault is asked for the code only when it is
     * shown and again when it has run out (the countdown between is counted here); the secret key never reaches this screen.
     */
    private inner class CodeRow {
        val row = ui.row()
        private val digits = TextView(this@EntryActivity).apply {
            textSize = 30f
            typeface = Typeface.MONOSPACE
            letterSpacing = 0.04f
            setTextColor(ui.colors.accent)
            text = "••• •••"
        }
        private val left = TextView(this@EntryActivity).apply {
            textSize = 13f
            setTextColor(ui.colors.muted)
        }
        private val bar = ProgressBar(this@EntryActivity, null, android.R.attr.progressBarStyleHorizontal).apply {
            progressTintList = ColorStateList.valueOf(ui.colors.accent)
            progressBackgroundTintList = ColorStateList.valueOf(ui.colors.muted)
            alpha = 0.8f
        }
        private var code: OtpCode? = null
        private var endsAt = 0L
        private var running = false
        private val tick = Runnable { onTick() }

        init {
            val texts = ui.texts(getString(R.string.vault_otp_label), null)
            texts.getChildAt(0).let { (it as TextView).textSize = 13f; it.setTextColor(ui.colors.muted) }
            texts.addView(digits)
            texts.addView(bar, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, ui.dp(4)).apply { topMargin = ui.dp(4); bottomMargin = ui.dp(2) })
            texts.addView(left)
            row.addView(texts, ui.weighted())
            row.addView(ui.iconButton(VaultIcons.Kind.COPY, getString(R.string.vault_copy), ui.colors.muted) { copy() })
            row.setOnClickListener { copy() }
        }

        fun start() {
            running = true
            fetch()
        }

        fun stop() {
            running = false
            main.removeCallbacks(tick)
        }

        private fun fetch() {
            call({ it.otpCode(entryId) }, fail = { stop(); report(it) }) { c ->
                if (!running) return@call
                if (c == null) {
                    stop()
                    digits.text = "—"
                    left.text = ""
                    return@call
                }
                code = c
                endsAt = SystemClock.elapsedRealtime() + c.secondsLeft * 1000L
                digits.text = OtpFormat.grouped(c.code)
                bar.max = c.period
                onTick()
            }
        }

        private fun onTick() {
            main.removeCallbacks(tick)
            val c = code ?: return
            val ms = endsAt - SystemClock.elapsedRealtime()
            val shown = OtpFormat.secondsShown(ms)
            if (shown <= 0) { // ran out: the next code comes from the vault
                fetch()
                return
            }
            left.text = getString(R.string.vault_otp_changes_in, shown)
            left.setTextColor(if (shown <= OtpFormat.MIN_SECONDS_TO_TYPE) VAULT_RED else ui.colors.muted)
            bar.progress = shown
            if (running) main.postDelayed(tick, OtpFormat.nextTickDelay(ms))
        }

        private fun copy() {
            val c = code ?: return
            VaultClip.copy(this@EntryActivity, c.code, getString(R.string.vault_otp_copied))
        }
    }

    private fun openLink(address: String) {
        val a = address.trim()
        val withScheme = if (a.contains("://")) a else "https://$a"
        try {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(withScheme)))
        } catch (_: ActivityNotFoundException) {
            Toast.makeText(this, R.string.vault_no_browser, Toast.LENGTH_SHORT).show()
        }
    }

    // ------------------------------------------------------------------------------------------------------------------------ edit and delete

    private fun attachmentRow(a: AttachmentItem): View = ui.row().apply {
        addView(ui.texts(a.name, Formatter.formatShortFileSize(this@EntryActivity, a.size)), ui.weighted())
        addView(ui.iconButton(VaultIcons.Kind.SAVE_FILE, getString(R.string.vault_attachments), ui.colors.accent) { pickPlaceFor(a) })
        setOnClickListener { pickPlaceFor(a) }
    }

    private var savingAttachment: String? = null

    private fun pickPlaceFor(a: AttachmentItem) {
        savingAttachment = a.name
        @Suppress("DEPRECATION")
        startActivityForResult(VaultHub.createIntent(a.name), REQ_SAVE_FILE)
    }

    /** The decrypted file goes straight from the vault process to the place that was picked. */
    private fun saveAttachment(name: String, dest: Uri) {
        busy.show(getString(R.string.vault_attach_saving))
        call({ v -> v.saveAttachment(entryId, name, dest) }, fail = { report(it) }) {
            busy.hide()
            Toast.makeText(this, R.string.vault_attach_saved, Toast.LENGTH_LONG).show()
        }
    }

    private fun edit() {
        @Suppress("DEPRECATION")
        startActivityForResult(EntryEditActivity.intent(this, entryId, groupId), REQ_EDIT)
    }

    private fun delete(d: EntryDetail) {
        change(R.string.vault_saving, { it.deleteEntry(entryId) }) {
            setResult(RESULT_OK, Intent()
                .putExtra(EXTRA_DELETED_ID, entryId)
                .putExtra(EXTRA_DELETED_FROM, groupId)
                .putExtra(EXTRA_DELETED_TITLE, d.summary.title))
            finish()
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        @Suppress("DEPRECATION")
        super.onActivityResult(requestCode, resultCode, data)
        // the list and this page reload in onStart
        val uri = data?.data
        val name = savingAttachment
        if (requestCode == REQ_SAVE_FILE && resultCode == RESULT_OK && uri != null && name != null) saveAttachment(name, uri)
    }

    companion object {
        const val EXTRA_ID = "id"
        const val EXTRA_GROUP = "group"
        const val EXTRA_DELETED_ID = "deleted_id"
        const val EXTRA_DELETED_FROM = "deleted_from"
        const val EXTRA_DELETED_TITLE = "deleted_title"
        private const val REQ_EDIT = 1
        private const val REQ_SAVE_FILE = 2
        private const val REVEAL_MS = 30_000L
        private const val MASK = "••••••••"

        fun intent(context: Context, id: String, groupId: String): Intent =
            Intent(context, EntryActivity::class.java).putExtra(EXTRA_ID, id).putExtra(EXTRA_GROUP, groupId)
    }
}
