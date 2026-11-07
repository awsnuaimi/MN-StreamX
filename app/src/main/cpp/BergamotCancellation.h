#pragma once
#include <atomic>
#include <chrono>
#include <stdexcept>
namespace mnx {
class TranslationDeadline final : public std::runtime_error {
public: TranslationDeadline() : std::runtime_error("Local text translation exceeded its deadline") {}
};
extern thread_local const std::atomic<bool>* translationCancelled;
extern thread_local std::chrono::steady_clock::time_point translationDeadline;
inline void checkTranslation() {
    if (translationCancelled && translationCancelled->load(std::memory_order_relaxed))
        throw std::runtime_error("Local text translation cancelled");
    if (translationCancelled && std::chrono::steady_clock::now() >= translationDeadline) throw TranslationDeadline();
}
class TranslationCall final {
public:
    TranslationCall(const std::atomic<bool>& cancelled, int millis) {
        translationCancelled = &cancelled;
        translationDeadline = std::chrono::steady_clock::now() + std::chrono::milliseconds(millis);
    }
    ~TranslationCall() { translationCancelled = nullptr; }
    TranslationCall(const TranslationCall&) = delete;
    TranslationCall& operator=(const TranslationCall&) = delete;
};
}