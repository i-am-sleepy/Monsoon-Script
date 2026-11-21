package com.monsoonscript.keyboard.clipboard

/**
 * What the keyboard's clipboard panel shows and does (E3, D65), without any drawing: the category chips, the search, the cards, the multi-selection, the
 * 3-dot menu of a card and the bulk actions. The view only draws this and turns touches into calls; everything the phone itself must do (type text into
 * the editor, put a clip back on the system clipboard, share, edit, open the settings) goes through [Host]. Pure Kotlin, unit tested.
 * Main thread only. Call [open] when the panel is shown and [close] when it is hidden.
 */
class ClipPanelModel(private val store: ClipStore, private val host: Host) : ClipStore.Listener {

    interface Host {
        /** Type this text into the editor. */
        fun paste(text: String)

        /** Put this text back on the system clipboard ("Copy again"). */
        fun copyAgain(text: String)

        /** Share this text with another app. */
        fun share(text: String)

        /** Open the editor for this text clip. */
        fun edit(clipId: Long)

        fun openSettings()

        /** The owner flipped the 2 / 1 column toggle. */
        fun columnsChanged(cols: Int)

        /** Anything the view draws changed (list, selection, menu): lay out and redraw. */
        fun changed()
    }

    enum class ChipKind { ALL, PINNED, TEXT, IMAGES, SHORTCUTS, FOLDER }

    class Chip(val kind: ChipKind, val folder: Long, val label: String)

    /** One card: a clip, or (on the Shortcuts chip) a text shortcut. */
    class Card(val clip: ClipItem?, val shortcut: ClipShortcut?) {
        /** Unique per card: a clip's id, or the negated id of a shortcut. */
        val key: Long get() = clip?.id ?: -(shortcut?.id ?: 0L)
    }

    enum class MenuItem { PIN, UNPIN, EDIT, COPY_AGAIN, SHARE, DELETE }

    val chips = ArrayList<Chip>()
    val cards = ArrayList<Card>()

    var chipIndex = 0
        private set
    var query = ""
        private set

    /** 2 or 1 cards per row. */
    var columns = 2

    /** Shown in the empty-list message only. */
    var historyEnabled = true

    /** The card whose 3-dot menu is open, or null. */
    var menuCard: Card? = null
        private set
    var pickerOpen = false
        private set

    private val selected = LinkedHashSet<Long>()
    private var isOpen = false

    init {
        rebuild()
    }

    val selecting: Boolean get() = selected.isNotEmpty()
    val selectionCount: Int get() = selected.size

    fun isSelected(card: Card): Boolean {
        val c = card.clip ?: return false
        return c.id in selected
    }

    // ---------------------------------------------------------------- lifecycle

    /** The panel was shown: start from "All", nothing selected, and follow the store. */
    fun open() {
        selected.clear()
        menuCard = null
        pickerOpen = false
        query = ""
        chipIndex = 0
        if (!isOpen) {
            isOpen = true
            store.addListener(this)
        }
        rebuild()
    }

    fun close() {
        if (isOpen) {
            isOpen = false
            store.removeListener(this)
        }
        selected.clear()
        menuCard = null
        pickerOpen = false
    }

    override fun onClipsChanged() {
        rebuild()
        host.changed()
    }

    // ---------------------------------------------------------------- list

    private fun rebuild() {
        chips.clear()
        chips.add(Chip(ChipKind.ALL, 0L, "All"))
        chips.add(Chip(ChipKind.PINNED, 0L, "Pinned"))
        chips.add(Chip(ChipKind.TEXT, 0L, "Text"))
        chips.add(Chip(ChipKind.IMAGES, 0L, "Images"))
        chips.add(Chip(ChipKind.SHORTCUTS, 0L, "Shortcuts"))
        for (f in store.folders) chips.add(Chip(ChipKind.FOLDER, f.id, f.name))
        if (chipIndex >= chips.size) chipIndex = 0

        cards.clear()
        val chip = chips[chipIndex]
        when (chip.kind) {
            ChipKind.SHORTCUTS -> {
                val q = query.trim().lowercase()
                for (s in store.shortcuts) {
                    if (q.isEmpty() || s.trigger.lowercase().contains(q) || s.text.lowercase().contains(q)) cards.add(Card(null, s))
                }
            }
            ChipKind.FOLDER -> for (c in store.query(ClipFilter.ALL, query, chip.folder)) cards.add(Card(c, null))
            else -> {
                val filter = when (chip.kind) {
                    ChipKind.PINNED -> ClipFilter.PINNED
                    ChipKind.TEXT -> ClipFilter.TEXT
                    ChipKind.IMAGES -> ClipFilter.IMAGES
                    else -> ClipFilter.ALL
                }
                for (c in store.query(filter, query)) cards.add(Card(c, null))
            }
        }
        // forget what no longer exists (deleted elsewhere, e.g. in the app)
        if (selected.isNotEmpty()) selected.retainAll { id -> store.byId(id) != null }
        val m = menuCard?.clip
        if (m != null && store.byId(m.id) == null) menuCard = null
        if (selected.isEmpty()) pickerOpen = false
    }

    val emptyMessage: String
        get() = when {
            query.isNotBlank() -> "No matches"
            chips[chipIndex].kind == ChipKind.SHORTCUTS -> "No shortcuts yet.\nCreate them in the Monsoon app."
            chips[chipIndex].kind == ChipKind.PINNED -> "No pinned clips yet.\nTap the three dots on a clip and choose Pin."
            chips[chipIndex].kind == ChipKind.IMAGES -> "No copied images yet."
            chips[chipIndex].kind == ChipKind.FOLDER -> "This folder is empty.\nSelect clips and move them here."
            !historyEnabled -> "Clipboard history is off.\nTurn it on in the Monsoon app."
            else -> "Nothing copied yet.\nCopy some text and it shows up here."
        }

