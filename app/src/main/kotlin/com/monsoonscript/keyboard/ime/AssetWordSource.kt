package com.monsoonscript.keyboard.ime

import android.content.res.AssetManager
import com.monsoonscript.keyboard.lang.avro.AvroWordSource
import java.io.IOException

/**
 * Dictionary buckets read from `assets/avro/dict/<bucket>.txt` (one word per line), keeping the [capacity] most recently
 * used ones in memory so typing a few words in a row does not re-read files. Used from the suggestion worker thread only.
 */
class AssetWordSource(private val assets: AssetManager, private val capacity: Int = 12) : AvroWordSource {
    // One first letter needs up to 7 buckets (the letter "a"), so the capacity must stay above that or every keypress would reload them.

    private val cache = object : LinkedHashMap<String, Array<String>>(16, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Array<String>>?): Boolean = size > capacity
    }

    override fun load(bucket: String): Array<String> {
        cache[bucket]?.let { return it }
        val words = read(bucket)
        cache[bucket] = words
        return words
    }

    private fun read(bucket: String): Array<String> = try {
        assets.open("avro/dict/$bucket.txt").bufferedReader(Charsets.UTF_8).use { r -> r.readLines().toTypedArray() }
    } catch (e: IOException) {
        emptyArray() // a missing bucket just means no suggestions from it
    }
}
