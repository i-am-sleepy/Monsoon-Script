package com.monsoonscript.keyboard.voice

import android.content.Context
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Handler
import android.os.Looper
import com.monsoonscript.keyboard.view.Waveform

/**
 * Method 2 of the voice typing design (G5, D66): records the microphone itself and transcribes it on the phone with a downloaded model (transcribe.cpp).
 * The models are not streaming: while the key is held the audio is only collected, and [stop] turns it into text (a second or two for a long sentence).
 * The model stays loaded for a minute after use so the next dictation starts at once, then it is freed. All calls on the main thread.
 */
class LocalVoiceEngine(private val context: Context, private val model: VoiceModel) : VoiceEngine {

    private val main = Handler(Looper.getMainLooper())
    private var session = 0

    @Volatile private var recording = false

    override fun start(lang: VoiceLang, callback: VoiceEngine.Callback, autoStop: Boolean) {
        cancel()
        if (!NativeVoice.available) {
            callback.onError(VoiceError.NOT_AVAILABLE)
            return
        }
        if (!VoiceModels.installed(context, model)) {
            callback.onError(VoiceError.NO_MODEL)
            return
        }
        main.removeCallbacks(unload)
        val mine = ++session
        recording = true
        preload()
        Thread({ record(mine, callback, autoStop) }, "voice-record").start()
    }

    /** Ends the recording; the text follows through the callback. */
    override fun stop() {
        recording = false
    }

    override fun cancel() {
        session++
        recording = false
    }

    /** Frees the model now (the keyboard is closing). */
    fun release() {
        cancel()
        main.removeCallbacks(unload)
        synchronized(LOCK) { freeLocked() }
    }

    private fun preload() {
        Thread({ synchronized(LOCK) { ensureLoadedLocked() } }, "voice-load").start()
    }

