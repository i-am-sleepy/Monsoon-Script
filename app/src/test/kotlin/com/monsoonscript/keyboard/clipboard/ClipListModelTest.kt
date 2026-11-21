package com.monsoonscript.keyboard.clipboard

import com.monsoonscript.keyboard.clipboard.ClipListModel.Empty
import com.monsoonscript.keyboard.clipboard.ClipListModel.Scope
import java.util.TimeZone
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ClipListModelTest {
    private val utc = TimeZone.getTimeZone("UTC")
    private val day = 86_400_000L
    private var now = 10 * day + 12 * 3_600_000L // day 10, noon
    private val db = FakeClipDb()
    private val store = ClipStore(db) { now }.also { it.load() }
    private val model = ClipListModel(store)

    /** Clips must be added oldest first, as they are in real use (the store puts a new clip at the top). */
    private fun add(text: String, ageMs: Long = 0L): ClipItem {
        val saved = now
        now -= ageMs
        val c = store.addText(text)!!
        now = saved
        return c
    }

    private fun show(scope: Scope = Scope.HISTORY, folder: Long = 0L) {
        model.show(scope, folder)
        model.rebuild(now, utc)
    }

    private fun texts(): List<String> = model.rows.map { it.clip?.text ?: "#${it.group}" }

    @Test
    fun historyIsGroupedUnderTodayYesterdayAndEarlier() {
        add("old", 5 * day)
        add("yest", day)
        add("now1")
        show()
        assertEquals(listOf("#2", "now1", "#3", "yest", "#4", "old"), texts())
        assertEquals(3, model.clipCount)
    }

    @Test
    fun noHeadersWhenGroupingIsOff() {
        add("a", day)
        add("b")
        model.groupByDate = false
        show()
        assertEquals(listOf("b", "a"), texts())
    }

    @Test
    fun pinnedClipsGetTheirOwnGroupFirstWhileTheyStayOnTop() {
        val p = add("pinme", 3 * day)
        add("today")
        store.setPinned(listOf(p.id), true)
        show()
        assertEquals(listOf("#1", "pinme", "#2", "today"), texts())
        store.pinnedOnTop = false
        show()
        assertEquals(listOf("#2", "today", "#4", "pinme"), texts())
    }

    @Test
    fun pinnedAndFolderScopesAreFlat() {
        val a = add("a")
        val b = add("b", day)
        store.setPinned(listOf(a.id), true)
        val f = store.createFolder("Work")!!
        store.moveToFolder(listOf(b.id), f.id)
        show(Scope.PINNED)
        assertEquals(listOf("a"), texts())
        show(Scope.FOLDER, f.id)
        assertEquals(listOf("b"), texts())
    }

    @Test
    fun searchFiltersAndSaysWhyItIsEmpty() {
        add("hello world")
        add("other")
        show()
        assertEquals(Empty.NONE, model.empty)
        model.setQuery("WORLD")
        model.rebuild(now, utc)
        assertEquals(1, model.clipCount)
        model.setQuery("zzz")
        model.rebuild(now, utc)
        assertEquals(Empty.NO_MATCH, model.empty)
    }

    @Test
    fun emptyKindsFollowTheScope() {
        show()
        assertEquals(Empty.HISTORY, model.empty)
        show(Scope.PINNED)
        assertEquals(Empty.PINNED, model.empty)
        show(Scope.FOLDER, 99L)
        assertEquals(Empty.FOLDER, model.empty)
    }

    @Test
    fun selectionToggleSelectAllAndOrder() {
        add("b", day)
        val a = add("a")
        show()
        assertFalse(model.selecting)
        model.toggle(a)
        assertTrue(model.selecting)
        assertTrue(model.isSelected(a))
        model.toggle(a)
        assertFalse(model.selecting)
        model.selectAll()
        assertEquals(2, model.selectionCount)
        assertEquals(listOf("a", "b"), model.selectedClips().map { it.text })
        model.clearSelection()
        assertEquals(0, model.selectionCount)
    }

    @Test
    fun pinSelectedPinsThenUnpinsAndEndsTheSelection() {
        val a = add("a")
        val b = add("b")
        show()
        model.toggle(a)
        model.toggle(b)
        model.pinSelected()
        assertTrue(a.pinned && b.pinned)
        assertFalse(model.selecting)
        model.toggle(a)
        model.toggle(b)
        model.pinSelected()
        assertFalse(a.pinned || b.pinned)
    }

    @Test
    fun pinSelectedPinsEveryoneWhenOnlySomeArePinned() {
        val a = add("a")
        val b = add("b")
        store.setPinned(listOf(a.id), true)
        show()
        model.toggle(a)
        model.toggle(b)
        model.pinSelected()
        assertTrue(a.pinned && b.pinned)
    }

    @Test
    fun deleteAndMoveSelected() {
        val a = add("a")
        val b = add("b")
        val c = add("c")
        val f = store.createFolder("Keep")!!
        show()
        model.toggle(a)
        model.moveSelected(f.id)
        assertEquals(f.id, a.folder)
        model.toggle(b)
        model.deleteSelected()
        assertEquals(setOf("a", "c"), store.query(ClipFilter.ALL).map { it.text }.toSet())
        assertEquals(null, store.byId(b.id))
        assertEquals(c.id, store.byId(c.id)!!.id)
        assertFalse(model.selecting)
    }

    @Test
    fun selectionForgetsClipsDeletedElsewhere() {
        val a = add("a")
        add("b")
        show()
        model.toggle(a)
        store.delete(listOf(a.id))
        model.rebuild(now, utc)
        assertFalse(model.selecting)
    }

    @Test
    fun exportTextJoinsTheSelectedClipsWithABlankLine() {
        val b = add("second", day)
        val a = add("first")
        show()
        model.toggle(b)
        model.toggle(a)
        assertEquals("first\n\nsecond", model.exportText())
    }

    @Test
    fun showResetsSelectionAndSearch() {
        val a = add("a")
        show()
        model.toggle(a)
        model.setQuery("x")
        show(Scope.PINNED)
        assertFalse(model.selecting)
        assertEquals("", model.query)
    }
}
