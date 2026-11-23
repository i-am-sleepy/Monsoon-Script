package com.monsoonscript.keyboard.clipboard

import android.content.Context
import android.content.BroadcastReceiver
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
import android.net.Uri
import android.os.Handler
import android.os.Build
import android.os.Looper
import android.os.SystemClock
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

    /** Is the clipboard unlocked right now (E3.9, D71)? The screen turning off locks it. */
    val lock = LockState(SystemClock::elapsedRealtime)

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
        val screenOff = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                lock.lockNow()
            }
        }
        val filter = IntentFilter(Intent.ACTION_SCREEN_OFF)
        if (Build.VERSION.SDK_INT >= 33) app.registerReceiver(screenOff, filter, Context.RECEIVER_NOT_EXPORTED) else app.registerReceiver(screenOff, filter)
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
                maybeAutoBackup()
            }
        }
    }

    /**
     * The keyboard and the screens ask the lock through [lockOpen] / [lockOn]: the switch and the timer are read from the settings every time, so a change made in the
     * app counts at once and no stale copy can keep asking for the fingerprint (E3.12).
     */
    fun lockOpen(): Boolean {
        syncLock()
        return lock.isOpen()
    }

    fun lockOn(): Boolean {
        syncLock()
        return lock.enabled
    }

    private fun syncLock() {
        lock.enabled = prefs.lockEnabled
        lock.timeoutMs = prefs.lockTimeoutMs
    }

    /** The unlock prompt of the keyboard's clipboard key is over: true = unlocked, false = cancelled or failed. The keyboard listens (E3.12). */
    var onUnlockFinished: ((Boolean) -> Unit)? = null

    /** The keyboard reads these on every key, so they are copied here when a setting changes (D77). */
    @Volatile var directShortcuts = false
        private set
    @Volatile var recentChip = true
        private set

    /** Copy the history rules from the settings into the store. */
    fun applySettings() {
        directShortcuts = prefs.directShortcuts
        recentChip = prefs.recentChip
        store.maxItems = prefs.maxItems
        store.autoDeleteMs = prefs.autoDeleteMs
        store.keepPinned = prefs.keepPinned
        store.dedupe = prefs.dedupe
        store.pinnedOnTop = prefs.pinnedOnTop
        lock.timeoutMs = prefs.lockTimeoutMs
        lock.enabled = prefs.lockEnabled
        if (store.loaded) {
            store.purge()
            trimImages()
            maybeAutoBackup()
        }
    }

    /** Keep the saved pictures within the size the owner chose. */
    private fun trimImages() {
        store.trimImages(prefs.imageCapBytes, images::sizeOf)
    }

    /** The latest copied text for the strip chip (D77), and who wants to know when a new one arrives (the keyboard). */
    val recent = RecentCopy(SystemClock::elapsedRealtime)
    var onCopied: (() -> Unit)? = null

    /** A text was copied (called by [ClipCapture] after its checks). */
    fun record(text: String) {
        if (!started) start()
        recent.note(text)
        onCopied?.invoke()
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

    /** What an import did: [error] is [ClipBackup.OK] or a reason ([ClipBackup.NOT_A_BACKUP] ... or [ERROR_LOADING]); [result] only when it worked. */
    class ImportOutcome(val error: Int, val result: ImportResult?)

    private var autoBackupRunning = false

    /** Write a backup file to [uri] (the owner picked it with the system "save as" screen). [done] runs on the main thread with true when it was written. */
    fun exportBackup(uri: Uri, includeImages: Boolean, done: (Boolean) -> Unit) {
        if (!store.loaded) {
            done(false)
            return
        }
        val data = store.snapshot(includeImages)
        val resolver = app.contentResolver
        io.execute {
            val ok = try {
                resolver.openOutputStream(uri)?.use { BackupIo.write(it, data, images) } != null
            } catch (e: Exception) {
                false
            }
            main.post { done(ok) }
        }
    }

    /** Merge the backup at [uri] into the history ([ClipStore.importBackup]). [done] runs on the main thread. */
    fun importBackup(uri: Uri, done: (ImportOutcome) -> Unit) {
        if (!store.loaded) {
            done(ImportOutcome(ERROR_LOADING, null))
            return
        }
        val resolver = app.contentResolver
        io.execute {
            val read = try {
                resolver.openInputStream(uri)?.use { BackupIo.read(it, images) }
            } catch (e: Exception) {
                null
            }
            main.post {
                val data = read?.decoded?.data
                if (read == null || data == null) {
                    done(ImportOutcome(read?.decoded?.error ?: ClipBackup.DAMAGED, null))
                    return@post
                }
                val result = store.importBackup(data, read.imageFiles)
                trimImages()
                val keep = store.imageNames()
                val unused = read.fresh.filter { it !in keep } // pictures that came with the file but no clip uses
                if (unused.isNotEmpty()) io.execute { images.delete(unused) }
                done(ImportOutcome(ClipBackup.OK, result))
            }
        }
    }

    /**
     * The automatic backup (D71): when it is switched on, a folder is chosen and the last one is older than a day / a week (and at least an hour since the last
     * try), write one into the folder on the worker thread. No timers or services: this runs when the clipboard loads and when settings change.
     */
    fun maybeAutoBackup() {
        if (autoBackupRunning || !store.loaded) return
        val mode = prefs.autoBackup
        val folder = prefs.backupFolder
        if (mode == ClipPrefs.BACKUP_OFF || folder.isEmpty()) return
        if (store.size == 0 && store.folders.isEmpty() && store.shortcuts.isEmpty()) return
        val now = System.currentTimeMillis()
        val period = if (mode == ClipPrefs.BACKUP_DAILY) DAY_MS else 7 * DAY_MS
        if (now - prefs.lastBackup < period || now - prefs.lastBackupTry < HOUR_MS) return
        autoBackupRunning = true // before the preferences below change: that calls applySettings again
        prefs.lastBackupTry = now
        val withImages = prefs.backupImages
        val data = store.snapshot(withImages)
        io.execute {
            val ok = try {
                AutoBackup.write(app, Uri.parse(folder), data, if (withImages) images else null, now)
            } catch (e: Exception) {
                false
            }
            main.post {
                autoBackupRunning = false
                if (ok) prefs.lastBackup = now
                prefs.backupFailed = !ok
            }
        }
    }

    /** Number and disk space of the saved pictures (settings summary). Main thread, a few file lookups. */
    fun imageStats(): Pair<Int, Long> = Pair(store.imageCount(), store.imageBytes(images::sizeOf))

    companion object {
        const val ERROR_LOADING = 100
        private const val HOUR_MS = 3_600_000L
        private const val DAY_MS = 24 * HOUR_MS
        private const val MAX_PENDING = 10

        @Volatile
        private var instance: ClipHub? = null

        fun get(context: Context): ClipHub =
            instance ?: synchronized(this) {
                instance ?: ClipHub(context.applicationContext).also { instance = it }
            }
    }
}
