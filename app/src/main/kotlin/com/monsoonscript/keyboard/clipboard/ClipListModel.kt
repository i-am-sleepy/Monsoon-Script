package com.monsoonscript.keyboard.clipboard

import java.util.TimeZone

/**
 * The logic of one list in the app's clipboard screens (E3.6, D67): History, Pinned or the clips of one folder. It turns the store into rows
 * (optionally under date headers), keeps the search text and the multi-selection, and runs the bulk actions. Pure Kotlin; the screen only shows [rows].
 */
class ClipListModel(private val store: ClipStore) {

    enum class Scope { HISTORY, PINNED, FOLDER }

    /** A header row has [clip] == null and says which [group] it starts; every other row is a clip. */
    class Row(val group: Int, val clip: ClipItem?)

    /** Why the list is empty (the screen picks the words). */
    enum class Empty { NONE, HISTORY, PINNED, FOLDER, NO_MATCH }

    var scope = Scope.HISTORY
        private set

    /** The folder shown when [scope] is [Scope.FOLDER]. */
    var folder = 0L
        private set

    var query = ""
        private set

    /** History only: Pinned / Today / Yesterday / Earlier headers. The other scopes are flat. */
    var groupByDate = true

    val rows = ArrayList<Row>()

    /** Number of clips in [rows] (headers not counted). */
    var clipCount = 0
        private set

    private val selected = LinkedHashSet<Long>()
    val selecting: Boolean get() = selected.isNotEmpty()
    val selectionCount: Int get() = selected.size

    fun show(scope: Scope, folder: Long = 0L) {
        this.scope = scope
        this.folder = folder
        selected.clear()
        query = ""
        rebuild()
    }

    fun setQuery(q: String) {
        if (q == query) return
        query = q
        rebuild()
    }

    /** Rebuild the rows from the store (after any change); forgets selected clips that are gone. */
    fun rebuild(now: Long = System.currentTimeMillis(), zone: TimeZone = TimeZone.getDefault()) {
        rows.clear()
        val found = when (scope) {
            Scope.HISTORY -> store.query(ClipFilter.ALL, query)
            Scope.PINNED -> store.query(ClipFilter.PINNED, query)
            Scope.FOLDER -> store.query(ClipFilter.ALL, query, folder)
        }
        clipCount = found.size
        val grouped = scope == Scope.HISTORY && groupByDate
        var last = -1
        for (c in found) {
            if (grouped) {
                val g = groupOf(c, now, zone)
                if (g != last) {
                    rows.add(Row(g, null))
                    last = g
                }
            }
            rows.add(Row(GROUP_NONE, c))
        }
        if (selected.isNotEmpty()) selected.retainAll { store.byId(it) != null }
    }

    private fun groupOf(c: ClipItem, now: Long, zone: TimeZone): Int {
        if (c.pinned && store.pinnedOnTop) return GROUP_PINNED
        val days = ClipFormat.daysAgo(c.used, now, zone)
        return when {
            days <= 0L -> GROUP_TODAY
            days == 1L -> GROUP_YESTERDAY
            else -> GROUP_EARLIER
        }
    }

    val empty: Empty
        get() = when {
            clipCount > 0 -> Empty.NONE
            query.isNotBlank() -> Empty.NO_MATCH
            scope == Scope.HISTORY -> Empty.HISTORY
            scope == Scope.PINNED -> Empty.PINNED
            else -> Empty.FOLDER
        }

    // ---------------------------------------------------------------- selection

    fun isSelected(clip: ClipItem): Boolean = clip.id in selected

    fun toggle(clip: ClipItem) {
        if (!selected.remove(clip.id)) selected.add(clip.id)
    }

    fun selectAll() {
        for (r in rows) r.clip?.let { selected.add(it.id) }
    }

    fun clearSelection() = selected.clear()

    /** The selected clips in list order. */
    fun selectedClips(): List<ClipItem> {
        val out = ArrayList<ClipItem>(selected.size)
        for (r in rows) r.clip?.let { if (it.id in selected) out.add(it) }
        return out
    }

    // ---------------------------------------------------------------- bulk actions (they end the selection)

    /** Pins all selected clips, or unpins them when they are all pinned already. */
    fun pinSelected() {
        val clips = selectedClips()
        if (clips.isEmpty()) return
        val pin = clips.any { !it.pinned }
        store.setPinned(clips.map { it.id }, pin)
        selected.clear()
        rebuild()
    }

    fun deleteSelected() {
        if (selected.isEmpty()) return
        store.delete(ArrayList(selected))
        selected.clear()
        rebuild()
    }

    /** [folderId] 0 = out of any folder. */
    fun moveSelected(folderId: Long) {
        if (selected.isEmpty()) return
        store.moveToFolder(ArrayList(selected), folderId)
        selected.clear()
        rebuild()
    }

    /** The selected text clips as one text (blank line between), for sharing. */
    fun exportText(): String = ClipFormat.exportText(selectedClips())

    companion object {
        const val GROUP_NONE = 0
        const val GROUP_PINNED = 1
        const val GROUP_TODAY = 2
        const val GROUP_YESTERDAY = 3
        const val GROUP_EARLIER = 4
    }
}
