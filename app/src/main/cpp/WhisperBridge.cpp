#include <jni.h>
#include <whisper.h>
#include <atomic>
#include <chrono>
#include <string>
#include <vector>
#include <new>

struct Session {
    whisper_context *context = nullptr;
    int threads = 1;
    std::atomic<bool> cancelled{false};
    int detectedLanguage = -1;
    std::chrono::steady_clock::time_point deadline;
};
static bool abortWork(void *data) {
    auto *s = static_cast<Session *>(data);
    return s->cancelled.load(std::memory_order_relaxed) || std::chrono::steady_clock::now() > s->deadline;
}
static void error(JNIEnv *env, const char *message) {
    env->ThrowNew(env->FindClass("java/io/IOException"), message);
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
        s->threads = threads < 1 ? 1 : (threads > 2 ? 2 : threads);
        whisper_log_set([](ggml_log_level, const char *, void *) {}, nullptr);
        auto options = whisper_context_default_params(); options.use_gpu = false;
        s->context = whisper_init_from_file_with_params(name, options);
    } catch (...) { }
    env->ReleaseStringUTFChars(path, name);
    if (!s || !s->context) { delete s; error(env, "Could not load the local speech model"); return 0; }
    return reinterpret_cast<jlong>(s);
}
extern "C" JNIEXPORT jobjectArray JNICALL
Java_com_yourapp_iptv_data_caption_NativeWhisper_decode(JNIEnv *env, jobject, jlong handle,
                                                      jfloatArray samples, jstring language) {
    auto *s = reinterpret_cast<Session *>(handle);
    const jsize count = env->GetArrayLength(samples);
    if (!s || count < 16000 || count > 96000) { error(env, "Invalid speech window"); return nullptr; }
    const char *lang = env->GetStringUTFChars(language, nullptr);
    if (!lang) return nullptr;
    std::string selected(lang); env->ReleaseStringUTFChars(language, lang);
    if (selected == "auto" && s->detectedLanguage >= 0) selected = whisper_lang_str(s->detectedLanguage);
    std::vector<float> input;
    try { input.resize(count); } catch (...) { error(env, "Not enough speech memory"); return nullptr; }
    env->GetFloatArrayRegion(samples, 0, count, input.data());
    if (env->ExceptionCheck()) return nullptr;
    auto p = whisper_full_default_params(WHISPER_SAMPLING_GREEDY);
    p.n_threads = s->threads;
    p.language = selected == "auto" ? "auto" : selected.c_str();
    p.translate = true; p.no_context = true; p.single_segment = true;
    // Windows are at most five seconds. Avoid encoding thirty seconds of padding each time.
    p.audio_ctx = 384; p.max_tokens = 72; p.n_max_text_ctx = 0;
    p.print_realtime = false; p.print_progress = false; p.print_timestamps = false; p.print_special = false;
    p.suppress_nst = true; p.temperature = 0.0f; p.temperature_inc = 0.0f;
    p.abort_callback = abortWork; p.abort_callback_user_data = s;
    // A hard native deadline also covers an engine that is too slow for live captions.
    s->deadline = std::chrono::steady_clock::now() + std::chrono::seconds(10);
    int result;
    try { result = whisper_full(s->context, p, input.data(), count); }
    catch (...) { error(env, "Local speech inference failed"); return nullptr; }
    if (std::chrono::steady_clock::now() > s->deadline) {
        env->ThrowNew(env->FindClass("java/util/concurrent/TimeoutException"), "Local speech exceeded its time budget");
        return nullptr;
    }
    if (result != 0 || abortWork(s)) { error(env, "Local speech inference interrupted"); return nullptr; }
    s->detectedLanguage = whisper_full_lang_id(s->context);
    std::string text;
    int64_t first = 0, last = 0;
    const int segments = whisper_full_n_segments(s->context);
    for (int i = 0; i < segments; ++i) {
        if (whisper_full_get_segment_no_speech_prob(s->context, i) > 0.75f) continue;
        if (text.empty()) first = whisper_full_get_segment_t0(s->context, i) * 10;
        text += whisper_full_get_segment_text(s->context, i);
        last = whisper_full_get_segment_t1(s->context, i) * 10;
    }
    std::string values[] = {"en", std::to_string(first), std::to_string(last), text};
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
Java_com_yourapp_iptv_data_caption_NativeWhisper_release(JNIEnv *, jobject, jlong handle) {
    auto *s = reinterpret_cast<Session *>(handle);
    if (s) { whisper_free(s->context); delete s; }
}