package com.monsoonscript.keyboard.autofill

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SaveRulesTest {
    private fun update(d: SaveDecision) = (d as SaveDecision.Update).id

    @Test
    fun aNewLoginIsNew() {
        assertTrue(SaveRules.decide("anna", emptyList()) is SaveDecision.New)
        assertTrue(SaveRules.decide("anna", listOf(Candidate("1", "bob", false))) is SaveDecision.New)
    }

    @Test
    fun theSameLoginAgainAsksNothing() {
        assertTrue(SaveRules.decide("anna", listOf(Candidate("1", "anna", true))) === SaveDecision.Nothing)
        // user names of e-mail logins differ only in case
        assertTrue(SaveRules.decide("Anna@Example.com", listOf(Candidate("1", "anna@example.com", true))) === SaveDecision.Nothing)
        // one of several entries has it
        assertTrue(SaveRules.decide("anna", listOf(Candidate("1", "anna", false), Candidate("2", "anna", true))) === SaveDecision.Nothing)
    }

    @Test
    fun aChangedPasswordOffersAnUpdateOfThatEntry() {
        assertEquals("1", update(SaveRules.decide("anna", listOf(Candidate("2", "bob", false), Candidate("1", "anna", false)))))
        assertEquals("1", update(SaveRules.decide(" anna ", listOf(Candidate("1", "ANNA", false)))))
    }

    @Test
    fun withoutAUserNameNothingIsEverOverwritten() {
        assertTrue(SaveRules.decide("", listOf(Candidate("1", "", false))) is SaveDecision.New)
        assertTrue(SaveRules.decide("  ", listOf(Candidate("1", "", true))) is SaveDecision.New)
    }

    @Test
    fun theDefaultTitleIsTheSiteOrTheApp() {
        assertEquals("github.com", SaveRules.defaultTitle("www.GitHub.com", "com.android.chrome", "Chrome"))
        assertEquals("accounts.google.com", SaveRules.defaultTitle("accounts.google.com", "com.android.chrome", null))
        assertEquals("Twitter", SaveRules.defaultTitle(null, "com.twitter.android", " Twitter "))
        assertEquals("com.twitter.android", SaveRules.defaultTitle(null, "com.twitter.android", null))
        assertEquals("com.twitter.android", SaveRules.defaultTitle("  ", "com.twitter.android", ""))
    }
}

class SaveHandoffTest {
    private fun save(password: String = "pw") = PendingSave("com.example", "example.com", "anna", password)

    @Test
    fun aLoginIsHandedOutOnce() {
        val token = SaveHandoff.put(save("secret"), now = 1_000)
        assertEquals("secret", SaveHandoff.take(token, now = 2_000)?.password)
        assertEquals(null, SaveHandoff.take(token, now = 2_001))
    }

    @Test
    fun aLoginExpiresAfterTwoMinutes() {
        val token = SaveHandoff.put(save(), now = 0)
        assertEquals(null, SaveHandoff.take(token, now = 120_001))
        val fresh = SaveHandoff.put(save(), now = 500_000)
        assertEquals("anna", SaveHandoff.take(fresh, now = 500_000 + 119_000)?.username)
    }

    @Test
    fun unknownOrMissingTokensGiveNothing() {
        assertEquals(null, SaveHandoff.take(null))
        assertEquals(null, SaveHandoff.take("no-such-token"))
    }
}
