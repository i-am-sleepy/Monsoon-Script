package com.monsoonscript.keyboard.clipboard

import com.monsoonscript.keyboard.clipboard.ClipPanelModel.ChipKind
import com.monsoonscript.keyboard.clipboard.ClipPanelModel.MenuItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ClipPanelModelTest {

    private class Host : ClipPanelModel.Host {
        val pasted = ArrayList<String>()
        val copied = ArrayList<String>()
        val shared = ArrayList<String>()
        val pastedImages = ArrayList<Long>()
        val copiedImages = ArrayList<Long>()
        val sharedImages = ArrayList<Long>()
        val edited = ArrayList<Long>()
        val columns = ArrayList<Int>()
        var settings = 0
        var changes = 0
        override fun paste(text: String) {
            pasted.add(text)
        }
        override fun copyAgain(text: String) {
            copied.add(text)
        }
        override fun share(text: String) {
            shared.add(text)
        }
        override fun pasteImage(clip: ClipItem) {
            pastedImages.add(clip.id)
        }
        override fun copyAgainImage(clip: ClipItem) {
            copiedImages.add(clip.id)
        }
        override fun shareImage(clip: ClipItem) {
            sharedImages.add(clip.id)
        }
        override fun edit(clipId: Long) {
            edited.add(clipId)
        }
        override fun openSettings() {
            settings++
        }
        override fun columnsChanged(cols: Int) {
            columns.add(cols)
        }
        override fun changed() {
            changes++
        }
    }

    private var now = 1_000L
    private val db = FakeClipDb()
    private val store = ClipStore(db) { now }.also { it.load() }
    private val host = Host()
    private val model = ClipPanelModel(store, host)

    private fun add(vararg texts: String): List<ClipItem> = texts.map { now++; store.addText(it)!! }

    private fun shown(): List<String> = model.cards.map { it.clip?.text ?: it.shortcut!!.trigger }

    private fun indexOf(text: String): Int = model.cards.indexOfFirst { it.clip?.text == text }

    @Test
    fun opensOnAllWithNewestFirstAndTheFixedChips() {
        add("a", "b", "c")
        model.open()
        assertEquals(listOf("c", "b", "a"), shown())
        assertEquals(listOf("All", "Pinned", "Text", "Images", "Shortcuts"), model.chips.map { it.label })
        assertEquals(0, model.chipIndex)
    }

    @Test
    fun foldersBecomeChipsAndShowTheirClips() {
        val (a, _) = add("a", "b")
        val f = store.createFolder("Work")!!
        store.moveToFolder(listOf(a.id), f.id)
        model.open()
        assertEquals("Work", model.chips.last().label)
        model.selectChip(model.chips.size - 1)
        assertEquals(listOf("a"), shown())
        assertEquals(ChipKind.FOLDER, model.chips[model.chipIndex].kind)
    }

    @Test
    fun chipsFilterAndBottomBarShortcutsJumpThere() {
        val (a, _) = add("a", "b")
        store.setPinned(listOf(a.id), true)
        store.addImage("x.png", "image/png")
        model.open()
        model.showPinned()
        assertEquals(listOf("a"), shown())
        model.selectChip(2)
        assertEquals(listOf("b", "a"), shown())
        model.selectChip(3)
        assertEquals(1, model.cards.size)
        assertTrue(model.cards[0].clip!!.isImage)
        model.showRecent()
        assertEquals(3, model.cards.size)
    }

    @Test
    fun tapPastesAndTheListStaysPut() {
        add("one", "two")
        model.open()
        model.tapCard(indexOf("one"))
        assertEquals(listOf("one"), host.pasted)
        assertEquals(listOf("two", "one"), shown())
        model.tapCard(99) // out of range: ignored
        assertEquals(1, host.pasted.size)
    }

    @Test
    fun longPressStartsSelectingAndTapsToggle() {
        add("a", "b", "c")
        model.open()
        assertTrue(model.longPressCard(indexOf("b")))
        assertTrue(model.selecting)
        assertTrue(model.isSelected(model.cards[indexOf("b")]))
        model.tapCard(indexOf("a")) // selecting: a tap selects instead of pasting
        assertEquals(2, model.selectionCount)
        assertTrue(host.pasted.isEmpty())
        model.tapCard(indexOf("a"))
        model.tapCard(indexOf("b"))
        assertFalse(model.selecting) // the last one was unselected: selecting ends
        model.tapCard(indexOf("c"))
        assertEquals(listOf("c"), host.pasted)
    }

    @Test
    fun selectAllClearAndBulkPin() {
        val (a, b, _) = add("a", "b", "c")
        store.setPinned(listOf(a.id), true)
        model.open()
        model.selectAll()
        assertEquals(3, model.selectionCount)
        model.pinSelected() // some are loose: pin all
        assertFalse(model.selecting)
        assertTrue(store.byId(b.id)!!.pinned)
        model.selectAll()
        model.pinSelected() // all pinned: unpin all
        assertTrue(store.query(ClipFilter.PINNED).isEmpty())
        model.selectAll()
        model.clearSelection()
        assertFalse(model.selecting)
    }

    @Test
    fun selectAllOnlyTakesWhatTheChipShows() {
        val (a, _, _) = add("a", "b", "c")
        store.setPinned(listOf(a.id), true)
        model.open()
        model.showPinned()
        model.selectAll()
        assertEquals(1, model.selectionCount)
        model.deleteSelected()
        assertEquals(2, store.size)
        assertEquals("b", store.texts().first { it == "b" })
    }

    @Test
    fun bulkMoveToFolderAndNoFolder() {
        val (a, b, _) = add("a", "b", "c")
        val f = store.createFolder("Notes")!!
        model.open()
        model.longPressCard(indexOf("a"))
        model.tapCard(indexOf("b"))
        model.openFolderPicker()
        assertTrue(model.pickerOpen)
        assertEquals(listOf("No folder", "Notes"), model.folderChoices().map { it.label })
        model.moveSelected(f.id)
        assertEquals(f.id, store.byId(a.id)!!.folder)
        assertEquals(f.id, store.byId(b.id)!!.folder)
        assertFalse(model.selecting)
        assertFalse(model.pickerOpen)
        model.selectChip(model.chips.size - 1)
        assertEquals(setOf("a", "b"), shown().toSet())
        model.selectAll()
        model.moveSelected(0L)
        assertEquals(0L, store.byId(a.id)!!.folder)
    }

    @Test
    fun exportSharesSelectedTextInListOrder() {
        add("first", "second", "third")
        store.addImage("p.png", "image/png")
        model.open()
        model.longPressCard(indexOf("first"))
        model.tapCard(indexOf("third"))
        model.exportSelected()
        assertEquals(listOf("third\n\nfirst"), host.shared)
        assertFalse(model.selecting)
    }

    @Test
    fun menuListsTheRightItemsPerClip() {
        val (a, b) = add("a", "b")
        val img = store.addImage("i.png", "image/png")
        store.setPinned(listOf(a.id), true)
        model.open()
        model.openMenu(indexOf("b"))
        assertEquals(listOf(MenuItem.PIN, MenuItem.EDIT, MenuItem.COPY_AGAIN, MenuItem.SHARE, MenuItem.DELETE), model.menuItems())
        model.closePopups()
        assertNull(model.menuCard)
        model.openMenu(indexOf("a"))
        assertEquals(MenuItem.UNPIN, model.menuItems()[0])
        model.closePopups()
        model.openMenu(model.cards.indexOfFirst { it.clip!!.id == img.id })
        assertEquals(listOf(MenuItem.PIN, MenuItem.COPY_AGAIN, MenuItem.SHARE, MenuItem.DELETE), model.menuItems())
        assertTrue(b.id > 0)
    }

    @Test
    fun imageCardsPasteCopyAndShareThroughTheImageCalls() {
        val img = store.addImage("i.png", "image/png")
        model.open()
        model.tapCard(0)
        assertEquals(listOf(img.id), host.pastedImages)
        assertTrue(host.pasted.isEmpty())
        model.openMenu(0)
        model.pickMenu(MenuItem.COPY_AGAIN)
        assertEquals(listOf(img.id), host.copiedImages)
        assertTrue(host.copied.isEmpty())
        model.openMenu(0)
        model.pickMenu(MenuItem.SHARE)
        assertEquals(listOf(img.id), host.sharedImages)
        assertTrue(host.shared.isEmpty())
    }

    @Test
    fun menuActionsPinEditCopyShareDelete() {
        val (a, b) = add("a", "b")
        model.open()
        model.openMenu(indexOf("a"))
        model.pickMenu(MenuItem.PIN)
        assertTrue(store.byId(a.id)!!.pinned)
        assertNull(model.menuCard)

        model.openMenu(indexOf("b"))
        model.pickMenu(MenuItem.EDIT)
        assertEquals(listOf(b.id), host.edited)

        now += 10
        model.openMenu(indexOf("a"))
        model.pickMenu(MenuItem.COPY_AGAIN)
        assertEquals(listOf("a"), host.copied)
        assertEquals("a", store.query(ClipFilter.TEXT)[0].text) // moved to the top

        model.openMenu(indexOf("b"))
        model.pickMenu(MenuItem.SHARE)
        assertEquals(listOf("b"), host.shared)

        model.openMenu(indexOf("b"))
        model.pickMenu(MenuItem.DELETE)
        assertNull(store.byId(b.id))
        assertEquals(listOf("a"), shown())
    }

    @Test
    fun menuDoesNotOpenWhileSelecting() {
        add("a", "b")
        model.open()
        model.longPressCard(0)
        model.openMenu(1)
        assertNull(model.menuCard)
    }

    @Test
    fun searchFiltersAndEmptyMessagesFollowTheSituation() {
        add("Hello there", "other")
        model.open()
        model.setQuery("HELLO")
        assertEquals(listOf("Hello there"), shown())
        model.setQuery("zzz")
        assertTrue(model.cards.isEmpty())
        assertEquals("No matches", model.emptyMessage)
        model.setQuery("")
        model.selectChip(1)
        assertTrue(model.emptyMessage.startsWith("No pinned clips yet"))
        model.selectChip(4)
        assertTrue(model.emptyMessage.startsWith("No shortcuts yet"))
        model.selectChip(0)
        store.clearAll()
        assertTrue(model.emptyMessage.startsWith("Nothing copied yet"))
        model.historyEnabled = false
        assertTrue(model.emptyMessage.startsWith("Clipboard history is off"))
    }

    @Test
    fun shortcutsChipListsShortcutsAndTapInsertsTheirText() {
        store.addShortcut(";email", "me@example.com")
        store.addShortcut(";sig", "Regards,\nMe")
        add("a clip")
        model.open()
        model.selectChip(4)
        assertEquals(listOf(";email", ";sig"), shown())
        model.tapCard(1)
        assertEquals(listOf("Regards,\nMe"), host.pasted)
        model.setQuery("example") // the search looks in the text too
        assertEquals(listOf(";email"), shown())
        assertTrue(model.cards[0].key < 0) // shortcut cards never collide with clip ids
        assertFalse(model.longPressCard(0)) // shortcuts are not selectable
        model.openMenu(0)
        assertNull(model.menuCard)
    }

    @Test
    fun theListFollowsTheStoreWhileOpenButNotAfterClose() {
        model.open()
        val before = host.changes
        add("new one")
        assertEquals(listOf("new one"), shown())
        assertTrue(host.changes > before)
        model.close()
        val after = host.changes
        add("later")
        assertEquals(after, host.changes)
    }

    @Test
    fun selectionDropsClipsThatWereDeletedElsewhere() {
        val (a, b) = add("a", "b")
        model.open()
        model.longPressCard(indexOf("a"))
        model.tapCard(indexOf("b"))
        store.delete(listOf(a.id)) // e.g. in the app
        assertEquals(1, model.selectionCount)
        store.delete(listOf(b.id))
        assertFalse(model.selecting)
    }

    @Test
    fun columnsToggleAndSettingsAreReported() {
        model.open()
        assertEquals(2, model.columns)
        model.toggleColumns()
        assertEquals(1, model.columns)
        model.toggleColumns()
        assertEquals(listOf(1, 2), host.columns)
        model.openSettings()
        assertEquals(1, host.settings)
    }

    @Test
    fun openingAgainStartsFresh() {
        add("a")
        model.open()
        model.selectChip(2)
        model.setQuery("a")
        model.longPressCard(0)
        model.close()
        model.open()
        assertEquals(0, model.chipIndex)
        assertEquals("", model.query)
        assertFalse(model.selecting)
    }

    // the store's texts in list order, for the assertions above
    private fun ClipStore.texts(): List<String> = query(ClipFilter.ALL).map { it.text }
}
