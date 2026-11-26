package com.monsoonscript.keyboard.vault

import android.app.AlertDialog
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.Editable
import android.text.TextWatcher
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.BaseAdapter
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.PopupMenu
import android.widget.TextView
import android.widget.Toast
import com.monsoonscript.keyboard.R
import com.monsoonscript.keyboard.settings.Icons
import com.monsoonscript.vault.EntrySummary
import com.monsoonscript.vault.GroupListing
import com.monsoonscript.vault.GroupRef
import com.monsoonscript.vault.VaultError
import com.monsoonscript.vault.VaultException
import com.monsoonscript.vault.VaultInfo

/**
 * The vault's home screen (E1.13, D33): with no vault yet it offers to create or open one (D35), with a locked one it asks for the master password, and with an
 * unlocked one it is the list: search on top, folders as rows, entries below, a "+" button and a menu. Deleting an entry is instant with an Undo bar (D81).
 */
internal class VaultMainActivity : VaultBaseActivity() {
    override val needsUnlocked: Boolean get() = false
    override val idleLockActive: Boolean get() = state == State.LIST

    private enum class State { NONE, WELCOME, UNLOCK, LIST }

    private var state = State.NONE
    private val main = Handler(Looper.getMainLooper())

    // unlock page
    private var keySummary: TextView? = null
    private var keyClear: View? = null
    private var fp: FingerprintUnlock? = null
    private var unlockNote: TextView? = null
    private var fpButton: View? = null

