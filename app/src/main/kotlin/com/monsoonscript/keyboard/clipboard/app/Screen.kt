package com.monsoonscript.keyboard.clipboard.app

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.view.View
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/** One tab of [ClipboardActivity]. It listens to the clip store only while it is visible ([show] .. [hide]). */
internal abstract class Screen {
    abstract val view: View

    /** Told when a multi-selection starts or ends, so the activity can swap its tab bar for the screen's action bar. */
    var onSelecting: ((Boolean) -> Unit)? = null

    /** The tab is on screen (again): show the current data and listen for changes. */
    abstract fun show()

    /** The tab went away: stop listening. */
    abstract fun hide()

    /** The Back button; true = the screen used it (ended a selection, left a folder). */
    open fun back(): Boolean = false
}

/** The settings gear, drawn with a few lines (no image files): a ring with eight teeth. */
internal class GearView(context: Context, color: Int) : View(context) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        this.color = color
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.BUTT
    }

    override fun onDraw(canvas: Canvas) {
        val cx = width / 2f
        val cy = height / 2f
        val r = min(width, height) * 0.22f
        paint.strokeWidth = r * 0.5f
        canvas.drawCircle(cx, cy, r, paint)
        paint.strokeWidth = r * 0.55f
        for (i in 0 until 8) {
            val a = i * Math.PI / 4.0
            val x = cos(a).toFloat()
            val y = sin(a).toFloat()
            canvas.drawLine(cx + x * r * 1.2f, cy + y * r * 1.2f, cx + x * r * 1.75f, cy + y * r * 1.75f, paint)
        }
    }
}
