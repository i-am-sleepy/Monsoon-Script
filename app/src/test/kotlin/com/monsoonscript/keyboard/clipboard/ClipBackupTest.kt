package com.monsoonscript.keyboard.clipboard

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ClipBackupTest {
    private var now = 1_000_000L
    private val db = FakeClipDb()
    private fun store(): ClipStore = ClipStore(db) { now }.also { it.load() }

    /** A second phone: its own database, so what [filled] saved is not in it. */
    private fun empty(): ClipStore = ClipStore(FakeClipDb()) { now }.also { it.load() }

    private fun filled(): ClipStore {
        val s = store()
        val f = s.createFolder("Work")!!
        val a = s.addText("first \"quoted\"\nline two বাংলা 😀")!!; now += 10
        s.addText("second")!!; now += 10
        s.addImage("a".repeat(64) + ".png", "image/png"); now += 10
        s.setPinned(listOf(a.id), true)
        s.moveToFolder(listOf(s.query(ClipFilter.TEXT, "second")[0].id), f.id)
        s.addShortcut(";sig", "Best regards,\nMe")
        return s
    }

    @Test
    fun encodeThenDecodeKeepsEverything() {
        val s = filled()
        val d = ClipBackup.decode(ClipBackup.encode(s.snapshot(true)))
        assertEquals(ClipBackup.OK, d.error)
        val b = d.data!!
        assertTrue(b.includesImages)
        assertEquals(listOf("Work"), b.folders.map { it.name })
        assertEquals(3, b.clips.size)
        val first = b.clips.first { it.text.startsWith("first") }
        assertEquals("first \"quoted\"\nline two বাংলা 😀", first.text)
        assertTrue(first.pinned)
        val second = b.clips.first { it.text == "second" }
        assertEquals(b.folders[0].id, second.folder)
        val img = b.clips.first { it.kind == ClipKind.IMAGE }
        assertEquals("a".repeat(64) + ".png", img.image)
        assertEquals("image/png", img.mime)
        assertEquals(listOf(";sig" to "Best regards,\nMe"), b.shortcuts.map { it.trigger to it.text })
    }

    @Test
    fun aBackupWithoutImagesLeavesThemOut() {
        val b = filled().snapshot(false)
        assertFalse(b.includesImages)
        assertEquals(2, b.clips.size)
        assertTrue(b.clips.none { it.kind == ClipKind.IMAGE })
    }

    @Test
    fun theFileSaysWhatItIsAndWhichVersion() {
        val json = ClipBackup.encode(store().snapshot(false))
        assertTrue(json.contains("\"app\":\"monsoon-clipboard\""))
        assertTrue(json.contains("\"format\":1"))
        assertTrue(json.contains("\"encrypted\":false"))
    }

    @Test
    fun otherFilesAndNewerFormatsAreRecognised() {
        assertEquals(ClipBackup.NOT_A_BACKUP, ClipBackup.decode("[1,2]").error)
        assertEquals(ClipBackup.NOT_A_BACKUP, ClipBackup.decode("{\"app\":\"something else\",\"format\":1}").error)
        assertEquals(ClipBackup.NOT_A_BACKUP, ClipBackup.decode("{\"app\":\"monsoon-clipboard\"}").error)
        assertEquals(ClipBackup.TOO_NEW, ClipBackup.decode("{\"app\":\"monsoon-clipboard\",\"format\":2}").error)
        assertEquals(ClipBackup.ENCRYPTED, ClipBackup.decode("{\"app\":\"monsoon-clipboard\",\"format\":1,\"encrypted\":true}").error)
        assertEquals(ClipBackup.DAMAGED, ClipBackup.decode("{\"app\":\"monsoon-clip").error)
        assertNull(ClipBackup.decode("not json").data)
    }

    @Test
    fun entriesThatMakeNoSenseAreLeftOutNotFatal() {
        val d = ClipBackup.decode(
            "{\"app\":\"monsoon-clipboard\",\"format\":1,\"clips\":[{\"kind\":\"text\"},{\"kind\":\"text\",\"text\":\"ok\",\"used\":5},7,{\"kind\":\"image\"}]," +
                "\"folders\":[{\"id\":\"x\"}],\"shortcuts\":[{\"trigger\":\"t\"}]}",
        )
        val b = d.data!!
        assertEquals(listOf("ok"), b.clips.map { it.text })
        assertEquals(5L, b.clips[0].used)
        assertTrue(b.folders.isEmpty() && b.shortcuts.isEmpty())
    }

    // ------------------------------------------------------------------ merging

    @Test
    fun importingIntoAnEmptyStoreRestoresClipsPinsFoldersAndShortcuts() {
        val source = filled()
        val backup = ClipBackup.decode(ClipBackup.encode(source.snapshot(true))).data!!
        val fresh = empty()
        val img = "a".repeat(64) + ".png"
        val r = fresh.importBackup(backup, mapOf(img to img))
        assertEquals(3, r.added)
        assertEquals(0, r.skipped)
        assertEquals(1, r.foldersAdded)
        assertEquals(1, r.shortcutsAdded)
        assertEquals(source.query(ClipFilter.ALL).map { it.text }, fresh.query(ClipFilter.ALL).map { it.text })
        assertTrue(fresh.query(ClipFilter.PINNED)[0].text.startsWith("first"))
        val second = fresh.query(ClipFilter.TEXT, "second")[0]
        assertEquals("Work", fresh.folderOf(second.folder)!!.name)
        assertEquals(";sig", fresh.shortcuts[0].trigger)
        assertEquals(setOf(img), fresh.imageNames())
    }

    @Test
    fun importingTwiceChangesNothingTheSecondTime() {
        val backup = ClipBackup.decode(ClipBackup.encode(filled().snapshot(true))).data!!
        val img = "a".repeat(64) + ".png"
        val s = empty()
        s.importBackup(backup, mapOf(img to img))
        val again = s.importBackup(backup, mapOf(img to img))
        assertEquals(0, again.added)
        assertEquals(3, again.skipped)
        assertEquals(0, again.foldersAdded)
        assertEquals(0, again.shortcutsAdded)
        assertEquals(1, again.shortcutsSkipped)
        assertEquals(3, s.size)
        assertEquals(1, s.folders.size)
        assertEquals(1, s.shortcuts.size)
    }

    @Test
    fun existingThingsWin() {
        val backup = BackupData(
            now, false,
            listOf(BackupFolder(9, "work", 5L)), // same name as ours, other case
            listOf(BackupClip(ClipKind.TEXT, "mine", null, null, true, 9, 5L, 5L)),
            listOf(BackupShortcut(";SIG", "THEIRS")),
        )
        val s = store()
        val ours = s.createFolder("Work")!!
        s.addText("mine")
        s.addShortcut(";sig", "ours")
        val r = s.importBackup(backup, emptyMap())
        assertEquals(0, r.added)
        assertEquals(1, r.skipped)
        assertEquals(0, r.foldersAdded)
        assertEquals(1, r.shortcutsSkipped)
        assertEquals(listOf(ours.id), s.folders.map { it.id })
        assertEquals("ours", s.shortcuts[0].text)
        assertFalse(s.query(ClipFilter.ALL)[0].pinned) // the clip that was here is not changed
    }

    @Test
    fun clipsKeepTheirOwnTimesAndLandInTheRightPlace() {
        val s = store()
        now = 10_000L
        s.addText("recent")
        val backup = BackupData(
            now, false, emptyList(),
            listOf(
                BackupClip(ClipKind.TEXT, "old", null, null, false, 0, 100L, 200L),
                BackupClip(ClipKind.TEXT, "newer", null, null, false, 0, 100L, 9_000L),
            ),
            emptyList(),
        )
        s.importBackup(backup, emptyMap())
        assertEquals(listOf("recent", "newer", "old"), s.query(ClipFilter.ALL).map { it.text })
        assertEquals(200L, s.query(ClipFilter.ALL, "old")[0].used)
    }

    @Test
    fun timesFromTheFutureAreClampedToNow() {
        val s = store()
        val backup = BackupData(now, false, emptyList(), listOf(BackupClip(ClipKind.TEXT, "x", null, null, false, 0, now + 99_999L, now + 99_999L)), emptyList())
        s.importBackup(backup, emptyMap())
        assertEquals(now, s.query(ClipFilter.ALL)[0].used)
    }

    @Test
    fun imagesWithoutTheirFileAreSkipped() {
        val backup = BackupData(
            now, true, emptyList(),
            listOf(BackupClip(ClipKind.IMAGE, "", "x.png", "image/png", false, 0, 1L, 1L)), emptyList(),
        )
        val s = store()
        val r = s.importBackup(backup, emptyMap())
        assertEquals(0, r.added)
        assertEquals(1, r.skipped)
        assertEquals(0, s.imageCount())
    }

    @Test
    fun theImageNameOfTheBackupMapsToTheFileSavedHere() {
        val backup = BackupData(
            now, true, emptyList(),
            listOf(BackupClip(ClipKind.IMAGE, "", "old-name.png", "image/png", false, 0, 1L, 1L)), emptyList(),
        )
        val s = store()
        s.importBackup(backup, mapOf("old-name.png" to "new-name.png"))
        assertEquals(setOf("new-name.png"), s.imageNames())
    }

    @Test
    fun theHistoryLimitStillAppliesAndIsReported() {
        val s = store()
        s.maxItems = 2
        val clips = (1..5).map { BackupClip(ClipKind.TEXT, "c$it", null, null, false, 0, it.toLong(), it.toLong()) }
        val r = s.importBackup(BackupData(now, false, emptyList(), clips, emptyList()), emptyMap())
        assertEquals(2, r.added)
        assertEquals(3, r.trimmed)
        assertEquals(listOf("c5", "c4"), s.query(ClipFilter.ALL).map { it.text })
    }

    @Test
    fun pinnedAndFolderedClipsSurviveTheLimit() {
        val s = store()
        s.maxItems = 1
        val backup = BackupData(
            now, false, listOf(BackupFolder(1, "F", 1L)),
            listOf(
                BackupClip(ClipKind.TEXT, "pin", null, null, true, 0, 1L, 1L),
                BackupClip(ClipKind.TEXT, "in folder", null, null, false, 1, 2L, 2L),
                BackupClip(ClipKind.TEXT, "loose1", null, null, false, 0, 3L, 3L),
                BackupClip(ClipKind.TEXT, "loose2", null, null, false, 0, 4L, 4L),
            ),
            emptyList(),
        )
        val r = s.importBackup(backup, emptyMap())
        assertEquals(1, r.trimmed)
        assertEquals(3, r.added)
        assertNotNull(s.query(ClipFilter.PINNED).firstOrNull())
        assertEquals(listOf("pin", "loose2", "in folder"), s.query(ClipFilter.ALL).map { it.text }) // pinned first, then newest first
    }

    @Test
    fun badEntriesAreSkippedNotFatal() {
        val backup = BackupData(
            now, false, listOf(BackupFolder(1, "  ", 1L), BackupFolder(2, "F".repeat(ClipStore.MAX_FOLDER_NAME + 1), 1L)),
            listOf(
                BackupClip(ClipKind.TEXT, "   ", null, null, false, 0, 1L, 1L),
                BackupClip(ClipKind.TEXT, "x".repeat(ClipStore.MAX_TEXT + 1), null, null, false, 0, 1L, 1L),
                BackupClip(ClipKind.TEXT, "fine", null, null, false, 1, 1L, 1L), // its folder was refused: the clip goes to no folder
            ),
            listOf(BackupShortcut("has space", "t"), BackupShortcut("ok", "")),
        )
        val s = store()
        val r = s.importBackup(backup, emptyMap())
        assertEquals(1, r.added)
        assertEquals(2, r.skipped)
        assertEquals(0, r.foldersAdded)
        assertEquals(2, r.shortcutsSkipped)
        assertEquals(0L, s.query(ClipFilter.ALL)[0].folder)
    }
}