    fun selectChip(i: Int) {
        if (i !in chips.indices || i == chipIndex) return
        chipIndex = i
        selected.clear()
        menuCard = null
        pickerOpen = false
        rebuild()
        host.changed()
    }

    /** Bottom bar "Recent": the All chip. */
    fun showRecent() = selectChip(0)

    /** Bottom bar "Pinned": the Pinned chip. */
    fun showPinned() = selectChip(1)

    /** The text to search for (the keyboard feeds this while the owner types in the search bar). */
    fun setQuery(q: String) {
        if (q == query) return
        query = q
        selected.clear()
        menuCard = null
        pickerOpen = false
        rebuild()
        host.changed()
    }

    fun toggleColumns() {
        columns = if (columns == 2) 1 else 2
        host.columnsChanged(columns)
        host.changed()
    }

    fun openSettings() = host.openSettings()

    // ---------------------------------------------------------------- touches on cards

    /** Tap: paste the clip (or shortcut); while selecting, select / unselect it. */
    fun tapCard(i: Int) {
        val card = cards.getOrNull(i) ?: return
        val clip = card.clip
        if (clip == null) {
            card.shortcut?.let { host.paste(it.text) }
            return
        }
        if (selecting) {
            if (!selected.remove(clip.id)) selected.add(clip.id)
            if (selected.isEmpty()) pickerOpen = false
            host.changed()
        } else if (clip.isText) {
            host.paste(clip.text)
        }
    }

    /** Long press: start selecting with this clip. Returns true when the press did something. */
    fun longPressCard(i: Int): Boolean {
        val clip = cards.getOrNull(i)?.clip ?: return false
        menuCard = null
        if (selected.add(clip.id)) host.changed()
        return true
    }

    // ---------------------------------------------------------------- the 3-dot menu

    fun openMenu(i: Int) {
        val card = cards.getOrNull(i) ?: return
        if (card.clip == null || selecting) return
        menuCard = card
        host.changed()
    }

    fun closePopups() {
        if (menuCard == null && !pickerOpen) return
        menuCard = null
        pickerOpen = false
        host.changed()
    }

    val hasPopup: Boolean get() = menuCard != null || pickerOpen

    fun menuItems(): List<MenuItem> {
        val clip = menuCard?.clip ?: return emptyList()
        val out = ArrayList<MenuItem>(5)
        out.add(if (clip.pinned) MenuItem.UNPIN else MenuItem.PIN)
        if (clip.isText) out.add(MenuItem.EDIT)
        if (clip.isText) out.add(MenuItem.COPY_AGAIN)
        if (clip.isText) out.add(MenuItem.SHARE)
        out.add(MenuItem.DELETE)
        return out
    }

    fun pickMenu(item: MenuItem) {
        val clip = menuCard?.clip ?: return
        menuCard = null
        when (item) {
            MenuItem.PIN -> store.setPinned(listOf(clip.id), true)
            MenuItem.UNPIN -> store.setPinned(listOf(clip.id), false)
            MenuItem.EDIT -> host.edit(clip.id)
            MenuItem.COPY_AGAIN -> {
                host.copyAgain(clip.text)
                store.touch(clip.id)
            }
            MenuItem.SHARE -> host.share(clip.text)
            MenuItem.DELETE -> store.delete(listOf(clip.id))
        }
        host.changed()
    }

    // ---------------------------------------------------------------- multi-selection

    fun selectAll() {
        for (c in cards) c.clip?.let { selected.add(it.id) }
        host.changed()
    }

    fun clearSelection() {
        if (selected.isEmpty()) return
        selected.clear()
        pickerOpen = false
        host.changed()
    }

    private fun selectedIds(): List<Long> = ArrayList(selected)

    /** Pin all selected clips, or unpin them when they are all pinned already. */
    fun pinSelected() {
        if (selected.isEmpty()) return
        val ids = selectedIds()
        var anyLoose = false
        for (id in ids) if (store.byId(id)?.pinned == false) anyLoose = true
        store.setPinned(ids, anyLoose)
        finishBulk()
    }

    fun deleteSelected() {
        if (selected.isEmpty()) return
        store.delete(selectedIds())
        finishBulk()
    }

    /** Share the selected text clips as one text (blank line between them), in list order. */
    fun exportSelected() {
        if (selected.isEmpty()) return
        val clips = ArrayList<ClipItem>()
        for (c in cards) {
            val clip = c.clip ?: continue
            if (clip.id in selected) clips.add(clip)
        }
        val text = ClipFormat.exportText(clips)
        if (text.isNotEmpty()) host.share(text)
        finishBulk()
    }

    fun openFolderPicker() {
        if (selected.isEmpty()) return
        pickerOpen = true
        host.changed()
    }

    /** The folders to choose from: "No folder" (id 0) first. */
    fun folderChoices(): List<Chip> {
        val out = ArrayList<Chip>()
        out.add(Chip(ChipKind.FOLDER, 0L, "No folder"))
        for (f in store.folders) out.add(Chip(ChipKind.FOLDER, f.id, f.name))
        return out
    }

    fun moveSelected(folderId: Long) {
        if (selected.isEmpty()) return
        store.moveToFolder(selectedIds(), folderId)
        finishBulk()
    }

    private fun finishBulk() {
        selected.clear()
        pickerOpen = false
        menuCard = null
        rebuild()
        host.changed()
    }
}
