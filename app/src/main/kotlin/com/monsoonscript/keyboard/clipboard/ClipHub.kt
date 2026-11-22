package com.monsoonscript.keyboard.clipboard

import android.content.Context
import android.content.SharedPreferences
import android.net.Uri
import android.os.Handler
import android.os.Looper
import java.util.concurrent.Executors

/**
 * The one clipboard store of the app process (E3, D65). The keyboard service, its panel and the app's clipboard screens all run in this process, so they
 * share the same in-memory [store] and see each other's changes at once through its listeners.
 *
 * Main thread only (like [ClipStore]); the database is read on its own thread by [start] and handed over when ready. Clips copied while it is loading wait in
 * a short queue.
 */
class ClipHub private constructor(private val app: Context) {
    val prefs = ClipPrefs(app)
    private val db = SqliteClipDb(app)
    val store = ClipStore(db)
    private val main = Handler(Looper.getMainLooper())
    private var started = false
    private val pending = ArrayList<String>()
    private val pendingImages = ArrayList<ImageFiles.Imported>()

    /** One worker thread for picture files (copy, thumbnails, delete), kept apart from the database thread. */
    private val io = Executors.newSingleThreadExecutor { r ->
        Thread(r, "clip-images").apply {
            isDaemon = true
            priority = Thread.MIN_PRIORITY
        }
    }

    /** The private picture folder, and the thumbnails of the pictures in it (E3.8). */
    val images = ImageFiles(app)
    val thumbs = ThumbCache(images, io, main)

    /** Held as a field: SharedPreferences keeps its listeners weakly. */
    private val prefsListener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ -> applySettings() }

    init {
        store.imageCleaner = { names ->
            thumbs.forget(names)
            io.execute { images.delete(names) }
        }
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
                for (g in pendingImages) store.addImage(g.name, g.mime)
                pendingImages.clear()
                trimImages()
                val known = store.imageNames()
                io.execute { images.sweep(known) }
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
        if (store.loaded) {
            store.purge()
            trimImages()
        }
    }

    /** Keep the saved pictures within the size the owner chose. */
    private fun trimImages() {
        store.trimImages(prefs.imageCapBytes, images::sizeOf)
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

    /** A picture was copied (called by [ClipCapture] after its checks): copy it into the private folder in the background, then add the clip. */
    fun recordImage(uri: Uri) {
        if (!started) start()
        val resolver = app.contentResolver
        io.execute {
            val got = images.import(resolver, uri) ?: return@execute
            main.post { addImported(got) }
        }
    }

    private fun addImported(got: ImageFiles.Imported) {
        if (!prefs.historyEnabled || !prefs.saveImages) { // switched off while it was being copied
            discard(got)
            return
        }
        if (!store.loaded) {
            if (pendingImages.size < MAX_PENDING) pendingImages.add(got) else discard(got)
            return
        }
        if (!images.file(got.name).isFile) return // deleted again in the meantime
        store.addImage(got.name, got.mime)
        trimImages()
    }

    private fun discard(got: ImageFiles.Imported) {
        if (got.fresh) io.execute { images.delete(listOf(got.name)) }
    }

    /** Number and disk space of the saved pictures (settings summary). Main thread, a few file lookups. */
    fun imageStats(): Pair<Int, Long> = Pair(store.imageCount(), store.imageBytes(images::sizeOf))

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
