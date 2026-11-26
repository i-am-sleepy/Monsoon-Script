package com.monsoonscript.keyboard.vault

/**
 * When the fingerprint may open the vault (D39, D91). Pure rules, no Android: the phone's boot counter and clocks come in as numbers so the rules can be tested
 * on a computer. The Keystore key itself enforces "a finger every time" and "not after the fingerprints changed"; these two rules the key cannot know:
 *  - after the phone restarted, the master password is needed once (the boot counter differs from the one saved when the fingerprint was set up);
 *  - after 72 hours without typing the master password, it is needed again (the age is measured twice, with the wall clock and with the clock that counts since
 *    the phone started; a clock moved back makes the wall clock age negative and counts as expired, so moving the date cannot stretch the time).
 */
internal object FingerprintPolicy {
    const val MAX_AGE_MS = 72L * 60 * 60 * 1000

    enum class Verdict { OK, NOT_SET_UP, AFTER_RESTART, TOO_OLD }

    /**
     * @param armed the key and the stored password exist
     * @param bootAtArming boot counter saved when the fingerprint was set up
     * @param bootNow boot counter now; below zero when the phone does not tell (the rule cannot be kept: not OK)
     * @param wallAtPassword wall clock (ms) of the last master password unlock
     * @param elapsedAtPassword time since the phone started (ms) at that unlock; only comparable while the boot counter is the same
     */
    fun verdict(armed: Boolean, bootAtArming: Int, bootNow: Int, wallAtPassword: Long, wallNow: Long, elapsedAtPassword: Long, elapsedNow: Long): Verdict {
        if (!armed) return Verdict.NOT_SET_UP
        if (bootNow < 0 || bootNow != bootAtArming) return Verdict.AFTER_RESTART
        val wallAge = wallNow - wallAtPassword
        if (wallAge < 0 || wallAge > MAX_AGE_MS) return Verdict.TOO_OLD
        val elapsedAge = elapsedNow - elapsedAtPassword
        if (elapsedAge < 0 || elapsedAge > MAX_AGE_MS) return Verdict.TOO_OLD
        return Verdict.OK
    }
}
