package com.monsoonscript.keyboard.theme

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.View
import kotlin.math.max
import kotlin.math.min

/**
 * The crop screen's stage (L10 step 2b, D90): the whole picture, dimmed, with a bright keyboard-shaped frame on it that shows what the keyboard will show. One
 * finger drags the picture, two fingers pinch it bigger or smaller. [aspect] is the shape of the frame (width / height); [onChanged] is called when a gesture ends.
 */
internal class CropFrameView(
    context: Context,
    private val bitmap: Bitmap,
    private val veil: Int,
    private val accent: Int,
    private val muted: Int,
) : View(context) {
    var aspect = 1.5f
        set(v) {
            field = v
            invalidate()
        }
    var onChanged: ((PictureCrop) -> Unit)? = null

    private var shown: Bitmap = bitmap
    private var zoom = 1f
    private var posX = 50f
    private var posY = 50f

    private val density = resources.displayMetrics.density
    private val bitmapPaint = Paint(Paint.FILTER_BITMAP_FLAG)
    private val dimPaint = Paint(Paint.FILTER_BITMAP_FLAG).apply { alpha = 95 }
    private val outline = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 2f * density
        color = accent
    }
    private val guide = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 1f * density
        color = (muted and 0x00FFFFFF) or 0x66000000
    }
    private val frame = RectF()
    private val dst = RectF()

    /** The picture as it will be drawn inside the frame (a blurred copy of [bitmap] for a blurred target); the dimmed part outside stays the original. */
    fun setShown(b: Bitmap) {
        shown = b
        invalidate()
    }

    fun setCrop(crop: PictureCrop) {
        zoom = crop.zoom / 100f
        posX = crop.x.toFloat()
        posY = crop.y.toFloat()
        invalidate()
    }

    fun crop(): PictureCrop = PictureCrop(Math.round(zoom * 100f), Math.round(posX), Math.round(posY))

    private fun layoutFrame() {
        val m = 16f * density
        val fw = min(width - 2 * m, (height - 2 * m) * aspect)
        val fh = fw / aspect
        frame.set((width - fw) / 2f, (height - fh) / 2f, (width + fw) / 2f, (height + fh) / 2f)
    }

    /** The visible part of the picture (left, top, right, bottom in picture pixels) for the current zoom and position. */
    private fun visible(): FloatArray = PictureMath.visibleRect(bitmap.width, bitmap.height, frame.width(), frame.height(), zoom, posX, posY)

    override fun onDraw(canvas: Canvas) {
        if (width == 0 || height == 0) return
        layoutFrame()
        val v = visible()
        val s = frame.width() / (v[2] - v[0])
        dst.set(frame.left - v[0] * s, frame.top - v[1] * s, frame.left + (bitmap.width - v[0]) * s, frame.top + (bitmap.height - v[1]) * s)
        canvas.drawBitmap(bitmap, null, dst, dimPaint) // the whole picture, dimmed: what is outside the frame
        canvas.save()
        canvas.clipRect(frame)
        canvas.drawBitmap(shown, null, dst, bitmapPaint) // what the keyboard shows, as the keyboard will show it
        canvas.drawColor(veil)
        canvas.restore()
        // faint key rows, to see where keys and letters will sit
        val rows = 4
        for (i in 1 until rows) {
            val y = frame.top + frame.height() * (0.18f + 0.82f * i / rows)
            canvas.drawLine(frame.left + 6f * density, y, frame.right - 6f * density, y, guide)
        }
        canvas.drawRoundRect(frame, 6f * density, 6f * density, outline)
    }

    // ---- gestures

    private var lastX = 0f
    private var lastY = 0f
    private var moving = false

    private val scaler = ScaleGestureDetector(context, object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
        override fun onScale(d: ScaleGestureDetector): Boolean {
            val before = visible()
            val sBefore = frame.width() / (before[2] - before[0])
            // the picture point under the fingers stays under the fingers
            val fx = (d.focusX - frame.left).coerceIn(0f, frame.width())
            val fy = (d.focusY - frame.top).coerceIn(0f, frame.height())
            val px = before[0] + fx / sBefore
            val py = before[1] + fy / sBefore
            zoom = (zoom * d.scaleFactor).coerceIn(PictureCrop.MIN_ZOOM / 100f, PictureCrop.MAX_ZOOM / 100f)
            val probe = PictureMath.visibleRect(bitmap.width, bitmap.height, frame.width(), frame.height(), zoom, 0f, 0f)
            val sAfter = frame.width() / (probe[2] - probe[0])
            moveTo(px - fx / sAfter, py - fy / sAfter)
            invalidate()
            return true
        }
    })

    /** Put the left / top of the visible part at [left] / [top] picture pixels (the position is kept inside the picture). */
    private fun moveTo(left: Float, top: Float) {
        val p = PictureMath.positionFor(bitmap.width, bitmap.height, frame.width(), frame.height(), zoom, left, top)
        posX = p[0]
        posY = p[1]
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(e: MotionEvent): Boolean {
        parent?.requestDisallowInterceptTouchEvent(true) // the page must not scroll while the picture is being moved
        scaler.onTouchEvent(e)
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_POINTER_DOWN -> {
                lastX = e.x
                lastY = e.y
                moving = e.pointerCount == 1
            }
            MotionEvent.ACTION_POINTER_UP -> { // one finger stays: carry on dragging from where it is
                val i = if (e.actionIndex == 0) 1 else 0
                lastX = e.getX(i)
                lastY = e.getY(i)
                moving = e.pointerCount - 1 == 1
            }
            MotionEvent.ACTION_MOVE -> if (moving && e.pointerCount == 1 && !scaler.isInProgress) {
                val dx = e.x - lastX
                val dy = e.y - lastY
                lastX = e.x
                lastY = e.y
                val v = visible()
                val s = frame.width() / (v[2] - v[0])
                moveTo(v[0] - dx / s, v[1] - dy / s)
                invalidate()
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                moving = false
                onChanged?.invoke(crop())
            }
        }
        return true
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        setMeasuredDimension(MeasureSpec.getSize(widthMeasureSpec), max((300f * density).toInt(), 1))
    }
}
