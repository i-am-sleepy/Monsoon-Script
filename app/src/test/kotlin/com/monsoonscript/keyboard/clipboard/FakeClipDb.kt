package com.monsoonscript.keyboard.clipboard

/** Keeps what the store writes, like the real database, so a second store can load it again. */
class FakeClipDb : ClipDb {
    val clips = LinkedHashMap<Long, ClipItem>()
    val folders = LinkedHashMap<Long, ClipFolder>()
    val shortcuts = LinkedHashMap<Long, ClipShortcut>()
    var writes = 0
    override fun loadClips(): List<ClipItem> = clips.values.map { copy(it) }
    override fun loadFolders(): List<ClipFolder> = folders.values.map { ClipFolder(it.id, it.name, it.created) }
    override fun loadShortcuts(): List<ClipShortcut> = shortcuts.values.map { ClipShortcut(it.id, it.trigger, it.text) }
    override fun saveClips(items: List<ClipItem>) {
        writes++
        for (c in items) clips[c.id] = copy(c)
    }
    override fun deleteClips(ids: LongArray) {
        writes++
        for (id in ids) clips.remove(id)
    }
    override fun saveFolder(folder: ClipFolder) {
        folders[folder.id] = ClipFolder(folder.id, folder.name, folder.created)
    }
    override fun deleteFolder(id: Long) {
        folders.remove(id)
    }
    override fun saveShortcut(shortcut: ClipShortcut) {
        shortcuts[shortcut.id] = ClipShortcut(shortcut.id, shortcut.trigger, shortcut.text)
    }
    override fun deleteShortcut(id: Long) {
        shortcuts.remove(id)
    }
    private fun copy(c: ClipItem) = ClipItem(c.id, c.kind, c.text, c.image, c.mime, c.pinned, c.folder, c.created, c.used)
}
