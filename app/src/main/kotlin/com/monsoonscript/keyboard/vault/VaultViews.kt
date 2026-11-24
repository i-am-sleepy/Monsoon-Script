package com.monsoonscript.keyboard.vault

import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.content.res.ColorStateList
import android.graphics.drawable.Drawable
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import com.monsoonscript.keyboard.R
import com.monsoonscript.keyboard.settings.SettingsUi

/** Small view helpers shared by the vault screens (plain framework views, themed like the rest of the app, D30). */

/** The red used for errors and for "delete" (the keyboard themes have no error color). */
internal const val VAULT_RED = 0xFFE5534B.toInt()

/** [view] with [horizontalDp] / [verticalDp] space around it, for a button or a field placed straight on a page. */
internal fun SettingsUi.padded(view: View, horizontalDp: Int = 16, verticalDp: Int = 8): View = FrameLayout(ctx).apply {
    setPadding(dp(horizontalDp), dp(verticalDp), dp(horizontalDp), dp(verticalDp))
    addView(view, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
}

/** A round touch target holding one icon. */
internal fun SettingsUi.iconButton(kind: VaultIcons.Kind, description: String, color: Int = colors.text, sizeDp: Int = 44, onClick: () -> Unit): ImageView =
    ImageView(ctx).apply {
        setImageDrawable(VaultIcons.drawable(kind, color, dp(24)))
        scaleType = ImageView.ScaleType.CENTER
        contentDescription = description
        background = ripple()
        layoutParams = LinearLayout.LayoutParams(dp(sizeDp), dp(sizeDp))
        setOnClickListener { onClick() }
    }

internal fun SettingsUi.vaultIcon(kind: VaultIcons.Kind, color: Int = colors.text, sizeDp: Int = 24): Drawable = VaultIcons.drawable(kind, color, dp(sizeDp))

/** A rounded square with a vault icon in it (the vault counterpart of [SettingsUi.iconChip]). */
internal fun SettingsUi.vaultChip(kind: VaultIcons.Kind, chipDp: Int = 40): View = FrameLayout(ctx).apply {
    background = rounded(colors.chip, 12)
    addView(ImageView(ctx).apply { setImageDrawable(vaultIcon(kind, colors.accent, chipDp * 11 / 20)) },
        FrameLayout.LayoutParams(dp(chipDp * 11 / 20), dp(chipDp * 11 / 20), Gravity.CENTER))
}

/** A rounded square with one letter in it (an entry's first letter). */
internal fun SettingsUi.letterChip(letter: String, chipDp: Int = 40): View = TextView(ctx).apply {
    text = letter
    textSize = 17f
    gravity = Gravity.CENTER
    setTextColor(colors.accent)
    background = rounded(colors.chip, 12)
    layoutParams = LinearLayout.LayoutParams(dp(chipDp), dp(chipDp))
}

/** An accent-colored text button in a dialog, the way the rest of the app does it. */
internal fun SettingsUi.styleDialog(dialog: AlertDialog) {
    for (b in intArrayOf(AlertDialog.BUTTON_POSITIVE, AlertDialog.BUTTON_NEGATIVE, AlertDialog.BUTTON_NEUTRAL)) dialog.getButton(b)?.setTextColor(colors.accent)
}

/**
 * A password box: hidden text with an eye to show it, and optionally one more icon at its end (the generator). The text is read as a [CharArray] so the
 * caller can wipe it after use ([chars], [clear]); Android's own text storage may still hold copies for a short time (the honest limit of D-design).
 */
internal class PasswordField(context: Context, private val ui: SettingsUi, hint: String, extra: View? = null) : LinearLayout(context) {
    val edit = EditText(context)
    private var shown = false
    private val eye: ImageView

    init {
        orientation = HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        background = ui.rounded(ui.colors.card, 12, ui.colors.cardStroke)
        setPadding(ui.dp(14), 0, ui.dp(4), 0)
        edit.apply {
            this.hint = hint
            textSize = 17f
            setTextColor(ui.colors.text)
            setHintTextColor(ui.colors.muted)
            background = null
            setSingleLine()
            applyType()
            imeOptions = EditorInfo.IME_ACTION_DONE
            setPadding(0, ui.dp(14), 0, ui.dp(14))
        }
        addView(edit, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        eye = ui.iconButton(VaultIcons.Kind.EYE, context.getString(R.string.vault_show_hide), ui.colors.muted) { toggle() }
        addView(eye)
        if (extra != null) addView(extra)
    }

    private fun applyType() {
        val variation = if (shown) InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD else InputType.TYPE_TEXT_VARIATION_PASSWORD
        edit.inputType = InputType.TYPE_CLASS_TEXT or variation or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
    }

    fun toggle() {
        shown = !shown
        val end = edit.selectionEnd
        applyType()
        edit.setSelection(maxOf(0, minOf(end, edit.length())))
        eye.setImageDrawable(VaultIcons.drawable(if (shown) VaultIcons.Kind.EYE_OFF else VaultIcons.Kind.EYE, ui.colors.muted, ui.dp(24)))
    }

    fun chars(): CharArray {
        val n = edit.length()
        val a = CharArray(n)
        edit.text.getChars(0, n, a, 0)
        return a
    }

    fun clear() {
        edit.setText("")
    }
}

/** A plain one-line or multi-line text box styled like [PasswordField] (names, user names, notes ...). */
internal fun SettingsUi.textBox(hint: String, multiLine: Boolean = false, inputType: Int = InputType.TYPE_CLASS_TEXT): EditText = EditText(ctx).apply {
    this.hint = hint
    textSize = 17f
    setTextColor(colors.text)
    setHintTextColor(colors.muted)
    background = rounded(colors.card, 12, colors.cardStroke)
    setPadding(dp(14), dp(14), dp(14), dp(14))
    if (multiLine) {
        this.inputType = inputType or InputType.TYPE_TEXT_FLAG_MULTI_LINE or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
        minLines = 3
        gravity = Gravity.TOP or Gravity.START
    } else {
        this.inputType = inputType
        setSingleLine()
    }
}

/** The small spinner dialog shown while the vault works (a save takes about a second: the key derivation is deliberately slow). */
internal class BusyDialog(private val activity: Activity, private val ui: SettingsUi) {
    private var dialog: AlertDialog? = null

    fun show(message: String) {
        hide()
        if (activity.isFinishing || activity.isDestroyed) return
        val spinner = ProgressBar(activity).apply { indeterminateTintList = ColorStateList.valueOf(ui.colors.accent) }
        val row = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(ui.dp(24), ui.dp(20), ui.dp(24), ui.dp(20))
            addView(spinner, LinearLayout.LayoutParams(ui.dp(32), ui.dp(32)))
            addView(TextView(activity).apply {
                text = message
                textSize = 16f
                setTextColor(ui.colors.text)
                setPadding(ui.dp(16), 0, 0, 0)
            })
        }
        dialog = AlertDialog.Builder(activity).setView(row).setCancelable(false).create().also { it.show() }
    }

    fun hide() {
        try {
            dialog?.dismiss()
        } catch (_: RuntimeException) {
        }
        dialog = null
    }
}
