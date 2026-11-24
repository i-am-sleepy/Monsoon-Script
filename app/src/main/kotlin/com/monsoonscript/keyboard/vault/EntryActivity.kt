package com.monsoonscript.keyboard.vault

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.TextUtils
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import com.monsoonscript.keyboard.R
import com.monsoonscript.vault.EntryDetail
import com.monsoonscript.vault.IVault
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
        val title = d.summary.title.ifEmpty { getString(R.string.vault_untitled) }
        val page = ui.page(title, onBack = { finish() })
        page.bar?.apply {
            (getChildAt(childCount - 1) as? TextView)?.apply {
                maxLines = 1
                ellipsize = TextUtils.TruncateAt.END
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            }
            addView(ui.iconButton(VaultIcons.Kind.EDIT, getString(R.string.vault_edit), ui.colors.text) { edit() })
            addView(ui.iconButton(VaultIcons.Kind.TRASH, getString(R.string.vault_delete_entry), VAULT_RED) { delete(d) })
        }
        with(ui) {
            val c = page.content
            if (d.summary.username.isNotEmpty()) c.add(valueRow(getString(R.string.vault_username), d.summary.username, getString(R.string.vault_username_copied)))
            if (d.summary.hasPassword) {
                c.add(secretRow(getString(R.string.vault_password), getString(R.string.vault_password_copied)) { v -> passwordOf(v) })
            }
            if (d.summary.url.isNotEmpty()) c.add(valueRow(getString(R.string.vault_url), d.summary.url, getString(R.string.vault_url_copied), open = true))
            for (f in d.fields) {
                val value = f.value
                if (f.protect || value == null) c.add(secretRow(f.name, getString(R.string.vault_field_copied, f.name)) { v -> v.fieldValue(entryId, f.name) ?: "" })
                else if (value.isNotEmpty()) c.add(valueRow(f.name, value, getString(R.string.vault_field_copied, f.name)))
            }
            if (d.notes.isNotEmpty()) c.add(valueRow(getString(R.string.vault_notes), d.notes, getString(R.string.vault_notes_copied)))
            if (c.childCount == 0) c.add(note(getString(R.string.vault_entry_empty)))
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
    }

    companion object {
        const val EXTRA_ID = "id"
        const val EXTRA_GROUP = "group"
        const val EXTRA_DELETED_ID = "deleted_id"
        const val EXTRA_DELETED_FROM = "deleted_from"
        const val EXTRA_DELETED_TITLE = "deleted_title"
        private const val REQ_EDIT = 1
        private const val REVEAL_MS = 30_000L
        private const val MASK = "••••••••"

        fun intent(context: Context, id: String, groupId: String): Intent =
            Intent(context, EntryActivity::class.java).putExtra(EXTRA_ID, id).putExtra(EXTRA_GROUP, groupId)
    }
}
