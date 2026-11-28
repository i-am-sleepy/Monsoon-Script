package com.monsoonscript.keyboard.view

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/**
 * The voice level display (G5): a row of rounded bars, the newest on the right, older ones fading to the left; the bars ease towards their new height so
 * it looks fluid at 10-20 level updates per second. While the text is being worked out the bars become a calm travelling wave. Cheap: one rounded rectangle
 * per bar (about 40), no allocation per frame. The owner of the view keeps it redrawing while it is visible.
 */
class Waveform(private val capacity: Int = 220) {
    private val target = FloatArray(capacity)
    private val shown = FloatArray(capacity)
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val rect = RectF()

    /** [level] 0..1 (see [levelOf]). */
    fun push(level: Float) {
        System.arraycopy(target, 1, target, 0, capacity - 1)
        target[capacity - 1] = level.coerceIn(0f, 1f)
    }

    fun reset() {
        target.fill(0f)
        shown.fill(0f)
    }

    /** [state] 1 = listening (bars follow the voice), 2 = working (travelling wave). */
    fun draw(c: Canvas, left: Float, top: Float, right: Float, bottom: Float, density: Float, accent: Int, muted: Int, state: Int, nowMs: Long) {
        val barW = 3f * density
        val gap = 3f * density
        val n = min(capacity, ((right - left + gap) / (barW + gap)).toInt())
        if (n <= 0) return
        val cy = (top + bottom) / 2f
        val maxH = bottom - top
        val minH = 3f * density
        val phase = nowMs / 240f
        val base = if (state == 2) muted else accent
        val r = (base shr 16) and 0xFF
        val g = (base shr 8) and 0xFF
        val b = base and 0xFF
        for (k in 0 until n) {
            val i = capacity - 1 - k // k = 0 is the newest bar, drawn at the right edge
            shown[i] += (target[i] - shown[i]) * 0.4f
            val h = if (state == 2) {
                minH + (sin(phase + k * 0.42f) + 1f) / 2f * maxH * 0.28f
            } else {
                max(minH, shown[i] * maxH)
            }
            val fade = 1f - 0.75f * (k.toFloat() / n)
            paint.color = ((255 * fade).toInt() shl 24) or (r shl 16) or (g shl 8) or b
            val x1 = right - k * (barW + gap)
            rect.set(x1 - barW, cy - h / 2f, x1, cy + h / 2f)
            c.drawRoundRect(rect, barW / 2f, barW / 2f, paint)
        }
    }

    companion object {
        /** A chunk's RMS (0..1 full scale) as a display level: -50 dB is silence, -8 dB is full height. */
        fun levelOf(rms: Float): Float {
            if (rms <= 1e-5f) return 0f
            val db = 20f * kotlin.math.log10(rms)
            return ((db + 50f) / 42f).coerceIn(0f, 1f)
        }
    }
}
