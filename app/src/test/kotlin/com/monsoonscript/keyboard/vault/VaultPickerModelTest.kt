package com.monsoonscript.keyboard.vault

import com.monsoonscript.keyboard.vault.VaultPickerModel.ChipKind
import com.monsoonscript.keyboard.vault.VaultPickerModel.Row
import com.monsoonscript.keyboard.vault.VaultPickerModel.State
import com.monsoonscript.keyboard.vault.VaultPickerModel.Tab
import com.monsoonscript.vault.EntrySummary
import com.monsoonscript.vault.GroupListing
import com.monsoonscript.vault.GroupRef
import com.monsoonscript.vault.OtpCode
import com.monsoonscript.vault.OtpKind
import com.monsoonscript.vault.VaultError
import com.monsoonscript.vault.VaultException
import com.monsoonscript.vault.VaultInfo
import com.monsoonscript.vault.VaultStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

private fun group(id: String, name: String, entries: Int = 0, groups: Int = 0) = GroupRef(id, name, entries, groups)

class VaultPickerModelTest {
    /** A vault answered on the spot: root has the folder "Work" and the bin; entries a, b in root, c in Work. */
    private class Fake : VaultPickerModel.Backend {
        var unlocked = true
        var conflict = false
        var failWith: Throwable? = null
        val a = EntrySummary("a", "root", "Alpha", "alice", "", true, 0L)
        val b = EntrySummary("b", "root", "Beta", "", "", true, 0L)
        val c = EntrySummary("c", "work", "Charlie", "carol", "", false, 0L)
        val d = EntrySummary("d", "root", "Delta", "dave", "", true, 0L, hasOtp = true)
        val matchesFor = HashMap<String, List<EntrySummary>>()
        val passwords = HashMap<String, String>().apply { put("a", "pw-a"); put("b", "pw-b") }
        var searched = ArrayList<String>()
        var passwordCalls = 0

        /** What the vault answers for a one-time code (null = none); tests change it between asks. */
        var code: OtpCode? = OtpCode("123456", 20, 30, OtpKind.TOTP)
        var otpCalls = 0

        private fun <T> answer(done: (Result<T>) -> Unit, value: () -> T) {
            val f = failWith
            done(if (f != null) Result.failure(f) else Result.success(value()))
        }

        override fun status(done: (Result<VaultStatus>) -> Unit) = answer(done) { VaultStatus(unlocked, false, conflict, false) }
        override fun info(done: (Result<VaultInfo>) -> Unit) = answer(done) { VaultInfo("V", 3, "AES", "4", "root", "bin") }
        override fun forApp(packageName: String, done: (Result<List<EntrySummary>>) -> Unit) = answer(done) { matchesFor[packageName] ?: emptyList() }
        override fun summaries(ids: List<String>, done: (Result<List<EntrySummary>>) -> Unit) =
            answer(done) { ids.mapNotNull { id -> listOf(a, b, c).firstOrNull { it.id == id } } }

        override fun list(groupId: String?, done: (Result<GroupListing>) -> Unit) = answer(done) {
            val root = group("root", "Root", 2, 2)
            if (groupId == null || groupId == "root") {
                GroupListing(root, listOf(root), listOf(group("work", "Work", 1), group("bin", "Recycle Bin")), listOf(a, b))
            } else {
                val work = group("work", "Work", 1)
                GroupListing(work, listOf(root, work), emptyList(), listOf(c))
            }
        }

        override fun search(query: String, done: (Result<List<EntrySummary>>) -> Unit) {
            searched.add(query)
            answer(done) { listOf(a, b, c, d).filter { it.title.contains(query, ignoreCase = true) } }
        }

        override fun password(id: String, done: (Result<CharArray>) -> Unit) {
            passwordCalls++
            answer(done) { passwords.getValue(id).toCharArray() }
        }

        override fun otpCode(id: String, done: (Result<OtpCode?>) -> Unit) {
            otpCalls++
            answer(done) { code }
        }
    }

    private class FakeHost : VaultPickerModel.Host {
        override var vaultConfigured = true
        var passwordField = true
        var recent: List<String> = emptyList()
        val typed = ArrayList<String>()
        val typedPasswords = ArrayList<String>()
        var closed = 0
        var changes = 0
        var lastPasswordArray: CharArray? = null

        override fun fieldIsPassword() = passwordField
        override fun typeText(text: String) {
            typed.add(text)
        }

        override fun typePassword(chars: CharArray) {
            lastPasswordArray = chars
            typedPasswords.add(String(chars))
        }

        override fun recentIds() = recent
        override fun noteUsed(id: String) {
            recent = VaultPickerModel.pushRecent(recent, id)
        }

        override fun describe(e: Throwable) = "problem: ${e.message}"
        override fun changed() {
            changes++
        }

        override fun closePicker() {
            closed++
        }

