#pragma once
#include <jni.h>
#include <string>
#include <new>
#if defined(__ANDROID__) || defined(__GLIBC__)
#include <malloc.h>
#endif
inline void mnxPurgeMemory() {
#if defined(__ANDROID__)
    mallopt(M_PURGE, 0);
#elif defined(__GLIBC__)
    malloc_trim(0);
#endif
}
inline void mnxError(JNIEnv* env, const char* message, const char* type = "java/io/IOException") {
    if (env->ExceptionCheck()) return;
    auto cls = env->FindClass(type);
    if (cls) { env->ThrowNew(cls, message); env->DeleteLocalRef(cls); }
}
// Convert real UTF-8 instead of JNI's modified UTF-8 (which splits emoji).
class MnxUtf8 final {
    std::string value_; bool valid_ = false;
public:
    MnxUtf8(JNIEnv* env, jstring text) {
        if (!text) return;
        auto cls = env->FindClass("java/lang/String"); if (!cls) return;
        auto method = env->GetMethodID(cls, "getBytes", "(Ljava/lang/String;)[B");
        auto encoding = env->NewStringUTF("UTF-8");
        auto bytes = method && encoding ? static_cast<jbyteArray>(env->CallObjectMethod(text, method, encoding)) : nullptr;
        if (encoding) env->DeleteLocalRef(encoding); env->DeleteLocalRef(cls);
        if (!bytes || env->ExceptionCheck()) return;
        auto count = env->GetArrayLength(bytes);
        try {
            value_.resize(static_cast<size_t>(count));
            env->GetByteArrayRegion(bytes, 0, count, reinterpret_cast<jbyte*>(value_.data()));
            valid_ = !env->ExceptionCheck();
        } catch (const std::bad_alloc&) { mnxError(env, "UTF-8 allocation failed", "java/lang/OutOfMemoryError"); }
        env->DeleteLocalRef(bytes);
    }
    const char* get() const { return valid_ ? value_.c_str() : nullptr; }
    MnxUtf8(const MnxUtf8&) = delete;
};
inline jstring mnxJavaText(JNIEnv* env, const std::string& text) {
    auto bytes = env->NewByteArray(static_cast<jsize>(text.size())); if (!bytes) return nullptr;
    env->SetByteArrayRegion(bytes, 0, static_cast<jsize>(text.size()), reinterpret_cast<const jbyte*>(text.data()));
    auto cls = env->FindClass("java/lang/String");
    if (!cls) { env->DeleteLocalRef(bytes); return nullptr; }
    auto constructor = env->GetMethodID(cls, "<init>", "([BLjava/lang/String;)V");
    auto encoding = env->NewStringUTF("UTF-8");
    auto result = constructor && encoding ? static_cast<jstring>(env->NewObject(cls, constructor, bytes, encoding)) : nullptr;
    env->DeleteLocalRef(bytes); env->DeleteLocalRef(cls);
    if (encoding) env->DeleteLocalRef(encoding); return result;
}