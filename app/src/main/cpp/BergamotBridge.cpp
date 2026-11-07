#include <jni.h>
#include "CaptionJni.h"
#include "BergamotCancellation.h"
#include "translator/parser.h"
#include "translator/service.h"
#include "translator/response.h"
#include <algorithm>
#include <atomic>
#include <memory>
#include <mutex>
#include <stdexcept>
namespace mnx {
thread_local const std::atomic<bool>* translationCancelled = nullptr;
thread_local std::chrono::steady_clock::time_point translationDeadline;
}
namespace {
struct TextSession {
    std::atomic<bool> cancelled{false};
    marian::bergamot::BlockingService service;
    std::shared_ptr<marian::bergamot::TranslationModel> model;
    TextSession() : service(marian::bergamot::BlockingService::Config{}) {}
};
std::string quote(const char* path) {
    std::string value = "\"";
    for (const char* p = path; *p; ++p) {
        if (*p == '\\' || *p == '"') value += '\\';
        if (*p == '\n' || *p == '\r') throw std::runtime_error("Invalid local model path");
        value += *p;
    }
    return value + "\"";
}
}
extern "C" JNIEXPORT jlong JNICALL Java_mnstudio_apps_mnstreamx_data_caption_NativeBergamot_create(
    JNIEnv* env, jobject, jstring modelPath, jstring vocabularyPath, jstring shortlistPath) {
    MnxUtf8 model(env, modelPath), vocab(env, vocabularyPath), shortlist(env, shortlistPath);
    if (!model.get() || !vocab.get() || !shortlist.get()) return 0;
    try {
        static std::once_flag exceptions;
        std::call_once(exceptions, [] { marian::setThrowExceptionOnAbort(true); });
        auto session = std::make_unique<TextSession>();
        const std::string config = "models: [" + quote(model.get()) + "]\nvocabs: [" + quote(vocab.get()) + ", " +
            quote(vocab.get()) + "]\nshortlist: [" + quote(shortlist.get()) + ", 50, 50]\n"
            "gemm-precision: int8shiftAlphaAll\nbeam-size: 1\nnormalize: 1.0\nword-penalty: 0\n"
            "max-length-break: 96\nmini-batch-words: 256\nmax-length-factor: 2.0\ncpu-threads: 1\n"
            "skip-cost: true\nworkspace: 32\n";
        auto options = marian::bergamot::parseOptionsFromString(config);
        session->model = std::make_shared<marian::bergamot::TranslationModel>(options, 1);
        return reinterpret_cast<jlong>(session.release());
    } catch (const std::exception&) { mnxError(env, "Could not load the local translation model"); }
    return 0;
}
extern "C" JNIEXPORT jstring JNICALL Java_mnstudio_apps_mnstreamx_data_caption_NativeBergamot_translate(
    JNIEnv* env, jobject, jlong pointer, jstring input, jint budgetMillis) {
    auto* session = reinterpret_cast<TextSession*>(pointer); MnxUtf8 text(env, input);
    if (!session || !text.get()) return nullptr;
    try {
        mnx::TranslationCall call(session->cancelled, std::max(1, std::min(8000, budgetMillis)));
        mnx::checkTranslation();
        marian::bergamot::ResponseOptions options;
        auto output = session->service.translateMultiple(session->model, {std::string(text.get())}, {options});
        mnx::checkTranslation();
        if (output.size() != 1) throw std::runtime_error("Invalid local text result");
        return mnxJavaText(env, output.front().target.text);
    } catch (const mnx::TranslationDeadline&) {
        mnxError(env, "Local text translation exceeded its deadline", "java/util/concurrent/TimeoutException");
    } catch (const std::exception&) { mnxError(env, "Local text translation failed or was cancelled"); }
    return nullptr;
}
extern "C" JNIEXPORT void JNICALL Java_mnstudio_apps_mnstreamx_data_caption_NativeBergamot_cancel(JNIEnv*, jobject, jlong p) {
    if (p) reinterpret_cast<TextSession*>(p)->cancelled.store(true, std::memory_order_relaxed);
}
extern "C" JNIEXPORT void JNICALL Java_mnstudio_apps_mnstreamx_data_caption_NativeBergamot_release(JNIEnv*, jobject, jlong p) {
    delete reinterpret_cast<TextSession*>(p); mnxPurgeMemory();
}