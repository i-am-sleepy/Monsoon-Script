package com.monsoonscript.keyboard.theme

/**
 * The cheap blur of the background pictures (L10 step 2c, D103): the picture is shrunk to [EDGE] pixels on its long side ONCE, blurred with three passes of a box
 * blur (together close to a Gaussian) and kept; the views then draw that small picture scaled up, which is smooth by itself. Nothing is blurred while drawing. Works on
 * a plain array of ARGB ints, so it is tested without Android.
 */
object PictureBlur {
    /** The long side of the picture that is blurred (a blur has no detail to keep, and 240 pixels blur in a few milliseconds). */
    const val EDGE = 240

    /** The box radius at 100 %: about 6 % of the width. */
    const val MAX_RADIUS = 14

    /** The radius for an intensity of 0..100 % (0 = no blur at all). */
    fun radius(intensity: Int): Int = (intensity.coerceIn(0, 100) * MAX_RADIUS + 50) / 100

    fun blurInPlace(px: IntArray, w: Int, h: Int, radius: Int) {
        if (radius <= 0 || w <= 0 || h <= 0 || px.size < w * h) return
        val tmp = IntArray(w * h)
        repeat(3) {
            pass(px, tmp, w, h, radius, horizontal = true)
            pass(tmp, px, w, h, radius, horizontal = false)
        }
    }

    /** One box blur of every line in one direction, edges repeated; [src] to [dst]. */
    private fun pass(src: IntArray, dst: IntArray, w: Int, h: Int, r: Int, horizontal: Boolean) {
        val lines = if (horizontal) h else w
        val len = if (horizontal) w else h
        val step = if (horizontal) 1 else w
        val size = 2 * r + 1
        for (line in 0 until lines) {
            val base = if (horizontal) line * w else line
            var a = 0
            var rr = 0
            var g = 0
            var b = 0
            for (i in -r..r) {
                val p = src[base + i.coerceIn(0, len - 1) * step]
                a += p ushr 24
                rr += (p shr 16) and 0xFF
                g += (p shr 8) and 0xFF
                b += p and 0xFF
            }
            for (i in 0 until len) {
                val half = size / 2 // rounded, not cut off: six passes of cutting would make the picture darker
                dst[base + i * step] = (((a + half) / size) shl 24) or (((rr + half) / size) shl 16) or (((g + half) / size) shl 8) or ((b + half) / size)
                val add = src[base + (i + r + 1).coerceAtMost(len - 1) * step]
                val del = src[base + (i - r).coerceAtLeast(0) * step]
                a += (add ushr 24) - (del ushr 24)
                rr += ((add shr 16) and 0xFF) - ((del shr 16) and 0xFF)
                g += ((add shr 8) and 0xFF) - ((del shr 8) and 0xFF)
                b += (add and 0xFF) - (del and 0xFF)
            }
        }
    }
}
