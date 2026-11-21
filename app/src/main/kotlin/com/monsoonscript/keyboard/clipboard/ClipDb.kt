package com.monsoonscript.keyboard.clipboard

/**
 * Where the clipboard store keeps its data. The store keeps everything in memory (a few hundred clips) and only writes changes through this interface.
 * The Android implementation (SqliteClipDb) copies the values at call time and writes on its own thread; tests use a fake that records the calls.
 * Local only: nothing here ever leaves the phone.
 */
interface ClipDb {
    fun loadClips(): List<ClipItem>
    fun loadFolders(): List<ClipFolder>
    fun loadShortcuts(): List<ClipShortcut>

    /** Insert or replace by id. */
    fun saveClips(items: List<ClipItem>)
    fun deleteClips(ids: LongArray)
    fun saveFolder(folder: ClipFolder)
    fun deleteFolder(id: Long)
    fun saveShortcut(shortcut: ClipShortcut)
    fun deleteShortcut(id: Long)
}
