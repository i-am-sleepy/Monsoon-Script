package com.monsoonscript.keyboard.view

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.SoundPool
import android.os.SystemClock
import com.monsoonscript.keyboard.layout.Codes
import java.io.File
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.Random
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.sin

/**
 * The keyboard's own key click (settings: "Click sound" > "Built-in click", G1.7). Android's click (`AudioManager.playSoundEffect`) only plays while
 * the system "Touch sounds" switch is on; this one does not need it. Three short clicks (letters, space/enter, delete) are made in code (no sound
 * files in the app), written once as tiny WAV files into the cache folder and played through a [SoundPool]. It plays at the system sound volume and
 * stays silent in silent and vibrate mode. Main thread only; call [release] when the view goes away.
 */
class ClickSound(context: Context) {
    private val audio = context.getSystemService(AudioManager::class.java)
    private val pool: SoundPool
    private val ids = IntArray(KINDS)
    private var quiet = false
    private var checkedAt = 0L

    init {
        val attrs = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()
        pool = SoundPool.Builder().setMaxStreams(4).setAudioAttributes(attrs).build()
        for (k in 0 until KINDS) {
            try {
                val f = File(context.cacheDir, "click$k.wav")
                f.writeBytes(wav(FREQ[k], MS[k])) // a few hundred bytes; rewritten each time so a changed sound is never stale
                ids[k] = pool.load(f.path, 1) // loads in the background; play() does nothing until it is ready
            } catch (e: IOException) {
                ids[k] = 0 // no click rather than a crash
            }
        }
    }

    fun play(code: Int) {
        if (silentMode()) return
        val k = when (code) {
            Codes.SPACE, Codes.ENTER -> 1
            Codes.BACKSPACE -> 2
            else -> 0
        }
        val id = ids[k]
        if (id != 0) pool.play(id, 1f, 1f, 1, 0, 1f)
    }

    fun release() {
        pool.release()
    }

    /** Silent or vibrate ringer mode; asked at most once a second (it is a call into the system). */
    private fun silentMode(): Boolean {
        val now = SystemClock.uptimeMillis()
        if (now - checkedAt > RECHECK_MS) {
            checkedAt = now
            quiet = audio?.ringerMode != AudioManager.RINGER_MODE_NORMAL
        }
        return quiet
    }

    private companion object {
        const val KINDS = 3
        const val RATE = 44100
        const val TAU = 0.003 // decay time of the click in seconds
        const val RECHECK_MS = 1000L
        val FREQ = doubleArrayOf(2200.0, 1500.0, 1800.0) // letters, space/enter, delete
        val MS = intArrayOf(18, 24, 20)

        /** 16-bit mono WAV of a short decaying tone with a burst of noise at the start: a soft mechanical click. */
        fun wav(freq: Double, ms: Int): ByteArray {
            val n = RATE * ms / 1000
            val b = ByteBuffer.allocate(44 + n * 2).order(ByteOrder.LITTLE_ENDIAN)
            b.put("RIFF".toByteArray()).putInt(36 + n * 2).put("WAVE".toByteArray())
            b.put("fmt ".toByteArray()).putInt(16).putShort(1).putShort(1).putInt(RATE).putInt(RATE * 2).putShort(2).putShort(16)
            b.put("data".toByteArray()).putInt(n * 2)
            val rnd = Random(7)
            for (i in 0 until n) {
                val t = i.toDouble() / RATE
                val noise = (rnd.nextDouble() * 2 - 1) * exp(-t / 0.0008)
                val s = (sin(2 * PI * freq * t) * 0.75 + noise * 0.35) * exp(-t / TAU)
                b.putShort((s.coerceIn(-1.0, 1.0) * 0.85 * 32767).toInt().toShort())
            }
            return b.array()
        }
    }
}
