package com.monsoonscript.keyboard.view

import android.content.Context
import android.content.res.Configuration
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
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
    }

    var listener: Listener? = null

    /** Keyboard background color; the IME paints its window and the navigation bar area with it. */
    val backgroundColor: Int get() = pal.bg
    val isLightTheme: Boolean get() = pal === LIGHT

    private val density = resources.displayMetrics.density
    private fun dp(v: Float) = v * density

    // ---- geometry (px)
    private var stripH = dp(44f)
    private var rowH = dp(52f)
    private val padX = dp(4f)
    private val padTop = dp(4f)
    private val padBottom = dp(8f)
    private val gapX = dp(3f)
    private val gapY = dp(4f)
    private val radius = dp(8f)
    private var rowsTop = 0f
    private var rowsBottom = 0f

    // ---- layout results, per page
    private var keys: Array<Array<Key>> = emptyArray()
    private var rects: Array<Array<RectF>> = emptyArray()

    // ---- state
    private var page = Layouts.PAGE_LETTERS
    private var shiftState = SHIFT_OFF
    private var lastShiftTap = 0L

    // ---- pointers (multi-touch)
    private val ptrId = IntArray(MAX_PTR) { -1 }
    private val ptrKey = IntArray(MAX_PTR) { -1 }
    private val ptrLong = BooleanArray(MAX_PTR)
    private val repeatCount = IntArray(MAX_PTR)
    private val uiHandler = Handler(Looper.getMainLooper())
    private val longPressRunnable = Array(MAX_PTR) { slot -> Runnable { onLongPress(slot) } }
    private val repeatRunnable = Array(MAX_PTR) { slot -> Runnable { onRepeat(slot) } }

    // ---- paint
    private val tmp = RectF()
    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val letterPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER }
    private val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER }
    private val hintPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.RIGHT }
    private val bubblePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER }
    private val iconPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
        strokeWidth = 0.09f // unit-square coordinates; the canvas is scaled to the icon size
    }
    private var letterBase = 0f
    private var labelBase = 0f
    private var bubbleBase = 0f
    private var hintTop = 0f
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

    /** Called by the IME after it checked the editor's auto-capitalization mode. Never overrides caps lock. */
    fun applyAutoCaps(on: Boolean) {
        if (shiftState == SHIFT_LOCKED) return
        val s = if (on) SHIFT_ONCE else SHIFT_OFF
        if (s != shiftState) {
            shiftState = s
            invalidate()
        }
    }

    fun cancelAll() {
        for (s in 0 until MAX_PTR) {
            cancelTimers(s)
            ptrId[s] = -1
            ptrKey[s] = -1
            ptrLong[s] = false
        }
        invalidate()
    }

    // ---------------------------------------------------------------- measure / layout

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val land = resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
        rowH = dp(if (land) 42f else 52f)
        stripH = dp(if (land) 38f else 44f)
        updateTextSizes()
        val h = (stripH + rowH * 4 + padTop + padBottom).toInt()
        setMeasuredDimension(MeasureSpec.getSize(widthMeasureSpec), h)
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        computeLayout(w)
    }

    private fun computeLayout(w: Int) {
        val iconCell = dp(44f)
        rowsTop = stripH + padTop
        rowsBottom = rowsTop + rowH * 4
        val outKeys = ArrayList<Array<Key>>()
        val outRects = ArrayList<Array<RectF>>()
        for (p in 0 until Layouts.PAGE_COUNT) {
            val kl = ArrayList<Key>()
            val rl = ArrayList<RectF>()
            var x = padX
            for (k in Layouts.stripLeft) {
                kl.add(k); rl.add(RectF(x, 0f, x + iconCell, stripH)); x += iconCell
            }
            var rx = w - padX
            for (k in Layouts.stripRight.reversed()) {
                rx -= iconCell
                kl.add(k); rl.add(RectF(rx, 0f, rx + iconCell, stripH))
            }
            for ((r, row) in Layouts.pages[p].withIndex()) {
                val total = row.sumOf { it.weight.toDouble() }.toFloat()
                val unit = (w - 2 * padX) / total
                val top = rowsTop + r * rowH
                var kx = padX
                for (k in row) {
                    val kw = k.weight * unit
                    if (k.kind != Kind.SPACER) {
                        kl.add(k); rl.add(RectF(kx, top, kx + kw, top + rowH))
                    }
                    kx += kw
                }
            }
            outKeys.add(kl.toTypedArray())
            outRects.add(rl.toTypedArray())
        }
        keys = outKeys.toTypedArray()
        rects = outRects.toTypedArray()
    }

    private fun updateTextSizes() {
        letterPaint.textSize = rowH * 0.40f
        labelPaint.textSize = rowH * 0.27f
        hintPaint.textSize = rowH * 0.22f
        bubblePaint.textSize = rowH * 0.62f
        letterBase = baseline(letterPaint)
        labelBase = baseline(labelPaint)
        bubbleBase = baseline(bubblePaint)
        hintTop = dp(3f) - hintPaint.fontMetrics.ascent
    }

    private fun baseline(p: Paint): Float {
        val fm = p.fontMetrics
        return -(fm.ascent + fm.descent) / 2f
    }

    // ---------------------------------------------------------------- theme

    fun refreshTheme() {
        val night = (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
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
        for (i in ks.indices) drawKey(canvas, ks[i], rs[i], isPressedIndex(i))
        for (s in 0 until MAX_PTR) {
            val i = ptrKey[s]
            if (i >= 0 && !ptrLong[s] && i < ks.size && ks[i].kind == Kind.CHAR) drawBubble(canvas, ks[i], rs[i])
        }
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
                val t = if (shiftState != SHIFT_OFF && k.isLetter) k.upper else k.text
                c.drawText(t, cx, cy + letterBase, letterPaint)
                val hint = k.hint
                if (hint != null) c.drawText(hint, tmp.right - dp(6f), tmp.top + hintTop, hintPaint)
            }
            Codes.SPACE -> {
                labelPaint.color = pal.textMuted
                c.drawText(k.label, cx, cy + labelBase, labelPaint)
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

    private fun drawBubble(c: Canvas, k: Key, r: RectF) {
        val bw = r.width() * 1.25f
        val bh = rowH * 1.05f
        val margin = dp(2f)
        val left = (r.centerX() - bw / 2).coerceIn(margin, (width - margin - bw).coerceAtLeast(margin))
        val top = (r.top - bh + dp(8f)).coerceAtLeast(0f)
        tmp.set(left, top, left + bw, top + bh)
        fillPaint.color = pal.bubble
        c.drawRoundRect(tmp, radius * 1.4f, radius * 1.4f, fillPaint)
        val t = if (shiftState != SHIFT_OFF && k.isLetter) k.upper else k.text
        c.drawText(t, tmp.centerX(), tmp.centerY() + bubbleBase, bubblePaint)
    }

    // ---------------------------------------------------------------- touch

    override fun onTouchEvent(e: MotionEvent): Boolean {
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
        val cy = if (y < stripH) y else y.coerceIn(rowsTop, rowsBottom - 0.01f)
        for (i in rs.indices) if (rs[i].contains(cx, cy)) return i
        return -1
    }

    private fun down(id: Int, x: Float, y: Float) {
        var slot = -1
        for (s in 0 until MAX_PTR) if (ptrId[s] == -1) { slot = s; break }
        if (slot < 0) return
        val idx = findKey(x, y)
        ptrId[slot] = id
        ptrKey[slot] = idx
        ptrLong[slot] = false
        repeatCount[slot] = 0
        if (idx < 0) return
        val k = keys[page][idx]
        performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
        invalidate()
        if (k.code == Codes.BACKSPACE) {
            listener?.onBackspace()
            uiHandler.postDelayed(repeatRunnable[slot], REPEAT_START_MS)
        } else if (hasLongPress(k)) {
            uiHandler.postDelayed(longPressRunnable[slot], LONG_PRESS_MS)
        }
    }

    private fun move(id: Int, x: Float, y: Float) {
        val slot = slotOf(id)
        if (slot < 0 || ptrLong[slot]) return
        var idx = findKey(x, y)
        val old = ptrKey[slot]
        if (idx == old) return
        // Do not let a finger that started on the keys slide into the strip (or the reverse) and trigger an icon.
        if (idx >= 0 && old >= 0 && (keys[page][idx].kind == Kind.STRIP) != (keys[page][old].kind == Kind.STRIP)) idx = -1
        if (idx == old) return
        cancelTimers(slot)
        ptrKey[slot] = idx
        invalidate()
        if (idx >= 0 && hasLongPress(keys[page][idx])) uiHandler.postDelayed(longPressRunnable[slot], LONG_PRESS_MS)
    }

    private fun up(id: Int) {
        val slot = slotOf(id)
        if (slot < 0) return
        cancelTimers(slot)
        val idx = ptrKey[slot]
        val wasLong = ptrLong[slot]
        ptrId[slot] = -1
        ptrKey[slot] = -1
        ptrLong[slot] = false
        invalidate()
        if (idx < 0 || wasLong || page >= keys.size) return
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
            performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
            listener?.onGlobeLong()
            invalidate()
            return
        }
        val hint = k.hint ?: return
        ptrLong[slot] = true
        performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
        listener?.onText(hint)
        invalidate()
    }

    private fun hasLongPress(k: Key): Boolean = k.hint != null || k.code == Codes.GLOBE

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
                val t = if (shiftState != SHIFT_OFF && k.isLetter) k.upper else k.text
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
            else -> Unit // emoji, clipboard, mic: not built yet
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

    private class Palette(
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
        const val REPEAT_START_MS = 400L
        const val REPEAT_MS = 50L
        const val REPEAT_FAST_MS = 30L
        const val DOUBLE_TAP_MS = 350L

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
