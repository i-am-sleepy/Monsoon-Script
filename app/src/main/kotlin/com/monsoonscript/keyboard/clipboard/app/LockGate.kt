package com.monsoonscript.keyboard.clipboard.app

import android.app.Activity
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import com.monsoonscript.keyboard.R
import com.monsoonscript.keyboard.clipboard.ClipHub
import com.monsoonscript.keyboard.clipboard.ClipUnlockActivity
import com.monsoonscript.keyboard.settings.SettingsUi

/**
 * The clipboard lock (E3.9, D71) for the app's clipboard screens: wraps the screen's content and covers it with a "locked" page while the lock is on and
 * nobody unlocked. Hand [root] to setContentView and forward onStart / onResume / onStop. While the lock is switched on the window is also protected
 * against screenshots and its preview in the recent apps. The prompt opens once by itself each time the screen is shown; after cancelling, the button on the
 * page opens it again.
 */
internal class LockGate(private val activity: Activity, private val ui: SettingsUi, private val hub: ClipHub, private val content: View) {
    val root = FrameLayout(activity)
    private val overlay = LinearLayout(activity)
    private var autoAsked = false

    init {
        root.addView(content, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        val bg = TypedValue()
        activity.theme.resolveAttribute(android.R.attr.colorBackground, bg, true)
        overlay.apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setBackgroundColor(bg.data)
            isClickable = true // keeps touches away from what is below
            visibility = View.GONE
            setPadding(ui.dp(32), ui.dp(32), ui.dp(32), ui.dp(32))
            addView(TextView(activity).apply {
                text = activity.getString(R.string.clip_locked_title)
                textSize = 22f
                gravity = Gravity.CENTER
                setTextColor(ui.primary)
            })
            addView(TextView(activity).apply {
                text = activity.getString(R.string.clip_locked_hint)
                textSize = 14f
                gravity = Gravity.CENTER
                setTextColor(ui.secondary)
                setPadding(0, ui.dp(8), 0, ui.dp(24))
            })
            addView(TextView(activity).apply {
                text = activity.getString(R.string.clip_unlock_button)
                textSize = 16f
                gravity = Gravity.CENTER
                setTextColor(ui.accent)
                setPadding(ui.dp(24), ui.dp(12), ui.dp(24), ui.dp(12))
                background = ui.ripple()
                setOnClickListener { unlock() }
            })
        }
        root.addView(overlay, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
    }

    fun start() {
        hub.lock.activityStarted()
        autoAsked = false
        refresh(true)
    }

    fun resume() {
        refresh(false)
    }

    fun stop() {
        hub.lock.activityStopped()
    }

    private fun refresh(autoPrompt: Boolean) {
        val flags = WindowManager.LayoutParams.FLAG_SECURE
        if (hub.prefs.lockEnabled) activity.window.addFlags(flags) else activity.window.clearFlags(flags)
        val open = hub.lock.isOpen()
        content.visibility = if (open) View.VISIBLE else View.INVISIBLE
        overlay.visibility = if (open) View.GONE else View.VISIBLE
        if (!open && autoPrompt && !autoAsked) {
            autoAsked = true
            unlock()
        }
    }

    /** Also called by the activity when it changes the lock settings. */
    fun unlock() {
        activity.startActivity(ClipUnlockActivity.intent(activity))
    }
}
