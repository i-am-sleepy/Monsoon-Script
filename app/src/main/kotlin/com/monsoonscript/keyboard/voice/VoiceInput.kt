package com.monsoonscript.keyboard.voice

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.widget.Toast
import com.monsoonscript.keyboard.R
import com.monsoonscript.keyboard.prefs.Prefs

/**
 * Voice typing for the keyboard (G5, D66). This first part is hold-to-talk on the strip's mic key: [holdStart] opens the microphone, [holdEnd] ends it, and the
 * finished text goes to the [Host], which types it at the cursor. While the key is held a pause in the speech finishes one piece of text, which is typed
 * at once, and listening starts again, so one long press can carry several sentences. Main thread only.
 */
class VoiceInput(private val context: Context, private val host: Host) {

    interface Host {
        /** Type this finished text at the cursor. */
        fun voiceText(text: String)

        /** The microphone opened or closed: the mic key shows it. */
        fun voiceListening(on: Boolean)
    }

    private val engine = GoogleVoiceEngine(context)
    private var holding = false
    private var lang = VoiceLang.ENGLISH
    private var silent = 0 // restarts in a row that heard nothing

    /** The notice was accepted and the microphone is allowed. Otherwise [VoicePermissionActivity] has to run first. */
    fun ready(): Boolean =
        context.checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED &&
            Prefs.of(context).getBoolean(Prefs.VOICE_NOTICE_OK, false)

    fun holdStart(l: VoiceLang) {
        if (!ready()) {
            VoicePermissionActivity.open(context)
            return
        }
        lang = l
        holding = true
        silent = 0
        begin()
    }

    /** The finger left the mic key. [cancel] = it slid away or the touch was cancelled: nothing is typed. */
    fun holdEnd(cancel: Boolean) {
        if (!holding) return
        holding = false
        host.voiceListening(false)
        if (cancel) engine.cancel() else engine.stop() // the text arrives through the callback
    }

    /** The keyboard is closing or the field changed. */
    fun cancel() {
        holding = false
        engine.cancel()
        host.voiceListening(false)
    }

    private fun begin() {
        engine.start(lang, callback)
    }

    private fun say(res: Int) = Toast.makeText(context, res, Toast.LENGTH_SHORT).show()

    private val callback = object : VoiceEngine.Callback {
        override fun onListening() {
            if (holding) host.voiceListening(true)
        }

        override fun onFinal(text: String) {
            if (text.isNotEmpty()) {
                silent = 0
                host.voiceText(text)
            } else {
                silent++
            }
            if (holding && silent < MAX_SILENT) begin() else if (holding) giveUp(R.string.voice_err_no_speech)
        }

        override fun onError(error: VoiceError) {
            if (error == VoiceError.NO_SPEECH) {
                silent++
                if (holding && silent < MAX_SILENT) begin() else if (holding) giveUp(R.string.voice_err_no_speech)
                return
            }
            holding = false
            host.voiceListening(false)
            say(
                when (error) {
                    VoiceError.NETWORK -> R.string.voice_err_network
                    VoiceError.NOT_AVAILABLE -> R.string.voice_err_unavailable
                    VoiceError.LANGUAGE -> R.string.voice_err_language
                    VoiceError.BUSY -> R.string.voice_err_busy
                    VoiceError.NO_PERMISSION -> R.string.voice_err_permission
                    else -> R.string.voice_err_other
                },
            )
        }
    }

    private fun giveUp(res: Int) {
        holding = false
        host.voiceListening(false)
        say(res)
    }

    private companion object {
        const val MAX_SILENT = 3
    }
}
