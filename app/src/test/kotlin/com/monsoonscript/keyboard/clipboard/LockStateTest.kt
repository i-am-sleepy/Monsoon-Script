package com.monsoonscript.keyboard.clipboard

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LockStateTest {
    private var now = 1_000_000L
    private val lock = LockState { now }.also { it.enabled = true }

    private fun min(m: Int): Long = m * 60_000L

    @Test
    fun withTheLockOffEverythingIsOpen() {
        val off = LockState { now }
        assertTrue(off.isOpen())
    }

    @Test
    fun startsLockedAndAPromptOpensIt() {
        assertFalse(lock.isOpen())
        lock.grant()
        assertTrue(lock.isOpen())
    }

    @Test
    fun stayingInsideNeverLocksNoMatterHowLong() {
        lock.grant()
        lock.activityStarted()
        now += min(600)
        assertTrue(lock.isOpen())
    }

    @Test
    fun afterLeavingItStaysOpenForTheTimeoutOnly() {
        lock.timeoutMs = min(5)
        lock.grant()
        lock.activityStarted()
        now += min(2)
        lock.activityStopped()
        now += min(4)
        assertTrue(lock.isOpen())
        now += min(2) // 6 minutes after leaving
        assertFalse(lock.isOpen())
        assertFalse(lock.isOpen()) // and it stays locked until the next prompt
        lock.grant()
        assertTrue(lock.isOpen())
    }

    @Test
    fun theKeyboardPanelCountsLikeAScreen() {
        lock.timeoutMs = min(1)
        lock.grant()
        lock.setPanelOpen(true)
        now += min(30)
        assertTrue(lock.isOpen())
        lock.setPanelOpen(true) // repeating it changes nothing
        lock.setPanelOpen(false)
        now += min(1) - 1
        assertTrue(lock.isOpen())
        now += 2
        assertFalse(lock.isOpen())
    }

    @Test
    fun theLockStaysOpenWhileEitherPlaceIsStillOpen() {
        lock.timeoutMs = 0L
        lock.grant()
        lock.activityStarted()
        lock.setPanelOpen(true)
        lock.activityStopped()
        now += min(10)
        assertTrue(lock.isOpen()) // the panel is still open
        lock.setPanelOpen(false)
        assertFalse(lock.isOpen())
    }

    @Test
    fun withTimeoutZeroItLocksAtOnceButLeavesTimeToGetIn() {
        lock.timeoutMs = 0L
        lock.grant()
        now += 10_000
        assertTrue(lock.isOpen()) // the keyboard panel opens a moment after the prompt
        now += 6_000
        assertFalse(lock.isOpen()) // the grace time is over and nobody went in
    }

    @Test
    fun leavingEndsTheGraceTime() {
        lock.timeoutMs = 0L
        lock.grant()
        lock.activityStarted()
        lock.activityStopped()
        now += 1_000
        assertFalse(lock.isOpen())
    }

    @Test
    fun screenOffLocksAtOnce() {
        lock.grant()
        lock.activityStarted()
        lock.lockNow()
        assertFalse(lock.isOpen())
        lock.grant()
        assertTrue(lock.isOpen())
    }

    @Test
    fun switchingTheLockOnStartsLocked() {
        val l = LockState { now }
        l.enabled = true
        l.grant()
        assertTrue(l.isOpen())
        l.enabled = false
        l.enabled = true
        assertFalse(l.isOpen())
    }

    @Test
    fun extraStopsDoNotUnbalanceTheCount() {
        lock.grant()
        lock.activityStopped() // never started: ignored
        lock.activityStarted()
        now += min(100)
        assertTrue(lock.isOpen())
    }
}
