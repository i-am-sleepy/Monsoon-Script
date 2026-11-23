package com.monsoonscript.keyboard.clipboard

/**
 * The clipboard history, pinned clips, folders and text shortcuts (E3, D65). Pure Kotlin: no Android classes, so it is unit tested on the JVM.
 *
 * Everything is kept in memory (a few hundred clips) so the panel, search and capture never wait for storage; every change is also written through [ClipDb].
 * NOT thread-safe: call it from the main thread only (the Android side loads the data on a worker thread and hands it over with [install]).
 *
 * Rules (owner's settings, D65):
 *  - "loose" clips (not pinned, not in a folder) are limited to [maxItems]; the oldest loose clip is dropped first. Pinned and foldered clips never count and are never dropped by the limit.
 *  - [autoDeleteMs] > 0 deletes loose clips older than that (by last use). Pinned clips are exempt while [keepPinned] is on. Foldered clips are always exempt (they are saved collections).
 *  - copying the same text again moves the clip to the top instead of adding a second one while [dedupe] is on (pin and folder are kept).
 */
class ClipStore(private val db: ClipDb, private val clock: () -> Long = { System.currentTimeMillis() }) {

    interface Listener {
        fun onClipsChanged()
    }

    var maxItems = 100
    var autoDeleteMs = 0L
    var keepPinned = true
    var dedupe = true
    var pinnedOnTop = true

    /** Called with the file names of image clips that were removed, so the Android side can delete the files (E3.8). */
    var imageCleaner: ((List<String>) -> Unit)? = null

    private val clips = ArrayList<ClipItem>() // newest first
    private val folderList = ArrayList<ClipFolder>()
    private val shortcutList = ArrayList<ClipShortcut>()
    private val listeners = ArrayList<Listener>(2)
    private var nextClipId = 1L
    private var nextFolderId = 1L
    private var nextShortcutId = 1L

    var loaded = false
        private set

    /** Grows with every change; views compare it to know when to rebuild their list. */
    var revision = 0
        private set

    val folders: List<ClipFolder> get() = folderList
    val shortcuts: List<ClipShortcut> get() = shortcutList
    val size: Int get() = clips.size

    fun addListener(l: Listener) {
        if (l !in listeners) listeners.add(l)
    }

    fun removeListener(l: Listener) {
        listeners.remove(l)
    }

    private fun changed() {
        revision++
        for (i in listeners.indices) listeners[i].onClipsChanged()
    }

    // ---------------------------------------------------------------- loading

    /** Hand over what the database holds (main thread). Replaces everything in memory. */
    fun install(c: List<ClipItem>, f: List<ClipFolder>, s: List<ClipShortcut>) {
        clips.clear()
        clips.addAll(c.sortedWith(compareByDescending<ClipItem> { it.used }.thenByDescending { it.id }))
        folderList.clear()
        folderList.addAll(f.sortedBy { it.id })
        shortcutList.clear()
        shortcutList.addAll(s.sortedBy { it.id })
        nextClipId = maxOf(nextClipId, (clips.maxOfOrNull { it.id } ?: 0L) + 1)
        nextFolderId = maxOf(nextFolderId, (folderList.maxOfOrNull { it.id } ?: 0L) + 1)
        nextShortcutId = maxOf(nextShortcutId, (shortcutList.maxOfOrNull { it.id } ?: 0L) + 1)
        loaded = true
        enforce(clock())
        changed()
    }

    /** Synchronous load (tests, or a caller that is already on a worker thread). */
    fun load() = install(db.loadClips(), db.loadFolders(), db.loadShortcuts())

    // ---------------------------------------------------------------- adding clips

    /**
     * A new text was copied. Returns the clip (new, or the existing one moved to the top), or null when the text is blank or too long to paste exactly
     * (longer than [MAX_TEXT] characters: a cut-off copy would be worse than none).
     */
    fun addText(raw: String): ClipItem? {
        if (raw.isBlank() || raw.length > MAX_TEXT) return null
        val now = clock()
        if (dedupe) {
            val i = indexOfText(raw)
            if (i >= 0) {
                val c = clips.removeAt(i)
                c.used = now
                clips.add(0, c)
                db.saveClips(listOf(c))
                enforce(now)
                changed()
                return c
            }
        }
        val c = ClipItem(nextClipId++, ClipKind.TEXT, raw, null, null, false, 0L, now, now)
        clips.add(0, c)
        db.saveClips(listOf(c))
        enforce(now)
        changed()
        return c
    }

