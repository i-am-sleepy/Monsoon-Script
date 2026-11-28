package com.monsoonscript.keyboard.ime

import android.content.ClipDescription
import android.content.Intent
import android.content.SharedPreferences
import android.content.res.Configuration
import android.graphics.drawable.ColorDrawable
import android.graphics.Color
import android.inputmethodservice.InputMethodService
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.os.Bundle
import android.text.InputType
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowInsetsController
import android.view.WindowManager
import android.view.Gravity
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputConnection
import android.view.inputmethod.InputContentInfo
import android.view.inputmethod.InputMethod
import android.view.inputmethod.InputMethodManager
import android.view.inputmethod.InlineSuggestionsRequest
import android.view.inputmethod.InlineSuggestionsResponse
import android.widget.LinearLayout
import android.widget.Toast
import android.widget.FrameLayout
import com.monsoonscript.keyboard.R
import com.monsoonscript.keyboard.calc.CalcEngine
import com.monsoonscript.keyboard.calc.CalcOffer
import com.monsoonscript.keyboard.calc.CalcRun
import com.monsoonscript.keyboard.clipboard.ClipCapture
import com.monsoonscript.keyboard.clipboard.ClipEditActivity
import com.monsoonscript.keyboard.clipboard.ClipFormat
import com.monsoonscript.keyboard.clipboard.ClipHub
import com.monsoonscript.keyboard.clipboard.ClipUnlockActivity
import com.monsoonscript.keyboard.clipboard.ClipItem
import com.monsoonscript.keyboard.clipboard.ImageMime
import com.monsoonscript.keyboard.clipboard.ImageShare
import com.monsoonscript.keyboard.clipboard.ClipPanelModel
import com.monsoonscript.keyboard.clipboard.ClipShortcut
import com.monsoonscript.keyboard.clipboard.TriggerRun
import com.monsoonscript.keyboard.clipboard.app.ClipboardSettingsActivity
import com.monsoonscript.keyboard.emoji.EmojiLoader
import com.monsoonscript.keyboard.emoji.EmojiStore
import com.monsoonscript.keyboard.emoji.PrefsTextStore
import com.monsoonscript.keyboard.lang.Language
import com.monsoonscript.keyboard.lang.PhoneticComposer
import com.monsoonscript.keyboard.lang.avro.AvroAutoCorrect
import com.monsoonscript.keyboard.lang.avro.AvroPhoneticConverter
import com.monsoonscript.keyboard.layout.Layouts
import com.monsoonscript.keyboard.predict.TextNorm
import com.monsoonscript.keyboard.predict.WordTracker
import com.monsoonscript.keyboard.prefs.Prefs
import com.monsoonscript.keyboard.view.ClipboardPanelView
import com.monsoonscript.keyboard.view.EmojiPanelView
import com.monsoonscript.keyboard.view.KeyboardView
import com.monsoonscript.keyboard.view.SearchablePanel
import com.monsoonscript.keyboard.view.VaultPanelView
import com.monsoonscript.keyboard.view.VoicePanelView
import com.monsoonscript.keyboard.vault.VaultHub
import com.monsoonscript.keyboard.vault.VaultMainActivity
import com.monsoonscript.keyboard.vault.VaultPickerModel
import com.monsoonscript.keyboard.vault.VaultPrefs
import com.monsoonscript.keyboard.vault.VaultUnlockActivity
import com.monsoonscript.keyboard.voice.CrashLog
import com.monsoonscript.keyboard.voice.VoiceInput
import com.monsoonscript.keyboard.voice.VoiceLang
import java.io.IOException
import com.monsoonscript.keyboard.theme.ThemeStore
import com.monsoonscript.keyboard.theme.palette
import com.monsoonscript.keyboard.autofill.InlineStrip
import com.monsoonscript.keyboard.autofill.InlineStripHost
import androidx.annotation.RequiresApi

