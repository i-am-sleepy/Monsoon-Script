package com.monsoonscript.keyboard.theme

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.view.View

/** A tiny picture of a keyboard in the colors of [spec]: the theme at a glance in the list (L10, D75). */
internal class ThemeSwatchView(context: Context) : View(context) {
    var spec: ThemeSpec? = null
        set(v) {
            field = v
            invalidate()
        }

    private val d = resources.displayMetrics.density
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val rect = RectF()

    override fun onMeasure(widthSpec: Int, heightSpec: Int) {
        setMeasuredDimension((76 * d).toInt(), (48 * d).toInt())
    }

    override fun onDraw(c: Canvas) {
        val s = spec ?: return
        val col = s.colors()
        val w = width.toFloat()
        val h = height.toFloat()
        val r = 6 * d
        paint.style = Paint.Style.FILL
        paint.color = col.bg
        rect.set(0f, 0f, w, h)
        c.drawRoundRect(rect, r, r, paint)
        // three rows of keys; the last row has a special key at the left and the Enter key at the right
        val pad = 5 * d
        val gap = 2.5f * d
        val rows = 3
        val keyH = (h - 2 * pad - (rows - 1) * gap) / rows
        for (row in 0 until rows) {
            val top = pad + row * (keyH + gap)
            val cells = 5
            val keyW = (w - 2 * pad - (cells - 1) * gap) / cells
            for (i in 0 until cells) {
                paint.color = when {
                    row == 2 && i == 0 -> col.special
                    row == 2 && i == cells - 1 -> col.accent
                    row == 1 && i == cells - 1 -> col.special
                    else -> col.key
                }
                val left = pad + i * (keyW + gap)
                rect.set(left, top, left + keyW, top + keyH)
                c.drawRoundRect(rect, 2 * d, 2 * d, paint)
                if (row == 0 && i < 3) { // a little "letter" mark in the letters color
                    paint.color = col.text
                    c.drawRect(left + keyW * 0.3f, top + keyH * 0.4f, left + keyW * 0.7f, top + keyH * 0.6f, paint)
                }
            }
        }
    }
}

/** A round dot of one color, with a thin outline, for the color rows of the editor. */
internal class ColorDotView(context: Context) : View(context) {
    var color: Int = 0
        set(v) {
            field = v
            invalidate()
        }
    var outline: Int = 0x66808080

    private val d = resources.displayMetrics.density
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)

    override fun onMeasure(widthSpec: Int, heightSpec: Int) {
        setMeasuredDimension((36 * d).toInt(), (36 * d).toInt())
    }

    override fun onDraw(c: Canvas) {
        val cx = width / 2f
        val cy = height / 2f
        val r = minOf(width, height) / 2f - 2 * d
        paint.style = Paint.Style.FILL
        paint.color = color
        c.drawCircle(cx, cy, r, paint)
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 1.5f * d
        paint.color = outline
        c.drawCircle(cx, cy, r, paint)
    }
}
