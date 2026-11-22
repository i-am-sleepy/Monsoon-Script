package com.monsoonscript.keyboard.clipboard

/**
 * Whether the clipboard is unlocked right now (E3.9, D71). Pure Kotlin on a clock you hand in, unit tested; the Android side ([ClipUnlockActivity],
 * the app's gate, the keyboard panel) only asks [isOpen] and reports when it was shown or left. Lives in memory only: when the app's process dies it starts
 * locked again.
 *
 * Rules: a successful prompt [grant]s access. While a clipboard screen is on screen ([activityStarted] ... [activityStopped]) or the keyboard panel is open
 * ([setPanelOpen]) it stays open however long that takes. After the last one is left it stays open for [timeoutMs] (0 = locks at once). Right after a grant
 * there is a short grace time to get in (the keyboard panel opens a moment after the prompt closes), which ends when you leave. [lockNow] is for the screen
 * turning off.
 */
class LockState(private val clock: () -> Long) {
    /** Is the lock switched on? Switching it on starts locked. */
    var enabled: Boolean = false
        set(value) {
            if (value && !field) granted = false
            field = value
        }

    /** How long after leaving the clipboard it stays unlocked. */
    var timeoutMs: Long = 60_000L

    private var granted = false
    private var graceUntil = 0L
    private var leftAt = 0L
    private var activities = 0
    private var panelOpen = false

    fun isOpen(): Boolean {
        if (!enabled) return true
        if (!granted) return false
        if (activities > 0 || panelOpen) return true
        val now = clock()
        if (now < graceUntil || now - leftAt < timeoutMs) return true
        granted = false
        return false
    }

    /** The prompt succeeded. */
    fun grant() {
        granted = true
        val now = clock()
        leftAt = now
        graceUntil = now + GRACE_MS
    }

    /** Lock at once (the screen turned off). */
    fun lockNow() {
        granted = false
    }

    /** A clipboard screen of the app became visible. */
    fun activityStarted() {
        activities++
    }

    /** A clipboard screen of the app went away. */
    fun activityStopped() {
        if (activities > 0) {
            activities--
            markLeft()
        }
    }

    /** The keyboard's clipboard panel opened or closed (calling it again with the same value does nothing). */
    fun setPanelOpen(open: Boolean) {
        if (panelOpen == open) return
        panelOpen = open
        if (!open) markLeft()
    }

    private fun markLeft() {
        if (activities == 0 && !panelOpen) {
            leftAt = clock()
            graceUntil = 0L
        }
    }

    companion object {
        /** Time to get in after a successful prompt even when the timeout is 0. */
        const val GRACE_MS = 15_000L
    }
}
