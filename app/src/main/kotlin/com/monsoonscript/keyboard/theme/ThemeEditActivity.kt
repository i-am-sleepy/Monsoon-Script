package com.monsoonscript.keyboard.theme

import android.app.AlertDialog
import android.os.Bundle
import android.text.InputType
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.LinearLayout
import com.monsoonscript.keyboard.R
import com.monsoonscript.keyboard.settings.BubbleHost
import com.monsoonscript.keyboard.settings.SettingsUi
import com.monsoonscript.keyboard.settings.ThemedActivity

/**
 * The editor of one custom theme (L10, D75): a live preview, the name, the five main colors, "advanced colors" (the six that follow by themselves unless set), and
 * what to use it for. Every change is saved at once, so the keyboard shows it too. Notes warn about colors that are hard to read together; nothing is blocked.
 */
class ThemeEditActivity : ThemedActivity() {
    private lateinit var ui: SettingsUi
    private lateinit var page: SettingsUi.Page
    private lateinit var spec: ThemeSpec
    private var bubbles: BubbleHost? = null
    private var advanced = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val found = ThemeStore.find(this, intent.getStringExtra(EXTRA_ID))
        if (found == null || found.builtIn) { // a ready-made theme is copied first, in the list
            finish()
            return
        }
        spec = found
        advanced = savedInstanceState?.getBoolean(STATE_ADVANCED) ?: false
        ui = SettingsUi(this)
        page = ui.page(getString(R.string.theme_edit_title)) { finish() }
        val host = BubbleHost(this, ui, page.root) // the "Try it" bubble: the real keyboard shows this theme once it is the day or night theme (L12 R3, D83/D85)
        host.addTryIt()
        host.restoreState(savedInstanceState)
        bubbles = host
        setContentView(host.root)
        render()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putBoolean(STATE_ADVANCED, advanced)
        bubbles?.saveState(outState)
    }

    private fun change(newSpec: ThemeSpec) {
        spec = newSpec
        ThemeStore.save(this, spec)
        render()
    }

    private fun render() {
        page.content.removeAllViews()
        with(ui) {
            page.content.add(note(getString(R.string.theme_edit_preview_note)))
            page.content.add(navRow(getString(R.string.theme_edit_name), spec.name) { rename() })
            page.content.add(header(getString(R.string.theme_edit_colors_header)))
            for (s in MAIN) page.content.add(colorRow(s))
            for (w in spec.warnings()) page.content.add(note("⚠ " + getString(warnText(w))))
            page.content.add(buttonRow(getString(if (advanced) R.string.theme_edit_advanced_hide else R.string.theme_edit_advanced_show), null) {
                advanced = !advanced
                render()
            })
            if (advanced) {
                page.content.add(note(getString(R.string.theme_edit_advanced_note)))
                for (s in DERIVED) page.content.add(colorRow(s))
                if (spec.overrides.isNotEmpty()) {
                    page.content.add(buttonRow(getString(R.string.theme_edit_all_automatic), null) { change(spec.clearOverrides()) })
                }
            }
            page.content.add(header(getString(R.string.theme_edit_use_header)))
            val isDay = ThemeStore.dayId(this@ThemeEditActivity) == spec.id
            val isNight = ThemeStore.nightId(this@ThemeEditActivity) == spec.id
            page.content.add(buttonRow(getString(if (isDay) R.string.theme_edit_is_day else R.string.themes_action_day), null) {
                ThemeStore.setDay(this@ThemeEditActivity, spec.id)
                render()
            })
            page.content.add(buttonRow(getString(if (isNight) R.string.theme_edit_is_night else R.string.themes_action_night), null) {
                ThemeStore.setNight(this@ThemeEditActivity, spec.id)
                render()
            })
            page.content.add(buttonRow(getString(R.string.themes_action_delete), null) { confirmDelete() })
        }
    }

    private fun colorRow(slot: Slot): View = ui.row().apply {
        addView(
            ColorDotView(this@ThemeEditActivity).also {
                it.color = spec.color(slot)
                it.outline = ui.secondary.defaultColor
            },
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { rightMargin = ui.dp(16) },
        )
        val auto = !slot.main && !spec.isOverridden(slot)
        val summary = ColorMath.hex(spec.color(slot)) + if (auto) " · " + getString(R.string.theme_automatic) else ""
        addView(ui.texts(getString(slotTitle(slot)), summary), ui.weighted())
        setOnClickListener { pick(slot) }
    }

    private fun pick(slot: Slot) {
        val title = getString(slotTitle(slot))
        val automatic: (() -> Unit)? = if (!slot.main && spec.isOverridden(slot)) ({ change(spec.withAutomatic(slot)) }) else null
        ColorDialog.show(this, title, spec.color(slot), { c -> change(spec.withColor(slot, c)) }, automatic)
    }

    private fun rename() {
        val field = EditText(this).apply {
            setText(spec.name)
            setSelectAllOnFocus(true)
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
            setSingleLine()
        }
        AlertDialog.Builder(this).setTitle(R.string.themes_rename_title).setView(field)
            .setPositiveButton(android.R.string.ok) { _, _ -> change(spec.copy(name = ThemeCodec.cleanName(field.text.toString()))) }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun confirmDelete() {
        AlertDialog.Builder(this).setTitle(getString(R.string.themes_delete_title, spec.name)).setMessage(R.string.themes_delete_message)
            .setPositiveButton(R.string.themes_action_delete) { _, _ ->
                ThemeStore.delete(this, spec.id)
                finish()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun slotTitle(s: Slot): Int = when (s) {
        Slot.BG -> R.string.theme_slot_bg
        Slot.KEY -> R.string.theme_slot_key
        Slot.SPECIAL -> R.string.theme_slot_special
        Slot.ACCENT -> R.string.theme_slot_accent
        Slot.TEXT -> R.string.theme_slot_text
        Slot.KEY_PRESSED -> R.string.theme_slot_key_pressed
        Slot.SPECIAL_PRESSED -> R.string.theme_slot_special_pressed
        Slot.ACCENT_PRESSED -> R.string.theme_slot_accent_pressed
        Slot.TEXT_MUTED -> R.string.theme_slot_text_muted
        Slot.ON_ACCENT -> R.string.theme_slot_on_accent
        Slot.BUBBLE -> R.string.theme_slot_bubble
    }

    private fun warnText(w: Warning): Int = when (w) {
        Warning.LETTERS_ON_KEYS -> R.string.theme_warn_keys
        Warning.LETTERS_ON_SPECIAL -> R.string.theme_warn_special
        Warning.LETTERS_ON_BACKGROUND -> R.string.theme_warn_bg
        Warning.ENTER_LABEL -> R.string.theme_warn_enter
        Warning.KEYS_VS_BACKGROUND -> R.string.theme_warn_flat
    }

    companion object {
        const val EXTRA_ID = "theme_id"
        private const val STATE_ADVANCED = "advanced"
        private val MAIN = listOf(Slot.BG, Slot.KEY, Slot.SPECIAL, Slot.ACCENT, Slot.TEXT)
        private val DERIVED = listOf(Slot.KEY_PRESSED, Slot.SPECIAL_PRESSED, Slot.ACCENT_PRESSED, Slot.TEXT_MUTED, Slot.ON_ACCENT, Slot.BUBBLE)
    }
}
