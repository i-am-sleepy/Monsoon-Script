package com.monsoonscript.keyboard.view

/**
 * Counts the characters the cursor moves while a finger slides along the space bar (L9, D72). Pure Kotlin, no Android, so it is unit-tested.
 *
 * One drag per finger: [start] when the hold delay is over, then [steps] for every touch move. A remainder smaller than one character is
 * kept (the anchor moves only by whole steps), so slow slides do not lose distance and a slide back undoes exactly what the slide out did.
 */
class SpaceDrag {
    private var anchor = 0f

    /** True once at least one character was moved: releasing then does NOT type a space. */
    var moved = false
        private set

    fun start(x: Float) {
        anchor = x
        moved = false
    }

    /** Whole characters to move now for a finger at [x]: positive = right, negative = left, 0 = not a whole step yet. [stepPx] pixels make one character. */
    fun steps(x: Float, stepPx: Float): Int {
        if (stepPx <= 0f) return 0
        val n = ((x - anchor) / stepPx).toInt() // toward zero: a step counts only when it is complete
        if (n == 0) return 0
        anchor += n * stepPx
        moved = true
        return n
    }

    companion object {
        /** Speed setting: 1 slow ... 10 fast. */
        const val SPEED_MIN = 1
        const val SPEED_MAX = 10
        const val SPEED_DEFAULT = 6

        /** Hold delay setting in units of [DELAY_UNIT_MS]: 150 ms ... 800 ms. */
        const val DELAY_UNIT_MS = 50
        const val DELAY_MIN = 3
        const val DELAY_MAX = 16
        const val DELAY_DEFAULT = 7

        /** The slide, in dp, that makes one character: 24 dp at speed 1 down to 4 dp at speed 10 (13 dp at the default 6, about 25 characters across a phone). */
        fun stepDp(speed: Int): Float = 24f - (speed.coerceIn(SPEED_MIN, SPEED_MAX) - 1) * 20f / 9f

        fun delayMs(units: Int): Long = units.coerceIn(DELAY_MIN, DELAY_MAX) * DELAY_UNIT_MS.toLong()
    }
}
