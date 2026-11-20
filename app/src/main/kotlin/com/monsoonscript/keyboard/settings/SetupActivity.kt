package com.monsoonscript.keyboard.settings

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.view.inputmethod.InputMethodManager
import android.widget.TextView
import com.monsoonscript.keyboard.R

/**
 * The main screen (and the keyboard's settings entry): setup status and the two setup steps on top, then the list of settings pages (D55):
 * Typing, Suggestions, About and licenses. Appearance joins when its settings exist (G1.5). Each row opens [SettingsPageActivity].
 */
class SetupActivity : Activity() {

    private lateinit var status: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val ui = SettingsUi(this)
        val page = ui.page(getString(R.string.app_name), null)
        status = TextView(this).apply {
            textSize = 16f
            setTextColor(ui.secondary)
            setPadding(ui.dp(20), 0, ui.dp(20), ui.dp(8))
        }
        with(ui) {
            page.content.add(status)
            page.content.add(buttonRow(getString(R.string.setup_enable), null) { startActivity(Intent(Settings.ACTION_INPUT_METHOD_SETTINGS)) })
            page.content.add(buttonRow(getString(R.string.setup_choose), null) { imm().showInputMethodPicker() })
            page.content.add(header(getString(R.string.settings_header)))
            page.content.add(navRow(getString(R.string.settings_typing), getString(R.string.settings_typing_summary)) { open(SettingsPageActivity.TYPING) })
            page.content.add(navRow(getString(R.string.settings_suggestions), getString(R.string.settings_suggestions_summary)) { open(SettingsPageActivity.SUGGESTIONS) })
            page.content.add(navRow(getString(R.string.settings_appearance), getString(R.string.settings_appearance_summary)) { open(SettingsPageActivity.APPEARANCE) })
            page.content.add(navRow(getString(R.string.settings_about), getString(R.string.settings_about_summary)) { open(SettingsPageActivity.ABOUT) })
        }
        setContentView(page.root)
    }

    private fun open(page: String) = startActivity(SettingsPageActivity.intent(this, page))

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
