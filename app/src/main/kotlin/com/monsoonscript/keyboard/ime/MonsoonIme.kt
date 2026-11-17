package com.monsoonscript.keyboard.ime

import android.content.res.Configuration
import android.graphics.drawable.ColorDrawable
import android.inputmethodservice.InputMethodService
import android.os.Build
import android.os.SystemClock
import android.text.InputType
import android.view.KeyEvent
import android.view.View
import android.view.WindowInsetsController
import android.view.WindowManager
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputConnection
import android.view.inputmethod.InputMethodManager
import com.monsoonscript.keyboard.lang.Language
import com.monsoonscript.keyboard.lang.PhoneticComposer
import com.monsoonscript.keyboard.lang.avro.AvroAutoCorrect
import com.monsoonscript.keyboard.lang.avro.AvroPhoneticConverter
import com.monsoonscript.keyboard.layout.Layouts
import com.monsoonscript.keyboard.view.KeyboardView
import java.io.IOException

/**
 * The keyboard service.
 *
 * Hardware keyboards: this class deliberately does NOT override onKeyDown/onKeyUp (and there is no fullscreen
 * extract UI), so physical key events always go straight to the app, untouched. Whether the on-screen keyboard shows
 * while a physical keyboard is connected is left to Android's own policy (the system "show on-screen keyboard"
 * setting), exactly like Gboard. See ARCHITECTURE.md, "Hardware keyboard rules".
 *
 * Avro Phonetic: letters are collected by a [PhoneticComposer] and shown as live, underlined Bangla composing text.
 * Anything that is not part of a word (space, digits, punctuation, Enter, Tab, switching keyboard) first finishes the
 * word, so the text in the editor is always exactly what the user sees.
 */
class MonsoonIme : InputMethodService(), KeyboardView.Listener {

    private var keyboard: KeyboardView? = null

    private var language = Language.ENGLISH
    private var lastBangla = Language.AVRO

    /** Created on first use of Avro (loads the autocorrect dictionary from assets, a few ms). */
    private var composer: PhoneticComposer? = null

    /** Direct conversion without autocorrect: the reference the suggestion search ranks against. Created with [composer]. */
    private var plainConverter: AvroPhoneticConverter? = null

    /** Background dictionary search, started on the first Bangla word. */
    private var worker: SuggestionWorker? = null

    /** Strip candidates for the word being typed: the word as shown, the plain conversion, then dictionary words (D23). */
    private var suggestions: List<String> = emptyList()

    /** Password, URL and e-mail fields get the typed letters as they are, even in a Bangla mode. */
    private var composingAllowed = true
    private var lastEditAt = 0L

    private val avroActive: Boolean get() = language == Language.AVRO && composingAllowed

    override fun onCreateInputView(): View {
        val v = KeyboardView(this)
        v.listener = this
        v.setLanguage(language.shortLabel, language.spaceLabel)
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
        composer?.clear()
        dropSuggestions()
        val inputType = info?.inputType ?: 0
        val cls = inputType and InputType.TYPE_MASK_CLASS
        composingAllowed = info != null && cls == InputType.TYPE_CLASS_TEXT && !isRawTextVariation(inputType)
        val v = keyboard ?: return
        val numeric = cls == InputType.TYPE_CLASS_NUMBER || cls == InputType.TYPE_CLASS_PHONE || cls == InputType.TYPE_CLASS_DATETIME
        v.setPage(if (numeric) Layouts.PAGE_SYMBOLS else Layouts.PAGE_LETTERS)
        v.setLanguage(language.shortLabel, language.spaceLabel)
        applySystemBars()
        refreshAutoCaps()
    }

    override fun onFinishInputView(finishingInput: Boolean) {
        super.onFinishInputView(finishingInput)
        currentInputConnection?.finishComposingText()
        composer?.clear()
        dropSuggestions()
        keyboard?.cancelAll()
    }

    override fun onDestroy() {
        worker?.stop()
        worker = null
        super.onDestroy()
    }

    /** The user moved the cursor or the app dropped the composing region: forget the word we were building. */
    override fun onUpdateSelection(
        oldSelStart: Int, oldSelEnd: Int, newSelStart: Int, newSelEnd: Int, candidatesStart: Int, candidatesEnd: Int,
    ) {
        super.onUpdateSelection(oldSelStart, oldSelEnd, newSelStart, newSelEnd, candidatesStart, candidatesEnd)
        val comp = composer ?: return
        if (!comp.isComposing) return
        val sinceEdit = SystemClock.uptimeMillis() - lastEditAt
        if (PhoneticComposer.externalMove(newSelStart, newSelEnd, candidatesStart, candidatesEnd, sinceEdit)) {
            comp.clear()
            dropSuggestions()
            currentInputConnection?.finishComposingText() // leave the old word as plain text
        }
    }

