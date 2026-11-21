package com.monsoonscript.keyboard.clipboard

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.util.Log
import java.util.concurrent.Executors

/**
 * The clipboard database (E3, D65): SQLite from the Android framework, one file `clipboard.db` in the app's private folder, nothing leaves the phone.
 * Writes are copied on the calling thread and executed by one background thread, so typing and the panel never wait for the disk. Reads (start-up load)
 * must go through [post] so they run on that same thread, after the writes queued before them.
 * A storage error is logged and swallowed: the keyboard must never crash because of the history.
 */
class SqliteClipDb(context: Context) : ClipDb {
    private val helper = Helper(context.applicationContext)
    private val io = Executors.newSingleThreadExecutor { r -> Thread(r, "monsoon-clip-db").apply { isDaemon = true } }

    /** Run [r] on the database thread. */
    fun post(r: Runnable) {
        io.execute(r)
    }

    override fun loadClips(): List<ClipItem> {
        val out = ArrayList<ClipItem>()
        try {
            helper.readableDatabase.query("clips", null, null, null, null, null, "used DESC, id DESC").use { c ->
                val id = c.getColumnIndexOrThrow("id")
                val kind = c.getColumnIndexOrThrow("kind")
                val text = c.getColumnIndexOrThrow("text")
                val image = c.getColumnIndexOrThrow("image")
                val mime = c.getColumnIndexOrThrow("mime")
                val pinned = c.getColumnIndexOrThrow("pinned")
                val folder = c.getColumnIndexOrThrow("folder")
                val created = c.getColumnIndexOrThrow("created")
                val used = c.getColumnIndexOrThrow("used")
                while (c.moveToNext()) {
                    out.add(
                        ClipItem(
                            c.getLong(id), c.getInt(kind), c.getString(text) ?: "",
                            if (c.isNull(image)) null else c.getString(image),
                            if (c.isNull(mime)) null else c.getString(mime),
                            c.getInt(pinned) != 0, c.getLong(folder), c.getLong(created), c.getLong(used),
                        ),
                    )
                }
            }
        } catch (e: RuntimeException) {
            Log.w(TAG, "load clips failed", e)
        }
        return out
    }

    override fun loadFolders(): List<ClipFolder> {
        val out = ArrayList<ClipFolder>()
        try {
            helper.readableDatabase.query("folders", null, null, null, null, null, "id").use { c ->
                val id = c.getColumnIndexOrThrow("id")
                val name = c.getColumnIndexOrThrow("name")
                val created = c.getColumnIndexOrThrow("created")
                while (c.moveToNext()) out.add(ClipFolder(c.getLong(id), c.getString(name) ?: "", c.getLong(created)))
            }
        } catch (e: RuntimeException) {
            Log.w(TAG, "load folders failed", e)
        }
        return out
    }

    override fun loadShortcuts(): List<ClipShortcut> {
        val out = ArrayList<ClipShortcut>()
        try {
            helper.readableDatabase.query("shortcuts", null, null, null, null, null, "id").use { c ->
                val id = c.getColumnIndexOrThrow("id")
                val trig = c.getColumnIndexOrThrow("trig")
                val text = c.getColumnIndexOrThrow("text")
                while (c.moveToNext()) out.add(ClipShortcut(c.getLong(id), c.getString(trig) ?: "", c.getString(text) ?: ""))
            }
        } catch (e: RuntimeException) {
            Log.w(TAG, "load shortcuts failed", e)
        }
        return out
    }

    override fun saveClips(items: List<ClipItem>) {
        if (items.isEmpty()) return
        val rows = ArrayList<ContentValues>(items.size)
        for (c in items) {
            rows.add(
                ContentValues(9).apply {
                    put("id", c.id)
                    put("kind", c.kind)
                    put("text", c.text)
                    put("image", c.image)
                    put("mime", c.mime)
                    put("pinned", if (c.pinned) 1 else 0)
                    put("folder", c.folder)
                    put("created", c.created)
                    put("used", c.used)
                },
            )
        }
        write { db -> for (v in rows) db.insertWithOnConflict("clips", null, v, SQLiteDatabase.CONFLICT_REPLACE) }
    }

    override fun deleteClips(ids: LongArray) {
        if (ids.isEmpty()) return
        val copy = ids.copyOf()
        write { db ->
            var i = 0
            while (i < copy.size) { // ids are numbers we made ourselves: safe to write into the statement, in chunks
                val end = minOf(i + 500, copy.size)
                val sb = StringBuilder("DELETE FROM clips WHERE id IN (")
                for (j in i until end) {
                    if (j > i) sb.append(',')
                    sb.append(copy[j])
                }
                db.execSQL(sb.append(')').toString())
                i = end
            }
        }
    }

    override fun saveFolder(folder: ClipFolder) {
        val v = ContentValues(3).apply {
            put("id", folder.id)
            put("name", folder.name)
            put("created", folder.created)
        }
        write { db -> db.insertWithOnConflict("folders", null, v, SQLiteDatabase.CONFLICT_REPLACE) }
    }

    override fun deleteFolder(id: Long) {
        write { db -> db.execSQL("DELETE FROM folders WHERE id = $id") }
    }

    override fun saveShortcut(shortcut: ClipShortcut) {
        val v = ContentValues(3).apply {
            put("id", shortcut.id)
            put("trig", shortcut.trigger)
            put("text", shortcut.text)
        }
        write { db -> db.insertWithOnConflict("shortcuts", null, v, SQLiteDatabase.CONFLICT_REPLACE) }
    }

    override fun deleteShortcut(id: Long) {
        write { db -> db.execSQL("DELETE FROM shortcuts WHERE id = $id") }
    }

    private fun write(body: (SQLiteDatabase) -> Unit) {
        io.execute {
            try {
                val db = helper.writableDatabase
                db.beginTransaction()
                try {
                    body(db)
                    db.setTransactionSuccessful()
                } finally {
                    db.endTransaction()
                }
            } catch (e: RuntimeException) {
                Log.w(TAG, "clipboard write failed", e)
            }
        }
    }

    private class Helper(context: Context) : SQLiteOpenHelper(context, "clipboard.db", null, 1) {
        init {
            setWriteAheadLoggingEnabled(true)
        }

        override fun onCreate(db: SQLiteDatabase) {
            db.execSQL(
                "CREATE TABLE clips (id INTEGER PRIMARY KEY, kind INTEGER NOT NULL, text TEXT NOT NULL, image TEXT, mime TEXT, " +
                    "pinned INTEGER NOT NULL DEFAULT 0, folder INTEGER NOT NULL DEFAULT 0, created INTEGER NOT NULL, used INTEGER NOT NULL)",
            )
            db.execSQL("CREATE INDEX clips_used ON clips(used)")
            db.execSQL("CREATE TABLE folders (id INTEGER PRIMARY KEY, name TEXT NOT NULL, created INTEGER NOT NULL)")
            db.execSQL("CREATE TABLE shortcuts (id INTEGER PRIMARY KEY, trig TEXT NOT NULL, text TEXT NOT NULL)")
        }

        override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
    }

    private companion object {
        const val TAG = "MonsoonClip"
    }
}
