package com.monsoonscript.keyboard.vault

import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.RectF
import android.graphics.drawable.Drawable

/**
 * The vault screens' line icons, drawn like the app's other icons (settings/Icons.kt, L12) on a 24 x 24 grid: no picture files, they take the color given.
 * Kept in the vault package so the vault work does not touch the app-wide icon file.
 */
internal object VaultIcons {
    enum class Kind { LOCK, KEY, FOLDER, PLUS, COPY, EYE, EYE_OFF, TRASH, EDIT, REFRESH, MORE, LINK, SAVE_FILE }

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
        }

        @Deprecated("Deprecated in Java")
        override fun getOpacity(): Int = PixelFormat.TRANSLUCENT

        override fun draw(canvas: Canvas) {
            val b = bounds
            if (b.isEmpty) return
            line.color = color
            line.alpha = alphaValue
            dot.color = color
            dot.alpha = alphaValue
            canvas.save()
            canvas.translate(b.left.toFloat(), b.top.toFloat())
            canvas.scale(b.width() / 24f, b.height() / 24f)
            when (kind) {
                Kind.LOCK -> {
                    box.set(5f, 11f, 19f, 21f)
                    canvas.drawRoundRect(box, 2f, 2f, line)
                    val p = Path().apply {
                        moveTo(8f, 11f)
                        lineTo(8f, 8f)
                        arcTo(RectF(8f, 4f, 16f, 12f), 180f, 180f)
                        lineTo(16f, 11f)
                    }
                    canvas.drawPath(p, line)
                    canvas.drawCircle(12f, 16f, 1.2f, dot)
                }
                Kind.KEY -> {
                    canvas.drawCircle(8f, 15f, 4f, line)
                    canvas.drawLine(11f, 12f, 20f, 3f, line)
                    canvas.drawLine(17f, 6f, 20f, 9f, line)
                    canvas.drawLine(14.5f, 8.5f, 16.5f, 10.5f, line)
                }
                Kind.FOLDER -> {
                    val p = Path().apply {
                        moveTo(3f, 7f)
                        lineTo(9f, 7f)
                        lineTo(11f, 9f)
                        lineTo(21f, 9f)
                        lineTo(21f, 19f)
                        lineTo(3f, 19f)
                        close()
                    }
                    canvas.drawPath(p, line)
                }
                Kind.PLUS -> {
                    canvas.drawLine(12f, 5f, 12f, 19f, line)
                    canvas.drawLine(5f, 12f, 19f, 12f, line)
                }
                Kind.COPY -> {
                    box.set(9f, 9f, 20f, 20f)
                    canvas.drawRoundRect(box, 2f, 2f, line)
                    val p = Path().apply {
                        moveTo(15f, 9f)
                        lineTo(15f, 5f)
                        lineTo(4f, 5f)
                        lineTo(4f, 15f)
                        lineTo(9f, 15f)
                    }
                    canvas.drawPath(p, line)
                }
                Kind.EYE, Kind.EYE_OFF -> {
                    val p = Path().apply {
                        moveTo(2f, 12f)
                        cubicTo(5f, 6f, 19f, 6f, 22f, 12f)
                        cubicTo(19f, 18f, 5f, 18f, 2f, 12f)
                    }
                    canvas.drawPath(p, line)
                    canvas.drawCircle(12f, 12f, 3f, line)
                    if (kind == Kind.EYE_OFF) canvas.drawLine(4f, 4f, 20f, 20f, line)
                }
                Kind.TRASH -> {
                    canvas.drawLine(4f, 7f, 20f, 7f, line)
                    val lid = Path().apply {
                        moveTo(9f, 7f)
                        lineTo(9f, 4f)
                        lineTo(15f, 4f)
                        lineTo(15f, 7f)
                    }
                    canvas.drawPath(lid, line)
                    val body = Path().apply {
                        moveTo(6f, 7f)
                        lineTo(7f, 20f)
                        lineTo(17f, 20f)
                        lineTo(18f, 7f)
                    }
                    canvas.drawPath(body, line)
                    canvas.drawLine(10f, 11f, 10f, 16f, line)
                    canvas.drawLine(14f, 11f, 14f, 16f, line)
                }
                Kind.EDIT -> {
                    val p = Path().apply {
                        moveTo(4f, 20f)
                        lineTo(5f, 15f)
                        lineTo(16f, 4f)
                        lineTo(20f, 8f)
                        lineTo(9f, 19f)
                        close()
                    }
                    canvas.drawPath(p, line)
                    canvas.drawLine(14f, 6f, 18f, 10f, line)
                }
                Kind.REFRESH -> {
                    box.set(5f, 5f, 19f, 19f)
                    canvas.drawArc(box, 40f, 280f, false, line)
                    canvas.drawLine(18.5f, 3.5f, 18.5f, 8f, line)
                    canvas.drawLine(18.5f, 8f, 14f, 8f, line)
                }
                Kind.MORE -> {
                    canvas.drawCircle(12f, 5f, 1.8f, dot)
                    canvas.drawCircle(12f, 12f, 1.8f, dot)
                    canvas.drawCircle(12f, 19f, 1.8f, dot)
                }
                Kind.LINK -> {
                    val p = Path().apply {
                        moveTo(14f, 4f)
                        lineTo(20f, 4f)
                        lineTo(20f, 10f)
                    }
                    canvas.drawPath(p, line)
                    canvas.drawLine(20f, 4f, 11f, 13f, line)
                    val frame = Path().apply {
                        moveTo(17f, 14f)
                        lineTo(17f, 20f)
                        lineTo(4f, 20f)
                        lineTo(4f, 7f)
                        lineTo(10f, 7f)
                    }
                    canvas.drawPath(frame, line)
                }
                Kind.SAVE_FILE -> {
                    val p = Path().apply {
                        moveTo(6f, 3f)
                        lineTo(14f, 3f)
                        lineTo(19f, 8f)
                        lineTo(19f, 21f)
                        lineTo(6f, 21f)
                        close()
                    }
                    canvas.drawPath(p, line)
                    canvas.drawLine(14f, 3f, 14f, 8f, line)
                    canvas.drawLine(14f, 8f, 19f, 8f, line)
                }
            }
            canvas.restore()
        }
    }
}
