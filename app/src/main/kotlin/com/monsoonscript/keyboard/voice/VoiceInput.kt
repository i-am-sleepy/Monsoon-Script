package com.monsoonscript.keyboard.voice

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import com.monsoonscript.keyboard.R
import com.monsoonscript.keyboard.prefs.Prefs

/**
 * Voice typing for the keyboard (G5, D66). This part is hold-to-talk on the strip's mic key: [holdStart] opens the microphone, [holdEnd] ends it, and the
 * finished text goes to the [Host], which types it at the cursor. The engine is chosen per language in Settings > Voice: Google's recognizer (default) or a
 * local model that was downloaded. While the key is held a pause in the speech finishes one piece of text (Google) or the recording reaches its limit (local):
 * that text is typed at once and listening starts again, so one long press can carry several sentences. Main thread only.
 */
class VoiceInput(private val context: Context, private val host: Host) {

    interface Host {
        /** Type this finished text at the cursor. */
        fun voiceText(text: String)

        /** What the mic key shows: [IDLE], [LISTENING] (microphone open) or [WORKING] (turning the audio into text). */
        fun voiceState(state: Int)
    }

    private val main = Handler(Looper.getMainLooper())
    private val google = GoogleVoiceEngine(context)
    private var local: LocalVoiceEngine? = null
    private var localModel: VoiceModel? = null
    private var engine: VoiceEngine? = null
    private var holding = false
    private var lang = VoiceLang.ENGLISH
    private var silent = 0 // rounds in a row that heard nothing

    private fun prefs() = Prefs.of(context)

    private fun hasMic() = context.checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED

    /** The engine chosen for this language, or null (a message was shown) when it cannot be used right now. */
    private fun pick(l: VoiceLang): VoiceEngine? {
        val key = if (l == VoiceLang.BANGLA) Prefs.VOICE_ENGINE_BN else Prefs.VOICE_ENGINE_EN
        if (prefs().getInt(key, Prefs.ENGINE_GOOGLE) == Prefs.ENGINE_LOCAL) {
            val m = VoiceModels.forLang(l)
            if (m == null || !NativeVoice.available || !VoiceModels.installed(context, m)) {
                say(R.string.voice_err_no_model)
                return null
            }
            if (local == null || localModel !== m) {
                local?.release()
                local = LocalVoiceEngine(context, m)
                localModel = m
            }
            return local
        }
        if (prefs().getBoolean(Prefs.VOICE_OFFLINE, false)) {
            say(R.string.voice_err_offline)
            return null
        }
        return google
    }

    fun holdStart(l: VoiceLang) {
        val e = pick(l) ?: return
        val isGoogle = e === google
        if (!hasMic() || (isGoogle && !prefs().getBoolean(Prefs.VOICE_NOTICE_OK, false))) {
            VoicePermissionActivity.open(context, isGoogle)
            return
        }
        main.removeCallbacks(resetWorking)
        engine = e
        lang = l
        holding = true
        silent = 0
        begin()
    }

    /** The finger left the mic key. [cancel] = it slid away or the touch was cancelled: nothing is typed. */
    fun holdEnd(cancel: Boolean) {
        if (!holding) return
        holding = false
        if (cancel) {
            engine?.cancel()
            host.voiceState(IDLE)
        } else {
            host.voiceState(WORKING) // the text arrives through the callback
            main.postDelayed(resetWorking, if (engine === google) 8_000L else 90_000L)
            engine?.stop()
        }
    }

    /** The keyboard is closing or the field changed. */
    fun cancel() {
        holding = false
        main.removeCallbacks(resetWorking)
        engine?.cancel()
        host.voiceState(IDLE)
    }

    /** The keyboard service is going away: also frees the local model. */
    fun release() {
        cancel()
        local?.release()
    }

    private val resetWorking = Runnable { if (!holding) host.voiceState(IDLE) }

    private fun begin() {
        engine?.start(lang, callback)
    }

    private fun say(res: Int) = Toast.makeText(context, res, Toast.LENGTH_SHORT).show()

    private fun finishRound() {
        main.removeCallbacks(resetWorking)
        host.voiceState(IDLE)
    }

    private val callback = object : VoiceEngine.Callback {
        override fun onListening() {
            if (holding) host.voiceState(LISTENING)
        }

        override fun onFinal(text: String) {
            if (text.isNotEmpty()) {
                silent = 0
                host.voiceText(text)
            } else {
                silent++
            }
            if (holding && silent < MAX_SILENT) begin() else if (holding) giveUp(R.string.voice_err_no_speech) else finishRound()
        }

        override fun onError(error: VoiceError) {
            if (error == VoiceError.NO_SPEECH) {
                silent++
                if (holding && silent < MAX_SILENT) begin() else if (holding) giveUp(R.string.voice_err_no_speech) else finishRound()
                return
            }
            holding = false
            finishRound()
            say(
                when (error) {
                    VoiceError.NETWORK -> R.string.voice_err_network
                    VoiceError.NOT_AVAILABLE -> R.string.voice_err_unavailable
                    VoiceError.LANGUAGE -> R.string.voice_err_language
                    VoiceError.BUSY -> R.string.voice_err_busy
                    VoiceError.NO_PERMISSION -> R.string.voice_err_permission
                    VoiceError.NO_MODEL -> R.string.voice_err_no_model
                    else -> R.string.voice_err_other
                },
            )
        }
    }

    private fun giveUp(res: Int) {
        holding = false
        finishRound()
        say(res)
    }

    companion object {
        const val IDLE = 0
        const val LISTENING = 1
        const val WORKING = 2
        private const val MAX_SILENT = 3
    }
}