    /**
     * An image clip (E3.8): [file] is the name inside the private image folder (its name is the SHA-256 of the picture, so the same picture always has the
     * same name). An image that is saved already only moves to the top, whatever the duplicate setting says: one file must never belong to two clips,
     * because deleting one clip deletes the file.
     */
    fun addImage(file: String, mime: String): ClipItem {
        val now = clock()
        val i = indexOfImage(file)
        if (i >= 0) {
            val old = clips.removeAt(i)
            old.used = now
            clips.add(0, old)
            db.saveClips(listOf(old))
            enforce(now)
            changed()
            return old
        }
        val c = ClipItem(nextClipId++, ClipKind.IMAGE, "", file, mime, false, 0L, now, now)
        clips.add(0, c)
        db.saveClips(listOf(c))
        enforce(now)
        changed()
        return c
    }

    private fun indexOfImage(file: String): Int {
        for (i in clips.indices) if (clips[i].image == file) return i
        return -1
    }

    private fun indexOfText(raw: String): Int {
        for (i in clips.indices) {
            val c = clips[i]
            if (c.kind == ClipKind.TEXT && c.text == raw) return i
        }
        return -1
    }

    fun byId(id: Long): ClipItem? {
        for (c in clips) if (c.id == id) return c
        return null
    }

    /** The newest text clip, or null. */
    fun newestText(): ClipItem? {
        for (c in clips) if (c.kind == ClipKind.TEXT) return c
        return null
    }

    // ---------------------------------------------------------------- limits and auto delete

    /** Apply the limit and the auto-delete age now (also called at start-up and when the panel opens). Returns true if something was removed. */
    fun purge(): Boolean {
        val removed = enforce(clock())
        if (removed) changed()
        return removed
    }

    private fun enforce(now: Long): Boolean {
        val doomed = HashSet<Long>()
        if (autoDeleteMs > 0) {
            for (c in clips) {
                if (c.folder != 0L) continue
                if (c.pinned && keepPinned) continue
                if (now - c.used > autoDeleteMs) doomed.add(c.id)
            }
        }
        var loose = 0
        for (c in clips) { // newest first: the first maxItems loose clips stay
            if (c.pinned || c.folder != 0L || c.id in doomed) continue
            loose++
            if (loose > maxItems) doomed.add(c.id)
        }
        return remove(doomed)
    }

    private fun remove(doomed: Set<Long>): Boolean {
        if (doomed.isEmpty()) return false
        val gone = LongArray(doomed.size)
        var n = 0
        val images = ArrayList<String>()
        val it = clips.iterator()
        while (it.hasNext()) {
            val c = it.next()
            if (c.id in doomed) {
                it.remove()
                gone[n++] = c.id
                val file = c.image
                if (file != null) images.add(file)
            }
        }
        if (n == 0) return false
        db.deleteClips(if (n == gone.size) gone else gone.copyOf(n))
        if (images.isNotEmpty()) imageCleaner?.invoke(images)
        return true
    }

    // ---------------------------------------------------------------- actions on clips

    fun delete(ids: Collection<Long>): Boolean {
        if (!remove(ids.toHashSet())) return false
        changed()
        return true
    }

    /** Pin or unpin. Unpinning makes a clip loose again, so the limit is applied. */
    fun setPinned(ids: Collection<Long>, pinned: Boolean): Boolean {
        val set = ids.toHashSet()
        val changedClips = ArrayList<ClipItem>()
        for (c in clips) if (c.id in set && c.pinned != pinned) {
            c.pinned = pinned
            changedClips.add(c)
        }
        if (changedClips.isEmpty()) return false
        db.saveClips(changedClips)
        if (!pinned) enforce(clock())
        changed()
        return true
    }

    /** Put clips into a folder, or take them out with [folderId] = 0. False if the folder does not exist. */
    fun moveToFolder(ids: Collection<Long>, folderId: Long): Boolean {
        if (folderId != 0L && folderOf(folderId) == null) return false
        val set = ids.toHashSet()
        val changedClips = ArrayList<ClipItem>()
        for (c in clips) if (c.id in set && c.folder != folderId) {
            c.folder = folderId
            changedClips.add(c)
        }
        if (changedClips.isEmpty()) return false
        db.saveClips(changedClips)
        if (folderId == 0L) enforce(clock())
        changed()
        return true
    }

    /** Change the text of a text clip (3-dot menu "Edit"). False for blank, too long, unchanged or image clips. */
    fun edit(id: Long, newText: String): Boolean {
        val c = byId(id) ?: return false
        if (c.kind != ClipKind.TEXT || newText.isBlank() || newText.length > MAX_TEXT || newText == c.text) return false
        c.text = newText
        db.saveClips(listOf(c))
        changed()
        return true
    }

