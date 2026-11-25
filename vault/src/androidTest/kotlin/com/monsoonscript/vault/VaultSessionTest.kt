package com.monsoonscript.vault

import androidx.test.platform.app.InstrumentationRegistry
import com.kunzisoft.keepass.database.crypto.kdf.Limits
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import java.io.File

/**
 * E1.13.1, emulator only: the vault session the app will use (VaultSession), tested on real files with the real engine.
 * Covers: create/open/lock, wrong password, entries with protected fields and history, search, recycle bin, folders, read-only,
 * the memory limit, damaged files, backups, and the "file changed elsewhere" question (D50) with all three answers.
 */
class VaultSessionTest {
    private lateinit var dir: File
    private lateinit var file: File
    private val pw get() = "master-pw".toCharArray()
    private val banglaPassword = "pass-বাংলা-য়"

    @Before
    fun setUp() {
        val cache = InstrumentationRegistry.getInstrumentation().targetContext.cacheDir
        dir = File(cache, "vsess-${System.nanoTime()}").also { it.mkdirs() }
        file = File(dir, "v.kdbx")
    }

    @After
    fun tearDown() {
        dir.deleteRecursively()
    }

    private fun store() = FileVaultStore(file, File(dir, "backups"))
    private fun session(limits: Limits = VaultSession.PERMISSIVE) = VaultSession(File(dir, "work"), limits)
    private fun newVault(): VaultSession = session().also { it.create(store(), "Test", pw, null, tune = false) }
    private fun openVault(): VaultSession = session().also { it.open(store(), pw, null) }

    private fun draft(
        title: String,
        group: String,
        id: String? = null,
        password: String? = "secret-$title",
        fields: List<FieldItem> = emptyList(),
        notes: String = "",
    ) = EntryDraft(id, group, title, "user-$title", password, "https://$title.example", notes, fields)

    private fun expectError(code: Int, block: () -> Unit) {
        try {
            block()
            fail("expected VaultException with code $code")
        } catch (e: VaultException) {
            assertEquals("${e.message}", code, e.code)
        }
    }

    private fun titles(s: VaultSession, group: String? = null) = s.list(group).entries.map { it.title }.toSet()

    @Test
    fun createLockAndReopenKeepsEverything() {
        val s = newVault()
        val root = s.info().rootId
        val work = s.createGroup(root, "Work").id!!
        val saved = s.saveEntry(
            draft(
                "Gmail", work, password = banglaPassword, notes = "line1\nline2",
                fields = listOf(FieldItem("Recovery", "1234-5678", true), FieldItem("Pin", "9", false)),
            )
        )
        assertTrue(saved.saved)
        s.lock()
        assertFalse(s.status().unlocked)
        expectError(VaultError.LOCKED) { s.list(null) }

        expectError(VaultError.WRONG_CREDENTIALS) { session().open(store(), "nope".toCharArray(), null) }

        val back = openVault()
        assertTrue(back.status().unlocked)
        assertEquals(setOf("Work", VaultSession.RECYCLE_BIN_NAME), back.list(null).groups.map { it.name }.toSet())
        val summary = back.list(work).entries.single()
        assertEquals("Gmail", summary.title)
        assertEquals("user-Gmail", summary.username)
        assertTrue(summary.hasPassword)
        assertEquals(banglaPassword, String(back.password(summary.id)))
        val detail = back.entry(summary.id)
        assertEquals("line1\nline2", detail.notes)
        val byName = detail.fields.associateBy { it.name }
        assertEquals("9", byName["Pin"]?.value)
        assertNull("a protected value must not be in the detail", byName["Recovery"]?.value)
        assertTrue(byName["Recovery"]?.protect == true)
        assertEquals("1234-5678", back.fieldValue(summary.id, "Recovery"))
        assertEquals(1, back.info().entryCount)
    }

