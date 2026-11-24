package com.monsoonscript.keyboard.voice

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer

/**
 * Method 1 of the voice typing design (G5, D66): Android's own [SpeechRecognizer], which is the speech service of the phone (normally Google's, the one
 * Gboard and Ridmik use). It needs no INTERNET permission from us; the service sends the audio to Google. A new recognizer is made for every recognition
 * and destroyed when it ends, which is the most reliable way to use this API.
 */
class GoogleVoiceEngine(private val context: Context) : VoiceEngine {

    private val main = Handler(Looper.getMainLooper())
    private var recognizer: SpeechRecognizer? = null
    private var session = 0

    /** Why the last recognition failed, in words and numbers for the owner and for me (shown next to the message). */
    var lastDetail: String = ""
        private set

    private fun services() = context.packageManager.queryIntentServices(Intent("android.speech.RecognitionService"), 0)

    /**
     * The speech service to use. Gboard does not depend on which service the phone has set as its default, so neither do we: when Google's own service is
     * installed it is asked for explicitly; otherwise the phone's default is used (null).
     */
    private fun googleComponent(): ComponentName? {
        val g = services().firstOrNull { it.serviceInfo.packageName == GOOGLE_APP } ?: return null
        return ComponentName(g.serviceInfo.packageName, g.serviceInfo.name)
    }

    fun isAvailable(): Boolean = googleComponent() != null || SpeechRecognizer.isRecognitionAvailable(context)

    private fun describeServices(): String = services().joinToString(", ") { it.serviceInfo.packageName }.ifEmpty { "none" }

    override fun start(lang: VoiceLang, callback: VoiceEngine.Callback, autoStop: Boolean) {
        cancel()
        lastDetail = ""
        if (!isAvailable()) {
            lastDetail = "no speech service on this phone (found: " + describeServices() + ")"
            callback.onError(VoiceError.NOT_AVAILABLE)
            return
        }
        val comp = googleComponent()
        val r = try {
            if (comp != null) SpeechRecognizer.createSpeechRecognizer(context, comp) else SpeechRecognizer.createSpeechRecognizer(context)
        } catch (e: RuntimeException) {
            lastDetail = "could not start the speech service: " + (e.message ?: e.javaClass.simpleName)
            null
        }
        if (r == null) {
            callback.onError(VoiceError.NOT_AVAILABLE)
            return
        }
        val mine = ++session
        recognizer = r
        r.setRecognitionListener(object : RecognitionListener {
            private fun live() = mine == session && recognizer === r

            override fun onReadyForSpeech(params: Bundle?) {
                if (live()) callback.onListening()
            }

            override fun onBeginningOfSpeech() {}

            override fun onRmsChanged(rmsdB: Float) {
                if (live()) callback.onLevel(((rmsdB + 2f) / 12f).coerceIn(0f, 1f))
            }

            override fun onBufferReceived(buffer: ByteArray?) {}

            override fun onEndOfSpeech() {}

            override fun onError(error: Int) {
                if (!live()) return
                finish(r)
                lastDetail = "Google speech error $error (" + errorName(error) + ")" + (if (comp != null) "" else ", default service")
                callback.onError(map(error))
            }

            override fun onResults(results: Bundle?) {
                if (!live()) return
                val text = first(results)
                finish(r)
                callback.onFinal(text)
            }

            override fun onPartialResults(partialResults: Bundle?) {
                if (!live()) return
                val text = first(partialResults)
                if (text.isNotEmpty()) callback.onPartial(text)
            }

            override fun onEvent(eventType: Int, params: Bundle?) {}
        })
        val i = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
        i.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
        i.putExtra(RecognizerIntent.EXTRA_LANGUAGE, lang.tag)
        i.putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
        i.putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
        i.putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, context.packageName)
        // Hints only: the speech service may ignore them. A little more patience for a pause between two sentences.
        i.putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 2000L)
        try {
            r.startListening(i)
        } catch (e: RuntimeException) {
            finish(r)
            callback.onError(VoiceError.OTHER)
        }
    }

    override fun stop() {
        recognizer?.stopListening()
    }

    override fun cancel() {
        session++
        val r = recognizer ?: return
        recognizer = null
        r.cancel()
        destroyLater(r)
    }

    /** The recognition ended by itself: forget the recognizer (the callback may start the next one at once). */
    private fun finish(r: SpeechRecognizer) {
        if (recognizer === r) recognizer = null
        destroyLater(r)
    }

    // Destroying a recognizer from inside one of its own callbacks is asking for trouble: do it on the next loop turn.
    private fun destroyLater(r: SpeechRecognizer) {
        main.post { r.destroy() }
    }

    private fun first(b: Bundle?): String =
        b?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()?.trim().orEmpty()

    private fun errorName(code: Int) = when (code) {
        1 -> "network timeout"
        2 -> "network"
        3 -> "audio"
        4 -> "server"
        5 -> "client"
        6 -> "no speech"
        7 -> "no match"
        8 -> "busy"
        9 -> "no permission"
        10 -> "too many requests"
        11 -> "server disconnected"
        12 -> "language not supported"
        13 -> "language unavailable"
        else -> "other"
    }

    private fun map(code: Int): VoiceError = when (code) {
        SpeechRecognizer.ERROR_NETWORK_TIMEOUT, SpeechRecognizer.ERROR_NETWORK, SpeechRecognizer.ERROR_SERVER -> VoiceError.NETWORK
        SpeechRecognizer.ERROR_SPEECH_TIMEOUT, SpeechRecognizer.ERROR_NO_MATCH -> VoiceError.NO_SPEECH
        SpeechRecognizer.ERROR_RECOGNIZER_BUSY, SpeechRecognizer.ERROR_TOO_MANY_REQUESTS -> VoiceError.BUSY
        SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> VoiceError.NO_PERMISSION
        12, 13 -> VoiceError.LANGUAGE // ERROR_LANGUAGE_NOT_SUPPORTED, ERROR_LANGUAGE_UNAVAILABLE (newer Android versions)
        else -> VoiceError.OTHER
    }

    private companion object {
        const val GOOGLE_APP = "com.google.android.googlequicksearchbox"
    }
}