    /** "Copy again": the clip moves to the top. */
    fun touch(id: Long): ClipItem? {
        val i = clips.indexOfFirst { it.id == id }
        if (i < 0) return null
        val c = clips.removeAt(i)
        c.used = clock()
        clips.add(0, c)
        db.saveClips(listOf(c))
        changed()
        return c
    }

    /** Clear unpinned: removes loose clips (not pinned, not in a folder). */
    fun clearUnpinned(): Boolean {
        val doomed = HashSet<Long>()
        for (c in clips) if (!c.pinned && c.folder == 0L) doomed.add(c.id)
        if (!remove(doomed)) return false
        changed()
        return true
    }

    // ---------------------------------------------------------------- image storage (E3.8)

    /** How many image clips there are. */
    fun imageCount(): Int {
        var n = 0
        for (c in clips) if (c.kind == ClipKind.IMAGE) n++
        return n
    }

    /** The files of all image clips (the Android side deletes files nobody refers to). */
    fun imageNames(): Set<String> {
        val out = HashSet<String>()
        for (c in clips) c.image?.let { out.add(it) }
        return out
    }

    /** Disk space of all images, as [sizeOf] measures one file. */
    fun imageBytes(sizeOf: (String) -> Long): Long {
        var total = 0L
        for (c in clips) c.image?.let { total += sizeOf(it) }
        return total
    }

    /**
     * Keep the images within [maxBytes]: the oldest loose image clips (not pinned, not in a folder) go first until it fits. Pinned and foldered images
     * count but are never dropped, so the total can stay above the cap. Returns true if something was removed.
     */
    fun trimImages(maxBytes: Long, sizeOf: (String) -> Long): Boolean {
        if (imageCount() == 0) return false
        var total = imageBytes(sizeOf)
        if (total <= maxBytes) return false
        val doomed = HashSet<Long>()
        for (i in clips.indices.reversed()) { // oldest first
            if (total <= maxBytes) break
            val c = clips[i]
            val file = c.image ?: continue
            if (c.pinned || c.folder != 0L) continue
            doomed.add(c.id)
            total -= sizeOf(file)
        }
        if (!remove(doomed)) return false
        changed()
        return true
    }

    /** "Delete all images" (settings): removes the loose image clips; pinned ones and ones in folders stay. */
    fun clearImages(): Boolean {
        val doomed = HashSet<Long>()
        for (c in clips) if (c.kind == ClipKind.IMAGE && !c.pinned && c.folder == 0L) doomed.add(c.id)
        if (!remove(doomed)) return false
        changed()
        return true
    }

    /** Clear the entire history: every clip, pinned and foldered ones too. Folders and shortcuts stay. */
    fun clearAll(): Boolean {
        val doomed = HashSet<Long>()
        for (c in clips) doomed.add(c.id)
        if (!remove(doomed)) return false
        changed()
        return true
    }

    // ---------------------------------------------------------------- listing and search

    /**
     * The clips to show, newest first. [filter] is the chip, [search] a case-insensitive text search (image clips never match a search),
     * [folder] limits to one folder (default: any). With [pinnedOnTop] the "All" list shows pinned clips first.
     */
    fun query(filter: ClipFilter, search: String = "", folder: Long = ANY_FOLDER): ArrayList<ClipItem> {
        val q = search.trim().lowercase()
        val out = ArrayList<ClipItem>()
        for (c in clips) {
            when (filter) {
                ClipFilter.PINNED -> if (!c.pinned) continue
                ClipFilter.TEXT -> if (c.kind != ClipKind.TEXT) continue
                ClipFilter.IMAGES -> if (c.kind != ClipKind.IMAGE) continue
                ClipFilter.ALL -> Unit
            }
            if (folder != ANY_FOLDER && c.folder != folder) continue
            if (q.isNotEmpty() && !(c.kind == ClipKind.TEXT && c.lower().contains(q))) continue
            out.add(c)
        }
        if (pinnedOnTop && filter == ClipFilter.ALL && folder == ANY_FOLDER && out.any { it.pinned }) {
            val pinned = ArrayList<ClipItem>()
            val rest = ArrayList<ClipItem>()
            for (c in out) if (c.pinned) pinned.add(c) else rest.add(c)
            pinned.addAll(rest)
            return pinned
        }
        return out
    }

    // ---------------------------------------------------------------- folders

    fun folderOf(id: Long): ClipFolder? {
        for (f in folderList) if (f.id == id) return f
        return null
    }

    fun folderSize(id: Long): Int {
        var n = 0
        for (c in clips) if (c.folder == id) n++
        return n
    }

