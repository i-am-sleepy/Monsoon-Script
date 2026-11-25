package com.monsoonscript.keyboard.theme

import kotlin.math.max
import kotlin.math.roundToInt

/** The numbers behind the background pictures (L10 step 2, D87), without any Android class so they are unit-tested. */
object PictureMath {
    /** The longest side a stored picture gets (a keyboard is at most about this wide; bigger photos only cost memory). */
    const val MAX_EDGE = 1600

    private val NAME = Regex("[A-Za-z0-9]{1,40}\\.jpg")

    /** A picture file name made by this app: letters and digits and ".jpg", never a path. */
    fun isValidFileName(name: String?): Boolean = name != null && NAME.matches(name)

    /** The power of two to decode a [w] x [h] photo with, so the result is still at least [maxEdge] on its longest side (when the photo has that many pixels). */
    fun sampleSize(w: Int, h: Int, maxEdge: Int = MAX_EDGE): Int {
        var s = 1
        val edge = max(w, h)
        while (edge / (s * 2) >= maxEdge) s *= 2
        return s
    }

    /** The size a [w] x [h] picture is stored at: the longest side at most [maxEdge], the shape kept, never enlarged. */
    fun scaledSize(w: Int, h: Int, maxEdge: Int = MAX_EDGE): Pair<Int, Int> {
        val edge = max(w, h)
        if (edge <= maxEdge) return w to h
        val k = maxEdge.toDouble() / edge
        return max(1, (w * k).roundToInt()) to max(1, (h * k).roundToInt())
    }

    /**
     * The part of a [bw] x [bh] picture a [w] x [h] view shows, in picture pixels: left, top, right, bottom (floats). At [zoom] 1 the picture is scaled to just
     * cover the view; a bigger zoom shows a smaller part. [x] / [y] (0..100) say where that part sits in the room the picture leaves (50 = the middle).
     */
    fun visibleRect(bw: Int, bh: Int, w: Float, h: Float, zoom: Float, x: Float, y: Float): FloatArray {
        if (bw <= 0 || bh <= 0 || w <= 0f || h <= 0f) return floatArrayOf(0f, 0f, max(bw, 0).toFloat(), max(bh, 0).toFloat())
        val z = zoom.coerceAtLeast(1f)
        val baseW: Float
        val baseH: Float
        if (bw.toDouble() * h > bh.toDouble() * w) { // the picture is wider than the view: at zoom 1 the full height shows
            baseH = bh.toFloat()
            baseW = bh * w / h
        } else {
            baseW = bw.toFloat()
            baseH = bw * h / w
        }
        val sw = (baseW / z).coerceIn(1f, bw.toFloat())
        val sh = (baseH / z).coerceIn(1f, bh.toFloat())
        val l = (bw - sw) * x.coerceIn(0f, 100f) / 100f
        val t = (bh - sh) * y.coerceIn(0f, 100f) / 100f
        return floatArrayOf(l, t, l + sw, t + sh)
    }

    /** [visibleRect] as whole pixels for the given [crop]: left, top, right, bottom. */
    fun cropRect(bw: Int, bh: Int, w: Int, h: Int, crop: PictureCrop = PictureCrop.AUTO): IntArray {
        if (bw <= 0 || bh <= 0 || w <= 0 || h <= 0) return intArrayOf(0, 0, max(bw, 0), max(bh, 0))
        val v = visibleRect(bw, bh, w.toFloat(), h.toFloat(), crop.zoom / 100f, crop.x.toFloat(), crop.y.toFloat())
        val l = v[0].roundToInt().coerceIn(0, bw - 1)
        val t = v[1].roundToInt().coerceIn(0, bh - 1)
        val r = v[2].roundToInt().coerceIn(l + 1, bw)
        val b = v[3].roundToInt().coerceIn(t + 1, bh)
        return intArrayOf(l, t, r, b)
    }

    /** The [x] / [y] (0..100) that put the visible part of a [bw] x [bh] picture, shown at [zoom] in a [w] x [h] view, at [left] / [top] picture pixels. */
    fun positionFor(bw: Int, bh: Int, w: Float, h: Float, zoom: Float, left: Float, top: Float): FloatArray {
        val v = visibleRect(bw, bh, w, h, zoom, 0f, 0f)
        val roomX = bw - (v[2] - v[0])
        val roomY = bh - (v[3] - v[1])
        val x = if (roomX > 0.5f) (left / roomX * 100f).coerceIn(0f, 100f) else 50f
        val y = if (roomY > 0.5f) (top / roomY * 100f).coerceIn(0f, 100f) else 50f
        return floatArrayOf(x, y)
    }

    /**
     * The shape (width / height) of the keyboard, to draw the crop frame in: [widthDp] is the screen's short side in portrait or its long side in landscape,
     * [heightPercent] the key height setting, [rows] 4 or 5 (number row). The same sizes the keyboard view uses (strip 44 / 38 dp, key row 52 / 42 dp, small padding).
     */
    fun keyboardAspect(landscape: Boolean, widthDp: Float, heightPercent: Int, rows: Int): Float {
        val strip = if (landscape) 38f else 44f
        val row = (if (landscape) 42f else 52f) * heightPercent.coerceIn(60, 160) / 100f
        val h = strip + row * rows + 8f
        return (widthDp / h).coerceIn(1f, 8f)
    }

    /** The alpha (0..255) of the veil that fades a picture by [fadePercent] %. */
    fun veilAlpha(fadePercent: Int): Int = (fadePercent.coerceIn(0, ThemeSpec.MAX_FADE) * 255 + 50) / 100
}
