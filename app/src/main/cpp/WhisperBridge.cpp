#include <jni.h>
#include "CaptionJni.h"
#include <whisper.h>
#include <atomic>
#include <algorithm>
#include <chrono>
#include <cmath>
#include <memory>
#include <mutex>
#include <stdexcept>
#include <vector>
namespace {
struct SpeechSession {
    whisper_context* context = nullptr; whisper_state* state = nullptr;
    std::atomic<bool> cancelled{false}; std::string path; int threads = 1; int detected = -1;
    std::chrono::steady_clock::time_point deadline;
    ~SpeechSession() { whisper_free_state(state); whisper_free(context); }
};
bool interrupted(void* pointer) {
    auto* s = static_cast<SpeechSession*>(pointer);
    return s->cancelled.load(std::memory_order_relaxed) || std::chrono::steady_clock::now() >= s->deadline;
}
bool load(SpeechSession& s) {
    if (s.cancelled.load(std::memory_order_relaxed)) return false;
    if (s.context) return true;
    auto options = whisper_context_default_params(); options.use_gpu = false; options.flash_attn = true;
    s.context = whisper_init_from_file_with_params_no_state(s.path.c_str(), options); return s.context != nullptr;
}
void park(SpeechSession& s) {
    whisper_free_state(s.state); s.state = nullptr; whisper_free(s.context); s.context = nullptr; mnxPurgeMemory();
}
}
extern "C" JNIEXPORT jlong JNICALL Java_mnstudio_apps_mnstreamx_data_caption_NativeWhisper_create(
    JNIEnv* env, jobject, jstring path, jint threads) {
    MnxUtf8 name(env, path); if (!name.get()) return 0;
    try {
        static std::once_flag log; std::call_once(log, [] { whisper_log_set([](ggml_log_level, const char*, void*) {}, nullptr); });
        auto s = std::make_unique<SpeechSession>(); s->path = name.get(); s->threads = std::max(1, std::min(2, threads));
        if (!load(*s)) throw std::runtime_error("Speech model unavailable"); return reinterpret_cast<jlong>(s.release());
    } catch (const std::exception&) { mnxError(env, "Could not load the local speech model"); } return 0;
}
static jobjectArray mnxDecodeSpeech(JNIEnv* env, jlong pointer, jfloatArray samples,
    jstring language, bool detectionOnly) {
    auto* s = reinterpret_cast<SpeechSession*>(pointer);
    if (!s || !samples) { mnxError(env, "Invalid speech input"); return nullptr; }
    const auto count = env->GetArrayLength(samples);
    if (count < 16000 || count > 96000) { mnxError(env, "Invalid speech window"); return nullptr; }
    try {
        MnxUtf8 source(env, language);
        if (!detectionOnly && !source.get()) return nullptr;
        std::string selected = detectionOnly ? "auto" : source.get();
        if (!detectionOnly && selected == "auto" && s->detected >= 0) selected = whisper_lang_str(s->detected);
        if (selected != "auto" && whisper_lang_id(selected.c_str()) < 0) throw std::runtime_error("Unsupported language");
        if (!load(*s)) throw std::runtime_error("Speech model unavailable");
        if (!s->state) s->state = whisper_init_state(s->context);
        if (!s->state) throw std::runtime_error("Speech state unavailable");
        std::vector<float> audio(static_cast<size_t>(count)); env->GetFloatArrayRegion(samples, 0, count, audio.data());
        if (env->ExceptionCheck()) return nullptr;
        auto o = whisper_full_default_params(WHISPER_SAMPLING_GREEDY);
        o.n_threads = s->threads; o.greedy.best_of = 1; o.language = selected.c_str();
        o.audio_ctx = 0; o.detect_language = detectionOnly; o.translate = false; o.no_context = true; o.no_timestamps = true; o.single_segment = false;
        o.n_max_text_ctx = 0; o.max_tokens = 96; o.temperature = 0.0f; o.temperature_inc = 0.0f; o.suppress_nst = true;
        o.print_realtime = false; o.print_progress = false; o.print_timestamps = false; o.print_special = false;
        o.abort_callback = interrupted; o.abort_callback_user_data = s;
        s->deadline = std::chrono::steady_clock::now() + std::chrono::seconds(10);
        const auto status = whisper_full_with_state(s->context, s->state, o, audio.data(), count);
        if (s->cancelled.load(std::memory_order_relaxed)) throw std::runtime_error("Speech cancelled");
        if (std::chrono::steady_clock::now() >= s->deadline) {
            mnxError(env, "Local speech exceeded its deadline", "java/util/concurrent/TimeoutException"); return nullptr;
        }
        if (status != 0) throw std::runtime_error("Speech inference failed");
        s->detected = whisper_full_lang_id_from_state(s->state); const char* detected = whisper_lang_str(s->detected);
        if (!detected) throw std::runtime_error("Invalid detected language"); std::string text;
        for (int i = 0; !detectionOnly && i < whisper_full_n_segments_from_state(s->state); ++i) {
            if (whisper_full_get_segment_no_speech_prob_from_state(s->state, i) > 0.75f) continue;
            double probability = 0.0; int tokens = 0;
            for (int j = 0; j < whisper_full_n_tokens_from_state(s->state, i); ++j) {
                auto token = whisper_full_get_token_data_from_state(s->state, i, j);
                if (token.id < whisper_token_eot(s->context)) { probability += token.plog; ++tokens; }
            }
            if (tokens == 0 || !std::isfinite(probability) || probability / tokens < -1.2) continue;
            const char* value = whisper_full_get_segment_text_from_state(s->state, i); if (value) text += value;
        }
        if (!detectionOnly && text.empty()) s->detected = -1;
        const std::string result[] = {detected, "0", std::to_string(count * 1000LL / 16000), text};
        auto cls = env->FindClass("java/lang/String"); if (!cls) return nullptr;
        auto output = env->NewObjectArray(4, cls, nullptr); env->DeleteLocalRef(cls); if (!output) return nullptr;
        for (int i = 0; i < 4; ++i) {
            auto value = mnxJavaText(env, result[i]); if (!value || env->ExceptionCheck()) return nullptr;
            env->SetObjectArrayElement(output, i, value); env->DeleteLocalRef(value);
        } return output;
    } catch (const std::exception&) { mnxError(env, "Local speech failed or was cancelled"); } return nullptr;
}
extern "C" JNIEXPORT jobjectArray JNICALL Java_mnstudio_apps_mnstreamx_data_caption_NativeWhisper_decode(
    JNIEnv* env, jobject, jlong pointer, jfloatArray samples, jstring language, jboolean, jstring) {
    return mnxDecodeSpeech(env, pointer, samples, language, false);
}
extern "C" JNIEXPORT jobjectArray JNICALL Java_mnstudio_apps_mnstreamx_data_caption_NativeWhisper_detectLanguage(
    JNIEnv* env, jobject, jlong pointer, jfloatArray samples) {
    return mnxDecodeSpeech(env, pointer, samples, nullptr, true);
}
extern "C" JNIEXPORT void JNICALL Java_mnstudio_apps_mnstreamx_data_caption_NativeWhisper_cancel(JNIEnv*, jobject, jlong p) {
    if (p) reinterpret_cast<SpeechSession*>(p)->cancelled.store(true, std::memory_order_relaxed);
}
extern "C" JNIEXPORT void JNICALL Java_mnstudio_apps_mnstreamx_data_caption_NativeWhisper_park(JNIEnv*, jobject, jlong p) { if (p) park(*reinterpret_cast<SpeechSession*>(p)); }
extern "C" JNIEXPORT void JNICALL Java_mnstudio_apps_mnstreamx_data_caption_NativeWhisper_resetState(JNIEnv*, jobject, jlong p) {
    auto* s = reinterpret_cast<SpeechSession*>(p); if (s) { whisper_free_state(s->state); s->state = nullptr; mnxPurgeMemory(); }
}
extern "C" JNIEXPORT void JNICALL Java_mnstudio_apps_mnstreamx_data_caption_NativeWhisper_trimMemory(JNIEnv*, jobject) { mnxPurgeMemory(); }
extern "C" JNIEXPORT void JNICALL Java_mnstudio_apps_mnstreamx_data_caption_NativeWhisper_release(JNIEnv*, jobject, jlong p) { delete reinterpret_cast<SpeechSession*>(p); mnxPurgeMemory(); }