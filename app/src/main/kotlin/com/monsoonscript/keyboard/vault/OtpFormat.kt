package com.monsoonscript.keyboard.vault

/** Small pure rules for one-time codes on screen and in the picker (E1.18, D98). */
internal object OtpFormat {
    /** A code with fewer seconds than this left is not typed at once: the picker waits for the next one (D98). */
    const val MIN_SECONDS_TO_TYPE = 5

    /** The code as shown: "123456" -> "123 456", "12345678" -> "1234 5678", other lengths (and Steam's 5 characters) as they are. The copied text has no space. */
    fun grouped(code: String): String = when (code.length) {
        6 -> code.substring(0, 3) + " " + code.substring(3)
        8 -> code.substring(0, 4) + " " + code.substring(4)
        else -> code
    }

    /**
     * True when the code is about to change: it would probably be rejected by the time the owner sends it. Very short periods (under 15 s) never wait: the
     * next code is never far away and waiting would only be slower.
     */
    fun shouldWait(secondsLeft: Int, period: Int): Boolean = period >= 15 && secondsLeft < MIN_SECONDS_TO_TYPE

    /** Milliseconds from now until the next whole second of [msLeft] ticks over (so a countdown label changes exactly when its number does). */
    fun nextTickDelay(msLeft: Long): Long {
        val r = msLeft % 1000
        return (if (r <= 0) 1000 else r) + 5
    }

    /** Whole seconds shown for [msLeft] (rounded up: "1" until it really is over). */
    fun secondsShown(msLeft: Long): Int = if (msLeft <= 0) 0 else ((msLeft + 999) / 1000).toInt()
}
