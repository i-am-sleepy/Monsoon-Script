package com.monsoonscript.keyboard.view

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import com.monsoonscript.keyboard.theme.PictureCrop
import com.monsoonscript.keyboard.theme.PictureMath

/**
 * The picture behind the keyboard and its panels (L10 step 2, D87): [bitmap] scaled to cover the view and cut in the middle, then a see-through [veil] color over it
 * (the fade). The views draw it instead of the plain background color; two backdrops are equal when [key] (which picture file, which version) and the veil match, so
 * a view is not redrawn for a theme that did not change.
 */
class Backdrop(
    val key: String,
    private val bitmap: Bitmap,
    val veil: Int,
    private val cropPortrait: PictureCrop = PictureCrop.AUTO,
    private val cropLandscape: PictureCrop = PictureCrop.AUTO,
) {
    private val paint = Paint(Paint.FILTER_BITMAP_FLAG)
    private val src = Rect()
    private val dst = Rect()

    /** Fill a view of [w] x [h] (or the clip of the canvas inside it) with the picture, placed as set for the phone held upright or on its side ([landscape]). */
    fun draw(c: Canvas, w: Int, h: Int, landscape: Boolean) {
        if (w <= 0 || h <= 0) return
        val r = PictureMath.cropRect(bitmap.width, bitmap.height, w, h, if (landscape) cropLandscape else cropPortrait)
        src.set(r[0], r[1], r[2], r[3])
        dst.set(0, 0, w, h)
        c.drawBitmap(bitmap, src, dst, paint)
        if (veil ushr 24 != 0) c.drawColor(veil)
    }

    override fun equals(other: Any?): Boolean =
        other is Backdrop && other.key == key && other.veil == veil && other.cropPortrait == cropPortrait && other.cropLandscape == cropLandscape

    override fun hashCode(): Int = ((key.hashCode() * 31 + veil) * 31 + cropPortrait.hashCode()) * 31 + cropLandscape.hashCode()

    override fun toString(): String = "Backdrop($key)"
}