    private fun isRawTextVariation(inputType: Int): Boolean {
        return when (inputType and InputType.TYPE_MASK_VARIATION) {
            InputType.TYPE_TEXT_VARIATION_PASSWORD,
            InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD,
            InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD,
            InputType.TYPE_TEXT_VARIATION_URI,
            InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS,
            InputType.TYPE_TEXT_VARIATION_WEB_EMAIL_ADDRESS -> true
            else -> false
        }
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

    // ---- composing (Avro Phonetic)

    private fun ensureComposer(): PhoneticComposer {
        composer?.let { return it }
        val dict: Map<String, String> = try {
            assets.open("avro/autodict.dct").use { AvroAutoCorrect.load(it) }
        } catch (e: IOException) {
            emptyMap() // no autocorrect, phonetic typing still works
        }
        val c = PhoneticComposer(AvroPhoneticConverter(dict))
        composer = c
        plainConverter = AvroPhoneticConverter().also { it.autoCorrectEnabled = false }
        return c
    }

    private fun showComposing(ic: InputConnection, comp: PhoneticComposer) {
        val shown = comp.text()
        ic.setComposingText(shown, 1)
        lastEditAt = SystemClock.uptimeMillis()
        requestSuggestions(comp.rawText(), shown)
    }

    // ---- suggestions (T8b; the strip that displays them is T8b.4)

    /** Update the first candidate at once (it is the live word), then ask the worker for dictionary words. */
    private fun requestSuggestions(raw: String, shown: String) {
        val head = if (suggestions.isEmpty()) arrayListOf(shown) else ArrayList(suggestions).also { it[0] = shown }
        setSuggestions(head) // later entries stay until the answer arrives, so the strip does not blink
        val plain = plainConverter?.convert(raw) ?: shown
        val w = worker ?: SuggestionWorker(assets, ::onSuggestionResult).also { worker = it }
        w.request(raw, plain)
    }

    private fun onSuggestionResult(r: SuggestionWorker.Result) {
        val comp = composer ?: return
        if (!comp.isComposing || comp.rawText() != r.english) return // the word has moved on since the request
        val shown = comp.text()
        val plain = plainConverter?.convert(r.english)
        val out = ArrayList<String>(r.words.size + 2)
        out.add(shown)
        if (plain != null && plain != shown) out.add(plain)
        for (w in r.words) if (w != shown && w != plain) out.add(w)
        setSuggestions(out)
    }

    /** The word ended (or was abandoned): no candidates and no pending search. */
    private fun dropSuggestions() {
        worker?.cancel()
        if (suggestions.isNotEmpty()) setSuggestions(emptyList())
    }

    private fun setSuggestions(list: List<String>) {
        suggestions = list
    }

    /** What is shown is already the final Bangla text, so finishing just makes it normal (not underlined) text. */
    private fun commitComposingIfAny() {
        val comp = composer ?: return
        if (!comp.isComposing) return
        currentInputConnection?.finishComposingText()
        comp.clear()
        dropSuggestions()
        lastEditAt = SystemClock.uptimeMillis()
    }

    private fun setLanguage(l: Language) {
        language = l
        if (l.isBangla) lastBangla = l
        keyboard?.setLanguage(l.shortLabel, l.spaceLabel)
        refreshAutoCaps()
    }

    // ---- KeyboardView.Listener

    override fun onText(text: String) {
        val ic = currentInputConnection ?: return
        if (avroActive) {
            val comp = ensureComposer()
            if (text.length == 1 && comp.accepts(text[0])) {
                comp.append(text[0])
                showComposing(ic, comp)
                return
            }
            if (comp.isComposing) {
                commitComposingIfAny()
                if (text == ".") { // a full stop right after a Bangla word is the dari
                    ic.commitText(DARI, 1)
                    return
                }
            }
        }
        ic.commitText(text, 1)
        // getCursorCapsMode is a blocking call into the app: only ask when the text could start a new sentence/word.
        if (text == " " || text == "." || text == "!" || text == "?") refreshAutoCaps()
    }

    override fun onBackspace() {
        val comp = composer
        val ic = currentInputConnection
        if (avroActive && comp != null && comp.isComposing && ic != null) {
            comp.deleteLast()
            if (comp.isComposing) {
                showComposing(ic, comp)
            } else {
                ic.commitText("", 1)
                dropSuggestions()
            }
            lastEditAt = SystemClock.uptimeMillis()
            return
        }
        sendDownUpKeyEvents(KeyEvent.KEYCODE_DEL)
    }

    override fun onEnter() {
        commitComposingIfAny()
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
        commitComposingIfAny()
        sendDownUpKeyEvents(KeyEvent.KEYCODE_TAB)
    }

    /** Tap: jump to the next enabled keyboard (Gboard etc.). Falls back to the picker if Android refuses. */
    @Suppress("DEPRECATION")
    override fun onGlobe() {
        commitComposingIfAny()
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
        commitComposingIfAny()
        getSystemService(InputMethodManager::class.java)?.showInputMethodPicker()
    }

    /** Language key / space-bar swipe: English <-> the Bangla layout used last (D19). */
    override fun onLanguageToggle() {
        commitComposingIfAny()
        setLanguage(if (language.isBangla) Language.ENGLISH else lastBangla)
    }

    private fun refreshAutoCaps() {
        // Capitals mean different letters in Avro Phonetic (Dhaka vs dhaka), so never auto-capitalize in Bangla.
        if (language.isBangla) {
            keyboard?.applyAutoCaps(false)
            return
        }
        val ic = currentInputConnection ?: return
        val ei = currentInputEditorInfo ?: return
        keyboard?.applyAutoCaps(ic.getCursorCapsMode(ei.inputType) != 0)
    }

    private companion object {
        const val DARI = "।"
    }
}
