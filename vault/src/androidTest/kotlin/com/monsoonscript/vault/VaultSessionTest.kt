package com.monsoonscript.vault

import androidx.test.platform.app.InstrumentationRegistry
import com.kunzisoft.keepass.database.crypto.kdf.Limits
import com.kunzisoft.keepass.otp.TokenCalculator
import com.kunzisoft.keepass.otp.TokenCalculator.HashAlgorithm
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
    /** Picked files are plain files in these tests (the service uses the content resolver). */
    private val fileStreams = object : UriStreams {
        override fun input(uri: android.net.Uri): java.io.InputStream = File(uri.path!!).inputStream()
        override fun output(uri: android.net.Uri): java.io.OutputStream = File(uri.path!!).outputStream()
    }

    private fun session(limits: Limits = VaultSession.PERMISSIVE) = VaultSession(File(dir, "work"), limits, streams = fileStreams)
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

    // ------------------------------------------------------------------ E1.17: attachments

    private fun picked(name: String, content: ByteArray): NewAttachment {
        val f = File(dir, "picked-$name").also { it.writeBytes(content) }
        return NewAttachment(name, android.net.Uri.fromFile(f))
    }

    private fun bytes(n: Int, seed: Int = 1) = ByteArray(n) { ((it * 31 + seed) % 251).toByte() }

    private fun saved(s: VaultSession, id: String, name: String): ByteArray {
        val out = File(dir, "out-${System.nanoTime()}")
        s.saveAttachment(id, name, android.net.Uri.fromFile(out))
        return out.readBytes()
    }

    @Test
    fun anAttachedFileSurvivesSavingAndReopeningAndComesBackByteForByte() {
        val s = newVault()
        val data = bytes(50_000)
        val id = s.saveEntry(draft("Docs", s.info().rootId).copy(newAttachments = listOf(picked("codes.pdf", data), picked("note.txt", "hello".toByteArray())))).id!!
        val listed = s.entry(id).attachments
        assertEquals(listOf("codes.pdf", "note.txt"), listed.map { it.name })
        assertTrue(listed.all { it.size > 0 })
        assertTrue(saved(s, id, "codes.pdf").contentEquals(data))
        s.lock()
        val again = openVault()
        assertEquals(listOf("codes.pdf", "note.txt"), again.entry(id).attachments.map { it.name })
        assertTrue(saved(again, id, "codes.pdf").contentEquals(data))
        assertEquals("hello", String(saved(again, id, "note.txt")))
    }

    @Test
    fun editingAnEntryWithoutTouchingAttachmentsKeepsThem() {
        val s = newVault()
        val id = s.saveEntry(draft("Docs", s.info().rootId).copy(newAttachments = listOf(picked("a.bin", bytes(2_000))))).id!!
        s.saveEntry(draft("Docs renamed", s.info().rootId, id = id))
        assertEquals(listOf("a.bin"), s.entry(id).attachments.map { it.name })
        assertTrue(saved(s, id, "a.bin").contentEquals(bytes(2_000)))
    }

    @Test
    fun removingAnAttachmentTakesItAwayAndLeavesTheOthers() {
        val s = newVault()
        val id = s.saveEntry(draft("Docs", s.info().rootId).copy(newAttachments = listOf(picked("a.bin", bytes(1_000)), picked("b.bin", bytes(1_500, 7))))).id!!
        s.saveEntry(draft("Docs", s.info().rootId, id = id).copy(removeAttachments = listOf("a.bin")))
        assertEquals(listOf("b.bin"), s.entry(id).attachments.map { it.name })
        expectError(VaultError.NOT_FOUND) { saved(s, id, "a.bin") }
        s.lock()
        val again = openVault()
        assertEquals(listOf("b.bin"), again.entry(id).attachments.map { it.name })
        assertTrue(saved(again, id, "b.bin").contentEquals(bytes(1_500, 7)))
    }

    @Test
    fun aNewFileWithTheNameOfAnExistingOneReplacesIt() {
        val s = newVault()
        val id = s.saveEntry(draft("Docs", s.info().rootId).copy(newAttachments = listOf(picked("a.bin", bytes(1_000))))).id!!
        s.saveEntry(draft("Docs", s.info().rootId, id = id).copy(newAttachments = listOf(picked("a.bin", bytes(3_000, 9)))))
        assertEquals(1, s.entry(id).attachments.size)
        assertTrue(saved(s, id, "a.bin").contentEquals(bytes(3_000, 9)))
    }

    @Test
    fun aFileOverTheLimitIsRefusedAndNothingChanges() {
        val s = newVault()
        val id = s.saveEntry(draft("Docs", s.info().rootId)).id!!
        val before = file.readBytes()
        val big = picked("big.bin", ByteArray((VaultSession.MAX_ATTACHMENT_BYTES + 1).toInt()))
        expectError(VaultError.TOO_BIG) { s.saveEntry(draft("Docs changed", s.info().rootId, id = id).copy(newAttachments = listOf(big))) }
        assertEquals(setOf("Docs"), titles(s))
        assertTrue(s.entry(id).attachments.isEmpty())
        assertTrue(file.readBytes().contentEquals(before))
        // exactly the limit is fine
        val exact = picked("exact.bin", ByteArray(VaultSession.MAX_ATTACHMENT_BYTES.toInt()) { 7 })
        s.saveEntry(draft("Docs", s.info().rootId, id = id).copy(newAttachments = listOf(exact)))
        assertEquals(listOf("exact.bin"), s.entry(id).attachments.map { it.name })
    }

    @Test
    fun badAttachmentsAreRefusedBeforeAnythingIsTouched() {
        val s = newVault()
        val root = s.info().rootId
        val before = file.readBytes()
        expectError(VaultError.INVALID) { s.saveEntry(draft("X", root).copy(newAttachments = listOf(picked(" ", bytes(10))))) }
        expectError(VaultError.INVALID) { s.saveEntry(draft("X", root).copy(newAttachments = listOf(picked("e.bin", ByteArray(0))))) }
        expectError(VaultError.INVALID) { s.saveEntry(draft("X", root).copy(newAttachments = listOf(picked("d.bin", bytes(10)), picked("d.bin", bytes(11))))) }
        expectError(VaultError.FILE_NOT_FOUND) { s.saveEntry(draft("X", root).copy(newAttachments = listOf(NewAttachment("gone.bin", android.net.Uri.fromFile(File(dir, "does-not-exist")))))) }
        assertTrue("no entry was created", titles(s).isEmpty())
        assertTrue(file.readBytes().contentEquals(before))
    }

    @Test
    fun savingAnAttachmentNeedsAnExistingOneAndAnOpenVault() {
        val s = newVault()
        val id = s.saveEntry(draft("Docs", s.info().rootId).copy(newAttachments = listOf(picked("a.bin", bytes(100))))).id!!
        expectError(VaultError.NOT_FOUND) { saved(s, id, "nope.bin") }
        s.lock()
        expectError(VaultError.LOCKED) { saved(s, id, "a.bin") }
    }

    // ------------------------------------------------------------------ E1.16: the backups screen's engine side

    /** A vault whose file holds "One" and whose backups hold the version before "Two" was added; returns the session that did the second save. */
    private fun vaultWithABackup(): VaultSession {
        val first = newVault()
        first.saveEntry(draft("One", first.info().rootId))
        first.lock()
        val again = openVault()
        again.saveEntry(draft("Two", again.info().rootId)) // the first save of a session keeps the version just opened
        return again
    }

    @Test
    fun backupsAreListedNewestFirstWithTheirSize() {
        val s = vaultWithABackup()
        val list = s.backups()
        assertTrue(list.isNotEmpty())
        assertEquals(list.sortedByDescending { it.time }, list)
        assertEquals(VaultBackups.list(File(dir, "backups")).last().length(), list.first().size)
    }

    @Test
    fun restoringABackupBringsTheOlderVersionBackLocksAndKeepsTheCurrentOneAsABackup() {
        val s = vaultWithABackup()
        assertEquals(setOf("One", "Two"), titles(s))
        val current = file.readBytes()
        s.restoreBackup(s.backups().first().id)
        assertFalse("the restored file has its own master password: the vault locks", s.status().unlocked)
        assertEquals(setOf("One"), titles(openVault()))
        assertTrue("the version that was replaced must be a backup, so a restore can be undone",
            VaultBackups.list(File(dir, "backups")).any { it.readBytes().contentEquals(current) })
    }

    @Test
    fun aBackupIdThatIsNotABackupOfThisVaultIsRefused() {
        val s = vaultWithABackup()
        val before = file.readBytes()
        expectError(VaultError.NOT_FOUND) { s.restoreBackup("../v.kdbx") }
        expectError(VaultError.NOT_FOUND) { s.restoreBackup("nope.bak") }
        assertTrue(s.status().unlocked)
        assertTrue(file.readBytes().contentEquals(before))
    }

    @Test
    fun restoringAPickedKeepassFileReplacesTheVault() {
        val other = File(dir, "other.kdbx")
        val otherStore = FileVaultStore(other, File(dir, "other-backups"))
        session().also {
            it.create(otherStore, "Other", pw, null, tune = false)
            it.saveEntry(draft("FromTheOtherFile", it.info().rootId))
            it.lock()
        }
        val s = newVault()
        s.saveEntry(draft("Mine", s.info().rootId))
        val mine = file.readBytes()
        s.restoreFromFile(otherStore)
        assertFalse(s.status().unlocked)
        assertEquals(setOf("FromTheOtherFile"), titles(openVault()))
        assertTrue("my version is kept as a backup", VaultBackups.list(File(dir, "backups")).any { it.readBytes().contentEquals(mine) })
    }

    @Test
    fun aFileThatIsNotAVaultIsRefusedBeforeAnythingIsTouched() {
        val s = newVault()
        s.saveEntry(draft("Mine", s.info().rootId))
        val before = file.readBytes()
        val junk = File(dir, "junk.kdbx").also { it.writeText("this is not a vault at all, just some text") }
        expectError(VaultError.NOT_A_VAULT) { s.restoreFromFile(FileVaultStore(junk, File(dir, "junk-backups"))) }
        val empty = File(dir, "empty.kdbx").also { it.writeBytes(ByteArray(0)) }
        expectError(VaultError.NOT_A_VAULT) { s.restoreFromFile(FileVaultStore(empty, File(dir, "empty-backups"))) }
        assertTrue("the vault stays open", s.status().unlocked)
        assertTrue("and the file is untouched", file.readBytes().contentEquals(before))
    }

    @Test
    fun restoringNeedsAnOpenWritableVault() {
        val s = vaultWithABackup()
        val id = s.backups().first().id
        s.lock()
        expectError(VaultError.LOCKED) { s.restoreBackup(id) }
        expectError(VaultError.LOCKED) { s.backups() }
        val ro = session()
        ro.open(store(), pw, null, readOnly = true)
        expectError(VaultError.READ_ONLY) { ro.restoreBackup(id) }
        assertTrue(ro.status().unlocked)
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

    // ---------------------------------------------------------------- Autofill matching (E1.19, D100)

    private fun site(s: VaultSession, title: String, url: String, username: String = "user", fields: List<FieldItem> = emptyList()) =
        s.saveEntry(draft(title, s.info().rootId, fields = fields).copy(url = url, username = username)).id!!

    @Test
    fun matchesFindsEntriesBySiteExactFirstAndSkipsLookAlikesAndTheBin() {
        val s = newVault()
        site(s, "Main", "https://example.com/login")
        site(s, "Www", "https://www.example.com")
        site(s, "Accounts", "https://accounts.example.com")
        site(s, "Other", "https://example.org")
        site(s, "Lookalike", "https://evilexample.com")
        site(s, "No address", "")
        val gone = site(s, "Deleted", "https://example.com")
        s.deleteEntry(gone)
        // on accounts.example.com: the exact entry first, then the parent-domain ones (sorted by title)
        assertEquals(listOf("Accounts", "Main", "Www"), s.matches("accounts.example.com", "com.android.chrome").map { it.title })
        assertEquals(listOf("Main", "Www"), s.matches("www.example.com", "").map { it.title })
        assertTrue(s.matches("nothing.test", "com.android.chrome").isEmpty())
    }

    @Test
    fun matchesUsesTheAppPackageOnlyWhenThereIsNoWebAddress() {
        val s = newVault()
        site(s, "Field", "https://bank.example", fields = listOf(FieldItem("AndroidApp", "com.example.bank", false)))
        site(s, "Link", "androidapp://com.example.bank")
        site(s, "Pro", "androidapp://com.example.bank.pro")
        site(s, "Site", "https://example.com")
        assertEquals(listOf("Field", "Link"), s.matches("", "com.example.bank").map { it.title })
        // a browser names the site: the browser's own package says nothing
        assertEquals(listOf("Site"), s.matches("example.com", "com.example.bank").map { it.title })
        assertTrue(s.matches("", "").isEmpty())
        assertTrue(s.matches("  ", "  ").isEmpty())
    }

    @Test
    fun matchesGivesAtMostTenEntriesAndNeedsAnOpenVault() {
        val s = newVault()
        for (i in 1..12) site(s, "Site %02d".format(i), "https://example.com/$i")
        assertEquals(VaultSession.MAX_MATCHES, s.matches("example.com", "").size)
        s.lock()
        expectError(VaultError.LOCKED) { s.matches("example.com", "") }
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

    // ---------------------------------------------------------------- one-time codes (E1.18)

    /** The secret of the RFC 4226 / 6238 test vectors ("12345678901234567890") and the same in base32. */
    private val rfcSecret = "12345678901234567890".toByteArray()
    private val rfcBase32 = "GEZDGNBVGY3TQOJQGEZDGNBVGY3TQOJQ"

    private fun totpAt(seconds: Long, period: Int = 30, digits: Int = 6): String =
        String(TokenCalculator.formatTokenString(TokenCalculator.getTotpRfc6238Token(rfcSecret, period, seconds, digits, HashAlgorithm.SHA1), digits))

    private fun now() = System.currentTimeMillis() / 1000

    /** The code from [ask] must be the right one for the wall clock (checked before and after the call, so a step change in between is fine). */
    private fun assertCurrent(period: Int, digits: Int, ask: () -> OtpCode): OtpCode {
        val before = now()
        val got = ask()
        val after = now()
        val ok = setOf(totpAt(before, period, digits), totpAt(after, period, digits))
        assertTrue("${got.code} is not one of $ok", got.code in ok)
        assertEquals(period, got.period)
        assertTrue("seconds left ${got.secondsLeft}", got.secondsLeft in 1..period)
        return got
    }

    private fun entryWithCode(s: VaultSession, input: String, title: String = "Bank"): String =
        s.saveEntry(draft(title, s.info().rootId).copy(otp = input)).id!!

    /** An edit that sends the entry as the screens do: everything as stored, [otp] as given (null = keep). */
    private fun edit(s: VaultSession, id: String, otp: String?, title: String? = null): Change {
        val e = s.entry(id)
        return s.saveEntry(EntryDraft(id, e.summary.groupId, title ?: e.summary.title, e.summary.username, null, e.summary.url, e.notes, e.fields, otp = otp))
    }

    @Test
    fun theEngineGivesTheStandardTestCodes() {
        // RFC 6238 appendix B (SHA-1, 8 digits) and RFC 4226 appendix D: the same numbers every authenticator app must give.
        val rfc6238 = mapOf(59L to 94287082, 1111111109L to 7081804, 1111111111L to 14050471, 1234567890L to 89005924, 2000000000L to 69279037, 20000000000L to 65353130)
        for ((time, expected) in rfc6238) {
            assertEquals("time $time", expected, TokenCalculator.getTotpRfc6238Token(rfcSecret, 30, time, 8, HashAlgorithm.SHA1))
        }
        val rfc4226 = listOf("755224", "287082", "359152", "969429", "338314", "254676", "287922", "162583", "399871", "520489")
        rfc4226.forEachIndexed { counter, expected ->
            assertEquals("counter $counter", expected, String(TokenCalculator.getHotpToken(rfcSecret, counter.toLong(), 6, HashAlgorithm.SHA1)))
        }
    }

    @Test
    fun anEntryWithoutACodeHasNone() {
        val s = newVault()
        val id = s.saveEntry(draft("Plain", s.info().rootId)).id!!
        assertNull(s.otpCode(id))
    }

    @Test
    fun aBareSecretKeyGivesTheNormalSixDigitCodeAndSurvivesLockAndReopen() {
        val s = newVault()
        // spaces, dashes and lower case are fine in a typed key
        val id = entryWithCode(s, "gezd gnbv-gy3t qojq gezd gnbv gy3t qojq")
        val code = assertCurrent(30, 6) { s.otpCode(id)!! }
        assertEquals(OtpKind.TOTP, code.kind)
        assertEquals(6, code.code.length)
        assertTrue(code.code.all { it.isDigit() })
        // the secret is not in the field list sent to the screens; it is stored as the usual protected "otp" link and can be revealed on request
        val detail = s.entry(id)
        assertEquals(OtpKind.TOTP, detail.otp)
        assertTrue(detail.fields.none { it.name == "otp" })
        val link = s.fieldValue(id, "otp")!!
        assertTrue(link, link.startsWith("otpauth://totp/") && link.contains("secret=$rfcBase32"))
        s.lock()
        val b = openVault()
        assertCurrent(30, 6) { b.otpCode(id)!! }
    }

    @Test
    fun aLinkWithItsOwnDigitsAndPeriodIsHonoured() {
        val s = newVault()
        val id = entryWithCode(s, "otpauth://totp/Bank:me@example.com?secret=$rfcBase32&issuer=Bank&digits=8&period=60")
        val code = assertCurrent(60, 8) { s.otpCode(id)!! }
        assertEquals(8, code.code.length)
    }

    @Test
    fun aSteamCodeUsesItsOwnLettersAndFiveCharacters() {
        val s = newVault()
        val id = entryWithCode(s, "otpauth://totp/Steam:me?secret=$rfcBase32&encoder=steam")
        val code = s.otpCode(id)!!
        assertEquals(OtpKind.STEAM, code.kind)
        assertEquals(5, code.code.length)
        assertTrue(code.code, code.code.all { it in "23456789BCDFGHJKMNPQRTVWXY" })
        assertEquals(30, code.period)
    }

    @Test
    fun aCounterBasedCodeFromAnotherAppIsKnownButGivesNoCode() {
        val s = newVault()
        // the KeePass 2 "HmacOtp" fields, as another app may have stored them
        val fields = listOf(FieldItem("HmacOtp-Secret-Base32", rfcBase32, true), FieldItem("HmacOtp-Counter", "1", false))
        val id = s.saveEntry(draft("Old", s.info().rootId, fields = fields)).id!!
        assertEquals(OtpKind.HOTP, s.entry(id).otp)
        assertNull(s.otpCode(id))
        expectError(VaultError.INVALID) { s.otpCheck("otpauth://hotp/Site:me?secret=$rfcBase32&counter=1") }
    }

    @Test
    fun theDraftKeepsReplacesAndRemovesTheCode() {
        val s = newVault()
        val id = entryWithCode(s, rfcBase32)
        assertEquals(OtpKind.TOTP, s.entry(id).otp)
        // an ordinary edit (key left alone) keeps the stored key, also in the history copy's successor
        edit(s, id, otp = null, title = "Renamed")
        assertEquals("Renamed", s.entry(id).summary.title)
        assertCurrent(30, 6) { s.otpCode(id)!! }
        // a blank key removes it
        edit(s, id, otp = "  ")
        assertEquals(0, s.entry(id).otp)
        assertNull(s.otpCode(id))
        assertNull(s.fieldValue(id, "otp"))
        // a new key sets it again, here a Steam link
        edit(s, id, otp = "otpauth://totp/Steam:me?secret=$rfcBase32&encoder=steam")
        assertEquals(OtpKind.STEAM, s.entry(id).otp)
        assertEquals(5, s.otpCode(id)!!.code.length)
        // all of it survives a lock and a reopen
        s.lock()
        assertEquals(OtpKind.STEAM, openVault().entry(id).otp)
    }

    @Test
    fun anUnusableKeyInADraftChangesNothing() {
        val s = newVault()
        val id = entryWithCode(s, rfcBase32)
        val before = s.entry(id).historyCount
        expectError(VaultError.INVALID) { edit(s, id, otp = "not a key!", title = "Should not stick") }
        expectError(VaultError.INVALID) { s.saveEntry(draft("New", s.info().rootId).copy(otp = "otpauth://hotp/x?secret=$rfcBase32&counter=1")) }
        assertEquals("Bank", s.entry(id).summary.title)
        assertEquals(before, s.entry(id).historyCount)
        assertEquals(setOf("Bank"), titles(s))
        assertCurrent(30, 6) { s.otpCode(id)!! }
    }

    @Test
    fun theListsSayWhichEntriesHaveACode() {
        val s = newVault()
        val withCode = entryWithCode(s, rfcBase32, "With code")
        val without = s.saveEntry(draft("Without", s.info().rootId)).id!!
        // a counter-based code stored the old way is not offered (no code can be given for it)
        val hotp = s.saveEntry(draft("Hotp", s.info().rootId, fields = listOf(FieldItem("HmacOtp-Secret-Base32", rfcBase32, true), FieldItem("HmacOtp-Counter", "1", false)))).id!!
        val byId = s.list(null).entries.associateBy { it.id }
        assertTrue(byId.getValue(withCode).hasOtp)
        assertFalse(byId.getValue(without).hasOtp)
        assertFalse(byId.getValue(hotp).hasOtp)
        assertTrue(s.search("With code").single().hasOtp)
        assertFalse(s.search("Without").single().hasOtp)
    }

    @Test
    fun aCustomFieldCannotBeCalledOtp() {
        val s = newVault()
        for (name in listOf("otp", "OTP", " Otp ")) {
            expectError(VaultError.INVALID) { s.saveEntry(draft("X", s.info().rootId, fields = listOf(FieldItem(name, "whatever", false)))) }
        }
    }

    @Test
    fun otpCheckReturnsTheCodeWithoutStoringAnything() {
        val s = newVault()
        val code = assertCurrent(30, 6) { s.otpCheck(rfcBase32) }
        assertEquals(OtpKind.TOTP, code.kind)
        assertTrue(s.list(null).entries.isEmpty())
    }

    @Test
    fun otpCheckRefusesWhatIsNotAKey() {
        val s = newVault()
        for (bad in listOf("", "   ", "hello world!", "12", "ABCDEFG1", "otpauth://totp/x", "otpauth://totp/x?secret=", "otpauth://totp/x?secret=!!!!", "otpauth://nothing/x?secret=$rfcBase32", "https://example.com")) {
            expectError(VaultError.INVALID) { s.otpCheck(bad) }
        }
    }

    @Test
    fun otpCallsNeedAnOpenVaultAndAKnownEntry() {
        val s = newVault()
        val id = entryWithCode(s, rfcBase32)
        expectError(VaultError.NOT_FOUND) { s.otpCode("00000000-0000-0000-0000-000000000000") }
        s.lock()
        expectError(VaultError.LOCKED) { s.otpCode(id) }
        expectError(VaultError.LOCKED) { s.otpCheck(rfcBase32) }
    }

    // ---------------------------------------------------------------- importing logins from a CSV file (E1.23, D104)

    private fun csv(text: String): android.net.Uri = android.net.Uri.fromFile(File(dir, "import-${System.nanoTime()}.csv").also { it.writeText(text) })

    private val googleCsv = "name,url,username,password,note\n" +
        "GitHub,https://github.com/login,me@example.com,pw-1,\n" +
        "Shop,https://shop.example/,me,pw-2,my note\n"

    private fun folderId(s: VaultSession, name: String) = s.list(null).groups.single { it.name == name }.id

    @Test
    fun anImportAddsTheLoginsToANewFolderAndSurvivesReopening() {
        val s = newVault()
        val r = s.importLogins(csv(googleCsv), "Imported 1")
        assertEquals(2, r.added)
        assertEquals(0, r.skipped)
        assertTrue(r.saved)
        assertEquals("Google / Chrome", r.format)
        assertEquals(r.groupId, folderId(s, "Imported 1"))
        s.lock()
        val again = openVault()
        val entries = again.list(folderId(again, "Imported 1")).entries
        assertEquals(setOf("GitHub", "Shop"), entries.map { it.title }.toSet())
        val shop = entries.single { it.title == "Shop" }
        assertEquals("pw-2", String(again.password(shop.id)))
        assertEquals("my note", again.entry(shop.id).notes)
        assertEquals("https://shop.example/", shop.url)
    }

    @Test
    fun theSameFileImportedAgainOnlySkipsAndMakesNoFolder() {
        val s = newVault()
        s.importLogins(csv(googleCsv), "A")
        val r = s.importLogins(csv(googleCsv), "B")
        assertEquals(0, r.added + r.keptBoth)
        assertEquals(2, r.skipped)
        assertNull(r.groupId)
        assertTrue(s.list(null).groups.none { it.name == "B" })
    }

    @Test
    fun aDifferentPasswordForTheSameLoginIsKeptAsASecondEntryWithANote() {
        val s = newVault()
        s.importLogins(csv(googleCsv), "Account 1")
        val r = s.importLogins(csv("name,url,username,password\nGitHub,https://www.github.com/,ME@example.com,older-pw\nNew,https://new.example,u,p\n"), "Account 2")
        assertEquals(1, r.added)
        assertEquals(1, r.keptBoth)
        val second = s.list(folderId(s, "Account 2")).entries.single { it.title == "GitHub" }
        assertEquals("older-pw", String(s.password(second.id)))
        assertTrue(s.entry(second.id).notes.startsWith(VaultSession.IMPORT_NOTE))
        val first = s.list(folderId(s, "Account 1")).entries.single { it.title == "GitHub" }
        assertEquals("pw-1", String(s.password(first.id)))
        assertEquals("", s.entry(first.id).notes)
    }

    @Test
    fun importingIntoAnExistingFolderAddsToItAndTheRecycleBinDoesNotCount() {
        val s = newVault()
        s.importLogins(csv(googleCsv), "Mixed")
        val gone = s.list(folderId(s, "Mixed")).entries.single { it.title == "GitHub" }
        s.deleteEntry(gone.id)
        val r = s.importLogins(csv(googleCsv + "Extra,https://extra.example,u,p,\n"), "mixed")
        assertEquals(2, r.added) // GitHub again (the old one is in the bin) and Extra
        assertEquals(1, r.skipped) // Shop
        assertEquals(1, s.list(null).groups.count { it.name.equals("Mixed", ignoreCase = true) })
        assertEquals(setOf("Shop", "GitHub", "Extra"), titles(s, folderId(s, "Mixed")))
    }

    @Test
    fun aCodeInTheFileIsKeptAndAnUnusableOneIsDroppedNotTheLogin() {
        val s = newVault()
        val r = s.importLogins(csv("name,url,username,password,totp\nGood,https://g.example,u,p,$rfcBase32\nBad,https://b.example,u,p,not a key!\n"), "Codes")
        assertEquals(2, r.added)
        assertEquals(1, r.codesDropped)
        val byTitle = s.list(folderId(s, "Codes")).entries.associateBy { it.title }
        assertTrue(byTitle.getValue("Good").hasOtp)
        assertFalse(byTitle.getValue("Bad").hasOtp)
    }

    @Test
    fun anUnusableFileOrFolderNameChangesNothingAndNeedsAnOpenWritableVault() {
        val s = newVault()
        expectError(VaultError.INVALID) { s.importLogins(csv("a,b\n1,2\n"), "X") }
        expectError(VaultError.INVALID) { s.importLogins(csv(googleCsv), "  ") }
        expectError(VaultError.FILE_NOT_FOUND) { s.importLogins(android.net.Uri.fromFile(File(dir, "missing.csv")), "X") }
        assertTrue(s.list(null).groups.none { it.name == "X" })
        assertEquals(0, s.info().entryCount)
        s.lock()
        expectError(VaultError.LOCKED) { s.importLogins(csv(googleCsv), "X") }
        val ro = session()
        ro.open(store(), pw, null, readOnly = true)
        expectError(VaultError.READ_ONLY) { ro.importLogins(csv(googleCsv), "X") }
    }
}
