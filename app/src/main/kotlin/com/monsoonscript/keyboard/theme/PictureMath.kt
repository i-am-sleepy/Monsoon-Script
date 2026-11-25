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
     * The part of a [bw] x [bh] picture that fills a [w] x [h] view when the picture is scaled to cover the view and cut at the edges, in the middle:
     * left, top, right, bottom in picture pixels.
     */
    fun cropRect(bw: Int, bh: Int, w: Int, h: Int): IntArray {
        if (bw <= 0 || bh <= 0 || w <= 0 || h <= 0) return intArrayOf(0, 0, max(bw, 0), max(bh, 0))
        val sw: Int
        val sh: Int
        if (bw.toLong() * h > bh.toLong() * w) { // the picture is wider than the view: use the full height
            sh = bh
            sw = (bh.toLong() * w / h).toInt().coerceIn(1, bw)
        } else {
            sw = bw
            sh = (bw.toLong() * h / w).toInt().coerceIn(1, bh)
        }
        val l = (bw - sw) / 2
        val t = (bh - sh) / 2
        return intArrayOf(l, t, l + sw, t + sh)
    }

    /** The alpha (0..255) of the veil that fades a picture by [fadePercent] %. */
    fun veilAlpha(fadePercent: Int): Int = (fadePercent.coerceIn(0, ThemeSpec.MAX_FADE) * 255 + 50) / 100
}