    private fun record(mine: Int, callback: VoiceEngine.Callback, autoStop: Boolean) {
        val minBuf = AudioRecord.getMinBufferSize(RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
        val bufBytes = maxOf(minBuf, RATE / 4 * 2)
        var rec: AudioRecord? = null
        for (source in intArrayOf(MediaRecorder.AudioSource.VOICE_RECOGNITION, MediaRecorder.AudioSource.MIC)) {
            val r = try {
                AudioRecord(source, RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, bufBytes)
            } catch (e: Exception) {
                null
            }
            if (r != null && r.state == AudioRecord.STATE_INITIALIZED) {
                rec = r
                break
            }
            r?.release()
        }
        if (rec == null) {
            post(mine) { callback.onError(VoiceError.NO_PERMISSION) }
            return
        }
        val pcm = FloatArray(model.maxSeconds * RATE)
        var n = 0
        try {
            rec.startRecording()
            post(mine) { callback.onListening() }
            val chunk = ShortArray(RATE / 20) // 50 ms: the level display and the pause detection work on these
            var floorDb = 0f
            var first = true
            var spoke = false
            var quiet = 0 // chunks of silence since the speech
            var waited = 0 // chunks before any speech
            while (recording && n < pcm.size && session == mine) {
                val got = rec.read(chunk, 0, minOf(chunk.size, pcm.size - n))
                if (got <= 0) break
                var sum = 0f
                for (i in 0 until got) {
                    val v = chunk[i] / 32768f
                    pcm[n + i] = v
                    sum += v * v
                }
                n += got
                val rms = kotlin.math.sqrt(sum / got)
                val level = Waveform.levelOf(rms)
                post(mine) { callback.onLevel(level) }
                if (autoStop) { // stop by itself after a pause (the tap panel)
                    val db = if (rms > 1e-5f) 20f * kotlin.math.log10(rms) else -100f
                    if (first || db < floorDb) floorDb = db else floorDb += 0.05f // the noise floor follows the quietest recent level
                    first = false
                    if (db > maxOf(-48f, floorDb + 12f)) {
                        spoke = true
                        quiet = 0
                    } else if (spoke) {
                        quiet++
                    } else {
                        waited++
                    }
                    if ((spoke && quiet >= PAUSE_CHUNKS) || (!spoke && waited >= WAIT_CHUNKS)) recording = false
                }
            }
        } catch (e: Exception) {
            post(mine) { callback.onError(VoiceError.OTHER) }
            return
        } finally {
            try {
                rec.stop()
            } catch (e: Exception) {
            }
            rec.release()
        }
        if (session != mine) return // cancelled
        if (n < MIN_SAMPLES) {
            post(mine) { callback.onFinal("") }
            return
        }
        var text: String? = null
        var noSpeech = false
        synchronized(LOCK) {
            val h = ensureLoadedLocked()
            if (h != 0L) {
                val speech = speechOnlyLocked(pcm, n) // music, noise and silence never reach the speech model
                if (speech == null) {
                    noSpeech = true
                } else {
                    text = NativeVoice.run(h, speech.first, speech.second)?.toString(Charsets.UTF_8)
                }
            }
        }
        if (noSpeech) {
            post(mine) { callback.onFinal("") }
            return
        }
        if (session != mine) return
        main.post { scheduleUnload() }
        if (text == null) post(mine) { callback.onError(VoiceError.OTHER) } else post(mine) { callback.onFinal(text.trim()) }
    }

    private fun post(mine: Int, r: () -> Unit) {
        main.post { if (session == mine) r() }
    }

    private fun scheduleUnload() {
        main.removeCallbacks(unload)
        main.postDelayed(unload, IDLE_MS)
    }

    private val unload = Runnable {
        if (!recording) synchronized(LOCK) { freeLocked() }
    }

    /**
     * Keeps only the speech of a recording (Silero VAD): the pieces it finds, each with 0.04 s around it, joined. Null = there was no speech. Tight is the point:
     * the Bangla model writes "music" for leading silence (CI experiment: 33 of 80 clips as recorded, 6 of 80 cropped tight, more with extra silence).
     * Without the detector installed (it downloads by itself, 1 MB) the silence before the first loud moment is cropped by energy instead.
     */
    private fun speechOnlyLocked(pcm: FloatArray, n: Int): Pair<FloatArray, Int>? {
        val h = ensureVadLocked()
        if (h == 0L) return cropLeadingSilence(pcm, n)
        val segs = NativeVoice.vadSegments(h, pcm, n, VAD_THRESHOLD) ?: return Pair(pcm, n)
        if (segs.isEmpty()) return null
        val pad = RATE * 4 / 100
        val ranges = ArrayList<IntArray>()
        var i = 0
        while (i + 1 < segs.size) {
            val s = maxOf(0, segs[i] - pad)
            val e = minOf(n, segs[i + 1] + pad)
            if (ranges.isNotEmpty() && s <= ranges.last()[1]) ranges.last()[1] = maxOf(ranges.last()[1], e) else ranges.add(intArrayOf(s, e))
            i += 2
        }
        var total = 0
        for (r in ranges) total += r[1] - r[0]
        if (total <= 0) return null
        val out = FloatArray(total)
        var o = 0
        for (r in ranges) {
            System.arraycopy(pcm, r[0], out, o, r[1] - r[0])
            o += r[1] - r[0]
        }
        return Pair(out, total)
    }

    /** The fallback without the detector: drop the quiet start of the recording (20 ms frames, louder than 4x the quietest tenth and -45 dB), keep 0.05 s of it. */
    private fun cropLeadingSilence(pcm: FloatArray, n: Int): Pair<FloatArray, Int> {
        val fr = 320
        val frames = n / fr
        if (frames < 4) return Pair(pcm, n)
        val e = FloatArray(frames)
        for (f in 0 until frames) {
            var s = 0f
            for (i in f * fr until (f + 1) * fr) s += pcm[i] * pcm[i]
            e[f] = kotlin.math.sqrt(s / fr)
        }
        val sorted = e.sortedArray()
        val thr = maxOf(sorted[frames / 10] * 4f, 0.0056f)
        val first = e.indexOfFirst { it > thr }
        if (first < 0) return Pair(pcm, n)
        val start = maxOf(0, first * fr - RATE / 20)
        if (start == 0) return Pair(pcm, n)
        return Pair(pcm.copyOfRange(start, n), n - start)
    }

    private fun ensureVadLocked(): Long {
        if (vad != 0L) return vad
        if (!VoiceModels.installed(context, VoiceModels.VAD)) {
            VoiceModels.ensureVad(context)
            return 0L
        }
        vad = NativeVoice.vadLoad(VoiceModels.file(context, VoiceModels.VAD).absolutePath)
        return vad
    }

    private fun ensureLoadedLocked(): Long {
        val path = VoiceModels.file(context, model).absolutePath
        if (handle != 0L && loadedPath == path) return handle
        freeLocked()
        val threads = (Runtime.getRuntime().availableProcessors() - 2).coerceIn(2, 4)
        handle = NativeVoice.load(path, threads)
        loadedPath = if (handle != 0L) path else null
        return handle
    }

    private fun freeLocked() {
        if (handle != 0L) NativeVoice.free(handle)
        if (vad != 0L) NativeVoice.vadFree(vad)
        handle = 0L
        vad = 0L
        loadedPath = null
    }

    private companion object {
        const val RATE = 16_000
        const val MIN_SAMPLES = RATE * 4 / 10 // under 0.4 s is a mis-tap
        const val IDLE_MS = 60_000L
        const val PAUSE_CHUNKS = 28 // 1.4 s of silence after speech ends a tap-panel recording
        const val WAIT_CHUNKS = 140 // 7 s without any speech ends it too
        val LOCK = Any()
        var handle = 0L
        var vad = 0L
        var loadedPath: String? = null
        const val VAD_THRESHOLD = 0.5f
    }
}
