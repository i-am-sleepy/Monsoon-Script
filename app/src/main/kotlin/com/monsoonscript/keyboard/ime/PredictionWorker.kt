package com.monsoonscript.keyboard.ime

import android.content.Context
import android.os.Handler
import android.os.HandlerThread
import android.os.Looper
import com.monsoonscript.keyboard.predict.LangPack
import com.monsoonscript.keyboard.predict.Predictor
import com.monsoonscript.keyboard.predict.TextNorm
import com.monsoonscript.keyboard.predict.UserStats
import com.monsoonscript.keyboard.predict.WeightedPack
import java.util.concurrent.atomic.AtomicReference

/**
 * Runs word prediction (autocomplete + next word, see `predict/Predictor`) off the main thread. Like [SuggestionWorker], requests
 * are coalesced: while one runs only the newest is kept, so fast typing never builds a queue. The packs are memory-mapped lazily on
 * the first request (on the worker thread), so opening the keyboard stays fast. All pack and predictor access happens on that thread.
 *
 * The answer arrives on the main thread with the request's [Result.seq]; the caller drops it when it is not the newest sequence number
 * (the text has moved on). Create, [request], [cancel], [learn], [clearLearned] and [stop] from the main thread only.
 *
 * It also owns what the owner has typed: [learn] records a finished word, the facts are saved to the private SQLite store a few seconds
 * later (or at [flush] / [stop]) and loaded again on the first use after a restart. Predictions mix them in (see `predict/Predictor`).
 */
class PredictionWorker(
    context: Context,
    private val onResult: (Result) -> Unit,
) {
    enum class Mode { ENGLISH, BANGLA }

    /** [words] are ready to show: capitalized like what is being typed. */
    class Result(val seq: Int, val words: List<String>)

    private class Request(
        val seq: Int, val mode: Mode, val typed: String, val prefix: String,
        val w1: String?, val w2: String?, val capitalizeStart: Boolean,
    )

    private val appContext = context.applicationContext
    private val assets = appContext.assets
    private val main = Handler(Looper.getMainLooper())
    private val pending = AtomicReference<Request?>(null)
    private var handler: Handler? = null
    private var thread: HandlerThread? = null

    // Worker thread only.
    private var en: LangPack? = null
    private var bn: LangPack? = null
    private var rom: LangPack? = null
    private val stats = UserStats()
    private val predictor = Predictor(stats)
    private var store: UserStore? = null
    private var userLoaded = false
    private var flushQueued = false

    /**
     * [typed] is the word in progress exactly as in the editor (for capitals), [prefix] the same in lookup form, [w1]/[w2] the two words
     * before it (lookup form, null when unknown), [capitalizeStart] true when the next word begins a sentence.
     */
    fun request(seq: Int, mode: Mode, typed: String, prefix: String, w1: String?, w2: String?, capitalizeStart: Boolean) {
        val h = handler ?: start()
        if (pending.getAndSet(Request(seq, mode, typed, prefix, w1, w2, capitalizeStart)) == null) h.post(drain)
    }

    /** The owner finished typing [word] after [prev] (null at a sentence start). Both in `TextNorm.key` form. Saved a few seconds later. */
    fun learn(prev: String?, word: String) {
        val h = handler ?: start()
        h.post {
            ensureUser()
            stats.learn(prev, word)
            if (!flushQueued) {
                flushQueued = true
                handler?.postDelayed(flushRunnable, FLUSH_DELAY_MS)
            }
        }
    }

    /** Save what changed now (the keyboard is closing). */
    fun flush() {
        handler?.post(flushRunnable)
    }

    /** Forget everything learned, in memory and on disk. */
    fun clearLearned() {
        val h = handler ?: start()
        h.post {
            stats.clear()
            openStore()?.clear()
        }
    }

    /** Forget a queued request (the word ended or the field changed). One already running still reports back; the caller drops it by seq. */
    fun cancel() {
        pending.set(null)
    }

    fun stop() {
        pending.set(null)
        handler?.post { // quitSafely still runs what is already queued, so the last facts are saved
            flushRunnable.run()
            store?.close()
            store = null
        }
        thread?.quitSafely()
        thread = null
        handler = null
    }

    private fun start(): Handler {
        val t = HandlerThread("monsoon-predict").also { it.start() }
        val h = Handler(t.looper)
        thread = t
        handler = h
        return h
    }

    private val flushRunnable = Runnable {
        flushQueued = false
        if (userLoaded) store?.save(stats.takeChanges())
    }

    private fun openStore(): UserStore? {
        store?.let { return it }
        return try {
            UserStore(appContext).also { store = it }
        } catch (e: RuntimeException) {
            null
        }
    }

    /** Worker thread: load the saved facts before the first prediction or learning step. */
    private fun ensureUser() {
        if (userLoaded) return
        userLoaded = true
        openStore()?.loadInto(stats)
    }

    private val drain = Runnable {
        val r = pending.getAndSet(null) ?: return@Runnable
        val words = try {
            ensureUser()
            predict(r)
        } catch (e: RuntimeException) {
            emptyList() // a damaged pack must never crash the keyboard: no suggestions this time
        }
        val result = Result(r.seq, words)
        main.post { onResult(result) }
    }

    private fun predict(r: Request): List<String> {
        val packs: List<WeightedPack> = when (r.mode) {
            Mode.ENGLISH -> {
                val e = en ?: PackLoader.load(assets, "en")?.also { en = it }
                val ro = rom ?: PackLoader.load(assets, "rom")?.also { rom = it }
                when {
                    e != null && ro != null -> Predictor.englishMix(e, ro, r.w1, r.w2)
                    e != null -> listOf(WeightedPack(e))
                    else -> emptyList()
                }
            }
            Mode.BANGLA -> {
                val b = bn ?: PackLoader.load(assets, "bn")?.also { bn = it }
                if (b != null) listOf(WeightedPack(b)) else emptyList()
            }
        }
        if (packs.isEmpty()) return emptyList()
        val keys = predictor.predict(packs, r.prefix, r.w1, r.w2, RESULTS)
        return keys.map { TextNorm.matchCase(r.typed, it, r.capitalizeStart) }
    }

    private companion object {
        const val RESULTS = 3
        const val FLUSH_DELAY_MS = 8_000L
    }
}
