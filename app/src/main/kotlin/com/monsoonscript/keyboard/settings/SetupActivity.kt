package com.monsoonscript.keyboard.settings

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import com.monsoonscript.keyboard.R

/** Placeholder setup screen: enable the keyboard, then choose it. The real settings UI comes later (needs owner design input). */
class SetupActivity : Activity() {

    private lateinit var status: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val dp = resources.displayMetrics.density
        val pad = (24 * dp).toInt()

        val title = TextView(this).apply {
            setText(R.string.app_name)
            textSize = 28f
        }
        status = TextView(this).apply {
            textSize = 16f
            setPadding(0, (16 * dp).toInt(), 0, (16 * dp).toInt())
        }
        val enable = Button(this).apply {
            setText(R.string.setup_enable)
            setOnClickListener { startActivity(Intent(Settings.ACTION_INPUT_METHOD_SETTINGS)) }
        }
        val choose = Button(this).apply {
            setText(R.string.setup_choose)
            setOnClickListener { imm().showInputMethodPicker() }
        }

        setContentView(
            LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(pad, pad * 2, pad, pad)
                addView(title)
                addView(status)
                addView(enable)
                addView(choose)
            }
        )
    }

    override fun onResume() {
        super.onResume()
        status.setText(statusText())
    }

    private fun imm(): InputMethodManager = getSystemService(InputMethodManager::class.java)

    private fun statusText(): Int = when {
        !isEnabled() -> R.string.setup_status_off
        !isSelected() -> R.string.setup_status_enabled
        else -> R.string.setup_status_active
    }

    private fun isEnabled(): Boolean = imm().enabledInputMethodList.any { it.packageName == packageName }

    private fun isSelected(): Boolean {
        val id = Settings.Secure.getString(contentResolver, Settings.Secure.DEFAULT_INPUT_METHOD) ?: return false
        return id.startsWith("$packageName/")
    }
}
