package com.monsoonscript.keyboard.view

import android.content.Context
import android.content.res.Configuration
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Typeface
import android.graphics.RectF
import android.media.AudioManager
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.os.VibrationEffect
import android.os.Vibrator
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import kotlin.math.abs
import com.monsoonscript.keyboard.lang.Language
import com.monsoonscript.keyboard.layout.Codes
import com.monsoonscript.keyboard.layout.Key
import com.monsoonscript.keyboard.layout.Kind
import com.monsoonscript.keyboard.layout.Layouts

/**
 * The whole keyboard drawn on one Canvas: shortcut strip on top, four key rows below.
 * Hot paths (touch, draw) allocate nothing: paints, paths and rects are built once.
 */
class KeyboardView(context: Context) : View(context) {

    interface Listener {
        fun onText(text: String)
        fun onBackspace()
        fun onEnter()
        fun onTab()
        fun onGlobe()
        fun onGlobeLong()

        /** Language key tap, or a swipe across the space bar: switch English <-> the last Bangla layout. */
        fun onLanguageToggle()

        /** A layout was picked in the popup that a long press on the language key opens (L4, D19). */
        fun onLanguagePicked(language: Language) {}

        /** A suggestion in the strip was tapped (D23): commit that word, plus a space. */
        fun onSuggestion(word: String) {}

        /** The emoji key in the strip was tapped (E2): the IME swaps the keys for the emoji panel. */
        fun onEmojiKey() {}
    }

    var listener: Listener? = null

    /** Keyboard background color; the IME paints its window and the navigation bar area with it. */
    val backgroundColor: Int get() = pal.bg
    val isLightTheme: Boolean get() = pal === LIGHT

    /** The colors in use; the emoji panel (E2) draws with the same ones so the two always match. */
    val palette: Palette get() = pal

    private val density = resources.displayMetrics.density
    private fun dp(v: Float) = v * density

    // ---- appearance (G1.5): set by the IME from the settings (and by the preview on the Appearance page); declared before init, which reads themeMode
    private var themeMode = THEME_SYSTEM
    private var numberRow = false
    private var heightPortrait = 100
    private var heightLandscape = 100
    private var keyGapDp = 3

    // ---- geometry (px)
    private var stripH = dp(44f)
    private var rowH = dp(52f)
    private val padX = dp(4f)
    private val padTop = dp(4f)
    private val padBottom = dp(8f)
    private var gapX = dp(3f)
    private var gapY = dp(4f)
    private val radius = dp(8f)
    private var rowsTop = 0f
    private var rowsBottom = 0f

    // ---- layout results, per page
    private var keys: Array<Array<Key>> = emptyArray()
    private var rects: Array<Array<RectF>> = emptyArray()

    // ---- state
    private var stripShown = true // false while the emoji search bar sits above the keys (E2): no strip, no icons, no suggestions
    private var page = Layouts.PAGE_LETTERS
    private var shiftState = SHIFT_OFF
    private var lastShiftTap = 0L

    // ---- language (the IME owns the state and pushes it in; the letters page and the labels follow it)
    private var language = Language.ENGLISH
    private var langLabel = "EN"
    private var spaceLabel = "English"
    private var languageKeyEnabled = true
    private var spaceSwipeEnabled = true

    // ---- language popup (L4, D19): a long press on the strip's language key lists the layouts, drawn over the keys below the strip
    private val popupLanguages = Language.values()
    private var popupOpen = false
    private var popupHeld = false // the finger that opened it is still down; lifting it off the key leaves the list open
    private var popupSel = -1
    private var popupDownOutside = false
    private val popupRect = RectF()
    private var popupRowH = 0f

    // ---- pointers (multi-touch)
    private val ptrId = IntArray(MAX_PTR) { -1 }
    private val ptrKey = IntArray(MAX_PTR) { -1 }
    private val ptrLong = BooleanArray(MAX_PTR)
    private val ptrHint = BooleanArray(MAX_PTR) // a long press armed the key's hint: its bubble shows the hint, which is typed on release (G1.6)
    private val downX = FloatArray(MAX_PTR)
    private val swipeArmed = BooleanArray(MAX_PTR)
    private val repeatCount = IntArray(MAX_PTR)
    private val uiHandler = Handler(Looper.getMainLooper())
    private val longPressRunnable = Array(MAX_PTR) { slot -> Runnable { onLongPress(slot) } }
    private val repeatRunnable = Array(MAX_PTR) { slot -> Runnable { onRepeat(slot) } }

    // Suggestion strip (D23): while a Bangla word is being typed, the first SUG_SLOTS candidates replace the strip icons.
    private val sug = arrayOfNulls<String>(SUG_SLOTS)
    private var sugCount = 0
    private val ptrInSug = BooleanArray(MAX_PTR) // this finger went down in the suggestion strip
    private val ptrSug = IntArray(MAX_PTR) { -1 } // slot under that finger, -1 for none
    // Gboard-style arrow at the left of the strip whenever suggestions exist: tap = swap suggestions <-> the icon row.
    private val arrowW = dp(32f)
    private var iconsPinned = false
    private val ptrArrow = BooleanArray(MAX_PTR) // this finger went down on the arrow
    private var stripLeftCount = 0 // the first entries of each page's key list are the left-hand strip icons
    private var stripShift = 0f // how far those icons are moved right to make room for the arrow
    private val showSug: Boolean get() = sugCount > 0 && !iconsPinned

