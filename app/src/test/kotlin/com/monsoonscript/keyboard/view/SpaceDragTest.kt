package com.monsoonscript.keyboard.view

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SpaceDragTest {
    private fun drag(x: Float = 100f) = SpaceDrag().also { it.start(x) }

    @Test fun lessThanOneStepMovesNothingAndIsNotAMove() {
        val d = drag()
        assertEquals(0, d.steps(109f, 10f))
        assertFalse(d.moved)
    }

    @Test fun movesOneCharacterPerWholeStep() {
        val d = drag()
        assertEquals(1, d.steps(110f, 10f))
        assertTrue(d.moved)
        assertEquals(0, d.steps(115f, 10f)) // 5 px into the next step
        assertEquals(1, d.steps(121f, 10f))
    }

    @Test fun aFastSlideMovesSeveralCharactersAtOnce() {
        assertEquals(4, drag().steps(145f, 10f))
        assertEquals(-3, drag().steps(70f, 10f))
    }

    @Test fun slidingBackUndoesExactlyWhatTheSlideOutDid() {
        val d = drag()
        var total = 0
        for (x in listOf(112f, 127f, 143f, 160f, 150f, 131f, 108f, 100f)) total += d.steps(x, 10f)
        assertEquals(0, total) // back at the start point: net zero characters
    }

    @Test fun theRemainderIsKeptSoSlowSlidesLoseNothing() {
        val d = drag(0f)
        var total = 0
        var x = 0f
        repeat(100) { x += 1f; total += d.steps(x, 10f) } // 100 tiny moves of 1 px
        assertEquals(10, total)
    }

    @Test fun aNewDragStartsFresh() {
        val d = drag()
        d.steps(150f, 10f)
        d.start(300f)
        assertFalse(d.moved)
        assertEquals(0, d.steps(305f, 10f))
    }

    @Test fun aBrokenStepSizeNeverMoves() {
        assertEquals(0, drag().steps(500f, 0f))
        assertEquals(0, drag().steps(500f, -3f))
    }

    @Test fun speedAndDelaySettingsStayInRange() {
        assertEquals(24f, SpaceDrag.stepDp(1), 1e-4f)
        assertEquals(4f, SpaceDrag.stepDp(10), 1e-4f)
        assertEquals(SpaceDrag.stepDp(1), SpaceDrag.stepDp(-5), 1e-4f)
        assertEquals(SpaceDrag.stepDp(10), SpaceDrag.stepDp(99), 1e-4f)
        assertTrue(SpaceDrag.stepDp(6) in 12f..14f)
        assertEquals(350L, SpaceDrag.delayMs(SpaceDrag.DELAY_DEFAULT))
        assertEquals(150L, SpaceDrag.delayMs(0))
        assertEquals(800L, SpaceDrag.delayMs(100))
    }

    @Test fun fasterSpeedMeansASmallerStep() {
        for (s in 1 until 10) assertTrue(SpaceDrag.stepDp(s + 1) < SpaceDrag.stepDp(s))
    }
}