        /** What the model asked to run later, with its delay; a test runs them by hand. */
        val later = ArrayList<Pair<Long, () -> Unit>>()
        override fun after(delayMs: Long, block: () -> Unit) {
            later.add(delayMs to block)
        }
    }

    private val backend = Fake()
    private val host = FakeHost()
    private val model = VaultPickerModel(backend, host)

    private fun titles() = model.rows.map { r ->
        when (r) {
            is Row.Item -> r.entry.title
            is Row.Folder -> "[${r.ref.name}]"
            is Row.Up -> "up:${r.name}"
        }
    }

    @Test
    fun noVaultChosenIsSaidBeforeAnyCall() {
        host.vaultConfigured = false
        model.open("com.app")
        assertEquals(State.NO_VAULT, model.state)
    }

    @Test
    fun lockedVaultAndConflictAreStates() {
        backend.unlocked = false
        model.open("com.app")
        assertEquals(State.LOCKED, model.state)
        backend.unlocked = true
        backend.conflict = true
        model.open("com.app")
        assertEquals(State.CONFLICT, model.state)
    }

    @Test
    fun entriesForThisAppComeFirstThenRecentThenTheFolders() {
        backend.matchesFor["com.bank"] = listOf(backend.a)
        host.recent = listOf("b")
        model.open("com.bank")
        assertEquals(State.READY, model.state)
        assertTrue(model.hasApp)
        assertEquals(Tab.APP, model.tab)
        assertEquals(listOf("Alpha"), titles())

        model.open("com.none")
        assertFalse(model.hasApp)
        assertEquals(Tab.RECENT, model.tab)
        assertEquals(listOf("Beta"), titles())

        host.recent = emptyList()
        model.open("")
        assertEquals(Tab.FOLDERS, model.tab)
        assertEquals(listOf("[Work]", "Alpha", "Beta"), titles()) // the recycle bin is not offered
    }

    @Test
    fun foldersOpenAndGoUp() {
        model.open("")
        model.selectTab(Tab.FOLDERS)
        model.tapRow(0) // Work
        assertEquals("Work", model.folderName)
        assertEquals(listOf("up:null", "Charlie"), titles())
        model.tapRow(0)
        assertNull(model.folderName)
        assertEquals(listOf("[Work]", "Alpha", "Beta"), titles())
    }

    @Test
    fun searchReplacesTheViewAndBlankBringsItBack() {
        host.recent = listOf("a")
        model.open("")
        assertEquals(Tab.RECENT, model.tab)
        model.setQuery("  ch ")
        assertTrue(model.searching)
        assertEquals(listOf("Charlie"), titles())
        assertEquals(listOf("ch"), backend.searched)
        model.setQuery("")
        assertFalse(model.searching)
        assertEquals(listOf("Alpha"), titles())
    }

    @Test
    fun tappingAnEntryOpensItInPlaceAndTappingAgainClosesIt() {
        host.recent = listOf("a", "b")
        model.open("")
        model.tapRow(0)
        assertEquals("a", model.expandedId)
        assertEquals(listOf(ChipKind.USERNAME, ChipKind.PASSWORD), model.chipsFor(model.expandedEntry()!!))
        model.tapRow(1)
        assertEquals("b", model.expandedId)
        assertEquals(listOf(ChipKind.PASSWORD), model.chipsFor(model.expandedEntry()!!)) // blank user name: no chip
        model.tapRow(1)
        assertNull(model.expandedId)
    }

    @Test
    fun anEntryWithoutAPasswordHasNoPasswordChip() {
        assertEquals(listOf(ChipKind.USERNAME), model.chipsFor(backend.c))
    }

    @Test
    fun usernameIsTypedAndThePickerStaysOpen() {
        host.recent = listOf("a")
        model.open("")
        model.tapRow(0)
        model.tapChip(ChipKind.USERNAME)
        assertEquals(listOf("alice"), host.typed)
        assertEquals(0, host.closed)
        assertEquals("a", model.expandedId)
    }

    @Test
    fun passwordInAPasswordFieldIsTypedWipedAndThePickerCloses() {
        host.recent = listOf("a")
        model.open("")
        model.tapRow(0)
        model.tapChip(ChipKind.PASSWORD)
        assertEquals(listOf("pw-a"), host.typedPasswords)
        assertEquals(1, host.closed)
        assertTrue("the array handed over is wiped afterwards", host.lastPasswordArray!!.all { it == '\u0000' })
        assertEquals(listOf("a"), host.recent)
    }

    @Test
    fun passwordInAnotherFieldAsksFirst() {
        host.passwordField = false
        host.recent = listOf("a")
        model.open("")
        model.tapRow(0)
        model.tapChip(ChipKind.PASSWORD)
        assertTrue(model.asking)
        assertEquals(0, backend.passwordCalls)
        model.answerAsk(false)
        assertFalse(model.asking)
        assertTrue(host.typedPasswords.isEmpty())
        model.tapChip(ChipKind.PASSWORD)
        model.answerAsk(true)
        assertEquals(listOf("pw-a"), host.typedPasswords)
        assertEquals(1, host.closed)
    }

