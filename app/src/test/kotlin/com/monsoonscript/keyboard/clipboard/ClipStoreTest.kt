package com.monsoonscript.keyboard.clipboard

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class ClipStoreTest {

    private var now = 1_000L
    private val db = FakeClipDb()
    private fun store(): ClipStore = ClipStore(db) { now }.also { it.load() }

    private fun ClipStore.texts(filter: ClipFilter = ClipFilter.ALL, search: String = ""): List<String> = query(filter, search).map { it.text }

    @Test
    fun newestClipIsFirst() {
        val s = store()
        s.addText("one"); now++
        s.addText("two"); now++
        s.addText("three")
        assertEquals(listOf("three", "two", "one"), s.texts())
    }

    @Test
    fun blankAndHugeTextIsNotRecorded() {
        val s = store()
        assertNull(s.addText(""))
        assertNull(s.addText("   \n\t"))
        assertNull(s.addText("x".repeat(ClipStore.MAX_TEXT + 1)))
        assertNotNull(s.addText("x".repeat(ClipStore.MAX_TEXT)))
        assertEquals(1, s.size)
    }

    @Test
    fun copyingTheSameTextMovesItToTheTopAndKeepsPinAndFolder() {
        val s = store()
        val a = s.addText("a")!!; now++
        s.addText("b"); now++
        val folder = s.createFolder("Work")!!
        s.setPinned(listOf(a.id), true)
        s.moveToFolder(listOf(a.id), folder.id)
        now++
        val again = s.addText("a")!!
        assertSame(a, again)
        assertEquals(2, s.size)
        assertEquals("a", s.query(ClipFilter.ALL, "", ClipStore.ANY_FOLDER)[0].text)
        assertTrue(again.pinned)
        assertEquals(folder.id, again.folder)
        assertEquals(now, db.clips[a.id]!!.used)
    }

    @Test
    fun duplicatesAreKeptWhenDedupeIsOff() {
        val s = store()
        s.dedupe = false
        s.addText("a"); now++
        s.addText("a")
        assertEquals(2, s.size)
    }

    @Test
    fun limitDropsTheOldestLooseClipButNeverPinnedOrFoldered() {
        val s = store()
        s.maxItems = 3
        val a = s.addText("a")!!; now++
        val b = s.addText("b")!!; now++
        s.setPinned(listOf(a.id), true)
        val f = s.createFolder("F")!!
        s.moveToFolder(listOf(b.id), f.id)
        for (t in listOf("c", "d", "e", "f", "g")) {
            s.addText(t); now++
        }
        // loose clips: only the newest 3 remain; pinned a and foldered b stay
        assertEquals(setOf("a", "b", "e", "f", "g"), s.texts().toSet())
        assertEquals(5, db.clips.size)
    }

    @Test
    fun unpinningAppliesTheLimit() {
        val s = store()
        s.maxItems = 1
        val a = s.addText("a")!!; now++
        s.setPinned(listOf(a.id), true)
        s.addText("b"); now++
        s.addText("c"); now++
        assertEquals(setOf("a", "c"), s.texts().toSet())
        s.setPinned(listOf(a.id), false)
        assertEquals(listOf("c"), s.texts())
    }

    @Test
    fun autoDeleteRemovesOldLooseClipsButNotPinnedWhileKeepPinnedIsOn() {
        val s = store()
        val old = s.addText("old")!!
        val pinned = s.addText("pinned")!!
        s.setPinned(listOf(pinned.id), true)
        val saved = s.addText("saved")!!
        val f = s.createFolder("F")!!
        s.moveToFolder(listOf(saved.id), f.id)
        now += 2 * HOUR
        s.addText("fresh")
        s.autoDeleteMs = HOUR
        assertTrue(s.purge())
        assertEquals(setOf("pinned", "saved", "fresh"), s.texts().toSet())
        assertNull(s.byId(old.id))
        s.keepPinned = false
        now += 2 * HOUR
        assertTrue(s.purge())
        assertEquals(setOf("saved"), s.texts().toSet()) // pinned is gone too, the folder copy stays, fresh is old now
    }

    @Test
    fun autoDeleteNeverKeepsEverything() {
        val s = store()
        s.addText("a")
        now += 100 * DAY
        s.autoDeleteMs = 0L
        assertFalse(s.purge())
        assertEquals(1, s.size)
    }

    @Test
    fun searchIsCaseInsensitiveAndFiltersByChip() {
        val s = store()
        s.addText("Hello World"); now++
        val p = s.addText("hello pinned")!!; now++
        s.addText("other")
        s.setPinned(listOf(p.id), true)
        s.addImage("img1.png", "image/png")
        assertEquals(setOf("Hello World", "hello pinned"), s.texts(ClipFilter.ALL, "HELLO").toSet())
        assertEquals(listOf("hello pinned"), s.texts(ClipFilter.PINNED, "hello"))
        assertEquals(1, s.query(ClipFilter.IMAGES).size)
        assertEquals(3, s.query(ClipFilter.TEXT).size)
        assertTrue(s.query(ClipFilter.ALL, "img1").isEmpty()) // images never match a text search
        assertTrue(s.query(ClipFilter.ALL, "   ").size == 4) // a blank search shows everything
    }

    @Test
    fun banglaTextSearchWorks() {
        val s = store()
        s.addText("আমার সোনার বাংলা")
        s.addText("english text")
        assertEquals(listOf("আমার সোনার বাংলা"), s.texts(ClipFilter.ALL, "সোনার"))
    }

    @Test
    fun pinnedGoOnTopOfTheAllListOnlyWhileThatSettingIsOn() {
        val s = store()
        val a = s.addText("a")!!; now++
        s.addText("b"); now++
        s.addText("c")
        s.setPinned(listOf(a.id), true)
        assertEquals(listOf("a", "c", "b"), s.texts())
        s.pinnedOnTop = false
        assertEquals(listOf("c", "b", "a"), s.texts())
    }

    @Test
    fun editChangesTextOnlyForTextClips() {
        val s = store()
        val a = s.addText("hello")!!
        val img = s.addImage("x.png", "image/png")
        assertTrue(s.edit(a.id, "hello there"))
        assertEquals("hello there", db.clips[a.id]!!.text)
        assertEquals(listOf("hello there"), s.texts(ClipFilter.ALL, "there"))
        assertFalse(s.edit(a.id, "hello there"))
        assertFalse(s.edit(a.id, "  "))
        assertFalse(s.edit(img.id, "text"))
    }

    @Test
    fun touchMovesToTheTop() {
        val s = store()
        val a = s.addText("a")!!; now++
        s.addText("b"); now++
        assertNotNull(s.touch(a.id))
        assertEquals(listOf("a", "b"), s.texts())
        assertNull(s.touch(999))
    }

    @Test
    fun bulkActionsAndClearing() {
        val s = store()
        val ids = ArrayList<Long>()
        for (t in listOf("a", "b", "c", "d")) {
            ids.add(s.addText(t)!!.id); now++
        }
        assertTrue(s.setPinned(listOf(ids[0], ids[1]), true))
        assertFalse(s.setPinned(listOf(ids[0]), true)) // nothing changed
        val f = s.createFolder("F")!!
        assertTrue(s.moveToFolder(listOf(ids[2]), f.id))
        assertFalse(s.moveToFolder(listOf(ids[3]), 777)) // unknown folder
        assertTrue(s.clearUnpinned()) // only d is loose
        assertEquals(setOf("a", "b", "c"), s.texts().toSet())
        assertTrue(s.delete(listOf(ids[0])))
        assertEquals(setOf("b", "c"), s.texts().toSet())
        assertTrue(s.clearAll())
        assertEquals(0, s.size)
        assertEquals(0, db.clips.size)
        assertEquals(1, s.folders.size) // folders survive clearing the history
    }

    @Test
    fun foldersHaveUniqueNamesAndDeletingOneKeepsItsClips() {
        val s = store()
        assertNull(s.createFolder("  "))
        val work = s.createFolder(" Work ")!!
        assertEquals("Work", work.name)
        assertNull(s.createFolder("work"))
        assertNull(s.createFolder("x".repeat(ClipStore.MAX_FOLDER_NAME + 1)))
        val home = s.createFolder("Home")!!
        assertFalse(s.renameFolder(home.id, "WORK"))
        assertTrue(s.renameFolder(home.id, "Family"))
        val a = s.addText("a")!!
        s.moveToFolder(listOf(a.id), work.id)
        assertEquals(1, s.folderSize(work.id))
        assertEquals(listOf("a"), s.query(ClipFilter.ALL, "", work.id).map { it.text })
        assertTrue(s.query(ClipFilter.ALL, "", home.id).isEmpty())
        assertTrue(s.deleteFolder(work.id))
        assertNull(s.folderOf(work.id))
        assertEquals(0L, s.byId(a.id)!!.folder)
        assertEquals(1, s.size)
        assertFalse(s.deleteFolder(work.id))
    }

    @Test
    fun deletingAFolderAppliesTheLimitToItsClips() {
        val s = store()
        s.maxItems = 1
        val f = s.createFolder("F")!!
        val a = s.addText("a")!!; now++
        s.moveToFolder(listOf(a.id), f.id)
        s.addText("b"); now++
        s.addText("c")
        assertEquals(setOf("a", "c"), s.texts().toSet())
        s.deleteFolder(f.id)
        assertEquals(listOf("c"), s.texts())
    }

    @Test
    fun shortcutsAreCheckedAndMatchedByPrefix() {
        val s = store()
        assertEquals(ClipStore.SHORTCUT_EMPTY, s.checkShortcut(" ", "x"))
        assertEquals(ClipStore.SHORTCUT_HAS_SPACE, s.checkShortcut(";my email", "x"))
        assertEquals(ClipStore.SHORTCUT_NO_TEXT, s.checkShortcut(";e", " "))
        assertEquals(ClipStore.SHORTCUT_TOO_LONG, s.checkShortcut(";" + "e".repeat(ClipStore.MAX_TRIGGER), "x"))
        val email = s.addShortcut(";email", "me@example.com")!!
        val em = s.addShortcut(";em", "short")!!
        val sig = s.addShortcut(";sig", "Regards,\nMe")!!
        assertNull(s.addShortcut(";EMAIL", "dup"))
        assertEquals(ClipStore.SHORTCUT_DUPLICATE, s.checkShortcut(";Sig", "x"))
        assertEquals(ClipStore.SHORTCUT_OK, s.checkShortcut(";sig", "x", sig.id)) // editing itself is fine
        assertEquals(listOf(";em", ";email"), s.matchShortcuts(";em", 5).map { it.trigger }) // exact match first
        assertEquals(listOf(";email"), s.matchShortcuts(";EMAI", 5).map { it.trigger })
        assertEquals(1, s.matchShortcuts(";", 1).size)
        assertTrue(s.matchShortcuts("", 5).isEmpty())
        assertTrue(s.matchShortcuts("hello", 5).isEmpty())
        assertEquals(em.id, s.shortcutFor(";EM")!!.id) // direct insertion: only the complete trigger, any case
        assertEquals(email.id, s.shortcutFor(";email")!!.id)
        assertNull(s.shortcutFor(";e")) // a prefix is not enough
        assertNull(s.shortcutFor(";emails"))
        assertNull(s.shortcutFor(""))
        assertTrue(s.updateShortcut(em.id, ";emm", "changed"))
        assertEquals("changed", db.shortcuts[em.id]!!.text)
        assertTrue(s.deleteShortcut(email.id))
        assertEquals(2, db.shortcuts.size)
    }

    @Test
    fun everythingSurvivesAReload() {
        val s = store()
        val a = s.addText("kept"); now++
        s.addText("second"); now++
        s.setPinned(listOf(a!!.id), true)
        val f = s.createFolder("Notes")!!
        s.addShortcut(";x", "expanded")
        s.moveToFolder(listOf(s.addText("third")!!.id), f.id)
        val again = ClipStore(db) { now }
        again.load()
        assertTrue(again.loaded)
        assertEquals(s.texts(), again.texts())
        assertEquals(listOf("Notes"), again.folders.map { it.name })
        assertEquals(listOf(";x"), again.shortcuts.map { it.trigger })
        assertTrue(again.byId(a.id)!!.pinned)
        // new ids continue after the loaded ones
        val fresh = again.addText("fresh")!!
        assertTrue(fresh.id > s.byId(a.id)!!.id)
        assertEquals(4, again.size)
    }

    @Test
    fun removedImageClipsReportTheirFiles() {
        val s = store()
        val removedFiles = ArrayList<String>()
        s.imageCleaner = { removedFiles.addAll(it) }
        val img = s.addImage("a.png", "image/png")
        s.addText("t")
        s.delete(listOf(img.id))
        assertEquals(listOf("a.png"), removedFiles)
        s.addImage("b.png", "image/png")
        s.clearAll()
        assertEquals(listOf("a.png", "b.png"), removedFiles)
    }

    @Test
    fun theSameImageMovesToTheTopInsteadOfBeingStoredTwice() {
        val s = store()
        val a = s.addImage("a.png", "image/png"); now++
        s.addText("t"); now++
        s.addImage("b.png", "image/png"); now++
        s.setPinned(listOf(a.id), true)
        s.dedupe = false // images are deduplicated whatever the text setting says: one file, one clip
        val again = s.addImage("a.png", "image/png")
        assertSame(a, again)
        assertTrue(again.pinned)
        assertEquals(3, s.size)
        assertEquals(a.id, s.query(ClipFilter.IMAGES, "")[0].id)
    }

    @Test
    fun trimImagesDropsTheOldestLooseOnesUntilItFits() {
        val s = store()
        val removed = ArrayList<String>()
        s.imageCleaner = { removed.addAll(it) }
        val size = mapOf("old" to 40L, "pin" to 40L, "mid" to 40L, "new" to 40L)
        val old = s.addImage("old", "image/png"); now++
        val pin = s.addImage("pin", "image/png"); now++
        s.addImage("mid", "image/png"); now++
        s.addImage("new", "image/png")
        s.setPinned(listOf(pin.id), true)
        assertFalse(s.trimImages(160L) { size.getValue(it) }) // exactly fits
        assertTrue(s.trimImages(100L) { size.getValue(it) })
        assertEquals(setOf("old", "mid"), removed.toSet()) // 160 -> 120 -> 80: the two oldest loose ones; the pinned old-timer and the newest stay
        assertEquals(null, s.byId(old.id))
        assertEquals(2, s.imageCount())
        assertEquals(setOf("pin", "new"), s.imageNames())
        assertEquals(80L, s.imageBytes { size.getValue(it) })
    }

    @Test
    fun trimImagesNeverDropsPinnedOrFolderedImages() {
        val s = store()
        val f = s.createFolder("F")!!
        val a = s.addImage("a", "image/png"); now++
        val b = s.addImage("b", "image/png")
        s.setPinned(listOf(a.id), true)
        s.moveToFolder(listOf(b.id), f.id)
        assertFalse(s.trimImages(1L) { 100L })
        assertEquals(2, s.imageCount())
    }

    @Test
    fun clearImagesRemovesLooseImagesOnly() {
        val s = store()
        val keep = s.addImage("keep", "image/png")
        s.setPinned(listOf(keep.id), true)
        s.addImage("gone", "image/png")
        s.addText("t")
        assertTrue(s.clearImages())
        assertEquals(setOf("keep"), s.imageNames())
        assertEquals(2, s.size)
        assertFalse(s.clearImages())
    }

    @Test
    fun listenersAndRevisionFollowChanges() {
        val s = store()
        var calls = 0
        val l = object : ClipStore.Listener {
            override fun onClipsChanged() {
                calls++
            }
        }
        s.addListener(l)
        s.addListener(l) // only once
        val rev = s.revision
        s.addText("a")
        assertEquals(1, calls)
        assertTrue(s.revision > rev)
        s.removeListener(l)
        s.addText("b")
        assertEquals(1, calls)
    }

    @Test
    fun newestTextSkipsImages() {
        val s = store()
        s.addText("t"); now++
        s.addImage("i.png", "image/png")
        assertEquals("t", s.newestText()!!.text)
    }

    private companion object {
        const val HOUR = 3_600_000L
        const val DAY = 24 * HOUR
    }
}
