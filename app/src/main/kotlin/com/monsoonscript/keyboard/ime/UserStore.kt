package com.monsoonscript.keyboard.ime

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteException
import android.database.sqlite.SQLiteOpenHelper
import com.monsoonscript.keyboard.predict.UserStats

/**
 * The learned words and word pairs on disk (a few thousand small rows at most; see [UserStats] for how they are kept and pruned).
 * Private to the app, never leaves the phone. All calls come from the prediction worker thread. A database problem must never
 * break typing, so every call swallows [SQLiteException]: the keyboard then just learns nothing this session.
 */
internal class UserStore(context: Context) : SQLiteOpenHelper(context.applicationContext, NAME, null, VERSION) {

    override fun onCreate(db: SQLiteDatabase) {
        // w2 is empty for a word row; n is the faded use count, last the time of the last use (ms since 1970).
        db.execSQL("CREATE TABLE facts (w1 TEXT NOT NULL, w2 TEXT NOT NULL, n REAL NOT NULL, last INTEGER NOT NULL, PRIMARY KEY (w1, w2)) WITHOUT ROWID")
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit

    fun loadInto(stats: UserStats) {
        try {
            readableDatabase.rawQuery("SELECT w1, w2, n, last FROM facts", null).use { c ->
                while (c.moveToNext()) stats.restore(UserStats.Row(c.getString(0), c.getString(1), c.getDouble(2), c.getLong(3)))
            }
        } catch (e: SQLiteException) {
            // unreadable: start empty
        }
    }

    /** Write the changed facts in one transaction; a zero count deletes the row. */
    fun save(rows: List<UserStats.Row>) {
        if (rows.isEmpty()) return
        try {
            val db = writableDatabase
            db.beginTransaction()
            try {
                val put = db.compileStatement("INSERT OR REPLACE INTO facts (w1, w2, n, last) VALUES (?, ?, ?, ?)")
                val del = db.compileStatement("DELETE FROM facts WHERE w1 = ? AND w2 = ?")
                for (r in rows) {
                    if (r.count > 0.0) {
                        put.bindString(1, r.w1)
                        put.bindString(2, r.w2)
                        put.bindDouble(3, r.count)
                        put.bindLong(4, r.last)
                        put.executeInsert()
                    } else {
                        del.bindString(1, r.w1)
                        del.bindString(2, r.w2)
                        del.executeUpdateDelete()
                    }
                }
                db.setTransactionSuccessful()
            } finally {
                db.endTransaction()
            }
        } catch (e: SQLiteException) {
            // disk full or similar: these facts are lost, typing goes on
        }
    }

    fun clear() {
        try {
            writableDatabase.delete("facts", null, null)
        } catch (e: SQLiteException) {
            // nothing to clear
        }
    }

    companion object {
        const val NAME = "learned.db"
        private const val VERSION = 1

        /** Delete every learned fact when no worker is running (the settings screen's "Clear learned words"). */
        fun deleteAll(context: Context) {
            val s = UserStore(context)
            try {
                s.clear()
            } finally {
                s.close()
            }
        }
    }
}
