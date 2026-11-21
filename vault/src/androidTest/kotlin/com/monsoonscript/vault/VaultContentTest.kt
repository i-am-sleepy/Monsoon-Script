package com.monsoonscript.vault

import androidx.test.platform.app.InstrumentationRegistry
import com.kunzisoft.keepass.database.crypto.kdf.KdfFactory
import com.kunzisoft.keepass.database.element.Database
import com.kunzisoft.keepass.database.element.Field
import com.kunzisoft.keepass.database.element.security.ProtectedString
import com.kunzisoft.keepass.otp.TokenCalculator
import com.kunzisoft.keepass.otp.TokenCalculator.HashAlgorithm
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * E1 Phase 0, emulator only. Checks CONTENT, not just that files open: fields, Unicode, protected custom fields, KeePassDX's
 * app-link convention (D32), TOTP against the official RFC 6238 vectors, the KeePassXC-style `otp` field, and merging.
 */
class VaultContentTest {
    private val dir get() = InstrumentationRegistry.getInstrumentation().targetContext.cacheDir
    private val pw = "master-pw"
    private val banglaPassword = "pass-\u09ac\u09be\u0982\u09b2\u09be-\u09df"

    private fun Database.addSimple(title: String) {
        val entry = createEntry()!!
        entry.title = title
        entry.username = "user-$title"
        addEntryTo(entry, rootGroup!!)
    }

    @Test
    fun entryFieldsSurviveSaveAndReloadWithArgon2id() {
        val file = File(dir, "content.kdbx")
        val db = VaultEngine.newDatabase(KdfFactory.argon2idKdf)
        val entry = db.createEntry()!!
        entry.title = "Facebook"
        entry.username = "rafi.dev"
        entry.password = banglaPassword.toCharArray()
        entry.url = "https://facebook.com"
        entry.notes = "first line\nsecond line"
        entry.putExtraField(Field("AndroidApp", ProtectedString(false, "com.facebook.katana")))
        entry.putExtraField(Field("Recovery code", ProtectedString(true, "1234-5678")))
        entry.putExtraField(
            Field("otp", ProtectedString(true, "otpauth://totp/Example:alice@example.com?secret=JBSWY3DPEHPK3PXP&issuer=Example&period=30&digits=6"))
        )
        db.addEntryTo(entry, db.rootGroup!!)
        VaultEngine.save(db, file, pw, dir)

        // The file really is KDBX 4: signature 03 D9 A2 9A 67 FB 4B B5, then minor version (2 bytes), major version (2 bytes).
        val head = file.inputStream().use { it.readNBytes(12) }
        assertEquals(0x03, head[0].toInt() and 0xFF)
        assertEquals(0xD9, head[1].toInt() and 0xFF)
        assertEquals(4, head[10].toInt() and 0xFF)

        val back = VaultEngine.open(file, pw, dir).rootGroup!!.getChildEntries().first { it.title == "Facebook" }
        assertEquals("rafi.dev", back.username)
        assertEquals(banglaPassword, String(back.password))
        assertEquals("https://facebook.com", back.url)
        assertEquals("first line\nsecond line", back.notes)

        val extra = back.getExtraFields().associate { it.name to it.protectedValue }
        assertEquals("com.facebook.katana", extra["AndroidApp"]?.toString())
        assertEquals("1234-5678", extra["Recovery code"]?.toString())
        assertTrue("protected flag lost", extra["Recovery code"]?.isProtected == true)

        // KeePassDX's own convention for linking an entry to an Android app (D32).
        val origin = back.getAppOrigin()
        assertTrue("app link not understood", origin?.androidOrigins?.any { it.packageName == "com.facebook.katana" } == true)

        // One-time code stored the KeePassXC way (an `otp` attribute holding an otpauth:// URI).
        val otp = back.getOtpElement()
        assertNotNull("otp field not understood", otp)
        assertEquals(6, otp!!.token.size)
    }

    @Test
    fun totpMatchesTheOfficialRfc6238Vectors() {
        val sha1 = "12345678901234567890".toByteArray()
        val sha256 = "12345678901234567890123456789012".toByteArray()
        val sha512 = "1234567890123456789012345678901234567890123456789012345678901234".toByteArray()
        fun token(secret: ByteArray, time: Long, algorithm: HashAlgorithm): Int =
            TokenCalculator.getTotpRfc6238Token(secret, 30, time, 8, algorithm)

        assertEquals(94287082, token(sha1, 59, HashAlgorithm.SHA1))
        assertEquals(46119246, token(sha256, 59, HashAlgorithm.SHA256))
        assertEquals(90693936, token(sha512, 59, HashAlgorithm.SHA512))
        assertEquals(7081804, token(sha1, 1111111109, HashAlgorithm.SHA1))
        assertEquals(65353130, token(sha1, 20000000000, HashAlgorithm.SHA1))
        assertEquals(77737706, token(sha256, 20000000000, HashAlgorithm.SHA256))
        assertEquals(47863826, token(sha512, 20000000000, HashAlgorithm.SHA512))
    }

    @Test
    fun mergeCombinesTwoDivergedCopies() {
        val a = File(dir, "merge-a.kdbx")
        val b = File(dir, "merge-b.kdbx")
        val start = VaultEngine.newDatabase(KdfFactory.aesKdf)
        start.addSimple("Gmail")
        VaultEngine.save(start, a, pw, dir)

        // Copy B starts from A and gains "Netflix".
        val copyB = VaultEngine.open(a, pw, dir)
        copyB.addSimple("Netflix")
        VaultEngine.save(copyB, b, pw, dir)

        // Meanwhile A gains "Bank".
        val copyA = VaultEngine.open(a, pw, dir)
        copyA.addSimple("Bank")
        VaultEngine.save(copyA, a, pw, dir)

        // Merge B's changes into A.
        val merged = VaultEngine.open(a, pw, dir)
        VaultEngine.mergeFrom(merged, b, pw)
        val titles = merged.rootGroup!!.getChildEntries().map { it.title }.toSet()
        assertEquals(setOf("Gmail", "Bank", "Netflix"), titles)
    }
}
