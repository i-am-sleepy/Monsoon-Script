package com.monsoonscript.keyboard.vault

import android.app.Activity
import android.app.AlertDialog
import android.content.res.ColorStateList
import android.graphics.Typeface
import android.view.Gravity
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.Switch
import android.widget.TextView
import com.monsoonscript.keyboard.R
import com.monsoonscript.keyboard.settings.SettingsUi

/**
 * The password generator (E1.13, D81): random characters, length 8 to 64, switches for A-Z, a-z, 0-9 and symbols, one tap for another password. [onUse] set =
 * the dialog came from the edit form and offers "Use"; null = the menu's generator, which only copies. What the owner chose is remembered.
 */
internal object GeneratorDialog {
    fun show(activity: Activity, ui: SettingsUi, onUse: ((String) -> Unit)?) {
        val sp = VaultPrefs(activity).raw
        var length = sp.getInt("gen_length", PasswordGenerator.DEFAULT_LENGTH).coerceIn(PasswordGenerator.MIN_LENGTH, PasswordGenerator.MAX_LENGTH)
        var upper = sp.getBoolean("gen_upper", true)
        var lower = sp.getBoolean("gen_lower", true)
        var digits = sp.getBoolean("gen_digits", true)
        var symbols = sp.getBoolean("gen_symbols", true)
        var current = ""

        val result = TextView(activity).apply {
            typeface = Typeface.MONOSPACE
            textSize = 19f
            setTextColor(ui.colors.text)
            setTextIsSelectable(true)
            setPadding(ui.dp(14), ui.dp(14), ui.dp(14), ui.dp(14))
            background = ui.rounded(ui.colors.card, 12, ui.colors.cardStroke)
        }
        val strength = TextView(activity).apply {
            textSize = 13f
            setTextColor(ui.colors.muted)
            setPadding(ui.dp(4), ui.dp(6), 0, ui.dp(8))
        }
        val lengthLabel = TextView(activity).apply {
            textSize = 15f
            setTextColor(ui.colors.text)
        }

        fun regenerate() {
            // The last switch cannot be turned off, so there is always something to pick from.
            current = PasswordGenerator.generate(length, upper, lower, digits, symbols)
            result.text = current
            val label = when (PasswordStrength.level(current)) {
                PasswordStrength.Level.EMPTY, PasswordStrength.Level.WEAK -> activity.getString(R.string.vault_strength_weak)
                PasswordStrength.Level.FAIR -> activity.getString(R.string.vault_strength_fair)
                PasswordStrength.Level.GOOD -> activity.getString(R.string.vault_strength_good)
                PasswordStrength.Level.STRONG -> activity.getString(R.string.vault_strength_strong)
            }
            strength.text = activity.getString(R.string.vault_strength, label)
            lengthLabel.text = activity.getString(R.string.vault_gen_length, length)
        }

        fun save() {
            sp.edit().putInt("gen_length", length).putBoolean("gen_upper", upper).putBoolean("gen_lower", lower)
                .putBoolean("gen_digits", digits).putBoolean("gen_symbols", symbols).apply()
        }

        val seek = SeekBar(activity).apply {
            max = PasswordGenerator.MAX_LENGTH - PasswordGenerator.MIN_LENGTH
            progress = length - PasswordGenerator.MIN_LENGTH
            progressTintList = ColorStateList.valueOf(ui.colors.accent)
            thumbTintList = ColorStateList.valueOf(ui.colors.accent)
            progressBackgroundTintList = ColorStateList.valueOf(ui.colors.muted)
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) {
                    length = progress + PasswordGenerator.MIN_LENGTH
                    if (fromUser) {
                        save()
                        regenerate()
                    } else {
                        lengthLabel.text = activity.getString(R.string.vault_gen_length, length)
                    }
                }

                override fun onStartTrackingTouch(seekBar: SeekBar) {}

                override fun onStopTrackingTouch(seekBar: SeekBar) {}
            })
        }

        val switches = ArrayList<Switch>()
        fun switchRow(label: String, initial: Boolean, set: (Boolean) -> Unit, get: () -> Boolean): LinearLayout {
            val sw = Switch(activity).apply {
                isChecked = initial
                val states = arrayOf(intArrayOf(android.R.attr.state_checked), intArrayOf())
                thumbTintList = ColorStateList(states, intArrayOf(ui.colors.accent, ui.colors.muted))
                trackTintList = ColorStateList(states, intArrayOf(ui.colors.accent, ui.colors.muted))
            }
            switches += sw
            sw.setOnCheckedChangeListener { _, on ->
                set(on)
                if (!upper && !lower && !digits && !symbols) { // never all off: put this one back
                    set(true)
                    sw.isChecked = true
                    return@setOnCheckedChangeListener
                }
                if (get() == on) {
                    save()
                    regenerate()
                }
            }
            return LinearLayout(activity).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(0, ui.dp(2), 0, ui.dp(2))
                addView(TextView(activity).apply {
                    text = label
                    textSize = 16f
                    setTextColor(ui.colors.text)
                }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
                addView(sw)
            }
        }

        val again = ui.iconButton(VaultIcons.Kind.REFRESH, activity.getString(R.string.vault_gen_again), ui.colors.accent) { regenerate() }
        val top = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            addView(result, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            addView(again)
        }
        val body = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(ui.dp(20), ui.dp(12), ui.dp(20), ui.dp(4))
            addView(top)
            addView(strength)
            addView(lengthLabel)
            addView(seek)
            addView(switchRow(activity.getString(R.string.vault_gen_upper), upper, { upper = it }, { upper }))
            addView(switchRow(activity.getString(R.string.vault_gen_lower), lower, { lower = it }, { lower }))
            addView(switchRow(activity.getString(R.string.vault_gen_digits), digits, { digits = it }, { digits }))
            addView(switchRow(activity.getString(R.string.vault_gen_symbols), symbols, { symbols = it }, { symbols }))
        }
        regenerate()

        val builder = AlertDialog.Builder(activity)
            .setTitle(R.string.vault_generator)
            .setView(body)
            .setNegativeButton(R.string.vault_close, null)
        if (onUse != null) {
            builder.setPositiveButton(R.string.vault_use) { _, _ -> onUse(current) }
            builder.setNeutralButton(R.string.vault_copy, null)
        } else {
            builder.setPositiveButton(R.string.vault_copy, null)
        }
        val dialog = builder.create()
        dialog.show()
        ui.styleDialog(dialog)
        // "Copy" keeps the dialog open (the owner may copy, then ask for another): its click is set after show().
        val copyButton = dialog.getButton(if (onUse != null) AlertDialog.BUTTON_NEUTRAL else AlertDialog.BUTTON_POSITIVE)
        copyButton.setOnClickListener { VaultClip.copy(activity, current, activity.getString(R.string.vault_password_copied)) }
    }
}
