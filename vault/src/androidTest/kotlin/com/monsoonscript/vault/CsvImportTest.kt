package com.monsoonscript.vault

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Reading the CSV files of other password managers (E1.23, D104). Pure Kotlin, so these also run on a plain JVM. */
class CsvImportTest {
    @Test
    fun googleAndChromeFile() {
        val text = "name,url,username,password,note\r\n" +
            "github.com,https://github.com/login,me@example.com,pw one,\r\n" +
            "\"Shop, Inc\",https://shop.example/,\"jo \"\"the\"\" user\",\"a,b\",\"line 1\r\nline 2\"\r\n"
        val p = CsvImport.parse(text)
        assertNull(p.problem)
        assertEquals("Google / Chrome", p.format)
        assertEquals(2, p.logins.size)
        val a = p.logins[0]
        assertEquals("github.com", a.title)
        assertEquals("https://github.com/login", a.url)
        assertEquals("me@example.com", a.username)
        assertEquals("pw one", a.password)
        assertEquals("", a.notes)
        val b = p.logins[1]
        assertEquals("Shop, Inc", b.title)
        assertEquals("jo \"the\" user", b.username)
        assertEquals("a,b", b.password)
        assertEquals("line 1\nline 2", b.notes)
    }

    @Test
    fun aByteOrderMarkAndBlankLinesAreIgnored() {
        val p = CsvImport.parse("﻿name,url,username,password,note\n\n\nx,https://x.org,u,p,\n\n")
        assertNull(p.problem)
        assertEquals(1, p.logins.size)
        assertEquals("x", p.logins[0].title)
    }

    @Test
    fun aMissingTitleBecomesTheSiteOrTheUserName() {
        val p = CsvImport.parse("url,username,password\nhttps://www.example.com/a,me,pw\n,solo,pw2\n")
        assertNull(p.problem)
        assertEquals("example.com", p.logins[0].title)
        assertEquals("solo", p.logins[1].title)
    }

    @Test
    fun bitwardenLeavesOutNotesAndKeepsTheCodeAndFields() {
        val text = "folder,favorite,type,name,notes,fields,reprompt,login_uri,login_username,login_password,login_totp\n" +
            "Work,1,login,Mail,,\"PIN: 1234\",0,https://mail.example.com,me,secret,otpauth://totp/x?secret=JBSWY3DPEHPK3PXP\n" +
            ",,note,A secure note,words,,0,,,,\n" +
            ",,card,My card,,,0,,,,\n"
        val p = CsvImport.parse(text)
        assertNull(p.problem)
        assertEquals("Bitwarden", p.format)
        assertEquals(1, p.logins.size)
        assertEquals(2, p.notLogins)
        val l = p.logins[0]
        assertEquals("Mail", l.title)
        assertEquals("https://mail.example.com", l.url)
        assertEquals("secret", l.password)
        assertEquals("otpauth://totp/x?secret=JBSWY3DPEHPK3PXP", l.otp)
        assertEquals("PIN: 1234", l.notes)
    }

    @Test
    fun lastPassSecureNotesAreLeftOut() {
        val text = "url,username,password,totp,extra,name,grouping,fav\n" +
            "https://a.example,u,p,,some note,A,Group,0\n" +
            "http://sn,,,,NoteType:Server,Server note,Group,0\n"
        val p = CsvImport.parse(text)
        assertEquals("LastPass", p.format)
        assertEquals(1, p.logins.size)
        assertEquals(1, p.notLogins)
        assertEquals("some note", p.logins[0].notes)
    }

    @Test
    fun keePassXcAnd1PasswordColumns() {
        val kx = CsvImport.parse("\"Group\",\"Title\",\"Username\",\"Password\",\"URL\",\"Notes\",\"TOTP\",\"Icon\"\n\"Root\",\"Bank\",\"me\",\"pw\",\"https://bank.example\",\"n\",\"JBSWY3DP\",\"0\"\n")
        assertEquals("KeePassXC", kx.format)
        assertEquals("JBSWY3DP", kx.logins[0].otp)
        assertEquals("Bank", kx.logins[0].title)
        val op = CsvImport.parse("Title,Url,Username,Password,OTPAuth,Favorite,Archived,Tags,Notes\nShop,https://shop.example,me,pw,,0,0,,hi\n")
        assertEquals("1Password", op.format)
        assertEquals("hi", op.logins[0].notes)
    }

    @Test
    fun aSemicolonOrTabSeparatedFileIsRead() {
        val semi = CsvImport.parse("name;url;username;password\nA;https://a.example;u;p;w\n")
        assertEquals(1, semi.logins.size)
        assertEquals("p", semi.logins[0].password)
        val tab = CsvImport.parse("name\turl\tusername\tpassword\nA\thttps://a.example\tu\tp\n")
        assertEquals("p", tab.logins[0].password)
    }

    @Test
    fun loginsWithoutAnythingToFillAreCounted() {
        val p = CsvImport.parse("name,url,username,password\nA,https://a.example,u,\nB,https://b.example,u,pw\n")
        assertEquals(1, p.logins.size)
        assertEquals(1, p.noPassword)
    }

    @Test
    fun unusableFilesComeBackWithASentence() {
        assertNotNull(CsvImport.parse("").problem)
        assertNotNull(CsvImport.parse("a,b,c\n1,2,3\n").problem)
        assertNotNull(CsvImport.parse("password\nsecret\n").problem)
        assertTrue(CsvImport.parse("name,url,username,password\n").problem!!.isNotEmpty())
    }

    @Test
    fun theSameLoginIsFoundWhateverTheAddressLooksLike() {
        val a = CsvImport.loginKey("x", "https://www.Example.com/login?next=1", "Me@Mail.com ")
        val b = CsvImport.loginKey("y", "example.com", "me@mail.com")
        assertEquals(a, b)
        assertTrue(CsvImport.loginKey("x", "https://example.com", "me") != CsvImport.loginKey("x", "https://example.org", "me"))
        assertTrue(CsvImport.loginKey("x", "", "me") != CsvImport.loginKey("z", "", "me"))
        assertEquals(CsvImport.loginKey("My App", "androidapp://com.a", "me"), CsvImport.loginKey("my app", "", "me"))
    }
}
