package com.monsoonscript.keyboard.settings

import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import com.monsoonscript.keyboard.R
import com.monsoonscript.keyboard.ime.LearnSettings
import com.monsoonscript.keyboard.prefs.Prefs
import com.monsoonscript.keyboard.view.SpaceDrag
import com.monsoonscript.keyboard.theme.ThemesActivity

/**
 * One settings sub-page (D55): which one comes in the intent extra [EXTRA_PAGE]. Each page is its own activity, so the system Back button
 * returns to the main list ([SetupActivity]). All rows write to [Prefs] straight away; the keyboard hears the change.
 */
class SettingsPageActivity : ThemedActivity() {

    private var bubbles: BubbleHost? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val ui = SettingsUi(this)
        val page = when (intent.getStringExtra(EXTRA_PAGE)) {
            SUGGESTIONS -> suggestions(ui)
            APPEARANCE -> appearance(ui)
            ABOUT -> about(ui)
            else -> typing(ui)
        }
        // the floating "Try it" bubble (L12 R3, D83/D85) on every page
        val host = BubbleHost(this, ui, page.root)
        host.addTryIt()
        host.restoreState(savedInstanceState)
        bubbles = host
        setContentView(host.root)
        intent.getStringExtra(EXTRA_FOCUS)?.let { ui.focusRow(page, it) } // opened from the search box: scroll to the row and flash it
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        bubbles?.saveState(outState)
    }

    private fun typing(ui: SettingsUi): SettingsUi.Page {
        val page = ui.page(getString(R.string.settings_typing)) { finish() }
        with(ui) {
            page.content.add(header(getString(R.string.settings_language_header)))
            page.content.add(switchRow(getString(R.string.settings_language_key), getString(R.string.settings_language_key_summary), Prefs.LANGUAGE_KEY, true))
            page.content.add(switchRow(getString(R.string.settings_space_swipe), getString(R.string.settings_space_swipe_summary), Prefs.SPACE_SWIPE, true))
            val digits = listOf(R.string.settings_digits_english, R.string.settings_digits_bangla).map { getString(it) }
            page.content.add(choiceRow(getString(R.string.settings_digits), Prefs.DIGITS, digits, 0))
            page.content.add(note(getString(R.string.settings_digits_note)))
            page.content.add(header(getString(R.string.settings_behavior_header)))
            page.content.add(switchRow(getString(R.string.settings_auto_show), getString(R.string.settings_auto_show_summary), Prefs.AUTO_SHOW, true))
            page.content.add(note(getString(R.string.settings_auto_show_note)))
            val volume = listOf(R.string.settings_volume_off, R.string.settings_volume_up_left, R.string.settings_volume_up_right).map { getString(it) }
            page.content.add(choiceRow(getString(R.string.settings_volume_cursor), Prefs.VOLUME_CURSOR, volume, Prefs.VOLUME_OFF))
            page.content.add(note(getString(R.string.settings_volume_cursor_note)))
            page.content.add(header(getString(R.string.settings_drag_header)))
            page.content.add(switchRow(getString(R.string.settings_space_drag), getString(R.string.settings_space_drag_summary), Prefs.SPACE_DRAG, true))
            page.content.add(sliderRow(getString(R.string.settings_drag_delay), Prefs.DRAG_DELAY, SpaceDrag.DELAY_MIN, SpaceDrag.DELAY_MAX, SpaceDrag.DELAY_DEFAULT) { "${it * SpaceDrag.DELAY_UNIT_MS} ms" })
            page.content.add(sliderRow(getString(R.string.settings_drag_speed), Prefs.DRAG_SPEED, SpaceDrag.SPEED_MIN, SpaceDrag.SPEED_MAX, SpaceDrag.SPEED_DEFAULT) { it.toString() })
            page.content.add(note(getString(R.string.settings_drag_note)))
            page.content.add(header(getString(R.string.settings_feel_header)))
            val levels = listOf(R.string.settings_vib_off, R.string.settings_vib_light, R.string.settings_vib_normal, R.string.settings_vib_strong).map { getString(it) }
            page.content.add(choiceRow(getString(R.string.settings_vibration), Prefs.VIBRATION, levels, Prefs.VIB_NORMAL))
            page.content.add(note(getString(R.string.settings_vibration_note)))
            page.content.add(switchRow(getString(R.string.settings_key_sound), getString(R.string.settings_key_sound_summary), Prefs.KEY_SOUND, false))
            val sources = listOf(R.string.settings_sound_android, R.string.settings_sound_builtin).map { getString(it) }
            page.content.add(choiceRow(getString(R.string.settings_sound_source), Prefs.SOUND_SOURCE, sources, Prefs.SOUND_ANDROID))
            page.content.add(note(getString(R.string.settings_sound_source_note)))
            page.content.add(buttonRow(getString(R.string.settings_sound_settings), getString(R.string.settings_sound_settings_summary)) { openSoundSettings() })
            val delays = listOf(R.string.settings_press_short, R.string.settings_press_normal, R.string.settings_press_long).map { getString(it) }
            page.content.add(choiceRow(getString(R.string.settings_long_press), Prefs.LONG_PRESS, delays, Prefs.LONG_PRESS_NORMAL))
        }
        return page
    }

    /** Android's sound settings (no phone lets an app jump straight to the "touch sounds" switch); the general settings if that page is missing. */
    private fun openSoundSettings() {
        try {
            startActivity(Intent(Settings.ACTION_SOUND_SETTINGS))
        } catch (e: RuntimeException) {
            try {
                startActivity(Intent(Settings.ACTION_SETTINGS))
            } catch (e2: RuntimeException) {
                Toast.makeText(this, R.string.settings_sound_settings_failed, Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun suggestions(ui: SettingsUi): SettingsUi.Page {
        val page = ui.page(getString(R.string.settings_suggestions)) { finish() }
        with(ui) {
            page.content.add(header(getString(R.string.settings_strip_header)))
            page.content.add(switchRow(getString(R.string.settings_show_suggestions), getString(R.string.settings_show_suggestions_summary), Prefs.SHOW_SUGGESTIONS, true))
            page.content.add(switchRow(getString(R.string.settings_next_word), getString(R.string.settings_next_word_summary), Prefs.NEXT_WORD, true))
            page.content.add(switchRow(getString(R.string.settings_typed_first), getString(R.string.settings_typed_first_summary), Prefs.TYPED_FIRST, true))
            page.content.add(header(getString(R.string.settings_correct_header)))
            page.content.add(switchRow(getString(R.string.settings_autocorrect), getString(R.string.settings_autocorrect_summary), Prefs.AUTOCORRECT, false))
            page.content.add(header(getString(R.string.settings_personal_header)))
            page.content.add(switchRow(getString(R.string.settings_learn), getString(R.string.settings_learn_summary), Prefs.LEARN, true))
            page.content.add(buttonRow(getString(R.string.settings_clear), getString(R.string.settings_clear_summary)) { confirmClear() })
        }
        return page
    }

    private fun appearance(ui: SettingsUi): SettingsUi.Page {
        val page = ui.page(getString(R.string.settings_appearance)) { finish() }
        with(ui) {
            page.content.add(note(getString(R.string.settings_preview_note)))
            page.content.add(header(getString(R.string.settings_look_header)))
            page.content.add(navRow(getString(R.string.themes_title), getString(R.string.themes_row_summary)) { startActivity(Intent(this@SettingsPageActivity, ThemesActivity::class.java)) })
            page.content.add(switchRow(getString(R.string.settings_number_row), getString(R.string.settings_number_row_summary), Prefs.NUMBER_ROW, false))
            page.content.add(switchRow(getString(R.string.settings_emoji_key), getString(R.string.settings_emoji_key_summary), Prefs.EMOJI_KEY, true))
            page.content.add(header(getString(R.string.settings_size_header)))
            val percent = { v: Int -> getString(R.string.settings_percent, v) }
            page.content.add(sliderRow(getString(R.string.settings_height_portrait), Prefs.HEIGHT_PORTRAIT, Prefs.HEIGHT_MIN, Prefs.HEIGHT_MAX, Prefs.HEIGHT_DEFAULT, percent))
            page.content.add(sliderRow(getString(R.string.settings_height_landscape), Prefs.HEIGHT_LANDSCAPE, Prefs.HEIGHT_MIN, Prefs.HEIGHT_MAX, Prefs.HEIGHT_DEFAULT, percent))
            page.content.add(sliderRow(getString(R.string.settings_key_gap), Prefs.KEY_GAP, Prefs.GAP_MIN, Prefs.GAP_MAX, Prefs.GAP_DEFAULT) { getString(R.string.settings_dp, it) })
            page.content.add(sliderRow(getString(R.string.settings_key_text), Prefs.KEY_TEXT_SIZE, Prefs.TEXT_SIZE_MIN, Prefs.TEXT_SIZE_MAX, Prefs.TEXT_SIZE_DEFAULT, percent))
            page.content.add(sliderRow(getString(R.string.settings_strip_text), Prefs.STRIP_TEXT_SIZE, Prefs.TEXT_SIZE_MIN, Prefs.TEXT_SIZE_MAX, Prefs.TEXT_SIZE_DEFAULT, percent))
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
        const val EXTRA_FOCUS = "focus"
        const val TYPING = "typing"
        const val SUGGESTIONS = "suggestions"
        const val APPEARANCE = "appearance"
        const val ABOUT = "about"

        /** [focus] = the title of a row to scroll to and flash (from the search box). */
        fun intent(context: Context, page: String, focus: String? = null): Intent =
            Intent(context, SettingsPageActivity::class.java).putExtra(EXTRA_PAGE, page).also { if (focus != null) it.putExtra(EXTRA_FOCUS, focus) }
    }
}