    // ---- paint
    private val tmp = RectF()
    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val letterPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER }
    private val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER }
    private val hintPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.RIGHT }
    private val bubblePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER }
    private val stripTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        typeface = Typeface.DEFAULT_BOLD
    }
    private val popupPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.LEFT }
    private val popupBorderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = dp(1f)
    }
    private val sugPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER }
    private val sugBoldPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        typeface = Typeface.DEFAULT_BOLD
    }
    private val iconPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
        strokeWidth = 0.09f // unit-square coordinates; the canvas is scaled to the icon size
    }
    private var letterBase = 0f
    private var labelBase = 0f
    private var bubbleBase = 0f
    private var stripBase = 0f
    private var sugBase = 0f
    private var hintTop = 0f
    private var popupBase = 0f
    private var pal = DARK

    private val iconShift = Path().apply {
        moveTo(.5f, .12f); lineTo(.92f, .54f); lineTo(.68f, .54f); lineTo(.68f, .88f)
        lineTo(.32f, .88f); lineTo(.32f, .54f); lineTo(.08f, .54f); close()
    }
    private val iconBackspace = Path().apply {
        moveTo(.34f, .18f); lineTo(.94f, .18f); lineTo(.94f, .82f); lineTo(.34f, .82f); lineTo(.04f, .5f); close()
    }
    private val iconBackspaceX = Path().apply {
        moveTo(.52f, .38f); lineTo(.76f, .62f); moveTo(.76f, .38f); lineTo(.52f, .62f)
    }
    private val iconChevron = Path().apply { moveTo(.62f, .16f); lineTo(.36f, .5f); lineTo(.62f, .84f) }
    private val iconEnter = Path().apply {
        moveTo(.86f, .20f); lineTo(.86f, .58f); lineTo(.22f, .58f)
        moveTo(.42f, .38f); lineTo(.22f, .58f); lineTo(.42f, .78f)
    }

    init {
        refreshTheme()
        updateTextSizes()
    }

    // ---------------------------------------------------------------- public API

    fun setPage(p: Int) {
        if (p == page) return
        cancelAll()
        page = p
        invalidate()
    }

    /** Hide or show the shortcut strip. Hidden (E2) the keys move up and the view gets shorter by the strip's height. */
    fun setStripVisible(v: Boolean) {
        if (v == stripShown) return
        stripShown = v
        cancelAll()
        if (width > 0) computeLayout(width)
        requestLayout()
        invalidate()
    }

    /** The height this view measures to with the strip shown; the emoji panel takes the same height so the window does not jump. */
    fun desiredHeight(): Int = (stripHeight() + currentRowH() * rowSlots() + padTop + padBottom).toInt()

    /**
     * [theme]: 0 follow the phone, 1 light, 2 dark. [numberRow] adds the digits row above the letters (the symbol pages keep the same total
     * height with taller keys). [heightPortrait] / [heightLandscape] are percent of the standard key height, [keyGapDp] the space between keys.
     */
    fun setAppearance(theme: Int, numberRow: Boolean, heightPortrait: Int, heightLandscape: Int, keyGapDp: Int) {
        if (theme == themeMode && numberRow == this.numberRow && heightPortrait == this.heightPortrait &&
            heightLandscape == this.heightLandscape && keyGapDp == this.keyGapDp
        ) return
        themeMode = theme
        this.numberRow = numberRow
        this.heightPortrait = heightPortrait
        this.heightLandscape = heightLandscape
        this.keyGapDp = keyGapDp
        refreshTheme()
        updateMetrics()
        cancelAll()
        if (width > 0) computeLayout(width)
        requestLayout()
        invalidate()
    }

    private fun isLandscape(): Boolean = resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE

    private fun rowSlots(): Int = if (numberRow) 5 else 4

    private fun stripHeight(): Float = dp(if (isLandscape()) 38f else 44f)

    /** One key row: the standard height scaled by the setting, but never so tall that the keyboard takes more than 65% of the screen. */
    private fun currentRowH(): Float {
        val land = isLandscape()
        val base = dp(if (land) 42f else 52f) * (if (land) heightLandscape else heightPortrait) / 100f
        val room = (resources.displayMetrics.heightPixels * 0.65f - stripHeight() - padTop - padBottom) / rowSlots()
        return minOf(base, maxOf(room, dp(30f)))
    }

    /** Row height, strip height, key gaps (never more than a fifth of a key) and text sizes for the current settings and orientation. */
    private fun updateMetrics() {
        stripH = stripHeight()
        rowH = currentRowH()
        gapX = minOf(dp(keyGapDp.toFloat()), resources.displayMetrics.widthPixels / 50f)
        gapY = minOf(dp(keyGapDp + 1f), rowH * 0.2f)
        updateTextSizes()
    }

    /** The rows of page [p]; the letters page gets the number row on top when it is on. */
    private fun rowsOf(p: Int): Array<Array<Key>> {
        val rows = Layouts.pages(language)[p] // the letters page follows the language (Probhat, National), the symbol pages are shared
        return if (numberRow && p == Layouts.PAGE_LETTERS) arrayOf(Layouts.numberRow) + rows else rows
    }

    /**
     * What a long press on [k] types, and its corner hint: with Shift on the Shift hint (Shift+AltGr of a fixed Bangla layout, D59), else the
     * plain one. The digit hints of the top letter row are dropped while the number row shows the digits itself.
     */
    private fun hintOf(k: Key): String? {
        val h = (if (shiftState != SHIFT_OFF) k.hintShifted else k.hint) ?: return null
        return if (numberRow && page == Layouts.PAGE_LETTERS && h.length == 1 && h[0] in '0'..'9') null else h
    }

    /** What is painted for that hint: ZWNJ / ZWJ by name, a vowel sign with a dotted circle (see [Key.drawable]). */
    private fun hintDrawnOf(k: Key): String? =
        if (hintOf(k) == null) null else if (shiftState != SHIFT_OFF) k.hintDrawnShifted else k.hintDrawn

    /**
     * The typing language: its letters page (Probhat and National have their own, D58 / D59), the label of the strip's language key and
     * the space bar label (D60). A different layout starts with Shift off, so a locked Shift cannot leak into another script.
     */
    fun setLanguage(l: Language) {
        if (l == language) return
        val newLayout = Layouts.pages(l) !== Layouts.pages(language)
        language = l
        langLabel = l.shortLabel
        spaceLabel = l.spaceLabel
        if (newLayout) {
            shiftState = SHIFT_OFF
            cancelAll()
            if (width > 0) computeLayout(width)
        }
        invalidate()
    }

    /** The two switches from D15/D19 (settings wire them in T10). Hiding the language key re-flows the strip. */
    fun setLanguageOptions(languageKey: Boolean, spaceSwipe: Boolean) {
        spaceSwipeEnabled = spaceSwipe
        if (languageKey != languageKeyEnabled) {
            languageKeyEnabled = languageKey
            if (width > 0) computeLayout(width)
            cancelAll()
        }
    }

    // ---- typing feel (G1.4): set by the IME from the settings; the touch code only reads these fields and allocates nothing

    private var vibration = VIB_NORMAL
    private var keySound = false
    private var longPressMs = LONG_PRESS_MS
    private var tapEffect: VibrationEffect? = null
    private var longEffect: VibrationEffect? = null
    private val vibrator: Vibrator? by lazy { context.getSystemService(Vibrator::class.java) }
    private val audio: AudioManager? by lazy { context.getSystemService(AudioManager::class.java) }

    /**
     * [vibration]: 0 Off, 1 Light, 2 Normal (Android's own keyboard tap, the behaviour before the setting existed), 3 Strong. Light and Strong
     * drive the vibration motor directly (short pulses, amplitude when the motor supports it). [keySound] plays Android's key click (it follows
     * the system "touch sounds" switch). [longPressMs] is the hold time of a long press.
     */
    fun setTypingFeel(vibration: Int, keySound: Boolean, longPressMs: Long) {
        this.vibration = vibration.coerceIn(VIB_OFF, VIB_STRONG)
        this.keySound = keySound
        this.longPressMs = longPressMs
        tapEffect = null
        longEffect = null
        if (this.vibration == VIB_LIGHT || this.vibration == VIB_STRONG) {
            val light = this.vibration == VIB_LIGHT
            val amplitude = vibrator?.hasAmplitudeControl() == true
            tapEffect = pulse(if (light) 10L else 30L, if (light) 60 else 255, amplitude)
            longEffect = pulse(if (light) 16L else 45L, if (light) 90 else 255, amplitude)
        }
    }

    private fun pulse(ms: Long, amplitude: Int, hasAmplitude: Boolean): VibrationEffect =
        VibrationEffect.createOneShot(ms, if (hasAmplitude) amplitude else VibrationEffect.DEFAULT_AMPLITUDE)

    private fun tap() {
        when (vibration) {
            VIB_OFF -> Unit
            VIB_NORMAL -> performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
            else -> buzz(tapEffect)
        }
    }

    private fun longTap() {
        when (vibration) {
            VIB_OFF -> Unit
            VIB_NORMAL -> performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
            else -> buzz(longEffect)
        }
    }

    private fun buzz(effect: VibrationEffect?) {
        if (effect == null) return
        try {
            vibrator?.vibrate(effect)
        } catch (e: SecurityException) {
            // no vibrate permission: stay silent, typing must never fail because of feedback
        }
    }

    private fun click(code: Int) {
        if (!keySound) return
        val effect = when (code) {
            Codes.SPACE -> AudioManager.FX_KEYPRESS_SPACEBAR
            Codes.BACKSPACE -> AudioManager.FX_KEYPRESS_DELETE
            Codes.ENTER -> AudioManager.FX_KEYPRESS_RETURN
            else -> AudioManager.FX_KEYPRESS_STANDARD
        }
        audio?.playSoundEffect(effect)
    }

    /** Called by the IME after it checked the editor's auto-capitalization mode. Never overrides caps lock. */
    fun applyAutoCaps(on: Boolean) {
        if (shiftState == SHIFT_LOCKED) return
        val s = if (on) SHIFT_ONCE else SHIFT_OFF
        if (s != shiftState) {
            shiftState = s
            invalidate()
        }
    }

    /**
     * Candidates for the word being typed or the next word (D23, D41). The first [SUG_SLOTS] are shown in the strip next to a small
     * arrow and the icons hide; the arrow swaps them for the icon row until the candidates change again. An empty list brings the plain
     * icon row back (no arrow). Cheap when nothing changed, so the IME may call it on every keystroke.
     */
    fun setSuggestions(list: List<String>) {
        val n = if (list.size < SUG_SLOTS) list.size else SUG_SLOTS
        var changed = n != sugCount
        for (i in 0 until SUG_SLOTS) {
            val w = if (i < n) list[i] else null
            if (sug[i] != w) {
                sug[i] = w
                changed = true
            }
        }
        if (!changed) return
        val wasShown = sugCount > 0
        sugCount = n
        iconsPinned = false // new candidates: show them, the icons were only a peek
        if ((n > 0) != wasShown) placeStrip(if (n > 0) arrowW else 0f)
        invalidate() // a finger already down on a slot keeps its press: the tap delivers whatever the slot shows on release
    }

    fun cancelAll() {
        for (s in 0 until MAX_PTR) {
            cancelTimers(s)
            ptrId[s] = -1
            ptrKey[s] = -1
            ptrLong[s] = false
            ptrHint[s] = false
            swipeArmed[s] = false
            ptrInSug[s] = false
            ptrSug[s] = -1
            ptrArrow[s] = false
        }
        popupOpen = false
        popupHeld = false
        popupSel = -1
        invalidate()
    }

    // ---------------------------------------------------------------- measure / layout

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        updateMetrics()
        val h = ((if (stripShown) stripH else 0f) + rowH * rowSlots() + padTop + padBottom).toInt()
        setMeasuredDimension(MeasureSpec.getSize(widthMeasureSpec), h)
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        computeLayout(w)
    }

    private fun computeLayout(w: Int) {
        val iconCell = dp(44f)
        rowsTop = (if (stripShown) stripH else 0f) + padTop
        rowsBottom = rowsTop + rowH * rowSlots()
        val outKeys = ArrayList<Array<Key>>()
        val outRects = ArrayList<Array<RectF>>()
        for (p in 0 until Layouts.PAGE_COUNT) {
            val kl = ArrayList<Key>()
            val rl = ArrayList<RectF>()
            var x = padX
            if (stripShown) {
                for (k in Layouts.stripLeft) {
                    if (k.code == Codes.LANG && !languageKeyEnabled) continue
                    val cw = iconCell * k.weight
                    kl.add(k); rl.add(RectF(x, 0f, x + cw, stripH)); x += cw
                }
            }
            stripLeftCount = kl.size
            if (stripShown) {
                var rx = w - padX
                for (k in Layouts.stripRight.reversed()) {
                    rx -= iconCell
                    kl.add(k); rl.add(RectF(rx, 0f, rx + iconCell, stripH))
                }
            }
            val rows = rowsOf(p)
            val rh = rowH * rowSlots() / rows.size // the same total height on every page: fewer rows = taller rows
            for ((r, row) in rows.withIndex()) {
                val total = row.sumOf { it.weight.toDouble() }.toFloat()
                val unit = (w - 2 * padX) / total
                val top = rowsTop + r * rh
                var kx = padX
                for (k in row) {
                    val kw = k.weight * unit
                    if (k.kind != Kind.SPACER) {
                        kl.add(k); rl.add(RectF(kx, top, kx + kw, top + rh))
                    }
                    kx += kw
                }
            }
            outKeys.add(kl.toTypedArray())
            outRects.add(rl.toTypedArray())
        }
        keys = outKeys.toTypedArray()
        rects = outRects.toTypedArray()
        stripShift = 0f
        placeStrip(if (sugCount > 0) arrowW else 0f)
    }

    /** Move the left-hand strip icons so that their offset from the edge is [shift]: room for the arrow while suggestions exist. */
    private fun placeStrip(shift: Float) {
        val d = shift - stripShift
        if (d == 0f) return
        stripShift = shift
        for (p in rects.indices) for (i in 0 until stripLeftCount.coerceAtMost(rects[p].size)) rects[p][i].offset(d, 0f)
    }

    private fun updateTextSizes() {
        letterPaint.textSize = rowH * 0.40f
        labelPaint.textSize = rowH * 0.27f
        hintPaint.textSize = rowH * 0.22f
        popupPaint.textSize = dp(16f)
        popupBase = baseline(popupPaint)
        bubblePaint.textSize = rowH * 0.62f
        stripTextPaint.textSize = stripH * 0.36f
        sugPaint.textSize = stripH * 0.40f
        sugBoldPaint.textSize = stripH * 0.40f
        sugBase = baseline(sugPaint)
        letterBase = baseline(letterPaint)
        labelBase = baseline(labelPaint)
        bubbleBase = baseline(bubblePaint)
        stripBase = baseline(stripTextPaint)
        hintTop = dp(3f) - hintPaint.fontMetrics.ascent
    }

    private fun baseline(p: Paint): Float {
        val fm = p.fontMetrics
        return -(fm.ascent + fm.descent) / 2f
    }

    // ---------------------------------------------------------------- theme

    fun refreshTheme() {
        val night = when (themeMode) {
            THEME_LIGHT -> false
            THEME_DARK -> true
            else -> (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
        }
        pal = if (night) DARK else LIGHT
        letterPaint.color = pal.text
        labelPaint.color = pal.text
        hintPaint.color = pal.textMuted
        bubblePaint.color = pal.text
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        refreshTheme()
        requestLayout()
        invalidate()
    }

    // ---------------------------------------------------------------- drawing

    override fun onDraw(canvas: Canvas) {
        canvas.drawColor(pal.bg)
        if (page >= keys.size) return
        val ks = keys[page]
        val rs = rects[page]
        for (i in ks.indices) {
            if (showSug && ks[i].kind == Kind.STRIP) continue // the icons give way to the suggestions
            drawKey(canvas, ks[i], rs[i], isPressedIndex(i))
        }
        if (stripShown && sugCount > 0) {
            drawArrow(canvas)
            if (showSug) drawSuggestions(canvas)
        }
        for (s in 0 until MAX_PTR) {
            val i = ptrKey[s]
            if (i < 0 || i >= ks.size) continue
            if (ptrHint[s]) {
                val hint = hintDrawnOf(ks[i])
                if (hint != null) drawBubble(canvas, hint, rs[i]) // held long enough: the bubble shows the hint, typed on release
            } else if (!ptrLong[s] && ks[i].kind == Kind.CHAR) {
                drawBubble(canvas, if (shiftState != SHIFT_OFF) ks[i].faceShifted else ks[i].face, rs[i])
            }
        }
        if (popupOpen) drawLanguagePopup(canvas)
    }

    /** The list of layouts (L4, D19) over the keys below the strip: the current one in the accent color, the one under the finger highlighted. */
    private fun drawLanguagePopup(c: Canvas) {
        fillPaint.color = pal.bubble
        c.drawRoundRect(popupRect, radius * 1.4f, radius * 1.4f, fillPaint)
        popupBorderPaint.color = pal.specialPressed
        c.drawRoundRect(popupRect, radius * 1.4f, radius * 1.4f, popupBorderPaint)
        val pad = dp(6f)
        for (i in popupLanguages.indices) {
            val top = popupRect.top + pad + i * popupRowH
            if (i == popupSel) {
                tmp.set(popupRect.left + pad, top, popupRect.right - pad, top + popupRowH)
                fillPaint.color = pal.specialPressed
                c.drawRoundRect(tmp, radius, radius, fillPaint)
            }
            popupPaint.color = if (popupLanguages[i] == language) pal.accent else pal.text
            c.drawText(popupLanguages[i].spaceLabel, popupRect.left + dp(18f), top + popupRowH / 2 + popupBase, popupPaint)
        }
    }

    /** Open the list under the language key [anchor]; rows shrink when the keyboard is too low for all of them. */
    private fun openLanguagePopup(anchor: RectF) {
        val n = popupLanguages.size
        val pad = dp(6f)
        val top = (if (stripShown) stripH else 0f) + dp(2f)
        popupRowH = minOf(dp(46f), (height - top - dp(4f) - 2 * pad) / n).coerceAtLeast(dp(26f))
        val w = minOf(dp(240f), width - 2 * padX)
        val left = anchor.left.coerceIn(padX, (width - padX - w).coerceAtLeast(padX))
        popupRect.set(left, top, left + w, top + popupRowH * n + 2 * pad)
        popupOpen = true
        popupHeld = true
        popupSel = -1
        popupDownOutside = false
        invalidate()
    }

    private fun closeLanguagePopup() {
        popupOpen = false
        popupHeld = false
        popupSel = -1
        invalidate()
    }

    private fun popupIndexAt(x: Float, y: Float): Int {
        if (!popupRect.contains(x, y)) return -1
        val i = ((y - popupRect.top - dp(6f)) / popupRowH).toInt()
        return if (i in popupLanguages.indices && y >= popupRect.top + dp(6f)) i else -1
    }

    /**
     * While the list is open every touch belongs to it: sliding highlights, lifting over a row picks it. The finger that opened the list is
     * usually still down, lifting it off the key leaves the list open; a touch that starts and ends outside the list closes it.
     */
    private fun popupTouch(e: MotionEvent): Boolean {
        val a = e.actionIndex
        val x = e.getX(a)
        val y = e.getY(a)
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_POINTER_DOWN -> {
                popupDownOutside = !popupRect.contains(x, y)
                popupSel = popupIndexAt(x, y)
                invalidate()
            }
            MotionEvent.ACTION_MOVE -> {
                val s = popupIndexAt(e.x, e.y)
                if (s != popupSel) {
                    popupSel = s
                    invalidate()
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_POINTER_UP -> {
                val s = popupIndexAt(x, y)
                val held = popupHeld
                popupHeld = false
                when {
                    s >= 0 -> {
                        val picked = popupLanguages[s]
                        closeLanguagePopup()
                        tap()
                        listener?.onLanguagePicked(picked)
                    }
                    held -> popupSel = -1 // the opening finger left the key: the list stays
                    popupDownOutside || !popupRect.contains(x, y) -> closeLanguagePopup()
                    else -> popupSel = -1
                }
                invalidate()
            }
            MotionEvent.ACTION_CANCEL -> closeLanguagePopup()
        }
        return true
    }

    private fun isPressedIndex(i: Int): Boolean {
        for (s in 0 until MAX_PTR) if (ptrKey[s] == i) return true
        return false
    }

    private fun drawKey(c: Canvas, k: Key, r: RectF, pressed: Boolean) {
        if (k.kind == Kind.STRIP) {
            drawStripIcon(c, k, r, pressed)
            return
        }
        tmp.set(r.left + gapX, r.top + gapY, r.right - gapX, r.bottom - gapY)
        fillPaint.color = when (k.kind) {
            Kind.SPECIAL -> if (pressed) pal.specialPressed else pal.special
            Kind.ACTION -> if (pressed) pal.accentPressed else pal.accent
            else -> if (pressed) pal.keyPressed else pal.key
        }
        c.drawRoundRect(tmp, radius, radius, fillPaint)

        val cx = tmp.centerX()
        val cy = tmp.centerY()
        when (k.code) {
            Codes.CHAR -> {
                c.drawText(if (shiftState != SHIFT_OFF) k.faceShifted else k.face, cx, cy + letterBase, letterPaint)
                val hint = hintDrawnOf(k)
                if (hint != null) {
                    val small = hint.length > 2 // "ZWNJ" / "ZWJ" must still fit in the corner
                    if (small) hintPaint.textSize = rowH * 0.15f
                    c.drawText(hint, tmp.right - dp(6f), tmp.top + hintTop, hintPaint)
                    if (small) hintPaint.textSize = rowH * 0.22f
                }
            }
            Codes.SPACE -> {
                labelPaint.color = pal.textMuted
                c.drawText(spaceLabel, cx, cy + labelBase, labelPaint)
                labelPaint.color = pal.text
            }
            Codes.SHIFT -> {
                drawIcon(c, iconShift, cx, cy, rowH * 0.42f, shiftState != SHIFT_OFF, pal.text)
                if (shiftState == SHIFT_LOCKED) {
                    iconPaint.color = pal.text
                    iconPaint.style = Paint.Style.STROKE
                    c.save()
                    c.translate(cx - rowH * 0.21f, cy - rowH * 0.21f)
                    c.scale(rowH * 0.42f, rowH * 0.42f)
                    c.drawLine(.3f, 1.0f, .7f, 1.0f, iconPaint)
                    c.restore()
                }
            }
            Codes.BACKSPACE -> {
                drawIcon(c, iconBackspace, cx, cy, rowH * 0.46f, false, pal.text)
                drawIcon(c, iconBackspaceX, cx, cy, rowH * 0.46f, false, pal.text)
            }
            Codes.ENTER -> drawIcon(c, iconEnter, cx, cy, rowH * 0.44f, false, pal.onAccent)
            else -> c.drawText(k.label, cx, cy + labelBase, labelPaint)
        }
    }

    private fun drawIcon(c: Canvas, path: Path, cx: Float, cy: Float, size: Float, fill: Boolean, color: Int) {
        iconPaint.color = color
        iconPaint.style = if (fill) Paint.Style.FILL_AND_STROKE else Paint.Style.STROKE
        c.save()
        c.translate(cx - size / 2, cy - size / 2)
        c.scale(size, size)
        c.drawPath(path, iconPaint)
        c.restore()
    }

    private fun drawStripIcon(c: Canvas, k: Key, r: RectF, pressed: Boolean) {
        if (k.code == Codes.LANG) {
            drawLanguagePill(c, r, pressed)
            return
        }
        if (pressed) {
            fillPaint.color = pal.specialPressed
            c.drawCircle(r.centerX(), r.centerY(), stripH * 0.42f, fillPaint)
        }
        val size = dp(24f)
        iconPaint.color = pal.text
        iconPaint.style = Paint.Style.STROKE
        c.save()
        c.translate(r.centerX() - size / 2, r.centerY() - size / 2)
        c.scale(size, size)
        when (k.code) {
            Codes.GLOBE -> {
                c.drawCircle(.5f, .5f, .4f, iconPaint)
                c.drawOval(.32f, .1f, .68f, .9f, iconPaint)
                c.drawLine(.1f, .5f, .9f, .5f, iconPaint)
            }
            Codes.EMOJI -> {
                c.drawCircle(.5f, .5f, .4f, iconPaint)
                c.drawArc(.3f, .34f, .7f, .72f, 20f, 140f, false, iconPaint)
                iconPaint.style = Paint.Style.FILL
                c.drawCircle(.37f, .42f, .045f, iconPaint)
                c.drawCircle(.63f, .42f, .045f, iconPaint)
            }
            Codes.CLIPBOARD -> {
                c.drawRoundRect(.22f, .18f, .78f, .9f, .08f, .08f, iconPaint)
                c.drawRoundRect(.38f, .08f, .62f, .26f, .05f, .05f, iconPaint)
                c.drawLine(.34f, .5f, .66f, .5f, iconPaint)
                c.drawLine(.34f, .68f, .58f, .68f, iconPaint)
            }
            Codes.MIC -> {
                c.drawRoundRect(.38f, .08f, .62f, .55f, .12f, .12f, iconPaint)
                c.drawArc(.26f, .3f, .74f, .72f, 0f, 180f, false, iconPaint)
                c.drawLine(.5f, .72f, .5f, .9f, iconPaint)
                c.drawLine(.36f, .9f, .64f, .9f, iconPaint)
            }
        }
        c.restore()
    }

    /** Small rounded pill with the current language (EN / বাং) in the strip, next to the globe. */
    private fun drawLanguagePill(c: Canvas, r: RectF, pressed: Boolean) {
        val ph = stripH * 0.64f
        val pw = r.width() - dp(8f)
        tmp.set(r.centerX() - pw / 2, r.centerY() - ph / 2, r.centerX() + pw / 2, r.centerY() + ph / 2)
        fillPaint.color = if (pressed) pal.keyPressed else pal.special
        c.drawRoundRect(tmp, ph / 2, ph / 2, fillPaint)
        stripTextPaint.color = pal.text
        c.drawText(langLabel, tmp.centerX(), tmp.centerY() + stripBase, stripTextPaint)
    }

    /** The small chevron at the left of the strip: points left while suggestions show (tap = icons), right while the icons show (tap = back). */
    private fun drawArrow(c: Canvas) {
        var pressed = false
        for (s in 0 until MAX_PTR) if (ptrArrow[s] && ptrSug[s] == ARROW_SLOT) pressed = true
        val cx = padX + arrowW / 2
        if (pressed) {
            fillPaint.color = pal.specialPressed
            c.drawCircle(cx, stripH / 2, stripH * 0.38f, fillPaint)
        }
        val size = dp(20f)
        iconPaint.color = pal.textMuted
        iconPaint.style = Paint.Style.STROKE
        c.save()
        c.translate(cx - size / 2, stripH / 2 - size / 2)
        c.scale(if (iconsPinned) -size else size, size) // a negative x scale mirrors the chevron
        if (iconsPinned) c.translate(-1f, 0f)
        c.drawPath(iconChevron, iconPaint)
        c.restore()
    }

    /** [SUG_SLOTS] equal columns right of the arrow; the first one (the word as it is in the editor, or the best guess) is bold. */
    private fun drawSuggestions(c: Canvas) {
        val slotW = (width - 2 * padX - arrowW) / SUG_SLOTS
        for (i in 0 until SUG_SLOTS) {
            val word = sug[i] ?: continue
            val x0 = padX + arrowW + slotW * i
            if (isSugPressed(i)) {
                tmp.set(x0 + dp(2f), stripH * 0.12f, x0 + slotW - dp(2f), stripH * 0.88f)
                fillPaint.color = pal.special
                c.drawRoundRect(tmp, radius, radius, fillPaint)
            }
            if (i > 0 && sug[i - 1] != null) { // thin divider between neighbours
                tmp.set(x0 - dp(0.5f), stripH * 0.28f, x0 + dp(0.5f), stripH * 0.72f)
                fillPaint.color = (pal.textMuted and 0x00FFFFFF) or 0x55000000
                c.drawRect(tmp, fillPaint)
            }
            val p = if (i == 0) sugBoldPaint else sugPaint
            p.color = pal.text
            drawFitted(c, word, p, x0 + slotW / 2, stripH / 2, slotW - dp(12f))
        }
    }

    /** Draw [text] centered at ([cx], [cy]); long words shrink (down to 72%) and are then cut, so nothing spills into the next slot. */
    private fun drawFitted(c: Canvas, text: String, p: Paint, cx: Float, cy: Float, maxW: Float) {
        val base = p.textSize
        val w = p.measureText(text)
        var n = text.length
        if (w > maxW) {
            p.textSize = (base * maxW / w).coerceAtLeast(base * 0.72f)
            if (p.measureText(text) > maxW) n = p.breakText(text, true, maxW, null)
        }
        c.drawText(text, 0, n, cx, cy + sugBase * (p.textSize / base), p)
        p.textSize = base
    }

    private fun isSugPressed(i: Int): Boolean {
        for (s in 0 until MAX_PTR) if (ptrInSug[s] && ptrSug[s] == i) return true
        return false
    }

    /** The suggestion slot under x (edges snap to the nearest slot), or -1 when that slot is empty. */
    private fun sugIndexAt(x: Float): Int {
        val slotW = (width - 2 * padX - arrowW) / SUG_SLOTS
        val i = ((x.coerceIn(padX + arrowW, width - padX - 0.01f) - padX - arrowW) / slotW).toInt()
        return if (i in 0 until SUG_SLOTS && sug[i] != null) i else -1
    }

    private fun drawBubble(c: Canvas, text: String, r: RectF) {
        val bw = r.width() * 1.25f
        val bh = rowH * 1.05f
        val margin = dp(2f)
        val left = (r.centerX() - bw / 2).coerceIn(margin, (width - margin - bw).coerceAtLeast(margin))
        val top = (r.top - bh + dp(8f)).coerceAtLeast(0f)
        tmp.set(left, top, left + bw, top + bh)
        fillPaint.color = pal.bubble
        c.drawRoundRect(tmp, radius * 1.4f, radius * 1.4f, fillPaint)
        c.drawText(text, tmp.centerX(), tmp.centerY() + bubbleBase, bubblePaint)
    }

    // ---------------------------------------------------------------- touch

    override fun onTouchEvent(e: MotionEvent): Boolean {
        if (popupOpen) return popupTouch(e) // before ACTION_DOWN's cancelAll(), which would close the list
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                cancelAll() // drop any stuck pointer from a missed UP
                down(e.getPointerId(0), e.getX(0), e.getY(0))
            }
            MotionEvent.ACTION_POINTER_DOWN -> {
                val i = e.actionIndex
                down(e.getPointerId(i), e.getX(i), e.getY(i))
            }
            MotionEvent.ACTION_MOVE -> {
                for (i in 0 until e.pointerCount) move(e.getPointerId(i), e.getX(i), e.getY(i))
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_POINTER_UP -> {
                val i = e.actionIndex
                up(e.getPointerId(i))
            }
            MotionEvent.ACTION_CANCEL -> cancelAll()
        }
        return true
    }

    private fun slotOf(id: Int): Int {
        for (s in 0 until MAX_PTR) if (ptrId[s] == id) return s
        return -1
    }

    private fun findKey(x: Float, y: Float): Int {
        if (page >= rects.size) return -1
        val rs = rects[page]
        val cx = x.coerceIn(padX, width - padX - 0.01f)
        val cy = if (stripShown && y < stripH) y else y.coerceIn(rowsTop, rowsBottom - 0.01f)
        for (i in rs.indices) if (rs[i].contains(cx, cy)) return i
        return -1
    }

    private fun down(id: Int, x: Float, y: Float) {
        var slot = -1
        for (s in 0 until MAX_PTR) if (ptrId[s] == -1) { slot = s; break }
        if (slot < 0) return
        val inStrip = stripShown && sugCount > 0 && y < stripH
        val inArrow = inStrip && x < padX + arrowW
        val inSug = inStrip && showSug && !inArrow // the strip shows suggestions: a touch there picks one, never a hidden icon
        val idx = if (inSug || inArrow) -1 else findKey(x, y)
        ptrId[slot] = id
        ptrKey[slot] = idx
        ptrLong[slot] = false
        ptrHint[slot] = false
        repeatCount[slot] = 0
        swipeArmed[slot] = false
        ptrInSug[slot] = inSug
        ptrArrow[slot] = inArrow
        ptrSug[slot] = if (inSug) sugIndexAt(x) else if (inArrow) ARROW_SLOT else -1
        if (inArrow) {
            tap()
            invalidate()
            return
        }
        if (inSug) {
            if (ptrSug[slot] >= 0) {
                tap()
                invalidate()
            }
            return
        }
        if (idx < 0) return
        val k = keys[page][idx]
        downX[slot] = x
        swipeArmed[slot] = spaceSwipeEnabled && k.code == Codes.SPACE
        tap()
        click(k.code)
        invalidate()
        if (k.code == Codes.BACKSPACE) {
            listener?.onBackspace()
            uiHandler.postDelayed(repeatRunnable[slot], REPEAT_START_MS)
        } else if (hasLongPress(k)) {
            uiHandler.postDelayed(longPressRunnable[slot], longPressMs)
        }
    }

    private fun move(id: Int, x: Float, y: Float) {
        val slot = slotOf(id)
        if (slot < 0 || ptrLong[slot]) return
        if (ptrArrow[slot]) { // sliding off the arrow cancels the toggle
            val on = if (y >= 0f && y < stripH && x < padX + arrowW) ARROW_SLOT else -1
            if (on != ptrSug[slot]) {
                ptrSug[slot] = on
                invalidate()
            }
            return
        }
        if (ptrInSug[slot]) { // sliding off a suggestion cancels the pick
            val i = if (y >= 0f && y < stripH) sugIndexAt(x) else -1
            if (i != ptrSug[slot]) {
                ptrSug[slot] = i
                invalidate()
            }
            return
        }
        if (swipeArmed[slot] && abs(x - downX[slot]) > dp(SWIPE_DP)) {
            // Swipe across the space bar: switch language, and do not type a space on release.
            swipeArmed[slot] = false
            cancelTimers(slot)
            ptrLong[slot] = true
            longTap()
            listener?.onLanguageToggle()
            invalidate()
            return
        }
        var idx = findKey(x, y)
        val old = ptrKey[slot]
        if (idx == old) return
        // Do not let a finger that started on the keys slide into the strip (or the reverse) and trigger an icon.
        if (idx >= 0 && old >= 0 && (keys[page][idx].kind == Kind.STRIP) != (keys[page][old].kind == Kind.STRIP)) idx = -1
        if (idx == old) return
        cancelTimers(slot)
        ptrKey[slot] = idx
        invalidate()
        if (idx >= 0 && hasLongPress(keys[page][idx])) uiHandler.postDelayed(longPressRunnable[slot], longPressMs)
    }

    private fun up(id: Int) {
        val slot = slotOf(id)
        if (slot < 0) return
        cancelTimers(slot)
        val idx = ptrKey[slot]
        val wasLong = ptrLong[slot]
        val wasHint = ptrHint[slot]
        val wasSug = ptrInSug[slot]
        val wasArrow = ptrArrow[slot]
        val sugIdx = ptrSug[slot]
        ptrId[slot] = -1
        ptrKey[slot] = -1
        ptrLong[slot] = false
        ptrHint[slot] = false
        ptrInSug[slot] = false
        ptrArrow[slot] = false
        ptrSug[slot] = -1
        invalidate()
        if (wasHint) { // the finger was lifted while the hint bubble showed: type the hint now (G1.6)
            if (idx >= 0 && page < keys.size) {
                val k = keys[page][idx]
                val hint = hintOf(k)
                if (hint != null) {
                    // A Shift hint (Shift+AltGr character, D59) uses up a one-shot Shift like any typed character; a digit hint does not.
                    if (shiftState == SHIFT_ONCE && k.shiftHint != null) shiftState = SHIFT_OFF
                    listener?.onText(hint)
                    invalidate()
                }
            }
            return
        }
        if (wasArrow) {
            if (sugIdx == ARROW_SLOT && sugCount > 0) iconsPinned = !iconsPinned
            return
        }
        if (wasSug) {
            // Whatever the slot shows now (null if the word ended meanwhile, then nothing happens).
            if (sugIdx >= 0) sug[sugIdx]?.let { listener?.onSuggestion(it) }
            return
        }
        if (idx < 0 || wasLong || page >= keys.size) return
        if (showSug && keys[page][idx].kind == Kind.STRIP) return // finger went down on an icon before it was hidden
        perform(keys[page][idx])
    }

    private fun cancelTimers(slot: Int) {
        uiHandler.removeCallbacks(longPressRunnable[slot])
        uiHandler.removeCallbacks(repeatRunnable[slot])
    }

    private fun onLongPress(slot: Int) {
        val idx = ptrKey[slot]
        if (idx < 0 || page >= keys.size) return
        val k = keys[page][idx]
        if (k.code == Codes.GLOBE) {
            ptrLong[slot] = true
            longTap()
            listener?.onGlobeLong()
            invalidate()
            return
        }
        if (k.code == Codes.LANG) { // the list of layouts (L4, D19); it takes over every touch until it is closed
            val anchor = rects[page][idx]
            cancelAll()
            longTap()
            openLanguagePopup(anchor)
            return
        }
        if (hintOf(k) == null) return
        ptrLong[slot] = true
        ptrHint[slot] = true // the hint is typed when the finger is lifted; until then its bubble shows it
        longTap()
        invalidate()
    }

    private fun hasLongPress(k: Key): Boolean = hintOf(k) != null || k.code == Codes.GLOBE || k.code == Codes.LANG

    private fun onRepeat(slot: Int) {
        val idx = ptrKey[slot]
        if (idx < 0 || page >= keys.size || keys[page][idx].code != Codes.BACKSPACE) return
        listener?.onBackspace()
        repeatCount[slot]++
        uiHandler.postDelayed(repeatRunnable[slot], if (repeatCount[slot] > 12) REPEAT_FAST_MS else REPEAT_MS)
    }

    private fun perform(k: Key) {
        when (k.code) {
            Codes.CHAR -> {
                val t = if (shiftState != SHIFT_OFF) k.shifted else k.text
                val wasOnce = shiftState == SHIFT_ONCE
                if (wasOnce) shiftState = SHIFT_OFF // before the listener, so the IME can switch it back on
                listener?.onText(t)
                if (wasOnce) invalidate()
            }
            Codes.SPACE -> listener?.onText(" ")
            Codes.ENTER -> listener?.onEnter()
            Codes.TAB -> listener?.onTab()
            Codes.SHIFT -> toggleShift()
            Codes.SYMBOLS -> setPage(Layouts.PAGE_SYMBOLS)
            Codes.SYMBOLS2 -> setPage(Layouts.PAGE_SYMBOLS2)
            Codes.LETTERS -> setPage(Layouts.PAGE_LETTERS)
            Codes.GLOBE -> listener?.onGlobe()
            Codes.LANG -> listener?.onLanguageToggle()
            Codes.EMOJI -> listener?.onEmojiKey()
            else -> Unit // clipboard, mic: not built yet
        }
    }

    private fun toggleShift() {
        val now = SystemClock.uptimeMillis()
        shiftState = when {
            shiftState == SHIFT_LOCKED -> SHIFT_OFF
            shiftState == SHIFT_ONCE && now - lastShiftTap < DOUBLE_TAP_MS -> SHIFT_LOCKED
            shiftState == SHIFT_ONCE -> SHIFT_OFF
            else -> SHIFT_ONCE
        }
        lastShiftTap = now
        invalidate()
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        for (s in 0 until MAX_PTR) cancelTimers(s)
    }

    class Palette(
        val bg: Int,
        val key: Int,
        val keyPressed: Int,
        val special: Int,
        val specialPressed: Int,
        val accent: Int,
        val accentPressed: Int,
        val text: Int,
        val textMuted: Int,
        val onAccent: Int,
        val bubble: Int,
    )

    private companion object {
        const val MAX_PTR = 4
        const val SHIFT_OFF = 0
        const val SHIFT_ONCE = 1
        const val SHIFT_LOCKED = 2
        const val LONG_PRESS_MS = 300L
        const val THEME_SYSTEM = 0 // the theme values match Prefs.THEME
        const val THEME_LIGHT = 1
        const val THEME_DARK = 2
        const val VIB_OFF = 0 // the vibration levels match Prefs.VIBRATION
        const val VIB_LIGHT = 1
        const val VIB_NORMAL = 2
        const val VIB_STRONG = 3
        const val REPEAT_START_MS = 400L
        const val REPEAT_MS = 50L
        const val REPEAT_FAST_MS = 30L
        const val DOUBLE_TAP_MS = 350L
        const val SWIPE_DP = 64f
        const val SUG_SLOTS = 3
        const val ARROW_SLOT = 100 // ptrSug value while a finger is on the strip arrow

        // Placeholder colors (owner has not picked a palette yet); custom themes come in v0.5.
        val DARK = Palette(
            bg = Color.parseColor("#1B1C1E"),
            key = Color.parseColor("#3C3F43"),
            keyPressed = Color.parseColor("#5A5E63"),
            special = Color.parseColor("#2B2D30"),
            specialPressed = Color.parseColor("#46494D"),
            accent = Color.parseColor("#80CBC4"),
            accentPressed = Color.parseColor("#A7DDD8"),
            text = Color.parseColor("#E8EAED"),
            textMuted = Color.parseColor("#9AA0A6"),
            onAccent = Color.parseColor("#00302C"),
            bubble = Color.parseColor("#5A5E63"),
        )
        val LIGHT = Palette(
            bg = Color.parseColor("#E3E6EA"),
            key = Color.parseColor("#FFFFFF"),
            keyPressed = Color.parseColor("#D3D8DD"),
            special = Color.parseColor("#C9CFD5"),
            specialPressed = Color.parseColor("#B4BBC2"),
            accent = Color.parseColor("#1B6B63"),
            accentPressed = Color.parseColor("#155650"),
            text = Color.parseColor("#202124"),
            textMuted = Color.parseColor("#70757A"),
            onAccent = Color.parseColor("#FFFFFF"),
            bubble = Color.parseColor("#FFFFFF"),
        )
    }
}
