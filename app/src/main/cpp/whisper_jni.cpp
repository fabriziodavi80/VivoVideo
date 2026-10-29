// Ponte fra l'app e whisper.cpp: carica un modello e trascrive audio mono a 16 kHz.
// Restituisce una parola per riga: "inizio_ms\tfine_ms\tparola".
#include <jni.h>
#include <atomic>
#include <string>
#include <thread>
#include <android/log.h>
#include "whisper.h"

static std::atomic<int> g_progress{0};
static std::atomic<bool> g_abort{false};

extern "C" JNIEXPORT jlong JNICALL
Java_it_fabriziodavi_vivovideo_engine_Whisper_nativeInit(JNIEnv *env, jobject, jstring path) {
    const char *p = env->GetStringUTFChars(path, nullptr);
    whisper_context_params cp = whisper_context_default_params();
    cp.use_gpu = false;
    whisper_context *ctx = whisper_init_from_file_with_params(p, cp);
    env->ReleaseStringUTFChars(path, p);
    return reinterpret_cast<jlong>(ctx);
}

extern "C" JNIEXPORT void JNICALL
Java_it_fabriziodavi_vivovideo_engine_Whisper_nativeFree(JNIEnv *, jobject, jlong ctx) {
    if (ctx != 0) whisper_free(reinterpret_cast<whisper_context *>(ctx));
}

extern "C" JNIEXPORT jint JNICALL
Java_it_fabriziodavi_vivovideo_engine_Whisper_nativeProgress(JNIEnv *, jobject) { return g_progress.load(); }

extern "C" JNIEXPORT void JNICALL
Java_it_fabriziodavi_vivovideo_engine_Whisper_nativeAbort(JNIEnv *, jobject) { g_abort.store(true); }

// Restituisce byte UTF-8 (non String): un testo con caratteri spezzati non deve far cadere l'app.
static jbyteArray bytes(JNIEnv *env, const std::string &s) {
    jbyteArray a = env->NewByteArray((jsize) s.size());
    env->SetByteArrayRegion(a, 0, (jsize) s.size(), reinterpret_cast<const jbyte *>(s.data()));
    return a;
}

extern "C" JNIEXPORT jbyteArray JNICALL
Java_it_fabriziodavi_vivovideo_engine_Whisper_nativeTranscribe(JNIEnv *env, jobject, jlong ctxPtr, jfloatArray pcm, jstring lang, jint threads, jstring prompt, jint beam) {
    auto *ctx = reinterpret_cast<whisper_context *>(ctxPtr);
    if (ctx == nullptr) return bytes(env, "");
    g_progress.store(0);
    g_abort.store(false);
    const char *l = env->GetStringUTFChars(lang, nullptr);
    std::string language(l);
    env->ReleaseStringUTFChars(lang, l);
    const char *pr = env->GetStringUTFChars(prompt, nullptr);
    std::string initialPrompt(pr);
    env->ReleaseStringUTFChars(prompt, pr);

    // Con beam > 1 il modello valuta più frasi possibili e tiene la migliore: più lento, molto più preciso sulle parole.
    whisper_full_params wp = whisper_full_default_params(beam > 1 ? WHISPER_SAMPLING_BEAM_SEARCH : WHISPER_SAMPLING_GREEDY);
    if (beam > 1) wp.beam_search.beam_size = beam;
    wp.n_threads = threads > 0 ? threads : 4;
    wp.language = language == "auto" ? nullptr : language.c_str();
    wp.detect_language = false;
    wp.translate = false;
    wp.print_progress = false;
    wp.print_realtime = false;
    wp.print_timestamps = false;
    wp.print_special = false;
    // Una parola per segmento, con i suoi tempi: servono per i sottotitoli parola per parola.
    wp.token_timestamps = true;
    wp.max_len = 1;
    wp.split_on_word = true;
    wp.suppress_nst = true;
    // Un esempio di parlato "con gli ehm" convince il modello a scriverli (di solito li tralascia).
    wp.initial_prompt = initialPrompt.empty() ? nullptr : initialPrompt.c_str();
    wp.progress_callback = [](whisper_context *, whisper_state *, int progress, void *) { g_progress.store(progress); };
    wp.abort_callback = [](void *) { return g_abort.load(); };

    jsize n = env->GetArrayLength(pcm);
    jfloat *data = env->GetFloatArrayElements(pcm, nullptr);
    int rc = whisper_full(ctx, wp, data, n);
    env->ReleaseFloatArrayElements(pcm, data, JNI_ABORT);
    if (rc != 0) return bytes(env, "");

    std::string out;
    const int segs = whisper_full_n_segments(ctx);
    for (int i = 0; i < segs; i++) {
        const char *text = whisper_full_get_segment_text(ctx, i);
        int64_t t0 = whisper_full_get_segment_t0(ctx, i) * 10; // centesimi → millisecondi
        int64_t t1 = whisper_full_get_segment_t1(ctx, i) * 10;
        out += std::to_string(t0) + "\t" + std::to_string(t1) + "\t" + text + "\n";
    }
    g_progress.store(100);
    return bytes(env, out);
}