    @Test
    fun editKeepsOldValuesInHistoryAndUnsentSecrets() {
        val s = newVault()
        val root = s.info().rootId
        val id = s.saveEntry(
            draft("Bank", root, fields = listOf(FieldItem("Recovery", "1234", true), FieldItem("Old", "x", false)))
        ).id!!
        assertEquals(0, s.entry(id).historyCount)

        // password null and the protected field's value null = "keep what is stored"
        s.saveEntry(
            draft("Bank 2", root, id = id, password = null, fields = listOf(FieldItem("Recovery", null, true)))
        )
        assertEquals("secret-Bank", String(s.password(id)))
        assertEquals("1234", s.fieldValue(id, "Recovery"))
        assertEquals(listOf("Recovery"), s.entry(id).fields.map { it.name }) // "Old" was removed by the edit
        assertEquals("Bank 2", s.entry(id).summary.title)
        assertEquals(1, s.entry(id).historyCount)

        s.saveEntry(draft("Bank 2", root, id = id, password = "new-secret"))
        assertEquals(2, s.entry(id).historyCount)

        s.lock()
        val back = openVault()
        assertEquals("new-secret", String(back.password(id)))
        assertEquals(2, back.entry(id).historyCount)
        assertTrue(back.entry(id).fields.isEmpty())
    }

    @Test
    fun searchFindsEveryWordCaseInsensitivelyAndSkipsTheRecycleBin() {
        val s = newVault()
        val root = s.info().rootId
        val a = s.saveEntry(draft("Facebook", root, notes = "personal")).id!!
        s.saveEntry(draft("GitHub", root))
        assertEquals(listOf("Facebook"), s.search("FACE").map { it.title })
        assertEquals(listOf("GitHub"), s.search("github user-gith").map { it.title })
        assertEquals(listOf("Facebook"), s.search("personal").map { it.title })
        assertTrue(s.search("zzz").isEmpty())
        assertTrue(s.search("   ").isEmpty())
        s.deleteEntry(a)
        assertTrue(s.search("facebook").isEmpty())
    }

    @Test
    fun deleteMovesToRecycleBinThenRemovesForGood() {
        val s = newVault()
        val info = s.info()
        val bin = info.recycleBinId!!
        val id = s.saveEntry(draft("Temp", info.rootId)).id!!
        s.deleteEntry(id)
        assertEquals(setOf("Temp"), titles(s, bin))
        assertTrue(titles(s, info.rootId).isEmpty())
        s.deleteEntry(id)
        assertTrue(titles(s, bin).isEmpty())
        expectError(VaultError.NOT_FOUND) { s.entry(id) }

        s.lock()
        val back = openVault()
        assertTrue(titles(back, bin).isEmpty())
    }

    @Test
    fun emptyingTheRecycleBinRemovesEverythingInItWithOneSave() {
        val s = newVault()
        val info = s.info()
        val bin = info.recycleBinId!!
        val folder = s.createGroup(info.rootId, "Old").id!!
        val inFolder = s.saveEntry(draft("In folder", folder)).id!!
        val loose = s.saveEntry(draft("Loose", info.rootId)).id!!
        val keep = s.saveEntry(draft("Keep", info.rootId)).id!!
        s.deleteEntry(loose)
        s.deleteGroup(folder)
        assertEquals(setOf("Loose"), titles(s, bin))
        assertEquals(1, s.list(bin).groups.size)

        val change = s.emptyRecycleBin()
        assertTrue(change.saved)
        assertTrue(titles(s, bin).isEmpty())
        assertTrue(s.list(bin).groups.isEmpty())
        expectError(VaultError.NOT_FOUND) { s.entry(loose) }
        expectError(VaultError.NOT_FOUND) { s.entry(inFolder) }
        assertEquals("Keep", s.entry(keep).summary.title)
        assertTrue(s.emptyRecycleBin().saved) // an empty bin is not an error

        s.lock()
        val back = openVault()
        assertTrue(titles(back, bin).isEmpty())
        assertEquals(setOf("Keep"), titles(back, info.rootId))
    }

    @Test
    fun foldersCanBeCreatedRenamedMovedIntoAndDeleted() {
        val s = newVault()
        val root = s.info().rootId
        val a = s.createGroup(root, "A").id!!
        val b = s.createGroup(a, "B").id!!
        s.renameGroup(b, "Banks")
        val e = s.saveEntry(draft("Visa", a)).id!!
        s.moveEntry(e, b)
        assertEquals(setOf("Visa"), titles(s, b))
        assertEquals(3, s.list(b).path.size) // root > A > Banks
        assertEquals("Banks", s.list(b).path.last().name)

        // an edit that names another folder moves the entry too
        s.saveEntry(draft("Visa", a, id = e, password = null))
        assertEquals(setOf("Visa"), titles(s, a))
        assertTrue(titles(s, b).isEmpty())

        expectError(VaultError.INVALID) { s.deleteGroup(root) }
        expectError(VaultError.INVALID) { s.createGroup(root, "   ") }
        expectError(VaultError.INVALID) { s.renameGroup(root, "x") }
        s.deleteGroup(a)
        assertEquals(setOf(VaultSession.RECYCLE_BIN_NAME), s.list(null).groups.map { it.name }.toSet())
        s.lock()
        assertEquals(setOf(VaultSession.RECYCLE_BIN_NAME), openVault().list(null).groups.map { it.name }.toSet())
    }

