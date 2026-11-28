package com.monsoonscript.keyboard.view

import android.content.Context
import android.graphics.Canvas
import android.widget.LinearLayout

/**
 * The column that holds the keys and the panels in the keyboard window (L10 step 2c, D103). With the "use the notch area" option on (landscape) the window reaches under
 * the camera cutout and the column gets side padding so the keys stay clear of it; this class paints the background (picture or color) into that padding, and the
 * keys and panels line their own picture up with the whole column ([KeyboardView.Palette]). Without padding it does nothing.
 */
class CutoutRoot(context: Context, private val palette: () -> KeyboardView.Palette?) : LinearLayout(context) {
    fun hasSidePadding(): Boolean = paddingLeft > 0 || paddingRight > 0

    /** Set the padding the cutout needs (0 = none). */
    fun setCutoutPadding(left: Int, right: Int) {
        if (left == paddingLeft && right == paddingRight) return
        setPadding(left, 0, right, 0)
        invalidate()
    }

    override fun dispatchDraw(canvas: Canvas) {
        if (hasSidePadding()) palette()?.paintBackground(this, canvas)
        super.dispatchDraw(canvas)
    }
}
