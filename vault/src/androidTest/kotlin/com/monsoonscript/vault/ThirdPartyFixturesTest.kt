package com.monsoonscript.vault

import android.util.Base64
import androidx.test.platform.app.InstrumentationRegistry
import com.kunzisoft.keepass.database.element.Database
import com.kunzisoft.keepass.database.exception.DatabaseException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.io.File

/**
 * E1 Phase 0, emulator only: vaults made by ANOTHER implementation (pykeepass 4.2.0, an independent KDBX 4 library; fake data,
 * not KeePassXC) must open, read correctly and survive being saved by our engine. Fixtures are base64 text in the test assets.
 */
class ThirdPartyFixturesTest {
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val dir get() = instrumentation.targetContext.cacheDir
    private val pw = "fixture-pw"
    private val latestPassword = "pass-\u09ac\u09be\u0982\u09b2\u09be-\u09df-v2"

    private fun fixture(name: String): ByteArray {
        val text = instrumentation.context.assets.open("fixtures/$name").bufferedReader().use { it.readText() }.trim()
        return Base64.decode(text, Base64.DEFAULT)
    }

    private fun write(name: String, bytes: ByteArray): File = File(dir, name).also { it.writeBytes(bytes) }

    private fun assertFacebookIntact(db: Database) {
        val root = db.rootGroup!!
        assertEquals(setOf("Social", "Work"), root.getChildGroups().map { it.title }.toSet())

        val fb = root.getChildGroups().first { it.title == "Social" }.getChildEntries().first { it.title == "Facebook" }
        assertEquals("rafi.dev", fb.username)
        assertEquals(latestPassword, String(fb.password))
        assertEquals("https://facebook.com", fb.url)
        assertEquals("first line\nsecond line", fb.notes)

        val extra = fb.getExtraFields().associate { it.name to it.protectedValue }
        assertEquals("com.facebook.katana", extra["AndroidApp"]?.toString())
        assertEquals("1234-5678", extra["Recovery code"]?.toString())
        assertTrue("protected flag lost", extra["Recovery code"]?.isProtected == true)
        assertTrue(fb.getAppOrigin()?.androidOrigins?.any { it.packageName == "com.facebook.katana" } == true)

        val otp = fb.getOtpElement()
        assertNotNull("one-time code field not understood", otp)
        assertEquals(6, otp!!.token.size)

        assertEquals(setOf("social", "fixture"), fb.tags.toStringList().toSet())
        assertEquals("history lost", 1, fb.getHistory().size)

        val attachments = fb.getAttachments(db.attachmentPool)
        assertEquals(listOf("note.txt"), attachments.map { it.name })
        val bytes = attachments.first().binaryData.getInputDataStream(db.binaryCache).use { it.readBytes() }
        assertEquals("fixture attachment bytes\n", String(bytes))

        val gmail = root.getChildGroups().first { it.title == "Work" }
            .getChildGroups().first { it.title == "Email" }.getChildEntries().first()
        assertEquals("Gmail", gmail.title)
        assertEquals("rafi@example.com", gmail.username)
    }

    @Test
    fun opensAFullVaultMadeByAnotherImplementation() {
        val file = write("pyk-full.kdbx", fixture("pykeepass-full.kdbx.b64"))
        assertFacebookIntact(VaultSpike.open(file, pw, dir))
    }

    @Test
    fun savingAThirdPartyVaultKeepsEverything() {
        val file = write("pyk-full-copy.kdbx", fixture("pykeepass-full.kdbx.b64"))
        val db = VaultSpike.open(file, pw, dir)
        val added = db.createEntry()!!
        added.title = "Added by our engine"
        added.username = "u"
        db.addEntryTo(added, db.rootGroup!!)

        val resaved = File(dir, "pyk-resaved.kdbx")
        VaultSpike.save(db, resaved, pw, dir)

        val back = VaultSpike.open(resaved, pw, dir)
        assertFacebookIntact(back)
        assertTrue(back.rootGroup!!.getChildEntries().any { it.title == "Added by our engine" })
    }

    @Test
    fun opensAVaultProtectedByPasswordAndKeyFile() {
        val file = write("pyk-keyfile.kdbx", fixture("pykeepass-keyfile.kdbx.b64"))
        val keyFile = fixture("pykeepass-keyfile.key.b64")
        val db = VaultSpike.open(file, pw, dir, keyFile)
        val bank = db.rootGroup!!.getChildEntries().first { it.title == "Bank" }
        assertEquals("user-c", bank.username)
        assertEquals("pw-c", String(bank.password))

        try {
            VaultSpike.open(file, pw, dir)
            fail("opened a key-file vault without its key file")
        } catch (expected: DatabaseException) {
            // good: the key file is really required
        }
    }

    @Test
    fun aWrongPasswordIsRejected() {
        val file = write("pyk-wrong.kdbx", fixture("pykeepass-full.kdbx.b64"))
        try {
            VaultSpike.open(file, "not-the-password", dir)
            fail("opened with a wrong password")
        } catch (expected: DatabaseException) {
            // good
        }
    }
}