    @Test
    fun badInputIsRefusedBeforeAnythingIsWritten() {
        val s = newVault()
        val root = s.info().rootId
        expectError(VaultError.INVALID) { s.saveEntry(draft("x", root, fields = listOf(FieldItem("Password", "p", false)))) }
        expectError(VaultError.INVALID) { s.saveEntry(draft("x", root, fields = listOf(FieldItem("  ", "p", false)))) }
        expectError(VaultError.INVALID) {
            s.saveEntry(draft("x", root, fields = listOf(FieldItem("A", "1", false), FieldItem("a", "2", false))))
        }
        expectError(VaultError.NOT_FOUND) { s.list("00000000-0000-0000-0000-000000000000") }
        expectError(VaultError.NOT_FOUND) { s.entry("not-a-uuid") }
        expectError(VaultError.INVALID) { session().create(store(), "x", CharArray(0), null, tune = false) }
        assertTrue(s.list(null).entries.isEmpty())
    }

    @Test
    fun readOnlyRefusesChanges() {
        val writer = newVault()
        writer.saveEntry(draft("One", writer.info().rootId))
        val ro = session()
        ro.open(store(), pw, null, readOnly = true)
        assertTrue(ro.status().readOnly)
        expectError(VaultError.READ_ONLY) { ro.saveEntry(draft("Two", ro.info().rootId)) }
        assertEquals(setOf("One"), titles(ro))
    }

    @Test
    fun aVaultNeedingMoreMemoryThanAllowedIsRefusedNotOpened() {
        newVault()
        val strict = session(Limits(isMemorySufficient = { _, _ -> false }, parallelism = 1L))
        expectError(VaultError.TOO_HEAVY) { strict.open(store(), pw, null) }
        assertFalse(strict.status().unlocked)
    }

    @Test
    fun missingAndDamagedFilesGiveClearErrors() {
        expectError(VaultError.FILE_NOT_FOUND) { session().open(store(), pw, null) }
        file.writeBytes(ByteArray(300) { (it * 7).toByte() })
        expectError(VaultError.NOT_A_VAULT) { session().open(store(), pw, null) }
    }

    @Test
    fun backupsKeepTheLastFiveVersionsAndSkipQuickRepeats() {
        val f = File(dir, "plain.bin")
        val st = FileVaultStore(f, File(dir, "bk"))
        for (i in 1..8) {
            f.writeText("version $i")
            VaultBackups.backup(st, force = true, now = 1_000_000L * i)
        }
        val kept = VaultBackups.list(st.backupDir)
        assertEquals(VaultBackups.KEEP, kept.size)
        assertEquals("version 8", kept.last().readText())
        assertEquals("version 4", kept.first().readText())

        f.writeText("version 9")
        VaultBackups.backup(st, force = false, now = 8_000_000L + 1_000L) // 1 s after the newest: not worth a slot
        assertEquals("version 8", VaultBackups.list(st.backupDir).last().readText())
        VaultBackups.backup(st, force = false, now = 8_000_000L + VaultBackups.MIN_GAP_MS + 1)
        assertEquals("version 9", VaultBackups.list(st.backupDir).last().readText())
    }

    @Test
    fun theFirstSaveOfASessionKeepsTheVersionThatWasJustOpened() {
        val s = newVault()
        s.saveEntry(draft("One", s.info().rootId))
        s.lock()
        val before = file.readBytes()
        val again = openVault()
        again.saveEntry(draft("Two", again.info().rootId))
        val backups = VaultBackups.list(File(dir, "backups"))
        assertTrue(backups.isNotEmpty())
        assertTrue("the newest backup is the file as it was when opened", backups.last().readBytes().contentEquals(before))
    }

    // ------------------------------------------------------------------ D50: the file changed elsewhere

