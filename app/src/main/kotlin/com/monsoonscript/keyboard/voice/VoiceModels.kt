package com.monsoonscript.keyboard.voice

import android.content.Context
import android.net.Uri
import android.os.Handler
import android.os.Looper
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

/** One downloadable local speech model (G5, D66). [bytes] and [sha256] are pinned: a file that does not match is never used. */
class VoiceModel(
    val id: String,
    val title: String,
    val lang: VoiceLang,
    val file: String,
    val url: String,
    val bytes: Long,
    val sha256: String,
    val credit: String,
    val maxSeconds: Int, // one recording; the Moonshine models accept 48 s per run, Parakeet takes a whole clip
)

object VoiceModels {
    val ENGLISH = VoiceModel(
        id = "parakeet-en",
        title = "English: Parakeet TDT-CTC 110M",
        lang = VoiceLang.ENGLISH,
        file = "parakeet-tdt_ctc-110m-Q4_K_M.gguf",
        url = "https://huggingface.co/handy-computer/parakeet-tdt_ctc-110m-gguf/resolve/main/parakeet-tdt_ctc-110m-Q4_K_M.gguf",
        bytes = 89_989_600L,
        sha256 = "486414fd90185a8c8a4ced7c123cfb133ff4f7958426c6b8bd9049946b56b448",
        credit = "NVIDIA Parakeet TDT-CTC 110M, 4-bit GGUF by the transcribe.cpp project (CC-BY-4.0)",
        maxSeconds = 120,
    )

    val BANGLA = VoiceModel(
        id = "moonshine-bn",
        title = "Bangla: Moonshine Base (fine-tuned)",
        lang = VoiceLang.BANGLA,
        file = "moonshine-base-bn-Q8_0.gguf",
        url = "https://github.com/i-am-sleepy/Monsoon-Script/releases/download/voice-models/moonshine-base-bn-Q8_0.gguf",
        bytes = 76_493_888L,
        sha256 = "9b90dfd2cc934b15d49bf7335ce98fb654191e3a9273e51e7f162636ffc16f95",
        credit = "Sanjidh090/moonshine-base-bn (MIT), a fine-tune of Moonshine Base by Useful Sensors (MIT), converted to GGUF (Q8_0)",
        maxSeconds = 40,
    )

    val ALL = listOf(ENGLISH, BANGLA)

    fun forLang(lang: VoiceLang): VoiceModel = if (lang == VoiceLang.ENGLISH) ENGLISH else BANGLA

    fun dir(context: Context): File = File(context.filesDir, "voice").also { it.mkdirs() }

    fun file(context: Context, m: VoiceModel): File = File(dir(context), m.file)

    /** The file is there and has the pinned size (the hash was checked when it was installed). */
    fun installed(context: Context, m: VoiceModel): Boolean = file(context, m).let { it.isFile && it.length() == m.bytes }

    fun delete(context: Context, m: VoiceModel) {
        file(context, m).delete()
        File(dir(context), m.file + ".part").delete()
    }

    fun partBytes(context: Context, m: VoiceModel): Long = File(dir(context), m.file + ".part").let { if (it.isFile) it.length() else 0L }

    fun sha256Of(f: File): String {
        val md = MessageDigest.getInstance("SHA-256")
        f.inputStream().use { input ->
            val buf = ByteArray(256 * 1024)
            while (true) {
                val n = input.read(buf)
                if (n < 0) break
                md.update(buf, 0, n)
            }
        }
        return md.digest().joinToString("") { "%02x".format(it) }
    }

    /** Moves a finished .part file into place if it is the pinned model; deletes it otherwise. */
    private fun finish(context: Context, m: VoiceModel, part: File): Boolean {
        val ok = part.length() == m.bytes && sha256Of(part).equals(m.sha256, ignoreCase = true)
        if (!ok) {
            part.delete()
            return false
        }
        val dest = file(context, m)
        dest.delete()
        return part.renameTo(dest)
    }

    /** Copies a file the owner picked (already downloaded in a browser, say) into the app; false if it is not the pinned model. Blocking. */
    fun importFrom(context: Context, m: VoiceModel, uri: Uri): Boolean {
        val part = File(dir(context), m.file + ".part")
        part.delete()
        try {
            val input = context.contentResolver.openInputStream(uri) ?: return false
            input.use { src -> FileOutputStream(part).use { out -> src.copyTo(out, 256 * 1024) } }
        } catch (e: Exception) {
            part.delete()
            return false
        }
        return finish(context, m, part)
    }

    /**
     * Downloads [m] on a background thread and resumes a partial file. The listener is called on the main thread. Nothing is downloaded while Offline mode is
     * on (D10); the caller checks that before it starts, this class checks nothing about the network setting itself.
     */
    class Download(private val context: Context, private val m: VoiceModel, private val listener: Listener) {
        interface Listener {
            fun onProgress(done: Long, total: Long)

            fun onDone(ok: Boolean, message: String?)
        }

        private val main = Handler(Looper.getMainLooper())

        @Volatile private var cancelled = false

        fun start() {
            Thread({ run() }, "voice-download").start()
        }

        fun cancel() {
            cancelled = true
        }

        private fun done(ok: Boolean, message: String?) = main.post { listener.onDone(ok, message) }

        private fun run() {
            val part = File(dir(context), m.file + ".part")
            var conn: HttpURLConnection? = null
            try {
                var have = if (part.isFile) part.length() else 0L
                if (have > m.bytes) {
                    part.delete()
                    have = 0L
                }
                conn = (URL(m.url).openConnection() as HttpURLConnection).apply {
                    connectTimeout = 15_000
                    readTimeout = 30_000
                    instanceFollowRedirects = true
                    if (have > 0) setRequestProperty("Range", "bytes=$have-")
                }
                val code = conn.responseCode
                if (code != 200 && code != 206) {
                    done(false, "HTTP $code")
                    return
                }
                if (code == 200) have = 0L // the server ignored the range: start again
                var done = have
                FileOutputStream(part, have > 0).use { out ->
                    conn.inputStream.use { input ->
                        val buf = ByteArray(128 * 1024)
                        var last = 0L
                        while (true) {
                            if (cancelled) {
                                done(false, "cancelled")
                                return
                            }
                            val n = input.read(buf)
                            if (n < 0) break
                            out.write(buf, 0, n)
                            done += n
                            val now = System.currentTimeMillis()
                            if (now - last > 250) {
                                last = now
                                val d = done
                                main.post { listener.onProgress(d, m.bytes) }
                            }
                        }
                    }
                }
                if (finish(context, m, part)) done(true, null) else done(false, "the file did not match the expected model (size or SHA-256)")
            } catch (e: Exception) {
                done(false, e.message ?: e.javaClass.simpleName)
            } finally {
                conn?.disconnect()
            }
        }
    }
}