/**
 * The keyboard service.
 *
 * Hardware keyboards: the only key events this class looks at are the volume keys, and only while the owner switched on
 * "Volume buttons move the cursor" (G2) with the keyboard on screen; every other key goes to super, so physical key events
 * reach the app untouched (and there is no fullscreen extract UI). Whether the on-screen keyboard shows
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

    // ---- text shortcuts in the strip (E3.7, D68): a typed `;sig` offers its saved text as a candidate; nothing is inserted until it is tapped

    /** The characters typed since the last space: matched against the triggers. Fed next to [tracker]. */
    /** The last characters of the line, for the calculator on the strip (L13, D99); it is fed from the same places as [triggerRun] (see below). */
    private val calcRun = CalcRun()
    private var calcOn = true
    private var calcRadians = false
    private var calcOffer: CalcOffer? = null

    /** The run of characters since the last space (shortcuts). It also keeps [calcRun] in step: every place that feeds, clears or rebuilds the one does the same to the other. */
    private val triggerRun = object : TriggerRun() {
        override fun clear() {
            super.clear()
            calcRun.clear()
        }

        override fun append(c: Char) {
            super.append(c)
            calcRun.append(c)
        }

        override fun backspace(): Boolean {
            val known = super.backspace()
            if (!calcRun.backspace()) return false
            return known
        }

        override fun reset(before: CharSequence?) {
            super.reset(before)
            calcRun.reset(before)
        }
    }

    /** The shortcuts offered right now. Their texts are the FIRST entries of [suggestions]: never use those for auto-correction. */
    private var shortcutHits: List<ClipShortcut> = emptyList()
    private var openClipboardAfterUnlock = 0L // uptime of the clipboard key press that started the unlock prompt (D71), 0 = none
    private var unlockEndedAt = 0L // uptime when that prompt ended (unlocked or cancelled), 0 = still running or none (E3.12)
    private var unlockOk = false
    private var unlockRetries = 0
    private val mainHandler = Handler(Looper.getMainLooper())

    /** What the word engines last asked the strip to show, before the shortcut candidates were put in front. */
    private var engineWords: List<String> = emptyList()

    /** Password, URL and e-mail fields get the typed letters as they are, even in a Bangla mode. */
    private var composingAllowed = true
    private var lastEditAt = 0L

    private val avroActive: Boolean get() = language == Language.AVRO && composingAllowed

    // ---- word prediction (L2): autocomplete and next word in the strip, see predict/ and PredictionWorker

    /** Not in password/URL/e-mail/number fields, and not when the editor asks for no suggestions. */
    private var predictionAllowed = false

    /**
     * Follows the word being typed and the two before it in the editor (the editor stays the source of truth). In Avro it is fed what is
     * committed (the word being composed joins it when it is committed), so while a word is composed the strip shows the Avro dictionary
     * words and only after it the next-word predictions from the Bangla pack.
     */
    private val tracker = WordTracker()

    /** Background predictions, started on the first request. */
    private var predWorker: PredictionWorker? = null

    /** Newest prediction request; answers with another number are stale and dropped. */
    private var predSeq = 0

    /** The editor has a selection: Backspace then deletes more than one character and the tracker must be rebuilt. */
    private var hasSelection = false

    private val predicting: Boolean get() = predictionAllowed

    /** A next-word / completion answer will follow a drop right away; only then may the strip hold the old candidates (G4). */
    private val answerFollows: Boolean get() = predictionAllowed && showSuggestions

    /**
     * Strip settings (G1.3), cached by [applyPrefs]. They only decide what the strip shows: [predicting] (the word tracker, and with it
     * learning) is not touched, so turning the strip off does not stop "Learn from my typing". Avro dictionary choices are not affected.
     */
    private var showSuggestions = true
    private var nextWord = true
    private var typedFirst = true

    // ---- auto-correction (L8, D69): off by default, English typing only. See predict/Corrector and ARCHITECTURE.md

    private var autoCorrect = false

    /** The fix the worker found for [typed]; space applies it while the typed word is still the same. Never read from the strip (shortcut texts may come first, E3.7). */
    private class PendingFix(val typed: String, val fix: String)
    private var pendingFix: PendingFix? = null

    /** The fix just applied, so Backspace right after it can give the typed word back. [stamp] is [lastEditAt] right after the fix: any later edit changes it. */
    private class DoneFix(val original: String, val fixed: String, val stamp: Long)
    private var lastFix: DoneFix? = null

    /** After Backspace brought a trigger back, the next Space must not turn it into the text again (D77). */
    private var skipTrigger: String? = null

    /** Typing settings (G2, G3), cached by [applyPrefs]; [autoShow] is also read before the view exists, so [onCreate] sets it too. */
    private var autoShow = true
    private var volumeCursor = Prefs.VOLUME_OFF

    /**
     * The strip still paints candidates that were already dropped (G4): right after a space, a tapped word or a language switch the
     * next answer is a few frames away, and clearing at once would flash the icon row in between. They are not tappable meanwhile
     * ([suggestions] is empty); [heldClear] ends the hold if no answer comes.
     */
    private var stripHeld = false
    private val heldClear = Runnable { if (stripHeld) setSuggestions(emptyList()) }

    /**
     * Finished words are remembered (on the phone only, D42) when the owner left "Learn from my typing" on and the field allows it:
     * not in password/e-mail/URL fields, and not when the app asks for no personalized learning (incognito tabs and the like).
     */
    private var learnAllowed = false

    // ---- emoji (E2, D56) and clipboard (E3, D65) panels: one of them replaces the keys; while searching it sits above the keys and typed text goes to the query, not the editor

    private var root: LinearLayout? = null
    private var emojiPanel: EmojiPanelView? = null
    private var emojiLoader: EmojiLoader? = null
    private var emojiStore: EmojiStore? = null
    private var emojiPreloadScheduled = false

    /** The panel that is on screen in place of the keys (emoji or clipboard); null while the keys show. */
    private var shownPanel: View? = null
    private val searchPanel: SearchablePanel? get() = shownPanel as? SearchablePanel

    /** Typing into a panel's search bar: [searchBuf] holds the finished part of the query, the Avro composer the word in progress. */
    private var panelSearch = false
    private val searchBuf = StringBuilder()
    private var searchCaret = 0 // the voice panel's text box has a caret that can be moved; other panels always type at the end of the query

    /** Recents are not kept in password fields or when the app asks for no personalized learning (incognito). */
    private var emojiRecents = true

    // ---- clipboard history (E3, D65): records what is copied while this keyboard is the active one; never in password / incognito fields
    private var clipHub: ClipHub? = null
    private var clipCapture: ClipCapture? = null
    private var clipPanel: ClipboardPanelView? = null
    private var clipModel: ClipPanelModel? = null

    // ---- password picker (E1.14, D27 / D29 / D87): the key icon in the strip opens it in place of the keys; what it types never reaches the learning (D42)
    private var vaultPanel: VaultPanelView? = null
    private var vaultModel: VaultPickerModel? = null
    private var vaultPrefsCache: VaultPrefs? = null
    private var vaultPackage = "" // the app whose field has the keyboard (EditorInfo.packageName), for "This app"
    private var fieldIsPassword = false
    private var openVaultAfterUnlock = 0L // uptime of the "Unlock" tap that opened the see-through unlock card, 0 = none
    private var vaultAfterUnlock = false // the unlock that just ended was the picker's (not the clipboard's)
    private var vaultCardUp = false // the see-through unlock card is on screen (from the "Unlock" tap until it has gone)
    private var vaultReturnPackage = "" // the app the picker was opened in: only a field of that app gets the keyboard / picker back after the card
    private var vaultLearnBlock = false // something from the vault was typed into this field: nothing typed after it is learned until the field is gone

    init {
        tracker.onWord = { prev, word -> learnWord(prev, word) }
    }

    /** The settings screen's "Clear learned words" bumps an epoch counter; the service empties its in-memory copy when it hears it. */
    private val prefsListener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        if (key == LearnSettings.KEY_CLEAR_EPOCH) predWorker?.clearLearned() else applyPrefs()
    }

    /** Settings that the view needs to know (read when the view is created, when an input starts and when a setting changes). */
    private fun applyPrefs() {
        keyboard?.setLanguageOptions(Prefs.languageKey(this), Prefs.spaceSwipe(this))
        keyboard?.setBanglaDigits(Prefs.banglaDigits(this))
        keyboard?.let { kb ->
            kb.setThemes(ThemeStore.day(this).palette(this), ThemeStore.night(this).palette(this)) // the owner's themes (L10, D75)
            kb.setAppearance(Prefs.theme(this), Prefs.numberRow(this), Prefs.emojiKey(this), Prefs.heightPortrait(this), Prefs.heightLandscape(this), Prefs.keyGap(this))
            kb.setTextSizes(Prefs.keyTextSize(this), Prefs.stripTextSize(this))
            applySystemBars() // the theme setting can change the keyboard color
            emojiPanel?.setPalette(kb.palette)
            clipPanel?.setPalette(kb.palette)
        }
        keyboard?.setSpaceDrag(Prefs.spaceDrag(this), Prefs.dragDelayMs(this), Prefs.dragStepDp(this))
        keyboard?.setTypingFeel(Prefs.vibration(this), Prefs.keySound(this), Prefs.soundSource(this) == Prefs.SOUND_BUILTIN, Prefs.longPressMs(this))
        showSuggestions = Prefs.showSuggestions(this)
        nextWord = Prefs.nextWord(this)
        typedFirst = Prefs.typedFirst(this)
        autoCorrect = Prefs.autoCorrect(this)
        if (!autoCorrect) {
            pendingFix = null
            lastFix = null
        }
        calcOn = Prefs.calc(this)
        calcRadians = Prefs.calcRadians(this)
        if (!calcOn) calcOffer = null
        autoShow = Prefs.autoShow(this)
        volumeCursor = Prefs.volumeCursor(this)
        if (!showSuggestions && composer?.isComposing != true) dropSuggestions() // switched off: clear what is shown, never an Avro word in progress
    }

    override fun onCreate() {
        super.onCreate()
        CrashLog.install(this) // uncaught exceptions are written to a file (Settings > Voice typing > Problem report)
        language = Prefs.language(this) // where the keyboard was left (saved by name, unknown = English)
        lastBangla = Prefs.lastBangla(this)
        autoShow = Prefs.autoShow(this) // asked before the keyboard view exists
        volumeCursor = Prefs.volumeCursor(this)
        Prefs.of(this).registerOnSharedPreferenceChangeListener(prefsListener)
        val hub = ClipHub.get(this)
        clipHub = hub
        hub.onUnlockFinished = { ok -> unlockFinished(ok) }
        VaultHub.onUnlockFinished = { ok -> vaultUnlockFinished(ok) }
        hub.start()
        clipCapture = ClipCapture(this, hub).also { it.start() }
        hub.onCopied = { refreshRecentChip() }
    }

    override fun onCreateInputView(): View {
        val v = KeyboardView(this)
        v.listener = this
        v.setLanguage(language)
        keyboard = v
        // The keys and (once the emoji key is used) the emoji panel share this column; only one of them is visible at a time, except while searching.
        val r = LinearLayout(this)
        r.orientation = LinearLayout.VERTICAL
        r.addView(v, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        root = r
        // E1.21: Android 11+ Autofill chips sit in a bar over the strip, so the input view is a frame that holds the keyboard column and that bar.
        var top: View = r
        inlineBar = null
        if (Build.VERSION.SDK_INT >= 30) {
            val frame = FrameLayout(this)
            frame.addView(r, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
            val bar = InlineStripHost(this) { (keyboard?.palette?.bg ?: Color.BLACK) to (keyboard?.palette?.textMuted ?: Color.GRAY) }
            frame.addView(bar, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, v.stripHeightPx(), Gravity.TOP))
            inlineBar = bar
            top = frame
        }
        // A new input view (rotation, dark mode, font size, ...) means the panels of the old one are gone: every lazily built panel must be forgotten here. The voice
        // panel was missing from this list, so after a rebuild a tap on the mic hid the NEW keys and showed the OLD panel, which is not on screen: an empty keyboard
        // until Back (the owner's "voice panel sometimes invisible", cured only by an update = a new process).
        if (voicePanel?.visibility == View.VISIBLE) voice?.panelClose()
        voicePanel = null
        emojiPanel = null
        clipPanel = null
        clipModel = null
        vaultPanel = null
        vaultModel = null
        shownPanel = null
        panelSearch = false
        searchBuf.setLength(0)
        applyPrefs()
        applySystemBars()
        return top
    }

    // ---- E1.21 (D102): Autofill chips in the strip, Android 11+. The vault's Autofill service answers; this only asks and hosts what comes back (autofill/InlineStrip.kt).

    /** The bar over the strip that hosts the chips; null below Android 11 (the dropdown menu is used there). */
    private var inlineBar: InlineStripHost? = null

    @RequiresApi(Build.VERSION_CODES.R)
    override fun onCreateInlineSuggestionsRequest(uiExtras: Bundle): InlineSuggestionsRequest? {
        val kb = keyboard ?: return null
        if (!VaultPrefs(this).hasVault) return null // nothing of ours to suggest; another Autofill service may still use the dropdown
        return try {
            InlineStrip.request(this, uiExtras, kb.stripHeightPx())
        } catch (_: RuntimeException) {
            null // never let Autofill break typing: the dropdown stays
        }
    }

    @RequiresApi(Build.VERSION_CODES.R)
    override fun onInlineSuggestionsResponse(response: InlineSuggestionsResponse): Boolean {
        val bar = inlineBar ?: return false
        if (panelSearch || shownPanel != null) return false // the strip is not on screen: Android falls back to its dropdown
        return try {
            bar.show(response.inlineSuggestions)
            true
        } catch (_: RuntimeException) {
            bar.clear()
            false
        }
    }

    /** The owner typed: the chips make way for the strip, like the clipboard chip. */
    private fun inlineTyped() {
        if (Build.VERSION.SDK_INT >= 30) inlineBar?.let { if (it.isShown) it.dismiss() }
    }

    /** No full-screen extract UI in landscape (Gboard does not either). */
    override fun onEvaluateFullscreenMode(): Boolean = false

    /**
     * G3: with "Open the keyboard automatically" off, only a request that comes from the user (a tap on the field, which the app passes on as
     * an explicit show) opens the keyboard; the implicit ones Android makes when a window or field just got focus are refused. A rotation
     * re-asks with the original flags and is let through, so an open keyboard stays open. Apps that ask for the keyboard "explicitly" on
     * their own cannot be told from a tap and still open it.
     */
    override fun onShowInputRequested(flags: Int, configChange: Boolean): Boolean {
        if (!autoShow && !configChange && (flags and InputMethod.SHOW_EXPLICIT) == 0 && !returningFromUnlock()) return false
        return super.onShowInputRequested(flags, configChange)
    }

    /**
     * The keyboard was open when the clipboard key started the unlock prompt (D71), the prompt took the screen and is over now (unlocked or cancelled). For [RETURN_MS] the
     * keyboard comes back by itself even with "Open the keyboard automatically: off": Android's own implicit request is let through, and [showKeyboardAfterUnlock] asks too
     * because some apps never do. Not while the prompt is still up (E3.11, E3.12).
     */
    private fun returningFromUnlock(): Boolean {
        if (unlockEndedAt == 0L || SystemClock.uptimeMillis() - unlockEndedAt >= RETURN_MS) return false
        return !vaultAfterUnlock || vaultEditorIsBack()
    }

    /** True when a real field of the app the picker was opened in has the keyboard again and the unlock card is gone (E1.14: never for the card's own field or another app). */
    private fun vaultEditorIsBack(info: EditorInfo? = currentInputEditorInfo): Boolean =
        !vaultCardUp && currentInputStarted && info != null && info.inputType != InputType.TYPE_NULL && info.packageName == vaultReturnPackage

    /** The keyboard may be asked to show only while a field is attached; without one the request opens a keyboard with no text field (owner report, 2026-11-25). */
    private fun editorAttached(): Boolean {
        val info = currentInputEditorInfo
        return currentInputStarted && info != null && info.inputType != InputType.TYPE_NULL
    }

    /** The unlock prompt of the clipboard key ended ([ClipHub.onUnlockFinished]). */
    private fun unlockFinished(ok: Boolean) {
        val pressed = openClipboardAfterUnlock
        if (pressed == 0L || SystemClock.uptimeMillis() - pressed >= UNLOCK_OPEN_MS) return // not ours, or too long ago
        vaultAfterUnlock = false
        unlockEndedAt = SystemClock.uptimeMillis()
        unlockOk = ok
        unlockRetries = 0
        if (isInputViewShown) openPanelAfterUnlock() else showKeyboardAfterUnlock()
    }

    /** The keyboard is up after the prompt: the clipboard panel opens when it worked, on cancel the keys stay. May run more than once (a field can start twice). */
    private fun openPanelAfterUnlock() {
        if (vaultAfterUnlock) { // the picker's unlock card: unlocked = the picker opens again; cancelled = the keys stay (the owner closed it on purpose)
            if (!unlockOk || !vaultEditorIsBack()) return
            vaultAfterUnlock = false // used up: later fields start on the keys
            unlockEndedAt = 0L
            mainHandler.post { if (shownPanel?.visibility != View.VISIBLE && vaultEditorIsBack()) showVault() }
            return
        }
        if (!unlockOk || clipHub?.lockOpen() != true) return
        mainHandler.post { if (shownPanel?.visibility != View.VISIBLE) showClipboard() }
    }

    /** Ask the system to show the keyboard again a few times (the app's field may not be back yet the first time). */
    private fun showKeyboardAfterUnlock() {
        if (!returningFromUnlock() || isInputViewShown) return
        if (editorAttached()) { // a request without a field would open a keyboard with no text field
            try {
                requestShowSelf(0) // an explicit request: allowed by "Open the keyboard automatically: off" too
            } catch (e: RuntimeException) {
                // no field to show it for
            }
        }
        if (++unlockRetries < SHOW_RETRIES) mainHandler.postDelayed({ showKeyboardAfterUnlock() }, SHOW_RETRY_MS)
    }

    override fun onStartInput(attribute: EditorInfo?, restarting: Boolean) {
        super.onStartInput(attribute, restarting)
        if (returningFromUnlock() && !isInputViewShown) { // the app's field is back after the prompt
            unlockRetries = 0
            mainHandler.postDelayed({ showKeyboardAfterUnlock() }, SHOW_RETRY_MS / 3)
        }
    }

    // ---- volume buttons move the cursor (G2): the IME sees key events before the app while the keyboard is up

    /** True when this volume key is ours: the setting is on, the keyboard is on screen over a real editor, and no emoji search is typing. */
    private fun volumeKeyIsOurs(keyCode: Int): Boolean {
        if (volumeCursor == Prefs.VOLUME_OFF) return false
        if (keyCode != KeyEvent.KEYCODE_VOLUME_UP && keyCode != KeyEvent.KEYCODE_VOLUME_DOWN) return false
        if (!isInputViewShown || panelSearch) return false
        val info = currentInputEditorInfo ?: return false
        return currentInputConnection != null && info.inputType != InputType.TYPE_NULL
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        if (volumeKeyIsOurs(keyCode)) {
            // Holding the button repeats this call, which moves the cursor again and again. Up = left unless the setting says otherwise.
            val up = keyCode == KeyEvent.KEYCODE_VOLUME_UP
            moveCursor(left = if (volumeCursor == Prefs.VOLUME_UP_LEFT) up else !up)
            return true
        }
        return super.onKeyDown(keyCode, event)
    }

    /** The release of a volume key whose press moved the cursor must not reach the app or the volume panel either. */
    override fun onKeyUp(keyCode: Int, event: KeyEvent): Boolean {
        if (volumeKeyIsOurs(keyCode)) return true
        return super.onKeyUp(keyCode, event)
    }

    /** One step left or right, as the app's own arrow keys would do it (clusters, right-to-left text). A Bangla word in progress is finished first. */
    private fun moveCursor(left: Boolean) {
        commitComposingIfAny()
        pendingFix = null // the cursor leaves the word: no automatic fix any more, and no undo of the last one (L8)
        lastFix = null
        sendDownUpKeyEvents(if (left) KeyEvent.KEYCODE_DPAD_LEFT else KeyEvent.KEYCODE_DPAD_RIGHT)
        lastEditAt = 0L // the selection change that follows is a real move, not an echo: [onUpdateSelection] reads the editor again
    }

    /** The space bar cursor drag (L9, D72): [steps] characters, negative = left; each one is a cursor key press, so clusters and right-to-left text behave as in the app itself. */
    override fun onCursorMove(steps: Int) {
        if (panelSearch || steps == 0) return
        repeat(kotlin.math.abs(steps)) { moveCursor(left = steps < 0) }
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        keyboard?.refreshTheme()
        applySystemBars()
        val kb = keyboard ?: return
        emojiPanel?.setPalette(kb.palette)
        clipPanel?.setPalette(kb.palette)
        val shown = searchPanel ?: return
        setPanelHeight(if (panelSearch) shown.compactHeight() else kb.desiredHeight())
    }

    override fun onStartInputView(info: EditorInfo?, restarting: Boolean) {
        super.onStartInputView(info, restarting)
        if (!restarting) {
            closePanels() // a new field starts on the letter keys (a restart of the same field keeps the panel)
            if (Build.VERSION.SDK_INT >= 30) inlineBar?.reset() // and may show Autofill chips again
        }
        if (panelSearch) flushSearchComposing() else composer?.clear()
        dropSuggestions()
        val inputType = info?.inputType ?: 0
        val cls = inputType and InputType.TYPE_MASK_CLASS
        composingAllowed = info != null && cls == InputType.TYPE_CLASS_TEXT && !isRawTextVariation(inputType)
        predictionAllowed = composingAllowed && (inputType and InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS) == 0
        val noLearning = ((info?.imeOptions ?: 0) and EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING) != 0
        learnAllowed = predictionAllowed && !noLearning && LearnSettings.enabled(this)
        emojiRecents = !noLearning && !isPasswordField(inputType)
        fieldIsPassword = isPasswordField(inputType)
        vaultPackage = info?.packageName ?: ""
        clipCapture?.fieldAllowed = emojiRecents // nothing copied while a password / incognito field has the keyboard is saved (D65)
        clipHub?.store?.purge() // apply the limit and auto-delete age
        tracker.reset(null)
        triggerRun.clear()
        shortcutHits = emptyList()
        hasSelection = false
        val v = keyboard ?: return
        applyPrefs()
        if (!emojiPreloadScheduled) { // read the emoji list and font in the background shortly after the first use, so the panel opens at once
            emojiPreloadScheduled = true
            v.postDelayed({ ensureEmojiLoader().start(null) }, EMOJI_PRELOAD_MS)
        }
        val numeric = cls == InputType.TYPE_CLASS_NUMBER || cls == InputType.TYPE_CLASS_PHONE || cls == InputType.TYPE_CLASS_DATETIME
        v.setPage(if (numeric) Layouts.PAGE_SYMBOLS else Layouts.PAGE_LETTERS)
        v.setLanguage(language)
        applySystemBars()
        refreshAutoCaps()
        // Read what is already in the field once the keyboard is on screen (a blocking call into the app, so not before the first frame).
        if (predicting) v.post { resyncAndPredict() }
        if (returningFromUnlock()) openPanelAfterUnlock() // the prompt of the clipboard key is over: the panel opens (E3.12; the mark ends by itself)
    }

    override fun onFinishInputView(finishingInput: Boolean) {
        super.onFinishInputView(finishingInput)
        if (Build.VERSION.SDK_INT >= 30) inlineBar?.clear()
        clipHub?.lock?.setPanelOpen(false) // the keyboard went away: the lock timer starts now
        currentInputConnection?.finishComposingText()
        composer?.clear()
        tracker.reset(null)
        triggerRun.clear()
        shortcutHits = emptyList()
        dropSuggestions()
        predWorker?.flush() // save what was learned
        keyboard?.cancelAll()
        voice?.cancel()
        closePanels()
    }

    /** The field is gone: clips copied in other apps from now on are recorded again. */
    override fun onFinishInput() {
        super.onFinishInput()
        clipCapture?.fieldAllowed = true
        vaultLearnBlock = false // the field the vault typed into is gone
    }

    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        if (vaultCardUp) CrashLog.note(this, "memory warning (trim level $level) while the vault unlock card is up")
    }

    override fun onDestroy() {
        clipHub?.onCopied = null
        clipHub?.onUnlockFinished = null
        VaultHub.onUnlockFinished = null
        clipCapture?.stop()
        clipCapture = null
        Prefs.of(this).unregisterOnSharedPreferenceChangeListener(prefsListener)
        worker?.stop()
        worker = null
        predWorker?.stop()
        predWorker = null
        voice?.release()
        voice = null
        super.onDestroy()
    }

    /** The user moved the cursor or the app dropped the composing region: forget the word we were building. */
    override fun onUpdateSelection(
        oldSelStart: Int, oldSelEnd: Int, newSelStart: Int, newSelEnd: Int, candidatesStart: Int, candidatesEnd: Int,
    ) {
        super.onUpdateSelection(oldSelStart, oldSelEnd, newSelStart, newSelEnd, candidatesStart, candidatesEnd)
        hasSelection = newSelStart != newSelEnd
        // A change that is not an echo of our own edit (cursor moved, text replaced by the app, a physical keyboard): read the editor again.
        if (predicting && SystemClock.uptimeMillis() - lastEditAt > PhoneticComposer.STALE_MS) resyncAndPredict()
        if (panelSearch) return // the composer holds the search word, not text in the editor
        val comp = composer ?: return
        if (!comp.isComposing) return
        val sinceEdit = SystemClock.uptimeMillis() - lastEditAt
        if (PhoneticComposer.externalMove(newSelStart, newSelEnd, candidatesStart, candidatesEnd, sinceEdit)) {
            comp.clear()
            dropSuggestions()
            currentInputConnection?.finishComposingText() // leave the old word as plain text
            resyncAndPredict()
        }
    }

    private fun isPasswordField(inputType: Int): Boolean {
        val variation = inputType and InputType.TYPE_MASK_VARIATION
        return when (inputType and InputType.TYPE_MASK_CLASS) {
            InputType.TYPE_CLASS_TEXT -> variation == InputType.TYPE_TEXT_VARIATION_PASSWORD ||
                variation == InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD ||
                variation == InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD
            InputType.TYPE_CLASS_NUMBER -> variation == InputType.TYPE_NUMBER_VARIATION_PASSWORD
            else -> false
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
        if (comp.endsWithColon) {
            // D24: a colon at the end stays a plain ':'; looking up words would drop it, so show only the word itself.
            worker?.cancel()
            setSuggestions(listOf(shown))
        } else {
            requestSuggestions(comp.rawText(), shown)
        }
    }

    // ---- suggestions (T8b): worker answers become the strip candidates, see KeyboardView.setSuggestions

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

    /**
     * The word ended (or was abandoned): no candidates and no pending search. With [keepShown] (only where a new request follows at once:
     * after a space or punctuation, a tapped suggestion, a language switch) the strip keeps painting the old candidates until the answer
     * replaces them (G4), at most [HELD_MS]; a tap on them does nothing meanwhile.
     */
    private fun dropSuggestions(keepShown: Boolean = false) {
        worker?.cancel()
        predWorker?.cancel()
        predSeq++ // an answer still on its way is stale now
        engineWords = emptyList()
        pendingFix = null
        val showing = suggestions.isNotEmpty() || stripHeld
        val kb = keyboard
        if (keepShown && kb != null && showing) {
            suggestions = emptyList()
            stripHeld = true
            kb.removeCallbacks(heldClear)
            kb.postDelayed(heldClear, HELD_MS)
            return
        }
        if (showing) setSuggestions(emptyList())
    }

    // ---- prediction (English layout)

    /** Rebuild the tracker from the text before the cursor (the last few dozen characters are enough). */
    private fun resyncTracker() {
        val before = currentInputConnection?.getTextBeforeCursor(RESYNC_CHARS, 0)
        tracker.reset(before)
        triggerRun.reset(before)
    }

    private fun resyncAndPredict() {
        if (!predicting || composer?.isComposing == true) {
            if (stripHeld && composer?.isComposing != true) dropSuggestions() // no answer is coming: end the hold at once
            return
        }
        resyncTracker()
        requestPrediction()
    }

    /** Ask the worker for completions of the word in progress, or for the next word when none is started. */
    private fun requestPrediction() {
        refreshShortcuts()
        refreshCalc()
        if (!predicting || !showSuggestions) {
            if (stripHeld) dropSuggestions() // nothing will answer: end the hold at once
            return
        }
        if (composer?.isComposing == true) return // the Avro dictionary words own the strip while a word is composed
        val prefix = tracker.prefix
        val w1 = tracker.w1
        if (prefix.isEmpty() && !nextWord) { // next-word suggestions are off: nothing between words, the icon row stays
            dropSuggestions()
            return
        }
        if (prefix.isEmpty() && w1 == null) { // nothing to go on (start of a field or sentence): the icon row stays
            dropSuggestions()
            return
        }
        val seq = ++predSeq
        val w = predWorker ?: PredictionWorker(this, ::onPredictionResult).also { predWorker = it }
        val mode = if (language.isBangla) PredictionWorker.Mode.BANGLA else PredictionWorker.Mode.ENGLISH
        w.request(seq, mode, tracker.typed, prefix, w1, tracker.w2, tracker.atSentenceStart, typedFirst, autoCorrect && !language.isBangla)
    }

    private fun learnWord(prev: String?, word: String) {
        if (!learnAllowed || vaultLearnBlock) return
        if (word.length == 1 && word[0] < '\u0980') return // single Latin letters are not worth remembering
        val w = predWorker ?: PredictionWorker(this, ::onPredictionResult).also { predWorker = it }
        w.learn(prev, word)
    }

    private fun onPredictionResult(r: PredictionWorker.Result) {
        if (r.seq != predSeq || !predicting || !showSuggestions) return
        pendingFix = if (autoCorrect && !language.isBangla) r.autoFix?.let { PendingFix(tracker.typed, it) } else null
        setSuggestions(r.words)
    }

    /** A prediction was tapped: it replaces the word in progress (checked against the editor first) and a space follows. */
    private fun acceptPrediction(ic: InputConnection, word: String) {
        val typed = tracker.typed
        if (autoCorrect && typed.isNotEmpty() && word == typed) predWorker?.reject(TextNorm.key(typed)) // tapped "my word": never fix it (D69)
        if (typed.isNotEmpty()) {
            val before = ic.getTextBeforeCursor(typed.length, 0)
            if (before == null || before.toString() != typed) { // the editor is not what we thought: do not delete the wrong text
                resyncTracker()
                requestPrediction()
                return
            }
        }
        ic.beginBatchEdit()
        if (typed.isNotEmpty()) ic.deleteSurroundingText(typed.length, 0)
        ic.commitText("$word ", 1)
        ic.endBatchEdit()
        lastEditAt = SystemClock.uptimeMillis()
        tracker.dropWord()
        tracker.appendAll("$word ") // the word is finished: it is the new context
        triggerRun.clear()
        refreshAutoCaps()
        requestPrediction()
    }

    private fun setSuggestions(list: List<String>) {
        if (stripHeld) { // a real answer (or the end of the hold) replaces the held candidates
            stripHeld = false
            keyboard?.removeCallbacks(heldClear)
        }
        engineWords = list
        val shown = withShortcuts(list)
        suggestions = shown
        val fx = pendingFix
        val quoted = if (fx != null && autoCorrect && !language.isBangla) shown.indexOf(fx.typed) else -1 // the typed word beside a fix is drawn in quotes
        keyboard?.setSuggestions(shown, quoted)
        keyboard?.removeCallbacks(chipExpire)
        val chip = clipHub?.recent
        if (chip != null && shown.isNotEmpty() && recentChipText() != null) keyboard?.postDelayed(chipExpire, chip.remainingMs() + 50) // the chip leaves when its minute is over
    }

    // ---- auto-correction (L8, D69)

    /**
     * Space after a word the worker found a sure fix for: the fix replaces the word, then the space follows. What the strip shows first
     * (the fix, the typed word in quotes) is exactly what happens. Only while the answer is current, the editor holds the typed word
     * and no shortcut candidate is in front. Returns true when it did the whole job.
     */
    private fun applyAutoFix(ic: InputConnection): Boolean {
        val f = pendingFix ?: return false
        pendingFix = null
        if (!autoCorrect || language.isBangla || !predicting || composer?.isComposing == true) return false
        if (shortcutHits.isNotEmpty() || tracker.typed != f.typed) return false
        val before = ic.getTextBeforeCursor(f.typed.length, 0)
        if (before == null || before.toString() != f.typed) return false // the editor is not what we thought
        ic.beginBatchEdit()
        ic.deleteSurroundingText(f.typed.length, 0)
        ic.commitText(f.fix + " ", 1)
        ic.endBatchEdit()
        lastEditAt = SystemClock.uptimeMillis()
        lastFix = DoneFix(f.typed, f.fix, lastEditAt)
        tracker.dropWord()
        tracker.appendAll(f.fix + " ") // the fixed word is the context now (and what is learned)
        triggerRun.clear()
        refreshAutoCaps()
        requestPrediction()
        return true
    }

    /** Backspace right after an automatic fix: the typed word comes back, without the space, and is never fixed again. Returns true when it did. */
    private fun undoAutoFix(): Boolean {
        val d = lastFix ?: return false
        lastFix = null
        val ic = currentInputConnection ?: return false
        if (hasSelection || d.stamp != lastEditAt) return false
        val expected = d.fixed + " "
        val before = ic.getTextBeforeCursor(expected.length, 0)
        if (before == null || before.toString() != expected) return false
        ic.beginBatchEdit()
        ic.deleteSurroundingText(expected.length, 0)
        ic.commitText(d.original, 1)
        ic.endBatchEdit()
        lastEditAt = SystemClock.uptimeMillis()
        predWorker?.reject(TextNorm.key(d.original))
        skipTrigger = d.original // a shortcut trigger comes back as typed: the next Space leaves it alone (D77)
        resyncTracker()
        requestPrediction()
        refreshAutoCaps()
        return true
    }

    // ---- text shortcuts (E3.7, D68)

    /** Shortcuts are offered where the strip may show candidates and the characters are committed as typed (not while Avro composes). */
    private fun shortcutsActive(): Boolean =
        answerFollows && !avroActive && !panelSearch && clipHub?.store?.shortcuts?.isNotEmpty() == true

    /**
     * Direct insertion (E3.10, D77): Space after a complete trigger replaces the trigger with the full text, the space follows. Uses [lastFix], so Backspace right after
     * brings the trigger back (without the space) like an auto-correction. Goes before auto-correction; no strip candidates are shown while it is on.
     */
    private fun applyDirectShortcut(ic: InputConnection): Boolean {
        val hub = clipHub ?: return false
        if (!hub.directShortcuts || !predicting || avroActive || panelSearch || hasSelection || composer?.isComposing == true) return false
        val typed = triggerRun.text
        if (typed.isEmpty()) return false
        val skip = skipTrigger
        skipTrigger = null
        if (skip != null && skip.equals(typed, ignoreCase = true)) return false
        val hit = hub.store.shortcutFor(typed) ?: return false
        val before = ic.getTextBeforeCursor(typed.length, 0)
        if (before == null || before.toString() != typed) return false // the editor is not what we thought: a plain space
        ic.beginBatchEdit()
        ic.deleteSurroundingText(typed.length, 0)
        ic.commitText(hit.text + " ", 1)
        ic.endBatchEdit()
        lastEditAt = SystemClock.uptimeMillis()
        lastFix = DoneFix(typed, hit.text, lastEditAt)
        shortcutHits = emptyList()
        resyncTracker() // the editor now holds the full text
        refreshAutoCaps()
        requestPrediction()
        return true
    }

    /** Match what was typed since the last space against the triggers; when the offer changed, repaint the strip. Called before every prediction request. */
    private fun refreshShortcuts() {
        val store = clipHub?.store
        var hits: List<ClipShortcut> = emptyList()
        if (store != null && shortcutsActive() && clipHub?.directShortcuts != true) { // direct insertion replaces the candidates
            val typed = triggerRun.text
            if (typed.isNotEmpty()) hits = store.matchShortcuts(typed, MAX_SHORTCUT_CANDIDATES)
        }
        if (hits.isEmpty() && shortcutHits.isEmpty()) return // the usual case: nothing offered, nothing to take back
        if (hits.size == shortcutHits.size) {
            var same = true
            for (i in hits.indices) if (hits[i].id != shortcutHits[i].id || hits[i].text != shortcutHits[i].text) same = false
            if (same) return
        }
        shortcutHits = hits
        setSuggestions(engineWords) // merges the new offer in front of the engines' words
    }

    /** The strip list: the calculator's two chips, the recent copy chip, the shortcut texts (one line each), then what the word engines suggested. */
    private fun withShortcuts(words: List<String>): List<String> {
        val chip = recentChipText()
        val calc = calcOffer
        if (shortcutHits.isEmpty() && chip == null && calc == null) return words
        val out = ArrayList<String>(words.size + shortcutHits.size + 3)
        if (calc != null) {
            out.add(calc.appendLabel)
            out.add(calc.replaceLabel)
        }
        if (chip != null) out.add(chipLabel(chip))
        for (h in shortcutHits) {
            val label = ClipFormat.stripLabel(h.text)
            if (label.isNotEmpty() && label !in out) out.add(label)
        }
        for (w in words) if (w !in out) out.add(w)
        return out
    }

    // ---- the calculator on the strip (L13, D99)

    /**
     * Look at the end of the typed line: when it is a calculation (16+8, 20% of 150, 5 km in miles) the strip gets two chips in front, "= 24" (adds the answer
     * after it) and "\u2192 24" (replaces what was typed by it). Called before every prediction request like [refreshShortcuts]; the engine says "nothing" after one
     * look at the last character for almost everything that is typed, and the text comes from [calcRun], so there is no call into the app.
     */
    private fun refreshCalc() {
        var offer: CalcOffer? = null
        if (calcOn && predicting && !panelSearch && shownPanel == null) offer = CalcEngine.offer(calcRun.chars, calcRadians)
        val old = calcOffer
        if (offer == null && old == null) return
        if (offer != null && old != null && offer.start == old.start && offer.typed == old.typed && offer.value == old.value) return
        calcOffer = offer
        setSuggestions(engineWords) // merges the new chips in front of the engines' words
    }

    /** A strip candidate was tapped: when it is one of the calculator's chips the answer is added or replaces the calculation (checked against the editor first). */
    private fun acceptCalc(word: String): Boolean {
        val offer = calcOffer ?: return false
        val append = word == offer.appendLabel
        val replace = !append && word == offer.replaceLabel
        if (!append && !replace) return false
        val ic = currentInputConnection ?: return true
        val typed = offer.typed
        val before = ic.getTextBeforeCursor(typed.length, 0)
        if (before == null || before.toString() != typed) { // the editor is not what we thought: do not delete the wrong text
            resyncTracker()
            requestPrediction()
            return true
        }
        ic.beginBatchEdit()
        if (replace) {
            ic.deleteSurroundingText(typed.length, 0)
            ic.commitText(offer.replaceText, 1)
        } else {
            ic.commitText(offer.appendText, 1)
        }
        ic.endBatchEdit()
        lastEditAt = SystemClock.uptimeMillis()
        calcOffer = null
        resyncTracker() // the editor now holds the answer
        refreshAutoCaps()
        requestPrediction()
        return true
    }

    // ---- recent copy chip (E3.10, D77)

    /**
     * The text copied in the last minute, offered as the first strip candidate while nothing is typed yet. Never while the clipboard lock is on, never when the owner
     * switched it off; password fields have no strip, and what is never recorded (sensitive, vault) never reaches [RecentCopy].
     */
    private fun recentChipText(): String? {
        val hub = clipHub ?: return null
        if (!hub.recentChip || hub.lockOn() || !answerFollows || avroActive || panelSearch) return null
        if (composer?.isComposing == true || triggerRun.text.isNotEmpty() || tracker.typed.isNotEmpty()) return null
        return hub.recent.current()
    }

    private fun chipLabel(text: String): String = CHIP_MARK + ClipFormat.stripLabel(text)

    /** A strip candidate was tapped: when it is the recent copy chip, the whole copied text is pasted (and the chip is gone). */
    private fun acceptRecent(word: String): Boolean {
        val hub = clipHub ?: return false
        val text = recentChipText() ?: return false
        if (word != chipLabel(text)) return false
        hub.recent.dismiss()
        pasteClip(text)
        requestPrediction()
        refreshAutoCaps()
        return true
    }

    /** A text was copied while the keyboard is up: show the chip at once, and take it away again when its minute is over. */
    private fun refreshRecentChip() {
        if (recentChipText() == null) return
        setSuggestions(engineWords)
    }

    private val chipExpire = Runnable { setSuggestions(engineWords) }

    /** A strip candidate was tapped: when it is a shortcut, the typed trigger is replaced by the full text (checked against the editor first, no space follows). */
    private fun acceptShortcut(word: String): Boolean {
        if (shortcutHits.isEmpty()) return false
        val hit = shortcutHits.firstOrNull { ClipFormat.stripLabel(it.text) == word } ?: return false
        val ic = currentInputConnection ?: return true
        val typed = triggerRun.text
        if (typed.isNotEmpty()) {
            val before = ic.getTextBeforeCursor(typed.length, 0)
            if (before == null || before.toString() != typed) { // the editor is not what we thought: do not delete the wrong text
                resyncTracker()
                requestPrediction()
                return true
            }
        }
        ic.beginBatchEdit()
        if (typed.isNotEmpty()) ic.deleteSurroundingText(typed.length, 0)
        ic.commitText(hit.text, 1)
        ic.endBatchEdit()
        lastEditAt = SystemClock.uptimeMillis()
        shortcutHits = emptyList()
        resyncTracker() // the editor now holds the expanded text
        refreshAutoCaps()
        requestPrediction()
        return true
    }

    /** What is shown is already the final Bangla text, so finishing just makes it normal (not underlined) text. */
    private fun commitComposingIfAny(keepStrip: Boolean = false) {
        val comp = composer ?: return
        if (!comp.isComposing) return
        if (panelSearch) { // the word belongs to the search query, not to the editor
            flushSearchComposing()
            return
        }
        val shown = comp.text()
        currentInputConnection?.finishComposingText()
        comp.clear()
        dropSuggestions(keepStrip)
        lastEditAt = SystemClock.uptimeMillis()
        if (predicting) tracker.appendAll(shown) // plain text now: the word in progress for the tracker, finished by whatever comes next
    }

    private fun setLanguage(l: Language) {
        language = l
        pendingFix = null
        lastFix = null
        triggerRun.clear() // what was typed in another layout cannot be a trigger of this one
        if (l.isBangla) lastBangla = l
        Prefs.saveLanguage(this, l, lastBangla)
        keyboard?.setLanguage(l)
        refreshAutoCaps()
    }

    // ---- KeyboardView.Listener

    override fun onText(text: String) {
        if (panelSearch) {
            searchText(text)
            return
        }
        val ic = currentInputConnection ?: return
        lastFix = null // any typing ends the chance to undo the last fix
        clipHub?.recent?.dismiss() // typing means the chip is not wanted
        inlineTyped()
        if (text != " ") skipTrigger = null
        if (text == " " && applyDirectShortcut(ic)) return
        if (text == " " && applyAutoFix(ic)) return
        if (avroActive) {
            val comp = ensureComposer()
            if (text.length == 1 && comp.accepts(text[0])) {
                comp.append(text[0])
                showComposing(ic, comp)
                return
            }
            if (comp.isComposing) {
                commitComposingIfAny(keepStrip = answerFollows) // the next-word request follows below
                if (text == ".") { // a full stop right after a Bangla word is the dari
                    ic.commitText(DARI, 1)
                    lastEditAt = SystemClock.uptimeMillis()
                    if (predicting) {
                        tracker.appendAll(DARI)
                        requestPrediction()
                    }
                    return
                }
            }
        }
        ic.commitText(text, 1)
        if (predicting) {
            lastEditAt = SystemClock.uptimeMillis()
            tracker.appendAll(text)
            triggerRun.append(text)
            requestPrediction()
        }
        // getCursorCapsMode is a blocking call into the app: only ask when the text could start a new sentence/word.
        if (text == " " || text == "." || text == "!" || text == "?") refreshAutoCaps()
    }

    /** A strip suggestion was tapped (D23): it replaces the underlined word and a space follows. */
    override fun onSuggestion(word: String) {
        if (panelSearch || suggestions.isEmpty()) return // empty = the strip only still paints the old words (G4)
        lastFix = null
        if (acceptCalc(word)) return
        if (acceptShortcut(word)) return
        if (acceptRecent(word)) return
        val ic = currentInputConnection ?: return
        val comp = composer
        if (comp == null || !comp.isComposing) {
            if (predicting) acceptPrediction(ic, word) // else: the word ended while the finger was down
            return
        }
        ic.commitText("$word ", 1) // one call: replaces the composing region, then the space
        comp.clear()
        dropSuggestions(keepShown = answerFollows) // the next-word request follows below
        lastEditAt = SystemClock.uptimeMillis()
        if (predicting) {
            tracker.appendAll("$word ") // finished: learned, and the context for the next-word predictions
            triggerRun.clear()
            requestPrediction()
        }
    }

    override fun onBackspace() {
        if (panelSearch) {
            searchBackspace()
            return
        }
        inlineTyped()
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
        if (undoAutoFix()) return
        val selected = hasSelection
        sendDownUpKeyEvents(KeyEvent.KEYCODE_DEL)
        if (predicting) {
            lastEditAt = SystemClock.uptimeMillis()
            tracker.backspace()
            val runKnown = triggerRun.backspace()
            if (selected || tracker.needsResync || !runKnown) resyncTracker() // more than one character went, or the cursor is in an earlier word now
            requestPrediction()
        }
    }

    override fun onEnter() {
        if (panelSearch) { // Enter in the search bar shows the results in the grid
            endSearchTyping(clear = false)
            return
        }
        lastFix = null
        commitComposingIfAny()
        val options = currentInputEditorInfo?.imeOptions ?: 0
        val action = options and EditorInfo.IME_MASK_ACTION
        val noEnterAction = (options and EditorInfo.IME_FLAG_NO_ENTER_ACTION) != 0
        if (!noEnterAction && action != EditorInfo.IME_ACTION_NONE && action != EditorInfo.IME_ACTION_UNSPECIFIED) {
            currentInputConnection?.performEditorAction(action)
        } else {
            sendDownUpKeyEvents(KeyEvent.KEYCODE_ENTER)
        }
        if (predicting) {
            lastEditAt = SystemClock.uptimeMillis()
            tracker.append('\n')
            triggerRun.clear()
            requestPrediction()
        }
        refreshAutoCaps()
    }

    override fun onTab() {
        if (panelSearch) return
        lastFix = null
        commitComposingIfAny()
        sendDownUpKeyEvents(KeyEvent.KEYCODE_TAB)
        if (predicting) {
            tracker.append('\t')
            triggerRun.clear()
            requestPrediction()
        }
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
    override fun onLanguageToggle() = switchLanguage(if (language.isBangla) Language.ENGLISH else lastBangla)

    /** A layout was picked in the popup that a long press on the language key opens (L4, D19). */
    override fun onLanguagePicked(language: Language) = switchLanguage(language)

    private fun switchLanguage(target: Language) {
        if (target == language) return
        commitComposingIfAny(keepStrip = answerFollows)
        setLanguage(target)
        dropSuggestions(keepShown = answerFollows) // the new language's answer replaces the old candidates without the icon row flashing in between (G4)
        resyncAndPredict() // English: read what is in the field now (the Bangla text was not tracked)
    }

    // ---- voice typing (G5, D66; spec docs/VOICE_TYPING_SPEC.md): hold the mic key to dictate

    private var voice: VoiceInput? = null

    private val voiceHost = object : VoiceInput.Host {
        override fun voiceText(text: String) = typeVoiceText(text)

        override fun voiceState(state: Int) {
            keyboard?.setMicState(state)
            voicePanel?.setState(state)
        }

        override fun voiceLevel(level: Float) {
            keyboard?.pushMicLevel(level)
            voicePanel?.pushLevel(level)
        }

        override fun voiceMessage(text: String) {
            if (voicePanelUp()) voicePanel?.setMessage(text) else keyboard?.flash(text)
        }
    }

    private fun ensureVoice(): VoiceInput = voice ?: VoiceInput(this, voiceHost).also { voice = it }

    /** The language to listen for follows the active layout, it is never detected (D66). */
    private fun voiceLang() = if (language.isBangla) VoiceLang.BANGLA else VoiceLang.ENGLISH

    override fun onMicTap() {
        try {
            commitComposingIfAny()
            showVoicePanel()
        } catch (t: Throwable) {
            voiceFailed("mic tap / voice panel", t)
        }
    }

    /** The voice code must never take the keyboard down or leave it invisible: record the problem, put the keys back and say what happened over the strip. */
    private fun voiceFailed(what: String, t: Throwable) {
        try {
            CrashLog.record(this, what, t)
        } catch (x: Throwable) {
        }
        voicePanel?.let {
            it.visibility = View.GONE
            if (shownPanel === it) shownPanel = null
        }
        panelSearch = false
        keyboard?.let {
            it.visibility = View.VISIBLE
            it.setStripVisible(true)
            it.flash("Voice problem: " + t.javaClass.simpleName + " " + (t.message ?: ""))
        }
    }

    override fun onMicHoldStart() {
        try {
            commitComposingIfAny()
            ensureVoice().holdStart(voiceLang())
        } catch (t: Throwable) {
            voiceFailed("mic hold", t)
        }
    }

    override fun onMicHoldEnd(cancel: Boolean) {
        voice?.holdEnd(cancel)
    }

    /** Finished speech goes into the tap panel's text box while that is up, otherwise into the editor at the cursor. */
    private fun typeVoiceText(text: String) {
        val panel = voicePanel
        if (panel != null && shownPanel === panel) {
            appendToVoicePanel(text)
            return
        }
        if (panelSearch) return // the keys belong to another panel's search bar, not to the editor
        commitToEditor(text)
    }

    /** Plain text at the cursor, with a space in front when it follows a word. */
    private fun commitToEditor(text: String) {
        val ic = currentInputConnection ?: return
        commitComposingIfAny()
        val before = ic.getTextBeforeCursor(1, 0)
        val last = if (before.isNullOrEmpty()) ' ' else before[before.length - 1]
        val lead = if (Character.isLetterOrDigit(last) || last in ".,!?;:\u0964") " " else ""
        ic.commitText(lead + text, 1)
        lastEditAt = SystemClock.uptimeMillis()
        if (predicting) resyncTracker()
        refreshAutoCaps()
    }

    // ---- voice panel (G5.3b, D66): tap the mic key, speak, fix the text, Insert

    private var voicePanel: VoicePanelView? = null

    private val voicePanelListener = object : VoicePanelView.Listener {
        override fun onMicTap() = voicePanelMic()

        override fun onInsert() = voicePanelInsert()

        override fun onClose() {
            closeVoicePanel()
            refreshAutoCaps()
            resyncAndPredict()
        }

        override fun onBoxTap(offset: Int) = if (panelSearch) moveVoiceCaret(offset) else startVoiceEditing(offset)

        override fun onDoneEditing() = endSearchTyping(clear = false)
    }

    private fun ensureVoicePanel(): VoicePanelView? {
        voicePanel?.let { if (it.parent === root) return it } // a panel left over from an earlier input view is not used again
        val r = root ?: return null
        val kb = keyboard ?: return null
        val p = VoicePanelView(this, kb.palette)
        p.listener = voicePanelListener
        p.visibility = View.GONE
        r.addView(p, 0, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, kb.desiredHeight()))
        voicePanel = p
        return p
    }

    /** The panel takes the place of the keys, at the same height, and starts listening at once. */
    private fun showVoicePanel() {
        val kb = keyboard ?: return
        val panel = ensureVoicePanel() ?: return
        closeEmoji()
        closeClipboard()
        kb.cancelAll()
        panelSearch = false
        searchBuf.setLength(0)
        composer?.clear()
        dropSuggestions()
        searchCaret = 0
        panel.reset()
        panel.setSearching(false)
        panel.setQuery("")
        panel.setPalette(kb.palette)
        kb.setStripVisible(true)
        kb.visibility = View.GONE
        shownPanel = panel
        setPanelHeight(kb.desiredHeight())
        panel.visibility = View.VISIBLE
        ensureVoice().panelStart(voiceLang())
    }

    /** Back to the keys (ABC key, Insert, new field, keyboard hidden). Safe to call when the panel was never shown. */
    private fun closeVoicePanel() {
        val panel = voicePanel ?: return
        if (panel.visibility != View.VISIBLE) return
        voice?.panelClose()
        if (panelSearch) composer?.clear()
        panelSearch = false
        searchBuf.setLength(0)
        searchCaret = 0
        panel.reset()
        panel.setQuery("")
        panel.visibility = View.GONE
        if (shownPanel === panel) shownPanel = null
        val kb = keyboard ?: return
        kb.setStripVisible(true)
        kb.visibility = View.VISIBLE
    }

    /** The round mic button: listening -> stop and keep the text; idle -> listen again and add to the text. */
    private fun voicePanelMic() {
        val v = ensureVoice()
        if (panelSearch) endSearchTyping(clear = false) // the keys give way to the panel again
        if (v.isListening()) v.panelStop() else v.panelStart(voiceLang())
    }

    /** The text box was tapped at [offset]: the caret goes there, the keys come back below a smaller panel; a running recording ends first. */
    private fun startVoiceEditing(offset: Int) {
        voice?.let { if (it.isListening()) it.panelStop() }
        searchCaret = offset.coerceIn(0, searchBuf.length)
        startSearchTyping()
    }

    /** A tap in the text box while typing: the caret moves there (a word being composed becomes plain text first, which keeps the offsets valid). */
    private fun moveVoiceCaret(offset: Int) {
        flushSearchComposing()
        searchCaret = offset.coerceIn(0, searchBuf.length)
        updateSearchQuery()
    }

    private fun voicePanelInsert() {
        val panel = voicePanel ?: return
        flushSearchComposing()
        val text = searchBuf.toString().trim()
        closeVoicePanel()
        if (text.isNotEmpty()) commitToEditor(text)
        refreshAutoCaps()
        resyncAndPredict()
    }

    /** Recognized text joins the text box: a space between pieces, no limit on the length. */
    private fun appendToVoicePanel(text: String) {
        if (panelSearch) flushSearchComposing()
        val at = caretPos()
        val lead = at > 0 && !searchBuf[at - 1].isWhitespace()
        val trail = at < searchBuf.length && !searchBuf[at].isWhitespace()
        val piece = (if (lead) " " else "") + text + (if (trail) " " else "")
        searchBuf.insert(at, piece)
        searchCaret = at + piece.length
        updateSearchQuery()
    }

    // ---- emoji panel (E2, D56)

    override fun onEmojiKey() {
        commitComposingIfAny()
        showEmoji()
    }

    private val panelListener = object : EmojiPanelView.Listener {
        override fun onEmoji(text: String) = typeEmoji(text)

        override fun onBackspace() = this@MonsoonIme.onBackspace() // the panel's own backspace edits the editor (search typing uses the keys)

        override fun onClose() {
            closeEmoji()
            refreshAutoCaps()
            resyncAndPredict()
        }

        override fun onSearchTap() = startSearchTyping()

        override fun onSearchBack() = endSearchTyping(clear = true)

        override fun onSearchClear() = clearSearch()
    }

    private fun ensureEmojiLoader(): EmojiLoader = emojiLoader ?: EmojiLoader(this).also { emojiLoader = it }

    private fun ensureEmojiStore(): EmojiStore = emojiStore ?: EmojiStore(PrefsTextStore(this)).also { emojiStore = it }

    private fun ensureEmojiPanel(): EmojiPanelView? {
        emojiPanel?.let { return it }
        val r = root ?: return null
        val kb = keyboard ?: return null
        val p = EmojiPanelView(this, kb.palette)
        p.listener = panelListener
        p.visibility = View.GONE
        r.addView(p, 0, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, kb.desiredHeight()))
        emojiPanel = p
        return p
    }

    private fun setPanelHeight(h: Int) {
        val p = shownPanel ?: return
        val lp = p.layoutParams ?: return
        if (lp.height != h) {
            lp.height = h
            p.layoutParams = lp
        }
    }

    /** The panel takes the place of the keys, at the same height so the window does not jump. */
    private fun showEmoji() {
        val kb = keyboard ?: return
        val panel = ensureEmojiPanel() ?: return
        closeClipboard()
        kb.cancelAll()
        panelSearch = false
        searchBuf.setLength(0)
        composer?.clear()
        dropSuggestions()
        panel.setSearching(false)
        panel.setQuery("")
        panel.setPalette(kb.palette)
        val loader = ensureEmojiLoader()
        val ready = loader.result
        if (ready != null) {
            if (!panel.hasContent) panel.setContent(ready.data, ready.typeface, ensureEmojiStore())
        } else {
            loader.start(Runnable { applyEmojiContent() }) // the panel says "Loading emoji" until then
        }
        kb.setStripVisible(true)
        kb.visibility = View.GONE
        shownPanel = panel
        setPanelHeight(kb.desiredHeight())
        panel.visibility = View.VISIBLE
        panel.open()
    }

    private fun applyEmojiContent() {
        val r = emojiLoader?.result ?: return
        emojiPanel?.setContent(r.data, r.typeface, ensureEmojiStore())
    }

    /** Back to the keys (ABC key, new field, keyboard hidden). Safe to call when the panel was never shown. */
    private fun closeEmoji() {
        val panel = emojiPanel ?: return
        if (panel.visibility != View.VISIBLE) return
        if (panelSearch) composer?.clear()
        panelSearch = false
        searchBuf.setLength(0)
        panel.close()
        panel.setSearching(false)
        panel.setQuery("")
        panel.visibility = View.GONE
        if (shownPanel === panel) shownPanel = null
        val kb = keyboard ?: return
        kb.setStripVisible(true)
        kb.visibility = View.VISIBLE
    }

    /** One complete emoji (a ZWJ family, a flag, a skin-toned hand) goes into the editor as plain Unicode text in a single commit. */
    private fun typeEmoji(text: String) {
        val ic = currentInputConnection ?: return
        ic.commitText(text, 1)
        lastEditAt = SystemClock.uptimeMillis()
        if (predicting) {
            tracker.append(' ') // an emoji ends the word before it; its own characters are not words
            triggerRun.clear()
        }
        if (emojiRecents) emojiStore?.recordUse(text)
    }

    /** The search pill was tapped: the panel shrinks to the results row and the real keys come back below it. */
    private fun startSearchTyping() {
        val kb = keyboard ?: return
        val panel = searchPanel ?: return
        kb.cancelAll()
        panelSearch = true
        composer?.clear()
        dropSuggestions()
        kb.setStripVisible(false)
        kb.setPage(Layouts.PAGE_LETTERS)
        panel.setSearching(true)
        setPanelHeight(panel.compactHeight())
        kb.visibility = View.VISIBLE
        updateSearchQuery()
    }

    /** Leave the keys and show the results (or all emoji when the query is blank / was cleared) in the grid. */
    private fun endSearchTyping(clear: Boolean) {
        val kb = keyboard ?: return
        val panel = searchPanel ?: return
        flushSearchComposing()
        composer?.clear()
        if (clear) searchBuf.setLength(0)
        panelSearch = false
        panel.setSearching(false)
        panel.setQuery(searchBuf.toString())
        kb.visibility = View.GONE
        kb.setStripVisible(true)
        setPanelHeight(kb.desiredHeight())
    }

    private fun clearSearch() {
        searchBuf.setLength(0)
        composer?.clear()
        searchPanel?.setQuery("")
    }

    /** The Avro word in progress becomes plain query text. */
    private fun flushSearchComposing() {
        val comp = composer ?: return
        if (comp.isComposing) {
            val t = comp.text()
            val at = caretPos()
            searchBuf.insert(at, t)
            if (voicePanelUp()) searchCaret = at + t.length
            comp.clear()
        }
        updateSearchQuery()
    }

    private fun voicePanelUp() = voicePanel != null && shownPanel === voicePanel

    /** Where typing goes in [searchBuf]: at the caret in the voice panel's text box, at the end for every other panel. */
    private fun caretPos(): Int = if (voicePanelUp()) searchCaret.coerceIn(0, searchBuf.length) else searchBuf.length

    private fun updateSearchQuery() {
        val comp = composer
        val composing = if (comp != null && comp.isComposing) comp.text() else ""
        if (voicePanelUp()) { // text before the caret + the word being composed + text after it
            val at = caretPos()
            voicePanel?.setText(StringBuilder(searchBuf).insert(at, composing).toString(), at + composing.length)
            return
        }
        searchPanel?.setQuery(searchBuf.toString() + composing)
    }

    private fun searchText(text: String) {
        if (language == Language.AVRO) {
            val comp = ensureComposer()
            if (text.length == 1 && comp.accepts(text[0])) {
                if (searchBuf.length + comp.rawText().length < queryLimit()) {
                    comp.append(text[0])
                    updateSearchQuery()
                }
                return
            }
            flushSearchComposing()
        }
        if (searchBuf.length + text.length <= queryLimit()) {
            val at = caretPos()
            searchBuf.insert(at, text)
            if (voicePanelUp()) searchCaret = at + text.length
        }
        updateSearchQuery()
    }

    private fun queryLimit(): Int = if (voicePanel != null && shownPanel === voicePanel) VOICE_TEXT_LIMIT else MAX_QUERY

    private fun searchBackspace() {
        val comp = composer
        if (comp != null && comp.isComposing) {
            comp.deleteLast()
        } else if (voicePanelUp()) { // delete the letter before the caret
            val at = caretPos()
            if (at > 0) {
                val from = searchBuf.offsetByCodePoints(at, -1)
                searchBuf.delete(from, at)
                searchCaret = from
            }
        } else if (searchBuf.isNotEmpty()) {
            searchBuf.setLength(searchBuf.offsetByCodePoints(searchBuf.length, -1))
        }
        updateSearchQuery()
    }

    // ---- password picker (E1.14)

    override fun onVaultKey() {
        commitComposingIfAny()
        showVault()
    }

    private fun vaultPrefs(): VaultPrefs = vaultPrefsCache ?: VaultPrefs(this).also { vaultPrefsCache = it }

    /** Every vault call goes to the vault process on the client's own worker thread; the answers come back on the main thread. */
    private val vaultBackend = object : VaultPickerModel.Backend {
        private fun client() = VaultHub.client(this@MonsoonIme)
        override fun status(done: (Result<com.monsoonscript.vault.VaultStatus>) -> Unit) = client().async({ it.status() }, done)
        override fun info(done: (Result<com.monsoonscript.vault.VaultInfo>) -> Unit) = client().async({ it.info() }, done)
        override fun forApp(packageName: String, done: (Result<List<com.monsoonscript.vault.EntrySummary>>) -> Unit) = client().async({ it.forApp(packageName) }, done)
        override fun summaries(ids: List<String>, done: (Result<List<com.monsoonscript.vault.EntrySummary>>) -> Unit) = client().async({ it.summaries(ids) }, done)
        override fun list(groupId: String?, done: (Result<com.monsoonscript.vault.GroupListing>) -> Unit) = client().async({ it.list(groupId) }, done)
        override fun search(query: String, done: (Result<List<com.monsoonscript.vault.EntrySummary>>) -> Unit) = client().async({ it.search(query) }, done)
        override fun password(id: String, done: (Result<CharArray>) -> Unit) = client().async({ it.password(id) }, done)
        override fun otpCode(id: String, done: (Result<com.monsoonscript.vault.OtpCode?>) -> Unit) = client().async({ it.otpCode(id) }, done)
    }

    private val vaultHost = object : VaultPickerModel.Host {
        override val vaultConfigured: Boolean get() = vaultPrefs().hasVault
        override fun fieldIsPassword(): Boolean = this@MonsoonIme.fieldIsPassword
        override fun typeText(text: String) = typeFromVault(text)
        override fun typePassword(chars: CharArray) = typeFromVault(java.nio.CharBuffer.wrap(chars))
        override fun recentIds(): List<String> = vaultPrefs().recentIds
        override fun noteUsed(id: String) {
            val prefs = vaultPrefs()
            prefs.recentIds = VaultPickerModel.pushRecent(prefs.recentIds, id)
        }

        override fun describe(e: Throwable): String = VaultHub.message(this@MonsoonIme, e)
        override fun changed() {
            vaultPanel?.onModelChanged()
        }

        override fun closePicker() {
            closeVault()
            refreshAutoCaps()
            dropSuggestions() // no suggestions made from what the vault just typed
        }

        override fun after(delayMs: Long, block: () -> Unit) {
            mainHandler.postDelayed(block, delayMs) // the model drops it when the panel or entry changed meanwhile
        }
    }

    private val vaultListener = object : VaultPanelView.Listener {
        override fun onBackspace() = this@MonsoonIme.onBackspace()

        override fun onClose() {
            closeVault()
            refreshAutoCaps()
            resyncAndPredict()
        }

        override fun onSearchTap() = startSearchTyping()

        override fun onSearchBack() = endSearchTyping(clear = true)

        override fun onSearchClear() = clearSearch()

        override fun onUnlock() {
            if (vaultCardUp && SystemClock.uptimeMillis() - openVaultAfterUnlock < UNLOCK_OPEN_MS) return // already asking
            vaultCardUp = true
            vaultReturnPackage = vaultPackage
            vaultAfterUnlock = false
            openVaultAfterUnlock = SystemClock.uptimeMillis()
            unlockEndedAt = 0L
            CrashLog.note(this@MonsoonIme, "vault unlock card: started from $vaultPackage")
            launch(Intent(this@MonsoonIme, VaultUnlockActivity::class.java))
        }

        override fun onOpenApp() = launch(Intent(this@MonsoonIme, VaultMainActivity::class.java))

        override fun onLockNow() {
            VaultHub.client(this@MonsoonIme).async({ it.lock() }) { vaultPanel?.open(vaultPackage) }
        }

        override fun onRetry() {
            vaultPanel?.open(vaultPackage)
        }
    }

    /**
     * Text from the vault goes into the editor in one commit. It is not typed text: the word tracker forgets what it knew, and until the field is gone nothing typed
     * is learned any more (D42: a user name or password must never end up in the word lists, however a later re-reading of the field would see it).
     */
    private fun typeFromVault(text: CharSequence) {
        val ic = currentInputConnection ?: return
        ic.commitText(text, 1)
        lastEditAt = SystemClock.uptimeMillis()
        vaultLearnBlock = true
        tracker.reset(null)
        triggerRun.clear()
    }

    /** The unlock card ended ([VaultHub.onUnlockFinished]). Like the clipboard prompt: the keyboard and the picker come back by themselves. */
    private fun vaultUnlockFinished(ok: Boolean) {
        val pressed = openVaultAfterUnlock
        openVaultAfterUnlock = 0L
        vaultCardUp = false
        CrashLog.note(this, "vault unlock card: ended ok=$ok after ${SystemClock.uptimeMillis() - pressed} ms")
        if (pressed == 0L || SystemClock.uptimeMillis() - pressed >= UNLOCK_OPEN_MS) return // not ours, or too long ago
        vaultAfterUnlock = true
        unlockEndedAt = SystemClock.uptimeMillis()
        unlockOk = ok
        unlockRetries = 0
        // The card is gone now. The app's field usually starts again by itself a moment later (onStartInput / onStartInputView carry on from there); if it is already back, carry on now.
        if (vaultEditorIsBack()) {
            if (isInputViewShown) openPanelAfterUnlock() else showKeyboardAfterUnlock()
        }
    }

    private fun ensureVaultPanel(): VaultPanelView? {
        vaultPanel?.let { return it }
        val r = root ?: return null
        val kb = keyboard ?: return null
        val model = VaultPickerModel(vaultBackend, vaultHost)
        val p = VaultPanelView(this, kb.palette, model)
        p.listener = vaultListener
        p.visibility = View.GONE
        r.addView(p, 0, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, kb.desiredHeight()))
        vaultModel = model
        vaultPanel = p
        return p
    }

    /** The picker replaces the keys, at the same height so the window does not jump. */
    private fun showVault() {
        val kb = keyboard ?: return
        val panel = ensureVaultPanel() ?: return
        closeEmoji()
        closeClipboard()
        closeVoicePanel()
        kb.cancelAll()
        panelSearch = false
        searchBuf.setLength(0)
        composer?.clear()
        dropSuggestions()
        panel.setSearching(false)
        panel.setPalette(kb.palette)
        kb.setStripVisible(true)
        kb.visibility = View.GONE
        shownPanel = panel
        setPanelHeight(kb.desiredHeight())
        panel.visibility = View.VISIBLE
        panel.open(vaultPackage)
    }

    /** Back to the keys (ABC key, a password was typed, new field, keyboard hidden). Safe to call when the picker was never shown. */
    private fun closeVault() {
        val panel = vaultPanel ?: return
        if (panel.visibility != View.VISIBLE) return
        if (panelSearch) composer?.clear()
        panelSearch = false
        searchBuf.setLength(0)
        panel.close()
        panel.setSearching(false)
        panel.visibility = View.GONE
        if (shownPanel === panel) shownPanel = null
        val kb = keyboard ?: return
        kb.setStripVisible(true)
        kb.visibility = View.VISIBLE
    }

    // ---- clipboard panel (E3, D65)

    override fun onClipboardKey() {
        commitComposingIfAny()
        val hub = clipHub
        if (hub != null && !hub.lockOpen()) { // the clipboard lock (D71): the prompt first; the panel opens by itself when the keyboard comes back
            openClipboardAfterUnlock = SystemClock.uptimeMillis()
            vaultAfterUnlock = false
            unlockEndedAt = 0L
            launch(ClipUnlockActivity.intent(this))
            return
        }
        showClipboard()
    }

    /** Back to the keys from whichever panel is up. Safe when none is. */
    private fun closePanels() {
        closeEmoji()
        closeClipboard()
        closeVoicePanel()
        closeVault()
    }

    private val clipListener = object : ClipboardPanelView.Listener {
        override fun onBackspace() = this@MonsoonIme.onBackspace()

        override fun onClose() {
            closeClipboard()
            refreshAutoCaps()
            resyncAndPredict()
        }

        override fun onEmoji() {
            closeClipboard()
            showEmoji()
        }

        override fun onSearchTap() = startSearchTyping()

        override fun onSearchBack() = endSearchTyping(clear = true)

        override fun onSearchClear() = clearSearch()
    }

    /** What the clipboard panel asks of the service: type a clip, put it back on the clipboard, share it, edit it, open the settings. */
    private val clipHost = object : ClipPanelModel.Host {
        override fun paste(text: String) = pasteClip(text)

        override fun copyAgain(text: String) = copyToClipboard(text)

        override fun share(text: String) {
            val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text)
            launch(Intent.createChooser(send, null))
        }

        override fun pasteImage(clip: ClipItem) = pasteImageClip(clip)

        override fun copyAgainImage(clip: ClipItem) {
            val hub = clipHub ?: return
            val name = clip.image ?: return
            ClipCapture.copyImageWithoutRecording(this@MonsoonIme, hub.images.uri(name), clip.mime ?: "image/png")
        }

        override fun shareImage(clip: ClipItem) {
            val hub = clipHub ?: return
            ImageShare.chooser(hub.images, clip)?.let { launch(it) }
        }

        override fun shareClips(images: List<ClipItem>, text: String) {
            val hub = clipHub ?: return
            ImageShare.chooserMany(hub.images, images, text)?.let { launch(it) }
        }

        override fun edit(clipId: Long) = launch(ClipEditActivity.intent(this@MonsoonIme, clipId))

        override fun openSettings() = launch(Intent(this@MonsoonIme, ClipboardSettingsActivity::class.java))

        override fun columnsChanged(cols: Int) {
            clipHub?.prefs?.columns = cols
        }

        override fun changed() {
            clipPanel?.onModelChanged()
        }
    }

    private fun ensureClipPanel(): ClipboardPanelView? {
        clipPanel?.let { return it }
        val r = root ?: return null
        val kb = keyboard ?: return null
        val hub = clipHub ?: return null
        val model = ClipPanelModel(hub.store, clipHost)
        val p = ClipboardPanelView(this, kb.palette, model)
        p.listener = clipListener
        p.thumbs = hub.thumbs
        p.visibility = View.GONE
        r.addView(p, 0, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, kb.desiredHeight()))
        clipModel = model
        clipPanel = p
        return p
    }

    /** The clipboard replaces the keys, at the same height so the window does not jump. */
    private fun showClipboard() {
        val kb = keyboard ?: return
        val hub = clipHub ?: return
        if (!hub.lockOpen()) return
        val panel = ensureClipPanel() ?: return
        val model = clipModel ?: return
        closeEmoji()
        kb.cancelAll()
        panelSearch = false
        searchBuf.setLength(0)
        composer?.clear()
        dropSuggestions()
        hub.start() // reads the saved clips if that has not happened yet
        hub.lock.setPanelOpen(true)
        val prefs = hub.prefs
        panel.setStyle(prefs.previewLines, prefs.autoFit, prefs.compact, prefs.showTime)
        model.columns = prefs.columns
        model.historyEnabled = prefs.historyEnabled
        panel.setSearching(false)
        panel.setPalette(kb.palette)
        kb.setStripVisible(true)
        kb.visibility = View.GONE
        shownPanel = panel
        setPanelHeight(kb.desiredHeight())
        panel.visibility = View.VISIBLE
        panel.open()
    }

    /** Back to the keys (ABC key, new field, keyboard hidden). Safe to call when the panel was never shown. */
    private fun closeClipboard() {
        clipHub?.lock?.setPanelOpen(false)
        val panel = clipPanel ?: return
        if (panel.visibility != View.VISIBLE) return
        if (panelSearch) composer?.clear()
        panelSearch = false
        searchBuf.setLength(0)
        panel.close()
        panel.setSearching(false)
        panel.visibility = View.GONE
        if (shownPanel === panel) shownPanel = null
        val kb = keyboard ?: return
        kb.setStripVisible(true)
        kb.visibility = View.VISIBLE
    }

    /** A clip goes into the editor as plain text in one commit; pasted text is not a typed word, so it is not learned. */
    private fun pasteClip(text: String) {
        val ic = currentInputConnection ?: return
        ic.commitText(text, 1)
        lastEditAt = SystemClock.uptimeMillis()
        if (predicting) {
            tracker.append(' ')
            triggerRun.clear()
        }
        hideAfterPasteIfWanted()
    }

    private fun hideAfterPasteIfWanted() {
        if (clipHub?.prefs?.hideAfterPaste == true) {
            closeClipboard()
            refreshAutoCaps()
            resyncAndPredict()
        }
    }

    /**
     * A saved picture goes into the editor with commitContent, if the field says it takes that type. Where it does not (or the app refuses), the picture goes onto
     * the system clipboard instead, marked so the history does not record it again, and a message says so (D70).
     */
    private fun pasteImageClip(clip: ClipItem) {
        val hub = clipHub ?: return
        val name = clip.image ?: return
        val mime = clip.mime ?: "image/png"
        val uri = hub.images.uri(name)
        if (commitImage(uri, mime)) {
            lastEditAt = SystemClock.uptimeMillis()
            hideAfterPasteIfWanted()
        } else if (ClipCapture.copyImageWithoutRecording(this, uri, mime)) {
            Toast.makeText(this, R.string.clip_image_not_accepted, Toast.LENGTH_LONG).show()
        }
    }

    private fun commitImage(uri: Uri, mime: String): Boolean {
        val ic = currentInputConnection ?: return false
        val info = currentInputEditorInfo ?: return false
        if (!ImageMime.accepted(acceptedTypes(info), mime)) return false
        return try {
            val content = InputContentInfo(uri, ClipDescription("", arrayOf(mime)))
            ic.commitContent(content, InputConnection.INPUT_CONTENT_GRANT_READ_URI_PERMISSION, null)
        } catch (e: RuntimeException) {
            false
        }
    }

    /** The types the field takes from a keyboard: the field of the editor info, or where an older app put them (the extras of the support library). */
    private fun acceptedTypes(info: EditorInfo): Array<String>? {
        info.contentMimeTypes?.let { if (it.isNotEmpty()) return it }
        val extras = info.extras ?: return null
        return extras.getStringArray("androidx.core.view.inputmethod.EditorInfoCompat.CONTENT_MIME_TYPES")
            ?: extras.getStringArray("android.support.v13.view.inputmethod.EditorInfoCompat.CONTENT_MIME_TYPES")
    }

    /** "Copy again": the text goes back on the system clipboard, marked so the history does not record it a second time. */
    private fun copyToClipboard(text: String) {
        ClipCapture.copyWithoutRecording(this, text)
    }

    /** Start an activity from the service (a new task is required); an app that cannot handle the intent is ignored. */
    private fun launch(intent: Intent) {
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        try {
            startActivity(intent)
        } catch (e: RuntimeException) {
            // no app can handle it
        }
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
        const val RESYNC_CHARS = 80
        const val EMOJI_PRELOAD_MS = 3000L
        const val UNLOCK_OPEN_MS = 120_000L
        const val RETURN_MS = 10_000L // after the unlock prompt: how long the keyboard and its panel come back by themselves (E3.12)
        const val SHOW_RETRY_MS = 350L
        const val SHOW_RETRIES = 5
        const val CHIP_MARK = "\uD83D\uDCCB " // the clipboard sign in front of the recent copy chip (E3.10)
        const val MAX_SHORTCUT_CANDIDATES = 2 // of the strip's three slots; the third stays free for a word (E3.7)
        const val HELD_MS = 400L // how long the strip may keep painting dropped candidates while the next answer is computed (G4)
        const val MAX_QUERY = 40
        const val VOICE_TEXT_LIMIT = 4000 // the tap panel's text box
    }
}