    /** Vault with entry "One", opened twice: [a] saves "Two" (the file changes), then [b]'s change "Three" finds a conflict. */
    private fun conflictScenario(): Pair<VaultSession, VaultSession> {
        val a = newVault()
        a.saveEntry(draft("One", a.info().rootId))
        val b = openVault()
        a.saveEntry(draft("Two", a.info().rootId))
        val change = b.saveEntry(draft("Three", b.info().rootId))
        assertFalse("nothing may be written over a file that changed", change.saved)
        assertTrue(b.status().conflict)
        assertTrue(b.status().dirty)
        assertEquals("the pending change stays in memory (D50)", setOf("One", "Three"), titles(b))
        assertEquals("the file still holds the other version", setOf("One", "Two"), titles(openVault()))
        return a to b
    }

    @Test
    fun conflictMergeKeepsBothSides() {
        val (_, b) = conflictScenario()
        assertTrue(b.resolve(Resolve.MERGE, pw, null).saved)
        assertFalse(b.status().conflict)
        assertEquals(setOf("One", "Two", "Three"), titles(b))
        assertEquals(setOf("One", "Two", "Three"), titles(openVault()))
    }

    @Test
    fun conflictOverwriteKeepsMineAndBacksUpTheOtherVersion() {
        val (_, b) = conflictScenario()
        val otherVersion = file.readBytes()
        assertTrue(b.resolve(Resolve.OVERWRITE, null, null).saved)
        assertEquals(setOf("One", "Three"), titles(openVault()))
        val backups = VaultBackups.list(File(dir, "backups"))
        assertTrue("the overwritten version must be a backup", backups.any { it.readBytes().contentEquals(otherVersion) })
    }

    @Test
    fun conflictKeepFileDropsMyChange() {
        val (_, b) = conflictScenario()
        b.resolve(Resolve.KEEP_FILE, pw, null)
        assertFalse(b.status().conflict)
        assertFalse(b.status().dirty)
        assertEquals(setOf("One", "Two"), titles(b))
    }

    @Test
    fun forAppFindsEntriesByTheirAppLinkAndSkipsTheRecycleBin() {
        val s = newVault()
        val root = s.info().rootId
        val bank = s.saveEntry(draft("Bank", root, fields = listOf(FieldItem("AndroidApp", "com.example.bank", false)))).id!!
        s.saveEntry(draft("Two apps", root, fields = listOf(FieldItem("AndroidApp", "com.other", false), FieldItem("AndroidApp1", "com.example.bank", false))))
        s.saveEntry(draft("Signature only", root, fields = listOf(FieldItem("AndroidApp Signature", "com.example.bank", false))))
        s.saveEntry(draft("Other", root, fields = listOf(FieldItem("AndroidApp", "com.example.bank.pro", false))))
        s.saveEntry(draft("No link", root))
        assertEquals(listOf("Bank", "Two apps"), s.forApp("com.example.bank").map { it.title })
        assertTrue(s.forApp("").isEmpty())
        assertTrue(s.forApp("com.nothing").isEmpty())
        s.deleteEntry(bank)
        assertEquals(listOf("Two apps"), s.forApp("com.example.bank").map { it.title })
    }

    @Test
    fun summariesKeepTheOrderAndDropMissingOrDeletedEntries() {
        val s = newVault()
        val root = s.info().rootId
        val a = s.saveEntry(draft("A", root)).id!!
        val b = s.saveEntry(draft("B", root)).id!!
        val c = s.saveEntry(draft("C", root)).id!!
        assertEquals(listOf("C", "A", "B"), s.summaries(listOf(c, a, b)).map { it.title })
        s.deleteEntry(a)
        assertEquals(listOf("C", "B"), s.summaries(listOf(c, a, "not-an-id", b, "00000000-0000-0000-0000-000000000000")).map { it.title })
        assertTrue(s.summaries(emptyList()).isEmpty())
    }

    @Test
    fun conflictMergeNeedsTheRightPassword() {
        val (_, b) = conflictScenario()
        expectError(VaultError.WRONG_CREDENTIALS) { b.resolve(Resolve.MERGE, "wrong".toCharArray(), null) }
        assertTrue("still waiting for an answer, nothing lost", b.status().conflict)
        assertEquals(setOf("One", "Three"), titles(b))
    }
}
