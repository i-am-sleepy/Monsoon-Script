package com.monsoonscript.keyboard.clipboard

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.text.InputFilter
import android.text.InputType
import android.view.Gravity
import android.view.ViewGroup
import android.widget.EditText
import android.widget.LinearLayout
import com.monsoonscript.keyboard.R
import com.monsoonscript.keyboard.settings.SettingsUi

/**
 * Edit the text of one clip (E3, D65): a plain page with a text field, Save and Cancel. Opened from the clipboard panel's 3-dot menu; it runs in the
 * same process as the keyboard, so it edits the same in-memory [ClipStore] and the panel updates by itself.
 */
class ClipEditActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val hub = ClipHub.get(this)
        hub.start()
        val id = intent.getLongExtra(EXTRA_ID, -1L)
        val clip = hub.store.byId(id)
        if (clip == null || !clip.isText) { // deleted meanwhile, or the process was restarted and the history is still loading
            finish()
            return
        }
        val ui = SettingsUi(this)
        val page = ui.page(getString(R.string.clip_edit_title)) { finish() }
        val field = EditText(this).apply {
            hint = getString(R.string.clip_edit_hint)
            setText(clip.text)
            setSelection(text.length)
            textSize = 17f
            setTextColor(ui.primary)
            setHintTextColor(ui.secondary)
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
            minLines = 6
            gravity = Gravity.TOP or Gravity.START
            filters = arrayOf(InputFilter.LengthFilter(ClipStore.MAX_TEXT))
        }
        val box = LinearLayout(this).apply {
            setPadding(ui.dp(20), ui.dp(4), ui.dp(20), ui.dp(8))
            addView(field, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        }
        with(ui) {
            page.content.add(box)
            page.content.add(buttonRow(getString(R.string.clip_edit_save), null) {
                hub.store.edit(id, field.text.toString())
                finish()
            })
            page.content.add(buttonRow(getString(R.string.clip_edit_cancel), null) { finish() })
        }
        setContentView(page.root)
    }

    companion object {
        private const val EXTRA_ID = "clip_id"

        /** The intent that opens the editor for one clip (the caller adds FLAG_ACTIVITY_NEW_TASK when it is a service). */
        fun intent(context: Context, clipId: Long): Intent =
            Intent(context, ClipEditActivity::class.java).putExtra(EXTRA_ID, clipId)
    }
}
