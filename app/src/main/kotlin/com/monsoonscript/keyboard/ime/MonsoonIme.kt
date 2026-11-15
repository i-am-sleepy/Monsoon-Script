package com.monsoonscript.keyboard.ime

import android.graphics.Color
import android.inputmethodservice.InputMethodService
import android.view.Gravity
import android.view.View
import android.widget.TextView
import com.monsoonscript.keyboard.R

/**
 * Skeleton IME. It shows a placeholder panel only.
 * It deliberately does NOT override onKeyDown/onKeyUp, so hardware keys always reach the app untouched
 * (see ARCHITECTURE.md, "Hardware keyboard rules").
 */
class MonsoonIme : InputMethodService() {

    override fun onCreateInputView(): View =
        TextView(this).apply {
            setText(R.string.ime_placeholder)
            gravity = Gravity.CENTER
            setTextColor(Color.WHITE)
            setBackgroundColor(Color.parseColor("#1F1F1F"))
            minHeight = (resources.displayMetrics.density * 220).toInt()
        }
}
