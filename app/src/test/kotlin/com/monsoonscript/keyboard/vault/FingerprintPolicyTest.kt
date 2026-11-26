package com.monsoonscript.keyboard.vault

import com.monsoonscript.keyboard.vault.FingerprintPolicy.Verdict
import org.junit.Assert.assertEquals
import org.junit.Test

class FingerprintPolicyTest {
    private val hour = 60L * 60 * 1000
    private val wall0 = 1_800_000_000_000L // some day
    private val up0 = 10 * hour // the phone had been up for 10 hours when the password was typed

    private fun v(
        armed: Boolean = true, bootAtArming: Int = 7, bootNow: Int = 7,
        passwordWall: Long = wall0, nowWall: Long = wall0 + hour,
        passwordUp: Long = up0, nowUp: Long = up0 + hour,
    ) = FingerprintPolicy.verdict(armed, bootAtArming, bootNow, passwordWall, nowWall, passwordUp, nowUp)

    @Test fun anHourAfterThePasswordIsFine() = assertEquals(Verdict.OK, v())

    @Test fun notSetUpIsNotSetUp() = assertEquals(Verdict.NOT_SET_UP, v(armed = false))

    @Test fun aRestartNeedsThePassword() = assertEquals(Verdict.AFTER_RESTART, v(bootNow = 8))

    @Test fun aBootCounterThatCannotBeReadFailsClosed() = assertEquals(Verdict.AFTER_RESTART, v(bootNow = -1))

    @Test fun exactlySeventyTwoHoursStillWorks() =
        assertEquals(Verdict.OK, v(nowWall = wall0 + FingerprintPolicy.MAX_AGE_MS, nowUp = up0 + FingerprintPolicy.MAX_AGE_MS))

    @Test fun aMinuteMoreThanSeventyTwoHoursDoesNot() =
        assertEquals(Verdict.TOO_OLD, v(nowWall = wall0 + FingerprintPolicy.MAX_AGE_MS + 60_000, nowUp = up0 + FingerprintPolicy.MAX_AGE_MS + 60_000))

    @Test fun aClockMovedBackCountsAsExpired() = assertEquals(Verdict.TOO_OLD, v(nowWall = wall0 - hour))

    @Test fun aClockMovedBackDoesNotHideTheTimeSincePhoneStart() =
        // wall clock was set back to look young, but the phone has been up for four days since the password
        assertEquals(Verdict.TOO_OLD, v(nowWall = wall0 + hour, nowUp = up0 + 96 * hour))

    @Test fun aClockMovedForwardAlsoExpires() = assertEquals(Verdict.TOO_OLD, v(nowWall = wall0 + 100 * hour))

    @Test fun restartWinsOverAge() = assertEquals(Verdict.AFTER_RESTART, v(bootNow = 9, nowWall = wall0 + 200 * hour))
}
