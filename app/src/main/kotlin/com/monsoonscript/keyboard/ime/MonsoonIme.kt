package com.monsoonscript.keyboard.ime

import android.content.SharedPreferences
import android.content.res.Configuration
import android.graphics.drawable.ColorDrawable
import android.inputmethodservice.InputMethodService
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
import android.view.inputmethod.InputMethodManager
import android.widget.LinearLayout
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
import com.monsoonscript.keyboard.view.EmojiPanelView
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

    /**
     * Strip settings (G1.3), cached by [applyPrefs]. They only decide what the strip shows: [predicting] (the word tracker, and with it
     * learning) is not touched, so turning the strip off does not stop "Learn from my typing". Avro dictionary choices are not affected.
     */
    private var showSuggestions = true
    private var nextWord = true
    private var typedFirst = true

    /**
     * Finished words are remembered (on the phone only, D42) when the owner left "Learn from my typing" on and the field allows it:
     * not in password/e-mail/URL fields, and not when the app asks for no personalized learning (incognito tabs and the like).
     */
    private var learnAllowed = false

    // ---- emoji panel (E2, D56): it replaces the keys; while searching it sits above the keys and typed text goes to the query, not the editor

    private var root: LinearLayout? = null
    private var emojiPanel: EmojiPanelView? = null
    private var emojiLoader: EmojiLoader? = null
    private var emojiStore: EmojiStore? = null
    private var emojiPreloadScheduled = false

    /** Typing into the emoji search bar: [searchBuf] holds the finished part of the query, the Avro composer the word in progress. */
    private var emojiSearch = false
    private val searchBuf = StringBuilder()

    /** Recents are not kept in password fields or when the app asks for no personalized learning (incognito). */
    private var emojiRecents = true

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
        showSuggestions = Prefs.showSuggestions(this)
        nextWord = Prefs.nextWord(this)
        typedFirst = Prefs.typedFirst(this)
        if (!showSuggestions && composer?.isComposing != true) dropSuggestions() // switched off: clear what is shown, never an Avro word in progress
    }

    override fun onCreate() {
        super.onCreate()
        language = Prefs.language(this) // where the keyboard was left (saved by name, unknown = English)
        lastBangla = Prefs.lastBangla(this)
        Prefs.of(this).registerOnSharedPreferenceChangeListener(prefsListener)
    }

    override fun onCreateInputView(): View {
        val v = KeyboardView(this)
        v.listener = this
        v.setLanguage(language.shortLabel, language.spaceLabel)
        keyboard = v
        // The keys and (once the emoji key is used) the emoji panel share this column; only one of them is visible at a time, except while searching.
        val r = LinearLayout(this)
        r.orientation = LinearLayout.VERTICAL
        r.addView(v, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        root = r
        emojiPanel = null
        emojiSearch = false
        searchBuf.setLength(0)
        applyPrefs()
        applySystemBars()
        return r
    }

    /** No full-screen extract UI in landscape (Gboard does not either). */
    override fun onEvaluateFullscreenMode(): Boolean = false

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        keyboard?.refreshTheme()
        applySystemBars()
        val kb = keyboard
        val panel = emojiPanel
        if (kb != null && panel != null) {
            panel.setPalette(kb.palette)
            if (panel.visibility == View.VISIBLE) setPanelHeight(if (emojiSearch) panel.compactHeight() else kb.desiredHeight())
        }
    }

    override fun onStartInputView(info: EditorInfo?, restarting: Boolean) {
        super.onStartInputView(info, restarting)
        if (!restarting) closeEmoji() // a new field starts on the letter keys (a restart of the same field keeps the panel)
        if (emojiSearch) flushSearchComposing() else composer?.clear()
        dropSuggestions()
        val inputType = info?.inputType ?: 0
        val cls = inputType and InputType.TYPE_MASK_CLASS
        composingAllowed = info != null && cls == InputType.TYPE_CLASS_TEXT && !isRawTextVariation(inputType)
        predictionAllowed = composingAllowed && (inputType and InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS) == 0
        val noLearning = ((info?.imeOptions ?: 0) and EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING) != 0
        learnAllowed = predictionAllowed && !noLearning && LearnSettings.enabled(this)
        emojiRecents = !noLearning && !isPasswordField(inputType)
        tracker.reset(null)
        hasSelection = false
        val v = keyboard ?: return
        applyPrefs()
        if (!emojiPreloadScheduled) { // read the emoji list and font in the background shortly after the first use, so the panel opens at once
            emojiPreloadScheduled = true
            v.postDelayed({ ensureEmojiLoader().start(null) }, EMOJI_PRELOAD_MS)
        }
        val numeric = cls == InputType.TYPE_CLASS_NUMBER || cls == InputType.TYPE_CLASS_PHONE || cls == InputType.TYPE_CLASS_DATETIME
        v.setPage(if (numeric) Layouts.PAGE_SYMBOLS else Layouts.PAGE_LETTERS)
        v.setLanguage(language.shortLabel, language.spaceLabel)
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
        dropSuggestions()
        predWorker?.flush() // save what was learned
        keyboard?.cancelAll()
        closeEmoji()
    }

    override fun onDestroy() {
        Prefs.of(this).unregisterOnSharedPreferenceChangeListener(prefsListener)
        worker?.stop()
        worker = null
        predWorker?.stop()
        predWorker = null
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
        if (emojiSearch) return // the composer holds the search word, not text in the editor
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

    /** The word ended (or was abandoned): no candidates and no pending search. */
    private fun dropSuggestions() {
        worker?.cancel()
        predWorker?.cancel()
        predSeq++ // an answer still on its way is stale now
        if (suggestions.isNotEmpty()) setSuggestions(emptyList())
    }

    // ---- prediction (English layout)

    /** Rebuild the tracker from the text before the cursor (the last few dozen characters are enough). */
    private fun resyncTracker() {
        tracker.reset(currentInputConnection?.getTextBeforeCursor(RESYNC_CHARS, 0))
    }

    private fun resyncAndPredict() {
        if (!predicting || composer?.isComposing == true) return
        resyncTracker()
        requestPrediction()
    }

    /** Ask the worker for completions of the word in progress, or for the next word when none is started. */
    private fun requestPrediction() {
        if (!predicting || !showSuggestions) return
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
        refreshAutoCaps()
        requestPrediction()
    }

    private fun setSuggestions(list: List<String>) {
        suggestions = list
        keyboard?.setSuggestions(list)
    }

    /** What is shown is already the final Bangla text, so finishing just makes it normal (not underlined) text. */
    private fun commitComposingIfAny() {
        val comp = composer ?: return
        if (!comp.isComposing) return
        if (emojiSearch) { // the word belongs to the search query, not to the editor
            flushSearchComposing()
            return
        }
        val shown = comp.text()
        currentInputConnection?.finishComposingText()
        comp.clear()
        dropSuggestions()
        lastEditAt = SystemClock.uptimeMillis()
        if (predicting) tracker.appendAll(shown) // plain text now: the word in progress for the tracker, finished by whatever comes next
    }

    private fun setLanguage(l: Language) {
        language = l
        if (l.isBangla) lastBangla = l
        Prefs.saveLanguage(this, l, lastBangla)
        keyboard?.setLanguage(l.shortLabel, l.spaceLabel)
        refreshAutoCaps()
    }

    // ---- KeyboardView.Listener

    override fun onText(text: String) {
        if (emojiSearch) {
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
                commitComposingIfAny()
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
            requestPrediction()
        }
        // getCursorCapsMode is a blocking call into the app: only ask when the text could start a new sentence/word.
        if (text == " " || text == "." || text == "!" || text == "?") refreshAutoCaps()
    }

    /** A strip suggestion was tapped (D23): it replaces the underlined word and a space follows. */
    override fun onSuggestion(word: String) {
        if (emojiSearch) return
        val ic = currentInputConnection ?: return
        val comp = composer
        if (comp == null || !comp.isComposing) {
            if (predicting) acceptPrediction(ic, word) // else: the word ended while the finger was down
            return
        }
        ic.commitText("$word ", 1) // one call: replaces the composing region, then the space
        comp.clear()
        dropSuggestions()
        lastEditAt = SystemClock.uptimeMillis()
        if (predicting) {
            tracker.appendAll("$word ") // finished: learned, and the context for the next-word predictions
            requestPrediction()
        }
    }

    override fun onBackspace() {
        if (emojiSearch) {
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
            if (selected || tracker.needsResync) resyncTracker() // more than one character went, or the cursor is in an earlier word now
            requestPrediction()
        }
    }

    override fun onEnter() {
        if (emojiSearch) { // Enter in the search bar shows the results in the grid
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
            requestPrediction()
        }
        refreshAutoCaps()
    }

    override fun onTab() {
        if (emojiSearch) return
        commitComposingIfAny()
        sendDownUpKeyEvents(KeyEvent.KEYCODE_TAB)
        if (predicting) {
            tracker.append('\t')
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
    override fun onLanguageToggle() {
        commitComposingIfAny()
        setLanguage(if (language.isBangla) Language.ENGLISH else lastBangla)
        dropSuggestions()
        resyncAndPredict() // English: read what is in the field now (the Bangla text was not tracked)
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
        val p = emojiPanel ?: return
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
        kb.cancelAll()
        emojiSearch = false
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
        if (emojiSearch) composer?.clear()
        emojiSearch = false
        searchBuf.setLength(0)
        panel.close()
        panel.setSearching(false)
        panel.setQuery("")
        panel.visibility = View.GONE
        val kb = keyboard ?: return
        kb.setStripVisible(true)
        kb.visibility = View.VISIBLE
    }

    /** One complete emoji (a ZWJ family, a flag, a skin-toned hand) goes into the editor as plain Unicode text in a single commit. */
    private fun typeEmoji(text: String) {
        val ic = currentInputConnection ?: return
        ic.commitText(text, 1)
        lastEditAt = SystemClock.uptimeMillis()
        if (predicting) tracker.append(' ') // an emoji ends the word before it; its own characters are not words
        if (emojiRecents) emojiStore?.recordUse(text)
    }

    /** The search pill was tapped: the panel shrinks to the results row and the real keys come back below it. */
    private fun startSearchTyping() {
        val kb = keyboard ?: return
        val panel = emojiPanel ?: return
        kb.cancelAll()
        emojiSearch = true
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
        val panel = emojiPanel ?: return
        flushSearchComposing()
        composer?.clear()
        if (clear) searchBuf.setLength(0)
        emojiSearch = false
        panel.setSearching(false)
        panel.setQuery(searchBuf.toString())
        kb.visibility = View.GONE
        kb.setStripVisible(true)
        setPanelHeight(kb.desiredHeight())
    }

    private fun clearSearch() {
        searchBuf.setLength(0)
        composer?.clear()
        emojiPanel?.setQuery("")
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
        emojiPanel?.setQuery(q)
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
        const val MAX_QUERY = 40
    }
}
