package com.monsoonscript.keyboard.autofill

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.graphics.Typeface
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import com.monsoonscript.keyboard.R
import com.monsoonscript.keyboard.settings.SettingsUi
import com.monsoonscript.keyboard.vault.BusyDialog
import com.monsoonscript.keyboard.vault.EntryActivity
import com.monsoonscript.keyboard.vault.PasswordField
import com.monsoonscript.keyboard.vault.VaultHub
import com.monsoonscript.keyboard.vault.VaultPrefs
import com.monsoonscript.keyboard.vault.styleDialog
import com.monsoonscript.keyboard.vault.textBox
import com.monsoonscript.vault.EntryDraft
import com.monsoonscript.vault.EntrySummary
import com.monsoonscript.vault.FieldItem
import com.monsoonscript.vault.IVault
import com.monsoonscript.vault.VaultInfo

/**
 * The small save screen after a login was typed in another app or a browser (E1.20, D100). It opens as a see-through card over the app (like the unlock card),
 * asks the vault to open first if it is locked, and then, depending on what is stored: nothing (the same login is already there), "Update the saved password?"
 * (same site and user name, other password) or the form for a new entry (title, folder, user name, password). With the setting "Save at once" the form is
 * skipped and the entry goes into the main folder, followed by a card that opens it. The login arrives through [SaveHandoff], never in an Intent.
 */
internal class AutofillSaveActivity : Activity() {
    private class Folder(val id: String, val label: String)
    private class Prep(val decision: SaveDecision, val existing: EntrySummary?, val folders: List<Folder>, val defaultTitle: String)

