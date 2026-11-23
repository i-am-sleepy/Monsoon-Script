package com.monsoonscript.keyboard.view

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.os.SystemClock
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.view.MotionEvent
import android.view.View
import com.monsoonscript.keyboard.R
import kotlin.math.sin

/**
 * The tap-to-speak panel (G5.3b, D66): it takes the place of the keys. The text box shows what was recognized, the round mic button listens (it stops on a pause,
 * tap it again to add more), the level display moves while it listens, and Insert types the text into the app. Tapping the text box brings the normal keys back
 * below a smaller panel so the text can be fixed with the keyboard's own keys; what is typed never reaches the app until Insert. The keyboard service owns the
 * text (it is the same "query" machinery as the emoji search) and feeds it in through [setQuery]; the caret is always at the end of the text.
 */
class VoicePanelView(context: Context, private var pal: KeyboardView.Palette) : View(context), SearchablePanel {

    interface Listener {
        /** The round mic button: start listening, or stop and keep what was heard. */
        fun onMicTap()

        fun onInsert()

        /** ABC: back to the keys, the text is dropped. */
        fun onClose()

        /** The text box was tapped: show the keys below the panel. */
        fun onBoxTap()

        /** "Done" while typing: hide the keys again. */
        fun onDoneEditing()
    }

    var listener: Listener? = null

