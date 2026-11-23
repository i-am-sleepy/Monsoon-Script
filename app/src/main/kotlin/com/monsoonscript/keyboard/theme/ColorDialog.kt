package com.monsoonscript.keyboard.theme

import android.app.Activity
import android.app.AlertDialog
import android.text.Editable
import android.text.InputFilter
import android.text.InputType
import android.text.TextWatcher
import android.view.View
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import com.monsoonscript.keyboard.R

/** The dialog to choose one color (L10, D75): the picker, the old and the new color side by side, and a field for a hex code like #1B6B63. */
internal object ColorDialog {
    /**
     * [initial] is the color now. [onPick] gets the chosen color. [onAutomatic], when not null, adds an "Automatic" button (a derived color that may follow the
     * main ones again).
     */
    fun show(activity: Activity, title: String, initial: Int, onPick: (Int) -> Unit, onAutomatic: (() -> Unit)? = null) {
        val d = activity.resources.displayMetrics.density
        fun dp(v: Int) = (v * d + 0.5f).toInt()

        var current = initial
        val box = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(8), dp(20), dp(4))
        }
        val oldView = View(activity).apply { setBackgroundColor(initial) }
        val newView = View(activity).apply { setBackgroundColor(initial) }
        val swatches = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            addView(oldView, LinearLayout.LayoutParams(0, dp(34), 1f))
            addView(newView, LinearLayout.LayoutParams(0, dp(34), 1f))
        }
        box.addView(swatches, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))
        box.addView(TextView(activity).apply {
            text = activity.getString(R.string.theme_color_old_new)
            textSize = 12f
            setPadding(0, dp(2), 0, dp(10))
        })

        val picker = HsvPickerView(activity).also { it.setColor(initial) }
        box.addView(picker, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))

        var settingHex = false
        val hex = EditText(activity).apply {
            setText(ColorMath.hex(initial))
            filters = arrayOf(InputFilter.LengthFilter(7))
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS or InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS
            setSingleLine()
            contentDescription = activity.getString(R.string.theme_color_hex)
        }
        box.addView(hex, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(12) })

        picker.onChange = { c ->
            current = c
            newView.setBackgroundColor(c)
            settingHex = true
            hex.setText(ColorMath.hex(c))
            settingHex = false
        }
        hex.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                if (settingHex) return
                val c = ColorMath.parseHex(s?.toString().orEmpty()) ?: return
                current = c
                newView.setBackgroundColor(c)
                picker.setColor(c)
            }
        })

        val scroll = ScrollView(activity).apply { addView(box) }
        val builder = AlertDialog.Builder(activity)
            .setTitle(title)
            .setView(scroll)
            .setPositiveButton(android.R.string.ok) { _, _ -> onPick(current) }
            .setNegativeButton(android.R.string.cancel, null)
        if (onAutomatic != null) builder.setNeutralButton(R.string.theme_color_automatic) { _, _ -> onAutomatic() }
        builder.show()
    }
}
