package com.monsoonscript.keyboard.emoji

import android.content.Context
import android.graphics.Paint
import android.graphics.Typeface
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.Process
import java.util.BitSet

/**
 * Loads the emoji list and the Noto Color Emoji font once, on a background thread (D56): parses assets/emoji/emoji.tsv, maps the font
 * straight from the APK (the asset is stored uncompressed), and drops the emoji this phone's own font cannot draw. The font only draws the
 * picker; what gets typed is always the Unicode string, drawn by whatever app receives it, so the check asks the SYSTEM font.
 */
class EmojiLoader(context: Context) {
    class Result(val data: EmojiData, val typeface: Typeface)

    private val app = context.applicationContext
    private val main = Handler(Looper.getMainLooper())
    private var started = false
    private var readyCallback: Runnable? = null

    @Volatile
    var result: Result? = null
        private set

    /** Starts loading in the background (once). [ready] runs on the main thread when the result exists; later calls only replace the callback. Main thread only. */
    fun start(ready: Runnable?) {
        readyCallback = ready
        if (result != null) {
            if (ready != null) main.post(ready)
            return
        }
        if (started) return
        started = true
        Thread({ run() }, "emoji-load").start()
    }

    private fun run() {
        Process.setThreadPriority(Process.THREAD_PRIORITY_BACKGROUND)
        try {
            val parsed = app.assets.open(DATA_ASSET).bufferedReader(Charsets.UTF_8).use { EmojiData.parse(it) }
            val typeface = try {
                Typeface.createFromAsset(app.assets, FONT_ASSET)
            } catch (e: RuntimeException) {
                Typeface.DEFAULT // font missing or unreadable: the system emoji font draws the picker
            }
            result = Result(filterForPhone(parsed), typeface)
        } catch (e: Exception) {
            started = false // a later open of the picker tries again
            return
        }
        main.post { readyCallback?.run() }
    }

    /** Hides what the system font cannot draw; the answer is cached per build of the phone's software (it only changes with a system update). */
    private fun filterForPhone(parsed: EmojiData): EmojiData {
        val flat = parsed.flatStrings()
        val prefs = app.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val key = Build.FINGERPRINT + "|" + flat.size
        var unsupported = if (prefs.getString(KEY_SUPPORT_FOR, null) == key) decode(prefs.getString(KEY_SUPPORT_BITS, null), flat.size) else null
        if (unsupported == null) {
            val paint = Paint() // default typeface = the system font stack, which is what other apps draw with
            val u = BooleanArray(flat.size)
            for (i in flat.indices) u[i] = !paint.hasGlyph(flat[i])
            unsupported = u
            prefs.edit().putString(KEY_SUPPORT_FOR, key).putString(KEY_SUPPORT_BITS, encode(u)).apply()
        }
        // If the phone seems to have no color emoji font at all, hiding almost everything would be worse than showing everything.
        var okBase = 0
        var n = 0
        for (i in parsed.emoji.indices) {
            if (!unsupported[n]) okBase++
            n += 1 + parsed.variants[i].size
        }
        return if (okBase * 2 < parsed.size) parsed else parsed.withoutUnsupported(unsupported)
    }

    private fun encode(flags: BooleanArray): String {
        val bits = BitSet(flags.size)
        for (i in flags.indices) if (flags[i]) bits.set(i)
        val bytes = bits.toByteArray()
        val sb = StringBuilder(bytes.size * 2)
        for (b in bytes) {
            val v = b.toInt() and 0xFF
            sb.append(HEX[v shr 4]).append(HEX[v and 15])
        }
        return sb.toString()
    }

    private fun decode(hex: String?, count: Int): BooleanArray? {
        if (hex == null || hex.length % 2 != 0) return null
        val bytes = ByteArray(hex.length / 2)
        for (i in bytes.indices) {
            val hi = Character.digit(hex[2 * i], 16)
            val lo = Character.digit(hex[2 * i + 1], 16)
            if (hi < 0 || lo < 0) return null
            bytes[i] = ((hi shl 4) or lo).toByte()
        }
        val bits = BitSet.valueOf(bytes)
        if (bits.length() > count) return null
        return BooleanArray(count) { bits.get(it) }
    }

    private companion object {
        const val DATA_ASSET = "emoji/emoji.tsv"
        const val FONT_ASSET = "emoji/NotoColorEmoji.ttf"
        const val PREFS = "monsoon_emoji"
        const val KEY_SUPPORT_FOR = "support_for"
        const val KEY_SUPPORT_BITS = "support_bits"
        const val HEX = "0123456789abcdef"
    }
}

/** [TextStore] on a SharedPreferences file (recents and remembered skin tones stay on the phone, never uploaded). Writes are asynchronous. */
class PrefsTextStore(context: Context) : TextStore {
    private val prefs = context.applicationContext.getSharedPreferences("monsoon_emoji", Context.MODE_PRIVATE)

    override fun get(key: String): String? = prefs.getString(key, null)

    override fun put(key: String, value: String) {
        prefs.edit().putString(key, value).apply()
    }
}
