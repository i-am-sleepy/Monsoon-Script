package com.monsoonscript.keyboard.voice

/** What is being spoken. It comes from the active layout, never from detection (D66): English layout = English, any Bangla layout = Bangla. */
enum class VoiceLang(val tag: String) {
    ENGLISH("en-US"),
    BANGLA("bn-BD"),
}

enum class VoiceError { NO_PERMISSION, NOT_AVAILABLE, NO_SPEECH, NETWORK, LANGUAGE, BUSY, NO_MODEL, OTHER }

/**
 * One way of turning speech into text (G5, D66): Google's recognizer today, the optional local models later. All calls and all callbacks are on the
 * main thread. An engine runs ONE recognition at a time; [start] drops whatever was running.
 */
interface VoiceEngine {
    interface Callback {
        /** The microphone is open. */
        fun onListening() {}

        /** Words recognized so far (engines that stream). */
        fun onPartial(text: String) {}

        /** The loudness of the voice right now, 0..1, about 10-20 times a second while listening: drives the level display. */
        fun onLevel(level: Float) {}

        /** The finished text of this recognition (may be empty). Exactly one of [onFinal] / [onError] ends a recognition, unless it was cancelled. */
        fun onFinal(text: String)

        fun onError(error: VoiceError)
    }

    /** [autoStop]: finish by itself when the speaker pauses (the tap panel); false = only [stop] ends it (hold to talk). Engines that always end on a pause ignore it. */
    fun start(lang: VoiceLang, callback: Callback, autoStop: Boolean)

    /** Stop listening and deliver what was heard so far through [Callback.onFinal]. */
    fun stop()

    /** Drop the recognition: no callback follows. */
    fun cancel()
}
