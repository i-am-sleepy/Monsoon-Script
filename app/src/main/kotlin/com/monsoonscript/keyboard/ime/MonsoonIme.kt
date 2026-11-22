package com.monsoonscript.keyboard.ime

import android.content.ClipDescription
import android.content.Intent
import android.content.SharedPreferences
import android.content.res.Configuration
import android.graphics.drawable.ColorDrawable
import android.inputmethodservice.InputMethodService
import android.net.Uri
import android.os.Build
import android.os.SystemClock
import android.text.InputType
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowInsetsController
import android.view.WindowManager
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputConnection
import android.view.inputmethod.InputContentInfo
import android.view.inputmethod.InputMethod
import android.view.inputmethod.InputMethodManager
import android.widget.LinearLayout
import android.widget.Toast
import com.monsoonscript.keyboard.R
import com.monsoonscript.keyboard.clipboard.ClipCapture
import com.monsoonscript.keyboard.clipboard.ClipEditActivity
import com.monsoonscript.keyboard.clipboard.ClipFormat
import com.monsoonscript.keyboard.clipboard.ClipHub
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
import com.monsoonscript.keyboard.predict.WordTracker
import com.monsoonscript.keyboard.prefs.Prefs
import com.monsoonscript.keyboard.view.ClipboardPanelView
import com.monsoonscript.keyboard.view.EmojiPanelView
import com.monsoonscript.keyboard.view.KeyboardView
import com.monsoonscript.keyboard.view.SearchablePanel
import com.monsoonscript.keyboard.voice.VoiceInput
import com.monsoonscript.keyboard.voice.VoiceLang
import java.io.IOException

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
    private val triggerRun = TriggerRun()

    /** The shortcuts offered right now. Their texts are the FIRST entries of [suggestions]: never use those for auto-correction. */
    private var shortcutHits: List<ClipShortcut> = emptyList()

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

    /** Recents are not kept in password fields or when the app asks for no personalized learning (incognito). */
    private var emojiRecents = true

    // ---- clipboard history (E3, D65): records what is copied while this keyboard is the active one; never in password / incognito fields
    private var clipHub: ClipHub? = null
    private var clipCapture: ClipCapture? = null
    private var clipPanel: ClipboardPanelView? = null
    private var clipModel: ClipPanelModel? = null

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
            kb.setAppearance(Prefs.theme(this), Prefs.numberRow(this), Prefs.emojiKey(this), Prefs.heightPortrait(this), Prefs.heightLandscape(this), Prefs.keyGap(this))
            applySystemBars() // the theme setting can change the keyboard color
            emojiPanel?.setPalette(kb.palette)
            clipPanel?.setPalette(kb.palette)
        }
        keyboard?.setTypingFeel(Prefs.vibration(this), Prefs.keySound(this), Prefs.soundSource(this) == Prefs.SOUND_BUILTIN, Prefs.longPressMs(this))
        showSuggestions = Prefs.showSuggestions(this)
        nextWord = Prefs.nextWord(this)
        typedFirst = Prefs.typedFirst(this)
        autoShow = Prefs.autoShow(this)
        volumeCursor = Prefs.volumeCursor(this)
        if (!showSuggestions && composer?.isComposing != true) dropSuggestions() // switched off: clear what is shown, never an Avro word in progress
    }

    override fun onCreate() {
        super.onCreate()
        language = Prefs.language(this) // where the keyboard was left (saved by name, unknown = English)
        lastBangla = Prefs.lastBangla(this)
        autoShow = Prefs.autoShow(this) // asked before the keyboard view exists
        volumeCursor = Prefs.volumeCursor(this)
        Prefs.of(this).registerOnSharedPreferenceChangeListener(prefsListener)
        val hub = ClipHub.get(this)
        clipHub = hub
        hub.start()
        clipCapture = ClipCapture(this, hub).also { it.start() }
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
        emojiPanel = null
        clipPanel = null
        clipModel = null
        shownPanel = null
        panelSearch = false
        searchBuf.setLength(0)
        applyPrefs()
        applySystemBars()
        return r
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
        if (!autoShow && !configChange && (flags and InputMethod.SHOW_EXPLICIT) == 0) return false
        return super.onShowInputRequested(flags, configChange)
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
        sendDownUpKeyEvents(if (left) KeyEvent.KEYCODE_DPAD_LEFT else KeyEvent.KEYCODE_DPAD_RIGHT)
        lastEditAt = 0L // the selection change that follows is a real move, not an echo: [onUpdateSelection] reads the editor again
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
        if (!restarting) closePanels() // a new field starts on the letter keys (a restart of the same field keeps the panel)
        if (panelSearch) flushSearchComposing() else composer?.clear()
        dropSuggestions()
        val inputType = info?.inputType ?: 0
        val cls = inputType and InputType.TYPE_MASK_CLASS
        composingAllowed = info != null && cls == InputType.TYPE_CLASS_TEXT && !isRawTextVariation(inputType)
        predictionAllowed = composingAllowed && (inputType and InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS) == 0
        val noLearning = ((info?.imeOptions ?: 0) and EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING) != 0
        learnAllowed = predictionAllowed && !noLearning && LearnSettings.enabled(this)
        emojiRecents = !noLearning && !isPasswordField(inputType)
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
    }

    override fun onFinishInputView(finishingInput: Boolean) {
        super.onFinishInputView(finishingInput)
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
    }

    override fun onDestroy() {
        clipCapture?.stop()
        clipCapture = null
        Prefs.of(this).unregisterOnSharedPreferenceChangeListener(prefsListener)
        worker?.stop()
        worker = null
        predWorker?.stop()
        predWorker = null
        voice?.cancel()
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
        w.request(seq, mode, tracker.typed, prefix, w1, tracker.w2, tracker.atSentenceStart, typedFirst)
    }

    private fun learnWord(prev: String?, word: String) {
        if (!learnAllowed) return
        if (word.length == 1 && word[0] < '\u0980') return // single Latin letters are not worth remembering
        val w = predWorker ?: PredictionWorker(this, ::onPredictionResult).also { predWorker = it }
        w.learn(prev, word)
    }

    private fun onPredictionResult(r: PredictionWorker.Result) {
        if (r.seq != predSeq || !predicting || !showSuggestions) return
        setSuggestions(r.words)
    }

    /** A prediction was tapped: it replaces the word in progress (checked against the editor first) and a space follows. */
    private fun acceptPrediction(ic: InputConnection, word: String) {
        val typed = tracker.typed
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
        keyboard?.setSuggestions(shown)
    }

    // ---- text shortcuts (E3.7, D68)

    /** Shortcuts are offered where the strip may show candidates and the characters are committed as typed (not while Avro composes). */
    private fun shortcutsActive(): Boolean =
        answerFollows && !avroActive && !panelSearch && clipHub?.store?.shortcuts?.isNotEmpty() == true

    /** Match what was typed since the last space against the triggers; when the offer changed, repaint the strip. Called before every prediction request. */
    private fun refreshShortcuts() {
        val store = clipHub?.store
        var hits: List<ClipShortcut> = emptyList()
        if (store != null && shortcutsActive()) {
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

    /** The strip list: the shortcut texts first (one line each), then what the word engines suggested. */
    private fun withShortcuts(words: List<String>): List<String> {
        if (shortcutHits.isEmpty()) return words
        val out = ArrayList<String>(words.size + shortcutHits.size)
        for (h in shortcutHits) {
            val label = ClipFormat.stripLabel(h.text)
            if (label.isNotEmpty() && label !in out) out.add(label)
        }
        for (w in words) if (w !in out) out.add(w)
        return out
    }

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
        if (acceptShortcut(word)) return
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

        override fun voiceListening(on: Boolean) {
            keyboard?.setMicActive(on)
        }
    }

    private fun ensureVoice(): VoiceInput = voice ?: VoiceInput(this, voiceHost).also { voice = it }

    /** The language to listen for follows the active layout, it is never detected (D66). */
    private fun voiceLang() = if (language.isBangla) VoiceLang.BANGLA else VoiceLang.ENGLISH

    override fun onMicTap() {
        Toast.makeText(this, R.string.voice_hint_hold, Toast.LENGTH_SHORT).show() // the tap panel comes in the next G5 step
    }

    override fun onMicHoldStart() {
        commitComposingIfAny()
        ensureVoice().holdStart(voiceLang())
    }

    override fun onMicHoldEnd(cancel: Boolean) {
        voice?.holdEnd(cancel)
    }

    /** Finished speech goes into the editor at the cursor as plain text, with a space in front when it follows a word. */
    private fun typeVoiceText(text: String) {
        val ic = currentInputConnection ?: return
        if (panelSearch) return // the keys belong to a panel's search bar, not to the editor
        commitComposingIfAny()
        val before = ic.getTextBeforeCursor(1, 0)
        val last = if (before.isNullOrEmpty()) ' ' else before[before.length - 1]
        val lead = if (Character.isLetterOrDigit(last) || last in ".,!?;:\u0964") " " else ""
        ic.commitText(lead + text, 1)
        lastEditAt = SystemClock.uptimeMillis()
        if (predicting) resyncTracker()
        refreshAutoCaps()
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
            searchBuf.append(comp.text())
            comp.clear()
        }
        updateSearchQuery()
    }

    private fun updateSearchQuery() {
        val comp = composer
        val q = if (comp != null && comp.isComposing) searchBuf.toString() + comp.text() else searchBuf.toString()
        searchPanel?.setQuery(q)
    }

    private fun searchText(text: String) {
        if (language == Language.AVRO) {
            val comp = ensureComposer()
            if (text.length == 1 && comp.accepts(text[0])) {
                if (searchBuf.length + comp.rawText().length < MAX_QUERY) {
                    comp.append(text[0])
                    updateSearchQuery()
                }
                return
            }
            flushSearchComposing()
        }
        if (searchBuf.length + text.length <= MAX_QUERY) searchBuf.append(text)
        updateSearchQuery()
    }

    private fun searchBackspace() {
        val comp = composer
        if (comp != null && comp.isComposing) {
            comp.deleteLast()
        } else if (searchBuf.isNotEmpty()) {
            searchBuf.setLength(searchBuf.offsetByCodePoints(searchBuf.length, -1))
        }
        updateSearchQuery()
    }

    // ---- clipboard panel (E3, D65)

    override fun onClipboardKey() {
        commitComposingIfAny()
        showClipboard()
    }

    /** Back to the keys from whichever panel is up. Safe when none is. */
    private fun closePanels() {
        closeEmoji()
        closeClipboard()
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
        val panel = ensureClipPanel() ?: return
        val model = clipModel ?: return
        closeEmoji()
        kb.cancelAll()
        panelSearch = false
        searchBuf.setLength(0)
        composer?.clear()
        dropSuggestions()
        hub.start() // reads the saved clips if that has not happened yet
        val prefs = hub.prefs
        panel.setStyle(prefs.previewLines, prefs.compact, prefs.showTime)
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
        const val MAX_SHORTCUT_CANDIDATES = 2 // of the strip's three slots; the third stays free for a word (E3.7)
        const val HELD_MS = 400L // how long the strip may keep painting dropped candidates while the next answer is computed (G4)
        const val MAX_QUERY = 40
    }
}
