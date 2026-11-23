// Native side of the local voice engines (G5, D66): a thin JNI layer over transcribe.cpp (ggml, MIT).
// Kotlin side: com.monsoonscript.keyboard.voice.NativeVoice. All functions are static; the "handle" is a transcribe_session*.
#include <jni.h>

#include <cstring>

#include "transcribe.h"

namespace {
thread_local int g_status = 0;  // 0 = fine, > 0 = transcribe_status, < 0 = our own failure
}

extern "C" {

JNIEXPORT jlong JNICALL Java_com_monsoonscript_keyboard_voice_NativeVoice_load(JNIEnv *env, jclass, jstring jpath, jint threads) {
    g_status = 0;
    const char *path = env->GetStringUTFChars(jpath, nullptr);
    if (!path) {
        g_status = -1;
        return 0;
    }
    transcribe_model_load_params lp;
    transcribe_model_load_params_init(&lp);
    lp.backend = TRANSCRIBE_BACKEND_CPU;
    transcribe_session_params sp;
    transcribe_session_params_init(&sp);
    sp.n_threads = threads;
    transcribe_session *session = nullptr;
    const transcribe_status st = transcribe_open(path, &lp, &sp, &session);
    env->ReleaseStringUTFChars(jpath, path);
    if (st != TRANSCRIBE_OK || !session) {
        g_status = st != TRANSCRIBE_OK ? static_cast<int>(st) : -2;
        return 0;
    }
    return reinterpret_cast<jlong>(session);
}

// Returns the UTF-8 bytes of the transcript (possibly empty), or null on failure (see lastStatus).
JNIEXPORT jbyteArray JNICALL Java_com_monsoonscript_keyboard_voice_NativeVoice_run(JNIEnv *env, jclass, jlong handle, jfloatArray jpcm, jint n) {
    g_status = 0;
    auto *session = reinterpret_cast<transcribe_session *>(handle);
    if (!session || n <= 0) {
        g_status = -3;
        return nullptr;
    }
    jfloat *pcm = env->GetFloatArrayElements(jpcm, nullptr);
    if (!pcm) {
        g_status = -1;
        return nullptr;
    }
    transcribe_run_params rp;
    transcribe_run_params_init(&rp);
    const transcribe_status st = transcribe_run(session, pcm, static_cast<int>(n), &rp);
    env->ReleaseFloatArrayElements(jpcm, pcm, JNI_ABORT);
    if (st != TRANSCRIBE_OK) {
        g_status = static_cast<int>(st);
        return nullptr;
    }
    const char *text = transcribe_full_text(session);
    const size_t len = text ? std::strlen(text) : 0;
    jbyteArray out = env->NewByteArray(static_cast<jsize>(len));
    if (out && len) env->SetByteArrayRegion(out, 0, static_cast<jsize>(len), reinterpret_cast<const jbyte *>(text));
    return out;
}

// Longest audio one run accepts, in seconds; 0 = no limit known / unbounded.
JNIEXPORT jfloat JNICALL Java_com_monsoonscript_keyboard_voice_NativeVoice_maxSeconds(JNIEnv *, jclass, jlong handle) {
    auto *session = reinterpret_cast<transcribe_session *>(handle);
    if (!session) return 0.f;
    transcribe_session_limits lim;
    transcribe_session_limits_init(&lim);
    if (transcribe_session_get_limits(session, &lim) != TRANSCRIBE_OK) return 0.f;
    return lim.effective_max_audio_ms > 0 ? static_cast<jfloat>(lim.effective_max_audio_ms) / 1000.f : 0.f;
}

JNIEXPORT jint JNICALL Java_com_monsoonscript_keyboard_voice_NativeVoice_lastStatus(JNIEnv *, jclass) { return g_status; }

JNIEXPORT void JNICALL Java_com_monsoonscript_keyboard_voice_NativeVoice_free(JNIEnv *, jclass, jlong handle) {
    if (handle) transcribe_session_free(reinterpret_cast<transcribe_session *>(handle));
}

}  // extern "C"
