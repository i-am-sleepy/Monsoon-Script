package com.monsoonscript.keyboard.ime

import android.inputmethodservice.InputMethodService
import android.os.Build
import android.text.InputType
import android.view.KeyEvent
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import com.monsoonscript.keyboard.layout.Layouts
import com.monsoonscript.keyboard.view.KeyboardView

/**
 * The keyboard service. It deliberately does NOT override onKeyDown/onKeyUp, so hardware keys always reach
 * the app untouched (see ARCHITECTURE.md, "Hardware keyboard rules").
 */
class MonsoonIme : InputMethodService(), KeyboardView.Listener {

    private var keyboard: KeyboardView? = null

    override fun onCreateInputView(): View {
        val v = KeyboardView(this)
        v.listener = this
        keyboard = v
        return v
    }

    /** No full-screen extract UI in landscape (Gboard does not either). */
    override fun onEvaluateFullscreenMode(): Boolean = false

    override fun onStartInputView(info: EditorInfo?, restarting: Boolean) {
        super.onStartInputView(info, restarting)
        val v = keyboard ?: return
        val cls = (info?.inputType ?: 0) and InputType.TYPE_MASK_CLASS
        val numeric = cls == InputType.TYPE_CLASS_NUMBER || cls == InputType.TYPE_CLASS_PHONE || cls == InputType.TYPE_CLASS_DATETIME
        v.setPage(if (numeric) Layouts.PAGE_SYMBOLS else Layouts.PAGE_LETTERS)
        refreshAutoCaps()
    }

    override fun onFinishInputView(finishingInput: Boolean) {
        super.onFinishInputView(finishingInput)
        keyboard?.cancelAll()
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

    private fun refreshAutoCaps() {
        val ic = currentInputConnection ?: return
        val ei = currentInputEditorInfo ?: return
        keyboard?.applyAutoCaps(ic.getCursorCapsMode(ei.inputType) != 0)
    }
}
