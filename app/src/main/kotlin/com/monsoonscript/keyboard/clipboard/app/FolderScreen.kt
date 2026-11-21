package com.monsoonscript.keyboard.clipboard.app

import android.app.Activity
import android.app.AlertDialog
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.TextView
import com.monsoonscript.keyboard.R
import com.monsoonscript.keyboard.clipboard.ClipFolder
import com.monsoonscript.keyboard.clipboard.ClipHub
import com.monsoonscript.keyboard.clipboard.ClipListModel
import com.monsoonscript.keyboard.clipboard.ClipStore
import com.monsoonscript.keyboard.settings.SettingsUi

/**
 * The Folders tab (D67): a "New folder" button and the list of folders with their clip counts. A tap opens the folder's clips (a [ClipListScreen] with a
 * back bar); the three dots on a row rename or delete the folder (its clips stay in the history).
 */
internal class FolderScreen(
    private val activity: Activity,
    private val ui: SettingsUi,
    private val hub: ClipHub,
    private val actions: ClipActions,
) : Screen(), ClipStore.Listener {

    private val store = hub.store
    private val frame = FrameLayout(activity)
    override val view: View get() = frame

    private val folderList = ListView(activity)
    private val emptyText = TextView(activity)
    private val folderPage = LinearLayout(activity)
    private var inner: ClipListScreen? = null
    private var innerView: View? = null
    private var listening = false

    private val adapter = object : BaseAdapter() {
        override fun getCount(): Int = store.folders.size

        override fun getItem(position: Int): Any = store.folders[position]

        override fun getItemId(position: Int): Long = store.folders[position].id

        override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
            val f = store.folders[position]
            val n = store.folderSize(f.id)
            val count = if (n == 1) activity.getString(R.string.clip_folder_count_one) else activity.getString(R.string.clip_folder_count, n)
            return ui.row().apply {
                addView(ui.texts(f.name, count), ui.weighted())
                addView(TextView(activity).apply {
                    text = "⋮"
                    textSize = 24f
                    setTextColor(ui.secondary)
                    gravity = Gravity.CENTER
                    setPadding(ui.dp(16), ui.dp(8), ui.dp(4), ui.dp(8))
                    isFocusable = false
                    setOnClickListener { folderMenu(f) }
                })
                isFocusable = false
                setOnClickListener { open(f) }
            }
        }
    }

    init {
        folderPage.orientation = LinearLayout.VERTICAL
        with(ui) {
            folderPage.add(buttonRow(activity.getString(R.string.clip_new_folder), null) { newFolder() })
        }
        folderList.apply {
            divider = null
            adapter = this@FolderScreen.adapter
        }
        emptyText.apply {
            text = activity.getString(R.string.clip_folders_empty)
            textSize = 16f
            setTextColor(ui.secondary)
            gravity = Gravity.CENTER
            setPadding(ui.dp(32), ui.dp(40), ui.dp(32), 0)
        }
        val body = FrameLayout(activity).apply {
            addView(folderList, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
            addView(emptyText, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.TOP))
        }
        folderPage.addView(body, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        frame.addView(folderPage, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
    }

    // ---------------------------------------------------------------- folder list

    private fun newFolder() {
        actions.askText(R.string.clip_new_folder, R.string.clip_folder_hint, "", R.string.clip_save) { name ->
            val made = store.createFolder(name) != null
            if (!made) actions.toast(R.string.clip_folder_bad_name)
            made
        }
    }

    private fun folderMenu(f: ClipFolder) {
        val labels = arrayOf(activity.getString(R.string.clip_folder_rename), activity.getString(R.string.clip_folder_delete))
        AlertDialog.Builder(activity)
            .setTitle(f.name)
            .setItems(labels) { _, which -> if (which == 0) rename(f) else confirmDelete(f) }
            .show()
    }

    private fun rename(f: ClipFolder) {
        actions.askText(R.string.clip_folder_rename_title, R.string.clip_folder_hint, f.name, R.string.clip_save) { name ->
            val ok = name.trim() == f.name || store.renameFolder(f.id, name)
            if (!ok) actions.toast(R.string.clip_folder_bad_name)
            ok
        }
    }

    private fun confirmDelete(f: ClipFolder) {
        AlertDialog.Builder(activity)
            .setTitle(activity.getString(R.string.clip_folder_delete_title, f.name))
            .setMessage(R.string.clip_folder_delete_note)
            .setPositiveButton(R.string.clip_delete_confirm) { _, _ -> store.deleteFolder(f.id) }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    // ---------------------------------------------------------------- one folder

    private fun open(f: ClipFolder) {
        val screen = ClipListScreen(activity, ui, hub, actions, ClipListModel.Scope.FOLDER, f.id)
        screen.onSelecting = { onSelecting?.invoke(it) }
        val bar = LinearLayout(activity).apply {
            gravity = Gravity.CENTER_VERTICAL
            setPadding(ui.dp(8), 0, ui.dp(20), 0)
            addView(TextView(activity).apply {
                text = "‹"
                textSize = 30f
                setTextColor(ui.primary)
                setPadding(ui.dp(12), 0, ui.dp(14), ui.dp(4))
                contentDescription = activity.getString(R.string.settings_back)
                background = ui.ripple()
                setOnClickListener { closeInner() }
            })
            addView(TextView(activity).apply {
                text = f.name
                textSize = 18f
                setTextColor(ui.primary)
                maxLines = 1
            })
        }
        val page = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            addView(bar, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
            addView(screen.view, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        }
        inner = screen
        innerView = page
        folderPage.visibility = View.GONE
        frame.addView(page, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        screen.show()
    }

    private fun closeInner() {
        val screen = inner ?: return
        screen.hide()
        innerView?.let { frame.removeView(it) }
        inner = null
        innerView = null
        folderPage.visibility = View.VISIBLE
        onSelecting?.invoke(false)
        refresh()
    }

    // ---------------------------------------------------------------- Screen

    override fun show() {
        hub.start()
        if (!listening) {
            store.addListener(this)
            listening = true
        }
        inner?.show()
        refresh()
    }

    override fun hide() {
        if (listening) {
            store.removeListener(this)
            listening = false
        }
        inner?.hide()
    }

    override fun back(): Boolean {
        val screen = inner ?: return false
        if (!screen.back()) closeInner()
        return true
    }

    override fun onClipsChanged() = refresh()

    private fun refresh() {
        adapter.notifyDataSetChanged()
        emptyText.visibility = if (store.folders.isEmpty()) View.VISIBLE else View.GONE
    }
}
