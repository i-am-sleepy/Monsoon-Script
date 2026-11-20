package com.monsoonscript.keyboard.settings

import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.Toast
import com.monsoonscript.keyboard.R
import com.monsoonscript.keyboard.ime.LearnSettings
import com.monsoonscript.keyboard.prefs.Prefs

/**
 * One settings sub-page (D55): which one comes in the intent extra [EXTRA_PAGE]. Each page is its own activity, so the system Back button
 * returns to the main list ([SetupActivity]). All rows write to [Prefs] straight away; the keyboard hears the change.
 */
class SettingsPageActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val ui = SettingsUi(this)
        val page = when (intent.getStringExtra(EXTRA_PAGE)) {
            SUGGESTIONS -> suggestions(ui)
            ABOUT -> about(ui)
            else -> typing(ui)
        }
        setContentView(page.root)
    }

    private fun typing(ui: SettingsUi): SettingsUi.Page {
        val page = ui.page(getString(R.string.settings_typing)) { finish() }
        with(ui) {
            page.content.add(header(getString(R.string.settings_language_header)))
            page.content.add(switchRow(getString(R.string.settings_language_key), getString(R.string.settings_language_key_summary), Prefs.LANGUAGE_KEY, true))
            page.content.add(switchRow(getString(R.string.settings_space_swipe), getString(R.string.settings_space_swipe_summary), Prefs.SPACE_SWIPE, true))
        }
        return page
    }

    private fun suggestions(ui: SettingsUi): SettingsUi.Page {
        val page = ui.page(getString(R.string.settings_suggestions)) { finish() }
        with(ui) {
            page.content.add(header(getString(R.string.settings_strip_header)))
            page.content.add(switchRow(getString(R.string.settings_show_suggestions), getString(R.string.settings_show_suggestions_summary), Prefs.SHOW_SUGGESTIONS, true))
            page.content.add(switchRow(getString(R.string.settings_next_word), getString(R.string.settings_next_word_summary), Prefs.NEXT_WORD, true))
            page.content.add(switchRow(getString(R.string.settings_typed_first), getString(R.string.settings_typed_first_summary), Prefs.TYPED_FIRST, true))
            page.content.add(header(getString(R.string.settings_personal_header)))
            page.content.add(switchRow(getString(R.string.settings_learn), getString(R.string.settings_learn_summary), Prefs.LEARN, true))
            page.content.add(buttonRow(getString(R.string.settings_clear), getString(R.string.settings_clear_summary)) { confirmClear() })
        }
        return page
    }

    private fun about(ui: SettingsUi): SettingsUi.Page {
        val page = ui.page(getString(R.string.settings_about)) { finish() }
        with(ui) {
            page.content.add(infoRow(getString(R.string.app_name), getString(R.string.settings_version, versionName())))
            page.content.add(navRow(getString(R.string.settings_licenses), getString(R.string.settings_licenses_summary)) {
                startActivity(Intent(this@SettingsPageActivity, LicensesActivity::class.java))
            })
        }
        return page
    }

    private fun confirmClear() {
        AlertDialog.Builder(this)
            .setTitle(R.string.settings_clear)
            .setMessage(R.string.settings_clear_confirm)
            .setPositiveButton(R.string.settings_clear_yes) { _, _ ->
                LearnSettings.clearLearnedWords(this)
                Toast.makeText(this, R.string.settings_cleared, Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun versionName(): String = try {
        packageManager.getPackageInfo(packageName, 0).versionName ?: "?"
    } catch (e: PackageManager.NameNotFoundException) {
        "?"
    }

    companion object {
        const val EXTRA_PAGE = "page"
        const val TYPING = "typing"
        const val SUGGESTIONS = "suggestions"
        const val ABOUT = "about"

        fun intent(context: Context, page: String): Intent =
            Intent(context, SettingsPageActivity::class.java).putExtra(EXTRA_PAGE, page)
    }
}
