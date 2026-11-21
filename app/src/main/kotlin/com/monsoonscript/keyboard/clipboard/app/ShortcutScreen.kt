package com.monsoonscript.keyboard.clipboard.app

import android.app.Activity
import android.app.AlertDialog
import android.text.InputFilter
import android.text.InputType
import android.text.TextUtils
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.TextView
import com.monsoonscript.keyboard.R
import com.monsoonscript.keyboard.clipboard.ClipFormat
import com.monsoonscript.keyboard.clipboard.ClipHub
import com.monsoonscript.keyboard.clipboard.ClipShortcut
import com.monsoonscript.keyboard.clipboard.ClipStore
import com.monsoonscript.keyboard.settings.SettingsUi

/** The Shortcuts tab (D65, D67): the user's own text shortcuts (a trigger such as ;sig and the text it inserts). A tap edits one; "New shortcut" adds one. Nothing is preset. */
internal class ShortcutScreen(
    private val activity: Activity,
    private val ui: SettingsUi,
    private val hub: ClipHub,
    private val actions: ClipActions,
) : Screen(), ClipStore.Listener {

    private val store = hub.store
    private val root = LinearLayout(activity)
    override val view: View get() = root

    private val list = ListView(activity)
    private val emptyText = TextView(activity)
    private var listening = false

    private val adapter = object : BaseAdapter() {
        override fun getCount(): Int = store.shortcuts.size

        override fun getItem(position: Int): Any = store.shortcuts[position]

        override fun getItemId(position: Int): Long = store.shortcuts[position].id

        override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
            val s = store.shortcuts[position]
            return LinearLayout(activity).apply {
                orientation = LinearLayout.VERTICAL
                minimumHeight = ui.dp(60)
                setPadding(ui.dp(20), ui.dp(10), ui.dp(20), ui.dp(10))
                background = ui.ripple()
                addView(TextView(activity).apply {
                    text = s.trigger
                    textSize = 16f
                    setTextColor(ui.accent)
                    typeface = android.graphics.Typeface.DEFAULT_BOLD
                    maxLines = 1
                })
                addView(TextView(activity).apply {
                    text = ClipFormat.previewSource(s.text, 200)
                    textSize = 14f
                    setTextColor(ui.secondary)
                    maxLines = 2
                    ellipsize = TextUtils.TruncateAt.END
                    setPadding(0, ui.dp(2), 0, 0)
                })
                setOnClickListener { edit(s) }
            }
        }
    }

    init {
        root.orientation = LinearLayout.VERTICAL
        with(ui) {
            root.add(buttonRow(activity.getString(R.string.clip_new_shortcut), null) { edit(null) })
            root.add(note(activity.getString(R.string.clip_shortcuts_note)))
        }
        list.apply {
            divider = null
            adapter = this@ShortcutScreen.adapter
        }
        emptyText.apply {
            text = activity.getString(R.string.clip_shortcuts_empty)
            textSize = 16f
            setTextColor(ui.secondary)
            gravity = Gravity.CENTER
            setPadding(ui.dp(32), ui.dp(24), ui.dp(32), 0)
        }
        val body = FrameLayout(activity).apply {
            addView(list, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
            addView(emptyText, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.TOP))
        }
        root.addView(body, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
    }

    /** The editor dialog: a new shortcut when [existing] is null, otherwise that one (with a Delete button). */
    private fun edit(existing: ClipShortcut?) {
        val trigger = EditText(activity).apply {
            hint = activity.getString(R.string.clip_shortcut_trigger_hint)
            setText(existing?.trigger ?: "")
            setSingleLine()
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
        }
        val body = EditText(activity).apply {
            hint = activity.getString(R.string.clip_shortcut_text_hint)
            setText(existing?.text ?: "")
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
            minLines = 3
            gravity = Gravity.TOP or Gravity.START
            filters = arrayOf(InputFilter.LengthFilter(ClipStore.MAX_TEXT))
        }
        val box = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(ui.dp(20), ui.dp(8), ui.dp(20), 0)
            addView(trigger, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
            addView(body, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        }
        val builder = AlertDialog.Builder(activity)
            .setTitle(if (existing == null) R.string.clip_new_shortcut else R.string.clip_shortcut_edit_title)
            .setView(box)
            .setPositiveButton(R.string.clip_save, null)
            .setNegativeButton(android.R.string.cancel, null)
        if (existing != null) builder.setNeutralButton(R.string.clip_shortcut_delete, null)
        val dialog = builder.create()
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val t = trigger.text.toString()
                val x = body.text.toString()
                val code = store.checkShortcut(t, x, existing?.id ?: 0L)
                if (code != ClipStore.SHORTCUT_OK) {
                    actions.toast(errorText(code))
                } else {
                    if (existing == null) store.addShortcut(t, x) else store.updateShortcut(existing.id, t, x)
                    dialog.dismiss()
                }
            }
            if (existing != null) {
                dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener {
                    store.deleteShortcut(existing.id)
                    dialog.dismiss()
                }
            }
        }
        dialog.show()
    }

    private fun errorText(code: Int): Int = when (code) {
        ClipStore.SHORTCUT_EMPTY -> R.string.clip_sc_empty
        ClipStore.SHORTCUT_HAS_SPACE -> R.string.clip_sc_space
        ClipStore.SHORTCUT_TOO_LONG -> R.string.clip_sc_long
        ClipStore.SHORTCUT_NO_TEXT -> R.string.clip_sc_no_text
        ClipStore.SHORTCUT_DUPLICATE -> R.string.clip_sc_duplicate
        else -> R.string.clip_sc_many
    }

    override fun show() {
        hub.start()
        if (!listening) {
            store.addListener(this)
            listening = true
        }
        refresh()
    }

    override fun hide() {
        if (listening) {
            store.removeListener(this)
            listening = false
        }
    }

    override fun onClipsChanged() = refresh()

    private fun refresh() {
        adapter.notifyDataSetChanged()
        emptyText.visibility = if (store.shortcuts.isEmpty()) View.VISIBLE else View.GONE
    }
}