    private lateinit var prefs: VaultPrefs
    private lateinit var ui: SettingsUi
    private lateinit var busy: BusyDialog
    private lateinit var root: FrameLayout
    private var pending: PendingSave? = null
    private var prep: Prep? = null
    private var folder: Folder? = null
    private var gate: UnlockGate? = null
    private val autoClose = Runnable { finish() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_SECURE) // the password is on this screen
        window.decorView.importantForAutofill = View.IMPORTANT_FOR_AUTOFILL_NO_EXCLUDE_DESCENDANTS // no Autofill service may offer to save this very form again
        window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE or WindowManager.LayoutParams.SOFT_INPUT_STATE_HIDDEN)
        prefs = VaultPrefs(this)
        ui = SettingsUi(this, prefs.raw)
        busy = BusyDialog(this, ui)
        val p = SaveHandoff.take(intent.getStringExtra(X_TOKEN))
        if (p == null || savedInstanceState != null) { // nothing waiting (took too long, or recreated): the login is gone, nothing to show
            finish()
            return
        }
        pending = p
        root = FrameLayout(this).apply { setBackgroundColor(0x99000000.toInt()) } // dims the app behind; it stays visible
        setContentView(root)
        gate = UnlockGate(this, ::ready, ::finish)
    }

    override fun onResume() {
        super.onResume()
        gate?.resume()
    }

    override fun onDestroy() {
        if (::root.isInitialized) root.removeCallbacks(autoClose)
        if (::busy.isInitialized) busy.hide()
        pending = null
        super.onDestroy()
    }

    // ------------------------------------------------------------------------------------------------------------------------ what is stored

    /** The vault is open: look at what it holds for this site or app. */
    private fun ready() {
        val p = pending ?: return finish()
        val label = try {
            packageManager.getApplicationLabel(packageManager.getApplicationInfo(p.packageName, 0)).toString()
        } catch (_: Exception) {
            null // not visible to us (Android 11 package visibility): the package name stands in
        }
        val mainFolder = getString(R.string.autofill_save_main_folder)
        VaultHub.client(this).async({ v -> prepare(v, p, label, mainFolder) }) { r -> r.fold(::decided, ::failed) }
    }

    private fun prepare(v: IVault, p: PendingSave, appLabel: String?, mainFolder: String): Prep {
        val info = v.info()
        val same = if (p.username.isBlank()) emptyList() else v.matches(p.webDomain ?: "", p.packageName).filter { it.username.equals(p.username.trim(), ignoreCase = true) }
        val candidates = same.map { Candidate(it.id, it.username, samePassword(v, it.id, p.password)) }
        val decision = SaveRules.decide(p.username, candidates)
        val existing = (decision as? SaveDecision.Update)?.let { u -> same.first { it.id == u.id } }
        val folders = if (decision is SaveDecision.Nothing) emptyList() else allFolders(v, info, mainFolder)
        return Prep(decision, existing, folders, SaveRules.defaultTitle(p.webDomain, p.packageName, appLabel))
    }

    private fun samePassword(v: IVault, id: String, password: String): Boolean {
        val chars = v.password(id)
        return try {
            String(chars) == password
        } finally {
            chars.fill('\u0000')
        }
    }

    /** Every folder of the vault, the main one first (the Recycle Bin left out), each named by its path. */
    private fun allFolders(v: IVault, info: VaultInfo, mainFolder: String): List<Folder> {
        val out = ArrayList<Folder>()
        fun walk(id: String, path: String, label: String, depth: Int) {
            if (out.size >= MAX_FOLDERS) return
            out += Folder(id, label)
            if (depth >= MAX_DEPTH) return
            for (g in v.list(id).groups) {
                if (g.id == info.recycleBinId) continue
                walk(g.id, if (path.isEmpty()) g.name else "$path / ${g.name}", if (path.isEmpty()) g.name else "$path / ${g.name}", depth + 1)
            }
        }
        walk(info.rootId, "", mainFolder, 0)
        return out
    }

    private fun decided(p: Prep) {
        if (isFinishing) return
        prep = p
        folder = p.folders.firstOrNull()
        val pending = pending ?: return finish()
        when (p.decision) {
            SaveDecision.Nothing -> finish()
            is SaveDecision.Update -> showUpdate(p.existing ?: return finish())
            SaveDecision.New ->
                if (prefs.saveAtOnce) saveNew(p.defaultTitle, pending.username, pending.password, folder ?: return finish()) else showForm(p)
        }
    }

    // ------------------------------------------------------------------------------------------------------------------------ screens

    private fun showForm(p: Prep) {
        val pending = pending ?: return finish()
        val card = card(getString(R.string.autofill_save_title), pending.webDomain ?: p.defaultTitle)
        val titleBox = ui.textBox(getString(R.string.vault_title_hint)).apply { setText(p.defaultTitle) }
        val userBox = ui.textBox(getString(R.string.vault_username)).apply { setText(pending.username) }
        val passwordBox = PasswordField(this, ui, getString(R.string.vault_password)).apply { edit.setText(pending.password) }
        card.addView(titleBox, below(0))
        card.addView(userBox, below(10))
        card.addView(passwordBox, below(10))
        if (p.folders.size > 1) {
            val labels = ui.texts(getString(R.string.autofill_save_folder), folder?.label.orEmpty())
            val summary = labels.getChildAt(1) as TextView
            card.addView(ui.row().apply {
                setPadding(0, ui.dp(6), 0, ui.dp(6))
                minimumHeight = 0
                addView(labels, ui.weighted())
                addView(ui.chevron())
                setOnClickListener { chooseFolder(p.folders) { f -> folder = f; summary.text = f.label } }
            }, below(6))
        }
        val notNow = ui.outlineButton(getString(R.string.autofill_not_now)) { finish() }
        val save = ui.filledButton(getString(R.string.autofill_save_button)) {
            val pw = passwordBox.chars()
            val password = String(pw)
            pw.fill('\u0000')
            val f = folder
            if (password.isEmpty() || f == null) {
                Toast.makeText(this, R.string.autofill_save_need_password, Toast.LENGTH_SHORT).show()
            } else {
                passwordBox.clear()
                saveNew(titleBox.text.toString().trim().ifEmpty { p.defaultTitle }, userBox.text.toString().trim(), password, f)
            }
        }
        card.addView(buttons(notNow, save))
        place(card)
    }

    private fun showUpdate(existing: EntrySummary) {
        val card = card(getString(R.string.autofill_update_title), null)
        val title = existing.title.ifBlank { existing.url }.ifBlank { getString(R.string.autofill_untitled) }
        card.addView(TextView(this).apply {
            text = getString(R.string.autofill_update_text, title, existing.username)
            textSize = 15f
            setTextColor(ui.colors.text)
        }, below(0))
        card.addView(ui.filledButton(getString(R.string.autofill_update_yes)) { update(existing) }, below(14))
        val notNow = ui.outlineButton(getString(R.string.autofill_not_now)) { finish() }
        val asNew = ui.outlineButton(getString(R.string.autofill_update_new)) { prep?.let { showForm(it) } }
        card.addView(buttons(notNow, asNew))
        place(card)
    }

    /** "Save at once" done: a card with the way to the new entry; it closes by itself. */
    private fun showSaved(id: String?, title: String, f: Folder) {
        val card = card(getString(R.string.autofill_saved), title)
        val done = ui.outlineButton(getString(R.string.autofill_done)) { finish() }
        val open = ui.filledButton(getString(R.string.autofill_open)) {
            if (id != null) startActivity(Intent(this, EntryActivity::class.java).putExtra(EntryActivity.EXTRA_ID, id).putExtra(EntryActivity.EXTRA_GROUP, f.id))
            finish()
        }
        card.addView(buttons(done, open))
        place(card)
        root.postDelayed(autoClose, AUTO_CLOSE_MS)
    }

    private fun chooseFolder(folders: List<Folder>, chosen: (Folder) -> Unit) {
        val dialog = AlertDialog.Builder(this)
            .setTitle(R.string.autofill_save_folder)
            .setItems(folders.map { it.label }.toTypedArray()) { _, i -> chosen(folders[i]) }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
        ui.styleDialog(dialog)
    }

    // ------------------------------------------------------------------------------------------------------------------------ saving

    private fun saveNew(title: String, username: String, password: String, f: Folder) {
        val p = pending ?: return finish()
        val web = p.webDomain
        // A website is found again by its address, an app by the custom field KeePassDX writes for it (D32).
        val draft = EntryDraft(
            id = null,
            groupId = f.id,
            title = title,
            username = username,
            password = password,
            url = if (web != null) "https://$web" else "",
            notes = "",
            fields = if (web == null) listOf(FieldItem(APP_FIELD, p.packageName, false)) else emptyList(),
        )
        busy.show(getString(R.string.vault_saving))
        VaultHub.client(this).async({ it.saveEntry(draft) }) { r ->
            busy.hide()
            r.fold({ c ->
                when {
                    !c.saved -> {
                        Toast.makeText(this, R.string.autofill_save_conflict, Toast.LENGTH_LONG).show()
                        finish()
                    }
                    prefs.saveAtOnce -> showSaved(c.id, title, f)
                    else -> {
                        Toast.makeText(this, R.string.autofill_saved, Toast.LENGTH_SHORT).show()
                        finish()
                    }
                }
            }, ::failed)
        }
    }

    /** Replaces the password of the stored entry; the old one stays in the entry's history, everything else of the entry is carried over. */
    private fun update(existing: EntrySummary) {
        val p = pending ?: return finish()
        busy.show(getString(R.string.vault_saving))
        VaultHub.client(this).async({ v ->
            val detail = v.entry(existing.id)
            v.saveEntry(EntryDraft(existing.id, existing.groupId, existing.title, existing.username, p.password, existing.url, detail.notes, detail.fields))
        }) { r ->
            busy.hide()
            r.fold({ c ->
                Toast.makeText(this, if (c.saved) R.string.autofill_updated else R.string.autofill_save_conflict, Toast.LENGTH_LONG).show()
                finish()
            }, ::failed)
        }
    }

    private fun failed(e: Throwable) {
        busy.hide()
        if (!VaultHub.isLocked(e)) Toast.makeText(this, VaultHub.message(this, e), Toast.LENGTH_LONG).show()
        finish()
    }

    // ------------------------------------------------------------------------------------------------------------------------ building blocks

    /** A card with a heading and a muted line under it, the same look as the unlock card. */
    private fun card(heading: String, sub: String?): LinearLayout {
        val c = ui.colors
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = ui.rounded(c.bg, 20, c.cardStroke)
            setPadding(ui.dp(20), ui.dp(18), ui.dp(20), ui.dp(18))
            isClickable = true
            addView(TextView(context).apply {
                text = heading
                textSize = 18f
                typeface = Typeface.DEFAULT_BOLD
                setTextColor(c.text)
            })
            if (sub != null) {
                addView(TextView(context).apply {
                    text = getString(R.string.autofill_save_for, sub)
                    textSize = 14f
                    setTextColor(c.muted)
                    setPadding(0, ui.dp(4), 0, ui.dp(8))
                })
            } else {
                addView(View(context), LinearLayout.LayoutParams(0, ui.dp(8)))
            }
        }
    }

    private fun below(dp: Int) = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = ui.dp(dp) }

    private fun buttons(left: View, right: View): LinearLayout = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        setPadding(0, ui.dp(14), 0, 0)
        addView(left, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply { rightMargin = ui.dp(8) })
        addView(right, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
    }

    private fun place(card: View) {
        root.removeAllViews()
        val lp = FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.TOP or Gravity.CENTER_HORIZONTAL)
        lp.setMargins(ui.dp(16), ui.dp(64), ui.dp(16), 0)
        val maxW = ui.dp(440)
        if (resources.displayMetrics.widthPixels - ui.dp(32) > maxW) lp.width = maxW
        root.addView(card, lp)
    }

    private companion object {
        const val APP_FIELD = "AndroidApp"
        const val MAX_FOLDERS = 100
        const val MAX_DEPTH = 8
        const val AUTO_CLOSE_MS = 6000L
    }
}