    private fun folderNameTaken(name: String, exceptId: Long): Boolean {
        for (f in folderList) if (f.id != exceptId && f.name.equals(name, ignoreCase = true)) return true
        return false
    }

    /** New folder; null when the name is empty, longer than [MAX_FOLDER_NAME] or already used (case-insensitive). */
    fun createFolder(name: String): ClipFolder? {
        val n = name.trim()
        if (n.isEmpty() || n.length > MAX_FOLDER_NAME || folderNameTaken(n, 0L)) return null
        val f = ClipFolder(nextFolderId++, n, clock())
        folderList.add(f)
        db.saveFolder(f)
        changed()
        return f
    }

    fun renameFolder(id: Long, name: String): Boolean {
        val f = folderOf(id) ?: return false
        val n = name.trim()
        if (n.isEmpty() || n.length > MAX_FOLDER_NAME || n == f.name || folderNameTaken(n, id)) return false
        f.name = n
        db.saveFolder(f)
        changed()
        return true
    }

    /** Delete a folder; its clips stay in the history (they become loose clips again, so the limit applies). */
    fun deleteFolder(id: Long): Boolean {
        val f = folderOf(id) ?: return false
        val moved = ArrayList<ClipItem>()
        for (c in clips) if (c.folder == id) {
            c.folder = 0L
            moved.add(c)
        }
        if (moved.isNotEmpty()) db.saveClips(moved)
        folderList.remove(f)
        db.deleteFolder(id)
        enforce(clock())
        changed()
        return true
    }

    // ---------------------------------------------------------------- backup and import (E3.9, D71)

    /** Everything for a backup file: all clips (image clips only when [includeImages]), the folders and the shortcuts. */
    fun snapshot(includeImages: Boolean): BackupData {
        val list = ArrayList<BackupClip>(clips.size)
        for (c in clips) {
            if (c.isImage && !includeImages) continue
            list.add(BackupClip(c.kind, c.text, c.image, c.mime, c.pinned, c.folder, c.created, c.used))
        }
        return BackupData(
            clock(),
            includeImages,
            folderList.map { BackupFolder(it.id, it.name, it.created) },
            list,
            shortcutList.map { BackupShortcut(it.trigger, it.text) },
        )
    }

    /**
     * Merge a backup into what is here, never replacing: a text that is saved already is skipped, so is an image that is saved already; a folder is matched by
     * its name (case-insensitive) or made new; a shortcut whose trigger exists is skipped (the existing one wins). Pins, folders and the original times of the
     * clips are kept. [imageFiles] maps the image names of the backup to the names of the files that were saved here (the file name is the SHA-256 of the
     * picture, so normally the same); an image clip without an entry is skipped. The limit and auto-delete apply afterwards, so clips over the limit
     * are reported in [ImportResult.trimmed].
     */
    fun importBackup(b: BackupData, imageFiles: Map<String, String>): ImportResult {
        val now = clock()
        val folderMap = HashMap<Long, Long>()
        var foldersAdded = 0
        for (f in b.folders) {
            val name = f.name.trim()
            if (name.isEmpty() || name.length > MAX_FOLDER_NAME) continue
            val same = folderList.firstOrNull { it.name.equals(name, ignoreCase = true) }
            if (same != null) {
                folderMap[f.id] = same.id
                continue
            }
            val made = ClipFolder(nextFolderId++, name, minOf(maxOf(f.created, 0L), now))
            folderList.add(made)
            db.saveFolder(made)
            folderMap[f.id] = made.id
            foldersAdded++
        }
        val added = ArrayList<ClipItem>()
        var skipped = 0
        for (c in b.clips) {
            val used = minOf(maxOf(c.used, 0L), now).let { if (it == 0L) now else it }
            val created = minOf(maxOf(c.created, 0L), used).let { if (it == 0L) used else it }
            val folder = if (c.folder == 0L) 0L else folderMap[c.folder] ?: 0L
            if (c.kind == ClipKind.IMAGE) {
                val file = c.image?.let { imageFiles[it] }
                if (file == null || indexOfImage(file) >= 0) {
                    skipped++
                    continue
                }
                val item = ClipItem(nextClipId++, ClipKind.IMAGE, "", file, c.mime ?: "image/*", c.pinned, folder, created, used)
                clips.add(item)
                added.add(item)
            } else {
                if (c.text.isBlank() || c.text.length > MAX_TEXT || indexOfText(c.text) >= 0) {
                    skipped++
                    continue
                }
                val item = ClipItem(nextClipId++, ClipKind.TEXT, c.text, null, null, c.pinned, folder, created, used)
                clips.add(item)
                added.add(item)
            }
        }
        clips.sortWith(compareByDescending<ClipItem> { it.used }.thenByDescending { it.id })
        if (added.isNotEmpty()) db.saveClips(added)
        var shortcutsAdded = 0
        var shortcutsSkipped = 0
        for (s in b.shortcuts) {
            if (checkShortcut(s.trigger, s.text) != SHORTCUT_OK) {
                shortcutsSkipped++
                continue
            }
            val made = ClipShortcut(nextShortcutId++, s.trigger.trim(), s.text)
            shortcutList.add(made)
            db.saveShortcut(made)
            shortcutsAdded++
        }
        enforce(now)
        val kept = HashSet<Long>()
        for (c in clips) kept.add(c.id)
        var trimmed = 0
        for (c in added) if (c.id !in kept) trimmed++
        changed()
        return ImportResult(added.size - trimmed, skipped, trimmed, foldersAdded, shortcutsAdded, shortcutsSkipped)
    }

