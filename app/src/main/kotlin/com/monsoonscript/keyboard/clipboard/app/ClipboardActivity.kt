package com.monsoonscript.keyboard.clipboard.app

import android.app.Activity
import android.content.Intent
import android.graphics.Typeface
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import com.monsoonscript.keyboard.R
import com.monsoonscript.keyboard.clipboard.ClipHub
import com.monsoonscript.keyboard.clipboard.ClipListModel
import com.monsoonscript.keyboard.settings.SettingsUi

/**
 * The clipboard in the app (E3.6, D67): a page with four bottom tabs, History / Pinned / Folders / Shortcuts, and a gear that opens the clipboard settings.
 * It is opened from the "Clipboard" row on the main screen. Each tab is a [Screen] built the first time it is shown; only the visible one listens to the store.
 */
class ClipboardActivity : Activity() {

    private val screens = arrayOfNulls<Screen>(TAB_COUNT)
    private val tabs = ArrayList<TextView>()
    private var current = 0
    private var started = false
    private lateinit var ui: SettingsUi
    private lateinit var hub: ClipHub
    private lateinit var actions: ClipActions
    private lateinit var content: FrameLayout
    private lateinit var tabBar: LinearLayout
    private lateinit var gate: LockGate

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        hub = ClipHub.get(this)
        hub.start()
        ui = SettingsUi(this)
        actions = ClipActions(this, hub.store)

        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        root.addView(topBar(), LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        content = FrameLayout(this)
        root.addView(content, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        root.addView(View(this).apply { setBackgroundColor(ui.secondary.defaultColor and 0x00FFFFFF or 0x33000000) },
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 1))
        tabBar = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        val names = intArrayOf(R.string.clip_tab_history, R.string.clip_tab_pinned, R.string.clip_tab_folders, R.string.clip_tab_shortcuts)
        for (i in names.indices) {
            val t = TextView(this).apply {
                text = getString(names[i])
                textSize = 14f
                gravity = Gravity.CENTER
                setPadding(0, ui.dp(14), 0, ui.dp(14))
                background = ui.ripple()
                setOnClickListener { select(i) }
            }
            tabs.add(t)
            tabBar.addView(t, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        }
        root.addView(tabBar, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        gate = LockGate(this, ui, hub, root) // the biometric lock (D71)
        setContentView(gate.root)
        select(savedInstanceState?.getInt(KEY_TAB, 0)?.coerceIn(0, TAB_COUNT - 1) ?: 0)
    }

    private fun topBar(): View = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        setPadding(ui.dp(8), ui.dp(16), ui.dp(8), ui.dp(8))
        addView(TextView(this@ClipboardActivity).apply {
            text = "‹"
            textSize = 34f
            setTextColor(ui.primary)
            setPadding(ui.dp(12), 0, ui.dp(16), ui.dp(4))
            contentDescription = getString(R.string.settings_back)
            background = ui.ripple()
            setOnClickListener { finish() }
        })
        addView(TextView(this@ClipboardActivity).apply {
            text = getString(R.string.clip_title)
            textSize = 26f
            setTextColor(ui.primary)
        }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        addView(GearView(this@ClipboardActivity, ui.primary.defaultColor).apply {
            contentDescription = getString(R.string.clip_settings_button)
            background = ui.ripple()
            setOnClickListener { startActivity(Intent(this@ClipboardActivity, ClipboardSettingsActivity::class.java)) }
        }, LinearLayout.LayoutParams(ui.dp(48), ui.dp(48)))
    }

    private fun screenFor(i: Int): Screen = screens[i] ?: when (i) {
        0 -> ClipListScreen(this, ui, hub, actions, ClipListModel.Scope.HISTORY)
        1 -> ClipListScreen(this, ui, hub, actions, ClipListModel.Scope.PINNED)
        2 -> FolderScreen(this, ui, hub, actions)
        else -> ShortcutScreen(this, ui, hub, actions)
    }.also { screen ->
        screen.onSelecting = { selecting -> if (screens[current] === screen) tabBar.visibility = if (selecting) View.GONE else View.VISIBLE }
        screens[i] = screen
    }

    private fun select(i: Int) {
        if (started) screens[current]?.hide()
        content.removeAllViews()
        current = i
        val screen = screenFor(i)
        content.addView(screen.view, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        tabBar.visibility = View.VISIBLE
        for (k in tabs.indices) {
            tabs[k].setTextColor(if (k == i) ui.accent else ui.secondary.defaultColor)
            tabs[k].typeface = if (k == i) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
        }
        if (started) screen.show()
    }

    override fun onStart() {
        super.onStart()
        gate.start()
        started = true
        screens[current]?.show()
    }

    override fun onResume() {
        super.onResume()
        gate.resume()
    }

    override fun onStop() {
        screens[current]?.hide()
        started = false
        gate.stop()
        super.onStop()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putInt(KEY_TAB, current)
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        if (screens[current]?.back() == true) return
        @Suppress("DEPRECATION")
        super.onBackPressed()
    }

    private companion object {
        const val TAB_COUNT = 4
        const val KEY_TAB = "tab"
    }
}
