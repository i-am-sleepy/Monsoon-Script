package com.monsoonscript.keyboard.theme

import android.app.AlertDialog
import android.content.ActivityNotFoundException
import android.content.Intent
import android.widget.Toast
import android.os.Bundle
import android.text.InputType
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.LinearLayout
import com.monsoonscript.keyboard.R
import com.monsoonscript.keyboard.settings.BubbleHost
import com.monsoonscript.keyboard.settings.SettingsUi
import com.monsoonscript.keyboard.view.KeyboardView
import com.monsoonscript.keyboard.settings.ThemedActivity

/**
 * The editor of one custom theme (L10, D75): a live preview, the name, the five main colors, "advanced colors" (the six that follow by themselves unless set), and
 * what to use it for. Every change is saved at once, so the keyboard shows it too. Notes warn about colors that are hard to read together; nothing is blocked.
 */
class ThemeEditActivity : ThemedActivity() {
    private lateinit var ui: SettingsUi
    private lateinit var page: SettingsUi.Page
    private lateinit var spec: ThemeSpec
    private lateinit var kb: KeyboardView
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
        kb = KeyboardView(this)
        val host = BubbleHost(this, ui, page.root) // two bubbles (L12, D83/D85/D86): "Try it" = the real keyboard, palette = a look-only preview of THIS theme docked at the bottom
        host.addTryIt()
        host.addPreview(kb, 1)
        if (savedInstanceState == null) host.setDock(true) // opening the editor shows the preview at once; a rebuild keeps what the person chose
        host.restoreState(savedInstanceState)
        bubbles = host
        setContentView(host.root)
        showPreview()
        render()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putBoolean(STATE_ADVANCED, advanced)
        bubbles?.saveState(outState)
    }

    override fun onResume() {
        super.onResume()
        // the crop screen saves its own changes: take them over, so the next change here does not undo them
        val fresh = ThemeStore.find(this, spec.id)
        if (fresh != null && (fresh.cropPortrait != spec.cropPortrait || fresh.cropLandscape != spec.cropLandscape)) {
            spec = spec.copy(cropPortrait = fresh.cropPortrait, cropLandscape = fresh.cropLandscape)
            showPreview()
        }
    }

    private fun showPreview() {
        // the theme being edited is shown whatever the phone is doing now
        ThemePreview.apply(this, kb, spec, spec, ThemePreview.ALWAYS_DAY)
    }

    private fun change(newSpec: ThemeSpec) {
        spec = newSpec
        ThemeStore.save(this, spec)
        showPreview()
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
            addShapeSection()
            addPictureSection()
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
            page.content.add(buttonRow(getString(R.string.theme_share_row), getString(if (spec.hasPicture) R.string.theme_share_row_picture else R.string.theme_share_row_summary)) {
                ThemeSharing.send(this@ThemeEditActivity, spec)
            })
            page.content.add(buttonRow(getString(R.string.themes_action_delete), null) { confirmDelete() })
        }
    }

    // ---- the key shape (L10 step 4, D96)

    private fun SettingsUi.addShapeSection() {
        page.content.add(header(getString(R.string.theme_shape_header)))
        page.content.add(valueSliderRow(getString(R.string.theme_shape_round), 0, 100, spec.keyRoundness, { v: Int -> "$v %" }) { v -> tweak(spec.copy(keyRoundness = v)) })
        page.content.add(valueSwitchRow(getString(R.string.theme_shape_border), getString(R.string.theme_shape_border_summary), spec.keyBorder) { on -> tweak(spec.copy(keyBorder = on)) })
        page.content.add(valueSwitchRow(getString(R.string.theme_shape_shadow), getString(R.string.theme_shape_shadow_summary), spec.keyShadow) { on -> tweak(spec.copy(keyShadow = on)) })
    }

    // ---- the background picture (L10 step 2, D87)

    private fun SettingsUi.addPictureSection() {
        page.content.add(header(getString(R.string.theme_pic_header)))
        page.content.add(buttonRow(getString(if (spec.hasPicture) R.string.theme_pic_change else R.string.theme_pic_choose), null) { choosePicture() })
        if (spec.hasPicture) {
            val percent = { v: Int -> "$v %" }
            page.content.add(navRow(getString(R.string.theme_crop_row), getString(R.string.theme_crop_row_summary)) {
                startActivity(Intent(this@ThemeEditActivity, PictureCropActivity::class.java).putExtra(PictureCropActivity.EXTRA_ID, spec.id))
            })
            page.content.add(valueSliderRow(getString(R.string.theme_pic_fade), 0, ThemeSpec.MAX_FADE, spec.pictureFade, percent) { v -> tweak(spec.copy(pictureFade = v)) })
            page.content.add(valueSliderRow(getString(R.string.theme_pic_keys), ThemeSpec.MIN_KEY_OPACITY, 100, spec.keyOpacity, percent) { v -> tweak(spec.copy(keyOpacity = v)) })
            page.content.add(buttonRow(getString(R.string.theme_pic_remove), null) {
                ThemePictures.delete(this@ThemeEditActivity, spec.picture)
                change(spec.copy(picture = null))
            })
        }
        page.content.add(note(getString(R.string.theme_pic_note)))
    }

    /** A change while a slider is being dragged: saved and shown, but the page is NOT rebuilt (that would drop the slider from under the finger). */
    private fun tweak(newSpec: ThemeSpec) {
        spec = newSpec
        ThemeStore.save(this, spec)
        showPreview()
    }

    private fun choosePicture() {
        val pick = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "image/*"
        }
        try {
            startActivityForResult(pick, REQ_PICTURE)
        } catch (e: ActivityNotFoundException) {
            Toast.makeText(this, R.string.theme_pic_failed, Toast.LENGTH_LONG).show()
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        val uri = data?.data
        if (requestCode != REQ_PICTURE || resultCode != RESULT_OK || uri == null) return
        val app = applicationContext
        val id = spec.id
        Toast.makeText(this, R.string.theme_pic_working, Toast.LENGTH_SHORT).show()
        Thread {
            val name = ThemePictures.importFrom(app, uri)
            runOnUiThread {
                if (name == null) {
                    Toast.makeText(app, R.string.theme_pic_failed, Toast.LENGTH_LONG).show()
                    return@runOnUiThread
                }
                // saved through the store (not only into this screen) so the picture is not lost when the screen was rebuilt in the meantime
                val current = ThemeStore.find(app, id)
                if (current == null) {
                    ThemePictures.delete(app, name)
                    return@runOnUiThread
                }
                ThemePictures.delete(app, current.picture)
                val updated = current.copy(picture = name)
                ThemeStore.save(app, updated)
                if (!isDestroyed) {
                    spec = updated
                    showPreview()
                    render()
                }
            }
        }.start()
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
        private const val REQ_PICTURE = 71
        private val MAIN = listOf(Slot.BG, Slot.KEY, Slot.SPECIAL, Slot.ACCENT, Slot.TEXT)
        private val DERIVED = listOf(Slot.KEY_PRESSED, Slot.SPECIAL_PRESSED, Slot.ACCENT_PRESSED, Slot.TEXT_MUTED, Slot.ON_ACCENT, Slot.BUBBLE)
    }
}