    // list page
    private var group: String? = null // the open folder; null = the root
    private val above = ArrayList<String?>() // the folders above it, to go back up
    private var listing: GroupListing? = null
    private var info: VaultInfo? = null
    private var query = ""
    private var found: List<EntrySummary> = emptyList()
    private var searchSeq = 0
    private val adapter = RowAdapter()
    private var items: List<Item> = emptyList()
    private lateinit var listView: ListView
    private lateinit var titleView: TextView
    private lateinit var emptyView: TextView
    private lateinit var fab: View
    private lateinit var searchBox: EditText
    private lateinit var undoBar: View
    private lateinit var undoText: TextView
    private var undoAction: (() -> Unit)? = null
    private val hideUndo = Runnable { if (::undoBar.isInitialized) undoBar.visibility = View.GONE }
    private val searchRun = Runnable { runSearch() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.setSoftInputMode(android.view.WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
    }

    override fun onStart() {
        super.onStart()
        decide()
    }

    override fun onLocked() {
        if (state == State.LIST || state == State.NONE) {
            if (prefs.hasVault) showUnlock() else showWelcome()
        }
    }

    private fun keyboard(visible: Boolean) {
        val flag = if (visible) android.view.WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE else android.view.WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN
        window.setSoftInputMode(android.view.WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE or flag)
    }

    private fun decide() {
        if (!prefs.hasVault) {
            showWelcome()
            return
        }
        call({ it.status() }, fail = { showUnlock() }) { s ->
            when {
                !s.unlocked -> showUnlock()
                state == State.LIST -> reload()
                else -> showList()
            }
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        if (state == State.LIST) goUp() else super.onBackPressed()
    }

    // ------------------------------------------------------------------------------------------------------------------------ welcome

    private fun showWelcome() {
        state = State.WELCOME
        keyboard(false)
        val page = ui.page(getString(R.string.vault_title), onBack = { finish() })
        with(ui) {
            page.content.add(note(getString(R.string.vault_welcome_text)))
            page.content.add(padded(filledButton(getString(R.string.vault_create)) { startActivity(Intent(this@VaultMainActivity, VaultCreateActivity::class.java)) }))
            page.content.add(padded(outlineButton(getString(R.string.vault_open)) { pickVault() }))
            page.content.add(note(getString(R.string.vault_welcome_note)))
        }
        setContentView(page.root)
    }

    private fun pickVault() {
        @Suppress("DEPRECATION")
        startActivityForResult(VaultHub.openIntent(), REQ_OPEN)
    }

    // ------------------------------------------------------------------------------------------------------------------------ unlock

    private fun showUnlock() {
        state = State.UNLOCK
        fp?.stop()
        val fingerprint = FingerprintUnlock(this, prefs)
        fp = fingerprint
        keyboard(!fingerprint.willPrompt()) // with the fingerprint prompt coming, the soft keyboard stays down until the master password is chosen (D91)
        val name = prefs.fileName.ifEmpty { getString(R.string.vault_title) }
        val page = ui.page(getString(R.string.vault_unlock_title), onBack = { finish() })
        val field = PasswordField(this, ui, getString(R.string.vault_master_password))
        val error = TextView(this).apply {
            textSize = 14f
            setTextColor(VAULT_RED)
            visibility = View.GONE
        }
        field.edit.setOnEditorActionListener { _, action, _ ->
            if (action == EditorInfo.IME_ACTION_DONE) {
                unlock(field, error)
                true
            } else false
        }
        val reasonView = TextView(this).apply { // why the fingerprint is not used (restart, 3 days, new fingerprints ...)
            textSize = 14f
            setTextColor(ui.colors.accent)
            visibility = View.GONE
        }
        unlockNote = reasonView
        val keyRow = ui.row()
        val keyTexts = ui.texts(getString(R.string.vault_key_file), keyFileLabel())
        keySummary = keyTexts.getChildAt(1) as TextView
        val clear = ImageView(this).apply {
            setImageDrawable(ui.icon(Icons.Kind.CLOSE, ui.colors.muted, 20))
            scaleType = ImageView.ScaleType.CENTER
            contentDescription = getString(R.string.vault_remove_key_file)
            visibility = if (prefs.keyFileUri == null) View.GONE else View.VISIBLE
            setOnClickListener { setKeyFile(null) }
        }
        keyClear = clear
        keyRow.addView(keyTexts, ui.weighted())
        keyRow.addView(clear, LinearLayout.LayoutParams(ui.dp(40), ui.dp(40)))
        keyRow.setOnClickListener {
            @Suppress("DEPRECATION")
            startActivityForResult(VaultHub.keyFileIntent(), REQ_KEYFILE)
        }
        // next to the password: the way back to the fingerprint when its prompt was missed or dismissed (shown only when the fingerprint is set up and allowed now)
        val useFingerprint = ui.outlineButton(getString(R.string.fp_button)) { startFingerprint(field, error) }.apply {
            visibility = if (fingerprint.willPrompt()) View.VISIBLE else View.GONE
        }
        fpButton = useFingerprint
        with(ui) {
            page.content.add(note(getString(R.string.vault_unlock_for, name)))
            page.content.add(padded(reasonView, 20, 0))
            page.content.add(padded(field))
            page.content.add(gap(8))
            page.content.add(padded(useFingerprint))
            page.content.add(padded(error, 20, 0))
            page.content.add(padded(filledButton(getString(R.string.vault_unlock)) { unlock(field, error) })) // right under the field: the keyboard cannot hide it
            page.content.add(gap(12))
            page.content.add(keyRow)
            page.content.add(buttonRow(getString(R.string.vault_other_vault), null) { confirmForget() })
        }
        setContentView(page.root)
        startFingerprint(field, error)
    }

    /** The fingerprint prompt (again): when the page opens, and from the "Use fingerprint" button for the owner who missed or dismissed it. */
    private fun startFingerprint(field: PasswordField, error: TextView) {
        val f = fp ?: return
        unlockNote?.visibility = View.GONE
        error.visibility = View.GONE
        field.edit.windowToken?.let { getSystemService(InputMethodManager::class.java)?.hideSoftInputFromWindow(it, 0) } // the prompt is not covered by the keyboard
        f.begin(
            onPassword = { pw -> unlock(field, error, pw, typed = false) },
            onTyping = { why -> showTyping(field, why) },
        )
    }

    /** The master password field gets the focus and the soft keyboard (now, or as soon as the fingerprint prompt has closed), with a line saying why when there is one. */
    private fun showTyping(field: PasswordField, why: String?) {
        if (isDestroyed || state != State.UNLOCK) return
        if (why != null) unlockNote?.let {
            it.text = why
            it.visibility = View.VISIBLE
        }
        fpButton?.visibility = if (fp?.willPrompt() == true) View.VISIBLE else View.GONE // only while the fingerprint can still be used
        keyboard(true)
        SoftKeyboard.show(field.edit)
    }

    private fun keyFileLabel(): String {
        val k = prefs.keyFileUri ?: return getString(R.string.vault_key_file_none)
        return VaultHub.displayName(this, Uri.parse(k)).ifEmpty { getString(R.string.vault_key_file_none) }
    }

    private fun setKeyFile(uri: Uri?) {
        prefs.keyFileUri = uri?.toString()
        keySummary?.text = keyFileLabel()
        keyClear?.visibility = if (uri == null) View.GONE else View.VISIBLE
    }

    /** Opens the vault with [pw] (wiped here); [typed] false = it came out of the fingerprint's storage. */
    private fun unlock(field: PasswordField, error: TextView, pw: CharArray = field.chars(), typed: Boolean = true) {
        val keyUri = prefs.keyFileUri
        if (pw.isEmpty() && keyUri == null) {
            error.text = getString(R.string.vault_need_password)
            error.visibility = View.VISIBLE
            return
        }
        val file = Uri.parse(prefs.fileUri ?: return)
        val readOnly = !prefs.writable
        val autoLock = prefs.autoLockMs
        error.visibility = View.GONE
        busy.show(getString(R.string.vault_unlocking))
        val keep = if (typed && pw.isNotEmpty()) pw.copyOf() else CharArray(0) // for the fingerprint set-up after a typed unlock; wiped on every path
        call({ v ->
            val key = keyUri?.let { VaultHub.readKeyFile(this, Uri.parse(it)) }
            try {
                v.open(file, pw, key, readOnly)
                v.setAutoLock(autoLock)
            } finally {
                pw.fill('\u0000')
            }
        }, fail = { e ->
            keep.fill('\u0000')
            busy.hide()
            if (!typed && fp?.passwordRejected(e) == true) { // the stored password is out of date: back to typing, and the fingerprint is off until it is set up again
                showTyping(field, fp?.passwordNote)
            } else {
                error.text = VaultHub.message(this, e)
                error.visibility = View.VISIBLE
                if (!typed) showTyping(field, null)
            }
        }) {
            field.clear()
            val f = fp
            if (typed && f != null) {
                f.afterTyped(keep) { // one touch to finish a fingerprint set-up, or the 72 hours start again
                    busy.hide()
                    showList()
                }
            } else {
                keep.fill('\u0000')
                busy.hide()
                showList()
            }
        }
    }

    private fun confirmForget() {
        val dialog = AlertDialog.Builder(this)
            .setTitle(R.string.vault_other_vault)
            .setMessage(R.string.vault_other_vault_text)
            .setPositiveButton(R.string.vault_forget_yes) { _, _ ->
                vault.async({ it.lock() }) { }
                prefs.forgetVault()
                showWelcome()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
        ui.styleDialog(dialog)
    }

    // ------------------------------------------------------------------------------------------------------------------------ list

    private fun showList() {
        state = State.LIST
        keyboard(false)
        group = null
        above.clear()
        query = ""
        found = emptyList()
        buildListUi()
        reload()
        armIdle()
    }

    private fun buildListUi() {
        val c = ui.colors
        titleView = TextView(this).apply {
            textSize = 24f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(c.text)
            setSingleLine()
            ellipsize = android.text.TextUtils.TruncateAt.END
        }
        lateinit var menuButton: View
        menuButton = ui.iconButton(VaultIcons.Kind.MORE, getString(R.string.vault_menu), c.text) { showMenu(menuButton) }
        val bar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(ui.dp(8), ui.dp(10), ui.dp(8), ui.dp(4))
            addView(ui.backButton { goUp() })
            addView(titleView, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            addView(menuButton)
        }
        searchBox = ui.textBox(getString(R.string.vault_search)).apply {
            imeOptions = EditorInfo.IME_ACTION_SEARCH
            addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}

                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}

                override fun afterTextChanged(s: Editable?) {
                    query = s?.toString()?.trim() ?: ""
                    main.removeCallbacks(searchRun)
                    main.postDelayed(searchRun, 150)
                }
            })
        }
        listView = ListView(this).apply {
            divider = null
            dividerHeight = 0
            selector = android.graphics.drawable.ColorDrawable(Color.TRANSPARENT)
            clipToPadding = false
            setPadding(0, ui.dp(4), 0, ui.dp(96))
            adapter = this@VaultMainActivity.adapter
            setOnItemClickListener { _, _, position, _ -> items.getOrNull(position)?.let { open(it) } }
            setOnItemLongClickListener { _, view, position, _ ->
                items.getOrNull(position)?.let { longPress(it, view) }
                true
            }
        }
        val column = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(bar)
            addView(ui.padded(searchBox, 16, 6))
            addView(listView, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        }
        emptyView = TextView(this).apply {
            textSize = 15f
            setTextColor(c.muted)
            gravity = Gravity.CENTER
            setPadding(ui.dp(40), ui.dp(160), ui.dp(40), 0)
            visibility = View.GONE
        }
        fab = ImageView(this).apply {
            setImageDrawable(VaultIcons.drawable(VaultIcons.Kind.PLUS, c.onAccent, ui.dp(28)))
            scaleType = ImageView.ScaleType.CENTER
            contentDescription = getString(R.string.vault_new_entry)
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(c.accent)
            }
            elevation = ui.dp(6).toFloat()
            setOnClickListener { newEntry() }
        }
        undoText = TextView(this).apply {
            textSize = 15f
            setTextColor(c.text)
        }
        val undoButton = TextView(this).apply {
            text = getString(R.string.vault_undo)
            textSize = 15f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(c.accent)
            setPadding(ui.dp(16), ui.dp(8), ui.dp(8), ui.dp(8))
            background = ui.ripple()
            setOnClickListener {
                val action = undoAction
                undoAction = null
                undoBar.visibility = View.GONE
                action?.invoke()
            }
        }
        val bottom = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = ui.rounded(c.card, 14, c.cardStroke)
            setPadding(ui.dp(18), ui.dp(6), ui.dp(10), ui.dp(6))
            elevation = ui.dp(6).toFloat()
            visibility = View.GONE
            addView(undoText, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            addView(undoButton)
        }
        undoBar = bottom
        val root = FrameLayout(this).apply {
            setBackgroundColor(c.bg)
            addView(column, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
            addView(emptyView, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
            addView(fab, FrameLayout.LayoutParams(ui.dp(56), ui.dp(56), Gravity.BOTTOM or Gravity.END).apply {
                rightMargin = ui.dp(20)
                bottomMargin = ui.dp(24)
            })
            addView(bottom, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM).apply {
                leftMargin = ui.dp(16)
                rightMargin = ui.dp(16)
                bottomMargin = ui.dp(24)
            })
        }
        root.isFocusableInTouchMode = true // so the search box does not grab the focus (and the keyboard) when the list opens
        setContentView(root)
        root.requestFocus()
    }

    private fun reload() {
        call({ v -> Pair(v.list(group), v.info()) }, fail = { e ->
            if ((e as? VaultException)?.code == VaultError.NOT_FOUND && group != null) { // the folder is gone (a merge removed it): back to the top
                group = null
                above.clear()
                reload()
            } else report(e)
        }) { (l, i) ->
            listing = l
            info = i
            render()
        }
    }

    private fun runSearch() {
        if (state != State.LIST) return
        if (query.isEmpty()) {
            found = emptyList()
            render()
            return
        }
        val seq = ++searchSeq
        val q = query
        call({ it.search(q) }) { list ->
            if (seq == searchSeq) {
                found = list
                render()
            }
        }
    }

    private class Item(val group: GroupRef? = null, val entry: EntrySummary? = null, val isBin: Boolean = false)

    private fun inBin(): Boolean = info?.recycleBinId != null && info?.recycleBinId == listing?.group?.id

    private fun render() {
        val l = listing ?: return
        val i = info ?: return
        val atRoot = above.isEmpty() && group == null
        titleView.text = when {
            query.isNotEmpty() -> getString(R.string.vault_search_results)
            atRoot -> i.name.ifEmpty { prefs.fileName.ifEmpty { getString(R.string.vault_title) } }
            else -> l.group.name
        }
        val list = ArrayList<Item>()
        if (query.isNotEmpty()) {
            found.forEach { list += Item(entry = it) }
        } else {
            val bin = i.recycleBinId
            l.groups.filter { it.id != bin }.forEach { list += Item(group = it) }
            l.groups.filter { it.id == bin }.forEach { list += Item(group = it, isBin = true) }
            l.entries.forEach { list += Item(entry = it) }
        }
        items = list
        adapter.notifyDataSetChanged()
        fab.visibility = if (inBin() || query.isNotEmpty()) View.GONE else View.VISIBLE
        emptyView.text = when {
            query.isNotEmpty() -> getString(R.string.vault_nothing_found)
            inBin() -> getString(R.string.vault_bin_empty)
            else -> getString(R.string.vault_empty_folder)
        }
        emptyView.visibility = if (list.isEmpty()) View.VISIBLE else View.GONE
    }

    private fun goUp() {
        when {
            query.isNotEmpty() || searchBox.length() > 0 -> searchBox.setText("")
            above.isNotEmpty() -> {
                group = above.removeAt(above.size - 1)
                reload()
            }
            group != null -> {
                group = null
                reload()
            }
            else -> finish()
        }
    }

    private fun open(item: Item) {
        val g = item.group
        if (g != null) {
            above.add(group)
            group = g.id
            reload()
            return
        }
        val e = item.entry ?: return
        @Suppress("DEPRECATION")
        startActivityForResult(EntryActivity.intent(this, e.id, e.groupId), REQ_ENTRY)
    }

    private fun newEntry() {
        val parent = listing?.group?.id ?: return
        startActivity(EntryEditActivity.intent(this, null, parent))
    }

    // ------------------------------------------------------------------------------------------------------------------------ actions

    private fun showMenu(anchor: View) {
        val popup = PopupMenu(this, anchor)
        val m = popup.menu
        val bin = inBin()
        val atRoot = group == null
        if (!bin) m.add(0, MENU_NEW_FOLDER, 0, R.string.vault_new_folder)
        m.add(0, MENU_GENERATOR, 1, R.string.vault_generator)
        if (bin) m.add(0, MENU_EMPTY_BIN, 2, R.string.vault_empty_bin)
        if (!atRoot && !bin) {
            m.add(0, MENU_RENAME, 3, R.string.vault_rename_folder)
            m.add(0, MENU_DELETE_FOLDER, 4, R.string.vault_delete_folder)
        }
        m.add(0, MENU_SETTINGS, 5, R.string.vault_settings)
        m.add(0, MENU_LOCK, 6, R.string.vault_lock_now)
        popup.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                MENU_NEW_FOLDER -> askFolderName(R.string.vault_new_folder, "") { name -> createFolder(name) }
                MENU_GENERATOR -> GeneratorDialog.show(this, ui, null)
                MENU_EMPTY_BIN -> confirmEmptyBin()
                MENU_RENAME -> askFolderName(R.string.vault_rename_folder, listing?.group?.name ?: "") { name -> renameFolder(listing!!.group.id, name) }
                MENU_DELETE_FOLDER -> listing?.group?.let { confirmDeleteFolder(it, leaveAfter = true) }
                MENU_SETTINGS -> startActivity(Intent(this, VaultSettingsActivity::class.java))
                MENU_LOCK -> lockNow()
            }
            true
        }
        popup.show()
    }

    private fun askFolderName(title: Int, initial: String, onName: (String) -> Unit) {
        val box = ui.textBox(getString(R.string.vault_folder_name)).apply { setText(initial); setSelection(length()) }
        val dialog = AlertDialog.Builder(this)
            .setTitle(title)
            .setView(ui.padded(box, 20, 8))
            .setPositiveButton(android.R.string.ok) { _, _ ->
                val name = box.text.toString().trim()
                if (name.isNotEmpty()) onName(name)
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
        ui.styleDialog(dialog)
    }

    private fun createFolder(name: String) {
        val parent = listing?.group?.id ?: return
        change(R.string.vault_saving, { it.createGroup(parent, name) }) { reload() }
    }

    private fun renameFolder(id: String, name: String) {
        change(R.string.vault_saving, { it.renameGroup(id, name) }) { reload() }
    }

    private fun confirmDeleteFolder(g: GroupRef, leaveAfter: Boolean) {
        val dialog = AlertDialog.Builder(this)
            .setTitle(getString(R.string.vault_delete_folder_title, g.name))
            .setMessage(R.string.vault_delete_folder_text)
            .setPositiveButton(R.string.vault_move_to_bin) { _, _ ->
                change(R.string.vault_saving, { it.deleteGroup(g.id) }) {
                    if (leaveAfter) goUp() else reload()
                }
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
        ui.styleDialog(dialog)
    }

    private fun confirmEmptyBin() {
        val dialog = AlertDialog.Builder(this)
            .setTitle(R.string.vault_empty_bin)
            .setMessage(R.string.vault_empty_bin_text)
            .setPositiveButton(R.string.vault_delete_forever) { _, _ ->
                change(R.string.vault_saving, { it.emptyRecycleBin() }) { reload() }
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
        ui.styleDialog(dialog)
    }

    private fun longPress(item: Item, anchor: View) {
        val popup = PopupMenu(this, anchor, Gravity.END)
        val m = popup.menu
        val e = item.entry
        val g = item.group
        if (e != null) {
            m.add(0, 1, 0, R.string.vault_copy_password)
            if (e.username.isNotEmpty()) m.add(0, 2, 1, R.string.vault_copy_username)
            if (inBin() || query.isNotEmpty() && e.groupId == info?.recycleBinId) {
                m.add(0, 3, 2, R.string.vault_restore)
                m.add(0, 4, 3, R.string.vault_delete_forever)
            } else {
                m.add(0, 5, 2, R.string.vault_delete_entry)
            }
        } else if (g != null && !item.isBin) {
            m.add(0, 6, 0, R.string.vault_rename_folder)
            m.add(0, 7, 1, R.string.vault_delete_folder)
        }
        popup.setOnMenuItemClickListener { mi ->
            when (mi.itemId) {
                1 -> copyPassword(e!!)
                2 -> VaultClip.copy(this, e!!.username, getString(R.string.vault_username_copied), secret = false)
                3 -> restore(e!!)
                4 -> confirmDeleteForever(e!!)
                5 -> deleteEntry(e!!)
                6 -> askFolderName(R.string.vault_rename_folder, g!!.name) { name -> renameFolder(g.id, name) }
                7 -> confirmDeleteFolder(g!!, leaveAfter = false)
            }
            true
        }
        if (m.size() > 0) popup.show()
    }

    private fun copyPassword(e: EntrySummary) {
        call({ it.password(e.id) }) { chars ->
            val text = String(chars)
            chars.fill('\u0000')
            if (text.isEmpty()) Toast.makeText(this, R.string.vault_no_password, Toast.LENGTH_SHORT).show()
            else VaultClip.copy(this, text, getString(R.string.vault_password_copied))
        }
    }

    /** Instant (D81): the row goes at once, the save happens in the background, and Undo puts it back. */
    private fun deleteEntry(e: EntrySummary) {
        items = items.filter { it.entry?.id != e.id }
        adapter.notifyDataSetChanged()
        emptyView.visibility = if (items.isEmpty()) View.VISIBLE else View.GONE
        showUndo(getString(R.string.vault_entry_deleted, e.title.ifEmpty { getString(R.string.vault_untitled) })) { undoDelete(e) }
        call({ it.deleteEntry(e.id) }, fail = { err ->
            hideUndoNow()
            report(err)
            reload()
        }) { c ->
            if (!c.saved) askConflict { reload() }
        }
    }

    private fun undoDelete(e: EntrySummary) {
        call({ it.moveEntry(e.id, e.groupId) }, fail = { err ->
            Toast.makeText(this, R.string.vault_undo_failed, Toast.LENGTH_LONG).show()
            if (VaultHub.isLocked(err)) report(err) else reload()
        }) { c ->
            if (!c.saved) askConflict { reload() } else reload()
        }
    }

    private fun restore(e: EntrySummary) {
        val root = info?.rootId ?: return
        change(R.string.vault_saving, { it.moveEntry(e.id, root) }) {
            Toast.makeText(this, R.string.vault_restored, Toast.LENGTH_SHORT).show()
            if (query.isNotEmpty()) runSearch() else reload()
        }
    }

    private fun confirmDeleteForever(e: EntrySummary) {
        val dialog = AlertDialog.Builder(this)
            .setTitle(getString(R.string.vault_delete_forever_title, e.title.ifEmpty { getString(R.string.vault_untitled) }))
            .setMessage(R.string.vault_delete_forever_text)
            .setPositiveButton(R.string.vault_delete_forever) { _, _ ->
                change(R.string.vault_saving, { it.deleteEntry(e.id) }) { if (query.isNotEmpty()) runSearch() else reload() }
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
        ui.styleDialog(dialog)
    }

    private fun showUndo(text: String, action: () -> Unit) {
        if (state != State.LIST || !::undoText.isInitialized) return
        undoText.text = text
        undoAction = action
        undoBar.visibility = View.VISIBLE
        main.removeCallbacks(hideUndo)
        main.postDelayed(hideUndo, UNDO_MS)
    }

    private fun hideUndoNow() {
        main.removeCallbacks(hideUndo)
        undoAction = null
        if (::undoBar.isInitialized) undoBar.visibility = View.GONE
    }

    // ------------------------------------------------------------------------------------------------------------------------ results

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        @Suppress("DEPRECATION")
        super.onActivityResult(requestCode, resultCode, data)
        val uri = data?.data
        when (requestCode) {
            REQ_OPEN -> if (resultCode == RESULT_OK && uri != null && data != null) {
                val writable = VaultHub.keepAccess(this, uri, data)
                vault.async({ it.lock() }) { } // switching files: whatever was open is locked
                prefs.fileUri = uri.toString()
                prefs.fileName = VaultHub.displayName(this, uri)
                prefs.writable = writable
                prefs.keyFileUri = null
                showUnlock()
            }
            REQ_KEYFILE -> if (resultCode == RESULT_OK && uri != null && data != null) {
                try {
                    contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
                } catch (_: SecurityException) {
                }
                setKeyFile(uri)
            }
            REQ_ENTRY -> {
                val id = data?.getStringExtra(EntryActivity.EXTRA_DELETED_ID)
                val from = data?.getStringExtra(EntryActivity.EXTRA_DELETED_FROM)
                val title = data?.getStringExtra(EntryActivity.EXTRA_DELETED_TITLE) ?: ""
                if (resultCode == RESULT_OK && id != null && from != null) {
                    showUndo(getString(R.string.vault_entry_deleted, title.ifEmpty { getString(R.string.vault_untitled) })) { undoDelete(EntrySummary(id, from, title, "", "", false, 0L)) }
                }
            }
        }
    }

    override fun onDestroy() {
        main.removeCallbacksAndMessages(null)
        fp?.stop()
        super.onDestroy()
    }

    // ------------------------------------------------------------------------------------------------------------------------ rows

    private inner class RowAdapter : BaseAdapter() {
        override fun getCount(): Int = items.size

        override fun getItem(position: Int): Any = items[position]

        override fun getItemId(position: Int): Long = position.toLong()

        override fun getView(position: Int, convertView: View?, parent: ViewGroup?): View {
            val row = (convertView as? LinearLayout) ?: newRow()
            val chip = row.getChildAt(0) as FrameLayout
            val texts = row.getChildAt(1) as LinearLayout
            val title = texts.getChildAt(0) as TextView
            val sub = texts.getChildAt(1) as TextView
            val arrow = row.getChildAt(2)
            val item = items[position]
            chip.removeAllViews()
            val g = item.group
            val e = item.entry
            if (g != null) {
                chip.addView(ui.vaultChip(if (item.isBin) VaultIcons.Kind.TRASH else VaultIcons.Kind.FOLDER), FrameLayout.LayoutParams(ui.dp(40), ui.dp(40)))
                title.text = g.name
                sub.text = counts(g)
                sub.visibility = View.VISIBLE
                arrow.visibility = View.VISIBLE
            } else if (e != null) {
                val name = e.title.ifEmpty { getString(R.string.vault_untitled) }
                chip.addView(ui.letterChip(name.take(1).uppercase()), FrameLayout.LayoutParams(ui.dp(40), ui.dp(40)))
                title.text = name
                sub.text = e.username
                sub.visibility = if (e.username.isEmpty()) View.GONE else View.VISIBLE
                arrow.visibility = View.GONE
            }
            return row
        }

        private fun counts(g: GroupRef): String {
            val parts = ArrayList<String>(2)
            if (g.groups > 0) parts += resources.getQuantityString(R.plurals.vault_folders, g.groups, g.groups)
            parts += resources.getQuantityString(R.plurals.vault_entries, g.entries, g.entries)
            return parts.joinToString(" · ")
        }

        private fun newRow(): LinearLayout {
            val row = LinearLayout(this@VaultMainActivity).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                minimumHeight = ui.dp(64)
                setPadding(ui.dp(20), ui.dp(8), ui.dp(16), ui.dp(8))
                background = ui.ripple()
            }
            row.addView(FrameLayout(this@VaultMainActivity), LinearLayout.LayoutParams(ui.dp(40), ui.dp(40)).apply { rightMargin = ui.dp(16) })
            val texts = ui.texts("", "")
            row.addView(texts, ui.weighted())
            row.addView(ui.chevron())
            return row
        }
    }

    private companion object {
        const val REQ_OPEN = 1
        const val REQ_KEYFILE = 2
        const val REQ_ENTRY = 3
        const val UNDO_MS = 7000L
        const val MENU_NEW_FOLDER = 1
        const val MENU_GENERATOR = 2
        const val MENU_EMPTY_BIN = 3
        const val MENU_RENAME = 4
        const val MENU_DELETE_FOLDER = 5
        const val MENU_SETTINGS = 6
        const val MENU_LOCK = 7
    }
}
