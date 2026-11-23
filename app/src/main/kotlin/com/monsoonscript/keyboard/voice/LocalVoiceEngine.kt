package com.monsoonscript.keyboard.voice

import android.content.Context
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Handler
import android.os.Looper

/**
 * Method 2 of the voice typing design (G5, D66): records the microphone itself and transcribes it on the phone with a downloaded model (transcribe.cpp).
 * The models are not streaming: while the key is held the audio is only collected, and [stop] turns it into text (a second or two for a long sentence).
 * The model stays loaded for a minute after use so the next dictation starts at once, then it is freed. All calls on the main thread.
 */
class LocalVoiceEngine(private val context: Context, private val model: VoiceModel) : VoiceEngine {

    private val main = Handler(Looper.getMainLooper())
    private var session = 0

    @Volatile private var recording = false

    override fun start(lang: VoiceLang, callback: VoiceEngine.Callback) {
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
        Thread({ record(mine, callback) }, "voice-record").start()
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

    private fun record(mine: Int, callback: VoiceEngine.Callback) {
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
        val pcm = FloatArray(MAX_SECONDS * RATE)
        var n = 0
        try {
            rec.startRecording()
            post(mine) { callback.onListening() }
            val chunk = ShortArray(RATE / 10)
            while (recording && n < pcm.size && session == mine) {
                val got = rec.read(chunk, 0, minOf(chunk.size, pcm.size - n))
                if (got <= 0) break
                for (i in 0 until got) pcm[n + i] = chunk[i] / 32768f
                n += got
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
        val text: String?
        var status = 0
        synchronized(LOCK) {
            val h = ensureLoadedLocked()
            if (h == 0L) {
                status = NativeVoice.lastStatus()
                text = null
            } else {
                val bytes = NativeVoice.run(h, pcm, n)
                status = NativeVoice.lastStatus()
                text = bytes?.toString(Charsets.UTF_8)
            }
        }
        if (session != mine) return
        main.post { scheduleUnload() }
        if (text == null) post(mine) { callback.onError(VoiceError.OTHER) } else post(mine) { callback.onFinal(clean(text)) }
    }

    private fun clean(t: String): String = t.trim().replace(Regex("\\s+"), " ")

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
        handle = 0L
        loadedPath = null
    }

    private companion object {
        const val RATE = 16_000
        const val MAX_SECONDS = 120 // one recording; Parakeet takes a whole clip in one pass
        const val MIN_SAMPLES = RATE * 4 / 10 // under 0.4 s is a mis-tap
        const val IDLE_MS = 60_000L
        val LOCK = Any()
        var handle = 0L
        var loadedPath: String? = null
    }
}
