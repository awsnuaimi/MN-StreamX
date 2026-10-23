#include <jni.h>
#include <sentencepiece_processor.h>
#include <string>
#include <vector>

static void failure(JNIEnv *env, const char *message) {
    env->ThrowNew(env->FindClass("java/io/IOException"), message);
}
static std::string utf8(JNIEnv *env, jstring value) {
    auto cls = env->FindClass("java/lang/String");
    auto method = env->GetMethodID(cls, "getBytes", "(Ljava/lang/String;)[B");
    auto encoding = env->NewStringUTF("UTF-8");
    auto bytes = static_cast<jbyteArray>(env->CallObjectMethod(value, method, encoding));
    std::string result;
    if (bytes) {
        result.resize(env->GetArrayLength(bytes));
        env->GetByteArrayRegion(bytes, 0, static_cast<jsize>(result.size()), reinterpret_cast<jbyte *>(result.data()));
        env->DeleteLocalRef(bytes);
    }
    env->DeleteLocalRef(cls); env->DeleteLocalRef(encoding);
    return result;
}
static jstring stringValue(JNIEnv *env, const std::string &value) {
    auto cls = env->FindClass("java/lang/String");
    auto constructor = env->GetMethodID(cls, "<init>", "([BLjava/lang/String;)V");
    auto encoding = env->NewStringUTF("UTF-8");
    auto bytes = env->NewByteArray(static_cast<jsize>(value.size()));
    if (!bytes) return nullptr;
    env->SetByteArrayRegion(bytes, 0, static_cast<jsize>(value.size()), reinterpret_cast<const jbyte *>(value.data()));
    auto result = static_cast<jstring>(env->NewObject(cls, constructor, bytes, encoding));
    env->DeleteLocalRef(cls); env->DeleteLocalRef(encoding); env->DeleteLocalRef(bytes);
    return result;
}
extern "C" JNIEXPORT jlong JNICALL
Java_com_yourapp_iptv_data_caption_NativePieces_create(JNIEnv *env, jobject, jstring path) {
    sentencepiece::SentencePieceProcessor *processor = nullptr;
    try {
        processor = new sentencepiece::SentencePieceProcessor();
        if (!processor->Load(utf8(env, path)).ok()) { delete processor; processor = nullptr; }
    } catch (...) { delete processor; processor = nullptr; }
    if (!processor) failure(env, "Could not load translation tokenizer");
    return reinterpret_cast<jlong>(processor);
}
extern "C" JNIEXPORT jobjectArray JNICALL
Java_com_yourapp_iptv_data_caption_NativePieces_encode(JNIEnv *env, jobject, jlong handle, jstring text) {
    auto *processor = reinterpret_cast<sentencepiece::SentencePieceProcessor *>(handle);
    if (!processor) { failure(env, "Tokenizer is closed"); return nullptr; }
    std::vector<std::string> pieces;
    try {
        if (!processor->Encode(utf8(env, text), &pieces).ok() || pieces.size() > 512) {
            failure(env, "Invalid translation input"); return nullptr;
        }
    } catch (...) { failure(env, "Tokenizer failed"); return nullptr; }
    auto output = env->NewObjectArray(static_cast<jsize>(pieces.size()), env->FindClass("java/lang/String"), nullptr);
    if (!output) return nullptr;
    for (size_t i = 0; i < pieces.size(); ++i) {
        auto value = stringValue(env, pieces[i]);
        if (!value || env->ExceptionCheck()) return nullptr;
        env->SetObjectArrayElement(output, static_cast<jsize>(i), value); env->DeleteLocalRef(value);
    }
    return output;
}
extern "C" JNIEXPORT void JNICALL
Java_com_yourapp_iptv_data_caption_NativePieces_release(JNIEnv *, jobject, jlong handle) {
    delete reinterpret_cast<sentencepiece::SentencePieceProcessor *>(handle);
}
extern "C" JNIEXPORT jstring JNICALL
Java_com_yourapp_iptv_data_caption_NativePieces_decode(JNIEnv *env, jobject, jlong handle, jobjectArray values) {
    auto *processor = reinterpret_cast<sentencepiece::SentencePieceProcessor *>(handle);
    if (!processor) { failure(env, "Tokenizer is closed"); return nullptr; }
    try {
        const jsize count = env->GetArrayLength(values);
        if (count > 512) { failure(env, "Invalid translation output"); return nullptr; }
        std::vector<std::string> pieces;
        pieces.reserve(count);
        for (jsize i = 0; i < count; ++i) {
            auto value = static_cast<jstring>(env->GetObjectArrayElement(values, i));
            if (!value || env->ExceptionCheck()) return nullptr;
            pieces.push_back(utf8(env, value)); env->DeleteLocalRef(value);
            if (env->ExceptionCheck()) return nullptr;
        }
        std::string text;
        if (!processor->Decode(pieces, &text).ok()) { failure(env, "Tokenizer decoding failed"); return nullptr; }
        return stringValue(env, text);
    } catch (...) { failure(env, "Tokenizer decoding failed"); return nullptr; }
}