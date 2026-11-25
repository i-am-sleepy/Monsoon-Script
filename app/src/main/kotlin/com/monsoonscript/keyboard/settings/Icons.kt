package com.monsoonscript.keyboard.settings

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.RectF
import android.graphics.drawable.Drawable

/**
 * The small line icons of the app (L12, D83), drawn with a few lines and curves on a 24 x 24 grid, so there are no picture files and they take the color
 * of the theme. [drawable] gives one at any size.
 */
internal object Icons {
    enum class Kind { BACK, CHEVRON, KEYBOARD, SPARKLE, DROP, CLIPBOARD, MIC, INFO, SEARCH, CLOSE, CHECK, LOCK }

    fun drawable(kind: Kind, color: Int, sizePx: Int): Drawable = IconDrawable(kind, color, sizePx)

    private class IconDrawable(private val kind: Kind, private val color: Int, private val size: Int) : Drawable() {
        private val line = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
            strokeWidth = 1.9f
        }
        private val dot = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
        private var alphaValue = 255
        private val box = RectF()

        override fun getIntrinsicWidth(): Int = size

        override fun getIntrinsicHeight(): Int = size

        override fun setAlpha(alpha: Int) {
            alphaValue = alpha
            invalidateSelf()
        }

        override fun setColorFilter(colorFilter: ColorFilter?) {
            line.colorFilter = colorFilter
            dot.colorFilter = colorFilter
            invalidateSelf()
        }

        @Deprecated("Deprecated in Java")
        override fun getOpacity(): Int = PixelFormat.TRANSLUCENT

        override fun draw(canvas: Canvas) {
            val b = bounds
            if (b.isEmpty) return
            val shown = (Color.alpha(color) * alphaValue / 255) shl 24 or (color and 0x00FFFFFF)
            line.color = shown
            dot.color = shown
            val s = minOf(b.width(), b.height()) / 24f
            canvas.save()
            canvas.translate(b.left + (b.width() - 24f * s) / 2f, b.top + (b.height() - 24f * s) / 2f)
            canvas.scale(s, s)
            drawKind(canvas)
            canvas.restore()
        }

        private fun round(canvas: Canvas, l: Float, t: Float, r: Float, b: Float, radius: Float) {
            box.set(l, t, r, b)
            canvas.drawRoundRect(box, radius, radius, line)
        }

        private fun path(canvas: Canvas, build: Path.() -> Unit) {
            canvas.drawPath(Path().apply(build), line)
        }

        private fun drawKind(c: Canvas) {
            when (kind) {
                Kind.BACK -> path(c) { moveTo(15f, 5f); lineTo(8f, 12f); lineTo(15f, 19f) }
                Kind.CHEVRON -> path(c) { moveTo(9f, 5f); lineTo(16f, 12f); lineTo(9f, 19f) }
                Kind.KEYBOARD -> {
                    round(c, 2.5f, 5.5f, 21.5f, 18.5f, 2.5f)
                    for (x in floatArrayOf(6.5f, 10.2f, 13.8f, 17.5f)) c.drawCircle(x, 9.6f, 1.1f, dot)
                    c.drawLine(7.5f, 14.4f, 16.5f, 14.4f, line)
                }
                Kind.SPARKLE -> path(c) {
                    moveTo(12f, 3f)
                    quadTo(12.9f, 11.1f, 21f, 12f)
                    quadTo(12.9f, 12.9f, 12f, 21f)
                    quadTo(11.1f, 12.9f, 3f, 12f)
                    quadTo(11.1f, 11.1f, 12f, 3f)
                    close()
                }
                Kind.DROP -> path(c) {
                    moveTo(12f, 3f)
                    cubicTo(12f, 3f, 5.5f, 10f, 5.5f, 14.5f)
                    cubicTo(5.5f, 18.1f, 8.4f, 21f, 12f, 21f)
                    cubicTo(15.6f, 21f, 18.5f, 18.1f, 18.5f, 14.5f)
                    cubicTo(18.5f, 10f, 12f, 3f, 12f, 3f)
                    close()
                }
                Kind.CLIPBOARD -> {
                    round(c, 5f, 4.5f, 19f, 21f, 2f)
                    round(c, 9f, 2.5f, 15f, 6.5f, 1.2f)
                    c.drawLine(8.5f, 11f, 15.5f, 11f, line)
                    c.drawLine(8.5f, 15f, 13.5f, 15f, line)
                }
                Kind.MIC -> {
                    round(c, 9f, 2.5f, 15f, 14f, 3f)
                    path(c) { moveTo(5.5f, 11f); quadTo(5.5f, 17.5f, 12f, 17.5f); quadTo(18.5f, 17.5f, 18.5f, 11f) }
                    c.drawLine(12f, 17.5f, 12f, 21.5f, line)
                    c.drawLine(8.5f, 21.5f, 15.5f, 21.5f, line)
                }
                Kind.INFO -> {
                    c.drawCircle(12f, 12f, 9.5f, line)
                    c.drawCircle(12f, 7.9f, 1.15f, dot)
                    c.drawLine(12f, 11f, 12f, 16.5f, line)
                }
                Kind.SEARCH -> {
                    c.drawCircle(10.5f, 10.5f, 6.5f, line)
                    c.drawLine(15.3f, 15.3f, 20.5f, 20.5f, line)
                }
                Kind.CLOSE -> {
                    c.drawLine(6f, 6f, 18f, 18f, line)
                    c.drawLine(18f, 6f, 6f, 18f, line)
                }
                Kind.LOCK -> { // E1.13 (claude-e): the password vault row
                    round(c, 5f, 11f, 19f, 21f, 2f)
                    path(c) { moveTo(8f, 11f); lineTo(8f, 8f); cubicTo(8f, 2.5f, 16f, 2.5f, 16f, 8f); lineTo(16f, 11f) }
                    c.drawCircle(12f, 16f, 1.2f, dot)
                }
                Kind.CHECK -> path(c) { moveTo(5f, 12.5f); lineTo(10f, 17.5f); lineTo(19f, 7f) }
            }
        }
    }
}
