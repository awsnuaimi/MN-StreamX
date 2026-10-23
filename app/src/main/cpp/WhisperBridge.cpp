#include <jni.h>
#include <whisper.h>
#include <atomic>
#include <algorithm>
#include <chrono>
#include <cmath>
#include <string>
#include <vector>
#include <new>
#if defined(__ANDROID__) || defined(__GLIBC__)
#include <malloc.h>
#endif

// Supplied by the checked patch of the pinned upstream source.
extern "C" whisper_state * mnx_whisper_init_live_state(whisper_context *, int);
extern "C" int mnx_whisper_full_with_target(whisper_context *, whisper_state *, whisper_full_params,
                                           const float *, int, const char *);

struct Session {
    whisper_context *context = nullptr;
    whisper_state *state = nullptr;
    int threads = 1;
    std::atomic<bool> cancelled{false};
    int detectedLanguage = -1;
    std::string modelPath;
    std::chrono::steady_clock::time_point deadline;
};
static bool abortWork(void *data) {
    auto *s = static_cast<Session *>(data);
    return s->cancelled.load(std::memory_order_relaxed) || std::chrono::steady_clock::now() > s->deadline;
}
static void error(JNIEnv *env, const char *message) {
    env->ThrowNew(env->FindClass("java/io/IOException"), message);
}
static bool loadModel(Session *s) {
    if (s->cancelled.load(std::memory_order_relaxed)) return false;
    if (s->context) return true;
    auto options = whisper_context_default_params(); options.use_gpu = false;
    s->context = whisper_init_from_file_with_params_no_state(s->modelPath.c_str(), options);
    return s->context != nullptr;
}
static void purgeUnusedMemory() {
    // Return already-freed pages at phase boundaries; never change the global allocator policy.
#if defined(__ANDROID__)
    mallopt(M_PURGE, 0); // Available since API 28; application minSdk is 29.
#elif defined(__GLIBC__)
    malloc_trim(0);
#endif
}
// Whisper returns UTF-8, whereas JNI NewStringUTF expects modified UTF-8.
static jstring javaString(JNIEnv *env, const std::string &text) {
    auto bytes = env->NewByteArray(static_cast<jsize>(text.size()));
    if (!bytes) return nullptr;
    env->SetByteArrayRegion(bytes, 0, static_cast<jsize>(text.size()), reinterpret_cast<const jbyte *>(text.data()));
    auto cls = env->FindClass("java/lang/String");
    auto constructor = env->GetMethodID(cls, "<init>", "([BLjava/lang/String;)V");
    auto encoding = env->NewStringUTF("UTF-8");
    auto result = static_cast<jstring>(env->NewObject(cls, constructor, bytes, encoding));
    env->DeleteLocalRef(bytes); env->DeleteLocalRef(encoding); env->DeleteLocalRef(cls);
    return result;
}
extern "C" JNIEXPORT jlong JNICALL
Java_com_yourapp_iptv_data_caption_NativeWhisper_create(JNIEnv *env, jobject, jstring path, jint threads) {
    const char *name = env->GetStringUTFChars(path, nullptr);
    if (!name) return 0;
    Session *s = nullptr;
    try {
        s = new Session();
        s->modelPath = name;
        s->threads = threads < 1 ? 1 : (threads > 2 ? 2 : threads);
        whisper_log_set([](ggml_log_level, const char *, void *) {}, nullptr);
        loadModel(s);
    } catch (...) { }
    env->ReleaseStringUTFChars(path, name);
    if (!s || !s->context) { delete s; error(env, "Could not load the local speech model"); return 0; }
    return reinterpret_cast<jlong>(s);
}
extern "C" JNIEXPORT jobjectArray JNICALL
Java_com_yourapp_iptv_data_caption_NativeWhisper_decode(JNIEnv *env, jobject, jlong handle,
                                                      jfloatArray samples, jstring language, jboolean translateToEnglish,
                                                      jstring targetLanguage) {
    auto *s = reinterpret_cast<Session *>(handle);
    const jsize count = env->GetArrayLength(samples);
    if (!s || count < 16000 || count > 96000) { error(env, "Invalid speech window"); return nullptr; }
    try { if (!loadModel(s)) { error(env, "Could not reload the local speech model"); return nullptr; } }
    catch (...) { error(env, "Could not reload the local speech model"); return nullptr; }
    if (!s->state) {
        // Capacity covers the entire maximum six-second input (300 steps, rounded to 320).
        // The upstream default allocates encoder/decoder buffers for a 30-second file.
        try { s->state = mnx_whisper_init_live_state(s->context, 320); }
        catch (...) { error(env, "Could not allocate speech state"); return nullptr; }
        if (!s->state) { error(env, "Could not allocate speech state"); return nullptr; }
    }
    const char *lang = env->GetStringUTFChars(language, nullptr);
    if (!lang) return nullptr;
    std::string selected(lang); env->ReleaseStringUTFChars(language, lang);
    const char *targetValue = env->GetStringUTFChars(targetLanguage, nullptr);
    if (!targetValue) return nullptr;
    std::string target(targetValue); env->ReleaseStringUTFChars(targetLanguage, targetValue);
    if (selected == "auto" && s->detectedLanguage >= 0) selected = whisper_lang_str(s->detectedLanguage);
    std::vector<float> input;
    try { input.resize(count); } catch (...) { error(env, "Not enough speech memory"); return nullptr; }
    env->GetFloatArrayRegion(samples, 0, count, input.data());
    if (env->ExceptionCheck()) return nullptr;
    // Real-time TV decodes cannot afford four competing beam hypotheses.
    // Greedy with one candidate bounds decoder work, without relaxing the deadline.
    auto p = whisper_full_default_params(WHISPER_SAMPLING_GREEDY);
    p.greedy.best_of = 1;
    p.n_threads = s->threads;
    p.language = selected == "auto" ? "auto" : selected.c_str();
    p.translate = translateToEnglish == JNI_TRUE; p.no_context = true; p.single_segment = true;
    // One encoder step covers 320 PCM samples. Round up, keeping the entire audio window.
    p.audio_ctx = std::max(128, ((count + 319) / 320 + 31) / 32 * 32);
    // Bound text length to the available audio; reduces repeated-token runaway on TV CPUs.
    p.max_tokens = std::min(72, std::max(32, (count * 16) / 16000));
    p.n_max_text_ctx = 0;
    p.print_realtime = false; p.print_progress = false; p.print_timestamps = false; p.print_special = false;
    p.suppress_nst = true; p.temperature = 0.0f; p.temperature_inc = 0.0f;
    p.abort_callback = abortWork; p.abort_callback_user_data = s;
    // Model reload and state allocation are setup costs, not speech inference.
    // Start the watchdog after setup so the first live window is not penalized.
    s->deadline = std::chrono::steady_clock::now() + std::chrono::seconds(10);
    if (abortWork(s)) {
        if (s->cancelled.load(std::memory_order_relaxed)) error(env, "Local speech inference interrupted");
        else env->ThrowNew(env->FindClass("java/util/concurrent/TimeoutException"), "Local speech exceeded its time budget");
        return nullptr;
    }
    int result;
    try { result = mnx_whisper_full_with_target(s->context, s->state, p, input.data(), count, target.c_str()); }
    catch (...) { error(env, "Local speech inference failed"); return nullptr; }
    if (std::chrono::steady_clock::now() > s->deadline) {
        env->ThrowNew(env->FindClass("java/util/concurrent/TimeoutException"), "Local speech exceeded its time budget");
        return nullptr;
    }
    if (result != 0 || abortWork(s)) { error(env, "Local speech inference interrupted"); return nullptr; }
    s->detectedLanguage = whisper_full_lang_id_from_state(s->state);
    std::string text;
    int64_t first = 0, last = 0;
    const int segments = whisper_full_n_segments_from_state(s->state);
    for (int i = 0; i < segments; ++i) {
        if (whisper_full_get_segment_no_speech_prob_from_state(s->state, i) > 0.75f) continue;
        double logProbability = 0.0;
        int tokens = 0;
        for (int j = 0; j < whisper_full_n_tokens_from_state(s->state, i); ++j) {
            const auto token = whisper_full_get_token_data_from_state(s->state, i, j);
            if (token.id < whisper_token_eot(s->context)) { logProbability += token.plog; ++tokens; }
        }
        if (tokens == 0 || !std::isfinite(logProbability) || logProbability / tokens < -1.2) continue;
        if (text.empty()) first = whisper_full_get_segment_t0_from_state(s->state, i) * 10;
        text += whisper_full_get_segment_text_from_state(s->state, i);
        last = whisper_full_get_segment_t1_from_state(s->state, i) * 10;
    }
    const std::string detected = whisper_lang_str(s->detectedLanguage);
    const bool translated = translateToEnglish == JNI_TRUE && detected != target;
    std::string values[] = {translated ? "en" : detected, std::to_string(first), std::to_string(last), text};
    if (text.empty()) s->detectedLanguage = -1; // Re-detect after an unreliable automatic window.
    auto output = env->NewObjectArray(4, env->FindClass("java/lang/String"), nullptr);
    if (!output) return nullptr;
    for (int i = 0; i < 4; ++i) {
        auto value = javaString(env, values[i]);
        if (!value || env->ExceptionCheck()) return nullptr;
        env->SetObjectArrayElement(output, i, value); env->DeleteLocalRef(value);
    }
    return output;
}
extern "C" JNIEXPORT void JNICALL
Java_com_yourapp_iptv_data_caption_NativeWhisper_cancel(JNIEnv *, jobject, jlong handle) {
    if (handle) reinterpret_cast<Session *>(handle)->cancelled.store(true, std::memory_order_relaxed);
}
extern "C" JNIEXPORT void JNICALL
Java_com_yourapp_iptv_data_caption_NativeWhisper_park(JNIEnv *, jobject, jlong handle) {
    auto *s = reinterpret_cast<Session *>(handle);
    if (s) {
        whisper_free_state(s->state); s->state = nullptr;
        // Text inference must not retain even the speech weights on low-RAM televisions.
        // The verified file path and detected language suffice to reload on the next window.
        whisper_free(s->context); s->context = nullptr;
        purgeUnusedMemory();
    }
}
extern "C" JNIEXPORT void JNICALL
Java_com_yourapp_iptv_data_caption_NativeWhisper_resetState(JNIEnv *, jobject, jlong handle) {
    auto *s = reinterpret_cast<Session *>(handle);
    if (s) {
        // Called after decode has returned on the same serial inference worker.
        // Abort can leave scratch/state partly computed, but model weights remain valid.
        whisper_free_state(s->state); s->state = nullptr;
        purgeUnusedMemory();
    }
}
extern "C" JNIEXPORT void JNICALL
Java_com_yourapp_iptv_data_caption_NativeWhisper_trimMemory(JNIEnv *, jobject) {
    purgeUnusedMemory();
}
extern "C" JNIEXPORT void JNICALL
Java_com_yourapp_iptv_data_caption_NativeWhisper_release(JNIEnv *, jobject, jlong handle) {
    auto *s = reinterpret_cast<Session *>(handle);
    if (s) { whisper_free_state(s->state); whisper_free(s->context); delete s; purgeUnusedMemory(); }
}