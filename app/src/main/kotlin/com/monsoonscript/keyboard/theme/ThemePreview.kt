package com.monsoonscript.keyboard.theme

import android.content.Context
import android.view.MotionEvent
import android.view.ViewGroup
import android.widget.FrameLayout
import com.monsoonscript.keyboard.prefs.Prefs
import com.monsoonscript.keyboard.view.KeyboardView

/** A keyboard to look at on the theme screens (L10, D75): the real view with the owner's size settings; the frame takes every touch, so the page still scrolls. */
internal object ThemePreview {
    /** The `Prefs.THEME` value "always the day theme" (the view's own constants are private). */
    const val ALWAYS_DAY = 1

    fun frame(context: Context, kb: KeyboardView): FrameLayout {
        val frame = object : FrameLayout(context) {
            override fun onInterceptTouchEvent(ev: MotionEvent): Boolean = true
        }
        frame.addView(kb, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        return frame
    }

    /** Show [day] and [night]; [mode] is the `Prefs.THEME` value: 0 follow the phone, 1 always the day theme ([ALWAYS_DAY]), 2 always the night theme. */
    fun apply(context: Context, kb: KeyboardView, day: ThemeSpec, night: ThemeSpec, mode: Int = Prefs.theme(context)) {
        kb.setThemes(day.colors().toPalette(), night.colors().toPalette())
        kb.setAppearance(mode, Prefs.numberRow(context), Prefs.emojiKey(context), Prefs.heightPortrait(context), Prefs.heightLandscape(context), Prefs.keyGap(context))
    }
}
