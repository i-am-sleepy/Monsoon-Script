package com.monsoonscript.keyboard.theme

import android.app.AlertDialog
import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.text.InputType
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.LinearLayout
import com.monsoonscript.keyboard.R
import com.monsoonscript.keyboard.prefs.Prefs
import com.monsoonscript.keyboard.settings.BubbleHost
import com.monsoonscript.keyboard.settings.SettingsUi
import com.monsoonscript.keyboard.settings.ThemedActivity

/**
 * The list of themes (L10, D75): a preview, the choice "follow the phone / always day / always night", and every theme (ready-made first, then the owner's own).
 * A tap opens the actions: use it for day or night, edit or copy it, rename, duplicate, delete.
 */
class ThemesActivity : ThemedActivity() {
    private lateinit var ui: SettingsUi
    private lateinit var page: SettingsUi.Page
    private var bubbles: BubbleHost? = null
    private val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ -> render() }

    /** Choosing another day or night theme recolors this screen (and the app) at once (L12, D83). */
    override val liveRecolor: Boolean get() = true

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ui = SettingsUi(this)
        page = ui.page(getString(R.string.themes_title)) { finish() }
        val host = BubbleHost(this, ui, page.root) // the "Try it" bubble: the real keyboard follows the chosen themes at once (L12 R3, D83/D85)
        host.addTryIt()
        host.restoreState(savedInstanceState)
        bubbles = host
        setContentView(host.root)
        render()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        bubbles?.saveState(outState)
    }

    override fun onResume() {
        super.onResume()
        Prefs.of(this).registerOnSharedPreferenceChangeListener(listener)
        render() // the editor may have changed a theme
    }

    override fun onPause() {
        Prefs.of(this).unregisterOnSharedPreferenceChangeListener(listener)
        super.onPause()
    }

    private fun render() {
        page.content.removeAllViews()
        val day = ThemeStore.day(this)
        val night = ThemeStore.night(this)
        with(ui) {
            page.content.add(note(getString(R.string.themes_intro)))
            val modes = listOf(R.string.themes_mode_system, R.string.themes_mode_day, R.string.themes_mode_night).map { getString(it) }
            page.content.add(choiceRow(getString(R.string.themes_mode), Prefs.THEME, modes, 0))
            page.content.add(buttonRow(getString(R.string.themes_new), getString(R.string.themes_new_summary)) { createNew() })
            page.content.add(header(getString(R.string.themes_list_header)))
            for (t in ThemeStore.all(this@ThemesActivity)) page.content.add(themeRow(t, day.id, night.id))
        }
    }

    private fun themeRow(t: ThemeSpec, dayId: String, nightId: String): View = ui.row().apply {
        addView(
            ThemeSwatchView(this@ThemesActivity).also { it.spec = t },
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { rightMargin = ui.dp(16) },
        )
        val tags = ArrayList<String>()
        tags += getString(if (t.builtIn) R.string.themes_tag_builtin else R.string.themes_tag_yours)
        if (t.id == dayId) tags += getString(R.string.themes_tag_day)
        if (t.id == nightId) tags += getString(R.string.themes_tag_night)
        addView(ui.texts(t.name, tags.joinToString(" · ")), ui.weighted())
        setOnClickListener { actions(t) }
    }

    private fun actions(t: ThemeSpec) {
        val items = ArrayList<Pair<String, () -> Unit>>()
        items += getString(R.string.themes_action_day) to { ThemeStore.setDay(this, t.id) }
        items += getString(R.string.themes_action_night) to { ThemeStore.setNight(this, t.id) }
        if (t.builtIn) {
            items += getString(R.string.themes_action_copy) to { edit(copy(t)) }
        } else {
            items += getString(R.string.themes_action_edit) to { edit(t) }
            items += getString(R.string.themes_action_duplicate) to { copy(t) }
            items += getString(R.string.themes_action_rename) to { rename(t) }
            items += getString(R.string.themes_action_delete) to { confirmDelete(t) }
        }
        AlertDialog.Builder(this).setTitle(t.name)
            .setItems(items.map { it.first }.toTypedArray()) { _, i -> items[i].second() }
            .show()
    }

    /** A saved custom copy of [t] with a name that is not taken. */
    private fun copy(t: ThemeSpec): ThemeSpec {
        val c = ThemeStore.copyOf(this, t, getString(R.string.themes_copy_name, t.name))
        ThemeStore.save(this, c)
        return c
    }

    private fun createNew() {
        val c = ThemeStore.copyOf(this, ThemeStore.day(this), getString(R.string.themes_new_name))
        ThemeStore.save(this, c)
        edit(c)
    }

    private fun edit(t: ThemeSpec) {
        startActivity(Intent(this, ThemeEditActivity::class.java).putExtra(ThemeEditActivity.EXTRA_ID, t.id))
    }

    private fun rename(t: ThemeSpec) {
        val field = EditText(this).apply {
            setText(t.name)
            setSelectAllOnFocus(true)
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
            setSingleLine()
        }
        AlertDialog.Builder(this).setTitle(R.string.themes_rename_title).setView(field)
            .setPositiveButton(android.R.string.ok) { _, _ -> ThemeStore.save(this, t.copy(name = ThemeCodec.cleanName(field.text.toString()))) }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun confirmDelete(t: ThemeSpec) {
        AlertDialog.Builder(this).setTitle(getString(R.string.themes_delete_title, t.name)).setMessage(R.string.themes_delete_message)
            .setPositiveButton(R.string.themes_action_delete) { _, _ -> ThemeStore.delete(this, t.id) }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }
}
