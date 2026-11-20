package com.monsoonscript.vault

import android.util.Base64
import androidx.test.platform.app.InstrumentationRegistry
import com.kunzisoft.keepass.database.element.Database
import com.kunzisoft.keepass.database.exception.DatabaseException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.io.File

/**
 * E1 Phase 0, emulator only: vaults made by REAL KeePassXC 2.7.6 (command-line tool; fake data). They use the older KDBX 3.1
 * format with AES key derivation (about 33 million rounds, so the engine's native AES path is exercised), a nested folder, an
 * attachment, Unicode text, and a key file in KeePassXC's own XML v2 format.
 *
 * Lesson from the first run: KDBX 3.1 stores attachments GZIP-compressed. The engine's `getInputDataStream` returns the raw
 * (compressed) bytes; `getUnGzipInputDataStream` is the call that works for compressed and uncompressed attachments alike.
 */
class KeePassXcFixturesTest {
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val dir get() = instrumentation.targetContext.cacheDir
    private val pw = "fixture-pw"
    private val banglaTitle = "\u09ac\u09be\u0982\u09b2\u09be \u09ac\u09cd\u09af\u09be\u0982\u0995"
    private val banglaPassword = "pass-\u09ac\u09be\u0982\u09b2\u09be-kx"

    private fun fixture(name: String): ByteArray {
        val text = instrumentation.context.assets.open("fixtures/$name").bufferedReader().use { it.readText() }.trim()
        return Base64.decode(text, Base64.DEFAULT)
    }

    private fun write(name: String, bytes: ByteArray): File = File(dir, name).also { it.writeBytes(bytes) }

    private fun assertKeePassXcContentIntact(db: Database) {
        val root = db.rootGroup!!
        val bank = root.getChildEntries().first { it.title == banglaTitle }
        assertEquals("bank-user", bank.username)
        assertEquals(banglaPassword, String(bank.password))

        val messenger = root.getChildGroups().first { it.title == "Social" }
            .getChildGroups().first { it.title == "Family" }.getChildEntries().first { it.title == "Messenger" }
        assertEquals("kx-user", messenger.username)
        assertEquals(banglaPassword, String(messenger.password))
        assertEquals("https://messenger.com", messenger.url)
        assertEquals("kx note line", messenger.notes)
        val attachments = messenger.getAttachments(db.attachmentPool)
        assertEquals(listOf("note.txt"), attachments.map { it.name })
        val bytes = attachments.first().binaryData.getUnGzipInputDataStream(db.binaryCache).use { it.readBytes() }
        assertEquals("fixture attachment bytes\n", String(bytes))
    }

    @Test
    fun opensAVaultMadeByKeePassXc() {
        val file = write("kx-full.kdbx", fixture("keepassxc-full.kdbx.b64"))
        assertKeePassXcContentIntact(VaultEngine.open(file, pw, dir))
    }

    @Test
    fun savingAKeePassXcVaultKeepsEverything() {
        val file = write("kx-full-copy.kdbx", fixture("keepassxc-full.kdbx.b64"))
        val db = VaultEngine.open(file, pw, dir)
        val added = db.createEntry()!!
        added.title = "Added by our engine"
        db.addEntryTo(added, db.rootGroup!!)
        val resaved = File(dir, "kx-resaved.kdbx")
        VaultEngine.save(db, resaved, pw, dir)

        val back = VaultEngine.open(resaved, pw, dir)
        assertKeePassXcContentIntact(back)
        assertTrue(back.rootGroup!!.getChildEntries().any { it.title == "Added by our engine" })
    }

    @Test
    fun opensAKeePassXcVaultProtectedByPasswordAndXmlKeyFile() {
        val file = write("kx-keyfile.kdbx", fixture("keepassxc-keyfile.kdbx.b64"))
        val db = VaultEngine.open(file, pw, dir, fixture("keepassxc.keyx.b64"))
        val entry = db.rootGroup!!.getChildEntries().first { it.title == "KeyfileEntry" }
        assertEquals("kf-user", entry.username)
        assertEquals("kf-pass", String(entry.password))

        try {
            VaultEngine.open(file, pw, dir)
            fail("opened a key-file vault without its key file")
        } catch (expected: DatabaseException) {
            // good
        }
    }
}
