#include <jni.h>
#include "CaptionJni.h"
#include <zstd.h>
#include <array>
#include <cstdio>
#include <memory>
#include <stdexcept>
extern "C" JNIEXPORT void JNICALL Java_mnstudio_apps_mnstreamx_data_caption_NativeBergamot_unpack(
    JNIEnv* env, jobject, jstring source, jstring destination, jlong expectedBytes) {
    MnxUtf8 inputPath(env, source), outputPath(env, destination);
    if (!inputPath.get() || !outputPath.get()) return;
    try {
        if (expectedBytes < 0 || expectedBytes > 64LL * 1024 * 1024) throw std::runtime_error("Invalid model extent");
        std::unique_ptr<FILE, decltype(&fclose)> input(fopen(inputPath.get(), "rb"), &fclose);
        std::unique_ptr<FILE, decltype(&fclose)> output(fopen(outputPath.get(), "wb"), &fclose);
        std::unique_ptr<ZSTD_DCtx, decltype(&ZSTD_freeDCtx)> ctx(ZSTD_createDCtx(), &ZSTD_freeDCtx);
        if (!input || !output || !ctx) throw std::runtime_error("Cannot open translation package");
        if (ZSTD_isError(ZSTD_DCtx_setParameter(ctx.get(), ZSTD_d_windowLogMax, 26))) throw std::runtime_error("Cannot limit decompression");
        std::array<char, 65536> sourceBuffer{}, targetBuffer{}; uint64_t total = 0; size_t remaining = 1;
        while (true) {
            size_t count = fread(sourceBuffer.data(), 1, sourceBuffer.size(), input.get());
            if (!count) { if (ferror(input.get())) throw std::runtime_error("Read failed"); break; }
            ZSTD_inBuffer in{sourceBuffer.data(), count, 0};
            while (in.pos < in.size) {
                ZSTD_outBuffer out{targetBuffer.data(), targetBuffer.size(), 0};
                remaining = ZSTD_decompressStream(ctx.get(), &out, &in);
                if (ZSTD_isError(remaining) || out.pos > static_cast<uint64_t>(expectedBytes) - total) throw std::runtime_error("Invalid compressed model");
                if (fwrite(targetBuffer.data(), 1, out.pos, output.get()) != out.pos) throw std::runtime_error("Write failed");
                total += out.pos;
            }
        }
        if (remaining != 0 || total != static_cast<uint64_t>(expectedBytes) || fflush(output.get()) != 0) throw std::runtime_error("Truncated model");
    } catch (const std::exception&) { mnxError(env, "Could not unpack the verified translation package"); }
    mnxPurgeMemory();
}