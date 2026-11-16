package com.monsoonscript.keyboard.ime

import android.content.res.Configuration
import android.graphics.drawable.ColorDrawable
import android.inputmethodservice.InputMethodService
import android.os.Build
import android.text.InputType
import android.view.KeyEvent
import android.view.View
import android.view.WindowInsetsController
import android.view.WindowManager
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import com.monsoonscript.keyboard.layout.Layouts
import com.monsoonscript.keyboard.view.KeyboardView

/**
 * The keyboard service.
 *
 * Hardware keyboards: this class deliberately does NOT override onKeyDown/onKeyUp (and there is no fullscreen
 * extract UI), so physical key events always go straight to the app, untouched. Whether the on-screen keyboard shows
 * while a physical keyboard is connected is left to Android's own policy (the system "show on-screen keyboard"
 * setting), exactly like Gboard. See ARCHITECTURE.md, "Hardware keyboard rules".
 */
class MonsoonIme : InputMethodService(), KeyboardView.Listener {

    private var keyboard: KeyboardView? = null

    override fun onCreateInputView(): View {
        val v = KeyboardView(this)
        v.listener = this
        keyboard = v
        applySystemBars()
        return v
    }

    /** No full-screen extract UI in landscape (Gboard does not either). */
    override fun onEvaluateFullscreenMode(): Boolean = false

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        keyboard?.refreshTheme()
        applySystemBars()
    }

    override fun onStartInputView(info: EditorInfo?, restarting: Boolean) {
        super.onStartInputView(info, restarting)
        val v = keyboard ?: return
        val cls = (info?.inputType ?: 0) and InputType.TYPE_MASK_CLASS
        val numeric = cls == InputType.TYPE_CLASS_NUMBER || cls == InputType.TYPE_CLASS_PHONE || cls == InputType.TYPE_CLASS_DATETIME
        v.setPage(if (numeric) Layouts.PAGE_SYMBOLS else Layouts.PAGE_LETTERS)
        applySystemBars()
        refreshAutoCaps()
    }

    override fun onFinishInputView(finishingInput: Boolean) {
        super.onFinishInputView(finishingInput)
        keyboard?.cancelAll()
    }

    /**
     * Paint the IME window and the navigation-bar area with the keyboard color, otherwise Android leaves a transparent
     * strip under the keys (the app behind shows through), and keep the gesture pill readable in light and dark.
     */
    @Suppress("DEPRECATION")
    private fun applySystemBars() {
        val v = keyboard ?: return
        val w = window?.window ?: return
        w.setBackgroundDrawable(ColorDrawable(v.backgroundColor))
        w.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS)
        w.navigationBarColor = v.backgroundColor
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) w.isNavigationBarContrastEnforced = false
        val light = v.isLightTheme
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            w.insetsController?.setSystemBarsAppearance(
                if (light) WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS else 0,
                WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS,
            )
        } else {
            val d = w.decorView
            d.systemUiVisibility = if (light) {
                d.systemUiVisibility or View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR
            } else {
                d.systemUiVisibility and View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR.inv()
            }
        }
    }

    // ---- KeyboardView.Listener

    override fun onText(text: String) {
        currentInputConnection?.commitText(text, 1)
        // getCursorCapsMode is a blocking call into the app: only ask when the text could start a new sentence/word.
        if (text == " " || text == "." || text == "!" || text == "?") refreshAutoCaps()
    }

    override fun onBackspace() {
        sendDownUpKeyEvents(KeyEvent.KEYCODE_DEL)
    }

    override fun onEnter() {
        val options = currentInputEditorInfo?.imeOptions ?: 0
        val action = options and EditorInfo.IME_MASK_ACTION
        val noEnterAction = (options and EditorInfo.IME_FLAG_NO_ENTER_ACTION) != 0
        if (!noEnterAction && action != EditorInfo.IME_ACTION_NONE && action != EditorInfo.IME_ACTION_UNSPECIFIED) {
            currentInputConnection?.performEditorAction(action)
        } else {
            sendDownUpKeyEvents(KeyEvent.KEYCODE_ENTER)
        }
        refreshAutoCaps()
    }

    override fun onTab() {
        sendDownUpKeyEvents(KeyEvent.KEYCODE_TAB)
    }

    /** Tap: jump to the next enabled keyboard (Gboard etc.). Falls back to the picker if Android refuses. */
    @Suppress("DEPRECATION")
    override fun onGlobe() {
        val imm = getSystemService(InputMethodManager::class.java)
        val switched = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            switchToNextInputMethod(false)
        } else {
            imm?.switchToNextInputMethod(window?.window?.attributes?.token, false) ?: false
        }
        if (!switched) imm?.showInputMethodPicker()
    }

    /** Long press: the full keyboard picker. */
    override fun onGlobeLong() {
        getSystemService(InputMethodManager::class.java)?.showInputMethodPicker()
    }

    private fun refreshAutoCaps() {
        val ic = currentInputConnection ?: return
        val ei = currentInputEditorInfo ?: return
        keyboard?.applyAutoCaps(ic.getCursorCapsMode(ei.inputType) != 0)
    }
}
