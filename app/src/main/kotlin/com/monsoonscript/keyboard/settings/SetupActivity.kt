package com.monsoonscript.keyboard.settings

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.animation.AccelerateDecelerateInterpolator
import android.view.inputmethod.InputMethodManager
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import com.monsoonscript.keyboard.R
import com.monsoonscript.keyboard.clipboard.app.ClipboardActivity
import com.monsoonscript.keyboard.vault.VaultMainActivity
import com.monsoonscript.keyboard.theme.ThemesActivity
import com.monsoonscript.keyboard.voice.VoiceSettingsActivity

/**
 * The main screen (and the keyboard's settings entry), L12 / D83: the logo and name on top, a highlighted setup card that follows the three states (not enabled,
 * enabled but not chosen, active), then the settings pages as list rows with icons in grouped cards (Typing, Suggestions, Appearance, Clipboard (E3.6: its own
 * page with tabs, [ClipboardActivity]), Voice typing, About). Each row opens [SettingsPageActivity] or its own screen. On the first start of the app the logo is
 * shown in the middle of an empty screen for about a second, then flies to its place in the header while the cards slide in.
 */
class SetupActivity : ThemedActivity() {

    private lateinit var ui: SettingsUi
    private lateinit var page: SettingsUi.Page
    private lateinit var setupBox: LinearLayout
    private lateinit var setupCard: View
    private var introOverlay: FrameLayout? = null
    private var bubbles: BubbleHost? = null
    private lateinit var results: SettingsUi.GroupedColumn
    private var menuViews: List<View> = emptyList()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ui = SettingsUi(this)
        page = ui.page(getString(R.string.app_name), logo = true, onBack = null)
        setupBox = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(ui.dp(20), ui.dp(20), ui.dp(20), ui.dp(20))
        }
        results = SettingsUi.GroupedColumn(ui).apply { orientation = LinearLayout.VERTICAL; visibility = View.GONE }
        with(ui) {
            (page.content as SettingsUi.GroupedColumn).addCard(searchField(getString(R.string.search_hint)) { search(it) }, colors.card, 12)
            page.content.add(results)
            val menuStart = page.content.childCount
            (page.content as SettingsUi.GroupedColumn).addCard(setupBox, colors.highlight, 4)
            setupCard = setupBox.parent as View
            page.content.add(header(getString(R.string.settings_header)))
            page.content.add(navRow(getString(R.string.settings_typing), getString(R.string.settings_typing_summary), Icons.Kind.KEYBOARD) { open(SettingsPageActivity.TYPING) })
            page.content.add(navRow(getString(R.string.settings_suggestions), getString(R.string.settings_suggestions_summary), Icons.Kind.SPARKLE) { open(SettingsPageActivity.SUGGESTIONS) })
            page.content.add(navRow(getString(R.string.settings_appearance), getString(R.string.settings_appearance_summary), Icons.Kind.DROP) { open(SettingsPageActivity.APPEARANCE) })
            page.content.add(gap())
            page.content.add(navRow(getString(R.string.clip_title), getString(R.string.clip_row_summary), Icons.Kind.CLIPBOARD) { startActivity(Intent(this@SetupActivity, ClipboardActivity::class.java)) })
            page.content.add(navRow(getString(R.string.vault_title), getString(R.string.vault_row_summary), Icons.Kind.LOCK) { startActivity(Intent(this@SetupActivity, VaultMainActivity::class.java)) })
            page.content.add(navRow(getString(R.string.voice_settings_title), getString(R.string.voice_settings_summary), Icons.Kind.MIC) { startActivity(Intent(this@SetupActivity, VoiceSettingsActivity::class.java)) })
            page.content.add(gap())
            page.content.add(navRow(getString(R.string.settings_about), getString(R.string.settings_about_summary), Icons.Kind.INFO) { open(SettingsPageActivity.ABOUT) })
            menuViews = (menuStart until page.content.childCount).map { page.content.getChildAt(it) }
        }
        renderSetup()
        val root = FrameLayout(this)
        val host = BubbleHost(this, ui, page.root)
        host.addTryIt(0)
        host.restoreState(savedInstanceState)
        bubbles = host
        root.addView(host.root, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        val first = savedInstanceState == null
        if (first && !introPlayed) {
            introPlayed = true
            startIntro(root)
        } else if (first) {
            ui.animateIn(page)
        }
        setContentView(root)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        bubbles?.saveState(outState)
    }

    private fun open(page: String) = startActivity(SettingsPageActivity.intent(this, page))

    // ---- the search box: while there is text, the results replace the setup card and the menu

    private fun search(query: String) {
        results.removeAllViews()
        if (SettingsSearch.words(query).isEmpty()) {
            results.visibility = View.GONE
            for (v in menuViews) v.visibility = View.VISIBLE
            return
        }
        for (v in menuViews) v.visibility = View.GONE
        results.visibility = View.VISIBLE
        val hits = SettingsIndex.ALL.filter { e ->
            SettingsSearch.matches(query, getString(e.title), if (e.summary != 0) getString(e.summary) else "", getString(SettingsIndex.pageName(e.page)))
        }.take(MAX_HITS)
        with(ui) {
            if (hits.isEmpty()) results.add(note(getString(R.string.search_none)))
            for (e in hits) {
                results.add(navRow(getString(e.title), getString(R.string.search_in, getString(SettingsIndex.pageName(e.page))), e.icon) { openHit(e) })
            }
        }
    }

    private fun openHit(e: SettingsIndex.Entry) {
        when (e.page) {
            SettingsIndex.THEMES -> startActivity(Intent(this, ThemesActivity::class.java))
            SettingsIndex.CLIPBOARD -> startActivity(Intent(this, ClipboardActivity::class.java))
            SettingsIndex.VOICE -> startActivity(Intent(this, VoiceSettingsActivity::class.java))
            SettingsIndex.VAULT -> startActivity(Intent(this, VaultMainActivity::class.java))
            else -> startActivity(SettingsPageActivity.intent(this, e.page, getString(e.title)))
        }
    }

    override fun onResume() {
        super.onResume()
        renderSetup()
    }

    // ---- the setup card

    private fun renderSetup() {
        val state = when {
            !isEnabled() -> 0
            !isSelected() -> 1
            else -> 2
        }
        setupBox.removeAllViews()
        setupCard.background = ui.rounded(if (state == 2) colors.card else colors.highlight, 16, colors.cardStroke)
        val title = getString(when (state) { 0 -> R.string.setup_card_off; 1 -> R.string.setup_card_enabled; else -> R.string.setup_card_active })
        val body = getString(when (state) { 0 -> R.string.setup_status_off; 1 -> R.string.setup_status_enabled; else -> R.string.setup_status_active })
        setupBox.addView(LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            addView(ui.iconChip(if (state == 2) Icons.Kind.CHECK else Icons.Kind.KEYBOARD, 48), LinearLayout.LayoutParams(ui.dp(48), ui.dp(48)).apply { rightMargin = ui.dp(16) })
            addView(ui.texts(title, body), ui.weighted())
        })
        val button: View? = when (state) {
            0 -> ui.filledButton(getString(R.string.setup_enable)) { startActivity(Intent(Settings.ACTION_INPUT_METHOD_SETTINGS)) }
            1 -> ui.filledButton(getString(R.string.setup_choose)) { imm().showInputMethodPicker() }
            else -> ui.outlineButton(getString(R.string.setup_change)) { imm().showInputMethodPicker() }
        }
        if (button != null) setupBox.addView(button, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = ui.dp(16) })
    }

    private fun imm(): InputMethodManager = getSystemService(InputMethodManager::class.java)

    private fun isEnabled(): Boolean = imm().enabledInputMethodList.any { it.packageName == packageName }

    private fun isSelected(): Boolean {
        val id = Settings.Secure.getString(contentResolver, Settings.Secure.DEFAULT_INPUT_METHOD) ?: return false
        return id.startsWith("$packageName/")
    }

    // ---- the start screen: the logo appears in the middle, rests about a second, then flies to the header while the cards slide in

    private fun startIntro(root: FrameLayout) {
        val cover = View(this).apply { setBackgroundColor(colors.bg) }
        val big = ui.dp(190)
        val logo = ImageView(this).apply {
            setImageResource(R.drawable.logo_art)
            setColorFilter(colors.text)
            alpha = 0f
            scaleX = 0.9f
            scaleY = 0.9f
        }
        val overlay = FrameLayout(this)
        overlay.addView(cover, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        overlay.addView(logo, FrameLayout.LayoutParams(big, big, Gravity.CENTER))
        root.addView(overlay, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        introOverlay = overlay
        page.logo?.visibility = View.INVISIBLE
        for (i in 0 until page.content.childCount) page.content.getChildAt(i).alpha = 0f
        page.bar?.alpha = 0f
        logo.animate().alpha(1f).scaleX(1f).scaleY(1f).setDuration(350).setInterpolator(AccelerateDecelerateInterpolator()).start()
        overlay.postDelayed({ flyToHeader(root, overlay, cover, logo) }, INTRO_HOLD_MS)
        overlay.postDelayed({ endIntro(root) }, INTRO_HOLD_MS + 2000) // safety net: whatever happened, the screen ends up complete
    }

    private fun flyToHeader(root: FrameLayout, overlay: FrameLayout, cover: View, logo: ImageView) {
        try {
            val target = page.logo
            if (target == null || logo.width == 0 || target.width == 0) {
                endIntro(root)
                return
            }
            val from = IntArray(2)
            val to = IntArray(2)
            logo.getLocationOnScreen(from)
            target.getLocationOnScreen(to)
            val dx = (to[0] + target.width / 2f) - (from[0] + logo.width / 2f)
            val dy = (to[1] + target.height / 2f) - (from[1] + logo.height / 2f)
            val scale = target.width.toFloat() / logo.width
            page.bar?.alpha = 1f
            ui.animateIn(page, 120)
            cover.animate().alpha(0f).setStartDelay(120).setDuration(480).start()
            logo.animate().translationX(dx).translationY(dy).scaleX(scale).scaleY(scale).setDuration(620)
                .setInterpolator(AccelerateDecelerateInterpolator()).withEndAction { endIntro(root) }.start()
        } catch (e: RuntimeException) {
            endIntro(root)
        }
    }

    private fun endIntro(root: FrameLayout) {
        val overlay = introOverlay ?: return
        introOverlay = null
        page.logo?.visibility = View.VISIBLE
        page.bar?.alpha = 1f
        ui.settle(page)
        root.removeView(overlay)
    }

    private companion object {
        /** True once the start screen played in this run of the app (it plays on a cold start only). */
        var introPlayed = false
        const val INTRO_HOLD_MS = 1000L
        const val MAX_HITS = 12
    }
}
