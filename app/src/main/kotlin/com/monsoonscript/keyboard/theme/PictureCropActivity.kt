package com.monsoonscript.keyboard.theme

import android.content.res.Configuration
import android.graphics.Typeface
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import com.monsoonscript.keyboard.R
import com.monsoonscript.keyboard.prefs.Prefs
import com.monsoonscript.keyboard.settings.SettingsUi
import com.monsoonscript.keyboard.settings.ThemedActivity

/**
 * Where the background picture sits in the keyboard (L10 step 2b, D90): a keyboard-shaped frame on the whole picture; drag to move the picture, pinch to zoom, and a
 * switch between the keyboard held upright (portrait) and on its side (landscape), each with its own placement. Every finished gesture is saved at once, so the
 * keyboard and the theme editor's preview follow while this screen is open.
 */
class PictureCropActivity : ThemedActivity() {
    private lateinit var ui: SettingsUi
    private lateinit var spec: ThemeSpec
    private lateinit var stage: CropFrameView
    private lateinit var portraitButton: TextView
    private lateinit var landscapeButton: TextView
    private var landscape = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val found = ThemeStore.find(this, intent.getStringExtra(EXTRA_ID))
        val bitmap = found?.let { ThemePictures.bitmap(this, it) }
        if (found == null || bitmap == null) {
            finish()
            return
        }
        spec = found
        ui = SettingsUi(this)
        // starts with the way the phone is held now
        landscape = savedInstanceState?.getBoolean(STATE_LANDSCAPE)
            ?: (resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE)
        val page = ui.page(getString(R.string.theme_crop_title)) { finish() }
        stage = CropFrameView(this, bitmap, ThemePictures.veil(spec), colors.accent, colors.muted)
        stage.onChanged = { crop -> save(crop) }
        portraitButton = switchButton(getString(R.string.theme_crop_portrait)) { select(false) }
        landscapeButton = switchButton(getString(R.string.theme_crop_landscape)) { select(true) }
        with(ui) {
            page.content.add(note(getString(R.string.theme_crop_note)))
            page.content.add(LinearLayout(this@PictureCropActivity).apply {
                orientation = LinearLayout.HORIZONTAL
                setPadding(dp(16), dp(4), dp(16), dp(10))
                addView(portraitButton, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply { rightMargin = dp(8) })
                addView(landscapeButton, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            })
            page.content.add(stage)
            page.content.add(buttonRow(getString(R.string.theme_crop_reset), null) {
                stage.setCrop(PictureCrop.AUTO)
                save(PictureCrop.AUTO)
            })
        }
        select(landscape)
        setContentView(page.root)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putBoolean(STATE_LANDSCAPE, landscape)
    }

    private fun switchButton(label: String, onClick: () -> Unit): TextView = TextView(this).apply {
        text = label
        textSize = 15f
        typeface = Typeface.DEFAULT_BOLD
        gravity = Gravity.CENTER
        setPadding(ui.dp(12), ui.dp(12), ui.dp(12), ui.dp(12))
        setOnClickListener { onClick() }
    }

    private fun paintSwitch(button: TextView, on: Boolean) {
        val accent = colors.accent
        button.setTextColor(if (on) colors.onAccent else accent)
        button.background = if (on) ui.rounded(accent, 12) else ui.rounded(colors.card, 12, accent)
    }

    /** Show the placement of the portrait or the landscape keyboard. */
    private fun select(toLandscape: Boolean) {
        landscape = toLandscape
        val d = resources.displayMetrics
        val shortDp = minOf(d.widthPixels, d.heightPixels) / d.density
        val longDp = maxOf(d.widthPixels, d.heightPixels) / d.density
        val percent = if (landscape) Prefs.heightLandscape(this) else Prefs.heightPortrait(this)
        stage.aspect = PictureMath.keyboardAspect(landscape, if (landscape) longDp else shortDp, percent, if (Prefs.numberRow(this)) 5 else 4)
        stage.setCrop(if (landscape) spec.cropLandscape else spec.cropPortrait)
        paintSwitch(portraitButton, !landscape)
        paintSwitch(landscapeButton, landscape)
    }

    private fun save(crop: PictureCrop) {
        spec = if (landscape) spec.copy(cropLandscape = crop) else spec.copy(cropPortrait = crop)
        ThemeStore.save(this, spec)
    }

    companion object {
        const val EXTRA_ID = "theme_id"
        private const val STATE_LANDSCAPE = "landscape"
    }
}
