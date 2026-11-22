package com.monsoonscript.keyboard.clipboard.app

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.text.InputType
import android.view.WindowManager
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.Toast
import com.monsoonscript.keyboard.R
import com.monsoonscript.keyboard.clipboard.ClipCapture
import com.monsoonscript.keyboard.clipboard.ClipEditActivity
import com.monsoonscript.keyboard.clipboard.ClipHub
import com.monsoonscript.keyboard.clipboard.ClipItem
import com.monsoonscript.keyboard.clipboard.ClipStore
import com.monsoonscript.keyboard.clipboard.ImageShare

/** The things a clip can do in the app's screens (copy, share, edit, move, delete) and the small dialogs they need. Every change goes through the [store]; the screens update by listening to it. */
internal class ClipActions(private val activity: Activity, private val store: ClipStore) {

    private fun dp(v: Int): Int = (v * activity.resources.displayMetrics.density + 0.5f).toInt()

    fun toast(res: Int) = Toast.makeText(activity, res, Toast.LENGTH_SHORT).show()

    /** Put a clip (text or picture) back on the system clipboard and move it to the top of the history. */
    fun copy(clip: ClipItem) {
        val ok = if (clip.isImage) {
            val name = clip.image ?: return
            ClipCapture.copyImageWithoutRecording(activity, ClipHub.get(activity).images.uri(name), clip.mime ?: "image/png")
        } else {
            ClipCapture.copyWithoutRecording(activity, clip.text)
        }
        if (ok) {
            store.touch(clip.id)
            toast(R.string.clip_copied)
        }
    }

    fun shareImage(clip: ClipItem) {
        val chooser = ImageShare.chooser(ClipHub.get(activity).images, clip) ?: return
        try {
            activity.startActivity(chooser)
        } catch (e: RuntimeException) {
            // no app can take it
        }
    }

    fun share(text: String) {
        if (text.isEmpty()) return
        val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text)
        try {
            activity.startActivity(Intent.createChooser(send, null))
        } catch (e: RuntimeException) {
            // no app can take it
        }
    }

    fun edit(clip: ClipItem) {
        if (clip.isText) activity.startActivity(ClipEditActivity.intent(activity, clip.id))
    }

    fun confirmDelete(count: Int, then: () -> Unit) {
        val title = if (count == 1) activity.getString(R.string.clip_delete_one) else activity.getString(R.string.clip_delete_many, count)
        AlertDialog.Builder(activity)
            .setTitle(title)
            .setPositiveButton(R.string.clip_delete_confirm) { _, _ -> then() }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    /** Ask which folder to move to ("No folder" first); [onPick] gets the folder id, 0 = none. */
    fun pickFolder(onPick: (Long) -> Unit) {
        val folders = store.folders
        if (folders.isEmpty()) {
            toast(R.string.clip_no_folders_yet)
            return
        }
        val ids = LongArray(folders.size + 1)
        val names = Array(folders.size + 1) { "" }
        names[0] = activity.getString(R.string.clip_no_folder)
        for (i in folders.indices) {
            ids[i + 1] = folders[i].id
            names[i + 1] = folders[i].name
        }
        AlertDialog.Builder(activity)
            .setTitle(R.string.clip_move_title)
            .setItems(names) { _, which -> onPick(ids[which]) }
            .show()
    }

    /** The list of things to do with one clip (tap on a row). */
    fun showMenu(clip: ClipItem) {
        val labels = ArrayList<String>()
        val actions = ArrayList<() -> Unit>()
        fun add(res: Int, run: () -> Unit) {
            labels.add(activity.getString(res))
            actions.add(run)
        }
        add(R.string.clip_act_copy) { copy(clip) }
        add(if (clip.pinned) R.string.clip_act_unpin else R.string.clip_act_pin) { store.setPinned(listOf(clip.id), !clip.pinned) }
        if (clip.isText) add(R.string.clip_act_edit) { edit(clip) }
        add(R.string.clip_act_share) { if (clip.isImage) shareImage(clip) else share(clip.text) }
        add(R.string.clip_act_move) { pickFolder { store.moveToFolder(listOf(clip.id), it) } }
        add(R.string.clip_act_delete) { confirmDelete(1) { store.delete(listOf(clip.id)) } }
        AlertDialog.Builder(activity)
            .setItems(labels.toTypedArray()) { _, which -> actions[which]() }
            .show()
    }

    /** A dialog with one text field (a folder name). [onOk] returns false to keep the dialog open (the name was refused). */
    fun askText(title: Int, hint: Int, initial: String, ok: Int, onOk: (String) -> Boolean) {
        val field = EditText(activity).apply {
            this.hint = activity.getString(hint)
            setText(initial)
            setSelection(text.length)
            setSingleLine()
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
        }
        val box = LinearLayout(activity).apply {
            setPadding(dp(20), dp(8), dp(20), 0)
            addView(field, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))
        }
        val dialog = AlertDialog.Builder(activity)
            .setTitle(title)
            .setView(box)
            .setPositiveButton(ok, null)
            .setNegativeButton(android.R.string.cancel, null)
            .create()
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                if (onOk(field.text.toString())) dialog.dismiss()
            }
        }
        dialog.window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE)
        dialog.show()
    }
}
