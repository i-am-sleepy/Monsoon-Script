package com.monsoonscript.keyboard.voice

/**
 * The native voice engine (G5, D66): transcribe.cpp behind a few JNI functions (app/src/main/cpp/voice_jni.cpp). [available] is false on phones the library
 * was not built for (it is arm64 only) and in builds without the transcribe.cpp submodule; the keyboard then simply offers no local engine.
 */
object NativeVoice {
    val available: Boolean = try {
        System.loadLibrary("monsoonvoice")
        true
    } catch (e: UnsatisfiedLinkError) {
        false
    }

    /** Opens a GGUF model; 0 on failure (see [lastStatus]). */
    @JvmStatic external fun load(path: String, threads: Int): Long

    /** Transcribes 16 kHz mono float PCM; the UTF-8 bytes of the text, or null on failure (see [lastStatus]). */
    @JvmStatic external fun run(handle: Long, pcm: FloatArray, n: Int): ByteArray?

    /** The longest audio one run accepts in seconds, 0 = no limit. */
    @JvmStatic external fun maxSeconds(handle: Long): Float

    @JvmStatic external fun lastStatus(): Int

    /** Opens the Silero speech detector (a small GGUF); 0 on failure. */
    @JvmStatic external fun vadLoad(path: String): Long

    /** The speech parts of 16 kHz mono PCM as [start0, end0, start1, end1 ...] in samples; empty = no speech; null = failure. [threshold] 0..1, higher = stricter. */
    @JvmStatic external fun vadSegments(handle: Long, pcm: FloatArray, n: Int, threshold: Float): IntArray?

    @JvmStatic external fun vadFree(handle: Long)

    @JvmStatic external fun free(handle: Long)
}
