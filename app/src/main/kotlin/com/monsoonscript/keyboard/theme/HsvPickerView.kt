package com.monsoonscript.keyboard.theme

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.view.MotionEvent
import android.view.View

/**
 * A color picker made of two touch areas (L10, D75): a square for saturation (left to right) and brightness (bottom to top) of the current hue, and a bar for the
 * hue. No libraries. [setColor] sets it, [onChange] reports every change the finger makes.
 */
internal class HsvPickerView(context: Context) : View(context) {
    var onChange: ((Int) -> Unit)? = null

    private var hue = 0f
    private var sat = 0f
    private var value = 1f
    private var drag = NONE

    private val d = resources.displayMetrics.density
    private val barH = 30 * d
    private val gap = 14 * d
    private val squareH = 170 * d
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val rect = RectF()

    fun setColor(c: Int) {
        val h = ColorMath.toHsv(c)
        // a grey has no hue of its own: keep the hue that is shown so the bar does not jump to red
        if (h[1] > 0.001f && h[2] > 0.001f) hue = h[0]
        sat = h[1]
        value = h[2]
        invalidate()
    }

    fun color(): Int = ColorMath.fromHsv(hue, sat, value)

    override fun onMeasure(widthSpec: Int, heightSpec: Int) {
        setMeasuredDimension(MeasureSpec.getSize(widthSpec), (squareH + gap + barH).toInt())
    }

    override fun onDraw(c: Canvas) {
        val w = width.toFloat()
        // the square: white to the pure hue from left to right, then transparent to black from top to bottom
        paint.style = Paint.Style.FILL
        paint.shader = LinearGradient(0f, 0f, w, 0f, Color.WHITE, ColorMath.fromHsv(hue, 1f, 1f), Shader.TileMode.CLAMP)
        rect.set(0f, 0f, w, squareH)
        c.drawRoundRect(rect, 6 * d, 6 * d, paint)
        paint.shader = LinearGradient(0f, 0f, 0f, squareH, Color.TRANSPARENT, Color.BLACK, Shader.TileMode.CLAMP)
        c.drawRoundRect(rect, 6 * d, 6 * d, paint)
        paint.shader = null
        ring(c, sat * w, (1f - value) * squareH, 11 * d)

        // the hue bar
        val top = squareH + gap
        val stops = IntArray(7) { ColorMath.fromHsv(it * 60f, 1f, 1f) }
        paint.shader = LinearGradient(0f, 0f, w, 0f, stops, null, Shader.TileMode.CLAMP)
        rect.set(0f, top, w, top + barH)
        c.drawRoundRect(rect, 6 * d, 6 * d, paint)
        paint.shader = null
        ring(c, hue / 360f * w, top + barH / 2f, barH / 2f + 1 * d)
    }

    private fun ring(c: Canvas, x: Float, y: Float, r: Float) {
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 3 * d
        paint.color = Color.BLACK
        c.drawCircle(x, y, r, paint)
        paint.strokeWidth = 1.8f * d
        paint.color = Color.WHITE
        c.drawCircle(x, y, r, paint)
        paint.style = Paint.Style.FILL
    }

    override fun onTouchEvent(e: MotionEvent): Boolean {
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                drag = when {
                    e.y <= squareH -> SQUARE
                    e.y >= squareH + gap / 2f -> BAR
                    else -> NONE
                }
                if (drag == NONE) return false
                parent?.requestDisallowInterceptTouchEvent(true) // the page must not scroll while the finger picks a color
                update(e)
            }
            MotionEvent.ACTION_MOVE -> if (drag != NONE) update(e)
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> drag = NONE
        }
        return true
    }

    private fun update(e: MotionEvent) {
        val w = width.toFloat()
        if (w <= 0f) return
        if (drag == SQUARE) {
            sat = (e.x / w).coerceIn(0f, 1f)
            value = 1f - (e.y / squareH).coerceIn(0f, 1f)
        } else if (drag == BAR) {
            hue = (e.x / w).coerceIn(0f, 1f) * 360f
        }
        invalidate()
        onChange?.invoke(color())
    }

    private companion object {
        const val NONE = 0
        const val SQUARE = 1
        const val BAR = 2
    }
}
