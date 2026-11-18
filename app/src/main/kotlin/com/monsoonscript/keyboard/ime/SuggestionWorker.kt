package com.monsoonscript.keyboard.ime

import android.content.res.AssetManager
import android.os.Handler
import android.os.HandlerThread
import android.os.Looper
import com.monsoonscript.keyboard.lang.avro.AvroSuffixes
import com.monsoonscript.keyboard.lang.avro.AvroSuggester
import java.io.IOException
import java.util.concurrent.atomic.AtomicReference

/**
 * Runs the Avro dictionary search off the main thread. Requests are coalesced: while a search is running, only the
 * newest request is kept, so fast typing never builds a queue. The answer is delivered on the main thread; the
 * caller must compare [Result.english] with what is on screen now and drop it when the word has moved on.
 *
 * Create, [request] and [stop] from the main thread only. The dictionary and suffix table load lazily on the first
 * search (on the worker thread), so opening the keyboard stays fast.
 */
class SuggestionWorker(
    private val assets: AssetManager,
    private val onResult: (Result) -> Unit,
) {
    class Result(val english: String, val words: List<String>)

    private class Request(val english: String, val plain: String)

    private val main = Handler(Looper.getMainLooper())
    private val pending = AtomicReference<Request?>(null)
    private var handler: Handler? = null
    private var thread: HandlerThread? = null

    /** Touched on the worker thread only. */
    private var suggester: AvroSuggester? = null

    /** Look up dictionary words for [english] (the typed Latin letters); [plain] is its direct conversion without autocorrect. */
    fun request(english: String, plain: String) {
        val h = handler ?: start()
        // A drain is already queued whenever something was pending, so only the first request after a drain posts one.
        if (pending.getAndSet(Request(english, plain)) == null) h.post(drain)
    }

    /** Forget a queued request (the word ended). A search already running still reports back; the caller drops it. */
    fun cancel() {
        pending.set(null)
    }

    fun stop() {
        pending.set(null)
        thread?.quitSafely()
        thread = null
        handler = null
    }

    private fun start(): Handler {
        val t = HandlerThread("monsoon-suggest").also { it.start() }
        val h = Handler(t.looper)
        thread = t
        handler = h
        return h
    }

    private val drain = Runnable {
        val r = pending.getAndSet(null) ?: return@Runnable
        val s = suggester ?: createSuggester().also { suggester = it }
        val words = try {
            s.suggest(r.english, r.plain)
        } catch (e: RuntimeException) {
            emptyList() // e.g. a regex the platform rejects: no suggestions for this word
        } catch (e: StackOverflowError) {
            emptyList() // very long alternations can overflow java.util.regex; same treatment
        }
        val result = Result(r.english, words)
        main.post { onResult(result) }
    }

    private fun createSuggester(): AvroSuggester {
        val suffixes = try {
            assets.open("avro/suffixes.tsv").use { AvroSuffixes.load(it) }
        } catch (e: IOException) {
            HashMap<String, String>() // no suffix forms, stems still work
        }
        // Among equally close spellings the more common word comes first (frequency from the Bangla prediction pack, its own mapping for this thread).
        val bn = PackLoader.load(assets, "bn")
        val rank: ((String) -> Int)? = if (bn == null) null else { w ->
            val i = bn.indexOf(w)
            if (i < 0) 0 else bn.countLevel(i) + 1
        }
        return AvroSuggester(AssetWordSource(assets), suffixes, rank)
    }
}