    private val d = resources.displayMetrics.density
    private var text = ""
    private var searching = false
    private var state = 0 // 0 idle, 1 listening, 2 working
    private val wave = Waveform()
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND }
    private val body = TextPaint(Paint.ANTI_ALIAS_FLAG)
    private val label = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER }
    private val box = RectF()
    private val left = RectF()
    private val mic = RectF()
    private val insert = RectF()
    private val tmp = RectF()
    private var layout: StaticLayout? = null
    private var layoutW = -1
    private var pressed = NONE

    fun setPalette(p: KeyboardView.Palette) {
        pal = p
        invalidate()
    }

    fun setState(s: Int) {
        if (state == s) return
        state = s
        if (s == 1) wave.reset()
        invalidate()
    }

    fun pushLevel(level: Float) {
        wave.push(level)
    }

    fun reset() {
        text = ""
        layout = null
        searching = false
        state = 0
        wave.reset()
        pressed = NONE
    }

    // ---------------------------------------------------------------- SearchablePanel

    override fun setQuery(q: String) {
        if (q == text) return
        text = q
        layout = null
        invalidate()
    }

    override fun setSearching(on: Boolean) {
        if (searching == on) return
        searching = on
        layout = null
        invalidate()
    }

    override fun compactHeight(): Int = (132f * d).toInt()

    // ---------------------------------------------------------------- drawing

    private fun dp(v: Float) = v * d

    private fun place() {
        val w = width.toFloat()
        val h = height.toFloat()
        val pad = dp(8f)
        val barH = dp(if (searching) 44f else 54f)
        box.set(pad, pad, w - pad, h - barH - 2 * pad)
        val top = h - barH - pad
        left.set(pad, top, pad + dp(68f), top + barH)
        insert.set(w - pad - dp(100f), top, w - pad, top + barH)
        val cx = left.right + dp(14f) + barH / 2
        mic.set(cx - barH / 2, top, cx + barH / 2, top + barH)
    }

    override fun onDraw(c: Canvas) {
        c.drawColor(pal.bg)
        place()
        val now = SystemClock.uptimeMillis()
        val canInsert = text.isNotBlank() && state == 0

        // text box
        fill.color = pal.key
        c.drawRoundRect(box, dp(14f), dp(14f), fill)
        val padX = dp(14f)
        val padY = dp(10f)
        val tw = (box.width() - 2 * padX).toInt()
        body.textSize = dp(18f)
        body.color = pal.text
        if (text.isEmpty()) {
            body.color = pal.textMuted
            val hint = when (state) {
                1 -> R.string.voice_panel_listening
                2 -> R.string.voice_panel_working
                else -> R.string.voice_panel_hint
            }
            c.drawText(context.getString(hint), box.left + padX, box.top + padY - body.ascent(), body)
        } else {
            if (layout == null || layoutW != tw) {
                layout = StaticLayout.Builder.obtain(text, 0, text.length, body, tw).setAlignment(Layout.Alignment.ALIGN_NORMAL).setLineSpacing(0f, 1.1f).build()
                layoutW = tw
            }
            val lay = layout!!
            val visible = box.height() - 2 * padY
            val shift = if (lay.height > visible) lay.height - visible else 0f
            c.save()
            c.clipRect(box.left + dp(4f), box.top + dp(4f), box.right - dp(4f), box.bottom - dp(4f))
            c.translate(box.left + padX, box.top + padY - shift)
            lay.draw(c)
            if (searching) { // typing: the caret at the end of the text
                val last = lay.lineCount - 1
                val x = lay.getLineRight(last)
                fill.color = pal.accent
                tmp.set(x + dp(1f), lay.getLineTop(last).toFloat(), x + dp(3f), lay.getLineBottom(last).toFloat())
                c.drawRect(tmp, fill)
            }
            c.restore()
        }

        // left button: ABC (close) or Done (hide the keys)
        button(c, left, if (pressed == LEFT) pal.keyPressed else pal.special, context.getString(if (searching) R.string.voice_panel_done else R.string.voice_panel_abc), pal.text)

        // mic
        val cx = mic.centerX()
        val cy = mic.centerY()
        val r = mic.width() / 2
        if (state == 1) { // a soft ring that breathes while listening
            val k = (sin(now / 260.0).toFloat() + 1f) / 2f
            fill.color = (pal.accent and 0x00FFFFFF) or ((40 + 50 * k).toInt() shl 24)
            c.drawCircle(cx, cy, r + dp(2f) + dp(5f) * k, fill)
        }
        fill.color = if (state == 2) pal.special else if (pressed == MIC) pal.accentPressed else pal.accent
        c.drawCircle(cx, cy, r, fill)
        drawMic(c, cx, cy, r * 0.95f, if (state == 2) pal.textMuted else pal.onAccent)

        // level display
        val wl = mic.right + dp(12f)
        val wr = insert.left - dp(12f)
        if (state != 0 && wr > wl) wave.draw(c, wl, mic.top + dp(6f), wr, mic.bottom - dp(6f), d, pal.accent, pal.textMuted, state, now)

        // insert
        button(c, insert, if (canInsert) (if (pressed == INSERT) pal.accentPressed else pal.accent) else pal.special, context.getString(R.string.voice_panel_insert), if (canInsert) pal.onAccent else pal.textMuted)

        if (state != 0) postInvalidateOnAnimation()
    }

    private fun button(c: Canvas, rect: RectF, color: Int, text: String, textColor: Int) {
        fill.color = color
        c.drawRoundRect(rect, rect.height() / 2, rect.height() / 2, fill)
        label.textSize = dp(15f)
        label.color = textColor
        c.drawText(text, rect.centerX(), rect.centerY() - (label.ascent() + label.descent()) / 2, label)
    }

    /** A microphone: capsule, cradle and stem. */
    private fun drawMic(c: Canvas, cx: Float, cy: Float, size: Float, color: Int) {
        val bw = size * 0.42f
        val bh = size * 0.66f
        fill.color = color
        tmp.set(cx - bw / 2, cy - size * 0.50f, cx + bw / 2, cy - size * 0.50f + bh)
        c.drawRoundRect(tmp, bw / 2, bw / 2, fill)
        stroke.color = color
        stroke.strokeWidth = size * 0.11f
        tmp.set(cx - size * 0.34f, cy - size * 0.38f, cx + size * 0.34f, cy + size * 0.30f)
        c.drawArc(tmp, 20f, 140f, false, stroke)
        c.drawLine(cx, cy + size * 0.30f, cx, cy + size * 0.50f, stroke)
        c.drawLine(cx - size * 0.16f, cy + size * 0.50f, cx + size * 0.16f, cy + size * 0.50f, stroke)
    }

    // ---------------------------------------------------------------- touch

    private fun regionAt(x: Float, y: Float): Int {
        place()
        return when {
            left.contains(x, y) -> LEFT
            mic.contains(x, y) -> MIC
            insert.contains(x, y) -> INSERT
            box.contains(x, y) -> BOX
            else -> NONE
        }
    }

    override fun onTouchEvent(e: MotionEvent): Boolean {
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                pressed = regionAt(e.x, e.y)
                invalidate()
                return true
            }
            MotionEvent.ACTION_UP -> {
                val hit = regionAt(e.x, e.y)
                val was = pressed
                pressed = NONE
                invalidate()
                if (hit == was) {
                    when (hit) {
                        LEFT -> if (searching) listener?.onDoneEditing() else listener?.onClose()
                        MIC -> listener?.onMicTap()
                        INSERT -> if (text.isNotBlank() && state == 0) listener?.onInsert()
                        BOX -> if (!searching) listener?.onBoxTap()
                    }
                }
                return true
            }
            MotionEvent.ACTION_CANCEL -> {
                pressed = NONE
                invalidate()
                return true
            }
        }
        return true
    }

    private companion object {
        const val NONE = 0
        const val BOX = 1
        const val LEFT = 2
        const val MIC = 3
        const val INSERT = 4
    }
}
