package com.monsoonscript.keyboard.theme

/**
 * How a background picture is placed in the keyboard (L10 step 2b, D90), kept for portrait and for landscape: [zoom] in percent (100 = the picture just covers the
 * keyboard, up to 400), and which part is kept as [x] / [y] in percent of the room there is (0 = left / top edge of the picture, 50 = the middle, 100 = right /
 * bottom edge). The default (100, 50, 50) is the automatic crop: the middle of the picture. The numbers are always kept inside their limits.
 */
class PictureCrop(zoom: Int = 100, x: Int = 50, y: Int = 50) {
    val zoom: Int = zoom.coerceIn(MIN_ZOOM, MAX_ZOOM)
    val x: Int = x.coerceIn(0, 100)
    val y: Int = y.coerceIn(0, 100)

    val isAuto: Boolean get() = this == AUTO

    override fun equals(other: Any?): Boolean = other is PictureCrop && other.zoom == zoom && other.x == x && other.y == y

    override fun hashCode(): Int = (zoom * 31 + x) * 31 + y

    override fun toString(): String = "PictureCrop($zoom, $x, $y)"

    fun toMap(): Map<String, Any?> = mapOf("z" to zoom, "x" to x, "y" to y)

    companion object {
        const val MIN_ZOOM = 100
        const val MAX_ZOOM = 400
        val AUTO = PictureCrop()

        /** The crop in saved [m] ({"z","x","y"}); anything missing or damaged gives the automatic value for that number. */
        fun fromMap(m: Map<*, *>?): PictureCrop {
            if (m == null) return AUTO
            return PictureCrop((m["z"] as? Number)?.toInt() ?: 100, (m["x"] as? Number)?.toInt() ?: 50, (m["y"] as? Number)?.toInt() ?: 50)
        }
    }
}
