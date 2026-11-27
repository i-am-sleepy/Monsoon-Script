package com.monsoonscript.vault

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** The matching rules of Autofill (E1.19, D100). Pure Kotlin, so these also run on a plain JVM. */
class SiteMatchTest {
    @Test
    fun theHostIsReadFromWhatIsTypedInTheUrlField() {
        assertEquals("example.com", SiteMatch.hostOf("https://www.Example.com:8443/login?x=1#top"))
        assertEquals("example.com", SiteMatch.hostOf("Example.com"))
        assertEquals("example.com", SiteMatch.hostOf("  http://user:pw@example.com./a/b  "))
        assertEquals("accounts.example.com", SiteMatch.hostOf("accounts.example.com/signin"))
        assertEquals("[::1]", SiteMatch.hostOf("http://[::1]:8080/"))
        assertEquals("192.168.0.1", SiteMatch.hostOf("http://192.168.0.1/admin"))
        assertEquals("localhost", SiteMatch.hostOf("localhost:3000"))
    }

    @Test
    fun noHostMeansNoMatch() {
        assertNull(SiteMatch.hostOf(""))
        assertNull(SiteMatch.hostOf("   "))
        assertNull(SiteMatch.hostOf("androidapp://com.example.bank"))
        assertNull(SiteMatch.hostOf("my bank login"))
        assertNull(SiteMatch.hostOf("https://"))
        assertEquals(SiteMatch.NONE, SiteMatch.level("", "example.com"))
        assertEquals(SiteMatch.NONE, SiteMatch.level("example.com", ""))
    }

    @Test
    fun theSameSiteMatchesExactlyWhateverTheWrapping() {
        assertEquals(SiteMatch.EXACT, SiteMatch.level("https://example.com/login", "example.com"))
        assertEquals(SiteMatch.EXACT, SiteMatch.level("https://www.example.com", "example.com"))
        assertEquals(SiteMatch.EXACT, SiteMatch.level("example.com", "www.example.com"))
        assertEquals(SiteMatch.EXACT, SiteMatch.level("HTTP://EXAMPLE.COM:81", "Example.Com"))
        assertEquals(SiteMatch.EXACT, SiteMatch.level("http://192.168.0.1/admin", "192.168.0.1"))
        assertEquals(SiteMatch.EXACT, SiteMatch.level("localhost:3000", "localhost"))
    }

    @Test
    fun aParentDomainEntryMatchesItsSubdomainsButNotTheOtherWayRound() {
        assertEquals(SiteMatch.SUBDOMAIN, SiteMatch.level("https://example.com", "accounts.example.com"))
        assertEquals(SiteMatch.SUBDOMAIN, SiteMatch.level("example.com", "a.b.example.com"))
        assertEquals(SiteMatch.NONE, SiteMatch.level("https://accounts.example.com", "example.com"))
        assertEquals(SiteMatch.NONE, SiteMatch.level("https://mail.example.com", "accounts.example.com"))
    }

    @Test
    fun lookAlikesNeverMatch() {
        assertEquals(SiteMatch.NONE, SiteMatch.level("example.com", "evilexample.com"))
        assertEquals(SiteMatch.NONE, SiteMatch.level("example.com", "example.com.evil.org"))
        assertEquals(SiteMatch.NONE, SiteMatch.level("example.com", "example.org"))
        assertEquals(SiteMatch.NONE, SiteMatch.level("example.com", "notexample.com"))
        assertEquals(SiteMatch.NONE, SiteMatch.level("example.com", "example.com@evil.org"))
    }

    @Test
    fun sharedEndingsAndAddressesMatchOnlyThemselves() {
        assertEquals(SiteMatch.NONE, SiteMatch.level("co.uk", "bank.co.uk"))
        assertEquals(SiteMatch.NONE, SiteMatch.level("https://github.io", "someone.github.io"))
        assertEquals(SiteMatch.EXACT, SiteMatch.level("someone.github.io", "someone.github.io"))
        assertEquals(SiteMatch.NONE, SiteMatch.level("com", "example.com"))
        assertEquals(SiteMatch.NONE, SiteMatch.level("10.0.0.1", "x.10.0.0.1"))
        assertEquals(SiteMatch.NONE, SiteMatch.level("[::1]", "x.[::1]"))
    }
}
