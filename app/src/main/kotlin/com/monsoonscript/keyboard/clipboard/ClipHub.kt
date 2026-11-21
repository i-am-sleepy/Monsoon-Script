package com.monsoonscript.keyboard.clipboard

import android.content.Context
import android.content.SharedPreferences
import android.os.Handler
import android.os.Looper

/**
 * The one clipboard store of the app process (E3, D65). The keyboard service, its panel and the app's clipboard screens all run in this process, so they
 * share the same in-memory [store] and see each other's changes at once through its listeners.
 *
 * Main thread only (like [ClipStore]); the database is read on its own thread by [start] and handed over when ready. Clips copied while it is loading wait in
 * a short queue.
 */
class ClipHub private constructor(app: Context) {
    val prefs = ClipPrefs(app)
    private val db = SqliteClipDb(app)
    val store = ClipStore(db)
    private val main = Handler(Looper.getMainLooper())
    private var started = false
    private val pending = ArrayList<String>()

    /** Held as a field: SharedPreferences keeps its listeners weakly. */
    private val prefsListener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ -> applySettings() }

    init {
        applySettings()
        prefs.sp.registerOnSharedPreferenceChangeListener(prefsListener)
    }

    /** Read the saved clips in the background (once). */
    fun start() {
        if (started) return
        started = true
        db.post {
            val clips = db.loadClips()
            val folders = db.loadFolders()
            val shortcuts = db.loadShortcuts()
            main.post {
                store.install(clips, folders, shortcuts)
                for (t in pending) store.addText(t)
                pending.clear()
            }
        }
    }

    /** Copy the history rules from the settings into the store. */
    fun applySettings() {
        store.maxItems = prefs.maxItems
        store.autoDeleteMs = prefs.autoDeleteMs
        store.keepPinned = prefs.keepPinned
        store.dedupe = prefs.dedupe
        store.pinnedOnTop = prefs.pinnedOnTop
        if (store.loaded) store.purge()
    }

    /** A text was copied (called by [ClipCapture] after its checks). */
    fun record(text: String) {
        if (!started) start()
        if (store.loaded) {
            store.addText(text)
        } else if (pending.size < MAX_PENDING) {
            pending.add(text)
        }
    }

    companion object {
        private const val MAX_PENDING = 10

        @Volatile
        private var instance: ClipHub? = null

        fun get(context: Context): ClipHub =
            instance ?: synchronized(this) {
                instance ?: ClipHub(context.applicationContext).also { instance = it }
            }
    }
}
