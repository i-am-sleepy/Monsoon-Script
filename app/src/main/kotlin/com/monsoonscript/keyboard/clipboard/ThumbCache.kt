package com.monsoonscript.keyboard.clipboard

import android.graphics.Bitmap
import android.os.Handler
import android.util.LruCache
import java.util.concurrent.Executor

/** Gives views the thumbnail of an image clip (E3.8). [thumb] never waits: it answers from memory or null, and [onReady] runs on the main thread once a missing one is loaded. */
interface ThumbLoader {
    fun thumb(name: String, onReady: Runnable): Bitmap?
}

/**
 * A small memory cache of decoded thumbnails (about 4 MB, a few dozen pictures) shared by the keyboard panel and the app's list. Missing ones are decoded one by
 * one on the worker [io] thread; main thread only for [thumb] (the Handler [main] is the main looper).
 */
class ThumbCache(private val images: ImageFiles, private val io: Executor, private val main: Handler) : ThumbLoader {
    private val cache = object : LruCache<String, Bitmap>(MAX_BYTES) {
        override fun sizeOf(key: String, value: Bitmap): Int = value.byteCount
    }
    private val waiting = HashMap<String, ArrayList<Runnable>>()
    private val broken = HashSet<String>()

    override fun thumb(name: String, onReady: Runnable): Bitmap? {
        val hit = cache.get(name)
        if (hit != null) return hit
        if (name in broken) return null
        val list = waiting[name]
        if (list != null) {
            if (onReady !in list) list.add(onReady)
            return null
        }
        waiting[name] = arrayListOf(onReady)
        io.execute {
            val bmp = images.loadThumb(name)
            main.post {
                if (bmp != null) cache.put(name, bmp) else broken.add(name)
                val callbacks = waiting.remove(name)
                if (callbacks != null && bmp != null) for (r in callbacks) r.run()
            }
        }
        return null
    }

    /** A picture was deleted: forget its thumbnail. */
    fun forget(names: List<String>) {
        for (n in names) {
            cache.remove(n)
            broken.remove(n)
        }
    }

    private companion object {
        const val MAX_BYTES = 4 * 1024 * 1024
    }
}