    // ---------------------------------------------------------------- text shortcuts

    /** [SHORTCUT_OK] or the reason a shortcut cannot be saved. [exceptId] = the shortcut being edited (0 for a new one). */
    fun checkShortcut(trigger: String, text: String, exceptId: Long = 0L): Int {
        val t = trigger.trim()
        if (t.isEmpty()) return SHORTCUT_EMPTY
        if (t.any { it.isWhitespace() }) return SHORTCUT_HAS_SPACE
        if (t.length > MAX_TRIGGER) return SHORTCUT_TOO_LONG
        if (text.isBlank() || text.length > MAX_TEXT) return SHORTCUT_NO_TEXT
        for (s in shortcutList) if (s.id != exceptId && s.trigger.equals(t, ignoreCase = true)) return SHORTCUT_DUPLICATE
        if (exceptId == 0L && shortcutList.size >= MAX_SHORTCUTS) return SHORTCUT_TOO_MANY
        return SHORTCUT_OK
    }

    fun addShortcut(trigger: String, text: String): ClipShortcut? {
        if (checkShortcut(trigger, text) != SHORTCUT_OK) return null
        val s = ClipShortcut(nextShortcutId++, trigger.trim(), text)
        shortcutList.add(s)
        db.saveShortcut(s)
        changed()
        return s
    }

    fun updateShortcut(id: Long, trigger: String, text: String): Boolean {
        val s = shortcutList.firstOrNull { it.id == id } ?: return false
        if (checkShortcut(trigger, text, id) != SHORTCUT_OK) return false
        s.trigger = trigger.trim()
        s.text = text
        db.saveShortcut(s)
        changed()
        return true
    }

    fun deleteShortcut(id: Long): Boolean {
        val s = shortcutList.firstOrNull { it.id == id } ?: return false
        shortcutList.remove(s)
        db.deleteShortcut(id)
        changed()
        return true
    }

    /**
     * Shortcuts whose trigger starts with what was typed (case-insensitive), the exact match first, then shorter triggers first.
     * The strip asks this while a word is typed (D65: a tap on the suggestion inserts the text; nothing is replaced on its own).
     */
    fun matchShortcuts(typed: String, limit: Int): List<ClipShortcut> {
        if (typed.isEmpty() || shortcutList.isEmpty()) return emptyList()
        val out = ArrayList<ClipShortcut>()
        for (s in shortcutList) if (s.trigger.startsWith(typed, ignoreCase = true)) out.add(s)
        if (out.size > 1) out.sortWith(compareBy<ClipShortcut> { !it.trigger.equals(typed, ignoreCase = true) }.thenBy { it.trigger.length }.thenBy { it.id })
        return if (out.size > limit) out.subList(0, limit) else out
    }

    /** The shortcut whose trigger is exactly [typed] (case-insensitive), for the direct insertion on Space (D77). */
    fun shortcutFor(typed: String): ClipShortcut? {
        if (typed.isEmpty()) return null
        for (s in shortcutList) if (s.trigger.equals(typed, ignoreCase = true)) return s
        return null
    }

    companion object {
        const val ANY_FOLDER = -1L
        const val MAX_TEXT = 100_000
        const val MAX_FOLDER_NAME = 40
        const val MAX_TRIGGER = 32
        const val MAX_SHORTCUTS = 500

        const val SHORTCUT_OK = 0
        const val SHORTCUT_EMPTY = 1
        const val SHORTCUT_HAS_SPACE = 2
        const val SHORTCUT_TOO_LONG = 3
        const val SHORTCUT_NO_TEXT = 4
        const val SHORTCUT_DUPLICATE = 5
        const val SHORTCUT_TOO_MANY = 6
    }
}