    @Test
    fun aVaultThatLocksMeanwhileShowsTheLockedState() {
        host.recent = listOf("a")
        model.open("")
        model.tapRow(0)
        backend.failWith = VaultException(VaultError.LOCKED, "locked")
        model.tapChip(ChipKind.PASSWORD)
        assertEquals(State.LOCKED, model.state)
        assertTrue(host.typedPasswords.isEmpty())
    }

    @Test
    fun otherFailuresShowAMessage() {
        backend.failWith = VaultException(VaultError.IO, "disk")
        model.open("")
        assertEquals(State.ERROR, model.state)
        assertEquals("problem: disk", model.message)
    }

    @Test
    fun closingDropsWhatIsShownAndLateAnswersAreIgnored() {
        host.recent = listOf("a")
        model.open("")
        model.close()
        assertTrue(model.rows.isEmpty())
        assertNull(model.expandedId)
    }

    // ------------------------------------------------------------------------------------------------------------------ the Code chip (E1.18, D98)

    private fun openDelta() {
        model.open("")
        model.setQuery("delta")
        model.tapRow(0)
        assertEquals("d", model.expandedId)
    }

    @Test
    fun onlyAnEntryWithACodeOffersTheCodeChip() {
        assertEquals(listOf(ChipKind.USERNAME, ChipKind.PASSWORD, ChipKind.CODE), model.chipsFor(backend.d))
        assertEquals(listOf(ChipKind.USERNAME, ChipKind.PASSWORD), model.chipsFor(backend.a))
        assertEquals(listOf(ChipKind.PASSWORD), model.chipsFor(backend.b))
    }

    @Test
    fun theCurrentCodeIsTypedAtOnceAndThePickerCloses() {
        openDelta()
        model.tapChip(ChipKind.CODE)
        assertEquals(listOf("123456"), host.typed)
        assertEquals(1, host.closed)
        assertEquals(listOf("d"), host.recent.take(1))
        assertFalse(model.waitingForCode)
        assertTrue(host.later.isEmpty())
    }

    @Test
    fun aCodeAboutToChangeWaitsForTheNextOne() {
        backend.code = OtpCode("111111", 3, 30, OtpKind.TOTP)
        openDelta()
        model.tapChip(ChipKind.CODE)
        assertTrue(model.waitingForCode)
        assertTrue(host.typed.isEmpty())
        assertEquals(0, host.closed)
        assertEquals(3_000L + VaultPickerModel.NEXT_CODE_MARGIN_MS, host.later.single().first)
        // a second tap while waiting changes nothing
        model.tapChip(ChipKind.CODE)
        assertEquals(1, backend.otpCalls)
        // the next step has begun: the new code is asked for and typed whatever it says
        backend.code = OtpCode("222222", 2, 30, OtpKind.TOTP)
        host.later.single().second()
        assertEquals(listOf("222222"), host.typed)
        assertFalse(model.waitingForCode)
        assertEquals(1, host.closed)
    }

    @Test
    fun closingThePanelOrMovingOnDropsTheWait() {
        backend.code = OtpCode("111111", 2, 30, OtpKind.TOTP)
        openDelta()
        model.tapChip(ChipKind.CODE)
        assertTrue(model.waitingForCode)
        model.close()
        assertFalse(model.waitingForCode)
        host.later.single().second()
        assertTrue(host.typed.isEmpty())
        assertEquals(0, host.closed)

        // leaving for another view also drops it
        host.later.clear()
        openDelta()
        model.tapChip(ChipKind.CODE)
        assertTrue(model.waitingForCode)
        model.selectTab(Tab.RECENT)
        assertFalse(model.waitingForCode)
        host.later.single().second()
        assertTrue(host.typed.isEmpty())
    }

    @Test
    fun anEntryThatLostItsCodeTypesNothing() {
        backend.code = null
        openDelta()
        model.tapChip(ChipKind.CODE)
        assertTrue(host.typed.isEmpty())
        assertEquals(0, host.closed)
        assertEquals(State.READY, model.state)
    }

    @Test
    fun aVaultThatLocksWhileAskingForACodeShowsTheLockedState() {
        openDelta()
        backend.failWith = VaultException(VaultError.LOCKED, "locked")
        model.tapChip(ChipKind.CODE)
        assertEquals(State.LOCKED, model.state)
        assertTrue(host.typed.isEmpty())
    }

    @Test
    fun recentListKeepsTheNewestFirstWithoutDuplicates() {
        var l = emptyList<String>()
        for (id in listOf("1", "2", "3", "2")) l = VaultPickerModel.pushRecent(l, id, 3)
        assertEquals(listOf("2", "3", "1"), l)
        l = VaultPickerModel.pushRecent(l, "4", 3)
        assertEquals(listOf("4", "2", "3"), l)
    }
}
